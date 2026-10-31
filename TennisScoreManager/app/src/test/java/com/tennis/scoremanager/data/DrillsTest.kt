// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DrillsTest {
    private val drill = Drill("d", "Incrociati e lungolinea", TrainedStroke.FOREHAND, listOf(
        DrillStep(3, direction = ShotDirection.CROSS), DrillStep(2, direction = ShotDirection.LINE),
    ))

    @Test
    fun stepsFollowTheShots() {
        assertEquals(5, drill.total)
        assertEquals(Drills.Progress(0, 0, false), Drills.progress(drill, 0))
        assertEquals(Drills.Progress(0, 2, false), Drills.progress(drill, 2))
        // Il terzo colpo chiude il primo passo: si passa al lungolinea.
        val p3 = Drills.progress(drill, 3)
        assertEquals(Drills.Progress(1, 0, false), p3)
        assertTrue(p3.stepJustEnded)
        assertEquals(ShotDirection.LINE, Drills.current(drill, 3)?.direction)
        // Finito: niente passo in corso.
        val p5 = Drills.progress(drill, 5)
        assertTrue(p5.finished && p5.stepJustEnded)
        assertNull(Drills.current(drill, 7))
        // Un passo a metà non è appena finito.
        assertTrue(!Drills.progress(drill, 1).stepJustEnded)
    }

    @Test
    fun templatesForEveryStroke() {
        val t = Drills.templates({ it.name }, 1)
        assertEquals(TrainedStroke.entries.toSet(), t.map { it.stroke }.toSet())
        assertTrue(t.all { d -> d.steps.isNotEmpty() && d.steps.all { it.count > 0 } })
    }
}
