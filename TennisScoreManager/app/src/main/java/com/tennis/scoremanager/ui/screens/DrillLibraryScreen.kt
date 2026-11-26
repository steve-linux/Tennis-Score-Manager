// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tennis.scoremanager.MatchController
import com.tennis.scoremanager.data.DrillLibrary
import com.tennis.scoremanager.ui.BigButton
import com.tennis.scoremanager.ui.CourtDiagram
import com.tennis.scoremanager.ui.GhostButton
import com.tennis.scoremanager.ui.LocalArea
import com.tennis.scoremanager.ui.LocalStrings
import com.tennis.scoremanager.ui.ScreenScaffold
import com.tennis.scoremanager.ui.SegOption
import com.tennis.scoremanager.ui.Segmented
import com.tennis.scoremanager.ui.TagChip
import com.tennis.scoremanager.ui.TsmColors
import com.tennis.scoremanager.ui.libLevelLabel
import com.tennis.scoremanager.ui.libTopicLabel

/**
 * La raccolta di esercizi illustrati: filtri per argomento, livello e giocatori; ogni esercizio col suo disegno del
 * campo. Toccandolo si apre la scheda (obiettivo, come si fa, varianti) e, per quelli di un colpo, "Aggiungi ai miei
 * esercizi" lo mette negli esercizi guidati, da usare con "Un colpo".
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun DrillLibraryScreen(c: MatchController) {
    val s = LocalStrings.current
    val context = LocalContext.current
    BackHandler { c.back() }
    val lang = c.options.collectAsState().value.lang
    // I testi degli esercizi ci sono per ora solo in italiano: nelle altre lingue si vedono quelli (se un giorno c'è il
    // file della lingua dell'app, si usa quello).
    val texts = remember(lang) {
        runCatching { DrillLibrary.texts(context.assets.open("drills/${lang.code}.json").bufferedReader().readText()) }
            .recoverCatching { DrillLibrary.texts(context.assets.open("drills/it.json").bufferedReader().readText()) }.getOrDefault(emptyMap())
    }
    var topic by rememberSaveable { mutableStateOf<DrillLibrary.Topic?>(null) }
    var level by rememberSaveable { mutableStateOf<DrillLibrary.Level?>(null) }
    var players by rememberSaveable { mutableStateOf<Int?>(null) }
    var open by remember { mutableStateOf<DrillLibrary.Item?>(null) }
    ScreenScaffold(
        onHelp = { c.openHelp("7.10") },
        title = s.dlTitle,
        subtitle = s.dlHint,
        icon = Icons.AutoMirrored.Filled.MenuBook,
        bottomBar = { GhostButton(s.back, Icons.AutoMirrored.Filled.ArrowBack, { c.back() }, Modifier.weight(1f)) },
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TagChip(s.cnTagAll, topic == null) { topic = null }
            for (t in DrillLibrary.Topic.entries) TagChip(libTopicLabel(s, t), topic == t) { topic = if (topic == t) null else t }
        }
        val levels = listOf<DrillLibrary.Level?>(null) + DrillLibrary.Level.entries
        Segmented(levels.map { SegOption(it?.let { l -> libLevelLabel(s, l) } ?: s.cnTagAll) }, levels.indexOf(level), { level = levels[it] }, columns = 2)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(s.dlPlayers, color = TsmColors.TextDim, fontSize = 13.sp)
            TagChip(s.cnTagAll, players == null) { players = null }
            for (n in 1..4) TagChip("$n", players == n) { players = if (players == n) null else n }
        }
        val list = DrillLibrary.filter(topic, level, players)
        Text(s.dlCount(list.size), color = TsmColors.TextDim, fontSize = 13.sp)
        if (list.isEmpty()) Text(s.dlNone, color = TsmColors.TextDim)
        for (item in list) {
            val t = texts[item.id] ?: continue
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(TsmColors.Surface).clickable { open = item }.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CourtDiagram(item.diagram, Modifier.width(64.dp).clip(RoundedCornerShape(8.dp)))
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(t.name, color = TsmColors.TextMain, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(meta(s, item), color = LocalArea.current, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(t.goal, color = TsmColors.TextDim, fontSize = 12.sp, maxLines = 2)
                }
            }
        }
        Text(s.dlOriginal, color = TsmColors.TextDim, fontSize = 11.sp)
    }
    open?.let { item -> texts[item.id]?.let { DrillDetail(c, item, it) { open = null } } }
}

/** "Intermedio · Dritto · 2 giocatori · 15 min". */
private fun meta(s: com.tennis.scoremanager.ui.Strings, item: DrillLibrary.Item): String =
    listOf(libLevelLabel(s, item.level), libTopicLabel(s, item.topic), s.dlPlayersN(item.players), "${item.minutes} min").joinToString(" · ")

/** La scheda di un esercizio: il disegno grande con la legenda, l'obiettivo, i passi, le varianti. */
@Composable
private fun DrillDetail(c: MatchController, item: DrillLibrary.Item, t: DrillLibrary.Text, onDone: () -> Unit) {
    val s = LocalStrings.current
    val context = LocalContext.current
    val area = LocalArea.current
    Dialog(onDismissRequest = onDone, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(24.dp)).background(TsmColors.Background).padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(t.name, color = TsmColors.TextMain, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text(meta(s, item), color = area, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            CourtDiagram(item.diagram, Modifier.fillMaxWidth(if (item.diagram.half) 0.85f else 0.7f).align(Alignment.CenterHorizontally).clip(RoundedCornerShape(12.dp)))
            Text(s.dlLegend, color = TsmColors.TextDim, fontSize = 12.sp)
            Section(s.dlGoal) { Text(t.goal, color = TsmColors.TextMain, fontSize = 14.sp) }
            Section(s.dlHow) {
                t.steps.forEachIndexed { i, step ->
                    Row {
                        Text("${i + 1}.", color = area, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.width(22.dp))
                        Text(step, color = TsmColors.TextMain, fontSize = 14.sp)
                    }
                }
            }
            Section(s.dlEasier) { Text(t.easier, color = TsmColors.TextMain, fontSize = 14.sp) }
            Section(s.dlHarder) { Text(t.harder, color = TsmColors.TextMain, fontSize = 14.sp) }
            if (item.physical) Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Filled.Info, null, tint = TsmColors.Orange)
                Text(s.dlSafety, color = TsmColors.TextDim, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp))
            }
            // Gli esercizi di un colpo diventano esercizi guidati e restano scelti: in Allenati › Un colpo sono già pronti,
            // col loro colpo. Se c'è già (aggiunto prima), lo si sceglie invece di farne una copia.
            item.guided?.let { (stroke, _) ->
                val drills by c.drillArea.drills.collectAsState()
                val existing = drills.firstOrNull { it.id.startsWith("lib-${item.id}-") }
                BigButton(if (existing != null) s.dlUse else s.dlAdd, Icons.Filled.Add, {
                    val d = existing ?: DrillLibrary.toDrill(item, t.name, System.currentTimeMillis())?.also { c.drillArea.saveDrill(it) }
                    if (d != null) {
                        c.trainStroke.value = stroke
                        c.drillArea.trainDrill.value = d.id
                    }
                    android.widget.Toast.makeText(context, s.dlReady(com.tennis.scoremanager.ui.strokeLabel(s, stroke)), android.widget.Toast.LENGTH_LONG).show()
                    onDone()
                }, Modifier.fillMaxWidth(), color = area, onColor = TsmColors.OnArea)
            }
            GhostButton(s.back, Icons.AutoMirrored.Filled.ArrowBack, onDone, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, color = LocalArea.current, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        content()
    }
}
