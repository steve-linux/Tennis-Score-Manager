// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import com.tennis.scoremanager.model.MatchEvent
import com.tennis.scoremanager.model.MatchState
import com.tennis.scoremanager.model.ScoreEngine
import com.tennis.scoremanager.model.Side
import com.tennis.scoremanager.model.TiebreakKind

/**
 * Statistiche di fine partita e cronologia punto per punto, ricavate rigiocando i punti (come le pagine
 * "statistiche" e "cronologia" dei siti di risultati). Ace, doppi falli, prime di servizio, vincenti ed errori
 * non si possono sapere: l'app conosce solo chi ha vinto ogni punto (e, coi braccialetti 2.4, i colpi).
 */
object MatchStats {

    /** Un punto giocato. [breakPoint] e [matchPointFor] valgono per la situazione prima del punto. */
    data class PlayedPoint(
        /** Set in cui si è giocato il punto (0 = primo). */
        val set: Int,
        val after: MatchState,
        val winner: Side,
        val server: Side,
        val tiebreak: TiebreakKind,
        val breakPoint: Boolean,
        val matchPointFor: Side?,
        /** Chi ha vinto il game (o il tie-break) con questo punto; null se il game continua. */
        val gameWinner: Side?,
        /** Colpi dei braccialetti nello scambio (null = non contati). */
        val strokes: Int?,
    )

    fun points(rec: MatchRecord): List<PlayedPoint> {
        var s = ScoreEngine.initial(rec.rules)
        val out = mutableListOf<PlayedPoint>()
        for (e in rec.events) {
            val step = ScoreEngine.apply(s, e)
            val t = step.transition
            if (e is MatchEvent.Point && t != null) {
                out += PlayedPoint(
                    set = s.sets.size,
                    after = step.state,
                    winner = e.winner,
                    server = s.server,
                    tiebreak = s.tiebreak,
                    breakPoint = ScoreEngine.isBreakPoint(s),
                    matchPointFor = Side.entries.firstOrNull { ScoreEngine.lookahead(s, it)?.matchWinner == it },
                    gameWinner = t.gameWinner,
                    strokes = e.strokes,
                )
            }
            s = step.state
        }
        return out
    }

    /** Numeri di un giocatore (o squadra) in un periodo. Le coppie "vinti / giocati" danno le percentuali. */
    data class SideTotals(
        val servePointsWon: Int = 0,
        val servePoints: Int = 0,
        val serviceGamesWon: Int = 0,
        val serviceGames: Int = 0,
        val breakPointsSaved: Int = 0,
        val breakPointsFaced: Int = 0,
        val returnPointsWon: Int = 0,
        val returnPoints: Int = 0,
        val returnGamesWon: Int = 0,
        val returnGames: Int = 0,
        val breakPointsWon: Int = 0,
        val breakPoints: Int = 0,
        val pointsWon: Int = 0,
        val points: Int = 0,
        val gamesWon: Int = 0,
        val games: Int = 0,
        /** Punti vinti di fila (il massimo nel periodo). */
        val maxStreak: Int = 0,
        /** Punti vinti tra gli ultimi 10 del periodo. */
        val last10: Int = 0,
        val matchPointsSaved: Int = 0,
    )

    /** [set] = null: tutta la partita; altrimenti solo quel set (0 = primo). */
    fun totals(all: List<PlayedPoint>, set: Int? = null): Map<Side, SideTotals> {
        val pts = if (set == null) all else all.filter { it.set == set }
        return Side.entries.associateWith { totalsFor(pts, it) }
    }

    private fun totalsFor(pts: List<PlayedPoint>, me: Side): SideTotals {
        val serve = pts.filter { it.server == me }
        val ret = pts.filter { it.server != me }
        // Game chiusi: il tie-break di set conta come un game (7-6), il match tie-break no.
        val games = pts.filter { it.gameWinner != null && it.tiebreak != TiebreakKind.MATCH }
        // Game al servizio e in risposta: solo quelli normali (nel tie-break si serve a turno).
        val regular = games.filter { it.tiebreak == TiebreakKind.NONE }
        var streak = 0
        var best = 0
        for (p in pts) {
            streak = if (p.winner == me) streak + 1 else 0
            best = maxOf(best, streak)
        }
        return SideTotals(
            servePointsWon = serve.count { it.winner == me },
            servePoints = serve.size,
            serviceGamesWon = regular.count { it.server == me && it.gameWinner == me },
            serviceGames = regular.count { it.server == me },
            breakPointsSaved = serve.count { it.breakPoint && it.winner == me },
            breakPointsFaced = serve.count { it.breakPoint },
            returnPointsWon = ret.count { it.winner == me },
            returnPoints = ret.size,
            returnGamesWon = regular.count { it.server != me && it.gameWinner == me },
            returnGames = regular.count { it.server != me },
            breakPointsWon = ret.count { it.breakPoint && it.winner == me },
            breakPoints = ret.count { it.breakPoint },
            pointsWon = pts.count { it.winner == me },
            points = pts.size,
            gamesWon = games.count { it.gameWinner == me },
            games = games.size,
            maxStreak = best,
            last10 = pts.takeLast(10).count { it.winner == me },
            matchPointsSaved = pts.count { it.matchPointFor == me.other && it.winner == me },
        )
    }

    /** Colpi per punto nel periodo (punti con la lunghezza dello scambio); null se non ce ne sono. */
    fun rally(all: List<PlayedPoint>, set: Int? = null): Reports.RallyStats? =
        Reports.rallyStats(all.filter { set == null || it.set == set }.mapNotNull { it.strokes })

    // ------------------------------------------------------------------ cronologia

    /** Cosa si gioca il punto successivo: palla break, set point o match point. */
    enum class Marker { BREAK_POINT, SET_POINT, MATCH_POINT }

    /** Punteggio dopo un punto ("30", "40", "AD"; numeri nel tie-break) e la palla che si gioca dopo. */
    data class ScoreStep(val p1: String, val p2: String, val marker: Marker?)

    data class GameLog(
        val set: Int,
        /** Chi serve il game (nel tie-break: chi serve il primo punto). */
        val server: Side,
        val tiebreak: TiebreakKind,
        /** Game nel set dopo questo game; per il match tie-break i punti del tie-break. */
        val games1: Int,
        val games2: Int,
        /** null = game in corso (partita sospesa). */
        val winner: Side?,
        /** Game perso da chi serviva (fuori dal tie-break). */
        val broken: Boolean,
        /** Punteggi dopo ogni punto tranne quello che chiude il game. */
        val steps: List<ScoreStep>,
        /** Punti del tie-break (solo se [tiebreak] non è NONE e il tie-break è finito). */
        val tb1: Int? = null,
        val tb2: Int? = null,
        val closesSet: Boolean = false,
        val closesMatch: Boolean = false,
    )

    fun chronology(all: List<PlayedPoint>): List<GameLog> {
        val out = mutableListOf<GameLog>()
        var cur = mutableListOf<PlayedPoint>()
        for (p in all) {
            cur += p
            if (p.gameWinner != null) {
                out += game(cur)
                cur = mutableListOf()
            }
        }
        if (cur.isNotEmpty()) out += game(cur)
        return out
    }

    private fun marker(s: MatchState): Marker? = when {
        s.isFinished -> null
        Side.entries.any { ScoreEngine.lookahead(s, it)?.matchWinner == it } -> Marker.MATCH_POINT
        Side.entries.any { ScoreEngine.lookahead(s, it)?.setWinner == it } -> Marker.SET_POINT
        ScoreEngine.isBreakPoint(s) -> Marker.BREAK_POINT
        else -> null
    }

    private fun game(pts: List<PlayedPoint>): GameLog {
        val first = pts.first()
        val last = pts.last()
        val winner = last.gameWinner
        val steps = pts.filter { it.gameWinner == null }
            .map { ScoreStep(it.after.pointLabel(Side.P1), it.after.pointLabel(Side.P2), marker(it.after)) }
        // Game che chiude il set: il punteggio sta nel set appena aggiunto, altrimenti nei game in corso.
        val closedSet = last.after.sets.getOrNull(first.set)?.takeIf { winner != null }
        val mtb = first.tiebreak == TiebreakKind.MATCH
        return GameLog(
            set = first.set,
            server = first.server,
            tiebreak = first.tiebreak,
            games1 = when {
                closedSet == null -> last.after.g1
                mtb -> closedSet.tb1 ?: 0
                else -> closedSet.g1
            },
            games2 = when {
                closedSet == null -> last.after.g2
                mtb -> closedSet.tb2 ?: 0
                else -> closedSet.g2
            },
            winner = winner,
            broken = first.tiebreak == TiebreakKind.NONE && winner != null && winner != first.server,
            steps = steps,
            tb1 = closedSet?.tb1.takeIf { first.tiebreak != TiebreakKind.NONE },
            tb2 = closedSet?.tb2.takeIf { first.tiebreak != TiebreakKind.NONE },
            closesSet = closedSet != null,
            closesMatch = winner != null && last.after.isFinished,
        )
    }
}
