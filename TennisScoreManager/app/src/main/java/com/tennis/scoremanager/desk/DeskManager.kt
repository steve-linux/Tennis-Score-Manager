// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.desk

import android.content.Context
import android.util.Log
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.tennis.scoremanager.MatchController
import com.tennis.scoremanager.data.ClubBook
import com.tennis.scoremanager.ClubFeature
import com.tennis.scoremanager.data.BookingRules
import com.tennis.scoremanager.data.CTEntry
import com.tennis.scoremanager.data.ClubServerConf
import com.tennis.scoremanager.data.ClubServers
import com.tennis.scoremanager.data.OfficeBackupFile
import com.tennis.scoremanager.data.OfficeSnapshot
import com.tennis.scoremanager.data.SrvAdmin
import com.tennis.scoremanager.data.SrvBackup
import com.tennis.scoremanager.data.CourtBlock
import com.tennis.scoremanager.data.CourtInfo
import com.tennis.scoremanager.data.CourtSettings
import com.tennis.scoremanager.data.Course
import com.tennis.scoremanager.data.Ladder
import com.tennis.scoremanager.data.Fixture
import com.tennis.scoremanager.data.Team
import com.tennis.scoremanager.data.ClubTournament
import com.tennis.scoremanager.data.CourtBooking
import com.tennis.scoremanager.data.CourtPlan
import com.tennis.scoremanager.data.PayType
import com.tennis.scoremanager.data.PlayerProfile
import com.tennis.scoremanager.data.SrvInvite
import com.tennis.scoremanager.service.DeskService
import com.tennis.scoremanager.tv.TvServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap

/** I dati veri dell'app per la Segreteria da PC: ogni richiesta gira sul filo principale, come i tocchi sul telefono. */
class DeskBridge(private val c: MatchController) : DeskData {
    override fun <T> tx(f: () -> T): T = runBlocking(Dispatchers.Main.immediate) { f() }
    override val club: String get() = c.storage.clubName
    override val lang: String get() = c.options.value.lang.code
    override fun now(): Long = System.currentTimeMillis()
    override fun players(): List<PlayerProfile> = c.players.value
    override fun savePlayers(list: List<PlayerProfile>) = c.savePlayers(list)
    override fun book(): ClubBook = c.clubBook.value
    override fun saveBook(b: ClubBook) = c.saveClubBook(b)
    override fun payTypes(): List<PayType> = c.planning.payTypes()
    override fun courts(): List<String> = c.planning.clubCourts.value
    override fun plan(): CourtPlan = c.courtPlan.value
    override fun tournaments(): List<ClubTournament> = c.competitions.clubTournaments.value
    override fun saveBooking(b: CourtBooking, weeks: Int, charge: Boolean): List<Long> = c.planning.saveBooking(b, weeks, charge)
    override fun deleteBooking(b: CourtBooking, series: Boolean) = c.planning.deleteBooking(b, wholeSeries = series)
    override fun saveBlock(b: CourtBlock, delete: Boolean) = c.planning.saveCourtBlock(b, delete)
    override fun features(): Set<ClubFeature> = c.clubFeatures.value
    override fun courses(): List<Course> = c.school.courses.value
    override fun teams(): List<Team> = c.competitions.teams.value
    override fun ladders(): List<Ladder> = c.competitions.ladders.value
    override fun saveCourse(c: Course) = this.c.school.saveCourse(c)
    override fun deleteCourse(c: Course) = this.c.school.deleteCourse(c.id)
    override fun courseFeeLabel(c: Course, month: Pair<Int, Int>?): String = this.c.school.courseFeeLabel(c, month)
    override fun chargeCourse(c: Course, month: Pair<Int, Int>?): Int = this.c.school.chargeCourse(c, month)
    override fun dues(charges: Set<String>): List<DeskDue> = c.notices.duesRecipients(charges).map { DeskDue(it.id, it.name, it.phone, it.email, it.text) }
    override fun sendDues(charges: Set<String>) = c.notices.noticeDues(charges)
    override fun saveTeam(t: Team) = c.competitions.saveTeam(t)
    override fun deleteTeam(t: Team) = c.competitions.deleteTeam(t.id)
    override fun teamNotice(t: Team, f: Fixture, kind: String) = when (kind) {
        "AVAILABILITY" -> c.competitions.noticeAvailability(t, f, missingOnly = false)
        "MISSING" -> c.competitions.noticeAvailability(t, f, missingOnly = true)
        "CALLUP" -> c.competitions.noticeCallUp(t, f)
        else -> c.competitions.noticeResult(t, f)
    }
    override fun convokeMatch(t: ClubTournament, matchId: String) { t.matches.firstOrNull { it.id == matchId }?.let { c.notices.noticeMatch(t, it) } }
    override fun convokeRubber(t: Team, f: Fixture, index: Int) = c.notices.noticeRubber(t, f, index)
    override fun sendCourseToCoach(c: Course) = this.c.coaching.sendCourseToCoach(c)
    override fun noticeCourseMoved(c: Course, key: String) = this.c.school.noticeCourseMoved(c, key)
    override fun registerPdf(c: Course): ByteArray? = this.c.coaching.registerFile(c.id, coachSide = false)?.let { runCatching { it.readBytes() }.getOrNull() }
    override fun registerXlsx(c: Course): ByteArray? = this.c.coaching.registerXlsx(c.id, coachSide = false)?.let { runCatching { it.readBytes() }.getOrNull() }

    /** Un file ricevuto letto come lo leggerebbe il telefono: risultato dal campo, presenze dell'allenatore o allievo proposto. */
    override fun profileFields(id: String) = fieldsOf(id)
    override fun profileFile(id: String, fields: Set<com.tennis.scoremanager.data.ProfileField>?): String? = c.playerById(id)?.let { c.profiles.profileText(it, fields) }
    override fun applyProfile(text: String, asMember: Boolean): String? = com.tennis.scoremanager.data.ProfileCards.decode(text)?.let { c.profiles.applyProfileFromDesk(it, asMember, false) }

    private fun fieldsOf(id: String): DeskProfileFields? = c.playerById(id)?.let { p ->
        val photo = c.profiles.photoBytes(p.name) != null
        DeskProfileFields(com.tennis.scoremanager.data.ProfileCards.available(p, photo), com.tennis.scoremanager.data.ProfileCards.defaults(p, photo).toList())
    }

    override fun incomingFile(text: String): DeskIncoming? {
        val s = c.strings
        com.tennis.scoremanager.data.ProfileCards.decode(text)?.let { return c.profiles.profilePreview(it) }
        com.tennis.scoremanager.data.MatchCards.decodeResult(text)?.let { r ->
            val st = c.cards.resultStatus(r)
            val winner = when (r.winner) { 1 -> r.side1; 2 -> r.side2; else -> null }
            val msg = when (st) {
                com.tennis.scoremanager.data.ResultStatus.READY -> s.mrReady
                com.tennis.scoremanager.data.ResultStatus.SAME -> s.mrSame
                com.tennis.scoremanager.data.ResultStatus.CONFLICT -> s.mrConflict(c.cards.resultNow(r))
                com.tennis.scoremanager.data.ResultStatus.BAD_CODE -> s.mrBadCode
                com.tennis.scoremanager.data.ResultStatus.NOT_FOUND -> s.mrNotFound
            }
            return DeskIncoming("RESULT", s.mrTitle, listOfNotNull(listOf(r.event, r.round).filter { it.isNotBlank() }.joinToString(" · "), "${r.side1} – ${r.side2}",
                com.tennis.scoremanager.data.MatchCards.resultText(r, s.ctWalkover, s.ctRetired, s.ctDisqualified), winner?.let { s.mrWinner(it) }), msg,
                st == com.tennis.scoremanager.data.ResultStatus.READY || st == com.tennis.scoremanager.data.ResultStatus.CONFLICT, replace = st == com.tennis.scoremanager.data.ResultStatus.CONFLICT)
        }
        com.tennis.scoremanager.data.CourseCards.decodeReport(text)?.let { r ->
            val st = c.coaching.attendanceStatus(r)
            val msg = when (st) {
                com.tennis.scoremanager.data.AttendanceStatus.READY -> s.ccAttReady
                com.tennis.scoremanager.data.AttendanceStatus.SAME -> s.ccAttSame
                com.tennis.scoremanager.data.AttendanceStatus.BAD_CODE -> s.ccAttBadCode
                com.tennis.scoremanager.data.AttendanceStatus.NOT_FOUND -> s.ccAttNotFound
            }
            return DeskIncoming("ATTENDANCE", s.ccAttendanceTitle, listOfNotNull(r.courseName, r.coach.ifBlank { null }?.let { s.ccFrom(it) }, s.ccLessonsMarked(r.attendance.size)),
                msg, st == com.tennis.scoremanager.data.AttendanceStatus.READY)
        }
        com.tennis.scoremanager.data.CourseCards.decodeProposal(text)?.let { p ->
            val x = p.student
            val guardian = listOf(x.guardian, x.guardianPhone).filter { it.isNotBlank() }.joinToString(" ")
            return DeskIncoming("PROPOSAL", s.ccProposalTitle, listOfNotNull(x.name, x.phone.ifBlank { null }, x.email.ifBlank { null }, guardian.ifBlank { null }?.let { "${s.coGuardian}: $it" },
                listOfNotNull(p.coach.ifBlank { null }?.let { s.ccFrom(it) }, p.level.ifBlank { null }, p.note.ifBlank { null }).joinToString(" · ").ifBlank { null }),
                s.ccProposalCourse, true, courses = c.school.courses.value.sortedBy { it.name.lowercase() }.map { DeskIdName(it.id, it.name) }, course = p.course?.takeIf { id -> c.school.courses.value.any { it.id == id } })
        }
        return null
    }

    override fun applyIncoming(text: String, course: String?): String? {
        com.tennis.scoremanager.data.MatchCards.decodeResult(text)?.let { r ->
            val st = c.cards.resultStatus(r)
            if (st != com.tennis.scoremanager.data.ResultStatus.READY && st != com.tennis.scoremanager.data.ResultStatus.CONFLICT) return st.name
            c.cards.applyResult(r); return null
        }
        com.tennis.scoremanager.data.CourseCards.decodeReport(text)?.let { r ->
            val st = c.coaching.attendanceStatus(r)
            if (st != com.tennis.scoremanager.data.AttendanceStatus.READY) return st.name
            c.coaching.applyAttendance(r); return null
        }
        com.tennis.scoremanager.data.CourseCards.decodeProposal(text)?.let { p -> c.profiles.applyProposal(p, course?.takeIf { id -> c.school.courses.value.any { it.id == id } }); return null }
        return "NOT_A_FILE"
    }
    override fun setClubName(name: String) { c.storage.clubName = name }
    override fun saveCourtSettings(st: CourtSettings) = c.planning.saveCourtSettings(st)
    override fun addCourt(info: CourtInfo): Boolean = c.planning.addCourt(info)
    override fun renameCourt(old: String, new: String): Boolean = c.planning.renameCourt(old, new)
    override fun saveCourtInfo(info: CourtInfo) = c.planning.saveCourtInfo(info)
    override fun deleteCourt(name: String) = c.planning.deleteCourt(name)
    override fun moveCourt(name: String, up: Boolean) = c.planning.moveCourt(name, up)
    override fun savePayTypes(list: List<PayType>) = c.planning.savePayTypes(list)
    override fun serverConf(): ClubServerConf = c.clubServer.value
    override fun saveServerRules(rules: BookingRules, payInfo: String, alerts: Boolean) {
        c.serverArea.saveServerRules(rules, payInfo)
        c.serverArea.setServerAlerts(alerts)
    }
    // Rete: gira sul filo della richiesta, mai su quello principale.
    override fun serverState(): SrvAdmin = ClubServers.withServer(c.clubServer.value) { it.state() }
    override fun serverSync(): String? = runBlocking(Dispatchers.Main.immediate) { c.serverArea.syncServerNow() }
    override fun readOnlineNotice(id: String?) = c.serverArea.readOnlineNotice(id)
    override fun serverInvite(player: PlayerProfile): SrvInvite = ClubServers.withServer(c.clubServer.value) { it.invite(player.id, player.name) }
    override fun serverRevoke(playerId: String): String? = runBlocking(Dispatchers.Main.immediate) { c.serverArea.serverRemovePlayer(playerId) }
    override fun serverResetPin(playerId: String): String? = runBlocking(Dispatchers.Main.immediate) { c.serverArea.serverResetPin(playerId) }
    override fun serverRemove(kind: String, id: String) {
        ClubServers.withServer(c.clubServer.value) { if (kind == "wait") it.removeWaitAdmin(id) else it.removeSeekAdmin(id) }
    }
    override fun officeBackup(): Pair<String, ByteArray> = c.office.officeBackupName() to c.office.officeBackupBytes()
    override fun officeAuto(): Boolean = c.storage.officeBackupAuto
    override fun setOfficeAuto(on: Boolean) = c.office.setOfficeBackupAuto(on)
    override fun officeLast(): Pair<Long, String> = c.storage.officeBackupAt to c.storage.officeBackupServerError
    override fun localBackups(): List<OfficeBackupFile> = c.office.officeBackups()
    override fun localBackup(name: String): ByteArray? = c.office.officeBackupFile(name)?.readBytes()
    override fun officeBackupNow(): String? = runBlocking(Dispatchers.Main.immediate) { c.office.officeBackupNow(onlyIfChanged = false) }
    override fun serverChatRooms() = ClubServers.withServer(c.clubServer.value) { it.adminChats() }
    override fun serverChat(room: String) = ClubServers.withServer(c.clubServer.value) { it.adminChat(room) }
    override fun serverBoardPost(text: String) { ClubServers.withServer(c.clubServer.value) { it.postBoard(text) } }
    override fun serverChatDelete(id: String) { ClubServers.withServer(c.clubServer.value) { it.deleteChat(id) } }
    override fun serverBackups(): List<SrvBackup> = ClubServers.withServer(c.clubServer.value) { it.backups() }
    override fun serverBackup(name: String): ByteArray = ClubServers.withServer(c.clubServer.value) { it.backup(name) }
    override fun officeRestore(s: OfficeSnapshot) = runBlocking(Dispatchers.Main.immediate) { c.office.restoreOffice(s) }
    override fun receiptPdf(chargeId: String, paymentId: String): ByteArray? = c.notices.receiptFile(chargeId, paymentId)?.let { f -> f.readBytes().also { f.delete() } }
    override fun paymentsCsv(year: Int): ByteArray? = c.notices.paymentsCsv(year)?.readBytes()
    override fun saveTournament(t: ClubTournament) = c.competitions.saveClubTournament(t)
    override fun deleteTournament(t: ClubTournament) = c.competitions.deleteClubTournament(t.id)
    override fun addEntry(t: ClubTournament, people: List<PlayerProfile>) = c.competitions.addClubEntry(t, people)
    override fun removeEntry(t: ClubTournament, entry: CTEntry) = c.competitions.removeClubEntry(t, entry.id)
}

/** I dati veri dell'app per l'Allenatore da PC: allievi, agenda, corsi, PDF ed Excel fatti dal telefono. */
class CoachBridge(private val c: MatchController) : CoachData {
    override fun now(): Long = System.currentTimeMillis()
    override val lang: String get() = c.options.value.lang.code
    override fun coachName(): String = c.me()?.name ?: c.storage.myName
    override fun book() = c.coaching.coachBook.value
    override fun players(): List<PlayerProfile> = c.players.value
    override fun sessions() = c.serveSessions.value
    override fun addDiary(student: String, text: String) = c.coaching.addDiary(student, text)
    override fun setNote(student: String, text: String) { c.playerById(student)?.let { c.savePlayer(it.copy(note = text.take(4000))) } }
    override fun markPackPaid(packId: String) { c.coaching.coachBook.value.packs.firstOrNull { it.id == packId }?.let { c.coaching.markPackPaid(it) } }
    override fun unmarkPackPaid(packId: String) { c.coaching.coachBook.value.packs.firstOrNull { it.id == packId }?.let { c.coaching.unmarkPackPaid(it) } }
    override fun deletePack(packId: String) = c.coaching.deletePack(packId)
    override fun setLessonDone(lessonId: String, done: Boolean) { c.coaching.coachBook.value.lessons.firstOrNull { it.id == lessonId }?.let { c.coaching.saveLesson(it.copy(done = done, cancelled = if (done) false else it.cancelled)) } }
    override fun markCourseLesson(courseId: String, key: String, start: Long, present: Set<String>, cancelled: Boolean, note: String) =
        c.coaching.markCourseLesson(courseId, key, start, present, cancelled, note)
    override fun saveOwnCourse(c: com.tennis.scoremanager.data.Course) = this.c.coaching.saveCoachOwnCourse(c)
    override fun deleteOwnCourse(id: String) = c.coaching.deleteCoachCourse(id)
    override fun setCourseColor(id: String, color: String) = c.coaching.setCoachCourseColor(id, color)
    override fun saveBusy(b: com.tennis.scoremanager.data.CoachBusy) = c.coaching.saveCoachBusy(b)
    override fun deleteBusy(id: String) = c.coaching.deleteCoachBusy(id)
    override fun moveLesson(courseId: String, key: String, start: Long, minutes: Int?, court: String?) { c.moveCoachLesson(courseId, key, start, minutes, court) }
    override fun saveStudent(p: PlayerProfile) = c.savePlayer(p)
    override fun profileFields(id: String) = fieldsOf(id)
    override fun profileFile(id: String, fields: Set<com.tennis.scoremanager.data.ProfileField>?): String? = c.playerById(id)?.let { c.profiles.profileText(it, fields) }
    override fun profilePreview(text: String): DeskIncoming? = com.tennis.scoremanager.data.ProfileCards.decode(text)?.let { c.profiles.profilePreview(it) }
    override fun applyProfile(text: String): String? = com.tennis.scoremanager.data.ProfileCards.decode(text)?.let { c.profiles.applyProfileFromDesk(it, false, true) }

    private fun fieldsOf(id: String): DeskProfileFields? = c.playerById(id)?.let { p ->
        val photo = c.profiles.photoBytes(p.name) != null
        DeskProfileFields(com.tennis.scoremanager.data.ProfileCards.available(p, photo), com.tennis.scoremanager.data.ProfileCards.defaults(p, photo).toList())
    }
    override fun country(): String = com.tennis.scoremanager.data.Holidays.country(c.options.value.lang.code)
    private fun bytesOf(f: java.io.File?): ByteArray? = f?.let { runCatching { it.readBytes() }.getOrNull() }
    override fun receiptPdf(packId: String): ByteArray? = bytesOf(c.coaching.packReceiptFile(packId))
    override fun reportPdf(student: String, days: Int?): ByteArray? = c.playerById(student)?.let { bytesOf(c.studentReportFile(it, days, pdf = true)) }
    override fun registerPdf(courseId: String): ByteArray? = bytesOf(c.coaching.registerFile(courseId, coachSide = true))
    override fun registerXlsx(courseId: String): ByteArray? = bytesOf(c.coaching.registerXlsx(courseId, coachSide = true))
    override fun receiptsXlsx(year: Int): ByteArray? = bytesOf(c.coaching.receiptsXlsx(year))
}

/**
 * La Segreteria da PC sul telefono: acceso solo quando serve. Si spegne da solo dopo mezz'ora senza nessuno collegato.
 * Il certificato sta nella cartella esclusa dai backup: la chiave privata non esce mai dal telefono.
 */
class DeskManager(context: Context, private val data: DeskData, private val coachData: CoachData? = null) {

    data class Ui(
        val running: Boolean = false, val urls: List<String> = emptyList(), val code: String = "", val waitingIp: String? = null,
        val connectedIp: String? = null, val fingerprint: String = "", val failed: Boolean = false,
        /** Acceso come Allenatore da PC (solo i dati dell'allenatore) o come Segreteria da PC. */
        val coach: Boolean = false,
    )

    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _ui = MutableStateFlow(Ui())
    val ui: StateFlow<Ui> = _ui
    private val auth = DeskAuth()
    private var server: DeskServer? = null
    private var ticker: Job? = null
    private val files = ConcurrentHashMap<String, ByteArray>()

    private fun asset(name: String): ByteArray? = files[name] ?: runCatching { app.assets.open("desk/$name").use { it.readBytes() } }.getOrNull()?.also { files[name] = it }

    /** Accende il server; [coach] = Allenatore da PC. Acceso nell'altro modo, si spegne e riparte in questo. */
    fun start(coach: Boolean = false) {
        if (server != null && _ui.value.coach != coach) stop()
        scope.launch {
            synchronized(this@DeskManager) {
                if (server != null) return@launch
                try {
                    val cert = DeskCert.loadOrCreate(app.noBackupFilesDir)
                    val api = DeskApi(data, auth, qr = ::qrRows, coach = if (coach) coachData?.let { CoachApi(it) } else null)
                    val s = DeskServer(cert, api, ::asset, { TvServer.isLocalPeer(it) })
                    s.start()
                    server = s
                    _ui.value = Ui(running = true, fingerprint = cert.fingerprint, coach = coach)
                } catch (e: Exception) {
                    Log.w(TAG, "Avvio non riuscito", e)
                    _ui.value = Ui(failed = true)
                    return@launch
                }
            }
            DeskService.start(app)
            tick()
        }
    }

    /** Ogni secondo: codice, PC in attesa e indirizzi aggiornati; dopo mezz'ora vuota si spegne. */
    private fun tick() {
        ticker?.cancel()
        ticker = scope.launch {
            var active = System.currentTimeMillis()
            while (isActive) {
                val port = server?.port ?: break
                val connected = auth.connected
                if (connected != null || auth.pending != null) active = System.currentTimeMillis()
                _ui.value = _ui.value.copy(
                    urls = TvServer.localAddresses().map { "https://$it:$port" }, code = auth.shownCode, waitingIp = auth.pending?.ip, connectedIp = connected,
                )
                if (System.currentTimeMillis() - active > DeskAuth.IDLE_MS) { stop(); break }
                delay(1_000)
            }
        }
    }

    private fun qrRows(text: String): List<String> {
        val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 0))
        return (0 until m.height).map { y -> buildString { for (x in 0 until m.width) append(if (m[x, y]) '1' else '0') } }
    }

    /** Il telefono ha inquadrato un QR: false se non è quello di un PC in attesa. */
    fun scan(text: String): Boolean = auth.scan(text)

    /** La risposta del telefono al PC che chiede di entrare. */
    fun answer(allow: Boolean) = auth.answer(allow)

    /** Chiude il PC collegato; il server resta acceso con un codice nuovo. */
    fun disconnect() = auth.logout()

    @Synchronized
    fun stop() {
        ticker?.cancel()
        ticker = null
        auth.logout()
        server?.stop()
        server = null
        files.clear()
        _ui.value = Ui()
        DeskService.stop(app)
    }

    companion object { private const val TAG = "DeskManager" }
}
