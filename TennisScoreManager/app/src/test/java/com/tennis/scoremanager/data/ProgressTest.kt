// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProgressTest {

    @Test
    fun halvesSkipMissingValues() {
        // Senza i valori mancanti restano 2, 4, 6, 8: prima metà 3, seconda 7.
        val m = Progress.Metric("Ace", "", listOf(2.0, null, 4.0, 6.0, null, 8.0), better = true)
        assertEquals(3.0 to 7.0, m.halves)
        assertEquals(1, m.verdict)
    }

    @Test
    fun lowerIsBetterForDoubleFaults() {
        assertEquals(1, Progress.Metric("DF", "", listOf(6.0, 5.0, 3.0, 2.0), better = false).verdict)
        assertEquals(-1, Progress.Metric("DF", "", listOf(2.0, 2.0, 5.0, 6.0), better = false).verdict)
    }

    @Test
    fun smallChangesAndUnknownDirectionAreSteady() {
        assertEquals(0, Progress.Metric("HRV", "ms", listOf(40.0, 41.0), better = true).verdict)
        assertEquals(0, Progress.Metric("bpm", "bpm", listOf(120.0, 160.0), better = null).verdict)
        assertNull(Progress.Metric("x", "", listOf(1.0, null), better = true).halves)
    }
}
