/*
  Tennis Score Manager - firmware braccialetto M5StickS3
  --------------------------------------------------------
  KEY1 corto  : punto a chi indossa il braccialetto
  KEY1 lungo  : mostra la batteria (tiene anche acceso il braccialetto se è inattivo)
  KEY2 corto  : annulla l'ultimo punto
  KEY2 lungo  : spegne il braccialetto (riaccensione: tasto laterale, un clic)
  Se non è collegato, un tasto qualsiasi rimanda lo spegnimento automatico.

  Impostazioni (dall'app, menu "Impostazioni braccialetto"), salvate nel braccialetto:
  nome, luminosità, durata del punteggio, volume, display capovolto e i tre tempi di
  spegnimento automatico (nessun telefono all'accensione, telefono perso, inattività).

  Librerie (Gestore librerie di Arduino IDE):
    - M5Unified      >= 0.2.12  (installa anche M5GFX)
    - NimBLE-Arduino >= 2.1     (di h2zero)
  Scheda: "M5StickS3" (pacchetto schede M5Stack >= 3.2.5)
          oppure "ESP32S3 Dev Module" del pacchetto esp32 di Espressif.

  Il protocollo BLE è descritto in BandProtocol.kt dell'app: gli UUID devono coincidere.
*/

#include <M5Unified.h>
#include <NimBLEDevice.h>
#include <Preferences.h>

#define FW_VERSION "2.0"

// ------------------------------------------------------------------ tempi fissi
static const uint32_t FAST_ADV_MS          = 30UL * 1000UL;        // primi 30 s: advertising veloce
static const uint32_t KEY1_HOLD_MS         = 1000;                 // pressione lunga KEY1 (batteria)
static const uint32_t KEY2_HOLD_MS         = 2000;                 // pressione lunga KEY2 (spegnimento)
static const uint32_t BLINK_PERIOD_MS      = 2000;                 // lampeggio "PAIRING": ogni 2 s...
static const uint32_t BLINK_ON_MS          = 350;                  // ...acceso solo 350 ms
static const uint32_t COUNTDOWN_MS         = 10000;                // ultimi 10 s prima dello spegnimento: lampeggio ogni secondo
static const uint32_t IDLE_WARN_MS         = 30000;                // avviso 30 s prima dello spegnimento per inattività
static const uint32_t PAIRED_MSG_MS        = 3000;                 // "PAIRING OK" per 3 s
static const uint32_t STATUS_PERIOD_MS     = 60000;                // stato batteria al telefono ogni minuto
static const uint32_t BATT_CHECK_MS        = 15000;                // controllo batteria scarica
static const int      BATT_EMPTY_MV        = 3300;                 // sotto questa tensione la LiPo è vuota
static const uint32_t IDENTIFY_FLASH_MS    = 300;                  // lampeggio di "Identifica"

// Testi mostrati dal braccialetto (solo ASCII)
#define TXT_PAIRING    "PAIRING..."
#define TXT_PAIRED     "PAIRING OK"
#define TXT_RECONNECT  "RICONNESSIONE"
#define TXT_NO_PHONE   "NESSUN TELEFONO"
#define TXT_POWER_OFF  "SPEGNIMENTO"
#define TXT_BATTERY    "BATTERIA"
#define TXT_CHARGING   "IN CARICA"
#define TXT_NO_LINK    "NON CONNESSO"
#define TXT_IDLE       "INATTIVO"
#define TXT_HOLD_KEY1  "TIENI PREMUTO KEY1"
#define TXT_EMPTY      "BATTERIA SCARICA"
#define TXT_SAVED      "IMPOSTAZIONI OK"

// ------------------------------------------------------------------ protocollo (uguale all'app)
#define SERVICE_UUID "7a1e0001-5c3b-4f6e-9d2a-3e7b1c9a0f10"
#define EVENT_UUID   "7a1e0002-5c3b-4f6e-9d2a-3e7b1c9a0f10"
#define DISPLAY_UUID "7a1e0003-5c3b-4f6e-9d2a-3e7b1c9a0f10"
#define STATUS_UUID  "7a1e0004-5c3b-4f6e-9d2a-3e7b1c9a0f10"  // "mv=3987;chg=0;up=1234;dsp=56" per misurare i consumi
#define CONFIG_UUID  "7a1e0005-5c3b-4f6e-9d2a-3e7b1c9a0f10"  // impostazioni: "fw=2.0;name=...;bri=20;pt=3;vol=50;flip=0;pair=30;lost=180;idle=30"
enum : uint8_t { EVT_POINT = 1, EVT_UNDO = 2, EVT_POWER_OFF = 3, EVT_BATTERY = 4 };
// Terzo byte di EVT_POWER_OFF: perché si spegne
enum : uint8_t { OFF_KEY = 0, OFF_IDLE = 1, OFF_BATTERY = 2, OFF_APP = 3, OFF_TIMEOUT = 4 };

// Colori (RGB565)
static const uint16_t C_BG     = TFT_BLACK;
static const uint16_t C_TEXT   = TFT_WHITE;
static const uint16_t C_DIM    = 0x8410;   // grigio
static const uint16_t C_BALL   = 0xC7E6;   // verde pallina
static const uint16_t C_ORANGE = 0xFD20;
static const uint16_t C_RED    = 0xF800;

// ------------------------------------------------------------------ impostazioni (salvate in memoria)
struct Settings {
  char     name[13];   // nome del braccialetto, max 12 caratteri ASCII
  uint8_t  bri;        // luminosità display 5-100 %
  uint8_t  pointS;     // secondi di punteggio acceso dopo ogni punto (0 = non mostrarlo)
  uint8_t  vol;        // volume cicalino 0-100 % (0 = muto)
  bool     flip;       // display capovolto (braccialetto sull'altro polso)
  uint16_t pairS;      // spegnimento se all'accensione nessun telefono si collega (s)
  uint16_t lostS;      // spegnimento se il telefono si scollega (s)
  uint16_t idleMin;    // spegnimento se collegato ma inattivo (min)
};
static Settings cfg;
static Preferences prefs;
static char defaultName[13];

// ------------------------------------------------------------------ stato
static NimBLEServer*         server   = nullptr;
static NimBLECharacteristic* evtChr   = nullptr;
static NimBLECharacteristic* battChr  = nullptr;
static NimBLECharacteristic* statusChr = nullptr;
static NimBLECharacteristic* configChr = nullptr;

static volatile bool connected      = false;
static volatile bool justConnected  = false;
static volatile bool justDisconnect = false;
static bool everConnected = false;

// Messaggi ricevuti dal telefono: li scrive il task BLE, li usa il loop.
static portMUX_TYPE rxMux = portMUX_INITIALIZER_UNLOCKED;
static char rxBuf[192];
static volatile bool rxReady = false;
static char cfgBuf[192];
static volatile bool cfgReady = false;

static uint8_t  seqNo = 0;
static bool     displayOn = false;
static uint32_t displayOffAt = 0;
static volatile uint32_t advSince = 0;  // aggiornato anche dal task BLE alla disconnessione
static bool     advFast = true;
static bool     advertising = false;
static uint32_t lastActivity = 0;
static bool     idleWarned = false;
static uint32_t lastBattery = 0;
static uint32_t lastBattCheck = 0;
static uint8_t  battEmptyCount = 0;
static uint32_t displayOnSince = 0;   // per contare quanto resta acceso il display (diagnostica consumi)
static uint32_t displayOnTotalMs = 0;
static uint32_t nextBlink = 0;
static bool     blinkShown = false;
static bool     countdownBeeped = false;

// "Identifica": lampeggio a tutto schermo col colore del giocatore
static uint32_t identifyUntil = 0;
static uint32_t identifyNext = 0;
static bool     identifyPhase = false;
static uint16_t identifyColor = C_BALL;
static char     identifyL1[24];
static char     identifyL2[32];

// Cicalino: acceso solo mentre suona
static bool     spkOn = false;
static uint32_t spkOffAt = 0;

// ------------------------------------------------------------------ cicalino
static void beep(float freq, uint32_t ms) {
  if (cfg.vol == 0) return;
  if (!spkOn) {
    if (!M5.Speaker.begin()) return;
    spkOn = true;
  }
  // volume percepito ~ quadratico: 50 % = 64/255 (il valore predefinito di M5Unified)
  uint32_t v = (uint32_t)cfg.vol * cfg.vol * 255UL / 10000UL;
  M5.Speaker.setVolume((uint8_t)constrain(v, 8UL, 255UL));
  M5.Speaker.tone(freq, ms, 0, false);  // canale 0 senza interrompere: due bip di fila suonano uno dopo l'altro
  spkOffAt = millis() + ms + 1500;      // l'amplificatore resta acceso 1,5 s: più toni di fila non lo riaccendono
}

static void speakerOff() {
  M5.Speaker.end();  // spegne l'amplificatore
  // M5Unified lascia acceso il codec ES8311: si spengono DAC, uscita e parte analogica
  // (M5.Speaker.begin() riscrive questi stessi registri al prossimo bip).
  static const uint8_t ES8311 = 0x18;
  M5.In_I2C.writeRegister8(ES8311, 0x32, 0x00, 400000);  // volume DAC a zero
  M5.In_I2C.writeRegister8(ES8311, 0x12, 0x02, 400000);  // DAC spento
  M5.In_I2C.writeRegister8(ES8311, 0x13, 0x00, 400000);  // uscita spenta
  M5.In_I2C.writeRegister8(ES8311, 0x0D, 0xFC, 400000);  // parte analogica spenta
  M5.In_I2C.writeRegister8(ES8311, 0x00, 0x00, 400000);  // macchina a stati spenta
  spkOn = false;
}

static void speakerIdle(uint32_t now) {
  if (spkOn && (int32_t)(now - spkOffAt) >= 0 && !M5.Speaker.isPlaying()) speakerOff();
}

// ------------------------------------------------------------------ display
static void displayWake() {
  if (!displayOn) {
    M5.Display.wakeup();
    M5.Display.setBrightness((uint8_t)(cfg.bri * 255 / 100));
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
static void drawFit(const char* txt, int y, const lgfx::IFont* const* fonts, int nFonts, uint16_t color, int maxW = 232, uint16_t bg = C_BG) {
  M5.Display.setTextColor(color, bg);
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

static void drawMessage(const char* l1, const char* l2, uint16_t color = C_TEXT, uint16_t bg = C_BG, uint16_t color2 = C_DIM) {
  displayWake();
  M5.Display.fillScreen(bg);
  if (l2 && l2[0]) {
    drawFit(l1, 45, BIG_FONTS, 4, color, 232, bg);
    drawFit(l2, 100, SMALL_FONTS, 3, color2, 232, bg);
  } else {
    drawFit(l1, M5.Display.height() / 2, BIG_FONTS, 4, color, 232, bg);
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

// ------------------------------------------------------------------ impostazioni
static void clampSettings() {
  cfg.bri     = constrain(cfg.bri, 5, 100);
  cfg.pointS  = constrain(cfg.pointS, 0, 10);
  cfg.vol     = constrain(cfg.vol, 0, 100);
  cfg.pairS   = constrain(cfg.pairS, 15, 600);
  cfg.lostS   = constrain(cfg.lostS, 30, 1800);
  cfg.idleMin = constrain(cfg.idleMin, 5, 120);
  if (!cfg.name[0]) strlcpy(cfg.name, defaultName, sizeof(cfg.name));
}

static void loadSettings() {
  prefs.begin("tsm", false);
  String n = prefs.getString("name", "");
  strlcpy(cfg.name, n.c_str(), sizeof(cfg.name));
  cfg.bri     = prefs.getUChar("bri", 20);
  cfg.pointS  = prefs.getUChar("pt", 3);
  cfg.vol     = prefs.getUChar("vol", 50);
  cfg.flip    = prefs.getBool("flip", false);
  cfg.pairS   = prefs.getUShort("pair", 30);
  cfg.lostS   = prefs.getUShort("lost", 180);
  cfg.idleMin = prefs.getUShort("idle", 30);
  clampSettings();
}

static void saveSettings() {
  // Il nome predefinito non si salva: così resta legato al chip anche dopo un ripristino.
  prefs.putString("name", strcmp(cfg.name, defaultName) == 0 ? "" : cfg.name);
  prefs.putUChar("bri", cfg.bri);
  prefs.putUChar("pt", cfg.pointS);
  prefs.putUChar("vol", cfg.vol);
  prefs.putBool("flip", cfg.flip);
  prefs.putUShort("pair", cfg.pairS);
  prefs.putUShort("lost", cfg.lostS);
  prefs.putUShort("idle", cfg.idleMin);
}

static void publishConfig(bool notify) {
  char buf[128];
  snprintf(buf, sizeof(buf), "fw=%s;name=%s;bri=%u;pt=%u;vol=%u;flip=%u;pair=%u;lost=%u;idle=%u",
           FW_VERSION, cfg.name, cfg.bri, cfg.pointS, cfg.vol, cfg.flip ? 1 : 0, cfg.pairS, cfg.lostS, cfg.idleMin);
  configChr->setValue((const uint8_t*)buf, strlen(buf));
  if (notify && connected) configChr->notify();
}

static void applyName() {
  NimBLEDevice::setDeviceName(cfg.name);
  NimBLEAdvertisementData scanData;
  scanData.setName(cfg.name);
  NimBLEAdvertising* adv = NimBLEDevice::getAdvertising();
  bool wasOn = adv->isAdvertising();
  if (wasOn) adv->stop();
  adv->setScanResponseData(scanData);
  if (wasOn) adv->start();
}

// "chiave=valore;chiave=valore": si applicano solo le chiavi presenti, le altre restano come sono.
static void handleConfig(char* text) {
  char oldName[13];
  strlcpy(oldName, cfg.name, sizeof(oldName));
  const uint8_t oldVol = cfg.vol;
  for (char* part = strtok(text, ";"); part; part = strtok(nullptr, ";")) {
    char* eq = strchr(part, '=');
    if (!eq) continue;
    *eq = 0;
    const char* k = part;
    const char* v = eq + 1;
    if (!strcmp(k, "name")) {
      char clean[13];
      int j = 0;
      for (const char* p = v; *p && j < 12; p++) {
        if (*p >= 32 && *p <= 126 && *p != '|' && *p != ';' && *p != '=') clean[j++] = *p;
      }
      clean[j] = 0;
      strlcpy(cfg.name, clean, sizeof(cfg.name));
    }
    else if (!strcmp(k, "bri"))  cfg.bri     = atoi(v);
    else if (!strcmp(k, "pt"))   cfg.pointS  = atoi(v);
    else if (!strcmp(k, "vol"))  cfg.vol     = atoi(v);
    else if (!strcmp(k, "flip")) cfg.flip    = atoi(v) != 0;
    else if (!strcmp(k, "pair")) cfg.pairS   = atoi(v);
    else if (!strcmp(k, "lost")) cfg.lostS   = atoi(v);
    else if (!strcmp(k, "idle")) cfg.idleMin = atoi(v);
  }
  clampSettings();
  saveSettings();
  if (strcmp(oldName, cfg.name) != 0) applyName();
  M5.Display.setRotation(cfg.flip ? 3 : 1);
  if (displayOn) M5.Display.setBrightness((uint8_t)(cfg.bri * 255 / 100));
  publishConfig(true);
  // Anteprima: nome con la nuova luminosità e il nuovo verso, e un bip col nuovo volume.
  identifyUntil = 0;
  drawMessage(cfg.name, TXT_SAVED, C_BALL);
  showFor(2000);
  if (cfg.vol > 0 && cfg.vol != oldVol) beep(2700, 80);
}

// ------------------------------------------------------------------ messaggi dal telefono
// P|mio|avversario|servizio|intestazione   G|mieiG|loroG|mieiS|loroS|intestazione   M|riga1|riga2|secondi
// I|riga1|riga2|secondi|RRGGBB (identifica)   O|riga1|riga2 (spegni)
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

static uint16_t rgb565(const char* hex) {
  uint32_t rgb = strtoul(hex, nullptr, 16);
  return (uint16_t)(((rgb >> 8) & 0xF800) | ((rgb >> 5) & 0x07E0) | ((rgb >> 3) & 0x001F));
}

static void powerOff(const char* l1, const char* why, uint8_t reason);

static void handleMessage(char* msg) {
  char* f[8] = { 0 };
  int n = splitFields(msg, f, 8);
  if (n < 1 || !f[0][0]) return;
  const char type = f[0][0];
  if (type != 'I') identifyUntil = 0;  // un nuovo messaggio interrompe "Identifica"
  switch (type) {
    case 'P':
      if (n >= 5 && cfg.pointS > 0) { drawPoint(f[1], f[2], atoi(f[3]), f[4]); showFor(cfg.pointS * 1000UL); }
      break;
    case 'G':
      if (n >= 6 && cfg.pointS > 0) { drawGames(atoi(f[1]), atoi(f[2]), atoi(f[3]), atoi(f[4]), f[5]); showFor((cfg.pointS + 2) * 1000UL); }
      break;
    case 'M':
      if (n >= 4) {
        drawMessage(f[1], f[2]);
        int s = atoi(f[3]);
        showFor((uint32_t)constrain(s, 1, 30) * 1000UL);
      }
      break;
    case 'I':
      if (n >= 4) {
        strlcpy(identifyL1, f[1], sizeof(identifyL1));
        strlcpy(identifyL2, f[2], sizeof(identifyL2));
        identifyColor = (n >= 5 && f[4][0]) ? rgb565(f[4]) : C_BALL;
        identifyUntil = millis() + (uint32_t)constrain(atoi(f[3]), 1, 30) * 1000UL;
        identifyNext = millis();
        identifyPhase = false;
      }
      break;
    case 'O':
      powerOff(n >= 2 && f[1][0] ? f[1] : TXT_POWER_OFF, n >= 3 ? f[2] : "", OFF_APP);
      break;
  }
}

// Lampeggio: colore pieno con testo nero, poi nero con testo colorato; un bip a ogni lampo.
static void identifyStep(uint32_t now) {
  if (!identifyUntil) return;
  if ((int32_t)(now - identifyUntil) >= 0) {
    identifyUntil = 0;
    drawMessage(identifyL1, identifyL2, identifyColor);
    showFor(1500);
    return;
  }
  if ((int32_t)(now - identifyNext) < 0) return;
  identifyNext = now + IDENTIFY_FLASH_MS;
  identifyPhase = !identifyPhase;
  if (identifyPhase) {
    drawMessage(identifyL1, identifyL2, C_BG, identifyColor, C_BG);
    beep(2400, 60);
  } else {
    drawMessage(identifyL1, identifyL2, identifyColor);
  }
  showFor(IDENTIFY_FLASH_MS * 3);
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

static void copyValue(NimBLECharacteristic* c, char* buf, size_t size, volatile bool& flag) {
  NimBLEAttValue v = c->getValue();
  size_t len = v.length();
  if (len >= size) len = size - 1;
  portENTER_CRITICAL(&rxMux);
  memcpy(buf, v.data(), len);
  buf[len] = 0;
  flag = true;
  portEXIT_CRITICAL(&rxMux);
}

class DisplayCallbacks : public NimBLECharacteristicCallbacks {
  void onWrite(NimBLECharacteristic* c, NimBLEConnInfo& info) override { copyValue(c, rxBuf, sizeof(rxBuf), rxReady); }
};

class ConfigCallbacks : public NimBLECharacteristicCallbacks {
  void onWrite(NimBLECharacteristic* c, NimBLEConnInfo& info) override { copyValue(c, cfgBuf, sizeof(cfgBuf), cfgReady); }
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

static void sendEvent(uint8_t type, uint8_t extra = 0) {
  if (!connected || !evtChr) return;
  uint8_t data[3] = { type, ++seqNo, extra };
  evtChr->setValue(data, 3);
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
  snprintf(defaultName, sizeof(defaultName), "TSM-%04X", (unsigned)((mac >> 32) & 0xFFFF));
  loadSettings();
  NimBLEDevice::init(cfg.name);
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
  configChr = svc->createCharacteristic(CONFIG_UUID, NIMBLE_PROPERTY::READ | NIMBLE_PROPERTY::WRITE | NIMBLE_PROPERTY::NOTIFY);
  configChr->setCallbacks(new ConfigCallbacks());

  NimBLEService* bas = server->createService("180F");
  battChr = bas->createCharacteristic("2A19", NIMBLE_PROPERTY::READ | NIMBLE_PROPERTY::NOTIFY);
  server->start();  // registra i servizi GATT (in NimBLE 2.x si avvia il server, non i singoli servizi)
  updateBattery(false);
  publishConfig(false);

  // Pacchetto di advertising: flag + UUID del servizio (l'app filtra la ricerca su questo);
  // il nome va nella risposta allo scan per stare nei 31 byte.
  NimBLEAdvertisementData advData;
  advData.setFlags(BLE_HS_ADV_F_DISC_GEN | BLE_HS_ADV_F_BREDR_UNSUP);
  advData.addServiceUUID(NimBLEUUID(SERVICE_UUID));
  NimBLEAdvertisementData scanData;
  scanData.setName(cfg.name);
  NimBLEAdvertising* adv = NimBLEDevice::getAdvertising();
  adv->setAdvertisementData(advData);
  adv->setScanResponseData(scanData);
}

// ------------------------------------------------------------------ spegnimento
static void powerOff(const char* l1, const char* why, uint8_t reason) {
  // L'app deve sapere che il braccialetto si è spento (e perché), non che l'ha perso.
  if (connected) {
    sendEvent(EVT_POWER_OFF, reason);
    delay(300);  // lascia partire la notifica
  }
  drawMessage(l1, why, C_ORANGE);
  beep(1200, 120);
  delay(250);
  beep(700, 250);
  delay(1250);
  if (connected) {
    for (uint16_t h : server->getPeerDevices()) server->disconnect(h);
    delay(200);
  }
  if (spkOn) speakerOff();
  M5.Display.setBrightness(0);
  M5.Display.sleep();
  M5.Power.powerOff();  // col cavo USB collegato il PM1 può non togliere corrente: allora deep sleep
  while (true) delay(1000);
}

// ------------------------------------------------------------------ setup / loop
void setup() {
  setCpuFrequencyMhz(80);  // il minimo che tiene in piedi il Bluetooth: consumo molto più basso di 240 MHz

  auto cfgM5 = M5.config();
  cfgM5.clear_display = true;
  cfgM5.output_power  = false;   // niente 5V sul connettore Grove
  cfgM5.internal_imu  = false;
  cfgM5.internal_rtc  = false;
  cfgM5.internal_spk  = true;    // configurato ma spento: si accende solo per i bip
  cfgM5.internal_mic  = false;
  cfgM5.led_brightness = 0;
  M5.begin(cfgM5);

  setupBle();  // carica anche le impostazioni

  M5.Display.setRotation(cfg.flip ? 3 : 1);
  M5.Display.setBrightness((uint8_t)(cfg.bri * 255 / 100));
  displayOn = true;
  displayOnSince = millis();
  M5.BtnA.setHoldThresh(KEY1_HOLD_MS);
  M5.BtnB.setHoldThresh(KEY2_HOLD_MS);

  drawMessage("TSM BAND", cfg.name, C_BALL);
  delay(1200);
  displaySleep();

  advSince = millis();
  startAdvertising(true);
  lastActivity = millis();
  nextBlink = millis();
}

// Un tasto premuto senza telefono: rimanda lo spegnimento e torna all'advertising veloce.
static void keyWhileDisconnected(uint32_t now) {
  advSince = now;
  if (!advFast) startAdvertising(true);
  drawMessage(TXT_NO_LINK, cfg.name, C_RED);
  showFor(1500);
  beep(400, 150);
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
    idleWarned = false;
    drawMessage(TXT_PAIRED, cfg.name, C_BALL);
    showFor(PAIRED_MSG_MS);
    beep(2000, 70);
    beep(2800, 90);
    updateBattery(true);
    publishConfig(true);
  }
  if (justDisconnect) {
    justDisconnect = false;
    advSince = now;
    identifyUntil = 0;
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
    idleWarned = false;
  }
  if (cfgReady) {
    char local[sizeof(cfgBuf)];
    portENTER_CRITICAL(&rxMux);
    memcpy(local, cfgBuf, sizeof(cfgBuf));
    cfgReady = false;
    portEXIT_CRITICAL(&rxMux);
    handleConfig(local);
    lastActivity = now;
    idleWarned = false;
  }

  // --- tasti
  if (M5.BtnA.wasClicked()) {
    lastActivity = now;
    idleWarned = false;
    if (connected) {
      sendEvent(EVT_POINT);
      beep(2700, 40);
    } else {
      keyWhileDisconnected(now);
    }
  }
  if (M5.BtnA.wasHold()) {
    lastActivity = now;
    idleWarned = false;
    if (!connected) advSince = now;
    drawBattery();
    sendEvent(EVT_BATTERY);
  }
  if (M5.BtnB.wasClicked()) {
    lastActivity = now;
    idleWarned = false;
    if (connected) {
      sendEvent(EVT_UNDO);
      beep(1800, 40);
    } else {
      keyWhileDisconnected(now);
    }
  }
  if (M5.BtnB.wasHold()) powerOff(TXT_POWER_OFF, "", OFF_KEY);

  // --- advertising, lampeggio e spegnimento automatico senza telefono
  if (!connected && !justDisconnect) {
    if (advFast && now - advSince > FAST_ADV_MS) startAdvertising(false);
    // all'accensione vale il tempo di pairing, dopo una disconnessione quello di telefono perso
    const uint32_t limit = (uint32_t)(everConnected ? cfg.lostS : cfg.pairS) * 1000UL;
    const int32_t left = (int32_t)limit - (int32_t)(millis() - advSince);
    if (left <= 0) powerOff(TXT_POWER_OFF, TXT_NO_PHONE, OFF_TIMEOUT);
    const bool countdown = left <= (int32_t)COUNTDOWN_MS;
    if (!countdown) countdownBeeped = false;
    if ((int32_t)(now - nextBlink) >= 0 && (!displayOn || blinkShown)) {
      if (countdown) {
        char l2[24];
        snprintf(l2, sizeof(l2), "%lds - PREMI UN TASTO", (long)((left + 999) / 1000));
        drawMessage(TXT_POWER_OFF, l2, C_ORANGE);
        if (!countdownBeeped) {  // un solo bip all'inizio del conto alla rovescia
          countdownBeeped = true;
          beep(1000, 80);
        }
      } else {
        drawMessage(everConnected ? TXT_RECONNECT : TXT_PAIRING, cfg.name, everConnected ? C_ORANGE : C_BALL);
      }
      showFor(BLINK_ON_MS);
      blinkShown = true;
      nextBlink = now + (countdown ? 1000 : BLINK_PERIOD_MS);
    }
  } else if (connected) {
    blinkShown = false;
    // --- spegnimento per inattività, con avviso 30 s prima
    const uint32_t idleMs = (uint32_t)cfg.idleMin * 60000UL;
    if (now - lastActivity > idleMs) powerOff(TXT_POWER_OFF, TXT_IDLE, OFF_IDLE);
    if (!idleWarned && now - lastActivity > idleMs - IDLE_WARN_MS) {
      idleWarned = true;
      drawMessage(TXT_IDLE, TXT_HOLD_KEY1, C_ORANGE);
      showFor(5000);
      beep(1000, 150);
    }
  }

  // --- "Identifica"
  identifyStep(now);

  // --- batteria e stato ogni minuto
  if (now - lastBattery > STATUS_PERIOD_MS) {
    lastBattery = now;
    updateBattery(true);
  }

  // --- batteria scarica: meglio spegnersi che restare acceso a metà (due letture di fila, in carica no)
  if (now - lastBattCheck > BATT_CHECK_MS) {
    lastBattCheck = now;
    const int mv = M5.Power.getBatteryVoltage();
    const bool charging = M5.Power.isCharging() == m5::Power_Class::is_charging;
    if (!charging && mv > 2500 && mv < BATT_EMPTY_MV) {
      if (++battEmptyCount >= 2) powerOff(TXT_POWER_OFF, TXT_EMPTY, OFF_BATTERY);
    } else {
      battEmptyCount = 0;
    }
  }

  // --- spegnimento display a tempo
  if (displayOn && (int32_t)(now - displayOffAt) >= 0) {
    displaySleep();
    blinkShown = !connected;
  }

  speakerIdle(now);

  delay(20);  // 50 Hz per i tasti; nel resto del tempo la CPU resta ferma in idle
}
