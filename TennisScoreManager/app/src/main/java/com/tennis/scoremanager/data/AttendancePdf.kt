// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.tennis.scoremanager.model.Lang
import com.tennis.scoremanager.ui.Strings
import java.io.OutputStream

/**
 * Il registro presenze di un corso su fogli A4 orizzontali: gli allievi sulle righe, le lezioni già passate sulle
 * colonne (✓ presente, ✗ assente, — annullata, vuoto da segnare) e in fondo il totale di ciascuno. Con tante lezioni o
 * tanti allievi continua sui fogli dopo.
 */
object AttendancePdf {
    private const val W = 842
    private const val H = 595
    private const val M = 32f
    private const val NAME_W = 170f
    private const val TOT_W = 60f
    private const val COLS = 18
    private const val ROWS = 24

    /** Il segno di una casella: ✓, ✗, — o vuoto. */
    fun mark(c: Course, key: String, student: String): String = when {
        key in c.cancelled -> "—"
        key !in c.attendance -> ""
        student in c.attendance[key].orEmpty() -> "✓"
        else -> "✗"
    }

    fun write(c: Course, names: (String) -> String, header: String, s: Strings, lang: Lang, now: Long, out: OutputStream) {
        val doc = PdfDocument()
        val lessons = Courses.lessons(c).filter { it.start <= now }
        val students = c.students.sortedBy { names(it).lowercase() }
        fun paint(size: Float, color: Int = 0xFF111827.toInt(), bold: Boolean = false, align: Paint.Align = Paint.Align.LEFT) = Paint().apply {
            isAntiAlias = true; textSize = size; this.color = color; textAlign = align
            typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
        }
        val grey = 0xFF6B7280.toInt()
        val rule = Paint().apply { color = 0xFFD1D5DB.toInt(); strokeWidth = 0.8f }
        val colChunks = lessons.chunked(COLS).ifEmpty { listOf(emptyList()) }
        val rowChunks = students.chunked(ROWS).ifEmpty { listOf(emptyList()) }
        var pageNo = 0
        for (cols in colChunks) for (rows in rowChunks) {
            pageNo++
            val page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
            val cv = page.canvas
            var y = M + 16f
            cv.drawText("${s.rgTitle} · ${c.name}", M, y, paint(16f, bold = true))
            cv.drawText(header, W - M, y, paint(10f, grey, align = Paint.Align.RIGHT))
            y += 16f
            cv.drawText(listOf(c.level, "${Reports.date(c.from, lang)} – ${Reports.date(c.to, lang)}").filter { it.isNotBlank() }.joinToString(" · "), M, y, paint(9.5f, grey))
            y += 22f
            val cw = if (cols.isEmpty()) 0f else minOf(34f, (W - 2 * M - NAME_W - TOT_W) / cols.size)
            // Intestazione: il giorno di ogni lezione.
            for ((i, l) in cols.withIndex()) {
                val x = M + NAME_W + i * cw + cw / 2
                val cal = java.util.Calendar.getInstance().apply { timeInMillis = l.start }
                cv.drawText("%02d/%02d".format(cal.get(java.util.Calendar.DAY_OF_MONTH), cal.get(java.util.Calendar.MONTH) + 1), x, y, paint(7.5f, grey, align = Paint.Align.CENTER))
            }
            cv.drawText(s.rgTotal, W - M - TOT_W / 2, y, paint(8f, grey, bold = true, align = Paint.Align.CENTER))
            y += 6f
            cv.drawLine(M, y, W - M, y, rule)
            for (sid in rows) {
                y += 18f
                cv.drawText(names(sid).take(28), M, y, paint(10f))
                for ((i, l) in cols.withIndex()) {
                    val m = mark(c, l.key, sid)
                    cv.drawText(m, M + NAME_W + i * cw + cw / 2, y, paint(10f, if (m == "✗") 0xFFDC2626.toInt() else if (m == "✓") 0xFF15803D.toInt() else grey, bold = true, align = Paint.Align.CENTER))
                }
                val (pres, tot) = Courses.attendanceOf(c, sid, now)
                cv.drawText("$pres/$tot", W - M - TOT_W / 2, y, paint(10f, bold = true, align = Paint.Align.CENTER))
                y += 5f
                cv.drawLine(M, y, W - M, y, rule)
            }
            cv.drawText(s.rgLegend, M, H - M - 12f, paint(8.5f, grey))
            cv.drawText("${s.generatedWith} · ${Reports.date(now, lang)}" + if (colChunks.size * rowChunks.size > 1) " · $pageNo/${colChunks.size * rowChunks.size}" else "",
                M, H - M, paint(8f, grey))
            doc.finishPage(page)
        }
        doc.writeTo(out)
        doc.close()
    }
}
