#!/usr/bin/env bash
# ============================================================================
#  Tennis Score Manager - installazione completa del progetto (app + firmware)
#  Tennis Score Manager - complete project installer (app + firmware)
#  Uso / usage:  bash installa_tsm.sh [cartella_progetto] [cartella_sketch]
#  Predefinite / defaults: ~/AndroidStudioProjects/TennisScoreManager
#                          ~/Arduino/TSM_Band
#  Se la cartella del progetto esiste già viene spostata in un backup datato.
#  An existing project folder is moved to a dated backup first.
#  Linux, macOS, or Windows in Git Bash.
# ============================================================================
set -euo pipefail
DEST="${1:-$HOME/AndroidStudioProjects/TennisScoreManager}"
FWDIR="${2:-$HOME/Arduino/TSM_Band}"

if [ -d "$DEST" ] && [ -n "$(ls -A "$DEST" 2>/dev/null)" ]; then
  BACKUP="${DEST}.backup-$(date +%Y%m%d-%H%M%S)"
  echo ">> Cartella esistente spostata in / existing folder moved to: $BACKUP"
  mv "$DEST" "$BACKUP"
fi
mkdir -p "$DEST"

echo ">> Creo le cartelle / creating folders"
mkdir -p "$DEST/app"
mkdir -p "$DEST/app/src/main"
mkdir -p "$DEST/app/src/main/assets"
mkdir -p "$DEST/app/src/main/java/com/tennis/scoremanager"
mkdir -p "$DEST/app/src/main/java/com/tennis/scoremanager/ble"
mkdir -p "$DEST/app/src/main/java/com/tennis/scoremanager/data"
mkdir -p "$DEST/app/src/main/java/com/tennis/scoremanager/model"
mkdir -p "$DEST/app/src/main/java/com/tennis/scoremanager/service"
mkdir -p "$DEST/app/src/main/java/com/tennis/scoremanager/tv"
mkdir -p "$DEST/app/src/main/java/com/tennis/scoremanager/ui"
mkdir -p "$DEST/app/src/main/java/com/tennis/scoremanager/ui/screens"
mkdir -p "$DEST/app/src/main/java/com/tennis/scoremanager/voice"
mkdir -p "$DEST/app/src/main/res/drawable"
mkdir -p "$DEST/app/src/main/res/mipmap-anydpi-v26"
mkdir -p "$DEST/app/src/main/res/values"
mkdir -p "$DEST/app/src/main/res/xml"
mkdir -p "$DEST/app/src/test/java/com/tennis/scoremanager/ble"
mkdir -p "$DEST/app/src/test/java/com/tennis/scoremanager/data"
mkdir -p "$DEST/app/src/test/java/com/tennis/scoremanager/model"
mkdir -p "$DEST/app/src/test/java/com/tennis/scoremanager/tv"
mkdir -p "$DEST/app/src/test/java/com/tennis/scoremanager/ui"
mkdir -p "$DEST/app/src/test/java/com/tennis/scoremanager/voice"
mkdir -p "$DEST/gradle"
mkdir -p "$DEST/gradle/wrapper"

# ---------------------------------------------------------------- app/build.gradle.kts
cat > "$DEST/app/build.gradle.kts" << 'TSM_EOF'
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.tennis.scoremanager"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.tennis.scoremanager"
        minSdk = 26
        targetSdk = 36
        versionCode = 8
        versionName = "2.4.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.zxing.core)  // QR del tabellone TV
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
TSM_EOF

# ---------------------------------------------------------------- app/proguard-rules.pro
cat > "$DEST/app/proguard-rules.pro" << 'TSM_EOF'
# Nessuna regola particolare: la build release non usa la minificazione.
TSM_EOF

# ---------------------------------------------------------------- app/src/main/AndroidManifest.xml
cat > "$DEST/app/src/main/AndroidManifest.xml" << 'TSM_EOF'
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-feature android:name="android.hardware.bluetooth_le" android:required="false" />

    <!-- Bluetooth LE per i braccialetti (Android 12+ usa SCAN/CONNECT, prima BLUETOOTH/ADMIN) -->
    <uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />
    <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30" />
    <!-- La ricerca serve solo a trovare i braccialetti, non a localizzare: da Android 12 niente posizione -->
    <uses-permission android:name="android.permission.BLUETOOTH_SCAN"
        android:usesPermissionFlags="neverForLocation"
        tools:targetApi="s" />
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
    <!-- Posizione: per il luogo nel riepilogo e, fino ad Android 11, per la ricerca dei braccialetti -->
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
    <!-- Servizio in primo piano durante la partita con i braccialetti -->
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <!-- Tabellone TV: server web locale, ricerca del telefono dell'arbitro, rete Wi-Fi anche senza internet -->
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.CHANGE_NETWORK_STATE" />
    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />

    <!-- Android 11+: senza questa dichiarazione la sintesi vocale non trova i motori TTS -->
    <queries>
        <intent>
            <action android:name="android.intent.action.TTS_SERVICE" />
        </intent>
    </queries>

    <application
        android:name=".TsmApp"
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.TSM"
        android:networkSecurityConfig="@xml/network_security_config"
        tools:targetApi="36">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:launchMode="singleTask"
            android:screenOrientation="portrait"
            android:windowSoftInputMode="adjustResize"
            tools:ignore="DiscouragedApi,LockedOrientationActivity">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <!-- Telefono usato come tabellone: pagina del telefono dell'arbitro a schermo intero, in orizzontale -->
        <activity
            android:name=".tv.DisplayActivity"
            android:exported="false"
            android:screenOrientation="sensorLandscape"
            android:configChanges="orientation|screenSize|screenLayout|smallestScreenSize|keyboardHidden|density"
            android:launchMode="singleTask" />

        <service
            android:name=".service.MatchService"
            android:exported="false"
            android:foregroundServiceType="connectedDevice" />

        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="${applicationId}.fileprovider"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/file_paths" />
        </provider>
    </application>
</manifest>
TSM_EOF

# ---------------------------------------------------------------- app/src/main/assets/scoreboard.html
cat > "$DEST/app/src/main/assets/scoreboard.html" << 'TSM_EOF'
<!doctype html>
<html lang="it">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<meta name="theme-color" content="#000000">
<meta name="mobile-web-app-capable" content="yes">
<title>Tennis Score Manager</title>
<!--
  Tabellone di Tennis Score Manager, servito dal telefono dell'arbitro (TvServer.kt).
  Riceve lo stato in diretta da /events (un JSON a ogni punto e ogni 5 secondi) e fa scorrere
  da sé il tempo partita e i cronometri tra un aggiornamento e l'altro.
  Stile "LED affiancato": cifre a 7 segmenti con i segmenti spenti visibili.
  Anteprima senza telefono: scoreboard.html?demo=1 (&lang=en, fr, de, es, pt; &state=ad, tb, end, mtb, long, idle, doubles)
-->
<style>
  :root {
    --p1: #FFD600;
    --p2: #FF3030;
    --ghost: #15181C;
    --line: #2A2D31;
    --dim: #9A9A9A;
    --cyan: #22C3EE;
    --green: #22D65A;
    --orange: #FF9800;
    --ball: #C6F432;
    /* 1vh e 1vw: li ricalcola anche lo script, perché alcune WebView danno 1vh = 0 */
    --vh: 1vh;
    --vw: 1vw;
  }
  html, body { margin: 0; height: 100%; background: #000; overflow: hidden; }
  body { cursor: none; -webkit-user-select: none; user-select: none; }
  body.pointer { cursor: default; }
  #board {
    position: fixed; inset: 0; display: grid; grid-template-rows: 77% 23%;
    font-family: "DejaVu Sans Mono", "Roboto Mono", "Droid Sans Mono", "Liberation Mono", Menlo, monospace;
    font-weight: 700; color: #DDD; transition: opacity .4s;
  }
  body.lost #board { opacity: .35; }
  svg { display: block; width: 100%; height: 100%; overflow: visible; }

  #main { display: grid; grid-template-columns: 1fr 15.6% 1fr; padding: calc(4 * var(--vh)) calc(2.1 * var(--vw)) calc(1.2 * var(--vh)); min-height: 0; }
  .side { display: grid; grid-template-rows: 17% 83%; min-width: 0; min-height: 0; padding: 0 calc(1.4 * var(--vw)); }
  .name {
    display: flex; align-items: center; gap: .7em; min-width: 0; white-space: nowrap; overflow: hidden;
    font-size: min(calc(6.2 * var(--vh)), calc(3.6 * var(--vw))); letter-spacing: .04em; text-transform: uppercase;
  }
  .name .nm { overflow: hidden; text-overflow: clip; }
  .ball { flex: none; width: .62em; height: .62em; border-radius: 50%; background: var(--ball); box-shadow: 0 0 .35em var(--ball); visibility: hidden; }
  .serving .ball { visibility: visible; }
  .big { padding: calc(2.8 * var(--vh)) 0 calc(3.2 * var(--vh)); min-height: 0; }

  #mid {
    border-left: 2px solid var(--line); border-right: 2px solid var(--line); min-width: 0; min-height: 0;
    display: grid; grid-template-rows: 15% 23% 9% 23% 10% 16%; justify-items: center; align-items: center;
  }
  #vs { font-size: min(calc(5.6 * var(--vh)), calc(3.2 * var(--vw))); font-style: italic; color: #5E6166; letter-spacing: .06em; white-space: nowrap; }
  #vs.tb { font-style: normal; color: var(--cyan); font-size: min(calc(3.4 * var(--vh)), calc(1.9 * var(--vw))); text-align: center; white-space: normal; line-height: 1.1; }
  .gm { height: 86%; width: 60%; }
  .lbl { font-size: min(calc(4.6 * var(--vh)), calc(2.7 * var(--vw))); letter-spacing: .05em; color: var(--dim); }
  .lbl.cy { color: var(--cyan); }
  #setrow { display: grid; grid-template-columns: 1fr 1fr; gap: 28%; width: 62%; height: 88%; }

  #foot {
    border-top: 2px solid var(--line); margin: 0 calc(2.1 * var(--vw)); min-height: 0;
    /* la scritta al centro tiene sempre un po' di posto: un messaggio lungo si rimpicciolisce (fitText) invece di schiacciarla */
    display: grid; grid-template-columns: minmax(0, auto) minmax(calc(14 * var(--vw)), 1fr) minmax(0, auto); column-gap: calc(2 * var(--vw));
    padding: calc(2.4 * var(--vh)) calc(1.2 * var(--vw)) calc(3 * var(--vh));
  }
  #fl, #fr { display: grid; grid-template-rows: 1fr 1.5fr; min-height: 0; min-width: 0; }
  #fr { justify-items: start; }
  #done { font-size: min(calc(4.8 * var(--vh)), calc(2.8 * var(--vw))); color: var(--cyan); white-space: nowrap; overflow: hidden; align-self: center; letter-spacing: .02em; }
  #clock { height: 78%; width: auto; align-self: end; }
  #fc { display: flex; align-items: flex-end; justify-content: center; min-width: 0; }
  #title { font-size: min(calc(3.2 * var(--vh)), calc(1.9 * var(--vw))); color: #6B6F75; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; letter-spacing: .04em; padding-bottom: calc(.6 * var(--vh)); }
  #msg { font-size: min(calc(4.6 * var(--vh)), calc(2.7 * var(--vw))); color: var(--orange); white-space: nowrap; align-self: center; letter-spacing: .03em; }
  #msg.blink { animation: blink 1.2s steps(2, start) infinite; }
  #timer { font-size: min(calc(5.4 * var(--vh)), calc(3.1 * var(--vw))); color: var(--p2); white-space: nowrap; align-self: end; letter-spacing: .05em; }
  #timer.pause { color: var(--orange); }
  .hide { visibility: hidden; }
  @keyframes blink { to { visibility: hidden; } }

  #banner {
    position: fixed; left: 0; right: 0; top: 0; padding: calc(1.4 * var(--vh)) calc(2 * var(--vw)); text-align: center; display: none;
    font: 700 min(calc(4 * var(--vh)), calc(2.4 * var(--vw))) monospace; color: #000; background: var(--orange); letter-spacing: .04em;
  }
  body.lost #banner { display: block; }
  #fs {
    position: fixed; left: calc(2 * var(--vw)); bottom: calc(2 * var(--vh)); display: none; padding: calc(1.2 * var(--vh)) calc(1.6 * var(--vw)); border: 2px solid #444; border-radius: calc(1 * var(--vh));
    font: 700 min(calc(3 * var(--vh)), calc(1.8 * var(--vw))) monospace; color: #DDD; background: #111; cursor: pointer;
  }
  body.pointer #fs.can { display: block; }
</style>
</head>
<body>
<div id="board">
  <div id="main">
    <section class="side" id="side0">
      <div class="name"><span class="nm" id="n0"></span><span class="ball"></span></div>
      <div class="big"><svg id="pt0"></svg></div>
    </section>
    <section id="mid">
      <div id="vs">VS</div>
      <svg class="gm" id="g0"></svg>
      <div class="lbl" id="lgames">GAMES</div>
      <svg class="gm" id="g1"></svg>
      <div class="lbl cy" id="lset">SET</div>
      <div id="setrow"><svg id="s0"></svg><svg id="s1"></svg></div>
    </section>
    <section class="side" id="side1">
      <div class="name"><span class="nm" id="n1"></span><span class="ball"></span></div>
      <div class="big"><svg id="pt1"></svg></div>
    </section>
  </div>
  <div id="foot">
    <div id="fl"><div id="done"></div><svg id="clock"></svg></div>
    <div id="fc"><div id="title"></div></div>
    <div id="fr"><div id="msg"></div><div id="timer"></div></div>
  </div>
</div>
<div id="banner"></div>
<button id="fs" type="button"></button>

<!-- Testi di ogni lingua per prima del primo stato (titolo, "connessione persa", "schermo intero") e per l'anteprima.
     Copia di quelli dell'app: TvSnapshotTest controlla che siano uguali e, se no, stampa il blocco giusto. -->
<script type="application/json" id="texts">
{
"it": {"labels":{"vs":"VS","games":"GAMES","set":"SET","sec":"SEC","waiting":"IN ATTESA DELLA PARTITA","ready":"IN ATTESA DEL VIA","suspended":"PARTITA SOSPESA","winner":"VINCE","tiebreak":"TIE-BREAK","matchTiebreak":"MATCH TIE-BREAK","lost":"CONNESSIONE PERSA - RICONNESSIONE...","fullscreen":"SCHERMO INTERO","page":"Tabellone TSM"},"demo":{"serve":"SERVIZIO","changeover":"CAMBIO CAMPO","setPoint":"SET POINT","title":"Tennis Club · Campo 3"}},
"en": {"labels":{"vs":"VS","games":"GAMES","set":"SETS","sec":"SEC","waiting":"WAITING FOR THE MATCH","ready":"READY TO PLAY","suspended":"MATCH SUSPENDED","winner":"WINNER","tiebreak":"TIE-BREAK","matchTiebreak":"MATCH TIE-BREAK","lost":"CONNECTION LOST - RECONNECTING...","fullscreen":"FULL SCREEN","page":"TSM Scoreboard"},"demo":{"serve":"SERVE","changeover":"CHANGEOVER","setPoint":"SET POINT","title":"Tennis Club · Court 3"}},
"fr": {"labels":{"vs":"VS","games":"JEUX","set":"MANCHES","sec":"SEC","waiting":"EN ATTENTE DU MATCH","ready":"PRÊTS À JOUER","suspended":"MATCH SUSPENDU","winner":"VAINQUEUR","tiebreak":"JEU DÉCISIF","matchTiebreak":"SUPER JEU DÉCISIF","lost":"CONNEXION PERDUE - RECONNEXION...","fullscreen":"PLEIN ÉCRAN","page":"Tableau d'affichage TSM"},"demo":{"serve":"SERVICE","changeover":"CHANGEMENT DE CÔTÉ","setPoint":"BALLE DE SET","title":"Tennis Club · Court 3"}},
"de": {"labels":{"vs":"VS","games":"SPIELE","set":"SÄTZE","sec":"SEK","waiting":"WARTEN AUF DAS MATCH","ready":"BEREIT ZUM SPIELEN","suspended":"MATCH UNTERBROCHEN","winner":"SIEGER","tiebreak":"TIE-BREAK","matchTiebreak":"MATCH-TIE-BREAK","lost":"VERBINDUNG VERLOREN - NEUER VERSUCH...","fullscreen":"VOLLBILD","page":"TSM-Anzeigetafel"},"demo":{"serve":"AUFSCHLAG","changeover":"SEITENWECHSEL","setPoint":"SATZBALL","title":"Tennis Club · Platz 3"}},
"es": {"labels":{"vs":"VS","games":"JUEGOS","set":"SETS","sec":"SEG","waiting":"ESPERANDO EL PARTIDO","ready":"LISTOS PARA JUGAR","suspended":"PARTIDO SUSPENDIDO","winner":"GANADOR","tiebreak":"TIE-BREAK","matchTiebreak":"SÚPER TIE-BREAK","lost":"CONEXIÓN PERDIDA - RECONECTANDO...","fullscreen":"PANTALLA COMPLETA","page":"Marcador TSM"},"demo":{"serve":"SAQUE","changeover":"CAMBIO DE LADO","setPoint":"BOLA DE SET","title":"Tennis Club · Pista 3"}},
"pt": {"labels":{"vs":"VS","games":"JOGOS","set":"SETS","sec":"SEG","waiting":"AGUARDANDO A PARTIDA","ready":"PRONTOS PARA JOGAR","suspended":"PARTIDA SUSPENSA","winner":"VENCEDOR","tiebreak":"TIE-BREAK","matchTiebreak":"TIE-BREAK DECISIVO","lost":"CONEXÃO PERDIDA - RECONECTANDO...","fullscreen":"TELA CHEIA","page":"Placar TSM"},"demo":{"serve":"SERVIÇO","changeover":"TROCA DE LADO","setPoint":"SET POINT","title":"Tennis Club · Quadra 3"}}
}
</script>
<script>
"use strict";
// ------------------------------------------------------------------ cifre a 7 segmenti
// Una cifra è 100 x 172: barre orizzontali tra le verticali, come un tabellone a LED.
const DW = 100, DH = 172, T = 14, GAP = 1.8, DSP = 16, COLON = 34;
const V = (DH - 3 * T) / 2;
const RECT = {
  a: [T + GAP, 0, DW - 2 * T - 2 * GAP, T],
  b: [DW - T, T + GAP, T, V - 2 * GAP],
  c: [DW - T, 2 * T + V + GAP, T, V - 2 * GAP],
  d: [T + GAP, DH - T, DW - 2 * T - 2 * GAP, T],
  e: [0, 2 * T + V + GAP, T, V - 2 * GAP],
  f: [0, T + GAP, T, V - 2 * GAP],
  g: [T + GAP, T + V, DW - 2 * T - 2 * GAP, T],
};
const GLYPH = {
  "0": "abcdef", "1": "bc", "2": "abdeg", "3": "abcdg", "4": "bcfg", "5": "acdfg", "6": "acdefg",
  "7": "abc", "8": "abcdefg", "9": "abcdfg", "A": "abcefg", "D": "bcdeg", "-": "g", " ": "",
};
const NS = "http://www.w3.org/2000/svg";
let ghost = true;

/** Prepara un display: "88" = due cifre, "88:88:88" = orologio. */
function buildSeg(svg, pattern) {
  let x = 0;
  const cells = [];
  for (const ch of pattern) {
    if (ch === ":") {
      const dots = [];
      for (const y of [DH * 0.32, DH * 0.68]) {
        const r = document.createElementNS(NS, "rect");
        r.setAttribute("x", x + COLON / 2 - T / 2); r.setAttribute("y", y - T / 2);
        r.setAttribute("width", T); r.setAttribute("height", T); r.setAttribute("rx", 2);
        svg.appendChild(r); dots.push(r);
      }
      cells.push({ colon: dots });
      x += COLON + DSP;
    } else {
      const segs = {};
      for (const k in RECT) {
        const [rx, ry, w, h] = RECT[k];
        const r = document.createElementNS(NS, "rect");
        r.setAttribute("x", x + rx); r.setAttribute("y", ry);
        r.setAttribute("width", w); r.setAttribute("height", h); r.setAttribute("rx", 2.5);
        svg.appendChild(r); segs[k] = r;
      }
      cells.push({ segs });
      x += DW + DSP;
    }
  }
  svg.setAttribute("viewBox", `0 0 ${x - DSP} ${DH}`);
  svg.setAttribute("preserveAspectRatio", "xMidYMid meet");
  svg._cells = cells;
  svg._text = null;
}

/** Scrive un testo (allineato a destra, spazi = cifra spenta) nel colore dato. */
function setSeg(svg, text, color) {
  const key = text + "|" + color + "|" + ghost;
  if (svg._text === key) return;
  svg._text = key;
  const digits = svg._cells.filter(c => c.segs).length;
  const chars = String(text).toUpperCase().padStart(digits, " ").slice(-digits).split("");
  let i = 0;
  for (const cell of svg._cells) {
    if (cell.colon) { cell.colon.forEach(r => r.setAttribute("fill", color)); continue; }
    const on = GLYPH[chars[i++]] ?? "";
    for (const k in cell.segs) cell.segs[k].setAttribute("fill", on.includes(k) ? color : (ghost ? "var(--ghost)" : "transparent"));
  }
}

// ------------------------------------------------------------------ stato
const $ = id => document.getElementById(id);
const pts = [$("pt0"), $("pt1")], gms = [$("g0"), $("g1")], sts = [$("s0"), $("s1")];
pts.forEach(s => buildSeg(s, "88"));
gms.forEach(s => buildSeg(s, "8"));
sts.forEach(s => buildSeg(s, "8"));
buildSeg($("clock"), "88:88:88");

// Testi prima che arrivi il primo stato, nella lingua del browser (poi valgono quelli dell'app, nella lingua dell'arbitro).
const TEXTS = JSON.parse($("texts").textContent);
const FALLBACK = (TEXTS[(navigator.language || "it").slice(0, 2).toLowerCase()] || TEXTS.en).labels;
document.title = FALLBACK.page;
let S = null, recvAt = 0, lastMsg = performance.now(), lostSince = 0, lostCalledAt = 0;
const inApp = typeof window.TSMDisplay !== "undefined" || /[?&]display=app/.test(location.search);
const color = i => (S && S.players[i] && S.players[i].color) || (i ? "#FF3030" : "#FFD600");

function fitText(el, maxPx) {
  el.style.fontSize = "";
  let size = parseFloat(getComputedStyle(el).fontSize);
  const box = el.parentElement;
  while (el.scrollWidth > box.clientWidth - (maxPx || 0) && size > 8) {
    size *= 0.92;
    el.style.fontSize = size + "px";
  }
}

function hms(ms) {
  const t = Math.max(0, Math.floor(ms / 1000));
  const h = Math.floor(t / 3600) % 100, m = Math.floor(t / 60) % 60, s = t % 60;
  return [h, m, s].map(v => String(v).padStart(2, "0")).join("");
}

function setText(el, text) { if (el.textContent !== text) el.textContent = text; }

function render() {
  if (!S) return;
  ghost = S.show.ghost !== false;
  document.documentElement.lang = S.lang;
  const L = S.labels;
  if (L.page && document.title !== L.page) document.title = L.page;
  setText(fs, "⛶ " + L.fullscreen);
  document.documentElement.style.setProperty("--p1", color(0));
  document.documentElement.style.setProperty("--p2", color(1));
  for (let i = 0; i < 2; i++) {
    const n = $("n" + i);
    n.style.color = color(i);
    if (n.textContent !== S.players[i].name) { n.textContent = S.players[i].name; fitText(n, 60); }
    $("side" + i).classList.toggle("serving", S.show.serve && S.server === i);
    setSeg(pts[i], S.points[i], color(i));
    setSeg(gms[i], S.phase === "idle" ? " " : String(S.games[i] % 10), color(i));
    setSeg(sts[i], S.phase === "idle" ? " " : String(S.sets[i] % 10), color(i));
  }
  const vs = $("vs");
  const tb = S.phase === "play" || S.phase === "suspended" ? (S.tiebreak === "match" ? L.matchTiebreak : S.tiebreak === "set" ? L.tiebreak : "") : "";
  setText(vs, tb || L.vs);
  vs.classList.toggle("tb", !!tb);
  setText($("lgames"), L.games);
  setText($("lset"), L.set);

  // set conclusi: [6-4] [7-6(5)] [10-8]
  const done = S.show.sets ? S.done.map(d => {
    const tbPts = !d.mtb && d.tb1 != null && d.tb2 != null ? `(${Math.min(d.tb1, d.tb2)})` : "";
    return d.mtb ? `[${d.tb1}-${d.tb2}]` : `[${d.g1}-${d.g2}${tbPts}]`;
  }).join(" ") : "";
  setText($("done"), done);
  $("clock").classList.toggle("hide", !S.show.clock);
  setText($("title"), S.title || "");

  // messaggio: fasi speciali prima di tutto
  const msg = $("msg");
  let text = "", blink = false, col = "var(--orange)";
  if (S.phase === "idle") text = L.waiting;
  else if (S.phase === "ready") text = L.ready;
  else if (S.phase === "suspended") { text = L.suspended; blink = true; }
  else if (S.phase === "finished" && S.winner != null) { text = `${L.winner} ${S.players[S.winner].name}`.toUpperCase(); col = color(S.winner); }
  else if (S.message) text = S.message.toUpperCase();
  if (msg.textContent !== text) { msg.textContent = text; fitText(msg); }
  msg.style.color = col;
  msg.classList.toggle("blink", blink);
  tick();
}

function tick() {
  const now = performance.now();
  if (S) {
    const clock = S.clockMs + (S.clockRunning ? now - recvAt : 0);
    setSeg($("clock"), hms(clock), "var(--green)");
    const timer = $("timer");
    const cd = S.countdown;
    if (cd) {
      const left = Math.max(0, Math.ceil((cd.leftMs - (now - recvAt)) / 1000));
      setText(timer, `${cd.label}: ${String(left).padStart(2, "0")} ${S.labels.sec}`);
      timer.classList.toggle("pause", !cd.shot);
      timer.classList.remove("hide");
    } else {
      timer.classList.add("hide");
    }
  }
  // collegamento: il telefono manda qualcosa almeno ogni 5 secondi
  const silent = now - lastMsg;
  document.body.classList.toggle("lost", silent > 12000);
  if (silent > 12000) {
    setText($("banner"), S ? S.labels.lost : FALLBACK.lost);
    if (!lostSince) lostSince = now;
    // nell'app: dopo 20" si chiede al telefono di ricollegarsi, poi di nuovo ogni 30" finché non torna
    if (inApp && window.TSMDisplay && now - lostSince > 20000 && (!lostCalledAt || now - lostCalledAt > 30000)) {
      lostCalledAt = now;
      try { TSMDisplay.lost(); } catch (e) {}
    }
    // il collegamento può essere morto senza che il browser se ne accorga (Wi-Fi caduto): se ne apre uno nuovo
    if (now - reopenAt > 15000) reopen();
  } else {
    lostSince = 0;
    lostCalledAt = 0;
  }
}

function onState(json) {
  S = json;
  recvAt = lastMsg = performance.now();
  render();
}

// ------------------------------------------------------------------ collegamento
let es = null, retryMs = 2000, retryTimer = 0, reopenAt = 0;
function connect() {
  clearTimeout(retryTimer);
  if (!window.EventSource) { poll(); return; }
  if (es) es.close();
  const src = es = new EventSource("events");
  src.onmessage = e => { retryMs = 2000; try { onState(JSON.parse(e.data)); } catch (err) {} };
  src.onerror = () => {
    // Risposta non valida (es. server pieno): l'EventSource si chiude per sempre, si riprova da qui sempre più piano.
    // Gli altri errori (rete) li riprova da solo.
    if (src !== es || src.readyState !== EventSource.CLOSED) return;
    retryTimer = setTimeout(connect, retryMs);
    retryMs = Math.min(retryMs * 2, 30000);
  };
}
function reopen() {
  reopenAt = performance.now();
  if (window.EventSource && !isDemo) connect();
}
function poll() {
  fetch("state", { cache: "no-store" }).then(r => r.json()).then(onState).catch(() => {}).finally(() => setTimeout(poll, 1000));
}

// ------------------------------------------------------------------ schermo intero e schermo acceso
const fs = $("fs");
fs.textContent = "⛶ " + FALLBACK.fullscreen;
if (!inApp && document.documentElement.requestFullscreen) fs.classList.add("can");
let pointerTimer = 0;
function showPointer() {
  document.body.classList.add("pointer");
  clearTimeout(pointerTimer);
  pointerTimer = setTimeout(() => document.body.classList.remove("pointer"), 4000);
}
["mousemove", "touchstart", "click"].forEach(ev => document.addEventListener(ev, showPointer, { passive: true }));
fs.addEventListener("click", async () => {
  try {
    await document.documentElement.requestFullscreen({ navigationUI: "hide" });
    if (screen.orientation && screen.orientation.lock) screen.orientation.lock("landscape").catch(() => {});
  } catch (e) {}
  keepAwake();
});
document.addEventListener("fullscreenchange", () => fs.classList.toggle("can", !document.fullscreenElement));
async function keepAwake() {
  // funziona solo in https o su localhost; nell'app TSM lo schermo resta acceso comunque
  try { if (navigator.wakeLock) await navigator.wakeLock.request("screen"); } catch (e) {}
}
document.addEventListener("visibilitychange", () => { if (document.visibilityState === "visible") keepAwake(); });
function viewportUnits() {
  if (innerHeight > 0 && innerWidth > 0) {
    document.documentElement.style.setProperty("--vh", innerHeight / 100 + "px");
    document.documentElement.style.setProperty("--vw", innerWidth / 100 + "px");
  }
}
viewportUnits();
window.addEventListener("resize", () => { viewportUnits(); for (let i = 0; i < 2; i++) fitText($("n" + i), 60); fitText($("msg")); });
keepAwake();
setInterval(tick, 200);

// ------------------------------------------------------------------ anteprima senza telefono (?demo=1)
const isDemo = /[?&]demo=1/.test(location.search);
if (isDemo) {
  const lm = location.search.match(/[?&]lang=([a-z]{2})/);
  const lang = lm && TEXTS[lm[1]] ? lm[1] : "it";
  const { labels, demo } = TEXTS[lang];
  const base = {
    tsm: 1, seq: 1, lang, phase: "play", title: demo.title,
    players: [{ name: "Stefano", color: "#FFD600" }, { name: "Mario", color: "#FF3030" }],
    server: 0, points: ["15", "0"], games: [0, 0], sets: [1, 0], done: [{ g1: 6, g2: 0 }], tiebreak: "",
    winner: null, clockMs: 113000, clockRunning: true, countdown: { label: demo.serve, leftMs: 25000, shot: true },
    message: null, labels,
    show: { clock: true, timers: true, sets: true, messages: true, serve: true, ghost: true },
  };
  const m = location.search.match(/[?&]state=([a-z]+)/);
  const variants = {
    play: {},
    ad: { points: ["AD", "40"], games: [5, 4], message: demo.setPoint, server: 1 },
    tb: { points: ["6", "5"], games: [6, 6], tiebreak: "set", done: [{ g1: 6, g2: 0 }], countdown: { label: demo.changeover, leftMs: 30000, shot: false } },
    end: { phase: "finished", server: null, points: ["", ""], games: [7, 6], sets: [2, 0], done: [{ g1: 6, g2: 0 }, { g1: 7, g2: 6, tb1: 7, tb2: 5 }], winner: 0, clockRunning: false, countdown: null },
    // vinta al match tie-break: vale come set vinto 1-0, i punti stanno tra i set conclusi
    mtb: { phase: "finished", server: null, points: ["", ""], games: [1, 0], sets: [2, 1], done: [{ g1: 6, g2: 3 }, { g1: 4, g2: 6 }, { g1: 1, g2: 0, tb1: 10, tb2: 8, mtb: true }], winner: 0, clockRunning: false, countdown: null },
    // messaggio lungo (doppio, fine set + match tie-break + cambio campo): si rimpicciolisce senza schiacciare la scritta
    long: { players: [{ name: "Rossi / Bianchi", color: "#FFD600" }, { name: "Verdi / Esposito", color: "#FF3030" }], points: ["0", "0"], games: [0, 0], sets: [1, 1], done: [{ g1: 6, g2: 4 }, { g1: 6, g2: 7, tb1: 5, tb2: 7 }], tiebreak: "match", message: `Rossi / Bianchi · ${labels.matchTiebreak} · ${demo.changeover}`, countdown: { label: demo.changeover, leftMs: 90000, shot: false } },
    idle: { phase: "idle", points: ["", ""], server: null, done: [], sets: [0, 0], clockMs: 0, clockRunning: false, countdown: null },
    doubles: { players: [{ name: "Rossi / Bianchi", color: "#FFD600" }, { name: "Verdi / Esposito", color: "#FF3030" }], points: ["30", "40"], games: [3, 2] },
  };
  onState(Object.assign({}, base, variants[(m && m[1]) || "play"] || {}));
  setInterval(() => { lastMsg = performance.now(); }, 1000);
} else {
  connect();
}
</script>
</body>
</html>
TSM_EOF

# ---------------------------------------------------------------- app/src/main/java/com/tennis/scoremanager/MainActivity.kt
cat > "$DEST/app/src/main/java/com/tennis/scoremanager/MainActivity.kt" << 'TSM_EOF'
package com.tennis.scoremanager

import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.tennis.scoremanager.data.PlayMode
import com.tennis.scoremanager.ui.AppRoot
import com.tennis.scoremanager.ui.LocalStrings
import com.tennis.scoremanager.ui.TsmTheme
import com.tennis.scoremanager.ui.stringsFor
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val controller: MatchController get() = (application as TsmApp).controller

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        controller.onActivityCreated(restored = savedInstanceState != null)
        // Tema sempre scuro: icone chiare nelle barre di sistema.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            val options by controller.options.collectAsState()
            TsmTheme {
                CompositionLocalProvider(LocalStrings provides stringsFor(options.lang)) {
                    AppRoot(controller)
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    controller.screen.collect { s ->
                        // Schermo sempre acceso in attesa dell'inizio e durante la partita.
                        if (s == Screen.MATCH || s == Screen.START) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }
                launch {
                    controller.toasts.collect { Toast.makeText(this@MainActivity, it, Toast.LENGTH_LONG).show() }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        controller.envTick.value++
        controller.onForeground()
        controller.ble.refreshLocation()
        if (controller.options.value.mode == PlayMode.BANDS) controller.ble.reconnectAll()
    }

    override fun onStop() {
        super.onStop()
        controller.onBackground()
    }
}
TSM_EOF

# ---------------------------------------------------------------- app/src/main/java/com/tennis/scoremanager/MatchController.kt
cat > "$DEST/app/src/main/java/com/tennis/scoremanager/MatchController.kt" << 'TSM_EOF'
package com.tennis.scoremanager

import android.app.Activity
import android.app.Application
import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import android.provider.DocumentsContract
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
import com.tennis.scoremanager.ble.BandEvent
import com.tennis.scoremanager.ble.BandInfo
import com.tennis.scoremanager.ble.BandProtocol
import com.tennis.scoremanager.ble.BandSettings
import com.tennis.scoremanager.ble.BandStatus
import com.tennis.scoremanager.ble.BatteryModel
import com.tennis.scoremanager.ble.BleManager
import com.tennis.scoremanager.ble.FoundBand
import com.tennis.scoremanager.ble.LinkState
import com.tennis.scoremanager.data.LocationHelper
import com.tennis.scoremanager.data.MatchLocation
import com.tennis.scoremanager.data.MatchOptions
import com.tennis.scoremanager.data.MatchRecord
import com.tennis.scoremanager.data.Names
import com.tennis.scoremanager.data.PlayMode
import com.tennis.scoremanager.data.Reports
import com.tennis.scoremanager.data.SetupData
import com.tennis.scoremanager.data.Storage
import com.tennis.scoremanager.model.MatchEvent
import com.tennis.scoremanager.model.MatchState
import com.tennis.scoremanager.model.RulesConfig
import com.tennis.scoremanager.model.ScoreEngine
import com.tennis.scoremanager.model.Side
import com.tennis.scoremanager.model.Transition
import com.tennis.scoremanager.service.MatchService
import com.tennis.scoremanager.tv.TvInput
import com.tennis.scoremanager.tv.TvServer
import com.tennis.scoremanager.tv.TvSettings
import com.tennis.scoremanager.tv.TvSnapshot
import com.tennis.scoremanager.tv.TvSnapshots
import com.tennis.scoremanager.ui.Strings
import com.tennis.scoremanager.ui.TsmColors
import com.tennis.scoremanager.ui.stringsFor
import com.tennis.scoremanager.voice.Announcer
import com.tennis.scoremanager.voice.CallBuilder
import com.tennis.scoremanager.voice.Seg
import com.tennis.scoremanager.voice.VoicePack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import java.io.File
import java.io.OutputStream

enum class Screen { SETUP, OPTIONS, START, MATCH, SUMMARY }

enum class CountdownKind { SHOT_CLOCK, CHANGEOVER, SET_BREAK, TIEBREAK_BREAK }

data class CountdownUi(val kind: CountdownKind, val seconds: Int)

data class LiveMatch(val record: MatchRecord, val state: MatchState)

/**
 * Carica del braccialetto e autonomia stimata dal consumo misurato (null finché non ci sono dati).
 * [charging] = col cavo USB; [full] = carica completa (firmware 2.1).
 */
data class BandBattery(val percent: Int, val hoursLeft: Double?, val charging: Boolean, val full: Boolean = false) {
    /** "~6 h" oppure "~40 min". */
    fun leftText(): String? = hoursLeft?.let { h -> if (h >= 1.0) "~${Math.round(h)} h" else "~${Math.round(h * 60)} min" }
}

/**
 * Cuore dell'app: tiene la partita, i tempi, la voce e i braccialetti.
 * Vive quanto il processo (non quanto l'Activity), così i braccialetti funzionano anche a schermo spento.
 */
class MatchController(
    private val app: Application,
    val storage: Storage,
    val ble: BleManager,
    val voice: VoicePack,
    val announcer: Announcer,
) {
    companion object {
        const val SHOT_CLOCK_S = 25          // ITF: 25" tra un punto e l'altro
        const val CHANGEOVER_S = 90          // ITF: 90" al cambio campo
        const val SET_BREAK_S = 120          // ITF: 120" a fine set
        const val WALK_S = 30                // pausa per spostarsi (dopo il 1° game, nel tie-break, sul 6-6)
        private const val BAND_GAP_MS = 2_000L
        private const val TAP_GAP_MS = 700L
        /** "Annulla punto" dal telefono: un doppio tocco toglierebbe due punti. */
        private const val UNDO_GAP_MS = 1_000L
        /** Il riepilogo si riprende la posizione solo poco dopo la fine (dopo si è altrove). */
        private const val LATE_LOCATION_MS = 30 * 60_000L
        private const val MESSAGE_MS = 5_000L
        /** Nome della prova voce in [Announcer.playing]. */
        const val VOICE_TEST = "test"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val io = Dispatchers.IO.limitedParallelism(1)

    val screen = MutableStateFlow(Screen.SETUP)
    val setup = MutableStateFlow(storage.setup)
    val options = MutableStateFlow(storage.options)
    val live = MutableStateFlow<LiveMatch?>(null)
    val summary = MutableStateFlow<LiveMatch?>(null)
    val clockMs = MutableStateFlow(0L)
    val countdown = MutableStateFlow<CountdownUi?>(null)
    val message = MutableStateFlow<String?>(null)
    val endDialog = MutableStateFlow(false)
    val serveOrderPrompt = MutableStateFlow(false)
    val saved = MutableStateFlow<List<MatchRecord>>(emptyList())
    /** Riepilogo salvato nello storico o condiviso: "Nuova partita" ed "Esci" non chiedono conferma. */
    val summaryKept = MutableStateFlow(false)
    val voiceProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    /** File generati dal TTS e registrazioni personalizzate presenti per la lingua corrente. */
    val voiceCount = MutableStateFlow(0)
    val customVoiceCount = MutableStateFlow(0)
    val bandBattery = MutableStateFlow<Map<Side, BandBattery>>(emptyMap())
    private val batterySamples = mutableMapOf<Side, MutableList<Pair<Long, Int>>>()
    /** Primo stato della finestra di campioni: serve per sapere quanto è rimasto acceso il display nel frattempo. */
    private val statusStart = mutableMapOf<Side, BandStatus>()
    private val lastStatus = mutableMapOf<Side, BandStatus>()
    private val batteryWarned = mutableMapOf<Side, Int>()
    /** Consumo di base misurato per braccialetto (indirizzo -> mA): rende più precisa la stima nelle impostazioni. */
    val bandBaseMa = MutableStateFlow<Map<String, Double>>(emptyMap())
    /** Ricerca automatica attiva (pagina dei braccialetti aperta): i braccialetti trovati riempiono i posti liberi. */
    private var autoAssign = false
    /** Braccialetti tolti a mano: la ricerca automatica non li rimette. */
    private val autoBlocked = mutableSetOf<String>()
    private var closing = false
    /** Aumenta a ogni onResume dell'Activity: le schermate ricontrollano Bluetooth/posizione. */
    val envTick = MutableStateFlow(0)
    private val _toasts = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val toasts: SharedFlow<String> = _toasts

    /** Tabellone TV: impostazioni, server web e messaggi per il pubblico (solo quelli di gioco). */
    val tv = MutableStateFlow(storage.tv)
    val tvServer = TvServer(app)
    private val tvMessage = MutableStateFlow<String?>(null)
    private var tvMessageJob: Job? = null
    private var tvSeq = 0L
    /** Fine del cronometro in corso (null = nessuno): a ogni nuovo cronometro il tabellone riceve subito il tempo giusto. */
    private val tvCountdownEnd = MutableStateFlow<Long?>(null)
    private val tvJson = Json { encodeDefaults = true }

    val strings: Strings get() = stringsFor(options.value.lang)
    fun names(s: SetupData = setup.value): Names = Names(s, strings)
    private fun calls() = CallBuilder(options.value.lang)

    private var runningSince: Long? = null
    private var cdKind: CountdownKind? = null
    private var cdEnd = 0L
    private var lastPointAt = 0L
    private var lastBandUndoAt = 0L
    private var lastUndoAt = 0L
    private var locationJob: Job? = null
    private var locationFor: String? = null
    /** Il riepilogo perso con il processo si riapre una volta sola, alla prima Activity. */
    private var restoreChecked = false
    private var lastAutosave = 0L
    private var messageJob: Job? = null
    /**
     * Lingua già chiesta a ciascun braccialetto, per non riscriverla a ogni aggiornamento prima che risponda.
     * Va dichiarata prima di `init`: lì watchBands() la usa subito con i braccialetti salvati (altrimenti è null).
     */
    private val bandLangSent = mutableMapOf<Side, String>()

    init {
        announcer.lang = options.value.lang
        announcer.enabled = options.value.audio
        announcer.useGeneratedFiles = options.value.voiceFiles
        announcer.configure(options.value.ttsEngine, options.value.ttsVoice)
        scope.launch(io) { voice.writeReadme(strings) }
        refreshVoiceCount()
        if (options.value.mode == PlayMode.BANDS) restoreBands()
        refreshSaved()
        scope.launch { ble.events.collect { onBandEvent(it) } }
        scope.launch { ble.ready.collect { onBandReady(it) } }
        scope.launch { ble.found.collect { autoFill(it) } }
        scope.launch { ble.status.collect { (side, st) -> onBandStatus(side, st) } }
        scope.launch { watchBands() }
        scope.launch {
            while (true) {
                tick()
                delay(200)
            }
        }
        scope.launch {
            var first = true
            tv.map { it.enabled }.distinctUntilChanged().collect { on ->
                if (on) tvServer.start() else tvServer.stop()
                publishTv()
                // Il servizio in primo piano tiene vivo il server anche a schermo spento, in ogni schermata (anche il
                // riepilogo). Non all'avvio dell'app (first): lì parte con la partita, quando l'app è di sicuro visibile.
                val bandsMatch = options.value.mode == PlayMode.BANDS && (screen.value == Screen.START || screen.value == Screen.MATCH)
                if (on && !first || !on && bandsMatch) MatchService.start(app)  // anche per aggiornare il testo della notifica
                else if (!on) MatchService.stop(app)
                first = false
            }
        }
        scope.launch {
            // A ogni cambiamento che si vede sul tabellone (i cronometri li fa scorrere la pagina da sé: basta l'inizio)...
            merge(
                screen, setup, options, live, summary, tvMessage, tv, tvServer.clients, tvCountdownEnd,
            ).collect { publishTv() }
        }
        scope.launch {
            // ...e comunque ogni 5 secondi: rimette in passo gli orologi e dice al tabellone che il telefono c'è.
            while (true) {
                delay(5_000)
                publishTv()
            }
        }
    }

    // ---------------------------------------------------------------- tabellone TV

    fun updateTv(transform: (TvSettings) -> TvSettings) {
        val v = transform(tv.value)
        tv.value = v
        storage.tv = v
    }

    /** Serve il servizio in primo piano: braccialetti, oppure tabellone TV da tenere acceso. */
    private fun needsService() = options.value.mode == PlayMode.BANDS || tv.value.enabled

    private fun publishTv() {
        if (!tvServer.running.value) return
        val sc = screen.value
        val lm = live.value
        val match = lm ?: summary.value?.takeIf { sc == Screen.SUMMARY }
        val o = options.value
        val snap = TvSnapshots.build(
            TvInput(
                screen = sc,
                setup = setup.value,
                lang = o.lang,
                firstServer = o.firstServer,
                match = match,
                clockMs = if (lm != null) currentClock() else match?.record?.clockMs ?: 0L,
                clockRunning = lm != null && runningSince != null,
                countdown = cdKind,
                countdownLeftMs = cdEnd - SystemClock.elapsedRealtime(),
                message = tvMessage.value,
                tv = tv.value,
                strings = strings,
                seq = ++tvSeq,
            ),
        )
        tvServer.publish(tvJson.encodeToString(TvSnapshot.serializer(), snap))
    }

    /** Messaggio di gioco per il tabellone (set point, cambio campo...): 5 secondi come sul telefono. */
    private fun showTvMessage(text: String?) {
        tvMessage.value = text
        tvMessageJob?.cancel()
        if (text != null) {
            tvMessageJob = scope.launch {
                delay(MESSAGE_MS)
                tvMessage.value = null
            }
        }
    }

    // ---------------------------------------------------------------- configurazione

    fun updateSetup(transform: (SetupData) -> SetupData) {
        val v = transform(setup.value)
        setup.value = v
        storage.setup = v
    }

    fun updateOptions(transform: (MatchOptions) -> MatchOptions) {
        val old = options.value
        val v = transform(old)
        options.value = v
        storage.options = v
        announcer.lang = v.lang
        announcer.enabled = v.audio
        announcer.useGeneratedFiles = v.voiceFiles
        if (old.ttsEngine != v.ttsEngine || old.ttsVoice != v.ttsVoice) announcer.configure(v.ttsEngine, v.ttsVoice)
        if (old.lang != v.lang) {
            refreshVoiceCount()
            val s = strings
            scope.launch(io) { voice.writeReadme(s) }
            syncBandLanguage(ble.bands.value)
        }
        if (old.mode != v.mode) {
            if (v.mode == PlayMode.BANDS) restoreBands() else ble.disconnectAll()
        }
    }

    fun go(to: Screen) {
        if (to == Screen.START) refreshSaved()
        if (to == Screen.OPTIONS && options.value.mode == PlayMode.BANDS) ble.reconnectAll()
        // Il servizio in primo piano parte ora che l'app è visibile: avviarlo dopo, da un KEY1 a schermo
        // bloccato, Android lo vieterebbe e la partita resterebbe senza protezione in background.
        if (to == Screen.START && needsService()) MatchService.start(app)
        screen.value = to
    }

    fun back() {
        screen.value = when (screen.value) {
            Screen.OPTIONS -> Screen.SETUP
            Screen.START -> {
                if (tv.value.enabled) MatchService.start(app) else MatchService.stop(app)
                Screen.OPTIONS
            }
            else -> screen.value
        }
    }

    // ---------------------------------------------------------------- braccialetti

    private fun restoreBands() {
        storage.bandAddress(true)?.let { ble.assign(Side.P1, it, storage.bandName(true)) }
        storage.bandAddress(false)?.let { ble.assign(Side.P2, it, storage.bandName(false)) }
    }

    fun assignBand(side: Side, address: String?, name: String?) {
        if (address == null) ble.bands.value[side]?.address?.let { autoBlocked += it } else autoBlocked -= address
        setBand(side, address, name)
    }

    private fun setBand(side: Side, address: String?, name: String?) {
        // Un braccialetto appartiene a un solo giocatore: se era sull'altro lato lo si toglie.
        if (address != null && storage.bandAddress(side == Side.P2) == address) storage.setBand(side == Side.P2, null, null)
        storage.setBand(side == Side.P1, address, name)
        ble.assign(side, address, name)
    }

    /**
     * Ricerca automatica e continua mentre la pagina dei braccialetti è aperta (e l'app in primo piano).
     * I braccialetti trovati vanno da soli nei posti liberi: prima Giocatore 1, poi Giocatore 2.
     */
    fun setBandScan(on: Boolean) {
        autoAssign = on && options.value.mode == PlayMode.BANDS
        ble.setAutoScan(autoAssign)
    }

    private fun autoFill(found: List<FoundBand>) {
        if (!autoAssign || options.value.mode != PlayMode.BANDS) return
        for (f in found.sortedByDescending { it.rssi }) {
            val bands = ble.bands.value
            if (bands.values.any { it.address == f.address } || f.address in autoBlocked) continue
            val free = Side.entries.firstOrNull { bands[it] == null } ?: return
            setBand(free, f.address, f.name)
        }
    }

    /** Scambia i braccialetti tra i due giocatori (restano collegati) e lo mostra su ciascuno. */
    fun swapBands() {
        val p1 = storage.bandAddress(true) to storage.bandName(true)
        val p2 = storage.bandAddress(false) to storage.bandName(false)
        storage.setBand(true, p2.first, p2.second)
        storage.setBand(false, p1.first, p1.second)
        ble.swapSides()
        Side.entries.forEach { ble.send(it, BandProtocol.message(strings.bandPaired, names().short(it), 3)) }
    }

    /** Il braccialetto lampeggia nel colore del giocatore e suona: così si vede quale braccialetto è di chi. */
    fun identifyBand(side: Side) {
        val s = strings
        val line1 = s.playerDefault(if (side == Side.P1) 1 else 2)
        scope.launch {
            if (!ble.command(side, BandProtocol.identify(line1, names().short(side), 6, TsmColors.player(side).toArgb()))) {
                _toasts.tryEmit(s.bandNotReady)
            }
        }
    }

    /** Scrive le impostazioni nel braccialetto; il braccialetto risponde con quelle applicate. */
    fun writeBandSettings(side: Side, settings: BandSettings) {
        val s = strings
        scope.launch { if (!ble.writeSettings(side, settings)) _toasts.tryEmit(s.bandNotReady) }
    }

    /** Stesse impostazioni sull'altro braccialetto (il nome resta il suo). */
    fun copyBandSettings(from: Side) {
        val src = ble.bands.value[from]?.settings ?: return
        val dst = ble.bands.value[from.other]?.settings ?: return
        writeBandSettings(from.other, src.copy(name = dst.name))
    }

    fun powerOffBand(side: Side) {
        val s = strings
        scope.launch { if (!ble.command(side, BandProtocol.powerOff(s.bandOffFromApp, ""))) _toasts.tryEmit(s.bandNotReady) }
    }

    /** Spegne i braccialetti collegati; [line2] è il testo sotto a "SPEGNIMENTO" per ciascun lato. */
    private suspend fun powerOffBands(line1: String, line2: (Side) -> String) {
        withTimeoutOrNull(3_000) {
            coroutineScope {
                Side.entries.map { side -> async { ble.command(side, BandProtocol.powerOff(line1, line2(side))) } }.awaitAll()
            }
        }
    }

    private fun bandLabel(side: Side) = if (side == Side.P1) "1" else "2"

    /**
     * Stato batteria ogni minuto: stima dell'autonomia sul consumo reale, avvisi al 20 % e al 10 %,
     * registro CSV (files/battery_log.csv) per verificare quanto dura il braccialetto.
     */
    private fun onBandStatus(side: Side, st: BandStatus) {
        val now = SystemClock.elapsedRealtime()
        val soc = BatteryModel.soc(st.millivolts)
        val samples = batterySamples.getOrPut(side) { mutableListOf() }
        // Braccialetto riacceso (i contatori ripartono) o in carica: si ricomincia a misurare.
        val rebooted = lastStatus[side]?.let { st.uptimeS < it.uptimeS } == true
        lastStatus[side] = st
        if (st.charging || rebooted) samples.clear()
        if (!st.charging) {
            if (samples.isEmpty()) statusStart[side] = st
            samples += now to soc
        }
        // teniamo al massimo le ultime 3 ore di campioni
        while (samples.isNotEmpty() && now - samples.first().first > 3 * 3_600_000L) samples.removeAt(0)
        val hours = if (st.charging) null else BatteryModel.hoursLeft(samples)
        val shown = if (st.charging) BatteryModel.shownPercent(st) else soc
        bandBattery.value = bandBattery.value + (side to BandBattery(shown, hours, st.charging, st.full))
        calibrate(side, st, samples)
        android.util.Log.i("BandStatus", "${bandLabel(side)} mv=${st.millivolts} soc=$soc shown=$shown chg=${st.charging} full=${st.full} usb=${st.usbMv} up=${st.uptimeS}s dsp=${st.displayS}s left=${hours?.let { "%.1fh".format(it) } ?: "-"}")
        scope.launch(io) {
            runCatching {
                java.io.File(app.filesDir, "battery_log.csv").appendText(
                    "${System.currentTimeMillis()},${bandLabel(side)},${st.millivolts},$shown,${if (st.charging) 1 else 0},${st.uptimeS},${st.displayS},${st.usbMv ?: ""},${if (st.full) 1 else 0}\n",
                )
            }
        }
        live.value?.let { lm ->
            if (lm.record.startedAt != null && side !in lm.record.batteryStart && !st.charging) {
                setRecord(lm.record.copy(batteryStart = lm.record.batteryStart + (side to soc)))
            }
        }
        if (!st.charging && screen.value == Screen.MATCH) {
            val level = when {
                soc <= 10 -> 10
                soc <= 20 -> 20
                else -> null
            }
            if (level != null && (batteryWarned[side] ?: 101) > level) {
                batteryWarned[side] = level
                showMessage(strings.msgBandBatteryLow(bandLabel(side), soc))
                ble.send(side, BandProtocol.message(strings.bandBatteryLow, "$soc%", 4))
            }
        }
    }

    /**
     * Consumo di base misurato: calo della carica meno il display acceso nel frattempo (lo conta il braccialetto).
     * Si salva per indirizzo, così la stima nelle impostazioni migliora a ogni uso.
     */
    private fun calibrate(side: Side, st: BandStatus, samples: List<Pair<Long, Int>>) {
        val band = ble.bands.value[side] ?: return
        val settings = band.settings ?: return
        val start = statusStart[side] ?: return
        val drain = BatteryModel.drainPerHour(samples) ?: return
        val upS = st.uptimeS - start.uptimeS
        if (upS <= 0) return
        val duty = ((st.displayS - start.displayS).toDouble() / upS).coerceIn(0.0, 1.0)
        val base = BatteryModel.baseFromMeasure(drain, duty, settings)
        storage.setBandBaseMa(band.address, base)
        bandBaseMa.value = bandBaseMa.value + (band.address to base)
    }

    /** Batteria bassa già all'inizio: meglio saperlo prima di giocare. */
    private fun warnLowBandsAtStart() {
        if (options.value.mode != PlayMode.BANDS) return
        for ((side, b) in bandBattery.value) {
            if (!b.charging && b.percent < 30) showMessage(strings.msgBandBatteryLow(bandLabel(side), b.percent))
        }
    }

    private suspend fun watchBands() {
        var prev: Map<Side, LinkState> = emptyMap()
        ble.bands.collect { bands ->
            if (screen.value == Screen.MATCH && options.value.mode == PlayMode.BANDS) {
                for ((side, info) in bands) {
                    val before = prev[side]
                    if (before == LinkState.READY && info.state == LinkState.IDLE) showMessage(strings.msgBandLost(bandLabel(side)))
                }
            }
            prev = bands.mapValues { it.value.state }
            for ((side, info) in bands) {
                // Nome cambiato dalle impostazioni: lo si ricorda per la prossima volta.
                val n = info.settings?.name
                if (!n.isNullOrEmpty() && storage.bandAddress(side == Side.P1) == info.address && storage.bandName(side == Side.P1) != n) {
                    storage.setBand(side == Side.P1, info.address, n)
                }
                if (info.address !in bandBaseMa.value) storage.bandBaseMa(info.address)?.let { bandBaseMa.value = bandBaseMa.value + (info.address to it) }
            }
            syncBandLanguage(bands)
        }
    }

    /**
     * I braccialetti dal firmware 2.2 parlano la lingua dell'app: la si manda quando si collegano e quando
     * la si cambia. Quelli più vecchi non dichiarano la lingua e restano in italiano.
     */
    private fun syncBandLanguage(bands: Map<Side, BandInfo>) {
        val want = options.value.lang.code
        for ((side, info) in bands) {
            val have = info.settings?.lang
            if (info.state != LinkState.READY || have.isNullOrEmpty()) {
                bandLangSent.remove(side)
                continue
            }
            if (have == want || bandLangSent[side] == want) continue
            bandLangSent[side] = want
            scope.launch { if (!ble.writeLanguage(side, want)) bandLangSent.remove(side) }
        }
    }

    private fun onBandReady(side: Side) {
        scope.launch {
            // Il braccialetto mostra 3 secondi "PAIRING OK", poi a chi è associato, poi il punteggio.
            delay(3_200)
            ble.send(side, BandProtocol.message(strings.bandPaired, names().short(side), 3))
            if (screen.value == Screen.MATCH) {
                showMessage(strings.msgBandConnected(bandLabel(side)))
                delay(3_200)
                live.value?.let { pushScore(it.state, null, only = side) }
            }
        }
    }

    private fun onBandEvent(e: BandEvent) {
        val now = SystemClock.elapsedRealtime()
        when (e.type) {
            BandProtocol.EVT_POWER_OFF -> if (screen.value == Screen.MATCH && options.value.mode == PlayMode.BANDS) {
                showMessage(strings.msgBandOff(bandLabel(e.side), e.reason))
            }
            BandProtocol.EVT_POINT -> when (screen.value) {
                Screen.START -> startMatch()
                // Finché la voce non ha detto "gioco" i KEY1 servono solo ad avviare: niente punti sullo 0-0.
                Screen.MATCH -> if (!endDialog.value && live.value?.record?.startedAt != null) awardPoint(e.side, fromBand = true)
                else -> Unit
            }
            // KEY2 annulla l'ultimo punto, anche dal popup di fine partita. Non può mai confermare la fine.
            // Due KEY2 ravvicinati (anche da braccialetti diversi) annullano un solo punto.
            BandProtocol.EVT_UNDO -> if (screen.value == Screen.MATCH && now - lastBandUndoAt >= BAND_GAP_MS) {
                lastBandUndoAt = now
                undo()
            }
        }
    }

    /** Invia ai braccialetti il punteggio visto da chi li indossa. */
    private fun pushScore(state: MatchState, t: Transition?, only: Side? = null) {
        if (options.value.mode != PlayMode.BANDS) return
        val s = strings
        for (side in listOf(Side.P1, Side.P2)) {
            if (only != null && side != only) continue
            val o = side.other
            val payload = when {
                state.isFinished -> BandProtocol.message(s.bandGameSetMatch, Reports.scoreLine(state, side), 15)
                t?.setWinner != null -> {
                    val set = state.sets.last()
                    BandProtocol.games(set.games(side), set.games(o), state.setsWon(side), state.setsWon(o), "${s.bandSet} ${state.sets.size}")
                }
                t?.gameWinner != null -> BandProtocol.games(
                    state.games(side), state.games(o), state.setsWon(side), state.setsWon(o),
                    when {
                        t.tiebreakStarted -> s.bandTiebreak
                        t.changeEnds -> s.bandChangeEnds
                        else -> ""
                    },
                )
                else -> BandProtocol.point(
                    state.pointLabel(side), state.pointLabel(o),
                    if (state.server == side) 1 else 2,
                    when {
                        t?.changeEnds == true -> s.bandChangeEnds
                        state.inTiebreak -> s.bandTiebreak
                        else -> ""
                    },
                )
            }
            ble.send(side, payload)
        }
    }

    private fun bandMessage(line1: String, line2: String, seconds: Int) {
        if (options.value.mode != PlayMode.BANDS) return
        Side.entries.forEach { ble.send(it, BandProtocol.message(line1, line2, seconds)) }
    }

    // ---------------------------------------------------------------- partita

    fun startMatch() {
        if (screen.value != Screen.START) return
        val o = options.value
        val su = setup.value
        val rules = RulesConfig(
            format = o.format,
            noAd = o.noAd,
            doubles = su.doubles,
            firstServer = o.firstServer,
            p1StartsLeft = o.p1Left,
            firstServerP1 = if (su.doubles) o.firstServerP1 else 0,
            firstServerP2 = if (su.doubles) o.firstServerP2 else 0,
        )
        val now = System.currentTimeMillis()
        val rec = MatchRecord(id = "m$now", setup = su, options = o, rules = rules, updatedAt = now)
        val state = ScoreEngine.initial(rules)
        live.value = LiveMatch(rec, state)
        summary.value = null
        runningSince = null
        clockMs.value = 0
        endDialog.value = false
        serveOrderPrompt.value = false
        stopCountdown()
        lastPointAt = 0
        screen.value = Screen.MATCH
        persist()
        if (needsService()) MatchService.start(app)
        batteryWarned.clear()
        warnLowBandsAtStart()
        fetchLocation()
        // "Primo set" · "[nome] al servizio" · "gioco": il tempo partita parte su "gioco".
        announcer.announce(calls().start(state, names())) { tag -> if (tag == CallBuilder.TAG_PLAY) onPlay() }
    }

    private fun onPlay() {
        val lm = live.value ?: return
        if (lm.record.startedAt != null || lm.record.suspended) return
        runningSince = SystemClock.elapsedRealtime()
        val startBattery = if (options.value.mode == PlayMode.BANDS) {
            bandBattery.value.filterValues { !it.charging }.mapValues { it.value.percent }
        } else emptyMap()
        setRecord(lm.record.copy(startedAt = System.currentTimeMillis(), batteryStart = startBattery))
        startCountdown(CountdownKind.SHOT_CLOCK, SHOT_CLOCK_S)
        bandMessage(strings.bandPlay, "0 - 0", 3)
    }

    fun awardPoint(side: Side, fromBand: Boolean = false) {
        val lm = live.value ?: return
        if (closing || lm.record.suspended || lm.state.isFinished || endDialog.value) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastPointAt < if (fromBand) BAND_GAP_MS else TAP_GAP_MS) return
        // Il secondo tocco di un doppio tocco su "Annulla" può cadere su un tasto punto (o il popup di fine
        // partita sparisce sotto il dito): non è un punto.
        if (!fromBand && now - lastUndoAt < TAP_GAP_MS) return
        lastPointAt = now
        if (lm.record.startedAt == null) onPlay()
        val current = live.value ?: return
        val step = ScoreEngine.pointWonBy(current.state, side)
        val t = step.transition ?: return
        val rec = current.record.copy(events = current.record.events + MatchEvent.Point(side, System.currentTimeMillis()))
        serveOrderPrompt.value = false
        commit(rec, step.state)
        afterTransition(step.state, t)
    }

    private fun afterTransition(state: MatchState, t: Transition) {
        val n = names()
        announcer.announce(calls().afterPoint(state, t, n))
        pushScore(state, t)
        when {
            t.matchWinner != null -> {
                stopClock()
                stopCountdown()
                live.value?.let { setRecord(it.record.copy(endedAt = System.currentTimeMillis())) }
                persist()
                message.value = null
                endDialog.value = true
                return
            }
            t.setWinner != null -> startCountdown(CountdownKind.SET_BREAK, SET_BREAK_S)
            t.tiebreakStarted -> startCountdown(CountdownKind.TIEBREAK_BREAK, WALK_S)
            t.changeEnds -> startCountdown(
                CountdownKind.CHANGEOVER,
                if (t.inTiebreak || t.firstGameOfSet) WALK_S else CHANGEOVER_S,
            )
            else -> startCountdown(CountdownKind.SHOT_CLOCK, SHOT_CLOCK_S)
        }
        val s = strings
        val msg = when {
            t.setWinner != null -> buildString {
                append(s.msgSetWon(n.short(t.setWinner)))
                if (t.matchTiebreakStarted) append(" · ").append(s.msgMatchTiebreak)
                if (t.changeEnds) append(" · ").append(s.msgChangeEnds)
            }
            t.tiebreakStarted -> s.msgTiebreak
            t.changeEnds -> s.msgChangeEnds
            else -> pressureMessage(state)
        }
        if (msg != null) showMessage(msg)
        showTvMessage(msg)
        if (t.setWinner != null && state.rules.doubles) serveOrderPrompt.value = true
    }

    /** Match point / set point / palla break / punto decisivo per il prossimo punto. */
    private fun pressureMessage(state: MatchState): String? {
        val s = strings
        val next = Side.entries.mapNotNull { ScoreEngine.lookahead(state, it) }
        return when {
            next.any { it.matchWinner != null } -> s.msgMatchPoint
            next.any { it.setWinner != null } -> s.msgSetPoint
            // Nel No-Ad sul 40-40 chi riceve vince il game col prossimo punto, quindi è sempre anche palla break:
            // il punto decisivo va controllato prima.
            state.rules.noAd && state.isDeuce -> s.msgDecidingPoint
            ScoreEngine.isBreakPoint(state) -> s.msgBreakPoint
            else -> null
        }
    }

    /** Annulla l'ultimo punto: si rigiocano gli eventi, quindi funziona anche dopo fine game, set o partita. */
    fun undo() {
        val lm = live.value ?: return
        if (closing) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastUndoAt < UNDO_GAP_MS) return
        val events = lm.record.events.toMutableList()
        while (events.isNotEmpty() && events[events.lastIndex] !is MatchEvent.Point) events.removeAt(events.lastIndex)
        if (events.isEmpty()) return
        events.removeAt(events.lastIndex)
        lastUndoAt = now
        val wasFinished = lm.state.isFinished
        val state = ScoreEngine.replay(lm.record.rules, events)
        val rec = lm.record.copy(events = events, endedAt = if (wasFinished) null else lm.record.endedAt)
        lastPointAt = 0
        serveOrderPrompt.value = false
        commit(rec, state)
        if (wasFinished) {
            endDialog.value = false
            if (!rec.suspended) runningSince = SystemClock.elapsedRealtime()
        }
        if (!rec.suspended) startCountdown(CountdownKind.SHOT_CLOCK, SHOT_CLOCK_S)
        announcer.announce(calls().correction(state, names()))
        pushScore(state, null)
        showMessage(strings.msgPointUndone)
        showTvMessage(null)
    }

    fun setServeOrder(firstP1: Int, firstP2: Int) {
        serveOrderPrompt.value = false
        val lm = live.value ?: return
        if (!ScoreEngine.canChangeServeOrder(lm.state)) return
        if (firstP1 == lm.state.order1 && firstP2 == lm.state.order2) return
        val rec = lm.record.copy(events = lm.record.events + MatchEvent.ServeOrder(lm.state.setNumber, firstP1, firstP2))
        commit(rec, ScoreEngine.replay(rec.rules, rec.events))
    }

    fun toggleSuspend() {
        val lm = live.value ?: return
        if (lm.state.isFinished) return
        val s = strings
        if (lm.record.suspended) {
            runningSince = SystemClock.elapsedRealtime()
            setRecord(lm.record.copy(suspended = false, startedAt = lm.record.startedAt ?: System.currentTimeMillis()))
            lastPointAt = 0
            startCountdown(CountdownKind.SHOT_CLOCK, SHOT_CLOCK_S)
            announcer.announce(calls().resume())
            showMessage(s.msgResumed)
            pushScore(lm.state, null)
            if (needsService()) MatchService.start(app)
        } else {
            val total = currentClock()
            runningSince = null
            setRecord(lm.record.copy(suspended = true, clockMs = total))
            stopCountdown()
            announcer.stop()
            showMessage(s.msgSuspended)
            bandMessage(s.bandSuspended, "", 5)
        }
        persist()
    }

    fun toggleAudio() = updateOptions { it.copy(audio = !it.audio) }

    /** "Partita conclusa": solo dal telefono, mai dai braccialetti. */
    fun confirmEnd() {
        val lm = live.value ?: return
        if (!lm.state.isFinished) return
        val endBattery = if (lm.record.options.mode == PlayMode.BANDS) {
            bandBattery.value.filterValues { !it.charging }.mapValues { it.value.percent }
        } else emptyMap()
        val rec = lm.record.copy(
            finished = true, suspended = false, clockMs = currentClock(), updatedAt = System.currentTimeMillis(),
            batteryEnd = endBattery,
        )
        runningSince = null
        scope.launch(io) {
            storage.deleteMatch(rec.id)
            storage.saveLastFinished(rec)
        }
        // Se Android chiude il processo sul riepilogo (es. mentre si condivide) lo si riapre: vedi onActivityCreated.
        storage.summaryOpen = rec.id
        summaryKept.value = false
        summary.value = LiveMatch(rec, lm.state)
        live.value = null
        endDialog.value = false
        stopCountdown()
        screen.value = Screen.SUMMARY
        // Col tabellone acceso il servizio resta (il risultato è ancora sul tabellone): si riavvia solo per il testo della notifica.
        if (tv.value.enabled) MatchService.start(app) else MatchService.stop(app)
        // I braccialetti hanno finito: si spengono subito invece di aspettare l'inattività.
        if (rec.options.mode == PlayMode.BANDS && options.value.bandsOffAtEnd) {
            val s = strings
            scope.launch { powerOffBands(s.bandMatchOver) { side -> Reports.scoreLine(lm.state, side) } }
        }
    }

    /** Torna alla prima schermata. Una partita non finita resta salvata tra le sospese. */
    fun newMatch() {
        live.value?.let { lm ->
            val total = currentClock()
            runningSince = null
            live.value = LiveMatch(lm.record.copy(suspended = true, clockMs = total), lm.state)
            persist()
        }
        announcer.stop()
        live.value = null
        summary.value = null
        storage.summaryOpen = null
        runningSince = null
        clockMs.value = 0
        stopCountdown()
        endDialog.value = false
        serveOrderPrompt.value = false
        message.value = null
        if (tv.value.enabled) MatchService.start(app) else MatchService.stop(app)
        updateOptions { it.copy(tossWinner = null) }
        refreshSaved()
        screen.value = Screen.SETUP
    }

    fun resumeSaved(rec: MatchRecord) {
        val cur = options.value
        val o = rec.options.copy(
            mode = cur.mode, lang = cur.lang, audio = cur.audio,
            ttsEngine = cur.ttsEngine, ttsVoice = cur.ttsVoice, voiceFiles = cur.voiceFiles,
        )
        setup.value = rec.setup
        storage.setup = rec.setup
        options.value = o
        storage.options = o
        val state = ScoreEngine.replay(rec.rules, rec.events)
        live.value = LiveMatch(rec.copy(options = o, suspended = !state.isFinished), state)
        summary.value = null
        runningSince = null
        clockMs.value = rec.clockMs
        stopCountdown()
        lastPointAt = 0
        endDialog.value = state.isFinished
        serveOrderPrompt.value = false
        screen.value = Screen.MATCH
        if (!state.isFinished) showMessage(strings.msgSuspended)
        if (rec.location == null) fetchLocation()
        if (o.mode == PlayMode.BANDS) ble.reconnectAll()
        if (needsService()) MatchService.start(app)
        pushScore(state, null)
    }

    fun deleteSaved(rec: MatchRecord) {
        scope.launch {
            withContext(io) { storage.deleteMatch(rec.id) }
            refreshSaved()
        }
    }

    fun refreshSaved() {
        scope.launch { saved.value = withContext(io) { storage.loadUnfinished() } }
    }

    // ---------------------------------------------------------------- tempi

    private fun currentClock(now: Long = SystemClock.elapsedRealtime()): Long {
        val base = live.value?.record?.clockMs ?: 0L
        return base + (runningSince?.let { now - it } ?: 0L)
    }

    private fun stopClock() {
        val lm = live.value ?: return
        val total = currentClock()
        runningSince = null
        setRecord(lm.record.copy(clockMs = total))
        clockMs.value = total
    }

    private fun startCountdown(kind: CountdownKind, seconds: Int) {
        cdKind = kind
        cdEnd = SystemClock.elapsedRealtime() + seconds * 1000L
        countdown.value = CountdownUi(kind, seconds)
        tvCountdownEnd.value = cdEnd
    }

    private fun stopCountdown() {
        cdKind = null
        countdown.value = null
        tvCountdownEnd.value = null
    }

    private fun tick() {
        val now = SystemClock.elapsedRealtime()
        if (live.value != null) clockMs.value = currentClock(now)
        cdKind?.let { k ->
            val rem = ((cdEnd - now + 999) / 1000).coerceAtLeast(0).toInt()
            if (rem == 0 && k != CountdownKind.SHOT_CLOCK) {
                // Finita la pausa parte lo shot clock di 25".
                startCountdown(CountdownKind.SHOT_CLOCK, SHOT_CLOCK_S)
            } else if (countdown.value?.seconds != rem || countdown.value?.kind != k) {
                countdown.value = CountdownUi(k, rem)
            }
        }
        if (runningSince != null && now - lastAutosave > 15_000) persist()
    }

    fun showMessage(text: String) {
        message.value = text
        messageJob?.cancel()
        messageJob = scope.launch {
            delay(MESSAGE_MS)
            message.value = null
        }
    }

    // ---------------------------------------------------------------- salvataggi

    private fun commit(rec: MatchRecord, state: MatchState) {
        live.value = LiveMatch(rec, state)
        persist()
    }

    private fun setRecord(rec: MatchRecord) {
        live.value?.let { live.value = it.copy(record = rec) }
    }

    /** Salvataggio automatico su file a ogni punto (e ogni 15" col tempo che scorre). */
    fun persist() {
        // In chiusura il file giusto l'ha già scritto shutdown(): un salvataggio dopo lo sovrascriverebbe.
        if (closing) return
        val lm = live.value ?: return
        lastAutosave = SystemClock.elapsedRealtime()
        val snapshot = lm.record.copy(clockMs = currentClock(), updatedAt = System.currentTimeMillis())
        scope.launch(io) { storage.saveMatch(snapshot) }
    }

    private fun fetchLocation() {
        val id = (live.value ?: summary.value)?.record?.id ?: return
        if (locationJob?.isActive == true && locationFor == id) return
        locationJob?.cancel()
        locationFor = id
        locationJob = scope.launch {
            val loc = LocationHelper.current(app) ?: return@launch
            updateLocation(id, MatchLocation(loc.latitude, loc.longitude))
            val address = LocationHelper.address(app, loc, Reports.locale(options.value.lang))
            if (address != null) updateLocation(id, MatchLocation(loc.latitude, loc.longitude, address))
        }
    }

    private fun updateLocation(id: String, loc: MatchLocation) {
        live.value?.let { if (it.record.id == id) { setRecord(it.record.copy(location = loc)); persist() } }
        summary.value?.let {
            if (it.record.id == id) {
                val rec = it.record.copy(location = loc)
                summary.value = it.copy(record = rec)
                scope.launch(io) { storage.saveLastFinished(rec) }
            }
        }
    }

    // ---------------------------------------------------------------- voce

    fun refreshVoiceCount() {
        val l = options.value.lang
        scope.launch {
            val (gen, rec) = withContext(io) {
                voice.refresh(l)
                voice.generatedCount(l) to voice.customCount(l)
            }
            voiceCount.value = gen
            customVoiceCount.value = rec
        }
    }

    fun generateVoice() {
        if (voiceProgress.value != null) return
        val l = options.value.lang
        scope.launch {
            voiceProgress.value = 0 to com.tennis.scoremanager.voice.Phrases.keys.size
            val r = try {
                announcer.generateVoicePack(l) { i, n -> voiceProgress.value = i to n }
            } finally {
                voiceProgress.value = null
            }
            refreshVoiceCount()
            _toasts.tryEmit(if (r.installed) strings.voiceFiles(r.created, r.total) else strings.voiceGenerationFailed)
        }
    }

    fun importVoiceZip(uri: Uri) {
        val l = options.value.lang
        scope.launch {
            // ZIP rovinato o troncato: le registrazioni restano com'erano e si avvisa (non "0 file").
            val n = withContext(io) {
                runCatching { voice.importZip(uri, l) }.onFailure { android.util.Log.w("Voice", "Import ZIP", it) }.getOrNull()
            }
            refreshVoiceCount()
            _toasts.tryEmit(if (n == null) strings.voiceImportFailed else strings.voiceFiles(n, com.tennis.scoremanager.voice.Phrases.keys.size))
        }
    }

    fun deleteCustomVoice() {
        val l = options.value.lang
        scope.launch {
            withContext(io) { voice.deleteCustom(l) }
            refreshVoiceCount()
        }
    }

    /** Prova voce; lo stesso tasto la ferma ([stopVoiceTest]). */
    fun testVoice() {
        val n = names()
        announcer.announce(
            force = true,
            name = VOICE_TEST,
            segs = listOf(Seg.Clip("first_set"), Seg.Pause(700)) + calls().toServe(n.side(Side.P1)) + listOf(
                Seg.Pause(700),
                Seg.Clip("score_1_0"), Seg.Pause(500),
                Seg.Clip("deuce"), Seg.Pause(500),
                Seg.Clip("advantage"), Seg.Say(n.side(Side.P2)), Seg.Pause(500),
                Seg.Clip("game"), Seg.Say(n.side(Side.P1)), Seg.Pause(300),
                Seg.Say(n.side(Side.P1)), Seg.Clip("leads"), Seg.Clip("games_1_0"), Seg.Pause(500),
                Seg.Clip("change_ends"), Seg.Pause(700),
                Seg.Clip("game"), Seg.Say(n.side(Side.P2)), Seg.Pause(300),
                Seg.Clip("games_all_6"), Seg.Clip("tiebreak"),
            ),
        )
    }

    /** Ferma la prova voce (e solo quella: una chiamata di partita non si tocca). */
    fun stopVoiceTest() {
        if (announcer.playing.value == VOICE_TEST) announcer.stop()
    }

    // ---------------------------------------------------------------- riepilogo

    fun summaryNames(): Names = names(summary.value?.record?.setup ?: setup.value)

    fun defaultFileName(): String {
        val sm = summary.value ?: return "TSM"
        return Reports.fileBaseName(sm.record, summaryNames())
    }

    fun folderLabel(): String {
        val tree = storage.historyTree ?: return strings.defaultFolder
        return runCatching { DocumentsContract.getTreeDocumentId(Uri.parse(tree)).substringAfter(':').ifBlank { "/" } }
            .getOrDefault(strings.defaultFolder)
    }

    fun setHistoryFolder(uri: Uri?) {
        if (uri != null) {
            runCatching {
                app.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        }
        storage.historyTree = uri?.toString()
    }

    /** Salva nello storico con nome e cartella scelti dall'utente. */
    fun saveHistory(name: String, txt: Boolean, json: Boolean, png: Boolean) {
        val sm = summary.value ?: return
        val s = strings
        val n = summaryNames()
        val base = name.trim().ifEmpty { defaultFileName() }.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val tree = storage.historyTree
        scope.launch {
            val where = withContext(io) {
                runCatching {
                    val outputs = buildList<Triple<String, String, (OutputStream) -> Unit>> {
                        if (txt) add(Triple("$base.txt", "text/plain") { o -> o.write(Reports.text(sm.record, sm.state, s, n).toByteArray()) })
                        if (json) add(Triple("$base.json", "application/json") { o ->
                            o.write(storage.json.encodeToString(MatchRecord.serializer(), sm.record).toByteArray())
                        })
                        if (png) add(Triple("$base.png", "image/png") { o ->
                            Reports.renderCard(sm.record, sm.state, s, n).compress(Bitmap.CompressFormat.PNG, 100, o)
                        })
                    }
                    if (tree != null) {
                        val treeUri = Uri.parse(tree)
                        val parent = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
                        for ((file, mime, write) in outputs) {
                            val doc = DocumentsContract.createDocument(app.contentResolver, parent, mime, file) ?: error("createDocument")
                            app.contentResolver.openOutputStream(doc)?.use(write) ?: error("openOutputStream")
                        }
                        folderLabel()
                    } else {
                        // Come fa il selettore di sistema: "Finale (1).txt" invece di sovrascrivere "Finale.txt".
                        val dir = storage.defaultHistoryDir
                        val suffix = generateSequence(0) { it + 1 }.map { if (it == 0) "" else " ($it)" }
                            .first { sfx -> outputs.none { (file, _, _) -> File(dir, withSuffix(file, sfx)).exists() } }
                        for ((file, _, write) in outputs) File(dir, withSuffix(file, suffix)).outputStream().use(write)
                        dir.absolutePath
                    }
                }.getOrNull()
            }
            if (where != null) summaryKept.value = true
            _toasts.tryEmit(if (where != null) s.savedTo(where) else s.saveError)
        }
    }

    private fun withSuffix(file: String, suffix: String) =
        if (suffix.isEmpty()) file else file.substringBeforeLast('.') + suffix + "." + file.substringAfterLast('.')

    /** Il riepilogo è stato condiviso: c'è una copia fuori dall'app. */
    fun markSummaryKept() {
        summaryKept.value = true
    }

    /** Immagine + testo del risultato da condividere sui social. */
    fun shareIntent(): Intent? {
        val sm = summary.value ?: return null
        val s = strings
        val n = summaryNames()
        val dir = File(app.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "${Reports.fileBaseName(sm.record, n)}.png")
        runCatching { file.outputStream().use { Reports.renderCard(sm.record, sm.state, s, n).compress(Bitmap.CompressFormat.PNG, 100, it) } }
            .onFailure { return null }
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, Reports.text(sm.record, sm.state, s, n))
            putExtra(Intent.EXTRA_SUBJECT, s.shareSubject)
            clipData = ClipData.newRawUri("", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, s.share)
    }

    /** "Esci": l'app si chiude davvero e alla prossima apertura riparte dalla prima schermata. */
    fun exitApp(activity: Activity) = shutdown { activity.finishAndRemoveTask() }

    /** L'app è stata tolta dalle app recenti mentre il servizio della partita era attivo: stessa cosa di "Esci". */
    fun onTaskRemoved(stopService: () -> Unit) = shutdown(stopService)

    /**
     * Chiusura: la partita in corso resta tra le sospese (salvata prima di tutto), i braccialetti si spengono
     * (se l'opzione è attiva), poi si chiude il processo. Senza questo Android lo tiene in vita e l'app
     * riaprirebbe esattamente dov'era.
     */
    private fun shutdown(finish: () -> Unit) {
        if (closing) return
        closing = true
        val s = strings
        storage.summaryOpen = null
        scope.launch {
            announcer.stop()
            live.value?.let { lm ->
                val snapshot = lm.record.copy(
                    suspended = !lm.state.isFinished, clockMs = currentClock(), updatedAt = System.currentTimeMillis(),
                )
                runningSince = null
                // Anche in memoria: nei secondi in cui si spengono i braccialetti nulla deve ripartire dal vecchio stato.
                live.value = lm.copy(record = snapshot)
                withContext(io) { storage.saveMatch(snapshot) }
            }
            if (options.value.mode == PlayMode.BANDS && options.value.bandsOffAtEnd) powerOffBands(s.bandAppClosed) { "" }
            ble.disconnectAll()
            MatchService.stop(app)
            finish()
            delay(400)
            android.os.Process.killProcess(android.os.Process.myPid())
        }
    }

    /** Chiamato quando l'Activity va in secondo piano. */
    fun onBackground() = persist()

    /**
     * Prima Activity del processo. [restored] = Android l'ha ricreata dopo aver chiuso il processo (l'app era
     * nelle recenti, non è stata chiusa con "Esci"): se era sul riepilogo lo si riapre, altrimenti andrebbe perso.
     * Un'apertura normale riparte invece dalla prima schermata.
     */
    fun onActivityCreated(restored: Boolean) {
        if (restoreChecked) return
        restoreChecked = true
        if (!restored || live.value != null || summary.value != null || screen.value != Screen.SETUP) return
        val id = storage.summaryOpen ?: return
        val rec = storage.loadLastFinished()?.takeIf { it.id == id } ?: return
        summary.value = LiveMatch(rec, ScoreEngine.replay(rec.rules, rec.events))
        summaryKept.value = false
        screen.value = Screen.SUMMARY
    }

    /**
     * L'app torna in primo piano. Se la partita è partita da un braccialetto a schermo bloccato la posizione
     * non è arrivata (Android la nega in background): la si chiede ora.
     */
    fun onForeground() {
        val lm = live.value
        val sm = summary.value
        when {
            lm != null -> if (lm.record.location == null) fetchLocation()
            sm != null -> {
                val ended = sm.record.endedAt ?: return
                if (sm.record.location == null && System.currentTimeMillis() - ended < LATE_LOCATION_MS) fetchLocation()
            }
        }
    }
}
TSM_EOF

# ---------------------------------------------------------------- app/src/main/java/com/tennis/scoremanager/TsmApp.kt
cat > "$DEST/app/src/main/java/com/tennis/scoremanager/TsmApp.kt" << 'TSM_EOF'
package com.tennis.scoremanager

import android.app.Application
import com.tennis.scoremanager.ble.BleManager
import com.tennis.scoremanager.data.Storage
import com.tennis.scoremanager.voice.Announcer
import com.tennis.scoremanager.voice.VoicePack

class TsmApp : Application() {

    lateinit var controller: MatchController
        private set

    override fun onCreate() {
        super.onCreate()
        val voice = VoicePack(this)
        controller = MatchController(this, Storage(this), BleManager(this), voice, Announcer(this, voice))
    }
}
TSM_EOF

# ---------------------------------------------------------------- app/src/main/java/com/tennis/scoremanager/ble/BandProtocol.kt
cat > "$DEST/app/src/main/java/com/tennis/scoremanager/ble/BandProtocol.kt" << 'TSM_EOF'
package com.tennis.scoremanager.ble

import java.text.Normalizer
import java.util.UUID

/**
 * Protocollo BLE condiviso con il firmware del braccialetto (TSM_Band.ino): tenere allineati gli UUID.
 *
 * Braccialetto -> telefono (notify su EVENT): [tipo, sequenza] più, dal firmware 2.0, il motivo dello spegnimento;
 * dal firmware 2.3 anche [id di accensione (4 byte, little endian), età dell'evento in decimi di secondo]: vedi [parseEvent].
 * Telefono -> braccialetto (write su DISPLAY): testo ASCII con campi separati da '|':
 *   P|<mio>|<avversario>|<servizio 0/1/2>|<intestazione>        punteggio del game (grande)
 *   G|<miei game>|<game avv>|<miei set>|<set avv>|<intestazione> riepilogo a fine game
 *   M|<riga 1>|<riga 2>|<secondi>                                messaggio
 *   I|<riga 1>|<riga 2>|<secondi>|<RRGGBB>                       "Identifica": lampeggia e suona (firmware 2.0)
 *   O|<riga 1>|<riga 2>                                          si spegne (firmware 2.0)
 *   H|1                                                          confermo gli eventi (app 2.4): prima delle notifiche
 *   K|<sequenza>                                                 conferma di un evento (solo ai firmware 2.3)
 * "mio" è sempre il giocatore che indossa il braccialetto; servizio 1 = serve lui, 2 = serve l'avversario.
 * I firmware vecchi ignorano i tipi che non conoscono.
 *
 * Conferma dei tasti (firmware 2.3 + app 2.4): il braccialetto tiene in coda KEY1/KEY2 finché l'app non li conferma
 * e suona il bip di conferma solo allora; dopo una riconnessione rimanda quelli non confermati (fino a 6,5 s dalla
 * pressione) e a 8 s li dà per persi ("NON INVIATO"). L'app conferma anche i doppioni ma li applica una volta sola
 * ([EventDedupe]). Senza "H|1" (app vecchia) il firmware 2.3 suona all'invio come prima; senza id di accensione
 * (firmware vecchio) l'app non manda conferme.
 */
object BandProtocol {
    val SERVICE: UUID = UUID.fromString("7a1e0001-5c3b-4f6e-9d2a-3e7b1c9a0f10")
    val EVENT: UUID = UUID.fromString("7a1e0002-5c3b-4f6e-9d2a-3e7b1c9a0f10")
    val DISPLAY: UUID = UUID.fromString("7a1e0003-5c3b-4f6e-9d2a-3e7b1c9a0f10")
    /** Stato batteria ogni minuto (notify): vedi [BatteryModel.parse]. Assente nei firmware vecchi. */
    val STATUS: UUID = UUID.fromString("7a1e0004-5c3b-4f6e-9d2a-3e7b1c9a0f10")
    /** Impostazioni del braccialetto (lettura, scrittura, notify): vedi [BandSettings]. Dal firmware 2.0. */
    val CONFIG: UUID = UUID.fromString("7a1e0005-5c3b-4f6e-9d2a-3e7b1c9a0f10")
    val BATTERY_SERVICE: UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
    val BATTERY_LEVEL: UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
    val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    const val EVT_POINT = 1      // KEY1 pressione corta: punto a chi indossa il braccialetto
    const val EVT_UNDO = 2       // KEY2 pressione corta: annulla l'ultimo punto
    const val EVT_POWER_OFF = 3  // il braccialetto si spegne (terzo byte: OFF_*)
    const val EVT_BATTERY = 4    // KEY1 pressione lunga: mostra la batteria (solo informativo)

    // Motivo dello spegnimento (terzo byte di EVT_POWER_OFF)
    const val OFF_KEY = 0        // KEY2 tenuto premuto
    const val OFF_IDLE = 1       // collegato ma inattivo troppo a lungo
    const val OFF_BATTERY = 2    // batteria scarica
    const val OFF_APP = 3        // l'ha chiesto l'app
    const val OFF_TIMEOUT = 4    // nessun telefono

    /** Il font del braccialetto è ASCII: niente accenti, niente '|', maiuscolo. */
    fun clean(s: String, max: Int = 18): String {
        val plain = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        return plain.uppercase().replace('|', '/').filter { it.code in 32..126 }.take(max).trim()
    }

    fun point(mine: String, theirs: String, serve: Int, header: String) =
        "P|${clean(mine, 3)}|${clean(theirs, 3)}|$serve|${clean(header)}"

    fun games(myGames: Int, theirGames: Int, mySets: Int, theirSets: Int, header: String) =
        "G|$myGames|$theirGames|$mySets|$theirSets|${clean(header)}"

    fun message(line1: String, line2: String, seconds: Int) =
        "M|${clean(line1)}|${clean(line2, 28)}|$seconds"

    /** Lampeggio a tutto schermo nel colore del giocatore (0xRRGGBB), con un bip a ogni lampo. */
    fun identify(line1: String, line2: String, seconds: Int, rgb: Int) =
        "I|${clean(line1)}|${clean(line2, 28)}|$seconds|${"%06X".format(rgb and 0xFFFFFF)}"

    fun powerOff(line1: String, line2: String) = "O|${clean(line1)}|${clean(line2, 28)}"

    /** L'app conferma gli eventi su questa connessione. Va scritto prima di attivare le notifiche di EVENT. */
    const val HELLO = "H|1"

    /** Conferma dell'evento [seq]: il braccialetto suona il bip di conferma. */
    fun ack(seq: Int) = "K|$seq"

    /** Evento da EVENT; null se troppo corto. Gli eventi dei firmware prima della 2.3 hanno [boot] null. */
    fun parseEvent(b: ByteArray): RawBandEvent? {
        if (b.size < 2) return null
        fun u(i: Int) = b[i].toInt() and 0xFF
        val boot = if (b.size >= 7) u(3).toLong() or (u(4).toLong() shl 8) or (u(5).toLong() shl 16) or (u(6).toLong() shl 24) else null
        return RawBandEvent(
            type = u(0),
            seq = u(1),
            extra = if (b.size >= 3) u(2) else null,
            boot = boot,
            ageMs = if (b.size >= 8) u(7) * 100 else 0,
        )
    }
}

/**
 * Evento così come arriva dal braccialetto. [boot]: id casuale di quell'accensione (firmware 2.3), null prima;
 * [ageMs]: da quanto è stato premuto il tasto (più di zero se rimandato dopo una riconnessione).
 */
data class RawBandEvent(val type: Int, val seq: Int, val extra: Int?, val boot: Long?, val ageMs: Int)

/**
 * Eventi già ricevuti, per braccialetto e accensione. Un evento rimandato dopo una riconnessione (la conferma
 * era andata persa) si conferma di nuovo ma non si applica una seconda volta. Il braccialetto rimanda solo
 * eventi di meno di 8 s: la finestra di 30 s basta e la sequenza (8 bit) non fa in tempo a ripetersi.
 */
class EventDedupe(private val windowMs: Long = 30_000) {
    private val seen = mutableMapOf<String, MutableMap<Int, Long>>()

    /** true la prima volta che arriva quell'evento ([now] in ms, orologio monotono). */
    fun firstTime(address: String, boot: Long, seq: Int, now: Long): Boolean {
        seen.values.forEach { m -> m.values.removeAll { now - it > windowMs } }
        seen.values.removeAll { it.isEmpty() }
        val m = seen.getOrPut("$address/$boot") { mutableMapOf() }
        if (seq in m) return false
        m[seq] = now
        return true
    }
}

/**
 * Impostazioni salvate nel braccialetto. Testo sulla caratteristica CONFIG:
 * "fw=2.0;name=TSM-1A2B;bri=20;pt=3;vol=50;flip=0;pair=30;lost=180;idle=30", dal firmware 2.2 anche ";lang=it".
 * In scrittura bastano le chiavi da cambiare; il braccialetto risponde con tutte.
 * La lingua non passa da [encode]: la manda l'app da sola ([languageConfig]) e il braccialetto la salva senza anteprima.
 */
data class BandSettings(
    val name: String = "",
    /** Luminosità del display, 5-100 %. */
    val brightness: Int = 20,
    /** Secondi di punteggio acceso dopo ogni punto (0 = non mostrarlo; il riepilogo di fine game dura 2 s in più). */
    val pointSeconds: Int = 3,
    /** Volume del cicalino, 0-100 % (0 = muto). */
    val volume: Int = 50,
    /** Display capovolto, per portare il braccialetto sull'altro polso. */
    val flip: Boolean = false,
    /** Spegnimento se all'accensione nessun telefono si collega (s). */
    val pairTimeoutS: Int = 30,
    /** Spegnimento se il telefono si scollega (s). */
    val lostTimeoutS: Int = 180,
    /** Spegnimento se collegato ma inattivo (min). */
    val idleTimeoutMin: Int = 30,
    val firmware: String = "",
    /** Lingua dei testi del braccialetto ("it", "en", ...); vuota = firmware senza lingue (prima della 2.2). */
    val lang: String = "",
) {
    fun clamped() = copy(
        name = cleanName(name),
        brightness = brightness.coerceIn(5, 100),
        pointSeconds = pointSeconds.coerceIn(0, 10),
        volume = volume.coerceIn(0, 100),
        pairTimeoutS = pairTimeoutS.coerceIn(15, 600),
        lostTimeoutS = lostTimeoutS.coerceIn(30, 1800),
        idleTimeoutMin = idleTimeoutMin.coerceIn(5, 120),
    )

    /** Testo da scrivere sulla caratteristica CONFIG (il nome solo se c'è). */
    fun encode(): String {
        val c = clamped()
        return buildList {
            if (c.name.isNotEmpty()) add("name=${c.name}")
            add("bri=${c.brightness}")
            add("pt=${c.pointSeconds}")
            add("vol=${c.volume}")
            add("flip=${if (c.flip) 1 else 0}")
            add("pair=${c.pairTimeoutS}")
            add("lost=${c.lostTimeoutS}")
            add("idle=${c.idleTimeoutMin}")
        }.joinToString(";")
    }

    companion object {
        const val NAME_MAX = 12

        /** Solo la lingua: il braccialetto la salva in silenzio (niente "IMPOSTAZIONI OK"). */
        fun languageConfig(code: String) = "lang=$code"

        /** Nome valido per il braccialetto: ASCII stampabile, senza i separatori del protocollo, max 12. */
        fun cleanName(s: String): String {
            val plain = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
            return plain.filter { it.code in 32..126 && it !in "|;=" }.trim().take(NAME_MAX).trim()
        }

        fun parse(text: String): BandSettings? {
            val map = text.split(';').mapNotNull { part ->
                val kv = part.split('=', limit = 2)
                if (kv.size == 2) kv[0].trim() to kv[1].trim() else null
            }.toMap()
            if ("bri" !in map) return null
            val d = BandSettings()
            return BandSettings(
                name = map["name"] ?: "",
                brightness = map["bri"]?.toIntOrNull() ?: d.brightness,
                pointSeconds = map["pt"]?.toIntOrNull() ?: d.pointSeconds,
                volume = map["vol"]?.toIntOrNull() ?: d.volume,
                flip = map["flip"] == "1",
                pairTimeoutS = map["pair"]?.toIntOrNull() ?: d.pairTimeoutS,
                lostTimeoutS = map["lost"]?.toIntOrNull() ?: d.lostTimeoutS,
                idleTimeoutMin = map["idle"]?.toIntOrNull() ?: d.idleTimeoutMin,
                firmware = map["fw"] ?: "",
                lang = map["lang"] ?: "",
            )
        }
    }
}
TSM_EOF

# ---------------------------------------------------------------- app/src/main/java/com/tennis/scoremanager/ble/BatteryModel.kt
cat > "$DEST/app/src/main/java/com/tennis/scoremanager/ble/BatteryModel.kt" << 'TSM_EOF'
package com.tennis.scoremanager.ble

/**
 * Stato inviato dal braccialetto ogni minuto: "mv=3987;chg=0;up=1234;dsp=56", dal firmware 2.1 anche
 * ";usb=5010;full=0;pct=71". [charging] = alimentato dal cavo USB (dal 2.1 anche a carica completa).
 */
data class BandStatus(
    val millivolts: Int,
    val charging: Boolean,
    val uptimeS: Long,
    val displayS: Long,
    /** Tensione USB in mV (0 = senza cavo); null = firmware prima della 2.1. */
    val usbMv: Int? = null,
    /** Carica completa, col cavo ancora collegato. */
    val full: Boolean = false,
    /** Percentuale mostrata dal braccialetto: in carica è quella della carica (la tensione lì è falsata). */
    val percent: Int? = null,
)

/** Consumo medio stimato (mA) diviso per voce; [measured] = la base viene da una misura sul campo. */
data class PowerEstimate(val baseMa: Double, val displayMa: Double, val soundMa: Double, val measured: Boolean) {
    val totalMa: Double get() = baseMa + displayMa + soundMa
    /** Ore di autonomia da carica piena. */
    val hoursFull: Double get() = BatteryModel.CAPACITY_MAH / totalMa
    /** Ore di autonomia con la carica indicata. */
    fun hoursAt(percent: Int): Double = hoursFull * percent.coerceIn(0, 100) / 100.0
}

/**
 * Batteria LiPo del braccialetto (250 mAh): la carica si ricava dalla tensione con la curva di scarica
 * tipica (a basso carico), molto più fedele della retta 3,30-4,15 V usata da M5Unified.
 * L'autonomia si stima dal consumo reale misurato durante l'uso.
 */
object BatteryModel {

    const val CAPACITY_MAH = 250.0

    /**
     * ESP32-S3 a 80 MHz con il Bluetooth collegato e il display spento. Il core Arduino è compilato senza
     * gestione del risparmio energetico (niente light sleep col Bluetooth acceso), quindi questa è la voce
     * che pesa di più. Valore stimato: appena c'è una misura sul campo si usa quella ([baseFromMeasure]).
     */
    const val BASE_MA = 35.0

    // Uso tipico in partita per ogni braccialetto: ~60 punti e ~10 game all'ora, ~1 minuto di messaggi,
    // ~40 bip (i tasti premuti da chi lo indossa più gli avvisi).
    const val POINTS_PER_HOUR = 60
    const val GAMES_PER_HOUR = 10
    const val MESSAGE_S_PER_HOUR = 60
    const val BEEPS_PER_HOUR = 40

    private val curve = listOf(
        4200 to 100, 4150 to 95, 4110 to 90, 4080 to 85, 4020 to 80, 3980 to 75, 3950 to 70,
        3910 to 65, 3870 to 60, 3850 to 55, 3840 to 50, 3820 to 45, 3800 to 40, 3790 to 35,
        3770 to 30, 3750 to 25, 3730 to 20, 3710 to 15, 3690 to 10, 3610 to 5, 3270 to 0,
    )

    /** Tensioni plausibili per la LiPo: fuori da qui la lettura del braccialetto è fallita (i firmware 2.2 mandano "mv=0"). */
    val PLAUSIBLE_MV = 2500..5000

    /**
     * null se il testo non è uno stato valido. Uno stato con una tensione impossibile si scarta tutto: con "mv=0"
     * la batteria risultava allo 0 % (falso allarme che zittiva quelli veri) e la stima dei consumi si sballava.
     */
    fun parse(text: String): BandStatus? {
        val map = text.split(';').mapNotNull { part ->
            val kv = part.split('=', limit = 2)
            if (kv.size == 2) kv[0].trim() to kv[1].trim() else null
        }.toMap()
        val mv = map["mv"]?.toIntOrNull()?.takeIf { it in PLAUSIBLE_MV } ?: return null
        return BandStatus(
            millivolts = mv,
            charging = map["chg"] == "1",
            uptimeS = map["up"]?.toLongOrNull()?.takeIf { it >= 0 } ?: 0,
            displayS = map["dsp"]?.toLongOrNull()?.takeIf { it >= 0 } ?: 0,
            usbMv = map["usb"]?.toIntOrNull(),
            full = map["full"] == "1",
            percent = map["pct"]?.toIntOrNull()?.takeIf { it in 0..100 },
        )
    }

    /** Percentuale da mostrare: in carica quella del braccialetto (firmware 2.1), altrimenti dalla tensione. */
    fun shownPercent(st: BandStatus): Int = when {
        st.full -> 100
        st.charging && st.percent != null -> st.percent
        else -> soc(st.millivolts)
    }

    /** Percentuale di carica (0-100) dalla tensione in mV. */
    fun soc(mv: Int): Int {
        if (mv >= curve.first().first) return 100
        if (mv <= curve.last().first) return 0
        for (i in 0 until curve.lastIndex) {
            val (vHi, pHi) = curve[i]
            val (vLo, pLo) = curve[i + 1]
            if (mv in vLo..vHi) return pLo + (mv - vLo) * (pHi - pLo) / (vHi - vLo)
        }
        return 0
    }

    /**
     * Calo della carica in % all'ora (positivo), dalla retta dei minimi quadrati sui campioni (tempo in ms, carica %).
     * Servono almeno 20 minuti di dati e un calo misurabile, altrimenti null.
     */
    fun drainPerHour(samples: List<Pair<Long, Int>>): Double? {
        if (samples.size < 3) return null
        val t0 = samples.first().first
        val span = samples.last().first - t0
        if (span < 20 * 60_000L) return null
        val xs = samples.map { (it.first - t0) / 3_600_000.0 }
        val ys = samples.map { it.second.toDouble() }
        val mx = xs.average()
        val my = ys.average()
        val den = xs.sumOf { (it - mx) * (it - mx) }
        if (den <= 0.0) return null
        val slope = xs.indices.sumOf { (xs[it] - mx) * (ys[it] - my) } / den // % all'ora (negativo)
        if (slope >= -0.5) return null // calo troppo piccolo per stimare
        return -slope
    }

    /** Ore di autonomia rimaste secondo il calo misurato (null finché non si può stimare). */
    fun hoursLeft(samples: List<Pair<Long, Int>>): Double? {
        val drain = drainPerHour(samples) ?: return null
        val xs = samples.map { (it.first - samples.first().first) / 3_600_000.0 }
        val my = samples.map { it.second.toDouble() }.average()
        val now = my - drain * (xs.last() - xs.average())
        return (now / drain).coerceAtLeast(0.0)
    }

    /** Display: controller più retroilluminazione, in mA mentre è acceso. */
    fun displayOnMa(brightness: Int): Double = 3.0 + 0.25 * brightness.coerceIn(0, 100)

    /** Frazione di tempo col display acceso in partita (punteggio dopo ogni punto, riepilogo a fine game, messaggi). */
    fun displayDuty(pointSeconds: Int): Double {
        val shown = if (pointSeconds <= 0) 0 else POINTS_PER_HOUR * pointSeconds + GAMES_PER_HOUR * (pointSeconds + 2)
        return (shown + MESSAGE_S_PER_HOUR) / 3600.0
    }

    /** Cicalino: codec e amplificatore restano accesi ~1,6 s per bip. */
    fun soundMa(volume: Int): Double =
        if (volume <= 0) 0.0 else BEEPS_PER_HOUR * 1.6 / 3600.0 * (15.0 + 0.6 * volume.coerceIn(0, 100))

    /** Consumo medio con le impostazioni scelte; [measuredBaseMa] sostituisce la base stimata se c'è. */
    fun estimate(s: BandSettings, measuredBaseMa: Double? = null): PowerEstimate = PowerEstimate(
        baseMa = measuredBaseMa ?: BASE_MA,
        displayMa = displayOnMa(s.brightness) * displayDuty(s.pointSeconds),
        soundMa = soundMa(s.volume),
        measured = measuredBaseMa != null,
    )

    /**
     * Consumo di base ricavato da una misura: calo % all'ora × capacità, meno il display
     * (acceso per [displayDuty] del tempo, misurato dal braccialetto) e il cicalino.
     */
    fun baseFromMeasure(drainPctPerHour: Double, displayDuty: Double, s: BandSettings): Double {
        val total = drainPctPerHour * CAPACITY_MAH / 100.0
        return (total - displayOnMa(s.brightness) * displayDuty - soundMa(s.volume)).coerceIn(10.0, 150.0)
    }

    /** "~7 h 10 min" oppure "~40 min". */
    fun formatHours(h: Double): String {
        val min = Math.round(h * 60).toInt()
        return when {
            min < 60 -> "~$min min"
            min % 60 == 0 || min >= 600 -> "~${Math.round(h)} h"
            else -> "~${min / 60} h ${min % 60} min"
        }
    }
}
TSM_EOF

# ---------------------------------------------------------------- app/src/main/java/com/tennis/scoremanager/ble/BleManager.kt
cat > "$DEST/app/src/main/java/com/tennis/scoremanager/ble/BleManager.kt" << 'TSM_EOF'
package com.tennis.scoremanager.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.ParcelUuid
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.tennis.scoremanager.data.LocationHelper
import com.tennis.scoremanager.model.Side
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

enum class LinkState { IDLE, CONNECTING, READY, POWERED_OFF }

data class FoundBand(val address: String, val name: String, val rssi: Int, val lastSeen: Long = 0L)

data class BandInfo(
    val address: String,
    val name: String,
    val state: LinkState = LinkState.IDLE,
    /** Percentuale: dalla tensione se il firmware la manda, altrimenti quella del braccialetto. */
    val battery: Int? = null,
    val millivolts: Int? = null,
    val charging: Boolean = false,
    /** Carica completa col cavo ancora collegato (firmware 2.1). */
    val chargeFull: Boolean = false,
    /** Impostazioni lette dal braccialetto; null = firmware senza impostazioni (prima della 2.0) o non ancora lette. */
    val settings: BandSettings? = null,
)

/** [reason] solo per EVT_POWER_OFF dai firmware 2.0: vedi BandProtocol.OFF_*. */
data class BandEvent(val side: Side, val type: Int, val reason: Int? = null)

/**
 * Gestisce i due braccialetti. Ogni lato (Giocatore 1 / Giocatore 2) ha al massimo un braccialetto:
 * il legame lato <-> indirizzo è l'unica fonte di verità, così i punti non finiscono mai al giocatore sbagliato.
 */
@SuppressLint("MissingPermission")
class BleManager(context: Context) {

    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val adapter: BluetoothAdapter? = app.getSystemService(BluetoothManager::class.java)?.adapter

    private val _found = MutableStateFlow<List<FoundBand>>(emptyList())
    val found: StateFlow<List<FoundBand>> = _found
    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning
    private val _bands = MutableStateFlow<Map<Side, BandInfo>>(emptyMap())
    val bands: StateFlow<Map<Side, BandInfo>> = _bands
    private val _events = MutableSharedFlow<BandEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<BandEvent> = _events
    private val _ready = MutableSharedFlow<Side>(extraBufferCapacity = 4)
    /** Emesso quando un braccialetto è connesso e pronto a ricevere. */
    val ready: SharedFlow<Side> = _ready
    private val _status = MutableSharedFlow<Pair<Side, BandStatus>>(extraBufferCapacity = 8)
    /** Stato batteria ricevuto dai braccialetti (per stima autonomia e registro consumi). */
    val status: SharedFlow<Pair<Side, BandStatus>> = _status
    private val _adapterOn = MutableStateFlow(adapter?.isEnabled == true)
    val adapterOn: StateFlow<Boolean> = _adapterOn
    private val _locationOn = MutableStateFlow(LocationHelper.isEnabled(app))
    /** Posizione del telefono attiva: senza, Android non restituisce i braccialetti trovati. Si aggiorna da sola. */
    val locationOn: StateFlow<Boolean> = _locationOn

    private val links = mutableMapOf<Side, BandLink>()
    private var scanJob: Job? = null
    /** Ricerca automatica voluta (pagina dei braccialetti aperta): riparte da sola se Bluetooth o posizione tornano. */
    private var autoScan = false
    /** La ricerca è davvero avviata nel sistema (startScan riuscito e non fallito dopo). */
    private var scanRunning = false
    private var scanStartJob: Job? = null
    private var scanStopJob: Job? = null
    private val scanThrottle = ScanThrottle()
    /** Eventi già ricevuti (per braccialetto e accensione): vale anche tra una connessione e l'altra. */
    private val dedupe = EventDedupe()

    init {
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        ContextCompat.registerReceiver(app, object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                val st = i?.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                _adapterOn.value = st == BluetoothAdapter.STATE_ON
                if (st == BluetoothAdapter.STATE_ON) {
                    links.values.forEach { it.connect() }
                    if (autoScan) ensureScan()
                }
                if (st == BluetoothAdapter.STATE_OFF) {
                    stopScanNow()
                    links.values.forEach { it.onAdapterOff() }
                }
            }
        }, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        // La posizione si può spegnere dalla tendina senza che l'app vada in pausa: la si segue in tempo reale.
        val locFilter = IntentFilter().apply {
            addAction(LocationManager.MODE_CHANGED_ACTION)
            addAction(LocationManager.PROVIDERS_CHANGED_ACTION)
        }
        ContextCompat.registerReceiver(app, object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) = refreshLocation()
        }, locFilter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    /** Ricontrolla la posizione (anche a ogni ritorno nell'app). Quando torna attiva la ricerca riparte. */
    fun refreshLocation() {
        val on = LocationHelper.isEnabled(app)
        val was = _locationOn.value
        _locationOn.value = on
        // Fino ad Android 11 senza posizione la ricerca non restituisce niente: quando torna la si riavvia.
        if (on && !was && autoScan) restartScan()
    }

    val isSupported: Boolean get() = adapter != null && app.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
    val isEnabled: Boolean get() = adapter?.isEnabled == true

    private fun granted(p: String) = ContextCompat.checkSelfPermission(app, p) == PackageManager.PERMISSION_GRANTED

    /** Per collegarsi a un braccialetto già associato: da Android 12 basta BLUETOOTH_CONNECT (la posizione no). */
    fun canConnect(): Boolean = Build.VERSION.SDK_INT < 31 || granted(Manifest.permission.BLUETOOTH_CONNECT)

    /**
     * Per la ricerca: da Android 12 BLUETOOTH_SCAN, dichiarato "neverForLocation" nel manifest, quindi la posizione
     * non serve (va bene anche quella approssimativa); prima di Android 12 serve la posizione precisa.
     */
    fun canScan(): Boolean =
        if (Build.VERSION.SDK_INT >= 31) granted(Manifest.permission.BLUETOOTH_SCAN) else granted(Manifest.permission.ACCESS_FINE_LOCATION)

    /** Tutto quello che serve ai braccialetti (ricerca e collegamento). La richiesta ([requiredPermissions]) chiede anche la posizione. */
    fun hasPermissions(): Boolean = canScan() && canConnect()

    /**
     * Ricerca automatica e continua dei braccialetti finché [on] (la pagina dei braccialetti è aperta).
     * I braccialetti che non si sentono più da 10 s spariscono dall'elenco (spenti, o già collegati: da
     * collegati non trasmettono più).
     */
    fun setAutoScan(on: Boolean) {
        autoScan = on
        if (on) {
            scanStopJob?.cancel()
            ensureScan()
        } else {
            // Si ferma con un attimo di ritardo: una pausa breve (dialogo dei permessi, Bluetooth da attivare)
            // non deve costare un nuovo avvio. Android ne permette 5 in 30 s, poi la ricerca non parte e basta.
            scanStopJob?.cancel()
            scanStopJob = scope.launch {
                delay(SCAN_STOP_GRACE_MS)
                stopScanNow()
            }
        }
    }

    /** Avvia la ricerca se è voluta e non è già avviata (o in partenza), rispettando il limite di avvii di Android. */
    private fun ensureScan(retryInMs: Long = 0) {
        if (!autoScan || scanRunning || scanStartJob?.isActive == true) return
        if (!canScan() || !isEnabled || adapter?.bluetoothLeScanner == null) {
            _scanning.value = false
            return
        }
        _scanning.value = true  // in partenza: per chi guarda è già "ricerca in corso"
        scanStartJob = scope.launch {
            delay(maxOf(retryInMs, scanThrottle.delayBeforeStart(SystemClock.elapsedRealtime())))
            startScanNow()
        }
    }

    private fun restartScan() {
        if (scanRunning) {
            scanJob?.cancel()
            runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
            scanRunning = false
        }
        ensureScan()
    }

    private fun startScanNow() {
        val scanner = adapter?.bluetoothLeScanner
        if (!autoScan || scanner == null || !canScan() || !isEnabled) {
            _scanning.value = false
            return
        }
        val ok = runCatching { scanner.startScan(scanFilters, scanSettings, scanCallback) }.isSuccess
        scanThrottle.recordStart(SystemClock.elapsedRealtime())
        if (!ok) {
            _scanning.value = false
            ensureScan(retryInMs = SCAN_RETRY_MS)
            return
        }
        scanRunning = true
        _scanning.value = true
        scanJob?.cancel()
        scanJob = scope.launch {
            var sinceRestart = 0L
            while (true) {
                delay(2_000)
                val now = SystemClock.elapsedRealtime()
                _found.update { list -> list.filter { now - it.lastSeen < 10_000 } }
                // Android declassa le ricerche che durano più di 30 minuti: si riparte ogni 10.
                sinceRestart += 2_000
                if (sinceRestart >= 10 * 60_000L) {
                    sinceRestart = 0
                    restartScan()
                    break
                }
            }
        }
    }

    private fun stopScanNow() {
        scanStopJob?.cancel()
        scanStartJob?.cancel()
        scanJob?.cancel()
        scanJob = null
        if (scanRunning) runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
        scanRunning = false
        _scanning.value = false
        _found.value = emptyList()
    }

    private val scanFilters = listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(BandProtocol.SERVICE)).build())
    private val scanSettings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val addr = result.device.address
            val name = result.scanRecord?.deviceName ?: runCatching { result.device.name }.getOrNull() ?: "TSM-Band"
            val band = FoundBand(addr, name, result.rssi, SystemClock.elapsedRealtime())
            _found.update { list -> (list.filterNot { it.address == addr } + band).sortedBy { it.name } }
            // Un braccialetto associato che trasmette è acceso e libero: lo si collega subito.
            scope.launch { links.values.firstOrNull { it.address == addr }?.onSeen() }
        }

        override fun onScanFailed(errorCode: Int) {
            scope.launch {
                Log.w(TAG, "ricerca fallita: $errorCode")
                if (errorCode == SCAN_FAILED_ALREADY_STARTED) return@launch  // è già avviata: i risultati arrivano
                scanJob?.cancel()
                scanRunning = false
                _scanning.value = false
                // Troppi avvii (Android 13+ lo dice, prima tace) o errore del sistema: si riprova più tardi.
                ensureScan(retryInMs = if (errorCode == SCAN_FAILED_TOO_FREQUENTLY) 30_000 else SCAN_RETRY_MS)
            }
        }
    }

    /** Associa (o toglie, con address null) il braccialetto a un giocatore. Un braccialetto non può stare su due lati. */
    fun assign(side: Side, address: String?, name: String?) {
        links.remove(side)?.close()
        if (address != null) {
            links.entries.firstOrNull { it.value.address == address }?.let { other ->
                links.remove(other.key)?.close()
            }
            val link = BandLink(side, address, name ?: address)
            links[side] = link
            link.connect()
        }
        publish()
    }

    /** Scambia i due braccialetti tra Giocatore 1 e Giocatore 2 senza scollegarli. */
    fun swapSides() {
        val a = links.remove(Side.P1)
        val b = links.remove(Side.P2)
        a?.let { it.side = Side.P2; links[Side.P2] = it }
        b?.let { it.side = Side.P1; links[Side.P1] = it }
        publish()
    }

    /**
     * Riprova a collegare i braccialetti associati (es. dopo aver concesso i permessi, o tornando nell'app):
     * un braccialetto che non rispondeva si riprova subito; uno spento resta in attesa in background.
     */
    fun reconnectAll() = links.values.forEach { it.retryNow() }

    /** Punteggi e messaggi: se ne arrivano altri prima dell'invio vale l'ultimo. */
    fun send(side: Side, payload: String) {
        links[side]?.send(payload)
    }

    /** Comando che non deve andare perso (Identifica, spegnimento): scritto subito, true se è arrivato. */
    suspend fun command(side: Side, payload: String): Boolean = links[side]?.command(payload) ?: false

    /** Scrive le impostazioni nel braccialetto; true se le ha ricevute (poi risponde con quelle applicate). */
    suspend fun writeSettings(side: Side, settings: BandSettings): Boolean = links[side]?.writeConfig(settings.encode()) ?: false

    suspend fun writeLanguage(side: Side, code: String): Boolean = links[side]?.writeConfig(BandSettings.languageConfig(code)) ?: false

    fun isReady(side: Side): Boolean = links[side]?.state == LinkState.READY

    fun disconnectAll() {
        setAutoScan(false)
        links.values.forEach { it.close() }
        links.clear()
        publish()
    }

    private fun publish() {
        _bands.value = links.mapValues { (_, l) ->
            BandInfo(
                address = l.address,
                name = l.settings?.name?.takeIf { it.isNotEmpty() } ?: l.name,
                state = l.state,
                battery = l.status?.let { BatteryModel.shownPercent(it) } ?: l.battery,
                millivolts = l.status?.millivolts,
                charging = l.status?.charging == true,
                chargeFull = l.status?.full == true,
                settings = l.settings,
            )
        }
    }

    private enum class OpKind { MTU, DESCRIPTOR, WRITE, READ }

    private inner class BandLink(var side: Side, val address: String, val name: String) {
        var state = LinkState.IDLE
            private set
        var battery: Int? = null
        var status: BandStatus? = null
        var settings: BandSettings? = null
        private var gatt: BluetoothGatt? = null
        private var closed = false
        private var retryJob: Job? = null
        private var setupWatchdog: Job? = null
        private val opLock = Mutex()
        @Volatile private var pendingOp: CompletableDeferred<Int>? = null
        @Volatile private var pendingKind: OpKind? = null
        private val wake = Channel<Unit>(Channel.CONFLATED)
        private var latest: String? = null
        private var lastSeq = -1
        /** Tentativo in corso in background (connectGatt con autoConnect): aspetta il braccialetto senza scadenza. */
        private var background = false
        /** Tentativi di fila finiti senza arrivare a READY. */
        private var failures = 0
        @Volatile private var mtu = DEFAULT_MTU
        /** Fino a quando non si scrive sul display: "PAIRING OK" resta a schermo i suoi 3 secondi. */
        private var quietUntil = 0L
        private val acks = Channel<Int>(Channel.UNLIMITED)
        private val sender: Job = scope.launch {
            for (tick in wake) {
                if (state != LinkState.READY) continue
                val wait = quietUntil - SystemClock.elapsedRealtime()
                if (wait > 0) delay(wait)
                if (state != LinkState.READY) continue
                val msg = latest ?: continue
                latest = null
                if (!write(msg) && latest == null) latest = msg
            }
        }
        /** Conferme dei tasti: partono subito, anche durante la configurazione (il braccialetto rimanda gli eventi appena ascoltiamo). */
        private val acker: Job = scope.launch {
            // Persa? Il braccialetto rimanda l'evento alla prossima connessione e lo si conferma di nuovo.
            for (seq in acks) if (gatt != null) write(BandProtocol.ack(seq))
        }

        private fun changeState(s: LinkState) {
            state = s
            publish()
        }

        /**
         * Tentativo diretto (veloce, ma Android lo chiude dopo ~30 s) finché il braccialetto risponde; dopo uno
         * spegnimento o [DIRECT_TRIES] tentativi a vuoto si aspetta in background: Android collega da solo il
         * braccialetto quando torna a trasmettere, senza riprovare ogni pochi secondi per sempre.
         */
        fun connect(direct: Boolean = false) {
            if (closed || !canConnect() || !isEnabled) return
            if (gatt != null) {
                // Un tentativo diretto prende il posto solo di un'attesa in background.
                if (!direct || !background || state == LinkState.READY) return
                runCatching { gatt?.close() }
                gatt = null
            }
            val dev = runCatching { adapter?.getRemoteDevice(address) }.getOrNull() ?: return
            retryJob?.cancel()
            val bg = !direct && (state == LinkState.POWERED_OFF || failures >= DIRECT_TRIES)
            background = bg
            if (state != LinkState.POWERED_OFF) changeState(if (bg) LinkState.IDLE else LinkState.CONNECTING)
            gatt = runCatching { dev.connectGatt(app, bg, callback, BluetoothDevice.TRANSPORT_LE) }.getOrNull()
            if (gatt == null) scheduleReconnect(wasBackground = bg, afterLoss = false)
        }

        /** Dall'app (ritorno in primo piano, permessi): chi non rispondeva si riprova subito, chi è spento no. */
        fun retryNow() {
            if (state == LinkState.POWERED_OFF) {
                connect()
            } else {
                failures = 0
                connect(direct = true)
            }
        }

        /** La ricerca lo vede trasmettere: è acceso e libero, il tentativo diretto è più rapido dell'attesa in background. */
        fun onSeen() {
            if (!background || state == LinkState.READY) return
            failures = 0
            connect(direct = true)
        }

        fun onAdapterOff() {
            pendingOp?.complete(-1)
            runCatching { gatt?.close() }
            gatt = null
            changeState(LinkState.IDLE)
        }

        fun send(payload: String) {
            latest = payload
            wake.trySend(Unit)
        }

        fun close() {
            closed = true
            retryJob?.cancel()
            sender.cancel()
            acker.cancel()
            pendingOp?.complete(-1)
            runCatching { gatt?.disconnect() }
            runCatching { gatt?.close() }
            gatt = null
        }

        private fun scheduleReconnect(wasBackground: Boolean, afterLoss: Boolean) {
            if (closed) return
            retryJob?.cancel()
            retryJob = scope.launch {
                delay(
                    when {
                        wasBackground -> 30_000   // l'attesa in background è finita male da sola: niente raffiche
                        afterLoss -> 500          // collegamento appena perso: il braccialetto trasmette già di nuovo
                        else -> 2_500
                    },
                )
                connect()
            }
        }

        private fun completeOp(kind: OpKind, status: Int) {
            if (pendingKind == kind) pendingOp?.complete(status)
        }

        private val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                scope.launch {
                    if (g !== gatt) return@launch
                    if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                        lastSeq = -1  // firmware vecchi: il braccialetto può essere stato riacceso, la sequenza riparte
                        background = false
                        mtu = DEFAULT_MTU
                        quietUntil = SystemClock.elapsedRealtime() + PAIRED_MSG_MS
                        setupWatchdog?.cancel()
                        setupWatchdog = scope.launch {
                            // Se la configurazione non finisce (callback persa) si ricomincia da capo.
                            delay(15_000)
                            if (gatt === g && state != LinkState.READY) runCatching { g.disconnect() }
                        }
                        // Pausa prima della scoperta dei servizi (le librerie BLE di riferimento ne fanno una simile):
                        // su alcuni telefoni la scoperta fallisce se parte mentre il braccialetto chiede i suoi
                        // parametri di connessione. Dal firmware 2.3 un tasto premuto intanto resta in coda.
                        delay(400)
                        runCatching { g.discoverServices() }
                    } else {
                        setupWatchdog?.cancel()
                        pendingOp?.complete(-1)
                        runCatching { g.close() }
                        gatt = null
                        val wasReady = state == LinkState.READY
                        if (wasReady) failures = 0 else failures++
                        if (state != LinkState.POWERED_OFF) changeState(LinkState.IDLE)
                        scheduleReconnect(wasBackground = background, afterLoss = wasReady)
                    }
                }
            }

            override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                scope.launch { if (g === gatt) setup(g) }
            }

            override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) this@BandLink.mtu = mtu
                completeOp(OpKind.MTU, status)
            }

            override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
                completeOp(OpKind.DESCRIPTOR, status)
            }

            override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
                completeOp(OpKind.WRITE, status)
            }

            override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) handle(c.uuid, value, fromRead = true)
                completeOp(OpKind.READ, status)
            }

            @Suppress("OVERRIDE_DEPRECATION")
            override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
                if (Build.VERSION.SDK_INT >= 33) return
                @Suppress("DEPRECATION")
                if (status == BluetoothGatt.GATT_SUCCESS) handle(c.uuid, c.value ?: byteArrayOf(), fromRead = true)
                completeOp(OpKind.READ, status)
            }

            override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
                handle(c.uuid, value, fromRead = false)
            }

            @Suppress("OVERRIDE_DEPRECATION")
            override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
                if (Build.VERSION.SDK_INT >= 33) return
                @Suppress("DEPRECATION")
                handle(c.uuid, c.value ?: byteArrayOf(), fromRead = false)
            }
        }

        private fun handle(uuid: UUID, value: ByteArray, fromRead: Boolean) {
            val copy = value.copyOf()
            scope.launch {
                // Con l'MTU minimo (23) le notifiche si fermano a 20 byte: impostazioni e stato arriverebbero
                // tronchi. Una notifica piena si rilegge: la lettura prende il valore intero, a pezzi.
                if (!fromRead && (uuid == BandProtocol.CONFIG || uuid == BandProtocol.STATUS) && copy.size >= mtu - 3) {
                    readFull(uuid)
                    return@launch
                }
                when (uuid) {
                    BandProtocol.EVENT -> onEvent(copy)
                    BandProtocol.CONFIG -> BandSettings.parse(String(copy, Charsets.US_ASCII))?.let {
                        settings = it
                        publish()
                    }
                    BandProtocol.BATTERY_LEVEL -> if (copy.isNotEmpty()) {
                        battery = (copy[0].toInt() and 0xFF).coerceIn(0, 100)
                        publish()
                    }
                    BandProtocol.STATUS -> BatteryModel.parse(String(copy, Charsets.US_ASCII))?.let {
                        status = it
                        publish()
                        _status.tryEmit(side to it)
                    }
                }
            }
        }

        private fun onEvent(bytes: ByteArray) {
            val ev = BandProtocol.parseEvent(bytes) ?: return
            if (ev.boot != null) {
                // Firmware 2.3: si conferma sempre (anche un doppione: la conferma di prima può essere andata
                // persa), ma si applica una volta sola.
                acks.trySend(ev.seq)
                if (!dedupe.firstTime(address, ev.boot, ev.seq, SystemClock.elapsedRealtime())) {
                    Log.i(TAG, "${side.name}: evento ${ev.seq} già ricevuto, solo confermato")
                    return
                }
                if (ev.ageMs > 0) Log.i(TAG, "${side.name}: evento ${ev.seq} rimandato dopo ${ev.ageMs} ms")
            } else {
                if (ev.seq == lastSeq) return // stessa pressione ricevuta due volte
                lastSeq = ev.seq
            }
            if (ev.type == BandProtocol.EVT_POWER_OFF) changeState(LinkState.POWERED_OFF)
            val reason = if (ev.type == BandProtocol.EVT_POWER_OFF) ev.extra else null
            _events.tryEmit(BandEvent(side, ev.type, reason))
        }

        private suspend fun readFull(uuid: UUID) {
            val g = gatt ?: return
            val c = g.getService(BandProtocol.SERVICE)?.getCharacteristic(uuid) ?: return
            op(OpKind.READ) { g.readCharacteristic(c) }
        }

        private suspend fun setup(g: BluetoothGatt) {
            val svc = g.getService(BandProtocol.SERVICE)
            val evt = svc?.getCharacteristic(BandProtocol.EVENT)
            if (svc == null || evt == null) {
                runCatching { g.disconnect() }
                return
            }
            // Prima di tutto i tasti: finché le notifiche di EVENT non sono attive il braccialetto non può
            // mandarli (i firmware prima della 2.3 li perdono). "H|1" va prima: con le notifiche il firmware 2.3
            // rimanda subito i tasti in coda e deve già sapere che li confermiamo. I firmware vecchi lo ignorano.
            write(BandProtocol.HELLO)
            // Senza notifiche i tasti del braccialetto andrebbero persi: meglio riconnettersi.
            if (!enableNotify(g, evt)) {
                runCatching { g.disconnect() }
                return
            }
            // Senza risposta resta l'MTU minimo: le notifiche lunghe si rileggono (vedi handle()).
            op(OpKind.MTU) { g.requestMtu(185) }
            g.getService(BandProtocol.BATTERY_SERVICE)?.getCharacteristic(BandProtocol.BATTERY_LEVEL)?.let {
                enableNotify(g, it)
                op(OpKind.READ) { g.readCharacteristic(it) }
            }
            svc.getCharacteristic(BandProtocol.STATUS)?.let {
                enableNotify(g, it)
                op(OpKind.READ) { g.readCharacteristic(it) }
            }
            svc.getCharacteristic(BandProtocol.CONFIG)?.let {
                enableNotify(g, it)
                op(OpKind.READ) { g.readCharacteristic(it) }
            }
            // Connessione a basso consumo (intervallo ~100 ms, latenza 2): la radio del braccialetto
            // si sveglia molto meno spesso; un tasto arriva comunque entro ~125 ms.
            runCatching { g.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_LOW_POWER) }
            if (g !== gatt) return
            setupWatchdog?.cancel()
            failures = 0
            changeState(LinkState.READY)
            _ready.tryEmit(side)
            wake.trySend(Unit)  // un messaggio rimasto in sospeso parte quando finisce "PAIRING OK" (quietUntil)
        }

        /** Android esegue un'operazione GATT alla volta: le serializziamo e aspettiamo la callback. */
        private suspend fun op(kind: OpKind, start: () -> Boolean): Boolean = opLock.withLock {
            val d = CompletableDeferred<Int>()
            pendingKind = kind
            pendingOp = d
            val started = runCatching(start).getOrDefault(false)
            val result = if (started) withTimeoutOrNull(5_000) { d.await() } else null
            pendingOp = null
            pendingKind = null
            result == BluetoothGatt.GATT_SUCCESS
        }

        private suspend fun enableNotify(g: BluetoothGatt, c: BluetoothGattCharacteristic): Boolean {
            runCatching { g.setCharacteristicNotification(c, true) }
            val d = c.getDescriptor(BandProtocol.CCCD) ?: return false
            val value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            return op(OpKind.DESCRIPTOR) {
                if (Build.VERSION.SDK_INT >= 33) {
                    g.writeDescriptor(d, value) == BluetoothStatusCodes.SUCCESS
                } else {
                    @Suppress("DEPRECATION")
                    d.value = value
                    @Suppress("DEPRECATION")
                    g.writeDescriptor(d)
                }
            }
        }

        suspend fun command(msg: String): Boolean = state == LinkState.READY && write(msg)

        suspend fun writeConfig(text: String): Boolean = state == LinkState.READY && write(text, BandProtocol.CONFIG)

        private suspend fun write(msg: String, uuid: UUID = BandProtocol.DISPLAY): Boolean {
            val g = gatt ?: return false
            val c = g.getService(BandProtocol.SERVICE)?.getCharacteristic(uuid) ?: return false
            val bytes = msg.toByteArray(Charsets.US_ASCII).copyOf(minOf(msg.length, 180))
            return op(OpKind.WRITE) {
                if (Build.VERSION.SDK_INT >= 33) {
                    g.writeCharacteristic(c, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothStatusCodes.SUCCESS
                } else {
                    @Suppress("DEPRECATION")
                    c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    @Suppress("DEPRECATION")
                    c.value = bytes
                    @Suppress("DEPRECATION")
                    g.writeCharacteristic(c)
                }
            }
        }
    }

    companion object {
        private const val TAG = "BleManager"
        /** Tentativi diretti a vuoto prima di aspettare il braccialetto in background (~1,5 minuti). */
        private const val DIRECT_TRIES = 3
        private const val DEFAULT_MTU = 23
        /** Il firmware mostra "PAIRING OK" per 3 s dal collegamento: prima nessun messaggio sul display. */
        private const val PAIRED_MSG_MS = 3_200L
        private const val SCAN_STOP_GRACE_MS = 1_500L
        private const val SCAN_RETRY_MS = 5_000L
        private const val SCAN_FAILED_ALREADY_STARTED = 1  // ScanCallback.SCAN_FAILED_ALREADY_STARTED
        private const val SCAN_FAILED_TOO_FREQUENTLY = 6   // ScanCallback.SCAN_FAILED_SCANNING_TOO_FREQUENTLY (API 33)

        fun requiredPermissions(): Array<String> =
            if (Build.VERSION.SDK_INT >= 31) {
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                )
            } else {
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            }
    }
}

/**
 * Android blocca la sesta ricerca avviata in 30 s (fino ad Android 12 in silenzio: nessun errore, nessun
 * risultato). Si tiene il conto degli avvii e si aspetta, con un margine: al massimo [maxStarts] ogni [windowMs].
 */
internal class ScanThrottle(private val maxStarts: Int = 4, private val windowMs: Long = 30_000) {
    private val starts = ArrayDeque<Long>()

    /** Millisecondi da aspettare prima del prossimo avvio (0 = subito). */
    fun delayBeforeStart(now: Long): Long {
        while (starts.isNotEmpty() && now - starts.first() >= windowMs) starts.removeFirst()
        return if (starts.size < maxStarts) 0 else starts.first() + windowMs - now + 500
    }

    fun recordStart(now: Long) {
        starts.addLast(now)
        while (starts.size > maxStarts) starts.removeFirst()
    }
}
TSM_EOF

# ---------------------------------------------------------------- app/src/main/java/com/tennis/scoremanager/data/LocationHelper.kt
cat > "$DEST/app/src/main/java/com/tennis/scoremanager/data/LocationHelper.kt" << 'TSM_EOF'
package com.tennis.scoremanager.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/** Posizione del campo per il riepilogo (senza Google Play Services). */
object LocationHelper {

    fun hasPermission(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun isEnabled(ctx: Context): Boolean {
        val lm = ctx.getSystemService(LocationManager::class.java) ?: return false
        return LocationManagerCompat.isLocationEnabled(lm)
    }

    @SuppressLint("MissingPermission")
    suspend fun current(ctx: Context): Location? {
        if (!hasPermission(ctx) || !isEnabled(ctx)) return null
        val lm = ctx.getSystemService(LocationManager::class.java) ?: return null
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        val last = providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
        if (last != null && System.currentTimeMillis() - last.time < 10 * 60_000) return last
        val provider = providers.firstOrNull { it == LocationManager.NETWORK_PROVIDER } ?: providers.firstOrNull() ?: return last
        val fresh = withTimeoutOrNull(15_000) {
            suspendCancellableCoroutine { cont ->
                val signal = android.os.CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                LocationManagerCompat.getCurrentLocation(lm, provider, signal, ContextCompat.getMainExecutor(ctx)) { loc ->
                    if (cont.isActive) cont.resume(loc)
                }
            }
        }
        return fresh ?: last
    }

    /** Indirizzo leggibile; serve la rete di solito, quindi se non va restano le coordinate. */
    suspend fun address(ctx: Context, loc: Location, locale: Locale): String? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(ctx, locale)
        return withTimeoutOrNull(8_000) {
            if (Build.VERSION.SDK_INT >= 33) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocation(loc.latitude, loc.longitude, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<android.location.Address>) {
                            if (cont.isActive) cont.resume(addresses.firstOrNull()?.let { format(it) })
                        }

                        override fun onError(errorMessage: String?) {
                            if (cont.isActive) cont.resume(null)
                        }
                    })
                }
            } else {
                withContext(Dispatchers.IO) {
                    @Suppress("DEPRECATION")
                    runCatching { geocoder.getFromLocation(loc.latitude, loc.longitude, 1)?.firstOrNull()?.let { format(it) } }.getOrNull()
                }
            }
        }
    }

    private fun format(a: android.location.Address): String =
        listOfNotNull(
            listOfNotNull(a.thoroughfare, a.subThoroughfare).joinToString(" ").ifBlank { null },
            a.locality,
            a.adminArea,
        ).joinToString(", ").ifBlank { a.getAddressLine(0) ?: "" }
}
TSM_EOF

# ---------------------------------------------------------------- app/src/main/java/com/tennis/scoremanager/data/Models.kt
cat > "$DEST/app/src/main/java/com/tennis/scoremanager/data/Models.kt" << 'TSM_EOF'
package com.tennis.scoremanager.data

import com.tennis.scoremanager.model.Lang
import com.tennis.scoremanager.model.MatchEvent
import com.tennis.scoremanager.model.MatchFormat
import com.tennis.scoremanager.model.RulesConfig
import com.tennis.scoremanager.model.Side
import com.tennis.scoremanager.ui.Strings
import com.tennis.scoremanager.voice.CallNames
import kotlinx.serialization.Serializable

/** Pagina 1: dati facoltativi. p1a/p1b sono sempre del Giocatore 1, p2a/p2b del Giocatore 2. */
@Serializable
data class SetupData(
    val club: String = "",
    val court: String = "",
    val doubles: Boolean = false,
    val p1a: String = "",
    val p1b: String = "",
    val p2a: String = "",
    val p2b: String = "",
)

@Serializable
enum class PlayMode { REFEREE, BANDS }

/** Pagina 2: modalità, lingua, audio, formato e sorteggio. */
@Serializable
data class MatchOptions(
    val mode: PlayMode = PlayMode.REFEREE,
    val lang: Lang = Lang.IT,
    val audio: Boolean = true,
    val format: MatchFormat = MatchFormat.BEST_OF_THREE,
    val noAd: Boolean = false,
    val tossWinner: Side? = null,
    val firstServer: Side = Side.P1,
    val p1Left: Boolean = true,
    val firstServerP1: Int = 0,
    val firstServerP2: Int = 0,
    /** Motore di sintesi vocale (pacchetto) e voce scelti; null = automatico. */
    val ttsEngine: String? = null,
    val ttsVoice: String? = null,
    /** true = legge i file generati invece della sintesi continua. */
    val voiceFiles: Boolean = false,
    /** Spegne i braccialetti quando si conferma la fine della partita (e quando si esce dall'app). */
    val bandsOffAtEnd: Boolean = true,
)

@Serializable
data class MatchLocation(val lat: Double, val lon: Double, val address: String? = null)

/** Partita salvata: basta rigiocare gli eventi per riavere lo stato esatto. */
@Serializable
data class MatchRecord(
    val id: String,
    val setup: SetupData,
    val options: MatchOptions,
    val rules: RulesConfig,
    val events: List<MatchEvent> = emptyList(),
    /** Tempo partita accumulato (ms) fino all'ultima pausa. */
    val clockMs: Long = 0L,
    /** Ora del telefono in cui la voce ha detto "gioco". */
    val startedAt: Long? = null,
    /** Ora del telefono dell'ultimo punto che ha deciso la partita. */
    val endedAt: Long? = null,
    val suspended: Boolean = false,
    val finished: Boolean = false,
    val location: MatchLocation? = null,
    val updatedAt: Long = 0L,
    /** Carica dei braccialetti (%) all'inizio e alla fine della partita, per verificare l'autonomia. */
    val batteryStart: Map<Side, Int> = emptyMap(),
    val batteryEnd: Map<Side, Int> = emptyMap(),
)

/** Nomi mostrati e letti, sempre legati al lato giusto. */
class Names(private val setup: SetupData, private val strings: Strings) : CallNames {

    private fun n(v: String) = v.trim()

    /** Nomi dei giocatori di un lato: 1 nel singolare, 2 nel doppio. */
    fun players(side: Side): List<String> {
        val num = if (side == Side.P1) 1 else 2
        val a = n(if (side == Side.P1) setup.p1a else setup.p2a)
        val b = n(if (side == Side.P1) setup.p1b else setup.p2b)
        if (!setup.doubles) return listOf(a.ifEmpty { strings.playerDefault(num) })
        return listOf(a.ifEmpty { "${strings.playerDefault(num)}A" }, b.ifEmpty { "${strings.playerDefault(num)}B" })
    }

    /** Nome del lato: il giocatore, oppure "Rossi e Bianchi" nel doppio (se non ci sono nomi: "Giocatore 1"). */
    override fun side(side: Side): String {
        val num = if (side == Side.P1) 1 else 2
        if (!setup.doubles) return players(side).first()
        val a = n(if (side == Side.P1) setup.p1a else setup.p2a)
        val b = n(if (side == Side.P1) setup.p1b else setup.p2b)
        return when {
            a.isEmpty() && b.isEmpty() -> strings.playerDefault(num)
            a.isEmpty() || b.isEmpty() -> a.ifEmpty { b }
            else -> a + strings.teamJoiner + b
        }
    }

    /** Versione compatta per il tabellone: "Rossi / Bianchi". */
    fun short(side: Side): String = if (setup.doubles) players(side).joinToString(" / ") else side(side)

    override fun player(side: Side, index: Int): String = players(side).getOrElse(index) { side(side) }
}
TSM_EOF

# ---------------------------------------------------------------- app/src/main/java/com/tennis/scoremanager/data/Reports.kt
cat > "$DEST/app/src/main/java/com/tennis/scoremanager/data/Reports.kt" << 'TSM_EOF'
package com.tennis.scoremanager.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.tennis.scoremanager.model.Lang
import com.tennis.scoremanager.model.MatchEvent
import com.tennis.scoremanager.model.MatchFormat
import com.tennis.scoremanager.model.MatchState
import com.tennis.scoremanager.model.SetScore
import com.tennis.scoremanager.model.Side
import com.tennis.scoremanager.ui.Strings
import com.tennis.scoremanager.ui.stringsFor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Resoconto finale della partita: testo, JSON e immagine da condividere. */
object Reports {

    fun locale(lang: Lang): Locale = lang.locale

    fun duration(ms: Long): String {
        val s = ms / 1000
        return String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60)
    }

    fun time(ts: Long?, lang: Lang): String =
        ts?.let { SimpleDateFormat("HH:mm", locale(lang)).format(Date(it)) } ?: "--:--"

    fun date(ts: Long?, lang: Lang): String =
        ts?.let {
            SimpleDateFormat(stringsFor(lang).datePattern, locale(lang)).format(Date(it))
        } ?: ""

    /** "6-4" · "7-6(5)" · "[10-8]" dal punto di vista di [from]. */
    fun setText(set: SetScore, from: Side): String {
        val o = from.other
        return when {
            set.matchTiebreak -> "[${set.tb(from)}-${set.tb(o)}]"
            set.hasTiebreak -> "${set.games(from)}-${set.games(o)}(${minOf(set.tb1!!, set.tb2!!)})"
            else -> "${set.games(from)}-${set.games(o)}"
        }
    }

    fun scoreLine(state: MatchState, from: Side): String = state.sets.joinToString("  ") { setText(it, from) }

    fun pointsWon(rec: MatchRecord, side: Side): Int = rec.events.count { it is MatchEvent.Point && it.winner == side }
    fun gamesWon(state: MatchState, side: Side): Int = state.sets.sumOf { it.games(side) } + state.games(side)

    /** "G1 92% → 71% · G2 88% → 70%" */
    fun batteryLine(rec: MatchRecord, s: Strings): String =
        Side.entries.mapNotNull { side ->
            val a = rec.batteryStart[side]
            val b = rec.batteryEnd[side]
            if (a == null && b == null) null
            else "${s.playerTag(side.ordinal + 1)} ${a?.let { "$it%" } ?: "?"} → ${b?.let { "$it%" } ?: "?"}"
        }.joinToString(" · ")

    fun formatLabel(rec: MatchRecord, s: Strings): String =
        (if (rec.rules.format == MatchFormat.BEST_OF_THREE) s.formatBestOfThree else s.formatMatchTiebreak) +
            (if (rec.rules.noAd) " · ${s.noAdShort}" else "") +
            " · " + (if (rec.rules.doubles) s.doubles else s.singles)

    fun place(rec: MatchRecord, s: Strings): String {
        val loc = rec.location ?: return s.placeUnavailable
        val coords = String.format(Locale.ROOT, "%.5f, %.5f", loc.lat, loc.lon)
        return if (loc.address.isNullOrBlank()) coords else "${loc.address} ($coords)"
    }

    fun fileBaseName(rec: MatchRecord, names: Names): String {
        val d = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.ROOT).format(Date(rec.startedAt ?: rec.updatedAt))
        fun clean(x: String) = x.replace(Regex("[^\\p{L}\\p{N}]+"), "-").trim('-').take(30)
        return "${d}_${clean(names.short(Side.P1))}_vs_${clean(names.short(Side.P2))}"
    }

    fun text(rec: MatchRecord, state: MatchState, s: Strings, names: Names): String {
        val lang = rec.options.lang
        val w = state.winner ?: Side.P1
        val sb = StringBuilder()
        sb.appendLine("🎾 ${s.appName.uppercase()}")
        if (rec.setup.club.isNotBlank() || rec.setup.court.isNotBlank()) {
            sb.appendLine(listOfNotNull(
                rec.setup.club.ifBlank { null },
                rec.setup.court.ifBlank { null }?.let { "${s.court} $it" },
            ).joinToString(" · "))
        }
        sb.appendLine(date(rec.startedAt, lang).replaceFirstChar { it.uppercase() })
        sb.appendLine()
        sb.appendLine("🏆 ${s.winner}: ${names.side(w)}")
        sb.appendLine("${s.result}: ${names.short(w)} ${s.vs} ${names.short(w.other)}  ${scoreLine(state, w)}")
        sb.appendLine()
        for (side in listOf(Side.P1, Side.P2)) {
            val sets = state.sets.joinToString("  ") { set ->
                val tb = if (set.hasTiebreak && !set.matchTiebreak && set.winner != side) "(${set.tb(side)})" else ""
                "${set.shown(side)}$tb"
            }
            val mark = if (side == w) " ✔" else ""
            sb.appendLine("${names.short(side)}: $sets$mark")
        }
        sb.appendLine()
        sb.appendLine("⏱ ${s.duration}: ${duration(rec.clockMs)}")
        sb.appendLine("🕘 ${s.startTime}: ${time(rec.startedAt, lang)} · ${s.endTime}: ${time(rec.endedAt, lang)}")
        sb.appendLine("📍 ${s.place}: ${place(rec, s)}")
        sb.appendLine("📋 ${s.format}: ${formatLabel(rec, s)}")
        sb.appendLine("${s.pointsWon}: ${pointsWon(rec, Side.P1)} - ${pointsWon(rec, Side.P2)} · ${s.gamesWon}: ${gamesWon(state, Side.P1)} - ${gamesWon(state, Side.P2)}")
        if (rec.batteryStart.isNotEmpty() || rec.batteryEnd.isNotEmpty()) sb.appendLine("🔋 ${s.bandsBattery}: ${batteryLine(rec, s)}")
        sb.appendLine()
        sb.append("#tennis · ${s.generatedWith}")
        return sb.toString()
    }

    /** Immagine 1080x1350 (formato adatto ai social) con il risultato. */
    fun renderCard(rec: MatchRecord, state: MatchState, s: Strings, names: Names): Bitmap {
        val w = 1080
        val h = 1350
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val bg = Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, h.toFloat(), Color.rgb(12, 18, 32), Color.rgb(10, 60, 48), Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bg)

        fun paint(size: Float, color: Int, bold: Boolean = false, align: Paint.Align = Paint.Align.LEFT) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = if (bold) Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) else Typeface.SANS_SERIF
            textAlign = align
        }
        val white = Color.WHITE
        val grey = Color.rgb(170, 184, 200)
        val yellow = Color.rgb(255, 214, 0)
        val red = Color.rgb(229, 57, 53)
        val lang = rec.options.lang
        val winner = state.winner ?: Side.P1

        c.drawText("TENNIS SCORE MANAGER", w / 2f, 110f, paint(40f, Color.rgb(198, 244, 50), true, Paint.Align.CENTER).apply { letterSpacing = 0.2f })
        val header = listOfNotNull(rec.setup.club.ifBlank { null }, rec.setup.court.ifBlank { null }?.let { "${s.court} $it" }).joinToString(" · ")
        if (header.isNotEmpty()) paint(46f, white, true, Paint.Align.CENTER).let { c.drawText(fit(header, it, w - 120f), w / 2f, 175f, it) }
        c.drawText(date(rec.startedAt, lang).replaceFirstChar { it.uppercase() }, w / 2f, 235f, paint(36f, grey, false, Paint.Align.CENTER))

        // Riquadro del vincitore
        val box = RectF(60f, 290f, w - 60f, 470f)
        c.drawRoundRect(box, 36f, 36f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(60, 255, 255, 255) })
        c.drawText("🏆  ${s.winner.uppercase()}", w / 2f, 355f, paint(38f, grey, true, Paint.Align.CENTER))
        paint(66f, if (winner == Side.P1) yellow else red, true, Paint.Align.CENTER).let { c.drawText(fit(names.side(winner), it, box.width() - 60f), w / 2f, 440f, it) }

        // Tabella punteggio
        val top = 540f
        val rowH = 190f
        val setCount = state.sets.size.coerceAtLeast(1)
        val colW = 130f
        val firstCol = w - 70f - setCount * colW
        for ((i, side) in listOf(Side.P1, Side.P2).withIndex()) {
            val y = top + i * (rowH + 30f)
            val rect = RectF(60f, y, w - 60f, y + rowH)
            c.drawRoundRect(rect, 28f, 28f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(40, 255, 255, 255) })
            c.drawRoundRect(RectF(60f, y, 84f, y + rowH), 12f, 12f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (side == Side.P1) yellow else red })
            val players = names.players(side)
            // I nomi non devono finire sotto la colonna del primo set.
            val nameW = firstCol - 120f - 20f
            if (players.size == 1) {
                paint(58f, white, side == winner).let { c.drawText(fit(players[0], it, nameW), 120f, y + rowH / 2 + 22f, it) }
            } else {
                paint(50f, white, side == winner).let { c.drawText(fit(players[0], it, nameW), 120f, y + rowH / 2 - 14f, it) }
                paint(50f, white, side == winner).let { c.drawText(fit(players[1], it, nameW), 120f, y + rowH / 2 + 50f, it) }
            }
            for ((j, set) in state.sets.withIndex()) {
                val x = firstCol + j * colW + colW / 2
                val won = set.winner == side
                c.drawText(set.shown(side).toString(), x, y + rowH / 2 + 34f, paint(if (set.matchTiebreak) 64f else 92f, if (won) white else grey, won, Paint.Align.CENTER))
                if (set.hasTiebreak && !set.matchTiebreak && !won) {
                    c.drawText(set.tb(side).toString(), x + 42f, y + rowH / 2 - 22f, paint(38f, grey, false, Paint.Align.LEFT))
                }
            }
        }

        // Dati della partita
        var y = top + 2 * (rowH + 30f) + 70f
        fun row(l: String, v: String) {
            paint(34f, grey).let { c.drawText(fit(l, it, 380f - 80f - 16f), 80f, y, it) }
            paint(40f, white, true).let { c.drawText(fit(v, it, w - 60f - 380f), 380f, y, it) }
            y += 66f
        }
        row(s.duration, duration(rec.clockMs))
        row("${s.startTime} / ${s.endTime}", "${time(rec.startedAt, lang)} – ${time(rec.endedAt, lang)}")
        row(s.place, rec.location?.address?.ifBlank { null } ?: place(rec, s))
        row(s.format, formatLabel(rec, s))
        c.drawText(s.generatedWith, w / 2f, h - 50f, paint(30f, grey, false, Paint.Align.CENTER))
        return bmp
    }

    /**
     * Testo che sta in [maxWidth] pixel (non in un numero di lettere: "WWW" e "iii" sono larghe diverse):
     * prima si rimpicciolisce il carattere di [p] fino al 75%, poi si tronca con "…".
     */
    private fun fit(t: String, p: Paint, maxWidth: Float): String {
        val minSize = p.textSize * 0.75f
        while (p.measureText(t) > maxWidth && p.textSize > minSize) p.textSize -= 2f
        if (p.measureText(t) <= maxWidth) return t
        var end = t.length
        while (end > 1 && p.measureText(t, 0, end) + p.measureText("…") > maxWidth) end--
        if (end > 1 && Character.isHighSurrogate(t[end - 1])) end--
        return t.substring(0, end).trimEnd() + "…"
    }
}
TSM_EOF

# ---------------------------------------------------------------- app/src/main/java/com/tennis/scoremanager/data/Storage.kt
cat > "$DEST/app/src/main/java/com/tennis/scoremanager/data/Storage.kt" << 'TSM_EOF'
package com.tennis.scoremanager.data

import android.content.Context
import android.util.Log
import com.tennis.scoremanager.tv.TvSettings
import kotlinx.serialization.json.Json
import java.io.File

/** Preferenze e partite salvate in locale (memoria interna dell'app). */
class Storage(context: Context) {

    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("tsm", Context.MODE_PRIVATE)

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    private val matchesDir: File get() = File(app.filesDir, "matches").apply { mkdirs() }
    private val lastFinishedFile: File get() = File(app.filesDir, "last_finished.json")

    var setup: SetupData
        get() = read(prefs.getString("setup", null)) ?: SetupData()
        set(v) = prefs.edit().putString("setup", json.encodeToString(SetupData.serializer(), v)).apply()

    var options: MatchOptions
        get() = prefs.getString("options", null)?.let {
            runCatching { json.decodeFromString(MatchOptions.serializer(), it) }.getOrNull()
        } ?: MatchOptions()
        set(v) = prefs.edit().putString("options", json.encodeToString(MatchOptions.serializer(), v)).apply()

    /** Tabellone TV: impostazioni del telefono, non della partita. */
    var tv: TvSettings
        get() = prefs.getString("tv", null)?.let {
            runCatching { json.decodeFromString(TvSettings.serializer(), it) }.getOrNull()
        } ?: TvSettings()
        set(v) = prefs.edit().putString("tv", json.encodeToString(TvSettings.serializer(), v)).apply()

    /** Ultimo indirizzo a cui si è collegato questo telefono usato come tabellone ("192.168.43.1:8080"). */
    var lastScoreboardHost: String?
        get() = prefs.getString("display_host", null)
        set(v) = prefs.edit().putString("display_host", v).apply()

    private fun read(s: String?): SetupData? =
        s?.let { runCatching { json.decodeFromString(SetupData.serializer(), it) }.getOrNull() }

    fun bandAddress(p1: Boolean): String? = prefs.getString(if (p1) "band_p1" else "band_p2", null)
    fun bandName(p1: Boolean): String? = prefs.getString(if (p1) "band_p1_name" else "band_p2_name", null)
    fun setBand(p1: Boolean, address: String?, name: String?) {
        prefs.edit()
            .putString(if (p1) "band_p1" else "band_p2", address)
            .putString(if (p1) "band_p1_name" else "band_p2_name", name)
            .apply()
    }

    /** Consumo di base misurato sul campo per ogni braccialetto (mA), per stimare l'autonomia. */
    fun bandBaseMa(address: String): Double? =
        prefs.getFloat("base_ma_$address", -1f).takeIf { it > 0f }?.toDouble()

    fun setBandBaseMa(address: String, ma: Double) {
        prefs.edit().putFloat("base_ma_$address", ma.toFloat()).apply()
    }

    var historyTree: String?
        get() = prefs.getString("history_tree", null)
        set(v) = prefs.edit().putString("history_tree", v).apply()

    /** Id della partita il cui riepilogo è a schermo (null dopo "Nuova partita" o "Esci"). */
    var summaryOpen: String?
        get() = prefs.getString("summary_open", null)
        set(v) = prefs.edit().putString("summary_open", v).apply()

    /** Scrittura atomica: prima su file temporaneo, poi rinomina (sicuro anche se il telefono si spegne). */
    @Synchronized
    fun saveMatch(rec: MatchRecord) {
        runCatching {
            val f = File(matchesDir, "${rec.id}.json")
            val tmp = File(matchesDir, "${rec.id}.tmp")
            tmp.writeText(json.encodeToString(MatchRecord.serializer(), rec))
            if (!tmp.renameTo(f)) {
                f.delete()
                tmp.renameTo(f)
            }
        }.onFailure { Log.e("Storage", "Salvataggio partita fallito", it) }
    }

    fun loadUnfinished(): List<MatchRecord> =
        matchesDir.listFiles { f -> f.extension == "json" }.orEmpty()
            .mapNotNull { f -> runCatching { json.decodeFromString(MatchRecord.serializer(), f.readText()) }.getOrNull() }
            .filter { !it.finished }
            .sortedByDescending { it.updatedAt }

    @Synchronized
    fun deleteMatch(id: String) {
        File(matchesDir, "$id.json").delete()
    }

    fun saveLastFinished(rec: MatchRecord) {
        runCatching { lastFinishedFile.writeText(json.encodeToString(MatchRecord.serializer(), rec)) }
    }

    fun loadLastFinished(): MatchRecord? =
        runCatching { json.decodeFromString(MatchRecord.serializer(), lastFinishedFile.readText()) }.getOrNull()

    /** Cartella predefinita dello storico se l'utente non ne sceglie una. */
    val defaultHistoryDir: File
        get() = File(app.getExternalFilesDir(null) ?: app.filesDir, "Storico").apply { mkdirs() }
}
TSM_EOF

# ---------------------------------------------------------------- app/src/main/java/com/tennis/scoremanager/model/Rules.kt
cat > "$DEST/app/src/main/java/com/tennis/scoremanager/model/Rules.kt" << 'TSM_EOF'
package com.tennis.scoremanager.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.Locale

/** Giocatore 1 (giallo) e Giocatore 2 (rosso). Nel doppio indica la squadra. */
@Serializable
enum class Side {
    P1, P2;

    val other: Side get() = if (this == P1) P2 else P1
}

/**
 * Lingue di app, chiamate e braccialetti. [code] va al tabellone TV e al braccialetto; [locale] sceglie la voce
 * della sintesi vocale (preferita, se c'è, quella del paese) e il formato delle date. [label] si scrive nella lingua stessa.
 */
@Serializable
enum class Lang(val code: String, val locale: Locale, val label: String, val flag: String) {
    IT("it", Locale.ITALY, "Italiano", "🇮🇹"),
    EN("en", Locale.UK, "English", "🇬🇧"),
    FR("fr", Locale.FRANCE, "Français", "🇫🇷"),
    DE("de", Locale.GERMANY, "Deutsch", "🇩🇪"),
    ES("es", Locale.forLanguageTag("es-ES"), "Español", "🇪🇸"),
    PT("pt", Locale.forLanguageTag("pt-BR"), "Português", "🇧🇷");

    companion object {
        fun fromCode(code: String?): Lang? = entries.firstOrNull { it.code == code?.lowercase() }
    }
}

@Serializable
enum class MatchFormat {
    /** Al meglio dei tre set, tie-break a 7 punti sul 6-6 in ogni set. */
    BEST_OF_THREE,

    /** Due set con tie-break a 7 sul 6-6; sull'1-1 si gioca un match tie-break (super tie-break) a 10. */
    TWO_SETS_MATCH_TIEBREAK,
}

/** Regole della partita decise prima dell'inizio (formato, sorteggio, lati del campo). */
@Serializable
data class RulesConfig(
    val format: MatchFormat = MatchFormat.BEST_OF_THREE,
    /** No-Ad: sul 40-40 si gioca il punto decisivo. */
    val noAd: Boolean = false,
    val doubles: Boolean = false,
    /** Chi serve il primo game della partita. */
    val firstServer: Side = Side.P1,
    /** Vista dal giudice di sedia: il Giocatore 1 inizia sul lato sinistro? */
    val p1StartsLeft: Boolean = true,
    /** Doppio: quale giocatore della squadra (0 o 1) serve per primo nel primo set. */
    val firstServerP1: Int = 0,
    val firstServerP2: Int = 0,
)

/** Eventi della partita: lo stato si ricalcola sempre rigiocandoli (undo sicuro a ogni livello). */
@Serializable
sealed class MatchEvent {
    @Serializable
    @SerialName("point")
    data class Point(val winner: Side, val at: Long = 0L) : MatchEvent()

    /** Doppio: ordine di servizio scelto all'inizio di un set (ammesso solo prima del primo punto del set). */
    @Serializable
    @SerialName("serveOrder")
    data class ServeOrder(val setNumber: Int, val firstP1: Int, val firstP2: Int) : MatchEvent()
}

/** Punteggio di un set concluso. Per il match tie-break g1/g2 valgono 1-0 e i punti stanno in tb1/tb2. */
@Serializable
data class SetScore(
    val g1: Int,
    val g2: Int,
    val tb1: Int? = null,
    val tb2: Int? = null,
    val matchTiebreak: Boolean = false,
) {
    val winner: Side get() = if (g1 > g2) Side.P1 else Side.P2
    val hasTiebreak: Boolean get() = tb1 != null && tb2 != null

    fun games(side: Side): Int = if (side == Side.P1) g1 else g2
    fun tb(side: Side): Int? = if (side == Side.P1) tb1 else tb2

    /** Numeri da leggere/mostrare per questo set: game, oppure punti per il match tie-break. */
    fun shown(side: Side): Int = if (matchTiebreak) tb(side) ?: 0 else games(side)
}

enum class TiebreakKind { NONE, SET, MATCH }
TSM_EOF

# ---------------------------------------------------------------- app/src/main/java/com/tennis/scoremanager/model/ScoreEngine.kt
cat > "$DEST/app/src/main/java/com/tennis/scoremanager/model/ScoreEngine.kt" << 'TSM_EOF'
package com.tennis.scoremanager.model

/**
 * Stato della partita in un istante. È immutabile: ogni punto produce un nuovo stato.
 *
 * Il servizio non è memorizzato game per game ma ricavato dal "turno di servizio" nel set:
 * turno = game giocati nel set (+ turni del tie-break). Nei turni pari serve chi ha iniziato il set.
 * Così la stessa formula copre singolare, doppio (rotazione a 4) e tie-break (1 punto, poi 2 a testa).
 */
data class MatchState(
    val rules: RulesConfig,
    val sets: List<SetScore> = emptyList(),
    val g1: Int = 0,
    val g2: Int = 0,
    /** Punti del game corrente (0,1,2,3,4...) oppure punti del tie-break. */
    val pt1: Int = 0,
    val pt2: Int = 0,
    val tiebreak: TiebreakKind = TiebreakKind.NONE,
    /** Squadra/giocatore che serve il primo game del set corrente. */
    val setStartServer: Side = rules.firstServer,
    /** Doppio: indice (0/1) di chi serve per primo, per squadra, nel set corrente. */
    val order1: Int = rules.firstServerP1,
    val order2: Int = rules.firstServerP2,
    /** Vista arbitro: il Giocatore 1 è sul lato sinistro? */
    val p1Left: Boolean = rules.p1StartsLeft,
    val winner: Side? = null,
    val pointsPlayed: Int = 0,
) {
    val isFinished: Boolean get() = winner != null
    val setNumber: Int get() = sets.size + 1
    val inTiebreak: Boolean get() = tiebreak != TiebreakKind.NONE
    val tiebreakTarget: Int get() = if (tiebreak == TiebreakKind.MATCH) 10 else 7

    fun games(side: Side): Int = if (side == Side.P1) g1 else g2
    fun points(side: Side): Int = if (side == Side.P1) pt1 else pt2
    fun setsWon(side: Side): Int = sets.count { it.winner == side }
    fun order(side: Side): Int = if (side == Side.P1) order1 else order2
    fun leftSide(): Side = if (p1Left) Side.P1 else Side.P2

    /** Turno di servizio corrente all'interno del set. */
    val serviceTurn: Int
        get() = if (inTiebreak) g1 + g2 + (pt1 + pt2 + 1) / 2 else g1 + g2

    fun serverOfTurn(turn: Int): Side = if (turn % 2 == 0) setStartServer else setStartServer.other

    /** Doppio: quale giocatore (0/1) della squadra [side] serve al turno [turn]. */
    fun playerOfTurn(turn: Int, side: Side): Int = (order(side) + turn / 2) % 2

    val server: Side get() = serverOfTurn(serviceTurn)
    val receiver: Side get() = server.other

    /** Doppio: indice del giocatore al servizio nella sua squadra (nel singolare è sempre 0). */
    val serverPlayer: Int get() = if (rules.doubles) playerOfTurn(serviceTurn, server) else 0

    /** Punteggio "da tabellone" del game: 0 15 30 40 AD, oppure i punti del tie-break. */
    fun pointLabel(side: Side): String {
        if (inTiebreak) return points(side).toString()
        val p = points(side)
        val o = points(side.other)
        if (p >= 3 && o >= 3) {
            return when {
                p == o -> "40"
                p > o -> "AD"
                else -> "40"
            }
        }
        return POINT_LABELS[p.coerceAtMost(3)]
    }

    val isDeuce: Boolean get() = !inTiebreak && pt1 >= 3 && pt1 == pt2

    companion object {
        val POINT_LABELS = listOf("0", "15", "30", "40")
    }
}

/** Cosa è successo con l'ultimo punto: serve a chiamate vocali, pause, messaggi e braccialetti. */
data class Transition(
    val pointWinner: Side,
    val gameWinner: Side? = null,
    val setWinner: Side? = null,
    val matchWinner: Side? = null,
    /** Cambio campo dopo questo punto/game. */
    val changeEnds: Boolean = false,
    /** Si è arrivati al 6-6: inizia il tie-break. */
    val tiebreakStarted: Boolean = false,
    /** Set pari nel formato con match tie-break: inizia il super tie-break. */
    val matchTiebreakStarted: Boolean = false,
    /** Il game appena vinto era il primo del set. */
    val firstGameOfSet: Boolean = false,
    /** Il punto è stato giocato in un tie-break (di set o di match). */
    val inTiebreak: Boolean = false,
)

data class Step(val state: MatchState, val transition: Transition?)

object ScoreEngine {

    fun initial(rules: RulesConfig): MatchState = MatchState(rules = rules)

    /** Ricostruisce lo stato applicando in ordine tutti gli eventi. */
    fun replay(rules: RulesConfig, events: List<MatchEvent>): MatchState =
        events.fold(initial(rules)) { s, e -> apply(s, e).state }

    fun apply(state: MatchState, event: MatchEvent): Step = when (event) {
        is MatchEvent.Point -> pointWonBy(state, event.winner)
        is MatchEvent.ServeOrder -> Step(applyServeOrder(state, event), null)
    }

    /** L'ordine di servizio del doppio si può cambiare solo prima del primo punto del set. */
    fun canChangeServeOrder(s: MatchState): Boolean =
        s.rules.doubles && !s.isFinished && s.g1 == 0 && s.g2 == 0 && s.pt1 == 0 && s.pt2 == 0

    private fun applyServeOrder(s: MatchState, e: MatchEvent.ServeOrder): MatchState {
        if (!canChangeServeOrder(s) || e.setNumber != s.setNumber) return s
        return s.copy(order1 = e.firstP1.coerceIn(0, 1), order2 = e.firstP2.coerceIn(0, 1))
    }

    fun pointWonBy(s: MatchState, w: Side): Step {
        if (s.isFinished) return Step(s, null)
        val pt1 = s.pt1 + if (w == Side.P1) 1 else 0
        val pt2 = s.pt2 + if (w == Side.P2) 1 else 0
        val pw = if (w == Side.P1) pt1 else pt2
        val po = if (w == Side.P1) pt2 else pt1
        val played = s.copy(pt1 = pt1, pt2 = pt2, pointsPlayed = s.pointsPlayed + 1)

        if (s.inTiebreak) {
            if (pw >= s.tiebreakTarget && pw - po >= 2) {
                val set = if (s.tiebreak == TiebreakKind.MATCH) {
                    SetScore(
                        g1 = if (w == Side.P1) 1 else 0,
                        g2 = if (w == Side.P2) 1 else 0,
                        tb1 = pt1, tb2 = pt2, matchTiebreak = true,
                    )
                } else {
                    SetScore(
                        g1 = s.g1 + if (w == Side.P1) 1 else 0,
                        g2 = s.g2 + if (w == Side.P2) 1 else 0,
                        tb1 = pt1, tb2 = pt2,
                    )
                }
                return closeSet(played, w, set, fromTiebreak = true)
            }
            // Nel tie-break si cambia campo ogni 6 punti giocati.
            val change = (pt1 + pt2) % 6 == 0
            return Step(
                played.copy(p1Left = if (change) !s.p1Left else s.p1Left),
                Transition(pointWinner = w, changeEnds = change, inTiebreak = true),
            )
        }

        val gameWon = if (s.rules.noAd) pw >= 4 else pw >= 4 && pw - po >= 2
        if (!gameWon) return Step(played, Transition(pointWinner = w))

        val g1 = s.g1 + if (w == Side.P1) 1 else 0
        val g2 = s.g2 + if (w == Side.P2) 1 else 0
        val gw = if (w == Side.P1) g1 else g2
        val go = if (w == Side.P1) g2 else g1
        val total = g1 + g2

        if (gw >= 6 && gw - go >= 2) {
            return closeSet(played, w, SetScore(g1, g2), fromTiebreak = false)
        }
        if (g1 == 6 && g2 == 6) {
            // 6-6: dodicesimo game (pari) quindi nessun cambio campo; parte il tie-break.
            return Step(
                played.copy(g1 = g1, g2 = g2, pt1 = 0, pt2 = 0, tiebreak = TiebreakKind.SET),
                Transition(pointWinner = w, gameWinner = w, tiebreakStarted = true),
            )
        }
        // Cambio campo dopo ogni game dispari del set (1°, 3°, 5°...).
        val change = total % 2 == 1
        return Step(
            played.copy(g1 = g1, g2 = g2, pt1 = 0, pt2 = 0, p1Left = if (change) !s.p1Left else s.p1Left),
            Transition(pointWinner = w, gameWinner = w, changeEnds = change, firstGameOfSet = total == 1),
        )
    }

    private fun closeSet(s: MatchState, w: Side, set: SetScore, fromTiebreak: Boolean): Step {
        val sets = s.sets + set
        val won = sets.count { it.winner == w }
        if (won >= 2) {
            val final = s.copy(
                sets = sets, g1 = 0, g2 = 0, pt1 = 0, pt2 = 0,
                tiebreak = TiebreakKind.NONE, winner = w,
            )
            return Step(
                final,
                Transition(pointWinner = w, gameWinner = w, setWinner = w, matchWinner = w, inTiebreak = fromTiebreak),
            )
        }
        // Il tie-break conta come un game: un set 7-6 ha 13 game (dispari) e fa cambiare campo.
        val gamesInSet = set.g1 + set.g2
        val change = gamesInSet % 2 == 1
        // Serve per primo nel nuovo set chi non ha servito l'ultimo turno
        // (dopo un tie-break: chi ha ricevuto il primo punto del tie-break).
        val nextStart = s.serverOfTurn(gamesInSet)
        val nextTiebreak =
            if (s.rules.format == MatchFormat.TWO_SETS_MATCH_TIEBREAK && sets.size == 2) TiebreakKind.MATCH
            else TiebreakKind.NONE
        val next = s.copy(
            sets = sets, g1 = 0, g2 = 0, pt1 = 0, pt2 = 0,
            tiebreak = nextTiebreak,
            setStartServer = nextStart,
            order1 = naturalNextServer(s, Side.P1),
            order2 = naturalNextServer(s, Side.P2),
            p1Left = if (change) !s.p1Left else s.p1Left,
        )
        return Step(
            next,
            Transition(
                pointWinner = w, gameWinner = w, setWinner = w,
                changeEnds = change,
                matchTiebreakStarted = nextTiebreak == TiebreakKind.MATCH,
                inTiebreak = fromTiebreak,
            ),
        )
    }

    /**
     * Doppio: se nessuno cambia l'ordine a inizio set, la rotazione prosegue:
     * per ogni squadra serve il compagno di chi ha servito per ultimo.
     * [s] è lo stato con l'ultimo punto del set già contato ma con i game non ancora aggiornati.
     */
    private fun naturalNextServer(s: MatchState, side: Side): Int {
        // Ultimo punto del tie-break = punto n. (pt1+pt2-1), turno (k+1)/2; fuori dal tie-break il game appena vinto.
        val lastTurn = if (s.inTiebreak) s.g1 + s.g2 + (s.pt1 + s.pt2) / 2 else s.g1 + s.g2
        var turn = lastTurn
        while (turn >= 0 && s.serverOfTurn(turn) != side) turn--
        if (turn < 0) return s.order(side)
        return 1 - s.playerOfTurn(turn, side)
    }

    /** Il prossimo punto vinto da [side] chiuderebbe set o partita? (per "set point" / "match point"). */
    fun lookahead(s: MatchState, side: Side): Transition? =
        if (s.isFinished) null else pointWonBy(s, side).transition

    /** Palla break: il ricevitore vincerebbe il game col prossimo punto (fuori dal tie-break). */
    fun isBreakPoint(s: MatchState): Boolean {
        if (s.isFinished || s.inTiebreak) return false
        return lookahead(s, s.receiver)?.gameWinner == s.receiver
    }
}
TSM_EOF

# ---------------------------------------------------------------- app/src/main/java/com/tennis/scoremanager/service/MatchService.kt
cat > "$DEST/app/src/main/java/com/tennis/scoremanager/service/MatchService.kt" << 'TSM_EOF'
package com.tennis.scoremanager.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.tennis.scoremanager.MainActivity
import com.tennis.scoremanager.R
import com.tennis.scoremanager.Screen
import com.tennis.scoremanager.TsmApp

/**
 * Servizio in primo piano durante la partita con i braccialetti, e sempre col tabellone TV acceso: tiene attivo il
 * processo (Bluetooth, server del tabellone, voce e cronometri) anche con lo schermo spento o l'app in secondo piano.
 */
class MatchService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    /** App tolta dalle recenti durante la partita: si chiude come con "Esci" (partita salvata tra le sospese). */
    override fun onTaskRemoved(rootIntent: Intent?) {
        (application as TsmApp).controller.onTaskRemoved { stopSelf() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        val c = (application as TsmApp).controller
        val s = c.strings
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, s.notifChannel, NotificationManager.IMPORTANCE_LOW))
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val bands = c.options.value.mode == com.tennis.scoremanager.data.PlayMode.BANDS
        val tv = c.tv.value.enabled
        // Fuori dalla partita (riepilogo, nuova partita, impostazioni) il servizio resta solo per il tabellone TV.
        val inMatch = c.screen.value == Screen.START || c.screen.value == Screen.MATCH
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_tennis)
            .setContentTitle("Tennis Score Manager")
            .setContentText(if (tv && !inMatch) s.notifTvOnly else s.notifText(bands, tv))
            .setOngoing(true)
            .setContentIntent(open)
            .build()
        try {
            ServiceCompat.startForeground(
                this, 1, notification,
                if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0,
            )
        } catch (e: Exception) {
            Log.w("MatchService", "Servizio in primo piano non avviato", e)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    companion object {
        private const val CHANNEL = "match"

        fun start(ctx: Context) {
            // Il tipo "connectedDevice" richiede il permesso Bluetooth o quello di rete (CHANGE_NETWORK_STATE,
            // concesso da solo): senza nessuno dei due Android chiuderebbe l'app.
            fun granted(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
            if (Build.VERSION.SDK_INT >= 31 &&
                !granted(Manifest.permission.BLUETOOTH_CONNECT) && !granted(Manifest.permission.CHANGE_NETWORK_STATE)
            ) return
            runCatching { ContextCompat.startForegroundService(ctx, Intent(ctx, MatchService::class.java)) }
                .onFailure { Log.w("MatchService", "start", it) }
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, MatchService::class.java))
        }
    }
}
TSM_EOF

# ---------------------------------------------------------------- app/src/main/java/com/tennis/scoremanager/tv/DisplayActivity.kt
cat > "$DEST/app/src/main/java/com/tennis/scoremanager/tv/DisplayActivity.kt" << 'TSM_EOF'
package com.tennis.scoremanager.tv

import android.annotation.SuppressLint
import android.app.Presentation
import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.hardware.display.DisplayManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Display
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.tennis.scoremanager.TsmApp
import com.tennis.scoremanager.ui.BigButton
import com.tennis.scoremanager.ui.GhostButton
import com.tennis.scoremanager.ui.Strings
import com.tennis.scoremanager.ui.TsmColors
import com.tennis.scoremanager.ui.TsmTheme
import com.tennis.scoremanager.ui.stringsFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * "Usa come tabellone": questo telefono trova da solo il telefono dell'arbitro sulla rete dell'hotspot
 * e mostra il tabellone a schermo intero, in orizzontale, con lo schermo sempre acceso.
 * Con un monitor collegato (cavo USB-C/HDMI) il tabellone va sul monitor con il suo formato 16:9
 * (Presentation) e il telefono resta libero; senza, lo schermo del telefono si può trasmettere
 * a un Chromecast ("Trasmetti schermo").
 */
class DisplayActivity : ComponentActivity() {

    private val controller get() = (application as TsmApp).controller
    private val s: Strings get() = stringsFor(controller.options.value.lang)
    private lateinit var finder: ScoreboardFinder
    private lateinit var displays: DisplayManager
    private var cm: ConnectivityManager? = null

    private val found = MutableStateFlow<FoundScoreboard?>(null)
    /** Pagina da mostrare: cambia a ogni collegamento, così si ricarica anche se l'indirizzo è lo stesso. */
    private val page = MutableStateFlow<ScoreboardPage?>(null)
    private var loads = 0
    private val searching = MutableStateFlow(false)
    private val notFound = MutableStateFlow(false)
    private val external = MutableStateFlow<Display?>(null)
    private val showHere = MutableStateFlow(false)
    private var presentation: ScoreboardPresentation? = null
    /** Tabellone sul monitor esterno, per l'interfaccia ([presentation] da sola non la aggiorna). */
    private val onMonitor = MutableStateFlow(false)
    private var systemDismissAt = 0L
    private var searchJob: Job? = null
    private var lastBack = 0L
    /** Solo per le prove (adb): accetta anche il server di questo stesso telefono. */
    private var allowSelf = false

    /** La rete Wi-Fi (anche senza internet): se cade o cambia (hotspot spento e riacceso) ci si ricollega. */
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = runOnUiThread { onNetwork(network) }
        override fun onLost(network: Network) = runOnUiThread { onNetworkLost(network) }
    }

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(id: Int) = refreshExternal()
        override fun onDisplayRemoved(id: Int) = refreshExternal()
        override fun onDisplayChanged(id: Int) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finder = ScoreboardFinder(this)
        displays = getSystemService(DisplayManager::class.java)
        allowSelf = intent.getBooleanExtra("allowSelf", false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.setBackgroundColor(Color.BLACK)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Due volte indietro per uscire: un tocco per sbaglio non spegne il tabellone.
                val now = SystemClock.elapsedRealtime()
                if (found.value == null || now - lastBack < 2_500) finish()
                else Toast.makeText(this@DisplayActivity, s.displayBackAgain, Toast.LENGTH_SHORT).show()
                lastBack = now
            }
        })
        displays.registerDisplayListener(displayListener, Handler(Looper.getMainLooper()))
        refreshExternal()
        cm = getSystemService(ConnectivityManager::class.java)
        setContent { TsmTheme { DisplayContent() } }
        search()
        val wifi = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        runCatching { cm?.requestNetwork(wifi, networkCallback) }.onFailure { Log.w("Tabellone", "requestNetwork", it) }
    }

    override fun onDestroy() {
        displays.unregisterDisplayListener(displayListener)
        runCatching { cm?.unregisterNetworkCallback(networkCallback) }
        presentation.also { presentation = null }?.dismiss()
        cm?.bindProcessToNetwork(null)
        super.onDestroy()
    }

    // ---------------------------------------------------------------- ricerca e collegamento

    private fun search(manual: String? = null) {
        searchJob?.cancel()
        notFound.value = false
        searching.value = true
        searchJob = lifecycleScope.launch {
            val result = if (manual != null) {
                ScoreboardFinder.parseAddress(manual)?.let { (h, p) -> withContext(Dispatchers.IO) { finder.verify(h, p) } }
            } else {
                // Il server di questo telefono (tabellone acceso anche qui) si salta e la ricerca continua.
                finder.find(controller.storage.lastScoreboardHost, accept = ::notSelf)
            }
            searching.value = false
            if (result != null) connect(result) else notFound.value = found.value == null
        }
    }

    /** Il server di questo stesso telefono non è il tabellone da mostrare (tranne che nelle prove). */
    private fun notSelf(f: FoundScoreboard) = allowSelf || f.host !in TvServer.localAddresses()

    private fun connect(f: FoundScoreboard) {
        // Collegato all'hotspot dell'altro telefono: il traffico della pagina deve passare dal Wi-Fi anche se
        // Android, non vedendo internet lì, preferirebbe i dati mobili.
        cm?.bindProcessToNetwork(f.network)
        controller.storage.lastScoreboardHost = f.label
        found.value = f
        page.value = ScoreboardPage(f.url, ++loads)
        updatePresentation()
    }

    /**
     * Collegamento perso: la pagina tace da 20 secondi (poi lo ridice ogni 30), oppure la rete Wi-Fi è caduta o
     * cambiata. Si ritrova lo stesso tabellone, anche a un indirizzo nuovo, si ricollega la rete e si ricarica la
     * pagina. Mai un altro campo della stessa rete: per quello si esce e si cerca di nuovo.
     */
    private fun recover(restart: Boolean = false) {
        val prev = found.value ?: return
        if (searchJob?.isActive == true) {
            if (!restart) return
            searchJob?.cancel()
        }
        searchJob = lifecycleScope.launch {
            val again = finder.find(prev.label, timeoutMs = 30_000) { notSelf(it) && it.sameAs(prev) }
            if (again != null) connect(again)
        }
    }

    /** Rete Wi-Fi disponibile: si riprova la ricerca andata a vuoto, o si passa alla rete nuova (hotspot riacceso). */
    private fun onNetwork(network: Network) {
        if (isDestroyed) return
        val f = found.value
        if (f == null) {
            if (searchJob?.isActive != true) search()
        } else if (f.network != null && f.network != network) {
            recover(restart = true)
        }
    }

    private fun onNetworkLost(network: Network) {
        if (isDestroyed) return
        val f = found.value ?: return
        if (f.network != network) return
        // Legati a una rete che non c'è più, anche dopo il ritorno del Wi-Fi fallirebbe tutto: si slega e si cerca.
        cm?.bindProcessToNetwork(null)
        recover(restart = true)
    }

    // ---------------------------------------------------------------- monitor esterno

    private fun refreshExternal() {
        external.value = displays.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION).firstOrNull()
        updatePresentation()
    }

    private fun updatePresentation() {
        val d = external.value
        val p = page.value
        val current = presentation
        if (d == null || p == null) {
            presentation = null
            current?.dismiss()
        } else if (current == null || current.display.displayId != d.displayId) {
            presentation = null
            current?.dismiss()
            presentation = ScoreboardPresentation(this, d, p) { runOnUiThread { recover() } }.also { pr ->
                pr.setOnDismissListener { onPresentationDismissed(pr) }
                runCatching { pr.show() }.onFailure { presentation = null }
            }
        } else {
            current.load(p)
        }
        monitorChanged()
    }

    /** Chiuso dal sistema (monitor staccato o preso da un'altra app): si riprova una volta, poi resta sul telefono. */
    private fun onPresentationDismissed(pr: ScoreboardPresentation) {
        if (presentation !== pr || isDestroyed) return  // chiuso da qui
        presentation = null
        val now = SystemClock.elapsedRealtime()
        val retry = now - systemDismissAt > 10_000
        systemDismissAt = now
        if (retry) refreshExternal() else monitorChanged()
    }

    private fun monitorChanged() {
        onMonitor.value = presentation != null
        // Tabellone sul monitor: il telefono si abbassa al minimo (resta acceso, se no si spegne anche l'uscita video).
        window.attributes = window.attributes.apply {
            screenBrightness = if (presentation != null && !showHere.value) 0.05f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
    }

    // ---------------------------------------------------------------- interfaccia

    @Composable
    private fun DisplayContent() {
        val f by found.collectAsState()
        val p by page.collectAsState()
        val monitor by onMonitor.collectAsState()
        val here by showHere.collectAsState()
        val busy by searching.collectAsState()
        val missing by notFound.collectAsState()
        val shown = f
        val current = p
        when {
            shown == null || current == null -> SearchScreen(busy, missing)
            monitor && !here -> OnMonitorScreen(shown)
            else -> AndroidView(
                factory = { ctx -> scoreboardWebView(ctx) { runOnUiThread { recover() } } },
                update = { web -> web.showPage(current) },
                // WebView non più mostrata (es. il tabellone passa al monitor): va distrutta, se no il suo
                // collegamento in diretta resta aperto e occupa un posto sul server.
                onRelease = { web -> web.release() },
                modifier = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black),
            )
        }
    }

    @Composable
    private fun SearchScreen(busy: Boolean, missing: Boolean) {
        val str = s
        var address by remember { mutableStateOf(controller.storage.lastScoreboardHost ?: "") }
        Row(
            Modifier.fillMaxSize().background(TsmColors.Background).padding(horizontal = 32.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Tv, null, tint = TsmColors.Ball, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(str.displayMode, color = TsmColors.TextMain, fontSize = 26.sp, fontWeight = FontWeight.Black)
                }
                if (busy) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(24.dp), color = TsmColors.Ball, strokeWidth = 3.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(str.displaySearching, color = TsmColors.TextMain, fontSize = 18.sp)
                    }
                } else if (missing) {
                    Text(str.displayNotFound, color = TsmColors.Orange, fontSize = 16.sp)
                }
                Text(str.displaySteps, color = TsmColors.TextDim, fontSize = 15.sp, lineHeight = 22.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Cast, null, tint = TsmColors.TextDim, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(str.tvChromecastHint, color = TsmColors.TextDim, fontSize = 13.sp)
                }
            }
            Column(Modifier.widthIn(max = 320.dp).fillMaxWidth(0.4f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it.take(40) },
                    label = { Text(str.displayManual) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                BigButton(str.displayConnect, Icons.Filled.Link, { search(address) }, Modifier.fillMaxWidth(), enabled = address.isNotBlank() && !busy)
                GhostButton(str.displayRetry, Icons.Filled.Refresh, { search() }, Modifier.fillMaxWidth(), enabled = !busy)
                GhostButton(str.exit, Icons.AutoMirrored.Filled.ExitToApp, { finish() }, Modifier.fillMaxWidth())
            }
        }
    }

    @Composable
    private fun OnMonitorScreen(f: FoundScoreboard) {
        val str = s
        Column(
            Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Filled.Tv, null, tint = TsmColors.Ball, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(8.dp))
            Text(str.displayOnMonitor, color = TsmColors.TextMain, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(f.label, color = TsmColors.TextDim, fontSize = 14.sp)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GhostButton(str.displayShowHere, Icons.Filled.Tv, { showHere.value = true; monitorChanged() })
                GhostButton(str.exit, Icons.AutoMirrored.Filled.ExitToApp, { finish() })
            }
        }
    }
}

/** Il tabellone su un monitor collegato col cavo: occupa tutto il monitor, il telefono resta libero. */
class ScoreboardPresentation(
    context: Context,
    display: Display,
    private var page: ScoreboardPage,
    private val onLost: () -> Unit,
) : Presentation(context, display) {
    private var web: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(scoreboardWebView(context, onLost).also { web = it; it.showPage(page) })
    }

    /** Collegamento nuovo sullo stesso monitor: si ricarica la pagina senza ricreare la finestra. */
    fun load(p: ScoreboardPage) {
        page = p
        web?.showPage(p)
    }

    /** Chiuso (da qui o dal sistema): la WebView si distrugge insieme al suo collegamento in diretta. */
    override fun onStop() {
        web?.release()
        web = null
        super.onStop()
    }
}

/** Pagina del tabellone: [n] cambia a ogni collegamento, così si ricarica anche allo stesso indirizzo. */
data class ScoreboardPage(val url: String, val n: Int)

/** Carica [page] se non è già quella mostrata. */
fun WebView.showPage(page: ScoreboardPage) {
    if (tag != page) {
        tag = page
        loadUrl(page.url)
    }
}

/** WebView non più usata: si distrugge (chiude anche il collegamento /events). */
fun WebView.release() {
    tag = null
    stopLoading()
    destroy()
}

/** WebView del tabellone: JavaScript acceso, fondo nero, ricarica da sola se la pagina non arriva. */
@SuppressLint("SetJavaScriptEnabled")
fun scoreboardWebView(context: Context, onLost: () -> Unit): WebView = WebView(context).apply {
    // Altezza esplicita: con WRAP_CONTENT (il default di AndroidView) la WebView calcola 1vh = 0 e i testi spariscono.
    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    // Versione di prova (Android Studio): la pagina si ispeziona da chrome://inspect sul computer.
    if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) WebView.setWebContentsDebuggingEnabled(true)
    setBackgroundColor(Color.BLACK)
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    overScrollMode = View.OVER_SCROLL_NEVER
    isVerticalScrollBarEnabled = false
    isHorizontalScrollBarEnabled = false
    keepScreenOn = true
    addJavascriptInterface(object {
        @JavascriptInterface
        fun lost() = onLost()
    }, "TSMDisplay")
    webChromeClient = object : WebChromeClient() {
        override fun onConsoleMessage(m: ConsoleMessage): Boolean {
            Log.d("Tabellone", "${m.message()} (riga ${m.lineNumber()})")
            return true
        }
    }
    webViewClient = object : WebViewClient() {
        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            val page = view.tag as? ScoreboardPage ?: return
            // se intanto la pagina è cambiata (o la WebView è stata distrutta) non si ricarica più quella vecchia
            if (request.isForMainFrame) view.postDelayed({ if (view.tag == page) view.loadUrl(page.url) }, 3_000)
        }
    }
}
TSM_EOF

# ---------------------------------------------------------------- app/src/main/java/com/tennis/scoremanager/tv/ScoreboardFinder.kt
cat > "$DEST/app/src/main/java/com/tennis/scoremanager/tv/ScoreboardFinder.kt" << 'TSM_EOF'
package com.tennis.scoremanager.tv

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import javax.net.SocketFactory

/**
 * Telefono dell'arbitro trovato: [network] è la rete da usare per raggiungerlo (null = quella normale).
 * [id] identifica il telefono (null = versione dell'app senza), [title] è la scritta del tabellone (circolo e campo).
 */
data class FoundScoreboard(
    val host: String,
    val port: Int,
    val network: Network?,
    val id: String? = null,
    val title: String = "",
) {
    val url: String get() = "http://$host:$port/?display=app"
    val label: String get() = "$host:$port"

    /**
     * Lo stesso tabellone di prima, per riprendere dopo un'interruzione senza saltare su un altro campo della
     * stessa rete: stesso telefono, oppure stesso indirizzo, oppure stessa scritta (stesso circolo e campo).
     */
    fun sameAs(prev: FoundScoreboard): Boolean =
        (id != null && id == prev.id) || label == prev.label || (title.isNotEmpty() && title == prev.title)
}

/**
 * Cerca il telefono dell'arbitro sulla rete locale, senza chiedere nulla all'utente:
 *   1. l'ultimo indirizzo che ha funzionato;
 *   2. l'annuncio "_tsm._tcp" sulla rete (NSD/mDNS);
 *   3. una scansione della rete dell'hotspot (al massimo 1024 indirizzi, porta 8080 e seguenti).
 * Ogni candidato si conferma chiedendo /state: deve rispondere il JSON del tabellone ("tsm":1).
 */
class ScoreboardFinder(context: Context) {

    private val app = context.applicationContext
    private val cm = app.getSystemService(ConnectivityManager::class.java)

    /**
     * [accept] scarta i tabelloni che non vanno bene (questo stesso telefono, un altro campo) e la ricerca continua:
     * vale anche per [lastKnown].
     */
    suspend fun find(
        lastKnown: String?,
        timeoutMs: Long = 20_000,
        accept: (FoundScoreboard) -> Boolean = { true },
    ): FoundScoreboard? = withContext(Dispatchers.IO) {
        lastKnown?.let { parseAddress(it) }?.let { (h, p) -> verify(h, p)?.takeIf(accept)?.let { return@withContext it } }
        val result = CompletableDeferred<FoundScoreboard?>()
        val job = launch {
            launch { nsd(accept)?.let { result.complete(it) } }
            for (port in listOf(TvServer.PORT, TvServer.PORT + 1, TvServer.PORT + 2)) {
                if (result.isCompleted) break
                scan(port, accept)?.let { result.complete(it) }
            }
            // scansione finita senza risultato: qualche secondo ancora per l'annuncio NSD
            delay(3_000)
            result.complete(null)
        }
        val found = withTimeoutOrNull(timeoutMs) { result.await() }
        job.cancel()
        found
    }


    /** Conferma che a [host]:[port] c'è un tabellone TSM (con la rete giusta per raggiungerlo). */
    fun verify(host: String, port: Int): FoundScoreboard? {
        val network = networkFor(host)
        val factory = network?.socketFactory ?: SocketFactory.getDefault()
        return runCatching {
            factory.createSocket().use { s ->
                s.connect(InetSocketAddress(host, port), 700)
                s.soTimeout = 1_500
                s.getOutputStream().write("GET /state HTTP/1.0\r\nHost: $host\r\n\r\n".toByteArray())
                val response = readAll(s.getInputStream(), 16_384)
                if ("\"tsm\":1" in response) {
                    val (id, title) = identity(response)
                    FoundScoreboard(host, port, network, id, title)
                } else null
            }
        }.getOrNull()
    }

    // ---------------------------------------------------------------- NSD

    private suspend fun nsd(accept: (FoundScoreboard) -> Boolean): FoundScoreboard? {
        val nsd = app.getSystemService(NsdManager::class.java) ?: return null
        val services = kotlinx.coroutines.channels.Channel<NsdServiceInfo>(8)
        val listener = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(info: NsdServiceInfo) { services.trySend(info) }
            override fun onDiscoveryStarted(type: String) {}
            override fun onDiscoveryStopped(type: String) {}
            override fun onServiceLost(info: NsdServiceInfo) {}
            override fun onStartDiscoveryFailed(type: String, error: Int) { services.close() }
            override fun onStopDiscoveryFailed(type: String, error: Int) {}
        }
        runCatching { nsd.discoverServices(TvServer.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener) }.onFailure { return null }
        try {
            for (info in services) {
                val resolved = resolve(nsd, info) ?: continue
                @Suppress("DEPRECATION")
                val host = (resolved.host as? Inet4Address)?.hostAddress ?: continue
                verify(host, resolved.port)?.takeIf(accept)?.let { return it }
            }
        } finally {
            runCatching { nsd.stopServiceDiscovery(listener) }
        }
        return null
    }

    @Suppress("DEPRECATION")
    private suspend fun resolve(nsd: NsdManager, info: NsdServiceInfo): NsdServiceInfo? {
        val done = CompletableDeferred<NsdServiceInfo?>()
        runCatching {
            nsd.resolveService(info, object : NsdManager.ResolveListener {
                override fun onResolveFailed(i: NsdServiceInfo, error: Int) { done.complete(null) }
                override fun onServiceResolved(i: NsdServiceInfo) { done.complete(i) }
            })
        }.onFailure { return null }
        return withTimeoutOrNull(5_000) { done.await() }
    }

    // ---------------------------------------------------------------- scansione

    /** Reti IPv4 locali: indirizzo di questo telefono e lunghezza del prefisso (es. 192.168.43.12/24). */
    private data class Lan(val self: Int, val prefix: Int, val iface: String)

    private fun lans(): List<Lan> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback && !it.name.startsWith("rmnet") && !it.name.startsWith("dummy") && !it.name.startsWith("tun") }
            .flatMap { nif ->
                nif.interfaceAddresses.mapNotNull { a ->
                    val ip = a.address as? Inet4Address ?: return@mapNotNull null
                    if (!ip.isSiteLocalAddress || a.networkPrefixLength !in 20..30) return@mapNotNull null
                    Lan(toInt(ip), a.networkPrefixLength.toInt(), nif.name)
                }
            }
    }.getOrDefault(emptyList())

    private suspend fun scan(port: Int, accept: (FoundScoreboard) -> Boolean): FoundScoreboard? = coroutineScope {
        val gate = Semaphore(48)
        for (lan in lans()) {
            val mask = -1 shl (32 - lan.prefix)
            val base = lan.self and mask
            val size = (1 shl (32 - lan.prefix)).coerceAtMost(1024)
            // prima il probabile router/hotspot (.1), poi il resto
            val hosts = (listOf(base + 1) + (1 until size - 1).map { base + it }).distinct().filter { it != lan.self }
            val found = CompletableDeferred<FoundScoreboard?>()
            val jobs = hosts.map { ipInt ->
                async {
                    gate.withPermit {
                        ensureActive()
                        if (!found.isCompleted) verify(fromInt(ipInt), port)?.takeIf(accept)?.let { found.complete(it) }
                    }
                }
            }
            launch { jobs.awaitAll(); found.complete(null) }
            val hit = found.await()
            jobs.forEach { it.cancel() }
            if (hit != null) {
                Log.i("ScoreboardFinder", "Trovato ${hit.label} su ${lan.iface}")
                return@coroutineScope hit
            }
        }
        null
    }

    /**
     * La rete (Network) da cui si raggiunge [host]: serve quando questo telefono è collegato all'hotspot
     * dell'altro e Android, vedendola senza internet, manderebbe il traffico sui dati mobili.
     * Se il telefono è lui stesso l'hotspot la rete non è una "Network" di Android: null va bene.
     */
    fun networkFor(host: String): Network? {
        val target = runCatching { toInt(java.net.InetAddress.getByName(host) as Inet4Address) }.getOrNull() ?: return null
        @Suppress("DEPRECATION")
        val all = cm?.allNetworks.orEmpty()
        return all.firstOrNull { n ->
            cm?.getLinkProperties(n)?.linkAddresses.orEmpty().any { la ->
                val ip = la.address as? Inet4Address ?: return@any false
                val p = la.prefixLength
                val mask = if (p == 0) 0 else -1 shl (32 - p)
                (toInt(ip) and mask) == (target and mask)
            }
        }
    }

    private fun readAll(input: java.io.InputStream, max: Int): String {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(2048)
        while (out.size() < max) {
            val n = input.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.toString("UTF-8")
    }

    private fun toInt(ip: Inet4Address): Int = ip.address.fold(0) { acc, b -> (acc shl 8) or (b.toInt() and 0xFF) }

    private fun fromInt(v: Int): String = "${v ushr 24 and 0xFF}.${v ushr 16 and 0xFF}.${v ushr 8 and 0xFF}.${v and 0xFF}"

    companion object {
        /** Identità e scritta del tabellone dalla risposta di /state (intestazioni + JSON); vuote se mancano. */
        fun identity(response: String): Pair<String?, String> {
            val head = response.substringBefore("\r\n\r\n")
            val id = head.lineSequence().firstOrNull { it.startsWith(TvServer.ID_HEADER + ":", ignoreCase = true) }
                ?.substringAfter(':')?.trim()?.takeIf { it.isNotEmpty() }
            val title = runCatching {
                Json.parseToJsonElement(response.substringAfter("\r\n\r\n")).jsonObject["title"]?.jsonPrimitive?.contentOrNull
            }.getOrNull().orEmpty()
            return id to title
        }

        /** "192.168.43.1", "192.168.43.1:8081" o "http://192.168.43.1:8080/" -> host e porta. */
        fun parseAddress(text: String): Pair<String, Int>? {
            val t = text.trim().removePrefix("http://").removePrefix("https://").substringBefore('/')
            if (t.isEmpty()) return null
            val host = t.substringBefore(':')
            val port = t.substringAfter(':', "").toIntOrNull() ?: TvServer.PORT
            return if (host.isNotEmpty() && port in 1..65535) host to port else null
        }

        /** Per i log: modello e Android, utile se la ricerca fallisce su un telefono particolare. */
        val device: String get() = "${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})"
    }
}
TSM_EOF

# ---------------------------------------------------------------- app/src/main/java/com/tennis/scoremanager/tv/TvModels.kt
cat > "$DEST/app/src/main/java/com/tennis/scoremanager/tv/TvModels.kt" << 'TSM_EOF'
package com.tennis.scoremanager.tv

import com.tennis.scoremanager.CountdownKind
import com.tennis.scoremanager.LiveMatch
import com.tennis.scoremanager.Screen
import com.tennis.scoremanager.data.Names
import com.tennis.scoremanager.data.SetupData
import com.tennis.scoremanager.model.Lang
import com.tennis.scoremanager.model.Side
import com.tennis.scoremanager.model.TiebreakKind
import com.tennis.scoremanager.ui.Strings
import kotlinx.serialization.Serializable

/** Tabellone su TV: impostazioni del telefono dell'arbitro (non della singola partita). */
@Serializable
data class TvSettings(
    val enabled: Boolean = false,
    /** Riga facoltativa in basso (torneo, circolo...); vuota = circolo e campo della pagina 1. */
    val title: String = "",
    /** Colori dei due giocatori sul tabellone (#RRGGBB). */
    val color1: String = TvColors.YELLOW,
    val color2: String = TvColors.RED,
    val showClock: Boolean = true,
    /** Cronometro dei 25" di servizio e delle pause. */
    val showTimers: Boolean = true,
    /** Punteggi dei set conclusi, es. [6-4] [3-6]. */
    val showSets: Boolean = true,
    /** Palla break, set point, match point, cambio campo... */
    val showMessages: Boolean = true,
    /** Pallina accanto a chi serve. */
    val showServe: Boolean = true,
    /** Segmenti spenti visibili, come un tabellone a LED vero. */
    val ghostSegments: Boolean = true,
)

object TvColors {
    const val YELLOW = "#FFD600"
    const val RED = "#FF3030"
    /** Tavolozza proposta nelle impostazioni (colori ben visibili su fondo nero). */
    val palette = listOf(YELLOW, RED, "#2F80FF", "#22D65A", "#22D3EE", "#FF9800", "#F5F5F5", "#FF3DCC")
}

/**
 * Quello che il tabellone deve mostrare in un istante. Viaggia in JSON verso la pagina (scoreboard.html):
 * i tempi arrivano come valore più "sta correndo", così la pagina li fa scorrere da sola tra un invio e l'altro.
 * Indici delle liste: 0 = Giocatore 1, 1 = Giocatore 2.
 */
@Serializable
data class TvSnapshot(
    /** Firma per riconoscere il server TSM durante la ricerca. */
    val tsm: Int = 1,
    val seq: Long,
    val lang: String,
    /** idle (nessuna partita), ready (in attesa del via), play, suspended, finished. */
    val phase: String,
    val title: String,
    val players: List<TvPlayer>,
    val server: Int? = null,
    /** Punti del game: "0" "15" "30" "40" "AD" o i punti del tie-break; "" = spento. */
    val points: List<String>,
    val games: List<Int>,
    val sets: List<Int>,
    val done: List<TvSet>,
    /** "", "set" o "match". */
    val tiebreak: String,
    val winner: Int? = null,
    val clockMs: Long,
    val clockRunning: Boolean,
    val countdown: TvCountdown? = null,
    val message: String? = null,
    val show: TvShow,
    val labels: TvLabels,
)

@Serializable
data class TvPlayer(val name: String, val color: String)

@Serializable
data class TvSet(val g1: Int, val g2: Int, val tb1: Int? = null, val tb2: Int? = null, val mtb: Boolean = false)

/** [shot] = i 25" tra un punto e l'altro (in rosso come sul tabellone di riferimento). */
@Serializable
data class TvCountdown(val label: String, val leftMs: Long, val shot: Boolean)

@Serializable
data class TvShow(val clock: Boolean, val timers: Boolean, val sets: Boolean, val messages: Boolean, val serve: Boolean, val ghost: Boolean)

@Serializable
data class TvLabels(
    val vs: String,
    val games: String,
    val set: String,
    val sec: String,
    val waiting: String,
    val ready: String,
    val suspended: String,
    val winner: String,
    val tiebreak: String,
    val matchTiebreak: String,
    val lost: String,
    val fullscreen: String,
    /** Titolo della pagina (document.title). */
    val page: String,
)

/** Stato dell'app da cui si ricava il tabellone. */
data class TvInput(
    val screen: Screen,
    val setup: SetupData,
    val lang: Lang,
    val firstServer: Side,
    /** Partita in corso, oppure quella appena finita mentre si guarda il riepilogo. */
    val match: LiveMatch?,
    val clockMs: Long,
    val clockRunning: Boolean,
    val countdown: CountdownKind?,
    val countdownLeftMs: Long,
    val message: String?,
    val tv: TvSettings,
    val strings: Strings,
    val seq: Long,
)

object TvSnapshots {

    fun build(i: TvInput): TvSnapshot {
        val s = i.strings
        val m = i.match
        val st = m?.state
        val names = Names(m?.record?.setup ?: i.setup, s)
        val phase = when {
            m == null || st == null -> if (i.screen == Screen.START) "ready" else "idle"
            st.isFinished -> "finished"
            m.record.suspended -> "suspended"
            m.record.startedAt == null -> "ready"
            else -> "play"
        }
        val sides = listOf(Side.P1, Side.P2)
        val points = when {
            st == null -> if (phase == "ready") listOf("0", "0") else listOf("", "")
            st.isFinished -> listOf("", "")
            else -> sides.map { st.pointLabel(it) }
        }
        // A partita finita i game del set in corso sono azzerati: si mostrano quelli dell'ultimo set. Il match tie-break
        // vale come set vinto 1-0, come sui tabelloni a LED (una cifra per i game); i punti, es. [10-8], stanno tra i set conclusi.
        val games = when {
            st == null -> listOf(0, 0)
            st.isFinished -> st.sets.lastOrNull()?.let { set -> sides.map { set.games(it) } } ?: listOf(0, 0)
            else -> sides.map { st.games(it) }
        }
        val server = when {
            phase == "finished" || phase == "idle" -> null
            st != null -> st.server.ordinal
            else -> i.firstServer.ordinal
        }
        val countdown = i.countdown?.takeIf { phase == "play" && i.tv.showTimers }?.let { k ->
            TvCountdown(
                label = when (k) {
                    CountdownKind.SHOT_CLOCK -> s.tvServe
                    CountdownKind.CHANGEOVER -> s.tvChangeover
                    CountdownKind.SET_BREAK -> s.tvSetBreak
                    CountdownKind.TIEBREAK_BREAK -> s.tvTiebreakBreak
                },
                leftMs = i.countdownLeftMs.coerceAtLeast(0),
                shot = k == CountdownKind.SHOT_CLOCK,
            )
        }
        return TvSnapshot(
            seq = i.seq,
            lang = i.lang.code,
            phase = phase,
            title = i.tv.title.trim().ifEmpty { defaultTitle(m?.record?.setup ?: i.setup, s) },
            players = listOf(TvPlayer(names.short(Side.P1), i.tv.color1), TvPlayer(names.short(Side.P2), i.tv.color2)),
            server = server,
            points = points,
            games = games,
            sets = sides.map { st?.setsWon(it) ?: 0 },
            done = st?.sets.orEmpty().map { TvSet(it.g1, it.g2, it.tb1, it.tb2, it.matchTiebreak) },
            tiebreak = when (st?.tiebreak) {
                TiebreakKind.SET -> "set"
                TiebreakKind.MATCH -> "match"
                else -> ""
            },
            winner = st?.winner?.ordinal,
            clockMs = if (phase == "idle") 0 else i.clockMs,
            clockRunning = phase == "play" && i.clockRunning,
            countdown = countdown,
            message = i.message?.takeIf { phase == "play" && i.tv.showMessages },
            show = TvShow(i.tv.showClock, i.tv.showTimers, i.tv.showSets, i.tv.showMessages, i.tv.showServe, i.tv.ghostSegments),
            labels = labels(s),
        )
    }

    /** Testi fissi della pagina; `scoreboard.html` ne ha una copia per lingua (id="texts") da usare prima del primo stato. */
    fun labels(s: Strings) = TvLabels(
        vs = s.tvVs, games = s.tvGames, set = s.tvSet, sec = s.tvSec,
        waiting = s.tvWaiting, ready = s.tvReady, suspended = s.tvSuspended, winner = s.tvWinner,
        tiebreak = s.tvTiebreak, matchTiebreak = s.tvMatchTiebreak, lost = s.tvLost, fullscreen = s.tvFullscreen,
        page = s.tvPageTitle,
    )

    /** "Circolo Tennis · Campo 3" dai dati della pagina 1 (un numero da solo diventa "Campo 3"). */
    fun defaultTitle(su: SetupData, s: Strings): String {
        val court = su.court.trim().let { if (it.isNotEmpty() && it.all(Char::isDigit)) "${s.court} $it" else it }
        return listOf(su.club.trim(), court).filter { it.isNotEmpty() }.joinToString(" · ")
    }
}
TSM_EOF

# ---------------------------------------------------------------- app/src/main/java/com/tennis/scoremanager/tv/TvServer.kt
cat > "$DEST/app/src/main/java/com/tennis/scoremanager/tv/TvServer.kt" << 'TSM_EOF'
package com.tennis.scoremanager.tv

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Piccolo server web sul telefono dell'arbitro, attivo solo con il tabellone TV acceso.
 *   /        la pagina del tabellone (assets/scoreboard.html)
 *   /events  aggiornamenti in diretta (Server-Sent Events): un JSON a ogni punto e ogni 5 secondi
 *   /state   l'ultimo JSON (serve anche alla ricerca automatica del telefono-tabellone)
 * Solo lettura: dal tabellone non si può cambiare niente, e risponde solo alla rete locale (non ai dati mobili).
 * Si annuncia sulla rete come "_tsm._tcp" e tiene agganciata la rete Wi-Fi anche se non ha internet
 * (hotspot dell'altro telefono).
 */
class TvServer(context: Context) {

    companion object {
        const val PORT = 8080
        const val SERVICE_TYPE = "_tsm._tcp"
        /** Intestazione di /state con l'identità del telefono (vedi [ScoreboardFinder]). */
        const val ID_HEADER = "X-TSM-Id"
        private const val MAX_STREAMS = 12
        /** Scrittura ferma da tanto = tabellone sparito senza chiudere: si chiude il collegamento. */
        private const val WRITE_TIMEOUT_MS = 20_000L
        /** Linux: chiude la connessione se i dati inviati restano senza conferma per troppo tempo. */
        private const val TCP_USER_TIMEOUT = 18
        private const val STOP = "\u0000stop"
        private const val TAG = "TvServer"

        /**
         * Chi può collegarsi: solo la rete locale (Wi-Fi, hotspot, USB). Il server ascolta su tutte le interfacce
         * (il telefono può essere lui l'hotspot), quindi si scartano i dati mobili, IPv6 pubblico compreso.
         * Niente 100.64/10: lo usano gli operatori mobili (CGNAT), non gli hotspot Android né iPhone.
         */
        fun isLocalPeer(a: InetAddress): Boolean {
            val b = a.address
            // IPv4 scritto in IPv6 (::ffff:192.168.43.5)
            if (a is Inet6Address && b.size == 16 && (0 until 10).all { b[it] == 0.toByte() } && b[10] == (-1).toByte() && b[11] == (-1).toByte()) {
                return isLocalPeer(InetAddress.getByAddress(b.copyOfRange(12, 16)))
            }
            return a.isLoopbackAddress || a.isLinkLocalAddress || a.isSiteLocalAddress ||
                (a is Inet6Address && (b[0].toInt() and 0xFE) == 0xFC)  // IPv6 locale (ULA, fc00::/7)
        }

        /**
         * Indirizzi IPv4 locali del telefono, prima Wi-Fi e hotspot. Esclusi i dati mobili e le VPN:
         * da lì il tabellone non si raggiunge.
         */
        fun localAddresses(): List<String> = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback && !skipInterface(it.name) }
                .sortedBy { interfaceRank(it.name) }
                .flatMap { nif ->
                    nif.inetAddresses.toList().filterIsInstance<Inet4Address>()
                        .filter { it.isSiteLocalAddress }
                        .mapNotNull { it.hostAddress }
                }
                .distinct()
        }.getOrDefault(emptyList())

        private fun skipInterface(name: String): Boolean =
            listOf("rmnet", "r_rmnet", "ccmni", "v4-", "dummy", "tun", "ppp", "ipsec", "clat").any { name.startsWith(it) }

        /** wlan0 = collegato a una rete; swlan/ap/wlan1 = hotspot di questo telefono; poi USB ed Ethernet. */
        private fun interfaceRank(name: String): Int = when {
            name == "wlan0" -> 0
            name.startsWith("swlan") || name.startsWith("ap") || name.startsWith("softap") || name.startsWith("wlan") -> 1
            name.startsWith("rndis") || name.startsWith("usb") || name.startsWith("eth") -> 2
            else -> 3
        }
    }

    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pool = Executors.newCachedThreadPool()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running
    private val _port = MutableStateFlow<Int?>(null)
    val port: StateFlow<Int?> = _port
    private val _clients = MutableStateFlow(0)
    /** Tabelloni collegati in questo momento. */
    val clients: StateFlow<Int> = _clients
    private val _addresses = MutableStateFlow<List<String>>(emptyList())
    val addresses: StateFlow<List<String>> = _addresses

    /** Un tabellone collegato a /events. */
    private class Stream(val socket: Socket) {
        val queue = LinkedBlockingQueue<String>()
        /** Da quando è ferma la scrittura in corso (0 = non sta scrivendo). */
        @Volatile var writingSince = 0L

        fun close() {
            queue.offer(STOP)
            runCatching { socket.close() }  // sblocca anche una scrittura ferma
        }
    }

    @Volatile private var latest: String = "{}"
    @Volatile private var server: ServerSocket? = null
    /** Modifiche ed elenco dei collegati sotto synchronized(streams), così il numero mostrato resta giusto. */
    private val streams = CopyOnWriteArrayList<Stream>()
    private var addressJob: Job? = null
    private var watchdogJob: Job? = null
    private var nsdListener: NsdManager.RegistrationListener? = null
    private var wifiCallback: ConnectivityManager.NetworkCallback? = null
    private val page: ByteArray by lazy { app.assets.open("scoreboard.html").use { it.readBytes() } }
    /** Identità di questo telefono: dopo un'interruzione il telefono-tabellone lo ritrova anche se l'indirizzo è cambiato. */
    private val serverId: String by lazy {
        val prefs = app.getSharedPreferences("tv_server", Context.MODE_PRIVATE)
        prefs.getString("id", null) ?: java.util.UUID.randomUUID().toString().take(8).also { prefs.edit().putString("id", it).apply() }
    }

    /** Indirizzo da mostrare e da mettere nel QR, es. http://192.168.43.1:8080/ (null = nessuna rete locale). */
    fun url(address: String? = _addresses.value.firstOrNull()): String? {
        val p = _port.value ?: return null
        return address?.let { "http://$it:$p/" }
    }

    @Synchronized
    fun start() {
        if (server != null) return
        val socket = (PORT until PORT + 10).firstNotNullOfOrNull { p ->
            runCatching { ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(p)) } }.getOrNull()
        } ?: run {
            Log.w(TAG, "Nessuna porta libera tra $PORT e ${PORT + 9}")
            return
        }
        server = socket
        _port.value = socket.localPort
        _running.value = true
        pool.execute { acceptLoop(socket) }
        registerNsd(socket.localPort)
        keepWifi()
        addressJob = scope.launch {
            while (isActive) {
                _addresses.value = localAddresses()
                delay(3_000)
            }
        }
        watchdogJob = scope.launch {
            while (isActive) {
                delay(5_000)
                val now = SystemClock.elapsedRealtime()
                streams.filter { it.writingSince != 0L && now - it.writingSince > WRITE_TIMEOUT_MS }.forEach {
                    Log.d(TAG, "tabellone fermo da ${WRITE_TIMEOUT_MS / 1000}\": chiuso")
                    it.close()
                }
            }
        }
        Log.i(TAG, "Tabellone su porta ${socket.localPort}")
    }

    @Synchronized
    fun stop() {
        val socket = server ?: return
        server = null
        runCatching { socket.close() }
        synchronized(streams) {
            streams.forEach { it.close() }
            streams.clear()
            _clients.value = 0
        }
        _running.value = false
        _port.value = null
        addressJob?.cancel()
        addressJob = null
        watchdogJob?.cancel()
        watchdogJob = null
        unregisterNsd()
        releaseWifi()
    }

    /** Nuovo stato del tabellone: va subito a tutti i tabelloni collegati. */
    fun publish(json: String) {
        latest = json
        for (st in streams) {
            if (st.queue.size > 16) st.queue.clear()  // tabellone bloccato: meglio perdere i vecchi che riempire la memoria
            st.queue.offer(json)
        }
    }

    // ---------------------------------------------------------------- HTTP

    private fun acceptLoop(socket: ServerSocket) {
        while (!socket.isClosed) {
            val client = try {
                socket.accept()
            } catch (e: IOException) {
                if (socket.isClosed) break
                // errore passeggero (es. troppi file aperti): si riprova, se no il tabellone sparirebbe per sempre
                Log.w(TAG, "accept: $e")
                runCatching { Thread.sleep(500) }
                continue
            }
            if (!isLocalPeer(client.inetAddress)) {
                Log.d(TAG, "rifiutato ${client.inetAddress}")
                runCatching { client.close() }
                continue
            }
            pool.execute { runCatching { handle(client) }.onFailure { Log.d(TAG, "richiesta interrotta: $it") } }
        }
    }

    private fun handle(sock: Socket) {
        sock.use { s ->
            s.soTimeout = 10_000
            s.tcpNoDelay = true
            val input = s.getInputStream()
            val requestLine = readLine(input) ?: return
            while (true) {
                val header = readLine(input) ?: return
                if (header.isEmpty()) break
            }
            val parts = requestLine.split(' ')
            if (parts.size < 2) return
            val method = parts[0]
            val path = parts[1].substringBefore('?')
            val out = BufferedOutputStream(s.getOutputStream())
            val head = method == "HEAD"
            if (method != "GET" && !head) {
                respond(out, "405 Method Not Allowed", "text/plain", "GET only".toByteArray(), head)
                return
            }
            when (path) {
                "/", "/index.html" -> respond(out, "200 OK", "text/html; charset=utf-8", page, head)
                "/state", "/state.json" -> respond(out, "200 OK", "application/json; charset=utf-8", latest.toByteArray(), head, "$ID_HEADER: $serverId\r\n")
                "/events" -> if (head) respond(out, "200 OK", "text/event-stream", ByteArray(0), true) else stream(s, out)
                "/favicon.ico" -> respond(out, "204 No Content", "text/plain", ByteArray(0), head)
                else -> respond(out, "404 Not Found", "text/plain", "Not found".toByteArray(), head)
            }
        }
    }

    /** Riga della richiesta HTTP (max 4 KB), senza \r\n; null = connessione chiusa. */
    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        while (sb.length < 4096) {
            val c = input.read()
            if (c < 0) return if (sb.isEmpty()) null else sb.toString()
            if (c == '\n'.code) return sb.toString().trimEnd('\r')
            sb.append(c.toChar())
        }
        return sb.toString()
    }

    private fun respond(out: OutputStream, status: String, type: String, body: ByteArray, head: Boolean, extra: String = "") {
        val headers = "HTTP/1.1 $status\r\n" +
            "Content-Type: $type\r\n" +
            "Content-Length: ${body.size}\r\n" +
            "Cache-Control: no-store\r\n" +
            "Access-Control-Allow-Origin: *\r\n" +
            extra +
            "Connection: close\r\n\r\n"
        out.write(headers.toByteArray(Charsets.ISO_8859_1))
        if (!head) out.write(body)
        out.flush()
    }

    /**
     * Flusso SSE: lo stato attuale subito, poi ogni aggiornamento; un commento ogni 15" se tutto tace.
     * Un tabellone sparito senza chiudere (Wi-Fi caduto) non tiene il posto per sempre: buffer piccolo e
     * TCP_USER_TIMEOUT fanno fallire presto la scrittura, il controllo chiude chi resta fermo, e a posti esauriti
     * si libera il collegamento più vecchio invece di rifiutare il nuovo.
     */
    private fun stream(s: Socket, out: OutputStream) {
        val st = Stream(s)
        synchronized(streams) {
            while (streams.size >= MAX_STREAMS) streams.removeAt(0).close()
            streams += st
            _clients.value = streams.size
        }
        try {
            s.soTimeout = 0
            s.sendBufferSize = 16 * 1024
            runCatching {
                ParcelFileDescriptor.fromSocket(s).use { Os.setsockoptInt(it.fileDescriptor, OsConstants.IPPROTO_TCP, TCP_USER_TIMEOUT, 30_000) }
            }
            send(
                st, out,
                "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: text/event-stream; charset=utf-8\r\n" +
                    "Cache-Control: no-store\r\n" +
                    "Access-Control-Allow-Origin: *\r\n" +
                    "Connection: keep-alive\r\n\r\n" +
                    "retry: 2000\n\n" +
                    "data: $latest\n\n",
            )
            while (server != null) {
                val msg = st.queue.poll(15, TimeUnit.SECONDS)
                if (msg == STOP) break
                send(st, out, if (msg == null) ": ping\n\n" else "data: $msg\n\n")
            }
        } catch (e: IOException) {
            // tabellone chiuso o fuori portata: se ne va da solo
        } finally {
            synchronized(streams) {
                streams -= st
                _clients.value = streams.size
            }
        }
    }

    private fun send(st: Stream, out: OutputStream, text: String) {
        st.writingSince = SystemClock.elapsedRealtime()
        try {
            out.write(text.toByteArray(Charsets.UTF_8))
            out.flush()
        } finally {
            st.writingSince = 0L
        }
    }

    // ---------------------------------------------------------------- rete

    private fun registerNsd(port: Int) {
        val nsd = app.getSystemService(NsdManager::class.java) ?: return
        val info = NsdServiceInfo().apply {
            serviceName = "Tennis Score Manager"  // chi cerca guarda il tipo, non il nome
            serviceType = SERVICE_TYPE
            setPort(port)
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                Log.i(TAG, "Annunciato come ${info.serviceName}")
            }

            override fun onRegistrationFailed(info: NsdServiceInfo, error: Int) {
                Log.w(TAG, "Annuncio fallito: $error")
            }
            override fun onServiceUnregistered(info: NsdServiceInfo) {}
            override fun onUnregistrationFailed(info: NsdServiceInfo, error: Int) {}
        }
        runCatching { nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener) }
            .onSuccess { nsdListener = listener }
            .onFailure { Log.w(TAG, "NSD", it) }
    }

    private fun unregisterNsd() {
        val l = nsdListener ?: return
        nsdListener = null
        runCatching { app.getSystemService(NsdManager::class.java)?.unregisterService(l) }
    }

    /**
     * Collegato all'hotspot di un altro telefono (senza internet) Android potrebbe lasciare la rete Wi-Fi:
     * una richiesta "Wi-Fi anche senza internet" la tiene agganciata finché il tabellone è acceso.
     */
    private fun keepWifi() {
        val cm = app.getSystemService(ConnectivityManager::class.java) ?: return
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                _addresses.value = localAddresses()
            }

            override fun onLost(network: Network) {
                _addresses.value = localAddresses()
            }
        }
        runCatching { cm.requestNetwork(request, cb) }
            .onSuccess { wifiCallback = cb }
            .onFailure { Log.w(TAG, "requestNetwork", it) }
    }

    private fun releaseWifi() {
        val cb = wifiCallback ?: return
        wifiCallback = null
        runCatching { app.getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(cb) }
    }
}
TSM_EOF

# ---------------------------------------------------------------- app/src/main/java/com/tennis/scoremanager/ui/BandSettingsPanel.kt
cat > "$DEST/app/src/main/java/com/tennis/scoremanager/ui/BandSettingsPanel.kt" << 'TSM_EOF'
package com.tennis.scoremanager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tennis.scoremanager.MatchController
import com.tennis.scoremanager.ble.BandSettings
import com.tennis.scoremanager.ble.BatteryModel
import com.tennis.scoremanager.ble.LinkState
import com.tennis.scoremanager.model.Side
import java.util.Locale

/**
 * Impostazioni di un braccialetto: si scrivono nel braccialetto (che le salva) e la stima dell'autonomia
 * si aggiorna mentre si muovono i cursori, prima ancora di rilasciarli.
 */
@Composable
fun BandSettingsPanel(c: MatchController, side: Side) {
    val s = LocalStrings.current
    val bands by c.ble.bands.collectAsState()
    val batteries by c.bandBattery.collectAsState()
    val baseMa by c.bandBaseMa.collectAsState()
    val band = bands[side] ?: return
    if (band.state != LinkState.READY) {
        Text(s.bandSettingsNeedLink, color = TsmColors.TextDim, fontSize = 13.sp)
        return
    }
    val settings = band.settings ?: run {
        Text(s.bandFirmwareOld, color = TsmColors.Orange, fontSize = 13.sp)
        return
    }
    // Bozza locale: i cursori la cambiano subito, il braccialetto riceve il valore al rilascio.
    var draft by remember(band.address, settings) { mutableStateOf(settings) }
    var confirmOff by remember(band.address) { mutableStateOf(false) }
    val locale = c.options.collectAsState().value.lang.locale
    fun commit(v: BandSettings) {
        draft = v
        if (v != settings) c.writeBandSettings(side, v)
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        NameField(settings.name) { commit(draft.copy(name = it)) }

        SliderRow(Icons.Filled.BrightnessMedium, s.brightness, "${draft.brightness}%", draft.brightness, 5..100, 5,
            onChange = { draft = draft.copy(brightness = it) }, onDone = { commit(draft) })

        Label(Icons.Filled.Timer, s.scoreTime)
        val times = listOf(0, 2, 3, 5, 8)
        Segmented(
            times.map { SegOption(if (it == 0) s.off else "$it s") },
            selected = times.indexOf(draft.pointSeconds).takeIf { it >= 0 } ?: times.indexOfFirst { it >= draft.pointSeconds }.coerceAtLeast(0),
            onSelect = { commit(draft.copy(pointSeconds = times[it])) },
        )
        Text(s.scoreTimeHint, color = TsmColors.TextDim, fontSize = 12.sp)

        SliderRow(Icons.AutoMirrored.Filled.VolumeUp, s.beeperVolume, if (draft.volume == 0) s.mute else "${draft.volume}%",
            draft.volume, 0..100, 10, onChange = { draft = draft.copy(volume = it) }, onDone = { commit(draft) })

        SwitchRow(Icons.Filled.ScreenRotation, s.flipDisplay, s.flipDisplayHint, draft.flip) { commit(draft.copy(flip = it)) }

        Label(Icons.Filled.PowerSettingsNew, s.autoOff)
        Picker(s.pairTimeout, duration(draft.pairTimeoutS), listOf(15, 30, 60, 120, 180, 300).map { it to duration(it) }) {
            commit(draft.copy(pairTimeoutS = it))
        }
        Picker(s.lostTimeout, duration(draft.lostTimeoutS), listOf(60, 120, 180, 300, 600).map { it to duration(it) }) {
            commit(draft.copy(lostTimeoutS = it))
        }
        Picker(s.idleTimeout, duration(draft.idleTimeoutMin * 60), listOf(10, 15, 30, 45, 60).map { it to duration(it * 60) }) {
            commit(draft.copy(idleTimeoutMin = it))
        }

        Estimate(BatteryModel.estimate(draft, baseMa[band.address]), batteries[side]?.takeIf { !it.charging }?.percent ?: band.battery, locale)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PanelButton(s.identify, Icons.Filled.FlashOn, Modifier.weight(1f)) { c.identifyBand(side) }
            // Anche a partita in corso: spegnere per sbaglio il braccialetto di un giocatore va confermato.
            PanelButton(s.powerOff, Icons.Filled.PowerSettingsNew, Modifier.weight(1f), danger = true) { confirmOff = true }
        }
        val other = bands[side.other]
        PanelButton(s.copyToOther, Icons.Filled.ContentCopy, Modifier.fillMaxWidth(),
            enabled = other?.state == LinkState.READY && other.settings != null) { c.copyBandSettings(side) }
        if (settings.firmware.isNotEmpty()) Text(s.bandFirmware(settings.firmware), color = TsmColors.TextDim, fontSize = 11.sp)
    }
    if (confirmOff) {
        ConfirmDialog(
            icon = Icons.Filled.PowerSettingsNew,
            title = s.bandPowerOffTitle,
            text = s.bandPowerOffText(settings.name.ifBlank { band.name }),
            confirm = s.powerOff,
            danger = true,
            onConfirm = {
                confirmOff = false
                c.powerOffBand(side)
            },
            onDismiss = { confirmOff = false },
        )
    }
}

/** "30 s", "1 min", "1 min 30 s". */
private fun duration(seconds: Int): String = when {
    seconds < 60 -> "$seconds s"
    seconds % 60 == 0 -> "${seconds / 60} min"
    else -> "${seconds / 60} min ${seconds % 60} s"
}

/** Milliampere con la virgola o il punto della lingua dell'app (non del telefono). */
private fun ma(v: Double, locale: Locale): String = String.format(locale, if (v >= 10) "%.0f" else "%.1f", v)

@Composable
private fun Estimate(est: com.tennis.scoremanager.ble.PowerEstimate, percent: Int?, locale: Locale) {
    val s = LocalStrings.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TsmColors.Ball.copy(alpha = 0.10f)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.BatteryStd, null, tint = TsmColors.Ball, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(s.estimateFull(BatteryModel.formatHours(est.hoursFull)), color = TsmColors.TextMain, fontWeight = FontWeight.Bold)
        }
        if (percent != null) Text(s.estimateNow(BatteryModel.formatHours(est.hoursAt(percent)), percent), color = TsmColors.Ball, fontWeight = FontWeight.SemiBold)
        Text(s.estimateBreakdown(ma(est.totalMa, locale), ma(est.baseMa, locale), ma(est.displayMa, locale), ma(est.soundMa, locale)), color = TsmColors.TextDim, fontSize = 12.sp)
        Text(if (est.measured) s.estimateMeasured else s.estimateTheory, color = TsmColors.TextDim, fontSize = 12.sp)
    }
}

@Composable
private fun NameField(current: String, onSave: (String) -> Unit) {
    val s = LocalStrings.current
    val focus = LocalFocusManager.current
    var text by remember(current) { mutableStateOf(current) }
    val clean = BandSettings.cleanName(text)
    fun save() {
        focus.clearFocus()
        if (clean.isNotEmpty() && clean != current) onSave(clean)
    }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it.take(BandSettings.NAME_MAX) },
        label = { Text(s.bandName) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { save() }),
        trailingIcon = {
            if (clean.isNotEmpty() && clean != current) {
                IconButton(onClick = { save() }) { Icon(Icons.Filled.Check, s.save, tint = TsmColors.Ball) }
            }
        },
    )
}

@Composable
private fun Label(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = TsmColors.TextDim, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = TsmColors.TextMain, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SliderRow(
    icon: ImageVector,
    title: String,
    valueText: String,
    value: Int,
    range: IntRange,
    step: Int,
    onChange: (Int) -> Unit,
    onDone: () -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = TsmColors.TextDim, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(title, color = TsmColors.TextMain, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(valueText, color = TsmColors.Ball, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value.toFloat(),
            // a scatti di [step] senza i puntini delle tacche
            onValueChange = { onChange((Math.round(it / step) * step).coerceIn(range)) },
            onValueChangeFinished = onDone,
            valueRange = range.first.toFloat()..range.last.toFloat(),
            colors = SliderDefaults.colors(thumbColor = TsmColors.Ball, activeTrackColor = TsmColors.Ball),
            modifier = Modifier.height(36.dp),
        )
    }
}

@Composable
private fun PanelButton(text: String, icon: ImageVector, modifier: Modifier, enabled: Boolean = true, danger: Boolean = false, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(46.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (danger) TsmColors.Danger.copy(alpha = 0.18f) else TsmColors.SurfaceHigh,
            contentColor = if (danger) TsmColors.Danger else TsmColors.TextMain,
        ),
    ) {
        Icon(icon, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
TSM_EOF

# ---------------------------------------------------------------- app/src/main/java/com/tennis/scoremanager/ui/Components.kt
cat > "$DEST/app/src/main/java/com/tennis/scoremanager/ui/Components.kt" << 'TSM_EOF'
package com.tennis.scoremanager.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay
import com.tennis.scoremanager.MatchController
import com.tennis.scoremanager.Screen
import com.tennis.scoremanager.ui.screens.MatchScreen
import com.tennis.scoremanager.ui.screens.OptionsScreen
import com.tennis.scoremanager.ui.screens.SetupScreen
import com.tennis.scoremanager.ui.screens.StartScreen
import com.tennis.scoremanager.ui.screens.SummaryScreen

@Composable
fun AppRoot(c: MatchController) {
    val screen by c.screen.collectAsState()
    Surface(
        color = TsmColors.Background,
        modifier = Modifier.fillMaxSize().pointerInput(c) {
            // Il secondo tocco di un doppio tocco finirebbe sul pulsante che sta nello stesso punto della schermata
            // nuova ("Inizia partita" due volte = "Sospendi", "Avanti" due volte salta la pagina 2). Si decide al
            // momento del tocco, leggendo la schermata dal controller: un overlay comparirebbe solo al fotogramma
            // dopo, troppo tardi su un telefono lento.
            var upAt = 0L
            var screenAtUp: Screen? = null
            var blocking = false
            awaitPointerEventScope {
                while (true) {
                    val e = awaitPointerEvent(PointerEventPass.Initial)
                    val now = e.changes.firstOrNull()?.uptimeMillis ?: continue
                    if (e.type == PointerEventType.Press) {
                        blocking = now - upAt < TAP_GUARD_MS && c.screen.value != screenAtUp
                    }
                    if (blocking) e.changes.forEach { it.consume() }
                    if (e.type == PointerEventType.Release) {
                        // Nel passaggio Initial il clic non è ancora avvenuto: questa è la schermata su cui si è toccato.
                        upAt = now
                        screenAtUp = c.screen.value
                    }
                }
            }
        },
    ) {
        AnimatedContent(targetState = screen, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "screen") { s ->
            when (s) {
                Screen.SETUP -> SetupScreen(c)
                Screen.OPTIONS -> OptionsScreen(c)
                Screen.START -> StartScreen(c)
                Screen.MATCH -> MatchScreen(c)
                Screen.SUMMARY -> SummaryScreen(c)
            }
        }
    }
}

/** Quanto restano sordi ai tocchi una schermata o un popup appena aperti. */
const val TAP_GUARD_MS = 450L

/**
 * Diventa true [TAP_GUARD_MS] dopo l'apertura: i pulsanti di un popup appena comparso ignorano il secondo
 * tocco di un doppio tocco fatto sotto (es. il punto della partita e poi "Annulla ultimo punto").
 */
@Composable
fun rememberArmed(): Boolean {
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(TAP_GUARD_MS)
        armed = true
    }
    return armed
}

/** Conferma di un'azione che non si può annullare (cancellare, spegnere, chiudere). */
@Composable
fun ConfirmDialog(
    icon: ImageVector,
    title: String,
    text: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    danger: Boolean = false,
) {
    val s = LocalStrings.current
    val armed = rememberArmed()
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(icon, null, tint = if (danger) TsmColors.Danger else TsmColors.Orange) },
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            Button(
                onClick = { if (armed) onConfirm() },
                colors = if (danger) ButtonDefaults.buttonColors(containerColor = TsmColors.Danger, contentColor = TsmColors.TextMain)
                else ButtonDefaults.buttonColors(),
            ) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.cancel) } },
    )
}

/** Schermata standard: intestazione, contenuto scorrevole, barra pulsanti in basso. */
@Composable
fun ScreenScaffold(
    title: String,
    subtitle: String?,
    icon: ImageVector,
    bottomBar: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(TsmColors.Ball),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = TsmColors.OnBall) }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.headlineMedium, color = TsmColors.TextMain)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TsmColors.TextDim)
            }
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            content()
            Spacer(Modifier.height(8.dp))
        }
        Row(
            Modifier.fillMaxWidth().background(TsmColors.Surface).padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = bottomBar,
        )
    }
}

@Composable
fun SectionCard(title: String, icon: ImageVector, accent: Color = TsmColors.Ball, content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = TsmColors.Surface),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, color = TsmColors.TextMain)
            }
            content()
        }
    }
}

data class SegOption(val label: String, val icon: ImageVector? = null, val color: Color = TsmColors.Ball, val onColor: Color = TsmColors.OnBall)

/** Selettore a segmenti (uno solo attivo). Con [columns] minore del numero di opzioni va a capo in più righe. */
@Composable
fun Segmented(
    options: List<SegOption>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, columns: Int = options.size,
    enabled: Boolean = true,
) {
    Column(
        modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.4f).clip(RoundedCornerShape(14.dp))
            .border(1.dp, TsmColors.Outline, RoundedCornerShape(14.dp)),
    ) {
        options.chunked(columns.coerceAtLeast(1)).forEachIndexed { r, row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEachIndexed { c, o ->
                    val i = r * columns + c
                    val sel = i == selected
                    Row(
                        Modifier.weight(1f)
                            .background(if (sel) o.color else Color.Transparent)
                            .clickable(enabled = enabled, role = Role.RadioButton) { onSelect(i) }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (o.icon != null) {
                            Icon(o.icon, null, tint = if (sel) o.onColor else TsmColors.TextDim, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            o.label,
                            color = if (sel) o.onColor else TsmColors.TextMain,
                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 14.sp,
                        )
                    }
                }
                // Ultima riga incompleta: celle vuote per tenere la stessa larghezza.
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
fun SwitchRow(icon: ImageVector, title: String, hint: String?, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.4f).clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled) { onChange(!checked) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = TsmColors.TextDim)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = TsmColors.TextMain, style = MaterialTheme.typography.titleMedium)
            if (hint != null) Text(hint, color = TsmColors.TextDim, style = MaterialTheme.typography.bodyMedium)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(checkedTrackColor = TsmColors.Ball, checkedThumbColor = TsmColors.OnBall),
        )
    }
}

@Composable
fun BigButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = TsmColors.Ball,
    onColor: Color = TsmColors.OnBall,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(54.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = onColor),
    ) {
        Icon(icon, null)
        Spacer(Modifier.width(8.dp))
        Text(
            text, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center, lineHeight = 17.sp,
        )
    }
}

@Composable
fun GhostButton(text: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(54.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Icon(icon, null, tint = TsmColors.TextMain)
        Spacer(Modifier.width(8.dp))
        Text(text, color = TsmColors.TextMain, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Riga stato con pallino verde/rosso e un pulsante per sistemare. */
@Composable
fun RequirementRow(icon: ImageVector, label: String, ok: Boolean, action: String, onAction: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(icon, null, tint = if (ok) TsmColors.Ok else TsmColors.Danger)
        Spacer(Modifier.width(10.dp))
        Text(label, color = TsmColors.TextMain, modifier = Modifier.weight(1f))
        if (ok) {
            Text("✓", color = TsmColors.Ok, fontWeight = FontWeight.Black, fontSize = 20.sp)
        } else {
            Button(
                onClick = onAction,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TsmColors.Orange, contentColor = TsmColors.OnOrange),
            ) { Text(action) }
        }
    }
}

@Composable
fun Pill(text: String, color: Color, onColor: Color, icon: ImageVector? = null, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(color)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = onColor, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, color = onColor, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

/** Campo a tendina semplice: etichetta, valore attuale e voci del menu (chiave, testo). */
@Composable
fun <T> Picker(label: String, value: String, options: List<Pair<T, String>>, enabled: Boolean = true, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.4f)) {
        Text(label, color = TsmColors.TextDim, fontSize = 13.sp)
        Spacer(Modifier.height(4.dp))
        Box {
            OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth(), enabled = enabled, shape = RoundedCornerShape(12.dp)) {
                Text(value, color = TsmColors.TextMain, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(Icons.Filled.ArrowDropDown, null, tint = TsmColors.TextDim)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                for ((key, text) in options) {
                    DropdownMenuItem(text = { Text(text) }, onClick = {
                        open = false
                        onSelect(key)
                    })
                }
            }
        }
    }
}
TSM_EOF

# ---------------------------------------------------------------- app/src/main/java/com/tennis/scoremanager/ui/Strings.kt
cat > "$DEST/app/src/main/java/com/tennis/scoremanager/ui/Strings.kt" << 'TSM_EOF'
package com.tennis.scoremanager.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.tennis.scoremanager.ble.BandProtocol
import com.tennis.scoremanager.model.Lang

/**
 * Testi dell'app, uno per lingua (vedi [stringsFor]). Le etichette dei cronometri restano in inglese come sul
 * tabellone ATP. I testi per i braccialetti vanno in maiuscolo e senza accenti (il font è ASCII).
 */
interface Strings {
    val appName: String get() = "Tennis Score Manager"

    /** Sigla del giocatore/squadra 1 o 2 ("G1", "P1"...), usata dove non c'è posto per il nome. */
    val playerTag: (Int) -> String

    /** Data lunga per riepilogo e partite salvate (SimpleDateFormat). */
    val datePattern: String

    // Pagina 1
    val setupTitle: String
    val setupSubtitle: String
    val clubSection: String
    val clubName: String
    val courtNumber: String
    val singles: String
    val doubles: String
    val doublesHint: String
    val player1: String
    val player2: String
    val playerName: String
    val clearFields: String
    val next: String
    val back: String

    // Pagina 2
    val optionsTitle: String
    val modeSection: String
    val modeReferee: String
    val modeBands: String
    val modeRefereeHint: String
    val modeBandsHint: String
    val requirements: String
    val bluetooth: String
    val location: String
    val locationServices: String
    val enable: String
    val allow: String
    val ok: String
    val bandFor: (String) -> String
    val noBand: String
    val bandConnected: String
    val bandConnecting: String
    val bandIdle: String
    val bandOff: String
    val battery: String
    val bandCharging: (Int) -> String
    val bandChargeFull: String
    val autoSearch: String
    val autoSearchOff: String
    val identify: String
    val swapBands: String
    val bandsOffAtEnd: String
    val bandsOffAtEndHint: String
    val bandNotReady: String

    // Impostazioni del braccialetto
    val bandSettings: String
    val settingsShort: String
    val bandSettingsNeedLink: String
    val bandFirmwareOld: String
    val bandFirmware: (String) -> String
    val bandName: String
    val brightness: String
    val scoreTime: String
    val scoreTimeHint: String
    val beeperVolume: String
    val mute: String
    val off: String
    val flipDisplay: String
    val flipDisplayHint: String
    val autoOff: String
    val pairTimeout: String
    val lostTimeout: String
    val idleTimeout: String
    val estimateFull: (String) -> String
    val estimateNow: (String, Int) -> String
    val estimateBreakdown: (String, String, String, String) -> String
    val estimateMeasured: String
    val estimateTheory: String
    val copyToOther: String
    val powerOff: String
    /** Conferma di "Spegni" dal pannello del braccialetto (nome del braccialetto). */
    val bandPowerOffTitle: String
    val bandPowerOffText: (String) -> String
    val languageSection: String
    val languageHint: String
    val audioSection: String
    val voiceCalls: String
    val voiceFiles: (Int, Int) -> String
    val voiceFilesHint: String
    val generateVoice: String
    val generating: (Int, Int) -> String
    /** "Genera file" non arrivato in fondo: restano i file di prima. */
    val voiceGenerationFailed: String
    val importVoiceZip: String
    val voiceImportFailed: String
    val deleteCustomVoice: String
    val deleteCustomConfirmTitle: String
    val deleteCustomConfirmText: (Int) -> String
    val ttsEngine: String
    val engineDefault: String
    val ttsVoice: String
    val voiceAuto: String
    val voiceName: (String) -> String
    val online: String
    val offline: String
    val voiceFilesMode: String
    val voiceFilesModeHint: String
    val customRecordings: (Int, Int) -> String
    /** Intestazione di LEGGIMI.txt nella cartella voce (lingue, formati) e titolo della colonna delle chiavi. */
    val voiceReadme: (String, String) -> String
    val voiceReadmeKey: String
    val testVoice: String
    val stopVoiceTest: String
    val ttsMissing: String
    val installVoice: String
    /** Il motore di sintesi vocale non parte: installare una voce non serve. */
    val ttsEngineError: String
    val openTtsSettings: String
    val formatSection: String
    val formatBestOfThree: String
    val formatBestOfThreeHint: String
    val formatMatchTiebreak: String
    val formatMatchTiebreakHint: String
    val noAd: String
    val noAdHint: String
    /** No-Ad nella riga del formato (riepilogo, resoconto, immagine). */
    val noAdShort: String get() = "No-Ad"
    val coinToss: String
    val tossCoin: String
    val tossWinner: (String) -> String
    val tossHint: String
    val serving: String
    val courtSides: String
    val umpireView: String
    val swapSides: String
    val firstServerOf: (String) -> String
    val left: String
    val right: String
    val net: String
    val umpireChair: String

    val locationDialogTitle: String
    val locationDialogText: String
    val continueWithout: String
    val bandsRequiredTitle: String
    val bandsRequiredText: String
    val bandsMissingTitle: String
    /** Giocatori senza braccialetto (uno o due: singolare o plurale). */
    val bandsMissingText: (List<String>) -> String
    val continueAnyway: String
    val cancel: String

    // Pagina 3
    val startMatch: String
    val startHint: String
    val startHintBands: String
    val startButton: String
    val resumeSaved: String
    val noSavedMatches: String
    val savedMatchesTitle: String
    val delete: String
    /** Conferma prima di eliminare una partita sospesa ("Rossi vs Bianchi"). */
    val deleteSavedTitle: String
    val deleteSavedText: (String) -> String
    val vs: String

    // Partita
    val matchTime: String
    val shotClock: String get() = "Shot Clock"
    val changeoverTime: String get() = "Changeover Time"
    val setBreakTime: String get() = "Set Break Time"
    val tiebreakTime: String get() = "Tie-Break Time"
    val setsHeader: String
    val gamesHeader: String
    val onServe: String get() = "On Serve"
    val undoPoint: String
    val suspend: String
    val resume: String
    val audioOn: String
    val audioOff: String
    val newMatch: String
    val suspendedOverlay: String
    val newMatchConfirmTitle: String
    val newMatchConfirmText: String
    val endDialogTitle: String
    /** Vincitore, punteggio, doppio (il verbo va al plurale: "Vincono Rossi e Bianchi"). */
    val endDialogText: (String, String, Boolean) -> String
    val matchConcluded: String
    val undoLastPoint: String
    val serveOrderTitle: (Int) -> String
    val whoServesFirst: (String) -> String
    val confirm: String
    val backDisabled: String
    val exit: String
    val exitConfirmTitle: String
    val exitConfirmText: String
    val exitConfirmBands: String

    // Messaggi del riquadro arancione
    val msgChangeEnds: String
    val msgTiebreak: String
    val msgMatchTiebreak: String
    val msgSetWon: (String) -> String
    val msgSetPoint: String
    val msgMatchPoint: String
    val msgBreakPoint: String
    val msgDecidingPoint: String
    val msgPointUndone: String
    val msgSuspended: String
    val msgResumed: String
    val msgBandConnected: (String) -> String
    val msgBandLost: (String) -> String
    val msgBandOff: (String, Int?) -> String
    val msgBandBatteryLow: (String, Int) -> String
    val bandBatteryLow: String
    val autonomy: (String) -> String
    val bandsBattery: String

    // Notifica mentre la partita è in corso
    val notifChannel: String
    val notifText: (bands: Boolean, tv: Boolean) -> String
    /** Nessuna partita in corso (riepilogo, nuova partita) ma tabellone TV acceso. */
    val notifTvOnly: String

    // Braccialetti (solo ASCII, poche lettere)
    val bandPaired: String
    val bandPlay: String
    val bandChangeEnds: String
    val bandTiebreak: String
    val bandSet: String
    val bandSuspended: String
    val bandGameSetMatch: String
    val bandMatchOver: String
    val bandAppClosed: String
    val bandOffFromApp: String

    // Riepilogo
    val summaryTitle: String
    val winner: String
    val duration: String
    val startTime: String
    val endTime: String
    val date: String
    val club: String
    val court: String
    val place: String
    val placeUnavailable: String
    val format: String
    val pointsWon: String
    val gamesWon: String
    val result: String
    val saveHistory: String
    /** "Nuova partita" o "Esci" dal riepilogo senza averlo salvato né condiviso. */
    val summaryLeaveTitle: String
    val summaryLeaveText: String
    val share: String
    val saveDialogTitle: String
    val fileName: String
    val folder: String
    val chooseFolder: String
    val defaultFolder: String
    val formatReport: String
    val formatData: String
    val formatImage: String
    val save: String
    val savedTo: (String) -> String
    val saveError: String
    val shareSubject: String
    val playerDefault: (Int) -> String
    val teamJoiner: String
    val generatedWith: String

    // Tabellone TV: testi della pagina (maiuscolo, come un tabellone a LED)
    val tvVs: String get() = "VS"
    val tvGames: String get() = "GAMES"
    val tvSet: String get() = "SET"
    val tvSec: String get() = "SEC"
    val tvServe: String
    val tvChangeover: String
    val tvSetBreak: String
    val tvTiebreakBreak: String
    val tvWaiting: String
    val tvReady: String
    val tvSuspended: String
    val tvWinner: String
    val tvTiebreak: String get() = "TIE-BREAK"
    val tvMatchTiebreak: String get() = "MATCH TIE-BREAK"
    val tvLost: String
    val tvFullscreen: String
    /** Titolo della pagina (scheda del browser, trasmissione dello schermo). */
    val tvPageTitle: String

    // Tabellone TV: impostazioni sul telefono dell'arbitro
    val tvSection: String
    val tvEnable: String
    val tvEnableHint: String
    val tvAddress: String
    val tvNoNetwork: String
    val tvScreens: (Int) -> String
    val tvQrHint: String
    val tvLook: String
    val tvTitle: String
    val tvTitleHint: (String) -> String
    val tvColorOf: (String) -> String
    val tvShowClock: String
    val tvShowTimers: String
    val tvShowSets: String
    val tvShowMessages: String
    val tvShowServe: String
    val tvGhost: String
    val tvPreview: String
    val tvChromecastHint: String

    // Telefono usato come tabellone
    val displayMode: String
    val displayModeHint: String
    val displaySearching: String
    val displaySteps: String
    val displayManual: String
    val displayConnect: String
    val displayNotFound: String
    val displayOnMonitor: String
    val displayShowHere: String
    val displayBackAgain: String
    val displayRetry: String
}

object ItStrings : Strings {
    override val playerTag: (Int) -> String = { "G$it" }
    override val datePattern = "EEEE d MMMM yyyy"

    override val setupTitle = "Nuova partita"
    override val setupSubtitle = "Configurazione facoltativa: puoi lasciare tutto vuoto e andare avanti."
    override val clubSection = "Circolo e campo"
    override val clubName = "Nome circolo tennis"
    override val courtNumber = "Numero campo"
    override val singles = "Singolare"
    override val doubles = "Doppio"
    override val doublesHint = "Nel doppio inserisci due nomi per squadra: il conteggio segue le regole ITF del doppio."
    override val player1 = "Giocatore 1"
    override val player2 = "Giocatore 2"
    override val playerName = "Nome"
    override val clearFields = "Svuota campi"
    override val next = "Avanti"
    override val back = "Indietro"

    override val optionsTitle = "Modalità e regole"
    override val modeSection = "Modalità di gioco"
    override val modeReferee = "Arbitro"
    override val modeBands = "Braccialetti"
    override val modeRefereeHint = "Il punteggio si assegna dal telefono, come il giudice di sedia."
    override val modeBandsHint = "Ogni giocatore assegna il punto con KEY1 del proprio M5StickS3."
    override val requirements = "Requisiti"
    override val bluetooth = "Bluetooth"
    override val location = "Permesso posizione"
    override val locationServices = "Posizione attiva"
    override val enable = "Attiva"
    override val allow = "Consenti"
    override val ok = "OK"
    override val bandFor: (String) -> String = { "Braccialetto di $it" }
    override val noBand = "Nessuno"
    override val bandConnected = "Connesso"
    override val bandConnecting = "Connessione…"
    override val bandIdle = "Non connesso"
    override val bandOff = "Spento"
    override val battery = "Batteria"
    override val bandCharging: (Int) -> String = { "In carica $it%" }
    override val bandChargeFull = "Carica completa"
    override val autoSearch = "Ricerca automatica: accendi i braccialetti (tasto laterale), si associano da soli."
    override val autoSearchOff = "La ricerca parte quando i requisiti qui sopra sono a posto."
    override val identify = "Identifica"
    override val swapBands = "Scambia G1 ↔ G2"
    override val bandsOffAtEnd = "Spegni i braccialetti a fine partita e all'uscita"
    override val bandsOffAtEndHint = "Si riaccendono con un clic sul tasto laterale."
    override val bandNotReady = "Braccialetto non collegato"

    override val bandSettings = "Impostazioni braccialetto"
    override val settingsShort = "Impostazioni"
    override val bandSettingsNeedLink = "Collega il braccialetto per vedere e cambiare le impostazioni."
    override val bandFirmwareOld = "Il firmware di questo braccialetto non ha le impostazioni: carica TSM_Band.ino 2.0."
    override val bandFirmware: (String) -> String = { "Firmware $it" }
    override val bandName = "Nome"
    override val brightness = "Luminosità display"
    override val scoreTime = "Punteggio visibile dopo ogni punto"
    override val scoreTimeHint = "Il riepilogo di fine game resta 2 secondi in più."
    override val beeperVolume = "Volume cicalino"
    override val mute = "Muto"
    override val off = "No"
    override val flipDisplay = "Display capovolto"
    override val flipDisplayHint = "Per portare il braccialetto sull'altro polso."
    override val autoOff = "Spegnimento automatico"
    override val pairTimeout = "All'accensione, se nessun telefono si collega"
    override val lostTimeout = "Se perde il collegamento col telefono"
    override val idleTimeout = "Se resta collegato ma inattivo"
    override val estimateFull: (String) -> String = { "Autonomia stimata $it da carica piena" }
    override val estimateNow: (String, Int) -> String = { h, p -> "$h con la carica attuale ($p%)" }
    override val estimateBreakdown: (String, String, String, String) -> String = { tot, base, dsp, snd ->
        "Consumo medio $tot mA: scheda e Bluetooth $base · display $dsp · cicalino $snd"
    }
    override val estimateMeasured = "Base misurata su questo braccialetto durante l'uso."
    override val estimateTheory = "Stima teorica: dopo 20 minuti di uso si corregge col consumo misurato."
    override val copyToOther = "Copia sull'altro braccialetto"
    override val powerOff = "Spegni"
    override val bandPowerOffTitle = "Spegnere il braccialetto?"
    override val bandPowerOffText: (String) -> String = { "$it si spegne subito. Per riaccenderlo: un clic sul tasto laterale." }
    override val languageSection = "Lingua"
    override val languageHint = "Vale per le schermate, la voce dell'arbitro, il tabellone TV e i braccialetti."
    override val audioSection = "Audio e voce"
    override val voiceCalls = "Chiamate vocali dell'arbitro"
    override val voiceFiles: (Int, Int) -> String = { n, tot -> "File generati: $n/$tot" }
    override val voiceFilesHint = "Tutto funziona senza internet con le voci installate sul telefono. Le registrazioni personalizzate (ZIP) hanno sempre la precedenza sulla sintesi vocale."
    override val generateVoice = "Genera file"
    override val generating: (Int, Int) -> String = { n, tot -> "Generazione $n/$tot…" }
    override val voiceGenerationFailed = "Generazione non completata: i file vocali di prima restano com'erano."
    override val importVoiceZip = "Importa ZIP"
    override val voiceImportFailed = "ZIP non leggibile o incompleto: nessuna registrazione è cambiata."
    override val deleteCustomVoice = "Rimuovi registrazioni"
    override val deleteCustomConfirmTitle = "Rimuovere le registrazioni?"
    override val deleteCustomConfirmText: (Int) -> String = { n -> "Le registrazioni personalizzate in italiano ($n) vengono cancellate dal telefono. Per riaverle bisogna importare di nuovo lo ZIP." }
    override val ttsEngine = "Motore sintesi vocale"
    override val engineDefault = "Predefinito del telefono"
    override val ttsVoice = "Voce"
    override val voiceAuto = "Automatica (migliore offline)"
    override val voiceName: (String) -> String = { "Voce $it" }
    override val online = "online"
    override val offline = "offline"
    override val voiceFilesMode = "Usa file audio pre-generati"
    override val voiceFilesModeHint = "Di norma ogni chiamata è letta in un'unica frase (più naturale). Attivalo per usare i file generati, ad esempio per portarti offline una voce online."
    override val customRecordings: (Int, Int) -> String = { n, tot -> "Registrazioni personalizzate: $n/$tot" }
    override val voiceReadme: (String, String) -> String = { langs, formats ->
        "TENNIS SCORE MANAGER - FILE VOCALI\n" +
            "Metti le registrazioni in voice/<lingua>/ ($langs) con il nome della chiave.\n" +
            "Formati: $formats. I nomi dei giocatori sono sempre letti dal TTS.\n" +
            "La cartella tts/ contiene i file generati dall'app: le tue registrazioni hanno la precedenza."
    }
    override val voiceReadmeKey = "CHIAVE"
    override val testVoice = "Prova voce"
    override val stopVoiceTest = "Ferma la prova"
    override val ttsMissing = "Voce italiana della sintesi vocale non installata sul telefono."
    override val installVoice = "Installa voce"
    override val ttsEngineError = "Sintesi vocale non disponibile: controlla il motore in Impostazioni di Android."
    override val openTtsSettings = "Apri impostazioni"
    override val formatSection = "Formato partita"
    override val formatBestOfThree = "3 set · tie-break a 7"
    override val formatBestOfThreeHint = "Al meglio dei tre set, tie-break sul 6-6 in ogni set."
    override val formatMatchTiebreak = "2 set + super tie-break a 10"
    override val formatMatchTiebreakHint = "Sull'1-1 il terzo set è un match tie-break a 10 punti (2 di scarto)."
    override val noAd = "No-Ad (punto decisivo)"
    override val noAdHint = "Sul 40-40 si gioca un solo punto: chi lo vince vince il game."
    override val coinToss = "Sorteggio (Coin Toss)"
    override val tossCoin = "Lancia la moneta"
    override val tossWinner: (String) -> String = { "Vince il sorteggio: $it" }
    override val tossHint = "Chi vince sceglie: servizio, risposta o campo. Imposta qui la scelta."
    override val serving = "Al servizio"
    override val courtSides = "Lati del campo"
    override val umpireView = "Vista dal giudice di sedia"
    override val swapSides = "Inverti lati"
    override val firstServerOf: (String) -> String = { "Serve per primo ($it)" }
    override val left = "Sinistra"
    override val right = "Destra"
    override val net = "RETE"
    override val umpireChair = "Giudice di sedia"

    override val locationDialogTitle = "Abilita la posizione"
    override val locationDialogText = "Se non abiliti la posizione non potrai averla nei dati riepilogativi della partita."
    override val continueWithout = "Continua senza"
    override val bandsRequiredTitle = "Bluetooth e posizione obbligatori"
    override val bandsRequiredText = "Per usare i braccialetti devi attivare il Bluetooth, concedere il permesso di posizione e tenere attiva la posizione."
    override val bandsMissingTitle = "Braccialetti non associati"
    override val bandsMissingText: (List<String>) -> String = {
        (if (it.size > 1) "Mancano i braccialetti per: " else "Manca il braccialetto per: ") + it.joinToString(", ") + ". Continuare lo stesso?"
    }
    override val continueAnyway = "Continua"
    override val cancel = "Annulla"

    override val startMatch = "INIZIO PARTITA"
    override val startHint = "Premi il pulsante per iniziare"
    override val startHintBands = "Premi il pulsante oppure KEY1 su un braccialetto"
    override val startButton = "Inizia partita"
    override val resumeSaved = "Riprendi partita sospesa"
    override val noSavedMatches = "Nessuna partita sospesa salvata."
    override val savedMatchesTitle = "Partite sospese"
    override val delete = "Elimina"
    override val deleteSavedTitle = "Eliminare la partita sospesa?"
    override val deleteSavedText: (String) -> String = { "La partita $it non si potrà più riprendere." }
    override val vs = "vs"

    override val matchTime = "Match Time"
    override val setsHeader = "SET"
    override val gamesHeader = "GAME"
    override val undoPoint = "Annulla punto"
    override val suspend = "Sospendi"
    override val resume = "Riprendi"
    override val audioOn = "Audio On"
    override val audioOff = "Audio Off"
    override val newMatch = "Nuova partita"
    override val suspendedOverlay = "PARTITA SOSPESA"
    override val newMatchConfirmTitle = "Nuova partita?"
    override val newMatchConfirmText = "La partita in corso resta salvata tra le partite sospese e potrai riprenderla."
    override val endDialogTitle = "Gioco, set, partita"
    override val endDialogText: (String, String, Boolean) -> String = { name, score, team -> "${if (team) "Vincono" else "Vince"} $name\n$score" }
    override val matchConcluded = "Partita conclusa"
    override val undoLastPoint = "Annulla ultimo punto"
    override val serveOrderTitle: (Int) -> String = { "Ordine di servizio · set $it" }
    override val whoServesFirst: (String) -> String = { "Chi serve per primo in $it?" }
    override val confirm = "Conferma"
    override val backDisabled = "Durante la partita usa «Nuova partita» o «Esci»."
    override val exit = "Esci"
    override val exitConfirmTitle = "Uscire dall'app?"
    override val exitConfirmText = "La partita resta salvata tra le partite sospese e potrai riprenderla."
    override val exitConfirmBands = "I braccialetti vengono spenti."

    override val msgChangeEnds = "CAMBIO CAMPO"
    override val msgTiebreak = "TIE-BREAK"
    override val msgMatchTiebreak = "SUPER TIE-BREAK"
    override val msgSetWon: (String) -> String = { "SET $it" }
    override val msgSetPoint = "SET POINT"
    override val msgMatchPoint = "MATCH POINT"
    override val msgBreakPoint = "PALLA BREAK"
    override val msgDecidingPoint = "PUNTO DECISIVO"
    override val msgPointUndone = "PUNTO ANNULLATO"
    override val msgSuspended = "PARTITA SOSPESA"
    override val msgResumed = "PARTITA RIPRESA"
    override val msgBandConnected: (String) -> String = { "BRACCIALETTO $it CONNESSO" }
    override val msgBandLost: (String) -> String = { "BRACCIALETTO $it DISCONNESSO" }
    override val msgBandOff: (String, Int?) -> String = { n, why ->
        "BRACCIALETTO $n SPENTO" + when (why) {
            BandProtocol.OFF_IDLE -> " (INATTIVO)"
            BandProtocol.OFF_BATTERY -> " (BATTERIA SCARICA)"
            BandProtocol.OFF_TIMEOUT -> " (NESSUN TELEFONO)"
            else -> ""
        }
    }
    override val msgBandBatteryLow: (String, Int) -> String = { n, p -> "BRACCIALETTO $n: BATTERIA $p%" }
    override val bandBatteryLow = "BATTERIA BASSA"
    override val autonomy: (String) -> String = { "autonomia ~$it" }
    override val bandsBattery = "Batteria braccialetti"

    override val notifChannel = "Partita in corso"
    override val notifText: (Boolean, Boolean) -> String = { bands, tv ->
        "Partita in corso · " + when {
            bands && tv -> "braccialetti e tabellone TV attivi"
            tv -> "tabellone TV attivo"
            else -> "braccialetti attivi"
        }
    }
    override val notifTvOnly = "Tabellone TV attivo"

    override val bandPaired = "ASSOCIATO A"
    override val bandPlay = "GIOCO"
    override val bandChangeEnds = "CAMBIO CAMPO"
    override val bandTiebreak = "TIE-BREAK"
    override val bandSet = "SET"
    override val bandSuspended = "SOSPESA"
    override val bandGameSetMatch = "GAME SET MATCH"
    override val bandMatchOver = "FINE PARTITA"
    override val bandAppClosed = "APP CHIUSA"
    override val bandOffFromApp = "SPEGNIMENTO"

    override val summaryTitle = "Partita conclusa"
    override val winner = "Vincitore"
    override val duration = "Durata"
    override val startTime = "Inizio"
    override val endTime = "Fine"
    override val date = "Data"
    override val club = "Circolo"
    override val court = "Campo"
    override val place = "Luogo"
    override val placeUnavailable = "Posizione non disponibile"
    override val format = "Formato"
    override val pointsWon = "Punti vinti"
    override val gamesWon = "Game vinti"
    override val result = "Risultato"
    override val saveHistory = "Salva nello storico"
    override val summaryLeaveTitle = "Riepilogo non salvato"
    override val summaryLeaveText = "Non hai salvato né condiviso il riepilogo della partita: dopo non potrai più rivederlo."
    override val share = "Condividi"
    override val saveDialogTitle = "Salva nello storico"
    override val fileName = "Nome"
    override val folder = "Cartella"
    override val chooseFolder = "Scegli cartella"
    override val defaultFolder = "Cartella dell'app (predefinita)"
    override val formatReport = "Resoconto (.txt)"
    override val formatData = "Dati partita (.json)"
    override val formatImage = "Immagine (.png)"
    override val save = "Salva"
    override val savedTo: (String) -> String = { "Salvato in $it" }
    override val saveError = "Salvataggio non riuscito"
    override val shareSubject = "Risultato partita di tennis"
    override val playerDefault: (Int) -> String = { "Giocatore $it" }
    override val teamJoiner = " e "
    override val generatedWith = "Creato con Tennis Score Manager"

    override val tvServe = "SERVIZIO"
    override val tvChangeover = "CAMBIO CAMPO"
    override val tvSetBreak = "PAUSA SET"
    override val tvTiebreakBreak = "PAUSA"
    override val tvWaiting = "IN ATTESA DELLA PARTITA"
    override val tvReady = "IN ATTESA DEL VIA"
    override val tvSuspended = "PARTITA SOSPESA"
    override val tvWinner = "VINCE"
    override val tvLost = "CONNESSIONE PERSA - RICONNESSIONE..."
    override val tvFullscreen = "SCHERMO INTERO"
    override val tvPageTitle = "Tabellone TSM"

    override val tvSection = "Tabellone su TV"
    override val tvEnable = "Tabellone su TV o monitor"
    override val tvEnableHint = "Un altro telefono (o un computer, o un Chromecast) mostra il punteggio in diretta su un monitor. I telefoni devono stare sulla stessa rete: l'hotspot di uno dei due."
    override val tvAddress = "Indirizzo del tabellone"
    override val tvNoNetwork = "Nessuna rete: accendi l'hotspot su uno dei due telefoni e collega l'altro."
    override val tvScreens: (Int) -> String = { if (it == 0) "Nessun tabellone collegato" else if (it == 1) "1 tabellone collegato" else "$it tabelloni collegati" }
    override val tvQrHint = "Sull'altro telefono: apri Tennis Score Manager e tocca «Usa come tabellone» (si collega da solo), oppure inquadra il codice con la fotocamera e aprilo nel browser."
    override val tvLook = "Aspetto del tabellone"
    override val tvTitle = "Scritta in basso"
    override val tvTitleHint: (String) -> String = { if (it.isEmpty()) "Vuota: nessuna scritta" else "Vuota: «$it» (circolo e campo della pagina 1)" }
    override val tvColorOf: (String) -> String = { "Colore di $it" }
    override val tvShowClock = "Tempo partita"
    override val tvShowTimers = "Cronometro servizio e pause"
    override val tvShowSets = "Set conclusi"
    override val tvShowMessages = "Messaggi (palla break, set point...)"
    override val tvShowServe = "Pallina di chi serve"
    override val tvGhost = "Segmenti spenti visibili"
    override val tvPreview = "Anteprima su questo telefono"
    override val tvChromecastHint = "Con un Chromecast: sul telefono-tabellone usa il pulsante «Trasmetti» delle impostazioni rapide («Trasmissione schermo» fino ad Android 14, «Smart View» sui Samsung). Il Chromecast vuole una rete con internet: accendi i dati mobili sul telefono che fa l'hotspot."

    override val displayMode = "Usa come tabellone"
    override val displayModeHint = "Questo telefono mostra il punteggio sul monitor (cavo HDMI o Chromecast)"
    override val displaySearching = "Cerco il telefono dell'arbitro…"
    override val displaySteps = "1. Accendi l'hotspot su uno dei due telefoni e collega l'altro.\n2. Sul telefono dell'arbitro: pagina 2 → «Tabellone su TV» acceso.\n3. Collega questo telefono al monitor (cavo USB-C/HDMI) o trasmetti lo schermo a un Chromecast."
    override val displayManual = "Indirizzo (es. 192.168.43.1:8080)"
    override val displayConnect = "Collega"
    override val displayNotFound = "Non trovato. Controlla che i due telefoni siano sulla stessa rete e che il tabellone sia acceso nell'app dell'arbitro."
    override val displayOnMonitor = "Il tabellone è sul monitor esterno"
    override val displayShowHere = "Mostra anche qui"
    override val displayBackAgain = "Premi di nuovo indietro per uscire"
    override val displayRetry = "Cerca di nuovo"
}

object EnStrings : Strings {
    override val playerTag: (Int) -> String = { "P$it" }
    override val datePattern = "EEEE, d MMMM yyyy"

    override val setupTitle = "New match"
    override val setupSubtitle = "Optional setup: you can leave everything empty and go on."
    override val clubSection = "Club and court"
    override val clubName = "Tennis club name"
    override val courtNumber = "Court number"
    override val singles = "Singles"
    override val doubles = "Doubles"
    override val doublesHint = "In doubles enter two names per team: scoring follows the ITF doubles rules."
    override val player1 = "Player 1"
    override val player2 = "Player 2"
    override val playerName = "Name"
    override val clearFields = "Clear fields"
    override val next = "Next"
    override val back = "Back"

    override val optionsTitle = "Mode and rules"
    override val modeSection = "Play mode"
    override val modeReferee = "Umpire"
    override val modeBands = "Wristbands"
    override val modeRefereeHint = "Points are scored on the phone, like a chair umpire."
    override val modeBandsHint = "Each player scores with KEY1 on their own M5StickS3."
    override val requirements = "Requirements"
    override val bluetooth = "Bluetooth"
    override val location = "Location permission"
    override val locationServices = "Location on"
    override val enable = "Turn on"
    override val allow = "Allow"
    override val ok = "OK"
    override val bandFor: (String) -> String = { "$it's wristband" }
    override val noBand = "None"
    override val bandConnected = "Connected"
    override val bandConnecting = "Connecting…"
    override val bandIdle = "Not connected"
    override val bandOff = "Off"
    override val battery = "Battery"
    override val bandCharging: (Int) -> String = { "Charging $it%" }
    override val bandChargeFull = "Fully charged"
    override val autoSearch = "Searching automatically: switch the wristbands on (side button), they pair by themselves."
    override val autoSearchOff = "The search starts once the requirements above are met."
    override val identify = "Identify"
    override val swapBands = "Swap P1 ↔ P2"
    override val bandsOffAtEnd = "Switch the wristbands off at match end and on exit"
    override val bandsOffAtEndHint = "One click on the side button switches them back on."
    override val bandNotReady = "Wristband not connected"

    override val bandSettings = "Wristband settings"
    override val settingsShort = "Settings"
    override val bandSettingsNeedLink = "Connect the wristband to see and change its settings."
    override val bandFirmwareOld = "This wristband's firmware has no settings: upload TSM_Band.ino 2.0."
    override val bandFirmware: (String) -> String = { "Firmware $it" }
    override val bandName = "Name"
    override val brightness = "Display brightness"
    override val scoreTime = "Score shown after each point"
    override val scoreTimeHint = "The end-of-game summary stays 2 seconds longer."
    override val beeperVolume = "Beeper volume"
    override val mute = "Mute"
    override val off = "Off"
    override val flipDisplay = "Flip display"
    override val flipDisplayHint = "To wear the wristband on the other wrist."
    override val autoOff = "Automatic power-off"
    override val pairTimeout = "At power-on, if no phone connects"
    override val lostTimeout = "If the phone link is lost"
    override val idleTimeout = "If connected but idle"
    override val estimateFull: (String) -> String = { "Estimated battery life $it from full" }
    override val estimateNow: (String, Int) -> String = { h, p -> "$h at the current charge ($p%)" }
    override val estimateBreakdown: (String, String, String, String) -> String = { tot, base, dsp, snd ->
        "Average draw $tot mA: board and Bluetooth $base · display $dsp · beeper $snd"
    }
    override val estimateMeasured = "Base draw measured on this wristband while in use."
    override val estimateTheory = "Theoretical estimate: after 20 minutes of use it switches to the measured draw."
    override val copyToOther = "Copy to the other wristband"
    override val powerOff = "Power off"
    override val bandPowerOffTitle = "Power off the wristband?"
    override val bandPowerOffText: (String) -> String = { "$it switches off now. To turn it back on: one click on the side button." }
    override val languageSection = "Language"
    override val languageHint = "Applies to the screens, the umpire's voice, the TV scoreboard and the wristbands."
    override val audioSection = "Audio and voice"
    override val voiceCalls = "Umpire voice calls"
    override val voiceFiles: (Int, Int) -> String = { n, tot -> "Generated files: $n/$tot" }
    override val voiceFilesHint = "Everything works offline with the voices installed on the phone. Custom recordings (ZIP) always take precedence over text-to-speech."
    override val generateVoice = "Generate files"
    override val generating: (Int, Int) -> String = { n, tot -> "Generating $n/$tot…" }
    override val voiceGenerationFailed = "Generation not completed: the previous voice files are unchanged."
    override val importVoiceZip = "Import ZIP"
    override val voiceImportFailed = "ZIP unreadable or incomplete: no recordings were changed."
    override val deleteCustomVoice = "Remove recordings"
    override val deleteCustomConfirmTitle = "Remove the recordings?"
    override val deleteCustomConfirmText: (Int) -> String = { n -> "The custom English recordings ($n) are deleted from the phone. To get them back, import the ZIP again." }
    override val ttsEngine = "Speech engine"
    override val engineDefault = "Phone default"
    override val ttsVoice = "Voice"
    override val voiceAuto = "Automatic (best offline)"
    override val voiceName: (String) -> String = { "Voice $it" }
    override val online = "online"
    override val offline = "offline"
    override val voiceFilesMode = "Use pre-generated audio files"
    override val voiceFilesModeHint = "By default each call is read as one sentence (more natural). Turn this on to play the generated files, e.g. to take an online voice offline."
    override val customRecordings: (Int, Int) -> String = { n, tot -> "Custom recordings: $n/$tot" }
    override val voiceReadme: (String, String) -> String = { langs, formats ->
        "TENNIS SCORE MANAGER - VOICE FILES\n" +
            "Put your recordings in voice/<language>/ ($langs), named after the key.\n" +
            "Formats: $formats. Player names are always read by text-to-speech.\n" +
            "The tts/ folder holds the files generated by the app: your recordings take precedence."
    }
    override val voiceReadmeKey = "KEY"
    override val testVoice = "Test voice"
    override val stopVoiceTest = "Stop the test"
    override val ttsMissing = "English text-to-speech voice is not installed on this phone."
    override val installVoice = "Install voice"
    override val ttsEngineError = "Text-to-speech is not available: check the engine in Android Settings."
    override val openTtsSettings = "Open settings"
    override val formatSection = "Match format"
    override val formatBestOfThree = "3 sets · tie-break to 7"
    override val formatBestOfThreeHint = "Best of three sets, tie-break at 6-6 in every set."
    override val formatMatchTiebreak = "2 sets + match tie-break to 10"
    override val formatMatchTiebreakHint = "At one set all the third set is a 10-point match tie-break (win by 2)."
    override val noAd = "No-Ad (deciding point)"
    override val noAdHint = "At deuce a single deciding point is played."
    override val coinToss = "Coin toss"
    override val tossCoin = "Toss the coin"
    override val tossWinner: (String) -> String = { "Toss won by: $it" }
    override val tossHint = "The winner chooses serve, receive or end. Set the choice here."
    override val serving = "Serving"
    override val courtSides = "Court ends"
    override val umpireView = "Chair umpire's view"
    override val swapSides = "Swap ends"
    override val firstServerOf: (String) -> String = { "Serves first ($it)" }
    override val left = "Left"
    override val right = "Right"
    override val net = "NET"
    override val umpireChair = "Chair umpire"

    override val locationDialogTitle = "Turn on location"
    override val locationDialogText = "Without location it cannot be included in the match summary."
    override val continueWithout = "Continue without"
    override val bandsRequiredTitle = "Bluetooth and location required"
    override val bandsRequiredText = "To use the wristbands turn on Bluetooth, allow location and keep location on."
    override val bandsMissingTitle = "Wristbands not assigned"
    override val bandsMissingText: (List<String>) -> String = {
        (if (it.size > 1) "No wristbands for: " else "No wristband for: ") + it.joinToString(", ") + ". Continue anyway?"
    }
    override val continueAnyway = "Continue"
    override val cancel = "Cancel"

    override val startMatch = "MATCH START"
    override val startHint = "Press the button to start"
    override val startHintBands = "Press the button or KEY1 on a wristband"
    override val startButton = "Start match"
    override val resumeSaved = "Resume suspended match"
    override val noSavedMatches = "No suspended match saved."
    override val savedMatchesTitle = "Suspended matches"
    override val delete = "Delete"
    override val deleteSavedTitle = "Delete the suspended match?"
    override val deleteSavedText: (String) -> String = { "The match $it can no longer be resumed." }
    override val vs = "vs"

    override val matchTime = "Match Time"
    override val setsHeader = "SETS"
    override val gamesHeader = "GAMES"
    override val undoPoint = "Undo point"
    override val suspend = "Suspend"
    override val resume = "Resume"
    override val audioOn = "Audio On"
    override val audioOff = "Audio Off"
    override val newMatch = "New match"
    override val suspendedOverlay = "MATCH SUSPENDED"
    override val newMatchConfirmTitle = "New match?"
    override val newMatchConfirmText = "The current match stays saved among suspended matches and can be resumed."
    override val endDialogTitle = "Game, set and match"
    override val endDialogText: (String, String, Boolean) -> String = { name, score, team -> "$name ${if (team) "win" else "wins"}\n$score" }
    override val matchConcluded = "Match concluded"
    override val undoLastPoint = "Undo last point"
    override val serveOrderTitle: (Int) -> String = { "Serving order · set $it" }
    override val whoServesFirst: (String) -> String = { "Who serves first for $it?" }
    override val confirm = "Confirm"
    override val backDisabled = "During the match use «New match» or «Exit»."
    override val exit = "Exit"
    override val exitConfirmTitle = "Exit the app?"
    override val exitConfirmText = "The match stays saved among suspended matches and can be resumed."
    override val exitConfirmBands = "The wristbands are switched off."

    override val msgChangeEnds = "CHANGE ENDS"
    override val msgTiebreak = "TIE-BREAK"
    override val msgMatchTiebreak = "MATCH TIE-BREAK"
    override val msgSetWon: (String) -> String = { "SET $it" }
    override val msgSetPoint = "SET POINT"
    override val msgMatchPoint = "MATCH POINT"
    override val msgBreakPoint = "BREAK POINT"
    override val msgDecidingPoint = "DECIDING POINT"
    override val msgPointUndone = "POINT UNDONE"
    override val msgSuspended = "MATCH SUSPENDED"
    override val msgResumed = "MATCH RESUMED"
    override val msgBandConnected: (String) -> String = { "WRISTBAND $it CONNECTED" }
    override val msgBandLost: (String) -> String = { "WRISTBAND $it DISCONNECTED" }
    override val msgBandOff: (String, Int?) -> String = { n, why ->
        "WRISTBAND $n OFF" + when (why) {
            BandProtocol.OFF_IDLE -> " (IDLE)"
            BandProtocol.OFF_BATTERY -> " (BATTERY EMPTY)"
            BandProtocol.OFF_TIMEOUT -> " (NO PHONE)"
            else -> ""
        }
    }
    override val msgBandBatteryLow: (String, Int) -> String = { n, p -> "WRISTBAND $n: BATTERY $p%" }
    override val bandBatteryLow = "LOW BATTERY"
    override val autonomy: (String) -> String = { "about $it left" }
    override val bandsBattery = "Wristband battery"

    override val notifChannel = "Match in progress"
    override val notifText: (Boolean, Boolean) -> String = { bands, tv ->
        "Match in progress · " + when {
            bands && tv -> "wristbands and TV scoreboard on"
            tv -> "TV scoreboard on"
            else -> "wristbands on"
        }
    }
    override val notifTvOnly = "TV scoreboard on"

    override val bandPaired = "PAIRED WITH"
    override val bandPlay = "PLAY"
    override val bandChangeEnds = "CHANGE ENDS"
    override val bandTiebreak = "TIE-BREAK"
    override val bandSet = "SET"
    override val bandSuspended = "SUSPENDED"
    override val bandGameSetMatch = "GAME SET MATCH"
    override val bandMatchOver = "MATCH OVER"
    override val bandAppClosed = "APP CLOSED"
    override val bandOffFromApp = "POWER OFF"

    override val summaryTitle = "Match concluded"
    override val winner = "Winner"
    override val duration = "Duration"
    override val startTime = "Start"
    override val endTime = "End"
    override val date = "Date"
    override val club = "Club"
    override val court = "Court"
    override val place = "Place"
    override val placeUnavailable = "Location not available"
    override val format = "Format"
    override val pointsWon = "Points won"
    override val gamesWon = "Games won"
    override val result = "Result"
    override val saveHistory = "Save to history"
    override val summaryLeaveTitle = "Summary not saved"
    override val summaryLeaveText = "You have neither saved nor shared the match summary: you won't be able to see it again."
    override val share = "Share"
    override val saveDialogTitle = "Save to history"
    override val fileName = "Name"
    override val folder = "Folder"
    override val chooseFolder = "Choose folder"
    override val defaultFolder = "App folder (default)"
    override val formatReport = "Report (.txt)"
    override val formatData = "Match data (.json)"
    override val formatImage = "Image (.png)"
    override val save = "Save"
    override val savedTo: (String) -> String = { "Saved to $it" }
    override val saveError = "Saving failed"
    override val shareSubject = "Tennis match result"
    override val playerDefault: (Int) -> String = { "Player $it" }
    override val teamJoiner = " and "
    override val generatedWith = "Made with Tennis Score Manager"

    override val tvSet = "SETS"
    override val tvServe = "SERVE"
    override val tvChangeover = "CHANGEOVER"
    override val tvSetBreak = "SET BREAK"
    override val tvTiebreakBreak = "BREAK"
    override val tvWaiting = "WAITING FOR THE MATCH"
    override val tvReady = "READY TO PLAY"
    override val tvSuspended = "MATCH SUSPENDED"
    override val tvWinner = "WINNER"
    override val tvLost = "CONNECTION LOST - RECONNECTING..."
    override val tvFullscreen = "FULL SCREEN"
    override val tvPageTitle = "TSM Scoreboard"

    override val tvSection = "TV scoreboard"
    override val tvEnable = "Scoreboard on a TV or monitor"
    override val tvEnableHint = "Another phone (or a computer, or a Chromecast) shows the live score on a monitor. The phones must be on the same network: one phone's hotspot."
    override val tvAddress = "Scoreboard address"
    override val tvNoNetwork = "No network: turn on the hotspot on one phone and connect the other."
    override val tvScreens: (Int) -> String = { if (it == 0) "No scoreboard connected" else if (it == 1) "1 scoreboard connected" else "$it scoreboards connected" }
    override val tvQrHint = "On the other phone: open Tennis Score Manager and tap «Use as scoreboard» (it connects by itself), or scan the code with the camera and open it in the browser."
    override val tvLook = "Scoreboard look"
    override val tvTitle = "Bottom line"
    override val tvTitleHint: (String) -> String = { if (it.isEmpty()) "Empty: no text" else "Empty: «$it» (club and court from page 1)" }
    override val tvColorOf: (String) -> String = { "Colour of $it" }
    override val tvShowClock = "Match time"
    override val tvShowTimers = "Serve clock and breaks"
    override val tvShowSets = "Finished sets"
    override val tvShowMessages = "Messages (break point, set point...)"
    override val tvShowServe = "Ball next to the server"
    override val tvGhost = "Unlit segments visible"
    override val tvPreview = "Preview on this phone"
    override val tvChromecastHint = "With a Chromecast: on the scoreboard phone use the «Cast» tile in Quick Settings («Screen Cast» up to Android 14, «Smart View» on Samsung). The Chromecast needs a network with internet: turn on mobile data on the hotspot phone."

    override val displayMode = "Use as scoreboard"
    override val displayModeHint = "This phone shows the score on the monitor (HDMI cable or Chromecast)"
    override val displaySearching = "Looking for the umpire's phone…"
    override val displaySteps = "1. Turn on the hotspot on one phone and connect the other.\n2. On the umpire's phone: page 2 → «TV scoreboard» on.\n3. Connect this phone to the monitor (USB-C/HDMI cable) or cast the screen to a Chromecast."
    override val displayManual = "Address (e.g. 192.168.43.1:8080)"
    override val displayConnect = "Connect"
    override val displayNotFound = "Not found. Check that both phones are on the same network and the scoreboard is on in the umpire's app."
    override val displayOnMonitor = "The scoreboard is on the external monitor"
    override val displayShowHere = "Show here too"
    override val displayBackAgain = "Press back again to exit"
    override val displayRetry = "Search again"
}

fun stringsFor(lang: Lang): Strings = when (lang) {
    Lang.IT -> ItStrings
    Lang.EN -> EnStrings
    Lang.FR -> FrStrings
    Lang.DE -> DeStrings
    Lang.ES -> EsStrings
    Lang.PT -> PtStrings
}

val LocalStrings = staticCompositionLocalOf<Strings> { ItStrings }
TSM_EOF

# ---------------------------------------------------------------- app/src/main/java/com/tennis/scoremanager/ui/StringsDe.kt
cat > "$DEST/app/src/main/java/com/tennis/scoremanager/ui/StringsDe.kt" << 'TSM_EOF'
package com.tennis.scoremanager.ui

import com.tennis.scoremanager.ble.BandProtocol

/** Deutsch (DTB-Begriffe: Stuhlschiedsrichter, Satzball, Seitenwechsel). */
object DeStrings : Strings {
    override val playerTag: (Int) -> String = { "S$it" }
    override val datePattern = "EEEE, d. MMMM yyyy"

    override val setupTitle = "Neues Match"
    override val setupSubtitle = "Einrichtung optional: Du kannst alles leer lassen und weitermachen."
    override val clubSection = "Verein und Platz"
    override val clubName = "Name des Tennisvereins"
    override val courtNumber = "Platznummer"
    override val singles = "Einzel"
    override val doubles = "Doppel"
    override val doublesHint = "Im Doppel zwei Namen pro Team eingeben: Die Zählung folgt den ITF-Regeln für das Doppel."
    override val player1 = "Spieler 1"
    override val player2 = "Spieler 2"
    override val playerName = "Name"
    override val clearFields = "Felder leeren"
    override val next = "Weiter"
    override val back = "Zurück"

    override val optionsTitle = "Modus und Regeln"
    override val modeSection = "Spielmodus"
    override val modeReferee = "Schiedsrichter"
    override val modeBands = "Armbänder"
    override val modeRefereeHint = "Die Punkte werden am Telefon vergeben, wie vom Stuhlschiedsrichter."
    override val modeBandsHint = "Jeder Spieler vergibt den Punkt mit KEY1 an seinem eigenen M5StickS3."
    override val requirements = "Voraussetzungen"
    override val bluetooth = "Bluetooth"
    override val location = "Standortberechtigung"
    override val locationServices = "Standort aktiviert"
    override val enable = "Einschalten"
    override val allow = "Erlauben"
    override val ok = "OK"
    override val bandFor: (String) -> String = { "Armband von $it" }
    override val noBand = "Keins"
    override val bandConnected = "Verbunden"
    override val bandConnecting = "Verbinde…"
    override val bandIdle = "Nicht verbunden"
    override val bandOff = "Aus"
    override val battery = "Akku"
    override val bandCharging: (Int) -> String = { "Lädt $it %" }
    override val bandChargeFull = "Voll geladen"
    override val autoSearch = "Automatische Suche: Schalte die Armbänder ein (Seitentaste), sie koppeln sich von selbst."
    override val autoSearchOff = "Die Suche startet, sobald die Voraussetzungen oben erfüllt sind."
    override val identify = "Erkennen"
    override val swapBands = "S1 ↔ S2 tauschen"
    override val bandsOffAtEnd = "Armbänder am Matchende und beim Beenden ausschalten"
    override val bandsOffAtEndHint = "Ein Klick auf die Seitentaste schaltet sie wieder ein."
    override val bandNotReady = "Armband nicht verbunden"

    override val bandSettings = "Armband-Einstellungen"
    override val settingsShort = "Einstellungen"
    override val bandSettingsNeedLink = "Verbinde das Armband, um seine Einstellungen zu sehen und zu ändern."
    override val bandFirmwareOld = "Die Firmware dieses Armbands hat keine Einstellungen: Lade TSM_Band.ino 2.0 hoch."
    override val bandFirmware: (String) -> String = { "Firmware $it" }
    override val bandName = "Name"
    override val brightness = "Displayhelligkeit"
    override val scoreTime = "Spielstand nach jedem Punkt sichtbar"
    override val scoreTimeHint = "Die Zusammenfassung am Spielende bleibt 2 Sekunden länger."
    override val beeperVolume = "Lautstärke des Piepsers"
    override val mute = "Stumm"
    override val off = "Aus"
    override val flipDisplay = "Display gedreht"
    override val flipDisplayHint = "Um das Armband am anderen Handgelenk zu tragen."
    override val autoOff = "Automatisches Ausschalten"
    override val pairTimeout = "Beim Einschalten, wenn sich kein Telefon verbindet"
    override val lostTimeout = "Wenn die Verbindung zum Telefon verloren geht"
    override val idleTimeout = "Wenn es verbunden, aber inaktiv bleibt"
    override val estimateFull: (String) -> String = { "Geschätzte Laufzeit $it bei voller Ladung" }
    override val estimateNow: (String, Int) -> String = { h, p -> "$h mit der aktuellen Ladung ($p %)" }
    override val estimateBreakdown: (String, String, String, String) -> String = { tot, base, dsp, snd ->
        "Mittlerer Verbrauch $tot mA: Platine und Bluetooth $base · Display $dsp · Piepser $snd"
    }
    override val estimateMeasured = "Grundverbrauch an diesem Armband während der Nutzung gemessen."
    override val estimateTheory = "Theoretische Schätzung: Nach 20 Minuten Nutzung wird sie mit dem gemessenen Verbrauch korrigiert."
    override val copyToOther = "Auf das andere Armband kopieren"
    override val powerOff = "Ausschalten"
    override val bandPowerOffTitle = "Armband ausschalten?"
    override val bandPowerOffText: (String) -> String = { "$it schaltet sich sofort aus. Zum Einschalten: ein Klick auf die Seitentaste." }
    override val languageSection = "Sprache"
    override val languageHint = "Gilt für die Bildschirme, die Stimme des Schiedsrichters, die TV-Anzeigetafel und die Armbänder."
    override val audioSection = "Audio und Stimme"
    override val voiceCalls = "Ansagen des Schiedsrichters"
    override val voiceFiles: (Int, Int) -> String = { n, tot -> "Erzeugte Dateien: $n/$tot" }
    override val voiceFilesHint = "Alles funktioniert ohne Internet mit den auf dem Telefon installierten Stimmen. Eigene Aufnahmen (ZIP) haben immer Vorrang vor der Sprachausgabe."
    override val generateVoice = "Dateien erzeugen"
    override val generating: (Int, Int) -> String = { n, tot -> "Erzeuge $n/$tot…" }
    override val voiceGenerationFailed = "Erzeugung nicht abgeschlossen: Die bisherigen Sprachdateien bleiben unverändert."
    override val importVoiceZip = "ZIP importieren"
    override val voiceImportFailed = "ZIP nicht lesbar oder unvollständig: Keine Aufnahme wurde geändert."
    override val deleteCustomVoice = "Aufnahmen entfernen"
    override val deleteCustomConfirmTitle = "Aufnahmen entfernen?"
    override val deleteCustomConfirmText: (Int) -> String = { n -> "Die eigenen deutschen Aufnahmen ($n) werden vom Telefon gelöscht. Um sie zurückzubekommen, importiere das ZIP erneut." }
    override val ttsEngine = "Sprachausgabe-Engine"
    override val engineDefault = "Standard des Telefons"
    override val ttsVoice = "Stimme"
    override val voiceAuto = "Automatisch (beste offline)"
    override val voiceName: (String) -> String = { "Stimme $it" }
    override val online = "online"
    override val offline = "offline"
    override val voiceFilesMode = "Vorab erzeugte Audiodateien verwenden"
    override val voiceFilesModeHint = "Normalerweise wird jede Ansage in einem Satz gesprochen (natürlicher). Schalte es ein, um die erzeugten Dateien abzuspielen, z. B. um eine Online-Stimme offline mitzunehmen."
    override val customRecordings: (Int, Int) -> String = { n, tot -> "Eigene Aufnahmen: $n/$tot" }
    override val voiceReadme: (String, String) -> String = { langs, formats ->
        "TENNIS SCORE MANAGER - SPRACHDATEIEN\n" +
            "Lege die Aufnahmen in voice/<sprache>/ ($langs) ab, benannt nach dem Schlüssel.\n" +
            "Formate: $formats. Die Spielernamen liest immer die Sprachausgabe.\n" +
            "Der Ordner tts/ enthält die von der App erzeugten Dateien: Deine Aufnahmen haben Vorrang."
    }
    override val voiceReadmeKey = "SCHLÜSSEL"
    override val testVoice = "Stimme testen"
    override val stopVoiceTest = "Test beenden"
    override val ttsMissing = "Die deutsche Stimme der Sprachausgabe ist auf dem Telefon nicht installiert."
    override val installVoice = "Stimme installieren"
    override val ttsEngineError = "Sprachausgabe nicht verfügbar: Prüfe die Engine in den Android-Einstellungen."
    override val openTtsSettings = "Einstellungen öffnen"
    override val formatSection = "Matchformat"
    override val formatBestOfThree = "2 Gewinnsätze · Tie-Break bis 7"
    override val formatBestOfThreeHint = "Auf zwei Gewinnsätze, Tie-Break bei 6 beide in jedem Satz."
    override val formatMatchTiebreak = "2 Sätze + Match-Tie-Break bis 10"
    override val formatMatchTiebreakHint = "Bei 1:1 Sätzen entscheidet ein Match-Tie-Break bis 10 Punkte (2 Punkte Abstand)."
    override val noAd = "No-Ad (entscheidender Punkt)"
    override val noAdHint = "Bei Einstand wird ein einziger Punkt gespielt: Wer ihn gewinnt, gewinnt das Spiel."
    override val coinToss = "Wahl (Münzwurf)"
    override val tossCoin = "Münze werfen"
    override val tossWinner: (String) -> String = { "Wahl gewonnen: $it" }
    override val tossHint = "Wer die Wahl gewinnt, entscheidet: Aufschlag, Rückschlag oder Seite. Hier einstellen."
    override val serving = "Aufschlag"
    override val courtSides = "Platzseiten"
    override val umpireView = "Sicht vom Schiedsrichterstuhl"
    override val swapSides = "Seiten tauschen"
    override val firstServerOf: (String) -> String = { "Schlägt zuerst auf ($it)" }
    override val left = "Links"
    override val right = "Rechts"
    override val net = "NETZ"
    override val umpireChair = "Stuhlschiedsrichter"

    override val locationDialogTitle = "Standort einschalten"
    override val locationDialogText = "Ohne Standort kann der Ort nicht in der Match-Zusammenfassung stehen."
    override val continueWithout = "Ohne weiter"
    override val bandsRequiredTitle = "Bluetooth und Standort erforderlich"
    override val bandsRequiredText = "Für die Armbänder Bluetooth einschalten, die Standortberechtigung erteilen und den Standort eingeschaltet lassen."
    override val bandsMissingTitle = "Armbänder nicht zugeordnet"
    override val bandsMissingText: (List<String>) -> String = {
        (if (it.size > 1) "Es fehlen die Armbänder für: " else "Es fehlt das Armband für: ") + it.joinToString(", ") + ". Trotzdem fortfahren?"
    }
    override val continueAnyway = "Fortfahren"
    override val cancel = "Abbrechen"

    override val startMatch = "MATCHBEGINN"
    override val startHint = "Drücke die Taste, um zu beginnen"
    override val startHintBands = "Drücke die Taste oder KEY1 an einem Armband"
    override val startButton = "Match starten"
    override val resumeSaved = "Unterbrochenes Match fortsetzen"
    override val noSavedMatches = "Kein unterbrochenes Match gespeichert."
    override val savedMatchesTitle = "Unterbrochene Matches"
    override val delete = "Löschen"
    override val deleteSavedTitle = "Unterbrochenes Match löschen?"
    override val deleteSavedText: (String) -> String = { "Das Match $it kann danach nicht mehr fortgesetzt werden." }
    override val vs = "gegen"

    override val matchTime = "Match Time"
    override val setsHeader = "SÄTZE"
    override val gamesHeader = "SPIELE"
    override val undoPoint = "Punkt zurück"
    override val suspend = "Unterbrechen"
    override val resume = "Fortsetzen"
    override val audioOn = "Audio On"
    override val audioOff = "Audio Off"
    override val newMatch = "Neues Match"
    override val suspendedOverlay = "MATCH UNTERBROCHEN"
    override val newMatchConfirmTitle = "Neues Match?"
    override val newMatchConfirmText = "Das laufende Match bleibt bei den unterbrochenen Matches gespeichert und kann fortgesetzt werden."
    override val endDialogTitle = "Spiel, Satz und Sieg"
    override val endDialogText: (String, String, Boolean) -> String = { name, score, _ -> "Sieg für $name\n$score" }
    override val matchConcluded = "Match beendet"
    override val undoLastPoint = "Letzten Punkt zurücknehmen"
    override val serveOrderTitle: (Int) -> String = { "Aufschlagfolge · Satz $it" }
    override val whoServesFirst: (String) -> String = { "Wer schlägt bei $it zuerst auf?" }
    override val confirm = "Bestätigen"
    override val backDisabled = "Während des Matches «Neues Match» oder «Beenden» verwenden."
    override val exit = "Beenden"
    override val exitConfirmTitle = "App beenden?"
    override val exitConfirmText = "Das Match bleibt bei den unterbrochenen Matches gespeichert und kann fortgesetzt werden."
    override val exitConfirmBands = "Die Armbänder werden ausgeschaltet."

    override val msgChangeEnds = "SEITENWECHSEL"
    override val msgTiebreak = "TIE-BREAK"
    override val msgMatchTiebreak = "MATCH-TIE-BREAK"
    override val msgSetWon: (String) -> String = { "SATZ $it" }
    override val msgSetPoint = "SATZBALL"
    override val msgMatchPoint = "MATCHBALL"
    override val msgBreakPoint = "BREAKBALL"
    override val msgDecidingPoint = "ENTSCHEIDENDER PUNKT"
    override val msgPointUndone = "PUNKT ZURÜCKGENOMMEN"
    override val msgSuspended = "MATCH UNTERBROCHEN"
    override val msgResumed = "MATCH FORTGESETZT"
    override val msgBandConnected: (String) -> String = { "ARMBAND $it VERBUNDEN" }
    override val msgBandLost: (String) -> String = { "ARMBAND $it GETRENNT" }
    override val msgBandOff: (String, Int?) -> String = { n, why ->
        "ARMBAND $n AUS" + when (why) {
            BandProtocol.OFF_IDLE -> " (INAKTIV)"
            BandProtocol.OFF_BATTERY -> " (AKKU LEER)"
            BandProtocol.OFF_TIMEOUT -> " (KEIN TELEFON)"
            else -> ""
        }
    }
    override val msgBandBatteryLow: (String, Int) -> String = { n, p -> "ARMBAND $n: AKKU $p %" }
    override val bandBatteryLow = "AKKU SCHWACH"
    override val autonomy: (String) -> String = { "noch ~$it" }
    override val bandsBattery = "Akku der Armbänder"

    override val notifChannel = "Laufendes Match"
    override val notifText: (Boolean, Boolean) -> String = { bands, tv ->
        "Match läuft · " + when {
            bands && tv -> "Armbänder und TV-Anzeigetafel aktiv"
            tv -> "TV-Anzeigetafel aktiv"
            else -> "Armbänder aktiv"
        }
    }
    override val notifTvOnly = "TV-Anzeigetafel aktiv"

    override val bandPaired = "GEKOPPELT MIT"
    override val bandPlay = "SPIELEN"
    override val bandChangeEnds = "SEITENWECHSEL"
    override val bandTiebreak = "TIE-BREAK"
    override val bandSet = "SATZ"
    override val bandSuspended = "UNTERBROCHEN"
    override val bandGameSetMatch = "SPIEL SATZ SIEG"
    override val bandMatchOver = "MATCH BEENDET"
    override val bandAppClosed = "APP BEENDET"
    override val bandOffFromApp = "AUSSCHALTEN"

    override val summaryTitle = "Match beendet"
    override val winner = "Sieger"
    override val duration = "Dauer"
    override val startTime = "Beginn"
    override val endTime = "Ende"
    override val date = "Datum"
    override val club = "Verein"
    override val court = "Platz"
    override val place = "Ort"
    override val placeUnavailable = "Standort nicht verfügbar"
    override val format = "Format"
    override val pointsWon = "Gewonnene Punkte"
    override val gamesWon = "Gewonnene Spiele"
    override val result = "Ergebnis"
    override val saveHistory = "Im Verlauf speichern"
    override val summaryLeaveTitle = "Zusammenfassung nicht gespeichert"
    override val summaryLeaveText = "Die Zusammenfassung des Matches wurde weder gespeichert noch geteilt: Danach ist sie nicht mehr abrufbar."
    override val share = "Teilen"
    override val saveDialogTitle = "Im Verlauf speichern"
    override val fileName = "Name"
    override val folder = "Ordner"
    override val chooseFolder = "Ordner wählen"
    override val defaultFolder = "App-Ordner (Standard)"
    override val formatReport = "Bericht (.txt)"
    override val formatData = "Matchdaten (.json)"
    override val formatImage = "Bild (.png)"
    override val save = "Speichern"
    override val savedTo: (String) -> String = { "Gespeichert in $it" }
    override val saveError = "Speichern fehlgeschlagen"
    override val shareSubject = "Ergebnis des Tennismatches"
    override val playerDefault: (Int) -> String = { "Spieler $it" }
    override val teamJoiner = " und "
    override val generatedWith = "Erstellt mit Tennis Score Manager"

    override val tvGames = "SPIELE"
    override val tvSet = "SÄTZE"
    override val tvSec = "SEK"
    override val tvServe = "AUFSCHLAG"
    override val tvChangeover = "SEITENWECHSEL"
    override val tvSetBreak = "SATZPAUSE"
    override val tvTiebreakBreak = "PAUSE"
    override val tvWaiting = "WARTEN AUF DAS MATCH"
    override val tvReady = "BEREIT ZUM SPIELEN"
    override val tvSuspended = "MATCH UNTERBROCHEN"
    override val tvWinner = "SIEGER"
    override val tvMatchTiebreak = "MATCH-TIE-BREAK"
    override val tvLost = "VERBINDUNG VERLOREN - NEUER VERSUCH..."
    override val tvFullscreen = "VOLLBILD"
    override val tvPageTitle = "TSM-Anzeigetafel"

    override val tvSection = "TV-Anzeigetafel"
    override val tvEnable = "Anzeigetafel auf TV oder Monitor"
    override val tvEnableHint = "Ein anderes Telefon (oder ein Computer oder ein Chromecast) zeigt den Spielstand live auf einem Monitor. Die Telefone müssen im selben Netz sein: im Hotspot eines der beiden."
    override val tvAddress = "Adresse der Anzeigetafel"
    override val tvNoNetwork = "Kein Netz: Schalte auf einem der beiden Telefone den Hotspot ein und verbinde das andere."
    override val tvScreens: (Int) -> String = { if (it == 0) "Keine Anzeigetafel verbunden" else if (it == 1) "1 Anzeigetafel verbunden" else "$it Anzeigetafeln verbunden" }
    override val tvQrHint = "Auf dem anderen Telefon: Tennis Score Manager öffnen und «Als Anzeigetafel verwenden» tippen (verbindet sich von selbst), oder den Code mit der Kamera scannen und im Browser öffnen."
    override val tvLook = "Aussehen der Anzeigetafel"
    override val tvTitle = "Text unten"
    override val tvTitleHint: (String) -> String = { if (it.isEmpty()) "Leer: kein Text" else "Leer: «$it» (Verein und Platz von Seite 1)" }
    override val tvColorOf: (String) -> String = { "Farbe von $it" }
    override val tvShowClock = "Matchdauer"
    override val tvShowTimers = "Aufschlaguhr und Pausen"
    override val tvShowSets = "Beendete Sätze"
    override val tvShowMessages = "Meldungen (Breakball, Satzball...)"
    override val tvShowServe = "Ball beim Aufschläger"
    override val tvGhost = "Ausgeschaltete Segmente sichtbar"
    override val tvPreview = "Vorschau auf diesem Telefon"
    override val tvChromecastHint = "Mit einem Chromecast: Auf dem Anzeigetafel-Telefon die Kachel «Streamen» in den Schnelleinstellungen verwenden («Bildschirm übertragen» bis Android 14, «Smart View» bei Samsung). Der Chromecast braucht ein Netz mit Internet: Schalte die mobilen Daten auf dem Hotspot-Telefon ein."

    override val displayMode = "Als Anzeigetafel verwenden"
    override val displayModeHint = "Dieses Telefon zeigt den Spielstand auf dem Monitor (HDMI-Kabel oder Chromecast)"
    override val displaySearching = "Suche das Telefon des Schiedsrichters…"
    override val displaySteps = "1. Schalte auf einem der beiden Telefone den Hotspot ein und verbinde das andere.\n2. Auf dem Telefon des Schiedsrichters: Seite 2 → «TV-Anzeigetafel» ein.\n3. Verbinde dieses Telefon mit dem Monitor (USB-C/HDMI-Kabel) oder übertrage den Bildschirm auf einen Chromecast."
    override val displayManual = "Adresse (z. B. 192.168.43.1:8080)"
    override val displayConnect = "Verbinden"
    override val displayNotFound = "Nicht gefunden. Prüfe, ob beide Telefone im selben Netz sind und die Anzeigetafel in der App des Schiedsrichters eingeschaltet ist."
    override val displayOnMonitor = "Die Anzeigetafel ist auf dem externen Monitor"
    override val displayShowHere = "Auch hier zeigen"
    override val displayBackAgain = "Zum Beenden noch einmal Zurück drücken"
    override val displayRetry = "Erneut suchen"
}
TSM_EOF

# ---------------------------------------------------------------- app/src/main/java/com/tennis/scoremanager/ui/StringsEs.kt
cat > "$DEST/app/src/main/java/com/tennis/scoremanager/ui/StringsEs.kt" << 'TSM_EOF'
package com.tennis.scoremanager.ui

import com.tennis.scoremanager.ble.BandProtocol

/** Español (España, terminología RFET). */
object EsStrings : Strings {
    override val playerTag: (Int) -> String = { "J$it" }
    override val datePattern = "EEEE, d 'de' MMMM 'de' yyyy"

    override val setupTitle = "Nuevo partido"
    override val setupSubtitle = "Configuración opcional: puedes dejarlo todo vacío y continuar."
    override val clubSection = "Club y pista"
    override val clubName = "Nombre del club de tenis"
    override val courtNumber = "Número de pista"
    override val singles = "Individual"
    override val doubles = "Dobles"
    override val doublesHint = "En dobles escribe dos nombres por pareja: el tanteo sigue las reglas ITF de dobles."
    override val player1 = "Jugador 1"
    override val player2 = "Jugador 2"
    override val playerName = "Nombre"
    override val clearFields = "Vaciar campos"
    override val next = "Siguiente"
    override val back = "Atrás"

    override val optionsTitle = "Modo y reglas"
    override val modeSection = "Modo de juego"
    override val modeReferee = "Juez"
    override val modeBands = "Pulseras"
    override val modeRefereeHint = "Los puntos se asignan desde el teléfono, como hace el juez de silla."
    override val modeBandsHint = "Cada jugador se anota el punto con KEY1 de su propio M5StickS3."
    override val requirements = "Requisitos"
    override val bluetooth = "Bluetooth"
    override val location = "Permiso de ubicación"
    override val locationServices = "Ubicación activada"
    override val enable = "Activar"
    override val allow = "Permitir"
    override val ok = "OK"
    override val bandFor: (String) -> String = { "Pulsera de $it" }
    override val noBand = "Ninguna"
    override val bandConnected = "Conectada"
    override val bandConnecting = "Conectando…"
    override val bandIdle = "No conectada"
    override val bandOff = "Apagada"
    override val battery = "Batería"
    override val bandCharging: (Int) -> String = { "Cargando $it %" }
    override val bandChargeFull = "Carga completa"
    override val autoSearch = "Búsqueda automática: enciende las pulseras (botón lateral) y se vinculan solas."
    override val autoSearchOff = "La búsqueda empieza cuando se cumplen los requisitos de arriba."
    override val identify = "Identificar"
    override val swapBands = "Intercambiar J1 ↔ J2"
    override val bandsOffAtEnd = "Apagar las pulseras al final del partido y al salir"
    override val bandsOffAtEndHint = "Se vuelven a encender con un clic en el botón lateral."
    override val bandNotReady = "Pulsera no conectada"

    override val bandSettings = "Ajustes de la pulsera"
    override val settingsShort = "Ajustes"
    override val bandSettingsNeedLink = "Conecta la pulsera para ver y cambiar sus ajustes."
    override val bandFirmwareOld = "El firmware de esta pulsera no tiene ajustes: carga TSM_Band.ino 2.0."
    override val bandFirmware: (String) -> String = { "Firmware $it" }
    override val bandName = "Nombre"
    override val brightness = "Brillo de la pantalla"
    override val scoreTime = "Marcador visible tras cada punto"
    override val scoreTimeHint = "El resumen de final de juego dura 2 segundos más."
    override val beeperVolume = "Volumen del pitido"
    override val mute = "Silencio"
    override val off = "No"
    override val flipDisplay = "Pantalla girada"
    override val flipDisplayHint = "Para llevar la pulsera en la otra muñeca."
    override val autoOff = "Apagado automático"
    override val pairTimeout = "Al encenderla, si no se conecta ningún teléfono"
    override val lostTimeout = "Si pierde la conexión con el teléfono"
    override val idleTimeout = "Si sigue conectada pero sin uso"
    override val estimateFull: (String) -> String = { "Autonomía estimada $it con la carga completa" }
    override val estimateNow: (String, Int) -> String = { h, p -> "$h con la carga actual ($p %)" }
    override val estimateBreakdown: (String, String, String, String) -> String = { tot, base, dsp, snd ->
        "Consumo medio $tot mA: placa y Bluetooth $base · pantalla $dsp · pitido $snd"
    }
    override val estimateMeasured = "Consumo base medido en esta pulsera durante el uso."
    override val estimateTheory = "Estimación teórica: tras 20 minutos de uso se corrige con el consumo medido."
    override val copyToOther = "Copiar a la otra pulsera"
    override val powerOff = "Apagar"
    override val bandPowerOffTitle = "¿Apagar la pulsera?"
    override val bandPowerOffText: (String) -> String = { "$it se apaga ahora mismo. Para volver a encenderla: un clic en el botón lateral." }
    override val languageSection = "Idioma"
    override val languageHint = "Se aplica a las pantallas, la voz del juez, el marcador de TV y las pulseras."
    override val audioSection = "Audio y voz"
    override val voiceCalls = "Cantos del juez por voz"
    override val voiceFiles: (Int, Int) -> String = { n, tot -> "Archivos generados: $n/$tot" }
    override val voiceFilesHint = "Todo funciona sin internet con las voces instaladas en el teléfono. Las grabaciones personalizadas (ZIP) siempre tienen prioridad sobre la síntesis de voz."
    override val generateVoice = "Generar archivos"
    override val generating: (Int, Int) -> String = { n, tot -> "Generando $n/$tot…" }
    override val voiceGenerationFailed = "Generación no completada: los archivos de voz anteriores no cambian."
    override val importVoiceZip = "Importar ZIP"
    override val voiceImportFailed = "ZIP ilegible o incompleto: no se ha cambiado ninguna grabación."
    override val deleteCustomVoice = "Quitar grabaciones"
    override val deleteCustomConfirmTitle = "¿Quitar las grabaciones?"
    override val deleteCustomConfirmText: (Int) -> String = { n -> "Las grabaciones personalizadas en español ($n) se borran del teléfono. Para recuperarlas, importa de nuevo el ZIP." }
    override val ttsEngine = "Motor de síntesis de voz"
    override val engineDefault = "Predeterminado del teléfono"
    override val ttsVoice = "Voz"
    override val voiceAuto = "Automática (la mejor sin conexión)"
    override val voiceName: (String) -> String = { "Voz $it" }
    override val online = "en línea"
    override val offline = "sin conexión"
    override val voiceFilesMode = "Usar archivos de audio pregenerados"
    override val voiceFilesModeHint = "Normalmente cada canto se lee en una sola frase (más natural). Actívalo para usar los archivos generados, por ejemplo para llevarte sin conexión una voz en línea."
    override val customRecordings: (Int, Int) -> String = { n, tot -> "Grabaciones personalizadas: $n/$tot" }
    override val voiceReadme: (String, String) -> String = { langs, formats ->
        "TENNIS SCORE MANAGER - ARCHIVOS DE VOZ\n" +
            "Pon las grabaciones en voice/<idioma>/ ($langs) con el nombre de la clave.\n" +
            "Formatos: $formats. Los nombres de los jugadores los lee siempre la síntesis de voz.\n" +
            "La carpeta tts/ contiene los archivos generados por la app: tus grabaciones tienen prioridad."
    }
    override val voiceReadmeKey = "CLAVE"
    override val testVoice = "Probar voz"
    override val stopVoiceTest = "Detener la prueba"
    override val ttsMissing = "La voz en español de la síntesis de voz no está instalada en el teléfono."
    override val installVoice = "Instalar voz"
    override val ttsEngineError = "Síntesis de voz no disponible: revisa el motor en los Ajustes de Android."
    override val openTtsSettings = "Abrir ajustes"
    override val formatSection = "Formato del partido"
    override val formatBestOfThree = "3 sets · tie-break a 7"
    override val formatBestOfThreeHint = "Al mejor de tres sets, tie-break con 6-6 en cada set."
    override val formatMatchTiebreak = "2 sets + súper tie-break a 10"
    override val formatMatchTiebreakHint = "Con un set iguales, el tercer set es un match tie-break a 10 puntos (2 de diferencia)."
    override val noAd = "Sin ventaja (punto decisivo)"
    override val noAdHint = "Con iguales se juega un solo punto: quien lo gana se lleva el juego."
    override val noAdShort = "Sin ventaja"
    override val coinToss = "Sorteo"
    override val tossCoin = "Lanzar la moneda"
    override val tossWinner: (String) -> String = { "Gana el sorteo: $it" }
    override val tossHint = "Quien gana elige: saque, resto o lado. Indica aquí la elección."
    override val serving = "Al servicio"
    override val courtSides = "Lados de la pista"
    override val umpireView = "Vista desde la silla del juez"
    override val swapSides = "Cambiar lados"
    override val firstServerOf: (String) -> String = { "Saca primero ($it)" }
    override val left = "Izquierda"
    override val right = "Derecha"
    override val net = "RED"
    override val umpireChair = "Juez de silla"

    override val locationDialogTitle = "Activa la ubicación"
    override val locationDialogText = "Si no activas la ubicación, el lugar no aparecerá en el resumen del partido."
    override val continueWithout = "Continuar sin ella"
    override val bandsRequiredTitle = "Bluetooth y ubicación obligatorios"
    override val bandsRequiredText = "Para usar las pulseras activa el Bluetooth, concede el permiso de ubicación y mantén la ubicación activada."
    override val bandsMissingTitle = "Pulseras no vinculadas"
    override val bandsMissingText: (List<String>) -> String = {
        (if (it.size > 1) "Faltan las pulseras de: " else "Falta la pulsera de: ") + it.joinToString(", ") + ". ¿Continuar de todos modos?"
    }
    override val continueAnyway = "Continuar"
    override val cancel = "Cancelar"

    override val startMatch = "INICIO DEL PARTIDO"
    override val startHint = "Pulsa el botón para empezar"
    override val startHintBands = "Pulsa el botón o KEY1 en una pulsera"
    override val startButton = "Empezar partido"
    override val resumeSaved = "Reanudar partido suspendido"
    override val noSavedMatches = "No hay partidos suspendidos guardados."
    override val savedMatchesTitle = "Partidos suspendidos"
    override val delete = "Eliminar"
    override val deleteSavedTitle = "¿Eliminar el partido suspendido?"
    override val deleteSavedText: (String) -> String = { "El partido $it ya no se podrá reanudar." }
    override val vs = "contra"

    override val matchTime = "Match Time"
    override val setsHeader = "SETS"
    override val gamesHeader = "JUEGOS"
    override val undoPoint = "Anular punto"
    override val suspend = "Suspender"
    override val resume = "Reanudar"
    override val audioOn = "Audio On"
    override val audioOff = "Audio Off"
    override val newMatch = "Nuevo partido"
    override val suspendedOverlay = "PARTIDO SUSPENDIDO"
    override val newMatchConfirmTitle = "¿Nuevo partido?"
    override val newMatchConfirmText = "El partido en curso queda guardado entre los partidos suspendidos y podrás reanudarlo."
    override val endDialogTitle = "Juego, set y partido"
    override val endDialogText: (String, String, Boolean) -> String = { name, score, team -> "${if (team) "Ganan" else "Gana"} $name\n$score" }
    override val matchConcluded = "Partido terminado"
    override val undoLastPoint = "Anular el último punto"
    override val serveOrderTitle: (Int) -> String = { "Orden de saque · set $it" }
    override val whoServesFirst: (String) -> String = { "¿Quién saca primero en $it?" }
    override val confirm = "Confirmar"
    override val backDisabled = "Durante el partido usa «Nuevo partido» o «Salir»."
    override val exit = "Salir"
    override val exitConfirmTitle = "¿Salir de la app?"
    override val exitConfirmText = "El partido queda guardado entre los partidos suspendidos y podrás reanudarlo."
    override val exitConfirmBands = "Las pulseras se apagarán."

    override val msgChangeEnds = "CAMBIO DE LADO"
    override val msgTiebreak = "TIE-BREAK"
    override val msgMatchTiebreak = "SÚPER TIE-BREAK"
    override val msgSetWon: (String) -> String = { "SET $it" }
    override val msgSetPoint = "BOLA DE SET"
    override val msgMatchPoint = "BOLA DE PARTIDO"
    override val msgBreakPoint = "BOLA DE BREAK"
    override val msgDecidingPoint = "PUNTO DECISIVO"
    override val msgPointUndone = "PUNTO ANULADO"
    override val msgSuspended = "PARTIDO SUSPENDIDO"
    override val msgResumed = "PARTIDO REANUDADO"
    override val msgBandConnected: (String) -> String = { "PULSERA $it CONECTADA" }
    override val msgBandLost: (String) -> String = { "PULSERA $it DESCONECTADA" }
    override val msgBandOff: (String, Int?) -> String = { n, why ->
        "PULSERA $n APAGADA" + when (why) {
            BandProtocol.OFF_IDLE -> " (SIN USO)"
            BandProtocol.OFF_BATTERY -> " (BATERÍA AGOTADA)"
            BandProtocol.OFF_TIMEOUT -> " (SIN TELÉFONO)"
            else -> ""
        }
    }
    override val msgBandBatteryLow: (String, Int) -> String = { n, p -> "PULSERA $n: BATERÍA $p %" }
    override val bandBatteryLow = "BATERÍA BAJA"
    override val autonomy: (String) -> String = { "autonomía ~$it" }
    override val bandsBattery = "Batería de las pulseras"

    override val notifChannel = "Partido en curso"
    override val notifText: (Boolean, Boolean) -> String = { bands, tv ->
        "Partido en curso · " + when {
            bands && tv -> "pulseras y marcador de TV activos"
            tv -> "marcador de TV activo"
            else -> "pulseras activas"
        }
    }
    override val notifTvOnly = "Marcador de TV activo"

    override val bandPaired = "VINCULADA A"
    override val bandPlay = "JUEGUEN"
    override val bandChangeEnds = "CAMBIO DE LADO"
    override val bandTiebreak = "TIE-BREAK"
    override val bandSet = "SET"
    override val bandSuspended = "SUSPENDIDO"
    override val bandGameSetMatch = "JUEGO SET PARTIDO"
    override val bandMatchOver = "FIN DEL PARTIDO"
    override val bandAppClosed = "APP CERRADA"
    override val bandOffFromApp = "APAGANDO"

    override val summaryTitle = "Partido terminado"
    override val winner = "Ganador"
    override val duration = "Duración"
    override val startTime = "Inicio"
    override val endTime = "Fin"
    override val date = "Fecha"
    override val club = "Club"
    override val court = "Pista"
    override val place = "Lugar"
    override val placeUnavailable = "Ubicación no disponible"
    override val format = "Formato"
    override val pointsWon = "Puntos ganados"
    override val gamesWon = "Juegos ganados"
    override val result = "Resultado"
    override val saveHistory = "Guardar en el historial"
    override val summaryLeaveTitle = "Resumen no guardado"
    override val summaryLeaveText = "No has guardado ni compartido el resumen del partido: después ya no podrás volver a verlo."
    override val share = "Compartir"
    override val saveDialogTitle = "Guardar en el historial"
    override val fileName = "Nombre"
    override val folder = "Carpeta"
    override val chooseFolder = "Elegir carpeta"
    override val defaultFolder = "Carpeta de la app (predeterminada)"
    override val formatReport = "Resumen (.txt)"
    override val formatData = "Datos del partido (.json)"
    override val formatImage = "Imagen (.png)"
    override val save = "Guardar"
    override val savedTo: (String) -> String = { "Guardado en $it" }
    override val saveError = "No se pudo guardar"
    override val shareSubject = "Resultado del partido de tenis"
    override val playerDefault: (Int) -> String = { "Jugador $it" }
    override val teamJoiner = " y "
    override val generatedWith = "Creado con Tennis Score Manager"

    override val tvGames = "JUEGOS"
    override val tvSet = "SETS"
    override val tvSec = "SEG"
    override val tvServe = "SAQUE"
    override val tvChangeover = "CAMBIO DE LADO"
    override val tvSetBreak = "DESCANSO"
    override val tvTiebreakBreak = "PAUSA"
    override val tvWaiting = "ESPERANDO EL PARTIDO"
    override val tvReady = "LISTOS PARA JUGAR"
    override val tvSuspended = "PARTIDO SUSPENDIDO"
    override val tvWinner = "GANADOR"
    override val tvMatchTiebreak = "SÚPER TIE-BREAK"
    override val tvLost = "CONEXIÓN PERDIDA - RECONECTANDO..."
    override val tvFullscreen = "PANTALLA COMPLETA"
    override val tvPageTitle = "Marcador TSM"

    override val tvSection = "Marcador en TV"
    override val tvEnable = "Marcador en TV o monitor"
    override val tvEnableHint = "Otro teléfono (o un ordenador, o un Chromecast) muestra el marcador en directo en un monitor. Los teléfonos deben estar en la misma red: el punto de acceso de uno de los dos."
    override val tvAddress = "Dirección del marcador"
    override val tvNoNetwork = "Sin red: activa el punto de acceso en uno de los dos teléfonos y conecta el otro."
    override val tvScreens: (Int) -> String = { if (it == 0) "Ningún marcador conectado" else if (it == 1) "1 marcador conectado" else "$it marcadores conectados" }
    override val tvQrHint = "En el otro teléfono: abre Tennis Score Manager y toca «Usar como marcador» (se conecta solo), o escanea el código con la cámara y ábrelo en el navegador."
    override val tvLook = "Aspecto del marcador"
    override val tvTitle = "Texto inferior"
    override val tvTitleHint: (String) -> String = { if (it.isEmpty()) "Vacío: sin texto" else "Vacío: «$it» (club y pista de la página 1)" }
    override val tvColorOf: (String) -> String = { "Color de $it" }
    override val tvShowClock = "Tiempo de partido"
    override val tvShowTimers = "Reloj de saque y descansos"
    override val tvShowSets = "Sets terminados"
    override val tvShowMessages = "Mensajes (bola de break, bola de set...)"
    override val tvShowServe = "Pelota junto a quien saca"
    override val tvGhost = "Segmentos apagados visibles"
    override val tvPreview = "Vista previa en este teléfono"
    override val tvChromecastHint = "Con un Chromecast: en el teléfono-marcador usa el botón «Enviar» de los ajustes rápidos («Enviar pantalla» hasta Android 14, «Smart View» en Samsung). El Chromecast necesita una red con internet: activa los datos móviles en el teléfono que hace de punto de acceso."

    override val displayMode = "Usar como marcador"
    override val displayModeHint = "Este teléfono muestra el marcador en el monitor (cable HDMI o Chromecast)"
    override val displaySearching = "Buscando el teléfono del juez…"
    override val displaySteps = "1. Activa el punto de acceso en uno de los dos teléfonos y conecta el otro.\n2. En el teléfono del juez: página 2 → «Marcador en TV» activado.\n3. Conecta este teléfono al monitor (cable USB-C/HDMI) o envía la pantalla a un Chromecast."
    override val displayManual = "Dirección (p. ej. 192.168.43.1:8080)"
    override val displayConnect = "Conectar"
    override val displayNotFound = "No encontrado. Comprueba que los dos teléfonos están en la misma red y que el marcador está activado en la app del juez."
    override val displayOnMonitor = "El marcador está en el monitor externo"
    override val displayShowHere = "Mostrar también aquí"
    override val displayBackAgain = "Pulsa atrás otra vez para salir"
    override val displayRetry = "Buscar de nuevo"
}
TSM_EOF

# ---------------------------------------------------------------- app/src/main/java/com/tennis/scoremanager/ui/StringsFr.kt
cat > "$DEST/app/src/main/java/com/tennis/scoremanager/ui/StringsFr.kt" << 'TSM_EOF'
package com.tennis.scoremanager.ui

import com.tennis.scoremanager.ble.BandProtocol

/** "de Sinner", "d'Alcaraz". */
private fun de(name: String) = if (name.firstOrNull()?.lowercaseChar()?.let { it in "aeiouyhàâäéèêëîïôöûüœ" } == true) "d'$name" else "de $name"

/** Français (France, terminologie FFT). */
object FrStrings : Strings {
    override val playerTag: (Int) -> String = { "J$it" }
    override val datePattern = "EEEE d MMMM yyyy"

    override val setupTitle = "Nouveau match"
    override val setupSubtitle = "Configuration facultative : vous pouvez tout laisser vide et continuer."
    override val clubSection = "Club et court"
    override val clubName = "Nom du club de tennis"
    override val courtNumber = "Numéro du court"
    override val singles = "Simple"
    override val doubles = "Double"
    override val doublesHint = "En double, saisissez deux noms par équipe : le décompte suit les règles ITF du double."
    override val player1 = "Joueur 1"
    override val player2 = "Joueur 2"
    override val playerName = "Nom"
    override val clearFields = "Effacer les champs"
    override val next = "Suivant"
    override val back = "Retour"

    override val optionsTitle = "Mode et règles"
    override val modeSection = "Mode de jeu"
    override val modeReferee = "Arbitre"
    override val modeBands = "Bracelets"
    override val modeRefereeHint = "Les points se donnent sur le téléphone, comme le fait l'arbitre de chaise."
    override val modeBandsHint = "Chaque joueur marque le point avec KEY1 de son propre M5StickS3."
    override val requirements = "Prérequis"
    override val bluetooth = "Bluetooth"
    override val location = "Autorisation de localisation"
    override val locationServices = "Localisation activée"
    override val enable = "Activer"
    override val allow = "Autoriser"
    override val ok = "OK"
    override val bandFor: (String) -> String = { "Bracelet ${de(it)}" }
    override val noBand = "Aucun"
    override val bandConnected = "Connecté"
    override val bandConnecting = "Connexion…"
    override val bandIdle = "Non connecté"
    override val bandOff = "Éteint"
    override val battery = "Batterie"
    override val bandCharging: (Int) -> String = { "En charge $it %" }
    override val bandChargeFull = "Charge terminée"
    override val autoSearch = "Recherche automatique : allumez les bracelets (bouton latéral), ils s'associent tout seuls."
    override val autoSearchOff = "La recherche démarre dès que les prérequis ci-dessus sont remplis."
    override val identify = "Identifier"
    override val swapBands = "Échanger J1 ↔ J2"
    override val bandsOffAtEnd = "Éteindre les bracelets en fin de match et en quittant"
    override val bandsOffAtEndHint = "Un clic sur le bouton latéral les rallume."
    override val bandNotReady = "Bracelet non connecté"

    override val bandSettings = "Réglages du bracelet"
    override val settingsShort = "Réglages"
    override val bandSettingsNeedLink = "Connectez le bracelet pour voir et modifier ses réglages."
    override val bandFirmwareOld = "Le firmware de ce bracelet n'a pas de réglages : chargez TSM_Band.ino 2.0."
    override val bandFirmware: (String) -> String = { "Firmware $it" }
    override val bandName = "Nom"
    override val brightness = "Luminosité de l'écran"
    override val scoreTime = "Score affiché après chaque point"
    override val scoreTimeHint = "Le résumé de fin de jeu reste affiché 2 secondes de plus."
    override val beeperVolume = "Volume du bip"
    override val mute = "Muet"
    override val off = "Non"
    override val flipDisplay = "Écran retourné"
    override val flipDisplayHint = "Pour porter le bracelet à l'autre poignet."
    override val autoOff = "Extinction automatique"
    override val pairTimeout = "À l'allumage, si aucun téléphone ne se connecte"
    override val lostTimeout = "S'il perd la connexion avec le téléphone"
    override val idleTimeout = "S'il reste connecté mais inactif"
    override val estimateFull: (String) -> String = { "Autonomie estimée $it avec une charge complète" }
    override val estimateNow: (String, Int) -> String = { h, p -> "$h avec la charge actuelle ($p %)" }
    override val estimateBreakdown: (String, String, String, String) -> String = { tot, base, dsp, snd ->
        "Consommation moyenne $tot mA : carte et Bluetooth $base · écran $dsp · bip $snd"
    }
    override val estimateMeasured = "Consommation de base mesurée sur ce bracelet pendant l'utilisation."
    override val estimateTheory = "Estimation théorique : après 20 minutes d'utilisation, elle se corrige avec la consommation mesurée."
    override val copyToOther = "Copier sur l'autre bracelet"
    override val powerOff = "Éteindre"
    override val bandPowerOffTitle = "Éteindre le bracelet ?"
    override val bandPowerOffText: (String) -> String = { "$it s'éteint tout de suite. Pour le rallumer : un clic sur le bouton latéral." }
    override val languageSection = "Langue"
    override val languageHint = "S'applique aux écrans, à la voix de l'arbitre, au tableau d'affichage TV et aux bracelets."
    override val audioSection = "Audio et voix"
    override val voiceCalls = "Annonces vocales de l'arbitre"
    override val voiceFiles: (Int, Int) -> String = { n, tot -> "Fichiers générés : $n/$tot" }
    override val voiceFilesHint = "Tout fonctionne sans internet avec les voix installées sur le téléphone. Les enregistrements personnalisés (ZIP) ont toujours la priorité sur la synthèse vocale."
    override val generateVoice = "Générer les fichiers"
    override val generating: (Int, Int) -> String = { n, tot -> "Génération $n/$tot…" }
    override val voiceGenerationFailed = "Génération non terminée : les fichiers vocaux précédents restent inchangés."
    override val importVoiceZip = "Importer un ZIP"
    override val voiceImportFailed = "ZIP illisible ou incomplet : aucun enregistrement n'a été modifié."
    override val deleteCustomVoice = "Supprimer les enregistrements"
    override val deleteCustomConfirmTitle = "Supprimer les enregistrements ?"
    override val deleteCustomConfirmText: (Int) -> String = { n -> "Les enregistrements personnalisés en français ($n) sont effacés du téléphone. Pour les récupérer, importez de nouveau le ZIP." }
    override val ttsEngine = "Moteur de synthèse vocale"
    override val engineDefault = "Par défaut du téléphone"
    override val ttsVoice = "Voix"
    override val voiceAuto = "Automatique (meilleure hors ligne)"
    override val voiceName: (String) -> String = { "Voix $it" }
    override val online = "en ligne"
    override val offline = "hors ligne"
    override val voiceFilesMode = "Utiliser des fichiers audio pré-générés"
    override val voiceFilesModeHint = "Par défaut, chaque annonce est lue d'une seule phrase (plus naturel). Activez cette option pour lire les fichiers générés, par exemple pour emporter hors ligne une voix en ligne."
    override val customRecordings: (Int, Int) -> String = { n, tot -> "Enregistrements personnalisés : $n/$tot" }
    override val voiceReadme: (String, String) -> String = { langs, formats ->
        "TENNIS SCORE MANAGER - FICHIERS VOCAUX\n" +
            "Placez les enregistrements dans voice/<langue>/ ($langs), nommés d'après la clé.\n" +
            "Formats : $formats. Les noms des joueurs sont toujours lus par la synthèse vocale.\n" +
            "Le dossier tts/ contient les fichiers générés par l'application : vos enregistrements ont la priorité."
    }
    override val voiceReadmeKey = "CLÉ"
    override val testVoice = "Tester la voix"
    override val stopVoiceTest = "Arrêter le test"
    override val ttsMissing = "La voix française de la synthèse vocale n'est pas installée sur ce téléphone."
    override val installVoice = "Installer la voix"
    override val ttsEngineError = "Synthèse vocale indisponible : vérifiez le moteur dans les Paramètres d'Android."
    override val openTtsSettings = "Ouvrir les paramètres"
    override val formatSection = "Format du match"
    override val formatBestOfThree = "3 manches · jeu décisif à 7"
    override val formatBestOfThreeHint = "Au meilleur des trois manches, jeu décisif à 6-6 dans chaque manche."
    override val formatMatchTiebreak = "2 manches + super jeu décisif à 10"
    override val formatMatchTiebreakHint = "À une manche partout, la troisième manche est un super jeu décisif en 10 points (2 points d'écart)."
    override val noAd = "No-Ad (point décisif)"
    override val noAdHint = "À 40-40, on joue un seul point : celui qui le gagne remporte le jeu."
    override val coinToss = "Tirage au sort (toss)"
    override val tossCoin = "Lancer la pièce"
    override val tossWinner: (String) -> String = { "Tirage au sort gagné par : $it" }
    override val tossHint = "Le gagnant choisit : service, relance ou côté. Indiquez ici le choix."
    override val serving = "Au service"
    override val courtSides = "Côtés du court"
    override val umpireView = "Vue depuis la chaise d'arbitre"
    override val swapSides = "Inverser les côtés"
    override val firstServerOf: (String) -> String = { "Sert en premier ($it)" }
    override val left = "Gauche"
    override val right = "Droite"
    override val net = "FILET"
    override val umpireChair = "Arbitre de chaise"

    override val locationDialogTitle = "Activer la localisation"
    override val locationDialogText = "Sans la localisation, le lieu ne pourra pas figurer dans le résumé du match."
    override val continueWithout = "Continuer sans"
    override val bandsRequiredTitle = "Bluetooth et localisation obligatoires"
    override val bandsRequiredText = "Pour utiliser les bracelets, activez le Bluetooth, autorisez la localisation et laissez-la activée."
    override val bandsMissingTitle = "Bracelets non associés"
    override val bandsMissingText: (List<String>) -> String = {
        (if (it.size > 1) "Il manque les bracelets de : " else "Il manque le bracelet de : ") + it.joinToString(", ") + ". Continuer quand même ?"
    }
    override val continueAnyway = "Continuer"
    override val cancel = "Annuler"

    override val startMatch = "DÉBUT DU MATCH"
    override val startHint = "Appuyez sur le bouton pour commencer"
    override val startHintBands = "Appuyez sur le bouton ou sur KEY1 d'un bracelet"
    override val startButton = "Commencer le match"
    override val resumeSaved = "Reprendre un match suspendu"
    override val noSavedMatches = "Aucun match suspendu enregistré."
    override val savedMatchesTitle = "Matchs suspendus"
    override val delete = "Supprimer"
    override val deleteSavedTitle = "Supprimer le match suspendu ?"
    override val deleteSavedText: (String) -> String = { "Le match $it ne pourra plus être repris." }
    override val vs = "contre"

    override val matchTime = "Match Time"
    override val setsHeader = "SETS"
    override val gamesHeader = "JEUX"
    override val undoPoint = "Annuler le point"
    override val suspend = "Suspendre"
    override val resume = "Reprendre"
    override val audioOn = "Audio On"
    override val audioOff = "Audio Off"
    override val newMatch = "Nouveau match"
    override val suspendedOverlay = "MATCH SUSPENDU"
    override val newMatchConfirmTitle = "Nouveau match ?"
    override val newMatchConfirmText = "Le match en cours reste enregistré parmi les matchs suspendus et pourra être repris."
    override val endDialogTitle = "Jeu, set et match"
    override val endDialogText: (String, String, Boolean) -> String = { name, score, _ -> "Victoire de $name\n$score" }
    override val matchConcluded = "Match terminé"
    override val undoLastPoint = "Annuler le dernier point"
    override val serveOrderTitle: (Int) -> String = { "Ordre de service · manche $it" }
    override val whoServesFirst: (String) -> String = { "Qui sert en premier chez $it ?" }
    override val confirm = "Confirmer"
    override val backDisabled = "Pendant le match, utilisez « Nouveau match » ou « Quitter »."
    override val exit = "Quitter"
    override val exitConfirmTitle = "Quitter l'application ?"
    override val exitConfirmText = "Le match reste enregistré parmi les matchs suspendus et pourra être repris."
    override val exitConfirmBands = "Les bracelets seront éteints."

    override val msgChangeEnds = "CHANGEMENT DE CÔTÉ"
    override val msgTiebreak = "JEU DÉCISIF"
    override val msgMatchTiebreak = "SUPER JEU DÉCISIF"
    override val msgSetWon: (String) -> String = { "MANCHE $it" }
    override val msgSetPoint = "BALLE DE SET"
    override val msgMatchPoint = "BALLE DE MATCH"
    override val msgBreakPoint = "BALLE DE BREAK"
    override val msgDecidingPoint = "POINT DÉCISIF"
    override val msgPointUndone = "POINT ANNULÉ"
    override val msgSuspended = "MATCH SUSPENDU"
    override val msgResumed = "REPRISE DU MATCH"
    override val msgBandConnected: (String) -> String = { "BRACELET $it CONNECTÉ" }
    override val msgBandLost: (String) -> String = { "BRACELET $it DÉCONNECTÉ" }
    override val msgBandOff: (String, Int?) -> String = { n, why ->
        "BRACELET $n ÉTEINT" + when (why) {
            BandProtocol.OFF_IDLE -> " (INACTIF)"
            BandProtocol.OFF_BATTERY -> " (BATTERIE VIDE)"
            BandProtocol.OFF_TIMEOUT -> " (AUCUN TÉLÉPHONE)"
            else -> ""
        }
    }
    override val msgBandBatteryLow: (String, Int) -> String = { n, p -> "BRACELET $n : BATTERIE $p %" }
    override val bandBatteryLow = "BATTERIE FAIBLE"
    override val autonomy: (String) -> String = { "autonomie ~$it" }
    override val bandsBattery = "Batterie des bracelets"

    override val notifChannel = "Match en cours"
    override val notifText: (Boolean, Boolean) -> String = { bands, tv ->
        "Match en cours · " + when {
            bands && tv -> "bracelets et tableau TV actifs"
            tv -> "tableau TV actif"
            else -> "bracelets actifs"
        }
    }
    override val notifTvOnly = "Tableau TV actif"

    override val bandPaired = "ASSOCIE A"
    override val bandPlay = "JOUEZ"
    override val bandChangeEnds = "CHANGEMENT DE COTE"
    override val bandTiebreak = "JEU DECISIF"
    override val bandSet = "MANCHE"
    override val bandSuspended = "SUSPENDU"
    override val bandGameSetMatch = "JEU SET ET MATCH"
    override val bandMatchOver = "FIN DU MATCH"
    override val bandAppClosed = "APPLI FERMEE"
    override val bandOffFromApp = "EXTINCTION"

    override val summaryTitle = "Match terminé"
    override val winner = "Vainqueur"
    override val duration = "Durée"
    override val startTime = "Début"
    override val endTime = "Fin"
    override val date = "Date"
    override val club = "Club"
    override val court = "Court"
    override val place = "Lieu"
    override val placeUnavailable = "Position non disponible"
    override val format = "Format"
    override val pointsWon = "Points gagnés"
    override val gamesWon = "Jeux gagnés"
    override val result = "Résultat"
    override val saveHistory = "Enregistrer dans l'historique"
    override val summaryLeaveTitle = "Résumé non enregistré"
    override val summaryLeaveText = "Vous n'avez ni enregistré ni partagé le résumé du match : vous ne pourrez plus le revoir."
    override val share = "Partager"
    override val saveDialogTitle = "Enregistrer dans l'historique"
    override val fileName = "Nom"
    override val folder = "Dossier"
    override val chooseFolder = "Choisir un dossier"
    override val defaultFolder = "Dossier de l'application (par défaut)"
    override val formatReport = "Compte rendu (.txt)"
    override val formatData = "Données du match (.json)"
    override val formatImage = "Image (.png)"
    override val save = "Enregistrer"
    override val savedTo: (String) -> String = { "Enregistré dans $it" }
    override val saveError = "Échec de l'enregistrement"
    override val shareSubject = "Résultat du match de tennis"
    override val playerDefault: (Int) -> String = { "Joueur $it" }
    override val teamJoiner = " et "
    override val generatedWith = "Créé avec Tennis Score Manager"

    override val tvGames = "JEUX"
    override val tvSet = "MANCHES"
    override val tvServe = "SERVICE"
    override val tvChangeover = "CHANGEMENT DE CÔTÉ"
    override val tvSetBreak = "PAUSE SET"
    override val tvTiebreakBreak = "PAUSE"
    override val tvWaiting = "EN ATTENTE DU MATCH"
    override val tvReady = "PRÊTS À JOUER"
    override val tvSuspended = "MATCH SUSPENDU"
    override val tvWinner = "VAINQUEUR"
    override val tvTiebreak = "JEU DÉCISIF"
    override val tvMatchTiebreak = "SUPER JEU DÉCISIF"
    override val tvLost = "CONNEXION PERDUE - RECONNEXION..."
    override val tvFullscreen = "PLEIN ÉCRAN"
    override val tvPageTitle = "Tableau d'affichage TSM"

    override val tvSection = "Tableau d'affichage TV"
    override val tvEnable = "Tableau d'affichage sur TV ou écran"
    override val tvEnableHint = "Un autre téléphone (ou un ordinateur, ou un Chromecast) affiche le score en direct sur un écran. Les téléphones doivent être sur le même réseau : le point d'accès de l'un des deux."
    override val tvAddress = "Adresse du tableau d'affichage"
    override val tvNoNetwork = "Aucun réseau : activez le point d'accès sur l'un des deux téléphones et connectez-y l'autre."
    override val tvScreens: (Int) -> String = { if (it == 0) "Aucun tableau connecté" else if (it == 1) "1 tableau connecté" else "$it tableaux connectés" }
    override val tvQrHint = "Sur l'autre téléphone : ouvrez Tennis Score Manager et touchez « Utiliser comme tableau » (il se connecte tout seul), ou scannez le code avec l'appareil photo et ouvrez-le dans le navigateur."
    override val tvLook = "Apparence du tableau"
    override val tvTitle = "Texte en bas"
    override val tvTitleHint: (String) -> String = { if (it.isEmpty()) "Vide : aucun texte" else "Vide : « $it » (club et court de la page 1)" }
    override val tvColorOf: (String) -> String = { "Couleur ${de(it)}" }
    override val tvShowClock = "Durée du match"
    override val tvShowTimers = "Chrono du service et des pauses"
    override val tvShowSets = "Manches terminées"
    override val tvShowMessages = "Messages (balle de break, balle de set...)"
    override val tvShowServe = "Balle à côté du serveur"
    override val tvGhost = "Segments éteints visibles"
    override val tvPreview = "Aperçu sur ce téléphone"
    override val tvChromecastHint = "Avec un Chromecast : sur le téléphone-tableau, utilisez le bouton « Caster » des réglages rapides (« Diffusion de l'écran » jusqu'à Android 14, « Smart View » sur Samsung). Le Chromecast a besoin d'un réseau avec internet : activez les données mobiles sur le téléphone qui partage la connexion."

    override val displayMode = "Utiliser comme tableau"
    override val displayModeHint = "Ce téléphone affiche le score sur l'écran (câble HDMI ou Chromecast)"
    override val displaySearching = "Recherche du téléphone de l'arbitre…"
    override val displaySteps = "1. Activez le point d'accès sur l'un des deux téléphones et connectez-y l'autre.\n2. Sur le téléphone de l'arbitre : page 2 → « Tableau d'affichage TV » activé.\n3. Branchez ce téléphone à l'écran (câble USB-C/HDMI) ou castez l'écran sur un Chromecast."
    override val displayManual = "Adresse (ex. 192.168.43.1:8080)"
    override val displayConnect = "Connecter"
    override val displayNotFound = "Introuvable. Vérifiez que les deux téléphones sont sur le même réseau et que le tableau est activé dans l'application de l'arbitre."
    override val displayOnMonitor = "Le tableau est sur l'écran externe"
    override val displayShowHere = "Afficher aussi ici"
    override val displayBackAgain = "Appuyez encore sur retour pour quitter"
    override val displayRetry = "Chercher à nouveau"
}
TSM_EOF

# ---------------------------------------------------------------- app/src/main/java/com/tennis/scoremanager/ui/StringsPt.kt
cat > "$DEST/app/src/main/java/com/tennis/scoremanager/ui/StringsPt.kt" << 'TSM_EOF'
package com.tennis.scoremanager.ui

import com.tennis.scoremanager.ble.BandProtocol

/**
 * Português: grafia do Brasil onde as variantes divergem (tela, salvar, quadra), termos das regras
 * da FPT e da CBT (árbitro de cadeira, sorteio, tie-break decisivo).
 */
object PtStrings : Strings {
    override val playerTag: (Int) -> String = { "J$it" }
    override val datePattern = "EEEE, d 'de' MMMM 'de' yyyy"

    override val setupTitle = "Nova partida"
    override val setupSubtitle = "Configuração opcional: você pode deixar tudo em branco e continuar."
    override val clubSection = "Clube e quadra"
    override val clubName = "Nome do clube de tênis"
    override val courtNumber = "Número da quadra"
    override val singles = "Simples"
    override val doubles = "Duplas"
    override val doublesHint = "Nas duplas, escreva dois nomes por equipe: a contagem segue as regras ITF de duplas."
    override val player1 = "Jogador 1"
    override val player2 = "Jogador 2"
    override val playerName = "Nome"
    override val clearFields = "Limpar campos"
    override val next = "Avançar"
    override val back = "Voltar"

    override val optionsTitle = "Modo e regras"
    override val modeSection = "Modo de jogo"
    override val modeReferee = "Árbitro"
    override val modeBands = "Pulseiras"
    override val modeRefereeHint = "Os pontos são marcados no telefone, como faz o árbitro de cadeira."
    override val modeBandsHint = "Cada jogador marca o ponto com o KEY1 do seu próprio M5StickS3."
    override val requirements = "Requisitos"
    override val bluetooth = "Bluetooth"
    override val location = "Permissão de localização"
    override val locationServices = "Localização ativada"
    override val enable = "Ativar"
    override val allow = "Permitir"
    override val ok = "OK"
    override val bandFor: (String) -> String = { "Pulseira de $it" }
    override val noBand = "Nenhuma"
    override val bandConnected = "Conectada"
    override val bandConnecting = "Conectando…"
    override val bandIdle = "Não conectada"
    override val bandOff = "Desligada"
    override val battery = "Bateria"
    override val bandCharging: (Int) -> String = { "Carregando $it%" }
    override val bandChargeFull = "Carga completa"
    override val autoSearch = "Busca automática: ligue as pulseiras (botão lateral), elas se pareiam sozinhas."
    override val autoSearchOff = "A busca começa quando os requisitos acima estiverem ok."
    override val identify = "Identificar"
    override val swapBands = "Trocar J1 ↔ J2"
    override val bandsOffAtEnd = "Desligar as pulseiras no fim da partida e ao sair"
    override val bandsOffAtEndHint = "Um clique no botão lateral volta a ligá-las."
    override val bandNotReady = "Pulseira não conectada"

    override val bandSettings = "Ajustes da pulseira"
    override val settingsShort = "Ajustes"
    override val bandSettingsNeedLink = "Conecte a pulseira para ver e mudar os ajustes."
    override val bandFirmwareOld = "O firmware desta pulseira não tem ajustes: carregue o TSM_Band.ino 2.0."
    override val bandFirmware: (String) -> String = { "Firmware $it" }
    override val bandName = "Nome"
    override val brightness = "Brilho da tela"
    override val scoreTime = "Placar visível após cada ponto"
    override val scoreTimeHint = "O resumo do fim do jogo fica 2 segundos a mais."
    override val beeperVolume = "Volume do bipe"
    override val mute = "Mudo"
    override val off = "Não"
    override val flipDisplay = "Tela invertida"
    override val flipDisplayHint = "Para usar a pulseira no outro pulso."
    override val autoOff = "Desligamento automático"
    override val pairTimeout = "Ao ligar, se nenhum telefone se conectar"
    override val lostTimeout = "Se perder a conexão com o telefone"
    override val idleTimeout = "Se ficar conectada mas sem uso"
    override val estimateFull: (String) -> String = { "Autonomia estimada $it com a carga completa" }
    override val estimateNow: (String, Int) -> String = { h, p -> "$h com a carga atual ($p%)" }
    override val estimateBreakdown: (String, String, String, String) -> String = { tot, base, dsp, snd ->
        "Consumo médio $tot mA: placa e Bluetooth $base · tela $dsp · bipe $snd"
    }
    override val estimateMeasured = "Consumo base medido nesta pulseira durante o uso."
    override val estimateTheory = "Estimativa teórica: após 20 minutos de uso ela é corrigida com o consumo medido."
    override val copyToOther = "Copiar para a outra pulseira"
    override val powerOff = "Desligar"
    override val bandPowerOffTitle = "Desligar a pulseira?"
    override val bandPowerOffText: (String) -> String = { "$it desliga agora. Para ligá-la de novo: um clique no botão lateral." }
    override val languageSection = "Idioma"
    override val languageHint = "Vale para as telas, a voz do árbitro, o placar na TV e as pulseiras."
    override val audioSection = "Áudio e voz"
    override val voiceCalls = "Anúncios do árbitro por voz"
    override val voiceFiles: (Int, Int) -> String = { n, tot -> "Arquivos gerados: $n/$tot" }
    override val voiceFilesHint = "Tudo funciona sem internet com as vozes instaladas no telefone. As gravações personalizadas (ZIP) sempre têm prioridade sobre a síntese de voz."
    override val generateVoice = "Gerar arquivos"
    override val generating: (Int, Int) -> String = { n, tot -> "Gerando $n/$tot…" }
    override val voiceGenerationFailed = "Geração não concluída: os arquivos de voz anteriores continuam iguais."
    override val importVoiceZip = "Importar ZIP"
    override val voiceImportFailed = "ZIP ilegível ou incompleto: nenhuma gravação foi alterada."
    override val deleteCustomVoice = "Remover gravações"
    override val deleteCustomConfirmTitle = "Remover as gravações?"
    override val deleteCustomConfirmText: (Int) -> String = { n -> "As gravações personalizadas em português ($n) são apagadas do telefone. Para recuperá-las, importe o ZIP de novo." }
    override val ttsEngine = "Mecanismo de síntese de voz"
    override val engineDefault = "Padrão do telefone"
    override val ttsVoice = "Voz"
    override val voiceAuto = "Automática (melhor offline)"
    override val voiceName: (String) -> String = { "Voz $it" }
    override val online = "online"
    override val offline = "offline"
    override val voiceFilesMode = "Usar arquivos de áudio pré-gerados"
    override val voiceFilesModeHint = "Normalmente cada anúncio é lido numa única frase (mais natural). Ative para usar os arquivos gerados, por exemplo para levar offline uma voz online."
    override val customRecordings: (Int, Int) -> String = { n, tot -> "Gravações personalizadas: $n/$tot" }
    override val voiceReadme: (String, String) -> String = { langs, formats ->
        "TENNIS SCORE MANAGER - ARQUIVOS DE VOZ\n" +
            "Coloque as gravações em voice/<idioma>/ ($langs) com o nome da chave.\n" +
            "Formatos: $formats. Os nomes dos jogadores são sempre lidos pela síntese de voz.\n" +
            "A pasta tts/ contém os arquivos gerados pelo app: suas gravações têm prioridade."
    }
    override val voiceReadmeKey = "CHAVE"
    override val testVoice = "Testar voz"
    override val stopVoiceTest = "Parar o teste"
    override val ttsMissing = "A voz em português da síntese de voz não está instalada no telefone."
    override val installVoice = "Instalar voz"
    override val ttsEngineError = "Síntese de voz indisponível: verifique o mecanismo nas Configurações do Android."
    override val openTtsSettings = "Abrir configurações"
    override val formatSection = "Formato da partida"
    override val formatBestOfThree = "3 sets · tie-break a 7"
    override val formatBestOfThreeHint = "Melhor de três sets, tie-break no 6-6 em cada set."
    override val formatMatchTiebreak = "2 sets + tie-break decisivo a 10"
    override val formatMatchTiebreakHint = "No 1-1 em sets, o terceiro set é um tie-break decisivo a 10 pontos (2 de diferença)."
    override val noAd = "Sem vantagem (ponto decisivo)"
    override val noAdHint = "No 40-40 joga-se um único ponto: quem ganhar leva o jogo."
    override val noAdShort = "Sem vantagem"
    override val coinToss = "Sorteio"
    override val tossCoin = "Jogar a moeda"
    override val tossWinner: (String) -> String = { "Venceu o sorteio: $it" }
    override val tossHint = "Quem vence escolhe: serviço, recepção ou lado. Marque a escolha aqui."
    override val serving = "No serviço"
    override val courtSides = "Lados da quadra"
    override val umpireView = "Vista da cadeira do árbitro"
    override val swapSides = "Inverter lados"
    override val firstServerOf: (String) -> String = { "Serve primeiro ($it)" }
    override val left = "Esquerda"
    override val right = "Direita"
    override val net = "REDE"
    override val umpireChair = "Árbitro de cadeira"

    override val locationDialogTitle = "Ative a localização"
    override val locationDialogText = "Sem a localização, o local não aparece no resumo da partida."
    override val continueWithout = "Continuar sem"
    override val bandsRequiredTitle = "Bluetooth e localização obrigatórios"
    override val bandsRequiredText = "Para usar as pulseiras, ative o Bluetooth, conceda a permissão de localização e mantenha a localização ativada."
    override val bandsMissingTitle = "Pulseiras não associadas"
    override val bandsMissingText: (List<String>) -> String = {
        (if (it.size > 1) "Faltam as pulseiras de: " else "Falta a pulseira de: ") + it.joinToString(", ") + ". Continuar mesmo assim?"
    }
    override val continueAnyway = "Continuar"
    override val cancel = "Cancelar"

    override val startMatch = "INÍCIO DA PARTIDA"
    override val startHint = "Toque no botão para começar"
    override val startHintBands = "Toque no botão ou aperte KEY1 numa pulseira"
    override val startButton = "Começar partida"
    override val resumeSaved = "Retomar partida suspensa"
    override val noSavedMatches = "Nenhuma partida suspensa salva."
    override val savedMatchesTitle = "Partidas suspensas"
    override val delete = "Excluir"
    override val deleteSavedTitle = "Excluir a partida suspensa?"
    override val deleteSavedText: (String) -> String = { "A partida $it não poderá mais ser retomada." }
    override val vs = "x"

    override val matchTime = "Match Time"
    override val setsHeader = "SETS"
    override val gamesHeader = "JOGOS"
    override val undoPoint = "Anular ponto"
    override val suspend = "Suspender"
    override val resume = "Retomar"
    override val audioOn = "Audio On"
    override val audioOff = "Audio Off"
    override val newMatch = "Nova partida"
    override val suspendedOverlay = "PARTIDA SUSPENSA"
    override val newMatchConfirmTitle = "Nova partida?"
    override val newMatchConfirmText = "A partida em andamento fica salva entre as partidas suspensas e você poderá retomá-la."
    override val endDialogTitle = "Jogo, set e partida"
    override val endDialogText: (String, String, Boolean) -> String = { name, score, _ -> "Vitória de $name\n$score" }
    override val matchConcluded = "Partida encerrada"
    override val undoLastPoint = "Anular o último ponto"
    override val serveOrderTitle: (Int) -> String = { "Ordem de serviço · set $it" }
    override val whoServesFirst: (String) -> String = { "Quem serve primeiro em $it?" }
    override val confirm = "Confirmar"
    override val backDisabled = "Durante a partida use «Nova partida» ou «Sair»."
    override val exit = "Sair"
    override val exitConfirmTitle = "Sair do app?"
    override val exitConfirmText = "A partida fica salva entre as partidas suspensas e você poderá retomá-la."
    override val exitConfirmBands = "As pulseiras serão desligadas."

    override val msgChangeEnds = "TROCA DE LADO"
    override val msgTiebreak = "TIE-BREAK"
    override val msgMatchTiebreak = "TIE-BREAK DECISIVO"
    override val msgSetWon: (String) -> String = { "SET $it" }
    override val msgSetPoint = "SET POINT"
    override val msgMatchPoint = "MATCH POINT"
    override val msgBreakPoint = "BREAK POINT"
    override val msgDecidingPoint = "PONTO DECISIVO"
    override val msgPointUndone = "PONTO ANULADO"
    override val msgSuspended = "PARTIDA SUSPENSA"
    override val msgResumed = "PARTIDA RETOMADA"
    override val msgBandConnected: (String) -> String = { "PULSEIRA $it CONECTADA" }
    override val msgBandLost: (String) -> String = { "PULSEIRA $it DESCONECTADA" }
    override val msgBandOff: (String, Int?) -> String = { n, why ->
        "PULSEIRA $n DESLIGADA" + when (why) {
            BandProtocol.OFF_IDLE -> " (SEM USO)"
            BandProtocol.OFF_BATTERY -> " (BATERIA VAZIA)"
            BandProtocol.OFF_TIMEOUT -> " (SEM TELEFONE)"
            else -> ""
        }
    }
    override val msgBandBatteryLow: (String, Int) -> String = { n, p -> "PULSEIRA $n: BATERIA $p%" }
    override val bandBatteryLow = "BATERIA FRACA"
    override val autonomy: (String) -> String = { "autonomia ~$it" }
    override val bandsBattery = "Bateria das pulseiras"

    override val notifChannel = "Partida em andamento"
    override val notifText: (Boolean, Boolean) -> String = { bands, tv ->
        "Partida em andamento · " + when {
            bands && tv -> "pulseiras e placar na TV ativos"
            tv -> "placar na TV ativo"
            else -> "pulseiras ativas"
        }
    }
    override val notifTvOnly = "Placar na TV ativo"

    override val bandPaired = "PAREADA COM"
    override val bandPlay = "JOGUEM"
    override val bandChangeEnds = "TROCA DE LADO"
    override val bandTiebreak = "TIE-BREAK"
    override val bandSet = "SET"
    override val bandSuspended = "SUSPENSA"
    override val bandGameSetMatch = "JOGO SET PARTIDA"
    override val bandMatchOver = "FIM DA PARTIDA"
    override val bandAppClosed = "APP FECHADO"
    override val bandOffFromApp = "DESLIGANDO"

    override val summaryTitle = "Partida encerrada"
    override val winner = "Vencedor"
    override val duration = "Duração"
    override val startTime = "Início"
    override val endTime = "Fim"
    override val date = "Data"
    override val club = "Clube"
    override val court = "Quadra"
    override val place = "Local"
    override val placeUnavailable = "Localização indisponível"
    override val format = "Formato"
    override val pointsWon = "Pontos ganhos"
    override val gamesWon = "Jogos ganhos"
    override val result = "Resultado"
    override val saveHistory = "Salvar no histórico"
    override val summaryLeaveTitle = "Resumo não salvo"
    override val summaryLeaveText = "Você não salvou nem compartilhou o resumo da partida: depois não poderá mais vê-lo."
    override val share = "Compartilhar"
    override val saveDialogTitle = "Salvar no histórico"
    override val fileName = "Nome"
    override val folder = "Pasta"
    override val chooseFolder = "Escolher pasta"
    override val defaultFolder = "Pasta do app (padrão)"
    override val formatReport = "Relatório (.txt)"
    override val formatData = "Dados da partida (.json)"
    override val formatImage = "Imagem (.png)"
    override val save = "Salvar"
    override val savedTo: (String) -> String = { "Salvo em $it" }
    override val saveError = "Não foi possível salvar"
    override val shareSubject = "Resultado da partida de tênis"
    override val playerDefault: (Int) -> String = { "Jogador $it" }
    override val teamJoiner = " e "
    override val generatedWith = "Criado com Tennis Score Manager"

    override val tvGames = "JOGOS"
    override val tvSet = "SETS"
    override val tvSec = "SEG"
    override val tvServe = "SERVIÇO"
    override val tvChangeover = "TROCA DE LADO"
    override val tvSetBreak = "INTERVALO"
    override val tvTiebreakBreak = "PAUSA"
    override val tvWaiting = "AGUARDANDO A PARTIDA"
    override val tvReady = "PRONTOS PARA JOGAR"
    override val tvSuspended = "PARTIDA SUSPENSA"
    override val tvWinner = "VENCEDOR"
    override val tvMatchTiebreak = "TIE-BREAK DECISIVO"
    override val tvLost = "CONEXÃO PERDIDA - RECONECTANDO..."
    override val tvFullscreen = "TELA CHEIA"
    override val tvPageTitle = "Placar TSM"

    override val tvSection = "Placar na TV"
    override val tvEnable = "Placar na TV ou monitor"
    override val tvEnableHint = "Outro telefone (ou um computador, ou um Chromecast) mostra o placar ao vivo num monitor. Os telefones precisam estar na mesma rede: o hotspot de um dos dois."
    override val tvAddress = "Endereço do placar"
    override val tvNoNetwork = "Sem rede: ative o hotspot num dos dois telefones e conecte o outro."
    override val tvScreens: (Int) -> String = { if (it == 0) "Nenhum placar conectado" else if (it == 1) "1 placar conectado" else "$it placares conectados" }
    override val tvQrHint = "No outro telefone: abra o Tennis Score Manager e toque em «Usar como placar» (conecta sozinho), ou leia o código com a câmera e abra no navegador."
    override val tvLook = "Aparência do placar"
    override val tvTitle = "Texto embaixo"
    override val tvTitleHint: (String) -> String = { if (it.isEmpty()) "Vazio: sem texto" else "Vazio: «$it» (clube e quadra da página 1)" }
    override val tvColorOf: (String) -> String = { "Cor de $it" }
    override val tvShowClock = "Tempo de partida"
    override val tvShowTimers = "Relógio de serviço e pausas"
    override val tvShowSets = "Sets terminados"
    override val tvShowMessages = "Mensagens (break point, set point...)"
    override val tvShowServe = "Bola ao lado de quem serve"
    override val tvGhost = "Segmentos apagados visíveis"
    override val tvPreview = "Prévia neste telefone"
    override val tvChromecastHint = "Com um Chromecast: no telefone-placar use o botão de transmissão das configurações rápidas («Transmitir», «Transmissão» ou «Transmissão de tela», conforme a versão do Android; «Smart View» nos Samsung). O Chromecast precisa de uma rede com internet: ative os dados móveis no telefone que faz o hotspot."

    override val displayMode = "Usar como placar"
    override val displayModeHint = "Este telefone mostra o placar no monitor (cabo HDMI ou Chromecast)"
    override val displaySearching = "Procurando o telefone do árbitro…"
    override val displaySteps = "1. Ative o hotspot num dos dois telefones e conecte o outro.\n2. No telefone do árbitro: página 2 → «Placar na TV» ativado.\n3. Ligue este telefone ao monitor (cabo USB-C/HDMI) ou transmita a tela para um Chromecast."
    override val displayManual = "Endereço (ex. 192.168.43.1:8080)"
    override val displayConnect = "Conectar"
    override val displayNotFound = "Não encontrado. Verifique se os dois telefones estão na mesma rede e se o placar está ativado no app do árbitro."
    override val displayOnMonitor = "O placar está no monitor externo"
    override val displayShowHere = "Mostrar aqui também"
    override val displayBackAgain = "Toque em voltar de novo para sair"
    override val displayRetry = "Procurar de novo"
}
TSM_EOF

# ---------------------------------------------------------------- app/src/main/java/com/tennis/scoremanager/ui/Theme.kt
cat > "$DEST/app/src/main/java/com/tennis/scoremanager/ui/Theme.kt" << 'TSM_EOF'
package com.tennis.scoremanager.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.tennis.scoremanager.model.Side

/** Colori fissi dell'app: giallo = Giocatore 1, rosso = Giocatore 2. */
object TsmColors {
    val Background = Color(0xFF0B1220)
    val Surface = Color(0xFF151D2E)
    val SurfaceHigh = Color(0xFF1E2940)
    val Outline = Color(0xFF33415C)
    val Ball = Color(0xFFC6F432)
    val OnBall = Color(0xFF15210A)
    val Player1 = Color(0xFFFFD600)
    val OnPlayer1 = Color(0xFF231C00)
    val Player2 = Color(0xFFE53935)
    val OnPlayer2 = Color(0xFFFFFFFF)
    val Orange = Color(0xFFFF9800)
    val OnOrange = Color(0xFF231300)
    val Danger = Color(0xFFFF3B30)
    val TextMain = Color(0xFFF1F5FB)
    val TextDim = Color(0xFF9AA8BD)
    val Court = Color(0xFF1F7A4D)
    val CourtLine = Color(0xFFE8F5E9)
    val Ok = Color(0xFF4CAF50)

    fun player(side: Side) = if (side == Side.P1) Player1 else Player2
    fun onPlayer(side: Side) = if (side == Side.P1) OnPlayer1 else OnPlayer2
}

private val scheme = darkColorScheme(
    primary = TsmColors.Ball,
    onPrimary = TsmColors.OnBall,
    secondary = TsmColors.Orange,
    onSecondary = TsmColors.OnOrange,
    background = TsmColors.Background,
    onBackground = TsmColors.TextMain,
    surface = TsmColors.Surface,
    onSurface = TsmColors.TextMain,
    surfaceVariant = TsmColors.SurfaceHigh,
    onSurfaceVariant = TsmColors.TextDim,
    surfaceContainer = TsmColors.Surface,
    surfaceContainerHigh = TsmColors.SurfaceHigh,
    surfaceContainerHighest = TsmColors.SurfaceHigh,
    outline = TsmColors.Outline,
    error = TsmColors.Danger,
)

private val typography = Typography(
    headlineMedium = TextStyle(fontWeight = FontWeight.Black, fontSize = 26.sp, letterSpacing = 0.5.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
)

@Composable
fun TsmTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
TSM_EOF

# ---------------------------------------------------------------- app/src/main/java/com/tennis/scoremanager/ui/TvSection.kt
cat > "$DEST/app/src/main/java/com/tennis/scoremanager/ui/TvSection.kt" << 'TSM_EOF'
package com.tennis.scoremanager.ui

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Grid4x4
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SportsTennis
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.tennis.scoremanager.MatchController
import com.tennis.scoremanager.model.Side
import com.tennis.scoremanager.tv.TvColors
import com.tennis.scoremanager.tv.TvSnapshots

/** Pagina 2: tabellone su TV (acceso/spento, indirizzo e QR, aspetto). */
@Composable
fun TvSection(c: MatchController) {
    val s = LocalStrings.current
    val tv by c.tv.collectAsState()
    val su by c.setup.collectAsState()
    var lookOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current

    SectionCard(s.tvSection, Icons.Filled.Tv) {
        SwitchRow(Icons.Filled.Tv, s.tvEnable, s.tvEnableHint, tv.enabled) { v -> c.updateTv { it.copy(enabled = v) } }
        if (!tv.enabled) return@SectionCard
        TvStatus(c)
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Filled.Cast, null, tint = TsmColors.TextDim, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(s.tvChromecastHint, color = TsmColors.TextDim, fontSize = 13.sp)
        }
        val port by c.tvServer.port.collectAsState()
        GhostButton(s.tvPreview, Icons.Filled.OpenInBrowser, {
            port?.let { p -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("http://127.0.0.1:$p/"))) } }
        }, Modifier.fillMaxWidth(), enabled = port != null)

        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { lookOpen = !lookOpen }.padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Palette, null, tint = TsmColors.TextDim)
            Spacer(Modifier.width(12.dp))
            Text(s.tvLook, color = TsmColors.TextMain, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Icon(if (lookOpen) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = TsmColors.TextDim)
        }
        if (lookOpen) {
            val names = c.names(su)
            ColorRow(s.tvColorOf(names.short(Side.P1)), tv.color1) { hex -> c.updateTv { it.copy(color1 = hex) } }
            ColorRow(s.tvColorOf(names.short(Side.P2)), tv.color2) { hex -> c.updateTv { it.copy(color2 = hex) } }
            SwitchRow(Icons.Filled.WatchLater, s.tvShowClock, null, tv.showClock) { v -> c.updateTv { it.copy(showClock = v) } }
            SwitchRow(Icons.Filled.Timer, s.tvShowTimers, null, tv.showTimers) { v -> c.updateTv { it.copy(showTimers = v) } }
            SwitchRow(Icons.Filled.FormatListNumbered, s.tvShowSets, null, tv.showSets) { v -> c.updateTv { it.copy(showSets = v) } }
            SwitchRow(Icons.Filled.Message, s.tvShowMessages, null, tv.showMessages) { v -> c.updateTv { it.copy(showMessages = v) } }
            SwitchRow(Icons.Filled.SportsTennis, s.tvShowServe, null, tv.showServe) { v -> c.updateTv { it.copy(showServe = v) } }
            SwitchRow(Icons.Filled.Grid4x4, s.tvGhost, null, tv.ghostSegments) { v -> c.updateTv { it.copy(ghostSegments = v) } }
            OutlinedTextField(
                value = tv.title,
                onValueChange = { v -> c.updateTv { it.copy(title = v.take(60)) } },
                label = { Text(s.tvTitle) },
                leadingIcon = { Icon(Icons.Filled.Title, null) },
                supportingText = { Text(s.tvTitleHint(TvSnapshots.defaultTitle(su, s))) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Indirizzo, QR da inquadrare e tabelloni collegati (anche dalla schermata della partita). */
@Composable
fun TvStatus(c: MatchController) {
    val s = LocalStrings.current
    val addresses by c.tvServer.addresses.collectAsState()
    val port by c.tvServer.port.collectAsState()
    val clients by c.tvServer.clients.collectAsState()
    val url = port?.let { p -> addresses.firstOrNull()?.let { "http://$it:$p/" } }
    if (url == null) {
        Text(s.tvNoNetwork, color = TsmColors.Orange, fontSize = 14.sp)
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            QrCode(url, 120.dp)
            Spacer(Modifier.width(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(s.tvAddress, color = TsmColors.TextDim, fontSize = 12.sp)
                Text(url.removePrefix("http://").removeSuffix("/"), color = TsmColors.Ball, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                // altre reti (es. hotspot e Wi-Fi insieme)
                for (a in addresses.drop(1)) Text("$a:$port", color = TsmColors.TextDim, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                Text(s.tvScreens(clients), color = if (clients > 0) TsmColors.Ok else TsmColors.TextDim, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
    Text(s.tvQrHint, color = TsmColors.TextDim, fontSize = 13.sp)
}

/** Chip "TV" nella schermata della partita: tabelloni collegati; toccandolo si rivede il QR. */
@Composable
fun TvChip(c: MatchController) {
    val s = LocalStrings.current
    val tv by c.tv.collectAsState()
    if (!tv.enabled) return
    val clients by c.tvServer.clients.collectAsState()
    var open by remember { mutableStateOf(false) }
    Pill("TV · $clients", if (clients > 0) TsmColors.Ok else TsmColors.SurfaceHigh, if (clients > 0) Color.White else TsmColors.TextDim, Icons.Filled.Tv) { open = true }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            icon = { Icon(Icons.Filled.Tv, null, tint = TsmColors.Ball) },
            title = { Text(s.tvSection) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { TvStatus(c) } },
            confirmButton = { TextButton(onClick = { open = false }) { Text(s.ok) } },
        )
    }
}

@Composable
private fun ColorRow(label: String, selected: String, onPick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = TsmColors.TextMain, fontSize = 14.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (hex in TvColors.palette) {
                val on = hex.equals(selected, ignoreCase = true)
                Box(
                    Modifier.size(28.dp).clip(CircleShape).background(Color(android.graphics.Color.parseColor(hex)))
                        .border(if (on) 3.dp else 1.dp, if (on) Color.White else TsmColors.Outline, CircleShape)
                        .clickable { onPick(hex) },
                )
            }
        }
    }
}

/** Codice QR (bianco e nero, con margine) generato sul telefono: niente internet. */
@Composable
fun QrCode(text: String, size: Dp) {
    val bitmap = remember(text) {
        val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 2))
        val px = IntArray(m.width * m.height) { i -> if (m[i % m.width, i / m.width]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
        Bitmap.createBitmap(px, m.width, m.height, Bitmap.Config.ARGB_8888).asImageBitmap()
    }
    Image(
        bitmap, null,
        filterQuality = FilterQuality.None,
        modifier = Modifier.size(size).clip(RoundedCornerShape(8.dp)).background(Color.White),
    )
}
TSM_EOF

# ---------------------------------------------------------------- app/src/main/java/com/tennis/scoremanager/ui/screens/MatchScreen.kt
cat > "$DEST/app/src/main/java/com/tennis/scoremanager/ui/screens/MatchScreen.kt" << 'TSM_EOF'
package com.tennis.scoremanager.ui.screens

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.SportsTennis
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.tennis.scoremanager.CountdownKind
import com.tennis.scoremanager.CountdownUi
import com.tennis.scoremanager.MatchController
import com.tennis.scoremanager.ble.BandInfo
import com.tennis.scoremanager.ble.LinkState
import com.tennis.scoremanager.data.Names
import com.tennis.scoremanager.data.PlayMode
import com.tennis.scoremanager.data.Reports
import com.tennis.scoremanager.model.MatchState
import com.tennis.scoremanager.model.Side
import com.tennis.scoremanager.model.TiebreakKind
import com.tennis.scoremanager.ui.BandSettingsPanel
import com.tennis.scoremanager.ui.ConfirmDialog
import com.tennis.scoremanager.ui.LocalStrings
import com.tennis.scoremanager.ui.Pill
import com.tennis.scoremanager.ui.SegOption
import com.tennis.scoremanager.ui.Segmented
import com.tennis.scoremanager.ui.Strings
import com.tennis.scoremanager.ui.TsmColors
import com.tennis.scoremanager.ui.TvChip
import com.tennis.scoremanager.ui.rememberArmed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchScreen(c: MatchController) {
    val s = LocalStrings.current
    val context = LocalContext.current
    val activity = context as? Activity
    val liveMatch by c.live.collectAsState()
    val lm = liveMatch ?: return
    val clock by c.clockMs.collectAsState()
    val cd by c.countdown.collectAsState()
    val msg by c.message.collectAsState()
    val end by c.endDialog.collectAsState()
    val serveOrder by c.serveOrderPrompt.collectAsState()
    val o by c.options.collectAsState()
    val bands by c.ble.bands.collectAsState()
    val names = remember(lm.record.setup, s) { Names(lm.record.setup, s) }
    val state = lm.state
    val suspended = lm.record.suspended
    var confirmNew by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }
    var bandSheet by remember { mutableStateOf<Side?>(null) }

    BackHandler { Toast.makeText(context, s.backDisabled, Toast.LENGTH_SHORT).show() }

    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TimersRow(clock, cd, s) { TvChip(c) }
        if (o.mode == PlayMode.BANDS) BandStatusRow(bands, c.bandBattery.collectAsState().value) { bandSheet = it }
        MessageBox(msg)
        Scoreboard(state, names, s)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            PointButtons(state, names, s, enabled = !suspended && !state.isFinished) { c.awardPoint(it) }
            if (suspended) {
                Box(
                    Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)).background(Color(0xCC0B1220)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.Pause, null, tint = TsmColors.Orange, modifier = Modifier.size(56.dp))
                        Text(s.suspendedOverlay, color = TsmColors.Orange, fontSize = 26.sp, lineHeight = 30.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ControlButton(Icons.AutoMirrored.Filled.Undo, s.undoPoint, Modifier.weight(1f)) { c.undo() }
            ControlButton(
                if (suspended) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                if (suspended) s.resume else s.suspend,
                Modifier.weight(1f),
                highlight = suspended,
            ) { c.toggleSuspend() }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // Audio solo icona (barrata quando è spento): lascia spazio a "Nuova partita" ed "Esci".
            ControlButton(
                if (o.audio) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                null,
                Modifier.width(64.dp),
                contentDescription = if (o.audio) s.audioOn else s.audioOff,
            ) { c.toggleAudio() }
            ControlButton(Icons.Filled.AddCircle, s.newMatch, Modifier.weight(1.3f)) { confirmNew = true }
            ControlButton(Icons.AutoMirrored.Filled.ExitToApp, s.exit, Modifier.weight(1f)) { confirmExit = true }
        }
    }

    if (confirmExit) {
        ConfirmDialog(
            icon = Icons.AutoMirrored.Filled.ExitToApp,
            title = s.exitConfirmTitle,
            text = s.exitConfirmText + if (o.mode == PlayMode.BANDS && o.bandsOffAtEnd) " " + s.exitConfirmBands else "",
            confirm = s.exit,
            onConfirm = {
                confirmExit = false
                activity?.let { c.exitApp(it) }
            },
            onDismiss = { confirmExit = false },
        )
    }

    bandSheet?.let { first ->
        var side by remember(first) { mutableStateOf(first) }
        ModalBottomSheet(onDismissRequest = { bandSheet = null }, containerColor = TsmColors.Surface) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(s.bandSettings, color = TsmColors.TextMain, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Segmented(
                    Side.entries.map { SegOption(names.short(it), Icons.Filled.Watch, TsmColors.player(it), TsmColors.onPlayer(it)) },
                    selected = side.ordinal,
                    onSelect = { side = Side.entries[it] },
                )
                BandSettingsPanel(c, side)
            }
        }
    }

    if (end && state.isFinished) {
        val w = state.winner ?: Side.P1
        val armed = rememberArmed()
        AlertDialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            icon = { Icon(Icons.Filled.EmojiEvents, null, tint = TsmColors.player(w), modifier = Modifier.size(40.dp)) },
            title = { Text(s.endDialogTitle, fontWeight = FontWeight.Black) },
            text = {
                Text(
                    s.endDialogText(names.side(w), Reports.scoreLine(state, w), state.rules.doubles),
                    fontSize = 18.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(
                    onClick = { if (armed) c.confirmEnd() },
                    colors = ButtonDefaults.buttonColors(containerColor = TsmColors.Ball, contentColor = TsmColors.OnBall),
                ) {
                    Icon(Icons.Filled.Check, null)
                    Spacer(Modifier.width(6.dp))
                    Text(s.matchConcluded, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { if (armed) c.undo() }) {
                    Icon(Icons.AutoMirrored.Filled.Undo, null, tint = TsmColors.TextMain)
                    Spacer(Modifier.width(6.dp))
                    Text(s.undoLastPoint, color = TsmColors.TextMain)
                }
            },
        )
    }

    if (confirmNew) {
        ConfirmDialog(
            icon = Icons.Filled.AddCircle,
            title = s.newMatchConfirmTitle,
            text = s.newMatchConfirmText,
            confirm = s.newMatch,
            onConfirm = {
                confirmNew = false
                c.newMatch()
            },
            onDismiss = { confirmNew = false },
        )
    }

    if (serveOrder && state.rules.doubles && !end) {
        var p1 by remember(state.setNumber) { mutableIntStateOf(state.order1) }
        var p2 by remember(state.setNumber) { mutableIntStateOf(state.order2) }
        AlertDialog(
            onDismissRequest = { c.setServeOrder(p1, p2) },
            icon = { Icon(Icons.Filled.SportsTennis, null, tint = TsmColors.Ball) },
            title = { Text(s.serveOrderTitle(state.setNumber)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (side in listOf(state.setStartServer, state.setStartServer.other)) {
                        Text(s.whoServesFirst(names.short(side)), color = TsmColors.player(side), fontWeight = FontWeight.Bold)
                        Segmented(
                            names.players(side).map { SegOption(it, null, TsmColors.player(side), TsmColors.onPlayer(side)) },
                            selected = if (side == Side.P1) p1 else p2,
                            onSelect = { i -> if (side == Side.P1) p1 = i else p2 = i },
                        )
                    }
                }
            },
            confirmButton = { Button(onClick = { c.setServeOrder(p1, p2) }) { Text(s.confirm) } },
        )
    }
}

private fun formatClock(ms: Long): String {
    val t = ms / 1000
    return String.format(java.util.Locale.ROOT, "%02d:%02d:%02d", t / 3600, (t / 60) % 60, t % 60)
}

private fun formatCountdown(sec: Int): String = if (sec >= 60) String.format(java.util.Locale.ROOT, "%d:%02d", sec / 60, sec % 60) else sec.toString()

@Composable
private fun TimersRow(clock: Long, cd: CountdownUi?, s: Strings, middle: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column {
            Text(s.matchTime, color = TsmColors.TextDim, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(formatClock(clock), color = TsmColors.TextMain, fontSize = 30.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.weight(1f))
        Box(Modifier.padding(top = 10.dp)) { middle() }
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            val label = when (cd?.kind) {
                CountdownKind.CHANGEOVER -> s.changeoverTime
                CountdownKind.SET_BREAK -> s.setBreakTime
                CountdownKind.TIEBREAK_BREAK -> s.tiebreakTime
                else -> s.shotClock
            }
            val red = cd != null && cd.seconds <= 5
            val color by animateColorAsState(if (red) TsmColors.Danger else TsmColors.TextMain, label = "cd")
            Text(label, color = if (cd?.kind == CountdownKind.SHOT_CLOCK || cd == null) TsmColors.TextDim else TsmColors.Orange, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(cd?.let { formatCountdown(it.seconds) } ?: "--", color = color, fontSize = 30.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }
    }
}

/** Stato dei braccialetti; toccandone uno si aprono le sue impostazioni. */
@Composable
private fun BandStatusRow(bands: Map<Side, BandInfo>, battery: Map<Side, com.tennis.scoremanager.BandBattery>, onOpen: (Side) -> Unit) {
    val s = LocalStrings.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        for (side in Side.entries) {
            val b = bands[side]
            val (icon, ok) = when (b?.state) {
                LinkState.READY -> Icons.Filled.BluetoothConnected to true
                LinkState.POWERED_OFF -> Icons.Filled.PowerSettingsNew to false
                else -> Icons.Filled.BluetoothDisabled to false
            }
            val bat = battery[side]
            val low = bat != null && !bat.charging && bat.percent <= 20
            Pill(
                s.playerTag(side.ordinal + 1) +
                    ((bat?.percent ?: b?.battery)?.let { " · $it%" } ?: "") +
                    (if (bat?.charging == true || b?.charging == true) " ⚡" else "") +
                    (bat?.leftText()?.let { " · $it" } ?: ""),
                when {
                    low -> TsmColors.Danger
                    ok -> TsmColors.player(side)
                    else -> TsmColors.SurfaceHigh
                },
                if (low) TsmColors.TextMain else if (ok) TsmColors.onPlayer(side) else TsmColors.TextDim,
                icon,
                onClick = { onOpen(side) },
            )
        }
        Spacer(Modifier.weight(1f))
        Icon(
            Icons.Filled.Tune, null, tint = TsmColors.TextDim,
            modifier = Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)).clickable { onOpen(Side.P1) },
        )
    }
}

/**
 * Riquadro arancione: spento, si accende 5 secondi con il messaggio.
 * Altezza minima, non fissa: con il carattere di sistema ingrandito la seconda riga non va persa.
 */
@Composable
private fun MessageBox(msg: String?) {
    val bg by animateColorAsState(if (msg != null) TsmColors.Orange else TsmColors.Surface, label = "msg")
    Box(
        Modifier.fillMaxWidth().heightIn(min = 50.dp).clip(RoundedCornerShape(14.dp)).background(bg)
            .border(1.dp, if (msg != null) TsmColors.Orange else TsmColors.Outline, RoundedCornerShape(14.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(msg, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "msgText") { m ->
            if (m != null) {
                Text(
                    m, color = TsmColors.OnOrange, fontWeight = FontWeight.Black, fontSize = 18.sp, lineHeight = 21.sp,
                    maxLines = 2, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun Scoreboard(state: MatchState, names: Names, s: Strings) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(TsmColors.Surface).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            for (i in state.sets.indices) HeaderCell("S${i + 1}", 30.dp)
            HeaderCell(s.setsHeader, 52.dp)
            HeaderCell(s.gamesHeader, 58.dp)
        }
        for (side in Side.entries) {
            val serving = !state.isFinished && state.server == side
            Row(Modifier.fillMaxWidth().height(if (state.rules.doubles) 52.dp else 44.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(6.dp).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(TsmColors.player(side)))
                Spacer(Modifier.width(8.dp))
                Box(Modifier.width(20.dp), contentAlignment = Alignment.Center) {
                    if (serving) Icon(Icons.Filled.SportsTennis, null, tint = TsmColors.Ball, modifier = Modifier.size(18.dp))
                }
                Column(Modifier.weight(1f)) {
                    val players = names.players(side)
                    players.forEachIndexed { i, p ->
                        val bold = !state.rules.doubles || (serving && state.serverPlayer == i)
                        Text(
                            p, color = TsmColors.player(side), maxLines = 1, overflow = TextOverflow.Ellipsis,
                            fontSize = if (players.size > 1) 15.sp else 20.sp,
                            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
                for (set in state.sets) {
                    Box(Modifier.width(30.dp), contentAlignment = Alignment.Center) {
                        Row(verticalAlignment = Alignment.Top) {
                            Text(
                                set.shown(side).toString(),
                                color = if (set.winner == side) TsmColors.TextMain else TsmColors.TextDim,
                                fontSize = if (set.matchTiebreak) 15.sp else 18.sp,
                                fontWeight = if (set.winner == side) FontWeight.Bold else FontWeight.Normal,
                            )
                            if (set.hasTiebreak && !set.matchTiebreak && set.winner != side) {
                                Text(set.tb(side).toString(), color = TsmColors.TextDim, fontSize = 10.sp)
                            }
                        }
                    }
                }
                ScoreCell(state.setsWon(side).toString(), 52.dp, TsmColors.SurfaceHigh, TsmColors.TextMain)
                Spacer(Modifier.width(4.dp))
                ScoreCell(state.games(side).toString(), 54.dp, TsmColors.player(side).copy(alpha = 0.22f), TsmColors.player(side))
            }
        }
    }
}

@Composable
private fun HeaderCell(text: String, width: androidx.compose.ui.unit.Dp) {
    Text(text, color = TsmColors.TextDim, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.width(width))
}

@Composable
private fun ScoreCell(text: String, width: androidx.compose.ui.unit.Dp, bg: Color, fg: Color) {
    Box(Modifier.width(width).height(40.dp).clip(RoundedCornerShape(10.dp)).background(bg), contentAlignment = Alignment.Center) {
        Text(text, color = fg, fontSize = 26.sp, fontWeight = FontWeight.Black)
    }
}

/**
 * I due tasti quadrati seguono la posizione reale dei giocatori vista dal giudice di sedia:
 * a ogni cambio campo si scambiano di posto.
 */
@Composable
private fun PointButtons(state: MatchState, names: Names, s: Strings, enabled: Boolean, onPoint: (Side) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val gap = 12.dp
        val label = 30.dp
        val sizeDp = min((maxWidth - gap) / 2, maxHeight - label - 6.dp).coerceAtLeast(0.dp)
        Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(gap)) {
            val left = state.leftSide()
            for (side in listOf(left, left.other)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        onClick = { onPoint(side) },
                        enabled = enabled,
                        shape = RoundedCornerShape(24.dp),
                        color = TsmColors.player(side),
                        shadowElevation = 6.dp,
                        modifier = Modifier.size(sizeDp),
                    ) {
                        Column(
                            Modifier.fillMaxSize().padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                names.short(side), color = TsmColors.onPlayer(side), fontWeight = FontWeight.Bold,
                                fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                            )
                            Text(
                                state.pointLabel(side), color = TsmColors.onPlayer(side),
                                fontSize = (sizeDp.value * 0.42f).coerceIn(40f, 110f).sp, fontWeight = FontWeight.Black,
                            )
                            Text(
                                when (state.tiebreak) {
                                    TiebreakKind.SET -> s.msgTiebreak
                                    TiebreakKind.MATCH -> s.msgMatchTiebreak
                                    TiebreakKind.NONE -> " "
                                },
                                color = TsmColors.onPlayer(side).copy(alpha = 0.8f),
                                fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.height(label), contentAlignment = Alignment.Center) {
                        if (!state.isFinished && state.server == side) {
                            val who = if (state.rules.doubles) " · " + names.player(side, state.serverPlayer) else ""
                            Pill(s.onServe + who, TsmColors.Ball, TsmColors.OnBall, Icons.Filled.SportsTennis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ControlButton(
    icon: ImageVector,
    text: String?,
    modifier: Modifier,
    highlight: Boolean = false,
    contentDescription: String? = null,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (highlight) TsmColors.Orange else TsmColors.SurfaceHigh,
        modifier = modifier.height(52.dp),
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(icon, contentDescription, tint = if (highlight) TsmColors.OnOrange else TsmColors.TextMain)
            if (text != null) {
                Spacer(Modifier.width(8.dp))
                Text(text, color = if (highlight) TsmColors.OnOrange else TsmColors.TextMain, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
TSM_EOF

# ---------------------------------------------------------------- app/src/main/java/com/tennis/scoremanager/ui/screens/OptionsScreen.kt
cat > "$DEST/app/src/main/java/com/tennis/scoremanager/ui/screens/OptionsScreen.kt" << 'TSM_EOF'
package com.tennis.scoremanager.ui.screens

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.speech.tts.TextToSpeech
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.SportsTennis
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.tennis.scoremanager.MatchController
import com.tennis.scoremanager.Screen
import com.tennis.scoremanager.ble.BandInfo
import com.tennis.scoremanager.ble.BleManager
import com.tennis.scoremanager.ble.FoundBand
import com.tennis.scoremanager.ble.LinkState
import com.tennis.scoremanager.data.LocationHelper
import com.tennis.scoremanager.data.MatchOptions
import com.tennis.scoremanager.data.Names
import com.tennis.scoremanager.data.PlayMode
import com.tennis.scoremanager.model.Lang
import com.tennis.scoremanager.model.MatchFormat
import com.tennis.scoremanager.model.Side
import com.tennis.scoremanager.ui.BandSettingsPanel
import com.tennis.scoremanager.ui.BigButton
import com.tennis.scoremanager.ui.GhostButton
import com.tennis.scoremanager.ui.LocalStrings
import com.tennis.scoremanager.ui.Picker
import com.tennis.scoremanager.ui.RequirementRow
import com.tennis.scoremanager.ui.ScreenScaffold
import com.tennis.scoremanager.ui.SectionCard
import com.tennis.scoremanager.ui.SegOption
import com.tennis.scoremanager.ui.Segmented
import com.tennis.scoremanager.ui.Strings
import com.tennis.scoremanager.ui.SwitchRow
import com.tennis.scoremanager.ui.TsmColors
import com.tennis.scoremanager.ui.TvSection
import com.tennis.scoremanager.voice.Phrases
import com.tennis.scoremanager.voice.TtsStatus
import kotlinx.coroutines.launch
import kotlin.random.Random

/** Pagina 2: modalità, braccialetti, lingua, voce, formato e sorteggio. */
@Composable
fun OptionsScreen(c: MatchController) {
    val s = LocalStrings.current
    val context = LocalContext.current
    val o by c.options.collectAsState()
    val su by c.setup.collectAsState()
    val env by c.envTick.collectAsState()
    val btOn by c.ble.adapterOn.collectAsState()
    // Bluetooth e posizione si seguono in tempo reale (anche se li si spegne dalla tendina senza lasciare l'app).
    val locOn by c.ble.locationOn.collectAsState()
    var permTick by remember { mutableIntStateOf(0) }
    val names = remember(su, s) { Names(su, s) }

    val blePerms = remember(env, permTick) { c.ble.hasPermissions() }
    val locPerm = remember(env, permTick) { LocationHelper.hasPermission(context) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permTick++
        c.ble.reconnectAll()
    }
    val btLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { permTick++ }

    fun requestBandPermissions() {
        val extra = if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.POST_NOTIFICATIONS) else emptyArray()
        permLauncher.launch(BleManager.requiredPermissions() + extra)
    }

    fun requestLocation() = permLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))

    // Col tabellone TV il servizio in primo piano parte anche senza braccialetti: su Android 13+ senza questo
    // permesso la sua notifica non compare e non si torna all'app toccandola.
    val tvOn = c.tv.collectAsState().value.enabled
    LaunchedEffect(tvOn) {
        if (tvOn && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            permLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
        }
    }
    fun openLocationSettings() = runCatching { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
    fun enableBluetooth() {
        if (!blePerms) requestBandPermissions() else runCatching { btLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }
    }

    var locationDialog by remember { mutableStateOf(false) }
    var bandsRequired by remember { mutableStateOf(false) }
    var missingBands by remember { mutableStateOf<List<String>?>(null) }
    val bandsReadyEnv = blePerms && btOn && locPerm && locOn
    BackHandler { c.back() }

    fun onNext() {
        if (o.mode == PlayMode.BANDS) {
            if (!bandsReadyEnv) {
                bandsRequired = true
                return
            }
            val bands = c.ble.bands.value
            val missing = Side.entries.filter { bands[it] == null }.map { names.short(it) }
            if (missing.isNotEmpty()) {
                missingBands = missing
                return
            }
        } else if (!(locPerm && locOn)) {
            locationDialog = true
            return
        }
        c.go(Screen.START)
    }

    ScreenScaffold(
        title = s.optionsTitle,
        subtitle = null,
        icon = Icons.Filled.Tune,
        bottomBar = {
            GhostButton(s.back, Icons.AutoMirrored.Filled.ArrowBack, { c.back() }, Modifier.weight(1f))
            BigButton(s.next, Icons.AutoMirrored.Filled.ArrowForward, { onNext() }, Modifier.weight(1f))
        },
    ) {
        // Modalità
        SectionCard(s.modeSection, Icons.Filled.SportsTennis) {
            Segmented(
                listOf(SegOption(s.modeReferee, Icons.Filled.Gavel), SegOption(s.modeBands, Icons.Filled.Watch)),
                selected = if (o.mode == PlayMode.BANDS) 1 else 0,
                onSelect = { i -> c.updateOptions { it.copy(mode = if (i == 1) PlayMode.BANDS else PlayMode.REFEREE) } },
            )
            Text(if (o.mode == PlayMode.BANDS) s.modeBandsHint else s.modeRefereeHint, color = TsmColors.TextDim)
            if (o.mode == PlayMode.BANDS) {
                Text(s.requirements, style = MaterialTheme.typography.titleMedium, color = TsmColors.TextMain)
                RequirementRow(Icons.Filled.Bluetooth, s.bluetooth, btOn, s.enable) { enableBluetooth() }
                RequirementRow(Icons.Filled.LocationOn, s.location, blePerms && locPerm, s.allow) { requestBandPermissions() }
                RequirementRow(Icons.Filled.MyLocation, s.locationServices, locOn, s.enable) { openLocationSettings() }
                BandsSection(c, names, enabled = bandsReadyEnv)
            }
        }

        // Lingua
        SectionCard(s.languageSection, Icons.Filled.Language) {
            // Ogni lingua è scritta nella lingua stessa, così si ritrova anche se l'app è in una lingua che non si legge.
            // Bloccata mentre si generano i file vocali, come motore e voce (vedi VoiceSection).
            val generating = c.voiceProgress.collectAsState().value != null
            Segmented(
                Lang.entries.map { SegOption("${it.flag}  ${it.label}") },
                selected = o.lang.ordinal,
                onSelect = { i -> c.updateOptions { it.copy(lang = Lang.entries[i]) } },
                columns = 2,
                enabled = !generating,
            )
            Text(s.languageHint, color = TsmColors.TextDim, fontSize = 13.sp)
        }

        // Audio e voce
        VoiceSection(c, o)

        // Tabellone su TV
        TvSection(c)

        // Formato
        SectionCard(s.formatSection, Icons.Filled.EmojiEvents) {
            FormatOption(s.formatBestOfThree, s.formatBestOfThreeHint, o.format == MatchFormat.BEST_OF_THREE) {
                c.updateOptions { it.copy(format = MatchFormat.BEST_OF_THREE) }
            }
            FormatOption(s.formatMatchTiebreak, s.formatMatchTiebreakHint, o.format == MatchFormat.TWO_SETS_MATCH_TIEBREAK) {
                c.updateOptions { it.copy(format = MatchFormat.TWO_SETS_MATCH_TIEBREAK) }
            }
            SwitchRow(Icons.Filled.Timer, s.noAd, s.noAdHint, o.noAd) { v -> c.updateOptions { it.copy(noAd = v) } }
        }

        // Sorteggio
        CoinTossSection(c, o, names, su.doubles)
    }

    if (locationDialog) {
        AlertDialog(
            onDismissRequest = { locationDialog = false },
            icon = { Icon(Icons.Filled.LocationOn, null, tint = TsmColors.Orange) },
            title = { Text(s.locationDialogTitle) },
            text = { Text(s.locationDialogText) },
            confirmButton = {
                Button(onClick = {
                    locationDialog = false
                    if (!locPerm) requestLocation() else openLocationSettings()
                }) { Text(s.enable) }
            },
            dismissButton = {
                TextButton(onClick = {
                    locationDialog = false
                    c.go(Screen.START)
                }) { Text(s.continueWithout) }
            },
        )
    }
    if (bandsRequired) {
        AlertDialog(
            onDismissRequest = { bandsRequired = false },
            icon = { Icon(Icons.Filled.Bluetooth, null, tint = TsmColors.Orange) },
            title = { Text(s.bandsRequiredTitle) },
            text = { Text(s.bandsRequiredText) },
            confirmButton = {
                Button(onClick = {
                    bandsRequired = false
                    when {
                        !(blePerms && locPerm) -> requestBandPermissions()
                        !btOn -> enableBluetooth()
                        !locOn -> openLocationSettings()
                    }
                }) { Text(s.enable) }
            },
            dismissButton = { TextButton(onClick = { bandsRequired = false }) { Text(s.cancel) } },
        )
    }
    missingBands?.let { list ->
        AlertDialog(
            onDismissRequest = { missingBands = null },
            icon = { Icon(Icons.Filled.Watch, null, tint = TsmColors.Orange) },
            title = { Text(s.bandsMissingTitle) },
            text = { Text(s.bandsMissingText(list)) },
            confirmButton = {
                Button(onClick = {
                    missingBands = null
                    c.go(Screen.START)
                }) { Text(s.continueAnyway) }
            },
            dismissButton = { TextButton(onClick = { missingBands = null }) { Text(s.cancel) } },
        )
    }
}

@Composable
private fun BandsSection(c: MatchController, names: Names, enabled: Boolean) {
    val s = LocalStrings.current
    val o by c.options.collectAsState()
    val found by c.ble.found.collectAsState()
    val scanning by c.ble.scanning.collectAsState()
    val bands by c.ble.bands.collectAsState()
    val batteries by c.bandBattery.collectAsState()

    // Ricerca automatica e continua finché questa pagina è aperta e l'app è in primo piano.
    LifecycleResumeEffect(enabled) {
        c.setBandScan(enabled)
        onPauseOrDispose { c.setBandScan(false) }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        if (scanning) {
            CircularProgressIndicator(Modifier.size(22.dp), color = TsmColors.Ball, strokeWidth = 3.dp)
            Spacer(Modifier.width(10.dp))
        } else {
            Icon(Icons.AutoMirrored.Filled.BluetoothSearching, null, tint = TsmColors.TextDim)
            Spacer(Modifier.width(10.dp))
        }
        Text(if (scanning) s.autoSearch else s.autoSearchOff, color = if (scanning) TsmColors.TextMain else TsmColors.Orange, fontSize = 13.sp)
    }
    for (side in Side.entries) {
        BandPicker(c, side, names.short(side), bands[side], found, bands, batteries[side])
    }
    if (bands.size == 2) {
        GhostButton(s.swapBands, Icons.Filled.SwapHoriz, { c.swapBands() }, Modifier.fillMaxWidth())
    }
    SwitchRow(Icons.Filled.PowerSettingsNew, s.bandsOffAtEnd, s.bandsOffAtEndHint, o.bandsOffAtEnd) { v ->
        c.updateOptions { it.copy(bandsOffAtEnd = v) }
    }
}

@Composable
private fun BandPicker(
    c: MatchController,
    side: Side,
    playerName: String,
    current: BandInfo?,
    found: List<FoundBand>,
    all: Map<Side, BandInfo>,
    battery: com.tennis.scoremanager.BandBattery?,
) {
    val s = LocalStrings.current
    val locale = c.options.collectAsState().value.lang.locale
    var open by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    val accent = TsmColors.player(side)
    Column(Modifier.fillMaxWidth()) {
        Text(s.bandFor(playerName), color = accent, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Box {
            OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                val (icon, tint) = when (current?.state) {
                    LinkState.READY -> Icons.Filled.BluetoothConnected to TsmColors.Ok
                    LinkState.CONNECTING -> Icons.Filled.Bluetooth to TsmColors.Orange
                    LinkState.POWERED_OFF -> Icons.Filled.PowerSettingsNew to TsmColors.Danger
                    else -> Icons.Filled.BluetoothDisabled to TsmColors.TextDim
                }
                Icon(icon, null, tint = tint)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(current?.name ?: s.noBand, color = TsmColors.TextMain, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (current != null) {
                        val st = when (current.state) {
                            LinkState.READY -> s.bandConnected
                            LinkState.CONNECTING -> s.bandConnecting
                            LinkState.POWERED_OFF -> s.bandOff
                            LinkState.IDLE -> s.bandIdle
                        }
                        val volts = current.millivolts?.let { String.format(locale, " (%.2f V)", it / 1000.0) } ?: ""
                        val batteryText = when {
                            current.chargeFull -> " · ${s.bandChargeFull}"
                            current.charging && current.battery != null -> " · ${s.bandCharging(current.battery)}$volts"
                            else -> current.battery?.let { " · ${s.battery} $it%$volts" } ?: ""
                        }
                        Text(
                            st + batteryText +
                                (battery?.leftText()?.let { " · ${s.autonomy(it)}" } ?: ""),
                            color = TsmColors.TextDim, fontSize = 12.sp,
                        )
                    }
                }
                Icon(Icons.Filled.ArrowDropDown, null, tint = TsmColors.TextDim)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                DropdownMenuItem(text = { Text(s.noBand) }, onClick = {
                    open = false
                    c.assignBand(side, null, null)
                })
                val options = (found.map { it.address to "${it.name}  (${it.rssi} dBm)" } +
                    all.values.map { it.address to it.name }).distinctBy { it.first }
                for ((address, label) in options) {
                    val usedBy = all.entries.firstOrNull { it.value.address == address && it.key != side }?.key
                    DropdownMenuItem(
                        text = { Text(label + (usedBy?.let { " → ${s.playerTag(it.ordinal + 1)}" } ?: "")) },
                        leadingIcon = { Icon(Icons.Filled.Watch, null) },
                        onClick = {
                            open = false
                            c.assignBand(side, address, label.substringBefore("  ("))
                        },
                    )
                }
            }
        }
        if (current != null) {
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val ready = current.state == LinkState.READY
                SmallAction(s.identify, Icons.Filled.FlashOn, Modifier.weight(1f), enabled = ready) { c.identifyBand(side) }
                SmallAction(
                    s.settingsShort, if (settingsOpen) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, Modifier.weight(1f),
                ) { settingsOpen = !settingsOpen }
            }
            if (settingsOpen) {
                Spacer(Modifier.height(8.dp))
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(14.dp)).padding(12.dp),
                ) { BandSettingsPanel(c, side) }
            }
        }
    }
}

@Composable
private fun VoiceSection(c: MatchController, o: MatchOptions) {
    val s = LocalStrings.current
    val context = LocalContext.current
    val generated by c.voiceCount.collectAsState()
    val custom by c.customVoiceCount.collectAsState()
    val progress by c.voiceProgress.collectAsState()
    val tts by c.announcer.status.collectAsState()
    val engines by c.announcer.engines.collectAsState()
    val voices by c.announcer.voices.collectAsState()
    val currentVoice by c.announcer.currentVoice.collectAsState()
    val currentEngine by c.announcer.currentEngineFlow.collectAsState()
    val zipLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { c.importVoiceZip(it) } }
    val total = Phrases.keys.size
    // Durante "Genera file" motore, voce, lingua, prova e audio non si toccano: i file uscirebbero misti o interrotti.
    val generating = progress != null
    var confirmDelete by remember { mutableStateOf(false) }

    SectionCard(s.audioSection, Icons.AutoMirrored.Filled.VolumeUp) {
        SwitchRow(Icons.Filled.RecordVoiceOver, s.voiceCalls, null, o.audio, enabled = !generating) { v -> c.updateOptions { it.copy(audio = v) } }

        // Motore: Samsung, Google, ... (i nomi delle voci cambiano col motore, quindi si riparte da "automatica")
        val engineLabel = engines.firstOrNull { it.pkg == (o.ttsEngine ?: currentEngine) }?.label
        Picker(
            label = s.ttsEngine,
            value = if (o.ttsEngine == null) "${s.engineDefault}${engineLabel?.let { " ($it)" } ?: ""}" else engineLabel ?: o.ttsEngine,
            options = listOf<Pair<String?, String>>(null to s.engineDefault) + engines.map { it.pkg to it.label },
            enabled = !generating,
        ) { pkg -> c.updateOptions { it.copy(ttsEngine = pkg, ttsVoice = null) } }

        Picker(
            label = s.ttsVoice,
            value = if (o.ttsVoice == null) "${s.voiceAuto}${currentVoice?.let { " · ${voiceLabel(it, s)}" } ?: ""}"
            else voiceLabel(o.ttsVoice, s),
            options = listOf<Pair<String?, String>>(null to s.voiceAuto) +
                voices.map { it.name to "${voiceLabel(it.name, s)} · ${if (it.online) s.online else s.offline}" },
            enabled = !generating,
        ) { name -> c.updateOptions { it.copy(ttsVoice = name) } }

        if (tts == TtsStatus.MISSING_LANGUAGE) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.ttsMissing, color = TsmColors.Orange, modifier = Modifier.weight(1f), fontSize = 13.sp)
                TextButton(onClick = {
                    runCatching { context.startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)) }
                }) { Text(s.installVoice) }
            }
        }
        // Il motore non parte: installare una voce non serve, si apre la pagina "Sintesi vocale" di Android
        // (o le Impostazioni, se il telefono non ce l'ha).
        if (tts == TtsStatus.ERROR) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.ttsEngineError, color = TsmColors.Orange, modifier = Modifier.weight(1f), fontSize = 13.sp)
                TextButton(onClick = {
                    runCatching { context.startActivity(Intent("com.android.settings.TTS_SETTINGS")) }
                        .onFailure { runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS)) } }
                }) { Text(s.openTtsSettings) }
            }
        }
        // Lo stesso tasto avvia e ferma la prova; uscendo dalla pagina si ferma da sola.
        val playing by c.announcer.playing.collectAsState()
        val testing = playing == MatchController.VOICE_TEST
        DisposableEffect(Unit) { onDispose { c.stopVoiceTest() } }
        SmallAction(
            if (testing) s.stopVoiceTest else s.testVoice,
            if (testing) Icons.Filled.StopCircle else Icons.Filled.PlayCircle,
            Modifier.fillMaxWidth(),
            enabled = !generating,
        ) { if (testing) c.stopVoiceTest() else c.testVoice() }
        Text(s.voiceFilesHint, color = TsmColors.TextDim, fontSize = 13.sp)

        // Registrazioni personalizzate (voce vera): sempre prioritarie
        Text(s.customRecordings(custom, total), color = if (custom > 0) TsmColors.Ok else TsmColors.TextDim, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallAction(s.importVoiceZip, Icons.Filled.FolderZip, Modifier.weight(1f)) {
                zipLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
            }
            SmallAction(s.deleteCustomVoice, Icons.Filled.DeleteOutline, Modifier.weight(1f), enabled = custom > 0) { confirmDelete = true }
        }

        // File pre-generati (facoltativi)
        SwitchRow(Icons.Filled.FolderZip, s.voiceFilesMode, s.voiceFilesModeHint, o.voiceFiles, enabled = !generating) { v ->
            c.updateOptions { it.copy(voiceFiles = v) }
        }
        if (o.voiceFiles) {
            Text(s.voiceFiles(generated, total), color = if (generated == total) TsmColors.Ok else TsmColors.Orange, fontWeight = FontWeight.Bold)
            progress?.let { (i, n) ->
                Text(s.generating(i, n), color = TsmColors.TextMain)
                LinearProgressIndicator(progress = { if (n == 0) 0f else i / n.toFloat() }, modifier = Modifier.fillMaxWidth(), color = TsmColors.Ball)
            }
            SmallAction(s.generateVoice, Icons.Filled.RecordVoiceOver, Modifier.fillMaxWidth(), enabled = !generating) { c.generateVoice() }
        }
        Text(c.voice.baseDir.absolutePath, color = TsmColors.TextDim, fontSize = 11.sp)
    }

    // Le registrazioni rimosse non si recuperano: si chiede conferma come per "Nuova partita" ed "Esci".
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = { Icon(Icons.Filled.DeleteOutline, null, tint = TsmColors.Orange) },
            title = { Text(s.deleteCustomConfirmTitle) },
            text = { Text(s.deleteCustomConfirmText(custom)) },
            confirmButton = {
                Button(onClick = {
                    confirmDelete = false
                    c.deleteCustomVoice()
                }) { Text(s.deleteCustomVoice) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(s.cancel) } },
        )
    }
}

/** "it-it-x-itb-local" -> "Voce ITB"; gli altri nomi restano come sono. */
private fun voiceLabel(name: String, s: Strings): String =
    if ("-x-" in name) s.voiceName(name.substringAfter("-x-").substringBefore('-').uppercase()) else name

@Composable
private fun SmallAction(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(46.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = TsmColors.SurfaceHigh, contentColor = TsmColors.TextMain),
    ) {
        Icon(icon, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun FormatOption(title: String, hint: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .border(2.dp, if (selected) TsmColors.Ball else TsmColors.Outline, RoundedCornerShape(14.dp))
            .background(if (selected) TsmColors.Ball.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(22.dp).clip(CircleShape).border(2.dp, if (selected) TsmColors.Ball else TsmColors.TextDim, CircleShape),
            contentAlignment = Alignment.Center,
        ) { if (selected) Box(Modifier.size(12.dp).clip(CircleShape).background(TsmColors.Ball)) }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, color = TsmColors.TextMain, fontWeight = FontWeight.Bold)
            Text(hint, color = TsmColors.TextDim, fontSize = 13.sp)
        }
    }
}

@Composable
private fun CoinTossSection(c: MatchController, o: MatchOptions, names: Names, doubles: Boolean) {
    val s = LocalStrings.current
    val scope = rememberCoroutineScope()
    val angle = remember { Animatable(if (o.tossWinner == Side.P2) 180f else 0f) }
    var flipping by remember { mutableStateOf(false) }

    SectionCard(s.coinToss, Icons.Filled.Casino) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Coin(angle.value, names)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BigButton(s.tossCoin, Icons.Filled.Casino, enabled = !flipping, onClick = {
                    flipping = true
                    scope.launch {
                        val w = if (Random.nextBoolean()) Side.P1 else Side.P2
                        angle.snapTo(angle.value % 360f)
                        val target = angle.value - angle.value % 360f + 360f * 6 + if (w == Side.P2) 180f else 0f
                        angle.animateTo(target, tween(1700, easing = FastOutSlowInEasing))
                        c.updateOptions { it.copy(tossWinner = w, firstServer = w) }
                        flipping = false
                    }
                })
                o.tossWinner?.let {
                    if (!flipping) Text(s.tossWinner(names.short(it)), color = TsmColors.player(it), fontWeight = FontWeight.Black)
                }
            }
        }
        Text(s.tossHint, color = TsmColors.TextDim, fontSize = 13.sp)

        Text(s.serving, style = MaterialTheme.typography.titleMedium, color = TsmColors.TextMain)
        Segmented(
            Side.entries.map { SegOption(names.short(it), Icons.Filled.SportsTennis, TsmColors.player(it), TsmColors.onPlayer(it)) },
            selected = o.firstServer.ordinal,
            onSelect = { i -> c.updateOptions { it.copy(firstServer = Side.entries[i]) } },
        )
        if (doubles) {
            for (side in Side.entries) {
                Text(s.firstServerOf(names.short(side)), color = TsmColors.player(side), fontSize = 13.sp)
                Segmented(
                    names.players(side).map { SegOption(it, null, TsmColors.player(side), TsmColors.onPlayer(side)) },
                    selected = if (side == Side.P1) o.firstServerP1 else o.firstServerP2,
                    onSelect = { i -> c.updateOptions { if (side == Side.P1) it.copy(firstServerP1 = i) else it.copy(firstServerP2 = i) } },
                )
            }
        }

        Text("${s.courtSides} · ${s.umpireView}", style = MaterialTheme.typography.titleMedium, color = TsmColors.TextMain)
        CourtDiagram(o.p1Left, o.firstServer, names, s)
        GhostButton(s.swapSides, Icons.Filled.SwapHoriz, { c.updateOptions { it.copy(p1Left = !it.p1Left) } }, Modifier.fillMaxWidth())
    }
}

/** Moneta che gira: fronte = Giocatore 1 (giallo), retro = Giocatore 2 (rosso). */
@Composable
private fun Coin(angle: Float, names: Names) {
    val a = ((angle % 360f) + 360f) % 360f
    val back = a in 90f..270f
    val side = if (back) Side.P2 else Side.P1
    Box(
        Modifier.size(96.dp).graphicsLayer {
            rotationY = angle
            cameraDistance = 14f * density
        }.clip(CircleShape).background(
            Brush.radialGradient(listOf(Color(0xFFFFF3B0), Color(0xFFD4A017), Color(0xFF8C6A00))),
        ).border(4.dp, TsmColors.player(side), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.graphicsLayer { if (back) rotationY = 180f }) {
            Text(if (side == Side.P1) "1" else "2", fontSize = 34.sp, fontWeight = FontWeight.Black, color = Color(0xFF3B2B00))
            Text(names.short(side).take(10), fontSize = 10.sp, color = Color(0xFF3B2B00), maxLines = 1)
        }
    }
}

/** Campo visto dall'alto con il giudice di sedia a bordo rete: i giocatori sono a sinistra e a destra. */
@Composable
fun CourtDiagram(p1Left: Boolean, server: Side, names: Names, s: Strings) {
    val left = if (p1Left) Side.P1 else Side.P2
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().aspectRatio(2.1f).clip(RoundedCornerShape(14.dp)).background(TsmColors.Court)) {
            Canvas(Modifier.fillMaxSize()) {
                val pad = 10.dp.toPx()
                val w = size.width
                val h = size.height
                val line = TsmColors.CourtLine
                drawRect(line, topLeft = Offset(pad, pad), size = Size(w - 2 * pad, h - 2 * pad), style = Stroke(3f))
                val alley = (h - 2 * pad) * 0.125f
                drawLine(line, Offset(pad, pad + alley), Offset(w - pad, pad + alley), 2f)
                drawLine(line, Offset(pad, h - pad - alley), Offset(w - pad, h - pad - alley), 2f)
                val cx = w / 2
                val svc = (w - 2 * pad) / 2 * 0.538f
                drawLine(line, Offset(cx - svc, pad + alley), Offset(cx - svc, h - pad - alley), 2f)
                drawLine(line, Offset(cx + svc, pad + alley), Offset(cx + svc, h - pad - alley), 2f)
                drawLine(line, Offset(cx - svc, h / 2), Offset(cx + svc, h / 2), 2f)
                drawLine(Color.White, Offset(cx, pad - 6f), Offset(cx, h - pad + 6f), 6f)
            }
            Row(Modifier.fillMaxSize()) {
                for (side in listOf(left, left.other)) {
                    Column(
                        Modifier.weight(1f).fillMaxSize().padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Box(
                            Modifier.clip(RoundedCornerShape(10.dp)).background(TsmColors.player(side)).padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Text(
                                names.short(side), color = TsmColors.onPlayer(side), fontWeight = FontWeight.Bold,
                                maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, fontSize = 13.sp,
                            )
                        }
                        // Lo spazio della pallina c'è sempre, così i due nomi restano allineati.
                        Spacer(Modifier.height(4.dp))
                        Icon(
                            Icons.Filled.SportsTennis, null,
                            tint = if (side == server) TsmColors.Ball else Color.Transparent,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth()) {
            Text("◀ ${s.left}", color = TsmColors.TextDim, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text("▲ ${s.umpireChair}", color = TsmColors.TextMain, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text("${s.right} ▶", color = TsmColors.TextDim, fontSize = 12.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        }
    }
}
TSM_EOF

# ---------------------------------------------------------------- app/src/main/java/com/tennis/scoremanager/ui/screens/SetupScreen.kt
cat > "$DEST/app/src/main/java/com/tennis/scoremanager/ui/screens/SetupScreen.kt" << 'TSM_EOF'
package com.tennis.scoremanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SportsTennis
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tennis.scoremanager.MatchController
import com.tennis.scoremanager.Screen
import com.tennis.scoremanager.data.SetupData
import com.tennis.scoremanager.model.Side
import com.tennis.scoremanager.tv.DisplayActivity
import android.content.Intent
import com.tennis.scoremanager.ui.BigButton
import com.tennis.scoremanager.ui.GhostButton
import com.tennis.scoremanager.ui.LocalStrings
import com.tennis.scoremanager.ui.ScreenScaffold
import com.tennis.scoremanager.ui.SectionCard
import com.tennis.scoremanager.ui.SegOption
import com.tennis.scoremanager.ui.Segmented
import com.tennis.scoremanager.ui.TsmColors

/** Pagina 1: circolo, campo, singolare/doppio e nomi. Tutto facoltativo. */
@Composable
fun SetupScreen(c: MatchController) {
    val s = LocalStrings.current
    val su by c.setup.collectAsState()
    val context = LocalContext.current
    ScreenScaffold(
        title = s.setupTitle,
        subtitle = s.setupSubtitle,
        icon = Icons.Filled.SportsTennis,
        bottomBar = {
            GhostButton(s.clearFields, Icons.Filled.DeleteSweep, { c.updateSetup { SetupData(doubles = it.doubles) } }, Modifier.weight(1f))
            BigButton(s.next, Icons.AutoMirrored.Filled.ArrowForward, { c.go(Screen.OPTIONS) }, Modifier.weight(1f))
        },
    ) {
        SectionCard(s.clubSection, Icons.Filled.Business) {
            Field(su.club, s.clubName, Icons.Filled.Business) { v -> c.updateSetup { it.copy(club = v) } }
            Field(su.court, s.courtNumber, Icons.Filled.Tag, KeyboardType.Text) { v -> c.updateSetup { it.copy(court = v) } }
        }
        SectionCard(if (su.doubles) s.doubles else s.singles, Icons.Filled.Groups) {
            Segmented(
                listOf(SegOption(s.singles, Icons.Filled.Person), SegOption(s.doubles, Icons.Filled.Groups)),
                selected = if (su.doubles) 1 else 0,
                onSelect = { i -> c.updateSetup { it.copy(doubles = i == 1) } },
            )
            if (su.doubles) Text(s.doublesHint, color = TsmColors.TextDim)
        }
        PlayerCard(c, su, Side.P1)
        PlayerCard(c, su, Side.P2)
        // Il secondo telefono, collegato al monitor, fa da tabellone per quello dell'arbitro.
        SectionCard(s.displayMode, Icons.Filled.Tv) {
            Text(s.displayModeHint, color = TsmColors.TextDim)
            GhostButton(s.displayMode, Icons.Filled.Tv, {
                context.startActivity(Intent(context, DisplayActivity::class.java))
            }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun PlayerCard(c: MatchController, su: SetupData, side: Side) {
    val s = LocalStrings.current
    val accent = TsmColors.player(side)
    val title = if (side == Side.P1) s.player1 else s.player2
    SectionCard(title, Icons.Filled.Person, accent = accent) {
        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(accent))
        val a = if (side == Side.P1) su.p1a else su.p2a
        val b = if (side == Side.P1) su.p1b else su.p2b
        Field(a, if (su.doubles) "${s.playerName} A" else s.playerName, Icons.Filled.Person, accent = accent) { v ->
            c.updateSetup { if (side == Side.P1) it.copy(p1a = v) else it.copy(p2a = v) }
        }
        if (su.doubles) {
            Field(b, "${s.playerName} B", Icons.Filled.Person, accent = accent) { v ->
                c.updateSetup { if (side == Side.P1) it.copy(p1b = v) else it.copy(p2b = v) }
            }
        }
    }
}

@Composable
private fun Field(
    value: String,
    label: String,
    icon: ImageVector,
    keyboard: KeyboardType = KeyboardType.Text,
    accent: Color = TsmColors.Ball,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.take(40)) },
        label = { Text(label) },
        leadingIcon = { Icon(icon, null) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Words,
            keyboardType = keyboard,
            imeAction = ImeAction.Next,
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = accent,
            focusedLabelColor = accent,
            focusedLeadingIconColor = accent,
            cursorColor = accent,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.width(0.dp))
}
TSM_EOF

# ---------------------------------------------------------------- app/src/main/java/com/tennis/scoremanager/ui/screens/StartScreen.kt
cat > "$DEST/app/src/main/java/com/tennis/scoremanager/ui/screens/StartScreen.kt" << 'TSM_EOF'
package com.tennis.scoremanager.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SportsTennis
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tennis.scoremanager.MatchController
import com.tennis.scoremanager.ble.LinkState
import com.tennis.scoremanager.data.MatchRecord
import com.tennis.scoremanager.data.Names
import com.tennis.scoremanager.data.PlayMode
import com.tennis.scoremanager.data.Reports
import com.tennis.scoremanager.model.Lang
import com.tennis.scoremanager.model.MatchFormat
import com.tennis.scoremanager.model.ScoreEngine
import com.tennis.scoremanager.model.Side
import com.tennis.scoremanager.ui.BigButton
import com.tennis.scoremanager.ui.ConfirmDialog
import com.tennis.scoremanager.ui.GhostButton
import com.tennis.scoremanager.ui.LocalStrings
import com.tennis.scoremanager.ui.Pill
import com.tennis.scoremanager.ui.TsmColors

/** Pagina 3: INIZIO PARTITA lampeggia in attesa del pulsante o di KEY1 su un braccialetto. */
@Composable
fun StartScreen(c: MatchController) {
    val s = LocalStrings.current
    val o by c.options.collectAsState()
    val su by c.setup.collectAsState()
    val saved by c.saved.collectAsState()
    val bands by c.ble.bands.collectAsState()
    val names = remember(su, s) { Names(su, s) }
    var showSaved by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<MatchRecord?>(null) }
    BackHandler { c.back() }

    val pulse = rememberInfiniteTransition(label = "pulse")
    val alpha by pulse.animateFloat(0.35f, 1f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "alpha")
    val scale by pulse.animateFloat(0.94f, 1.04f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "scale")
    val spin by pulse.animateFloat(0f, 360f, infiniteRepeatable(tween(6000)), label = "spin")

    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { c.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, s.back, tint = TsmColors.TextMain) }
            Spacer(Modifier.weight(1f))
            if (o.mode == PlayMode.BANDS) {
                for (side in Side.entries) {
                    val ready = bands[side]?.state == LinkState.READY
                    Pill(
                        s.playerTag(side.ordinal + 1),
                        if (ready) TsmColors.player(side) else TsmColors.SurfaceHigh,
                        if (ready) TsmColors.onPlayer(side) else TsmColors.TextDim,
                        Icons.Filled.Watch,
                    )
                    Spacer(Modifier.width(6.dp))
                }
            }
        }
        Spacer(Modifier.weight(0.6f))

        // Pallina da tennis che pulsa e ruota
        Box(
            Modifier.size(150.dp).scale(scale).graphicsLayer { rotationZ = spin }
                .clip(RoundedCornerShape(50))
                .background(Brush.radialGradient(listOf(Color(0xFFEFFF8A), TsmColors.Ball, Color(0xFF8DB31C))))
                .clickable { c.startMatch() },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val st = Stroke(width = size.minDimension * 0.05f)
                drawArc(Color.White, -60f, 120f, false, Offset(-size.width * 0.55f, 0f), Size(size.width, size.height), style = st)
                drawArc(Color.White, 120f, 120f, false, Offset(size.width * 0.55f, 0f), Size(size.width, size.height), style = st)
            }
        }
        Spacer(Modifier.height(28.dp))
        Text(
            s.startMatch,
            style = TextStyle(
                brush = Brush.horizontalGradient(listOf(TsmColors.Ball, TsmColors.Player1, TsmColors.Orange)),
                fontSize = 44.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center,
            ),
            modifier = Modifier.graphicsLayer { this.alpha = alpha },
        )
        Spacer(Modifier.height(8.dp))
        Text(if (o.mode == PlayMode.BANDS) s.startHintBands else s.startHint, color = TsmColors.TextDim, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Text(names.short(Side.P1), color = TsmColors.Player1, fontWeight = FontWeight.Bold, fontSize = 18.sp, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
            Text("  ${s.vs}  ", color = TsmColors.TextDim)
            Text(names.short(Side.P2), color = TsmColors.Player2, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(if (o.format == MatchFormat.BEST_OF_THREE) s.formatBestOfThree else s.formatMatchTiebreak, TsmColors.SurfaceHigh, TsmColors.TextMain)
            Pill("${s.serving}: ${names.short(o.firstServer)}", TsmColors.player(o.firstServer), TsmColors.onPlayer(o.firstServer), Icons.Filled.SportsTennis)
        }
        Spacer(Modifier.weight(1f))
        BigButton(s.startButton, Icons.Filled.PlayArrow, { c.startMatch() }, Modifier.fillMaxWidth().height(64.dp))
        Spacer(Modifier.height(10.dp))
        GhostButton(s.resumeSaved, Icons.Filled.History, {
            c.refreshSaved()
            showSaved = true
        }, Modifier.fillMaxWidth())
    }

    if (showSaved) {
        AlertDialog(
            onDismissRequest = { showSaved = false },
            icon = { Icon(Icons.Filled.History, null, tint = TsmColors.Ball) },
            title = { Text(s.savedMatchesTitle) },
            text = {
                if (saved.isEmpty()) {
                    Text(s.noSavedMatches)
                } else {
                    LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(saved, key = { it.id }) { rec ->
                            SavedRow(rec, o.lang, onOpen = {
                                showSaved = false
                                c.resumeSaved(rec)
                            }, onDelete = { toDelete = rec })
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSaved = false }) { Text(s.cancel) } },
        )
    }

    // Il cestino sta nella stessa riga che riprende la partita: un tocco storto non deve cancellarla.
    toDelete?.let { rec ->
        val n = Names(rec.setup, s)
        ConfirmDialog(
            icon = Icons.Filled.Delete,
            title = s.deleteSavedTitle,
            text = s.deleteSavedText("${n.short(Side.P1)} ${s.vs} ${n.short(Side.P2)}"),
            confirm = s.delete,
            danger = true,
            onConfirm = {
                toDelete = null
                c.deleteSaved(rec)
            },
            onDismiss = { toDelete = null },
        )
    }
}

@Composable
private fun SavedRow(rec: MatchRecord, lang: Lang, onOpen: () -> Unit, onDelete: () -> Unit) {
    val s = LocalStrings.current
    val names = remember(rec.id) { Names(rec.setup, s) }
    val state = remember(rec.id, rec.events.size) { ScoreEngine.replay(rec.rules, rec.events) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TsmColors.SurfaceHigh).clickable(onClick = onOpen).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("${names.short(Side.P1)} ${s.vs} ${names.short(Side.P2)}", fontWeight = FontWeight.Bold, color = TsmColors.TextMain)
            val score = (Reports.scoreLine(state, Side.P1) + "  " + "${state.g1}-${state.g2}").trim()
            Text(score, color = TsmColors.Ball)
            Text(
                "${Reports.date(rec.startedAt ?: rec.updatedAt, lang)} · ${Reports.time(rec.updatedAt, lang)}",
                color = TsmColors.TextDim, fontSize = 12.sp,
            )
        }
        IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, s.delete, tint = TsmColors.Danger) }
    }
}
TSM_EOF

# ---------------------------------------------------------------- app/src/main/java/com/tennis/scoremanager/ui/screens/SummaryScreen.kt
cat > "$DEST/app/src/main/java/com/tennis/scoremanager/ui/screens/SummaryScreen.kt" << 'TSM_EOF'
package com.tennis.scoremanager.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tennis.scoremanager.MatchController
import com.tennis.scoremanager.data.Reports
import com.tennis.scoremanager.model.Side
import com.tennis.scoremanager.ui.BigButton
import com.tennis.scoremanager.ui.ConfirmDialog
import com.tennis.scoremanager.ui.GhostButton
import com.tennis.scoremanager.ui.LocalStrings
import com.tennis.scoremanager.ui.TsmColors

/** Ultima schermata: tutti i dati della partita, salvataggio, condivisione, nuova partita, uscita. */
@Composable
fun SummaryScreen(c: MatchController) {
    val s = LocalStrings.current
    val context = LocalContext.current
    val activity = LocalActivity.current
    val sm by c.summary.collectAsState()
    val done = sm ?: return
    val rec = done.record
    val state = done.state
    val names = remember(rec.id, s) { c.summaryNames() }
    val w = state.winner ?: Side.P1
    val lang = rec.options.lang
    var showSave by remember { mutableStateOf(false) }
    val kept by c.summaryKept.collectAsState()
    /** Uscita da confermare: true = "Esci", false = "Nuova partita". */
    var leaving by remember { mutableStateOf<Boolean?>(null) }
    fun leave(exit: Boolean) {
        if (exit) activity?.let { c.exitApp(it) } else c.newMatch()
    }
    BackHandler { }

    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(s.summaryTitle, fontSize = 26.sp, lineHeight = 30.sp, fontWeight = FontWeight.Black, color = TsmColors.TextMain)

            // Vincitore
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(TsmColors.player(w).copy(alpha = 0.16f)).padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Filled.EmojiEvents, null, tint = TsmColors.player(w), modifier = Modifier.size(52.dp))
                Text(s.winner.uppercase(), color = TsmColors.TextDim, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Text(names.side(w), color = TsmColors.player(w), fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                Spacer(Modifier.height(6.dp))
                Text(Reports.scoreLine(state, w), color = TsmColors.TextMain, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }

            // Tabellone finale
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(TsmColors.Surface).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (side in Side.entries) {
                    Row(Modifier.fillMaxWidth().height(if (rec.rules.doubles) 56.dp else 44.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(6.dp).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(TsmColors.player(side)))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            for (p in names.players(side)) {
                                Text(
                                    p, color = TsmColors.player(side), fontWeight = if (side == w) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 17.sp,
                                )
                            }
                        }
                        for (set in state.sets) {
                            Box(Modifier.width(44.dp), contentAlignment = Alignment.Center) {
                                Row(verticalAlignment = Alignment.Top) {
                                    Text(
                                        set.shown(side).toString(),
                                        color = if (set.winner == side) TsmColors.TextMain else TsmColors.TextDim,
                                        fontSize = if (set.matchTiebreak) 18.sp else 24.sp,
                                        fontWeight = if (set.winner == side) FontWeight.Black else FontWeight.Normal,
                                    )
                                    if (set.hasTiebreak && !set.matchTiebreak && set.winner != side) {
                                        Text(set.tb(side).toString(), color = TsmColors.TextDim, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                        Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) {
                            if (side == w) Icon(Icons.Filled.EmojiEvents, null, tint = TsmColors.Ball, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            // Dati partita
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(TsmColors.Surface).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                InfoRow(Icons.Filled.Timer, s.duration, Reports.duration(rec.clockMs))
                InfoRow(Icons.Filled.Event, s.date, Reports.date(rec.startedAt, lang).replaceFirstChar { it.uppercase() })
                InfoRow(Icons.Filled.Schedule, s.startTime, Reports.time(rec.startedAt, lang))
                InfoRow(Icons.Filled.Schedule, s.endTime, Reports.time(rec.endedAt, lang))
                if (rec.setup.club.isNotBlank()) InfoRow(Icons.Filled.Business, s.club, rec.setup.club)
                if (rec.setup.court.isNotBlank()) InfoRow(Icons.Filled.Tag, s.court, rec.setup.court)
                InfoRow(Icons.Filled.Place, s.place, Reports.place(rec, s))
                InfoRow(Icons.AutoMirrored.Filled.Rule, s.format, Reports.formatLabel(rec, s))
                InfoRow(Icons.Filled.BarChart, s.pointsWon, "${Reports.pointsWon(rec, Side.P1)} - ${Reports.pointsWon(rec, Side.P2)}")
                InfoRow(Icons.Filled.BarChart, s.gamesWon, "${Reports.gamesWon(state, Side.P1)} - ${Reports.gamesWon(state, Side.P2)}")
                if (rec.batteryStart.isNotEmpty() || rec.batteryEnd.isNotEmpty()) {
                    InfoRow(Icons.Filled.BatteryStd, s.bandsBattery, Reports.batteryLine(rec, s))
                }
            }
        }
        Column(
            Modifier.fillMaxWidth().background(TsmColors.Surface).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BigButton(s.saveHistory, Icons.Filled.Save, { showSave = true }, Modifier.weight(1f))
                BigButton(s.share, Icons.Filled.Share, {
                    c.shareIntent()?.let { runCatching { context.startActivity(it) }.onSuccess { c.markSummaryKept() } }
                }, Modifier.weight(1f), color = TsmColors.Orange, onColor = TsmColors.OnOrange)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // Senza salvataggio né condivisione il riepilogo si perde: un tocco solo non basta.
                GhostButton(s.newMatch, Icons.Filled.Replay, { if (kept) leave(false) else leaving = false }, Modifier.weight(1f))
                GhostButton(s.exit, Icons.AutoMirrored.Filled.ExitToApp, { if (kept) leave(true) else leaving = true }, Modifier.weight(1f))
            }
        }
    }

    if (showSave) SaveDialog(c) { showSave = false }
    leaving?.let { exit ->
        ConfirmDialog(
            icon = if (exit) Icons.AutoMirrored.Filled.ExitToApp else Icons.Filled.Replay,
            title = s.summaryLeaveTitle,
            text = s.summaryLeaveText,
            confirm = if (exit) s.exit else s.newMatch,
            onConfirm = {
                leaving = null
                leave(exit)
            },
            onDismiss = { leaving = null },
        )
    }
}

@Composable
private fun InfoRow(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = TsmColors.Ball, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, color = TsmColors.TextDim, modifier = Modifier.width(110.dp))
        Text(value, color = TsmColors.TextMain, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
    }
}

/** "Salva nello storico": nome del file, cartella scelta dall'utente e formati. */
@Composable
private fun SaveDialog(c: MatchController, onDismiss: () -> Unit) {
    val s = LocalStrings.current
    var name by remember { mutableStateOf(c.defaultFileName()) }
    var folder by remember { mutableStateOf(c.folderLabel()) }
    var txt by remember { mutableStateOf(true) }
    var json by remember { mutableStateOf(true) }
    var png by remember { mutableStateOf(true) }
    val treeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            c.setHistoryFolder(uri)
            folder = c.folderLabel()
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Save, null, tint = TsmColors.Ball) },
        title = { Text(s.saveDialogTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(s.fileName) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text(s.folder, color = TsmColors.TextDim)
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(TsmColors.SurfaceHigh)
                        .clickable { treeLauncher.launch(null) }.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Folder, null, tint = TsmColors.Orange)
                    Spacer(Modifier.width(8.dp))
                    Text(folder, color = TsmColors.TextMain, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Row {
                    TextButton(onClick = { treeLauncher.launch(null) }) { Text(s.chooseFolder) }
                    TextButton(onClick = {
                        c.setHistoryFolder(null)
                        folder = c.folderLabel()
                    }) { Text(s.defaultFolder, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
                CheckRow(s.formatReport, txt) { txt = it }
                CheckRow(s.formatData, json) { json = it }
                CheckRow(s.formatImage, png) { png = it }
            }
        },
        confirmButton = {
            Button(enabled = txt || json || png, onClick = {
                c.saveHistory(name, txt, json, png)
                onDismiss()
            }) { Text(s.save) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.cancel) } },
    )
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, onChange, colors = CheckboxDefaults.colors(checkedColor = TsmColors.Ball, checkmarkColor = TsmColors.OnBall))
        Text(label, color = TsmColors.TextMain)
    }
}
TSM_EOF

# ---------------------------------------------------------------- app/src/main/java/com/tennis/scoremanager/voice/Announcer.kt
cat > "$DEST/app/src/main/java/com/tennis/scoremanager/voice/Announcer.kt" << 'TSM_EOF'
package com.tennis.scoremanager.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import com.tennis.scoremanager.model.Lang
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

enum class TtsStatus { INIT, READY, MISSING_LANGUAGE, ERROR }

/** Motore di sintesi vocale installato sul telefono (Samsung, Google, ...). */
data class EngineOption(val pkg: String, val label: String)

/** Voce disponibile per la lingua corrente. */
data class VoiceOption(val name: String, val online: Boolean, val quality: Int)

/** Esito di "Genera file": file creati su [total]; [installed] = hanno preso il posto di quelli di prima. */
data class GenResult(val created: Int, val total: Int, val installed: Boolean)

/**
 * Legge le chiamate. Di norma ogni chiamata è detta dalla sintesi vocale in un'unica frase (suona naturale e
 * funziona offline con le voci installate); le registrazioni personalizzate, se ci sono, hanno la precedenza.
 * Con [useGeneratedFiles] si usano invece i file generati una volta dal TTS (utile con una voce online).
 * L'audio esce dal canale "media", quindi va anche su una cassa Bluetooth collegata al telefono.
 */
class Announcer(context: Context, private val voice: VoicePack) : TextToSpeech.OnInitListener {

    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private var enginePkg: String? = null
    private var preferredVoice: String? = null
    private var tts: TextToSpeech? = TextToSpeech(app, this)
    private val pending = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()
    private var job: Job? = null
    private var player: MediaPlayer? = null
    /** Aumentano a ogni [stop] e a ogni cambio di motore, voce o lingua: la generazione dei file li controlla. */
    private var stops = 0
    private var configs = 0

    private val _status = MutableStateFlow(TtsStatus.INIT)
    val status: StateFlow<TtsStatus> = _status
    private val _engines = MutableStateFlow<List<EngineOption>>(emptyList())
    val engines: StateFlow<List<EngineOption>> = _engines
    private val _voices = MutableStateFlow<List<VoiceOption>>(emptyList())
    val voices: StateFlow<List<VoiceOption>> = _voices
    private val _currentVoice = MutableStateFlow<String?>(null)
    val currentVoice: StateFlow<String?> = _currentVoice
    private val _currentEngine = MutableStateFlow<String?>(null)
    val currentEngineFlow: StateFlow<String?> = _currentEngine
    private val _playing = MutableStateFlow<String?>(null)
    /** Chiamata in corso: il [name] passato ad [announce] ("" se senza nome); null = nessuna. */
    val playing: StateFlow<String?> = _playing

    var enabled: Boolean = true
        set(value) {
            field = value
            if (!value) stop()
        }

    var useGeneratedFiles: Boolean = false

    var lang: Lang = Lang.IT
        set(value) {
            if (field != value) {
                field = value
                configs++
                applyLanguage(value)
            }
        }

    /** Pacchetto del motore effettivamente in uso (serve alle correzioni di pronuncia). */
    private val currentEngine: String?
        get() = enginePkg ?: runCatching { tts?.defaultEngine }.getOrNull()

    /** Sceglie motore (null = predefinito del telefono) e voce (null = la migliore offline). */
    fun configure(engine: String?, voiceName: String?) {
        configs++
        preferredVoice = voiceName
        if (engine != enginePkg) {
            enginePkg = engine
            stop()
            runCatching { tts?.shutdown() }
            _status.value = TtsStatus.INIT
            tts = TextToSpeech(app, this, engine)
        } else {
            applyLanguage(lang)
        }
    }

    override fun onInit(status: Int) {
        val t = tts
        if (status != TextToSpeech.SUCCESS || t == null) {
            _status.value = TtsStatus.ERROR
            return
        }
        t.setAudioAttributes(attrs)
        t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                utteranceId?.let { pending.remove(it)?.complete(true) }
            }

            @Suppress("OVERRIDE_DEPRECATION")
            override fun onError(utteranceId: String?) {
                utteranceId?.let { pending.remove(it)?.complete(false) }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                utteranceId?.let { pending.remove(it)?.complete(false) }
            }

            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                utteranceId?.let { pending.remove(it)?.complete(false) }
            }
        })
        _engines.value = runCatching { t.engines.map { EngineOption(it.name, it.label) } }.getOrDefault(emptyList())
        _currentEngine.value = currentEngine
        applyLanguage(lang)
    }

    /** Imposta la lingua e la voce: quella scelta se esiste, altrimenti la migliore installata sul telefono. */
    private fun applyLanguage(l: Lang) {
        val t = tts ?: return
        if (_status.value == TtsStatus.ERROR) return
        val loc = l.locale
        val res = runCatching { t.setLanguage(loc) }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
        if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
            _voices.value = emptyList()
            _status.value = TtsStatus.MISSING_LANGUAGE
            return
        }
        val all: List<Voice> = runCatching { t.voices?.toList() }.getOrNull().orEmpty()
            .filter { it.locale.language == loc.language && "notInstalled" !in it.features }
        _voices.value = all
            .map { VoiceOption(it.name, it.isNetworkConnectionRequired, it.quality) }
            .sortedWith(compareBy({ it.online }, { it.name }))
        val chosen = all.firstOrNull { it.name == preferredVoice }
            ?: all.filter { !it.isNetworkConnectionRequired }
                .maxWithOrNull(compareBy({ it.locale.country == loc.country }, { it.quality }))
        chosen?.let { runCatching { t.voice = it } }
        _currentVoice.value = runCatching { t.voice?.name }.getOrNull()
        _status.value = TtsStatus.READY
    }

    private sealed interface Part {
        val tag: String?

        data class Speech(val text: String, override val tag: String?) : Part
        /** File della frase [key]: se non si riesce a riprodurre, la frase la dice il TTS. */
        data class Audio(val file: File, val key: String, override val tag: String?) : Part
        data class Silence(val ms: Long) : Part {
            override val tag: String? get() = null
        }
    }

    private fun fileFor(key: String, l: Lang): File? =
        voice.customFor(key, l) ?: if (useGeneratedFiles) voice.generatedFor(key, l) else null

    /** Unisce i pezzi consecutivi senza file in un'unica frase TTS: suona molto più naturale. */
    private fun plan(segs: List<Seg>, l: Lang): List<Part> {
        val parts = mutableListOf<Part>()
        val text = StringBuilder()
        var tag: String? = null
        fun flush() {
            if (text.isNotBlank()) parts += Part.Speech(text.toString().trim(), tag)
            text.clear()
            tag = null
        }
        for (s in segs) {
            when (s) {
                is Seg.Pause -> { flush(); parts += Part.Silence(s.ms) }
                is Seg.Clip -> {
                    val f = fileFor(s.key, l)
                    if (f != null) {
                        flush()
                        parts += Part.Audio(f, s.key, s.tag)
                    } else {
                        if (s.tag != null) { flush(); tag = s.tag }
                        text.append(Phrases.text(s.key, l)).append(' ')
                    }
                }
                is Seg.Say -> {
                    if (s.tag != null) { flush(); tag = s.tag }
                    text.append(s.text).append(' ')
                }
            }
        }
        flush()
        return parts
    }

    /**
     * Legge una chiamata interrompendo quella in corso. [onTag] viene chiamato quando parte un pezzo
     * con tag (es. "gioco" che avvia il tempo partita); se l'audio è spento o la chiamata viene
     * interrotta, i tag vengono comunque notificati subito. [force] legge anche ad audio spento (prova voce).
     * [name] distingue la chiamata in [playing] (es. la prova voce, che il suo tasto può fermare).
     */
    fun announce(segs: List<Seg>, force: Boolean = false, name: String = "", onTag: (String) -> Unit = {}) {
        val tags = segs.mapNotNull {
            when (it) {
                is Seg.Clip -> it.tag
                is Seg.Say -> it.tag
                is Seg.Pause -> null
            }
        }
        stop()
        // In Logcat (filtro "Announcer") si legge ogni chiamata: comodo per controllare le frasi.
        Log.d("Announcer", CallBuilder(lang).render(segs))
        if ((!enabled && !force) || segs.isEmpty()) {
            tags.forEach(onTag)
            return
        }
        val l = lang
        val fired = mutableSetOf<String>()
        // LAZY: [job] è assegnato prima che parta, così anche una chiamata che finisce subito azzera [playing].
        val j = scope.launch(start = CoroutineStart.LAZY) {
            try {
                for (p in plan(segs, l)) {
                    p.tag?.let { fired += it; onTag(it) }
                    when (p) {
                        is Part.Silence -> delay(p.ms)
                        is Part.Audio -> if (!playFile(p.file)) {
                            // File rovinato: non si usa più e la frase la dice la sintesi vocale (non si salta).
                            voice.markBroken(p.file)
                            speak(Pronunciation.fix(Phrases.text(p.key, l), l, currentEngine))
                        }
                        is Part.Speech -> speak(Pronunciation.fix(p.text, l, currentEngine))
                    }
                }
            } finally {
                tags.filter { it !in fired }.forEach(onTag)
                if (job === coroutineContext[Job]) {
                    job = null
                    _playing.value = null
                }
            }
        }
        job = j
        _playing.value = name
        j.start()
    }

    fun stop() {
        stops++
        job?.cancel()
        job = null
        _playing.value = null
        runCatching { tts?.stop() }
        pending.values.forEach { it.complete(false) }
        pending.clear()
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
    }

    private suspend fun speak(text: String) {
        val t = tts ?: return
        if (_status.value != TtsStatus.READY) return
        val id = UUID.randomUUID().toString()
        val done = CompletableDeferred<Boolean>()
        pending[id] = done
        val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1f) }
        if (t.speak(text, TextToSpeech.QUEUE_ADD, params, id) != TextToSpeech.SUCCESS) {
            pending.remove(id)
            return
        }
        withTimeoutOrNull(3_000L + text.length * 150L) { done.await() }
        pending.remove(id)
    }

    /** Riproduce il file; false se il telefono non riesce a leggerlo. */
    private suspend fun playFile(file: File): Boolean {
        val mp = MediaPlayer()
        player = mp
        try {
            mp.setAudioAttributes(attrs)
            mp.setDataSource(file.absolutePath)
            mp.prepare()
            // Allo scadere del tempo (null) il file è comunque stato suonato: conta come riuscito.
            val ok = withTimeoutOrNull(mp.duration.toLong().coerceAtLeast(500L) + 1_500L) {
                suspendCancellableCoroutine<Boolean> { cont ->
                    mp.setOnCompletionListener { if (cont.isActive) cont.resume(true) }
                    mp.setOnErrorListener { _, _, _ -> if (cont.isActive) cont.resume(false); true }
                    mp.start()
                }
            } ?: true
            if (!ok) Log.w("Announcer", "Errore durante la riproduzione: ${file.name}")
            return ok
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("Announcer", "File audio non leggibile: ${file.name}", e)
            return false
        } finally {
            if (player === mp) player = null
            runCatching { mp.release() }
        }
    }

    /**
     * Genera i file vocali di [l] con la voce scelta (anche una voce online: dopo funzionano senza rete).
     * I file nuovi si creano in una cartella a parte e prendono il posto di quelli di prima solo se la generazione
     * arriva in fondo e non ne crea meno di prima; se a metà cambiano motore, voce o lingua si lascia perdere.
     */
    suspend fun generateVoicePack(l: Lang, onProgress: (Int, Int) -> Unit): GenResult {
        val keys = Phrases.keys
        val none = GenResult(0, keys.size, false)
        withTimeoutOrNull(5_000) { while (_status.value == TtsStatus.INIT) delay(100) }
        val t = tts ?: return none
        if (_status.value == TtsStatus.ERROR) return none
        stop()
        applyLanguage(l)
        if (_status.value != TtsStatus.READY) {
            applyLanguage(lang)
            return none
        }
        val config = configs
        val before = voice.generatedCount(l)
        val dir = voice.newGeneratedDir(l)
        val engine = currentEngine
        var ok = 0
        var installed = false
        try {
            for ((i, key) in keys.withIndex()) {
                if (tts !== t || configs != config) break
                if (synthesize(t, Pronunciation.fix(Phrases.text(key, l), l, engine), File(dir, "$key.wav"))) ok++
                onProgress(i + 1, keys.size)
            }
            val complete = tts === t && configs == config
            installed = complete && ok > 0 && ok >= before && voice.installGenerated(l, dir)
        } finally {
            if (!installed) dir.deleteRecursively()
            if (tts === t) applyLanguage(lang)
        }
        return GenResult(ok, keys.size, installed)
    }

    /**
     * Un file della generazione. Se intanto [stop] interrompe la sintesi (una chiamata, la prova voce, l'audio spento)
     * si riprova, al massimo tre volte: [TextToSpeech.stop] ferma anche i file in scrittura.
     */
    private suspend fun synthesize(t: TextToSpeech, text: String, f: File): Boolean {
        repeat(3) {
            val stopsBefore = stops
            val id = "gen_${UUID.randomUUID()}"
            val done = CompletableDeferred<Boolean>()
            pending[id] = done
            val r = runCatching { t.synthesizeToFile(text, Bundle(), f, id) }.getOrDefault(TextToSpeech.ERROR)
            val good = r == TextToSpeech.SUCCESS && withTimeoutOrNull(20_000) { done.await() } == true
            pending.remove(id)
            if (good && f.length() > VoicePack.MIN_BYTES) return true
            f.delete()
            if (stops == stopsBefore) return false
        }
        return false
    }

    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
    }
}
TSM_EOF

# ---------------------------------------------------------------- app/src/main/java/com/tennis/scoremanager/voice/CallWords.kt
cat > "$DEST/app/src/main/java/com/tennis/scoremanager/voice/CallWords.kt" << 'TSM_EOF'
package com.tennis.scoremanager.voice

import com.tennis.scoremanager.model.Lang

/**
 * Parole e ordine delle chiamate dell'arbitro in una lingua, presi dai testi ufficiali per i giudici di sedia:
 * ITF (inglese), FFT «L'arbitrage en 255 questions» e ITF in francese, RFET «Deberes y procedimientos» (spagnolo),
 * DTB (tedesco), FPT (portoghese). Tutto in lettere: i motori vocali leggono male cifre e trattini ("6-4").
 */
internal abstract class CallWords {
    /** Frasi fisse: una per ciascuna chiave di [Phrases.FIXED_KEYS]. */
    abstract val fixed: Map<String, String>

    /** Numeri da 0 a [Phrases.MAX_NUMBER] (tie-break e punteggi dei set a fine partita). */
    abstract val numbers: List<String>

    /** Punti del game: 0, 15, 30, 40. */
    abstract val points: List<String>

    fun number(n: Int): String = numbers.getOrElse(n) { n.toString() }

    /** Lo zero nei set letti a fine partita ([Phrases.SET_ZERO]): di norma è il numero. */
    open val setZero: String get() = number(0)

    /** Punteggio del game dal lato di chi serve (mai 0-0 né 40-40): "quindici zero", "trenta pari". */
    open fun score(s: Int, r: Int): String =
        if (s == r) "${points[s]} ${fixed.getValue("all")}" else "${points[s]} ${points[r]}"

    /** Game del set dal lato di chi conduce: "tre giochi a due". */
    abstract fun games(a: Int, b: Int): String

    /** Game pari: "due giochi pari". */
    abstract fun gamesAll(n: Int): String

    /** "Rossi al servizio" (nome prima) oppure "Au service Dupont" (nome dopo). */
    open val nameBeforeToServe: Boolean = true

    /**
     * 10-6 … 10-9 detti di seguito suonano come un numero solo: "dix huit" = diciotto, "diez ocho" = "dieciocho",
     * "dez oito" = "dezoito" (verificato trascrivendo le voci Google). Lì si dice "a" anche dove di solito no.
     */
    protected fun soundsLikeOneNumber(a: Int, b: Int): Boolean = a == 10 && b in 6..9

    /** Nel tie-break, tra i due numeri si dice "a" ("tre a uno Rossi")? */
    open fun tiebreakTo(a: Int, b: Int): Boolean = soundsLikeOneNumber(a, b)

    /** Nei set letti a fine partita, tra i due numeri si dice "a"? */
    open fun setScoreTo(a: Int, b: Int): Boolean = soundsLikeOneNumber(a, b)

    companion object {
        fun of(lang: Lang): CallWords = when (lang) {
            Lang.IT -> ItWords
            Lang.EN -> EnWords
            Lang.FR -> FrWords
            Lang.DE -> DeWords
            Lang.ES -> EsWords
            Lang.PT -> PtWords
        }
    }
}

internal object ItWords : CallWords() {
    override val fixed = mapOf(
        "first_set" to "primo set",
        "to_serve" to "al servizio",
        "play" to "gioco",
        "deuce" to "parità",
        "advantage" to "vantaggio",
        "deciding_point" to "punto decisivo",
        "game" to "gioco",
        "leads" to "conduce",
        "sets_1_0" to "un set a zero",
        "sets_all_1" to "un set pari",
        "tiebreak" to "tie-break",
        "match_tiebreak" to "super tie-break",
        "to" to "a",
        "all" to "pari",
        "game_set_match" to "gioco, set, partita",
        "change_ends" to "cambio campo",
        "correction" to "correzione",
    )
    override val numbers = listOf(
        "zero", "uno", "due", "tre", "quattro", "cinque", "sei", "sette", "otto", "nove", "dieci",
        "undici", "dodici", "tredici", "quattordici", "quindici", "sedici", "diciassette", "diciotto",
        "diciannove", "venti", "ventuno", "ventidue", "ventitré", "ventiquattro", "venticinque",
        "ventisei", "ventisette", "ventotto", "ventinove", "trenta",
    )
    override val points = listOf("zero", "quindici", "trenta", "quaranta")
    private fun g(n: Int) = if (n == 1) "un gioco" else "${number(n)} giochi"
    override fun games(a: Int, b: Int) = "${g(a)} a ${number(b)}"
    override fun gamesAll(n: Int) = "${g(n)} pari"
    override fun tiebreakTo(a: Int, b: Int) = true
    override fun setScoreTo(a: Int, b: Int) = false
}

internal object EnWords : CallWords() {
    override val fixed = mapOf(
        "first_set" to "first set",
        "to_serve" to "to serve",
        "play" to "play",
        "deuce" to "deuce",
        "advantage" to "advantage",
        "deciding_point" to "deciding point",
        "game" to "game",
        "leads" to "leads",
        "sets_1_0" to "one set to love",
        "sets_all_1" to "one set all",
        "tiebreak" to "tie-break",
        "match_tiebreak" to "match tie-break",
        "to" to "to",
        "all" to "all",
        "game_set_match" to "game, set and match",
        "change_ends" to "change ends",
        "correction" to "correction",
    )
    override val numbers = listOf(
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
        "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen",
        "nineteen", "twenty", "twenty-one", "twenty-two", "twenty-three", "twenty-four", "twenty-five",
        "twenty-six", "twenty-seven", "twenty-eight", "twenty-nine", "thirty",
    )
    override val points = listOf("love", "fifteen", "thirty", "forty")
    // ITF: i set si leggono "six love, six four"; nel tie-break invece "one zero".
    override val setZero = "love"
    private fun g(n: Int) = if (n == 1) "one game" else "${number(n)} games"
    override fun games(a: Int, b: Int) = "${g(a)} to ${if (b == 0) "love" else number(b)}"
    override fun gamesAll(n: Int) = "${g(n)} all"
    override fun tiebreakTo(a: Int, b: Int) = false
    override fun setScoreTo(a: Int, b: Int) = false
}

/** Francese FFT: il set è la "manche" (tranne in "jeu, set et match"), 15-15 è "quinze A", il nome va dopo "au service". */
internal object FrWords : CallWords() {
    override val fixed = mapOf(
        "first_set" to "première manche",
        "to_serve" to "au service",
        "play" to "jouez",
        "deuce" to "égalité",
        "advantage" to "avantage",
        "deciding_point" to "point décisif",
        "game" to "jeu",
        "leads" to "mène",
        "sets_1_0" to "une manche à zéro",
        "sets_all_1" to "une manche partout",
        "tiebreak" to "jeu décisif",
        "match_tiebreak" to "super jeu décisif",
        "to" to "à",
        "all" to "partout",
        "game_set_match" to "jeu, set et match",
        "change_ends" to "changement de côté",
        "correction" to "correction",
    )
    override val numbers = listOf(
        "zéro", "un", "deux", "trois", "quatre", "cinq", "six", "sept", "huit", "neuf", "dix",
        "onze", "douze", "treize", "quatorze", "quinze", "seize", "dix-sept", "dix-huit",
        "dix-neuf", "vingt", "vingt et un", "vingt-deux", "vingt-trois", "vingt-quatre", "vingt-cinq",
        "vingt-six", "vingt-sept", "vingt-huit", "vingt-neuf", "trente",
    )
    override val points = listOf("zéro", "quinze", "trente", "quarante")
    override fun score(s: Int, r: Int) = if (s == r) "${points[s]} A" else "${points[s]}-${points[r]}"
    private fun g(n: Int) = if (n == 1) "un jeu" else "${number(n)} jeux"
    override fun games(a: Int, b: Int) = "${g(a)} à ${number(b)}"
    override fun gamesAll(n: Int) = "${g(n)} partout"
    override val nameBeforeToServe = false
}

/**
 * Spagnolo RFET: "iguales" per i pari e per 40-40, "gana" per chi conduce, "al servicio" prima del nome,
 * "jueguen" per iniziare. "uno" in fondo alla frase ("dos juegos a uno"), "un" davanti al nome ("un juego").
 */
internal object EsWords : CallWords() {
    override val fixed = mapOf(
        "first_set" to "primer set",
        "to_serve" to "al servicio",
        "play" to "jueguen",
        "deuce" to "iguales",
        "advantage" to "ventaja",
        "deciding_point" to "punto decisivo",
        "game" to "juego",
        "leads" to "gana",
        "sets_1_0" to "un set a cero",
        "sets_all_1" to "un set iguales",
        "tiebreak" to "tie-break",
        "match_tiebreak" to "súper tie-break",
        "to" to "a",
        "all" to "iguales",
        "game_set_match" to "juego, set y partido",
        "change_ends" to "cambio de lado",
        "correction" to "corrección",
    )
    override val numbers = listOf(
        "cero", "uno", "dos", "tres", "cuatro", "cinco", "seis", "siete", "ocho", "nueve", "diez",
        "once", "doce", "trece", "catorce", "quince", "dieciséis", "diecisiete", "dieciocho",
        "diecinueve", "veinte", "veintiuno", "veintidós", "veintitrés", "veinticuatro", "veinticinco",
        "veintiséis", "veintisiete", "veintiocho", "veintinueve", "treinta",
    )
    override val points = listOf("cero", "quince", "treinta", "cuarenta")
    private fun g(n: Int) = if (n == 1) "un juego" else "${number(n)} juegos"
    override fun games(a: Int, b: Int) = "${g(a)} a ${number(b)}"
    override fun gamesAll(n: Int) = "${g(n)} iguales"
    override val nameBeforeToServe = false
}

/**
 * Tedesco DTB (modulo «Korrekte Ansagen» del BTV, documentazione Swiss Tennis): "Aufschlag" prima del nome,
 * "beide" per i pari, i punteggi con "zu" ("drei zu zwei", come si legge "3:2"), ma i punti del game senza.
 * "eins" da solo, "ein" davanti al nome ("ein Spiel beide").
 */
internal object DeWords : CallWords() {
    override val fixed = mapOf(
        "first_set" to "erster Satz",
        "to_serve" to "Aufschlag",
        "play" to "spielen",
        "deuce" to "Einstand",
        "advantage" to "Vorteil",
        "deciding_point" to "entscheidender Punkt",
        "game" to "Spiel",
        "leads" to "führt",
        "sets_1_0" to "eins zu null in Sätzen",
        "sets_all_1" to "ein Satz beide",
        "tiebreak" to "Tie-Break",
        "match_tiebreak" to "Match-Tie-Break",
        "to" to "zu",
        "all" to "beide",
        "game_set_match" to "Spiel, Satz und Sieg",
        "change_ends" to "Seitenwechsel",
        "correction" to "Korrektur",
    )
    override val numbers = listOf(
        "null", "eins", "zwei", "drei", "vier", "fünf", "sechs", "sieben", "acht", "neun", "zehn",
        "elf", "zwölf", "dreizehn", "vierzehn", "fünfzehn", "sechzehn", "siebzehn", "achtzehn",
        "neunzehn", "zwanzig", "einundzwanzig", "zweiundzwanzig", "dreiundzwanzig", "vierundzwanzig", "fünfundzwanzig",
        "sechsundzwanzig", "siebenundzwanzig", "achtundzwanzig", "neunundzwanzig", "dreißig",
    )
    override val points = listOf("null", "fünfzehn", "dreißig", "vierzig")
    override fun games(a: Int, b: Int) = "${number(a)} zu ${number(b)}"
    override fun gamesAll(n: Int) = if (n == 1) "ein Spiel beide" else "${number(n)} beide"
    override val nameBeforeToServe = false
    override fun tiebreakTo(a: Int, b: Int) = true
    override fun setScoreTo(a: Int, b: Int) = true
}

/**
 * Portoghese: struttura del copione ufficiale FPT («Deveres e Procedimentos para Árbitros», l'unico pubblicato),
 * con parole che vanno bene in Portogallo e in Brasile ("jogo" e non "game", "partida" e non "encontro"),
 * "iguais" per i pari e grafia brasiliana dei numeri (la voce preferita è pt-BR).
 */
internal object PtWords : CallWords() {
    override val fixed = mapOf(
        "first_set" to "primeiro set",
        "to_serve" to "ao serviço",
        "play" to "joguem",
        "deuce" to "iguais",
        "advantage" to "vantagem",
        "deciding_point" to "ponto decisivo",
        "game" to "jogo",
        "leads" to "vence por",
        "sets_1_0" to "um set a zero",
        // "um set a um" suona "um sete a um" (7-1): al singolare "set" si pronuncia come "sete".
        "sets_all_1" to "sets iguais",
        "tiebreak" to "tie-break",
        "match_tiebreak" to "tie-break decisivo",
        "to" to "a",
        "all" to "iguais",
        "game_set_match" to "jogo, set e partida",
        "change_ends" to "troca de lado",
        "correction" to "correção",
    )
    override val numbers = listOf(
        "zero", "um", "dois", "três", "quatro", "cinco", "seis", "sete", "oito", "nove", "dez",
        "onze", "doze", "treze", "catorze", "quinze", "dezesseis", "dezessete", "dezoito",
        "dezenove", "vinte", "vinte e um", "vinte e dois", "vinte e três", "vinte e quatro", "vinte e cinco",
        "vinte e seis", "vinte e sete", "vinte e oito", "vinte e nove", "trinta",
    )
    override val points = listOf("zero", "quinze", "trinta", "quarenta")
    override fun score(s: Int, r: Int) = if (s == r) "${points[s]} iguais" else "${points[s]}-${points[r]}"
    private fun g(n: Int) = if (n == 1) "um jogo" else "${number(n)} jogos"
    override fun games(a: Int, b: Int) = "${g(a)} a ${number(b)}"
    override fun gamesAll(n: Int) = if (n == 1) "um jogo igual" else "${g(n)} iguais"
    override fun tiebreakTo(a: Int, b: Int) = true
}
TSM_EOF

# ---------------------------------------------------------------- app/src/main/java/com/tennis/scoremanager/voice/Calls.kt
cat > "$DEST/app/src/main/java/com/tennis/scoremanager/voice/Calls.kt" << 'TSM_EOF'
package com.tennis.scoremanager.voice

import com.tennis.scoremanager.model.Lang
import com.tennis.scoremanager.model.MatchState
import com.tennis.scoremanager.model.Side
import com.tennis.scoremanager.model.Transition

/** Un pezzo di chiamata: frase registrata (file locale o TTS di riserva), testo TTS (nomi) o pausa. */
sealed interface Seg {
    /** Frase del catalogo: se esiste il file audio locale si usa quello, altrimenti TTS. */
    data class Clip(val key: String, val tag: String? = null) : Seg

    /** Testo sempre letto dal TTS (nomi dei giocatori). */
    data class Say(val text: String, val tag: String? = null) : Seg

    data class Pause(val ms: Long) : Seg
}

/** Nomi usati nelle chiamate: squadra/giocatore per lato, e singolo giocatore del doppio. */
interface CallNames {
    fun side(side: Side): String
    fun player(side: Side, index: Int): String
}

/**
 * Catalogo delle frasi fisse. Ogni chiave corrisponde a un file `<chiave>.wav|.mp3|.ogg|.m4a`
 * nella cartella voce della lingua; il testo serve per generare il file o come riserva TTS.
 * Le chiavi sono le stesse in tutte le lingue; parole e ordine stanno in [CallWords].
 */
object Phrases {
    const val MAX_NUMBER = 30

    val FIXED_KEYS = listOf(
        "first_set", "to_serve", "play", "deuce", "advantage", "deciding_point", "game", "leads",
        "sets_1_0", "sets_all_1", "tiebreak", "match_tiebreak", "to", "all", "game_set_match",
        "change_ends", "correction",
    )

    /** Tutte le chiavi del catalogo, nell'ordine in cui vengono generate. */
    val keys: List<String> by lazy {
        buildList {
            addAll(FIXED_KEYS)
            for (s in 0..3) for (r in 0..3) {
                if ((s == 0 && r == 0) || (s == 3 && r == 3)) continue
                add("score_${s}_$r")
            }
            // Game durante il set: chi conduce ha al massimo 5 game, oppure 6-5.
            for (a in 1..6) for (b in 0 until a) {
                if (a == 6 && b != 5) continue
                add("games_${a}_$b")
            }
            for (n in 1..6) add("games_all_$n")
            for (n in 0..MAX_NUMBER) add("num_$n")
            add(SET_ZERO)
        }
    }

    /** Lo zero nei set letti a fine partita: "zero" quasi ovunque, ma in inglese "love" ("six love"). */
    const val SET_ZERO = "set_zero"

    /** Chiavi che in alcune lingue dicono la stessa cosa di un'altra: lì basta il file dell'altra. */
    private val sameAs = mapOf(SET_ZERO to "num_0")

    /** Chiave con lo stesso testo di [key] in [lang] (il suo file va bene anche per [key]), se c'è. */
    fun alias(key: String, lang: Lang): String? = sameAs[key]?.takeIf { text(it, lang) == text(key, lang) }

    fun text(key: String, lang: Lang): String {
        val w = CallWords.of(lang)
        w.fixed[key]?.let { return it }
        val parts = key.split('_')
        return when {
            key.startsWith("score_") -> w.score(parts[1].toInt(), parts[2].toInt())
            key.startsWith("games_all_") -> w.gamesAll(parts[2].toInt())
            key.startsWith("games_") -> w.games(parts[1].toInt(), parts[2].toInt())
            key.startsWith("num_") -> w.number(parts[1].toInt())
            key == SET_ZERO -> w.setZero
            else -> key
        }
    }
}

/** Costruisce le chiamate dell'arbitro secondo le regole concordate. */
class CallBuilder(private val lang: Lang) {

    private val words = CallWords.of(lang)

    companion object {
        /** Tag del segmento "gioco"/"play" iniziale: fa partire il tempo partita. */
        const val TAG_PLAY = "play"
        private const val SHORT = 300L
        private const val MEDIUM = 500L
        private const val START_GAP = 2000L
    }

    private fun num(n: Int): Seg = if (n in 0..Phrases.MAX_NUMBER) Seg.Clip("num_$n") else Seg.Say(n.toString())

    private fun serverName(s: MatchState, names: CallNames): String =
        if (s.rules.doubles) names.player(s.server, s.serverPlayer) else names.side(s.server)

    /** "Primo set" · "[nome] al servizio" · "gioco" con due secondi tra una frase e l'altra. */
    fun start(s: MatchState, names: CallNames): List<Seg> = listOf(
        Seg.Clip("first_set"),
        Seg.Pause(START_GAP),
    ) + toServe(serverName(s, names)) + listOf(
        Seg.Pause(START_GAP),
        Seg.Clip("play", tag = TAG_PLAY),
    )

    /** "Rossi al servizio", "Au service Dupont", "Aufschlag Müller". */
    fun toServe(name: String): List<Seg> =
        if (words.nameBeforeToServe) listOf(Seg.Say(name), Seg.Clip("to_serve")) else listOf(Seg.Clip("to_serve"), Seg.Say(name))

    /** Ripresa dopo una sospensione. */
    fun resume(): List<Seg> = listOf(Seg.Clip("play"))

    fun afterPoint(after: MatchState, t: Transition, names: CallNames): List<Seg> {
        val out = mutableListOf<Seg>()
        when {
            t.matchWinner != null -> out += matchEnd(after, t.matchWinner, names)
            t.setWinner != null -> {
                out += Seg.Clip("game")
                out += Seg.Say(names.side(t.setWinner))
                out += Seg.Pause(SHORT)
                out += setsStanding(after, names)
                if (t.matchTiebreakStarted) {
                    out += Seg.Pause(SHORT)
                    out += Seg.Clip("match_tiebreak")
                }
            }
            t.gameWinner != null -> {
                out += Seg.Clip("game")
                out += Seg.Say(names.side(t.gameWinner))
                out += Seg.Pause(SHORT)
                out += gamesStanding(after, names)
                if (t.tiebreakStarted) out += Seg.Clip("tiebreak")
            }
            after.inTiebreak -> out += tiebreakScore(after, names)
            else -> out += pointScore(after, names)
        }
        if (t.changeEnds && t.matchWinner == null) {
            out += Seg.Pause(MEDIUM)
            out += Seg.Clip("change_ends")
        }
        return out
    }

    /** "Correzione" seguita dal punteggio attuale. */
    fun correction(s: MatchState, names: CallNames): List<Seg> = listOf(Seg.Clip("correction")) + standing(s, names)

    /** Punteggio attuale, detto nel modo più utile per il momento della partita. */
    fun standing(s: MatchState, names: CallNames): List<Seg> = when {
        s.isFinished -> emptyList()
        s.inTiebreak -> tiebreakScore(s, names)
        s.pt1 == 0 && s.pt2 == 0 -> when {
            s.g1 != 0 || s.g2 != 0 -> gamesStanding(s, names)
            s.sets.isNotEmpty() -> setsStanding(s, names)
            else -> emptyList()
        }
        else -> pointScore(s, names)
    }

    /** Game normale: sempre dal punto di vista di chi serve ("quindici zero", "zero quaranta"). */
    fun pointScore(s: MatchState, names: CallNames): List<Seg> {
        val sp = s.points(s.server)
        val rp = s.points(s.receiver)
        if (sp >= 3 && rp >= 3) {
            if (sp == rp) {
                return if (s.rules.noAd) listOf(Seg.Clip("deuce"), Seg.Clip("deciding_point")) else listOf(Seg.Clip("deuce"))
            }
            val leader = if (sp > rp) s.server else s.receiver
            return listOf(Seg.Clip("advantage"), Seg.Say(names.side(leader)))
        }
        return listOf(Seg.Clip("score_${sp}_$rp"))
    }

    /** Tie-break: sempre dal punto di vista di chi conduce ("tre a uno Rossi", "sei pari"). */
    fun tiebreakScore(s: MatchState, names: CallNames): List<Seg> {
        val a = maxOf(s.pt1, s.pt2)
        val b = minOf(s.pt1, s.pt2)
        if (a == b) return listOf(num(a), Seg.Clip("all"))
        val leader = if (s.pt1 > s.pt2) Side.P1 else Side.P2
        return pair(num(a), num(b), words.tiebreakTo(a, b)) + Seg.Say(names.side(leader))
    }

    /** Due numeri di seguito, con "a" in mezzo se la lingua lo vuole ("tre a uno", "three one"). */
    private fun pair(a: Seg, b: Seg, to: Boolean): List<Seg> = if (to) listOf(a, Seg.Clip("to"), b) else listOf(a, b)

    /** Game di un set a fine partita: lo zero è [Phrases.SET_ZERO] ("six love"), nel match tie-break resta "zero". */
    private fun setNum(n: Int, matchTiebreak: Boolean): Seg = if (n == 0 && !matchTiebreak) Seg.Clip(Phrases.SET_ZERO) else num(n)

    /** "[nome] conduce tre giochi a due" oppure "due giochi pari". */
    fun gamesStanding(s: MatchState, names: CallNames): List<Seg> {
        if (s.g1 == s.g2) return listOf(Seg.Clip("games_all_${s.g1.coerceIn(1, 6)}"))
        val leader = if (s.g1 > s.g2) Side.P1 else Side.P2
        val a = maxOf(s.g1, s.g2)
        val b = minOf(s.g1, s.g2)
        return listOf(Seg.Say(names.side(leader)), Seg.Clip("leads"), Seg.Clip("games_${a}_$b"))
    }

    /** "[nome] conduce un set a zero" oppure "un set pari". */
    fun setsStanding(s: MatchState, names: CallNames): List<Seg> {
        val s1 = s.setsWon(Side.P1)
        val s2 = s.setsWon(Side.P2)
        if (s1 == s2) return listOf(Seg.Clip("sets_all_1"))
        val leader = if (s1 > s2) Side.P1 else Side.P2
        return listOf(Seg.Say(names.side(leader)), Seg.Clip("leads"), Seg.Clip("sets_1_0"))
    }

    /** "Gioco, set, partita [nome], sei quattro, tre sei, sette cinque" (punteggi dal lato del vincitore). */
    fun matchEnd(s: MatchState, winner: Side, names: CallNames): List<Seg> {
        val out = mutableListOf<Seg>(Seg.Clip("game_set_match"), Seg.Say(names.side(winner)))
        for (set in s.sets) {
            val a = set.shown(winner)
            val b = set.shown(winner.other)
            out += Seg.Pause(SHORT)
            out += pair(setNum(a, set.matchTiebreak), setNum(b, set.matchTiebreak), words.setScoreTo(a, b))
        }
        return out
    }

    /** Testo leggibile della chiamata (per test, log e riserva TTS). */
    fun render(segs: List<Seg>): String = segs.mapNotNull {
        when (it) {
            is Seg.Clip -> Phrases.text(it.key, lang)
            is Seg.Say -> it.text
            is Seg.Pause -> null
        }
    }.joinToString(" ")
}
TSM_EOF

# ---------------------------------------------------------------- app/src/main/java/com/tennis/scoremanager/voice/Pronunciation.kt
cat > "$DEST/app/src/main/java/com/tennis/scoremanager/voice/Pronunciation.kt" << 'TSM_EOF'
package com.tennis.scoremanager.voice

import com.tennis.scoremanager.model.Lang

/**
 * Correzioni di pronuncia per la sintesi vocale, verificate trascrivendo l'audio dei motori reali:
 * - Samsung legge "primo set" come "primo settembre": "sèt" (con l'accento) non viene espanso da nessun motore;
 * - "tie-break" diventa "die breaka" (Samsung) o "time break" (Google): "taibrèk" è letto giusto da entrambi;
 * - Samsung pronuncia male "gioco" isolato (sembra "Giacomo"); dentro una frase va bene, da solo si usa "giuoco";
 * - tedesco (Google): "Tie-Break" diventa "Teilbreg" o "Tea Break", "Taibreak" è letto giusto; senza una virgola
 *   prima, "sechs beide Taibreak" si impasta in "sechs bei Detailbreak";
 * - francese (Google): "à" da sola (il file vocale della chiave "to") è letta "a accento grave".
 * Il testo mostrato a schermo e nel file LEGGIMI resta quello normale.
 */
object Pronunciation {
    const val SAMSUNG = "com.samsung.SMT"

    private val wordSet = Regex("\\bset\\b", RegexOption.IGNORE_CASE)
    private val tieBreak = Regex("(?<!super )tie-break", RegexOption.IGNORE_CASE)
    private val tieBreakDe = Regex("tie-break", RegexOption.IGNORE_CASE)
    private val beforeTieBreakDe = Regex("(?<=[^\\s,-]) +(?=tie-break)", RegexOption.IGNORE_CASE)

    fun fix(text: String, lang: Lang, engine: String?): String = when (lang) {
        Lang.IT -> {
            var t = tieBreak.replace(text, "taibrèk")
            t = wordSet.replace(t, "sèt")
            if (engine == SAMSUNG && t.trim().trimEnd('.', '!', ',').equals("gioco", ignoreCase = true)) t = "giuoco"
            t
        }
        Lang.DE -> tieBreakDe.replace(beforeTieBreakDe.replace(text, ", "), "Taibreak")
        Lang.FR -> if (text.trim() == "à") "a" else text
        else -> text
    }
}
TSM_EOF

# ---------------------------------------------------------------- app/src/main/java/com/tennis/scoremanager/voice/VoicePack.kt
cat > "$DEST/app/src/main/java/com/tennis/scoremanager/voice/VoicePack.kt" << 'TSM_EOF'
package com.tennis.scoremanager.voice

import android.content.Context
import android.net.Uri
import com.tennis.scoremanager.model.Lang
import com.tennis.scoremanager.ui.Strings
import java.io.File
import java.io.IOException
import java.util.zip.CRC32
import java.util.zip.CheckedInputStream
import java.util.zip.ZipException
import java.util.zip.ZipFile

/**
 * Cartella locale con i file audio delle chiamate (uso senza internet).
 *
 *   Android/data/com.tennis.scoremanager/files/voice/it/        registrazioni personalizzate (priorità)
 *   Android/data/com.tennis.scoremanager/files/voice/it/tts/    file generati dal TTS del telefono
 *
 * Il nome di ogni file è la chiave della frase (vedi LEGGIMI.txt), es. `score_1_0.mp3` = "quindici zero".
 * LEGGIMI.txt ha sempre questo nome (lo citano le guide); le spiegazioni in testa sono nella lingua dell'app.
 */
class VoicePack(private val context: Context) {

    companion object {
        val EXTS = listOf("wav", "mp3", "ogg", "m4a", "aac", "flac")

        /** File più piccoli di così sono vuoti o rovinati: non si usano. */
        const val MIN_BYTES = 64L
    }

    private val custom = mutableMapOf<Lang, Map<String, File>>()
    private val generated = mutableMapOf<Lang, Map<String, File>>()

    /** File che non si sono potuti riprodurre: esclusi fino al riavvio dell'app, o finché non vengono sostituiti. */
    private val broken = mutableSetOf<String>()
    private fun id(f: File) = "${f.absolutePath}|${f.length()}|${f.lastModified()}"

    val baseDir: File
        get() = File(context.getExternalFilesDir(null) ?: context.filesDir, "voice")

    fun dir(lang: Lang): File = File(baseDir, lang.name.lowercase()).apply { mkdirs() }
    fun ttsDir(lang: Lang): File = File(dir(lang), "tts").apply { mkdirs() }

    /** Registrazione personalizzata (voce vera) della frase, se c'è: ha sempre la precedenza. */
    @Synchronized
    fun customFor(key: String, lang: Lang): File? = lookup(custom.getOrPut(lang) { scan(dir(lang)) }, key, lang)

    /** File generato dal TTS del telefono (usato solo con l'opzione "File audio offline"). */
    @Synchronized
    fun generatedFor(key: String, lang: Lang): File? = lookup(generated.getOrPut(lang) { scan(ttsDir(lang)) }, key, lang)

    /** Se manca il file della frase va bene quello di una frase con lo stesso testo ([Phrases.alias]). */
    private fun lookup(files: Map<String, File>, key: String, lang: Lang): File? =
        files[key] ?: Phrases.alias(key, lang)?.let { files[it] }

    /** Il file non si riesce a riprodurre (rovinato o in un formato che il telefono non legge): non si usa più. */
    @Synchronized
    fun markBroken(f: File) {
        broken += id(f)
        for (m in listOf(custom, generated)) {
            for (l in m.keys.toList()) m[l] = m.getValue(l).filterValues { it != f }
        }
    }

    @Synchronized
    fun refresh(lang: Lang) {
        custom[lang] = scan(dir(lang))
        generated[lang] = scan(ttsDir(lang))
    }

    fun generatedCount(lang: Lang): Int = Phrases.keys.count { generatedFor(it, lang) != null }
    fun customCount(lang: Lang): Int = Phrases.keys.count { customFor(it, lang) != null }

    private fun scan(folder: File): Map<String, File> {
        val out = HashMap<String, File>()
        folder.listFiles()?.forEach { f ->
            if (f.isFile && f.extension.lowercase() in EXTS && f.length() > MIN_BYTES && f.nameWithoutExtension in Phrases.keys &&
                id(f) !in broken
            ) {
                out[f.nameWithoutExtension] = f
            }
        }
        return out
    }

    /**
     * Importa uno ZIP di registrazioni (quali file e in che lingua: [VoiceZip.target]). Lo ZIP viene prima copiato
     * ed estratto tutto in una cartella a parte: se è rovinato o troncato lancia un'eccezione e le registrazioni
     * restano com'erano. Ritorna quante registrazioni ha importato.
     */
    fun importZip(uri: Uri, fallback: Lang): Int {
        val tmp = File(context.cacheDir, "voice-import.zip")
        // Sotto voice/, così i file si spostano al loro posto senza copiarli di nuovo.
        val staging = File(baseDir, ".import")
        try {
            val input = context.contentResolver.openInputStream(uri) ?: throw IOException("ZIP non leggibile")
            input.use { i -> tmp.outputStream().use { i.copyTo(it) } }
            staging.deleteRecursively()
            staging.mkdirs()
            VoiceZip.extract(tmp, staging, fallback)
            return synchronized(this) { VoiceZip.install(staging) { dir(it) } }
        } finally {
            tmp.delete()
            staging.deleteRecursively()
            Lang.entries.forEach { refresh(it) }
        }
    }

    fun deleteCustom(lang: Lang) {
        dir(lang).listFiles()?.filter { it.isFile && it.extension.lowercase() in EXTS }?.forEach { it.delete() }
        refresh(lang)
    }

    /** Cartella dove si generano i file nuovi: quelli di prima restano in tts/ finché i nuovi non sono pronti. */
    fun newGeneratedDir(lang: Lang): File = File(dir(lang), ".tts-new").apply { deleteRecursively(); mkdirs() }

    /** Mette i file generati in [fresh] al posto di quelli di tts/; se non ci riesce restano quelli di prima. */
    @Synchronized
    fun installGenerated(lang: Lang, fresh: File): Boolean {
        val cur = ttsDir(lang)
        val old = File(dir(lang), ".tts-old").apply { deleteRecursively() }
        val ok = when {
            !cur.renameTo(old) -> false
            !fresh.renameTo(cur) -> { old.renameTo(cur); false }
            else -> { old.deleteRecursively(); true }
        }
        fresh.deleteRecursively()
        refresh(lang)
        return ok
    }

    /** Elenco delle frasi da registrare, scritto nella cartella voce (di nuovo a ogni cambio di lingua). */
    @Synchronized
    fun writeReadme(s: Strings) {
        val sb = StringBuilder()
        sb.appendLine(s.voiceReadme(Lang.entries.joinToString(", ") { it.code }, EXTS.joinToString()))
        // Una colonna per lingua, separate da tabulazioni: si apre bene anche come foglio di calcolo.
        sb.appendLine()
        sb.appendLine((listOf(s.voiceReadmeKey) + Lang.entries.map { it.label.uppercase() }).joinToString("\t"))
        for (k in Phrases.keys) {
            sb.appendLine((listOf(k) + Lang.entries.map { Phrases.text(k, it) }).joinToString("\t"))
        }
        runCatching { File(baseDir.apply { mkdirs() }, "LEGGIMI.txt").writeText(sb.toString()) }
    }
}

/** Lettura degli ZIP di registrazioni, senza Android: si prova nei test. */
internal object VoiceZip {

    /**
     * Dove va un file dello ZIP: (lingua, nome del file) oppure null se va ignorato.
     * - `it/deuce.mp3`, anche dentro altre cartelle (`voice/it/deuce.mp3`): lingua = la cartella che contiene il file;
     * - file fuori da ogni cartella di lingua (`deuce.mp3`, `Registrazioni/deuce.mp3`): lingua [fallback];
     * - si ignora quello che sta in una cartella `tts` (i file generati dall'app: importando la cartella voice/
     *   non devono diventare registrazioni) o più in fondo dentro una cartella di lingua (`it/vecchie/deuce.mp3`).
     */
    fun target(entryName: String, fallback: Lang): Pair<Lang, String>? {
        val parts = entryName.replace('\\', '/').split('/').filter { it.isNotEmpty() }
        val file = File(parts.lastOrNull() ?: return null)
        val ext = file.extension.lowercase()
        if (file.nameWithoutExtension !in Phrases.keys || ext !in VoicePack.EXTS) return null
        val folders = parts.dropLast(1).map { it.lowercase() }
        if ("tts" in folders) return null
        val parent = folders.lastOrNull()?.let { Lang.fromCode(it) }
        val lang = when {
            parent != null -> parent
            folders.any { Lang.fromCode(it) != null } -> return null
            else -> fallback
        }
        return lang to "${file.nameWithoutExtension}.$ext"
    }

    /**
     * Estrae i file validi dello ZIP in [staging]/`<lingua>`/. ZipFile legge l'indice in fondo all'archivio, quindi
     * uno ZIP troncato fallisce subito; un file rovinato fallisce sul controllo CRC. In entrambi i casi eccezione.
     * Ritorna quanti file ha estratto.
     */
    fun extract(zipFile: File, staging: File, fallback: Lang): Int {
        ZipFile(zipFile).use { zip ->
            for (entry in zip.entries()) {
                if (entry.isDirectory) continue
                val (lang, name) = target(entry.name, fallback) ?: continue
                val dir = File(staging, lang.code).apply { mkdirs() }
                val out = File(dir, name)
                val crc = CRC32()
                CheckedInputStream(zip.getInputStream(entry), crc).use { input -> out.outputStream().use { input.copyTo(it) } }
                if (entry.crc != -1L && crc.value != entry.crc) throw ZipException("CRC errato: ${entry.name}")
                if (out.length() <= VoicePack.MIN_BYTES) { out.delete(); continue }
                // La stessa frase in due formati: vale l'ultima.
                VoicePack.EXTS.forEach { e -> File(dir, "${out.nameWithoutExtension}.$e").takeIf { it != out }?.delete() }
            }
        }
        return staging.listFiles().orEmpty().sumOf { it.listFiles().orEmpty().size }
    }

    /** Sposta i file estratti nella cartella della loro lingua ([dir]), togliendo la stessa frase negli altri formati. */
    fun install(staging: File, dir: (Lang) -> File): Int {
        var n = 0
        for (langDir in staging.listFiles().orEmpty()) {
            val dest = dir(Lang.fromCode(langDir.name) ?: continue)
            for (f in langDir.listFiles().orEmpty()) {
                VoicePack.EXTS.forEach { File(dest, "${f.nameWithoutExtension}.$it").delete() }
                val to = File(dest, f.name)
                if (!f.renameTo(to)) f.copyTo(to, overwrite = true)
                n++
            }
        }
        return n
    }
}
TSM_EOF

# ---------------------------------------------------------------- app/src/main/res/drawable/ic_launcher_background.xml
cat > "$DEST/app/src/main/res/drawable/ic_launcher_background.xml" << 'TSM_EOF'
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <solid android:color="@color/tsm_background" />
</shape>
TSM_EOF

# ---------------------------------------------------------------- app/src/main/res/drawable/ic_launcher_foreground.xml
cat > "$DEST/app/src/main/res/drawable/ic_launcher_foreground.xml" << 'TSM_EOF'
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#FFC6F432"
        android:pathData="M24,54a30,30 0,1 0,60 0a30,30 0,1 0,-60 0z" />
    <path
        android:fillColor="#00000000"
        android:strokeColor="#FFFFFFFF"
        android:strokeWidth="3.4"
        android:strokeLineCap="round"
        android:pathData="M33,35C46,46 46,62 33,73" />
    <path
        android:fillColor="#00000000"
        android:strokeColor="#FFFFFFFF"
        android:strokeWidth="3.4"
        android:strokeLineCap="round"
        android:pathData="M75,35C62,46 62,62 75,73" />
</vector>
TSM_EOF

# ---------------------------------------------------------------- app/src/main/res/drawable/ic_stat_tennis.xml
cat > "$DEST/app/src/main/res/drawable/ic_stat_tennis.xml" << 'TSM_EOF'
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="M12,2A10,10 0,1 0,12 22A10,10 0,1 0,12 2ZM5.2,6.3C7.5,8.6 7.5,15.4 5.2,17.7A8,8 0,0 1,5.2 6.3ZM18.8,6.3A8,8 0,0 1,18.8 17.7C16.5,15.4 16.5,8.6 18.8,6.3Z" />
</vector>
TSM_EOF

# ---------------------------------------------------------------- app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml
cat > "$DEST/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml" << 'TSM_EOF'
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
TSM_EOF

# ---------------------------------------------------------------- app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml
cat > "$DEST/app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml" << 'TSM_EOF'
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
TSM_EOF

# ---------------------------------------------------------------- app/src/main/res/values/colors.xml
cat > "$DEST/app/src/main/res/values/colors.xml" << 'TSM_EOF'
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="tsm_background">#FF0B1220</color>
    <color name="tsm_ball">#FFC6F432</color>
</resources>
TSM_EOF

# ---------------------------------------------------------------- app/src/main/res/values/strings.xml
cat > "$DEST/app/src/main/res/values/strings.xml" << 'TSM_EOF'
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">Tennis Score Manager</string>
</resources>
TSM_EOF

# ---------------------------------------------------------------- app/src/main/res/values/themes.xml
cat > "$DEST/app/src/main/res/values/themes.xml" << 'TSM_EOF'
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.TSM" parent="android:Theme.Material.NoActionBar">
        <item name="android:windowBackground">@color/tsm_background</item>
        <item name="android:statusBarColor">@android:color/transparent</item>
        <item name="android:navigationBarColor">@android:color/transparent</item>
    </style>
</resources>
TSM_EOF

# ---------------------------------------------------------------- app/src/main/res/xml/file_paths.xml
cat > "$DEST/app/src/main/res/xml/file_paths.xml" << 'TSM_EOF'
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <cache-path name="share" path="share/" />
</paths>
TSM_EOF

# ---------------------------------------------------------------- app/src/main/res/xml/network_security_config.xml
cat > "$DEST/app/src/main/res/xml/network_security_config.xml" << 'TSM_EOF'
<?xml version="1.0" encoding="utf-8"?>
<!-- Il tabellone arriva in http dal telefono dell'arbitro sulla rete locale (hotspot): niente https lì. -->
<network-security-config>
    <base-config cleartextTrafficPermitted="true" />
</network-security-config>
TSM_EOF

# ---------------------------------------------------------------- app/src/test/java/com/tennis/scoremanager/ble/BandEventTest.kt
cat > "$DEST/app/src/test/java/com/tennis/scoremanager/ble/BandEventTest.kt" << 'TSM_EOF'
package com.tennis.scoremanager.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BandEventTest {

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    @Test
    fun parseOldAndNewEvents() {
        // firmware prima della 2.0: solo tipo e sequenza
        val v1 = BandProtocol.parseEvent(bytes(1, 7))!!
        assertEquals(RawBandEvent(1, 7, null, null, 0), v1)
        // firmware 2.0-2.2: terzo byte = motivo dello spegnimento
        val v2 = BandProtocol.parseEvent(bytes(3, 200, 4))!!
        assertEquals(BandProtocol.EVT_POWER_OFF, v2.type)
        assertEquals(4, v2.extra)
        assertNull(v2.boot)
        // firmware 2.3: id di accensione little endian ed età in decimi di secondo
        val v3 = BandProtocol.parseEvent(bytes(2, 255, 0, 0x78, 0x56, 0x34, 0xF2, 63))!!
        assertEquals(BandProtocol.EVT_UNDO, v3.type)
        assertEquals(255, v3.seq)
        assertEquals(0xF2345678L, v3.boot)
        assertEquals(6_300, v3.ageMs)
        assertNull(BandProtocol.parseEvent(bytes(1)))
    }

    @Test
    fun helloAndAckMessages() {
        assertEquals("H|1", BandProtocol.HELLO)
        assertEquals("K|0", BandProtocol.ack(0))
        assertEquals("K|255", BandProtocol.ack(255))
        // il firmware li riconosce dal secondo carattere e dalla lunghezza (2-6)
        assertTrue(BandProtocol.ack(255).length in 2..6 && BandProtocol.ack(255)[1] == '|')
    }

    @Test
    fun resentEventIsAppliedOnce() {
        val d = EventDedupe()
        val boot = 0xCAFEL
        assertTrue(d.firstTime("AA", boot, 5, 1_000))
        // rimandato dopo una riconnessione: già visto
        assertFalse(d.firstTime("AA", boot, 5, 7_000))
        // il seguente è nuovo
        assertTrue(d.firstTime("AA", boot, 6, 7_100))
        // stessa sequenza, ma il braccialetto è stato riacceso: è un altro tasto
        assertTrue(d.firstTime("AA", 0xBEEFL, 5, 7_200))
        // stessa sequenza e accensione, ma dall'altro braccialetto
        assertTrue(d.firstTime("BB", boot, 5, 7_300))
    }

    @Test
    fun dedupeWindowLetsTheSequenceWrap() {
        val d = EventDedupe(windowMs = 30_000)
        assertTrue(d.firstTime("AA", 1L, 9, 0))
        assertFalse(d.firstTime("AA", 1L, 9, 29_000))
        // dopo 256 tasti la sequenza torna a 9: fuori dalla finestra vale di nuovo
        assertTrue(d.firstTime("AA", 1L, 9, 61_000))
    }

    @Test
    fun scanThrottleKeepsUnderAndroidLimit() {
        val t = ScanThrottle(maxStarts = 4, windowMs = 30_000)
        for (i in 0 until 4) {
            assertEquals(0, t.delayBeforeStart(i * 1_000L))
            t.recordStart(i * 1_000L)
        }
        // quinto avvio a 4 s: si aspetta che il primo (a 0 s) esca dalla finestra, più un margine
        assertEquals(26_500, t.delayBeforeStart(4_000))
        // passato quel tempo si riparte subito
        assertEquals(0, t.delayBeforeStart(30_000))
    }
}
TSM_EOF

# ---------------------------------------------------------------- app/src/test/java/com/tennis/scoremanager/ble/BandSettingsTest.kt
cat > "$DEST/app/src/test/java/com/tennis/scoremanager/ble/BandSettingsTest.kt" << 'TSM_EOF'
package com.tennis.scoremanager.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BandSettingsTest {

    @Test
    fun parseFirmwareConfig() {
        val s = BandSettings.parse("fw=2.0;name=TSM-1A2B;bri=20;pt=3;vol=50;flip=1;pair=30;lost=180;idle=30")!!
        assertEquals("TSM-1A2B", s.name)
        assertEquals(20, s.brightness)
        assertEquals(3, s.pointSeconds)
        assertEquals(50, s.volume)
        assertTrue(s.flip)
        assertEquals(30, s.pairTimeoutS)
        assertEquals(180, s.lostTimeoutS)
        assertEquals(30, s.idleTimeoutMin)
        assertEquals("2.0", s.firmware)
        // un testo qualsiasi (o lo stato batteria) non sono impostazioni
        assertNull(BandSettings.parse("mv=3987;chg=0;up=1234;dsp=56"))
    }

    @Test
    fun encodeRoundTripAndClamp() {
        val s = BandSettings(name = "Mario", brightness = 70, pointSeconds = 5, volume = 0, flip = true, pairTimeoutS = 60, lostTimeoutS = 300, idleTimeoutMin = 45)
        assertEquals("name=Mario;bri=70;pt=5;vol=0;flip=1;pair=60;lost=300;idle=45", s.encode())
        assertEquals(s, BandSettings.parse(s.encode()))
        // valori fuori scala: il telefono li riporta negli stessi limiti del firmware
        val wild = BandSettings(brightness = 0, pointSeconds = 99, volume = 150, pairTimeoutS = 1, lostTimeoutS = 99_999, idleTimeoutMin = 0)
        assertEquals("bri=5;pt=10;vol=100;flip=0;pair=15;lost=1800;idle=5", wild.encode())
    }

    @Test
    fun nameIsSafeForTheProtocol() {
        assertEquals("Nicolo G1", BandSettings.cleanName("Nicolò G1"))
        assertEquals("ab", BandSettings.cleanName("a|;=b"))
        assertEquals("ABCDEFGHIJKL", BandSettings.cleanName("ABCDEFGHIJKLMNOP"))
        assertFalse(BandSettings(name = "x;pt=0").encode().contains("x;pt=0"))
    }

    @Test
    fun identifyAndPowerOffMessages() {
        assertEquals("I|GIOCATORE 1|ROSSI|6|FFD600", BandProtocol.identify("Giocatore 1", "Rossi", 6, 0xFFFFD600.toInt()))
        assertEquals("O|FINE PARTITA|6-4 6-3", BandProtocol.powerOff("Fine partita", "6-4 6-3"))
    }
}
TSM_EOF

# ---------------------------------------------------------------- app/src/test/java/com/tennis/scoremanager/ble/BatteryModelTest.kt
cat > "$DEST/app/src/test/java/com/tennis/scoremanager/ble/BatteryModelTest.kt" << 'TSM_EOF'
package com.tennis.scoremanager.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryModelTest {

    @Test
    fun parseStatus() {
        val s = BatteryModel.parse("mv=3987;chg=0;up=1234;dsp=56")!!
        assertEquals(3987, s.millivolts)
        assertEquals(false, s.charging)
        assertEquals(1234L, s.uptimeS)
        assertEquals(56L, s.displayS)
        assertNull(BatteryModel.parse("garbage"))
    }

    @Test
    fun failedVoltageReadIsDiscarded() {
        // firmware 2.2 con la lettura del PM1 fallita: "mv=0" (e "chg=1"), non una batteria a zero
        assertNull(BatteryModel.parse("mv=0;chg=1;up=600;dsp=20;usb=0;full=0;pct=-1"))
        assertNull(BatteryModel.parse("mv=2400;chg=0;up=600;dsp=20"))
        assertNull(BatteryModel.parse("mv=65535;chg=0;up=600;dsp=20"))
        // ai limiti della LiPo vale ancora
        assertEquals(3300, BatteryModel.parse("mv=3300;chg=0;up=600;dsp=20")!!.millivolts)
        assertNull(BatteryModel.parse("mv=3300;chg=0;up=600;dsp=20;usb=0;full=0;pct=-1")!!.percent)
    }

    @Test
    fun parseChargeStatus() {
        // firmware 2.1: col cavo "chg" resta 1 anche a carica completa
        val c = BatteryModel.parse("mv=4150;chg=1;up=60;dsp=30;usb=5012;full=0;pct=62")!!
        assertEquals(true, c.charging)
        assertEquals(5012, c.usbMv)
        assertEquals(false, c.full)
        assertEquals(62, c.percent)
        // in carica vale la percentuale del braccialetto, non la tensione (falsata dalla carica)
        assertEquals(62, BatteryModel.shownPercent(c))
        val f = BatteryModel.parse("mv=4190;chg=1;up=60;dsp=30;usb=5012;full=1;pct=100")!!
        assertEquals(100, BatteryModel.shownPercent(f))
        // firmware 2.0: niente campi nuovi, percentuale dalla tensione
        val old = BatteryModel.parse("mv=3840;chg=0;up=1;dsp=0")!!
        assertNull(old.usbMv)
        assertNull(old.percent)
        assertEquals(50, BatteryModel.shownPercent(old))
        // senza cavo la percentuale del braccialetto non serve: stessa curva dall'app
        assertEquals(50, BatteryModel.shownPercent(BatteryModel.parse("mv=3840;chg=0;up=1;dsp=0;usb=0;full=0;pct=49")!!))
    }

    @Test
    fun socFromVoltage() {
        assertEquals(100, BatteryModel.soc(4250))
        assertEquals(0, BatteryModel.soc(3200))
        assertEquals(50, BatteryModel.soc(3840))
        assertEquals(20, BatteryModel.soc(3730))
        val mid = BatteryModel.soc(4000)
        assertTrue(mid in 76..79)
    }

    @Test
    fun hoursLeftFromSteadyDrain() {
        // 10 % all'ora partendo da 80 %: dopo 1 ora siamo a 70 %, restano ~7 ore
        val samples = (0..60).map { m -> m * 60_000L to (80 - m / 6) }
        val h = BatteryModel.hoursLeft(samples)
        assertNotNull(h)
        assertEquals(7.0, h!!, 0.4)
        // troppo pochi dati
        assertNull(BatteryModel.hoursLeft(samples.take(10)))
        // nessun calo
        assertNull(BatteryModel.hoursLeft((0..30).map { it * 60_000L to 80 }))
    }

    @Test
    fun estimateFollowsSettings() {
        val default = BatteryModel.estimate(BandSettings())
        assertTrue(!default.measured)
        // 250 mAh con ~36 mA: circa 7 ore da carica piena, più della partita più lunga
        assertEquals(7.0, default.hoursFull, 0.5)
        assertEquals(default.hoursFull / 2, default.hoursAt(50), 0.01)
        // display più luminoso e più a lungo, cicalino al massimo: consuma di più
        val bright = BatteryModel.estimate(BandSettings(brightness = 100, pointSeconds = 8, volume = 100))
        assertTrue(bright.totalMa > default.totalMa + 3)
        // punteggio spento e muto: consuma di meno
        val saver = BatteryModel.estimate(BandSettings(brightness = 5, pointSeconds = 0, volume = 0))
        assertTrue(saver.totalMa < default.totalMa)
        assertEquals(0.0, saver.soundMa, 0.0)
        // con una base misurata si usa quella
        val measured = BatteryModel.estimate(BandSettings(), 50.0)
        assertTrue(measured.measured)
        assertEquals(50.0, measured.baseMa, 0.0)
    }

    @Test
    fun baseFromMeasuredDrain() {
        // 16 % all'ora di 250 mAh = 40 mA in tutto; display acceso il 10 % del tempo a luminosità 20 (8 mA)
        val s = BandSettings(brightness = 20, volume = 0)
        assertEquals(40.0 - 0.8, BatteryModel.baseFromMeasure(16.0, 0.10, s), 0.01)
    }

    @Test
    fun hoursText() {
        assertEquals("~40 min", BatteryModel.formatHours(40 / 60.0))
        assertEquals("~7 h", BatteryModel.formatHours(7.0))
        assertEquals("~6 h 30 min", BatteryModel.formatHours(6.5))
    }
}
TSM_EOF

# ---------------------------------------------------------------- app/src/test/java/com/tennis/scoremanager/data/DataTest.kt
cat > "$DEST/app/src/test/java/com/tennis/scoremanager/data/DataTest.kt" << 'TSM_EOF'
package com.tennis.scoremanager.data

import com.tennis.scoremanager.ble.BandProtocol
import com.tennis.scoremanager.model.Lang
import com.tennis.scoremanager.model.RulesConfig
import com.tennis.scoremanager.model.SetScore
import com.tennis.scoremanager.model.Side
import com.tennis.scoremanager.ui.EnStrings
import com.tennis.scoremanager.ui.ItStrings
import com.tennis.scoremanager.ui.stringsFor
import org.junit.Assert.assertEquals
import org.junit.Test

class DataTest {

    @Test
    fun singlesNamesAndDefaults() {
        val n = Names(SetupData(p1a = "  Rossi ", p2a = ""), ItStrings)
        assertEquals("Rossi", n.side(Side.P1))
        assertEquals("Giocatore 2", n.side(Side.P2))
        assertEquals(listOf("Rossi"), n.players(Side.P1))
        assertEquals("Player 2", Names(SetupData(), EnStrings).side(Side.P2))
    }

    @Test
    fun doublesNamesStayOnTheirSide() {
        val su = SetupData(doubles = true, p1a = "Rossi", p1b = "Verdi", p2a = "Bianchi", p2b = "")
        val n = Names(su, ItStrings)
        assertEquals("Rossi e Verdi", n.side(Side.P1))
        assertEquals("Rossi / Verdi", n.short(Side.P1))
        assertEquals("Bianchi", n.side(Side.P2))
        assertEquals(listOf("Bianchi", "Giocatore 2B"), n.players(Side.P2))
        assertEquals("Verdi", n.player(Side.P1, 1))
        assertEquals("Giocatore 1", Names(SetupData(doubles = true), ItStrings).side(Side.P1))
        assertEquals("Rossi and Verdi", Names(su, EnStrings).side(Side.P1))
    }

    @Test
    fun setNotation() {
        assertEquals("6-4", Reports.setText(SetScore(6, 4), Side.P1))
        assertEquals("4-6", Reports.setText(SetScore(6, 4), Side.P2))
        assertEquals("7-6(5)", Reports.setText(SetScore(7, 6, 7, 5), Side.P1))
        assertEquals("6-7(5)", Reports.setText(SetScore(7, 6, 7, 5), Side.P2))
        assertEquals("[10-8]", Reports.setText(SetScore(1, 0, 10, 8, matchTiebreak = true), Side.P1))
        assertEquals("[8-10]", Reports.setText(SetScore(1, 0, 10, 8, matchTiebreak = true), Side.P2))
        assertEquals("1:02:03", Reports.duration(3_723_000))
    }

    @Test
    fun bandTextIsPlainAscii() {
        assertEquals("NICCOLO FORTE", BandProtocol.clean("Niccolò Forté"))
        assertEquals("A/B", BandProtocol.clean("a|b"))
        assertEquals("P|15|AD|1|TIE-BREAK", BandProtocol.point("15", "AD", 1, "Tie-break"))
        assertEquals("M|GAME SET MATCH|6-4 7-5|15", BandProtocol.message("Game set match", "6-4 7-5", 15))
    }

    @Test
    fun formatLineUsesTheLanguageForNoAd() {
        val rec = MatchRecord(id = "m", setup = SetupData(), options = MatchOptions(), rules = RulesConfig(noAd = true))
        assertEquals("3 set · tie-break a 7 · No-Ad · Singolare", Reports.formatLabel(rec, ItStrings))
        assertEquals("3 sets · tie-break a 7 · Sin ventaja · Individual", Reports.formatLabel(rec, stringsFor(Lang.ES)))
        assertEquals("3 sets · tie-break a 7 · Sem vantagem · Simples", Reports.formatLabel(rec, stringsFor(Lang.PT)))
    }
}
TSM_EOF

# ---------------------------------------------------------------- app/src/test/java/com/tennis/scoremanager/model/ScoreEngineTest.kt
cat > "$DEST/app/src/test/java/com/tennis/scoremanager/model/ScoreEngineTest.kt" << 'TSM_EOF'
package com.tennis.scoremanager.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoreEngineTest {

    private val P1 = Side.P1
    private val P2 = Side.P2

    private fun play(s: MatchState, vararg winners: Side): MatchState =
        winners.fold(s) { acc, w -> ScoreEngine.pointWonBy(acc, w).state }

    private fun game(s: MatchState, w: Side): MatchState = play(s, w, w, w, w)

    /** Porta il set sul punteggio indicato alternando i game (tiene conto di chi vince). */
    private fun games(s: MatchState, a: Int, b: Int): MatchState {
        var st = s
        var ga = 0
        var gb = 0
        while (ga < a || gb < b) {
            st = if (ga < a && (ga <= gb || gb >= b)) { ga++; game(st, P1) } else { gb++; game(st, P2) }
        }
        return st
    }

    @Test
    fun pointLabelsAndDeuce() {
        var s = ScoreEngine.initial(RulesConfig())
        s = play(s, P1)
        assertEquals("15", s.pointLabel(P1)); assertEquals("0", s.pointLabel(P2))
        s = play(s, P1, P2, P2, P2, P1)
        assertEquals("40", s.pointLabel(P1)); assertEquals("40", s.pointLabel(P2)); assertTrue(s.isDeuce)
        s = play(s, P2)
        assertEquals("AD", s.pointLabel(P2)); assertEquals("40", s.pointLabel(P1))
        s = play(s, P1)
        assertTrue(s.isDeuce)
        s = play(s, P1)
        val step = ScoreEngine.pointWonBy(s, P1)
        assertEquals(P1, step.transition!!.gameWinner)
        assertEquals(1, step.state.g1)
        assertEquals(0, step.state.pt1 + step.state.pt2)
    }

    @Test
    fun noAdDecidingPoint() {
        var s = ScoreEngine.initial(RulesConfig(noAd = true))
        s = play(s, P1, P1, P1, P2, P2, P2)
        assertTrue(s.isDeuce)
        val step = ScoreEngine.pointWonBy(s, P2)
        assertEquals(P2, step.transition!!.gameWinner)
    }

    @Test
    fun changeOfEndsAfterOddGamesOnly() {
        var s = ScoreEngine.initial(RulesConfig(p1StartsLeft = true))
        val changes = mutableListOf<Int>()
        repeat(9) { i ->
            val before = s
            val w = if (i % 2 == 0) P1 else P2
            var t: Transition? = null
            repeat(4) { val st = ScoreEngine.pointWonBy(s, w); s = st.state; t = st.transition }
            if (t!!.changeEnds) changes += before.g1 + before.g2 + 1
        }
        assertEquals(listOf(1, 3, 5, 7, 9), changes)
    }

    @Test
    fun firstGameFlagAndServiceAlternates() {
        var s = ScoreEngine.initial(RulesConfig(firstServer = P2))
        assertEquals(P2, s.server)
        s = play(s, P1, P1, P1)
        val step = ScoreEngine.pointWonBy(s, P1)
        assertTrue(step.transition!!.firstGameOfSet)
        assertTrue(step.transition!!.changeEnds)
        assertEquals(P1, step.state.server)
    }

    @Test
    fun setWonSixFourAndNextSetServer() {
        var s = ScoreEngine.initial(RulesConfig(firstServer = P1, p1StartsLeft = true))
        s = games(s, 5, 4)
        assertEquals(5, s.g1); assertEquals(4, s.g2)
        // 9 game giocati: serve il turno 9 (dispari) -> P2
        assertEquals(P2, s.server)
        val step = ScoreEngine.pointWonBy(play(s, P1, P1, P1), P1)
        val t = step.transition!!
        assertEquals(P1, t.setWinner)
        assertFalse("6-4 = 10 game: nessun cambio a fine set", t.changeEnds)
        assertEquals(listOf(SetScore(6, 4)), step.state.sets)
        // Il 10° game (turno 9) l'ha servito P2: il secondo set lo apre P1.
        assertEquals(P1, step.state.server)
        // Dopo il primo game del nuovo set si cambia campo.
        val g1 = ScoreEngine.pointWonBy(play(step.state, P1, P1, P1), P1)
        assertTrue(g1.transition!!.changeEnds)
    }

    @Test
    fun setWonSixThreeChangesEndsAtSetEnd() {
        var s = ScoreEngine.initial(RulesConfig())
        s = games(s, 5, 3)
        val step = ScoreEngine.pointWonBy(play(s, P1, P1, P1), P1)
        assertEquals(P1, step.transition!!.setWinner)
        assertTrue("6-3 = 9 game: cambio a fine set", step.transition!!.changeEnds)
    }

    @Test
    fun sevenFiveNeedsTwoGames() {
        var s = ScoreEngine.initial(RulesConfig())
        s = games(s, 5, 5)
        s = game(s, P1)
        assertEquals(6, s.g1); assertTrue(s.sets.isEmpty())
        val step = ScoreEngine.pointWonBy(play(s, P1, P1, P1), P1)
        assertEquals(SetScore(7, 5), step.state.sets.single())
    }

    @Test
    fun tiebreakAtSixAllServiceOrderAndChanges() {
        var s = ScoreEngine.initial(RulesConfig(firstServer = P1, p1StartsLeft = true))
        s = games(s, 5, 5)
        s = game(s, P1)
        val leftBefore = s.p1Left
        val tbStart = ScoreEngine.pointWonBy(play(s, P2, P2, P2), P2)
        assertTrue(tbStart.transition!!.tiebreakStarted)
        assertFalse("6-6: 12 game, nessun cambio campo", tbStart.transition!!.changeEnds)
        s = tbStart.state
        assertEquals(leftBefore, s.p1Left)
        assertTrue(s.inTiebreak)
        // Turno 12 (pari) -> serve chi ha iniziato il set: P1. Poi 2 punti a testa.
        val servers = mutableListOf<Side>()
        var t = s
        val changes = mutableListOf<Int>()
        repeat(12) { i ->
            servers += t.server
            val st = ScoreEngine.pointWonBy(t, if (i % 2 == 0) P1 else P2)
            if (st.transition!!.changeEnds) changes += st.state.pt1 + st.state.pt2
            t = st.state
        }
        assertEquals(listOf(P1, P2, P2, P1, P1, P2, P2, P1, P1, P2, P2, P1), servers)
        assertEquals(listOf(6, 12), changes)
        assertEquals("6", t.pointLabel(P1)); assertEquals("6", t.pointLabel(P2))
        // 8-6 chiude il tie-break e il set 7-6
        val a = ScoreEngine.pointWonBy(t, P1)
        assertNull(a.transition!!.setWinner)
        val b = ScoreEngine.pointWonBy(a.state, P1)
        assertEquals(P1, b.transition!!.setWinner)
        assertEquals(SetScore(7, 6, 8, 6), b.state.sets.single())
        assertTrue("fine tie-break: 13° game, cambio campo", b.transition!!.changeEnds)
        // Ha servito il primo punto del tie-break P1 -> nel set successivo serve P2.
        assertEquals(P2, b.state.server)
    }

    @Test
    fun tiebreakEndingOnMultipleOfSixChangesOnlyOnce() {
        var s = ScoreEngine.initial(RulesConfig(p1StartsLeft = true))
        s = games(s, 5, 5); s = game(s, P1); s = game(s, P2)
        val left = s.p1Left
        // 7-5 = 12 punti
        s = play(s, P1, P2, P1, P2, P1, P2, P1, P2, P1, P2, P1)
        assertEquals(!left, s.p1Left) // cambio al 6° punto
        val end = ScoreEngine.pointWonBy(s, P1)
        assertEquals(P1, end.transition!!.setWinner)
        assertTrue(end.transition!!.changeEnds)
        assertEquals("un solo cambio alla fine (non doppio)", left, end.state.p1Left)
    }

    @Test
    fun matchBestOfThree() {
        var s = ScoreEngine.initial(RulesConfig())
        s = games(s, 5, 0)
        s = game(s, P1)
        assertEquals(1, s.setsWon(P1))
        s = games(s, 0, 5); s = game(s, P2)
        assertEquals(1, s.setsWon(P2))
        s = games(s, 5, 0)
        val end = ScoreEngine.pointWonBy(play(s, P1, P1, P1), P1)
        assertEquals(P1, end.transition!!.matchWinner)
        assertTrue(end.state.isFinished)
        assertEquals(3, end.state.sets.size)
        // A partita finita i punti vengono ignorati.
        assertNull(ScoreEngine.pointWonBy(end.state, P2).transition)
    }

    @Test
    fun matchTiebreakToTenWithTwoClear() {
        var s = ScoreEngine.initial(RulesConfig(format = MatchFormat.TWO_SETS_MATCH_TIEBREAK))
        s = games(s, 5, 0); s = game(s, P1)
        s = games(s, 0, 5)
        val setEnd = ScoreEngine.pointWonBy(play(s, P2, P2, P2), P2)
        assertTrue(setEnd.transition!!.matchTiebreakStarted)
        s = setEnd.state
        assertEquals(TiebreakKind.MATCH, s.tiebreak)
        // 9-9 poi 11-9
        repeat(9) { s = play(s, P1, P2) }
        assertEquals(9, s.pt1); assertEquals(9, s.pt2)
        s = play(s, P2)
        assertNull(s.winner)
        s = play(s, P1, P1)
        assertNull(s.winner)
        val end = ScoreEngine.pointWonBy(s, P1)
        assertEquals(P1, end.transition!!.matchWinner)
        val last = end.state.sets.last()
        assertTrue(last.matchTiebreak)
        assertEquals(12, last.tb1); assertEquals(10, last.tb2)
        assertEquals(12, last.shown(P1))
    }

    @Test
    fun doublesRotationAndTiebreak() {
        val rules = RulesConfig(doubles = true, firstServer = P1, firstServerP1 = 1, firstServerP2 = 0)
        var s = ScoreEngine.initial(rules)
        val seq = mutableListOf<Pair<Side, Int>>()
        repeat(6) {
            seq += s.server to s.serverPlayer
            s = game(s, if (it % 2 == 0) P1 else P2)
        }
        assertEquals(listOf(P1 to 1, P2 to 0, P1 to 0, P2 to 1, P1 to 1, P2 to 0), seq)
        // dal 3-3 al 6-6 alternando i game
        repeat(6) { s = game(s, if (it % 2 == 0) P1 else P2) }
        assertEquals(6, s.g1); assertEquals(6, s.g2)
        assertTrue(s.inTiebreak)
        val tb = mutableListOf<Pair<Side, Int>>()
        repeat(5) { i ->
            tb += s.server to s.serverPlayer
            s = play(s, if (i % 2 == 0) P1 else P2)
        }
        // Turno 12 = stesso giocatore del game 1 (P1 #1), poi P2 #0, P2 #0, P1 #0, P1 #0
        assertEquals(listOf(P1 to 1, P2 to 0, P2 to 0, P1 to 0, P1 to 0), tb)
    }

    @Test
    fun doublesServeOrderEventOnlyAtSetStart() {
        val rules = RulesConfig(doubles = true)
        val events = mutableListOf<MatchEvent>(MatchEvent.ServeOrder(1, 1, 1))
        var s = ScoreEngine.replay(rules, events)
        assertEquals(1, s.order1); assertEquals(1, s.order2)
        events += MatchEvent.Point(Side.P1)
        events += MatchEvent.ServeOrder(1, 0, 0) // ignorato: il set è iniziato
        s = ScoreEngine.replay(rules, events)
        assertEquals(1, s.order1)
    }

    @Test
    fun replayUndoAcrossSetAndMatchEnd() {
        val rules = RulesConfig()
        val events = mutableListOf<MatchEvent>()
        fun add(w: Side, n: Int) = repeat(n) { events += MatchEvent.Point(w) }
        repeat(6) { add(P1, 4) }
        repeat(6) { add(P1, 4) }
        val final = ScoreEngine.replay(rules, events)
        assertEquals(P1, final.winner)
        val undone = ScoreEngine.replay(rules, events.dropLast(1))
        assertNull(undone.winner)
        assertEquals(5, undone.g1)
        assertEquals("40", undone.pointLabel(P1))
        assertEquals(1, undone.sets.size)
        // Undo oltre la fine del primo set
        val back = ScoreEngine.replay(rules, events.take(24 - 1))
        assertTrue(back.sets.isEmpty())
        assertEquals(5, back.g1)
    }

    @Test
    fun lookaheadSetAndMatchPoint() {
        var s = ScoreEngine.initial(RulesConfig())
        s = games(s, 5, 2)
        s = play(s, P1, P1, P1)
        assertEquals(P1, ScoreEngine.lookahead(s, P1)!!.setWinner)
        assertNull(ScoreEngine.lookahead(s, P1)!!.matchWinner)
    }

    @Test
    fun breakPoint() {
        val s = play(ScoreEngine.initial(RulesConfig(firstServer = P1)), P2, P2, P2)
        assertTrue(ScoreEngine.isBreakPoint(s))
        val t = play(ScoreEngine.initial(RulesConfig(firstServer = P1)), P1, P1, P1)
        assertFalse(ScoreEngine.isBreakPoint(t))
    }
}
TSM_EOF

# ---------------------------------------------------------------- app/src/test/java/com/tennis/scoremanager/tv/TvSnapshotTest.kt
cat > "$DEST/app/src/test/java/com/tennis/scoremanager/tv/TvSnapshotTest.kt" << 'TSM_EOF'
package com.tennis.scoremanager.tv

import com.tennis.scoremanager.CountdownKind
import com.tennis.scoremanager.LiveMatch
import com.tennis.scoremanager.Screen
import com.tennis.scoremanager.data.MatchOptions
import com.tennis.scoremanager.data.MatchRecord
import com.tennis.scoremanager.data.SetupData
import com.tennis.scoremanager.model.Lang
import com.tennis.scoremanager.model.MatchEvent
import com.tennis.scoremanager.model.MatchFormat
import com.tennis.scoremanager.model.RulesConfig
import com.tennis.scoremanager.model.ScoreEngine
import com.tennis.scoremanager.model.Side
import com.tennis.scoremanager.ui.ItStrings
import com.tennis.scoremanager.ui.stringsFor
import java.io.File
import java.net.InetAddress
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TvSnapshotTest {

    private val setup = SetupData(club = "TC Roma", court = "3", p1a = "Stefano", p2a = "Mario")
    private val rules = RulesConfig()

    private fun match(points: List<Side>, started: Boolean = true, suspended: Boolean = false, rules: RulesConfig = this.rules): LiveMatch {
        val events = points.map { MatchEvent.Point(it) }
        val rec = MatchRecord(
            id = "m1", setup = setup, options = MatchOptions(), rules = rules, events = events,
            startedAt = if (started) 1L else null, suspended = suspended,
        )
        return LiveMatch(rec, ScoreEngine.replay(rules, events))
    }

    private fun input(
        screen: Screen = Screen.MATCH,
        match: LiveMatch? = null,
        countdown: CountdownKind? = null,
        message: String? = null,
        tv: TvSettings = TvSettings(enabled = true),
    ) = TvInput(
        screen = screen, setup = setup, lang = Lang.IT, firstServer = Side.P2, match = match,
        clockMs = 65_000, clockRunning = true, countdown = countdown, countdownLeftMs = 20_000,
        message = message, tv = tv, strings = ItStrings, seq = 7,
    )

    @Test
    fun idleBeforeAnyMatch() {
        val s = TvSnapshots.build(input(screen = Screen.SETUP))
        assertEquals("idle", s.phase)
        assertEquals(listOf("", ""), s.points)
        assertNull(s.server)
        assertEquals(0L, s.clockMs)
        assertEquals("TC Roma · Campo 3", s.title)
        assertEquals(listOf("Stefano", "Mario"), s.players.map { it.name })
    }

    @Test
    fun readyOnStartScreenShowsFirstServer() {
        val s = TvSnapshots.build(input(screen = Screen.START))
        assertEquals("ready", s.phase)
        assertEquals(listOf("0", "0"), s.points)
        assertEquals(1, s.server)
    }

    @Test
    fun pointsGamesAndAdvantage() {
        // 1-0 in game, poi 40-40 e vantaggio Mario
        val pts = List(4) { Side.P1 } + listOf(Side.P1, Side.P1, Side.P1, Side.P2, Side.P2, Side.P2, Side.P2)
        val s = TvSnapshots.build(input(match = match(pts), countdown = CountdownKind.SHOT_CLOCK, message = "Palla break"))
        assertEquals("play", s.phase)
        assertEquals(listOf(1, 0), s.games)
        assertEquals(listOf("40", "AD"), s.points)
        assertEquals("SERVIZIO", s.countdown!!.label)
        assertTrue(s.countdown!!.shot)
        assertEquals(20_000L, s.countdown!!.leftMs)
        assertEquals("Palla break", s.message)
        assertTrue(s.clockRunning)
    }

    @Test
    fun finishedShowsLastSetAndWinner() {
        // 6-0 6-0 per Stefano
        val s = TvSnapshots.build(input(screen = Screen.SUMMARY, match = match(List(48) { Side.P1 }), countdown = CountdownKind.SHOT_CLOCK, message = "x"))
        assertEquals("finished", s.phase)
        assertEquals(0, s.winner)
        assertEquals(listOf(6, 0), s.games)
        assertEquals(listOf(2, 0), s.sets)
        assertEquals(2, s.done.size)
        assertEquals(listOf("", ""), s.points)
        assertNull(s.server)
        assertNull(s.countdown)
        assertNull(s.message)
        assertFalse(s.clockRunning)
    }

    @Test
    fun finishedInMatchTiebreakShowsItAsOneSet() {
        // 6-0 0-6 [10-8] per Stefano: una cifra per i game, quindi 1-0 (non 10-8, che diventerebbe 0-8)
        val pts = List(24) { Side.P1 } + List(24) { Side.P2 } + List(8) { listOf(Side.P1, Side.P2) }.flatten() + listOf(Side.P1, Side.P1)
        val s = TvSnapshots.build(input(screen = Screen.SUMMARY, match = match(pts, rules = RulesConfig(format = MatchFormat.TWO_SETS_MATCH_TIEBREAK))))
        assertEquals("finished", s.phase)
        assertEquals(0, s.winner)
        assertEquals(listOf(1, 0), s.games)
        assertEquals(listOf(2, 1), s.sets)
        assertEquals(TvSet(1, 0, 10, 8, mtb = true), s.done.last())
        assertEquals(listOf("", ""), s.points)
    }

    @Test
    fun suspendedHidesTimersAndMessages() {
        val s = TvSnapshots.build(input(match = match(listOf(Side.P1), suspended = true), countdown = CountdownKind.SHOT_CLOCK, message = "x"))
        assertEquals("suspended", s.phase)
        assertEquals(listOf("15", "0"), s.points)
        assertNull(s.countdown)
        assertNull(s.message)
    }

    @Test
    fun settingsHideTimersAndMessages() {
        val tv = TvSettings(enabled = true, showTimers = false, showMessages = false, title = " Torneo sociale ", color1 = "#2F80FF")
        val s = TvSnapshots.build(input(match = match(emptyList()), countdown = CountdownKind.CHANGEOVER, message = "x", tv = tv))
        assertNull(s.countdown)
        assertNull(s.message)
        assertEquals("Torneo sociale", s.title)
        assertEquals("#2F80FF", s.players[0].color)
        assertFalse(s.show.timers)
    }

    @Test
    fun jsonIsOneLineWithSignature() {
        val s = TvSnapshots.build(input(match = match(listOf(Side.P2))))
        val json = Json { encodeDefaults = true }.encodeToString(TvSnapshot.serializer(), s)
        assertFalse('\n' in json)  // una riga sola: così viaggia in un unico evento SSE
        assertTrue("\"tsm\":1" in json)  // la ricerca del tabellone riconosce il server da questo
    }

    @Test
    fun defaultTitle() {
        assertEquals("", TvSnapshots.defaultTitle(SetupData(), ItStrings))
        assertEquals("Campo Centrale", TvSnapshots.defaultTitle(SetupData(court = "Campo Centrale"), ItStrings))
        assertEquals("TC Roma", TvSnapshots.defaultTitle(SetupData(club = "TC Roma"), ItStrings))
    }

    @Test
    fun manualAddresses() {
        assertEquals("192.168.43.1" to 8080, ScoreboardFinder.parseAddress("192.168.43.1"))
        assertEquals("192.168.43.1" to 8081, ScoreboardFinder.parseAddress(" http://192.168.43.1:8081/ "))
        assertNull(ScoreboardFinder.parseAddress(""))
        assertNull(ScoreboardFinder.parseAddress("10.0.0.2:99999"))
    }

    @Test
    fun onlyLocalNetworkMayConnect() {
        val ok = listOf("192.168.43.5", "10.0.0.2", "172.20.10.2", "127.0.0.1", "169.254.1.1", "::1", "fe80::1", "fd12:3456::1", "::ffff:192.168.1.2")
        val no = listOf("8.8.8.8", "100.64.1.1", "2001:db8::1", "2a01:4f8::1", "::ffff:8.8.8.8")
        ok.forEach { assertTrue(it, TvServer.isLocalPeer(InetAddress.getByName(it))) }
        no.forEach { assertFalse(it, TvServer.isLocalPeer(InetAddress.getByName(it))) }
    }

    @Test
    fun identityFromStateResponse() {
        val response = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nX-TSM-Id: ab12cd34\r\nConnection: close\r\n\r\n" +
            "{\"tsm\":1,\"title\":\"TC Roma · Campo 3\"}"
        assertEquals("ab12cd34" to "TC Roma · Campo 3", ScoreboardFinder.identity(response))
        // server di una versione precedente: niente identità
        assertEquals(null to "", ScoreboardFinder.identity("HTTP/1.1 200 OK\r\n\r\n{\"tsm\":1}"))
    }

    @Test
    fun recoveryStaysOnTheSameCourt() {
        val prev = FoundScoreboard("192.168.43.1", 8080, null, "ab12cd34", "TC Roma · Campo 3")
        assertTrue(FoundScoreboard("192.168.43.7", 8080, null, "ab12cd34", "").sameAs(prev))  // stesso telefono, indirizzo nuovo
        assertTrue(FoundScoreboard("192.168.43.1", 8080, null, null).sameAs(prev))  // stesso indirizzo
        assertTrue(FoundScoreboard("192.168.43.9", 8081, null, "ffff0000", "TC Roma · Campo 3").sameAs(prev))  // stesso campo
        assertFalse(FoundScoreboard("192.168.43.9", 8080, null, "ffff0000", "TC Roma · Campo 4").sameAs(prev))
        assertFalse(FoundScoreboard("192.168.43.9", 8080, null, null, "").sameAs(prev.copy(title = "")))
    }

    /** Il blocco id="texts" di scoreboard.html deve avere gli stessi testi dell'app, lingua per lingua. */
    @Test
    fun pageTextsMatchTheApp() {
        val expected = Lang.entries.associate { lang ->
            val s = stringsFor(lang)
            val demo = mapOf(
                "serve" to s.tvServe,
                "changeover" to s.tvChangeover,
                "setPoint" to s.msgSetPoint,
                "title" to TvSnapshots.defaultTitle(SetupData(club = "Tennis Club", court = "3"), s),
            )
            lang.code to JsonObject(
                mapOf(
                    "labels" to Json.encodeToJsonElement(TvSnapshots.labels(s)),
                    "demo" to JsonObject(demo.mapValues { JsonPrimitive(it.value) }),
                ),
            )
        }
        val html = File("src/main/assets/scoreboard.html").readText()
        val block = html.substringAfter("<script type=\"application/json\" id=\"texts\">").substringBefore("</script>")
        val paste = expected.entries.joinToString(",\n", "{\n", "\n}") { (k, v) -> "\"$k\": $v" }
        assertEquals("Copia questo blocco in scoreboard.html:\n$paste\n", JsonObject(expected), Json.parseToJsonElement(block))
    }
}
TSM_EOF

# ---------------------------------------------------------------- app/src/test/java/com/tennis/scoremanager/ui/StringsTest.kt
cat > "$DEST/app/src/test/java/com/tennis/scoremanager/ui/StringsTest.kt" << 'TSM_EOF'
package com.tennis.scoremanager.ui

import com.tennis.scoremanager.ble.BandProtocol
import com.tennis.scoremanager.ble.BandSettings
import com.tennis.scoremanager.model.Lang
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier
import java.text.SimpleDateFormat
import java.util.Date

class StringsTest {

    /** Prova gli argomenti finché i tipi combaciano (le lambda controllano i tipi solo quando vengono chiamate). */
    private fun call(f: (List<Any?>) -> Any?, tries: List<List<Any?>>): String =
        tries.firstNotNullOfOrNull { args -> runCatching { f(args) as String }.getOrNull() } ?: error("nessun argomento valido")

    /** Ogni testo di ogni lingua, funzioni comprese (chiamate con argomenti di prova). */
    @Suppress("UNCHECKED_CAST")
    private fun texts(s: Strings): Map<String, String> =
        Strings::class.java.methods
            .filter { it.name.startsWith("get") && it.parameterCount == 0 && !Modifier.isStatic(it.modifiers) }
            .associate { m ->
                val name = m.name.removePrefix("get")
                val text = when (val v = m.invoke(s)) {
                    is String -> v
                    is Function1<*, *> -> call({ (v as Function1<Any?, Any?>)(it[0]) }, listOf(listOf(2), listOf("Rossi"), listOf(listOf("Rossi"))))
                    is Function2<*, *, *> -> call(
                        { (v as Function2<Any?, Any?, Any?>)(it[0], it[1]) },
                        listOf(listOf("Rossi", "6-4"), listOf("Rossi", 2), listOf(2, 3), listOf(true, false)),
                    )
                    is Function3<*, *, *, *> -> call(
                        { (v as Function3<Any?, Any?, Any?, Any?>)(it[0], it[1], it[2]) },
                        listOf(listOf("Rossi", "6-4", false)),
                    )
                    is Function4<*, *, *, *, *> -> call(
                        { (v as Function4<Any?, Any?, Any?, Any?, Any?>)(it[0], it[1], it[2], it[3]) },
                        listOf(listOf("1", "2", "3", "4")),
                    )
                    else -> error("$name: ${v?.javaClass}")
                }
                name to text
            }

    @Test
    fun everyLanguageHasEveryText() {
        val it = texts(ItStrings)
        assertTrue(it.size > 250)
        for (lang in Lang.entries) {
            val t = texts(stringsFor(lang))
            assertEquals(it.keys, t.keys)
            for ((k, v) in t) assertTrue("$lang $k", v.isNotBlank() || k == "TeamJoiner")
        }
    }

    @Test
    fun doublesAndTwoBandsUsePlural() {
        assertEquals("Vince Rossi\n6-4 6-3", ItStrings.endDialogText("Rossi", "6-4 6-3", false))
        assertEquals("Vincono Rossi e Bianchi\n6-4 6-3", ItStrings.endDialogText("Rossi e Bianchi", "6-4 6-3", true))
        assertEquals("Rossi and Bianchi win\n6-4", EnStrings.endDialogText("Rossi and Bianchi", "6-4", true))
        assertEquals("Ganan Rossi y Bianchi\n6-4", stringsFor(Lang.ES).endDialogText("Rossi y Bianchi", "6-4", true))
        for (lang in Lang.entries) {
            val s = stringsFor(lang)
            val one = s.bandsMissingText(listOf("Rossi"))
            val two = s.bandsMissingText(listOf("Rossi", "Bianchi"))
            assertTrue("$lang: $two", "Rossi, Bianchi" in two)
            // Al plurale cambia anche il resto della frase, non solo l'elenco dei nomi.
            assertTrue("$lang: $one / $two", two.replace(", Bianchi", "") != one)
        }
    }

    @Test
    fun newLanguagesAreActuallyTranslated() {
        val en = texts(EnStrings)
        for (lang in listOf(Lang.FR, Lang.DE, Lang.ES, Lang.PT)) {
            val t = texts(stringsFor(lang))
            val same = t.filter { (k, v) -> v == en[k] && v.any { c -> c.isLetter() } }.keys
            // Restano uguali solo nomi propri e sigle: Bluetooth, OK, Firmware, Audio On/Off, online...
            assertTrue("$lang copia l'inglese in: $same", same.size < 30)
            val sentences = same.filter { k -> (en[k] ?: "").count { c -> c == ' ' } >= 3 }
            assertTrue("$lang frasi non tradotte: $sentences", sentences.isEmpty())
        }
    }

    @Test
    fun bandTextsFitTheWristband() {
        for (lang in Lang.entries) {
            val s = stringsFor(lang)
            val band = listOf(
                s.bandPaired, s.bandPlay, s.bandChangeEnds, s.bandTiebreak, s.bandSet, s.bandSuspended,
                s.bandGameSetMatch, s.bandMatchOver, s.bandAppClosed, s.bandOffFromApp, s.bandBatteryLow,
            )
            for (b in band) {
                // Il braccialetto tronca la riga 1 a 18 caratteri: meglio che non serva.
                assertEquals("$lang «$b»", BandProtocol.clean(b, 99), BandProtocol.clean(b))
                assertTrue("$lang «$b»", BandProtocol.clean(b).isNotBlank())
            }
        }
    }

    @Test
    fun datePatternsAreValid() {
        for (lang in Lang.entries) {
            val d = SimpleDateFormat(stringsFor(lang).datePattern, lang.locale).format(Date(0))
            assertTrue("$lang $d", d.contains("1970"))
        }
    }

    @Test
    fun bandSettingsCarryTheLanguage() {
        val s = BandSettings.parse("fw=2.2;name=TSM-1A2B;bri=20;pt=3;vol=50;flip=0;pair=30;lost=180;idle=30;lang=fr")!!
        assertEquals("fr", s.lang)
        assertEquals("", BandSettings.parse("fw=2.1;name=TSM-1A2B;bri=20")!!.lang)
        assertEquals("lang=de", BandSettings.languageConfig("de"))
        // Le impostazioni scritte dal pannello non toccano la lingua (quella la manda l'app da sola).
        assertTrue("lang" !in s.encode())
    }
}
TSM_EOF

# ---------------------------------------------------------------- app/src/test/java/com/tennis/scoremanager/voice/CallBuilderTest.kt
cat > "$DEST/app/src/test/java/com/tennis/scoremanager/voice/CallBuilderTest.kt" << 'TSM_EOF'
package com.tennis.scoremanager.voice

import com.tennis.scoremanager.model.Lang
import com.tennis.scoremanager.model.MatchEvent
import com.tennis.scoremanager.model.MatchFormat
import com.tennis.scoremanager.model.RulesConfig
import com.tennis.scoremanager.model.ScoreEngine
import com.tennis.scoremanager.model.Side
import com.tennis.scoremanager.model.Transition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CallBuilderTest {

    private val names = object : CallNames {
        override fun side(side: Side) = if (side == Side.P1) "Rossi" else "Bianchi"
        override fun player(side: Side, index: Int) = listOf(listOf("Rossi", "Verdi"), listOf("Bianchi", "Neri"))[side.ordinal][index]
    }
    private val itb = CallBuilder(Lang.IT)
    private val en = CallBuilder(Lang.EN)

    private var state = ScoreEngine.initial(RulesConfig(firstServer = Side.P1))
    private var last: Transition? = null

    private fun point(w: Side): String {
        val step = ScoreEngine.pointWonBy(state, w)
        state = step.state
        last = step.transition
        return itb.render(itb.afterPoint(state, last!!, names))
    }

    private fun pointEn(w: Side): String {
        val step = ScoreEngine.pointWonBy(state, w)
        state = step.state
        last = step.transition
        return en.render(en.afterPoint(state, last!!, names))
    }

    private fun games(a: Int, b: Int) {
        var ga = 0
        var gb = 0
        while (ga < a || gb < b) {
            val w = if (ga < a && (ga <= gb || gb >= b)) { ga++; Side.P1 } else { gb++; Side.P2 }
            repeat(4) { state = ScoreEngine.pointWonBy(state, w).state }
        }
    }

    @Test
    fun startCall() {
        assertEquals("primo set Rossi al servizio gioco", itb.render(itb.start(state, names)))
        assertEquals(CallBuilder.TAG_PLAY, (itb.start(state, names).last() as Seg.Clip).tag)
        assertEquals("first set Rossi to serve play", en.render(en.start(state, names)))
    }

    @Test
    fun pointsReadFromServer() {
        assertEquals("quindici zero", point(Side.P1))
        assertEquals("quindici pari", point(Side.P2))
        assertEquals("quindici trenta", point(Side.P2))
        assertEquals("quindici quaranta", point(Side.P2))
        assertEquals("trenta quaranta", point(Side.P1))
        assertEquals("parità", point(Side.P1))
        assertEquals("vantaggio Bianchi", point(Side.P2))
        assertEquals("parità", point(Side.P1))
        assertEquals("vantaggio Rossi", point(Side.P1))
    }

    @Test
    fun receiverScoresFirstIsZeroFifteen() {
        assertEquals("zero quindici", point(Side.P2))
        point(Side.P2)
        assertEquals("zero quaranta", point(Side.P2))
    }

    @Test
    fun gameCallsAndChangeOfEnds() {
        repeat(3) { point(Side.P1) }
        assertEquals("gioco Rossi Rossi conduce un gioco a zero cambio campo", point(Side.P1))
        repeat(3) { point(Side.P2) }
        assertEquals("gioco Bianchi un gioco pari", point(Side.P2))
        repeat(3) { point(Side.P1) }
        assertEquals("gioco Rossi Rossi conduce due giochi a uno cambio campo", point(Side.P1))
    }

    @Test
    fun englishGameCalls() {
        repeat(3) { pointEn(Side.P1) }
        assertEquals("game Rossi Rossi leads one game to love change ends", pointEn(Side.P1))
        repeat(3) { pointEn(Side.P2) }
        assertEquals("game Bianchi one game all", pointEn(Side.P2))
    }

    @Test
    fun sixAllTiebreakAndTiebreakCalls() {
        games(5, 5)
        repeat(3) { point(Side.P1) }
        point(Side.P1)
        repeat(3) { point(Side.P2) }
        assertEquals("gioco Bianchi sei giochi pari tie-break", point(Side.P2))
        assertEquals("uno a zero Rossi", point(Side.P1))
        assertEquals("uno pari", point(Side.P2))
        assertEquals("due a uno Bianchi", point(Side.P2))
        point(Side.P2); point(Side.P1)
        // 6 punti giocati: 3-3 -> cambio campo dopo il punteggio
        assertEquals("tre pari cambio campo", point(Side.P1))
    }

    @Test
    fun tiebreakWinCallsSetStanding() {
        games(5, 5)
        repeat(4) { point(Side.P1) }
        repeat(4) { point(Side.P2) }
        repeat(6) { point(Side.P1) }
        assertEquals("gioco Rossi Rossi conduce un set a zero cambio campo", point(Side.P1))
    }

    @Test
    fun setAllCall() {
        games(6, 0)
        games(0, 5)
        repeat(3) { point(Side.P2) }
        // secondo set 0-6 = 6 game: nessun cambio a fine set
        assertEquals("gioco Bianchi un set pari", point(Side.P2))
    }

    @Test
    fun superTiebreakAnnounced() {
        state = ScoreEngine.initial(RulesConfig(format = MatchFormat.TWO_SETS_MATCH_TIEBREAK))
        games(6, 0)
        games(0, 5)
        repeat(3) { point(Side.P2) }
        assertEquals("gioco Bianchi un set pari super tie-break", point(Side.P2))
    }

    @Test
    fun matchEndReadsSetsFromWinnerSide() {
        games(6, 4)
        games(3, 6)
        games(6, 5)
        repeat(3) { point(Side.P1) }
        assertEquals("gioco, set, partita Rossi sei quattro tre sei sette cinque", point(Side.P1))
        assertTrue(state.isFinished)
    }

    @Test
    fun correctionCall() {
        val one = ScoreEngine.replay(state.rules, listOf(MatchEvent.Point(Side.P1)))
        assertEquals("correzione quindici zero", itb.render(itb.correction(one, names)))
        val fresh = ScoreEngine.initial(state.rules)
        assertEquals("correzione", itb.render(itb.correction(fresh, names)))
    }

    @Test
    fun noAdDeuceCall() {
        state = ScoreEngine.initial(RulesConfig(noAd = true))
        repeat(3) { point(Side.P1) }
        repeat(2) { point(Side.P2) }
        assertEquals("parità punto decisivo", point(Side.P2))
    }

    @Test
    fun doublesStartUsesIndividualServer() {
        val d = ScoreEngine.initial(RulesConfig(doubles = true, firstServer = Side.P2, firstServerP2 = 1))
        assertEquals("primo set Neri al servizio gioco", itb.render(itb.start(d, names)))
    }

    @Test
    fun catalogHasEveryKeyUsed() {
        val keys = Phrases.keys.toSet()
        assertTrue("score_3_2" in keys)
        assertTrue("games_6_5" in keys)
        assertTrue("games_all_6" in keys)
        assertTrue("num_30" in keys)
        assertEquals(keys.size, Phrases.keys.size)
        for (k in keys) {
            assertTrue(k, Phrases.text(k, Lang.IT).isNotBlank())
            assertTrue(k, Phrases.text(k, Lang.EN).isNotBlank())
        }
        assertEquals("tre giochi a due", Phrases.text("games_3_2", Lang.IT))
        assertEquals("one game to love", Phrases.text("games_1_0", Lang.EN))
        assertEquals("trenta quindici", Phrases.text("score_2_1", Lang.IT))
    }
}
TSM_EOF

# ---------------------------------------------------------------- app/src/test/java/com/tennis/scoremanager/voice/LanguagesTest.kt
cat > "$DEST/app/src/test/java/com/tennis/scoremanager/voice/LanguagesTest.kt" << 'TSM_EOF'
package com.tennis.scoremanager.voice

import com.tennis.scoremanager.model.Lang
import com.tennis.scoremanager.model.MatchFormat
import com.tennis.scoremanager.model.MatchState
import com.tennis.scoremanager.model.RulesConfig
import com.tennis.scoremanager.model.ScoreEngine
import com.tennis.scoremanager.model.Side
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Le stesse partite chiamate in ogni lingua, con le formule dei testi ufficiali (vedi CallWords). */
class LanguagesTest {

    private val names = object : CallNames {
        override fun side(side: Side) = if (side == Side.P1) "Rossi" else "Bianchi"
        override fun player(side: Side, index: Int) = listOf(listOf("Rossi", "Verdi"), listOf("Bianchi", "Neri"))[side.ordinal][index]
    }

    /** Gioca la partita con [lang] e registra la chiamata dopo ogni punto. */
    private inner class Match(val lang: Lang, rules: RulesConfig = RulesConfig(firstServer = Side.P1)) {
        val calls = CallBuilder(lang)
        var state: MatchState = ScoreEngine.initial(rules)

        fun start() = calls.render(calls.start(state, names))

        fun point(w: Side): String {
            val step = ScoreEngine.pointWonBy(state, w)
            state = step.state
            return calls.render(calls.afterPoint(state, step.transition!!, names))
        }

        fun games(a: Int, b: Int) {
            var ga = 0
            var gb = 0
            while (ga < a || gb < b) {
                val w = if (ga < a && (ga <= gb || gb >= b)) { ga++; Side.P1 } else { gb++; Side.P2 }
                repeat(4) { state = ScoreEngine.pointWonBy(state, w).state }
            }
        }
    }

    private data class Expected(
        val start: String,
        val points: List<String>,
        val firstGame: String,
        val gamesAll: String,
        val tiebreak: List<String>,
        val matchEnd: String,
        val noAd: String,
        val superTiebreakEnd: String,
    )

    /** Punti: 15-0, 15-15, 15-30, 15-40, 30-40, deuce, vantaggio Bianchi. Tie-break: 6-6 annunciato, 1-0, 1-1, 1-2. */
    private fun check(lang: Lang, e: Expected) {
        assertEquals(e.start, Match(lang).start())

        val m = Match(lang)
        val order = listOf(Side.P1, Side.P2, Side.P2, Side.P2, Side.P1, Side.P1, Side.P2)
        assertEquals(e.points, order.map { m.point(it) })

        val g = Match(lang)
        repeat(3) { g.point(Side.P1) }
        assertEquals(e.firstGame, g.point(Side.P1))
        repeat(3) { g.point(Side.P2) }
        assertEquals(e.gamesAll, g.point(Side.P2))

        val t = Match(lang)
        t.games(5, 5)
        repeat(4) { t.point(Side.P1) }
        repeat(3) { t.point(Side.P2) }
        assertEquals(e.tiebreak, listOf(t.point(Side.P2), t.point(Side.P1), t.point(Side.P2), t.point(Side.P2)))

        val end = Match(lang)
        end.games(6, 4)
        end.games(3, 6)
        end.games(6, 5)
        repeat(3) { end.point(Side.P1) }
        assertEquals(e.matchEnd, end.point(Side.P1))

        val na = Match(lang, RulesConfig(noAd = true))
        repeat(3) { na.point(Side.P1) }
        repeat(2) { na.point(Side.P2) }
        assertEquals(e.noAd, na.point(Side.P2))

        // Match tie-break vinto 10-8: "dix huit", "diez ocho", "dez oito" suonerebbero 18.
        val st = Match(lang, RulesConfig(format = MatchFormat.TWO_SETS_MATCH_TIEBREAK))
        st.games(6, 0)
        st.games(0, 6)
        repeat(8) { st.point(Side.P1); st.point(Side.P2) }
        st.point(Side.P1)
        assertEquals(e.superTiebreakEnd, st.point(Side.P1))
    }

    @Test
    fun italian() = check(
        Lang.IT,
        Expected(
            start = "primo set Rossi al servizio gioco",
            points = listOf("quindici zero", "quindici pari", "quindici trenta", "quindici quaranta", "trenta quaranta", "parità", "vantaggio Bianchi"),
            firstGame = "gioco Rossi Rossi conduce un gioco a zero cambio campo",
            gamesAll = "gioco Bianchi un gioco pari",
            tiebreak = listOf("gioco Bianchi sei giochi pari tie-break", "uno a zero Rossi", "uno pari", "due a uno Bianchi"),
            matchEnd = "gioco, set, partita Rossi sei quattro tre sei sette cinque",
            noAd = "parità punto decisivo",
            superTiebreakEnd = "gioco, set, partita Rossi sei zero zero sei dieci otto",
        ),
    )

    @Test
    fun english() = check(
        Lang.EN,
        Expected(
            start = "first set Rossi to serve play",
            points = listOf("fifteen love", "fifteen all", "fifteen thirty", "fifteen forty", "thirty forty", "deuce", "advantage Bianchi"),
            firstGame = "game Rossi Rossi leads one game to love change ends",
            gamesAll = "game Bianchi one game all",
            tiebreak = listOf("game Bianchi six games all tie-break", "one zero Rossi", "one all", "two one Bianchi"),
            matchEnd = "game, set and match Rossi six four three six seven five",
            noAd = "deuce deciding point",
            superTiebreakEnd = "game, set and match Rossi six love love six ten eight",
        ),
    )

    @Test
    fun french() = check(
        Lang.FR,
        Expected(
            start = "première manche au service Rossi jouez",
            points = listOf("quinze-zéro", "quinze A", "quinze-trente", "quinze-quarante", "trente-quarante", "égalité", "avantage Bianchi"),
            firstGame = "jeu Rossi Rossi mène un jeu à zéro changement de côté",
            gamesAll = "jeu Bianchi un jeu partout",
            tiebreak = listOf("jeu Bianchi six jeux partout jeu décisif", "un zéro Rossi", "un partout", "deux un Bianchi"),
            matchEnd = "jeu, set et match Rossi six quatre trois six sept cinq",
            noAd = "égalité point décisif",
            superTiebreakEnd = "jeu, set et match Rossi six zéro zéro six dix à huit",
        ),
    )

    @Test
    fun german() = check(
        Lang.DE,
        Expected(
            start = "erster Satz Aufschlag Rossi spielen",
            points = listOf("fünfzehn null", "fünfzehn beide", "fünfzehn dreißig", "fünfzehn vierzig", "dreißig vierzig", "Einstand", "Vorteil Bianchi"),
            firstGame = "Spiel Rossi Rossi führt eins zu null Seitenwechsel",
            gamesAll = "Spiel Bianchi ein Spiel beide",
            tiebreak = listOf("Spiel Bianchi sechs beide Tie-Break", "eins zu null Rossi", "eins beide", "zwei zu eins Bianchi"),
            matchEnd = "Spiel, Satz und Sieg Rossi sechs zu vier drei zu sechs sieben zu fünf",
            noAd = "Einstand entscheidender Punkt",
            superTiebreakEnd = "Spiel, Satz und Sieg Rossi sechs zu null null zu sechs zehn zu acht",
        ),
    )

    @Test
    fun spanish() = check(
        Lang.ES,
        Expected(
            start = "primer set al servicio Rossi jueguen",
            points = listOf("quince cero", "quince iguales", "quince treinta", "quince cuarenta", "treinta cuarenta", "iguales", "ventaja Bianchi"),
            firstGame = "juego Rossi Rossi gana un juego a cero cambio de lado",
            gamesAll = "juego Bianchi un juego iguales",
            tiebreak = listOf("juego Bianchi seis juegos iguales tie-break", "uno cero Rossi", "uno iguales", "dos uno Bianchi"),
            matchEnd = "juego, set y partido Rossi seis cuatro tres seis siete cinco",
            noAd = "iguales punto decisivo",
            superTiebreakEnd = "juego, set y partido Rossi seis cero cero seis diez a ocho",
        ),
    )

    @Test
    fun portuguese() = check(
        Lang.PT,
        Expected(
            start = "primeiro set Rossi ao serviço joguem",
            points = listOf("quinze-zero", "quinze iguais", "quinze-trinta", "quinze-quarenta", "trinta-quarenta", "iguais", "vantagem Bianchi"),
            firstGame = "jogo Rossi Rossi vence por um jogo a zero troca de lado",
            gamesAll = "jogo Bianchi um jogo igual",
            tiebreak = listOf("jogo Bianchi seis jogos iguais tie-break", "um a zero Rossi", "um iguais", "dois a um Bianchi"),
            matchEnd = "jogo, set e partida Rossi seis quatro três seis sete cinco",
            noAd = "iguais ponto decisivo",
            superTiebreakEnd = "jogo, set e partida Rossi seis zero zero seis dez a oito",
        ),
    )

    @Test
    fun everyLanguageHasTheWholeCatalog() {
        for (lang in Lang.entries) {
            assertEquals(lang.name, Phrases.FIXED_KEYS.toSet(), CallWords.of(lang).fixed.keys)
            assertEquals(lang.name, Phrases.MAX_NUMBER + 1, CallWords.of(lang).numbers.size)
            assertEquals(lang.name, 4, CallWords.of(lang).points.size)
            for (k in Phrases.keys) {
                val t = Phrases.text(k, lang)
                assertTrue("$lang $k", t.isNotBlank() && "_" !in t)
                // Mai cifre: i motori vocali le leggono come orari, date o sottrazioni.
                assertFalse("$lang $k: $t", t.any { it.isDigit() })
            }
        }
    }

    @Test
    fun fixedKeysAreUnique() {
        assertEquals(Phrases.FIXED_KEYS.size, Phrases.FIXED_KEYS.toSet().size)
        assertEquals(Phrases.keys.size, Phrases.keys.toSet().size)
    }

    @Test
    fun langCodes() {
        for (lang in Lang.entries) assertEquals(lang, Lang.fromCode(lang.code))
        assertEquals(Lang.FR, Lang.fromCode("FR"))
        assertEquals(null, Lang.fromCode("xx"))
    }
}
TSM_EOF

# ---------------------------------------------------------------- app/src/test/java/com/tennis/scoremanager/voice/PronunciationTest.kt
cat > "$DEST/app/src/test/java/com/tennis/scoremanager/voice/PronunciationTest.kt" << 'TSM_EOF'
package com.tennis.scoremanager.voice

import com.tennis.scoremanager.model.Lang
import org.junit.Assert.assertEquals
import org.junit.Test

class PronunciationTest {

    @Test
    fun italianFixes() {
        assertEquals("primo sèt", Pronunciation.fix("primo set", Lang.IT, null))
        assertEquals("gioco, sèt, partita Rossi", Pronunciation.fix("gioco, set, partita Rossi", Lang.IT, null))
        assertEquals("gioco Rossi sei giochi pari taibrèk", Pronunciation.fix("gioco Rossi sei giochi pari tie-break", Lang.IT, null))
        assertEquals("un sèt pari super tie-break", Pronunciation.fix("un set pari super tie-break", Lang.IT, null))
        // "Settimo" e i nomi che contengono "set" non vanno toccati
        assertEquals("Setti conduce", Pronunciation.fix("Setti conduce", Lang.IT, null))
    }

    @Test
    fun samsungIsolatedGioco() {
        assertEquals("giuoco", Pronunciation.fix("gioco", Lang.IT, Pronunciation.SAMSUNG))
        assertEquals("gioco", Pronunciation.fix("gioco", Lang.IT, "com.google.android.tts"))
        assertEquals("gioco Rossi", Pronunciation.fix("gioco Rossi", Lang.IT, Pronunciation.SAMSUNG))
    }

    @Test
    fun englishUntouched() {
        assertEquals("first set Rossi to serve", Pronunciation.fix("first set Rossi to serve", Lang.EN, Pronunciation.SAMSUNG))
    }

    @Test
    fun germanTieBreak() {
        assertEquals("Taibreak", Pronunciation.fix("Tie-Break", Lang.DE, null))
        assertEquals("Match-Taibreak", Pronunciation.fix("Match-Tie-Break", Lang.DE, null))
        assertEquals("sechs beide, Taibreak", Pronunciation.fix("sechs beide Tie-Break", Lang.DE, null))
    }

    @Test
    fun frenchIsolatedA() {
        assertEquals("a", Pronunciation.fix("à", Lang.FR, null))
        assertEquals("dix à huit Rossi", Pronunciation.fix("dix à huit Rossi", Lang.FR, null))
    }

    @Test
    fun otherLanguagesUntouched() {
        assertEquals("juego Rossi seis juegos iguales tie-break", Pronunciation.fix("juego Rossi seis juegos iguales tie-break", Lang.ES, null))
        assertEquals("primeiro set", Pronunciation.fix("primeiro set", Lang.PT, null))
    }
}
TSM_EOF

# ---------------------------------------------------------------- app/src/test/java/com/tennis/scoremanager/voice/VoiceFilesTest.kt
cat > "$DEST/app/src/test/java/com/tennis/scoremanager/voice/VoiceFilesTest.kt" << 'TSM_EOF'
package com.tennis.scoremanager.voice

import com.tennis.scoremanager.model.Lang
import com.tennis.scoremanager.model.MatchFormat
import com.tennis.scoremanager.model.RulesConfig
import com.tennis.scoremanager.model.ScoreEngine
import com.tennis.scoremanager.model.Side
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** ZIP di registrazioni (quali file, in che lingua, ZIP rovinati) e lo zero dei set a fine partita. */
class VoiceFilesTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val audio = ByteArray(200) { (it * 7).toByte() }

    // ---------------------------------------------------------------- dove va ogni file dello ZIP

    @Test
    fun languageIsTheFolderThatContainsTheFile() {
        assertEquals(Lang.IT to "deuce.mp3", VoiceZip.target("it/deuce.mp3", Lang.FR))
        assertEquals(Lang.EN to "game.wav", VoiceZip.target("voice/EN/game.WAV", Lang.FR))
        assertEquals(Lang.FR to "deuce.wav", VoiceZip.target("Registrazioni\\fr\\deuce.wav", Lang.IT))
    }

    @Test
    fun filesOutsideLanguageFoldersUseTheCurrentLanguage() {
        assertEquals(Lang.DE to "deuce.ogg", VoiceZip.target("deuce.ogg", Lang.DE))
        assertEquals(Lang.DE to "deuce.ogg", VoiceZip.target("Registrazioni/deuce.ogg", Lang.DE))
    }

    @Test
    fun generatedFilesAndUnknownNamesAreIgnored() {
        // La cartella voice/ dell'app zippata così com'è: i file generati non diventano registrazioni.
        assertNull(VoiceZip.target("it/tts/deuce.wav", Lang.IT))
        assertNull(VoiceZip.target("voice/it/tts/deuce.wav", Lang.IT))
        assertNull(VoiceZip.target("tts/deuce.wav", Lang.IT))
        assertNull(VoiceZip.target("voice/it/.tts-new/deuce.wav", Lang.IT))
        assertNull(VoiceZip.target("it/vecchie/deuce.mp3", Lang.IT))
        assertNull(VoiceZip.target("it/sconosciuta.mp3", Lang.IT))
        assertNull(VoiceZip.target("it/deuce.txt", Lang.IT))
        assertNull(VoiceZip.target("__MACOSX/it/._deuce.mp3", Lang.IT))
        assertNull(VoiceZip.target("it/", Lang.IT))
    }

    // ---------------------------------------------------------------- estrazione e installazione

    private fun zip(vararg entries: Pair<String, ByteArray>, stored: Boolean = false): File {
        val f = tmp.newFile()
        ZipOutputStream(f.outputStream()).use { z ->
            for ((name, data) in entries) {
                val e = ZipEntry(name)
                if (stored) {
                    e.method = ZipEntry.STORED
                    e.size = data.size.toLong()
                    e.crc = CRC32().apply { update(data) }.value
                }
                z.putNextEntry(e)
                z.write(data)
                z.closeEntry()
            }
        }
        return f
    }

    @Test
    fun importOfTheAppVoiceFolderKeepsRecordings() {
        val base = tmp.newFolder("voice")
        File(base, "it").mkdirs()
        File(base, "it/deuce.wav").writeBytes(audio)
        val z = zip(
            "voice/it/deuce.mp3" to audio,
            "voice/it/tts/deuce.wav" to audio, // dopo it/deuce.mp3 in ordine alfabetico: prima lo sostituiva
            "voice/en/game.wav" to audio,
            "voice/it/play.wav" to ByteArray(10), // troppo piccolo: si ignora
            "advantage.ogg" to audio,
        )
        val staging = tmp.newFolder("staging")
        assertEquals(3, VoiceZip.extract(z, staging, Lang.DE))
        assertEquals(3, VoiceZip.install(staging) { File(base, it.code).apply { mkdirs() } })
        assertEquals(setOf("deuce.mp3"), File(base, "it").list()!!.toSet())
        assertTrue(File(base, "en/game.wav").isFile)
        assertTrue(File(base, "de/advantage.ogg").isFile)
    }

    @Test
    fun truncatedZipFailsBeforeTouchingAnything() {
        val full = zip("it/deuce.mp3" to audio, "it/game.mp3" to audio).readBytes()
        // Tagliato alla fine dei dati: ZipInputStream lo leggeva "tutto" senza accorgersene.
        val cut = tmp.newFile().apply { writeBytes(full.copyOf(full.size - 60)) }
        val staging = tmp.newFolder("staging")
        assertTrue(runCatching { VoiceZip.extract(cut, staging, Lang.IT) }.isFailure)
    }

    @Test
    fun corruptedEntryFailsTheCrcCheck() {
        val bytes = zip("it/deuce.wav" to audio, stored = true).readBytes()
        // Un byte del file audio cambiato (i dati iniziano dopo l'intestazione locale: 30 byte + nome).
        val at = 30 + "it/deuce.wav".length + 50
        bytes[at] = (bytes[at] + 1).toByte()
        val bad = tmp.newFile().apply { writeBytes(bytes) }
        assertTrue(runCatching { VoiceZip.extract(bad, tmp.newFolder("staging"), Lang.IT) }.isFailure)
    }

    // ---------------------------------------------------------------- zero dei set

    @Test
    fun setZeroIsLoveOnlyInEnglish() {
        assertEquals("love", Phrases.text(Phrases.SET_ZERO, Lang.EN))
        assertNull(Phrases.alias(Phrases.SET_ZERO, Lang.EN))
        for (lang in Lang.entries - Lang.EN) {
            assertEquals(Phrases.text("num_0", lang), Phrases.text(Phrases.SET_ZERO, lang))
            // Stesso testo: va bene il file di "num_0" (le registrazioni già fatte restano complete).
            assertEquals("num_0", Phrases.alias(Phrases.SET_ZERO, lang))
        }
        assertFalse(Phrases.alias("num_0", Lang.IT) != null)
    }

    @Test
    fun matchTiebreakKeepsZero() {
        val calls = CallBuilder(Lang.EN)
        var s = ScoreEngine.initial(RulesConfig(firstServer = Side.P1, format = MatchFormat.TWO_SETS_MATCH_TIEBREAK))
        fun game(w: Side) = repeat(4) { s = ScoreEngine.pointWonBy(s, w).state }
        repeat(6) { game(Side.P1) }
        repeat(6) { game(Side.P2) }
        repeat(9) { s = ScoreEngine.pointWonBy(s, Side.P1).state }
        val step = ScoreEngine.pointWonBy(s, Side.P1)
        assertEquals(
            "game, set and match Rossi six love love six ten zero",
            calls.render(calls.afterPoint(step.state, step.transition!!, names)),
        )
    }

    private val names = object : CallNames {
        override fun side(side: Side) = if (side == Side.P1) "Rossi" else "Bianchi"
        override fun player(side: Side, index: Int) = side(side)
    }
}
TSM_EOF

# ---------------------------------------------------------------- build.gradle.kts
cat > "$DEST/build.gradle.kts" << 'TSM_EOF'
// File di build principale: le versioni stanno in gradle/libs.versions.toml
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
TSM_EOF

# ---------------------------------------------------------------- gradle.properties
cat > "$DEST/gradle.properties" << 'TSM_EOF'
org.gradle.jvmargs=-Xmx3072m -Dfile.encoding=UTF-8
android.useAndroidX=true
android.nonTransitiveRClass=true
kotlin.code.style=official
TSM_EOF

# ---------------------------------------------------------------- gradle/libs.versions.toml
cat > "$DEST/gradle/libs.versions.toml" << 'TSM_EOF'
[versions]
agp = "8.13.2"
kotlin = "2.2.21"
composeBom = "2025.12.00"
coreKtx = "1.17.0"
activityCompose = "1.12.2"
lifecycle = "2.9.4"
coroutines = "1.10.2"
serialization = "1.9.0"
junit = "4.13.2"
zxing = "3.5.3"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-compose-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
androidx-compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
androidx-compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-compose-material-icons-extended = { group = "androidx.compose.material", name = "material-icons-extended" }
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "serialization" }
junit = { group = "junit", name = "junit", version.ref = "junit" }
zxing-core = { group = "com.google.zxing", name = "core", version.ref = "zxing" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
TSM_EOF

# ---------------------------------------------------------------- gradle/wrapper/gradle-wrapper.jar
base64 -d > "$DEST/gradle/wrapper/gradle-wrapper.jar" << 'TSM_EOF'
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
AmjHZbyJDvTasg8HbXkII7ZUcMyWx3HKlqfpX0gQVYE8KQgRJRUkl93CmVh3Uyg4gXdDFj62cO4a
yXN3SG8eadcCNrH0wMkh+1GNOSRbgtex3Hfewld34Q36zrNKyvaqiJwWVu6uEuHHuip856OVwajv
mybKYQIX7hCS4U/695CVGvpebEu6xnP2/bnI3MS6bXIKUU3SKTtdAzntZtIW0fiCK33f757E5eBh
AZrEr1dKPM2zt4TjKVsGe42w1/PYm9OxVYSttrF7cti9NK4iuU2w0EQkxNoq76J6eeXV0F1Uha6u
PocqNp2NbqqmTUZoOhkitdUUDrNTn4OK/wBQSwcIAIcj5lUGAADFDAAAUEsBAhQAFAAICAgAAAAh
ALC3ox7pDQAAvicAABAACQAAAAAAAAAAAAAAAAAAAE1FVEEtSU5GL0xJQ0VOU0VVVAUAAQAAAABQ
SwECFAAUAAgICAAAACEAlmkO7HsAAACUAAAAFAAJAAAAAAAAAAAAAAAwDgAATUVUQS1JTkYvTUFO
SUZFU1QuTUZVVAUAAQAAAABQSwECFAAUAAgICAAAACEAAsIE3yIBAABwAQAAMQAJAAAAAAAAAAAA
AAD2DgAAb3JnL2dyYWRsZS9jbGkvQ29tbWFuZExpbmVBcmd1bWVudEV4Y2VwdGlvbi5jbGFzc1VU
BQABAAAAAFBLAQIUABQACAgIAAAAIQBsZK5NbgIAALMDAAAmAAkAAAAAAAAAAAAAAIAQAABvcmcv
Z3JhZGxlL2NsaS9Db21tYW5kTGluZU9wdGlvbi5jbGFzc1VUBQABAAAAAFBLAQIUABQACAgIAAAA
IQBrrAeZWwIAALYEAAAzAAkAAAAAAAAAAAAAAEsTAABvcmcvZ3JhZGxlL2NsaS9Db21tYW5kTGlu
ZVBhcnNlciRBZnRlck9wdGlvbnMuY2xhc3NVVAUAAQAAAABQSwECFAAUAAgICAAAACEABUgOxC4D
AABdBwAAPAAJAAAAAAAAAAAAAAAQFgAAb3JnL2dyYWRsZS9jbGkvQ29tbWFuZExpbmVQYXJzZXIk
QmVmb3JlRmlyc3RTdWJDb21tYW5kLmNsYXNzVVQFAAEAAAAAUEsBAhQAFAAICAgAAAAhAO+A7pzc
BgAAYg4AAD0ACQAAAAAAAAAAAAAAsRkAAG9yZy9ncmFkbGUvY2xpL0NvbW1hbmRMaW5lUGFyc2Vy
JEtub3duT3B0aW9uUGFyc2VyU3RhdGUuY2xhc3NVVAUAAQAAAABQSwECFAAUAAgICAAAACEAxIDB
O00CAACXBAAAPAAJAAAAAAAAAAAAAAABIQAAb3JnL2dyYWRsZS9jbGkvQ29tbWFuZExpbmVQYXJz
ZXIkTWlzc2luZ09wdGlvbkFyZ1N0YXRlLmNsYXNzVVQFAAEAAAAAUEsBAhQAFAAICAgAAAAhAKUE
GSPYAgAASgUAAD0ACQAAAAAAAAAAAAAAwSMAAG9yZy9ncmFkbGUvY2xpL0NvbW1hbmRMaW5lUGFy
c2VyJE9wdGlvbkF3YXJlUGFyc2VyU3RhdGUuY2xhc3NVVAUAAQAAAABQSwECFAAUAAgICAAAACEA
IjgzfKIBAAB9AgAAOAAJAAAAAAAAAAAAAAANJwAAb3JnL2dyYWRsZS9jbGkvQ29tbWFuZExpbmVQ
YXJzZXIkT3B0aW9uUGFyc2VyU3RhdGUuY2xhc3NVVAUAAQAAAABQSwECFAAUAAgICAAAACEAXLd3
EQ4CAABDAwAAMwAJAAAAAAAAAAAAAAAeKQAAb3JnL2dyYWRsZS9jbGkvQ29tbWFuZExpbmVQYXJz
ZXIkT3B0aW9uU3RyaW5nLmNsYXNzVVQFAAEAAAAAUEsBAhQAFAAICAgAAAAhAPqZmAqtAQAAzgIA
ADIACQAAAAAAAAAAAAAAlisAAG9yZy9ncmFkbGUvY2xpL0NvbW1hbmRMaW5lUGFyc2VyJFBhcnNl
clN0YXRlLmNsYXNzVVQFAAEAAAAAUEsBAhQAFAAICAgAAAAhAF9ySiV0AgAAxwQAAD8ACQAAAAAA
AAAAAAAArC0AAG9yZy9ncmFkbGUvY2xpL0NvbW1hbmRMaW5lUGFyc2VyJFVua25vd25PcHRpb25Q
YXJzZXJTdGF0ZS5jbGFzc1VUBQABAAAAAFBLAQIUABQACAgIAAAAIQChI9D7sQQAAGMIAAAmAAkA
AAAAAAAAAAAAAJYwAABvcmcvZ3JhZGxlL2NsaS9Db21tYW5kTGluZVBhcnNlci5jbGFzc1VUBQAB
AAAAAFBLAQIUABQACAgIAAAAIQBs5kG4PAQAAOEHAAAmAAkAAAAAAAAAAAAAAKQ1AABvcmcvZ3Jh
ZGxlL2NsaS9QYXJzZWRDb21tYW5kTGluZS5jbGFzc1VUBQABAAAAAFBLAQIUABQACAgIAAAAIQBT
aI5SUwEAAKwBAAAsAAkAAAAAAAAAAAAAAD06AABvcmcvZ3JhZGxlL2NsaS9QYXJzZWRDb21tYW5k
TGluZU9wdGlvbi5jbGFzc1VUBQABAAAAAFBLAQIUABQACAgIAAAAIQDXNTCqAQMAAJwEAAAzAAkA
AAAAAAAAAAAAAPM7AABvcmcvZ3JhZGxlL2ludGVybmFsL2ZpbGUvUGF0aFRyYXZlcnNhbENoZWNr
ZXIuY2xhc3NVVAUAAQAAAABQSwECFAAUAAgICAAAACEAzX+dg4cBAAADAgAAQQAJAAAAAAAAAAAA
AABePwAAb3JnL2dyYWRsZS9pbnRlcm5hbC9maWxlL2xvY2tpbmcvRXhjbHVzaXZlRmlsZUFjY2Vz
c01hbmFnZXIuY2xhc3NVVAUAAQAAAABQSwECFAAUAAgICAAAACEAxlVR5sIBAACjAgAAPgAJAAAA
AAAAAAAAAABdQQAAb3JnL2dyYWRsZS91dGlsL2ludGVybmFsL1dyYXBwZXJEaXN0cmlidXRpb25V
cmxDb252ZXJ0ZXIuY2xhc3NVVAUAAQAAAABQSwECFAAUAAgICAAAACEA6rj0Po4BAAAeAgAALwAJ
AAAAAAAAAAAAAACUQwAAb3JnL2dyYWRsZS93cmFwcGVyL0Jvb3RzdHJhcE1haW5TdGFydGVyJDEu
Y2xhc3NVVAUAAQAAAABQSwECFAAUAAgICAAAACEAjzQ+dCoDAADkBAAAQQAJAAAAAAAAAAAAAACI
RQAAb3JnL2dyYWRsZS93cmFwcGVyL0Rvd25sb2FkJERlZmF1bHREb3dubG9hZFByb2dyZXNzTGlz
dGVuZXIuY2xhc3NVVAUAAQAAAABQSwECFAAUAAgICAAAACEA+xvp5+YCAAARBQAANAAJAAAAAAAA
AAAAAAAqSQAAb3JnL2dyYWRsZS93cmFwcGVyL0Rvd25sb2FkJFByb3h5QXV0aGVudGljYXRvci5j
bGFzc1VUBQABAAAAAFBLAQIUABQACAgIAAAAIQDhpeNBZgkAACoSAAAhAAkAAAAAAAAAAAAAAHtM
AABvcmcvZ3JhZGxlL3dyYXBwZXIvRG93bmxvYWQuY2xhc3NVVAUAAQAAAABQSwECFAAUAAgICAAA
ACEArVD6lNkBAACyAgAALQAJAAAAAAAAAAAAAAA5VgAAb3JnL2dyYWRsZS93cmFwcGVyL0dyYWRs
ZVVzZXJIb21lTG9va3VwLmNsYXNzVVQFAAEAAAAAUEsBAhQAFAAICAgAAAAhAMdVAEMlFQAAyikA
ACoACQAAAAAAAAAAAAAAdlgAAG9yZy9ncmFkbGUvd3JhcHBlci9HcmFkbGVXcmFwcGVyTWFpbi5j
bGFzc1VUBQABAAAAAFBLAQIUABQACAgIAAAAIQBOkCHD5QsAAP8VAAAiAAkAAAAAAAAAAAAAAPxt
AABvcmcvZ3JhZGxlL3dyYXBwZXIvSW5zdGFsbCQxLmNsYXNzVVQFAAEAAAAAUEsBAhQAFAAICAgA
AAAhAESeOwJrAQAA5wEAAC0ACQAAAAAAAAAAAAAAOnoAAG9yZy9ncmFkbGUvd3JhcHBlci9JbnN0
YWxsJEluc3RhbGxDaGVjay5jbGFzc1VUBQABAAAAAFBLAQIUABQACAgIAAAAIQAxIoJL+Q4AAHgc
AAAgAAkAAAAAAAAAAAAAAAl8AABvcmcvZ3JhZGxlL3dyYXBwZXIvSW5zdGFsbC5jbGFzc1VUBQAB
AAAAAFBLAQIUABQACAgIAAAAIQDsCfwFPAIAAB4EAAAfAAkAAAAAAAAAAAAAAFmLAABvcmcvZ3Jh
ZGxlL3dyYXBwZXIvTG9nZ2VyLmNsYXNzVVQFAAEAAAAAUEsBAhQAFAAICAgAAAAhAOswW/wkAQAA
agEAACYACQAAAAAAAAAAAAAA640AAG9yZy9ncmFkbGUvd3JhcHBlci9QYXRoQXNzZW1ibGVyLmNs
YXNzVVQFAAEAAAAAUEsBAhQAFAAICAgAAAAhAF0m+m/2AwAA9gYAAC4ACQAAAAAAAAAAAAAAbI8A
AG9yZy9ncmFkbGUvd3JhcHBlci9Qcm9wZXJ0aWVzRmlsZUhhbmRsZXIuY2xhc3NVVAUAAQAAAABQ
SwECFAAUAAgICAAAACEAUsq4SO8CAABQBgAALQAJAAAAAAAAAAAAAADHkwAAb3JnL2dyYWRsZS93
cmFwcGVyL1dyYXBwZXJDb25maWd1cmF0aW9uLmNsYXNzVVQFAAEAAAAAUEsBAhQAFAAICAgAAAAh
AACHI+ZVBgAAxQwAACgACQAAAAAAAAAAAAAAGpcAAG9yZy9ncmFkbGUvd3JhcHBlci9XcmFwcGVy
RXhlY3V0b3IuY2xhc3NVVAUAAQAAAABQSwUGAAAAACEAIQAQDQAAzp0AAAAA
TSM_EOF

# ---------------------------------------------------------------- gradle/wrapper/gradle-wrapper.properties
cat > "$DEST/gradle/wrapper/gradle-wrapper.properties" << 'TSM_EOF'
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.14.3-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
TSM_EOF

# ---------------------------------------------------------------- gradlew
cat > "$DEST/gradlew" << 'TSM_EOF'
#!/bin/sh

#
# Copyright © 2015-2021 the original authors.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# SPDX-License-Identifier: Apache-2.0
#

##############################################################################
#
#   Gradle start up script for POSIX generated by Gradle.
#
#   Important for running:
#
#   (1) You need a POSIX-compliant shell to run this script. If your /bin/sh is
#       noncompliant, but you have some other compliant shell such as ksh or
#       bash, then to run this script, type that shell name before the whole
#       command line, like:
#
#           ksh Gradle
#
#       Busybox and similar reduced shells will NOT work, because this script
#       requires all of these POSIX shell features:
#         * functions;
#         * expansions «$var», «${var}», «${var:-default}», «${var+SET}»,
#           «${var#prefix}», «${var%suffix}», and «$( cmd )»;
#         * compound commands having a testable exit status, especially «case»;
#         * various built-in commands including «command», «set», and «ulimit».
#
#   Important for patching:
#
#   (2) This script targets any POSIX shell, so it avoids extensions provided
#       by Bash, Ksh, etc; in particular arrays are avoided.
#
#       The "traditional" practice of packing multiple parameters into a
#       space-separated string is a well documented source of bugs and security
#       problems, so this is (mostly) avoided, by progressively accumulating
#       options in "$@", and eventually passing that to Java.
#
#       Where the inherited environment variables (DEFAULT_JVM_OPTS, JAVA_OPTS,
#       and GRADLE_OPTS) rely on word-splitting, this is performed explicitly;
#       see the in-line comments for details.
#
#       There are tweaks for specific operating systems such as AIX, CygWin,
#       Darwin, MinGW, and NonStop.
#
#   (3) This script is generated from the Groovy template
#       https://github.com/gradle/gradle/blob/HEAD/platforms/jvm/plugins-application/src/main/resources/org/gradle/api/internal/plugins/unixStartScript.txt
#       within the Gradle project.
#
#       You can find Gradle at https://github.com/gradle/gradle/.
#
##############################################################################

# Attempt to set APP_HOME

# Resolve links: $0 may be a link
app_path=$0

# Need this for daisy-chained symlinks.
while
    APP_HOME=${app_path%"${app_path##*/}"}  # leaves a trailing /; empty if no leading path
    [ -h "$app_path" ]
do
    ls=$( ls -ld "$app_path" )
    link=${ls#*' -> '}
    case $link in             #(
      /*)   app_path=$link ;; #(
      *)    app_path=$APP_HOME$link ;;
    esac
done

# This is normally unused
# shellcheck disable=SC2034
APP_BASE_NAME=${0##*/}
# Discard cd standard output in case $CDPATH is set (https://github.com/gradle/gradle/issues/25036)
APP_HOME=$( cd -P "${APP_HOME:-./}" > /dev/null && printf '%s\n' "$PWD" ) || exit

# Use the maximum available, or set MAX_FD != -1 to use that value.
MAX_FD=maximum

warn () {
    echo "$*"
} >&2

die () {
    echo
    echo "$*"
    echo
    exit 1
} >&2

# OS specific support (must be 'true' or 'false').
cygwin=false
msys=false
darwin=false
nonstop=false
case "$( uname )" in                #(
  CYGWIN* )         cygwin=true  ;; #(
  Darwin* )         darwin=true  ;; #(
  MSYS* | MINGW* )  msys=true    ;; #(
  NONSTOP* )        nonstop=true ;;
esac

CLASSPATH="\\\"\\\""


# Determine the Java command to use to start the JVM.
if [ -n "$JAVA_HOME" ] ; then
    if [ -x "$JAVA_HOME/jre/sh/java" ] ; then
        # IBM's JDK on AIX uses strange locations for the executables
        JAVACMD=$JAVA_HOME/jre/sh/java
    else
        JAVACMD=$JAVA_HOME/bin/java
    fi
    if [ ! -x "$JAVACMD" ] ; then
        die "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME

Please set the JAVA_HOME variable in your environment to match the
location of your Java installation."
    fi
else
    JAVACMD=java
    if ! command -v java >/dev/null 2>&1
    then
        die "ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.

Please set the JAVA_HOME variable in your environment to match the
location of your Java installation."
    fi
fi

# Increase the maximum file descriptors if we can.
if ! "$cygwin" && ! "$darwin" && ! "$nonstop" ; then
    case $MAX_FD in #(
      max*)
        # In POSIX sh, ulimit -H is undefined. That's why the result is checked to see if it worked.
        # shellcheck disable=SC2039,SC3045
        MAX_FD=$( ulimit -H -n ) ||
            warn "Could not query maximum file descriptor limit"
    esac
    case $MAX_FD in  #(
      '' | soft) :;; #(
      *)
        # In POSIX sh, ulimit -n is undefined. That's why the result is checked to see if it worked.
        # shellcheck disable=SC2039,SC3045
        ulimit -n "$MAX_FD" ||
            warn "Could not set maximum file descriptor limit to $MAX_FD"
    esac
fi

# Collect all arguments for the java command, stacking in reverse order:
#   * args from the command line
#   * the main class name
#   * -classpath
#   * -D...appname settings
#   * --module-path (only if needed)
#   * DEFAULT_JVM_OPTS, JAVA_OPTS, and GRADLE_OPTS environment variables.

# For Cygwin or MSYS, switch paths to Windows format before running java
if "$cygwin" || "$msys" ; then
    APP_HOME=$( cygpath --path --mixed "$APP_HOME" )
    CLASSPATH=$( cygpath --path --mixed "$CLASSPATH" )

    JAVACMD=$( cygpath --unix "$JAVACMD" )

    # Now convert the arguments - kludge to limit ourselves to /bin/sh
    for arg do
        if
            case $arg in                                #(
              -*)   false ;;                            # don't mess with options #(
              /?*)  t=${arg#/} t=/${t%%/*}              # looks like a POSIX filepath
                    [ -e "$t" ] ;;                      #(
              *)    false ;;
            esac
        then
            arg=$( cygpath --path --ignore --mixed "$arg" )
        fi
        # Roll the args list around exactly as many times as the number of
        # args, so each arg winds up back in the position where it started, but
        # possibly modified.
        #
        # NB: a `for` loop captures its iteration list before it begins, so
        # changing the positional parameters here affects neither the number of
        # iterations, nor the values presented in `arg`.
        shift                   # remove old arg
        set -- "$@" "$arg"      # push replacement arg
    done
fi


# Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass JVM options to this script.
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'

# Collect all arguments for the java command:
#   * DEFAULT_JVM_OPTS, JAVA_OPTS, and optsEnvironmentVar are not allowed to contain shell fragments,
#     and any embedded shellness will be escaped.
#   * For example: A user cannot expect ${Hostname} to be expanded, as it is an environment variable and will be
#     treated as '${Hostname}' itself on the command line.

set -- \
        "-Dorg.gradle.appname=$APP_BASE_NAME" \
        -classpath "$CLASSPATH" \
        -jar "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" \
        "$@"

# Stop when "xargs" is not available.
if ! command -v xargs >/dev/null 2>&1
then
    die "xargs is not available"
fi

# Use "xargs" to parse quoted args.
#
# With -n1 it outputs one arg per line, with the quotes and backslashes removed.
#
# In Bash we could simply go:
#
#   readarray ARGS < <( xargs -n1 <<<"$var" ) &&
#   set -- "${ARGS[@]}" "$@"
#
# but POSIX shell has neither arrays nor command substitution, so instead we
# post-process each arg (as a line of input to sed) to backslash-escape any
# character that might be a shell metacharacter, then use eval to reverse
# that process (while maintaining the separation between arguments), and wrap
# the whole thing up as a single "set" statement.
#
# This will of course break if any of these variables contains a newline or
# an unmatched quote.
#

eval "set -- $(
        printf '%s\n' "$DEFAULT_JVM_OPTS $JAVA_OPTS $GRADLE_OPTS" |
        xargs -n1 |
        sed ' s~[^-[:alnum:]+,./:=@_]~\\&~g; ' |
        tr '\n' ' '
    )" '"$@"'

exec "$JAVACMD" "$@"
TSM_EOF

# ---------------------------------------------------------------- gradlew.bat
cat > "$DEST/gradlew.bat" << 'TSM_EOF'
@rem
@rem Copyright 2015 the original author or authors.
@rem
@rem Licensed under the Apache License, Version 2.0 (the "License");
@rem you may not use this file except in compliance with the License.
@rem You may obtain a copy of the License at
@rem
@rem      https://www.apache.org/licenses/LICENSE-2.0
@rem
@rem Unless required by applicable law or agreed to in writing, software
@rem distributed under the License is distributed on an "AS IS" BASIS,
@rem WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
@rem See the License for the specific language governing permissions and
@rem limitations under the License.
@rem
@rem SPDX-License-Identifier: Apache-2.0
@rem

@if "%DEBUG%"=="" @echo off
@rem ##########################################################################
@rem
@rem  Gradle startup script for Windows
@rem
@rem ##########################################################################

@rem Set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" setlocal

set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
@rem This is normally unused
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

@rem Resolve any "." and ".." in APP_HOME to make it shorter.
for %%i in ("%APP_HOME%") do set APP_HOME=%%~fi

@rem Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass JVM options to this script.
set DEFAULT_JVM_OPTS="-Xmx64m" "-Xms64m"

@rem Find java.exe
if defined JAVA_HOME goto findJavaFromJavaHome

set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if %ERRORLEVEL% equ 0 goto execute

echo. 1>&2
echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH. 1>&2
echo. 1>&2
echo Please set the JAVA_HOME variable in your environment to match the 1>&2
echo location of your Java installation. 1>&2

goto fail

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/java.exe

if exist "%JAVA_EXE%" goto execute

echo. 1>&2
echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME% 1>&2
echo. 1>&2
echo Please set the JAVA_HOME variable in your environment to match the 1>&2
echo location of your Java installation. 1>&2

goto fail

:execute
@rem Setup the command line

set CLASSPATH=


@rem Execute Gradle
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% "-Dorg.gradle.appname=%APP_BASE_NAME%" -classpath "%CLASSPATH%" -jar "%APP_HOME%\gradle\wrapper\gradle-wrapper.jar" %*

:end
@rem End local scope for the variables with windows NT shell
if %ERRORLEVEL% equ 0 goto mainEnd

:fail
rem Set variable GRADLE_EXIT_CONSOLE if you need the _script_ return code instead of
rem the _cmd.exe /c_ return code!
set EXIT_CODE=%ERRORLEVEL%
if %EXIT_CODE% equ 0 set EXIT_CODE=1
if not ""=="%GRADLE_EXIT_CONSOLE%" exit %EXIT_CODE%
exit /b %EXIT_CODE%

:mainEnd
if "%OS%"=="Windows_NT" endlocal

:omega
TSM_EOF

# ---------------------------------------------------------------- settings.gradle.kts
cat > "$DEST/settings.gradle.kts" << 'TSM_EOF'
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "TennisScoreManager"
include(":app")
TSM_EOF

chmod +x "$DEST/gradlew"

# ---------------------------------------------------------------- firmware M5StickS3
mkdir -p "$FWDIR"
cat > "$FWDIR/TSM_Band.ino" << 'TSM_EOF'
/*
  Tennis Score Manager - firmware braccialetto M5StickS3
  --------------------------------------------------------
  KEY1 corto  : punto a chi indossa il braccialetto
  KEY1 lungo  : mostra la batteria (tiene anche acceso il braccialetto se è inattivo)
  KEY2 corto  : annulla l'ultimo punto
  KEY2 lungo  : spegne il braccialetto (riaccensione: tasto laterale, un clic)
  Se non è collegato, un tasto qualsiasi rimanda lo spegnimento automatico.
  Conferma dei tasti (dalla 2.3, con l'app 2.4): il bip di KEY1/KEY2 suona quando il telefono ha
  ricevuto il tasto, non alla pressione. Un tasto non confermato entro 8 s (collegamento perso) non vale:
  "NON INVIATO" e due bip bassi, va ripremuto. Con le app vecchie il bip suona all'invio, come prima.

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
#include <driver/rtc_io.h>

#define FW_VERSION "2.3"

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
static const uint32_t EVT_MAX_AGE_MS       = 8000;                 // tasto non confermato entro 8 s: non vale più, va ripremuto
static const uint32_t EVT_SEND_MAX_MS      = 6500;                 // oltre non si (ri)manda: la conferma deve arrivare prima degli 8 s

// Tasti dell'M5StickS3 (gli stessi pin di M5Unified), premuto = basso. Sono pin RTC (0-21 sull'S3):
// possono svegliare il braccialetto dal deep sleep.
static const gpio_num_t KEY1_PIN = GPIO_NUM_11;
static const gpio_num_t KEY2_PIN = GPIO_NUM_12;

// Testi mostrati dal braccialetto (solo ASCII, maiuscolo), una riga per lingua: vedi TXT[] più sotto.
enum : uint8_t { L_IT, L_EN, L_FR, L_DE, L_ES, L_PT, N_LANG };
static const char* const LANG_CODES[N_LANG] = { "it", "en", "fr", "de", "es", "pt" };
enum : uint8_t {
  T_PAIRING, T_PAIRED, T_RECONNECT, T_NO_PHONE, T_POWER_OFF, T_BATTERY, T_CHARGING, T_NO_LINK, T_NOT_SENT, T_IDLE,
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
#define TXT_NOT_SENT   txt(T_NOT_SENT)
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
#define EVENT_UUID   "7a1e0002-5c3b-4f6e-9d2a-3e7b1c9a0f10"  // [tipo, seq, extra] + dalla 2.3 [id accensione x4, età in 1/10 s]
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
    "NON CONNESSO", "NON INVIATO", "INATTIVO", "TIENI PREMUTO KEY1", "BATTERIA SCARICA", "IMPOSTAZIONI OK", "CARICA COMPLETA",
    "ALIMENTATO DA USB", "USB SCOLLEGATO", "LA CARICA CONTINUA",
    "GAME", "SET", "CARICATA IN %s", "DA %s", "FINE ~%d MIN", "%lds - PREMI UN TASTO" },
  { "PAIRING...", "PAIRED", "RECONNECTING", "NO PHONE", "POWERING OFF", "BATTERY", "CHARGING",
    "NOT CONNECTED", "NOT SENT", "IDLE", "HOLD KEY1", "BATTERY EMPTY", "SETTINGS SAVED", "FULLY CHARGED",
    "USB POWERED", "USB UNPLUGGED", "STILL CHARGING",
    "GAMES", "SETS", "CHARGED IN %s", "%s IN", "~%d MIN LEFT", "%lds - PRESS ANY KEY" },
  { "APPAIRAGE...", "APPAIRE", "RECONNEXION", "AUCUN TELEPHONE", "EXTINCTION", "BATTERIE", "EN CHARGE",
    "NON CONNECTE", "NON ENVOYE", "INACTIF", "MAINTENIR KEY1", "BATTERIE VIDE", "REGLAGES OK", "CHARGE TERMINEE",
    "ALIMENTE PAR USB", "USB DEBRANCHE", "LA CHARGE CONTINUE",
    "JEUX", "MANCHES", "CHARGEE EN %s", "DEPUIS %s", "FIN ~%d MIN", "%lds - APPUYER SUR UNE TOUCHE" },
  { "KOPPELN...", "GEKOPPELT", "VERBINDE NEU", "KEIN TELEFON", "AUSSCHALTEN", "AKKU", "LAEDT",
    "NICHT VERBUNDEN", "NICHT GESENDET", "INAKTIV", "KEY1 GEDRUECKT HALTEN", "AKKU LEER", "EINSTELLUNGEN OK", "VOLL GELADEN",
    "USB-STROM", "USB GETRENNT", "LAEDT WEITER",
    "SPIELE", "SAETZE", "GELADEN IN %s", "SEIT %s", "ENDE ~%d MIN", "%lds - TASTE DRUECKEN" },
  { "VINCULANDO...", "VINCULADA", "RECONECTANDO", "SIN TELEFONO", "APAGANDO", "BATERIA", "CARGANDO",
    "NO CONECTADO", "NO ENVIADO", "INACTIVO", "MANTEN PULSADO KEY1", "BATERIA AGOTADA", "AJUSTES OK", "CARGA COMPLETA",
    "ALIMENTADO POR USB", "USB DESCONECTADO", "SIGUE CARGANDO",
    "JUEGOS", "SETS", "CARGADA EN %s", "HACE %s", "FIN ~%d MIN", "%lds - PULSA UN BOTON" },
  { "PAREANDO...", "PAREADA", "RECONECTANDO", "SEM TELEFONE", "DESLIGANDO", "BATERIA", "CARREGANDO",
    "SEM CONEXAO", "NAO ENVIADO", "INATIVO", "SEGURE KEY1", "BATERIA VAZIA", "AJUSTES OK", "CARGA COMPLETA",
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

// Eventi dei tasti (dalla 2.3). Un evento parte solo se l'app ha attivato le notifiche di EVENT: prima
// NimBLE non manda niente e non lo rimanda dopo. Resta in coda finché l'app non lo conferma ("K|seq");
// a ogni nuova iscrizione (riconnessione) si rimanda quello che non è confermato, finché ha meno di 6,5 s;
// a 8 s dalla pressione un evento non confermato è perso ("NON INVIATO").
struct PendingEvt { uint8_t type; uint8_t seq; uint32_t at; bool sent; };
static const uint8_t PEND_MAX = 6;
static PendingEvt pending[PEND_MAX];
static uint8_t  pendingN = 0;
static uint32_t bootId = 0;                    // casuale a ogni accensione: l'app riconosce i doppioni anche dopo una riconnessione
static volatile uint16_t connHandle = BLE_HS_CONN_HANDLE_NONE;
static volatile bool subscribed = false;       // l'app ascolta EVENT su questa connessione
static volatile bool justSubscribed = false;
static volatile bool ackMode = false;          // l'app conferma gli eventi ("H|1" su questa connessione): bip alla conferma
static uint8_t  ackBuf[8];                     // conferme ricevute dal task BLE, le usa il loop
static volatile uint8_t ackN = 0;

static bool     displayOn = false;
static uint32_t displayOffAt = 0;
static uint32_t pairedOffAt = 0;       // displayOffAt del messaggio "PAIRING OK": finché è lo stesso, è ancora a schermo
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

// Letture dal PM1. Se la lettura I2C fallisce M5Unified dà 0 mV e, per CHG_STAT, "in carica": qui una
// lettura fallita dà 0 mV (si riprova una volta) e "non in carica".
static int batteryMv() {
  int mv = M5.Power.getBatteryVoltage();
  if (mv <= 2500) mv = M5.Power.getBatteryVoltage();
  return mv > 2500 ? mv : 0;
}

static bool chargingNow() {
  if (M5.getBoard() != m5::board_t::board_M5StickS3) return M5.Power.isCharging() == m5::Power_Class::is_charging;
  uint8_t bits;
  if (!M5.Power.M5pm1.getGPIOInputBits(&bits)) return false;
  return !(bits & 0x01);  // PM1 G0 = CHG_STAT, basso = in carica
}

// Percentuale da mostrare: col cavo USB quella della carica, altrimenti dalla tensione (-1 = lettura fallita).
static int batteryPct() {
  if (usbOn) return chgPct;
  const int mv = batteryMv();
  return mv > 0 ? socFromMv(mv) : -1;
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
    if (cfg.lang != oldLang) {
      prefs.putUChar("lang", cfg.lang);
      // La lingua arriva ~1 s dopo il collegamento: se "PAIRING OK" è ancora a schermo, lo si riscrive nella
      // lingua giusta (conta per un braccialetto nuovo, che parte in italiano) senza allungarne la durata.
      if (displayOn && displayOffAt == pairedOffAt && (int32_t)(pairedOffAt - millis()) > 0) {
        drawMessage(TXT_PAIRED, cfg.name, C_BALL);
      }
    }
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
    connHandle = info.getConnHandle();
    subscribed = false;
    ackMode = false;   // ogni connessione la riannuncia ("H|1"): un'app vecchia non conferma
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
    subscribed = false;
    ackMode = false;
    connected = false;
    justDisconnect = true;
  }
};

// L'app attiva le notifiche di EVENT dopo aver scoperto i servizi: prima di allora un evento andrebbe perso.
class EventCallbacks : public NimBLECharacteristicCallbacks {
  void onSubscribe(NimBLECharacteristic* c, NimBLEConnInfo& info, uint16_t subValue) override {
    const bool on = (subValue & 1) != 0;
    if (on) justSubscribed = true;  // prima di "subscribed": il loop non deve inviare senza saperlo
    subscribed = on;
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

// "H|1" (l'app conferma gli eventi) e "K|<seq>" (conferma) si gestiscono qui, senza passare da rxBuf:
// così non cancellano un punteggio arrivato subito prima. I firmware vecchi ignorano i tipi che non conoscono.
class DisplayCallbacks : public NimBLECharacteristicCallbacks {
  void onWrite(NimBLECharacteristic* c, NimBLEConnInfo& info) override {
    NimBLEAttValue v = c->getValue();
    const size_t len = v.length();
    if (len >= 2 && len <= 6 && v.data()[1] == '|') {
      if (v.data()[0] == 'H') {
        ackMode = true;
        return;
      }
      if (v.data()[0] == 'K') {
        char num[8];
        memcpy(num, v.data() + 2, len - 2);
        num[len - 2] = 0;
        const uint8_t seq = (uint8_t)atoi(num);
        portENTER_CRITICAL(&rxMux);
        const uint8_t n = ackN;
        if (n < sizeof(ackBuf)) {
          ackBuf[n] = seq;
          ackN = n + 1;
        }
        portEXIT_CRITICAL(&rxMux);
        return;
      }
    }
    copyValue(c, rxBuf, sizeof(rxBuf), rxReady);
  }
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

// [tipo, seq, extra] come prima (le app vecchie leggono solo questi), poi id di accensione (4 byte) ed età
// dell'evento in decimi di secondo. Si manda col valore nel pacchetto e alla sola connessione: due eventi di
// fila non si sovrascrivono come con setValue() + notify().
static bool sendEvent(uint8_t type, uint8_t seq, uint8_t extra, uint32_t ageMs) {
  if (!connected || !subscribed || !evtChr) return false;
  const uint32_t age = ageMs / 100;
  const uint8_t data[8] = { type, seq, extra, (uint8_t)bootId, (uint8_t)(bootId >> 8), (uint8_t)(bootId >> 16),
                            (uint8_t)(bootId >> 24), (uint8_t)(age > 255 ? 255 : age) };
  evtChr->setValue(data, sizeof(data));
  return evtChr->notify(data, sizeof(data), connHandle);
}

// Evento informativo (batteria, spegnimento): parte subito se l'app ascolta, senza conferma.
static void sendNow(uint8_t type, uint8_t extra = 0) {
  sendEvent(type, ++seqNo, extra, 0);
}

static void confirmBeep(uint8_t type) {
  if (type == EVT_UNDO) beep(1800, 40);
  else beep(2700, 40);
}

// Tasto mai arrivato al telefono: avviso ben diverso dal bip di conferma, va ripremuto.
static void notSent(uint8_t type) {
  identifyUntil = 0;
  drawMessage(TXT_NOT_SENT, type == EVT_UNDO ? "KEY2" : "KEY1", C_RED);
  showFor(2500);
  blinkShown = false;           // il lampeggio "RICONNESSIONE" non lo copre subito
  nextBlink = millis() + 2500;
  beep(500, 120);
  beep(350, 300);
}

static void removePending(uint8_t i) {
  memmove(&pending[i], &pending[i + 1], (pendingN - i - 1) * sizeof(PendingEvt));
  pendingN--;
}

// KEY1/KEY2 da collegati: in coda, li manda serviceEvents() (subito, se l'app ascolta già).
static void queueEvent(uint8_t type, uint32_t now) {
  if (pendingN == PEND_MAX) {
    notSent(pending[0].type);
    removePending(0);
  }
  pending[pendingN++] = { type, ++seqNo, now, false };
}

// A ogni giro del loop: conferme, rinvio dopo una riconnessione, scadenza e invio.
static void serviceEvents(uint32_t now) {
  uint8_t acks[sizeof(ackBuf)];
  portENTER_CRITICAL(&rxMux);
  const uint8_t nAck = ackN;
  memcpy(acks, ackBuf, nAck);
  ackN = 0;
  portEXIT_CRITICAL(&rxMux);
  for (uint8_t a = 0; a < nAck; a++) {
    for (uint8_t i = 0; i < pendingN; i++) {
      if (pending[i].seq != acks[a]) continue;
      confirmBeep(pending[i].type);  // il telefono l'ha ricevuto: solo ora il bip di conferma
      removePending(i);
      break;
    }
  }
  if (justSubscribed) {
    justSubscribed = false;
    for (uint8_t i = 0; i < pendingN; i++) pending[i].sent = false;  // l'invio precedente può essere andato perso
  }
  uint8_t lost = 0;
  for (uint8_t i = 0; i < pendingN;) {
    if ((int32_t)(now - pending[i].at) >= (int32_t)EVT_MAX_AGE_MS) {
      lost = pending[i].type;
      removePending(i);
    } else {
      i++;
    }
  }
  if (lost) notSent(lost);
  for (uint8_t i = 0; i < pendingN;) {
    PendingEvt& e = pending[i];
    // Già mandato, o troppo vecchio per avere la conferma in tempo (l'app lo applicherebbe mentre qui
    // compare "NON INVIATO", e il tasto ripremuto conterebbe due volte).
    if (e.sent || (int32_t)(now - e.at) >= (int32_t)EVT_SEND_MAX_MS) {
      i++;
      continue;
    }
    if (!sendEvent(e.type, e.seq, 0, now - e.at)) break;
    if (ackMode) {
      e.sent = true;
      i++;
    } else {
      // App vecchia (non conferma): vale l'invio, come nei firmware 2.2.
      confirmBeep(e.type);
      removePending(i);
    }
  }
}

static void updateBattery(bool notify) {
  const int mv = batteryMv();
  const int level = usbOn ? chgPct : (mv > 0 ? socFromMv(mv) : -1);
  if (level >= 0) {
    uint8_t v = (uint8_t)constrain(level, 0, 100);
    battChr->setValue(&v, 1);
    if (notify && connected) battChr->notify();
  }
  // Stato per l'app: tensione in mV (più precisa della percentuale), alimentato da USB (chg=1 anche a carica
  // completa: non c'è consumo da misurare), secondi di accensione e di display acceso (consumo e autonomia
  // reali); dal firmware 2.1 anche tensione USB, carica completa e la percentuale mostrata dal braccialetto.
  // Lettura della tensione fallita: niente stato (dalla 2.3). Con "mv=0" l'app vedeva la batteria a zero:
  // falso allarme e stima dei consumi sbagliata. Si riprova al giro successivo.
  if (mv <= 0) return;
  char buf[96];
  uint32_t dsp = displayOnTotalMs + (displayOn ? millis() - displayOnSince : 0);
  const bool chg = usbOn || chargingNow();
  snprintf(buf, sizeof(buf), "mv=%d;chg=%d;up=%lu;dsp=%lu;usb=%d;full=%d;pct=%d",
           mv, chg ? 1 : 0,
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

  bootId = esp_random();  // col Bluetooth acceso è un numero casuale vero
  server = NimBLEDevice::createServer();
  server->setCallbacks(new ServerCallbacks());
  server->advertiseOnDisconnect(false);  // la ripartenza la gestisce il loop

  NimBLEService* svc = server->createService(SERVICE_UUID);
  evtChr = svc->createCharacteristic(EVENT_UUID, NIMBLE_PROPERTY::READ | NIMBLE_PROPERTY::NOTIFY);
  evtChr->setCallbacks(new EventCallbacks());
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
// Col cavo USB il PM1 può non togliere corrente e M5Unified va in deep sleep senza un modo per svegliarsi
// (resterebbe "spento" finché non si stacca il cavo). Si arma il risveglio da KEY1/KEY2, dopo che sono stati
// rilasciati: KEY2 è ancora premuto se lo spegnimento viene dal tasto. Se il PM1 spegne, non conta.
static void armKeyWake() {
  const uint32_t t0 = millis();
  while ((!gpio_get_level(KEY1_PIN) || !gpio_get_level(KEY2_PIN)) && millis() - t0 < 5000) delay(20);
  uint64_t mask = 0;
  if (gpio_get_level(KEY1_PIN)) mask |= 1ULL << KEY1_PIN;
  if (gpio_get_level(KEY2_PIN)) mask |= 1ULL << KEY2_PIN;
  // Ancora premuti dopo 5 s: meglio riaccendersi subito che non svegliarsi più.
  if (!mask) mask = (1ULL << KEY1_PIN) | (1ULL << KEY2_PIN);
  // I tasti hanno già le loro resistenze di pull-up; queste interne tengono alto il pin anche in deep sleep.
  rtc_gpio_pullup_en(KEY1_PIN);
  rtc_gpio_pulldown_dis(KEY1_PIN);
  rtc_gpio_pullup_en(KEY2_PIN);
  rtc_gpio_pulldown_dis(KEY2_PIN);
  esp_sleep_enable_ext1_wakeup_io(mask, ESP_EXT1_WAKEUP_ANY_LOW);
}

static void powerOff(const char* l1, const char* why, uint8_t reason) {
  // L'app deve sapere che il braccialetto si è spento (e perché), non che l'ha perso.
  if (connected) {
    sendNow(EVT_POWER_OFF, reason);
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
  armKeyWake();
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
  chgState = chargingNow() ? CHG_ACTIVE : CHG_IDLE;
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
  const bool chg = chargingNow();  // CHG_STAT basso = in carica (lettura fallita = no: non deve sembrare un cavo)
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
  if (esp_sleep_get_wakeup_cause() == ESP_SLEEP_WAKEUP_EXT1) {
    // Riacceso da un tasto (era spento col cavo USB): i due pin tornano GPIO normali, altrimenti restano
    // al dominio RTC e i tasti non si leggono.
    rtc_gpio_deinit(KEY1_PIN);
    rtc_gpio_deinit(KEY2_PIN);
  }

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
    pairedOffAt = displayOffAt;
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
      queueEvent(EVT_POINT, now);  // il bip arriva con la conferma del telefono (vedi serviceEvents)
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
    sendNow(EVT_BATTERY);
  }
  if (M5.BtnB.wasClicked()) {
    lastActivity = now;
    idleWarned = false;
    if (connected) {
      queueEvent(EVT_UNDO, now);
    } else if (usbOn) {
      chargeScreenFor(CHARGE_SHOW_MS);
    } else {
      keyWhileDisconnected(now);
    }
  }
  if (M5.BtnB.wasHold()) powerOff(TXT_POWER_OFF, usbOn ? TXT_KEEPS_CHG : "", OFF_KEY);
  serviceEvents(now);

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
    const bool charging = chargingNow();
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
TSM_EOF

echo ">> Fatto / done: 69 file del progetto / project files in $DEST"
echo ">> Sketch del braccialetto / wristband sketch: $FWDIR/TSM_Band.ino"
echo ">> Ora apri la cartella del progetto con Android Studio / now open the project folder in Android Studio (File > Open)."
