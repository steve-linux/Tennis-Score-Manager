// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/** Le note dell'allenatore come elenco: dal testo dettato di prima, filtri e resoconto per argomento. */
class CoachNotesTest {

    private fun at(d: Int, m: Int, h: Int, min: Int) = Calendar.getInstance().apply { clear(); set(2026, m - 1, d, h, min) }.timeInMillis

    @Test
    fun dictatedTextBecomesSingleNotes() {
        val text = """
            Obiettivo: seconda di servizio più carica
            📅 26.11.26  🕐 18:45
            Rovescio da rivedere
            🕐 18:30
            Servizio più sciolto
            📅 25.11.26  🕐 17:05
            Volée
            sul piede giusto
        """.trimIndent()
        assertTrue(CoachNotes.hasLegacy(text))
        val (free, notes) = CoachNotes.split(text)
        assertEquals("Obiettivo: seconda di servizio più carica", free)
        assertEquals(listOf("Rovescio da rivedere", "Servizio più sciolto", "Volée\nsul piede giusto"), notes.map { it.text })
        assertEquals(listOf(at(26, 11, 18, 45), at(26, 11, 18, 30), at(25, 11, 17, 5)), notes.map { it.at })
        assertFalse(CoachNotes.hasLegacy(free))
    }

    @Test
    fun filtersByPeriodTopicAndText() {
        val notes = listOf(
            CoachNote("a", at(1, 9, 10, 0), "Rovescio corto", NoteTag.BACKHAND),
            CoachNote("b", at(20, 11, 10, 0), "Servizio: lancio più alto", NoteTag.SERVE),
            CoachNote("c", at(25, 11, 10, 0), "Rovescio lungolinea bene", NoteTag.BACKHAND),
        )
        assertEquals(listOf("c", "b"), CoachNotes.filter(notes, at(1, 11, 0, 0), null, "").map { it.id })
        assertEquals(listOf("c", "a"), CoachNotes.filter(notes, null, NoteTag.BACKHAND, "").map { it.id })
        assertEquals(listOf("b"), CoachNotes.filter(notes, null, null, "LANCIO").map { it.id })
    }

    @Test
    fun reportGroupsByTopicOldestFirst() {
        val notes = listOf(
            CoachNote("c", at(25, 11, 10, 0), "Lungolinea bene", NoteTag.BACKHAND),
            CoachNote("x", at(24, 11, 10, 0), "Stanco"),
            CoachNote("a", at(1, 11, 10, 0), "Corto", NoteTag.BACKHAND),
            CoachNote("b", at(20, 11, 10, 0), "Lancio alto", NoteTag.SERVE),
        )
        val r = CoachNotes.report(notes, { it?.name ?: "-" }, { "d$it".take(3) })
        assertEquals(listOf("SERVE", "BACKHAND", "-"), r.map { it.first })
        assertEquals(listOf("Corto", "Lungolinea bene"), r[1].second.map { it.substringAfter(" – ") })
    }

    @Test
    fun oldPlayersWithoutNotesStillLoad() {
        val p = Storage.JSON.decodeFromString(PlayerProfile.serializer(), """{"id":"p","name":"Rossi","note":"vecchia nota"}""")
        assertTrue(p.coachNotes.isEmpty())
        assertEquals("vecchia nota", p.note)
    }
}
