// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tennis.scoremanager.LiveMatch
import com.tennis.scoremanager.MatchController
import com.tennis.scoremanager.data.PlayerStats
import com.tennis.scoremanager.data.Rating
import com.tennis.scoremanager.data.Sport
import com.tennis.scoremanager.model.Side

/** Variazione col segno: "+12", "−8", "0". */
fun ratingDelta(d: Int): String = when {
    d > 0 -> "+$d"
    d < 0 -> "−${-d}"
    else -> "0"
}

/**
 * Progressi › Partite: rating TSM del giocatore scelto (con l'andamento) e la classifica di tutti. Con partite di
 * tennis e di padel si sceglie lo sport: le due classifiche sono separate.
 */
@Composable
fun RatingSection(c: MatchController, key: String?) {
    val s = LocalStrings.current
    val history by c.history.collectAsState()
    val sports = remember(history) { c.ratingSports() }
    var chosen by rememberSaveable { mutableStateOf<Sport?>(null) }
    val sport = chosen?.takeIf { it in sports } ?: sports.firstOrNull()
    SectionCard(s.ratingTitle, Icons.Filled.EmojiEvents) {
        if (sport == null) {
            Text(s.ratingNone, color = TsmColors.TextDim)
            return@SectionCard
        }
        if (sports.size > 1) {
            Segmented(sports.map { SegOption(if (it == Sport.PADEL) s.sportPadel else s.sportTennis) }, sports.indexOf(sport), { chosen = sports[it] })
        }
        val table = remember(history, sport) { c.ratingTable(sport) }
        val me = key?.let { table.player(it) }
        if (me != null) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${me.rating}", color = TsmColors.Ball, fontWeight = FontWeight.Bold, fontSize = 40.sp)
                Column {
                    Text(s.ratingMatches(me.matches, me.wins), color = TsmColors.TextMain, fontSize = 14.sp)
                    me.history.size.takeIf { it >= 2 }?.let {
                        val last = me.history[it - 1].second - me.history[it - 2].second
                        Text(s.ratingLast(ratingDelta(last)), color = if (last >= 0) TsmColors.Ok else TsmColors.Orange, fontSize = 13.sp)
                    }
                    if (me.provisional) Text(s.ratingProvisional(Rating.PROVISIONAL - me.matches), color = TsmColors.TextDim, fontSize = 12.sp)
                }
            }
            // Da 1500, il punto di partenza di tutti, a ogni partita.
            RatingTrend(listOf(Rating.START) + me.history.map { it.second })
        }
        Text(s.ratingHint, color = TsmColors.TextDim, fontSize = 12.sp)
        Ranking(table, key)
    }
}

/** Andamento del rating partita dopo partita, con la linea di partenza tratteggiata in chiaro. */
@Composable
private fun RatingTrend(values: List<Int>) {
    val all = values + Rating.START
    val lo = all.min() - 10
    val hi = all.max() + 10
    Canvas(Modifier.fillMaxWidth().height(64.dp)) {
        fun y(v: Int) = size.height * (1f - (v - lo).toFloat() / (hi - lo))
        drawLine(TsmColors.Outline, Offset(0f, y(Rating.START)), Offset(size.width, y(Rating.START)), strokeWidth = 1.5f)
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = if (values.size == 1) 0f else size.width * i / (values.size - 1)
            if (i == 0) path.moveTo(x, y(v)) else path.lineTo(x, y(v))
        }
        drawPath(path, TsmColors.Ball, style = Stroke(width = 2.5f * density))
    }
}

/** Classifica TSM: i primi dieci, tutti a richiesta; il giocatore scelto in evidenza. */
@Composable
private fun Ranking(table: Rating.Table, key: String?) {
    val s = LocalStrings.current
    var all by rememberSaveable { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.Icon(Icons.Filled.Leaderboard, null, tint = TsmColors.TextMain)
        Text("  " + s.ratingRanking, color = TsmColors.TextMain, fontWeight = FontWeight.Bold)
    }
    val shown = if (all) table.players else table.players.take(10)
    shown.forEachIndexed { i, p ->
        val mine = p.key == key
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("${i + 1}.", color = TsmColors.TextDim, fontSize = 14.sp, modifier = Modifier.width(32.dp))
            Text(
                p.name + if (p.provisional) " *" else "", color = if (mine) TsmColors.Ball else TsmColors.TextMain,
                fontWeight = if (mine) FontWeight.Bold else FontWeight.Normal, fontSize = 14.sp, modifier = Modifier.weight(1f),
            )
            Text("${p.rating}", color = if (mine) TsmColors.Ball else TsmColors.TextMain, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
    if (table.players.any { it.provisional }) Text("* " + s.ratingProvisional(Rating.PROVISIONAL).substringBefore(':'), color = TsmColors.TextDim, fontSize = 12.sp)
    if (table.players.size > 10) TextButton(onClick = { all = !all }) { Text(if (all) s.ratingShowLess else s.ratingShowAll) }
}

/**
 * Riepilogo: amichevole sì o no e, se conta, il rating di ciascun giocatore dopo la partita con la variazione.
 */
@Composable
fun SummaryRatingCard(c: MatchController, m: LiveMatch) {
    val s = LocalStrings.current
    val rec = m.record
    if (!rec.finished) return
    val history by c.history.collectAsState()
    SectionCard(s.ratingTitle, Icons.Filled.EmojiEvents) {
        SwitchRow(Icons.Filled.Handshake, s.ratingFriendly, s.ratingFriendlyHint, rec.friendly) { c.setFriendly(it) }
        val table = remember(history, rec) { c.ratingTableWith(m) }
        val deltas = table.deltas[rec.id]
        if (rec.friendly || deltas == null) {
            Text(s.ratingNotCounted, color = TsmColors.TextDim, fontSize = 13.sp)
            return@SectionCard
        }
        Text(s.ratingInMatch, color = TsmColors.TextDim, fontSize = 13.sp)
        for (side in Side.entries) {
            for (name in PlayerStats.names(rec.setup, side)) {
                val k = PlayerStats.key(name)
                val d = deltas[k] ?: continue
                // Il rating subito dopo questa partita (quelle giocate dopo l'hanno già cambiato).
                val after = table.player(k)?.history?.firstOrNull { it.first == (rec.endedAt ?: rec.startedAt ?: rec.updatedAt) }?.second
                Row(Modifier.fillMaxWidth()) {
                    Text(name, color = TsmColors.player(side), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    after?.let { Text("$it  ", color = TsmColors.TextMain, fontWeight = FontWeight.Bold) }
                    Text(ratingDelta(d), color = if (d >= 0) TsmColors.Ok else TsmColors.Orange, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
