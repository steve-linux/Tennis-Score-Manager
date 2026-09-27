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
}
