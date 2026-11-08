// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.video

import android.content.Context
import android.content.Intent
import android.provider.OpenableColumns
import android.net.Uri
import android.os.Environment
import kotlinx.serialization.json.Json
import java.io.File

/**
 * I video registrati dal telefono-telecamera: per ciascuno il file dei capitoli (.json, nella cartella dell'app) e
 * il video, nella galleria ([VideoChapters.uri]) o ancora nella cartella dell'app (registrazioni vecchie o Android 9).
 */
object VideoLibrary {
    private val json = Json { ignoreUnknownKeys = true }

    data class Entry(val chaptersFile: File, val chapters: VideoChapters, val video: Uri)

    private fun dir(context: Context): File = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir

    /**
     * Dal più recente; solo quelli il cui video c'è ancora. I capitoli di un video tolto dalla galleria (o da dove
     * era stato importato) non servono più: si cancellano.
     */
    fun list(context: Context): List<Entry> =
        dir(context).listFiles { f -> f.extension == "json" }.orEmpty().mapNotNull { f ->
            load(context, f) ?: run {
                if (runCatching { json.decodeFromString(VideoChapters.serializer(), f.readText()) }.isSuccess) f.delete()
                null
            }
        }.sortedByDescending { it.chapters.recordedAt.takeIf { t -> t > 0 } ?: it.chaptersFile.lastModified() }

    fun load(context: Context, f: File): Entry? {
        val ch = runCatching { json.decodeFromString(VideoChapters.serializer(), f.readText()) }.getOrNull() ?: return null
        val local = File(f.parentFile, ch.video)
        val uri = ch.uri?.let(Uri::parse)?.takeIf { u -> runCatching { context.contentResolver.openFileDescriptor(u, "r")?.close() }.isSuccess }
            ?: local.takeIf { it.exists() }?.let(Uri::fromFile)
            ?: return null
        return Entry(f, ch, uri)
    }

    /**
     * Elimina video (anche dalla galleria) e capitoli. Un video importato resta dov'era: si tolgono solo i capitoli
     * e il permesso di leggerlo.
     */
    fun delete(context: Context, e: Entry) {
        if (e.chapters.imported) {
            e.chapters.uri?.let { u ->
                runCatching { context.contentResolver.releasePersistableUriPermission(Uri.parse(u), Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            }
        } else {
            e.chapters.uri?.let { u -> runCatching { context.contentResolver.delete(Uri.parse(u), null, null) } }
            File(e.chaptersFile.parentFile, e.chapters.video).delete()
        }
        e.chaptersFile.delete()
    }

    fun save(e: Entry, ch: VideoChapters): Entry {
        e.chaptersFile.writeText(json.encodeToString(VideoChapters.serializer(), ch))
        return e.copy(chapters = ch)
    }

    /**
     * Un video scelto dal telefono: si tiene il permesso di leggerlo e si creano i capitoli, ancora senza punti.
     * Se lo stesso video è già stato importato si riapre quello (con i punti già segnati), senza doppioni.
     */
    fun import(context: Context, uri: Uri): Entry? = runCatching {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        list(context).filter { it.chapters.imported && it.chapters.uri == uri.toString() }
            .maxByOrNull { it.chapters.points.size }?.let { return@runCatching it }
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: "video"
        val f = File(dir(context), "IMP_${System.currentTimeMillis()}.json")
        val ch = VideoChapters(name, emptyList(), uri.toString(), System.currentTimeMillis(), imported = true)
        f.writeText(json.encodeToString(VideoChapters.serializer(), ch))
        Entry(f, ch, uri)
    }.getOrNull()
}
