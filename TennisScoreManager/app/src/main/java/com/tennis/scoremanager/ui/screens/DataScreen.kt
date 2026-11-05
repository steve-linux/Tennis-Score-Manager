// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.tennis.scoremanager.BuildConfig
import com.tennis.scoremanager.MatchController
import com.tennis.scoremanager.data.Backup
import com.tennis.scoremanager.data.Reports
import com.tennis.scoremanager.ui.BigButton
import com.tennis.scoremanager.ui.GhostButton
import com.tennis.scoremanager.ui.LocalStrings
import com.tennis.scoremanager.ui.ScreenScaffold
import com.tennis.scoremanager.ui.SectionCard
import com.tennis.scoremanager.ui.SwitchRow
import com.tennis.scoremanager.ui.TsmColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Altro › Profilo e dati: backup completo in un file che resta tuo, ripristino (anche su un telefono nuovo) e
 * cancellazione di tutti i dati. Dopo ripristino e cancellazione l'app si chiude: riaprendola riparte dai dati nuovi.
 */
@Composable
fun DataScreen(c: MatchController) {
    val s = LocalStrings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    BackHandler { c.back() }
    val lang = c.options.value.lang
    var videos by remember { mutableStateOf(false) }
    var wipeVideos by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var restoreAsk by remember { mutableStateOf<Pair<Uri, Backup.Manifest>?>(null) }
    var wipeAsk by remember { mutableStateOf(0) }  // 0 = no, 1 = prima domanda, 2 = conferma
    var closeAfter by remember { mutableStateOf(false) }
    val videoMb by produceState(0L) {
        value = withContext(Dispatchers.IO) { Backup.videoBytes(context, c.storage, s.strokeFolderName) / 1_048_576 }
    }
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        busy = s.dataBusy
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { Backup.write(context, c.storage, it, videos, BuildConfig.VERSION_NAME, s.strokeFolderName) } ?: error("open")
                }.isSuccess
            }
            busy = null
            if (ok) c.storage.lastBackupAt = System.currentTimeMillis()
            message = if (ok) s.dataBackupDone else s.dataBackupFailed
        }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val m = withContext(Dispatchers.IO) { runCatching { context.contentResolver.openInputStream(uri)?.use { Backup.peek(it) } }.getOrNull() }
            if (m == null) message = s.dataNotBackup else restoreAsk = uri to m
        }
    }
    ScreenScaffold(
        onHelp = { c.openHelp("7.8") },
        title = s.dataTitle,
        subtitle = s.dataHint,
        icon = Icons.Filled.Backup,
        bottomBar = { GhostButton(s.back, Icons.AutoMirrored.Filled.ArrowBack, { c.back() }, Modifier.weight(1f)) },
    ) {
        // Chi usa l'app: i ruoli cambiano le schede della prima schermata, non i dati.
        val roles by c.roles.collectAsState()
        SectionCard(s.rolesTitle, Icons.Filled.Groups) {
            Text(s.rolesHint, color = TsmColors.TextDim, fontSize = 13.sp)
            com.tennis.scoremanager.ui.RolesPicker(roles) { c.setRoles(it) }
        }
        SectionCard(s.dataBackup, Icons.Filled.Backup) {
            Text(s.dataBackupHint, color = TsmColors.TextDim, fontSize = 13.sp)
            var reminder by remember { mutableStateOf(c.storage.backupReminder) }
            SwitchRow(Icons.Filled.Alarm, s.backupReminder, s.backupReminderHint, reminder) { reminder = it; c.storage.backupReminder = it }
            SwitchRow(Icons.Filled.Videocam, s.dataVideos(videoMb.toInt()), s.dataVideosHint, videos) { videos = it }
            BigButton(s.dataBackupDo, Icons.Filled.Backup, {
                val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())
                create.launch("TennisScoreManager-backup-$stamp.zip")
            }, Modifier.fillMaxWidth())
        }
        SectionCard(s.dataRestore, Icons.Filled.Restore) {
            Text(s.dataRestoreHint, color = TsmColors.TextDim, fontSize = 13.sp)
            GhostButton(s.dataRestoreDo, Icons.Filled.Restore, { open.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }, Modifier.fillMaxWidth())
        }
        SectionCard(s.dataWipe, Icons.Filled.DeleteForever, accent = TsmColors.Danger) {
            Text(s.dataWipeHint, color = TsmColors.TextDim, fontSize = 13.sp)
            SwitchRow(Icons.Filled.Videocam, s.dataWipeVideos, s.dataWipeVideosHint, wipeVideos) { wipeVideos = it }
            GhostButton(s.dataWipe, Icons.Filled.DeleteForever, { wipeAsk = 1 }, Modifier.fillMaxWidth())
        }
    }
    busy?.let { AlertDialog(onDismissRequest = {}, text = { Text(it) }, confirmButton = {}) }
    message?.let { m ->
        AlertDialog(
            onDismissRequest = { message = null; if (closeAfter) c.closeAfterData() },
            text = { Text(m) },
            confirmButton = { TextButton(onClick = { message = null; if (closeAfter) c.closeAfterData() }) { Text(s.ok) } },
        )
    }
    restoreAsk?.let { (uri, m) ->
        AlertDialog(
            onDismissRequest = { restoreAsk = null },
            title = { Text(s.dataRestore) },
            text = { Text(s.dataRestoreAsk(Reports.date(m.createdAt, lang), m.app, m.videos)) },
            confirmButton = {
                TextButton(onClick = {
                    restoreAsk = null
                    busy = s.dataBusy
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) {
                            runCatching { context.contentResolver.openInputStream(uri)?.use { Backup.restore(context, c.storage, it) } == true }.getOrDefault(false)
                        }
                        busy = null
                        closeAfter = ok
                        message = if (ok) s.dataRestoreDone else s.dataNotBackup
                    }
                }) { Text(s.dataRestoreDo, color = TsmColors.Danger, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { restoreAsk = null }) { Text(s.back) } },
        )
    }
    if (wipeAsk > 0) {
        AlertDialog(
            onDismissRequest = { wipeAsk = 0 },
            title = { Text(s.dataWipe) },
            text = { Text(if (wipeAsk == 1) s.dataWipeAsk else s.dataWipeAsk2) },
            confirmButton = {
                TextButton(onClick = {
                    if (wipeAsk == 1) { wipeAsk = 2; return@TextButton }
                    wipeAsk = 0
                    busy = s.dataBusy
                    scope.launch {
                        withContext(Dispatchers.IO) { Backup.wipe(context, c.storage, wipeVideos, listOf(s.strokeFolderName)) }
                        busy = null
                        closeAfter = true
                        message = s.dataWipeDone
                    }
                }) { Text(if (wipeAsk == 1) s.next else s.dataWipeConfirm, color = TsmColors.Danger, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { wipeAsk = 0 }) { Text(s.back) } },
        )
    }
}
