// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoachingTest {
    private fun session(id: String, at: Long, student: String?, inside: Int, out: Int, stroke: TrainedStroke = TrainedStroke.FOREHAND) =
        ServeSession(
            id, at, stroke = stroke, student = student,
            serves = List(inside) { ServeAttempt(at, true, ServeSide.DEUCE, ServeKind.FIRST) } +
                List(out) { ServeAttempt(at, false, ServeSide.DEUCE, ServeKind.FIRST) },
        )

    @Test fun balanceCountsDoneLessonsAndUnpaidPacks() {
        val book = CoachBook(
            lessons = listOf(
                CoachLesson("l1", 1_000, students = listOf("a", "b"), done = true),
                CoachLesson("l2", 2_000, students = listOf("a"), done = true),
                CoachLesson("l3", 3_000, students = listOf("a"), done = true, cancelled = true),
                CoachLesson("l4", 4_000, students = listOf("a")),
            ),
            packs = listOf(
                LessonPack("p1", "a", 10, cents = 20000, paidAt = 5),
                LessonPack("p2", "a", 5, cents = 11000),
                LessonPack("p3", "b", 5, cents = 9000),
            ),
        )
        val a = Coaching.balance(book, "a")
        assertEquals(15, a.bought)
        assertEquals(2, a.used)
        assertEquals(13, a.left)
        assertEquals(11000, a.dueCents)
        assertEquals(1, a.duePacks)
        val b = Coaching.balance(book, "b")
        assertEquals(1, b.used)
        assertEquals(4, b.left)
    }

    @Test fun upcomingSkipsCancelledAndFinished() {
        val now = 10 * 60_000L
        val lessons = listOf(
            CoachLesson("past", 0, minutes = 5),
            CoachLesson("running", 8 * 60_000L, minutes = 60),
            CoachLesson("later", 100 * 60_000L),
            CoachLesson("off", 50 * 60_000L, cancelled = true),
        )
        assertEquals(listOf("running", "later"), Coaching.upcoming(lessons, now).map { it.id })
    }

    @Test fun goalUsesLastSessionsOfThatStrokeOnly() {
        val g = StudentGoal("g", "a", TrainedStroke.FOREHAND, targetPct = 70, window = 2)
        val sessions = listOf(
            session("old", 1, "a", 0, 10),          // fuori dalla finestra
            session("s1", 2, "a", 7, 3),
            session("s2", 3, "a", 8, 2),
            session("serve", 4, "a", 0, 10, TrainedStroke.SERVE),
            session("other", 5, "b", 0, 10),
        )
        val st = Coaching.goalStatus(g, sessions)
        assertEquals(75, st.pct)
        assertEquals(2, st.sessions)
        assertTrue(st.reached(g))
        assertNull(Coaching.goalStatus(g.copy(stroke = TrainedStroke.VOLLEY), sessions).pct)
        assertFalse(Coaching.goalStatus(g.copy(targetPct = 80), sessions).reached(g.copy(targetPct = 80)))
    }

    @Test fun markReachedStampsOnlyOnce() {
        val book = CoachBook(goals = listOf(StudentGoal("g", "a", TrainedStroke.FOREHAND, 50)))
        val sessions = listOf(session("s", 1, "a", 6, 4))
        val once = Coaching.markReached(book, sessions, 100)
        assertEquals(100L, once.goals[0].reachedAt)
        assertEquals(100L, Coaching.markReached(once, sessions, 200).goals[0].reachedAt)
    }

    @Test fun lastSeenTakesLatestSessionOrDoneLesson() {
        val book = CoachBook(lessons = listOf(
            CoachLesson("l1", 500, students = listOf("a"), done = true),
            CoachLesson("l2", 900, students = listOf("a")), // non fatta: non conta
        ))
        val sessions = listOf(session("s", 300, "a", 1, 0))
        assertEquals(500L, Coaching.lastSeen(book, sessions, "a"))
        assertNotNull(Coaching.lastSeen(CoachBook(), sessions, "a"))
        assertNull(Coaching.lastSeen(book, sessions, "z"))
    }
}
