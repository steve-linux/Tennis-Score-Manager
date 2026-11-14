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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tennis.scoremanager.MatchController
import androidx.compose.material.icons.automirrored.filled.Send
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

/**
 * Manda il risultato della partita della scheda [code] alla segreteria: WhatsApp dritto al suo numero (dalla scheda),
 * o si sceglie a chi se la scheda non ha il numero. Il testo si legge anche senza aprire il file.
 */
fun sendResult(context: android.content.Context, c: MatchController, code: String) {
    val (file, text) = c.resultToSend(code) ?: return
    val office = c.receivedCards.value.firstOrNull { it.card.code == code }?.card?.office.orEmpty()
    val wa = com.tennis.scoremanager.data.Notices.waNumber(office)
    val intent = if (wa != null) whatsAppFile(context, wa, text, file) else shareFile(context, text, file)
    runCatching { context.startActivity(intent); c.resultSent(code) }
}

/** Nel riepilogo di una partita giocata da una scheda: il risultato da mandare alla segreteria. */
@Composable
fun SendResultButton(c: MatchController, recordId: String) {
    val s = LocalStrings.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val cards by c.receivedCards.collectAsState()
    val rc = cards.firstOrNull { it.record == recordId && it.result != null } ?: return
    BigButton(if (rc.sentAt == null) s.mrSend else s.mrSendAgain, androidx.compose.material.icons.Icons.AutoMirrored.Filled.Send,
        { sendResult(context, c, rc.card.code) }, Modifier.fillMaxWidth())
}

/**
 * Segreteria: un risultato arrivato dal campo. Dice di che partita è e com'è rispetto al tabellone (da registrare,
 * già registrato, diverso da quello che c'è, codice sbagliato, partita che non c'è) e, se si può, lo registra.
 */
@Composable
fun IncomingResultDialog(c: MatchController, r: com.tennis.scoremanager.data.MatchResult) {
    val s = LocalStrings.current
    val status = remember(r) { c.resultStatus(r) }
    val now = remember(r) { c.resultNow(r) }
    val winner = when (r.winner) { 1 -> r.side1; 2 -> r.side2; else -> null }
    AlertDialog(
        onDismissRequest = { c.incomingResult.value = null },
        icon = { Icon(Icons.Filled.EmojiEvents, null, tint = TsmColors.Ball) },
        title = { Text(s.mrTitle) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(listOf(r.event, r.round).filter { it.isNotBlank() }.joinToString(" · "), color = TsmColors.TextDim, fontSize = 13.sp)
                Text("${r.side1} – ${r.side2}", color = TsmColors.TextMain, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(com.tennis.scoremanager.data.MatchCards.resultText(r, s.ctWalkover, s.ctRetired, s.ctDisqualified), color = TsmColors.Ball, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                winner?.let { Text(s.mrWinner(it), color = TsmColors.TextMain, fontSize = 14.sp) }
                val (msg, warn) = when (status) {
                    com.tennis.scoremanager.data.ResultStatus.READY -> s.mrReady to false
                    com.tennis.scoremanager.data.ResultStatus.SAME -> s.mrSame to false
                    com.tennis.scoremanager.data.ResultStatus.CONFLICT -> s.mrConflict(now) to true
                    com.tennis.scoremanager.data.ResultStatus.BAD_CODE -> s.mrBadCode to true
                    com.tennis.scoremanager.data.ResultStatus.NOT_FOUND -> s.mrNotFound to true
                }
                Text(msg, color = if (warn) TsmColors.Orange else TsmColors.TextDim, fontSize = 13.sp)
            }
        },
        confirmButton = {
            when (status) {
                com.tennis.scoremanager.data.ResultStatus.READY -> TextButton(onClick = { c.applyResult(r) }) { Text(s.mrApply) }
                com.tennis.scoremanager.data.ResultStatus.CONFLICT -> TextButton(onClick = { c.applyResult(r) }) { Text(s.mrReplace, color = TsmColors.Danger) }
                else -> TextButton(onClick = { c.incomingResult.value = null }) { Text(s.ok) }
            }
        },
        dismissButton = {
            if (status == com.tennis.scoremanager.data.ResultStatus.READY || status == com.tennis.scoremanager.data.ResultStatus.CONFLICT)
                TextButton(onClick = { c.incomingResult.value = null }) { Text(s.back) }
        },
    )
}
