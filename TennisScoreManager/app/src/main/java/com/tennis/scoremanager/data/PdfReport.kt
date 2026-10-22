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
import com.tennis.scoremanager.model.MatchState
import com.tennis.scoremanager.model.Side
import com.tennis.scoremanager.model.TiebreakKind
import com.tennis.scoremanager.ui.Strings
import java.io.File
import java.io.OutputStream

/**
 * Riepilogo completo in PDF (A4), fatto con PdfDocument di Android, senza librerie: risultato con l'immagine del
 * riepilogo e le foto dei giocatori, statistiche della partita e di ogni set, cronologia game per game e, alla fine,
 * le foto ricordo della partita (due per pagina).
 */
object PdfReport {
    private const val W = 595
    private const val H = 842
    private const val M = 40f
    private const val CONTENT_W = W - 2 * M

    private val dark = Color.rgb(20, 28, 44)
    private val grey = Color.rgb(110, 120, 135)
    private val line = Color.rgb(220, 224, 230)
    private val yellow = Color.rgb(200, 160, 0)
    private val red = Color.rgb(205, 45, 42)

    private fun paint(size: Float, color: Int = dark, bold: Boolean = false, align: Paint.Align = Paint.Align.LEFT) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = if (bold) Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) else Typeface.SANS_SERIF
            textAlign = align
        }

    /** Pagine una dopo l'altra: [ensure] apre una pagina nuova quando lo spazio rimasto non basta. */
    private class Writer(val doc: PdfDocument, val footer: String) {
        private var number = 0
        var page: PdfDocument.Page? = null
        var y = M

        val canvas: Canvas get() = page!!.canvas

        fun newPage() {
            finish()
            number++
            page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, number).create())
            y = M
        }

        fun ensure(height: Float) {
            if (page == null || y + height > H - M - 16) newPage()
        }

        fun finish() {
            page?.let {
                it.canvas.drawText(footer, W / 2f, H - M / 2, paint(8f, grey, align = Paint.Align.CENTER))
                it.canvas.drawText("$number", W - M, H - M / 2, paint(8f, grey, align = Paint.Align.RIGHT))
                doc.finishPage(it)
            }
            page = null
        }

        fun title(text: String) {
            ensure(40f)
            y += 18
            canvas.drawText(text, M, y, paint(16f, bold = true))
            y += 12
        }

        /** Testo che va a capo sulla larghezza della pagina. */
        fun paragraph(text: String, p: Paint, gap: Float = 4f) {
            val words = text.split(' ')
            var cur = ""
            fun flush() {
                ensure(p.textSize + gap)
                y += p.textSize + gap
                canvas.drawText(cur, M, y, p)
                cur = ""
            }
            for (w in words) {
                val next = if (cur.isEmpty()) w else "$cur $w"
                if (p.measureText(next) > CONTENT_W && cur.isNotEmpty()) {
                    flush()
                    cur = w
                } else cur = next
            }
            if (cur.isNotEmpty()) flush()
        }
    }

    fun write(
        out: OutputStream, rec: MatchRecord, state: MatchState, s: Strings, names: Names,
        card: Bitmap, matchPhotos: List<File>, playerPhoto: (String) -> Bitmap?,
    ) {
        val doc = PdfDocument()
        val wr = Writer(doc, "${s.appName} · ${Reports.siteUrl(rec.options.lang)}")
        val lang = rec.options.lang
        val pts = MatchStats.points(rec)

        // ---- Pagina 1: immagine del risultato, giocatori con le foto, dati della partita
        wr.newPage()
        val cardW = 300f
        val cardH = cardW * card.height / card.width
        wr.canvas.drawBitmap(card, null, RectF((W - cardW) / 2, wr.y, (W + cardW) / 2, wr.y + cardH), Paint(Paint.FILTER_BITMAP_FLAG))
        wr.y += cardH + 16
        for (side in Side.entries) {
            val real = PlayerStats.names(rec.setup, side)
            var x = M
            val color = if (side == Side.P1) yellow else red
            for (n in names.players(side)) {
                val photo = if (n in real) playerPhoto(n) else null
                if (photo != null) {
                    drawCircle(wr.canvas, photo, x, wr.y, 28f)
                    x += 34f
                }
                wr.canvas.drawText(n, x, wr.y + 20, paint(12f, color, bold = side == state.winner))
                x += paint(12f, bold = true).measureText(n) + 18
            }
            wr.y += 34
        }
        wr.y += 6
        val body = paint(11f)
        val info = listOf(
            "${s.result}: ${names.short(state.winner ?: Side.P1)} ${Reports.scoreLine(state, state.winner ?: Side.P1)}",
            "${s.duration}: ${Reports.duration(rec.clockMs)}",
            "${Reports.date(rec.startedAt, lang).replaceFirstChar { it.uppercase() }} · ${s.startTime} ${Reports.time(rec.startedAt, lang)} · ${s.endTime} ${Reports.time(rec.endedAt, lang)}",
            listOfNotNull(rec.setup.club.ifBlank { null }, rec.setup.court.ifBlank { null }?.let { "${s.court} $it" }).joinToString(" · "),
            "${s.place}: ${Reports.place(rec, s)}",
            "${s.format}: ${Reports.formatLabel(rec, s)}",
        ).filter { it.isNotBlank() }
        info.forEach { wr.paragraph(it, body) }

        // ---- Statistiche: partita intera e poi ogni set
        val sets = (pts.maxOfOrNull { it.set } ?: -1) + 1
        val periods = listOf<Int?>(null) + if (sets > 1) (0 until sets).toList() else emptyList()
        wr.newPage()
        wr.title(s.tabStats)
        for (period in periods) {
            wr.ensure(60f)
            wr.y += 14
            wr.canvas.drawText(if (period == null) s.periodMatch else s.periodSet(period + 1), M, wr.y, paint(12f, grey, bold = true))
            wr.y += 6
            statsTable(wr, statRows(rec, pts, period, s), names)
        }
        if (sets > 0) {
            val durations = MatchStats.setDurations(pts)
            if (durations.any { it != null }) {
                wr.paragraph("${s.statSetDurations}: " + durations.joinToString(" · ") { d -> d?.let { minutes(it) } ?: "–" }, body, 10f)
            }
            MatchStats.longestGame(pts)?.let { (ms, n) -> wr.paragraph("${s.statLongestGame}: ${s.longestGameValue(minutes(ms), n)}", body) }
        }

        // ---- Cronologia game per game
        wr.newPage()
        wr.title(s.tabChronology)
        val small = paint(9.5f)
        for (g in MatchStats.chronology(pts)) {
            val head = buildString {
                append("${s.periodSet(g.set + 1)} · ${g.games1}-${g.games2}")
                if (g.tiebreak == TiebreakKind.SET && g.tb1 != null && g.tb2 != null) append(" (${g.tb1}-${g.tb2})")
                append(" · ${s.serving}: ${names.short(g.server)}")
                if (g.broken) append(" · ${s.serveLost}")
            }
            val steps = g.steps.joinToString(", ") { st ->
                "${st.p1}:${st.p2}" + when (st.marker) {
                    MatchStats.Marker.BREAK_POINT -> " [${s.markerBreak}]"
                    MatchStats.Marker.SET_POINT -> " [${s.markerSet}]"
                    MatchStats.Marker.MATCH_POINT -> " [${s.markerMatch}]"
                    null -> ""
                }
            }
            val end = when {
                g.winner == null -> ""
                g.closesMatch -> s.chronoMatch
                g.closesSet -> s.chronoSet
                g.broken -> s.chronoBreak
                else -> s.chronoGame
            }
            wr.paragraph(head, paint(10f, bold = true), 8f)
            if (steps.isNotEmpty() || end.isNotEmpty()) wr.paragraph(listOf(steps, end).filter { it.isNotEmpty() }.joinToString(", "), small, 3f)
        }

        // ---- Foto ricordo, due per pagina
        val photos = matchPhotos.mapNotNull { f -> runCatching { BitmapFactory.decodeFile(f.absolutePath) }.getOrNull() }
        photos.chunked(2).forEach { pair ->
            wr.newPage()
            wr.title(s.matchPhotosTitle)
            val boxH = (H - M - 16 - wr.y - 20) / 2
            pair.forEach { b ->
                val scale = minOf(CONTENT_W / b.width, boxH / b.height)
                val w = b.width * scale
                val h = b.height * scale
                wr.canvas.drawBitmap(b, null, RectF((W - w) / 2, wr.y + 10, (W + w) / 2, wr.y + 10 + h), Paint(Paint.FILTER_BITMAP_FLAG))
                wr.y += boxH + 10
            }
        }
        wr.finish()
        doc.writeTo(out)
        doc.close()
    }

    private fun drawCircle(c: Canvas, b: Bitmap, x: Float, y: Float, size: Float) {
        c.save()
        c.clipPath(Path().apply { addCircle(x + size / 2, y + size / 2, size / 2, Path.Direction.CW) })
        c.drawBitmap(b, null, RectF(x, y, x + size, y + size), Paint(Paint.FILTER_BITMAP_FLAG))
        c.restore()
    }

    private fun statsTable(wr: Writer, rows: List<Triple<String, String, String>>, names: Names) {
        val label = paint(10f)
        val value = paint(10f, bold = true, align = Paint.Align.RIGHT)
        val colA = M + CONTENT_W * 0.72f
        val colB = W - M
        wr.ensure(18f)
        wr.y += 14
        wr.canvas.drawText(names.short(Side.P1).take(22), colA, wr.y, paint(9f, yellow, bold = true, align = Paint.Align.RIGHT))
        wr.canvas.drawText(names.short(Side.P2).take(22), colB, wr.y, paint(9f, red, bold = true, align = Paint.Align.RIGHT))
        for ((l, a, b) in rows) {
            wr.ensure(15f)
            wr.y += 15
            wr.canvas.drawText(l, M, wr.y, label)
            wr.canvas.drawText(a, colA, wr.y, value)
            wr.canvas.drawText(b, colB, wr.y, value)
            wr.canvas.drawLine(M, wr.y + 4, W - M, wr.y + 4, Paint().apply { color = line; strokeWidth = 0.5f })
        }
        wr.y += 6
    }

    private fun pct(v: Int, of: Int) = if (of > 0) "${Math.round(v * 100.0 / of)}% ($v/$of)" else "–"

    /** Righe della tabella: le stesse voci della scheda Statistiche. */
    fun statRows(rec: MatchRecord, pts: List<MatchStats.PlayedPoint>, set: Int?, s: Strings): List<Triple<String, String, String>> {
        val t = MatchStats.totals(pts, set)
        val a = t.getValue(Side.P1)
        val b = t.getValue(Side.P2)
        fun row(l: String, f: (MatchStats.SideTotals) -> String) = Triple(l, f(a), f(b))
        val rows = mutableListOf(
            row(s.statTotalPoints) { pct(it.pointsWon, it.points) },
            row(s.statServicePoints) { pct(it.servePointsWon, it.servePoints) },
            row(s.statReturnPoints) { pct(it.returnPointsWon, it.returnPoints) },
            row(s.statServiceGames) { pct(it.serviceGamesWon, it.serviceGames) },
            row(s.statReturnGames) { pct(it.returnGamesWon, it.returnGames) },
            row(s.statBreakWon) { "${it.breakPointsWon}/${it.breakPoints}" },
            row(s.statBreakSaved) { "${it.breakPointsSaved}/${it.breakPointsFaced}" },
            row(s.statGamePointsWon) { "${it.gamePointsWon}/${it.gamePoints}" },
            row(s.statSetPointsWon) { "${it.setPointsWon}/${it.setPoints}" },
            row(s.statMatchPointsWon) { "${it.matchPointsWon}/${it.matchPoints}" },
            row(s.statTotalGames) { pct(it.gamesWon, it.games) },
            row(s.statLoveGames) { "${it.loveGames}" },
            row(s.statBestGameRun) { "${it.bestGameRun}" },
            row(s.statStreak) { "${it.maxStreak}" },
        )
        if (set == null) rows += row(s.statSetsFromBreakDown) { "${it.setsFromBreakDown}" }
        if (MatchStats.hasDetail(rec, pts)) {
            rows += listOf(
                row(s.statAces) { "${it.aces}" },
                row(s.statDoubleFaults) { "${it.doubleFaults}" },
                row(s.statFirstServeIn) { pct(it.firstServesIn, it.servePoints) },
                row(s.statFirstServeWon) { pct(it.firstServePointsWon, it.firstServesIn) },
                row(s.statSecondServeWon) { pct(it.secondServePointsWon, it.secondServes) },
                row(s.statWinners) { "${it.winners}" },
                row(s.statErrors) { "${it.errors}" },
                row(s.statUnforcedErrors) { "${it.unforcedErrors}" },
                row(s.statForcedErrors) { "${it.forcedErrors}" },
            )
        }
        return rows
    }

    private fun minutes(ms: Long): String {
        val m = Math.round(ms / 60_000.0).toInt()
        return if (m >= 60) "${m / 60} h ${"%02d".format(m % 60)}'" else "$m'"
    }
}
