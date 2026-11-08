// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkoutStatsTest {

    /** Un valore al secondo da 0 a [n]-1 con il battito dato. */
    private fun track(n: Int, bpm: (Int) -> Int) = HeartTrack(t = (0 until n).toList(), bpm = (0 until n).map(bpm))

    @Test
    fun lapsWithRecovery() {
        // Due giri: 0-120 a 160 bpm, poi si cala a 120 entro il minuto dopo; secondo giro fino a 300 a 150.
        val w = WorkoutRecord(
            id = "w1", startedAt = 0, durationS = 300, laps = listOf(120),
            heart = track(301) { s -> if (s <= 120) 160 else if (s <= 180) 160 - (s - 120) * 40 / 60 else 150 },
            cooldown = List(60) { 150 - it / 2 },
        )
        val laps = WorkoutStats.laps(w)
        assertEquals(2, laps.size)
        assertEquals(120, laps[0].durationS)
        assertEquals(160, laps[0].avg)
        assertEquals(160, laps[0].max)
        assertEquals(40, laps[0].recovery)          // 160 a 120 s, 120 a 180 s
        assertEquals(180, laps[1].durationS)
        assertEquals(29, laps[1].recovery)          // dopo "Fine": 150 → 121
        assertEquals(29, WorkoutStats.finalRecovery(w))
    }

    @Test
    fun noRecoveryWithoutData() {
        val w = WorkoutRecord(id = "w", startedAt = 0, durationS = 100, heart = track(101) { 140 })
        assertNull(WorkoutStats.finalRecovery(w))
        assertNull(WorkoutStats.laps(w).single().recovery)
    }

    @Test
    fun bpmAtToleratesSmallHoles() {
        val t = HeartTrack(t = listOf(0, 1, 2, 10), bpm = listOf(100, 101, 102, 110))
        assertEquals(102, WorkoutStats.bpmAt(t, 4))
        assertNull(WorkoutStats.bpmAt(t, 6))
    }

    @Test
    fun rmssdSkipsArtifacts() {
        // Alterna 800 e 820 ms: differenze di 20 ms → RMSSD 20. Un battito perso (1600) e uno doppio (400) si scartano.
        val rr = List(60) { if (it % 2 == 0) 800 else 820 } + listOf(1600, 800, 400, 820)
        assertEquals(20.0, WorkoutStats.rmssd(rr)!!, 0.001)
        assertNull(WorkoutStats.rmssd(List(10) { 800 }))
    }

    @Test
    fun csvSecondBySecond() {
        val w = WorkoutRecord(
            id = "w", startedAt = 0, durationS = 4, laps = listOf(2),
            heart = track(5) { 100 + it }, cooldown = listOf(99, 98),
        )
        assertEquals(
            "second,bpm,lap\n0,100,1\n1,101,1\n2,102,2\n3,103,2\n4,104,2\n5,99,recovery\n6,98,recovery\n",
            WorkoutStats.csv(w),
        )
    }
}
