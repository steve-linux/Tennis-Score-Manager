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
    fun saveBusy(b: com.tennis.scoremanager.data.CoachBusy) {}
    fun deleteBusy(id: String) {}
    /** Sposta una lezione di un corso dell'allenatore (solo i suoi). */
    fun moveLesson(courseId: String, key: String, start: Long, minutes: Int?, court: String?) {}
    /** Salva un allievo (nuovo o cambiato) nel database dei giocatori. */
    fun saveStudent(p: PlayerProfile) {}
    /** Il profilo dell'allievo da passare (vedi [DeskData.profileFields]) e quello caricato dal PC. */
    fun profileFields(id: String): DeskProfileFields? = null
    fun profileFile(id: String, fields: Set<com.tennis.scoremanager.data.ProfileField>?): String? = null
    fun profilePreview(text: String): DeskIncoming? = null
    fun applyProfile(text: String): String? = null
    /** Il Paese per le festività nazionali (quello del telefono). */
    fun country(): String = com.tennis.scoremanager.data.Holidays.country()
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
    /** Quante lezioni (la fine si calcola da inizio e orari) e se le feste nazionali non contano (e risultano annullate). */
    val lessons: Int? = null, val skipHolidays: Boolean = true, val skipBusy: Boolean = false,
)
@Serializable data class CdEnd(val to: String? = null)
@Serializable data class CdRegLesson(val key: String, val date: String, val startMin: Int, val cancelled: Boolean, val marked: Boolean, val past: Boolean, val holiday: String? = null, val busy: CdBusy? = null,
    val movedFrom: String? = null, val movedFromMin: Int? = null)
@Serializable data class CdMoveIn(val key: String, val date: String = "", val startMin: Int = 0, val makeup: Boolean = false, val reset: Boolean = false)
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
@Serializable data class CdCalDay(val date: String, val items: List<CdCalItem>, val holiday: String? = null, val busy: CdBusy? = null)
@Serializable data class CdCal(val first: String, val days: List<CdCalDay>)
@Serializable data class CdText(val text: String = "")
/** Un gruppo di allievi dell'allenatore (per aggiungerli tutti insieme a un corso). */
@Serializable data class CdGroup(val id: String, val name: String, val students: List<String>)
/** Un impegno dell'allenatore (giorni occupati) e le lezioni che ci cadono. */
@Serializable data class CdBusy(val id: String, val from: String, val to: String, val kind: com.tennis.scoremanager.data.BusyKind, val note: String = "")
@Serializable data class CdBusyIn(val from: String = "", val to: String = "", val kind: com.tennis.scoremanager.data.BusyKind = com.tennis.scoremanager.data.BusyKind.BUSY, val note: String = "")
@Serializable data class CdClash(val date: String, val startMin: Int, val title: String, val busyId: String, val courseId: String? = null)
@Serializable data class CdBusyList(val items: List<CdBusy>, val clashes: List<CdClash>)
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
        m == "GET" && p == listOf("coach", "busy") -> ok(CdBusyList.serializer(), busyList())
        m == "POST" && p == listOf("coach", "busy") -> saveBusy(null, read(CdBusyIn.serializer(), r))
        m == "PUT" && p.size == 3 && p[0] == "coach" && p[1] == "busy" -> saveBusy(p[2], read(CdBusyIn.serializer(), r))
        m == "DELETE" && p.size == 3 && p[0] == "coach" && p[1] == "busy" -> {
            if (data.book().busy.none { it.id == p[2] }) throw DeskApi.Fail(404, "NOT_FOUND")
            data.deleteBusy(p[2]); ok(CdBusyList.serializer(), busyList())
        }
        m == "GET" && p == listOf("coach", "groups") -> ok(ListSerializer(CdGroup.serializer()), data.book().groups.sortedBy { it.name.lowercase() }.map { CdGroup(it.id, it.name, it.students) })
        m == "GET" && p.size == 3 && p[0] == "coach" && p[1] == "students" -> ok(CdStudentDetail.serializer(), detail(p[2]))
        m == "POST" && p == listOf("coach", "students") -> saveStudent(null, read(CdStudentIn.serializer(), r))
        m == "GET" && p.size == 4 && p[0] == "coach" && p[1] == "students" && p[3] == "profile" -> {
            if (data.players().none { it.id == p[2] && it.student }) throw DeskApi.Fail(404, "NOT_FOUND")
            ok(DeskProfileFields.serializer(), data.profileFields(p[2]) ?: throw DeskApi.Fail(404, "NOT_FOUND"))
        }
        m == "GET" && p.size == 4 && p[0] == "coach" && p[1] == "students" && p[3] == "profile.tsm" -> {
            if (data.players().none { it.id == p[2] && it.student }) throw DeskApi.Fail(404, "NOT_FOUND")
            val f = r.query["fields"]?.split(',')?.mapNotNull { x -> runCatching { com.tennis.scoremanager.data.ProfileField.valueOf(x.trim()) }.getOrNull() }?.toSet()
            DeskApi.Resp(200, (data.profileFile(p[2], f) ?: throw DeskApi.Fail(404, "NOT_FOUND")).toByteArray(), "application/octet-stream")
        }
        m == "POST" && p == listOf("coach", "profile", "check") -> ok(DeskIncoming.serializer(), data.profilePreview(read(DeskFileIn.serializer(), r).text) ?: throw DeskApi.Fail(400, "NOT_A_FILE"))
        m == "POST" && p == listOf("coach", "profile", "apply") -> ok(DeskId.serializer(), DeskId(data.applyProfile(read(DeskFileIn.serializer(), r).text) ?: throw DeskApi.Fail(400, "NOT_A_FILE")))
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
        m == "POST" && p == listOf("coach", "courses", "end") -> read(CdCourseIn.serializer(), r).let { i -> ok(CdEnd.serializer(), CdEnd(day(endOf(i)))) }
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
        m == "POST" && p.size == 4 && p[0] == "coach" && p[1] == "courses" && p[3] == "move" -> moveLesson(p[2], read(CdMoveIn.serializer(), r))
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
            lessons.map { CdRegLesson(it.key, day(it.start)!!, minOf(it.start), it.key in co.cancelled, it.key in co.attendance, it.start <= now + 3_600_000L,
                com.tennis.scoremanager.data.Holidays.on(it.start, data.country(), zone()), Coaching.busyOn(data.book().busy, it.start)?.let(::busyOut),
                it.movedFrom?.let { f -> day(f) }, it.movedFrom?.let { f -> minOf(f) }) },
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
        val from = dayStart(i.from)
        val to = endOf(i) ?: dayStart(i.to)
        if (to < from) throw DeskApi.Fail(400, "BAD_REQUEST")
        val students = i.students.filter { s -> data.players().any { it.id == s && it.student } }.distinct()
        val old = id?.let { x -> data.book().courses.firstOrNull { it.card.course.id == x } ?: throw DeskApi.Fail(404, "NOT_FOUND") }
        if (old != null && !old.personal) throw DeskApi.Fail(409, "NOT_OWN")
        val base = old?.card?.course ?: com.tennis.scoremanager.data.Course(Coaching.newId("cc"), name, from = from, to = to, createdAt = data.now())
        val co0 = base.copy(name = name, level = i.level.trim().take(40), from = from, to = to,
            slots = i.slots.map { com.tennis.scoremanager.data.CourseSlot(it.weekday, it.startMin, it.minutes, it.court.trim().take(30)) }.distinct().sortedWith(compareBy({ it.weekday }, { it.startMin })),
            students = students)
        // Le lezioni nei giorni di festa risultano annullate (si rimettono dall'appello).
        val co = co0.copy(cancelled = co0.cancelled + Courses.skipped(co0) { t -> skipDay(i, t) })
        data.saveOwnCourse(co)
        i.color?.takeIf { it in com.tennis.scoremanager.data.CourseCards.COLORS }?.let { data.setCourseColor(co.id, it) }
        return ok(CdCourse.serializer(), course(co.id))
    }

    /** La fine del corso dal numero di lezioni (saltando le feste, se richiesto); null senza numero o senza orari. */
    private fun endOf(i: CdCourseIn): Long? {
        val n = i.lessons ?: return null
        val from = runCatching { dayStart(i.from) }.getOrNull() ?: return null
        val slots = i.slots.filter { it.weekday in 1..7 }.map { com.tennis.scoremanager.data.CourseSlot(it.weekday, it.startMin, it.minutes, it.court) }
        return Courses.endAfter(from, slots, n) { t -> skipDay(i, t) }
    }

    /** Il giorno di [t] non conta per il corso: festa nazionale o impegno dell'allenatore, come chiesto. */
    private fun skipDay(i: CdCourseIn, t: Long): Boolean =
        (i.skipHolidays && com.tennis.scoremanager.data.Holidays.on(t, data.country(), zone()) != null) || (i.skipBusy && Coaching.busyOn(data.book().busy, t) != null)

    private fun busyOut(b: com.tennis.scoremanager.data.CoachBusy) = CdBusy(b.id, day(b.from)!!, day(b.to)!!, b.kind, b.note)

    /** Gli impegni da oggi in poi e le lezioni che ci cadono. */
    private fun busyList(): CdBusyList {
        val book = data.book(); val today = dayStart(day(data.now()))
        val names = data.players().associate { it.id to it.name }
        return CdBusyList(
            book.busy.filter { it.to >= today }.sortedBy { it.from }.map(::busyOut),
            Coaching.busyClashes(book, data.now()) { ids -> ids.joinToString(", ") { names[it] ?: "—" } }
                .map { CdClash(day(it.start)!!, minOf(it.start), it.title, it.busy.id, it.courseId) },
        )
    }

    private fun saveBusy(id: String?, i: CdBusyIn): DeskApi.Resp {
        val from = dayStart(i.from); val to = if (i.to.isBlank()) from else dayStart(i.to)
        if (id != null && data.book().busy.none { it.id == id }) throw DeskApi.Fail(404, "NOT_FOUND")
        data.saveBusy(com.tennis.scoremanager.data.CoachBusy(id ?: Coaching.newId("bz"), minOf(from, to), maxOf(from, to), i.kind, i.note.trim().take(200)))
        return ok(CdBusyList.serializer(), busyList())
    }

    private fun moveLesson(id: String, i: CdMoveIn): DeskApi.Resp {
        val cc = data.book().courses.firstOrNull { it.card.course.id == id } ?: throw DeskApi.Fail(404, "NOT_FOUND")
        if (!cc.personal) throw DeskApi.Fail(409, "NOT_OWN")
        val co = cc.card.course
        val l = Courses.lessons(co).firstOrNull { it.key == i.key } ?: throw DeskApi.Fail(404, "NOT_FOUND")
        when {
            i.reset -> l.movedFrom?.let { data.moveLesson(id, l.key, it, null, null) }
            i.makeup -> {
                val mk = Courses.makeup(co.copy(moved = co.moved - l.key)) { t -> com.tennis.scoremanager.data.Holidays.on(t, data.country(), zone()) != null } ?: throw DeskApi.Fail(409, "BAD_REQUEST")
                data.moveLesson(id, l.key, mk.start, mk.minutes, mk.court)
            }
            else -> {
                if (i.startMin !in 0 until 24 * 60) throw DeskApi.Fail(400, "BAD_REQUEST")
                data.moveLesson(id, l.key, dayStart(i.date) + i.startMin * 60_000L, null, null)
            }
        }
        return ok(CdRegister.serializer(), register(id))
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
        val holidays = com.tennis.scoremanager.data.Holidays.of(start.year, data.country())
        val days = generateSequence(start) { it.plusDays(1) }.takeWhile { it.isBefore(start.plusMonths(1)) }
            .map { d -> CdCalDay(d.toString(), byDay[d.toString()].orEmpty().map { it.second }, holidays[d],
                Coaching.busyOn(book.busy, d.atTime(12, 0).atZone(zone()).toInstant().toEpochMilli())?.let(::busyOut)) }.toList()
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
