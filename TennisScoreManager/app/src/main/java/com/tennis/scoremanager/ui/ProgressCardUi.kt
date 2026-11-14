// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Grading
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tennis.scoremanager.MatchController
import com.tennis.scoremanager.data.Coaching
import com.tennis.scoremanager.data.ProgressCard
import com.tennis.scoremanager.data.Reports
import com.tennis.scoremanager.ui.screens.shortDate

/** È arrivata la scheda progressi dall'allenatore: chi la manda, di chi è, e la si salva nei propri progressi. */
@Composable
fun IncomingProgressDialog(c: MatchController, card: ProgressCard) {
    val s = LocalStrings.current
    val lang = c.options.collectAsState().value.lang
    AlertDialog(
        onDismissRequest = { c.incomingProgress.value = null },
        icon = { Icon(Icons.Filled.Grading, null, tint = TsmColors.Ball) },
        title = { Text(s.pcTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(card.studentName, color = TsmColors.TextMain, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(listOfNotNull(card.coach.ifBlank { null }?.let { s.pcFrom(it) }, card.club.ifBlank { null }, s.pcUpdated(shortDate(card.issuedAt, lang))).joinToString(" · "),
                    color = TsmColors.TextDim, fontSize = 13.sp)
                Text(s.pcHint, color = TsmColors.TextDim, fontSize = 13.sp)
            }
        },
        confirmButton = { TextButton(onClick = { c.acceptProgress(card) }) { Text(s.pcSave) } },
        dismissButton = { TextButton(onClick = { c.incomingProgress.value = null }) { Text(s.back) } },
    )
}

/** Progressi (ruolo Tu): l'ultima scheda dell'allenatore, con valutazione, obiettivi, test, classifica e lezioni. */
@Composable
fun MyCoachSection(c: MatchController) {
    val s = LocalStrings.current
    val card by c.myProgress.collectAsState()
    val p = card ?: return
    val lang = c.options.collectAsState().value.lang
    SectionCard(s.pcSection, Icons.Filled.Grading) {
        Text(listOfNotNull(p.coach.ifBlank { null }?.let { s.pcFrom(it) }, s.pcUpdated(shortDate(p.issuedAt, lang))).joinToString(" · "), color = TsmColors.TextDim, fontSize = 13.sp)
        val last = p.evaluations.lastOrNull()
        val prev = p.evaluations.getOrNull(p.evaluations.size - 2)
        if (last != null) {
            Text(s.evLast(shortDate(last.at, lang)), color = TsmColors.Ball, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            for (k in Coaching.ASPECTS) {
                val v = last.scores[k] ?: continue
                val d = prev?.scores?.get(k)?.let { v - it }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(aspectLabel(s, k), color = TsmColors.TextMain, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Text("●".repeat(v) + "○".repeat(5 - v), color = TsmColors.Ball, fontSize = 14.sp)
                    if (d != null && d != 0) Text(" " + (if (d > 0) "+$d" else "$d"), color = if (d > 0) TsmColors.Ok else TsmColors.Orange, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (last.note.isNotBlank()) Text(last.note, color = TsmColors.TextMain, fontSize = 13.sp)
        }
        if (p.goals.isNotEmpty()) {
            Text(s.coGoals, color = TsmColors.Ball, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            for (g in p.goals) Text(s.coGoalLine(strokeLabel(s, g.stroke), g.target) + " · " +
                (g.reachedAt?.let { s.coGoalReached(shortDate(it, lang)) } ?: g.now?.let { "$it%" } ?: s.coGoalNoData),
                color = if (g.reachedAt != null) TsmColors.Ok else TsmColors.TextMain, fontSize = 14.sp)
        }
        val tests = p.tests
        if (tests.isNotEmpty()) {
            Text(s.ftTitle, color = TsmColors.Ball, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            for ((k, lowerBetter) in Coaching.TESTS) {
                val (v, before) = Coaching.lastTwo(tests, k)
                if (v == null) continue
                val better = before?.let { if (lowerBetter) v < it else v > it }
                Text("${testLabel(s, k)}: ${testValue(k, v)}" + (before?.takeIf { it != v }?.let { " (${testValue(k, it)})" } ?: ""),
                    color = if (better == true) TsmColors.Ok else TsmColors.TextMain, fontSize = 14.sp)
            }
        }
        p.rankings.lastOrNull()?.let { Text(s.rkNow(it.ranking) + if (p.rankings.size > 1) "  (" + p.rankings.takeLast(4).joinToString(" → ") { r -> r.ranking } + ")" else "", color = TsmColors.TextMain, fontSize = 14.sp) }
        for (e in p.events.filter { it.at >= System.currentTimeMillis() - 86_400_000L }) Text("${shortDate(e.at, lang)} · ${e.name}" + (e.place.ifBlank { null }?.let { " · $it" } ?: ""), color = TsmColors.TextMain, fontSize = 14.sp)
        p.nextLessons.firstOrNull { it > System.currentTimeMillis() }?.let { Text(s.pcNext("${Reports.date(it, lang)} ${Reports.time(it, lang)}"), color = TsmColors.TextMain, fontSize = 14.sp) }
        p.lessonsLeft?.let { Text(s.pcLessonsLeft(it), color = if (it <= 1) TsmColors.Orange else TsmColors.TextDim, fontSize = 13.sp) }
    }
}
