// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.video

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.util.Calendar
import java.util.TimeZone

/**
 * Il giorno in cui è stato girato un video importato. La data del file non va bene: per un video scaricato o
 * esportato è quella del download. Si usa la data scritta dentro il video dalla fotocamera, o quella nel nome del
 * file ("2026-10-24 23-16-57.mp4", "VID_20261024_231657.mp4"); se non c'è né l'una né l'altra, null (meglio nessuna
 * data che una sbagliata).
 */
object VideoDates {
    fun guess(context: Context, video: Uri, fileName: String, now: Long = System.currentTimeMillis()): Long? {
        val meta = runCatching {
            val r = MediaMetadataRetriever()
            try { r.setDataSource(context, video); r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE) } finally { r.release() }
        }.getOrNull()
        return fromMeta(meta, now) ?: fromName(fileName, now)
    }

    /** "20261024T211657.000Z" (ora UTC, come la scrivono le fotocamere); "19040101…" vuol dire nessuna data. */
    fun fromMeta(meta: String?, now: Long): Long? {
        val m = Regex("""^(\d{4})(\d{2})(\d{2})T(\d{2})(\d{2})(\d{2})""").find(meta?.trim().orEmpty()) ?: return null
        val (y, mo, d, h, mi, s) = m.destructured
        return at(y.toInt(), mo.toInt(), d.toInt(), h.toInt(), mi.toInt(), s.toInt(), TimeZone.getTimeZone("UTC"), now)
    }

    /** Una data nel nome del file: anno-mese-giorno, con o senza separatori, e l'ora se segue. */
    fun fromName(name: String, now: Long): Long? {
        val m = Regex("""(?<!\d)(20\d{2})[-_.]?(\d{2})[-_.]?(\d{2})(?:[ _T-]?(\d{2})[-_.:]?(\d{2})(?:[-_.:]?(\d{2}))?)?(?!\d)""").find(name) ?: return null
        val g = m.groupValues
        return at(g[1].toInt(), g[2].toInt(), g[3].toInt(), g[4].toIntOrNull() ?: 12, g[5].toIntOrNull() ?: 0, g[6].toIntOrNull() ?: 0, TimeZone.getDefault(), now)
    }

    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int, s: Int, tz: TimeZone, now: Long): Long? {
        if (y < 2000 || mo !in 1..12 || d !in 1..31 || h > 23 || mi > 59 || s > 59) return null
        val t = Calendar.getInstance(tz).apply { clear(); set(y, mo - 1, d, h, mi, s) }.timeInMillis
        // Una data nel futuro non è quella del video.
        return t.takeIf { it <= now + 24 * 3_600_000L }
    }
}
