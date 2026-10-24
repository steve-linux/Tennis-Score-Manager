// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.video

import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Monta gli highlights sul telefono (Media3 Transformer): i pezzi scelti da [Highlights.select], uno dopo l'altro,
 * senza scritte; nei video importati con la grafica del punteggio (in quelli registrati c'è già). Il risultato va
 * in galleria (Film/Tennis Score Manager) con i suoi capitoli, così si rivede punto per punto come gli altri video.
 */
@UnstableApi
class HighlightMaker(context: Context) {
    private val app = context.applicationContext
    private var transformer: Transformer? = null
    private val main = Handler(Looper.getMainLooper())

    /** Da chiamare sul thread principale. [onProgress] in percento; [onDone] col file dei capitoli, o null se fallisce. */
    fun make(
        e: VideoLibrary.Entry,
        clips: List<Highlights.Clip>,
        onProgress: (Int) -> Unit,
        onDone: (File?) -> Unit,
    ) {
        val ch = e.chapters
        val width = frameWidth(e) ?: 1920
        // Le misure della grafica sono pensate per 1920 di larghezza: si adattano al video.
        val scale = width / 1920f * 1.2f
        val items = clips.map { c ->
            val item = MediaItem.Builder().setUri(e.video).setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder().setStartPositionMs(c.startMs).setEndPositionMs(c.endMs).build(),
            ).build()
            // Nei video importati il punteggio non è nel video: lo si disegna, com'era prima del punto.
            val effects = ch.rules?.let { rules ->
                val before = PointMarks.stateAfter(rules, ch.points, c.index)
                val bmp = OverlayRenderer.render(ScoreOverlay.from(before, ch.names, null, ""), scale)
                Effects(listOf(), listOf(OverlayEffect(listOf(BitmapOverlay.createStaticBitmapOverlay(bmp, at(-0.94f, -0.88f, -1f, -1f))))))
            } ?: Effects.EMPTY
            EditedMediaItem.Builder(item).setEffects(effects).build()
        }
        val dir = app.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: app.filesDir
        val out = File(dir, "TSM_highlights_${System.currentTimeMillis()}.mp4")
        val t = Transformer.Builder(app).addListener(object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                transformer = null
                Thread { val f = finish(e, clips, out); main.post { onDone(f) } }.start()
            }

            override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                Log.w(TAG, "montaggio non riuscito", exportException)
                transformer = null
                out.delete()
                onDone(null)
            }
        }).build()
        transformer = t
        t.start(Composition.Builder(EditedMediaItemSequence.Builder(items).build()).build(), out.absolutePath)
        // Avanzamento: Transformer lo dice solo se glielo si chiede.
        val holder = ProgressHolder()
        val poll = object : Runnable {
            override fun run() {
                val tr = transformer ?: return
                if (tr.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) onProgress(holder.progress)
                main.postDelayed(this, 500)
            }
        }
        main.post(poll)
    }

    fun cancel() {
        transformer?.cancel()
        transformer = null
    }

    /** Ancoraggi: (x, y) del punto del video e del punto della scritta da far coincidere, da -1 a 1. */
    private fun at(bx: Float, by: Float, ox: Float, oy: Float) =
        StaticOverlaySettings.Builder().setBackgroundFrameAnchor(bx, by).setOverlayFrameAnchor(ox, oy).build()

    private fun frameWidth(e: VideoLibrary.Entry): Int? = runCatching {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(app, e.video)
            val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toInt()
            val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toInt()
            val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toInt() ?: 0
            if (rot == 90 || rot == 270) h else w
        } finally {
            r.release()
        }
    }.getOrNull()

    /** Copia in galleria e capitoli del montaggio: ogni pezzo è un punto, con l'ora nel nuovo video. */
    private fun finish(e: VideoLibrary.Entry, clips: List<Highlights.Clip>, out: File): File? = runCatching {
        var offset = 0L
        val marks = clips.map { c ->
            val p = e.chapters.points[c.index]
            val end = offset + (p.at - c.startMs)
            offset += c.durationMs
            p.copy(at = end)
        }
        var uri: String? = null
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, out.name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/Tennis Score Manager")
            }
            val u = app.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: error("insert")
            app.contentResolver.openOutputStream(u)?.use { o -> out.inputStream().use { it.copyTo(o) } } ?: error("open")
            uri = u.toString()
            out.delete()
        }
        val json = File(out.parentFile, out.nameWithoutExtension + ".json")
        json.writeText(Json.encodeToString(VideoChapters.serializer(), VideoChapters(out.name, marks, uri, System.currentTimeMillis(), e.chapters.names)))
        json
    }.onFailure { Log.w(TAG, "highlights non salvati", it) }.getOrNull()

    companion object {
        private const val TAG = "Highlights"
    }
}
