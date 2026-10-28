// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.ui

import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tennis.scoremanager.MatchController
import com.tennis.scoremanager.data.Reports
import com.tennis.scoremanager.relay.CamCmd
import kotlinx.coroutines.delay

/** Chip "Telecamera" in partita, accanto a "Moviola": c'è solo con un telefono-telecamera collegato. */
@Composable
fun CameraChip(c: MatchController) {
    val s = LocalStrings.current
    val relay by c.relay.ui.collectAsState()
    val st by c.relay.camState.collectAsState()
    var open by remember { mutableStateOf(false) }
    if (relay.cameras > 0) {
        val rec = st?.recording == true
        Pill(if (rec) "● " + s.camRemoteTitle else s.camRemoteTitle, if (rec) TsmColors.Danger else TsmColors.SurfaceHigh, TsmColors.TextMain, Icons.Filled.Videocam) { open = true }
    }
    if (open) CameraRemoteDialog(c) { open = false }
}

/**
 * Telefono dell'arbitro: l'inquadratura del telefono-telecamera (spesso in alto, irraggiungibile) e i suoi comandi.
 * Finché è aperta chiede i fotogrammi ogni 3 s; chiusa, la telecamera smette di mandarli.
 */
@Composable
fun CameraRemoteDialog(c: MatchController, onClose: () -> Unit) {
    val s = LocalStrings.current
    val relay by c.relay.ui.collectAsState()
    val st by c.relay.camState.collectAsState()
    val frame by c.relay.camFrame.collectAsState()
    LaunchedEffect(Unit) {
        while (true) {
            c.relay.camCommand(CamCmd.PREVIEW)
            delay(3_000)
        }
    }
    DisposableEffect(Unit) { onDispose { c.relay.stopCamPreview() } }
    // La durata della registrazione arriva ogni 3 s: in mezzo la si conta qui.
    var stateAt by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(st) { stateAt = SystemClock.elapsedRealtime() }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(st?.recording) {
        while (st?.recording == true) {
            now = SystemClock.elapsedRealtime()
            delay(500)
        }
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().padding(10.dp), shape = RoundedCornerShape(18.dp), color = TsmColors.Surface) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Videocam, null, tint = TsmColors.Ball)
                    Text("  " + s.camRemoteTitle, color = TsmColors.TextMain, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = onClose) { Icon(Icons.Filled.Close, s.back, tint = TsmColors.TextMain) }
                }
                Box(
                    Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).background(Color.Black),
                    contentAlignment = Alignment.Center,
                ) {
                    val f = frame
                    when {
                        relay.cameras == 0 -> Text(s.camRemoteGone, color = TsmColors.Orange)
                        f != null -> Image(f.asImageBitmap(), null, Modifier.fillMaxWidth(), contentScale = ContentScale.Fit)
                        else -> Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(color = TsmColors.Ball)
                            Text("  " + s.camRemoteWaiting, color = TsmColors.TextDim, fontSize = 13.sp)
                        }
                    }
                    // Sopra l'immagine, come sul telefono-telecamera.
                    val cam = st
                    Row(Modifier.align(Alignment.TopStart).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (cam?.recording == true) Tag("● REC ${Reports.duration(cam.recMs + (now - stateAt).coerceAtLeast(0))}", TsmColors.Danger)
                        when (cam?.live) {
                            2 -> Tag("LIVE", TsmColors.Danger)
                            1 -> Tag(s.cameraConnecting, TsmColors.Orange)
                        }
                    }
                }
                st?.let { cam ->
                    val info = listOfNotNull(
                        cam.battery?.let { s.camRemoteBattery(it) },
                        cam.freeMb?.let { s.camRemoteFree(if (it >= 1024) "%.1f GB".format(it / 1024f) else "$it MB") },
                    ).joinToString(" · ")
                    if (info.isNotEmpty()) Text(info, color = if ((cam.battery ?: 100) < 20 || (cam.freeMb ?: Long.MAX_VALUE) < 2048) TsmColors.Orange else TsmColors.TextDim, fontSize = 13.sp)
                    cam.message?.let { Text(it, color = TsmColors.TextMain, fontSize = 13.sp) }
                }
                val cam = st
                val enabled = relay.cameras > 0 && cam != null
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val rec = cam?.recording == true
                    Cmd(if (rec) Icons.Filled.Stop else Icons.Filled.FiberManualRecord, if (rec) s.cameraStopRec else s.cameraRec, TsmColors.Danger, enabled, Modifier.weight(1f)) {
                        c.relay.camCommand(if (rec) CamCmd.REC_STOP else CamCmd.REC_START)
                    }
                    val live = (cam?.live ?: 0) != 0
                    Cmd(Icons.Filled.LiveTv, if (live) s.cameraStopLive else s.cameraGoLive, if (live) TsmColors.Danger else TsmColors.SurfaceHigh, enabled, Modifier.weight(1f)) {
                        c.relay.camCommand(if (live) CamCmd.LIVE_STOP else CamCmd.LIVE_START)
                    }
                    val ov = cam?.overlay == true
                    Cmd(Icons.Filled.Layers, s.cameraOverlay, if (ov) TsmColors.Ok else TsmColors.SurfaceHigh, enabled, Modifier.weight(1f)) {
                        c.relay.camCommand(if (ov) CamCmd.OVERLAY_OFF else CamCmd.OVERLAY_ON)
                    }
                }
                // Assistente inquadratura: la livella del telefono-telecamera, da guardare mentre lo si sistema in alto.
                if (cam?.tilt != null && cam.roll != null) LevelReadout(cam.tilt, cam.roll, cam.tiltTarget, Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val level = cam?.tilt != null
                    Cmd(Icons.Filled.Straighten, if (level) s.assistHideLevel else s.assistShowLevel, if (level) TsmColors.Ok else TsmColors.SurfaceHigh, enabled, Modifier.weight(1f)) {
                        c.relay.camCommand(if (level) CamCmd.ASSIST_OFF else CamCmd.ASSIST_ON)
                    }
                    if ((cam?.lenses ?: 1) > 1) {
                        Cmd(Icons.Filled.Cameraswitch, "${s.cameraLens}: ${lensName(s, cam!!.lens, cam.lenses)}", TsmColors.SurfaceHigh, enabled && !cam.recording && cam.live == 0, Modifier.weight(1f)) {
                            c.relay.camCommand(CamCmd.LENS_NEXT)
                        }
                    }
                }
                if (cam != null && cam.zoomMax > cam.zoomMin) ZoomRow(c, cam.zoom, cam.zoomMin, cam.zoomMax)
                Text(s.camRemoteHint, color = TsmColors.TextDim, fontSize = 12.sp)
            }
        }
    }
}

/** Zoom: cursore (il comando parte quando lo si lascia, per non intasare il Wi-Fi) e tasti − / +. */
@Composable
private fun ZoomRow(c: MatchController, zoom: Float, min: Float, max: Float) {
    val s = LocalStrings.current
    var drag by remember { mutableStateOf<Float?>(null) }
    var local by remember { mutableFloatStateOf(zoom) }
    LaunchedEffect(zoom) { if (drag == null) local = zoom }
    val step = (max - min) / 10f
    fun send(v: Float) {
        local = v.coerceIn(min, max)
        c.relay.camCommand(CamCmd.ZOOM, local)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { send(local - step) }) { Icon(Icons.Filled.ZoomOut, s.camRemoteZoom, tint = TsmColors.TextMain) }
        Slider(
            value = drag ?: local, onValueChange = { drag = it }, valueRange = min..max,
            onValueChangeFinished = { drag?.let { send(it) }; drag = null },
            colors = SliderDefaults.colors(thumbColor = TsmColors.Ball, activeTrackColor = TsmColors.Ball),
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { send(local + step) }) { Icon(Icons.Filled.ZoomIn, s.camRemoteZoom, tint = TsmColors.TextMain) }
        Text("%.1f×".format(drag ?: local), color = TsmColors.TextDim, fontSize = 13.sp)
    }
}

@Composable
private fun Tag(text: String, color: Color) {
    Text(
        text, color = TsmColors.TextMain, fontWeight = FontWeight.Bold, fontSize = 12.sp,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(color).padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun Cmd(icon: ImageVector, label: String, color: Color, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(14.dp), color = if (enabled) color else TsmColors.SurfaceHigh, modifier = modifier) {
        Column(Modifier.padding(vertical = 10.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = if (enabled) TsmColors.TextMain else TsmColors.TextDim)
            Text(label, color = if (enabled) TsmColors.TextMain else TsmColors.TextDim, fontSize = 12.sp, maxLines = 1)
        }
    }
}
