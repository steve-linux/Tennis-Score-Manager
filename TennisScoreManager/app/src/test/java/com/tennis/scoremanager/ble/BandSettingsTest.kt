package com.tennis.scoremanager.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BandSettingsTest {

    @Test
    fun parseFirmwareConfig() {
        val s = BandSettings.parse("fw=2.0;name=TSM-1A2B;bri=20;pt=3;vol=50;flip=1;pair=30;lost=180;idle=30")!!
        assertEquals("TSM-1A2B", s.name)
        assertEquals(20, s.brightness)
        assertEquals(3, s.pointSeconds)
        assertEquals(50, s.volume)
        assertTrue(s.flip)
        assertEquals(30, s.pairTimeoutS)
        assertEquals(180, s.lostTimeoutS)
        assertEquals(30, s.idleTimeoutMin)
        assertEquals("2.0", s.firmware)
        // un testo qualsiasi (o lo stato batteria) non sono impostazioni
        assertNull(BandSettings.parse("mv=3987;chg=0;up=1234;dsp=56"))
    }

    @Test
    fun encodeRoundTripAndClamp() {
        val s = BandSettings(name = "Mario", brightness = 70, pointSeconds = 5, volume = 0, flip = true, pairTimeoutS = 60, lostTimeoutS = 300, idleTimeoutMin = 45)
        assertEquals("name=Mario;bri=70;pt=5;vol=0;flip=1;pair=60;lost=300;idle=45", s.encode())
        assertEquals(s, BandSettings.parse(s.encode()))
        // valori fuori scala: il telefono li riporta negli stessi limiti del firmware
        val wild = BandSettings(brightness = 0, pointSeconds = 99, volume = 150, pairTimeoutS = 1, lostTimeoutS = 99_999, idleTimeoutMin = 0)
        assertEquals("bri=5;pt=10;vol=100;flip=0;pair=15;lost=1800;idle=5", wild.encode())
    }

    @Test
    fun nameIsSafeForTheProtocol() {
        assertEquals("Nicolo G1", BandSettings.cleanName("Nicolò G1"))
        assertEquals("ab", BandSettings.cleanName("a|;=b"))
        assertEquals("ABCDEFGHIJKL", BandSettings.cleanName("ABCDEFGHIJKLMNOP"))
        assertFalse(BandSettings(name = "x;pt=0").encode().contains("x;pt=0"))
    }

    @Test
    fun identifyAndPowerOffMessages() {
        assertEquals("I|GIOCATORE 1|ROSSI|6|FFD600", BandProtocol.identify("Giocatore 1", "Rossi", 6, 0xFFFFD600.toInt()))
        assertEquals("O|FINE PARTITA|6-4 6-3", BandProtocol.powerOff("Fine partita", "6-4 6-3"))
    }
}
