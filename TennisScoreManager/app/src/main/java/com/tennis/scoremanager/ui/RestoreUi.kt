// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.tennis.scoremanager.MatchController
import com.tennis.scoremanager.data.Backup
import com.tennis.scoremanager.data.Reports
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * La scelta del file che si apre già nella cartella delle copie automatiche (Documenti/Tennis Score Manager/Backup):
 * dopo una reinstallazione l'app non vede più da sola i file della vecchia installazione, ma chi li sceglie qui sì.
 */
private class OpenBackup : ActivityResultContracts.OpenDocument() {
    override fun createIntent(context: Context, input: Array<String>): Intent = super.createIntent(context, input).apply {
        runCatching {
            putExtra(DocumentsContract.EXTRA_INITIAL_URI,
                DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Documents/Tennis Score Manager/${Backup.AUTO_DIR}"))
        }
    }
}

/**
 * Il salvataggio di un backup a mano, che parte da Documenti/Tennis Score Manager/Backup accanto alle copie automatiche
 * (quelle tolte da sole hanno un altro nome, [Backup.autoList]): senza, Android riapre l'ultima cartella usata (spesso
 * quella dei video importati).
 */
class CreateInDocuments(mime: String) : ActivityResultContracts.CreateDocument(mime) {
    override fun createIntent(context: Context, input: String): Intent = super.createIntent(context, input).apply {
        runCatching {
            putExtra(DocumentsContract.EXTRA_INITIAL_URI,
                DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Documents/Tennis Score Manager/${Backup.AUTO_DIR}"))
        }
    }
}

/**
 * Prima apertura: chi aveva già l'app ritrova i suoi dati da una copia (quelle automatiche restano in Documenti anche
 * se si disinstalla). Dopo il ripristino l'app si chiude e si riapre con i dati di prima.
 */
@Composable
fun FirstStartRestore(c: MatchController) {
    val s = LocalStrings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lang = c.options.collectAsState().value.lang
    var ask by remember { mutableStateOf<Pair<() -> java.io.InputStream?, Backup.Manifest>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf(false) }
    // In chiaro o protetto da password (allora la si chiede prima).
    val opener = remember { BackupOpener({ input, m -> ask = input to m }) { message = it } }
    BackupOpenerDialog(opener)
    val open = rememberLauncherForActivityResult(OpenBackup()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch { opener.pick({ context.contentResolver.openInputStream(uri) }, s) }
    }
    GhostButton(s.onbRestore, Icons.Filled.Restore, { open.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }, Modifier.fillMaxWidth())
    Text(s.onbRestoreHint, color = TsmColors.TextDim, fontSize = 13.sp)
    ask?.let { (input, m) ->
        AlertDialog(
            onDismissRequest = { ask = null },
            title = { Text(s.dataRestore) },
            text = { Text(s.dataRestoreAsk(Reports.date(m.createdAt, lang), m.app, m.videos)) },
            confirmButton = {
                TextButton(onClick = {
                    ask = null
                    busy = true
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) {
                            runCatching { input()?.use { Backup.restore(context, c.storage, it) } == true }.getOrDefault(false)
                        }
                        busy = false
                        done = ok
                        message = if (ok) s.dataRestoreDone else s.dataNotBackup
                    }
                }) { Text(s.dataRestoreDo, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { ask = null }) { Text(s.back) } },
        )
    }
    if (busy) AlertDialog(onDismissRequest = {}, text = { Text(s.dataBusy) }, confirmButton = {})
    message?.let { msg ->
        AlertDialog(
            onDismissRequest = { message = null; if (done) c.closeAfterData() },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { message = null; if (done) c.closeAfterData() }) { Text(s.ok) } },
        )
    }
}
