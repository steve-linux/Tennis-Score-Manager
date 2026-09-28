package com.tennis.scoremanager.ui.screens

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.SportsTennis
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.tennis.scoremanager.CountdownKind
import com.tennis.scoremanager.CountdownUi
import com.tennis.scoremanager.MatchController
import com.tennis.scoremanager.ble.BandInfo
import com.tennis.scoremanager.ble.LinkState
import com.tennis.scoremanager.data.Names
import com.tennis.scoremanager.data.PlayMode
import com.tennis.scoremanager.data.Reports
import com.tennis.scoremanager.model.MatchState
import com.tennis.scoremanager.model.Side
import com.tennis.scoremanager.ui.BandSettingsPanel
import com.tennis.scoremanager.ui.LocalStrings
import com.tennis.scoremanager.ui.Pill
import com.tennis.scoremanager.ui.SegOption
import com.tennis.scoremanager.ui.Segmented
import com.tennis.scoremanager.ui.Strings
import com.tennis.scoremanager.ui.TsmColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchScreen(c: MatchController) {
    val s = LocalStrings.current
    val context = LocalContext.current
    val activity = context as? Activity
    val liveMatch by c.live.collectAsState()
    val lm = liveMatch ?: return
    val clock by c.clockMs.collectAsState()
    val cd by c.countdown.collectAsState()
    val msg by c.message.collectAsState()
    val end by c.endDialog.collectAsState()
    val serveOrder by c.serveOrderPrompt.collectAsState()
    val o by c.options.collectAsState()
    val bands by c.ble.bands.collectAsState()
    val names = remember(lm.record.setup, s) { Names(lm.record.setup, s) }
    val state = lm.state
    val suspended = lm.record.suspended
    var confirmNew by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }
    var bandSheet by remember { mutableStateOf<Side?>(null) }

    BackHandler { Toast.makeText(context, s.backDisabled, Toast.LENGTH_SHORT).show() }

    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TimersRow(clock, cd, s)
        if (o.mode == PlayMode.BANDS) BandStatusRow(bands, c.bandBattery.collectAsState().value) { bandSheet = it }
        MessageBox(msg)
        Scoreboard(state, names, s)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            PointButtons(state, names, s, enabled = !suspended && !state.isFinished) { c.awardPoint(it) }
            if (suspended) {
                Box(
                    Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)).background(Color(0xCC0B1220)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.Pause, null, tint = TsmColors.Orange, modifier = Modifier.size(56.dp))
                        Text(s.suspendedOverlay, color = TsmColors.Orange, fontSize = 26.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ControlButton(Icons.AutoMirrored.Filled.Undo, s.undoPoint, Modifier.weight(1f)) { c.undo() }
            ControlButton(
                if (suspended) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                if (suspended) s.resume else s.suspend,
                Modifier.weight(1f),
                highlight = suspended,
            ) { c.toggleSuspend() }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // Audio solo icona (barrata quando è spento): lascia spazio a "Nuova partita" ed "Esci".
            ControlButton(
                if (o.audio) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                null,
                Modifier.width(64.dp),
                contentDescription = if (o.audio) s.audioOn else s.audioOff,
            ) { c.toggleAudio() }
            ControlButton(Icons.Filled.AddCircle, s.newMatch, Modifier.weight(1.3f)) { confirmNew = true }
            ControlButton(Icons.AutoMirrored.Filled.ExitToApp, s.exit, Modifier.weight(1f)) { confirmExit = true }
        }
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            icon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, null, tint = TsmColors.Orange) },
            title = { Text(s.exitConfirmTitle) },
            text = {
                Text(s.exitConfirmText + if (o.mode == PlayMode.BANDS && o.bandsOffAtEnd) " " + s.exitConfirmBands else "")
            },
            confirmButton = {
                Button(onClick = {
                    confirmExit = false
                    activity?.let { c.exitApp(it) }
                }) { Text(s.exit) }
            },
            dismissButton = { TextButton(onClick = { confirmExit = false }) { Text(s.cancel) } },
        )
    }

    bandSheet?.let { first ->
        var side by remember(first) { mutableStateOf(first) }
        ModalBottomSheet(onDismissRequest = { bandSheet = null }, containerColor = TsmColors.Surface) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(s.bandSettings, color = TsmColors.TextMain, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Segmented(
                    Side.entries.map { SegOption(names.short(it), Icons.Filled.Watch, TsmColors.player(it), TsmColors.onPlayer(it)) },
                    selected = side.ordinal,
                    onSelect = { side = Side.entries[it] },
                )
                BandSettingsPanel(c, side)
            }
        }
    }

    if (end && state.isFinished) {
        val w = state.winner ?: Side.P1
        AlertDialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            icon = { Icon(Icons.Filled.EmojiEvents, null, tint = TsmColors.player(w), modifier = Modifier.size(40.dp)) },
            title = { Text(s.endDialogTitle, fontWeight = FontWeight.Black) },
            text = {
                Text(
                    s.endDialogText(names.side(w), Reports.scoreLine(state, w)),
                    fontSize = 18.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(
                    onClick = { c.confirmEnd() },
                    colors = ButtonDefaults.buttonColors(containerColor = TsmColors.Ball, contentColor = TsmColors.OnBall),
                ) {
                    Icon(Icons.Filled.Check, null)
                    Spacer(Modifier.width(6.dp))
                    Text(s.matchConcluded, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { c.undo() }) {
                    Icon(Icons.AutoMirrored.Filled.Undo, null, tint = TsmColors.TextMain)
                    Spacer(Modifier.width(6.dp))
                    Text(s.undoLastPoint, color = TsmColors.TextMain)
                }
            },
        )
    }

    if (confirmNew) {
        AlertDialog(
            onDismissRequest = { confirmNew = false },
            icon = { Icon(Icons.Filled.AddCircle, null, tint = TsmColors.Orange) },
            title = { Text(s.newMatchConfirmTitle) },
            text = { Text(s.newMatchConfirmText) },
            confirmButton = {
                Button(onClick = {
                    confirmNew = false
                    c.newMatch()
                }) { Text(s.newMatch) }
            },
            dismissButton = { TextButton(onClick = { confirmNew = false }) { Text(s.cancel) } },
        )
    }

    if (serveOrder && state.rules.doubles && !end) {
        var p1 by remember(state.setNumber) { mutableIntStateOf(state.order1) }
        var p2 by remember(state.setNumber) { mutableIntStateOf(state.order2) }
        AlertDialog(
            onDismissRequest = { c.setServeOrder(p1, p2) },
            icon = { Icon(Icons.Filled.SportsTennis, null, tint = TsmColors.Ball) },
            title = { Text(s.serveOrderTitle(state.setNumber)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (side in listOf(state.setStartServer, state.setStartServer.other)) {
                        Text(s.whoServesFirst(names.short(side)), color = TsmColors.player(side), fontWeight = FontWeight.Bold)
                        Segmented(
                            names.players(side).map { SegOption(it, null, TsmColors.player(side), TsmColors.onPlayer(side)) },
                            selected = if (side == Side.P1) p1 else p2,
                            onSelect = { i -> if (side == Side.P1) p1 = i else p2 = i },
                        )
                    }
                }
            },
            confirmButton = { Button(onClick = { c.setServeOrder(p1, p2) }) { Text(s.confirm) } },
        )
    }
}

private fun formatClock(ms: Long): String {
    val t = ms / 1000
    return String.format(java.util.Locale.ROOT, "%02d:%02d:%02d", t / 3600, (t / 60) % 60, t % 60)
}

private fun formatCountdown(sec: Int): String = if (sec >= 60) String.format(java.util.Locale.ROOT, "%d:%02d", sec / 60, sec % 60) else sec.toString()

@Composable
private fun TimersRow(clock: Long, cd: CountdownUi?, s: Strings) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column {
            Text(s.matchTime, color = TsmColors.TextDim, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(formatClock(clock), color = TsmColors.TextMain, fontSize = 30.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            val label = when (cd?.kind) {
                CountdownKind.CHANGEOVER -> s.changeoverTime
                CountdownKind.SET_BREAK -> s.setBreakTime
                CountdownKind.TIEBREAK_BREAK -> s.tiebreakTime
                else -> s.shotClock
            }
            val red = cd != null && cd.seconds <= 5
            val color by animateColorAsState(if (red) TsmColors.Danger else TsmColors.TextMain, label = "cd")
            Text(label, color = if (cd?.kind == CountdownKind.SHOT_CLOCK || cd == null) TsmColors.TextDim else TsmColors.Orange, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(cd?.let { formatCountdown(it.seconds) } ?: "--", color = color, fontSize = 30.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }
    }
}

/** Stato dei braccialetti; toccandone uno si aprono le sue impostazioni. */
@Composable
private fun BandStatusRow(bands: Map<Side, BandInfo>, battery: Map<Side, com.tennis.scoremanager.BandBattery>, onOpen: (Side) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        for (side in Side.entries) {
            val b = bands[side]
            val (icon, ok) = when (b?.state) {
                LinkState.READY -> Icons.Filled.BluetoothConnected to true
                LinkState.POWERED_OFF -> Icons.Filled.PowerSettingsNew to false
                else -> Icons.Filled.BluetoothDisabled to false
            }
            val bat = battery[side]
            val low = bat != null && !bat.charging && bat.percent <= 20
            Pill(
                (if (side == Side.P1) "G1" else "G2") +
                    ((bat?.percent ?: b?.battery)?.let { " · $it%" } ?: "") +
                    (bat?.leftText()?.let { " · $it" } ?: ""),
                when {
                    low -> TsmColors.Danger
                    ok -> TsmColors.player(side)
                    else -> TsmColors.SurfaceHigh
                },
                if (low) TsmColors.TextMain else if (ok) TsmColors.onPlayer(side) else TsmColors.TextDim,
                icon,
                onClick = { onOpen(side) },
            )
        }
        Spacer(Modifier.weight(1f))
        Icon(
            Icons.Filled.Tune, null, tint = TsmColors.TextDim,
            modifier = Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)).clickable { onOpen(Side.P1) },
        )
    }
}

/** Riquadro arancione: spento, si accende 5 secondi con il messaggio. */
@Composable
private fun MessageBox(msg: String?) {
    val bg by animateColorAsState(if (msg != null) TsmColors.Orange else TsmColors.Surface, label = "msg")
    Box(
        Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(14.dp)).background(bg)
            .border(1.dp, if (msg != null) TsmColors.Orange else TsmColors.Outline, RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(msg, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "msgText") { m ->
            if (m != null) {
                Text(m, color = TsmColors.OnOrange, fontWeight = FontWeight.Black, fontSize = 18.sp, maxLines = 2, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun Scoreboard(state: MatchState, names: Names, s: Strings) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(TsmColors.Surface).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            for (i in state.sets.indices) HeaderCell("S${i + 1}", 30.dp)
            HeaderCell(s.setsHeader, 52.dp)
            HeaderCell(s.gamesHeader, 58.dp)
        }
        for (side in Side.entries) {
            val serving = !state.isFinished && state.server == side
            Row(Modifier.fillMaxWidth().height(if (state.rules.doubles) 52.dp else 44.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(6.dp).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(TsmColors.player(side)))
                Spacer(Modifier.width(8.dp))
                Box(Modifier.width(20.dp), contentAlignment = Alignment.Center) {
                    if (serving) Icon(Icons.Filled.SportsTennis, null, tint = TsmColors.Ball, modifier = Modifier.size(18.dp))
                }
                Column(Modifier.weight(1f)) {
                    val players = names.players(side)
                    players.forEachIndexed { i, p ->
                        val bold = !state.rules.doubles || (serving && state.serverPlayer == i)
                        Text(
                            p, color = TsmColors.player(side), maxLines = 1, overflow = TextOverflow.Ellipsis,
                            fontSize = if (players.size > 1) 15.sp else 20.sp,
                            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
                for (set in state.sets) {
                    Box(Modifier.width(30.dp), contentAlignment = Alignment.Center) {
                        Row(verticalAlignment = Alignment.Top) {
                            Text(
                                set.shown(side).toString(),
                                color = if (set.winner == side) TsmColors.TextMain else TsmColors.TextDim,
                                fontSize = if (set.matchTiebreak) 15.sp else 18.sp,
                                fontWeight = if (set.winner == side) FontWeight.Bold else FontWeight.Normal,
                            )
                            if (set.hasTiebreak && !set.matchTiebreak && set.winner != side) {
                                Text(set.tb(side).toString(), color = TsmColors.TextDim, fontSize = 10.sp)
                            }
                        }
                    }
                }
                ScoreCell(state.setsWon(side).toString(), 52.dp, TsmColors.SurfaceHigh, TsmColors.TextMain)
                Spacer(Modifier.width(4.dp))
                ScoreCell(state.games(side).toString(), 54.dp, TsmColors.player(side).copy(alpha = 0.22f), TsmColors.player(side))
            }
        }
    }
}

@Composable
private fun HeaderCell(text: String, width: androidx.compose.ui.unit.Dp) {
    Text(text, color = TsmColors.TextDim, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.width(width))
}

@Composable
private fun ScoreCell(text: String, width: androidx.compose.ui.unit.Dp, bg: Color, fg: Color) {
    Box(Modifier.width(width).height(40.dp).clip(RoundedCornerShape(10.dp)).background(bg), contentAlignment = Alignment.Center) {
        Text(text, color = fg, fontSize = 26.sp, fontWeight = FontWeight.Black)
    }
}

/**
 * I due tasti quadrati seguono la posizione reale dei giocatori vista dal giudice di sedia:
 * a ogni cambio campo si scambiano di posto.
 */
@Composable
private fun PointButtons(state: MatchState, names: Names, s: Strings, enabled: Boolean, onPoint: (Side) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val gap = 12.dp
        val label = 30.dp
        val sizeDp = min((maxWidth - gap) / 2, maxHeight - label - 6.dp).coerceAtLeast(0.dp)
        Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(gap)) {
            val left = state.leftSide()
            for (side in listOf(left, left.other)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        onClick = { onPoint(side) },
                        enabled = enabled,
                        shape = RoundedCornerShape(24.dp),
                        color = TsmColors.player(side),
                        shadowElevation = 6.dp,
                        modifier = Modifier.size(sizeDp),
                    ) {
                        Column(
                            Modifier.fillMaxSize().padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                names.short(side), color = TsmColors.onPlayer(side), fontWeight = FontWeight.Bold,
                                fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                            )
                            Text(
                                state.pointLabel(side), color = TsmColors.onPlayer(side),
                                fontSize = (sizeDp.value * 0.42f).coerceIn(40f, 110f).sp, fontWeight = FontWeight.Black,
                            )
                            Text(
                                if (state.inTiebreak) "TIE-BREAK" else " ", color = TsmColors.onPlayer(side).copy(alpha = 0.8f),
                                fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.height(label), contentAlignment = Alignment.Center) {
                        if (!state.isFinished && state.server == side) {
                            val who = if (state.rules.doubles) " · " + names.player(side, state.serverPlayer) else ""
                            Pill(s.onServe + who, TsmColors.Ball, TsmColors.OnBall, Icons.Filled.SportsTennis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ControlButton(
    icon: ImageVector,
    text: String?,
    modifier: Modifier,
    highlight: Boolean = false,
    contentDescription: String? = null,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (highlight) TsmColors.Orange else TsmColors.SurfaceHigh,
        modifier = modifier.height(52.dp),
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(icon, contentDescription, tint = if (highlight) TsmColors.OnOrange else TsmColors.TextMain)
            if (text != null) {
                Spacer(Modifier.width(8.dp))
                Text(text, color = if (highlight) TsmColors.OnOrange else TsmColors.TextMain, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
