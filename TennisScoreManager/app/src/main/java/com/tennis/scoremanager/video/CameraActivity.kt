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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import com.pedro.encoder.input.video.CameraHelper
import com.pedro.library.base.recording.RecordController
import com.pedro.library.generic.GenericStream
import com.tennis.scoremanager.TsmApp
import com.tennis.scoremanager.data.Reports
import com.tennis.scoremanager.ui.Strings
import com.tennis.scoremanager.ui.TsmColors
import com.tennis.scoremanager.ui.TsmTheme
import com.tennis.scoremanager.ui.stringsFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Telefono-telecamera: dietro il campo, al centro. Registra la partita in MP4 1080p e può trasmetterla in diretta
 * (RTMP: YouTube, Twitch, un server proprio), con la grafica del punteggio che arriva dal telefono dell'arbitro
 * (vedi PhoneRelay, ruolo "camera"). Ogni punto ricevuto durante la registrazione diventa un capitolo, salvato
 * accanto al video (file .json con lo stesso nome).
 */
class CameraActivity : ComponentActivity() {

    private val controller get() = (application as TsmApp).controller
    private val s: Strings get() = stringsFor(controller.options.value.lang)

    private val live = MutableStateFlow(LiveState.OFF)
    private val recording = MutableStateFlow(false)
    private val recordSince = MutableStateFlow(0L)
    private val message = MutableStateFlow<String?>(null)
    private val permsOk = MutableStateFlow(false)

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
        refreshPerms()
        if (!permsOk.value) perms.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
        setContent { TsmTheme { Content() } }
        // Grafica: si ridisegna a ogni punteggio nuovo dal telefono dell'arbitro.
        lifecycleScope.launch { controller.relay.score.collect { updateOverlay() } }
        // Capitoli: i punti arrivati mentre si registra.
        lifecycleScope.launch {
            controller.relay.points.collect { p -> if (recording.value) chapters += p.copy(at = p.at - recordStartWall) }
        }
    }

    private fun refreshPerms() {
        permsOk.value = listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
            .all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
    }

    private fun stream(): GenericStream = stream ?: GenericStream(this, checker).also {
        it.getGlInterface().autoHandleOrientation = true
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

    private fun toggleRecord() {
        val st = stream ?: return
        if (recording.value) {
            st.stopRecord()
            recording.value = false
            finishRecording()
            return
        }
        if (!prepare()) return
        val dir = getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: filesDir
        val file = File(dir, "TSM_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(Date())}.mp4")
        recordFile = file
        chapters.clear()
        recordStartWall = System.currentTimeMillis()
        st.startRecord(file.absolutePath) { status ->
            if (status == RecordController.Status.RECORDING) runOnUiThread { recordSince.value = SystemClock.elapsedRealtime() }
        }
        recording.value = true
        recordSince.value = SystemClock.elapsedRealtime()
    }

    /** Capitoli accanto al video e copia del video nella galleria (Film/Tennis Score Manager). */
    private fun finishRecording() {
        val file = recordFile ?: return
        val marks = chapters.toList()
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    File(file.parentFile, file.nameWithoutExtension + ".json")
                        .writeText(Json.encodeToString(VideoChapters.serializer(), VideoChapters(file.name, marks)))
                    if (Build.VERSION.SDK_INT >= 29) {
                        val values = ContentValues().apply {
                            put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
                            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                            put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/Tennis Score Manager")
                        }
                        val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: error("insert")
                        contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } } ?: error("open")
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
        stream?.let { st ->
            if (st.isRecording) {
                st.stopRecord()
                finishRecording()
            }
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
        Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)) {
            AndroidView(factory = { ctx ->
                SurfaceView(ctx).apply {
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(h: SurfaceHolder) {
                            if (!prepare()) return
                            val st = stream()
                            if (!st.isOnPreview) st.startPreview(this@apply)
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
            Controls(onSettings = { settings = true })
        }
        if (settings) StreamSettings { settings = false }
    }

    @Composable
    private fun Controls(onSettings: () -> Unit) {
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
            Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Btn(if (rec) Icons.Filled.Stop else Icons.Filled.FiberManualRecord, if (rec) str.cameraStopRec else str.cameraRec, TsmColors.Danger) { toggleRecord() }
                Btn(Icons.Filled.LiveTv, if (lv == LiveState.OFF) str.cameraGoLive else str.cameraStopLive, if (lv == LiveState.OFF) TsmColors.SurfaceHigh else TsmColors.Danger) {
                    toggleLive(onSettings)
                }
                Btn(Icons.Filled.Layers, str.cameraOverlay, if (controller.storage.cameraOverlay) TsmColors.Ok else TsmColors.SurfaceHigh) {
                    controller.storage.cameraOverlay = !controller.storage.cameraOverlay
                    updateOverlay()
                }
                Btn(Icons.Filled.Settings, str.cameraStreamUrl, TsmColors.SurfaceHigh) { onSettings() }
                Btn(Icons.AutoMirrored.Filled.ExitToApp, str.cameraClose, TsmColors.SurfaceHigh) {
                    if (rec || lv != LiveState.OFF) Toast.makeText(this@CameraActivity, str.cameraStopFirst, Toast.LENGTH_SHORT).show() else finish()
                }
            }
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
        private const val TAG = "Telecamera"
        private const val WIDTH = 1920
        private const val HEIGHT = 1080
        /** 6 Mbit/s: quanto chiede YouTube per il 1080p a 30 fotogrammi. */
        private const val VIDEO_BITRATE = 6_000_000
    }
}

/** Capitoli di un video: un segno per punto, [PointMark.at] in ms dall'inizio della registrazione. */
@kotlinx.serialization.Serializable
data class VideoChapters(val video: String, val points: List<PointMark>)
