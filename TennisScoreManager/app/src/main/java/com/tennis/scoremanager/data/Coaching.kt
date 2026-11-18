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
    /** Lezione fatta ma senza qualcuno: assente (la lezione conta lo stesso) o assente giustificato (da recuperare). */
    val absent: Set<String> = emptySet(),
    val excused: Set<String> = emptySet(),
    /** Lezione di recupero: non scala dal pacchetto, recupera un'assenza giustificata di chi c'è. */
    val recovery: Boolean = false,
) {
    fun present(student: String): Boolean = student in students && student !in absent && student !in excused
}

/** Una partita dell'allievo giocata fuori da questo telefono (un torneo, un campionato), scritta a mano. */
@Serializable
data class ManualMatch(
    val id: String,
    val student: String,
    val at: Long,
    val event: String = "",
    val opponent: String = "",
    val opponentRanking: String = "",
    /** Dal lato dell'allievo: "6-4 3-6 10-7". */
    val score: String = "",
    val won: Boolean = true,
)

/** Un torneo in programma per l'allievo. */
@Serializable
data class StudentEvent(val id: String, val student: String, val name: String, val at: Long, val place: String = "", val note: String = "")

/** La classifica dell'allievo da una certa data (4.4, 4.2…): la storia si vede nella scheda. */
@Serializable
data class RankingEntry(val id: String, val student: String, val at: Long, val ranking: String)

/** Una valutazione tecnica: un voto da 1 a 5 per aspetto (vedi [Coaching.ASPECTS]) e una nota. */
@Serializable
data class Evaluation(val id: String, val student: String, val at: Long, val scores: Map<String, Int> = emptyMap(), val note: String = "")

/** Un giorno di test fisici: i valori per test (vedi [Coaching.TESTS]). */
@Serializable
data class FitnessTest(val id: String, val student: String, val at: Long, val values: Map<String, Double> = emptyMap())

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
    /** Il numero della ricevuta ("2026/003"), dato quando lo si segna pagato. */
    val receipt: String? = null,
)

/** Un gruppo di allievi (es. "Agonisti U14"): si allena, si avvisa e si mette in agenda insieme. */
@Serializable
data class StudentGroup(val id: String, val name: String, val students: List<String> = emptyList())

/** Perché l'allenatore non c'è in quei giorni. */
enum class BusyKind { BUSY, VACATION, TOURNAMENT, SICK, OTHER }

/** Un impegno dell'allenatore: dal giorno [from] al giorno [to] compresi (inizio del giorno), per vedere se le lezioni ci cadono. */
@Serializable
data class CoachBusy(val id: String, val from: Long, val to: Long, val kind: BusyKind = BusyKind.BUSY, val note: String = "")

/** Una lezione (privata o di un corso) che cade in un giorno occupato. */
data class BusyClash(val start: Long, val title: String, val busy: CoachBusy, val courseId: String? = null, val lessonId: String? = null)

/** Tutto quello che l'allenatore tiene dei suoi allievi oltre alle sessioni: un file solo (coach_book.json). */
@Serializable
data class CoachBook(
    val diary: List<DiaryEntry> = emptyList(),
    val goals: List<StudentGoal> = emptyList(),
    val lessons: List<CoachLesson> = emptyList(),
    val packs: List<LessonPack> = emptyList(),
    val groups: List<StudentGroup> = emptyList(),
    /** I corsi della scuola tennis ricevuti dalla segreteria (scheda corso) con le presenze segnate qui. */
    val courses: List<CoachCourse> = emptyList(),
    val manualMatches: List<ManualMatch> = emptyList(),
    val events: List<StudentEvent> = emptyList(),
    val rankings: List<RankingEntry> = emptyList(),
    val evaluations: List<Evaluation> = emptyList(),
    val tests: List<FitnessTest> = emptyList(),
    /** I giorni in cui l'allenatore è occupato (impegni, ferie, tornei…). */
    val busy: List<CoachBusy> = emptyList(),
)

object Coaching {
    fun newId(prefix: String): String = prefix + System.currentTimeMillis().toString(36) + (1000..9999).random()

    /** Il diario dell'allievo, dal più recente. */
    fun diaryOf(book: CoachBook, student: String): List<DiaryEntry> = book.diary.filter { it.student == student }.sortedByDescending { it.at }

    /** Le lezioni dell'allievo (anche di gruppo), in ordine di tempo. */
    fun lessonsOf(book: CoachBook, student: String): List<CoachLesson> = book.lessons.filter { student in it.students }.sortedBy { it.start }

    /** Le prossime lezioni (da [now] in poi, non annullate), in ordine. */
    /** L'impegno del giorno di [t], se c'è. */
    fun busyOn(busy: List<CoachBusy>, t: Long): CoachBusy? {
        val d = CourtPlans.dayStart(t)
        return busy.firstOrNull { d >= CourtPlans.dayStart(it.from) && d <= CourtPlans.dayStart(it.to) }
    }

    /** Le lezioni da adesso in poi (private e dei corsi, non annullate) che cadono in un giorno occupato, in ordine. */
    fun busyClashes(book: CoachBook, now: Long, names: (List<String>) -> String = { it.joinToString(", ") }): List<BusyClash> {
        if (book.busy.isEmpty()) return emptyList()
        val out = mutableListOf<BusyClash>()
        for (l in book.lessons) if (!l.cancelled && !l.done && l.start >= now) busyOn(book.busy, l.start)?.let { out += BusyClash(l.start, names(l.students), it, lessonId = l.id) }
        for (cc in book.courses) {
            val co = cc.course
            for (les in Courses.lessons(co)) if (les.start >= now && les.key !in co.cancelled && les.key !in co.attendance)
                busyOn(book.busy, les.start)?.let { out += BusyClash(les.start, co.name, it, courseId = co.id) }
        }
        return out.sortedBy { it.start }
    }

    fun upcoming(lessons: List<CoachLesson>, now: Long): List<CoachLesson> = lessons.filter { !it.cancelled && it.start + it.minutes * 60_000L > now }.sortedBy { it.start }

    /**
     * Lezioni comprate, fatte (contano anche le assenze non giustificate) e rimaste; quante sono da pagare (pacchetti
     * non pagati) e quanto; assenze e recuperi ancora da fare (assenze giustificate meno recuperi fatti).
     */
    data class Balance(val bought: Int, val used: Int, val dueCents: Int, val duePacks: Int, val absences: Int = 0, val toRecover: Int = 0) {
        val left: Int get() = bought - used
    }

    fun balance(book: CoachBook, student: String): Balance {
        val packs = book.packs.filter { it.student == student }
        val mine = book.lessons.filter { student in it.students && it.done && !it.cancelled }
        val used = mine.count { !it.recovery && student !in it.excused }
        val excused = mine.count { !it.recovery && student in it.excused }
        val recovered = mine.count { it.recovery && it.present(student) }
        val due = packs.filter { it.paidAt == null }
        return Balance(packs.sumOf { it.lessons }, used, due.sumOf { it.cents }, due.size,
            absences = mine.count { student in it.absent || student in it.excused }, toRecover = (excused - recovered).coerceAtLeast(0))
    }

    /** Il numero della prossima ricevuta dell'anno di [at]: "2026/001", "2026/002"… (una serie per anno). */
    fun nextReceipt(book: CoachBook, at: Long): String {
        val year = java.util.Calendar.getInstance().apply { timeInMillis = at }.get(java.util.Calendar.YEAR)
        val n = book.packs.mapNotNull { it.receipt }.filter { it.startsWith("$year/") }.mapNotNull { it.substringAfter('/').toIntOrNull() }.maxOrNull() ?: 0
        return "$year/" + (n + 1).toString().padStart(3, '0')
    }

    /** Gli aspetti della valutazione tecnica, nell'ordine in cui si mostrano. */
    val ASPECTS = listOf("serve", "forehand", "backhand", "volley", "movement", "tactics", "mental")

    /** I test fisici: chiave e se un valore più basso è meglio (tempi) o più alto (salti, livelli). */
    val TESTS = listOf("sprint" to true, "spider" to true, "jump" to false, "yoyo" to false)

    /** La classifica di adesso: l'ultima scritta (o arrivata con una scheda corso). */
    fun ranking(book: CoachBook, student: String): String? = book.rankings.filter { it.student == student }.maxByOrNull { it.at }?.ranking

    fun evaluationsOf(book: CoachBook, student: String): List<Evaluation> = book.evaluations.filter { it.student == student }.sortedBy { it.at }

    fun average(e: Evaluation): Double? = e.scores.values.takeIf { it.isNotEmpty() }?.average()

    /** Come va l'allievo: la media dell'ultima valutazione meno quella di prima (null con meno di due valutazioni). */
    fun trend(book: CoachBook, student: String): Double? {
        val ev = evaluationsOf(book, student).mapNotNull { average(it) }
        return if (ev.size < 2) null else ev.last() - ev[ev.size - 2]
    }

    fun testsOf(book: CoachBook, student: String): List<FitnessTest> = book.tests.filter { it.student == student }.sortedBy { it.at }

    /** L'ultimo valore di un test e quello di prima (per vedere se migliora). */
    fun lastTwo(tests: List<FitnessTest>, key: String): Pair<Double?, Double?> {
        val v = tests.mapNotNull { it.values[key] }
        return v.lastOrNull() to v.getOrNull(v.size - 2)
    }

    /** Il riepilogo di un mese ([from], [to)): lezioni private, allievi, incassi, lezioni dei corsi e presenze. */
    data class Month(
        val privateLessons: Int, val privateMinutes: Int, val students: Int, val paidCents: Int,
        val courseLessons: Int, val courseMinutes: Int, val presencePct: Int?,
    )

    fun month(book: CoachBook, from: Long, to: Long): Month {
        val priv = book.lessons.filter { it.done && !it.cancelled && it.start in from until to }
        val paid = book.packs.filter { it.paidAt != null && it.paidAt in from until to }.sumOf { it.cents }
        var held = 0; var minutes = 0; var present = 0; var expected = 0
        for (cc in book.courses) {
            val co = cc.course
            for (l in Courses.lessons(co).filter { it.start in from until to && it.key !in co.cancelled && it.key in co.attendance }) {
                held++; minutes += l.minutes
                present += co.attendance[l.key].orEmpty().size; expected += co.students.size
            }
        }
        return Month(priv.size, priv.sumOf { it.minutes }, priv.flatMap { it.students }.toSet().size, paid, held, minutes,
            if (expected == 0) null else present * 100 / expected)
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
            book.lessons.filter { it.done && it.present(student) }.maxOfOrNull { it.start },
            book.courses.flatMap { cc -> Courses.lessons(cc.course).filter { l -> student in cc.course.attendance[l.key].orEmpty() }.map { it.start } }.maxOrNull(),
        ).maxOrNull()
}
