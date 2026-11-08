// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TournamentTest {

    private fun names(n: Int) = (1..n).map { "G$it" }

    /** Gioca il turno in corso dando a ogni partita il punteggio scelto da [score] e prepara il successivo. */
    private fun playRound(t: Tournament, score: (TMatch) -> Pair<Int, Int> = { 13 to 11 }): Tournament {
        val cur = t.current!!
        val done = cur.copy(matches = cur.matches.map { m -> score(m).let { (a, b) -> m.copy(scoreA = a, scoreB = b) } })
        val t2 = t.copy(rounds = t.rounds.dropLast(1) + done)
        return t2.copy(rounds = t2.rounds + Tournaments.nextRound(t2))
    }

    private fun start(t: Tournament) = t.copy(rounds = listOf(Tournaments.nextRound(t)))

    @Test
    fun americanoEightPlayersRotatesPartners() {
        var t = start(Tournament(id = "t1", players = names(8), courts = 2))
        repeat(6) { t = playRound(t) }
        assertEquals(7, t.rounds.size)
        // Ognuno gioca ogni turno (8 giocatori, 2 campi) e nessuno riposa.
        t.rounds.forEach { r ->
            assertEquals(8, r.matches.flatMap { it.a + it.b }.toSet().size)
            assertTrue(r.resting.isEmpty())
        }
        // In 7 turni ognuno ha 7 compagni su 7 possibili: al massimo poche coppie ripetute.
        val pairs = t.rounds.flatMap { r -> r.matches.flatMap { listOf(it.a.sorted(), it.b.sorted()) } }
        val repeats = pairs.size - pairs.toSet().size
        assertTrue("coppie ripetute: $repeats", repeats <= 4)
    }

    @Test
    fun restingIsFair() {
        // 6 giocatori, un campo: due riposano a ogni turno; in tre turni ognuno riposa una volta.
        var t = start(Tournament(id = "t2", players = names(6), courts = 1))
        repeat(2) { t = playRound(t) }
        val rests = t.rounds.flatMap { it.resting }.groupingBy { it }.eachCount()
        assertEquals(6, rests.size)
        assertTrue(rests.values.all { it == 1 })
    }

    @Test
    fun mexicanoPairsFirstAndFourthAgainstSecondAndThird() {
        var t = start(Tournament(id = "t3", kind = TournamentKind.MEXICANO, players = names(8), courts = 2))
        // Primo turno: la coppia "a" vince sempre 20-4.
        t = playRound(t) { 20 to 4 }
        val st = Tournaments.standings(t).map { it.player }
        val court1 = t.current!!.matches.first { it.court == 1 }
        assertEquals(listOf(st[0], st[3]), court1.a)
        assertEquals(listOf(st[1], st[2]), court1.b)
        val court2 = t.current!!.matches.first { it.court == 2 }
        assertEquals(listOf(st[4], st[7]), court2.a)
    }

    @Test
    fun standingsCountPointsWinsAndDraws() {
        val t = Tournament(
            id = "t4", players = names(4), courts = 1,
            rounds = listOf(
                TRound(1, listOf(TMatch(1, listOf(0, 1), listOf(2, 3), 15, 9))),
                TRound(2, listOf(TMatch(1, listOf(0, 2), listOf(1, 3), 12, 12))),
            ),
        )
        val s = Tournaments.standings(t)
        assertEquals(0, s.first().player)
        assertEquals(27, s.first().points)
        assertEquals(1, s.first().won)
        assertEquals(1, s.first().drawn)
        assertEquals(2, s.first().played)
        assertEquals(3, s.last().player)
        assertEquals(21, s.last().points)
    }

    @Test
    fun courtsAreLimitedByPlayers() {
        val t = start(Tournament(id = "t5", players = names(9), courts = 4))
        assertEquals(2, t.current!!.matches.size)
        assertEquals(1, t.current!!.resting.size)
    }
}
