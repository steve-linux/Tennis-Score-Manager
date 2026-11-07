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

    @Test fun onlineNewsOnlyWhatChanged() {
        fun ob(id: String, start: Long) = OnlineBooking(id, "Campo 1", start, 60, "p1", "Mario")
        val label = { b: OnlineBooking -> "${b.name} ${b.id}" }
        // La prima volta si ricorda e basta.
        val first = ClubServers.onlineNews(null, listOf(ob("a", now + h), ob("b", now + 2 * h)), now, label = label)
        assertTrue(first.added.isEmpty() && first.cancelled.isEmpty())
        assertEquals(setOf("a", "b"), first.seen.keys)
        // Arriva c, sparisce b (disdetta), a resta.
        val next = ClubServers.onlineNews(first.seen, listOf(ob("a", now + h), ob("c", now + 5 * h)), now, label = label)
        assertEquals(listOf("Mario c"), next.added)
        assertEquals(listOf("Mario b"), next.cancelled)
        assertEquals(now + 2 * h, next.firstDay)
        // Tolta dalla segreteria stessa: niente notifica.
        assertTrue(ClubServers.onlineNews(first.seen, listOf(ob("a", now + h)), now, setOf("b"), label).cancelled.isEmpty())
        // Una già giocata che il server dimentica non è una disdetta.
        val later = now + 3 * h
        val gone = ClubServers.onlineNews(next.seen, listOf(ob("c", now + 5 * h)), later, label = label)
        assertTrue(gone.cancelled.isEmpty())
        assertTrue(gone.added.isEmpty())
    }

    @Test fun leNovitaDiventanoMessaggiDaLeggere() {
        fun ob(id: String, start: Long) = OnlineBooking(id, "Campo 1", start, 60, "p1", "Mario")
        val label = { b: OnlineBooking -> "${b.name} ${b.id}" }
        val first = ClubServers.onlineNews(null, listOf(ob("a", now + h)), now, label = label)
        assertTrue("la prima volta niente messaggi", first.notices.isEmpty())
        val next = ClubServers.onlineNews(first.seen, listOf(ob("c", now + 5 * h)), now, label = label)
        assertEquals(listOf("ac" to false, "ca" to true), next.notices.map { it.id to it.cancelled })
        assertEquals("Mario c", next.notices.first().label)
        // Nella casella: niente doppioni e al massimo INBOX_MAX, i più nuovi.
        val inbox = ClubServers.addNotices(next.notices, next.notices)
        assertEquals(2, inbox.size)
        val many = (1..60).map { OnlineNotice("x$it", false, "n$it", now, now) }
        val capped = ClubServers.addNotices(inbox, many)
        assertEquals(ClubServers.INBOX_MAX, capped.size)
        assertEquals("x60", capped.last().id)
    }

    @Test fun playerRemindersAndRemovals() {
        fun mb(id: String, start: Long) = SrvMyBooking(id, "Campo 1", start, 60, 0, false, false, true)
        val label = { b: SrvMyBooking -> b.id }
        val a0 = BookingAccount("http://x", "t")
        // La prima volta: si ricorda e basta (niente promemoria per quelle già vicine).
        val first = ClubServers.myNews(a0, listOf(mb("a", now + h), mb("b", now + 10 * h)), 2, now, label)
        assertTrue(first.remind.isEmpty() && first.removed.isEmpty())
        // Più tardi b entra nelle 2 ore: promemoria una volta sola.
        val later = now + 9 * h
        val second = ClubServers.myNews(first.account, listOf(mb("b", now + 10 * h)), 2, later, label)
        assertEquals(listOf("b"), second.remind.map { it.label })
        assertTrue(second.removed.isEmpty())  // a è già passata: non è una rimozione
        assertTrue(ClubServers.myNews(second.account, listOf(mb("b", now + 10 * h)), 2, later + 60_000, label).remind.isEmpty())
        // c tolta dalla segreteria, d disdetta da qui.
        val withCd = ClubServers.myNews(second.account, listOf(mb("b", now + 10 * h), mb("c", now + 30 * h), mb("d", now + 40 * h)), 2, later, label)
        val gone = ClubServers.myNews(withCd.account.copy(cancelledHere = listOf("d")), listOf(mb("b", now + 10 * h)), 2, later, label)
        assertEquals(listOf("c"), gone.removed.map { it.label })
    }

    @Test fun freedWaitsAlertOnce() {
        val a = BookingAccount("http://x", "t")
        val waits = listOf(SrvWait("w1", "Campo 1", now + 5 * h, 60), SrvWait("w2", null, now + 6 * h, 60, freedCourt = "Campo 2"))
        val (fresh, next) = ClubServers.freedNews(a, waits)
        assertEquals(listOf("w2"), fresh.map { it.id })
        assertTrue(ClubServers.freedNews(next, waits).first.isEmpty())
    }

    @Test fun seekNewsOnlyChanges() {
        fun k(players: List<String>, court: String? = null, noCourt: Boolean = false, mine: Boolean = true) =
            SrvSeek("s1", mine, !mine, "Mario", "2026-11-14", 540, 720, false, "", "", players, 2 - players.size, court, court?.let { now + h }, noCourt)
        val first = ClubServers.seekNews(BookingAccount("http://x", "t"), listOf(k(listOf("Mario"))))
        assertTrue(first.joined.isEmpty() && first.booked.isEmpty())
        val joined = ClubServers.seekNews(first.account, listOf(k(listOf("Mario", "Luca"))))
        assertEquals(1, joined.joined.size)
        val booked = ClubServers.seekNews(joined.account, listOf(k(listOf("Mario", "Luca"), court = "Campo 1")))
        assertEquals(1, booked.booked.size)
        assertTrue(ClubServers.seekNews(booked.account, listOf(k(listOf("Mario", "Luca"), court = "Campo 1"))).booked.isEmpty())
    }
}
