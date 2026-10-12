// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.ble

import com.tennis.scoremanager.data.StrokeStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Colpi dal firmware 2.4: stesso formato di sendStroke() e publishConfig() in TSM_Band.ino. */
class StrokeProtocolTest {

    @Test
    fun parseStrokeLikeTheFirmwareSendsIt() {
        // indice 300, servizio, 91 %, 1480 °/s, 12,34 g, accensione 0xA1B2C3D4
        val b = byteArrayOf(
            0x2C, 0x01, 2, 91,
            0xC8.toByte(), 0x05, 0xD2.toByte(), 0x04,
            0xD4.toByte(), 0xC3.toByte(), 0xB2.toByte(), 0xA1.toByte(),
        )
        val r = BandProtocol.parseStroke(b)!!
        assertEquals(300, r.index)
        assertEquals(BandProtocol.STROKE_SERVE, r.stroke)
        assertEquals(91, r.confidence)
        assertEquals(1480, r.peakGyroDps)
        assertEquals(12.34, r.peakAccelG, 1e-9)
        assertEquals(0xA1B2C3D4L, r.boot)
        assertNull(BandProtocol.parseStroke(b.copyOf(11)))
    }

    @Test
    fun unknownStrokeIs255() {
        val b = ByteArray(12).also { it[2] = 0xFF.toByte() }
        assertEquals(BandProtocol.STROKE_UNKNOWN, BandProtocol.parseStroke(b)!!.stroke)
    }

    @Test
    fun calibrationCommands() {
        assertEquals("C|1", BandProtocol.calibrate(BandProtocol.STROKE_BACKHAND))
        assertEquals("C|", BandProtocol.calibrate(null))
        assertEquals("U|", BandProtocol.UNDO_EXAMPLE)
        assertEquals("X|3", BandProtocol.clearExamples(BandProtocol.STROKE_VOLLEY))
    }

    @Test
    fun settingsWithStrokes() {
        val s = BandSettings.parse(
            "fw=2.4;name=TSM-1A2B;bri=20;pt=3;vol=50;flip=0;pair=30;lost=180;idle=30;lang=it;sw=1;swd=0;swc=8,9,0,2;cal=1",
        )!!
        assertTrue(s.supportsStrokes)
        assertTrue(s.strokes)
        assertFalse(s.strokeDisplay)
        assertEquals(listOf(8, 9, 0, 2), s.strokeExamples)
        assertEquals(BandProtocol.STROKE_BACKHAND, s.calibrating)
        assertTrue(s.strokesReady)  // dritto e rovescio hanno almeno 3 esempi
        assertEquals("bri=20;pt=3;vol=50;flip=0;pair=30;lost=180;idle=30;sw=1;swd=0", s.copy(name = "").encode())
    }

    @Test
    fun oldFirmwareHasNoStrokes() {
        val s = BandSettings.parse("fw=2.3;name=TSM-1A2B;bri=20;pt=3;vol=50;flip=0;pair=30;lost=180;idle=30;lang=it")!!
        assertFalse(s.supportsStrokes)
        assertFalse(s.strokesReady)
        assertEquals(-1, s.calibrating)
        // Al firmware vecchio non si mandano chiavi che non conosce.
        assertFalse("sw=" in s.copy(strokes = true).encode())
    }

    @Test
    fun readyNeedsTwoStrokesWithThreeExamples() {
        fun ready(vararg n: Int) = BandSettings(strokeExamples = n.toList()).strokesReady
        assertFalse(ready(20, 2, 2, 0))
        assertTrue(ready(3, 3, 0, 0))
        assertFalse(ready(0, 0, 0, 0))
    }

    @Test
    fun badCalibrationValuesAreIgnored() {
        val s = BandSettings.parse("fw=2.4;bri=20;swc=1,2,3;cal=7")!!
        assertNull(s.strokeExamples)  // servono quattro contatori
        assertEquals(-1, s.calibrating)
    }

    @Test
    fun strokeStatsCount() {
        val st = StrokeStats() + BandProtocol.STROKE_FOREHAND + BandProtocol.STROKE_FOREHAND +
            BandProtocol.STROKE_VOLLEY + BandProtocol.STROKE_UNKNOWN
        assertEquals(2, st.count(BandProtocol.STROKE_FOREHAND))
        assertEquals(0, st.count(BandProtocol.STROKE_SERVE))
        assertEquals(1, st.count(BandProtocol.STROKE_VOLLEY))
        assertEquals(1, st.unknown)
        assertEquals(4, st.total)
    }

    @Test
    fun imuCountsInTheBatteryEstimate() {
        val off = BandSettings(strokes = false)
        val on = BandSettings(strokes = true)
        assertEquals(BatteryModel.IMU_MA, BatteryModel.estimate(on).totalMa - BatteryModel.estimate(off).totalMa, 1e-9)
        // Una misura fatta col sensore acceso non lo conta due volte: 15 %/h = 37,5 mA misurati, sensore compreso.
        val s = on.copy(volume = 0)
        val e = BatteryModel.estimate(s, BatteryModel.baseFromMeasure(15.0, 0.0, s))
        assertEquals(37.5, e.totalMa - e.displayMa, 1e-9)
    }
}
