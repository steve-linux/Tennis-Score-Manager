// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import kotlin.math.abs

class StatsMusicTest {

    @Test
    fun tenSecondsWithFadesAndNoClicks() {
        val pcm = StatsMusic.samples(10_000)
        assertEquals(10 * StatsMusic.RATE * 2, pcm.size)
        // Dissolvenze: quasi silenzio all'inizio e alla fine, pieno nel mezzo.
        assertTrue(pcm.take(400).all { abs(it) < 0.01f })
        assertTrue(pcm.takeLast(400).all { abs(it) < 0.01f })
        val peak = pcm.maxOf { abs(it) }
        assertTrue("picco $peak", peak in 0.2f..0.95f)
        // Nessun salto brusco (un clic): si guarda il suono smussato, così il fruscio delle percussioni non conta.
        val left = FloatArray(pcm.size / 2) { pcm[2 * it] }
        val smooth = FloatArray(left.size) { i -> (maxOf(0, i - 7)..i).sumOf { left[it].toDouble() }.toFloat() / 8 }
        var maxJump = 0f
        for (i in 1 until smooth.size) maxJump = maxOf(maxJump, abs(smooth[i] - smooth[i - 1]))
        assertTrue("salto $maxJump", maxJump < 0.05f)
        // Il crescendo: la seconda metà è più forte della prima (dissolvenze escluse).
        fun rms(from: Int, to: Int) = kotlin.math.sqrt((from until to).sumOf { (left[it] * left[it]).toDouble() } / (to - from))
        val sr = StatsMusic.RATE
        assertTrue(rms(2 * sr, 5 * sr) < rms(5 * sr, 8 * sr))
        // Sempre uguale: è generata, non casuale.
        assertTrue(pcm.contentEquals(StatsMusic.samples(10_000)))
        System.getenv("TSM_MUSIC_PREVIEW")?.let { path -> java.io.File(path).outputStream().use { StatsMusic.writeWav(StatsMusic.samples(20_000), it) } }
    }

    @Test
    fun wavHeader() {
        val out = ByteArrayOutputStream()
        StatsMusic.writeWav(StatsMusic.samples(100), out)
        val b = out.toByteArray()
        assertEquals("RIFF", String(b, 0, 4))
        assertEquals("WAVE", String(b, 8, 4))
        assertEquals(44 + 4410 * 2 * 2, b.size)
    }
}
