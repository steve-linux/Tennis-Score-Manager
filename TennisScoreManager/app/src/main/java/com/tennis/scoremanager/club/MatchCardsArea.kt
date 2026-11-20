// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later
package com.tennis.scoremanager.club

import android.app.Application
import com.tennis.scoremanager.Screen
import com.tennis.scoremanager.data.MatchRecord
import com.tennis.scoremanager.data.SetupData
import com.tennis.scoremanager.data.Storage
import com.tennis.scoremanager.model.Side
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * La scheda partita tra segreteria e giocatori, senza server (o col server del circolo, se c'è): la scheda ricevuta
 * prepara Nuova partita, a fine partita il risultato è pronto da rimandare, e alla segreteria il risultato arrivato
 * si registra nel tabellone, nell'incontro o nella classifica. Quello che serve del resto dell'app è in [ClubHost].
 */
class MatchCardsArea(
    host: ClubHost,
    private val app: Application,
    private val storage: Storage,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
) : ClubHost by host {
    /** La scheda appena aperta da WhatsApp o dai file: la finestra chiede se preparare la partita. */
    val incomingCard = MutableStateFlow<com.tennis.scoremanager.data.MatchCard?>(null)
    val receivedCards = MutableStateFlow(storage.loadReceivedCards())
    internal var pendingCard: com.tennis.scoremanager.data.MatchCard? = null
    /** Il Giocatore 1 com'era scritto da [prepareCard]: se in campo c'è un altro, la partita non è quella della scheda. */
    internal var pendingCardP1: Pair<String, String> = "" to ""

    /** Un risultato arrivato dal campo (segreteria): la finestra dice com'è e chiede se registrarlo. */
    val incomingResult = MutableStateFlow<com.tennis.scoremanager.data.MatchResult?>(null)

    fun resultStatus(r: com.tennis.scoremanager.data.MatchResult): com.tennis.scoremanager.data.ResultStatus =
        com.tennis.scoremanager.data.MatchCards.check(r, competitions.clubTournaments.value, competitions.teams.value, competitions.ladders.value)

    /** Com'è segnata adesso la partita del risultato (per dire cosa si sostituisce). */
    fun resultNow(r: com.tennis.scoremanager.data.MatchResult): String = when (r.kind) {
        com.tennis.scoremanager.data.CardKind.TOURNAMENT -> competitions.clubTournament(r.ref)?.matches?.firstOrNull { it.id == r.match }?.let { m ->
            listOf(entryNameOf(competitions.clubTournament(r.ref)!!, m.winner), m.score).filter { it.isNotBlank() }.joinToString(" · ")
        }.orEmpty()
        com.tennis.scoremanager.data.CardKind.TEAM -> competitions.team(r.ref)?.fixtures?.firstOrNull { it.id == r.match }?.rubbers?.getOrNull(r.index)?.score.orEmpty()
        com.tennis.scoremanager.data.CardKind.LADDER -> competitions.ladder(r.ref)?.challenges?.firstOrNull { it.id == r.match }?.score.orEmpty()
    }

    /** Registra il risultato arrivato dal campo nel tabellone, nell'incontro o nella classifica. */
    fun applyResult(r: com.tennis.scoremanager.data.MatchResult) {
        incomingResult.value = null
        val status = resultStatus(r)
        if (status != com.tennis.scoremanager.data.ResultStatus.READY && status != com.tennis.scoremanager.data.ResultStatus.CONFLICT) return
        val s = strings
        when (r.kind) {
            com.tennis.scoremanager.data.CardKind.TOURNAMENT -> {
                val t = competitions.clubTournament(r.ref) ?: return
                val m = t.matches.firstOrNull { it.id == r.match } ?: return
                val w = when (r.winner) { 1 -> m.a; 2 -> m.b; else -> null }
                competitions.saveClubTournament(com.tennis.scoremanager.data.ClubTournaments.setResult(t, m.id, w, r.score, walkover = r.walkover, retired = r.retired, disqualified = r.disqualified))
            }
            com.tennis.scoremanager.data.CardKind.TEAM -> {
                val t = competitions.team(r.ref) ?: return
                val f = t.fixtures.firstOrNull { it.id == r.match } ?: return
                val won = when (r.winner) { 1 -> true; 2 -> false; else -> null }
                competitions.saveFixture(t, com.tennis.scoremanager.data.Teams.setRubber(f, r.index, com.tennis.scoremanager.data.MatchCards.resultText(r, s.ctWalkover, s.ctRetired, s.ctDisqualified), won, walkover = r.walkover))
            }
            com.tennis.scoremanager.data.CardKind.LADDER -> {
                val l = competitions.ladder(r.ref) ?: return
                val won = when (r.winner) { 1 -> true; 2 -> false; else -> return }
                competitions.saveLadder(com.tennis.scoremanager.data.Ladders.result(l, r.match, com.tennis.scoremanager.data.MatchCards.resultText(r, s.ctWalkover, s.ctRetired, s.ctDisqualified), won, System.currentTimeMillis()))
            }
        }
        toast(s.mrApplied)
    }

    /** A fine partita, se era quella di una scheda ricevuta: il risultato da rimandare alla segreteria è pronto. */
    internal fun cardMatchFinished(rec: MatchRecord, state: com.tennis.scoremanager.model.MatchState) {
        val rc = receivedCards.value.firstOrNull { it.record == rec.id } ?: return
        val winner = when (state.winner) { Side.P1 -> 1; Side.P2 -> 2; else -> null }
        val wo = com.tennis.scoremanager.data.Reports.isWalkover(state)
        val result = com.tennis.scoremanager.data.MatchCards.result(
            rc.card, winner, com.tennis.scoremanager.data.Reports.scoreLine(state, Side.P1),
            walkover = wo, retired = state.retired != null && !state.disqualified, disqualified = state.disqualified, now = System.currentTimeMillis(),
        )
        val list = receivedCards.value.map { if (it.card.code == rc.card.code) it.copy(result = result, sentAt = null) else it }
        receivedCards.value = list
        scope.launch(io) { storage.saveReceivedCards(list) }
        // Arrivata dal server del circolo: il risultato torna da lì, da solo (se non c'è rete, resta il pulsante).
        rc.via?.let { url -> scope.launch { if (postToOffice(url, "result", com.tennis.scoremanager.data.MatchCards.encodeResult(result))) resultSent(rc.card.code) } }
    }

    /** Il file del risultato da mandare e il testo che lo accompagna (si legge anche senza aprire il file). */
    fun resultToSend(code: String): Pair<File, String>? {
        val rc = receivedCards.value.firstOrNull { it.card.code == code } ?: return null
        val r = rc.result ?: return null
        val s = strings
        val dir = File(app.cacheDir, "share/cards").apply { mkdirs() }
        val f = runCatching { File(dir, com.tennis.scoremanager.data.MatchCards.resultFileName(s.mrFilePrefix, r)).apply { writeText(com.tennis.scoremanager.data.MatchCards.encodeResult(r)) } }.getOrNull() ?: return null
        val winner = when (r.winner) { 1 -> r.side1; 2 -> r.side2; else -> null }
        val text = listOfNotNull(
            s.mrTitle + ": " + listOf(r.event, r.round).filter { it.isNotBlank() }.joinToString(" · "),
            "${r.side1} – ${r.side2}",
            com.tennis.scoremanager.data.MatchCards.resultText(r, s.ctWalkover, s.ctRetired, s.ctDisqualified),
            winner?.let { s.mrWinner(it) },
        ).joinToString("\n")
        return f to text
    }

    /**
     * Manda il risultato della scheda [code]: arrivata dal server del circolo, da lì (senza WhatsApp); sennò, o se il
     * server non risponde, [fallback] (WhatsApp o condivisione).
     */
    fun sendResultAuto(code: String, fallback: () -> Unit) {
        val rc = receivedCards.value.firstOrNull { it.card.code == code } ?: return
        val r = rc.result ?: return
        val url = rc.via ?: return fallback()
        scope.launch {
            if (postToOffice(url, "result", com.tennis.scoremanager.data.MatchCards.encodeResult(r))) { resultSent(code); toast(strings.ibSentServer) }
            else fallback()
        }
    }

    /** Il risultato è stato mandato (la voce in Gioca sparisce). */
    fun resultSent(code: String) {
        val list = receivedCards.value.map { if (it.card.code == code) it.copy(sentAt = System.currentTimeMillis()) else it }
        receivedCards.value = list
        scope.launch(io) { storage.saveReceivedCards(list) }
    }

    /**
     * Prepara la partita della scheda: Nuova partita con giocatori, classifiche, torneo, turno, campo e regole. Sul
     * telefono della segreteria (il torneo c'è e il codice è quello) è la partita del tabellone, come da Partite del
     * circolo. Il giocatore del database si usa se c'è già (stesso id), sennò lo trova o lo crea Nuova partita dal nome.
     */
    fun prepareCard(card: com.tennis.scoremanager.data.MatchCard) {
        incomingCard.value = null
        // Sul telefono della segreteria (c'è la partita col codice di questa scheda) si gioca quella, come da Partite del circolo.
        when (card.kind) {
            com.tennis.scoremanager.data.CardKind.TOURNAMENT -> {
                val t = competitions.clubTournament(card.ref)
                val m = t?.matches?.firstOrNull { it.id == card.match && it.cardCode == card.code }
                if (t != null && m != null) { if (!m.played) competitions.playClubMatch(t, m); return }
            }
            com.tennis.scoremanager.data.CardKind.TEAM -> {
                val tm = competitions.team(card.ref)
                val f = tm?.fixtures?.firstOrNull { it.id == card.match }
                if (tm != null && f != null && f.rubbers.getOrNull(card.index)?.cardCode == card.code) {
                    if (f.rubbers[card.index].won == null) competitions.playTeamRubber(tm, f, card.index); return
                }
            }
            com.tennis.scoremanager.data.CardKind.LADDER -> {
                val l = competitions.ladder(card.ref)
                val ch = l?.challenges?.firstOrNull { it.id == card.match && it.cardCode == card.code }
                if (l != null && ch != null) { if (ch.open) competitions.playChallenge(l, ch); return }
            }
        }
        // Ogni giocatore della scheda nel database di questo telefono: lo stesso id, o lo stesso nome e cognome, o nuovo
        // (gli avversari di un incontro a squadre arrivano solo col nome: Nuova partita li trova o li crea dal nome).
        val now0 = System.currentTimeMillis()
        val local = (card.side1 + card.side2).associateWith { cp ->
            if (cp.id.isBlank()) null
            else playerById(cp.id)
                ?: com.tennis.scoremanager.data.Players.sameFullName(players.value, cp.firstName, cp.lastName).singleOrNull()?.takeIf { cp.firstName.isNotBlank() && cp.lastName.isNotBlank() }
                ?: com.tennis.scoremanager.data.PlayerProfile(cp.id, cp.name, cp.firstName, cp.lastName, cp.nickname, createdAt = now0).also { savePlayer(it) }
        }
        fun id(p: com.tennis.scoremanager.data.CardPlayer?) = p?.let { local[it]?.id }.orEmpty()
        fun name(p: com.tennis.scoremanager.data.CardPlayer?) = p?.let { local[it]?.name ?: it.name }.orEmpty()
        val (a1, a2) = card.side1.getOrNull(0) to card.side1.getOrNull(1)
        val (b1, b2) = card.side2.getOrNull(0) to card.side2.getOrNull(1)
        updateSetup {
            it.copy(
                club = card.club.ifBlank { it.club }, court = card.court.ifBlank { it.court }, doubles = card.doubles,
                p1a = name(a1), p1b = if (card.doubles) name(a2) else "", p2a = name(b1), p2b = if (card.doubles) name(b2) else "",
                p1aId = id(a1), p1bId = if (card.doubles) id(a2) else "", p2aId = id(b1), p2bId = if (card.doubles) id(b2) else "",
                p1aRank = a1?.ranking.orEmpty(), p1bRank = if (card.doubles) a2?.ranking.orEmpty() else "",
                p2aRank = b1?.ranking.orEmpty(), p2bRank = if (card.doubles) b2?.ranking.orEmpty() else "",
                event = card.event, category = card.category, round = card.round,
            )
        }
        card.rules?.let { r -> updateOptions { it.copy(format = r.format, gamesPerSet = r.gamesPerSet, setTiebreak = r.setTiebreak, noAd = r.noAd) } }
        // La scheda resta tra le ricevute (l'ultima convocazione della partita sostituisce le altre).
        val now = System.currentTimeMillis()
        val list = receivedCards.value.filter { it.card.code != card.code } +
            com.tennis.scoremanager.data.ReceivedCard(card, receivedCards.value.firstOrNull { it.card.code == card.code }?.record, now, via = incomingVia)
        receivedCards.value = list
        scope.launch(io) { storage.saveReceivedCards(list) }
        pendingCard = card
        pendingCardP1 = name(a1) to id(a1)
        go(Screen.SETUP)
    }

    /** La partita appena iniziata è quella della scheda (se in campo ci sono ancora quei giocatori). */
    internal fun linkCard(recordId: String, su: SetupData) {
        val card = pendingCard ?: return
        pendingCard = null
        val (n, id) = pendingCardP1
        if (su.p1a != n && (id.isBlank() || su.p1aId != id)) return
        val list = receivedCards.value.map { if (it.card.code == card.code) it.copy(record = recordId) else it }
        receivedCards.value = list
        scope.launch(io) { storage.saveReceivedCards(list) }
    }
}
