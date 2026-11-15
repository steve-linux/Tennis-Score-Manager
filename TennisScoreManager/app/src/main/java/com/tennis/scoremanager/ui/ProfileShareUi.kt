// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SportsTennis
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.tennis.scoremanager.MatchController
import com.tennis.scoremanager.Role
import com.tennis.scoremanager.data.PlayerProfile
import com.tennis.scoremanager.data.ProfileCard
import com.tennis.scoremanager.data.ProfileCards
import com.tennis.scoremanager.data.ProfileField
import com.tennis.scoremanager.data.Reports

fun profileFieldName(s: Strings, f: ProfileField): String = when (f) {
    ProfileField.PHOTO -> s.pfPhoto
    ProfileField.CONTACTS -> s.pfContacts
    ProfileField.BIRTH -> s.pfBirth
    ProfileField.PLAY -> s.pfPlay
    ProfileField.BODY -> s.pfBody
    ProfileField.GUARDIAN -> s.pfGuardian
    ProfileField.FITP -> s.pfFitp
    ProfileField.MEDICAL -> s.pfMedical
}

private fun profileFieldIcon(f: ProfileField) = when (f) {
    ProfileField.PHOTO -> Icons.Filled.PhotoCamera
    ProfileField.CONTACTS -> Icons.Filled.Phone
    ProfileField.BIRTH -> Icons.Filled.Cake
    ProfileField.PLAY -> Icons.Filled.SportsTennis
    ProfileField.BODY -> Icons.Filled.Height
    ProfileField.GUARDIAN -> Icons.Filled.FamilyRestroom
    ProfileField.FITP -> Icons.Filled.Badge
    ProfileField.MEDICAL -> Icons.Filled.LocalHospital
}

/**
 * Passa il profilo: chi manda sceglie i dati (il certificato medico solo a mano), poi il file (con la foto, da
 * WhatsApp, email…) o il QR da far leggere all'altro telefono (senza foto).
 */
@Composable
fun SendProfileDialog(c: MatchController, p: PlayerProfile, onDone: () -> Unit) {
    val s = LocalStrings.current
    val context = LocalContext.current
    val hasPhoto = remember(p.name) { c.photoBytes(p.name) != null }
    val available = remember(p) { ProfileCards.available(p, hasPhoto) }
    var fields by remember(p) { mutableStateOf(ProfileCards.defaults(p, hasPhoto)) }
    var qr by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDone,
        icon = { Icon(Icons.Filled.SwapHoriz, null, tint = TsmColors.Ball) },
        title = { Text(s.pfSend) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(p.name, color = TsmColors.TextMain, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(s.pfSendHint, color = TsmColors.TextDim, fontSize = 13.sp)
                Text(s.pfAlways, color = TsmColors.TextDim, fontSize = 13.sp)
                if (available.isEmpty()) Text(s.pfNothing, color = TsmColors.TextDim, fontSize = 13.sp)
                for (f in available) SwitchRow(profileFieldIcon(f), profileFieldName(s, f), if (f == ProfileField.MEDICAL) s.pfMedicalHint else null, f in fields) { on ->
                    fields = if (on) fields + f else fields - f
                }
                BigButton(s.pfAsFile, Icons.Filled.Share, {
                    c.profileToSend(p, fields)?.let { (file, text) -> context.startActivity(shareFile(context, text, file)) }
                    onDone()
                }, Modifier.fillMaxWidth())
                Text(s.pfAsFileHint, color = TsmColors.TextDim, fontSize = 12.sp)
                GhostButton(s.pfAsQr, Icons.Filled.QrCode, { qr = c.profileQr(p, fields) }, Modifier.fillMaxWidth())
                Text(s.pfAsQrHint, color = TsmColors.TextDim, fontSize = 12.sp)
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text(s.ok) } },
    )
    qr?.let { text ->
        AlertDialog(
            onDismissRequest = { qr = null },
            title = { Text(s.pfQrTitle(p.name)) },
            text = { Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) { QrCode(text, 280.dp) } },
            confirmButton = { TextButton(onClick = { qr = null }) { Text(s.ok) } },
        )
    }
}

/** Ricevi un profilo: legge il QR mostrato dall'altro telefono (il file invece si apre con Condividi › TSM). */
@Composable
fun ReceiveProfileButton(c: MatchController, modifier: Modifier = Modifier.fillMaxWidth()) {
    val s = LocalStrings.current
    val scanner = rememberLauncherForActivityResult(ScanContract()) { r -> r.contents?.let { c.receiveProfileQr(it) } }
    GhostButton(s.pfReceive, Icons.Filled.QrCodeScanner, {
        scanner.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE).setPrompt(s.pfScanPrompt).setBeepEnabled(false).setOrientationLocked(true))
    }, modifier)
}

/**
 * Un profilo arrivato: chi è, cosa porta, e se c'è già (si completa senza cancellare niente) o è nuovo. L'allenatore
 * lo mette tra i suoi allievi, la segreteria può iscriverlo come socio.
 */
@Composable
fun IncomingProfileDialog(c: MatchController, card: ProfileCard) {
    val s = LocalStrings.current
    val lang = c.options.collectAsState().value.lang
    val role by c.activeRole.collectAsState()
    val players by c.players.collectAsState()
    val sp = card.profile
    val match = remember(card, players) { ProfileCards.find(players, sp) }
    var intoExisting by remember { mutableStateOf(true) }
    var asStudent by remember { mutableStateOf(role == Role.COACH) }
    var asMember by remember { mutableStateOf(role == Role.CLUB) }
    val photo = remember(card.photo) {
        card.photo?.let { runCatching { java.util.Base64.getDecoder().decode(it) }.getOrNull() }?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }?.asImageBitmap()
    }
    val close = { c.incomingProfile.value = null }
    AlertDialog(
        onDismissRequest = close,
        icon = { Icon(Icons.Filled.SwapHoriz, null, tint = TsmColors.Ball) },
        title = { Text(s.pfIncomingTitle) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    photo?.let { Image(it, null, Modifier.size(64.dp).clip(CircleShape)) }
                    Column {
                        Text(sp.name, color = TsmColors.TextMain, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        val full = listOf(sp.firstName, sp.lastName).filter { it.isNotBlank() }.joinToString(" ")
                        if (full.isNotBlank() && full != sp.name) Text(full, color = TsmColors.TextDim, fontSize = 13.sp)
                    }
                }
                if (card.from.isNotBlank()) Text(s.pfFrom(card.from), color = TsmColors.TextDim, fontSize = 13.sp)
                val lines = listOfNotNull(
                    listOf(sp.phone, sp.email).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { null },
                    sp.birth?.let { "${s.memberBirth}: ${Reports.date(it, lang)}" },
                    sp.club.ifBlank { null },
                    listOf(sp.guardian, sp.guardianPhone).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { null }?.let { "${s.coGuardian}: $it" },
                    listOf(sp.fitpNumber, sp.ranking).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { null }?.let { "${s.pfFitp}: $it" },
                    sp.medicalUntil?.let { "${s.pfMedical}: ${Reports.date(it, lang)}" },
                )
                if (lines.isNotEmpty()) Text(lines.joinToString("\n"), color = TsmColors.TextMain, fontSize = 14.sp)
                if (match != null) {
                    SwitchRow(Icons.Filled.SwapHoriz, s.pfExisting(match.name), null, intoExisting) { intoExisting = it }
                } else Text(s.pfNew, color = TsmColors.Ok, fontSize = 13.sp)
                if (role == Role.COACH) SwitchRow(Icons.Filled.School, s.pfAsStudent, null, asStudent) { asStudent = it }
                if (role == Role.CLUB && (match?.member == null || !intoExisting)) SwitchRow(Icons.Filled.Badge, s.pfAsMember, null, asMember) { asMember = it }
            }
        },
        confirmButton = {
            val into = match?.id?.takeIf { intoExisting }
            TextButton(onClick = {
                val pl = c.applyProfile(card, into, asMember, asStudent)
                c.openStudent(pl.id)
            }) { Text(if (into != null) s.pfComplete else s.pfAdd) }
        },
        dismissButton = { TextButton(onClick = close) { Text(s.cancel) } },
    )
}
