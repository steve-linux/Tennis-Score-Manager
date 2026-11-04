// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tennis.scoremanager.MatchController
import com.tennis.scoremanager.data.OfficeBackup
import com.tennis.scoremanager.data.OfficeSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private fun whenText(t: Long, c: MatchController): String = java.text.DateFormat.getDateTimeInstance(
    java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT, c.options.value.lang.locale,
).format(java.util.Date(t))

/**
 * Campi › Backup della segreteria: il backup automatico, un backup da mandare dove si vuole (Drive, email…) e il
 * ripristino da un file o dalle copie del telefono. Dal PC c'è la stessa cosa, con le copie del server.
 */
@Composable
fun OfficeBackupDialog(c: MatchController, onDone: () -> Unit) {
    val s = LocalStrings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var auto by remember { mutableStateOf(c.storage.officeBackupAuto) }
    var copies by remember { mutableStateOf(c.officeBackups()) }
    var ask by remember { mutableStateOf<OfficeSnapshot?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bytes = withContext(Dispatchers.IO) { runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull() }
            val snap = bytes?.let(OfficeBackup::decode)
            if (snap == null) note = s.obNotBackup else ask = snap
        }
    }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(s.obTitle) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(s.obHint, color = TsmColors.TextDim, fontSize = 13.sp)
                SwitchRow(Icons.Filled.Schedule, s.obAuto, s.obAutoHint, auto) { auto = it; c.setOfficeBackupAuto(it) }
                val last = c.storage.officeBackupAt
                Text(if (last > 0) s.obLast(whenText(last, c)) else s.obNever, color = TsmColors.TextDim, fontSize = 13.sp)
                GhostButton(s.obShare, Icons.Filled.Share, {
                    busy = true
                    scope.launch {
                        val name = c.officeBackupNow(onlyIfChanged = false)
                        copies = c.officeBackups()
                        busy = false
                        val f = name?.let { c.officeBackupFile(it) } ?: return@launch
                        // Si condivide una copia nella cartella "share": il FileProvider dà solo quella.
                        val out = withContext(Dispatchers.IO) { File(File(context.cacheDir, "share").apply { mkdirs() }, f.name).also { f.copyTo(it, overwrite = true) } }
                        context.startActivity(Intent.createChooser(c.shareFileIntent(out, "application/octet-stream"), s.obShare))
                    }
                }, Modifier.fillMaxWidth(), enabled = !busy)
                GhostButton(s.obRestoreFile, Icons.Filled.Restore, { pick.launch(arrayOf("*/*")) }, Modifier.fillMaxWidth())
                note?.let { Text(it, color = TsmColors.Orange, fontSize = 13.sp) }
                if (copies.isNotEmpty()) {
                    Text(s.obCopies, color = TsmColors.TextMain, fontSize = 14.sp)
                    for (f in copies.take(5)) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(whenText(f.at, c), color = TsmColors.TextDim, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            scope.launch {
                                val snap = withContext(Dispatchers.IO) { c.officeBackupFile(f.name)?.readBytes()?.let(OfficeBackup::decode) }
                                if (snap == null) note = s.obNotBackup else ask = snap
                            }
                        }) { Text(s.obRestore) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text(s.ok) } },
    )
    ask?.let { snap ->
        val sum = OfficeBackup.summary(snap)
        AlertDialog(
            onDismissRequest = { ask = null },
            title = { Text(s.obRestoreTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(listOf(sum.club, whenText(sum.createdAt, c)).filter { it.isNotBlank() }.joinToString(" · "), color = TsmColors.TextMain)
                    Text("${s.membersTitle}: ${sum.members} · ${s.paymentsTitle}: ${sum.charges} · ${s.planTitle}: ${sum.bookings}", color = TsmColors.TextDim, fontSize = 13.sp)
                    Text(s.obRestoreAsk, color = TsmColors.Orange, fontSize = 13.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    ask = null
                    scope.launch { c.restoreOffice(snap); copies = c.officeBackups(); note = s.obRestored }
                }) { Text(s.obRestore) }
            },
            dismissButton = { TextButton(onClick = { ask = null }) { Text(s.back) } },
        )
    }
}
