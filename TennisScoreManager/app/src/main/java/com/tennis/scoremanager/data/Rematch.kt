// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import java.util.Calendar
import java.util.TimeZone

/**
 * Rivincita: dopo una partita, un invito pronto da mandare con WhatsApp (o quello che si usa già) e da mettere in
 * calendario, e la stessa partita pronta in pagina 1. Niente account e niente server: passa tutto dai canali di chi gioca.
 */
object Rematch {
    /**
     * Quando proporla: lo stesso giorno della settimana e la stessa ora della partita (arrotondata alla mezz'ora), la
     * prima volta che cade almeno [minAheadMs] dopo [now]. Di solito la settimana dopo.
     */
    fun suggestedTime(lastStart: Long, now: Long, zone: TimeZone = TimeZone.getDefault(), minAheadMs: Long = 12 * 3600_000L): Long {
        val c = Calendar.getInstance(zone).apply { timeInMillis = lastStart }
        val minutes = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        val rounded = (minutes + 15) / 30 * 30
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        c.add(Calendar.MINUTE, rounded)
        while (c.timeInMillis < now + minAheadMs) c.add(Calendar.DAY_OF_YEAR, 7)
        return c.timeInMillis
    }

    /** Durata da mettere in calendario: quella della partita arrotondata alla mezz'ora sopra, tra 1 e 3 ore. */
    fun durationMs(clockMs: Long): Long {
        val half = 30 * 60_000L
        return ((clockMs + half - 1) / half * half).coerceIn(2 * half, 6 * half)
    }

    /** La stessa partita per la pagina 1: giocatori, circolo e campo, sport; il torneo no (una rivincita è fuori torneo). */
    fun setup(from: SetupData): SetupData = from.copy(event = "", category = "", round = "")

    /** Le stesse regole: formato, game per set, tie-break, killer point, star point, statistiche dettagliate. */
    fun options(current: MatchOptions, from: MatchOptions): MatchOptions = current.copy(
        format = from.format, gamesPerSet = from.gamesPerSet, setTiebreak = from.setTiebreak, noAd = from.noAd,
        starPoint = from.starPoint, detailedStats = from.detailedStats, tossWinner = null,
    )

    /**
     * Il messaggio: chi contro chi, quando, dove, com'è finita l'ultima volta, la nota di chi invita e, in fondo, l'app.
     * [whenText] e [result] arrivano già scritti nella lingua dell'app.
     */
    fun message(
        title: String, sideA: String, sideB: String, versus: String, whenText: String, place: String,
        lastLine: String?, note: String, credit: String,
    ): String = buildString {
        appendLine("🎾 $title")
        appendLine("$sideA $versus $sideB")
        appendLine("📅 $whenText")
        if (place.isNotBlank()) appendLine("📍 ${place.trim()}")
        lastLine?.takeIf { it.isNotBlank() }?.let { appendLine(it) }
        if (note.isNotBlank()) appendLine(note.trim())
        appendLine()
        append(credit)
    }
}
