// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class TeamsTest {
    private fun at(y: Int, m: Int, d: Int, h: Int = 12, min: Int = 0): Long = Calendar.getInstance().apply { set(y, m - 1, d, h, min, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
    private val team = Team("t", "TC Prova D1", "Serie D1 maschile", "2027", roster = listOf("a", "b", "c", "d", "e"), singles = 4, doubles = 2)

    @Test
    fun resultAndOutcome() {
        var f = Fixture("f", "TC Altro", at(2027, 4, 11, 9), rubbers = Teams.emptyRubbers(team))
        assertEquals(6, f.rubbers.size)
        assertEquals(listOf(false, false, false, false, true, true), f.rubbers.map { it.doubles })
        assertFalse(Teams.decided(f))
        // Il vincitore dal punteggio, dal nostro lato.
        f = Teams.setRubber(f, 0, "6-4 6-3", null)
        f = Teams.setRubber(f, 1, "3-6 4-6", null)
        f = Teams.setRubber(f, 2, "6-1 6-1", null)
        assertEquals(2 to 1, Teams.result(f))
        assertNull(Teams.outcome(f))
        f = Teams.setRubber(f, 3, "7-5 6-4", null)
        assertFalse(Teams.decided(f))
        f = Teams.setRubber(f, 4, "6-3 6-3", null)
        // 4 su 6: incontro vinto anche senza giocare l'ultimo doppio.
        assertTrue(Teams.decided(f))
        assertEquals(true, Teams.outcome(f))
        assertEquals(Teams.Record(1, 0, 0), Teams.record(team.copy(fixtures = listOf(f))))
        // Pareggio 3-3: deciso, ma né vinto né perso.
        var g = Fixture("g", "TC Terzo", at(2027, 4, 18, 9), rubbers = Teams.emptyRubbers(team))
        listOf(true, true, true, false, false, false).forEachIndexed { i, w -> g = Teams.setRubber(g, i, "", w) }
        assertTrue(Teams.decided(g)); assertNull(Teams.outcome(g))
        assertEquals(Teams.Record(1, 0, 1), Teams.record(team.copy(fixtures = listOf(f, g))))
    }

    @Test
    fun availabilityAndCandidates() {
        val f = Fixture("f", "TC Altro", at(2027, 4, 11, 9), availability = mapOf("c" to Availability.YES, "a" to Availability.MAYBE, "b" to Availability.YES, "d" to Availability.NO))
        // Prima chi ha detto sì (nell'ordine della rosa), poi i forse.
        assertEquals(listOf("b", "c", "a"), Teams.candidates(team, f))
        assertEquals(listOf("e"), Teams.noAnswer(team, f))
    }

    @Test
    fun fitpEligibilityOnTheMatchDay() {
        val ok = Membership(fitpNumber = "1", fitpCard = FitpCard.AGONIST, fitpYear = 2027, medical = MedicalKind.AGONISTIC, medicalUntil = at(2027, 6, 1))
        val p = PlayerProfile("a", "Rossi", member = ok)
        val day = at(2027, 4, 11)
        assertTrue(Teams.problems(team, p, day).isEmpty())
        assertEquals(listOf(ClubTournaments.Problem.NO_MEDICAL), Teams.problems(team, p.copy(member = ok.copy(medicalUntil = at(2027, 4, 1))), day))
        // Non FITP: basta il certificato.
        assertTrue(Teams.problems(team.copy(fitp = false), p.copy(member = ok.copy(fitpYear = 2026)), day).isEmpty())
    }

    @Test
    fun homeFixturesBlockTheirCourts() {
        val home = Fixture("f", "TC Altro", at(2027, 4, 11, 9), home = true, courts = listOf("Campo 1", "Campo 2"), hours = 5)
        val away = Fixture("g", "TC Terzo", at(2027, 4, 18, 9), home = false, courts = listOf("Campo 1"))
        val blocks = Teams.blocks(team.copy(fixtures = listOf(home, away)))
        assertEquals(1, blocks.size)
        val plan = CourtPlan(blocks = blocks)
        assertEquals(1, CourtPlans.blockedBy(plan, "Campo 2", at(2027, 4, 11, 13), at(2027, 4, 11, 14), BookingKind.LESSON).size)
        assertTrue(CourtPlans.blockedBy(plan, "Campo 2", at(2027, 4, 11, 14), at(2027, 4, 11, 15), BookingKind.PLAY).isEmpty())
        assertTrue(CourtPlans.blockedBy(plan, "Campo 3", at(2027, 4, 11, 10), at(2027, 4, 11, 11), BookingKind.PLAY).isEmpty())
    }
}
