// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.video

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.media3.common.C
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
import com.tennis.scoremanager.model.Side
import java.io.File
import kotlinx.serialization.json.Json

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

    /** Schermate delle statistiche da mettere in coda: la partita, le foto dei giocatori e la scritta "punti vinti". */
    class Stats(
        val card: HighlightStats.Card, val photos: Map<Side, List<Bitmap?>>, val pointsWon: String, val music: File? = null,
        val avatars: StatsCardRenderer.Avatars? = null,
        /** Il brano dell'app, copiato dalle risorse (la musica scelta dall'utente ha la precedenza). */
        val defaultMusic: File? = null,
        /** In fondo alle schermate: "Creato con Tennis Score Manager" e l'indirizzo del sito. */
        val credit: String = "", val site: String = "",
    )

    /**
     * Da chiamare sul thread principale. [onProgress] in percento; [onDone] col file dei capitoli, o null se fallisce.
     * Con [stats] le schermate delle statistiche chiudono il video, [HighlightStats.PAGE_MS] l'una.
     */
    fun make(
        e: VideoLibrary.Entry,
        clips: List<Highlights.Clip>,
        onProgress: (Int) -> Unit,
        stats: Stats? = null,
        onDone: (File?) -> Unit,
    ) {
        val ch = e.chapters
        val (width, height) = frameSize(e) ?: (1920 to 1080)
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
        } + statsItems(stats, width, height)
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
        // Le immagini delle statistiche non hanno audio: una traccia muta tiene insieme video e immagini, e sotto
        // le statistiche suona il sottofondo dell'app (seconda sequenza solo audio, dopo una pausa lunga quanto i punti).
        val sequence = EditedMediaItemSequence.Builder(items).experimentalSetForceAudioTrack(stats != null).build()
        val music = musicSequence(stats, clips.sumOf { it.durationMs })
        t.start(Composition.Builder(listOfNotNull(sequence, music)).build(), out.absolutePath)
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

    /** Larghezza e altezza del video come si vede (rotazione compresa). */
    private fun frameSize(e: VideoLibrary.Entry): Pair<Int, Int>? = runCatching {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(app, e.video)
            val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)!!.toInt()
            val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)!!.toInt()
            val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toInt() ?: 0
            if (rot == 90 || rot == 270) h to w else w to h
        } finally {
            r.release()
        }
    }.getOrNull()

    private fun musicSequence(stats: Stats?, clipsMs: Long): EditedMediaItemSequence? {
        val pages = stats?.card?.pages?.size ?: 0
        if (pages == 0) return null
        val wav = File(app.cacheDir, "hl_stats/music.wav")
        val ms = pages * HighlightStats.PAGE_MS
        // Il brano scelto dall'utente, se c'è e si legge; altrimenti quello dell'app ("Nowhere Land"); se nemmeno
        // quello si legge, la musica generata.
        val chosen = listOfNotNull(stats?.music, stats?.defaultMusic).firstOrNull { it.exists() && MusicClip.writeWav(it, ms, wav) } != null
        if (!chosen) StatsMusic.writeWav(ms, wav)
        val item = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(wav))).build()
        return EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_AUDIO)).addGap(clipsMs * 1000).addItem(item).build()
    }

    /** Una schermata per pagina, alla misura del video, come immagine ferma per [HighlightStats.PAGE_MS]. */
    private fun statsItems(stats: Stats?, width: Int, height: Int): List<EditedMediaItem> {
        stats ?: return emptyList()
        val dir = File(app.cacheDir, "hl_stats").apply { deleteRecursively(); mkdirs() }
        return stats.card.pages.mapIndexed { i, page ->
            val bmp = StatsCardRenderer.render(stats.card, page, width, height, stats.photos, stats.pointsWon, stats.avatars, stats.credit, stats.site)
            val f = File(dir, "stats_$i.png")
            f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bmp.recycle()
            val item = MediaItem.Builder().setUri(Uri.fromFile(f)).setImageDurationMs(HighlightStats.PAGE_MS).build()
            EditedMediaItem.Builder(item).setFrameRate(30).build()
        }
    }

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
