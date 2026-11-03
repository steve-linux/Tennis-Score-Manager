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
import com.tennis.scoremanager.R
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
import com.tennis.scoremanager.data.Feel
import com.tennis.scoremanager.data.Progress
import com.tennis.scoremanager.data.ProgressItem
import com.tennis.scoremanager.data.ProgressItems
import com.tennis.scoremanager.data.ProgressPdf
import com.tennis.scoremanager.data.Racket
import com.tennis.scoremanager.data.RacketStats
import com.tennis.scoremanager.data.RacketUse
import com.tennis.scoremanager.data.Stringing
import com.tennis.scoremanager.data.Reports
import com.tennis.scoremanager.data.SetupData
import com.tennis.scoremanager.data.Sport
import com.tennis.scoremanager.data.Storage
import com.tennis.scoremanager.data.StrokeStats
import com.tennis.scoremanager.data.Tournament
import com.tennis.scoremanager.data.TournamentKind
import com.tennis.scoremanager.data.TournamentScoring
import com.tennis.scoremanager.data.Tournaments
import com.tennis.scoremanager.data.WorkoutRecord
import com.tennis.scoremanager.data.WorkoutReport
import com.tennis.scoremanager.data.WorkoutStats
import com.tennis.scoremanager.model.MatchEvent
import com.tennis.scoremanager.model.MatchState
import com.tennis.scoremanager.model.PointKind
import com.tennis.scoremanager.model.RulesConfig
import com.tennis.scoremanager.model.ScoreEngine
import com.tennis.scoremanager.model.Side
import com.tennis.scoremanager.model.Transition
import com.tennis.scoremanager.relay.PhoneRelay
import com.tennis.scoremanager.relay.RelayRole
import com.tennis.scoremanager.remote.Gesture
import com.tennis.scoremanager.remote.RemoteInput
import com.tennis.scoremanager.service.MatchService
import com.tennis.scoremanager.transfer.TransferBundle
import com.tennis.scoremanager.transfer.TransferServer
import com.tennis.scoremanager.tv.TvInput
import com.tennis.scoremanager.tv.TvReplay
import com.tennis.scoremanager.tv.TvServer
import com.tennis.scoremanager.tv.TvSettings
import com.tennis.scoremanager.tv.TvSnapshot
import com.tennis.scoremanager.tv.TvSnapshots
import com.tennis.scoremanager.ui.Strings
import com.tennis.scoremanager.ui.TsmColors
import com.tennis.scoremanager.ui.stringsFor
import com.tennis.scoremanager.video.PointMark
import com.tennis.scoremanager.video.PointMarks
import com.tennis.scoremanager.video.ScoreOverlay
import com.tennis.scoremanager.voice.Announcer
import com.tennis.scoremanager.voice.CallBuilder
import com.tennis.scoremanager.voice.Seg
import com.tennis.scoremanager.voice.VoicePack
import com.tennis.scoremanager.workout.WorkoutSession
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json

enum class Screen { HOME, SETUP, OPTIONS, START, MATCH, SUMMARY, HISTORY, TOURNAMENT, WORKOUT, PROGRESS, VIDEOS, RACKETS, DEVICES, VOICE, STROKE_SESSIONS, HELP, STUDENTS, STUDENT, PLAYERS, DATA, DRILLS, MEMBERS, PAYMENTS, CLUB_TOURNAMENTS, CLUB_TOURNAMENT, NOTICE, COURTS_PLAN, SCHOOL, COURSE, TEAMS, TEAM, FIXTURE, BOOK_COURT, COURT_STATS, LADDERS, LADDER }

/**
 * Le schede della prima schermata, organizzate per cosa si vuole fare; quali si vedono dipende dal ruolo attivo
 * ([Role.tabs]).
 */
enum class HomeTab { PLAY, TRAIN, VIDEO, PROGRESS, MORE, STUDENTS, TOURNAMENTS, COURTS, PEOPLE }

/**
 * Chi usa l'app: si possono avere più ruoli (chi gioca e allena) sugli stessi dati; il ruolo attivo sceglie le schede.
 * Il tabellone c'è in tutti: l'allenatore arbitra le partite tra allievi, il circolo quelle dei tornei.
 */
enum class Role(val tabs: List<HomeTab>) {
    /** Tu: giochi, ti alleni, i tuoi video e progressi (l'app di sempre). */
    PLAYER(listOf(HomeTab.PLAY, HomeTab.TRAIN, HomeTab.VIDEO, HomeTab.PROGRESS, HomeTab.MORE)),
    /** Allenatore: gli allievi e i loro allenamenti, partite tra allievi, video. */
    COACH(listOf(HomeTab.STUDENTS, HomeTab.PLAY, HomeTab.VIDEO, HomeTab.MORE)),
    /** Circolo: tornei, campi coi loro tabelloni, giocatori e allenatori. */
    CLUB(listOf(HomeTab.TOURNAMENTS, HomeTab.COURTS, HomeTab.PEOPLE, HomeTab.MORE)),
}

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
        /** Lunghezza massima delle note di una partita (caratteri). */
        const val MAX_NOTES = 2000
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

    val screen = MutableStateFlow(Screen.HOME)
    /** Scheda della prima schermata: resta quella da cui si è partiti, così "Indietro" ci torna. */
    val homeTab = MutableStateFlow(HomeTab.PLAY)

    /** Torna alla prima schermata ([tab] se dato, altrimenti la scheda di prima). */
    fun home(tab: HomeTab? = null) {
        tab?.let { homeTab.value = it }
        screen.value = Screen.HOME
    }
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
    /**
     * Moviola sul tabellone (null = niente): la manda l'arbitro dalla sua moviola. Anche questa va dichiarata prima
     * del blocco init, che la ascolta per aggiornare il tabellone.
     */
    val tvReplay = MutableStateFlow<TvReplay?>(null)
    /** Il torneo del circolo mostrato sul tabellone (ClubTournament.id), null = nessuno. */
    val tvClubTournament = MutableStateFlow<String?>(null)
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
    /** Racchetta dell'allenamento in corso, presa all'avvio. */
    private var workoutUse: RacketUse? = null

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
        updateBandLinks()
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
                screen, setup, options, live, summary, tvMessage, tv, tvServer.clients, tvCountdownEnd, tvReplay,
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

    /**
     * Foto dei giocatori per il tabellone: "/photo/<file>?v=<versione>", così il tabellone ricarica quelle cambiate;
     * senza foto il ritratto di uomo o di donna ("/avatar/man.webp", "/avatar/woman.webp").
     */
    private fun tvPhotos(su: SetupData): Map<Side, List<String>> = Side.entries.associateWith { side ->
        PlayerStats.names(su, side).map { n ->
            storage.photoFile(PlayerStats.key(n)).takeIf { it.exists() }?.let { "/photo/${it.name}?v=${photosVersion.value}" }
                ?: "/avatar/${if (isFemale(n)) "woman" else "man"}.webp"
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
                replay = tvReplay.value,
                club = tvClubSnapshot(),
            ),
        )
        tvServer.publish(tvJson.encodeToString(TvSnapshot.serializer(), snap))
    }

    /** Il tabellone può mostrare la moviola: il suo server è acceso (tabellone TV o secondo telefono). */
    val canShowReplayOnTv: Boolean get() = tvServer.running.value

    fun showReplayOnTv(files: List<File>, speed: Float) {
        val id = System.currentTimeMillis().toString(36)
        tvServer.replayFiles = files
        tvServer.replayId = id
        tvReplay.value = TvReplay(id, files.size, speed, strings.replayTitle.uppercase())
    }

    fun showClubOnTv(id: String?) {
        tvClubTournament.value = id
        publishTv()
    }

    /** Il torneo sul tabellone, rifatto a ogni invio: così i risultati nuovi si vedono subito. */
    private fun tvClubSnapshot(): com.tennis.scoremanager.tv.TvClub? {
        val t = clubTournament(tvClubTournament.value) ?: return null
        val s = strings; val lang = options.value.lang
        fun n(id: String?) = if (id == null) "" else entryNameOf(t, id)
        val pages = mutableListOf<com.tennis.scoremanager.tv.TvClubPage>()
        val groups = com.tennis.scoremanager.data.ClubTournaments.groupMembers(t)
        if (groups.isNotEmpty()) pages += com.tennis.scoremanager.tv.TvClubPage("groups", s.tvGroupsTitle, groups = groups.indices.map { g ->
            com.tennis.scoremanager.tv.TvGroup(s.ctGroup(com.tennis.scoremanager.ui.groupLetter(g)), com.tennis.scoremanager.data.ClubTournaments.standings(t, g).mapIndexed { i, st ->
                com.tennis.scoremanager.tv.TvStanding(i + 1, n(st.entry), st.won, st.played, "%+d".format(st.setsWon - st.setsLost), "%+d".format(st.gamesWon - st.gamesLost), i < t.qualify)
            })
        })
        if (t.rounds > 0) pages += com.tennis.scoremanager.tv.TvClubPage("draw", s.ctTabDraw, rounds = (1..t.rounds).map { r ->
            val ms = t.matches.filter { it.round == r }.sortedBy { it.slot }
            com.tennis.scoremanager.tv.TvRound(clubRoundName(t, ms.first()), ms.map { m ->
                val a = if (m.a == null && m.bye) s.ctBye else n(m.a); val b = if (m.b == null && m.bye) s.ctBye else n(m.b)
                val extra = listOfNotNull(m.score.ifBlank { null }, s.ctWalkover.takeIf { m.walkover }, s.ctRetired.takeIf { m.retired }).joinToString(" ")
                com.tennis.scoremanager.tv.TvDrawMatch(a, b, if (m.bye) "" else extra, when (m.winner) { null -> null; m.a -> 0; else -> 1 })
            })
        })
        // Ordine di gioco: le partite di oggi (con l'orario), o se oggi non ce ne sono le prossime in programma.
        val today = com.tennis.scoremanager.data.CourtPlans.dayStart(System.currentTimeMillis())
        val scheduled = t.matches.filter { !it.bye && it.at != null && it.a != null && it.b != null }
        val todays = com.tennis.scoremanager.data.Notices.onDay(scheduled, today)
        val show = todays.ifEmpty { scheduled.filter { !it.played && it.at!! >= today }.sortedBy { it.at }.take(12) }
        if (show.isNotEmpty() || t.drawn) pages += com.tennis.scoremanager.tv.TvClubPage(
            "order", if (todays.isNotEmpty()) s.ntOrderTitle(t.name, com.tennis.scoremanager.data.Notices.day(today, lang)) else s.ntOrderAll(t.name),
            rows = show.sortedWith(compareBy({ it.at }, { it.court })).map { m ->
                com.tennis.scoremanager.tv.TvOrderRow(
                    (if (todays.isEmpty()) com.tennis.scoremanager.data.Notices.day(m.at!!, lang) + " " else "") + com.tennis.scoremanager.data.Reports.time(m.at, lang),
                    m.court, n(m.a), n(m.b), clubRoundName(t, m), m.score, m.played, when (m.winner) { null -> null; m.a -> 0; else -> 1 },
                )
            },
        )
        val sub = listOfNotNull(storage.clubName.ifBlank { null }, t.category.ifBlank { null }, t.champion?.let { s.ctChampion(n(it)) }).joinToString(" · ")
        return com.tennis.scoremanager.tv.TvClub(t.id, t.name, sub, 15, pages, com.tennis.scoremanager.tv.TvClubLabels(s.ctColWon, s.ctColSets, s.ctColGames, s.tvNoMatches))
    }

    fun setTvReplaySpeed(speed: Float) {
        tvReplay.value = tvReplay.value?.copy(speed = speed)
    }

    /** Pausa, ripresa o fotogramma dell'arbitro: il tabellone va allo stesso punto e fa lo stesso. */
    fun syncTvReplay(paused: Boolean, part: Int, posMs: Long) {
        tvReplay.value = tvReplay.value?.let { it.copy(paused = paused, part = part, posMs = posMs, sync = it.sync + 1) }
    }

    fun hideReplayOnTv() {
        tvReplay.value = null
        tvServer.replayId = ""
        tvServer.replayFiles = emptyList()
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
            updateBandLinks()
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
            Screen.SETUP -> Screen.HOME
            Screen.PROGRESS -> Screen.HOME
            Screen.RACKETS -> Screen.HOME
            Screen.VIDEOS -> Screen.HOME
            Screen.DEVICES -> Screen.HOME
            Screen.VOICE -> Screen.HOME
            Screen.STROKE_SESSIONS -> strokeSessionsBack
            Screen.HELP -> Screen.HOME
            Screen.STUDENTS -> Screen.HOME
            Screen.STUDENT -> studentBack
            Screen.PLAYERS -> Screen.HOME
            Screen.DATA -> Screen.HOME
            Screen.DRILLS -> Screen.HOME
            Screen.MEMBERS -> Screen.HOME
            Screen.PAYMENTS -> Screen.HOME
            Screen.CLUB_TOURNAMENTS -> Screen.HOME
            Screen.CLUB_TOURNAMENT -> Screen.CLUB_TOURNAMENTS
            Screen.NOTICE -> noticeBack
            Screen.COURTS_PLAN -> Screen.HOME
            Screen.BOOK_COURT -> Screen.HOME
            Screen.COURT_STATS -> Screen.COURTS_PLAN
            Screen.LADDERS -> Screen.HOME
            Screen.LADDER -> Screen.LADDERS
            Screen.SCHOOL -> Screen.HOME
            Screen.COURSE -> Screen.SCHOOL
            Screen.TEAMS -> Screen.HOME
            Screen.TEAM -> Screen.TEAMS
            Screen.FIXTURE -> Screen.TEAM
            // L'allenamento in corso continua anche uscendo dalla sua schermata (si ritrova riaprendola).
            Screen.WORKOUT -> if (workoutDetail.value != null) {
                workoutDetail.value = null
                Screen.WORKOUT
            } else {
                Screen.HOME
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
        // Solo i lati non ancora collegati a quel braccialetto: assign chiude e riapre il collegamento.
        for (side in Side.entries) {
            val p1 = side == Side.P1
            val addr = storage.bandAddress(p1) ?: continue
            if (ble.bands.value[side]?.address != addr) ble.assign(side, addr, storage.bandName(p1))
        }
    }

    /** Pagina Dispositivi aperta: lì i braccialetti si collegano e si cercano in qualsiasi modalità. */
    @Volatile private var devicesOpen = false

    /** I braccialetti servono: partita in modalità braccialetti, allenamento dei colpi o pagina Dispositivi. */
    private fun bandsWanted() = options.value.mode == PlayMode.BANDS || serveTraining || devicesOpen

    /** Collega i braccialetti salvati quando servono, altrimenti li stacca (consumano meno e non segnano punti per sbaglio). */
    private fun updateBandLinks() {
        if (relay.isSatellite) return
        if (bandsWanted()) restoreBands() else ble.disconnectAll()
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
    fun setBandScan(on: Boolean, devicesPage: Boolean = false) {
        if (devicesPage) {
            devicesOpen = on
            updateBandLinks()
        }
        autoAssign = on && bandsWanted()
        ble.setAutoScan(autoAssign)
    }

    private fun autoFill(found: List<FoundBand>) {
        if (!autoAssign || !bandsWanted()) return
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
            syncTrainingStrokes(bands)
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

    /**
     * Allenamento dei colpi aperto: i tasti del braccialetto vanno a lui, non alla partita, e il riconoscimento dei colpi
     * si accende da sé (serve l'istante del colpo per la clip e l'analisi; non serve la calibrazione).
     */
    @Volatile var serveTraining = false
        set(v) {
            field = v
            synchronized(trainingTried) { trainingTried.clear() }
            if (v) {
                // In allenamento il braccialetto serve qualunque sia la modalità della partita.
                updateBandLinks()
                syncTrainingStrokes(ble.bands.value)
            } else {
                // Prima si rimette com'era il riconoscimento, poi si staccano i braccialetti se non servono più.
                syncTrainingStrokes(ble.bands.value)
                scope.launch {
                    delay(1_500)
                    if (!serveTraining) updateBandLinks()
                }
            }
        }

    /** Braccialetti già provati in questo allenamento: se il riconoscimento lo si spegne a mano, resta spento. */
    private val trainingTried = mutableSetOf<String>()

    /** Accende il riconoscimento dei colpi durante l'allenamento e lo rimette com'era dopo. */
    private fun syncTrainingStrokes(bands: Map<Side, com.tennis.scoremanager.ble.BandInfo>) {
        for ((side, info) in bands) {
            if (info.state != LinkState.READY) continue
            val st = info.settings?.takeIf { it.supportsStrokes } ?: continue
            val auto = storage.strokesAutoOn
            if (serveTraining) {
                val first = synchronized(trainingTried) { trainingTried.add(info.address) }
                if (first && !st.strokes) {
                    storage.strokesAutoOn = auto + info.address
                    scope.launch { ble.writeSettings(side, st.copy(strokes = true)) }
                }
            } else if (info.address in auto) {
                storage.strokesAutoOn = auto - info.address
                if (st.strokes) scope.launch { ble.writeSettings(side, st.copy(strokes = false)) }
            }
        }
    }

    /** Sessioni di allenamento al servizio salvate, dalla più recente. */
    val serveSessions = MutableStateFlow<List<com.tennis.scoremanager.data.ServeSession>>(emptyList())

    fun refreshServeSessions() {
        scope.launch { serveSessions.value = withContext(io) { storage.loadServeSessions() } }
    }

    /**
     * Una volta per avvio: crea le quattro cartelle dei colpi in Documenti e ci sposta le clip delle sessioni di prima.
     */
    private var strokeFoldersDone = false

    fun prepareStrokeFolders() {
        if (strokeFoldersDone) return
        strokeFoldersDone = true
        scope.launch {
            withContext(io) {
                runCatching {
                    storage.ensureStrokeFolders(strings.strokeFolderName, strings.strokeName)
                    storage.migrateServeSessions(strings.strokeFolderName, strings.strokeName)
                }
            }
            refreshServeSessions()
        }
    }

    fun deleteServeSession(sess: com.tennis.scoremanager.data.ServeSession) = deleteServeSessions(listOf(sess))

    /** Cancella più sessioni insieme; con [videosOnly] solo i loro video (restano conteggi e analisi). */
    fun deleteServeSessions(list: List<com.tennis.scoremanager.data.ServeSession>, videosOnly: Boolean = false) {
        scope.launch {
            withContext(io) { list.forEach { if (videosOnly) storage.deleteServeVideos(it) else storage.deleteServeSession(it) } }
            refreshServeSessions()
        }
    }

    /** Colpo scelto in Allenati (vale anche per la pagina di tutte le sessioni). */
    val trainStroke = MutableStateFlow(com.tennis.scoremanager.data.TrainedStroke.SERVE)

    /** Apre tutte le sessioni del colpo [stroke] (dell'allievo scelto); Indietro torna da dove si è partiti. */
    fun openStrokeSessions(stroke: com.tennis.scoremanager.data.TrainedStroke) {
        trainStroke.value = stroke
        strokeSessionsBack = if (screen.value == Screen.STUDENT) Screen.STUDENT else Screen.HOME
        screen.value = Screen.STROKE_SESSIONS
    }

    private var strokeSessionsBack = Screen.HOME

    // ---------------------------------------------------------------- giocatori e allievi

    /** Il database dei giocatori, per nome. Gli allievi sono quelli con student. */
    val players = MutableStateFlow(storage.loadPlayers().sortedBy { it.name.lowercase() })

    /** Gli allievi dell'allenatore (giocatori con student). */
    val students: StateFlow<List<com.tennis.scoremanager.data.PlayerProfile>> =
        players.map { l -> l.filter { it.student } }.stateIn(scope, SharingStarted.Eagerly, players.value.filter { it.student })

    /** Chi si allena con l'allenamento dei colpi: PlayerProfile.id, null = nessun allievo. */
    val trainStudent = MutableStateFlow(storage.trainStudent.ifEmpty { null })

    /** Giocatore aperto nella sua scheda. */
    val openStudentId = MutableStateFlow<String?>(null)

    fun refreshPlayers() {
        scope.launch { players.value = withContext(io) { storage.loadPlayers() }.sortedBy { it.name.lowercase() } }
    }

    fun refreshStudents() = refreshPlayers()

    /** Il giocatore di un identificativo (anche di un suo alias). */
    fun playerById(id: String?): com.tennis.scoremanager.data.PlayerProfile? =
        id?.let { com.tennis.scoremanager.data.Players.byId(players.value, it) }

    fun studentById(id: String?) = playerById(id)

    /** Aggiunge o cambia un giocatore (stesso id). */
    fun savePlayer(p: com.tennis.scoremanager.data.PlayerProfile) {
        val list = players.value.filter { it.id != p.id } + p
        players.value = list.sortedBy { it.name.lowercase() }
        scope.launch(io) { storage.savePlayers(list) }
    }

    fun saveStudent(st: com.tennis.scoremanager.data.PlayerProfile) = savePlayer(st.copy(student = true))

    /** Domande in sospeso sui giocatori arrivati da un altro telefono ("è lui?"). */
    val playerQuestions = MutableStateFlow(storage.loadPlayerQuestions())

    /**
     * Risposta a una domanda: [localId] = è il nostro giocatore (i due identificativi si legano e le sue partite
     * arrivate diventano sue); null = un'altra persona, che si aggiunge col suo identificativo.
     */
    fun answerPlayerQuestion(q: com.tennis.scoremanager.data.Players.Question, localId: String?) {
        val rest = playerQuestions.value.filter { it.incomingId != q.incomingId }
        playerQuestions.value = rest
        scope.launch {
            withContext(io) {
                var people = storage.loadPlayers()
                if (localId != null) {
                    people = people.map { if (it.id == localId) it.copy(aliases = it.aliases + q.incomingId) else it }
                    storage.savePlayers(people)
                    storage.remapPlayer(setOf(q.incomingId), localId)
                } else if (people.none { it.id == q.incomingId }) {
                    storage.savePlayers(people + com.tennis.scoremanager.data.PlayerProfile(q.incomingId, q.name, nickname = q.name, createdAt = System.currentTimeMillis()))
                }
                storage.savePlayerQuestions(rest)
            }
            refreshPlayers()
            refreshHistory()
            refreshSaved()
        }
    }

    /**
     * Unisce [other] a [keep]: è la stessa persona scritta in due modi o creata due volte. Le partite e le sessioni di
     * [other] diventano di [keep], che tiene anche i suoi identificativi (così le partite che arriveranno da altri
     * telefoni lo riconoscono).
     */
    fun mergePlayers(keep: String, other: String) {
        val a = playerById(keep) ?: return
        val b = playerById(other) ?: return
        if (a.id == b.id) return
        val merged = a.copy(
            aliases = a.aliases + b.aliases + b.id, student = a.student || b.student,
            hand = a.hand ?: b.hand, racket = a.racket ?: b.racket,
            phone = a.phone.ifBlank { b.phone }, email = a.email.ifBlank { b.email }, birth = a.birth ?: b.birth, member = a.member ?: b.member,
            sex = a.sex.ifBlank { b.sex }, heightCm = a.heightCm ?: b.heightCm, weightKg = a.weightKg ?: b.weightKg, club = a.club.ifBlank { b.club }, backhand = a.backhand ?: b.backhand,
            note = listOf(a.note, b.note).filter { it.isNotBlank() }.joinToString("\n"),
        )
        val list = players.value.filter { it.id != b.id && it.id != a.id } + merged
        players.value = list.sortedBy { it.name.lowercase() }
        if (trainStudent.value == b.id) setTrainStudent(a.id)
        scope.launch {
            withContext(io) {
                storage.savePlayers(list)
                storage.remapPlayer(setOf(b.id) + b.aliases, a.id)
                saveClubBook(com.tennis.scoremanager.data.Fees.remap(clubBook.value, setOf(b.id) + b.aliases, a.id))
                storage.loadServeSessions().filter { it.student == b.id }.forEach { storage.saveServeSession(it.copy(student = a.id)) }
            }
            refreshHistory()
            refreshSaved()
            refreshServeSessions()
        }
    }

    /**
     * Separa un omonimo: le partite [matchIds] di [id] sono di un'altra persona con lo stesso nome, che nasce ora.
     * Restituisce il giocatore nuovo.
     */
    fun splitPlayer(id: String, matchIds: Set<String>): com.tennis.scoremanager.data.PlayerProfile? {
        val p = playerById(id) ?: return null
        if (matchIds.isEmpty()) return null
        val twin = com.tennis.scoremanager.data.PlayerProfile(com.tennis.scoremanager.data.Players.newId(), p.name, nickname = p.name, createdAt = System.currentTimeMillis())
        val list = players.value + twin
        players.value = list.sortedBy { it.name.lowercase() }
        scope.launch {
            withContext(io) {
                storage.savePlayers(list)
                storage.remapPlayer(setOf(p.id) + p.aliases, twin.id, matchIds)
            }
            refreshHistory()
            refreshSaved()
        }
        return twin
    }

    /** Un giocatore nuovo nel database (anche con lo stesso nome di un altro: un omonimo). */
    /** Posto di Nuova partita per cui si sta creando un giocatore (nome e cognome da chiedere); null = nessuno. */
    val playerToCreate = MutableStateFlow<com.tennis.scoremanager.data.Players.Slot?>(null)

    /** Per distinguere due omonimi: quante partite ha e l'ultima volta (null senza partite). */
    fun playerHint(p: com.tennis.scoremanager.data.PlayerProfile): String? {
        val lines = studentMatches(p)
        val last = lines.lastOrNull() ?: return if (p.student) strings.studentsTitle else null
        return strings.playerMatchesHint(lines.size, com.tennis.scoremanager.data.Reports.date(last.rec.endedAt ?: last.rec.updatedAt, options.value.lang))
    }

    /** Toglie un allievo dall'elenco: resta un giocatore, e le sue sessioni restano sue. */
    fun deleteStudent(id: String) {
        val p = playerById(id) ?: return
        if (trainStudent.value == id) setTrainStudent(null)
        savePlayer(p.copy(student = false))
    }

    /**
     * Cancella l'allievo e quello che il telefono ha di suo: il giocatore, le sessioni con video e analisi, note e
     * foto; la sua racchetta resta tra le racchette ma non è più sua. Le partite dello storico no: sono anche degli
     * avversari (vi resta il nome).
     */
    fun purgeStudent(id: String) {
        val st = playerById(id) ?: return
        val list = players.value.filter { it.id != st.id }
        players.value = list
        if (trainStudent.value == id) setTrainStudent(null)
        removePhoto(st.name)
        // Dal registro del circolo via gli addebiti non pagati; i pagamenti fatti restano col nome (contabilità).
        saveClubBook(com.tennis.scoremanager.data.Fees.forget(clubBook.value, setOf(st.id) + st.aliases))
        rackets.value.filter { it.owner.isNotBlank() && PlayerStats.key(it.owner) == PlayerStats.key(st.name) }.forEach { saveRacket(it.copy(owner = "")) }
        scope.launch {
            withContext(io) {
                storage.savePlayers(list)
                storage.loadServeSessions().filter { it.student == st.id }.forEach { storage.deleteServeSession(it) }
            }
            refreshServeSessions()
        }
    }

    fun setTrainStudent(id: String?) {
        trainStudent.value = id
        storage.trainStudent = id.orEmpty()
    }

    fun openStudents() {
        refreshPlayers()
        screen.value = Screen.STUDENTS
    }

    fun openStudent(id: String) {
        openStudentId.value = id
        // Indietro torna da dove si è aperta la scheda.
        studentBack = screen.value.takeIf { it in setOf(Screen.PLAYERS, Screen.MEMBERS, Screen.PAYMENTS, Screen.CLUB_TOURNAMENT, Screen.COURTS_PLAN, Screen.COURSE, Screen.TEAM, Screen.FIXTURE) } ?: Screen.STUDENTS
        screen.value = Screen.STUDENT
    }

    private var studentBack = Screen.STUDENTS

    /** Circolo › Soci: i giocatori iscritti al circolo, con tessere, certificati e consensi. */
    fun openMembers() {
        refreshPlayers()
        screen.value = Screen.MEMBERS
    }

    /** Circolo › Quote e pagamenti. */
    fun openPayments() {
        refreshPlayers()
        screen.value = Screen.PAYMENTS
    }

    /** Altro › Giocatori: tutto il database. */
    fun openPlayers() {
        refreshPlayers()
        refreshHistory()
        screen.value = Screen.PLAYERS
    }

    /** Chi è il giocatore nelle statistiche: il suo identificativo e quelli che sono lui (alias). */
    private fun keysOf(p: com.tennis.scoremanager.data.PlayerProfile): Set<String> = setOf(p.id) + p.aliases

    /** Partite finite del giocatore dallo storico, dalla più vecchia. */
    fun studentMatches(p: com.tennis.scoremanager.data.PlayerProfile): List<PlayerStats.MatchLine> {
        val keys = keysOf(p)
        return PlayerStats.linesBy(played()) { rec -> Side.entries.firstOrNull { side -> PlayerStats.whos(rec.setup, side).any { it.key in keys } } }
    }

    /** Rating TSM del giocatore nello sport con più partite (null senza partite). */
    fun studentRating(p: com.tennis.scoremanager.data.PlayerProfile) = ratingSports().firstOrNull()?.let { ratingTable(it).player(p.id) }

    /**
     * Tu: il tuo giocatore del database. Prima del database c'era solo il nome (Altro › Tu): se è di un giocatore solo,
     * è lui.
     */
    fun me(): com.tennis.scoremanager.data.PlayerProfile? =
        playerById(storage.myPlayerId) ?: com.tennis.scoremanager.data.Players.byName(players.value, storage.myName).singleOrNull()

    /** Sei tu: il tuo nome segue quello del giocatore (per le versioni e i punti dell'app che usano ancora il nome). */
    fun setMe(p: com.tennis.scoremanager.data.PlayerProfile?) {
        storage.myPlayerId = p?.id.orEmpty()
        storage.myName = p?.name.orEmpty()
    }

    // ---------------------------------------------------------------- esercizi guidati

    /** Gli esercizi guidati; la prima volta gli esempi, uno per colpo (si cambiano o si tolgono a piacere). */
    val drills = MutableStateFlow(storage.loadDrills() ?: com.tennis.scoremanager.data.Drills.templates({ strings.drillTemplateName(it.band) }, System.currentTimeMillis()))

    /** Esercizio scelto per il prossimo allenamento (Drill.id), null = libero. */
    val trainDrill = MutableStateFlow<String?>(null)

    fun drillById(id: String?): com.tennis.scoremanager.data.Drill? = id?.let { i -> drills.value.firstOrNull { it.id == i } }

    /** Il registro del circolo: quote, addebiti e pagamenti. */
    val clubBook = MutableStateFlow(storage.loadClubBook())

    fun saveClubBook(book: com.tennis.scoremanager.data.ClubBook) {
        clubBook.value = book
        scope.launch(io) { storage.saveClubBook(book) }
    }

    // ---- Tornei del circolo (sociali e FITP) ----

    val clubTournaments = MutableStateFlow(storage.loadClubTournaments())
    val openClubTournamentId = MutableStateFlow<String?>(null)
    /** Partita del tabellone che si sta per giocare col segnapunti: (torneo, partita). */
    private var pendingClubMatch: Pair<String, String>? = null

    fun openClubTournaments() {
        refreshPlayers()
        screen.value = Screen.CLUB_TOURNAMENTS
    }

    fun openClubTournament(id: String) {
        openClubTournamentId.value = id
        screen.value = Screen.CLUB_TOURNAMENT
    }

    fun clubTournament(id: String?): com.tennis.scoremanager.data.ClubTournament? = id?.let { i -> clubTournaments.value.firstOrNull { it.id == i } }

    fun saveClubTournament(t: com.tennis.scoremanager.data.ClubTournament) {
        val list = clubTournaments.value.filter { it.id != t.id } + t
        clubTournaments.value = list
        // Il torneo sul tabellone si aggiorna subito.
        if (tvClubTournament.value == t.id) publishTv()
        // La quota d'iscrizione è una quota del registro: nome e importo seguono il torneo.
        if (t.feeCents > 0) {
            val book = clubBook.value
            val fee = book.fees.firstOrNull { it.id == t.feeId }
            val name = strings.ctFeeName(t.name)
            val want = (fee ?: com.tennis.scoremanager.data.Fee(t.feeId ?: com.tennis.scoremanager.data.Fees.newId("f"), name, t.feeCents, com.tennis.scoremanager.data.FeeKind.TOURNAMENT, createdAt = System.currentTimeMillis(), tournament = t.id))
                .copy(name = name, cents = t.feeCents, due = t.start)
            if (want != fee) saveClubBook(book.copy(fees = book.fees.filter { it.id != want.id } + want))
            if (t.feeId != want.id) return saveClubTournament(t.copy(feeId = want.id))
        }
        scope.launch(io) { storage.saveClubTournaments(list) }
    }

    /** Toglie il torneo; dal registro spariscono le iscrizioni non pagate (quelle pagate restano: contabilità). */
    fun deleteClubTournament(id: String) {
        val t = clubTournament(id) ?: return
        val list = clubTournaments.value.filter { it.id != id }
        clubTournaments.value = list
        if (tvClubTournament.value == id) showClubOnTv(null)
        t.feeId?.let { f -> dropUnpaidFees(f, null) }
        scope.launch(io) { storage.saveClubTournaments(list) }
    }

    private fun dropUnpaidFees(feeId: String, players: Set<String>?) {
        var book = clubBook.value
        for (ch in book.charges.filter { it.fee == feeId && (players == null || it.player in players) && it.payments.isEmpty() }) book = com.tennis.scoremanager.data.Fees.cancel(book, ch.id)
        saveClubBook(book)
    }

    /** Iscrive un giocatore (o una coppia); con la quota, l'iscrizione si addebita a ciascuno. */
    fun addClubEntry(t: com.tennis.scoremanager.data.ClubTournament, people: List<com.tennis.scoremanager.data.PlayerProfile>) {
        val now = System.currentTimeMillis()
        val e = com.tennis.scoremanager.data.CTEntry(com.tennis.scoremanager.data.ClubTournaments.newId("e"), people.map { it.id }, people.map { it.name }, at = now)
        val saved = t.copy(entries = t.entries + e)
        saveClubTournament(saved)
        val cur = clubTournament(t.id) ?: return
        val fee = clubBook.value.fees.firstOrNull { it.id == cur.feeId } ?: return
        var book = clubBook.value
        for (p in people) if (book.charges.none { it.fee == fee.id && it.player == p.id && !it.cancelled }) book = com.tennis.scoremanager.data.Fees.charge(book, p, fee.name, fee.cents, now, fee)
        saveClubBook(book)
    }

    fun removeClubEntry(t: com.tennis.scoremanager.data.ClubTournament, entryId: String) {
        val e = t.entries.firstOrNull { it.id == entryId } ?: return
        saveClubTournament(t.copy(entries = t.entries - e))
        t.feeId?.let { dropUnpaidFees(it, e.players.toSet()) }
    }

    /** Il sorteggio: entrano gli iscritti in regola, in ordine di classifica; gironi o tabellone. */
    fun drawClubTournament(t: com.tennis.scoremanager.data.ClubTournament): Boolean {
        val now = System.currentTimeMillis()
        val ok = com.tennis.scoremanager.data.ClubTournaments.eligible(t, players.value, clubBook.value, now)
        if (ok.size < 2) return false
        val ids = com.tennis.scoremanager.data.ClubTournaments.ordered(ok, players.value).map { it.id }
        val ms = if (t.format == com.tennis.scoremanager.data.CTFormat.GROUPS) com.tennis.scoremanager.data.ClubTournaments.groupsDraw(ids, t.groups)
        else com.tennis.scoremanager.data.ClubTournaments.knockout(ids, kotlin.random.Random(now), now)
        saveClubTournament(t.copy(matches = ms, drawnAt = now))
        return true
    }

    /** Finiti i gironi: il tabellone con chi passa. */
    fun clubKnockoutFromGroups(t: com.tennis.scoremanager.data.ClubTournament) {
        val ko = com.tennis.scoremanager.data.ClubTournaments.knockoutFromGroups(t, kotlin.random.Random(System.currentTimeMillis()), System.currentTimeMillis())
        saveClubTournament(t.copy(matches = t.matches.filter { it.round == 0 } + ko))
    }

    /** Rifà il sorteggio da capo (si può finché non c'è un risultato). */
    fun undoClubDraw(t: com.tennis.scoremanager.data.ClubTournament) = saveClubTournament(t.copy(matches = emptyList(), drawnAt = null))

    fun setClubResult(t: com.tennis.scoremanager.data.ClubTournament, matchId: String, winner: String?, score: String, walkover: Boolean, retired: Boolean) =
        saveClubTournament(com.tennis.scoremanager.data.ClubTournaments.setResult(t, matchId, winner, score, walkover, retired))

    fun setClubSchedule(t: com.tennis.scoremanager.data.ClubTournament, matchId: String, court: String, at: Long?) =
        saveClubTournament(t.copy(matches = t.matches.map { if (it.id == matchId) it.copy(court = court, at = at) else it }))

    /** Nome del turno: finale, semifinali, quarti, ottavi… o "Girone A". */
    fun clubRoundName(t: com.tennis.scoremanager.data.ClubTournament, m: com.tennis.scoremanager.data.CTMatch): String = com.tennis.scoremanager.ui.ctRoundName(strings, t, m)

    /**
     * Gioca una partita del tabellone col segnapunti: Nuova partita si apre con giocatori, torneo, turno e campo già
     * scritti; a partita finita il risultato torna da solo nel tabellone.
     */
    fun playClubMatch(t: com.tennis.scoremanager.data.ClubTournament, m: com.tennis.scoremanager.data.CTMatch) {
        val a = t.entries.firstOrNull { it.id == m.a } ?: return
        val b = t.entries.firstOrNull { it.id == m.b } ?: return
        fun p(e: com.tennis.scoremanager.data.CTEntry, i: Int) = e.players.getOrNull(i)?.let { playerById(it) }
        fun name(e: com.tennis.scoremanager.data.CTEntry, i: Int) = p(e, i)?.name ?: e.names.getOrNull(i).orEmpty()
        fun rank(e: com.tennis.scoremanager.data.CTEntry, i: Int) = p(e, i)?.member?.ranking.orEmpty()
        updateSetup {
            it.copy(
                club = storage.clubName.ifBlank { it.club }, court = m.court.ifBlank { it.court }, doubles = t.doubles,
                p1a = name(a, 0), p1b = if (t.doubles) name(a, 1) else "", p2a = name(b, 0), p2b = if (t.doubles) name(b, 1) else "",
                p1aId = a.players.getOrNull(0).orEmpty(), p1bId = if (t.doubles) a.players.getOrNull(1).orEmpty() else "",
                p2aId = b.players.getOrNull(0).orEmpty(), p2bId = if (t.doubles) b.players.getOrNull(1).orEmpty() else "",
                p1aRank = rank(a, 0), p1bRank = if (t.doubles) rank(a, 1) else "", p2aRank = rank(b, 0), p2bRank = if (t.doubles) rank(b, 1) else "",
                event = t.name, category = t.category, round = clubRoundName(t, m),
            )
        }
        pendingClubMatch = t.id to m.id
        go(Screen.SETUP)
    }

    /** La partita appena iniziata è quella del tabellone scelta con [playClubMatch]. */
    private fun linkClubMatch(recordId: String, su: SetupData) {
        val (tid, mid) = pendingClubMatch ?: return
        pendingClubMatch = null
        val t = clubTournament(tid) ?: return
        val m = t.matches.firstOrNull { it.id == mid } ?: return
        // Solo se in campo ci sono proprio loro: chi è tornato indietro e ha iniziato un'altra partita non la lega al torneo.
        val a = t.entries.firstOrNull { it.id == m.a }?.players?.firstOrNull()
        val b = t.entries.firstOrNull { it.id == m.b }?.players?.firstOrNull()
        if (a == null || b == null || su.p1aId != a || su.p2aId != b) return
        saveClubTournament(t.copy(matches = t.matches.map { if (it.id == mid) it.copy(record = recordId) else it }))
    }

    /** A partita finita, il risultato va nel tabellone (Giocatore 1 = il primo iscritto della partita). */
    private fun clubMatchFinished(rec: MatchRecord, state: com.tennis.scoremanager.model.MatchState) {
        for (t in clubTournaments.value) {
            val m = t.matches.firstOrNull { it.record == rec.id } ?: continue
            val w = when (state.winner) { Side.P1 -> m.a; Side.P2 -> m.b; else -> null }
            saveClubTournament(com.tennis.scoremanager.data.ClubTournaments.setResult(t, m.id, w, com.tennis.scoremanager.data.Reports.scoreLine(state, Side.P1), record = rec.id))
        }
    }

    // ---- Squadre e campionati ----

    val teams = MutableStateFlow(storage.loadTeams())
    val openTeamId = MutableStateFlow<String?>(null)
    val openFixtureId = MutableStateFlow<String?>(null)
    /** Partita di un incontro da giocare col segnapunti: (squadra, incontro, partita). */
    private var pendingTeamRubber: Triple<String, String, Int>? = null

    fun openTeams() {
        refreshPlayers()
        screen.value = Screen.TEAMS
    }

    fun openTeam(id: String) {
        openTeamId.value = id
        screen.value = Screen.TEAM
    }

    fun openFixture(teamId: String, fixtureId: String) {
        openTeamId.value = teamId
        openFixtureId.value = fixtureId
        screen.value = Screen.FIXTURE
    }

    fun team(id: String?): com.tennis.scoremanager.data.Team? = id?.let { i -> teams.value.firstOrNull { it.id == i } }

    /** Salva la squadra e rifà i campi tenuti per gli incontri in casa nel planning. */
    fun saveTeam(t: com.tennis.scoremanager.data.Team) {
        val list = teams.value.filter { it.id != t.id } + t
        teams.value = list
        scope.launch(io) { storage.saveTeams(list) }
        val p = courtPlan.value
        val np = p.copy(blocks = p.blocks.filter { it.team != t.id } + com.tennis.scoremanager.data.Teams.blocks(t))
        if (np != p) {
            courtPlan.value = np
            scope.launch(io) { storage.saveCourtPlan(np) }
        }
    }

    fun saveFixture(t: com.tennis.scoremanager.data.Team, f: com.tennis.scoremanager.data.Fixture) =
        saveTeam(t.copy(fixtures = (t.fixtures.filter { it.id != f.id } + f).sortedBy { it.at }))

    fun deleteTeam(id: String) {
        val list = teams.value.filter { it.id != id }
        teams.value = list
        scope.launch(io) { storage.saveTeams(list) }
        val p = courtPlan.value.let { it.copy(blocks = it.blocks.filter { b -> b.team != id }) }
        courtPlan.value = p
        scope.launch(io) { storage.saveCourtPlan(p) }
    }

    fun teamsOf(p: com.tennis.scoremanager.data.PlayerProfile): List<com.tennis.scoremanager.data.Team> {
        val keys = setOf(p.id) + p.aliases
        return teams.value.filter { t -> t.roster.any { it in keys } || t.captain in keys }
    }

    /** "domenica 11 aprile, 09:00 · in casa" o "· trasferta: TC Altro, via…". */
    fun fixtureWhen(f: com.tennis.scoremanager.data.Fixture): String {
        val s = strings; val lang = options.value.lang
        return "${com.tennis.scoremanager.data.Notices.day(f.at, lang)}, ${com.tennis.scoremanager.data.Reports.time(f.at, lang)} · " +
            (if (f.home) s.tmHome else s.tmAway) + (f.place.ifBlank { null }?.let { ": $it" } ?: "")
    }

    private fun teamNotice(title: String, t: com.tennis.scoremanager.data.Team, f: com.tennis.scoremanager.data.Fixture, who: List<String>, body: (com.tennis.scoremanager.data.PlayerProfile) -> List<String>, common: String) {
        val now = System.currentTimeMillis()
        val rec = who.mapNotNull { playerById(it) }.map { p ->
            val hello = strings.ntHello(p.firstName.ifBlank { p.name })
            com.tennis.scoremanager.data.Notices.recipient(p, (listOf(hello) + body(p) + listOfNotNull(clubSign.ifBlank { null }?.let { "\n$it" })).joinToString("\n"), now)
        }
        openNotice(com.tennis.scoremanager.data.NoticePlan(title, "${t.name} – ${f.opponent}", common, rec))
    }

    /** Chiede la disponibilità a tutta la rosa (o a chi non ha ancora risposto, con [missingOnly]). */
    fun noticeAvailability(t: com.tennis.scoremanager.data.Team, f: com.tennis.scoremanager.data.Fixture, missingOnly: Boolean) {
        val s = strings
        val who = if (missingOnly) com.tennis.scoremanager.data.Teams.noAnswer(t, f) else t.roster
        val lines = listOf(s.tmAskAvailability("${t.name} – ${f.opponent}"), fixtureWhen(f), s.tmReplyYesNo)
        teamNotice(s.tmAvailabilityTitle, t, f, who, { lines }, (listOf("${t.name} – ${f.opponent}", fixtureWhen(f), s.tmReplyYesNo)).joinToString("\n"))
    }

    /** La convocazione ai convocati: incontro, giorno, ora, dove e il ritrovo. */
    fun noticeCallUp(t: com.tennis.scoremanager.data.Team, f: com.tennis.scoremanager.data.Fixture) {
        val s = strings
        val names = f.callUp.mapNotNull { playerById(it)?.name }
        val lines = listOfNotNull(s.tmCalledUp("${t.name} – ${f.opponent}"), fixtureWhen(f), f.meeting.ifBlank { null }?.let { s.tmMeetingLine(it) }, f.note.ifBlank { null })
        val common = (listOf(s.tmCallUpTitle + ": ${t.name} – ${f.opponent}", fixtureWhen(f)) + listOfNotNull(f.meeting.ifBlank { null }?.let { s.tmMeetingLine(it) }) + names.map { "• $it" }).joinToString("\n")
        teamNotice(s.tmCallUpTitle, t, f, f.callUp, { lines }, common)
    }

    /** Il risultato dell'incontro, partita per partita, per il gruppo della squadra. */
    fun noticeResult(t: com.tennis.scoremanager.data.Team, f: com.tennis.scoremanager.data.Fixture) {
        val (w, l) = com.tennis.scoremanager.data.Teams.result(f)
        val text = (listOf("${t.name} – ${f.opponent} $w-$l") + f.rubbers.mapIndexed { i, r ->
            val ours = r.ours.mapNotNull { playerById(it)?.name }.joinToString(" / ").ifBlank { "—" }
            "${rubberName(t, i)}: $ours – ${r.theirs.joinToString(" / ").ifBlank { "—" }} ${r.score}".trim()
        } + listOfNotNull(clubSign.ifBlank { null })).joinToString("\n")
        val now = System.currentTimeMillis()
        openNotice(com.tennis.scoremanager.data.NoticePlan(strings.tmResultTitle, "${t.name} – ${f.opponent}", text, t.roster.mapNotNull { playerById(it) }.map { com.tennis.scoremanager.data.Notices.recipient(it, text, now) }))
    }

    /** "Singolare 1", "Doppio 2". */
    fun rubberName(t: com.tennis.scoremanager.data.Team, i: Int): String = if (i < t.singles) strings.tmSingleN(i + 1) else strings.tmDoubleN(i - t.singles + 1)

    /** Gioca una partita dell'incontro col segnapunti: i nostri come Giocatore 1, gli avversari come Giocatore 2. */
    fun playTeamRubber(t: com.tennis.scoremanager.data.Team, f: com.tennis.scoremanager.data.Fixture, index: Int) {
        val r = f.rubbers.getOrNull(index) ?: return
        val ours = r.ours.map { playerById(it) }
        fun n(i: Int) = ours.getOrNull(i)?.name.orEmpty()
        fun id(i: Int) = r.ours.getOrNull(i).orEmpty()
        updateSetup {
            it.copy(
                club = storage.clubName.ifBlank { it.club }, doubles = r.doubles,
                p1a = n(0), p1b = if (r.doubles) n(1) else "", p2a = r.theirs.getOrNull(0).orEmpty(), p2b = if (r.doubles) r.theirs.getOrNull(1).orEmpty() else "",
                p1aId = id(0), p1bId = if (r.doubles) id(1) else "", p2aId = "", p2bId = "",
                p1aRank = ours.getOrNull(0)?.member?.ranking.orEmpty(), p1bRank = if (r.doubles) ours.getOrNull(1)?.member?.ranking.orEmpty() else "", p2aRank = "", p2bRank = "",
                event = t.championship.ifBlank { t.name }, category = "${t.name} – ${f.opponent}", round = rubberName(t, index),
            )
        }
        pendingTeamRubber = Triple(t.id, f.id, index)
        go(Screen.SETUP)
    }

    private fun linkTeamRubber(recordId: String, su: SetupData) {
        val (tid, fid, i) = pendingTeamRubber ?: return
        pendingTeamRubber = null
        val t = team(tid) ?: return
        val f = t.fixtures.firstOrNull { it.id == fid } ?: return
        val r = f.rubbers.getOrNull(i) ?: return
        // Solo se in campo c'è proprio il nostro giocatore di quella partita.
        if (r.ours.firstOrNull() != su.p1aId) return
        saveFixture(t, f.copy(rubbers = f.rubbers.mapIndexed { k, x -> if (k == i) x.copy(record = recordId) else x }))
    }

    private fun teamRubberFinished(rec: MatchRecord, state: com.tennis.scoremanager.model.MatchState) {
        for (t in teams.value) for (f in t.fixtures) {
            val i = f.rubbers.indexOfFirst { it.record == rec.id }
            if (i < 0) continue
            val won = when (state.winner) { Side.P1 -> true; Side.P2 -> false; else -> null }
            saveFixture(t, com.tennis.scoremanager.data.Teams.setRubber(f, i, com.tennis.scoremanager.data.Reports.scoreLine(state, Side.P1), won, rec.id))
            return
        }
    }

    // ---- Classifica a sfida ----

    val ladders = MutableStateFlow(storage.loadLadders())
    val openLadderId = MutableStateFlow<String?>(null)
    private var pendingLadder: Pair<String, String>? = null

    fun openLadders() { refreshPlayers(); screen.value = Screen.LADDERS }

    fun openLadder(id: String) { openLadderId.value = id; refreshPlayers(); screen.value = Screen.LADDER }

    fun ladder(id: String?): com.tennis.scoremanager.data.Ladder? = ladders.value.firstOrNull { it.id == id }

    fun saveLadder(l: com.tennis.scoremanager.data.Ladder) {
        val list = ladders.value.filter { it.id != l.id } + l
        ladders.value = list.sortedBy { it.createdAt }
        scope.launch(io) { storage.saveLadders(list) }
    }

    fun deleteLadder(id: String) {
        val list = ladders.value.filter { it.id != id }
        ladders.value = list
        scope.launch(io) { storage.saveLadders(list) }
    }

    /** La sfida col segnapunti: lo sfidante è il Giocatore 1, lo sfidato il Giocatore 2; il risultato torna da solo. */
    fun playChallenge(l: com.tennis.scoremanager.data.Ladder, ch: com.tennis.scoremanager.data.Challenge) {
        val a = playerById(ch.challenger); val b = playerById(ch.defender)
        updateSetup {
            it.copy(
                club = storage.clubName.ifBlank { it.club }, doubles = false,
                p1a = a?.name.orEmpty(), p1b = "", p2a = b?.name.orEmpty(), p2b = "",
                p1aId = ch.challenger, p1bId = "", p2aId = ch.defender, p2bId = "",
                p1aRank = a?.member?.ranking.orEmpty(), p1bRank = "", p2aRank = b?.member?.ranking.orEmpty(), p2bRank = "",
                event = l.name, category = strings.ldTitle, round = strings.ldPositions(ch.challengerPos, ch.defenderPos),
            )
        }
        pendingLadder = l.id to ch.id
        go(Screen.SETUP)
    }

    private fun linkLadderMatch(recordId: String, su: SetupData) {
        val (lid, cid) = pendingLadder ?: return
        pendingLadder = null
        val l = ladder(lid) ?: return
        val ch = l.challenges.firstOrNull { it.id == cid && it.open } ?: return
        // Solo se in campo ci sono proprio loro due.
        if (su.p1aId != ch.challenger || su.p2aId != ch.defender) return
        saveLadder(l.copy(challenges = l.challenges.map { if (it.id == cid) it.copy(record = recordId) else it }))
    }

    private fun ladderMatchFinished(rec: MatchRecord, state: com.tennis.scoremanager.model.MatchState) {
        for (l in ladders.value) {
            val ch = l.challenges.firstOrNull { it.open && it.record == rec.id } ?: continue
            val won = when (state.winner) { Side.P1 -> true; Side.P2 -> false; else -> return }
            saveLadder(com.tennis.scoremanager.data.Ladders.result(l, ch.id, com.tennis.scoremanager.data.Reports.scoreLine(state, Side.P1), won, System.currentTimeMillis(), rec.id))
            return
        }
    }

    /** L'avviso ai due giocatori: chi sfida chi e entro quando. */
    fun notifyChallenge(l: com.tennis.scoremanager.data.Ladder, ch: com.tennis.scoremanager.data.Challenge) {
        val s = strings
        val now = System.currentTimeMillis()
        val a = playerById(ch.challenger)?.name.orEmpty(); val b = playerById(ch.defender)?.name.orEmpty()
        val text = listOf(l.name, s.ldVs(a, b), s.ldBy(com.tennis.scoremanager.ui.screens.shortDate(ch.deadline, options.value.lang)), storage.clubName).filter { it.isNotBlank() }.joinToString("\n")
        val rec = listOfNotNull(playerById(ch.challenger), playerById(ch.defender)).map { com.tennis.scoremanager.data.Notices.recipient(it, text, now) }
        openNotice(com.tennis.scoremanager.data.NoticePlan(s.ldNoticeTitle, l.name, text, rec))
    }

    // ---- Scuola tennis ----

    val courses = MutableStateFlow(storage.loadCourses())
    val openCourseId = MutableStateFlow<String?>(null)

    fun openSchool() {
        refreshPlayers()
        screen.value = Screen.SCHOOL
    }

    fun openCourse(id: String) {
        openCourseId.value = id
        screen.value = Screen.COURSE
    }

    fun course(id: String?): com.tennis.scoremanager.data.Course? = id?.let { i -> courses.value.firstOrNull { it.id == i } }

    /** Salva il corso e rifà i suoi periodi non prenotabili nel planning (uno per orario). */
    fun saveCourse(c: com.tennis.scoremanager.data.Course) {
        val list = courses.value.filter { it.id != c.id } + c
        courses.value = list
        scope.launch(io) { storage.saveCourses(list) }
        val p = courtPlan.value
        val np = p.copy(blocks = p.blocks.filter { it.course != c.id } + com.tennis.scoremanager.data.Courses.blocks(c))
        if (np != p) {
            courtPlan.value = np
            scope.launch(io) { storage.saveCourtPlan(np) }
        }
    }

    /** Toglie il corso e i suoi periodi non prenotabili; le quote già addebitate restano nel registro. */
    fun deleteCourse(id: String) {
        val list = courses.value.filter { it.id != id }
        courses.value = list
        scope.launch(io) { storage.saveCourses(list) }
        val p = courtPlan.value.let { it.copy(blocks = it.blocks.filter { b -> b.course != id }) }
        courtPlan.value = p
        scope.launch(io) { storage.saveCourtPlan(p) }
    }

    /** I corsi di un giocatore (come allievo). */
    fun coursesOf(p: com.tennis.scoremanager.data.PlayerProfile): List<com.tennis.scoremanager.data.Course> {
        val keys = setOf(p.id) + p.aliases
        return courses.value.filter { c -> c.students.any { it in keys } }
    }

    /** "SAT Under 10 · novembre 2026" o "SAT Under 10 · stagione": la voce della quota nel registro. */
    fun courseFeeLabel(c: com.tennis.scoremanager.data.Course, month: Pair<Int, Int>?): String {
        if (month == null) return "${c.name} · ${strings.scSeason}"
        val cal = java.util.Calendar.getInstance().apply { set(month.first, month.second, 1) }
        return "${c.name} · " + java.text.SimpleDateFormat("LLLL yyyy", options.value.lang.locale).format(cal.time)
    }

    /** Addebita la quota del mese (o della stagione) a chi non l'ha ancora; restituisce quanti addebiti nuovi. */
    fun chargeCourse(c: com.tennis.scoremanager.data.Course, month: Pair<Int, Int>?): Int {
        val due = month?.let { (y, m) -> java.util.Calendar.getInstance().apply { set(y, m, 10, 12, 0, 0) }.timeInMillis } ?: c.from
        val (book, n) = com.tennis.scoremanager.data.Courses.charge(clubBook.value, c, players.value, courseFeeLabel(c, month), System.currentTimeMillis(), due)
        if (n > 0) saveClubBook(book)
        return n
    }

    /** Avvisa gli allievi del corso: lezione annullata ([lesson]) o un messaggio libero. Ai minorenni scrive al genitore. */
    fun noticeCourse(c: com.tennis.scoremanager.data.Course, lesson: com.tennis.scoremanager.data.Lesson?) {
        val s = strings; val lang = options.value.lang; val now = System.currentTimeMillis()
        val people = c.students.mapNotNull { playerById(it) }
        val text = if (lesson != null) (listOf(s.scCancelledNotice(c.name), com.tennis.scoremanager.data.Notices.day(lesson.start, lang) + ", " + com.tennis.scoremanager.data.Reports.time(lesson.start, lang) + listOf(lesson.court).filter { it.isNotBlank() }.joinToString { " · $it" })
            + listOfNotNull(clubSign.ifBlank { null })).joinToString("\n") else "${c.name}\n\n"
        val rec = people.map { com.tennis.scoremanager.data.Notices.recipient(it, if (lesson != null) text else "", now) }
        openNotice(com.tennis.scoremanager.data.NoticePlan(if (lesson != null) s.scCancelledTitle else s.ntKindFree, c.name, text, rec, editable = lesson == null))
    }

    // ---- Planning dei campi ----

    val courtPlan = MutableStateFlow(storage.loadCourtPlan())

    fun openCourtsPlan() {
        refreshPlayers()
        screen.value = Screen.COURTS_PLAN
    }

    fun saveCourtSettings(st: com.tennis.scoremanager.data.CourtSettings) {
        val p = courtPlan.value.copy(settings = st)
        courtPlan.value = p
        scope.launch(io) { storage.saveCourtPlan(p) }
    }

    private fun putBookings(list: List<com.tennis.scoremanager.data.CourtBooking>, remove: Set<String> = emptySet()) {
        val ids = list.map { it.id }.toSet() + remove
        val p = courtPlan.value.copy(bookings = courtPlan.value.bookings.filter { it.id !in ids } + list)
        courtPlan.value = p
        scope.launch(io) { storage.saveCourtPlan(p) }
    }

    /** "Campo 1 · sab 14 nov 18:00": la descrizione negli addebiti e negli avvisi. */
    fun bookingLabel(b: com.tennis.scoremanager.data.CourtBooking): String {
        val lang = options.value.lang
        val loc = lang.locale
        val day = java.text.SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(loc, "EEEdMMM"), loc).format(java.util.Date(b.start))
        return "${b.court} · $day ${com.tennis.scoremanager.data.Reports.time(b.start, lang)}"
    }

    /**
     * Salva una prenotazione (nuova o cambiata), ripetuta per [weeks] settimane; con [charge] il prezzo si divide tra
     * i giocatori del database e va nel registro. Restituisce le date saltate perché il campo era occupato.
     */
    fun saveBooking(b: com.tennis.scoremanager.data.CourtBooking, weeks: Int, charge: Boolean): List<Long> {
        val (list, skipped) = if (weeks > 1) com.tennis.scoremanager.data.CourtPlans.weekly(courtPlan.value, clubTournaments.value, b, weeks) else listOf(b) to emptyList()
        val old = courtPlan.value.bookings.firstOrNull { it.id == b.id }
        // Una prenotazione online spostata (campo, ora, durata) diventa della segreteria: sul server si toglie.
        val keepOnline = old?.online?.takeIf { old.court == b.court && old.start == b.start && old.minutes == b.minutes }
        old?.online?.let { id -> if (keepOnline == null) saveServer { it.copy(cancel = (it.cancel + id).distinct()) } }
        var book = clubBook.value
        // Cambiando una prenotazione, gli addebiti di prima non pagati si tolgono e si rifanno.
        old?.charges?.forEach { id -> if (book.charges.firstOrNull { it.id == id }?.payments.isNullOrEmpty()) book = com.tennis.scoremanager.data.Fees.cancel(book, id) }
        val now = System.currentTimeMillis()
        val saved = list.map { x ->
            val people = x.players.mapNotNull { playerById(it) }
            if (!charge || x.cents <= 0 || people.isEmpty()) x.copy(charges = old?.takeIf { it.id == x.id }?.charges?.filter { id -> book.charges.any { it.id == id } }.orEmpty())
            else {
                val parts = com.tennis.scoremanager.data.CourtPlans.split(x.cents, people.size)
                val before = book.charges.map { it.id }.toSet()
                people.forEachIndexed { i, p -> book = com.tennis.scoremanager.data.Fees.charge(book, p, bookingLabel(x), parts[i], now) }
                x.copy(charges = book.charges.map { it.id }.filter { it !in before })
            }
        }
        saveClubBook(book)
        putBookings(saved.map { it.copy(online = if (it.id == b.id) keepOnline else null) })
        return skipped
    }

    /** Com'è fatto un campo e i suoi prezzi. */
    /** I modi di pagamento del circolo (quelli proposti finché non se ne sceglie uno). */
    fun payTypes(): List<com.tennis.scoremanager.data.PayType> = com.tennis.scoremanager.data.Fees.types(clubBook.value) { com.tennis.scoremanager.ui.payMethodName(strings, it) }

    fun savePayTypes(list: List<com.tennis.scoremanager.data.PayType>) = saveClubBook(clubBook.value.copy(payTypes = list))

    fun saveCourtInfo(info: com.tennis.scoremanager.data.CourtInfo) {
        val st = courtPlan.value.settings
        saveCourtSettings(st.copy(courts = st.courts.filter { it.name != info.name } + info))
    }

    /** I campi del circolo, nell'ordine in cui si vedono (Nuova partita, planning, tornei). */
    val clubCourts = MutableStateFlow(storage.clubCourts)

    fun setClubCourts(list: List<String>) {
        val clean = list.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        storage.clubCourts = clean
        clubCourts.value = clean
    }

    /** Un campo nuovo con le sue caratteristiche; false se il nome c'è già. */
    fun addCourt(info: com.tennis.scoremanager.data.CourtInfo): Boolean {
        val name = info.name.trim()
        if (name.isEmpty() || clubCourts.value.any { it.equals(name, ignoreCase = true) }) return false
        setClubCourts(clubCourts.value + name)
        saveCourtInfo(info.copy(name = name))
        return true
    }

    /**
     * Rinomina un campo: lo seguono caratteristiche e prezzi, prenotazioni, periodi non prenotabili e partite dei
     * tornei. false se il nome nuovo è già di un altro campo.
     */
    fun renameCourt(old: String, new: String): Boolean {
        val n = new.trim()
        if (n.isEmpty()) return false
        if (n == old) return true
        if (clubCourts.value.any { it != old && it.equals(n, ignoreCase = true) }) return false
        setClubCourts(clubCourts.value.map { if (it == old) n else it })
        val p = courtPlan.value
        val np = p.copy(
            settings = p.settings.copy(courts = p.settings.courts.map { if (it.name == old) it.copy(name = n) else it }),
            bookings = p.bookings.map { if (it.court == old) it.copy(court = n) else it },
            blocks = p.blocks.map { b -> b.copy(courts = b.courts.map { if (it == old) n else it }) },
        )
        courtPlan.value = np
        scope.launch(io) { storage.saveCourtPlan(np) }
        for (t in clubTournaments.value) if (t.matches.any { it.court == old }) saveClubTournament(t.copy(matches = t.matches.map { if (it.court == old) it.copy(court = n) else it }))
        return true
    }

    /** Le prenotazioni da oggi in poi su quel campo (quelle che sparirebbero eliminandolo). */
    fun futureBookings(court: String): Int {
        val today = com.tennis.scoremanager.data.CourtPlans.dayStart(System.currentTimeMillis())
        return courtPlan.value.bookings.count { it.court == court && it.start >= today }
    }

    /** Elimina un campo: via le sue prenotazioni future (con gli addebiti non pagati) e le sue caratteristiche. */
    fun deleteCourt(court: String) {
        courtPlan.value.settings.info(court).photos.forEach { n -> scope.launch(io) { courtPhotoFile(n).delete() } }
        val today = com.tennis.scoremanager.data.CourtPlans.dayStart(System.currentTimeMillis())
        courtPlan.value.bookings.filter { it.court == court && it.start >= today }.forEach { deleteBooking(it, false) }
        setClubCourts(clubCourts.value - court)
        val p = courtPlan.value
        val np = p.copy(
            settings = p.settings.copy(courts = p.settings.courts.filter { it.name != court }),
            blocks = p.blocks.mapNotNull { b -> if (court !in b.courts) b else b.copy(courts = b.courts - court).takeIf { it.courts.isNotEmpty() } },
        )
        courtPlan.value = np
        scope.launch(io) { storage.saveCourtPlan(np) }
    }

    /** La cartella delle foto dei campi (nei dati dell'app: va nel backup). */
    private val courtPhotosDir: File get() = File(app.filesDir, "court_photos").apply { mkdirs() }

    fun courtPhotoFile(name: String): File = File(courtPhotosDir, name)

    /** Aggiunge al campo le foto scelte, ridotte (lato lungo 1600 px) e in JPEG. */
    fun addCourtPhotos(court: String, uris: List<android.net.Uri>) {
        if (uris.isEmpty()) return
        scope.launch {
            val names = withContext(io) {
                uris.mapNotNull { uri ->
                    runCatching {
                        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        app.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
                        var sample = 1
                        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1600) sample *= 2
                        val bmp = app.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return@runCatching null
                        val k = 1600f / maxOf(bmp.width, bmp.height)
                        val out = if (k < 1f) android.graphics.Bitmap.createScaledBitmap(bmp, (bmp.width * k).toInt(), (bmp.height * k).toInt(), true) else bmp
                        val name = "c" + java.util.UUID.randomUUID().toString().take(12) + ".jpg"
                        courtPhotoFile(name).outputStream().use { out.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, it) }
                        if (out !== bmp) out.recycle()
                        bmp.recycle()
                        name
                    }.getOrNull()
                }
            }
            val info = courtPlan.value.settings.info(court)
            saveCourtInfo(info.copy(photos = info.photos + names))
        }
    }

    fun removeCourtPhoto(court: String, name: String) {
        val info = courtPlan.value.settings.info(court)
        saveCourtInfo(info.copy(photos = info.photos - name))
        scope.launch(io) { courtPhotoFile(name).delete() }
    }

    /** Sposta un campo di un posto nell'elenco ([up] = verso l'inizio). */
    fun moveCourt(court: String, up: Boolean) {
        val l = clubCourts.value.toMutableList()
        val i = l.indexOf(court); val j = if (up) i - 1 else i + 1
        if (i < 0 || j !in l.indices) return
        l[i] = l[j].also { l[j] = l[i] }
        setClubCourts(l)
    }

    /** Salva (o toglie, con [delete]) un periodo di campi non prenotabili. */
    fun saveCourtBlock(b: com.tennis.scoremanager.data.CourtBlock, delete: Boolean = false) {
        val p = courtPlan.value.copy(blocks = courtPlan.value.blocks.filter { it.id != b.id } + listOfNotNull(b.takeIf { !delete }))
        courtPlan.value = p
        scope.launch(io) { storage.saveCourtPlan(p) }
    }

    /**
     * Toglie la prenotazione (o tutta la serie da quella in poi); gli addebiti non pagati spariscono. Quelle fatte
     * online si tolgono anche dal server ([fromServer]: le ha già tolte lui).
     */
    fun deleteBooking(b: com.tennis.scoremanager.data.CourtBooking, wholeSeries: Boolean, fromServer: Boolean = false) {
        val gone = if (wholeSeries && b.series != null) courtPlan.value.bookings.filter { it.series == b.series && it.start >= b.start } else listOf(b)
        if (!fromServer) gone.mapNotNull { it.online }.takeIf { it.isNotEmpty() }?.let { ids -> saveServer { it.copy(cancel = (it.cancel + ids).distinct()) } }
        var book = clubBook.value
        gone.flatMap { it.charges }.forEach { id -> if (book.charges.firstOrNull { it.id == id }?.payments.isNullOrEmpty()) book = com.tennis.scoremanager.data.Fees.cancel(book, id) }
        saveClubBook(book)
        putBookings(emptyList(), gone.map { it.id }.toSet())
    }

    // ---- Server del circolo (facoltativo) ----

    /** Il server del circolo per le prenotazioni dei giocatori; non abbinato = si prenota solo in segreteria. */
    val clubServer = MutableStateFlow(storage.loadClubServer())
    private val serverLock = kotlinx.coroutines.sync.Mutex()

    private fun saveServer(f: (com.tennis.scoremanager.data.ClubServerConf) -> com.tennis.scoremanager.data.ClubServerConf) {
        val n = synchronized(clubServer) { f(clubServer.value).also { clubServer.value = it } }
        scope.launch(io) { storage.saveClubServer(n) }
    }

    init {
        // Il controllo in background (notifiche delle prenotazioni online) solo con un server abbinato e gli avvisi accesi.
        scope.launch {
            clubServer.map { it.paired && it.alerts }.distinctUntilChanged().collect { com.tennis.scoremanager.service.ServerCheckJob.schedule(app, it) }
        }
        // Ogni cambio del planning, dei tornei, dei campi o delle regole va al server (dopo qualche secondo di calma).
        scope.launch {
            combine(courtPlan, clubTournaments, clubCourts, clubServer.map { Triple(it.paired, it.rules, it.payInfo) }.distinctUntilChanged()) { _, _, _, _ -> Unit }
                .collectLatest { delay(3_000); if (clubServer.value.paired) syncServerNow() }
        }
    }

    /** Abbina la segreteria al server con il codice che il server ha scritto all'avvio. null = fatto, altrimenti il codice dell'errore. */
    suspend fun pairServer(url: String, code: String): String? {
        val u = com.tennis.scoremanager.data.ClubServers.cleanUrl(url)
        if (u.isEmpty()) return "BAD_URL"
        return try {
            val token = withContext(Dispatchers.IO) {
                com.tennis.scoremanager.data.ClubServerClient(u, null).info()
                com.tennis.scoremanager.data.ClubServerClient(u, null).pair(code).token
            }
            saveServer { it.copy(url = u, token = token, error = null, lastSync = 0, players = emptyList(), cancel = emptyList(), seen = null) }
            syncServerNow()
        } catch (e: com.tennis.scoremanager.data.ClubServerError) { e.code }
    }

    /**
     * Manda il planning al server e prende le prenotazioni online: le nuove entrano nel planning, quelle disdette
     * escono. Prima toglie dal server quelle tolte in segreteria. null = fatto, altrimenti il codice dell'errore.
     */
    suspend fun syncServerNow(): String? = serverLock.withLock {
        val conf = clubServer.value
        if (!conf.paired) return@withLock null
        val srv = com.tennis.scoremanager.data.ClubServers
        try {
            for (id in conf.cancel) {
                withContext(Dispatchers.IO) { try { srv.withServer(conf) { it.cancel(id) } } catch (e: com.tennis.scoremanager.data.ClubServerError) { if (e.code != "NOT_FOUND") throw e } }
                saveServer { it.copy(cancel = it.cancel - id) }
            }
            val sync = srv.buildSync(conf, courtPlan.value, clubTournaments.value, clubCourts.value, storage.clubName, java.util.TimeZone.getDefault().id)
            val st = withContext(Dispatchers.IO) { srv.withServer(conf) { it.sync(sync) } }
            val (added, gone) = com.tennis.scoremanager.data.ClubServers.merge(courtPlan.value, st.bookings, { playerById(it) != null }, System.currentTimeMillis())
            gone.forEach { deleteBooking(it, wholeSeries = false, fromServer = true) }
            if (added.isNotEmpty()) putBookings(added)
            // Le novità per la segreteria: chi ha prenotato o disdetto online da quando si è guardato l'ultima volta.
            val news = com.tennis.scoremanager.data.ClubServers.onlineNews(clubServer.value.seen, st.bookings, System.currentTimeMillis(), conf.cancel.toSet()) { b ->
                listOf(b.name, b.court, bookingLabel(com.tennis.scoremanager.data.CourtBooking("", b.court, b.start)).substringAfter(" · ")).joinToString(" · ")
            }
            if (clubServer.value.alerts) com.tennis.scoremanager.service.OnlineAlerts.post(app, strings, news.added, news.cancelled, news.firstDay)
            saveServer { it.copy(lastSync = System.currentTimeMillis(), error = null, players = st.players, club = sync.club, seen = news.seen) }
            null
        } catch (e: com.tennis.scoremanager.data.ClubServerError) {
            saveServer { it.copy(error = e.code) }
            e.code
        }
    }

    fun setServerAlerts(on: Boolean) = saveServer { it.copy(alerts = on) }

    /** Il giorno su cui aprire il planning (dalla notifica di una prenotazione online). */
    val planDayRequest = MutableStateFlow<Long?>(null)

    fun openPlanAt(day: Long) {
        planDayRequest.value = day
        openCourtsPlan()
    }

    fun saveServerRules(rules: com.tennis.scoremanager.data.BookingRules, payInfo: String, publicUrl: String = clubServer.value.publicUrl) =
        saveServer { it.copy(rules = rules, payInfo = payInfo.trim(), publicUrl = com.tennis.scoremanager.data.ClubServers.cleanUrl(publicUrl)) }

    /** L'invito per un giocatore: codice e scadenza (il link lo fa [com.tennis.scoremanager.data.ClubServers.inviteLink]). */
    suspend fun serverInvite(p: com.tennis.scoremanager.data.PlayerProfile): Result<com.tennis.scoremanager.data.SrvInvite> {
        val conf = clubServer.value
        if (!conf.paired) return Result.failure(com.tennis.scoremanager.data.ClubServerError("NOT_PAIRED"))
        return try {
            Result.success(withContext(Dispatchers.IO) { com.tennis.scoremanager.data.ClubServers.withServer(conf) { it.invite(p.id, p.name) } })
        } catch (e: com.tennis.scoremanager.data.ClubServerError) { Result.failure(e) }
    }

    /** Il giocatore non prenota più online (le sue prenotazioni restano nel planning). */
    suspend fun serverRemovePlayer(id: String): String? {
        val conf = clubServer.value
        return try {
            withContext(Dispatchers.IO) { com.tennis.scoremanager.data.ClubServers.withServer(conf) { it.removePlayer(id) } }
            saveServer { it.copy(players = it.players.filter { p -> p.id != id }) }
            null
        } catch (e: com.tennis.scoremanager.data.ClubServerError) { e.code }
    }

    /** Scollega il server: le prenotazioni online già nel planning restano, come quelle della segreteria. */
    fun unpairServer() {
        saveServer { com.tennis.scoremanager.data.ClubServerConf(rules = it.rules, payInfo = it.payInfo, publicUrl = it.publicUrl, alerts = it.alerts) }
        val p = courtPlan.value
        if (p.bookings.any { it.online != null }) {
            val np = p.copy(bookings = p.bookings.map { it.copy(online = null) })
            courtPlan.value = np
            scope.launch(io) { storage.saveCourtPlan(np) }
        }
    }

    // ---- Prenota un campo (il giocatore, col server del suo circolo) ----

    val bookingAccount = MutableStateFlow(storage.loadBookingAccount())

    private fun saveBookingAccount(a: com.tennis.scoremanager.data.BookingAccount) {
        bookingAccount.value = a
        scope.launch(io) { storage.saveBookingAccount(a) }
    }

    /**
     * Entra nel circolo con l'invito: il link o il QR (con dentro l'indirizzo), oppure il codice e [url].
     * null = fatto, altrimenti il codice dell'errore.
     */
    suspend fun joinBooking(invite: String, url: String): String? {
        val (u0, code) = com.tennis.scoremanager.data.ClubServers.parseInvite(invite) ?: return "BAD_CODE"
        val u = u0 ?: com.tennis.scoremanager.data.ClubServers.cleanUrl(url).ifEmpty { return "NEED_URL" }
        return try {
            val (t, me) = withContext(Dispatchers.IO) {
                val t = com.tennis.scoremanager.data.ClubServerClient(u, null).claim(code)
                t to com.tennis.scoremanager.data.ClubServerClient(u, t.token).me()
            }
            saveBookingAccount(com.tennis.scoremanager.data.BookingAccount(u, t.token, t.name, t.player, me.club))
            null
        } catch (e: com.tennis.scoremanager.data.ClubServerError) { e.code }
    }

    /**
     * Una chiamata del giocatore al server del suo circolo. Se il server non riconosce più la chiave (accesso
     * tolto dalla segreteria) si esce dal circolo: serve un nuovo invito.
     */
    suspend fun <T> booking(f: (com.tennis.scoremanager.data.ClubServerClient) -> T): Result<T> {
        val a = bookingAccount.value
        if (!a.joined) return Result.failure(com.tennis.scoremanager.data.ClubServerError("UNAUTHORIZED"))
        return try {
            Result.success(withContext(Dispatchers.IO) { f(com.tennis.scoremanager.data.ClubServerClient(a.url, a.token)) })
        } catch (e: com.tennis.scoremanager.data.ClubServerError) {
            if (e.code == "UNAUTHORIZED") saveBookingAccount(com.tennis.scoremanager.data.BookingAccount())
            Result.failure(e)
        }
    }

    init {
        // Promemoria e avvisi del giocatore anche ad app chiusa, solo se è entrato in un circolo e li vuole.
        scope.launch {
            bookingAccount.map { it.joined && it.reminders }.distinctUntilChanged().collect { com.tennis.scoremanager.service.ServerCheckJob.schedulePlayer(app, it) }
        }
    }

    fun setBookingReminders(on: Boolean) = saveBookingAccount(bookingAccount.value.copy(reminders = on))

    /** Disdetta da qui: non è una notizia da dare al giocatore. */
    fun noteMyCancel(id: String) = saveBookingAccount(bookingAccount.value.copy(cancelledHere = (bookingAccount.value.cancelledHere + id).distinct()))

    /** Il controllo del giocatore (in background): promemoria della partita e prenotazioni tolte dalla segreteria. */
    suspend fun checkMyBookings() {
        val a = bookingAccount.value
        if (!a.joined) return
        val me = booking { it.me() }.getOrNull() ?: return
        val now = System.currentTimeMillis()
        val news = com.tennis.scoremanager.data.ClubServers.myNews(bookingAccount.value, me.bookings, me.rules.remindHours, now) { b ->
            bookingLabel(com.tennis.scoremanager.data.CourtBooking("", b.court, b.start))
        }
        val (freed, acc) = com.tennis.scoremanager.data.ClubServers.freedNews(news.account, me.waits)
        val seeks = booking { it.seeks() }.getOrNull()
        val sk = seeks?.let { com.tennis.scoremanager.data.ClubServers.seekNews(acc, it) }
        saveBookingAccount(sk?.account ?: acc)
        if (a.reminders) {
            com.tennis.scoremanager.service.OnlineAlerts.postPlayer(
                app, strings, news.remind.map { it.label }, news.removed.map { it.label },
                freed.map { w -> bookingLabel(com.tennis.scoremanager.data.CourtBooking("", w.freedCourt!!, w.start)) },
            )
            if (sk != null) com.tennis.scoremanager.service.OnlineAlerts.postSeeks(
                app, strings,
                sk.joined.map { it.players.last() + " · " + it.date },
                sk.booked.map { bookingLabel(com.tennis.scoremanager.data.CourtBooking("", it.bookedCourt!!, it.bookedStart!!)) + " · " + it.players.joinToString(", ") },
                sk.noCourt.map { it.date + " · " + it.players.joinToString(", ") },
            )
        }
    }

    /** Esce dal circolo su questo telefono (per rientrare serve un nuovo invito). */
    fun leaveBooking() {
        val a = bookingAccount.value
        scope.launch {
            if (a.joined) runCatching { withContext(Dispatchers.IO) { com.tennis.scoremanager.data.ClubServerClient(a.url, a.token).logout() } }
        }
        saveBookingAccount(com.tennis.scoremanager.data.BookingAccount())
    }

    /** Il campo e com'è fatto: "Campo 1 · terra · coperto". */
    fun courtLine(court: String): String {
        val i = courtPlan.value.settings.info(court)
        val s = strings
        return listOfNotNull(court, i.surface?.let { com.tennis.scoremanager.ui.screens.surfaceName(s, it) }, if (i.covered) s.crtCovered else null).joinToString(" · ")
    }

    /** Registra il pagamento di tutti gli addebiti aperti della prenotazione (al campo o con Satispay). */
    fun payBooking(b: com.tennis.scoremanager.data.CourtBooking, type: com.tennis.scoremanager.data.PayType) {
        var book = clubBook.value
        val now = System.currentTimeMillis()
        for (id in b.charges) {
            val ch = book.charges.firstOrNull { it.id == id } ?: continue
            if (ch.left > 0) com.tennis.scoremanager.data.Fees.pay(book, id, ch.left, now, type.method, "", type.name)?.let { book = it.first }
        }
        saveClubBook(book)
    }

    /** Avvisa i giocatori della prenotazione: campo, giorno e ora, e come si paga. */
    fun noticeBooking(b: com.tennis.scoremanager.data.CourtBooking) {
        val s = strings; val now = System.currentTimeMillis(); val lang = options.value.lang
        val heads = (b.players.size + b.names.size).coerceAtLeast(1)
        val each = com.tennis.scoremanager.data.Fees.money(com.tennis.scoremanager.data.CourtPlans.split(b.cents, heads).firstOrNull() ?: 0, lang.locale)
        val pay = if (b.cents <= 0) emptyList() else if (b.payWhere == com.tennis.scoremanager.data.PayWhere.ONLINE)
            listOf(s.ntPayOnline(each)) + com.tennis.scoremanager.data.Notices.payLines(s, clubBook.value) else listOf(s.ntPayCourt(each))
        val text = (listOf(s.bkNotice("${courtLine(b.court)} · ${bookingLabel(b).substringAfter(" · ")}"), (b.names + b.players.mapNotNull { playerById(it)?.name }).distinct().joinToString(" · "))
            + pay + listOfNotNull(b.note.ifBlank { null }, clubSign.ifBlank { null })).joinToString("\n")
        val rec = b.players.mapNotNull { playerById(it) }.map { com.tennis.scoremanager.data.Notices.recipient(it, text, now) }
        openNotice(com.tennis.scoremanager.data.NoticePlan(s.bkTitle, bookingLabel(b), text, rec))
    }

    // ---- Avvisi (WhatsApp, SMS, email, condivisione) ----

    val notice = MutableStateFlow<com.tennis.scoremanager.data.NoticePlan?>(null)
    private var noticeBack = Screen.HOME

    private fun openNotice(plan: com.tennis.scoremanager.data.NoticePlan) {
        notice.value = plan
        noticeBack = screen.value
        screen.value = Screen.NOTICE
    }

    private fun entryNameOf(t: com.tennis.scoremanager.data.ClubTournament, id: String?): String =
        t.entries.firstOrNull { it.id == id }?.let { e -> e.players.mapIndexed { i, p -> playerById(p)?.name ?: e.names.getOrNull(i).orEmpty() }.joinToString(" / ") } ?: "—"

    private fun peopleOf(t: com.tennis.scoremanager.data.ClubTournament, entryIds: Collection<String?>): List<com.tennis.scoremanager.data.PlayerProfile> =
        t.entries.filter { it.id in entryIds }.flatMap { it.players }.distinct().mapNotNull { playerById(it) }

    private val clubSign: String get() = storage.clubName

    /** A ogni iscritto: iscrizione confermata e quota pagata o da pagare. */
    fun noticeEntries(t: com.tennis.scoremanager.data.ClubTournament) {
        val s = strings; val lang = options.value.lang; val now = System.currentTimeMillis()
        val people = peopleOf(t, t.entries.map { it.id })
        val rec = people.map { p -> com.tennis.scoremanager.data.Notices.recipient(p, com.tennis.scoremanager.data.Notices.entryText(s, t, p, clubBook.value, clubSign, lang, now), now) }
        // Per il gruppo: l'elenco degli iscritti.
        val common = (listOf(s.ntEntriesList(t.name)) + t.entries.map { "• " + entryNameOf(t, it.id) } + listOfNotNull(clubSign.ifBlank { null })).joinToString("\n")
        openNotice(com.tennis.scoremanager.data.NoticePlan(s.ntKindEntries, t.name, common, rec))
    }

    /**
     * Le partite da giocare: a ciascun giocatore la sua (turno, avversario, giorno, ora, campo); per il gruppo
     * l'ordine di gioco. [day] = solo quel giorno, null = tutte quelle già con i due giocatori.
     */
    fun noticeMatches(t: com.tennis.scoremanager.data.ClubTournament, day: Long?) {
        val s = strings; val lang = options.value.lang; val now = System.currentTimeMillis()
        val open = t.matches.filter { !it.played && it.a != null && it.b != null }
        val ms = if (day != null) com.tennis.scoremanager.data.Notices.onDay(open, day) else open
        val names = { id: String? -> entryNameOf(t, id) }
        val rec = ms.flatMap { m ->
            peopleOf(t, listOf(m.a, m.b)).map { p ->
                com.tennis.scoremanager.data.Notices.recipient(p, com.tennis.scoremanager.data.Notices.matchText(s, t, m, clubRoundName(t, m), names, p, clubSign, lang, now), now)
            }
        }
        val title = if (day != null) s.ntOrderTitle(t.name, com.tennis.scoremanager.data.Notices.day(day, lang)) else s.ntOrderAll(t.name)
        val common = com.tennis.scoremanager.data.Notices.orderOfPlay(s, t, ms, { clubRoundName(t, it) }, names, title, lang, clubSign)
        openNotice(com.tennis.scoremanager.data.NoticePlan(if (day != null) s.ntKindOrder else s.ntKindNext, title, common, rec))
    }

    /** Il tabellone in un'immagine, per il gruppo e i social; a ciascuno lo stesso testo. */
    fun noticeDraw(t: com.tennis.scoremanager.data.ClubTournament) {
        val s = strings; val now = System.currentTimeMillis()
        val dir = File(app.cacheDir, "share").apply { mkdirs() }
        val f = File(dir, "${s.ctTabDraw} ${t.name}.png".replace(Regex("[\\\\/:*?\"<>|]"), "_"))
        val sub = listOfNotNull(clubSign.ifBlank { null }, t.category.ifBlank { null }, if (t.kind == com.tennis.scoremanager.data.CTKind.FITP) "FITP" else s.ctSocial).joinToString(" · ")
        val ok = runCatching {
            val b = com.tennis.scoremanager.data.ClubDrawImage.render(t, { entryNameOf(t, it) }, s, sub, s.generatedWith)
            f.outputStream().use { b.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            b.recycle()
        }.isSuccess
        val common = (listOf(s.ntDrawText(t.name)) + t.champion?.let { listOf(s.ctChampion(entryNameOf(t, it))) }.orEmpty() + listOfNotNull(clubSign.ifBlank { null })).joinToString("\n")
        val rec = peopleOf(t, t.entries.map { it.id }).map { com.tennis.scoremanager.data.Notices.recipient(it, common, now) }
        openNotice(com.tennis.scoremanager.data.NoticePlan(s.ntKindDraw, t.name, common, rec, image = f.takeIf { ok }))
    }

    /** Messaggio libero agli iscritti: il testo si scrive nella schermata degli avvisi. */
    fun noticeFreeTournament(t: com.tennis.scoremanager.data.ClubTournament) {
        val now = System.currentTimeMillis()
        val rec = peopleOf(t, t.entries.map { it.id }).map { com.tennis.scoremanager.data.Notices.recipient(it, "", now) }
        openNotice(com.tennis.scoremanager.data.NoticePlan(strings.ntKindFree, t.name, "${t.name}\n\n", rec, editable = true))
    }

    /** Sollecito a chi deve qualcosa al circolo, voce per voce. */
    fun noticeDues() {
        val s = strings; val lang = options.value.lang; val now = System.currentTimeMillis()
        val book = clubBook.value
        val rec = players.value.mapNotNull { p ->
            val open = com.tennis.scoremanager.data.Fees.of(book, p).filter { it.left > 0 }
            if (open.isEmpty()) null else com.tennis.scoremanager.data.Notices.recipient(p, com.tennis.scoremanager.data.Notices.duesText(s, p, open, clubSign, lang, now, com.tennis.scoremanager.data.Notices.payLines(s, book)), now)
        }
        openNotice(com.tennis.scoremanager.data.NoticePlan(s.ntDuesButton, s.paymentsTitle, "", rec))
    }

    /** Ai soci che hanno qualcosa da sistemare (certificato, tessera, consensi…), a ciascuno il suo. */
    fun noticeMembersTodo() {
        val s = strings; val lang = options.value.lang; val now = System.currentTimeMillis()
        val rec = players.value.filter { it.member != null }.mapNotNull { p ->
            val issues = com.tennis.scoremanager.data.Members.issues(p, now).filter { it != com.tennis.scoremanager.data.Members.Issue.NO_CONTACT }
            if (issues.isEmpty()) null
            else com.tennis.scoremanager.data.Notices.recipient(p, com.tennis.scoremanager.data.Notices.memberText(s, p, issues.map { com.tennis.scoremanager.ui.screens.issueText(s, p, it, now, lang) }, clubSign, now), now)
        }
        openNotice(com.tennis.scoremanager.data.NoticePlan(s.ntMembersTodo, s.membersTitle, "", rec))
    }

    /** Messaggio libero a tutti i soci. */
    fun noticeMembersAll() {
        val now = System.currentTimeMillis()
        val rec = players.value.filter { it.member != null }.map { com.tennis.scoremanager.data.Notices.recipient(it, "", now) }
        openNotice(com.tennis.scoremanager.data.NoticePlan(strings.ntMembersAll, clubSign.ifBlank { strings.membersTitle }, clubSign.ifBlank { "" }.let { if (it.isBlank()) "" else "$it\n\n" }, rec, editable = true))
    }

    /** Ricevuta (non fiscale) di un pagamento, in PDF da condividere o stampare. */
    fun receiptFile(chargeId: String, paymentId: String): File? {
        val c = clubBook.value.charges.firstOrNull { it.id == chargeId } ?: return null
        val p = c.payments.firstOrNull { it.id == paymentId } ?: return null
        val s = strings
        val dir = File(app.cacheDir, "share").apply { mkdirs() }
        val f = File(dir, "${s.receiptTitle} ${p.receipt.replace('/', '-')} ${c.name}.pdf".replace(Regex("[\\\\/:*?\"<>|]"), "_"))
        return runCatching {
            f.outputStream().use { com.tennis.scoremanager.data.ReceiptPdf.write(c, p, storage.clubName, s, options.value.lang, it) }
            f
        }.getOrNull()
    }

    /** I pagamenti di un anno in CSV, per il commercialista. */
    fun paymentsCsv(year: Int): File? {
        val s = strings
        val lang = options.value.lang
        val dir = File(app.cacheDir, "share").apply { mkdirs() }
        val f = File(dir, "${s.paymentsCsvName} $year.csv")
        return runCatching {
            // Date in cifre (01/11/2026): il foglio di calcolo le riconosce come date.
            val day = java.text.SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(lang.locale, "ddMMyyyy"), lang.locale)
            val text = com.tennis.scoremanager.data.Fees.csv(clubBook.value, year, s.paymentsCsvHeader, { com.tennis.scoremanager.ui.payMethodName(s, it) }, { day.format(java.util.Date(it)) }, lang.locale)
            // Il BOM fa riconoscere al foglio di calcolo le lettere accentate.
            f.writeText("\uFEFF" + text)
            f
        }.getOrNull()
    }

    fun saveDrill(d: com.tennis.scoremanager.data.Drill) {
        val list = drills.value.filter { it.id != d.id } + d
        drills.value = list
        scope.launch(io) { storage.saveDrills(list) }
    }

    fun deleteDrill(id: String) {
        val list = drills.value.filter { it.id != id }
        drills.value = list
        if (trainDrill.value == id) trainDrill.value = null
        scope.launch(io) { storage.saveDrills(list) }
    }

    // ---------------------------------------------------------------- ruoli

    /** I ruoli scelti (almeno uno) e quello attivo, che decide le schede della prima schermata. */
    val roles = MutableStateFlow(storage.roles.mapNotNull { r -> Role.entries.firstOrNull { it.name == r } }.toSet().ifEmpty { setOf(Role.PLAYER) })
    val activeRole = MutableStateFlow(Role.entries.firstOrNull { it.name == storage.activeRole }?.takeIf { it in roles.value } ?: roles.value.first())

    /** Cambia i ruoli; se quello attivo non c'è più si passa al primo rimasto. Almeno uno resta sempre. */
    fun setRoles(set: Set<Role>) {
        val v = set.ifEmpty { setOf(Role.PLAYER) }
        roles.value = Role.entries.filter { it in v }.toSet()
        storage.roles = roles.value.map { it.name }.toSet()
        if (activeRole.value !in roles.value) setActiveRole(roles.value.first())
    }

    /** Passa a un ruolo: la prima schermata mostra le sue schede, partendo dalla prima. */
    fun setActiveRole(r: Role) {
        activeRole.value = r
        storage.activeRole = r.name
        homeTab.value = r.tabs.first()
    }

    /** Il backup va ricordato: promemoria acceso, ultimo backup da più di 30 giorni (o mai) e qualcosa da salvare. */
    fun backupDue(now: Long = System.currentTimeMillis()): Boolean =
        storage.backupReminder && now - storage.lastBackupAt > 30L * 24 * 3600 * 1000 && (history.value.isNotEmpty() || serveSessions.value.isNotEmpty())

    /** Chi sei nelle statistiche: il tuo giocatore, o il tuo nome se non l'hai ancora scelto. */
    fun myKey(): String = me()?.id ?: keyForName(storage.myName)

    /** Chi è un nome scritto (es. il proprio, in Altro › Tu): il giocatore del database se è uno solo, sennò il nome. */
    fun keyForName(name: String): String =
        com.tennis.scoremanager.data.Players.byName(players.value, name).singleOrNull()?.id ?: PlayerStats.key(name)

    /** I pezzi di video dei servizi [serves] della sessione [sess], in ordine, per la moviola. */
    fun serveClipFiles(sess: com.tennis.scoremanager.data.ServeSession, serves: List<com.tennis.scoremanager.data.ServeAttempt>): List<File> {
        val dir = storage.serveClipsDir(sess)
        return serves.mapNotNull { it.clip }.flatMap { c ->
            File(dir, c).listFiles { f -> f.extension == "mp4" }.orEmpty().sortedBy { it.name }
        }
    }

    private fun onBandEvent(e: BandEvent) {
        if (serveTraining) return
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

    /** I dati per le calorie dalla scheda del giocatore (età, peso, sesso); null se ne manca uno. */
    fun heartProfileOf(p: com.tennis.scoremanager.data.PlayerProfile?): HeartProfile? {
        p ?: return null
        val age = com.tennis.scoremanager.data.Members.age(p.birth, System.currentTimeMillis()) ?: return null
        val kg = p.weightKg ?: return null
        return HeartProfile(age, kg, female = p.sex == "F")
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

    /** Moviola: il punto [back] (0 = l'ultimo, 1 = quello prima) o, con null, gli ultimi secondi. */
    fun requestReplay(back: Int?) {
        if (!relay.requestReplay(back, com.tennis.scoremanager.video.ReplayRecorder.DEFAULT_S, app.cacheDir)) _toasts.tryEmit(strings.replayNoCamera)
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
        // Il sesso detto per quei nomi (anche in partite di prima) entra nella partita: lo usano highlights e storico.
        val su = setup.value.let { s ->
            fun sex(field: String, name: String) = field.ifEmpty { storage.playerSex(PlayerStats.key(name)) }
            s.copy(p1aSex = sex(s.p1aSex, s.p1a), p1bSex = sex(s.p1bSex, s.p1b), p2aSex = sex(s.p2aSex, s.p2a), p2bSex = sex(s.p2bSex, s.p2b))
        }
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
        // Ogni nome scritto diventa un giocatore del database (quello che c'è già, o uno nuovo): la partita è di persone.
        val (withIds, created) = com.tennis.scoremanager.data.Players.assign(su, players.value, now)
        // Chi ha età e peso nella scheda: le calorie della fascia cardio li prendono da lì.
        heartProfileOf(playerById(withIds.p1aId))?.let { setHeartProfile(Side.P1, it) }
        heartProfileOf(playerById(withIds.p2aId))?.let { setHeartProfile(Side.P2, it) }
        if (created.isNotEmpty()) {
            val list = players.value + created
            players.value = list.sortedBy { it.name.lowercase() }
            scope.launch(io) { storage.savePlayers(list) }
        }
        val rec = MatchRecord(id = "m$now-$suffix", setup = withIds, options = o, rules = rules, updatedAt = now, rackets = racketsFor(su))
        linkClubMatch(rec.id, withIds)
        linkTeamRubber(rec.id, withIds)
        linkLadderMatch(rec.id, withIds)
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
        clubMatchFinished(rec, lm.state)
        teamRubberFinished(rec, lm.state)
        ladderMatchFinished(rec, lm.state)
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

    /** Elimina più partite sospese insieme (dall'elenco delle sospese). */
    fun deleteSaved(ids: Set<String>) {
        scope.launch {
            withContext(io) { ids.forEach { storage.deleteMatch(it) } }
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

    // ---------------------------------------------------------------- racchetta virtuale

    /** Racchette con le incordature, in ordine di creazione. */
    val rackets = MutableStateFlow(storage.loadRackets())
    /** Racchetta proposta per gli allenamenti ("" = nessuna). */
    val workoutRacket = MutableStateFlow(storage.workoutRacket)

    fun racketById(id: String): Racket? = id.takeIf { it.isNotEmpty() }?.let { k -> rackets.value.firstOrNull { it.id == k } }

    /** Le racchette scelte in pagina 1, com'erano all'inizio della partita. */
    private fun racketsFor(su: SetupData): Map<Side, RacketUse> = buildMap {
        racketById(su.p1Racket)?.let { put(Side.P1, it.use()) }
        racketById(su.p2Racket)?.let { put(Side.P2, it.use()) }
    }

    fun openRackets() {
        refreshHistory()
        refreshWorkouts()
        screen.value = Screen.RACKETS
    }

    private fun storeRackets(list: List<Racket>) {
        rackets.value = list
        scope.launch(io) { storage.saveRackets(list) }
    }

    fun newRacketId(): String = "r${System.currentTimeMillis()}"

    fun newStringingId(): String = "s${System.currentTimeMillis()}"

    /** Aggiunge o aggiorna una racchetta (stesso id). */
    fun saveRacket(r: Racket) {
        val list = rackets.value
        storeRackets(if (list.any { it.id == r.id }) list.map { if (it.id == r.id) r else it } else list + r)
    }

    /** Toglie la racchetta dall'elenco; le partite giocate la conservano (ne hanno una copia). */
    fun deleteRacket(id: String) {
        storeRackets(rackets.value.filter { it.id != id })
        updateSetup { it.copy(p1Racket = if (it.p1Racket == id) "" else it.p1Racket, p2Racket = if (it.p2Racket == id) "" else it.p2Racket) }
        if (workoutRacket.value == id) setWorkoutRacket("")
    }

    fun saveStringing(racketId: String, st: Stringing) {
        val r = racketById(racketId) ?: return
        val list = if (r.stringings.any { it.id == st.id }) r.stringings.map { if (it.id == st.id) st else it } else r.stringings + st
        saveRacket(r.copy(stringings = list.sortedBy { it.date }))
    }

    fun deleteStringing(racketId: String, stringingId: String) {
        val r = racketById(racketId) ?: return
        saveRacket(r.copy(stringings = r.stringings.filter { it.id != stringingId }))
    }

    fun setWorkoutRacket(id: String) {
        workoutRacket.value = id
        storage.workoutRacket = id
    }

    /** Partite dello storico e allenamenti, come sessioni con una racchetta. */
    fun racketSessions(): List<RacketStats.Session> = RacketStats.sessions(played(), workouts.value)

    /** Sensazioni dette nel riepilogo per la racchetta di un lato. */
    fun setMatchFeel(side: Side, feel: Feel) {
        val sm = summary.value ?: return
        val use = sm.record.rackets[side] ?: return
        saveSummary(sm.copy(record = sm.record.copy(rackets = sm.record.rackets + (side to use.copy(feel = feel)))))
    }

    /**
     * Associa (o toglie, con "") una racchetta a un lato della partita nel riepilogo, anche a partita già
     * giocata: si prende l'incordatura montata quel giorno.
     */
    fun setMatchRacket(side: Side, racketId: String) {
        val sm = summary.value ?: return
        val old = sm.record.rackets
        val r = racketById(racketId)
        val at = sm.record.startedAt ?: sm.record.updatedAt
        val rackets = if (r == null) old - side else old + (side to r.useAt(at).copy(feel = old[side]?.feel))
        saveSummary(sm.copy(record = sm.record.copy(rackets = rackets)))
    }

    /** Come [setMatchRacket] per l'allenamento aperto. */
    fun setWorkoutRacketOf(racketId: String) {
        val w = workoutDetail.value ?: return
        val use = racketById(racketId)?.useAt(w.startedAt)?.copy(feel = w.racket?.feel)
        saveWorkout(w.copy(racket = use))
    }

    private fun saveSummary(sm: LiveMatch) {
        val rec = sm.record
        summary.value = sm
        val fromHistory = summaryFromHistory.value
        scope.launch(io) {
            storage.saveFinished(rec)
            if (!fromHistory) storage.saveLastFinished(rec)
        }
        history.value = history.value.map { if (it.record.id == rec.id) it.copy(record = rec) else it }
    }

    private fun saveWorkout(rec: WorkoutRecord) {
        workoutDetail.value = rec
        workouts.value = workouts.value.map { if (it.id == rec.id) rec else it }
        scope.launch(io) { storage.saveWorkout(rec) }
    }

    /** Sensazioni dell'allenamento aperto nel riepilogo. */
    fun setWorkoutFeel(feel: Feel) {
        val w = workoutDetail.value ?: return
        val use = w.racket ?: return
        saveWorkout(w.copy(racket = use.copy(feel = feel)))
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
    /** Giocatori dello storico (chi è, nome): col nome del database se è un giocatore del database. */
    fun progressPlayers(): List<Pair<String, String>> = PlayerStats.players(played()).map { it.key to (playerById(it.key)?.name ?: it.name) }

    /** Rating TSM di uno sport, dalle partite finite dello storico (le amichevoli non contano). */
    fun ratingTable(sport: com.tennis.scoremanager.data.Sport): com.tennis.scoremanager.data.Rating.Table =
        com.tennis.scoremanager.data.Rating.compute(
            history.value.mapNotNull { com.tennis.scoremanager.data.Rating.gameOf(it.record, it.state.sets, it.state.winner) }, sport,
        )

    /** Rating dello sport di [m], con [m] dentro anche se lo storico non l'ha ancora ricaricata (appena finita). */
    fun ratingTableWith(m: LiveMatch): com.tennis.scoremanager.data.Rating.Table =
        com.tennis.scoremanager.data.Rating.compute(
            (history.value.filter { it.record.id != m.record.id } + m)
                .mapNotNull { com.tennis.scoremanager.data.Rating.gameOf(it.record, it.state.sets, it.state.winner) },
            m.record.setup.sport,
        )

    /** Sport con almeno una partita che conta per il rating, il più giocato per primo. */
    fun ratingSports(): List<com.tennis.scoremanager.data.Sport> =
        history.value.mapNotNull { com.tennis.scoremanager.data.Rating.gameOf(it.record, it.state.sets, it.state.winner)?.sport }
            .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }

    /** Data corta: nelle colonne del documento quella lunga ("martedì 20 ottobre 2026") non ci sta. */
    private fun shortDate(): (Long) -> String {
        val fmt = java.text.SimpleDateFormat("dd/MM/yy", options.value.lang.locale)
        return { fmt.format(java.util.Date(it)) }
    }

    /** L'elenco da spuntare dei Progressi: allenamenti ([playerKey] null) o partite del giocatore, dal più recente. */
    fun progressItems(playerKey: String?): List<ProgressItem> {
        val s = strings
        val fmt = java.text.SimpleDateFormat("EEE dd/MM/yy HH:mm", options.value.lang.locale)
        val date: (Long) -> String = { fmt.format(java.util.Date(it)).replaceFirstChar { c -> c.uppercase() } }
        return if (playerKey == null) ProgressItems.workouts(workouts.value, date)
        else ProgressItems.matches(PlayerStats.lines(played(), playerKey), playerKey, s.progressWon, s.progressLost, date)
    }

    /** [playerKey] null = allenamenti; [ids] = quelli spuntati (null = tutti). */
    fun progressReport(playerKey: String?, ids: Set<String>? = null): Progress.Report {
        val s = strings
        val date = shortDate()
        return if (playerKey == null) {
            Progress.workouts(workouts.value.filter { ids == null || it.id in ids }, s, date)
        } else {
            val name = PlayerStats.players(played()).firstOrNull { it.key == playerKey }?.name ?: playerKey
            Progress.matches(PlayerStats.lines(played(), playerKey).filter { ids == null || it.rec.id in ids }, name, s, date)
        }
    }

    /** Il PDF dei progressi nella cartella di condivisione; null se non si è potuto scrivere. */
    fun progressPdf(playerKey: String?, ids: Set<String>? = null): File? {
        val r = progressReport(playerKey, ids)
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

    /**
     * Il report di [p] come file da condividere: PDF completo ([pdf]) o immagine; [days] = gli ultimi giorni (null =
     * tutto). L'allenatore è il tuo giocatore, il circolo quello di Circolo › Campi. null se non si è potuto fare.
     */
    fun studentReportFile(p: com.tennis.scoremanager.data.PlayerProfile, days: Int?, pdf: Boolean): File? {
        val s = strings
        val now = System.currentTimeMillis()
        val r = com.tennis.scoremanager.data.StudentReport.build(
            storage, s, p, storage.loadServeSessions(), studentMatches(p), studentRating(p)?.rating,
            coach = me()?.takeIf { it.id != p.id }?.name, club = storage.clubName.ifBlank { null },
            from = days?.let { now - it * 24L * 3600 * 1000 }, now = now,
        )
        val dir = File(app.cacheDir, "share").apply { mkdirs() }
        val base = "${s.reportTitle} ${p.name}".replace(Regex("[\\\\/:*?\"<>|]"), "_")
        return runCatching {
            if (pdf) File(dir, "$base.pdf").also { f ->
                f.outputStream().use { com.tennis.scoremanager.data.StudentReportPdf.write(r, s, options.value.lang, "${s.generatedWith} · ${Reports.date(now, options.value.lang)}", it) }
            } else File(dir, "$base.png").also { f ->
                val b = com.tennis.scoremanager.data.StudentReportImage.render(r, s, options.value.lang)
                f.outputStream().use { b.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                b.recycle()
            }
        }.getOrNull()
    }

    /** Condivide un file; [text] va con il file (la didascalia su WhatsApp, il testo del post). */
    fun shareFileIntent(file: File, mime: String, text: String? = null): Intent {
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.nameWithoutExtension)
            text?.let { putExtra(Intent.EXTRA_TEXT, it) }
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
        // Ti alleni tu: età, peso e sesso dalla tua scheda, se ci sono.
        heartProfileOf(me())?.let { setHeartProfile(side, it) }
        workoutUse = racketById(workoutRacket.value)?.use()
        workout.start(side, straps[side]?.name)
        MatchService.start(app)
        return true
    }

    private fun onWorkoutFinished(rec: WorkoutRecord) {
        val w = rec.copy(heart = rec.heart.copy(profile = heartProfiles.value[workoutSide]), racket = workoutUse)
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
                // Le partite senza giocatori del database (di prima, o arrivate da versioni vecchie) li ricevono ora.
                if (storage.assignPlayerIds() > 0) players.value = storage.loadPlayers().sortedBy { it.name.lowercase() }
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
    private fun importRecords(recs: List<MatchRecord>, info: List<com.tennis.scoremanager.data.PlayerProfile> = emptyList()): Pair<Int, Int> {
        var done = 0
        var open = 0
        // I giocatori delle partite arrivate: quelli che conosciamo si agganciano, quelli nuovi si aggiungono col loro
        // identificativo, chi ha il nome di uno dei nostri resta in sospeso finché l'utente non dice se è lui.
        var people = storage.loadPlayers()
        val questions = storage.loadPlayerQuestions().toMutableList()
        val now = System.currentTimeMillis()
        val resolved = recs.map { rec ->
            val (su, created, asks) = com.tennis.scoremanager.data.Players.resolveIncoming(rec.setup, people, now)
            people = people + created
            asks.filter { q -> questions.none { it.incomingId == q.incomingId } }.forEach { questions += it }
            rec.copy(setup = su)
        }
        // Le schede arrivate completano i nostri giocatori (nome, cognome, soprannome, mano dove mancano) e quelli nuovi.
        people = com.tennis.scoremanager.data.Players.fillFrom(people, info)
        storage.savePlayers(people)
        storage.savePlayerQuestions(questions)
        players.value = people.sortedBy { it.name.lowercase() }
        playerQuestions.value = questions
        for (rec in resolved) {
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

    /** Donna solo se lo si è detto per quel nome; altrimenti (uomo o non detto) il ritratto è quello dell'uomo. */
    fun isFemale(name: String): Boolean = storage.playerSex(PlayerStats.key(name)) == "F"

    /** "M", "F" o "" per il giocatore [name]: vale per tutti i suoi avatar, finché non ha una foto. */
    fun setPlayerSex(name: String, sex: String) {
        val key = PlayerStats.key(name)
        if (key.isEmpty()) return
        storage.setPlayerSex(key, sex)
        photosVersion.value++
        publishTv()
    }

    /** Il ritratto di uomo o di donna per chi non ha la foto (PDF e highlights). */
    fun avatarBitmap(female: Boolean): Bitmap =
        BitmapFactory.decodeResource(app.resources, if (female) R.drawable.avatar_woman else R.drawable.avatar_man)

    fun removePhoto(name: String) {
        val key = PlayerStats.key(name)
        if (key.isEmpty()) return
        scope.launch {
            withContext(io) { storage.deletePhoto(key) }
            photosVersion.value++
            publishTv()
        }
    }

    // ---------------------------------------------------------------- note della partita

    private var notesJob: Job? = null

    /** Note del riepilogo: subito a schermo, sul file dopo un attimo di pausa (non a ogni lettera). */
    // ---------------------------------------------------------------- rivincita

    /** Nomi di un lato per l'invito: "Rossi" o "Rossi / Bianchi". */
    private fun rematchSide(rec: MatchRecord, side: Side): String = Names(rec.setup, strings).players(side).joinToString(" / ")

    /** Luogo proposto: circolo e campo della partita, altrimenti l'indirizzo trovato. */
    fun rematchPlace(rec: MatchRecord): String =
        listOf(rec.setup.club.trim(), rec.setup.court.trim().takeIf { it.isNotEmpty() }?.let { strings.court + " " + it })
            .filterNot { it.isNullOrEmpty() }.joinToString(" · ").ifEmpty { rec.location?.address.orEmpty() }

    /** Il messaggio d'invito, nella lingua dell'app. */
    fun rematchText(m: LiveMatch, at: Long, place: String, note: String): String {
        val s = strings
        val lang = options.value.lang
        val rec = m.record
        val last = m.state.winner?.let { w -> s.rematchLast(Reports.scoreLine(m.state, w), rematchSide(rec, w)) }
        return com.tennis.scoremanager.data.Rematch.message(
            s.rematchAsk, rematchSide(rec, Side.P1), rematchSide(rec, Side.P2), s.rematchVersus,
            "${Reports.date(at, lang)}, ${Reports.time(at, lang)}", place, last, note,
            "${s.generatedWith} · ${Reports.siteUrl(lang).removePrefix("https://").removeSuffix("/")}",
        )
    }

    fun rematchShareIntent(text: String): Intent =
        Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }, strings.share)

    /** Evento nel calendario (lo crea l'app del calendario: niente permessi). */
    fun rematchCalendarIntent(m: LiveMatch, at: Long, place: String, text: String): Intent =
        Intent(Intent.ACTION_INSERT).setData(android.provider.CalendarContract.Events.CONTENT_URI)
            .putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, at)
            .putExtra(android.provider.CalendarContract.EXTRA_EVENT_END_TIME, at + com.tennis.scoremanager.data.Rematch.durationMs(m.record.clockMs))
            .putExtra(android.provider.CalendarContract.Events.TITLE, strings.rematchEvent("${rematchSide(m.record, Side.P1)} – ${rematchSide(m.record, Side.P2)}"))
            .putExtra(android.provider.CalendarContract.Events.EVENT_LOCATION, place)
            .putExtra(android.provider.CalendarContract.Events.DESCRIPTION, text)

    /** La stessa partita in pagina 1: giocatori, circolo, campo e regole; il torneo no. */
    fun prepareRematch(rec: MatchRecord) {
        updateSetup { com.tennis.scoremanager.data.Rematch.setup(rec.setup) }
        updateOptions { com.tennis.scoremanager.data.Rematch.options(it, rec.options) }
        summaryFromHistory.value = false
        newMatch()
    }

    /** Partita del riepilogo amichevole o no: si salva subito e il rating si ricalcola con lo storico. */
    fun setFriendly(on: Boolean) {
        val sm = summary.value ?: return
        val rec = sm.record.copy(friendly = on)
        summary.value = sm.copy(record = rec)
        val fromHistory = summaryFromHistory.value
        scope.launch {
            withContext(io) {
                storage.saveFinished(rec)
                if (!fromHistory) storage.saveLastFinished(rec)
            }
            refreshHistory()
        }
    }

    fun setNotes(text: String) {
        val sm = summary.value ?: return
        val rec = sm.record.copy(notes = text.take(MAX_NOTES))
        summary.value = sm.copy(record = rec)
        val fromHistory = summaryFromHistory.value
        notesJob?.cancel()
        notesJob = scope.launch {
            delay(800)
            withContext(io) {
                storage.saveFinished(rec)
                // Il riepilogo da riaprire è quello appena finito, non una partita riaperta dallo storico.
                if (!fromHistory) storage.saveLastFinished(rec)
            }
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

    /** Le partite che si possono inviare: finite e sospese, dalla più recente. */
    fun sendableMatches(): List<MatchRecord> = (storage.loadFinished() + storage.loadUnfinished()).distinctBy { it.id }.sortedByDescending { it.updatedAt }

    /**
     * Apre il server e prepara il QR con le partite [ids] scelte da chi invia (null = tutte). Con le partite viaggiano
     * solo le foto e le schede dei loro giocatori. false = nessuna rete locale (Wi-Fi o hotspot) per l'altro telefono.
     */
    fun startSending(ids: Set<String>? = null): Boolean {
        stopSending()
        val host = TvServer.localAddresses().firstOrNull() ?: return false
        val server = TransferServer(
            payload = {
                val all = (storage.loadFinished() + storage.loadUnfinished()).filter { ids == null || it.id in ids }
                // Le foto viaggiano con le partite (piccole: qualche decina di kB l'una), solo dei giocatori di queste.
                val keys = all.flatMap { m -> Side.entries.flatMap { PlayerStats.names(m.setup, it) } }.map { PlayerStats.key(it) }.toSet()
                val photos = storage.photoKeys().filter { it in keys }.associateWith { k -> Base64.encodeToString(storage.photoFile(k).readBytes(), Base64.NO_WRAP) }
                val matchPhotos = all.associate { m ->
                    m.id to storage.matchPhotos(m.id).map { Base64.encodeToString(it.readBytes(), Base64.NO_WRAP) }
                }.filterValues { it.isNotEmpty() }
                storage.json.encodeToString(
                    TransferBundle.serializer(), TransferBundle(
                        matches = all, photos = photos, matchPhotos = matchPhotos,
                        players = all.flatMap { com.tennis.scoremanager.data.Players.slots(it.setup) }.mapNotNull { playerById(it.id) }.distinctBy { it.id }
                            .map { com.tennis.scoremanager.data.PlayerProfile(it.id, it.name, it.firstName, it.lastName, it.nickname, it.hand, aliases = it.aliases) },
                    ),
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
                    importRecords(bundle.matches, bundle.players)
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
            storage.matchPhotos(sm.record.id), playerPhoto = { loadPhoto(it) ?: avatarBitmap(isFemale(it)) },
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

    /**
     * Dopo un ripristino o una cancellazione di tutti i dati: si chiude subito, senza salvare niente (salvando si
     * riscriverebbero le impostazioni di prima sopra quelle nuove). Riaprendo l'app riparte dai dati nuovi.
     */
    fun closeAfterData() {
        closing = true
        runCatching { ble.disconnectAll() }
        runCatching { relay.stop() }
        runCatching { heart.disconnectAll() }
        runCatching { MatchService.stop(app) }
        android.os.Process.killProcess(android.os.Process.myPid())
    }

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
        if (live.value != null || summary.value != null || screen.value != Screen.HOME) return
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
