// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.ui.screens

import android.content.Intent
import androidx.core.net.toUri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.SportsTennis
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tennis.scoremanager.MatchController
import com.tennis.scoremanager.arena.Arena
import com.tennis.scoremanager.ble.LinkState
import com.tennis.scoremanager.model.Side
import com.tennis.scoremanager.ui.GhostButton
import com.tennis.scoremanager.ui.LocalStrings
import com.tennis.scoremanager.ui.QrCode
import com.tennis.scoremanager.ui.ScreenScaffold
import com.tennis.scoremanager.ui.SectionCard
import com.tennis.scoremanager.ui.SegOption
import com.tennis.scoremanager.ui.Segmented
import com.tennis.scoremanager.ui.SwitchRow
import com.tennis.scoremanager.ui.TsmColors

/**
 * TSM Arena: tennis sulla TV coi braccialetti come racchette. Qui si scelgono giocatori e opzioni e si vede
 * l'indirizzo da aprire sulla TV; il gioco vero è la pagina arena.html, servita da questo telefono.
 */
@Composable
fun ArenaScreen(c: MatchController) {
    val s = LocalStrings.current
    val context = LocalContext.current
    BackHandler { c.back() }
    // Si gioca guardando la TV: lo schermo del telefono non deve spegnersi (l'app resterebbe in pausa).
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    // App appena installata: senza "Dispositivi nelle vicinanze" i braccialetti non si vedono e la schermata direbbe solo
    // "non collegato". I permessi si chiedono qui, e appena concessi i braccialetti si ricollegano.
    val permLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions(),
    ) { c.arenaBandsAgain() }
    LaunchedEffect(Unit) {
        if (!c.ble.hasPermissions()) permLauncher.launch(com.tennis.scoremanager.ble.BleManager.requiredPermissions())
    }
    val a by c.arena.collectAsState()
    val bands by c.ble.bands.collectAsState()
    val students by c.students.collectAsState()
    val addresses by c.tvServer.addresses.collectAsState()
    val port by c.tvServer.port.collectAsState()
    val screens by c.tvServer.arenaClients.collectAsState()

    ScreenScaffold(
        onHelp = { c.openHelp("8.4") },
        title = s.arenaTitle,
        subtitle = s.arenaHint,
        icon = Icons.Filled.SportsEsports,
        bottomBar = {
            GhostButton(s.back, Icons.AutoMirrored.Filled.ArrowBack, { c.back() }, Modifier.weight(1f))
            GhostButton(s.arenaPreview, Icons.Filled.OpenInBrowser, {
                port?.let { p -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, "http://127.0.0.1:$p/arena".toUri())) } }
            }, Modifier.weight(1f), enabled = port != null)
        },
    ) {
        // --- TV
        SectionCard(s.arenaTv, Icons.Filled.Tv) {
            val url = port?.let { c.arenaUrl(addresses.firstOrNull()) }
            if (url == null) {
                Text(s.tvNoNetwork, color = TsmColors.Orange, fontSize = 14.sp)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    QrCode(url, 120.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(s.tvAddress, color = TsmColors.TextDim, fontSize = 12.sp)
                        Text(url.removePrefix("http://"), color = TsmColors.Ball, fontSize = 17.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        Text(s.arenaScreens(screens), color = if (screens > 0) TsmColors.Ok else TsmColors.TextDim, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Text(s.arenaTvHint, color = TsmColors.TextDim, fontSize = 13.sp)
        }

        // --- Giocatori e braccialetti
        SectionCard(s.arenaPlayers, Icons.Filled.Groups) {
            Segmented(
                listOf(SegOption(s.arenaVsCpu, Icons.Filled.Person), SegOption(s.arenaTwoPlayers, Icons.Filled.Groups)),
                if (a.twoPlayers) 1 else 0,
                { i -> c.updateArena { it.copy(twoPlayers = i == 1) } },
            )
            val sides = if (a.twoPlayers) Side.entries else listOf(Side.P1)
            for (side in sides) {
                val chosen = if (side == Side.P1) a.p1 else a.p2
                PlayerPick(
                    label = c.arenaName(side),
                    options = listOf(null to (if (side == Side.P1) s.arenaYou else s.player2)) + students.map { it.id to it.name },
                    selected = chosen,
                ) { id -> c.updateArena { if (side == Side.P1) it.copy(p1 = id) else it.copy(p2 = id) } }
                val info = bands[side]
                val (text, ok) = when {
                    info == null -> s.arenaBandMissing to false
                    info.state != LinkState.READY -> s.arenaBandMissing to false
                    info.settings?.supportsArena == false -> s.arenaBandOld to false
                    else -> "${s.arenaBandReady} · ${info.name}" to true
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp)) {
                    Icon(
                        if (ok) Icons.Filled.Bluetooth else Icons.Filled.BluetoothDisabled, null,
                        tint = if (ok) TsmColors.Ok else TsmColors.Orange, modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(text, color = if (ok) TsmColors.TextMain else TsmColors.Orange, fontSize = 14.sp)
                }
            }
            Text(s.arenaHowTo, color = TsmColors.TextDim, fontSize = 13.sp)
        }

        // --- Partita
        SectionCard(s.arenaMatch, Icons.Filled.SportsTennis) {
            if (!a.twoPlayers) {
                Text(s.arenaLevel, color = TsmColors.TextMain, fontWeight = FontWeight.SemiBold)
                Segmented(
                    listOf(SegOption(s.arenaEasy), SegOption(s.arenaNormal), SegOption(s.arenaHard)),
                    (a.level - 1).coerceIn(0, 2),
                    { i -> c.updateArena { it.copy(level = i + 1) } },
                )
            }
            Text(s.arenaGames, color = TsmColors.TextMain, fontWeight = FontWeight.SemiBold)
            Segmented(
                Arena.GAME_CHOICES.map { SegOption(it.toString()) },
                Arena.GAME_CHOICES.indexOf(a.games).coerceAtLeast(0),
                { i -> c.updateArena { it.copy(games = Arena.GAME_CHOICES[i]) } },
            )
            SwitchRow(Icons.Filled.Verified, s.arenaStrict, s.arenaStrictHint, a.strict) { v -> c.updateArena { it.copy(strict = v) } }
            SwitchRow(Icons.Filled.RecordVoiceOver, s.arenaVoice, null, a.voice) { v -> c.updateArena { it.copy(voice = v) } }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Timer, null, tint = TsmColors.TextDim)
                Spacer(Modifier.width(12.dp))
                Text(s.arenaTvLag, color = TsmColors.TextMain, fontWeight = FontWeight.SemiBold)
            }
            Segmented(
                listOf(SegOption(s.arenaLagLow), SegOption(s.arenaLagMedium), SegOption(s.arenaLagHigh)),
                a.tvLag.coerceIn(0, 2),
                { i -> c.updateArena { it.copy(tvLag = i) } },
            )
            Text(s.arenaTvLagHint, color = TsmColors.TextDim, fontSize = 13.sp)
        }

        // --- Grafica sulla TV
        SectionCard(s.arenaGraphics, Icons.Filled.ViewInAr) {
            Segmented(
                listOf(SegOption(s.arenaGfx2d), SegOption(s.arenaGfxLow), SegOption(s.arenaGfxReal)),
                a.graphics.coerceIn(0, 2),
                { i -> c.updateArena { it.copy(graphics = i) } },
            )
            Text(s.arenaGfxHint, color = TsmColors.TextDim, fontSize = 13.sp)
        }
    }
}

/** Chi gioca con un braccialetto: tu (o Giocatore 2) oppure uno degli allievi. */
@Composable
private fun PlayerPick(label: String, options: List<Pair<String?, String>>, selected: String?, onPick: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.fillMaxWidth().clickable { open = true }.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Person, null, tint = TsmColors.Ball)
            Spacer(Modifier.width(12.dp))
            Text("$label ▾", color = TsmColors.TextMain, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.weight(1f))
        }
        DropdownMenu(open, { open = false }) {
            for ((id, name) in options) {
                DropdownMenuItem(
                    text = { Text(name, fontWeight = if (id == selected) FontWeight.Bold else FontWeight.Normal) },
                    onClick = {
                        open = false
                        onPick(id)
                    },
                )
            }
        }
    }
}
