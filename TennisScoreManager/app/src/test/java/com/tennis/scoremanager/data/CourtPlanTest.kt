// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class CourtPlanTest {
    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int = 0): Long = Calendar.getInstance().apply { set(y, m - 1, d, h, min, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
    private val settings = CourtSettings(openHour = 8, closeHour = 23, slotMinutes = 60, centsPerHour = 1_000, centsPerHourLights = 1_600, lightsFrom = 18)

    @Test
    fun slotsOfTheDay() {
        val s = CourtPlans.slots(at(2026, 11, 14, 15), settings)
        assertEquals(15, s.size)
        assertEquals(at(2026, 11, 14, 8), s.first())
        assertEquals(at(2026, 11, 14, 22), s.last())
        assertEquals(30, CourtPlans.slots(at(2026, 11, 14, 15), settings.copy(slotMinutes = 30)).size)
    }

    @Test
    fun priceWithAndWithoutLights() {
        assertEquals(1_000, CourtPlans.price(settings, at(2026, 11, 14, 10), 60, BookingKind.PLAY))
        assertEquals(1_600, CourtPlans.price(settings, at(2026, 11, 14, 19), 60, BookingKind.PLAY))
        // 17:30-18:30: mezz'ora di giorno e mezz'ora con le luci.
        assertEquals(1_300, CourtPlans.price(settings, at(2026, 11, 14, 17, 30), 60, BookingKind.PLAY))
        assertEquals(0, CourtPlans.price(settings, at(2026, 11, 14, 10), 60, BookingKind.CLOSED))
        assertTrue(CourtPlans.needsLights(settings, at(2026, 11, 14, 17, 30), 60))
        assertFalse(CourtPlans.needsLights(settings, at(2026, 11, 14, 17), 60))
    }

    @Test
    fun conflictsWithBookingsAndTournamentMatches() {
        val b = CourtBooking("b1", "Campo 1", at(2026, 11, 14, 10), 60)
        val plan = CourtPlan(settings, listOf(b))
        val t = ClubTournament("t", "Sociale", matches = listOf(CTMatch("m", 1, 0, a = "e1", b = "e2", court = "Campo 2", at = at(2026, 11, 14, 9))))
        assertEquals(1, CourtPlans.conflicts(plan, listOf(t), "Campo 1", at(2026, 11, 14, 10, 30), at(2026, 11, 14, 11, 30)).size)
        // Subito dopo, nessuna sovrapposizione; la prenotazione che si sta cambiando non conta.
        assertTrue(CourtPlans.conflicts(plan, listOf(t), "Campo 1", at(2026, 11, 14, 11), at(2026, 11, 14, 12)).isEmpty())
        assertTrue(CourtPlans.conflicts(plan, listOf(t), "Campo 1", at(2026, 11, 14, 10), at(2026, 11, 14, 11), except = "b1").isEmpty())
        // La partita del torneo dura 90 minuti: occupa il Campo 2 fino alle 10:30.
        assertEquals(1, CourtPlans.conflicts(plan, listOf(t), "Campo 2", at(2026, 11, 14, 10), at(2026, 11, 14, 11)).size)
        assertEquals(2, CourtPlans.occupied(plan, listOf(t), at(2026, 11, 14, 0)).size)
        assertEquals(60, CourtPlans.busyMinutes(CourtPlans.occupied(plan, listOf(t), at(2026, 11, 14, 0)), "Campo 1"))
    }

    @Test
    fun weeklySkipsBusyDates() {
        val busy = CourtBooking("x", "Campo 1", at(2026, 11, 21, 18), 60)
        val plan = CourtPlan(settings, listOf(busy))
        val lesson = CourtBooking("l", "Campo 1", at(2026, 11, 14, 18), 60, BookingKind.LESSON)
        val (ok, skipped) = CourtPlans.weekly(plan, emptyList(), lesson, 4)
        assertEquals(3, ok.size)
        assertEquals(listOf(at(2026, 11, 21, 18)), skipped)
        assertEquals("l", ok.first().id)
        assertEquals(1, ok.map { it.series }.toSet().size)
        assertEquals(at(2026, 12, 5, 18), ok.last().start)
    }

    @Test
    fun splitAmongPlayers() {
        assertEquals(listOf(334, 333, 333), CourtPlans.split(1_000, 3))
        assertEquals(listOf(400, 400, 400, 400), CourtPlans.split(1_600, 4))
        assertTrue(CourtPlans.split(1_000, 0).isEmpty())
    }

    @Test
    fun blocksStopBookingsButNotCourseLessons() {
        // Scuola tennis: Campo 1, lunedì e mercoledì 15-18, da novembre a dicembre.
        val course = CourtBlock("k", "Scuola tennis", BlockReason.COURSE, listOf("Campo 1"), at(2026, 11, 1, 0), at(2026, 12, 31, 0), setOf(1, 3), 15 * 60, 18 * 60)
        val plan = CourtPlan(settings, blocks = listOf(course))
        val monday = at(2026, 11, 16, 16)
        assertEquals(1, CourtPlans.weekday(monday))
        assertEquals(listOf(course), CourtPlans.blockedBy(plan, "Campo 1", monday, monday + 3_600_000, BookingKind.PLAY))
        // Le lezioni del corso sì; il Campo 2 è libero; il martedì e dopo le 18 pure.
        assertTrue(CourtPlans.blockedBy(plan, "Campo 1", monday, monday + 3_600_000, BookingKind.LESSON).isEmpty())
        assertTrue(CourtPlans.blockedBy(plan, "Campo 2", monday, monday + 3_600_000, BookingKind.PLAY).isEmpty())
        assertTrue(CourtPlans.blockedBy(plan, "Campo 1", at(2026, 11, 17, 16), at(2026, 11, 17, 17), BookingKind.PLAY).isEmpty())
        assertTrue(CourtPlans.blockedBy(plan, "Campo 1", at(2026, 11, 16, 18), at(2026, 11, 16, 19), BookingKind.PLAY).isEmpty())
        // Dopo la fine del periodo non vale più.
        assertTrue(CourtPlans.blockedBy(plan, "Campo 1", at(2027, 1, 4, 16), at(2027, 1, 4, 17), BookingKind.PLAY).isEmpty())
        // Torneo su tutti i campi, tutto il giorno: ferma anche le lezioni, e la ripetizione settimanale lo salta.
        val tour = CourtBlock("t", "Sociale", BlockReason.TOURNAMENT, emptyList(), at(2026, 11, 21, 0), at(2026, 11, 22, 0))
        val p2 = plan.copy(blocks = plan.blocks + tour)
        assertEquals(listOf(tour), CourtPlans.blockedBy(p2, "Campo 2", at(2026, 11, 21, 10), at(2026, 11, 21, 11), BookingKind.LESSON))
        val (ok, skipped) = CourtPlans.weekly(p2, emptyList(), CourtBooking("b", "Campo 2", at(2026, 11, 14, 10), 60), 3)
        assertEquals(2, ok.size); assertEquals(listOf(at(2026, 11, 21, 10)), skipped)
        // I blocchi non contano come ore occupate.
        assertEquals(0, CourtPlans.busyMinutes(CourtPlans.occupied(p2, emptyList(), at(2026, 11, 21, 0)), "Campo 2"))
    }

    @Test
    fun pricesPerCourtWithLightsAndHeating() {
        val covered = CourtInfo("Campo 3", Surface.HARD, covered = true, lights = true, heating = true, centsPerHour = 2_000, centsPerHourLights = 2_500, heatingCentsPerHour = 600)
        val dark = CourtInfo("Campo 4", Surface.CLAY, lights = false)
        val st = settings.copy(courts = listOf(covered, dark))
        assertEquals(2_000, CourtPlans.price(st, at(2026, 11, 14, 10), 60, BookingKind.PLAY, "Campo 3"))
        assertEquals(3_100, CourtPlans.price(st, at(2026, 11, 14, 19), 60, BookingKind.PLAY, "Campo 3", heating = true))
        // Senza luci resta il prezzo normale (quello generale, il campo non ne ha uno suo); il riscaldamento non c'è.
        assertEquals(1_000, CourtPlans.price(st, at(2026, 11, 14, 19), 60, BookingKind.PLAY, "Campo 4", heating = true))
        // Un campo senza scheda ha i prezzi generali.
        assertEquals(1_600, CourtPlans.price(st, at(2026, 11, 14, 19), 60, BookingKind.PLAY, "Campo 9"))
    }
}
