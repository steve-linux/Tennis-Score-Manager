// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Un obiettivo come lo vede l'allievo: colpo, percentuale voluta, a che punto è, raggiunto quando. */
@Serializable
data class PcGoal(val stroke: TrainedStroke, val target: Int, val now: Int? = null, val reachedAt: Long? = null)

/**
 * La scheda progressi che l'allenatore manda all'allievo (un file .tsm): valutazioni tecniche, test fisici, obiettivi,
 * classifica, tornei in programma, prossime lezioni e lezioni rimaste. Niente diario né note dell'allenatore, che
 * restano sue. L'allievo la apre con TSM e la ritrova in Progressi; una scheda più nuova sostituisce la vecchia.
 */
@Serializable
data class ProgressCard(
    val tsm: String = ProgressCards.KIND,
    val v: Int = 1,
    val issuedAt: Long,
    val coach: String = "",
    val club: String = "",
    val student: String,
    val studentName: String,
    val evaluations: List<Evaluation> = emptyList(),
    val tests: List<FitnessTest> = emptyList(),
    val goals: List<PcGoal> = emptyList(),
    val rankings: List<RankingEntry> = emptyList(),
    val events: List<StudentEvent> = emptyList(),
    val nextLessons: List<Long> = emptyList(),
    val lessonsLeft: Int? = null,
)

object ProgressCards {
    const val KIND = "tsm-progress-card"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    fun encode(c: ProgressCard): String = json.encodeToString(ProgressCard.serializer(), c)
    fun decode(text: String): ProgressCard? = runCatching { json.decodeFromString(ProgressCard.serializer(), text) }.getOrNull()?.takeIf { it.tsm == KIND }

    /** La scheda di [student] da quello che l'allenatore ha nel telefono. */
    fun build(book: CoachBook, sessions: List<ServeSession>, student: String, name: String, coach: String, club: String, now: Long): ProgressCard {
        val bal = Coaching.balance(book, student)
        return ProgressCard(
            issuedAt = now, coach = coach, club = club, student = student, studentName = name,
            evaluations = Coaching.evaluationsOf(book, student).takeLast(6),
            tests = Coaching.testsOf(book, student).takeLast(6),
            goals = book.goals.filter { it.student == student }.map { g -> PcGoal(g.stroke, g.targetPct, Coaching.goalStatus(g, sessions).pct, g.reachedAt) },
            rankings = book.rankings.filter { it.student == student }.sortedBy { it.at }.takeLast(10),
            events = book.events.filter { it.student == student && it.at >= now - 86_400_000L }.sortedBy { it.at },
            nextLessons = Coaching.upcoming(Coaching.lessonsOf(book, student), now).take(5).map { it.start },
            lessonsLeft = bal.left.takeIf { bal.bought > 0 },
        )
    }

    fun fileName(prefix: String, name: String): String =
        "$prefix $name".replace(Regex("[\\\\/:*?\"<>|\\n]"), "_").take(80).trim() + "." + MatchCards.EXT
}
