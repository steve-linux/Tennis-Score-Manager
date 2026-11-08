// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.video

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.io.File
import java.nio.ByteBuffer

/**
 * Unisce i pezzi da 4 s della memoria ([ReplayRecorder.cutRange]) in un video solo, senza ricodificare: vengono tutti
 * dallo stesso codificatore, quindi basta rimetterli in fila coi loro tempi. Così ogni colpo nelle cartelle di
 * Documenti è un MP4 che si apre e si condivide.
 */
object ClipJoin {
    private const val TAG = "ClipJoin"
    /** Nome del video unito di una clip. */
    const val VIDEO = "video.mp4"

    /**
     * Mette in [out] i pezzi [parts] uno dopo l'altro; [starts] sono le loro ore d'inizio (ms), da cui si prendono gli
     * scarti tra un pezzo e l'altro. false se non riesce (il file a metà si cancella).
     */
    fun join(parts: List<File>, starts: List<Long>, out: File): Boolean {
        if (parts.isEmpty() || starts.size != parts.size) return false
        var muxer: MediaMuxer? = null
        return runCatching {
            val m = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4).also { muxer = it }
            // Tracce (video e, se c'è, audio) dal primo pezzo: gli altri hanno lo stesso formato.
            val tracks = HashMap<String, Int>()
            MediaExtractor().apply { setDataSource(parts[0].absolutePath) }.let { ex ->
                for (i in 0 until ex.trackCount) {
                    val f = ex.getTrackFormat(i)
                    val kind = kind(f) ?: continue
                    if (kind !in tracks) tracks[kind] = m.addTrack(f)
                }
                ex.release()
            }
            if ("video" !in tracks) error("nessuna traccia video")
            m.start()
            val last = HashMap<Int, Long>()
            val buf = ByteBuffer.allocate(8 shl 20)
            val info = MediaCodec.BufferInfo()
            for ((k, p) in parts.withIndex()) {
                val ex = MediaExtractor().apply { setDataSource(p.absolutePath) }
                val map = HashMap<Int, Int>()
                for (i in 0 until ex.trackCount) {
                    val t = kind(ex.getTrackFormat(i))?.let { tracks[it] } ?: continue
                    ex.selectTrack(i)
                    map[i] = t
                }
                val offsetUs = (starts[k] - starts[0]) * 1000
                while (true) {
                    val n = ex.readSampleData(buf, 0)
                    if (n < 0) break
                    val t = map[ex.sampleTrackIndex]
                    if (t != null) {
                        val us = ex.sampleTime + offsetUs
                        // Il muxer vuole tempi che crescono: un pezzo che si accavalla di qualche ms col precedente perde
                        // solo quei campioni.
                        if (us > (last[t] ?: -1L)) {
                            val key = ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0
                            info.set(0, n, us, if (key) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                            m.writeSampleData(t, buf, info)
                            last[t] = us
                        }
                    }
                    ex.advance()
                }
                ex.release()
            }
            m.stop()
            true
        }.onFailure { Log.w(TAG, "unione non riuscita", it) }.also {
            runCatching { muxer?.release() }
            if (it.isFailure) out.delete()
        }.getOrDefault(false)
    }

    private fun kind(f: MediaFormat): String? {
        val mime = f.getString(MediaFormat.KEY_MIME) ?: return null
        return when {
            mime.startsWith("video/") -> "video"
            mime.startsWith("audio/") -> "audio"
            else -> null
        }
    }

    /**
     * Copia la clip [src] (pezzi, [ReplayRecorder.STARTS], analisi e immagini) in [out] con i pezzi uniti in [VIDEO].
     * Una clip senza video (cancellati) copia solo il resto. false se qualcosa non si copia.
     */
    fun joinDir(src: File, out: File): Boolean = runCatching {
        out.mkdirs()
        val parts = src.listFiles { f -> f.extension == "mp4" }.orEmpty().sortedBy { it.name }
        val starts = runCatching { File(src, ReplayRecorder.STARTS).readLines().mapNotNull { it.trim().toLongOrNull() } }.getOrDefault(emptyList())
        for (f in src.listFiles { f -> f.isFile && f.extension != "mp4" && f.name != ReplayRecorder.STARTS }.orEmpty()) {
            f.copyTo(File(out, f.name), overwrite = true)
        }
        when {
            parts.isEmpty() -> starts.firstOrNull()?.let { File(out, ReplayRecorder.STARTS).writeText(it.toString()) }
            parts.size == 1 && parts[0].name == VIDEO || starts.size != parts.size -> {
                // Già unita, o senza le ore dei pezzi: si copia com'è.
                for (p in parts) p.copyTo(File(out, p.name), overwrite = true)
                File(src, ReplayRecorder.STARTS).takeIf { it.exists() }?.copyTo(File(out, ReplayRecorder.STARTS), overwrite = true)
            }
            else -> {
                if (!join(parts, starts, File(out, VIDEO))) error("unione")
                File(out, ReplayRecorder.STARTS).writeText(starts[0].toString())
            }
        }
        true
    }.onFailure { Log.w(TAG, "copia della clip non riuscita", it) }.getOrDefault(false)
}
