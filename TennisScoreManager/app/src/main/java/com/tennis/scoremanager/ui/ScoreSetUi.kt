// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tennis.scoremanager.data.Names
import com.tennis.scoremanager.model.MatchState
import com.tennis.scoremanager.model.ScoreEngine
import com.tennis.scoremanager.model.SetScore
import com.tennis.scoremanager.model.Side

/** Un numero con − e +, da 0 a [max]. */
@Composable
private fun Counter(value: Int, max: Int, color: androidx.compose.ui.graphics.Color, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onChange((value - 1).coerceAtLeast(0)) }, enabled = value > 0) { Icon(Icons.Filled.Remove, null) }
        Text(value.toString(), color = color, fontSize = 22.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, modifier = Modifier.width(36.dp))
        IconButton(onClick = { onChange((value + 1).coerceAtMost(max)) }, enabled = value < max) { Icon(Icons.Filled.Add, null) }
    }
}

/** I punti del game come si dicono: 0 15 30 40 AD (AD solo contro 40). */
private val pointLabels = listOf("0", "15", "30", "40", "AD")

/**
 * Imposta punteggio: i set finiti, i game del set in corso, i punti e chi serve. Parte dal punteggio di adesso; il tasto
 * di conferma si accende solo con un punteggio che si può giocare con le regole della partita.
 */
@Composable
fun ScoreSetDialog(state: MatchState, names: Names, onSet: (List<SetScore>, Int, Int, Int, Int, Side, Int) -> Unit, onDismiss: () -> Unit) {
    val s = LocalStrings.current
    val rules = state.rules
    var sets by remember { mutableStateOf(state.sets.filter { !it.partial }) }
    var g1 by remember { mutableIntStateOf(state.g1) }
    var g2 by remember { mutableIntStateOf(state.g2) }
    // Ai vantaggi i punti veri (es. 5-4) diventano come si dicono: 40 pari o AD.
    val deuce = !state.inTiebreak && state.pt1 >= 3 && state.pt2 >= 3
    var pt1 by remember { mutableIntStateOf(if (deuce) (if (state.pt1 > state.pt2) 4 else 3) else state.pt1) }
    var pt2 by remember { mutableIntStateOf(if (deuce) (if (state.pt2 > state.pt1) 4 else 3) else state.pt2) }
    var server by remember { mutableStateOf(state.server) }
    var serverPlayer by remember { mutableIntStateOf(state.serverPlayer) }
    val matchTb = ScoreEngine.matchTiebreakSet(rules, sets.size)
    val tb = matchTb || ScoreEngine.tiebreakAt(rules, g1, g2)
    val maxGames = rules.gamesPerSet + if (rules.setTiebreak) 1 else 30
    val ok = ScoreEngine.validScore(rules, sets, g1, g2, pt1, pt2)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s.scoreSetButton, fontWeight = FontWeight.Black) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(s.scoreSetHint, color = TsmColors.TextDim, fontSize = 13.sp)
                Text(names.short(Side.P1) + " – " + names.short(Side.P2), fontWeight = FontWeight.Bold)
                // I set finiti, uno per riga.
                Text(s.scoreSetDone, color = TsmColors.TextDim, fontSize = 13.sp)
                for ((i, set) in sets.withIndex()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Counter(set.g1, maxGames, TsmColors.player(Side.P1)) { v -> sets = sets.mapIndexed { k, x -> if (k == i) x.copy(g1 = v) else x } }
                        Counter(set.g2, maxGames, TsmColors.player(Side.P2)) { v -> sets = sets.mapIndexed { k, x -> if (k == i) x.copy(g2 = v) else x } }
                        IconButton(onClick = { sets = sets.filterIndexed { k, _ -> k != i } }) { Icon(Icons.Filled.Close, null, tint = TsmColors.TextDim) }
                    }
                }
                if (sets.size < rules.setsToWin * 2 - 2) TextButton(onClick = { sets = sets + SetScore(rules.gamesPerSet, 0) }) { Text(s.scoreSetAdd) }
                if (!matchTb) {
                    Text(s.scoreSetGames, color = TsmColors.TextDim, fontSize = 13.sp)
                    Row {
                        Counter(g1, maxGames, TsmColors.player(Side.P1)) { g1 = it }
                        Counter(g2, maxGames, TsmColors.player(Side.P2)) { g2 = it }
                    }
                }
                Text(if (tb) s.scoreSetTbPoints else s.scoreSetPoints, color = TsmColors.TextDim, fontSize = 13.sp)
                if (tb) {
                    Row {
                        Counter(pt1, 99, TsmColors.player(Side.P1)) { pt1 = it }
                        Counter(pt2, 99, TsmColors.player(Side.P2)) { pt2 = it }
                    }
                } else {
                    // 0 15 30 40 AD per ciascuno: AD vale solo contro 40.
                    val labels = if (rules.noAd) pointLabels.take(4) else pointLabels
                    for ((side, value) in listOf(Side.P1 to pt1, Side.P2 to pt2)) {
                        Segmented(labels.map { SegOption(it, null, TsmColors.player(side), TsmColors.onPlayer(side)) }, value.coerceAtMost(labels.lastIndex), { v ->
                            if (side == Side.P1) { pt1 = v; if (v == 4) pt2 = 3 else if (pt2 == 4 && v < 3) pt2 = 3 } else { pt2 = v; if (v == 4) pt1 = 3 else if (pt1 == 4 && v < 3) pt1 = 3 }
                        })
                    }
                }
                Text(s.scoreSetServer, color = TsmColors.TextDim, fontSize = 13.sp)
                for (side in Side.entries) for ((i, name) in names.players(side).withIndex()) {
                    val on = server == side && (!rules.doubles || serverPlayer == i)
                    OutlinedButton(onClick = { server = side; serverPlayer = i }, modifier = Modifier.fillMaxWidth()) {
                        Text(name + if (on) "  ✓" else "", color = TsmColors.player(side), fontWeight = if (on) FontWeight.Black else FontWeight.Normal)
                    }
                }
                if (!ok) Text(s.scoreSetBad, color = TsmColors.Orange, fontSize = 13.sp)
            }
        },
        confirmButton = { Button(onClick = { onSet(sets, g1, g2, pt1, pt2, server, serverPlayer) }, enabled = ok) { Text(s.confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.back) } },
    )
}
