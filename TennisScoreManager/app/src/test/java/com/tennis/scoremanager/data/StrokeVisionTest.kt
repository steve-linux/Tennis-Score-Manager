// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StrokeVisionTest {
    /** Una persona in piedi (naso 0,1, caviglie 0,9) con i polsi dove dice [wrists] (destro, sinistro) a ogni fotogramma. */
    private fun clip(vararg wrists: Pair<Pair<Float, Float>, Pair<Float, Float>>): List<PoseFrame> = wrists.mapIndexed { k, (r, l) ->
        val x = MutableList(17) { 0.5f }; val y = MutableList(17) { 0.5f }
        y[ServePose.NOSE] = 0.1f
        x[ServePose.L_HIP] = 0.48f; x[ServePose.R_HIP] = 0.52f; y[ServePose.L_HIP] = 0.5f; y[ServePose.R_HIP] = 0.5f
        y[ServePose.L_ANKLE] = 0.9f; y[ServePose.R_ANKLE] = 0.9f; x[ServePose.L_ANKLE] = 0.4f; x[ServePose.R_ANKLE] = 0.6f
        x[ServePose.R_WRIST] = r.first; y[ServePose.R_WRIST] = r.second
        x[ServePose.L_WRIST] = l.first; y[ServePose.L_WRIST] = l.second
        PoseFrame(k * 66L, x, y, List(17) { 0.9f })
    }

    @Test fun serveBothArmsUp() {
        val g = StrokeVision.guess(clip(
            (0.55f to 0.5f) to (0.45f to 0.5f), (0.6f to 0.4f) to (0.45f to -0.15f), (0.55f to 0.2f) to (0.45f to 0.0f),
            (0.5f to -0.15f) to (0.45f to 0.3f), (0.3f to 0.4f) to (0.45f to 0.5f),
        ))
        assertEquals(StrokeGuess(TrainedStroke.SERVE, true), g)
    }

    @Test fun forehandArmsApartBackhandTogether() {
        // Dritto: preparazione con il polso destro indietro (0,8) e il sinistro steso avanti (0,3), poi il colpo.
        val fh = clip((0.7f to 0.5f) to (0.35f to 0.45f), (0.8f to 0.55f) to (0.3f to 0.45f), (0.5f to 0.5f) to (0.4f to 0.5f), (0.15f to 0.3f) to (0.45f to 0.5f))
        assertEquals(TrainedStroke.FOREHAND, StrokeVision.guess(fh)?.stroke)
        // Rovescio: le due mani insieme indietro, poi il colpo.
        val bh = clip((0.75f to 0.5f) to (0.72f to 0.52f), (0.82f to 0.55f) to (0.8f to 0.56f), (0.5f to 0.5f) to (0.5f to 0.52f), (0.15f to 0.3f) to (0.2f to 0.32f))
        assertEquals(StrokeGuess(TrainedStroke.BACKHAND, true), StrokeVision.guess(bh))
    }

    @Test fun volleyShortSwing() {
        val v = clip((0.55f to 0.45f) to (0.4f to 0.5f), (0.6f to 0.45f) to (0.4f to 0.5f), (0.45f to 0.45f) to (0.4f to 0.5f), (0.4f to 0.45f) to (0.4f to 0.5f))
        val g = StrokeVision.guess(v)!!
        assertEquals(TrainedStroke.VOLLEY, g.stroke)
        assertTrue(g.sure)
    }

    @Test fun nothingWithoutBody() {
        assertNull(StrokeVision.guess(emptyList()))
    }
}

class LessonsTest {
    private fun a(at: Long, stroke: TrainedStroke?, marked: Boolean = false) = ServeAttempt(at, true, ServeSide.DEUCE, ServeKind.FIRST, marked = marked, stroke = stroke, clip = "c$at")

    @Test fun splitByStroke() {
        val l = ServeSession("s1", 0L, serves = listOf(a(1, TrainedStroke.FOREHAND), a(2, TrainedStroke.BACKHAND), a(3, null), a(4, TrainedStroke.BACKHAND)), folder = "Lezioni/x")
        val parts = Lessons.split(l) { listOf("Dritto", "Rovescio", "Servizio", "Volée")[it] }
        assertEquals(listOf(TrainedStroke.FOREHAND, TrainedStroke.BACKHAND), parts.map { it.stroke })
        assertEquals(listOf(1L, 3L), parts[0].serves.map { it.at })
        assertEquals("Lezioni/x/Rovescio", parts[1].folder)
        assertEquals("s1-backhand", parts[1].id)
        assertTrue(parts.all { it.lesson == "s1" })
    }

    @Test fun unmarkedShotsDoNotCount() {
        val n = ServeTraining.count(listOf(a(1, null), a(2, null, marked = true)))
        assertEquals(1, n.total); assertEquals(1, n.inPlay)
    }

    @Test fun markOnlyTheRecentShot() {
        assertEquals(10L, Lessons.toMark(listOf(a(10, null)), 10_000L)?.at)
        assertNull(Lessons.toMark(listOf(a(10, null)), 40_000L))
        assertNull(Lessons.toMark(listOf(a(10, null, marked = true)), 11L))
    }

    @Test fun currentAgendaLesson() {
        val h = 3_600_000L
        val ls = listOf(CoachLesson("a", 10 * h, 60, listOf("p1")), CoachLesson("b", 10 * h, 60, listOf("p2", "p3")))
        assertEquals("b", Lessons.current(ls, "p3", 10 * h + 5)?.id)
        assertEquals("a", Lessons.current(ls, null, 10 * h - 10 * 60_000L)?.id)
        assertNull(Lessons.current(ls, "p1", 12 * h))
    }
}
