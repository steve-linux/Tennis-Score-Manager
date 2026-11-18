// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
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
import com.tennis.scoremanager.data.PlayerStats
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore

/** Pagina 1: circolo, campo, singolare/doppio e nomi. Tutto facoltativo. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SetupScreen(c: MatchController) {
    val s = LocalStrings.current
    val su by c.setup.collectAsState()
    val o by c.options.collectAsState()
    val rackets by c.rackets.collectAsState()
    val context = LocalContext.current
    // Pagina 1 è il primo passo di una nuova partita: "Indietro" torna da dove la si è aperta.
    androidx.activity.compose.BackHandler { c.back() }
    var permTick by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val locationPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions(),
    ) { permTick++ }
    // In campo vanno giocatori del database: un nome senza scheda (partite di prima, rivincite) si sceglie dall'elenco
    // o si crea, prima di andare avanti.
    val creating by c.playerToCreate.collectAsState()
    fun missing(setup: SetupData) = com.tennis.scoremanager.data.Players.slots(setup).firstOrNull { it.name.isNotBlank() && c.playerById(it.id) == null }
    creating?.let { slot ->
        val taken = com.tennis.scoremanager.data.Players.slots(su).filter { it.side != slot.side || it.second != slot.second }.map { it.id }.filter { it.isNotEmpty() }.toSet()
        PlayerPickDialog(c, taken, title = s.setupPickPlayer, query0 = slot.name) { p ->
            c.playerToCreate.value = null
            if (p != null) {
                c.updateSetup { it.withPlayer(c, slot.side, slot.second, p.name, p.id) }
                // Il prossimo da sistemare, o avanti.
                if (slot.fromNext) missing(c.setup.value)?.let { c.playerToCreate.value = it.copy(fromNext = true) } ?: c.go(Screen.OPTIONS)
            }
        }
    }
    ScreenScaffold(
        onHelp = { c.openHelp("7") },
        title = s.setupTitle,
        subtitle = s.step1Hint,
        icon = Icons.Filled.SportsTennis,
        bottomBar = {
            GhostButton(s.back, Icons.AutoMirrored.Filled.ArrowBack, { c.back() }, Modifier.weight(1f))
            BigButton(s.next, Icons.AutoMirrored.Filled.ArrowForward, {
                missing(su)?.let { c.playerToCreate.value = it.copy(fromNext = true) } ?: c.go(Screen.OPTIONS)
            }, Modifier.weight(1f))
        },
        // Svuota i campi: in alto accanto al titolo, così in basso restano solo Indietro e Avanti.
        action = {
            androidx.compose.material3.IconButton(onClick = { c.updateSetup { SetupData(doubles = it.doubles, sport = it.sport) } }) {
                Icon(Icons.Filled.DeleteSweep, s.clearFields, tint = TsmColors.TextDim)
            }
        },
    ) {
        // Prima chi gioca (con i giocatori recenti da toccare), poi lo sport; circolo e torneo sotto, chiusi.
        SectionCard(if (su.doubles) s.doubles else s.singles, Icons.Filled.Groups) {
            Segmented(
                listOf(SegOption(s.singles, Icons.Filled.Person), SegOption(s.doubles, Icons.Filled.Groups)),
                selected = if (su.doubles) 1 else 0,
                onSelect = { i -> c.updateSetup { it.copy(doubles = i == 1) } },
            )
            if (su.doubles) Text(s.doublesHint, color = TsmColors.TextDim)
        }
        val recent = remember(c.history.collectAsState().value, c.players.collectAsState().value) { c.progressPlayers().take(12) }
        PlayerCard(c, su, Side.P1, recent)
        PlayerCard(c, su, Side.P2, recent)
        // Il padel si conta come il tennis: sceglierlo propone doppio e punto d'oro (si possono sempre cambiare).
        if (c.uses(com.tennis.scoremanager.PlayerFeature.PADEL) || su.sport == Sport.PADEL) SectionCard(s.sportSection, Icons.Filled.SportsTennis) {
            Segmented(
                listOf(SegOption(s.sportTennis), SegOption(s.sportPadel)),
                selected = su.sport.ordinal,
                onSelect = { i -> c.selectSport(Sport.entries[i]) },
            )
            if (su.sport == Sport.PADEL) Text(s.padelHint, color = TsmColors.TextDim)
        }
        // Circolo, campo e torneo: facoltativi, chiusi finché non servono (aperti se già scritti).
        val filled = listOf(su.club, su.court, su.event, su.category, su.round).any { it.isNotBlank() }
        var details by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(filled) }
        SectionCard(s.moreDetails, Icons.Filled.Business) {
            if (!details) {
                Text(s.moreDetailsHint, color = TsmColors.TextDim)
                GhostButton(s.moreDetails, Icons.Filled.Business, { details = true }, Modifier.fillMaxWidth())
            } else {
                Field(su.club, s.clubName, Icons.Filled.Business) { v -> c.updateSetup { it.copy(club = v) } }
                Field(su.court, s.courtNumber, Icons.Filled.Tag, KeyboardType.Text) { v -> c.updateSetup { it.copy(court = v) } }
                // I campi del circolo (Circolo › Campi): un tocco e circolo e campo sono scritti.
                val courts = c.storage.clubCourts
                if (courts.isNotEmpty()) androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    for (ct in courts) Text(
                        ct, color = if (su.court == ct) TsmColors.OnBall else TsmColors.Ball, fontSize = 13.sp,
                        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(if (su.court == ct) TsmColors.Ball else TsmColors.SurfaceHigh)
                            .clickable { c.updateSetup { it.copy(court = ct, club = it.club.ifBlank { c.storage.clubName }) } }.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
                // Il luogo nel riepilogo: dalla posizione del telefono (chiesta una volta prima della partita).
                val place by c.matchPlace.collectAsState()
                SwitchRow(Icons.Filled.Place, s.matchPlace, s.matchPlaceHint, place) { v -> c.setMatchPlace(v) }
                val located = remember(permTick) { com.tennis.scoremanager.data.LocationHelper.hasPermission(context) }
                if (place && !located) {
                    GhostButton("${s.allow}: ${s.location}", Icons.Filled.Place, {
                        locationPermission.launch(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION))
                    }, Modifier.fillMaxWidth())
                }
                // Facoltativo e spento di default: il Geocoder di sistema, con i servizi Google, manda le coordinate a Google.
                if (place) SwitchRow(Icons.Filled.Place, s.addressLookup, s.addressLookupHint, o.addressLookup) { v ->
                    c.updateOptions { it.copy(addressLookup = v) }
                }
                // Torneo: per i circuiti amatoriali e semiprofessionistici; tutto facoltativo.
                Text(s.eventSection, color = TsmColors.TextMain, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                Field(su.event, s.eventName, Icons.Filled.EmojiEvents) { v -> c.updateSetup { it.copy(event = v) } }
                Field(su.category, s.eventCategory, Icons.Filled.Category) { v -> c.updateSetup { it.copy(category = v) } }
                Field(su.round, s.eventRound, Icons.Filled.Flag) { v -> c.updateSetup { it.copy(round = v) } }
                Text(s.eventHint, color = TsmColors.TextDim, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun PlayerCard(c: MatchController, su: SetupData, side: Side, recent: List<Pair<String, String>> = emptyList()) {
    val s = LocalStrings.current
    val accent = TsmColors.player(side)
    val title = if (side == Side.P1) s.player1 else s.player2
    SectionCard(title, Icons.Filled.Person, accent = accent) {
        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(accent))
        val a = if (side == Side.P1) su.p1a else su.p2a
        val b = if (side == Side.P1) su.p1b else su.p2b
        // A sinistra di ogni nome la sua foto: si tocca per metterla o cambiarla.
        PlayerSlot(c, su, side, false, recent, accent)
        if (su.doubles) PlayerSlot(c, su, side, true, recent, accent)
        // Classifica, uomo/donna e racchetta arrivano dalla scheda del giocatore: qui in una riga, aperti solo se servono.
        val rackets by c.rackets.collectAsState()
        var details by androidx.compose.runtime.saveable.rememberSaveable(side) { mutableStateOf(false) }
        if (!details) {
            val racket = rackets.firstOrNull { it.id == (if (side == Side.P1) su.p1Racket else su.p2Racket) }?.label
            val sexOf = { name: String, field: String -> when (field.ifEmpty { if (name.isBlank()) "" else c.storage.playerSex(PlayerStats.key(name)) }) { "M" -> s.heartMale; "F" -> s.heartFemale; else -> null } }
            val line = listOfNotNull(
                (if (side == Side.P1) su.p1aRank else su.p2aRank).ifBlank { null },
                sexOf(a, if (side == Side.P1) su.p1aSex else su.p2aSex),
                if (su.doubles) (if (side == Side.P1) su.p1bRank else su.p2bRank).ifBlank { null } else null,
                if (su.doubles) sexOf(b, if (side == Side.P1) su.p1bSex else su.p2bSex) else null,
                racket?.ifBlank { null },
            ).joinToString(" · ")
            // Una riga da toccare, con la freccia: "Cambia" accanto al nome è già quello che cambia il giocatore.
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { details = true }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(line.ifEmpty { s.playerDetails }, color = TsmColors.TextDim, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.ExpandMore, s.playerDetails, tint = accent)
            }
            return@SectionCard
        }
        Field(if (side == Side.P1) su.p1aRank else su.p2aRank, s.playerRank, Icons.Filled.Leaderboard, accent = accent) { v ->
            c.updateSetup { if (side == Side.P1) it.copy(p1aRank = v) else it.copy(p2aRank = v) }
        }
        SexChoice(c, a, if (side == Side.P1) su.p1aSex else su.p2aSex, accent) { v ->
            c.updateSetup { if (side == Side.P1) it.copy(p1aSex = v) else it.copy(p2aSex = v) }
        }
        // La racchetta di oggi: la partita ne tiene una copia con corde e tensione.
        if (rackets.isNotEmpty()) {
            RacketPicker(c, if (side == Side.P1) su.p1Racket else su.p2Racket, a) { id ->
                c.updateSetup { if (side == Side.P1) it.copy(p1Racket = id) else it.copy(p2Racket = id) }
            }
        }
        if (su.doubles) {
            Field(if (side == Side.P1) su.p1bRank else su.p2bRank, s.playerRank, Icons.Filled.Leaderboard, accent = accent) { v ->
                c.updateSetup { if (side == Side.P1) it.copy(p1bRank = v) else it.copy(p2bRank = v) }
            }
            SexChoice(c, b, if (side == Side.P1) su.p1bSex else su.p2bSex, accent) { v ->
                c.updateSetup { if (side == Side.P1) it.copy(p1bSex = v) else it.copy(p2bSex = v) }
            }
        }
    }
}

/**
 * Il nome di un posto in campo, col giocatore del database se lo si è scelto ([id]). Scritto a mano resta di quel
 * giocatore solo finché è ancora il suo nome.
 */
private fun SetupData.withPlayer(c: MatchController, side: Side, second: Boolean, name: String, id: String?): SetupData {
    val cur = when { side == Side.P1 && !second -> p1aId; side == Side.P1 -> p1bId; !second -> p2aId; else -> p2bId }
    val keep = id ?: cur.takeIf { c.playerById(it)?.let { p -> PlayerStats.key(p.name) == PlayerStats.key(name) } == true }.orEmpty()
    return when {
        side == Side.P1 && !second -> copy(p1a = name, p1aId = keep)
        side == Side.P1 -> copy(p1b = name, p1bId = keep)
        !second -> copy(p2a = name, p2aId = keep)
        else -> copy(p2b = name, p2bId = keep)
    }
}

/**
 * Un posto in campo: il giocatore del database scelto (foto, nome, classifica) con Cambia e la X; vuoto, il tasto per
 * sceglierlo dall'elenco (dove si può anche crearne uno nuovo) e i giocatori recenti da toccare al volo. Un nome senza
 * scheda (partite di prima) va scelto dall'elenco.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun PlayerSlot(c: MatchController, su: SetupData, side: Side, second: Boolean, recent: List<Pair<String, String>>, accent: Color) {
    val s = LocalStrings.current
    val name = when { side == Side.P1 && !second -> su.p1a; side == Side.P1 -> su.p1b; !second -> su.p2a; else -> su.p2b }
    val id = when { side == Side.P1 && !second -> su.p1aId; side == Side.P1 -> su.p1bId; !second -> su.p2aId; else -> su.p2bId }
    // Chi è già negli altri posti non si propone.
    val others = com.tennis.scoremanager.data.Players.slots(su).filter { it.side != side || it.second != second }
    val takenIds = others.map { it.id }.filter { it.isNotEmpty() }.toSet()
    val linked = c.playerById(id)
    var picking by remember { mutableStateOf(false) }
    fun choose(p: com.tennis.scoremanager.data.PlayerProfile) = c.updateSetup { cur ->
        val x = cur.withPlayer(c, side, second, p.name, p.id)
        // La classifica di un socio entra da sola, se non è già scritta.
        val rank = p.member?.ranking.orEmpty()
        if (rank.isBlank()) x else when {
            side == Side.P1 && !second -> x.copy(p1aRank = x.p1aRank.ifBlank { rank })
            side == Side.P1 -> x.copy(p1bRank = x.p1bRank.ifBlank { rank })
            !second -> x.copy(p2aRank = x.p2aRank.ifBlank { rank })
            else -> x.copy(p2bRank = x.p2bRank.ifBlank { rank })
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        EditablePlayerAvatar(c, name, accent, 52.dp)
        if (linked != null) {
            Column(Modifier.weight(1f)) {
                Text(linked.name, color = TsmColors.TextMain, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = 18.sp)
                val info = listOfNotNull(linked.fullName.takeIf { it.isNotBlank() && it != linked.name }, linked.member?.ranking?.ifBlank { null }).joinToString(" · ")
                if (info.isNotBlank()) Text(info, color = TsmColors.TextDim, fontSize = 12.sp)
            }
            TextButton(onClick = { picking = true }) { Text(s.setupChange, color = accent) }
            androidx.compose.material3.IconButton(onClick = { c.updateSetup { it.withPlayer(c, side, second, "", "") } }) {
                Icon(Icons.Filled.Close, null, tint = TsmColors.TextDim)
            }
        } else {
            Column(Modifier.weight(1f)) {
                if (name.isNotBlank()) Text(s.setupNotInDb(name.trim()), color = TsmColors.Orange, fontSize = 13.sp)
                GhostButton(if (su.doubles) "${s.setupPickPlayer} ${if (second) "B" else "A"}" else s.setupPickPlayer, Icons.Filled.PersonAdd, { picking = true }, Modifier.fillMaxWidth())
            }
        }
    }
    // Vuoto: i giocatori recenti (del database), un tocco e sono in campo.
    if (linked == null && name.isBlank()) {
        val picks = recent.mapNotNull { (k, _) -> c.playerById(k) }.filter { it.id !in takenIds }.distinctBy { it.id }.take(6)
        if (picks.isNotEmpty()) androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (p in picks) Text(
                p.name, color = accent, fontSize = 13.sp,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(TsmColors.SurfaceHigh).clickable { choose(p) }.padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
    if (picking) PlayerPickDialog(c, takenIds, title = s.setupPickPlayer, query0 = if (linked == null) name.trim() else "") { p ->
        picking = false
        p?.let(::choose)
    }
}

/**
 * Uomo o donna (facoltativo): sceglie il ritratto del giocatore quando manca la foto, in tutta l'app. Vale per il
 * nome, come la foto: lo si ritrova nelle partite dopo. Ritoccandolo si toglie.
 */
@Composable
private fun SexChoice(c: MatchController, name: String, field: String, accent: Color, onChange: (String) -> Unit) {
    val s = LocalStrings.current
    val version by c.photosVersion.collectAsState()
    val value = remember(name, field, version) { field.ifEmpty { if (name.isBlank()) "" else c.storage.playerSex(PlayerStats.key(name)) } }
    Segmented(
        listOf(SegOption(s.heartMale, color = accent, onColor = TsmColors.Background), SegOption(s.heartFemale, color = accent, onColor = TsmColors.Background)),
        selected = when (value) { "M" -> 0; "F" -> 1; else -> -1 },
        onSelect = { i ->
            val v = if (i == 0) "M" else "F"
            val next = if (v == value) "" else v
            onChange(next)
            c.setPlayerSex(name, next)
        },
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
