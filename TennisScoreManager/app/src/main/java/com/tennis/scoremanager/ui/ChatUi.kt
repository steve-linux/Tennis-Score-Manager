// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tennis.scoremanager.data.SrvChatMsg
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** I colori dei nomi nella chat di gruppo: ognuno sempre il suo, dal nome. */
private val NameColors = listOf(
    Color(0xFF29B6F6), Color(0xFFFF9800), Color(0xFFAB47BC), Color(0xFF66BB6A), Color(0xFFEF5350), Color(0xFF26C6DA), Color(0xFFFFCA28), Color(0xFFEC407A),
)
fun nameColor(name: String): Color = NameColors[(name.hashCode() and 0x7fffffff) % NameColors.size]

/** Le iniziali in un cerchio colorato, come nelle app di messaggi; [icon] al posto delle iniziali (gruppo, bacheca). */
@Composable
fun ChatAvatar(name: String, size: Int = 44, icon: ImageVector? = null) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(if (icon != null) TsmColors.Ball else nameColor(name)), contentAlignment = Alignment.Center) {
        if (icon != null) Icon(icon, null, tint = TsmColors.OnBall, modifier = Modifier.size((size * 0.55).dp))
        else Text(name.split(' ', '-').filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1).uppercase() }.ifBlank { "?" },
            color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size * 0.38).sp)
    }
}

/** Una riga dell'elenco delle chat: cerchio, nome, ultimo messaggio, ora e il tondino coi non letti. */
@Composable
fun ChatRow(name: String, preview: String, at: Long, unread: Int, icon: ImageVector? = null, onClick: () -> Unit) {
    val time = remember(at) { if (at > 0) whenShort(at) else "" }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ChatAvatar(name, icon = icon)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, color = TsmColors.TextMain, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(time, color = if (unread > 0) TsmColors.Ball else TsmColors.TextDim, fontSize = 12.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(preview, color = TsmColors.TextDim, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (unread > 0) Box(Modifier.padding(start = 6.dp).clip(CircleShape).background(TsmColors.Ball).padding(horizontal = 7.dp, vertical = 2.dp)) {
                    Text(if (unread > 99) "99+" else unread.toString(), color = TsmColors.OnBall, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** L'ora se è di oggi, se no la data: come negli elenchi delle chat. */
private fun whenShort(at: Long): String {
    val day = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalDate()
    return if (day == LocalDate.now()) DateFormat.getTimeInstance(DateFormat.SHORT).format(java.util.Date(at))
    else DateFormat.getDateInstance(DateFormat.SHORT).format(java.util.Date(at))
}

/**
 * Una chat del circolo a tutto schermo, come nelle app di messaggi: in alto il titolo, i messaggi a fumetti (i propri
 * a destra; nelle chat di gruppo [group] il nome colorato di chi scrive) con le date in mezzo, in basso la barra per
 * scrivere. Si ricarica ogni pochi secondi finché è aperta. [load] dà i messaggi; [send] null = solo lettura (la
 * bacheca per i giocatori); tenendo premuto un messaggio: [report] (segnalare quelli degli altri) o [delete] (la
 * moderazione della segreteria). [extra] è una voce del menu in alto (Blocca, nelle private). [onRead] riceve l'ora
 * dell'ultimo letto.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatDialog(
    title: String,
    load: suspend () -> Result<List<SrvChatMsg>>,
    send: (suspend (String) -> String?)? = null,
    delete: (suspend (String) -> String?)? = null,
    report: (suspend (String) -> String?)? = null,
    /** I propri messaggi: correggerli (nei primi [CHAT_EDIT_MINUTES] minuti) ed eliminarli. */
    editMine: (suspend (String, String) -> String?)? = null,
    deleteMine: (suspend (String) -> String?)? = null,
    hint: String? = null,
    extra: Pair<String, () -> Unit>? = null,
    group: Boolean = false,
    icon: ImageVector? = null,
    errText: (String) -> String,
    onRead: (Long) -> Unit = {},
    onDone: () -> Unit,
) {
    val s = LocalStrings.current
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    var msgs by remember { mutableStateOf<List<SrvChatMsg>>(emptyList()) }
    var text by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var tick by remember { mutableStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf<SrvChatMsg?>(null) }
    /** Il proprio messaggio che si sta correggendo: il testo va nella barra in basso. */
    var editing by remember { mutableStateOf<SrvChatMsg?>(null) }
    var deleteAsk by remember { mutableStateOf<SrvChatMsg?>(null) }
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
    // I messaggi con le date in mezzo ("Oggi", "Ieri" o il giorno); in fondo sempre l'ultimo.
    val zone = remember { ZoneId.systemDefault() }
    val rows = remember(msgs) {
        buildList<Any> {
            var last: LocalDate? = null
            for (m in msgs) {
                val d = Instant.ofEpochMilli(m.at).atZone(zone).toLocalDate()
                if (d != last) { add(d); last = d }
                add(m)
            }
        }
    }
    LaunchedEffect(rows.size) { if (rows.isNotEmpty()) listState.animateScrollToItem(rows.size + 1 + if (hint != null) 1 else 0) }
    val time = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }
    val dayFmt = remember { DateFormat.getDateInstance(DateFormat.FULL) }

    Dialog(onDismissRequest = onDone, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(TsmColors.Background).statusBarsPadding().navigationBarsPadding().imePadding()) {
            // In alto: indietro, il cerchio, il titolo; nel menu le azioni sulla chat.
            Row(Modifier.fillMaxWidth().background(TsmColors.Surface).padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, s.back, tint = TsmColors.TextMain) }
                ChatAvatar(title, size = 38, icon = icon ?: if (group) Icons.Filled.Forum else if (send == null) Icons.Filled.Campaign else null)
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(title, color = TsmColors.TextMain, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (extra != null) Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, null, tint = TsmColors.TextMain) }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text(extra.first, color = TsmColors.Danger) }, leadingIcon = { Icon(Icons.Filled.Block, null, tint = TsmColors.Danger) },
                            onClick = { menu = false; extra.second() })
                    }
                }
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 10.dp), state = listState, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                item { Spacer(Modifier.size(6.dp)) }
                // Com'è questa chat (chi la legge, come si segnala): un riquadro in cima, come nelle app di messaggi.
                hint?.let { h ->
                    item {
                        Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp), contentAlignment = Alignment.Center) {
                            Text(h, color = TsmColors.TextDim, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(TsmColors.Surface).padding(horizontal = 12.dp, vertical = 6.dp))
                        }
                    }
                }
                if (msgs.isEmpty()) item {
                    Box(Modifier.fillMaxWidth().padding(top = 30.dp), contentAlignment = Alignment.Center) { Text(s.pbChatNone, color = TsmColors.TextDim, fontSize = 14.sp) }
                }
                items(rows, key = { r -> if (r is SrvChatMsg) r.id else r.toString() }) { r ->
                    if (r is LocalDate) {
                        val label = when (r) {
                            LocalDate.now() -> s.pbToday
                            LocalDate.now().minusDays(1) -> s.pbYesterday
                            else -> dayFmt.format(java.util.Date.from(r.atStartOfDay(zone).toInstant()))
                        }
                        Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                            Text(label, color = TsmColors.TextDim, fontSize = 12.sp,
                                modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(TsmColors.Surface).padding(horizontal = 10.dp, vertical = 3.dp))
                        }
                    } else {
                        val m = r as SrvChatMsg
                        val canAct = !m.deleted && ((report != null && !m.mine && !m.office) || delete != null || (m.mine && deleteMine != null))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.mine) Arrangement.End else Arrangement.Start) {
                            Column(
                                Modifier.widthIn(max = 300.dp)
                                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = if (m.mine) 16.dp else 4.dp, bottomEnd = if (m.mine) 4.dp else 16.dp))
                                    .background(if (m.mine) TsmColors.Ball else if (m.office) TsmColors.SurfaceHigh else TsmColors.Surface)
                                    .combinedClickable(onClick = {}, onLongClick = { if (canAct) picked = m })
                                    .padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 4.dp),
                            ) {
                                if (!m.mine && (group || m.office)) Text(m.name, color = if (m.office) TsmColors.Ball else nameColor(m.name), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                val fg = if (m.mine) TsmColors.OnBall else TsmColors.TextMain
                                if (m.deleted) Text("🚫 " + s.pbMsgDeleted + if (m.text.isNotEmpty()) " — ${m.text}" else "", color = fg.copy(alpha = 0.6f), fontSize = 14.sp,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                                else Text(m.text, color = fg, fontSize = 15.sp)
                                Text((if (m.edited && !m.deleted) s.pbMsgEdited + " · " else "") + time.format(java.util.Date(m.at)),
                                    color = if (m.mine) TsmColors.OnBall.copy(alpha = 0.6f) else TsmColors.TextDim, fontSize = 11.sp, modifier = Modifier.align(Alignment.End))
                            }
                        }
                    }
                }
                item { Spacer(Modifier.size(6.dp)) }
            }
            err?.let { Text(it, color = TsmColors.Orange, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) }
            // In basso la barra per scrivere (non c'è dove si legge soltanto).
            // Mentre si corregge un messaggio: una riga sopra la barra, con la X per lasciar perdere.
            editing?.let { e ->
                Row(Modifier.fillMaxWidth().background(TsmColors.Surface).padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Edit, null, tint = TsmColors.Ball, modifier = Modifier.size(18.dp))
                    Column(Modifier.weight(1f).padding(start = 10.dp, top = 6.dp, bottom = 6.dp)) {
                        Text(s.pbMsgEditing, color = TsmColors.Ball, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(e.text, color = TsmColors.TextDim, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = { editing = null; text = "" }) { Icon(Icons.Filled.Close, s.back, tint = TsmColors.TextDim) }
                }
            }
            if (send != null) Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(
                    text, { text = it.take(500) }, Modifier.weight(1f), placeholder = { Text(s.pbChatPlaceholder) }, maxLines = 5,
                    shape = RoundedCornerShape(24.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = TsmColors.Surface, unfocusedContainerColor = TsmColors.Surface,
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = TsmColors.TextMain, unfocusedTextColor = TsmColors.TextMain, cursorColor = TsmColors.Ball,
                    ),
                )
                val ready = !busy && text.isNotBlank()
                Box(
                    Modifier.size(52.dp).clip(CircleShape).background(if (ready) TsmColors.Ball else TsmColors.SurfaceHigh).clickable(enabled = ready) {
                        val t = text.trim()
                        busy = true
                        val e = editing
                        scope.launch {
                            err = (if (e != null && editMine != null) editMine(e.id, t) else send(t))?.let(errText)
                            if (err == null) { text = ""; editing = null }
                            busy = false; tick++
                        }
                    },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.AutoMirrored.Filled.Send, s.pbChatSend, tint = if (ready) TsmColors.OnBall else TsmColors.TextDim) }
            } else Spacer(Modifier.size(8.dp))
        }
        // Tenendo premuto un proprio messaggio: correggerlo (nei primi minuti) o eliminarlo.
        picked?.takeIf { it.mine && delete == null }?.let { m ->
            val canEdit = editMine != null && System.currentTimeMillis() - m.at <= com.tennis.scoremanager.data.CHAT_EDIT_MINUTES * 60_000L
            AlertDialog(
                onDismissRequest = { picked = null },
                text = { Text(m.text, color = TsmColors.TextMain, maxLines = 6, overflow = TextOverflow.Ellipsis) },
                confirmButton = {
                    Row {
                        if (canEdit) TextButton(onClick = { picked = null; editing = m; text = m.text }) {
                            Icon(Icons.Filled.Edit, null, tint = TsmColors.Ball); Text(" " + s.pbMsgEdit, color = TsmColors.Ball)
                        }
                        TextButton(onClick = { picked = null; deleteAsk = m }) { Icon(Icons.Filled.Delete, null, tint = TsmColors.Danger); Text(" " + s.pbMsgDelete, color = TsmColors.Danger) }
                    }
                },
                dismissButton = { TextButton(onClick = { picked = null }) { Text(s.back) } },
            )
        }
        deleteAsk?.let { m ->
            AlertDialog(
                onDismissRequest = { deleteAsk = null },
                text = { Text(s.pbMsgDeleteAsk) },
                confirmButton = { TextButton(onClick = {
                    deleteAsk = null
                    scope.launch { err = deleteMine?.invoke(m.id)?.let(errText); if (editing?.id == m.id) { editing = null; text = "" }; tick++ }
                }) { Text(s.pbMsgDelete, color = TsmColors.Danger) } },
                dismissButton = { TextButton(onClick = { deleteAsk = null }) { Text(s.back) } },
            )
        }
        // Tenendo premuto un messaggio: toglierlo (la segreteria) o segnalarlo.
        picked?.takeIf { !it.mine || delete != null }?.let { m ->
            AlertDialog(
                onDismissRequest = { picked = null },
                text = { Text("${m.name}: ${m.text}", color = TsmColors.TextMain, maxLines = 6, overflow = TextOverflow.Ellipsis) },
                confirmButton = {
                    if (delete != null) TextButton(onClick = { picked = null; scope.launch { err = delete(m.id)?.let(errText); tick++ } }) {
                        Icon(Icons.Filled.Delete, null, tint = TsmColors.Danger); Text(" " + s.srvChatDelete, color = TsmColors.Danger)
                    } else if (report != null) TextButton(onClick = {
                        picked = null
                        scope.launch { err = report(m.id)?.let(errText); if (err == null) android.widget.Toast.makeText(context, s.pbReported, android.widget.Toast.LENGTH_SHORT).show() }
                    }) { Icon(Icons.Filled.Flag, null, tint = TsmColors.Orange); Text(" " + s.pbReport, color = TsmColors.Orange) }
                },
                dismissButton = { TextButton(onClick = { picked = null }) { Text(s.back) } },
            )
        }
    }
}
