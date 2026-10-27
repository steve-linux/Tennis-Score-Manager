// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.SportsTennis
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tennis.scoremanager.BuildConfig
import com.tennis.scoremanager.MatchController
import com.tennis.scoremanager.Screen
import com.tennis.scoremanager.data.SetupData
import com.tennis.scoremanager.data.Sport
import com.tennis.scoremanager.model.Side
import com.tennis.scoremanager.tv.DisplayActivity
import com.tennis.scoremanager.ui.BigButton
import com.tennis.scoremanager.ui.EditablePlayerAvatar
import com.tennis.scoremanager.ui.GhostButton
import com.tennis.scoremanager.ui.LocalStrings
import com.tennis.scoremanager.ui.ScreenScaffold
import com.tennis.scoremanager.ui.SectionCard
import com.tennis.scoremanager.ui.SegOption
import com.tennis.scoremanager.ui.Segmented
import com.tennis.scoremanager.ui.SwitchRow
import com.tennis.scoremanager.ui.TsmColors

/** Pagina 1: circolo, campo, singolare/doppio e nomi. Tutto facoltativo. */
@Composable
fun SetupScreen(c: MatchController) {
    val s = LocalStrings.current
    val su by c.setup.collectAsState()
    val o by c.options.collectAsState()
    val rackets by c.rackets.collectAsState()
    val context = LocalContext.current
    ScreenScaffold(
        title = s.setupTitle,
        subtitle = s.setupSubtitle,
        icon = Icons.Filled.SportsTennis,
        bottomBar = {
            GhostButton(s.clearFields, Icons.Filled.DeleteSweep, { c.updateSetup { SetupData(doubles = it.doubles, sport = it.sport) } }, Modifier.weight(1f))
            BigButton(s.next, Icons.AutoMirrored.Filled.ArrowForward, { c.go(Screen.OPTIONS) }, Modifier.weight(1f))
        },
    ) {
        // Scorciatoie in una riga: allenamento con la fascia, progressi stampabili, storico delle partite, video registrati.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HomeShortcut(Icons.Filled.MonitorHeart, TsmColors.Danger, s.workoutTitle, Modifier.weight(1f)) { c.openWorkouts() }
            HomeShortcut(Icons.AutoMirrored.Filled.TrendingUp, TsmColors.Ball, s.progressTitle, Modifier.weight(1f)) { c.openProgress() }
            HomeShortcut(Icons.Filled.History, TsmColors.Ball, s.historyTitle, Modifier.weight(1f)) { c.openHistory() }
            HomeShortcut(Icons.Filled.VideoLibrary, TsmColors.Ball, s.videoTitle, Modifier.weight(1f)) { c.screen.value = Screen.VIDEOS }
        }
        // Il padel si conta come il tennis: sceglierlo propone doppio e punto d'oro (si possono sempre cambiare).
        SectionCard(s.sportSection, Icons.Filled.SportsTennis) {
            Segmented(
                listOf(SegOption(s.sportTennis), SegOption(s.sportPadel)),
                selected = su.sport.ordinal,
                onSelect = { i -> c.selectSport(Sport.entries[i]) },
            )
            if (su.sport == Sport.PADEL) {
                Text(s.padelHint, color = TsmColors.TextDim)
                // Tornei a coppie che cambiano: schermata a parte, senza toccare la partita normale.
                GhostButton(s.tournamentButton, Icons.Filled.EmojiEvents, { c.openTournaments() }, Modifier.fillMaxWidth())
            }
        }
        SectionCard(s.clubSection, Icons.Filled.Business) {
            Field(su.club, s.clubName, Icons.Filled.Business) { v -> c.updateSetup { it.copy(club = v) } }
            Field(su.court, s.courtNumber, Icons.Filled.Tag, KeyboardType.Text) { v -> c.updateSetup { it.copy(court = v) } }
            // Facoltativo e spento di default: il Geocoder di sistema, con i servizi Google, manda le coordinate a Google.
            SwitchRow(Icons.Filled.Place, s.addressLookup, s.addressLookupHint, o.addressLookup) { v ->
                c.updateOptions { it.copy(addressLookup = v) }
            }
        }
        // Torneo: per i circuiti amatoriali e semiprofessionistici; tutto facoltativo.
        SectionCard(s.eventSection, Icons.Filled.EmojiEvents) {
            Field(su.event, s.eventName, Icons.Filled.EmojiEvents) { v -> c.updateSetup { it.copy(event = v) } }
            Field(su.category, s.eventCategory, Icons.Filled.Category) { v -> c.updateSetup { it.copy(category = v) } }
            Field(su.round, s.eventRound, Icons.Filled.Flag) { v -> c.updateSetup { it.copy(round = v) } }
            Text(s.eventHint, color = TsmColors.TextDim, fontSize = 13.sp)
        }
        SectionCard(if (su.doubles) s.doubles else s.singles, Icons.Filled.Groups) {
            Segmented(
                listOf(SegOption(s.singles, Icons.Filled.Person), SegOption(s.doubles, Icons.Filled.Groups)),
                selected = if (su.doubles) 1 else 0,
                onSelect = { i -> c.updateSetup { it.copy(doubles = i == 1) } },
            )
            if (su.doubles) Text(s.doublesHint, color = TsmColors.TextDim)
        }
        PlayerCard(c, su, Side.P1)
        PlayerCard(c, su, Side.P2)
        // Racchetta virtuale: le racchette con corde e tensioni, da confrontare nel tempo.
        SectionCard(s.racketTitle, Icons.Filled.SportsTennis) {
            Text(if (rackets.isEmpty()) s.racketHint else s.racketSetupHint, color = TsmColors.TextDim)
            GhostButton(s.racketMine, Icons.Filled.SportsTennis, { c.openRackets() }, Modifier.fillMaxWidth())
        }
        // Il secondo telefono, collegato al monitor, fa da tabellone per quello dell'arbitro.
        SectionCard(s.displayMode, Icons.Filled.Tv) {
            Text(s.displayModeHint, color = TsmColors.TextDim)
            GhostButton(s.displayMode, Icons.Filled.Tv, {
                context.startActivity(Intent(context, DisplayActivity::class.java))
            }, Modifier.fillMaxWidth())
        }
        AboutFooter()
    }
}

/** Indirizzo del codice sorgente, mostrato in fondo alla pagina 1. */
private const val SOURCE_URL = "https://github.com/steve-linux/Tennis-Score-Manager"

/** Versione, licenza e assenza di garanzia (la GPL chiede di mostrarle nei programmi con interfaccia), più il link al codice. */
@Composable
private fun AboutFooter() {
    val s = LocalStrings.current
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(s.aboutLicense(BuildConfig.VERSION_NAME), color = TsmColors.TextDim, fontSize = 12.sp, textAlign = TextAlign.Center)
        TextButton(onClick = {
            // Nessun browser installato: il tocco non fa nulla invece di chiudere l'app.
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SOURCE_URL))) }
        }) {
            Text("${s.aboutSource}: ${SOURCE_URL.removePrefix("https://")}", color = TsmColors.Ball, fontSize = 12.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun PlayerCard(c: MatchController, su: SetupData, side: Side) {
    val s = LocalStrings.current
    val accent = TsmColors.player(side)
    val title = if (side == Side.P1) s.player1 else s.player2
    SectionCard(title, Icons.Filled.Person, accent = accent) {
        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(accent))
        val a = if (side == Side.P1) su.p1a else su.p2a
        val b = if (side == Side.P1) su.p1b else su.p2b
        // A sinistra di ogni nome la sua foto: si tocca per metterla o cambiarla.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            EditablePlayerAvatar(c, a, accent, 52.dp)
            Box(Modifier.weight(1f)) {
                Field(a, if (su.doubles) "${s.playerName} A" else s.playerName, Icons.Filled.Person, accent = accent) { v ->
                    c.updateSetup { if (side == Side.P1) it.copy(p1a = v) else it.copy(p2a = v) }
                }
            }
        }
        Field(if (side == Side.P1) su.p1aRank else su.p2aRank, s.playerRank, Icons.Filled.Leaderboard, accent = accent) { v ->
            c.updateSetup { if (side == Side.P1) it.copy(p1aRank = v) else it.copy(p2aRank = v) }
        }
        SexChoice(if (side == Side.P1) su.p1aSex else su.p2aSex, accent) { v ->
            c.updateSetup { if (side == Side.P1) it.copy(p1aSex = v) else it.copy(p2aSex = v) }
        }
        // La racchetta di oggi: la partita ne tiene una copia con corde e tensione.
        if (c.rackets.collectAsState().value.isNotEmpty()) {
            RacketPicker(c, if (side == Side.P1) su.p1Racket else su.p2Racket, a) { id ->
                c.updateSetup { if (side == Side.P1) it.copy(p1Racket = id) else it.copy(p2Racket = id) }
            }
        }
        if (su.doubles) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                EditablePlayerAvatar(c, b, accent, 52.dp)
                Box(Modifier.weight(1f)) {
                    Field(b, "${s.playerName} B", Icons.Filled.Person, accent = accent) { v ->
                        c.updateSetup { if (side == Side.P1) it.copy(p1b = v) else it.copy(p2b = v) }
                    }
                }
            }
            Field(if (side == Side.P1) su.p1bRank else su.p2bRank, s.playerRank, Icons.Filled.Leaderboard, accent = accent) { v ->
                c.updateSetup { if (side == Side.P1) it.copy(p1bRank = v) else it.copy(p2bRank = v) }
            }
            SexChoice(if (side == Side.P1) su.p1bSex else su.p2bSex, accent) { v ->
                c.updateSetup { if (side == Side.P1) it.copy(p1bSex = v) else it.copy(p2bSex = v) }
            }
        }
    }
}

/** Uomo o donna (facoltativo): solo per la sagoma degli highlights quando manca la foto. Ritoccandolo si toglie. */
@Composable
private fun SexChoice(value: String, accent: Color, onChange: (String) -> Unit) {
    val s = LocalStrings.current
    Segmented(
        listOf(SegOption(s.heartMale, color = accent, onColor = TsmColors.Background), SegOption(s.heartFemale, color = accent, onColor = TsmColors.Background)),
        selected = when (value) { "M" -> 0; "F" -> 1; else -> -1 },
        onSelect = { i -> val v = if (i == 0) "M" else "F"; onChange(if (v == value) "" else v) },
    )
}

@Composable
private fun Field(
    value: String,
    label: String,
    icon: ImageVector,
    keyboard: KeyboardType = KeyboardType.Text,
    accent: Color = TsmColors.Ball,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.take(40)) },
        label = { Text(label) },
        leadingIcon = { Icon(icon, null) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Words,
            keyboardType = keyboard,
            imeAction = ImeAction.Next,
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = accent,
            focusedLabelColor = accent,
            focusedLeadingIconColor = accent,
            cursorColor = accent,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.width(0.dp))
}

@Composable
private fun HomeShortcut(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    label: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Surface(onClick = onClick, shape = RoundedCornerShape(14.dp), color = TsmColors.SurfaceHigh, modifier = modifier) {
        Column(Modifier.padding(horizontal = 2.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = tint)
            // Quattro scorciatoie in una riga: "Allenamento" deve starci anche sui telefoni stretti.
            Text(label, color = TsmColors.TextMain, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, letterSpacing = 0.sp,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
    }
}
