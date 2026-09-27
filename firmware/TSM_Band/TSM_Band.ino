/*
  Tennis Score Manager - firmware braccialetto M5StickS3
  --------------------------------------------------------
  KEY1 corto  : punto a chi indossa il braccialetto
  KEY1 lungo  : mostra la batteria
  KEY2 corto  : annulla l'ultimo punto
  KEY2 lungo  : spegne il braccialetto (riaccensione: tasto laterale, un clic)

  Librerie (Gestore librerie di Arduino IDE):
    - M5Unified      >= 0.2.12  (installa anche M5GFX)
    - NimBLE-Arduino >= 2.1     (di h2zero)
  Scheda: "M5StickS3" (pacchetto schede M5Stack >= 3.2.5)
          oppure "ESP32S3 Dev Module" del pacchetto esp32 di Espressif.

  Il protocollo BLE è descritto in BandProtocol.kt dell'app: gli UUID devono coincidere.
*/

#include <M5Unified.h>
#include <NimBLEDevice.h>

// ------------------------------------------------------------------ impostazioni
#define DISPLAY_ROTATION 1          // 1 o 3 = orizzontale (girare di 180° se il braccialetto è montato al contrario)
static const uint8_t  BRIGHTNESS           = 48;                 // luminosità bassa (0-255)
static const uint32_t PAIRING_TIMEOUT_MS   = 3UL * 60UL * 1000UL;  // nessun telefono all'accensione: si spegne dopo 3 min
static const uint32_t RECONNECT_TIMEOUT_MS = 5UL * 60UL * 1000UL;  // telefono perso: si spegne dopo 5 min
static const uint32_t IDLE_TIMEOUT_MS      = 45UL * 60UL * 1000UL; // connesso ma inattivo: si spegne dopo 45 min
static const uint32_t FAST_ADV_MS          = 30UL * 1000UL;        // primi 30 s: advertising veloce
static const uint32_t KEY1_HOLD_MS         = 1000;                 // pressione lunga KEY1 (batteria)
static const uint32_t KEY2_HOLD_MS         = 2000;                 // pressione lunga KEY2 (spegnimento)
static const uint32_t BLINK_PERIOD_MS      = 2000;                 // lampeggio "PAIRING": ogni 2 s...
static const uint32_t BLINK_ON_MS          = 350;                  // ...acceso solo 350 ms
static const uint32_t PAIRED_MSG_MS        = 3000;                 // "PAIRING OK" per 3 s
static const uint32_t POINT_SHOW_MS        = 3000;                 // punteggio del game acceso 3 s
static const uint32_t GAMES_SHOW_MS        = 5000;                 // riepilogo game/set acceso 5 s
static const uint32_t STATUS_PERIOD_MS     = 60000;                // stato batteria al telefono ogni minuto

// Testi mostrati dal braccialetto (solo ASCII)
#define TXT_PAIRING    "PAIRING..."
#define TXT_PAIRED     "PAIRING OK"
#define TXT_NO_PHONE   "NESSUN TELEFONO"
#define TXT_POWER_OFF  "SPEGNIMENTO"
#define TXT_BATTERY    "BATTERIA"
#define TXT_CHARGING   "IN CARICA"
#define TXT_NO_LINK    "NON CONNESSO"
#define TXT_IDLE       "INATTIVO"

// ------------------------------------------------------------------ protocollo (uguale all'app)
#define SERVICE_UUID "7a1e0001-5c3b-4f6e-9d2a-3e7b1c9a0f10"
#define EVENT_UUID   "7a1e0002-5c3b-4f6e-9d2a-3e7b1c9a0f10"
#define DISPLAY_UUID "7a1e0003-5c3b-4f6e-9d2a-3e7b1c9a0f10"
#define STATUS_UUID  "7a1e0004-5c3b-4f6e-9d2a-3e7b1c9a0f10"  // "mv=3987;chg=0;up=1234;dsp=56" per misurare i consumi
enum : uint8_t { EVT_POINT = 1, EVT_UNDO = 2, EVT_POWER_OFF = 3, EVT_BATTERY = 4 };

// Colori (RGB565)
static const uint16_t C_BG     = TFT_BLACK;
static const uint16_t C_TEXT   = TFT_WHITE;
static const uint16_t C_DIM    = 0x8410;   // grigio
static const uint16_t C_BALL   = 0xC7E6;   // verde pallina
static const uint16_t C_ORANGE = 0xFD20;
static const uint16_t C_RED    = 0xF800;

// ------------------------------------------------------------------ stato
static NimBLEServer*         server   = nullptr;
static NimBLECharacteristic* evtChr   = nullptr;
static NimBLECharacteristic* battChr  = nullptr;
static NimBLECharacteristic* statusChr = nullptr;
static char deviceName[16];

static volatile bool connected      = false;
static volatile bool justConnected  = false;
static volatile bool justDisconnect = false;
static bool everConnected = false;

// Messaggio ricevuto dal telefono: lo scrive il task BLE, lo disegna il loop.
static portMUX_TYPE rxMux = portMUX_INITIALIZER_UNLOCKED;
static char rxBuf[192];
static volatile bool rxReady = false;

static uint8_t  seqNo = 0;
static bool     displayOn = false;
static uint32_t displayOffAt = 0;
static volatile uint32_t advSince = 0;  // aggiornato anche dal task BLE alla disconnessione
static bool     advFast = true;
static bool     advertising = false;
static uint32_t lastActivity = 0;
static uint32_t lastBattery = 0;
static uint32_t displayOnSince = 0;   // per contare quanto resta acceso il display (diagnostica consumi)
static uint32_t displayOnTotalMs = 0;
static uint32_t nextBlink = 0;
static bool     blinkShown = false;

// ------------------------------------------------------------------ display
static void displayWake() {
  if (!displayOn) {
    M5.Display.wakeup();
    M5.Display.setBrightness(BRIGHTNESS);
    displayOn = true;
    displayOnSince = millis();
  }
}

static void displaySleep() {
  if (displayOn) {
    M5.Display.fillScreen(C_BG);
    M5.Display.setBrightness(0);
    M5.Display.sleep();
    displayOn = false;
    displayOnTotalMs += millis() - displayOnSince;
  }
}

static void showFor(uint32_t ms) {
  displayOffAt = millis() + ms;
}

// Scrive un testo centrato scegliendo il font più grande che ci sta in larghezza.
static void drawFit(const char* txt, int y, const lgfx::IFont* const* fonts, int nFonts, uint16_t color, int maxW = 232) {
  M5.Display.setTextColor(color, C_BG);
  M5.Display.setTextDatum(middle_center);
  for (int i = 0; i < nFonts; i++) {
    M5.Display.setFont(fonts[i]);
    M5.Display.setTextSize(1);
    if (M5.Display.textWidth(txt) <= maxW || i == nFonts - 1) break;
  }
  M5.Display.drawString(txt, M5.Display.width() / 2, y);
}

static const lgfx::IFont* const BIG_FONTS[]   = { &fonts::FreeSansBold24pt7b, &fonts::FreeSansBold18pt7b, &fonts::FreeSansBold12pt7b, &fonts::FreeSansBold9pt7b };
static const lgfx::IFont* const SMALL_FONTS[] = { &fonts::FreeSansBold12pt7b, &fonts::FreeSansBold9pt7b, &fonts::Font2 };

static void drawMessage(const char* l1, const char* l2, uint16_t color = C_TEXT) {
  displayWake();
  M5.Display.fillScreen(C_BG);
  if (l2 && l2[0]) {
    drawFit(l1, 45, BIG_FONTS, 4, color);
    drawFit(l2, 100, SMALL_FONTS, 3, C_DIM);
  } else {
    drawFit(l1, M5.Display.height() / 2, BIG_FONTS, 4, color);
  }
}

// Punteggio del game: a sinistra chi indossa il braccialetto, a destra l'avversario.
static void drawPoint(const char* mine, const char* theirs, int serve, const char* header) {
  displayWake();
  M5.Display.fillScreen(C_BG);
  const int w = M5.Display.width();
  const int h = M5.Display.height();
  if (header && header[0]) drawFit(header, 14, SMALL_FONTS, 3, C_ORANGE);
  M5.Display.setFont(&fonts::FreeSansBold24pt7b);
  M5.Display.setTextDatum(middle_center);
  M5.Display.setTextSize(1.5f);
  M5.Display.setTextColor(C_TEXT, C_BG);
  M5.Display.drawString(mine, w / 4, h / 2 + 6);
  M5.Display.setTextColor(C_DIM, C_BG);
  M5.Display.drawString(theirs, 3 * w / 4, h / 2 + 6);
  M5.Display.setTextSize(1);
  M5.Display.fillRect(w / 2 - 1, h / 2 - 20, 3, 44, C_DIM);
  // pallina sotto chi serve
  if (serve == 1) M5.Display.fillCircle(w / 4, h - 12, 7, C_BALL);
  if (serve == 2) M5.Display.fillCircle(3 * w / 4, h - 12, 7, C_BALL);
}

// Riepilogo a fine game: game del set e set vinti.
static void drawGames(int myG, int thG, int myS, int thS, const char* header) {
  displayWake();
  M5.Display.fillScreen(C_BG);
  const int w = M5.Display.width();
  if (header && header[0]) drawFit(header, 14, SMALL_FONTS, 3, C_ORANGE);
  char buf[16];
  M5.Display.setTextDatum(middle_left);
  M5.Display.setFont(&fonts::FreeSansBold12pt7b);
  M5.Display.setTextColor(C_DIM, C_BG);
  M5.Display.drawString("GAME", 8, 55);
  M5.Display.drawString("SET", 8, 108);
  M5.Display.setTextDatum(middle_right);
  M5.Display.setFont(&fonts::FreeSansBold24pt7b);
  M5.Display.setTextColor(C_TEXT, C_BG);
  snprintf(buf, sizeof(buf), "%d - %d", myG, thG);
  M5.Display.drawString(buf, w - 8, 55);
  M5.Display.setFont(&fonts::FreeSansBold18pt7b);
  M5.Display.setTextColor(C_BALL, C_BG);
  snprintf(buf, sizeof(buf), "%d - %d", myS, thS);
  M5.Display.drawString(buf, w - 8, 108);
}

static void drawBattery() {
  int level = M5.Power.getBatteryLevel();
  bool charging = M5.Power.isCharging() == m5::Power_Class::is_charging;
  char buf[24];
  if (level < 0) snprintf(buf, sizeof(buf), "--%%");
  else snprintf(buf, sizeof(buf), "%d%%", level);
  drawMessage(buf, charging ? TXT_CHARGING : TXT_BATTERY, level >= 0 && level < 20 ? C_RED : C_BALL);
  showFor(3000);
}

// ------------------------------------------------------------------ messaggi dal telefono
// P|mio|avversario|servizio|intestazione   G|mieiG|loroG|mieiS|loroS|intestazione   M|riga1|riga2|secondi
static int splitFields(char* s, char** out, int maxOut) {
  int n = 0;
  out[n++] = s;
  for (char* p = s; *p && n < maxOut; p++) {
    if (*p == '|') {
      *p = 0;
      out[n++] = p + 1;
    }
  }
  return n;
}

static void handleMessage(char* msg) {
  char* f[8] = { 0 };
  int n = splitFields(msg, f, 8);
  if (n < 1 || !f[0][0]) return;
  switch (f[0][0]) {
    case 'P':
      if (n >= 5) { drawPoint(f[1], f[2], atoi(f[3]), f[4]); showFor(POINT_SHOW_MS); }
      break;
    case 'G':
      if (n >= 6) { drawGames(atoi(f[1]), atoi(f[2]), atoi(f[3]), atoi(f[4]), f[5]); showFor(GAMES_SHOW_MS); }
      break;
    case 'M':
      if (n >= 4) {
        drawMessage(f[1], f[2]);
        int s = atoi(f[3]);
        showFor((uint32_t)constrain(s, 1, 30) * 1000UL);
      }
      break;
  }
}

// ------------------------------------------------------------------ BLE
class ServerCallbacks : public NimBLEServerCallbacks {
  void onConnect(NimBLEServer* s, NimBLEConnInfo& info) override {
    connected = true;
    justConnected = true;
    // Intervallo 80-120 ms con latenza 3: senza traffico la radio si sveglia 2-3 volte al secondo
    // (con 30 ms e latenza 0 erano 33 volte), un tasto parte comunque entro ~120 ms.
    // Timeout 6 s: tiene la connessione anche con il polso che copre l'antenna.
    s->updateConnParams(info.getConnHandle(), 64, 96, 3, 600);
  }
  void onDisconnect(NimBLEServer* s, NimBLEConnInfo& info, int reason) override {
    // Prima il tempo, poi lo stato: il loop non deve mai vedere "non connesso" con un advSince vecchio
    // (spegnerebbe il braccialetto in piena partita).
    advSince = millis();
    connected = false;
    justDisconnect = true;
  }
};

class DisplayCallbacks : public NimBLECharacteristicCallbacks {
  void onWrite(NimBLECharacteristic* c, NimBLEConnInfo& info) override {
    NimBLEAttValue v = c->getValue();
    size_t len = v.length();
    if (len >= sizeof(rxBuf)) len = sizeof(rxBuf) - 1;
    portENTER_CRITICAL(&rxMux);
    memcpy(rxBuf, v.data(), len);
    rxBuf[len] = 0;
    rxReady = true;
    portEXIT_CRITICAL(&rxMux);
  }
};

static void startAdvertising(bool fast) {
  NimBLEAdvertising* adv = NimBLEDevice::getAdvertising();
  adv->stop();
  // unità da 0,625 ms: 160-240 = 100-150 ms (veloce), 1600-1920 = 1-1,2 s (lento)
  adv->setMinInterval(fast ? 160 : 1600);
  adv->setMaxInterval(fast ? 240 : 1920);
  adv->start();
  advertising = true;
  advFast = fast;
}

static void sendEvent(uint8_t type) {
  if (!connected || !evtChr) return;
  uint8_t data[2] = { type, ++seqNo };
  evtChr->setValue(data, 2);
  evtChr->notify();
}

static void updateBattery(bool notify) {
  int level = M5.Power.getBatteryLevel();
  if (level >= 0) {
    uint8_t v = (uint8_t)constrain(level, 0, 100);
    battChr->setValue(&v, 1);
    if (notify && connected) battChr->notify();
  }
  // Stato per l'app: tensione in mV (più precisa della percentuale), in carica, secondi di accensione,
  // secondi di display acceso. Con questi dati l'app calcola consumo e autonomia reali.
  char buf[64];
  uint32_t dsp = displayOnTotalMs + (displayOn ? millis() - displayOnSince : 0);
  snprintf(buf, sizeof(buf), "mv=%d;chg=%d;up=%lu;dsp=%lu",
           (int)M5.Power.getBatteryVoltage(),
           M5.Power.isCharging() == m5::Power_Class::is_charging ? 1 : 0,
           (unsigned long)(millis() / 1000), (unsigned long)(dsp / 1000));
  statusChr->setValue((const uint8_t*)buf, strlen(buf));
  if (notify && connected) statusChr->notify();
}

static void setupBle() {
  uint64_t mac = ESP.getEfuseMac();
  snprintf(deviceName, sizeof(deviceName), "TSM-%04X", (unsigned)((mac >> 32) & 0xFFFF));
  NimBLEDevice::init(deviceName);
  NimBLEDevice::setPower(9);  // dBm: portata sufficiente per un campo da tennis col polso in mezzo
  NimBLEDevice::setMTU(185);

  server = NimBLEDevice::createServer();
  server->setCallbacks(new ServerCallbacks());
  server->advertiseOnDisconnect(false);  // la ripartenza la gestisce il loop

  NimBLEService* svc = server->createService(SERVICE_UUID);
  evtChr = svc->createCharacteristic(EVENT_UUID, NIMBLE_PROPERTY::READ | NIMBLE_PROPERTY::NOTIFY);
  NimBLECharacteristic* disp = svc->createCharacteristic(DISPLAY_UUID, NIMBLE_PROPERTY::WRITE | NIMBLE_PROPERTY::WRITE_NR);
  disp->setCallbacks(new DisplayCallbacks());
  statusChr = svc->createCharacteristic(STATUS_UUID, NIMBLE_PROPERTY::READ | NIMBLE_PROPERTY::NOTIFY);

  NimBLEService* bas = server->createService("180F");
  battChr = bas->createCharacteristic("2A19", NIMBLE_PROPERTY::READ | NIMBLE_PROPERTY::NOTIFY);
  server->start();  // registra i servizi GATT (in NimBLE 2.x si avvia il server, non i singoli servizi)
  updateBattery(false);

  // Pacchetto di advertising: flag + UUID del servizio (l'app filtra la ricerca su questo);
  // il nome va nella risposta allo scan per stare nei 31 byte.
  NimBLEAdvertisementData advData;
  advData.setFlags(BLE_HS_ADV_F_DISC_GEN | BLE_HS_ADV_F_BREDR_UNSUP);
  advData.addServiceUUID(NimBLEUUID(SERVICE_UUID));
  NimBLEAdvertisementData scanData;
  scanData.setName(deviceName);
  NimBLEAdvertising* adv = NimBLEDevice::getAdvertising();
  adv->setAdvertisementData(advData);
  adv->setScanResponseData(scanData);
}

// ------------------------------------------------------------------ spegnimento
static void powerOff(const char* why) {
  drawMessage(TXT_POWER_OFF, why, C_ORANGE);
  delay(1500);
  if (connected) {
    for (uint16_t h : server->getPeerDevices()) server->disconnect(h);
    delay(200);
  }
  M5.Display.setBrightness(0);
  M5.Display.sleep();
  M5.Power.powerOff();
  // Con l'USB collegato alcuni moduli restano alimentati: in quel caso si resta a schermo spento.
  while (true) delay(1000);
}

// ------------------------------------------------------------------ setup / loop
void setup() {
  setCpuFrequencyMhz(80);  // il minimo che tiene in piedi il Bluetooth: consumo molto più basso di 240 MHz

  auto cfg = M5.config();
  cfg.clear_display = true;
  cfg.output_power  = false;   // niente 5V sul connettore Grove
  cfg.internal_imu  = false;
  cfg.internal_rtc  = false;
  cfg.internal_spk  = false;
  cfg.internal_mic  = false;
  cfg.led_brightness = 0;
  M5.begin(cfg);
  M5.Display.setRotation(DISPLAY_ROTATION);
  M5.Display.setBrightness(BRIGHTNESS);
  displayOn = true;
  M5.BtnA.setHoldThresh(KEY1_HOLD_MS);
  M5.BtnB.setHoldThresh(KEY2_HOLD_MS);

  setupBle();

  drawMessage("TSM BAND", deviceName, C_BALL);
  delay(1200);
  displaySleep();

  advSince = millis();
  startAdvertising(true);
  lastActivity = millis();
  nextBlink = millis();
}

void loop() {
  M5.update();
  const uint32_t now = millis();

  // --- eventi BLE
  if (justConnected) {
    justConnected = false;
    everConnected = true;
    advertising = false;
    lastActivity = now;
    drawMessage(TXT_PAIRED, deviceName, C_BALL);
    showFor(PAIRED_MSG_MS);
    updateBattery(true);
  }
  if (justDisconnect) {
    justDisconnect = false;
    advSince = now;
    startAdvertising(true);
  }
  if (rxReady) {
    char local[sizeof(rxBuf)];
    portENTER_CRITICAL(&rxMux);
    memcpy(local, rxBuf, sizeof(rxBuf));
    rxReady = false;
    portEXIT_CRITICAL(&rxMux);
    // Il messaggio "PAIRING OK" resta i suoi 3 secondi; poi vale quello che manda il telefono.
    handleMessage(local);
    lastActivity = now;
  }

  // --- tasti
  if (M5.BtnA.wasClicked()) {
    lastActivity = now;
    if (connected) {
      sendEvent(EVT_POINT);
    } else {
      drawMessage(TXT_NO_LINK, deviceName, C_RED);
      showFor(1500);
    }
  }
  if (M5.BtnA.wasHold()) {
    lastActivity = now;
    drawBattery();
    sendEvent(EVT_BATTERY);
  }
  if (M5.BtnB.wasClicked()) {
    lastActivity = now;
    if (connected) {
      sendEvent(EVT_UNDO);
    } else {
      drawMessage(TXT_NO_LINK, deviceName, C_RED);
      showFor(1500);
    }
  }
  if (M5.BtnB.wasHold()) {
    sendEvent(EVT_POWER_OFF);
    delay(300);  // lascia partire la notifica prima di spegnere
    powerOff("");
  }

  // --- advertising e lampeggio "PAIRING"
  if (!connected && !justDisconnect) {
    if (advFast && now - advSince > FAST_ADV_MS) startAdvertising(false);
    const uint32_t limit = everConnected ? RECONNECT_TIMEOUT_MS : PAIRING_TIMEOUT_MS;
    if ((int32_t)(millis() - advSince) > (int32_t)limit) powerOff(TXT_NO_PHONE);
    if ((int32_t)(now - nextBlink) >= 0 && (!displayOn || blinkShown)) {
      drawMessage(TXT_PAIRING, deviceName, C_BALL);
      showFor(BLINK_ON_MS);
      blinkShown = true;
      nextBlink = now + BLINK_PERIOD_MS;
    }
  } else if (connected) {
    blinkShown = false;
    if (now - lastActivity > IDLE_TIMEOUT_MS) powerOff(TXT_IDLE);
  }

  // --- batteria e stato ogni minuto
  if (now - lastBattery > STATUS_PERIOD_MS) {
    lastBattery = now;
    updateBattery(true);
  }

  // --- spegnimento display a tempo
  if (displayOn && (int32_t)(now - displayOffAt) >= 0) {
    displaySleep();
    blinkShown = !connected;
  }

  delay(20);  // 50 Hz per i tasti; nel resto del tempo la CPU resta ferma in idle
}
