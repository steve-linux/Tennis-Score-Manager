// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class CourtStatsTest {
    /** Lunedì 9 novembre 2026. */
    private val monday = Calendar.getInstance().apply { clear(); set(2026, Calendar.NOVEMBER, 9) }.timeInMillis

    @Test fun occupancyRevenueAndHeat() {
        val at = { h: Int -> CourtPlans.at(monday, h) }
        val plan = CourtPlan(
            settings = CourtSettings(openHour = 8, closeHour = 12),
            bookings = listOf(
                CourtBooking("a", "Campo 1", at(8), 60, players = listOf("p1"), cents = 1200, online = "w1"),
                CourtBooking("b", "Campo 1", at(10), 30, names = listOf("Rossi"), cents = 600),
                CourtBooking("c", "Campo 2", at(8), 120, kind = BookingKind.CLOSED),
            ),
            blocks = listOf(CourtBlock("k", "Corso", BlockReason.COURSE, listOf("Campo 1"), monday, monday, startMin = 11 * 60, endMin = 12 * 60)),
        )
        val r = CourtStats.report(plan, emptyList(), listOf("Campo 1", "Campo 2"), monday, CourtPlans.addDays(monday, 1))
        val c1 = r.courts.first { it.court == "Campo 1" }
        assertEquals(4.0, c1.openHours, 0.001)
        assertEquals(1.0, c1.blockedHours, 0.001)
        assertEquals(1.5, c1.bookedHours, 0.001)
        assertEquals(0.5, c1.occupancy, 0.001)
        assertEquals(1800L, c1.cents)
        assertEquals(2, c1.bookings)
        assertEquals(1, c1.online)
        // La chiusura per manutenzione occupa il campo ma non incassa e non conta come prenotazione.
        val c2 = r.courts.first { it.court == "Campo 2" }
        assertEquals(2.0, c2.bookedHours, 0.001)
        assertEquals(0, c2.bookings)
        // Lunedì alle 8: entrambi i campi occupati; alle 9 uno (il Campo 2); alle 11 il Campo 1 è bloccato e il 2 libero.
        assertEquals(1.0, r.heat[0][0], 0.001)
        assertEquals(0.5, r.heat[0][1], 0.001)
        assertEquals(0.0, r.heat[0][3], 0.001)
        assertEquals(8, r.firstHour)
        assertEquals(listOf("Rossi" to 1, "p1" to 1), r.top)
    }
}
