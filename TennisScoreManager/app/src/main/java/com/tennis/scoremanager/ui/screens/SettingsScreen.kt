// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.tennis.scoremanager.BuildConfig
import com.tennis.scoremanager.MatchController
import com.tennis.scoremanager.Screen
import com.tennis.scoremanager.data.Reports
import com.tennis.scoremanager.model.Lang
import com.tennis.scoremanager.ui.GhostButton
import com.tennis.scoremanager.ui.LocalStrings
import com.tennis.scoremanager.ui.RolesFeaturesDialog
import com.tennis.scoremanager.ui.ScreenScaffold
import com.tennis.scoremanager.ui.SectionCard
import com.tennis.scoremanager.ui.SegOption
import com.tennis.scoremanager.ui.Segmented
import com.tennis.scoremanager.ui.TsmColors

/** Indirizzo del codice sorgente, mostrato con la versione e la licenza. */
private const val SOURCE_URL = "https://github.com/steve-linux/Tennis-Score-Manager"

/**
 * Altro › Impostazioni, le stesse per tutti i profili: la lingua in cima, poi la voce dell'arbitro, i dispositivi, ruoli
 * e funzioni, backup e dati, e in fondo le informazioni sull'app (versione, licenza e codice sorgente: la GPL chiede di
 * mostrarli).
 */
@Composable
fun SettingsScreen(c: MatchController) {
    val s = LocalStrings.current
    val context = LocalContext.current
    val o by c.options.collectAsState()
    var roles by remember { mutableStateOf(false) }
    BackHandler { c.back() }
    ScreenScaffold(
        onHelp = { c.openHelp("3.1") },
        // La versione si vede subito, senza scorrere fino a Informazioni.
        title = s.settingsTitle, subtitle = "Tennis Score Manager ${BuildConfig.VERSION_NAME}", icon = Icons.Filled.Settings,
        bottomBar = { GhostButton(s.back, Icons.AutoMirrored.Filled.ArrowBack, { c.back() }, Modifier.weight(1f)) },
    ) {
        SectionCard(s.languageSection, Icons.Filled.Language) {
            // Ogni lingua è scritta nella lingua stessa, così si ritrova anche se l'app è in una lingua che non si legge.
            // Bloccata mentre si generano i file vocali (sono nella lingua di adesso).
            val generating = c.voiceProgress.collectAsState().value != null
            Segmented(
                Lang.entries.map { SegOption("${it.flag}  ${it.label}") },
                selected = o.lang.ordinal,
                onSelect = { i -> c.updateOptions { it.copy(lang = Lang.entries[i]) } },
                columns = 2,
                enabled = !generating,
            )
            Text(s.languageHint, color = TsmColors.TextDim, fontSize = 13.sp)
        }
        Entry(Icons.Filled.RecordVoiceOver, s.moreVoice, s.moreVoiceHint) { c.go(Screen.VOICE) }
        Entry(Icons.Filled.Bluetooth, s.moreDevices, s.moreDevicesHint) { c.go(Screen.DEVICES) }
        Entry(Icons.Filled.Tune, s.rolesFeatures, s.rolesFeaturesHint) { roles = true }
        Entry(Icons.Filled.Backup, s.dataTitle, s.dataHint) { c.go(Screen.DATA) }
        SectionCard(s.aboutTitle, Icons.Filled.Info) {
            Text("Tennis Score Manager", color = TsmColors.TextMain, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(s.aboutVersion(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE), color = TsmColors.Ball, fontWeight = FontWeight.Bold)
            Text(s.aboutLicense(BuildConfig.VERSION_NAME), color = TsmColors.TextDim, fontSize = 13.sp)
            // Nessun browser installato: il tocco non fa nulla invece di chiudere l'app.
            fun open(url: String) = runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            GhostButton(s.aboutSite, Icons.Filled.Public, { open(Reports.siteUrl(o.lang)) }, Modifier.fillMaxWidth())
            GhostButton(s.aboutSource, Icons.Filled.Code, { open(SOURCE_URL) }, Modifier.fillMaxWidth())
        }
    }
    if (roles) RolesFeaturesDialog(c) { roles = false }
}
