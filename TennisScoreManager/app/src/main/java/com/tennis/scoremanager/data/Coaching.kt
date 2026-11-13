// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import kotlinx.serialization.Serializable

/** Una riga del diario di un allievo: cosa si è fatto quel giorno e cosa resta da fare. */
@Serializable
data class DiaryEntry(val id: String, val student: String, val at: Long, val text: String)

/**
 * Un obiettivo dell'allievo su un colpo: [targetPct] colpi dentro, sulla media delle ultime [window] sessioni di quel
 * colpo, entro [by] (facoltativo). [reachedAt] = quando l'ha raggiunto.
 */
@Serializable
data class StudentGoal(
    val id: String,
    val student: String,
    val stroke: TrainedStroke,
    val targetPct: Int,
    val window: Int = 3,
    val by: Long? = null,
    val createdAt: Long = 0,
    val reachedAt: Long? = null,
)

/** Una lezione in agenda: uno o più allievi, quando, quanto dura, dove. [done] = fatta (conta nel pacchetto). */
@Serializable
data class CoachLesson(
    val id: String,
    val start: Long,
    val minutes: Int = 60,
    val students: List<String> = emptyList(),
    val group: String? = null,
    val place: String = "",
    val note: String = "",
    val done: Boolean = false,
    val cancelled: Boolean = false,
)

/** Un pacchetto di lezioni pagato (o da pagare) da un allievo: [lessons] lezioni per [cents]. */
@Serializable
data class LessonPack(
    val id: String,
    val student: String,
    val lessons: Int,
    val cents: Int = 0,
    val createdAt: Long = 0,
    /** Quando è stato pagato; null = da pagare. */
    val paidAt: Long? = null,
)

/** Un gruppo di allievi (es. "Agonisti U14"): si allena, si avvisa e si mette in agenda insieme. */
@Serializable
data class StudentGroup(val id: String, val name: String, val students: List<String> = emptyList())

/** Tutto quello che l'allenatore tiene dei suoi allievi oltre alle sessioni: un file solo (coach_book.json). */
@Serializable
data class CoachBook(
    val diary: List<DiaryEntry> = emptyList(),
    val goals: List<StudentGoal> = emptyList(),
    val lessons: List<CoachLesson> = emptyList(),
    val packs: List<LessonPack> = emptyList(),
    val groups: List<StudentGroup> = emptyList(),
)

object Coaching {
    fun newId(prefix: String): String = prefix + System.currentTimeMillis().toString(36) + (1000..9999).random()

    /** Il diario dell'allievo, dal più recente. */
    fun diaryOf(book: CoachBook, student: String): List<DiaryEntry> = book.diary.filter { it.student == student }.sortedByDescending { it.at }

    /** Le lezioni dell'allievo (anche di gruppo), in ordine di tempo. */
    fun lessonsOf(book: CoachBook, student: String): List<CoachLesson> = book.lessons.filter { student in it.students }.sortedBy { it.start }

    /** Le prossime lezioni (da [now] in poi, non annullate), in ordine. */
    fun upcoming(lessons: List<CoachLesson>, now: Long): List<CoachLesson> = lessons.filter { !it.cancelled && it.start + it.minutes * 60_000L > now }.sortedBy { it.start }

    /** Lezioni comprate, fatte e rimaste; quante sono da pagare (pacchetti non pagati) e quanto. */
    data class Balance(val bought: Int, val used: Int, val dueCents: Int, val duePacks: Int) {
        val left: Int get() = bought - used
    }

    fun balance(book: CoachBook, student: String): Balance {
        val packs = book.packs.filter { it.student == student }
        val used = book.lessons.count { student in it.students && it.done && !it.cancelled }
        val due = packs.filter { it.paidAt == null }
        return Balance(packs.sumOf { it.lessons }, used, due.sumOf { it.cents }, due.size)
    }

    /** Dove è un obiettivo: la percentuale di colpi dentro sulle ultime sessioni del colpo (null = ancora niente). */
    data class GoalStatus(val pct: Int?, val sessions: Int) {
        fun reached(g: StudentGoal): Boolean = pct != null && sessions >= 1 && pct >= g.targetPct
    }

    fun goalStatus(g: StudentGoal, sessions: List<ServeSession>): GoalStatus {
        val mine = sessions.filter { it.student == g.student && it.stroke == g.stroke && it.serves.isNotEmpty() }
            .sortedByDescending { it.startedAt }.take(g.window)
        val total = mine.sumOf { ServeTraining.count(it.serves).total }
        val inside = mine.sumOf { ServeTraining.count(it.serves).inPlay }
        return GoalStatus(if (total == 0) null else inside * 100 / total, mine.size)
    }

    /** Gli obiettivi raggiunti adesso prendono la data (gli altri restano come sono). */
    fun markReached(book: CoachBook, sessions: List<ServeSession>, now: Long): CoachBook =
        book.copy(goals = book.goals.map { g -> if (g.reachedAt == null && goalStatus(g, sessions).reached(g)) g.copy(reachedAt = now) else g })

    /** Gli allievi da quanto non si vedono: l'ultima sessione o lezione fatta, null = mai. */
    fun lastSeen(book: CoachBook, sessions: List<ServeSession>, student: String): Long? =
        listOfNotNull(
            sessions.filter { it.student == student }.maxOfOrNull { it.startedAt },
            book.lessons.filter { student in it.students && it.done }.maxOfOrNull { it.start },
        ).maxOrNull()
}
