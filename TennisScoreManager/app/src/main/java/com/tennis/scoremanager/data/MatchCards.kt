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

/** Da dove viene la partita: un torneo del circolo, un incontro a squadre o una sfida della classifica. */
@Serializable
enum class CardKind { TOURNAMENT, TEAM, LADDER }

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
    /**
     * Nel telefono della segreteria: torneo e partita (ClubTournament.id, CTMatch.id), squadra e incontro (Team.id,
     * Fixture.id, con [index] la partita dell'incontro) o classifica e sfida (Ladder.id, Challenge.id).
     */
    val ref: String,
    val match: String,
    val index: Int = 0,
    val court: String = "",
    val at: Long? = null,
    val doubles: Boolean = false,
    val side1: List<CardPlayer>,
    val side2: List<CardPlayer>,
    val rules: CardRules? = null,
)

/**
 * Il risultato che torna alla segreteria: la stessa partita della scheda ([kind], [ref], [match], [index], [code]),
 * chi ha vinto (1 = il primo lato della scheda, 2 = il secondo, null = nessuno), il punteggio dal primo lato e come è
 * finita. I nomi servono solo a chi lo legge.
 */
@Serializable
data class MatchResult(
    val tsm: String = MatchCards.RESULT,
    val v: Int = 1,
    val kind: CardKind = CardKind.TOURNAMENT,
    val code: String,
    val ref: String,
    val match: String,
    val index: Int = 0,
    val event: String = "",
    val round: String = "",
    val side1: String = "",
    val side2: String = "",
    val winner: Int? = null,
    val score: String = "",
    val walkover: Boolean = false,
    val retired: Boolean = false,
    val disqualified: Boolean = false,
    val playedAt: Long = 0,
)

/**
 * Una scheda partita ricevuta: quando la partita è iniziata la partita segnata ([record] = MatchRecord.id), a fine
 * partita il risultato da rimandare e quando è stato mandato.
 */
@Serializable
data class ReceivedCard(
    val card: MatchCard,
    val record: String? = null,
    val receivedAt: Long = 0,
    val result: MatchResult? = null,
    val sentAt: Long? = null,
)

/** Com'è un risultato arrivato rispetto a quello che c'è nel telefono della segreteria. */
enum class ResultStatus { READY, SAME, CONFLICT, BAD_CODE, NOT_FOUND }

object MatchCards {
    const val KIND = "tsm-match-card"
    const val RESULT = "tsm-match-result"
    const val EXT = "tsm"
    const val MIME = "application/octet-stream"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    fun encode(card: MatchCard): String = json.encodeToString(MatchCard.serializer(), card)

    /** La scheda da un file; null se non è una scheda partita di TSM. */
    fun decode(text: String): MatchCard? =
        runCatching { json.decodeFromString(MatchCard.serializer(), text) }.getOrNull()?.takeIf { it.tsm == KIND }

    fun encodeResult(r: MatchResult): String = json.encodeToString(MatchResult.serializer(), r)

    /** Il risultato da un file; null se non è un risultato di TSM. */
    fun decodeResult(text: String): MatchResult? =
        runCatching { json.decodeFromString(MatchResult.serializer(), text) }.getOrNull()?.takeIf { it.tsm == RESULT }

    /** Il risultato della partita della scheda: [winner] 1 o 2 (il lato della scheda), il punteggio dal primo lato. */
    fun result(card: MatchCard, winner: Int?, score: String, walkover: Boolean, retired: Boolean, disqualified: Boolean, now: Long): MatchResult =
        MatchResult(
            kind = card.kind, code = card.code, ref = card.ref, match = card.match, index = card.index,
            event = card.event, round = card.round,
            side1 = card.side1.joinToString(" / ") { it.name }, side2 = card.side2.joinToString(" / ") { it.name },
            winner = winner, score = score, walkover = walkover, retired = retired && !walkover, disqualified = disqualified && !walkover, playedAt = now,
        )

    /** Il nome del file del risultato: "Risultato Finale Rossi - Bianchi.tsm" ([prefix] nella lingua dell'app). */
    fun resultFileName(prefix: String, r: MatchResult): String {
        fun last(n: String) = n.split(" / ").joinToString(" & ") { it.trim().substringAfterLast(' ') }
        val base = listOf(prefix, r.round, "${last(r.side1)} - ${last(r.side2)}").filter { it.isNotBlank() }.joinToString(" ")
        return base.replace(Regex("[\\\\/:*?\"<>|\\n]"), "_").take(80).trim() + ".$EXT"
    }

    /**
     * Il risultato [r] rispetto ai dati della segreteria: la partita c'è e il codice è quello della scheda mandata?
     * È già segnata (con lo stesso esito: [ResultStatus.SAME], con un altro: [ResultStatus.CONFLICT])?
     */
    fun check(r: MatchResult, tournaments: List<ClubTournament>, teams: List<Team>, ladders: List<Ladder>): ResultStatus = when (r.kind) {
        CardKind.TOURNAMENT -> {
            val m = tournaments.firstOrNull { it.id == r.ref }?.matches?.firstOrNull { it.id == r.match }
            when {
                m == null -> ResultStatus.NOT_FOUND
                m.cardCode != r.code -> ResultStatus.BAD_CODE
                !m.played -> ResultStatus.READY
                m.winner == (if (r.winner == 1) m.a else if (r.winner == 2) m.b else null) && (m.score == r.score || m.walkover) -> ResultStatus.SAME
                else -> ResultStatus.CONFLICT
            }
        }
        CardKind.TEAM -> {
            val rb = teams.firstOrNull { it.id == r.ref }?.fixtures?.firstOrNull { it.id == r.match }?.rubbers?.getOrNull(r.index)
            when {
                rb == null -> ResultStatus.NOT_FOUND
                rb.cardCode != r.code -> ResultStatus.BAD_CODE
                rb.won == null -> ResultStatus.READY
                rb.won == (r.winner == 1) -> ResultStatus.SAME
                else -> ResultStatus.CONFLICT
            }
        }
        CardKind.LADDER -> {
            val ch = ladders.firstOrNull { it.id == r.ref }?.challenges?.firstOrNull { it.id == r.match }
            when {
                ch == null -> ResultStatus.NOT_FOUND
                ch.cardCode != r.code -> ResultStatus.BAD_CODE
                ch.cancelled -> ResultStatus.NOT_FOUND
                ch.open -> ResultStatus.READY
                ch.challengerWon == (r.winner == 1) -> ResultStatus.SAME
                else -> ResultStatus.CONFLICT
            }
        }
    }

    /** Il punteggio da leggere nella lingua di chi lo registra: col ritiro "6-3  2-1 rit.", senza giocare "w.o.". */
    fun resultText(r: MatchResult, walkoverText: String, retiredText: String, disqualifiedText: String): String = when {
        r.walkover -> walkoverText
        r.disqualified -> "${r.score} $disqualifiedText".trim()
        r.retired -> "${r.score} $retiredText".trim()
        else -> r.score
    }

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

    /** La scheda di una partita di un incontro a squadre, con la formazione fatta e non ancora giocata. */
    fun forRubber(
        t: Team, f: Fixture, index: Int, code: String, now: Long, club: String, office: String, round: String,
        find: (String) -> PlayerProfile?,
    ): MatchCard? {
        val r = f.rubbers.getOrNull(index) ?: return null
        val need = if (r.doubles) 2 else 1
        if (r.ours.size != need || r.won != null) return null
        val theirs = r.theirs.filter { it.isNotBlank() }.ifEmpty { listOf(f.opponent) }
        return MatchCard(
            kind = CardKind.TEAM, code = code, issuedAt = now, club = club, office = office,
            event = t.championship.ifBlank { t.name }, category = "${t.name} – ${f.opponent}", round = round,
            ref = t.id, match = f.id, index = index, court = f.place, at = f.at, doubles = r.doubles,
            side1 = r.ours.map { player(find(it), it, it) }, side2 = theirs.map { CardPlayer("", it) },
        )
    }

    /** La scheda di una sfida della classifica ancora da giocare: lo sfidante è il primo lato. */
    fun forChallenge(
        l: Ladder, ch: Challenge, code: String, now: Long, club: String, office: String, round: String,
        find: (String) -> PlayerProfile?,
    ): MatchCard? {
        if (!ch.open) return null
        return MatchCard(
            kind = CardKind.LADDER, code = code, issuedAt = now, club = club, office = office,
            event = l.name, round = round, ref = l.id, match = ch.id,
            side1 = listOf(player(find(ch.challenger), ch.challenger, "")), side2 = listOf(player(find(ch.defender), ch.defender, "")),
        )
    }

    /** Il nome del file: "Semifinale Rossi - Bianchi.tsm", senza caratteri che i telefoni non accettano. */
    fun fileName(card: MatchCard): String {
        fun names(l: List<CardPlayer>) = l.joinToString(" & ") { it.lastName.ifBlank { it.name } }
        val base = listOf(card.round, "${names(card.side1)} - ${names(card.side2)}").filter { it.isNotBlank() }.joinToString(" ")
        return base.replace(Regex("[\\\\/:*?\"<>|\\n]"), "_").take(80).trim() + ".$EXT"
    }
}
