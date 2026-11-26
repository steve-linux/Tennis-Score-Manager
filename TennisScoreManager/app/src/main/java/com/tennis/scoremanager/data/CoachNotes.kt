// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import kotlinx.serialization.Serializable
import java.util.Calendar
import java.util.UUID

/** Di cosa parla una nota dell'allenatore: facoltativo, serve a filtrare e a dividere il resoconto. */
@Serializable
enum class NoteTag { SERVE, FOREHAND, BACKHAND, VOLLEY, TACTICS, FITNESS, MENTAL }

/** Una nota dell'allenatore su un allievo: quando ([at]), cosa e, se detto, di cosa parla. */
@Serializable
data class CoachNote(val id: String, val at: Long, val text: String, val tag: NoteTag? = null)

/** Le note dell'allenatore come elenco: dal testo di prima, per giorno e mese, filtri e resoconto. */
object CoachNotes {

    fun newId(): String = "n" + UUID.randomUUID().toString().take(8)

    /** "📅 26.11.26  🕐 18:45" (la prima nota del giorno) o "🕐 18:30" (le altre): come le scriveva la dettatura. */
    private val dayLine = Regex("""^📅 (\d{2})\.(\d{2})\.(\d{2})\s+🕐 (\d{2}):(\d{2})\s*$""")
    private val timeLine = Regex("""^🕐 (\d{2}):(\d{2})\s*$""")

    /**
     * Le note dettate scritte nel testo libero (righe "📅 giorno 🕐 ora" e "🕐 ora" con sotto il testo) diventano note
     * singole; quello che non sta sotto una data resta testo libero. Restituisce (testo libero, note).
     */
    fun split(text: String): Pair<String, List<CoachNote>> {
        val free = mutableListOf<String>()
        val notes = mutableListOf<CoachNote>()
        var day: Triple<Int, Int, Int>? = null
        var at: Long? = null
        val body = mutableListOf<String>()
        fun flush() {
            val t = body.joinToString("\n").trim()
            if (at != null && t.isNotEmpty()) notes += CoachNote(newId(), at!!, t)
            body.clear()
        }
        for (line in text.lines()) {
            val d = dayLine.matchEntire(line.trim())
            val h = timeLine.matchEntire(line.trim())
            when {
                d != null -> {
                    flush()
                    val (dd, mm, yy, hh, mi) = d.destructured
                    day = Triple(dd.toInt(), mm.toInt(), 2000 + yy.toInt())
                    at = time(day!!, hh.toInt(), mi.toInt())
                }
                h != null && day != null -> {
                    flush()
                    at = time(day!!, h.groupValues[1].toInt(), h.groupValues[2].toInt())
                }
                at != null -> body += line
                else -> free += line
            }
        }
        flush()
        return free.joinToString("\n").trim() to notes.sortedByDescending { it.at }
    }

    private fun time(day: Triple<Int, Int, Int>, h: Int, m: Int): Long =
        Calendar.getInstance().apply { clear(); set(day.third, day.second - 1, day.first, h, m) }.timeInMillis

    /** Ha ancora note dettate dentro il testo libero (da passare all'elenco)? */
    fun hasLegacy(text: String): Boolean = text.lines().any { dayLine.matches(it.trim()) }

    /** Le note che corrispondono: dal giorno [from] (null = tutte), dell'etichetta [tag] (null = tutte), con [query]. */
    fun filter(notes: List<CoachNote>, from: Long?, tag: NoteTag?, query: String): List<CoachNote> {
        val q = query.trim().lowercase()
        return notes.filter { n ->
            (from == null || n.at >= from) && (tag == null || n.tag == tag) && (q.isEmpty() || q in n.text.lowercase())
        }.sortedByDescending { it.at }
    }

    /** Inizio del giorno di [at] (per raggruppare). */
    fun dayStart(at: Long): Long = Calendar.getInstance().apply {
        timeInMillis = at; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** Inizio del mese di [at] (per i mesi che si chiudono). */
    fun monthStart(at: Long): Long = Calendar.getInstance().apply {
        timeInMillis = dayStart(at); set(Calendar.DAY_OF_MONTH, 1)
    }.timeInMillis

    /**
     * Il resoconto: le note divise per etichetta (nell'ordine delle etichette, poi quelle senza), dalla più vecchia
     * alla più recente dentro ogni gruppo. [label] dà il nome di un'etichetta (null = senza), [stamp] la data.
     */
    fun report(notes: List<CoachNote>, label: (NoteTag?) -> String, stamp: (Long) -> String): List<Pair<String, List<String>>> =
        (NoteTag.entries.map { it as NoteTag? } + null).mapNotNull { tag ->
            val mine = notes.filter { it.tag == tag }.sortedBy { it.at }
            if (mine.isEmpty()) null else label(tag) to mine.map { "${stamp(it.at)} – ${it.text}" }
        }
}
