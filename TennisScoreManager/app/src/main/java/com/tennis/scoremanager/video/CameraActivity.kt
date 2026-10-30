// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.video

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.LooksOne
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.size
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.gl.render.filters.`object`.ImageFilterRender
import com.pedro.encoder.input.sources.video.Camera2Source
import com.pedro.encoder.input.video.CameraHelper
import com.pedro.library.generic.GenericStream
import com.tennis.scoremanager.TsmApp
import com.tennis.scoremanager.data.Reports
import com.tennis.scoremanager.relay.CamCmd
import com.tennis.scoremanager.relay.CamState
import com.tennis.scoremanager.relay.RelayMsg
import com.tennis.scoremanager.ble.BandProtocol
import com.tennis.scoremanager.data.ServeAttempt
import com.tennis.scoremanager.data.ServeKind
import com.tennis.scoremanager.data.ServeSession
import com.tennis.scoremanager.data.ServeSide
import com.tennis.scoremanager.data.ServeTarget
import com.tennis.scoremanager.data.ServeTraining
import com.tennis.scoremanager.data.ShotDirection
import com.tennis.scoremanager.data.TrainedStroke
import com.tennis.scoremanager.data.Wing
import com.tennis.scoremanager.ui.directionName
import com.tennis.scoremanager.ui.strokeLabel
import com.tennis.scoremanager.ui.wingName
import com.tennis.scoremanager.model.Side
import com.tennis.scoremanager.ui.CalibrationPanel
import com.tennis.scoremanager.ui.ServePlacementDialog
import com.tennis.scoremanager.ui.ServeReplayButtons
import com.tennis.scoremanager.ui.ServeSummaryContent
import com.tennis.scoremanager.ui.serveKindName
import com.tennis.scoremanager.ui.serveSideName
import com.tennis.scoremanager.ui.serveTargetName
import com.tennis.scoremanager.ui.CourtOutline
import com.tennis.scoremanager.ui.FramingDialog
import com.tennis.scoremanager.ui.HorizonOverlay
import com.tennis.scoremanager.ui.LevelReadout
import com.tennis.scoremanager.ui.LocalStrings
import com.tennis.scoremanager.ui.Strings
import com.tennis.scoremanager.ui.lensName
import com.tennis.scoremanager.ui.TsmColors
import com.tennis.scoremanager.ui.TsmTheme
import com.tennis.scoremanager.ui.stringsFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Telefono-telecamera: dietro il campo, al centro. Registra la partita in MP4 1080p e può trasmetterla in diretta
 * (RTMP: YouTube, Twitch, un server proprio), con la grafica del punteggio che arriva dal telefono dell'arbitro
 * (vedi PhoneRelay, ruolo "camera"). Ogni punto ricevuto durante la registrazione diventa un capitolo, salvato
 * accanto al video (file .json con lo stesso nome). Spesso sta in alto, lontano: l'arbitro ne vede l'inquadratura e la
 * comanda dal suo telefono (registra, diretta, grafica, zoom; vedi CameraRemoteActivity).
 */
class CameraActivity : ComponentActivity() {

    private val controller get() = (application as TsmApp).controller
    private val s: Strings get() = stringsFor(controller.options.value.lang)

    private val live = MutableStateFlow(LiveState.OFF)
    private val recording = MutableStateFlow(false)
    private val recordSince = MutableStateFlow(0L)
    private val message = MutableStateFlow<String?>(null)
    private val permsOk = MutableStateFlow(false)
    private val overlayOn = MutableStateFlow(false)
    private val zoom = MutableStateFlow(1f)
    /** Fino a quando mandare l'inquadratura all'arbitro (si allunga a ogni sua richiesta). */
    @Volatile private var previewUntil = 0L
    /** Obiettivi posteriori utili (0 = principale) e quello in uso. */
    private val lenses by lazy { CameraLenses.list(this) }
    private val lensIndex = MutableStateFlow(0)
    /** Assistente inquadratura: livella accesa e inclinazione del telefono. */
    private val levelOn = MutableStateFlow(false)
    private val tiltSensor by lazy {
        TiltSensor(this) {
            @Suppress("DEPRECATION")
            when (windowManager.defaultDisplay.rotation) { android.view.Surface.ROTATION_90 -> 90; android.view.Surface.ROTATION_180 -> 180; android.view.Surface.ROTATION_270 -> 270; else -> 0 }
        }
    }
    private var lastTiltSent = 0L

    // ---------------------------------------------------------------- allenamento al servizio (EXTRA_SERVE)

    private val serveMode by lazy { intent.getBooleanExtra(EXTRA_SERVE, false) }
    private val serveSession = MutableStateFlow(ServeSession("s${System.currentTimeMillis()}", System.currentTimeMillis()))
    private val serveSide = MutableStateFlow(ServeSide.DEUCE)
    private val serveKind = MutableStateFlow(ServeKind.FIRST)
    private val serveTarget = MutableStateFlow<ServeTarget?>(null)
    /** Dritto, rovescio e volée: direzione voluta e, per la volée, dritto o rovescio. */
    private val shotDirection = MutableStateFlow<ShotDirection?>(null)
    private val volleyWing = MutableStateFlow(Wing.FOREHAND)
    private val trained: TrainedStroke get() = serveSession.value.stroke
    /** Servizio riconosciuto e non ancora segnato (dentro o fuori). */
    private val servePending = MutableStateFlow(false)
    private val serveSummary = MutableStateFlow(false)
    private val placementHelp = MutableStateFlow(false)
    private var lastDetected: ServeTraining.Detected? = null

    /**
     * Allenamento in pausa: telecamera e memoria spente, i tasti del braccialetto non segnano (KEY1 riprende).
     * [autoPaused]: messa da sola dopo [AUTO_PAUSE_MS] senza colpi; allora anche un colpo riconosciuto la toglie.
     */
    private val trainPaused = MutableStateFlow(false)
    private var autoPaused = false
    /** Ultimo segno di vita dell'allenamento (colpo segnato o riconosciuto, un tasto sullo schermo). */
    @Volatile private var lastTrainActivity = System.currentTimeMillis()
    /** Spazio dei video salvati in questa sessione (byte), per l'indicatore. */
    private val trainBytes = MutableStateFlow(0L)
    /** L'anteprima, per riaccenderla dopo la pausa. */
    private var previewView: SurfaceView? = null
    private var lastUsedDetectedAt: Long? = null
    /** Sagoma del campo sull'anteprima e sua taratura (ricordata per campo: "Circolo · Campo" dell'arbitro). */
    private val outlineOn = MutableStateFlow(false)
    private val calibrating = MutableStateFlow(false)
    private val calib = MutableStateFlow(Framing.Calib())
    private var calibPlace: String? = null
    private var wasLevel = false
    private var previewJob: kotlinx.coroutines.Job? = null

    private enum class LiveState { OFF, CONNECTING, ON }

    private var stream: GenericStream? = null
    private var prepared = false
    private val overlay = ImageFilterRender()
    private var overlayAttached = false
    private var recordFile: File? = null
    private var recordStartWall = 0L
    private val chapters = mutableListOf<PointMark>()

    private val checker = object : ConnectChecker {
        override fun onConnectionStarted(url: String) = runOnUiThread { live.value = LiveState.CONNECTING }
        override fun onConnectionSuccess() = runOnUiThread { live.value = LiveState.ON }
        override fun onConnectionFailed(reason: String) = runOnUiThread {
            Log.w(TAG, "diretta non riuscita: $reason")
            stream?.stopStream()
            live.value = LiveState.OFF
            message.value = s.cameraLiveFailed(reason)
        }
        override fun onDisconnect() = runOnUiThread { if (live.value != LiveState.OFF) live.value = LiveState.OFF }
        override fun onAuthError() = runOnUiThread { message.value = s.cameraLiveFailed("auth") }
        override fun onAuthSuccess() {}
    }

    private val perms = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refreshPerms() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.setBackgroundColor(Color.BLACK)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        overlayOn.value = controller.storage.cameraOverlay
        calib.value = controller.storage.framingCalib
        lensIndex.value = lenses.indexOfFirst { it.id == controller.storage.cameraLensId }.coerceAtLeast(0)
        if (serveMode) startServeTraining()
        refreshPerms()
        if (!permsOk.value) perms.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
        setContent { TsmTheme { androidx.compose.runtime.CompositionLocalProvider(LocalStrings provides s) { Content() } } }
        // Grafica: si ridisegna a ogni punteggio nuovo dal telefono dell'arbitro.
        lifecycleScope.launch {
            controller.relay.score.collect {
                updateOverlay()
                loadCalibFor(it?.title.orEmpty())
            }
        }
        // Capitoli: i punti arrivati mentre si registra.
        lifecycleScope.launch {
            controller.relay.points.collect { p ->
                if (recording.value) chapters += p.copy(at = p.at - recordStartWall)
                // Per la moviola: gli ultimi punti con la loro ora (anche senza registrare la partita).
                recentPoints.addLast(p)
                while (recentPoints.size > 30) recentPoints.removeFirst()
            }
        }
        // Comandi dall'arbitro, e lo stato che gli torna: a ogni cambio e ogni 3 s (durata, batteria, spazio).
        lifecycleScope.launch { controller.relay.camCommands.collect { onRemote(it) } }
        lifecycleScope.launch {
            kotlinx.coroutines.flow.combine(recording, live, overlayOn, zoom, message) { _, _, _, _, _ -> Unit }.collect { sendState() }
        }
        lifecycleScope.launch { kotlinx.coroutines.flow.combine(lensIndex, levelOn, outlineOn, calib) { _, _, _, _ -> Unit }.collect { sendState() } }
        // Livella: all'arbitro due volte al secondo; una vibrazione quando inclinazione e livella diventano giuste.
        lifecycleScope.launch {
            tiltSensor.tilt.collect { t ->
                if (t == null) return@collect
                val now = SystemClock.elapsedRealtime()
                if (now - lastTiltSent >= 500) {
                    lastTiltSent = now
                    sendState()
                }
                val target = framingTarget()
                val ok = Framing.grade(t.roll) == 0 && (target == null || Framing.grade(t.down + calib.value.pitchOffset - target) == 0)
                if (ok && !wasLevel) buzz()
                wasLevel = ok
            }
        }
        lifecycleScope.launch {
            while (true) {
                delay(3_000)
                sendState()
            }
        }
        // Moviola chiesta dall'arbitro: gli ultimi secondi, subito, al suo telefono.
        lifecycleScope.launch {
            controller.relay.challenges.collect { ch ->
                val out = File(cacheDir, "replay_out/${ch.rid}")
                // Un punto preciso: dalla fine di quello prima a 3 s dopo la sua fine, anche se la richiesta arriva tardi.
                // back = -1: il punto contestato non è ancora segnato, dall'ultimo segnato a adesso.
                val pts = recentPoints.toList()
                val target = ch.back?.takeIf { it >= 0 }?.let { pts.getOrNull(pts.lastIndex - it) }
                val parts = withContext(Dispatchers.IO) {
                    runCatching {
                        if (ch.back == -1) {
                            val from = pts.lastOrNull()?.at?.minus(1_000) ?: (ch.at - 60_000)
                            recorder.cutRange(maxOf(from, ch.at - 110_000), ch.at, out)
                        } else if (target != null) {
                            val prevEnd = pts.getOrNull(pts.indexOf(target) - 1)?.at ?: (target.at - 20_000)
                            recorder.cutRange(maxOf(prevEnd, target.at - 40_000), target.at + 3_000, out)
                        } else {
                            recorder.cut(ch.at, ch.seconds * 1000L, out)
                        }
                    }.getOrDefault(emptyList())
                }
                controller.relay.sendReplay(ch.rid, parts)
                message.value = if (parts.isEmpty()) s.cameraReplayNone else s.cameraReplaySent
            }
        }
    }

    private fun refreshPerms() {
        permsOk.value = listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
            .all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
    }

    /** Registrazione nostra: video della partita a comando, e sempre gli ultimi secondi per la moviola. */
    private val recorder by lazy { ReplayRecorder(File(cacheDir, "replay_buffer")) }
    private val recentPoints = ArrayDeque<PointMark>()

    private fun stream(): GenericStream = stream ?: GenericStream(this, checker).also {
        it.getGlInterface().autoHandleOrientation = true
        it.setRecordController(recorder)
        stream = it
    }

    private fun prepare(): Boolean {
        if (prepared) return true
        val st = stream()
        val rotation = CameraHelper.getCameraOrientation(this)
        prepared = st.prepareVideo(WIDTH, HEIGHT, VIDEO_BITRATE, rotation = rotation) && st.prepareAudio(44_100, true, 128_000)
        if (!prepared) message.value = s.cameraNotSupported
        return prepared
    }

    // ---------------------------------------------------------------- allenamento al servizio

    private fun startServeTraining() {
        controller.serveTraining = true
        val stroke = runCatching { TrainedStroke.valueOf(intent.getStringExtra(EXTRA_STROKE).orEmpty()) }.getOrDefault(TrainedStroke.SERVE)
        // Le clip vanno in Documenti/Tennis Score Manager/Allenamento colpi/<colpo>/<data e ora>.
        val folder = controller.storage.newServeFolder(stroke, serveSession.value.startedAt, s.strokeFolderName, s.strokeName)
        serveSession.value = serveSession.value.copy(stroke = stroke, folder = folder)
        // La prima volta con questo colpo si mostra subito dove mettere il telefono.
        if (controller.storage.loadServeSessions().none { it.stroke == stroke }) placementHelp.value = true
        lifecycleScope.launch {
            controller.ble.events.collect { e ->
                // In pausa KEY1 riprende e gli altri tasti non fanno niente (niente colpi segnati per sbaglio).
                if (trainPaused.value) {
                    if (e.type == BandProtocol.EVT_POINT) resumeTraining(e.side)
                    return@collect
                }
                when (e.type) {
                    BandProtocol.EVT_POINT -> serveOutcome(true, e.side)
                    BandProtocol.EVT_UNDO -> serveOutcome(false, e.side)
                    BandProtocol.EVT_BATTERY -> undoServe(e.side)
                }
            }
        }
        // In un allenamento al servizio ogni colpo riconosciuto è un servizio, anche se il firmware lo scambia per
        // un dritto: conta l'istante, e i picchi del polso.
        lifecycleScope.launch {
            controller.ble.strokes.collect { st ->
                // Dopo la pausa automatica si ricomincia a giocare: si riprende da soli (questo colpo non ha la clip).
                if (trainPaused.value) {
                    if (autoPaused) resumeTraining()
                    return@collect
                }
                lastTrainActivity = System.currentTimeMillis()
                lastDetected = ServeTraining.Detected(System.currentTimeMillis(), st.stroke.peakGyroDps, st.stroke.peakAccelG)
                servePending.value = true
            }
        }
        // Pausa automatica: dopo qualche minuto senza colpi si spegne la telecamera (batteria, calore).
        lifecycleScope.launch {
            while (true) {
                delay(5_000)
                if (!trainPaused.value && serveSummary.value.not() && System.currentTimeMillis() - lastTrainActivity > AUTO_PAUSE_MS) {
                    pauseTraining(auto = true)
                }
            }
        }
    }

    private fun endServeTraining() {
        controller.serveTraining = false
        val sess = serveSession.value
        if (sess.serves.isEmpty()) controller.storage.deleteServeSession(sess)
        else controller.storage.saveServeSession(sess.copy(endedAt = sess.endedAt ?: System.currentTimeMillis()))
        controller.refreshServeSessions()
    }

    private fun saveServes(transform: (List<ServeAttempt>) -> List<ServeAttempt>) {
        val sess = serveSession.value.let { it.copy(serves = transform(it.serves)) }
        serveSession.value = sess
        lifecycleScope.launch(Dispatchers.IO) { controller.storage.saveServeSession(sess) }
    }

    /**
     * Dentro o fuori, dal braccialetto o dallo schermo. L'istante è quello del servizio riconosciuto (se c'è), e la
     * clip si prende quando la memoria della moviola ha anche i 3 secondi dopo.
     */
    /** Spegne telecamera e memoria; i tasti del braccialetto non segnano finché non si riprende. */
    private fun pauseTraining(auto: Boolean = false) {
        if (trainPaused.value) return
        trainPaused.value = true
        autoPaused = auto
        servePending.value = false
        stream?.let { st ->
            if (st.isRecording) st.stopRecord()
            if (st.isOnPreview) st.stopPreview()
        }
        say(if (auto) s.trainSayAutoPaused else s.trainSayPaused)
        bandShow(null, s.trainBandPaused, s.trainBandResume)
    }

    /** Riaccende telecamera e memoria: le clip ripartono dai colpi seguenti. */
    private fun resumeTraining(band: Side? = null) {
        if (!trainPaused.value) return
        trainPaused.value = false
        autoPaused = false
        lastTrainActivity = System.currentTimeMillis()
        val v = previewView
        stream?.let { st ->
            if (v != null && !st.isOnPreview && v.holder.surface.isValid) {
                st.startPreview(v)
                openLens()
            }
        }
        startBuffer()
        say(s.trainSayResumed)
        val n = ServeTraining.count(serveSession.value.serves)
        bandShow(band, s.trainBandGo, "${n.inPlay}/${n.total}")
    }

    private fun serveOutcome(inPlay: Boolean, band: Side? = null) {
        if (trainPaused.value) return
        val now = System.currentTimeMillis()
        lastTrainActivity = now
        val (at, used) = ServeTraining.anchor(now, lastDetected, lastUsedDetectedAt)
        used?.let { lastUsedDetectedAt = it.at }
        servePending.value = false
        val clip = "%03d".format(serveSession.value.serves.size + 1) + "_$at"
        val serve = trained == TrainedStroke.SERVE
        saveServes {
            it + ServeAttempt(
                at, inPlay, serveSide.value, serveKind.value, serveTarget.value.takeIf { serve }, used != null, used?.gyroDps, used?.accelG,
                direction = shotDirection.value.takeIf { !serve }, wing = volleyWing.value.takeIf { trained == TrainedStroke.VOLLEY },
            )
        }
        val n = ServeTraining.count(serveSession.value.serves)
        say(if (inPlay) s.serveSayIn(n.inPlay, n.total) else s.serveSayOut(n.inPlay, n.total))
        bandShow(band, if (inPlay) s.serveIn else s.serveOut, "${n.inPlay}/${n.total}" + (n.pct?.let { "  $it%" } ?: ""))
        val st = stream ?: return
        if (!st.isOnPreview) return
        lifecycleScope.launch {
            val wait = at + ServeTraining.CLIP_AFTER_MS + 600 - System.currentTimeMillis()
            if (wait > 0) delay(wait)
            val dir = File(controller.storage.serveClipsDir(serveSession.value), clip)
            val parts = withContext(Dispatchers.IO) {
                // I pezzi da 4 s si uniscono in un video solo (si apre e si condivide); se non riesce restano i pezzi.
                val tmp = File(cacheDir, "serve_cut_$clip")
                val cut = runCatching { recorder.cutRange(at - ServeTraining.CLIP_BEFORE_MS, at + ServeTraining.CLIP_AFTER_MS, tmp) }.getOrDefault(emptyList())
                val ok = cut.isNotEmpty() && (ClipJoin.joinDir(tmp, dir) || runCatching { tmp.copyRecursively(dir, overwrite = true) }.getOrDefault(false))
                tmp.deleteRecursively()
                if (ok) cut else emptyList()
            }
            // Annullato nel frattempo: la clip non serve più.
            if (serveSession.value.serves.none { it.at == at } || parts.isEmpty()) {
                withContext(Dispatchers.IO) { dir.deleteRecursively() }
                return@launch
            }
            saveServes { list -> list.map { if (it.at == at) it.copy(clip = clip) else it } }
            trainBytes.value = withContext(Dispatchers.IO) { controller.storage.serveVideoBytes(serveSession.value) }
        }
    }

    private fun undoServe(band: Side? = null) {
        lastTrainActivity = System.currentTimeMillis()
        val last = serveSession.value.serves.lastOrNull() ?: return
        saveServes { it.dropLast(1) }
        last.clip?.let { c -> lifecycleScope.launch(Dispatchers.IO) { File(controller.storage.serveClipsDir(serveSession.value), c).deleteRecursively() } }
        say(s.serveSayUndo)
        val n = ServeTraining.count(serveSession.value.serves)
        bandShow(band, s.serveSayUndo, "${n.inPlay}/${n.total}")
    }

    private fun say(text: String) {
        if (controller.options.value.audio) controller.announcer.announce(listOf(com.tennis.scoremanager.voice.Seg.Say(text)), force = true)
    }

    /** Sul display del braccialetto che ha premuto (o di quello collegato): esito e conteggio per 3 secondi. */
    private fun bandShow(band: Side?, line1: String, line2: String) {
        val side = band ?: Side.entries.firstOrNull { controller.ble.isReady(it) } ?: return
        controller.ble.send(side, BandProtocol.message(line1.uppercase(), line2, 3))
    }

    // ---------------------------------------------------------------- comandi dall'arbitro

    private fun onRemote(m: RelayMsg) {
        when (m.cmd) {
            CamCmd.PREVIEW -> {
                previewUntil = SystemClock.elapsedRealtime() + CamCmd.PREVIEW_TTL_MS
                if (previewJob?.isActive != true) previewJob = lifecycleScope.launch { sendPreview() }
            }
            CamCmd.PREVIEW_OFF -> previewUntil = 0L
            CamCmd.REC_START -> if (!recording.value) toggleRecord()
            CamCmd.REC_STOP -> if (recording.value) toggleRecord()
            CamCmd.LIVE_START -> if (live.value == LiveState.OFF) toggleLive { message.value = s.cameraNoStreamKey }
            CamCmd.LIVE_STOP -> if (live.value != LiveState.OFF) toggleLive {}
            CamCmd.OVERLAY_ON -> setOverlay(true)
            CamCmd.OVERLAY_OFF -> setOverlay(false)
            CamCmd.ZOOM -> m.zoom?.let { setZoom(it) }
            CamCmd.LENS_NEXT -> if (lenses.size > 1) useLens((lensIndex.value + 1) % lenses.size)
            CamCmd.ASSIST_ON -> setLevel(true)
            CamCmd.ASSIST_OFF -> setLevel(false)
            CamCmd.OUTLINE_ON -> setOutline(true)
            CamCmd.OUTLINE_OFF -> setOutline(false)
            CamCmd.CALIB -> m.calib?.let { setCalib(it) }
        }
    }

    // ---------------------------------------------------------------- assistente inquadratura e obiettivo

    /** Inclinazione consigliata con le misure della taratura e l'obiettivo in uso. */
    private fun framingTarget(): Float? {
        val c = calib.value
        return Framing.plan(c.distance, c.height, c.courtType, lenses.getOrNull(lensIndex.value)).tilt
    }

    private fun setLevel(on: Boolean) {
        if (on && !tiltSensor.available) {
            message.value = s.assistNoSensor
            return
        }
        levelOn.value = on
        wasLevel = false
        updateSensor()
    }

    /** La sagoma segue l'inclinazione del telefono: serve il sensore anche a livella spenta. */
    private fun setOutline(on: Boolean) {
        if (on && !tiltSensor.available) {
            message.value = s.assistNoSensor
            return
        }
        outlineOn.value = on
        if (!on) calibrating.value = false
        updateSensor()
    }

    private var sensorOn = false

    private fun updateSensor() {
        val want = levelOn.value || outlineOn.value
        if (want == sensorOn) return
        sensorOn = want
        if (want) tiltSensor.start() else tiltSensor.stop()
    }

    /** Nuova taratura: la usa subito, la ricorda come attuale e per il campo della partita (se si sa). */
    private fun setCalib(c: Framing.Calib) {
        calib.value = c
        controller.storage.framingCalib = c
        calibPlace?.let { controller.storage.saveFramingCalibFor(it, c) }
    }

    /** Arrivato il campo della partita: se lì c'è già una taratura, si riprende quella. */
    private fun loadCalibFor(place: String) {
        if (place.isBlank() || place == calibPlace) return
        calibPlace = place
        val saved = controller.storage.framingCalibFor(place) ?: return
        if (saved != calib.value) {
            calib.value = saved
            controller.storage.framingCalib = saved
            message.value = s.outlineLoaded(place)
        }
    }

    private fun canChangeLens(): Boolean = !recording.value && live.value == LiveState.OFF

    /** Passa all'obiettivo [index]: solo a registrazione e diretta ferme (il cambio interrompe il video un attimo). */
    private fun useLens(index: Int) {
        val lens = lenses.getOrNull(index) ?: return
        if (!canChangeLens()) {
            message.value = s.cameraLensBusy
            return
        }
        controller.storage.cameraLensId = lens.id
        lensIndex.value = index
        openLens()
    }

    private fun openLens() {
        val cam = camera() ?: return
        val id = lenses.getOrNull(lensIndex.value)?.id ?: return
        if (runCatching { cam.getCurrentCameraId() }.getOrNull() == id) return
        runCatching { cam.openCameraId(id) }.onFailure { Log.w(TAG, "obiettivo $id non aperto", it) }
        zoom.value = 1f
    }

    private fun buzz() {
        runCatching {
            val v = if (Build.VERSION.SDK_INT >= 31) getSystemService(android.os.VibratorManager::class.java).defaultVibrator
            else @Suppress("DEPRECATION") getSystemService(android.os.Vibrator::class.java)
            v.vibrate(android.os.VibrationEffect.createOneShot(120, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    private fun camera(): Camera2Source? = stream?.videoSource as? Camera2Source

    private fun setZoom(z: Float) {
        val cam = camera() ?: return
        val r = runCatching { cam.getZoomRange() }.getOrNull() ?: return
        val v = z.coerceIn(r.lower, r.upper)
        runCatching { cam.setZoom(v) }
        zoom.value = v
    }

    private fun sendState() {
        val cam = camera()
        val range = cam?.let { runCatching { it.getZoomRange() }.getOrNull() }
        val battery = runCatching {
            getSystemService(android.os.BatteryManager::class.java).getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
        }.getOrNull()?.takeIf { it in 0..100 }
        val free = runCatching { (getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: filesDir).usableSpace / (1024 * 1024) }.getOrNull()
        controller.relay.sendCamState(
            CamState(
                recording = recording.value,
                recMs = if (recording.value) SystemClock.elapsedRealtime() - recordSince.value else 0L,
                live = when (live.value) { LiveState.OFF -> 0; LiveState.CONNECTING -> 1; LiveState.ON -> 2 },
                liveReady = controller.storage.streamKey.isNotEmpty(),
                overlay = overlayOn.value,
                zoom = zoom.value,
                zoomMin = range?.lower ?: 1f,
                zoomMax = range?.upper ?: 1f,
                battery = battery,
                freeMb = free,
                message = message.value,
                lens = lensIndex.value,
                lenses = lenses.size.coerceAtLeast(1),
                tilt = tiltSensor.tilt.value?.down?.takeIf { levelOn.value || outlineOn.value },
                roll = tiltSensor.tilt.value?.roll?.takeIf { levelOn.value || outlineOn.value },
                tiltTarget = framingTarget()?.takeIf { levelOn.value },
                outline = outlineOn.value,
                calib = calib.value,
                hFov = lenses.getOrNull(lensIndex.value)?.hFov,
                vFov = lenses.getOrNull(lensIndex.value)?.vFov,
            ),
        )
    }

    /**
     * Inquadratura all'arbitro finché la chiede: un fotogramma piccolo (640×360, JPEG) circa tre volte al secondo,
     * preso dopo la grafica, cioè come finisce nel video.
     */
    private suspend fun sendPreview() {
        while (SystemClock.elapsedRealtime() < previewUntil) {
            val st = stream
            if (st != null && st.isOnPreview) {
                val bmp = kotlinx.coroutines.withTimeoutOrNull(1_500) {
                    kotlinx.coroutines.suspendCancellableCoroutine<android.graphics.Bitmap?> { cont ->
                        runCatching { st.getGlInterface().takePhoto { b -> if (cont.isActive) cont.resumeWith(Result.success(b)) } }
                            .onFailure { if (cont.isActive) cont.resumeWith(Result.success(null)) }
                    }
                }
                if (bmp != null) {
                    val jpeg = withContext(Dispatchers.Default) {
                        val small = android.graphics.Bitmap.createScaledBitmap(bmp, PREVIEW_W, PREVIEW_W * bmp.height / bmp.width.coerceAtLeast(1), true)
                        ByteArrayOutputStream().also { small.compress(android.graphics.Bitmap.CompressFormat.JPEG, 55, it) }.toByteArray()
                    }
                    controller.relay.sendCamFrame(jpeg)
                }
            }
            delay(PREVIEW_EVERY_MS)
        }
    }

    private fun setOverlay(on: Boolean) {
        controller.storage.cameraOverlay = on
        overlayOn.value = on
        updateOverlay()
    }

    // ---------------------------------------------------------------- grafica

    private fun updateOverlay() {
        val st = stream ?: return
        val o = controller.relay.score.value
        if (!controller.storage.cameraOverlay || o == null) {
            if (overlayAttached) {
                st.getGlInterface().clearFilters()
                overlayAttached = false
            }
            return
        }
        if (!overlayAttached) {
            st.getGlInterface().setFilter(overlay)
            overlayAttached = true
        }
        // 1,2: da lontano (telefono dietro il campo, video guardato sul telefono) le misure "da TV" sono piccole.
        val bmp = OverlayRenderer.render(o, scale = 1.2f)
        overlay.setImage(bmp)
        // In percentuale del fotogramma: in basso a sinistra, con un margine da "zona sicura" televisiva.
        val w = bmp.width * 100f / WIDTH
        val h = bmp.height * 100f / HEIGHT
        overlay.setScale(w, h)
        overlay.setPosition(3f, 100f - h - 5f)
    }

    // ---------------------------------------------------------------- registrazione e diretta

    /** Il codificatore gira sempre (per la memoria della moviola): "Registra" apre e chiude solo il file della partita. */
    private fun startBuffer() {
        val st = stream ?: return
        if (!st.isRecording) st.startRecord(File(cacheDir, "replay_buffer").absolutePath) { }
    }

    private fun toggleRecord() {
        if (stream == null) return
        if (recording.value) {
            recorder.stopFile()
            recording.value = false
            finishRecording()
            return
        }
        if (!prepare()) return
        startBuffer()
        val dir = getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: filesDir
        val file = File(dir, "TSM_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(Date())}.mp4")
        recordFile = file
        chapters.clear()
        recordStartWall = System.currentTimeMillis()
        recorder.startFile(file)
        recording.value = true
        recordSince.value = SystemClock.elapsedRealtime()
    }

    /** Capitoli accanto al video e copia del video nella galleria (Film/Tennis Score Manager). */
    private fun finishRecording() {
        val file = recordFile ?: return
        val marks = chapters.toList()
        lifecycleScope.launch {
            val started = recordStartWall
            val names = controller.relay.score.value?.names.orEmpty()
            val ok = withContext(Dispatchers.IO) {
                val json = File(file.parentFile, file.nameWithoutExtension + ".json")
                fun save(uri: String?) = json.writeText(Json.encodeToString(VideoChapters.serializer(), VideoChapters(file.name, marks, uri, started, names)))
                runCatching {
                    save(null)
                    if (Build.VERSION.SDK_INT >= 29) {
                        val values = ContentValues().apply {
                            put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
                            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                            put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/Tennis Score Manager")
                        }
                        val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: error("insert")
                        contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } } ?: error("open")
                        // Una copia sola: un'ora di partita sono 3-4 GB. I capitoli restano qui e puntano alla galleria.
                        save(uri.toString())
                        file.delete()
                    }
                }.onFailure { Log.w(TAG, "copia del video non riuscita", it) }.isSuccess
            }
            message.value = if (ok) s.cameraSaved(marks.size) else s.cameraSaveFailed(file.absolutePath)
        }
    }

    private fun toggleLive(openSettings: () -> Unit) {
        val st = stream ?: return
        if (live.value != LiveState.OFF) {
            st.stopStream()
            live.value = LiveState.OFF
            return
        }
        val key = controller.storage.streamKey
        if (key.isEmpty()) {
            openSettings()
            return
        }
        if (!prepare()) return
        st.startStream(controller.storage.streamUrl.trimEnd('/') + "/" + key)
    }

    override fun onDestroy() {
        tiltSensor.stop()
        if (serveMode) endServeTraining()
        stream?.let { st ->
            if (recorder.fileActive) {
                recorder.stopFile()
                finishRecording()
            }
            if (st.isRecording) st.stopRecord()
            if (st.isStreaming) st.stopStream()
            if (st.isOnPreview) st.stopPreview()
            st.release()
        }
        stream = null
        super.onDestroy()
    }

    // ---------------------------------------------------------------- interfaccia

    @Composable
    private fun Content() {
        val ok by permsOk.collectAsState()
        val str = s
        if (!ok) {
            Box(Modifier.fillMaxSize().background(TsmColors.Background), contentAlignment = Alignment.Center) {
                Text(str.cameraPermissions, color = TsmColors.TextMain, modifier = Modifier.padding(32.dp))
            }
            return
        }
        var settings by remember { mutableStateOf(false) }
        var framing by remember { mutableStateOf(false) }
        Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)) {
            // L'anteprima ha le proporzioni del video (16:9): quello che si vede è quello che si registra, e la sagoma
            // del campo cade esattamente sulle linee.
            Box(Modifier.align(Alignment.Center).fillMaxSize().wrapContentSize().aspectRatio(16f / 9f, matchHeightConstraintsFirst = true)) {
            AndroidView(factory = { ctx ->
                SurfaceView(ctx).apply {
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(h: SurfaceHolder) {
                            previewView = this@apply
                            if (!prepare()) return
                            // In pausa la telecamera resta spenta anche tornando nell'app.
                            if (trainPaused.value) return
                            val st = stream()
                            if (!st.isOnPreview) st.startPreview(this@apply)
                            openLens()
                            startBuffer()
                            updateOverlay()
                        }

                        override fun surfaceChanged(h: SurfaceHolder, format: Int, w: Int, hh: Int) {
                            stream?.getGlInterface()?.setPreviewResolution(w, hh)
                        }

                        override fun surfaceDestroyed(h: SurfaceHolder) {
                            stream?.let { if (it.isOnPreview) it.stopPreview() }
                        }
                    })
                }
            }, modifier = Modifier.fillMaxSize())
            Outline()
            }
            Level()
            // In pausa l'anteprima resta ferma sull'ultima immagine: un velo scuro dice che la telecamera è spenta.
            if (serveMode) {
                val paused by trainPaused.collectAsState()
                if (paused) {
                    Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.75f)), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.Pause, null, tint = TsmColors.Orange, modifier = Modifier.size(64.dp))
                            Text(str.trainBandPaused, color = TsmColors.TextMain, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            if (serveMode) ServeControls() else {
                Controls(onSettings = { settings = true }, onFraming = { framing = true })
                Calibration()
            }
        }
        if (settings) StreamSettings { settings = false }
        if (framing) {
            val lv by levelOn.collectAsState()
            val li by lensIndex.collectAsState()
            val ol by outlineOn.collectAsState()
            FramingDialog(
                controller.storage, lenses, li, lv, canChangeLens(), onUseLens = { useLens(it) }, onLevel = { setLevel(it) },
                outlineOn = ol, onOutline = { on -> setOutline(on); calibrating.value = on },
            ) {
                framing = false
                // Distanza, altezza e campo scritti nella finestra fanno parte della taratura.
                setCalib(controller.storage.framingCalib)
            }
        }
    }

    /** Livella sull'anteprima (non finisce nel video): orizzonte e indicazioni, al centro. */
    @Composable
    private fun Level() {
        val on by levelOn.collectAsState()
        val t by tiltSensor.tilt.collectAsState()
        val li by lensIndex.collectAsState()
        val tilt = t ?: return
        if (!on) return
        Box(Modifier.fillMaxSize()) {
            HorizonOverlay(tilt.roll)
            LevelReadout(tilt.down + calib.collectAsState().value.pitchOffset, tilt.roll, framingTarget(), Modifier.align(Alignment.Center).padding(top = 80.dp))
        }
    }

    /** Sagoma del campo sopra l'anteprima (non finisce nel video). */
    @Composable
    private fun Outline() {
        val on by outlineOn.collectAsState()
        val t by tiltSensor.tilt.collectAsState()
        val c by calib.collectAsState()
        val li by lensIndex.collectAsState()
        val lens = lenses.getOrNull(li) ?: return
        val tilt = t ?: return
        if (on) CourtOutline(c, lens.hFov, lens.vFov, tilt.down, tilt.roll)
    }

    /** Cursori della taratura a sinistra, sopra l'anteprima: si vedono le linee spostarsi mentre li si muove. */
    @Composable
    private fun Calibration() {
        val on by calibrating.collectAsState()
        val c by calib.collectAsState()
        if (!on) return
        Column(
            Modifier.fillMaxHeight().width(320.dp).background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f))
                .verticalScroll(rememberScrollState()).padding(12.dp),
        ) {
            Text(s.outlineTitle, color = TsmColors.TextMain, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            CalibrationPanel(c, { setCalib(it) })
            TextButton(onClick = { calibrating.value = false }) { Text(s.outlineDone) }
        }
    }

    /**
     * Allenamento al servizio: in alto braccialetto e conteggio, in basso dentro, fuori, annulla, lato, prima o
     * seconda, bersaglio, obiettivo, dove mettere il telefono e fine.
     */
    @Composable
    private fun ServeControls() {
        val str = s
        val sess by serveSession.collectAsState()
        val pending by servePending.collectAsState()
        val bands by controller.ble.bands.collectAsState()
        val side by serveSide.collectAsState()
        val kind by serveKind.collectAsState()
        val target by serveTarget.collectAsState()
        val li by lensIndex.collectAsState()
        val msg by message.collectAsState()
        val paused by trainPaused.collectAsState()
        val bytes by trainBytes.collectAsState()
        val n = ServeTraining.count(sess.serves)
        val band = bands.entries.firstOrNull { it.value.state == com.tennis.scoremanager.ble.LinkState.READY }?.value
        // Spiegazioni lunghe solo all'inizio (8 s) o toccando "i": il resto del tempo l'inquadratura resta libera.
        var info by remember { mutableStateOf(true) }
        LaunchedEffect(info) { if (info) { delay(8_000); info = false } }
        // Anche i messaggi spariscono da soli.
        LaunchedEffect(msg) {
            val m = msg ?: return@LaunchedEffect
            delay(5_000)
            if (message.value == m) message.value = null
        }
        val serve = sess.stroke == TrainedStroke.SERVE
        val st = band?.settings
        val recColor = when {
            st == null -> TsmColors.SurfaceHigh
            !st.supportsStrokes -> TsmColors.Orange
            st.strokes -> TsmColors.Ok
            else -> TsmColors.SurfaceHigh
        }
        val clips = sess.serves.count { it.clip != null }
        val mb = String.format(java.util.Locale.ROOT, "%.0f MB", bytes / 1_048_576.0)
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Una riga sola, piccola: conteggio, ultimo colpo, braccialetto, riconoscimento, memoria, "i".
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Pill(str.serveCountLine(n.inPlay, n.total) + (n.pct?.let { " · $it%" } ?: ""), TsmColors.SurfaceHigh)
                    sess.serves.lastOrNull()?.let { Pill(if (it.inPlay) str.serveIn else str.serveOut, if (it.inPlay) TsmColors.Ok else TsmColors.Danger) }
                    Chip(Icons.Filled.Watch, null, if (band != null) TsmColors.Ok else TsmColors.Orange)
                    if (band != null) Chip(Icons.Filled.Sensors, null, recColor)
                    if (paused) Chip(Icons.Filled.Pause, null, TsmColors.Orange)
                    else Chip(Icons.Filled.FiberManualRecord, "$clips · $mb", TsmColors.SurfaceHigh, tint = TsmColors.Danger)
                    Chip(Icons.Filled.Info, null, if (info) TsmColors.Ball else TsmColors.SurfaceHigh, tint = if (info) TsmColors.OnBall else TsmColors.TextMain) { info = !info }
                }
                // Colpo riconosciuto in attesa di dentro/fuori: serve vederlo, resta finché non si risponde.
                if (pending && !paused) Pill(strokeLabel(str, sess.stroke) + " · " + if (serve) str.serveDetected else str.strokeDetected, TsmColors.Orange)
                if (info) {
                    Column(
                        Modifier.clip(RoundedCornerShape(12.dp)).background(TsmColors.Surface.copy(alpha = 0.85f)).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        @Composable fun line(t: String) = Text(t, color = TsmColors.TextMain, fontSize = 13.sp)
                        line(band?.let { str.serveBandOk(it.name) } ?: str.serveNoBand)
                        line(strokeLabel(str, sess.stroke) + " · " + if (serve) str.serveWaiting else str.strokeWaiting)
                        st?.let { line(if (!it.supportsStrokes) str.trainRecOldFw else if (it.strokes) str.trainRecOn else str.trainRecStarting) }
                        line(if (paused) (if (autoPaused) str.trainAutoPaused else str.trainPaused) else str.trainMemory(clips, mb))
                    }
                }
                msg?.let { Pill(it, TsmColors.SurfaceHigh) }
            }
            Row(Modifier.align(Alignment.End).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (paused) {
                    Btn(Icons.Filled.PlayArrow, str.trainResume, TsmColors.Ok) { resumeTraining() }
                } else {
                    Btn(Icons.Filled.CheckCircle, str.serveIn, TsmColors.Ok) { serveOutcome(true) }
                    Btn(Icons.Filled.Cancel, str.serveOut, TsmColors.Danger) { serveOutcome(false) }
                    Btn(Icons.Filled.Pause, str.trainPause, TsmColors.SurfaceHigh) { pauseTraining() }
                }
                Btn(Icons.AutoMirrored.Filled.Undo, str.serveUndo, TsmColors.SurfaceHigh) { undoServe() }
                if (sess.stroke == TrainedStroke.SERVE) {
                    Btn(Icons.Filled.SwapHoriz, serveSideName(str, side), TsmColors.SurfaceHigh) {
                        serveSide.value = if (side == ServeSide.DEUCE) ServeSide.AD else ServeSide.DEUCE
                    }
                    Btn(Icons.Filled.LooksOne, serveKindName(str, kind), TsmColors.SurfaceHigh) {
                        serveKind.value = if (kind == ServeKind.FIRST) ServeKind.SECOND else ServeKind.FIRST
                    }
                    Btn(Icons.Filled.GpsFixed, serveTargetName(str, target), TsmColors.SurfaceHigh) {
                        serveTarget.value = when (target) { null -> ServeTarget.T; ServeTarget.T -> ServeTarget.BODY; ServeTarget.BODY -> ServeTarget.WIDE; ServeTarget.WIDE -> null }
                    }
                } else {
                    val dir by shotDirection.collectAsState()
                    val wing by volleyWing.collectAsState()
                    if (sess.stroke == TrainedStroke.VOLLEY) Btn(Icons.Filled.SwapHoriz, wingName(str, wing), TsmColors.SurfaceHigh) {
                        volleyWing.value = if (wing == Wing.FOREHAND) Wing.BACKHAND else Wing.FOREHAND
                    }
                    Btn(Icons.Filled.GpsFixed, directionName(str, dir), TsmColors.SurfaceHigh) {
                        shotDirection.value = when (dir) { null -> ShotDirection.CROSS; ShotDirection.CROSS -> ShotDirection.MIDDLE; ShotDirection.MIDDLE -> ShotDirection.LINE; ShotDirection.LINE -> null }
                    }
                }
                if (lenses.size > 1) Btn(Icons.Filled.Cameraswitch, lensName(str, li, lenses.size), TsmColors.SurfaceHigh) { useLens((li + 1) % lenses.size) }
                Btn(Icons.Filled.HelpOutline, str.servePlaceShow, TsmColors.SurfaceHigh) { placementHelp.value = true }
                Btn(Icons.Filled.Flag, str.serveEnd, TsmColors.SurfaceHigh) {
                    serveSession.value = serveSession.value.copy(endedAt = System.currentTimeMillis())
                    serveSummary.value = true
                }
            }
        }
        if (placementHelp.collectAsState().value) ServePlacementDialog(sess.stroke) { placementHelp.value = false }
        if (serveSummary.collectAsState().value) {
            AlertDialog(
                onDismissRequest = { serveSummary.value = false },
                title = { Text(str.serveSummary) },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ServeSummaryContent(str, sess.serves, sess.stroke)
                        ServeReplayButtons(controller, sess)
                    }
                },
                confirmButton = { TextButton(onClick = { serveSummary.value = false; finish() }) { Text(str.serveEnd) } },
                dismissButton = { TextButton(onClick = { serveSummary.value = false }) { Text(str.back) } },
            )
        }
    }

    @Composable
    private fun Controls(onSettings: () -> Unit, onFraming: () -> Unit) {
        val str = s
        val rec by recording.collectAsState()
        val since by recordSince.collectAsState()
        val lv by live.collectAsState()
        val msg by message.collectAsState()
        val relay by controller.relay.ui.collectAsState()
        val score by controller.relay.score.collectAsState()
        var now by remember { mutableStateOf(SystemClock.elapsedRealtime()) }
        LaunchedEffect(rec) {
            while (rec) {
                now = SystemClock.elapsedRealtime()
                delay(500)
            }
        }
        LaunchedEffect(msg) {
            if (msg != null) {
                delay(6_000)
                message.value = null
            }
        }
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (rec) Pill("● REC ${Reports.duration(now - since)}", TsmColors.Danger)
                if (lv == LiveState.ON) Pill("LIVE", TsmColors.Danger) else if (lv == LiveState.CONNECTING) Pill(str.cameraConnecting, TsmColors.Orange)
                if (relay.wifiOff && !relay.connected) {
                    Surface(onClick = { com.tennis.scoremanager.ui.openWifiPanel(this@CameraActivity) }, shape = RoundedCornerShape(10.dp), color = TsmColors.Orange) {
                        Text(str.relayWifiOn, color = TsmColors.OnBall, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                    }
                }
                Pill(
                    when {
                        !relay.camera -> str.cameraNoReferee
                        relay.wifiOff && !relay.connected -> str.relayWifiOff
                        !relay.connected -> str.relayDisconnected
                        score == null -> str.cameraWaitingScore
                        else -> str.cameraScoreOk
                    },
                    if (relay.camera && relay.connected && score != null) TsmColors.Ok else TsmColors.Orange,
                )
            }
            msg?.let { Pill(it, TsmColors.SurfaceHigh) }
            // Sette tasti: sui telefoni stretti la fila scorre invece di uscire dallo schermo.
            Row(Modifier.align(Alignment.End).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Btn(if (rec) Icons.Filled.Stop else Icons.Filled.FiberManualRecord, if (rec) str.cameraStopRec else str.cameraRec, TsmColors.Danger) { toggleRecord() }
                Btn(Icons.Filled.LiveTv, if (lv == LiveState.OFF) str.cameraGoLive else str.cameraStopLive, if (lv == LiveState.OFF) TsmColors.SurfaceHigh else TsmColors.Danger) {
                    toggleLive(onSettings)
                }
                Btn(Icons.Filled.Straighten, str.assistButton, if (levelOn.collectAsState().value) TsmColors.Ok else TsmColors.SurfaceHigh) { onFraming() }
                val ol by outlineOn.collectAsState()
                Btn(Icons.Filled.GridOn, str.outlineButton, if (ol) TsmColors.Ok else TsmColors.SurfaceHigh) {
                    if (ol && !calibrating.value) calibrating.value = true else { setOutline(!ol); calibrating.value = !ol }
                }
                val li by lensIndex.collectAsState()
                if (lenses.size > 1) Btn(Icons.Filled.Cameraswitch, lensName(str, li, lenses.size), TsmColors.SurfaceHigh) { useLens((li + 1) % lenses.size) }
                val ov by overlayOn.collectAsState()
                Btn(Icons.Filled.Layers, str.cameraOverlay, if (ov) TsmColors.Ok else TsmColors.SurfaceHigh) { setOverlay(!ov) }
                Btn(Icons.Filled.Settings, str.cameraStreamUrl, TsmColors.SurfaceHigh) { onSettings() }
                Btn(Icons.AutoMirrored.Filled.ExitToApp, str.cameraClose, TsmColors.SurfaceHigh) {
                    if (rec || lv != LiveState.OFF) Toast.makeText(this@CameraActivity, str.cameraStopFirst, Toast.LENGTH_SHORT).show() else finish()
                }
            }
        }
    }

    /** Segnale piccolo con un'icona (e a volte un numero): toccabile se c'è [onClick]. */
    @Composable
    private fun Chip(
        icon: androidx.compose.ui.graphics.vector.ImageVector, text: String?, color: androidx.compose.ui.graphics.Color,
        tint: androidx.compose.ui.graphics.Color = TsmColors.TextMain, onClick: (() -> Unit)? = null,
    ) {
        Row(
            Modifier.clip(RoundedCornerShape(10.dp)).background(color).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
            if (text != null) Text(" $text", color = TsmColors.TextMain, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }

    @Composable
    private fun Pill(text: String, color: androidx.compose.ui.graphics.Color) {
        Text(
            text, color = TsmColors.TextMain, fontWeight = FontWeight.Bold, fontSize = 14.sp,
            modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(color).padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }

    @Composable
    private fun Btn(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
        Surface(onClick = onClick, shape = RoundedCornerShape(14.dp), color = color) {
            Column(Modifier.width(92.dp).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(icon, null, tint = TsmColors.TextMain)
                Text(label, color = TsmColors.TextMain, fontSize = 11.sp, maxLines = 1)
            }
        }
    }

    /** Server e chiave della diretta: di serie YouTube (la chiave è in YouTube Studio › Trasmetti dal vivo). */
    @Composable
    private fun StreamSettings(onDone: () -> Unit) {
        val str = s
        var url by remember { mutableStateOf(controller.storage.streamUrl) }
        var key by remember { mutableStateOf(controller.storage.streamKey) }
        AlertDialog(
            onDismissRequest = onDone,
            title = { Text(str.cameraStreamSettings) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(str.cameraStreamHint, fontSize = 13.sp)
                    OutlinedTextField(url, { url = it }, label = { Text(str.cameraStreamUrl) }, singleLine = true)
                    OutlinedTextField(key, { key = it }, label = { Text(str.cameraStreamKey) }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    controller.storage.streamUrl = url
                    controller.storage.streamKey = key
                    onDone()
                }) { Text(str.ok) }
            },
        )
    }

    companion object {
        /** true = allenamento al servizio: telecamera e braccialetto sullo stesso telefono, senza arbitro. */
        const val EXTRA_SERVE = "serve"
        /** Colpo dell'allenamento (TrainedStroke, di serie il servizio). */
        const val EXTRA_STROKE = "stroke"
        /** Pausa automatica dell'allenamento dopo questo tempo senza colpi. */
        const val AUTO_PAUSE_MS = 5 * 60_000L
        private const val TAG = "Telecamera"
        private const val WIDTH = 1920
        private const val HEIGHT = 1080
        /** 6 Mbit/s: quanto chiede YouTube per il 1080p a 30 fotogrammi. */
        private const val VIDEO_BITRATE = 6_000_000
        /** Inquadratura per l'arbitro: larghezza dei fotogrammi e ogni quanto. */
        private const val PREVIEW_W = 640
        private const val PREVIEW_EVERY_MS = 330L
    }
}

/**
 * Capitoli di un video: un segno per punto, [PointMark.at] in ms dall'inizio della registrazione. [uri] è la copia
 * nella galleria (content://); senza, il video è ancora nella cartella dell'app col nome [video].
 */
@kotlinx.serialization.Serializable
data class VideoChapters(
    val video: String,
    val points: List<PointMark>,
    val uri: String? = null,
    val recordedAt: Long = 0L,
    /** Nomi dei due lati, dall'ultimo punteggio ricevuto. */
    val names: List<String> = emptyList(),
    /** Video preso dal telefono (non registrato da TSM): non si cancella mai, i punti si segnano guardandolo. */
    val imported: Boolean = false,
    /** Regole per ricalcolare il punteggio dai punti segnati dopo (null = punti arrivati dal vivo dall'arbitro). */
    val rules: com.tennis.scoremanager.model.RulesConfig? = null,
)
