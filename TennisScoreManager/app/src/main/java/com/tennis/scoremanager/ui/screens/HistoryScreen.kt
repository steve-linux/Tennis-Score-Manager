// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.tennis.scoremanager.LiveMatch
import com.tennis.scoremanager.MatchController
import com.tennis.scoremanager.Screen
import com.tennis.scoremanager.data.Names
import com.tennis.scoremanager.data.PlayerStats
import com.tennis.scoremanager.data.Reports
import com.tennis.scoremanager.model.Side
import com.tennis.scoremanager.ui.BigButton
import com.tennis.scoremanager.ui.EditablePlayerAvatar
import com.tennis.scoremanager.ui.GhostButton
import com.tennis.scoremanager.ui.LocalStrings
import com.tennis.scoremanager.ui.PlayerAvatar
import com.tennis.scoremanager.ui.QrCode
import com.tennis.scoremanager.ui.ScreenScaffold
import com.tennis.scoremanager.ui.SegOption
import com.tennis.scoremanager.ui.Segmented
import com.tennis.scoremanager.ui.TsmColors

/**
 * Storico: le partite finite salvate sul telefono e i giocatori, riconosciuti dal nome, con vittorie, sconfitte
 * e scontri diretti. Da qui si importano anche partite salvate come JSON su un altro telefono.
 */
@Composable
fun HistoryScreen(c: MatchController) {
    val s = LocalStrings.current
    val matches by c.history.collectAsState()
    /** 0 = partite, 1 = giocatori. */
    var tab by rememberSaveable { mutableStateOf(0) }
    /** Giocatore aperto (chiave del nome); null = elenco. */
    var player by rememberSaveable { mutableStateOf<String?>(null) }
    val played = remember(matches) { matches.map { PlayerStats.Played(it.record, it.state.winner) } }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { c.importMatches(it) }
    val scanner = rememberLauncherForActivityResult(ScanContract()) { r -> r.contents?.let { c.receiveFrom(it) } }
    var transferMenu by remember { mutableStateOf(false) }
    val sendUrl by c.transferUrl.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(Unit) { c.refreshHistory() }
    BackHandler { if (player != null) player = null else c.home() }

    ScreenScaffold(
        title = s.historyTitle,
        subtitle = s.historySubtitle,
        icon = Icons.Filled.History,
        bottomBar = {
            GhostButton(s.back, Icons.AutoMirrored.Filled.ArrowBack, { if (player != null) player = null else c.home() },
                Modifier.weight(1f))
            BigButton(s.historyImport, Icons.Filled.FileOpen, { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                Modifier.weight(1f))
        },
        action = {
            Surface(onClick = { transferMenu = true }, shape = RoundedCornerShape(14.dp), color = TsmColors.SurfaceHigh) {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.QrCode2, null, tint = TsmColors.Ball)
                    Text(s.transferTitle, color = TsmColors.TextMain, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        },
    ) {
        val open = player
        if (open != null) {
            PlayerDetail(c, played, matches, open)
            return@ScreenScaffold
        }
        Segmented(listOf(SegOption(s.historyMatches), SegOption(s.historyPlayers)), selected = tab, onSelect = { tab = it })
        if (tab == 0) {
            if (matches.isEmpty()) Text(s.historyEmpty, color = TsmColors.TextDim)
            matches.forEach { MatchRow(it) { c.openFromHistory(it) } }
        } else {
            val players = remember(played) { PlayerStats.players(played) }
            if (players.isEmpty()) Text(s.historyPlayersEmpty, color = TsmColors.TextDim)
            players.forEach { p -> PlayerRow(c, p.name, s.historyRecord(p.won, p.lost)) { player = p.key } }
        }
    }

    if (transferMenu) {
        AlertDialog(
            onDismissRequest = { transferMenu = false },
            icon = { Icon(Icons.Filled.QrCode2, null, tint = TsmColors.Ball) },
            title = { Text(s.transferTitle) },
            text = { Text(s.transferHint) },
            confirmButton = {
                TextButton(onClick = {
                    transferMenu = false
                    if (!c.startSending()) Toast.makeText(context, s.transferNoNetwork, Toast.LENGTH_LONG).show()
                }) { Text(s.transferSend) }
            },
            dismissButton = {
                TextButton(onClick = {
                    transferMenu = false
                    scanner.launch(
                        ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE).setPrompt(s.transferScanPrompt)
                            .setBeepEnabled(false).setOrientationLocked(true),
                    )
                }) { Text(s.transferReceive) }
            },
        )
    }
    sendUrl?.let { url -> SendDialog(c, url) }
}

/** Finestra di chi invia: il QR con l'indirizzo. Chiudendola il server si spegne. */
@Composable
private fun SendDialog(c: MatchController, url: String) {
    val s = LocalStrings.current
    val served by c.transferServed.collectAsState()
    DisposableEffect(Unit) { onDispose { c.stopSending() } }
    AlertDialog(
        onDismissRequest = { c.stopSending() },
        title = { Text(s.transferSend) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                QrCode(url, 240.dp)
                Text(if (served > 0) s.transferSent else s.transferShowQr, color = if (served > 0) TsmColors.Ball else TsmColors.TextMain,
                    fontWeight = if (served > 0) FontWeight.Bold else FontWeight.Normal)
            }
        },
        confirmButton = { TextButton(onClick = { c.stopSending() }) { Text(s.transferClose) } },
    )
}

@Composable
private fun PlayerDetail(c: MatchController, played: List<PlayerStats.Played>, matches: List<LiveMatch>, key: String) {
    val s = LocalStrings.current
    val p = remember(played, key) { PlayerStats.players(played).firstOrNull { it.key == key } }
    val rivals = remember(played, key) { PlayerStats.rivals(played, key) }
    val mine = remember(matches, key) { matches.filter { PlayerStats.sideOf(it.record, key) != null } }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(TsmColors.Surface).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EditablePlayerAvatar(c, p?.name ?: key, TsmColors.Ball, 72.dp)
        Spacer(Modifier.width(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(p?.name ?: key, color = TsmColors.TextMain, fontSize = 24.sp, fontWeight = FontWeight.Black)
            if (p != null) {
                val pct = if (p.played > 0) Math.round(p.won * 100.0 / p.played) else 0
                Text(s.historyRecord(p.won, p.lost) + " · $pct%", color = TsmColors.Ball, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
    val lines = remember(played, key) { PlayerStats.lines(played, key) }
    if (lines.size >= 2) TrendCard(lines)
    if (rivals.isNotEmpty()) {
        Text(s.historyHeadToHead, color = TsmColors.TextDim, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        rivals.forEach { r -> PlayerRow(c, r.name, s.historyRecord(r.won, r.lost), chevron = false) {} }
    }
    Text(s.historyMatches, color = TsmColors.TextDim, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    mine.forEach { MatchRow(it) { c.openFromHistory(it) } }
}

/** Una partita: data e formato, i due lati (il vincitore colorato) e il punteggio dal punto di vista di chi ha vinto. */
@Composable
private fun MatchRow(m: LiveMatch, onClick: () -> Unit) {
    val s = LocalStrings.current
    val rec = m.record
    val names = remember(rec.id, s) { Names(rec.setup, s) }
    val w = m.state.winner
    val lang = rec.options.lang
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(RoundedCornerShape(16.dp)).background(TsmColors.Surface)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(5.dp).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(w?.let { TsmColors.player(it) } ?: TsmColors.Outline))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "${Reports.date(rec.startedAt ?: rec.updatedAt, lang)} · ${Reports.formatLabel(rec, s)}",
                color = TsmColors.TextDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            for (side in Side.entries) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        names.short(side), color = TsmColors.player(side), fontSize = 16.sp,
                        fontWeight = if (side == w) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                    )
                    if (side == w) {
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Filled.EmojiEvents, null, tint = TsmColors.Ball, modifier = Modifier.padding(top = 1.dp).width(16.dp))
                    }
                }
            }
            if (w != null) Text(Reports.scoreLine(m.state, w), color = TsmColors.TextMain, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        Icon(Icons.Filled.ChevronRight, null, tint = TsmColors.TextDim)
    }
}

@Composable
private fun PlayerRow(c: MatchController, name: String, record: String, chevron: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(TsmColors.Surface)
            .then(if (chevron) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlayerAvatar(c, name, TsmColors.Ball, 36.dp)
        Spacer(Modifier.width(10.dp))
        Text(name, color = TsmColors.TextMain, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(record, color = TsmColors.TextDim, fontSize = 13.sp)
        if (chevron) Icon(Icons.Filled.ChevronRight, null, tint = TsmColors.TextDim)
    }
}

/**
 * Andamento del giocatore: una colonna per partita (ultime 12) con la percentuale di punti vinti, piena se vinta;
 * la linea è il 50%. Sotto le medie, e quelle delle statistiche dettagliate se qualche partita le ha.
 */
@Composable
private fun TrendCard(lines: List<PlayerStats.MatchLine>) {
    val s = LocalStrings.current
    val last = lines.takeLast(12)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(TsmColors.Surface).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(s.trendTitle, color = TsmColors.TextMain, fontWeight = FontWeight.Bold)
        Box(Modifier.fillMaxWidth().height(90.dp)) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
                last.forEach { l ->
                    Box(
                        Modifier.weight(1f).fillMaxHeight(l.pointsWonPct / 100f).clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(if (l.won) TsmColors.Ball else TsmColors.Outline),
                    )
                }
                repeat(12 - last.size) { Spacer(Modifier.weight(1f)) }
            }
            // Linea del 50%: sopra si sono vinti più punti di quanti se ne sono persi.
            Box(Modifier.fillMaxWidth().height(1.dp).align(Alignment.Center).background(TsmColors.TextDim.copy(alpha = 0.6f)))
        }
        Text(s.trendHint, color = TsmColors.TextDim, fontSize = 12.sp)
        Text(s.trendPointsAvg(Math.round(lines.map { it.pointsWonPct }.average()).toInt()), color = TsmColors.TextMain)
        val det = lines.mapNotNull { it.detail }
        if (det.isNotEmpty()) {
            val n = det.size.toDouble()
            fun avg(f: (com.tennis.scoremanager.data.MatchStats.SideTotals) -> Int) = String.format(java.util.Locale.ROOT, "%.1f", det.sumOf(f) / n)
            val serves = det.sumOf { it.servePoints }
            val firstIn = if (serves > 0) Math.round(det.sumOf { it.firstServesIn } * 100.0 / serves) else 0
            Text(s.trendDetailTitle(det.size), color = TsmColors.TextMain, fontWeight = FontWeight.Bold)
            Text(
                "${s.statAces} ${avg { it.aces }} · ${s.statDoubleFaults} ${avg { it.doubleFaults }} · ${s.statFirstServeIn} $firstIn% · " +
                    "${s.statWinners} ${avg { it.winners }} · ${s.statErrors} ${avg { it.errors }}",
                color = TsmColors.TextDim, fontSize = 13.sp,
            )
        }
    }
}

