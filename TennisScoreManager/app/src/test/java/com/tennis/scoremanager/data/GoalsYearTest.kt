// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import com.tennis.scoremanager.model.RulesConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class GoalsYearTest {
    private fun at(y: Int, m: Int, d: Int) = Calendar.getInstance().apply { set(y, m, d, 18, 0, 0) }.timeInMillis

    private fun line(t: Long, won: Boolean, opponent: String = "Mario", dfs: Int? = null, firstIn: Int = 0, served: Int = 0, aces: Int = 0) = PlayerStats.MatchLine(
        MatchRecord(id = "m$t", setup = SetupData(p1a = "Stefano", p1aId = "me", p2a = opponent), options = MatchOptions(), rules = RulesConfig(),
            startedAt = t, endedAt = t + 3_600_000L, updatedAt = t, finished = true, clockMs = 90 * 60_000L),
        won, if (won) 55 else 45,
        dfs?.let { MatchStats.SideTotals(doubleFaults = it, firstServesIn = firstIn, servePoints = served, aces = aces) },
    )

    @Test fun firstServeOverTheLastMatches() {
        val now = at(2026, 10, 30)
        val g = Goal("g", GoalKind.FIRST_SERVE, target = 60.0, window = 3)
        // Le prime due partite non contano: si guardano le ultime tre (50, 60, 70 = 60%).
        val lines = listOf(30, 40, 50, 60, 70).mapIndexed { i, pct -> line(at(2026, 10, i + 1), true, dfs = 2, firstIn = pct, served = 100) }
        val st = Goals.status(g, lines, emptyList(), now)
        assertEquals(60.0, st.current!!, 1e-9)
        assertTrue(st.reached)
        // Con due partite sole no, anche se la media c'è: ne servono tre.
        assertFalse(Goals.status(g, lines.take(2), emptyList(), now).reached)
        assertEquals(0.99f, Goals.status(g.copy(target = 30.0), lines.take(2), emptyList(), now).progress)
    }

    @Test fun lowerIsBetterForDoubleFaults() {
        val now = at(2026, 10, 30)
        val g = Goal("g", GoalKind.DOUBLE_FAULTS, target = 2.0, window = 2)
        val many = listOf(line(at(2026, 10, 1), true, dfs = 4), line(at(2026, 10, 2), true, dfs = 4))
        assertEquals(0.5f, Goals.status(g, many, emptyList(), now).progress)
        val few = many + listOf(line(at(2026, 10, 3), true, dfs = 1), line(at(2026, 10, 4), true, dfs = 2))
        assertTrue(Goals.status(g, few, emptyList(), now).reached)
        // Senza statistiche dettagliate non c'è il dato.
        assertNull(Goals.status(g, listOf(line(at(2026, 10, 1), true)), emptyList(), now).current)
    }

    @Test fun matchesAndWorkoutsCountRecentOnes() {
        val now = at(2026, 10, 30)
        val lines = listOf(line(at(2026, 8, 1), true), line(at(2026, 10, 20), true), line(at(2026, 10, 25), false))
        assertEquals(2.0, Goals.status(Goal("g", GoalKind.MATCHES_MONTH, 2.0), lines, emptyList(), now).current!!, 0.0)
        val w = listOf(at(2026, 10, 26), at(2026, 10, 29), at(2026, 10, 10))
        val st = Goals.status(Goal("g", GoalKind.WORKOUTS_WEEK, 3.0), emptyList(), w, now)
        assertEquals(2.0, st.current!!, 0.0)
        assertFalse(st.reached)
        // Appena raggiunto prende la data, una volta sola.
        val marked = Goals.markReached(listOf(Goal("a", GoalKind.MATCHES_MONTH, 2.0), Goal("b", GoalKind.MATCHES_MONTH, 5.0)), lines, w, now)
        assertEquals(listOf(now, null), marked.map { it.reachedAt })
        assertEquals(marked, Goals.markReached(marked, lines, w, now + 1000))
    }

    @Test fun myYear() {
        val lines = listOf(
            line(at(2025, 11, 20), true),
            line(at(2026, 0, 10), true), line(at(2026, 0, 17), true), line(at(2026, 0, 24), false, "Luca"),
            line(at(2026, 4, 2), true), line(at(2026, 4, 9), false), line(at(2026, 4, 16), true, dfs = 1, aces = 4),
        )
        assertEquals(listOf(2026, 2025), YearReview.years(lines))
        val y = YearReview.of(2026, lines, "me", listOf(at(2026, 2, 1), at(2025, 1, 1)))
        assertEquals(6, y.played)
        assertEquals(4, y.won)
        assertEquals(67, y.winPct)
        assertEquals(9 * 60L, y.minutes)
        assertEquals("Mario", y.rival)
        assertEquals(4 to 1, y.rivalWon to y.rivalLost)
        assertEquals(2, y.streak)
        assertEquals(4, y.aces)
        assertEquals(1, y.workouts)
        // Gennaio e maggio pari (3 e 3): vince il primo.
        assertEquals(0, y.bestMonth)
    }
}
