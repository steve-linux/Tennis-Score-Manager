// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.tennis.scoremanager.MatchController
import com.tennis.scoremanager.ui.GhostButton
import com.tennis.scoremanager.ui.LocalStrings
import com.tennis.scoremanager.ui.ScreenScaffold
import com.tennis.scoremanager.ui.SectionCard
import com.tennis.scoremanager.ui.TsmColors

/** Accende la Segreteria da PC: indirizzo e codice da scrivere sul computer, chi è collegato, il consenso. */
@Composable
fun DeskScreen(c: MatchController) {
    val s = LocalStrings.current
    BackHandler { c.back() }
    val ui by c.desk.ui.collectAsState()
    val context = LocalContext.current
    var scanBad by remember { mutableStateOf(false) }
    val scanner = rememberLauncherForActivityResult(ScanContract()) { r -> r.contents?.let { scanBad = !c.desk.scan(it) } }
    ScreenScaffold(
        onHelp = { c.openHelp("7.20") },
        title = s.deskTitle,
        subtitle = s.deskHint,
        icon = Icons.Filled.Computer,
        bottomBar = { GhostButton(s.back, Icons.AutoMirrored.Filled.ArrowBack, { c.back() }, Modifier.weight(1f)) },
    ) {
        if (!ui.running) {
            Text(s.deskIntro, color = TsmColors.TextDim)
            if (ui.failed) Text(s.deskFailed, color = TsmColors.Orange)
            GhostButton(s.deskStart, Icons.Filled.PowerSettingsNew, { c.desk.start() }, Modifier.fillMaxWidth())
            return@ScreenScaffold
        }
        val ip = ui.connectedIp
        if (ip != null) {
            SectionCard(s.deskConnected(ip), Icons.Filled.Link) {
                GhostButton(s.deskDisconnect, Icons.Filled.LinkOff, { c.desk.disconnect() }, Modifier.fillMaxWidth())
            }
        } else {
            SectionCard(s.deskAddress, Icons.Filled.Computer) {
                if (ui.urls.isEmpty()) Text(s.deskNoNetwork, color = TsmColors.Orange)
                ui.urls.forEach { Text(it, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace) }
                ui.urls.firstOrNull()?.let { url ->
                    GhostButton(s.deskShare, Icons.Filled.Share, {
                        // Solo l'indirizzo: per entrare servono comunque il codice e il consenso sul telefono.
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url)
                        context.startActivity(Intent.createChooser(send, s.deskShare))
                    }, Modifier.fillMaxWidth())
                }
                Text(s.deskWarning, color = TsmColors.TextDim, fontSize = 13.sp)
            }
            SectionCard(s.deskScan, Icons.Filled.QrCodeScanner) {
                Text(s.deskScanHint, color = TsmColors.TextDim, fontSize = 13.sp)
                if (scanBad) Text(s.deskScanBad, color = TsmColors.Orange)
                GhostButton(s.deskScan, Icons.Filled.QrCodeScanner, {
                    scanBad = false
                    scanner.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE).setPrompt(s.deskScanHint).setBeepEnabled(false).setOrientationLocked(true))
                }, Modifier.fillMaxWidth())
            }
            SectionCard(s.deskCode, Icons.Filled.VpnKey) {
                Text(ui.code.chunked(4).joinToString(" "), fontSize = 30.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TsmColors.Ball)
                Text(s.deskCodeHint, color = TsmColors.TextDim, fontSize = 13.sp)
            }
        }
        Text(s.deskFingerprint, color = TsmColors.TextDim, fontSize = 12.sp)
        Text(ui.fingerprint, color = TsmColors.TextDim, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        GhostButton(s.deskStop, Icons.Filled.PowerSettingsNew, { c.desk.stop() }, Modifier.fillMaxWidth())
    }
    ui.waitingIp?.let { ip ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text(s.deskAskTitle) },
            text = { Text(s.deskAsk(ip)) },
            confirmButton = { TextButton(onClick = { c.desk.answer(true) }) { Text(s.deskAllow) } },
            dismissButton = { TextButton(onClick = { c.desk.answer(false) }) { Text(s.deskDeny) } },
        )
    }
}
