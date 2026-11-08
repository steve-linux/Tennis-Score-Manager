// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import kotlinx.serialization.Serializable
import java.util.Calendar

/** Un orario settimanale del corso: giorno (1 = lunedì … 7 = domenica), inizio, durata e campo. */
@Serializable
data class CourseSlot(val weekday: Int, val startMin: Int, val minutes: Int = 60, val court: String = "")

/** Come si paga il corso: una quota per la stagione o una al mese. */
@Serializable
enum class CourseFee { SEASON, MONTHLY }

/**
 * Un corso della scuola tennis: allievi (giocatori del database), istruttore, orari settimanali nella stagione
 * [from]–[to], quota. [attendance]: per ogni lezione (chiave [Courses.key]) chi c'era; [cancelled]: le lezioni
 * annullate (pioggia, festa…).
 */
@Serializable
data class Course(
    val id: String,
    val name: String,
    val level: String = "",
    /** L'istruttore: un giocatore del database, o solo il nome. */
    val coach: String? = null,
    val coachName: String = "",
    val students: List<String> = emptyList(),
    val slots: List<CourseSlot> = emptyList(),
    val from: Long,
    val to: Long,
    val maxStudents: Int = 0,
    val feeCents: Int = 0,
    val feeKind: CourseFee = CourseFee.MONTHLY,
    val note: String = "",
    val attendance: Map<String, Set<String>> = emptyMap(),
    val cancelled: Set<String> = emptySet(),
    val createdAt: Long = 0,
)

/** Una lezione del calendario del corso. */
data class Lesson(val key: String, val start: Long, val minutes: Int, val court: String) {
    val end: Long get() = start + minutes * 60_000L
}

object Courses {
    fun newId(prefix: String): String = prefix + java.util.UUID.randomUUID().toString().take(12)

    /** "2026-11-16@1700@Campo 2": la stessa lezione resta riconoscibile anche se si cambia un altro orario. */
    fun key(start: Long, court: String): String {
        val c = Calendar.getInstance().apply { timeInMillis = start }
        return "%04d-%02d-%02d@%02d%02d@%s".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH), c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), court)
    }

    /** Tutte le lezioni della stagione, in ordine, dagli orari settimanali. */
    fun lessons(c: Course): List<Lesson> {
        val out = mutableListOf<Lesson>()
        var day = CourtPlans.dayStart(c.from)
        val last = CourtPlans.dayStart(c.to)
        var guard = 0
        while (day <= last && guard++ < 800) {
            val wd = CourtPlans.weekday(day)
            for (s in c.slots.filter { it.weekday == wd }) {
                val start = CourtPlans.at(day, s.startMin / 60, s.startMin % 60)
                out += Lesson(key(start, s.court), start, s.minutes, s.court)
            }
            day = CourtPlans.addDays(day, 1)
        }
        return out.sortedBy { it.start }
    }

    /** Le lezioni fatte (non annullate) fino a [now]. */
    fun held(c: Course, now: Long): List<Lesson> = lessons(c).filter { it.start <= now && it.key !in c.cancelled }

    /** La presenza di un allievo: lezioni a cui c'era su quelle fatte (solo quelle con l'appello). */
    fun attendanceOf(c: Course, student: String, now: Long): Pair<Int, Int> {
        val marked = held(c, now).filter { it.key in c.attendance }
        return marked.count { student in c.attendance[it.key].orEmpty() } to marked.size
    }

    /** Segna chi c'era a una lezione (sostituisce l'appello di prima). */
    fun mark(c: Course, key: String, present: Set<String>): Course = c.copy(attendance = c.attendance + (key to present))

    fun setCancelled(c: Course, key: String, cancelled: Boolean): Course = c.copy(cancelled = if (cancelled) c.cancelled + key else c.cancelled - key)

    /** La prossima lezione (non annullata) da [now] in poi. */
    fun next(c: Course, now: Long): Lesson? = lessons(c).firstOrNull { it.end > now && it.key !in c.cancelled }

    /** I mesi della stagione: (anno, mese 0-11), per la quota mensile. */
    fun months(c: Course): List<Pair<Int, Int>> {
        val out = mutableListOf<Pair<Int, Int>>()
        val cal = Calendar.getInstance().apply { timeInMillis = CourtPlans.dayStart(c.from); set(Calendar.DAY_OF_MONTH, 1) }
        val end = Calendar.getInstance().apply { timeInMillis = c.to }
        var guard = 0
        while ((cal.get(Calendar.YEAR) < end.get(Calendar.YEAR) || (cal.get(Calendar.YEAR) == end.get(Calendar.YEAR) && cal.get(Calendar.MONTH) <= end.get(Calendar.MONTH))) && guard++ < 36) {
            out += cal.get(Calendar.YEAR) to cal.get(Calendar.MONTH)
            cal.add(Calendar.MONTH, 1)
        }
        return out
    }

    /**
     * Addebita la quota agli allievi che non l'hanno ancora per quel periodo ([label]: "Corso SAT · novembre 2026"
     * o "Corso SAT · stagione"). Restituisce il registro e quanti addebiti nuovi.
     */
    fun charge(book: ClubBook, c: Course, people: List<PlayerProfile>, label: String, now: Long, due: Long? = null): Pair<ClubBook, Int> {
        if (c.feeCents <= 0) return book to 0
        var b = book
        var n = 0
        for (p in people.filter { it.id in c.students }) {
            val keys = setOf(p.id) + p.aliases
            if (b.charges.any { it.player in keys && it.label == label && !it.cancelled }) continue
            b = b.copy(charges = b.charges + Charge(Fees.newId("c"), p.id, p.name, null, label, c.feeCents, now, due, kind = FeeKind.COURSE))
            n++
        }
        return b to n
    }

    /**
     * I periodi non prenotabili del corso, uno per orario: il campo, il giorno della settimana e le ore, per tutta
     * la stagione. Così nel planning nessuno prenota sopra il corso (le sue lezioni sì).
     */
    fun blocks(c: Course): List<CourtBlock> = c.slots.filter { it.court.isNotBlank() }.mapIndexed { i, s ->
        CourtBlock(
            id = "course-${c.id}-$i", label = c.name, reason = BlockReason.COURSE, courts = listOf(s.court),
            from = CourtPlans.dayStart(c.from), to = CourtPlans.dayStart(c.to), weekdays = setOf(s.weekday),
            startMin = s.startMin, endMin = (s.startMin + s.minutes).coerceAtMost(24 * 60), course = c.id,
        )
    }

    /** Il corso è pieno (con un massimo di allievi). */
    fun full(c: Course): Boolean = c.maxStudents > 0 && c.students.size >= c.maxStudents
}
