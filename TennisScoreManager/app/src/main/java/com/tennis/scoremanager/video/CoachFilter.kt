// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.video

import com.tennis.scoremanager.model.RulesConfig

/**
 * Filtri da allenatore: quali punti rivedere. [player] 0/1 (null = tutti) a cui si riferiscono vinti/persi e
 * servizio/risposta; i campi null non filtrano niente.
 */
data class CoachFilter(
    val player: Int? = null,
    /** true = vinti da [player], false = persi. */
    val won: Boolean? = null,
    /** true = [player] al servizio, false = in risposta. */
    val serving: Boolean? = null,
    /** true = giocati sulla seconda, false = sulla prima. */
    val secondServe: Boolean? = null,
    /** Com'è finito (ACE, DOUBLE_FAULT, WINNER, UNFORCED_ERROR, FORCED_ERROR): vuoto = qualsiasi. */
    val kinds: Set<String> = emptySet(),
    /** "BREAK" = palle break, "SET_MATCH" = set e match point. */
    val situation: String? = null,
) {
    val active: Boolean get() = this != CoachFilter()

    companion object {
        /**
         * I punti che passano il filtro, in ordine. Chi serviva e cosa c'era in palio, se mancano nei segni (video
         * segnati prima che li portassero), si ricalcolano con le regole.
         */
        fun matching(points: List<PointMark>, rules: RulesConfig?, f: CoachFilter): List<Int> = points.indices.filter { i ->
            val p = points[i]
            val before = if ((p.server < 0 || p.pressure == null) && rules != null) PointMarks.stateAfter(rules, points, i) else null
            val server = p.server.takeIf { it >= 0 } ?: before?.server?.ordinal ?: -1
            val pressure = p.pressure ?: before?.let { PointMarks.pressure(it) }
            val me = f.player
            (f.won == null || me == null || (p.winner == me) == f.won) &&
                (f.serving == null || me == null || (server >= 0 && (server == me) == f.serving)) &&
                (f.secondServe == null || p.secondServe == f.secondServe) &&
                (f.kinds.isEmpty() || p.kind in f.kinds) &&
                when (f.situation) {
                    "BREAK" -> pressure == "BREAK_POINT"
                    "SET_MATCH" -> pressure == "SET_POINT" || pressure == "MATCH_POINT"
                    else -> true
                }
        }
    }
}
