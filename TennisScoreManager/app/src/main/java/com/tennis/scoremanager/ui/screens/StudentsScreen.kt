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
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Person
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.School
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
import com.tennis.scoremanager.data.PlayerProfile
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
import kotlinx.coroutines.launch
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
        onHelp = { c.openHelp("7.6") },
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

/**
 * Scheda dell'allievo: colpi (percentuale dentro nel tempo), movimento (misure dell'ultima sessione analizzata contro
 * quella prima), partite dallo storico col suo nome, note dell'allenatore.
 */
@Composable
fun StudentScreen(c: MatchController) {
    val s = LocalStrings.current
    BackHandler { c.back() }
    val id by c.openStudentId.collectAsState()
    // La scheda vale per ogni giocatore del database: gli allievi hanno in più allenamenti e "Allena ora".
    val everyone by c.players.collectAsState()
    val history by c.history.collectAsState()
    val st = everyone.firstOrNull { it.id == id } ?: run {
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
    var merge by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf(false) }
    var split by remember { mutableStateOf(false) }
    ScreenScaffold(
        onHelp = { c.openHelp("7.6") },
        title = st.name,
        subtitle = listOfNotNull(
            st.fullName.takeIf { it.isNotBlank() && it != st.name }, st.hand?.let { if (it == Hand.RIGHT) s.handRight else s.handLeft }, s.studentSessions(mine.size),
        ).joinToString(" · "),
        icon = Icons.Filled.Groups,
        action = { TextButton(onClick = { edit = true }) { Text(s.studentEdit, color = TsmColors.Ball) } },
        bottomBar = {
            GhostButton(s.back, Icons.AutoMirrored.Filled.ArrowBack, { c.back() }, Modifier.weight(1f))
            // Si sceglie lui in Allenati e si torna lì, pronti per Inizia.
            if (st.student) BigButton(s.studentTrainNow, Icons.Filled.Videocam, { c.setTrainStudent(st.id); c.home(if (c.activeRole.value == com.tennis.scoremanager.Role.COACH) HomeTab.STUDENTS else HomeTab.TRAIN) }, Modifier.weight(1f))
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EditablePlayerAvatar(c, st.name, TsmColors.Ball, 72.dp)
            Column(Modifier.padding(start = 12.dp)) {
                // Età, fisico, circolo, rovescio: quello che c'è nella scheda.
                val info = listOfNotNull(
                    com.tennis.scoremanager.data.Members.age(st.birth, System.currentTimeMillis())?.let { s.playerAge(it) },
                    listOfNotNull(st.heightCm?.let { "$it cm" }, st.weightKg?.let { "$it kg" }).joinToString(" · ").ifBlank { null },
                    st.club.ifBlank { null },
                    st.backhand?.let { s.playerBackhand + " " + if (it == com.tennis.scoremanager.data.Backhand.ONE) s.backhandOne else s.backhandTwo },
                )
                for (l in info) Text(l, color = TsmColors.TextMain, fontSize = 14.sp)
                Text(s.studentPhotoHint, color = TsmColors.TextDim, fontSize = 13.sp)
            }
        }
        // Sei tu? Il tuo giocatore è quello di Progressi, del rating e di Altro › Tu.
        if (c.me()?.id == st.id) Text(s.meIsYou, color = TsmColors.Ball, fontWeight = FontWeight.Bold)
        else GhostButton(s.meThisIsMe, Icons.Filled.Person, { c.setMe(st) }, Modifier.fillMaxWidth())
        // Nato dallo storico con un nome solo: nome e cognome da aggiungere.
        if (!st.complete) GhostButton(s.playerComplete, Icons.Filled.EditNote, { edit = true }, Modifier.fillMaxWidth())
        // Socio del circolo: tessere, certificato e consensi (o il tasto per iscriverlo, col ruolo Circolo).
        val features by c.clubFeatures.collectAsState()
        if (com.tennis.scoremanager.ClubFeature.MEMBERS in features) MemberSection(c, st)
        // Con il server del circolo: l'invito per prenotare online.
        PlayerOnlineSection(c, st)
        if (com.tennis.scoremanager.ClubFeature.PAYMENTS in features) ChargesSection(c, st)
        // Le squadre in cui gioca.
        val teamsAll by c.teams.collectAsState()
        val mineT = teamsAll.filter { tm -> tm.roster.any { it == st.id || it in st.aliases } }
        if (mineT.isNotEmpty()) SectionCard(s.tmTitle, Icons.Filled.Groups) {
            for (tm in mineT) Text(tm.name + (tm.championship.ifBlank { null }?.let { " · $it" } ?: ""), color = TsmColors.TextMain, fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth().clickable { c.openTeam(tm.id) }.padding(vertical = 6.dp))
        }
        // I corsi della scuola tennis che frequenta.
        val courses by c.courses.collectAsState()
        val mineC = courses.filter { co -> co.students.any { it == st.id || it in st.aliases } }
        if (mineC.isNotEmpty()) SectionCard(s.scTitle, Icons.Filled.School) {
            for (co in mineC) {
                val (pres, tot) = com.tennis.scoremanager.data.Courses.attendanceOf(co, st.id, System.currentTimeMillis())
                Text(co.name + if (tot > 0) " · " + s.scAttendance(pres, tot) else "", color = TsmColors.TextMain, fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth().clickable { c.openCourse(co.id) }.padding(vertical = 6.dp))
            }
        }
        com.tennis.scoremanager.ui.SwitchRow(Icons.Filled.School, s.playerStudentToggle, s.playerStudentToggleHint, st.student) { on ->
            if (!on && c.trainStudent.value == st.id) c.setTrainStudent(null)
            c.savePlayer(st.copy(student = on))
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
                    val analysed = mine.filter { it.stroke == t }.sortedByDescending { it.startedAt }.mapNotNull { com.tennis.scoremanager.data.StudentAnalysis.means(c.storage, it) }.take(2)
                    val now = analysed.getOrNull(0) ?: return@mapNotNull null
                    t to com.tennis.scoremanager.data.StudentAnalysis.changes(s, now, analysed.getOrNull(1))
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
            val lines = remember(st, history) { c.studentMatches(st) }
            if (lines.isEmpty()) {
                Text(s.studentMatchesNone, color = TsmColors.TextDim, fontSize = 13.sp)
            } else {
                Text(s.studentWonLost(lines.count { it.won }, lines.count { !it.won }), color = TsmColors.TextMain, fontWeight = FontWeight.Bold)
                remember(st, history) { c.studentRating(st) }?.let { Text("${s.ratingTitle}: ${it.rating}", color = TsmColors.Ball, fontWeight = FontWeight.Bold) }
                for (m in lines.takeLast(5).reversed()) {
                    Row(Modifier.fillMaxWidth()) {
                        Text(Reports.date(m.rec.endedAt ?: m.rec.updatedAt, lang), color = TsmColors.TextDim, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Text(
                            (if (m.won) s.studentWon else s.studentLost) + " · ${m.pointsWonPct}%",
                            color = if (m.won) TsmColors.Ok else TsmColors.Danger, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        )
                    }
                }
                // Due persone con lo stesso nome finite insieme: si spuntano le partite dell'altra.
                GhostButton(s.playerSplit, Icons.Filled.CallSplit, { split = true }, Modifier.fillMaxWidth())
            }
        }

        // Note dell'allenatore: si salvano da sole mentre si scrive.
        SectionCard(s.studentNotes, Icons.Filled.EditNote) {
            var note by remember(st.id) { mutableStateOf(st.note) }
            LaunchedEffect(note) {
                if (note == st.note) return@LaunchedEffect
                delay(800)
                c.savePlayer(st.copy(note = note))
            }
            OutlinedTextField(note, { note = it.take(4000) }, Modifier.fillMaxWidth(), minLines = 4, placeholder = { Text(s.studentNotesHint) })
        }
        // Il report da dare all'allievo (PDF o immagine per WhatsApp).
        if (st.student) BigButton(s.reportTitle, Icons.Filled.Share, { report = true }, Modifier.fillMaxWidth())
        GhostButton(s.playerMerge, Icons.Filled.MergeType, { merge = true }, Modifier.fillMaxWidth())
        if (st.student) GhostButton(s.studentDelete, Icons.Filled.PersonRemove, { delete = true }, Modifier.fillMaxWidth())
        // Tutto quello che il telefono ha di suo, per chi smette o chiede di cancellare i propri dati.
        GhostButton(s.studentPurge, Icons.Filled.DeleteForever, { purge = true }, Modifier.fillMaxWidth())
    }
    if (purge) {
        AlertDialog(
            onDismissRequest = { purge = false },
            title = { Text(s.studentPurge) },
            text = { Text(s.studentPurgeAsk(st.name, mine.size)) },
            confirmButton = {
                // Sparito il giocatore, la scheda torna indietro da sola (una volta).
                TextButton(onClick = { purge = false; c.purgeStudent(st.id) }) {
                    Text(s.studentPurgeConfirm, color = TsmColors.Danger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { purge = false }) { Text(s.back) } },
        )
    }
    if (edit) StudentEditDialog(c, st) { edit = false }
    if (merge) MergeDialog(c, st) { merge = false }
    if (report) ReportDialog(c, st) { report = false }
    if (split) SplitDialog(c, st) { split = false }
    if (delete) {
        AlertDialog(
            onDismissRequest = { delete = false },
            text = { Text(s.studentDeleteAsk(st.name)) },
            confirmButton = { TextButton(onClick = { delete = false; c.deleteStudent(st.id); c.back() }) { Text(s.studentDelete, color = TsmColors.Danger) } },
            dismissButton = { TextButton(onClick = { delete = false }) { Text(s.back) } },
        )
    }
}

/** Report per l'allievo: periodo (ultimi 30 giorni o tutto) e formato; si condivide subito. */
@Composable
private fun ReportDialog(c: MatchController, st: PlayerProfile, onDone: () -> Unit) {
    val s = LocalStrings.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var all by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    fun make(pdf: Boolean) {
        busy = true
        scope.launch {
            val f = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { c.studentReportFile(st, if (all) null else 30, pdf) }
            busy = false
            if (f == null) android.widget.Toast.makeText(context, s.reportFailed, android.widget.Toast.LENGTH_LONG).show()
            else { context.startActivity(c.shareFileIntent(f, if (pdf) "application/pdf" else "image/png")); onDone() }
        }
    }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(s.reportTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(s.reportHint, color = TsmColors.TextDim, fontSize = 13.sp)
                com.tennis.scoremanager.ui.Segmented(
                    listOf(com.tennis.scoremanager.ui.SegOption(s.report30), com.tennis.scoremanager.ui.SegOption(s.reportAll)), if (all) 1 else 0, { all = it == 1 },
                )
                if (busy) Text(s.dataBusy, color = TsmColors.TextDim)
                GhostButton(s.reportPdf, Icons.Filled.PictureAsPdf, { if (!busy) make(true) }, Modifier.fillMaxWidth())
                GhostButton(s.reportImage, Icons.Filled.Image, { if (!busy) make(false) }, Modifier.fillMaxWidth())
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDone) { Text(s.back) } },
    )
}

/**
 * Unisci: si sceglie l'altro giocatore (la stessa persona scritta in due modi o creata due volte) e si conferma; le
 * sue partite e sessioni diventano di questo.
 */
@Composable
private fun MergeDialog(c: MatchController, st: PlayerProfile, onDone: () -> Unit) {
    val s = LocalStrings.current
    val everyone by c.players.collectAsState()
    var query by remember { mutableStateOf("") }
    var other by remember { mutableStateOf<PlayerProfile?>(null) }
    val o = other
    if (o != null) {
        AlertDialog(
            onDismissRequest = onDone,
            text = { Text(s.playerMergeAsk(o.name, st.name)) },
            confirmButton = { TextButton(onClick = { c.mergePlayers(st.id, o.id); onDone() }) { Text(s.playerMerge, color = TsmColors.Danger, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = onDone) { Text(s.back) } },
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(s.playerMerge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(s.playerMergeHint, color = TsmColors.TextDim, fontSize = 13.sp)
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(s.playersSearch) })
                val list = (if (query.isBlank()) everyone else com.tennis.scoremanager.data.Players.search(everyone, query)).filter { it.id != st.id }.take(8)
                for (p in list) Row(Modifier.fillMaxWidth().clickable { other = p }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    PlayerAvatar(c, p.name, TsmColors.Ball, 32.dp)
                    Text(" " + p.name + (c.playerHint(p)?.let { " · $it" } ?: ""), color = TsmColors.TextMain, fontSize = 14.sp)
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDone) { Text(s.back) } },
    )
}

/** Separa un omonimo: si spuntano le partite di un'altra persona con lo stesso nome, che diventa un giocatore nuovo. */
@Composable
private fun SplitDialog(c: MatchController, st: PlayerProfile, onDone: () -> Unit) {
    val s = LocalStrings.current
    val lang = c.options.value.lang
    val lines = remember(st) { c.studentMatches(st).reversed() }
    var picked by remember { mutableStateOf(emptySet<String>()) }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(s.playerSplit) },
        text = {
            Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                Text(s.playerSplitHint(st.name), color = TsmColors.TextDim, fontSize = 13.sp)
                for (m in lines) {
                    val on = m.rec.id in picked
                    val opp = com.tennis.scoremanager.data.PlayerStats.names(m.rec.setup, com.tennis.scoremanager.data.Students.sideIn(m.rec, st)?.other ?: com.tennis.scoremanager.model.Side.P2).joinToString(" / ")
                    Row(Modifier.fillMaxWidth().clickable { picked = if (on) picked - m.rec.id else picked + m.rec.id }, verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(on, { picked = if (on) picked - m.rec.id else picked + m.rec.id })
                        Text("${Reports.date(m.rec.endedAt ?: m.rec.updatedAt, lang)} · $opp · " + (if (m.won) s.studentWon else s.studentLost), color = TsmColors.TextMain, fontSize = 13.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = picked.isNotEmpty(), onClick = { c.splitPlayer(st.id, picked); onDone() }) { Text(s.playerSplitDo(picked.size), color = TsmColors.Danger) }
        },
        dismissButton = { TextButton(onClick = onDone) { Text(s.back) } },
    )
}
