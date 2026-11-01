// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import kotlin.random.Random

class ClubTournamentTest {
    private fun day(y: Int, m: Int, d: Int): Long = Calendar.getInstance().apply { set(y, m - 1, d, 12, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
    private val start = day(2026, 11, 14)

    @Test
    fun seedOrderIsTheClassicBracket() {
        assertEquals(listOf(1, 2), ClubTournaments.seedOrder(2))
        assertEquals(listOf(1, 4, 3, 2), ClubTournaments.seedOrder(4))
        assertEquals(listOf(1, 8, 5, 4, 3, 6, 7, 2), ClubTournaments.seedOrder(8))
        assertEquals(8, ClubTournaments.drawSize(5))
        assertEquals(4, ClubTournaments.seedCount(16))
        assertEquals(2, ClubTournaments.seedCount(8))
    }

    @Test
    fun knockoutSeedsByesAndAdvance() {
        val ids = (1..6).map { "e$it" }   // e1 la più forte
        for (seed in 0 until 20) {
            val ms = ClubTournaments.knockout(ids, Random(seed), 0)
            assertEquals(4 + 2 + 1, ms.size)
            val r1 = ms.filter { it.round == 1 }.sortedBy { it.slot }
            val slots = r1.flatMap { listOf(it.a, it.b) }
            // La 1 in cima, la 2 in fondo; i due bye contro di loro e passano da soli al turno dopo.
            assertEquals("e1", slots[0]); assertNull(slots[1])
            assertEquals("e2", slots[7]); assertNull(slots[6])
            assertEquals(ids.toSet(), slots.filterNotNull().toSet())
            val r2 = ms.filter { it.round == 2 }.sortedBy { it.slot }
            assertEquals("e1", r2[0].a)
            assertEquals("e2", r2[1].b)
        }
    }

    @Test
    fun resultsMoveUpAndCorrectionsClearLaterRounds() {
        var t = ClubTournament("t", "Sociale", entries = (1..4).map { CTEntry("e$it", listOf("p$it"), listOf("P$it")) })
        t = t.copy(matches = ClubTournaments.knockout(listOf("e1", "e2", "e3", "e4"), Random(1), 0), drawnAt = 1)
        val semis = t.matches.filter { it.round == 1 }.sortedBy { it.slot }
        // 1 contro 4 in alto, 3 contro 2 in basso.
        assertEquals(setOf("e1", "e4"), setOf(semis[0].a, semis[0].b))
        t = ClubTournaments.setResult(t, semis[0].id, "e1", "6-3 6-2")
        t = ClubTournaments.setResult(t, semis[1].id, "e3", "4-6 7-5 10-8")
        val final = t.matches.single { it.round == 2 }
        assertEquals("e1", final.a); assertEquals("e3", final.b)
        t = ClubTournaments.setResult(t, final.id, "e3", "6-4 6-4")
        assertEquals("e3", t.champion)
        // Corretto il risultato di una semifinale: la finale perde il suo.
        t = ClubTournaments.setResult(t, semis[1].id, "e2", "6-4 6-4")
        val f2 = t.matches.single { it.round == 2 }
        assertEquals("e2", f2.b); assertNull(f2.winner); assertNull(t.champion)
    }

    @Test
    fun groupsSnakeRoundRobinStandingsAndKnockout() {
        val ids = (1..8).map { "e$it" }
        val ms = ClubTournaments.groupsDraw(ids, 2)
        var t = ClubTournament("t", "Gironi", format = CTFormat.GROUPS, groups = 2, qualify = 2, matches = ms, drawnAt = 1)
        val groups = ClubTournaments.groupMembers(t)
        assertEquals(listOf(listOf("e1", "e4", "e5", "e8"), listOf("e2", "e3", "e6", "e7")).map { it.toSet() }, groups.map { it.toSet() })
        // Quattro per girone: sei partite, ognuno ne gioca tre.
        assertEquals(12, ms.size)
        assertTrue(groups.all { g -> g.all { e -> ms.count { it.a == e || it.b == e } == 3 } })
        // Vince sempre chi ha il numero più basso.
        for (m in t.matches) {
            val w = listOf(m.a!!, m.b!!).minBy { it.drop(1).toInt() }
            t = ClubTournaments.setResult(t, m.id, w, if (w == m.a) "6-1 6-1" else "1-6 1-6")
        }
        assertTrue(t.groupsDone)
        assertEquals(listOf("e1", "e4", "e5", "e8"), ClubTournaments.standings(t, 0).map { it.entry })
        val ko = ClubTournaments.knockoutFromGroups(t, Random(3), 0).filter { it.round == 1 }.sortedBy { it.slot }
        // A1 contro B2, B1 contro A2: quelli dello stesso girone si ritrovano solo in finale.
        assertEquals(setOf("e1", "e3"), setOf(ko[0].a, ko[0].b))
        assertEquals(setOf("e2", "e4"), setOf(ko[1].a, ko[1].b))
    }

    @Test
    fun scoreParsing() {
        assertEquals(listOf(6 to 4, 3 to 6, 10 to 8), ClubTournaments.sets("6-4 3-6 10-8"))
        assertEquals(listOf(7 to 6, 6 to 3), ClubTournaments.sets("7-6(5) 6/3"))
        assertEquals("b", ClubTournaments.winnerFromScore("4-6 6-3 8-10"))
        assertNull(ClubTournaments.winnerFromScore(""))
    }

    @Test
    fun entryRulesForFitpAndSocial() {
        val ok = Membership(fitpNumber = "1", fitpCard = FitpCard.AGONIST, fitpYear = 2026, ranking = "4.2", medical = MedicalKind.AGONISTIC, medicalUntil = day(2027, 3, 1))
        val p = PlayerProfile("p", "Rossi", "Mario", "Rossi", member = ok)
        val fitp = ClubTournament("t", "Open", kind = CTKind.FITP, rankLimit = "4.1", start = start)
        val book = ClubBook()
        assertEquals(emptyList<ClubTournaments.Problem>(), ClubTournaments.problems(fitp, p, book, 0))
        // Più forte del limite, certificato non agonistico, tessera non agonista.
        val strong = p.copy(member = ok.copy(ranking = "3.5", medical = MedicalKind.NON_AGONISTIC, fitpCard = FitpCard.NON_AGONIST))
        assertEquals(
            listOf(ClubTournaments.Problem.MEDICAL_NOT_AGONISTIC, ClubTournaments.Problem.NOT_AGONIST_CARD, ClubTournaments.Problem.RANK_TOO_HIGH),
            ClubTournaments.problems(fitp, strong, book, 0),
        )
        // Certificato scaduto prima dell'inizio del torneo.
        assertTrue(ClubTournaments.Problem.NO_MEDICAL in ClubTournaments.problems(fitp, p.copy(member = ok.copy(medicalUntil = day(2026, 11, 10))), book, 0))
        // Sociale: chi non è socio si segnala ma non blocca; senza certificato sì.
        val social = ClubTournament("s", "Sociale", start = start)
        val guest = PlayerProfile("g", "Ospite")
        val pr = ClubTournaments.problems(social, guest, book, 0)
        assertEquals(listOf(ClubTournaments.Problem.NOT_MEMBER, ClubTournaments.Problem.NO_MEDICAL), pr)
        assertFalse(ClubTournaments.blocking(ClubTournaments.Problem.NOT_MEMBER))
    }

    @Test
    fun entryFeeMustBePaid() {
        val p = PlayerProfile("p", "Rossi", member = Membership(medicalUntil = day(2027, 3, 1)))
        val fee = Fee("f", "Iscrizione Sociale", 1_500, FeeKind.TOURNAMENT, tournament = "t")
        val t = ClubTournament("t", "Sociale", start = start, feeCents = 1_500, feeId = "f", entries = listOf(CTEntry("e", listOf("p"), listOf("Rossi"))))
        var book = Fees.charge(ClubBook(fees = listOf(fee)), p, fee.name, fee.cents, 0, fee)
        assertEquals(listOf(ClubTournaments.Problem.UNPAID), ClubTournaments.problems(t, p, book, 0))
        assertTrue(ClubTournaments.eligible(t, listOf(p), book, 0).isEmpty())
        book = Fees.pay(book, book.charges.single().id, 1_500, 0, PayMethod.CASH, "")!!.first
        assertEquals(listOf("e"), ClubTournaments.eligible(t, listOf(p), book, 0).map { it.id })
        // Senza obbligo di pagamento entra lo stesso.
        assertTrue(ClubTournaments.problems(t.copy(requirePaid = false), p, ClubBook(), 0).isEmpty())
    }
}
