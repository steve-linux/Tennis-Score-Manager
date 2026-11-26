// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tennis.scoremanager.MatchController
import com.tennis.scoremanager.data.CoachNote
import com.tennis.scoremanager.data.CoachNotes
import com.tennis.scoremanager.data.NoteTag
import com.tennis.scoremanager.data.PlayerProfile
import com.tennis.scoremanager.data.TrainedStroke
import com.tennis.scoremanager.ui.screens.noteDay
import com.tennis.scoremanager.ui.screens.noteTime
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date

/** Il nome di un'etichetta delle note (null = senza etichetta). I colpi hanno gli stessi nomi dell'allenamento. */
fun noteTagLabel(s: Strings, t: NoteTag?): String = when (t) {
    NoteTag.SERVE -> strokeLabel(s, TrainedStroke.SERVE)
    NoteTag.FOREHAND -> strokeLabel(s, TrainedStroke.FOREHAND)
    NoteTag.BACKHAND -> strokeLabel(s, TrainedStroke.BACKHAND)
    NoteTag.VOLLEY -> strokeLabel(s, TrainedStroke.VOLLEY)
    NoteTag.TACTICS -> s.cnTactics
    NoteTag.FITNESS -> s.cnFitness
    NoteTag.MENTAL -> s.cnMental
    null -> s.cnTagNone
}

/** I periodi del filtro: tutto, 30 giorni, 3 mesi, 12 mesi. */
private val PERIOD_DAYS = listOf<Int?>(null, 30, 90, 365)

/**
 * Le note dell'allenatore su un allievo, una per una: la nota generale (testo libero), una nota nuova (scritta o
 * dettata, con l'argomento), la ricerca e i filtri, l'elenco per mese e per giorno (i mesi passati chiusi), il
 * resoconto da condividere e la richiesta di un riassunto all'assistente che l'allenatore ha sul telefono.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun CoachNotesSection(c: MatchController, st: PlayerProfile) {
    val s = LocalStrings.current
    val context = LocalContext.current
    val lang = c.options.collectAsState().value.lang
    val area = LocalArea.current
    // Le note dettate finite nel testo libero (versione di prova di prima) diventano note dell'elenco, una volta.
    LaunchedEffect(st.id) {
        if (CoachNotes.hasLegacy(st.note)) {
            val (free, notes) = CoachNotes.split(st.note)
            c.savePlayer(st.copy(note = free, coachNotes = (notes + st.coachNotes).sortedByDescending { it.at }))
        }
    }
    fun save(list: List<CoachNote>) = c.savePlayer(st.copy(coachNotes = list.sortedByDescending { it.at }))

    // La nota generale: obiettivi e cose da ricordare sempre (si salva da sola mentre si scrive).
    SectionCard(s.cnGeneral, Icons.Filled.EditNote) {
        var note by remember(st.id) { mutableStateOf(st.note) }
        LaunchedEffect(note) {
            if (note == st.note) return@LaunchedEffect
            delay(800)
            c.savePlayer(st.copy(note = note))
        }
        OutlinedTextField(note, { note = it.take(4000) }, Modifier.fillMaxWidth(), minLines = 2, maxLines = 6, placeholder = { Text(s.cnGeneralHint) },
            trailingIcon = { DictateIcon(c) { said -> note = appendDictated(note, said).take(4000) } })
    }

    // Una nota nuova: scritta o dettata, con l'argomento se si vuole.
    SectionCard(s.studentNotes, Icons.AutoMirrored.Filled.Notes) {
        var draft by rememberSaveable(st.id) { mutableStateOf("") }
        var tag by rememberSaveable(st.id) { mutableStateOf<NoteTag?>(null) }
        OutlinedTextField(draft, { draft = it.take(2000) }, Modifier.fillMaxWidth(), minLines = 2, maxLines = 5, placeholder = { Text(s.cnNewHint) },
            trailingIcon = { DictateIcon(c) { said -> draft = appendDictated(draft, said).take(2000) } })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (t in NoteTag.entries) Chip(noteTagLabel(s, t), tag == t) { tag = if (tag == t) null else t }
        }
        BigButton(s.cnAdd, Icons.Filled.Add, {
            save(st.coachNotes + CoachNote(CoachNotes.newId(), System.currentTimeMillis(), draft.trim(), tag))
            draft = ""
            tag = null
        }, Modifier.fillMaxWidth(), enabled = draft.isNotBlank(), color = area, onColor = TsmColors.OnArea)
    }

    if (st.coachNotes.isEmpty()) {
        Text(s.cnNone, color = TsmColors.TextDim, fontSize = 13.sp)
        return
    }

    // Ricerca e filtri: valgono anche per il resoconto e per il riassunto.
    var query by rememberSaveable(st.id) { mutableStateOf("") }
    var period by rememberSaveable(st.id) { mutableStateOf(0) }
    var filterTag by rememberSaveable(st.id) { mutableStateOf<NoteTag?>(null) }
    val now = remember { System.currentTimeMillis() }
    val from = PERIOD_DAYS[period]?.let { now - it * 24L * 3600 * 1000 }
    val shown = CoachNotes.filter(st.coachNotes, from, filterTag, query)
    if (st.coachNotes.size > 5) OutlinedTextField(query, { query = it.take(40) }, Modifier.fillMaxWidth(), singleLine = true,
        placeholder = { Text(s.cnSearch) }, leadingIcon = { Icon(Icons.Filled.Search, null) })
    Segmented(listOf(SegOption(s.cnAll), SegOption(s.cn30), SegOption(s.cn90), SegOption(s.cnYear)), period, { period = it })
    val used = NoteTag.entries.filter { t -> st.coachNotes.any { it.tag == t } }
    if (used.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Chip(s.cnTagAll, filterTag == null) { filterTag = null }
        for (t in used) Chip(noteTagLabel(s, t), filterTag == t) { filterTag = if (filterTag == t) null else t }
    }

    // Resoconto e riassunto, sulle note che si vedono.
    var ask by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        GhostButton(s.cnReport, Icons.Filled.Share, {
            val text = s.cnReportTitle(st.name) + "\n\n" + CoachNotes.report(shown, { noteTagLabel(s, it) }, { noteDay(it) })
                .joinToString("\n\n") { (title, lines) -> title + "\n" + lines.joinToString("\n") }
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), s.cnReport))
        }, Modifier.weight(1f), enabled = shown.isNotEmpty())
        GhostButton(s.cnAsk, Icons.Filled.AutoAwesome, { ask = true }, Modifier.weight(1f), enabled = shown.isNotEmpty())
    }
    if (ask) AskSummaryDialog(c, st, shown) { ask = false }

    Text(s.cnCount(shown.size), color = TsmColors.TextDim, fontSize = 13.sp)
    // L'elenco per mese (il mese della nota più recente aperto, gli altri chiusi; con ricerca o filtri tutti aperti) e,
    // dentro, per giorno.
    val filtering = query.isNotBlank() || filterTag != null
    var openMonths by remember(st.id) { mutableStateOf(setOfNotNull(shown.firstOrNull()?.let { CoachNotes.monthStart(it.at) })) }
    var editing by remember { mutableStateOf<CoachNote?>(null) }
    for ((month, inMonth) in shown.groupBy { CoachNotes.monthStart(it.at) }) {
        val open = filtering || month in openMonths
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TsmColors.SurfaceHigh)
                .clickable { openMonths = if (month in openMonths) openMonths - month else openMonths + month }.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(SimpleDateFormat("LLLL yyyy", lang.locale).format(Date(month)).replaceFirstChar { it.uppercase() },
                color = TsmColors.TextMain, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("${inMonth.size}", color = TsmColors.TextDim, fontSize = 13.sp, modifier = Modifier.padding(end = 6.dp))
            Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = TsmColors.TextDim)
        }
        if (!open) continue
        for ((day, inDay) in inMonth.groupBy { CoachNotes.dayStart(it.at) }) {
            Text("📅 ${noteDay(day)}", color = area, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            for (n in inDay) Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TsmColors.Surface).clickable { editing = n }.padding(10.dp),
            ) {
                Text("🕐 ${noteTime(n.at)}", color = TsmColors.TextDim, fontSize = 13.sp, modifier = Modifier.width(72.dp))
                Column(Modifier.weight(1f)) {
                    n.tag?.let { Text(noteTagLabel(s, it), color = area, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    Text(n.text, color = TsmColors.TextMain, fontSize = 14.sp)
                }
            }
        }
    }
    editing?.let { n ->
        NoteEditDialog(c, n, onSave = { e -> save(st.coachNotes.map { if (it.id == e.id) e else it }); editing = null },
            onDelete = { save(st.coachNotes.filter { it.id != n.id }); editing = null }) { editing = null }
    }
}

@Composable
private fun Chip(label: String, on: Boolean, onClick: () -> Unit) {
    val area = LocalArea.current
    Text(
        label, color = if (on) TsmColors.OnArea else TsmColors.TextMain, fontSize = 13.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(if (on) area else TsmColors.SurfaceHigh).clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

/** Cambiare il testo o l'argomento di una nota, o eliminarla (con conferma). */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun NoteEditDialog(c: MatchController, n: CoachNote, onSave: (CoachNote) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    val s = LocalStrings.current
    var text by remember(n.id) { mutableStateOf(n.text) }
    var tag by remember(n.id) { mutableStateOf(n.tag) }
    var askDelete by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("📅 ${noteDay(n.at)}  🕐 ${noteTime(n.at)}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(text, { text = it.take(2000) }, Modifier.fillMaxWidth(), minLines = 3, maxLines = 8,
                    trailingIcon = { DictateIcon(c) { said -> text = appendDictated(text, said).take(2000) } })
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (t in NoteTag.entries) Chip(noteTagLabel(s, t), tag == t) { tag = if (tag == t) null else t }
                }
                TextButton(onClick = { askDelete = true }) { Text(s.cnDelete, color = TsmColors.Danger) }
            }
        },
        confirmButton = { TextButton(enabled = text.isNotBlank(), onClick = { onSave(n.copy(text = text.trim(), tag = tag)) }) { Text(s.ok) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.back) } },
    )
    if (askDelete) AlertDialog(
        onDismissRequest = { askDelete = false },
        text = { Text(s.cnDeleteAsk) },
        confirmButton = { TextButton(onClick = { askDelete = false; onDelete() }) { Text(s.delete, color = TsmColors.Danger) } },
        dismissButton = { TextButton(onClick = { askDelete = false }) { Text(s.back) } },
    )
}

/**
 * Il riassunto con l'assistente che l'allenatore ha già (Gemini, ChatGPT, Claude…): la richiesta, scritta o scelta tra
 * quelle pronte, con le note che si vedono; l'app la passa con Condividi e non manda niente da sola. Di serie solo il
 * nome dell'allievo, senza cognome.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun AskSummaryDialog(c: MatchController, st: PlayerProfile, notes: List<CoachNote>, onDone: () -> Unit) {
    val s = LocalStrings.current
    val context = LocalContext.current
    var request by rememberSaveable { mutableStateOf("") }
    var firstOnly by rememberSaveable { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDone,
        icon = { Icon(Icons.Filled.AutoAwesome, null, tint = LocalArea.current) },
        title = { Text(s.cnAsk) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(s.cnAskHint, color = TsmColors.TextDim, fontSize = 13.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (q in listOf(s.cnAskProgress, s.cnAskParents, s.cnAskPlan)) Chip(q, request == q) { request = q }
                }
                OutlinedTextField(request, { request = it.take(500) }, Modifier.fillMaxWidth(), minLines = 2, maxLines = 5, label = { Text(s.cnAskRequest) },
                    trailingIcon = { DictateIcon(c) { said -> request = appendDictated(request, said).take(500) } })
                SwitchRow(Icons.Filled.EditNote, s.cnAskFirstName, null, firstOnly) { firstOnly = it }
                Text(s.cnAskSends(notes.size), color = TsmColors.TextMain, fontSize = 13.sp)
            }
        },
        confirmButton = {
            TextButton(enabled = request.isNotBlank(), onClick = {
                val name = if (firstOnly) firstName(st) else st.fullName.ifBlank { st.name }
                val lines = notes.sortedBy { it.at }.joinToString("\n") { n ->
                    listOfNotNull("${noteDay(n.at)} ${noteTime(n.at)}", n.tag?.let { "[${noteTagLabel(s, it)}]" }, n.text).joinToString(" ")
                }
                val text = s.cnAskPrompt(name, request.trim()) + "\n\n" + lines
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), s.cnAskOpen))
                onDone()
            }) { Text(s.cnAskOpen) }
        },
        dismissButton = { TextButton(onClick = onDone) { Text(s.back) } },
    )
}

/** Il nome senza cognome: il nome scritto, o il soprannome, o la prima parola del nome. */
private fun firstName(p: PlayerProfile): String =
    p.firstName.ifBlank { p.nickname }.ifBlank { p.name.trim().substringBefore(' ') }.ifBlank { p.name }
