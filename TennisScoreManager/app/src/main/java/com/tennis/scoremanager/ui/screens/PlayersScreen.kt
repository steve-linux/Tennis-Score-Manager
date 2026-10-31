// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tennis.scoremanager.MatchController
import com.tennis.scoremanager.data.Players
import com.tennis.scoremanager.ui.GhostButton
import com.tennis.scoremanager.ui.LocalStrings
import com.tennis.scoremanager.ui.PlayerAvatar
import com.tennis.scoremanager.ui.ScreenScaffold
import com.tennis.scoremanager.ui.TsmColors

/**
 * Altro › Giocatori: il database di tutti, chi ha giocato e gli allievi, con la ricerca. Dalla scheda di ciascuno si
 * uniscono due giocatori che sono la stessa persona e si separano gli omonimi.
 */
@Composable
fun PlayersScreen(c: MatchController) {
    val s = LocalStrings.current
    BackHandler { c.back() }
    val players by c.players.collectAsState()
    val history by c.history.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    val list = if (query.isBlank()) players else Players.search(players, query)
    // Gli omonimi si vedono subito: stesso nome, più giocatori.
    val twins = remember(players) { players.groupBy { com.tennis.scoremanager.data.PlayerStats.key(it.name) }.filterValues { it.size > 1 }.keys }
    val hints = remember(players, history) { players.associate { it.id to c.playerHint(it) } }
    ScreenScaffold(
        title = s.playersTitle,
        subtitle = s.playersHint,
        icon = Icons.Filled.Groups,
        bottomBar = { GhostButton(s.back, Icons.AutoMirrored.Filled.ArrowBack, { c.back() }, Modifier.weight(1f)) },
    ) {
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(s.playersSearch) }, leadingIcon = { Icon(Icons.Filled.Search, null) })
        if (players.isEmpty()) Text(s.playersNone, color = TsmColors.TextDim)
        for (p in list) {
            Row(Modifier.fillMaxWidth().clickable { c.openStudent(p.id) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                PlayerAvatar(c, p.name, TsmColors.Ball, 44.dp)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(p.name, color = TsmColors.TextMain, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        if (p.student) Icon(Icons.Filled.School, s.studentsTitle, tint = TsmColors.Ball, modifier = Modifier.padding(start = 6.dp).size(16.dp))
                    }
                    // Nome e cognome per esteso sotto il soprannome; da completare in arancione.
                    p.fullName.takeIf { it.isNotBlank() && it != p.name }?.let { Text(it, color = TsmColors.TextDim, fontSize = 13.sp) }
                    val warn = !p.complete || com.tennis.scoremanager.data.PlayerStats.key(p.name) in twins
                    val hint = listOfNotNull(
                        hints[p.id], s.playerTwin.takeIf { com.tennis.scoremanager.data.PlayerStats.key(p.name) in twins }, s.playerIncomplete.takeIf { !p.complete },
                    ).joinToString(" · ")
                    if (hint.isNotEmpty()) Text(hint, color = if (warn) TsmColors.Orange else TsmColors.TextDim, fontSize = 13.sp)
                }
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = TsmColors.TextDim)
            }
        }
    }
}
