// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import kotlin.math.hypot

/** Il colpo visto nel video; [sure] false quando gli indizi sono deboli (allora non si propone di spostarlo). */
data class StrokeGuess(val stroke: TrainedStroke, val sure: Boolean)

/**
 * Riconoscimento del colpo dal solo video, dai punti del corpo già trovati con l'analisi (senza braccialetto). Indizi
 * semplici e prudenti: nel servizio tutti e due i polsi vanno sopra la testa (lancio e colpo); nella volée il polso fa
 * poca strada; nel rovescio, quando la racchetta è più indietro, le due mani sono vicine (a due mani, o l'altra mano
 * che regge il cuore della racchetta), nel dritto l'altro braccio è lontano, steso in avanti. Le misure sono in
 * frazioni della statura, così non contano la distanza dalla telecamera e la risoluzione.
 */
object StrokeVision {
    private const val SCORE = ServePose.MIN_SCORE

    fun guess(frames: List<PoseFrame>): StrokeGuess? {
        val stature = stature(frames) ?: return null
        fun seen(f: PoseFrame, i: Int) = f.s[i] >= SCORE
        // Il polso che colpisce è quello che va più veloce.
        fun peak(w: Int): Pair<Float, Int> {
            var best = 0f; var at = -1
            for (k in 1 until frames.size) {
                val a = frames[k - 1]; val b = frames[k]
                if (!seen(a, w) || !seen(b, w)) continue
                val dt = (b.t - a.t).coerceAtLeast(1L).toFloat()
                val v = hypot(b.x[w] - a.x[w], b.y[w] - a.y[w]) / dt
                if (v > best) { best = v; at = k }
            }
            return best to at
        }
        val (vl, kl) = peak(ServePose.L_WRIST)
        val (vr, kr) = peak(ServePose.R_WRIST)
        if (kl < 0 && kr < 0) return null
        val hit = if (vr >= vl) ServePose.R_WRIST else ServePose.L_WRIST
        val other = if (hit == ServePose.R_WRIST) ServePose.L_WRIST else ServePose.R_WRIST
        val peakAt = if (hit == ServePose.R_WRIST) kr else kl
        // Quanto sopra la testa (naso) arriva un polso, in statura; negativo = sotto.
        fun up(w: Int) = frames.filter { seen(it, w) && seen(it, ServePose.NOSE) }.maxOfOrNull { (it.y[ServePose.NOSE] - it.y[w]) / stature } ?: -1f
        val hitUp = up(hit); val otherUp = up(other)
        if (hitUp > 0.15f && otherUp > 0.08f) return StrokeGuess(TrainedStroke.SERVE, hitUp > 0.25f && otherUp > 0.12f)
        // Strada orizzontale del polso che colpisce.
        val xs = frames.filter { seen(it, hit) }.map { it.x[hit] }
        if (xs.size < 3) return null
        val amp = (xs.max() - xs.min()) / stature
        if (amp < 0.45f) return StrokeGuess(TrainedStroke.VOLLEY, amp < 0.35f)
        // La racchetta più indietro: prima del picco, il polso più lontano dal centro delle anche.
        val before = frames.take(peakAt.coerceAtLeast(1)).filter { seen(it, hit) && seen(it, other) && seen(it, ServePose.L_HIP) && seen(it, ServePose.R_HIP) }
        val back = before.maxByOrNull { kotlin.math.abs(it.x[hit] - (it.x[ServePose.L_HIP] + it.x[ServePose.R_HIP]) / 2) } ?: return null
        val gap = hypot(back.x[hit] - back.x[other], back.y[hit] - back.y[other]) / stature
        return when {
            gap < 0.18f -> StrokeGuess(TrainedStroke.BACKHAND, gap < 0.12f)
            gap > 0.25f -> StrokeGuess(TrainedStroke.FOREHAND, gap > 0.32f)
            else -> null
        }
    }

    /** Statura come nell'analisi: naso-caviglia diviso 0,89, mediana sui fotogrammi in cui si vedono. */
    fun stature(frames: List<PoseFrame>): Float? = frames.mapNotNull { g ->
        val ankle = listOfNotNull(g.y[ServePose.L_ANKLE].takeIf { g.s[ServePose.L_ANKLE] >= SCORE }, g.y[ServePose.R_ANKLE].takeIf { g.s[ServePose.R_ANKLE] >= SCORE }).maxOrNull()
        if (ankle == null || g.s[ServePose.NOSE] < SCORE) null else (ankle - g.y[ServePose.NOSE]) / 0.89f
    }.sorted().let { if (it.isEmpty()) null else it[it.size / 2] }?.takeIf { it > 0.05f }
}
