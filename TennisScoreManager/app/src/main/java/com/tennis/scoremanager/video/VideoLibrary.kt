// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.video

import android.content.Context
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

    /** Dal più recente; solo quelli il cui video c'è ancora. */
    fun list(context: Context): List<Entry> =
        dir(context).listFiles { f -> f.extension == "json" }.orEmpty().mapNotNull { f -> load(context, f) }
            .sortedByDescending { it.chapters.recordedAt.takeIf { t -> t > 0 } ?: it.chaptersFile.lastModified() }

    fun load(context: Context, f: File): Entry? {
        val ch = runCatching { json.decodeFromString(VideoChapters.serializer(), f.readText()) }.getOrNull() ?: return null
        val local = File(f.parentFile, ch.video)
        val uri = ch.uri?.let(Uri::parse)?.takeIf { u -> runCatching { context.contentResolver.openFileDescriptor(u, "r")?.close() }.isSuccess }
            ?: local.takeIf { it.exists() }?.let(Uri::fromFile)
            ?: return null
        return Entry(f, ch, uri)
    }

    /** Elimina video (anche dalla galleria) e capitoli. */
    fun delete(context: Context, e: Entry) {
        e.chapters.uri?.let { u -> runCatching { context.contentResolver.delete(Uri.parse(u), null, null) } }
        File(e.chaptersFile.parentFile, e.chapters.video).delete()
        e.chaptersFile.delete()
    }
}
