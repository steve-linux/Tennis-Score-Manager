// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LadderTest {
    private val l0 = Ladder("l", "Classifica", listOf("a", "b", "c", "d", "e", "f"), reach = 3)

    @Test fun whoCanChallengeWhom() {
        assertNull(Ladders.canChallenge(l0, "e", "b"))
        assertEquals(Ladders.Problem.TOO_FAR, Ladders.canChallenge(l0, "f", "a"))
        assertEquals(Ladders.Problem.NOT_ABOVE, Ladders.canChallenge(l0, "b", "e"))
        assertEquals(Ladders.Problem.NOT_IN, Ladders.canChallenge(l0, "z", "a"))
        assertEquals(listOf("d", "c", "b"), Ladders.targets(l0, "e"))
        val l1 = Ladders.challenge(l0, "e", "b", 0)
        assertEquals(Ladders.Problem.BUSY, Ladders.canChallenge(l1, "d", "b"))
        // b è impegnato: d può sfidare solo c e a.
        assertEquals(listOf("c", "a"), Ladders.targets(l1, "d"))
    }

    @Test fun challengerWinsTakesThePlace() {
        val l1 = Ladders.challenge(l0, "e", "b", 0)
        val id = l1.challenges.single().id
        val l2 = Ladders.result(l1, id, "6-4 6-3", null, 1000)
        assertEquals(listOf("a", "e", "b", "c", "d", "f"), l2.players)
        assertEquals(true, l2.challenges.single().challengerWon)
        assertEquals(5, l2.challenges.single().challengerPos)
        assertTrue(Ladders.targets(l2, "b").isNotEmpty())
    }

    @Test fun defenderWinsNothingMoves() {
        val l1 = Ladders.challenge(l0, "d", "c", 0)
        val l2 = Ladders.result(l1, l1.challenges.single().id, "4-6 2-6", null, 1000)
        assertEquals(l0.players, l2.players)
        assertEquals(false, l2.challenges.single().challengerWon)
    }

    @Test fun addRemoveMoveCancel() {
        val l1 = Ladders.add(l0, listOf("g", "a"))
        assertEquals(7, Ladders.position(l1, "g"))
        val l2 = Ladders.challenge(l1, "g", "d", 0)
        val l3 = Ladders.remove(l2, "d")
        assertTrue(l3.challenges.single().cancelled)
        assertEquals(listOf("a", "b", "c", "e", "f", "g"), l3.players)
        assertEquals(listOf("b", "a", "c", "e", "f", "g"), Ladders.move(l3, "a", up = false).players)
        val l4 = Ladders.challenge(l3, "g", "e", 0)
        assertTrue(Ladders.cancel(l4, l4.challenges.last().id).challenges.last().cancelled)
    }
}
