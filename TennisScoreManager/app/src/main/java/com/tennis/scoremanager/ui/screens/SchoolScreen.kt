// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.ui.screens

import android.app.TimePickerDialog
import android.widget.Toast
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Euro
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.tennis.scoremanager.data.Course
import com.tennis.scoremanager.data.CourseFee
import com.tennis.scoremanager.data.CourseSlot
import com.tennis.scoremanager.data.Courses
import com.tennis.scoremanager.data.CourtPlans
import com.tennis.scoremanager.data.Fees
import com.tennis.scoremanager.data.Lesson
import com.tennis.scoremanager.data.Reports
import com.tennis.scoremanager.model.Lang
import com.tennis.scoremanager.ui.BigButton
import com.tennis.scoremanager.ui.GhostButton
import com.tennis.scoremanager.ui.LocalStrings
import com.tennis.scoremanager.ui.PlayerAvatar
import com.tennis.scoremanager.ui.ScreenScaffold
import com.tennis.scoremanager.ui.SectionCard
import com.tennis.scoremanager.ui.SegOption
import com.tennis.scoremanager.ui.Segmented
import com.tennis.scoremanager.ui.Strings
import com.tennis.scoremanager.ui.SwitchRow
import com.tennis.scoremanager.ui.TsmColors
import java.util.Calendar

/** "Lun 17:00 · 1 h 30' · Campo 2". */
private fun slotLine(s: Strings, x: CourseSlot, lang: Lang): String {
    val cal = Calendar.getInstance().apply { set(Calendar.DAY_OF_WEEK, if (x.weekday == 7) Calendar.SUNDAY else x.weekday + 1) }
    val day = java.text.SimpleDateFormat("EEE", lang.locale).format(cal.time)
    return listOf("$day %02d:%02d".format(x.startMin / 60, x.startMin % 60), s.bkMinutes(x.minutes), x.court).filter { it.isNotBlank() }.joinToString(" · ")
}

private fun lessonLine(l: Lesson, lang: Lang): String =
    "${com.tennis.scoremanager.data.Notices.day(l.start, lang)} · ${Reports.time(l.start, lang)}" + (if (l.court.isNotBlank()) " · ${l.court}" else "")

/** Circolo › Scuola tennis: i corsi in corso, quelli che devono iniziare e quelli finiti. */
@Composable
fun SchoolScreen(c: MatchController) {
    val s = LocalStrings.current
    BackHandler { c.back() }
    val list by c.courses.collectAsState()
    val lang = c.options.collectAsState().value.lang
    val now = remember { System.currentTimeMillis() }
    var creating by remember { mutableStateOf(false) }
    ScreenScaffold(
        onHelp = { c.openHelp("7.16") },
        title = s.scTitle,
        subtitle = s.scHint,
        icon = Icons.Filled.School,
        bottomBar = {
            GhostButton(s.back, Icons.AutoMirrored.Filled.ArrowBack, { c.back() }, Modifier.weight(1f))
            BigButton(s.scNew, Icons.Filled.Add, { creating = true }, Modifier.weight(1f))
        },
    ) {
        if (list.isEmpty()) Text(s.scNone, color = TsmColors.TextDim)
        val groups = listOf(
            s.ctRunning to list.filter { it.from <= now && CourtPlans.addDays(CourtPlans.dayStart(it.to), 1) > now },
            s.scSoon to list.filter { it.from > now },
            s.ctDone to list.filter { CourtPlans.addDays(CourtPlans.dayStart(it.to), 1) <= now },
        )
        for ((title, items) in groups) {
            if (items.isEmpty()) continue
            SectionCard(title, Icons.Filled.School) {
                for (co in items.sortedBy { it.name.lowercase() }) {
                    Row(Modifier.fillMaxWidth().clickable { c.openCourse(co.id) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(co.name, color = TsmColors.TextMain, fontWeight = FontWeight.Bold)
                            Text(
                                listOfNotNull(co.level.ifBlank { null }, co.slots.joinToString(", ") { slotLine(s, it, lang) }.ifBlank { null },
                                    s.scStudentsCount(co.students.size, co.maxStudents)).joinToString(" · "),
                                color = TsmColors.TextDim, fontSize = 13.sp,
                            )
                            Courses.next(co, now)?.let { Text(s.scNext(lessonLine(it, lang)), color = TsmColors.Ball, fontSize = 13.sp) }
                        }
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = TsmColors.TextDim)
                    }
                }
            }
        }
    }
    if (creating) CourseEditDialog(c, null) { id -> creating = false; id?.let { c.openCourse(it) } }
}

/** Un corso: allievi con le presenze, lezioni con l'appello, quote. */
@Composable
fun CourseScreen(c: MatchController) {
    val s = LocalStrings.current
    val context = LocalContext.current
    BackHandler { c.back() }
    val list by c.courses.collectAsState()
    val id by c.openCourseId.collectAsState()
    val co = list.firstOrNull { it.id == id } ?: return
    val players by c.players.collectAsState()
    val book by c.clubBook.collectAsState()
    val lang = c.options.collectAsState().value.lang
    val now = remember { System.currentTimeMillis() }
    var tab by rememberSaveable { mutableStateOf(0) }
    var edit by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var openLesson by remember { mutableStateOf<Lesson?>(null) }
    var showPast by remember { mutableStateOf(false) }
    ScreenScaffold(
        onHelp = { c.openHelp("7.16") },
        title = co.name,
        subtitle = listOfNotNull(co.level.ifBlank { null }, (co.coach?.let { c.playerById(it)?.name } ?: co.coachName).ifBlank { null }?.let { s.scCoachLine(it) },
            "${shortDate(co.from, lang)} – ${shortDate(co.to, lang)}").joinToString(" · "),
        icon = Icons.Filled.School,
        action = { TextButton(onClick = { edit = true }) { Text(s.studentEdit, color = TsmColors.Ball) } },
        bottomBar = {
            GhostButton(s.back, Icons.AutoMirrored.Filled.ArrowBack, { c.back() }, Modifier.weight(1f))
            if (co.students.isNotEmpty()) BigButton(s.scNotify, Icons.Filled.Campaign, { c.noticeCourse(co, null) }, Modifier.weight(1f))
        },
    ) {
        if (co.slots.isNotEmpty()) Text(co.slots.joinToString("\n") { slotLine(s, it, lang) }, color = TsmColors.TextDim, fontSize = 13.sp)
        Segmented(listOf(SegOption(s.scTabStudents), SegOption(s.scTabLessons), SegOption(s.scTabFees)), tab, { tab = it })
        when (tab) {
            0 -> {
                Text(s.scStudentsCount(co.students.size, co.maxStudents), color = if (Courses.full(co)) TsmColors.Orange else TsmColors.TextDim, fontSize = 13.sp)
                if (!Courses.full(co)) BigButton(s.scAddStudent, Icons.Filled.PersonAdd, { adding = true }, Modifier.fillMaxWidth())
                for (sid in co.students) {
                    val p = c.playerById(sid)
                    val (pres, tot) = Courses.attendanceOf(co, sid, now)
                    Row(Modifier.fillMaxWidth().clickable(enabled = p != null) { p?.let { c.openStudent(it.id) } }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        PlayerAvatar(c, p?.name.orEmpty(), TsmColors.Ball, 40.dp)
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(p?.name ?: "—", color = TsmColors.TextMain, fontWeight = FontWeight.Bold)
                            if (tot > 0) Text(s.scAttendance(pres, tot), color = if (pres * 4 < tot * 3) TsmColors.Orange else TsmColors.TextDim, fontSize = 13.sp)
                            p?.let { Fees.owed(book, it) }?.takeIf { it > 0 }?.let { Text(s.issueFeeDue(Fees.money(it, lang.locale)), color = TsmColors.Orange, fontSize = 13.sp) }
                        }
                        IconButton(onClick = { c.saveCourse(co.copy(students = co.students - sid)) }) { Icon(Icons.Filled.Close, s.scRemoveStudent, tint = TsmColors.TextDim) }
                    }
                }
            }
            1 -> {
                // Le prossime lezioni; quelle passate a richiesta, dalla più recente.
                val all = Courses.lessons(co)
                val past = all.filter { it.start <= now }.reversed()
                val next = all.filter { it.start > now }
                if (all.isEmpty()) Text(s.scNoLessons, color = TsmColors.TextDim)
                @Composable fun row(l: Lesson) {
                    val cancelled = l.key in co.cancelled
                    val marked = co.attendance[l.key]
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(TsmColors.Surface).clickable { openLesson = l }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(lessonLine(l, lang), color = if (cancelled) TsmColors.TextDim else TsmColors.TextMain, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Text(
                            when { cancelled -> s.scCancelled; marked != null -> s.scPresent(marked.size, co.students.size); l.start <= now -> s.scToMark; else -> "" },
                            color = when { cancelled -> TsmColors.Danger; marked != null -> TsmColors.Ok; l.start <= now -> TsmColors.Orange; else -> TsmColors.TextDim }, fontSize = 13.sp,
                        )
                    }
                }
                if (past.isNotEmpty()) {
                    Text(s.scPast, color = TsmColors.TextMain, fontWeight = FontWeight.Bold)
                    for (l in if (showPast) past else past.take(3)) row(l)
                    if (past.size > 3 && !showPast) TextButton(onClick = { showPast = true }) { Text(s.scShowAll(past.size), color = TsmColors.Ball) }
                }
                if (next.isNotEmpty()) {
                    Text(s.scUpcoming, color = TsmColors.TextMain, fontWeight = FontWeight.Bold)
                    for (l in next.take(12)) row(l)
                }
            }
            else -> {
                if (co.feeCents <= 0) Text(s.scNoFee, color = TsmColors.TextDim)
                else {
                    Text("${Fees.money(co.feeCents, lang.locale)} · ${if (co.feeKind == CourseFee.MONTHLY) s.scMonthly else s.scSeasonFee}", color = TsmColors.TextMain, fontWeight = FontWeight.Bold)
                    val periods: List<Pair<Int, Int>?> = if (co.feeKind == CourseFee.MONTHLY) Courses.months(co) else listOf(null)
                    for (m in periods) {
                        val label = c.courseFeeLabel(co, m)
                        val charged = book.charges.filter { it.label == label && !it.cancelled }
                        val paid = charged.count { it.settled }
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(TsmColors.Surface).padding(10.dp)) {
                            Text(label.substringAfter(" · "), color = TsmColors.TextMain, fontWeight = FontWeight.Bold)
                            if (charged.isNotEmpty()) Text(s.feeChargedLine(paid, charged.size), color = if (paid == charged.size) TsmColors.Ok else TsmColors.Orange, fontSize = 13.sp)
                            if (charged.size < co.students.size) GhostButton(s.scChargeStudents, Icons.Filled.Euro, {
                                val n = c.chargeCourse(co, m)
                                Toast.makeText(context, s.scCharged(n), Toast.LENGTH_SHORT).show()
                            }, Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
    if (edit) CourseEditDialog(c, co) { edit = false }
    if (adding) PlayerPickDialog(c, co.students.toSet()) { p -> adding = false; p?.let { c.saveCourse(co.copy(students = co.students + it.id)) } }
    openLesson?.let { l -> LessonDialog(c, co, l) { openLesson = null } }
}

/** L'appello di una lezione: chi c'era; oppure lezione annullata (e si avvisano gli allievi). */
@Composable
private fun LessonDialog(c: MatchController, co: Course, l: Lesson, onDone: () -> Unit) {
    val s = LocalStrings.current
    val lang = c.options.collectAsState().value.lang
    var present by remember { mutableStateOf(co.attendance[l.key] ?: co.students.toSet()) }
    var cancelled by remember { mutableStateOf(l.key in co.cancelled) }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(lessonLine(l, lang)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SwitchRow(Icons.Filled.EventBusy, s.scCancelLesson, null, cancelled) { cancelled = it }
                if (!cancelled) {
                    Text(s.scWhoWasThere, color = TsmColors.TextDim, fontSize = 13.sp)
                    for (sid in co.students) {
                        val name = c.playerById(sid)?.name ?: continue
                        SwitchRow(Icons.Filled.School, name, null, sid in present) { on -> present = if (on) present + sid else present - sid }
                    }
                } else if (co.students.isNotEmpty()) GhostButton(s.scNotifyCancelled, Icons.Filled.Campaign, {
                    var x = Courses.setCancelled(c.course(co.id) ?: co, l.key, true)
                    c.saveCourse(x)
                    onDone()
                    c.noticeCourse(x, l)
                }, Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                var x = Courses.setCancelled(c.course(co.id) ?: co, l.key, cancelled)
                if (!cancelled && l.start <= System.currentTimeMillis() + 3_600_000L) x = Courses.mark(x, l.key, present)
                c.saveCourse(x)
                onDone()
            }) { Text(s.ok) }
        },
        dismissButton = { TextButton(onClick = onDone) { Text(s.back) } },
    )
}

/** Corso nuovo o da cambiare: nome, livello, istruttore, stagione, orari settimanali, posti, quota. */
@Composable
private fun CourseEditDialog(c: MatchController, co: Course?, onDone: (String?) -> Unit) {
    val s = LocalStrings.current
    val context = LocalContext.current
    val lang = c.options.collectAsState().value.lang
    val courts by c.clubCourts.collectAsState()
    val today = CourtPlans.dayStart(System.currentTimeMillis())
    var x by remember { mutableStateOf(co ?: Course(Courses.newId("k"), "", from = today, to = CourtPlans.addDays(today, 180), createdAt = System.currentTimeMillis())) }
    var fee by remember { mutableStateOf(co?.feeCents?.takeIf { it > 0 }?.let { plain(it, lang) }.orEmpty()) }
    var pickCoach by remember { mutableStateOf(false) }
    var newSlot by remember { mutableStateOf<CourseSlot?>(null) }
    var askDelete by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { onDone(null) },
        title = { Text(if (co == null) s.scNew else co.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(x.name, { x = x.copy(name = it.take(50)) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(s.scName) })
                OutlinedTextField(x.level, { x = x.copy(level = it.take(40)) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(s.scLevel) })
                // L'istruttore: dal database o scritto a mano.
                val coach = x.coach?.let { c.playerById(it)?.name }
                if (coach != null) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(s.scCoachLine(coach), color = TsmColors.TextMain, modifier = Modifier.weight(1f))
                    IconButton(onClick = { x = x.copy(coach = null) }) { Icon(Icons.Filled.Close, null, tint = TsmColors.TextDim) }
                } else {
                    OutlinedTextField(x.coachName, { x = x.copy(coachName = it.take(40)) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(s.scCoach) })
                    GhostButton(s.scCoachPick, Icons.Filled.PersonAdd, { pickCoach = true }, Modifier.fillMaxWidth())
                }
                DateButton(s.blkFrom, x.from, lang) { d -> x = x.copy(from = CourtPlans.dayStart(d), to = maxOf(x.to, CourtPlans.dayStart(d))) }
                DateButton(s.blkTo, x.to, lang) { d -> x = x.copy(to = maxOf(CourtPlans.dayStart(d), x.from)) }
                Text(s.scSchedule, color = TsmColors.TextMain, fontWeight = FontWeight.Bold)
                for (sl in x.slots) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(slotLine(s, sl, lang), color = TsmColors.TextMain, modifier = Modifier.weight(1f))
                    IconButton(onClick = { x = x.copy(slots = x.slots - sl) }) { Icon(Icons.Filled.Close, null, tint = TsmColors.TextDim) }
                }
                GhostButton(s.scAddSlot, Icons.Filled.Schedule, { newSlot = CourseSlot(1, 17 * 60, 60, courts.firstOrNull().orEmpty()) }, Modifier.fillMaxWidth())
                Text(s.scScheduleHint, color = TsmColors.TextDim, fontSize = 12.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (x.maxStudents == 0) s.scNoMax else s.scMax(x.maxStudents), color = TsmColors.TextMain, modifier = Modifier.weight(1f))
                    IconButton(onClick = { x = x.copy(maxStudents = (x.maxStudents - 1).coerceAtLeast(0)) }) { Icon(Icons.Filled.Remove, null) }
                    IconButton(onClick = { x = x.copy(maxStudents = (x.maxStudents + 1).coerceAtMost(40)) }) { Icon(Icons.Filled.Add, null) }
                }
                MoneyField(fee, s.scFee) { fee = it }
                Segmented(listOf(SegOption(s.scMonthly), SegOption(s.scSeasonFee)), if (x.feeKind == CourseFee.MONTHLY) 0 else 1, { x = x.copy(feeKind = if (it == 0) CourseFee.MONTHLY else CourseFee.SEASON) })
                OutlinedTextField(x.note, { x = x.copy(note = it.take(200)) }, Modifier.fillMaxWidth(), label = { Text(s.bkNote) })
                if (co != null) TextButton(onClick = { askDelete = true }) { Text(s.scDelete, color = TsmColors.Danger) }
            }
        },
        confirmButton = {
            TextButton(enabled = x.name.isNotBlank() && (fee.isBlank() || Fees.parse(fee) != null), onClick = {
                val saved = x.copy(name = x.name.trim(), feeCents = Fees.parse(fee.ifBlank { "0" }) ?: 0)
                c.saveCourse(saved)
                onDone(saved.id)
            }) { Text(s.ok) }
        },
        dismissButton = { TextButton(onClick = { onDone(null) }) { Text(s.back) } },
    )
    if (pickCoach) PlayerPickDialog(c, emptySet()) { p -> pickCoach = false; p?.let { x = x.copy(coach = it.id, coachName = it.name) } }
    newSlot?.let { sl0 ->
        var sl by remember { mutableStateOf(sl0) }
        AlertDialog(
            onDismissRequest = { newSlot = null },
            title = { Text(s.scAddSlot) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (d in 1..7) {
                            val on = sl.weekday == d
                            val cal = Calendar.getInstance().apply { set(Calendar.DAY_OF_WEEK, if (d == 7) Calendar.SUNDAY else d + 1) }
                            Text(java.text.SimpleDateFormat("EEE", lang.locale).format(cal.time).take(3), color = if (on) TsmColors.OnBall else TsmColors.TextMain, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (on) TsmColors.Ball else TsmColors.SurfaceHigh).clickable { sl = sl.copy(weekday = d) }.padding(horizontal = 6.dp, vertical = 8.dp))
                        }
                    }
                    GhostButton("${s.ctTime}: %02d:%02d".format(sl.startMin / 60, sl.startMin % 60), Icons.Filled.Schedule, {
                        TimePickerDialog(context, { _, h, m -> sl = sl.copy(startMin = h * 60 + m) }, sl.startMin / 60, sl.startMin % 60, true).show()
                    }, Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(s.bkDuration, color = TsmColors.TextMain, modifier = Modifier.weight(1f))
                        IconButton(onClick = { sl = sl.copy(minutes = (sl.minutes - 30).coerceAtLeast(30)) }) { Icon(Icons.Filled.Remove, null) }
                        Text(s.bkMinutes(sl.minutes), color = TsmColors.TextMain, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { sl = sl.copy(minutes = (sl.minutes + 30).coerceAtMost(300)) }) { Icon(Icons.Filled.Add, null) }
                    }
                    val opts = listOf("") + courts
                    Segmented(opts.map { SegOption(it.ifBlank { "—" }) }, opts.indexOf(sl.court).coerceAtLeast(0), { sl = sl.copy(court = opts[it]) }, columns = minOf(3, opts.size))
                }
            },
            confirmButton = { TextButton(onClick = { x = x.copy(slots = (x.slots + sl).sortedWith(compareBy({ it.weekday }, { it.startMin }))); newSlot = null }) { Text(s.ok) } },
            dismissButton = { TextButton(onClick = { newSlot = null }) { Text(s.back) } },
        )
    }
    if (askDelete && co != null) AlertDialog(
        onDismissRequest = { askDelete = false },
        text = { Text(s.scDeleteAsk(co.name)) },
        confirmButton = { TextButton(onClick = { askDelete = false; c.deleteCourse(co.id); onDone(null); c.back() }) { Text(s.scDelete, color = TsmColors.Danger) } },
        dismissButton = { TextButton(onClick = { askDelete = false }) { Text(s.back) } },
    )
}
