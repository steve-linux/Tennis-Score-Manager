// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.video

import java.util.Locale

/**
 * Capitoli di un video: il punto i va dalla fine del punto precedente (o dall'inizio del video) al suo segno
 * [PointMark.at]. Da qui la navigazione per punto, game e set e i capitoli per YouTube.
 */
object Chapters {
    /** Quanto prima della fine del punto precedente si riparte: il servizio comincia un attimo dopo. */
    const val LEAD_MS = 0L

    fun start(points: List<PointMark>, i: Int): Long = if (i <= 0) 0L else points[i - 1].at + LEAD_MS

    /** Il punto in corso alla posizione [posMs] (l'ultimo se si è oltre la fine); -1 senza punti. */
    fun indexAt(points: List<PointMark>, posMs: Long): Int {
        if (points.isEmpty()) return -1
        val i = points.indexOfFirst { posMs < it.at }
        return if (i < 0) points.lastIndex else i
    }

    /** Primo punto del game (o del set) successivo; null se è già l'ultimo. */
    fun nextGame(points: List<PointMark>, i: Int): Int? = points.indices.firstOrNull { it > i && points[it].game > points[i].game }

    fun nextSet(points: List<PointMark>, i: Int): Int? = points.indices.firstOrNull { it > i && points[it].set > points[i].set }

    /** Primo punto del game in corso; se ci si è già, quello del game prima. */
    fun prevGame(points: List<PointMark>, i: Int, atStart: Boolean): Int {
        if (i < 0) return 0
        val first = points.indexOfFirst { it.game == points[i].game }
        if (!atStart || first < i) return first
        val prev = points.getOrNull(first - 1) ?: return 0
        return points.indexOfFirst { it.game == prev.game }
    }

    fun prevSet(points: List<PointMark>, i: Int, atStart: Boolean): Int {
        if (i < 0) return 0
        val first = points.indexOfFirst { it.set == points[i].set }
        if (!atStart || first < i) return first
        val prev = points.getOrNull(first - 1) ?: return 0
        return points.indexOfFirst { it.set == prev.set }
    }

    /**
     * Capitoli per la descrizione di YouTube, uno per game (YouTube vuole almeno tre capitoli, il primo a 0:00, e
     * ognuno di almeno 10 secondi: i singoli punti sono spesso più corti). [label] dà il nome del game dal set
     * (da 1) e dal numero del game nel set (da 1).
     */
    fun youtube(points: List<PointMark>, start: String, label: (set: Int, game: Int) -> String): String {
        val lines = mutableListOf("0:00 $start")
        var lastAt = 0L
        var gameInSet = 0
        for (i in points.indices) {
            val p = points[i]
            val prev = points.getOrNull(i - 1)
            if (prev != null && prev.game == p.game) continue
            gameInSet = if (prev == null || prev.set != p.set) 1 else gameInSet + 1
            val at = start(points, i)
            if (at - lastAt < 10_000 && i > 0) continue
            if (at < 10_000) continue  // il primo game parte con "0:00"
            lines += "${time(at)} ${label(p.set + 1, gameInSet)}"
            lastAt = at
        }
        return lines.joinToString("\n")
    }

    fun time(ms: Long): String {
        val s = ms / 1000
        return if (s >= 3600) String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
        else String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60)
    }
}
