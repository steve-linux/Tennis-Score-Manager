// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.tennis.scoremanager.model.Lang
import com.tennis.scoremanager.ui.Strings
import java.util.Calendar

/**
 * Il mio anno: le partite finite di chi usa l'app in un anno, in breve. Quante, quante vinte, le ore in campo, il
 * rivale (l'avversario incontrato più spesso) col bilancio, le partite mese per mese, la serie di vittorie più lunga,
 * gli ace (dalle partite con le statistiche dettagliate) e gli allenamenti.
 */
object YearReview {
    data class Year(
        val year: Int, val played: Int, val won: Int, val minutes: Long, val rival: String?, val rivalWon: Int, val rivalLost: Int,
        val months: List<Int>, val streak: Int, val aces: Int?, val workouts: Int,
    ) {
        val winPct: Int get() = if (played > 0) Math.round(won * 100.0 / played).toInt() else 0
        /** Il mese (0-11) con più partite; null senza partite. */
        val bestMonth: Int? get() = months.withIndex().filter { it.value > 0 }.maxByOrNull { it.value }?.index
    }

    private fun yearOf(t: Long): Int = Calendar.getInstance().apply { timeInMillis = t }.get(Calendar.YEAR)
    private fun monthOf(t: Long): Int = Calendar.getInstance().apply { timeInMillis = t }.get(Calendar.MONTH)
    private fun at(l: PlayerStats.MatchLine): Long = l.rec.startedAt ?: l.rec.endedAt ?: l.rec.updatedAt

    /** Gli anni con almeno una partita, dal più recente. */
    fun years(lines: List<PlayerStats.MatchLine>): List<Int> = lines.map { yearOf(at(it)) }.distinct().sortedDescending()

    /** [lines]: le partite di [key] (chi usa l'app), dalla più vecchia; [workoutStarts]: quando sono iniziati gli allenamenti. */
    fun of(year: Int, lines: List<PlayerStats.MatchLine>, key: String, workoutStarts: List<Long>): Year {
        val mine = lines.filter { yearOf(at(it)) == year }
        // Il rivale: chi si è incontrato più volte (nel doppio, la coppia).
        val vs = mine.mapNotNull { l ->
            val side = PlayerStats.sideOf(l.rec, key) ?: return@mapNotNull null
            PlayerStats.names(l.rec.setup, side.other).joinToString(" / ").takeIf { it.isNotBlank() }?.let { it to l.won }
        }
        val rival = vs.groupBy { it.first }.maxWithOrNull(compareBy({ it.value.size }, { -it.value.count { w -> !w.second } }))
        var best = 0; var run = 0
        for (l in mine) { run = if (l.won) run + 1 else 0; best = maxOf(best, run) }
        val months = MutableList(12) { 0 }
        mine.forEach { months[monthOf(at(it))]++ }
        val withDetail = mine.mapNotNull { it.detail }
        return Year(
            year, mine.size, mine.count { it.won }, mine.sumOf { it.rec.clockMs } / 60_000L,
            rival?.key?.takeIf { rival.value.size >= 2 }, rival?.value?.count { it.second } ?: 0, rival?.value?.count { !it.second } ?: 0,
            months, best, withDetail.takeIf { it.isNotEmpty() }?.sumOf { it.aces }, workoutStarts.count { yearOf(it) == year },
        )
    }
}

/** L'immagine del mio anno per WhatsApp e i social (1080×1350), coi colori dell'app. */
object YearReviewImage {
    private val bg = Color.rgb(13, 20, 33)
    private val surface = Color.rgb(25, 35, 54)
    private val ball = Color.rgb(198, 244, 50)
    private val main = Color.WHITE
    private val dim = Color.rgb(150, 162, 180)

    fun render(y: YearReview.Year, name: String, s: Strings, lang: Lang): Bitmap {
        val w = 1080; val h = 1350
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(bg)
        fun p(size: Float, color: Int, bold: Boolean = false, align: Paint.Align = Paint.Align.LEFT) = Paint().apply {
            textSize = size; this.color = color; typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT; isAntiAlias = true; textAlign = align
        }
        fun box(r: RectF) = c.drawRoundRect(r, 32f, 32f, Paint().apply { color = surface; isAntiAlias = true })
        c.drawText(s.yrOf(y.year), 70f, 130f, p(84f, main, true))
        if (name.isNotBlank()) c.drawText(name, 70f, 195f, p(40f, dim))
        if (y.played == 0) {
            c.drawText(s.yrNone, 70f, 320f, p(40f, dim))
            c.drawText(s.generatedWith, w / 2f, h - 50f, p(30f, dim, align = Paint.Align.CENTER))
            return bmp
        }
        // Tre numeri grandi: partite, vinte, ore in campo.
        val tiles = listOf(y.played.toString() to s.yrMatches, "${y.winPct}%" to s.yrWon, String.format(lang.locale, if (y.minutes < 600) "%.1f" else "%.0f", y.minutes / 60.0) to s.yrHours)
        val tw = (w - 120f - 2 * 30f) / 3
        tiles.forEachIndexed { i, (big, label) ->
            val x = 60f + i * (tw + 30f)
            box(RectF(x, 250f, x + tw, 470f))
            c.drawText(big, x + tw / 2, 375f, p(96f, ball, true, Paint.Align.CENTER))
            c.drawText(label, x + tw / 2, 435f, p(32f, dim, align = Paint.Align.CENTER))
        }
        var top = 510f
        // Il rivale col bilancio.
        y.rival?.let { r ->
            box(RectF(60f, top, w - 60f, top + 170f))
            c.drawText(s.yrRival, 100f, top + 60f, p(32f, dim))
            c.drawText(r.take(28), 100f, top + 125f, p(52f, main, true))
            c.drawText("${y.rivalWon}–${y.rivalLost}", w - 100f, top + 120f, p(80f, ball, true, Paint.Align.RIGHT))
            top += 200f
        }
        // Le partite mese per mese.
        box(RectF(60f, top, w - 60f, top + 330f))
        val max = (y.months.maxOrNull() ?: 0).coerceAtLeast(1)
        val bw = (w - 200f) / 12
        val initials = java.text.DateFormatSymbols(lang.locale).shortMonths
        for (m in 0 until 12) {
            val x = 100f + m * bw
            val bh = 200f * y.months[m] / max
            c.drawRoundRect(RectF(x + 8f, top + 250f - bh, x + bw - 8f, top + 250f), 10f, 10f, Paint().apply { color = if (y.months[m] == max) ball else dim; isAntiAlias = true })
            c.drawText(initials[m].take(1).uppercase(lang.locale), x + bw / 2, top + 300f, p(28f, dim, align = Paint.Align.CENTER))
        }
        top += 360f
        // In fondo, in righe: il mese con più partite, la serie, gli ace, gli allenamenti.
        val months = java.text.DateFormatSymbols(lang.locale).months
        val lines = listOfNotNull(
            y.bestMonth?.let { s.yrBestMonth(months[it].replaceFirstChar { ch -> ch.titlecase(lang.locale) }) },
            y.streak.takeIf { it >= 2 }?.let { s.yrStreak(it) },
            y.aces?.takeIf { it > 0 }?.let { s.yrAces(it) },
            y.workouts.takeIf { it > 0 }?.let { s.yrWorkouts(it) },
        )
        for (l in lines) {
            if (top > h - 130f) break
            c.drawText(l, 70f, top + 40f, p(38f, main))
            top += 62f
        }
        c.drawText(s.generatedWith, w / 2f, h - 50f, p(30f, dim, align = Paint.Align.CENTER))
        return bmp
    }
}
