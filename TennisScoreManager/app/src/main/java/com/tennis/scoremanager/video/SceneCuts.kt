// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.video

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log

/**
 * Tagli netti nei video importati. Un video già montato (per esempio una partita pubblicata su YouTube) ha le pause tra
 * i punti tagliate: il pezzo di un punto, che tiene 2 s dopo la sua fine e comincia 1 s dopo il punto prima, finirebbe
 * per mostrare per un attimo l'altra parte del taglio. Si cercano i tagli vicino all'inizio e alla fine del pezzo e lo
 * si ferma lì. Un taglio è un salto improvviso tra due fotogrammi di fila, molto più grande di quelli intorno (con la
 * telecamera ferma un giocatore che corre cambia poco l'immagine, un taglio la cambia tutta).
 */
object SceneCuts {
    /** L'immagine ridotta a 32×18 grigi: basta per i tagli e si calcola in fretta. */
    private const val GRID_W = 32
    private const val GRID_H = 18
    /** Differenza media minima (0-1) e quante volte più grande di quelle vicine. Misurate su una partita montata vera. */
    private const val ABS_MIN = 0.008f
    private const val RATIO = 4f
    /**
     * Dove si cerca: i primi 1,5 s del pezzo (il taglio dopo il punto prima cade entro 1,6 s dal tocco, e il pezzo
     * parte 1 s dopo), e da poco prima della fine del punto (tocco in ritardo) alla fine.
     */
    const val START_WINDOW_MS = 1_500L
    const val LATE_TAP_MS = 1_500L
    /** Oltre questo tempo si rinuncia (il pezzo resta com'è). */
    private const val TIMEOUT_MS = 15_000L

    /** Differenza media tra due immagini ridotte. */
    fun diff(a: FloatArray, b: FloatArray): Float {
        var s = 0f
        for (i in a.indices) s += kotlin.math.abs(a[i] - b[i])
        return s / a.size
    }

    /** I salti: [diffs] i = differenza tra il fotogramma i e i+1; si restituisce i+1, il primo dopo il taglio. */
    fun spikes(diffs: FloatArray): List<Int> = (2 until diffs.size - 2).filter { i ->
        val near = maxOf(diffs[i - 2], diffs[i - 1], diffs[i + 1], diffs[i + 2])
        diffs[i] > ABS_MIN && diffs[i] > RATIO * near
    }.map { it + 1 }

    /**
     * Il pezzo fermato ai tagli: comincia dopo l'ultimo taglio dei primi [START_WINDOW_MS] (prima di [pointAt]) e
     * finisce al primo taglio dopo la fine del punto ([pointAt], o poco prima se il tocco è arrivato in ritardo).
     */
    fun trim(c: Highlights.Clip, pointAt: Long, cuts: List<Long>): Highlights.Clip {
        val endFrom = pointAt - LATE_TAP_MS
        val start = cuts.filter { it > c.startMs && it < minOf(c.startMs + START_WINDOW_MS, endFrom) }.maxOrNull() ?: c.startMs
        val end = cuts.filter { it >= endFrom && it < c.endMs && it > start }.minOrNull() ?: c.endMs
        return c.copy(startMs = start, endMs = end)
    }

    /** I pezzi di un video importato fermati ai suoi tagli; se qualcosa non va, i pezzi restano come sono. */
    fun trimAll(context: Context, video: Uri, clips: List<Highlights.Clip>, pointAt: (Highlights.Clip) -> Long): List<Highlights.Clip> {
        val t0 = System.currentTimeMillis()
        return trimEach(context, video, clips, pointAt).also { Log.i(TAG, "tagli cercati in ${clips.size} pezzi: ${System.currentTimeMillis() - t0} ms") }
    }

    private fun trimEach(context: Context, video: Uri, clips: List<Highlights.Clip>, pointAt: (Highlights.Clip) -> Long): List<Highlights.Clip> = clips.map { c ->
        runCatching {
            val at = pointAt(c)
            val cuts = detect(context, video, c.startMs, minOf(c.startMs + START_WINDOW_MS, at)) +
                detect(context, video, maxOf(c.startMs, at - LATE_TAP_MS), c.endMs)
            trim(c, at, cuts)
        }.onFailure { Log.w(TAG, "tagli non cercati", it) }.getOrDefault(c)
    }

    /** I tagli tra [fromMs] e [toMs] (ms del video): l'ora del primo fotogramma dopo ogni taglio. */
    fun detect(context: Context, video: Uri, fromMs: Long, toMs: Long): List<Long> {
        if (toMs <= fromMs) return emptyList()
        // Due fotogrammi in più per parte: il salto si confronta con i vicini.
        val from = (fromMs - 200).coerceAtLeast(0) * 1000
        val to = (toMs + 200) * 1000
        val ex = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            ex.setDataSource(context, video, null)
            val track = (0 until ex.trackCount).firstOrNull { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true } ?: return emptyList()
            ex.selectTrack(track)
            val fmt = ex.getTrackFormat(track)
            fmt.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            val dec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!)
            codec = dec
            dec.configure(fmt, null, null, 0)
            dec.start()
            ex.seekTo(from, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val times = ArrayList<Long>()
            val diffs = ArrayList<Float>()
            var prev: FloatArray? = null
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            val deadline = System.currentTimeMillis() + TIMEOUT_MS
            while (System.currentTimeMillis() < deadline) {
                if (!inputDone) {
                    val i = dec.dequeueInputBuffer(5_000)
                    if (i >= 0) {
                        val n = ex.readSampleData(dec.getInputBuffer(i)!!, 0)
                        if (n < 0 || ex.sampleTime > to) {
                            dec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            dec.queueInputBuffer(i, 0, n, ex.sampleTime, 0)
                            ex.advance()
                        }
                    }
                }
                val o = dec.dequeueOutputBuffer(info, 5_000)
                if (o < 0) continue
                val eos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                if (info.size > 0 && info.presentationTimeUs in from..to) {
                    dec.getOutputImage(o)?.use { img -> grid(img) }?.let { g ->
                        prev?.let { diffs += diff(it, g) }
                        times += info.presentationTimeUs / 1000
                        prev = g
                    }
                }
                dec.releaseOutputBuffer(o, false)
                if (eos || info.presentationTimeUs > to) break
            }
            return spikes(diffs.toFloatArray()).map { times[it] }.filter { it in fromMs..toMs }
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            ex.release()
        }
    }

    /** La luminanza ridotta a [GRID_W]×[GRID_H]: 4×4 campioni per casella, mediati. */
    private fun grid(img: android.media.Image): FloatArray {
        val plane = img.planes[0]
        val buf = plane.buffer
        val crop = img.cropRect
        val out = FloatArray(GRID_W * GRID_H)
        val cw = crop.width() / GRID_W.toFloat()
        val ch = crop.height() / GRID_H.toFloat()
        for (gy in 0 until GRID_H) for (gx in 0 until GRID_W) {
            var s = 0
            for (sy in 0 until 4) for (sx in 0 until 4) {
                val x = crop.left + ((gx + (sx + 0.5f) / 4) * cw).toInt()
                val y = crop.top + ((gy + (sy + 0.5f) / 4) * ch).toInt()
                s += buf.get(y * plane.rowStride + x * plane.pixelStride).toInt() and 0xff
            }
            out[gy * GRID_W + gx] = s / 16f / 255f
        }
        return out
    }

    private const val TAG = "Tagli"
}
