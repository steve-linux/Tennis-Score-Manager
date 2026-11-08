// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class MembersTest {
    private fun day(y: Int, m: Int, d: Int): Long = Calendar.getInstance().apply { set(y, m - 1, d, 12, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
    private val now = day(2026, 11, 1)

    private fun member(m: Membership, birth: Long? = day(1990, 5, 10), phone: String = "333 1234567") =
        PlayerProfile("p", "Rossi", "Mario", "Rossi", phone = phone, birth = birth, member = m)

    private val ok = Membership(fitpNumber = "123456", fitpYear = 2026, medicalUntil = day(2027, 6, 1), privacyAt = day(2026, 1, 10))

    @Test
    fun inOrderHasNoIssues() {
        assertEquals(emptyList<Members.Issue>(), Members.issues(member(ok), now))
        // Chi non è socio non ha niente da sistemare.
        assertEquals(emptyList<Members.Issue>(), Members.issues(member(ok).copy(member = null), now))
    }

    @Test
    fun medicalExpiredSoonAndMissing() {
        assertEquals(listOf(Members.Issue.MEDICAL_EXPIRED), Members.issues(member(ok.copy(medicalUntil = day(2026, 10, 31))), now))
        assertEquals(listOf(Members.Issue.MEDICAL_SOON), Members.issues(member(ok.copy(medicalUntil = day(2026, 11, 20))), now))
        // Vale fino a fine giornata della scadenza.
        assertTrue(Members.medicalValid(ok.copy(medicalUntil = day(2026, 11, 1)), day(2026, 11, 1) + 6 * 3_600_000L))
        assertEquals(listOf(Members.Issue.NO_MEDICAL), Members.issues(member(ok.copy(medicalUntil = null)), now))
    }

    @Test
    fun fitpOfAnotherYearPrivacyAndContact() {
        val i = Members.issues(member(ok.copy(fitpYear = 2025, privacyAt = null), phone = ""), now)
        assertEquals(listOf(Members.Issue.NO_PRIVACY, Members.Issue.NO_FITP, Members.Issue.NO_CONTACT), i)
        assertTrue(Members.blocking(Members.Issue.NO_FITP))
        assertFalse(Members.blocking(Members.Issue.NO_PRIVACY))
    }

    @Test
    fun minorsNeedTheParent() {
        val kid = member(ok, birth = day(2012, 3, 1), phone = "")
        assertEquals(14, Members.age(kid.birth, now))
        assertTrue(Members.issues(kid, now).contains(Members.Issue.NO_GUARDIAN))
        val withParent = kid.copy(member = ok.copy(guardian = "Anna Rossi", guardianPhone = "333 7654321"))
        // Il telefono del genitore vale come contatto.
        assertEquals(emptyList<Members.Issue>(), Members.issues(withParent, now))
        // Il compleanno non ancora arrivato quest'anno non conta.
        assertEquals(17, Members.age(day(2008, 12, 25), now))
    }

    @Test
    fun rankingValue() {
        assertEquals(2.8, Members.rankingValue("2.8")!!, 1e-9)
        assertEquals(4.1, Members.rankingValue("4,1")!!, 1e-9)
        assertEquals(4.9, Members.rankingValue("4.nc")!!, 1e-9)
        assertEquals(1.0, Members.rankingValue("1")!!, 1e-9)
        assertTrue(Members.rankingValue("2.8")!! < Members.rankingValue("3.1")!!)
        assertNull(Members.rankingValue(""))
        assertNull(Members.rankingValue("forte"))
    }
}
