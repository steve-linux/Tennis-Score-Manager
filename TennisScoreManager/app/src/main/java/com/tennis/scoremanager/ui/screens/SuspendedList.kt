// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tennis.scoremanager.MatchController
import com.tennis.scoremanager.data.MatchRecord
import com.tennis.scoremanager.data.PlayerStats
import com.tennis.scoremanager.data.Reports
import com.tennis.scoremanager.model.ScoreEngine
import com.tennis.scoremanager.model.Side
import com.tennis.scoremanager.ui.GhostButton
import com.tennis.scoremanager.ui.LocalStrings
import com.tennis.scoremanager.ui.TsmColors

/**
 * Storico › Sospese: tutte le partite sospese, dalla più recente, con il punteggio a cui ci si è fermati. Si riprende
 * una partita o la si elimina; spuntandone più d'una si eliminano insieme (sempre con la conferma).
 */
@Composable
fun SuspendedList(c: MatchController) {
    val s = LocalStrings.current
    val saved by c.saved.collectAsState()
    val lang = c.options.value.lang
    val list = saved.sortedByDescending { it.updatedAt }
    var picked by remember { mutableStateOf(setOf<String>()) }
    var ask by remember { mutableStateOf<Set<String>?>(null) }
    // Le spunte di partite che non ci sono più (eliminate) si tolgono.
    val ids = list.map { it.id }.toSet()
    if (!ids.containsAll(picked)) picked = picked intersect ids
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (list.isEmpty()) { Text(s.susNone, color = TsmColors.TextDim); return@Column }
        Text(s.historySuspended(list.size), color = TsmColors.TextDim, fontSize = 13.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { picked = ids }) { Text(s.transferPickAll, color = TsmColors.Ball) }
            TextButton(onClick = { picked = emptySet() }) { Text(s.transferPickNone, color = TsmColors.Ball) }
        }
        if (picked.isNotEmpty()) GhostButton(s.susDeleteN(picked.size), Icons.Filled.Delete, { ask = picked }, Modifier.fillMaxWidth())
        for (rec in list) {
            val on = rec.id in picked
            val score = remember(rec.id, rec.updatedAt) {
                runCatching { Reports.scoreLine(ScoreEngine.replay(rec.rules, rec.events), Side.P1) }.getOrDefault("")
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TsmColors.Surface).clickable { picked = if (on) picked - rec.id else picked + rec.id }.padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = on, onCheckedChange = { picked = if (it) picked + rec.id else picked - rec.id })
                Column(Modifier.weight(1f)) {
                    Text(names(rec), color = TsmColors.TextMain, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    val at = rec.startedAt ?: rec.updatedAt
                    Text(listOfNotNull("${Reports.date(at, lang)} ${Reports.time(at, lang)}", score.ifBlank { null }, rec.setup.event.ifBlank { null }).joinToString(" · "),
                        color = TsmColors.TextDim, fontSize = 12.sp)
                }
                IconButton(onClick = { c.resumeSaved(rec) }) { Icon(Icons.Filled.PlayArrow, s.resume, tint = TsmColors.Ball) }
                IconButton(onClick = { ask = setOf(rec.id) }) { Icon(Icons.Filled.Delete, s.delete, tint = TsmColors.TextDim) }
            }
        }
    }
    ask?.let { set ->
        val one = list.firstOrNull { it.id == set.singleOrNull() }
        AlertDialog(
            onDismissRequest = { ask = null },
            title = { Text(s.deleteSavedTitle) },
            text = { Text(if (one != null) s.deleteSavedText(names(one)) else s.susDeleteAsk(set.size)) },
            confirmButton = { TextButton(onClick = { c.deleteSaved(set); picked = picked - set; ask = null }) { Text(s.delete, color = TsmColors.Danger) } },
            dismissButton = { TextButton(onClick = { ask = null }) { Text(s.back) } },
        )
    }
}

private fun names(rec: MatchRecord): String = Side.entries.joinToString(" – ") { PlayerStats.names(rec.setup, it).joinToString(" / ").ifEmpty { "?" } }
