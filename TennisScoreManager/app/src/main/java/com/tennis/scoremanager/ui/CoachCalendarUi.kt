// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tennis.scoremanager.MatchController
import com.tennis.scoremanager.data.CoachBook
import com.tennis.scoremanager.data.CoachLesson
import com.tennis.scoremanager.data.Coaching
import com.tennis.scoremanager.data.CourtPlans
import com.tennis.scoremanager.data.Courses
import com.tennis.scoremanager.data.Holidays
import com.tennis.scoremanager.data.Reports
import java.util.Calendar

/** I tornei degli allievi nel calendario (viola: non si confonde con feste, impegni e corsi). */
private val EventColor = Color(0xFFAB47BC)

/** Una cosa del calendario dell'allenatore: lezione privata, lezione di un corso o torneo di un allievo. */
private data class CalEntry(val start: Long, val minutes: Int, val title: String, val color: Color, val cancelled: Boolean, val open: () -> Unit)

/** Il primo giorno del mese di [t] (inizio del giorno). */
private fun monthStart(t: Long): Long = Calendar.getInstance().apply {
    timeInMillis = CourtPlans.dayStart(t); set(Calendar.DAY_OF_MONTH, 1)
}.timeInMillis

private fun addMonths(t: Long, n: Int): Long = Calendar.getInstance().apply { timeInMillis = t; add(Calendar.MONTH, n) }.timeInMillis

/**
 * Il mese dell'allenatore: lezioni private, lezioni dei corsi (nel colore del corso) e tornei degli allievi; le feste
 * in rosso, i giorni occupati in arancione. Tocca un giorno per vederne le cose, e una cosa per aprirla.
 */
@Composable
fun CoachMonthCalendar(c: MatchController, book: CoachBook, onLesson: (CoachLesson) -> Unit) {
    val s = LocalStrings.current
    val lang = c.options.collectAsState().value.lang
    val country = remember(lang) { Holidays.country(lang.code) }
    val today = CourtPlans.dayStart(System.currentTimeMillis())
    var month by rememberSaveable { mutableLongStateOf(monthStart(today)) }
    var picked by rememberSaveable { mutableLongStateOf(today) }
    val next = addMonths(month, 1)
    val names = { ids: List<String> -> ids.mapNotNull { c.playerById(it)?.name }.joinToString(", ") }
    // Tutto il mese, giorno per giorno.
    val byDay = remember(book, month) {
        val out = mutableListOf<CalEntry>()
        for (l in book.lessons.filter { it.start in month until next }) out += CalEntry(l.start, l.minutes, names(l.students).ifBlank { "—" }, TsmColors.TextMain, l.cancelled) { onLesson(l) }
        for (cc in book.courses) {
            val co = cc.course
            for (les in Courses.lessons(co).filter { it.start in month until next })
                out += CalEntry(les.start, les.minutes, co.name, courseColor(cc.label), les.key in co.cancelled) { c.openCoachCourse(co.id) }
        }
        for (e in book.events.filter { it.at in month until next }) out += CalEntry(e.at, 0, "${e.name} · ${c.playerById(e.student)?.name.orEmpty()}", EventColor, false) { c.openStudent(e.student) }
        out.sortedBy { it.start }.groupBy { CourtPlans.dayStart(it.start) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { month = addMonths(month, -1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = TsmColors.TextMain) }
            Text(java.text.SimpleDateFormat("LLLL yyyy", lang.locale).format(month).replaceFirstChar { it.titlecase(lang.locale) },
                color = TsmColors.TextMain, fontWeight = FontWeight.Bold, fontSize = 17.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)
                    .clickable { month = monthStart(today); picked = today })
            IconButton(onClick = { month = addMonths(month, 1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = TsmColors.TextMain) }
        }
        // Le intestazioni da lunedì e le settimane del mese.
        Row(Modifier.fillMaxWidth()) {
            for (d in 1..7) Text(java.time.DayOfWeek.of(d).getDisplayName(java.time.format.TextStyle.SHORT, lang.locale).take(3),
                color = TsmColors.TextDim, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        }
        val lead = CourtPlans.weekday(month) - 1
        val days = generateSequence(month) { CourtPlans.addDays(it, 1) }.takeWhile { it < next }.toList()
        val cells: List<Long?> = List(lead) { null } + days
        for (week in cells.chunked(7)) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            for (i in 0 until 7) {
                val d = week.getOrNull(i)
                Box(Modifier.weight(1f).aspectRatio(0.9f)) {
                    if (d != null) {
                        val holiday = Holidays.on(d + 12 * 3_600_000L, country)
                        val busy = Coaching.busyOn(book.busy, d + 12 * 3_600_000L)
                        val items = byDay[d].orEmpty()
                        Column(
                            Modifier.fillMaxWidth().aspectRatio(0.9f).clip(RoundedCornerShape(8.dp))
                                .background(if (busy != null) TsmColors.Orange.copy(alpha = 0.22f) else TsmColors.Surface)
                                .then(if (d == picked) Modifier.border(2.dp, TsmColors.Ball, RoundedCornerShape(8.dp)) else Modifier)
                                .clickable { picked = d }.padding(3.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(Calendar.getInstance().apply { timeInMillis = d }.get(Calendar.DAY_OF_MONTH).toString(),
                                color = when { holiday != null -> TsmColors.Danger; d == today -> TsmColors.Ball; else -> TsmColors.TextMain },
                                fontWeight = if (d == today || holiday != null) FontWeight.Bold else FontWeight.Normal, fontSize = 14.sp)
                            // Un pallino per cosa (al massimo quattro), nel suo colore.
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                for (e in items.take(4)) Box(Modifier.size(6.dp).clip(CircleShape).background(if (e.cancelled) TsmColors.TextDim else e.color))
                            }
                            if (items.size > 4) Text("+${items.size - 4}", color = TsmColors.TextDim, fontSize = 9.sp)
                        }
                    }
                }
            }
        }
        // Il giorno scelto: feste, impegni e le sue cose da aprire.
        Text(com.tennis.scoremanager.data.Notices.day(picked, lang).replaceFirstChar { it.titlecase(lang.locale) }, color = TsmColors.TextMain, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
        Holidays.on(picked + 12 * 3_600_000L, country)?.let { Text("${s.hdHoliday}: $it", color = TsmColors.Danger, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
        Coaching.busyOn(book.busy, picked + 12 * 3_600_000L)?.let { Text(busyLabel(s, it), color = TsmColors.Orange, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
        val items = byDay[picked].orEmpty()
        if (items.isEmpty()) Text(s.agDayEmpty, color = TsmColors.TextDim, fontSize = 13.sp)
        for (e in items) Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(TsmColors.Surface).clickable { e.open() }.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(5.dp).size(5.dp, 30.dp).clip(RoundedCornerShape(3.dp)).background(e.color))
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(Reports.time(e.start, lang) + if (e.minutes > 0) " · " + s.coMinutes(e.minutes) else "", color = TsmColors.TextDim, fontSize = 12.sp)
                Text(e.title, color = if (e.cancelled) TsmColors.TextDim else TsmColors.TextMain, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                if (e.cancelled) Text(s.coCancelled, color = TsmColors.Orange, fontSize = 12.sp)
            }
        }
    }
}
