// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Un allenamento con la fascia cardio, senza partita. [heart]: un valore al secondo, [HeartTrack.t] in secondi di
 * allenamento (le pause non contano e restano buchi). [laps]: secondo di fine di ciascun giro tranne l'ultimo,
 * che finisce con l'allenamento. [cooldown]: battito nei secondi dopo "Fine", per il recupero. [rr]: intervalli
 * tra i battiti (ms) mandati dalla fascia, per l'HRV.
 */
@Serializable
data class WorkoutRecord(
    val id: String,
    val startedAt: Long,
    val endedAt: Long = 0L,
    val durationS: Int = 0,
    val heart: HeartTrack = HeartTrack(),
    val laps: List<Int> = emptyList(),
    val cooldown: List<Int> = emptyList(),
    val rr: List<Int> = emptyList(),
    val strapName: String? = null,
)

/** Calcoli sugli allenamenti: giri, HRV e recupero. */
object WorkoutStats {
    /** Il recupero si misura dopo un minuto. */
    const val RECOVERY_S = 60

    data class Lap(val number: Int, val startS: Int, val endS: Int, val avg: Int?, val max: Int?, val recovery: Int?) {
        val durationS: Int get() = endS - startS
    }

    /** Battito al secondo [sec] (il valore più vicino entro 3 secondi), null se lì la fascia non mandava. */
    fun bpmAt(track: HeartTrack, sec: Int): Int? {
        var best: Int? = null
        var bestD = Int.MAX_VALUE
        for (i in 0 until track.size) {
            val d = abs(track.t[i] - sec)
            if (d < bestD) {
                bestD = d
                best = track.bpm[i]
            }
        }
        return best.takeIf { bestD <= 3 }
    }

    /**
     * Giri con durata, battito medio e massimo e recupero: quanto scende il battito nel minuto dopo la fine del
     * giro. Per l'ultimo giro il minuto dopo è quello dopo "Fine" ([WorkoutRecord.cooldown]).
     */
    fun laps(w: WorkoutRecord): List<Lap> {
        val ends = (w.laps.filter { it in 1 until w.durationS } + w.durationS).distinct().sorted()
        var start = 0
        return ends.mapIndexed { i, end ->
            val values = (0 until w.heart.size).filter { w.heart.t[it] in start until end.coerceAtLeast(start + 1) }.map { w.heart.bpm[it] }
            val last = i == ends.lastIndex
            val atEnd = bpmAt(w.heart, end)
            val after = if (last) w.cooldown.getOrNull(RECOVERY_S - 1) else bpmAt(w.heart, end + RECOVERY_S)
            val lap = Lap(
                number = i + 1, startS = start, endS = end,
                avg = values.takeIf { it.isNotEmpty() }?.let { Math.round(it.average()).toInt() },
                max = values.maxOrNull(),
                recovery = if (atEnd != null && after != null) atEnd - after else null,
            )
            start = end
            lap
        }
    }

    /** Recupero dopo "Fine": battito all'ultimo secondo meno quello un minuto dopo. */
    fun finalRecovery(w: WorkoutRecord): Int? {
        val atEnd = bpmAt(w.heart, w.durationS) ?: return null
        val after = w.cooldown.getOrNull(RECOVERY_S - 1) ?: return null
        return atEnd - after
    }

    /**
     * HRV come RMSSD (ms): radice della media dei quadrati delle differenze tra intervalli consecutivi. Si scartano
     * gli intervalli impossibili e i salti di oltre il 20% (battiti persi o doppi della fascia). null con meno di
     * 30 differenze buone.
     */
    fun rmssd(rr: List<Int>): Double? {
        var sum = 0.0
        var n = 0
        for (i in 1 until rr.size) {
            val a = rr[i - 1]
            val b = rr[i]
            if (a !in 300..2000 || b !in 300..2000) continue
            if (abs(b - a) > a * 0.2) continue
            val d = (b - a).toDouble()
            sum += d * d
            n++
        }
        return if (n >= 30) sqrt(sum / n) else null
    }

    /** CSV secondo per secondo: secondo, battito, giro (con il recupero dopo "Fine" in fondo). */
    fun csv(w: WorkoutRecord): String = buildString {
        append("second,bpm,lap\n")
        val ends = (w.laps.filter { it in 1 until w.durationS } + w.durationS).distinct().sorted()
        for (i in 0 until w.heart.size) {
            val t = w.heart.t[i]
            val lap = ends.indexOfFirst { t < it }.let { if (it < 0) ends.size else it + 1 }
            append("$t,${w.heart.bpm[i]},$lap\n")
        }
        w.cooldown.forEachIndexed { i, bpm -> append("${w.durationS + i + 1},$bpm,recovery\n") }
    }
}
