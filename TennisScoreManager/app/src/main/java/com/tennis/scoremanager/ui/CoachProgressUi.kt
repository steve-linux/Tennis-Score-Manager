// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Grading
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tennis.scoremanager.MatchController
import com.tennis.scoremanager.data.Coaching
import com.tennis.scoremanager.data.Evaluation
import com.tennis.scoremanager.data.FitnessTest
import com.tennis.scoremanager.data.ManualMatch
import com.tennis.scoremanager.data.PlayerProfile
import com.tennis.scoremanager.data.StudentEvent
import com.tennis.scoremanager.ui.screens.shortDate

fun aspectLabel(s: Strings, key: String): String = when (key) {
    "serve" -> s.asServe; "forehand" -> s.asForehand; "backhand" -> s.asBackhand; "volley" -> s.asVolley
    "movement" -> s.asMovement; "tactics" -> s.asTactics; else -> s.asMental
}

fun testLabel(s: Strings, key: String): String = when (key) {
    "sprint" -> s.ftSprint; "spider" -> s.ftSpider; "jump" -> s.ftJump; else -> s.ftYoyo
}

/** Il valore di un test come si legge: tempi con due decimali, centimetri e livelli interi o con un decimale. */
fun testValue(key: String, v: Double): String =
    if (key == "sprint" || key == "spider") String.format(java.util.Locale.ROOT, "%.2f", v)
    else if (v % 1.0 == 0.0) v.toLong().toString() else String.format(java.util.Locale.ROOT, "%.1f", v)

/**
 * I progressi dell'allievo che segue l'allenatore, nella sua scheda: valutazione tecnica, test fisici, classifica e
 * tornei in programma, risultati scritti a mano (partite giocate fuori da questo telefono).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StudentProgressSections(c: MatchController, st: PlayerProfile) {
    val s = LocalStrings.current
    val book by c.coachBook.collectAsState()
    val lang = c.options.collectAsState().value.lang
    var evalOpen by remember { mutableStateOf<Evaluation?>(null) }
    var testOpen by remember { mutableStateOf<FitnessTest?>(null) }
    var matchOpen by remember { mutableStateOf<ManualMatch?>(null) }
    var eventOpen by remember { mutableStateOf<StudentEvent?>(null) }
    var rankEdit by remember { mutableStateOf(false) }
    val now = System.currentTimeMillis()

    // Valutazione tecnica: l'ultima con la differenza dalla precedente; tenendo premuto la si apre per cambiarla.
    SectionCard(s.evTitle, Icons.Filled.Grading) {
        val ev = Coaching.evaluationsOf(book, st.id)
        val last = ev.lastOrNull()
        val before = ev.getOrNull(ev.size - 2)
        if (last == null) Text(s.evNone, color = TsmColors.TextDim, fontSize = 13.sp)
        else {
            Text(s.evLast(shortDate(last.at, lang)) + (Coaching.trend(book, st.id)?.let { t -> " · " + if (t >= 0) s.evTrendUp else s.evTrendDown } ?: ""),
                color = TsmColors.Ball, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                modifier = Modifier.combinedClickable(onClick = { evalOpen = last }, onLongClick = { evalOpen = last }))
            for (k in Coaching.ASPECTS) {
                val v = last.scores[k] ?: continue
                val d = before?.scores?.get(k)?.let { v - it }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(aspectLabel(s, k), color = TsmColors.TextMain, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Text("●".repeat(v) + "○".repeat(5 - v), color = TsmColors.Ball, fontSize = 14.sp)
                    if (d != null && d != 0) Text(" " + (if (d > 0) "+$d" else "$d"), color = if (d > 0) TsmColors.Ok else TsmColors.Orange, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (last.note.isNotBlank()) Text(last.note, color = TsmColors.TextDim, fontSize = 13.sp)
        }
        GhostButton(s.evNew, Icons.Filled.Add, { evalOpen = Evaluation(Coaching.newId("e"), st.id, now, last?.scores.orEmpty()) }, Modifier.fillMaxWidth())
    }

    // Test fisici: l'ultimo valore di ogni test e se è migliorato.
    SectionCard(s.ftTitle, Icons.Filled.DirectionsRun) {
        val tests = Coaching.testsOf(book, st.id)
        if (tests.isEmpty()) Text(s.ftNone, color = TsmColors.TextDim, fontSize = 13.sp)
        for ((k, lowerBetter) in Coaching.TESTS) {
            val (v, prev) = Coaching.lastTwo(tests, k)
            if (v == null) continue
            val better = prev?.let { if (lowerBetter) v < it else v > it }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(testLabel(s, k), color = TsmColors.TextMain, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text(testValue(k, v), color = TsmColors.TextMain, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                if (prev != null && prev != v) Text(" (${testValue(k, prev)})", color = if (better == true) TsmColors.Ok else TsmColors.Orange, fontSize = 13.sp)
            }
        }
        tests.lastOrNull()?.let { Text(shortDate(it.at, lang), color = TsmColors.TextDim, fontSize = 12.sp, modifier = Modifier.combinedClickable(onClick = { testOpen = it }, onLongClick = { testOpen = it })) }
        GhostButton(s.ftNew, Icons.Filled.Add, { testOpen = FitnessTest(Coaching.newId("f"), st.id, now) }, Modifier.fillMaxWidth())
    }

    // Classifica (con la storia) e tornei in programma.
    SectionCard(s.seTitle, Icons.Filled.Leaderboard) {
        val rk = book.rankings.filter { it.student == st.id }.sortedBy { it.at }
        Text(rk.lastOrNull()?.let { s.rkNow(it.ranking) } ?: s.rkNone, color = TsmColors.TextMain, fontWeight = FontWeight.Bold)
        if (rk.size > 1) Text(rk.takeLast(5).joinToString(" → ") { it.ranking }, color = TsmColors.TextDim, fontSize = 13.sp)
        TextButton(onClick = { rankEdit = true }) { Text(s.rkUpdate, color = TsmColors.Ball) }
        val events = book.events.filter { it.student == st.id && it.at >= now - 86_400_000L }.sortedBy { it.at }
        if (events.isEmpty()) Text(s.seNone, color = TsmColors.TextDim, fontSize = 13.sp)
        for (e in events) Column(Modifier.fillMaxWidth().combinedClickable(onClick = { eventOpen = e }, onLongClick = { eventOpen = e }).padding(vertical = 4.dp)) {
            Text(e.name, color = TsmColors.TextMain, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(listOf(shortDate(e.at, lang), e.place, e.note).filter { it.isNotBlank() }.joinToString(" · "), color = TsmColors.TextDim, fontSize = 13.sp)
        }
        GhostButton(s.seAdd, Icons.Filled.Add, { eventOpen = StudentEvent(Coaching.newId("t"), st.id, "", now + 7 * 86_400_000L) }, Modifier.fillMaxWidth())
    }

    // Risultati di partite giocate fuori da questo telefono.
    SectionCard(s.mmTitle, Icons.Filled.EmojiEvents) {
        val list = book.manualMatches.filter { it.student == st.id }.sortedByDescending { it.at }
        if (list.isNotEmpty()) Text(s.studentWonLost(list.count { it.won }, list.count { !it.won }), color = TsmColors.TextMain, fontWeight = FontWeight.Bold)
        for (m in list.take(8)) Row(Modifier.fillMaxWidth().combinedClickable(onClick = { matchOpen = m }, onLongClick = { matchOpen = m }).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(listOf(m.opponent + (m.opponentRanking.ifBlank { null }?.let { " ($it)" } ?: ""), m.score).filter { it.isNotBlank() }.joinToString(" · "),
                    color = TsmColors.TextMain, fontSize = 14.sp)
                Text(listOf(shortDate(m.at, lang), m.event).filter { it.isNotBlank() }.joinToString(" · "), color = TsmColors.TextDim, fontSize = 12.sp)
            }
            Text(if (m.won) s.studentWon else s.studentLost, color = if (m.won) TsmColors.Ok else TsmColors.Danger, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        GhostButton(s.mmAdd, Icons.Filled.Add, { matchOpen = ManualMatch(Coaching.newId("m"), st.id, now) }, Modifier.fillMaxWidth())
    }

    evalOpen?.let { e -> EvaluationDialog(c, e, isNew = book.evaluations.none { it.id == e.id }) { evalOpen = null } }
    testOpen?.let { x -> FitnessDialog(c, x, isNew = book.tests.none { it.id == x.id }) { testOpen = null } }
    matchOpen?.let { m -> ManualMatchDialog(c, m, isNew = book.manualMatches.none { it.id == m.id }) { matchOpen = null } }
    eventOpen?.let { e -> EventDialog(c, e, isNew = book.events.none { it.id == e.id }) { eventOpen = null } }
    if (rankEdit) {
        var v by remember { mutableStateOf(Coaching.ranking(book, st.id).orEmpty()) }
        AlertDialog(
            onDismissRequest = { rankEdit = false },
            title = { Text(s.rkUpdate) },
            text = {
                Column {
                    OutlinedTextField(v, { v = it.take(10) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(s.rkTitle) })
                    Text(s.rkHint, color = TsmColors.TextDim, fontSize = 12.sp)
                }
            },
            confirmButton = { TextButton(onClick = { c.addRanking(st.id, v); rankEdit = false }) { Text(s.save) } },
            dismissButton = { TextButton(onClick = { rankEdit = false }) { Text(s.back) } },
        )
    }
}

@Composable
private fun EvaluationDialog(c: MatchController, e0: Evaluation, isNew: Boolean, onDone: () -> Unit) {
    val s = LocalStrings.current
    var e by remember { mutableStateOf(e0) }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(s.evTitle) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (k in Coaching.ASPECTS) {
                    Text(aspectLabel(s, k), color = TsmColors.TextMain, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Segmented((1..5).map { SegOption("$it") }, (e.scores[k] ?: 0) - 1, { i -> e = e.copy(scores = e.scores + (k to i + 1)) }, columns = 5)
                }
                OutlinedTextField(e.note, { e = e.copy(note = it.take(500)) }, Modifier.fillMaxWidth(), label = { Text(s.evNote) })
                if (!isNew) TextButton(onClick = { c.deleteEvaluation(e.id); onDone() }) { Text(s.delete, color = TsmColors.Danger) }
            }
        },
        confirmButton = { TextButton(enabled = e.scores.isNotEmpty(), onClick = { c.saveEvaluation(e); onDone() }) { Text(s.save) } },
        dismissButton = { TextButton(onClick = onDone) { Text(s.back) } },
    )
}

@Composable
private fun FitnessDialog(c: MatchController, x0: FitnessTest, isNew: Boolean, onDone: () -> Unit) {
    val s = LocalStrings.current
    val texts = remember { mutableStateOf(Coaching.TESTS.associate { (k, _) -> k to (x0.values[k]?.let { testValue(k, it) } ?: "") }) }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(s.ftTitle) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for ((k, _) in Coaching.TESTS) OutlinedTextField(
                    texts.value[k].orEmpty(), { v -> texts.value = texts.value + (k to v.take(8)) }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text(testLabel(s, k)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                if (!isNew) TextButton(onClick = { c.deleteFitnessTest(x0.id); onDone() }) { Text(s.delete, color = TsmColors.Danger) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val values = texts.value.mapNotNull { (k, v) -> v.replace(',', '.').toDoubleOrNull()?.let { k to it } }.toMap()
                if (values.isNotEmpty()) c.saveFitnessTest(x0.copy(values = values))
                onDone()
            }) { Text(s.save) }
        },
        dismissButton = { TextButton(onClick = onDone) { Text(s.back) } },
    )
}

@Composable
private fun ManualMatchDialog(c: MatchController, m0: ManualMatch, isNew: Boolean, onDone: () -> Unit) {
    val s = LocalStrings.current
    val lang = c.options.collectAsState().value.lang
    var m by remember { mutableStateOf(m0) }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(s.mmAdd) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                com.tennis.scoremanager.ui.screens.DateButton(s.payDate, m.at, lang) { d -> m = m.copy(at = d) }
                OutlinedTextField(m.event, { m = m.copy(event = it.take(60)) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(s.mmEvent) })
                OutlinedTextField(m.opponent, { m = m.copy(opponent = it.take(60)) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(s.mmOpponent) })
                OutlinedTextField(m.opponentRanking, { m = m.copy(opponentRanking = it.take(10)) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(s.mmOppRank) })
                OutlinedTextField(m.score, { v ->
                    m = m.copy(score = v.take(40))
                    com.tennis.scoremanager.data.ClubTournaments.winnerFromScore(v)?.let { w -> m = m.copy(won = w == "a") }
                }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(s.mmScore) })
                Segmented(listOf(SegOption(s.studentWon), SegOption(s.studentLost)), if (m.won) 0 else 1, { m = m.copy(won = it == 0) })
                if (!isNew) TextButton(onClick = { c.deleteManualMatch(m.id); onDone() }) { Text(s.delete, color = TsmColors.Danger) }
            }
        },
        confirmButton = { TextButton(enabled = m.opponent.isNotBlank() || m.score.isNotBlank(), onClick = { c.saveManualMatch(m); onDone() }) { Text(s.save) } },
        dismissButton = { TextButton(onClick = onDone) { Text(s.back) } },
    )
}

@Composable
private fun EventDialog(c: MatchController, e0: StudentEvent, isNew: Boolean, onDone: () -> Unit) {
    val s = LocalStrings.current
    val lang = c.options.collectAsState().value.lang
    var e by remember { mutableStateOf(e0) }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(s.seAdd) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(e.name, { e = e.copy(name = it.take(60)) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(s.seName) })
                com.tennis.scoremanager.ui.screens.DateButton(s.payDate, e.at, lang) { d -> e = e.copy(at = d) }
                OutlinedTextField(e.place, { e = e.copy(place = it.take(60)) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(s.sePlace) })
                OutlinedTextField(e.note, { e = e.copy(note = it.take(200)) }, Modifier.fillMaxWidth(), label = { Text(s.coNote) })
                if (!isNew) TextButton(onClick = { c.deleteStudentEvent(e.id); onDone() }) { Text(s.delete, color = TsmColors.Danger) }
            }
        },
        confirmButton = { TextButton(enabled = e.name.isNotBlank(), onClick = { c.saveStudentEvent(e); onDone() }) { Text(s.save) } },
        dismissButton = { TextButton(onClick = onDone) { Text(s.back) } },
    )
}
