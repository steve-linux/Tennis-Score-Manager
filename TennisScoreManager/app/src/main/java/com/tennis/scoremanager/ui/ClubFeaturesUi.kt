// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CardMembership
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tennis.scoremanager.ClubFeature
import com.tennis.scoremanager.MatchController

/** Nome, spiegazione e icona di una funzione del circolo (gli stessi delle voci dei menu). */
fun featureInfo(s: Strings, f: ClubFeature): Triple<String, String, ImageVector> = when (f) {
    ClubFeature.MEMBERS -> Triple(s.membersTitle, s.membersHint, Icons.Filled.CardMembership)
    ClubFeature.PAYMENTS -> Triple(s.paymentsTitle, s.paymentsHint, Icons.Filled.Payments)
    ClubFeature.PLANNING -> Triple(s.planTitle, s.planHint, Icons.Filled.CalendarMonth)
    ClubFeature.TOURNAMENTS -> Triple(s.ctTitle, s.ctHint, Icons.Filled.EmojiEvents)
    ClubFeature.TEAMS -> Triple(s.tmTitle, s.tmHint, Icons.Filled.Groups)
    ClubFeature.SCHOOL -> Triple(s.scTitle, s.scHint, Icons.Filled.School)
    ClubFeature.LADDER -> Triple(s.ldTitle, s.ldHint, Icons.Filled.Leaderboard)
    ClubFeature.STATS -> Triple(s.stTitle, s.cfStatsHint, Icons.Filled.BarChart)
    ClubFeature.SERVER -> Triple(s.srvTitle, s.srvButtonHint, Icons.Filled.Cloud)
    ClubFeature.DESK -> Triple(s.deskTitle, s.deskHint, Icons.Filled.Computer)
}

/** Le funzioni del circolo: un interruttore per ognuna, quelle di base prima. I dati delle funzioni spente restano. */
@Composable
fun ClubFeaturesDialog(c: MatchController, onDone: () -> Unit) {
    val s = LocalStrings.current
    val on by c.clubFeatures.collectAsState()
    val paired by c.clubServer.collectAsState()
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(s.cfTitle) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(s.cfHint, color = TsmColors.TextDim, fontSize = 13.sp)
                for ((title, list) in listOf(s.cfBase to ClubFeature.entries.filter { it.base }, s.cfExtra to ClubFeature.entries.filter { !it.base })) {
                    Text(title, color = TsmColors.Ball, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                    for (f in list) {
                        val (name, hint, icon) = featureInfo(s, f)
                        // Il server abbinato non si spegne da qui: prima si scollega (se no continuerebbe a lavorare nascosto).
                        val locked = f == ClubFeature.SERVER && paired.paired && f in on
                        SwitchRow(icon, name, if (locked) s.cfServerPaired else hint, f in on, enabled = !locked) { c.setFeature(f, it) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text(s.ok) } },
    )
}

/**
 * In cima alla prima scheda del Circolo, finché non si sono guardate le funzioni: un invito a scegliere solo quelle
 * che servono. Sotto ogni scheda resta comunque il tasto per cambiarle.
 */
@Composable
fun ClubFeaturesHint(c: MatchController) {
    val s = LocalStrings.current
    var seen by remember { mutableStateOf(c.storage.clubFeaturesSeen) }
    var open by remember { mutableStateOf(false) }
    if (!seen) Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(TsmColors.SurfaceHigh).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(s.cfAsk, color = TsmColors.TextMain, fontSize = 14.sp)
        GhostButton(s.cfChoose, Icons.Filled.Tune, { open = true }, Modifier.fillMaxWidth())
    }
    if (open) ClubFeaturesDialog(c) { open = false; seen = true; c.storage.clubFeaturesSeen = true }
}

/** In fondo alle schede del Circolo: cambia le funzioni. */
@Composable
fun ClubFeaturesButton(c: MatchController) {
    val s = LocalStrings.current
    var open by remember { mutableStateOf(false) }
    GhostButton(s.cfTitle, Icons.Filled.Tune, { open = true }, Modifier.fillMaxWidth())
    if (open) ClubFeaturesDialog(c) { open = false; c.storage.clubFeaturesSeen = true }
}
