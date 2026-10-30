// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.EmojiEvents
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
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Videocam
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
import com.tennis.scoremanager.HomeTab
import com.tennis.scoremanager.MatchController
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
 * Prima schermata: cinque schede in basso, per cosa si vuole fare (giocare, allenarsi, i video, i progressi, il resto).
 * Ogni voce porta alla sua schermata; "Indietro" da lì torna alla scheda da cui si è partiti.
 */
@Composable
fun HomeScreen(c: MatchController) {
    val s = LocalStrings.current
    val tab by c.homeTab.collectAsState()
    val history by c.history.collectAsState()
    val saved by c.saved.collectAsState()
    // Prima accensione: le tre domande, solo a chi non ha ancora partite (gli altri la trovano in Altro).
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
    // Da un'altra scheda "Indietro" torna a Gioca; da Gioca esce dall'app come sempre.
    BackHandler(enabled = tab != HomeTab.PLAY) { c.homeTab.value = HomeTab.PLAY }
    val (title, icon) = when (tab) {
        HomeTab.PLAY -> s.tabPlay to Icons.Filled.SportsTennis
        HomeTab.TRAIN -> s.tabTrain to Icons.Filled.FitnessCenter
        HomeTab.VIDEO -> s.tabVideo to Icons.Filled.Videocam
        HomeTab.PROGRESS -> s.tabProgress to Icons.AutoMirrored.Filled.TrendingUp
        HomeTab.MORE -> s.tabMore to Icons.Filled.MoreHoriz
    }
    ScreenScaffold(title = title, subtitle = null, icon = icon, bottomBar = { TabBar(tab) { c.homeTab.value = it } }) {
        when (tab) {
            HomeTab.PLAY -> PlayTab(c)
            HomeTab.TRAIN -> TrainTab(c)
            HomeTab.VIDEO -> VideoTab(c)
            HomeTab.PROGRESS -> ProgressTab(c)
            HomeTab.MORE -> MoreTab(c) { tour = true }
        }
    }
}

@Composable
private fun RowScope.TabBar(current: HomeTab, onSelect: (HomeTab) -> Unit) {
    val s = LocalStrings.current
    for ((t, label, icon) in listOf(
        Triple(HomeTab.PLAY, s.tabPlay, Icons.Filled.SportsTennis),
        Triple(HomeTab.TRAIN, s.tabTrain, Icons.Filled.FitnessCenter),
        Triple(HomeTab.VIDEO, s.tabVideo, Icons.Filled.Videocam),
        Triple(HomeTab.PROGRESS, s.tabProgress, Icons.AutoMirrored.Filled.TrendingUp),
        Triple(HomeTab.MORE, s.tabMore, Icons.Filled.MoreHoriz),
    )) {
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
private fun Entry(icon: ImageVector, title: String, hint: String?, main: Boolean = false, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(18.dp), color = if (main) TsmColors.Ball else TsmColors.Surface, modifier = Modifier.fillMaxWidth()) {
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
private fun SectionLabel(text: String) {
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
private fun PlayTab(c: MatchController) {
    val s = LocalStrings.current
    val context = LocalContext.current
    val saved by c.saved.collectAsState()
    val history by c.history.collectAsState()
    LaunchedEffect(Unit) {
        c.refreshSaved()
        c.refreshHistory()
    }
    DeviceStatus(c)
    Entry(Icons.Filled.PlayArrow, s.newMatch, s.playNewMatchHint, main = true) { c.go(Screen.SETUP) }
    // La partita lasciata a metà e la rivincita dell'ultima: quello che di solito si vuole fare subito.
    for (rec in saved.take(2)) {
        val names = Side.entries.joinToString(" – ") { PlayerStats.names(rec.setup, it).joinToString(" / ").ifEmpty { "?" } }
        val lang = c.options.value.lang
        val at = rec.startedAt ?: rec.updatedAt
        Entry(Icons.Filled.History, s.playResume(names), "${com.tennis.scoremanager.data.Reports.date(at, lang)} ${com.tennis.scoremanager.data.Reports.time(at, lang)}") { c.resumeSaved(rec) }
    }
    history.maxByOrNull { it.record.endedAt ?: it.record.updatedAt }?.takeIf { it.record.finished }?.let { last ->
        val su = last.record.setup
        if (Side.entries.all { PlayerStats.names(su, it).isNotEmpty() }) {
            val names = Side.entries.joinToString(" – ") { PlayerStats.names(su, it).joinToString(" / ") }
            Entry(Icons.Filled.Replay, s.playRematch(names), null) { c.prepareRematch(last.record) }
        }
    }
    Entry(Icons.Filled.EmojiEvents, s.playTournament, s.playTournamentHint) { c.openTournaments() }
    SectionLabel(s.playUseAs)
    Entry(Icons.Filled.Tv, s.displayMode, s.playAsBoardHint) { context.startActivity(Intent(context, DisplayActivity::class.java)) }
    Entry(Icons.Filled.Videocam, s.playAsCamera, s.playAsCameraHint) { c.go(Screen.DEVICES) }
}

@Composable
private fun TrainTab(c: MatchController) {
    val s = LocalStrings.current
    Entry(Icons.Filled.MonitorHeart, s.trainCardio, s.trainCardioHint, main = true) { c.openWorkouts() }
    // Servizio, dritto, rovescio e volée: avvio, sessioni salvate, clip e analisi del movimento.
    ServeTrainingCard(c)
    // Per l'allenatore: un allievo per scheda, con allenamenti, movimento, partite e note.
    Entry(Icons.Filled.Groups, s.studentsTitle, s.studentsHint) { c.openStudents() }
}

@Composable
private fun VideoTab(c: MatchController) {
    val s = LocalStrings.current
    val context = LocalContext.current
    Entry(Icons.Filled.Videocam, s.videoRecord, s.videoRecordHint, main = true) { context.startActivity(Intent(context, CameraActivity::class.java)) }
    Entry(Icons.Filled.VideoLibrary, s.videoLibrary, s.videoLibraryHint) { c.screen.value = Screen.VIDEOS }
    Entry(Icons.Filled.PlayCircle, s.playAsCamera, s.playAsCameraHint) { c.go(Screen.DEVICES) }
}

@Composable
private fun ProgressTab(c: MatchController) {
    val s = LocalStrings.current
    // Col proprio nome (Altro › Tu) la voce mostra subito il proprio rating.
    val history by c.history.collectAsState()
    val me = c.storage.myName
    val mine = remember(history, me) {
        if (me.isBlank()) null else c.ratingSports().firstOrNull()?.let { c.ratingTable(it).player(PlayerStats.key(me)) }
    }
    Entry(
        Icons.AutoMirrored.Filled.TrendingUp, "${s.ratingTitle} · ${s.progressTitle}",
        mine?.let { "$me: ${it.rating} · ${s.ratingMatches(it.matches, it.wins)}" } ?: s.progressTrendHint, main = true,
    ) { c.openProgress() }
    Entry(Icons.Filled.History, s.historyTitle, s.progressHistoryHint) { c.openHistory() }
    Entry(Icons.Filled.SportsTennis, s.racketMine, s.progressRacketsHint) { c.openRackets() }
    // Allenamento dei colpi: per ogni colpo la percentuale di colpi dentro, sessione dopo sessione.
    val sessions by c.serveSessions.collectAsState()
    LaunchedEffect(Unit) { c.refreshServeSessions() }
    val trainee by c.trainStudent.collectAsState()
    val ofTrainee = sessions.filter { it.student == trainee }
    val done = com.tennis.scoremanager.data.TrainedStroke.entries.filter { t -> ofTrainee.any { it.stroke == t } }
    if (done.isNotEmpty()) {
        com.tennis.scoremanager.ui.SectionCard(s.progressStrokes + (c.studentById(trainee)?.name?.let { " · $it" } ?: ""), Icons.Filled.SportsTennis) {
            Text(s.progressStrokesHint, color = TsmColors.TextDim, fontSize = 13.sp)
            for (t in done) {
                val mine = ofTrainee.filter { it.stroke == t }
                val title = "${com.tennis.scoremanager.ui.strokeLabel(s, t)} · ${mine.size}"
                Surface(onClick = { c.openStrokeSessions(t) }, color = TsmColors.Surface, modifier = Modifier.fillMaxWidth()) {
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

@Composable
private fun MoreTab(c: MatchController, onTour: () -> Unit) {
    val s = LocalStrings.current
    var profile by remember { mutableStateOf(false) }
    val me = c.storage.myName
    Entry(Icons.Filled.Person, if (me.isBlank()) s.moreProfile else "${s.moreProfile}: $me", s.moreProfileHint) { profile = true }
    if (profile) com.tennis.scoremanager.ui.ProfileDialog(c) { profile = false }
    Entry(Icons.Filled.Bluetooth, s.moreDevices, s.moreDevicesHint) { c.go(Screen.DEVICES) }
    Entry(Icons.Filled.Language, s.moreVoice, s.moreVoiceHint) { c.go(Screen.VOICE) }
    Entry(Icons.AutoMirrored.Filled.Help, s.moreHelp, s.moreHelpHint) { c.go(Screen.HELP) }
    Entry(Icons.Filled.SportsTennis, s.racketMine, s.moreRacketsHint) { c.openRackets() }
    Entry(Icons.Filled.SwapHoriz, s.moreTransfer, s.moreTransferHint) { c.openHistory() }
    Entry(Icons.Filled.School, s.moreTour, s.moreTourHint) { onTour() }
    // Versione, licenza e codice sorgente (la GPL chiede di mostrarli).
    AboutFooter()
}
