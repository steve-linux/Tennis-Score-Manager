// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PressDetectorTest {

    private val volUp = 24
    private val enter = 66

    @Test
    fun singleClickIsAPointAfterTheDoubleClickWindow() {
        val d = PressDetector()
        assertNull(d.down("a", volUp, 1_000))
        assertNull(d.up("a", volUp, 1_080))
        assertEquals(1_530L, d.nextDeadline())
        assertEquals(emptyList<String>(), d.due(1_500))
        assertEquals(listOf("a"), d.due(1_530))
        assertNull(d.nextDeadline())
    }

    @Test
    fun doubleClickUndoesWithoutGivingThePoint() {
        val d = PressDetector()
        d.down("a", volUp, 1_000)
        d.up("a", volUp, 1_080)
        assertEquals(Gesture.UNDO, d.down("a", volUp, 1_300))
        assertNull(d.up("a", volUp, 1_380))
        assertEquals(emptyList<String>(), d.due(5_000))
    }

    @Test
    fun twoClicksFarApartAreTwoPoints() {
        val d = PressDetector()
        d.down("a", volUp, 1_000)
        d.up("a", volUp, 1_080)
        assertEquals(listOf("a"), d.due(1_600))
        assertNull(d.down("a", volUp, 3_000))
        d.up("a", volUp, 3_080)
        assertEquals(listOf("a"), d.due(3_600))
    }

    @Test
    fun longPressUndoesOnReleaseWhenTheRemoteDoesNotRepeat() {
        val d = PressDetector()
        d.down("a", volUp, 1_000)
        assertEquals(Gesture.UNDO, d.up("a", volUp, 2_000))
        assertEquals(emptyList<String>(), d.due(5_000))
    }

    @Test
    fun longPressUndoesOnceWhileHeldWhenTheRemoteRepeats() {
        val d = PressDetector()
        d.down("a", volUp, 1_000)
        assertNull(d.down("a", volUp, 1_500))
        assertEquals(Gesture.UNDO, d.down("a", volUp, 1_950))
        assertNull(d.down("a", volUp, 2_000))
        assertNull(d.up("a", volUp, 2_400))
        assertEquals(emptyList<String>(), d.due(5_000))
    }

    @Test
    fun twoKeysSentForOnePressAreOneClick() {
        val d = PressDetector()
        // Insieme...
        d.down("a", volUp, 1_000)
        d.down("a", enter, 1_005)
        d.up("a", volUp, 1_070)
        assertNull(d.up("a", enter, 1_075))
        assertEquals(listOf("a"), d.due(2_000))
        // ...o uno dopo l'altro.
        d.down("a", volUp, 3_000)
        d.up("a", volUp, 3_010)
        assertNull(d.down("a", enter, 3_020))
        assertNull(d.up("a", enter, 3_030))
        assertEquals(listOf("a"), d.due(4_000))
    }

    @Test
    fun doubleClickOnATwoKeyRemote() {
        val d = PressDetector()
        d.down("a", volUp, 1_000)
        d.up("a", volUp, 1_010)
        d.down("a", enter, 1_020)
        d.up("a", enter, 1_030)
        assertEquals(Gesture.UNDO, d.down("a", volUp, 1_250))
        d.up("a", volUp, 1_260)
        assertNull(d.down("a", enter, 1_270))
        assertNull(d.up("a", enter, 1_280))
        assertEquals(emptyList<String>(), d.due(5_000))
    }

    @Test
    fun remotesAreIndependent() {
        val d = PressDetector()
        d.down("a", volUp, 1_000)
        d.up("a", volUp, 1_050)
        // Il clic dell'altro telecomando non è un doppio clic del primo.
        assertNull(d.down("b", volUp, 1_100))
        d.up("b", volUp, 1_150)
        assertEquals(listOf("a"), d.due(1_500))
        assertEquals(listOf("b"), d.due(1_600))
    }

    @Test
    fun releaseWithoutPressIsIgnored() {
        val d = PressDetector()
        assertNull(d.up("a", volUp, 1_000))
        assertNull(d.nextDeadline())
    }
}
