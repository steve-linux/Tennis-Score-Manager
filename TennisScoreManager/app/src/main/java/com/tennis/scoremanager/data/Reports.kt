package com.tennis.scoremanager.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.tennis.scoremanager.model.Lang
import com.tennis.scoremanager.model.MatchEvent
import com.tennis.scoremanager.model.MatchFormat
import com.tennis.scoremanager.model.MatchState
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

    /** "G1 92% → 71% · G2 88% → 70%" */
    fun batteryLine(rec: MatchRecord, s: Strings): String =
        Side.entries.mapNotNull { side ->
            val a = rec.batteryStart[side]
            val b = rec.batteryEnd[side]
            if (a == null && b == null) null
            else "${s.playerTag(side.ordinal + 1)} ${a?.let { "$it%" } ?: "?"} → ${b?.let { "$it%" } ?: "?"}"
        }.joinToString(" · ")

    fun formatLabel(rec: MatchRecord, s: Strings): String =
        (if (rec.rules.format == MatchFormat.BEST_OF_THREE) s.formatBestOfThree else s.formatMatchTiebreak) +
            (if (rec.rules.noAd) " · ${s.noAdShort}" else "") +
            " · " + (if (rec.rules.doubles) s.doubles else s.singles)

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
        sb.appendLine(date(rec.startedAt, lang).replaceFirstChar { it.uppercase() })
        sb.appendLine()
        sb.appendLine("🏆 ${s.winner}: ${names.side(w)}")
        sb.appendLine("${s.result}: ${names.short(w)} ${s.vs} ${names.short(w.other)}  ${scoreLine(state, w)}")
        sb.appendLine()
        for (side in listOf(Side.P1, Side.P2)) {
            val sets = state.sets.joinToString("  ") { set ->
                val tb = if (set.hasTiebreak && !set.matchTiebreak && set.winner != side) "(${set.tb(side)})" else ""
                "${set.shown(side)}$tb"
            }
            val mark = if (side == w) " ✔" else ""
            sb.appendLine("${names.short(side)}: $sets$mark")
        }
        sb.appendLine()
        sb.appendLine("⏱ ${s.duration}: ${duration(rec.clockMs)}")
        sb.appendLine("🕘 ${s.startTime}: ${time(rec.startedAt, lang)} · ${s.endTime}: ${time(rec.endedAt, lang)}")
        sb.appendLine("📍 ${s.place}: ${place(rec, s)}")
        sb.appendLine("📋 ${s.format}: ${formatLabel(rec, s)}")
        sb.appendLine("${s.pointsWon}: ${pointsWon(rec, Side.P1)} - ${pointsWon(rec, Side.P2)} · ${s.gamesWon}: ${gamesWon(state, Side.P1)} - ${gamesWon(state, Side.P2)}")
        if (rec.batteryStart.isNotEmpty() || rec.batteryEnd.isNotEmpty()) sb.appendLine("🔋 ${s.bandsBattery}: ${batteryLine(rec, s)}")
        sb.appendLine()
        sb.append("#tennis · ${s.generatedWith}")
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
        if (header.isNotEmpty()) c.drawText(ellipsize(header, 56), w / 2f, 175f, paint(46f, white, true, Paint.Align.CENTER))
        c.drawText(date(rec.startedAt, lang).replaceFirstChar { it.uppercase() }, w / 2f, 235f, paint(36f, grey, false, Paint.Align.CENTER))

        // Riquadro del vincitore
        val box = RectF(60f, 290f, w - 60f, 470f)
        c.drawRoundRect(box, 36f, 36f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(60, 255, 255, 255) })
        c.drawText("🏆  ${s.winner.uppercase()}", w / 2f, 355f, paint(38f, grey, true, Paint.Align.CENTER))
        c.drawText(ellipsize(names.side(winner), 28), w / 2f, 440f, paint(66f, if (winner == Side.P1) yellow else red, true, Paint.Align.CENTER))

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
            if (players.size == 1) {
                c.drawText(ellipsize(players[0], 18), 120f, y + rowH / 2 + 22f, paint(58f, white, side == winner))
            } else {
                c.drawText(ellipsize(players[0], 18), 120f, y + rowH / 2 - 14f, paint(50f, white, side == winner))
                c.drawText(ellipsize(players[1], 18), 120f, y + rowH / 2 + 50f, paint(50f, white, side == winner))
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
        val label = paint(34f, grey)
        val value = paint(40f, white, true)
        fun row(l: String, v: String) {
            c.drawText(l, 80f, y, label)
            c.drawText(ellipsize(v, 40), 380f, y, value)
            y += 66f
        }
        row(s.duration, duration(rec.clockMs))
        row("${s.startTime} / ${s.endTime}", "${time(rec.startedAt, lang)} – ${time(rec.endedAt, lang)}")
        row(s.place, rec.location?.address?.ifBlank { null } ?: place(rec, s))
        row(s.format, formatLabel(rec, s))
        c.drawText(s.generatedWith, w / 2f, h - 50f, paint(30f, grey, false, Paint.Align.CENTER))
        return bmp
    }

    private fun ellipsize(t: String, max: Int) = if (t.length <= max) t else t.take(max - 1) + "…"
}
