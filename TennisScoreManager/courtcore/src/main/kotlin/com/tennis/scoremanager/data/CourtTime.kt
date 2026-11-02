// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import java.util.Calendar

/**
 * Giorni, caselle e prezzi dei campi, nel fuso orario predefinito (quello del telefono nell'app, quello del
 * circolo nel server).
 */
object CourtTime {
    /** Mezzanotte del giorno di [t]. */
    fun dayStart(t: Long): Long = Calendar.getInstance().apply {
        timeInMillis = t; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun at(day: Long, hour: Int, minute: Int = 0): Long = Calendar.getInstance().apply {
        timeInMillis = dayStart(day); set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute)
    }.timeInMillis

    fun addDays(t: Long, days: Int): Long = Calendar.getInstance().apply { timeInMillis = t; add(Calendar.DAY_OF_YEAR, days) }.timeInMillis

    /** Gli inizi delle caselle del giorno, dall'apertura alla chiusura. */
    fun slots(day: Long, s: CourtSettings): List<Long> {
        val step = s.slotMinutes.coerceIn(15, 180)
        val out = mutableListOf<Long>()
        var m = s.openHour * 60
        while (m + step <= s.closeHour * 60) { out += at(day, m / 60, m % 60); m += step }
        return out
    }

    /** 1 = lunedì … 7 = domenica. */
    fun weekday(t: Long): Int = Calendar.getInstance().apply { timeInMillis = t }.get(Calendar.DAY_OF_WEEK).let { if (it == Calendar.SUNDAY) 7 else it - 1 }

    /** Il blocco vale quel giorno: dentro le date e in uno dei suoi giorni della settimana. */
    fun blockActive(b: CourtBlock, day: Long): Boolean {
        val d = dayStart(day)
        return d >= dayStart(b.from) && d <= dayStart(b.to) && (b.weekdays.isEmpty() || weekday(d) in b.weekdays)
    }

    /** Inizio e fine del blocco nel giorno [day]. */
    fun blockSpan(b: CourtBlock, day: Long): Pair<Long, Long> = at(day, b.startMin / 60, b.startMin % 60) to at(day, 0) + b.endMin * 60_000L

    /**
     * Il blocco ferma una prenotazione di tipo [kind] su [court] tra [start] e [end]? Le lezioni si possono mettere
     * nei blocchi dei corsi e le chiusure (manutenzione) dappertutto.
     */
    fun blocks(b: CourtBlock, court: String, start: Long, end: Long, kind: BookingKind): Boolean {
        if (kind == BookingKind.CLOSED || (kind == BookingKind.LESSON && b.reason == BlockReason.COURSE)) return false
        if (b.courts.isNotEmpty() && court !in b.courts) return false
        if (!blockActive(b, start)) return false
        val (bs, be) = blockSpan(b, start)
        return bs < end && be > start
    }

    /**
     * Il prezzo: le ore prima delle luci al prezzo normale, quelle dopo al prezzo con le luci (un campo senza luci
     * resta al prezzo normale), più il riscaldamento se lo si chiede. Prezzi del campo, o quelli generali. Chiuso e
     * altro: 0.
     */
    fun price(s: CourtSettings, start: Long, minutes: Int, kind: BookingKind, court: String = "", heating: Boolean = false): Int {
        if (kind == BookingKind.CLOSED || kind == BookingKind.OTHER) return 0
        val info = s.info(court)
        val base = info.centsPerHour ?: s.centsPerHour
        val dark = if (info.lights) info.centsPerHourLights ?: s.centsPerHourLights else base
        val lightsAt = at(start, s.lightsFrom)
        val end = start + minutes * 60_000L
        val darkMs = (end - maxOf(start, lightsAt)).coerceAtLeast(0)
        val dayMs = (minutes * 60_000L - darkMs).coerceAtLeast(0)
        val heat = if (heating && info.heating) minutes * 60_000L * info.heatingCentsPerHour else 0L
        return Math.round((dayMs * base + darkMs * dark + heat) / 3_600_000.0).toInt()
    }

    /** Servono le luci se la prenotazione arriva oltre l'ora delle luci. */
    fun needsLights(s: CourtSettings, start: Long, minutes: Int): Boolean = start + minutes * 60_000L > at(start, s.lightsFrom)

    /** Divide [cents] tra [n] persone; i centesimi in più ai primi (10 € in 3: 3,34 + 3,33 + 3,33). */
    fun split(cents: Int, n: Int): List<Int> {
        if (n <= 0) return emptyList()
        val base = cents / n; val extra = cents % n
        return List(n) { base + if (it < extra) 1 else 0 }
    }
}
