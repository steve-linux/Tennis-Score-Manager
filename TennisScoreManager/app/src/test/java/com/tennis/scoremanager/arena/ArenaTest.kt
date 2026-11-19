// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.arena

import com.tennis.scoremanager.ble.BandProtocol
import com.tennis.scoremanager.ble.BandSettings
import com.tennis.scoremanager.ble.RawSwing
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** TSM Arena: SWING del firmware 2.5 (sendSwing() in TSM_Band.ino) e messaggi per arena.html. */
class ArenaTest {

    @Test
    fun parseSwingLikeTheFirmwareSendsIt() {
        // sequenza 7, 1480 °/s, 12,34 g, 30 ms dalla soglia, accensione 0xA1B2C3D4
        val b = byteArrayOf(
            7, 0xC8.toByte(), 0x05, 0xD2.toByte(), 0x04, 30,
            0xD4.toByte(), 0xC3.toByte(), 0xB2.toByte(), 0xA1.toByte(),
        )
        val r = BandProtocol.parseSwing(b)!!
        assertEquals(7, r.seq)
        assertEquals(1480, r.peakGyroDps)
        assertEquals(12.34, r.peakAccelG, 1e-9)
        assertEquals(30, r.afterMs)
        assertEquals(0xA1B2C3D4L, r.boot)
        assertNull(BandProtocol.parseSwing(b.copyOf(9)))
    }

    @Test
    fun arenaCommand() {
        assertEquals("A|1", BandProtocol.arena(true))
        assertEquals("A|0", BandProtocol.arena(false))
    }

    @Test
    fun arenaNeedsFirmware25() {
        assertTrue(BandSettings(firmware = "2.5").supportsArena)
        assertTrue(BandSettings(firmware = "2.5.1").supportsArena)
        assertTrue(BandSettings(firmware = "3.0").supportsArena)
        assertFalse(BandSettings(firmware = "2.4").supportsArena)
        assertTrue(BandSettings(firmware = "2.10").supportsArena)
        assertFalse(BandSettings(firmware = "").supportsArena)
        assertFalse(BandSettings(firmware = "beta").supportsArena)
    }

    @Test
    fun strengthGrowsWithTheWristAndStaysInRange() {
        assertEquals(0.15, Arena.strength(0), 1e-9)
        assertEquals(0.15, Arena.strength(450), 1e-9)
        assertEquals(0.5, Arena.strength(1000), 1e-9)
        assertEquals(1.0, Arena.strength(1600), 1e-9)
        assertEquals(1.0, Arena.strength(4000), 1e-9)
    }

    @Test
    fun messagesForThePage() {
        val cfg = Json.parseToJsonElement(
            Arena.config(ArenaSettings(level = 3, games = 5, twoPlayers = true, tvLag = 2), "de", "Rossi", "Bianchi", true, false),
        ).jsonObject
        assertEquals("cfg", cfg["t"]!!.jsonPrimitive.content)
        assertEquals("de", cfg["lang"]!!.jsonPrimitive.content)
        assertEquals(3, cfg["level"]!!.jsonPrimitive.int)
        assertEquals(5, cfg["games"]!!.jsonPrimitive.int)
        assertEquals(Arena.tvLagMs(2), cfg["lagMs"]!!.jsonPrimitive.int)
        assertTrue(cfg["two"]!!.jsonPrimitive.boolean)
        assertEquals("Rossi", cfg["p1"]!!.jsonObject["name"]!!.jsonPrimitive.content)
        assertFalse(cfg["p2"]!!.jsonObject["band"]!!.jsonPrimitive.boolean)
        assertEquals(2, cfg["gfx"]!!.jsonPrimitive.int)  // 3D realistica, se la TV ce la fa
        val flat = Json.parseToJsonElement(Arena.config(ArenaSettings(graphics = 0), "it", "A", "B", false, false)).jsonObject
        assertEquals(0, flat["gfx"]!!.jsonPrimitive.int)

        val sw = Json.parseToJsonElement(Arena.swing(2, RawSwing(1, 1000, 3.0, 30, 0))).jsonObject
        assertEquals("swing", sw["t"]!!.jsonPrimitive.content)
        assertEquals(2, sw["side"]!!.jsonPrimitive.int)
        assertEquals(0.5, sw["s"]!!.jsonPrimitive.double, 1e-9)

        val key = Json.parseToJsonElement(Arena.key(1, BandProtocol.EVT_UNDO)).jsonObject
        assertEquals(2, key["k"]!!.jsonPrimitive.int)
        assertEquals(1, Json.parseToJsonElement(Arena.key(1, BandProtocol.EVT_POINT)).jsonObject["k"]!!.jsonPrimitive.int)
    }
}
