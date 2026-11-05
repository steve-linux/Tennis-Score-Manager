// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

class ActivityCalendarTest {
    private fun at(m: Int, d: Int, h: Int = 18) = Calendar.getInstance().apply { clear(); set(2026, m, d, h, 0) }.timeInMillis

    @Test fun daysAndWeeksInARow() {
        val days = ActivityCalendar.byDay(listOf("m1" to at(10, 2), "m2" to at(10, 2, 20)), listOf("w1" to at(10, 2, 9), "w2" to at(10, 4)))
        assertEquals(listOf("m1", "m2"), days[ActivityCalendar.dayStart(at(10, 2))]!!.matches)
        assertEquals(listOf("w1"), days[ActivityCalendar.dayStart(at(10, 2))]!!.workouts)
        // Novembre 2026: lunedì 2, 9, 16, 23. Attività nelle settimane del 26 ottobre, del 2 e del 9 novembre.
        val times = listOf(at(9, 28), at(10, 3), at(10, 12))
        assertEquals(3, ActivityCalendar.weekStreak(times, at(10, 13)))
        // Mercoledì 18 senza niente ancora: la settimana non è finita, si conta dalla scorsa.
        assertEquals(3, ActivityCalendar.weekStreak(times, at(10, 18)))
        // Una settimana intera saltata: la serie si azzera.
        assertEquals(0, ActivityCalendar.weekStreak(times, at(10, 25)))
    }

    @Test fun monthStartsOnMonday() {
        // Il 1° novembre 2026 è domenica: sei caselle vuote prima.
        val g = ActivityCalendar.monthGrid(2026, 10)
        assertEquals(0, g.size % 7)
        assertNull(g[5])
        assertEquals(ActivityCalendar.dayStart(at(10, 1)), g[6])
        assertEquals(ActivityCalendar.dayStart(at(10, 2)), g[7])
    }
}
