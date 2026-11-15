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
    val saved = mutableListOf<Course>()
    override fun saveOwnCourse(c: Course) {
        saved += c
        val old = book.courses.firstOrNull { it.card.course.id == c.id }
        book = book.copy(courses = book.courses.filter { it.card.course.id != c.id } + (old?.copy(card = old.card.copy(course = c)) ?: CoachCourse(CourseCard(code = "", issuedAt = 1, course = c), personal = true)))
    }
    override fun saveStudent(p: PlayerProfile) { people = people.filter { it.id != p.id } + p }
    override fun country() = "IT"
    override fun profileFields(id: String) = people.firstOrNull { it.id == id }?.let { p -> DeskProfileFields(com.tennis.scoremanager.data.ProfileCards.available(p, false), com.tennis.scoremanager.data.ProfileCards.defaults(p, false).toList()) }
    override fun profileFile(id: String, fields: Set<com.tennis.scoremanager.data.ProfileField>?) = people.firstOrNull { it.id == id }?.let { com.tennis.scoremanager.data.ProfileCards.encode(com.tennis.scoremanager.data.ProfileCards.card(it, fields ?: emptySet(), null, "Marco", time)) }
    override fun profilePreview(text: String) = com.tennis.scoremanager.data.ProfileCards.decode(text)?.let { DeskIncoming("PROFILE", "Profilo", listOf(it.profile.name), "", true) }
    override fun applyProfile(text: String) = com.tennis.scoremanager.data.ProfileCards.decode(text)?.let { c -> val p = com.tennis.scoremanager.data.ProfileCards.merge(null, c.profile, "nuovo", time, asStudent = true); people = people + p; p.id }
    override fun saveBusy(b: com.tennis.scoremanager.data.CoachBusy) { book = book.copy(busy = book.busy.filter { it.id != b.id } + b) }
    override fun deleteBusy(id: String) { book = book.copy(busy = book.busy.filter { it.id != id }) }
    override fun moveLesson(courseId: String, key: String, start: Long, minutes: Int?, court: String?) {
        book = book.copy(courses = book.courses.map { if (it.card.course.id == courseId) it.copy(card = it.card.copy(course = com.tennis.scoremanager.data.Courses.move(it.card.course, key, start, minutes, court))) else it })
    }
    val colors = mutableMapOf<String, String>()
    override fun setCourseColor(id: String, color: String) { colors[id] = color }
    override fun deleteOwnCourse(id: String) { book = book.copy(courses = book.courses.filter { it.card.course.id != id }) }
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

    @Test fun ownCoursesFromThePcButNotTheClubOnes() {
        coach.book = CoachBook(courses = listOf(CoachCourse(CourseCard(code = "X", issuedAt = 1, course = Course("club", "Under 10", from = now, to = now)))))
        val tk = login()
        val body = """{"name":"Preagonistica","level":"Base","from":"2026-11-16","to":"2027-05-31","slots":[{"weekday":3,"startMin":1020,"minutes":90,"court":"Campo 2"}],"students":["s1","x","nessuno"]}"""
        val made = req("POST", "/coach/courses", body, tk)
        assertEquals(200, made.first)
        assertTrue(made.second, "\"personal\":true" in made.second)
        val c = coach.saved.single()
        assertEquals(listOf("s1"), c.students)
        assertEquals(90, c.slots.single().minutes)
        // Senza orari o senza nome no; il corso della segreteria non si cambia né si toglie dal PC.
        assertEquals(400, req("POST", "/coach/courses", """{"name":"X","from":"2026-11-16","to":"2026-12-01","slots":[]}""", tk).first)
        assertEquals(409, req("PUT", "/coach/courses/club", body, tk).first)
        assertEquals(409, req("DELETE", "/coach/courses/club", token = tk).first)
        assertEquals(200, req("PUT", "/coach/courses/${c.id}", body.replace("Preagonistica", "Agonistica"), tk).first)
        assertEquals("Agonistica", coach.saved.last().name)
        // L'etichetta colorata: solo dei colori previsti, per qualsiasi corso.
        assertEquals(200, req("PUT", "/coach/courses/club/color", """{"text":"#FF9800"}""", tk).first)
        assertEquals("#FF9800", coach.colors["club"])
        assertEquals(400, req("PUT", "/coach/courses/club/color", """{"text":"#123456"}""", tk).first)
        assertEquals(200, req("DELETE", "/coach/courses/${c.id}", token = tk).first)
        assertTrue(coach.book.courses.none { it.card.course.id == c.id })
    }

    @Test fun lessonsCountGivesTheEndAndSkipsHolidays() {
        val tk = login()
        // Il martedì dal 1° dicembre 2026: l'8 è l'Immacolata.
        val base = """{"name":"Mini","from":"2026-12-01","slots":[{"weekday":2,"startMin":1020,"minutes":60,"court":""}],"lessons":2"""
        assertTrue(req("POST", "/coach/courses/end", "$base}", tk).second.contains("2026-12-15"))
        assertTrue(req("POST", "/coach/courses/end", "$base,\"skipHolidays\":false}", tk).second.contains("2026-12-08"))
        assertEquals(200, req("POST", "/coach/courses", "$base}", tk).first)
        val c = coach.saved.last()
        assertEquals(java.time.LocalDate.of(2026, 12, 15), java.time.Instant.ofEpochMilli(c.to).atZone(zone).toLocalDate())
        assertEquals(1, c.cancelled.size)
        val cal = req("GET", "/coach/calendar", token = tk, q = mapOf("date" to "2026-12-01")).second
        assertTrue(cal, "\"holiday\":\"Immacolata Concezione\"" in cal)
        coach.time = java.time.LocalDate.of(2026, 12, 9).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        val reg = req("GET", "/coach/courses/${c.id}", token = tk).second
        assertTrue(reg, "Immacolata" in reg)
    }

    @Test fun busyDaysShowAndAreSkipped() {
        val tk = login()
        // Occupato dal 15 al 16 dicembre 2026 (un martedì e un mercoledì).
        val made = req("POST", "/coach/busy", """{"from":"2026-12-16","to":"2026-12-15","kind":"VACATION","note":"Campo estivo"}""", tk)
        assertEquals(200, made.first)
        val b = coach.book.busy.single()
        assertEquals(java.time.LocalDate.of(2026, 12, 15), java.time.Instant.ofEpochMilli(b.from).atZone(zone).toLocalDate())
        val base = """{"name":"Mini","from":"2026-12-01","slots":[{"weekday":2,"startMin":1020,"minutes":60,"court":""}],"lessons":2"""
        // L'8 è festa, il 15 occupato: con entrambi la seconda lezione è il 22.
        assertTrue(req("POST", "/coach/courses/end", "$base,\"skipBusy\":true}", tk).second.contains("2026-12-22"))
        assertTrue(req("POST", "/coach/courses/end", "$base}", tk).second.contains("2026-12-15"))
        assertEquals(200, req("POST", "/coach/courses", "$base}", tk).first)
        val list = req("GET", "/coach/busy", token = tk).second
        assertTrue(list, "\"clashes\":[{\"date\":\"2026-12-15\"" in list)
        val cal = req("GET", "/coach/calendar", token = tk, q = mapOf("date" to "2026-12-01")).second
        assertTrue(cal, "Campo estivo" in cal)
        assertEquals(200, req("DELETE", "/coach/busy/${b.id}", token = tk).first)
        assertTrue(coach.book.busy.isEmpty())
        assertEquals(404, req("DELETE", "/coach/busy/${b.id}", token = tk).first)
    }

    @Test fun ownCourseLessonMovesFromThePc() {
        val tk = login()
        assertEquals(200, req("POST", "/coach/courses", """{"name":"Mini","from":"2026-11-17","slots":[{"weekday":2,"startMin":1020,"minutes":60,"court":""}],"lessons":3,"skipHolidays":false}""", tk).first)
        val c = coach.book.courses.single { it.card.course.name == "Mini" }.card.course
        val first = com.tennis.scoremanager.data.Courses.lessons(c).first()
        val out = req("POST", "/coach/courses/${c.id}/move", """{"key":"${first.key}","date":"2026-11-19","startMin":1080}""", tk).second
        assertTrue(out, "\"movedFrom\":\"2026-11-17\"" in out)
        // Quelli della segreteria no.
        coach.book = coach.book.copy(courses = coach.book.courses + CoachCourse(CourseCard(code = "X", issuedAt = 1, course = Course("club", "Under 10", slots = c.slots, from = c.from, to = c.to))))
        assertEquals(409, req("POST", "/coach/courses/club/move", """{"key":"${first.key}","makeup":true}""", tk).first)
    }

    @Test fun studentProfileFromAndToThePc() {
        val tk = login()
        val file = req("GET", "/coach/students/s1/profile.tsm", token = tk).second
        assertTrue(file, "Anna" in file)
        assertEquals(404, req("GET", "/coach/students/x/profile.tsm", token = tk).first)   // non è un allievo
        val other = kotlinx.serialization.json.JsonPrimitive(file.replace("Anna", "Bea")).toString()
        assertTrue(req("POST", "/coach/profile/check", "{\"text\":$other}", tk).second.contains("PROFILE"))
        assertEquals(200, req("POST", "/coach/profile/apply", "{\"text\":$other}", tk).first)
        assertTrue(coach.people.any { it.firstName == "Bea" && it.student })
    }

    @Test fun studentsFromThePcWithoutDuplicates() {
        val tk = login()
        val made = req("POST", "/coach/students", """{"firstName":"Giulia","lastName":"Verdi","guardian":"Marta Verdi","guardianPhone":"3331234567","consent":true}""", tk)
        assertEquals(200, made.first)
        val g = coach.people.single { it.lastName == "Verdi" }
        assertTrue(g.student)
        assertEquals("Marta Verdi", g.guardian)
        assertEquals(now, g.consentAt)
        assertEquals(400, req("POST", "/coach/students", """{"firstName":"Solo"}""", tk).first)
        // Un giocatore che c'è già (stesso nome e cognome) diventa allievo, senza doppioni.
        coach.people = coach.people + PlayerProfile("q", "Paolo Rossi", "Paolo", "Rossi", phone = "333000")
        req("POST", "/coach/students", """{"firstName":"Paolo","lastName":"Rossi","phone":"999"}""", tk)
        val p = coach.people.filter { it.lastName == "Rossi" }
        assertEquals(1, p.size)
        assertTrue(p[0].student)
        assertEquals("333000", p[0].phone)
        // Modifica: il soprannome cambia il nome che si vede; senza consenso la data si toglie.
        assertEquals(200, req("PUT", "/coach/students/${g.id}", """{"firstName":"Giulia","lastName":"Verdi","nickname":"Giuly","consent":false}""", tk).first)
        val g2 = coach.people.single { it.id == g.id }
        assertEquals("Giuly", g2.name)
        assertEquals(null, g2.consentAt)
    }
}
