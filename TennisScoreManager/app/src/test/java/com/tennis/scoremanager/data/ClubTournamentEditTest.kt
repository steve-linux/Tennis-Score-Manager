// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import com.tennis.scoremanager.data.ClubTournaments.Seat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ClubTournamentEditTest {
    private fun entries(n: Int) = (1..n).map { CTEntry("e$it", listOf("p$it"), listOf("G$it")) }

    private fun cup(n: Int, inDraw: Int = n): ClubTournament {
        val ids = (1..inDraw).map { "e$it" }
        return ClubTournament("t", "Prova", entries = entries(n), matches = ClubTournaments.knockout(ids, Random(1), 0), drawnAt = 1)
    }

    private fun groups(n: Int, inDraw: Int = n, count: Int = 2): ClubTournament {
        val ids = (1..inDraw).map { "e$it" }
        return ClubTournament("t", "Prova", format = CTFormat.GROUPS, groups = count, entries = entries(n), matches = ClubTournaments.groupsDraw(ids, count), drawnAt = 1)
    }

    private fun first(t: ClubTournament) = t.matches.filter { it.round == 1 }.sortedBy { it.slot }
    private fun inDraw(t: ClubTournament) = first(t).flatMap { listOf(it.a, it.b) }.filterNotNull().sorted()
    private fun play(t: ClubTournament, m: CTMatch) = ClubTournaments.setResult(t, m.id, m.a, "6-1 6-1")

    @Test fun scambiaDueGiocatoriSenzaPerderneNessuno() {
        val t = cup(8)
        val (m0, m1) = first(t)
        val done = ClubTournaments.swapSeats(t, Seat(m0.id, 0), Seat(m1.id, 1))!!
        val (n0, n1) = first(done)
        assertEquals(m1.b, n0.a)
        assertEquals(m0.a, n1.b)
        assertEquals(inDraw(t), inDraw(done))
        assertEquals(1, done.manualEdits)
    }

    @Test fun scambioDentroLaStessaPartita() {
        val t = cup(4)
        val m = first(t)[0]
        val n = first(ClubTournaments.swapSeats(t, Seat(m.id, 0), Seat(m.id, 1))!!)[0]
        assertEquals(m.b, n.a)
        assertEquals(m.a, n.b)
    }

    @Test fun ilByeSiPuoDareAUnAltro() {
        val t = cup(6)  // 6 su 8: due bye
        val bye = first(t).first { it.bye }
        val real = first(t).first { !it.bye }
        val moved = bye.a ?: bye.b
        val empty = if (bye.a == null) 0 else 1
        val done = ClubTournaments.swapSeats(t, Seat(bye.id, empty), Seat(real.id, 0))!!
        val m = done.matches.first { it.id == bye.id }
        val r = done.matches.first { it.id == real.id }
        assertFalse("ora si gioca", m.bye)
        assertNull(m.winner)
        assertTrue("l'altra partita ora è un bye", r.bye)
        assertEquals(r.b, r.winner)
        val next = done.matches.first { it.round == 2 && it.slot == bye.slot / 2 }
        assertFalse("chi aveva il bye non è più avanti", next.a == moved || next.b == moved)
    }

    @Test fun nonSiCreaUnaPartitaSenzaGiocatori() {
        val t = cup(6)
        val (b1, b2) = first(t).filter { it.bye }
        val filled = if (b1.a != null) 0 else 1
        val free = if (b2.a == null) 0 else 1
        assertNull(ClubTournaments.swapSeats(t, Seat(b1.id, filled), Seat(b2.id, free)))
        assertNull("due posti vuoti", ClubTournaments.swapSeats(t, Seat(b1.id, 1 - filled), Seat(b2.id, free)))
    }

    @Test fun cioCheEGiocatoNonSiTocca() {
        val t0 = cup(8)
        val (m0, m1, m2) = first(t0)
        val t = play(t0, m0)
        val snap = t
        assertTrue(ClubTournaments.seatLocked(t, t.matches.first { it.id == m0.id }))
        assertNull(ClubTournaments.swapSeats(t, Seat(m0.id, 0), Seat(m1.id, 0)))
        assertNotNull(ClubTournaments.swapSeats(t, Seat(m1.id, 0), Seat(m2.id, 0)))
        assertEquals("niente cambia nel torneo di partenza", m0.a, snap.matches.first { it.id == m0.id }.winner)
    }

    @Test fun lIncontroGiocatoDopoUnByeBloccaQuelBye() {
        var t = cup(6)
        val bye = first(t).first { it.bye }
        val partner = first(t).first { it.slot == (bye.slot xor 1) }
        if (!partner.bye) t = play(t, partner)
        val next = t.matches.first { it.round == 2 && it.slot == bye.slot / 2 }
        t = ClubTournaments.setResult(t, next.id, next.a, "6-0 6-0")
        assertTrue(ClubTournaments.seatLocked(t, t.matches.first { it.id == bye.id }))
    }

    @Test fun ilRipescatoPrendeIlPostoEChiEsceRestaIscritto() {
        val t = cup(9, 8)
        val m = first(t)[0]
        assertEquals(listOf("e9"), ClubTournaments.outsideDraw(t).map { it.id })
        val done = ClubTournaments.replaceSeat(t, Seat(m.id, 0), "e9")!!
        assertEquals("e9", first(done)[0].a)
        assertEquals(listOf(m.a), ClubTournaments.outsideDraw(done).map { it.id })
        assertEquals(9, done.entries.size)
        assertNull("già in tabellone", ClubTournaments.replaceSeat(t, Seat(m.id, 0), "e2"))
        assertNull("non iscritto", ClubTournaments.replaceSeat(t, Seat(m.id, 0), "e99"))
    }

    @Test fun ilRipescatoRiempieUnBye() {
        val t = cup(7, 6)
        val bye = first(t).first { it.bye }
        val done = ClubTournaments.replaceSeat(t, Seat(bye.id, if (bye.a == null) 0 else 1), "e7")!!
        val m = done.matches.first { it.id == bye.id }
        assertFalse(m.bye)
        assertNull(m.winner)
    }

    @Test fun gironiSiScambianoInTutteLePartite() {
        val t = groups(6)
        val before = ClubTournaments.groupMembers(t)
        val x = before[0][0]; val y = before[1][0]
        val done = ClubTournaments.swapGroupEntries(t, x, y)!!
        val after = ClubTournaments.groupMembers(done)
        assertTrue(y in after[0] && x in after[1])
        assertFalse(x in after[0])
        assertEquals(t.matches.size, done.matches.size)
        assertTrue(done.matches.none { it.a == it.b })
    }

    @Test fun gironiBloccatiDaiRisultatiEDalTabellone() {
        val t0 = groups(6)
        val m = t0.matches.first { it.round == 0 }
        val t = ClubTournaments.setResult(t0, m.id, m.a, "6-1 6-1")
        val other = ClubTournaments.groupMembers(t)[1].first()
        assertNull(ClubTournaments.swapGroupEntries(t, m.a!!, other))
        val ko = t0.copy(matches = t0.matches + CTMatch("k", 1, 0, a = "e1", b = "e2"))
        assertTrue(ClubTournaments.groupsLocked(ko))
        assertNull(ClubTournaments.swapGroupEntries(ko, ClubTournaments.groupMembers(ko)[0][0], ClubTournaments.groupMembers(ko)[1][0]))
    }

    @Test fun ripescatoNelGirone() {
        val t = groups(7, 6)
        val old = ClubTournaments.groupMembers(t)[0][0]
        val done = ClubTournaments.replaceGroupEntry(t, old, "e7")!!
        assertTrue("e7" in ClubTournaments.groupMembers(done)[0])
        assertEquals(listOf(old), ClubTournaments.outsideDraw(done).map { it.id })
    }
}
