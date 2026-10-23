// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import com.tennis.scoremanager.ble.HeartRateProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeartStatsTest {

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    @Test
    fun parsesEightBitHeartRate() {
        val m = HeartRateProtocol.parse(bytes(0x00, 72))!!
        assertEquals(72, m.bpm)
        assertNull(m.contact)
        assertTrue(m.rrMs.isEmpty())
        assertTrue(m.valid)
    }

    @Test
    fun parsesSixteenBitHeartRateWithContactAndRr() {
        // 16 bit, contatto supportato e presente, RR: 1024 -> 1000 ms, 512 -> 500 ms
        val m = HeartRateProtocol.parse(bytes(0x01 or 0x02 or 0x04 or 0x10, 0x2C, 0x01, 0x00, 0x04, 0x00, 0x02))!!
        assertEquals(300, m.bpm)
        assertEquals(true, m.contact)
        assertEquals(listOf(1000, 500), m.rrMs)
        assertFalse(m.valid)  // 300 bpm non è un battito vero
    }

    @Test
    fun skipsEnergyBeforeRr() {
        val m = HeartRateProtocol.parse(bytes(0x08 or 0x10, 150, 0x10, 0x00, 0x00, 0x03))!!
        assertEquals(150, m.bpm)
        assertEquals(listOf(750), m.rrMs)
    }

    @Test
    fun noSkinContactIsNotValid() {
        val m = HeartRateProtocol.parse(bytes(0x04, 0))!!
        assertEquals(false, m.contact)
        assertFalse(m.valid)
        assertNull(HeartRateProtocol.parse(bytes(0x01, 0x10)))
    }

    @Test
    fun keytelMatchesThePaper() {
        // Uomo di 30 anni, 75 kg, 140 bpm: (-55.0969 + 0.6309*140 + 0.1988*75 + 0.2017*30) / 4.184 ≈ 12.95 kcal/min
        assertEquals(12.95, HeartStats.kcalPerMinute(140, HeartProfile(30, 75)), 0.01)
        // Donna di 30 anni, 60 kg, 140 bpm: (-20.4022 + 0.4472*140 - 0.1263*60 + 0.074*30) / 4.184 ≈ 8.81
        assertEquals(8.81, HeartStats.kcalPerMinute(140, HeartProfile(30, 60, female = true)), 0.01)
        assertEquals(0.0, HeartStats.kcalPerMinute(50, HeartProfile(30, 75)), 0.0)
    }

    @Test
    fun summaryOfAnHour() {
        val n = 3600 / HeartStats.STEP_S
        val track = HeartTrack(t = List(n) { it * HeartStats.STEP_S }, bpm = List(n) { if (it % 2 == 0) 130 else 150 }, profile = HeartProfile(30, 75))
        val s = HeartStats.summary(track)!!
        assertEquals(140, s.avg)
        assertEquals(150, s.max)
        assertEquals(130, s.min)
        assertEquals(60, s.minutes)
        assertEquals(777.0, s.kcal!!.toDouble(), 2.0)
    }

    @Test
    fun gapsDoNotCount() {
        // 10 minuti, un'ora di sospensione, altri 10 minuti: contano 20 minuti, non 80.
        val first = List(120) { it * 5 }
        val second = List(120) { 4200 + it * 5 }
        val track = HeartTrack(t = first + second, bpm = List(240) { 140 }, profile = HeartProfile(30, 75))
        val s = HeartStats.summary(track)!!
        assertEquals(20, s.minutes)
        assertEquals(259.0, s.kcal!!.toDouble(), 2.0)
    }

    @Test
    fun noProfileNoCalories() {
        assertNull(HeartStats.summary(HeartTrack(listOf(0), listOf(120)))!!.kcal)
        assertNull(HeartStats.summary(HeartTrack()))
        assertNull(HeartStats.profile(30, null, false))
        assertNull(HeartStats.profile(150, 70, false))
        assertEquals(HeartProfile(45, 82, false), HeartStats.profile(45, 82, false))
    }

    @Test
    fun zones() {
        // 30 anni: massimo 187. 100 bpm (53%) zona 1, 140 (75%) zona 3, 180 (96%) zona 5; 60 bpm (32%) va in zona 1.
        assertEquals(187, HeartStats.maxBpm(30))
        val track = HeartTrack(t = listOf(0, 5, 10, 15), bpm = listOf(100, 140, 180, 60))
        assertEquals(listOf(10, 0, 5, 0, 5), HeartStats.zoneSeconds(track, 30))
        assertTrue(HeartStats.zoneSeconds(HeartTrack(), 30).isEmpty())
    }

    @Test
    fun bucketAverages() {
        val b = HeartBucket()
        assertNull(b.take())
        b.add(120)
        b.add(125)
        assertEquals(123, b.take())
        assertNull(b.take())
    }
}
