// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import java.util.Calendar
import java.util.TimeZone

/**
 * Un allenamento o una partita nell'elenco dei Progressi: [title] e [detail] si mostrano, gli altri campi servono ai
 * filtri (vuoti se non si sanno).
 */
data class ProgressItem(
    val id: String,
    val at: Long,
    val title: String,
    val detail: String,
    val event: String = "",
    val club: String = "",
    val opponent: String = "",
)

/**
 * Quali allenamenti o partite confrontare: periodo ([days] indietro da oggi, null = tutto), giorni della settimana
 * ([weekdays] come Calendar.DAY_OF_WEEK, vuoto = tutti) e, per le partite, torneo, circolo e avversario (null = tutti).
 */
data class ProgressFilter(
    val days: Int? = null,
    val weekdays: Set<Int> = emptySet(),
    val event: String? = null,
    val club: String? = null,
    val opponent: String? = null,
) {
    fun accepts(item: ProgressItem, now: Long, zone: TimeZone = TimeZone.getDefault()): Boolean {
        if (days != null && item.at < now - days * DAY_MS) return false
        if (weekdays.isNotEmpty() && weekdayOf(item.at, zone) !in weekdays) return false
        if (event != null && !item.event.equals(event, ignoreCase = true)) return false
        if (club != null && !item.club.equals(club, ignoreCase = true)) return false
        if (opponent != null && !item.opponent.equals(opponent, ignoreCase = true)) return false
        return true
    }

    companion object {
        private const val DAY_MS = 24 * 3600_000L

        fun weekdayOf(at: Long, zone: TimeZone = TimeZone.getDefault()): Int =
            Calendar.getInstance(zone).apply { timeInMillis = at }.get(Calendar.DAY_OF_WEEK)

        /** I valori diversi (non vuoti) di un campo, per i menu dei filtri, in ordine alfabetico. */
        fun choices(items: List<ProgressItem>, field: (ProgressItem) -> String): List<String> =
            items.map { field(it).trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }.sortedBy { it.lowercase() }
    }
}

object ProgressItems {
    /** Allenamenti dal più recente: data e ora, durata e battito medio. */
    fun workouts(list: List<WorkoutRecord>, date: (Long) -> String): List<ProgressItem> =
        list.sortedByDescending { it.startedAt }.map { w ->
            val avg = HeartStats.summary(w.heart)?.avg
            ProgressItem(w.id, w.startedAt, date(w.startedAt), Reports.duration(w.durationS * 1000L) + (avg?.let { " · $it bpm" } ?: ""))
        }

    /** Partite del giocatore [key] dalla più recente: avversari, risultato, torneo o circolo. */
    fun matches(lines: List<PlayerStats.MatchLine>, key: String, won: String, lost: String, date: (Long) -> String): List<ProgressItem> =
        lines.sortedByDescending { it.rec.startedAt ?: it.rec.updatedAt }.mapNotNull { l ->
            val side = PlayerStats.sideOf(l.rec, key) ?: return@mapNotNull null
            val su = l.rec.setup
            val opp = PlayerStats.names(su, side.other).joinToString(" / ")
            val at = l.rec.startedAt ?: l.rec.updatedAt
            val where = su.event.ifBlank { su.club }.trim()
            ProgressItem(
                id = l.rec.id, at = at,
                title = date(at) + if (opp.isNotEmpty()) " · $opp" else "",
                detail = listOf(if (l.won) won else lost, "${l.pointsWonPct}%", where).filter { it.isNotEmpty() }.joinToString(" · "),
                event = su.event.trim(), club = su.club.trim(), opponent = opp,
            )
        }
}
