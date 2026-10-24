// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.video

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.tennis.scoremanager.TsmApp
import com.tennis.scoremanager.ui.Strings
import com.tennis.scoremanager.ui.TsmColors
import com.tennis.scoremanager.ui.TsmTheme
import com.tennis.scoremanager.ui.stringsFor
import kotlinx.coroutines.delay
import java.io.File

/**
 * Lettore del video di una partita con i capitoli: a destra l'elenco dei punti (tocchi e il video va lì) e i
 * pulsanti per saltare di punto, di game e di set, rivedere il punto e copiare i capitoli per YouTube.
 */
class VideoPlayerActivity : ComponentActivity() {

    private val controller get() = (application as TsmApp).controller
    private val s: Strings get() = stringsFor(controller.options.value.lang)
    private var player: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.setBackgroundColor(Color.BLACK)
        // A schermo intero: in orizzontale ogni riga conta per l'elenco dei punti.
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        val entry = intent.getStringExtra(EXTRA_CHAPTERS)?.let { VideoLibrary.load(this, File(it)) }
        if (entry == null) {
            finish()
            return
        }
        val p = ExoPlayer.Builder(this).build().apply {
            setMediaItem(MediaItem.fromUri(entry.video))
            prepare()
            playWhenReady = true
        }
        player = p
        setContent { TsmTheme { Content(entry, p) } }
    }

    override fun onStop() {
        player?.pause()
        super.onStop()
    }

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }

    @Composable
    private fun Content(e: VideoLibrary.Entry, p: ExoPlayer) {
        val str = s
        val points = e.chapters.points
        var pos by remember { mutableLongStateOf(0L) }
        LaunchedEffect(p) {
            while (true) {
                pos = p.currentPosition
                delay(250)
            }
        }
        val cur = Chapters.indexAt(points, pos)
        val list = rememberLazyListState()
        LaunchedEffect(cur) { if (cur >= 0) list.animateScrollToItem((cur - 2).coerceAtLeast(0)) }
        fun go(i: Int?) {
            if (i == null || i < 0 || points.isEmpty()) return
            p.seekTo(Chapters.start(points, i.coerceIn(0, points.lastIndex)))
            p.play()
        }
        // "All'inizio del punto": nei primi 2 secondi indietro va al punto prima, dopo riparte da questo.
        val atStart = cur >= 0 && pos - Chapters.start(points, cur) < 2_000
        Row(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)) {
            AndroidView(
                factory = { ctx -> PlayerView(ctx).apply { player = p } },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            Column(
                Modifier.width(300.dp).fillMaxHeight().background(TsmColors.Background).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NavBtn(Icons.AutoMirrored.Filled.ArrowBack, str.back, Modifier.weight(1f)) { finish() }
                    NavBtn(Icons.Filled.Subtitles, str.videoYoutubeChapters, Modifier.weight(1f)) { shareYoutube(e) }
                }
                if (points.isEmpty()) {
                    Text(str.videoNoPoints, color = TsmColors.TextDim, fontSize = 13.sp)
                    return@Column
                }
                // Toccando "Punto" si rivede il punto in corso dall'inizio.
                NavRow(str.videoPoint, { go(if (atStart) cur - 1 else cur) }, { go(cur + 1) }, onLabel = { go(cur) })
                NavRow(str.videoGame, { go(Chapters.prevGame(points, cur, atStart)) }, { go(Chapters.nextGame(points, cur)) })
                NavRow(str.videoSet, { go(Chapters.prevSet(points, cur, atStart)) }, { go(Chapters.nextSet(points, cur)) })
                LazyColumn(state = list, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    itemsIndexed(points) { i, pt ->
                        val newGame = i == 0 || points[i - 1].game != pt.game
                        if (newGame) {
                            Text(
                                "${str.videoSet} ${pt.set + 1} · ${str.videoGame} ${pt.game + 1}",
                                color = TsmColors.TextDim, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                        PointRow(i, pt, e.chapters.names, i == cur, Chapters.start(points, i)) { go(i) }
                    }
                }
            }
        }
    }

    @Composable
    private fun PointRow(i: Int, pt: PointMark, names: List<String>, current: Boolean, startMs: Long, onClick: () -> Unit) {
        val str = s
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                .background(if (current) TsmColors.SurfaceHigh else TsmColors.Surface)
                .clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val side = if (pt.winner == 1) com.tennis.scoremanager.model.Side.P2 else com.tennis.scoremanager.model.Side.P1
            Box(Modifier.size(10.dp).clip(CircleShape).background(if (pt.winner in 0..1) TsmColors.player(side) else TsmColors.TextDim))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(pt.score, color = TsmColors.TextMain, fontSize = 13.sp, fontWeight = if (current) FontWeight.Bold else FontWeight.Normal)
                val who = names.getOrNull(pt.winner)
                val kind = pt.kind?.let { str.videoKind(it) }
                if (who != null || kind != null) {
                    Text(listOfNotNull(who, kind).joinToString(" · "), color = TsmColors.TextDim, fontSize = 11.sp)
                }
            }
            Text("${i + 1} · ${Chapters.time(startMs)}", color = TsmColors.TextDim, fontSize = 11.sp)
        }
    }

    @Composable
    private fun NavRow(label: String, prev: () -> Unit, next: () -> Unit, onLabel: (() -> Unit)? = null) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NavBtn(Icons.Filled.SkipPrevious, null, Modifier.weight(1f), prev)
            if (onLabel != null) {
                NavBtn(Icons.Filled.Replay, label, Modifier.width(104.dp), onLabel)
            } else {
                Text(label, color = TsmColors.TextMain, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(104.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
            NavBtn(Icons.Filled.SkipNext, null, Modifier.weight(1f), next)
        }
    }

    @Composable
    private fun NavBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String?, modifier: Modifier, onClick: () -> Unit) {
        Surface(onClick = onClick, shape = RoundedCornerShape(10.dp), color = TsmColors.SurfaceHigh, modifier = modifier) {
            Row(Modifier.padding(horizontal = 8.dp, vertical = 5.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = TsmColors.TextMain)
                label?.let {
                    Spacer(Modifier.width(6.dp))
                    Text(it, color = TsmColors.TextMain, fontSize = 12.sp, maxLines = 1)
                }
            }
        }
    }

    /** Capitoli per game da incollare nella descrizione del video su YouTube. */
    private fun shareYoutube(e: VideoLibrary.Entry) {
        val str = s
        val text = Chapters.youtube(e.chapters.points, str.videoStart) { set, game -> "${str.videoSet} $set · ${str.videoGame} $game" }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(send, str.videoYoutubeChapters))
    }

    companion object {
        /** Percorso del file dei capitoli (.json) del video da aprire. */
        const val EXTRA_CHAPTERS = "chapters"
    }
}
