// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.ui

import androidx.compose.foundation.Image
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tennis.scoremanager.MatchController
import com.tennis.scoremanager.data.Goal
import com.tennis.scoremanager.data.GoalKind
import com.tennis.scoremanager.data.Goals
import com.tennis.scoremanager.data.Reports
import com.tennis.scoremanager.data.YearReview
import com.tennis.scoremanager.data.YearReviewImage
import java.io.File

/** Il nome della voce, come in Progressi. */
fun goalName(s: Strings, k: GoalKind): String = when (k) {
    GoalKind.WINS -> s.progressWins
    GoalKind.POINTS_WON -> s.progressPointsWon
    GoalKind.FIRST_SERVE -> s.statFirstServeIn
    GoalKind.ACES -> s.statAces
    GoalKind.DOUBLE_FAULTS -> s.statDoubleFaults
    GoalKind.UNFORCED_ERRORS -> s.statUnforcedErrors
    GoalKind.MATCHES_MONTH -> s.goalMatchesMonth
    GoalKind.WORKOUTS_WEEK -> s.goalWorkoutsWeek
}

/** "Prime di servizio in campo almeno 60%". */
fun goalLine(s: Strings, g: Goal): String =
    "${goalName(s, g.kind)} " + (if (g.kind.lowerIsBetter) s.goalAtMost(Goals.format(g.kind, g.target)) else s.goalAtLeast(Goals.format(g.kind, g.target)))

/** Progressi › Obiettivi: ognuno con la sua barra; si aggiungono e si tolgono. */
@Composable
fun GoalsCard(c: MatchController) {
    val s = LocalStrings.current
    val goals by c.goals.collectAsState()
    val history by c.history.collectAsState()
    val workouts by c.workouts.collectAsState()
    var adding by remember { mutableStateOf(false) }
    val lang = c.options.value.lang
    SectionCard(s.goalsTitle, Icons.Filled.Flag) {
        if (c.myKey().isEmpty()) { Text(s.goalNeedMe, color = TsmColors.TextDim, fontSize = 13.sp); return@SectionCard }
        Text(s.goalsHint, color = TsmColors.TextDim, fontSize = 13.sp)
        if (goals.isEmpty()) Text(s.goalsNone, color = TsmColors.TextDim)
        for (g in goals) {
            val st = remember(g, history, workouts) { c.goalStatus(g) }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(goalLine(s, g), color = TsmColors.TextMain, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = { c.deleteGoal(g.id) }) { Text(s.goalDelete, color = TsmColors.TextDim) }
                }
                LinearProgressIndicator(progress = { st.progress }, modifier = Modifier.fillMaxWidth(), color = if (st.reached) TsmColors.Ok else TsmColors.Ball)
                val parts = listOfNotNull(
                    st.current?.let { s.goalNow(Goals.format(g.kind, it)) },
                    s.goalWindow(g.window).takeIf { g.kind.perMatch },
                    (g.window - st.count).takeIf { g.kind.perMatch && !st.reached && it > 0 }?.let { s.goalMore(it) },
                    g.reachedAt?.let { s.goalReached(Reports.date(it, lang)) },
                )
                Text(parts.joinToString(" · "), color = if (st.reached) TsmColors.Ok else TsmColors.TextDim, fontSize = 12.sp)
            }
        }
        GhostButton(s.goalNew, Icons.Filled.Add, { adding = true }, Modifier.fillMaxWidth())
    }
    if (adding) GoalDialog(c) { adding = false }
}

@Composable
private fun GoalDialog(c: MatchController, onDone: () -> Unit) {
    val s = LocalStrings.current
    val kinds = GoalKind.entries
    var kind by remember { mutableStateOf(GoalKind.FIRST_SERVE) }
    var target by remember(kind) { mutableStateOf(Goals.choices(kind)[Goals.choices(kind).size / 2]) }
    val windows = listOf(3, 5, 10)
    var window by remember { mutableIntStateOf(5) }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(s.goalNew) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(s.goalWhat, color = TsmColors.TextDim, fontSize = 13.sp)
                Segmented(kinds.map { SegOption(goalName(s, it)) }, kinds.indexOf(kind), { kind = kinds[it] }, columns = 1)
                Text(s.goalTarget + " · " + (if (kind.lowerIsBetter) s.goalAtMost("…") else s.goalAtLeast("…")), color = TsmColors.TextDim, fontSize = 13.sp)
                val choices = Goals.choices(kind)
                Segmented(choices.map { SegOption(Goals.format(kind, it)) }, choices.indexOf(target).coerceAtLeast(0), { target = choices[it] }, columns = 3)
                if (kind.perMatch) {
                    Text(s.goalWindowLabel, color = TsmColors.TextDim, fontSize = 13.sp)
                    Segmented(windows.map { SegOption(it.toString()) }, windows.indexOf(window), { window = windows[it] })
                }
            }
        },
        confirmButton = { TextButton(onClick = { c.addGoal(kind, target, window); onDone() }) { Text(s.ok) } },
        dismissButton = { TextButton(onClick = onDone) { Text(s.back) } },
    )
}

/** Il mio anno: l'anno da scegliere, l'immagine e il tasto per condividerla. */
@Composable
fun YearReviewDialog(c: MatchController, onDone: () -> Unit) {
    val s = LocalStrings.current
    val context = LocalContext.current
    val history by c.history.collectAsState()
    val workouts by c.workouts.collectAsState()
    val lang = c.options.value.lang
    val lines = remember(history) { c.myLines() }
    val years = remember(lines) { YearReview.years(lines).ifEmpty { listOf(java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)) } }
    var year by remember { mutableIntStateOf(years.first()) }
    val name = c.me()?.name ?: c.storage.myName
    val bmp = remember(year, lines, workouts) { YearReviewImage.render(YearReview.of(year, lines, c.myKey(), workouts.map { it.startedAt }), name, s, lang) }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(s.yrTitle) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (c.myKey().isEmpty()) { Text(s.goalNeedMe, color = TsmColors.TextDim, fontSize = 13.sp); return@Column }
                if (years.size > 1) Segmented(years.map { SegOption(it.toString()) }, years.indexOf(year), { year = years[it] }, columns = minOf(years.size, 4))
                Image(bmp.asImageBitmap(), contentDescription = s.yrOf(year), modifier = Modifier.fillMaxWidth())
                GhostButton(s.yrShare, Icons.Filled.Share, {
                    val f = File(File(context.cacheDir, "share").apply { mkdirs() }, "${s.yrOf(year)}.png")
                    f.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                    context.startActivity(android.content.Intent.createChooser(c.shareFileIntent(f, "image/png"), s.yrShare))
                }, Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text(s.ok) } },
    )
}

/** Progressi › Calendario: il mese coi giorni di partite (giallo) e allenamenti (rosso), e le settimane di fila. */
@Composable
fun ActivityCalendarCard(c: MatchController) {
    val s = LocalStrings.current
    val history by c.history.collectAsState()
    val workouts by c.workouts.collectAsState()
    val lang = c.options.value.lang
    val now = remember { System.currentTimeMillis() }
    val today = java.util.Calendar.getInstance()
    var ym by remember { mutableStateOf(today.get(java.util.Calendar.YEAR) * 12 + today.get(java.util.Calendar.MONTH)) }
    val days = remember(history, workouts) {
        com.tennis.scoremanager.data.ActivityCalendar.byDay(
            history.filter { it.record.finished }.map { it.record.id to (it.record.startedAt ?: it.record.updatedAt) },
            workouts.map { it.id to it.startedAt },
        )
    }
    val streak = remember(days) { com.tennis.scoremanager.data.ActivityCalendar.weekStreak(days.keys.toList(), now) }
    var picked by remember { mutableStateOf<Long?>(null) }
    SectionCard(s.calTitle, androidx.compose.material.icons.Icons.Filled.CalendarMonth) {
        Text(s.calHint, color = TsmColors.TextDim, fontSize = 13.sp)
        if (streak >= 1) Text(s.calStreak(streak), color = TsmColors.Ball, fontSize = 14.sp)
        val y = ym / 12; val m = ym % 12
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { ym--; picked = null }) { Text("←", fontSize = 20.sp) }
            val title = java.text.SimpleDateFormat("LLLL yyyy", lang.locale).format(java.util.Calendar.getInstance().apply { clear(); set(y, m, 1) }.time)
            Text(title.replaceFirstChar { it.titlecase(lang.locale) }, color = TsmColors.TextMain, fontSize = 16.sp, modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            TextButton(onClick = { ym++; picked = null }) { Text("→", fontSize = 20.sp) }
        }
        val names = java.text.DateFormatSymbols(lang.locale).shortWeekdays
        val order = listOf(java.util.Calendar.MONDAY, java.util.Calendar.TUESDAY, java.util.Calendar.WEDNESDAY, java.util.Calendar.THURSDAY,
            java.util.Calendar.FRIDAY, java.util.Calendar.SATURDAY, java.util.Calendar.SUNDAY)
        Row { for (d in order) Text(names[d].take(2).replaceFirstChar { it.titlecase(lang.locale) }, color = TsmColors.TextDim, fontSize = 11.sp,
            modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
        val todayStart = com.tennis.scoremanager.data.ActivityCalendar.dayStart(now)
        for (week in com.tennis.scoremanager.data.ActivityCalendar.monthGrid(y, m).chunked(7)) Row {
            for (d in week) {
                val info = d?.let { days[it] }
                Column(
                    Modifier.weight(1f).height(44.dp).padding(1.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                        .background(if (d != null && d == picked) TsmColors.SurfaceHigh else androidx.compose.ui.graphics.Color.Transparent)
                        .let { if (d != null) it.clickable { picked = d } else it },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    if (d != null) {
                        val n = java.util.Calendar.getInstance().apply { timeInMillis = d }.get(java.util.Calendar.DAY_OF_MONTH)
                        Text("$n", color = if (d == todayStart) TsmColors.Ball else TsmColors.TextMain, fontSize = 13.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            if (!info?.matches.isNullOrEmpty()) Dot(TsmColors.Ball)
                            if (!info?.workouts.isNullOrEmpty()) Dot(TsmColors.Danger)
                        }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Dot(TsmColors.Ball); Text(s.progressMatches, color = TsmColors.TextDim, fontSize = 12.sp)
            Dot(TsmColors.Danger); Text(s.progressWorkouts, color = TsmColors.TextDim, fontSize = 12.sp)
        }
        picked?.let { d ->
            val info = days[d]
            Text(com.tennis.scoremanager.data.Notices.day(d, lang), color = TsmColors.TextMain, fontSize = 14.sp)
            if (info == null) Text(s.calNoneDay, color = TsmColors.TextDim, fontSize = 13.sp)
            for (id in info?.matches.orEmpty()) history.firstOrNull { it.record.id == id }?.let { lm ->
                val rec = lm.record
                val names2 = com.tennis.scoremanager.model.Side.entries.joinToString(" – ") { com.tennis.scoremanager.data.PlayerStats.names(rec.setup, it).joinToString(" / ").ifEmpty { "?" } }
                TextButton(onClick = { c.openFromHistory(lm, com.tennis.scoremanager.Screen.PROGRESS) }, modifier = Modifier.fillMaxWidth()) {
                    Text("${Reports.time(rec.startedAt ?: rec.updatedAt, lang)} · $names2", color = TsmColors.TextMain, modifier = Modifier.fillMaxWidth())
                }
            }
            for (id in info?.workouts.orEmpty()) workouts.firstOrNull { it.id == id }?.let { w ->
                Text("${Reports.time(w.startedAt, lang)} · ${s.trainCardio} · ${Reports.duration(w.durationS * 1000L)}", color = TsmColors.TextMain, fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
            }
        }
    }
}

@Composable
private fun Dot(color: androidx.compose.ui.graphics.Color) =
    androidx.compose.foundation.layout.Box(Modifier.size(6.dp).clip(androidx.compose.foundation.shape.CircleShape).background(color))
