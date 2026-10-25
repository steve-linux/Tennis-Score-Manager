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
import com.tennis.scoremanager.ui.TsmColors
import com.tennis.scoremanager.ui.TsmTheme
import com.tennis.scoremanager.ui.stringsFor
import java.io.File

/**
 * Moviola a schermo intero sul telefono dell'arbitro: gira in orizzontale e in verticale, in loop, a 1×, ½× e ¼×,
 * un fotogramma alla volta. Due dita ingrandiscono (fino a 6×) e spostano l'immagine, un doppio tocco torna normale.
 * Il video si disegna su una TextureView: a differenza della SurfaceView si può ingrandire e spostare.
 */
class ReplayActivity : ComponentActivity() {

    private val controller get() = (application as TsmApp).controller
    private var player: ExoPlayer? = null
    private var files: List<File> = emptyList()
    /** Moviola mandata anche al tabellone: la si toglie chiudendo. */
    private var onTv by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.setBackgroundColor(Color.BLACK)
        files = intent.getStringArrayExtra(EXTRA_FILES)?.map(::File)?.filter { it.exists() }.orEmpty()
        if (files.isEmpty()) {
            finish()
            return
        }
        val p = ExoPlayer.Builder(this).build().apply {
            setMediaItems(files.map { MediaItem.fromUri(Uri.fromFile(it)) })
            repeatMode = Player.REPEAT_MODE_ALL
            prepare()
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

    @Composable
    private fun Content(p: ExoPlayer) {
        val s = stringsFor(controller.options.value.lang)
        var ratio by remember { mutableFloatStateOf(16f / 9f) }
        var speed by remember { mutableFloatStateOf(1f) }
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
                AndroidView(
                    factory = { ctx -> TextureView(ctx).also { p.setVideoTextureView(it) } },
                    // La più grande che sta nello schermo con le proporzioni del video, in verticale come in orizzontale.
                    modifier = Modifier.aspectRatio(ratio)
                        .graphicsLayer {
                            scaleX = zoom
                            scaleY = zoom
                            translationX = pan.x
                            translationY = pan.y
                        },
                )
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
        private const val MAX_ZOOM = 6f
    }
}
