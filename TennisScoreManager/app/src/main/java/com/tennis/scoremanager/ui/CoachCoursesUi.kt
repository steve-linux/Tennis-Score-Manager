// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.EventRepeat
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

/** Il colore dell'etichetta di un corso ("#RRGGBB"). */
fun courseColor(hex: String): androidx.compose.ui.graphics.Color =
    runCatching { androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(TsmColors.Ball)

/** I pallini dei colori tra cui scegliere l'etichetta di un corso; quello scelto ha il bordo. */
@Composable
fun CourseColorDots(selected: String, onPick: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        for (hex in com.tennis.scoremanager.data.CourseCards.COLORS) {
            val on = hex == selected
            androidx.compose.foundation.layout.Box(
                Modifier.size(if (on) 32.dp else 26.dp).clip(androidx.compose.foundation.shape.CircleShape).background(courseColor(hex))
                    .then(if (on) Modifier.border(3.dp, TsmColors.TextMain, androidx.compose.foundation.shape.CircleShape) else Modifier)
                    .clickable { onPick(hex) },
            )
        }
    }
}

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
    val book by c.coaching.coachBook.collectAsState()
    val lang = c.options.collectAsState().value.lang
    val now = System.currentTimeMillis()
    var creating by remember { mutableStateOf(false) }
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
        // I corsi dell'allenatore in azzurro, quelli della segreteria nel colore dell'app.
        com.tennis.scoremanager.ui.screens.Entry(Icons.Filled.School, co.name, listOfNotNull(s.ccOwnTag.takeIf { cc.personal }, hint.ifBlank { null }).joinToString(" · ").ifBlank { null },
            tint = courseColor(cc.label)) { c.coaching.openCoachCourse(co.id) }
    }
    // Un corso suo, non della segreteria: gruppi, preagonistica, un camp estivo.
    com.tennis.scoremanager.ui.screens.Entry(Icons.Filled.Add, s.ccNew, s.ccNewHint) { creating = true }
    if (creating) CoachCourseEditDialog(c, null) { id -> creating = false; id?.let { c.coaching.openCoachCourse(it) } }
}

/**
 * Un corso dell'allenatore, nuovo o da cambiare: nome, livello, dal… al…, orari settimanali (giorno, ora, durata,
 * dove) e allievi. [onDone] riceve l'id del corso salvato (null = annullato o tolto).
 */
@Composable
fun CoachCourseEditDialog(c: MatchController, cc: CoachCourse?, onDone: (String?) -> Unit) {
    val s = LocalStrings.current
    val context = LocalContext.current
    val lang = c.options.collectAsState().value.lang
    val today = CourtPlans.dayStart(System.currentTimeMillis())
    var co by remember {
        mutableStateOf(cc?.card?.course ?: com.tennis.scoremanager.data.Course(Coaching.newId("cc"), "", from = today, to = CourtPlans.addDays(today, 180), createdAt = System.currentTimeMillis()))
    }
    var picking by remember { mutableStateOf(false) }
    var askDelete by remember { mutableStateOf(false) }
    var color by remember { mutableStateOf(cc?.label ?: com.tennis.scoremanager.data.CourseCards.OWN) }
    // Quante lezioni: dalla data d'inizio e dagli orari la fine si calcola da sola; le feste (se le si salta) non contano.
    var lessonsText by remember { mutableStateOf(cc?.course?.let { x -> Courses.lessons(x).count { it.key !in x.cancelled }.toString() } ?: "10") }
    var skipHolidays by remember { mutableStateOf(true) }
    val country = remember { com.tennis.scoremanager.data.Holidays.country(lang.code) }
    val isHoliday = { t: Long -> com.tennis.scoremanager.data.Holidays.on(t, country) != null }
    // Anche i giorni occupati dell'allenatore, se lo si chiede (e solo se ne ha).
    val busyList = c.coaching.coachBook.collectAsState().value.busy
    var skipBusy by remember { mutableStateOf(busyList.isNotEmpty()) }
    val isBusy = { t: Long -> Coaching.busyOn(busyList, t) != null }
    val skip = { t: Long -> (skipHolidays && isHoliday(t)) || (skipBusy && isBusy(t)) }
    // L'orario da aggiungere: giorno (1 = lunedì), ora, durata, dove.
    var wd by remember { mutableStateOf(1) }
    var startMin by remember { mutableStateOf(17 * 60) }
    var minutes by remember { mutableStateOf(60) }
    var place by remember { mutableStateOf("") }
    fun dayName(d: Int) = java.time.DayOfWeek.of(d).getDisplayName(java.time.format.TextStyle.SHORT, lang.locale)
    fun hm(m: Int) = "%02d:%02d".format(m / 60, m % 60)
    AlertDialog(
        onDismissRequest = { onDone(null) },
        title = { Text(if (cc == null) s.ccNew else co.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(co.name, { co = co.copy(name = it.take(60)) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(s.ccName) })
                OutlinedTextField(co.level, { co = co.copy(level = it.take(40)) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(s.ccLevel) })
                Text(s.ccColor, color = TsmColors.TextDim, fontSize = 13.sp)
                CourseColorDots(color) { color = it }
                com.tennis.scoremanager.ui.screens.DateButton(s.ctStart, co.from, lang) { d -> co = co.copy(from = d, to = maxOf(co.to, d)) }
                Text(s.ccSlots, color = TsmColors.TextMain, fontWeight = FontWeight.Bold)
                for (sl in co.slots) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${dayName(sl.weekday)} ${hm(sl.startMin)} · ${s.coMinutes(sl.minutes)}" + (sl.court.ifBlank { null }?.let { " · $it" } ?: ""), color = TsmColors.TextMain, modifier = Modifier.weight(1f))
                    TextButton(onClick = { co = co.copy(slots = co.slots - sl) }) { Text("✕", color = TsmColors.Danger) }
                }
                Segmented((1..7).map { SegOption(dayName(it)) }, wd - 1, { wd = it + 1 }, columns = 7)
                GhostButton(hm(startMin), Icons.Filled.EventBusy, {
                    android.app.TimePickerDialog(context, { _, h, m -> startMin = h * 60 + m }, startMin / 60, startMin % 60, true).show()
                }, Modifier.fillMaxWidth())
                val lengths = listOf(45, 60, 90, 120)
                Segmented(lengths.map { SegOption(s.coMinutes(it)) }, lengths.indexOf(minutes), { minutes = lengths[it] }, columns = 4)
                OutlinedTextField(place, { place = it.take(30) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(s.coPlace) })
                GhostButton(s.ccAddSlot, Icons.Filled.Add, {
                    co = co.copy(slots = (co.slots + com.tennis.scoremanager.data.CourseSlot(wd, startMin, minutes, place.trim())).distinct().sortedWith(compareBy({ it.weekday }, { it.startMin })))
                }, Modifier.fillMaxWidth())
                // Numero di lezioni e fine: calcolata da inizio, orari e feste; senza numero si sceglie a mano.
                OutlinedTextField(lessonsText, { v -> lessonsText = v.filter(Char::isDigit).take(3) }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text(s.ccLessonsCount) }, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
                SwitchRow(Icons.Filled.EventBusy, s.ccSkipHolidays, s.ccSkipHolidaysHint, skipHolidays) { skipHolidays = it }
                if (busyList.isNotEmpty()) SwitchRow(Icons.Filled.EventBusy, s.ccSkipBusy, s.ccSkipBusyHint, skipBusy) { skipBusy = it }
                val autoEnd = lessonsText.toIntOrNull()?.let { Courses.endAfter(co.from, co.slots, it, skip) }
                if (autoEnd != null) Text(s.ccEndAuto(shortDate(autoEnd, lang)), color = TsmColors.Ball, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                else com.tennis.scoremanager.ui.screens.DateButton(s.ctEnd, co.to, lang) { d -> co = co.copy(to = maxOf(d, co.from)) }
                Text(s.coGroupCount(co.students.size) + if (co.students.isNotEmpty()) ": " + co.students.mapNotNull { c.playerById(it)?.name }.joinToString(", ") else "",
                    color = TsmColors.TextMain, fontSize = 14.sp)
                GhostButton(s.ccPickStudents, Icons.Filled.School, { picking = true }, Modifier.fillMaxWidth())
                if (cc != null) TextButton(onClick = { askDelete = true }) { Text(s.delete, color = TsmColors.Danger) }
            }
        },
        confirmButton = {
            TextButton(enabled = co.name.isNotBlank() && co.slots.isNotEmpty(), onClick = {
                val end = lessonsText.toIntOrNull()?.let { Courses.endAfter(co.from, co.slots, it, skip) } ?: co.to
                val x = co.copy(name = co.name.trim(), level = co.level.trim(), to = end)
                // Le lezioni nei giorni di festa risultano annullate (si possono sempre rimettere dall'appello).
                c.coaching.saveCoachOwnCourse(x.copy(cancelled = x.cancelled + Courses.skipped(x, skip)), color); onDone(co.id)
            }) { Text(s.save) }
        },
        dismissButton = { TextButton(onClick = { onDone(null) }) { Text(s.back) } },
    )
    if (picking) StudentPickDialog(c, s.ccPickStudents, exclude = emptySet(), initial = co.students.toSet(), multi = true) { picked -> picking = false; co = co.copy(students = picked) }
    if (askDelete) AlertDialog(
        onDismissRequest = { askDelete = false },
        text = { Text(s.ccDeleteAsk(co.name)) },
        confirmButton = { TextButton(onClick = { askDelete = false; c.coaching.deleteCoachCourse(co.id); onDone(null); c.back() }) { Text(s.delete, color = TsmColors.Danger) } },
        dismissButton = { TextButton(onClick = { askDelete = false }) { Text(s.back) } },
    )
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
    val book by c.coaching.coachBook.collectAsState()
    val id by c.coaching.openCoachCourseId.collectAsState()
    val cc = book.courses.firstOrNull { it.card.course.id == id } ?: return
    val co = cc.course
    val lang = c.options.collectAsState().value.lang
    val now = remember { System.currentTimeMillis() }
    var tab by rememberSaveable { mutableStateOf(1) }
    var open by remember { mutableStateOf<Lesson?>(null) }
    var showPast by remember { mutableStateOf(false) }
    var register by remember { mutableStateOf(false) }
    var calendar by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    ScreenScaffold(
        onHelp = { c.openHelp("7.6") },
        title = co.name,
        subtitle = listOfNotNull(co.level.ifBlank { null }, cc.card.club.ifBlank { null }, "${shortDate(co.from, lang)} – ${shortDate(co.to, lang)}").joinToString(" · "),
        icon = Icons.Filled.School,
        bottomBar = {
            GhostButton(s.back, Icons.AutoMirrored.Filled.ArrowBack, { c.back() }, Modifier.weight(1f))
            if (!cc.personal) BigButton(s.ccSendAttendance, Icons.AutoMirrored.Filled.Send, {
                val byApp = { c.coaching.attendanceToSend(co.id)?.let { (f, text, office) -> if (sendToOffice(context, f, text, office)) c.coaching.attendanceSent(co.id) } }
                // Corso arrivato dal server del circolo: le presenze tornano da lì; se non risponde, su WhatsApp.
                if (cc.via != null) scope.launch { if (c.mailbox.sendAttendanceServer(co.id)) android.widget.Toast.makeText(context, s.ibSentServer, android.widget.Toast.LENGTH_SHORT).show() else byApp() }
                else byApp()
            }, Modifier.weight(1f))
        },
    ) {
        // Modifica (i corsi suoi), calendario e registro: sotto il titolo, così il nome del corso resta leggibile.
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (cc.personal) TextButton(onClick = { editing = true }) { Text(s.studentEdit, color = TsmColors.Ball) }
            TextButton(onClick = { calendar = true }) { Text(s.calShort, color = TsmColors.Ball) }
            TextButton(onClick = { register = true }) { Text(s.rgShort, color = TsmColors.Ball) }
        }
        // Un corso della segreteria dice se le presenze sono da mandare; uno dell'allenatore no (restano qui).
        if (!cc.personal) Text(cc.sentAt?.let { if (cc.toSend) s.ccToSend else s.ccSent(shortDate(it, lang)) } ?: if (cc.toSend) s.ccToSend else s.ccNothingYet,
            color = if (cc.toSend) TsmColors.Orange else TsmColors.TextDim, fontSize = 13.sp)
        else Text(s.ccOwn, color = courseColor(cc.label), fontSize = 13.sp)
        // L'etichetta colorata del corso: balza all'occhio nell'elenco e nel calendario (anche sul PC).
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(s.ccColor, color = TsmColors.TextDim, fontSize = 13.sp, modifier = Modifier.padding(end = 10.dp))
            CourseColorDots(cc.label) { c.coaching.setCoachCourseColor(co.id, it) }
        }
        Segmented(listOf(SegOption(s.scTabStudents), SegOption(s.scTabLessons)), tab, { tab = it })
        if (tab == 0) {
            // Con tanti iscritti: un filtro per nome.
            var who by remember { mutableStateOf("") }
            if (co.students.size > 12) OutlinedTextField(who, { who = it.take(40) }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text(s.coSearch) })
            for (sid in co.students.filter { sid -> who.isBlank() || (c.playerById(sid)?.name ?: cc.card.students.firstOrNull { it.id == sid }?.name.orEmpty()).contains(who.trim(), ignoreCase = true) }) {
                val p = c.playerById(sid)
                val (pres, tot) = Courses.attendanceOf(co, sid, now)
                Row(Modifier.fillMaxWidth().clickable(enabled = p != null) { p?.let { c.openStudent(it.id) } }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(p?.name ?: cc.card.students.firstOrNull { it.id == sid }?.name ?: "—", color = TsmColors.TextMain, fontWeight = FontWeight.Bold)
                        if (tot > 0) Text(s.scAttendance(pres, tot), color = if (pres * 4 < tot * 3) TsmColors.Orange else TsmColors.TextDim, fontSize = 13.sp)
                    }
                }
            }
            if (co.students.isNotEmpty()) GhostButton(s.coWrite, Icons.Filled.Campaign, { c.coaching.noticeStudents(co.students) }, Modifier.fillMaxWidth())
        } else {
            val all = Courses.lessons(co)
            val past = all.filter { it.start <= now }.reversed()
            val next = all.filter { it.start > now }
            if (all.isEmpty()) Text(s.scNoLessons, color = TsmColors.TextDim)
            @Composable fun row(l: Lesson) {
                val cancelled = l.key in co.cancelled
                val marked = co.attendance[l.key]
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(TsmColors.Surface).clickable { open = l }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(lessonText(l, lang), color = if (cancelled) TsmColors.TextDim else TsmColors.TextMain, fontSize = 14.sp)
                        // Lezione in un giorno di festa: in rosso, si vede subito.
                        com.tennis.scoremanager.data.Holidays.on(l.start, com.tennis.scoremanager.data.Holidays.country(lang.code))?.let { Text("${s.hdHoliday}: $it", color = TsmColors.Danger, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                        Coaching.busyOn(book.busy, l.start)?.let { Text(busyLabel(s, it), color = TsmColors.Orange, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                        movedLine(s, l, lang)?.let { Text(it, color = TsmColors.Orange, fontSize = 13.sp) }
                    }
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
    if (editing) CoachCourseEditDialog(c, cc) { editing = false }
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
        val file = remember { c.coaching.registerFile(co.id, coachSide = true) }
        PdfActionsDialog(c, file, s.rgTitle, s.rgLegend, null, excel = { c.coaching.registerXlsx(co.id, coachSide = true) }) { register = false }
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
    var query by remember { mutableStateOf("") }
    val canMark = l.start <= System.currentTimeMillis() + 3_600_000L
    var moving by remember { mutableStateOf(false) }
    if (moving) {
        MoveLessonDialog(c, co, l, emptyList(), { start, minutes, court, notify ->
            val x = c.moveCoachLesson(co.id, l.key, start, minutes, court)
            moving = false
            onDone()
            if (notify && x != null) c.school.noticeCourseMoved(x, l.key)
        }) { moving = false }
        return
    }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(lessonText(l, lang)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                movedLine(s, l, lang)?.let { Text(it, color = TsmColors.Orange, fontSize = 13.sp) }
                // Solo nei suoi corsi: l'orario di quelli della segreteria lo decide lei.
                if (cc.personal) GhostButton(s.scMove, Icons.Filled.EventRepeat, { moving = true }, Modifier.fillMaxWidth())
                SwitchRow(Icons.Filled.EventBusy, s.scCancelLesson, null, cancelled) { cancelled = it }
                if (cancelled) {
                    if (co.students.isNotEmpty()) GhostButton(s.scNotifyCancelled, Icons.Filled.Campaign, {
                        c.coaching.markCourseLesson(co.id, l.key, l.start, emptySet(), true, "")
                        onDone()
                        c.coaching.noticeCourseCancelled(co.id, l.start)
                    }, Modifier.fillMaxWidth())
                } else if (canMark) {
                    Text(s.scWhoWasThere + " (${present.size}/${co.students.size})", color = TsmColors.TextDim, fontSize = 13.sp)
                    // Con tanti iscritti: cerca, tutti presenti o nessuno, poi si sistemano gli altri.
                    val names = co.students.associateWith { sid -> c.playerById(sid)?.name ?: cc.card.students.firstOrNull { it.id == sid }?.name }
                    if (co.students.size > 12) OutlinedTextField(query, { query = it.take(40) }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text(s.coSearch) })
                    val shown = co.students.filter { sid -> names[sid] != null && (query.isBlank() || names[sid]!!.contains(query.trim(), ignoreCase = true)) }
                    Row {
                        TextButton(onClick = { present = present + shown }) { Text(s.pkAll, color = TsmColors.Ball) }
                        TextButton(onClick = { present = present - shown.toSet() }) { Text(s.pkNone, color = TsmColors.Ball) }
                    }
                    for (sid in shown) SwitchRow(Icons.Filled.School, names[sid]!!, null, sid in present) { on -> present = if (on) present + sid else present - sid }
                    OutlinedTextField(note, { note = it.take(500) }, Modifier.fillMaxWidth(), label = { Text(s.ccLessonNote) },
                        trailingIcon = { DictateIcon(c) { said -> note = appendDictated(note, said).take(500) } })
                } else Text(s.ccNotYet, color = TsmColors.TextDim, fontSize = 13.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (cancelled || canMark) c.coaching.markCourseLesson(co.id, l.key, l.start, present, cancelled, note)
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
    val update = c.coaching.coachBook.collectAsState().value.courses.any { it.card.course.id == card.course.id }
    AlertDialog(
        onDismissRequest = { c.coaching.incomingCourse.value = null },
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
        confirmButton = { TextButton(onClick = { c.coaching.acceptCourse(card) }) { Text(if (update) s.ccUpdate else s.ccAccept) } },
        dismissButton = { TextButton(onClick = { c.coaching.incomingCourse.value = null }) { Text(s.back) } },
    )
}

/** Segreteria: sono arrivate le presenze di un corso dall'allenatore. */
@Composable
fun IncomingAttendanceDialog(c: MatchController, r: AttendanceReport) {
    val s = LocalStrings.current
    val status = remember(r) { c.coaching.attendanceStatus(r) }
    AlertDialog(
        onDismissRequest = { c.coaching.incomingAttendance.value = null },
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
            if (status == AttendanceStatus.READY) TextButton(onClick = { c.coaching.applyAttendance(r) }) { Text(s.mrApply) }
            else TextButton(onClick = { c.coaching.incomingAttendance.value = null }) { Text(s.ok) }
        },
        dismissButton = { if (status == AttendanceStatus.READY) TextButton(onClick = { c.coaching.incomingAttendance.value = null }) { Text(s.back) } },
    )
}

/** Segreteria: un allievo nuovo proposto dall'allenatore; si aggiunge ai giocatori e, a scelta, a un corso. */
@Composable
fun IncomingProposalDialog(c: MatchController, p: StudentProposal) {
    val s = LocalStrings.current
    val courses by c.school.courses.collectAsState()
    var pick by remember { mutableStateOf(p.course?.takeIf { id -> courses.any { it.id == id } }) }
    AlertDialog(
        onDismissRequest = { c.coaching.incomingProposal.value = null },
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
        confirmButton = { TextButton(onClick = { c.profiles.applyProposal(p, pick) }) { Text(s.ccProposalAdd) } },
        dismissButton = { TextButton(onClick = { c.coaching.incomingProposal.value = null }) { Text(s.back) } },
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
                val byApp = { c.coaching.proposalToSend(st, level, note, null)?.let { (f, text, office) -> sendToOffice(context, f, text, office) } }
                // Con il server del circolo dei suoi corsi la proposta va da lì; se non risponde, su WhatsApp.
                if (c.mailbox.coachVia() != null) scope.launch { if (c.mailbox.sendProposalServer(st, level, note)) android.widget.Toast.makeText(context, s.ibSentServer, android.widget.Toast.LENGTH_SHORT).show() else byApp() }
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
    val book by c.coaching.coachBook.collectAsState()
    var back by remember { mutableStateOf(0) }
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = CourtPlans.dayStart(System.currentTimeMillis()); set(java.util.Calendar.DAY_OF_MONTH, 1); add(java.util.Calendar.MONTH, -back) }
    val from = cal.timeInMillis
    val to = (cal.clone() as java.util.Calendar).apply { add(java.util.Calendar.MONTH, 1) }.timeInMillis
    val label = java.text.SimpleDateFormat("LLLL yyyy", lang.locale).format(java.util.Date(from))
    val m = Coaching.month(book, from, to)
    val text = c.coaching.monthText(m, label)
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
                GhostButton("${s.xlReceipts} $year (Excel)", Icons.Filled.School, { shareXlsx(context, c, c.coaching.receiptsXlsx(year)) }, Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val wa = Notices.waNumber(c.coaching.coachOffice())
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
