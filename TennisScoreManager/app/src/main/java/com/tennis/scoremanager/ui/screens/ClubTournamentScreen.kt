// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.ui.screens

import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Tv
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tennis.scoremanager.MatchController
import com.tennis.scoremanager.data.CTEntry
import com.tennis.scoremanager.data.CTFormat
import com.tennis.scoremanager.data.CTKind
import com.tennis.scoremanager.data.CTMatch
import com.tennis.scoremanager.data.ClubTournament
import com.tennis.scoremanager.data.ClubTournaments
import com.tennis.scoremanager.data.Fees
import com.tennis.scoremanager.data.PlayerProfile
import com.tennis.scoremanager.data.Players
import com.tennis.scoremanager.ui.BigButton
import com.tennis.scoremanager.ui.GhostButton
import com.tennis.scoremanager.ui.LocalStrings
import com.tennis.scoremanager.ui.PlayerEditDialog
import com.tennis.scoremanager.ui.ScreenScaffold
import com.tennis.scoremanager.ui.SectionCard
import com.tennis.scoremanager.ui.SegOption
import com.tennis.scoremanager.ui.Segmented
import com.tennis.scoremanager.ui.SwitchRow
import com.tennis.scoremanager.ui.TsmColors
import com.tennis.scoremanager.ui.ctProblemText
import com.tennis.scoremanager.ui.ctRoundName
import com.tennis.scoremanager.ui.groupLetter
import java.util.Calendar

/** Il nome di un iscritto: i nomi di oggi dei giocatori ("Rossi / Bianchi" nel doppio). */
private fun entryName(c: MatchController, e: CTEntry?): String =
    e?.players?.mapIndexed { i, id -> c.playerById(id)?.name ?: e.names.getOrNull(i).orEmpty() }?.joinToString(" / ") ?: ""

/** "Sociale · Singolare · Eliminazione diretta" e simili. */
private fun kindLine(s: com.tennis.scoremanager.ui.Strings, t: ClubTournament): String = listOfNotNull(
    if (t.kind == CTKind.FITP) "FITP" else s.ctSocial,
    if (t.doubles) s.ctDoubles else s.ctSingles,
    if (t.format == CTFormat.GROUPS) s.ctFormatGroups else s.ctFormatKnockout,
    t.category.ifBlank { null },
    t.rankLimit.ifBlank { null }?.let { s.ctLimitShort(it) },
).joinToString(" · ")

/** Circolo › Tornei del circolo: in preparazione, in corso e conclusi. */
@Composable
fun ClubTournamentsScreen(c: MatchController) {
    val s = LocalStrings.current
    BackHandler { c.back() }
    val list by c.clubTournaments.collectAsState()
    val lang = c.options.value.lang
    var creating by remember { mutableStateOf(false) }
    ScreenScaffold(
        onHelp = { c.openHelp("7.13") },
        title = s.ctTitle,
        subtitle = s.ctHint,
        icon = Icons.Filled.EmojiEvents,
        bottomBar = {
            GhostButton(s.back, Icons.AutoMirrored.Filled.ArrowBack, { c.back() }, Modifier.weight(1f))
            BigButton(s.ctNew, Icons.Filled.Add, { creating = true }, Modifier.weight(1f))
        },
    ) {
        if (list.isEmpty()) Text(s.ctNone, color = TsmColors.TextDim)
        val groups = listOf(
            s.ctOpen to list.filter { !it.drawn },
            s.ctRunning to list.filter { it.drawn && it.champion == null },
            s.ctDone to list.filter { it.champion != null },
        )
        for ((title, items) in groups) {
            if (items.isEmpty()) continue
            SectionCard(title, Icons.Filled.EmojiEvents) {
                for (t in items.sortedByDescending { it.start ?: it.createdAt }) {
                    Row(Modifier.fillMaxWidth().clickable { c.openClubTournament(t.id) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(t.name, color = TsmColors.TextMain, fontWeight = FontWeight.Bold)
                            Text(kindLine(s, t), color = TsmColors.TextDim, fontSize = 13.sp)
                            Text(
                                listOfNotNull(t.start?.let { shortDate(it, lang) }, s.ctEntriesCount(t.entries.size),
                                    t.champion?.let { ch -> s.ctChampion(entryName(c, t.entries.firstOrNull { it.id == ch })) }).joinToString(" · "),
                                color = if (t.champion != null) TsmColors.Ball else TsmColors.TextDim, fontSize = 13.sp,
                            )
                        }
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = TsmColors.TextDim)
                    }
                }
            }
        }
    }
    if (creating) TournamentEditDialog(c, null) { id -> creating = false; id?.let { c.openClubTournament(it) } }
}

/** Un torneo: iscritti, tabellone (con i gironi) e partite. */
@Composable
fun ClubTournamentScreen(c: MatchController) {
    val s = LocalStrings.current
    BackHandler { c.back() }
    val list by c.clubTournaments.collectAsState()
    val id by c.openClubTournamentId.collectAsState()
    val t = list.firstOrNull { it.id == id } ?: return
    val players by c.players.collectAsState()
    val book by c.clubBook.collectAsState()
    var tab by rememberSaveable { mutableStateOf(0) }
    var edit by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var askDraw by remember { mutableStateOf(false) }
    var openMatch by remember { mutableStateOf<String?>(null) }
    var openEntry by remember { mutableStateOf<String?>(null) }
    var notify by remember { mutableStateOf(false) }
    val now = remember { System.currentTimeMillis() }
    val hasResults = t.matches.any { it.played && !it.bye }
    val hasKnockout = t.matches.any { it.round > 0 }
    ScreenScaffold(
        onHelp = { c.openHelp("7.13") },
        title = t.name,
        subtitle = kindLine(s, t),
        icon = Icons.Filled.EmojiEvents,
        action = { TextButton(onClick = { edit = true }) { Text(s.studentEdit, color = TsmColors.Ball) } },
        bottomBar = {
            GhostButton(s.back, Icons.AutoMirrored.Filled.ArrowBack, { c.back() }, Modifier.weight(1f))
            when {
                !t.drawn -> BigButton(s.ctDraw, Icons.Filled.Casino, { askDraw = true }, Modifier.weight(1f))
                t.format == CTFormat.GROUPS && t.groupsDone && !hasKnockout -> BigButton(s.ctMakeKnockout, Icons.Filled.EmojiEvents, { c.clubKnockoutFromGroups(t); tab = 1 }, Modifier.weight(1f))
                else -> Unit
            }
        },
    ) {
        t.champion?.let { ch -> Text(s.ctChampion(entryName(c, t.entries.firstOrNull { it.id == ch })), color = TsmColors.Ball, fontWeight = FontWeight.Bold, fontSize = 18.sp) }
        Segmented(listOf(SegOption(s.ctTabEntries), SegOption(s.ctTabDraw), SegOption(s.ctTabMatches)), tab, { tab = it })
        if (t.entries.isNotEmpty()) GhostButton(s.ntButton, Icons.Filled.Campaign, { notify = true }, Modifier.fillMaxWidth())
        // Sul tabellone TV: tabellone, gironi e ordine di gioco che si alternano, aggiornati a ogni risultato.
        if (t.drawn) {
            val onTv by c.tvClubTournament.collectAsState()
            val tvRunning by c.tvServer.running.collectAsState()
            if (onTv == t.id) GhostButton(s.ctTvHide, Icons.Filled.Tv, { c.showClubOnTv(null) }, Modifier.fillMaxWidth())
            else if (tvRunning) GhostButton(s.ctTvShow, Icons.Filled.Tv, { c.showClubOnTv(t.id) }, Modifier.fillMaxWidth())
            else GhostButton(s.ctTvOff, Icons.Filled.Tv, { c.go(com.tennis.scoremanager.Screen.DEVICES) }, Modifier.fillMaxWidth())
        }
        when (tab) {
            0 -> EntriesTab(c, t, players, book, now, onAdd = { adding = true }, onOpen = { openEntry = it })
            1 -> DrawTab(c, t) { openMatch = it }
            else -> MatchesTab(c, t) { openMatch = it }
        }
        if (t.drawn && !hasResults) GhostButton(s.ctUndoDraw, Icons.Filled.Undo, { c.undoClubDraw(t) }, Modifier.fillMaxWidth())
    }
    if (edit) TournamentEditDialog(c, t) { edit = false }
    if (notify) NoticeChoiceDialog(c, t) { notify = false }
    if (adding) EntryPickDialog(c, t) { adding = false }
    if (askDraw) {
        val ok = ClubTournaments.eligible(t, players, book, now)
        val out = t.entries.size - ok.size
        AlertDialog(
            onDismissRequest = { askDraw = false },
            title = { Text(s.ctDraw) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(s.ctDrawAsk(ok.size), color = TsmColors.TextMain)
                    if (out > 0) Text(s.ctDrawExcluded(out), color = TsmColors.Orange)
                    if (ok.size < 2) Text(s.ctDrawTooFew, color = TsmColors.Danger)
                }
            },
            confirmButton = { TextButton(enabled = ok.size >= 2, onClick = { askDraw = false; if (c.drawClubTournament(t)) tab = 1 }) { Text(s.ctDraw) } },
            dismissButton = { TextButton(onClick = { askDraw = false }) { Text(s.back) } },
        )
    }
    openMatch?.let { mid -> t.matches.firstOrNull { it.id == mid }?.let { m -> MatchDialog(c, t, m) { openMatch = null } } }
    openEntry?.let { eid -> t.entries.firstOrNull { it.id == eid }?.let { e -> EntryDialog(c, t, e) { openEntry = null } } }
}

@Composable
private fun EntriesTab(
    c: MatchController, t: ClubTournament, players: List<PlayerProfile>, book: com.tennis.scoremanager.data.ClubBook, now: Long,
    onAdd: () -> Unit, onOpen: (String) -> Unit,
) {
    val s = LocalStrings.current
    val lang = c.options.value.lang
    // Le informazioni del torneo.
    val info = listOfNotNull(
        t.start?.let { st -> s.ctDates(shortDate(st, lang), t.end?.let { shortDate(it, lang) } ?: shortDate(st, lang)) },
        t.feeCents.takeIf { it > 0 }?.let { s.ctFeeLine(Fees.money(it, lang.locale)) + if (t.requirePaid) " · " + s.ctPaidOnlyShort else "" },
        t.fitpCode.ifBlank { null }?.let { s.ctFitpCodeLine(it) },
        t.referee.ifBlank { null }?.let { s.ctRefereeLine(it) },
    )
    for (l in info) Text(l, color = TsmColors.TextDim, fontSize = 13.sp)
    // Anche dopo il sorteggio: un ripescato si iscrive qui e prende posto con Modifica tabellone.
    if (t.champion == null) BigButton(s.ctAddEntry, Icons.Filled.PersonAdd, onAdd, Modifier.fillMaxWidth())
    if (t.entries.isEmpty()) Text(s.ctNoEntries, color = TsmColors.TextDim)
    val ordered = ClubTournaments.ordered(t.entries, players)
    val okIds = ClubTournaments.eligible(t, players, book, now).map { it.id }.toSet()
    val seeds = ClubTournaments.seedCount(ClubTournaments.drawSize(okIds.size))
    val seedOf = ordered.filter { it.id in okIds }.take(seeds).mapIndexed { i, e -> e.id to i + 1 }.toMap()
    for (e in ordered) {
        val people = e.players.mapNotNull { Players.byId(players, it) }
        val problems = people.flatMap { p -> ClubTournaments.problems(t, p, book, now).map { p to it } }
        Row(Modifier.fillMaxWidth().clickable { onOpen(e.id) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    (seedOf[e.id]?.let { "[$it] " } ?: "") + entryName(c, e),
                    color = TsmColors.TextMain, fontWeight = FontWeight.Bold,
                )
                val ranks = people.map { it.member?.ranking?.ifBlank { null } ?: "NC" }.joinToString(" / ")
                Text(ranks, color = TsmColors.TextDim, fontSize = 13.sp)
                if (problems.isEmpty()) Text(s.ctInOrder, color = TsmColors.Ok, fontSize = 13.sp)
                else Text(
                    problems.joinToString(" · ") { (p, pr) -> (if (people.size > 1) "${p.name}: " else "") + ctProblemText(s, pr) },
                    color = if (problems.any { ClubTournaments.blocking(it.second) }) TsmColors.Danger else TsmColors.Orange, fontSize = 13.sp,
                )
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = TsmColors.TextDim)
        }
    }
}

/** Il posto scelto in Modifica tabellone: nel primo turno ([seat]) o in un girone ([entry]). */
private data class EditPick(val seat: ClubTournaments.Seat? = null, val entry: String? = null)

/** Modifica a mano: si tocca un posto, poi quello con cui scambiarlo, o un iscritto rimasto fuori. */
@Composable
private fun EditDraw(c: MatchController, t: ClubTournament) {
    val s = LocalStrings.current
    val players by c.players.collectAsState()
    val book by c.clubBook.collectAsState()
    var picked by remember { mutableStateOf<EditPick?>(null) }
    var failed by remember { mutableStateOf(false) }
    val now = remember { System.currentTimeMillis() }
    val inGroups = t.format == CTFormat.GROUPS && !ClubTournaments.groupsLocked(t)
    val ok = ClubTournaments.eligible(t, players, book, now).map { it.id }.toSet()
    val subs = ClubTournaments.outsideDraw(t).filter { it.id in ok }
    fun change(f: (ClubTournament) -> ClubTournament?) { failed = !c.editClubDraw(t, f); picked = null }
    fun tap(x: EditPick) {
        val p = picked
        when {
            p == null -> picked = x
            p == x -> picked = null
            p.seat != null && x.seat != null -> change { ClubTournaments.swapSeats(it, p.seat, x.seat) }
            p.entry != null && x.entry != null -> change { ClubTournaments.swapGroupEntries(it, p.entry, x.entry) }
        }
    }
    @Composable fun place(name: String, x: EditPick, locked: Boolean) {
        val on = picked == x
        Text(
            name, color = if (on) TsmColors.Background else if (locked) TsmColors.TextDim else TsmColors.TextMain, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(if (on) TsmColors.Ball else TsmColors.SurfaceHigh)
                .clickable(enabled = !locked) { tap(x) }.padding(horizontal = 10.dp, vertical = 9.dp),
        )
    }
    if (t.kind == CTKind.FITP) Text(s.ctEditFitp, color = TsmColors.Orange, fontSize = 13.sp)
    Text(if (picked != null) s.ctEditPicked else if (inGroups) s.ctEditGroupsHint else s.ctEditHint, color = TsmColors.TextDim, fontSize = 13.sp)
    if (failed) Text(s.ctEditNo, color = TsmColors.Orange, fontSize = 13.sp)
    if (inGroups) {
        for ((g, members) in ClubTournaments.groupMembers(t).withIndex()) {
            Text(s.ctGroup(groupLetter(g)), color = TsmColors.TextDim, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            for (id in members) place(entryName(c, t.entries.firstOrNull { it.id == id }), EditPick(entry = id), ClubTournaments.groupEntryLocked(t, id))
        }
    } else {
        for (m in t.matches.filter { it.round == 1 }.sortedBy { it.slot }) {
            val locked = ClubTournaments.seatLocked(t, m)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for ((side, id) in listOf(0 to m.a, 1 to m.b)) {
                    place(id?.let { x -> entryName(c, t.entries.firstOrNull { it.id == x }) } ?: s.ctBye, EditPick(ClubTournaments.Seat(m.id, side)), locked)
                }
            }
        }
    }
    val p = picked
    if (p != null && subs.isNotEmpty()) {
        Text(s.ctEditSubs, color = TsmColors.TextDim, fontSize = 13.sp)
        for (e in subs) GhostButton(entryName(c, e), Icons.Filled.PersonAdd, {
            change { if (p.seat != null) ClubTournaments.replaceSeat(it, p.seat, e.id) else ClubTournaments.replaceGroupEntry(it, p.entry.orEmpty(), e.id) }
        }, Modifier.fillMaxWidth())
    }
}

/** Altezza di una casella del tabellone (dp). */
private const val BOX_H = 96

/** Una casella del tabellone: due righe (in grassetto chi ha vinto) e il punteggio. */
@Composable
private fun MatchBox(c: MatchController, t: ClubTournament, m: CTMatch, onOpen: (String) -> Unit) {
    val s = LocalStrings.current
    Column(
        Modifier.width(190.dp).height(BOX_H.dp).clip(RoundedCornerShape(10.dp)).background(TsmColors.SurfaceHigh)
            .clickable(enabled = m.a != null && m.b != null) { onOpen(m.id) }.padding(8.dp),
    ) {
        for ((side, e) in listOf(m.a to "a", m.b to "b")) {
            val name = side?.let { entryName(c, t.entries.firstOrNull { x -> x.id == it }) } ?: if (m.bye) s.ctBye else "—"
            Text(
                name, color = if (m.winner != null && m.winner == side) TsmColors.Ball else TsmColors.TextMain,
                fontWeight = if (m.winner != null && m.winner == side) FontWeight.Bold else FontWeight.Normal, fontSize = 13.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        val extra = listOfNotNull(m.score.ifBlank { null }, s.ctWalkover.takeIf { m.walkover }, s.ctRetired.takeIf { m.retired }).joinToString(" ")
        if (extra.isNotBlank()) Text(extra, color = TsmColors.TextDim, fontSize = 12.sp)
    }
}

@Composable
private fun DrawTab(c: MatchController, t: ClubTournament, onOpen: (String) -> Unit) {
    val s = LocalStrings.current
    if (!t.drawn) { Text(s.ctNotDrawn, color = TsmColors.TextDim); return }
    var editing by remember { mutableStateOf(false) }
    if (t.manualEdits > 0) Text(s.ctEdited(t.manualEdits), color = if (t.kind == CTKind.FITP) TsmColors.Orange else TsmColors.TextDim, fontSize = 13.sp)
    if (t.champion == null) GhostButton(if (editing) s.ctEditDone else s.ctEditDraw, Icons.Filled.SwapHoriz, { editing = !editing }, Modifier.fillMaxWidth())
    if (editing) { EditDraw(c, t); return }
    // Gironi: la classifica (in evidenza chi passa) e le partite.
    val groups = ClubTournaments.groupMembers(t)
    for (g in groups.indices) {
        SectionCard(s.ctGroup(groupLetter(g)), Icons.Filled.EmojiEvents) {
            Row(Modifier.fillMaxWidth()) {
                Text("", Modifier.weight(1f))
                for (h in listOf(s.ctColWon, s.ctColSets, s.ctColGames)) Text(h, color = TsmColors.TextDim, fontSize = 12.sp, modifier = Modifier.width(52.dp))
            }
            for ((i, st) in ClubTournaments.standings(t, g).withIndex()) {
                val q = i < t.qualify
                Row(Modifier.fillMaxWidth()) {
                    Text("${i + 1}. " + entryName(c, t.entries.firstOrNull { it.id == st.entry }), color = if (q) TsmColors.Ball else TsmColors.TextMain,
                        fontWeight = if (q) FontWeight.Bold else FontWeight.Normal, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${st.won}/${st.played}", color = TsmColors.TextMain, fontSize = 13.sp, modifier = Modifier.width(52.dp))
                    Text("%+d".format(st.setsWon - st.setsLost), color = TsmColors.TextMain, fontSize = 13.sp, modifier = Modifier.width(52.dp))
                    Text("%+d".format(st.gamesWon - st.gamesLost), color = TsmColors.TextMain, fontSize = 13.sp, modifier = Modifier.width(52.dp))
                }
            }
        }
    }
    // Il tabellone a eliminazione: un turno per colonna, si scorre di lato.
    val rounds = t.rounds
    if (rounds == 0) { if (groups.isNotEmpty() && !t.groupsDone) Text(s.ctGroupsFirst, color = TsmColors.TextDim, fontSize = 13.sp); return }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        for (r in 1..rounds) {
            val ms = t.matches.filter { it.round == r }.sortedBy { it.slot }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(ctRoundName(s, t, ms.first()), color = TsmColors.TextDim, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                // Caselle tutte alte uguali: nei turni dopo si lascia lo spazio giusto perché ogni partita stia a metà
                // tra le due da cui viene.
                val gap = ((1 shl (r - 1)) - 1) * (BOX_H + 8)
                for ((i, m) in ms.withIndex()) {
                    Spacer(Modifier.height((if (i == 0) gap / 2 else gap).dp))
                    MatchBox(c, t, m, onOpen)
                }
            }
        }
    }
}

@Composable
private fun MatchesTab(c: MatchController, t: ClubTournament, onOpen: (String) -> Unit) {
    val s = LocalStrings.current
    val lang = c.options.value.lang
    if (!t.drawn) { Text(s.ctNotDrawn, color = TsmColors.TextDim); return }
    // Prima quelle da giocare (in ordine di orario), poi le giocate; i bye non contano.
    val ms = t.matches.filter { !it.bye }
    val todo = ms.filter { !it.played && it.a != null && it.b != null }.sortedWith(compareBy({ it.at ?: Long.MAX_VALUE }, { it.round }, { it.group ?: 0 }, { it.slot }))
    val done = ms.filter { it.played }.sortedWith(compareByDescending<CTMatch> { it.round }.thenBy { it.slot })
    for ((title, list) in listOf(s.ctToPlay to todo, s.ctPlayed to done)) {
        if (list.isEmpty()) continue
        Text(title, color = TsmColors.TextMain, fontWeight = FontWeight.Bold)
        for (m in list) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(TsmColors.Surface).clickable { onOpen(m.id) }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(ctRoundName(s, t, m), color = TsmColors.TextDim, fontSize = 12.sp)
                    Text("${entryName(c, t.entries.firstOrNull { it.id == m.a })} – ${entryName(c, t.entries.firstOrNull { it.id == m.b })}", color = TsmColors.TextMain, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    val when_ = listOfNotNull(m.at?.let { shortDate(it, lang) + " " + com.tennis.scoremanager.data.Reports.time(it, lang) }, m.court.ifBlank { null }).joinToString(" · ")
                    if (when_.isNotBlank()) Text(when_, color = TsmColors.TextDim, fontSize = 12.sp)
                }
                if (m.played) Text(m.score.ifBlank { if (m.walkover) s.ctWalkover else "✓" }, color = TsmColors.Ball, fontSize = 13.sp)
            }
        }
    }
}

/** Una partita: gioca col segnapunti, risultato a mano (anche w.o. e ritiro), campo e orario. */
@Composable
private fun MatchDialog(c: MatchController, t: ClubTournament, m: CTMatch, onDone: () -> Unit) {
    val s = LocalStrings.current
    val context = LocalContext.current
    val lang = c.options.value.lang
    val a = entryName(c, t.entries.firstOrNull { it.id == m.a })
    val b = entryName(c, t.entries.firstOrNull { it.id == m.b })
    var winner by remember { mutableStateOf(m.winner) }
    var score by remember { mutableStateOf(m.score) }
    var walkover by remember { mutableStateOf(m.walkover) }
    var retired by remember { mutableStateOf(m.retired) }
    var court by remember { mutableStateOf(m.court) }
    var at by remember { mutableStateOf(m.at) }
    val courts = c.storage.clubCourts
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(ctRoundName(s, t, m)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("$a – $b", color = TsmColors.TextMain, fontWeight = FontWeight.Bold)
                if (!m.played && m.a != null && m.b != null) BigButton(s.ctPlayLive, Icons.Filled.PlayArrow, { onDone(); c.playClubMatch(t, m.copy(court = court)) }, Modifier.fillMaxWidth())
                Text(s.ctWinner, color = TsmColors.TextDim, fontSize = 13.sp)
                val sides = listOf(m.a, m.b)
                Segmented(listOf(SegOption(a), SegOption(b)), sides.indexOf(winner).takeIf { winner != null } ?: -1, { winner = sides[it] }, columns = 1)
                OutlinedTextField(score, {
                    score = it.take(40)
                    // Dal punteggio si capisce chi ha vinto.
                    ClubTournaments.winnerFromScore(it)?.let { w -> winner = if (w == "a") m.a else m.b }
                }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(s.ctScore) })
                SwitchRow(Icons.Filled.EmojiEvents, s.ctWalkoverLong, null, walkover) { walkover = it; if (it) retired = false }
                SwitchRow(Icons.Filled.EmojiEvents, s.ctRetiredLong, null, retired) { retired = it; if (it) walkover = false }
                Text(s.ctWhere, color = TsmColors.TextDim, fontSize = 13.sp)
                if (courts.isNotEmpty()) Segmented(courts.map { SegOption(it) }, courts.indexOf(court), { court = courts[it] }, columns = minOf(3, courts.size))
                else OutlinedTextField(court, { court = it.take(30) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(s.ctCourt) })
                DateButton(s.payDate, at, lang) { d ->
                    // Il giorno scelto, con l'ora di prima (o le 9).
                    val old = Calendar.getInstance().apply { timeInMillis = at ?: d }
                    at = Calendar.getInstance().apply { timeInMillis = d; set(Calendar.HOUR_OF_DAY, if (at == null) 9 else old.get(Calendar.HOUR_OF_DAY)); set(Calendar.MINUTE, if (at == null) 0 else old.get(Calendar.MINUTE)) }.timeInMillis
                }
                if (at != null) GhostButton("${s.ctTime}: ${com.tennis.scoremanager.data.Reports.time(at, lang)}", Icons.Filled.Schedule, {
                    val cal = Calendar.getInstance().apply { timeInMillis = at!! }
                    TimePickerDialog(context, { _, h, min -> at = Calendar.getInstance().apply { timeInMillis = at!!; set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, min) }.timeInMillis },
                        cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
                }, Modifier.fillMaxWidth())
                if (m.played) TextButton(onClick = { c.setClubResult(t, m.id, null, "", false, false); onDone() }) { Text(s.ctClearResult, color = TsmColors.Danger) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                var cur = c.clubTournament(t.id) ?: t
                if (court != m.court || at != m.at) { c.setClubSchedule(cur, m.id, court, at); cur = c.clubTournament(t.id) ?: cur }
                if (winner != m.winner || score != m.score || walkover != m.walkover || retired != m.retired) c.setClubResult(cur, m.id, winner, score, walkover, retired)
                onDone()
            }) { Text(s.ok) }
        },
        dismissButton = { TextButton(onClick = onDone) { Text(s.back) } },
    )
}

/** Un iscritto: testa di serie a mano e ritiro (prima del sorteggio). */
@Composable
private fun EntryDialog(c: MatchController, t: ClubTournament, e: CTEntry, onDone: () -> Unit) {
    val s = LocalStrings.current
    var seed by remember { mutableStateOf(e.seed) }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(entryName(c, e)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (id in e.players) c.playerById(id)?.let { p -> GhostButton(p.name, Icons.AutoMirrored.Filled.ArrowForward, { onDone(); c.openStudent(p.id) }, Modifier.fillMaxWidth()) }
                if (t.drawn && ClubTournaments.outsideDraw(t).any { it.id == e.id })
                    TextButton(onClick = { c.removeClubEntry(t, e.id); onDone() }) { Text(s.ctRemoveEntry, color = TsmColors.Danger) }
                if (!t.drawn) {
                    Text(s.ctSeedHint, color = TsmColors.TextDim, fontSize = 13.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { seed = seed?.let { if (it <= 1) null else it - 1 } }) { Icon(Icons.Filled.Remove, null) }
                        Text(seed?.let { s.ctSeedN(it) } ?: s.ctSeedAuto, color = TsmColors.TextMain, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        IconButton(onClick = { seed = (seed ?: 0) + 1 }) { Icon(Icons.Filled.Add, null) }
                    }
                    TextButton(onClick = { c.removeClubEntry(t, e.id); onDone() }) { Text(s.ctRemoveEntry, color = TsmColors.Danger) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (seed != e.seed) c.saveClubTournament(t.copy(entries = t.entries.map { if (it.id == e.id) it.copy(seed = seed) else it }))
                onDone()
            }) { Text(s.ok) }
        },
        dismissButton = { TextButton(onClick = onDone) { Text(s.back) } },
    )
}

/** Iscrive un giocatore (o, nel doppio, due): dal database, o uno nuovo. */
@Composable
private fun EntryPickDialog(c: MatchController, t: ClubTournament, onDone: () -> Unit) {
    val s = LocalStrings.current
    val players by c.players.collectAsState()
    var query by remember { mutableStateOf("") }
    var first by remember { mutableStateOf<PlayerProfile?>(null) }
    var creating by remember { mutableStateOf(false) }
    val inside = t.entries.flatMap { it.players }.toSet()
    val free = players.filter { it.id !in inside && it.id != first?.id }
    // Prima i soci, poi gli altri.
    val list = (if (query.isBlank()) free else Players.search(free, query)).sortedBy { if (it.member != null) 0 else 1 }
    fun pick(p: PlayerProfile) {
        if (t.doubles && first == null) { first = p; query = ""; return }
        c.addClubEntry(c.clubTournament(t.id) ?: t, listOfNotNull(first, p))
        onDone()
    }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(if (t.doubles && first != null) s.ctPickPartner(first!!.name) else s.ctAddEntry) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                GhostButton(s.membersNewPlayer, Icons.Filled.PersonAdd, { creating = true }, Modifier.fillMaxWidth())
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(s.playersSearch) }, leadingIcon = { Icon(Icons.Filled.Search, null) })
                for (p in list.take(50)) {
                    Row(Modifier.fillMaxWidth().clickable { pick(p) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, color = TsmColors.TextMain, fontWeight = FontWeight.Bold)
                            Text(listOfNotNull(p.member?.ranking?.ifBlank { null }, s.membersTitle.takeIf { p.member != null }).joinToString(" · "), color = TsmColors.TextDim, fontSize = 12.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDone) { Text(s.back) } },
    )
    if (creating) PlayerEditDialog(c, null) { p -> creating = false; if (p != null) pick(p) }
}

/** Che avviso mandare agli iscritti: iscrizione, partite, ordine di gioco di un giorno, tabellone, messaggio libero. */
@Composable
private fun NoticeChoiceDialog(c: MatchController, t: ClubTournament, onDone: () -> Unit) {
    val s = LocalStrings.current
    val lang = c.options.value.lang
    var pickDay by remember { mutableStateOf(false) }
    val open = t.matches.filter { !it.played && it.a != null && it.b != null }
    // I giorni con partite in programma (a mezzanotte, uno per giorno).
    val days = open.mapNotNull { it.at }.map { at -> Calendar.getInstance().apply { timeInMillis = at; set(Calendar.HOUR_OF_DAY, 12); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis }.distinct().sorted()
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(if (pickDay) s.ntPickDay else s.ntChoose) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!pickDay) {
                    GhostButton(s.ntKindEntries, Icons.Filled.PersonAdd, { onDone(); c.noticeEntries(t) }, Modifier.fillMaxWidth())
                    if (open.isNotEmpty()) GhostButton(s.ntKindNext, Icons.Filled.PlayArrow, { onDone(); c.noticeMatches(t, null) }, Modifier.fillMaxWidth())
                    if (days.isNotEmpty()) GhostButton(s.ntKindOrder, Icons.Filled.Schedule, { pickDay = true }, Modifier.fillMaxWidth())
                    if (t.drawn) GhostButton(s.ntKindDraw, Icons.Filled.EmojiEvents, { onDone(); c.noticeDraw(t) }, Modifier.fillMaxWidth())
                    GhostButton(s.ntKindFree, Icons.Filled.Campaign, { onDone(); c.noticeFreeTournament(t) }, Modifier.fillMaxWidth())
                } else for (d in days) {
                    GhostButton(com.tennis.scoremanager.data.Notices.day(d, lang), Icons.Filled.Schedule, { onDone(); c.noticeMatches(t, d) }, Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDone) { Text(s.back) } },
    )
}

/** Torneo nuovo o da cambiare. [onDone] riceve l'id del torneo salvato (null = annullato o eliminato). */
@Composable
private fun TournamentEditDialog(c: MatchController, t: ClubTournament?, onDone: (String?) -> Unit) {
    val s = LocalStrings.current
    val lang = c.options.value.lang
    var name by remember { mutableStateOf(t?.name.orEmpty()) }
    var kind by remember { mutableStateOf(t?.kind ?: CTKind.SOCIAL) }
    var doubles by remember { mutableStateOf(t?.doubles ?: false) }
    var category by remember { mutableStateOf(t?.category.orEmpty()) }
    var format by remember { mutableStateOf(t?.format ?: CTFormat.KNOCKOUT) }
    var groups by remember { mutableStateOf(t?.groups ?: 2) }
    var qualify by remember { mutableStateOf(t?.qualify ?: 2) }
    var start by remember { mutableStateOf(t?.start) }
    var end by remember { mutableStateOf(t?.end) }
    var fee by remember { mutableStateOf(t?.feeCents?.takeIf { it > 0 }?.let { plain(it, lang) }.orEmpty()) }
    var requirePaid by remember { mutableStateOf(t?.requirePaid ?: true) }
    var limit by remember { mutableStateOf(t?.rankLimit.orEmpty()) }
    var code by remember { mutableStateOf(t?.fitpCode.orEmpty()) }
    var referee by remember { mutableStateOf(t?.referee.orEmpty()) }
    var askDelete by remember { mutableStateOf(false) }
    val drawn = t?.drawn == true
    val feeOk = fee.isBlank() || Fees.parse(fee) != null
    @Composable fun field(v: String, label: String, onChange: (String) -> Unit) =
        OutlinedTextField(v, { onChange(it.take(60)) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(label) })
    @Composable fun stepper(label: String, v: Int, min: Int, max: Int, onChange: (Int) -> Unit) = Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = TsmColors.TextMain, modifier = Modifier.weight(1f))
        IconButton(onClick = { onChange((v - 1).coerceAtLeast(min)) }) { Icon(Icons.Filled.Remove, null) }
        Text("$v", color = TsmColors.TextMain, fontWeight = FontWeight.Bold)
        IconButton(onClick = { onChange((v + 1).coerceAtMost(max)) }) { Icon(Icons.Filled.Add, null) }
    }
    AlertDialog(
        onDismissRequest = { onDone(null) },
        title = { Text(if (t == null) s.ctNew else t.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                field(name, s.ctName) { name = it }
                Segmented(listOf(SegOption(s.ctSocial), SegOption("FITP")), if (kind == CTKind.FITP) 1 else 0, { kind = if (it == 1) CTKind.FITP else CTKind.SOCIAL }, enabled = !drawn)
                Text(if (kind == CTKind.FITP) s.ctFitpHint else s.ctSocialHint, color = TsmColors.TextDim, fontSize = 12.sp)
                Segmented(listOf(SegOption(s.ctSingles), SegOption(s.ctDoubles)), if (doubles) 1 else 0, { doubles = it == 1 }, enabled = !drawn && t?.entries.isNullOrEmpty())
                field(category, s.ctCategory) { category = it }
                Segmented(listOf(SegOption(s.ctFormatKnockout), SegOption(s.ctFormatGroups)), if (format == CTFormat.GROUPS) 1 else 0, { format = if (it == 1) CTFormat.GROUPS else CTFormat.KNOCKOUT }, enabled = !drawn)
                if (format == CTFormat.GROUPS && !drawn) {
                    stepper(s.ctGroupsCount, groups, 1, 8) { groups = it }
                    stepper(s.ctQualify, qualify, 1, 4) { qualify = it }
                }
                DateButton(s.ctStart, start, lang) { start = it; if (end == null || end!! < it) end = it }
                DateButton(s.ctEnd, end, lang) { end = it }
                MoneyField(fee, s.ctFee) { fee = it }
                if ((Fees.parse(fee) ?: 0) > 0) SwitchRow(Icons.Filled.EmojiEvents, s.ctPaidOnly, s.ctPaidOnlyHint, requirePaid) { requirePaid = it }
                field(limit, s.ctLimit) { limit = it.trim().uppercase() }
                if (kind == CTKind.FITP) {
                    field(code, s.ctFitpCode) { code = it }
                    field(referee, s.ctReferee) { referee = it }
                }
                if (t != null) TextButton(onClick = { askDelete = true }) { Text(s.ctDelete, color = TsmColors.Danger) }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && feeOk, onClick = {
                val base = t ?: ClubTournament(ClubTournaments.newId("t"), "", createdAt = System.currentTimeMillis())
                val saved = base.copy(
                    name = name.trim(), kind = kind, doubles = doubles, category = category.trim(), format = format, groups = groups, qualify = qualify,
                    start = start, end = end, feeCents = Fees.parse(fee) ?: 0, requirePaid = requirePaid, rankLimit = limit,
                    fitpCode = if (kind == CTKind.FITP) code.trim() else "", referee = if (kind == CTKind.FITP) referee.trim() else "",
                )
                c.saveClubTournament(saved)
                onDone(saved.id)
            }) { Text(s.ok) }
        },
        dismissButton = { TextButton(onClick = { onDone(null) }) { Text(s.back) } },
    )
    if (askDelete && t != null) AlertDialog(
        onDismissRequest = { askDelete = false },
        text = { Text(s.ctDeleteAsk(t.name)) },
        confirmButton = { TextButton(onClick = { askDelete = false; c.deleteClubTournament(t.id); onDone(null); c.back() }) { Text(s.ctDelete, color = TsmColors.Danger) } },
        dismissButton = { TextButton(onClick = { askDelete = false }) { Text(s.back) } },
    )
}
