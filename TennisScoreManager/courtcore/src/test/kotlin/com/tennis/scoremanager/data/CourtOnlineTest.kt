// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class CourtOnlineTest {
    private var oldTz: TimeZone? = null

    @Before fun tz() { oldTz = TimeZone.getDefault(); TimeZone.setDefault(TimeZone.getTimeZone("Europe/Rome")) }
    @After fun restore() { TimeZone.setDefault(oldTz) }

    /** Lunedì 9 novembre 2026 alle [h]. */
    private fun mon(h: Int, m: Int = 0, plusDays: Int = 0): Long = Calendar.getInstance().apply {
        clear(); set(2026, Calendar.NOVEMBER, 9 + plusDays, h, m)
    }.timeInMillis

    private val now = mon(10)
    private val sync = ClubSync(
        club = "TC Prova", courts = listOf("Campo 1", "Campo 2"),
        settings = CourtSettings(openHour = 8, closeHour = 22, slotMinutes = 60, centsPerHour = 1000, centsPerHourLights = 1500, lightsFrom = 18),
        rules = BookingRules(daysAhead = 7, maxFuture = 2, maxMinutes = 120, cancelHours = 24, noticeMinutes = 30),
    )

    private fun ok(court: String = "Campo 1", start: Long = mon(15), minutes: Int = 60, s: ClubSync = sync, b: List<OnlineBooking> = emptyList()) =
        CourtOnline.check(s, b, "p1", court, start, minutes, now)

    @Test fun freeSlotIsBookable() = assertNull(ok())

    @Test fun rulesStopBadRequests() {
        assertEquals(BookingProblem.CLOSED, ok(s = sync.copy(rules = sync.rules.copy(open = false))))
        assertEquals(BookingProblem.UNKNOWN_COURT, ok(court = "Campo 9"))
        assertEquals(BookingProblem.UNKNOWN_COURT, ok(s = sync.copy(rules = sync.rules.copy(courts = listOf("Campo 2")))))
        assertEquals(BookingProblem.PAST, ok(start = mon(9)))
        assertEquals(BookingProblem.TOO_SOON, ok(start = mon(10, 15)))
        assertEquals(BookingProblem.TOO_FAR, ok(start = mon(15, plusDays = 8)))
        assertNull(ok(start = mon(15, plusDays = 7)))
        assertEquals(BookingProblem.NOT_SLOT, ok(start = mon(15, 30)))
        assertEquals(BookingProblem.NOT_SLOT, ok(minutes = 90))
        assertEquals(BookingProblem.NOT_SLOT, ok(start = mon(21), minutes = 120))
        assertEquals(BookingProblem.TOO_LONG, ok(minutes = 180))
    }

    @Test fun busyBlocksAndOthersStopIt() {
        val busy = sync.copy(busy = listOf(Busy("Campo 1", mon(14, 30), mon(15, 30))))
        assertEquals(BookingProblem.TAKEN, ok(s = busy))
        assertNull(ok(s = busy, court = "Campo 2"))
        val other = listOf(OnlineBooking("x", "Campo 1", mon(15), 60, "p2", "Altro"))
        assertEquals(BookingProblem.TAKEN, ok(b = other))
        val course = CourtBlock("k", "Corso", BlockReason.COURSE, emptyList(), mon(0), mon(0, plusDays = 30), setOf(1), 14 * 60, 17 * 60)
        assertEquals(BookingProblem.BLOCKED, ok(s = sync.copy(blocks = listOf(course))))
        assertNull(ok(s = sync.copy(blocks = listOf(course)), start = mon(15, plusDays = 1)))
    }

    @Test fun tooManyFutureBookings() {
        val mine = listOf(
            OnlineBooking("a", "Campo 2", mon(11), 60, "p1", "Io"),
            OnlineBooking("b", "Campo 2", mon(12), 60, "p1", "Io"),
        )
        assertEquals(BookingProblem.TOO_MANY, ok(b = mine))
        // Una già giocata non conta.
        assertNull(ok(b = mine.mapIndexed { i, x -> if (i == 0) x.copy(start = mon(8)) else x }))
    }

    @Test fun priceAndLightsLikeTheOffice() {
        val b = CourtOnline.make(sync, "n", "p1", "Io", "Campo 1", mon(17), 120, heating = true, now = now)
        assertEquals(1000 + 1500, b.cents)
        assertEquals(true, b.lights)
        assertEquals(false, b.heating)
    }

    @Test fun cancelOnlyYoursAndInTime() {
        val list = listOf(OnlineBooking("a", "Campo 1", mon(15, plusDays = 2), 60, "p1", "Io"), OnlineBooking("b", "Campo 1", mon(15), 60, "p1", "Io"))
        assertNull(CourtOnline.checkCancel(sync, list, "p1", "a", now))
        assertEquals(CancelProblem.TOO_LATE, CourtOnline.checkCancel(sync, list, "p1", "b", now))
        assertEquals(CancelProblem.NOT_YOURS, CourtOnline.checkCancel(sync, list, "p2", "a", now))
        assertEquals(CancelProblem.NOT_FOUND, CourtOnline.checkCancel(sync, list, "p1", "z", now))
    }

    @Test fun gridHidesWhoBooked() {
        val s = sync.copy(busy = listOf(Busy("Campo 2", mon(16), mon(17))))
        val list = listOf(OnlineBooking("m", "Campo 1", mon(15), 60, "p1", "Io"), OnlineBooking("o", "Campo 1", mon(16), 60, "p2", "Altro"))
        val g = CourtOnline.grid(s, list, "p1", mon(0), now)
        assertEquals(2 * 14, g.size)
        fun at(c: String, h: Int) = g.first { it.court == c && it.start == mon(h) }
        assertEquals(CellState.PAST, at("Campo 1", 9).state)
        assertEquals(CellState.MINE, at("Campo 1", 15).state)
        assertEquals("m", at("Campo 1", 15).booking)
        assertEquals(CellState.BUSY, at("Campo 1", 16).state)
        assertNull(at("Campo 1", 16).booking)
        assertEquals(CellState.BUSY, at("Campo 2", 16).state)
        assertEquals(CellState.FREE, at("Campo 2", 15).state)
    }
}
