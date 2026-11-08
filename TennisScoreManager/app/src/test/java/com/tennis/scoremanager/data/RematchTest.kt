// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import com.tennis.scoremanager.model.Lang
import com.tennis.scoremanager.model.MatchFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class RematchTest {
    private val rome = TimeZone.getTimeZone("Europe/Rome")

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int = 0): Long =
        Calendar.getInstance(rome).apply { clear(); set(y, m - 1, d, h, min) }.timeInMillis

    @Test
    fun sameWeekdayAndHourNextWeek() {
        // Venerdì 23 ottobre 2026 alle 17:46: la rivincita venerdì 30 alle 18:00 (dopo il cambio dell'ora, sempre 18:00).
        val last = at(2026, 10, 23, 17, 46)
        assertEquals(at(2026, 10, 30, 18, 0), Rematch.suggestedTime(last, at(2026, 10, 23, 19, 30), rome))
        // Se il riepilogo si apre giorni dopo, la prima data ancora utile.
        assertEquals(at(2026, 11, 6, 18, 0), Rematch.suggestedTime(last, at(2026, 10, 30, 10, 0), rome))
        // 9:10 diventa 9:00, 9:20 diventa 9:30.
        assertEquals(at(2026, 10, 31, 9, 0), Rematch.suggestedTime(at(2026, 10, 24, 9, 10), at(2026, 10, 24, 12, 0), rome))
        assertEquals(at(2026, 10, 31, 9, 30), Rematch.suggestedTime(at(2026, 10, 24, 9, 20), at(2026, 10, 24, 12, 0), rome))
    }

    @Test
    fun calendarDuration() {
        assertEquals(60 * 60_000L, Rematch.durationMs(25 * 60_000L))
        assertEquals(90 * 60_000L, Rematch.durationMs(65 * 60_000L + 35_000))
        assertEquals(180 * 60_000L, Rematch.durationMs(5 * 3600_000L))
    }

    @Test
    fun setupAndRulesAreReused() {
        val su = SetupData(club = "Circolo Tennis Esempio", court = "1", p1a = "Rossi", p2a = "Bianchi", event = "Open di Autunno", round = "Finale", p1aRank = "4.1")
        val again = Rematch.setup(su)
        assertEquals("Rossi", again.p1a)
        assertEquals("4.1", again.p1aRank)
        assertEquals("", again.event)
        assertEquals("", again.round)
        val from = MatchOptions(format = MatchFormat.BEST_OF_THREE, noAd = true, detailedStats = true, lang = Lang.EN)
        val now = Rematch.options(MatchOptions(lang = Lang.IT, audio = false), from)
        assertTrue(now.noAd && now.detailedStats)
        // Lingua e audio restano quelli di chi usa l'app adesso.
        assertEquals(Lang.IT, now.lang)
        assertFalse(now.audio)
    }

    @Test
    fun messageHasTheEssentials() {
        val m = Rematch.message("Rivincita?", "Rossi", "Bianchi", "contro", "venerdì 30 ottobre, 18:00", "Circolo Tennis Esempio · Campo 1",
            "Ultima volta: 6-4 6-3 per Rossi", "", "Creato con Tennis Score Manager · tennis-score-manager.netlify.app")
        val lines = m.lines()
        assertEquals("🎾 Rivincita?", lines[0])
        assertEquals("Rossi contro Bianchi", lines[1])
        assertTrue(m.contains("📍 Circolo Tennis Esempio · Campo 1"))
        assertTrue(m.endsWith("netlify.app"))
        // Senza luogo e senza nota non restano righe vuote in mezzo.
        assertFalse(Rematch.message("R", "A", "B", "vs", "domani", " ", null, " ", "c").contains("📍"))
    }
}
