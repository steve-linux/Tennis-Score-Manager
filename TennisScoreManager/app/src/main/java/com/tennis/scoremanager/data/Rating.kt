// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import com.tennis.scoremanager.model.Side
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Rating TSM: un punteggio Elo calcolato sul telefono dalle partite finite dello storico, separato per sport (tennis e
 * padel non si mescolano). Si parte da [START]; dopo ogni partita ciascun giocatore guadagna o perde secondo quanto il
 * risultato è stato migliore o peggiore del previsto.
 *
 * - Previsto: dalla differenza di rating ([expected]); nel doppio vale la media della coppia.
 * - Risultato: 75 % vittoria o sconfitta, 25 % quota di game vinti: 6-0 6-0 conta più di 7-6 7-6, ma vincere conta
 *   sempre più che perdere bene.
 * - Peso ([kFactor]): alto nelle prime partite, perché il rating trovi presto il suo posto, poi più stabile.
 * - Non contano le partite non finite, quelle segnate come amichevoli e i lati senza nomi scritti.
 *
 * I giocatori sono riconosciuti dal nome, come nello storico ([PlayerStats.key]).
 */
object Rating {
    const val START = 1500
    /** Sotto queste partite il rating è ancora "provvisorio". */
    const val PROVISIONAL = 5
    private const val WIN_WEIGHT = 0.75

    /** Una partita per il rating: nomi dei due lati, vincitore, game vinti da ciascuno. */
    data class Game(
        val id: String,
        val at: Long,
        val sport: Sport,
        val sides: Map<Side, List<String>>,
        val winner: Side,
        val games: Map<Side, Int>,
    )

    /** Rating di un giocatore: valore, partite contate, e il valore dopo ciascuna (per l'andamento). */
    data class Player(val key: String, val name: String, val rating: Int, val matches: Int, val wins: Int, val history: List<Pair<Long, Int>>) {
        val provisional: Boolean get() = matches < PROVISIONAL
    }

    /** Risultato del calcolo: giocatori di uno sport, dal più alto, e la variazione di ciascuno in ogni partita. */
    data class Table(val players: List<Player>, val deltas: Map<String, Map<String, Int>>) {
        fun player(key: String): Player? = players.firstOrNull { it.key == key }
        /** Variazione del giocatore [key] nella partita [id] (null se non contava). */
        fun delta(id: String, key: String): Int? = deltas[id]?.get(key)
    }

    fun expected(mine: Double, theirs: Double): Double = 1.0 / (1.0 + 10.0.pow((theirs - mine) / 400.0))

    fun kFactor(matchesBefore: Int): Double = when {
        matchesBefore < 10 -> 40.0
        matchesBefore < 30 -> 24.0
        else -> 16.0
    }

    /** Risultato tra 0 e 1 per il lato [side]: vittoria e quota di game. */
    fun score(g: Game, side: Side): Double {
        val mine = g.games[side] ?: 0
        val theirs = g.games[side.other] ?: 0
        val share = if (mine + theirs > 0) mine.toDouble() / (mine + theirs) else if (g.winner == side) 1.0 else 0.0
        return WIN_WEIGHT * (if (g.winner == side) 1.0 else 0.0) + (1 - WIN_WEIGHT) * share
    }

    /** Calcola i rating di [sport] dalle partite [games], in ordine di tempo. */
    fun compute(games: List<Game>, sport: Sport): Table {
        val rating = HashMap<String, Double>()
        val count = HashMap<String, Int>()
        val wins = HashMap<String, Int>()
        val names = HashMap<String, String>()
        val history = HashMap<String, MutableList<Pair<Long, Int>>>()
        val deltas = LinkedHashMap<String, Map<String, Int>>()
        for (g in games.filter { it.sport == sport }.sortedBy { it.at }) {
            // Nome scritto e chiave di ciascun giocatore, lato per lato.
            val named = Side.entries.associateWith { side ->
                g.sides[side].orEmpty().map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { PlayerStats.key(it) }
            }
            val keys = named.mapValues { (_, ns) -> ns.map { PlayerStats.key(it) } }
            if (keys.values.any { it.isEmpty() }) continue
            // Un nome su tutti e due i lati (errore di battitura): la partita non dice niente, si salta.
            if (keys.getValue(Side.P1).any { it in keys.getValue(Side.P2) }) continue
            val team = keys.mapValues { (_, ks) -> ks.map { rating[it] ?: START.toDouble() }.average() }
            val d = HashMap<String, Int>()
            for (side in Side.entries) {
                val e = expected(team.getValue(side), team.getValue(side.other))
                val s = score(g, side)
                for ((i, k) in keys.getValue(side).withIndex()) {
                    val before = rating[k] ?: START.toDouble()
                    val change = kFactor(count[k] ?: 0) * (s - e)
                    rating[k] = before + change
                    count[k] = (count[k] ?: 0) + 1
                    if (g.winner == side) wins[k] = (wins[k] ?: 0) + 1
                    names[k] = named.getValue(side)[i]
                    history.getOrPut(k) { mutableListOf() } += g.at to rating.getValue(k).roundToInt()
                    d[k] = (rating.getValue(k).roundToInt() - before.roundToInt())
                }
            }
            deltas[g.id] = d
        }
        val players = rating.keys.map { k ->
            Player(k, names[k] ?: k, rating.getValue(k).roundToInt(), count[k] ?: 0, wins[k] ?: 0, history[k].orEmpty())
        }.sortedWith(compareByDescending<Player> { it.rating }.thenBy { it.name.lowercase() })
        return Table(players, deltas)
    }

    /** Una partita dello storico come [Game], o null se non conta (non finita, amichevole, senza vincitore). */
    fun gameOf(rec: MatchRecord, sets: List<com.tennis.scoremanager.model.SetScore>, winner: Side?): Game? {
        if (winner == null || !rec.finished || rec.friendly) return null
        val games = Side.entries.associateWith { side ->
            // Il super tie-break vale come un game: i suoi punti falserebbero la quota.
            sets.sumOf { st -> if (st.matchTiebreak) (if (st.winner == side) 1 else 0) else st.games(side) }
        }
        return Game(
            id = rec.id,
            at = rec.endedAt ?: rec.startedAt ?: rec.updatedAt,
            sport = rec.setup.sport,
            sides = Side.entries.associateWith { PlayerStats.names(rec.setup, it) },
            winner = winner,
            games = games,
        )
    }
}
