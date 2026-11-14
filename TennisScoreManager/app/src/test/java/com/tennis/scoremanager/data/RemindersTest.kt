// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class RemindersTest {
    private fun at(dayOffset: Int, h: Int, m: Int = 0): Long = Calendar.getInstance().apply {
        set(2026, Calendar.NOVEMBER, 14, h, m, 0); set(Calendar.MILLISECOND, 0); add(Calendar.DAY_OF_MONTH, dayOffset)
    }.timeInMillis
    private val names = { id: String -> if (id == "s1") "Anna" else id }

    @Test fun coachGetsTomorrowsLessonsOnlyInTheEveningAndOnce() {
        val book = CoachBook(lessons = listOf(CoachLesson("l1", at(1, 18), students = listOf("s1")), CoachLesson("l2", at(1, 9), cancelled = true)))
        val evening = Reminders.due(at(0, 19), true, book, emptyList(), emptySet(), names)
        assertEquals(1, evening.size)
        assertEquals(Reminders.Kind.TOMORROW, evening[0].kind)
        assertEquals(1, evening[0].count)
        assertTrue(evening[0].text, "18:00 Anna" in evening[0].text)
        assertEquals("lessons", evening[0].target)
        assertTrue(Reminders.due(at(0, 10), true, book, emptyList(), emptySet(), names).isEmpty())
        assertTrue(Reminders.due(at(0, 19), false, book, emptyList(), emptySet(), names).isEmpty())
        assertTrue(Reminders.due(at(0, 20), true, book, emptyList(), setOf(evening[0].key), names).isEmpty())
    }

    @Test fun attendanceToSendAfterADay() {
        val course = Course("c1", "Under 10", from = 0, to = 0)
        val cc = CoachCourse(CourseCard(code = "X", issuedAt = 1, course = course), changedAt = at(-2, 18))
        val r = Reminders.due(at(0, 10), true, CoachBook(courses = listOf(cc)), emptyList(), emptySet(), names)
        assertEquals(listOf(Reminders.Kind.SEND), r.map { it.kind })
        assertEquals("course:c1", r[0].target)
        // Mandate dopo l'ultima modifica: niente.
        assertTrue(Reminders.due(at(0, 10), true, CoachBook(courses = listOf(cc.copy(sentAt = at(-1, 9)))), emptyList(), emptySet(), names).isEmpty())
    }

    @Test fun matchDayFromAReceivedCard() {
        val card = MatchCard(code = "C", issuedAt = 1, event = "Sociale", ref = "t", match = "m", court = "Campo 2", at = at(0, 18),
            side1 = listOf(CardPlayer("a", "Rossi")), side2 = listOf(CardPlayer("b", "Bianchi")))
        val r = Reminders.due(at(0, 8), false, CoachBook(), listOf(ReceivedCard(card)), emptySet(), names)
        assertEquals(1, r.size)
        assertTrue(r[0].text, r[0].text.startsWith("18:00 · Campo 2 · Rossi – Bianchi"))
        // Già iniziata (c'è la partita) o già passata: niente.
        assertTrue(Reminders.due(at(0, 8), false, CoachBook(), listOf(ReceivedCard(card, record = "m1")), emptySet(), names).isEmpty())
        assertTrue(Reminders.due(at(0, 19), false, CoachBook(), listOf(ReceivedCard(card)), emptySet(), names).isEmpty())
    }
}
