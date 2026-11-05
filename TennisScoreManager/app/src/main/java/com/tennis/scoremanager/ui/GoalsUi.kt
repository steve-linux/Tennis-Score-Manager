// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.ui

import androidx.compose.foundation.Image
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
