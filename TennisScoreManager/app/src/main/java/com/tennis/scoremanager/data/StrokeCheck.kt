// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import kotlin.math.abs
import kotlin.math.atan2

/**
 * La postura all'impatto, dai punti del corpo già salvati con l'analisi: [trunkDeg] quanto il busto è inclinato dalla
 * verticale (anche-spalle), [shouldersDeg] quanto la linea delle spalle è inclinata dall'orizzontale, [stance] la
 * distanza tra le caviglie in rapporto alla statura. Stime da un video di lato: null quando i punti non si vedono.
 */
data class Posture(val trunkDeg: Float?, val shouldersDeg: Float?, val stance: Float?)

/** Un possibile problema tecnico: da confermare col maestro. [value] e [ref] per dire di quanto (o null). */
data class StrokeFlag(val kind: Kind, val value: Float? = null, val ref: Float? = null) {
    enum class Kind {
        // Dalle misure del colpo, con soglie prudenti.
        ELBOW_BENT, KNEES_STIFF, CONTACT_LOW, TOSS_TILTED, CONTACT_BEHIND, PREP_SHORT, FINISH_LOW,
        // Rispetto al riferimento (i migliori colpi, o i colpi dentro): diverso dal solito.
        ELBOW_LESS, KNEES_LESS, CONTACT_LOWER, CONTACT_LATER, PREP_SHORTER, FINISH_LOWER,
    }
}

object StrokeCheck {
    private const val SCORE = ServePose.MIN_SCORE

    /** La postura nel fotogramma più vicino all'impatto [contactT] (ms come nei fotogrammi). */
    fun posture(frames: List<PoseFrame>, contactT: Long): Posture? {
        val f = ServePose.frameAt(frames, contactT) ?: return null
        fun ok(vararg i: Int) = i.all { f.s[it] >= SCORE }
        // Statura come nell'analisi: naso-caviglia diviso 0,89, sui fotogrammi in cui si vedono.
        val stature = frames.mapNotNull { g ->
            val ankle = listOfNotNull(g.y[ServePose.L_ANKLE].takeIf { g.s[ServePose.L_ANKLE] >= SCORE }, g.y[ServePose.R_ANKLE].takeIf { g.s[ServePose.R_ANKLE] >= SCORE }).maxOrNull()
            if (ankle == null || g.s[ServePose.NOSE] < SCORE) null else (ankle - g.y[ServePose.NOSE]) / 0.89f
        }.sorted().let { if (it.isEmpty()) null else it[it.size / 2] }?.takeIf { it > 0.05f }
        val trunk = if (ok(ServePose.L_SHOULDER, ServePose.R_SHOULDER, ServePose.L_HIP, ServePose.R_HIP)) {
            val sx = (f.x[ServePose.L_SHOULDER] + f.x[ServePose.R_SHOULDER]) / 2; val sy = (f.y[ServePose.L_SHOULDER] + f.y[ServePose.R_SHOULDER]) / 2
            val hx = (f.x[ServePose.L_HIP] + f.x[ServePose.R_HIP]) / 2; val hy = (f.y[ServePose.L_HIP] + f.y[ServePose.R_HIP]) / 2
            Math.toDegrees(atan2(abs(sx - hx).toDouble(), (hy - sy).toDouble())).toFloat()
        } else null
        // La linea delle spalle solo se le spalle sono abbastanza separate nel video (di profilo si sovrappongono).
        val shoulders = if (ok(ServePose.L_SHOULDER, ServePose.R_SHOULDER) && stature != null) {
            val dx = abs(f.x[ServePose.L_SHOULDER] - f.x[ServePose.R_SHOULDER]); val dy = abs(f.y[ServePose.L_SHOULDER] - f.y[ServePose.R_SHOULDER])
            if (dx < 0.06f * stature) null else Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        } else null
        val stance = if (ok(ServePose.L_ANKLE, ServePose.R_ANKLE) && stature != null) abs(f.x[ServePose.L_ANKLE] - f.x[ServePose.R_ANKLE]) / stature else null
        if (trunk == null && shoulders == null && stance == null) return null
        return Posture(trunk, shoulders, stance)
    }

    /** Servizio: soglie prudenti e, con un riferimento, quello che è diverso dal solito. Solo misure affidabili. */
    fun serve(m: ServeMetrics, ref: ServePose.Mean?): List<StrokeFlag> {
        if (!m.reliable) return emptyList()
        val out = ArrayList<StrokeFlag>()
        if (m.elbowContactDeg < 150f) out += StrokeFlag(StrokeFlag.Kind.ELBOW_BENT, m.elbowContactDeg)
        if (m.kneeMinDeg > 165f) out += StrokeFlag(StrokeFlag.Kind.KNEES_STIFF, m.kneeMinDeg)
        if (m.contactHeight < 1.12f) out += StrokeFlag(StrokeFlag.Kind.CONTACT_LOW, m.contactHeight)
        if (m.tossArmDeg > 35f) out += StrokeFlag(StrokeFlag.Kind.TOSS_TILTED, m.tossArmDeg)
        if (ref != null) {
            if (m.elbowContactDeg < ref.elbowContactDeg - 15f && StrokeFlag.Kind.ELBOW_BENT !in out.map { it.kind }) out += StrokeFlag(StrokeFlag.Kind.ELBOW_LESS, m.elbowContactDeg, ref.elbowContactDeg)
            if (m.kneeMinDeg > ref.kneeMinDeg + 15f && StrokeFlag.Kind.KNEES_STIFF !in out.map { it.kind }) out += StrokeFlag(StrokeFlag.Kind.KNEES_LESS, m.kneeMinDeg, ref.kneeMinDeg)
            if (m.contactHeight < ref.contactHeight - 0.06f && StrokeFlag.Kind.CONTACT_LOW !in out.map { it.kind }) out += StrokeFlag(StrokeFlag.Kind.CONTACT_LOWER, m.contactHeight, ref.contactHeight)
        }
        return out
    }

    /** Dritto, rovescio e volée: come [serve]. Nella volée il finale non conta (si ferma presto apposta). */
    fun ground(g: GroundMetrics, ref: ServePose.GroundMean?, volley: Boolean = false): List<StrokeFlag> {
        if (!g.reliable) return emptyList()
        val out = ArrayList<StrokeFlag>()
        if (g.contactInFront < 0f) out += StrokeFlag(StrokeFlag.Kind.CONTACT_BEHIND, g.contactInFront)
        if (!volley && g.backswing < 0.15f) out += StrokeFlag(StrokeFlag.Kind.PREP_SHORT, g.backswing)
        if (!volley && g.finishHeight < 0.75f) out += StrokeFlag(StrokeFlag.Kind.FINISH_LOW, g.finishHeight)
        g.kneeMinDeg?.let { if (it > 165f) out += StrokeFlag(StrokeFlag.Kind.KNEES_STIFF, it) }
        if (ref != null) {
            val has = { k: StrokeFlag.Kind -> out.any { it.kind == k } }
            if (g.contactInFront < ref.contactInFront - 0.08f && !has(StrokeFlag.Kind.CONTACT_BEHIND)) out += StrokeFlag(StrokeFlag.Kind.CONTACT_LATER, g.contactInFront, ref.contactInFront)
            if (!volley && ref.backswing > 0f && g.backswing < 0.7f * ref.backswing && !has(StrokeFlag.Kind.PREP_SHORT)) out += StrokeFlag(StrokeFlag.Kind.PREP_SHORTER, g.backswing, ref.backswing)
            if (!volley && g.finishHeight < ref.finishHeight - 0.12f && !has(StrokeFlag.Kind.FINISH_LOW)) out += StrokeFlag(StrokeFlag.Kind.FINISH_LOWER, g.finishHeight, ref.finishHeight)
            val k = g.kneeMinDeg; val rk = ref.kneeMinDeg
            if (k != null && rk != null && k > rk + 15f && !has(StrokeFlag.Kind.KNEES_STIFF)) out += StrokeFlag(StrokeFlag.Kind.KNEES_LESS, k, rk)
            if (g.contactHeight < ref.contactHeight - 0.1f) out += StrokeFlag(StrokeFlag.Kind.CONTACT_LOWER, g.contactHeight, ref.contactHeight)
        }
        return out
    }

    /**
     * Le segnalazioni che tornano nei colpi fuori più che in quelli dentro: tipo, quanti fuori ce l'hanno, quanti fuori
     * in tutto. Almeno due colpi fuori e più frequente che nei dentro; dalle più frequenti.
     */
    fun recurring(outs: List<List<StrokeFlag>>, ins: List<List<StrokeFlag>>): List<Triple<StrokeFlag.Kind, Int, Int>> {
        if (outs.isEmpty()) return emptyList()
        // "Impatto dietro al corpo" e "più indietro del solito" sono lo stesso problema: si contano insieme.
        fun family(k: StrokeFlag.Kind) = when (k) {
            StrokeFlag.Kind.ELBOW_BENT -> StrokeFlag.Kind.ELBOW_LESS
            StrokeFlag.Kind.KNEES_STIFF -> StrokeFlag.Kind.KNEES_LESS
            StrokeFlag.Kind.CONTACT_LOW -> StrokeFlag.Kind.CONTACT_LOWER
            StrokeFlag.Kind.CONTACT_BEHIND -> StrokeFlag.Kind.CONTACT_LATER
            StrokeFlag.Kind.PREP_SHORT -> StrokeFlag.Kind.PREP_SHORTER
            StrokeFlag.Kind.FINISH_LOW -> StrokeFlag.Kind.FINISH_LOWER
            else -> k
        }
        fun has(f: List<StrokeFlag>, fam: StrokeFlag.Kind) = f.any { family(it.kind) == fam }
        fun share(list: List<List<StrokeFlag>>, fam: StrokeFlag.Kind) = if (list.isEmpty()) 0f else list.count { has(it, fam) }.toFloat() / list.size
        return StrokeFlag.Kind.entries.map(::family).distinct().mapNotNull { fam ->
            val n = outs.count { has(it, fam) }
            if (n < 2 || share(outs, fam) <= share(ins, fam)) return@mapNotNull null
            // Il nome è quello che compare più spesso nei colpi fuori (senza riferimento c'è solo quello assoluto).
            val name = outs.flatMap { f -> f.map { it.kind }.filter { family(it) == fam }.distinct() }.groupingBy { it }.eachCount().maxByOrNull { it.value }!!.key
            Triple(name, n, outs.size)
        }.sortedByDescending { it.second }
    }
}
