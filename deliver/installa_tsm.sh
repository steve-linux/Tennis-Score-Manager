#!/usr/bin/env bash
# ============================================================================
#  Tennis Score Manager - installazione completa del progetto (app + firmware)
#  Uso:  bash installa_tsm.sh [cartella_progetto] [cartella_sketch]
#  Predefinite: /home/stefano/AndroidStudioProjects/TennisScoreManager
#               ~/Arduino/TSM_Band
#  Se la cartella del progetto esiste già viene spostata in un backup datato.
# ============================================================================
set -euo pipefail
DEST="${1:-/home/stefano/AndroidStudioProjects/TennisScoreManager}"
FWDIR="${2:-$HOME/Arduino/TSM_Band}"

if [ -d "$DEST" ] && [ -n "$(ls -A "$DEST" 2>/dev/null)" ]; then
  BACKUP="${DEST}.backup-$(date +%Y%m%d-%H%M%S)"
  echo ">> Cartella esistente spostata in: $BACKUP"
  mv "$DEST" "$BACKUP"
fi
mkdir -p "$DEST"

echo ">> Creo le cartelle"
mkdir -p "$DEST/app"
mkdir -p "$DEST/app/src/main"
mkdir -p "$DEST/app/src/main/java/com/tennis/scoremanager"
mkdir -p "$DEST/app/src/main/java/com/tennis/scoremanager/ble"
mkdir -p "$DEST/app/src/main/java/com/tennis/scoremanager/data"
mkdir -p "$DEST/app/src/main/java/com/tennis/scoremanager/model"
mkdir -p "$DEST/app/src/main/java/com/tennis/scoremanager/service"
mkdir -p "$DEST/app/src/main/java/com/tennis/scoremanager/ui"
mkdir -p "$DEST/app/src/main/java/com/tennis/scoremanager/ui/screens"
mkdir -p "$DEST/app/src/main/java/com/tennis/scoremanager/voice"
mkdir -p "$DEST/app/src/main/res/drawable"
mkdir -p "$DEST/app/src/main/res/mipmap-anydpi-v26"
mkdir -p "$DEST/app/src/main/res/values"
mkdir -p "$DEST/app/src/main/res/xml"
mkdir -p "$DEST/app/src/test/java/com/tennis/scoremanager/model"
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
        versionCode = 2
        versionName = "2.0.0"
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
    <uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
    <!-- Posizione: richiesta per i braccialetti e per il luogo nel riepilogo -->
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
    <!-- Servizio in primo piano durante la partita con i braccialetti -->
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

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

# ---------------------------------------------------------------- app/src/main/java/com/tennis/scoremanager/MainActivity.kt
cat > "$DEST/app/src/main/java/com/tennis/scoremanager/MainActivity.kt" << 'TSM_EOF'
package com.tennis.scoremanager

import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
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
        enableEdgeToEdge()
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
import androidx.core.content.FileProvider
import com.tennis.scoremanager.ble.BandEvent
import com.tennis.scoremanager.ble.BandProtocol
import com.tennis.scoremanager.ble.BleManager
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
import com.tennis.scoremanager.ui.Strings
import com.tennis.scoremanager.ui.stringsFor
import com.tennis.scoremanager.voice.Announcer
import com.tennis.scoremanager.voice.CallBuilder
import com.tennis.scoremanager.voice.Seg
import com.tennis.scoremanager.voice.VoicePack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream

enum class Screen { SETUP, OPTIONS, START, MATCH, SUMMARY }

enum class CountdownKind { SHOT_CLOCK, CHANGEOVER, SET_BREAK, TIEBREAK_BREAK }

data class CountdownUi(val kind: CountdownKind, val seconds: Int)

data class LiveMatch(val record: MatchRecord, val state: MatchState)

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
        private const val MESSAGE_MS = 5_000L
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
    val voiceProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    val voiceCount = MutableStateFlow(0)
    /** Aumenta a ogni onResume dell'Activity: le schermate ricontrollano Bluetooth/posizione. */
    val envTick = MutableStateFlow(0)
    private val _toasts = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val toasts: SharedFlow<String> = _toasts

    val strings: Strings get() = stringsFor(options.value.lang)
    fun names(s: SetupData = setup.value): Names = Names(s, strings)
    private fun calls() = CallBuilder(options.value.lang)

    private var runningSince: Long? = null
    private var cdKind: CountdownKind? = null
    private var cdEnd = 0L
    private var lastPointAt = 0L
    private var lastAutosave = 0L
    private var messageJob: Job? = null

    init {
        announcer.lang = options.value.lang
        announcer.enabled = options.value.audio
        scope.launch(io) { voice.writeReadme() }
        refreshVoiceCount()
        if (options.value.mode == PlayMode.BANDS) restoreBands()
        refreshSaved()
        scope.launch { ble.events.collect { onBandEvent(it) } }
        scope.launch { ble.ready.collect { onBandReady(it) } }
        scope.launch { watchBands() }
        scope.launch {
            while (true) {
                tick()
                delay(200)
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
        if (old.lang != v.lang) refreshVoiceCount()
        if (old.mode != v.mode) {
            if (v.mode == PlayMode.BANDS) restoreBands() else ble.disconnectAll()
        }
    }

    fun go(to: Screen) {
        if (to == Screen.START) refreshSaved()
        if (to == Screen.OPTIONS && options.value.mode == PlayMode.BANDS) ble.reconnectAll()
        screen.value = to
    }

    fun back() {
        screen.value = when (screen.value) {
            Screen.OPTIONS -> Screen.SETUP
            Screen.START -> Screen.OPTIONS
            else -> screen.value
        }
    }

    // ---------------------------------------------------------------- braccialetti

    private fun restoreBands() {
        storage.bandAddress(true)?.let { ble.assign(Side.P1, it, storage.bandName(true)) }
        storage.bandAddress(false)?.let { ble.assign(Side.P2, it, storage.bandName(false)) }
    }

    fun assignBand(side: Side, address: String?, name: String?) {
        // Un braccialetto appartiene a un solo giocatore: se era sull'altro lato lo si toglie.
        if (address != null && storage.bandAddress(side == Side.P2) == address) storage.setBand(side == Side.P2, null, null)
        storage.setBand(side == Side.P1, address, name)
        ble.assign(side, address, name)
    }

    private fun bandLabel(side: Side) = if (side == Side.P1) "1" else "2"

    private suspend fun watchBands() {
        var prev: Map<Side, LinkState> = emptyMap()
        ble.bands.collect { bands ->
            if (screen.value == Screen.MATCH && options.value.mode == PlayMode.BANDS) {
                for ((side, info) in bands) {
                    val before = prev[side]
                    if (before == LinkState.READY && info.state == LinkState.IDLE) showMessage(strings.msgBandLost(bandLabel(side)))
                    if (before != LinkState.POWERED_OFF && info.state == LinkState.POWERED_OFF) showMessage(strings.msgBandOff(bandLabel(side)))
                }
            }
            prev = bands.mapValues { it.value.state }
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
        when (e.type) {
            BandProtocol.EVT_POINT -> when (screen.value) {
                Screen.START -> startMatch()
                Screen.MATCH -> if (!endDialog.value) awardPoint(e.side, fromBand = true)
                else -> Unit
            }
            // KEY2 annulla l'ultimo punto, anche dal popup di fine partita. Non può mai confermare la fine.
            BandProtocol.EVT_UNDO -> if (screen.value == Screen.MATCH) undo()
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
        if (o.mode == PlayMode.BANDS) MatchService.start(app)
        fetchLocation()
        // "Primo set" · "[nome] al servizio" · "gioco": il tempo partita parte su "gioco".
        announcer.announce(calls().start(state, names())) { tag -> if (tag == CallBuilder.TAG_PLAY) onPlay() }
    }

    private fun onPlay() {
        val lm = live.value ?: return
        if (lm.record.startedAt != null) return
        runningSince = SystemClock.elapsedRealtime()
        setRecord(lm.record.copy(startedAt = System.currentTimeMillis()))
        startCountdown(CountdownKind.SHOT_CLOCK, SHOT_CLOCK_S)
        bandMessage(strings.bandPlay, "0 - 0", 3)
    }

    fun awardPoint(side: Side, fromBand: Boolean = false) {
        val lm = live.value ?: return
        if (lm.record.suspended || lm.state.isFinished || endDialog.value) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastPointAt < if (fromBand) BAND_GAP_MS else TAP_GAP_MS) return
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
        if (t.setWinner != null && state.rules.doubles) serveOrderPrompt.value = true
    }

    /** Match point / set point / palla break / punto decisivo per il prossimo punto. */
    private fun pressureMessage(state: MatchState): String? {
        val s = strings
        val next = Side.entries.mapNotNull { ScoreEngine.lookahead(state, it) }
        return when {
            next.any { it.matchWinner != null } -> s.msgMatchPoint
            next.any { it.setWinner != null } -> s.msgSetPoint
            ScoreEngine.isBreakPoint(state) -> s.msgBreakPoint
            state.rules.noAd && state.isDeuce -> s.msgDecidingPoint
            else -> null
        }
    }

    /** Annulla l'ultimo punto: si rigiocano gli eventi, quindi funziona anche dopo fine game, set o partita. */
    fun undo() {
        val lm = live.value ?: return
        val events = lm.record.events.toMutableList()
        while (events.isNotEmpty() && events[events.lastIndex] !is MatchEvent.Point) events.removeAt(events.lastIndex)
        if (events.isEmpty()) return
        events.removeAt(events.lastIndex)
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
            if (options.value.mode == PlayMode.BANDS) MatchService.start(app)
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
        val rec = lm.record.copy(finished = true, suspended = false, clockMs = currentClock(), updatedAt = System.currentTimeMillis())
        runningSince = null
        scope.launch(io) {
            storage.deleteMatch(rec.id)
            storage.saveLastFinished(rec)
        }
        summary.value = LiveMatch(rec, lm.state)
        live.value = null
        endDialog.value = false
        stopCountdown()
        screen.value = Screen.SUMMARY
        MatchService.stop(app)
    }

    /** Torna alla prima schermata. Una partita non finita resta salvata tra le sospese. */
    fun newMatch() {
        live.value?.let { lm ->
            live.value = LiveMatch(lm.record.copy(suspended = true, clockMs = currentClock()), lm.state)
            persist()
        }
        announcer.stop()
        live.value = null
        summary.value = null
        runningSince = null
        clockMs.value = 0
        stopCountdown()
        endDialog.value = false
        serveOrderPrompt.value = false
        message.value = null
        MatchService.stop(app)
        updateOptions { it.copy(tossWinner = null) }
        refreshSaved()
        screen.value = Screen.SETUP
    }

    fun resumeSaved(rec: MatchRecord) {
        val cur = options.value
        val o = rec.options.copy(mode = cur.mode, lang = cur.lang, audio = cur.audio)
        setup.value = rec.setup
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
        if (o.mode == PlayMode.BANDS) {
            ble.reconnectAll()
            MatchService.start(app)
        }
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
    }

    private fun stopCountdown() {
        cdKind = null
        countdown.value = null
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
        val lm = live.value ?: return
        lastAutosave = SystemClock.elapsedRealtime()
        val snapshot = lm.record.copy(clockMs = currentClock(), updatedAt = System.currentTimeMillis())
        scope.launch(io) { storage.saveMatch(snapshot) }
    }

    private fun fetchLocation() {
        val id = live.value?.record?.id ?: return
        scope.launch {
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
        scope.launch { voiceCount.value = withContext(io) { voice.refresh(l); voice.count(l) } }
    }

    fun generateVoice() {
        if (voiceProgress.value != null) return
        val l = options.value.lang
        scope.launch {
            voiceProgress.value = 0 to com.tennis.scoremanager.voice.Phrases.keys.size
            val ok = announcer.generateVoicePack(l) { i, n -> voiceProgress.value = i to n }
            voiceProgress.value = null
            refreshVoiceCount()
            _toasts.tryEmit(strings.voiceFiles(ok, com.tennis.scoremanager.voice.Phrases.keys.size))
        }
    }

    fun importVoiceZip(uri: Uri) {
        val l = options.value.lang
        scope.launch {
            val n = withContext(io) { runCatching { voice.importZip(uri, l) }.getOrDefault(0) }
            refreshVoiceCount()
            _toasts.tryEmit(strings.voiceFiles(n, com.tennis.scoremanager.voice.Phrases.keys.size))
        }
    }

    fun deleteCustomVoice() {
        val l = options.value.lang
        scope.launch {
            withContext(io) { voice.deleteCustom(l) }
            refreshVoiceCount()
        }
    }

    fun testVoice() {
        val n = names()
        announcer.announce(
            force = true,
            segs = listOf(
                Seg.Clip("score_1_0"), Seg.Pause(500),
                Seg.Clip("deuce"), Seg.Pause(500),
                Seg.Clip("advantage"), Seg.Say(n.side(Side.P1)), Seg.Pause(500),
                Seg.Clip("game"), Seg.Say(n.side(Side.P1)), Seg.Pause(300),
                Seg.Say(n.side(Side.P1)), Seg.Clip("leads"), Seg.Clip("games_1_0"), Seg.Pause(500),
                Seg.Clip("change_ends"),
            ),
        )
    }

    // ---------------------------------------------------------------- riepilogo

    fun summaryNames(): Names = names(summary.value?.record?.setup ?: setup.value)

    fun defaultFileName(): String {
        val sm = summary.value ?: return "partita"
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
                        val dir = storage.defaultHistoryDir
                        for ((file, _, write) in outputs) File(dir, file).outputStream().use(write)
                        dir.absolutePath
                    }
                }.getOrNull()
            }
            _toasts.tryEmit(if (where != null) s.savedTo(where) else s.saveError)
        }
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

    fun exitApp(activity: Activity) {
        persist()
        announcer.stop()
        ble.disconnectAll()
        MatchService.stop(app)
        activity.finishAndRemoveTask()
    }

    /** Chiamato quando l'Activity va in secondo piano. */
    fun onBackground() = persist()
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
 * Braccialetto -> telefono (notify su EVENT): 2 byte [tipo, sequenza].
 * Telefono -> braccialetto (write su DISPLAY): testo ASCII con campi separati da '|':
 *   P|<mio>|<avversario>|<servizio 0/1/2>|<intestazione>        punteggio del game (grande)
 *   G|<miei game>|<game avv>|<miei set>|<set avv>|<intestazione> riepilogo a fine game
 *   M|<riga 1>|<riga 2>|<secondi>                                messaggio
 * "mio" è sempre il giocatore che indossa il braccialetto; servizio 1 = serve lui, 2 = serve l'avversario.
 */
object BandProtocol {
    val SERVICE: UUID = UUID.fromString("7a1e0001-5c3b-4f6e-9d2a-3e7b1c9a0f10")
    val EVENT: UUID = UUID.fromString("7a1e0002-5c3b-4f6e-9d2a-3e7b1c9a0f10")
    val DISPLAY: UUID = UUID.fromString("7a1e0003-5c3b-4f6e-9d2a-3e7b1c9a0f10")
    val BATTERY_SERVICE: UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
    val BATTERY_LEVEL: UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
    val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    const val EVT_POINT = 1      // KEY1 pressione corta: punto a chi indossa il braccialetto
    const val EVT_UNDO = 2       // KEY2 pressione corta: annulla l'ultimo punto
    const val EVT_POWER_OFF = 3  // KEY2 pressione lunga: il braccialetto si spegne
    const val EVT_BATTERY = 4    // KEY1 pressione lunga: mostra la batteria (solo informativo)

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
        "M|${clean(line1)}|${clean(line2)}|$seconds"
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
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
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

data class FoundBand(val address: String, val name: String, val rssi: Int)

data class BandInfo(
    val address: String,
    val name: String,
    val state: LinkState = LinkState.IDLE,
    val battery: Int? = null,
)

data class BandEvent(val side: Side, val type: Int)

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
    private val _adapterOn = MutableStateFlow(adapter?.isEnabled == true)
    val adapterOn: StateFlow<Boolean> = _adapterOn

    private val links = mutableMapOf<Side, BandLink>()
    private var scanStopJob: Job? = null

    init {
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        ContextCompat.registerReceiver(app, object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                val st = i?.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                _adapterOn.value = st == BluetoothAdapter.STATE_ON
                if (st == BluetoothAdapter.STATE_ON) links.values.forEach { it.connect() }
                if (st == BluetoothAdapter.STATE_OFF) {
                    _scanning.value = false
                    links.values.forEach { it.onAdapterOff() }
                }
            }
        }, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    val isSupported: Boolean get() = adapter != null && app.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
    val isEnabled: Boolean get() = adapter?.isEnabled == true

    fun hasPermissions(): Boolean = requiredPermissions().all {
        ContextCompat.checkSelfPermission(app, it) == PackageManager.PERMISSION_GRANTED
    }

    fun startScan(): Boolean {
        val scanner = adapter?.bluetoothLeScanner ?: return false
        if (!hasPermissions() || !isEnabled) return false
        stopScan()
        _found.value = emptyList()
        val filters = listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(BandProtocol.SERVICE)).build())
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        return runCatching {
            scanner.startScan(filters, settings, scanCallback)
            _scanning.value = true
            scanStopJob = scope.launch { delay(15_000); stopScan() }
        }.isSuccess
    }

    fun stopScan() {
        scanStopJob?.cancel()
        if (_scanning.value) runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
        _scanning.value = false
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val addr = result.device.address
            val name = result.scanRecord?.deviceName ?: runCatching { result.device.name }.getOrNull() ?: "TSM-Band"
            _found.update { list -> (list.filterNot { it.address == addr } + FoundBand(addr, name, result.rssi)).sortedBy { it.name } }
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

    /** Riprova a collegare i braccialetti associati (es. dopo aver concesso i permessi). */
    fun reconnectAll() = links.values.forEach { it.connect() }

    fun send(side: Side, payload: String) {
        links[side]?.send(payload)
    }

    fun isReady(side: Side): Boolean = links[side]?.state == LinkState.READY

    fun disconnectAll() {
        stopScan()
        links.values.forEach { it.close() }
        links.clear()
        publish()
    }

    private fun publish() {
        _bands.value = links.mapValues { (_, l) -> BandInfo(l.address, l.name, l.state, l.battery) }
    }

    private inner class BandLink(val side: Side, val address: String, val name: String) {
        var state = LinkState.IDLE
            private set
        var battery: Int? = null
        private var gatt: BluetoothGatt? = null
        private var closed = false
        private var retryJob: Job? = null
        private val opLock = Mutex()
        @Volatile private var pendingOp: CompletableDeferred<Int>? = null
        private val wake = Channel<Unit>(Channel.CONFLATED)
        private var latest: String? = null
        private var lastSeq = -1
        private val sender: Job = scope.launch {
            for (tick in wake) {
                if (state != LinkState.READY) continue
                val msg = latest ?: continue
                latest = null
                if (!write(msg) && latest == null) latest = msg
            }
        }

        private fun changeState(s: LinkState) {
            state = s
            publish()
        }

        fun connect() {
            if (closed || gatt != null || !hasPermissions() || !isEnabled) return
            val dev = runCatching { adapter?.getRemoteDevice(address) }.getOrNull() ?: return
            retryJob?.cancel()
            if (state != LinkState.POWERED_OFF) changeState(LinkState.CONNECTING)
            gatt = runCatching { dev.connectGatt(app, false, callback, BluetoothDevice.TRANSPORT_LE) }.getOrNull()
            if (gatt == null) scheduleReconnect()
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
            pendingOp?.complete(-1)
            runCatching { gatt?.disconnect() }
            runCatching { gatt?.close() }
            gatt = null
        }

        private fun scheduleReconnect() {
            if (closed) return
            retryJob?.cancel()
            retryJob = scope.launch {
                delay(if (state == LinkState.POWERED_OFF) 5_000 else 2_500)
                connect()
            }
        }

        private val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                scope.launch {
                    if (g !== gatt) return@launch
                    if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                        delay(400)
                        runCatching { g.discoverServices() }
                    } else {
                        pendingOp?.complete(-1)
                        runCatching { g.close() }
                        gatt = null
                        if (state != LinkState.POWERED_OFF) changeState(LinkState.IDLE)
                        scheduleReconnect()
                    }
                }
            }

            override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                scope.launch { if (g === gatt) setup(g) }
            }

            override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
                pendingOp?.complete(status)
            }

            override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
                pendingOp?.complete(status)
            }

            override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
                pendingOp?.complete(status)
            }

            override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) handle(c.uuid, value)
                pendingOp?.complete(status)
            }

            @Suppress("OVERRIDE_DEPRECATION")
            override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
                if (Build.VERSION.SDK_INT >= 33) return
                @Suppress("DEPRECATION")
                if (status == BluetoothGatt.GATT_SUCCESS) handle(c.uuid, c.value ?: byteArrayOf())
                pendingOp?.complete(status)
            }

            override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
                handle(c.uuid, value)
            }

            @Suppress("OVERRIDE_DEPRECATION")
            override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
                if (Build.VERSION.SDK_INT >= 33) return
                @Suppress("DEPRECATION")
                handle(c.uuid, c.value ?: byteArrayOf())
            }
        }

        private fun handle(uuid: UUID, value: ByteArray) {
            val copy = value.copyOf()
            scope.launch {
                when (uuid) {
                    BandProtocol.EVENT -> if (copy.size >= 2) {
                        val type = copy[0].toInt() and 0xFF
                        val seq = copy[1].toInt() and 0xFF
                        if (seq == lastSeq) return@launch // stessa pressione ricevuta due volte
                        lastSeq = seq
                        if (type == BandProtocol.EVT_POWER_OFF) changeState(LinkState.POWERED_OFF)
                        _events.tryEmit(BandEvent(side, type))
                    }
                    BandProtocol.BATTERY_LEVEL -> if (copy.isNotEmpty()) {
                        battery = (copy[0].toInt() and 0xFF).coerceIn(0, 100)
                        publish()
                    }
                }
            }
        }

        private suspend fun setup(g: BluetoothGatt) {
            val svc = g.getService(BandProtocol.SERVICE)
            if (svc == null) {
                runCatching { g.disconnect() }
                return
            }
            op { g.requestMtu(185) }
            svc.getCharacteristic(BandProtocol.EVENT)?.let { enableNotify(g, it) }
            g.getService(BandProtocol.BATTERY_SERVICE)?.getCharacteristic(BandProtocol.BATTERY_LEVEL)?.let {
                enableNotify(g, it)
                op { g.readCharacteristic(it) }
            }
            if (g !== gatt) return
            lastSeq = -1
            changeState(LinkState.READY)
            _ready.tryEmit(side)
            wake.trySend(Unit)
        }

        /** Android esegue un'operazione GATT alla volta: le serializziamo e aspettiamo la callback. */
        private suspend fun op(start: () -> Boolean): Boolean = opLock.withLock {
            val d = CompletableDeferred<Int>()
            pendingOp = d
            val started = runCatching(start).getOrDefault(false)
            val result = if (started) withTimeoutOrNull(5_000) { d.await() } else null
            pendingOp = null
            result == BluetoothGatt.GATT_SUCCESS
        }

        private suspend fun enableNotify(g: BluetoothGatt, c: BluetoothGattCharacteristic): Boolean {
            runCatching { g.setCharacteristicNotification(c, true) }
            val d = c.getDescriptor(BandProtocol.CCCD) ?: return false
            val value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            return op {
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

        private suspend fun write(msg: String): Boolean {
            val g = gatt ?: return false
            val c = g.getService(BandProtocol.SERVICE)?.getCharacteristic(BandProtocol.DISPLAY) ?: return false
            val bytes = msg.toByteArray(Charsets.US_ASCII).copyOf(minOf(msg.length, 180))
            return op {
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
import java.util.concurrent.Executors
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
                LocationManagerCompat.getCurrentLocation(lm, provider, signal, Executors.newSingleThreadExecutor()) { loc ->
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Resoconto finale della partita: testo, JSON e immagine da condividere. */
object Reports {

    fun locale(lang: Lang): Locale = if (lang == Lang.IT) Locale.ITALY else Locale.UK

    fun duration(ms: Long): String {
        val s = ms / 1000
        return String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60)
    }

    fun time(ts: Long?, lang: Lang): String =
        ts?.let { SimpleDateFormat("HH:mm", locale(lang)).format(Date(it)) } ?: "--:--"

    fun date(ts: Long?, lang: Lang): String =
        ts?.let {
            SimpleDateFormat(if (lang == Lang.IT) "EEEE d MMMM yyyy" else "EEEE, d MMMM yyyy", locale(lang)).format(Date(it))
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

    fun formatLabel(rec: MatchRecord, s: Strings): String =
        (if (rec.rules.format == MatchFormat.BEST_OF_THREE) s.formatBestOfThree else s.formatMatchTiebreak) +
            (if (rec.rules.noAd) " · No-Ad" else "") +
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
        if (header.isNotEmpty()) c.drawText(ellipsize(header, 56), w / 2f, 175f, paint(46f, white, true, Paint.Align.CENTER))
        c.drawText(date(rec.startedAt, lang).replaceFirstChar { it.uppercase() }, w / 2f, 235f, paint(36f, grey, false, Paint.Align.CENTER))

        // Riquadro del vincitore
        val box = RectF(60f, 290f, w - 60f, 470f)
        c.drawRoundRect(box, 36f, 36f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(60, 255, 255, 255) })
        c.drawText("🏆  ${s.winner.uppercase()}", w / 2f, 355f, paint(38f, grey, true, Paint.Align.CENTER))
        c.drawText(ellipsize(names.side(winner), 28), w / 2f, 440f, paint(66f, if (winner == Side.P1) yellow else red, true, Paint.Align.CENTER))

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
            if (players.size == 1) {
                c.drawText(ellipsize(players[0], 18), 120f, y + rowH / 2 + 22f, paint(58f, white, side == winner))
            } else {
                c.drawText(ellipsize(players[0], 18), 120f, y + rowH / 2 - 14f, paint(50f, white, side == winner))
                c.drawText(ellipsize(players[1], 18), 120f, y + rowH / 2 + 50f, paint(50f, white, side == winner))
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
        val label = paint(34f, grey)
        val value = paint(40f, white, true)
        fun row(l: String, v: String) {
            c.drawText(l, 80f, y, label)
            c.drawText(ellipsize(v, 40), 380f, y, value)
            y += 66f
        }
        row(s.duration, duration(rec.clockMs))
        row("${s.startTime} / ${s.endTime}", "${time(rec.startedAt, lang)} – ${time(rec.endedAt, lang)}")
        row(s.place, rec.location?.address?.ifBlank { null } ?: place(rec, s))
        row(s.format, formatLabel(rec, s))
        c.drawText(s.generatedWith, w / 2f, h - 50f, paint(30f, grey, false, Paint.Align.CENTER))
        return bmp
    }

    private fun ellipsize(t: String, max: Int) = if (t.length <= max) t else t.take(max - 1) + "…"
}
TSM_EOF

# ---------------------------------------------------------------- app/src/main/java/com/tennis/scoremanager/data/Storage.kt
cat > "$DEST/app/src/main/java/com/tennis/scoremanager/data/Storage.kt" << 'TSM_EOF'
package com.tennis.scoremanager.data

import android.content.Context
import android.util.Log
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

    var historyTree: String?
        get() = prefs.getString("history_tree", null)
        set(v) = prefs.edit().putString("history_tree", v).apply()

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

/** Giocatore 1 (giallo) e Giocatore 2 (rosso). Nel doppio indica la squadra. */
@Serializable
enum class Side {
    P1, P2;

    val other: Side get() = if (this == P1) P2 else P1
}

@Serializable
enum class Lang { IT, EN }

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

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.tennis.scoremanager.MainActivity
import com.tennis.scoremanager.R

/**
 * Servizio in primo piano durante la partita con i braccialetti: tiene attivo il processo
 * (Bluetooth, voce e cronometri) anche con lo schermo spento o l'app in secondo piano.
 */
class MatchService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Partita in corso", NotificationManager.IMPORTANCE_LOW))
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_tennis)
            .setContentTitle("Tennis Score Manager")
            .setContentText("Partita in corso · braccialetti attivi")
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
            runCatching { ContextCompat.startForegroundService(ctx, Intent(ctx, MatchService::class.java)) }
                .onFailure { Log.w("MatchService", "start", it) }
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, MatchService::class.java))
        }
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    Surface(color = TsmColors.Background, modifier = Modifier.fillMaxSize()) {
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

/** Schermata standard: intestazione, contenuto scorrevole, barra pulsanti in basso. */
@Composable
fun ScreenScaffold(
    title: String,
    subtitle: String?,
    icon: ImageVector,
    bottomBar: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().systemBarsPadding()) {
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

/** Selettore a segmenti (uno solo attivo). */
@Composable
fun Segmented(options: List<SegOption>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).border(1.dp, TsmColors.Outline, RoundedCornerShape(14.dp)),
    ) {
        options.forEachIndexed { i, o ->
            val sel = i == selected
            Row(
                Modifier.weight(1f)
                    .background(if (sel) o.color else Color.Transparent)
                    .clickable(role = Role.RadioButton) { onSelect(i) }
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
    }
}

@Composable
fun SwitchRow(icon: ImageVector, title: String, hint: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onChange(!checked) }.padding(vertical = 4.dp),
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
        Text(text, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
fun Pill(text: String, color: Color, onColor: Color, icon: ImageVector? = null) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(color).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = onColor, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, color = onColor, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}
TSM_EOF

# ---------------------------------------------------------------- app/src/main/java/com/tennis/scoremanager/ui/Strings.kt
cat > "$DEST/app/src/main/java/com/tennis/scoremanager/ui/Strings.kt" << 'TSM_EOF'
package com.tennis.scoremanager.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.tennis.scoremanager.model.Lang

/** Testi dell'app nelle due lingue. Le etichette dei cronometri restano in inglese come sul tabellone ATP. */
interface Strings {
    val appName: String get() = "Tennis Score Manager"

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
    val searchBands: String
    val searching: String
    val bandFor: (String) -> String
    val noBand: String
    val bandNotFound: String
    val bandConnected: String
    val bandConnecting: String
    val bandIdle: String
    val bandOff: String
    val battery: String
    val languageSection: String
    val italian: String
    val english: String
    val audioSection: String
    val voiceCalls: String
    val voiceFiles: (Int, Int) -> String
    val voiceFilesHint: String
    val generateVoice: String
    val generating: (Int, Int) -> String
    val importVoiceZip: String
    val deleteCustomVoice: String
    val testVoice: String
    val ttsMissing: String
    val installVoice: String
    val formatSection: String
    val formatBestOfThree: String
    val formatBestOfThreeHint: String
    val formatMatchTiebreak: String
    val formatMatchTiebreakHint: String
    val noAd: String
    val noAdHint: String
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
    val bandsMissingText: (String) -> String
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
    val endDialogText: (String, String) -> String
    val matchConcluded: String
    val undoLastPoint: String
    val serveOrderTitle: (Int) -> String
    val whoServesFirst: (String) -> String
    val confirm: String
    val backDisabled: String

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
    val msgBandOff: (String) -> String

    // Braccialetti (solo ASCII, poche lettere)
    val bandPaired: String
    val bandPlay: String
    val bandChangeEnds: String
    val bandTiebreak: String
    val bandSet: String
    val bandSuspended: String
    val bandGameSetMatch: String

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
    val share: String
    val exit: String
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
}

object ItStrings : Strings {
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
    override val searchBands = "Cerca braccialetti"
    override val searching = "Ricerca in corso…"
    override val bandFor: (String) -> String = { "Braccialetto di $it" }
    override val noBand = "Nessuno"
    override val bandNotFound = "Nessun braccialetto trovato: accendilo (tasto laterale) e riprova."
    override val bandConnected = "Connesso"
    override val bandConnecting = "Connessione…"
    override val bandIdle = "Non connesso"
    override val bandOff = "Spento"
    override val battery = "Batteria"
    override val languageSection = "Lingua"
    override val italian = "Italiano"
    override val english = "English"
    override val audioSection = "Audio e voce"
    override val voiceCalls = "Chiamate vocali dell'arbitro"
    override val voiceFiles: (Int, Int) -> String = { n, tot -> "File vocali offline: $n/$tot" }
    override val voiceFilesHint = "Le chiamate fisse usano file audio sul telefono (funzionano senza internet); i nomi sono letti dalla sintesi vocale."
    override val generateVoice = "Genera file"
    override val generating: (Int, Int) -> String = { n, tot -> "Generazione $n/$tot…" }
    override val importVoiceZip = "Importa ZIP"
    override val deleteCustomVoice = "Rimuovi registrazioni"
    override val testVoice = "Prova voce"
    override val ttsMissing = "Voce italiana della sintesi vocale non installata sul telefono."
    override val installVoice = "Installa voce"
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
    override val bandsMissingText: (String) -> String = { "Manca il braccialetto per: $it. Continuare lo stesso?" }
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
    override val endDialogText: (String, String) -> String = { name, score -> "Vince $name\n$score" }
    override val matchConcluded = "Partita conclusa"
    override val undoLastPoint = "Annulla ultimo punto"
    override val serveOrderTitle: (Int) -> String = { "Ordine di servizio · set $it" }
    override val whoServesFirst: (String) -> String = { "Chi serve per primo in $it?" }
    override val confirm = "Conferma"
    override val backDisabled = "Durante la partita usa «Nuova partita» per uscire."

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
    override val msgBandOff: (String) -> String = { "BRACCIALETTO $it SPENTO" }

    override val bandPaired = "ASSOCIATO A"
    override val bandPlay = "GIOCO"
    override val bandChangeEnds = "CAMBIO CAMPO"
    override val bandTiebreak = "TIE-BREAK"
    override val bandSet = "SET"
    override val bandSuspended = "SOSPESA"
    override val bandGameSetMatch = "GAME SET MATCH"

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
    override val share = "Condividi"
    override val exit = "Esci"
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
}

object EnStrings : Strings {
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
    override val searchBands = "Search wristbands"
    override val searching = "Searching…"
    override val bandFor: (String) -> String = { "$it's wristband" }
    override val noBand = "None"
    override val bandNotFound = "No wristband found: switch it on (side button) and retry."
    override val bandConnected = "Connected"
    override val bandConnecting = "Connecting…"
    override val bandIdle = "Not connected"
    override val bandOff = "Off"
    override val battery = "Battery"
    override val languageSection = "Language"
    override val italian = "Italiano"
    override val english = "English"
    override val audioSection = "Audio and voice"
    override val voiceCalls = "Umpire voice calls"
    override val voiceFiles: (Int, Int) -> String = { n, tot -> "Offline voice files: $n/$tot" }
    override val voiceFilesHint = "Fixed calls use audio files stored on the phone (no internet needed); names are read by text-to-speech."
    override val generateVoice = "Generate files"
    override val generating: (Int, Int) -> String = { n, tot -> "Generating $n/$tot…" }
    override val importVoiceZip = "Import ZIP"
    override val deleteCustomVoice = "Remove recordings"
    override val testVoice = "Test voice"
    override val ttsMissing = "English text-to-speech voice is not installed on this phone."
    override val installVoice = "Install voice"
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
    override val bandsMissingText: (String) -> String = { "No wristband for: $it. Continue anyway?" }
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
    override val endDialogText: (String, String) -> String = { name, score -> "$name wins\n$score" }
    override val matchConcluded = "Match concluded"
    override val undoLastPoint = "Undo last point"
    override val serveOrderTitle: (Int) -> String = { "Serving order · set $it" }
    override val whoServesFirst: (String) -> String = { "Who serves first for $it?" }
    override val confirm = "Confirm"
    override val backDisabled = "During the match use «New match» to leave."

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
    override val msgBandOff: (String) -> String = { "WRISTBAND $it OFF" }

    override val bandPaired = "PAIRED WITH"
    override val bandPlay = "PLAY"
    override val bandChangeEnds = "CHANGE ENDS"
    override val bandTiebreak = "TIE-BREAK"
    override val bandSet = "SET"
    override val bandSuspended = "SUSPENDED"
    override val bandGameSetMatch = "GAME SET MATCH"

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
    override val share = "Share"
    override val exit = "Exit"
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
}

fun stringsFor(lang: Lang): Strings = if (lang == Lang.IT) ItStrings else EnStrings

val LocalStrings = staticCompositionLocalOf<Strings> { ItStrings }
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

# ---------------------------------------------------------------- app/src/main/java/com/tennis/scoremanager/ui/screens/MatchScreen.kt
cat > "$DEST/app/src/main/java/com/tennis/scoremanager/ui/screens/MatchScreen.kt" << 'TSM_EOF'
package com.tennis.scoremanager.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
import com.tennis.scoremanager.ui.LocalStrings
import com.tennis.scoremanager.ui.Pill
import com.tennis.scoremanager.ui.SegOption
import com.tennis.scoremanager.ui.Segmented
import com.tennis.scoremanager.ui.Strings
import com.tennis.scoremanager.ui.TsmColors

@Composable
fun MatchScreen(c: MatchController) {
    val s = LocalStrings.current
    val context = LocalContext.current
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

    BackHandler { Toast.makeText(context, s.backDisabled, Toast.LENGTH_SHORT).show() }

    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TimersRow(clock, cd, s)
        if (o.mode == PlayMode.BANDS) BandStatusRow(bands)
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
                        Text(s.suspendedOverlay, color = TsmColors.Orange, fontSize = 26.sp, fontWeight = FontWeight.Black)
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
            ControlButton(
                if (o.audio) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                if (o.audio) s.audioOn else s.audioOff,
                Modifier.weight(1f),
            ) { c.toggleAudio() }
            ControlButton(Icons.Filled.AddCircle, s.newMatch, Modifier.weight(1f)) { confirmNew = true }
        }
    }

    if (end && state.isFinished) {
        val w = state.winner ?: Side.P1
        AlertDialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            icon = { Icon(Icons.Filled.EmojiEvents, null, tint = TsmColors.player(w), modifier = Modifier.size(40.dp)) },
            title = { Text(s.endDialogTitle, fontWeight = FontWeight.Black) },
            text = {
                Text(
                    s.endDialogText(names.side(w), Reports.scoreLine(state, w)),
                    fontSize = 18.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(
                    onClick = { c.confirmEnd() },
                    colors = ButtonDefaults.buttonColors(containerColor = TsmColors.Ball, contentColor = TsmColors.OnBall),
                ) {
                    Icon(Icons.Filled.Check, null)
                    Spacer(Modifier.width(6.dp))
                    Text(s.matchConcluded, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { c.undo() }) {
                    Icon(Icons.AutoMirrored.Filled.Undo, null, tint = TsmColors.TextMain)
                    Spacer(Modifier.width(6.dp))
                    Text(s.undoLastPoint, color = TsmColors.TextMain)
                }
            },
        )
    }

    if (confirmNew) {
        AlertDialog(
            onDismissRequest = { confirmNew = false },
            icon = { Icon(Icons.Filled.AddCircle, null, tint = TsmColors.Orange) },
            title = { Text(s.newMatchConfirmTitle) },
            text = { Text(s.newMatchConfirmText) },
            confirmButton = {
                Button(onClick = {
                    confirmNew = false
                    c.newMatch()
                }) { Text(s.newMatch) }
            },
            dismissButton = { TextButton(onClick = { confirmNew = false }) { Text(s.cancel) } },
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
private fun TimersRow(clock: Long, cd: CountdownUi?, s: Strings) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column {
            Text(s.matchTime, color = TsmColors.TextDim, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(formatClock(clock), color = TsmColors.TextMain, fontSize = 30.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }
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

@Composable
private fun BandStatusRow(bands: Map<Side, BandInfo>) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (side in Side.entries) {
            val b = bands[side]
            val (icon, ok) = when (b?.state) {
                LinkState.READY -> Icons.Filled.BluetoothConnected to true
                LinkState.POWERED_OFF -> Icons.Filled.PowerSettingsNew to false
                else -> Icons.Filled.BluetoothDisabled to false
            }
            Pill(
                (if (side == Side.P1) "G1" else "G2") + (b?.battery?.let { " · $it%" } ?: ""),
                if (ok) TsmColors.player(side) else TsmColors.SurfaceHigh,
                if (ok) TsmColors.onPlayer(side) else TsmColors.TextDim,
                icon,
            )
        }
    }
}

/** Riquadro arancione: spento, si accende 5 secondi con il messaggio. */
@Composable
private fun MessageBox(msg: String?) {
    val bg by animateColorAsState(if (msg != null) TsmColors.Orange else TsmColors.Surface, label = "msg")
    Box(
        Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(14.dp)).background(bg)
            .border(1.dp, if (msg != null) TsmColors.Orange else TsmColors.Outline, RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(msg, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "msgText") { m ->
            if (m != null) {
                Text(m, color = TsmColors.OnOrange, fontWeight = FontWeight.Black, fontSize = 18.sp, maxLines = 2, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis)
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
        val sizeDp = min((maxWidth - gap) / 2, maxHeight - label - 6.dp)
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
                                if (state.inTiebreak) "TIE-BREAK" else " ", color = TsmColors.onPlayer(side).copy(alpha = 0.8f),
                                fontSize = 12.sp, fontWeight = FontWeight.Bold,
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
private fun ControlButton(icon: ImageVector, text: String, modifier: Modifier, highlight: Boolean = false, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (highlight) TsmColors.Orange else TsmColors.SurfaceHigh,
        modifier = modifier.height(52.dp),
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(icon, null, tint = if (highlight) TsmColors.OnOrange else TsmColors.TextMain)
            Spacer(Modifier.width(8.dp))
            Text(text, color = if (highlight) TsmColors.OnOrange else TsmColors.TextMain, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
import android.os.Build
import android.provider.Settings
import android.speech.tts.TextToSpeech
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
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.PowerSettingsNew
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
import com.tennis.scoremanager.ui.BigButton
import com.tennis.scoremanager.ui.GhostButton
import com.tennis.scoremanager.ui.LocalStrings
import com.tennis.scoremanager.ui.RequirementRow
import com.tennis.scoremanager.ui.ScreenScaffold
import com.tennis.scoremanager.ui.SectionCard
import com.tennis.scoremanager.ui.SegOption
import com.tennis.scoremanager.ui.Segmented
import com.tennis.scoremanager.ui.Strings
import com.tennis.scoremanager.ui.SwitchRow
import com.tennis.scoremanager.ui.TsmColors
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
    var permTick by remember { mutableIntStateOf(0) }
    val names = remember(su, s) { Names(su, s) }

    val blePerms = remember(env, permTick) { c.ble.hasPermissions() }
    val locPerm = remember(env, permTick) { LocationHelper.hasPermission(context) }
    val locOn = remember(env, permTick) { LocationHelper.isEnabled(context) }

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
    fun openLocationSettings() = runCatching { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
    fun enableBluetooth() {
        if (!blePerms) requestBandPermissions() else runCatching { btLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }
    }

    var locationDialog by remember { mutableStateOf(false) }
    var bandsRequired by remember { mutableStateOf(false) }
    var missingBands by remember { mutableStateOf<String?>(null) }
    val bandsReadyEnv = blePerms && btOn && locPerm && locOn

    fun onNext() {
        if (o.mode == PlayMode.BANDS) {
            if (!bandsReadyEnv) {
                bandsRequired = true
                return
            }
            val bands = c.ble.bands.value
            val missing = Side.entries.filter { bands[it] == null }.map { names.short(it) }
            if (missing.isNotEmpty()) {
                missingBands = missing.joinToString(", ")
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
            Segmented(
                listOf(SegOption("🇮🇹  ${s.italian}"), SegOption("🇬🇧  ${s.english}")),
                selected = if (o.lang == Lang.IT) 0 else 1,
                onSelect = { i -> c.updateOptions { it.copy(lang = if (i == 0) Lang.IT else Lang.EN) } },
            )
        }

        // Audio e voce
        VoiceSection(c, o)

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
    val found by c.ble.found.collectAsState()
    val scanning by c.ble.scanning.collectAsState()
    val bands by c.ble.bands.collectAsState()
    var searched by remember { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        BigButton(
            if (scanning) s.searching else s.searchBands,
            Icons.AutoMirrored.Filled.BluetoothSearching,
            onClick = {
                searched = true
                if (scanning) c.ble.stopScan() else c.ble.startScan()
            },
            enabled = enabled,
            modifier = Modifier.weight(1f),
            color = TsmColors.SurfaceHigh,
            onColor = TsmColors.TextMain,
        )
        if (scanning) {
            Spacer(Modifier.width(12.dp))
            CircularProgressIndicator(Modifier.size(28.dp), color = TsmColors.Ball, strokeWidth = 3.dp)
        }
    }
    if (searched && !scanning && found.isEmpty()) Text(s.bandNotFound, color = TsmColors.Orange)
    for (side in Side.entries) {
        BandPicker(c, side, names.short(side), bands[side], found, bands)
    }
}

@Composable
private fun BandPicker(c: MatchController, side: Side, playerName: String, current: BandInfo?, found: List<FoundBand>, all: Map<Side, BandInfo>) {
    val s = LocalStrings.current
    var open by remember { mutableStateOf(false) }
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
                        Text(st + (current.battery?.let { " · ${s.battery} $it%" } ?: ""), color = TsmColors.TextDim, fontSize = 12.sp)
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
                        text = { Text(label + (usedBy?.let { " → ${if (it == Side.P1) "G1" else "G2"}" } ?: "")) },
                        leadingIcon = { Icon(Icons.Filled.Watch, null) },
                        onClick = {
                            open = false
                            c.assignBand(side, address, label.substringBefore("  ("))
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun VoiceSection(c: MatchController, o: MatchOptions) {
    val s = LocalStrings.current
    val context = LocalContext.current
    val count by c.voiceCount.collectAsState()
    val progress by c.voiceProgress.collectAsState()
    val tts by c.announcer.status.collectAsState()
    val zipLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { c.importVoiceZip(it) } }
    val total = Phrases.keys.size

    SectionCard(s.audioSection, Icons.AutoMirrored.Filled.VolumeUp) {
        SwitchRow(Icons.Filled.RecordVoiceOver, s.voiceCalls, null, o.audio) { v -> c.updateOptions { it.copy(audio = v) } }
        Text(s.voiceFiles(count, total), color = if (count == total) TsmColors.Ok else TsmColors.Orange, fontWeight = FontWeight.Bold)
        Text(s.voiceFilesHint, color = TsmColors.TextDim, fontSize = 13.sp)
        if (tts == TtsStatus.MISSING_LANGUAGE || tts == TtsStatus.ERROR) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.ttsMissing, color = TsmColors.Orange, modifier = Modifier.weight(1f), fontSize = 13.sp)
                TextButton(onClick = {
                    runCatching { context.startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)) }
                }) { Text(s.installVoice) }
            }
        }
        progress?.let { (i, n) ->
            Text(s.generating(i, n), color = TsmColors.TextMain)
            LinearProgressIndicator(progress = { if (n == 0) 0f else i / n.toFloat() }, modifier = Modifier.fillMaxWidth(), color = TsmColors.Ball)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallAction(s.generateVoice, Icons.Filled.RecordVoiceOver, Modifier.weight(1f), enabled = progress == null) { c.generateVoice() }
            SmallAction(s.importVoiceZip, Icons.Filled.FolderZip, Modifier.weight(1f)) {
                zipLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallAction(s.testVoice, Icons.Filled.PlayCircle, Modifier.weight(1f)) { c.testVoice() }
            SmallAction(s.deleteCustomVoice, Icons.Filled.DeleteOutline, Modifier.weight(1f)) { c.deleteCustomVoice() }
        }
        Text(c.voice.baseDir.absolutePath, color = TsmColors.TextDim, fontSize = 11.sp)
    }
}

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
                        if (side == server) {
                            Spacer(Modifier.height(4.dp))
                            Icon(Icons.Filled.SportsTennis, null, tint = TsmColors.Ball, modifier = Modifier.size(22.dp))
                        }
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
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.tennis.scoremanager.model.MatchFormat
import com.tennis.scoremanager.model.ScoreEngine
import com.tennis.scoremanager.model.Side
import com.tennis.scoremanager.ui.BigButton
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
                        if (side == Side.P1) "G1" else "G2",
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
                            SavedRow(rec, onOpen = {
                                showSaved = false
                                c.resumeSaved(rec)
                            }, onDelete = { c.deleteSaved(rec) })
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSaved = false }) { Text(s.cancel) } },
        )
    }
}

@Composable
private fun SavedRow(rec: MatchRecord, onOpen: () -> Unit, onDelete: () -> Unit) {
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
                "${Reports.date(rec.startedAt ?: rec.updatedAt, rec.options.lang)} · ${Reports.time(rec.updatedAt, rec.options.lang)}",
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
    BackHandler { }

    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(s.summaryTitle, fontSize = 26.sp, fontWeight = FontWeight.Black, color = TsmColors.TextMain)

            // Vincitore
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(TsmColors.player(w).copy(alpha = 0.16f)).padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Filled.EmojiEvents, null, tint = TsmColors.player(w), modifier = Modifier.size(52.dp))
                Text(s.winner.uppercase(), color = TsmColors.TextDim, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Text(names.side(w), color = TsmColors.player(w), fontSize = 30.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
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
            }
        }
        Column(
            Modifier.fillMaxWidth().background(TsmColors.Surface).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BigButton(s.saveHistory, Icons.Filled.Save, { showSave = true }, Modifier.weight(1f))
                BigButton(s.share, Icons.Filled.Share, {
                    c.shareIntent()?.let { runCatching { context.startActivity(it) } }
                }, Modifier.weight(1f), color = TsmColors.Orange, onColor = TsmColors.OnOrange)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostButton(s.newMatch, Icons.Filled.Replay, { c.newMatch() }, Modifier.weight(1f))
                GhostButton(s.exit, Icons.AutoMirrored.Filled.ExitToApp, { activity?.let { c.exitApp(it) } }, Modifier.weight(1f))
            }
        }
    }

    if (showSave) SaveDialog(c) { showSave = false }
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
import android.util.Log
import com.tennis.scoremanager.model.Lang
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
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
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

enum class TtsStatus { INIT, READY, MISSING_LANGUAGE, ERROR }

/**
 * Legge le chiamate: file audio locali quando esistono, altrimenti TTS; i nomi sempre col TTS.
 * L'audio esce dal canale "media", quindi va anche su una cassa Bluetooth collegata al telefono.
 */
class Announcer(context: Context, private val voice: VoicePack) : TextToSpeech.OnInitListener {

    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private var tts: TextToSpeech? = TextToSpeech(app, this)
    private val pending = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()
    private var job: Job? = null
    private var player: MediaPlayer? = null

    private val _status = MutableStateFlow(TtsStatus.INIT)
    val status: StateFlow<TtsStatus> = _status

    var enabled: Boolean = true
        set(value) {
            field = value
            if (!value) stop()
        }

    var lang: Lang = Lang.IT
        set(value) {
            if (field != value) {
                field = value
                applyLanguage(value)
            }
        }

    override fun onInit(status: Int) {
        val t = tts
        if (status != TextToSpeech.SUCCESS || t == null) {
            _status.value = TtsStatus.ERROR
            return
        }
        t.setAudioAttributes(attrs)
        t.setSpeechRate(0.95f)
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
        applyLanguage(lang)
    }

    private fun locale(l: Lang): Locale = if (l == Lang.IT) Locale.ITALY else Locale.UK

    /** Imposta la lingua preferendo una voce installata sul telefono (niente rete). */
    private fun applyLanguage(l: Lang) {
        val t = tts ?: return
        if (_status.value == TtsStatus.ERROR) return
        val loc = locale(l)
        val res = runCatching { t.setLanguage(loc) }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
        if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
            _status.value = TtsStatus.MISSING_LANGUAGE
            return
        }
        runCatching {
            t.voices
                ?.filter { it.locale.language == loc.language && !it.isNetworkConnectionRequired && "notInstalled" !in it.features }
                ?.maxWithOrNull(compareBy({ it.locale.country == loc.country }, { it.quality }))
                ?.let { t.voice = it }
        }
        _status.value = TtsStatus.READY
    }

    private sealed interface Part {
        val tag: String?

        data class Speech(val text: String, override val tag: String?) : Part
        data class Audio(val file: File, override val tag: String?) : Part
        data class Silence(val ms: Long) : Part {
            override val tag: String? get() = null
        }
    }

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
                    val f = voice.fileFor(s.key, l)
                    if (f != null) {
                        flush()
                        parts += Part.Audio(f, s.tag)
                    } else {
                        if (s.tag != null) flush()
                        if (s.tag != null) tag = s.tag
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
     */
    fun announce(segs: List<Seg>, force: Boolean = false, onTag: (String) -> Unit = {}) {
        val tags = segs.mapNotNull {
            when (it) {
                is Seg.Clip -> it.tag
                is Seg.Say -> it.tag
                is Seg.Pause -> null
            }
        }
        stop()
        if ((!enabled && !force) || segs.isEmpty()) {
            tags.forEach(onTag)
            return
        }
        val l = lang
        val fired = mutableSetOf<String>()
        job = scope.launch {
            try {
                for (p in plan(segs, l)) {
                    p.tag?.let { fired += it; onTag(it) }
                    when (p) {
                        is Part.Silence -> delay(p.ms)
                        is Part.Audio -> playFile(p.file)
                        is Part.Speech -> speak(p.text)
                    }
                }
            } finally {
                tags.filter { it !in fired }.forEach(onTag)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
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

    private suspend fun playFile(file: File) {
        val mp = MediaPlayer()
        player = mp
        try {
            mp.setAudioAttributes(attrs)
            mp.setDataSource(file.absolutePath)
            mp.prepare()
            withTimeoutOrNull(mp.duration.toLong().coerceAtLeast(500L) + 1_500L) {
                suspendCancellableCoroutine<Unit> { cont ->
                    mp.setOnCompletionListener { if (cont.isActive) cont.resume(Unit) }
                    mp.setOnErrorListener { _, _, _ -> if (cont.isActive) cont.resume(Unit); true }
                    mp.start()
                }
            }
        } catch (e: Exception) {
            Log.w("Announcer", "File audio non leggibile: ${file.name}", e)
        } finally {
            if (player === mp) player = null
            runCatching { mp.release() }
        }
    }

    /**
     * Genera i file vocali di [l] con il TTS del telefono (una volta sola, poi funzionano offline).
     * Ritorna quanti file sono stati creati.
     */
    suspend fun generateVoicePack(l: Lang, onProgress: (Int, Int) -> Unit): Int {
        val t = tts ?: return 0
        withTimeoutOrNull(5_000) { while (_status.value == TtsStatus.INIT) delay(100) }
        if (_status.value == TtsStatus.ERROR) return 0
        stop()
        applyLanguage(l)
        if (_status.value != TtsStatus.READY) {
            applyLanguage(lang)
            return 0
        }
        val dir = voice.ttsDir(l)
        val keys = Phrases.keys
        var ok = 0
        for ((i, key) in keys.withIndex()) {
            val f = File(dir, "$key.wav")
            val id = "gen_${key}_${UUID.randomUUID()}"
            val done = CompletableDeferred<Boolean>()
            pending[id] = done
            val r = t.synthesizeToFile(Phrases.text(key, l), Bundle(), f, id)
            val good = r == TextToSpeech.SUCCESS && withTimeoutOrNull(20_000) { done.await() } == true
            pending.remove(id)
            if (good && f.length() > 64) ok++ else f.delete()
            onProgress(i + 1, keys.size)
        }
        applyLanguage(lang)
        voice.refresh(l)
        return ok
    }

    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
    }
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
 */
object Phrases {
    const val MAX_NUMBER = 30

    private val numIt = listOf(
        "zero", "uno", "due", "tre", "quattro", "cinque", "sei", "sette", "otto", "nove", "dieci",
        "undici", "dodici", "tredici", "quattordici", "quindici", "sedici", "diciassette", "diciotto",
        "diciannove", "venti", "ventuno", "ventidue", "ventitré", "ventiquattro", "venticinque",
        "ventisei", "ventisette", "ventotto", "ventinove", "trenta",
    )
    private val numEn = listOf(
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
        "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen",
        "nineteen", "twenty", "twenty-one", "twenty-two", "twenty-three", "twenty-four", "twenty-five",
        "twenty-six", "twenty-seven", "twenty-eight", "twenty-nine", "thirty",
    )
    private val pointIt = listOf("zero", "quindici", "trenta", "quaranta")
    private val pointEn = listOf("love", "fifteen", "thirty", "forty")

    private val fixed: Map<String, Pair<String, String>> = linkedMapOf(
        "first_set" to ("primo set" to "first set"),
        "to_serve" to ("al servizio" to "to serve"),
        "play" to ("gioco" to "play"),
        "deuce" to ("parità" to "deuce"),
        "advantage" to ("vantaggio" to "advantage"),
        "deciding_point" to ("punto decisivo" to "deciding point"),
        "game" to ("gioco" to "game"),
        "leads" to ("conduce" to "leads"),
        "sets_1_0" to ("un set a zero" to "one set to love"),
        "sets_all_1" to ("un set pari" to "one set all"),
        "tiebreak" to ("tie-break" to "tie-break"),
        "match_tiebreak" to ("super tie-break" to "match tie-break"),
        "to" to ("a" to "to"),
        "all" to ("pari" to "all"),
        "game_set_match" to ("gioco, set, partita" to "game, set and match"),
        "change_ends" to ("cambio campo" to "change ends"),
        "correction" to ("correzione" to "correction"),
    )

    private fun numberWord(n: Int, lang: Lang): String =
        (if (lang == Lang.IT) numIt else numEn).getOrElse(n) { n.toString() }

    private fun gamesWord(n: Int, lang: Lang): String = when (lang) {
        Lang.IT -> if (n == 1) "un gioco" else "${numberWord(n, lang)} giochi"
        Lang.EN -> if (n == 1) "one game" else "${numberWord(n, lang)} games"
    }

    /** Tutte le chiavi del catalogo, nell'ordine in cui vengono generate. */
    val keys: List<String> by lazy {
        buildList {
            addAll(fixed.keys)
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
        }
    }

    fun text(key: String, lang: Lang): String {
        fixed[key]?.let { return if (lang == Lang.IT) it.first else it.second }
        val parts = key.split('_')
        return when {
            key.startsWith("score_") -> {
                val s = parts[1].toInt()
                val r = parts[2].toInt()
                val words = if (lang == Lang.IT) pointIt else pointEn
                if (s == r) "${words[s]} ${if (lang == Lang.IT) "pari" else "all"}" else "${words[s]} ${words[r]}"
            }
            key.startsWith("games_all_") -> {
                val n = parts[2].toInt()
                "${gamesWord(n, lang)} ${if (lang == Lang.IT) "pari" else "all"}"
            }
            key.startsWith("games_") -> {
                val a = parts[1].toInt()
                val b = parts[2].toInt()
                if (lang == Lang.IT) "${gamesWord(a, lang)} a ${numberWord(b, lang)}"
                else "${gamesWord(a, lang)} to ${if (b == 0) "love" else numberWord(b, lang)}"
            }
            key.startsWith("num_") -> numberWord(parts[1].toInt(), lang)
            else -> key
        }
    }
}

/** Costruisce le chiamate dell'arbitro secondo le regole concordate. */
class CallBuilder(private val lang: Lang) {

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
        Seg.Say(serverName(s, names)),
        Seg.Clip("to_serve"),
        Seg.Pause(START_GAP),
        Seg.Clip("play", tag = TAG_PLAY),
    )

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
        return when (lang) {
            Lang.IT -> listOf(num(a), Seg.Clip("to"), num(b), Seg.Say(names.side(leader)))
            Lang.EN -> listOf(num(a), num(b), Seg.Say(names.side(leader)))
        }
    }

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
            out += Seg.Pause(SHORT)
            out += num(set.shown(winner))
            out += num(set.shown(winner.other))
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

# ---------------------------------------------------------------- app/src/main/java/com/tennis/scoremanager/voice/VoicePack.kt
cat > "$DEST/app/src/main/java/com/tennis/scoremanager/voice/VoicePack.kt" << 'TSM_EOF'
package com.tennis.scoremanager.voice

import android.content.Context
import android.net.Uri
import com.tennis.scoremanager.model.Lang
import java.io.File
import java.util.zip.ZipInputStream

/**
 * Cartella locale con i file audio delle chiamate (uso senza internet).
 *
 *   Android/data/com.tennis.scoremanager/files/voice/it/        registrazioni personalizzate (priorità)
 *   Android/data/com.tennis.scoremanager/files/voice/it/tts/    file generati dal TTS del telefono
 *
 * Il nome di ogni file è la chiave della frase (vedi LEGGIMI.txt), es. `score_1_0.mp3` = "quindici zero".
 */
class VoicePack(private val context: Context) {

    private val exts = listOf("wav", "mp3", "ogg", "m4a", "aac", "flac")
    private val cache = mutableMapOf<Lang, Map<String, File>>()

    val baseDir: File
        get() = File(context.getExternalFilesDir(null) ?: context.filesDir, "voice")

    fun dir(lang: Lang): File = File(baseDir, lang.name.lowercase()).apply { mkdirs() }
    fun ttsDir(lang: Lang): File = File(dir(lang), "tts").apply { mkdirs() }

    @Synchronized
    fun fileFor(key: String, lang: Lang): File? = cache.getOrPut(lang) { scan(lang) }[key]

    @Synchronized
    fun refresh(lang: Lang) {
        cache[lang] = scan(lang)
    }

    fun count(lang: Lang): Int = Phrases.keys.count { fileFor(it, lang) != null }
    fun customCount(lang: Lang): Int = Phrases.keys.count { k -> exts.any { File(dir(lang), "$k.$it").isFile } }

    private fun scan(lang: Lang): Map<String, File> {
        val out = HashMap<String, File>()
        for (folder in listOf(ttsDir(lang), dir(lang))) { // le registrazioni personalizzate sovrascrivono il TTS
            folder.listFiles()?.forEach { f ->
                if (f.isFile && f.extension.lowercase() in exts && f.length() > 64 && f.nameWithoutExtension in Phrases.keys) {
                    out[f.nameWithoutExtension] = f
                }
            }
        }
        return out
    }

    /** Importa uno ZIP di registrazioni: `it/score_1_0.mp3`, `en/deuce.wav` o file alla radice (lingua corrente). */
    fun importZip(uri: Uri, fallback: Lang): Int {
        var n = 0
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) continue
                    val path = entry.name.replace('\\', '/')
                    val file = File(path.substringAfterLast('/'))
                    val key = file.nameWithoutExtension
                    val ext = file.extension.lowercase()
                    if (key !in Phrases.keys || ext !in exts) continue
                    val parts = path.lowercase().split('/')
                    val lang = when {
                        "en" in parts.dropLast(1) -> Lang.EN
                        "it" in parts.dropLast(1) -> Lang.IT
                        else -> fallback
                    }
                    exts.forEach { File(dir(lang), "$key.$it").delete() }
                    File(dir(lang), "$key.$ext").outputStream().use { zip.copyTo(it) }
                    n++
                }
            }
        }
        Lang.entries.forEach { refresh(it) }
        return n
    }

    fun deleteCustom(lang: Lang) {
        dir(lang).listFiles()?.filter { it.isFile && it.extension.lowercase() in exts }?.forEach { it.delete() }
        refresh(lang)
    }

    fun deleteGenerated(lang: Lang) {
        ttsDir(lang).listFiles()?.forEach { it.delete() }
        refresh(lang)
    }

    /** Elenco delle frasi da registrare, scritto nella cartella voce. */
    fun writeReadme() {
        val sb = StringBuilder()
        sb.appendLine("TENNIS SCORE MANAGER - FILE VOCALI")
        sb.appendLine("Metti le registrazioni in voice/it/ oppure voice/en/ con il nome della chiave.")
        sb.appendLine("Formati: ${exts.joinToString()}. I nomi dei giocatori sono sempre letti dal TTS.")
        sb.appendLine("La cartella tts/ contiene i file generati dall'app: le tue registrazioni hanno la precedenza.")
        sb.appendLine()
        sb.appendLine(String.format("%-18s %-32s %s", "CHIAVE", "ITALIANO", "ENGLISH"))
        for (k in Phrases.keys) {
            sb.appendLine(String.format("%-18s %-32s %s", k, Phrases.text(k, Lang.IT), Phrases.text(k, Lang.EN)))
        }
        runCatching { File(baseDir.apply { mkdirs() }, "LEGGIMI.txt").writeText(sb.toString()) }
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
static uint32_t advSince = 0;
static bool     advFast = true;
static bool     advertising = false;
static uint32_t lastActivity = 0;
static uint32_t lastBattery = 0;
static uint32_t nextBlink = 0;
static bool     blinkShown = false;

// ------------------------------------------------------------------ display
static void displayWake() {
  if (!displayOn) {
    M5.Display.wakeup();
    M5.Display.setBrightness(BRIGHTNESS);
    displayOn = true;
  }
}

static void displaySleep() {
  if (displayOn) {
    M5.Display.fillScreen(C_BG);
    M5.Display.setBrightness(0);
    M5.Display.sleep();
    displayOn = false;
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
      if (n >= 5) { drawPoint(f[1], f[2], atoi(f[3]), f[4]); showFor(4000); }
      break;
    case 'G':
      if (n >= 6) { drawGames(atoi(f[1]), atoi(f[2]), atoi(f[3]), atoi(f[4]), f[5]); showFor(6000); }
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
    // Intervallo 30-50 ms con latenza 4: la radio si sveglia ogni ~250 ms quando non c'è traffico,
    // ma un tasto premuto parte al primo evento utile (<= 50 ms).
    s->updateConnParams(info.getConnHandle(), 24, 40, 4, 400);
  }
  void onDisconnect(NimBLEServer* s, NimBLEConnInfo& info, int reason) override {
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
  if (level < 0) return;
  uint8_t v = (uint8_t)constrain(level, 0, 100);
  battChr->setValue(&v, 1);
  if (notify && connected) battChr->notify();
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
  svc->start();

  NimBLEService* bas = server->createService("180F");
  battChr = bas->createCharacteristic("2A19", NIMBLE_PROPERTY::READ | NIMBLE_PROPERTY::NOTIFY);
  bas->start();
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
  if (!connected) {
    if (advFast && now - advSince > FAST_ADV_MS) startAdvertising(false);
    const uint32_t limit = everConnected ? RECONNECT_TIMEOUT_MS : PAIRING_TIMEOUT_MS;
    if (now - advSince > limit) powerOff(TXT_NO_PHONE);
    if ((int32_t)(now - nextBlink) >= 0 && (!displayOn || blinkShown)) {
      drawMessage(TXT_PAIRING, deviceName, C_BALL);
      showFor(BLINK_ON_MS);
      blinkShown = true;
      nextBlink = now + BLINK_PERIOD_MS;
    }
  } else {
    blinkShown = false;
    if (now - lastActivity > IDLE_TIMEOUT_MS) powerOff(TXT_IDLE);
  }

  // --- batteria ogni minuto
  if (now - lastBattery > 60000UL) {
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
TSM_EOF

echo ">> Fatto: 45 file del progetto in $DEST"
echo ">> Sketch del braccialetto in $FWDIR/TSM_Band.ino"
echo ">> Ora apri la cartella del progetto con Android Studio (File > Open)."
