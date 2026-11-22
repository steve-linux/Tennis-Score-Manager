// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later
package com.tennis.scoremanager.coach

import android.app.Application
import com.tennis.scoremanager.Screen
import com.tennis.scoremanager.data.Reports
import com.tennis.scoremanager.data.Storage
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Il lavoro dell'allenatore: diario, obiettivi, agenda e pacchetti di lezioni, gruppi, avvisi agli allievi, i corsi
 * ricevuti dalla segreteria (scheda corso) con presenze e proposte. Quello che serve del resto dell'app è in [CoachHost].
 */
class CoachArea(
    host: CoachHost,
    private val app: Application,
    private val storage: Storage,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
) : CoachHost by host {
    val coachBook = MutableStateFlow(storage.loadCoachBook())

    internal fun putCoach(b: com.tennis.scoremanager.data.CoachBook) {
        coachBook.value = b
        scope.launch(io) { storage.saveCoachBook(b) }
    }

    fun addDiary(student: String, text: String, at: Long = System.currentTimeMillis()) {
        val t = text.trim().take(2000)
        if (t.isEmpty()) return
        val b = coachBook.value
        putCoach(b.copy(diary = b.diary + com.tennis.scoremanager.data.DiaryEntry(com.tennis.scoremanager.data.Coaching.newId("d"), student, at, t)))
    }

    fun deleteDiary(id: String) = coachBook.value.let { b -> putCoach(b.copy(diary = b.diary.filter { it.id != id })) }

    /** Un obiettivo nuovo; se è già raggiunto lo si sa subito. */
    fun addStudentGoal(student: String, stroke: com.tennis.scoremanager.data.TrainedStroke, targetPct: Int, window: Int) {
        val now = System.currentTimeMillis()
        val g = com.tennis.scoremanager.data.StudentGoal(com.tennis.scoremanager.data.Coaching.newId("g"), student, stroke, targetPct, window, createdAt = now)
        val reached = com.tennis.scoremanager.data.Coaching.goalStatus(g, serveSessions.value).reached(g)
        coachBook.value.let { b -> putCoach(b.copy(goals = b.goals + g.copy(reachedAt = if (reached) now else null))) }
    }

    fun deleteStudentGoal(id: String) = coachBook.value.let { b -> putCoach(b.copy(goals = b.goals.filter { it.id != id })) }

    /** Dopo un allenamento: gli obiettivi raggiunti adesso prendono la data. */
    internal fun checkStudentGoals() {
        val b = coachBook.value
        if (b.goals.none { it.reachedAt == null }) return
        val next = com.tennis.scoremanager.data.Coaching.markReached(b, serveSessions.value, System.currentTimeMillis())
        if (next != b) putCoach(next)
    }

    fun saveLesson(l: com.tennis.scoremanager.data.CoachLesson) = coachBook.value.let { b ->
        putCoach(b.copy(lessons = b.lessons.filter { it.id != l.id } + l))
    }

    fun deleteLesson(id: String) = coachBook.value.let { b -> putCoach(b.copy(lessons = b.lessons.filter { it.id != id })) }

    fun savePack(p: com.tennis.scoremanager.data.LessonPack) = coachBook.value.let { b ->
        putCoach(b.copy(packs = b.packs.filter { it.id != p.id } + p))
    }

    fun deletePack(id: String) = coachBook.value.let { b -> putCoach(b.copy(packs = b.packs.filter { it.id != id })) }

    fun saveGroup(g: com.tennis.scoremanager.data.StudentGroup) = coachBook.value.let { b ->
        putCoach(b.copy(groups = b.groups.filter { it.id != g.id } + g))
    }

    fun deleteGroup(id: String) = coachBook.value.let { b -> putCoach(b.copy(groups = b.groups.filter { it.id != id })) }

    fun openLessons() {
        refreshPlayers()
        nav.push(Screen.LESSONS)
    }

    /** "lunedì 16 novembre, 18:00 · Campo 2". */
    fun lessonWhen(l: com.tennis.scoremanager.data.CoachLesson): String {
        val lang = options.value.lang
        return listOfNotNull("${com.tennis.scoremanager.data.Notices.day(l.start, lang)}, ${Reports.time(l.start, lang)}", l.place.ifBlank { null }).joinToString(" · ")
    }

    /** Un messaggio libero agli allievi (a chi è minorenne arriva al genitore): WhatsApp, SMS o email dall'app degli avvisi. */
    /**
     * Il report degli ultimi 30 giorni a ciascuno (al genitore se è minorenne), uno per uno con il PDF allegato.
     * Si fanno i PDF di tutti prima di aprire gli avvisi.
     */
    fun noticeReports(ids: List<String>) {
        val s = strings
        scope.launch {
            val now = System.currentTimeMillis()
            val rec = withContext(io) {
                ids.mapNotNull { playerById(it) }.map { p ->
                    val f = studentReportFile(p, 30, pdf = true)
                    val text = listOf(com.tennis.scoremanager.data.Notices.hello(s, p, now), s.rpText(p.name), clubSign).filter { it.isNotBlank() }.joinToString("\n")
                    com.tennis.scoremanager.data.Notices.recipient(p, text, now).copy(attachment = f)
                }
            }
            openNotice(com.tennis.scoremanager.data.NoticePlan(s.rpAll, s.studentsTitle, s.rpText(""), rec))
        }
    }

    fun noticeStudents(ids: List<String>) {
        val now = System.currentTimeMillis()
        val rec = ids.mapNotNull { playerById(it) }.map { com.tennis.scoremanager.data.Notices.recipient(it, "", now) }
        openNotice(com.tennis.scoremanager.data.NoticePlan(strings.coWriteTitle, strings.studentsTitle, "", rec, editable = true))
    }

    /** Il promemoria delle lezioni [lessons]: a ogni allievo la sua (giorno, ora, dove). */
    fun noticeLessons(lessons: List<com.tennis.scoremanager.data.CoachLesson>) {
        val s = strings; val now = System.currentTimeMillis()
        val rec = lessons.sortedBy { it.start }.flatMap { l ->
            l.students.mapNotNull { playerById(it) }.map { p ->
                val text = listOfNotNull(com.tennis.scoremanager.data.Notices.hello(s, p, now), s.coRemind(lessonWhen(l)), l.note.ifBlank { null }).joinToString("\n")
                com.tennis.scoremanager.data.Notices.recipient(p, text, now)
            }
        }.distinctBy { it.id }
        openNotice(com.tennis.scoremanager.data.NoticePlan(s.coLessons, s.coLessons, lessons.firstOrNull()?.let { s.coRemind(lessonWhen(it)) }.orEmpty(), rec))
    }

    /** Partita tra due allievi dalla scheda: i due in campo, e si va in Nuova partita (Indietro torna alla scheda). */
    fun matchWith(a: String, b: String) {
        val pa = playerById(a) ?: return
        val pb = playerById(b) ?: return
        updateSetup { it.copy(doubles = false, p1a = pa.name, p1aId = pa.id, p2a = pb.name, p2aId = pb.id, p1b = "", p1bId = "", p2b = "", p2bId = "") }
        go(Screen.SETUP)
    }

    // ---- Allenatore: corsi della scuola (scheda corso), presenze, proposte, partite e progressi degli allievi ----

    val openCoachCourseId = MutableStateFlow<String?>(null)
    fun openCoachCourse(id: String) { openCoachCourseId.value = id; nav.push(Screen.COACH_COURSE) }
    fun coachCourse(id: String?): com.tennis.scoremanager.data.CoachCourse? = coachBook.value.courses.firstOrNull { it.card.course.id == id }

    /** Segreteria: la scheda del corso in un file (il codice nasce al primo invio e resta). */
    fun courseCardFile(courseId: String): File? {
        val co = school.course(courseId) ?: return null
        val code = co.cardCode ?: com.tennis.scoremanager.data.MatchCards.newCode().also { school.saveCourse(co.copy(cardCode = it)) }
        val card = com.tennis.scoremanager.data.CourseCards.card(school.course(courseId) ?: co, code, System.currentTimeMillis(), storage.clubName, storage.clubPhone) { playerById(it) }
        if (card.office.isBlank()) toast(strings.mcNoOffice)
        val dir = File(app.cacheDir, "share/cards").apply { mkdirs() }
        return runCatching { File(dir, com.tennis.scoremanager.data.CourseCards.fileName(strings.ccFilePrefix, co.name)).apply { writeText(com.tennis.scoremanager.data.CourseCards.encode(card)) } }.getOrNull()
    }

    /** Segreteria: manda la scheda del corso all'allenatore (WhatsApp col file). */
    fun sendCourseToCoach(co: com.tennis.scoremanager.data.Course) {
        val s = strings; val lang = options.value.lang; val now = System.currentTimeMillis()
        val file = courseCardFile(co.id)
        val coach = co.coach?.let { playerById(it) }
        val text = listOf(s.ccCardText(co.name, co.students.size), "${com.tennis.scoremanager.ui.screens.shortDate(co.from, lang)} – ${com.tennis.scoremanager.ui.screens.shortDate(co.to, lang)}",
            s.ccCardHowTo, clubSign).filter { it.isNotBlank() }.joinToString("\n")
        val rec = listOfNotNull(coach?.let { com.tennis.scoremanager.data.Notices.recipient(it, text, now).copy(attachment = file) })
        if (coach != null && file != null) serverMail(coach.id, "course", file)
        openNotice(com.tennis.scoremanager.data.NoticePlan(s.ccSendToCoach, co.name, text, rec))
    }

    /** Allenatore: è arrivata una scheda corso (finestra di conferma). */
    val incomingCourse = MutableStateFlow<com.tennis.scoremanager.data.CourseCard?>(null)
    /** Segreteria: sono arrivate le presenze di un corso. */
    val incomingAttendance = MutableStateFlow<com.tennis.scoremanager.data.AttendanceReport?>(null)
    /** Segreteria: l'allenatore propone un allievo nuovo. */
    val incomingProposal = MutableStateFlow<com.tennis.scoremanager.data.StudentProposal?>(null)
    /** Un profilo arrivato (file o QR): la persona da aggiungere o da completare tra i giocatori. */
    val incomingProfile = MutableStateFlow<com.tennis.scoremanager.data.ProfileCard?>(null)

    /**
     * Allenatore: il corso tra i suoi; gli iscritti diventano suoi allievi col loro id della segreteria (così le
     * presenze tornano alla persona giusta), con contatti, genitore e consenso dove qui mancano.
     */
    fun acceptCourse(card: com.tennis.scoremanager.data.CourseCard) {
        incomingCourse.value = null
        val now = System.currentTimeMillis()
        val changed = card.students.map { cs ->
            val old = playerById(cs.id)
            (old ?: com.tennis.scoremanager.data.PlayerProfile(cs.id, cs.name, cs.firstName, cs.lastName, cs.nickname, createdAt = now)).let { p ->
                p.copy(
                    student = true,
                    phone = p.phone.ifBlank { cs.phone }, email = p.email.ifBlank { cs.email },
                    guardian = p.guardian.ifBlank { cs.guardian }, guardianPhone = p.guardianPhone.ifBlank { cs.guardianPhone },
                    consentAt = p.consentAt ?: cs.consentAt,
                )
            }
        }
        if (changed.isNotEmpty()) savePlayers(changed)
        cs_ranking(card.students, now)
        val b = coachBook.value
        val via = incomingVia
        putCoach(b.copy(courses = com.tennis.scoremanager.data.CourseCards.receive(b.courses, card).map { if (it.card.course.id == card.course.id && via != null) it.copy(via = via) else it }))
        openCoachCourse(card.course.id)
    }

    /** La classifica arrivata con la scheda entra nella storia dell'allievo se è nuova. */
    internal fun cs_ranking(list: List<com.tennis.scoremanager.data.CourseStudent>, now: Long) {
        val b = coachBook.value
        val add = list.filter { it.ranking.isNotBlank() && com.tennis.scoremanager.data.Coaching.ranking(b, it.id) != it.ranking }
            .map { com.tennis.scoremanager.data.RankingEntry(com.tennis.scoremanager.data.Coaching.newId("r"), it.id, now, it.ranking) }
        if (add.isNotEmpty()) putCoach(b.copy(rankings = b.rankings + add))
    }

    /**
     * Allenatore: l'appello di una lezione del corso. [note] va anche nel diario di chi c'era (una volta: se la nota
     * cambia, si aggiunge la nuova).
     */
    fun markCourseLesson(courseId: String, key: String, start: Long, present: Set<String>, cancelled: Boolean, note: String) {
        val b = coachBook.value
        val cc = b.courses.firstOrNull { it.card.course.id == courseId } ?: return
        val n = note.trim()
        val updated = cc.copy(
            attendance = if (cancelled) cc.attendance - key else cc.attendance + (key to present),
            cancelled = if (cancelled) cc.cancelled + key else cc.cancelled - key,
            notes = if (n.isEmpty()) cc.notes - key else cc.notes + (key to n),
            changedAt = System.currentTimeMillis(),
        )
        val diary = if (!cancelled && n.isNotEmpty() && cc.notes[key] != n)
            present.map { com.tennis.scoremanager.data.DiaryEntry(com.tennis.scoremanager.data.Coaching.newId("d"), it, start, "${cc.card.course.name}: $n") } else emptyList()
        putCoach(b.copy(courses = b.courses.map { if (it.card.course.id == courseId) updated else it }, diary = b.diary + diary))
    }

    /**
     * Allenatore: salva un corso suo (nuovo o cambiato): nome, livello, date, orari, allievi. I corsi della segreteria
     * non si cambiano qui (li cambia lei e li rimanda).
     */
    fun saveCoachOwnCourse(co: com.tennis.scoremanager.data.Course, color: String? = null) {
        val b = coachBook.value
        val old = b.courses.firstOrNull { it.card.course.id == co.id }
        if (old != null && !old.personal) return
        val cc = old?.copy(card = old.card.copy(course = co, issuedAt = System.currentTimeMillis()))
            ?: com.tennis.scoremanager.data.CoachCourse(com.tennis.scoremanager.data.CourseCard(code = "", issuedAt = System.currentTimeMillis(), course = co), personal = true)
        val withColor = if (color != null && color in com.tennis.scoremanager.data.CourseCards.COLORS) cc.copy(color = color) else cc
        putCoach(b.copy(courses = b.courses.filter { it.card.course.id != co.id } + withColor))
    }

    /** L'etichetta colorata di un corso dell'allenatore (qualsiasi: anche quelli della segreteria, solo sul suo telefono). */
    fun setCoachCourseColor(id: String, color: String) {
        if (color !in com.tennis.scoremanager.data.CourseCards.COLORS) return
        val b = coachBook.value
        putCoach(b.copy(courses = b.courses.map { if (it.card.course.id == id) it.copy(color = color) else it }))
    }

    /** Toglie un corso dall'elenco dell'allenatore (i suoi; quelli della segreteria tornano se lei li rimanda). */
    fun deleteCoachCourse(id: String) {
        val b = coachBook.value
        putCoach(b.copy(courses = b.courses.filter { it.card.course.id != id }))
    }

    /** Allenatore: avvisa gli iscritti che la lezione è annullata (a chi è minorenne scrive al genitore). */
    fun noticeCourseCancelled(courseId: String, lessonStart: Long) {
        val cc = coachCourse(courseId) ?: return
        val s = strings; val lang = options.value.lang; val now = System.currentTimeMillis()
        val text = listOf(s.scCancelledNotice(cc.card.course.name), com.tennis.scoremanager.data.Notices.day(lessonStart, lang) + ", " + com.tennis.scoremanager.data.Reports.time(lessonStart, lang))
            .joinToString("\n")
        val rec = cc.card.course.students.mapNotNull { playerById(it) }.map { com.tennis.scoremanager.data.Notices.recipient(it, text, now) }
        openNotice(com.tennis.scoremanager.data.NoticePlan(s.scCancelledTitle, cc.card.course.name, text, rec))
    }

    /** Allenatore: il file delle presenze da mandare, il testo e il numero della segreteria (dalla scheda). */
    fun attendanceToSend(courseId: String): Triple<File, String, String>? {
        val cc = coachCourse(courseId) ?: return null
        val now = System.currentTimeMillis()
        val r = com.tennis.scoremanager.data.CourseCards.report(cc, me()?.name ?: storage.myName, now, coachBook.value.busy)
        val dir = File(app.cacheDir, "share/cards").apply { mkdirs() }
        val f = runCatching { File(dir, com.tennis.scoremanager.data.CourseCards.fileName(strings.ccAttendancePrefix, cc.card.course.name)).apply { writeText(com.tennis.scoremanager.data.CourseCards.encodeReport(r)) } }.getOrNull() ?: return null
        val co = cc.course
        val held = com.tennis.scoremanager.data.Courses.lessons(co).count { it.key in cc.attendance }
        val text = listOf(strings.ccAttendanceText(cc.card.course.name, held), cc.card.club).filter { it.isNotBlank() }.joinToString("\n")
        return Triple(f, text, cc.card.office)
    }

    fun attendanceSent(courseId: String) {
        val b = coachBook.value
        putCoach(b.copy(courses = b.courses.map { if (it.card.course.id == courseId) it.copy(sentAt = System.currentTimeMillis()) else it }))
    }

    fun attendanceStatus(r: com.tennis.scoremanager.data.AttendanceReport): com.tennis.scoremanager.data.AttendanceStatus = com.tennis.scoremanager.data.CourseCards.check(r, school.courses.value)

    /** Segreteria: le presenze dell'allenatore nel corso. */
    fun applyAttendance(r: com.tennis.scoremanager.data.AttendanceReport) {
        incomingAttendance.value = null
        val co = school.course(r.course) ?: return
        if (co.cardCode != r.code) return
        school.saveCourse(com.tennis.scoremanager.data.CourseCards.apply(co, r))
        toast(strings.ccAttendanceApplied)
    }

    /** Allenatore: propone alla segreteria un allievo nuovo (file + testo; WhatsApp alla segreteria se se ne sa il numero). */
    fun proposalToSend(st: com.tennis.scoremanager.data.PlayerProfile, level: String, note: String, courseId: String?): Triple<File, String, String>? {
        val p = com.tennis.scoremanager.data.StudentProposal(student = com.tennis.scoremanager.data.CourseCards.student(st, st.id), coach = me()?.name ?: storage.myName, course = courseId, level = level.trim(), note = note.trim(), sentAt = System.currentTimeMillis())
        val dir = File(app.cacheDir, "share/cards").apply { mkdirs() }
        val f = runCatching { File(dir, com.tennis.scoremanager.data.CourseCards.fileName(strings.ccProposalPrefix, st.name)).apply { writeText(com.tennis.scoremanager.data.CourseCards.encodeProposal(p)) } }.getOrNull() ?: return null
        val office = coachBook.value.courses.maxByOrNull { it.card.issuedAt }?.card?.office.orEmpty()
        val text = listOf(strings.ccProposalText(st.name), level.trim(), note.trim()).filter { it.isNotBlank() }.joinToString("\n")
        return Triple(f, text, office)
    }

    // Partite, tornei, classifica, valutazioni e test degli allievi.
    fun saveManualMatch(m: com.tennis.scoremanager.data.ManualMatch) { val b = coachBook.value; putCoach(b.copy(manualMatches = b.manualMatches.filter { it.id != m.id } + m)) }
    fun deleteManualMatch(id: String) { val b = coachBook.value; putCoach(b.copy(manualMatches = b.manualMatches.filter { it.id != id })) }
    /** Gli impegni dell'allenatore (giorni occupati). */
    fun saveCoachBusy(x: com.tennis.scoremanager.data.CoachBusy) {
        val b = coachBook.value
        val y = x.copy(from = com.tennis.scoremanager.data.CourtPlans.dayStart(minOf(x.from, x.to)), to = com.tennis.scoremanager.data.CourtPlans.dayStart(maxOf(x.from, x.to)), note = x.note.trim().take(200))
        putCoach(busyChanged(b.copy(busy = (b.busy.filter { it.id != y.id } + y).sortedBy { it.from })))
    }
    fun deleteCoachBusy(id: String) { val b = coachBook.value; putCoach(busyChanged(b.copy(busy = b.busy.filter { it.id != id }))) }

    /** Gli impegni sono cambiati: i corsi della segreteria hanno qualcosa da mandarle (vanno con le presenze). */
    internal fun busyChanged(b: com.tennis.scoremanager.data.CoachBook): com.tennis.scoremanager.data.CoachBook {
        val now = System.currentTimeMillis()
        return b.copy(courses = b.courses.map { if (!it.personal && it.card.course.to >= now) it.copy(changedAt = now) else it })
    }
    fun saveStudentEvent(e: com.tennis.scoremanager.data.StudentEvent) { val b = coachBook.value; putCoach(b.copy(events = b.events.filter { it.id != e.id } + e)) }
    fun deleteStudentEvent(id: String) { val b = coachBook.value; putCoach(b.copy(events = b.events.filter { it.id != id })) }
    fun addRanking(student: String, ranking: String) {
        val r = ranking.trim().uppercase().take(10)
        if (r.isEmpty()) return
        val b = coachBook.value
        putCoach(b.copy(rankings = b.rankings + com.tennis.scoremanager.data.RankingEntry(com.tennis.scoremanager.data.Coaching.newId("r"), student, System.currentTimeMillis(), r)))
    }
    fun saveEvaluation(e: com.tennis.scoremanager.data.Evaluation) { val b = coachBook.value; putCoach(b.copy(evaluations = b.evaluations.filter { it.id != e.id } + e)) }
    fun deleteEvaluation(id: String) { val b = coachBook.value; putCoach(b.copy(evaluations = b.evaluations.filter { it.id != id })) }
    fun saveFitnessTest(x: com.tennis.scoremanager.data.FitnessTest) { val b = coachBook.value; putCoach(b.copy(tests = b.tests.filter { it.id != x.id } + x)) }
    fun deleteFitnessTest(id: String) { val b = coachBook.value; putCoach(b.copy(tests = b.tests.filter { it.id != id })) }

    /** Il riepilogo del mese per la segreteria, in un testo (lezioni, ore, allievi, incassi, corsi, presenze). */
    fun monthText(m: com.tennis.scoremanager.data.Coaching.Month, label: String): String {
        val s = strings; val lang = options.value.lang
        return listOfNotNull(
            s.cmMonthTitle(label) + (me()?.name ?: storage.myName).ifBlank { null }?.let { " · $it" }.orEmpty(),
            s.cmPrivate(m.privateLessons, hoursText(m.privateMinutes), m.students),
            s.cmCourses(m.courseLessons, hoursText(m.courseMinutes)) + (m.presencePct?.let { " · " + s.cmPresence(it) } ?: ""),
            m.paidCents.takeIf { it > 0 }?.let { s.cmPaid(com.tennis.scoremanager.data.Fees.money(it, lang.locale)) },
        ).joinToString("\n")
    }

    internal fun hoursText(minutes: Int): String = if (minutes % 60 == 0) "${minutes / 60} h" else "${minutes / 60} h ${minutes % 60}′"

    /** Il numero della segreteria che l'allenatore conosce (dall'ultima scheda corso). */
    fun coachOffice(): String = coachBook.value.courses.maxByOrNull { it.card.issuedAt }?.card?.office.orEmpty()

    // ---- Allenatore: ricevute dei pacchetti e registro presenze ----

    /** Segna pagato il pacchetto e gli dà il numero di ricevuta (una serie per anno). */
    fun markPackPaid(p: com.tennis.scoremanager.data.LessonPack) {
        val now = System.currentTimeMillis()
        savePack(p.copy(paidAt = p.paidAt ?: now, receipt = p.receipt ?: com.tennis.scoremanager.data.Coaching.nextReceipt(coachBook.value, now)))
    }

    /** La ricevuta (non fiscale) di un pacchetto pagato, in PDF: in alto l'allenatore (e il circolo, se lo si sa). */
    fun packReceiptFile(packId: String): File? {
        val p0 = coachBook.value.packs.firstOrNull { it.id == packId && it.paidAt != null } ?: return null
        val p = if (p0.receipt == null) p0.copy(receipt = com.tennis.scoremanager.data.Coaching.nextReceipt(coachBook.value, p0.paidAt!!)).also { savePack(it) } else p0
        val s = strings
        val st = playerById(p.student)
        val coach = me()?.name ?: storage.myName
        val club = coachBook.value.courses.maxByOrNull { it.card.issuedAt }?.card?.club.orEmpty().ifBlank { storage.clubName }
        val header = listOf(coach, club).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { s.receiptTitle }
        // Il minorenne non paga da sé: la ricevuta è intestata al genitore, per l'allievo.
        val from = st?.let { x -> x.guardian.takeIf { it.isNotBlank() }?.let { g -> s.rcFor(g, x.name) } ?: x.name }.orEmpty()
        val dir = File(app.cacheDir, "share").apply { mkdirs() }
        val f = File(dir, "${s.receiptTitle} ${p.receipt!!.replace('/', '-')} ${st?.name.orEmpty()}.pdf".replace(Regex("[\\\\/:*?\"<>|]"), "_"))
        return runCatching {
            f.outputStream().use { com.tennis.scoremanager.data.ReceiptPdf.write(header, p.receipt, p.paidAt!!, from, s.rcPack(p.lessons), "", p.cents, s, options.value.lang, it) }
            f
        }.getOrNull()
    }

    /** Manda la ricevuta all'allievo (al genitore se è minorenne), con il PDF allegato. */
    fun noticePackReceipt(packId: String) {
        val p = coachBook.value.packs.firstOrNull { it.id == packId } ?: return
        val st = playerById(p.student) ?: return
        val f = packReceiptFile(packId) ?: return
        val now = System.currentTimeMillis()
        val number = coachBook.value.packs.firstOrNull { it.id == packId }?.receipt.orEmpty()
        val text = listOf(com.tennis.scoremanager.data.Notices.hello(strings, st, now), strings.rcText(number)).joinToString("\n")
        openNotice(com.tennis.scoremanager.data.NoticePlan(strings.receiptTitle, st.name, text, listOf(com.tennis.scoremanager.data.Notices.recipient(st, text, now).copy(attachment = f))))
    }

    /** Il registro presenze di un corso in PDF (per l'allenatore il suo corso, per la segreteria quello della scuola). */
    fun registerFile(courseId: String, coachSide: Boolean): File? {
        val s = strings
        val co = (if (coachSide) coachCourse(courseId)?.course else school.course(courseId)) ?: return null
        val cardNames = coachCourse(courseId)?.card?.students.orEmpty().associate { it.id to it.name }
        val names = { id: String -> playerById(id)?.name ?: cardNames[id] ?: "—" }
        val coach = (co.coach?.let { playerById(it)?.name } ?: co.coachName).ifBlank { if (coachSide) me()?.name ?: storage.myName else "" }
        val club = if (coachSide) coachCourse(courseId)?.card?.club.orEmpty() else storage.clubName
        val header = listOf(club, coach.takeIf { it.isNotBlank() }?.let { s.scCoachLine(it) }).filter { !it.isNullOrBlank() }.joinToString(" · ")
        val dir = File(app.cacheDir, "share").apply { mkdirs() }
        val f = File(dir, "${s.rgTitle} ${co.name}.pdf".replace(Regex("[\\\\/:*?\"<>|]"), "_"))
        return runCatching {
            f.outputStream().use { com.tennis.scoremanager.data.AttendancePdf.write(co, names, header, s, options.value.lang, System.currentTimeMillis(), it) }
            f
        }.getOrNull()
    }

    /** Il registro presenze del corso in Excel (.xlsx): una colonna per lezione, presenze e lezioni in fondo. */
    fun registerXlsx(courseId: String, coachSide: Boolean): File? {
        val s = strings
        val co = (if (coachSide) coachCourse(courseId)?.course else school.course(courseId)) ?: return null
        val cardNames = coachCourse(courseId)?.card?.students.orEmpty().associate { it.id to it.name }
        val rows = com.tennis.scoremanager.data.Xlsx.registerRows(co, { id -> playerById(id)?.name ?: cardNames[id] ?: "—" }, s.xlStudent, s.rgTotal, s.coLessons, System.currentTimeMillis())
        val dir = File(app.cacheDir, "share").apply { mkdirs() }
        val f = File(dir, "${s.rgTitle} ${co.name}.xlsx".replace(Regex("[\\\\/:*?\"<>|]"), "_"))
        return runCatching { f.outputStream().use { com.tennis.scoremanager.data.Xlsx.write(co.name, rows, it) }; f }.getOrNull()
    }

    /** Le ricevute dei pacchetti di un anno in Excel: numero, data, da chi, allievo, per cosa, importo. */
    fun receiptsXlsx(year: Int): File? {
        val s = strings
        val cal = java.util.Calendar.getInstance()
        val packs = coachBook.value.packs.filter { p -> p.receipt != null && p.paidAt != null && cal.apply { timeInMillis = p.paidAt }.get(java.util.Calendar.YEAR) == year }
            .sortedBy { it.receipt }
        val df = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.ROOT)
        val rows = listOf(listOf<Any?>(s.xlNumber, s.payDate, s.receiptReceivedFrom, s.xlStudent, s.receiptFor, s.receiptAmount)) + packs.map { p ->
            val st = playerById(p.student)
            listOf<Any?>(p.receipt, df.format(java.util.Date(p.paidAt!!)), st?.guardian?.ifBlank { null } ?: st?.name.orEmpty(), st?.name.orEmpty(), s.rcPack(p.lessons), p.cents / 100.0)
        }
        val dir = File(app.cacheDir, "share").apply { mkdirs() }
        val f = File(dir, "${s.xlReceipts} $year.xlsx")
        return runCatching { f.outputStream().use { com.tennis.scoremanager.data.Xlsx.write("${s.xlReceipts} $year", rows, it) }; f }.getOrNull()
    }

    // ---- Scheda progressi: l'allenatore la manda, l'allievo la tiene in Progressi ----

    val incomingProgress = MutableStateFlow<com.tennis.scoremanager.data.ProgressCard?>(null)
    val myProgress = MutableStateFlow(storage.loadMyProgress())

    /** Allenatore: la scheda progressi dell'allievo in un file da mandargli. */
    fun progressCardFile(studentId: String): File? {
        val st = playerById(studentId) ?: return null
        val club = coachBook.value.courses.maxByOrNull { it.card.issuedAt }?.card?.club.orEmpty().ifBlank { storage.clubName }
        val card = com.tennis.scoremanager.data.ProgressCards.build(coachBook.value, serveSessions.value, st.id, st.name, me()?.name ?: storage.myName, club, System.currentTimeMillis())
        val dir = File(app.cacheDir, "share/cards").apply { mkdirs() }
        return runCatching { File(dir, com.tennis.scoremanager.data.ProgressCards.fileName(strings.pcTitle, st.name)).apply { writeText(com.tennis.scoremanager.data.ProgressCards.encode(card)) } }.getOrNull()
    }

    /** Allenatore: manda la scheda progressi all'allievo (al genitore se è minorenne), col file allegato. */
    fun noticeProgressCard(studentId: String) {
        val st = playerById(studentId) ?: return
        val f = progressCardFile(studentId) ?: return
        val now = System.currentTimeMillis()
        val text = listOf(com.tennis.scoremanager.data.Notices.hello(strings, st, now), strings.pcText).joinToString("\n")
        openNotice(com.tennis.scoremanager.data.NoticePlan(strings.pcTitle, st.name, text, listOf(com.tennis.scoremanager.data.Notices.recipient(st, text, now).copy(attachment = f))))
    }

    /** Allievo: la scheda arrivata va nei suoi progressi (una più vecchia di quella che c'è non la sostituisce). */
    fun acceptProgress(card: com.tennis.scoremanager.data.ProgressCard) {
        incomingProgress.value = null
        val old = myProgress.value
        if (old != null && old.issuedAt > card.issuedAt) return
        myProgress.value = card
        scope.launch(io) { storage.saveMyProgress(card) }
    }
}
