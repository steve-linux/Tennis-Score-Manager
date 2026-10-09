// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tennis.scoremanager.MatchController
import com.tennis.scoremanager.data.Reports
import com.tennis.scoremanager.model.Side
import com.tennis.scoremanager.ui.BigButton
import com.tennis.scoremanager.ui.ConfirmDialog
import com.tennis.scoremanager.ui.GhostButton
import com.tennis.scoremanager.ui.LocalStrings
import com.tennis.scoremanager.ui.TsmColors

/** Ultima schermata: tutti i dati della partita, salvataggio, condivisione, nuova partita, uscita. */
@Composable
fun SummaryScreen(c: MatchController) {
    val s = LocalStrings.current
    val context = LocalContext.current
    val activity = LocalActivity.current
    val sm by c.summary.collectAsState()
    val done = sm ?: return
    val rec = done.record
    val state = done.state
    val names = remember(rec.id, s) { c.summaryNames() }
    val w = state.winner ?: Side.P1
    val lang = rec.options.lang
    var showSave by remember { mutableStateOf(false) }
    val kept by c.summaryKept.collectAsState()
    /** Uscita da confermare: true = "Esci", false = "Nuova partita". */
    var leaving by remember { mutableStateOf<Boolean?>(null) }
    fun leave(exit: Boolean) {
        if (exit) activity?.let { c.exitApp(it) } else c.newMatch()
    }
    BackHandler { }

    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(s.summaryTitle, fontSize = 26.sp, lineHeight = 30.sp, fontWeight = FontWeight.Black, color = TsmColors.TextMain)

            // Vincitore
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(TsmColors.player(w).copy(alpha = 0.16f)).padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Filled.EmojiEvents, null, tint = TsmColors.player(w), modifier = Modifier.size(52.dp))
                Text(s.winner.uppercase(), color = TsmColors.TextDim, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Text(names.side(w), color = TsmColors.player(w), fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                Spacer(Modifier.height(6.dp))
                Text(Reports.scoreLine(state, w), color = TsmColors.TextMain, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }

            // Tabellone finale
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(TsmColors.Surface).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (side in Side.entries) {
                    Row(Modifier.fillMaxWidth().height(if (rec.rules.doubles) 56.dp else 44.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(6.dp).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(TsmColors.player(side)))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            for (p in names.players(side)) {
                                Text(
                                    p, color = TsmColors.player(side), fontWeight = if (side == w) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 17.sp,
                                )
                            }
                        }
                        for (set in state.sets) {
                            Box(Modifier.width(44.dp), contentAlignment = Alignment.Center) {
                                Row(verticalAlignment = Alignment.Top) {
                                    Text(
                                        set.shown(side).toString(),
                                        color = if (set.winner == side) TsmColors.TextMain else TsmColors.TextDim,
                                        fontSize = if (set.matchTiebreak) 18.sp else 24.sp,
                                        fontWeight = if (set.winner == side) FontWeight.Black else FontWeight.Normal,
                                    )
                                    if (set.hasTiebreak && !set.matchTiebreak && set.winner != side) {
                                        Text(set.tb(side).toString(), color = TsmColors.TextDim, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                        Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) {
                            if (side == w) Icon(Icons.Filled.EmojiEvents, null, tint = TsmColors.Ball, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            // Dati partita
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(TsmColors.Surface).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                InfoRow(Icons.Filled.Timer, s.duration, Reports.duration(rec.clockMs))
                InfoRow(Icons.Filled.Event, s.date, Reports.date(rec.startedAt, lang).replaceFirstChar { it.uppercase() })
                InfoRow(Icons.Filled.Schedule, s.startTime, Reports.time(rec.startedAt, lang))
                InfoRow(Icons.Filled.Schedule, s.endTime, Reports.time(rec.endedAt, lang))
                if (rec.setup.club.isNotBlank()) InfoRow(Icons.Filled.Business, s.club, rec.setup.club)
                if (rec.setup.court.isNotBlank()) InfoRow(Icons.Filled.Tag, s.court, rec.setup.court)
                InfoRow(Icons.Filled.Place, s.place, Reports.place(rec, s))
                InfoRow(Icons.AutoMirrored.Filled.Rule, s.format, Reports.formatLabel(rec, s))
                InfoRow(Icons.Filled.BarChart, s.pointsWon, "${Reports.pointsWon(rec, Side.P1)} - ${Reports.pointsWon(rec, Side.P2)}")
                InfoRow(Icons.Filled.BarChart, s.gamesWon, "${Reports.gamesWon(state, Side.P1)} - ${Reports.gamesWon(state, Side.P2)}")
                if (rec.batteryStart.isNotEmpty() || rec.batteryEnd.isNotEmpty()) {
                    InfoRow(Icons.Filled.BatteryStd, s.bandsBattery, Reports.batteryLine(rec, s))
                }
            }
        }
        Column(
            Modifier.fillMaxWidth().background(TsmColors.Surface).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BigButton(s.saveHistory, Icons.Filled.Save, { showSave = true }, Modifier.weight(1f))
                BigButton(s.share, Icons.Filled.Share, {
                    c.shareIntent()?.let { runCatching { context.startActivity(it) }.onSuccess { c.markSummaryKept() } }
                }, Modifier.weight(1f), color = TsmColors.Orange, onColor = TsmColors.OnOrange)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // Senza salvataggio né condivisione il riepilogo si perde: un tocco solo non basta.
                GhostButton(s.newMatch, Icons.Filled.Replay, { if (kept) leave(false) else leaving = false }, Modifier.weight(1f))
                GhostButton(s.exit, Icons.AutoMirrored.Filled.ExitToApp, { if (kept) leave(true) else leaving = true }, Modifier.weight(1f))
            }
        }
    }

    if (showSave) SaveDialog(c) { showSave = false }
    leaving?.let { exit ->
        ConfirmDialog(
            icon = if (exit) Icons.AutoMirrored.Filled.ExitToApp else Icons.Filled.Replay,
            title = s.summaryLeaveTitle,
            text = s.summaryLeaveText,
            confirm = if (exit) s.exit else s.newMatch,
            onConfirm = {
                leaving = null
                leave(exit)
            },
            onDismiss = { leaving = null },
        )
    }
}

@Composable
private fun InfoRow(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = TsmColors.Ball, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, color = TsmColors.TextDim, modifier = Modifier.width(110.dp))
        Text(value, color = TsmColors.TextMain, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
    }
}

/** "Salva nello storico": nome del file, cartella scelta dall'utente e formati. */
@Composable
private fun SaveDialog(c: MatchController, onDismiss: () -> Unit) {
    val s = LocalStrings.current
    var name by remember { mutableStateOf(c.defaultFileName()) }
    var folder by remember { mutableStateOf(c.folderLabel()) }
    var txt by remember { mutableStateOf(true) }
    var json by remember { mutableStateOf(true) }
    var png by remember { mutableStateOf(true) }
    val treeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            c.setHistoryFolder(uri)
            folder = c.folderLabel()
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Save, null, tint = TsmColors.Ball) },
        title = { Text(s.saveDialogTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(s.fileName) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text(s.folder, color = TsmColors.TextDim)
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(TsmColors.SurfaceHigh)
                        .clickable { treeLauncher.launch(null) }.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Folder, null, tint = TsmColors.Orange)
                    Spacer(Modifier.width(8.dp))
                    Text(folder, color = TsmColors.TextMain, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Row {
                    TextButton(onClick = { treeLauncher.launch(null) }) { Text(s.chooseFolder) }
                    TextButton(onClick = {
                        c.setHistoryFolder(null)
                        folder = c.folderLabel()
                    }) { Text(s.defaultFolder, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
                CheckRow(s.formatReport, txt) { txt = it }
                CheckRow(s.formatData, json) { json = it }
                CheckRow(s.formatImage, png) { png = it }
            }
        },
        confirmButton = {
            Button(enabled = txt || json || png, onClick = {
                c.saveHistory(name, txt, json, png)
                onDismiss()
            }) { Text(s.save) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.cancel) } },
    )
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, onChange, colors = CheckboxDefaults.colors(checkedColor = TsmColors.Ball, checkmarkColor = TsmColors.OnBall))
        Text(label, color = TsmColors.TextMain)
    }
}
