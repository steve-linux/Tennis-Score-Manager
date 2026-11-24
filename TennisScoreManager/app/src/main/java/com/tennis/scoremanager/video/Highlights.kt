// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.video

import com.tennis.scoremanager.model.RulesConfig

/**
 * Highlights: quali punti finiscono nel montaggio e da dove a dove. Le voci (ultimo punto, palle break/set/match
 * point, ace, vincenti, scambi lunghi) hanno la precedenza nell'ordine scelto dall'utente. Se tutti insieme superano la durata scelta, prima
 * si accorciano i pezzi (meno secondi prima della fine del punto, fino a [MIN_BEFORE_MS]), poi, se non basta, si
 * lasciano fuori i meno importanti; alla fine si rimettono in ordine di tempo.
 */
object Highlights {
    /** Prima della fine del punto: abbastanza per vedere servizio e scambio di un punto normale. */
    const val BEFORE_MS = 12_000L
    /** Il minimo a cui si accorcia un pezzo quando il montaggio supera la durata scelta. */
    const val MIN_BEFORE_MS = 5_000L
    /** Il punto più corto che si possa giocare (un ace): punti segnati più vicini di così sono tocchi a raffica. */
    const val MIN_POINT_MS = 4_000L
    /** Dopo la fine: la reazione, il punteggio che cambia. */
    const val AFTER_MS = 2_000L
    /** Dagli 8 colpi in su lo scambio è lungo. */
    const val LONG_RALLY = 8

    enum class Reason { LAST_POINT, MATCH_POINT, SET_POINT, BREAK_POINT, LONG_RALLY, ACE, WINNER }

    /**
     * Le voci che si possono mettere negli highlights, ordinabili: quelle più in alto hanno la precedenza.
     * [LAST_POINT] è l'ultimo punto segnato nel video: a partita finita, quello che la chiude.
     */
    enum class Category { LAST_POINT, BIG_POINTS, ACES, WINNERS, LONG_RALLIES }

    /**
     * Cosa includere e con che precedenza: [order] dalla più importante, [enabled] quelle accese. Quando non ci
     * sta tutto, restano fuori prima le voci in fondo.
     */
    data class Options(
        val order: List<Category> = Category.entries,
        val enabled: Set<Category> = Category.entries.toSet(),
        /** Durata massima del montaggio: 5 minuti vanno bene per i social, un circolo può volerne 15. */
        val maxMs: Long = 5 * 60_000L,
    )

    /** Un pezzo del montaggio: il punto [index], da [startMs] a [endMs] del video, e perché c'è. */
    data class Clip(val index: Int, val startMs: Long, val endMs: Long, val reason: Reason? = null, val priority: Int = 0) {
        val durationMs: Long get() = endMs - startMs
    }

    private fun category(r: Reason): Category = when (r) {
        Reason.MATCH_POINT, Reason.SET_POINT, Reason.BREAK_POINT -> Category.BIG_POINTS
        Reason.LAST_POINT -> Category.LAST_POINT
        Reason.LONG_RALLY -> Category.LONG_RALLIES
        Reason.ACE -> Category.ACES
        Reason.WINNER -> Category.WINNERS
    }

    /** Tutti i motivi per cui il punto potrebbe entrare (un ace su palla break ne ha due); [last]: è l'ultimo. */
    private fun reasons(p: PointMark, pressure: String?, last: Boolean): List<Reason> = listOfNotNull(
        Reason.LAST_POINT.takeIf { last },
        when (pressure) {
            "MATCH_POINT" -> Reason.MATCH_POINT
            "SET_POINT" -> Reason.SET_POINT
            "BREAK_POINT" -> Reason.BREAK_POINT
            else -> null
        },
        Reason.LONG_RALLY.takeIf { (p.strokes ?: 0) >= LONG_RALLY },
        Reason.ACE.takeIf { p.kind == "ACE" },
        Reason.WINNER.takeIf { p.kind == "WINNER" },
    )

    /**
     * Precedenza di un motivo: prima conta la posizione della sua voce (più in alto, più conta), poi, dentro "palle
     * break, set e match point", match point > set point > palla break.
     */
    fun priority(r: Reason, o: Options): Int {
        // Una voce che l'ordine non elenca conta meno di tutte.
        val rank = o.order.indexOf(category(r)).let { if (it < 0) 0 else o.order.size - it }
        val sub = when (r) {
            Reason.MATCH_POINT -> 3
            Reason.SET_POINT -> 2
            Reason.BREAK_POINT -> 1
            else -> 0
        }
        return rank * 10 + sub
    }

    /** Il motivo più forte per tenere il punto tra le voci accese (null = non va negli highlights). */
    fun reason(p: PointMark, pressure: String?, o: Options, last: Boolean = false): Reason? =
        reasons(p, pressure, last).filter { category(it) in o.enabled }.maxByOrNull { priority(it, o) }

    /** Il pezzo di video del punto [i]: da [before] prima della fine (non prima del punto precedente) a 2 s dopo. */
    fun clip(points: List<PointMark>, i: Int, videoMs: Long, before: Long = BEFORE_MS): Clip? {
        val p = points[i]
        // Non prima della fine del punto precedente: si vedrebbe la fine di un altro punto. Un punto vero dura almeno
        // [MIN_POINT_MS]: quelli segnati a raffica subito prima (per rimettere a posto il punteggio) non contano, se no
        // il pezzo diventerebbe di un secondo.
        val prevEnd = (i - 1 downTo 0).firstOrNull { p.at - points[it].at >= MIN_POINT_MS }?.let { points[it].at + 1_000 } ?: 0L
        val start = maxOf(p.at - before, prevEnd, 0L)
        val end = (p.at + AFTER_MS).let { if (videoMs > 0) minOf(it, videoMs) else it }
        return if (end <= start) null else Clip(i, start, end)
    }

    /**
     * I pezzi del montaggio, in ordine di tempo. [rules] serve per i video segnati prima che i punti portassero
     * cosa c'era in palio: lo si ricalcola. [videoMs] è la durata del video (i pezzi non vanno oltre).
     */
    fun select(points: List<PointMark>, rules: RulesConfig?, videoMs: Long, o: Options = Options()): List<Clip> {
        val chosen = points.mapIndexedNotNull { i, p ->
            val pressure = p.pressure ?: rules?.let { PointMarks.pressure(PointMarks.stateAfter(it, points, i)) }
            reason(p, pressure, o, last = i == points.lastIndex)?.let { i to it }
        }
        fun clips(before: Long) = chosen.mapNotNull { (i, r) -> clip(points, i, videoMs, before)?.copy(reason = r, priority = priority(r, o)) }
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
        for (c in candidates.sortedWith(compareByDescending<Clip> { it.priority }.thenByDescending { it.index })) {
            if (total + c.durationMs > o.maxMs) continue
            kept += c
            total += c.durationMs
        }
        return continuous(kept.sortedBy { it.index })
    }

    /**
     * Due punti di fila nel montaggio: il primo finisce [AFTER_MS] dopo la sua fine, il secondo comincerebbe 1 s dopo
     * la stessa fine, e quel secondo di video si vedrebbe due volte (sembra uno spezzone ripetuto). Il secondo
     * pezzo riparte da dove finisce il primo: il video scorre di seguito, senza ripetere niente.
     */
    fun continuous(clips: List<Clip>): List<Clip> {
        val out = ArrayList<Clip>(clips.size)
        for (c in clips) {
            val prev = out.lastOrNull()
            out += if (prev != null && c.startMs < prev.endMs) c.copy(startMs = prev.endMs) else c
        }
        return out.filter { it.durationMs > 0 }
    }
}
