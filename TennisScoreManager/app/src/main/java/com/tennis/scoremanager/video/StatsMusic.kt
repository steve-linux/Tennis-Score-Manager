// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.video

import java.io.File
import java.io.OutputStream
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/**
 * Sottofondo delle schermate delle statistiche, generato dall'app (niente brani di altri, niente licenze da
 * controllare), nello stile delle grafiche delle dirette sportive: 74 BPM in La minore (La minore–Fa–Do–Sol, un
 * accordo per battuta), tappeto di accordi e arpeggio di synth, poi entrano basso e cassa, poi rullante e
 * charleston, con un crescendo fino alla fine; dissolvenza in entrata e in uscita. Stereo, 44,1 kHz, 16 bit, in
 * un file WAV che Media3 mette sotto le immagini.
 */
object StatsMusic {
    const val RATE = 44_100
    private const val FADE_IN_S = 1.0
    private const val FADE_OUT_S = 2.5
    private const val BEAT_S = 60.0 / 74
    private const val BAR_S = 4 * BEAT_S

    // Per ogni accordo: il basso e tre note del tappeto (Hz).
    private val chords = listOf(
        doubleArrayOf(110.00, 220.00, 261.63, 329.63),  // La minore
        doubleArrayOf(87.31, 174.61, 220.00, 261.63),   // Fa
        doubleArrayOf(130.81, 196.00, 261.63, 329.63),  // Do
        doubleArrayOf(98.00, 196.00, 246.94, 293.66),   // Sol
    )
    /** Ordine delle note dell'arpeggio, un ottavo l'una, due ottave sopra il tappeto per le prime tre. */
    private val arp = intArrayOf(1, 2, 3, 2, 1, 3, 2, 3)

    /** Campioni stereo interleaved (sinistra, destra) da -1 a 1, lunghi [ms]. */
    fun samples(ms: Long): FloatArray {
        val n = (ms * RATE / 1000).toInt()
        val out = FloatArray(n * 2)
        val total = n.toDouble() / RATE
        val noise = java.util.Random(7)
        var lastNoise = 0.0
        for (i in 0 until n) {
            val t = i.toDouble() / RATE
            val bar = (t / BAR_S).toInt()
            val chord = chords[bar % chords.size]
            val inBar = t - bar * BAR_S
            val beat = (inBar / BEAT_S).toInt()
            val inBeat = inBar - beat * BEAT_S
            // Crescendo: da 0,65 a 1 su tutta la durata.
            val grow = 0.65 + 0.35 * t / total

            // Tappeto: tre note con due voci leggermente stonate (una a sinistra, una a destra); al cambio di
            // accordo il precedente sfuma nel nuovo in 0,15 s, così non scatta.
            val w = if (bar == 0) 1.0 else min(1.0, inBar / 0.15)
            val prev = chords[(bar + chords.size - 1) % chords.size]
            val padL = w * pad(chord, t, 0.997) + (1 - w) * pad(prev, t, 0.997)
            val padR = w * pad(chord, t, 1.003) + (1 - w) * pad(prev, t, 1.003)

            // Arpeggio: un ottavo l'uno, che si spegne da solo; la fase riparte a ogni nota.
            val eighth = (inBar / (BEAT_S / 2)).toInt()
            val since8 = inBar - eighth * (BEAT_S / 2)
            val f = chord[arp[eighth % arp.size]] * 2
            val env8 = exp(-since8 * 7) * min(1.0, since8 / 0.003)
            val pluck = (sin(2 * PI * f * since8) + 0.35 * sin(4 * PI * f * since8)) * env8

            // Dalla seconda battuta: basso su ogni battito e cassa sull'1 e sul 3.
            var bass = 0.0
            var kick = 0.0
            if (bar >= 1) {
                val envB = exp(-inBeat * 3.5) * min(1.0, inBeat / 0.005)
                bass = (sin(2 * PI * chord[0] * inBeat) + 0.4 * sin(4 * PI * chord[0] * inBeat)) * envB
                if (beat % 2 == 0) {
                    // Cassa: un tonfo che scende da 120 a 45 Hz.
                    val phase = 2 * PI * (45 * inBeat + 75.0 / 30 * (1 - exp(-30 * inBeat)))
                    kick = sin(phase) * exp(-inBeat * 16) * min(1.0, inBeat / 0.002)
                }
            }
            // Dalla terza battuta: rullante sul 2 e sul 4 e charleston sui controtempi.
            var snare = 0.0
            var hat = 0.0
            val white = noise.nextGaussian() * 0.5
            val bright = white - lastNoise  // solo le frequenze alte
            lastNoise = white
            if (bar >= 2) {
                if (beat % 2 == 1) {
                    val e = exp(-inBeat * 22) * min(1.0, inBeat / 0.002)
                    snare = (white * 0.7 + sin(2 * PI * 190 * inBeat) * 0.5) * e
                }
                if (since8 < BEAT_S / 2 && eighth % 2 == 1) hat = bright * exp(-since8 * 55) * min(1.0, since8 / 0.001)
            }

            val mid = 0.10 * pluck + 0.28 * bass + 0.42 * kick + 0.10 * snare
            out[2 * i] = ((0.055 * padL + mid + 0.025 * hat + 0.025 * pluck) * grow).toFloat()
            out[2 * i + 1] = ((0.055 * padR + mid + 0.030 * hat - 0.010 * pluck) * grow).toFloat()
        }
        // Stesso volume qualunque sia la durata: il picco a 0,85.
        val peak = out.maxOfOrNull { kotlin.math.abs(it) }?.takeIf { it > 0f } ?: 1f
        for (k in out.indices) out[k] = (out[k] * 0.85f / peak).coerceIn(-1f, 1f)
        fade(out, RATE, FADE_IN_S, FADE_OUT_S)
        return out
    }

    /** Tre note del tappeto, ognuna con le prime armoniche (suono pieno ma morbido), [detune] per la larghezza. */
    private fun pad(chord: DoubleArray, t: Double, detune: Double): Double {
        var v = 0.0
        for (k in 1..3) {
            val f = chord[k] * detune
            v += sin(2 * PI * f * t) + 0.5 * sin(4 * PI * f * t) + 0.25 * sin(6 * PI * f * t)
        }
        return v
    }

    /** Dissolvenza in entrata e in uscita su campioni stereo interleaved, sul posto. */
    fun fade(pcm: FloatArray, rate: Int, inS: Double, outS: Double) {
        val n = pcm.size / 2
        val total = n.toDouble() / rate
        for (i in 0 until n) {
            val t = i.toDouble() / rate
            val env = (min(1.0, t / inS) * min(1.0, (total - t) / outS)).coerceAtLeast(0.0).toFloat()
            pcm[2 * i] *= env
            pcm[2 * i + 1] *= env
        }
    }

    fun writeWav(ms: Long, file: File) = file.outputStream().buffered().use { writeWav(samples(ms), it) }

    fun writeWav(pcm: FloatArray, out: OutputStream, rate: Int = RATE) {
        val bytes = pcm.size * 2
        fun int(v: Int) = out.write(byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte()))
        fun short(v: Int) = out.write(byteArrayOf(v.toByte(), (v shr 8).toByte()))
        out.write("RIFF".toByteArray())
        int(36 + bytes)
        out.write("WAVEfmt ".toByteArray())
        int(16)
        short(1)          // PCM
        short(2)          // stereo
        int(rate)
        int(rate * 4)     // byte al secondo
        short(4)          // byte per campione stereo
        short(16)
        out.write("data".toByteArray())
        int(bytes)
        val buf = ByteArray(bytes)
        for (i in pcm.indices) {
            val v = (pcm[i] * 32767).toInt()
            buf[2 * i] = v.toByte()
            buf[2 * i + 1] = (v shr 8).toByte()
        }
        out.write(buf)
    }
}
