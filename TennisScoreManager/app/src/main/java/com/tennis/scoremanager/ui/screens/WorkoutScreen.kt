// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SportsTennis
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tennis.scoremanager.MatchController
import com.tennis.scoremanager.data.Reports
import com.tennis.scoremanager.data.WorkoutRecord
import com.tennis.scoremanager.data.WorkoutReport
import com.tennis.scoremanager.data.WorkoutStats
import com.tennis.scoremanager.model.Side
import com.tennis.scoremanager.ui.ServeTrainingCard
import com.tennis.scoremanager.ui.BigButton
import com.tennis.scoremanager.ui.ConfirmDialog
import com.tennis.scoremanager.ui.GhostButton
import com.tennis.scoremanager.ui.LocalStrings
import com.tennis.scoremanager.ui.ScreenScaffold
import com.tennis.scoremanager.ui.SectionCard
import com.tennis.scoremanager.ui.TsmColors
import com.tennis.scoremanager.ui.WorkoutStrapCard
import com.tennis.scoremanager.workout.WorkoutPhase

/**
 * Allenamento con la fascia cardio, senza partita: Avvia / Pausa / Giro / Fine, poi un minuto di recupero e il
 * riepilogo. Sotto, gli allenamenti salvati (a parte dalle partite).
 */
@Composable
fun WorkoutScreen(c: MatchController) {
    val s = LocalStrings.current
    val detail by c.workoutDetail.collectAsState()
    BackHandler { c.back() }
    val shown = detail
    if (shown != null) {
        WorkoutDetail(c, shown)
        return
    }
    val ui by c.workout.ui.collectAsState()
    val straps by c.straps.collectAsState()
    val list by c.workouts.collectAsState()
    val o by c.options.collectAsState()
    val rackets by c.rackets.collectAsState()
    val workoutRacket by c.workoutRacket.collectAsState()
    ScreenScaffold(
        title = s.workoutTitle,
        subtitle = s.workoutHint,
        icon = Icons.Filled.MonitorHeart,
        bottomBar = { GhostButton(s.back, Icons.AutoMirrored.Filled.ArrowBack, { c.back() }, Modifier.weight(1f)) },
    ) {
        SectionCard(s.workoutTitle, Icons.Filled.MonitorHeart, accent = TsmColors.Danger) {
            val strap = Side.entries.firstNotNullOfOrNull { straps[it] }
            if (ui.phase == WorkoutPhase.IDLE && strap == null) {
                Text(s.workoutPickStrap, color = TsmColors.Orange)
                return@SectionCard
            }
            Text(
                (ui.bpm ?: strap?.bpm)?.let { "$it" } ?: "--",
                color = TsmColors.TextMain, fontSize = 64.sp, fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
            Text("bpm", color = TsmColors.TextDim, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            strap?.let { Text(it.name, color = TsmColors.TextDim, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
            when (ui.phase) {
                WorkoutPhase.IDLE -> BigButton(s.workoutStart, Icons.Filled.PlayArrow, { c.startWorkout() }, Modifier.fillMaxWidth())
                WorkoutPhase.RUNNING, WorkoutPhase.PAUSED -> {
                    Text(
                        Reports.duration(ui.elapsedS * 1000L),
                        color = if (ui.phase == WorkoutPhase.PAUSED) TsmColors.Orange else TsmColors.TextMain,
                        fontSize = 32.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "${s.workoutLap} ${ui.lap} · ${Reports.duration(ui.lapS * 1000L)}",
                        color = TsmColors.TextDim, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (ui.phase == WorkoutPhase.RUNNING) {
                            GhostButton(s.workoutPause, Icons.Filled.Pause, { c.workout.pause() }, Modifier.weight(1f))
                            BigButton(s.workoutLap, Icons.Filled.Flag, { c.workout.lap() }, Modifier.weight(1f))
                        } else {
                            BigButton(s.workoutResume, Icons.Filled.PlayArrow, { c.workout.resume() }, Modifier.weight(1f))
                        }
                    }
                    BigButton(s.workoutEnd, Icons.Filled.Stop, { c.workout.end() }, Modifier.fillMaxWidth(),
                        color = TsmColors.Danger, onColor = TsmColors.TextMain)
                }
                WorkoutPhase.COOLDOWN -> {
                    Text(s.workoutRecovering(ui.cooldownLeft), color = TsmColors.Ball, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    Text(s.workoutRecoveringHint, color = TsmColors.TextDim, fontSize = 13.sp)
                    GhostButton(s.workoutSaveNow, Icons.Filled.Save, { c.workout.finish() }, Modifier.fillMaxWidth())
                }
            }
        }
        // La fascia si sceglie qui, senza passare dalla pagina 2.
        if (ui.phase == WorkoutPhase.IDLE) WorkoutStrapCard(c)
        // Racchetta dell'allenamento: l'allenamento ne tiene una copia con corde e tensione.
        if (ui.phase == WorkoutPhase.IDLE && rackets.isNotEmpty()) {
            SectionCard(s.racketField, Icons.Filled.SportsTennis) {
                RacketPicker(c, workoutRacket) { c.setWorkoutRacket(it) }
            }
        }
        if (ui.phase == WorkoutPhase.IDLE) {
            SectionCard(s.workoutHistory, Icons.Filled.TableChart) {
                if (list.isEmpty()) Text(s.workoutNone, color = TsmColors.TextDim)
                for (w in list) {
                    val sum = com.tennis.scoremanager.data.HeartStats.summary(w.heart)
                    Surface(onClick = { c.workoutDetail.value = w }, shape = RoundedCornerShape(12.dp), color = TsmColors.SurfaceHigh,
                        modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Text("${Reports.date(w.startedAt, o.lang)} ${Reports.time(w.startedAt, o.lang)}", color = TsmColors.TextMain,
                                fontWeight = FontWeight.SemiBold)
                            Text(
                                listOfNotNull(
                                    Reports.duration(w.durationS * 1000L),
                                    sum?.let { "${s.workoutAvgShort} ${it.avg} bpm" },
                                    sum?.let { "${s.workoutMaxShort} ${it.max}" },
                                ).joinToString(" · "),
                                color = TsmColors.TextDim, fontSize = 12.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Riepilogo di un allenamento: numeri, grafico, zone, giri; condivisione CSV/PDF ed eliminazione. */
@Composable
private fun WorkoutDetail(c: MatchController, w: WorkoutRecord) {
    val s = LocalStrings.current
    val context = LocalContext.current
    val o by c.options.collectAsState()
    var confirmDelete by remember { mutableStateOf(false) }
    ScreenScaffold(
        title = s.workoutTitle,
        subtitle = "${Reports.date(w.startedAt, o.lang)} ${Reports.time(w.startedAt, o.lang)}",
        icon = Icons.Filled.MonitorHeart,
        bottomBar = { GhostButton(s.back, Icons.AutoMirrored.Filled.ArrowBack, { c.back() }, Modifier.weight(1f)) },
    ) {
        StatCard(s.heartStat) {
            for ((k, v) in WorkoutReport.summaryLines(w, s)) {
                Row(Modifier.fillMaxWidth()) {
                    Text(k, color = TsmColors.TextMain, modifier = Modifier.weight(1f))
                    Text(v, color = TsmColors.TextMain, fontWeight = FontWeight.Bold)
                }
            }
            if (WorkoutStats.rmssd(w.rr) != null) Text(s.workoutHrvHint, color = TsmColors.TextDim, fontSize = 12.sp)
            Text(s.workoutRecoveryHint, color = TsmColors.TextDim, fontSize = 12.sp)
        }
        if (w.heart.size > 1) StatCard(s.workoutChart) { HeartChart(mapOf(Side.P1 to w.heart)) }
        StatCard(s.heartZones) { HeartZones(mapOf(Side.P1 to w.heart)) { w.strapName ?: s.workoutTitle } }
        val laps = WorkoutStats.laps(w)
        if (laps.size > 1) {
            StatCard(s.workoutLaps) {
                for (l in laps) Text(WorkoutReport.lapLine(l, s), color = TsmColors.TextMain, fontSize = 13.sp)
            }
        }
        RacketSessionCard(c, s.racketUsed, w.racket, o.lang, onRacket = { c.setWorkoutRacketOf(it) }, onFeel = { c.setWorkoutFeel(it) })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton(s.workoutShareCsv, Icons.Filled.TableChart, {
                c.workoutShareIntent(w, pdf = false)?.let { context.startActivity(it) }
            }, Modifier.weight(1f))
            GhostButton(s.workoutSharePdf, Icons.Filled.PictureAsPdf, {
                c.workoutShareIntent(w, pdf = true)?.let { context.startActivity(it) }
            }, Modifier.weight(1f))
        }
        GhostButton(s.delete, Icons.Filled.Delete, { confirmDelete = true }, Modifier.fillMaxWidth())
    }
    if (confirmDelete) {
        ConfirmDialog(
            Icons.Filled.Delete, s.workoutDeleteTitle, s.workoutDeleteText, s.delete,
            onConfirm = {
                confirmDelete = false
                c.deleteWorkout(w.id)
            },
            onDismiss = { confirmDelete = false },
            danger = true,
        )
    }
}
