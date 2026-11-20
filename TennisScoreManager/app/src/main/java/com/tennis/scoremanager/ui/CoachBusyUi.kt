// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tennis.scoremanager.MatchController
import com.tennis.scoremanager.data.BusyKind
import com.tennis.scoremanager.data.CoachBusy
import com.tennis.scoremanager.data.Coaching
import com.tennis.scoremanager.data.CourtPlans
import com.tennis.scoremanager.data.Reports
import com.tennis.scoremanager.ui.screens.DateButton

fun busyKindName(s: Strings, k: BusyKind): String = when (k) {
    BusyKind.BUSY -> s.bzBusy
    BusyKind.VACATION -> s.bzVacation
    BusyKind.TOURNAMENT -> s.bzTournament
    BusyKind.SICK -> s.bzSick
    BusyKind.OTHER -> s.bzOther
}

/** "Ferie · campo estivo": il motivo e la nota. */
fun busyLabel(s: Strings, b: CoachBusy): String = listOf(busyKindName(s, b.kind), b.note).filter { it.isNotBlank() }.joinToString(" · ")

/** I giorni dell'impegno: "12 dic" o "12 dic – 20 dic". */
fun busyDays(b: CoachBusy, lang: com.tennis.scoremanager.model.Lang): String =
    if (CourtPlans.dayStart(b.from) == CourtPlans.dayStart(b.to)) Reports.date(b.from, lang) else Reports.date(b.from, lang) + " – " + Reports.date(b.to, lang)

/**
 * I miei impegni (nell'Agenda delle lezioni): i giorni occupati da oggi in poi, le lezioni che ci cadono e
 * il pulsante per aggiungerne uno.
 */
@Composable
fun BusySection(c: MatchController) {
    val s = LocalStrings.current
    val lang = c.options.collectAsState().value.lang
    val book by c.coaching.coachBook.collectAsState()
    var open by remember { mutableStateOf<CoachBusy?>(null) }
    var isNew by remember { mutableStateOf(false) }
    val today = CourtPlans.dayStart(System.currentTimeMillis())
    val clashes = Coaching.busyClashes(book, System.currentTimeMillis()) { ids -> ids.mapNotNull { c.playerById(it)?.name }.joinToString(", ") }
    if (clashes.isNotEmpty()) SectionCard(s.bzClash(clashes.size), Icons.Filled.Warning) {
        for (x in clashes.take(20)) Text("${c.coaching.lessonWhen(com.tennis.scoremanager.data.CoachLesson("", x.start))} · ${x.title} — ${busyLabel(s, x.busy)}",
            color = TsmColors.Orange, fontSize = 14.sp)
        if (clashes.size > 20) Text("+${clashes.size - 20}", color = TsmColors.TextDim)
        Text(s.bzClashHint, color = TsmColors.TextDim, fontSize = 13.sp)
    }
    SectionCard(s.bzTitle, Icons.Filled.EventBusy) {
        Text(s.bzHint, color = TsmColors.TextDim, fontSize = 13.sp)
        val next = book.busy.filter { CourtPlans.dayStart(it.to) >= today }.sortedBy { it.from }
        if (next.isEmpty()) Text(s.bzNone, color = TsmColors.TextDim)
        for (b in next) Column(Modifier.fillMaxWidth().clickable { isNew = false; open = b }.padding(vertical = 6.dp)) {
            Text(busyDays(b, lang), color = TsmColors.Orange, fontWeight = FontWeight.Bold)
            Text(busyLabel(s, b), color = TsmColors.TextMain, fontSize = 14.sp)
        }
        GhostButton(s.bzNew, Icons.Filled.EventBusy, {
            isNew = true
            open = CoachBusy(Coaching.newId("bz"), CourtPlans.addDays(today, 1), CourtPlans.addDays(today, 1))
        }, Modifier.fillMaxWidth())
    }
    open?.let { b -> BusyDialog(c, b, isNew) { open = null } }
}

/** Un impegno: dal… al…, il motivo e una nota. */
@Composable
fun BusyDialog(c: MatchController, b0: CoachBusy, isNew: Boolean, onDone: () -> Unit) {
    val s = LocalStrings.current
    val lang = c.options.collectAsState().value.lang
    var b by remember(b0.id) { mutableStateOf(b0) }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(s.bzNew) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DateButton(s.bzFrom, b.from, lang) { d -> val x = CourtPlans.dayStart(d); b = b.copy(from = x, to = maxOf(x, b.to)) }
                DateButton(s.bzTo, b.to, lang) { d -> val x = CourtPlans.dayStart(d); b = b.copy(to = x, from = minOf(x, b.from)) }
                val kinds = BusyKind.entries
                Segmented(kinds.map { SegOption(busyKindName(s, it)) }, kinds.indexOf(b.kind), { b = b.copy(kind = kinds[it]) }, columns = 3)
                OutlinedTextField(b.note, { b = b.copy(note = it.take(200)) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(s.bzNote) })
                if (!isNew) TextButton(onClick = { c.deleteCoachBusy(b.id); onDone() }) { Text(s.delete, color = TsmColors.Danger) }
            }
        },
        confirmButton = { TextButton(onClick = { c.saveCoachBusy(b); onDone() }) { Text(s.save) } },
        dismissButton = { TextButton(onClick = onDone) { Text(s.cancel) } },
    )
}
