package com.tennis.scoremanager.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryModelTest {

    @Test
    fun parseStatus() {
        val s = BatteryModel.parse("mv=3987;chg=0;up=1234;dsp=56")!!
        assertEquals(3987, s.millivolts)
        assertEquals(false, s.charging)
        assertEquals(1234L, s.uptimeS)
        assertEquals(56L, s.displayS)
        assertNull(BatteryModel.parse("garbage"))
    }

    @Test
    fun parseChargeStatus() {
        // firmware 2.1: col cavo "chg" resta 1 anche a carica completa
        val c = BatteryModel.parse("mv=4150;chg=1;up=60;dsp=30;usb=5012;full=0;pct=62")!!
        assertEquals(true, c.charging)
        assertEquals(5012, c.usbMv)
        assertEquals(false, c.full)
        assertEquals(62, c.percent)
        // in carica vale la percentuale del braccialetto, non la tensione (falsata dalla carica)
        assertEquals(62, BatteryModel.shownPercent(c))
        val f = BatteryModel.parse("mv=4190;chg=1;up=60;dsp=30;usb=5012;full=1;pct=100")!!
        assertEquals(100, BatteryModel.shownPercent(f))
        // firmware 2.0: niente campi nuovi, percentuale dalla tensione
        val old = BatteryModel.parse("mv=3840;chg=0;up=1;dsp=0")!!
        assertNull(old.usbMv)
        assertNull(old.percent)
        assertEquals(50, BatteryModel.shownPercent(old))
        // senza cavo la percentuale del braccialetto non serve: stessa curva dall'app
        assertEquals(50, BatteryModel.shownPercent(BatteryModel.parse("mv=3840;chg=0;up=1;dsp=0;usb=0;full=0;pct=49")!!))
    }

    @Test
    fun socFromVoltage() {
        assertEquals(100, BatteryModel.soc(4250))
        assertEquals(0, BatteryModel.soc(3200))
        assertEquals(50, BatteryModel.soc(3840))
        assertEquals(20, BatteryModel.soc(3730))
        val mid = BatteryModel.soc(4000)
        assertTrue(mid in 76..79)
    }

    @Test
    fun hoursLeftFromSteadyDrain() {
        // 10 % all'ora partendo da 80 %: dopo 1 ora siamo a 70 %, restano ~7 ore
        val samples = (0..60).map { m -> m * 60_000L to (80 - m / 6) }
        val h = BatteryModel.hoursLeft(samples)
        assertNotNull(h)
        assertEquals(7.0, h!!, 0.4)
        // troppo pochi dati
        assertNull(BatteryModel.hoursLeft(samples.take(10)))
        // nessun calo
        assertNull(BatteryModel.hoursLeft((0..30).map { it * 60_000L to 80 }))
    }

    @Test
    fun estimateFollowsSettings() {
        val default = BatteryModel.estimate(BandSettings())
        assertTrue(!default.measured)
        // 250 mAh con ~36 mA: circa 7 ore da carica piena, più della partita più lunga
        assertEquals(7.0, default.hoursFull, 0.5)
        assertEquals(default.hoursFull / 2, default.hoursAt(50), 0.01)
        // display più luminoso e più a lungo, cicalino al massimo: consuma di più
        val bright = BatteryModel.estimate(BandSettings(brightness = 100, pointSeconds = 8, volume = 100))
        assertTrue(bright.totalMa > default.totalMa + 3)
        // punteggio spento e muto: consuma di meno
        val saver = BatteryModel.estimate(BandSettings(brightness = 5, pointSeconds = 0, volume = 0))
        assertTrue(saver.totalMa < default.totalMa)
        assertEquals(0.0, saver.soundMa, 0.0)
        // con una base misurata si usa quella
        val measured = BatteryModel.estimate(BandSettings(), 50.0)
        assertTrue(measured.measured)
        assertEquals(50.0, measured.baseMa, 0.0)
    }

    @Test
    fun baseFromMeasuredDrain() {
        // 16 % all'ora di 250 mAh = 40 mA in tutto; display acceso il 10 % del tempo a luminosità 20 (8 mA)
        val s = BandSettings(brightness = 20, volume = 0)
        assertEquals(40.0 - 0.8, BatteryModel.baseFromMeasure(16.0, 0.10, s), 0.01)
    }

    @Test
    fun hoursText() {
        assertEquals("~40 min", BatteryModel.formatHours(40 / 60.0))
        assertEquals("~7 h", BatteryModel.formatHours(7.0))
        assertEquals("~6 h 30 min", BatteryModel.formatHours(6.5))
    }
}
