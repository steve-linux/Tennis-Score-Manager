// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

/**
 * Il timer degli esercizi senza colpi da contare (gioco di gambe, preparazione fisica, tattica, smash): giri di lavoro e
 * recupero, con la voce che dice quando partire, quando riposare e il conto alla rovescia. Qui solo i conti, a secondi
 * interi dall'avvio: la schermata li chiede ogni secondo e dice a voce quello che capita in quel secondo.
 */
object DrillTimer {

    /** [work] secondi di lavoro e [rest] di recupero, per [rounds] giri; prima [lead] secondi per prepararsi (e sentire nome e obiettivo). */
    data class Plan(val work: Int, val rest: Int, val rounds: Int, val lead: Int = 10) {
        /** Quanto dura tutto, in secondi (dopo l'ultimo giro niente recupero). */
        val total: Int get() = lead + rounds * work + (rounds - 1) * rest
    }

    enum class Kind { PREP, WORK, REST, DONE }

    /** Dove si è: che fase, che giro (da 1) e quanti secondi mancano alla fine della fase. */
    data class State(val kind: Kind, val round: Int, val left: Int)

    /** Quello che la voce dice in un certo secondo. */
    sealed interface Cue {
        /** Parte il giro [round] (l'ultimo se [last]). */
        data class Go(val round: Int, val rounds: Int, val last: Boolean) : Cue
        /** Comincia il recupero, di [seconds] secondi. */
        data class Rest(val seconds: Int) : Cue
        /** Conto alla rovescia: 3, 2, 1 prima che la fase finisca. */
        data class Count(val n: Int) : Cue
        /** Mancano dieci secondi alla fine del lavoro (solo nei lavori lunghi). */
        data object TenLeft : Cue
        /** Tutto finito. */
        data object Done : Cue
    }

    fun state(p: Plan, t: Int): State {
        if (t < p.lead) return State(Kind.PREP, 1, p.lead - t)
        if (t >= p.total) return State(Kind.DONE, p.rounds, 0)
        var s = t - p.lead
        for (r in 1..p.rounds) {
            if (s < p.work) return State(Kind.WORK, r, p.work - s)
            s -= p.work
            if (r < p.rounds) {
                if (s < p.rest) return State(Kind.REST, r, p.rest - s)
                s -= p.rest
            }
        }
        return State(Kind.DONE, p.rounds, 0)
    }

    /** Quello che capita esattamente al secondo [t] (all'inizio di una fase, o negli ultimi 3 secondi). */
    fun cues(p: Plan, t: Int): List<Cue> {
        val st = state(p, t)
        return when (st.kind) {
            Kind.DONE -> if (t == p.total) listOf(Cue.Done) else emptyList()
            Kind.WORK -> buildList {
                if (st.left == p.work) add(Cue.Go(st.round, p.rounds, st.round == p.rounds))
                else if (st.left in 1..3) add(Cue.Count(st.left))
                else if (st.left == 10 && p.work >= 30) add(Cue.TenLeft)
            }
            Kind.REST -> buildList {
                if (st.left == p.rest) add(Cue.Rest(p.rest))
                else if (st.left in 1..3) add(Cue.Count(st.left))
            }
            Kind.PREP -> if (st.left in 1..3) listOf(Cue.Count(st.left)) else emptyList()
        }
    }
}
