// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.model

/**
 * Il cambio palle dei tornei: la prima volta dopo [first] game, poi ogni [every] (es. 7 e 9). Il tie-break conta come
 * un game, il match tie-break pure. Se il cambio tocca all'inizio di un tie-break si rimanda all'inizio del secondo
 * game del set dopo, come dice il regolamento.
 */
object NewBalls {
    /** Le sequenze che si propongono: (dopo quanti game la prima volta, poi ogni quanti). */
    val choices: List<Pair<Int, Int>> = listOf(7 to 9, 9 to 11, 11 to 13)

    /** I game giocati finora nella partita. */
    fun gamesPlayed(s: MatchState): Int = s.sets.sumOf { if (it.matchTiebreak) 1 else it.g1 + it.g2 } + s.g1 + s.g2

    /** Dopo [games] game si cambiano le palle? */
    fun dueAt(games: Int, first: Int, every: Int): Boolean =
        first > 0 && (games == first || (every > 0 && games > first && (games - first) % every == 0))

    /**
     * Dopo il punto che ha portato a [after] con [t]: (si cambiano adesso, resta un cambio rimandato). [pending] dice se
     * ce n'era uno rimandato dal tie-break.
     */
    fun check(after: MatchState, t: Transition, first: Int, every: Int, pending: Boolean): Pair<Boolean, Boolean> {
        if (first <= 0 || after.isFinished || t.gameWinner == null) return false to pending
        val due = dueAt(gamesPlayed(after), first, every)
        return when {
            // Si comincia il tie-break: il cambio aspetta il secondo game del set dopo.
            t.tiebreakStarted && due -> false to true
            // Il primo game del set è finito: tocca a quello rimandato (o a uno che cade proprio qui).
            pending && after.g1 + after.g2 == 1 && !after.inTiebreak -> true to false
            else -> due to pending
        }
    }
}
