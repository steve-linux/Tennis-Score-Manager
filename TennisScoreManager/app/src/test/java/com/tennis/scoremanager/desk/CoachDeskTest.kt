// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.desk

import com.tennis.scoremanager.data.CoachBook
import com.tennis.scoremanager.data.CoachCourse
import com.tennis.scoremanager.data.CoachLesson
import com.tennis.scoremanager.data.Course
import com.tennis.scoremanager.data.CourseCard
import com.tennis.scoremanager.data.CourseSlot
import com.tennis.scoremanager.data.LessonPack
import com.tennis.scoremanager.data.PlayerProfile
import com.tennis.scoremanager.data.ServeSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class FakeCoach(var time: Long) : CoachData {
    var book = CoachBook()
    var people = listOf(PlayerProfile("s1", "Anna Neri", "Anna", "Neri", student = true), PlayerProfile("x", "Socio Qualunque"))
    val diary = mutableListOf<Pair<String, String>>()
    val marked = mutableListOf<Triple<String, String, Set<String>>>()
    val paid = mutableListOf<String>()
    override fun now() = time
    override val lang = "it"
    override fun coachName() = "Marco"
    override fun book() = book
    override fun players() = people
    override fun sessions() = emptyList<ServeSession>()
    override fun addDiary(student: String, text: String) { diary += student to text }
    override fun setNote(student: String, text: String) {}
    override fun markPackPaid(packId: String) { paid += packId }
    override fun setLessonDone(lessonId: String, done: Boolean) {}
    override fun markCourseLesson(courseId: String, key: String, start: Long, present: Set<String>, cancelled: Boolean, note: String) { marked += Triple(courseId, key, present) }
    override fun receiptPdf(packId: String) = "%PDF".toByteArray()
    override fun reportPdf(student: String, days: Int?) = "%PDF".toByteArray()
    override fun registerPdf(courseId: String) = "%PDF".toByteArray()
    override fun registerXlsx(courseId: String) = "PK".toByteArray()
    override fun receiptsXlsx(year: Int) = "PK".toByteArray()
}

class CoachDeskTest {
    private var t = 1_000_000L
    private val zone = ZoneId.of("Europe/Rome")
    private val now = java.time.LocalDate.of(2026, 11, 14).atTime(20, 0).atZone(zone).toInstant().toEpochMilli()
    private val auth = DeskAuth({ t })
    private val coach = FakeCoach(now)
    private val api = DeskApi(FakeDesk(now), auth, zone = { zone }, coach = CoachApi(coach) { zone })
    private val ip = "192.168.1.40"

    private fun req(m: String, p: String, body: String = "", token: String? = null, q: Map<String, String> = emptyMap()) =
        api.handle(DeskApi.Req(m, p, q, body, token, ip)).let { it.status to it.body.toString(Charsets.UTF_8) }
    private fun field(json: String, k: String) = Regex("\"$k\":\"([^\"]+)\"").find(json)?.groupValues?.get(1)
    private fun login(): String {
        val id = field(req("POST", "/pair", """{"code":"${auth.shownCode}"}""").second, "id")!!
        auth.answer(true)
        return field(req("GET", "/pair/$id").second, "token")!!
    }

    @Test fun needsPairingAndHidesTheClubOffice() {
        assertEquals(401, req("GET", "/coach/students").first)
        val tk = login()
        val info = req("GET", "/info", token = tk)
        assertTrue(info.second, "\"mode\":\"coach\"" in info.second)
        assertTrue("\"coach\":\"Marco\"" in info.second)
        // Le pagine della segreteria non esistono per l'allenatore.
        assertEquals(404, req("GET", "/people", token = tk).first)
        assertEquals(404, req("GET", "/money", token = tk).first)
    }

    @Test fun studentsOnlyAndTheirCard() {
        coach.book = CoachBook(packs = listOf(LessonPack("pk", "s1", 10, 25000, createdAt = now)))
        val tk = login()
        val list = req("GET", "/coach/students", token = tk).second
        assertTrue("Anna Neri" in list)
        assertTrue("Socio Qualunque" !in list)
        assertEquals(404, req("GET", "/coach/students/x", token = tk).first)
        assertEquals(200, req("POST", "/coach/students/s1/diary", """{"text":"Bene il dritto"}""", tk).first)
        assertEquals("s1" to "Bene il dritto", coach.diary.single())
        assertEquals(200, req("POST", "/coach/packs/pk/paid", "{}", tk).first)
        assertEquals(listOf("pk"), coach.paid)
        assertEquals(404, req("POST", "/coach/packs/zz/paid", "{}", tk).first)
        val pdf = api.handle(DeskApi.Req("GET", "/coach/packs/pk/receipt.pdf", emptyMap(), "", tk, ip))
        assertEquals("application/pdf", pdf.type)
    }

    @Test fun courseRegisterAndAttendanceOnlyForPastLessons() {
        val start = now - 3L * 86_400_000L
        val course = Course("c1", "Under 10", students = listOf("s1"), slots = (1..7).map { CourseSlot(it, 17 * 60) }, from = start, to = now + 10L * 86_400_000L)
        coach.book = CoachBook(courses = listOf(CoachCourse(CourseCard(code = "X", issuedAt = 1, course = course))), lessons = listOf(CoachLesson("l1", now + 86_400_000L, students = listOf("s1"))))
        val tk = login()
        val reg = req("GET", "/coach/courses/c1", token = tk).second
        val keys = Regex("\"key\":\"([^\"]+)\"").findAll(reg).map { it.groupValues[1] }.toList()
        assertTrue(keys.size >= 4)
        assertEquals(200, req("PUT", "/coach/courses/c1/attendance", """{"key":"${keys.first()}","present":["s1","intruso"]}""", tk).first)
        assertEquals(setOf("s1"), coach.marked.single().third)
        // Una lezione futura non si segna (si può solo annullare).
        assertEquals(400, req("PUT", "/coach/courses/c1/attendance", """{"key":"${keys.last()}","present":["s1"]}""", tk).first)
        assertTrue("Under 10" in req("GET", "/coach/courses", token = tk).second)
        assertTrue("\"from\":\"2026-11-01\"" in req("GET", "/coach/month", token = tk, q = mapOf("date" to "2026-11-14")).second)
        assertTrue(req("GET", "/coach/lessons", token = tk).second.contains("l1"))
        // Il calendario del mese: lezioni del corso e lezione privata, giorno per giorno.
        val cal = req("GET", "/coach/calendar", token = tk, q = mapOf("date" to "2026-11-14")).second
        assertTrue(cal, "\"first\":\"2026-11-01\"" in cal)
        assertTrue("\"kind\":\"course\"" in cal)
        assertTrue("\"kind\":\"lesson\"" in cal)
        assertEquals(30, Regex("\"date\":\"2026-11-").findAll(cal).count())
    }
}
