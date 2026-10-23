// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.tennis.scoremanager.model.Lang
import com.tennis.scoremanager.ui.Strings
import java.io.OutputStream

/** Righe di riepilogo di un allenamento, uguali a schermo e nel PDF. */
object WorkoutReport {

    fun summaryLines(w: WorkoutRecord, s: Strings): List<Pair<String, String>> {
        val sum = HeartStats.summary(w.heart)
        return listOfNotNull(
            s.workoutDuration to Reports.duration(w.durationS * 1000L),
            sum?.let { "${s.heartAvg}" to "${it.avg} bpm" },
            sum?.let { "${s.heartMax}" to "${it.max} bpm" },
            sum?.kcal?.let { s.heartCalories to "$it kcal" },
            WorkoutStats.rmssd(w.rr)?.let { s.workoutHrv to "${Math.round(it)} ms" },
            WorkoutStats.finalRecovery(w)?.let { s.workoutRecovery to "−$it bpm" },
        )
    }

    fun lapLine(l: WorkoutStats.Lap, s: Strings): String = listOfNotNull(
        "${s.workoutLap} ${l.number}",
        Reports.duration(l.durationS * 1000L),
        l.avg?.let { "${s.workoutAvgShort} $it" },
        l.max?.let { "${s.workoutMaxShort} $it" },
        l.recovery?.let { "${s.workoutRecShort} −$it" },
    ).joinToString(" · ")

    /** PDF di una pagina A4: riepilogo, grafico del battito, zone e giri. */
    fun pdf(w: WorkoutRecord, s: Strings, lang: Lang, out: OutputStream) {
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
        val c = page.canvas
        val title = Paint().apply { textSize = 22f; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
        val head = Paint().apply { textSize = 14f; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
        val text = Paint().apply { textSize = 11f; isAntiAlias = true }
        val dim = Paint(text).apply { color = Color.DKGRAY }
        var y = 50f
        c.drawText(s.workoutTitle, 40f, y, title)
        y += 20f
        c.drawText("${Reports.date(w.startedAt, lang)} ${Reports.time(w.startedAt, lang)}" + (w.strapName?.let { " · $it" } ?: ""), 40f, y, dim)
        y += 28f
        for ((k, v) in summaryLines(w, s)) {
            c.drawText(k, 40f, y, text)
            c.drawText(v, 300f, y, text)
            y += 16f
        }
        y += 14f
        if (w.heart.size > 1) {
            c.drawText(s.workoutChart, 40f, y, head)
            y += 10f
            chart(c, w.heart, 40f, y, 515f, 160f)
            y += 180f
        }
        val age = w.heart.profile?.age
        if (age != null) {
            val z = HeartStats.zoneSeconds(w.heart, age)
            if (z.sum() > 0) {
                c.drawText(s.heartZones, 40f, y, head)
                y += 12f
                // La stessa torta del riepilogo della partita: a sinistra, con la legenda a destra.
                val ring = 18f
                val box = RectF(40f + ring / 2, y + ring / 2, 160f - ring / 2, y + 120f - ring / 2)
                val arc = Paint().apply { style = Paint.Style.STROKE; strokeWidth = ring; isAntiAlias = true }
                var start = -90f
                for (i in z.indices) {
                    val sweep = z[i] * 360f / z.sum()
                    if (sweep > 0f) c.drawArc(box, start, sweep, false, arc.apply { color = ZONE_COLORS[i] })
                    start += sweep
                }
                var ly = y + 22f
                val swatch = Paint()
                for (i in z.indices) {
                    c.drawRect(190f, ly - 9f, 200f, ly + 1f, swatch.apply { color = ZONE_COLORS[i] })
                    c.drawText(s.zoneName(i), 210f, ly, text)
                    c.drawText("${Math.round(z[i] * 100.0 / z.sum())}% · ${Reports.duration(z[i] * 1000L)}", 420f, ly, text)
                    ly += 20f
                }
                y += 135f
            }
        }
        val laps = WorkoutStats.laps(w)
        if (laps.size > 1) {
            c.drawText(s.workoutLaps, 40f, y, head)
            y += 18f
            for (l in laps) {
                if (y > 810f) break
                c.drawText(lapLine(l, s), 40f, y, text)
                y += 15f
            }
        }
        doc.finishPage(page)
        doc.writeTo(out)
        doc.close()
    }

    /** Dalla zona 1 alla 5, come nel riepilogo della partita (MatchStatsPanels). */
    private val ZONE_COLORS = intArrayOf(0xFF9AA8BD.toInt(), 0xFF4CAF50.toInt(), 0xFFC6F432.toInt(), 0xFFFF9800.toInt(), 0xFFFF3B30.toInt())

    private fun chart(c: Canvas, t: HeartTrack, x0: Float, y0: Float, w: Float, h: Float) {
        val values = t.bpm.take(t.size)
        val lo = (values.min() - 5).coerceAtLeast(30)
        val hi = values.max() + 5
        val end = t.t[t.size - 1].coerceAtLeast(1)
        val axis = Paint().apply { color = Color.LTGRAY; strokeWidth = 1f }
        val label = Paint().apply { textSize = 9f; color = Color.DKGRAY; isAntiAlias = true }
        val line = Paint().apply { color = Color.rgb(0xE5, 0x39, 0x35); strokeWidth = 1.6f; style = Paint.Style.STROKE; isAntiAlias = true }
        c.drawLine(x0, y0 + h, x0 + w, y0 + h, axis)
        c.drawText("$hi", x0 - 2f, y0 + 8f, label.apply { textAlign = Paint.Align.RIGHT })
        c.drawText("$lo", x0 - 2f, y0 + h, label)
        val path = Path()
        for (i in 0 until t.size) {
            val x = x0 + t.t[i].toFloat() / end * w
            val y = y0 + h - (t.bpm[i] - lo).toFloat() / (hi - lo) * h
            // Una pausa o una fascia staccata interrompono la linea.
            if (i == 0 || t.t[i] - t.t[i - 1] > 5) path.moveTo(x, y) else path.lineTo(x, y)
        }
        c.drawPath(path, line)
    }
}
