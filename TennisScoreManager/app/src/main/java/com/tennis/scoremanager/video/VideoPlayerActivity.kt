// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.video

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.tennis.scoremanager.TsmApp
import com.tennis.scoremanager.data.Names
import com.tennis.scoremanager.model.PointKind
import com.tennis.scoremanager.model.Side
import com.tennis.scoremanager.ui.SegOption
import com.tennis.scoremanager.ui.Segmented
import com.tennis.scoremanager.ui.Strings
import com.tennis.scoremanager.ui.TsmColors
import com.tennis.scoremanager.ui.TsmTheme
import com.tennis.scoremanager.ui.stringsFor
import kotlinx.coroutines.delay
import java.io.File

/**
 * Lettore del video di una partita con i capitoli: a destra l'elenco dei punti (tocchi e il video va lì) e i
 * pulsanti per saltare di punto, di game e di set, rivedere il punto e copiare i capitoli per YouTube.
 * Nei video importati i punti si segnano qui, guardando: a fine punto si tocca chi l'ha vinto (e, se si vuole, com'è
 * finito), e il punteggio si calcola con le regole della partita; la grafica del punteggio si disegna sopra il video.
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
            // Un video importato senza punti si apre fermo: prima si sceglie chi gioca.
            playWhenReady = !(entry.chapters.imported && entry.chapters.points.isEmpty())
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
    private fun Content(start: VideoLibrary.Entry, p: ExoPlayer) {
        val str = s
        var entry by remember { mutableStateOf(start) }
        val ch = entry.chapters
        val points = ch.points
        var pos by remember { mutableLongStateOf(0L) }
        LaunchedEffect(p) {
            while (true) {
                pos = p.currentPosition
                delay(200)
            }
        }
        var tagging by remember { mutableStateOf(ch.imported && points.isEmpty()) }
        var setup by remember { mutableStateOf(ch.imported && ch.rules == null) }
        val cur = Chapters.indexAt(points, pos)
        val list = rememberLazyListState()
        LaunchedEffect(cur) { if (cur >= 0) list.animateScrollToItem((cur - 2).coerceAtLeast(0)) }
        fun go(i: Int?) {
            if (i == null || i < 0 || points.isEmpty()) return
            p.seekTo(Chapters.start(points, i.coerceIn(0, points.lastIndex)))
            p.play()
        }
        fun update(new: VideoChapters) {
            entry = VideoLibrary.save(entry, new)
        }
        // "All'inizio del punto": nei primi 2 secondi indietro va al punto prima, dopo riparte da questo.
        val atStart = cur >= 0 && pos - Chapters.start(points, cur) < 2_000
        Row(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)) {
            Box(Modifier.weight(1f).fillMaxHeight()) {
                AndroidView(factory = { ctx -> PlayerView(ctx).apply { player = p } }, modifier = Modifier.fillMaxSize())
                // Video importati: la grafica del punteggio sopra il video, dal punteggio ricalcolato.
                ch.rules?.let { rules ->
                    val done = points.count { it.at <= pos }
                    val bmp = remember(ch, done) {
                        val state = PointMarks.stateAfter(rules, points, done)
                        OverlayRenderer.render(ScoreOverlay.from(state, ch.names, null, ""))
                    }
                    Image(
                        bmp.asImageBitmap(), null,
                        modifier = Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 56.dp).width((bmp.width / 4).dp),
                    )
                }
            }
            Column(
                Modifier.width(300.dp).fillMaxHeight().background(TsmColors.Background).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NavBtn(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.weight(0.6f)) { finish() }
                    NavBtn(Icons.Filled.Subtitles, str.videoYoutubeChapters, Modifier.weight(1.4f)) { shareYoutube(ch) }
                    if (ch.imported || ch.rules != null) {
                        NavBtn(if (tagging) Icons.Filled.Check else Icons.Filled.EditNote, null, Modifier.weight(0.6f)) {
                            if (ch.rules == null) setup = true else tagging = !tagging
                        }
                    }
                }
                if (tagging && ch.rules != null) {
                    TagPanel(ch, pos, p, onChange = ::update)
                } else if (points.isEmpty()) {
                    Text(if (ch.imported) str.videoTagHowTo else str.videoNoPoints, color = TsmColors.TextDim, fontSize = 13.sp)
                } else {
                    // Toccando "Punto" si rivede il punto in corso dall'inizio.
                    NavRow(str.videoPoint, { go(if (atStart) cur - 1 else cur) }, { go(cur + 1) }, onLabel = { go(cur) })
                    NavRow(str.videoGame, { go(Chapters.prevGame(points, cur, atStart)) }, { go(Chapters.nextGame(points, cur)) })
                    NavRow(str.videoSet, { go(Chapters.prevSet(points, cur, atStart)) }, { go(Chapters.nextSet(points, cur)) })
                }
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
                        PointRow(i, pt, ch.names, i == cur, Chapters.start(points, i)) { go(i) }
                    }
                }
            }
        }
        if (setup) {
            TagSetup(onDone = { names, server ->
                update(ch.copy(names = names, rules = controller.rulesFromOptions(server)))
                setup = false
                tagging = true
            }, onCancel = { setup = false })
        }
    }

    /**
     * Segnare i punti: a fine punto si tocca chi l'ha vinto; poi, se si vuole, com'è finito (vale per l'ultimo
     * punto segnato). Il punto finisce dove si trova il video in quel momento.
     */
    @Composable
    private fun TagPanel(ch: VideoChapters, pos: Long, p: ExoPlayer, onChange: (VideoChapters) -> Unit) {
        val str = s
        val rules = ch.rules ?: return
        val points = ch.points
        val state = remember(ch) { PointMarks.stateAfter(rules, points, points.size) }
        // Prima di servizio fuori: il prossimo punto si gioca sulla seconda; un altro fallo è doppio fallo.
        var fault by remember(points.size) { mutableStateOf(false) }
        // "Com'è finito?" compare dopo ogni punto segnato e sparisce appena si risponde (o si annulla).
        var askKind by remember { mutableStateOf(false) }
        fun add(winner: Side, kind: PointKind?) {
            val last = points.lastOrNull()?.at ?: -1L
            if (pos <= last) {
                Toast.makeText(this, str.videoTagTooEarly, Toast.LENGTH_SHORT).show()
                return
            }
            val probe = PointMark(pos, "", winner.ordinal, kind?.name, secondServe = fault)
            val after = PointMarks.stateAfter(rules, points + probe, points.size + 1)
            onChange(ch.copy(points = points + PointMarks.mark(pos, after, winner, kind, fault)))
            askKind = kind == null
        }
        // Il pannello è basso (telefono in orizzontale): pulsanti a due a due, istruzioni solo prima del primo punto.
        if (points.isEmpty()) Text(str.videoTagHowTo, color = TsmColors.TextDim, fontSize = 11.sp, lineHeight = 13.sp)
        Text(
            (if (points.isEmpty()) "0-0" else PointMarks.score(state)) + if (fault) " · ${str.secondServe}" else "",
            color = TsmColors.TextMain, fontSize = 15.sp, fontWeight = FontWeight.Bold,
        )
        if (state.isFinished) return
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Surface(
                onClick = { if (fault) add(state.receiver, PointKind.DOUBLE_FAULT) else fault = true },
                shape = RoundedCornerShape(10.dp), color = if (fault) TsmColors.Orange else TsmColors.SurfaceHigh, modifier = Modifier.weight(1f),
            ) {
                Text(
                    if (fault) str.btnDoubleFault else str.btnFault, color = if (fault) TsmColors.OnBall else TsmColors.TextMain,
                    fontWeight = FontWeight.Bold, fontSize = 13.sp, textAlign = TextAlign.Center, maxLines = 1,
                    modifier = Modifier.padding(vertical = 7.dp),
                )
            }
            if (points.isNotEmpty()) {
                NavBtn(Icons.AutoMirrored.Filled.Undo, str.videoUndoLast, Modifier.weight(1f)) {
                    p.seekTo(Chapters.start(points, points.lastIndex))
                    onChange(ch.copy(points = points.dropLast(1)))
                    askKind = false
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (side in Side.entries) {
                Surface(
                    onClick = { add(side, null) },
                    shape = RoundedCornerShape(10.dp), color = TsmColors.player(side), modifier = Modifier.weight(1f),
                ) {
                    Text(
                        str.videoPointFor(ch.names.getOrElse(side.ordinal) { str.playerTag(side.ordinal + 1) }),
                        color = TsmColors.onPlayer(side), fontWeight = FontWeight.Bold, fontSize = 13.sp, textAlign = TextAlign.Center,
                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp),
                    )
                }
            }
        }
        if (points.isNotEmpty() && askKind) {
            // Com'è finito l'ultimo punto (facoltativo): un tocco e il pannello torna pronto per il punto dopo.
            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                for (k in listOf(PointKind.ACE, PointKind.DOUBLE_FAULT, PointKind.WINNER, PointKind.UNFORCED_ERROR, PointKind.FORCED_ERROR)) {
                    val on = points.last().kind == k.name
                    Surface(
                        onClick = {
                            onChange(ch.copy(points = points.dropLast(1) + points.last().copy(kind = if (on) null else k.name)))
                            askKind = false
                        },
                        shape = RoundedCornerShape(16.dp), color = if (on) TsmColors.Ball else TsmColors.SurfaceHigh,
                    ) {
                        Text(str.videoKind(k.name), color = if (on) TsmColors.OnBall else TsmColors.TextMain, fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
            }
        }
    }

    /** Prima di segnare: chi gioca e chi serve per primo (le regole sono quelle di pagina 2). */
    @Composable
    private fun TagSetup(onDone: (List<String>, Side) -> Unit, onCancel: () -> Unit) {
        val str = s
        val names = remember { Names(controller.setup.value, str) }
        var n1 by remember { mutableStateOf(names.short(Side.P1)) }
        var n2 by remember { mutableStateOf(names.short(Side.P2)) }
        var server by remember { mutableIntStateOf(0) }
        AlertDialog(
            onDismissRequest = onCancel,
            title = { Text(str.videoTagSetupTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(n1, { n1 = it }, label = { Text(str.playerTag(1)) }, singleLine = true)
                    OutlinedTextField(n2, { n2 = it }, label = { Text(str.playerTag(2)) }, singleLine = true)
                    Text(str.videoFirstServer, fontSize = 13.sp)
                    Segmented(listOf(SegOption(n1.ifBlank { str.playerTag(1) }), SegOption(n2.ifBlank { str.playerTag(2) })), server, { server = it })
                    Text(str.videoTagSetupHint, fontSize = 12.sp, color = TsmColors.TextDim)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onDone(listOf(n1.trim().ifEmpty { str.playerTag(1) }, n2.trim().ifEmpty { str.playerTag(2) }), Side.entries[server])
                }) { Text(str.ok) }
            },
            dismissButton = { TextButton(onClick = onCancel) { Text(str.back) } },
        )
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
            val side = if (pt.winner == 1) Side.P2 else Side.P1
            Box(Modifier.size(10.dp).clip(CircleShape).background(if (pt.winner in 0..1) TsmColors.player(side) else TsmColors.TextDim))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(pt.score, color = TsmColors.TextMain, fontSize = 13.sp, fontWeight = if (current) FontWeight.Bold else FontWeight.Normal)
                val who = names.getOrNull(pt.winner)
                val kind = pt.kind?.let { str.videoKind(it) }
                val second = str.secondServe.takeIf { pt.secondServe && pt.kind != PointKind.DOUBLE_FAULT.name }
                if (who != null || kind != null || second != null) {
                    Text(listOfNotNull(who, kind, second).joinToString(" · "), color = TsmColors.TextDim, fontSize = 11.sp)
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
                    textAlign = TextAlign.Center)
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
    private fun shareYoutube(ch: VideoChapters) {
        val str = s
        val text = Chapters.youtube(ch.points, str.videoStart) { set, game -> "${str.videoSet} $set · ${str.videoGame} $game" }
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
