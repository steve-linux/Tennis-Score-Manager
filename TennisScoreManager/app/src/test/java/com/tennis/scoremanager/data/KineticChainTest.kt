// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import com.tennis.scoremanager.data.KineticChain.Segment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KineticChainTest {
    @Test fun orderWithTolerance() {
        val good = mapOf(Segment.LEGS to -300L, Segment.HIPS to -230L, Segment.SHOULDERS to -160L, Segment.ELBOW to -90L, Segment.WRIST to -20L)
        assertNull(KineticChain.outOfOrder(good, 66))
        // Le spalle un fotogramma prima delle anche: tollerato.
        assertNull(KineticChain.outOfOrder(good + (Segment.SHOULDERS to -280L), 66))
        // Il polso molto prima del gomito: fuori ordine (la prima parte superata è la spalla).
        assertEquals(Segment.WRIST to Segment.SHOULDERS, KineticChain.outOfOrder(good + (Segment.WRIST to -250L), 66))
    }

    @Test fun peaksFromFrames() {
        // Ogni parte si muove in un fotogramma diverso: gambe (t 1-2), anche (2-3), spalle (3-4), gomito (4-5), polso (5-6).
        val n = 8
        val frames = (0 until n).map { k ->
            val x = MutableList(17) { 0.5f }; val y = MutableList(17) { 0.5f }
            y[ServePose.NOSE] = 0.1f
            // Gambe: ginocchio piegato poi disteso tra 1 e 2.
            val kneeOut = if (k >= 2) 0.0f else 0.08f
            x[ServePose.L_HIP] = 0.48f; y[ServePose.L_HIP] = 0.5f; x[ServePose.L_KNEE] = 0.48f + kneeOut; y[ServePose.L_KNEE] = 0.7f; x[ServePose.L_ANKLE] = 0.48f; y[ServePose.L_ANKLE] = 0.9f
            // Anche e spalle: larghezza che cambia.
            x[ServePose.R_HIP] = if (k >= 3) 0.6f else 0.52f; y[ServePose.R_HIP] = 0.5f
            x[ServePose.R_KNEE] = 0.55f; y[ServePose.R_KNEE] = 0.7f; x[ServePose.R_ANKLE] = 0.55f; y[ServePose.R_ANKLE] = 0.9f
            x[ServePose.L_SHOULDER] = 0.46f; y[ServePose.L_SHOULDER] = 0.25f
            x[ServePose.R_SHOULDER] = if (k >= 4) 0.62f else 0.54f; y[ServePose.R_SHOULDER] = 0.25f
            // Gomito: si distende tra 4 e 5; polso: scatta tra 5 e 6.
            x[ServePose.R_ELBOW] = 0.62f; y[ServePose.R_ELBOW] = 0.35f
            val wx = if (k >= 6) 0.9f else if (k >= 5) 0.62f else 0.70f
            x[ServePose.R_WRIST] = wx; y[ServePose.R_WRIST] = if (k >= 5) 0.45f else 0.35f
            PoseFrame(k * 66L, x, y, List(17) { 0.9f })
        }
        val r = KineticChain.compute(frames, contactT = 6 * 66L, rightHanded = true)!!
        assertEquals(setOf(Segment.LEGS, Segment.HIPS, Segment.SHOULDERS, Segment.ELBOW, Segment.WRIST), r.peaks.keys)
        assertTrue(r.peaks.getValue(Segment.LEGS) < r.peaks.getValue(Segment.WRIST))
        assertTrue(r.inOrder)
    }
}
