package com.tennis.scoremanager.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SportsTennis
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tennis.scoremanager.MatchController
import com.tennis.scoremanager.ble.LinkState
import com.tennis.scoremanager.data.MatchRecord
import com.tennis.scoremanager.data.Names
import com.tennis.scoremanager.data.PlayMode
import com.tennis.scoremanager.data.Reports
import com.tennis.scoremanager.model.Lang
import com.tennis.scoremanager.model.MatchFormat
import com.tennis.scoremanager.model.ScoreEngine
import com.tennis.scoremanager.model.Side
import com.tennis.scoremanager.ui.BigButton
import com.tennis.scoremanager.ui.ConfirmDialog
import com.tennis.scoremanager.ui.GhostButton
import com.tennis.scoremanager.ui.LocalStrings
import com.tennis.scoremanager.ui.Pill
import com.tennis.scoremanager.ui.TsmColors

/** Pagina 3: INIZIO PARTITA lampeggia in attesa del pulsante o di KEY1 su un braccialetto. */
@Composable
fun StartScreen(c: MatchController) {
    val s = LocalStrings.current
    val o by c.options.collectAsState()
    val su by c.setup.collectAsState()
    val saved by c.saved.collectAsState()
    val bands by c.ble.bands.collectAsState()
    val names = remember(su, s) { Names(su, s) }
    var showSaved by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<MatchRecord?>(null) }
    BackHandler { c.back() }

    val pulse = rememberInfiniteTransition(label = "pulse")
    val alpha by pulse.animateFloat(0.35f, 1f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "alpha")
    val scale by pulse.animateFloat(0.94f, 1.04f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "scale")
    val spin by pulse.animateFloat(0f, 360f, infiniteRepeatable(tween(6000)), label = "spin")

    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { c.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, s.back, tint = TsmColors.TextMain) }
            Spacer(Modifier.weight(1f))
            if (o.mode == PlayMode.BANDS) {
                for (side in Side.entries) {
                    val ready = bands[side]?.state == LinkState.READY
                    Pill(
                        s.playerTag(side.ordinal + 1),
                        if (ready) TsmColors.player(side) else TsmColors.SurfaceHigh,
                        if (ready) TsmColors.onPlayer(side) else TsmColors.TextDim,
                        Icons.Filled.Watch,
                    )
                    Spacer(Modifier.width(6.dp))
                }
            }
        }
        Spacer(Modifier.weight(0.6f))

        // Pallina da tennis che pulsa e ruota
        Box(
            Modifier.size(150.dp).scale(scale).graphicsLayer { rotationZ = spin }
                .clip(RoundedCornerShape(50))
                .background(Brush.radialGradient(listOf(Color(0xFFEFFF8A), TsmColors.Ball, Color(0xFF8DB31C))))
                .clickable { c.startMatch() },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val st = Stroke(width = size.minDimension * 0.05f)
                drawArc(Color.White, -60f, 120f, false, Offset(-size.width * 0.55f, 0f), Size(size.width, size.height), style = st)
                drawArc(Color.White, 120f, 120f, false, Offset(size.width * 0.55f, 0f), Size(size.width, size.height), style = st)
            }
        }
        Spacer(Modifier.height(28.dp))
        Text(
            s.startMatch,
            style = TextStyle(
                brush = Brush.horizontalGradient(listOf(TsmColors.Ball, TsmColors.Player1, TsmColors.Orange)),
                fontSize = 44.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center,
            ),
            modifier = Modifier.graphicsLayer { this.alpha = alpha },
        )
        Spacer(Modifier.height(8.dp))
        Text(if (o.mode == PlayMode.BANDS) s.startHintBands else s.startHint, color = TsmColors.TextDim, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Text(names.short(Side.P1), color = TsmColors.Player1, fontWeight = FontWeight.Bold, fontSize = 18.sp, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
            Text("  ${s.vs}  ", color = TsmColors.TextDim)
            Text(names.short(Side.P2), color = TsmColors.Player2, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(if (o.format == MatchFormat.BEST_OF_THREE) s.formatBestOfThree else s.formatMatchTiebreak, TsmColors.SurfaceHigh, TsmColors.TextMain)
            Pill("${s.serving}: ${names.short(o.firstServer)}", TsmColors.player(o.firstServer), TsmColors.onPlayer(o.firstServer), Icons.Filled.SportsTennis)
        }
        Spacer(Modifier.weight(1f))
        BigButton(s.startButton, Icons.Filled.PlayArrow, { c.startMatch() }, Modifier.fillMaxWidth().height(64.dp))
        Spacer(Modifier.height(10.dp))
        GhostButton(s.resumeSaved, Icons.Filled.History, {
            c.refreshSaved()
            showSaved = true
        }, Modifier.fillMaxWidth())
    }

    if (showSaved) {
        AlertDialog(
            onDismissRequest = { showSaved = false },
            icon = { Icon(Icons.Filled.History, null, tint = TsmColors.Ball) },
            title = { Text(s.savedMatchesTitle) },
            text = {
                if (saved.isEmpty()) {
                    Text(s.noSavedMatches)
                } else {
                    LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(saved, key = { it.id }) { rec ->
                            SavedRow(rec, o.lang, onOpen = {
                                showSaved = false
                                c.resumeSaved(rec)
                            }, onDelete = { toDelete = rec })
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSaved = false }) { Text(s.cancel) } },
        )
    }

    // Il cestino sta nella stessa riga che riprende la partita: un tocco storto non deve cancellarla.
    toDelete?.let { rec ->
        val n = Names(rec.setup, s)
        ConfirmDialog(
            icon = Icons.Filled.Delete,
            title = s.deleteSavedTitle,
            text = s.deleteSavedText("${n.short(Side.P1)} ${s.vs} ${n.short(Side.P2)}"),
            confirm = s.delete,
            danger = true,
            onConfirm = {
                toDelete = null
                c.deleteSaved(rec)
            },
            onDismiss = { toDelete = null },
        )
    }
}

@Composable
private fun SavedRow(rec: MatchRecord, lang: Lang, onOpen: () -> Unit, onDelete: () -> Unit) {
    val s = LocalStrings.current
    val names = remember(rec.id) { Names(rec.setup, s) }
    val state = remember(rec.id, rec.events.size) { ScoreEngine.replay(rec.rules, rec.events) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TsmColors.SurfaceHigh).clickable(onClick = onOpen).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("${names.short(Side.P1)} ${s.vs} ${names.short(Side.P2)}", fontWeight = FontWeight.Bold, color = TsmColors.TextMain)
            val score = (Reports.scoreLine(state, Side.P1) + "  " + "${state.g1}-${state.g2}").trim()
            Text(score, color = TsmColors.Ball)
            Text(
                "${Reports.date(rec.startedAt ?: rec.updatedAt, lang)} · ${Reports.time(rec.updatedAt, lang)}",
                color = TsmColors.TextDim, fontSize = 12.sp,
            )
        }
        IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, s.delete, tint = TsmColors.Danger) }
    }
}
