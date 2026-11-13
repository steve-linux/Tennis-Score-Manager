// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import com.tennis.scoremanager.model.MatchFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchCardsTest {
    private val rossi = PlayerProfile("p1", "Mario Rossi", "Mario", "Rossi")
    private val bianchi = PlayerProfile("p2", "Luca Bianchi", "Luca", "Bianchi")
    private val t = ClubTournament(
        "t1", "Torneo sociale", category = "Singolare maschile",
        entries = listOf(CTEntry("e1", listOf("p1"), listOf("Mario Rossi")), CTEntry("e2", listOf("p2"), listOf("Luca Bianchi"))),
        matches = listOf(
            CTMatch("m1", 2, 0, a = "e1", b = "e2", court = "Campo 2", at = 1_000),
            CTMatch("m2", 2, 1, a = "e1"),
            CTMatch("m3", 2, 2, a = "e1", b = "e2", winner = "e1"),
        ),
        rules = CardRules(MatchFormat.TWO_SETS_MATCH_TIEBREAK, noAd = true),
    )
    private val find = { id: String -> listOf(rossi, bianchi).firstOrNull { it.id == id } }

    @Test fun cardCarriesPlayersRulesAndWhere() {
        val card = MatchCards.forTournament(t, t.matches[0], "ABCD2345", 99, "TC Prova", "+39 333 1234567", "Semifinale", find)!!
        assertEquals("t1", card.ref)
        assertEquals("m1", card.match)
        assertEquals(listOf("p1"), card.side1.map { it.id })
        assertEquals("Bianchi", card.side2[0].lastName)
        assertEquals("Campo 2", card.court)
        assertEquals(1_000L, card.at)
        assertEquals(MatchFormat.TWO_SETS_MATCH_TIEBREAK, card.rules?.format)
        assertTrue(card.rules!!.noAd)
        assertEquals("+39 333 1234567", card.office)
    }

    @Test fun noCardWithoutBothPlayersOrWhenPlayed() {
        assertNull(MatchCards.forTournament(t, t.matches[1], "X", 0, "", "", "", find))
        assertNull(MatchCards.forTournament(t, t.matches[2], "X", 0, "", "", "", find))
    }

    @Test fun encodeDecodeRoundTripAndRejectsOtherFiles() {
        val card = MatchCards.forTournament(t, t.matches[0], MatchCards.newCode(), 5, "", "", "Semifinale", find)!!
        assertEquals(card, MatchCards.decode(MatchCards.encode(card)))
        assertNull(MatchCards.decode("{\"tsm\":\"altro\",\"code\":\"x\",\"issuedAt\":0,\"event\":\"\",\"ref\":\"\",\"match\":\"\",\"side1\":[],\"side2\":[]}"))
        assertNull(MatchCards.decode("non è json"))
    }

    @Test fun codeIsReadableAndFileNameSafe() {
        val code = MatchCards.newCode()
        assertEquals(8, code.length)
        assertFalse(code.any { it in "01OIL" })
        val card = MatchCards.forTournament(t, t.matches[0], code, 0, "", "", "Semi/finale", find)!!
        assertEquals("Semi_finale Rossi - Bianchi.tsm", MatchCards.fileName(card))
        assertNotNull(card.side1.firstOrNull())
    }
}
