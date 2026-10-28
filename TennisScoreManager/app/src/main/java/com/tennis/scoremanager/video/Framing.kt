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

    private fun deg(r: Float) = Math.toDegrees(r.toDouble()).toFloat()
    private fun rad(d: Float) = Math.toRadians(d.toDouble()).toFloat()
}
