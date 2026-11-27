// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Il timer a voce: fasi, giri e quello che la voce dice a ogni secondo. */
class DrillTimerTest {

    private val plan = DrillTimer.Plan(work = 30, rest = 20, rounds = 3, lead = 10)

    @Test
    fun phasesAndRounds() {
        assertEquals(10 + 3 * 30 + 2 * 20, plan.total)
        assertEquals(DrillTimer.State(DrillTimer.Kind.PREP, 1, 10), DrillTimer.state(plan, 0))
        assertEquals(DrillTimer.State(DrillTimer.Kind.WORK, 1, 30), DrillTimer.state(plan, 10))
        assertEquals(DrillTimer.State(DrillTimer.Kind.REST, 1, 20), DrillTimer.state(plan, 40))
        assertEquals(DrillTimer.State(DrillTimer.Kind.WORK, 2, 30), DrillTimer.state(plan, 60))
        assertEquals(DrillTimer.State(DrillTimer.Kind.WORK, 3, 1), DrillTimer.state(plan, plan.total - 1))
        assertEquals(DrillTimer.Kind.DONE, DrillTimer.state(plan, plan.total).kind)
    }

    @Test
    fun voiceCues() {
        val all = (0..plan.total).flatMap { t -> DrillTimer.cues(plan, t).map { t to it } }
        // Prima del via: 3, 2, 1; poi "via" a ogni giro (l'ultimo segnato), recupero tra un giro e l'altro.
        assertEquals(listOf(7, 8, 9), all.filter { it.second is DrillTimer.Cue.Count && it.first < 10 }.map { it.first })
        assertEquals(listOf(DrillTimer.Cue.Go(1, 3, false), DrillTimer.Cue.Go(2, 3, false), DrillTimer.Cue.Go(3, 3, true)), all.map { it.second }.filterIsInstance<DrillTimer.Cue.Go>())
        assertEquals(listOf(40, 90), all.filter { it.second is DrillTimer.Cue.Rest }.map { it.first })
        assertEquals(3, all.count { it.second == DrillTimer.Cue.TenLeft })
        assertEquals(listOf(plan.total), all.filter { it.second == DrillTimer.Cue.Done }.map { it.first })
        // Lavori corti: niente "ancora dieci secondi".
        val short = DrillTimer.Plan(20, 10, 2)
        assertTrue((0..short.total).none { DrillTimer.cues(short, it).contains(DrillTimer.Cue.TenLeft) })
    }

    @Test
    fun everyDrillCanStart() {
        // Ogni esercizio della raccolta si avvia: o come esercizio guidato (colpi contati) o col timer, mai tutti e due.
        for (item in DrillLibrary.ITEMS) assertTrue(item.id, (item.guided != null) != (item.timer != null))
    }
}
