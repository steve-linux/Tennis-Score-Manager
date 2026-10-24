// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.video

import com.tennis.scoremanager.model.MatchEvent
import com.tennis.scoremanager.model.MatchState
import com.tennis.scoremanager.model.PointKind
import com.tennis.scoremanager.model.RulesConfig
import com.tennis.scoremanager.model.ScoreEngine
import com.tennis.scoremanager.model.Side

/** Dallo stato della partita al segno di un punto, e ritorno: serve dal vivo (arbitro) e ai punti segnati dopo. */
object PointMarks {
    /** "6-4 3-2 30-15": il punteggio dopo il punto. */
    fun score(state: MatchState): String =
        (state.sets.map { "${it.g1}-${it.g2}" } + if (state.isFinished) emptyList() else listOf("${state.g1}-${state.g2}", "${state.pointLabel(Side.P1)}-${state.pointLabel(Side.P2)}"))
            .joinToString(" ")

    /**
     * Set e game (contati su tutta la partita) in cui si è giocato l'ultimo punto, dallo stato dopo il punto: se il
     * punto ha chiuso il game (punti tornati a zero) apparteneva al game prima, e se ha chiuso il set al set prima.
     */
    fun place(state: MatchState): Pair<Int, Int> {
        val total = state.sets.sumOf { it.g1 + it.g2 } + state.g1 + state.g2
        val gameEnded = state.isFinished || (state.pt1 == 0 && state.pt2 == 0)
        val setEnded = state.isFinished || (gameEnded && state.g1 == 0 && state.g2 == 0 && state.sets.isNotEmpty())
        val set = if (setEnded) state.sets.size - 1 else state.sets.size
        val game = if (gameEnded) total - 1 else total
        return set.coerceAtLeast(0) to game.coerceAtLeast(0)
    }

    /** Il segno del punto appena giocato, con lo stato dopo il punto e (se si sa) quello prima. */
    fun mark(at: Long, state: MatchState, winner: Side, kind: PointKind?, secondServe: Boolean = false, before: MatchState? = null): PointMark {
        val (set, game) = place(state)
        return PointMark(at, score(state), winner.ordinal, kind?.name, set, game, secondServe, before?.let(::pressure))
    }

    /** Cosa c'è in palio nel prossimo punto (come le scritte del riquadro arancione e della grafica). */
    fun pressure(state: MatchState): String? {
        if (state.isFinished) return null
        val next = Side.entries.mapNotNull { ScoreEngine.lookahead(state, it) }
        return when {
            next.any { it.matchWinner != null } -> "MATCH_POINT"
            next.any { it.setWinner != null } -> "SET_POINT"
            state.isDecidingPoint -> "DECIDING"
            ScoreEngine.isBreakPoint(state) -> "BREAK_POINT"
            else -> null
        }
    }

    /** I punti segnati rigiocati con le regole: lo stato dopo i primi [n]. */
    fun stateAfter(rules: RulesConfig, points: List<PointMark>, n: Int): MatchState =
        ScoreEngine.replay(rules, points.take(n).map { p ->
            MatchEvent.Point(
                winner = if (p.winner == 1) Side.P2 else Side.P1,
                kind = p.kind?.let { k -> runCatching { PointKind.valueOf(k) }.getOrNull() },
                secondServe = p.secondServe,
            )
        })
}
