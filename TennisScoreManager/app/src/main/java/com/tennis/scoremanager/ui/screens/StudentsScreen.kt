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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.SportsTennis
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tennis.scoremanager.HomeTab
import com.tennis.scoremanager.MatchController
import com.tennis.scoremanager.data.Hand
import com.tennis.scoremanager.data.Reports
import com.tennis.scoremanager.data.ServePose
import com.tennis.scoremanager.data.ServePoseResult
import com.tennis.scoremanager.data.ServeSession
import com.tennis.scoremanager.data.Student
import com.tennis.scoremanager.data.Students
import com.tennis.scoremanager.data.TrainedStroke
import com.tennis.scoremanager.ui.BigButton
import com.tennis.scoremanager.ui.EditablePlayerAvatar
import com.tennis.scoremanager.ui.GhostButton
import com.tennis.scoremanager.ui.LocalStrings
import com.tennis.scoremanager.ui.PlayerAvatar
import com.tennis.scoremanager.ui.ScreenScaffold
import com.tennis.scoremanager.ui.SectionCard
import com.tennis.scoremanager.ui.StrokeTrend
import com.tennis.scoremanager.ui.StudentEditDialog
import com.tennis.scoremanager.ui.TsmColors
import com.tennis.scoremanager.ui.strokeLabel
import com.tennis.scoremanager.video.ServeClipAnalysis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/** Allievi dell'allenatore: chi sono, quante sessioni, l'ultima volta; da qui la scheda di ciascuno. */
@Composable
fun StudentsScreen(c: MatchController) {
    val s = LocalStrings.current
    BackHandler { c.back() }
    val students by c.students.collectAsState()
    val sessions by c.serveSessions.collectAsState()
    LaunchedEffect(Unit) { c.refreshServeSessions() }
    var create by remember { mutableStateOf(false) }
    val lang = c.options.value.lang
    ScreenScaffold(
        title = s.studentsTitle,
        subtitle = s.studentsHint,
        icon = Icons.Filled.Groups,
        bottomBar = {
            GhostButton(s.back, Icons.AutoMirrored.Filled.ArrowBack, { c.back() }, Modifier.weight(1f))
            BigButton(s.studentNew, Icons.Filled.PersonAdd, { create = true }, Modifier.weight(1f))
        },
    ) {
        if (students.isEmpty()) Text(s.studentNone, color = TsmColors.TextDim)
        for (st in students) {
            val mine = Students.sessionsOf(sessions, st.id)
            Row(
                Modifier.fillMaxWidth().clickable { c.openStudent(st.id) }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlayerAvatar(c, st.name, TsmColors.Ball, 48.dp)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(st.name, color = TsmColors.TextMain, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    val last = mine.maxOfOrNull { it.startedAt }
                    Text(
                        s.studentSessions(mine.size) + (last?.let { " · " + s.studentLast(Reports.date(it, lang)) } ?: ""),
                        color = TsmColors.TextDim, fontSize = 13.sp,
                    )
                    st.racket?.let { c.racketById(it) }?.label?.takeIf { it.isNotBlank() }?.let { Text(it, color = TsmColors.TextDim, fontSize = 13.sp) }
                }
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = TsmColors.TextDim)
            }
        }
    }
    if (create) StudentEditDialog(c, null) { saved -> create = false; saved?.let { c.openStudent(it.id) } }
}

/** Una sessione analizzata: le medie delle misure dei colpi affidabili. */
private sealed interface Means {
    data class Serve(val m: ServePose.Mean) : Means
    data class Ground(val m: ServePose.GroundMean) : Means
}

private val json = Json { ignoreUnknownKeys = true }

/** Le medie dell'analisi di una sessione (null se non è analizzata o nessun colpo è affidabile). */
private fun means(c: MatchController, sess: ServeSession): Means? {
    val dir = c.storage.serveClipsDir(sess)
    val results = sess.serves.mapNotNull { a ->
        val f = a.clip?.let { File(File(dir, it), ServeClipAnalysis.RESULT) }?.takeIf { it.exists() } ?: return@mapNotNull null
        runCatching { json.decodeFromString(ServePoseResult.serializer(), f.readText()) }.getOrNull()
    }
    return if (sess.stroke == TrainedStroke.SERVE) ServePose.mean(results.mapNotNull { it.metrics })?.let { Means.Serve(it) }
    else ServePose.groundMean(results.mapNotNull { it.ground })?.let { Means.Ground(it) }
}

/**
 * Scheda dell'allievo: colpi (percentuale dentro nel tempo), movimento (misure dell'ultima sessione analizzata contro
 * quella prima), partite dallo storico col suo nome, note dell'allenatore.
 */
@Composable
fun StudentScreen(c: MatchController) {
    val s = LocalStrings.current
    BackHandler { c.back() }
    val id by c.openStudentId.collectAsState()
    val students by c.students.collectAsState()
    val st = students.firstOrNull { it.id == id } ?: run {
        LaunchedEffect(Unit) { c.back() }
        return
    }
    val sessions by c.serveSessions.collectAsState()
    LaunchedEffect(Unit) { c.refreshServeSessions() }
    val mine = Students.sessionsOf(sessions, st.id)
    val lang = c.options.value.lang
    var edit by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf(false) }
    var purge by remember { mutableStateOf(false) }
    ScreenScaffold(
        title = st.name,
        subtitle = listOfNotNull(st.hand?.let { if (it == Hand.RIGHT) s.handRight else s.handLeft }, s.studentSessions(mine.size)).joinToString(" · "),
        icon = Icons.Filled.Groups,
        action = { TextButton(onClick = { edit = true }) { Text(s.studentEdit, color = TsmColors.Ball) } },
        bottomBar = {
            GhostButton(s.back, Icons.AutoMirrored.Filled.ArrowBack, { c.back() }, Modifier.weight(1f))
            // Si sceglie lui in Allenati e si torna lì, pronti per Inizia.
            BigButton(s.studentTrainNow, Icons.Filled.Videocam, { c.setTrainStudent(st.id); c.home(HomeTab.TRAIN) }, Modifier.weight(1f))
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EditablePlayerAvatar(c, st.name, TsmColors.Ball, 72.dp)
            Text(s.studentPhotoHint, color = TsmColors.TextDim, fontSize = 13.sp, modifier = Modifier.padding(start = 12.dp))
        }
        // La racchetta con l'incordatura montata adesso; toccandola si apre Le mie racchette.
        val rackets by c.rackets.collectAsState()
        val racket = st.racket?.let { r -> rackets.firstOrNull { it.id == r } }
        Row(
            Modifier.fillMaxWidth().clickable { if (racket != null) c.openRackets() else edit = true }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.SportsTennis, null, tint = TsmColors.Ball)
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(s.studentRacket, color = TsmColors.TextDim, fontSize = 12.sp)
                Text(racket?.label?.ifBlank { null } ?: s.studentRacketPick, color = TsmColors.TextMain, fontWeight = FontWeight.Bold)
                racket?.current?.let { g ->
                    val strings = listOf(g.brand, g.model).filter { it.isNotBlank() }.joinToString(" ")
                    val kg = listOfNotNull(g.mainsKg, g.crossesKg).distinct().joinToString("/") { "%.1f".format(it) }
                    Text(listOf(strings, kg.takeIf { it.isNotEmpty() }?.let { "$it kg" }, Reports.date(g.date, lang)).filter { !it.isNullOrBlank() }.joinToString(" · "),
                        color = TsmColors.TextDim, fontSize = 13.sp)
                }
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = TsmColors.TextDim)
        }

        // Colpi: per ciascuno sessioni, colpi e la linea dei colpi dentro; toccando, le sue sessioni.
        SectionCard(s.studentStrokes, Icons.Filled.SportsTennis) {
            val lines = Students.strokeLines(mine)
            if (lines.isEmpty()) Text(s.studentStrokesNone, color = TsmColors.TextDim, fontSize = 13.sp)
            for (l in lines) {
                Column(Modifier.fillMaxWidth().clickable { c.setTrainStudent(st.id); c.openStrokeSessions(l.stroke) }.padding(vertical = 6.dp)) {
                    Text(
                        "${strokeLabel(s, l.stroke)} · ${s.studentSessions(l.sessions)} · ${s.studentShots(l.shots)}",
                        color = TsmColors.TextMain, fontWeight = FontWeight.Bold,
                    )
                    Text(s.studentLast(Reports.date(l.lastAt, lang)) + (l.lastPct?.let { " · $it%" } ?: ""), color = TsmColors.TextDim, fontSize = 13.sp)
                    StrokeTrend(mine.filter { it.stroke == l.stroke })
                }
            }
        }

        // Movimento: per ogni colpo l'ultima sessione analizzata e quella prima, misura per misura.
        val changes by produceState(emptyList<Pair<TrainedStroke, List<Students.Change>>>(), mine) {
            value = withContext(Dispatchers.IO) {
                TrainedStroke.entries.mapNotNull { t ->
                    val analysed = mine.filter { it.stroke == t }.sortedByDescending { it.startedAt }.mapNotNull { means(c, it) }.take(2)
                    val now = analysed.getOrNull(0) ?: return@mapNotNull null
                    t to changesOf(s, now, analysed.getOrNull(1))
                }
            }
        }
        SectionCard(s.studentMovement, Icons.Filled.AccessibilityNew) {
            if (changes.isEmpty()) Text(s.studentMovementNone, color = TsmColors.TextDim, fontSize = 13.sp)
            for ((t, rows) in changes) {
                Text(strokeLabel(s, t), color = TsmColors.TextMain, fontWeight = FontWeight.Bold)
                for (r in rows) {
                    Row(Modifier.fillMaxWidth()) {
                        Text(r.label, color = TsmColors.TextDim, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        val fmt = { v: Float? -> v?.let { if (r.unit == "°") "%.0f°".format(it) else if (r.unit == "ms") "%.0f ms".format(it) else "%.2f".format(it) } ?: "—" }
                        Text(
                            if (r.before != null) "${fmt(r.before)} → ${fmt(r.now)}" else fmt(r.now),
                            color = TsmColors.TextMain, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        // Partite dallo storico con lo stesso nome, e il rating.
        SectionCard(s.studentMatches, Icons.Filled.EmojiEvents) {
            val lines = remember(st.name) { c.studentMatches(st.name) }
            if (lines.isEmpty()) {
                Text(s.studentMatchesNone, color = TsmColors.TextDim, fontSize = 13.sp)
            } else {
                Text(s.studentWonLost(lines.count { it.won }, lines.count { !it.won }), color = TsmColors.TextMain, fontWeight = FontWeight.Bold)
                remember(st.name) { c.studentRating(st.name) }?.let { Text("${s.ratingTitle}: ${it.rating}", color = TsmColors.Ball, fontWeight = FontWeight.Bold) }
                for (m in lines.takeLast(5).reversed()) {
                    Row(Modifier.fillMaxWidth()) {
                        Text(Reports.date(m.rec.endedAt ?: m.rec.updatedAt, lang), color = TsmColors.TextDim, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Text(
                            (if (m.won) s.studentWon else s.studentLost) + " · ${m.pointsWonPct}%",
                            color = if (m.won) TsmColors.Ok else TsmColors.Danger, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        // Note dell'allenatore: si salvano da sole mentre si scrive.
        SectionCard(s.studentNotes, Icons.Filled.EditNote) {
            var note by remember(st.id) { mutableStateOf(st.note) }
            LaunchedEffect(note) {
                if (note == st.note) return@LaunchedEffect
                delay(800)
                c.saveStudent(st.copy(note = note))
            }
            OutlinedTextField(note, { note = it.take(4000) }, Modifier.fillMaxWidth(), minLines = 4, placeholder = { Text(s.studentNotesHint) })
        }
        GhostButton(s.studentDelete, Icons.Filled.PersonRemove, { delete = true }, Modifier.fillMaxWidth())
        // Tutto quello che il telefono ha di suo, per chi smette o chiede di cancellare i propri dati.
        GhostButton(s.studentPurge, Icons.Filled.DeleteForever, { purge = true }, Modifier.fillMaxWidth())
    }
    if (purge) {
        AlertDialog(
            onDismissRequest = { purge = false },
            title = { Text(s.studentPurge) },
            text = { Text(s.studentPurgeAsk(st.name, mine.size)) },
            confirmButton = {
                TextButton(onClick = { purge = false; c.purgeStudent(st.id); c.back() }) {
                    Text(s.studentPurgeConfirm, color = TsmColors.Danger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { purge = false }) { Text(s.back) } },
        )
    }
    if (edit) StudentEditDialog(c, st) { edit = false }
    if (delete) {
        AlertDialog(
            onDismissRequest = { delete = false },
            text = { Text(s.studentDeleteAsk(st.name)) },
            confirmButton = { TextButton(onClick = { delete = false; c.deleteStudent(st.id); c.back() }) { Text(s.studentDelete, color = TsmColors.Danger) } },
            dismissButton = { TextButton(onClick = { delete = false }) { Text(s.back) } },
        )
    }
}

/** Le righe del confronto: per il servizio e per gli altri colpi le misure dell'analisi. */
private fun changesOf(s: com.tennis.scoremanager.ui.Strings, now: Means, before: Means?): List<Students.Change> = when (now) {
    is Means.Serve -> {
        val b = (before as? Means.Serve)?.m
        val m = now.m
        listOf(
            Students.Change(s.poseKneeMetric, b?.kneeMinDeg, m.kneeMinDeg, "°", 0),
            Students.Change(s.poseElbowMetric, b?.elbowContactDeg, m.elbowContactDeg, "°", 0),
            Students.Change(s.poseHeightMetric, b?.contactHeight, m.contactHeight, "", 0),
            Students.Change(s.poseTossMetric, b?.tossArmDeg, m.tossArmDeg, "°", 0),
            Students.Change(s.poseTimingMetric, b?.trophyToContactMs?.toFloat(), m.trophyToContactMs.toFloat(), "ms", 0),
        )
    }
    is Means.Ground -> {
        val b = (before as? Means.Ground)?.m
        val m = now.m
        listOf(
            Students.Change(s.poseInFrontMetric, b?.contactInFront, m.contactInFront, "", 0),
            Students.Change(s.poseElbowMetric, b?.elbowContactDeg, m.elbowContactDeg, "°", 0),
            Students.Change(s.poseKneeMetric, b?.kneeMinDeg, m.kneeMinDeg, "°", 0),
            Students.Change(s.poseBackswingMetric, b?.backswing, m.backswing, "", 0),
            Students.Change(s.poseFinishMetric, b?.finishHeight, m.finishHeight, "", 0),
            Students.Change(s.poseTimingGround, b?.prepToContactMs?.toFloat(), m.prepToContactMs.toFloat(), "ms", 0),
        )
    }
}
