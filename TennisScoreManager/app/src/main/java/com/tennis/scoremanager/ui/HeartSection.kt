// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.PhonelinkRing
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.tennis.scoremanager.MatchController
import com.tennis.scoremanager.ble.FoundStrap
import com.tennis.scoremanager.ble.StrapInfo
import com.tennis.scoremanager.ble.StrapState
import com.tennis.scoremanager.data.HeartProfile
import com.tennis.scoremanager.data.HeartStats
import com.tennis.scoremanager.data.Names
import com.tennis.scoremanager.model.Side
import com.tennis.scoremanager.relay.HotspotPermissions
import com.tennis.scoremanager.relay.JoinState
import com.tennis.scoremanager.relay.RelayRole
import com.tennis.scoremanager.relay.RelayUi
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

/**
 * Pagina 2, "Battito cardiaco": interruttore, fasce di ciascun lato (ricerca automatica mentre la sezione è aperta)
 * ed età, peso e sesso di chi le indossa per le calorie. Vale in tutte le modalità, anche arbitro e telecomandi.
 */
@Composable
fun HeartSection(
    c: MatchController,
    names: Names,
    btOn: Boolean,
    permsOk: Boolean,
    onEnableBluetooth: () -> Unit,
    onRequestPermissions: () -> Unit,
) {
    val s = LocalStrings.current
    val o by c.options.collectAsState()
    SectionCard(s.heartSection, Icons.Filled.MonitorHeart, accent = TsmColors.Danger) {
        SwitchRow(Icons.Filled.Favorite, s.heartRate, s.heartRateHint, o.heartRate) { v -> c.updateOptions { it.copy(heartRate = v) } }
        if (!o.heartRate) return@SectionCard
        if (!btOn) RequirementRow(Icons.Filled.Bluetooth, s.bluetooth, false, s.enable) { onEnableBluetooth() }
        if (!permsOk) RequirementRow(Icons.Filled.LocationOn, s.location, false, s.allow) { onRequestPermissions() }
        val ready = btOn && permsOk
        val found by c.heart.found.collectAsState()
        val scanning by c.heart.scanning.collectAsState()
        val straps by c.straps.collectAsState()
        val profiles by c.heartProfiles.collectAsState()
        LifecycleResumeEffect(ready) {
            c.setStrapScan(ready)
            onPauseOrDispose { c.setStrapScan(false) }
        }
        val relay by c.relay.ui.collectAsState()
        if (relay.role == RelayRole.SATELLITE) {
            // Il satellite segue le fasce scelte sul principale: qui solo come le sente.
            for (side in Side.entries) {
                val st = straps[side] ?: continue
                Column {
                    Text(st.name, color = TsmColors.player(side), fontWeight = FontWeight.Bold)
                    Text(strapStatus(st, s), color = TsmColors.TextDim, fontSize = 12.sp)
                }
            }
            RelayPanel(c, relay)
            return@SectionCard
        }
        Text(s.heartHowTo, color = TsmColors.TextMain, fontSize = 14.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (scanning) {
                CircularProgressIndicator(Modifier.size(22.dp), color = TsmColors.Ball, strokeWidth = 3.dp)
            } else {
                Icon(Icons.AutoMirrored.Filled.BluetoothSearching, null, tint = TsmColors.TextDim)
            }
            Spacer(Modifier.width(10.dp))
            Text(if (scanning) s.strapSearching else s.autoSearchOff, color = if (scanning) TsmColors.TextMain else TsmColors.Orange, fontSize = 13.sp)
        }
        for (side in Side.entries) {
            StrapPicker(c, side, names.short(side), straps[side], found, straps)
            // Scambiando le fasce i campi ripartono dal profilo che segue la fascia.
            straps[side]?.let { key(it.address) { ProfileRow(profiles[side]) { p -> c.setHeartProfile(side, p) } } }
        }
        if (straps.size == 2) GhostButton(s.swapBands, Icons.Filled.SwapHoriz, { c.swapStraps() }, Modifier.fillMaxWidth())
        Text(s.heartProfileHint, color = TsmColors.TextDim, fontSize = 13.sp)
        RelayPanel(c, relay)
    }
}

/**
 * Secondo telefono a fondo campo: da qui questo telefono diventa il principale (mostra il QR) oppure il satellite
 * (inquadra il QR del principale).
 */
@Composable
private fun RelayPanel(c: MatchController, relay: RelayUi) {
    val s = LocalStrings.current
    val context = LocalContext.current
    var showQr by remember { mutableStateOf(false) }
    val scanner = rememberLauncherForActivityResult(ScanContract()) { r -> r.contents?.let { c.joinRelay(it) } }
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
    Spacer(Modifier.height(4.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.PhonelinkRing, null, tint = TsmColors.Ball)
        Spacer(Modifier.width(10.dp))
        Text(s.relayTitle, color = TsmColors.TextMain, fontWeight = FontWeight.Bold)
    }
    when (relay.role) {
        RelayRole.OFF -> {
            Text(s.relayHint, color = TsmColors.TextDim, fontSize = 13.sp)
            GhostButton(s.relayMain, Icons.Filled.QrCode2, {
                withPerms("main") {
                    c.startRelayMain(useHotspot = true)
                    showQr = true
                }
            }, Modifier.fillMaxWidth())
            GhostButton(s.relayJoin, Icons.Filled.QrCodeScanner, { withPerms("join") { scan() } }, Modifier.fillMaxWidth())
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
            GhostButton(s.relayStop, Icons.Filled.Stop, { c.stopRelay() }, Modifier.fillMaxWidth())
        }
        RelayRole.SATELLITE -> {
            Text(s.relaySatelliteOn, color = TsmColors.TextMain, fontSize = 13.sp)
            val net = relay.hotspot
            val joinText = when {
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
            GhostButton(s.relayStop, Icons.Filled.Stop, { c.stopRelay() }, Modifier.fillMaxWidth())
        }
    }
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

@Composable
private fun StrapPicker(c: MatchController, side: Side, playerName: String, current: StrapInfo?, found: List<FoundStrap>, all: Map<Side, StrapInfo>) {
    val s = LocalStrings.current
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Text(s.strapFor(playerName), color = TsmColors.player(side), fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Box {
            OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                val (icon, tint) = when {
                    current == null -> Icons.Filled.BluetoothDisabled to TsmColors.TextDim
                    current.state == StrapState.READY -> Icons.Filled.BluetoothConnected to TsmColors.Ok
                    else -> Icons.Filled.Bluetooth to TsmColors.Orange
                }
                Icon(icon, null, tint = tint)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(current?.name ?: s.noStrap, color = TsmColors.TextMain, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (current != null) Text(strapStatus(current, s), color = TsmColors.TextDim, fontSize = 12.sp)
                }
                Icon(Icons.Filled.ArrowDropDown, null, tint = TsmColors.TextDim)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                DropdownMenuItem(text = { Text(s.noStrap) }, onClick = {
                    open = false
                    c.assignStrap(side, null, null)
                })
                val options = (found.map { it.address to "${it.name}  (${it.rssi} dBm)" } + all.values.map { it.address to it.name })
                    .distinctBy { it.first }
                for ((address, label) in options) {
                    val usedBy = all.entries.firstOrNull { it.value.address == address && it.key != side }?.key
                    DropdownMenuItem(
                        text = { Text(label + (usedBy?.let { " → ${s.playerTag(it.ordinal + 1)}" } ?: "")) },
                        leadingIcon = { Icon(Icons.Filled.Favorite, null) },
                        onClick = {
                            open = false
                            c.assignStrap(side, address, label.substringBefore("  ("))
                        },
                    )
                }
            }
        }
    }
}

/**
 * "Collegata · 72 bpm · −68 dBm · 100% ricevute · Batteria 90%", oppure perché il battito non arriva. Segnale e
 * misure ricevute nell'ultimo minuto servono a capire fin dove arriva la fascia.
 */
private fun strapStatus(st: StrapInfo, s: Strings): String {
    val state = when {
        st.state != StrapState.READY -> if (st.state == StrapState.CONNECTING) s.strapConnecting else s.strapWaiting
        st.contact == false -> s.strapNoContact
        st.bpm != null -> "${if (st.relayed) s.strapRelayed else s.strapConnected} · ${st.bpm} bpm"
        else -> s.strapWaiting
    }
    return state +
        (st.rssi?.let { " · $it dBm" } ?: "") +
        (st.received?.let { " · ${s.strapReceived(it)}" } ?: "") +
        (st.battery?.let { " · ${s.battery} $it%" } ?: "")
}

/** Età, peso e sesso: si salvano appena età e peso sono plausibili; svuotarli toglie il profilo. */
@Composable
private fun ProfileRow(profile: HeartProfile?, onChange: (HeartProfile?) -> Unit) {
    val s = LocalStrings.current
    var age by remember { mutableStateOf(profile?.age?.toString() ?: "") }
    var weight by remember { mutableStateOf(profile?.weightKg?.toString() ?: "") }
    var female by remember { mutableStateOf(profile?.female ?: false) }
    fun save() {
        val p = HeartStats.profile(age.toIntOrNull(), weight.toIntOrNull(), female)
        if (p != null || age.isBlank() || weight.isBlank()) onChange(p)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        NumberField(age, s.heartAge, Modifier.weight(1f)) {
            age = it
            save()
        }
        NumberField(weight, s.heartWeight, Modifier.weight(1.4f)) {
            weight = it
            save()
        }
    }
    Segmented(
        listOf(SegOption(s.heartMale), SegOption(s.heartFemale)),
        selected = if (female) 1 else 0,
        onSelect = {
            female = it == 1
            save()
        },
    )
}

@Composable
private fun NumberField(value: String, label: String, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() }.take(3)) },
        label = { Text(label, maxLines = 1) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = TsmColors.Ball,
            focusedLabelColor = TsmColors.Ball,
            cursorColor = TsmColors.Ball,
        ),
        modifier = modifier,
    )
}
