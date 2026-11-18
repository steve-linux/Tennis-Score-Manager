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

    @Test fun resultRoundTripAndCheckAgainstTournament() {
        val coded = t.copy(matches = t.matches.map { if (it.id == "m1") it.copy(cardCode = "CODE2345") else it })
        val card = MatchCards.forTournament(coded, coded.matches[0], "CODE2345", 0, "", "", "Finale", find)!!
        val r = MatchCards.result(card, 2, "4-6  6-7(3)", walkover = false, retired = false, disqualified = false, now = 9)
        assertEquals(r, MatchCards.decodeResult(MatchCards.encodeResult(r)))
        assertNull(MatchCards.decodeResult(MatchCards.encode(card)))
        assertNull(MatchCards.decode(MatchCards.encodeResult(r)))
        assertEquals(ResultStatus.READY, MatchCards.check(r, listOf(coded), emptyList(), emptyList()))
        assertEquals(ResultStatus.BAD_CODE, MatchCards.check(r.copy(code = "ALTRO"), listOf(coded), emptyList(), emptyList()))
        assertEquals(ResultStatus.NOT_FOUND, MatchCards.check(r.copy(match = "zz"), listOf(coded), emptyList(), emptyList()))
        val played = ClubTournaments.setResult(coded, "m1", "e2", "4-6  6-7(3)")
        assertEquals(ResultStatus.SAME, MatchCards.check(r, listOf(played), emptyList(), emptyList()))
        assertEquals(ResultStatus.CONFLICT, MatchCards.check(r.copy(winner = 1), listOf(played), emptyList(), emptyList()))
    }

    @Test fun teamAndLadderCardsAndChecks() {
        val team = Team("tm", "Serie C", championship = "Campionato", roster = listOf("p1", "p2"), singles = 1, doubles = 0,
            fixtures = listOf(Fixture("f1", "TC Ospiti", 5_000, place = "Campo 1", rubbers = listOf(Rubber(ours = listOf("p1"), theirs = listOf("Verdi"), cardCode = "TEAM2345")))))
        val f = team.fixtures[0]
        val card = MatchCards.forRubber(team, f, 0, "TEAM2345", 0, "", "", "Singolare 1", find)!!
        assertEquals(CardKind.TEAM, card.kind)
        assertEquals("Verdi", card.side2[0].name)
        assertEquals("", card.side2[0].id)
        assertEquals("Campo 1", card.court)
        assertNull(MatchCards.forRubber(team, f.copy(rubbers = listOf(Rubber())), 0, "X", 0, "", "", "", find))
        val r = MatchCards.result(card, 1, "6-2  6-1", false, false, false, 0)
        assertEquals(ResultStatus.READY, MatchCards.check(r, emptyList(), listOf(team), emptyList()))
        val done = team.copy(fixtures = listOf(Teams.setRubber(f, 0, "6-2 6-1", true)))
        assertEquals(ResultStatus.SAME, MatchCards.check(r, emptyList(), listOf(done), emptyList()))

        val ladder = Ladder("l1", "Classifica", listOf("p2", "p1"), challenges = listOf(Challenge("c1", "p1", "p2", challengerPos = 2, defenderPos = 1, cardCode = "LADR2345")))
        val lc = MatchCards.forChallenge(ladder, ladder.challenges[0], "LADR2345", 0, "", "", "2° → 1°", find)!!
        assertEquals("p1", lc.side1[0].id)
        val lr = MatchCards.result(lc, 1, "6-4  6-4", false, false, false, 0)
        assertEquals(ResultStatus.READY, MatchCards.check(lr, emptyList(), emptyList(), listOf(ladder)))
        val after = Ladders.result(ladder, "c1", "6-4 6-4", true, 1)
        assertEquals(ResultStatus.SAME, MatchCards.check(lr, emptyList(), emptyList(), listOf(after)))
        assertEquals(ResultStatus.CONFLICT, MatchCards.check(lr.copy(winner = 2), emptyList(), emptyList(), listOf(after)))
    }

    @Test fun resultTextAndFileName() {
        val r = MatchResult(code = "x", ref = "t", match = "m", round = "Finale", side1 = "Mario Rossi", side2 = "Luca Bianchi", score = "6-3  2-1")
        assertEquals("6-3  2-1", MatchCards.resultText(r, "w.o.", "rit.", "squal."))
        assertEquals("6-3  2-1 rit.", MatchCards.resultText(r.copy(retired = true), "w.o.", "rit.", "squal."))
        assertEquals("w.o.", MatchCards.resultText(r.copy(walkover = true), "w.o.", "rit.", "squal."))
        assertEquals("Risultato Finale Rossi - Bianchi.tsm", MatchCards.resultFileName("Risultato", r))
    }
}
