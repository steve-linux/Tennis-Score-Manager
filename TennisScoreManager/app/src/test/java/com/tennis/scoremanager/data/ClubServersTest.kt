// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClubServersTest {
    private val now = System.currentTimeMillis()
    private val h = 3_600_000L

    @Test fun addressIsCleaned() {
        assertEquals("http://192.168.1.5:8080", ClubServers.cleanUrl(" 192.168.1.5:8080/ "))
        assertEquals("https://tc.example.org", ClubServers.cleanUrl("https://tc.example.org/"))
        assertEquals("http://x:8080/#i=ABC", ClubServers.inviteLink(ClubServerConf(url = "http://x:8080"), "ABC"))
        // Con l'indirizzo da internet, gli inviti vanno lì (da casa e su iPhone serve l'https).
        assertEquals("https://tc.duckdns.org/#i=ABC", ClubServers.inviteLink(ClubServerConf(url = "http://x:8080", publicUrl = "https://tc.duckdns.org"), "ABC"))
        assertEquals(listOf("http://x:8080", "https://tc.duckdns.org"), ClubServerConf(url = "http://x:8080", publicUrl = "https://tc.duckdns.org").addresses)
    }

    @Test fun syncSendsOfficeBusyButNotOnlineOnes() {
        val plan = CourtPlan(
            settings = CourtSettings(courts = listOf(CourtInfo("Campo 1", photos = listOf("a.jpg")))),
            bookings = listOf(
                CourtBooking("b1", "Campo 1", now + h, names = listOf("Rossi")),
                CourtBooking("o1", "Campo 1", now + 3 * h, online = "w1"),
                CourtBooking("old", "Campo 1", now - 5 * 24 * h),
            ),
            blocks = listOf(CourtBlock("k", "Corso", from = now - 30 * 24 * h, to = now - 10 * 24 * h)),
        )
        val conf = ClubServerConf(url = "http://x", token = "t", rules = BookingRules(courts = listOf("Campo 1", "Sparito")), payInfo = "al campo")
        val sync = ClubServers.buildSync(conf, plan, emptyList(), listOf("Campo 1", "Campo 2"), "TC Prova", "Europe/Rome")
        assertEquals(listOf(Busy("Campo 1", now + h, now + 2 * h)), sync.busy)
        assertTrue(sync.blocks.isEmpty())
        assertTrue(sync.settings.courts.single().photos.isEmpty())
        assertEquals(listOf("Campo 1"), sync.rules.courts)
        assertEquals("al campo", sync.payInfo)
        // Nessun nome della segreteria va al server.
        assertTrue("Rossi" !in sync.toString())
    }

    @Test fun onlineBookingsComeInAndCancelledGoOut() {
        val kept = CourtBooking("o-w1", "Campo 1", now + h, online = "w1", note = "sistemata")
        val cancelled = CourtBooking("o-w2", "Campo 1", now + 2 * h, online = "w2")
        val forgotten = CourtBooking("o-w0", "Campo 1", now - 90 * 24 * h, online = "w0")
        val plan = CourtPlan(bookings = listOf(kept, cancelled, forgotten, CourtBooking("b", "Campo 2", now + h)))
        val online = listOf(
            OnlineBooking("w1", "Campo 1", now + h, 60, "p1", "Mario"),
            OnlineBooking("w3", "Campo 2", now + 4 * h, 60, "p1", "Mario", cents = 1200, lights = true),
            OnlineBooking("w4", "Campo 2", now + 5 * h, 60, "ignoto", "Luca"),
        )
        val (added, gone) = ClubServers.merge(plan, online, { it == "p1" }, now)
        assertEquals(listOf("w3", "w4"), added.map { it.online })
        assertEquals(listOf("p1"), added[0].players)
        assertEquals(1200, added[0].cents)
        assertEquals(listOf("Luca"), added[1].names)
        assertEquals(listOf(cancelled), gone)
    }

    @Test fun invitationLinkOrCode() {
        assertEquals("https://tc.duckdns.org" to "GGGKZZ4WK6", ClubServers.parseInvite(" https://tc.duckdns.org/#i=GGGKZ-Z4WK6 "))
        assertEquals("http://192.168.1.5:8080" to "ABCDEF2345", ClubServers.parseInvite("http://192.168.1.5:8080/?x=1#i=ABCDEF2345"))
        assertEquals(null to "GGGKZZ4WK6", ClubServers.parseInvite("gggkz-z4wk6"))
        assertEquals(null, ClubServers.parseInvite("ciao come va"))
        assertEquals(null, ClubServers.parseInvite("https://tc.duckdns.org/"))
    }
}
