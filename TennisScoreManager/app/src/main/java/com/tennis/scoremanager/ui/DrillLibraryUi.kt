// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.tennis.scoremanager.data.DrillLibrary
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Il nome di un argomento della raccolta: i colpi come nell'allenamento, gli altri coi loro nomi. */
fun libTopicLabel(s: Strings, t: DrillLibrary.Topic): String = when (t) {
    DrillLibrary.Topic.SERVE -> strokeLabel(s, com.tennis.scoremanager.data.TrainedStroke.SERVE)
    DrillLibrary.Topic.FOREHAND -> strokeLabel(s, com.tennis.scoremanager.data.TrainedStroke.FOREHAND)
    DrillLibrary.Topic.BACKHAND -> strokeLabel(s, com.tennis.scoremanager.data.TrainedStroke.BACKHAND)
    DrillLibrary.Topic.VOLLEY -> strokeLabel(s, com.tennis.scoremanager.data.TrainedStroke.VOLLEY)
    DrillLibrary.Topic.RETURN -> s.dlReturn
    DrillLibrary.Topic.OVERHEAD -> s.dlOverhead
    DrillLibrary.Topic.FOOTWORK -> s.dlFootwork
    DrillLibrary.Topic.TACTICS -> s.cnTactics
    DrillLibrary.Topic.FITNESS -> s.cnFitness
}

fun libLevelLabel(s: Strings, l: DrillLibrary.Level): String = when (l) {
    DrillLibrary.Level.BEGINNER -> s.dlBeginner
    DrillLibrary.Level.INTERMEDIATE -> s.dlIntermediate
    DrillLibrary.Level.ADVANCED -> s.dlAdvanced
}

private const val HALF_W = 5.485f
private const val BASE = 11.885f
private const val SERVICE = 6.40f
private const val SINGLES = 4.115f
private const val MARGIN_X = 1.6f
private const val MARGIN_Y = 2.0f

/**
 * Il disegno di un esercizio, fatto dall'app: il campo visto dall'alto (la metà vicina in basso), le zone bersaglio, i
 * coni, chi è in campo (A in giallo, gli altri in rosso, l'allenatore che lancia in arancio), la palla (linea piena con
 * la freccia e, se conta, il numero dell'ordine) e gli spostamenti (tratteggiati).
 */
@Composable
fun CourtDiagram(d: DrillLibrary.Diagram, modifier: Modifier = Modifier) {
    val area = LocalArea.current
    val minY = if (d.half) -1.2f else -BASE - MARGIN_Y
    val maxY = BASE + MARGIN_Y
    val spanX = 2 * (HALF_W + MARGIN_X)
    val spanY = maxY - minY
    Canvas(modifier.fillMaxWidth().aspectRatio(spanX / spanY)) {
        val k = size.width / spanX
        fun o(p: DrillLibrary.Pt) = Offset((p.x + HALF_W + MARGIN_X) * k, (p.y - minY) * k)
        fun o(x: Float, y: Float) = o(DrillLibrary.Pt(x, y))
        // Il campo e le sue linee.
        drawRect(TsmColors.Court, topLeft = Offset.Zero, size = size)
        val line = TsmColors.CourtLine
        val w = (0.08f * k).coerceAtLeast(1.5f)
        val top = if (d.half) 0f else -BASE
        drawRect(line, topLeft = o(-HALF_W, top), size = Size(2 * HALF_W * k, (BASE - top) * k), style = Stroke(w))
        for (x in listOf(-SINGLES, SINGLES)) drawLine(line, o(x, top), o(x, BASE), w)
        for (y in listOfNotNull(SERVICE, (-SERVICE).takeIf { !d.half })) drawLine(line, o(-SINGLES, y), o(SINGLES, y), w)
        drawLine(line, o(0f, if (d.half) 0f else -SERVICE), o(0f, SERVICE), w)
        drawLine(line, o(0f, BASE), o(0f, BASE - 0.3f), w)
        if (!d.half) drawLine(line, o(0f, -BASE), o(0f, -BASE + 0.3f), w)
        // La rete.
        drawLine(Color.White, o(-HALF_W - 0.5f, 0f), o(HALF_W + 0.5f, 0f), w * 2.2f)
        // Le zone bersaglio, nel colore dell'area.
        for (z in d.zones) {
            val a = o(minOf(z.a.x, z.b.x), minOf(z.a.y, z.b.y))
            val b = o(maxOf(z.a.x, z.b.x), maxOf(z.a.y, z.b.y))
            drawRect(area.copy(alpha = 0.35f), topLeft = a, size = Size(b.x - a.x, b.y - a.y))
        }
        // I coni.
        for (c in d.cones) {
            val p = o(c); val r = 0.38f * k
            drawPath(Path().apply { moveTo(p.x, p.y - r); lineTo(p.x + r, p.y + r * 0.8f); lineTo(p.x - r, p.y + r * 0.8f); close() }, TsmColors.Orange)
        }
        // Traiettorie: prima gli spostamenti, poi la palla.
        for (path in d.paths.sortedBy { !it.move }) drawTrajectory(path.points.map { o(it) }, path.move, path.order, k)
        // Chi è in campo.
        for (who in d.players) {
            val p = o(who.at)
            val r = 0.55f * k
            val fill = when {
                who.feeder -> TsmColors.Orange
                who.label.startsWith("A") -> TsmColors.Player1
                else -> TsmColors.Player2
            }
            drawCircle(fill, r, p)
            drawCircle(Color.Black.copy(alpha = 0.5f), r, p, style = Stroke(w))
            val label = if (who.feeder) "●" else who.label
            text(label, p, r * if (label.length > 1) 1.0f else 1.25f, if (fill == TsmColors.Player1 || who.feeder) Color.Black else Color.White)
        }
    }
}

/** Una traiettoria: piena con la freccia (palla) o tratteggiata (spostamento); col numero a metà se l'ordine conta. */
private fun DrawScope.drawTrajectory(pts: List<Offset>, move: Boolean, order: Int?, k: Float) {
    if (pts.size < 2) return
    val color = if (move) Color.White.copy(alpha = 0.85f) else TsmColors.Ball
    val w = (0.11f * k).coerceAtLeast(2f)
    val path = Path().apply { moveTo(pts[0].x, pts[0].y); for (p in pts.drop(1)) lineTo(p.x, p.y) }
    drawPath(path, color, style = Stroke(w, pathEffect = if (move) PathEffect.dashPathEffect(floatArrayOf(0.45f * k, 0.3f * k)) else null))
    // La freccia alla fine.
    val a = pts[pts.size - 2]; val b = pts.last()
    val ang = atan2(b.y - a.y, b.x - a.x)
    val len = 0.6f * k
    for (d in listOf(2.6f, -2.6f)) drawLine(color, b, Offset(b.x + len * cos(ang + d), b.y + len * sin(ang + d)), w)
    order?.let {
        val m = Offset((a.x + b.x) / 2, (a.y + b.y) / 2)
        val r = 0.42f * k
        drawCircle(Color.Black.copy(alpha = 0.75f), r, m)
        text("$it", m, r * 1.3f, color)
    }
}

/** Una scritta centrata in [c], alta [h] pixel. */
private fun DrawScope.text(t: String, c: Offset, h: Float, color: Color) {
    val paint = android.graphics.Paint().apply {
        isAntiAlias = true; textSize = h; this.color = color.toArgb(); textAlign = android.graphics.Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    drawContext.canvas.nativeCanvas.drawText(t, c.x, c.y + h * 0.36f, paint)
}
