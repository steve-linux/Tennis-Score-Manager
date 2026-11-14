// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Un iscritto del corso come lo manda la segreteria: chi è e come contattarlo (al genitore se è minorenne). */
@Serializable
data class CourseStudent(
    val id: String,
    val name: String,
    val firstName: String = "",
    val lastName: String = "",
    val nickname: String = "",
    val phone: String = "",
    val email: String = "",
    val guardian: String = "",
    val guardianPhone: String = "",
    val consentAt: Long? = null,
    val ranking: String = "",
)

/**
 * La scheda corso: la segreteria la manda all'allenatore (un file .tsm su WhatsApp) con orari, campi, date e iscritti.
 * L'allenatore la apre con TSM e il corso compare tra i suoi; una scheda nuova dello stesso corso ([code]) sostituisce la
 * vecchia (iscritti e orari), le presenze segnate restano. Le presenze tornano alla segreteria con lo stesso [code].
 */
@Serializable
data class CourseCard(
    val tsm: String = CourseCards.CARD,
    val v: Int = 1,
    val code: String,
    val issuedAt: Long,
    val club: String = "",
    val office: String = "",
    /** Il corso senza quote: orari, date, iscritti (id della segreteria), presenze e lezioni annullate già segnate. */
    val course: Course,
    val students: List<CourseStudent> = emptyList(),
)

/** Il corso nel telefono dell'allenatore: l'ultima scheda ricevuta, le presenze che ha segnato lui e quando le ha mandate. */
@Serializable
data class CoachCourse(
    val card: CourseCard,
    /** Presenze e lezioni annullate segnate dall'allenatore (chiave della lezione, come in [Courses]). */
    val attendance: Map<String, Set<String>> = emptyMap(),
    val cancelled: Set<String> = emptySet(),
    /** Cosa si è fatto in ogni lezione (va anche nel diario degli allievi presenti). */
    val notes: Map<String, String> = emptyMap(),
    val changedAt: Long = 0,
    val sentAt: Long? = null,
    /** Arrivata dal server del circolo (il suo indirizzo): le presenze tornano da lì. */
    val via: String? = null,
    /** Corso dell'allenatore, fatto da lui e non dalla segreteria: si cambia qui e le presenze non si mandano a nessuno. */
    val personal: Boolean = false,
) {
    /** Il corso con le presenze dell'allenatore sopra quelle della segreteria. */
    val course: Course get() = card.course.copy(
        attendance = card.course.attendance + attendance,
        cancelled = card.course.cancelled + cancelled,
    )
    val toSend: Boolean get() = !personal && changedAt > 0 && (sentAt == null || sentAt < changedAt)
}

/** Le presenze che l'allenatore rimanda alla segreteria: lezione per lezione chi c'era, e le lezioni annullate. */
@Serializable
data class AttendanceReport(
    val tsm: String = CourseCards.ATTENDANCE,
    val v: Int = 1,
    val code: String,
    val course: String,
    val courseName: String = "",
    val coach: String = "",
    val attendance: Map<String, Set<String>> = emptyMap(),
    val cancelled: Set<String> = emptySet(),
    val sentAt: Long = 0,
)

/** Un allievo nuovo che l'allenatore propone alla segreteria (dopo una lezione di prova): i dati per iscriverlo. */
@Serializable
data class StudentProposal(
    val tsm: String = CourseCards.PROPOSAL,
    val v: Int = 1,
    val student: CourseStudent,
    val coach: String = "",
    /** Il corso che l'allenatore consiglia (Course.id della segreteria), se lo sa; e due righe per la segreteria. */
    val course: String? = null,
    val level: String = "",
    val note: String = "",
    val sentAt: Long = 0,
)

/** Com'è un file di presenze rispetto al corso della segreteria. */
enum class AttendanceStatus { READY, SAME, BAD_CODE, NOT_FOUND }

object CourseCards {
    const val CARD = "tsm-course-card"
    const val ATTENDANCE = "tsm-course-attendance"
    const val PROPOSAL = "tsm-student-proposal"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    fun encode(c: CourseCard): String = json.encodeToString(CourseCard.serializer(), c)
    fun decode(text: String): CourseCard? = runCatching { json.decodeFromString(CourseCard.serializer(), text) }.getOrNull()?.takeIf { it.tsm == CARD }
    fun encodeReport(r: AttendanceReport): String = json.encodeToString(AttendanceReport.serializer(), r)
    fun decodeReport(text: String): AttendanceReport? = runCatching { json.decodeFromString(AttendanceReport.serializer(), text) }.getOrNull()?.takeIf { it.tsm == ATTENDANCE }
    fun encodeProposal(p: StudentProposal): String = json.encodeToString(StudentProposal.serializer(), p)
    fun decodeProposal(text: String): StudentProposal? = runCatching { json.decodeFromString(StudentProposal.serializer(), text) }.getOrNull()?.takeIf { it.tsm == PROPOSAL }

    fun student(p: PlayerProfile?, id: String): CourseStudent =
        if (p == null) CourseStudent(id, id)
        else CourseStudent(
            p.id, p.name, p.firstName, p.lastName, p.nickname, p.phone, p.email,
            p.guardian.ifBlank { p.member?.guardian.orEmpty() }, p.guardianPhone.ifBlank { p.member?.guardianPhone.orEmpty() },
            p.consentAt, p.member?.ranking.orEmpty(),
        )

    /** La scheda del corso [c] (senza le quote, che restano alla segreteria). */
    fun card(c: Course, code: String, now: Long, club: String, office: String, find: (String) -> PlayerProfile?): CourseCard =
        CourseCard(
            code = code, issuedAt = now, club = club, office = office,
            course = c.copy(feeCents = 0, cardCode = code),
            students = c.students.map { student(find(it), it) },
        )

    /** Una scheda arrivata: sostituisce quella dello stesso corso (più recente vince), le presenze dell'allenatore restano. */
    fun receive(list: List<CoachCourse>, card: CourseCard): List<CoachCourse> {
        val old = list.firstOrNull { it.card.course.id == card.course.id }
        if (old != null && old.card.issuedAt > card.issuedAt) return list
        val updated = old?.copy(card = card) ?: CoachCourse(card)
        return list.filter { it.card.course.id != card.course.id } + updated
    }

    /** Le presenze da rimandare: tutte quelle segnate dall'allenatore (la segreteria tiene l'ultima per ogni lezione). */
    fun report(cc: CoachCourse, coach: String, now: Long): AttendanceReport =
        AttendanceReport(code = cc.card.code, course = cc.card.course.id, courseName = cc.card.course.name, coach = coach,
            attendance = cc.attendance, cancelled = cc.cancelled, sentAt = now)

    /** Il file di presenze rispetto ai corsi della segreteria. */
    fun check(r: AttendanceReport, courses: List<Course>): AttendanceStatus {
        val c = courses.firstOrNull { it.id == r.course } ?: return AttendanceStatus.NOT_FOUND
        if (c.cardCode != r.code) return AttendanceStatus.BAD_CODE
        return if (apply(c, r) == c) AttendanceStatus.SAME else AttendanceStatus.READY
    }

    /**
     * Le presenze nel corso della segreteria: per ogni lezione segnata vale quella dell'allenatore (solo iscritti del
     * corso); le lezioni annullate si aggiungono, e una lezione con le presenze non è più annullata.
     */
    fun apply(c: Course, r: AttendanceReport): Course {
        val att = r.attendance.mapValues { (_, v) -> v.filter { it in c.students }.toSet() }
        return c.copy(attendance = c.attendance + att, cancelled = (c.cancelled + r.cancelled) - att.keys)
    }

    fun fileName(prefix: String, name: String): String =
        "$prefix $name".replace(Regex("[\\\\/:*?\"<>|\\n]"), "_").take(80).trim() + "." + MatchCards.EXT
}
