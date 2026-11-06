// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NewBallsTest {
    private val P1 = Side.P1
    private val P2 = Side.P2

    private fun game(s: MatchState, w: Side): Pair<MatchState, Transition> {
        var st = s
        var t: Transition? = null
        do { val step = ScoreEngine.pointWonBy(st, w); st = step.state; t = step.transition } while (t?.gameWinner == null && !st.isFinished)
        return st to t!!
    }

    @Test fun primaDopoSettePoiOgniNove() {
        assertTrue(NewBalls.dueAt(7, 7, 9))
        assertFalse(NewBalls.dueAt(8, 7, 9))
        assertTrue(NewBalls.dueAt(16, 7, 9))
        assertTrue(NewBalls.dueAt(25, 7, 9))
        assertFalse(NewBalls.dueAt(7, 0, 0))
    }

    @Test fun dopoIlSettimoGameSiCambiano() {
        var s = ScoreEngine.initial(RulesConfig())
        var calls = mutableListOf<Int>()
        var pending = false
        repeat(16) { i ->
            val (st, t) = game(s, if (i % 2 == 0) P1 else P2)
            s = st
            val (now, p) = NewBalls.check(s, t, 7, 9, pending)
            pending = p
            if (now) calls += NewBalls.gamesPlayed(s)
        }
        assertEquals(listOf(7, 16), calls)
    }

    @Test fun sulTiebreakSiRimandaAlSecondoGameDelSetDopo() {
        // 6-6 al 12° game: con 3/9 il cambio cadrebbe sul 12° (3, 12...), cioè all'inizio del tie-break.
        var s = ScoreEngine.initial(RulesConfig())
        var pending = false
        val calls = mutableListOf<Int>()
        repeat(12) { i ->
            val (st, t) = game(s, if (i % 2 == 0) P1 else P2)
            s = st
            val (now, p) = NewBalls.check(s, t, 3, 9, pending); pending = p
            if (now) calls += NewBalls.gamesPlayed(s)
        }
        assertTrue(s.inTiebreak)
        assertEquals("solo il terzo game; il dodicesimo aspetta", listOf(3), calls)
        assertTrue(pending)
        // Il tie-break e il primo game del set dopo: lì arriva il cambio rimandato.
        val (afterTb, tTb) = game(s, P1)
        val (n1, p1) = NewBalls.check(afterTb, tTb, 3, 9, pending)
        assertFalse(n1); assertTrue(p1)
        val (g1, t1) = game(afterTb, P2)
        val (n2, p2) = NewBalls.check(g1, t1, 3, 9, p1)
        assertTrue(n2); assertFalse(p2)
    }
}
