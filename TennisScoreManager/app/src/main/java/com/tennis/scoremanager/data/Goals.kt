// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import kotlinx.serialization.Serializable

/** Cosa si vuole raggiungere: le stesse voci di Progressi, più quante partite e allenamenti. */
@Serializable
enum class GoalKind(val lowerIsBetter: Boolean = false, val perMatch: Boolean = true) {
    WINS, POINTS_WON, FIRST_SERVE, ACES, DOUBLE_FAULTS(lowerIsBetter = true), UNFORCED_ERRORS(lowerIsBetter = true),
    MATCHES_MONTH(perMatch = false), WORKOUTS_WEEK(perMatch = false),
}

/**
 * Un obiettivo: arrivare a [target] (o restarci sotto, per doppi falli ed errori) in media nelle ultime [window]
 * partite; per partite e allenamenti, quanti negli ultimi 30 o 7 giorni. [reachedAt]: la prima volta che ci si è
 * arrivati; [seen]: l'avviso è già stato visto.
 */
@Serializable
data class Goal(
    val id: String,
    val kind: GoalKind,
    val target: Double,
    val window: Int = 5,
    val createdAt: Long = 0,
    val reachedAt: Long? = null,
    val seen: Boolean = false,
)

object Goals {
    fun newId(): String = "g" + java.util.UUID.randomUUID().toString().take(12)

    /** A che punto si è: il valore di adesso (null = non ci sono ancora dati), da 0 a 1, raggiunto o no, su quante partite. */
    data class Status(val current: Double?, val progress: Float, val reached: Boolean, val count: Int)

    /** Il valore di una partita per quella voce; null se la partita non l'ha (statistiche non dettagliate). */
    fun value(kind: GoalKind, l: PlayerStats.MatchLine): Double? {
        val d = l.detail
        return when (kind) {
            GoalKind.WINS -> if (l.won) 100.0 else 0.0
            GoalKind.POINTS_WON -> l.pointsWonPct.toDouble()
            GoalKind.FIRST_SERVE -> d?.takeIf { it.servePoints > 0 }?.let { it.firstServesIn * 100.0 / it.servePoints }
            GoalKind.ACES -> d?.aces?.toDouble()
            GoalKind.DOUBLE_FAULTS -> d?.doubleFaults?.toDouble()
            GoalKind.UNFORCED_ERRORS -> d?.unforcedErrors?.toDouble()
            GoalKind.MATCHES_MONTH, GoalKind.WORKOUTS_WEEK -> null
        }
    }

    /**
     * [lines]: le partite finite di chi usa l'app, dalla più vecchia. Per le voci delle partite conta la media delle
     * ultime [Goal.window] che hanno quel dato, e si è arrivati solo con almeno [Goal.window] partite.
     */
    fun status(g: Goal, lines: List<PlayerStats.MatchLine>, workoutStarts: List<Long>, now: Long): Status {
        val day = 86_400_000L
        val (current, count, enough) = when (g.kind) {
            GoalKind.MATCHES_MONTH -> lines.count { (it.rec.endedAt ?: it.rec.updatedAt) >= now - 30 * day }.let { Triple(it.toDouble(), it, true) }
            GoalKind.WORKOUTS_WEEK -> workoutStarts.count { it >= now - 7 * day }.let { Triple(it.toDouble(), it, true) }
            else -> {
                val v = lines.mapNotNull { value(g.kind, it) }.takeLast(g.window.coerceAtLeast(1))
                Triple(v.takeIf { it.isNotEmpty() }?.average(), v.size, v.size >= g.window)
            }
        }
        if (current == null) return Status(null, 0f, false, 0)
        val reached = enough && if (g.kind.lowerIsBetter) current <= g.target + 1e-9 else current >= g.target - 1e-9
        val progress = when {
            g.kind.lowerIsBetter -> if (current <= g.target) 1f else (g.target / current).toFloat()
            g.target <= 0 -> 1f
            else -> (current / g.target).toFloat()
        }.coerceIn(0f, 1f).let { if (!enough && it >= 1f) 0.99f else it }
        return Status(current, progress, reached, count)
    }

    /** Gli obiettivi appena raggiunti prendono la data (la prima volta): così arriva l'avviso. */
    fun markReached(goals: List<Goal>, lines: List<PlayerStats.MatchLine>, workoutStarts: List<Long>, now: Long): List<Goal> =
        goals.map { g -> if (g.reachedAt == null && status(g, lines, workoutStarts, now).reached) g.copy(reachedAt = now) else g }

    /** Il valore come si legge: percentuali senza decimali, medie per partita con uno. */
    fun format(kind: GoalKind, v: Double): String = when (kind) {
        GoalKind.WINS, GoalKind.POINTS_WON, GoalKind.FIRST_SERVE -> "${Math.round(v)}%"
        GoalKind.MATCHES_MONTH, GoalKind.WORKOUTS_WEEK -> "${Math.round(v)}"
        else -> String.format(java.util.Locale.ROOT, "%.1f", v).removeSuffix(".0")
    }

    /** I traguardi che si propongono, per voce: da dove si parte nel menu. */
    fun choices(kind: GoalKind): List<Double> = when (kind) {
        GoalKind.WINS -> listOf(40.0, 50.0, 60.0, 70.0, 80.0)
        GoalKind.POINTS_WON -> listOf(45.0, 50.0, 55.0, 60.0)
        GoalKind.FIRST_SERVE -> listOf(50.0, 55.0, 60.0, 65.0, 70.0, 75.0)
        GoalKind.ACES -> listOf(1.0, 2.0, 3.0, 5.0)
        GoalKind.DOUBLE_FAULTS -> listOf(1.0, 2.0, 3.0, 4.0)
        GoalKind.UNFORCED_ERRORS -> listOf(5.0, 10.0, 15.0, 20.0)
        GoalKind.MATCHES_MONTH -> listOf(2.0, 4.0, 6.0, 8.0, 12.0)
        GoalKind.WORKOUTS_WEEK -> listOf(1.0, 2.0, 3.0, 4.0, 5.0)
    }
}
