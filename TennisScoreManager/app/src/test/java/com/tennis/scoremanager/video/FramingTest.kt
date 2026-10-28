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
}
