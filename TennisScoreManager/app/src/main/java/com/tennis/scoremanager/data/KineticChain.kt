// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import kotlin.math.abs
import kotlin.math.hypot

/**
 * Catena cinetica, in prova: quando ogni parte del corpo arriva alla sua velocità massima prima dell'impatto. Nel colpo
 * ideale l'ordine va dalle gambe al polso (gambe, anche, spalle, gomito, polso), ognuna un po' dopo la precedente. Da un
 * video di lato le rotazioni si vedono male: anche e spalle si stimano da quanto cambia la loro larghezza nell'immagine
 * (girando, la linea si allarga o si stringe). Con 15 fotogrammi al secondo le differenze sotto un fotogramma non si
 * vedono: si tollera un fotogramma.
 */
object KineticChain {
    enum class Segment { LEGS, HIPS, SHOULDERS, ELBOW, WRIST }

    /** [peaks]: ms dall'impatto (negativo = prima) per ogni parte trovata; [outOfOrder]: la prima coppia fuori ordine (dopo, prima). */
    data class Result(val peaks: Map<Segment, Long>, val stepMs: Long, val outOfOrder: Pair<Segment, Segment>?) {
        val inOrder: Boolean get() = outOfOrder == null
    }

    private const val SCORE = ServePose.MIN_SCORE
    private const val BEFORE_MS = 700L
    private const val AFTER_MS = 60L

    fun compute(frames: List<PoseFrame>, contactT: Long, rightHanded: Boolean): Result? {
        val win = frames.filter { it.t in (contactT - BEFORE_MS)..(contactT + AFTER_MS) }.sortedBy { it.t }
        if (win.size < 4) return null
        val stature = StrokeVision.stature(frames) ?: return null
        fun ok(f: PoseFrame, vararg i: Int) = i.all { f.s[it] >= SCORE }
        val sh = if (rightHanded) ServePose.R_SHOULDER else ServePose.L_SHOULDER
        val el = if (rightHanded) ServePose.R_ELBOW else ServePose.L_ELBOW
        val wr = if (rightHanded) ServePose.R_WRIST else ServePose.L_WRIST
        // Una misura per fotogramma (null se non si vede), poi il picco della sua velocità tra due fotogrammi.
        fun knee(f: PoseFrame): Float? = listOfNotNull(
            ServePose.angle(f, ServePose.L_HIP, ServePose.L_KNEE, ServePose.L_ANKLE).takeIf { ok(f, ServePose.L_HIP, ServePose.L_KNEE, ServePose.L_ANKLE) },
            ServePose.angle(f, ServePose.R_HIP, ServePose.R_KNEE, ServePose.R_ANKLE).takeIf { ok(f, ServePose.R_HIP, ServePose.R_KNEE, ServePose.R_ANKLE) },
        ).takeIf { it.isNotEmpty() }?.average()?.toFloat()
        fun width(f: PoseFrame, a: Int, b: Int): Float? = if (ok(f, a, b)) hypot(f.x[a] - f.x[b], f.y[a] - f.y[b]) / stature else null
        fun elbow(f: PoseFrame): Float? = if (ok(f, sh, el, wr)) ServePose.angle(f, sh, el, wr) else null
        /** Picco di velocità: [signed] = conta solo l'aumento (le gambe che si distendono), altrimenti in valore assoluto. */
        fun peak(value: (PoseFrame) -> Float?, signed: Boolean = false): Long? {
            var best = 0f; var at: Long? = null
            for (k in 1 until win.size) {
                val a = win[k - 1]; val b = win[k]
                val va = value(a) ?: continue; val vb = value(b) ?: continue
                val d = (vb - va) / (b.t - a.t).coerceAtLeast(1L)
                val v = if (signed) d else abs(d)
                if (v > best) { best = v; at = (a.t + b.t) / 2 - contactT }
            }
            return at
        }
        fun wristPeak(): Long? {
            var best = 0f; var at: Long? = null
            for (k in 1 until win.size) {
                val a = win[k - 1]; val b = win[k]
                if (!ok(a, wr) || !ok(b, wr)) continue
                val v = hypot(b.x[wr] - a.x[wr], b.y[wr] - a.y[wr]) / (b.t - a.t).coerceAtLeast(1L)
                if (v > best) { best = v; at = (a.t + b.t) / 2 - contactT }
            }
            return at
        }
        val peaks = linkedMapOf<Segment, Long>()
        peak(::knee, signed = true)?.let { peaks[Segment.LEGS] = it }
        peak({ width(it, ServePose.L_HIP, ServePose.R_HIP) })?.let { peaks[Segment.HIPS] = it }
        peak({ width(it, ServePose.L_SHOULDER, ServePose.R_SHOULDER) })?.let { peaks[Segment.SHOULDERS] = it }
        peak(::elbow)?.let { peaks[Segment.ELBOW] = it }
        wristPeak()?.let { peaks[Segment.WRIST] = it }
        if (peaks.size < 4) return null
        val steps = win.zipWithNext { a, b -> b.t - a.t }.sorted()
        val step = steps[steps.size / 2].coerceAtLeast(1L)
        return Result(peaks, step, outOfOrder(peaks, step))
    }

    /** La prima parte che arriva prima di una che dovrebbe precederla (oltre [tolerance] ms): (questa, quella). */
    fun outOfOrder(peaks: Map<Segment, Long>, tolerance: Long): Pair<Segment, Segment>? {
        val seen = Segment.entries.filter { it in peaks }
        for (i in seen.indices) for (j in i + 1 until seen.size) {
            if (peaks.getValue(seen[j]) < peaks.getValue(seen[i]) - tolerance) return seen[j] to seen[i]
        }
        return null
    }
}
