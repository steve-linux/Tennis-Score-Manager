// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.ui

import androidx.compose.foundation.layout.Arrangement
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

/** I tre ruoli, ciascuno acceso o spento (almeno uno resta): in Profilo e dati e alla prima apertura. */
@Composable
fun RolesPicker(selected: Set<Role>, onChange: (Set<Role>) -> Unit) {
    val s = LocalStrings.current
    for ((r, title, hint) in listOf(
        Triple(Role.PLAYER, s.moreProfile, s.roleYouHint),
        Triple(Role.COACH, s.roleCoach, s.roleCoachHint),
        Triple(Role.CLUB, s.roleClub, s.roleClubHint),
    )) {
        val icon = when (r) { Role.PLAYER -> Icons.Filled.Person; Role.COACH -> Icons.Filled.School; Role.CLUB -> Icons.Filled.Business }
        SwitchRow(icon, title, hint, r in selected) { on ->
            val next = if (on) selected + r else selected - r
            if (next.isNotEmpty()) onChange(next)
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
    val hadNone = remember { c.storage.clubCourts.isEmpty() }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(s.roleClub) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { ClubFields(name, courts, { name = it }, { courts = it }, courtsHere = hadNone) } },
        confirmButton = {
            TextButton(onClick = {
                c.storage.clubName = name
                if (hadNone) c.setClubCourts(courts.lines())
                onDone()
            }) { Text(s.ok) }
        },
        dismissButton = { TextButton(onClick = onDone) { Text(s.back) } },
    )
}

/**
 * Ruoli e funzioni, in un posto solo (Altro e il ruolo in alto): chi usa l'app e, per il ruolo attivo, cosa tenere nei
 * menu (Cosa usi per Tu e Allenatore, le funzioni del circolo per la segreteria). Spento non vuol dire cancellato.
 */
@Composable
fun RolesFeaturesDialog(c: MatchController, onDone: () -> Unit) {
    val s = LocalStrings.current
    val roles by c.roles.collectAsState()
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
                RolesPicker(roles) { c.setRoles(it) }
                val club = role == Role.CLUB
                Text(if (club) s.cfTitle else s.pfTitle, color = TsmColors.Ball, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = androidx.compose.ui.Modifier.padding(top = 8.dp))
                Text(if (club) s.cfHint else s.pfEntryHint, color = TsmColors.TextDim, fontSize = 13.sp)
                GhostButton(if (club) s.cfTitle else s.pfTitle, Icons.Filled.Tune, { features = true }, androidx.compose.ui.Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text(s.ok) } },
    )
}
