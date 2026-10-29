// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.video

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.TextureView
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import com.tennis.scoremanager.TsmApp
import com.tennis.scoremanager.data.PoseFrame
import com.tennis.scoremanager.data.ServePose
import com.tennis.scoremanager.data.ServePoseResult
import com.tennis.scoremanager.ui.TsmColors
import com.tennis.scoremanager.ui.TsmTheme
import com.tennis.scoremanager.ui.stringsFor
import java.io.File

/**
 * Moviola a schermo intero sul telefono dell'arbitro: gira in orizzontale e in verticale, in loop, a 1×, ½× e ¼×,
 * un fotogramma alla volta. Due dita ingrandiscono (fino a 6×) e spostano l'immagine, un doppio tocco torna normale.
 * Il video si disegna su una TextureView: a differenza della SurfaceView si può ingrandire e spostare.
 * Con [EXTRA_POSE_DIR] (clip di un servizio analizzato) disegna sopra lo scheletro del giocatore, che segue il video
 * anche ingrandito, e ha i tasti per saltare ai tre momenti chiave.
 */
class ReplayActivity : ComponentActivity() {

    private val controller get() = (application as TsmApp).controller
    private var player: ExoPlayer? = null
    private var files: List<File> = emptyList()
    /** Moviola mandata anche al tabellone: la si toglie chiudendo. */
    private var onTv by mutableStateOf(false)
    /** Analisi del servizio: fotogrammi coi punti del corpo, ore d'inizio dei pezzi e istante del servizio. */
    private var pose: ServePoseResult? = null
    private var poseStarts: List<Long> = emptyList()
    private var poseAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.setBackgroundColor(Color.BLACK)
        files = intent.getStringArrayExtra(EXTRA_FILES)?.map(::File)?.filter { it.exists() }.orEmpty()
        if (files.isEmpty()) {
            finish()
            return
        }
        intent.getStringExtra(EXTRA_POSE_DIR)?.let { path ->
            val dir = File(path)
            poseAt = intent.getLongExtra(EXTRA_POSE_AT, 0L)
            poseStarts = runCatching { File(dir, ReplayRecorder.STARTS).readLines().mapNotNull { it.trim().toLongOrNull() } }.getOrDefault(emptyList())
            pose = runCatching {
                kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                    .decodeFromString(ServePoseResult.serializer(), File(dir, ServeClipAnalysis.RESULT).readText())
            }.getOrNull()?.takeIf { poseStarts.size == files.size }
        }
        val p = ExoPlayer.Builder(this).build().apply {
            setMediaItems(files.map { MediaItem.fromUri(Uri.fromFile(it)) })
            repeatMode = Player.REPEAT_MODE_ALL
            // Fermo il più vicino possibile all'istante chiesto: per l'avanti e indietro di un fotogramma.
            setSeekParameters(androidx.media3.exoplayer.SeekParameters.EXACT)
            prepare()
            // Con lo scheletro si parte poco prima del caricamento, al rallentatore.
            (pose?.metrics?.trophyT ?: pose?.ground?.prepT)?.let { first ->
                seekAbs(this, poseAt + first - 1_200)
                playbackParameters = PlaybackParameters(0.5f)
            }
            play()
        }
        player = p
        // Scelto nella finestra della moviola: subito anche sul tabellone.
        if (savedInstanceState == null && intent.getBooleanExtra(EXTRA_BOARD, false) && controller.canShowReplayOnTv) {
            controller.showReplayOnTv(files, 1f)
            onTv = true
        }
        setContent { TsmTheme { Content(p) } }
    }

    override fun onDestroy() {
        player?.release()
        player = null
        if (onTv) controller.hideReplayOnTv()
        // Chiusa la moviola, il telefono dell'arbitro torna alla partita (e può chiederne un'altra).
        if (isFinishing) controller.relay.closeReplay()
        super.onDestroy()
    }

    /** Va all'istante [abs] (ora del telefono) nel pezzo che lo contiene. */
    private fun seekAbs(p: ExoPlayer, abs: Long) {
        val i = poseStarts.indexOfLast { it <= abs }.coerceAtLeast(0)
        p.seekTo(i, (abs - (poseStarts.getOrNull(i) ?: abs)).coerceAtLeast(0))
    }

    /** I punti del corpo all'istante [rel] (ms dal servizio), tra i due fotogrammi analizzati più vicini. */
    private fun poseAt(rel: Long): PoseFrame? {
        val frames = pose?.frames ?: return null
        if (frames.isEmpty() || rel < frames.first().t - 70 || rel > frames.last().t + 70) return null
        val j = frames.indexOfFirst { it.t >= rel }
        if (j <= 0) return frames[if (j == 0) 0 else frames.lastIndex]
        val a = frames[j - 1]
        val b = frames[j]
        val k = ((rel - a.t).toFloat() / (b.t - a.t).coerceAtLeast(1)).coerceIn(0f, 1f)
        return PoseFrame(rel, List(17) { a.x[it] + (b.x[it] - a.x[it]) * k }, List(17) { a.y[it] + (b.y[it] - a.y[it]) * k }, List(17) { minOf(a.s[it], b.s[it]) })
    }

    /** Lo scheletro sopra il video: x e y dei punti sono in frazioni dell'altezza dell'immagine. */
    @Composable
    private fun SkeletonOverlay(p: ExoPlayer) {
        var frame by remember { mutableStateOf<PoseFrame?>(null) }
        LaunchedEffect(p) {
            while (true) {
                androidx.compose.runtime.withFrameNanos { }
                val abs = (poseStarts.getOrNull(p.currentMediaItemIndex) ?: continue) + p.currentPosition
                frame = poseAt(abs - poseAt)
            }
        }
        val f = frame ?: return
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val h = size.height
            val bone = TsmColors.Ball
            for ((a, b) in ServePose.BONES) {
                if (f.s[a] < ServePose.MIN_SCORE || f.s[b] < ServePose.MIN_SCORE) continue
                drawLine(bone, Offset(f.x[a] * h, f.y[a] * h), Offset(f.x[b] * h, f.y[b] * h), strokeWidth = h / 110, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            }
            for (i in 0 until 17) if (f.s[i] >= ServePose.MIN_SCORE) drawCircle(androidx.compose.ui.graphics.Color.White, h / 120, Offset(f.x[i] * h, f.y[i] * h))
        }
    }

    @Composable
    private fun Content(p: ExoPlayer) {
        val s = stringsFor(controller.options.value.lang)
        var ratio by remember { mutableFloatStateOf(16f / 9f) }
        var speed by remember { mutableFloatStateOf(if (pose?.metrics != null || pose?.ground != null) 0.5f else 1f) }
        var skeleton by remember { mutableStateOf(pose != null) }
        var playing by remember { mutableStateOf(true) }
        var zoom by remember { mutableFloatStateOf(1f) }
        var pan by remember { mutableStateOf(Offset.Zero) }
        remember(p) {
            p.addListener(object : Player.Listener {
                override fun onVideoSizeChanged(v: VideoSize) {
                    if (v.width > 0 && v.height > 0) ratio = v.width * v.pixelWidthHeightRatio / v.height
                }
            })
        }
        Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)) {
            // L'immagine: proporzioni del video, al centro; due dita per ingrandire e spostare.
            Box(
                Modifier.fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, move, scale, _ ->
                            zoom = (zoom * scale).coerceIn(1f, MAX_ZOOM)
                            // Spostamento limitato a quanto l'immagine ingrandita sborda dallo schermo.
                            val maxX = size.width * (zoom - 1) / 2
                            val maxY = size.height * (zoom - 1) / 2
                            pan = Offset((pan.x + move.x).coerceIn(-maxX, maxX), (pan.y + move.y).coerceIn(-maxY, maxY))
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = {
                            zoom = 1f
                            pan = Offset.Zero
                        })
                    },
                contentAlignment = Alignment.Center,
            ) {
                // La più grande che sta nello schermo con le proporzioni del video, in verticale come in orizzontale;
                // lo scheletro sta nello stesso riquadro, così si ingrandisce e si sposta insieme al video.
                Box(
                    Modifier.aspectRatio(ratio)
                        .graphicsLayer {
                            scaleX = zoom
                            scaleY = zoom
                            translationX = pan.x
                            translationY = pan.y
                        },
                ) {
                    AndroidView(factory = { ctx -> TextureView(ctx).also { p.setVideoTextureView(it) } }, modifier = Modifier.fillMaxSize())
                    if (skeleton) SkeletonOverlay(p)
                }
            }
            Btn(Icons.Filled.Close, s.replayClose, false, Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp)) { finish() }
            // Sul tabellone (TV o telefono-tabellone), alla stessa velocità; di nuovo per toglierla.
            if (controller.canShowReplayOnTv) {
                Btn(Icons.Filled.Tv, s.replayOnBoard, onTv, Modifier.align(Alignment.TopStart).statusBarsPadding().padding(12.dp)) {
                    if (onTv) {
                        controller.hideReplayOnTv()
                    } else {
                        controller.showReplayOnTv(files, speed)
                        syncBoard(p, !playing)
                    }
                    onTv = !onTv
                }
            }
            // Analisi del servizio: scheletro sì o no e salto ai tre momenti chiave (fermo, per guardarli bene).
            pose?.let { r ->
                Row(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(bottom = 64.dp)
                        .background(androidx.compose.ui.graphics.Color(0xAA0B1220)).padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
                ) {
                    Btn(Icons.Filled.AccessibilityNew, null, skeleton) { skeleton = !skeleton }
                    val moments = r.metrics?.let { m -> listOf(s.poseTrophy to m.trophyT, s.poseKnee to m.kneeT, s.poseContact to m.contactT) }
                        ?: r.ground?.let { g -> listOf(s.posePrep to g.prepT, s.poseContact to g.contactT, s.poseFinish to g.finishT) }
                    moments?.let { list ->
                        for ((label, t) in list) {
                            Btn(null, label, false) {
                                p.pause()
                                playing = false
                                seekAbs(p, poseAt + t)
                            }
                        }
                    }
                }
            }
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding()
                    .background(androidx.compose.ui.graphics.Color(0xAA0B1220)).padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
            ) {
                for (v in listOf(1f, 0.5f, 0.25f)) {
                    Btn(null, if (v == 1f) "1×" else if (v == 0.5f) "½×" else "¼×", speed == v) {
                        speed = v
                        p.playbackParameters = PlaybackParameters(v)
                        if (onTv) controller.setTvReplaySpeed(v)
                    }
                }
                // Un fotogramma a 30 fps sono ~33 ms: il video si ferma e si avanza a scatti.
                Btn(Icons.Filled.SkipPrevious, null, false) {
                    p.pause()
                    playing = false
                    p.seekTo(maxOf(0, p.currentPosition - 33))
                    syncBoard(p, true)
                }
                Btn(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, false) {
                    if (playing) p.pause() else p.play()
                    playing = !playing
                    syncBoard(p, !playing)
                }
                Btn(Icons.Filled.SkipNext, null, false) {
                    p.pause()
                    playing = false
                    p.seekTo(p.currentPosition + 33)
                    syncBoard(p, true)
                }
            }
        }
    }

    /** Il tabellone fa quello che fa la moviola dell'arbitro: stesso pezzo, stesso istante, fermo o in movimento. */
    private fun syncBoard(p: ExoPlayer, paused: Boolean) {
        if (onTv) controller.syncTvReplay(paused, p.currentMediaItemIndex, p.currentPosition)
    }

    @Composable
    private fun Btn(icon: ImageVector?, label: String?, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
        Surface(onClick = onClick, shape = RoundedCornerShape(10.dp), color = if (on) TsmColors.Orange else TsmColors.SurfaceHigh, modifier = modifier) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                icon?.let { Icon(it, null, tint = if (on) TsmColors.OnOrange else TsmColors.TextMain) }
                label?.let {
                    Text(
                        if (icon != null) " $it" else it, color = if (on) TsmColors.OnOrange else TsmColors.TextMain,
                        fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1,
                    )
                }
            }
        }
    }

    companion object {
        /** Percorsi dei pezzi della moviola, da vedere in fila. */
        const val EXTRA_FILES = "files"
        /** true = mandarla subito anche al tabellone. */
        const val EXTRA_BOARD = "board"
        /** Cartella della clip di un servizio analizzato (pose.json e starts.txt) e istante del servizio (ms). */
        const val EXTRA_POSE_DIR = "pose_dir"
        const val EXTRA_POSE_AT = "pose_at"
        private const val MAX_ZOOM = 6f
    }
}
