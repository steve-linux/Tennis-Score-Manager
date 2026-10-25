// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.video

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.tennis.scoremanager.R
import com.tennis.scoremanager.TsmApp
import com.tennis.scoremanager.data.MatchRecord
import com.tennis.scoremanager.data.Names
import com.tennis.scoremanager.model.PointKind
import com.tennis.scoremanager.model.Side
import com.tennis.scoremanager.ui.SegOption
import com.tennis.scoremanager.ui.Segmented
import com.tennis.scoremanager.ui.Strings
import com.tennis.scoremanager.ui.TsmColors
import com.tennis.scoremanager.ui.TsmTheme
import com.tennis.scoremanager.ui.stringsFor
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Lettore del video di una partita con i capitoli: a destra l'elenco dei punti (tocchi e il video va lì) e i
 * pulsanti per saltare di punto, di game e di set, rivedere il punto e copiare i capitoli per YouTube.
 * Nei video importati i punti si segnano qui, guardando: a fine punto si tocca chi l'ha vinto (e, se si vuole, com'è
 * finito), e il punteggio si calcola con le regole della partita; la grafica del punteggio si disegna sopra il video.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class VideoPlayerActivity : ComponentActivity() {

    private val controller get() = (application as TsmApp).controller
    private val s: Strings get() = stringsFor(controller.options.value.lang)
    private var player: ExoPlayer? = null
    private var maker: HighlightMaker? = null
    /** Montaggio in corso: percentuale (null = nessuno). */
    private var hlProgress by mutableStateOf<Int?>(null)

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
        maker?.cancel()
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
        var hlOpen by remember { mutableStateOf(false) }
        var filter by remember { mutableStateOf(CoachFilter()) }
        var filterOpen by remember { mutableStateOf(false) }
        val shown = remember(ch, filter) { if (filter.active) CoachFilter.matching(points, ch.rules, filter) else points.indices.toList() }
        val cur = Chapters.indexAt(points, pos)
        val list = rememberLazyListState()
        // L'elenco segue il video (con un filtro, la posizione del punto tra quelli mostrati).
        LaunchedEffect(cur, shown) {
            val k = shown.indexOf(cur)
            if (k >= 0) list.animateScrollToItem((k - 2).coerceAtLeast(0))
        }
        // Riproduzione dei soli punti filtrati: a fine pezzo si salta al prossimo, dopo l'ultimo ci si ferma.
        var queue by remember { mutableStateOf<List<Highlights.Clip>?>(null) }
        LaunchedEffect(queue) {
            val q = queue ?: return@LaunchedEffect
            var k = 0
            p.seekTo(q[0].startMs)
            p.play()
            while (k < q.size) {
                delay(150)
                if (p.currentPosition >= q[k].endMs) {
                    k++
                    if (k < q.size) p.seekTo(q[k].startMs) else p.pause()
                }
            }
            queue = null
        }
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
                    if (points.isNotEmpty() && !tagging) NavBtn(Icons.Filled.FilterList, null, Modifier.weight(0.6f)) { filterOpen = true }
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
                    if (filter.active) {
                        // Filtro acceso: quanti punti e, con un giocatore scelto, quanti vinti; poi riproduci o esporta.
                        val won = filter.player?.let { me -> shown.count { points[it].winner == me } }
                        Text(
                            if (shown.isEmpty()) str.coachNone else if (won != null) str.coachSummary(shown.size, won) else str.coachCount(shown.size),
                            color = TsmColors.Ball, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            val clips = shown.mapNotNull { Highlights.clip(points, it, p.duration.coerceAtLeast(0)) }
                            NavBtn(Icons.Filled.PlayArrow, str.coachPlay, Modifier.weight(1f)) { if (clips.isNotEmpty()) queue = clips }
                            NavBtn(Icons.Filled.Movie, null, Modifier.weight(0.5f)) { if (clips.isNotEmpty()) montage(entry, clips) }
                            NavBtn(Icons.Filled.Close, null, Modifier.weight(0.5f)) {
                                filter = CoachFilter()
                                queue = null
                            }
                        }
                    } else {
                        NavBtn(Icons.Filled.AutoAwesome, str.hlTitle, Modifier.fillMaxWidth()) { hlOpen = true }
                    }
                }
                LazyColumn(state = list, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    itemsIndexed(shown) { k, i ->
                        val pt = points[i]
                        val newGame = k == 0 || points[shown[k - 1]].game != pt.game
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
        if (hlOpen) HighlightDialog(entry, p.duration.coerceAtLeast(0)) { hlOpen = false }
        if (filterOpen) FilterDialog(ch, filter, { filter = it; queue = null }) { filterOpen = false }
        hlProgress?.let { HighlightProgress(it) }
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
            onChange(ch.copy(points = points + PointMarks.mark(pos, after, winner, kind, fault, before = state)))
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

    /** Highlights: cosa includere e quanto può durare al massimo; sotto, quanti punti e quanto verrebbe. */
    @Composable
    private fun HighlightDialog(e: VideoLibrary.Entry, videoMs: Long, onClose: () -> Unit) {
        val str = s
        // Ordine e voci accese dell'ultima volta.
        var o by remember {
            val st = controller.storage
            val order = st.highlightOrder.mapNotNull { n -> Highlights.Category.entries.firstOrNull { it.name == n } }
            val full = order + Highlights.Category.entries.filter { it !in order }
            val enabled = st.highlightEnabled?.mapNotNull { n -> Highlights.Category.entries.firstOrNull { it.name == n } }?.toSet()
            mutableStateOf(Highlights.Options(order = full, enabled = enabled ?: Highlights.Category.entries.toSet()))
        }
        // Dai social (1-5 minuti) al video del circolo (10-15).
        val minutes = listOf(1, 3, 5, 10, 15)
        // Durata a piacere in minuti: vale solo se è tra 1 e [MAX_HL_MINUTES], altrimenti resta l'ultima valida.
        var custom by remember { mutableStateOf("") }
        val clips = remember(o, e) { Highlights.select(e.chapters.points, e.chapters.rules, videoMs, o) }
        // Statistiche in coda: dalla partita dello storico che corrisponde al video (o dai punti segnati).
        var withStats by remember { mutableStateOf(controller.storage.highlightStats) }
        val statsRec by produceState<MatchRecord?>(null, e) {
            value = withContext(Dispatchers.IO) {
                val all = controller.storage.loadFinished() + listOfNotNull(controller.live.value?.record)
                HighlightStats.record(all, e.chapters, videoMs, controller.setup.value)
            }
        }
        val statsPages = remember(statsRec) { statsRec?.let { HighlightStats.card(it, str).pages.size } ?: 0 }
        // Musica delle statistiche: un brano dal telefono (copiato nell'app) o quella generata dall'app.
        var musicName by remember { mutableStateOf(controller.storage.highlightMusicName) }
        val pickMusic = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri ?: return@rememberLauncherForActivityResult
            val name = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            } ?: uri.lastPathSegment ?: "?"
            val ok = runCatching {
                contentResolver.openInputStream(uri)!!.use { i -> controller.storage.highlightMusicFile.outputStream().use { i.copyTo(it) } }
            }.isSuccess
            controller.storage.highlightMusicName = if (ok) name else null
            musicName = controller.storage.highlightMusicName
        }
        // Ordine e voci accese si ricordano subito, anche senza creare il video.
        LaunchedEffect(o.order, o.enabled) {
            controller.storage.highlightOrder = o.order.map { it.name }
            controller.storage.highlightEnabled = o.enabled.map { it.name }.toSet()
        }
        AlertDialog(
            onDismissRequest = onClose,
            title = { Text(str.hlTitle) },
            text = {
                // In orizzontale il dialogo è basso: si scorre.
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(str.hlOrderHint, fontSize = 12.sp, color = TsmColors.TextDim)
                    // In alto le voci che contano di più: se non ci sta tutto, restano fuori prima quelle in fondo.
                    o.order.forEachIndexed { i, cat ->
                        val label = when (cat) {
                            Highlights.Category.BIG_POINTS -> str.hlBig
                            Highlights.Category.ACES -> str.videoKind("ACE")
                            Highlights.Category.WINNERS -> str.videoKind("WINNER")
                            Highlights.Category.LONG_RALLIES -> str.hlRallies
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${i + 1}", color = TsmColors.TextDim, fontSize = 13.sp, modifier = Modifier.width(16.dp))
                            androidx.compose.material3.Checkbox(cat in o.enabled, { on ->
                                o = o.copy(enabled = if (on) o.enabled + cat else o.enabled - cat)
                            })
                            Text(label, fontSize = 14.sp, modifier = Modifier.weight(1f))
                            fun move(d: Int) {
                                val l = o.order.toMutableList()
                                val j = i + d
                                if (j !in l.indices) return
                                l[i] = l[j].also { l[j] = l[i] }
                                o = o.copy(order = l)
                            }
                            androidx.compose.material3.IconButton(onClick = { move(-1) }, enabled = i > 0) {
                                Icon(Icons.Filled.KeyboardArrowUp, str.hlUp, tint = if (i > 0) TsmColors.TextMain else TsmColors.TextDim)
                            }
                            androidx.compose.material3.IconButton(onClick = { move(1) }, enabled = i < o.order.lastIndex) {
                                Icon(Icons.Filled.KeyboardArrowDown, str.hlDown, tint = if (i < o.order.lastIndex) TsmColors.TextMain else TsmColors.TextDim)
                            }
                        }
                    }
                    Text(str.hlMax, fontSize = 13.sp)
                    // Con una durata scritta a mano nessun tempo pronto resta acceso (indice -1).
                    Segmented(minutes.map { SegOption("$it′") }, if (custom.isEmpty()) minutes.indexOf((o.maxMs / 60_000).toInt()) else -1, { i ->
                        custom = ""
                        o = o.copy(maxMs = minutes[i] * 60_000L)
                    }, columns = minutes.size)
                    OutlinedTextField(
                        value = custom,
                        onValueChange = { v ->
                            custom = v.filter { it.isDigit() }.take(2)
                            custom.toIntOrNull()?.takeIf { it in 1..MAX_HL_MINUTES }?.let { o = o.copy(maxMs = it * 60_000L) }
                        },
                        label = { Text(str.hlCustom) },
                        singleLine = true,
                        isError = custom.isNotEmpty() && custom.toIntOrNull()?.let { it in 1..MAX_HL_MINUTES } != true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(withStats && statsPages > 0, { on ->
                            withStats = on
                            controller.storage.highlightStats = on
                        }, enabled = statsPages > 0)
                        Text(str.hlStats, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    }
                    if (withStats && statsPages > 0) {
                        Text("${str.hlMusic}: ${musicName ?: STATS_MUSIC_CREDIT}", fontSize = 13.sp, maxLines = 3)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { pickMusic.launch("audio/*") }) { Text(str.hlMusicPick) }
                            if (musicName != null) TextButton(onClick = {
                                controller.storage.highlightMusicName = null
                                musicName = null
                            }) { Text(str.hlMusicReset) }
                        }
                        Text(str.hlMusicHint, fontSize = 11.sp, color = TsmColors.TextDim)
                        // Licenza CC BY: chi pubblica il video deve citare l'autore della musica dell'app.
                        if (musicName == null) Text(str.hlMusicCredit(STATS_MUSIC_CREDIT), fontSize = 11.sp, color = TsmColors.TextDim)
                    }
                    Text(
                        if (statsPages > 0) str.hlStatsHint(statsPages, (statsPages * HighlightStats.PAGE_MS / 1000).toInt()) else str.hlStatsNone,
                        fontSize = 11.sp, color = TsmColors.TextDim,
                    )
                    Text(
                        if (clips.isEmpty()) str.hlNone else str.hlPreview(clips.size, Chapters.time(clips.sumOf { it.durationMs })),
                        fontSize = 13.sp, color = if (clips.isEmpty()) TsmColors.Orange else TsmColors.TextMain,
                    )
                    Text(str.hlHint, fontSize = 11.sp, color = TsmColors.TextDim)
                }
            },
            confirmButton = {
                TextButton(enabled = clips.isNotEmpty(), onClick = {
                    onClose()
                    montage(e, clips, statsRec?.takeIf { withStats && statsPages > 0 })
                }) { Text(str.hlCreate) }
            },
            dismissButton = { TextButton(onClick = onClose) { Text(str.back) } },
        )
    }

    /** Monta [clips] (highlights o punti filtrati) e apre il risultato. */
    private fun montage(e: VideoLibrary.Entry, clips: List<Highlights.Clip>, statsRec: MatchRecord? = null) {
        val str = s
        player?.pause()
        hlProgress = 0
        val m = HighlightMaker(this)
        maker = m
        val stats = statsRec?.let { rec ->
            val card = HighlightStats.card(rec, str)
            // Foto di chi ha il nome scritto; agli altri l'avatar con le iniziali.
            val photos = Side.entries.associateWith { side ->
                val real = HighlightStats.photoNames(rec, side)
                card.players[side]?.names.orEmpty().map { n -> if (n in real) controller.loadPhoto(n) else null }
            }
            val avatars = runCatching {
                StatsCardRenderer.Avatars(
                    BitmapFactory.decodeResource(resources, R.drawable.avatar_man),
                    BitmapFactory.decodeResource(resources, R.drawable.avatar_woman),
                )
            }.getOrNull()
            // Il brano dell'app sta nelle risorse: per leggerlo come file lo si copia una volta nella cache.
            val bundled = File(cacheDir, "stats_music.m4a")
            if (!bundled.exists()) runCatching { resources.openRawResource(R.raw.stats_music).use { i -> bundled.outputStream().use { i.copyTo(it) } } }
            HighlightMaker.Stats(
                card, photos, str.pointsWon,
                controller.storage.highlightMusicFile.takeIf { controller.storage.highlightMusicName != null }, avatars,
                bundled.takeIf { it.exists() },
            )
        }
        m.make(e, clips, { hlProgress = it }, stats = stats) { file ->
            hlProgress = null
            maker = null
            if (file == null) {
                Toast.makeText(this, str.hlFailed, Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, str.hlDone, Toast.LENGTH_LONG).show()
                startActivity(Intent(this, VideoPlayerActivity::class.java).putExtra(EXTRA_CHAPTERS, file.absolutePath))
            }
        }
    }

    /** Filtri da allenatore: chi, vinti o persi, servizio, prima o seconda, com'è finito, situazione. */
    @Composable
    private fun FilterDialog(ch: VideoChapters, current: CoachFilter, onApply: (CoachFilter) -> Unit, onClose: () -> Unit) {
        val str = s
        var f by remember { mutableStateOf(current) }
        val names = List(2) { ch.names.getOrElse(it) { str.playerTag(it + 1) } }
        val count = remember(f, ch) { CoachFilter.matching(ch.points, ch.rules, f).size }
        AlertDialog(
            onDismissRequest = onClose,
            title = { Text(str.coachTitle) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Label(str.coachPlayer)
                    Segmented(listOf(SegOption(str.coachAll), SegOption(names[0]), SegOption(names[1])), (f.player ?: -1) + 1, { i ->
                        f = if (i == 0) f.copy(player = null, won = null, serving = null) else f.copy(player = i - 1)
                    })
                    // Vinti/persi e servizio/risposta si riferiscono al giocatore scelto.
                    if (f.player != null) {
                        Label(str.coachPoint)
                        Segmented(listOf(SegOption(str.coachAll), SegOption(str.coachWon), SegOption(str.coachLost)), when (f.won) { null -> 0; true -> 1; false -> 2 }, { i ->
                            f = f.copy(won = if (i == 0) null else i == 1)
                        })
                        Label(str.coachServe)
                        Segmented(listOf(SegOption(str.coachAll), SegOption(str.coachServing), SegOption(str.coachReturning)), when (f.serving) { null -> 0; true -> 1; false -> 2 }, { i ->
                            f = f.copy(serving = if (i == 0) null else i == 1)
                        })
                    }
                    Label(str.coachServeKind)
                    Segmented(listOf(SegOption(str.coachAll), SegOption(str.coachFirst), SegOption(str.coachSecond)), when (f.secondServe) { null -> 0; false -> 1; true -> 2 }, { i ->
                        f = f.copy(secondServe = if (i == 0) null else i == 2)
                    })
                    Label(str.coachEnd)
                    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (k in listOf("ACE", "DOUBLE_FAULT", "WINNER", "UNFORCED_ERROR", "FORCED_ERROR")) {
                            val on = k in f.kinds
                            // Nel dialogo lo sfondo è già chiaro: le etichette spente vanno più scure, se no non si vedono.
                            Surface(onClick = { f = f.copy(kinds = if (on) f.kinds - k else f.kinds + k) }, shape = RoundedCornerShape(16.dp),
                                color = if (on) TsmColors.Ball else TsmColors.Background) {
                                Text(str.videoKind(k), color = if (on) TsmColors.OnBall else TsmColors.TextMain, fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                            }
                        }
                    }
                    Label(str.coachSituation)
                    Segmented(listOf(SegOption(str.coachAll), SegOption(str.coachBreak), SegOption(str.coachSetMatch)), when (f.situation) { "BREAK" -> 1; "SET_MATCH" -> 2; else -> 0 }, { i ->
                        f = f.copy(situation = when (i) { 1 -> "BREAK"; 2 -> "SET_MATCH"; else -> null })
                    })
                    Text(if (count == 0) str.coachNone else str.coachCount(count), color = if (count == 0) TsmColors.Orange else TsmColors.TextMain, fontSize = 13.sp)
                }
            },
            confirmButton = { TextButton(onClick = { onApply(f); onClose() }) { Text(str.coachApply) } },
            dismissButton = { TextButton(onClick = { onApply(CoachFilter()); onClose() }) { Text(str.coachClear) } },
        )
    }

    @Composable
    private fun Label(text: String) = Text(text, fontSize = 12.sp, color = TsmColors.TextDim, fontWeight = FontWeight.Bold)

    @Composable
    private fun Check(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
        Row(Modifier.fillMaxWidth().clickable { onChange(!on) }, verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Checkbox(on, onChange)
            Text(label, fontSize = 14.sp)
        }
    }

    @Composable
    private fun HighlightProgress(pct: Int) {
        val str = s
        AlertDialog(
            onDismissRequest = {},
            title = { Text(str.hlTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(str.hlMaking(pct))
                    androidx.compose.material3.LinearProgressIndicator(progress = { pct / 100f }, modifier = Modifier.fillMaxWidth(), color = TsmColors.Ball)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    maker?.cancel()
                    maker = null
                    hlProgress = null
                }) { Text(str.back) }
            },
        )
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
        /** Musica dell'app per le statistiche (res/raw/stats_music.m4a, i primi 31 secondi del brano). */
        const val STATS_MUSIC_CREDIT = "\"Nowhere Land\" Kevin MacLeod (incompetech.com), CC BY 4.0"
        /** Durata massima a piacere degli highlights (minuti). */
        const val MAX_HL_MINUTES = 90
        /** Percorso del file dei capitoli (.json) del video da aprire. */
        const val EXTRA_CHAPTERS = "chapters"
    }
}
