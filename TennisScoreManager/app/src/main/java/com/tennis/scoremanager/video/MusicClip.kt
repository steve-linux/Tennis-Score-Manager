// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.video

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.nio.ByteOrder

/**
 * Musica scelta dall'utente per le statistiche: se ne decodificano i primi secondi (MP3, AAC, OGG... quello che il
 * telefono sa leggere), con dissolvenza in entrata e in uscita, in un WAV stereo come quello di [StatsMusic].
 * Il brano resta sul telefono: non entra nell'app né nel suo codice.
 */
object MusicClip {
    private const val FADE_IN_S = 1.5
    private const val FADE_OUT_S = 2.5

    /** true se è riuscito; altrimenti si usa la musica dell'app. */
    fun writeWav(source: File, ms: Long, out: File): Boolean = runCatching {
        val (pcm, rate) = decode(source, ms)
        if (pcm.isEmpty()) return false
        StatsMusic.fade(pcm, rate, FADE_IN_S, FADE_OUT_S)
        out.outputStream().buffered().use { StatsMusic.writeWav(pcm, it, rate) }
        true
    }.getOrDefault(false)

    /** Campioni stereo interleaved da -1 a 1 dei primi [ms] del brano, e la loro frequenza di campionamento. */
    private fun decode(source: File, ms: Long): Pair<FloatArray, Int> {
        val ex = MediaExtractor()
        ex.setDataSource(source.absolutePath)
        val track = (0 until ex.trackCount).first { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
        ex.selectTrack(track)
        val fmt = ex.getTrackFormat(track)
        var rate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channels = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(fmt, null, null, 0)
        codec.start()
        val want = (ms * rate / 1000).toInt()
        val out = ArrayList<Float>(want * 2)
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        try {
            while (out.size < want * 2) {
                if (!inputDone) {
                    val i = codec.dequeueInputBuffer(10_000)
                    if (i >= 0) {
                        val n = ex.readSampleData(codec.getInputBuffer(i)!!, 0)
                        if (n < 0) {
                            codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(i, 0, n, ex.sampleTime, 0)
                            ex.advance()
                        }
                    }
                }
                val o = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        rate = codec.outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = codec.outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                    o >= 0 -> {
                        val buf = codec.getOutputBuffer(o)!!.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                        // Stereo in uscita: un brano mono va su tutti e due i canali, oltre il secondo canale si ignora.
                        while (buf.remaining() >= channels && out.size < want * 2) {
                            val l = buf.get() / 32768f
                            val r = if (channels > 1) buf.get() / 32768f else l
                            repeat(channels - 2) { buf.get() }
                            out += l
                            out += r
                        }
                        codec.releaseOutputBuffer(o, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    }
                }
            }
        } finally {
            codec.stop()
            codec.release()
            ex.release()
        }
        return out.toFloatArray() to rate
    }
}
