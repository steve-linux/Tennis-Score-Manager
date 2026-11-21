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
import com.tennis.scoremanager.model.BreakKind
import com.tennis.scoremanager.model.MatchEvent
import com.tennis.scoremanager.model.MatchState
import com.tennis.scoremanager.model.PenaltyKind
import com.tennis.scoremanager.model.PenaltyOutcome
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

enum class Screen { HOME, SETUP, OPTIONS, TOSS, START, MATCH, SUMMARY, HISTORY, TOURNAMENT, WORKOUT, PROGRESS, VIDEOS, RACKETS, DEVICES, VOICE, STROKE_SESSIONS, HELP, STUDENTS, STUDENT, LESSONS, COACH_COURSE, PLAYERS, DATA, SETTINGS, DRILLS, MEMBERS, PAYMENTS, CLUB_TOURNAMENTS, CLUB_TOURNAMENT, NOTICE, COURTS_PLAN, SCHOOL, COURSE, TEAMS, TEAM, FIXTURE, BOOK_COURT, COURT_STATS, LADDERS, LADDER, DESK }

/**
 * Le schede della prima schermata, organizzate per cosa si vuole fare; quali si vedono dipende dal ruolo attivo
 * ([Role.tabs]).
 */
enum class HomeTab { PLAY, BOOKING, TRAIN, PROGRESS, MORE, STUDENTS, TOURNAMENTS, COURTS, PEOPLE, TODAY }

/**
 * Chi usa l'app, un ruolo solo per volta (si cambia dalle Impostazioni, i dati restano): ogni funzione sta in un ruolo
 * solo. Tu gioca (segnapunti, video, storico); l'Allenatore segue gli allievi e li fa giocare con "Partita con…"; il
 * Circolo gestisce (segreteria, planning, soci, tornei). Altro, con impostazioni e aiuto, è di tutti. La scheda Circolo
 * di chi gioca o allena ([HomeTab.BOOKING]) compare solo dopo l'invito della segreteria.
 */
enum class Role(val tabs: List<HomeTab>) {
    /** Tu: giochi (segnapunti, tabellone, video), il tuo circolo, ti alleni, i tuoi progressi. */
    PLAYER(listOf(HomeTab.PLAY, HomeTab.BOOKING, HomeTab.TRAIN, HomeTab.PROGRESS, HomeTab.MORE)),
    /** Allenatore: gli allievi (allenamenti, agenda, partite), il circolo dove prenota i campi. */
    COACH(listOf(HomeTab.STUDENTS, HomeTab.BOOKING, HomeTab.MORE)),
    /** Circolo: oggi (cosa serve adesso), il planning, i soci, i tornei; il circolo e i suoi strumenti in Altro. */
    CLUB(listOf(HomeTab.TODAY, HomeTab.COURTS, HomeTab.PEOPLE, HomeTab.TOURNAMENTS, HomeTab.MORE)),
}

/** Cosa usa chi gioca (ruoli Tu e Allenatore): quello che non usa non compare (i dati restano). */
enum class PlayerFeature { BANDS, HEART, TRAINING, VIDEO, PROGRESS, RACKETS, PADEL }

/**
 * Le funzioni del ruolo Circolo, da accendere o spegnere: quelle spente non compaiono nei menu (i dati restano).
 * [base] = accese all'inizio per tutti.
 */
enum class ClubFeature(val base: Boolean) {
    MEMBERS(true), PAYMENTS(true), PLANNING(true), TOURNAMENTS(true),
    TEAMS(false), SCHOOL(false), LADDER(false), STATS(false), SERVER(false), DESK(false),
}

/** Domanda sul punto appena segnato: come è finito (ace, vincente, errore) oppure, per un errore, di che tipo. */
enum class DetailAsk { OUTCOME, ERROR_TYPE }

/** [winner] ha vinto il punto, [server] serviva. */
data class DetailPrompt(val ask: DetailAsk, val winner: Side, val server: Side)

enum class CountdownKind { SHOT_CLOCK, CHANGEOVER, SET_BREAK, TIEBREAK_BREAK, MEDICAL, TOILET, WARMUP }

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
    override val heart: HeartRateManager,
    val voice: VoicePack,
    val announcer: Announcer,
) : com.tennis.scoremanager.club.ClubHost, com.tennis.scoremanager.coach.CoachHost, com.tennis.scoremanager.player.PlayerHost {
    companion object {
        const val SHOT_CLOCK_S = 25          // ITF: 25" tra un punto e l'altro
        const val CHANGEOVER_S = 90          // ITF: 90" al cambio campo
        const val SET_BREAK_S = 120          // ITF: 120" a fine set
        const val BALLS_ANIMATION_MS = 5_500L
        const val WARMUP_S = 300             // ITF: 5' di riscaldamento
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

    /** Le schermate e la strada fatta: Indietro torna da dove si è arrivati (vedi [Navigator]). */
    override val nav = Navigator()
    val screen = nav.screen
    /** Scheda della prima schermata: resta quella da cui si è partiti, così "Indietro" ci torna. */
    val homeTab = MutableStateFlow(HomeTab.PLAY)

    /** Torna alla prima schermata ([tab] se dato, altrimenti la scheda di prima). */
    fun home(tab: HomeTab? = null) {
        tab?.let { homeTab.value = it }
        nav.home()
    }
    val setup = MutableStateFlow(storage.setup)
    override val options = MutableStateFlow(storage.options)
    val live = MutableStateFlow<LiveMatch?>(null)
    override val summary = MutableStateFlow<LiveMatch?>(null)
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
    override val photosVersion = MutableStateFlow(0)
    /**
     * Moviola sul tabellone (null = niente): la manda l'arbitro dalla sua moviola. Anche questa va dichiarata prima
     * del blocco init, che la ascolta per aggiornare il tabellone.
     */
    val tvReplay = MutableStateFlow<TvReplay?>(null)
    /** Il torneo del circolo mostrato sul tabellone (ClubTournament.id), null = nessuno. */
    override val tvClubTournament = MutableStateFlow<String?>(null)
    /** Storico: partite finite salvate sul telefono, dalla più recente. */
    override val history = MutableStateFlow<List<LiveMatch>>(emptyList())
    /** Il riepilogo a schermo è stato aperto dallo storico: in fondo "Indietro" ed "Elimina" invece di nuova partita/esci. */
    val summaryFromHistory = MutableStateFlow(false)
    val voiceProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    /** File generati dal TTS e registrazioni personalizzate presenti per la lingua corrente. */
    val voiceCount = MutableStateFlow(0)
    val customVoiceCount = MutableStateFlow(0)
    val bandBattery = MutableStateFlow<Map<Side, BandBattery>>(emptyMap())
    /** Chi indossa la fascia cardio di ciascun lato (per le calorie). */
    override val heartProfiles = MutableStateFlow(Side.entries.mapNotNull { side -> storage.heartProfile(side == Side.P1)?.let { side to it } }.toMap())
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
    override fun toast(text: String) { _toasts.tryEmit(text) }
    val toasts: SharedFlow<String> = _toasts

    /** Tabellone TV: impostazioni, server web e messaggi per il pubblico (solo quelli di gioco). */
    val tv = MutableStateFlow(storage.tv)
    val tvServer = TvServer(app)
    private val tvMessage = MutableStateFlow<String?>(null)
    /** L'animazione del cambio palle: quando è partita (sul telefono e sul tabellone), null = niente. */
    val ballsAnimation = MutableStateFlow<Long?>(null)
    private var ballsJob: Job? = null
    /** Un cambio palle rimandato dall'inizio di un tie-break al secondo game del set dopo. */
    private var ballsPending = false
    /** Lo shot clock è arrivato a zero senza che si giocasse: si propone la violazione di tempo. */
    val shotClockOut = MutableStateFlow(false)
    private var tvMessageJob: Job? = null
    private var tvSeq = 0L
    /** Fine del cronometro in corso (null = nessuno): a ogni nuovo cronometro il tabellone riceve subito il tempo giusto. */
    private val tvCountdownEnd = MutableStateFlow<Long?>(null)
    private val tvJson = Json { encodeDefaults = true }

    override val strings: Strings get() = stringsFor(options.value.lang)
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

    /** Allenamento con la fascia, senza partita: vedi WorkoutArea. */
    val workoutArea = com.tennis.scoremanager.player.WorkoutArea(this, app, storage, scope, io)
    /** Allenamenti salvati, dal più recente. */
    override val workouts = MutableStateFlow<List<WorkoutRecord>>(emptyList())

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
                workoutArea.workout.onReading(side, m)
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
                screen, setup, options, live, summary, tvMessage, tv, tvServer.clients, tvCountdownEnd, tvReplay, ballsAnimation,
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
    override fun needsService(): Boolean =
        options.value.mode == PlayMode.BANDS || tv.value.enabled || options.value.heartRate && heart.straps.value.isNotEmpty() ||
            relay.ui.value.role != RelayRole.OFF || workoutArea.workout.active

    /**
     * Foto dei giocatori per il tabellone: "/photo/<file>?v=<versione>", così il tabellone ricarica quelle cambiate;
     * senza foto il ritratto di uomo o di donna ("/avatar/man.webp", "/avatar/woman.webp").
     */
    private fun tvPhotos(su: SetupData): Map<Side, List<String>> = Side.entries.associateWith { side ->
        PlayerStats.names(su, side).map { n ->
            storage.photoFile(PlayerStats.key(n)).takeIf { it.exists() }?.let { "/photo/${it.name}?v=${photosVersion.value}" }
                ?: "/avatar/${if (photos.isFemale(n)) "woman" else "man"}.webp"
        }
    }

    override fun publishTv() {
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
                balls = ballsAnimation.value,
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

    override fun showClubOnTv(id: String?) {
        tvClubTournament.value = id
        publishTv()
    }

    /** Il torneo sul tabellone, rifatto a ogni invio: così i risultati nuovi si vedono subito. */
    private fun tvClubSnapshot(): com.tennis.scoremanager.tv.TvClub? {
        val t = competitions.clubTournament(tvClubTournament.value) ?: return null
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
            com.tennis.scoremanager.tv.TvRound(competitions.clubRoundName(t, ms.first()), ms.map { m ->
                val a = if (m.a == null && m.bye) s.ctBye else n(m.a); val b = if (m.b == null && m.bye) s.ctBye else n(m.b)
                val extra = listOfNotNull(m.score.ifBlank { null }, s.ctWalkover.takeIf { m.walkover }, s.ctRetired.takeIf { m.retired }, s.ctDisqualified.takeIf { m.disqualified }, s.ctNoShow.takeIf { m.noShow }).joinToString(" ")
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
                    m.court, n(m.a), n(m.b), competitions.clubRoundName(t, m), m.score, m.played, when (m.winner) { null -> null; m.a -> 0; else -> 1 },
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

    override fun updateSetup(transform: (SetupData) -> SetupData) {
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

    override fun updateOptions(transform: (MatchOptions) -> MatchOptions) {
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

    override fun go(to: Screen) {
        if (to == Screen.START) refreshSaved()
        if (to == Screen.OPTIONS && options.value.mode == PlayMode.BANDS) ble.reconnectAll()
        if (to == Screen.OPTIONS && options.value.heartRate) heart.reconnectAll()
        // Il servizio in primo piano parte ora che l'app è visibile: avviarlo dopo, da un KEY1 a schermo
        // bloccato, Android lo vieterebbe e la partita resterebbe senza protezione in background.
        if (to == Screen.START && needsService()) MatchService.start(app)
        nav.push(to)
    }

    /** Indietro: la schermata da cui si è arrivati (la partita si lascia col suo menu, il riepilogo con "Fatto"). */
    fun back() {
        when (screen.value) {
            // L'allenamento in corso continua anche uscendo dalla sua schermata (si ritrova riaprendola).
            Screen.WORKOUT -> if (workoutArea.workoutDetail.value != null) { workoutArea.workoutDetail.value = null; return }
            Screen.START -> if (tv.value.enabled) MatchService.start(app) else MatchService.stop(app)
            Screen.MATCH -> return
            Screen.SUMMARY -> { closeSummary(); return }
            else -> {}
        }
        nav.back()
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
    override val serveSessions = MutableStateFlow<List<com.tennis.scoremanager.data.ServeSession>>(emptyList())

    fun refreshServeSessions() {
        scope.launch {
            serveSessions.value = withContext(io) { storage.loadServeSessions() }
            // Gli obiettivi degli allievi si controllano sulle sessioni nuove.
            coaching.checkStudentGoals()
        }
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

    /** Mette o toglie la stella (migliori colpi) al colpo [at] della sessione salvata [sessionId]; la sessione aggiornata. */
    fun setServeStar(sessionId: String, at: Long, on: Boolean): com.tennis.scoremanager.data.ServeSession? {
        val sess = storage.loadServeSessions().firstOrNull { it.id == sessionId } ?: return null
        val updated = sess.copy(serves = sess.serves.map { if (it.at == at) it.copy(starred = on) else it })
        storage.saveServeSession(updated)
        refreshServeSessions()
        return updated
    }

    /**
     * Sposta un colpo nella sessione di un altro colpo (riconosciuto dal video): nella stessa lezione, o in una sessione
     * nuova della stessa ora. La clip va nella cartella giusta e, se c'è ancora il video, l'analisi si rifà per quel
     * colpo. Restituisce la sessione di partenza aggiornata (null se è rimasta vuota e non c'è più).
     */
    fun moveServeShot(sessionId: String, at: Long, to: com.tennis.scoremanager.data.TrainedStroke, s: com.tennis.scoremanager.ui.Strings): com.tennis.scoremanager.data.ServeSession? {
        val all = storage.loadServeSessions()
        val src = all.firstOrNull { it.id == sessionId } ?: return null
        val a = src.serves.firstOrNull { it.at == at } ?: return src
        if (src.stroke == to) return src
        val lesson = src.lesson
        val targetId = if (lesson != null) com.tennis.scoremanager.data.Lessons.sessionId(lesson, to) else "${src.id}-${to.name.lowercase()}"
        val target = all.firstOrNull { it.id == targetId } ?: src.copy(
            id = targetId, stroke = to, serves = emptyList(), drillName = null,
            folder = if (lesson != null) src.folder?.substringBeforeLast('/')?.let { "$it/${s.strokeName(to.band)}" }
            else storage.newServeFolder(to, src.startedAt, s.strokeFolderName, s.strokeName, src.studentName),
        )
        a.clip?.let { clip ->
            val from = File(storage.serveClipsDir(src), clip)
            val dest = File(storage.serveClipsDir(target), clip)
            if (from.isDirectory && !from.renameTo(dest)) {
                if (runCatching { from.copyRecursively(dest, overwrite = true) }.getOrDefault(false)) from.deleteRecursively()
            }
            // Col video l'analisi si rifà (finestra e misure cambiano col colpo); senza, resta quella che c'è.
            if (dest.listFiles { f -> f.extension == "mp4" }?.isNotEmpty() == true) {
                for (n in listOf(
                    com.tennis.scoremanager.video.ServeClipAnalysis.RESULT, com.tennis.scoremanager.video.ServeClipAnalysis.TROPHY,
                    com.tennis.scoremanager.video.ServeClipAnalysis.KNEE, com.tennis.scoremanager.video.ServeClipAnalysis.CONTACT,
                    com.tennis.scoremanager.video.ServeClipAnalysis.PREP, com.tennis.scoremanager.video.ServeClipAnalysis.FINISH,
                )) File(dest, n).delete()
            }
        }
        storage.saveServeSession(target.copy(serves = (target.serves + a.copy(stroke = if (lesson != null) to else a.stroke)).sortedBy { it.at }))
        val rest = src.copy(serves = src.serves.filter { it.at != at })
        val out = if (rest.serves.isEmpty()) { storage.deleteServeSession(rest); null } else { storage.saveServeSession(rest); rest }
        refreshServeSessions()
        serveDiskVersion.value++
        return out
    }

    /** Cancella più sessioni insieme; con [videosOnly] solo i loro video (restano conteggi e analisi). */
    fun deleteServeSessions(list: List<com.tennis.scoremanager.data.ServeSession>, videosOnly: Boolean = false) {
        scope.launch {
            withContext(io) { list.forEach { if (videosOnly) storage.deleteServeVideos(it) else storage.deleteServeSession(it) } }
            refreshServeSessions()
            serveDiskVersion.value++
        }
    }

    /** Cresce quando si tolgono video: le pagine che mostrano lo spazio occupato lo ricalcolano. */
    val serveDiskVersion = MutableStateFlow(0)

    /** Colpo scelto in Allenati (vale anche per la pagina di tutte le sessioni). */
    val trainStroke = MutableStateFlow(com.tennis.scoremanager.data.TrainedStroke.SERVE)

    /** Apre tutte le sessioni del colpo [stroke] (dell'allievo scelto); Indietro torna da dove si è partiti. */
    fun openStrokeSessions(stroke: com.tennis.scoremanager.data.TrainedStroke) {
        trainStroke.value = stroke
        nav.push(Screen.STROKE_SESSIONS)
    }

    // ---------------------------------------------------------------- giocatori e allievi

    /** Il database dei giocatori, per nome. Gli allievi sono quelli con student. */
    override val players = MutableStateFlow(storage.loadPlayers().sortedBy { it.name.lowercase() })

    /** Gli allievi dell'allenatore (giocatori con student). */
    val students: StateFlow<List<com.tennis.scoremanager.data.PlayerProfile>> =
        players.map { l -> l.filter { it.student } }.stateIn(scope, SharingStarted.Eagerly, players.value.filter { it.student })

    /** Chi si allena con l'allenamento dei colpi: PlayerProfile.id, null = nessun allievo. */
    val trainStudent = MutableStateFlow(storage.trainStudent.ifEmpty { null })

    /** Giocatore aperto nella sua scheda. */
    val openStudentId = MutableStateFlow<String?>(null)

    override fun refreshPlayers() {
        scope.launch { players.value = withContext(io) { storage.loadPlayers() }.sortedBy { it.name.lowercase() } }
    }

    fun refreshStudents() = refreshPlayers()

    /** Il giocatore di un identificativo (anche di un suo alias). */
    override fun playerById(id: String?): com.tennis.scoremanager.data.PlayerProfile? =
        id?.let { com.tennis.scoremanager.data.Players.byId(players.value, it) }

    fun studentById(id: String?) = playerById(id)

    /** Aggiunge o cambia un giocatore (stesso id). */
    override fun savePlayer(p: com.tennis.scoremanager.data.PlayerProfile) {
        val list = players.value.filter { it.id != p.id } + p
        players.value = list.sortedBy { it.name.lowercase() }
        scope.launch(io) { storage.savePlayers(list) }
    }

    /** Tanti giocatori in una volta (Segreteria da PC, import di un foglio): un solo salvataggio. */
    override fun savePlayers(changed: List<com.tennis.scoremanager.data.PlayerProfile>) {
        val ids = changed.map { it.id }.toSet()
        val list = players.value.filter { it.id !in ids } + changed
        players.value = list.sortedBy { it.name.lowercase() }
        scope.launch(io) { storage.savePlayers(list) }
    }

    // ---- Backup della segreteria ----

    /** Il backup automatico di tutti i dati (senza video), una volta al giorno. */
    fun setPersonalBackupAuto(on: Boolean) {
        storage.personalBackupAuto = on
        com.tennis.scoremanager.service.DailyBackupJob.schedule(app, storage)
    }

    fun personalBackups(): List<File> = com.tennis.scoremanager.data.Backup.autoList(storage)

    /**
     * Una copia automatica di tutti i dati, senza video, in Documenti/Tennis Score Manager/Backup. Con [onlyIfChanged]
     * si salta se non è cambiato niente. Restituisce il file, o null se saltata o non riuscita.
     */
    /**
     * Mette (o toglie, con null) la password dei backup. Con la password nuova fa subito una copia protetta e, se
     * riesce, toglie le copie automatiche di prima senza password (avevano gli stessi dati in chiaro).
     */
    suspend fun setBackupPassword(pw: String?): Boolean {
        storage.backupPassword = pw
        if (pw == null) return true
        val made = personalBackupNow(onlyIfChanged = false) ?: return false
        withContext(io) { personalBackups().filter { it != made && !it.name.endsWith(com.tennis.scoremanager.data.Backup.LOCKED_EXT) }.forEach { it.delete() } }
        return true
    }

    suspend fun personalBackupNow(onlyIfChanged: Boolean): File? = withContext(io) {
        // Un'app appena installata non ha niente da salvare: una copia vuota diventerebbe la "più recente" in Documenti
        // proprio quando si vuole ripristinare quella di prima (dalla prima apertura). Le copie automatiche partono dopo.
        if (onlyIfChanged && !storage.onboarded) return@withContext null
        val folder = strings.strokeFolderName
        val print = com.tennis.scoremanager.data.Backup.fingerprint(app, storage, folder)
        if (onlyIfChanged && print == storage.personalBackupPrint && personalBackups().isNotEmpty()) return@withContext null
        val now = System.currentTimeMillis()
        runCatching { com.tennis.scoremanager.data.Backup.writeAuto(app, storage, com.tennis.scoremanager.BuildConfig.VERSION_NAME, folder, now, storage.backupPassword?.toCharArray()) }.getOrNull()?.also {
            storage.personalBackupPrint = print
            storage.lastBackupAt = now
        }
    }

    /** I backup della segreteria: vedi OfficeBackups. */
    val office = com.tennis.scoremanager.club.OfficeBackups(this, app, storage, scope, io)

    /** La Segreteria da PC: spenta finché non la si accende dall'app. */
    val desk by lazy { com.tennis.scoremanager.desk.DeskManager(app, com.tennis.scoremanager.desk.DeskBridge(this), com.tennis.scoremanager.desk.CoachBridge(this)) }

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
        photos.removePhoto(st.name)
        // Dal registro del circolo via gli addebiti non pagati; i pagamenti fatti restano col nome (contabilità).
        saveClubBook(com.tennis.scoremanager.data.Fees.forget(clubBook.value, setOf(st.id) + st.aliases))
        racketArea.rackets.value.filter { it.owner.isNotBlank() && PlayerStats.key(it.owner) == PlayerStats.key(st.name) }.forEach { racketArea.saveRacket(it.copy(owner = "")) }
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
        // L'allenatore ritrova il suo allievo anche dopo essersi allenato da solo (nel profilo Tu non c'è allievo).
        if (id != null) storage.coachStudent = id
    }

    /** Tornando al profilo Allenatore senza nessuno scelto: l'allievo dell'ultima volta, se c'è ancora. */
    fun restoreCoachStudent() {
        if (trainStudent.value != null) return
        storage.coachStudent.takeIf { id -> students.value.any { it.id == id } }?.let { setTrainStudent(it) }
    }

    /** "Allena ora" dalla scheda di un allievo (ruolo Allenatore): lui scelto, pronto per Inizia. */
    fun coachNow(id: String) {
        setTrainStudent(id)
        home(HomeTab.STUDENTS)
    }

    fun openStudents() {
        refreshPlayers()
        nav.push(Screen.STUDENTS)
    }

    fun openStudent(id: String) {
        openStudentId.value = id
        // Indietro torna da dove si è aperta la scheda.
        nav.push(Screen.STUDENT)
    }

    // ---- Allenatore: diario, obiettivi, agenda, pacchetti di lezioni, gruppi, corsi della scuola: vedi CoachArea ----

    // ---- Scheda partita ricevuta (convocazione della segreteria) ----

    /** Da dove viene il file che si sta aprendo: l'indirizzo del server del circolo, o null (WhatsApp, file). */
    override var incomingVia: String? = null

    /** Il testo di un file aperto con TSM: una scheda partita o un risultato; sennò lo si dice. */
    override fun receiveCardText(text: String?, via: String?) {
        incomingVia = via
        val card = text?.let { com.tennis.scoremanager.data.MatchCards.decode(it) }
        if (card != null) { cards.incomingCard.value = card; return }
        val result = text?.let { com.tennis.scoremanager.data.MatchCards.decodeResult(it) }
        if (result != null) { cards.incomingResult.value = result; return }
        text?.let { com.tennis.scoremanager.data.CourseCards.decode(it) }?.let { coaching.incomingCourse.value = it; return }
        text?.let { com.tennis.scoremanager.data.ProgressCards.decode(it) }?.let { coaching.incomingProgress.value = it; return }
        text?.let { com.tennis.scoremanager.data.CourseCards.decodeReport(it) }?.let { coaching.incomingAttendance.value = it; return }
        text?.let { com.tennis.scoremanager.data.CourseCards.decodeProposal(it) }?.let { coaching.incomingProposal.value = it; return }
        text?.let { com.tennis.scoremanager.data.ProfileCards.decode(it) }?.let { coaching.incomingProfile.value = it; return }
        // Una partita (da "File della partita" di un altro telefono): nello storico.
        text?.let { runCatching { storage.json.decodeFromString(MatchRecord.serializer(), it) }.getOrNull() }?.takeIf { it.events.isNotEmpty() }?.let { receiveMatchRecord(it); return }
        _toasts.tryEmit(strings.mcNotValid)
    }

    /** La scheda partita e i risultati tra segreteria e giocatori: vedi MatchCardsArea. */
    override val cards = com.tennis.scoremanager.club.MatchCardsArea(this, app, storage, scope, io)

    override val coaching = com.tennis.scoremanager.coach.CoachArea(this, app, storage, scope, io)

    // ---------------------------------------------------------------- passaggio del profilo

    /** Passaggio del profilo a un circolo o a un allenatore: vedi ProfileTransfer. */
    val profiles = com.tennis.scoremanager.club.ProfileTransfer(this, app, storage, scope, io)

    /** La partita finita in un file, da mandare all'allenatore (o a un altro telefono): la apre con TSM. */
    fun matchFileToShare(rec: MatchRecord): File? {
        val dir = File(app.cacheDir, "share/cards").apply { mkdirs() }
        val names = Side.entries.joinToString(" - ") { PlayerStats.names(rec.setup, it).joinToString(" & ") }
        return runCatching { File(dir, com.tennis.scoremanager.data.CourseCards.fileName(strings.ccMatchPrefix, names)).apply { writeText(storage.json.encodeToString(MatchRecord.serializer(), rec)) } }.getOrNull()
    }

    /** Una partita arrivata come file (da un allievo): nello storico, coi giocatori agganciati come nell'import. */
    private fun receiveMatchRecord(rec: MatchRecord) {
        scope.launch {
            val (done, open) = withContext(io) { importRecords(listOf(rec)) }
            _toasts.tryEmit(if (done + open == 0) strings.historyImportFailed else strings.historyImported(done, open))
            refreshHistory(); refreshSaved()
        }
    }

    // ---- Scheda progressi: vedi CoachArea. Casella del server del circolo: vedi ServerMailbox ----

    val mailbox = com.tennis.scoremanager.club.ServerMailbox(this, app, storage, scope, io)
    override fun serverMail(to: String, kind: String, file: File) = mailbox.serverMail(to, kind, file)
    override suspend fun postToOffice(url: String, kind: String, text: String): Boolean = mailbox.postToOffice(url, kind, text)
    override suspend fun fetchOfficeMail(conf: com.tennis.scoremanager.data.ClubServerConf) = mailbox.fetchOfficeMail(conf)

    // ---- Progressi, rating TSM e obiettivi: vedi ProgressArea ----

    override val progress = com.tennis.scoremanager.player.ProgressArea(this, app, storage, scope, io)

    /** Circolo › Soci: i giocatori iscritti al circolo, con tessere, certificati e consensi. */
    fun openMembers() {
        refreshPlayers()
        nav.push(Screen.MEMBERS)
    }

    /** Circolo › Quote e pagamenti. */
    fun openPayments() {
        refreshPlayers()
        nav.push(Screen.PAYMENTS)
    }

    /** Altro › Giocatori: tutto il database. */
    fun openPlayers() {
        refreshPlayers()
        refreshHistory()
        nav.push(Screen.PLAYERS)
    }

    /** Chi è il giocatore nelle statistiche: il suo identificativo e quelli che sono lui (alias). */
    private fun keysOf(p: com.tennis.scoremanager.data.PlayerProfile): Set<String> = setOf(p.id) + p.aliases

    /** Partite finite del giocatore dallo storico, dalla più vecchia. */
    fun studentMatches(p: com.tennis.scoremanager.data.PlayerProfile): List<PlayerStats.MatchLine> {
        val keys = keysOf(p)
        return PlayerStats.linesBy(progress.played()) { rec -> Side.entries.firstOrNull { side -> PlayerStats.whos(rec.setup, side).any { it.key in keys } } }
    }

    /** Rating TSM del giocatore nello sport con più partite (null senza partite). */
    fun studentRating(p: com.tennis.scoremanager.data.PlayerProfile) = progress.ratingSports().firstOrNull()?.let { progress.ratingTable(it).player(p.id) }

    /**
     * Tu: il tuo giocatore del database. Prima del database c'era solo il nome (Altro › Tu): se è di un giocatore solo,
     * è lui.
     */
    override fun me(): com.tennis.scoremanager.data.PlayerProfile? =
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
    override val clubBook = MutableStateFlow(storage.loadClubBook())

    override fun saveClubBook(book: com.tennis.scoremanager.data.ClubBook) {
        clubBook.value = book
        scope.launch(io) { storage.saveClubBook(book) }
    }

    // ---- Tornei, squadre e classifica a sfida del circolo: vedi ClubCompetitions ----

    override val competitions = com.tennis.scoremanager.club.ClubCompetitions(this, storage, scope, io)

    // ---- Statistiche dei campi (dal planning o dalla scheda Planning: Indietro torna lì) ----

    fun openCourtStats() {
        nav.push(Screen.COURT_STATS)
    }

    // ---- Aiuto al punto giusto ----

    /** La sezione della guida da aprire (dal "?" di una schermata), e dove si torna. */
    val helpSection = MutableStateFlow<String?>(null)

    fun openHelp(section: String) {
        helpSection.value = section
        nav.push(Screen.HELP)
    }

    // ---- Scuola tennis e planning dei campi: vedi ClubSchool e ClubPlanning ----

    override val school = com.tennis.scoremanager.club.ClubSchool(this, storage, scope, io)
    override val planning = com.tennis.scoremanager.club.ClubPlanning(this, app, storage, scope, io)
    override val courtPlan get() = planning.courtPlan

    /** Allenatore: sposta una lezione di un suo corso (non di quelli della segreteria: l'orario lo decide lei). */
    fun moveCoachLesson(courseId: String, key: String, start: Long, minutes: Int?, court: String?): com.tennis.scoremanager.data.Course? {
        val b = coaching.coachBook.value
        val cc = b.courses.firstOrNull { it.card.course.id == courseId }?.takeIf { it.personal } ?: return null
        val moved = com.tennis.scoremanager.data.Courses.move(cc.card.course, key, start, minutes, court)
        coaching.putCoach(b.copy(courses = b.courses.map { if (it.card.course.id == courseId) it.copy(card = it.card.copy(course = moved), cancelled = it.cancelled - key, changedAt = System.currentTimeMillis()) else it }))
        return moved
    }

    // ---- Server del circolo (facoltativo) ----

    /** Il server del circolo dalla parte della segreteria: vedi ClubServerArea. */
    val serverArea = com.tennis.scoremanager.club.ClubServerArea(this, app, storage, scope, io)
    override val clubServer get() = serverArea.clubServer
    override fun saveServer(f: (com.tennis.scoremanager.data.ClubServerConf) -> com.tennis.scoremanager.data.ClubServerConf) = serverArea.saveServer(f)

    // ---- Cosa usi (Tu e Allenatore) ----

    /** Finché non si sceglie, tutto acceso: a chi usa già l'app non sparisce niente. */
    val playerFeatures = MutableStateFlow(
        storage.playerFeatures?.mapNotNull { n -> PlayerFeature.entries.firstOrNull { it.name == n } }?.toSet() ?: PlayerFeature.entries.toSet(),
    )

    fun uses(f: PlayerFeature): Boolean = f in playerFeatures.value

    fun setPlayerFeature(f: PlayerFeature, on: Boolean) = setPlayerFeatures(if (on) playerFeatures.value + f else playerFeatures.value - f)

    fun setPlayerFeatures(set: Set<PlayerFeature>) {
        playerFeatures.value = set
        storage.playerFeatures = set.map { it.name }.toSet()
    }

    /** La prima presentazione propone cosa tenere dalla risposta a "come la userai" (solo se non si è mai scelto). */
    fun suggestPlayerFeatures(base: Set<PlayerFeature>) {
        if (storage.playerFeatures == null) setPlayerFeatures(base + PlayerFeature.PROGRESS + PlayerFeature.RACKETS)
    }

    // ---- Funzioni del circolo ----

    /**
     * Le funzioni accese. Finché il circolo non sceglie: quelle di base più quelle in cui ci sono già dati (squadre,
     * corsi, classifiche, server abbinato), così a chi le usa non sparisce niente.
     */
    override val clubFeatures = MutableStateFlow(
        storage.clubFeatures?.mapNotNull { n -> ClubFeature.entries.firstOrNull { it.name == n } }?.toSet()
            ?: (ClubFeature.entries.filter { it.base } + listOfNotNull(
                ClubFeature.TEAMS.takeIf { competitions.teams.value.isNotEmpty() },
                ClubFeature.SCHOOL.takeIf { school.courses.value.isNotEmpty() },
                ClubFeature.LADDER.takeIf { competitions.ladders.value.isNotEmpty() },
                ClubFeature.SERVER.takeIf { clubServer.value.paired },
            )).toSet(),
    )

    fun has(f: ClubFeature): Boolean = f in clubFeatures.value

    fun setFeature(f: ClubFeature, on: Boolean) {
        val n = if (on) clubFeatures.value + f else clubFeatures.value - f
        clubFeatures.value = n
        storage.clubFeatures = n.map { it.name }.toSet()
    }

    // ---- Prenota un campo (il giocatore, col server del suo circolo): vedi PlayerBooking ----

    override val book = com.tennis.scoremanager.club.PlayerBooking(
        app, storage, scope, io, { strings }, { options.value.lang.locale }, { serverArea.serverAnswers(it) }, { mailbox.fetchMyMail(it) }, { planning.bookingLabel(it) },
    ) { go(Screen.BOOK_COURT) }

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
        val text = (listOf(s.bkNotice("${courtLine(b.court)} · ${planning.bookingLabel(b).substringAfter(" · ")}"), (b.names + b.players.mapNotNull { playerById(it)?.name }).distinct().joinToString(" · "))
            + pay + listOfNotNull(b.note.ifBlank { null }, clubSign.ifBlank { null })).joinToString("\n")
        val rec = b.players.mapNotNull { playerById(it) }.map { com.tennis.scoremanager.data.Notices.recipient(it, text, now) }
        openNotice(com.tennis.scoremanager.data.NoticePlan(s.bkTitle, planning.bookingLabel(b), text, rec))
    }

    // ---- Avvisi (WhatsApp, SMS, email, condivisione) ----

    val notice = MutableStateFlow<com.tennis.scoremanager.data.NoticePlan?>(null)

    override fun openNotice(plan: com.tennis.scoremanager.data.NoticePlan) {
        notice.value = plan
        nav.push(Screen.NOTICE)
    }

    override fun entryNameOf(t: com.tennis.scoremanager.data.ClubTournament, id: String?): String =
        t.entries.firstOrNull { it.id == id }?.let { e -> e.players.mapIndexed { i, p -> playerById(p)?.name ?: e.names.getOrNull(i).orEmpty() }.joinToString(" / ") } ?: "—"


    override val clubSign: String get() = storage.clubName

    /** Gli avvisi del circolo: vedi ClubNotices. */
    val notices = com.tennis.scoremanager.club.ClubNotices(this, app, storage, scope, io)
    override fun challengeCardFile(ladderId: String, challengeId: String): File? = notices.challengeCardFile(ladderId, challengeId)

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

    /**
     * Il ruolo, uno solo: decide le schede della prima schermata e cosa c'è in Altro. I dati sono gli stessi per tutti e
     * cambiando ruolo (Impostazioni) non si perde niente. Chi aveva più ruoli accesi resta in quello che stava usando.
     */
    override val activeRole = MutableStateFlow(
        Role.entries.firstOrNull { it.name == storage.activeRole && it.name in storage.roles }
            ?: Role.entries.firstOrNull { it.name in storage.roles } ?: Role.PLAYER,
    )

    /** Il ruolo come insieme (sempre di uno solo), per chi chiede "c'è il Circolo?". */
    val roles = MutableStateFlow(setOf(activeRole.value))

    /** La partita [id] è di una competizione del circolo (torneo, incontro di squadra, sfida)? Si riprende da lì. */
    fun isClubRecord(id: String): Boolean =
        competitions.clubTournaments.value.any { t -> t.matches.any { it.record == id } } ||
            competitions.teams.value.any { t -> t.fixtures.any { f -> f.rubbers.any { it.record == id } } } ||
            competitions.ladders.value.any { l -> l.challenges.any { it.record == id } }

    /** Sceglie il ruolo: la prima schermata mostra le sue schede, partendo dalla prima. */
    fun setRole(r: Role) {
        activeRole.value = r
        roles.value = setOf(r)
        storage.activeRole = r.name
        storage.roles = setOf(r.name)
        homeTab.value = r.tabs.first()
    }

    /** Il backup va ricordato: promemoria acceso, ultimo backup da più di 30 giorni (o mai) e qualcosa da salvare. */
    fun backupDue(now: Long = System.currentTimeMillis()): Boolean =
        storage.backupReminder && !storage.personalBackupAuto && now - storage.lastBackupAt > 30L * 24 * 3600 * 1000 && (history.value.isNotEmpty() || serveSessions.value.isNotEmpty())

    /** Chi sei nelle statistiche: il tuo giocatore, o il tuo nome se non l'hai ancora scelto. */
    override fun myKey(): String = me()?.id ?: keyForName(storage.myName)

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
                state.isFinished -> BandProtocol.message(s.bandGameSetMatch, Reports.resultLine(state, side, s), 15)
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
    override fun heartProfileOf(p: com.tennis.scoremanager.data.PlayerProfile?): HeartProfile? {
        p ?: return null
        val age = com.tennis.scoremanager.data.Members.age(p.birth, System.currentTimeMillis()) ?: return null
        val kg = p.weightKg ?: return null
        return HeartProfile(age, kg, female = p.sex == "F")
    }

    override fun setHeartProfile(side: Side, p: HeartProfile?) {
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
            workoutArea.workout.onReading(side, m)
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
        val rec = MatchRecord(id = "m$now-$suffix", setup = withIds, options = o, rules = rules, updatedAt = now, rackets = racketArea.racketsFor(su))
        competitions.linkClubMatch(rec.id, withIds)
        competitions.linkTeamRubber(rec.id, withIds)
        competitions.linkLadderMatch(rec.id, withIds)
        cards.linkCard(rec.id, withIds)
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
        nav.play(Screen.MATCH)
        persist()
        if (needsService()) MatchService.start(app)
        batteryWarned.clear()
        ballsPending = false
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
    fun awardPoint(side: Side, fromBand: Boolean = false, kind: PointKind? = null, note: String? = null) {
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
        afterTransition(step.state, t, note)
    }

    /** [note]: detto prima del punteggio e scritto davanti al messaggio (il richiamo che ha dato il punto). */
    private fun afterTransition(state: MatchState, t: Transition, note: String? = null) {
        val n = names()
        val o = live.value?.record?.options ?: options.value
        val (balls, pending) = com.tennis.scoremanager.model.NewBalls.check(state, t, o.newBallsFirst, o.newBallsEvery, ballsPending)
        ballsPending = pending
        announcer.announce(
            listOfNotNull(note?.let { com.tennis.scoremanager.voice.Seg.Say(it) }) + calls().afterPoint(state, t, n) +
                listOfNotNull(strings.newBallsCall.takeIf { balls }?.let { com.tennis.scoremanager.voice.Seg.Say(it) }),
        )
        if (balls && o.newBallsAnimation) playBallsAnimation()
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
        }.let { m -> if (note == null) m else listOfNotNull(note, m).joinToString(" · ") }
            .let { m -> if (!balls) m else listOfNotNull(m, strings.newBallsCall).joinToString(" · ") }
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
        // Dopo un ritiro, un richiamo o una correzione si annulla quello, non l'ultimo punto.
        val events = ScoreEngine.undo(lm.state, lm.record.events) ?: return
        // Il primo evento tolto: il punto annullato (con le scelte del set nuovo venute dopo) o la correzione.
        val removed = lm.record.events[events.size]
        // I colpi dello scambio annullato tornano allo scambio in corso: il punto ridato li ritrova.
        if (removed is MatchEvent.Point) rallyStrokes += removed.strokes ?: 0
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
        showMessage(when (removed) {
            is MatchEvent.Point -> strings.msgPointUndone
            is MatchEvent.Penalty -> strings.penaltyUndone
            is MatchEvent.Retired -> if (removed.disqualified) strings.disqualifyUndone else strings.retireUndone
            is MatchEvent.Break -> strings.breakUndone
            // Chi serve, lati, punteggio messo a mano, ordine di risposta.
            else -> strings.fixUndone
        })
        showTvMessage(null)
    }

    /**
     * [side] si ritira (o, con [disqualified], è squalificato): la partita si ferma col punteggio di ora e si chiude
     * come le altre (annullabile fino alla conferma).
     */
    fun retire(side: Side, disqualified: Boolean = false) {
        val lm = live.value ?: return
        if (closing || lm.state.isFinished || endDialog.value) return
        askDetail(null)
        faultPending.value = false
        val rec = lm.record.copy(events = lm.record.events + MatchEvent.Retired(side, System.currentTimeMillis(), disqualified), endedAt = System.currentTimeMillis())
        val state = ScoreEngine.replay(rec.rules, rec.events)
        commit(rec, state)
        stopClock()
        stopCountdown()
        persist()
        val text = names(rec.setup).side(side).let { if (disqualified) strings.disqualifyCall(it) else strings.retireCall(it) }
        announcer.announce(listOf(com.tennis.scoremanager.voice.Seg.Say(text)))
        pushScore(state, null)
        showTvMessage(text)
        message.value = null
        endDialog.value = true
    }

    /**
     * Un richiamo dell'arbitro a [side]: si segna, si annuncia e, se tocca, il punto o il game va all'avversario (si
     * annulla con Annulla punto). La perdita del servizio, con le statistiche dettagliate, è una prima fuori.
     */
    fun penalty(side: Side, kind: PenaltyKind) {
        val lm = live.value ?: return
        if (closing || lm.record.suspended || lm.state.isFinished || endDialog.value) return
        askDetail(null)
        if (lm.record.startedAt == null) onPlay()
        val current = live.value ?: return
        val outcome = ScoreEngine.penaltyOutcome(current.state, side, kind)
        val event = MatchEvent.Penalty(side, kind, System.currentTimeMillis())
        val step = ScoreEngine.apply(current.state, event)
        val who = names(current.record.setup).side(side)
        val text = when (outcome) {
            PenaltyOutcome.WARNING -> if (kind == PenaltyKind.CODE) strings.penaltyWarnCode(who) else strings.penaltyWarnTime(who)
            PenaltyOutcome.POINT -> strings.penaltyPoint(who)
            PenaltyOutcome.GAME -> strings.penaltyGame(who)
            PenaltyOutcome.SERVE -> strings.penaltyServe(who)
        }
        // Il punto o il game di penalità chiude lo scambio in corso.
        if (step.transition != null) { faultPending.value = false; rallyStrokes = 0 }
        commit(current.record.copy(events = current.record.events + event), step.state)
        val t = step.transition
        if (t != null) {
            serveOrderPrompt.value = false
            afterTransition(step.state, t, text)
            return
        }
        if (outcome == PenaltyOutcome.SERVE && current.record.options.detailedStats) {
            lastPointAt = 0L
            // Sulla seconda è un doppio fallo: il punto va a chi risponde.
            if (faultPending.value) { awardPoint(step.state.receiver, kind = PointKind.DOUBLE_FAULT, note = text); return }
            fault()
        } else startCountdown(CountdownKind.SHOT_CLOCK, SHOT_CLOCK_S)
        // Il richiamo è dato: la proposta dello shot clock sparisce e i 25" ripartono.
        shotClockOut.value = false
        announcer.announce(listOf(com.tennis.scoremanager.voice.Seg.Say(text)))
        showTvMessage(text)
        showMessage(text)
    }

    /** Una pausa chiesta da [side]: si segna (si conta nel riepilogo, si annulla con Annulla) e parte il suo tempo. */
    fun takeBreak(side: Side, kind: BreakKind) {
        val lm = live.value ?: return
        if (closing || lm.record.suspended || lm.state.isFinished || endDialog.value) return
        askDetail(null)
        faultPending.value = false
        commit(lm.record.copy(events = lm.record.events + MatchEvent.Break(side, kind, System.currentTimeMillis())), lm.state)
        startCountdown(if (kind == BreakKind.MEDICAL) CountdownKind.MEDICAL else CountdownKind.TOILET, kind.seconds)
        val who = names(lm.record.setup).side(side)
        val text = if (kind == BreakKind.MEDICAL) strings.breakMedicalCall(who) else strings.breakToiletCall(who)
        announcer.announce(listOf(com.tennis.scoremanager.voice.Seg.Say(text)))
        showMessage(text)
        showTvMessage(text)
    }

    /**
     * Correzione dell'arbitro: da adesso serve [side] ([player] nel doppio), o con [ends] si scambiano i lati del
     * campo. Il punteggio resta (si annulla con Annulla).
     */
    fun fixServe(side: Side? = null, player: Int = 0, ends: Boolean = false) {
        val lm = live.value ?: return
        if (closing || lm.state.isFinished || endDialog.value) return
        askDetail(null)
        faultPending.value = false
        val event = if (ends) MatchEvent.EndsFix(System.currentTimeMillis()) else MatchEvent.ServerFix(side ?: return, player, System.currentTimeMillis())
        val state = ScoreEngine.apply(lm.state, event).state
        commit(lm.record.copy(events = lm.record.events + event), state)
        pushScore(state, null)
        val n = names(lm.record.setup)
        val text = if (ends) strings.endsFixed else strings.serverFixed(n.players(state.server).getOrNull(state.serverPlayer) ?: n.side(state.server))
        showMessage(text)
        showTvMessage(text)
    }

    /** L'animazione del cambio palle sul telefono e sul tabellone TV, per qualche secondo (anche per provarla). */
    fun playBallsAnimation() {
        ballsJob?.cancel()
        ballsAnimation.value = System.currentTimeMillis()
        ballsJob = scope.launch {
            delay(BALLS_ANIMATION_MS)
            ballsAnimation.value = null
        }
    }

    fun stopBallsAnimation() {
        ballsJob?.cancel()
        ballsAnimation.value = null
    }

    /**
     * Il punteggio messo a mano: i set finiti, i game e i punti di adesso e chi serve. Si continua da qui; Annulla lo
     * toglie. false = non si può giocare con queste regole.
     */
    fun setScore(sets: List<com.tennis.scoremanager.model.SetScore>, g1: Int, g2: Int, pt1: Int, pt2: Int, server: Side, serverPlayer: Int): Boolean {
        val lm = live.value ?: return false
        if (closing || lm.state.isFinished || endDialog.value) return false
        if (!ScoreEngine.validScore(lm.state.rules, sets, g1, g2, pt1, pt2)) return false
        askDetail(null)
        faultPending.value = false
        val event = MatchEvent.ScoreSet(sets, g1, g2, pt1, pt2, server, serverPlayer, System.currentTimeMillis())
        val state = ScoreEngine.apply(lm.state, event).state
        commit(lm.record.copy(events = lm.record.events + event), state)
        ballsPending = false
        pushScore(state, null)
        announcer.announce(calls().correction(state, names()))
        showMessage(strings.scoreSetApplied)
        showTvMessage(strings.scoreSetApplied)
        if (!lm.record.suspended && lm.record.startedAt != null) startCountdown(CountdownKind.SHOT_CLOCK, SHOT_CLOCK_S)
        return true
    }

    /** Doppio: chi risponde da destra in ciascuna squadra, per il set in corso (si può correggere quando serve). */
    fun setReceiveOrder(deuceP1: Int, deuceP2: Int) {
        val lm = live.value ?: return
        if (closing || lm.state.isFinished || !lm.state.rules.doubles) return
        val event = MatchEvent.ReceiveOrder(lm.state.setNumber, deuceP1, deuceP2)
        val state = ScoreEngine.apply(lm.state, event).state
        commit(lm.record.copy(events = lm.record.events + event), state)
    }

    /** Una nota dell'arbitro durante la partita: va nelle note della partita (riepilogo, PDF, testo condiviso) con l'ora e il punteggio. */
    fun addMatchNote(text: String) {
        val lm = live.value ?: return
        val t = text.trim().take(300)
        if (t.isEmpty()) return
        // I set finiti e i game del set in corso: "6-3 2-1".
        val score = listOfNotNull(Reports.scoreLine(lm.state, Side.P1).ifBlank { null }, "${lm.state.g1}-${lm.state.g2}").joinToString(" ")
        val line = "${Reports.time(System.currentTimeMillis(), lm.record.options.lang)} ($score) $t"
        setRecord(lm.record.copy(notes = listOf(lm.record.notes.trim(), line).filter { it.isNotEmpty() }.joinToString("\n")))
        persist()
        showMessage(strings.noteAdded)
    }

    /** I 5 minuti di riscaldamento, prima del primo punto: il tempo partita parte solo col primo punto. */
    fun warmUp() {
        val lm = live.value ?: return
        if (closing || lm.record.suspended || lm.state.isFinished || lm.state.pointsPlayed > 0) return
        // Il tempo partita, partito con "gioco", torna a zero: ripartirà col primo punto (awardPoint → onPlay).
        runningSince = null
        setRecord(lm.record.copy(startedAt = null, clockMs = 0L))
        clockMs.value = 0L
        startCountdown(CountdownKind.WARMUP, WARMUP_S)
        showMessage(strings.warmupTime)
    }

    /** Il riscaldamento si può fare solo prima del primo punto. */
    fun canWarmUp(): Boolean = live.value?.let { !it.state.isFinished && it.state.pointsPlayed == 0 && !it.record.suspended } == true

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
        competitions.clubMatchFinished(rec, lm.state)
        competitions.teamRubberFinished(rec, lm.state)
        competitions.ladderMatchFinished(rec, lm.state)
        cards.cardMatchFinished(rec, lm.state)
        // Prima si salva la partita finita, poi si toglie la sospesa: in mezzo non resta mai senza copia.
        scope.launch(io) {
            storage.saveFinished(rec)
            storage.saveLastFinished(rec)
            storage.deleteMatch(rec.id)
        }
        // Se Android chiude il processo sul riepilogo (es. mentre si condivide) lo si riapre: vedi onActivityCreated.
        storage.summaryOpen = rec.id
        summaryFromHistory.value = false
        summary.value = LiveMatch(rec, lm.state)
        live.value = null
        endDialog.value = false
        stopCountdown()
        nav.replace(Screen.SUMMARY)
        // Col tabellone acceso il servizio resta (il risultato è ancora sul tabellone): si riavvia solo per il testo della notifica.
        if (tv.value.enabled) MatchService.start(app) else MatchService.stop(app)
        // I braccialetti hanno finito: si spengono subito invece di aspettare l'inattività.
        if (rec.options.mode == PlayMode.BANDS && options.value.bandsOffAtEnd) {
            val s = strings
            scope.launch { powerOffBands(s.bandMatchOver) { side -> Reports.scoreLine(lm.state, side) } }
        }
    }

    /** Una partita nuova. Quella di adesso, se non è finita, resta salvata tra le sospese. */
    fun newMatch() {
        val from = screen.value
        closeMatch()
        // Dalla partita o dal suo riepilogo la partita nuova ne prende il posto: Indietro torna da dove era partita.
        if (from == Screen.MATCH || from == Screen.SUMMARY) nav.replace(Screen.SETUP) else nav.push(Screen.SETUP)
    }

    /** Lascia la partita (resta tra le sospese, la si riprende da Gioca) e torna da dove era partita. */
    fun leaveMatch() {
        closeMatch()
        nav.back()
    }

    /** "Fatto" nel riepilogo (anche Indietro): si torna da dove era partita la partita, o allo storico. */
    fun closeSummary() {
        val fromHistory = summaryFromHistory.value
        if (fromHistory) {
            summary.value = null
            summaryFromHistory.value = false
            refreshHistory()
        } else {
            closeMatch()
        }
        nav.back()
    }

    /** Chiude la partita in corso (sospesa, se non è finita) e il riepilogo: tempi, voce e servizio a riposo. */
    private fun closeMatch() {
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
        quickStart.value = false
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
        nav.play(Screen.MATCH)
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

    override fun refreshSaved() {
        scope.launch { saved.value = withContext(io) { storage.loadUnfinished() } }
    }

    // ---------------------------------------------------------------- storico

    fun openHistory() {
        refreshHistory()
        nav.push(Screen.HISTORY)
    }

    // ---------------------------------------------------------------- racchetta virtuale

    /** Racchette e incordature: vedi RacketsArea. */
    override val racketArea = com.tennis.scoremanager.player.RacketsArea(this, app, storage, scope, io)

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
        val r = racketArea.racketById(racketId)
        val at = sm.record.startedAt ?: sm.record.updatedAt
        val rackets = if (r == null) old - side else old + (side to r.useAt(at).copy(feel = old[side]?.feel))
        saveSummary(sm.copy(record = sm.record.copy(rackets = rackets)))
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

    // ---------------------------------------------------------------- progressi


    /**
     * Il report di [p] come file da condividere: PDF completo ([pdf]) o immagine; [days] = gli ultimi giorni (null =
     * tutto). L'allenatore è il tuo giocatore, il circolo quello di Circolo › Campi. null se non si è potuto fare.
     */
    override fun studentReportFile(p: com.tennis.scoremanager.data.PlayerProfile, days: Int?, pdf: Boolean): File? {
        val s = strings
        val now = System.currentTimeMillis()
        val r = com.tennis.scoremanager.data.StudentReport.build(
            storage, s, p, storage.loadServeSessions(), studentMatches(p), studentRating(p)?.rating,
            coach = me()?.takeIf { it.id != p.id }?.name, club = storage.clubName.ifBlank { null },
            from = days?.let { now - it * 24L * 3600 * 1000 }, now = now, extra = coachReportExtra(p, days?.let { now - it * 24L * 3600 * 1000 }),
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

    /**
     * Le parti dell'allenatore nel report dell'allievo: valutazione tecnica (ultima, con la differenza dalla prima del
     * periodo), test fisici, classifica, presenze a lezioni e corsi, risultati scritti a mano.
     */
    private fun coachReportExtra(p: com.tennis.scoremanager.data.PlayerProfile, from: Long?): List<Pair<String, List<String>>> {
        val s = strings; val lang = options.value.lang
        val b = coaching.coachBook.value
        val C = com.tennis.scoremanager.data.Coaching
        fun d(at: Long) = com.tennis.scoremanager.ui.screens.shortDate(at, lang)
        val ev = C.evaluationsOf(b, p.id)
        val last = ev.lastOrNull()
        val first = ev.firstOrNull { from == null || it.at >= from }?.takeIf { it != last }
        val evalLines = last?.let { e ->
            listOf(s.evLast(d(e.at))) + C.ASPECTS.mapNotNull { k -> e.scores[k]?.let { v ->
                val before = first?.scores?.get(k)
                "${com.tennis.scoremanager.ui.aspectLabel(s, k)}: $v/5" + (before?.takeIf { it != v }?.let { " (${if (v > it) "+" else ""}${v - it})" } ?: "")
            } } + listOfNotNull(e.note.ifBlank { null })
        }.orEmpty()
        val tests = C.testsOf(b, p.id)
        val testLines = C.TESTS.mapNotNull { (k, _) -> C.lastTwo(tests, k).first?.let { v -> "${com.tennis.scoremanager.ui.testLabel(s, k)}: ${com.tennis.scoremanager.ui.testValue(k, v)}" } }
        val rk = b.rankings.filter { it.student == p.id }.sortedBy { it.at }
        val rankLines = rk.takeLast(4).map { "${d(it.at)}: ${it.ranking}" }
        val inPeriod = { at: Long -> from == null || at >= from }
        val bal = C.balance(b, p.id)
        val courseLines = b.courses.filter { p.id in it.card.course.students }.map { cc ->
            val (pres, tot) = com.tennis.scoremanager.data.Courses.attendanceOf(cc.course, p.id, System.currentTimeMillis())
            "${cc.card.course.name}: " + s.scAttendance(pres, tot)
        } + listOfNotNull(s.evAbsences(bal.absences).takeIf { bal.absences > 0 })
        val manual = b.manualMatches.filter { it.student == p.id && inPeriod(it.at) }.sortedByDescending { it.at }.take(8).map {
            listOf(d(it.at), it.event, it.opponent + (it.opponentRanking.ifBlank { null }?.let { r -> " ($r)" } ?: ""), it.score, if (it.won) s.studentWon else s.studentLost)
                .filter { x -> x.isNotBlank() }.joinToString(" · ")
        }
        return listOf(s.evTitle to evalLines, s.ftTitle to testLines, s.rkTitle to rankLines, s.evPresence to courseLines, s.mmTitle to manual)
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
    override fun refreshWorkouts() {
        scope.launch { workouts.value = withContext(io) { storage.loadWorkouts() }; progress.checkGoals() }
    }

    override fun refreshHistory() {
        scope.launch {
            history.value = withContext(io) {
                // Le partite senza giocatori del database (di prima, o arrivate da versioni vecchie) li ricevono ora.
                if (storage.assignPlayerIds() > 0) players.value = storage.loadPlayers().sortedBy { it.name.lowercase() }
                // Prima dello storico l'app teneva solo l'ultima partita finita: entra anche lei.
                storage.loadLastFinished()?.let { if (!storage.hasFinished(it.id)) storage.saveFinished(it) }
                storage.loadFinished().map { LiveMatch(it, ScoreEngine.replay(it.rules, it.events)) }
            }
            progress.checkGoals()
        }
    }

    /** Il riepilogo di una partita dello storico (o del calendario, di un torneo...); Indietro torna lì. */
    fun openFromHistory(m: LiveMatch) {
        summary.value = m
        summaryFromHistory.value = true
        nav.push(Screen.SUMMARY)
    }

    fun deleteFromHistory(id: String) {
        scope.launch {
            withContext(io) {
                storage.deleteFinished(id)
                storage.deleteMatchPhotos(id)
                // Altrimenti l'ultima partita finita rientrerebbe nello storico al prossimo caricamento.
                if (storage.loadLastFinished()?.id == id) storage.clearLastFinished()
            }
            if (summary.value?.record?.id == id && screen.value == Screen.SUMMARY) closeSummary() else refreshHistory()
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
    override fun importRecords(recs: List<MatchRecord>, info: List<com.tennis.scoremanager.data.PlayerProfile>): Pair<Int, Int> {
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

    /** Il torneo tra amici (Americano e Mexicano), per conto suo: elenco, turni, classifica. */
    val friends = FriendsTournaments(storage, scope, io, nav, { strings }, { options.value.lang }) { a, b ->
        if (options.value.audio) announcer.announce(calls().counterScore(a, b))
    }

    // ---------------------------------------------------------------- foto dei giocatori

    /** Foto dei giocatori e foto ricordo delle partite: vedi PhotosArea. */
    val photos = com.tennis.scoremanager.player.PhotosArea(this, app, storage, scope, io)

    // ---------------------------------------------------------------- note della partita

    private var notesJob: Job? = null

    /** Note del riepilogo: subito a schermo, sul file dopo un attimo di pausa (non a ogni lettera). */
    // ---------------------------------------------------------------- rivincita

    /** Invito alla rivincita: vedi RematchInvite. */
    val rematch = com.tennis.scoremanager.player.RematchInvite(this, app, storage, scope, io)

    /** La stessa partita in pagina 1: giocatori, circolo, campo e regole; il torneo no. */
    /** Nuova partita più corta: le regole si vedono in una riga e si va dritti al sorteggio (finché non si tocca Cambia). */
    val quickStart = MutableStateFlow(false)

    /**
     * Come l'ultima volta: stessi giocatori, campo e regole, e subito al sorteggio (coi braccialetti o i telecomandi
     * prima la pagina dove si collegano). Indietro porta ai giocatori.
     */
    fun startLikeLast(rec: MatchRecord) {
        prepareRematch(rec)
        quickStart.value = true
        go(if (options.value.mode == PlayMode.REFEREE) Screen.TOSS else Screen.OPTIONS)
    }

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

    // ---------------------------------------------------------------- trasferimento tra telefoni (QR)

    /** Trasferimento tra telefoni col QR: vedi TransferArea. */
    val transfer = com.tennis.scoremanager.player.TransferArea(this, app, storage, scope, io)

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
        shotClockOut.value = false
        cdKind = kind
        cdEnd = SystemClock.elapsedRealtime() + seconds * 1000L
        countdown.value = CountdownUi(kind, seconds)
        tvCountdownEnd.value = cdEnd
    }

    private fun stopCountdown() {
        shotClockOut.value = false
        cdKind = null
        countdown.value = null
        tvCountdownEnd.value = null
    }

    private fun tick() {
        val now = SystemClock.elapsedRealtime()
        if (live.value != null) clockMs.value = currentClock(now)
        cdKind?.let { k ->
            val rem = ((cdEnd - now + 999) / 1000).coerceAtLeast(0).toInt()
            // Shot clock a zero a partita in corso: si propone la violazione di tempo (sparisce col punto dopo).
            if (rem == 0 && k == CountdownKind.SHOT_CLOCK && !shotClockOut.value) {
                val lm = live.value
                if (lm != null && runningSince != null && !lm.state.isFinished && !lm.record.suspended && !endDialog.value) shotClockOut.value = true
            }
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

    /** Il luogo della partita nel riepilogo (Nuova partita › Altri dettagli): spento, la posizione non si chiede. */
    val matchPlace = MutableStateFlow(storage.matchPlace)

    fun setMatchPlace(on: Boolean) {
        storage.matchPlace = on
        matchPlace.value = on
    }

    private fun fetchLocation() {
        if (!storage.matchPlace) return
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
            storage.matchPhotos(sm.record.id), playerPhoto = { photos.loadPhoto(it) ?: photos.avatarBitmap(photos.isFemale(it)) },
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
        nav.replace(Screen.SUMMARY)
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
