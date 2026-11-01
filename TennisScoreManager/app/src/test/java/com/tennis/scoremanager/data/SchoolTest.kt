// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class SchoolTest {
    private fun at(y: Int, m: Int, d: Int, h: Int = 12, min: Int = 0): Long = Calendar.getInstance().apply { set(y, m - 1, d, h, min, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis

    // Lunedì e mercoledì 17:00 per 90 minuti sul Campo 2, dal 2 al 30 novembre 2026.
    private val course = Course(
        "c", "SAT Under 10", students = listOf("a", "b"), from = at(2026, 11, 2), to = at(2026, 11, 30),
        slots = listOf(CourseSlot(1, 17 * 60, 90, "Campo 2"), CourseSlot(3, 17 * 60, 90, "Campo 2")), feeCents = 6_000,
    )

    @Test
    fun lessonsFromTheWeeklySchedule() {
        val l = Courses.lessons(course)
        // Lunedì 2, 9, 16, 23, 30 e mercoledì 4, 11, 18, 25.
        assertEquals(9, l.size)
        assertEquals(at(2026, 11, 2, 17), l.first().start)
        assertEquals(at(2026, 11, 30, 17), l.last().start)
        assertEquals("2026-11-04@1700@Campo 2", l[1].key)
        assertEquals(at(2026, 11, 4, 18, 30), l[1].end)
    }

    @Test
    fun attendanceAndCancelledLessons() {
        val l = Courses.lessons(course)
        var c = Courses.mark(course, l[0].key, setOf("a", "b"))
        c = Courses.mark(c, l[1].key, setOf("a"))
        c = Courses.setCancelled(c, l[2].key, true)
        val now = at(2026, 11, 12)
        // Fatte: 2, 4 e 11 (annullata la 9); con l'appello solo 2 e 4.
        assertEquals(2 to 2, Courses.attendanceOf(c, "a", now))
        assertEquals(1 to 2, Courses.attendanceOf(c, "b", now))
        assertEquals(3, Courses.held(c, now).size)
        // La prossima salta quella annullata del 9: è l'11.
        assertEquals(at(2026, 11, 11, 17), Courses.next(c, at(2026, 11, 5))?.start)
        assertEquals(course.cancelled, Courses.setCancelled(c, l[2].key, false).cancelled)
    }

    @Test
    fun monthlyFeeChargedOncePerStudent() {
        val a = PlayerProfile("a", "Luca"); val b = PlayerProfile("b", "Anna"); val x = PlayerProfile("x", "Altro")
        val (b1, n1) = Courses.charge(ClubBook(), course, listOf(a, b, x), "SAT Under 10 · novembre 2026", 0)
        assertEquals(2, n1)
        val (b2, n2) = Courses.charge(b1, course, listOf(a, b, x), "SAT Under 10 · novembre 2026", 0)
        assertEquals(0, n2); assertEquals(2, b2.charges.size)
        assertEquals(6_000, Fees.owed(b2, a))
        assertEquals(listOf(2026 to 10), Courses.months(course))
        assertEquals(listOf(2026 to 8, 2026 to 9, 2026 to 10, 2026 to 11, 2027 to 0), Courses.months(course.copy(from = at(2026, 9, 15), to = at(2027, 1, 10))))
    }

    @Test
    fun courseBlocksTheCourtsInThePlanner() {
        val blocks = Courses.blocks(course)
        assertEquals(2, blocks.size)
        val plan = CourtPlan(blocks = blocks)
        // Il lunedì alle 17:30 il Campo 2 non si prenota per giocare, ma la lezione sì; il Campo 1 è libero.
        assertEquals(1, CourtPlans.blockedBy(plan, "Campo 2", at(2026, 11, 16, 17, 30), at(2026, 11, 16, 18, 30), BookingKind.PLAY).size)
        assertTrue(CourtPlans.blockedBy(plan, "Campo 2", at(2026, 11, 16, 17, 30), at(2026, 11, 16, 18, 30), BookingKind.LESSON).isEmpty())
        assertTrue(CourtPlans.blockedBy(plan, "Campo 1", at(2026, 11, 16, 17, 30), at(2026, 11, 16, 18, 30), BookingKind.PLAY).isEmpty())
        assertEquals("c", blocks.first().course)
        assertFalse(Courses.full(course)); assertTrue(Courses.full(course.copy(maxStudents = 2)))
    }
}
