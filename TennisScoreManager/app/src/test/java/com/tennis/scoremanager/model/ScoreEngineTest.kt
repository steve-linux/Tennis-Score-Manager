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

    /** Vince [n] set di fila 6-0 per [w]. */
    private fun sets(s: MatchState, w: Side, n: Int): MatchState = (1..n).fold(s) { acc, _ -> (1..6).fold(acc) { g, _ -> game(g, w) } }

    @Test
    fun bestOfFiveNeedsThreeSets() {
        var s = sets(ScoreEngine.initial(RulesConfig(format = MatchFormat.BEST_OF_FIVE)), P1, 2)
        assertFalse(s.isFinished)
        s = sets(s, P2, 2)
        assertEquals(4, s.sets.size)
        assertFalse(s.isFinished)
        s = sets(s, P1, 1)
        assertEquals(P1, s.winner)
        assertEquals(5, s.sets.size)
    }

    @Test
    fun proSetToEightWithTiebreakAtEightAll() {
        val r = RulesConfig(format = MatchFormat.PRO_SET, gamesPerSet = 8)
        var s = games(ScoreEngine.initial(r), 7, 7)
        assertFalse(s.inTiebreak)
        s = game(s, P1)
        assertFalse(s.inTiebreak)
        s = game(s, P2)
        assertTrue(s.inTiebreak)
        s = play(s, *Array(7) { P2 })
        assertEquals(P2, s.winner)
        assertEquals(9, s.sets.single().g2)
        // Un solo set: vinto 8-6 la partita è finita.
        assertEquals(P1, games(ScoreEngine.initial(r), 8, 6).winner)
    }

    @Test
    fun shortSetsToFourWithTiebreakAtFourAll() {
        val r = RulesConfig(gamesPerSet = 4)
        assertEquals(1, games(ScoreEngine.initial(r), 4, 2).sets.size)
        val s = games(ScoreEngine.initial(r), 4, 4)
        assertTrue(s.inTiebreak)
        assertEquals(0, s.sets.size)
    }

    @Test
    fun setsWithoutTiebreakGoOnUntilTwoGamesAhead() {
        val r = RulesConfig(setTiebreak = false)
        var s = games(ScoreEngine.initial(r), 6, 6)
        assertFalse(s.inTiebreak)
        s = game(s, P1)
        assertEquals(0, s.sets.size)
        s = game(s, P1)
        assertEquals(1, s.sets.size)
        assertEquals(8, s.sets.single().g1)
        assertEquals(6, s.sets.single().g2)
        assertNull(s.sets.single().tb1)
    }

    @Test
    fun matchTiebreakOnly() {
        var s = ScoreEngine.initial(RulesConfig(format = MatchFormat.MATCH_TIEBREAK_ONLY))
        assertEquals(TiebreakKind.MATCH, s.tiebreak)
        s = play(s, *Array(9) { P1 }, *Array(9) { P2 })
        assertFalse(s.isFinished)
        s = play(s, P1, P1)
        assertEquals(P1, s.winner)
        assertTrue(s.sets.single().matchTiebreak)
        assertEquals(11, s.sets.single().tb1)
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
    fun starPointTwoAdvantagesThenDecidingPoint() {
        var s = ScoreEngine.initial(RulesConfig(starPoint = true))
        s = play(s, P1, P1, P1, P2, P2, P2)
        assertTrue(s.isDeuce); assertFalse(s.isDecidingPoint)
        // Primo vantaggio perso, secondo vantaggio perso: terza parità = star point.
        s = play(s, P1, P2, P2, P1)
        assertTrue(s.isDeuce); assertTrue(s.isDecidingPoint)
        assertEquals(0, s.g1 + s.g2)
        val step = ScoreEngine.pointWonBy(s, P2)
        assertEquals(P2, step.transition!!.gameWinner)
        assertEquals(1, step.state.g2)
    }

    @Test
    fun starPointAdvantageStillWinsTheGame() {
        var s = ScoreEngine.initial(RulesConfig(starPoint = true))
        s = play(s, P1, P1, P1, P2, P2, P2, P1, P2)
        // Seconda parità (4-4), poi secondo vantaggio a P1: vincendo il punto dopo prende il game.
        s = play(s, P1)
        assertEquals("AD", s.pointLabel(P1))
        assertFalse(s.isDecidingPoint)
        val step = ScoreEngine.pointWonBy(s, P1)
        assertEquals(P1, step.transition!!.gameWinner)
    }

    @Test
    fun noAdWinsOverStarPoint() {
        var s = ScoreEngine.initial(RulesConfig(noAd = true, starPoint = true))
        s = play(s, P1, P1, P1, P2, P2, P2)
        assertTrue(s.isDecidingPoint)
        assertEquals(P1, ScoreEngine.pointWonBy(s, P1).transition!!.gameWinner)
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

    @Test fun ritiroChiudeColPunteggioDelMomento() {
        var s = ScoreEngine.initial(RulesConfig())
        repeat(6) { s = game(s, P1) }
        repeat(2) { s = game(s, P1) }
        s = game(s, P2)
        s = play(s, P1)
        val after = ScoreEngine.apply(s, MatchEvent.Retired(P1)).state
        assertEquals(P2, after.winner)
        assertEquals(P1, after.retired)
        assertTrue(after.isFinished)
        assertEquals(2, after.sets.size)
        assertEquals(6 to 0, after.sets[0].g1 to after.sets[0].g2)
        assertEquals(2 to 1, after.sets[1].g1 to after.sets[1].g2)
        assertTrue(after.sets[1].partial)
        assertEquals("il set interrotto non è vinto da nessuno", 0, after.setsWon(P2))
        assertEquals(1, after.setsWon(P1))
        assertEquals("dopo il ritiro non si segna più", after, ScoreEngine.pointWonBy(after, P1).state)
    }

    @Test fun ritiroAInizioSetNonInventaUnSet() {
        var s = ScoreEngine.initial(RulesConfig())
        repeat(6) { s = game(s, P1) }
        assertEquals(1, ScoreEngine.apply(s, MatchEvent.Retired(P2)).state.sets.size)
        assertEquals(0, ScoreEngine.apply(ScoreEngine.initial(RulesConfig()), MatchEvent.Retired(P2)).state.sets.size)
    }

    @Test fun ritiroSiRigiocaESiAnnulla() {
        val rules = RulesConfig()
        val events = List(6) { MatchEvent.Point(P1) } + MatchEvent.Retired(P2)
        assertEquals(P2, ScoreEngine.replay(rules, events).retired)
        assertEquals(null, ScoreEngine.replay(rules, events.dropLast(1)).retired)
        assertEquals(1, ScoreEngine.replay(rules, events.dropLast(1)).games(P1))
    }

    @Test fun ritiroNelMatchTiebreakTieneIPunti() {
        val rules = RulesConfig(format = MatchFormat.MATCH_TIEBREAK_ONLY)
        val s = play(ScoreEngine.initial(rules), P1, P1, P2)
        val set = ScoreEngine.apply(s, MatchEvent.Retired(P2)).state.sets.single()
        assertEquals(2 to 1, set.tb1 to set.tb2)
        assertTrue(set.partial && set.matchTiebreak)
    }

    private fun pen(s: MatchState, side: Side, kind: PenaltyKind) = ScoreEngine.apply(s, MatchEvent.Penalty(side, kind)).state

    @Test fun condottaAvvertimentoPoiPuntoPoiGame() {
        var s = ScoreEngine.initial(RulesConfig(firstServer = P1))
        assertEquals(PenaltyOutcome.WARNING, ScoreEngine.penaltyOutcome(s, P1, PenaltyKind.CODE))
        s = pen(s, P1, PenaltyKind.CODE)
        assertEquals(0 to 0, s.pt1 to s.pt2)
        assertEquals(PenaltyOutcome.POINT, ScoreEngine.penaltyOutcome(s, P1, PenaltyKind.CODE))
        s = pen(s, P1, PenaltyKind.CODE)
        assertEquals(0 to 1, s.pt1 to s.pt2)
        assertEquals(PenaltyOutcome.GAME, ScoreEngine.penaltyOutcome(s, P1, PenaltyKind.CODE))
        s = pen(s, P1, PenaltyKind.CODE)
        assertEquals("il game va all'avversario", 0 to 1, s.g1 to s.g2)
        assertEquals(0 to 0, s.pt1 to s.pt2)
        assertEquals(3, s.penalties(P1, PenaltyKind.CODE))
        assertEquals("i richiami dell'altro sono a parte", PenaltyOutcome.WARNING, ScoreEngine.penaltyOutcome(s, P2, PenaltyKind.CODE))
    }

    @Test fun tempoChiServePerdeIlServizioChiRispondeIlPunto() {
        var s = ScoreEngine.initial(RulesConfig(firstServer = P1))
        s = pen(s, P1, PenaltyKind.TIME)
        s = pen(s, P2, PenaltyKind.TIME)
        assertEquals(PenaltyOutcome.SERVE, ScoreEngine.penaltyOutcome(s, P1, PenaltyKind.TIME))
        val served = pen(s, P1, PenaltyKind.TIME)
        assertEquals("la perdita del servizio non tocca il punteggio", 0 to 0, served.pt1 to served.pt2)
        assertEquals(PenaltyOutcome.POINT, ScoreEngine.penaltyOutcome(s, P2, PenaltyKind.TIME))
        val received = pen(s, P2, PenaltyKind.TIME)
        assertEquals(1 to 0, received.pt1 to received.pt2)
    }

    @Test fun gameDiPenalitaNelTiebreakChiudeIlSet() {
        var s = ScoreEngine.initial(RulesConfig())
        repeat(6) { s = game(s, P1); s = game(s, P2) }
        assertTrue(s.inTiebreak)
        s = pen(pen(pen(s, P2, PenaltyKind.CODE), P2, PenaltyKind.CODE), P2, PenaltyKind.CODE)
        assertEquals(1, s.sets.size)
        assertEquals(P1, s.sets[0].winner)
    }

    @Test fun richiamiSiRigiocanoESiAnnullano() {
        val rules = RulesConfig()
        val events = listOf(MatchEvent.Point(P1), MatchEvent.Penalty(P1, PenaltyKind.CODE), MatchEvent.Penalty(P1, PenaltyKind.CODE))
        val s = ScoreEngine.replay(rules, events)
        assertEquals(1 to 1, s.pt1 to s.pt2)
        assertEquals(2, s.penalties(P1, PenaltyKind.CODE))
        assertEquals(1 to 0, ScoreEngine.replay(rules, events.dropLast(1)).let { it.pt1 to it.pt2 })
    }

    @Test fun richiamoDopoLaFineNonCambiaNiente() {
        val s = ScoreEngine.apply(ScoreEngine.initial(RulesConfig()), MatchEvent.Retired(P2)).state
        assertEquals(s, pen(s, P1, PenaltyKind.CODE))
    }

    @Test fun ritiroNelPrimoGameSegnaZeroAZero() {
        val s = play(ScoreEngine.initial(RulesConfig()), P2)
        val set = ScoreEngine.apply(s, MatchEvent.Retired(P1)).state.sets.single()
        assertEquals(0 to 0, set.g1 to set.g2)
        assertTrue(set.partial)
    }

    @Test fun squalificaChiudeComeIlRitiro() {
        val s = play(ScoreEngine.initial(RulesConfig()), P1)
        val after = ScoreEngine.apply(s, MatchEvent.Retired(P1, disqualified = true)).state
        assertEquals(P2, after.winner)
        assertTrue(after.disqualified)
        assertFalse(ScoreEngine.apply(s, MatchEvent.Retired(P1)).state.disqualified)
    }

    @Test fun lePauseNonToccanoIlPunteggio() {
        val s = play(ScoreEngine.initial(RulesConfig()), P1, P2)
        assertEquals(s, ScoreEngine.apply(s, MatchEvent.Break(P1, BreakKind.MEDICAL)).state)
    }

    @Test fun correggereChiServeTieneIlPunteggio() {
        var s = ScoreEngine.initial(RulesConfig(firstServer = P1))
        s = game(s, P1)
        s = play(s, P1, P1)
        assertEquals(P2, s.server)
        val fixed = ScoreEngine.apply(s, MatchEvent.ServerFix(P1)).state
        assertEquals(P1, fixed.server)
        assertEquals(s.g1 to s.pt1, fixed.g1 to fixed.pt1)
        assertEquals("poi si alterna da qui", P2, game(fixed, P1).server)
    }

    @Test fun nelDoppioSiCorreggeAncheIlGiocatore() {
        val s = ScoreEngine.initial(RulesConfig(doubles = true, firstServer = P1, firstServerP1 = 0))
        val fixed = ScoreEngine.apply(s, MatchEvent.ServerFix(P1, 1)).state
        assertEquals(P1 to 1, fixed.server to fixed.serverPlayer)
        val two = game(game(fixed, P1), P2)
        assertEquals("al prossimo turno della squadra tocca al compagno", P1 to 0, two.server to two.serverPlayer)
    }

    @Test fun correggereILati() {
        val s = ScoreEngine.initial(RulesConfig())
        assertEquals(!s.p1Left, ScoreEngine.apply(s, MatchEvent.EndsFix()).state.p1Left)
    }

    @Test fun punteggioImpostatoSiContinuaDaLi() {
        val s = ScoreEngine.initial(RulesConfig(firstServer = P1))
        val e = MatchEvent.ScoreSet(listOf(SetScore(6, 3)), 4, 3, 2, 1, P2)
        val set = ScoreEngine.apply(s, e).state
        assertEquals(1, set.sets.size)
        assertEquals(4 to 3, set.g1 to set.g2)
        assertEquals(2 to 1, set.pt1 to set.pt2)
        assertEquals(P2, set.server)
        assertEquals(1, set.setsWon(P1))
        // Due punti a P1: il game è suo (5-3).
        assertEquals(5 to 3, play(set, P1, P1).let { it.g1 to it.g2 })
    }

    @Test fun punteggioImpossibileNonSiApplica() {
        val r = RulesConfig()
        assertFalse("set non finito", ScoreEngine.validScore(r, listOf(SetScore(5, 3)), 0, 0, 0, 0))
        assertFalse("partita già vinta", ScoreEngine.validScore(r, listOf(SetScore(6, 3), SetScore(6, 2)), 0, 0, 0, 0))
        assertFalse("set in corso già chiuso", ScoreEngine.validScore(r, emptyList(), 6, 3, 0, 0))
        assertFalse("game già vinto", ScoreEngine.validScore(r, emptyList(), 2, 2, 4, 1))
        assertTrue("vantaggio", ScoreEngine.validScore(r, emptyList(), 2, 2, 4, 3))
        assertTrue("7-6 col tie-break", ScoreEngine.validScore(r, listOf(SetScore(7, 6)), 6, 6, 5, 4))
        val s = ScoreEngine.initial(r)
        assertEquals(s, ScoreEngine.apply(s, MatchEvent.ScoreSet(emptyList(), 6, 0, 0, 0, P1)).state)
    }

    @Test fun punteggioImpostatoSul66EIlTiebreak() {
        val st = ScoreEngine.apply(ScoreEngine.initial(RulesConfig()), MatchEvent.ScoreSet(emptyList(), 6, 6, 3, 2, P1)).state
        assertTrue(st.inTiebreak)
        assertEquals("3", st.pointLabel(P1))
    }

    @Test fun ordineDiRispostaNelDoppio() {
        var s = ScoreEngine.initial(RulesConfig(doubles = true, firstServer = P1))
        assertEquals(null, s.receiverPlayer)
        s = ScoreEngine.apply(s, MatchEvent.ReceiveOrder(1, 0, 1)).state
        // Risponde P2: da destra il giocatore 1, poi l'altro.
        assertEquals(1, s.receiverPlayer)
        assertEquals(0, play(s, P1).receiverPlayer)
        assertEquals("nel set dopo si sceglie di nuovo", null, ScoreEngine.apply(s, MatchEvent.ReceiveOrder(2, 0, 0)).state.let { if (it == s) null else it.recv1 })
    }
}
