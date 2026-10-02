package com.tennis.scoremanager.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BandEventTest {

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    @Test
    fun parseOldAndNewEvents() {
        // firmware prima della 2.0: solo tipo e sequenza
        val v1 = BandProtocol.parseEvent(bytes(1, 7))!!
        assertEquals(RawBandEvent(1, 7, null, null, 0), v1)
        // firmware 2.0-2.2: terzo byte = motivo dello spegnimento
        val v2 = BandProtocol.parseEvent(bytes(3, 200, 4))!!
        assertEquals(BandProtocol.EVT_POWER_OFF, v2.type)
        assertEquals(4, v2.extra)
        assertNull(v2.boot)
        // firmware 2.3: id di accensione little endian ed età in decimi di secondo
        val v3 = BandProtocol.parseEvent(bytes(2, 255, 0, 0x78, 0x56, 0x34, 0xF2, 63))!!
        assertEquals(BandProtocol.EVT_UNDO, v3.type)
        assertEquals(255, v3.seq)
        assertEquals(0xF2345678L, v3.boot)
        assertEquals(6_300, v3.ageMs)
        assertNull(BandProtocol.parseEvent(bytes(1)))
    }

    @Test
    fun helloAndAckMessages() {
        assertEquals("H|1", BandProtocol.HELLO)
        assertEquals("K|0", BandProtocol.ack(0))
        assertEquals("K|255", BandProtocol.ack(255))
        // il firmware li riconosce dal secondo carattere e dalla lunghezza (2-6)
        assertTrue(BandProtocol.ack(255).length in 2..6 && BandProtocol.ack(255)[1] == '|')
    }

    @Test
    fun resentEventIsAppliedOnce() {
        val d = EventDedupe()
        val boot = 0xCAFEL
        assertTrue(d.firstTime("AA", boot, 5, 1_000))
        // rimandato dopo una riconnessione: già visto
        assertFalse(d.firstTime("AA", boot, 5, 7_000))
        // il seguente è nuovo
        assertTrue(d.firstTime("AA", boot, 6, 7_100))
        // stessa sequenza, ma il braccialetto è stato riacceso: è un altro tasto
        assertTrue(d.firstTime("AA", 0xBEEFL, 5, 7_200))
        // stessa sequenza e accensione, ma dall'altro braccialetto
        assertTrue(d.firstTime("BB", boot, 5, 7_300))
    }

    @Test
    fun dedupeWindowLetsTheSequenceWrap() {
        val d = EventDedupe(windowMs = 30_000)
        assertTrue(d.firstTime("AA", 1L, 9, 0))
        assertFalse(d.firstTime("AA", 1L, 9, 29_000))
        // dopo 256 tasti la sequenza torna a 9: fuori dalla finestra vale di nuovo
        assertTrue(d.firstTime("AA", 1L, 9, 61_000))
    }

    @Test
    fun scanThrottleKeepsUnderAndroidLimit() {
        val t = ScanThrottle(maxStarts = 4, windowMs = 30_000)
        for (i in 0 until 4) {
            assertEquals(0, t.delayBeforeStart(i * 1_000L))
            t.recordStart(i * 1_000L)
        }
        // quinto avvio a 4 s: si aspetta che il primo (a 0 s) esca dalla finestra, più un margine
        assertEquals(26_500, t.delayBeforeStart(4_000))
        // passato quel tempo si riparte subito
        assertEquals(0, t.delayBeforeStart(30_000))
    }
}
