// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.video

import com.tennis.scoremanager.model.RulesConfig

/**
 * Highlights: quali punti finiscono nel montaggio e da dove a dove. Ogni punto prende un valore (match point 5,
 * set point 4, palla break o scambio lungo 3, ace e vincente 2). Se tutti insieme superano la durata scelta, prima
 * si accorciano i pezzi (meno secondi prima della fine del punto, fino a [MIN_BEFORE_MS]), poi, se non basta, si
 * lasciano fuori i meno importanti; alla fine si rimettono in ordine di tempo.
 */
object Highlights {
    /** Prima della fine del punto: abbastanza per vedere servizio e scambio di un punto normale. */
    const val BEFORE_MS = 12_000L
    /** Il minimo a cui si accorcia un pezzo quando il montaggio supera la durata scelta. */
    const val MIN_BEFORE_MS = 5_000L
    /** Dopo la fine: la reazione, il punteggio che cambia. */
    const val AFTER_MS = 2_000L
    /** Dagli 8 colpi in su lo scambio è lungo. */
    const val LONG_RALLY = 8

    enum class Reason { MATCH_POINT, SET_POINT, BREAK_POINT, LONG_RALLY, ACE, WINNER }

    /** Cosa includere (tutto acceso di serie). */
    data class Options(
        val aces: Boolean = true,
        val winners: Boolean = true,
        val bigPoints: Boolean = true,
        val longRallies: Boolean = true,
        /** Durata massima del montaggio: 5 minuti vanno bene per i social, un circolo può volerne 15. */
        val maxMs: Long = 5 * 60_000L,
    )

    /** Un pezzo del montaggio: il punto [index], da [startMs] a [endMs] del video, e perché c'è. */
    data class Clip(val index: Int, val startMs: Long, val endMs: Long, val reason: Reason) {
        val durationMs: Long get() = endMs - startMs
    }

    /** Il motivo più forte per tenere il punto (null = non va negli highlights). */
    fun reason(p: PointMark, pressure: String?, o: Options): Reason? = when {
        o.bigPoints && pressure == "MATCH_POINT" -> Reason.MATCH_POINT
        o.bigPoints && pressure == "SET_POINT" -> Reason.SET_POINT
        o.bigPoints && pressure == "BREAK_POINT" -> Reason.BREAK_POINT
        o.longRallies && (p.strokes ?: 0) >= LONG_RALLY -> Reason.LONG_RALLY
        o.aces && p.kind == "ACE" -> Reason.ACE
        o.winners && p.kind == "WINNER" -> Reason.WINNER
        else -> null
    }

    fun value(r: Reason): Int = when (r) {
        Reason.MATCH_POINT -> 5
        Reason.SET_POINT -> 4
        Reason.BREAK_POINT, Reason.LONG_RALLY -> 3
        Reason.ACE, Reason.WINNER -> 2
    }

    /**
     * I pezzi del montaggio, in ordine di tempo. [rules] serve per i video segnati prima che i punti portassero
     * cosa c'era in palio: lo si ricalcola. [videoMs] è la durata del video (i pezzi non vanno oltre).
     */
    fun select(points: List<PointMark>, rules: RulesConfig?, videoMs: Long, o: Options = Options()): List<Clip> {
        val chosen = points.mapIndexedNotNull { i, p ->
            val pressure = p.pressure ?: rules?.let { PointMarks.pressure(PointMarks.stateAfter(it, points, i)) }
            reason(p, pressure, o)?.let { i to it }
        }
        fun clips(before: Long) = chosen.mapNotNull { (i, r) ->
            val p = points[i]
            // Non prima della fine del punto precedente: si vedrebbe la fine di un altro punto.
            val prevEnd = points.getOrNull(i - 1)?.at?.plus(1_000) ?: 0L
            val start = maxOf(p.at - before, prevEnd, 0L)
            val end = (p.at + AFTER_MS).let { if (videoMs > 0) minOf(it, videoMs) else it }
            if (end <= start) null else Clip(i, start, end, r)
        }
        // Troppo lungo: prima si accorciano tutti i pezzi, un secondo alla volta.
        var before = BEFORE_MS
        var candidates = clips(before)
        while (candidates.sumOf { it.durationMs } > o.maxMs && before > MIN_BEFORE_MS) {
            before -= 1_000
            candidates = clips(before)
        }
        // Ancora troppo lungo: restano i più importanti (a parità, i più recenti: il finale conta).
        val kept = mutableListOf<Clip>()
        var total = 0L
        for (c in candidates.sortedWith(compareByDescending<Clip> { value(it.reason) }.thenByDescending { it.index })) {
            if (total + c.durationMs > o.maxMs) continue
            kept += c
            total += c.durationMs
        }
        return kept.sortedBy { it.index }
    }
}
