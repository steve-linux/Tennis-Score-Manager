// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import kotlinx.serialization.Serializable

/** Una sfida della classifica: chi sfida, chi è sfidato, entro quando giocarla, com'è finita. */
@Serializable
data class Challenge(
    val id: String,
    val challenger: String,
    val defender: String,
    val createdAt: Long = 0,
    val deadline: Long = 0,
    /** null = da giocare; true/false = ha vinto lo sfidante o lo sfidato. */
    val challengerWon: Boolean? = null,
    val score: String = "",
    val cancelled: Boolean = false,
    /** La partita segnata col segnapunti (MatchRecord.id). */
    val record: String? = null,
    val playedAt: Long? = null,
    /** I posti prima della partita (1 = primo), per la cronologia. */
    val challengerPos: Int = 0,
    val defenderPos: Int = 0,
) {
    val open: Boolean get() = challengerWon == null && !cancelled
}

/**
 * Una classifica a sfida del circolo: i giocatori in ordine ([players], il primo è il numero 1). Si sfida chi sta
 * al massimo [reach] posti più su; se vince lo sfidante prende il suo posto e chi stava in mezzo scende di uno. La
 * sfida va giocata entro [days] giorni.
 */
@Serializable
data class Ladder(
    val id: String,
    val name: String,
    val players: List<String> = emptyList(),
    val reach: Int = 3,
    val days: Int = 14,
    val challenges: List<Challenge> = emptyList(),
    val createdAt: Long = 0,
)

object Ladders {
    fun newId(prefix: String): String = prefix + java.util.UUID.randomUUID().toString().take(12)

    enum class Problem { NOT_IN, SAME, NOT_ABOVE, TOO_FAR, BUSY }

    /** Il posto in classifica (1 = primo), 0 se non c'è. */
    fun position(l: Ladder, p: String): Int = l.players.indexOf(p) + 1

    fun busy(l: Ladder, p: String): Boolean = l.challenges.any { it.open && (it.challenger == p || it.defender == p) }

    /** [a] può sfidare [b]? null = sì. Ognuno ha al più una sfida aperta per volta. */
    fun canChallenge(l: Ladder, a: String, b: String): Problem? {
        val pa = position(l, a); val pb = position(l, b)
        return when {
            pa == 0 || pb == 0 -> Problem.NOT_IN
            a == b -> Problem.SAME
            pb > pa -> Problem.NOT_ABOVE
            pa - pb > l.reach.coerceAtLeast(1) -> Problem.TOO_FAR
            busy(l, a) || busy(l, b) -> Problem.BUSY
            else -> null
        }
    }

    /** Chi [a] può sfidare adesso, dal più vicino. */
    fun targets(l: Ladder, a: String): List<String> = l.players.filter { canChallenge(l, a, it) == null }.reversed()

    fun challenge(l: Ladder, a: String, b: String, now: Long): Ladder {
        require(canChallenge(l, a, b) == null)
        val c = Challenge(newId("h"), a, b, now, now + l.days.coerceAtLeast(1) * 86_400_000L, challengerPos = position(l, a), defenderPos = position(l, b))
        return l.copy(challenges = l.challenges + c)
    }

    /**
     * Il risultato (dal lato dello sfidante, come lo scrive il segnapunti col Giocatore 1 = sfidante): chi ha vinto
     * lo dice [challengerWon] o, se manca, il punteggio. Se vince lo sfidante prende il posto dello sfidato.
     */
    fun result(l: Ladder, id: String, score: String, challengerWon: Boolean?, now: Long, record: String? = null): Ladder {
        val c = l.challenges.firstOrNull { it.id == id && it.open } ?: return l
        val won = challengerWon ?: ClubTournaments.winnerFromScore(score)?.let { it == "a" } ?: return l
        val players = if (won && position(l, c.challenger) > position(l, c.defender)) {
            val rest = l.players - c.challenger
            val at = rest.indexOf(c.defender)
            rest.subList(0, at) + c.challenger + rest.subList(at, rest.size)
        } else l.players
        val done = c.copy(challengerWon = won, score = score.trim(), playedAt = now, record = record ?: c.record)
        return l.copy(players = players, challenges = l.challenges.map { if (it.id == id) done else it })
    }

    fun cancel(l: Ladder, id: String): Ladder = l.copy(challenges = l.challenges.map { if (it.id == id && it.open) it.copy(cancelled = true) else it })

    /** I nuovi in fondo alla classifica (chi c'è già resta dov'è). */
    fun add(l: Ladder, ids: List<String>): Ladder = l.copy(players = l.players + ids.filter { it !in l.players })

    /** Tolto dalla classifica: le sue sfide aperte si annullano, gli altri salgono. */
    fun remove(l: Ladder, p: String): Ladder =
        l.copy(players = l.players - p, challenges = l.challenges.map { if (it.open && (it.challenger == p || it.defender == p)) it.copy(cancelled = true) else it })

    /** Sposta a mano (la segreteria sistema la classifica di partenza). */
    fun move(l: Ladder, p: String, up: Boolean): Ladder {
        val i = l.players.indexOf(p); val j = if (up) i - 1 else i + 1
        if (i < 0 || j !in l.players.indices) return l
        val list = l.players.toMutableList(); list[i] = list[j].also { list[j] = list[i] }
        return l.copy(players = list)
    }
}
