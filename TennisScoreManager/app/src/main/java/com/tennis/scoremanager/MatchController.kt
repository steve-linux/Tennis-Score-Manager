// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

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
import com.tennis.scoremanager.ble.BandStroke
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
import com.tennis.scoremanager.data.Sport
import com.tennis.scoremanager.data.Storage
import com.tennis.scoremanager.data.StrokeStats
import com.tennis.scoremanager.model.MatchEvent
import com.tennis.scoremanager.model.MatchState
import com.tennis.scoremanager.model.PointKind
import com.tennis.scoremanager.model.RulesConfig
import com.tennis.scoremanager.model.ScoreEngine
import com.tennis.scoremanager.model.Side
import com.tennis.scoremanager.model.Transition
import com.tennis.scoremanager.remote.Gesture
import com.tennis.scoremanager.remote.RemoteInput
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import java.io.File
import java.io.OutputStream

enum class Screen { SETUP, OPTIONS, START, MATCH, SUMMARY, HISTORY }

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

    /** Telecomandini Bluetooth da selfie (modalità Telecomandi): i tasti arrivano da MainActivity. */
    val remotes = RemoteInput(app, storage, scope)

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
    /** Statistiche dettagliate: la prima di servizio del punto in corso è fuori (si gioca la seconda). */
    val faultPending = MutableStateFlow(false)
    val saved = MutableStateFlow<List<MatchRecord>>(emptyList())
    /** Storico: partite finite salvate sul telefono, dalla più recente. */
    val history = MutableStateFlow<List<LiveMatch>>(emptyList())
    /** Il riepilogo a schermo è stato aperto dallo storico: in fondo "Indietro" ed "Elimina" invece di nuova partita/esci. */
    val summaryFromHistory = MutableStateFlow(false)
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
    /** Colpi dei braccialetti dall'ultimo punto: diventano la lunghezza dello scambio del prossimo punto. */
    private var rallyStrokes = 0
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
        remotes.enabled = options.value.mode == PlayMode.REMOTES
        remotes.onGesture = { side, g -> onRemoteGesture(side, g) }
        refreshSaved()
        scope.launch { ble.events.collect { onBandEvent(it) } }
        scope.launch { ble.ready.collect { onBandReady(it) } }
        scope.launch { ble.found.collect { autoFill(it) } }
        scope.launch { ble.status.collect { (side, st) -> onBandStatus(side, st) } }
        scope.launch { ble.strokes.collect { onBandStroke(it) } }
        scope.launch { watchBands() }
        scope.launch { watchRemotes() }
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

    /** Doppio e No-Ad com'erano prima di scegliere Padel: tornando a Tennis si rimettono. */
    private var tennisDoubles: Boolean? = null
    private var tennisNoAd: Boolean? = null

    /**
     * Il padel si conta come il tennis: sceglierlo propone doppio e punto d'oro (si possono sempre cambiare).
     * Tornando a Tennis si rimettono com'erano, perché il No-Ad sta in un'altra pagina e rimasto acceso non si nota.
     * Se non si sa com'erano (app riaperta col padel già scelto) il No-Ad si spegne e il doppio resta com'è.
     */
    fun selectSport(sport: Sport) {
        if (sport == setup.value.sport) return
        if (sport == Sport.PADEL) {
            tennisDoubles = setup.value.doubles
            tennisNoAd = options.value.noAd
            updateSetup { it.copy(sport = sport, doubles = true) }
            updateOptions { it.copy(noAd = true) }
        } else {
            val doubles = tennisDoubles
            updateSetup { it.copy(sport = sport, doubles = doubles ?: it.doubles) }
            updateOptions { it.copy(noAd = tennisNoAd ?: false, starPoint = false) }
        }
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
            remotes.enabled = v.mode == PlayMode.REMOTES
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

    /** Calibrazione dei colpi sul braccialetto: [stroke] = BandProtocol.STROKE_*, null = fine. */
    fun calibrateStroke(side: Side, stroke: Int?) = bandCommand(side, BandProtocol.calibrate(stroke))

    fun undoStrokeExample(side: Side) = bandCommand(side, BandProtocol.UNDO_EXAMPLE)

    fun clearStrokeExamples(side: Side, stroke: Int) = bandCommand(side, BandProtocol.clearExamples(stroke))

    private fun bandCommand(side: Side, payload: String) {
        val s = strings
        scope.launch { if (!ble.command(side, payload)) _toasts.tryEmit(s.bandNotReady) }
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
        when (e.type) {
            BandProtocol.EVT_POWER_OFF -> if (screen.value == Screen.MATCH && options.value.mode == PlayMode.BANDS) {
                showMessage(strings.msgBandOff(bandLabel(e.side), e.reason))
            }
            BandProtocol.EVT_POINT -> playerPoint(e.side)
            BandProtocol.EVT_UNDO -> playerUndo()
        }
    }

    /** Punto chiesto da un giocatore (KEY1 del braccialetto o clic del telecomando). */
    private fun playerPoint(side: Side) {
        when (screen.value) {
            Screen.START -> startMatch()
            // Finché la voce non ha detto "gioco" i KEY1 servono solo ad avviare: niente punti sullo 0-0.
            Screen.MATCH -> if (!endDialog.value && live.value?.record?.startedAt != null) awardPoint(side, fromBand = true)
            else -> Unit
        }
    }

    /**
     * KEY2 (o doppio clic / pressione lunga del telecomando) annulla l'ultimo punto, anche dal popup di fine
     * partita. Non può mai confermare la fine. Due annulla ravvicinati (anche da giocatori diversi) tolgono un solo punto.
     */
    private fun playerUndo() {
        val now = SystemClock.elapsedRealtime()
        if (screen.value == Screen.MATCH && now - lastBandUndoAt >= BAND_GAP_MS) {
            lastBandUndoAt = now
            undo()
        }
    }

    // ---------------------------------------------------------------- telecomandi

    private fun onRemoteGesture(side: Side, g: Gesture) {
        if (options.value.mode != PlayMode.REMOTES) return
        when (g) {
            Gesture.POINT -> playerPoint(side)
            Gesture.UNDO -> playerUndo()
        }
    }

    /** Telecomando che si spegne o si riaccende durante la partita: lo si dice, come per i braccialetti. */
    private suspend fun watchRemotes() {
        var prev: Map<Side, Boolean> = emptyMap()
        combine(remotes.assigned, remotes.connected) { assigned, connected ->
            assigned.mapValues { it.value.descriptor in connected }
        }.collect { now ->
            if (screen.value == Screen.MATCH && options.value.mode == PlayMode.REMOTES) {
                for ((side, on) in now) {
                    val before = prev[side] ?: continue
                    if (before != on) showMessage(if (on) strings.msgRemoteConnected(bandLabel(side)) else strings.msgRemoteLost(bandLabel(side)))
                }
            }
            prev = now
        }
    }

    /** I colpi contano solo a partita avviata e in corso (non durante la calibrazione o il riscaldamento). */
    private fun onBandStroke(e: BandStroke) {
        val lm = live.value ?: return
        if (screen.value != Screen.MATCH || lm.record.startedAt == null || lm.record.suspended || lm.state.isFinished) return
        val stats = (lm.record.strokes[e.side] ?: StrokeStats()).add(e.stroke.stroke, e.stroke.peakGyroDps)
        setRecord(lm.record.copy(strokes = lm.record.strokes + (e.side to stats)))
        rallyStrokes++
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
            starPoint = su.sport == Sport.PADEL && o.starPoint && !o.noAd,
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
        rallyStrokes = 0
        faultPending.value = false
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

    /**
     * Statistiche dettagliate: [kind] dice come è finito il punto (null = non detto). La seconda di servizio
     * viene da [fault]: vale per il punto in corso, anche se poi lo dà un braccialetto o un telecomando.
     */
    fun awardPoint(side: Side, fromBand: Boolean = false, kind: PointKind? = null) {
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
        // Lunghezza dello scambio solo se i braccialetti mandano i colpi (almeno uno arrivato in questa partita).
        val strokes = rallyStrokes.takeIf { current.record.strokes.isNotEmpty() }
        rallyStrokes = 0
        val second = faultPending.value
        faultPending.value = false
        val point = MatchEvent.Point(side, System.currentTimeMillis(), strokes, kind, second)
        val rec = current.record.copy(events = current.record.events + point)
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
            // Sul punto decisivo (No-Ad o star point) chi riceve vince il game col prossimo punto, quindi è sempre
            // anche palla break: il punto decisivo va controllato prima.
            state.isDecidingPoint -> s.msgDecidingPoint
            ScoreEngine.isBreakPoint(state) -> s.msgBreakPoint
            else -> null
        }
    }

    /** Prima di servizio fuori; la seconda fuori è doppio fallo, cioè punto a chi risponde. */
    fun fault() {
        val lm = live.value ?: return
        if (closing || lm.record.suspended || lm.state.isFinished || endDialog.value) return
        if (faultPending.value) {
            awardPoint(lm.state.receiver, kind = PointKind.DOUBLE_FAULT)
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (now - lastPointAt < TAP_GAP_MS || now - lastUndoAt < TAP_GAP_MS) return
        if (lm.record.startedAt == null) onPlay()
        faultPending.value = true
        showMessage(strings.msgSecondServe)
        startCountdown(CountdownKind.SHOT_CLOCK, SHOT_CLOCK_S)
    }

    /** Annulla l'ultimo punto: si rigiocano gli eventi, quindi funziona anche dopo fine game, set o partita. */
    fun undo() {
        val lm = live.value ?: return
        if (closing) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastUndoAt < UNDO_GAP_MS) return
        // Con una prima fuori in sospeso, "Annulla" toglie solo quella.
        if (faultPending.value) {
            faultPending.value = false
            lastUndoAt = now
            showMessage(strings.msgFaultUndone)
            return
        }
        val events = lm.record.events.toMutableList()
        while (events.isNotEmpty() && events[events.lastIndex] !is MatchEvent.Point) events.removeAt(events.lastIndex)
        if (events.isEmpty()) return
        // I colpi dello scambio annullato tornano allo scambio in corso: il punto ridato li ritrova.
        rallyStrokes += (events.removeAt(events.lastIndex) as MatchEvent.Point).strokes ?: 0
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
            storage.saveFinished(rec)
            storage.saveLastFinished(rec)
        }
        // Se Android chiude il processo sul riepilogo (es. mentre si condivide) lo si riapre: vedi onActivityCreated.
        storage.summaryOpen = rec.id
        summaryFromHistory.value = false
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
        rallyStrokes = 0
        faultPending.value = false
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

    // ---------------------------------------------------------------- storico

    fun openHistory() {
        refreshHistory()
        screen.value = Screen.HISTORY
    }

    fun refreshHistory() {
        scope.launch {
            history.value = withContext(io) {
                // Prima dello storico l'app teneva solo l'ultima partita finita: entra anche lei.
                storage.loadLastFinished()?.let { if (!storage.hasFinished(it.id)) storage.saveFinished(it) }
                storage.loadFinished().map { LiveMatch(it, ScoreEngine.replay(it.rules, it.events)) }
            }
        }
    }

    fun openFromHistory(m: LiveMatch) {
        summary.value = m
        summaryFromHistory.value = true
        screen.value = Screen.SUMMARY
    }

    /** "Indietro" dal riepilogo aperto dallo storico. */
    fun backToHistory() {
        summary.value = null
        summaryFromHistory.value = false
        openHistory()
    }

    fun deleteFromHistory(id: String) {
        scope.launch {
            withContext(io) {
                storage.deleteFinished(id)
                // Altrimenti l'ultima partita finita rientrerebbe nello storico al prossimo caricamento.
                if (storage.loadLastFinished()?.id == id) storage.clearLastFinished()
            }
            if (summary.value?.record?.id == id) backToHistory() else refreshHistory()
        }
    }

    /**
     * Importa partite salvate come JSON ("Salva file" o "Condividi" di un altro telefono). Le finite vanno nello
     * storico, le altre tra le partite sospese da riprendere. La stessa partita importata due volte si sovrascrive.
     */
    fun importMatches(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val s = strings
        scope.launch {
            val (done, open) = withContext(io) {
                var done = 0
                var open = 0
                for (uri in uris) {
                    val rec = runCatching {
                        val text = app.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: return@runCatching null
                        storage.json.decodeFromString(MatchRecord.serializer(), text)
                    }.getOrNull() ?: continue
                    val state = runCatching { ScoreEngine.replay(rec.rules, rec.events) }.getOrNull() ?: continue
                    if (state.isFinished) {
                        storage.saveFinished(rec.copy(finished = true, suspended = false))
                        done++
                    } else {
                        storage.saveMatch(rec.copy(finished = false, suspended = true, updatedAt = System.currentTimeMillis()))
                        open++
                    }
                }
                done to open
            }
            _toasts.tryEmit(if (done + open == 0) s.historyImportFailed else s.historyImported(done, open))
            refreshHistory()
            refreshSaved()
        }
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
            if (!options.value.addressLookup) return@launch
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
                scope.launch(io) {
                    storage.saveFinished(rec)
                    storage.saveLastFinished(rec)
                }
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

    /** "Salva file": testo, JSON o immagine con nome e cartella scelti dall'utente. */
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
            _toasts.tryEmit(if (where != null) s.savedTo(where) else s.saveError)
        }
    }

    private fun withSuffix(file: String, suffix: String) =
        if (suffix.isEmpty()) file else file.substringBeforeLast('.') + suffix + "." + file.substringAfterLast('.')

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
            putExtra(Intent.EXTRA_SUBJECT, if (sm.record.setup.sport == Sport.PADEL) s.shareSubjectPadel else s.shareSubject)
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
     * Prima Activity del processo: se il processo si era chiuso sul riepilogo lo si riapre, altrimenti andrebbe perso.
     * Vale sia quando Android lo chiude in background sia dopo un aggiornamento dell'app, un crash o un riavvio del
     * telefono. Chi esce di proposito ("Nuova partita", "Esci", app tolta dalle recenti) cancella [Storage.summaryOpen]:
     * allora si riparte dalla prima schermata.
     */
    fun onActivityCreated() {
        if (restoreChecked) return
        restoreChecked = true
        if (live.value != null || summary.value != null || screen.value != Screen.SETUP) return
        val id = storage.summaryOpen ?: return
        val rec = storage.loadLastFinished()?.takeIf { it.id == id } ?: return
        summary.value = LiveMatch(rec, ScoreEngine.replay(rec.rules, rec.events))
        summaryFromHistory.value = false
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
