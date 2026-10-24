// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.video

import com.tennis.scoremanager.model.MatchState
import com.tennis.scoremanager.model.Side
import kotlinx.serialization.Serializable

/**
 * Il punteggio come lo disegna la grafica della telecamera: lo prepara il telefono dell'arbitro e lo manda al
 * telefono-telecamera a ogni punto. Una riga per giocatore (o squadra): [sets] finiti, [games] del set in corso,
 * [points] del game (o del tie-break), [server] 0/1 (-1 a partita finita).
 */
@Serializable
data class ScoreOverlay(
    val names: List<String>,
    val sets: List<List<Int>> = emptyList(),
    /** Punti del perdente nel tie-break di ciascun set finito (null = set senza tie-break). */
    val setTiebreaks: List<Int?> = emptyList(),
    val games: List<Int> = listOf(0, 0),
    val points: List<String> = listOf("0", "0"),
    val server: Int = 0,
    val tiebreak: Boolean = false,
    /** BREAK POINT, SET POINT, MATCH POINT...: la scritta sotto il punteggio. */
    val banner: String? = null,
    val winner: Int = -1,
    /** Circolo · campo, sopra il punteggio (vuoto = niente). */
    val title: String = "",
) {
    companion object {
        fun from(state: MatchState, names: List<String>, banner: String?, title: String): ScoreOverlay {
            val finished = state.isFinished
            return ScoreOverlay(
                names = names,
                sets = state.sets.map { listOf(it.g1, it.g2) },
                setTiebreaks = state.sets.map { s -> if (s.hasTiebreak) minOf(s.tb1!!, s.tb2!!) else null },
                games = if (finished) emptyList() else listOf(state.g1, state.g2),
                points = if (finished) emptyList() else Side.entries.map { state.pointLabel(it) },
                server = if (finished) -1 else state.server.ordinal,
                tiebreak = state.inTiebreak,
                banner = banner,
                winner = state.winner?.ordinal ?: -1,
                title = title,
            )
        }
    }
}

/**
 * Un punto giocato, per i capitoli del video: [at] ora della fine del punto (sull'orologio del telefono-telecamera,
 * poi in ms dall'inizio del video), [score] il punteggio dopo il punto ("6-4 3-2 30-15"), [winner] 0/1, [kind] ACE,
 * DOUBLE_FAULT... se segnato.
 */
@Serializable
data class PointMark(
    val at: Long,
    val score: String,
    val winner: Int,
    val kind: String? = null,
    /** Set (da 0) e game della partita (da 0, contati su tutti i set) in cui si è giocato il punto. */
    val set: Int = 0,
    val game: Int = 0,
    /** Punto giocato sulla seconda di servizio (la prima era fuori). */
    val secondServe: Boolean = false,
    /** Cosa c'era in palio prima del punto: MATCH_POINT, SET_POINT, BREAK_POINT, DECIDING (null = niente). */
    val pressure: String? = null,
    /** Colpi dello scambio (dai braccialetti), se si sanno. */
    val strokes: Int? = null,
    /** Chi serviva il punto: 0/1, -1 = non si sa (si ricalcola dalle regole se ci sono). */
    val server: Int = -1,
)
