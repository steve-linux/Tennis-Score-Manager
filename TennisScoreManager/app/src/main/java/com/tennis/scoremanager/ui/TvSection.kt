// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.ui

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Grid4x4
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SportsTennis
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.tennis.scoremanager.MatchController
import com.tennis.scoremanager.model.Side
import com.tennis.scoremanager.tv.TvColors
import com.tennis.scoremanager.tv.TvSnapshots

/** Pagina 2: tabellone su TV (acceso/spento, indirizzo e QR, aspetto). */
@Composable
fun TvSection(c: MatchController) {
    val s = LocalStrings.current
    val tv by c.tv.collectAsState()
    val su by c.setup.collectAsState()
    var lookOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current

    SectionCard(s.tvSection, Icons.Filled.Tv) {
        SwitchRow(Icons.Filled.Tv, s.tvEnable, s.tvEnableHint, tv.enabled) { v -> c.updateTv { it.copy(enabled = v) } }
        if (!tv.enabled) return@SectionCard
        TvStatus(c)
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Filled.Cast, null, tint = TsmColors.TextDim, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(s.tvChromecastHint, color = TsmColors.TextDim, fontSize = 13.sp)
        }
        val port by c.tvServer.port.collectAsState()
        GhostButton(s.tvPreview, Icons.Filled.OpenInBrowser, {
            port?.let { p -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("http://127.0.0.1:$p/"))) } }
        }, Modifier.fillMaxWidth(), enabled = port != null)

        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { lookOpen = !lookOpen }.padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Palette, null, tint = TsmColors.TextDim)
            Spacer(Modifier.width(12.dp))
            Text(s.tvLook, color = TsmColors.TextMain, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Icon(if (lookOpen) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = TsmColors.TextDim)
        }
        if (lookOpen) {
            val names = c.names(su)
            ColorRow(s.tvColorOf(names.short(Side.P1)), tv.color1) { hex -> c.updateTv { it.copy(color1 = hex) } }
            ColorRow(s.tvColorOf(names.short(Side.P2)), tv.color2) { hex -> c.updateTv { it.copy(color2 = hex) } }
            SwitchRow(Icons.Filled.WatchLater, s.tvShowClock, null, tv.showClock) { v -> c.updateTv { it.copy(showClock = v) } }
            SwitchRow(Icons.Filled.Timer, s.tvShowTimers, null, tv.showTimers) { v -> c.updateTv { it.copy(showTimers = v) } }
            SwitchRow(Icons.Filled.FormatListNumbered, s.tvShowSets, null, tv.showSets) { v -> c.updateTv { it.copy(showSets = v) } }
            SwitchRow(Icons.Filled.Message, s.tvShowMessages, null, tv.showMessages) { v -> c.updateTv { it.copy(showMessages = v) } }
            SwitchRow(Icons.Filled.SportsTennis, s.tvShowServe, null, tv.showServe) { v -> c.updateTv { it.copy(showServe = v) } }
            SwitchRow(Icons.Filled.Grid4x4, s.tvGhost, null, tv.ghostSegments) { v -> c.updateTv { it.copy(ghostSegments = v) } }
            OutlinedTextField(
                value = tv.title,
                onValueChange = { v -> c.updateTv { it.copy(title = v.take(60)) } },
                label = { Text(s.tvTitle) },
                leadingIcon = { Icon(Icons.Filled.Title, null) },
                supportingText = { Text(s.tvTitleHint(TvSnapshots.defaultTitle(su, s))) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Indirizzo, QR da inquadrare e tabelloni collegati (anche dalla schermata della partita). */
@Composable
fun TvStatus(c: MatchController) {
    val s = LocalStrings.current
    val addresses by c.tvServer.addresses.collectAsState()
    val port by c.tvServer.port.collectAsState()
    val clients by c.tvServer.clients.collectAsState()
    val url = port?.let { p -> addresses.firstOrNull()?.let { "http://$it:$p/" } }
    if (url == null) {
        Text(s.tvNoNetwork, color = TsmColors.Orange, fontSize = 14.sp)
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            QrCode(url, 120.dp)
            Spacer(Modifier.width(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(s.tvAddress, color = TsmColors.TextDim, fontSize = 12.sp)
                Text(url.removePrefix("http://").removeSuffix("/"), color = TsmColors.Ball, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                // altre reti (es. hotspot e Wi-Fi insieme)
                for (a in addresses.drop(1)) Text("$a:$port", color = TsmColors.TextDim, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                Text(s.tvScreens(clients), color = if (clients > 0) TsmColors.Ok else TsmColors.TextDim, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
    Text(s.tvQrHint, color = TsmColors.TextDim, fontSize = 13.sp)
}

/** Chip "TV" nella schermata della partita: tabelloni collegati; toccandolo si rivede il QR. */
@Composable
fun TvChip(c: MatchController) {
    val s = LocalStrings.current
    val tv by c.tv.collectAsState()
    if (!tv.enabled) return
    val clients by c.tvServer.clients.collectAsState()
    var open by remember { mutableStateOf(false) }
    Pill("TV · $clients", if (clients > 0) TsmColors.Ok else TsmColors.SurfaceHigh, if (clients > 0) Color.White else TsmColors.TextDim, Icons.Filled.Tv) { open = true }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            icon = { Icon(Icons.Filled.Tv, null, tint = TsmColors.Ball) },
            title = { Text(s.tvSection) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { TvStatus(c) } },
            confirmButton = { TextButton(onClick = { open = false }) { Text(s.ok) } },
        )
    }
}

@Composable
private fun ColorRow(label: String, selected: String, onPick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = TsmColors.TextMain, fontSize = 14.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (hex in TvColors.palette) {
                val on = hex.equals(selected, ignoreCase = true)
                Box(
                    Modifier.size(28.dp).clip(CircleShape).background(Color(android.graphics.Color.parseColor(hex)))
                        .border(if (on) 3.dp else 1.dp, if (on) Color.White else TsmColors.Outline, CircleShape)
                        .clickable { onPick(hex) },
                )
            }
        }
    }
}

/** Codice QR (bianco e nero, con margine) generato sul telefono: niente internet. */
@Composable
fun QrCode(text: String, size: Dp) {
    val bitmap = remember(text) {
        val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 2))
        val px = IntArray(m.width * m.height) { i -> if (m[i % m.width, i / m.width]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
        Bitmap.createBitmap(px, m.width, m.height, Bitmap.Config.ARGB_8888).asImageBitmap()
    }
    Image(
        bitmap, null,
        filterQuality = FilterQuality.None,
        modifier = Modifier.size(size).clip(RoundedCornerShape(8.dp)).background(Color.White),
    )
}
