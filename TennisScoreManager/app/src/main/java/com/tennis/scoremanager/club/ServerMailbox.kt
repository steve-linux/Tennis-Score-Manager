// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later
package com.tennis.scoremanager.club

import android.app.Application
import com.tennis.scoremanager.data.Storage
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * La casella dei file sul server del circolo (facoltativo): schede partita, risultati, presenze, proposte e profili
 * viaggiano da soli tra segreteria, giocatori e allenatore. I file arrivati e non ancora aperti stanno in [inbox].
 * Quello che serve del resto dell'app è in [ClubHost].
 */
class ServerMailbox(
    host: ClubHost,
    private val app: Application,
    private val storage: Storage,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
) : ClubHost by host {
    /** I file arrivati dal server e non ancora aperti (Dal circolo, nella prima schermata). */
    val inbox = MutableStateFlow(storage.loadInbox())

    internal fun putInbox(list: List<com.tennis.scoremanager.data.InboxItem>) {
        inbox.value = list
        scope.launch(io) { storage.saveInbox(list) }
    }

    /** Apre il primo file della casella come se arrivasse da WhatsApp (ma ricordando che viene dal server). */
    fun openInbox() {
        val first = inbox.value.firstOrNull() ?: return
        putInbox(inbox.value.drop(1))
        receiveCardText(first.text, first.via.ifBlank { null })
    }

    /** Segreteria: lascia un file nella casella del giocatore sul server (se c'è il server e lui è iscritto). */
    override fun serverMail(to: String, kind: String, file: File) {
        val conf = clubServer.value
        if (!conf.paired) return
        val text = runCatching { file.readText() }.getOrNull() ?: return
        scope.launch(io) { runCatching { com.tennis.scoremanager.data.ClubServers.withServer(conf) { it.sendMail(to, kind, text) } } }
    }

    /** Giocatore o allenatore: lascia un file alla segreteria sul server [url]; true se è arrivato. */
    override suspend fun postToOffice(url: String, kind: String, text: String): Boolean {
        val a = book.bookingAccounts.value.firstOrNull { it.url == url && it.joined } ?: return false
        return book.bookingWith(a) { it.postMail(kind, text) }.isSuccess
    }

    /** Il server del circolo da cui sono arrivati i corsi dell'allenatore (per mandare presenze e proposte da lì). */
    fun coachVia(): String? = coaching.coachBook.value.courses.mapNotNull { it.via }.firstOrNull { url -> book.bookingAccounts.value.any { it.url == url && it.joined } }

    /** Allenatore: le presenze del corso alla segreteria dal server; true se arrivate. */
    suspend fun sendAttendanceServer(courseId: String): Boolean {
        val cc = coaching.coachCourse(courseId) ?: return false
        val url = cc.via ?: return false
        val r = com.tennis.scoremanager.data.CourseCards.report(cc, me()?.name ?: storage.myName, System.currentTimeMillis(), coaching.coachBook.value.busy)
        val ok = postToOffice(url, "attendance", com.tennis.scoremanager.data.CourseCards.encodeReport(r))
        if (ok) coaching.attendanceSent(courseId)
        return ok
    }

    /** Allenatore: l'allievo proposto alla segreteria dal server; true se arrivato. */
    suspend fun sendProposalServer(st: com.tennis.scoremanager.data.PlayerProfile, level: String, note: String): Boolean {
        val url = coachVia() ?: return false
        val p = com.tennis.scoremanager.data.StudentProposal(student = com.tennis.scoremanager.data.CourseCards.student(st, st.id), coach = me()?.name ?: storage.myName,
            level = level.trim(), note = note.trim(), sentAt = System.currentTimeMillis())
        return postToOffice(url, "proposal", com.tennis.scoremanager.data.CourseCards.encodeProposal(p))
    }

    /** Giocatore/allenatore: prende i file della sua casella su quel server, li tiene da aprire e li toglie dal server. */
    internal suspend fun fetchMyMail(a: com.tennis.scoremanager.data.BookingAccount) {
        val mail = book.bookingWith(a) { it.myMail() }.getOrNull().orEmpty()
        if (mail.isEmpty()) return
        val have = inbox.value.map { it.id }.toSet()
        val fresh = mail.filter { it.id !in have }.map { com.tennis.scoremanager.data.InboxItem(it.id, it.kind, it.text, it.fromName, a.url, it.at) }
        putInbox(inbox.value + fresh)
        mail.forEach { m -> book.bookingWith(a) { it.dropMyMail(m.id) } }
        if (fresh.isNotEmpty()) com.tennis.scoremanager.service.RemindersJob.notifyInbox(app, strings.ibNew, strings.ibTitle(inbox.value.size))
    }

    /**
     * Segreteria: prende i file della sua casella. Risultati e presenze col codice giusto si registrano subito (come
     * con Registra); gli altri (diversi da quello che c'è, allievi proposti) restano da aprire.
     */
    override suspend fun fetchOfficeMail(conf: com.tennis.scoremanager.data.ClubServerConf) {
        val mail = runCatching { withContext(io) { com.tennis.scoremanager.data.ClubServers.withServer(conf) { it.adminMail() } } }.getOrNull().orEmpty()
        if (mail.isEmpty()) return
        val keep = mutableListOf<com.tennis.scoremanager.data.InboxItem>()
        val done = mutableListOf<String>()
        for (m in mail) {
            val r = com.tennis.scoremanager.data.MatchCards.decodeResult(m.text)
            val a = com.tennis.scoremanager.data.CourseCards.decodeReport(m.text)
            when {
                r != null && cards.resultStatus(r) == com.tennis.scoremanager.data.ResultStatus.READY -> { cards.applyResult(r); done += "${r.side1} – ${r.side2} ${r.score}" }
                r != null && cards.resultStatus(r) == com.tennis.scoremanager.data.ResultStatus.SAME -> {}
                a != null && coaching.attendanceStatus(a) == com.tennis.scoremanager.data.AttendanceStatus.READY -> { coaching.applyAttendance(a); done += a.courseName }
                a != null && coaching.attendanceStatus(a) == com.tennis.scoremanager.data.AttendanceStatus.SAME -> {}
                else -> keep += com.tennis.scoremanager.data.InboxItem(m.id, m.kind, m.text, m.fromName, "", m.at)
            }
        }
        val have = inbox.value.map { it.id }.toSet()
        putInbox(inbox.value + keep.filter { it.id !in have })
        mail.forEach { m -> runCatching { withContext(io) { com.tennis.scoremanager.data.ClubServers.withServer(conf) { it.dropAdminMail(m.id) } } } }
        if (done.isNotEmpty()) com.tennis.scoremanager.service.RemindersJob.notifyInbox(app, strings.ibApplied, done.joinToString(" · "))
        if (keep.isNotEmpty()) com.tennis.scoremanager.service.RemindersJob.notifyInbox(app, strings.ibNew, strings.ibTitle(inbox.value.size))
    }
}
