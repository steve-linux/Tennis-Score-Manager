// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tennis.scoremanager.MatchController

/** Una partita del circolo pronta da segnare: dove sta (torneo, incontro, sfida), chi gioca e come si avvia. */
class ClubMatchReady(val event: String, val round: String, val players: String, val play: () -> Unit)

/**
 * Le partite di tornei, squadre e sfide del circolo pronte da giocare e non ancora iniziate: per il ruolo Tu (la
 * segreteria non segna punteggi). Il risultato torna da solo nel tabellone, nell'incontro o nella classifica.
 */
@Composable
fun clubMatchesReady(c: MatchController): List<ClubMatchReady> {
    val tournaments by c.clubTournaments.collectAsState()
    val teams by c.teams.collectAsState()
    val ladders by c.ladders.collectAsState()
    val s = LocalStrings.current
    return buildList {
        for (t in tournaments) for (m in t.matches) {
            if (m.played || m.a == null || m.b == null || m.record != null) continue
            add(ClubMatchReady(t.name, c.clubRoundName(t, m), "${c.entryNameOf(t, m.a)} – ${c.entryNameOf(t, m.b)}") { c.playClubMatch(t, m) })
        }
        for (t in teams) for (f in t.fixtures) f.rubbers.forEachIndexed { i, r ->
            val need = if (r.doubles) 2 else 1
            if (r.ours.size != need || r.won != null || r.walkover || r.record != null) return@forEachIndexed
            val ours = r.ours.joinToString(" / ") { c.playerById(it)?.name.orEmpty() }
            val theirs = r.theirs.filter { it.isNotBlank() }.joinToString(" / ").ifBlank { f.opponent }
            add(ClubMatchReady("${t.name} – ${f.opponent}", c.rubberName(t, i), "$ours – $theirs") { c.playTeamRubber(t, f, i) })
        }
        for (l in ladders) for (ch in l.challenges) {
            if (!ch.open || ch.record != null) continue
            val a = c.playerById(ch.challenger)?.name.orEmpty(); val b = c.playerById(ch.defender)?.name.orEmpty()
            add(ClubMatchReady(l.name, s.ldTitle, "$a – $b") { c.playChallenge(l, ch) })
        }
    }
}

@Composable
fun ClubMatchesDialog(c: MatchController, ready: List<ClubMatchReady>, onDone: () -> Unit) {
    val s = LocalStrings.current
    AlertDialog(
        onDismissRequest = onDone,
        icon = { Icon(Icons.Filled.EmojiEvents, null, tint = TsmColors.Ball) },
        title = { Text(s.cmTitle) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(s.cmPick, color = TsmColors.TextDim, fontSize = 13.sp)
                for (m in ready) Column(Modifier.fillMaxWidth().clickable { onDone(); m.play() }.padding(vertical = 8.dp)) {
                    Text(m.players, color = TsmColors.TextMain, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("${m.event} · ${m.round}", color = TsmColors.TextDim, fontSize = 13.sp)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text(s.back) } },
    )
}
