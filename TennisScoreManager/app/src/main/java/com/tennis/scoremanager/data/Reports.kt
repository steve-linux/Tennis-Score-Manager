// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.tennis.scoremanager.ble.BandProtocol
import com.tennis.scoremanager.model.Lang
import com.tennis.scoremanager.model.MatchEvent
import com.tennis.scoremanager.model.MatchFormat
import com.tennis.scoremanager.model.MatchState
import com.tennis.scoremanager.model.RulesConfig
import com.tennis.scoremanager.model.SetScore
import com.tennis.scoremanager.model.Side
import com.tennis.scoremanager.ui.Strings
import com.tennis.scoremanager.ui.stringsFor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Resoconto finale della partita: testo, JSON e immagine da condividere. */
object Reports {

    fun locale(lang: Lang): Locale = lang.locale

    /** Sito dell'app, nella pagina della lingua scelta (l'italiano è la pagina principale). */
    fun siteUrl(lang: Lang): String = SITE_URL + if (lang == Lang.IT) "" else "${lang.code}/"
    private const val SITE_URL = "https://tennis-score-manager.netlify.app/"

    fun duration(ms: Long): String {
        val s = ms / 1000
        return String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60)
    }

    fun time(ts: Long?, lang: Lang): String =
        ts?.let { SimpleDateFormat("HH:mm", locale(lang)).format(Date(it)) } ?: "--:--"

    fun date(ts: Long?, lang: Lang): String =
        ts?.let {
            SimpleDateFormat(stringsFor(lang).datePattern, locale(lang)).format(Date(it))
        } ?: ""

    /** "6-4" · "7-6(5)" · "[10-8]" dal punto di vista di [from]. */
    fun setText(set: SetScore, from: Side): String {
        val o = from.other
        return when {
            set.matchTiebreak -> "[${set.tb(from)}-${set.tb(o)}]"
            set.hasTiebreak -> "${set.games(from)}-${set.games(o)}(${minOf(set.tb1!!, set.tb2!!)})"
            else -> "${set.games(from)}-${set.games(o)}"
        }
    }

    fun scoreLine(state: MatchState, from: Side): String = state.sets.joinToString("  ") { setText(it, from) }

    fun pointsWon(rec: MatchRecord, side: Side): Int = rec.events.count { it is MatchEvent.Point && it.winner == side }
    fun gamesWon(state: MatchState, side: Side): Int = state.sets.sumOf { it.games(side) } + state.games(side)

    /** "Open di Primavera · Lim. 4.1 · Semifinale"; null senza torneo. */
    fun eventLine(su: SetupData): String? =
        listOf(su.event, su.category, su.round).map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" · ").ifEmpty { null }

    /** "G1 92% → 71% · G2 88% → 70%" */
    fun batteryLine(rec: MatchRecord, s: Strings): String =
        Side.entries.mapNotNull { side ->
            val a = rec.batteryStart[side]
            val b = rec.batteryEnd[side]
            if (a == null && b == null) null
            else "${s.playerTag(side.ordinal + 1)} ${a?.let { "$it%" } ?: "?"} → ${b?.let { "$it%" } ?: "?"}"
        }.joinToString(" · ")

    /** "Dritto 12 · Rovescio 8 · Servizio 5 · Volée 3" (+ i non riconosciuti, se ce ne sono); null senza colpi. */
    fun strokesLine(rec: MatchRecord, side: Side, s: Strings): String? {
        val st = rec.strokes[side]?.takeIf { it.total > 0 } ?: return null
        val parts = (0 until BandProtocol.STROKE_COUNT).map { "${s.strokeName(it)} ${st.count(it)}" } +
            listOfNotNull(st.unknown.takeIf { it > 0 }?.let { "${s.strokeOther} $it" })
        return parts.joinToString(" · ")
    }

    /** "Dritto 1450 °/s (max 1980) · Servizio 1620 °/s (max 2010)": velocità del polso al picco; null senza dati. */
    fun wristSpeedLine(rec: MatchRecord, side: Side, s: Strings): String? {
        val st = rec.strokes[side] ?: return null
        val parts = (0 until BandProtocol.STROKE_COUNT).mapNotNull { k ->
            val avg = st.avgGyro(k) ?: return@mapNotNull null
            "${s.strokeName(k)} $avg °/s (${s.maxShort} ${st.maxGyro(k) ?: avg})"
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    /** "medio 138 · max 176 bpm · ~620 kcal (stima)"; null se la fascia di quel lato non ha mandato niente. */
    fun heartLine(rec: MatchRecord, side: Side, s: Strings): String? {
        val h = HeartStats.summary(rec.heart[side]) ?: return null
        return s.heartLine(h.avg, h.max) + (h.kcal?.let { " · ${s.caloriesLine(it)}" } ?: "")
    }

    /** Colpi per punto (entrambi i braccialetti): [shortPct] 1-3 colpi, [mediumPct] 4-8, [longPct] 9 o più. */
    data class RallyStats(val points: Int, val average: Double, val longest: Int, val shortPct: Int, val mediumPct: Int, val longPct: Int)

    /** Dai punti con la lunghezza dello scambio (braccialetti 2.4 col riconoscimento acceso); null se non ce ne sono. */
    fun rallyStats(rec: MatchRecord): RallyStats? = rallyStats(rec.events.mapNotNull { (it as? MatchEvent.Point)?.strokes })

    /** Da una lista di lunghezze di scambio (colpi per punto); null se vuota. */
    fun rallyStats(r: List<Int>): RallyStats? {
        if (r.isEmpty()) return null
        fun pct(n: Int) = Math.round(n * 100.0 / r.size).toInt()
        val short = pct(r.count { it <= 3 })
        val long = pct(r.count { it >= 9 })
        return RallyStats(r.size, r.average(), r.max(), short, (100 - short - long).coerceAtLeast(0), long)
    }

    /** "media 4,2 · più lungo 15 · brevi (1-3) 45% · medi (4-8) 40% · lunghi (9+) 15%" */
    fun rallyLine(st: RallyStats, s: Strings, lang: Lang): String =
        "${s.rallyAverage} ${String.format(locale(lang), "%.1f", st.average)} · ${s.rallyLongest} ${st.longest} · " +
            "${s.rallyShort} (1-3) ${st.shortPct}% · ${s.rallyMedium} (4-8) ${st.mediumPct}% · ${s.rallyLong} (9+) ${st.longPct}%"

    /** "3 set · tie-break a 7", "5 set · set a 4 game · senza tie-break", "pro set a 10", "solo super tie-break a 10". */
    fun formatName(r: RulesConfig, s: Strings): String {
        val noTb = if (!r.setTiebreak) listOf(s.labelNoTiebreak) else emptyList()
        val short = if (r.gamesPerSet == 4) listOf(s.labelShortSets) else emptyList()
        val parts = when (r.format) {
            MatchFormat.MATCH_TIEBREAK_ONLY -> listOf(s.formatMatchTiebreakOnly)
            MatchFormat.PRO_SET -> listOf(s.labelProSet(r.gamesPerSet)) + noTb
            MatchFormat.TWO_SETS_MATCH_TIEBREAK -> listOf(s.formatMatchTiebreak) + short + noTb
            MatchFormat.BEST_OF_THREE, MatchFormat.BEST_OF_FIVE ->
                listOf(s.labelSets(if (r.format == MatchFormat.BEST_OF_FIVE) 5 else 3)) + short + (if (r.setTiebreak) listOf(s.labelTiebreak7) else noTb)
        }
        return parts.joinToString(" · ")
    }

    fun formatLabel(rec: MatchRecord, s: Strings): String {
        val padel = rec.setup.sport == Sport.PADEL
        return (if (padel) "${s.sportPadel} · " else "") +
            formatName(rec.rules, s) +
            (if (rec.rules.noAd) " · ${if (padel) s.goldenPoint else s.noAdShort}" else "") +
            (if (rec.rules.starPoint && !rec.rules.noAd) " · ${s.starPoint}" else "") +
            " · " + (if (rec.rules.doubles) s.doubles else s.singles)
    }

    fun place(rec: MatchRecord, s: Strings): String {
        val loc = rec.location ?: return s.placeUnavailable
        val coords = String.format(Locale.ROOT, "%.5f, %.5f", loc.lat, loc.lon)
        return if (loc.address.isNullOrBlank()) coords else "${loc.address} ($coords)"
    }

    fun fileBaseName(rec: MatchRecord, names: Names): String {
        val d = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.ROOT).format(Date(rec.startedAt ?: rec.updatedAt))
        fun clean(x: String) = x.replace(Regex("[^\\p{L}\\p{N}]+"), "-").trim('-').take(30)
        return "${d}_${clean(names.short(Side.P1))}_vs_${clean(names.short(Side.P2))}"
    }

    fun text(rec: MatchRecord, state: MatchState, s: Strings, names: Names): String {
        val lang = rec.options.lang
        val w = state.winner ?: Side.P1
        val sb = StringBuilder()
        sb.appendLine("🎾 ${s.appName.uppercase()}")
        if (rec.setup.club.isNotBlank() || rec.setup.court.isNotBlank()) {
            sb.appendLine(listOfNotNull(
                rec.setup.club.ifBlank { null },
                rec.setup.court.ifBlank { null }?.let { "${s.court} $it" },
            ).joinToString(" · "))
        }
        eventLine(rec.setup)?.let { sb.appendLine("🏆 $it") }
        sb.appendLine(date(rec.startedAt, lang).replaceFirstChar { it.uppercase() })
        sb.appendLine()
        sb.appendLine("🏆 ${s.winner}: ${names.side(w)}")
        sb.appendLine("${s.result}: ${names.short(w)} ${s.vs} ${names.short(w.other)}  ${scoreLine(state, w)}")
        sb.appendLine()
        sb.appendLine("⏱ ${s.duration}: ${duration(rec.clockMs)}")
        sb.appendLine("🕘 ${s.startTime}: ${time(rec.startedAt, lang)} · ${s.endTime}: ${time(rec.endedAt, lang)}")
        sb.appendLine("📍 ${s.place}: ${place(rec, s)}")
        sb.appendLine("📋 ${s.format}: ${formatLabel(rec, s)}")
        sb.appendLine("${s.pointsWon}: ${pointsWon(rec, Side.P1)} - ${pointsWon(rec, Side.P2)} · ${s.gamesWon}: ${gamesWon(state, Side.P1)} - ${gamesWon(state, Side.P2)}")
        if (rec.batteryStart.isNotEmpty() || rec.batteryEnd.isNotEmpty()) sb.appendLine("🔋 ${s.bandsBattery}: ${batteryLine(rec, s)}")
        for (side in Side.entries) strokesLine(rec, side, s)?.let { sb.appendLine("🎾 ${s.strokes} ${names.short(side)}: $it") }
        for (side in Side.entries) wristSpeedLine(rec, side, s)?.let { sb.appendLine("⚡ ${s.wristSpeed} ${names.short(side)}: $it") }
        rallyStats(rec)?.let { sb.appendLine("🔁 ${s.strokesPerPoint}: ${rallyLine(it, s, lang)}") }
        for (side in Side.entries) heartLine(rec, side, s)?.let { sb.appendLine("❤️ ${s.heartStat} ${names.short(side)}: $it") }
        if (rec.notes.isNotBlank()) {
            sb.appendLine()
            sb.appendLine("📝 ${s.matchNotesTitle}")
            sb.appendLine(rec.notes.trim())
        }
        sb.appendLine()
        sb.appendLine("#tennis · ${s.generatedWith}")
        sb.append(siteUrl(lang))
        return sb.toString()
    }

    /** Immagine 1080x1350 (formato adatto ai social) con il risultato. */
    fun renderCard(rec: MatchRecord, state: MatchState, s: Strings, names: Names): Bitmap {
        val w = 1080
        val h = 1350
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val bg = Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, h.toFloat(), Color.rgb(12, 18, 32), Color.rgb(10, 60, 48), Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bg)

        fun paint(size: Float, color: Int, bold: Boolean = false, align: Paint.Align = Paint.Align.LEFT) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = if (bold) Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) else Typeface.SANS_SERIF
            textAlign = align
        }
        val white = Color.WHITE
        val grey = Color.rgb(170, 184, 200)
        val yellow = Color.rgb(255, 214, 0)
        val red = Color.rgb(229, 57, 53)
        val lang = rec.options.lang
        val winner = state.winner ?: Side.P1

        c.drawText("TENNIS SCORE MANAGER", w / 2f, 110f, paint(40f, Color.rgb(198, 244, 50), true, Paint.Align.CENTER).apply { letterSpacing = 0.2f })
        val header = listOfNotNull(rec.setup.club.ifBlank { null }, rec.setup.court.ifBlank { null }?.let { "${s.court} $it" }).joinToString(" · ")
        if (header.isNotEmpty()) paint(46f, white, true, Paint.Align.CENTER).let { c.drawText(fit(header, it, w - 120f), w / 2f, 175f, it) }
        c.drawText(date(rec.startedAt, lang).replaceFirstChar { it.uppercase() }, w / 2f, 235f, paint(36f, grey, false, Paint.Align.CENTER))

        // Riquadro del vincitore
        val box = RectF(60f, 290f, w - 60f, 470f)
        c.drawRoundRect(box, 36f, 36f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(60, 255, 255, 255) })
        c.drawText("🏆  ${s.winner.uppercase()}", w / 2f, 355f, paint(38f, grey, true, Paint.Align.CENTER))
        paint(66f, if (winner == Side.P1) yellow else red, true, Paint.Align.CENTER).let { c.drawText(fit(names.side(winner), it, box.width() - 60f), w / 2f, 440f, it) }

        // Tabella punteggio
        val top = 540f
        val rowH = 190f
        val setCount = state.sets.size.coerceAtLeast(1)
        val colW = 130f
        val firstCol = w - 70f - setCount * colW
        for ((i, side) in listOf(Side.P1, Side.P2).withIndex()) {
            val y = top + i * (rowH + 30f)
            val rect = RectF(60f, y, w - 60f, y + rowH)
            c.drawRoundRect(rect, 28f, 28f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(40, 255, 255, 255) })
            c.drawRoundRect(RectF(60f, y, 84f, y + rowH), 12f, 12f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (side == Side.P1) yellow else red })
            val players = names.players(side)
            // I nomi non devono finire sotto la colonna del primo set.
            val nameW = firstCol - 120f - 20f
            if (players.size == 1) {
                paint(58f, white, side == winner).let { c.drawText(fit(players[0], it, nameW), 120f, y + rowH / 2 + 22f, it) }
            } else {
                paint(50f, white, side == winner).let { c.drawText(fit(players[0], it, nameW), 120f, y + rowH / 2 - 14f, it) }
                paint(50f, white, side == winner).let { c.drawText(fit(players[1], it, nameW), 120f, y + rowH / 2 + 50f, it) }
            }
            for ((j, set) in state.sets.withIndex()) {
                val x = firstCol + j * colW + colW / 2
                val won = set.winner == side
                c.drawText(set.shown(side).toString(), x, y + rowH / 2 + 34f, paint(if (set.matchTiebreak) 64f else 92f, if (won) white else grey, won, Paint.Align.CENTER))
                if (set.hasTiebreak && !set.matchTiebreak && !won) {
                    c.drawText(set.tb(side).toString(), x + 42f, y + rowH / 2 - 22f, paint(38f, grey, false, Paint.Align.LEFT))
                }
            }
        }

        // Dati della partita
        var y = top + 2 * (rowH + 30f) + 70f
        fun row(l: String, v: String) {
            paint(34f, grey).let { c.drawText(fit(l, it, 380f - 80f - 16f), 80f, y, it) }
            paint(40f, white, true).let { c.drawText(fit(v, it, w - 60f - 380f), 380f, y, it) }
            y += 66f
        }
        row(s.duration, duration(rec.clockMs))
        row("${s.startTime} / ${s.endTime}", "${time(rec.startedAt, lang)} – ${time(rec.endedAt, lang)}")
        row(s.place, rec.location?.address?.ifBlank { null } ?: place(rec, s))
        row(s.format, formatLabel(rec, s))
        // "Creato con Tennis Score Manager · indirizzo del sito", rimpicciolito se non ci sta
        val credit = "${s.generatedWith} · "
        val site = siteUrl(lang).removePrefix("https://").removeSuffix("/")
        val creditPaint = paint(30f, grey)
        val sitePaint = paint(30f, Color.rgb(198, 244, 50), true)
        val scale = minOf(1f, (w - 80f) / (creditPaint.measureText(credit) + sitePaint.measureText(site)))
        creditPaint.textSize *= scale
        sitePaint.textSize *= scale
        val creditX = (w - creditPaint.measureText(credit) - sitePaint.measureText(site)) / 2f
        c.drawText(credit, creditX, h - 50f, creditPaint)
        c.drawText(site, creditX + creditPaint.measureText(credit), h - 50f, sitePaint)
        return bmp
    }

    /**
     * Testo che sta in [maxWidth] pixel (non in un numero di lettere: "WWW" e "iii" sono larghe diverse):
     * prima si rimpicciolisce il carattere di [p] fino al 75%, poi si tronca con "…".
     */
    private fun fit(t: String, p: Paint, maxWidth: Float): String {
        val minSize = p.textSize * 0.75f
        while (p.measureText(t) > maxWidth && p.textSize > minSize) p.textSize -= 2f
        if (p.measureText(t) <= maxWidth) return t
        var end = t.length
        while (end > 1 && p.measureText(t, 0, end) + p.measureText("…") > maxWidth) end--
        if (end > 1 && Character.isHighSurrogate(t[end - 1])) end--
        return t.substring(0, end).trimEnd() + "…"
    }
}
