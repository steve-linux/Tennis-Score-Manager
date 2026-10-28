// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import com.tennis.scoremanager.model.Side
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RatingTest {
    private var t = 0L

    private fun game(p1: List<String>, p2: List<String>, winner: Side, g1: Int, g2: Int, sport: Sport = Sport.TENNIS, id: String = "m${++t}") =
        Rating.Game(id, t, sport, mapOf(Side.P1 to p1, Side.P2 to p2), winner, mapOf(Side.P1 to g1, Side.P2 to g2))

    @Test
    fun equalPlayersMoveSymmetrically() {
        val table = Rating.compute(listOf(game(listOf("Rossi"), listOf("Bianchi"), Side.P1, 12, 6)), Sport.TENNIS)
        val rossi = table.player("rossi")!!
        val bianchi = table.player("bianchi")!!
        assertTrue(rossi.rating > Rating.START)
        assertEquals(Rating.START * 2, rossi.rating + bianchi.rating)
        assertEquals(rossi.rating - Rating.START, table.delta("m1", "rossi"))
        assertTrue(rossi.provisional)
        assertEquals("Rossi", rossi.name)
    }

    @Test
    fun scoreCountsButWinningMatters() {
        val clear = Rating.compute(listOf(game(listOf("A"), listOf("B"), Side.P1, 12, 0)), Sport.TENNIS).player("a")!!.rating
        val tight = Rating.compute(listOf(game(listOf("A"), listOf("B"), Side.P1, 13, 12)), Sport.TENNIS).player("a")!!.rating
        assertTrue(clear > tight)
        // Perdere 6-7 6-7 non dà punti: il risultato resta sotto 0,5.
        val loser = Rating.compute(listOf(game(listOf("A"), listOf("B"), Side.P1, 14, 12)), Sport.TENNIS).player("b")!!.rating
        assertTrue(loser < Rating.START)
    }

    @Test
    fun upsetGivesMoreThanExpectedWin() {
        // Forte vince tre volte contro Debole, poi Debole lo batte: guadagna più di quanto Forte guadagnava.
        val list = (1..3).map { game(listOf("Forte"), listOf("Debole"), Side.P1, 12, 4) } + game(listOf("Forte"), listOf("Debole"), Side.P2, 5, 12, id = "upset")
        val table = Rating.compute(list, Sport.TENNIS)
        val gain = table.delta("upset", "debole")!!
        assertTrue("$gain", gain > table.delta("m1", "forte")!!)
    }

    @Test
    fun doublesUseTeamAverageAndSkipBadMatches() {
        val list = listOf(
            game(listOf("Rossi", "Bianchi"), listOf("Verdi", "Neri"), Side.P1, 12, 9),
            // Un lato senza nomi e lo stesso nome sui due lati non contano.
            game(listOf("Rossi"), emptyList(), Side.P1, 12, 0),
            game(listOf("Rossi"), listOf("rossi "), Side.P1, 12, 0),
            game(listOf("Rossi"), listOf("Verdi"), Side.P1, 12, 0, sport = Sport.PADEL),
        )
        val tennis = Rating.compute(list, Sport.TENNIS)
        assertEquals(4, tennis.players.size)
        assertEquals(tennis.player("rossi")!!.rating, tennis.player("bianchi")!!.rating)
        assertEquals(1, tennis.player("rossi")!!.matches)
        assertNull(tennis.delta("m2", "rossi"))
        // Il padel ha la sua classifica.
        assertEquals(2, Rating.compute(list, Sport.PADEL).players.size)
    }

    @Test
    fun weightDropsWithExperience() {
        assertEquals(40.0, Rating.kFactor(0), 0.0)
        assertEquals(24.0, Rating.kFactor(10), 0.0)
        assertEquals(16.0, Rating.kFactor(30), 0.0)
        assertEquals(0.5, Rating.expected(1500.0, 1500.0), 1e-9)
        assertEquals(0.76, Rating.expected(1700.0, 1500.0), 0.01)
    }
}
