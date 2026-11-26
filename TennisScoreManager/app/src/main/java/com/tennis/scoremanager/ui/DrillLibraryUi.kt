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
import androidx.compose.ui.graphics.StrokeCap
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
private const val MARGIN_X = 1.8f
private const val MARGIN_Y = 2.0f

/** I colori dei colpi in ordine: 1 rosso, 2 arancio, 3 giallo, 4 azzurro (senza ordine: verde-giallo). */
private val ORDER_COLORS = listOf(Color(0xFFFF4D4D), Color(0xFFFF9800), Color(0xFFFFEB3B), Color(0xFF4FC3F7))
private fun shotColor(order: Int?): Color = order?.let { ORDER_COLORS[(it - 1).mod(ORDER_COLORS.size)] } ?: TsmColors.Ball

/**
 * Come si passa da un punto del campo (metri) a un punto del disegno. Dall'alto ([perspective] false) è una scala;
 * in prospettiva il campo si guarda da dietro la metà vicina, un po' dall'alto: le cose lontane sono più piccole
 * ([scale]) e la palla in volo ([z], metri) sale verso l'alto del disegno.
 */
private class Projection(val perspective: Boolean, val w: Float, minY: Float, maxY: Float) {
    private val spanX = 2 * (HALF_W + MARGIN_X)
    private val k = w / spanX
    private val minY = minY
    private val maxY = maxY
    // In prospettiva: il bordo lontano è largo il 58% di quello vicino.
    private val d0 = 1.38f * (maxY - minY)
    private val d1 = d0 + (maxY - minY)
    private val tilt = 0.62f
    val height: Float = if (perspective) (maxY - minY) * k * tilt else (maxY - minY) * k
    private fun d(y: Float) = d0 + (maxY - y)
    fun scale(y: Float): Float = if (perspective) d0 / d(y) else 1f
    fun at(x: Float, y: Float, z: Float = 0f): Offset {
        if (!perspective) return Offset((x + HALF_W + MARGIN_X) * k, (y - minY) * k - z * k * 0.5f)
        val u = (1 / d(y) - 1 / d1) / (1 / d0 - 1 / d1)
        return Offset(w / 2 + x * k * scale(y), u * height - z * k * scale(y) * 1.5f)
    }
    fun px(m: Float, y: Float) = m * k * scale(y)
}

/**
 * Il disegno di un esercizio, fatto dall'app. In prospettiva ([perspective], la scheda dell'esercizio) il campo si vede
 * da dietro A come in campo: la palla vola ad arco, un colore per ogni colpo dell'ordine (rosso, arancio, giallo,
 * azzurro) col numero accanto, i bersagli ovali col numero del colpo, le persone come omini con la racchetta dalla parte
 * del colpo (in arancio chi lancia le palle, col cesto), gli spostamenti tratteggiati e, tratteggiato, il riquadro dove
 * tornare dopo il colpo. Dall'alto (le miniature dell'elenco) le stesse cose più semplici.
 */
@Composable
fun CourtDiagram(d: DrillLibrary.Diagram, modifier: Modifier = Modifier, perspective: Boolean = false) {
    val area = LocalArea.current
    // In prospettiva più spazio dietro la linea di fondo lontana: gli omini sono in piedi e sporgono verso l'alto.
    val minY = if (d.half) -1.2f else -BASE - MARGIN_Y - if (perspective) 4.4f else 0f
    val maxY = BASE + MARGIN_Y + if (perspective) 0.6f else 0f
    val spanX = 2 * (HALF_W + MARGIN_X)
    val ratio = Projection(perspective, spanX, minY, maxY).let { spanX / it.height }
    Canvas(modifier.fillMaxWidth().aspectRatio(ratio)) {
        val pr = Projection(perspective, size.width, minY, maxY)
        fun o(x: Float, y: Float, z: Float = 0f) = pr.at(x, y, z)
        fun o(p: DrillLibrary.Pt) = pr.at(p.x, p.y)
        fun poly(pts: List<Offset>) = Path().apply { moveTo(pts[0].x, pts[0].y); for (p in pts.drop(1)) lineTo(p.x, p.y); close() }
        val top = if (d.half) 0f else -BASE
        // Il fondo (fuori dal campo) e il campo.
        drawRect(TsmColors.Court.copy(alpha = if (perspective) 0.55f else 1f), size = size)
        drawPath(poly(listOf(o(-HALF_W - 1f, top - 0.8f), o(HALF_W + 1f, top - 0.8f), o(HALF_W + 1f, BASE + 1.2f), o(-HALF_W - 1f, BASE + 1.2f))), TsmColors.Court)
        val line = TsmColors.CourtLine
        fun ln(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(line, o(x1, y1), o(x2, y2), (pr.px(0.07f, (y1 + y2) / 2)).coerceAtLeast(1.4f))
        ln(-HALF_W, top, HALF_W, top); ln(-HALF_W, BASE, HALF_W, BASE)
        for (x in listOf(-HALF_W, -SINGLES, SINGLES, HALF_W)) ln(x, top, x, BASE)
        for (y in listOfNotNull(SERVICE, (-SERVICE).takeIf { !d.half })) ln(-SINGLES, y, SINGLES, y)
        ln(0f, if (d.half) 0f else -SERVICE, 0f, SERVICE)
        ln(0f, BASE, 0f, BASE - 0.3f)
        if (!d.half) ln(0f, -BASE, 0f, -BASE + 0.3f)
        // La rete: in prospettiva con la sua altezza.
        if (perspective) {
            val h = 0.95f
            drawPath(poly(listOf(o(-HALF_W - 0.9f, 0f), o(HALF_W + 0.9f, 0f), o(HALF_W + 0.9f, 0f, h), o(-HALF_W - 0.9f, 0f, h))), Color.White.copy(alpha = 0.22f))
            drawLine(Color.White, o(-HALF_W - 0.9f, 0f, h), o(HALF_W + 0.9f, 0f, h), pr.px(0.12f, 0f).coerceAtLeast(2f))
            for (x in listOf(-HALF_W - 0.9f, HALF_W + 0.9f)) drawLine(Color.White, o(x, 0f), o(x, 0f, h), pr.px(0.12f, 0f).coerceAtLeast(2f))
        } else {
            drawLine(Color.White, o(-HALF_W - 0.5f, 0f), o(HALF_W + 0.5f, 0f), (pr.px(0.16f, 0f)).coerceAtLeast(2.5f))
        }
        // Le zone: le parti di campo (rettangoli) e i bersagli (ovali col numero del colpo).
        for (z in d.zones) {
            val x1 = minOf(z.a.x, z.b.x); val x2 = maxOf(z.a.x, z.b.x); val y1 = minOf(z.a.y, z.b.y); val y2 = maxOf(z.a.y, z.b.y)
            if (z.area) {
                drawPath(poly(listOf(o(x1, y1), o(x2, y1), o(x2, y2), o(x1, y2))), area.copy(alpha = 0.3f))
                continue
            }
            val cx = (x1 + x2) / 2; val cy = (y1 + y2) / 2; val rx = (x2 - x1) / 2; val ry = (y2 - y1) / 2
            val oval = Path().apply {
                for (i in 0..36) {
                    val a = i * Math.PI.toFloat() * 2 / 36
                    val p = o(cx + rx * cos(a), cy + ry * sin(a))
                    if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
                }
                close()
            }
            val fill = z.label.toIntOrNull()?.let { shotColor(it) } ?: TsmColors.Ball
            drawPath(oval, fill.copy(alpha = 0.45f))
            drawPath(oval, fill, style = Stroke(pr.px(0.08f, cy).coerceAtLeast(1.5f)))
            if (z.label.isNotEmpty()) text(z.label, o(cx, cy), pr.px(1.2f, cy).coerceAtLeast(9f), Color.Black)
        }
        // Dove tornare dopo il colpo: riquadro tratteggiato.
        for (r in d.recover) {
            val x1 = minOf(r.a.x, r.b.x); val x2 = maxOf(r.a.x, r.b.x); val y1 = minOf(r.a.y, r.b.y); val y2 = maxOf(r.a.y, r.b.y)
            drawPath(poly(listOf(o(x1, y1), o(x2, y1), o(x2, y2), o(x1, y2))), Color.White,
                style = Stroke(pr.px(0.07f, y2).coerceAtLeast(1.5f), pathEffect = PathEffect.dashPathEffect(floatArrayOf(pr.px(0.3f, y2), pr.px(0.2f, y2)))))
        }
        // I coni.
        for (c in d.cones) {
            val p = o(c); val r = pr.px(0.42f, c.y)
            drawPath(Path().apply { moveTo(p.x, p.y - r * 1.4f); lineTo(p.x + r * 0.8f, p.y); lineTo(p.x - r * 0.8f, p.y); close() }, TsmColors.Orange)
        }
        // Gli spostamenti, poi le persone, poi la palla sopra a tutto.
        for (path in d.paths.filter { it.move }) drawMove(path.points.map { o(it) }, pr.px(0.1f, path.points.last().y))
        for (who in d.players.sortedBy { it.at.y }) {
            if (perspective) drawFigure(who, pr, ::o, racketSide(d, who)) else drawDot(who, o(who.at), pr.px(0.6f, who.at.y))
        }
        d.paths.filter { !it.move }.forEachIndexed { idx, path ->
            val pts = path.points
            if (pts.size < 2) return@forEachIndexed
            // La palla in volo: sale e scende tra un punto e l'altro (in prospettiva si vede l'arco sopra la rete) e
            // piega un po' di lato, i colpi dispari da una parte e i pari dall'altra: andata e ritorno sulla stessa
            // diagonale non si coprono.
            var a = pts[0]; var b = pts.last()
            // In prospettiva la palla parte dalla racchetta di chi colpisce e arriva sulla racchetta di chi la riceve
            // (all'altezza dell'impatto), non a terra dietro di lui; sui bersagli arriva a terra.
            var za = 0f; var zb = 0f
            if (perspective) {
                hitPoint(d, a)?.let { (p, z) -> a = p; za = z }
                hitPoint(d, b)?.let { (p, z) -> b = p; zb = z }
            }
            val len = kotlin.math.hypot(b.x - a.x, b.y - a.y)
            val hMax = path.arc ?: (len * 0.07f).coerceIn(0.5f, 1.8f)
            // Il verso conta: un colpo verso il fondo lontano e il suo ritorno piegano da parti opposte.
            val sign = (if (((path.order ?: (idx + 1)) % 2) == 1) 1f else -1f) * (if (b.y < a.y) 1f else -1f)
            val bend = (len * 0.05f).coerceAtMost(1.1f) * sign
            val nx = -(b.y - a.y) / len; val ny = (b.x - a.x) / len
            fun curveWith(h: Float) = (0..32).map { i ->
                val f = i / 32f
                val side = 4 * f * (1 - f) * bend
                val z = if (perspective) za * (1 - f) + zb * f + 4 * h * f * (1 - f) else 0f
                o(a.x + (b.x - a.x) * f + nx * side, a.y + (b.y - a.y) * f + ny * side, z)
            }
            // Un pallonetto alto non esce dal disegno: se sfora in alto, l'arco si abbassa quanto basta.
            var h = hMax
            var curve = curveWith(h)
            while (curve.minOf { it.y } < 6f && h > 0.3f) { h *= 0.85f; curve = curveWith(h) }
            drawShot(curve, shotColor(path.order), path.order, pr.px(0.13f, (a.y + b.y) / 2).coerceAtLeast(2.2f), pr.px(0.75f, b.y).coerceAtLeast(7f))
        }
    }
}

/**
 * Da che parte tiene la racchetta [who], nel disegno: +1 a destra di chi guarda, -1 a sinistra. Il giocatore ha due
 * posizioni, dritto e rovescio (destrorso): chi è vicino si vede da dietro, quindi il suo rovescio è a sinistra; chi è
 * lontano si vede di fronte, quindi il suo rovescio è a destra di chi guarda. Se l'esercizio dice rovescio, è rovescio;
 * altrimenti decide la palla che gli arriva: dalla parte dove arriva (se arriva addosso, dritto).
 */
private fun racketSide(d: DrillLibrary.Diagram, who: DrillLibrary.Who): Float {
    val near = who.at.y > 0
    val forehand = if (near) 1f else -1f
    if (who.backhand) return -forehand
    val incoming = d.paths.filter { !it.move && it.points.size >= 2 }
        .filter { kotlin.math.hypot(it.points.last().x - who.at.x, it.points.last().y - who.at.y) < 1.5f }
        .minByOrNull { it.order ?: Int.MAX_VALUE } ?: return forehand
    val dx = incoming.points.last().x - who.at.x
    return if (kotlin.math.abs(dx) > 0.3f) kotlin.math.sign(dx) else forehand
}

/**
 * Dove colpisce chi sta in [at] (entro un metro e mezzo): la testa della racchetta, dalla parte dove la tiene, e la sua
 * altezza; per chi lancia le palle, la mano. null se lì non c'è nessuno (la palla arriva a terra, su un bersaglio).
 */
private fun hitPoint(d: DrillLibrary.Diagram, at: DrillLibrary.Pt): Pair<DrillLibrary.Pt, Float>? {
    val who = d.players.minByOrNull { kotlin.math.hypot(it.at.x - at.x, it.at.y - at.y) }
        ?.takeIf { kotlin.math.hypot(it.at.x - at.x, it.at.y - at.y) < 1.5f } ?: return null
    val side = racketSide(d, who)
    return if (who.feeder) DrillLibrary.Pt(who.at.x + side * 0.6f, who.at.y) to 1.16f
    else DrillLibrary.Pt(who.at.x + side * 0.93f, who.at.y) to 1.52f
}

/** Chi è in campo, dall'alto: un cerchio con la lettera (giallo A, rosso gli altri, arancio chi lancia). */
private fun DrawScope.drawDot(who: DrillLibrary.Who, p: Offset, r: Float) {
    val fill = when {
        who.feeder -> TsmColors.Orange
        who.label.startsWith("A") -> TsmColors.Player1
        else -> TsmColors.Player2
    }
    drawCircle(fill, r, p)
    drawCircle(Color.Black.copy(alpha = 0.5f), r, p, style = Stroke(r * 0.15f))
    val label = if (who.feeder) "●" else who.label
    text(label, p, r * if (label.length > 1) 1.0f else 1.25f, if (fill == TsmColors.Player1 || who.feeder) Color.Black else Color.White)
}

/**
 * Una persona in campo, in posizione di attesa (ginocchia un po' piegate): ombra a terra, scarpe, gambe, pantaloncini,
 * maglia nel colore del giocatore (A giallo, gli altri rossi, chi lancia arancio), braccia, testa coi capelli e la
 * racchetta con le corde dalla parte del colpo (dritto o rovescio; chi è vicino si vede da dietro, chi è lontano di
 * fronte). Chi lancia tiene una palla e ha il cesto accanto. La lettera in un cerchietto di fianco.
 */
private fun DrawScope.drawFigure(who: DrillLibrary.Who, pr: Projection, o: (Float, Float, Float) -> Offset, side: Float) {
    val x = who.at.x; val y = who.at.y
    val shirt = when {
        who.feeder -> TsmColors.Orange
        who.label.startsWith("A") -> TsmColors.Player1
        else -> TsmColors.Player2
    }
    val skin = Color(0xFFE2B48C)
    val shorts = Color(0xFF1E2636)
    val hair = Color(0xFF3B2A1E)
    val outline = Color(0xFF0B0F18).copy(alpha = 0.55f)
    val near = y > 0
    // Un punto della sagoma: [dx] di lato (metri), [z] in altezza.
    fun q(dx: Float, z: Float) = o(x + dx, y, z)
    fun px(m: Float) = pr.px(m, y)
    fun limb(color: Color, w: Float, vararg pts: Pair<Float, Float>) {
        for (k in 0 until pts.size - 1) drawLine(color, q(pts[k].first, pts[k].second), q(pts[k + 1].first, pts[k + 1].second), px(w), cap = StrokeCap.Round)
    }
    fun shape(color: Color, vararg pts: Pair<Float, Float>) {
        val path = Path().apply {
            val first = q(pts[0].first, pts[0].second); moveTo(first.x, first.y)
            for (p in pts.drop(1)) { val r = q(p.first, p.second); lineTo(r.x, r.y) }
            close()
        }
        drawPath(path, color)
        drawPath(path, outline, style = Stroke(px(0.025f).coerceAtLeast(1f)))
    }
    // Ombra a terra.
    val c = o(x, y, 0f)
    drawOval(Color.Black.copy(alpha = 0.28f), topLeft = Offset(c.x - px(0.5f), c.y - px(0.12f)), size = Size(px(1.0f), px(0.24f)))
    // Gambe piegate (coscia, polpaccio) e scarpe.
    for (s in listOf(-1f, 1f)) {
        limb(skin, 0.16f, s * 0.12f to 0.82f, s * 0.3f to 0.46f, s * 0.3f to 0.1f)
        drawOval(Color.White, topLeft = q(s * 0.3f - 0.12f, 0.11f), size = Size(px(0.24f), px(0.11f)))
    }
    // Pantaloncini e maglia.
    shape(shorts, -0.22f to 0.98f, 0.22f to 0.98f, 0.27f to 0.7f, 0.03f to 0.7f, 0f to 0.8f, -0.03f to 0.7f, -0.27f to 0.7f)
    shape(shirt, -0.25f to 1.47f, 0.25f to 1.47f, 0.23f to 1.2f, 0.21f to 0.94f, -0.21f to 0.94f, -0.23f to 1.2f)
    // Braccio senza racchetta piegato davanti; braccio con la racchetta in fuori.
    limb(shirt, 0.13f, -side * 0.24f to 1.44f, -side * 0.3f to 1.32f)
    limb(skin, 0.11f, -side * 0.3f to 1.32f, -side * 0.36f to 1.14f, -side * 0.16f to 1.04f)
    limb(shirt, 0.13f, side * 0.24f to 1.44f, side * 0.33f to 1.33f)
    limb(skin, 0.11f, side * 0.33f to 1.33f, side * 0.46f to 1.19f, side * 0.58f to 1.12f)
    if (who.feeder) {
        // La palla in mano e il cesto accanto.
        drawCircle(TsmColors.Ball, px(0.08f), q(side * 0.6f, 1.16f))
        val bx = -side * 0.75f
        shape(Color(0xFF555B66), bx - 0.22f to 0.55f, bx + 0.22f to 0.55f, bx + 0.18f to 0f, bx - 0.18f to 0f)
        for (k in -1..1) drawCircle(TsmColors.Ball, px(0.07f), q(bx + k * 0.12f, 0.6f))
    } else {
        // La racchetta: manico, cuore, ovale con le corde.
        limb(Color(0xFF222222), 0.045f, side * 0.58f to 1.12f, side * 0.76f to 1.32f)
        val head = q(side * 0.93f, 1.52f)
        val rw = px(0.3f); val rh = px(0.4f)
        drawOval(Color.White.copy(alpha = 0.55f), topLeft = Offset(head.x - rw / 2, head.y - rh / 2), size = Size(rw, rh))
        drawOval(Color(0xFF222222), topLeft = Offset(head.x - rw / 2, head.y - rh / 2), size = Size(rw, rh), style = Stroke(px(0.03f).coerceAtLeast(1.2f)))
    }
    // Collo, testa e capelli (visti da dietro i capelli coprono quasi tutta la testa).
    limb(skin, 0.08f, 0f to 1.47f, 0f to 1.55f)
    val headC = q(0f, 1.66f)
    val hr = px(0.12f)
    drawCircle(skin, hr, headC)
    if (near) drawCircle(hair, hr * 0.95f, Offset(headC.x, headC.y - hr * 0.08f))
    else drawArc(hair, 180f, 180f, useCenter = true, topLeft = Offset(headC.x - hr, headC.y - hr * 1.05f), size = Size(hr * 2, hr * 1.4f))
    drawCircle(outline, hr, headC, style = Stroke(px(0.02f).coerceAtLeast(1f)))
    // La lettera, in un cerchietto di fianco (dalla parte senza racchetta).
    if (who.label.isNotEmpty()) {
        val r = px(0.36f).coerceAtLeast(7f)
        val lc = q(-side * 0.62f, 1.25f)
        drawCircle(shirt, r, lc)
        drawCircle(outline, r, lc, style = Stroke(px(0.025f).coerceAtLeast(1f)))
        text(who.label, lc, r * if (who.label.length > 1) 1.0f else 1.3f, if (shirt == TsmColors.Player1 || who.feeder) Color.Black else Color.White)
    }
}

/** Uno spostamento: tratteggiato, con la freccia. */
private fun DrawScope.drawMove(pts: List<Offset>, w: Float) {
    if (pts.size < 2) return
    val color = Color.White.copy(alpha = 0.9f)
    val stroke = w.coerceAtLeast(1.8f)
    val path = Path().apply { moveTo(pts[0].x, pts[0].y); for (p in pts.drop(1)) lineTo(p.x, p.y) }
    drawPath(path, color, style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(stroke * 3.5f, stroke * 2.5f))))
    arrowHead(pts[pts.size - 2], pts.last(), color, stroke, stroke * 4.5f)
}

/** La palla: piena nel colore del suo numero, con la freccia e il numero a metà. */
private fun DrawScope.drawShot(pts: List<Offset>, color: Color, order: Int?, w: Float, head: Float) {
    val path = Path().apply { moveTo(pts[0].x, pts[0].y); for (p in pts.drop(1)) lineTo(p.x, p.y) }
    drawPath(path, Color.Black.copy(alpha = 0.35f), style = Stroke(w * 1.8f))
    drawPath(path, color, style = Stroke(w))
    arrowHead(pts[pts.size - 2], pts.last(), color, w, head)
    order?.let {
        val m = pts[pts.size * 3 / 10]
        val r = head * 0.62f
        drawCircle(Color.Black.copy(alpha = 0.8f), r, m)
        text("$it", m, r * 1.3f, color)
    }
}

private fun DrawScope.arrowHead(a: Offset, b: Offset, color: Color, w: Float, len: Float) {
    val ang = atan2(b.y - a.y, b.x - a.x)
    for (d in listOf(2.6f, -2.6f)) drawLine(color, b, Offset(b.x + len * cos(ang + d), b.y + len * sin(ang + d)), w)
}

/** Una scritta centrata in [c], alta [h] pixel. */
private fun DrawScope.text(t: String, c: Offset, h: Float, color: Color) {
    val paint = android.graphics.Paint().apply {
        isAntiAlias = true; textSize = h; this.color = color.toArgb(); textAlign = android.graphics.Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    drawContext.canvas.nativeCanvas.drawText(t, c.x, c.y + h * 0.36f, paint)
}
