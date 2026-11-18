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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
 * Confronto di due colpi analizzati dello stesso tipo: i due video con lo scheletro, uno sopra l'altro (affiancati in
 * orizzontale), allineati sull'impatto e in loop sullo stesso tratto, con velocità, fotogramma per fotogramma e i
 * momenti chiave in comune. "Sovrapponi" disegna lo scheletro del secondo sopra il primo, con le anche nello stesso
 * punto e il busto della stessa lunghezza: si vede subito dove il movimento cambia.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class CompareActivity : ComponentActivity() {
    private val controller get() = (application as TsmApp).controller

    /** Un colpo: video a pezzi, analisi, ore d'inizio dei pezzi, istante del colpo e l'etichetta in alto. */
    private class Side(val files: List<File>, val pose: ServePoseResult, val starts: List<Long>, val at: Long, val label: String) {
        val contactT: Long = pose.metrics?.contactT ?: pose.ground?.contactT ?: 0L
        /** I tre momenti chiave, in ms dall'impatto. */
        val moments: List<Long> = (pose.metrics?.let { listOf(it.trophyT, it.kneeT, it.contactT) } ?: pose.ground?.let { listOf(it.prepT, it.contactT, it.finishT) }).orEmpty()
            .map { it - contactT }
        lateinit var player: ExoPlayer

        /** Dove si è adesso, in ms dall'impatto. */
        fun rel(): Long = (starts.getOrNull(player.currentMediaItemIndex) ?: 0L) + player.currentPosition - at - contactT

        fun seekRel(rel: Long) {
            val abs = at + contactT + rel
            val i = starts.indexOfLast { it <= abs }.coerceAtLeast(0)
            player.seekTo(i, (abs - (starts.getOrNull(i) ?: abs)).coerceAtLeast(0))
        }

        fun frame(rel: Long): PoseFrame? = ServePose.frameAt(pose.frames, rel + contactT)
    }

    private val sides = mutableListOf<Side>()
    /** Il tratto in loop, in ms dall'impatto: da poco prima del primo momento chiave a poco dopo l'ultimo. */
    private var from = -1_500L
    private var to = 700L
    /** I lettori ci sono (non se mancava uno dei due colpi). */
    private var started = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.setBackgroundColor(Color.BLACK)
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        for (k in listOf("a", "b")) {
            val dir = File(intent.getStringExtra("$EXTRA_DIR$k") ?: continue)
            val files = dir.listFiles { f -> f.extension == "mp4" }.orEmpty().sortedBy { it.name }
            val starts = runCatching { File(dir, ReplayRecorder.STARTS).readLines().mapNotNull { it.trim().toLongOrNull() } }.getOrDefault(emptyList())
            val pose = runCatching { json.decodeFromString(ServePoseResult.serializer(), File(dir, ServeClipAnalysis.RESULT).readText()) }.getOrNull()
            if (files.isEmpty() || pose == null || starts.size != files.size) continue
            sides += Side(files, pose, starts, intent.getLongExtra("$EXTRA_AT$k", 0L), intent.getStringExtra("$EXTRA_LABEL$k").orEmpty())
        }
        if (sides.size != 2) {
            finish()
            return
        }
        from = sides.minOf { it.moments.minOrNull() ?: 0L } - 500
        to = sides.maxOf { it.moments.maxOrNull() ?: 0L } + 600
        for (sd in sides) {
            sd.player = ExoPlayer.Builder(this).build().apply {
                setMediaItems(sd.files.map { MediaItem.fromUri(Uri.fromFile(it)) })
                setSeekParameters(androidx.media3.exoplayer.SeekParameters.EXACT)
                volume = 0f
                playbackParameters = PlaybackParameters(0.5f)
                prepare()
            }
            sd.seekRel(from)
        }
        started = true
        sides.forEach { it.player.play() }
        setContent { TsmTheme { Content() } }
    }

    override fun onDestroy() {
        if (started) sides.forEach { it.player.release() }
        super.onDestroy()
    }

    /** Tutti e due allo stesso punto rispetto all'impatto. */
    private fun seekAll(rel: Long) = sides.forEach { it.seekRel(rel) }

    @Composable
    private fun Content() {
        val s = stringsFor(controller.options.collectAsState().value.lang)
        var speed by remember { mutableFloatStateOf(0.5f) }
        var playing by remember { mutableStateOf(true) }
        var skeleton by remember { mutableStateOf(true) }
        var overlay by remember { mutableStateOf(false) }
        // Istante mostrato (ms dall'impatto) per ciascun colpo: segue il video a ogni fotogramma.
        var rels by remember { mutableStateOf(listOf(from, from)) }
        LaunchedEffect(Unit) {
            while (true) {
                withFrameNanos { }
                val a = sides[0].rel()
                if (playing) {
                    // Finito il tratto si riparte insieme; se il secondo resta indietro (o avanti) lo si riallinea.
                    if (a > to || a < from - 300) seekAll(from)
                    else if (kotlin.math.abs(sides[1].rel() - a) > 120) sides[1].seekRel(a)
                }
                rels = sides.map { it.rel() }
            }
        }
        fun pause() { sides.forEach { it.player.pause() }; playing = false }
        fun step(ms: Long) { pause(); sides.forEach { it.seekRel(it.rel() + ms) } }
        Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)) {
            BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding().padding(bottom = 132.dp).navigationBarsPadding()) {
                val wide = maxWidth > maxHeight
                @Composable fun cell(i: Int, modifier: Modifier) {
                    val sd = sides[i]
                    run {
                        val other = sides[1 - i]
                        Box(modifier.padding(4.dp), contentAlignment = Alignment.Center) {
                            VideoBox(sd) {
                                if (skeleton) {
                                    val f = sd.frame(rels[i])
                                    // Sovrapposto: nel primo anche lo scheletro del secondo, allineato su anche e busto.
                                    val g = if (overlay && i == 0 && f != null) other.frame(rels[1])?.let { ServePose.align(f, it) } else null
                                    Canvas(Modifier.fillMaxSize()) {
                                        g?.let { drawSkeleton(it, TsmColors.Cyan, TsmColors.Cyan) }
                                        f?.let { drawSkeleton(it, if (i == 0) TsmColors.Ball else TsmColors.Cyan) }
                                    }
                                }
                            }
                            Text(
                                sd.label, color = if (i == 0) TsmColors.Ball else TsmColors.Cyan, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                // Il primo lascia posto alla X per chiudere.
                                modifier = Modifier.align(Alignment.TopStart).padding(end = if (i == 0) 60.dp else 0.dp).background(androidx.compose.ui.graphics.Color(0xAA0B1220), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
                if (wide) Row(Modifier.fillMaxSize()) { cell(0, Modifier.weight(1f).fillMaxSize()); cell(1, Modifier.weight(1f).fillMaxSize()) }
                else Column(Modifier.fillMaxSize()) { cell(0, Modifier.weight(1f).fillMaxSize()); cell(1, Modifier.weight(1f).fillMaxSize()) }
            }
            Btn(Icons.Filled.Close, null, false, Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp)) { finish() }
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().background(androidx.compose.ui.graphics.Color(0xAA0B1220)).padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // Su uno schermo stretto i momenti chiave non ci stanno tutti: la riga scorre.
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
                ) {
                    Btn(Icons.Filled.AccessibilityNew, null, skeleton) { skeleton = !skeleton }
                    Btn(Icons.Filled.Layers, s.compareOverlay, overlay) { overlay = !overlay; if (overlay) skeleton = true }
                    // I momenti chiave: ciascun video al suo (fermi, per guardarli bene).
                    val names = if (sides[0].pose.metrics != null) listOf(s.poseTrophy, s.poseKnee, s.poseContact) else listOf(s.posePrep, s.poseContact, s.poseFinish)
                    names.forEachIndexed { k, label ->
                        Btn(null, label, false) { pause(); sides.forEach { sd -> sd.moments.getOrNull(k)?.let { sd.seekRel(it) } } }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                    for (v in listOf(1f, 0.5f, 0.25f)) {
                        Btn(null, if (v == 1f) "1×" else if (v == 0.5f) "½×" else "¼×", speed == v) {
                            speed = v
                            sides.forEach { it.player.playbackParameters = PlaybackParameters(v) }
                        }
                    }
                    Btn(Icons.Filled.SkipPrevious, null, false) { step(-33) }
                    Btn(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, false) {
                        if (playing) pause() else {
                            // Si riparte insieme, dal punto del primo.
                            sides[1].seekRel(sides[0].rel())
                            sides.forEach { it.player.play() }
                            playing = true
                        }
                    }
                    Btn(Icons.Filled.SkipNext, null, false) { step(33) }
                }
            }
        }
    }

    /** Il video di un colpo con le sue proporzioni, il più grande possibile; [over] ci disegna sopra. */
    @Composable
    private fun VideoBox(sd: Side, over: @Composable () -> Unit) {
        var ratio by remember { mutableFloatStateOf(16f / 9f) }
        // Le proporzioni arrivano col primo fotogramma; l'ascolto si toglie quando il riquadro sparisce.
        DisposableEffect(sd) {
            val l = object : Player.Listener {
                override fun onVideoSizeChanged(v: VideoSize) {
                    if (v.width > 0 && v.height > 0) ratio = v.width * v.pixelWidthHeightRatio / v.height
                }
            }
            sd.player.addListener(l)
            onDispose { sd.player.removeListener(l) }
        }
        Box(Modifier.aspectRatio(ratio)) {
            AndroidView(factory = { ctx -> TextureView(ctx).also { sd.player.setVideoTextureView(it) } }, modifier = Modifier.fillMaxSize())
            over()
        }
    }

    @Composable
    private fun Btn(icon: ImageVector?, label: String?, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
        Surface(onClick = onClick, shape = RoundedCornerShape(10.dp), color = if (on) TsmColors.Orange else TsmColors.SurfaceHigh, modifier = modifier) {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                icon?.let { Icon(it, null, tint = if (on) TsmColors.OnOrange else TsmColors.TextMain) }
                label?.let {
                    Text(if (icon != null) " $it" else it, color = if (on) TsmColors.OnOrange else TsmColors.TextMain, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
                }
            }
        }
    }

    companion object {
        /** Per i due colpi ("a" e "b" in fondo): cartella della clip, istante del colpo, etichetta. */
        const val EXTRA_DIR = "dir_"
        const val EXTRA_AT = "at_"
        const val EXTRA_LABEL = "label_"
    }
}
