// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StrapGapsTest {

    @Test
    fun nothingBeforeFirstReading() {
        assertNull(StrapGaps().stats(10_000))
    }

    @Test
    fun smallHolesAreNotDrops() {
        val g = StrapGaps()
        // Una al secondo, con un secondo saltato ogni tanto: succede anche da vicino.
        listOf(1_000L, 2_000, 3_000, 5_000, 6_000, 8_900).forEach { g.onReading(it) }
        assertEquals(GapStats(0, 0), g.stats(9_500))
    }

    @Test
    fun dropsAndLostSeconds() {
        val g = StrapGaps()
        g.onReading(1_000)
        g.onReading(9_000)   // 8 s di buco: 7 persi
        g.onReading(10_000)
        g.onReading(16_000)  // 6 s: 5 persi
        assertEquals(GapStats(2, 12), g.stats(16_500))
    }

    @Test
    fun ongoingDropCountsAlready() {
        val g = StrapGaps()
        g.onReading(1_000)
        assertEquals(GapStats(0, 0), g.stats(3_500))
        assertEquals(GapStats(1, 9), g.stats(11_000))
        // Torna: lo stacco resta uno solo.
        g.onReading(12_000)
        assertEquals(GapStats(1, 10), g.stats(12_500))
    }

    @Test
    fun twoPhonesSameStrap() {
        // Le misure dei due telefoni si mescolano: una vecchia (o doppia) non apre né chiude buchi.
        val g = StrapGaps()
        g.onReading(1_000)
        g.onReading(2_000)
        g.onReading(1_900)
        g.onReading(2_000)
        g.onReading(3_000)
        assertEquals(GapStats(0, 0), g.stats(3_200))
    }

    @Test
    fun resetStartsAgain() {
        val g = StrapGaps()
        g.onReading(1_000)
        g.onReading(20_000)
        g.reset()
        assertNull(g.stats(21_000))
        g.onReading(22_000)
        assertEquals(GapStats(0, 0), g.stats(22_500))
    }
}
