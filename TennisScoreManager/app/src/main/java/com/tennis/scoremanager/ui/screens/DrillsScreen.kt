// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Remove
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tennis.scoremanager.MatchController
import com.tennis.scoremanager.data.Drill
import com.tennis.scoremanager.data.DrillStep
import com.tennis.scoremanager.data.ServeKind
import com.tennis.scoremanager.data.ServeSide
import com.tennis.scoremanager.data.ServeTarget
import com.tennis.scoremanager.data.ShotDirection
import com.tennis.scoremanager.data.TrainedStroke
import com.tennis.scoremanager.data.Wing
import com.tennis.scoremanager.ui.BigButton
import com.tennis.scoremanager.ui.GhostButton
import com.tennis.scoremanager.ui.LocalStrings
import com.tennis.scoremanager.ui.ScreenScaffold
import com.tennis.scoremanager.ui.SectionCard
import com.tennis.scoremanager.ui.SegOption
import com.tennis.scoremanager.ui.Segmented
import com.tennis.scoremanager.ui.TsmColors
import com.tennis.scoremanager.ui.drillSummary
import com.tennis.scoremanager.ui.directionName
import com.tennis.scoremanager.ui.serveKindName
import com.tennis.scoremanager.ui.serveSideName
import com.tennis.scoremanager.ui.serveTargetName
import com.tennis.scoremanager.ui.strokeLabel
import com.tennis.scoremanager.ui.wingName

/** Gli esercizi guidati, per colpo: si creano, si cambiano, si tolgono; si scelgono sotto Inizia. */
@Composable
fun DrillsScreen(c: MatchController) {
    val s = LocalStrings.current
    BackHandler { c.back() }
    val drills by c.drills.collectAsState()
    var edit by remember { mutableStateOf<Drill?>(null) }
    var creating by remember { mutableStateOf(false) }
    ScreenScaffold(
        onHelp = { c.openHelp("7.10") },
        title = s.drillsTitle,
        subtitle = s.drillsHint,
        icon = Icons.Filled.FormatListNumbered,
        bottomBar = {
            GhostButton(s.back, Icons.AutoMirrored.Filled.ArrowBack, { c.back() }, Modifier.weight(1f))
            BigButton(s.drillNew, Icons.Filled.Add, { creating = true }, Modifier.weight(1f))
        },
    ) {
        if (drills.isEmpty()) Text(s.drillsNone, color = TsmColors.TextDim)
        for (t in TrainedStroke.entries) {
            val mine = drills.filter { it.stroke == t }
            if (mine.isEmpty()) continue
            SectionCard(strokeLabel(s, t), Icons.Filled.FormatListNumbered) {
                for (d in mine) Row(Modifier.fillMaxWidth().clickable { edit = d }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(d.name, color = TsmColors.TextMain, fontWeight = FontWeight.Bold)
                        Text(drillSummary(s, d), color = TsmColors.TextDim, fontSize = 13.sp)
                    }
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = TsmColors.TextDim)
                }
            }
        }
    }
    if (creating) DrillEditDialog(c, null) { creating = false }
    edit?.let { d -> DrillEditDialog(c, d) { edit = null } }
}

/** Nome, colpo e passi: per ogni passo quanti colpi e, secondo il colpo, lato, prima/seconda, bersaglio o direzione. */
@Composable
private fun DrillEditDialog(c: MatchController, drill: Drill?, onDone: () -> Unit) {
    val s = LocalStrings.current
    var name by remember { mutableStateOf(drill?.name.orEmpty()) }
    var stroke by remember { mutableStateOf(drill?.stroke ?: c.trainStroke.value) }
    var steps by remember { mutableStateOf(drill?.steps ?: listOf(DrillStep(20))) }
    var ask by remember { mutableStateOf(false) }
    fun set(i: Int, st: DrillStep) { steps = steps.toMutableList().also { it[i] = st } }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(if (drill == null) s.drillNew else s.studentEdit) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it.take(60) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(s.drillName) })
                val strokes = TrainedStroke.entries
                Segmented(strokes.map { SegOption(strokeLabel(s, it)) }, strokes.indexOf(stroke), { stroke = strokes[it] }, columns = 2)
                for ((i, st) in steps.withIndex()) {
                    SectionCard(s.drillStep(i + 1), Icons.Filled.FormatListNumbered) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { set(i, st.copy(count = (st.count - 5).coerceAtLeast(1))) }) { Icon(Icons.Filled.Remove, null) }
                            Text(s.drillShots(st.count), color = TsmColors.TextMain, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            IconButton(onClick = { set(i, st.copy(count = (st.count + 5).coerceAtMost(200))) }) { Icon(Icons.Filled.Add, null) }
                            if (steps.size > 1) IconButton(onClick = { steps = steps.filterIndexed { k, _ -> k != i } }) { Icon(Icons.Filled.Delete, null, tint = TsmColors.Danger) }
                        }
                        @Composable fun <T> pick(values: List<T?>, label: (T) -> String, cur: T?, onPick: (T?) -> Unit) =
                            Segmented(values.map { v -> SegOption(v?.let(label) ?: "—") }, values.indexOf(cur), { onPick(values[it]) })
                        if (stroke == TrainedStroke.SERVE) {
                            pick(listOf(null, ServeSide.DEUCE, ServeSide.AD), { serveSideName(s, it) }, st.side) { set(i, st.copy(side = it)) }
                            pick(listOf(null, ServeKind.FIRST, ServeKind.SECOND), { serveKindName(s, it) }, st.kind) { set(i, st.copy(kind = it)) }
                            pick(listOf(null, ServeTarget.T, ServeTarget.BODY, ServeTarget.WIDE), { serveTargetName(s, it) }, st.target) { set(i, st.copy(target = it)) }
                        } else {
                            if (stroke == TrainedStroke.VOLLEY) pick(listOf(null, Wing.FOREHAND, Wing.BACKHAND), { wingName(s, it) }, st.wing) { set(i, st.copy(wing = it)) }
                            pick(listOf(null, ShotDirection.CROSS, ShotDirection.MIDDLE, ShotDirection.LINE), { directionName(s, it) }, st.direction) { set(i, st.copy(direction = it)) }
                        }
                    }
                }
                GhostButton(s.drillAddStep, Icons.Filled.Add, { steps = steps + steps.last().copy() }, Modifier.fillMaxWidth())
                if (drill != null) GhostButton(s.drillDelete, Icons.Filled.Delete, { ask = true }, Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                // Cambiando colpo, le impostazioni che quel colpo non ha si tolgono.
                val clean = steps.map { st ->
                    if (stroke == TrainedStroke.SERVE) st.copy(direction = null, wing = null)
                    else st.copy(side = null, kind = null, target = null, wing = st.wing.takeIf { stroke == TrainedStroke.VOLLEY })
                }
                c.saveDrill((drill ?: Drill("d${System.currentTimeMillis()}", "", stroke, emptyList(), System.currentTimeMillis())).copy(name = name.trim(), stroke = stroke, steps = clean))
                onDone()
            }) { Text(s.ok) }
        },
        dismissButton = { TextButton(onClick = onDone) { Text(s.back) } },
    )
    if (ask && drill != null) {
        AlertDialog(
            onDismissRequest = { ask = false },
            text = { Text(s.drillDeleteAsk(drill.name)) },
            confirmButton = { TextButton(onClick = { ask = false; c.deleteDrill(drill.id); onDone() }) { Text(s.drillDelete, color = TsmColors.Danger) } },
            dismissButton = { TextButton(onClick = { ask = false }) { Text(s.back) } },
        )
    }
}
