// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.SportsTennis
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tennis.scoremanager.MatchController
import com.tennis.scoremanager.data.CoachBook
import com.tennis.scoremanager.data.Coaching
import com.tennis.scoremanager.data.CourtPlans
import com.tennis.scoremanager.data.Fees
import com.tennis.scoremanager.data.CoachLesson
import com.tennis.scoremanager.data.LessonPack
import com.tennis.scoremanager.data.PlayerProfile
import com.tennis.scoremanager.data.Reports
import com.tennis.scoremanager.data.StudentGroup
import com.tennis.scoremanager.data.TrainedStroke
import com.tennis.scoremanager.ui.screens.DateButton
import com.tennis.scoremanager.ui.screens.MoneyField
import com.tennis.scoremanager.ui.screens.shortDate
import java.util.Calendar

/**
 * La parte dell'allenatore nella scheda di un allievo: diario delle lezioni, obiettivi sui colpi, lezioni e pacchetti,
 * contatti (genitore, consenso) con "Scrivi" e "Partita con…".
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StudentCoachSections(c: MatchController, st: PlayerProfile) {
    val s = LocalStrings.current
    val book by c.coachBook.collectAsState()
    val sessions by c.serveSessions.collectAsState()
    val lang = c.options.collectAsState().value.lang
    var goalNew by remember { mutableStateOf(false) }
    var lesson by remember { mutableStateOf<CoachLesson?>(null) }
    var packNew by remember { mutableStateOf(false) }
    var matchPick by remember { mutableStateOf(false) }
    var deleteEntry by remember { mutableStateOf<String?>(null) }

    // Diario: una riga per lezione, con la data; tenendo premuto si toglie.
    SectionCard(s.coDiary, Icons.Filled.EditNote) {
        Text(s.coDiaryHint, color = TsmColors.TextDim, fontSize = 13.sp)
        var text by remember(st.id) { mutableStateOf("") }
        OutlinedTextField(text, { text = it.take(2000) }, Modifier.fillMaxWidth(), minLines = 2)
        GhostButton(s.coDiaryAdd, Icons.Filled.Add, { c.addDiary(st.id, text); text = "" }, Modifier.fillMaxWidth())
        val diary = Coaching.diaryOf(book, st.id)
        if (diary.isEmpty()) Text(s.coDiaryNone, color = TsmColors.TextDim, fontSize = 13.sp)
        for (d in diary.take(8)) Column(Modifier.fillMaxWidth().combinedClickable(onClick = {}, onLongClick = { deleteEntry = d.id }).padding(vertical = 4.dp)) {
            Text(shortDate(d.at, lang), color = TsmColors.Ball, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(d.text, color = TsmColors.TextMain, fontSize = 14.sp)
        }
    }

    // Obiettivi sui colpi, seguiti sulle sue sessioni.
    SectionCard(s.coGoals, Icons.Filled.Flag) {
        val goals = book.goals.filter { it.student == st.id }
        if (goals.isEmpty()) Text(s.coGoalsNone, color = TsmColors.TextDim, fontSize = 13.sp)
        for (g in goals) {
            val now = Coaching.goalStatus(g, sessions)
            Column(Modifier.fillMaxWidth().combinedClickable(onClick = {}, onLongClick = { c.deleteStudentGoal(g.id) }).padding(vertical = 4.dp)) {
                Text(s.coGoalLine(strokeLabel(s, g.stroke), g.targetPct), color = TsmColors.TextMain, fontWeight = FontWeight.Bold)
                Text(
                    g.reachedAt?.let { s.coGoalReached(shortDate(it, lang)) } ?: now.pct?.let { s.coGoalNow(it, now.sessions) } ?: s.coGoalNoData,
                    color = if (g.reachedAt != null) TsmColors.Ok else TsmColors.TextDim, fontSize = 13.sp,
                )
            }
        }
        GhostButton(s.coGoalNew, Icons.Filled.Add, { goalNew = true }, Modifier.fillMaxWidth())
    }

    // Lezioni: la prossima, il pacchetto (comprate, fatte, rimaste) e cosa c'è da pagare.
    SectionCard(s.coLessons, Icons.Filled.Event) {
        val next = Coaching.upcoming(Coaching.lessonsOf(book, st.id), System.currentTimeMillis()).firstOrNull()
        Text(next?.let { s.coNextLesson(c.lessonWhen(it)) } ?: s.coNoLessons, color = TsmColors.TextMain)
        val bal = Coaching.balance(book, st.id)
        if (bal.bought > 0 || bal.used > 0) Text(s.coBalance(bal.bought, bal.used, bal.left), color = if (bal.left < 0) TsmColors.Orange else TsmColors.TextDim, fontSize = 13.sp)
        for (p in book.packs.filter { it.student == st.id }.sortedByDescending { it.createdAt }.take(4)) Row(verticalAlignment = Alignment.CenterVertically) {
            Text(s.coPackLine(p.lessons, Fees.money(p.cents, lang.locale)) + " · " + shortDate(p.createdAt, lang), color = TsmColors.TextMain, fontSize = 13.sp, modifier = Modifier.weight(1f))
            if (p.paidAt == null) TextButton(onClick = { c.savePack(p.copy(paidAt = System.currentTimeMillis())) }) { Text(s.coMarkPaid, color = TsmColors.Orange) }
            else Text(s.coPaid, color = TsmColors.Ok, fontSize = 13.sp)
        }
        if (bal.dueCents > 0) Text(s.coDue(Fees.money(bal.dueCents, lang.locale)), color = TsmColors.Orange, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton(s.coLessonNew, Icons.Filled.Event, {
                lesson = CoachLesson(Coaching.newId("l"), CourtPlans.at(CourtPlans.addDays(CourtPlans.dayStart(System.currentTimeMillis()), 1), 18), students = listOf(st.id))
            }, Modifier.weight(1f))
            GhostButton(s.coPackNew, Icons.Filled.Payments, { packNew = true }, Modifier.weight(1f))
        }
    }

    // Contatti: il genitore (per i minorenni), il consenso, un messaggio, una partita con un altro allievo.
    SectionCard(s.coWrite, Icons.Filled.Message) {
        if (st.guardian.isNotBlank() || st.guardianPhone.isNotBlank()) Text("${s.coGuardian}: ${listOf(st.guardian, st.guardianPhone).filter { it.isNotBlank() }.joinToString(" · ")}", color = TsmColors.TextMain, fontSize = 13.sp)
        Text(st.consentAt?.let { s.coConsentGiven(shortDate(it, lang)) } ?: s.coConsentMissing, color = if (st.consentAt != null) TsmColors.Ok else TsmColors.Orange, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton(s.coWrite, Icons.Filled.Message, { c.noticeStudents(listOf(st.id)) }, Modifier.weight(1f))
            GhostButton(s.coMatchWith, Icons.Filled.SportsTennis, { matchPick = true }, Modifier.weight(1f))
        }
    }

    if (goalNew) StudentGoalDialog(c, st.id) { goalNew = false }
    lesson?.let { l -> LessonDialog(c, l, isNew = book.lessons.none { it.id == l.id }) { lesson = null } }
    if (packNew) PackDialog(c, st.id) { packNew = false }
    if (matchPick) StudentPickDialog(c, s.coMatchWith, exclude = setOf(st.id), multi = false) { picked -> matchPick = false; picked.firstOrNull()?.let { c.matchWith(st.id, it) } }
    deleteEntry?.let { id ->
        AlertDialog(
            onDismissRequest = { deleteEntry = null },
            text = { Text(s.delete + "?") },
            confirmButton = { TextButton(onClick = { c.deleteDiary(id); deleteEntry = null }) { Text(s.delete, color = TsmColors.Danger) } },
            dismissButton = { TextButton(onClick = { deleteEntry = null }) { Text(s.back) } },
        )
    }
}

/** Un obiettivo nuovo: il colpo, la percentuale di colpi dentro, su quante sessioni. */
@Composable
fun StudentGoalDialog(c: MatchController, student: String, onDone: () -> Unit) {
    val s = LocalStrings.current
    var stroke by remember { mutableStateOf(TrainedStroke.SERVE) }
    var target by remember { mutableIntStateOf(70) }
    var window by remember { mutableIntStateOf(3) }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(s.coGoalNew) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Segmented(TrainedStroke.entries.map { SegOption(strokeLabel(s, it)) }, TrainedStroke.entries.indexOf(stroke), { stroke = TrainedStroke.entries[it] }, columns = 2)
                Text(s.coGoalTarget, color = TsmColors.TextMain, fontWeight = FontWeight.Bold)
                val targets = listOf(50, 60, 70, 80, 90)
                Segmented(targets.map { SegOption("$it%") }, targets.indexOf(target), { target = targets[it] })
                Text(s.coGoalWindow, color = TsmColors.TextMain, fontWeight = FontWeight.Bold)
                val windows = listOf(1, 3, 5, 10)
                Segmented(windows.map { SegOption("$it") }, windows.indexOf(window), { window = windows[it] })
            }
        },
        confirmButton = { TextButton(onClick = { c.addStudentGoal(student, stroke, target, window); onDone() }) { Text(s.save) } },
        dismissButton = { TextButton(onClick = onDone) { Text(s.cancel) } },
    )
}

/** Una lezione nuova o da cambiare: chi (allievi o un gruppo), quando, quanto, dove; fatta, annullata, avvisa. */
@Composable
fun LessonDialog(c: MatchController, l0: CoachLesson, isNew: Boolean, onDone: () -> Unit) {
    val s = LocalStrings.current
    val context = LocalContext.current
    val lang = c.options.collectAsState().value.lang
    val book by c.coachBook.collectAsState()
    var l by remember(l0.id) { mutableStateOf(l0) }
    var pick by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(if (isNew) s.coLessonNew else c.lessonWhen(l)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(s.coWho, color = TsmColors.TextMain, fontWeight = FontWeight.Bold)
                Text(l.students.mapNotNull { c.playerById(it)?.name }.joinToString(", ").ifBlank { "—" }, color = TsmColors.TextMain,
                    modifier = Modifier.fillMaxWidth().clickable { pick = true }.padding(vertical = 4.dp))
                // Un gruppo riempie gli allievi in un tocco.
                if (book.groups.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (g in book.groups.take(3)) TextButton(onClick = { l = l.copy(students = g.students, group = g.id) }) { Text(g.name, color = TsmColors.Ball) }
                }
                DateButton(s.coWhen, l.start, lang) { d -> val cal = Calendar.getInstance().apply { timeInMillis = l.start }; l = l.copy(start = CourtPlans.at(CourtPlans.dayStart(d), cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))) }
                GhostButton(Reports.time(l.start, lang), Icons.Filled.AccessTime, {
                    val cal = Calendar.getInstance().apply { timeInMillis = l.start }
                    TimePickerDialog(context, { _, h, m -> l = l.copy(start = CourtPlans.at(CourtPlans.dayStart(l.start), h, m)) }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
                }, Modifier.fillMaxWidth())
                val lengths = listOf(30, 45, 60, 90, 120)
                Segmented(lengths.map { SegOption(s.coMinutes(it)) }, lengths.indexOf(l.minutes), { l = l.copy(minutes = lengths[it]) }, columns = 3)
                OutlinedTextField(l.place, { l = l.copy(place = it.take(60)) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(s.coPlace) })
                // Col circolo collegato (invito della segreteria) il campo si prenota da qui: la lezione resta salvata.
                if (c.bookingAccounts.collectAsState().value.isNotEmpty()) GhostButton(s.coBookCourt, Icons.Filled.Event, {
                    if (l.students.isNotEmpty()) c.saveLesson(l)
                    onDone()
                    c.go(com.tennis.scoremanager.Screen.BOOK_COURT)
                }, Modifier.fillMaxWidth())
                OutlinedTextField(l.note, { l = l.copy(note = it.take(300)) }, Modifier.fillMaxWidth(), label = { Text(s.coNote) })
                if (!isNew) {
                    SwitchRow(Icons.Filled.Event, s.coDone, null, l.done) { l = l.copy(done = it, cancelled = if (it) false else l.cancelled) }
                    SwitchRow(Icons.Filled.Event, s.coCancelLesson, null, l.cancelled) { l = l.copy(cancelled = it, done = if (it) false else l.done) }
                    GhostButton(s.coNotify, Icons.Filled.Message, { c.saveLesson(l); onDone(); c.noticeLessons(listOf(l)) }, Modifier.fillMaxWidth())
                    TextButton(onClick = { c.deleteLesson(l.id); onDone() }) { Text(s.delete, color = TsmColors.Danger) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { if (l.students.isNotEmpty()) { c.saveLesson(l); onDone() } }) { Text(s.save) } },
        dismissButton = { TextButton(onClick = onDone) { Text(s.cancel) } },
    )
    if (pick) StudentPickDialog(c, s.coWho, initial = l.students.toSet(), multi = true) { ids -> pick = false; l = l.copy(students = ids, group = null) }
}

/** Un pacchetto di lezioni per l'allievo: quante, il prezzo, se è già pagato. */
@Composable
fun PackDialog(c: MatchController, student: String, onDone: () -> Unit) {
    val s = LocalStrings.current
    var count by remember { mutableIntStateOf(10) }
    var price by remember { mutableStateOf("") }
    var paid by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(s.coPackNew) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(s.coPackLessons, color = TsmColors.TextMain, fontWeight = FontWeight.Bold)
                val counts = listOf(1, 5, 10, 20)
                Segmented(counts.map { SegOption("$it") }, counts.indexOf(count), { count = counts[it] })
                MoneyField(price, s.coPackPrice) { price = it }
                SwitchRow(Icons.Filled.Payments, s.coPackPaidNow, null, paid) { paid = it }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val cents = if (price.isBlank()) 0 else Fees.parse(price) ?: return@TextButton
                val now = System.currentTimeMillis()
                c.savePack(LessonPack(Coaching.newId("p"), student, count, cents, now, if (paid) now else null))
                onDone()
            }) { Text(s.save) }
        },
        dismissButton = { TextButton(onClick = onDone) { Text(s.cancel) } },
    )
}

/** Scegli uno o più allievi. */
@Composable
fun StudentPickDialog(c: MatchController, title: String, exclude: Set<String> = emptySet(), initial: Set<String> = emptySet(), multi: Boolean, onDone: (List<String>) -> Unit) {
    val s = LocalStrings.current
    val students by c.students.collectAsState()
    var picked by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = { onDone(if (multi) picked.toList() else emptyList()) },
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                for (p in students.filter { it.id !in exclude }) Row(
                    Modifier.fillMaxWidth().clickable {
                        if (!multi) onDone(listOf(p.id)) else picked = if (p.id in picked) picked - p.id else picked + p.id
                    }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PlayerAvatar(c, p.name, if (p.id in picked) TsmColors.Ball else TsmColors.TextDim, 32.dp)
                    Text(p.name, color = if (p.id in picked) TsmColors.Ball else TsmColors.TextMain, fontWeight = if (p.id in picked) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.padding(start = 10.dp))
                }
            }
        },
        confirmButton = { if (multi) TextButton(onClick = { onDone(picked.toList()) }) { Text(s.ok) } },
        dismissButton = { TextButton(onClick = { onDone(if (multi) initial.toList() else emptyList()) }) { Text(s.cancel) } },
    )
}

/** I gruppi di allievi: nuovo, chi c'è dentro, elimina. */
@Composable
fun GroupsDialog(c: MatchController, onDone: () -> Unit) {
    val s = LocalStrings.current
    val book by c.coachBook.collectAsState()
    var editing by remember { mutableStateOf<StudentGroup?>(null) }
    var pick by remember { mutableStateOf(false) }
    val g = editing
    if (g != null) {
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (book.groups.none { it.id == g.id }) s.coGroupNew else g.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(g.name, { editing = g.copy(name = it.take(40)) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(s.coGroupName) })
                    Text(g.students.mapNotNull { c.playerById(it)?.name }.joinToString(", ").ifBlank { "—" }, color = TsmColors.TextMain)
                    GhostButton(s.coWho, Icons.Filled.Groups, { pick = true }, Modifier.fillMaxWidth())
                    if (book.groups.any { it.id == g.id }) TextButton(onClick = { c.deleteGroup(g.id); editing = null }) { Text(s.delete, color = TsmColors.Danger) }
                }
            },
            confirmButton = { TextButton(onClick = { if (g.name.isNotBlank()) { c.saveGroup(g); editing = null } }) { Text(s.save) } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text(s.cancel) } },
        )
        if (pick) StudentPickDialog(c, s.coWho, initial = g.students.toSet(), multi = true) { ids -> pick = false; editing = g.copy(students = ids) }
        return
    }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(s.coGroups) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (gr in book.groups) Row(Modifier.fillMaxWidth().clickable { editing = gr }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(gr.name, color = TsmColors.TextMain, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(s.coGroupCount(gr.students.size), color = TsmColors.TextDim, fontSize = 13.sp)
                }
                GhostButton(s.coGroupNew, Icons.Filled.Add, { editing = StudentGroup(Coaching.newId("gr"), "") }, Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text(s.ok) } },
    )
}

/** Il consenso, coi campi del genitore, nella modifica del giocatore (vedi PlayerEditDialog). */
@Composable
fun GuardianFields(guardian: String, guardianPhone: String, consent: Boolean, onGuardian: (String) -> Unit, onPhone: (String) -> Unit, onConsent: (Boolean) -> Unit) {
    val s = LocalStrings.current
    OutlinedTextField(guardian, { onGuardian(it.take(60)) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(s.coGuardian) })
    OutlinedTextField(guardianPhone, { onPhone(it.take(30)) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(s.coGuardianPhone) })
    SwitchRow(Icons.Filled.VerifiedUser, s.coConsent, s.coConsentHint, consent) { onConsent(it) }
}

/** Le lezioni di [book] da domani a dopodomani (non annullate): il promemoria della sera prima. */
fun tomorrowLessons(book: CoachBook, now: Long): List<CoachLesson> {
    val day = CourtPlans.addDays(CourtPlans.dayStart(now), 1)
    return book.lessons.filter { !it.cancelled && it.start >= day && it.start < CourtPlans.addDays(day, 1) }.sortedBy { it.start }
}
