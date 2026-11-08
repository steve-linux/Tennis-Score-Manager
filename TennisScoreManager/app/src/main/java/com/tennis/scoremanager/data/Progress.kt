// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import com.tennis.scoremanager.ui.Strings
import kotlin.math.abs

/**
 * Progressi: gli stessi numeri allenamento dopo allenamento (o partita dopo partita), per vedere se migliorano.
 * Ogni voce confronta la media della prima metà del periodo con quella della seconda.
 */
object Progress {

    /**
     * Una voce: un valore per allenamento o partita, dal più vecchio (null = lì non c'è). [better]: true se più alto
     * è meglio, false se più basso è meglio, null se non si può dire (battito medio).
     */
    data class Metric(val label: String, val unit: String, val values: List<Double?>, val better: Boolean?, val decimals: Int = 0) {
        private val known: List<Double> get() = values.filterNotNull()

        /** Media della prima e della seconda metà dei valori che ci sono; null con meno di 2 valori. */
        val halves: Pair<Double, Double>?
            get() {
                val v = known
                if (v.size < 2) return null
                val first = v.take(v.size / 2)
                val second = v.drop(v.size / 2)
                return first.average() to second.average()
            }

        /** +1 migliorato, −1 peggiorato, 0 uguale o non si può dire. */
        val verdict: Int
            get() {
                val (a, b) = halves ?: return 0
                val goodUp = better ?: return 0
                if (abs(b - a) < 0.05 * maxOf(abs(a), 1.0)) return 0
                return if ((b > a) == goodUp) 1 else -1
            }

        fun format(v: Double): String = if (decimals == 0) "${Math.round(v)}" else String.format(java.util.Locale.ROOT, "%.${decimals}f", v)
    }

    data class Report(val title: String, val subtitle: String, val count: Int, val metrics: List<Metric>, val rows: List<List<String>>, val header: List<String>)

    private fun pct(a: Int, b: Int): Double? = if (b > 0) a * 100.0 / b else null

    /** Allenamenti dal più vecchio: durata, battito, calorie, HRV, recupero. */
    fun workouts(list: List<WorkoutRecord>, s: Strings, date: (Long) -> String): Report {
        val w = list.sortedBy { it.startedAt }
        val sums = w.map { HeartStats.summary(it.heart) }
        val metrics = listOf(
            Metric(s.workoutDuration, "min", w.map { it.durationS / 60.0 }, true),
            Metric(s.heartAvg, "bpm", sums.map { it?.avg?.toDouble() }, null),
            Metric(s.heartMax, "bpm", sums.map { it?.max?.toDouble() }, null),
            Metric(s.heartCalories, "kcal", sums.map { it?.kcal?.toDouble() }, true),
            Metric(s.workoutHrv, "ms", w.map { WorkoutStats.rmssd(it.rr) }, true),
            Metric(s.workoutRecovery, "bpm", w.map { WorkoutStats.finalRecovery(it)?.toDouble() }, true),
        ).filter { m -> m.values.any { it != null } }
        val rows = w.mapIndexed { i, r ->
            listOf(
                date(r.startedAt),
                Reports.duration(r.durationS * 1000L),
                sums[i]?.avg?.toString() ?: "–",
                sums[i]?.max?.toString() ?: "–",
                WorkoutStats.rmssd(r.rr)?.let { "${Math.round(it)}" } ?: "–",
                WorkoutStats.finalRecovery(r)?.let { "−$it" } ?: "–",
            )
        }
        val header = listOf(s.progressDate, s.workoutDuration, s.workoutAvgShort, s.workoutMaxShort, "HRV", s.workoutRecShort)
        val span = if (w.isEmpty()) "" else "${date(w.first().startedAt)} – ${date(w.last().startedAt)}"
        return Report(s.progressWorkouts, span, w.size, metrics, rows, header)
    }

    /** Partite finite di un giocatore, dalla più vecchia: vittorie, punti, servizio, ace, doppi falli, vincenti, errori. */
    fun matches(lines: List<PlayerStats.MatchLine>, player: String, s: Strings, date: (Long) -> String): Report {
        fun d(f: (MatchStats.SideTotals) -> Double?): List<Double?> = lines.map { l -> l.detail?.let(f) }
        val metrics = listOf(
            Metric(s.progressWins, "%", lines.map { if (it.won) 100.0 else 0.0 }, true),
            Metric(s.progressPointsWon, "%", lines.map { it.pointsWonPct.toDouble() }, true),
            Metric(s.statAces, "", d { it.aces.toDouble() }, true),
            Metric(s.statDoubleFaults, "", d { it.doubleFaults.toDouble() }, false),
            Metric(s.statFirstServeIn, "%", d { pct(it.firstServesIn, it.servePoints) }, true),
            Metric(s.statWinners, "", d { it.winners.toDouble() }, true),
            Metric(s.statUnforcedErrors, "", d { it.unforcedErrors.toDouble() }, false),
        ).filter { m -> m.values.count { it != null } >= 1 }
        val rows = lines.map { l ->
            val t = l.detail
            listOf(
                date(l.rec.startedAt ?: l.rec.updatedAt),
                if (l.won) s.progressWon else s.progressLost,
                "${l.pointsWonPct}%",
                t?.aces?.toString() ?: "–",
                t?.doubleFaults?.toString() ?: "–",
                t?.winners?.toString() ?: "–",
                t?.unforcedErrors?.toString() ?: "–",
            )
        }
        val header = listOf(s.progressDate, s.progressResult, s.progressPointsShort, "Ace", s.progressDfShort, s.progressWinnersShort, s.progressUeShort)
        val span = if (lines.isEmpty()) "" else "${date(lines.first().rec.startedAt ?: 0)} – ${date(lines.last().rec.startedAt ?: 0)}"
        return Report("${s.progressMatches}: $player", span, lines.size, metrics, rows, header)
    }
}
