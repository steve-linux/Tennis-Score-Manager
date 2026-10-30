// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.ui

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SportsTennis
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tennis.scoremanager.MatchController
import com.tennis.scoremanager.data.Reports
import com.tennis.scoremanager.data.ServeAttempt
import com.tennis.scoremanager.data.ServeKind
import com.tennis.scoremanager.data.ServeSession
import com.tennis.scoremanager.data.ServeSide
import com.tennis.scoremanager.data.ServeTarget
import com.tennis.scoremanager.data.ServeTraining
import com.tennis.scoremanager.data.ShotDirection
import com.tennis.scoremanager.data.TrainedStroke
import com.tennis.scoremanager.data.Wing
import com.tennis.scoremanager.video.CameraActivity
import com.tennis.scoremanager.video.ReplayActivity

fun serveSideName(s: Strings, v: ServeSide) = if (v == ServeSide.DEUCE) s.serveSideDeuce else s.serveSideAd
fun serveKindName(s: Strings, v: ServeKind) = if (v == ServeKind.FIRST) s.serveFirst else s.serveSecond
fun serveTargetName(s: Strings, v: ServeTarget?) = when (v) {
    ServeTarget.T -> s.serveTargetT
    ServeTarget.BODY -> s.serveTargetBody
    ServeTarget.WIDE -> s.serveTargetWide
    null -> s.serveTargetNone
}

fun strokeLabel(s: Strings, v: TrainedStroke) = s.strokeName(v.band)
fun directionName(s: Strings, v: ShotDirection?) = when (v) {
    ShotDirection.CROSS -> s.dirCross
    ShotDirection.MIDDLE -> s.dirMiddle
    ShotDirection.LINE -> s.dirLine
    null -> s.dirNone
}
fun wingName(s: Strings, v: Wing) = if (v == Wing.FOREHAND) s.wingFh else s.wingBh

/**
 * Pagina Allenamento: scelta del colpo (servizio, dritto, rovescio, volée), avvio e le ultime sessioni di quel colpo;
 * le altre in "Tutte le sessioni".
 */
@Composable
fun ServeTrainingCard(c: MatchController) {
    val s = LocalStrings.current
    val context = LocalContext.current
    val sessions by c.serveSessions.collectAsState()
    val stroke by c.trainStroke.collectAsState()
    var help by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf<ServeSession?>(null) }
    LaunchedEffect(Unit) { c.refreshServeSessions() }
    val mine = sessions.filter { it.stroke == stroke }
    SectionCard(s.strokeTrainingTitle, Icons.Filled.SportsTennis) {
        Text(s.strokeTrainingHint, color = TsmColors.TextDim, fontSize = 13.sp)
        val strokes = TrainedStroke.entries
        // Ogni colpo col numero delle sue sessioni: l'elenco qui sotto mostra solo quelle del colpo scelto.
        Segmented(
            strokes.map { t -> SegOption(strokeLabel(s, t) + sessions.count { it.stroke == t }.takeIf { it > 0 }?.let { " · $it" }.orEmpty()) },
            strokes.indexOf(stroke), { c.trainStroke.value = strokes[it] }, columns = 2,
        )
        BigButton(s.serveStart, Icons.Filled.Videocam, {
            context.startActivity(
                Intent(context, CameraActivity::class.java).putExtra(CameraActivity.EXTRA_SERVE, true).putExtra(CameraActivity.EXTRA_STROKE, stroke.name),
            )
        }, Modifier.fillMaxWidth())
        GhostButton(s.servePlaceShow, Icons.Filled.Videocam, { help = true }, Modifier.fillMaxWidth())
        if (mine.isNotEmpty()) {
            Text(s.strokeSessionsTitle(strokeLabel(s, stroke)), color = TsmColors.TextMain, fontWeight = FontWeight.Bold)
            StrokeTrend(mine) { c.home(com.tennis.scoremanager.HomeTab.PROGRESS) }
            for (sess in mine.take(RECENT_SESSIONS)) SessionRow(c, sess) { open = sess }
            // Sempre, anche con poche sessioni: lì ci sono cartella, spazio occupato e la selezione per cancellare.
            GhostButton(s.strokeAllSessions(mine.size), Icons.Filled.FolderOpen, { c.openStrokeSessions(stroke) }, Modifier.fillMaxWidth())
        } else if (sessions.isNotEmpty()) {
            Text(s.strokeNone, color = TsmColors.TextDim, fontSize = 13.sp)
        }
    }
    if (help) ServePlacementDialog(stroke) { help = false }
    open?.let { sess -> ServeSessionDialog(c, sess) { open = null } }
}

/** Sotto "Inizia" le ultime sessioni del colpo scelto; le altre nella pagina di tutte le sessioni. */
const val RECENT_SESSIONS = 3

/** Una sessione in elenco: data e ora, colpi dentro e percentuale. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SessionRow(
    c: MatchController, sess: ServeSession, extra: String? = null, modifier: Modifier = Modifier, onLongClick: (() -> Unit)? = null, onClick: () -> Unit,
) {
    val s = LocalStrings.current
    val lang = c.options.value.lang
    val n = ServeTraining.count(sess.serves)
    Row(modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(vertical = 6.dp)) {
        Column(Modifier.weight(1f)) {
            Text("${Reports.date(sess.startedAt, lang)} ${Reports.time(sess.startedAt, lang)}", color = TsmColors.TextMain, fontSize = 14.sp)
            if (extra != null) Text(extra, color = TsmColors.TextDim, fontSize = 12.sp)
        }
        Text(s.serveCountLine(n.inPlay, n.total) + (n.pct?.let { " · $it%" } ?: ""), color = TsmColors.Ball, fontSize = 14.sp)
    }
}

/**
 * Percentuale di colpi dentro, sessione dopo sessione (dalla più vecchia), come una linea; con meno di due sessioni
 * non si disegna. [onClick] apre Progressi.
 */
@Composable
fun StrokeTrend(sessions: List<ServeSession>, title: String? = null, onClick: (() -> Unit)? = null) {
    val s = LocalStrings.current
    val pcts = sessions.sortedBy { it.startedAt }.mapNotNull { ServeTraining.count(it.serves).pct }.takeLast(20)
    if (pcts.size < 2) return
    Column(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)) {
        Row(Modifier.fillMaxWidth()) {
            Text(title ?: s.strokeTrend, color = if (title != null) TsmColors.TextMain else TsmColors.TextDim, fontSize = if (title != null) 14.sp else 12.sp,
                fontWeight = if (title != null) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f).padding(end = 8.dp))
            Text("${pcts.first()}% → ${pcts.last()}%", color = TsmColors.TextMain, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        val line = TsmColors.Ball
        val grid = TsmColors.TextDim.copy(alpha = 0.3f)
        // Scala sui valori (almeno 20 punti), così si vede anche un passo dall'80 all'83 %.
        val lo = (minOf(pcts.min(), pcts.max() - 20).coerceAtLeast(0)).toFloat()
        val hi = (maxOf(pcts.max(), pcts.min() + 20).coerceAtMost(100)).toFloat().coerceAtLeast(lo + 1)
        Canvas(Modifier.fillMaxWidth().height(56.dp).padding(vertical = 6.dp)) {
            drawLine(grid, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1f)
            drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1f)
            val dx = size.width / (pcts.size - 1)
            val pts = pcts.mapIndexed { i, p -> Offset(i * dx, size.height * (1 - (p - lo) / (hi - lo))) }
            for (i in 1 until pts.size) drawLine(line, pts[i - 1], pts[i], strokeWidth = 4f)
            for (p in pts) drawCircle(line, 5f, p)
        }
    }
}

/**
 * Dove mettere il telefono: uno schema dall'alto (metà campo, chi serve, il telefono a fianco e l'alternativa
 * dietro, tratteggiata) e le indicazioni.
 */
@Composable
fun ServePlacementDialog(stroke: TrainedStroke = TrainedStroke.SERVE, onClose: () -> Unit) {
    val s = LocalStrings.current
    val serve = stroke == TrainedStroke.SERVE
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(s.servePlaceTitle + " · " + strokeLabel(s, stroke)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PlacementDiagram(stroke)
                Text(
                    "● " + (if (serve) s.servePlaceServer else s.placeHitter) + "   ▮ " + s.servePlacePhone + (if (serve) "   ▯ " + s.servePlaceAlt else ""),
                    fontSize = 12.sp, color = TsmColors.TextDim,
                )
                Text(
                    when (stroke) {
                        TrainedStroke.SERVE -> s.servePlaceText
                        TrainedStroke.VOLLEY -> s.placeVolleyText
                        else -> s.placeGroundText
                    },
                    fontSize = 13.sp, color = TsmColors.TextMain,
                )
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(s.ok) } },
    )
}

/**
 * Metà campo vista dall'alto, rete in alto. Servizio: chi serve (giallo) a destra del segno centrale, il telefono
 * (verde) a fianco e l'alternativa dietro. Dritto e rovescio: chi colpisce dietro la linea di fondo, il telefono di
 * lato fuori dal corridoio. Volée: chi gioca a rete, il telefono di lato alla sua altezza.
 */
@Composable
private fun PlacementDiagram(stroke: TrainedStroke) {
    // 16 m di larghezza (campo da doppio con spazio ai lati) e 17,5 m di profondità: la metà campo e i 5 m dietro la linea.
    // Al massimo 260 dp d'altezza: con il telefono in orizzontale (telecamera) resta spazio anche per le indicazioni.
    Canvas(Modifier.fillMaxWidth().heightIn(max = 260.dp).aspectRatio(16f / 17.5f, matchHeightConstraintsFirst = true)) {
        val m = size.width / 16f
        val left = 2.5f * m
        val right = left + 10.97f * m
        val net = 0.6f * m
        val base = net + 11.885f * m
        val single = 1.37f * m
        val line = Color.White.copy(alpha = 0.8f)
        val st = Stroke(width = 2f)
        drawRect(TsmColors.Court, Offset(left, net), Size(right - left, base - net))
        drawRect(line, Offset(left, net), Size(right - left, base - net), style = st)
        drawLine(line, Offset(left + single, net), Offset(left + single, base), 2f)
        drawLine(line, Offset(right - single, net), Offset(right - single, base), 2f)
        val service = net + 6.40f * m
        drawLine(line, Offset(left + single, service), Offset(right - single, service), 2f)
        val mid = (left + right) / 2
        drawLine(line, Offset(mid, net), Offset(mid, service), 2f)
        drawLine(Color.White, Offset(left - 0.9f * m, net), Offset(right + 0.9f * m, net), 4f)
        if (stroke != TrainedStroke.SERVE) {
            val hitter = if (stroke == TrainedStroke.VOLLEY) Offset(mid, net + 2f * m) else Offset(mid, base + 0.6f * m)
            drawCircle(TsmColors.Player1, 0.45f * m, hitter)
            val phone = Offset(right + 1f * m, hitter.y)
            drawRect(TsmColors.Ball, Offset(phone.x - 0.3f * m, phone.y - 0.5f * m), Size(0.6f * m, 1f * m))
            drawLine(TsmColors.Ball, phone, hitter, 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
            return@Canvas
        }
        // Chi serve: dietro la linea di fondo, a destra del centro (servizio da destra).
        val server = Offset(mid + 1f * m, base + 0.5f * m)
        drawCircle(TsmColors.Player1, 0.45f * m, server)
        // Il telefono: 3,5 m a destra di chi serve, puntato su di lui.
        val phone = Offset(server.x + 3.5f * m, base + 0.2f * m)
        drawRect(TsmColors.Ball, Offset(phone.x - 0.3f * m, phone.y - 0.5f * m), Size(0.6f * m, 1f * m))
        drawLine(TsmColors.Ball, phone, server, 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
        // Alternativa: dietro chi serve, un po' di lato (per vedere dove va la palla).
        val behind = Offset(server.x + 1.2f * m, base + 3.8f * m)
        drawRect(TsmColors.Ball.copy(alpha = 0.45f), Offset(behind.x - 0.3f * m, behind.y - 0.5f * m), Size(0.6f * m, 1f * m), style = st)
    }
}

/** Riepilogo di una sessione: totali, per lato, prima e seconda, bersaglio, polso; sotto, le clip da rivedere. */
@Composable
fun ServeSummaryContent(s: Strings, serves: List<ServeAttempt>, stroke: TrainedStroke = TrainedStroke.SERVE) {
    val b = ServeTraining.breakdown(serves, stroke)
    Text(s.serveCountLine(b.all.inPlay, b.all.total) + (b.all.pct?.let { " · $it%" } ?: ""), color = TsmColors.Ball, fontWeight = FontWeight.Bold, fontSize = 22.sp)
    @Composable
    fun rows(title: String, map: Map<String, ServeTraining.Count>) {
        if (map.isEmpty()) return
        Text(title, color = TsmColors.TextDim, fontSize = 13.sp)
        for ((name, n) in map) {
            Row(Modifier.fillMaxWidth()) {
                Text(name, color = TsmColors.TextMain, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text(s.serveCountLine(n.inPlay, n.total) + (n.pct?.let { " · $it%" } ?: ""), color = TsmColors.TextMain, fontSize = 14.sp)
            }
        }
    }
    rows(s.serveBySide, b.bySide.mapKeys { serveSideName(s, it.key) })
    rows(s.serveByKind, b.byKind.mapKeys { serveKindName(s, it.key) })
    rows(s.serveByTarget, b.byTarget.mapKeys { serveTargetName(s, it.key) })
    rows(s.byWing, b.byWing.mapKeys { wingName(s, it.key) })
    rows(s.byDirection, b.byDirection.mapKeys { directionName(s, it.key) })
    val gin = ServeTraining.meanGyro(serves.filter { it.inPlay })
    val gout = ServeTraining.meanGyro(serves.filter { !it.inPlay })
    if (gin != null && gout != null) Text(s.serveWrist(gin, gout), color = TsmColors.TextDim, fontSize = 13.sp)
}

/** Sessione salvata: riepilogo, clip da rivedere (tutte, solo dentro, solo fuori) ed eliminazione. */
@Composable
fun ServeSessionDialog(c: MatchController, sess: ServeSession, onClose: () -> Unit) {
    val s = LocalStrings.current
    val context = LocalContext.current
    var ask by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(s.serveSummary + " · " + strokeLabel(s, sess.stroke)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ServeSummaryContent(s, sess.serves, sess.stroke)
                ServeReplayButtons(c, sess)
                // Posizione del corpo nelle clip: misure dei momenti chiave, dentro contro fuori.
                if (sess.serves.any { it.clip != null }) {
                    GhostButton(s.poseTitle, Icons.Filled.AccessibilityNew, {
                        context.startActivity(Intent(context, com.tennis.scoremanager.video.ServeAnalysisActivity::class.java)
                            .putExtra(com.tennis.scoremanager.video.ServeAnalysisActivity.EXTRA_ID, sess.id))
                    }, Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(s.ok) } },
        dismissButton = {
            TextButton(onClick = { ask = true }) {
                Icon(Icons.Filled.Delete, null, tint = TsmColors.Danger)
                Text(" " + s.serveDelete, color = TsmColors.Danger)
            }
        },
    )
    if (ask) {
        AlertDialog(
            onDismissRequest = { ask = false },
            text = { Text(s.serveDeleteAsk) },
            confirmButton = { TextButton(onClick = { ask = false; c.deleteServeSession(sess); onClose() }) { Text(s.serveDelete, color = TsmColors.Danger) } },
            dismissButton = { TextButton(onClick = { ask = false }) { Text(s.back) } },
        )
    }
}

/** Rivedi tutti / solo dentro / solo fuori, nella moviola (pausa, rallentatore, fotogramma per fotogramma). */
@Composable
fun ServeReplayButtons(c: MatchController, sess: ServeSession) {
    val s = LocalStrings.current
    val context = LocalContext.current
    if (sess.serves.none { it.clip != null }) {
        Text(s.serveNoClips, color = TsmColors.TextDim, fontSize = 13.sp)
        return
    }
    fun play(list: List<ServeAttempt>) {
        val files = c.serveClipFiles(sess, list)
        if (files.isEmpty()) return
        context.startActivity(Intent(context, ReplayActivity::class.java).putExtra(ReplayActivity.EXTRA_FILES, files.map { it.absolutePath }.toTypedArray()))
    }
    GhostButton(s.servePlayAll, Icons.Filled.PlayArrow, { play(sess.serves) }, Modifier.fillMaxWidth())
    if (sess.serves.any { it.inPlay && it.clip != null }) GhostButton(s.servePlayIn, Icons.Filled.PlayArrow, { play(sess.serves.filter { it.inPlay }) }, Modifier.fillMaxWidth())
    if (sess.serves.any { !it.inPlay && it.clip != null }) GhostButton(s.servePlayOut, Icons.Filled.PlayArrow, { play(sess.serves.filter { !it.inPlay }) }, Modifier.fillMaxWidth())
}
