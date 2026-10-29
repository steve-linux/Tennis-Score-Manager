// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import com.tennis.scoremanager.data.ServePose.L_ANKLE
import com.tennis.scoremanager.data.ServePose.L_ELBOW
import com.tennis.scoremanager.data.ServePose.L_HIP
import com.tennis.scoremanager.data.ServePose.L_KNEE
import com.tennis.scoremanager.data.ServePose.L_SHOULDER
import com.tennis.scoremanager.data.ServePose.L_WRIST
import com.tennis.scoremanager.data.ServePose.NOSE
import com.tennis.scoremanager.data.ServePose.R_ANKLE
import com.tennis.scoremanager.data.ServePose.R_ELBOW
import com.tennis.scoremanager.data.ServePose.R_HIP
import com.tennis.scoremanager.data.ServePose.R_KNEE
import com.tennis.scoremanager.data.ServePose.R_SHOULDER
import com.tennis.scoremanager.data.ServePose.R_WRIST
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max

class ServePoseTest {
    /**
     * Un servizio finto di un destro: lancio col sinistro in alto a -700 ms, ginocchia più piegate a -400 ms, impatto
     * col braccio destro teso sopra la testa a 0 ms.
     */
    private fun serve(score: Float = 0.9f): List<PoseFrame> = (-1500L..300L step 100).map { t ->
        val x = MutableList(17) { 0.5f }
        val y = MutableList(17) { 0.5f }
        fun p(i: Int, px: Float, py: Float) { x[i] = px; y[i] = py }
        p(NOSE, 0.5f, 0.15f)
        p(L_SHOULDER, 0.45f, 0.25f); p(R_SHOULDER, 0.55f, 0.25f)
        p(L_HIP, 0.47f, 0.5f); p(R_HIP, 0.53f, 0.5f)
        // Ginocchia: si spostano in avanti fino a -400 ms (la gamba si piega), poi tornano.
        val bend = max(0f, 1f - abs(t + 400) / 500f) * 0.08f
        p(L_KNEE, 0.47f + bend, 0.7f); p(R_KNEE, 0.53f + bend, 0.7f)
        p(L_ANKLE, 0.47f, 0.9f); p(R_ANKLE, 0.53f, 0.9f)
        // Braccio del lancio: sale dritto fino a -700 ms.
        val toss = max(0f, 1f - abs(t + 700) / 600f)
        p(L_ELBOW, 0.45f, 0.35f - toss * 0.2f); p(L_WRIST, 0.45f, 0.45f - toss * 0.45f)
        // Braccio che colpisce: basso, poi a 0 ms disteso sopra la spalla, col polso che scatta in avanti (lo swing,
        // più veloce proprio all'impatto).
        val hit = max(0f, 1f - abs(t) / 400f)
        val swing = 0.15f * t / (abs(t) + 100f)
        p(R_ELBOW, 0.55f + swing / 2, 0.35f - hit * 0.3f); p(R_WRIST, 0.55f + swing, 0.45f - hit * 0.6f)
        PoseFrame(t, x, y, List(17) { score })
    }

    @Test
    fun findsTheThreeMomentsAndMeasures() {
        val m = ServePose.metrics(serve())!!
        assertTrue(m.rightHanded)
        assertEquals(0L, m.contactT)
        assertEquals(-700L, m.trophyT)
        assertEquals(-400L, m.kneeT)
        assertEquals(700L, m.trophyToContactMs)
        assertEquals(180f, m.elbowContactDeg, 1f)
        assertEquals(0f, m.tossArmDeg, 1f)
        // Impatto a 0,15 sopra il naso: (0,9 + 0,15) / statura (0,75 / 0,89).
        assertEquals(1.246f, m.contactHeight, 0.01f)
        assertTrue("${m.kneeMinDeg}", m.kneeMinDeg < 170f)
        assertTrue(m.reliable)
    }

    @Test
    fun lowConfidenceIsNotReliable() {
        val m = ServePose.metrics(serve(score = 0.35f))!!
        assertTrue(!m.reliable)
        assertNull(ServePose.mean(listOf(m)))
        // Corpo non visto: niente misure.
        assertNull(ServePose.metrics(serve(score = 0.1f)))
        assertNull(ServePose.metrics(serve().take(3)))
    }

    @Test
    fun anglesAndMeans() {
        val f = serve().first { it.t == 0L }
        assertEquals(180f, ServePose.angle(f, R_SHOULDER, R_ELBOW, R_WRIST), 0.5f)
        val a = ServePose.metrics(serve())!!
        val mean = ServePose.mean(listOf(a, a.copy(kneeMinDeg = a.kneeMinDeg - 20)))!!
        assertEquals(2, mean.n)
        assertEquals(a.kneeMinDeg - 10, mean.kneeMinDeg, 0.01f)
    }

    /**
     * Un dritto finto di un destro visto di lato, corpo al centro (x 0,5): il polso va indietro fino a -500 ms (0,30),
     * accelera fino all'impatto a 0 ms davanti al corpo (0,62) all'altezza della vita, finisce in alto a +400 ms.
     */
    private fun forehand(): List<PoseFrame> = (-1200L..800L step 50).map { t ->
        val x = MutableList(17) { 0.5f }
        val y = MutableList(17) { 0.5f }
        fun p(i: Int, px: Float, py: Float) { x[i] = px; y[i] = py }
        p(NOSE, 0.5f, 0.15f)
        p(L_SHOULDER, 0.48f, 0.25f); p(R_SHOULDER, 0.52f, 0.25f)
        p(L_HIP, 0.49f, 0.5f); p(R_HIP, 0.51f, 0.5f)
        val bend = max(0f, 1f - abs(t + 250) / 400f) * 0.06f
        p(L_KNEE, 0.49f + bend, 0.7f); p(R_KNEE, 0.51f + bend, 0.7f)
        p(L_ANKLE, 0.49f, 0.9f); p(R_ANKLE, 0.51f, 0.9f)
        p(L_ELBOW, 0.45f, 0.35f); p(L_WRIST, 0.45f, 0.42f)
        val (wx, wy) = when {
            t <= -500 -> 0.42f - 0.12f * (t + 1200) / 700f to 0.45f
            t <= 0 -> { val k = (t + 500) / 500f; 0.30f + 0.32f * k * k * k to 0.5f }
            t <= 400 -> 0.62f + 0.05f * t / 400f to 0.5f - 0.35f * t / 400f
            else -> 0.67f to 0.15f + 0.1f * (t - 400) / 400f
        }
        p(R_WRIST, wx, wy)
        p(R_ELBOW, (wx + 0.52f) / 2, (wy + 0.25f) / 2 + 0.01f)
        PoseFrame(t, x, y, List(17) { 0.9f })
    }

    @Test
    fun forehandMomentsAndMeasures() {
        val m = ServePose.groundMetrics(forehand())!!
        assertTrue(m.rightHanded)
        assertEquals(0L, m.contactT)
        assertEquals(-500L, m.prepT)
        assertEquals(400L, m.finishT)
        assertEquals(500L, m.prepToContactMs)
        val stature = 0.75f / 0.89f
        assertEquals(0.12f / stature, m.contactInFront, 0.01f)
        assertEquals(0.20f / stature, m.backswing, 0.01f)
        assertEquals(0.40f / stature, m.contactHeight, 0.01f)
        assertEquals(0.75f / stature, m.finishHeight, 0.01f)
        assertTrue("${m.kneeMinDeg}", m.kneeMinDeg < 175f)
        assertTrue(m.reliable)
        assertEquals(1, ServePose.groundMean(listOf(m))!!.n)
    }
}
