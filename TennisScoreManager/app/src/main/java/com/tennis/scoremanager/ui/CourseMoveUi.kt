// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tennis.scoremanager.MatchController
import com.tennis.scoremanager.data.Course
import com.tennis.scoremanager.data.CourtPlans
import com.tennis.scoremanager.data.Courses
import com.tennis.scoremanager.data.Holidays
import com.tennis.scoremanager.data.Lesson
import com.tennis.scoremanager.data.Reports
import com.tennis.scoremanager.ui.screens.DateButton

/** "Spostata dal martedì 8 dicembre, 17:00": per le righe delle lezioni. */
fun movedLine(s: Strings, l: Lesson, lang: com.tennis.scoremanager.model.Lang): String? =
    l.movedFrom?.let { s.scMovedFrom(com.tennis.scoremanager.data.Notices.day(it, lang) + ", " + Reports.time(it, lang)) }

/**
 * Sposta una lezione del corso: un altro giorno, un'altra ora, un altro campo, o il recupero in fondo al corso
 * (il primo orario libero dopo l'ultima lezione, saltando le feste). La lezione tiene la sua chiave: presenze e
 * registro restano. [onMove] riceve inizio, durata, campo (null = come prima) e se avvisare gli iscritti.
 */
@Composable
fun MoveLessonDialog(c: MatchController, co: Course, l: Lesson, courts: List<String>, onMove: (Long, Int?, String?, Boolean) -> Unit, onDone: () -> Unit) {
    val s = LocalStrings.current
    val context = LocalContext.current
    val lang = c.options.collectAsState().value.lang
    val country = remember { Holidays.country(lang.code) }
    var start by remember { mutableLongStateOf(l.start) }
    var minutes by remember { mutableIntStateOf(l.minutes) }
    var court by remember { mutableStateOf(l.court) }
    val makeup = remember(co, l.key) { Courses.makeup(co.copy(moved = co.moved - l.key)) { t -> Holidays.on(t, country) != null } }
    fun when_(t: Long) = com.tennis.scoremanager.data.Notices.day(t, lang) + ", " + Reports.time(t, lang)
    AlertDialog(
        onDismissRequest = onDone,
        icon = { Icon(Icons.Filled.EventRepeat, null, tint = TsmColors.Ball) },
        title = { Text(s.scMoveTitle) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(co.name + " · " + when_(l.movedFrom ?: l.start), color = TsmColors.TextMain, fontWeight = FontWeight.Bold)
                Text(s.scMoveHint, color = TsmColors.TextDim, fontSize = 13.sp)
                movedLine(s, l, lang)?.let {
                    Text(it, color = TsmColors.Orange, fontSize = 13.sp)
                    GhostButton(s.scMoveBack, Icons.Filled.Undo, { onMove(l.movedFrom!!, null, null, false) }, Modifier.fillMaxWidth())
                }
                makeup?.let { mk ->
                    GhostButton(s.scMakeupOn(when_(mk.start)), Icons.Filled.EventRepeat, { start = mk.start; minutes = mk.minutes; court = mk.court }, Modifier.fillMaxWidth())
                }
                DateButton(s.scMoveTo, start, lang) { d ->
                    val cal = java.util.Calendar.getInstance().apply { timeInMillis = start }
                    start = CourtPlans.at(CourtPlans.dayStart(d), cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE))
                }
                GhostButton("${s.ctTime}: ${Reports.time(start, lang)}", Icons.Filled.Schedule, {
                    val cal = java.util.Calendar.getInstance().apply { timeInMillis = start }
                    TimePickerDialog(context, { _, h, m -> start = CourtPlans.at(CourtPlans.dayStart(start), h, m) }, cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE), true).show()
                }, Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(s.bkDuration, color = TsmColors.TextMain, modifier = Modifier.weight(1f))
                    IconButton(onClick = { minutes = (minutes - 15).coerceAtLeast(15) }) { Icon(Icons.Filled.Remove, null) }
                    Text(s.bkMinutes(minutes), color = TsmColors.TextMain, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { minutes = (minutes + 15).coerceAtMost(300) }) { Icon(Icons.Filled.Add, null) }
                }
                val opts = (listOf("") + courts + listOf(l.court)).distinct()
                if (opts.size > 1) Segmented(opts.map { SegOption(it.ifBlank { "—" }) }, opts.indexOf(court).coerceAtLeast(0), { court = opts[it] }, columns = minOf(3, opts.size))
                Holidays.on(start, country)?.let { Text("${s.scMoveHoliday}: $it", color = TsmColors.Danger, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                if (co.students.isNotEmpty()) GhostButton(s.scNotifyMoved, Icons.Filled.Campaign, { onMove(start, minutes, court, true) }, Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { onMove(start, minutes, court, false) }) { Text(s.scMove) } },
        dismissButton = { TextButton(onClick = onDone) { Text(s.back) } },
    )
}
