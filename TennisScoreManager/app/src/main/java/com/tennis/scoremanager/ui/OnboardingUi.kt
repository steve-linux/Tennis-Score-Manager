// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SportsTennis
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tennis.scoremanager.HomeTab
import com.tennis.scoremanager.MatchController
import com.tennis.scoremanager.Screen
import com.tennis.scoremanager.data.PlayMode
import com.tennis.scoremanager.model.Lang

/** Come si userà l'app: decide la modalità di serie e la scheda da cui si parte. */
private enum class Use { REFEREE, BANDS, TRAIN, VIDEO }

/**
 * Prima accensione: lingua, come si userà l'app e il proprio nome, a tutto schermo. Si può saltare; alla fine
 * l'app parte dalla scheda giusta (coi braccialetti, dai dispositivi da collegare).
 */
@Composable
fun OnboardingFlow(c: MatchController, onDone: () -> Unit) {
    val s = LocalStrings.current
    val o by c.options.collectAsState()
    var step by remember { mutableIntStateOf(0) }
    var use by remember { mutableStateOf<Use?>(null) }
    var name by remember { mutableStateOf(c.storage.myName) }
    fun finish() {
        c.storage.onboarded = true
        val n = name.trim()
        c.storage.myName = n
        if (n.isNotEmpty() && c.setup.value.p1a.isBlank()) c.updateSetup { it.copy(p1a = n) }
        when (use) {
            Use.REFEREE -> { c.updateOptions { it.copy(mode = PlayMode.REFEREE) }; c.home(HomeTab.PLAY) }
            Use.BANDS -> { c.updateOptions { it.copy(mode = PlayMode.BANDS) }; c.home(HomeTab.PLAY); c.go(Screen.DEVICES) }
            Use.TRAIN -> c.home(HomeTab.TRAIN)
            Use.VIDEO -> c.home(HomeTab.VIDEO)
            null -> c.home(HomeTab.PLAY)
        }
        onDone()
    }
    Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        Column(
            Modifier.fillMaxSize().background(TsmColors.Background).systemBarsPadding().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(14.dp), color = TsmColors.Ball, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Filled.SportsTennis, null, tint = TsmColors.OnBall, modifier = Modifier.padding(10.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.onbWelcome, color = TsmColors.TextMain, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Text("${step + 1} / 3", color = TsmColors.TextDim, fontSize = 13.sp)
                }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (step) {
                    0 -> {
                        Text(s.onbLang, color = TsmColors.TextMain, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        // Ogni lingua è scritta in sé stessa: si ritrova anche se l'app parte in una lingua che non si legge.
                        Segmented(Lang.entries.map { SegOption("${it.flag}  ${it.label}") }, o.lang.ordinal, { i -> c.updateOptions { it.copy(lang = Lang.entries[i]) } }, columns = 2)
                    }
                    1 -> {
                        Text(s.onbUseTitle, color = TsmColors.TextMain, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        for ((u, icon, title, hint) in listOf(
                            Quad(Use.REFEREE, Icons.Filled.Gavel, s.onbUseReferee, s.onbUseRefereeHint),
                            Quad(Use.BANDS, Icons.Filled.Watch, s.onbUseBands, s.onbUseBandsHint),
                            Quad(Use.TRAIN, Icons.Filled.MonitorHeart, s.onbUseTrain, s.onbUseTrainHint),
                            Quad(Use.VIDEO, Icons.Filled.Videocam, s.onbUseVideo, s.onbUseVideoHint),
                        )) {
                            Choice(icon, title, hint, use == u) { use = u }
                        }
                    }
                    else -> {
                        Text(s.onbNameTitle, color = TsmColors.TextMain, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        OutlinedTextField(
                            name, { name = it.take(40) }, label = { Text(s.profileName) }, leadingIcon = { Icon(Icons.Filled.Person, null) }, singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = KeyboardCapitalization.Words), modifier = Modifier.fillMaxWidth(),
                        )
                        Text(s.onbNameHint, color = TsmColors.TextDim, fontSize = 13.sp)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton(s.onbSkip, Icons.AutoMirrored.Filled.ArrowForward, { finish() }, Modifier.weight(1f))
                BigButton(if (step < 2) s.next else s.onbStart, Icons.AutoMirrored.Filled.ArrowForward, { if (step < 2) step++ else finish() }, Modifier.weight(1f))
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

private data class Quad(val use: Use, val icon: ImageVector, val title: String, val hint: String)

@Composable
private fun Choice(icon: ImageVector, title: String, hint: String, on: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(16.dp), color = if (on) TsmColors.Ball else TsmColors.Surface, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (on) TsmColors.OnBall else TsmColors.Ball, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, color = if (on) TsmColors.OnBall else TsmColors.TextMain, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(hint, color = if (on) TsmColors.OnBall else TsmColors.TextDim, fontSize = 13.sp)
            }
        }
    }
}

/** Altro › Tu: il proprio nome (Progressi parte da lì, la scheda Progressi mostra il proprio rating). */
@Composable
fun ProfileDialog(c: MatchController, onDone: () -> Unit) {
    val s = LocalStrings.current
    var name by remember { mutableStateOf(c.storage.myName) }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(s.moreProfile) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    name, { name = it.take(40) }, label = { Text(s.profileName) }, leadingIcon = { Icon(Icons.Filled.Person, null) }, singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = KeyboardCapitalization.Words), modifier = Modifier.fillMaxWidth(),
                )
                Text(s.moreProfileHint, color = TsmColors.TextDim, fontSize = 13.sp)
            }
        },
        confirmButton = { TextButton(onClick = { c.storage.myName = name; onDone() }) { Text(s.ok) } },
        dismissButton = { TextButton(onClick = onDone) { Text(s.back) } },
    )
}
