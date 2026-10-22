// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import com.tennis.scoremanager.model.RulesConfig
import com.tennis.scoremanager.model.Side
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerStatsTest {

    private fun m(p1: String, p2: String, winner: Side?, at: Long, doubles: Boolean = false, p1b: String = "", p2b: String = "") =
        PlayerStats.Played(
            MatchRecord(
                id = "m$at", setup = SetupData(doubles = doubles, p1a = p1, p1b = p1b, p2a = p2, p2b = p2b),
                options = MatchOptions(), rules = RulesConfig(doubles = doubles), endedAt = at,
            ),
            winner,
        )

    private val all = listOf(
        m("Rossi", "Bianchi", Side.P1, 1),
        m("bianchi ", "ROSSI", Side.P1, 2),
        m("Rossi", "Verdi", Side.P2, 3),
        // Nomi vuoti: non diventano giocatori.
        m("", "Rossi", Side.P2, 4),
        // Non finita: non conta.
        m("Rossi", "Neri", null, 5),
    )

    @Test
    fun playersAreMatchedByNameIgnoringCaseAndSpaces() {
        val p = PlayerStats.players(all)
        val rossi = p.first { it.key == "rossi" }
        assertEquals(4, rossi.played)
        assertEquals(2, rossi.won)
        assertEquals(2, rossi.lost)
        // Il nome si scrive come nella partita più recente.
        assertEquals("Rossi", rossi.name)
        assertEquals("bianchi", p.first { it.key == "bianchi" }.name)
        assertNull(p.firstOrNull { it.key == "neri" })
        assertEquals("rossi", p.first().key)
    }

    @Test
    fun headToHead() {
        val r = PlayerStats.rivals(all, "rossi")
        val bianchi = r.first { it.key == "bianchi" }
        assertEquals(1, bianchi.won)
        assertEquals(1, bianchi.lost)
        val verdi = r.first { it.key == "verdi" }
        assertEquals(0, verdi.won)
        assertEquals(1, verdi.lost)
        assertEquals(2, r.size)
    }

    @Test
    fun doublesCountEachPlayer() {
        val d = listOf(m("Rossi", "Verdi", Side.P1, 1, doubles = true, p1b = "Bianchi", p2b = "Neri"))
        val p = PlayerStats.players(d)
        assertEquals(4, p.size)
        assertEquals(1, p.first { it.key == "bianchi" }.won)
        assertEquals(1, p.first { it.key == "neri" }.lost)
        assertEquals(listOf("neri", "verdi"), PlayerStats.rivals(d, "bianchi").map { it.key })
    }
}
