// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager

import android.app.Activity
import android.app.Application
import android.content.ClipData
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Base64
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
import com.tennis.scoremanager.ble.HeartMeasurement
import com.tennis.scoremanager.ble.HeartRateManager
import com.tennis.scoremanager.ble.LinkState
import com.tennis.scoremanager.ble.StrapGaps
import com.tennis.scoremanager.ble.StrapInfo
import com.tennis.scoremanager.ble.StrapState
import com.tennis.scoremanager.data.HeartBucket
import com.tennis.scoremanager.data.HeartProfile
import com.tennis.scoremanager.data.HeartStats
import com.tennis.scoremanager.data.HeartTrack
import com.tennis.scoremanager.data.LocationHelper
import com.tennis.scoremanager.data.MatchLocation
import com.tennis.scoremanager.data.MatchOptions
import com.tennis.scoremanager.data.MatchRecord
import com.tennis.scoremanager.data.Names
import com.tennis.scoremanager.data.PdfReport
import com.tennis.scoremanager.data.PlayMode
import com.tennis.scoremanager.data.PlayerStats
import com.tennis.scoremanager.data.Reports
import com.tennis.scoremanager.data.SetupData
import com.tennis.scoremanager.data.Sport
import com.tennis.scoremanager.data.Storage
import com.tennis.scoremanager.data.StrokeStats
import com.tennis.scoremanager.data.Tournament
import com.tennis.scoremanager.data.TournamentKind
import com.tennis.scoremanager.data.TournamentScoring
import com.tennis.scoremanager.data.Tournaments
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
import com.tennis.scoremanager.relay.PhoneRelay
import com.tennis.scoremanager.workout.WorkoutSession
import com.tennis.scoremanager.video.PointMark
import com.tennis.scoremanager.video.PointMarks
import com.tennis.scoremanager.video.ScoreOverlay
import com.tennis.scoremanager.data.WorkoutRecord
import com.tennis.scoremanager.data.Progress
import com.tennis.scoremanager.data.ProgressPdf
import com.tennis.scoremanager.data.WorkoutReport
import com.tennis.scoremanager.data.WorkoutStats
import com.tennis.scoremanager.relay.RelayRole
import com.tennis.scoremanager.transfer.TransferBundle
import com.tennis.scoremanager.transfer.TransferServer
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
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json

enum class Screen { SETUP, OPTIONS, START, MATCH, SUMMARY, HISTORY, TOURNAMENT, WORKOUT, PROGRESS, VIDEOS }

/** Domanda sul punto appena segnato: come è finito (ace, vincente, errore) oppure, per un errore, di che tipo. */
enum class DetailAsk { OUTCOME, ERROR_TYPE }

/** [winner] ha vinto il punto, [server] serviva. */
data class DetailPrompt(val ask: DetailAsk, val winner: Side, val server: Side)

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
    val heart: HeartRateManager,
    val voice: VoicePack,
    val announcer: Announcer,
) {
    companion object {
        const val SHOT_CLOCK_S = 25          // ITF: 25" tra un punto e l'altro
        const val CHANGEOVER_S = 90          // ITF: 90" al cambio campo
        const val SET_BREAK_S = 120          // ITF: 120" a fine set
        const val WALK_S = 30                // pausa per spostarsi (dopo il 1° game, nel tie-break, sul 6-6)
        /** Lato delle foto dei giocatori salvate (pixel). */
        const val PHOTO_PX = 320
        /** Cartella predefinita di "Salva file" da Android 10 (memoria condivisa, visibile a tutte le app). */
        val DOCUMENTS_DIR = "${Environment.DIRECTORY_DOCUMENTS}/Tennis Score Manager"
        /** Lato lungo delle foto ricordo di una partita (pixel): basta per il PDF, circa 200-400 kB l'una. */
        const val MATCH_PHOTO_PX = 1600
        private const val BAND_GAP_MS = 2_000L
        private const val TAP_GAP_MS = 700L
        /** "Annulla punto" dal telefono: un doppio tocco toglierebbe due punti. */
        private const val UNDO_GAP_MS = 1_000L
        /** Il riepilogo si riprende la posizione solo poco dopo la fine (dopo si è altrove). */
        private const val LATE_LOCATION_MS = 30 * 60_000L
        private const val MESSAGE_MS = 5_000L
        /** Quanto resta la domanda sul punto appena segnato (statistiche dettagliate). */
        private const val DETAIL_PROMPT_MS = 6_000L
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
    /** Statistiche dettagliate: domanda facoltativa sul punto appena segnato (sparisce da sola dopo qualche secondo). */
    val detailPrompt = MutableStateFlow<DetailPrompt?>(null)
    private var detailJob: Job? = null
    val saved = MutableStateFlow<List<MatchRecord>>(emptyList())
    /**
     * Cresce a ogni foto cambiata: chi mostra le foto le ricarica. Va dichiarata qui, prima del blocco init:
     * col tabellone acceso, all'avvio publishTv la legge subito.
     */
    val photosVersion = MutableStateFlow(0)
    /** Storico: partite finite salvate sul telefono, dalla più recente. */
    val history = MutableStateFlow<List<LiveMatch>>(emptyList())
    /** Il riepilogo a schermo è stato aperto dallo storico: in fondo "Indietro" ed "Elimina" invece di nuova partita/esci. */
    val summaryFromHistory = MutableStateFlow(false)
    val voiceProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    /** File generati dal TTS e registrazioni personalizzate presenti per la lingua corrente. */
    val voiceCount = MutableStateFlow(0)
    val customVoiceCount = MutableStateFlow(0)
    val bandBattery = MutableStateFlow<Map<Side, BandBattery>>(emptyMap())
    /** Chi indossa la fascia cardio di ciascun lato (per le calorie). */
    val heartProfiles = MutableStateFlow(Side.entries.mapNotNull { side -> storage.heartProfile(side == Side.P1)?.let { side to it } }.toMap())
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
    /** Misure delle fasce arrivate negli ultimi [HeartStats.STEP_S] secondi, per lato. */
    private val heartBuckets = Side.entries.associateWith { HeartBucket() }
    /**
     * Stacchi di ciascuna fascia (per indirizzo), con le misure di questo telefono e del satellite insieme: conta
     * quando il battito non è arrivato da nessuno dei due. Ripartono a ogni partita.
     */
    private val strapGaps = mutableMapOf<String, StrapGaps>()
    /** Batte ogni 2 secondi: uno stacco in corso si vede crescere anche senza misure nuove. */
    private val gapTick = MutableStateFlow(0L)

    /** Misure arrivate in ritardo dal telefono satellite: (lato, secondo della partita, battito). */
    private val lateHeart = mutableListOf<Triple<Side, Int, Int>>()

    /** Due telefoni per le fasce cardio: principale o satellite (vedi [PhoneRelay]). */
    val relay: PhoneRelay = PhoneRelay(
        app, scope, heart, ble,
        matchIds = live.map { it?.record?.id.orEmpty() },
        tvPorts = tvServer.port,
        onRemoteReading = { addr, m, at, match -> onRemoteHeart(addr, m, at, match) },
        onSatelliteStopped = {
            heart.disconnectAll()
            ble.disconnectAll()
            if (options.value.heartRate) restoreStraps()
            if (options.value.mode == PlayMode.BANDS) restoreBands()
            if (!needsService()) MatchService.stop(app)
        },
    )

    /** Allenamento con la fascia, senza partita (schermata a parte, pulsante in pagina 1). */
    val workout = WorkoutSession(scope) { rec -> onWorkoutFinished(rec) }
    /** Allenamenti salvati, dal più recente. */
    val workouts = MutableStateFlow<List<WorkoutRecord>>(emptyList())
    /** Allenamento aperto nel riepilogo (null = schermata dell'allenamento in corso e dell'elenco). */
    val workoutDetail = MutableStateFlow<WorkoutRecord?>(null)
    private var workoutSide = Side.P1

    /**
     * Fasce come le mostrano le schermate: quelle di questo telefono e, se questo non le sente, il battito che
     * arriva dal satellite.
     */
    val straps: StateFlow<Map<Side, StrapInfo>> = combine(heart.straps, relay.remote, gapTick) { local, remote, _ ->
        val now = SystemClock.elapsedRealtime()
        local.mapValues { (_, st) ->
            val r = remote[st.address]?.status
            val localOk = st.state == StrapState.READY && st.bpm != null
            val shown = if (!localOk && r != null && r.ready && r.bpm != null) {
                st.copy(state = StrapState.READY, bpm = r.bpm, contact = null, rssi = r.rssi, relayed = true)
            } else {
                st
            }
            shown.copy(gaps = strapGaps[st.address]?.stats(now))
        }
    }.stateIn(scope, SharingStarted.Eagerly, emptyMap())
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
        if (options.value.heartRate) restoreStraps()
        remotes.enabled = options.value.mode == PlayMode.REMOTES
        remotes.onGesture = { side, g -> onRemoteGesture(side, g) }
        refreshSaved()
        // Sul telefono satellite i braccialetti li governa il principale: qui si girano soltanto (vedi PhoneRelay).
        scope.launch { ble.events.collect { if (!relay.isSatellite) onBandEvent(it) } }
        scope.launch { ble.ready.collect { if (!relay.isSatellite) onBandReady(it) } }
        scope.launch { ble.found.collect { if (!relay.isSatellite) autoFill(it) } }
        scope.launch { ble.status.collect { (side, st) -> if (!relay.isSatellite) onBandStatus(side, st) } }
        scope.launch { ble.strokes.collect { if (!relay.isSatellite) onBandStroke(it) } }
        scope.launch {
            heart.readings.collect { (side, m) ->
                heart.straps.value[side]?.address?.let { strapGap(it, SystemClock.elapsedRealtime()) }
                workout.onReading(side, m)
                onHeartReading(side, m)
                relay.onLocalReading(side, m)
            }
        }
        scope.launch {
            while (true) {
                delay(HeartStats.STEP_S * 1000L)
                flushHeart()
            }
        }
        scope.launch {
            heart.straps.collect { map ->
                // Una fascia tolta non conta più: se torna, si riparte da zero.
                val inUse = map.values.map { it.address }.toSet()
                strapGaps.keys.retainAll(inUse)
            }
        }
        scope.launch {
            while (true) {
                delay(2_000)
                if (strapGaps.isNotEmpty()) gapTick.value = SystemClock.elapsedRealtime()
            }
        }
        scope.launch { watchBands() }
        scope.launch {
            // Telefono-telecamera: punteggio per la grafica a ogni cambiamento e un segno a ogni punto (capitoli).
            var lastPoints = -1
            var before: MatchState? = null
            live.collect { lm ->
                if (lm == null) {
                    relay.publishScore(null)
                    lastPoints = -1
                    before = null
                    return@collect
                }
                relay.publishScore(scoreOverlay(lm))
                val played = lm.state.pointsPlayed
                if (lastPoints in 0 until played && lm.record.startedAt != null) {
                    val p = lm.record.events.lastOrNull { it is MatchEvent.Point } as? MatchEvent.Point
                    val (set, game) = PointMarks.place(lm.state)
                    relay.publishPoint(
                        PointMark(
                            System.currentTimeMillis(), PointMarks.score(lm.state), p?.winner?.ordinal ?: -1, p?.kind?.name, set, game,
                            p?.secondServe == true, before?.let(PointMarks::pressure), p?.strokes, before?.server?.ordinal ?: -1,
                        ),
                    )
                }
                lastPoints = played
                before = lm.state
            }
        }
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
            // Il server del tabellone serve anche al telefono satellite, che lo mostra all'altro fondo campo.
            combine(tv.map { it.enabled }, relay.ui.map { it.role == RelayRole.MAIN }) { a, b -> a || b }
                .distinctUntilChanged()
                .collect { on ->
                    if (on) tvServer.start() else tvServer.stop()
                    publishTv()
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
    private fun needsService(): Boolean =
        options.value.mode == PlayMode.BANDS || tv.value.enabled || options.value.heartRate && heart.straps.value.isNotEmpty() ||
            relay.ui.value.role != RelayRole.OFF || workout.active

    /** Foto dei giocatori per il tabellone: "/photo/<file>?v=<versione>", così il tabellone ricarica quelle cambiate. */
    private fun tvPhotos(su: SetupData): Map<Side, List<String>> = Side.entries.associateWith { side ->
        PlayerStats.names(su, side).mapNotNull { n ->
            storage.photoFile(PlayerStats.key(n)).takeIf { it.exists() }?.let { "/photo/${it.name}?v=${photosVersion.value}" }
        }
    }

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
                photos = tvPhotos(match?.record?.setup ?: setup.value),
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
        if (old.heartRate != v.heartRate) {
            if (v.heartRate) {
                restoreStraps()
            } else {
                relay.stop()
                heart.disconnectAll()
            }
        }
    }

    fun go(to: Screen) {
        if (to == Screen.START) refreshSaved()
        if (to == Screen.OPTIONS && options.value.mode == PlayMode.BANDS) ble.reconnectAll()
        if (to == Screen.OPTIONS && options.value.heartRate) heart.reconnectAll()
        // Il servizio in primo piano parte ora che l'app è visibile: avviarlo dopo, da un KEY1 a schermo
        // bloccato, Android lo vieterebbe e la partita resterebbe senza protezione in background.
        if (to == Screen.START && needsService()) MatchService.start(app)
        screen.value = to
    }

    fun back() {
        screen.value = when (screen.value) {
            Screen.OPTIONS -> Screen.SETUP
            Screen.PROGRESS -> Screen.SETUP
            Screen.VIDEOS -> Screen.SETUP
            // L'allenamento in corso continua anche uscendo dalla sua schermata (si ritrova riaprendola).
            Screen.WORKOUT -> if (workoutDetail.value != null) {
                workoutDetail.value = null
                Screen.WORKOUT
            } else {
                Screen.SETUP
            }
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
            if (relay.isSatellite) return@collect
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

    // ---------------------------------------------------------------- fasce cardio

    private fun restoreStraps() {
        storage.strapAddress(true)?.let { heart.assign(Side.P1, it, storage.strapName(true)) }
        storage.strapAddress(false)?.let { heart.assign(Side.P2, it, storage.strapName(false)) }
    }

    fun assignStrap(side: Side, address: String?, name: String?) {
        if (address != null && storage.strapAddress(side == Side.P2) == address) storage.setStrap(side == Side.P2, null, null)
        storage.setStrap(side == Side.P1, address, name)
        heart.assign(side, address, name)
    }

    /** Ricerca delle fasce mentre la loro sezione è aperta (e l'app in primo piano). */
    fun setStrapScan(on: Boolean) = heart.setAutoScan(on && options.value.heartRate)

    fun swapStraps() {
        val p1 = storage.strapAddress(true) to storage.strapName(true)
        val p2 = storage.strapAddress(false) to storage.strapName(false)
        storage.setStrap(true, p2.first, p2.second)
        storage.setStrap(false, p1.first, p1.second)
        heart.swapSides()
        val prof = heartProfiles.value
        setHeartProfile(Side.P1, prof[Side.P2])
        setHeartProfile(Side.P2, prof[Side.P1])
    }

    fun setHeartProfile(side: Side, p: HeartProfile?) {
        storage.setHeartProfile(side == Side.P1, p)
        heartProfiles.value = if (p == null) heartProfiles.value - side else heartProfiles.value + (side to p)
    }

    /** Il battito conta come i colpi: a partita avviata e in corso, non nelle pause. */
    private fun recordingHeart(lm: LiveMatch): Boolean =
        screen.value == Screen.MATCH && lm.record.startedAt != null && !lm.record.suspended && !lm.state.isFinished

    private fun onHeartReading(side: Side, m: HeartMeasurement) {
        val lm = live.value ?: return
        if (m.valid && recordingHeart(lm)) heartBuckets.getValue(side).add(m.bpm)
    }

    /**
     * Misura girata dal telefono satellite. Se è appena presa vale come quelle di questo telefono; se arriva in
     * ritardo (il Wi-Fi tra i telefoni era caduto) va a riempire i buchi della partita a cui appartiene.
     */
    private fun onRemoteHeart(addr: String, m: HeartMeasurement, at: Long, match: String) {
        val side = heart.straps.value.entries.firstOrNull { it.value.address == addr }?.key ?: return
        val age = System.currentTimeMillis() - at
        // Per gli stacchi conta l'ora della misura, riportata sull'orologio di questo telefono.
        if (age <= PhoneRelay.LIVE_MS) {
            strapGap(addr, SystemClock.elapsedRealtime() - age.coerceAtLeast(0))
            workout.onReading(side, m)
        }
        val lm = live.value ?: return
        if (match != lm.record.id || !m.valid) return
        if (System.currentTimeMillis() - at <= PhoneRelay.LIVE_MS) {
            onHeartReading(side, m)
        } else {
            val start = lm.record.startedAt ?: return
            if (at >= start && !lm.state.isFinished) lateHeart += Triple(side, ((at - start) / 1000).toInt(), m.bpm)
        }
    }

    private fun strapGap(addr: String, at: Long) {
        strapGaps.getOrPut(addr) { StrapGaps() }.onReading(at)
    }

    /** La grafica del punteggio per la telecamera, con la scritta di pressione (palla break, set point...). */
    private fun scoreOverlay(lm: LiveMatch): ScoreOverlay {
        val n = names(lm.record.setup)
        val title = listOf(lm.record.setup.club, lm.record.setup.court).filter { it.isNotBlank() }.joinToString(" · ")
        val banner = if (lm.state.isFinished) null else pressureMessage(lm.state)
        return ScoreOverlay.from(lm.state, Side.entries.map { n.short(it) }, banner, title)
    }

    /** Ogni [HeartStats.STEP_S] secondi la media delle misure entra nella partita, con quelle arrivate in ritardo. */
    private fun flushHeart() {
        val values = heartBuckets.mapValues { it.value.take() }.filterValues { it != null }
        val late = lateHeart.groupBy({ it.first }, { it.second to it.third })
        lateHeart.clear()
        val lm = live.value ?: return
        val start = lm.record.startedAt ?: return
        val now = values.takeIf { recordingHeart(lm) }.orEmpty()
        if (now.isEmpty() && late.isEmpty()) return
        val sec = ((System.currentTimeMillis() - start) / 1000).toInt()
        var heartMap = lm.record.heart
        for (side in Side.entries) {
            val bpm = now[side]
            val points = late[side].orEmpty()
            if (bpm == null && points.isEmpty()) continue
            var track = heartMap[side] ?: HeartTrack()
            if (bpm != null) track = track.add(sec, bpm)
            track = track.fill(points, HeartStats.STEP_S)
            heartMap = heartMap + (side to track.copy(profile = track.profile ?: heartProfiles.value[side]))
        }
        setRecord(lm.record.copy(heart = heartMap))
    }

    // ---------------------------------------------------------------- due telefoni

    /**
     * Questo telefono diventa il principale: con [useHotspot] (permesso concesso) crea una rete sua, altrimenti usa
     * il Wi-Fi in cui si trova. Se non c'è nessuna rete lo dice.
     */
    fun startRelayMain(useHotspot: Boolean) {
        scope.launch {
            if (relay.startMain(useHotspot)) MatchService.start(app) else _toasts.tryEmit(strings.transferNoNetwork)
        }
    }

    /** Testo del QR del principale: questo telefono diventa satellite (con [camera], telefono-telecamera). */
    fun joinRelay(text: String, camera: Boolean = false) {
        if (!relay.startSatellite(text, camera)) {
            _toasts.tryEmit(strings.transferBadCode)
            return
        }
        MatchService.start(app)
    }

    fun stopRelay() {
        relay.stop()
        if (!needsService()) MatchService.stop(app)
    }

    // ---------------------------------------------------------------- partita

    /** Regole di pagina 2 (formato, set, tie-break, No-Ad...) con chi serve per primo: anche per i video importati. */
    fun rulesFromOptions(firstServer: Side): RulesConfig {
        val o = options.value
        val su = setup.value
        return RulesConfig(
            format = o.format, gamesPerSet = o.gamesPerSet, setTiebreak = o.setTiebreak, noAd = o.noAd,
            starPoint = su.sport == Sport.PADEL && o.starPoint && !o.noAd, doubles = su.doubles, firstServer = firstServer,
        )
    }

    fun startMatch() {
        if (screen.value != Screen.START) return
        val o = options.value
        val su = setup.value
        val rules = RulesConfig(
            format = o.format,
            gamesPerSet = o.gamesPerSet,
            setTiebreak = o.setTiebreak,
            noAd = o.noAd,
            starPoint = su.sport == Sport.PADEL && o.starPoint && !o.noAd,
            doubles = su.doubles,
            firstServer = o.firstServer,
            p1StartsLeft = o.p1Left,
            firstServerP1 = if (su.doubles) o.firstServerP1 else 0,
            firstServerP2 = if (su.doubles) o.firstServerP2 else 0,
        )
        val now = System.currentTimeMillis()
        // Il suffisso casuale rende l'ID unico anche tra telefoni diversi (il satellite lo mette su ogni misura).
        val suffix = ByteArray(3).also { java.security.SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        val rec = MatchRecord(id = "m$now-$suffix", setup = su, options = o, rules = rules, updatedAt = now)
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
        // Gli stacchi contano dal "gioco": quelli del riscaldamento non dicono niente della partita.
        strapGaps.values.forEach { it.reset() }
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
        val server = current.state.server
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
        // Statistiche dettagliate: un punto dal braccialetto o dal telecomando arriva senza dettaglio, e un
        // "Errore" senza dire di che tipo; si chiede sul telefono, facoltativo.
        askDetail(
            when {
                !current.record.options.detailedStats || step.state.isFinished -> null
                fromBand && kind == null -> DetailPrompt(DetailAsk.OUTCOME, side, server)
                kind == PointKind.ERROR -> DetailPrompt(DetailAsk.ERROR_TYPE, side, server)
                else -> null
            },
        )
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

    private fun askDetail(p: DetailPrompt?) {
        detailJob?.cancel()
        detailPrompt.value = p
        if (p != null) detailJob = scope.launch {
            delay(DETAIL_PROMPT_MS)
            detailPrompt.value = null
        }
    }

    /**
     * Risposta alla domanda sul punto appena segnato: cambia solo il "come" dell'ultimo punto, non chi l'ha vinto,
     * quindi il punteggio resta uguale. Scegliendo "Errore" si chiede ancora se gratuito o forzato.
     */
    fun setPointDetail(kind: PointKind?) {
        val lm = live.value ?: return
        val last = lm.record.events.lastOrNull() as? MatchEvent.Point
        val prompt = detailPrompt.value
        if (last == null || prompt == null || kind == null) {
            askDetail(null)
            return
        }
        val rec = lm.record.copy(events = lm.record.events.dropLast(1) + last.copy(kind = kind))
        commit(rec, lm.state)
        askDetail(if (kind == PointKind.ERROR) prompt.copy(ask = DetailAsk.ERROR_TYPE) else null)
    }

    /** Prima di servizio fuori; la seconda fuori è doppio fallo, cioè punto a chi risponde. */
    fun fault() {
        val lm = live.value ?: return
        if (closing || lm.record.suspended || lm.state.isFinished || endDialog.value) return
        askDetail(null)
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
        askDetail(null)
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
            heartBuckets.values.forEach { it.clear() }
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
        // Età e peso detti a partita iniziata valgono lo stesso per le calorie.
        val heartMap = lm.record.heart.mapValues { (side, t) -> t.copy(profile = t.profile ?: heartProfiles.value[side]) }
        val rec = lm.record.copy(
            finished = true, suspended = false, clockMs = currentClock(), updatedAt = System.currentTimeMillis(),
            batteryEnd = endBattery, heart = heartMap,
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

    // ---------------------------------------------------------------- progressi

    /** Pagina 1 › Progressi: documenti stampabili, uno per gli allenamenti e uno per le partite di un giocatore. */
    fun openProgress() {
        refreshHistory()
        refreshWorkouts()
        screen.value = Screen.PROGRESS
    }

    private fun played() = history.value.map { PlayerStats.Played(it.record, it.state.winner) }

    /** Giocatori dello storico (chiave, nome), per scegliere di chi fare il documento delle partite. */
    fun progressPlayers(): List<Pair<String, String>> = PlayerStats.players(played()).map { it.key to it.name }

    /** [playerKey] null = allenamenti. */
    fun progressReport(playerKey: String?): Progress.Report {
        val s = strings
        val lang = options.value.lang
        // Data corta: nelle colonne del documento quella lunga ("martedì 20 ottobre 2026") non ci sta.
        val fmt = java.text.SimpleDateFormat("dd/MM/yy", lang.locale)
        val date: (Long) -> String = { fmt.format(java.util.Date(it)) }
        return if (playerKey == null) {
            Progress.workouts(workouts.value, s, date)
        } else {
            val name = PlayerStats.players(played()).firstOrNull { it.key == playerKey }?.name ?: playerKey
            Progress.matches(PlayerStats.lines(played(), playerKey), name, s, date)
        }
    }

    /** Il PDF dei progressi nella cartella di condivisione; null se non si è potuto scrivere. */
    fun progressPdf(playerKey: String?): File? {
        val r = progressReport(playerKey)
        val s = strings
        val dir = File(app.cacheDir, "share").apply { mkdirs() }
        val name = (if (playerKey == null) s.progressWorkouts else "${s.progressMatches} ${r.title.substringAfter(": ")}")
            .replace(Regex("[\\/:*?\"<>|]"), "_")
        val file = File(dir, "$name.pdf")
        return runCatching {
            file.outputStream().use { ProgressPdf.write(r, s, "Tennis Score Manager · ${Reports.date(System.currentTimeMillis(), options.value.lang)}", it) }
            file
        }.getOrNull()
    }

    fun shareFileIntent(file: File, mime: String): Intent {
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.nameWithoutExtension)
            clipData = ClipData.newRawUri("", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, strings.share)
    }

    // ---------------------------------------------------------------- allenamento

    fun openWorkouts() {
        workoutDetail.value = null
        refreshWorkouts()
        screen.value = Screen.WORKOUT
    }

    private fun refreshWorkouts() {
        scope.launch { workouts.value = withContext(io) { storage.loadWorkouts() } }
    }

    /** La fascia dell'allenamento: quella del Giocatore 1 o l'unica scelta. false se non ce n'è nessuna. */
    fun startWorkout(): Boolean {
        val straps = heart.straps.value
        val side = Side.entries.firstOrNull { straps[it] != null } ?: return false
        workoutSide = side
        workout.start(side, straps[side]?.name)
        MatchService.start(app)
        return true
    }

    private fun onWorkoutFinished(rec: WorkoutRecord) {
        val w = rec.copy(heart = rec.heart.copy(profile = heartProfiles.value[workoutSide]))
        scope.launch {
            withContext(io) { storage.saveWorkout(w) }
            refreshWorkouts()
        }
        workoutDetail.value = w
        if (!needsService()) MatchService.stop(app)
    }

    fun deleteWorkout(id: String) {
        scope.launch {
            withContext(io) { storage.deleteWorkout(id) }
            workoutDetail.value = null
            refreshWorkouts()
        }
    }

    /** Condivide l'allenamento come CSV (secondo per secondo) o PDF di riepilogo. */
    fun workoutShareIntent(w: WorkoutRecord, pdf: Boolean): Intent? {
        val s = strings
        val dir = File(app.cacheDir, "share").apply { mkdirs() }
        val stamp = java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm", java.util.Locale.ROOT).format(java.util.Date(w.startedAt))
        val file = File(dir, "${s.workoutTitle}_$stamp.${if (pdf) "pdf" else "csv"}")
        runCatching {
            file.outputStream().use { o ->
                if (pdf) WorkoutReport.pdf(w, s, options.value.lang, o) else o.write(WorkoutStats.csv(w).toByteArray())
            }
        }.onFailure { return null }
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = if (pdf) "application/pdf" else "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "${s.workoutTitle} ${Reports.date(w.startedAt, options.value.lang)}")
            clipData = ClipData.newRawUri("", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, s.share)
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
                storage.deleteMatchPhotos(id)
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
                importRecords(uris.mapNotNull { uri ->
                    runCatching {
                        val text = app.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: return@runCatching null
                        storage.json.decodeFromString(MatchRecord.serializer(), text)
                    }.getOrNull()
                })
            }
            _toasts.tryEmit(if (done + open == 0) s.historyImportFailed else s.historyImported(done, open))
            refreshHistory()
            refreshSaved()
        }
    }

    /** Salva partite arrivate da fuori: le finite nello storico, le altre tra le sospese. Restituisce (finite, sospese). */
    private fun importRecords(recs: List<MatchRecord>): Pair<Int, Int> {
        var done = 0
        var open = 0
        for (rec in recs) {
            val state = runCatching { ScoreEngine.replay(rec.rules, rec.events) }.getOrNull() ?: continue
            if (state.isFinished) {
                storage.saveFinished(rec.copy(finished = true, suspended = false))
                done++
            } else {
                storage.saveMatch(rec.copy(finished = false, suspended = true, updatedAt = System.currentTimeMillis()))
                open++
            }
        }
        return done to open
    }

    // ---------------------------------------------------------------- tornei Americano e Mexicano

    /** Tutti i tornei salvati, dal più recente. */
    val tournaments = MutableStateFlow<List<Tournament>>(emptyList())
    /** Torneo aperto; null = elenco dei tornei e creazione di uno nuovo. */
    val tournament = MutableStateFlow<Tournament?>(null)

    fun openTournaments() {
        refreshTournaments()
        // Si riapre da solo l'ultimo torneo non concluso.
        if (tournament.value == null) {
            scope.launch {
                withContext(io) { storage.loadTournaments() }.firstOrNull { !it.finished }?.let { tournament.value = it }
            }
        }
        screen.value = Screen.TOURNAMENT
    }

    private fun refreshTournaments() {
        scope.launch { tournaments.value = withContext(io) { storage.loadTournaments() } }
    }

    private fun saveTournament(t: Tournament) {
        tournament.value = t
        scope.launch {
            withContext(io) { storage.saveTournament(t) }
            refreshTournaments()
        }
    }

    fun createTournament(
        name: String, kind: TournamentKind, players: List<String>, courts: Int,
        scoring: TournamentScoring, pointsPerMatch: Int, minutes: Int,
    ) {
        val now = System.currentTimeMillis()
        val t = Tournament(
            id = "t$now", name = name.trim(), kind = kind, players = players.map { it.trim() },
            courts = courts, scoring = scoring, pointsPerMatch = pointsPerMatch, minutes = minutes, createdAt = now,
        )
        saveTournament(t.copy(rounds = listOf(Tournaments.nextRound(t))))
    }

    fun openTournament(t: Tournament) {
        tournament.value = t
    }

    /** Torna all'elenco dei tornei (quello aperto resta salvato). */
    fun closeTournament() {
        tournament.value = null
        refreshTournaments()
    }

    /** Risultato di una partita del turno [round]; null = ancora da giocare. */
    fun setTournamentResult(round: Int, court: Int, a: Int?, b: Int?) {
        val t = tournament.value ?: return
        val rounds = t.rounds.map { r ->
            if (r.number != round) r
            else r.copy(matches = r.matches.map { if (it.court == court) it.copy(scoreA = a, scoreB = b) else it })
        }
        saveTournament(t.copy(rounds = rounds))
    }

    /** Turno successivo, solo se tutte le partite del turno in corso hanno il risultato. */
    fun nextTournamentRound(): Boolean {
        val t = tournament.value ?: return false
        if (t.current?.complete == false) return false
        saveTournament(t.copy(rounds = t.rounds + Tournaments.nextRound(t)))
        return true
    }

    /** Conclude il torneo. L'ultimo turno, se non è stato giocato per niente, si toglie. */
    fun finishTournament() {
        val t = tournament.value ?: return
        val rounds = t.rounds.filterIndexed { i, r -> i < t.rounds.size - 1 || r.matches.any { it.played } }
        saveTournament(t.copy(rounds = rounds, finished = true))
    }

    fun deleteTournament(id: String) {
        if (tournament.value?.id == id) tournament.value = null
        scope.launch {
            withContext(io) { storage.deleteTournament(id) }
            refreshTournaments()
        }
    }

    /** La voce dice il punteggio di una partita contata punto per punto (se la voce è accesa). */
    fun announceCounter(a: Int, b: Int) {
        if (options.value.audio) announcer.announce(calls().counterScore(a, b))
    }

    /** Classifica del torneo come testo da condividere. */
    fun tournamentShareIntent(): Intent? {
        val t = tournament.value ?: return null
        val s = strings
        val kind = if (t.kind == TournamentKind.AMERICANO) s.tournamentAmericano else s.tournamentMexicano
        val text = buildString {
            appendLine(listOf(t.name.ifBlank { kind }, kind).distinct().joinToString(" · "))
            appendLine(s.tournamentRoundsPlayed(t.rounds.count { r -> r.matches.any { it.played } }))
            Tournaments.standings(t).forEachIndexed { i, st ->
                appendLine("${i + 1}. ${st.name} – ${st.points} (${s.tournamentWonShort} ${st.won}, ${st.diff.let { if (it > 0) "+$it" else "$it" }})")
            }
            append("Tennis Score Manager · ${Reports.siteUrl(options.value.lang)}")
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        return Intent.createChooser(send, s.tournamentShare)
    }

    // ---------------------------------------------------------------- foto dei giocatori

    /** Foto del giocatore col nome [name], ritagliata quadrata e rimpicciolita; null se non c'è. */
    fun loadPhoto(name: String): Bitmap? {
        val key = PlayerStats.key(name)
        if (key.isEmpty()) return null
        val f = storage.photoFile(key)
        return if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null
    }

    /** Foto scelta dalla galleria o scattata: si salva quadrata (centro dell'immagine) a [PHOTO_PX] pixel. */
    fun setPhoto(name: String, uri: Uri) {
        val key = PlayerStats.key(name)
        if (key.isEmpty()) return
        scope.launch {
            val ok = withContext(io) {
                runCatching {
                    // ImageDecoder (Android 9+) raddrizza le foto girate (EXIF); prima si legge com'è.
                    val src = if (Build.VERSION.SDK_INT >= 28) {
                        ImageDecoder.decodeBitmap(ImageDecoder.createSource(app.contentResolver, uri)) { d, info, _ ->
                            val side = minOf(info.size.width, info.size.height)
                            d.setTargetSampleSize((side / (PHOTO_PX * 2)).coerceAtLeast(1))
                            d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        }
                    } else {
                        app.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: error("foto illeggibile")
                    }
                    val side = minOf(src.width, src.height)
                    val square = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
                    val small = Bitmap.createScaledBitmap(square, PHOTO_PX, PHOTO_PX, true)
                    val out = ByteArrayOutputStream()
                    small.compress(Bitmap.CompressFormat.JPEG, 85, out)
                    storage.savePhoto(key, out.toByteArray())
                }.isSuccess
            }
            if (ok) photosVersion.value++ else _toasts.tryEmit(strings.photoError)
            publishTv()
        }
    }

    fun removePhoto(name: String) {
        val key = PlayerStats.key(name)
        if (key.isEmpty()) return
        scope.launch {
            withContext(io) { storage.deletePhoto(key) }
            photosVersion.value++
            publishTv()
        }
    }

    // ---------------------------------------------------------------- foto ricordo della partita

    /** Cresce a ogni foto della partita aggiunta o tolta. */
    val matchPhotosVersion = MutableStateFlow(0)

    fun matchPhotos(id: String): List<File> = storage.matchPhotos(id)

    /** Foto aggiunte al riepilogo a schermo: rimpicciolite a [MATCH_PHOTO_PX] sul lato lungo, fino a 10 per partita. */
    fun addMatchPhotos(uris: List<Uri>) {
        val id = summary.value?.record?.id ?: return
        val s = strings
        scope.launch {
            val (added, full) = withContext(io) {
                var added = 0
                var full = false
                for (uri in uris) {
                    if (storage.matchPhotos(id).size >= Storage.MAX_MATCH_PHOTOS) { full = true; break }
                    val jpeg = runCatching { scaledJpeg(uri, MATCH_PHOTO_PX) }.getOrNull() ?: continue
                    if (storage.addMatchPhoto(id, jpeg)) added++
                }
                added to full
            }
            matchPhotosVersion.value++
            if (full) _toasts.tryEmit(s.matchPhotosFull)
            else if (added == 0) _toasts.tryEmit(s.photoError)
        }
    }

    fun removeMatchPhoto(file: File) {
        scope.launch {
            withContext(io) { file.delete() }
            matchPhotosVersion.value++
        }
    }

    /** Foto [uri] rimpicciolita (lato lungo [maxSide]) in JPEG; ImageDecoder (Android 9+) la raddrizza. */
    private fun scaledJpeg(uri: Uri, maxSide: Int): ByteArray {
        val src = if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(app.contentResolver, uri)) { d, info, _ ->
                val long = maxOf(info.size.width, info.size.height)
                d.setTargetSampleSize((long / maxSide).coerceAtLeast(1))
                d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            app.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: error("foto illeggibile")
        }
        val scale = minOf(1f, maxSide.toFloat() / maxOf(src.width, src.height))
        val img = if (scale < 1f) Bitmap.createScaledBitmap(src, (src.width * scale).toInt(), (src.height * scale).toInt(), true) else src
        return ByteArrayOutputStream().also { img.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
    }

    /** File dove la fotocamera salva la foto da aggiungere al riepilogo (nella cache condivisibile). */
    fun cameraUri(): Uri {
        val f = File(File(app.cacheDir, "share").apply { mkdirs() }, "camera.jpg")
        return FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", f)
    }

    // ---------------------------------------------------------------- trasferimento tra telefoni (QR)

    /** Indirizzo da mostrare nel QR mentre questo telefono invia; null = non sta inviando. */
    val transferUrl = MutableStateFlow<String?>(null)
    /** Quante volte l'altro telefono ha già scaricato il pacchetto (per dire "Inviato"). */
    val transferServed = MutableStateFlow(0)
    private var transferServer: TransferServer? = null

    /** Apre il server e prepara il QR. false = nessuna rete locale (Wi-Fi o hotspot) a cui l'altro possa collegarsi. */
    fun startSending(): Boolean {
        stopSending()
        val host = TvServer.localAddresses().firstOrNull() ?: return false
        val server = TransferServer(
            payload = {
                val all = storage.loadFinished() + storage.loadUnfinished()
                // Le foto viaggiano con le partite (piccole: qualche decina di kB l'una).
                val photos = storage.photoKeys().associateWith { k -> Base64.encodeToString(storage.photoFile(k).readBytes(), Base64.NO_WRAP) }
                val matchPhotos = all.associate { m ->
                    m.id to storage.matchPhotos(m.id).map { Base64.encodeToString(it.readBytes(), Base64.NO_WRAP) }
                }.filterValues { it.isNotEmpty() }
                storage.json.encodeToString(
                    TransferBundle.serializer(), TransferBundle(matches = all, photos = photos, matchPhotos = matchPhotos),
                ).toByteArray()
            },
            onServed = { transferServed.value++ },
        )
        val port = runCatching { server.start() }.getOrNull() ?: return false
        transferServer = server
        transferServed.value = 0
        transferUrl.value = TransferServer.url(host, port, server.token)
        return true
    }

    fun stopSending() {
        transferServer?.stop()
        transferServer = null
        transferUrl.value = null
    }

    /** Testo letto dal QR dell'altro telefono: scarica storico e partite sospese e li aggiunge a questo telefono. */
    fun receiveFrom(text: String) {
        val s = strings
        if (!TransferServer.isTransferUrl(text)) {
            _toasts.tryEmit(s.transferBadCode)
            return
        }
        scope.launch {
            val result = withContext(io) {
                runCatching {
                    val bundle = storage.json.decodeFromString(TransferBundle.serializer(), TransferServer.fetch(text).decodeToString())
                    // Una foto arrivata non sostituisce quella che questo telefono ha già per lo stesso nome.
                    bundle.photos.forEach { (k, b64) ->
                        if (!storage.photoFile(k).exists()) runCatching { storage.savePhoto(k, Base64.decode(b64, Base64.DEFAULT)) }
                    }
                    // Foto ricordo: solo per le partite che qui non ne hanno già.
                    bundle.matchPhotos.forEach { (id, list) ->
                        if (storage.matchPhotos(id).isEmpty()) list.forEach { b64 ->
                            runCatching { storage.addMatchPhoto(id, Base64.decode(b64, Base64.DEFAULT)) }
                        }
                    }
                    importRecords(bundle.matches)
                }.getOrNull()
            }
            _toasts.tryEmit(
                when {
                    result == null -> s.transferFailed
                    result.first + result.second == 0 -> s.transferEmpty
                    else -> s.historyImported(result.first, result.second)
                },
            )
            refreshHistory()
            refreshSaved()
            photosVersion.value++
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
        val tree = storage.historyTree ?: return if (Build.VERSION.SDK_INT >= 29) DOCUMENTS_DIR else strings.defaultFolder
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
    fun saveHistory(name: String, txt: Boolean, json: Boolean, png: Boolean, pdf: Boolean = false) {
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
                        if (pdf) add(Triple("$base.pdf", "application/pdf") { o -> writePdf(sm, o) })
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
                    } else if (Build.VERSION.SDK_INT >= 29) {
                        // Documenti/Tennis Score Manager: si apre da qualsiasi app di gestione file e resta anche se
                        // l'app si disinstalla; niente permessi. Coi nomi uguali Android aggiunge da sé " (1)".
                        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                        for ((file, mime, write) in outputs) {
                            val values = ContentValues().apply {
                                put(MediaStore.MediaColumns.DISPLAY_NAME, file)
                                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                                put(MediaStore.MediaColumns.RELATIVE_PATH, DOCUMENTS_DIR)
                            }
                            val uri = app.contentResolver.insert(collection, values) ?: error("MediaStore insert")
                            app.contentResolver.openOutputStream(uri)?.use(write) ?: error("openOutputStream")
                        }
                        DOCUMENTS_DIR
                    } else {
                        // Android 8 e 9: la cartella dell'app (scrivere in Documenti chiederebbe un permesso in più).
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
    /** PDF completo del riepilogo [sm]: risultato, statistiche, cronologia e foto ricordo. */
    private fun writePdf(sm: LiveMatch, out: OutputStream) {
        val s = strings
        val n = summaryNames()
        PdfReport.write(
            out, sm.record, sm.state, s, n, Reports.renderCard(sm.record, sm.state, s, n),
            storage.matchPhotos(sm.record.id), playerPhoto = { loadPhoto(it) },
        )
    }

    /** Condivisione del riepilogo: l'immagine col testo per i social, oppure ([pdf]) il PDF completo. */
    fun shareIntent(pdf: Boolean = false): Intent? {
        val sm = summary.value ?: return null
        val s = strings
        val n = summaryNames()
        val dir = File(app.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "${Reports.fileBaseName(sm.record, n)}.${if (pdf) "pdf" else "png"}")
        runCatching {
            file.outputStream().use {
                if (pdf) writePdf(sm, it) else Reports.renderCard(sm.record, sm.state, s, n).compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }.onFailure { return null }
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = if (pdf) "application/pdf" else "image/png"
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
            relay.stop()
            heart.disconnectAll()
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
