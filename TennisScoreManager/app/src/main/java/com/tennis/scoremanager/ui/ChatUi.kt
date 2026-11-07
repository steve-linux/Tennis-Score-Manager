// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tennis.scoremanager.data.SrvChatMsg
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Una chat del circolo in una finestra: i messaggi (i propri a destra, quelli della segreteria evidenziati), ricaricati
 * ogni pochi secondi finché è aperta. [load] dà i messaggi (tutti: la finestra è piccola); [send] null = solo lettura
 * (la bacheca per i giocatori); [delete] = moderazione della segreteria. [onRead] riceve l'ora dell'ultimo letto.
 */
@Composable
fun ChatDialog(
    title: String,
    load: suspend () -> Result<List<SrvChatMsg>>,
    send: (suspend (String) -> String?)? = null,
    delete: (suspend (String) -> String?)? = null,
    hint: String? = null,
    errText: (String) -> String,
    onRead: (Long) -> Unit = {},
    onDone: () -> Unit,
) {
    val s = LocalStrings.current
    val scope = rememberCoroutineScope()
    var msgs by remember { mutableStateOf<List<SrvChatMsg>>(emptyList()) }
    var text by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var tick by remember { mutableStateOf(0) }
    val listState = rememberLazyListState()
    LaunchedEffect(tick) {
        while (true) {
            load().onSuccess { list ->
                if (list != msgs) { msgs = list; list.maxOfOrNull { it.at }?.let(onRead) }
                err = null
            }.onFailure { err = errText((it as? com.tennis.scoremanager.data.ClubServerError)?.code ?: "NET") }
            delay(4_000)
        }
    }
    LaunchedEffect(msgs.size) { if (msgs.isNotEmpty()) listState.animateScrollToItem(msgs.lastIndex) }
    val time = remember { java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT) }
    val day = remember { java.text.DateFormat.getDateInstance(java.text.DateFormat.SHORT) }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                hint?.let { Text(it, color = TsmColors.TextDim, fontSize = 12.sp) }
                if (msgs.isEmpty()) Text(s.pbChatNone, color = TsmColors.TextDim, fontSize = 13.sp)
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp), state = listState, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(msgs, key = { it.id }) { m ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.mine) Arrangement.End else Arrangement.Start, verticalAlignment = Alignment.CenterVertically) {
                            Column(
                                Modifier.fillMaxWidth(0.85f).clip(RoundedCornerShape(12.dp))
                                    .background(if (m.mine) TsmColors.Ball else if (m.office) TsmColors.SurfaceHigh else TsmColors.Surface).padding(8.dp),
                            ) {
                                val stamp = java.util.Date(m.at)
                                Text("${if (m.mine) s.pbChatYou else m.name} · ${day.format(stamp)} ${time.format(stamp)}",
                                    color = if (m.mine) TsmColors.OnBall else if (m.office) TsmColors.Ball else TsmColors.TextDim, fontSize = 11.sp)
                                Text(m.text, color = if (m.mine) TsmColors.OnBall else TsmColors.TextMain, fontSize = 14.sp)
                            }
                            if (delete != null) IconButton(onClick = { scope.launch { err = delete(m.id)?.let(errText); tick++ } }) {
                                Icon(Icons.Filled.Delete, s.srvChatDelete, tint = TsmColors.Danger)
                            }
                        }
                    }
                }
                if (send != null) Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(text, { text = it.take(500) }, Modifier.weight(1f), placeholder = { Text(s.pbChatPlaceholder) }, maxLines = 4)
                    IconButton(onClick = {
                        val t = text.trim()
                        if (t.isEmpty() || busy) return@IconButton
                        busy = true
                        scope.launch { err = send(t)?.let(errText); if (err == null) text = ""; busy = false; tick++ }
                    }, enabled = !busy && text.isNotBlank()) { Icon(Icons.AutoMirrored.Filled.Send, s.pbChatSend, tint = TsmColors.Ball) }
                }
                err?.let { Text(it, color = TsmColors.Orange, fontSize = 13.sp) }
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text(s.ok) } },
    )
}
