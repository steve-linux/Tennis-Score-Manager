// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StrokeCheckTest {
    private fun serve(elbow: Float = 170f, knee: Float = 130f, height: Float = 1.3f, toss: Float = 10f, quality: Float = 0.8f) =
        ServeMetrics(knee, elbow, height, toss, 5f, 600, 300, quality, -600, -300, 0, true)
    private fun ground(front: Float = 0.2f, back: Float = 0.5f, finish: Float = 1.0f, knee: Float? = 140f, height: Float = 0.6f) =
        GroundMetrics(knee, 150f, height, front, back, finish, 500, 0.8f, -500, 0, 300, true)
    private fun kinds(l: List<StrokeFlag>) = l.map { it.kind }.toSet()

    @Test fun serveFlagsAreConservativeAndSkipUnreliable() {
        assertTrue(StrokeCheck.serve(serve(), null).isEmpty())
        assertEquals(setOf(StrokeFlag.Kind.ELBOW_BENT, StrokeFlag.Kind.KNEES_STIFF, StrokeFlag.Kind.CONTACT_LOW, StrokeFlag.Kind.TOSS_TILTED),
            kinds(StrokeCheck.serve(serve(elbow = 140f, knee = 170f, height = 1.05f, toss = 40f), null)))
        assertTrue(StrokeCheck.serve(serve(elbow = 120f, quality = 0.2f), null).isEmpty())
    }

    @Test fun differentFromTheUsualOnlyWithAReference() {
        val ref = ServePose.mean(listOf(serve(elbow = 178f, knee = 120f, height = 1.4f)))!!
        val f = StrokeCheck.serve(serve(elbow = 160f, knee = 140f, height = 1.3f), ref)
        assertEquals(setOf(StrokeFlag.Kind.ELBOW_LESS, StrokeFlag.Kind.KNEES_LESS, StrokeFlag.Kind.CONTACT_LOWER), kinds(f))
        assertEquals(178f, f.first { it.kind == StrokeFlag.Kind.ELBOW_LESS }.ref!!, 0.01f)
        assertTrue(StrokeCheck.serve(serve(elbow = 160f), null).isEmpty())
    }

    @Test fun groundFlagsAndVolley() {
        assertEquals(setOf(StrokeFlag.Kind.CONTACT_BEHIND, StrokeFlag.Kind.PREP_SHORT, StrokeFlag.Kind.FINISH_LOW),
            kinds(StrokeCheck.ground(ground(front = -0.05f, back = 0.1f, finish = 0.6f), null)))
        // Nella volée preparazione corta e finale basso sono giusti.
        assertEquals(setOf(StrokeFlag.Kind.CONTACT_BEHIND), kinds(StrokeCheck.ground(ground(front = -0.05f, back = 0.1f, finish = 0.6f), null, volley = true)))
        val ref = ServePose.groundMean(listOf(ground(front = 0.3f, back = 0.6f, finish = 1.1f)))!!
        assertEquals(setOf(StrokeFlag.Kind.CONTACT_LATER, StrokeFlag.Kind.PREP_SHORTER, StrokeFlag.Kind.FINISH_LOWER),
            kinds(StrokeCheck.ground(ground(front = 0.1f, back = 0.35f, finish = 0.9f), ref)))
    }

    @Test fun recurringInTheOutsMoreThanInTheIns() {
        val late = listOf(StrokeFlag(StrokeFlag.Kind.CONTACT_LATER))
        val r = StrokeCheck.recurring(listOf(late, late, emptyList()), listOf(emptyList(), late, emptyList(), emptyList()))
        assertEquals(listOf(Triple(StrokeFlag.Kind.CONTACT_LATER, 2, 3)), r)
        // Una volta sola non basta.
        assertTrue(StrokeCheck.recurring(listOf(late, emptyList()), emptyList()).isEmpty())
        // "Dietro al corpo" e "più indietro del solito" si contano insieme, col nome più frequente.
        val behind = listOf(StrokeFlag(StrokeFlag.Kind.CONTACT_BEHIND))
        assertEquals(listOf(Triple(StrokeFlag.Kind.CONTACT_LATER, 3, 3)), StrokeCheck.recurring(listOf(late, behind, late), emptyList()))
    }

    @Test fun postureFromFrames() {
        // In piedi: naso a 0.1, spalle a 0.25, anche a 0.5, caviglie a 0.9; busto inclinato di 0.05 su 0.25.
        fun frame(t: Long): PoseFrame {
            val x = MutableList(17) { 0.5f }; val y = MutableList(17) { 0.5f }
            y[ServePose.NOSE] = 0.1f
            x[ServePose.L_SHOULDER] = 0.40f; x[ServePose.R_SHOULDER] = 0.60f; y[ServePose.L_SHOULDER] = 0.25f; y[ServePose.R_SHOULDER] = 0.25f
            x[ServePose.L_HIP] = 0.40f; x[ServePose.R_HIP] = 0.50f; y[ServePose.L_HIP] = 0.5f; y[ServePose.R_HIP] = 0.5f
            x[ServePose.L_ANKLE] = 0.35f; x[ServePose.R_ANKLE] = 0.62f; y[ServePose.L_ANKLE] = 0.9f; y[ServePose.R_ANKLE] = 0.9f
            return PoseFrame(t, x, y, List(17) { 0.9f })
        }
        val p = StrokeCheck.posture(listOf(frame(-100), frame(0), frame(100)), 0)!!
        assertEquals(Math.toDegrees(kotlin.math.atan2(0.05, 0.25)).toFloat(), p.trunkDeg!!, 0.5f)
        assertEquals(0f, p.shouldersDeg!!, 0.5f)
        assertEquals(0.27f / (0.8f / 0.89f), p.stance!!, 0.01f)
        assertNull(StrokeCheck.posture(listOf(frame(0)), 5_000))
    }
}
