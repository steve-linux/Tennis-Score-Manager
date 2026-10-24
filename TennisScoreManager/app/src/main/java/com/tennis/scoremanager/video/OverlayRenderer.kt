// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.video

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface

/**
 * Disegna la grafica del punteggio come nelle dirette dei tornei: due righe scure con nome, set finiti, game del
 * set in corso e, nel riquadro colorato, i punti del game; la pallina gialla accanto a chi serve; sotto, quando c'è,
 * la scritta di pressione (BREAK POINT...). Stile nostro, senza loghi né caratteri di altri.
 * Le misure sono pensate per un video 1920×1080: [scale] le adatta ad altre risoluzioni.
 */
object OverlayRenderer {
    private val BG = Color.argb(235, 12, 20, 36)
    private val BG_ALT = Color.argb(235, 22, 32, 52)
    private val SET_TXT = Color.rgb(170, 182, 200)
    private val ACCENT = Color.rgb(0xC6, 0xF4, 0x32)
    private val ON_ACCENT = Color.rgb(12, 20, 36)
    private val BALL = Color.rgb(0xE8, 0xF5, 0x4A)
    private val WIN = Color.rgb(0xC6, 0xF4, 0x32)

    fun render(o: ScoreOverlay, scale: Float = 1f): Bitmap {
        val rowH = 56f * scale
        val pad = 18f * scale
        val cellW = 46f * scale
        val pointW = 74f * scale
        val ballW = 28f * scale
        val name = Paint().apply { isAntiAlias = true; color = Color.WHITE; textSize = 30f * scale; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) }
        val num = Paint(name).apply { textAlign = Paint.Align.CENTER }
        val small = Paint(num).apply { textSize = 17f * scale }
        val titleP = Paint(name).apply { textSize = 22f * scale; color = SET_TXT; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL) }
        val bannerP = Paint(name).apply { textSize = 24f * scale; color = ON_ACCENT; letterSpacing = 0.08f }

        val nameW = (o.names.maxOfOrNull { name.measureText(it) } ?: 0f).coerceIn(160f * scale, 460f * scale)
        val setCols = o.sets.size + if (o.games.isNotEmpty()) 1 else 0
        val hasPoints = o.points.isNotEmpty()
        val width = ballW + nameW + pad + setCols * cellW + (if (hasPoints) pointW else 0f) + pad / 2
        val titleH = if (o.title.isNotEmpty()) 34f * scale else 0f
        val bannerText = o.banner?.uppercase()
        val bannerH = if (bannerText != null) 40f * scale else 0f
        val height = titleH + 2 * rowH + bannerH
        val bmp = Bitmap.createBitmap(width.toInt().coerceAtLeast(1), height.toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val fill = Paint().apply { isAntiAlias = true }

        if (titleH > 0f) {
            fill.color = BG
            c.drawRoundRect(RectF(0f, 0f, width, titleH + 8f * scale), 8f * scale, 8f * scale, fill)
            c.drawText(o.title, pad, titleH - 10f * scale, titleP)
        }
        for (row in 0..1) {
            val top = titleH + row * rowH
            fill.color = if (row == 0) BG else BG_ALT
            c.drawRect(0f, top, width, top + rowH, fill)
            val base = top + rowH / 2 - (name.descent() + name.ascent()) / 2
            // Pallina di chi serve (o spunta del vincitore a fine partita).
            if (o.server == row) {
                fill.color = BALL
                c.drawCircle(ballW / 2 + 4f * scale, top + rowH / 2, 8f * scale, fill)
            }
            val winner = o.winner == row
            name.color = if (o.winner >= 0 && !winner) SET_TXT else Color.WHITE
            c.drawText(o.names.getOrElse(row) { "" }, ballW + 4f * scale, base, name)
            var x = ballW + nameW + pad
            o.sets.forEachIndexed { i, s ->
                val mine = s[row]
                val won = mine > s[1 - row]
                num.color = if (won) Color.WHITE else SET_TXT
                c.drawText("$mine", x + cellW / 2, base, num)
                // Tie-break: i punti del perdente in piccolo, accanto ai game di chi l'ha perso.
                val tb = o.setTiebreaks.getOrNull(i)
                if (tb != null && !won) c.drawText("$tb", x + cellW - 6f * scale, top + 20f * scale, small.apply { color = SET_TXT })
                x += cellW
            }
            if (o.games.isNotEmpty()) {
                num.color = Color.WHITE
                c.drawText("${o.games[row]}", x + cellW / 2, base, num)
                x += cellW
            }
            if (hasPoints) {
                fill.color = ACCENT
                c.drawRect(x, top, x + pointW, top + rowH, fill)
                num.color = ON_ACCENT
                c.drawText(o.points[row], x + pointW / 2, base, num)
            }
            if (winner) {
                fill.color = WIN
                c.drawRect(0f, top, 6f * scale, top + rowH, fill)
            }
        }
        if (bannerText != null) {
            val top = titleH + 2 * rowH
            fill.color = ACCENT
            c.drawRect(0f, top, width, top + bannerH, fill)
            c.drawText(bannerText, pad, top + bannerH / 2 - (bannerP.descent() + bannerP.ascent()) / 2, bannerP)
        }
        return bmp
    }
}
