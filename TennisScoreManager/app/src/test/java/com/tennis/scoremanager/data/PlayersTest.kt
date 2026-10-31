// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import com.tennis.scoremanager.model.Side
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayersTest {
    private val rossi = PlayerProfile("id-rossi", "Rossi")

    @Test
    fun namesBecomePlayersOnceAndHomonymsWait() {
        // Un nome nuovo crea un giocatore; uno che c'è (maiuscole e spazi non contano) usa il suo.
        val (su, created) = Players.assign(SetupData(p1a = "rossi ", p2a = "Bianchi"), listOf(rossi), 1)
        assertEquals("id-rossi", su.p1aId)
        assertEquals(listOf("Bianchi"), created.map { it.name })
        assertEquals(created[0].id, su.p2aId)
        // Due volte lo stesso nome nuovo nella stessa partita (doppio): una persona sola.
        val (dbl, made) = Players.assign(SetupData(doubles = true, p1a = "Verdi", p1b = "Neri", p2a = "Verdi", p2b = "Gialli"), emptyList(), 1)
        assertEquals(dbl.p1aId, dbl.p2aId)
        assertEquals(3, made.size)
        // Con due giocatori dello stesso nome il posto resta col solo nome: lo si sceglie a mano.
        val twin = PlayerProfile("id-rossi-2", "Rossi")
        val (amb, none) = Players.assign(SetupData(p1a = "Rossi"), listOf(rossi, twin), 1)
        assertEquals("", amb.p1aId)
        assertTrue(none.isEmpty())
        assertEquals(1, Players.ambiguous(amb, listOf(rossi, twin)).size)
        // Già scelto: non si tocca.
        assertEquals("id-rossi-2", Players.assign(SetupData(p1a = "Rossi", p1aId = "id-rossi-2"), listOf(rossi, twin), 1).first.p1aId)
    }

    @Test
    fun searchAndAliases() {
        val list = listOf(PlayerProfile("1", "Mario Rossi"), PlayerProfile("2", "Rosà Verdi"), PlayerProfile("3", "Bianchi", aliases = setOf("x-9")))
        assertEquals(listOf("2", "1"), Players.search(list, "ros").map { it.id })
        assertEquals("3", Players.byId(list, "x-9")?.id)
        assertEquals(null, Players.byId(list, ""))
    }

    @Test
    fun statsCountPeopleNotNames() {
        fun rec(id: String, p1Id: String) = MatchRecord(
            id, SetupData(p1a = "Rossi", p1aId = p1Id, p2a = "Bianchi", p2aId = "b"),
            MatchOptions(), com.tennis.scoremanager.model.RulesConfig(), finished = true,
        )
        val played = listOf(PlayerStats.Played(rec("m1", "r1"), Side.P1), PlayerStats.Played(rec("m2", "r2"), Side.P2))
        // Due Rossi diversi: due giocatori, ciascuno con la sua partita.
        val players = PlayerStats.players(played)
        assertEquals(3, players.size)
        assertEquals(1, players.first { it.key == "r1" }.won)
        assertEquals(0, players.first { it.key == "r2" }.won)
        assertNotEquals(PlayerStats.lines(played, "r1").map { it.rec.id }, PlayerStats.lines(played, "r2").map { it.rec.id })
        // Le partite di prima, senza identificativi, restano per nome.
        assertEquals("rossi", PlayerStats.ident("", " Rossi"))
    }

    @Test
    fun matchesFromAnotherPhone() {
        val mine = listOf(PlayerProfile("A", "Rossi", aliases = setOf("A-old")), PlayerProfile("B", "Bianchi"))
        // Conosciuto (anche come alias): prende il nostro identificativo.
        val (known, n1, q1) = Players.resolveIncoming(SetupData(p1a = "Rossi", p1aId = "A-old", p2a = "Bianchi", p2aId = "B"), mine, 1)
        assertEquals("A", known.p1aId)
        assertTrue(n1.isEmpty() && q1.isEmpty())
        // Sconosciuto con un nome nuovo: diventa nostro col suo identificativo (i telefoni restano allineati).
        val (_, n2, q2) = Players.resolveIncoming(SetupData(p1a = "Verdi", p1aId = "V-9", p2a = "Bianchi", p2aId = "B"), mine, 1)
        assertEquals(listOf("V-9" to "Verdi"), n2.map { it.id to it.name })
        assertTrue(q2.isEmpty())
        // Sconosciuto col nome di uno dei nostri: si chiede se è lui, e intanto resta col suo identificativo.
        val (asked, n3, q3) = Players.resolveIncoming(SetupData(p1a = "Rossi", p1aId = "R-2", p2a = "Neri"), mine, 1)
        assertEquals("R-2", asked.p1aId)
        assertEquals(listOf(Players.Question("R-2", "Rossi", listOf("A"))), q3)
        // I posti senza identificativo (versioni vecchie) li sistema assign.
        assertTrue(n3.isEmpty())
        assertEquals("", asked.p2aId)
    }

    @Test
    fun nicknameFirstAndLastName() {
        assertEquals("Ste", PlayerProfile.display("Stefano", "Spagnolo", "Ste"))
        assertEquals("Mario Rossi", PlayerProfile.display(" Mario ", "Rossi", " "))
        val ste = PlayerProfile("s", "Ste", "Stefano", "Spagnolo", "Ste")
        val old = PlayerProfile("o", "Mario", nickname = "Mario")
        assertTrue(ste.complete)
        assertTrue(!old.complete)
        // Si trova per il nome che si vede, il soprannome, nome e cognome o solo il cognome.
        assertEquals(listOf("s"), Players.byName(listOf(ste, old), "stefano spagnolo").map { it.id })
        assertEquals(listOf("s"), Players.byName(listOf(ste, old), "Ste").map { it.id })
        assertEquals(listOf("s"), Players.search(listOf(ste, old), "spag").map { it.id })
        // Omonimi veri: stesso nome e cognome.
        assertEquals(listOf("s"), Players.sameFullName(listOf(ste, old), "stefano", "SPAGNOLO").map { it.id })
        // Le schede da un altro telefono completano chi da noi è incompleto, senza toccare chi è completo.
        val filled = Players.fillFrom(listOf(old, ste), listOf(PlayerProfile("o", "Mario Rossi", "Mario", "Rossi"), PlayerProfile("s", "X", "Altro", "Nome", "X")))
        assertEquals("Mario Rossi", filled[0].name)
        assertTrue(filled[0].complete)
        assertEquals(ste, filled[1])
    }
}
