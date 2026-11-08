// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.video

import com.tennis.scoremanager.video.Framing.Court
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FramingTest {
    // Galaxy A52s: principale 5,23 mm su 7,4 mm, grandangolo 1,74 mm su 4,48 mm.
    private val main = Framing.Lens.of("0", 5.23f, 7.3984f)
    private val wide = Framing.Lens.of("2", 1.74f, 4.48f)

    @Test
    fun lensFieldOfView() {
        assertEquals(65.0f, main.hFov, 0.5f)
        assertEquals(39.4f, main.vFov, 0.5f)
        assertEquals(98.0f, wide.hFov, 1f)
        // Senza stabilizzatore il principale vede di più.
        assertEquals(70.5f, Framing.Lens.of("0", 5.23f, 7.3984f, crop = 1f).hFov, 0.5f)
    }

    @Test
    fun tiltKeepsBothBaselines() {
        // 4 m dietro, 2,5 m d'altezza: mirare alla rete (8,9°) taglierebbe la linea vicina.
        val p = Framing.plan(4f, 2.5f, Court.TENNIS_DOUBLES, wide)
        assertTrue("${p.tilt}", p.tilt in 15f..19f)
        assertTrue(p.fitsV)
        // La linea vicina è 32° sotto l'orizzonte: sta dentro la metà inferiore dell'immagine.
        assertTrue(p.tilt + wide.vFov / 2 >= 32f)
    }

    @Test
    fun widthIsTheRealLimit() {
        // Alla recinzione (6,4 m) il principale non vede gli angoli vicini del doppio, il grandangolo sì.
        val fence = Framing.plan(6.4f, 2.5f, Court.TENNIS_DOUBLES, main)
        assertTrue(fence.fitsV)
        assertFalse(fence.fitsH)
        assertTrue(Framing.plan(6.4f, 2.5f, Court.TENNIS_DOUBLES, wide).fits)
        assertEquals("2", Framing.bestLens(6.4f, 2.5f, Court.TENNIS_DOUBLES, listOf(main, wide))?.id)
        // Da lontano basta il principale, che ha più dettaglio: si sceglie lui.
        assertEquals("0", Framing.bestLens(12f, 3f, Court.TENNIS_DOUBLES, listOf(main, wide))?.id)
        val min = Framing.minDistance(3f, Court.TENNIS_DOUBLES, main)
        assertNotNull(min)
        assertTrue("$min", min!! in 8f..12f)
    }

    @Test
    fun phoneTiltFromGravity() {
        // In orizzontale (schermo ruotato di 90°), obiettivo orizzontale e dritto: "su" = +x.
        val flat = Framing.tilt(9.81f, 0f, 0f, 90)!!
        assertEquals(0f, flat.down, 0.01f)
        assertEquals(0f, flat.roll, 0.01f)
        // Inclinato di 20° verso il basso: "su" esce un po' dallo schermo.
        val down = Framing.tilt(9.81f * 0.9397f, 0f, 9.81f * 0.342f, 90)!!
        assertEquals(20f, down.down, 0.1f)
        // Girato di 5° in senso orario: il "su" vero va verso la sinistra dello schermo (+y del telefono).
        val cw = Framing.tilt(9.81f * 0.9962f, 9.81f * 0.0872f, 0f, 90)!!
        assertEquals(5f, cw.roll, 0.1f)
        assertEquals(0, Framing.grade(0.8f))
        assertEquals(1, Framing.grade(-2.5f))
        assertEquals(2, Framing.grade(4f))
    }

    private fun centerOfNet(calib: Framing.Calib): Float =
        Math.toDegrees(kotlin.math.atan(calib.height / (calib.distance + 23.77f / 2f)).toDouble()).toFloat()

    @Test
    fun aimedPointIsInTheMiddle() {
        val calib = Framing.Calib(distance = 6f, height = 3f, court = "TENNIS_DOUBLES")
        val lines = Framing.project(calib, wide, centerOfNet(calib), 0f)
        // La linea centrale del servizio passa per il centro dell'immagine (la rete a terra è lì).
        val center = lines.flatten().minBy { (x, y) -> (x - 0.5f) * (x - 0.5f) + (y - 0.5f) * (y - 0.5f) }
        assertEquals(0.5f, center.first, 0.01f)
        assertEquals(0.5f, center.second, 0.02f)
        // Linea di fondo vicina: simmetrica e più in basso del centro; quella lontana più in alto.
        val near = lines.first()
        assertEquals(1f, near.first().first + near.last().first, 0.01f)
        assertTrue(near.first().second > 0.5f)
        assertTrue(lines[1].first().second < 0.5f)
    }

    @Test
    fun panAndRollMoveTheOutline() {
        val calib = Framing.Calib(distance = 6f, height = 3f)
        val pitch = centerOfNet(calib)
        fun netCenter(c: Framing.Calib, roll: Float) = Framing.project(c, wide, pitch, roll).flatten()
            .minBy { (x, y) -> (x - 0.5f) * (x - 0.5f) + (y - 0.5f) * (y - 0.5f) }
        // Telefono girato a destra: il campo si sposta a sinistra nell'immagine.
        val panned = Framing.project(calib.copy(pan = 5f), wide, pitch, 0f)
        assertTrue(panned.first().let { it.first().first + it.last().first } < 1f)
        // Telefono ruotato in senso orario: la linea di fondo vicina sale a destra (y più piccolo a destra).
        val near = Framing.project(calib, wide, pitch, 3f).first()
        assertTrue(near.last().second < near.first().second)
        assertEquals(0.5f, netCenter(calib, 3f).first, 0.02f)
        // La linea vicina con un telefono a 1 m: in parte dietro l'obiettivo, ma i pezzi davanti restano.
        assertTrue(Framing.project(Framing.Calib(distance = 0.5f, height = 1f), wide, 60f, 0f).isNotEmpty())
    }
}
