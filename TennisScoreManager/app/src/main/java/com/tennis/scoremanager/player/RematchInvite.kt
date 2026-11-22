// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later
package com.tennis.scoremanager.player

import android.app.Application
import android.content.Intent
import com.tennis.scoremanager.LiveMatch
import com.tennis.scoremanager.data.MatchRecord
import com.tennis.scoremanager.data.Names
import com.tennis.scoremanager.data.Reports
import com.tennis.scoremanager.data.Storage
import com.tennis.scoremanager.model.Side
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

/**
 * La rivincita: il testo dell'invito, la condivisione e l'evento nel calendario. "Come l'ultima volta", che fa
 * partire la partita, resta in MatchController.
 * Quello che serve del resto dell'app è in [PlayerHost].
 */
class RematchInvite(
    host: PlayerHost,
    private val app: Application,
    private val storage: Storage,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
) : PlayerHost by host {
    /** Nomi di un lato per l'invito: "Rossi" o "Rossi / Bianchi". */
    internal fun rematchSide(rec: MatchRecord, side: Side): String = Names(rec.setup, strings).players(side).joinToString(" / ")

    /** Luogo proposto: circolo e campo della partita, altrimenti l'indirizzo trovato. */
    fun rematchPlace(rec: MatchRecord): String =
        listOf(rec.setup.club.trim(), rec.setup.court.trim().takeIf { it.isNotEmpty() }?.let { strings.court + " " + it })
            .filterNot { it.isNullOrEmpty() }.joinToString(" · ").ifEmpty { rec.location?.address.orEmpty() }

    /** Il messaggio d'invito, nella lingua dell'app. */
    fun rematchText(m: LiveMatch, at: Long, place: String, note: String): String {
        val s = strings
        val lang = options.value.lang
        val rec = m.record
        val last = m.state.winner?.let { w -> s.rematchLast(Reports.scoreLine(m.state, w), rematchSide(rec, w)) }
        return com.tennis.scoremanager.data.Rematch.message(
            s.rematchAsk, rematchSide(rec, Side.P1), rematchSide(rec, Side.P2), s.rematchVersus,
            "${Reports.date(at, lang)}, ${Reports.time(at, lang)}", place, last, note,
            "${s.generatedWith} · ${Reports.siteUrl(lang).removePrefix("https://").removeSuffix("/")}",
        )
    }

    fun rematchShareIntent(text: String): Intent =
        Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }, strings.share)

    /** Evento nel calendario (lo crea l'app del calendario: niente permessi). */
    fun rematchCalendarIntent(m: LiveMatch, at: Long, place: String, text: String): Intent =
        Intent(Intent.ACTION_INSERT).setData(android.provider.CalendarContract.Events.CONTENT_URI)
            .putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, at)
            .putExtra(android.provider.CalendarContract.EXTRA_EVENT_END_TIME, at + com.tennis.scoremanager.data.Rematch.durationMs(m.record.clockMs))
            .putExtra(android.provider.CalendarContract.Events.TITLE, strings.rematchEvent("${rematchSide(m.record, Side.P1)} – ${rematchSide(m.record, Side.P2)}"))
            .putExtra(android.provider.CalendarContract.Events.EVENT_LOCATION, place)
            .putExtra(android.provider.CalendarContract.Events.DESCRIPTION, text)
}
