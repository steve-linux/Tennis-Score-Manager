// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

/** Battito medio e massimo, calorie stimate. */
object HeartStats {
    /** Un valore ogni 5 secondi: 2 ore di partita sono ~1.400 valori per fascia, il salvataggio resta leggero. */
    const val STEP_S = 5

    data class Summary(val avg: Int, val max: Int, val min: Int, val minutes: Int, val kcal: Int?)

    /** null se la fascia non ha mandato niente. */
    fun summary(track: HeartTrack?): Summary? {
        val n = track?.size ?: 0
        if (track == null || n == 0) return null
        val v = track.bpm.take(n)
        val seconds = (0 until n).sumOf { span(track, it) }
        return Summary(
            avg = Math.round(v.average()).toInt(),
            max = v.max(),
            min = v.min(),
            minutes = Math.round(seconds / 60.0).toInt(),
            kcal = track.profile?.let { kcal(track, it) },
        )
    }

    /** Secondi coperti dal valore [i]: fino al successivo, ma un buco (pausa, fascia staccata) non conta. */
    private fun span(track: HeartTrack, i: Int): Int {
        val next = track.t.getOrNull(i + 1)?.takeIf { i + 1 < track.size } ?: return STEP_S
        val gap = next - track.t[i]
        return if (gap in 1..2 * STEP_S) gap else STEP_S
    }

    /**
     * Calorie dal battito con la formula di Keytel et al. (2005, J Sports Sci 23:289), senza VO2max: kJ/min da
     * battito, peso ed età, diversa per uomini e donne. Errore tipico ±20%: è una stima.
     */
    fun kcal(track: HeartTrack, p: HeartProfile): Int {
        var total = 0.0
        for (i in 0 until track.size) total += kcalPerMinute(track.bpm[i], p) * span(track, i) / 60.0
        return Math.round(total).toInt()
    }

    fun kcalPerMinute(bpm: Int, p: HeartProfile): Double {
        val kj = if (p.female) {
            -20.4022 + 0.4472 * bpm - 0.1263 * p.weightKg + 0.074 * p.age
        } else {
            -55.0969 + 0.6309 * bpm + 0.1988 * p.weightKg + 0.2017 * p.age
        }
        // A riposo la formula scende sotto zero: lì non si conta niente.
        return (kj / 4.184).coerceAtLeast(0.0)
    }

    const val ZONES = 5

    /** Battito massimo stimato dall'età (Tanaka et al. 2001): più fedele di "220 − età", soprattutto dopo i 40 anni. */
    fun maxBpm(age: Int): Int = Math.round(208 - 0.7 * age).toInt()

    /**
     * Secondi passati in ciascuna delle 5 zone (50-60, 60-70, 70-80, 80-90, oltre 90% del battito massimo); sotto il
     * 50% conta come zona 1. Vuoto senza dati.
     */
    fun zoneSeconds(track: HeartTrack?, age: Int): List<Int> {
        if (track == null || track.size == 0) return emptyList()
        val max = maxBpm(age).toDouble()
        val out = IntArray(ZONES)
        for (i in 0 until track.size) {
            val zone = ((track.bpm[i] / max - 0.5) * 10).toInt().coerceIn(0, ZONES - 1)
            out[zone] += span(track, i)
        }
        return out.toList()
    }

    /** Età e peso plausibili, altrimenti niente profilo (e niente calorie). */
    fun profile(age: Int?, weightKg: Int?, female: Boolean): HeartProfile? =
        if (age != null && age in 8..99 && weightKg != null && weightKg in 25..200) HeartProfile(age, weightKg, female) else null
}

/** Media delle misure arrivate in un intervallo di [HeartStats.STEP_S] secondi. */
class HeartBucket {
    private var sum = 0
    private var count = 0

    fun add(bpm: Int) {
        sum += bpm
        count++
    }

    /** La media (e si riparte da zero); null se non è arrivato niente. */
    fun take(): Int? {
        if (count == 0) return null
        val avg = Math.round(sum.toDouble() / count).toInt()
        sum = 0
        count = 0
        return avg
    }

    fun clear() {
        sum = 0
        count = 0
    }
}
