// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ServeTrainingTest {
    @Test
    fun pressUsesTheRecognisedServe() {
        val d = ServeTraining.Detected(at = 100_000, gyroDps = 1800, accelG = 9.5)
        // Tasto 4 s dopo il servizio riconosciuto: vale quello.
        val (at, used) = ServeTraining.anchor(104_000, d, null)
        assertEquals(100_000, at)
        assertSame(d, used)
        // Troppo tardi, o già usato per il tasto prima: si suppone un servizio 3 s prima del tasto.
        assertEquals(117_000L to null, ServeTraining.anchor(120_000, d, null))
        assertEquals(101_000L to null, ServeTraining.anchor(104_000, d, 100_000))
        // Nessun servizio riconosciuto.
        assertEquals(47_000L to null, ServeTraining.anchor(50_000, null, null))
    }

    @Test
    fun breakdownBySideKindAndTarget() {
        fun s(inPlay: Boolean, side: ServeSide, kind: ServeKind = ServeKind.FIRST, target: ServeTarget? = null, gyro: Int? = null) =
            ServeAttempt(0, inPlay, side, kind, target, gyroDps = gyro)
        val list = listOf(
            s(true, ServeSide.DEUCE, target = ServeTarget.T, gyro = 1600), s(false, ServeSide.DEUCE, target = ServeTarget.T, gyro = 2000),
            s(true, ServeSide.AD, ServeKind.SECOND), s(true, ServeSide.AD, ServeKind.SECOND),
        )
        val b = ServeTraining.breakdown(list)
        assertEquals(ServeTraining.Count(4, 3), b.all)
        assertEquals(75, b.all.pct)
        assertEquals(50, b.bySide.getValue(ServeSide.DEUCE).pct)
        assertEquals(100, b.byKind.getValue(ServeKind.SECOND).pct)
        // Solo i bersagli usati.
        assertEquals(setOf(ServeTarget.T), b.byTarget.keys)
        assertEquals(1800, ServeTraining.meanGyro(list))
        assertNull(ServeTraining.count(emptyList()).pct)
    }

    @Test
    fun groundstrokesAndVolleysByDirectionAndWing() {
        fun a(inPlay: Boolean, dir: ShotDirection?, wing: Wing? = null) =
            ServeAttempt(0, inPlay, ServeSide.DEUCE, ServeKind.FIRST, direction = dir, wing = wing)
        val fh = listOf(a(true, ShotDirection.CROSS), a(false, ShotDirection.LINE), a(true, ShotDirection.LINE), a(true, null))
        val b = ServeTraining.breakdown(fh, TrainedStroke.FOREHAND)
        // Col dritto non ci sono lato, prima/seconda e bersaglio del servizio.
        assertEquals(emptyMap<ServeSide, ServeTraining.Count>(), b.bySide)
        assertEquals(emptyMap<ServeTarget, ServeTraining.Count>(), b.byTarget)
        assertEquals(ServeTraining.Count(2, 1), b.byDirection[ShotDirection.LINE])
        assertEquals(setOf(ShotDirection.CROSS, ShotDirection.LINE), b.byDirection.keys)
        assertEquals(emptyMap<Wing, ServeTraining.Count>(), b.byWing)
        val volley = ServeTraining.breakdown(listOf(a(true, null, Wing.FOREHAND), a(false, null, Wing.BACKHAND), a(true, null, Wing.BACKHAND)), TrainedStroke.VOLLEY)
        assertEquals(50, volley.byWing.getValue(Wing.BACKHAND).pct)
        // Le sessioni salvate prima erano di servizio.
        assertEquals(TrainedStroke.SERVE, ServeSession("x", 0).stroke)
    }
}
