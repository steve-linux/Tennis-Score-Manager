// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tennis.scoremanager.MatchController
import com.tennis.scoremanager.Role

/** I tre ruoli, se ne sceglie uno: alla prima apertura e in Impostazioni › Ruolo. */
@Composable
fun RolePicker(selected: Role, onChange: (Role) -> Unit) {
    val s = LocalStrings.current
    for ((r, title, hint) in listOf(
        Triple(Role.PLAYER, s.moreProfile, s.roleYouHint),
        Triple(Role.COACH, s.roleCoach, s.roleCoachHint),
        Triple(Role.CLUB, s.roleClub, s.roleClubHint),
    )) {
        val icon = when (r) { Role.PLAYER -> Icons.Filled.Person; Role.COACH -> Icons.Filled.School; Role.CLUB -> Icons.Filled.Business }
        val on = r == selected
        androidx.compose.foundation.layout.Row(
            Modifier.fillMaxWidth()
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                .background(if (on) TsmColors.SurfaceHigh else TsmColors.Surface)
                .clickable { onChange(r) }.padding(12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            androidx.compose.material3.Icon(icon, null, tint = if (on) TsmColors.Ball else TsmColors.TextDim)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(title, color = TsmColors.TextMain, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(hint, color = TsmColors.TextDim, fontSize = 13.sp)
            }
            androidx.compose.material3.RadioButton(on, { onChange(r) })
        }
    }
}

/** Il circolo: nome e campi (uno per riga), per Nuova partita, i tornei e i tabelloni. */
@Composable
fun ClubFields(name: String, courts: String, onName: (String) -> Unit, onCourts: (String) -> Unit, courtsHere: Boolean = true) {
    val s = LocalStrings.current
    OutlinedTextField(
        name, { onName(it.take(60)) }, label = { Text(s.clubName) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = KeyboardCapitalization.Words),
    )
    // Con i campi già fatti si cambiano in Campi › I campi (lì rinominarli non stacca le prenotazioni).
    if (!courtsHere) { Text(s.clubCourtsElsewhere, color = TsmColors.TextDim, fontSize = 12.sp); return }
    OutlinedTextField(courts, { onCourts(it.take(400)) }, label = { Text(s.clubCourtsLabel) }, minLines = 3, modifier = Modifier.fillMaxWidth())
    Text(s.clubCourtsHint, color = TsmColors.TextDim, fontSize = 12.sp)
}

@Composable
fun ClubDialog(c: MatchController, onDone: () -> Unit) {
    val s = LocalStrings.current
    var name by remember { mutableStateOf(c.storage.clubName) }
    var courts by remember { mutableStateOf(c.storage.clubCourts.joinToString("\n")) }
    var phone by remember { mutableStateOf(c.storage.clubPhone) }
    val hadNone = remember { c.storage.clubCourts.isEmpty() }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(s.roleClub) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ClubFields(name, courts, { name = it }, { courts = it }, courtsHere = hadNone)
                // Il telefono della segreteria va nelle schede partita: i giocatori rimandano lì il risultato.
                OutlinedTextField(phone, { phone = it.take(30) }, label = { Text(s.clubPhone) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone))
                Text(s.clubPhoneHint, color = TsmColors.TextDim, fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                c.storage.clubName = name
                c.storage.clubPhone = phone
                if (hadNone) c.planning.setClubCourts(courts.lines())
                onDone()
            }) { Text(s.ok) }
        },
        dismissButton = { TextButton(onClick = onDone) { Text(s.back) } },
    )
}

/**
 * Ruolo e funzioni, in Impostazioni: il ruolo (uno solo; cambiandolo i dati restano) e, per quel ruolo, cosa tenere
 * nei menu (Cosa usi per Tu e Allenatore, le funzioni del circolo per la segreteria). Spento non vuol dire cancellato.
 */
@Composable
fun RolesFeaturesDialog(c: MatchController, onDone: () -> Unit) {
    val s = LocalStrings.current
    val role by c.activeRole.collectAsState()
    var features by remember { mutableStateOf(false) }
    if (features) {
        if (role == Role.CLUB) ClubFeaturesDialog(c) { features = false; c.storage.clubFeaturesSeen = true }
        else PlayerFeaturesDialog(c) { features = false; c.storage.playerFeaturesSeen = true }
        return
    }
    AlertDialog(
        onDismissRequest = onDone,
        icon = { androidx.compose.material3.Icon(Icons.Filled.Tune, null, tint = TsmColors.Ball) },
        title = { Text(s.rolesFeatures) },
        text = {
            Column(androidx.compose.ui.Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(s.rolesTitle, color = TsmColors.Ball, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(s.rolesHint, color = TsmColors.TextDim, fontSize = 13.sp)
                RolePicker(role) { if (it != role) c.setRole(it) }
                val club = role == Role.CLUB
                Text(if (club) s.cfTitle else s.pfTitle, color = TsmColors.Ball, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = androidx.compose.ui.Modifier.padding(top = 8.dp))
                Text(if (club) s.cfHint else s.pfEntryHint, color = TsmColors.TextDim, fontSize = 13.sp)
                GhostButton(if (club) s.cfTitle else s.pfTitle, Icons.Filled.Tune, { features = true }, androidx.compose.ui.Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text(s.ok) } },
    )
}

/**
 * Il ruolo sempre in vista, in alto nella prima schermata: mostra quello attivo e con un tocco apre gli altri.
 * Cambiare ruolo cambia solo le schede (come in Impostazioni): i dati restano.
 */
@Composable
fun RoleSwitchButton(c: MatchController) {
    val s = LocalStrings.current
    val role by c.activeRole.collectAsState()
    var open by remember { mutableStateOf(false) }
    fun name(r: Role) = when (r) { Role.PLAYER -> s.moreProfile; Role.COACH -> s.roleCoach; Role.CLUB -> s.roleClub }
    fun icon(r: Role) = when (r) { Role.PLAYER -> Icons.Filled.Person; Role.COACH -> Icons.Filled.School; Role.CLUB -> Icons.Filled.Business }
    androidx.compose.foundation.layout.Box {
        androidx.compose.foundation.layout.Row(
            Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
                .background(TsmColors.SurfaceHigh)
                .border(1.dp, TsmColors.Ball, androidx.compose.foundation.shape.RoundedCornerShape(50))
                .clickable(onClickLabel = s.roleSwitch) { open = true }
                .semantics { contentDescription = "${s.roleSwitch}: ${name(role)}" }
                .padding(start = 10.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            androidx.compose.material3.Icon(icon(role), null, tint = TsmColors.Ball, modifier = Modifier.size(20.dp))
            Text(
                name(role), color = TsmColors.TextMain, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1,
                modifier = Modifier.padding(start = 6.dp).widthIn(max = 110.dp),
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            androidx.compose.material3.Icon(Icons.Filled.ArrowDropDown, null, tint = TsmColors.TextDim)
        }
        androidx.compose.material3.DropdownMenu(open, { open = false }) {
            Text(s.roleSwitch, color = TsmColors.TextDim, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            for (r in Role.entries) {
                val on = r == role
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(name(r), fontWeight = if (on) FontWeight.Bold else FontWeight.Normal) },
                    leadingIcon = { androidx.compose.material3.Icon(icon(r), null, tint = if (on) TsmColors.Ball else TsmColors.TextDim) },
                    trailingIcon = if (on) ({ androidx.compose.material3.Icon(Icons.Filled.Check, null, tint = TsmColors.Ball) }) else null,
                    onClick = { open = false; if (!on) c.setRole(r) },
                )
            }
        }
    }
}
