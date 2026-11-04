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
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.SportsTennis
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.SportsBaseball
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tennis.scoremanager.MatchController
import com.tennis.scoremanager.PlayerFeature

/** Nome, spiegazione e icona di una cosa che si può usare. */
fun playerFeatureInfo(s: Strings, f: PlayerFeature): Triple<String, String, ImageVector> = when (f) {
    PlayerFeature.BANDS -> Triple(s.pfBands, s.pfBandsHint, Icons.Filled.Watch)
    PlayerFeature.HEART -> Triple(s.pfHeart, s.pfHeartHint, Icons.Filled.MonitorHeart)
    PlayerFeature.TRAINING -> Triple(s.pfTraining, s.pfTrainingHint, Icons.Filled.FitnessCenter)
    PlayerFeature.VIDEO -> Triple(s.pfVideo, s.pfVideoHint, Icons.Filled.Videocam)
    PlayerFeature.PROGRESS -> Triple(s.pfProgress, s.pfProgressHint, Icons.Filled.Timeline)
    PlayerFeature.RACKETS -> Triple(s.pfRackets, s.pfRacketsHint, Icons.Filled.SportsTennis)
    PlayerFeature.PADEL -> Triple(s.sportPadel, s.pfPadelHint, Icons.Filled.SportsBaseball)
}

/** Cosa usi: un interruttore per ognuna; quello che è spento non compare, i dati restano. */
@Composable
fun PlayerFeaturesDialog(c: MatchController, onDone: () -> Unit) {
    val s = LocalStrings.current
    val on by c.playerFeatures.collectAsState()
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(s.pfTitle) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(s.pfHint, color = TsmColors.TextDim, fontSize = 13.sp)
                for (f in PlayerFeature.entries) {
                    val (name, hint, icon) = playerFeatureInfo(s, f)
                    SwitchRow(icon, name, hint, f in on) { c.setPlayerFeature(f, it) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text(s.ok) } },
    )
}

/** In cima a Gioca, finché non si è guardato: l'invito a tenere solo quello che si usa. */
@Composable
fun PlayerFeaturesHint(c: MatchController) {
    val s = LocalStrings.current
    var seen by remember { mutableStateOf(c.storage.playerFeaturesSeen) }
    var open by remember { mutableStateOf(false) }
    if (!seen) Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(TsmColors.SurfaceHigh).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(s.pfAsk, color = TsmColors.TextMain, fontSize = 14.sp)
        GhostButton(s.pfChoose, Icons.Filled.Tune, { open = true }, Modifier.fillMaxWidth())
    }
    if (open) PlayerFeaturesDialog(c) { open = false; seen = true; c.storage.playerFeaturesSeen = true }
}
