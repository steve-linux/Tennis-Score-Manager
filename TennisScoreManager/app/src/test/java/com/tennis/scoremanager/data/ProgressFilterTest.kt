// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class ProgressFilterTest {

    private val rome = TimeZone.getTimeZone("Europe/Rome")

    private fun at(y: Int, m: Int, d: Int, h: Int = 18): Long =
        Calendar.getInstance(rome).apply { clear(); set(y, m - 1, d, h, 0) }.timeInMillis

    private fun item(id: String, t: Long, event: String = "", club: String = "", opponent: String = "") =
        ProgressItem(id, t, id, "", event, club, opponent)

    @Test
    fun onlyWednesdays() {
        // 21 ottobre 2026 è mercoledì, il 22 giovedì.
        val wed = item("mer", at(2026, 10, 21))
        val thu = item("gio", at(2026, 10, 22))
        val f = ProgressFilter(weekdays = setOf(Calendar.WEDNESDAY))
        assertTrue(f.accepts(wed, at(2026, 10, 27), rome))
        assertFalse(f.accepts(thu, at(2026, 10, 27), rome))
        // Più giorni insieme: mercoledì o giovedì.
        assertTrue(f.copy(weekdays = setOf(Calendar.WEDNESDAY, Calendar.THURSDAY)).accepts(thu, at(2026, 10, 27), rome))
    }

    @Test
    fun lastDays() {
        val now = at(2026, 10, 27)
        val f = ProgressFilter(days = 30)
        assertTrue(f.accepts(item("a", at(2026, 10, 1)), now, rome))
        assertFalse(f.accepts(item("b", at(2026, 9, 1)), now, rome))
        assertTrue(ProgressFilter().accepts(item("b", at(2020, 1, 1)), now, rome))
    }

    @Test
    fun sameTournamentClubAndOpponent() {
        val now = at(2026, 10, 27)
        val a = item("a", at(2026, 10, 3), event = "Open di Autunno", club = "Circolo Tennis Esempio", opponent = "Verdi")
        val b = item("b", at(2026, 10, 10), event = "Torneo sociale", club = "Circolo Tennis Esempio", opponent = "Neri")
        assertTrue(ProgressFilter(event = "open di autunno").accepts(a, now, rome))
        assertFalse(ProgressFilter(event = "Open di Autunno").accepts(b, now, rome))
        assertTrue(ProgressFilter(club = "Circolo Tennis Esempio").accepts(b, now, rome))
        assertFalse(ProgressFilter(opponent = "Verdi").accepts(b, now, rome))
        assertEquals(listOf("Open di Autunno", "Torneo sociale"), ProgressFilter.choices(listOf(b, a, a.copy(id = "c"))) { it.event })
    }
}
