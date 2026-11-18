// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsTennis
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tennis.scoremanager.MatchController
import com.tennis.scoremanager.data.Challenge
import com.tennis.scoremanager.data.ClubTournaments
import com.tennis.scoremanager.data.Ladder
import com.tennis.scoremanager.data.Ladders
import com.tennis.scoremanager.ui.BigButton
import com.tennis.scoremanager.ui.GhostButton
import com.tennis.scoremanager.ui.LocalStrings
import com.tennis.scoremanager.ui.ScreenScaffold
import com.tennis.scoremanager.ui.SectionCard
import com.tennis.scoremanager.ui.SegOption
import com.tennis.scoremanager.ui.Segmented
import com.tennis.scoremanager.ui.TsmColors

/** Le classifiche a sfida del circolo (anche più di una: maschile, femminile, under…). */
@Composable
fun LaddersScreen(c: MatchController) {
    val s = LocalStrings.current
    BackHandler { c.back() }
    val list by c.ladders.collectAsState()
    var create by remember { mutableStateOf(false) }
    ScreenScaffold(
        onHelp = { c.openHelp("7.19") },
        title = s.ldTitle, subtitle = s.ldHint, icon = Icons.Filled.EmojiEvents,
        bottomBar = {
            GhostButton(s.back, Icons.AutoMirrored.Filled.ArrowBack, { c.back() }, Modifier.weight(1f))
            BigButton(s.ldNew, Icons.Filled.Add, { create = true }, Modifier.weight(1f))
        },
    ) {
        if (list.isEmpty()) Text(s.ldNone, color = TsmColors.TextDim)
        for (l in list) Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(TsmColors.Surface).clickable { c.openLadder(l.id) }.padding(14.dp),
        ) {
            Text(l.name, color = TsmColors.TextMain, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            val first = l.players.firstOrNull()?.let { c.playerById(it)?.name }
            Text(listOfNotNull(s.ldPlayers(l.players.size), first?.let { "1° $it" }, s.ldOpenCount(l.challenges.count { it.open }).takeIf { l.challenges.any { it.open } }).joinToString(" · "),
                color = TsmColors.TextDim, fontSize = 13.sp)
        }
    }
    if (create) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { create = false },
            title = { Text(s.ldNew) },
            text = { OutlinedTextField(name, { name = it }, label = { Text(s.ldName) }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    val l = Ladder(Ladders.newId("l"), name.trim(), createdAt = System.currentTimeMillis())
                    c.saveLadder(l); create = false; c.openLadder(l.id)
                }) { Text(s.ok) }
            },
            dismissButton = { TextButton(onClick = { create = false }) { Text(s.back) } },
        )
    }
}

/**
 * Una classifica: i giocatori in ordine, le sfide da giocare (segnapunti, risultato a mano, avviso, annulla) e quelle
 * giocate. Da qui si aggiungono i giocatori e si sistema a mano la classifica di partenza.
 */
@Composable
fun LadderScreen(c: MatchController) {
    val s = LocalStrings.current
    BackHandler { c.back() }
    val list by c.ladders.collectAsState()
    val id by c.openLadderId.collectAsState()
    val l = list.firstOrNull { it.id == id } ?: run { c.back(); return }
    val lang = c.options.collectAsState().value.lang
    fun name(p: String) = c.playerById(p)?.name ?: "?"
    var arrange by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    var newChallenge by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Challenge?>(null) }
    val now = System.currentTimeMillis()
    ScreenScaffold(
        onHelp = { c.openHelp("7.19") },
        title = l.name, subtitle = s.ldPlayers(l.players.size), icon = Icons.Filled.EmojiEvents,
        action = { IconButton(onClick = { settings = true }) { Icon(Icons.Filled.Settings, s.ldSettings, tint = TsmColors.Ball) } },
        bottomBar = {
            GhostButton(s.back, Icons.AutoMirrored.Filled.ArrowBack, { c.back() }, Modifier.weight(1f))
            BigButton(s.ldChallengeNew, Icons.Filled.SportsTennis, { newChallenge = true }, Modifier.weight(1f), enabled = l.players.size >= 2)
        },
    ) {
        val open = l.challenges.filter { it.open }
        if (open.isNotEmpty()) SectionCard(s.ldOpen, Icons.Filled.SportsTennis) {
            for (ch in open) Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TsmColors.SurfaceHigh).padding(10.dp)) {
                Text(s.ldVs("${name(ch.challenger)} (${Ladders.position(l, ch.challenger)}°)", "${name(ch.defender)} (${Ladders.position(l, ch.defender)}°)"),
                    color = TsmColors.TextMain, fontWeight = FontWeight.Bold)
                Text(if (ch.deadline < now) s.ldLate else s.ldBy(shortDate(ch.deadline, lang)), color = if (ch.deadline < now) TsmColors.Orange else TsmColors.TextDim, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { result = ch }) { Text(s.ldResult) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { c.notifyChallenge(l, ch) }) { Text(s.ldNotify) }
                    TextButton(onClick = { c.saveLadder(Ladders.cancel(l, ch.id)) }) { Text(s.ldCancel, color = TsmColors.Danger) }
                }
            }
        }

        SectionCard(s.ldRanking, Icons.Filled.EmojiEvents) {
            if (l.players.isEmpty()) Text(s.ldEmpty, color = TsmColors.TextDim, fontSize = 13.sp)
            l.players.forEachIndexed { i, p ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}°", color = if (i == 0) TsmColors.Ball else TsmColors.TextDim, fontWeight = FontWeight.Bold, modifier = Modifier.width(40.dp))
                    Text(name(p), color = TsmColors.TextMain, fontWeight = if (i < 3) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f).clickable { c.openStudent(p) })
                    if (Ladders.busy(l, p)) Icon(Icons.Filled.SportsTennis, null, tint = TsmColors.Orange)
                    if (arrange) {
                        IconButton(onClick = { c.saveLadder(Ladders.move(l, p, up = true)) }, enabled = i > 0) { Icon(Icons.Filled.KeyboardArrowUp, null) }
                        IconButton(onClick = { c.saveLadder(Ladders.move(l, p, up = false)) }, enabled = i < l.players.size - 1) { Icon(Icons.Filled.KeyboardArrowDown, null) }
                        IconButton(onClick = { c.saveLadder(Ladders.remove(l, p)) }) { Icon(Icons.Filled.Close, s.ldRemove, tint = TsmColors.Danger) }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton(s.ldAdd, Icons.Filled.PersonAdd, { adding = true }, Modifier.weight(1f))
                GhostButton(if (arrange) s.ldDone else s.ldArrange, Icons.Filled.SwapVert, { arrange = !arrange }, Modifier.weight(1f))
            }
        }

        val played = l.challenges.filter { it.challengerWon != null }.sortedByDescending { it.playedAt ?: 0 }
        if (played.isNotEmpty()) SectionCard(s.ldHistory, Icons.Filled.EmojiEvents) {
            for (ch in played.take(15)) {
                val a = name(ch.challenger); val b = name(ch.defender)
                Text(if (ch.challengerWon == true) s.ldBeats(a, b) else s.ldHolds(b, a), color = TsmColors.TextMain, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(listOfNotNull(ch.score.ifBlank { null }, s.ldMoved(ch.challengerPos, ch.defenderPos).takeIf { ch.challengerWon == true }, ch.playedAt?.let { shortDate(it, lang) }).joinToString(" · "),
                    color = TsmColors.TextDim, fontSize = 12.sp)
            }
        }
    }

    if (adding) PlayerPickDialog(c, l.players.toSet(), s.ldAdd) { p -> adding = false; p?.let { c.saveLadder(Ladders.add(l, listOf(it.id))) } }

    if (newChallenge) {
        var who by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { newChallenge = false },
            title = { Text(if (who == null) s.ldWho else s.ldWhom(l.reach)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val w = who
                    // Prima chi sfida (chi ha qualcuno da sfidare), poi chi può essere sfidato.
                    val options = if (w == null) l.players.filter { Ladders.targets(l, it).isNotEmpty() } else Ladders.targets(l, w)
                    if (options.isEmpty()) Text(s.ldNobody, color = TsmColors.TextDim)
                    for (p in options) Text("${Ladders.position(l, p)}°  ${name(p)}", color = TsmColors.TextMain, fontSize = 16.sp,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable {
                            if (w == null) who = p else { c.saveLadder(Ladders.challenge(l, w, p, System.currentTimeMillis())); newChallenge = false }
                        }.padding(10.dp))
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { if (who != null) who = null else newChallenge = false }) { Text(s.back) } },
        )
    }

    result?.let { ch ->
        var score by remember(ch.id) { mutableStateOf("") }
        val auto = ClubTournaments.winnerFromScore(score)?.let { it == "a" }
        var won by remember(ch.id) { mutableStateOf<Boolean?>(null) }
        val w = won ?: auto
        AlertDialog(
            onDismissRequest = { result = null },
            title = { Text(s.ldResult) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(s.ldVs(name(ch.challenger), name(ch.defender)), color = TsmColors.TextMain, fontWeight = FontWeight.Bold)
                    OutlinedTextField(score, { score = it }, label = { Text(s.ldScore) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Text(s.ldWinner, color = TsmColors.TextDim, fontSize = 13.sp)
                    Segmented(listOf(SegOption(name(ch.challenger)), SegOption(name(ch.defender))), when (w) { true -> 0; false -> 1; null -> -1 }, { won = it == 0 })
                }
            },
            confirmButton = {
                TextButton(enabled = w != null, onClick = { c.saveLadder(Ladders.result(l, ch.id, score, w, System.currentTimeMillis())); result = null }) { Text(s.ok) }
            },
            dismissButton = { TextButton(onClick = { result = null }) { Text(s.back) } },
        )
    }

    if (settings) {
        var nm by remember { mutableStateOf(l.name) }
        var reach by remember { mutableStateOf(l.reach) }
        var days by remember { mutableStateOf(l.days) }
        var del by remember { mutableStateOf(false) }
        @Composable fun stepper(label: String, v: Int, min: Int, max: Int, onChange: (Int) -> Unit) = Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = TsmColors.TextMain, fontSize = 14.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = { onChange((v - 1).coerceAtLeast(min)) }) { Icon(Icons.Filled.Remove, null) }
            Text(v.toString(), color = TsmColors.TextMain, fontWeight = FontWeight.Bold)
            IconButton(onClick = { onChange((v + 1).coerceAtMost(max)) }) { Icon(Icons.Filled.Add, null) }
        }
        AlertDialog(
            onDismissRequest = { settings = false },
            title = { Text(s.ldSettings) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(nm, { nm = it }, label = { Text(s.ldName) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    stepper(s.ldReach, reach, 1, 10) { reach = it }
                    stepper(s.ldDays, days, 1, 60) { days = it }
                    GhostButton(s.ldDelete, Icons.Filled.Close, { del = true }, Modifier.fillMaxWidth())
                }
            },
            confirmButton = { TextButton(enabled = nm.isNotBlank(), onClick = { c.saveLadder(l.copy(name = nm.trim(), reach = reach, days = days)); settings = false }) { Text(s.ok) } },
            dismissButton = { TextButton(onClick = { settings = false }) { Text(s.back) } },
        )
        if (del) AlertDialog(
            onDismissRequest = { del = false },
            text = { Text(s.ldDeleteAsk) },
            confirmButton = { TextButton(onClick = { del = false; settings = false; c.deleteLadder(l.id); c.back() }) { Text(s.ldDelete, color = TsmColors.Danger) } },
            dismissButton = { TextButton(onClick = { del = false }) { Text(s.back) } },
        )
    }
}
