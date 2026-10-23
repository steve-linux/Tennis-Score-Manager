// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.relay

import com.tennis.scoremanager.data.HeartTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RelayProtocolTest {
    private val token = "0123456789abcdef0123456789abcdef"

    @Test
    fun urlRoundTrip() {
        val url = RelayCodec.url("192.168.43.1", 40123, token)
        assertEquals(RelayTarget("192.168.43.1", 40123, token), RelayCodec.parseUrl(url))
        assertEquals(RelayTarget("192.168.43.1", 40123, token), RelayCodec.parseUrl("  $url\n"))
    }

    @Test
    fun urlCarriesHotspot() {
        // Nome con spazi e password con caratteri che nell'indirizzo hanno un significato.
        val net = HotspotInfo("AndroidShare_1234 campo", "a&b=c?d+e%f")
        val url = RelayCodec.url("192.168.49.1", 40123, token, net)
        assertEquals(RelayTarget("192.168.49.1", 40123, token, net), RelayCodec.parseUrl(url))
        // Rete aperta: niente password.
        val open = HotspotInfo("Aperta", null)
        assertEquals(open, RelayCodec.parseUrl(RelayCodec.url("10.0.0.1", 9, token, open))?.hotspot)
    }

    @Test
    fun otherQrCodesAreRejected() {
        assertNull(RelayCodec.parseUrl("http://192.168.1.5:8080/tsm-transfer/$token"))
        assertNull(RelayCodec.parseUrl("tsm-relay://192.168.1.5:99999/$token"))
        assertNull(RelayCodec.parseUrl("tsm-relay://192.168.1.5:8080/abc"))
    }

    @Test
    fun messageRoundTripAndGarbage() {
        val m = RelayMsg(RelayMsg.HR, sat = "s1", match = "m1-ab", seq = 7, addr = "AA:BB", bpm = 151, contact = true, at = 1_000L)
        val line = RelayCodec.encode(m)
        assertFalse(line.contains("\n"))
        assertFalse("i campi nulli non viaggiano", line.contains("null"))
        assertEquals(m, RelayCodec.decode(line))
        assertNull(RelayCodec.decode("non json"))
        // Un campo sconosciuto (versione più nuova dell'altro telefono) non rompe niente.
        assertEquals(RelayMsg.PING, RelayCodec.decode("""{"t":"ping","c":5,"nuovo":1}""")?.t)
    }

    @Test
    fun clockSyncKeepsFastestPing() {
        val c = ClockSync()
        assertNull(c.offset)
        // Satellite 10 s indietro: ping lento (asimmetrico) e ping veloce.
        c.add(sentAt = 1_000, masterAt = 11_400, receivedAt = 1_600)  // rtt 600, stima 10 100
        c.add(sentAt = 2_000, masterAt = 12_010, receivedAt = 2_020)  // rtt 20, stima 10 000
        assertEquals(10_000L, c.offset)
        c.add(sentAt = 3_000, masterAt = 0, receivedAt = 2_000)  // tempo all'indietro: scartato
        assertEquals(10_000L, c.offset)
    }

    @Test
    fun outboxResendsUntilAcked() {
        val o = RelayOutbox<String>(max = 3)
        assertEquals(1L, o.add("a"))
        assertEquals(2L, o.add("b"))
        o.add("c")
        o.add("d")  // oltre il massimo: si perde la più vecchia
        assertEquals(listOf(2L, 3L, 4L), o.pending().map { it.first })
        o.ack(3)
        assertEquals(listOf(4L to "d"), o.pending())
        o.ack(10)
        assertEquals(0, o.size)
        assertEquals(5L, o.add("e"))
    }

    @Test
    fun inboxSkipsResentReadings() {
        val i = RelayInbox()
        assertTrue(i.accept("s1", 1))
        assertTrue(i.accept("s1", 2))
        // Riconnessione: il satellite rimanda 2 (non confermata in tempo) e poi 3.
        assertFalse(i.accept("s1", 2))
        assertTrue(i.accept("s1", 3))
        assertEquals(3L, i.last("s1"))
        // Satellite riavviato: nuovo id, i numeri ripartono.
        assertTrue(i.accept("s2", 1))
    }

    @Test
    fun lateReadingsFillOnlyGaps() {
        // Dal vivo fino a 20 s, poi il Wi-Fi cade fino a 40 s.
        val track = HeartTrack(t = listOf(5, 10, 15, 20, 40), bpm = listOf(100, 110, 120, 130, 150))
        val late = (12..39).map { it to 140 }
        val filled = track.fill(late, 5)
        assertEquals(listOf(5, 10, 15, 20, 24, 29, 34, 40), filled.t)
        assertEquals(listOf(100, 110, 120, 130, 140, 140, 140, 150), filled.bpm)
        assertSame(filled, filled.fill(late, 5))
    }

    @Test
    fun lateReadingsAverageBySlot() {
        val filled = HeartTrack().fill(listOf(0 to 100, 2 to 110, 4 to 120, 7 to 90), 5)
        assertEquals(listOf(4, 9), filled.t)
        assertEquals(listOf(110, 90), filled.bpm)
    }
}
