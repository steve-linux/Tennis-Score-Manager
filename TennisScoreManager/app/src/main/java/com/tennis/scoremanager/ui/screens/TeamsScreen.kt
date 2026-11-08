// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.ui.screens

import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.text.style.TextAlign
import com.tennis.scoremanager.MatchController
import com.tennis.scoremanager.data.Availability
import com.tennis.scoremanager.data.ClubTournaments
import com.tennis.scoremanager.data.CourtPlans
import com.tennis.scoremanager.data.Fixture
import com.tennis.scoremanager.data.Rubber
import com.tennis.scoremanager.data.Team
import com.tennis.scoremanager.data.Teams
import com.tennis.scoremanager.ui.BigButton
import com.tennis.scoremanager.ui.GhostButton
import com.tennis.scoremanager.ui.LocalStrings
import com.tennis.scoremanager.ui.PlayerAvatar
import com.tennis.scoremanager.ui.ScreenScaffold
import com.tennis.scoremanager.ui.SectionCard
import com.tennis.scoremanager.ui.SegOption
import com.tennis.scoremanager.ui.Segmented
import com.tennis.scoremanager.ui.Strings
import com.tennis.scoremanager.ui.SwitchRow
import com.tennis.scoremanager.ui.TsmColors
import com.tennis.scoremanager.ui.ctProblemText
import java.util.Calendar

/** "V 3 · P 1 · N 0". */
private fun recordLine(s: Strings, t: Team): String = Teams.record(t).let { r -> s.tmRecord(r.won, r.lost) + if (r.drawn > 0) " · " + s.tmDrawn(r.drawn) else "" }

/** "4-2 vinto", "2-4 perso", "3-3", "2-1 in corso". */
private fun resultLine(s: Strings, f: Fixture): String {
    if (f.rubbers.none { it.won != null }) return ""
    val (w, l) = Teams.result(f)
    return "$w-$l " + when { !Teams.decided(f) -> s.tmOngoing; Teams.outcome(f) == true -> s.tmWon; Teams.outcome(f) == false -> s.tmLost; else -> s.tmDraw }
}

/** Circolo › Tornei › Squadre e campionati. */
@Composable
fun TeamsScreen(c: MatchController) {
    val s = LocalStrings.current
    BackHandler { c.back() }
    val list by c.teams.collectAsState()
    var creating by remember { mutableStateOf(false) }
    ScreenScaffold(
        onHelp = { c.openHelp("7.17") },
        title = s.tmTitle,
        subtitle = s.tmHint,
        icon = Icons.Filled.Groups,
        bottomBar = {
            GhostButton(s.back, Icons.AutoMirrored.Filled.ArrowBack, { c.back() }, Modifier.weight(1f))
            BigButton(s.tmNew, Icons.Filled.Add, { creating = true }, Modifier.weight(1f))
        },
    ) {
        if (list.isEmpty()) Text(s.tmNone, color = TsmColors.TextDim)
        val now = System.currentTimeMillis()
        for (t in list.sortedBy { it.name.lowercase() }) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TsmColors.Surface).clickable { c.openTeam(t.id) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(t.name, color = TsmColors.TextMain, fontWeight = FontWeight.Bold)
                    Text(listOfNotNull(t.championship.ifBlank { null }, t.season.ifBlank { null }, s.tmPlayers(t.roster.size), recordLine(s, t)).joinToString(" · "), color = TsmColors.TextDim, fontSize = 13.sp)
                    t.fixtures.firstOrNull { it.at > now - 6 * 3_600_000L && !Teams.decided(it) }?.let { f -> Text(s.scNext("${f.opponent} · ${c.fixtureWhen(f)}"), color = TsmColors.Ball, fontSize = 13.sp) }
                }
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = TsmColors.TextDim)
            }
        }
    }
    if (creating) TeamEditDialog(c, null) { id -> creating = false; id?.let { c.openTeam(it) } }
}

/** Una squadra: la rosa (nell'ordine della lista) e gli incontri. */
@Composable
fun TeamScreen(c: MatchController) {
    val s = LocalStrings.current
    BackHandler { c.back() }
    val list by c.teams.collectAsState()
    val id by c.openTeamId.collectAsState()
    val t = list.firstOrNull { it.id == id } ?: return
    val players by c.players.collectAsState()
    var tab by rememberSaveable { mutableStateOf(0) }
    var edit by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    val now = System.currentTimeMillis()
    // I controlli FITP valgono per il prossimo incontro (o per oggi).
    val day = t.fixtures.firstOrNull { it.at > now - 6 * 3_600_000L }?.at ?: now
    ScreenScaffold(
        onHelp = { c.openHelp("7.17") },
        title = t.name,
        subtitle = listOfNotNull(t.championship.ifBlank { null }, t.season.ifBlank { null }, recordLine(s, t)).joinToString(" · "),
        icon = Icons.Filled.Groups,
        action = { TextButton(onClick = { edit = true }) { Text(s.studentEdit, color = TsmColors.Ball) } },
        bottomBar = {
            GhostButton(s.back, Icons.AutoMirrored.Filled.ArrowBack, { c.back() }, Modifier.weight(1f))
            if (tab == 1) BigButton(s.tmNewFixture, Icons.Filled.Add, {
                val f = Fixture(Teams.newId("f"), "", CourtPlans.at(CourtPlans.addDays(now, 7), 9), rubbers = Teams.emptyRubbers(t))
                c.saveFixture(t, f); c.openFixture(t.id, f.id)
            }, Modifier.weight(1f))
        },
    ) {
        Segmented(listOf(SegOption(s.tmTabRoster), SegOption(s.tmTabFixtures)), tab, { tab = it })
        if (tab == 0) {
            BigButton(s.tmAddPlayer, Icons.Filled.PersonAdd, { adding = true }, Modifier.fillMaxWidth())
            if (t.roster.isEmpty()) Text(s.tmRosterEmpty, color = TsmColors.TextDim)
            for ((i, pid) in t.roster.withIndex()) {
                val p = players.firstOrNull { it.id == pid || pid in it.aliases }
                val problems = p?.let { Teams.problems(t, it, day) }.orEmpty()
                Row(Modifier.fillMaxWidth().clickable(enabled = p != null) { p?.let { c.openStudent(it.id) } }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}.", color = TsmColors.TextDim, modifier = Modifier.padding(end = 6.dp))
                    PlayerAvatar(c, p?.name.orEmpty(), TsmColors.Ball, 36.dp)
                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(p?.name ?: "—", color = TsmColors.TextMain, fontWeight = FontWeight.Bold)
                            if (t.captain == pid) Icon(Icons.Filled.Star, s.tmCaptain, tint = TsmColors.Ball, modifier = Modifier.padding(start = 4.dp))
                        }
                        Text(p?.member?.ranking?.ifBlank { null } ?: "NC", color = TsmColors.TextDim, fontSize = 12.sp)
                        if (problems.isNotEmpty()) Text(problems.joinToString(" · ") { ctProblemText(s, it) }, color = TsmColors.Danger, fontSize = 12.sp)
                    }
                    if (i > 0) IconButton(onClick = { c.saveTeam(t.copy(roster = t.roster.toMutableList().also { l -> l[i] = l[i - 1].also { l[i - 1] = l[i] } })) }) {
                        Icon(Icons.Filled.KeyboardArrowUp, null, tint = TsmColors.TextDim)
                    }
                    IconButton(onClick = { c.saveTeam(t.copy(roster = t.roster - pid, captain = t.captain.takeIf { it != pid })) }) { Icon(Icons.Filled.Close, null, tint = TsmColors.TextDim) }
                }
            }
        } else {
            if (t.fixtures.isEmpty()) Text(s.tmNoFixtures, color = TsmColors.TextDim)
            for (f in t.fixtures.sortedBy { it.at }) {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(TsmColors.Surface).clickable { c.openFixture(t.id, f.id) }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(f.opponent.ifBlank { "—" }, color = TsmColors.TextMain, fontWeight = FontWeight.Bold)
                        Text(c.fixtureWhen(f), color = TsmColors.TextDim, fontSize = 12.sp)
                    }
                    val r = resultLine(s, f)
                    if (r.isNotBlank()) Text(r, color = when (Teams.outcome(f)) { true -> TsmColors.Ok; false -> TsmColors.Danger; else -> TsmColors.TextMain }, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
    if (edit) TeamEditDialog(c, t) { edit = false }
    if (adding) PlayerPickDialog(c, t.roster.toSet()) { p -> adding = false; p?.let { c.saveTeam(t.copy(roster = t.roster + it.id)) } }
}

/** Un incontro: disponibilità, convocati, partite e risultato. */
@Composable
fun FixtureScreen(c: MatchController) {
    val s = LocalStrings.current
    BackHandler { c.back() }
    val list by c.teams.collectAsState()
    val tid by c.openTeamId.collectAsState()
    val fid by c.openFixtureId.collectAsState()
    val t = list.firstOrNull { it.id == tid } ?: return
    val f = t.fixtures.firstOrNull { it.id == fid } ?: return
    val players by c.players.collectAsState()
    var edit by remember { mutableStateOf(f.opponent.isBlank()) }
    var rubber by remember { mutableStateOf<Int?>(null) }
    fun save(x: Fixture) = c.saveFixture(c.team(t.id) ?: t, x)
    fun name(id: String) = players.firstOrNull { it.id == id || id in it.aliases }?.name ?: "—"
    ScreenScaffold(
        onHelp = { c.openHelp("7.17") },
        title = "${t.name} – ${f.opponent.ifBlank { "?" }}",
        subtitle = c.fixtureWhen(f),
        icon = Icons.Filled.EmojiEvents,
        action = { TextButton(onClick = { edit = true }) { Text(s.studentEdit, color = TsmColors.Ball) } },
        bottomBar = { GhostButton(s.back, Icons.AutoMirrored.Filled.ArrowBack, { c.back() }, Modifier.weight(1f)) },
    ) {
        if (f.meeting.isNotBlank()) Text(s.tmMeetingLine(f.meeting), color = TsmColors.TextDim, fontSize = 13.sp)
        if (f.home && f.courts.isNotEmpty()) Text(s.tmCourtsHeld(f.courts.joinToString(", ")), color = TsmColors.TextDim, fontSize = 13.sp)
        val res = resultLine(s, f)
        if (res.isNotBlank()) Text(res, color = when (Teams.outcome(f)) { true -> TsmColors.Ok; false -> TsmColors.Danger; else -> TsmColors.TextMain }, fontWeight = FontWeight.Bold, fontSize = 20.sp)

        // Disponibilità: un tocco per Sì / No / Forse (come hanno risposto al messaggio).
        SectionCard(s.tmAvailability, Icons.Filled.HowToReg) {
            val answers = listOf(null, Availability.YES, Availability.MAYBE, Availability.NO)
            for (pid in t.roster) {
                Text(name(pid), color = TsmColors.TextMain, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Segmented(answers.map { SegOption(when (it) { null -> "—"; Availability.YES -> s.tmYes; Availability.MAYBE -> s.tmMaybe; Availability.NO -> s.tmNo }) },
                    answers.indexOf(f.availability[pid]), { i -> val a = answers[i]; save(f.copy(availability = if (a == null) f.availability - pid else f.availability + (pid to a))) })
            }
            if (t.roster.isEmpty()) Text(s.tmRosterEmpty, color = TsmColors.TextDim, fontSize = 13.sp)
            else {
                GhostButton(s.tmAskAll, Icons.Filled.Campaign, { c.noticeAvailability(t, f, false) }, Modifier.fillMaxWidth())
                if (Teams.noAnswer(t, f).isNotEmpty() && f.availability.isNotEmpty()) GhostButton(s.tmAskMissing(Teams.noAnswer(t, f).size), Icons.Filled.Campaign, { c.noticeAvailability(t, f, true) }, Modifier.fillMaxWidth())
            }
        }

        // Convocati: prima chi ha detto sì, poi forse, poi gli altri; in rosso chi non è in regola quel giorno.
        SectionCard(s.tmCallUp, Icons.Filled.Groups) {
            val order = Teams.candidates(t, f) + t.roster.filter { it !in Teams.candidates(t, f) }
            for (pid in order) {
                val p = players.firstOrNull { it.id == pid } ?: continue
                val probs = Teams.problems(t, p, f.at)
                SwitchRow(Icons.Filled.HowToReg, p.name + (f.availability[pid]?.let { " · " + when (it) { Availability.YES -> s.tmYes; Availability.MAYBE -> s.tmMaybe; Availability.NO -> s.tmNo } } ?: ""),
                    probs.takeIf { it.isNotEmpty() }?.joinToString(" · ") { ctProblemText(s, it) }, pid in f.callUp) { on ->
                    save(f.copy(callUp = if (on) f.callUp + pid else f.callUp - pid))
                }
            }
            if (f.callUp.isNotEmpty()) BigButton(s.tmSendCallUp(f.callUp.size), Icons.Filled.Campaign, { c.noticeCallUp(t, f) }, Modifier.fillMaxWidth())
        }

        // Le partite: i nostri contro i loro, punteggio; si giocano anche col segnapunti.
        SectionCard(s.tmRubbers, Icons.Filled.EmojiEvents) {
            for ((i, r) in f.rubbers.withIndex()) {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(TsmColors.SurfaceHigh).clickable { rubber = i }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(c.rubberName(t, i), color = TsmColors.TextDim, fontSize = 12.sp)
                        Text("${r.ours.map(::name).joinToString(" / ").ifBlank { "—" }} – ${r.theirs.joinToString(" / ").ifBlank { "—" }}", color = TsmColors.TextMain, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Text(r.score.ifBlank { when (r.won) { true -> s.tmWon; false -> s.tmLost; null -> "" } } + if (r.walkover) " " + s.ctWalkover else "", color = when (r.won) { true -> TsmColors.Ok; false -> TsmColors.Danger; null -> TsmColors.TextDim }, fontSize = 13.sp)
                }
            }
            if (f.rubbers.any { it.won != null }) GhostButton(s.tmSendResult, Icons.Filled.Campaign, { c.noticeResult(t, f) }, Modifier.fillMaxWidth())
        }
    }
    if (edit) FixtureEditDialog(c, t, f) { edit = false }
    rubber?.let { i -> f.rubbers.getOrNull(i)?.let { r -> RubberDialog(c, t, f, i, r) { rubber = null } } }
}

/** Una partita dell'incontro: i nostri (dai convocati), i loro, punteggio e chi ha vinto. */
@Composable
private fun RubberDialog(c: MatchController, t: Team, f: Fixture, index: Int, r0: Rubber, onDone: () -> Unit) {
    val s = LocalStrings.current
    val players by c.players.collectAsState()
    var r by remember { mutableStateOf(r0) }
    var theirs1 by remember { mutableStateOf(r0.theirs.getOrNull(0).orEmpty()) }
    var theirs2 by remember { mutableStateOf(r0.theirs.getOrNull(1).orEmpty()) }
    val need = if (r.doubles) 2 else 1
    val pool = f.callUp.ifEmpty { t.roster }
    fun current() = r.copy(theirs = listOf(theirs1, theirs2).take(need).map { it.trim() }.filter { it.isNotEmpty() })
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(c.rubberName(t, index)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(s.tmOurs(need), color = TsmColors.TextDim, fontSize = 13.sp)
                for (pid in pool) {
                    val p = players.firstOrNull { it.id == pid } ?: continue
                    SwitchRow(Icons.Filled.HowToReg, p.name, null, pid in r.ours) { on ->
                        r = r.copy(ours = if (on) (r.ours + pid).takeLast(need) else r.ours - pid)
                    }
                }
                OutlinedTextField(theirs1, { theirs1 = it.take(40) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(s.tmTheirs) })
                if (r.doubles) OutlinedTextField(theirs2, { theirs2 = it.take(40) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(s.tmTheirs2) })
                if (r.ours.size == need && r.won == null) GhostButton(s.ctPlayLive, Icons.Filled.PlayArrow, {
                    val x = current()
                    c.saveFixture(c.team(t.id) ?: t, f.copy(rubbers = f.rubbers.mapIndexed { k, y -> if (k == index) x else y }))
                    onDone()
                    c.playTeamRubber(c.team(t.id) ?: t, (c.team(t.id) ?: t).fixtures.first { it.id == f.id }, index)
                }, Modifier.fillMaxWidth())
                OutlinedTextField(r.score, { v -> r = r.copy(score = v.take(40), walkover = false); ClubTournaments.winnerFromScore(v)?.let { w -> r = r.copy(won = w == "a") } }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(s.tmScoreOurs) })
                val outs = listOf(null, true, false)
                Segmented(outs.map { SegOption(when (it) { null -> "—"; true -> s.tmWeWon; false -> s.tmTheyWon }) }, outs.indexOf(r.won), { r = r.copy(won = outs[it], walkover = false) })
                // Non giocata: vinta a tavolino col punteggio del regolamento.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (us in listOf(true, false)) OutlinedButton(onClick = { r = r.copy(won = us, score = Teams.walkoverScore(us), walkover = true) }, modifier = Modifier.weight(1f)) {
                        Text(if (us) s.tmWalkoverUs else s.tmWalkoverThem, fontSize = 12.sp, textAlign = TextAlign.Center, color = if (r.walkover && r.won == us) TsmColors.Ball else TsmColors.TextMain)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val x = current()
                c.saveFixture(c.team(t.id) ?: t, f.copy(rubbers = f.rubbers.mapIndexed { k, y -> if (k == index) x else y }))
                onDone()
            }) { Text(s.ok) }
        },
        dismissButton = { TextButton(onClick = onDone) { Text(s.back) } },
    )
}

/** L'incontro: avversario, giorno e ora, casa o trasferta, dove, ritrovo, campi tenuti in casa, note. */
@Composable
private fun FixtureEditDialog(c: MatchController, t: Team, f0: Fixture, onDone: () -> Unit) {
    val s = LocalStrings.current
    val context = LocalContext.current
    val lang = c.options.value.lang
    val courts by c.clubCourts.collectAsState()
    var f by remember { mutableStateOf(f0) }
    var askDelete by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(if (f0.opponent.isBlank()) s.tmNewFixture else f0.opponent) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(f.opponent, { f = f.copy(opponent = it.take(50)) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(s.tmOpponent) })
                DateButton(s.planDay, f.at, lang) { d ->
                    val old = Calendar.getInstance().apply { timeInMillis = f.at }
                    f = f.copy(at = CourtPlans.at(d, old.get(Calendar.HOUR_OF_DAY), old.get(Calendar.MINUTE)))
                }
                GhostButton("${s.ctTime}: ${com.tennis.scoremanager.data.Reports.time(f.at, lang)}", Icons.Filled.Schedule, {
                    val cal = Calendar.getInstance().apply { timeInMillis = f.at }
                    TimePickerDialog(context, { _, h, m -> f = f.copy(at = CourtPlans.at(f.at, h, m)) }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
                }, Modifier.fillMaxWidth())
                Segmented(listOf(SegOption(s.tmHome), SegOption(s.tmAway)), if (f.home) 0 else 1, { f = f.copy(home = it == 0) })
                OutlinedTextField(f.place, { f = f.copy(place = it.take(80)) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(s.tmPlace) })
                OutlinedTextField(f.meeting, { f = f.copy(meeting = it.take(80)) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(s.tmMeeting) })
                if (f.home && courts.isNotEmpty()) {
                    Text(s.tmHoldCourts, color = TsmColors.TextDim, fontSize = 13.sp)
                    for (ct in courts) SwitchRow(Icons.Filled.Block, ct, null, ct in f.courts) { on -> f = f.copy(courts = if (on) f.courts + ct else f.courts - ct) }
                    if (f.courts.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(s.tmHours(f.hours), color = TsmColors.TextMain, modifier = Modifier.weight(1f))
                        IconButton(onClick = { f = f.copy(hours = (f.hours - 1).coerceAtLeast(1)) }) { Icon(Icons.Filled.Remove, null) }
                        IconButton(onClick = { f = f.copy(hours = (f.hours + 1).coerceAtMost(14)) }) { Icon(Icons.Filled.Add, null) }
                    }
                }
                OutlinedTextField(f.note, { f = f.copy(note = it.take(200)) }, Modifier.fillMaxWidth(), label = { Text(s.bkNote) })
                TextButton(onClick = { askDelete = true }) { Text(s.tmDeleteFixture, color = TsmColors.Danger) }
            }
        },
        confirmButton = { TextButton(enabled = f.opponent.isNotBlank(), onClick = { c.saveFixture(c.team(t.id) ?: t, f.copy(opponent = f.opponent.trim())); onDone() }) { Text(s.ok) } },
        dismissButton = { TextButton(onClick = onDone) { Text(s.back) } },
    )
    if (askDelete) AlertDialog(
        onDismissRequest = { askDelete = false },
        text = { Text(s.tmDeleteFixtureAsk) },
        confirmButton = {
            TextButton(onClick = {
                askDelete = false
                val cur = c.team(t.id) ?: t
                c.saveTeam(cur.copy(fixtures = cur.fixtures.filter { it.id != f0.id }))
                onDone(); c.back()
            }) { Text(s.tmDeleteFixture, color = TsmColors.Danger) }
        },
        dismissButton = { TextButton(onClick = { askDelete = false }) { Text(s.back) } },
    )
}

/** Squadra nuova o da cambiare: nome, campionato, stagione, FITP, formula, capitano. */
@Composable
private fun TeamEditDialog(c: MatchController, t0: Team?, onDone: (String?) -> Unit) {
    val s = LocalStrings.current
    var t by remember { mutableStateOf(t0 ?: Team(Teams.newId("t"), "", season = Calendar.getInstance().get(Calendar.YEAR).toString(), createdAt = System.currentTimeMillis())) }
    var pickCaptain by remember { mutableStateOf(false) }
    var askDelete by remember { mutableStateOf(false) }
    @Composable fun stepper(label: String, v: Int, onChange: (Int) -> Unit) = Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = TsmColors.TextMain, modifier = Modifier.weight(1f))
        IconButton(onClick = { onChange((v - 1).coerceAtLeast(0)) }) { Icon(Icons.Filled.Remove, null) }
        Text("$v", color = TsmColors.TextMain, fontWeight = FontWeight.Bold)
        IconButton(onClick = { onChange((v + 1).coerceAtMost(9)) }) { Icon(Icons.Filled.Add, null) }
    }
    AlertDialog(
        onDismissRequest = { onDone(null) },
        title = { Text(if (t0 == null) s.tmNew else t0.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(t.name, { t = t.copy(name = it.take(50)) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(s.tmName) })
                OutlinedTextField(t.championship, { t = t.copy(championship = it.take(60)) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(s.tmChampionship) })
                OutlinedTextField(t.season, { t = t.copy(season = it.take(12)) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(s.tmSeason) })
                SwitchRow(Icons.Filled.EmojiEvents, s.tmFitp, s.tmFitpHint, t.fitp) { t = t.copy(fitp = it) }
                stepper(s.tmSingles, t.singles) { t = t.copy(singles = it) }
                stepper(s.tmDoubles, t.doubles) { t = t.copy(doubles = it) }
                val cap = t.captain?.let { c.playerById(it)?.name }
                GhostButton(cap?.let { s.tmCaptainLine(it) } ?: s.tmPickCaptain, Icons.Filled.Star, { pickCaptain = true }, Modifier.fillMaxWidth())
                if (t0 != null) TextButton(onClick = { askDelete = true }) { Text(s.tmDelete, color = TsmColors.Danger) }
            }
        },
        confirmButton = {
            TextButton(enabled = t.name.isNotBlank(), onClick = {
                // Cambiando la formula, gli incontri ancora senza risultati prendono le partite nuove.
                val formula = t0 == null || t0.singles != t.singles || t0.doubles != t.doubles
                val fixed = if (!formula) t.fixtures else t.fixtures.map { f -> if (f.rubbers.none { it.won != null || it.ours.isNotEmpty() }) f.copy(rubbers = Teams.emptyRubbers(t)) else f }
                val saved = t.copy(name = t.name.trim(), fixtures = fixed, roster = (t.roster + listOfNotNull(t.captain)).distinct())
                c.saveTeam(saved)
                onDone(saved.id)
            }) { Text(s.ok) }
        },
        dismissButton = { TextButton(onClick = { onDone(null) }) { Text(s.back) } },
    )
    if (pickCaptain) PlayerPickDialog(c, emptySet()) { p -> pickCaptain = false; p?.let { t = t.copy(captain = it.id) } }
    if (askDelete && t0 != null) AlertDialog(
        onDismissRequest = { askDelete = false },
        text = { Text(s.tmDeleteAsk(t0.name)) },
        confirmButton = { TextButton(onClick = { askDelete = false; c.deleteTeam(t0.id); onDone(null); c.back() }) { Text(s.tmDelete, color = TsmColors.Danger) } },
        dismissButton = { TextButton(onClick = { askDelete = false }) { Text(s.back) } },
    )
}
