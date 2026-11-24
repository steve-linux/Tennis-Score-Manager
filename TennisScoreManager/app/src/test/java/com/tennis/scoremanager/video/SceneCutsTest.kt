// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.video

import org.junit.Assert.assertEquals
import org.junit.Test

class SceneCutsTest {
    @Test
    fun spikeIsACutSteadyMotionIsNot() {
        // Movimento normale (piccole differenze), poi un taglio tra il fotogramma 5 e il 6.
        val d = floatArrayOf(0.001f, 0.002f, 0.0015f, 0.001f, 0.002f, 0.03f, 0.002f, 0.001f, 0.0012f, 0.001f)
        assertEquals(listOf(6), SceneCuts.spikes(d))
        // Una panoramica: differenze grandi ma tutte simili, nessun taglio.
        assertEquals(emptyList<Int>(), SceneCuts.spikes(FloatArray(10) { 0.017f }))
    }

    @Test
    fun clipStopsAtTheCuts() {
        // Il punto delle 0:39 del 24 nov: tocco a 60,219 s, taglio a 61,83 s, pezzo fino a 62,219.
        val c = Highlights.Clip(5, 53_047, 62_219)
        assertEquals(Highlights.Clip(5, 53_047, 61_830), SceneCuts.trim(c, 60_219, listOf(61_830)))
        // Un taglio nei primi secondi: il pezzo parte dopo (non si vede la fine dell'altro punto).
        val d = Highlights.Clip(1, 7_104, 24_286)
        assertEquals(Highlights.Clip(1, 7_740, 23_560), SceneCuts.trim(d, 22_286, listOf(7_740, 23_560)))
        // Tocco in ritardo: il taglio poco prima del tocco ferma il pezzo lì.
        val e = Highlights.Clip(8, 110_000, 123_362)
        assertEquals(Highlights.Clip(8, 110_000, 120_990), SceneCuts.trim(e, 121_362, listOf(120_990)))
        // Nessun taglio: niente cambia.
        assertEquals(c, SceneCuts.trim(c, 60_219, emptyList()))
    }
}
