// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.CardMembership
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SportsTennis
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Check
import com.tennis.scoremanager.HomeTab
import com.tennis.scoremanager.ClubFeature
import com.tennis.scoremanager.MatchController
import com.tennis.scoremanager.PlayerFeature
import com.tennis.scoremanager.Screen
import com.tennis.scoremanager.ble.LinkState
import com.tennis.scoremanager.ble.StrapState
import com.tennis.scoremanager.data.PlayerStats
import com.tennis.scoremanager.model.Side
import com.tennis.scoremanager.tv.DisplayActivity
import com.tennis.scoremanager.ui.LocalStrings
import com.tennis.scoremanager.ui.ScreenScaffold
import com.tennis.scoremanager.ui.ServeTrainingCard
import com.tennis.scoremanager.ui.TsmColors
import com.tennis.scoremanager.video.CameraActivity

/**
 * Prima schermata: le schede in basso del ruolo attivo, ognuna per una cosa da fare (giocare, il circolo, allenarsi, i
 * progressi; per la segreteria oggi, planning, soci e tornei) e Altro per le impostazioni. Ogni voce porta alla sua
 * schermata; "Indietro" da lì torna alla scheda da cui si è partiti.
 */
@Composable
fun HomeScreen(c: MatchController) {
    val s = LocalStrings.current
    val tab by c.homeTab.collectAsState()
    val history by c.history.collectAsState()
    val saved by c.saved.collectAsState()
    // Partite arrivate da un altro telefono con un giocatore che si chiama come uno dei nostri: è lui?
    val questions by c.playerQuestions.collectAsState()
    questions.firstOrNull()?.let { PlayerQuestionDialog(c, it) }
    // Prima accensione: le domande, solo a chi non ha ancora partite (gli altri la trovano in Altro).
    var tour by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var asked by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        c.refreshHistory()
        c.refreshSaved()
        // Le quattro cartelle dei colpi in Documenti (e le clip di prima spostate lì), una volta per avvio.
        c.prepareStrokeFolders()
        kotlinx.coroutines.delay(400)
        if (!asked && !c.storage.onboarded && c.history.value.isEmpty() && c.saved.value.isEmpty()) tour = true
        asked = true
    }
    if (tour) com.tennis.scoremanager.ui.OnboardingFlow(c) { tour = false }
    // Le schede del ruolo attivo (Tu, Allenatore, Circolo); una scheda che il ruolo non ha porta alla sua prima.
    val role by c.activeRole.collectAsState()
    // Le schede che restano vuote con quello che non si usa spariscono.
    val pf by c.playerFeatures.collectAsState()
    val cf by c.clubFeatures.collectAsState()
    val accounts by c.bookingAccounts.collectAsState()
    val tabs = role.tabs.filter { t ->
        when (t) {
            // Il circolo di chi gioca: solo dopo esserci entrati con l'invito della segreteria.
            HomeTab.BOOKING -> accounts.isNotEmpty()
            HomeTab.TRAIN -> PlayerFeature.HEART in pf || PlayerFeature.TRAINING in pf
            HomeTab.PROGRESS -> PlayerFeature.PROGRESS in pf || PlayerFeature.RACKETS in pf || PlayerFeature.TRAINING in pf || PlayerFeature.VIDEO in pf
            HomeTab.COURTS -> ClubFeature.PLANNING in cf
            HomeTab.PEOPLE -> ClubFeature.MEMBERS in cf || ClubFeature.PAYMENTS in cf || ClubFeature.SCHOOL in cf
            HomeTab.TOURNAMENTS -> ClubFeature.TOURNAMENTS in cf || ClubFeature.TEAMS in cf || ClubFeature.LADDER in cf
            else -> true
        }
    }
    LaunchedEffect(role, tab, tabs) { if (tab !in tabs) c.homeTab.value = tabs.first() }
    // Da un'altra scheda "Indietro" torna alla prima; dalla prima esce dall'app come sempre.
    BackHandler(enabled = tab != tabs.first()) { c.homeTab.value = tabs.first() }
    val (title, icon) = tabInfo(s, tab)
    ScreenScaffold(
        onHelp = { c.openHelp(tabHelp(tab)) },
        title = title, subtitle = null, icon = icon, bottomBar = { TabBar(tabs, tab) { c.homeTab.value = it } },
        // Il ruolo attivo sempre in alto: da lì si passa a un altro e si scelgono ruoli e funzioni.
        action = { RoleSwitch(c) },
    ) {
        // Promemoria del backup (se acceso in Backup e dati): ogni 30 giorni.
        if (c.backupDue()) Entry(Icons.Filled.Backup, s.backupDueTitle, s.backupDueHint(c.storage.lastBackupAt.takeIf { it > 0 }?.let { com.tennis.scoremanager.data.Reports.date(it, c.options.value.lang) })) { c.go(Screen.DATA) }
        // Un archivio che non si leggeva è stato messo da parte (non sovrascritto): di qui al ripristino del backup.
        val unreadable = c.storage.unreadable.toList()
        if (unreadable.isNotEmpty()) Entry(Icons.Filled.Warning, s.unreadableTitle, s.unreadableHint(unreadable.joinToString(", "))) {
            c.storage.unreadable.clear()
            c.go(Screen.DATA)
        }
        // Chi aveva già l'app: il backup automatico ora è acceso di serie, lo si dice una volta.
        if (!c.storage.autoBackupNoticeSeen && c.storage.personalBackupAuto) Entry(Icons.Filled.Backup, s.autoBackupOnTitle, s.autoBackupOnHint) {
            c.storage.autoBackupNoticeSeen = true
            c.go(Screen.DATA)
        }
        when (tab) {
            // Senza la scheda Progressi (spenta in Cosa usi) lo storico sta in Gioca.
            HomeTab.PLAY -> PlayTab(c, historyHere = HomeTab.PROGRESS !in tabs)
            HomeTab.BOOKING -> BookingTab(c)
            HomeTab.TRAIN -> TrainTab(c)
            HomeTab.PROGRESS -> ProgressTab(c)
            HomeTab.MORE -> MoreTab(c, role) { tour = true }
            HomeTab.STUDENTS -> CoachTab(c)
            HomeTab.TOURNAMENTS -> TournamentsTab(c)
            HomeTab.COURTS -> PlanBody(c, inTab = true)
            HomeTab.PEOPLE -> PeopleTab(c)
            HomeTab.TODAY -> TodayTab(c)
        }
    }
}

/** Titolo e icona di una scheda. */
private fun tabInfo(s: com.tennis.scoremanager.ui.Strings, t: HomeTab): Pair<String, ImageVector> = when (t) {
    HomeTab.PLAY -> s.tabPlay to Icons.Filled.SportsTennis
    HomeTab.BOOKING -> s.roleClub to Icons.Filled.Business
    HomeTab.TRAIN -> s.tabTrain to Icons.Filled.FitnessCenter
    HomeTab.PROGRESS -> s.tabProgress to Icons.AutoMirrored.Filled.TrendingUp
    HomeTab.MORE -> s.tabMore to Icons.Filled.MoreHoriz
    HomeTab.STUDENTS -> s.studentsTitle to Icons.Filled.Groups
    HomeTab.TOURNAMENTS -> s.tabTournaments to Icons.Filled.EmojiEvents
    HomeTab.COURTS -> s.tabPlanning to Icons.Filled.CalendarMonth
    HomeTab.PEOPLE -> s.membersTitle to Icons.Filled.CardMembership
    HomeTab.TODAY -> s.tdTitle to Icons.Filled.Today
}

/** La sezione della guida che apre il "?" di ogni scheda. */
private fun tabHelp(t: HomeTab): String = when (t) {
    HomeTab.PLAY -> "7"
    HomeTab.BOOKING -> "7.18"
    HomeTab.TRAIN -> "7.1"
    HomeTab.PROGRESS -> "7.2"
    HomeTab.STUDENTS -> "7.6"
    HomeTab.TOURNAMENTS -> "7.13"
    HomeTab.COURTS -> "7.15"
    HomeTab.PEOPLE -> "7.11"
    HomeTab.TODAY, HomeTab.MORE -> "3.1"
}

fun roleName(s: com.tennis.scoremanager.ui.Strings, r: com.tennis.scoremanager.Role) = when (r) {
    com.tennis.scoremanager.Role.PLAYER -> s.moreProfile
    com.tennis.scoremanager.Role.COACH -> s.roleCoach
    com.tennis.scoremanager.Role.CLUB -> s.roleClub
}

fun roleIcon(r: com.tennis.scoremanager.Role): ImageVector = when (r) {
    com.tennis.scoremanager.Role.PLAYER -> Icons.Filled.Person
    com.tennis.scoremanager.Role.COACH -> Icons.Filled.School
    com.tennis.scoremanager.Role.CLUB -> Icons.Filled.Business
}

/** Il ruolo attivo, sempre in alto: toccandolo si passa a un altro dei propri ruoli o si scelgono ruoli e funzioni. */
@Composable
private fun RoleSwitch(c: MatchController) {
    val s = LocalStrings.current
    val role by c.activeRole.collectAsState()
    val roles by c.roles.collectAsState()
    var open by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.clip(RoundedCornerShape(12.dp)).background(TsmColors.SurfaceHigh).clickable { open = true }.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(roleIcon(role), null, tint = TsmColors.Ball, modifier = Modifier.size(18.dp))
            Text(" " + roleName(s, role) + " ▾", color = TsmColors.TextMain, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        androidx.compose.material3.DropdownMenu(open, { open = false }) {
            for (r in com.tennis.scoremanager.Role.entries.filter { it in roles }) {
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(roleName(s, r), fontWeight = if (r == role) FontWeight.Bold else FontWeight.Normal) },
                    leadingIcon = { Icon(roleIcon(r), null, tint = if (r == role) TsmColors.Ball else TsmColors.TextDim) },
                    onClick = { open = false; c.setActiveRole(r) },
                )
            }
            androidx.compose.material3.HorizontalDivider()
            androidx.compose.material3.DropdownMenuItem(
                text = { Text(s.rolesFeatures) },
                leadingIcon = { Icon(Icons.Filled.Tune, null, tint = TsmColors.TextDim) },
                onClick = { open = false; settings = true },
            )
        }
    }
    if (settings) com.tennis.scoremanager.ui.RolesFeaturesDialog(c) { settings = false }
}

/** Allenatore: gli allievi e l'allenamento dei colpi di chi si allena con lui, coi suoi esercizi. */
@Composable
private fun CoachTab(c: MatchController) {
    val s = LocalStrings.current
    val students by c.students.collectAsState()
    Entry(Icons.Filled.Groups, s.studentsTitle, s.studentsHint, main = true) { c.openStudents() }
    for (st in students.take(6)) Entry(Icons.Filled.Person, st.name, c.playerHint(st)) { c.openStudent(st.id) }
    // Gli esercizi si scelgono e si gestiscono dentro l'allenamento (Esercizio › Gestisci), una strada sola.
    ServeTrainingCard(c, coach = true)
}

/**
 * Circolo: i tornei, le squadre e le sfide. Il segnapunti parte da lì (Gioca col segnapunti) e le loro partite lasciate
 * a metà si riprendono da qui; il resto del segnapunti (partita libera, storico, tabellone) è del profilo Tu.
 */
@Composable
private fun TournamentsTab(c: MatchController) {
    val s = LocalStrings.current
    val f by c.clubFeatures.collectAsState()
    if (ClubFeature.TOURNAMENTS in f) Entry(Icons.Filled.EmojiEvents, s.ctTitle, s.ctHint, main = true) { c.openClubTournaments() }
    if (ClubFeature.TEAMS in f) Entry(Icons.Filled.Groups, s.tmTitle, s.tmHint, main = ClubFeature.TOURNAMENTS !in f) { c.openTeams() }
    if (ClubFeature.LADDER in f) Entry(Icons.Filled.Leaderboard, s.ldTitle, s.ldHint, main = ClubFeature.TOURNAMENTS !in f && ClubFeature.TEAMS !in f) { c.openLadders() }
    ResumeEntries(c, club = true)
}

/** Circolo: soci, quote, scuola tennis e il messaggio a tutti i soci. */
@Composable
private fun PeopleTab(c: MatchController) {
    val s = LocalStrings.current
    val f by c.clubFeatures.collectAsState()
    if (ClubFeature.MEMBERS in f) Entry(Icons.Filled.CardMembership, s.membersTitle, s.membersHint, main = true) { c.openMembers() }
    if (ClubFeature.PAYMENTS in f) Entry(Icons.Filled.Payments, s.paymentsTitle, s.paymentsHint, main = ClubFeature.MEMBERS !in f) { c.openPayments() }
    if (ClubFeature.SCHOOL in f) Entry(Icons.Filled.School, s.scTitle, s.scHint, main = ClubFeature.MEMBERS !in f && ClubFeature.PAYMENTS !in f) { c.openSchool() }
    if (ClubFeature.MEMBERS in f) Entry(Icons.Filled.Campaign, s.ntMembersAll, null) { c.noticeMembersAll() }
}

@Composable
private fun RowScope.TabBar(tabs: List<HomeTab>, current: HomeTab, onSelect: (HomeTab) -> Unit) {
    val s = LocalStrings.current
    for (t in tabs) {
        val (label, icon) = tabInfo(s, t)
        val on = t == current
        Column(
            Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { onSelect(t) }
                .background(if (on) TsmColors.SurfaceHigh else Color.Transparent).padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, null, tint = if (on) TsmColors.Ball else TsmColors.TextDim)
            // In tedesco o francese le parole sono lunghe: il testo si rimpicciolisce invece di essere tagliato.
            androidx.compose.foundation.text.BasicText(
                label, maxLines = 1,
                style = androidx.compose.ui.text.TextStyle(color = if (on) TsmColors.Ball else TsmColors.TextDim, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal),
                autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 12.sp, stepSize = 0.5.sp),
            )
        }
    }
}

/** Una voce della prima schermata: icona, titolo, una riga di spiegazione; [main] = l'azione principale della scheda. */
@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
internal fun Entry(icon: ImageVector, title: String, hint: String?, main: Boolean = false, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    // Con [onLongClick] la voce si tiene premuta per un'altra azione (eliminare la partita sospesa).
    Surface(shape = RoundedCornerShape(18.dp), color = if (main) TsmColors.Ball else TsmColors.Surface,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = if (main) 18.dp else 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (main) TsmColors.OnBall else TsmColors.Ball, modifier = Modifier.size(if (main) 30.dp else 24.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = if (main) TsmColors.OnBall else TsmColors.TextMain, fontWeight = FontWeight.Bold, fontSize = if (main) 19.sp else 16.sp)
                hint?.let { Text(it, color = if (main) TsmColors.OnBall else TsmColors.TextDim, fontSize = 13.sp) }
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = if (main) TsmColors.OnBall else TsmColors.TextDim)
        }
    }
}

@Composable
internal fun SectionLabel(text: String) {
    Text(text, color = TsmColors.TextDim, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
}

/** In cima a Gioca: cosa è collegato adesso (braccialetti, fasce, telecamera, TV); un tocco porta ai dispositivi. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeviceStatus(c: MatchController) {
    val s = LocalStrings.current
    val bands by c.ble.bands.collectAsState()
    val straps by c.straps.collectAsState()
    val relay by c.relay.ui.collectAsState()
    val tv by c.tv.collectAsState()
    val readyBands = Side.entries.count { bands[it]?.state == LinkState.READY }
    val readyStraps = Side.entries.count { straps[it]?.state == StrapState.READY }
    val chips = buildList {
        if (bands.isNotEmpty()) add(s.statusBands(readyBands) to (readyBands == bands.size))
        if (straps.isNotEmpty()) add(s.statusStraps(readyStraps) to (readyStraps == straps.size))
        if (relay.cameras > 0) add(s.statusCamera to true)
        if (tv.enabled) add(s.statusTv to true)
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (chips.isEmpty()) Chip(s.statusNone, null) { c.go(Screen.DEVICES) }
        for ((label, ok) in chips) Chip(label, ok) { c.go(Screen.DEVICES) }
    }
}

@Composable
private fun Chip(label: String, ok: Boolean?, onClick: () -> Unit) {
    Text(
        label, fontSize = 13.sp, fontWeight = FontWeight.Bold,
        color = when (ok) { true -> TsmColors.Ok; false -> TsmColors.Orange; null -> TsmColors.TextDim },
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(TsmColors.Surface).clickable { onClick() }.padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@Composable
private fun PlayTab(c: MatchController, historyHere: Boolean) {
    val s = LocalStrings.current
    val context = LocalContext.current
    val history by c.history.collectAsState()
    LaunchedEffect(Unit) {
        c.refreshSaved()
        c.refreshHistory()
    }
    val pf by c.playerFeatures.collectAsState()
    // La prima volta: l'invito a tenere solo quello che si usa.
    com.tennis.scoremanager.ui.PlayerFeaturesHint(c)
    // Un obiettivo appena raggiunto: l'avviso resta finché non lo si guarda.
    val goals by c.goals.collectAsState()
    for (g in goals.filter { it.reachedAt != null && !it.seen }) {
        Entry(Icons.Filled.EmojiEvents, s.goalReachedBanner(com.tennis.scoremanager.ui.goalLine(s, g)), s.goalsTitle) { c.goalSeen(g.id); c.openProgress() }
    }
    DeviceStatus(c)
    Entry(Icons.Filled.PlayArrow, s.newMatch, s.playNewMatchHint, main = true) { c.go(Screen.SETUP) }
    // La partita lasciata a metà e la rivincita dell'ultima: quello che di solito si vuole fare subito.
    ResumeEntries(c, club = false)
    history.maxByOrNull { it.record.endedAt ?: it.record.updatedAt }?.takeIf { it.record.finished }?.let { last ->
        val su = last.record.setup
        if (Side.entries.all { PlayerStats.names(su, it).isNotEmpty() }) {
            val names = Side.entries.joinToString(" – ") { PlayerStats.names(su, it).joinToString(" / ") }
            Entry(Icons.Filled.Replay, s.playLikeLast(names), s.playLikeLastHint) { c.startLikeLast(last.record) }
        }
    }
    Entry(Icons.Filled.EmojiEvents, s.playTournament, s.playTournamentHint) { c.friends.show() }
    if (historyHere) Entry(Icons.Filled.History, s.historyTitle, s.progressHistoryHint) { c.openHistory() }
    // Questo telefono come tabellone, come telecamera dell'arbitro o per riprendere da solo.
    SectionLabel(s.playThisPhone)
    Entry(Icons.Filled.Tv, s.displayMode, s.playAsBoardHint) { context.startActivity(Intent(context, DisplayActivity::class.java)) }
    if (PlayerFeature.VIDEO in pf) Entry(Icons.Filled.PlayCircle, s.playAsCamera, s.playAsCameraHint) { c.go(Screen.DEVICES) }
    if (PlayerFeature.VIDEO in pf) Entry(Icons.Filled.Videocam, s.videoRecord, s.videoRecordHint) { context.startActivity(Intent(context, CameraActivity::class.java)) }
}

/**
 * Le partite sospese più recenti, da riprendere con un tocco; tenendo premuto si eliminano (con conferma). Ognuna sta
 * in un profilo solo: quelle di un torneo, di una squadra o di una sfida ([club]) nel Circolo, le altre in Gioca.
 */
@Composable
private fun ResumeEntries(c: MatchController, club: Boolean) {
    val s = LocalStrings.current
    val saved by c.saved.collectAsState()
    LaunchedEffect(Unit) { c.refreshSaved() }
    var toDelete by remember { mutableStateOf<com.tennis.scoremanager.data.MatchRecord?>(null) }
    for (rec in saved.filter { c.isClubRecord(it.id) == club }.take(2)) {
        val lang = c.options.collectAsState().value.lang
        val at = rec.startedAt ?: rec.updatedAt
        // Il punteggio a cui ci si era fermati, accanto alla data.
        val hint = listOf("${com.tennis.scoremanager.data.Reports.date(at, lang)} ${com.tennis.scoremanager.data.Reports.time(at, lang)}", suspendedScore(rec)).filter { it.isNotBlank() }.joinToString(" · ")
        Entry(Icons.Filled.History, s.playResume(matchNames(rec, s)), hint,
            onLongClick = { toDelete = rec }) { c.resumeSaved(rec) }
    }
    toDelete?.let { rec ->
        val n = matchNames(rec, s, " ${s.vs} ")
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text(s.deleteSavedTitle) },
            text = { Text(s.deleteSavedText(n)) },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { c.deleteSaved(rec); toDelete = null }) { Text(s.delete, color = TsmColors.Danger) } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { toDelete = null }) { Text(s.back) } },
        )
    }
}

/**
 * Il circolo di chi gioca (dopo l'invito della segreteria): le novità (bacheca, chat, annunci), prenotare un campo, le
 * proprie prenotazioni, Cerco compagno e la chat del circolo. Se il server non risponde la voce lo dice.
 */
@Composable
private fun BookingTab(c: MatchController) {
    val s = LocalStrings.current
    ClubNews(c)
    val accounts by c.bookingAccounts.collectAsState()
    val reach by c.bookingReach.collectAsState()
    val down = accounts.filter { reach[it.url] == false }
    val names = { l: List<com.tennis.scoremanager.data.BookingAccount> -> l.joinToString(", ") { it.club.ifBlank { it.url } } }
    val hint = when {
        down.size == accounts.size -> s.pbOffline
        down.isNotEmpty() -> s.pbSomeOffline(names(down))
        else -> s.pbEntryJoined(names(accounts))
    }
    Entry(if (down.isEmpty()) Icons.Filled.CalendarMonth else Icons.Filled.CloudOff, s.pbTitle, hint, main = true) { c.go(Screen.BOOK_COURT) }
    Entry(Icons.Filled.EventNote, s.pbMine, s.pbMineEntryHint) { c.bookTabRequest.value = 1; c.go(Screen.BOOK_COURT) }
    Entry(Icons.Filled.PersonSearch, s.pbSeekTitle, s.pbSeekEntryHint) { c.bookTabRequest.value = 2; c.go(Screen.BOOK_COURT) }
    // La chat del circolo, sempre a portata: l'ultimo messaggio e quanti da leggere.
    for (a in accounts.filter { it.joined }) {
        val title = listOfNotNull(s.pbClubChat, a.club.takeIf { accounts.size > 1 && it.isNotBlank() }).joinToString(" · ") +
            if (a.clubUnread > 0) " (${a.clubUnread})" else ""
        Entry(Icons.Filled.Forum, title, a.clubPreview.ifBlank { s.pbClubEntryHint }) { c.selectBooking(a.url); c.chatRequest.value = "club"; c.go(Screen.BOOK_COURT) }
    }
}

/** Tu: l'allenamento con la fascia e l'allenamento dei tuoi colpi (quello degli allievi è nel profilo Allenatore). */
@Composable
private fun TrainTab(c: MatchController) {
    val s = LocalStrings.current
    val pf by c.playerFeatures.collectAsState()
    if (PlayerFeature.HEART in pf) Entry(Icons.Filled.MonitorHeart, s.trainCardio, s.trainCardioHint, main = true) { c.openWorkouts() }
    // Servizio, dritto, rovescio e volée: avvio, sessioni salvate, clip e analisi del movimento.
    if (PlayerFeature.TRAINING in pf) ServeTrainingCard(c, coach = false)
}

@Composable
private fun ProgressTab(c: MatchController) {
    val s = LocalStrings.current
    // Col proprio nome (Altro › Tu) la voce mostra subito il proprio rating.
    val history by c.history.collectAsState()
    val me = c.me()?.name ?: c.storage.myName
    val mine = remember(history, me) {
        if (me.isBlank()) null else c.ratingSports().firstOrNull()?.let { c.ratingTable(it).player(c.myKey()) }
    }
    val pf by c.playerFeatures.collectAsState()
    if (PlayerFeature.PROGRESS in pf) Entry(
        Icons.AutoMirrored.Filled.TrendingUp, "${s.ratingTitle} · ${s.progressTitle}",
        mine?.let { "$me: ${it.rating} · ${s.ratingMatches(it.matches, it.wins)}" } ?: s.progressTrendHint, main = true,
    ) { c.openProgress() }
    Entry(Icons.Filled.History, s.historyTitle, s.progressHistoryHint) { c.openHistory() }
    // Le partite riprese: capitoli, highlights e moviola stanno coi propri progressi.
    if (PlayerFeature.VIDEO in pf) Entry(Icons.Filled.VideoLibrary, s.videoLibrary, s.videoLibraryHint) { c.go(Screen.VIDEOS) }
    var year by remember { mutableStateOf(false) }
    Entry(Icons.Filled.CalendarMonth, s.yrTitle, s.yrHint) { year = true }
    if (year) com.tennis.scoremanager.ui.YearReviewDialog(c) { year = false }
    if (PlayerFeature.RACKETS in pf) Entry(Icons.Filled.SportsTennis, s.racketMine, s.progressRacketsHint) { c.openRackets() }
    // Allenamento dei tuoi colpi: per ogni colpo la percentuale di colpi dentro, sessione dopo sessione (quelli degli
    // allievi stanno nelle loro schede).
    val sessions by c.serveSessions.collectAsState()
    LaunchedEffect(Unit) { c.refreshServeSessions() }
    val ofTrainee = sessions.filter { it.student == null }
    val done = com.tennis.scoremanager.data.TrainedStroke.entries.filter { t -> ofTrainee.any { it.stroke == t } }
    if (done.isNotEmpty() && PlayerFeature.TRAINING in pf) {
        com.tennis.scoremanager.ui.SectionCard(s.progressStrokes, Icons.Filled.SportsTennis) {
            Text(s.progressStrokesHint, color = TsmColors.TextDim, fontSize = 13.sp)
            for (t in done) {
                val mine = ofTrainee.filter { it.stroke == t }
                val title = "${com.tennis.scoremanager.ui.strokeLabel(s, t)} · ${mine.size}"
                Surface(onClick = { c.setTrainStudent(null); c.openStrokeSessions(t) }, color = TsmColors.Surface, modifier = Modifier.fillMaxWidth()) {
                    // Con una sessione sola non c'è linea: basta il nome del colpo.
                    if (mine.count { com.tennis.scoremanager.data.ServeTraining.count(it.serves).pct != null } >= 2) {
                        com.tennis.scoremanager.ui.StrokeTrend(mine, title)
                    } else {
                        Text(title, color = TsmColors.TextMain, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Altro: prima le cose del profilo (chi gioca il suo profilo e i giocatori, la segreteria il circolo), poi quelle
 * dell'app, uguali per tutti: le impostazioni (lingua, voce, dispositivi, ruoli e funzioni, backup, versione) e l'aiuto.
 */
@Composable
private fun MoreTab(c: MatchController, role: com.tennis.scoremanager.Role, onTour: () -> Unit) {
    val s = LocalStrings.current
    when (role) {
        com.tennis.scoremanager.Role.CLUB -> ClubGroup(c)
        com.tennis.scoremanager.Role.PLAYER -> {
            var profile by remember { mutableStateOf(false) }
            val players by c.players.collectAsState()
            val mine = remember(players) { c.me() }
            val me = mine?.name ?: c.storage.myName
            // Col tuo giocatore si apre la tua scheda; senza, si sceglie chi sei.
            Entry(Icons.Filled.Person, if (me.isBlank()) s.moreProfile else "${s.moreProfile}: $me", s.moreProfileHint) {
                if (mine != null) c.openStudent(mine.id) else profile = true
            }
            if (profile) com.tennis.scoremanager.ui.ProfileDialog(c) { profile = false }
            Entry(Icons.Filled.Groups, s.playersTitle, s.playersHint) { c.openPlayers() }
            // Prima di entrare in un circolo: da qui si usa l'invito (poi c'è la scheda Circolo).
            val bookingAccounts by c.bookingAccounts.collectAsState()
            if (bookingAccounts.isEmpty()) Entry(Icons.Filled.CalendarMonth, s.pbHaveInvite, s.pbHaveInviteHint) { c.go(Screen.BOOK_COURT) }
        }
        com.tennis.scoremanager.Role.COACH -> {}
    }
    Entry(Icons.Filled.Settings, s.settingsTitle, s.settingsHint) { c.go(Screen.SETTINGS) }
    SectionLabel(s.moreGroupHelp)
    Entry(Icons.AutoMirrored.Filled.Help, s.moreHelp, s.moreHelpHint) { c.go(Screen.HELP) }
    Entry(Icons.Filled.School, s.moreTour, s.moreTourHint) { onTour() }
}

/** Altro del Circolo: il circolo e i suoi strumenti, nome, server, segreteria da PC, backup (campi e orari nel Planning). */
@Composable
private fun ClubGroup(c: MatchController) {
    val s = LocalStrings.current
    val f by c.clubFeatures.collectAsState()
    val courts by c.clubCourts.collectAsState()
    var edit by remember { mutableStateOf(false) }
    var manage by remember { mutableStateOf(false) }
    var server by remember { mutableStateOf(false) }
    var backup by remember { mutableStateOf(false) }
    SectionLabel(s.moreGroupClub)
    Entry(Icons.Filled.Business, c.storage.clubName.ifBlank { s.clubNoName }, s.clubNameHint) { edit = true }
    // Campi, orari e prezzi stanno nel Planning; senza Planning i campi (per tornei e Nuova partita) si scrivono qui.
    if (ClubFeature.PLANNING !in f) Entry(Icons.Filled.SportsTennis, s.crtTitle, if (courts.isEmpty()) s.clubNoCourts else courts.joinToString(" · ")) { manage = true }
    if (ClubFeature.SERVER in f) {
        val srv by c.clubServer.collectAsState()
        Entry(Icons.Filled.Cloud, s.srvTitle, if (srv.paired) s.srvConnected(srv.club.ifBlank { srv.url }) else s.srvButtonHint) { server = true }
    }
    if (ClubFeature.DESK in f) Entry(Icons.Filled.Computer, s.deskTitle, s.deskHint) { c.go(Screen.DESK) }
    Entry(Icons.Filled.Backup, s.obTitle, s.obHint) { backup = true }
    if (edit) com.tennis.scoremanager.ui.ClubDialog(c) { edit = false }
    if (manage) CourtsManageDialog(c) { manage = false }
    if (server) ClubServerDialog(c) { server = false }
    if (backup) com.tennis.scoremanager.ui.OfficeBackupDialog(c) { backup = false }
}

/** Una domanda alla volta: il giocatore arrivato è uno dei nostri con quel nome, o un'altra persona? */
@Composable
private fun PlayerQuestionDialog(c: MatchController, q: com.tennis.scoremanager.data.Players.Question) {
    val s = LocalStrings.current
    androidx.compose.material3.AlertDialog(
        onDismissRequest = {},
        title = { Text(s.questionTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(s.questionText(q.name))
                for (id in q.candidates) {
                    val p = c.playerById(id) ?: continue
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TsmColors.SurfaceHigh).clickable { c.answerPlayerQuestion(q, p.id) }.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        com.tennis.scoremanager.ui.PlayerAvatar(c, p.name, TsmColors.Ball, 32.dp)
                        Text(" " + s.questionYes(p.name) + (c.playerHint(p)?.let { " · $it" } ?: ""), color = TsmColors.TextMain, fontSize = 14.sp)
                    }
                }
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { c.answerPlayerQuestion(q, null) }) { Text(s.questionNo) } },
    )
}

/**
 * Le novità dai circoli in cui si è entrati: gli annunci di Cerco compagno degli altri non ancora letti, i più
 * vicini prima. Con più circoli, ogni riga dice di quale. Restano finché non si leggono (✓) o si aprono.
 */
@Composable
private fun ClubNews(c: MatchController) {
    val s = LocalStrings.current
    val accounts by c.bookingAccounts.collectAsState()
    val many = accounts.size > 1
    val news = accounts.flatMap { a -> a.unreadSeeks.map { a to it } }.sortedWith(compareBy({ it.second.date }, { it.second.fromMin }))
    // Le chat con messaggi nuovi: la bacheca del circolo e quelle degli annunci in cui si è.
    // Nell'elenco restano le chat degli annunci; bacheca, chat del circolo e private hanno la loro striscia.
    val chats = accounts.flatMap { a -> a.chatNews.filterKeys { it.startsWith("s-") }.map { (room, title) -> Triple(a, room, title) } }
    LaunchedEffect(Unit) { c.refreshClubNews() }
    // La bacheca ha la sua striscia, in cima, col numero degli avvisi da leggere: si tocca e si apre.
    for (a in accounts.filter { it.boardUnread > 0 }) {
        BoardBanner(listOfNotNull(a.club.takeIf { many && it.isNotBlank() }, s.pbBoardUnread(a.boardUnread)).joinToString(" · ")) {
            c.selectBooking(a.url); c.chatRequest.value = "board"; c.go(Screen.BOOK_COURT)
        }
    }
    for (a in accounts) {
        val where = a.club.takeIf { many && it.isNotBlank() }
        if (a.clubUnread > 0) BoardBanner(listOfNotNull(where, s.pbClubUnread(a.clubUnread)).joinToString(" · "), Icons.Filled.Forum) {
            c.selectBooking(a.url); c.chatRequest.value = "club"; c.go(Screen.BOOK_COURT)
        }
        for ((room, name) in a.chatNews.filterKeys { it.startsWith("d-") }) BoardBanner(listOfNotNull(where, s.pbDmFrom(name)).joinToString(" · "), Icons.Filled.Mail) {
            c.selectBooking(a.url); c.chatRequest.value = room; c.go(Screen.BOOK_COURT)
        }
    }
    if (news.isEmpty() && chats.isEmpty()) return
    androidx.compose.foundation.layout.Column(
        Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(18.dp)).background(TsmColors.Surface).padding(14.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
    ) {
        androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.NotificationsActive, null, tint = TsmColors.Ball)
            Text(s.pbNewsTitle(news.size + chats.size), color = TsmColors.TextMain, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f).padding(start = 12.dp))
        }
        Text(s.pbNewsHint, color = TsmColors.TextDim, fontSize = 12.sp)
        for ((a, room, title) in chats) {
            androidx.compose.foundation.layout.Row(
                Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).background(TsmColors.SurfaceHigh)
                    .clickable { c.selectBooking(a.url); if (room.startsWith("s-")) c.bookTabRequest.value = 2 else c.chatRequest.value = room; c.go(Screen.BOOK_COURT) }.padding(start = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.foundation.layout.Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                    Text(if (room == "board") s.pbBoardTitle else s.pbChatNew, color = TsmColors.Ball, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(listOfNotNull(a.club.takeIf { many && it.isNotBlank() }, title.ifBlank { null }).joinToString(" · ").ifBlank { s.pbBoardRead }, color = TsmColors.TextMain, fontSize = 14.sp)
                }
                androidx.compose.material3.IconButton(onClick = { c.markChatRead(a.url, room, System.currentTimeMillis()) }) { Icon(Icons.Filled.Check, s.tdNewsRead, tint = TsmColors.TextDim) }
            }
        }
        for ((a, k) in news.take(6)) {
            androidx.compose.foundation.layout.Row(
                Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).background(TsmColors.SurfaceHigh)
                    .clickable { c.openSeekNews(a.url, k.id) }.padding(start = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.foundation.layout.Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                    Text(s.pbSeekTitle, color = TsmColors.Ball, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(c.seekLine(k, if (many) a.club.ifBlank { null } else null), color = TsmColors.TextMain, fontSize = 14.sp)
                }
                androidx.compose.material3.IconButton(onClick = { c.readSeeks(a.url, listOf(k.id)) }) { Icon(Icons.Filled.Check, s.tdNewsRead, tint = TsmColors.TextDim) }
            }
        }
        if (news.size + chats.size > 1) androidx.compose.material3.TextButton(onClick = {
            accounts.forEach { a -> c.readSeeks(a.url); a.chatNews.keys.forEach { c.markChatRead(a.url, it, System.currentTimeMillis()) } }
        }) { Text(s.tdNewsReadAll) }
    }
}

