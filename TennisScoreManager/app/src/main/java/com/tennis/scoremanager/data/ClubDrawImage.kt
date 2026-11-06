// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.tennis.scoremanager.ui.Strings
import com.tennis.scoremanager.ui.ctRoundName
import com.tennis.scoremanager.ui.groupLetter

/**
 * Il tabellone di un torneo in un'immagine da mandare al gruppo o sui social: in alto nome e circolo, poi i gironi
 * con la classifica e il tabellone, un turno per colonna, con i vincitori in evidenza.
 */
object ClubDrawImage {
    private const val COL_W = 380f
    private const val BOX_W = 340f
    private const val BOX_H = 112f
    private const val GAP = 18f
    private const val M = 40f

    fun render(t: ClubTournament, name: (String?) -> String, s: Strings, subtitle: String, footer: String): Bitmap {
        val groups = ClubTournaments.groupMembers(t)
        val rounds = t.rounds
        val first = t.matches.count { it.round == 1 }
        val groupRows = groups.sumOf { it.size + 2 }
        val drawH = if (rounds > 0) 70f + first * (BOX_H + GAP) else 0f
        val w = maxOf(1080f, M * 2 + rounds * COL_W).toInt()
        val h = (190f + groupRows * 46f + groups.size * 20f + drawH + 90f).toInt().coerceAtMost(8000)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val bg = 0xFF0B1220.toInt(); val surface = 0xFF151D2E.toInt(); val main = 0xFFF1F5FB.toInt(); val dim = 0xFF9AA8BD.toInt(); val ball = 0xFFC6F432.toInt()
        c.drawColor(bg)
        fun p(size: Float, color: Int, bold: Boolean = false, align: Paint.Align = Paint.Align.LEFT) = Paint().apply {
            isAntiAlias = true; textSize = size; this.color = color; textAlign = align
            typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
        }
        fun fit(text: String, paint: Paint, max: Float): String {
            if (paint.measureText(text) <= max) return text
            var t2 = text
            while (t2.isNotEmpty() && paint.measureText("$t2…") > max) t2 = t2.dropLast(1)
            return "$t2…"
        }
        var y = 90f
        c.drawText(fit(t.name, p(56f, main, true), w - 2 * M), M, y, p(56f, main, true))
        y += 50f
        c.drawText(fit(subtitle, p(30f, dim), w - 2 * M), M, y, p(30f, dim))
        y += 50f
        // Gironi: posizione, nome, vittorie su partite giocate; in giallo chi passa.
        for ((g, _) in groups.withIndex()) {
            y += 20f
            c.drawText(s.ctGroup(groupLetter(g)), M, y + 34f, p(32f, ball, true))
            y += 46f
            for ((i, st) in ClubTournaments.standings(t, g).withIndex()) {
                val q = i < t.qualify
                c.drawText(fit("${i + 1}. ${name(st.entry)}", p(30f, if (q) ball else main, q), 700f), M, y + 34f, p(30f, if (q) ball else main, q))
                c.drawText("${st.won}/${st.played}   ${"%+d".format(st.setsWon - st.setsLost)}", w - M, y + 34f, p(30f, dim, align = Paint.Align.RIGHT))
                y += 46f
            }
            y += 46f
        }
        if (rounds > 0) {
            val top = y + 50f
            for (r in 1..rounds) {
                val ms = t.matches.filter { it.round == r }.sortedBy { it.slot }
                val x = M + (r - 1) * COL_W
                c.drawText(ctRoundName(s, t, ms.first()), x, top - 14f, p(28f, dim, true))
                // Ogni partita a metà tra le due da cui viene.
                val step = (BOX_H + GAP) * (1 shl (r - 1))
                for ((i, m) in ms.withIndex()) {
                    val by = top + i * step + (step - BOX_H - GAP) / 2
                    c.drawRoundRect(RectF(x, by, x + BOX_W, by + BOX_H), 18f, 18f, Paint().apply { color = surface; isAntiAlias = true })
                    for ((k, e) in listOf(m.a, m.b).withIndex()) {
                        val win = m.winner != null && m.winner == e
                        val label = e?.let(name) ?: if (m.bye) s.ctBye else "—"
                        c.drawText(fit(label, p(28f, if (win) ball else main, win), BOX_W - 30f), x + 16f, by + 40f + k * 36f, p(28f, if (win) ball else main, win))
                    }
                    val extra = listOfNotNull(m.score.ifBlank { null }, s.ctWalkover.takeIf { m.walkover }, s.ctRetired.takeIf { m.retired }, s.ctDisqualified.takeIf { m.disqualified }, s.ctNoShow.takeIf { m.noShow }).joinToString(" ")
                    if (extra.isNotBlank()) c.drawText(fit(extra, p(22f, dim), BOX_W - 30f), x + 16f, by + BOX_H - 10f, p(22f, dim))
                }
            }
        }
        c.drawText(footer, w / 2f, h - 36f, p(26f, dim, align = Paint.Align.CENTER))
        return bmp
    }
}
