// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * Tornei di padel a coppie che cambiano a ogni turno.
 * Americano: le coppie ruotano in modo che ognuno giochi con e contro più persone possibile.
 * Mexicano: dal secondo turno si gioca per classifica, 1° e 4° contro 2° e 3° nello stesso campo.
 * Ogni partita si gioca a punti (chi vince lo scambio fa un punto; si finisce al totale, es. 24) o a tempo.
 */
@Serializable
enum class TournamentKind { AMERICANO, MEXICANO }

@Serializable
enum class TournamentScoring { POINTS, TIME }

/** Una partita di un turno: [a] e [b] sono gli indici dei giocatori delle due coppie. Punteggio null = da giocare. */
@Serializable
data class TMatch(
    val court: Int,
    val a: List<Int>,
    val b: List<Int>,
    val scoreA: Int? = null,
    val scoreB: Int? = null,
) {
    val played: Boolean get() = scoreA != null && scoreB != null
}

@Serializable
data class TRound(val number: Int, val matches: List<TMatch>, val resting: List<Int> = emptyList()) {
    val complete: Boolean get() = matches.all { it.played }
}

@Serializable
data class Tournament(
    val id: String,
    val name: String = "",
    val kind: TournamentKind = TournamentKind.AMERICANO,
    val players: List<String>,
    val courts: Int = 1,
    val scoring: TournamentScoring = TournamentScoring.POINTS,
    /** Punti totali di una partita a punti (16, 24, 32): i due punteggi sommati fanno questo numero. */
    val pointsPerMatch: Int = 24,
    /** Minuti di una partita a tempo. */
    val minutes: Int = 12,
    val rounds: List<TRound> = emptyList(),
    val createdAt: Long = 0L,
    val finished: Boolean = false,
) {
    /** Partite contemporanee in un turno: un campo ogni quattro giocatori. */
    val matchesPerRound: Int get() = minOf(courts, players.size / 4)
    val current: TRound? get() = rounds.lastOrNull()
}

object Tournaments {
    const val MIN_PLAYERS = 4
    const val MAX_PLAYERS = 16
    const val MAX_COURTS = 4

    /** Riga della classifica: [points] = punti fatti dalla sua coppia in tutte le partite giocate. */
    data class Standing(
        val player: Int,
        val name: String,
        val points: Int,
        val against: Int,
        val played: Int,
        val won: Int,
        val drawn: Int,
        val rested: Int,
    ) {
        val diff: Int get() = points - against
    }

    /** Classifica: più punti, poi più vittorie, poi miglior differenza punti; a pari tutto, il nome. */
    fun standings(t: Tournament): List<Standing> {
        val pts = IntArray(t.players.size)
        val ag = IntArray(t.players.size)
        val pl = IntArray(t.players.size)
        val won = IntArray(t.players.size)
        val drawn = IntArray(t.players.size)
        val rest = IntArray(t.players.size)
        for (r in t.rounds) {
            r.resting.forEach { rest[it]++ }
            for (m in r.matches) {
                val sa = m.scoreA ?: continue
                val sb = m.scoreB ?: continue
                for (p in m.a) {
                    pts[p] += sa; ag[p] += sb; pl[p]++
                    if (sa > sb) won[p]++ else if (sa == sb) drawn[p]++
                }
                for (p in m.b) {
                    pts[p] += sb; ag[p] += sa; pl[p]++
                    if (sb > sa) won[p]++ else if (sa == sb) drawn[p]++
                }
            }
        }
        return t.players.indices
            .map { Standing(it, t.players[it], pts[it], ag[it], pl[it], won[it], drawn[it], rest[it]) }
            .sortedWith(
                compareByDescending<Standing> { it.points }.thenByDescending { it.won }.thenByDescending { it.diff }
                    .thenBy { it.name.lowercase() },
            )
    }

    /** Il turno successivo (il primo se non ce ne sono). Va chiamato quando il turno in corso è completo. */
    fun nextRound(t: Tournament): TRound {
        val number = t.rounds.size + 1
        val resting = pickResting(t)
        val playing = t.players.indices.filter { it !in resting }
        val matches = if (t.kind == TournamentKind.MEXICANO && t.rounds.isNotEmpty()) {
            mexicano(t, playing)
        } else {
            americano(t, playing, number)
        }
        return TRound(number, matches, resting.sorted())
    }

    /**
     * Chi riposa: chi ha riposato meno finora; a parità, a rotazione dall'ultimo dell'elenco, così tutti
     * riposano prima che qualcuno riposi due volte.
     */
    private fun pickResting(t: Tournament): Set<Int> {
        val n = t.players.size
        val out = n - t.matchesPerRound * 4
        if (out <= 0) return emptySet()
        val rested = IntArray(n)
        t.rounds.forEach { r -> r.resting.forEach { rested[it]++ } }
        val shift = t.rounds.size * out
        return t.players.indices
            .sortedWith(compareBy<Int> { rested[it] }.thenBy { (n - 1 - it + shift) % n })
            .take(out).toSet()
    }

    /** Mexicano: per classifica, a gruppi di quattro dal campo 1; nel gruppo 1° e 4° contro 2° e 3°. */
    private fun mexicano(t: Tournament, playing: List<Int>): List<TMatch> {
        val order = standings(t).map { it.player }.filter { it in playing }
        return order.chunked(4).mapIndexed { i, g -> TMatch(court = i + 1, a = listOf(g[0], g[3]), b = listOf(g[1], g[2])) }
    }

    /**
     * Americano: tra molte disposizioni a caso si tiene quella con meno compagni ripetuti (pesano di più) e meno
     * avversari ripetuti. Il seme dipende dal torneo e dal turno, così lo stesso turno esce sempre uguale.
     */
    private fun americano(t: Tournament, playing: List<Int>, number: Int): List<TMatch> {
        val n = t.players.size
        val partner = Array(n) { IntArray(n) }
        val opponent = Array(n) { IntArray(n) }
        for (r in t.rounds) for (m in r.matches) {
            partner[m.a[0]][m.a[1]]++; partner[m.a[1]][m.a[0]]++
            partner[m.b[0]][m.b[1]]++; partner[m.b[1]][m.b[0]]++
            for (x in m.a) for (y in m.b) { opponent[x][y]++; opponent[y][x]++ }
        }
        val rnd = Random(t.id.hashCode() * 31 + number)
        var best: List<Int> = playing
        var bestCost = Int.MAX_VALUE
        for (attempt in 0 until 3000) {
            if (bestCost == 0) break
            val p = playing.shuffled(rnd)
            var cost = 0
            for (g in p.chunked(4)) {
                cost += 100 * (partner[g[0]][g[1]] + partner[g[2]][g[3]])
                for (x in listOf(g[0], g[1])) for (y in listOf(g[2], g[3])) cost += opponent[x][y]
            }
            if (cost < bestCost) {
                bestCost = cost
                best = p
            }
        }
        return best.chunked(4).mapIndexed { i, g -> TMatch(court = i + 1, a = listOf(g[0], g[1]), b = listOf(g[2], g[3])) }
    }

    /** Turni consigliati per l'Americano: ognuno gioca con tutti gli altri una volta (n-1 con un numero pari). */
    fun suggestedRounds(players: Int): Int = if (players % 2 == 0) players - 1 else players
}
