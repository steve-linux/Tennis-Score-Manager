// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhonelinkRing
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.tennis.scoremanager.MatchController
import com.tennis.scoremanager.ble.LinkState
import com.tennis.scoremanager.data.Names
import com.tennis.scoremanager.model.Side
import com.tennis.scoremanager.relay.HotspotPermissions
import com.tennis.scoremanager.relay.JoinState
import com.tennis.scoremanager.relay.RelayRole
import com.tennis.scoremanager.tv.DisplayActivity
import com.tennis.scoremanager.video.CameraActivity

/**
 * Pagina 2, "Secondo telefono": un telefono per fondo campo. Da qui questo telefono diventa il principale (mostra il
 * QR) oppure il satellite (inquadra il QR del principale), che fa da ponte per fasce e braccialetti dei giocatori
 * vicini a lui e può mostrare il tabellone. Vale in tutte le modalità.
 */
@Composable
fun RelaySection(c: MatchController, names: Names) {
    val s = LocalStrings.current
    val relay by c.relay.ui.collectAsState()
    val context = LocalContext.current
    var showQr by remember { mutableStateOf(false) }
    var camRemote by remember { mutableStateOf(false) }
    // Il QR serve sia al satellite sia alla telecamera: [asCamera] dice quale dei due si è scelto.
    var asCamera by remember { mutableStateOf(false) }
    val scanner = rememberLauncherForActivityResult(ScanContract()) { r ->
        r.contents?.let {
            c.joinRelay(it, asCamera)
            if (asCamera && c.relay.ui.value.camera) context.startActivity(Intent(context, CameraActivity::class.java))
        }
    }
    fun scan() = scanner.launch(
        ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE).setPrompt(s.relayScanPrompt)
            .setBeepEnabled(false).setOrientationLocked(true),
    )
    // Il permesso serve al principale per creare la rete e al satellite per entrarci da solo. Se lo si nega, si
    // usa il Wi-Fi in cui i telefoni si trovano già.
    var afterPerms by remember { mutableStateOf<String?>(null) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        when (afterPerms) {
            "main" -> {
                c.startRelayMain(HotspotPermissions.granted(context))
                showQr = true
            }
            "join" -> scan()
        }
        afterPerms = null
    }
    fun withPerms(then: String, run: () -> Unit) {
        if (HotspotPermissions.granted(context)) {
            run()
        } else {
            afterPerms = then
            permLauncher.launch(HotspotPermissions.required())
        }
    }
    SectionCard(s.relayTitle, Icons.Filled.PhonelinkRing) {
    when (relay.role) {
        RelayRole.OFF -> {
            Text(s.relayHint, color = TsmColors.TextDim, fontSize = 13.sp)
            GhostButton(s.relayMain, Icons.Filled.QrCode2, {
                withPerms("main") {
                    c.startRelayMain(useHotspot = true)
                    showQr = true
                }
            }, Modifier.fillMaxWidth())
            GhostButton(s.relayJoin, Icons.Filled.QrCodeScanner, {
                asCamera = false
                withPerms("join") { scan() }
            }, Modifier.fillMaxWidth())
            // Telefono dietro il campo, al centro: registra e trasmette la partita con la grafica del punteggio.
            GhostButton(s.relayJoinCamera, Icons.Filled.Videocam, {
                asCamera = true
                withPerms("join") { scan() }
            }, Modifier.fillMaxWidth())
        }
        RelayRole.MAIN -> {
            Text(
                when {
                    relay.url == null -> s.relayPreparing
                    relay.satellites.isEmpty() -> s.relayNoSatellite
                    else -> s.relaySatellites(relay.satellites.joinToString(", "))
                },
                color = if (relay.satellites.isEmpty()) TsmColors.Orange else TsmColors.Ok, fontSize = 13.sp,
            )
            relay.hotspot?.let { Text(s.relayOwnNetwork(it.ssid), color = TsmColors.TextDim, fontSize = 13.sp) }
            GhostButton(s.relayShowCode, Icons.Filled.QrCode2, { showQr = true }, Modifier.fillMaxWidth())
            // Telefono-telecamera collegato: l'inquadratura e i comandi anche prima della partita, per sistemarlo.
            if (relay.cameras > 0) GhostButton(s.camRemoteOpen, Icons.Filled.Videocam, { camRemote = true }, Modifier.fillMaxWidth())
            GhostButton(s.relayStop, Icons.Filled.Stop, { c.stopRelay() }, Modifier.fillMaxWidth())
        }
        RelayRole.SATELLITE -> {
            if (!relay.camera) Text(s.relaySatelliteOn, color = TsmColors.TextMain, fontSize = 13.sp)
            val net = relay.hotspot
            if (relay.wifiOff && !relay.connected) {
                Text(s.relayWifiOff, color = TsmColors.Orange, fontSize = 13.sp)
                GhostButton(s.relayWifiOn, Icons.Filled.Wifi, { openWifiPanel(context) }, Modifier.fillMaxWidth())
            }
            val joinText = when {
                relay.wifiOff -> null
                net == null || relay.connected -> null
                relay.joinByHand -> s.relayJoinByHand(net.ssid, net.pass.orEmpty())
                relay.join == JoinState.WAITING -> s.relayJoinWaiting
                relay.join == JoinState.REFUSED -> s.relayJoinRefused
                relay.join == JoinState.LOST -> s.relayJoinLost
                else -> null
            }
            joinText?.let { Text(it, color = TsmColors.Orange, fontSize = 13.sp) }
            Text(
                (if (relay.connected) s.relayConnected else s.relayDisconnected) +
                    (if (relay.pending > 0) " · ${s.relayPending(relay.pending)}" else ""),
                color = if (relay.connected) TsmColors.Ok else TsmColors.Orange, fontSize = 13.sp,
            )
            if (relay.camera) {
                Text(s.cameraRoleOn, color = TsmColors.TextMain, fontSize = 13.sp)
                GhostButton(s.cameraOpen, Icons.Filled.Videocam, {
                    context.startActivity(Intent(context, CameraActivity::class.java))
                }, Modifier.fillMaxWidth())
            } else {
                SatelliteDevices(c, names)
            }
            relay.tv?.let { tv ->
                GhostButton(s.relayShowBoard, Icons.Filled.Tv, {
                    context.startActivity(Intent(context, DisplayActivity::class.java).putExtra(DisplayActivity.EXTRA_ADDRESS, tv))
                }, Modifier.fillMaxWidth())
            }
            GhostButton(s.relayStop, Icons.Filled.Stop, { c.stopRelay() }, Modifier.fillMaxWidth())
        }
    }
    }
    if (camRemote) CameraRemoteDialog(c) { camRemote = false }
    val url = relay.url
    if (showQr && relay.role == RelayRole.MAIN) {
        AlertDialog(
            onDismissRequest = { showQr = false },
            title = { Text(s.relayTitle) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (url == null) {
                        CircularProgressIndicator(Modifier.size(48.dp), color = TsmColors.Ball)
                        Text(s.relayPreparing, color = TsmColors.TextMain)
                        return@Column
                    }
                    QrCode(url, 240.dp)
                    relay.hotspot?.let { Text(s.relayOwnNetwork(it.ssid), color = TsmColors.TextDim, fontSize = 13.sp) }
                    Text(
                        if (relay.satellites.isEmpty()) s.relayShowQr else s.relaySatellites(relay.satellites.joinToString(", ")),
                        color = if (relay.satellites.isEmpty()) TsmColors.TextMain else TsmColors.Ok,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showQr = false }) { Text(s.ok) } },
        )
    }
}

/** Satellite: braccialetti e fasce che segue per il principale, e come li sente. */
@Composable
private fun SatelliteDevices(c: MatchController, names: Names) {
    val s = LocalStrings.current
    val bands by c.ble.bands.collectAsState()
    val straps by c.straps.collectAsState()
    for (side in Side.entries) {
        val b = bands[side]
        val st = straps[side]
        if (b == null && st == null) continue
        Column {
            Text(names.short(side), color = TsmColors.player(side), fontWeight = FontWeight.Bold)
            b?.let {
                val state = when (it.state) {
                    LinkState.READY -> s.bandConnected
                    LinkState.CONNECTING -> s.bandConnecting
                    LinkState.POWERED_OFF -> s.bandOff
                    LinkState.IDLE -> s.bandIdle
                }
                Text("${it.name} · $state" + (it.battery?.let { p -> " · ${s.battery} $p%" } ?: ""), color = TsmColors.TextDim, fontSize = 12.sp)
            }
            st?.let { Text("${it.name} · ${strapStatus(it, s)}", color = TsmColors.TextDim, fontSize = 12.sp) }
        }
    }
}

/** Pannello del Wi-Fi (da Android 10 l'app non può accenderlo da sola); prima, le impostazioni del Wi-Fi. */
fun openWifiPanel(context: android.content.Context) {
    val action = if (android.os.Build.VERSION.SDK_INT >= 29) android.provider.Settings.Panel.ACTION_WIFI else android.provider.Settings.ACTION_WIFI_SETTINGS
    runCatching { context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
