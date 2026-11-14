// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.desk

import com.tennis.scoremanager.data.CoachBook
import com.tennis.scoremanager.data.Coaching
import com.tennis.scoremanager.data.Courses
import com.tennis.scoremanager.data.PlayerProfile
import com.tennis.scoremanager.data.ServeSession
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.ZoneId

/** Quello che l'Allenatore da PC legge e cambia: i dati veri dell'app (vedi CoachBridge). */
interface CoachData {
    fun now(): Long
    val lang: String
    fun coachName(): String
    fun book(): CoachBook
    fun players(): List<PlayerProfile>
    fun sessions(): List<ServeSession>
    fun addDiary(student: String, text: String)
    fun setNote(student: String, text: String)
    fun markPackPaid(packId: String)
    fun setLessonDone(lessonId: String, done: Boolean)
    fun markCourseLesson(courseId: String, key: String, start: Long, present: Set<String>, cancelled: Boolean, note: String)
    /** Un corso dell'allenatore (non della segreteria): nuovo o cambiato, o tolto. */
    fun saveOwnCourse(c: com.tennis.scoremanager.data.Course) {}
    fun deleteOwnCourse(id: String) {}
    /** L'etichetta colorata di un corso (uno dei [com.tennis.scoremanager.data.CourseCards.COLORS]). */
    fun setCourseColor(id: String, color: String) {}
    /** Salva un allievo (nuovo o cambiato) nel database dei giocatori. */
    fun saveStudent(p: PlayerProfile) {}
    fun receiptPdf(packId: String): ByteArray?
    fun reportPdf(student: String, days: Int?): ByteArray?
    fun registerPdf(courseId: String): ByteArray?
    fun registerXlsx(courseId: String): ByteArray?
    fun receiptsXlsx(year: Int): ByteArray?
}

@Serializable data class CdBalance(val bought: Int, val used: Int, val left: Int, val dueCents: Int, val absences: Int, val toRecover: Int)
@Serializable data class CdStudent(
    val id: String, val name: String, val phone: String = "", val email: String = "", val guardian: String = "", val guardianPhone: String = "",
    val lastSeen: String? = null, val ranking: String? = null, val trend: Double? = null, val balance: CdBalance, val next: String? = null, val nextMin: Int? = null,
)
@Serializable data class CdDiary(val date: String, val text: String)
@Serializable data class CdEval(val date: String, val scores: Map<String, Int>, val note: String = "")
@Serializable data class CdTest(val key: String, val value: Double, val previous: Double? = null, val lowerBetter: Boolean)
@Serializable data class CdResult(val date: String, val event: String, val opponent: String, val opponentRanking: String, val score: String, val won: Boolean)
@Serializable data class CdEvent(val date: String, val name: String, val place: String)
@Serializable data class CdPack(val id: String, val lessons: Int, val cents: Int, val created: String, val paid: String? = null, val receipt: String? = null)
@Serializable data class CdCourseLine(val name: String, val present: Int, val total: Int)
/** Un allievo da creare o cambiare dal PC: chi è, i contatti, il genitore (per i minorenni) e il consenso. */
@Serializable data class CdStudentIn(
    val firstName: String = "", val lastName: String = "", val nickname: String = "", val phone: String = "", val email: String = "",
    val guardian: String = "", val guardianPhone: String = "", val consent: Boolean = false,
)
@Serializable data class CdStudentDetail(
    val firstName: String = "", val lastName: String = "", val nickname: String = "",
    val student: CdStudent, val note: String, val consent: String? = null, val diary: List<CdDiary>, val evaluations: List<CdEval>, val tests: List<CdTest>,
    val testDate: String? = null, val results: List<CdResult>, val events: List<CdEvent>, val rankings: List<String>, val packs: List<CdPack>, val courses: List<CdCourseLine>,
)
@Serializable data class CdLesson(val id: String, val date: String, val startMin: Int, val minutes: Int, val students: List<String>, val place: String, val done: Boolean, val cancelled: Boolean)
@Serializable data class CdSlot(val weekday: Int, val startMin: Int, val minutes: Int = 60, val court: String = "")
@Serializable data class CdCourse(
    val id: String, val name: String, val level: String, val club: String, val from: String, val to: String, val students: Int, val toSend: Boolean, val toMark: Int,
    val personal: Boolean = false, val slots: List<CdSlot> = emptyList(), val studentIds: List<String> = emptyList(),
    val color: String = com.tennis.scoremanager.data.CourseCards.CLUB, val colors: List<String> = com.tennis.scoremanager.data.CourseCards.COLORS,
)
/** Un corso dell'allenatore da creare o cambiare dal PC. */
@Serializable data class CdCourseIn(
    val name: String = "", val level: String = "", val from: String = "", val to: String = "", val slots: List<CdSlot> = emptyList(), val students: List<String> = emptyList(),
    val color: String? = null,
)
@Serializable data class CdRegLesson(val key: String, val date: String, val startMin: Int, val cancelled: Boolean, val marked: Boolean, val past: Boolean)
@Serializable data class CdRegRow(val id: String, val name: String, val marks: List<String>, val present: Int, val total: Int)
@Serializable data class CdRegister(val course: CdCourse, val lessons: List<CdRegLesson>, val rows: List<CdRegRow>, val notes: Map<String, String>)
@Serializable data class CdMonth(
    val from: String, val privateLessons: Int, val privateMinutes: Int, val students: Int, val paidCents: Int,
    val courseLessons: Int, val courseMinutes: Int, val presencePct: Int? = null, val receipts: List<CdReceipt>,
)
@Serializable data class CdReceipt(val packId: String, val number: String, val date: String, val name: String, val lessons: Int, val cents: Int)
/**
 * Una cosa nel calendario dell'allenatore: [kind] "lesson" (lezione privata, [ref] = id), "course" (lezione di un corso,
 * [ref] = id del corso, [key] la lezione), "event" (torneo di un allievo, [ref] = id dell'allievo).
 */
@Serializable data class CdCalItem(
    val kind: String, val ref: String, val key: String = "", val startMin: Int? = null, val minutes: Int = 0, val title: String, val place: String = "",
    val done: Boolean = false, val cancelled: Boolean = false, val past: Boolean = false,
    /** Il colore del corso (per le lezioni dei corsi). */
    val color: String? = null,
)
@Serializable data class CdCalDay(val date: String, val items: List<CdCalItem>)
@Serializable data class CdCal(val first: String, val days: List<CdCalDay>)
@Serializable data class CdText(val text: String = "")
@Serializable data class CdAttendanceIn(val key: String, val present: List<String> = emptyList(), val cancelled: Boolean = false, val note: String = "")
@Serializable data class CdDoneIn(val done: Boolean = true)
@Serializable data class CdInfo(val mode: String = "coach", val club: String, val lang: String, val today: String, val year: Int, val coach: String)

/**
 * L'API dell'Allenatore da PC (sotto /api/, dopo l'abbinamento come per la segreteria): allievi con la loro scheda,
 * agenda, corsi col registro, riepilogo del mese; PDF ed Excel fatti dal telefono. Nessun dato della segreteria.
 */
class CoachApi(private val data: CoachData, private val zone: () -> ZoneId = ZoneId::systemDefault) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
    private fun <T> ok(ser: KSerializer<T>, v: T) = DeskApi.Resp(200, json.encodeToString(ser, v).toByteArray())
    private fun done() = DeskApi.Resp(200, "{}".toByteArray())
    private fun <T> read(ser: KSerializer<T>, r: DeskApi.Req): T = try { json.decodeFromString(ser, r.body) } catch (_: Exception) { throw DeskApi.Fail(400, "BAD_REQUEST") }
    private fun bytes(b: ByteArray?, type: String) = DeskApi.Resp(200, b ?: throw DeskApi.Fail(404, "NOT_FOUND"), type)

    private fun day(t: Long?): String? = t?.let { Instant.ofEpochMilli(it).atZone(zone()).toLocalDate().toString() }
    private fun minOf(t: Long): Int = Instant.ofEpochMilli(t).atZone(zone()).let { it.hour * 60 + it.minute }
    private fun dayStart(d: String?): Long = try { java.time.LocalDate.parse(d).atStartOfDay(zone()).toInstant().toEpochMilli() } catch (_: Exception) { throw DeskApi.Fail(400, "BAD_REQUEST") }

    fun route(m: String, p: List<String>, r: DeskApi.Req, club: String): DeskApi.Resp = when {
        m == "GET" && p == listOf("info") -> ok(CdInfo.serializer(), CdInfo(club = club, lang = data.lang, today = day(data.now())!!, year = Instant.ofEpochMilli(data.now()).atZone(zone()).year, coach = data.coachName()))
        m == "GET" && p == listOf("coach", "students") -> ok(ListSerializer(CdStudent.serializer()), students().sortedBy { it.name.lowercase() })
        m == "GET" && p.size == 3 && p[0] == "coach" && p[1] == "students" -> ok(CdStudentDetail.serializer(), detail(p[2]))
        m == "POST" && p == listOf("coach", "students") -> saveStudent(null, read(CdStudentIn.serializer(), r))
        m == "PUT" && p.size == 3 && p[0] == "coach" && p[1] == "students" -> saveStudent(student(p[2]), read(CdStudentIn.serializer(), r))
        m == "POST" && p.size == 4 && p[0] == "coach" && p[1] == "students" && p[3] == "diary" -> { student(p[2]); data.addDiary(p[2], read(CdText.serializer(), r).text); done() }
        m == "PUT" && p.size == 4 && p[0] == "coach" && p[1] == "students" && p[3] == "note" -> { student(p[2]); data.setNote(p[2], read(CdText.serializer(), r).text); done() }
        m == "GET" && p.size == 4 && p[0] == "coach" && p[1] == "students" && p[3] == "report.pdf" ->
            bytes(data.reportPdf(student(p[2]).id, r.query["days"]?.toIntOrNull()), "application/pdf")
        m == "POST" && p.size == 4 && p[0] == "coach" && p[1] == "packs" && p[3] == "paid" -> { pack(p[2]); data.markPackPaid(p[2]); done() }
        m == "GET" && p.size == 4 && p[0] == "coach" && p[1] == "packs" && p[3] == "receipt.pdf" -> { pack(p[2]); bytes(data.receiptPdf(p[2]), "application/pdf") }
        m == "GET" && p == listOf("coach", "lessons") -> ok(ListSerializer(CdLesson.serializer()), lessons())
        m == "PUT" && p.size == 4 && p[0] == "coach" && p[1] == "lessons" && p[3] == "done" -> {
            if (data.book().lessons.none { it.id == p[2] }) throw DeskApi.Fail(404, "NOT_FOUND")
            data.setLessonDone(p[2], read(CdDoneIn.serializer(), r).done); done()
        }
        m == "GET" && p == listOf("coach", "courses") -> ok(ListSerializer(CdCourse.serializer()), data.book().courses.map { course(it.card.course.id) }.sortedBy { it.name.lowercase() })
        m == "GET" && p.size == 3 && p[0] == "coach" && p[1] == "courses" -> ok(CdRegister.serializer(), register(p[2]))
        m == "POST" && p == listOf("coach", "courses") -> ownCourse(null, read(CdCourseIn.serializer(), r))
        m == "PUT" && p.size == 3 && p[0] == "coach" && p[1] == "courses" -> ownCourse(p[2], read(CdCourseIn.serializer(), r))
        m == "PUT" && p.size == 4 && p[0] == "coach" && p[1] == "courses" && p[3] == "color" -> {
            if (data.book().courses.none { it.card.course.id == p[2] }) throw DeskApi.Fail(404, "NOT_FOUND")
            val color = read(CdText.serializer(), r).text
            if (color !in com.tennis.scoremanager.data.CourseCards.COLORS) throw DeskApi.Fail(400, "BAD_REQUEST")
            data.setCourseColor(p[2], color); ok(CdCourse.serializer(), course(p[2]))
        }
        m == "DELETE" && p.size == 3 && p[0] == "coach" && p[1] == "courses" -> {
            val cc = data.book().courses.firstOrNull { it.card.course.id == p[2] } ?: throw DeskApi.Fail(404, "NOT_FOUND")
            if (!cc.personal) throw DeskApi.Fail(409, "NOT_OWN")
            data.deleteOwnCourse(p[2]); done()
        }
        m == "PUT" && p.size == 4 && p[0] == "coach" && p[1] == "courses" && p[3] == "attendance" -> attendance(p[2], read(CdAttendanceIn.serializer(), r))
        m == "GET" && p.size == 4 && p[0] == "coach" && p[1] == "courses" && p[3] == "register.pdf" -> { course(p[2]); bytes(data.registerPdf(p[2]), "application/pdf") }
        m == "GET" && p.size == 4 && p[0] == "coach" && p[1] == "courses" && p[3] == "register.xlsx" -> { course(p[2]); bytes(data.registerXlsx(p[2]), com.tennis.scoremanager.data.Xlsx.MIME) }
        m == "GET" && p == listOf("coach", "month") -> ok(CdMonth.serializer(), month(r.query["date"]))
        m == "GET" && p == listOf("coach", "calendar") -> ok(CdCal.serializer(), calendar(r.query["date"]))
        m == "GET" && p == listOf("coach", "receipts.xlsx") -> bytes(data.receiptsXlsx(r.query["year"]?.toIntOrNull() ?: throw DeskApi.Fail(400, "BAD_REQUEST")), com.tennis.scoremanager.data.Xlsx.MIME)
        else -> throw DeskApi.Fail(404, "NOT_FOUND")
    }

    private fun student(id: String): PlayerProfile = data.players().firstOrNull { it.id == id && it.student } ?: throw DeskApi.Fail(404, "NOT_FOUND")
    private fun pack(id: String) = data.book().packs.firstOrNull { it.id == id } ?: throw DeskApi.Fail(404, "NOT_FOUND")

    private fun studentOut(p: PlayerProfile, book: CoachBook, sessions: List<ServeSession>, now: Long): CdStudent {
        val b = Coaching.balance(book, p.id)
        val next = Coaching.upcoming(Coaching.lessonsOf(book, p.id), now).firstOrNull()
        return CdStudent(
            p.id, p.name, p.phone, p.email, p.guardian.ifBlank { p.member?.guardian.orEmpty() }, p.guardianPhone.ifBlank { p.member?.guardianPhone.orEmpty() },
            day(Coaching.lastSeen(book, sessions, p.id)), Coaching.ranking(book, p.id), Coaching.trend(book, p.id),
            CdBalance(b.bought, b.used, b.left, b.dueCents, b.absences, b.toRecover), day(next?.start), next?.let { minOf(it.start) },
        )
    }

    /**
     * Crea ([old] null) o cambia un allievo. Nome e cognome servono; se c'è già un giocatore con lo stesso nome e
     * cognome diventa allievo lui (niente doppioni), coi contatti nuovi dove mancavano.
     */
    private fun saveStudent(old: PlayerProfile?, i: CdStudentIn): DeskApi.Resp {
        val first = i.firstName.trim().take(40); val last = i.lastName.trim().take(40); val nick = i.nickname.trim().take(40)
        if (first.isEmpty() || last.isEmpty()) throw DeskApi.Fail(400, "BAD_REQUEST")
        val now = data.now()
        val same = if (old == null) com.tennis.scoremanager.data.Players.sameFullName(data.players(), first, last).singleOrNull() else null
        val base = old ?: same ?: PlayerProfile(com.tennis.scoremanager.data.Players.newId(), PlayerProfile.display(first, last, nick), first, last, nick, createdAt = now)
        val keep = same != null && old == null
        val p = base.copy(
            name = PlayerProfile.display(first, last, nick), firstName = first, lastName = last, nickname = nick, student = true,
            phone = if (keep) base.phone.ifBlank { i.phone.trim() } else i.phone.trim().take(30),
            email = if (keep) base.email.ifBlank { i.email.trim() } else i.email.trim().take(80),
            guardian = if (keep) base.guardian.ifBlank { i.guardian.trim() } else i.guardian.trim().take(60),
            guardianPhone = if (keep) base.guardianPhone.ifBlank { i.guardianPhone.trim() } else i.guardianPhone.trim().take(30),
            consentAt = if (i.consent) base.consentAt ?: now else if (keep) base.consentAt else null,
        )
        data.saveStudent(p)
        return ok(CdStudentDetail.serializer(), detail(p.id))
    }

    private fun students(): List<CdStudent> {
        val book = data.book(); val sessions = data.sessions(); val now = data.now()
        return data.players().filter { it.student }.map { studentOut(it, book, sessions, now) }
    }

    private fun detail(id: String): CdStudentDetail {
        val p = student(id)
        val book = data.book(); val now = data.now()
        val tests = Coaching.testsOf(book, id)
        return CdStudentDetail(
            p.firstName, p.lastName, p.nickname,
            studentOut(p, book, data.sessions(), now), p.note, day(p.consentAt),
            Coaching.diaryOf(book, id).take(40).map { CdDiary(day(it.at)!!, it.text) },
            Coaching.evaluationsOf(book, id).takeLast(6).map { CdEval(day(it.at)!!, it.scores, it.note) },
            Coaching.TESTS.mapNotNull { (k, lower) -> Coaching.lastTwo(tests, k).let { (v, prev) -> v?.let { CdTest(k, it, prev, lower) } } },
            day(tests.lastOrNull()?.at),
            book.manualMatches.filter { it.student == id }.sortedByDescending { it.at }.map { CdResult(day(it.at)!!, it.event, it.opponent, it.opponentRanking, it.score, it.won) },
            book.events.filter { it.student == id && it.at >= now - 86_400_000L }.sortedBy { it.at }.map { CdEvent(day(it.at)!!, it.name, it.place) },
            book.rankings.filter { it.student == id }.sortedBy { it.at }.map { "${day(it.at)} ${it.ranking}" },
            book.packs.filter { it.student == id }.sortedByDescending { it.createdAt }.map { CdPack(it.id, it.lessons, it.cents, day(it.createdAt)!!, day(it.paidAt), it.receipt) },
            book.courses.filter { id in it.card.course.students }.map { cc -> Courses.attendanceOf(cc.course, id, now).let { (a, t) -> CdCourseLine(cc.card.course.name, a, t) } },
        )
    }

    private fun lessons(): List<CdLesson> {
        val book = data.book(); val now = data.now()
        val names = data.players().associate { it.id to it.name }
        // Le prossime e quelle passate da 60 giorni in qua (le passate non fatte sono da segnare).
        return book.lessons.filter { it.start >= now - 60L * 86_400_000L }.sortedBy { it.start }.map { l ->
            CdLesson(l.id, day(l.start)!!, minOf(l.start), l.minutes, l.students.map { names[it] ?: "—" }, l.place, l.done, l.cancelled)
        }
    }

    private fun course(id: String): CdCourse {
        val cc = data.book().courses.firstOrNull { it.card.course.id == id } ?: throw DeskApi.Fail(404, "NOT_FOUND")
        val co = cc.course; val now = data.now()
        return CdCourse(co.id, co.name, co.level, cc.card.club, day(co.from)!!, day(co.to)!!, co.students.size, cc.toSend,
            Courses.held(co, now).count { it.key !in co.attendance && it.key !in co.cancelled }, cc.personal,
            co.slots.map { CdSlot(it.weekday, it.startMin, it.minutes, it.court) }, co.students, cc.label)
    }

    private fun register(id: String): CdRegister {
        val head = course(id)
        val cc = data.book().courses.first { it.card.course.id == id }
        val co = cc.course; val now = data.now()
        val names = data.players().associate { it.id to it.name } + cc.card.students.associate { it.id to it.name }.filterKeys { k -> data.players().none { it.id == k } }
        val lessons = Courses.lessons(co).filter { it.start <= now + 7L * 86_400_000L }
        return CdRegister(
            head,
            lessons.map { CdRegLesson(it.key, day(it.start)!!, minOf(it.start), it.key in co.cancelled, it.key in co.attendance, it.start <= now + 3_600_000L) },
            co.students.sortedBy { (names[it] ?: "").lowercase() }.map { sid ->
                val (a, t) = Courses.attendanceOf(co, sid, now)
                CdRegRow(sid, names[sid] ?: "—", lessons.map { com.tennis.scoremanager.data.AttendancePdf.mark(co, it.key, sid) }, a, t)
            },
            cc.notes,
        )
    }

    /** Crea ([id] null) o cambia un corso dell'allenatore; quelli della segreteria non si toccano. */
    private fun ownCourse(id: String?, i: CdCourseIn): DeskApi.Resp {
        val name = i.name.trim().take(60)
        if (name.isEmpty() || i.slots.isEmpty() || i.slots.any { it.weekday !in 1..7 || it.startMin !in 0 until 24 * 60 || it.minutes !in 15..300 }) throw DeskApi.Fail(400, "BAD_REQUEST")
        val from = dayStart(i.from); val to = dayStart(i.to)
        if (to < from) throw DeskApi.Fail(400, "BAD_REQUEST")
        val students = i.students.filter { s -> data.players().any { it.id == s && it.student } }.distinct()
        val old = id?.let { x -> data.book().courses.firstOrNull { it.card.course.id == x } ?: throw DeskApi.Fail(404, "NOT_FOUND") }
        if (old != null && !old.personal) throw DeskApi.Fail(409, "NOT_OWN")
        val base = old?.card?.course ?: com.tennis.scoremanager.data.Course(Coaching.newId("cc"), name, from = from, to = to, createdAt = data.now())
        val co = base.copy(name = name, level = i.level.trim().take(40), from = from, to = to,
            slots = i.slots.map { com.tennis.scoremanager.data.CourseSlot(it.weekday, it.startMin, it.minutes, it.court.trim().take(30)) }.distinct().sortedWith(compareBy({ it.weekday }, { it.startMin })),
            students = students)
        data.saveOwnCourse(co)
        i.color?.takeIf { it in com.tennis.scoremanager.data.CourseCards.COLORS }?.let { data.setCourseColor(co.id, it) }
        return ok(CdCourse.serializer(), course(co.id))
    }

    private fun attendance(id: String, a: CdAttendanceIn): DeskApi.Resp {
        val cc = data.book().courses.firstOrNull { it.card.course.id == id } ?: throw DeskApi.Fail(404, "NOT_FOUND")
        val l = Courses.lessons(cc.course).firstOrNull { it.key == a.key } ?: throw DeskApi.Fail(404, "NOT_FOUND")
        if (!a.cancelled && l.start > data.now() + 3_600_000L) throw DeskApi.Fail(400, "BAD_REQUEST")
        data.markCourseLesson(id, a.key, l.start, a.present.filter { it in cc.card.course.students }.toSet(), a.cancelled, a.note)
        return ok(CdRegister.serializer(), register(id))
    }

    /** Il mese di [date], giorno per giorno: lezioni private, lezioni dei corsi e tornei degli allievi, in ordine d'ora. */
    private fun calendar(date: String?): CdCal {
        val start = Instant.ofEpochMilli(dayStart(date)).atZone(zone()).toLocalDate().withDayOfMonth(1)
        val f = start.atStartOfDay(zone()).toInstant().toEpochMilli()
        val t = start.plusMonths(1).atStartOfDay(zone()).toInstant().toEpochMilli()
        val book = data.book(); val now = data.now()
        val names = data.players().associate { it.id to it.name }
        val items = mutableListOf<Pair<Long, CdCalItem>>()
        for (l in book.lessons.filter { it.start in f until t }) items += l.start to CdCalItem("lesson", l.id, "", minOf(l.start), l.minutes,
            l.students.joinToString(", ") { names[it] ?: "—" }, l.place, l.done, l.cancelled, l.start <= now)
        for (cc in book.courses) {
            val co = cc.course
            for (les in Courses.lessons(co).filter { it.start in f until t }) items += les.start to CdCalItem(if (cc.personal) "own" else "course", co.id, les.key, minOf(les.start), les.minutes,
                co.name, les.court, les.key in co.attendance, les.key in co.cancelled, les.start <= now, cc.label)
        }
        for (e in book.events.filter { it.at in f until t }) items += e.at to CdCalItem("event", e.student, "", null, 0, "${e.name} · ${names[e.student] ?: ""}", e.place)
        val byDay = items.sortedBy { it.first }.groupBy { day(it.first)!! }
        val days = generateSequence(start) { it.plusDays(1) }.takeWhile { it.isBefore(start.plusMonths(1)) }
            .map { d -> CdCalDay(d.toString(), byDay[d.toString()].orEmpty().map { it.second }) }.toList()
        return CdCal(start.toString(), days)
    }

    private fun month(date: String?): CdMonth {
        val from = dayStart(date)
        val start = Instant.ofEpochMilli(from).atZone(zone()).toLocalDate().withDayOfMonth(1)
        val f = start.atStartOfDay(zone()).toInstant().toEpochMilli()
        val t = start.plusMonths(1).atStartOfDay(zone()).toInstant().toEpochMilli()
        val book = data.book()
        val m = Coaching.month(book, f, t)
        val names = data.players().associate { it.id to it.name }
        val year = start.year
        val receipts = book.packs.filter { it.receipt != null && it.paidAt != null && Instant.ofEpochMilli(it.paidAt).atZone(zone()).year == year }.sortedBy { it.receipt }
            .map { CdReceipt(it.id, it.receipt!!, day(it.paidAt)!!, names[it.student] ?: "—", it.lessons, it.cents) }
        return CdMonth(start.toString(), m.privateLessons, m.privateMinutes, m.students, m.paidCents, m.courseLessons, m.courseMinutes, m.presencePct, receipts)
    }
}
