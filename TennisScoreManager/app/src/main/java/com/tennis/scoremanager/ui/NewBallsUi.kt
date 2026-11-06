// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Durata dell'animazione del cambio palle, come sul tabellone TV. */
private const val BALLS_MS = 5_500

/** Da 0 a 1 tra [a] e [b] del tempo (fuori, fermo ai capi). */
private fun phase(t: Float, a: Float, b: Float): Float = ((t - a) / (b - a)).coerceIn(0f, 1f)

private fun lerp(a: Float, b: Float, f: Float) = a + (b - a) * f

/** Rallenta alla fine: come un lancio che arriva. */
private fun easeOut(f: Float) = 1f - (1f - f) * (1f - f)

/**
 * Il cambio palle sul telefono: il tubo sale, salta il tappo, escono tre palle che rimbalzano e arriva la scritta.
 * Tocca per chiudere; [onDone] arriva anche da sola, a fine animazione.
 */
@Composable
fun NewBallsOverlay(text: String, onDone: () -> Unit) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        anim.animateTo(1f, tween(BALLS_MS, easing = LinearEasing))
        onDone()
    }
    val t = anim.value
    val fade = when {
        t < 0.06f -> t / 0.06f
        t > 0.88f -> (1f - t) / 0.12f
        else -> 1f
    }.coerceIn(0f, 1f)
    Box(
        Modifier.fillMaxSize().alpha(fade).background(Brush.radialGradient(listOf(Color(0xF51E2E46), Color(0xFA05070B))))
            .clickable(remember { MutableInteractionSource() }, null) { onDone() },
    ) {
        Canvas(Modifier.fillMaxSize()) { drawBalls(t) }
        val w = phase(t, 0.38f, 0.48f)
        Text(
            text.uppercase(), color = Color(0xFFF1F5FB), fontSize = 40.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center,
            letterSpacing = (lerp(10f, 3f, phase(t, 0.38f, 0.54f))).sp, maxLines = 1, softWrap = false,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 110.dp).fillMaxWidth()
                .alpha(if (t > 0.88f) fade else w).scale(lerp(0.4f, 1f, easeOut(w))),
        )
    }
}

private val ballFill = Brush.radialGradient(listOf(Color(0xFFF6FFB0), Color(0xFFC6F432), Color(0xFF8DB21A)))

private fun DrawScope.drawBalls(t: Float) {
    val h = size.height
    val cx = size.width / 2
    val unit = h / 100f
    // Il tubo: sale nei primi istanti, se ne va verso la fine.
    val tubeW = 18 * unit; val tubeH = 46 * unit
    val rise = 1f - easeOut(phase(t, 0f, 0.12f))
    val leave = phase(t, 0.70f, 0.85f)
    val tubeTop = h - 8 * unit - tubeH + (rise * 70 + leave * 60) * unit
    drawRoundRect(
        Brush.horizontalGradient(listOf(Color(0x1AFFFFFF), Color(0x47FFFFFF), Color(0x14FFFFFF), Color(0x33FFFFFF)), cx - tubeW / 2, cx + tubeW / 2),
        Offset(cx - tubeW / 2, tubeTop), Size(tubeW, tubeH), CornerRadius(4 * unit), alpha = 1f - leave,
    )
    drawRoundRect(Color(0x73FFFFFF), Offset(cx - tubeW / 2, tubeTop), Size(tubeW, tubeH), CornerRadius(4 * unit), style = Stroke(0.5f * unit), alpha = 1f - leave)
    // Il tappo: salta e vola via girando.
    val lid = phase(t, 0.16f, 0.34f)
    if (lid < 1f) {
        val lx = cx + lerp(0f, -34f, lid) * size.width / 100f
        val ly = tubeTop - 3.2f * unit + (if (lid < 0.55f) lerp(0f, -52f, easeOut(lid / 0.55f)) else lerp(-52f, -20f, (lid - 0.55f) / 0.45f)) * unit
        rotate(-420f * lid, Offset(lx, ly + 2 * unit)) {
            drawRoundRect(Color(0xFFC6F432), Offset(lx - tubeW / 2 - 1.5f * unit, ly), Size(tubeW + 3 * unit, 4.5f * unit), CornerRadius(1.6f * unit), alpha = 1f - phase(lid, 0.7f, 1f))
        }
    }
    // Le tre palle: escono dal tubo una dopo l'altra, volano, rimbalzano e si fermano in fila (staccate anche in verticale).
    val r = minOf(8 * unit, size.width * 0.12f)
    for ((k, x) in listOf(-32f, 0f, 32f).withIndex()) {
        val start = 0.18f + k * 0.033f
        val f = phase(t, start, start + 0.34f)
        if (f <= 0f) continue
        val dx = x * size.width / 100f
        val restY = h - 40 * unit - r - (-8 * unit)
        val (px, py) = when {
            f < 0.35f -> { val g = easeOut(f / 0.35f); lerp(0f, dx * 0.6f, g) to lerp(h - 40 * unit, h * 0.25f, g) }
            f < 0.65f -> { val g = (f - 0.35f) / 0.30f; lerp(dx * 0.6f, dx, g) to lerp(h * 0.25f, restY + 2 * unit, g * g) }
            f < 0.82f -> { val g = (f - 0.65f) / 0.17f; dx to restY + 2 * unit - kotlin.math.sin(g * Math.PI).toFloat() * 10 * unit }
            else -> dx to restY
        }
        val out = phase(t, 0.88f, 1f)
        drawBall(Offset(cx + px, py), r * (1f + 0.2f * out), 460f * f, 1f - out)
    }
}

private fun DrawScope.drawBall(c: Offset, r: Float, angle: Float, alpha: Float) {
    drawCircle(Color(0x80000000), r, c + Offset(0f, r * 0.25f), alpha = alpha * 0.5f)
    drawCircle(ballFill, r, c, alpha = alpha)
    // Le due cuciture bianche, come due archi dai lati.
    val clip = Path().apply { addOval(androidx.compose.ui.geometry.Rect(c, r)) }
    clipPath(clip) {
        rotate(angle, c) {
            val seam = Stroke(r * 0.11f)
            drawCircle(Color(0xEBFFFFFF), r, c + Offset(-r * 1.24f, 0f), style = seam, alpha = alpha)
            drawCircle(Color(0xEBFFFFFF), r, c + Offset(r * 1.24f, 0f), style = seam, alpha = alpha)
        }
    }
}
