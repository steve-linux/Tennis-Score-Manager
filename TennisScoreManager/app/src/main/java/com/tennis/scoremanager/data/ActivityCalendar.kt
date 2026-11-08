// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import java.util.Calendar

/**
 * Il calendario di Progressi: quali giorni hanno partite e allenamenti, e da quante settimane di fila si gioca o ci
 * si allena. Le settimane cominciano il lunedì.
 */
object ActivityCalendar {
    /** Cosa c'è in un giorno: gli id delle partite e degli allenamenti. */
    data class Day(val matches: List<String> = emptyList(), val workouts: List<String> = emptyList())

    private fun cal(t: Long) = Calendar.getInstance().apply { firstDayOfWeek = Calendar.MONDAY; minimalDaysInFirstWeek = 4; timeInMillis = t }

    fun dayStart(t: Long): Long = cal(t).apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis

    /** Il lunedì della settimana di [t], a mezzanotte. */
    fun weekStart(t: Long): Long = cal(dayStart(t)).apply { while (get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) add(Calendar.DAY_OF_YEAR, -1) }.timeInMillis

    /** I giorni (a mezzanotte) con quello che c'è: partite e allenamenti come (id, quando). */
    fun byDay(matches: List<Pair<String, Long>>, workouts: List<Pair<String, Long>>): Map<Long, Day> {
        val out = HashMap<Long, Day>()
        for ((id, t) in matches) dayStart(t).let { d -> out[d] = (out[d] ?: Day()).let { it.copy(matches = it.matches + id) } }
        for ((id, t) in workouts) dayStart(t).let { d -> out[d] = (out[d] ?: Day()).let { it.copy(workouts = it.workouts + id) } }
        return out
    }

    /**
     * Da quante settimane di fila si gioca o ci si allena: si conta all'indietro da questa settimana (o dalla scorsa,
     * se questa è ancora vuota: la settimana non è finita).
     */
    fun weekStreak(times: List<Long>, now: Long): Int {
        val weeks = times.map(::weekStart).toSet()
        var w = weekStart(now)
        if (w !in weeks) w = cal(w).apply { add(Calendar.DAY_OF_YEAR, -7) }.timeInMillis
        var n = 0
        while (w in weeks) {
            n++
            w = cal(w).apply { add(Calendar.DAY_OF_YEAR, -7) }.timeInMillis
        }
        return n
    }

    /**
     * Le caselle del mese ([year], [month] 0-11), a righe di 7 dal lunedì: null prima del primo e dopo l'ultimo
     * giorno, se no la mezzanotte del giorno.
     */
    fun monthGrid(year: Int, month: Int): List<Long?> {
        val first = Calendar.getInstance().apply { clear(); set(year, month, 1) }
        val lead = (first.get(Calendar.DAY_OF_WEEK) + 5) % 7
        val days = first.getActualMaximum(Calendar.DAY_OF_MONTH)
        val cells = MutableList<Long?>(lead) { null }
        for (d in 1..days) cells += Calendar.getInstance().apply { clear(); set(year, month, d) }.timeInMillis
        while (cells.size % 7 != 0) cells += null
        return cells
    }
}
