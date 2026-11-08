// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.tennis.scoremanager.ui.Strings
import java.io.OutputStream

/**
 * Documento stampabile dei progressi (A4, più pagine se serve): per ogni voce la media della prima e della seconda
 * metà del periodo, la tendenza (verde se migliora, rossa se peggiora) e l'andamento; poi l'elenco completo.
 */
object ProgressPdf {
    private const val W = 595
    private const val H = 842
    private const val M = 40f
    private val dark = Color.rgb(20, 28, 44)
    private val grey = Color.rgb(110, 120, 135)
    private val rule = Color.rgb(220, 224, 230)
    private val green = Color.rgb(46, 125, 50)
    private val red = Color.rgb(205, 45, 42)

    private fun paint(size: Float, color: Int = dark, bold: Boolean = false, align: Paint.Align = Paint.Align.LEFT) = Paint().apply {
        textSize = size
        this.color = color
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        textAlign = align
        isAntiAlias = true
    }

    fun write(r: Progress.Report, s: Strings, footer: String, out: OutputStream) {
        val doc = PdfDocument()
        var pageNo = 0
        var page: PdfDocument.Page? = null
        var y = 0f
        fun newPage() {
            page?.let { doc.finishPage(it) }
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
            page!!.canvas.drawText("$footer · $pageNo", W / 2f, H - 20f, paint(8f, grey, align = Paint.Align.CENTER))
            y = M + 10f
        }
        fun ensure(h: Float) { if (y + h > H - 40f) newPage() }
        newPage()
        val c = { page!!.canvas }
        c().drawText(r.title, M, y, paint(20f, bold = true))
        y += 18f
        c().drawText("${r.subtitle} · ${s.progressCount(r.count)}", M, y, paint(10f, grey))
        y += 26f
        if (r.count < 2) {
            c().drawText(s.progressTooFew, M, y, paint(11f))
            y += 20f
        } else {
            // Intestazione delle voci
            val h = paint(9f, grey, bold = true)
            c().drawText(s.progressFirstHalf, 250f, y, h.apply { textAlign = Paint.Align.RIGHT })
            c().drawText(s.progressSecondHalf, 330f, y, paint(9f, grey, bold = true, align = Paint.Align.RIGHT))
            c().drawText(s.progressTrend, 345f, y, paint(9f, grey, bold = true))
            y += 8f
            c().drawLine(M, y, W - M, y, Paint().apply { color = rule })
            y += 16f
            for (m in r.metrics) {
                ensure(34f)
                val halves = m.halves
                c().drawText(m.label + if (m.unit.isNotEmpty()) " (${m.unit})" else "", M, y, paint(10f))
                if (halves != null) {
                    c().drawText(m.format(halves.first), 250f, y, paint(10f, align = Paint.Align.RIGHT))
                    c().drawText(m.format(halves.second), 330f, y, paint(10f, bold = true, align = Paint.Align.RIGHT))
                    val (txt, col) = when (m.verdict) {
                        1 -> "▲ ${s.progressBetter}" to green
                        -1 -> "▼ ${s.progressWorse}" to red
                        else -> "= ${s.progressSame}" to grey
                    }
                    c().drawText(txt, 345f, y, paint(10f, col, bold = m.verdict != 0))
                }
                sparkline(c(), m.values, 440f, y - 18f, W - M - 440f, 22f)
                y += 30f
            }
        }
        // Elenco completo
        y += 10f
        ensure(40f)
        c().drawText(s.progressList, M, y, paint(13f, bold = true))
        y += 18f
        val cols = r.header.size
        val colW = (W - 2 * M) / cols
        fun row(cells: List<String>, bold: Boolean) {
            ensure(16f)
            cells.forEachIndexed { i, t -> c().drawText(t, M + i * colW, y, paint(9f, if (bold) grey else dark, bold)) }
            y += 14f
        }
        row(r.header, true)
        for (cells in r.rows) row(cells, false)
        page?.let { doc.finishPage(it) }
        doc.writeTo(out)
        doc.close()
    }

    /** Andamento di una voce: punti uniti in ordine di tempo, i buchi saltati. */
    private fun sparkline(c: android.graphics.Canvas, values: List<Double?>, x0: Float, y0: Float, w: Float, h: Float) {
        val known = values.filterNotNull()
        if (known.size < 2) return
        val lo = known.min()
        val hi = known.max().let { if (it == lo) lo + 1 else it }
        val n = (values.size - 1).coerceAtLeast(1)
        val path = Path()
        var started = false
        values.forEachIndexed { i, v ->
            v ?: return@forEachIndexed
            val x = x0 + i.toFloat() / n * w
            val y = y0 + h - ((v - lo) / (hi - lo)).toFloat() * h
            if (!started) path.moveTo(x, y) else path.lineTo(x, y)
            started = true
        }
        c.drawPath(path, Paint().apply { color = Color.rgb(60, 110, 200); strokeWidth = 1.4f; style = Paint.Style.STROKE; isAntiAlias = true })
    }
}
