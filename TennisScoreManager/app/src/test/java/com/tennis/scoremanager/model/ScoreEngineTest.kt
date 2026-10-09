// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoreEngineTest {

    private val P1 = Side.P1
    private val P2 = Side.P2

    private fun play(s: MatchState, vararg winners: Side): MatchState =
        winners.fold(s) { acc, w -> ScoreEngine.pointWonBy(acc, w).state }

    private fun game(s: MatchState, w: Side): MatchState = play(s, w, w, w, w)

    /** Porta il set sul punteggio indicato alternando i game (tiene conto di chi vince). */
    private fun games(s: MatchState, a: Int, b: Int): MatchState {
        var st = s
        var ga = 0
        var gb = 0
        while (ga < a || gb < b) {
            st = if (ga < a && (ga <= gb || gb >= b)) { ga++; game(st, P1) } else { gb++; game(st, P2) }
        }
        return st
    }

    @Test
    fun pointLabelsAndDeuce() {
        var s = ScoreEngine.initial(RulesConfig())
        s = play(s, P1)
        assertEquals("15", s.pointLabel(P1)); assertEquals("0", s.pointLabel(P2))
        s = play(s, P1, P2, P2, P2, P1)
        assertEquals("40", s.pointLabel(P1)); assertEquals("40", s.pointLabel(P2)); assertTrue(s.isDeuce)
        s = play(s, P2)
        assertEquals("AD", s.pointLabel(P2)); assertEquals("40", s.pointLabel(P1))
        s = play(s, P1)
        assertTrue(s.isDeuce)
        s = play(s, P1)
        val step = ScoreEngine.pointWonBy(s, P1)
        assertEquals(P1, step.transition!!.gameWinner)
        assertEquals(1, step.state.g1)
        assertEquals(0, step.state.pt1 + step.state.pt2)
    }

    @Test
    fun noAdDecidingPoint() {
        var s = ScoreEngine.initial(RulesConfig(noAd = true))
        s = play(s, P1, P1, P1, P2, P2, P2)
        assertTrue(s.isDeuce)
        val step = ScoreEngine.pointWonBy(s, P2)
        assertEquals(P2, step.transition!!.gameWinner)
    }

    @Test
    fun changeOfEndsAfterOddGamesOnly() {
        var s = ScoreEngine.initial(RulesConfig(p1StartsLeft = true))
        val changes = mutableListOf<Int>()
        repeat(9) { i ->
            val before = s
            val w = if (i % 2 == 0) P1 else P2
            var t: Transition? = null
            repeat(4) { val st = ScoreEngine.pointWonBy(s, w); s = st.state; t = st.transition }
            if (t!!.changeEnds) changes += before.g1 + before.g2 + 1
        }
        assertEquals(listOf(1, 3, 5, 7, 9), changes)
    }

    @Test
    fun firstGameFlagAndServiceAlternates() {
        var s = ScoreEngine.initial(RulesConfig(firstServer = P2))
        assertEquals(P2, s.server)
        s = play(s, P1, P1, P1)
        val step = ScoreEngine.pointWonBy(s, P1)
        assertTrue(step.transition!!.firstGameOfSet)
        assertTrue(step.transition!!.changeEnds)
        assertEquals(P1, step.state.server)
    }

    @Test
    fun setWonSixFourAndNextSetServer() {
        var s = ScoreEngine.initial(RulesConfig(firstServer = P1, p1StartsLeft = true))
        s = games(s, 5, 4)
        assertEquals(5, s.g1); assertEquals(4, s.g2)
        // 9 game giocati: serve il turno 9 (dispari) -> P2
        assertEquals(P2, s.server)
        val step = ScoreEngine.pointWonBy(play(s, P1, P1, P1), P1)
        val t = step.transition!!
        assertEquals(P1, t.setWinner)
        assertFalse("6-4 = 10 game: nessun cambio a fine set", t.changeEnds)
        assertEquals(listOf(SetScore(6, 4)), step.state.sets)
        // Il 10° game (turno 9) l'ha servito P2: il secondo set lo apre P1.
        assertEquals(P1, step.state.server)
        // Dopo il primo game del nuovo set si cambia campo.
        val g1 = ScoreEngine.pointWonBy(play(step.state, P1, P1, P1), P1)
        assertTrue(g1.transition!!.changeEnds)
    }

    @Test
    fun setWonSixThreeChangesEndsAtSetEnd() {
        var s = ScoreEngine.initial(RulesConfig())
        s = games(s, 5, 3)
        val step = ScoreEngine.pointWonBy(play(s, P1, P1, P1), P1)
        assertEquals(P1, step.transition!!.setWinner)
        assertTrue("6-3 = 9 game: cambio a fine set", step.transition!!.changeEnds)
    }

    @Test
    fun sevenFiveNeedsTwoGames() {
        var s = ScoreEngine.initial(RulesConfig())
        s = games(s, 5, 5)
        s = game(s, P1)
        assertEquals(6, s.g1); assertTrue(s.sets.isEmpty())
        val step = ScoreEngine.pointWonBy(play(s, P1, P1, P1), P1)
        assertEquals(SetScore(7, 5), step.state.sets.single())
    }

    @Test
    fun tiebreakAtSixAllServiceOrderAndChanges() {
        var s = ScoreEngine.initial(RulesConfig(firstServer = P1, p1StartsLeft = true))
        s = games(s, 5, 5)
        s = game(s, P1)
        val leftBefore = s.p1Left
        val tbStart = ScoreEngine.pointWonBy(play(s, P2, P2, P2), P2)
        assertTrue(tbStart.transition!!.tiebreakStarted)
        assertFalse("6-6: 12 game, nessun cambio campo", tbStart.transition!!.changeEnds)
        s = tbStart.state
        assertEquals(leftBefore, s.p1Left)
        assertTrue(s.inTiebreak)
        // Turno 12 (pari) -> serve chi ha iniziato il set: P1. Poi 2 punti a testa.
        val servers = mutableListOf<Side>()
        var t = s
        val changes = mutableListOf<Int>()
        repeat(12) { i ->
            servers += t.server
            val st = ScoreEngine.pointWonBy(t, if (i % 2 == 0) P1 else P2)
            if (st.transition!!.changeEnds) changes += st.state.pt1 + st.state.pt2
            t = st.state
        }
        assertEquals(listOf(P1, P2, P2, P1, P1, P2, P2, P1, P1, P2, P2, P1), servers)
        assertEquals(listOf(6, 12), changes)
        assertEquals("6", t.pointLabel(P1)); assertEquals("6", t.pointLabel(P2))
        // 8-6 chiude il tie-break e il set 7-6
        val a = ScoreEngine.pointWonBy(t, P1)
        assertNull(a.transition!!.setWinner)
        val b = ScoreEngine.pointWonBy(a.state, P1)
        assertEquals(P1, b.transition!!.setWinner)
        assertEquals(SetScore(7, 6, 8, 6), b.state.sets.single())
        assertTrue("fine tie-break: 13° game, cambio campo", b.transition!!.changeEnds)
        // Ha servito il primo punto del tie-break P1 -> nel set successivo serve P2.
        assertEquals(P2, b.state.server)
    }

    @Test
    fun tiebreakEndingOnMultipleOfSixChangesOnlyOnce() {
        var s = ScoreEngine.initial(RulesConfig(p1StartsLeft = true))
        s = games(s, 5, 5); s = game(s, P1); s = game(s, P2)
        val left = s.p1Left
        // 7-5 = 12 punti
        s = play(s, P1, P2, P1, P2, P1, P2, P1, P2, P1, P2, P1)
        assertEquals(!left, s.p1Left) // cambio al 6° punto
        val end = ScoreEngine.pointWonBy(s, P1)
        assertEquals(P1, end.transition!!.setWinner)
        assertTrue(end.transition!!.changeEnds)
        assertEquals("un solo cambio alla fine (non doppio)", left, end.state.p1Left)
    }

    @Test
    fun matchBestOfThree() {
        var s = ScoreEngine.initial(RulesConfig())
        s = games(s, 5, 0)
        s = game(s, P1)
        assertEquals(1, s.setsWon(P1))
        s = games(s, 0, 5); s = game(s, P2)
        assertEquals(1, s.setsWon(P2))
        s = games(s, 5, 0)
        val end = ScoreEngine.pointWonBy(play(s, P1, P1, P1), P1)
        assertEquals(P1, end.transition!!.matchWinner)
        assertTrue(end.state.isFinished)
        assertEquals(3, end.state.sets.size)
        // A partita finita i punti vengono ignorati.
        assertNull(ScoreEngine.pointWonBy(end.state, P2).transition)
    }

    @Test
    fun matchTiebreakToTenWithTwoClear() {
        var s = ScoreEngine.initial(RulesConfig(format = MatchFormat.TWO_SETS_MATCH_TIEBREAK))
        s = games(s, 5, 0); s = game(s, P1)
        s = games(s, 0, 5)
        val setEnd = ScoreEngine.pointWonBy(play(s, P2, P2, P2), P2)
        assertTrue(setEnd.transition!!.matchTiebreakStarted)
        s = setEnd.state
        assertEquals(TiebreakKind.MATCH, s.tiebreak)
        // 9-9 poi 11-9
        repeat(9) { s = play(s, P1, P2) }
        assertEquals(9, s.pt1); assertEquals(9, s.pt2)
        s = play(s, P2)
        assertNull(s.winner)
        s = play(s, P1, P1)
        assertNull(s.winner)
        val end = ScoreEngine.pointWonBy(s, P1)
        assertEquals(P1, end.transition!!.matchWinner)
        val last = end.state.sets.last()
        assertTrue(last.matchTiebreak)
        assertEquals(12, last.tb1); assertEquals(10, last.tb2)
        assertEquals(12, last.shown(P1))
    }

    @Test
    fun doublesRotationAndTiebreak() {
        val rules = RulesConfig(doubles = true, firstServer = P1, firstServerP1 = 1, firstServerP2 = 0)
        var s = ScoreEngine.initial(rules)
        val seq = mutableListOf<Pair<Side, Int>>()
        repeat(6) {
            seq += s.server to s.serverPlayer
            s = game(s, if (it % 2 == 0) P1 else P2)
        }
        assertEquals(listOf(P1 to 1, P2 to 0, P1 to 0, P2 to 1, P1 to 1, P2 to 0), seq)
        // dal 3-3 al 6-6 alternando i game
        repeat(6) { s = game(s, if (it % 2 == 0) P1 else P2) }
        assertEquals(6, s.g1); assertEquals(6, s.g2)
        assertTrue(s.inTiebreak)
        val tb = mutableListOf<Pair<Side, Int>>()
        repeat(5) { i ->
            tb += s.server to s.serverPlayer
            s = play(s, if (i % 2 == 0) P1 else P2)
        }
        // Turno 12 = stesso giocatore del game 1 (P1 #1), poi P2 #0, P2 #0, P1 #0, P1 #0
        assertEquals(listOf(P1 to 1, P2 to 0, P2 to 0, P1 to 0, P1 to 0), tb)
    }

    @Test
    fun doublesServeOrderEventOnlyAtSetStart() {
        val rules = RulesConfig(doubles = true)
        val events = mutableListOf<MatchEvent>(MatchEvent.ServeOrder(1, 1, 1))
        var s = ScoreEngine.replay(rules, events)
        assertEquals(1, s.order1); assertEquals(1, s.order2)
        events += MatchEvent.Point(Side.P1)
        events += MatchEvent.ServeOrder(1, 0, 0) // ignorato: il set è iniziato
        s = ScoreEngine.replay(rules, events)
        assertEquals(1, s.order1)
    }

    @Test
    fun replayUndoAcrossSetAndMatchEnd() {
        val rules = RulesConfig()
        val events = mutableListOf<MatchEvent>()
        fun add(w: Side, n: Int) = repeat(n) { events += MatchEvent.Point(w) }
        repeat(6) { add(P1, 4) }
        repeat(6) { add(P1, 4) }
        val final = ScoreEngine.replay(rules, events)
        assertEquals(P1, final.winner)
        val undone = ScoreEngine.replay(rules, events.dropLast(1))
        assertNull(undone.winner)
        assertEquals(5, undone.g1)
        assertEquals("40", undone.pointLabel(P1))
        assertEquals(1, undone.sets.size)
        // Undo oltre la fine del primo set
        val back = ScoreEngine.replay(rules, events.take(24 - 1))
        assertTrue(back.sets.isEmpty())
        assertEquals(5, back.g1)
    }

    @Test
    fun lookaheadSetAndMatchPoint() {
        var s = ScoreEngine.initial(RulesConfig())
        s = games(s, 5, 2)
        s = play(s, P1, P1, P1)
        assertEquals(P1, ScoreEngine.lookahead(s, P1)!!.setWinner)
        assertNull(ScoreEngine.lookahead(s, P1)!!.matchWinner)
    }

    @Test
    fun breakPoint() {
        val s = play(ScoreEngine.initial(RulesConfig(firstServer = P1)), P2, P2, P2)
        assertTrue(ScoreEngine.isBreakPoint(s))
        val t = play(ScoreEngine.initial(RulesConfig(firstServer = P1)), P1, P1, P1)
        assertFalse(ScoreEngine.isBreakPoint(t))
    }
}
