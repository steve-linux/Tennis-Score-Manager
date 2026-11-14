// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tennis.scoremanager.MatchController
import com.tennis.scoremanager.data.AttendanceReport
import com.tennis.scoremanager.data.AttendanceStatus
import com.tennis.scoremanager.data.CoachCourse
import com.tennis.scoremanager.data.Coaching
import com.tennis.scoremanager.data.CourseCard
import com.tennis.scoremanager.data.CourtPlans
import com.tennis.scoremanager.data.Courses
import com.tennis.scoremanager.data.Fees
import com.tennis.scoremanager.data.Lesson
import com.tennis.scoremanager.data.Notices
import com.tennis.scoremanager.data.PlayerProfile
import com.tennis.scoremanager.data.Reports
import com.tennis.scoremanager.data.StudentProposal
import com.tennis.scoremanager.ui.screens.shortDate
import java.io.File
import kotlinx.coroutines.launch

/** Un file alla segreteria: WhatsApp dritto al suo numero se lo si sa (dalla scheda corso), sennò si sceglie a chi. */
fun sendToOffice(context: Context, file: File, text: String, office: String): Boolean {
    val wa = Notices.waNumber(office)
    return runCatching { context.startActivity(if (wa != null) whatsAppFile(context, wa, text, file) else shareFile(context, text, file)) }.isSuccess
}

private fun lessonText(l: Lesson, lang: com.tennis.scoremanager.model.Lang): String =
    "${Reports.date(l.start, lang)} · ${Reports.time(l.start, lang)}" + (l.court.ifBlank { null }?.let { " · $it" } ?: "")

/** Allievi › I miei corsi: i corsi ricevuti dalla segreteria, con la prossima lezione e le presenze da mandare. */
@Composable
fun CoachCoursesSection(c: MatchController) {
    val s = LocalStrings.current
    val book by c.coachBook.collectAsState()
    if (book.courses.isEmpty()) return
    val lang = c.options.collectAsState().value.lang
    val now = System.currentTimeMillis()
    com.tennis.scoremanager.ui.screens.SectionLabel(s.ccTitle)
    for (cc in book.courses.sortedBy { it.card.course.name.lowercase() }) {
        val co = cc.course
        val next = Courses.next(co, now)
        val toMark = Courses.held(co, now).count { it.key !in co.attendance && it.key !in co.cancelled }
        val hint = listOfNotNull(
            next?.let { lessonText(it, lang) },
            s.ccToMark(toMark).takeIf { toMark > 0 },
            s.ccToSend.takeIf { cc.toSend },
        ).joinToString(" · ")
        com.tennis.scoremanager.ui.screens.Entry(Icons.Filled.School, co.name, hint.ifBlank { null }) { c.openCoachCourse(co.id) }
    }
}

/**
 * Un corso dell'allenatore: gli iscritti (con le presenze) e le lezioni da segnare. "Manda le presenze alla segreteria"
 * spedisce il file con tutte le presenze segnate qui.
 */
@Composable
fun CoachCourseScreen(c: MatchController) {
    val s = LocalStrings.current
    val context = LocalContext.current
    BackHandler { c.back() }
    val book by c.coachBook.collectAsState()
    val id by c.openCoachCourseId.collectAsState()
    val cc = book.courses.firstOrNull { it.card.course.id == id } ?: return
    val co = cc.course
    val lang = c.options.collectAsState().value.lang
    val now = remember { System.currentTimeMillis() }
    var tab by rememberSaveable { mutableStateOf(1) }
    var open by remember { mutableStateOf<Lesson?>(null) }
    var showPast by remember { mutableStateOf(false) }
    var register by remember { mutableStateOf(false) }
    var calendar by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    ScreenScaffold(
        onHelp = { c.openHelp("7.6") },
        title = co.name,
        subtitle = listOfNotNull(co.level.ifBlank { null }, cc.card.club.ifBlank { null }, "${shortDate(co.from, lang)} – ${shortDate(co.to, lang)}").joinToString(" · "),
        icon = Icons.Filled.School,
        action = {
            Row {
                TextButton(onClick = { calendar = true }) { Text(s.calShort, color = TsmColors.Ball) }
                TextButton(onClick = { register = true }) { Text(s.rgShort, color = TsmColors.Ball) }
            }
        },
        bottomBar = {
            GhostButton(s.back, Icons.AutoMirrored.Filled.ArrowBack, { c.back() }, Modifier.weight(1f))
            BigButton(s.ccSendAttendance, Icons.AutoMirrored.Filled.Send, {
                val byApp = { c.attendanceToSend(co.id)?.let { (f, text, office) -> if (sendToOffice(context, f, text, office)) c.attendanceSent(co.id) } }
                // Corso arrivato dal server del circolo: le presenze tornano da lì; se non risponde, su WhatsApp.
                if (cc.via != null) scope.launch { if (c.sendAttendanceServer(co.id)) android.widget.Toast.makeText(context, s.ibSentServer, android.widget.Toast.LENGTH_SHORT).show() else byApp() }
                else byApp()
            }, Modifier.weight(1f))
        },
    ) {
        Text(cc.sentAt?.let { if (cc.toSend) s.ccToSend else s.ccSent(shortDate(it, lang)) } ?: if (cc.toSend) s.ccToSend else s.ccNothingYet,
            color = if (cc.toSend) TsmColors.Orange else TsmColors.TextDim, fontSize = 13.sp)
        Segmented(listOf(SegOption(s.scTabStudents), SegOption(s.scTabLessons)), tab, { tab = it })
        if (tab == 0) {
            for (sid in co.students) {
                val p = c.playerById(sid)
                val (pres, tot) = Courses.attendanceOf(co, sid, now)
                Row(Modifier.fillMaxWidth().clickable(enabled = p != null) { p?.let { c.openStudent(it.id) } }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(p?.name ?: cc.card.students.firstOrNull { it.id == sid }?.name ?: "—", color = TsmColors.TextMain, fontWeight = FontWeight.Bold)
                        if (tot > 0) Text(s.scAttendance(pres, tot), color = if (pres * 4 < tot * 3) TsmColors.Orange else TsmColors.TextDim, fontSize = 13.sp)
                    }
                }
            }
            if (co.students.isNotEmpty()) GhostButton(s.coWrite, Icons.Filled.Campaign, { c.noticeStudents(co.students) }, Modifier.fillMaxWidth())
        } else {
            val all = Courses.lessons(co)
            val past = all.filter { it.start <= now }.reversed()
            val next = all.filter { it.start > now }
            if (all.isEmpty()) Text(s.scNoLessons, color = TsmColors.TextDim)
            @Composable fun row(l: Lesson) {
                val cancelled = l.key in co.cancelled
                val marked = co.attendance[l.key]
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(TsmColors.Surface).clickable { open = l }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(lessonText(l, lang), color = if (cancelled) TsmColors.TextDim else TsmColors.TextMain, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Text(
                        when { cancelled -> s.scCancelled; marked != null -> s.scPresent(marked.size, co.students.size); l.start <= now -> s.scToMark; else -> "" },
                        color = when { cancelled -> TsmColors.Danger; marked != null -> TsmColors.Ok; l.start <= now -> TsmColors.Orange; else -> TsmColors.TextDim }, fontSize = 13.sp,
                    )
                }
            }
            if (past.isNotEmpty()) {
                Text(s.scPast, color = TsmColors.TextMain, fontWeight = FontWeight.Bold)
                for (l in if (showPast) past else past.take(4)) row(l)
                if (past.size > 4 && !showPast) TextButton(onClick = { showPast = true }) { Text(s.scShowAll(past.size), color = TsmColors.Ball) }
            }
            if (next.isNotEmpty()) {
                Text(s.scUpcoming, color = TsmColors.TextMain, fontWeight = FontWeight.Bold)
                for (l in next.take(10)) row(l)
            }
        }
    }
    open?.let { l -> CourseLessonDialog(c, cc, l) { open = null } }
    // Le lezioni del corso nel calendario: un evento che si ripete ogni settimana per ogni orario, fino alla fine.
    if (calendar) AlertDialog(
        onDismissRequest = { calendar = false },
        title = { Text(s.calRepeat) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val now = System.currentTimeMillis()
                for (slot in co.slots) {
                    val first = Courses.lessons(co).firstOrNull { it.start > now && CourtPlans.weekday(it.start) == slot.weekday && it.court == slot.court } ?: continue
                    GhostButton(java.text.SimpleDateFormat("EEEE HH:mm", lang.locale).format(java.util.Date(first.start)) + (slot.court.ifBlank { null }?.let { " · $it" } ?: ""),
                        Icons.Filled.School, {
                            addToCalendar(context, calendarIntent(co.name, first.start, slot.minutes, listOf(cc.card.club, slot.court).filter { it.isNotBlank() }.joinToString(" · "), weeklyUntil = co.to), s)
                        }, Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = { TextButton(onClick = { calendar = false }) { Text(s.back) } },
    )
    if (register) {
        val file = remember { c.registerFile(co.id, coachSide = true) }
        PdfActionsDialog(c, file, s.rgTitle, s.rgLegend, null, excel = { c.registerXlsx(co.id, coachSide = true) }) { register = false }
    }
}

/** L'appello di una lezione del corso: chi c'era e cosa si è fatto; oppure lezione annullata (e si avvisano gli iscritti). */
@Composable
private fun CourseLessonDialog(c: MatchController, cc: CoachCourse, l: Lesson, onDone: () -> Unit) {
    val s = LocalStrings.current
    val lang = c.options.collectAsState().value.lang
    val co = cc.course
    var present by remember { mutableStateOf(co.attendance[l.key] ?: co.students.toSet()) }
    var cancelled by remember { mutableStateOf(l.key in co.cancelled) }
    var note by remember { mutableStateOf(cc.notes[l.key].orEmpty()) }
    val canMark = l.start <= System.currentTimeMillis() + 3_600_000L
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(lessonText(l, lang)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SwitchRow(Icons.Filled.EventBusy, s.scCancelLesson, null, cancelled) { cancelled = it }
                if (cancelled) {
                    if (co.students.isNotEmpty()) GhostButton(s.scNotifyCancelled, Icons.Filled.Campaign, {
                        c.markCourseLesson(co.id, l.key, l.start, emptySet(), true, "")
                        onDone()
                        c.noticeCourseCancelled(co.id, l.start)
                    }, Modifier.fillMaxWidth())
                } else if (canMark) {
                    Text(s.scWhoWasThere, color = TsmColors.TextDim, fontSize = 13.sp)
                    for (sid in co.students) {
                        val name = c.playerById(sid)?.name ?: cc.card.students.firstOrNull { it.id == sid }?.name ?: continue
                        SwitchRow(Icons.Filled.School, name, null, sid in present) { on -> present = if (on) present + sid else present - sid }
                    }
                    OutlinedTextField(note, { note = it.take(500) }, Modifier.fillMaxWidth(), label = { Text(s.ccLessonNote) },
                        trailingIcon = { DictateIcon(c) { said -> note = appendDictated(note, said).take(500) } })
                } else Text(s.ccNotYet, color = TsmColors.TextDim, fontSize = 13.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (cancelled || canMark) c.markCourseLesson(co.id, l.key, l.start, present, cancelled, note)
                onDone()
            }) { Text(s.ok) }
        },
        dismissButton = { TextButton(onClick = onDone) { Text(s.back) } },
    )
}

/** Allenatore: è arrivata una scheda corso. */
@Composable
fun IncomingCourseDialog(c: MatchController, card: CourseCard) {
    val s = LocalStrings.current
    val lang = c.options.collectAsState().value.lang
    val update = c.coachBook.collectAsState().value.courses.any { it.card.course.id == card.course.id }
    AlertDialog(
        onDismissRequest = { c.incomingCourse.value = null },
        icon = { Icon(Icons.Filled.School, null, tint = TsmColors.Ball) },
        title = { Text(s.ccIncomingTitle) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(card.course.name, color = TsmColors.TextMain, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(listOfNotNull(card.course.level.ifBlank { null }, card.club.ifBlank { null }, "${shortDate(card.course.from, lang)} – ${shortDate(card.course.to, lang)}").joinToString(" · "),
                    color = TsmColors.TextDim, fontSize = 13.sp)
                Text(s.coGroupCount(card.students.size) + ": " + card.students.joinToString(", ") { it.name }, color = TsmColors.TextMain, fontSize = 14.sp)
                Text(s.ccIncomingHint, color = TsmColors.TextDim, fontSize = 13.sp)
            }
        },
        confirmButton = { TextButton(onClick = { c.acceptCourse(card) }) { Text(if (update) s.ccUpdate else s.ccAccept) } },
        dismissButton = { TextButton(onClick = { c.incomingCourse.value = null }) { Text(s.back) } },
    )
}

/** Segreteria: sono arrivate le presenze di un corso dall'allenatore. */
@Composable
fun IncomingAttendanceDialog(c: MatchController, r: AttendanceReport) {
    val s = LocalStrings.current
    val status = remember(r) { c.attendanceStatus(r) }
    AlertDialog(
        onDismissRequest = { c.incomingAttendance.value = null },
        icon = { Icon(Icons.Filled.School, null, tint = TsmColors.Ball) },
        title = { Text(s.ccAttendanceTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(r.courseName, color = TsmColors.TextMain, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(listOfNotNull(r.coach.ifBlank { null }?.let { s.ccFrom(it) }, s.ccLessonsMarked(r.attendance.size)).joinToString(" · "), color = TsmColors.TextDim, fontSize = 13.sp)
                val (msg, warn) = when (status) {
                    AttendanceStatus.READY -> s.ccAttReady to false
                    AttendanceStatus.SAME -> s.ccAttSame to false
                    AttendanceStatus.BAD_CODE -> s.ccAttBadCode to true
                    AttendanceStatus.NOT_FOUND -> s.ccAttNotFound to true
                }
                Text(msg, color = if (warn) TsmColors.Orange else TsmColors.TextDim, fontSize = 13.sp)
            }
        },
        confirmButton = {
            if (status == AttendanceStatus.READY) TextButton(onClick = { c.applyAttendance(r) }) { Text(s.mrApply) }
            else TextButton(onClick = { c.incomingAttendance.value = null }) { Text(s.ok) }
        },
        dismissButton = { if (status == AttendanceStatus.READY) TextButton(onClick = { c.incomingAttendance.value = null }) { Text(s.back) } },
    )
}

/** Segreteria: un allievo nuovo proposto dall'allenatore; si aggiunge ai giocatori e, a scelta, a un corso. */
@Composable
fun IncomingProposalDialog(c: MatchController, p: StudentProposal) {
    val s = LocalStrings.current
    val courses by c.courses.collectAsState()
    var pick by remember { mutableStateOf(p.course?.takeIf { id -> courses.any { it.id == id } }) }
    AlertDialog(
        onDismissRequest = { c.incomingProposal.value = null },
        icon = { Icon(Icons.Filled.School, null, tint = TsmColors.Ball) },
        title = { Text(s.ccProposalTitle) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(p.student.name, color = TsmColors.TextMain, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                val contacts = listOf(p.student.phone, p.student.email, listOf(p.student.guardian, p.student.guardianPhone).filter { it.isNotBlank() }.joinToString(" ")
                    .ifBlank { "" }.let { if (it.isBlank()) "" else "${s.coGuardian}: $it" }).filter { it.isNotBlank() }
                if (contacts.isNotEmpty()) Text(contacts.joinToString("\n"), color = TsmColors.TextMain, fontSize = 14.sp)
                Text(listOfNotNull(p.coach.ifBlank { null }?.let { s.ccFrom(it) }, p.level.ifBlank { null }, p.note.ifBlank { null }).joinToString(" · "), color = TsmColors.TextDim, fontSize = 13.sp)
                Text(s.ccProposalCourse, color = TsmColors.TextDim, fontSize = 13.sp)
                val options = listOf<String?>(null) + courses.sortedBy { it.name.lowercase() }.map { it.id }
                Segmented(options.map { id -> SegOption(id?.let { x -> courses.firstOrNull { it.id == x }?.name } ?: s.ccNoCourse) }, options.indexOf(pick), { pick = options[it] }, columns = 1)
            }
        },
        confirmButton = { TextButton(onClick = { c.applyProposal(p, pick) }) { Text(s.ccProposalAdd) } },
        dismissButton = { TextButton(onClick = { c.incomingProposal.value = null }) { Text(s.back) } },
    )
}

/** Allenatore: propone l'allievo alla segreteria, con livello e due righe. */
@Composable
fun ProposeStudentDialog(c: MatchController, st: PlayerProfile, onDone: () -> Unit) {
    val s = LocalStrings.current
    val context = LocalContext.current
    var level by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(s.ccPropose) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(s.ccProposeHint, color = TsmColors.TextDim, fontSize = 13.sp)
                OutlinedTextField(level, { level = it.take(40) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(s.ccLevel) })
                OutlinedTextField(note, { note = it.take(300) }, Modifier.fillMaxWidth(), label = { Text(s.coNote) },
                    trailingIcon = { DictateIcon(c) { said -> note = appendDictated(note, said).take(300) } })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val byApp = { c.proposalToSend(st, level, note, null)?.let { (f, text, office) -> sendToOffice(context, f, text, office) } }
                // Con il server del circolo dei suoi corsi la proposta va da lì; se non risponde, su WhatsApp.
                if (c.coachVia() != null) scope.launch { if (c.sendProposalServer(st, level, note)) android.widget.Toast.makeText(context, s.ibSentServer, android.widget.Toast.LENGTH_SHORT).show() else byApp() }
                else byApp()
                onDone()
            }) { Text(s.ccPropose) }
        },
        dismissButton = { TextButton(onClick = onDone) { Text(s.back) } },
    )
}

/** Allenatore: il riepilogo di un mese (lezioni private, corsi, presenze, incassi), da mandare alla segreteria. */
@Composable
fun MonthSummaryDialog(c: MatchController, onDone: () -> Unit) {
    val s = LocalStrings.current
    val context = LocalContext.current
    val lang = c.options.collectAsState().value.lang
    val book by c.coachBook.collectAsState()
    var back by remember { mutableStateOf(0) }
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = CourtPlans.dayStart(System.currentTimeMillis()); set(java.util.Calendar.DAY_OF_MONTH, 1); add(java.util.Calendar.MONTH, -back) }
    val from = cal.timeInMillis
    val to = (cal.clone() as java.util.Calendar).apply { add(java.util.Calendar.MONTH, 1) }.timeInMillis
    val label = java.text.SimpleDateFormat("LLLL yyyy", lang.locale).format(java.util.Date(from))
    val m = Coaching.month(book, from, to)
    val text = c.monthText(m, label)
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(s.cmMonth) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { back++ }) { Text("‹", fontSize = 20.sp) }
                    Text(label, color = TsmColors.TextMain, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(enabled = back > 0, onClick = { back-- }) { Text("›", fontSize = 20.sp) }
                }
                Text(text.lines().drop(1).joinToString("\n"), color = TsmColors.TextMain, fontSize = 14.sp)
                if (m.paidCents == 0 && m.privateLessons == 0 && m.courseLessons == 0) Text(s.cmEmpty, color = TsmColors.TextDim, fontSize = 13.sp)
                // Le ricevute dei pacchetti dell'anno del mese mostrato, in Excel (per il commercialista).
                val year = java.util.Calendar.getInstance().apply { timeInMillis = from }.get(java.util.Calendar.YEAR)
                GhostButton("${s.xlReceipts} $year (Excel)", Icons.Filled.School, { shareXlsx(context, c, c.receiptsXlsx(year)) }, Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val wa = Notices.waNumber(c.coachOffice())
                val i = if (wa != null) android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://wa.me/$wa?text=" + android.net.Uri.encode(text)))
                else android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, text), null)
                runCatching { context.startActivity(i) }
            }) { Text(s.cmSend) }
        },
        dismissButton = { TextButton(onClick = onDone) { Text(s.back) } },
    )
}

@Suppress("unused")
private fun money(cents: Int, lang: com.tennis.scoremanager.model.Lang) = Fees.money(cents, lang.locale)
