// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import com.tennis.scoremanager.model.MatchFormat
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Le regole delle partite di un torneo, decise dalla segreteria: vanno nella scheda partita e in campo sono già
 * impostate. Null nel torneo = si scelgono in campo.
 */
@Serializable
data class CardRules(
    val format: MatchFormat = MatchFormat.BEST_OF_THREE,
    /** 6 o 4 (set brevi); 8 o 10 nel pro set. */
    val gamesPerSet: Int = 6,
    val setTiebreak: Boolean = true,
    val noAd: Boolean = false,
)

/** Un giocatore della scheda: l'id della segreteria (così il risultato torna a lui) e come si chiama. */
@Serializable
data class CardPlayer(
    val id: String,
    val name: String,
    val firstName: String = "",
    val lastName: String = "",
    val nickname: String = "",
    val ranking: String = "",
)

/** Da dove viene la partita: un torneo del circolo (per ora l'unico; poi incontri a squadre e sfide). */
@Serializable
enum class CardKind { TOURNAMENT }

/**
 * La scheda partita: la segreteria la manda con la convocazione (un file .tsm su WhatsApp o per email) e chi tiene il
 * punteggio la apre con TSM, che prepara la partita con giocatori, regole, torneo e turno già scritti. A fine partita
 * il risultato torna alla segreteria con lo stesso [code], che lo lega a questa partita e solo a questa.
 * [issuedAt] distingue le convocazioni successive della stessa partita (vale l'ultima).
 */
@Serializable
data class MatchCard(
    val tsm: String = MatchCards.KIND,
    val v: Int = 1,
    val kind: CardKind = CardKind.TOURNAMENT,
    val code: String,
    val issuedAt: Long,
    val club: String = "",
    /** Il telefono della segreteria, a cui rimandare il risultato. */
    val office: String = "",
    val event: String,
    val category: String = "",
    val round: String = "",
    /** Il torneo (ClubTournament.id) e la partita (CTMatch.id) nel telefono della segreteria. */
    val ref: String,
    val match: String,
    val court: String = "",
    val at: Long? = null,
    val doubles: Boolean = false,
    val side1: List<CardPlayer>,
    val side2: List<CardPlayer>,
    val rules: CardRules? = null,
)

/** Una scheda partita ricevuta e, quando la partita è iniziata, la partita segnata ([record] = MatchRecord.id). */
@Serializable
data class ReceivedCard(val card: MatchCard, val record: String? = null, val receivedAt: Long = 0)

object MatchCards {
    const val KIND = "tsm-match-card"
    const val EXT = "tsm"
    const val MIME = "application/octet-stream"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    fun encode(card: MatchCard): String = json.encodeToString(MatchCard.serializer(), card)

    /** La scheda da un file; null se non è una scheda partita di TSM. */
    fun decode(text: String): MatchCard? =
        runCatching { json.decodeFromString(MatchCard.serializer(), text) }.getOrNull()?.takeIf { it.tsm == KIND }

    /** Un codice da 8 caratteri senza lettere che si confondono (0/O, 1/I/L). */
    fun newCode(): String {
        val chars = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
        val rnd = java.security.SecureRandom()
        return String(CharArray(8) { chars[rnd.nextInt(chars.length)] })
    }

    fun player(p: PlayerProfile?, id: String, fallback: String): CardPlayer =
        if (p == null) CardPlayer(id, fallback)
        else CardPlayer(p.id, p.name, p.firstName, p.lastName, p.nickname, p.member?.ranking.orEmpty())

    /**
     * La scheda di una partita del tabellone con i due iscritti; null se ne manca uno o è già giocata.
     * [find] trova il giocatore del database dal suo id.
     */
    fun forTournament(
        t: ClubTournament, m: CTMatch, code: String, now: Long, club: String, office: String, round: String,
        find: (String) -> PlayerProfile?,
    ): MatchCard? {
        if (m.played) return null
        val a = t.entries.firstOrNull { it.id == m.a } ?: return null
        val b = t.entries.firstOrNull { it.id == m.b } ?: return null
        fun side(e: CTEntry) = e.players.mapIndexed { i, id -> player(find(id), id, e.names.getOrNull(i).orEmpty()) }
        return MatchCard(
            code = code, issuedAt = now, club = club, office = office,
            event = t.name, category = t.category, round = round, ref = t.id, match = m.id,
            court = m.court, at = m.at, doubles = t.doubles, side1 = side(a), side2 = side(b), rules = t.rules,
        )
    }

    /** Il nome del file: "Semifinale Rossi - Bianchi.tsm", senza caratteri che i telefoni non accettano. */
    fun fileName(card: MatchCard): String {
        fun names(l: List<CardPlayer>) = l.joinToString(" & ") { it.lastName.ifBlank { it.name } }
        val base = listOf(card.round, "${names(card.side1)} - ${names(card.side2)}").filter { it.isNotBlank() }.joinToString(" ")
        return base.replace(Regex("[\\\\/:*?\"<>|\\n]"), "_").take(80).trim() + ".$EXT"
    }
}
