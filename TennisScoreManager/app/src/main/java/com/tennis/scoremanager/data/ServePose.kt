// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.hypot
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
    /** Ginocchio più piegato tra preparazione e impatto (180° = gamba tesa); null se le gambe non si vedono. */
    val kneeMinDeg: Float?,
    /** Gomito all'impatto (180° = braccio disteso); null se il gomito non si vede. */
    val elbowContactDeg: Float?,
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

    /** Polsi coperti dal corpo o mossi: la sicurezza è bassa ma la posizione di solito è giusta. */
    private const val WRIST_SCORE = 0.15f

    /** Contatto atteso col colpo riconosciuto dal braccialetto: arriva circa 0,4 s dopo il picco (la finestra che aspetta). */
    val DETECTED_CONTACT = -900L..-150L

    /**
     * Traiettoria del polso che colpisce, ripulita: [p] in lunghezze del busto rispetto al centro del busto (così non
     * contano i passi né lo zoom), null dove non si sa; [seg] numera i tratti senza salti (stacchi, altra persona).
     */
    private class Track(val p: Array<FloatArray?>, val seg: IntArray, val swapped: BooleanArray, val center: Array<FloatArray?>, val scale: Float)

    /**
     * Le due tracce dei polsi. MoveNet visto di lato scambia spesso il polso sinistro col destro (uno è dietro il corpo):
     * si tiene quello che continua il movimento di prima. Le punte isolate (va e torna in un fotogramma) si tolgono e i
     * buchi fino a 3 fotogrammi si riempiono.
     */
    private fun wristTracks(frames: List<PoseFrame>): Pair<Track, Track>? {
        val n = frames.size
        fun torso(f: PoseFrame): FloatArray? {
            val sh = listOf(L_SHOULDER, R_SHOULDER).filter { f.s[it] >= MIN_SCORE }
            val hp = listOf(L_HIP, R_HIP).filter { f.s[it] >= MIN_SCORE }
            if (sh.isEmpty() || hp.isEmpty()) return null
            val sx = sh.map { f.x[it] }.average().toFloat(); val sy = sh.map { f.y[it] }.average().toFloat()
            val hx = hp.map { f.x[it] }.average().toFloat(); val hy = hp.map { f.y[it] }.average().toFloat()
            val len = hypot(sx - hx, sy - hy)
            return if (len < 1e-3f) null else floatArrayOf((sx + hx) / 2, (sy + hy) / 2, len)
        }
        val tor = frames.map(::torso)
        val scale = median(tor.mapNotNull { it?.get(2) }) ?: return null
        // Statura apparente (dal naso alla caviglia più bassa): in un fotogramma cambia di poco, se non cambia lo zoom.
        fun height(f: PoseFrame): Float? {
            if (!ok(f, NOSE)) return null
            val ank = listOfNotNull(f.y[L_ANKLE].takeIf { ok(f, L_ANKLE) }, f.y[R_ANKLE].takeIf { ok(f, R_ANKLE) }).maxOrNull() ?: return null
            return (ank - f.y[NOSE]).takeIf { it > 1e-3f }
        }
        // Tratti: un salto del busto, o busto e statura che cambiano insieme di colpo (zoom, stacco, un'altra persona),
        // è un'altra inquadratura.
        val seg = IntArray(n)
        var cur = 0
        var last: FloatArray? = null
        var lastH: Float? = null
        for (i in 0 until n) {
            val t = tor[i]
            val h = height(frames[i])
            if (t != null && last != null) {
                val jump = hypot(t[0] - last[0], t[1] - last[1]) > 0.8f * scale || abs(t[2] - last[2]) > 0.35f * last[2]
                val dl = (t[2] - last[2]) / last[2]
                val dh = if (h != null && lastH != null) (h - lastH) / lastH else 0f
                val zoom = abs(dl) > 0.12f && abs(dh) > 0.08f && dl * dh > 0
                if (jump || zoom) cur++
            }
            if (t != null) { last = t; lastH = h }
            seg[i] = cur
        }
        // Centro del busto; dove non si vede, quello di un fotogramma vicino (al massimo due più in là, nello stesso tratto).
        val center = Array(n) { i ->
            (tor[i] ?: (maxOf(0, i - 2)..minOf(n - 1, i + 2)).filter { tor[it] != null && seg[it] == seg[i] }.minByOrNull { abs(it - i) }?.let { tor[it] })
                ?.let { floatArrayOf(it[0], it[1]) }
        }
        val a = arrayOfNulls<FloatArray>(n); val b = arrayOfNulls<FloatArray>(n)
        val swapped = BooleanArray(n)
        var pa: FloatArray? = null; var pb: FloatArray? = null
        fun d(u: FloatArray, v: FloatArray) = hypot(u[0] - v[0], u[1] - v[1])
        for (i in 0 until n) {
            val c = center[i] ?: continue
            val f = frames[i]
            fun pt(k: Int) = if (f.s[k] >= WRIST_SCORE) floatArrayOf((f.x[k] - c[0]) / scale, (f.y[k] - c[1]) / scale) else null
            if (i > 0 && seg[i] != seg[i - 1]) { pa = null; pb = null }
            var l = pt(L_WRIST); var r = pt(R_WRIST)
            val la = pa; val lb = pb
            val swap = when {
                l != null && r != null && la != null && lb != null -> d(l, lb) + d(r, la) < 0.7f * (d(l, la) + d(r, lb))
                l != null && r == null && la != null && lb != null -> d(l, lb) < 0.5f * d(l, la)
                r != null && l == null && la != null && lb != null -> d(r, la) < 0.5f * d(r, lb)
                else -> false
            }
            if (swap) { val t = l; l = r; r = t }
            swapped[i] = swap
            a[i] = l; b[i] = r
            if (l != null) pa = l
            if (r != null) pb = r
        }
        for (p in listOf(a, b)) {
            for (i in 1 until n - 1) {
                val u = p[i - 1]; val v = p[i]; val w = p[i + 1]
                if (u == null || v == null || w == null || seg[i - 1] != seg[i + 1]) continue
                val dev = hypot(v[0] - (u[0] + w[0]) / 2, v[1] - (u[1] + w[1]) / 2)
                if (dev > 1f && d(u, w) < 0.5f * dev) p[i] = null
            }
            var i = 0
            while (i < n) {
                if (p[i] != null) { i++; continue }
                var j = i
                while (j < n && p[j] == null) j++
                val u = if (i > 0) p[i - 1] else null
                val w = if (j < n) p[j] else null
                if (u != null && w != null && j - i <= 3 && seg[i - 1] == seg[j]) {
                    val t0 = frames[i - 1].t; val t1 = frames[j].t
                    for (k in i until j) {
                        val q = (frames[k].t - t0).toFloat() / (t1 - t0)
                        p[k] = floatArrayOf(u[0] + q * (w[0] - u[0]), u[1] + q * (w[1] - u[1]))
                    }
                }
                i = j
            }
        }
        return Track(a, seg, swapped, center, scale) to Track(b, seg, swapped, center, scale)
    }

    /** Velocità del polso (busti al secondo) sul tratto che arriva in ciascun fotogramma; 0 dove non si sa. */
    private fun speeds(frames: List<PoseFrame>, tr: Track): FloatArray = FloatArray(frames.size) { i ->
        val u = if (i > 0) tr.p[i - 1] else null
        val v = tr.p[i]
        if (u == null || v == null || tr.seg[i] != tr.seg[i - 1] || frames[i].t <= frames[i - 1].t) 0f
        else hypot(v[0] - u[0], v[1] - u[1]) / (frames[i].t - frames[i - 1].t) * 1000f
    }

    /**
     * Dritto, rovescio e volée: le misure dai fotogrammi in ordine di tempo; null se non si trovano i tre momenti.
     * L'impatto è dove il polso va più veloce (fra più colpi nella finestra, quello in [expected] se il braccialetto
     * l'ha riconosciuto, sennò il più vicino ad [anchor]); la preparazione è dove il polso si ferma in fondo
     * all'apertura, il finale il punto più alto dopo l'impatto (con la volée, dove il polso si ferma).
     */
    fun groundMetrics(frames: List<PoseFrame>, expected: LongRange? = null, anchor: Long = 0, volley: Boolean = false): GroundMetrics? {
        val n = frames.size
        if (n < 5) return null
        val (lt, rt) = wristTracks(frames) ?: return null
        val vl = speeds(frames, lt); val vr = speeds(frames, rt)
        // Braccio che colpisce: il polso che copre più spazio (lo swing va da dietro il corpo a sopra la spalla opposta);
        // la velocità da sola inganna, perché il braccio libero che si abbassa dopo il colpo, visto male, fa un salto.
        fun reach(tr: Track): Float {
            val pts = tr.p.filterNotNull()
            if (pts.isEmpty()) return 0f
            return hypot(pts.maxOf { it[0] } - pts.minOf { it[0] }, pts.maxOf { it[1] } - pts.minOf { it[1] })
        }
        val right = reach(rt) >= reach(lt)
        val tr = if (right) rt else lt
        val v = if (right) vr else vl
        val vmax = v.max()
        if (vmax <= 0f) return null
        // Un colpo vero accelera e poi rallenta: conta anche la velocità dei tratti accanto. Un polso scambiato o perso
        // per un fotogramma fa un salto solo, e qui pesa la metà.
        val sv = FloatArray(n) { i -> (v.getOrElse(i - 1) { 0f } + 2 * v[i] + v.getOrElse(i + 1) { 0f }) / 4 }
        val svMax = sv.max()
        val cand = (1 until n).filter { i -> sv[i] >= 0.5f * svMax && v[i] >= v[i - 1] && (i == n - 1 || v[i] >= v[i + 1]) }
        fun score(i: Int): Float {
            val t = frames[i].t
            val dt = if (expected != null) (if (t in expected) 0L else minOf(abs(t - expected.first), abs(t - expected.last))) else t - anchor
            val w = if (expected != null) 250f else 2_000f
            return sv[i] / (1f + (dt / w) * (dt / w))
        }
        val ci = cand.maxByOrNull(::score) ?: return null
        val tc = frames[ci].t
        val pc = tr.p[ci] ?: return null
        fun dist(j: Int) = tr.p[j]!!.let { hypot(it[0] - pc[0], it[1] - pc[1]) }
        // Preparazione: tra i punti lontani dall'impatto (almeno il 70 % del più lontano) il più lento, dove il polso
        // si ferma in fondo all'apertura prima di partire in avanti. Ci si ferma al colpo di prima.
        val back = ArrayList<Int>()
        for (j in ci - 1 downTo 0) {
            val t = frames[j].t
            if (t < tc - 1_500 || tr.seg[j] != tr.seg[ci]) break
            if (t > tc - 100 || tr.p[j] == null) continue
            if (tc - t > 300 && v[j] > 0.6f * vmax) break
            back += j
        }
        if (back.isEmpty()) return null
        val far = back.maxOf(::dist)
        // Lentezza sul fotogramma prima e dopo (il polso fermo in fondo); a pari lentezza il punto più indietro.
        fun still(j: Int): Float {
            val u = tr.p.getOrNull(j - 1) ?: tr.p[j]!!; val w = tr.p.getOrNull(j + 1) ?: tr.p[j]!!
            val dt = frames[minOf(j + 1, n - 1)].t - frames[maxOf(j - 1, 0)].t
            return if (dt <= 0) 0f else hypot(w[0] - u[0], w[1] - u[1]) / dt * 1000f
        }
        val farOnes = back.filter { dist(it) >= 0.7f * far }
        val slowest = farOnes.minOf(::still)
        val pi = farOnes.filter { still(it) <= slowest * 1.1f + 0.1f }.maxBy(::dist)
        // Finale: il punto più alto dopo l'impatto, finché il polso non riscende verso la posizione d'attesa.
        val fwd = ArrayList<Int>()
        var hi: Int? = null
        for (j in ci + 1 until n) {
            val t = frames[j].t
            if (t > tc + 1_200 || tr.seg[j] != tr.seg[ci]) break
            val p = tr.p[j] ?: continue
            if (t < tc + 100) continue
            if (t - tc > 300 && v[j] > 0.6f * vmax) break
            if (hi != null && p[1] > tr.p[hi]!![1] + 1f) break
            fwd += j
            if (hi == null || p[1] < tr.p[hi]!![1]) hi = j
        }
        val fi = (if (volley) fwd.firstOrNull { v[it] < 0.3f * v[ci] } ?: fwd.minByOrNull { v[it] } else hi) ?: return null

        // Punti del braccio che colpisce in un fotogramma, tenendo conto dello scambio sinistra/destra.
        fun side(j: Int, l: Int, r: Int) = if (right != tr.swapped[j]) r else l
        fun wrist(j: Int): Pair<Float, Float> {
            val c = tr.center[j]!!; val p = tr.p[j]!!
            return (c[0] + p[0] * tr.scale) to (c[1] + p[1] * tr.scale)
        }
        fun body(f: PoseFrame): Float? {
            val pts = listOf(L_HIP, R_HIP, L_SHOULDER, R_SHOULDER).filter { f.s[it] >= MIN_SCORE }
            return if (pts.size < 2) null else pts.map { f.x[it] }.average().toFloat()
        }
        // Direzione del colpo: dalla preparazione all'impatto, in orizzontale.
        val dir = if (wrist(ci).first >= wrist(pi).first) 1f else -1f
        fun knee(f: PoseFrame): Float? = listOfNotNull(
            angle(f, L_HIP, L_KNEE, L_ANKLE).takeIf { ok(f, L_HIP, L_KNEE, L_ANKLE) },
            angle(f, R_HIP, R_KNEE, R_ANKLE).takeIf { ok(f, R_HIP, R_KNEE, R_ANKLE) },
        ).minOrNull()
        val ki = (frames.indexOfFirst { it.t >= frames[pi].t - 300 }.coerceAtLeast(0)..ci).filter { knee(frames[it]) != null }
            .minByOrNull { knee(frames[it])!! }
        // Statura e terreno da tutti i fotogrammi (chi colpisce da fondo campo si muove, ma resta in piedi).
        val ankle = { f: PoseFrame -> listOfNotNull(f.y[L_ANKLE].takeIf { ok(f, L_ANKLE) }, f.y[R_ANKLE].takeIf { ok(f, R_ANKLE) }).maxOrNull() }
        val ground = median(frames.mapNotNull(ankle)) ?: return null
        val noseToAnkle = median(frames.filter { ok(it, NOSE) }.mapNotNull { f -> ankle(f)?.let { it - f.y[NOSE] } }) ?: return null
        val stature = noseToAnkle / NOSE_TO_ANKLE
        if (stature <= 0.05f) return null
        // Gomito all'impatto: dal fotogramma più vicino in cui spalla, gomito e polso si vedono (nel rovescio a due mani
        // il gomito è spesso coperto per un attimo).
        val ei = listOf(ci, ci - 1, ci + 1, ci - 2, ci + 2).filter { it in 0 until n }.firstOrNull { j ->
            ok(frames[j], side(j, L_SHOULDER, R_SHOULDER), side(j, L_ELBOW, R_ELBOW), side(j, L_WRIST, R_WRIST))
        }
        val elbow = ei?.let { angle(frames[it], side(it, L_SHOULDER, R_SHOULDER), side(it, L_ELBOW, R_ELBOW), side(it, L_WRIST, R_WRIST)) }
        val contact = frames[ci]; val prep = frames[pi]; val finish = frames[fi]
        val bc = body(contact) ?: return null
        val bp = body(prep) ?: return null
        val sw = { j: Int -> frames[j].s[side(j, L_WRIST, R_WRIST)] }
        // Sicurezza dei punti usati; un gomito o delle gambe che non si vedono contano come sicurezza zero.
        val used = listOf(sw(ci), sw(pi), sw(fi)) +
            (ei?.let { listOf(frames[it].s[side(it, L_ELBOW, R_ELBOW)], frames[it].s[side(it, L_SHOULDER, R_SHOULDER)]) } ?: listOf(0f, 0f)) +
            (ki?.let { k -> listOf(L_HIP, L_KNEE, L_ANKLE, R_HIP, R_KNEE, R_ANKLE).map { frames[k].s[it] } } ?: List(6) { 0f })
        return GroundMetrics(
            kneeMinDeg = ki?.let { knee(frames[it]) },
            elbowContactDeg = elbow,
            contactHeight = (ground - wrist(ci).second) / stature,
            contactInFront = dir * (wrist(ci).first - bc) / stature,
            backswing = -dir * (wrist(pi).first - bp) / stature,
            finishHeight = (ground - wrist(fi).second) / stature,
            prepToContactMs = contact.t - prep.t,
            quality = used.average().toFloat(),
            prepT = prep.t, contactT = contact.t, finishT = finish.t,
            // Mano: l'etichetta che la traccia ha più spesso (di lato MoveNet a volte le inverte).
            rightHanded = (0 until n).filter { tr.p[it] != null }.count { right != tr.swapped[it] } * 2 >= tr.p.count { it != null },
        )
    }

    data class GroundMean(
        val n: Int, val kneeMinDeg: Float?, val elbowContactDeg: Float?, val contactHeight: Float,
        val contactInFront: Float, val backswing: Float, val finishHeight: Float, val prepToContactMs: Long,
    )

    fun groundMean(list: List<GroundMetrics>): GroundMean? {
        val r = list.filter { it.reliable }.takeIf { it.isNotEmpty() } ?: return null
        return GroundMean(
            r.size, r.mapNotNull { it.kneeMinDeg }.takeIf { it.isNotEmpty() }?.average()?.toFloat(),
            r.mapNotNull { it.elbowContactDeg }.takeIf { it.isNotEmpty() }?.average()?.toFloat(),
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
