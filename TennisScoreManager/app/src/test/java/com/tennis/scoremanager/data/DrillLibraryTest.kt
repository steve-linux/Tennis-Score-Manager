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

    @Test
    fun guidedDrillsHaveTheirVoice() {
        for (item in DrillLibrary.ITEMS.filter { it.guided != null }) {
            val t = texts.getValue(item.id)
            assertTrue(item.id, t.cue.isNotBlank() && t.missTip.isNotBlank() && t.streakTip.isNotBlank())
            assertEquals("${item.id}: una frase per passo", item.guided!!.second.size, t.stepCues.size)
        }
    }

    @Test
    fun coachSpeaksAtTheRightMoments() {
        assertEquals("volley_feed", DrillLibrary.itemIdOf("lib-volley_feed-1764190000000"))
        assertEquals(null, DrillLibrary.itemIdOf("t-serve-t-wide"))
        val t = texts.getValue("volley_feed")
        val c = DrillLibrary.Coach(t)
        assertTrue(c.intro().startsWith(t.name))
        assertEquals(t.stepCues[1], c.step(1))
        // Due fuori: niente; il terzo: il consiglio; poi si riparte da zero.
        assertEquals(null, c.shot(false)); assertEquals(null, c.shot(false))
        assertEquals(t.missTip, c.shot(false))
        assertEquals(null, c.shot(false))
        // Cinque dentro di fila: la proposta, una volta sola nel passo.
        repeat(4) { assertEquals(null, c.shot(true)) }
        assertEquals(t.streakTip, c.shot(true))
        repeat(5) { assertEquals(null, c.shot(true)) }
        // Nuovo passo: la proposta torna possibile.
        c.step(0)
        repeat(4) { c.shot(true) }
        assertEquals(t.streakTip, c.shot(true))
        // Un colpo tolto azzera le serie.
        c.shot(false); c.shot(false); c.undo()
        assertEquals(null, c.shot(false))
    }

    @Test
    fun everyLanguageHasTheSameDrills() {
        for (lang in com.tennis.scoremanager.model.Lang.entries) {
            val other = DrillLibrary.texts(File("src/main/assets/drills/${lang.code}.json").readText())
            assertEquals(lang.code, texts.keys, other.keys)
            for ((id, it) in texts) {
                val t = other.getValue(id)
                assertTrue("${lang.code} $id", t.name.isNotBlank() && t.goal.isNotBlank() && t.easier.isNotBlank() && t.harder.isNotBlank())
                assertEquals("${lang.code} $id: passi", it.steps.size, t.steps.size)
                assertEquals("${lang.code} $id: voce", it.stepCues.size, t.stepCues.size)
                assertEquals("${lang.code} $id: frase chiave", it.cue.isBlank(), t.cue.isBlank())
                assertEquals("${lang.code} $id: consigli", it.missTip.isBlank() || it.streakTip.isBlank(), t.missTip.isBlank() || t.streakTip.isBlank())
            }
        }
    }
}
