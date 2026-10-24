// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.video

import com.tennis.scoremanager.model.RulesConfig
import com.tennis.scoremanager.model.Side
import org.junit.Assert.assertEquals
import org.junit.Test

/** Punti segnati guardando un video: punteggio, game e set come in partita. */
class PointMarksTest {
    private val rules = RulesConfig()

    /** Segna i punti uno dopo l'altro (G1 = 0, G2 = 1), un secondo l'uno. */
    private fun tag(vararg winners: Int): List<PointMark> {
        val out = mutableListOf<PointMark>()
        for (w in winners) {
            val side = if (w == 1) Side.P2 else Side.P1
            val at = (out.size + 1) * 1_000L
            val after = PointMarks.stateAfter(rules, out + PointMark(at, "", w), out.size + 1)
            out += PointMarks.mark(at, after, side, null)
        }
        return out
    }

    @Test
    fun gameByGame() {
        // Primo game a zero per G1, poi 15-0 per G2.
        val pts = tag(0, 0, 0, 0, 1)
        assertEquals(listOf("0-0 15-0", "0-0 30-0", "0-0 40-0", "1-0 0-0", "1-0 0-15"), pts.map { it.score })
        // I primi quattro punti sono del game 0 (anche quello che lo chiude), il quinto del game 1.
        assertEquals(listOf(0, 0, 0, 0, 1), pts.map { it.game })
        assertEquals(listOf(0, 0, 0, 0, 0), pts.map { it.set })
    }

    @Test
    fun setEnds() {
        // 6-0: 24 punti di G1; il punto che chiude il set appartiene ancora al set 0.
        val pts = tag(*IntArray(25) { if (it < 24) 0 else 1 })
        assertEquals("6-0 0-0 0-15", pts.last().score)
        assertEquals(0, pts[23].set)
        assertEquals(5, pts[23].game)
        assertEquals(1, pts.last().set)
        assertEquals(6, pts.last().game)
    }
}
