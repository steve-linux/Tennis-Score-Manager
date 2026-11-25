// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseCardsTest {
    private val anna = PlayerProfile("p1", "Anna Neri", "Anna", "Neri", guardian = "Paola Neri", guardianPhone = "3331112222", consentAt = 7)
    private val course = Course("c1", "Under 10", coach = "k", students = listOf("p1", "p2"), from = 0, to = 10, feeCents = 5000, cardCode = "CRS23456")
    private val find = { id: String -> if (id == "p1") anna else null }

    @Test fun cardDropsFeesAndCarriesContacts() {
        val card = CourseCards.card(course, "CRS23456", 5, "TC", "333", find)
        assertEquals(0, card.course.feeCents)
        assertEquals("Paola Neri", card.students[0].guardian)
        assertEquals(7L, card.students[0].consentAt)
        assertEquals("p2", card.students[1].id)
        assertEquals(card, CourseCards.decode(CourseCards.encode(card)))
        assertNull(CourseCards.decodeReport(CourseCards.encode(card)))
    }

    @Test fun newerCardReplacesOlderAndKeepsCoachAttendance() {
        val c1 = CourseCards.card(course, "CRS23456", 5, "", "", find)
        val list = CourseCards.receive(emptyList(), c1).map { it.copy(attendance = mapOf("k1" to setOf("p1")), changedAt = 9) }
        val c2 = CourseCards.card(course.copy(students = listOf("p1", "p2", "p3")), "CRS23456", 6, "", "", find)
        val after = CourseCards.receive(list, c2)
        assertEquals(1, after.size)
        assertEquals(3, after[0].card.course.students.size)
        assertEquals(setOf("p1"), after[0].attendance["k1"])
        // Una scheda più vecchia non torna indietro.
        assertEquals(after, CourseCards.receive(after, c1))
        assertTrue(after[0].toSend)
    }

    @Test fun attendanceReportAppliesOnlyWithTheRightCode() {
        val cc = CoachCourse(CourseCards.card(course, "CRS23456", 5, "", "", find), attendance = mapOf("k1" to setOf("p1", "zz")), cancelled = setOf("k2"), changedAt = 1)
        val r = CourseCards.report(cc, "Coach", 3)
        assertEquals(r, CourseCards.decodeReport(CourseCards.encodeReport(r)))
        assertEquals(AttendanceStatus.READY, CourseCards.check(r, listOf(course)))
        assertEquals(AttendanceStatus.BAD_CODE, CourseCards.check(r.copy(code = "X"), listOf(course)))
        assertEquals(AttendanceStatus.NOT_FOUND, CourseCards.check(r, emptyList()))
        val applied = CourseCards.apply(course.copy(cancelled = setOf("k1")), r)
        assertEquals(setOf("p1"), applied.attendance["k1"])
        assertFalse("k1" in applied.cancelled)
        assertTrue("k2" in applied.cancelled)
        assertEquals(AttendanceStatus.SAME, CourseCards.check(r, listOf(CourseCards.apply(course, r))))
    }

    @Test fun proposalRoundTrip() {
        val p = StudentProposal(student = CourseCards.student(anna, "p1"), coach = "Coach", level = "Base", sentAt = 1)
        assertEquals(p, CourseCards.decodeProposal(CourseCards.encodeProposal(p)))
        assertNull(CourseCards.decode(CourseCards.encodeProposal(p)))
    }

    @Test fun groupLessonAbsencesAndRecovery() {
        val book = CoachBook(
            lessons = listOf(
                CoachLesson("l1", 0, students = listOf("a", "b"), done = true, absent = setOf("b")),
                CoachLesson("l2", 1, students = listOf("a", "b"), done = true, excused = setOf("a")),
                CoachLesson("l3", 2, students = listOf("a"), done = true, recovery = true),
            ),
            packs = listOf(LessonPack("p", "a", 10), LessonPack("q", "b", 10)),
        )
        val a = Coaching.balance(book, "a")
        assertEquals(1, a.used)        // l1; l2 giustificata e l3 recupero non contano
        assertEquals(1, a.absences)
        assertEquals(0, a.toRecover)   // recuperata con l3
        val b = Coaching.balance(book, "b")
        assertEquals(2, b.used)        // l1 assente non giustificato conta, l2 presente
        assertEquals(1, b.absences)
    }

    @Test fun evaluationTrendAndMonth() {
        val book = CoachBook(
            evaluations = listOf(
                Evaluation("e1", "a", 1, mapOf("serve" to 2, "forehand" to 3)),
                Evaluation("e2", "a", 2, mapOf("serve" to 4, "forehand" to 3)),
            ),
            lessons = listOf(CoachLesson("l1", 100, minutes = 90, students = listOf("a"), done = true)),
            packs = listOf(LessonPack("p", "a", 5, cents = 15000, paidAt = 150)),
        )
        assertEquals(1.0, Coaching.trend(book, "a")!!, 0.001)
        assertNull(Coaching.trend(book, "z"))
        val m = Coaching.month(book, 0, 1000)
        assertEquals(1, m.privateLessons)
        assertEquals(90, m.privateMinutes)
        assertEquals(15000, m.paidCents)
        assertNull(m.presencePct)
    }

    @Test fun receiptNumbersRunPerYear() {
        val y2026 = java.util.Calendar.getInstance().apply { set(2026, 5, 1) }.timeInMillis
        val y2027 = java.util.Calendar.getInstance().apply { set(2027, 0, 10) }.timeInMillis
        val book = CoachBook(packs = listOf(LessonPack("a", "s", 5, receipt = "2026/001"), LessonPack("b", "s", 5, receipt = "2026/007"), LessonPack("c", "s", 5)))
        assertEquals("2026/008", Coaching.nextReceipt(book, y2026))
        assertEquals("2027/001", Coaching.nextReceipt(book, y2027))
    }

    @Test fun receiptNumberComesBackOnlyIfItWasTheLast() {
        val y2026 = java.util.Calendar.getInstance().apply { set(2026, 5, 1) }.timeInMillis
        val packs = listOf(LessonPack("a", "s", 5, receipt = "2026/001"), LessonPack("b", "s", 5, receipt = "2026/002"))
        // Tolta l'ultima, il numero torna libero; tolta una prima, resta il salto.
        assertEquals("2026/002", Coaching.nextReceipt(CoachBook(packs = packs.filter { it.id != "b" }), y2026))
        assertEquals("2026/003", Coaching.nextReceipt(CoachBook(packs = packs.filter { it.id != "a" }), y2026))
    }

    @Test fun registerMarks() {
        val c = course.copy(attendance = mapOf("k1" to setOf("p1")), cancelled = setOf("k2"))
        assertEquals("✓", AttendancePdf.mark(c, "k1", "p1"))
        assertEquals("✗", AttendancePdf.mark(c, "k1", "p2"))
        assertEquals("—", AttendancePdf.mark(c, "k2", "p1"))
        assertEquals("", AttendancePdf.mark(c, "k3", "p1"))
    }

    @Test fun ownCourseIsNeverToSend() {
        val own = CoachCourse(CourseCard(code = "", issuedAt = 1, course = course), attendance = mapOf("k1" to setOf("p1")), changedAt = 5, personal = true)
        assertFalse(own.toSend)
        assertTrue(own.copy(personal = false).toSend)
    }

    @Test fun coachBusyTravelsWithAttendanceWithoutNotes() {
        val cc = CoachCourse(CourseCards.card(course, "CRS23456", 5, "", "", find))
        val busy = listOf(CoachBusy("b1", 100, 200, BusyKind.VACATION, "visita dal dentista"), CoachBusy("b0", -5 * 86_400_000L, -3 * 86_400_000L))
        val r = CourseCards.report(cc, "Coach", 0, busy)
        assertEquals(listOf("b1"), r.busy!!.map { it.id })   // quelli passati no
        assertEquals("", r.busy!!.single().note)           // la nota resta all'allenatore
        // Solo gli impegni cambiati: il file va registrato lo stesso.
        assertEquals(AttendanceStatus.READY, CourseCards.check(r, listOf(course)))
        val applied = CourseCards.apply(course, r)
        assertEquals(BusyKind.VACATION, applied.coachAway.single().kind)
        // Un file di una versione vecchia (senza impegni) non li cancella.
        assertEquals(applied.coachAway, CourseCards.apply(applied, r.copy(busy = null)).coachAway)
        assertEquals(BusyKind.VACATION, Coaching.busyOn(applied.coachAway, 150)!!.kind)
    }

    @Test fun courseLabelColor() {
        val club = CoachCourse(CourseCard(code = "", issuedAt = 1, course = course))
        assertEquals(CourseCards.CLUB, club.label)
        assertEquals(CourseCards.OWN, club.copy(personal = true).label)
        assertEquals("#FF9800", club.copy(color = "#FF9800").label)
    }
}
