// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ClosureTest {
    private fun day(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private val patron = CourtBlock("p", "Festa del patrono", BlockReason.OTHER, from = day(2026, 12, 15), to = day(2026, 12, 15), yearly = true)

    @Test fun yearlyBlockRepeatsEveryYearFromTheFirst() {
        assertTrue(CourtPlans.blockActive(patron, day(2026, 12, 15)))
        assertTrue(CourtPlans.blockActive(patron, day(2027, 12, 15)))
        assertFalse(CourtPlans.blockActive(patron, day(2027, 12, 16)))
        assertFalse(CourtPlans.blockActive(patron, day(2025, 12, 15)))   // non prima del primo anno
        // A cavallo di capodanno.
        val xmas = CourtBlock("x", "Chiusura", from = day(2026, 12, 24), to = day(2027, 1, 6), yearly = true)
        assertTrue(CourtPlans.blockActive(xmas, day(2028, 1, 3)))
        assertFalse(CourtPlans.blockActive(xmas, day(2028, 1, 7)))
    }

    @Test fun closedOnlyWithAllCourtsAllDay() {
        val plan = CourtPlan(blocks = listOf(patron))
        assertEquals("Festa del patrono", CourtPlans.closedOn(plan, day(2027, 12, 15) + 3_600_000L * 12))
        assertNull(CourtPlans.closedOn(plan, day(2027, 12, 14)))
        // Un campo solo o qualche ora: il circolo non è chiuso.
        assertNull(CourtPlans.closedOn(CourtPlan(blocks = listOf(patron.copy(courts = listOf("Campo 1")))), day(2026, 12, 15)))
        assertNull(CourtPlans.closedOn(CourtPlan(blocks = listOf(patron.copy(startMin = 15 * 60, endMin = 18 * 60))), day(2026, 12, 15)))
    }

    @Test fun courseSkipsTheClosure() {
        val plan = CourtPlan(blocks = listOf(patron))
        // Martedì dal 1° dicembre 2026: l'8 è festa, il 15 il circolo è chiuso.
        val end = Courses.endAfter(day(2026, 12, 1), listOf(CourseSlot(2, 17 * 60)), 2) { t -> Holidays.on(t, "IT") != null || CourtPlans.closedOn(plan, t) != null }
        assertEquals(day(2026, 12, 22), end)
    }
}
