// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.SlowMotionVideo
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.tennis.scoremanager.MatchController
import com.tennis.scoremanager.relay.PhoneRelay
import com.tennis.scoremanager.video.ReplayActivity
import java.io.File

/**
 * Moviola sul telefono dell'arbitro: il chip "Moviola" (solo con una telecamera collegata) chiede il punto in corso
 * (dall'ultimo segnato), l'ultimo punto o quello prima; la moviola si guarda in [ReplayActivity].
 */
@Composable
fun ReplayChip(c: MatchController) {
    val s = LocalStrings.current
    val relay by c.relay.ui.collectAsState()
    val replay by c.relay.replay.collectAsState()
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    if (relay.cameras > 0) {
        Pill(s.replayTitle, TsmColors.Orange, TsmColors.OnOrange, Icons.Filled.SlowMotionVideo) { menu = true }
    }
    if (menu) {
        AlertDialog(
            onDismissRequest = { menu = false },
            icon = { Icon(Icons.Filled.SlowMotionVideo, null, tint = TsmColors.Orange) },
            title = { Text(s.replayTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(s.replayHint, fontSize = 13.sp, color = TsmColors.TextDim)
                    // Sul tabellone appena arriva (si toglie e si rimette anche dalla moviola).
                    if (c.canShowReplayOnTv) {
                        var board by remember { mutableStateOf(c.storage.replayOnBoard) }
                        SwitchRow(Icons.Filled.Tv, s.replayAlsoOnBoard, null, board) {
                            board = it
                            c.storage.replayOnBoard = it
                        }
                    }
                    // Di solito il punto contestato non è ancora stato dato: per primo "dall'ultimo punto segnato".
                    for ((label, back) in listOf(s.replaySinceLast to -1, s.replayLastPoint to 0, s.replayPrevPoint to 1)) {
                        GhostButton(label, Icons.Filled.SlowMotionVideo, {
                            menu = false
                            c.requestReplay(back)
                        }, Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = { TextButton(onClick = { menu = false }) { Text(s.back) } },
        )
    }
    when (val r = replay) {
        is PhoneRelay.Replay.Waiting -> AlertDialog(
            onDismissRequest = {},
            title = { Text(s.replayTitle) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = TsmColors.Orange)
                    Text("  " + s.replayWaiting, fontSize = 14.sp)
                }
            },
            confirmButton = { TextButton(onClick = { c.relay.closeReplay() }) { Text(s.back) } },
        )
        is PhoneRelay.Replay.Failed -> AlertDialog(
            onDismissRequest = { c.relay.closeReplay() },
            title = { Text(s.replayTitle) },
            text = { Text(s.replayFailed) },
            confirmButton = { TextButton(onClick = { c.relay.closeReplay() }) { Text(s.ok) } },
        )
        // Pronta: si apre a schermo intero in una pagina sua, che gira anche in orizzontale.
        is PhoneRelay.Replay.Ready -> LaunchedEffect(r) {
            context.startActivity(
                Intent(context, ReplayActivity::class.java).putExtra(ReplayActivity.EXTRA_FILES, r.files.map { it.absolutePath }.toTypedArray())
                    .putExtra(ReplayActivity.EXTRA_BOARD, c.canShowReplayOnTv && c.storage.replayOnBoard),
            )
        }
        null -> Unit
    }
}

@Composable
private fun Btn(icon: ImageVector?, label: String?, on: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(10.dp), color = if (on) TsmColors.Orange else TsmColors.SurfaceHigh) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            icon?.let { Icon(it, null, tint = if (on) TsmColors.OnOrange else TsmColors.TextMain) }
            label?.let { Text(it, color = if (on) TsmColors.OnOrange else TsmColors.TextMain, fontWeight = FontWeight.Bold, fontSize = 15.sp) }
        }
    }
}
