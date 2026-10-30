// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.sp
import com.tennis.scoremanager.MatchController
import com.tennis.scoremanager.data.ServeSession
import com.tennis.scoremanager.ui.GhostButton
import com.tennis.scoremanager.ui.LocalStrings
import com.tennis.scoremanager.ui.ScreenScaffold
import com.tennis.scoremanager.ui.SectionCard
import com.tennis.scoremanager.ui.ServeSessionDialog
import com.tennis.scoremanager.ui.SessionRow
import com.tennis.scoremanager.ui.StrokeTrend
import com.tennis.scoremanager.ui.TsmColors
import com.tennis.scoremanager.ui.strokeLabel
import com.tennis.scoremanager.video.ServeClipAnalysis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

/** Quello che si sa di una sessione guardando la sua cartella: spazio dei video, se ce ne sono, se è analizzata. */
private data class Disk(val bytes: Long, val videos: Boolean, val analysed: Boolean)

/**
 * Tutte le sessioni del colpo scelto in Allenati, per mese: cartella in Documenti, spazio occupato, andamento, e la
 * selezione (tenendo premuto) per cancellare le sessioni o solo i loro video.
 */
@Composable
fun StrokeSessionsScreen(c: MatchController) {
    val s = LocalStrings.current
    BackHandler { c.back() }
    val stroke by c.trainStroke.collectAsState()
    val all by c.serveSessions.collectAsState()
    LaunchedEffect(Unit) { c.refreshServeSessions() }
    val trainee by c.trainStudent.collectAsState()
    val list = all.filter { it.stroke == stroke && it.student == trainee }
    val who = c.studentById(trainee)?.name
    val lang = c.options.value.lang
    val disk by produceState(emptyMap<String, Disk>(), list) {
        value = withContext(Dispatchers.IO) {
            list.associate { sess ->
                val dir = c.storage.serveClipsDir(sess)
                val bytes = c.storage.serveVideoBytes(sess)
                val analysed = sess.serves.any { a -> a.clip?.let { File(File(dir, it), ServeClipAnalysis.RESULT).exists() } == true }
                sess.id to Disk(bytes, bytes > 0, analysed)
            }
        }
    }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    val selecting = selected.isNotEmpty()
    var open by remember { mutableStateOf<ServeSession?>(null) }
    var ask by remember { mutableStateOf<Boolean?>(null) }  // true = solo i video
    val name = strokeLabel(s, stroke)
    fun mb(b: Long) = String.format(lang.locale, "%.0f MB", b / 1_048_576.0)
    ScreenScaffold(
        title = s.strokeSessionsTitle(name + (who?.let { " · $it" } ?: "")),
        // Da Android 11 la cartella è Documenti; prima quella dell'app (come per i file salvati).
        subtitle = s.strokeSessionsWhere(
            (if (android.os.Build.VERSION.SDK_INT >= 30) "${s.documentsFolder}/Tennis Score Manager/${s.strokeFolderName}/$name"
            else File(c.storage.documentsRoot, "${s.strokeFolderName}/$name").absolutePath) +
                (who?.let { "/" + com.tennis.scoremanager.data.Students.folderName(it) } ?: ""),
        ),
        icon = Icons.Filled.FolderOpen,
        action = {
            if (list.isNotEmpty()) {
                IconButton(onClick = { selected = if (selecting) emptySet() else list.map { it.id }.toSet() }) {
                    Icon(Icons.Filled.Checklist, s.strokeSelectAll, tint = TsmColors.Ball)
                }
            }
        },
        bottomBar = {
            if (selecting) {
                GhostButton(s.strokeVideosN(selected.size), Icons.Filled.VideocamOff, { ask = true }, Modifier.weight(1f))
                GhostButton(s.strokeDeleteN(selected.size), Icons.Filled.Delete, { ask = false }, Modifier.weight(1f))
            } else {
                GhostButton(s.back, Icons.AutoMirrored.Filled.ArrowBack, { c.back() }, Modifier.weight(1f))
            }
        },
    ) {
        if (list.isEmpty()) {
            Text(s.strokeNone, color = TsmColors.TextDim)
            return@ScreenScaffold
        }
        SectionCard(name, Icons.Filled.FolderOpen) {
            Text(s.strokeSpace(mb(disk.values.sumOf { it.bytes }), list.size), color = TsmColors.TextMain)
            StrokeTrend(list)
            Text(s.strokeSelectHint, color = TsmColors.TextDim, fontSize = 12.sp)
        }
        // Per mese, dalla più recente.
        val month = SimpleDateFormat("LLLL yyyy", lang.locale)
        for ((label, group) in list.sortedByDescending { it.startedAt }.groupBy { month.format(Date(it.startedAt)).replaceFirstChar { ch -> ch.titlecase(lang.locale) } }) {
            SectionCard(label, Icons.Filled.FolderOpen) {
                for (sess in group) {
                    val d = disk[sess.id]
                    val extra = listOfNotNull(
                        d?.let { if (it.videos) mb(it.bytes) else s.strokeNoVideos },
                        s.strokeAnalysed.takeIf { d?.analysed == true },
                    ).joinToString(" · ").ifEmpty { null }
                    val on = sess.id in selected
                    fun toggle() { selected = if (on) selected - sess.id else selected + sess.id }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        if (selecting) {
                            Checkbox(on, { toggle() }, colors = CheckboxDefaults.colors(checkedColor = TsmColors.Ball, checkmarkColor = TsmColors.OnBall))
                        }
                        // Tenendo premuto si seleziona; con la selezione attiva anche un tocco seleziona.
                        SessionRow(c, sess, extra, Modifier.weight(1f), onLongClick = { toggle() }) { if (selecting) toggle() else open = sess }
                    }
                }
            }
        }
    }
    ask?.let { videosOnly ->
        val chosen = list.filter { it.id in selected }
        AlertDialog(
            onDismissRequest = { ask = null },
            text = { Text(if (videosOnly) s.strokeVideosAsk(chosen.size) else s.strokeDeleteAsk(chosen.size)) },
            confirmButton = {
                TextButton(onClick = {
                    c.deleteServeSessions(chosen, videosOnly)
                    selected = emptySet()
                    ask = null
                }) { Text(if (videosOnly) s.strokeVideosN(chosen.size) else s.strokeDeleteN(chosen.size), color = TsmColors.Danger, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { ask = null }) { Text(s.back) } },
        )
    }
    open?.let { sess -> ServeSessionDialog(c, sess) { open = null; c.refreshServeSessions() } }
}
