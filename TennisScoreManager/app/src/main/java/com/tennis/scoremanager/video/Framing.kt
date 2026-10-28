// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.video

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Assistente inquadratura: telefono dietro una linea di fondo, al centro, in orizzontale. Con distanza e altezza
 * calcola l'inclinazione che tiene nel video tutte e due le linee di fondo (e il giocatore lontano, testa compresa)
 * e dice se la larghezza ci sta con l'obiettivo scelto. La centratura destra/sinistra non si calcola: i sensori non la
 * sanno (la bussola sbaglia vicino a una recinzione di metallo), si guarda sull'anteprima.
 */
object Framing {
    /** Campo: lunghezza e larghezza in metri. */
    enum class Court(val length: Float, val width: Float) {
        TENNIS_SINGLES(23.77f, 8.23f),
        TENNIS_DOUBLES(23.77f, 10.97f),
        PADEL(20f, 10f),
    }

    /** Un obiettivo posteriore: id della fotocamera e campo visivo del video 16:9, in gradi. */
    data class Lens(val id: String, val hFov: Float, val vFov: Float) {
        companion object {
            /**
             * Dal sensore (larghezza in mm) e dalla focale: il 16:9 usa tutta la larghezza di un sensore 4:3.
             * [crop] toglie la parte che lo stabilizzatore tiene per sé (0,9 = 10 %).
             */
            fun of(id: String, focalMm: Float, sensorWidthMm: Float, crop: Float = STABILIZATION_CROP): Lens {
                val half = sensorWidthMm / 2f * crop
                return Lens(id, deg(2 * atan(half / focalMm)), deg(2 * atan(half * 9f / 16f / focalMm)))
            }
        }
    }

    /**
     * Risultato: [tilt] gradi verso il basso consigliati; quanto campo visivo serve in altezza e in larghezza; se ci
     * sta con l'obiettivo [lens].
     */
    data class Plan(val tilt: Float, val needV: Float, val needH: Float, val lens: Lens?, val fitsV: Boolean, val fitsH: Boolean) {
        val fits: Boolean get() = fitsV && fitsH
    }

    /** Il giocatore lontano sta fino a 2 m dietro la sua linea ed è alto fino a 1,9 m; mezzo metro di margine ai lati. */
    private const val FAR_BEHIND = 2f
    private const val PLAYER_H = 1.9f
    private const val SIDE_MARGIN = 0.5f
    /** Parte dell'immagine che lo stabilizzatore del video tiene per sé (stima: si vede meglio con la sagoma). */
    const val STABILIZATION_CROP = 0.9f

    /** Inclinazione e campo visivo necessari, da [distance] metri dietro la linea di fondo e [height] metri da terra. */
    fun plan(distance: Float, height: Float, court: Court, lens: Lens?): Plan {
        val d = max(distance, 0.3f)
        val h = max(height, 0.3f)
        // Sotto: la linea di fondo vicina. Sopra: la testa del giocatore lontano, o il terreno dietro di lui se più in alto.
        val bottom = deg(atan(h / d))
        val farDist = d + court.length + FAR_BEHIND
        val top = minOf(deg(atan(h / farDist)), deg(atan((h - PLAYER_H) / farDist)))
        val tilt = (bottom + top) / 2f
        val needV = bottom - top
        val needH = needH(d, h, court, tilt)
        return Plan(tilt, needV, needH, lens, lens != null && needV <= lens.vFov, lens != null && needH <= lens.hFov)
    }

    /** Larghezza: gli angoli vicini del campo, visti dalla fotocamera già inclinata di [tilt] gradi. */
    private fun needH(d: Float, h: Float, court: Court, tilt: Float): Float {
        val t = rad(tilt)
        val depth = d * cos(t) + h * sin(t)
        return deg(2 * atan((court.width / 2f + SIDE_MARGIN) / depth))
    }

    /** L'obiettivo da usare: il più stretto in cui ci sta tutto (più dettaglio), altrimenti il più largo. */
    fun bestLens(distance: Float, height: Float, court: Court, lenses: List<Lens>): Lens? {
        if (lenses.isEmpty()) return null
        return lenses.sortedBy { it.hFov }.firstOrNull { plan(distance, height, court, it).fits } ?: lenses.maxBy { it.hFov }
    }

    /** Metri dietro la linea di fondo da cui la larghezza ci sta con [lens] (null se oltre 30 m). */
    fun minDistance(height: Float, court: Court, lens: Lens): Float? {
        var d = 0.5f
        while (d <= 30f) {
            if (plan(d, height, court, lens).fitsH) return d
            d += 0.1f
        }
        return null
    }

    /**
     * Inclinazione del telefono da un vettore "in su" nelle coordinate del telefono (quello del sensore di gravità):
     * [Tilt.down] gradi verso il basso dell'obiettivo posteriore, [Tilt.roll] gradi di rotazione in senso orario
     * dell'immagine. [rotation] è la rotazione dello schermo (0, 90, 180, 270).
     */
    data class Tilt(val down: Float, val roll: Float)

    fun tilt(x: Float, y: Float, z: Float, rotation: Int): Tilt? {
        val n = sqrt(x * x + y * y + z * z)
        if (n < 1f) return null
        val ux = x / n
        val uy = y / n
        val uz = z / n
        // L'obiettivo posteriore guarda verso -z: verso il basso quando "su" esce dallo schermo.
        val down = deg(asin(uz.coerceIn(-1f, 1f)))
        // "Su" e "destra" dello schermo nelle coordinate del telefono.
        val (upX, upY, rightX, rightY) = when (rotation) {
            90 -> listOf(1f, 0f, 0f, -1f)
            180 -> listOf(0f, -1f, -1f, 0f)
            270 -> listOf(-1f, 0f, 0f, 1f)
            else -> listOf(0f, 1f, 1f, 0f)
        }
        val alongUp = ux * upX + uy * upY
        val alongRight = ux * rightX + uy * rightY
        // Telefono girato in senso orario: il "su" vero appare spostato a sinistra.
        val roll = deg(atan2(-alongRight, alongUp))
        return Tilt(down, roll)
    }

    /** Quanto è lontano dal giusto: 0 a posto, 1 da correggere poco, 2 da correggere. */
    fun grade(error: Float, tolerance: Float = TOLERANCE): Int = when {
        abs(error) <= tolerance -> 0
        abs(error) <= tolerance * 3 -> 1
        else -> 2
    }

    const val TOLERANCE = 1f

    // ---------------------------------------------------------------- sagoma del campo (fase 2)

    /**
     * Taratura: dove sta il telefono rispetto al centro della linea di fondo vicina ([distance] metri dietro,
     * [height] da terra, [lateral] metri a destra), quanto è girato a destra ([pan] gradi), correzione del campo
     * visivo ([fovScale], per lo stabilizzatore) e dell'inclinazione letta dal sensore ([pitchOffset] gradi).
     * Quando la sagoma combacia con le linee vere, questi numeri descrivono davvero la ripresa.
     */
    @kotlinx.serialization.Serializable
    data class Calib(
        val distance: Float = 6f,
        val height: Float = 2.5f,
        val court: String = Court.TENNIS_DOUBLES.name,
        val pan: Float = 0f,
        val lateral: Float = 0f,
        val fovScale: Float = 1f,
        val pitchOffset: Float = 0f,
    ) {
        val courtType: Court get() = runCatching { Court.valueOf(court) }.getOrDefault(Court.TENNIS_DOUBLES)
    }

    /** Un punto del campo: x a destra dal centro, y in avanti dalla linea di fondo vicina, z in su (metri). */
    data class P3(val x: Float, val y: Float, val z: Float = 0f)

    /** Linee del campo da disegnare, come spezzate; la rete in alto (nastro) per riconoscerla. */
    fun lines(court: Court): List<List<P3>> {
        val l = court.length
        val half = court.width / 2f
        val net = l / 2f
        fun seg(x1: Float, y1: Float, x2: Float, y2: Float) = listOf(P3(x1, y1), P3(x2, y2))
        val out = mutableListOf(
            seg(-half, 0f, half, 0f), seg(-half, l, half, l),
            seg(-half, 0f, -half, l), seg(half, 0f, half, l),
        )
        when (court) {
            Court.PADEL -> {
                val sv = 6.95f
                out += seg(-half, net - sv, half, net - sv)
                out += seg(-half, net + sv, half, net + sv)
                out += seg(0f, net - sv, 0f, net + sv)
                out += listOf(P3(-half, net, 0.92f), P3(0f, net, 0.88f), P3(half, net, 0.92f))
            }
            else -> {
                val single = 8.23f / 2f
                val sv = 6.40f
                if (court == Court.TENNIS_DOUBLES) {
                    out += seg(-single, 0f, -single, l)
                    out += seg(single, 0f, single, l)
                }
                out += seg(-single, net - sv, single, net - sv)
                out += seg(-single, net + sv, single, net + sv)
                out += seg(0f, net - sv, 0f, net + sv)
                // Segni centrali sulle linee di fondo.
                out += seg(0f, 0f, 0f, 0.1f)
                out += seg(0f, l, 0f, l - 0.1f)
                // Nastro della rete: 1,07 m ai pali (0,914 m fuori dal doppio), 0,914 m al centro.
                val post = 10.97f / 2f + 0.914f
                out += listOf(P3(-post, net, 1.07f), P3(0f, net, 0.914f), P3(post, net, 1.07f))
            }
        }
        return out
    }

    /**
     * Le linee di [lines] viste dalla fotocamera: punti in frazioni dell'immagine 16:9 (0..1, y in giù), spezzate dove
     * finiscono dietro l'obiettivo. [pitchDown] e [roll] sono quelli letti dal sensore (la taratura li corregge).
     */
    fun project(calib: Calib, lens: Lens, pitchDown: Float, roll: Float): List<List<Pair<Float, Float>>> {
        val yaw = rad(calib.pan)
        val p = rad(pitchDown + calib.pitchOffset)
        val rl = rad(roll)
        val f = floatArrayOf(sin(yaw) * cos(p), cos(yaw) * cos(p), -sin(p))
        val r0 = floatArrayOf(cos(yaw), -sin(yaw), 0f)
        val u0 = cross(r0, f)
        // Telefono girato in senso orario: la destra dell'immagine scende.
        val r = FloatArray(3) { r0[it] * cos(rl) - u0[it] * sin(rl) }
        val u = FloatArray(3) { u0[it] * cos(rl) + r0[it] * sin(rl) }
        val tx = kotlin.math.tan(rad(lens.hFov * calib.fovScale) / 2f)
        val ty = kotlin.math.tan(rad(lens.vFov * calib.fovScale) / 2f)
        val c = floatArrayOf(calib.lateral, -calib.distance, calib.height)
        val out = mutableListOf<List<Pair<Float, Float>>>()
        for (line in lines(calib.courtType)) {
            var cur = mutableListOf<Pair<Float, Float>>()
            for (k in 0 until line.size - 1) {
                val a = line[k]
                val b = line[k + 1]
                // A pezzi: una linea vicina alla fotocamera può finire in parte dietro, e i pezzi davanti restano.
                val steps = 24
                for (i in 0..steps) {
                    if (k > 0 && i == 0) continue
                    val t = i / steps.toFloat()
                    val v = floatArrayOf(a.x + (b.x - a.x) * t - c[0], a.y + (b.y - a.y) * t - c[1], a.z + (b.z - a.z) * t - c[2])
                    val z = dot(v, f)
                    if (z < 0.2f) {
                        if (cur.size > 1) out += cur
                        cur = mutableListOf()
                        continue
                    }
                    cur += (0.5f + dot(v, r) / z / (2f * tx)) to (0.5f - dot(v, u) / z / (2f * ty))
                }
            }
            if (cur.size > 1) out += cur
        }
        return out
    }

    private fun cross(a: FloatArray, b: FloatArray) = floatArrayOf(a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])
    private fun dot(a: FloatArray, b: FloatArray) = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]

    private fun deg(r: Float) = Math.toDegrees(r.toDouble()).toFloat()
    private fun rad(d: Float) = Math.toRadians(d.toDouble()).toFloat()
}
