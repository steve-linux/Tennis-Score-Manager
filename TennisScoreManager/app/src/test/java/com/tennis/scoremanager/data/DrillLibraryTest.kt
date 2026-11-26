// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** La raccolta di esercizi: ogni esercizio ha i suoi testi, un disegno dentro il campo e, se c'è, un esercizio guidato valido. */
class DrillLibraryTest {

    private val texts = DrillLibrary.texts(File("src/main/assets/drills/it.json").readText())

    @Test
    fun everyDrillHasItsTexts() {
        val ids = DrillLibrary.ITEMS.map { it.id }
        assertEquals("id doppi", ids.size, ids.toSet().size)
        assertEquals(ids.toSet(), texts.keys)
        for ((id, t) in texts) {
            assertTrue(id, t.name.isNotBlank() && t.goal.isNotBlank() && t.easier.isNotBlank() && t.harder.isNotBlank())
            assertTrue("$id: almeno 3 passi", t.steps.size >= 3 && t.steps.all { it.isNotBlank() })
        }
    }

    @Test
    fun diagramsStayOnTheCourtArea() {
        for (item in DrillLibrary.ITEMS) {
            val d = item.diagram
            val pts = d.players.map { it.at } + d.paths.flatMap { it.points } + d.cones + d.zones.flatMap { listOf(it.a, it.b) }
            for (p in pts) {
                assertTrue("${item.id} x=${p.x}", p.x in -7f..7f)
                assertTrue("${item.id} y=${p.y}", p.y in -13.9f..13.9f)
                if (d.half) assertTrue("${item.id}: mezzo campo, y=${p.y}", p.y >= -1.2f)
            }
            assertTrue(item.id, d.players.isNotEmpty())
            // I giocatori che servono ci sono nel disegno (chi lancia le palle non conta).
            assertTrue(item.id, d.players.count { !it.feeder } >= item.players)
        }
    }

    @Test
    fun guidedDrillsCanBeAdded() {
        val guided = DrillLibrary.ITEMS.filter { it.guided != null }
        assertTrue(guided.size >= 5)
        for (item in guided) {
            val d = DrillLibrary.toDrill(item, texts.getValue(item.id).name, 1L)
            assertNotNull(d)
            assertTrue(item.id, d!!.steps.isNotEmpty() && d.total > 0)
            assertEquals(texts.getValue(item.id).name, d.name)
        }
    }

    @Test
    fun filtersCombine() {
        assertEquals(DrillLibrary.ITEMS.size, DrillLibrary.filter(null, null, null).size)
        assertTrue(DrillLibrary.filter(DrillLibrary.Topic.SERVE, null, null).all { it.topic == DrillLibrary.Topic.SERVE })
        assertTrue(DrillLibrary.filter(null, DrillLibrary.Level.BEGINNER, 2).all { it.level == DrillLibrary.Level.BEGINNER && it.players == 2 })
    }
}
