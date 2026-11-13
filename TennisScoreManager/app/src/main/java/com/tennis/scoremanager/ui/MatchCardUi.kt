// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tennis.scoremanager.MatchController
import com.tennis.scoremanager.Screen
import com.tennis.scoremanager.data.CardRules
import com.tennis.scoremanager.data.MatchCard
import com.tennis.scoremanager.data.Reports
import com.tennis.scoremanager.model.MatchFormat

/** Le regole della scheda in una riga: "2 set + super tie-break a 10 · 6 game · No-Ad". */
fun cardRulesLine(s: Strings, r: CardRules?): String {
    if (r == null) return s.ctRulesFree
    val format = when (r.format) {
        MatchFormat.BEST_OF_THREE -> s.formatBestOfThree
        MatchFormat.TWO_SETS_MATCH_TIEBREAK -> s.formatMatchTiebreak
        MatchFormat.BEST_OF_FIVE -> s.formatBestOfFive
        MatchFormat.PRO_SET -> s.formatProSet
        MatchFormat.MATCH_TIEBREAK_ONLY -> s.formatMatchTiebreakOnly
    }
    return listOfNotNull(format, s.gamesN(r.gamesPerSet).takeIf { r.format != MatchFormat.MATCH_TIEBREAK_ONLY }, s.noAd.takeIf { r.noAd }).joinToString(" · ")
}

/**
 * La scheda partita appena aperta: torneo, turno, chi gioca, dove e quando, le regole. "Prepara la partita" apre
 * Nuova partita già scritta; durante una partita non si può (si riapre il file dopo).
 */
@Composable
fun IncomingCardDialog(c: MatchController, card: MatchCard) {
    val s = LocalStrings.current
    val lang = c.options.collectAsState().value.lang
    val busy = c.screen.collectAsState().value.let { it == Screen.MATCH || it == Screen.START }
    AlertDialog(
        onDismissRequest = { c.incomingCard.value = null },
        icon = { Icon(Icons.Filled.EmojiEvents, null, tint = TsmColors.Ball) },
        title = { Text(s.mcTitle) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(listOf(card.event, card.category, card.round).filter { it.isNotBlank() }.joinToString(" · "), color = TsmColors.TextDim, fontSize = 13.sp)
                Text(card.side1.joinToString(" / ") { it.name } + " – " + card.side2.joinToString(" / ") { it.name },
                    color = TsmColors.TextMain, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                val where = listOfNotNull(
                    card.at?.let { "${Reports.date(it, lang)} ${Reports.time(it, lang)}" },
                    card.court.ifBlank { null }, card.club.ifBlank { null },
                ).joinToString(" · ")
                if (where.isNotBlank()) Text(where, color = TsmColors.TextMain, fontSize = 14.sp)
                Text(cardRulesLine(s, card.rules), color = TsmColors.Ball, fontSize = 13.sp)
                Text(if (busy) s.mcBusy else s.mcHint, color = if (busy) TsmColors.Orange else TsmColors.TextDim, fontSize = 13.sp)
            }
        },
        confirmButton = { if (!busy) TextButton(onClick = { c.prepareCard(card) }) { Text(s.mcPrepare) } },
        dismissButton = { TextButton(onClick = { c.incomingCard.value = null }) { Text(s.back) } },
    )
}
