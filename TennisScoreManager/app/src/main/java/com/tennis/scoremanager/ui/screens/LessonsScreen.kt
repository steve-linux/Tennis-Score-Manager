// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tennis.scoremanager.MatchController
import com.tennis.scoremanager.data.Coaching
import com.tennis.scoremanager.data.CourtPlans
import com.tennis.scoremanager.data.CoachLesson
import com.tennis.scoremanager.ui.BigButton
import com.tennis.scoremanager.ui.GhostButton
import com.tennis.scoremanager.ui.LessonDialog
import com.tennis.scoremanager.ui.LocalStrings
import com.tennis.scoremanager.ui.ScreenScaffold
import com.tennis.scoremanager.ui.SectionCard
import com.tennis.scoremanager.ui.TsmColors
import com.tennis.scoremanager.ui.tomorrowLessons

/**
 * Allenatore › Agenda delle lezioni: quelle in programma, il promemoria di quelle di domani (WhatsApp o SMS a ogni
 * allievo, al genitore se è minorenne) e quelle passate da segnare come fatte (contano nei pacchetti).
 */
@Composable
fun LessonsScreen(c: MatchController) {
    val s = LocalStrings.current
    BackHandler { c.back() }
    val book by c.coachBook.collectAsState()
    var open by remember { mutableStateOf<CoachLesson?>(null) }
    val now = System.currentTimeMillis()
    ScreenScaffold(
        onHelp = { c.openHelp("7.6") },
        title = s.coAgenda, subtitle = s.coAgendaHint, icon = Icons.Filled.Event,
        bottomBar = {
            GhostButton(s.back, Icons.AutoMirrored.Filled.ArrowBack, { c.back() }, Modifier.weight(1f))
            BigButton(s.coLessonNew, Icons.Filled.Add, {
                open = CoachLesson(Coaching.newId("l"), CourtPlans.at(CourtPlans.addDays(CourtPlans.dayStart(now), 1), 18))
            }, Modifier.weight(1f))
        },
    ) {
        val tomorrow = tomorrowLessons(book, now)
        if (tomorrow.isNotEmpty()) BigButton(s.coRemindTomorrow(tomorrow.size), Icons.Filled.NotificationsActive, { c.noticeLessons(tomorrow) }, Modifier.fillMaxWidth())
        SectionCard(s.coUpcoming, Icons.Filled.Event) {
            val next = Coaching.upcoming(book.lessons, now)
            if (next.isEmpty()) Text(s.coNoLessons, color = TsmColors.TextDim)
            for (l in next.take(30)) LessonRow(c, l) { open = l }
        }
        // Le passate non ancora segnate: un tocco su "Fatta" e contano nel pacchetto dell'allievo.
        val past = book.lessons.filter { !it.done && !it.cancelled && it.start + it.minutes * 60_000L <= now }.sortedByDescending { it.start }
        if (past.isNotEmpty()) SectionCard(s.coToMark, Icons.Filled.Event) {
            for (l in past.take(20)) Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { LessonRow(c, l) { open = l } }
                TextButton(onClick = { c.saveLesson(l.copy(done = true)) }) { Text(s.coDone, color = TsmColors.Ok) }
            }
        }
    }
    open?.let { l -> LessonDialog(c, l, isNew = book.lessons.none { it.id == l.id }) { open = null } }
}

@Composable
private fun LessonRow(c: MatchController, l: CoachLesson, onClick: () -> Unit) {
    val s = LocalStrings.current
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp)) {
        Text(c.lessonWhen(l) + " · " + s.coMinutes(l.minutes), color = TsmColors.TextMain, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text(l.students.mapNotNull { c.playerById(it)?.name }.joinToString(", ") + if (l.cancelled) " · ${s.coCancelled}" else if (l.done) " · ${s.coDone}" else "",
            color = if (l.cancelled) TsmColors.Orange else TsmColors.TextDim, fontSize = 13.sp)
    }
}
