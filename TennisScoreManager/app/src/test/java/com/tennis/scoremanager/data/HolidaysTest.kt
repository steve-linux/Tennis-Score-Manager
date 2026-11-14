// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class HolidaysTest {
    @Test fun easterAndItalianHolidays() {
        assertEquals(LocalDate.of(2026, 4, 5), Holidays.easter(2026))
        assertEquals(LocalDate.of(2027, 3, 28), Holidays.easter(2027))
        val it = Holidays.of(2026, "IT")
        assertEquals("Lunedì dell'Angelo", it[LocalDate.of(2026, 4, 6)])
        assertEquals("Immacolata Concezione", it[LocalDate.of(2026, 12, 8)])
        assertEquals(12, it.size)
        assertEquals("Thanksgiving", Holidays.of(2026, "US")[LocalDate.of(2026, 11, 26)])
        assertEquals("Pfingstmontag", Holidays.of(2026, "DE")[LocalDate.of(2026, 5, 25)])
    }

    @Test fun endDateFromLessonsSkippingHolidays() {
        val zone = ZoneId.systemDefault()
        // Martedì 1 dicembre 2026, il martedì alle 17: la 2ª lezione è l'8 (festa) se non la si salta.
        val from = LocalDate.of(2026, 12, 1).atStartOfDay(zone).toInstant().toEpochMilli()
        val slots = listOf(CourseSlot(2, 17 * 60))
        val day = { t: Long -> java.time.Instant.ofEpochMilli(t).atZone(zone).toLocalDate() }
        assertEquals(LocalDate.of(2026, 12, 8), day(Courses.endAfter(from, slots, 2)!!))
        val skip = { t: Long -> Holidays.on(t, "IT", zone) != null }
        assertEquals(LocalDate.of(2026, 12, 15), day(Courses.endAfter(from, slots, 2, skip)!!))
        assertNull(Courses.endAfter(from, emptyList(), 3))
        val co = Course("c", "x", slots = slots, from = from, to = LocalDate.of(2026, 12, 31).atStartOfDay(zone).toInstant().toEpochMilli())
        assertEquals(1, Courses.skipped(co, skip).size)
    }
}
