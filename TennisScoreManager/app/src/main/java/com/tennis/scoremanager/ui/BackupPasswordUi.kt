// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tennis.scoremanager.MatchController
import com.tennis.scoremanager.data.Backup
import com.tennis.scoremanager.data.BackupCrypt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream

/** La password di un backup protetto, per aprirlo. */
@Composable
fun BackupPasswordAsk(onOk: (String) -> Unit, onDismiss: () -> Unit) {
    val s = LocalStrings.current
    var pw by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Lock, null, tint = TsmColors.Ball) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(s.bpAsk, color = TsmColors.TextMain)
                OutlinedTextField(pw, { pw = it }, Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
            }
        },
        confirmButton = { TextButton(enabled = pw.isNotEmpty(), onClick = { onOk(pw) }) { Text(s.ok) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.back) } },
    )
}

/**
 * Apre un backup scelto (in chiaro o protetto): se serve chiede la password, poi legge il manifest. [onReady] riceve
 * come riaprirlo in chiaro e il manifest; [onError] il messaggio (non è un backup, password sbagliata).
 */
class BackupOpener(private val onReady: (() -> InputStream?, Backup.Manifest) -> Unit, private val onError: (String) -> Unit) {
    var asking by mutableStateOf<(() -> InputStream?)?>(null)

    suspend fun pick(open: () -> InputStream?, s: Strings) {
        if (withContext(Dispatchers.IO) { Backup.isLocked(open) }) { asking = open; return }
        val m = withContext(Dispatchers.IO) { runCatching { open()?.use { Backup.peek(it) } }.getOrNull() }
        if (m == null) onError(s.dataNotBackup) else onReady(open, m)
    }

    suspend fun withPassword(pw: String, s: Strings) {
        val open = asking ?: return
        asking = null
        val r = withContext(Dispatchers.IO) { runCatching { Backup.openPlain(open, pw.toCharArray())?.use { Backup.peek(it) } } }
        val m = r.getOrNull()
        when {
            r.exceptionOrNull() is BackupCrypt.WrongPassword -> onError(s.bpWrong)
            m == null -> onError(s.dataNotBackup)
            else -> onReady({ Backup.openPlain(open, pw.toCharArray()) }, m)
        }
    }
}

/** La richiesta della password, quando l'[opener] ne ha bisogno. */
@Composable
fun BackupOpenerDialog(opener: BackupOpener) {
    val s = LocalStrings.current
    val scope = rememberCoroutineScope()
    if (opener.asking != null) BackupPasswordAsk({ pw -> scope.launch { opener.withPassword(pw, s) } }) { opener.asking = null }
}

/**
 * Backup e dati: proteggere i backup con una password (la si scrive due volte), cambiarla o toglierla. Con la
 * password nuova si fa subito una copia protetta al posto di quelle di prima in chiaro.
 */
@Composable
fun BackupPasswordRow(c: MatchController, onMessage: (String) -> Unit, onChanged: () -> Unit) {
    val s = LocalStrings.current
    val scope = rememberCoroutineScope()
    var on by remember { mutableStateOf(c.storage.backupPassword != null) }
    var setting by remember { mutableStateOf(false) }
    SwitchRow(Icons.Filled.Lock, s.bpTitle, s.bpHint, on) { want ->
        if (want) setting = true
        else scope.launch { c.setBackupPassword(null); on = false; onMessage(s.bpRemoved); onChanged() }
    }
    if (on) GhostButton(s.bpChange, Icons.Filled.Key, { setting = true }, Modifier.fillMaxWidth())
    if (setting) {
        var a by remember { mutableStateOf("") }
        var b by remember { mutableStateOf("") }
        val ok = a.length >= 6 && a == b
        AlertDialog(
            onDismissRequest = { setting = false },
            icon = { Icon(Icons.Filled.Lock, null, tint = TsmColors.Ball) },
            title = { Text(s.bpTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(s.bpHint, color = TsmColors.TextDim, fontSize = 13.sp)
                    OutlinedTextField(a, { a = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(s.bpNew) }, visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                    OutlinedTextField(b, { b = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(s.bpAgain) }, visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                    if (b.isNotEmpty() && !ok) Text(s.bpMismatch, color = TsmColors.Danger, fontSize = 13.sp)
                }
            },
            confirmButton = {
                TextButton(enabled = ok, onClick = {
                    setting = false
                    scope.launch {
                        val done = c.setBackupPassword(a)
                        on = true
                        onMessage(if (done) s.bpSet else s.dataBackupFailed)
                        onChanged()
                    }
                }) { Text(s.ok) }
            },
            dismissButton = { TextButton(onClick = { setting = false }) { Text(s.back) } },
        )
    }
}
