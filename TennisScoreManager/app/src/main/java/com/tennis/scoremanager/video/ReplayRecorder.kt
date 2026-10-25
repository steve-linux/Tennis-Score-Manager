// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.video

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import com.pedro.library.base.recording.BaseRecordController
import com.pedro.library.base.recording.RecordController
import java.io.File
import java.io.FileDescriptor
import java.nio.ByteBuffer

/**
 * Registrazione del telefono-telecamera, al posto di quella di RootEncoder. Due uscite dagli stessi fotogrammi
 * codificati (niente doppia codifica):
 *  - il video della partita, solo tra [startFile] e [stopFile];
 *  - una memoria degli ultimi 2 minuti circa in pezzi da [SEGMENT_US] (sempre, finché la telecamera è aperta), da cui si
 *    prende la moviola con [cut]. Un MP4 si legge solo quando è chiuso: per questo i pezzi sono corti.
 * Ogni pezzo e il video cominciano da un fotogramma chiave. I fotogrammi arrivano dai thread del codificatore.
 */
class ReplayRecorder(private val dir: File) : BaseRecordController() {

    /** Un pezzo chiuso della memoria: [startMs]/[endMs] sull'orologio del telefono. */
    data class Segment(val file: File, val startMs: Long, val endMs: Long)

    /** Un MP4 in scrittura: tracce, primo istante (per far partire il file da zero) e se ha già visto la chiave. */
    private class Out(val file: File, video: MediaFormat, audio: MediaFormat?) {
        val muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val videoTrack = muxer.addTrack(video)
        val audioTrack = audio?.let { muxer.addTrack(it) } ?: -1
        var baseUs = -1L
        var startMs = 0L
        var lastVideoUs = -1L
        var lastAudioUs = -1L

        init {
            muxer.start()
        }

        fun close() {
            runCatching { muxer.stop() }
            runCatching { muxer.release() }
        }
    }

    private val lock = Any()
    private var videoFormat: MediaFormat? = null
    private var audioFormat: MediaFormat? = null
    private var segment: Out? = null
    private val segments = ArrayDeque<Segment>()
    private var main: Out? = null
    /** Video della partita chiesto ma non ancora partito (aspetta il prossimo fotogramma chiave). */
    private var mainPending: File? = null
    private var counter = 0

    init {
        dir.mkdirs()
        dir.listFiles()?.forEach { it.delete() }
    }

    // ---------------------------------------------------------------- RecordController

    /** Da StreamBase.startRecord: accende la memoria dei pezzi (il percorso non serve). */
    override fun startRecord(path: String, listener: RecordController.Listener?, tracks: RecordController.RecordTracks) {
        this.listener = listener
        this.tracks = tracks
        status = RecordController.Status.RECORDING
        listener?.onStatusChange(status)
        requestKeyFrame?.onRequestKeyFrame()
    }

    override fun startRecord(fd: FileDescriptor, listener: RecordController.Listener?, tracks: RecordController.RecordTracks) =
        startRecord("", listener, tracks)

    override fun stopRecord() {
        synchronized(lock) {
            closeSegment()
            main?.close()
            main = null
            mainPending = null
        }
        status = RecordController.Status.STOPPED
        listener?.onStatusChange(status)
    }

    override fun setVideoFormat(format: MediaFormat) {
        videoFormat = format
    }

    override fun setAudioFormat(format: MediaFormat) {
        audioFormat = format
    }

    override fun resetFormats() {
        videoFormat = null
        audioFormat = null
    }

    override fun recordVideo(buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        if (status != RecordController.Status.RECORDING) return
        val video = videoFormat ?: return
        val key = info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0
        synchronized(lock) {
            try {
                // Memoria: un pezzo nuovo a ogni fotogramma chiave dopo SEGMENT_US.
                val seg = segment
                if (key && (seg == null || info.presentationTimeUs - seg.baseUs >= SEGMENT_US)) {
                    closeSegment()
                    segment = Out(File(dir, "seg_${counter++}.mp4"), video, audioFormat).also { it.startMs = System.currentTimeMillis() }
                }
                segment?.let { write(it, it.videoTrack, buffer, info, video = true) }
                // Video della partita: parte dal primo fotogramma chiave dopo "Registra".
                val pending = mainPending
                if (pending != null && key) {
                    main = Out(pending, video, audioFormat)
                    mainPending = null
                }
                main?.let { write(it, it.videoTrack, buffer, info, video = true) }
            } catch (e: Exception) {
                Log.w(TAG, "fotogramma non scritto", e)
            }
        }
    }

    override fun recordAudio(buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        if (status != RecordController.Status.RECORDING) return
        synchronized(lock) {
            try {
                segment?.let { if (it.audioTrack >= 0) write(it, it.audioTrack, buffer, info, video = false) }
                main?.let { if (it.audioTrack >= 0) write(it, it.audioTrack, buffer, info, video = false) }
            } catch (e: Exception) {
                Log.w(TAG, "audio non scritto", e)
            }
        }
    }

    /** Tempi dal primo fotogramma del file; l'audio prima del primo fotogramma video si salta. */
    private fun write(o: Out, track: Int, buffer: ByteBuffer, info: MediaCodec.BufferInfo, video: Boolean) {
        if (video && o.baseUs < 0) o.baseUs = info.presentationTimeUs
        if (o.baseUs < 0 || info.size <= 0) return
        val t = info.presentationTimeUs - o.baseUs
        if (t < 0) return
        // Il muxer vuole tempi che crescono, traccia per traccia.
        if (video) {
            if (t <= o.lastVideoUs) return
            o.lastVideoUs = t
        } else {
            if (t <= o.lastAudioUs) return
            o.lastAudioUs = t
        }
        val copy = MediaCodec.BufferInfo().apply { set(info.offset, info.size, t, info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG.inv()) }
        val b = buffer.duplicate()
        b.position(info.offset)
        b.limit(info.offset + info.size)
        o.muxer.writeSampleData(track, b, copy)
    }

    private fun closeSegment() {
        val s = segment ?: return
        segment = null
        s.close()
        if (s.lastVideoUs > 0) {
            segments.addLast(Segment(s.file, s.startMs, System.currentTimeMillis()))
        } else {
            s.file.delete()
        }
        while (segments.size > KEEP) segments.removeFirst().file.delete()
    }

    // ---------------------------------------------------------------- per la telecamera

    /** Comincia a scrivere il video della partita (dal prossimo fotogramma chiave, chiesto subito). */
    fun startFile(file: File) {
        synchronized(lock) { mainPending = file }
        requestKeyFrame?.onRequestKeyFrame()
    }

    fun stopFile() {
        synchronized(lock) {
            main?.close()
            main = null
            mainPending = null
        }
    }

    val fileActive: Boolean get() = synchronized(lock) { main != null || mainPending != null }

    /**
     * Moviola: chiude il pezzo in corso (così si legge) e restituisce, copiati in [out], i pezzi che coprono
     * gli ultimi [beforeMs] fino a [atMs] (ora del telefono). Il pezzo successivo parte al prossimo fotogramma chiave.
     */
    fun cut(atMs: Long, beforeMs: Long, out: File): List<File> = cutRange(atMs - beforeMs, atMs, out)

    /** I pezzi che coprono da [fromMs] a [toMs] (ora del telefono), come [cut]. */
    fun cutRange(fromMs: Long, toMs: Long, out: File): List<File> {
        val chosen = synchronized(lock) {
            closeSegment()
            segments.filter { it.endMs >= fromMs && it.startMs <= toMs }
        }
        requestKeyFrame?.onRequestKeyFrame()
        out.mkdirs()
        return chosen.mapIndexed { i, s -> File(out, "part_$i.mp4").also { s.file.copyTo(it, overwrite = true) } }
    }

    companion object {
        private const val TAG = "Moviola"
        /** Pezzi da 4 secondi: la moviola arriva entro un attimo e copre un punto intero. */
        const val SEGMENT_US = 4_000_000L
        /**
         * 30 pezzi, circa 2 minuti (~90 MB a 6 Mbit/s, cancellati man mano): anche una contestazione chiamata tardi
         * trova ancora il suo punto.
         */
        private const val KEEP = 30
        /** "Ultimi secondi" quando il punto non è stato segnato. */
        const val DEFAULT_S = 30
    }
}
