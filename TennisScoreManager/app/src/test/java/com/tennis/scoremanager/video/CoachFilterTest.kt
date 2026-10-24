// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.video

import com.tennis.scoremanager.model.RulesConfig
import com.tennis.scoremanager.model.Side
import org.junit.Assert.assertEquals
import org.junit.Test

class CoachFilterTest {
    private fun p(winner: Int, server: Int, kind: String? = null, second: Boolean = false, pressure: String? = null) =
        PointMark(0, "", winner, kind, secondServe = second, pressure = pressure, server = server)

    private val pts = listOf(
        p(0, 0, "ACE"),                              // 0: G1 serve, ace
        p(1, 0, "DOUBLE_FAULT", second = true),      // 1: G1 doppio fallo
        p(0, 0, second = true),                      // 2: G1 vince sulla seconda
        p(1, 1, "WINNER", pressure = "BREAK_POINT"), // 3: G2 serve, salva... vince la palla break
        p(0, 1, "UNFORCED_ERROR"),                   // 4: G1 vince in risposta su errore di G2
    )

    @Test
    fun byPlayerServeAndOutcome() {
        assertEquals(listOf(0, 1, 2), CoachFilter.matching(pts, null, CoachFilter(player = 0, serving = true)))
        assertEquals(listOf(1, 2), CoachFilter.matching(pts, null, CoachFilter(player = 0, serving = true, secondServe = true)))
        assertEquals(listOf(2), CoachFilter.matching(pts, null, CoachFilter(player = 0, serving = true, secondServe = true, won = true)))
        assertEquals(listOf(4), CoachFilter.matching(pts, null, CoachFilter(player = 0, serving = false, won = true)))
    }

    @Test
    fun byKindAndSituation() {
        assertEquals(listOf(0, 3), CoachFilter.matching(pts, null, CoachFilter(kinds = setOf("ACE", "WINNER"))))
        assertEquals(listOf(3), CoachFilter.matching(pts, null, CoachFilter(situation = "BREAK")))
        assertEquals(pts.indices.toList(), CoachFilter.matching(pts, null, CoachFilter()))
    }

    @Test
    fun serverFromRulesWhenMissing() {
        // Segni senza "server": si ricalcola. Primo game servito da G1, il secondo da G2.
        val rules = RulesConfig(firstServer = Side.P1)
        val raw = mutableListOf<PointMark>()
        repeat(5) {
            val after = PointMarks.stateAfter(rules, raw + PointMark(0, "", 0), raw.size + 1)
            raw += PointMarks.mark(it * 1_000L, after, Side.P1, null).copy(server = -1)
        }
        assertEquals(listOf(4), CoachFilter.matching(raw, rules, CoachFilter(player = 1, serving = true)))
    }
}
