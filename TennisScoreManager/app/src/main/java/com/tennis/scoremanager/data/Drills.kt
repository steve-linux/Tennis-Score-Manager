// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import kotlinx.serialization.Serializable

/**
 * Un passo di un esercizio: [count] colpi con queste impostazioni (quelle null restano come sono). Servizio: lato,
 * prima o seconda, bersaglio; dritto, rovescio e volée: direzione; volée: dritto o rovescio.
 */
@Serializable
data class DrillStep(
    val count: Int,
    val side: ServeSide? = null,
    val kind: ServeKind? = null,
    val target: ServeTarget? = null,
    val direction: ShotDirection? = null,
    val wing: Wing? = null,
)

/** Un esercizio guidato: nome, colpo e i passi in fila ("20 dritti incrociati, poi 20 lungolinea"). */
@Serializable
data class Drill(
    val id: String,
    val name: String,
    val stroke: TrainedStroke,
    val steps: List<DrillStep>,
    val createdAt: Long = 0,
) {
    val total: Int get() = steps.sumOf { it.count }
}

object Drills {
    /** A che punto è l'esercizio dopo [shots] colpi segnati: passo, colpi fatti nel passo, finito. */
    data class Progress(val step: Int, val done: Int, val finished: Boolean) {
        /** Il colpo appena segnato ha chiuso un passo (e se ne apre un altro, o l'esercizio è finito). */
        val stepJustEnded: Boolean get() = done == 0 && (step > 0 || finished)
    }

    fun progress(drill: Drill, shots: Int): Progress {
        var left = shots
        for ((i, st) in drill.steps.withIndex()) {
            if (left < st.count) return Progress(i, left, false)
            left -= st.count
        }
        return Progress(drill.steps.size, 0, true)
    }

    /** Il passo in corso (null se l'esercizio è finito o vuoto). */
    fun current(drill: Drill, shots: Int): DrillStep? = progress(drill, shots).let { p -> if (p.finished) null else drill.steps.getOrNull(p.step) }

    /** Esempi pronti, per colpo: si aggiungono all'elenco e si cambiano a piacere. */
    fun templates(names: (TrainedStroke) -> String, now: Long): List<Drill> = listOf(
        Drill("t-serve-t-wide", names(TrainedStroke.SERVE), TrainedStroke.SERVE, listOf(
            DrillStep(10, side = ServeSide.DEUCE, kind = ServeKind.FIRST, target = ServeTarget.T),
            DrillStep(10, side = ServeSide.DEUCE, kind = ServeKind.FIRST, target = ServeTarget.WIDE),
            DrillStep(10, side = ServeSide.AD, kind = ServeKind.FIRST, target = ServeTarget.T),
            DrillStep(10, side = ServeSide.AD, kind = ServeKind.FIRST, target = ServeTarget.WIDE),
        ), now),
        Drill("t-forehand-cross-line", names(TrainedStroke.FOREHAND), TrainedStroke.FOREHAND, listOf(
            DrillStep(20, direction = ShotDirection.CROSS), DrillStep(20, direction = ShotDirection.LINE),
        ), now),
        Drill("t-backhand-cross-line", names(TrainedStroke.BACKHAND), TrainedStroke.BACKHAND, listOf(
            DrillStep(20, direction = ShotDirection.CROSS), DrillStep(20, direction = ShotDirection.LINE),
        ), now),
        Drill("t-volley-fh-bh", names(TrainedStroke.VOLLEY), TrainedStroke.VOLLEY, listOf(
            DrillStep(15, wing = Wing.FOREHAND, direction = ShotDirection.CROSS), DrillStep(15, wing = Wing.BACKHAND, direction = ShotDirection.CROSS),
        ), now),
    )
}
