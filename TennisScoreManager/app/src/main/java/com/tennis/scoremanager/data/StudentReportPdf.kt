// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.tennis.scoremanager.model.Lang
import com.tennis.scoremanager.ui.Strings
import com.tennis.scoremanager.ui.strokeLabel
import java.io.OutputStream

/** Il report dell'allievo in PDF (A4, più pagine se serve), coi colori di stampa. */
object StudentReportPdf {
    private const val W = 595
    private const val H = 842
    private const val M = 40f
    private val dark = Color.rgb(20, 28, 44)
    private val grey = Color.rgb(110, 120, 135)
    private val rule = Color.rgb(220, 224, 230)
    private val accent = Color.rgb(98, 140, 20)
    private val green = Color.rgb(46, 125, 50)
    private val red = Color.rgb(205, 45, 42)

    private fun paint(size: Float, color: Int = dark, bold: Boolean = false, align: Paint.Align = Paint.Align.LEFT) = Paint().apply {
        textSize = size; this.color = color; typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT; textAlign = align; isAntiAlias = true
    }

    /** "dal 1 ottobre al 31 ottobre" o "tutto"; con l'allenatore e il circolo. */
    fun period(r: StudentReport.Report, s: Strings, lang: Lang): String = listOfNotNull(
        r.from?.let { s.reportPeriod(Reports.date(it, lang), Reports.date(r.to, lang)) } ?: s.reportAll,
        r.coach?.takeIf { it.isNotBlank() }?.let { s.reportCoach(it) }, r.club?.takeIf { it.isNotBlank() },
    ).joinToString(" · ")

    fun write(r: StudentReport.Report, s: Strings, lang: Lang, footer: String, out: OutputStream) {
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
        val c = { page!!.canvas }
        fun section(title: String) {
            ensure(40f)
            y += 10f
            c().drawText(title, M, y, paint(14f, accent, bold = true))
            y += 6f
            c().drawLine(M, y, W - M, y, Paint().apply { color = rule })
            y += 16f
        }
        fun wrap(text: String, p: Paint, width: Float, lineH: Float) {
            for (para in text.split('\n')) {
                var line = ""
                for (w in para.split(' ')) {
                    val t = if (line.isEmpty()) w else "$line $w"
                    if (p.measureText(t) > width && line.isNotEmpty()) { ensure(lineH); c().drawText(line, M, y, p); y += lineH; line = w } else line = t
                }
                ensure(lineH); c().drawText(line, M, y, p); y += lineH
            }
        }
        newPage()
        c().drawText(r.name, M, y, paint(22f, bold = true))
        y += 18f
        if (r.fullName.isNotBlank() && r.fullName != r.name) { c().drawText(r.fullName, M, y, paint(11f, grey)); y += 16f }
        c().drawText(period(r, s, lang), M, y, paint(10f, grey))
        y += 14f
        if (r.empty) { y += 10f; c().drawText(s.reportEmpty, M, y, paint(11f)); y += 16f }

        if (r.strokes.isNotEmpty()) {
            section(s.studentStrokes)
            for (b in r.strokes) {
                ensure(70f)
                c().drawText(StudentReport.strokeLine(s, b), M, y, paint(11f, bold = true))
                // Andamento: la percentuale dentro di ogni sessione, da sinistra (la più vecchia) a destra.
                if (b.trend.size >= 2) sparkline(c(), b.trend, RectF(W - M - 150f, y - 12f, W - M, y + 18f))
                y += if (b.trend.size >= 2) 26f else 15f
                for (row in b.rows.chunked(2)) {
                    ensure(14f)
                    row.forEachIndexed { i, (label, n) -> c().drawText("$label: ${s.serveCountLine(n.inPlay, n.total)}" + (n.pct?.let { " · $it%" } ?: ""), M + i * 250f, y, paint(9.5f)) }
                    y += 13f
                }
                y += 10f
            }
        }
        if (r.movement.isNotEmpty()) {
            section(s.studentMovement)
            for ((t, rows) in r.movement) {
                ensure(20f)
                c().drawText(strokeLabel(s, t), M, y, paint(11f, bold = true))
                y += 15f
                for (ch in rows) {
                    ensure(13f)
                    val fmt = { v: Float? -> v?.let { if (ch.unit == "°") "%.0f°".format(it) else if (ch.unit == "ms") "%.0f ms".format(it) else "%.2f".format(it) } ?: "—" }
                    c().drawText(ch.label, M, y, paint(9.5f, grey))
                    c().drawText(if (ch.before != null) "${fmt(ch.before)} → ${fmt(ch.now)}" else fmt(ch.now), W - M, y, paint(9.5f, bold = true, align = Paint.Align.RIGHT))
                    y += 13f
                }
                y += 6f
            }
        }
        if (r.examples.isNotEmpty()) {
            section(s.reportExamples)
            val gap = 8f
            val iw = (W - 2 * M - 2 * gap) / 3
            for (e in r.examples) {
                val bmps = e.images.map { (label, f) -> label to runCatching { decodeSmall(f, (iw * 2).toInt()) }.getOrNull() }
                val ratio = bmps.firstNotNullOfOrNull { it.second }?.let { it.height.toFloat() / it.width } ?: 0.56f
                val ih = iw * ratio
                ensure(ih + 40f)
                c().drawText("${strokeLabel(s, e.stroke)} · ${if (e.inPlay) s.reportShotIn else s.reportShotOut} · ${Reports.date(e.at, lang)}", M, y, paint(10f, if (e.inPlay) green else red, bold = true))
                y += 8f
                bmps.forEachIndexed { i, (label, b) ->
                    val x = M + i * (iw + gap)
                    if (b != null) {
                        // Ridotte prima di metterle nel PDF (non comprime le immagini).
                        val small = Bitmap.createScaledBitmap(b, (iw * 2).toInt(), (ih * 2).toInt(), true)
                        c().drawBitmap(small, null, RectF(x, y, x + iw, y + ih), Paint(Paint.FILTER_BITMAP_FLAG))
                        if (small !== b) small.recycle()
                        b.recycle()
                    }
                    c().drawText(label, x, y + ih + 11f, paint(8.5f, grey))
                }
                y += ih + 24f
            }
        }
        if (r.matches.isNotEmpty()) {
            section(s.studentMatches)
            c().drawText(s.studentWonLost(r.won, r.lost) + (r.rating?.let { " · ${s.ratingTitle}: $it" } ?: ""), M, y, paint(11f, bold = true))
            y += 16f
            for (m in r.matches) {
                ensure(13f)
                c().drawText("${Reports.date(m.at, lang)} · ${m.opponents}", M, y, paint(9.5f))
                c().drawText((if (m.won) s.studentWon else s.studentLost) + " · ${m.pointsPct}%", W - M, y, paint(9.5f, if (m.won) green else red, bold = true, align = Paint.Align.RIGHT))
                y += 13f
            }
        }
        if (r.note.isNotBlank()) {
            section(s.studentNotes)
            wrap(r.note, paint(10.5f), W - 2 * M, 14f)
        }
        page?.let { doc.finishPage(it) }
        doc.writeTo(out)
        doc.close()
    }

    /** La percentuale dentro sessione per sessione come una linea nel riquadro [box] (scala sui valori, almeno 20 punti). */
    /** L'immagine ridotta già in lettura (le clip possono essere grandi): almeno [minWidth] pixel di larghezza. */
    internal fun decodeSmall(f: java.io.File, minWidth: Int): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.absolutePath, o)
        var sample = 1
        while (o.outWidth / (sample * 2) >= minWidth) sample *= 2
        return BitmapFactory.decodeFile(f.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    fun sparkline(c: Canvas, values: List<Int>, box: RectF, line: Int = accent, guide: Int = rule) {
        val lo = minOf(values.min(), values.max() - 20).coerceAtLeast(0).toFloat()
        val hi = maxOf(values.max(), values.min() + 20).coerceAtMost(100).toFloat().coerceAtLeast(lo + 1)
        c.drawLine(box.left, box.bottom, box.right, box.bottom, Paint().apply { color = guide })
        val dx = box.width() / (values.size - 1)
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = box.left + i * dx
            val yy = box.bottom - box.height() * (v - lo) / (hi - lo)
            if (i == 0) path.moveTo(x, yy) else path.lineTo(x, yy)
        }
        c.drawPath(path, Paint().apply { color = line; style = Paint.Style.STROKE; strokeWidth = 2f; isAntiAlias = true })
    }
}

/** L'immagine del report per WhatsApp e i social (1080×1350): nome, colpi coi numeri e l'andamento, un'immagine dell'impatto. */
object StudentReportImage {
    private val bg = Color.rgb(13, 20, 33)
    private val surface = Color.rgb(25, 35, 54)
    private val ball = Color.rgb(198, 244, 50)
    private val main = Color.WHITE
    private val dim = Color.rgb(150, 162, 180)

    fun render(r: StudentReport.Report, s: Strings, lang: Lang): Bitmap {
        val w = 1080; val h = 1350
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(bg)
        fun p(size: Float, color: Int, bold: Boolean = false, align: Paint.Align = Paint.Align.LEFT) = Paint().apply {
            textSize = size; this.color = color; typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT; isAntiAlias = true; textAlign = align
        }
        var y = 120f
        c.drawText(r.name, 70f, y, p(78f, main, true))
        y += 60f
        c.drawText(StudentReportPdf.period(r, s, lang), 70f, y, p(32f, dim))
        y += 50f
        for (b in r.strokes.take(4)) {
            val box = RectF(60f, y, w - 60f, y + 170f)
            c.drawRoundRect(box, 32f, 32f, Paint().apply { color = surface; isAntiAlias = true })
            c.drawText(strokeLabel(s, b.stroke), 100f, y + 62f, p(44f, main, true))
            c.drawText(listOf(s.studentSessions(b.sessions), s.studentShots(b.shots)).joinToString(" · "), 100f, y + 120f, p(32f, dim))
            b.pct?.let { c.drawText("$it%", w - 100f, y + 100f, p(84f, ball, true, Paint.Align.RIGHT)) }
            if (b.trend.size >= 2) StudentReportPdf.sparkline(c, b.trend, RectF(520f, y + 40f, 760f, y + 130f), ball, dim)
            y += 195f
        }
        // L'impatto di un colpo dentro, se c'è, riempie il resto.
        val img = r.examples.firstOrNull { it.inPlay } ?: r.examples.firstOrNull()
        val contact = img?.images?.getOrNull(if (img.stroke == TrainedStroke.SERVE) 2 else 1)?.second
        val b = contact?.let { runCatching { StudentReportPdf.decodeSmall(it, 960) }.getOrNull() }
        val bottom = h - 110f
        if (b != null && bottom - y > 200f) {
            val avail = RectF(60f, y + 10f, w - 60f, bottom)
            val scale = minOf(avail.width() / b.width, avail.height() / b.height)
            val dw = b.width * scale; val dh = b.height * scale
            val dst = RectF(avail.centerX() - dw / 2, avail.top, avail.centerX() + dw / 2, avail.top + dh)
            c.drawBitmap(b, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
            // La didascalia su una fascia scura, sopra le scritte che la clip ha già in basso.
            c.drawRect(RectF(dst.left, dst.bottom - 70f, dst.right, dst.bottom), Paint().apply { color = 0xCC000000.toInt() })
            c.drawText("${strokeLabel(s, img.stroke)} · ${s.poseContact}", dst.left + 20f, dst.bottom - 24f, p(32f, main, true))
            b.recycle()
        } else if (r.strokes.isEmpty()) {
            c.drawText(s.reportEmpty, 70f, y + 60f, p(36f, dim))
        }
        c.drawText(s.generatedWith, w / 2f, h - 50f, p(30f, dim, align = Paint.Align.CENTER))
        return bmp
    }
}
