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

class CourseMoveTest {
    private fun day(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    // Martedì alle 17 sul Campo 1, dal 1° al 22 dicembre 2026: 1, 8, 15, 22.
    private val course = Course("k", "Mini", slots = listOf(CourseSlot(2, 17 * 60, 60, "Campo 1")), from = day(2026, 12, 1), to = day(2026, 12, 22))

    @Test fun movedLessonKeepsItsKeyAndAttendance() {
        val l8 = Courses.lessons(course)[1]
        val c = Courses.mark(Courses.setCancelled(course, l8.key, true), l8.key, setOf("a"))
        val thursday = CourtPlans.at(day(2026, 12, 10), 18, 0)
        val m = Courses.move(c, l8.key, thursday, court = "Campo 2")
        assertFalse(l8.key in m.cancelled)            // spostata, non più annullata
        val after = Courses.lessons(m)
        assertEquals(4, after.size)
        val moved = after.single { it.key == l8.key }
        assertEquals(thursday, moved.start)
        assertEquals("Campo 2", moved.court)
        assertEquals(l8.start, moved.movedFrom)
        assertEquals(setOf("a"), m.attendance[l8.key])   // le presenze restano sulla stessa chiave
        // Rimessa al suo posto non risulta più spostata.
        assertTrue(Courses.move(m, l8.key, l8.start, court = "Campo 1").moved.isEmpty())
    }

    @Test fun makeupIsTheFirstFreeSlotAfterTheLastLessonSkippingHolidays() {
        // L'ultima è il 22: il 29 dicembre è libero.
        assertEquals(CourtPlans.at(day(2026, 12, 29), 17, 0), Courses.makeup(course)!!.start)
        // Se il 29 fosse da saltare, il 5 gennaio.
        assertEquals(CourtPlans.at(day(2027, 1, 5), 17, 0), Courses.makeup(course) { CourtPlans.dayStart(it) == day(2026, 12, 29) }!!.start)
        assertNull(Courses.makeup(course.copy(slots = emptyList())))
    }

    @Test fun planningFreesTheOldHourAndBlocksTheNewOne() {
        val l8 = Courses.lessons(course)[1]
        val m = Courses.move(course, l8.key, CourtPlans.at(day(2026, 12, 10), 18, 0), court = "Campo 2")
        val blocks = Courses.blocks(m)
        val weekly = blocks.single { it.weekdays == setOf(2) }
        assertFalse(CourtPlans.blockActive(weekly, day(2026, 12, 8)))   // l'8 il Campo 1 è libero
        assertTrue(CourtPlans.blockActive(weekly, day(2026, 12, 15)))
        val one = blocks.single { it.id.contains("-m-") }
        assertEquals(listOf("Campo 2"), one.courts)
        assertTrue(CourtPlans.blockActive(one, day(2026, 12, 10)))
        assertFalse(CourtPlans.blockActive(one, day(2026, 12, 17)))
        assertEquals(18 * 60, one.startMin)
    }
}
