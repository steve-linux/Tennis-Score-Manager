/*
  Tennis Score Manager - firmware braccialetto M5StickS3
  --------------------------------------------------------
  KEY1 corto  : punto a chi indossa il braccialetto
  KEY1 lungo  : mostra la batteria (tiene anche acceso il braccialetto se è inattivo)
  KEY2 corto  : annulla l'ultimo punto
  KEY2 lungo  : spegne il braccialetto (riaccensione: tasto laterale, un clic)
  Se non è collegato, un tasto qualsiasi rimanda lo spegnimento automatico.

  Ricarica (cavo USB): il braccialetto non si spegne da solo finché è alimentato e mostra la
  schermata di carica (percentuale, tensioni, da quanto è in carica, fine stimata) per 30 s,
  poi un'occhiata ogni 10 s; un tasto qualsiasi la riaccende. A carica completa resta spento.
  Da collegato al telefono la partita ha la precedenza: solo un breve messaggio "IN CARICA".

  Impostazioni (dall'app, menu "Impostazioni braccialetto"), salvate nel braccialetto:
  nome, luminosità, durata del punteggio, volume, display capovolto e i tre tempi di
  spegnimento automatico (nessun telefono all'accensione, telefono perso, inattività).
  Lingua dei testi (dalla 2.2): la manda l'app da sola, uguale alla sua; resta salvata anche
  per i messaggi da scollegato (ricarica, ricerca del telefono, spegnimento; dalla 2.2.1 anche
  il conto alla rovescia prima dello spegnimento).

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

#define FW_VERSION "2.2.1"

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
static const uint32_t POWER_POLL_MS        = 1000;                 // controllo cavo USB e stato di carica
static const int      USB_MIN_MV           = 4000;                 // sopra questa tensione il cavo USB è collegato
static const int      CHG_OFFSET_MV        = 100;                  // in carica la tensione misurata è più alta di quella a riposo
static const uint32_t CHARGE_SHOW_MS       = 30000;                // schermata di carica accesa dopo l'inserimento o un tasto
static const uint32_t CHARGE_GLANCE_EVERY  = 10000;                // poi un'occhiata ogni 10 s...
static const uint32_t CHARGE_GLANCE_MS     = 1500;                 // ...di 1,5 s
static const uint32_t FULL_DEBOUNCE_MS     = 20000;                // CHG_STAT spento da 20 s = carica completa
static const int      CV_FULL_MV           = 4180;                 // riserva: 45 min sopra 4,18 V = carica completa
static const uint32_t CV_FULL_MS           = 45UL * 60UL * 1000UL;

// Testi mostrati dal braccialetto (solo ASCII, maiuscolo), una riga per lingua: vedi TXT[] più sotto.
enum : uint8_t { L_IT, L_EN, L_FR, L_DE, L_ES, L_PT, N_LANG };
static const char* const LANG_CODES[N_LANG] = { "it", "en", "fr", "de", "es", "pt" };
enum : uint8_t {
  T_PAIRING, T_PAIRED, T_RECONNECT, T_NO_PHONE, T_POWER_OFF, T_BATTERY, T_CHARGING, T_NO_LINK, T_IDLE,
  T_HOLD_KEY1, T_EMPTY, T_SAVED, T_FULL, T_USB_POWER, T_USB_OUT, T_KEEPS_CHG,
  T_GAMES, T_SETS, T_CHARGED_IN, T_SINCE, T_LEFT, T_PRESS_KEY, N_TXT
};
#define TXT_PAIRING    txt(T_PAIRING)
#define TXT_PAIRED     txt(T_PAIRED)
#define TXT_RECONNECT  txt(T_RECONNECT)
#define TXT_NO_PHONE   txt(T_NO_PHONE)
#define TXT_POWER_OFF  txt(T_POWER_OFF)
#define TXT_BATTERY    txt(T_BATTERY)
#define TXT_CHARGING   txt(T_CHARGING)
#define TXT_NO_LINK    txt(T_NO_LINK)
#define TXT_IDLE       txt(T_IDLE)
#define TXT_HOLD_KEY1  txt(T_HOLD_KEY1)
#define TXT_EMPTY      txt(T_EMPTY)
#define TXT_SAVED      txt(T_SAVED)
#define TXT_FULL       txt(T_FULL)
#define TXT_USB_POWER  txt(T_USB_POWER)
#define TXT_USB_OUT    txt(T_USB_OUT)
#define TXT_KEEPS_CHG  txt(T_KEEPS_CHG)

// ------------------------------------------------------------------ protocollo (uguale all'app)
#define SERVICE_UUID "7a1e0001-5c3b-4f6e-9d2a-3e7b1c9a0f10"
#define EVENT_UUID   "7a1e0002-5c3b-4f6e-9d2a-3e7b1c9a0f10"
#define DISPLAY_UUID "7a1e0003-5c3b-4f6e-9d2a-3e7b1c9a0f10"
#define STATUS_UUID  "7a1e0004-5c3b-4f6e-9d2a-3e7b1c9a0f10"  // "mv=3987;chg=0;up=1234;dsp=56;usb=0;full=0;pct=71" (consumi e carica)
#define CONFIG_UUID  "7a1e0005-5c3b-4f6e-9d2a-3e7b1c9a0f10"  // impostazioni: "fw=2.2;name=...;bri=20;pt=3;vol=50;flip=0;pair=30;lost=180;idle=30;lang=it"
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
  uint8_t  lang;       // lingua dei testi (L_IT...), la imposta l'app
};
static Settings cfg;
static Preferences prefs;
static char defaultName[13];

// Stesso ordine dell'enum T_*. T_CHARGED_IN, T_SINCE, T_LEFT e T_PRESS_KEY sono formati di snprintf
// (%s durata, %d minuti, %ld secondi al conto alla rovescia).
static const char* const TXT[N_LANG][N_TXT] = {
  { "PAIRING...", "PAIRING OK", "RICONNESSIONE", "NESSUN TELEFONO", "SPEGNIMENTO", "BATTERIA", "IN CARICA",
    "NON CONNESSO", "INATTIVO", "TIENI PREMUTO KEY1", "BATTERIA SCARICA", "IMPOSTAZIONI OK", "CARICA COMPLETA",
    "ALIMENTATO DA USB", "USB SCOLLEGATO", "LA CARICA CONTINUA",
    "GAME", "SET", "CARICATA IN %s", "DA %s", "FINE ~%d MIN", "%lds - PREMI UN TASTO" },
  { "PAIRING...", "PAIRED", "RECONNECTING", "NO PHONE", "POWERING OFF", "BATTERY", "CHARGING",
    "NOT CONNECTED", "IDLE", "HOLD KEY1", "BATTERY EMPTY", "SETTINGS SAVED", "FULLY CHARGED",
    "USB POWERED", "USB UNPLUGGED", "STILL CHARGING",
    "GAMES", "SETS", "CHARGED IN %s", "%s IN", "~%d MIN LEFT", "%lds - PRESS ANY KEY" },
  { "APPAIRAGE...", "APPAIRE", "RECONNEXION", "AUCUN TELEPHONE", "EXTINCTION", "BATTERIE", "EN CHARGE",
    "NON CONNECTE", "INACTIF", "MAINTENIR KEY1", "BATTERIE VIDE", "REGLAGES OK", "CHARGE TERMINEE",
    "ALIMENTE PAR USB", "USB DEBRANCHE", "LA CHARGE CONTINUE",
    "JEUX", "SETS", "CHARGEE EN %s", "DEPUIS %s", "FIN ~%d MIN", "%lds - APPUYER SUR UNE TOUCHE" },
  { "KOPPELN...", "GEKOPPELT", "VERBINDE NEU", "KEIN TELEFON", "AUSSCHALTEN", "AKKU", "LAEDT",
    "NICHT VERBUNDEN", "INAKTIV", "KEY1 GEDRUECKT HALTEN", "AKKU LEER", "EINSTELLUNGEN OK", "VOLL GELADEN",
    "USB-STROM", "USB GETRENNT", "LAEDT WEITER",
    "SPIELE", "SAETZE", "GELADEN IN %s", "SEIT %s", "ENDE ~%d MIN", "%lds - TASTE DRUECKEN" },
  { "EMPAREJANDO...", "EMPAREJADO", "RECONECTANDO", "SIN TELEFONO", "APAGANDO", "BATERIA", "CARGANDO",
    "NO CONECTADO", "INACTIVO", "MANTEN PULSADO KEY1", "BATERIA AGOTADA", "AJUSTES OK", "CARGA COMPLETA",
    "ALIMENTADO POR USB", "USB DESCONECTADO", "SIGUE CARGANDO",
    "JUEGOS", "SETS", "CARGADA EN %s", "HACE %s", "FIN ~%d MIN", "%lds - PULSA UN BOTON" },
  { "PAREANDO...", "PAREADO", "RECONECTANDO", "SEM TELEFONE", "DESLIGANDO", "BATERIA", "CARREGANDO",
    "SEM CONEXAO", "INATIVO", "SEGURE KEY1", "BATERIA VAZIA", "AJUSTES OK", "CARGA COMPLETA",
    "ALIMENTADO POR USB", "USB DESCONECTADO", "CONTINUA CARREGANDO",
    "JOGOS", "SETS", "CARREGADA EM %s", "HA %s", "FIM ~%d MIN", "%lds - APERTE UM BOTAO" },
};

static const char* txt(uint8_t id) { return TXT[cfg.lang < N_LANG ? cfg.lang : L_IT][id]; }

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

// Ricarica: stato letto ogni secondo dal PM1 (tensione USB) e dal caricabatterie (CHG_STAT)
enum ChargeState : uint8_t { CHG_NONE, CHG_ACTIVE, CHG_FULL, CHG_IDLE };  // IDLE = USB ma non carica (né completa)
static bool     usbOn = false;
static uint8_t  usbFlips = 0;          // letture di fila diverse dallo stato attuale (anti-rimbalzo)
static uint8_t  chgState = CHG_NONE;
static uint32_t usbSince = 0;          // inizio della carica
static uint32_t fullAt = 0;            // quando è diventata completa
static uint32_t notChgSince = 0;
static uint32_t cvSince = 0;
static int      chgPct = 0;            // percentuale mostrata in carica: non scende mai, 100 solo a carica completa
static int      lastMv = 0;
static int      restMv = 0;            // ultima tensione senza cavo: base della percentuale all'inserimento
static int      lastVbus = 0;
static uint32_t lastPowerPoll = 0;
static uint32_t chargeShowUntil = 0;   // schermata di carica accesa fino a...
static uint32_t chargeNextGlance = 0;
static uint32_t lastChargeDraw = 0;
static int      pctHist[11];           // percentuale minuto per minuto (ultimi 10 minuti) per stimare la fine
static uint8_t  pctHistN = 0;
static uint32_t lastPctSample = 0;
static bool     chargeScreen = false;  // sullo schermo c'è la schermata di carica (si può aggiornare)

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
static const lgfx::IFont* const TINY_FONTS[]  = { &fonts::Font2, &fonts::Font0 };  // righe di dettaglio: Font0 se non ci stanno

static void drawMessage(const char* l1, const char* l2, uint16_t color = C_TEXT, uint16_t bg = C_BG, uint16_t color2 = C_DIM) {
  displayWake();
  chargeScreen = false;
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
  chargeScreen = false;
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
  chargeScreen = false;
  M5.Display.fillScreen(C_BG);
  const int w = M5.Display.width();
  if (header && header[0]) drawFit(header, 14, SMALL_FONTS, 3, C_ORANGE);
  char buf[16];
  M5.Display.setTextDatum(middle_left);
  M5.Display.setFont(&fonts::FreeSansBold12pt7b);
  M5.Display.setTextColor(C_DIM, C_BG);
  M5.Display.drawString(txt(T_GAMES), 8, 55);
  M5.Display.drawString(txt(T_SETS), 8, 108);
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

// ------------------------------------------------------------------ batteria e ricarica
// Curva di scarica tipica della LiPo (mV -> %), la stessa dell'app (BatteryModel.kt): più fedele della
// retta 3,30-4,15 V di M5Unified, così braccialetto e telefono mostrano la stessa percentuale.
static const int16_t SOC_CURVE[][2] = {
  {4200, 100}, {4150, 95}, {4110, 90}, {4080, 85}, {4020, 80}, {3980, 75}, {3950, 70},
  {3910, 65}, {3870, 60}, {3850, 55}, {3840, 50}, {3820, 45}, {3800, 40}, {3790, 35},
  {3770, 30}, {3750, 25}, {3730, 20}, {3710, 15}, {3690, 10}, {3610, 5}, {3270, 0},
};

static int socFromMv(int mv) {
  const int n = sizeof(SOC_CURVE) / sizeof(SOC_CURVE[0]);
  if (mv >= SOC_CURVE[0][0]) return 100;
  if (mv <= SOC_CURVE[n - 1][0]) return 0;
  for (int i = 0; i < n - 1; i++) {
    const int vHi = SOC_CURVE[i][0], pHi = SOC_CURVE[i][1];
    const int vLo = SOC_CURVE[i + 1][0], pLo = SOC_CURVE[i + 1][1];
    if (mv >= vLo && mv <= vHi) return pLo + (mv - vLo) * (pHi - pLo) / (vHi - vLo);
  }
  return 0;
}

// Percentuale da mostrare: col cavo USB quella della carica, altrimenti dalla tensione (-1 = lettura fallita).
static int batteryPct() {
  if (usbOn) return chgPct;
  const int mv = M5.Power.getBatteryVoltage();
  return mv > 2500 ? socFromMv(mv) : -1;
}

// "42 MIN" oppure "1H 25"
static void fmtDuration(char* buf, size_t n, uint32_t ms) {
  const unsigned long min = ms / 60000UL;
  if (min < 60) snprintf(buf, n, "%lu MIN", min);
  else snprintf(buf, n, "%luH %02lu", min / 60, min % 60);
}

// Minuti alla fine della carica dal ritmo degli ultimi 10 minuti (-1 = non ancora stimabile).
// Il PM1 non misura la corrente di carica, quindi è una stima: arrotondata a 5 minuti.
static int chargeMinutesLeft() {
  if (chgState != CHG_ACTIVE || pctHistN < 11) return -1;
  const int gained = chgPct - pctHist[0];  // pctHist[0] = 10 minuti fa
  if (gained <= 0) return -1;
  const int left = (100 - chgPct) * 10 / gained;
  return constrain(((left + 4) / 5) * 5, 5, 300);
}

// Pila con livello di riempimento e, in carica, un fulmine.
static void drawBatteryIcon(int x, int y, int w, int h, int pct, uint16_t fill, bool bolt) {
  M5.Display.drawRoundRect(x, y, w, h, 7, C_TEXT);
  M5.Display.drawRoundRect(x + 1, y + 1, w - 2, h - 2, 6, C_TEXT);
  M5.Display.fillRoundRect(x + w, y + h / 2 - 9, 6, 18, 2, C_TEXT);  // polo positivo
  const int fw = (w - 10) * constrain(pct, 0, 100) / 100;
  if (fw > 0) M5.Display.fillRoundRect(x + 5, y + 5, fw, h - 10, 3, fill);
  if (bolt) {
    const int cx = x + w / 2, cy = y + h / 2;
    M5.Display.fillTriangle(cx + 5, cy - 17, cx - 9, cy + 3, cx + 2, cy + 3, C_TEXT);
    M5.Display.fillTriangle(cx - 5, cy + 17, cx + 9, cy - 3, cx - 2, cy - 3, C_TEXT);
  }
}

// Schermata di carica: nome e tensione USB, pila, percentuale, stato, tensione della batteria e tempi.
static void drawCharge() {
  displayWake();
  chargeScreen = true;
  lastChargeDraw = millis();
  M5.Display.fillScreen(C_BG);
  const int w = M5.Display.width();
  const bool full = chgState == CHG_FULL;
  const bool active = chgState == CHG_ACTIVE;
  char buf[48];
  char t[16];

  snprintf(buf, sizeof(buf), "%s   USB %d.%02dV", cfg.name, lastVbus / 1000, (lastVbus % 1000) / 10);
  drawFit(buf, 10, SMALL_FONTS + 2, 1, C_DIM);

  const uint16_t fill = full ? C_BALL : (active ? (chgPct < 20 ? C_ORANGE : C_BALL) : C_DIM);
  drawBatteryIcon(12, 29, 92, 52, chgPct, fill, active);
  snprintf(buf, sizeof(buf), "%d%%", chgPct);
  M5.Display.setFont(&fonts::FreeSansBold24pt7b);
  M5.Display.setTextSize(1);
  M5.Display.setTextDatum(middle_center);
  M5.Display.setTextColor(full ? C_BALL : C_TEXT, C_BG);
  M5.Display.drawString(buf, (118 + w) / 2, 57);

  drawFit(full ? TXT_FULL : (active ? TXT_CHARGING : TXT_USB_POWER), 99, SMALL_FONTS, 3, full ? C_BALL : (active ? C_TEXT : C_DIM));

  char since[32];
  char left[24] = "";
  if (full) {
    fmtDuration(t, sizeof(t), fullAt - usbSince);
    snprintf(since, sizeof(since), txt(T_CHARGED_IN), t);
  } else {
    fmtDuration(t, sizeof(t), millis() - usbSince);
    snprintf(since, sizeof(since), txt(T_SINCE), t);
    const int min = chargeMinutesLeft();
    if (min > 0) snprintf(left, sizeof(left), txt(T_LEFT), min);
  }
  if (left[0]) snprintf(buf, sizeof(buf), "%d.%02dV  %s  %s", lastMv / 1000, (lastMv % 1000) / 10, since, left);
  else snprintf(buf, sizeof(buf), "%d.%02dV   %s", lastMv / 1000, (lastMv % 1000) / 10, since);
  drawFit(buf, 124, TINY_FONTS, 2, C_DIM);
}

// Schermata di carica accesa per [ms] (aggiornata ogni volta che cambia qualcosa).
static void chargeScreenFor(uint32_t ms) {
  chargeShowUntil = millis() + ms;
  drawCharge();
  showFor(ms);
}

static void drawBattery() {
  if (usbOn && !connected) {
    chargeScreenFor(CHARGE_SHOW_MS);
    return;
  }
  const int level = batteryPct();
  char buf[24];
  if (level < 0) snprintf(buf, sizeof(buf), "--%%");
  else snprintf(buf, sizeof(buf), "%d%%", level);
  const char* l2 = usbOn ? (chgState == CHG_FULL ? TXT_FULL : TXT_CHARGING) : TXT_BATTERY;
  drawMessage(buf, l2, !usbOn && level >= 0 && level < 20 ? C_RED : C_BALL);
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
  if (cfg.lang >= N_LANG) cfg.lang = L_IT;
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
  cfg.lang    = prefs.getUChar("lang", L_IT);
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
  prefs.putUChar("lang", cfg.lang);
}

static void publishConfig(bool notify) {
  char buf[128];
  snprintf(buf, sizeof(buf), "fw=%s;name=%s;bri=%u;pt=%u;vol=%u;flip=%u;pair=%u;lost=%u;idle=%u;lang=%s",
           FW_VERSION, cfg.name, cfg.bri, cfg.pointS, cfg.vol, cfg.flip ? 1 : 0, cfg.pairS, cfg.lostS, cfg.idleMin,
           LANG_CODES[cfg.lang]);
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
// Solo "lang=xx" (l'app la manda da sola a ogni collegamento) si salva in silenzio, senza anteprima.
static void handleConfig(char* text) {
  char oldName[13];
  strlcpy(oldName, cfg.name, sizeof(oldName));
  const uint8_t oldVol = cfg.vol;
  const uint8_t oldLang = cfg.lang;
  bool onlyLang = true;
  for (char* part = strtok(text, ";"); part; part = strtok(nullptr, ";")) {
    char* eq = strchr(part, '=');
    if (!eq) continue;
    *eq = 0;
    const char* k = part;
    const char* v = eq + 1;
    if (!strcmp(k, "lang")) {
      for (uint8_t i = 0; i < N_LANG; i++) if (!strcasecmp(v, LANG_CODES[i])) cfg.lang = i;
      continue;
    }
    onlyLang = false;
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
  if (onlyLang) {
    if (cfg.lang != oldLang) prefs.putUChar("lang", cfg.lang);
    publishConfig(true);
    return;
  }
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
  const int level = batteryPct();
  if (level >= 0) {
    uint8_t v = (uint8_t)constrain(level, 0, 100);
    battChr->setValue(&v, 1);
    if (notify && connected) battChr->notify();
  }
  // Stato per l'app: tensione in mV (più precisa della percentuale), alimentato da USB (chg=1 anche a carica
  // completa: non c'è consumo da misurare), secondi di accensione e di display acceso (consumo e autonomia
  // reali); dal firmware 2.1 anche tensione USB, carica completa e la percentuale mostrata dal braccialetto.
  char buf[96];
  uint32_t dsp = displayOnTotalMs + (displayOn ? millis() - displayOnSince : 0);
  const bool chg = usbOn || M5.Power.isCharging() == m5::Power_Class::is_charging;
  snprintf(buf, sizeof(buf), "mv=%d;chg=%d;up=%lu;dsp=%lu;usb=%d;full=%d;pct=%d",
           (int)M5.Power.getBatteryVoltage(), chg ? 1 : 0,
           (unsigned long)(millis() / 1000), (unsigned long)(dsp / 1000),
           usbOn ? lastVbus : 0, chgState == CHG_FULL ? 1 : 0, level);
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

// ------------------------------------------------------------------ ricarica
static void onUsbIn(uint32_t now) {
  usbOn = true;
  usbSince = now;
  fullAt = 0;
  notChgSince = 0;
  cvSince = 0;
  chgState = M5.Power.isCharging() == m5::Power_Class::is_charging ? CHG_ACTIVE : CHG_IDLE;
  chgPct = restMv > 2500 ? socFromMv(restMv) : 0;
  pctHistN = 0;
  lastPctSample = now - 60000UL;
  if (connected) {
    // In partita (powerbank al polso) il punteggio ha la precedenza: solo un messaggio breve.
    char l2[16];
    snprintf(l2, sizeof(l2), "%d%%", chgPct);
    drawMessage(TXT_CHARGING, l2, C_BALL);
    showFor(2500);
  } else {
    identifyUntil = 0;
    chargeNextGlance = now + CHARGE_SHOW_MS;
    chargeScreenFor(CHARGE_SHOW_MS);
  }
  beep(2400, 60);
  updateBattery(true);
}

static void onUsbOut(uint32_t now) {
  usbOn = false;
  chgState = CHG_NONE;
  chargeShowUntil = 0;
  // Da qui ripartono i tempi di spegnimento automatico, come appena acceso.
  advSince = now;
  lastActivity = now;
  idleWarned = false;
  if (!connected && !advFast) startAdvertising(true);
  char l2[24];
  snprintf(l2, sizeof(l2), "%s %d%%", TXT_BATTERY, chgPct);
  identifyUntil = 0;
  drawMessage(TXT_USB_OUT, l2, C_ORANGE);
  showFor(3000);
  nextBlink = now + 3000;
  updateBattery(true);
}

// Ogni secondo: cavo USB (con anti-rimbalzo), stato della carica, percentuale e storico per la stima.
static void pollPower(uint32_t now) {
  if (now - lastPowerPoll < POWER_POLL_MS) return;
  lastPowerPoll = now;
  const int vbus = M5.Power.getVBUSVoltage();
  const bool chg = M5.Power.isCharging() == m5::Power_Class::is_charging;  // CHG_STAT basso = in carica
  const int mv = M5.Power.getBatteryVoltage();
  if (vbus > 0) lastVbus = vbus;
  if (mv > 2500) {
    lastMv = mv;
    if (!usbOn) restMv = mv;
  }
  const bool usb = vbus > USB_MIN_MV || chg;
  if (usb != usbOn) {
    if (++usbFlips >= 2) {
      usbFlips = 0;
      if (usb) onUsbIn(now);
      else onUsbOut(now);
    }
    return;
  }
  usbFlips = 0;
  if (!usbOn || chgState == CHG_FULL) return;

  if (chg) {
    notChgSince = 0;
    chgState = CHG_ACTIVE;
    // Riserva se il caricabatterie non chiude mai la carica: 45 minuti a tensione piena.
    if (lastMv >= CV_FULL_MV) {
      if (!cvSince) cvSince = now;
    } else {
      cvSince = 0;
    }
  } else {
    cvSince = 0;
    if (!notChgSince) notChgSince = now;
    // CHG_STAT spento per 20 s: carica finita (con la batteria alta) oppure solo alimentazione
    if (now - notChgSince >= FULL_DEBOUNCE_MS) chgState = lastMv >= USB_MIN_MV ? CHG_FULL : CHG_IDLE;
  }
  if (chgState == CHG_ACTIVE && cvSince && now - cvSince >= CV_FULL_MS) chgState = CHG_FULL;

  if (chgState == CHG_FULL) {
    fullAt = now;
    chgPct = 100;
    if (!connected) chargeScreenFor(CHARGE_SHOW_MS);  // niente bip: può succedere di notte
    updateBattery(true);
    return;
  }
  if (lastMv > 2500) {
    const int p = socFromMv(lastMv - (chgState == CHG_ACTIVE ? CHG_OFFSET_MV : 0));
    chgPct = max(chgPct, min(p, 99));
  }
  if (chgState == CHG_ACTIVE && now - lastPctSample >= 60000UL) {
    lastPctSample = now;
    if (pctHistN == 11) {
      memmove(pctHist, pctHist + 1, 10 * sizeof(int));
      pctHistN = 10;
    }
    pctHist[pctHistN++] = chgPct;
  }
}

// Col cavo e senza telefono: schermata di carica aggiornata mentre è accesa, poi un'occhiata ogni 10 s.
static void chargeDisplay(uint32_t now) {
  static int shownSig = -1;
  if (displayOn && chargeScreen && (int32_t)(now - chargeShowUntil) < 0) {
    // si ridisegna solo se cambia qualcosa (niente sfarfallio), comunque ogni 10 s per le tensioni
    const int sig = chgPct * 100000 + chgState * 10000 + (int)((now - usbSince) / 60000UL) * 10 + (chargeMinutesLeft() > 0 ? 1 : 0);
    if (sig != shownSig || now - lastChargeDraw >= 10000) {
      shownSig = sig;
      drawCharge();
    }
  } else if (!displayOn && chgState != CHG_FULL && (int32_t)(now - chargeNextGlance) >= 0) {
    drawCharge();
    showFor(CHARGE_GLANCE_MS);
    chargeNextGlance = now + CHARGE_GLANCE_EVERY;
  }
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
  pollPower(now);

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
    } else if (usbOn) {
      chargeScreenFor(CHARGE_SHOW_MS);
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
    } else if (usbOn) {
      chargeScreenFor(CHARGE_SHOW_MS);
    } else {
      keyWhileDisconnected(now);
    }
  }
  if (M5.BtnB.wasHold()) powerOff(TXT_POWER_OFF, usbOn ? TXT_KEEPS_CHG : "", OFF_KEY);

  // --- col cavo USB e senza telefono: niente spegnimento automatico, schermata di carica
  if (!connected && !justDisconnect && usbOn) {
    if (advFast && now - advSince > FAST_ADV_MS) startAdvertising(false);
    chargeDisplay(now);
  }
  // --- advertising, lampeggio e spegnimento automatico senza telefono
  else if (!connected && !justDisconnect) {
    if (advFast && now - advSince > FAST_ADV_MS) startAdvertising(false);
    // all'accensione vale il tempo di pairing, dopo una disconnessione quello di telefono perso
    const uint32_t limit = (uint32_t)(everConnected ? cfg.lostS : cfg.pairS) * 1000UL;
    const int32_t left = (int32_t)limit - (int32_t)(millis() - advSince);
    if (left <= 0) powerOff(TXT_POWER_OFF, TXT_NO_PHONE, OFF_TIMEOUT);
    const bool countdown = left <= (int32_t)COUNTDOWN_MS;
    if (!countdown) countdownBeeped = false;
    if ((int32_t)(now - nextBlink) >= 0 && (!displayOn || blinkShown)) {
      if (countdown) {
        char l2[40];
        snprintf(l2, sizeof(l2), txt(T_PRESS_KEY), (long)((left + 999) / 1000));
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
    // (col cavo USB no: il tempo riparte quando lo si stacca)
    const uint32_t idleMs = (uint32_t)cfg.idleMin * 60000UL;
    if (!usbOn && now - lastActivity > idleMs) powerOff(TXT_POWER_OFF, TXT_IDLE, OFF_IDLE);
    if (!usbOn && !idleWarned && now - lastActivity > idleMs - IDLE_WARN_MS) {
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
    if (!charging && !usbOn && mv > 2500 && mv < BATT_EMPTY_MV) {
      if (++battEmptyCount >= 2) powerOff(TXT_POWER_OFF, TXT_EMPTY, OFF_BATTERY);
    } else {
      battEmptyCount = 0;
    }
  }

  // --- spegnimento display a tempo
  if (displayOn && (int32_t)(now - displayOffAt) >= 0) {
    displaySleep();
    blinkShown = !connected;
    chargeScreen = false;
  }

  speakerIdle(now);

  delay(20);  // 50 Hz per i tasti; nel resto del tempo la CPU resta ferma in idle
}
