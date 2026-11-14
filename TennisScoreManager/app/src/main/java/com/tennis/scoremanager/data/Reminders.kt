// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import java.util.Calendar

/**
 * I promemoria del telefono (notifiche, niente messaggi a nessuno): all'allenatore la sera prima delle lezioni e le
 * presenze di un corso ancora da mandare; a chi ha ricevuto una scheda partita, la mattina del giorno della partita.
 * Ognuno ha una chiave: già mostrato, non torna.
 */
object Reminders {
    /** Un promemoria: [key] per non ripeterlo, [target] dove porta il tocco ("lessons", "course:<id>", "home"). */
    data class Item(val key: String, val kind: Kind, val count: Int, val text: String, val target: String)
    enum class Kind { TOMORROW, SEND, MATCH }

    private fun dayKey(t: Long): String = Calendar.getInstance().apply { timeInMillis = t }.let { "%04d-%02d-%02d".format(it.get(Calendar.YEAR), it.get(Calendar.MONTH) + 1, it.get(Calendar.DAY_OF_MONTH)) }
    private fun hour(t: Long): Int = Calendar.getInstance().apply { timeInMillis = t }.get(Calendar.HOUR_OF_DAY)
    private fun hhmm(t: Long): String = Calendar.getInstance().apply { timeInMillis = t }.let { "%02d:%02d".format(it.get(Calendar.HOUR_OF_DAY), it.get(Calendar.MINUTE)) }

    /**
     * Quelli da mostrare adesso ([now]) e non ancora mostrati ([shown]). [coach] = ruolo Allenatore; [names] dà il nome
     * di un allievo.
     */
    fun due(now: Long, coach: Boolean, book: CoachBook, cards: List<ReceivedCard>, shown: Set<String>, names: (String) -> String): List<Item> {
        val out = mutableListOf<Item>()
        val today = CourtPlans.dayStart(now)
        val tomorrow = CourtPlans.addDays(today, 1)
        val after = CourtPlans.addDays(today, 2)
        if (coach && hour(now) in 17..21) {
            // Lezioni private e dei corsi di domani: l'ora e con chi (le prime tre).
            val priv = book.lessons.filter { !it.cancelled && it.start in tomorrow until after }.map { it.start to it.students.joinToString(", ") { s -> names(s) } }
            val courses = book.courses.flatMap { cc -> Courses.lessons(cc.course).filter { it.start in tomorrow until after && it.key !in cc.course.cancelled }.map { it.start to cc.card.course.name } }
            val all = (priv + courses).sortedBy { it.first }
            val key = "tomorrow:" + dayKey(tomorrow)
            if (all.isNotEmpty() && key !in shown) out += Item(key, Kind.TOMORROW, all.size, all.take(3).joinToString(" · ") { "${hhmm(it.first)} ${it.second}" }, "lessons")
        }
        if (coach && hour(now) in 9..21) for (cc in book.courses.filter { it.toSend && now - it.changedAt > 20L * 3_600_000L }) {
            val key = "send:${cc.card.course.id}:" + dayKey(now)
            if (key !in shown) out += Item(key, Kind.SEND, 1, cc.card.course.name, "course:" + cc.card.course.id)
        }
        if (hour(now) >= 7) for (rc in cards.filter { it.record == null && it.card.at != null && it.card.at in now until tomorrow }) {
            val key = "card:${rc.card.code}:" + dayKey(now)
            val c = rc.card
            if (key !in shown) out += Item(key, Kind.MATCH, 1, listOf(hhmm(c.at!!), c.court, c.side1.joinToString(" / ") { it.name } + " – " + c.side2.joinToString(" / ") { it.name })
                .filter { it.isNotBlank() }.joinToString(" · "), "home")
        }
        return out
    }
}
