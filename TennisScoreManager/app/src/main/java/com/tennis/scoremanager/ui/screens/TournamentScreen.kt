// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.ui.screens

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SportsTennis
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tennis.scoremanager.MatchController
import com.tennis.scoremanager.Screen
import com.tennis.scoremanager.data.PlayerStats
import com.tennis.scoremanager.data.TMatch
import com.tennis.scoremanager.data.Tournament
import com.tennis.scoremanager.data.TournamentKind
import com.tennis.scoremanager.data.TournamentScoring
import com.tennis.scoremanager.data.Tournaments
import com.tennis.scoremanager.model.Side
import com.tennis.scoremanager.ui.BigButton
import com.tennis.scoremanager.ui.ConfirmDialog
import com.tennis.scoremanager.ui.GhostButton
import com.tennis.scoremanager.ui.LocalStrings
import com.tennis.scoremanager.ui.PlayerAvatar
import com.tennis.scoremanager.ui.ScreenScaffold
import com.tennis.scoremanager.ui.SectionCard
import com.tennis.scoremanager.ui.SegOption
import com.tennis.scoremanager.ui.Segmented
import com.tennis.scoremanager.ui.TsmColors
import kotlinx.coroutines.delay

/**
 * Tornei Americano e Mexicano: si crea il torneo (giocatori, campi, punti o tempo), poi a ogni turno si scrivono i
 * risultati (o si conta una partita punto per punto) e la classifica si aggiorna. Il turno successivo parte quando
 * tutte le partite del turno hanno il risultato.
 */
@Composable
fun TournamentScreen(c: MatchController) {
    val t by c.tournament.collectAsState()
    val cur = t
    if (cur == null) TournamentList(c) else TournamentView(c, cur)
}

// ------------------------------------------------------------------ elenco e creazione

@Composable
private fun TournamentList(c: MatchController) {
    val s = LocalStrings.current
    val all by c.tournaments.collectAsState()
    var creating by rememberSaveable { mutableStateOf(false) }
    BackHandler { if (creating) creating = false else c.go(Screen.SETUP) }
    if (creating || all.isEmpty()) {
        NewTournament(c) { if (all.isEmpty()) c.go(Screen.SETUP) else creating = false }
        return
    }
    ScreenScaffold(
        title = s.tournamentTitle,
        subtitle = s.tournamentSubtitle,
        icon = Icons.Filled.EmojiEvents,
        bottomBar = {
            GhostButton(s.back, Icons.AutoMirrored.Filled.ArrowBack, { c.go(Screen.SETUP) }, Modifier.weight(1f))
            BigButton(s.tournamentNew, Icons.Filled.Add, { creating = true }, Modifier.weight(1f))
        },
    ) {
        all.forEach { t ->
            val leader = Tournaments.standings(t).firstOrNull()?.takeIf { t.rounds.any { r -> r.matches.any { it.played } } }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(TsmColors.Surface).clickable { c.openTournament(t) }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title(t, s), color = TsmColors.TextMain, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text(
                        listOfNotNull(
                            s.tournamentPlayersCount(t.players.size), s.tournamentRound(t.rounds.size),
                            if (t.finished) s.tournamentFinished else null,
                        ).joinToString(" · "),
                        color = TsmColors.TextDim, fontSize = 13.sp,
                    )
                    if (leader != null) Text("1. ${leader.name} · ${leader.points}", color = TsmColors.Ball, fontSize = 13.sp)
                }
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = TsmColors.TextDim)
            }
        }
    }
}

private fun title(t: Tournament, s: com.tennis.scoremanager.ui.Strings): String {
    val kind = if (t.kind == TournamentKind.AMERICANO) s.tournamentAmericano else s.tournamentMexicano
    return if (t.name.isBlank()) kind else "${t.name} · $kind"
}

@Composable
private fun NewTournament(c: MatchController, onCancel: () -> Unit) {
    val s = LocalStrings.current
    var name by rememberSaveable { mutableStateOf("") }
    var mexicano by rememberSaveable { mutableStateOf(false) }
    val players = remember { mutableStateListOf("", "", "", "") }
    var courts by rememberSaveable { mutableIntStateOf(1) }
    var timed by rememberSaveable { mutableStateOf(false) }
    var points by rememberSaveable { mutableIntStateOf(24) }
    var minutes by rememberSaveable { mutableIntStateOf(12) }
    var error by remember { mutableStateOf(false) }
    val names = players.map { it.trim() }.filter { it.isNotEmpty() }
    val valid = names.size >= Tournaments.MIN_PLAYERS && names.map { PlayerStats.key(it) }.toSet().size == names.size
    ScreenScaffold(
        title = s.tournamentNew,
        subtitle = s.tournamentSubtitle,
        icon = Icons.Filled.EmojiEvents,
        bottomBar = {
            GhostButton(s.back, Icons.AutoMirrored.Filled.ArrowBack, onCancel, Modifier.weight(1f))
            BigButton(s.tournamentCreate, Icons.Filled.PlayArrow, {
                if (!valid) error = true
                else c.createTournament(
                    name, if (mexicano) TournamentKind.MEXICANO else TournamentKind.AMERICANO, names,
                    courts, if (timed) TournamentScoring.TIME else TournamentScoring.POINTS, points, minutes,
                )
            }, Modifier.weight(1f))
        },
    ) {
        SectionCard(s.tournamentKindTitle, Icons.Filled.EmojiEvents) {
            OutlinedTextField(name, { name = it.take(40) }, label = { Text(s.tournamentName) }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
            Segmented(listOf(SegOption(s.tournamentAmericano), SegOption(s.tournamentMexicano)), selected = if (mexicano) 1 else 0,
                onSelect = { mexicano = it == 1 })
            Text(if (mexicano) s.tournamentMexicanoHint else s.tournamentAmericanoHint, color = TsmColors.TextDim)
        }
        SectionCard(s.tournamentPlayers, Icons.Filled.Groups) {
            Text(s.tournamentPlayersHint, color = TsmColors.TextDim, fontSize = 13.sp)
            players.forEachIndexed { i, p ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlayerAvatar(c, p, TsmColors.Ball, 36.dp)
                    OutlinedTextField(p, { players[i] = it.take(30) }, label = { Text("${s.playerName} ${i + 1}") }, singleLine = true,
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
                    if (players.size > Tournaments.MIN_PLAYERS) {
                        IconButton(onClick = { players.removeAt(i) }) { Icon(Icons.Filled.Close, s.delete, tint = TsmColors.TextDim) }
                    }
                }
            }
            if (players.size < Tournaments.MAX_PLAYERS) {
                GhostButton(s.tournamentAddPlayer, Icons.Filled.Add, { players.add("") }, Modifier.fillMaxWidth())
            }
            if (error && !valid) Text(s.tournamentNeedPlayers, color = TsmColors.Danger, fontSize = 13.sp)
        }
        SectionCard(s.tournamentCourts, Icons.Filled.SportsTennis) {
            Segmented((1..Tournaments.MAX_COURTS).map { SegOption("$it") }, selected = courts - 1, onSelect = { courts = it + 1 })
            val per = minOf(courts, names.size / 4)
            val rest = names.size - per * 4
            Text(s.tournamentCourtsHint(per, rest), color = TsmColors.TextDim, fontSize = 13.sp)
        }
        SectionCard(s.tournamentScoring, Icons.Filled.Timer) {
            Segmented(listOf(SegOption(s.tournamentScoringPoints), SegOption(s.tournamentScoringTime)), selected = if (timed) 1 else 0,
                onSelect = { timed = it == 1 })
            if (timed) {
                val opts = listOf(10, 12, 15, 20)
                Segmented(opts.map { SegOption(s.tournamentMinutesN(it)) }, selected = opts.indexOf(minutes).coerceAtLeast(0),
                    onSelect = { minutes = opts[it] })
                Text(s.tournamentTimeHint, color = TsmColors.TextDim, fontSize = 13.sp)
            } else {
                val opts = listOf(16, 24, 32)
                Segmented(opts.map { SegOption(s.tournamentPointsN(it)) }, selected = opts.indexOf(points).coerceAtLeast(0),
                    onSelect = { points = opts[it] })
                Text(s.tournamentPointsHint, color = TsmColors.TextDim, fontSize = 13.sp)
            }
            if (!mexicano && names.size >= Tournaments.MIN_PLAYERS) {
                Text(s.tournamentSuggested(Tournaments.suggestedRounds(names.size)), color = TsmColors.TextDim, fontSize = 13.sp)
            }
        }
    }
}

// ------------------------------------------------------------------ torneo aperto

@Composable
private fun TournamentView(c: MatchController, t: Tournament) {
    val s = LocalStrings.current
    val context = LocalContext.current
    /** 0 = turno, 1 = classifica. */
    var tab by rememberSaveable(t.id) { mutableIntStateOf(if (t.finished) 1 else 0) }
    var shownRound by rememberSaveable(t.id) { mutableIntStateOf(0) }
    var confirmFinish by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var missing by remember { mutableStateOf(false) }
    /** Partita contata punto per punto: numero del turno e partita. */
    var counting by remember { mutableStateOf<Pair<Int, TMatch>?>(null) }
    // Si mostra sempre l'ultimo turno quando ne arriva uno nuovo.
    LaunchedEffect(t.rounds.size) { shownRound = t.rounds.size }
    BackHandler { c.closeTournament() }

    ScreenScaffold(
        title = title(t, s),
        subtitle = if (t.scoring == TournamentScoring.POINTS) s.tournamentPointsN(t.pointsPerMatch) else s.tournamentMinutesN(t.minutes),
        icon = Icons.Filled.EmojiEvents,
        bottomBar = {
            GhostButton(s.back, Icons.AutoMirrored.Filled.ArrowBack, { c.closeTournament() }, Modifier.weight(1f))
            if (t.finished) {
                BigButton(s.tournamentShare, Icons.Filled.Share, {
                    c.tournamentShareIntent()?.let { runCatching { context.startActivity(it) } }
                }, Modifier.weight(1f))
            } else {
                BigButton(s.tournamentNextRound, Icons.AutoMirrored.Filled.ArrowForward, {
                    if (!c.nextTournamentRound()) missing = true
                }, Modifier.weight(1f))
            }
        },
    ) {
        Segmented(listOf(SegOption(s.tournamentRounds), SegOption(s.tournamentStandings)), selected = tab, onSelect = { tab = it })
        if (tab == 1) {
            StandingsTable(c, t)
            if (!t.finished) GhostButton(s.tournamentShare, Icons.Filled.Share, {
                c.tournamentShareIntent()?.let { runCatching { context.startActivity(it) } }
            }, Modifier.fillMaxWidth())
        } else {
            if (t.rounds.size > 1) {
                Segmented(t.rounds.map { SegOption("${it.number}") }, selected = (shownRound - 1).coerceIn(0, t.rounds.size - 1),
                    onSelect = { shownRound = it + 1 })
            }
            val r = t.rounds.getOrNull(shownRound - 1) ?: t.current
            if (r != null) {
                Text(s.tournamentRound(r.number), color = TsmColors.TextMain, fontWeight = FontWeight.Black, fontSize = 20.sp)
                r.matches.forEach { m ->
                    MatchCard(c, t, m, editable = !t.finished, onCount = { counting = r.number to m }) { a, b ->
                        c.setTournamentResult(r.number, m.court, a, b)
                    }
                }
                if (r.resting.isNotEmpty()) {
                    Text("${s.tournamentResting}: " + r.resting.joinToString(", ") { t.players[it] }, color = TsmColors.TextDim)
                }
            }
        }
        if (!t.finished) {
            GhostButton(s.tournamentFinish, Icons.Filled.Flag, { confirmFinish = true }, Modifier.fillMaxWidth())
        }
        GhostButton(s.delete, Icons.Filled.Delete, { confirmDelete = true }, Modifier.fillMaxWidth())
    }

    if (missing) {
        ConfirmDialog(Icons.Filled.Flag, s.tournamentNextRound, s.tournamentMissingResults, s.ok, onConfirm = { missing = false }, onDismiss = { missing = false })
    }
    if (confirmFinish) {
        ConfirmDialog(Icons.Filled.Flag, s.tournamentFinish, s.tournamentFinishText, s.tournamentFinish,
            onConfirm = { confirmFinish = false; c.finishTournament(); tab = 1 }, onDismiss = { confirmFinish = false })
    }
    if (confirmDelete) {
        ConfirmDialog(Icons.Filled.Delete, s.tournamentDeleteTitle, s.tournamentDeleteText, s.delete,
            onConfirm = { confirmDelete = false; c.deleteTournament(t.id) }, onDismiss = { confirmDelete = false })
    }
    counting?.let { (roundNo, m) ->
        LiveCounter(c, t, m, onDismiss = { counting = null }) { a, b ->
            c.setTournamentResult(roundNo, m.court, a, b)
            counting = null
        }
    }
}

private fun team(t: Tournament, idx: List<Int>) = idx.joinToString(" / ") { t.players[it] }

/**
 * Una partita del turno: le due coppie e i punteggi da scrivere. Nelle partite a punti basta scrivere un
 * punteggio: l'altro si completa da solo (24 - 13 = 11).
 */
@Composable
private fun MatchCard(
    c: MatchController, t: Tournament, m: TMatch, editable: Boolean, onCount: () -> Unit, onResult: (Int?, Int?) -> Unit,
) {
    val s = LocalStrings.current
    var a by remember(m.scoreA, m.scoreB) { mutableStateOf(m.scoreA?.toString() ?: "") }
    var b by remember(m.scoreA, m.scoreB) { mutableStateOf(m.scoreB?.toString() ?: "") }
    val total = t.pointsPerMatch
    val byPoints = t.scoring == TournamentScoring.POINTS
    fun commit() {
        val ia = a.toIntOrNull()
        val ib = b.toIntOrNull()
        onResult(ia, ib)
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(TsmColors.Surface).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(s.tournamentCourt(m.court), color = TsmColors.Ball, fontWeight = FontWeight.Bold)
        for ((side, idx) in listOf(Side.P1 to m.a, Side.P2 to m.b)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                idx.forEach { PlayerAvatar(c, t.players[it], TsmColors.player(side), 30.dp) }
                Text(team(t, idx), color = TsmColors.player(side), fontWeight = FontWeight.Bold, maxLines = 2,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                OutlinedTextField(
                    value = if (side == Side.P1) a else b,
                    onValueChange = { v ->
                        val clean = v.filter { it.isDigit() }.take(3)
                        val n = clean.toIntOrNull()
                        if (side == Side.P1) {
                            a = clean
                            if (byPoints && n != null && n <= total) b = (total - n).toString()
                        } else {
                            b = clean
                            if (byPoints && n != null && n <= total) a = (total - n).toString()
                        }
                        commit()
                    },
                    enabled = editable,
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                        color = TsmColors.TextMain),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(76.dp),
                )
            }
        }
        if (byPoints && a.isNotEmpty() && b.isNotEmpty() && (a.toIntOrNull() ?: 0) + (b.toIntOrNull() ?: 0) != total) {
            Text(s.tournamentScoreHint(total), color = TsmColors.Orange, fontSize = 13.sp)
        }
        if (editable) GhostButton(s.tournamentCount, Icons.Filled.TouchApp, onCount, Modifier.fillMaxWidth())
    }
}

/** Conteggio punto per punto di una partita: due tasti grandi, la voce dice il punteggio, e a tempo il conto alla rovescia. */
@Composable
private fun LiveCounter(c: MatchController, t: Tournament, m: TMatch, onDismiss: () -> Unit, onSave: (Int, Int) -> Unit) {
    val s = LocalStrings.current
    val history = remember { mutableStateListOf<Side>() }
    val a = history.count { it == Side.P1 }
    val b = history.count { it == Side.P2 }
    val byPoints = t.scoring == TournamentScoring.POINTS
    val over = byPoints && a + b >= t.pointsPerMatch
    var startedAt by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(startedAt) {
        while (startedAt > 0) {
            now = SystemClock.elapsedRealtime()
            delay(250)
        }
    }
    val leftS = if (startedAt > 0) (t.minutes * 60 - ((now - startedAt) / 1000)).coerceAtLeast(0) else t.minutes * 60L
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = TsmColors.Background, modifier = Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().systemBarsPadding().padding(12.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(s.tournamentCourt(m.court), color = TsmColors.Ball, fontWeight = FontWeight.Black, fontSize = 20.sp, modifier = Modifier.weight(1f))
                    Text(
                        if (byPoints) "${a + b} / ${t.pointsPerMatch}" else "%d:%02d".format(leftS / 60, leftS % 60),
                        color = if (!byPoints && leftS == 0L) TsmColors.Danger else TsmColors.TextMain, fontWeight = FontWeight.Bold, fontSize = 20.sp,
                    )
                }
                if (!byPoints && startedAt == 0L) {
                    BigButton(s.tournamentStartTimer, Icons.Filled.Timer, { startedAt = SystemClock.elapsedRealtime() }, Modifier.fillMaxWidth())
                }
                if (!byPoints && startedAt > 0 && leftS == 0L) Text(s.tournamentTimeUp, color = TsmColors.Danger, fontWeight = FontWeight.Bold)
                for ((side, idx) in listOf(Side.P1 to m.a, Side.P2 to m.b)) {
                    Surface(
                        onClick = {
                            if (!over) {
                                history.add(side)
                                c.announceCounter(history.count { it == Side.P1 }, history.count { it == Side.P2 })
                            }
                        },
                        shape = RoundedCornerShape(24.dp),
                        color = TsmColors.player(side),
                        modifier = Modifier.fillMaxWidth().height(190.dp),
                    ) {
                        Column(Modifier.fillMaxSize().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceBetween) {
                            Text(team(t, idx), color = TsmColors.onPlayer(side), fontWeight = FontWeight.Bold, fontSize = 17.sp,
                                textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(if (side == Side.P1) "$a" else "$b", color = TsmColors.onPlayer(side), fontSize = 84.sp, fontWeight = FontWeight.Black)
                            Spacer(Modifier.height(4.dp))
                        }
                    }
                }
                if (over) Text(s.tournamentMatchOver, color = TsmColors.Ball, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GhostButton(s.undoPoint, Icons.AutoMirrored.Filled.Undo, {
                        if (history.isNotEmpty()) {
                            history.removeAt(history.lastIndex)
                            c.announceCounter(history.count { it == Side.P1 }, history.count { it == Side.P2 })
                        }
                    }, Modifier.weight(1f))
                    GhostButton(s.cancel, Icons.Filled.Close, onDismiss, Modifier.weight(1f))
                }
                BigButton(s.tournamentSave, Icons.Filled.Save, { onSave(a, b) }, Modifier.fillMaxWidth())
            }
        }
    }
}

/** Classifica: posizione, foto, nome, punti, partite giocate e vinte, differenza punti. */
@Composable
private fun StandingsTable(c: MatchController, t: Tournament) {
    val s = LocalStrings.current
    val rows = remember(t) { Tournaments.standings(t) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(TsmColors.Surface).padding(vertical = 6.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
            Text("", modifier = Modifier.weight(1f))
            for (h in listOf(s.tournamentPointsShort, s.tournamentPlayedShort, s.tournamentWonShort, "+/-")) {
                Text(h, color = TsmColors.TextDim, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.width(44.dp))
            }
        }
        rows.forEachIndexed { i, st ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}", color = if (i == 0) TsmColors.Ball else TsmColors.TextDim, fontWeight = FontWeight.Black, modifier = Modifier.width(26.dp))
                PlayerAvatar(c, st.name, if (i == 0) TsmColors.Ball else TsmColors.Outline, 30.dp)
                Spacer(Modifier.width(8.dp))
                Text(st.name, color = TsmColors.TextMain, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f))
                for (v in listOf("${st.points}", "${st.played}", "${st.won}", if (st.diff > 0) "+${st.diff}" else "${st.diff}")) {
                    Text(v, color = TsmColors.TextMain, textAlign = TextAlign.End, modifier = Modifier.width(44.dp))
                }
            }
            if (i < rows.lastIndex) Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(1.dp).background(TsmColors.Outline.copy(alpha = 0.4f)))
        }
    }
    Text(s.tournamentStandingsHint, color = TsmColors.TextDim, fontSize = 12.sp)
}
