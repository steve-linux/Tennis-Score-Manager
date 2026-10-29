// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * I 17 punti del corpo di MoveNet in un fotogramma del servizio. [t] in ms rispetto all'istante del servizio (negativo
 * prima); [x] e [y] in frazioni dell'altezza dell'immagine (y in giù), così le due misure hanno la stessa scala;
 * [s] la sicurezza di ciascun punto (0-1).
 */
@Serializable
data class PoseFrame(val t: Long, val x: List<Float>, val y: List<Float>, val s: List<Float>)

/**
 * Misure del servizio nei tre momenti chiave: il caricamento ("trofeo": braccio del lancio in alto), il ginocchio più
 * piegato e l'impatto (polso del braccio che colpisce nel punto più alto).
 */
@Serializable
data class ServeMetrics(
    /** Angolo del ginocchio più piegato (anca-ginocchio-caviglia): 180° gamba tesa, più basso = più piegato. */
    val kneeMinDeg: Float,
    /** Angolo del gomito all'impatto (spalla-gomito-polso): vicino a 180° = braccio disteso. */
    val elbowContactDeg: Float,
    /** Altezza del polso all'impatto in rapporto alla statura (dai piedi). */
    val contactHeight: Float,
    /** Braccio del lancio al trofeo, gradi dalla verticale: 0 = dritto in su. */
    val tossArmDeg: Float,
    /** Inclinazione delle spalle al trofeo: positiva se la spalla del lancio è più alta. */
    val shoulderTiltDeg: Float,
    val trophyToContactMs: Long,
    val kneeToContactMs: Long,
    /** Sicurezza media dei punti usati (0-1): sotto 0,4 le misure valgono poco. */
    val quality: Float,
    val trophyT: Long,
    val kneeT: Long,
    val contactT: Long,
    /** true = colpisce col braccio destro. */
    val rightHanded: Boolean,
) {
    val reliable: Boolean get() = quality >= ServePose.MIN_QUALITY
}

/** Analisi di un servizio: fotogrammi e misure (null se il corpo non si vede abbastanza). */
@Serializable
data class ServePoseResult(
    val frames: List<PoseFrame>,
    val metrics: ServeMetrics?,
    /** Persone diverse viste nell'inquadratura (si segue chi serve); 0 = nessuna. */
    val people: Int = 1,
    /** Dritto, rovescio e volée: le loro misure (per il servizio c'è [metrics]). */
    val ground: GroundMetrics? = null,
)

/**
 * Misure di dritto, rovescio e volée, ripresi di lato: i tre momenti sono la preparazione (polso più indietro), l'impatto
 * (polso più veloce) e il finale (polso più in alto dopo l'impatto). Le distanze sono in frazioni della statura,
 * "davanti" e "dietro" rispetto al centro del corpo nella direzione del colpo.
 */
@Serializable
data class GroundMetrics(
    /** Ginocchio più piegato tra preparazione e impatto (180° = gamba tesa). */
    val kneeMinDeg: Float,
    /** Gomito all'impatto (180° = braccio disteso). */
    val elbowContactDeg: Float,
    /** Altezza del polso all'impatto, sulla statura (circa 0,5 = all'altezza della vita). */
    val contactHeight: Float,
    /** Quanto davanti al corpo si colpisce (positivo = davanti). */
    val contactInFront: Float,
    /** Quanto indietro va il polso nella preparazione (ampiezza dell'apertura). */
    val backswing: Float,
    /** Altezza del polso nel finale, sulla statura. */
    val finishHeight: Float,
    val prepToContactMs: Long,
    val quality: Float,
    val prepT: Long,
    val contactT: Long,
    val finishT: Long,
    val rightHanded: Boolean,
) {
    val reliable: Boolean get() = quality >= ServePose.MIN_QUALITY
}

object ServePose {
    const val NOSE = 0
    const val L_SHOULDER = 5
    const val R_SHOULDER = 6
    const val L_ELBOW = 7
    const val R_ELBOW = 8
    const val L_WRIST = 9
    const val R_WRIST = 10
    const val L_HIP = 11
    const val R_HIP = 12
    const val L_KNEE = 13
    const val R_KNEE = 14
    const val L_ANKLE = 15
    const val R_ANKLE = 16

    /** Coppie di punti da unire per disegnare lo scheletro. */
    val BONES = listOf(
        L_SHOULDER to R_SHOULDER, L_SHOULDER to L_ELBOW, L_ELBOW to L_WRIST, R_SHOULDER to R_ELBOW, R_ELBOW to R_WRIST,
        L_SHOULDER to L_HIP, R_SHOULDER to R_HIP, L_HIP to R_HIP, L_HIP to L_KNEE, L_KNEE to L_ANKLE, R_HIP to R_KNEE, R_KNEE to R_ANKLE,
    )

    const val MIN_SCORE = 0.3f
    const val MIN_QUALITY = 0.4f
    /** Dal naso alla caviglia c'è circa l'89 % della statura. */
    private const val NOSE_TO_ANKLE = 0.89f

    private fun ok(f: PoseFrame, vararg i: Int) = i.all { f.s[it] >= MIN_SCORE }

    /** Angolo in [b] tra i segmenti b→a e b→c, in gradi. */
    fun angle(f: PoseFrame, a: Int, b: Int, c: Int): Float {
        val ux = f.x[a] - f.x[b]; val uy = f.y[a] - f.y[b]
        val vx = f.x[c] - f.x[b]; val vy = f.y[c] - f.y[b]
        val n = sqrt(ux * ux + uy * uy) * sqrt(vx * vx + vy * vy)
        if (n < 1e-6f) return 180f
        return Math.toDegrees(acos(((ux * vx + uy * vy) / n).coerceIn(-1f, 1f).toDouble())).toFloat()
    }

    private fun median(v: List<Float>): Float? = v.sorted().takeIf { it.isNotEmpty() }?.let { it[it.size / 2] }

    /** Le misure dai fotogrammi in ordine di tempo; null se non si trovano i tre momenti. */
    fun metrics(frames: List<PoseFrame>): ServeMetrics? {
        if (frames.size < 5) return null
        // Braccio che colpisce: il polso più veloce (lo swing); a pari dati, quello che arriva più in alto. L'altro è il
        // braccio del lancio, che al trofeo può salire quasi altrettanto.
        fun highest(w: Int) = frames.filter { ok(it, w) }.minOfOrNull { it.y[w] } ?: Float.MAX_VALUE
        fun speeds(w: Int): List<Pair<Int, Float>> = (1 until frames.size).mapNotNull { i ->
            val a = frames[i - 1]; val b = frames[i]
            if (!ok(a, w) || !ok(b, w) || b.t <= a.t) return@mapNotNull null
            val dx = b.x[w] - a.x[w]; val dy = b.y[w] - a.y[w]
            i to sqrt(dx * dx + dy * dy) / (b.t - a.t)
        }
        val vr = speeds(R_WRIST).maxOfOrNull { it.second }
        val vl = speeds(L_WRIST).maxOfOrNull { it.second }
        val right = if (vr != null && vl != null && abs(vr - vl) > 0.1f * maxOf(vr, vl)) vr > vl else highest(R_WRIST) <= highest(L_WRIST)
        val hitW = if (right) R_WRIST else L_WRIST
        val hitE = if (right) R_ELBOW else L_ELBOW
        val hitS = if (right) R_SHOULDER else L_SHOULDER
        val tossW = if (right) L_WRIST else R_WRIST
        val tossS = if (right) L_SHOULDER else R_SHOULDER

        // Impatto: il punto più alto del polso che colpisce vicino al momento in cui va più veloce.
        val fastest = speeds(hitW).maxByOrNull { it.second }?.first
        val near = if (fastest != null) frames.indices.filter { abs(frames[it].t - frames[fastest].t) <= 250 } else frames.indices.toList()
        val ci = near.filter { ok(frames[it], hitW) }.minByOrNull { frames[it].y[hitW] } ?: return null
        val contact = frames[ci]
        // Trofeo: il braccio del lancio più in alto, almeno 150 ms prima dell'impatto (e non più di 2 s).
        val ti = (0 until ci).filter { frames[it].t in (contact.t - 2_000)..(contact.t - 150) && ok(frames[it], tossW, tossS) }
            .minByOrNull { frames[it].y[tossW] } ?: return null
        val trophy = frames[ti]
        // Ginocchio: il più piegato tra le due gambe, dal trofeo (un po' prima) all'impatto.
        fun knee(f: PoseFrame): Float? = listOfNotNull(
            angle(f, L_HIP, L_KNEE, L_ANKLE).takeIf { ok(f, L_HIP, L_KNEE, L_ANKLE) },
            angle(f, R_HIP, R_KNEE, R_ANKLE).takeIf { ok(f, R_HIP, R_KNEE, R_ANKLE) },
        ).minOrNull()
        val from = frames.indexOfFirst { it.t >= trophy.t - 800 }.coerceAtLeast(0)
        val ki = (from..ci).filter { knee(frames[it]) != null }.minByOrNull { knee(frames[it])!! } ?: return null
        val kneeFrame = frames[ki]

        // Statura e terreno dai fotogrammi prima del trofeo (fermi, coi piedi a terra).
        val before = frames.subList(0, ti + 1)
        val ankle = { f: PoseFrame -> listOfNotNull(f.y[L_ANKLE].takeIf { ok(f, L_ANKLE) }, f.y[R_ANKLE].takeIf { ok(f, R_ANKLE) }).maxOrNull() }
        val ground = median(before.mapNotNull(ankle)) ?: return null
        val noseToAnkle = median(before.filter { ok(it, NOSE) }.mapNotNull { f -> ankle(f)?.let { it - f.y[NOSE] } }) ?: return null
        val stature = noseToAnkle / NOSE_TO_ANKLE
        if (stature <= 0.05f) return null

        val elbow = if (ok(contact, hitS, hitE, hitW)) angle(contact, hitS, hitE, hitW) else return null
        val dx = trophy.x[tossW] - trophy.x[tossS]
        val dy = trophy.y[tossW] - trophy.y[tossS]
        val tossArm = Math.toDegrees(atan2(abs(dx), -dy).toDouble()).toFloat()
        val tilt = if (ok(trophy, hitS, tossS)) {
            Math.toDegrees(atan2(trophy.y[hitS] - trophy.y[tossS], abs(trophy.x[hitS] - trophy.x[tossS]) + 1e-6f).toDouble()).toFloat()
        } else 0f
        val used = listOf(
            contact.s[hitW], contact.s[hitE], contact.s[hitS], trophy.s[tossW], trophy.s[tossS],
        ) + listOf(L_HIP, L_KNEE, L_ANKLE, R_HIP, R_KNEE, R_ANKLE).map { kneeFrame.s[it] }
        return ServeMetrics(
            kneeMinDeg = knee(kneeFrame)!!,
            elbowContactDeg = elbow,
            contactHeight = (ground - contact.y[hitW]) / stature,
            tossArmDeg = tossArm,
            shoulderTiltDeg = tilt,
            trophyToContactMs = contact.t - trophy.t,
            kneeToContactMs = contact.t - kneeFrame.t,
            quality = used.average().toFloat(),
            trophyT = trophy.t,
            kneeT = kneeFrame.t,
            contactT = contact.t,
            rightHanded = right,
        )
    }

    /** Dritto, rovescio e volée: le misure dai fotogrammi in ordine di tempo; null se non si trovano i tre momenti. */
    fun groundMetrics(frames: List<PoseFrame>): GroundMetrics? {
        if (frames.size < 5) return null
        fun speeds(w: Int): List<Pair<Int, Float>> = (1 until frames.size).mapNotNull { i ->
            val a = frames[i - 1]; val b = frames[i]
            if (!ok(a, w) || !ok(b, w) || b.t <= a.t) return@mapNotNull null
            val dx = b.x[w] - a.x[w]; val dy = b.y[w] - a.y[w]
            i to sqrt(dx * dx + dy * dy) / (b.t - a.t)
        }
        // Braccio che colpisce: il polso più veloce; l'impatto è il momento in cui va più veloce.
        val sr = speeds(R_WRIST); val sl = speeds(L_WRIST)
        val vr = sr.maxOfOrNull { it.second } ?: 0f
        val vl = sl.maxOfOrNull { it.second } ?: 0f
        if (vr == 0f && vl == 0f) return null
        val right = vr >= vl
        val hitW = if (right) R_WRIST else L_WRIST
        val hitE = if (right) R_ELBOW else L_ELBOW
        val hitS = if (right) R_SHOULDER else L_SHOULDER
        val ci = (if (right) sr else sl).maxBy { it.second }.first
        val contact = frames[ci]
        // Direzione del colpo: dove va il polso all'impatto (in orizzontale).
        val dir = (frames[ci].x[hitW] - frames[ci - 1].x[hitW]).let { if (it >= 0) 1f else -1f }
        fun body(f: PoseFrame): Float? {
            val pts = listOf(L_HIP, R_HIP, L_SHOULDER, R_SHOULDER).filter { f.s[it] >= MIN_SCORE }
            return if (pts.size < 2) null else pts.map { f.x[it] }.average().toFloat()
        }
        // Preparazione: il polso più indietro (contro la direzione del colpo) tra 1,5 s e 0,15 s prima dell'impatto.
        val pi = (0 until ci).filter { frames[it].t in (contact.t - 1_500)..(contact.t - 150) && ok(frames[it], hitW) && body(frames[it]) != null }
            .minByOrNull { dir * (frames[it].x[hitW] - body(frames[it])!!) } ?: return null
        val prep = frames[pi]
        // Finale: il polso più in alto dopo l'impatto (entro 0,9 s).
        val fi = (ci + 1 until frames.size).filter { frames[it].t in (contact.t + 100)..(contact.t + 900) && ok(frames[it], hitW) }
            .minByOrNull { frames[it].y[hitW] } ?: return null
        val finish = frames[fi]
        fun knee(f: PoseFrame): Float? = listOfNotNull(
            angle(f, L_HIP, L_KNEE, L_ANKLE).takeIf { ok(f, L_HIP, L_KNEE, L_ANKLE) },
            angle(f, R_HIP, R_KNEE, R_ANKLE).takeIf { ok(f, R_HIP, R_KNEE, R_ANKLE) },
        ).minOrNull()
        val ki = (frames.indexOfFirst { it.t >= prep.t - 300 }.coerceAtLeast(0)..ci).filter { knee(frames[it]) != null }
            .minByOrNull { knee(frames[it])!! } ?: return null
        // Statura e terreno da tutti i fotogrammi (chi colpisce da fondo campo si muove, ma resta in piedi).
        val ankle = { f: PoseFrame -> listOfNotNull(f.y[L_ANKLE].takeIf { ok(f, L_ANKLE) }, f.y[R_ANKLE].takeIf { ok(f, R_ANKLE) }).maxOrNull() }
        val ground = median(frames.mapNotNull(ankle)) ?: return null
        val noseToAnkle = median(frames.filter { ok(it, NOSE) }.mapNotNull { f -> ankle(f)?.let { it - f.y[NOSE] } }) ?: return null
        val stature = noseToAnkle / NOSE_TO_ANKLE
        if (stature <= 0.05f) return null
        val elbow = if (ok(contact, hitS, hitE, hitW)) angle(contact, hitS, hitE, hitW) else return null
        val bc = body(contact) ?: return null
        val used = listOf(contact.s[hitW], contact.s[hitE], contact.s[hitS], prep.s[hitW], finish.s[hitW]) +
            listOf(L_HIP, L_KNEE, L_ANKLE, R_HIP, R_KNEE, R_ANKLE).map { frames[ki].s[it] }
        return GroundMetrics(
            kneeMinDeg = knee(frames[ki])!!,
            elbowContactDeg = elbow,
            contactHeight = (ground - contact.y[hitW]) / stature,
            contactInFront = dir * (contact.x[hitW] - bc) / stature,
            backswing = -dir * (prep.x[hitW] - body(prep)!!) / stature,
            finishHeight = (ground - finish.y[hitW]) / stature,
            prepToContactMs = contact.t - prep.t,
            quality = used.average().toFloat(),
            prepT = prep.t, contactT = contact.t, finishT = finish.t,
            rightHanded = right,
        )
    }

    data class GroundMean(
        val n: Int, val kneeMinDeg: Float, val elbowContactDeg: Float, val contactHeight: Float,
        val contactInFront: Float, val backswing: Float, val finishHeight: Float, val prepToContactMs: Long,
    )

    fun groundMean(list: List<GroundMetrics>): GroundMean? {
        val r = list.filter { it.reliable }.takeIf { it.isNotEmpty() } ?: return null
        return GroundMean(
            r.size, r.map { it.kneeMinDeg }.average().toFloat(), r.map { it.elbowContactDeg }.average().toFloat(),
            r.map { it.contactHeight }.average().toFloat(), r.map { it.contactInFront }.average().toFloat(),
            r.map { it.backswing }.average().toFloat(), r.map { it.finishHeight }.average().toFloat(),
            r.map { it.prepToContactMs }.average().toLong(),
        )
    }

    /** Media di ogni misura sui servizi affidabili (null se nessuno). */
    data class Mean(
        val n: Int, val kneeMinDeg: Float, val elbowContactDeg: Float, val contactHeight: Float,
        val tossArmDeg: Float, val shoulderTiltDeg: Float, val trophyToContactMs: Long,
    )

    fun mean(list: List<ServeMetrics>): Mean? {
        val r = list.filter { it.reliable }.takeIf { it.isNotEmpty() } ?: return null
        return Mean(
            r.size, r.map { it.kneeMinDeg }.average().toFloat(), r.map { it.elbowContactDeg }.average().toFloat(),
            r.map { it.contactHeight }.average().toFloat(), r.map { it.tossArmDeg }.average().toFloat(),
            r.map { it.shoulderTiltDeg }.average().toFloat(), r.map { it.trophyToContactMs }.average().toLong(),
        )
    }
}
