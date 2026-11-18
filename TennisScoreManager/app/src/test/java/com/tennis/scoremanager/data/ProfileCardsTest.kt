// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileCardsTest {
    private val luca = PlayerProfile(
        "p1", "Luca Verdi", "Luca", "Verdi", phone = "+39 333 1234567", email = "luca@example.org", birth = 1_000, hand = Hand.LEFT,
        note = "segreto", student = true, guardian = "Anna Verdi", guardianPhone = "3330000000", consentAt = 5,
        member = Membership(fitpNumber = "123", ranking = "4.2", medical = MedicalKind.AGONISTIC, medicalUntil = 9_000, number = "77"),
    )

    @Test fun onlyChosenDataTravelAndNeverNotesOrConsent() {
        assertEquals(setOf(ProfileField.CONTACTS, ProfileField.BIRTH, ProfileField.PLAY, ProfileField.GUARDIAN, ProfileField.FITP), ProfileCards.defaults(luca, false))
        assertTrue(ProfileField.MEDICAL in ProfileCards.available(luca, false))
        assertFalse(ProfileField.PHOTO in ProfileCards.available(luca, false))
        val card = ProfileCards.card(luca, setOf(ProfileField.CONTACTS), byteArrayOf(1, 2, 3), "Circolo A", 10)
        val text = ProfileCards.encode(card)
        assertTrue(text, "\"tsm-profile\"" in text)                       // il file si riconosce
        assertTrue("\"tsm-profile\"" in ProfileCards.encodeQr(card))
        assertFalse("segreto" in text)
        assertFalse("4.2" in text)
        assertFalse("consent" in text)
        assertNull(card.photo)   // la foto non era tra i dati scelti
        val back = ProfileCards.decode(text)!!
        assertEquals("Luca", back.profile.firstName)
        assertEquals("+39 333 1234567", back.profile.phone)
        assertNull(back.profile.birth)
        assertNull(ProfileCards.decode("""{"tsm":"tsm-course-card"}"""))
    }

    @Test fun qrHasNoPhotoAndStaysSmall() {
        val all = ProfileField.entries.toSet()
        val card = ProfileCards.card(luca, all, ByteArray(3000) { 7 }, "Circolo A", 10)
        assertNotNull(card.photo)
        val qr = ProfileCards.encodeQr(card)
        assertFalse("photo" in qr)
        assertTrue(qr.length.toString(), qr.length < 800)
        assertEquals(9_000L, ProfileCards.decode(qr)!!.profile.medicalUntil)
    }

    @Test fun mergeFillsOnlyWhatIsMissing() {
        val sp = ProfileCards.card(luca, ProfileField.entries.toSet(), null, "", 0).profile
        val old = PlayerProfile("x", "Luca Verdi", "Luca", "Verdi", phone = "999", note = "mia nota")
        assertEquals(old, ProfileCards.find(listOf(old), sp))
        val m = ProfileCards.merge(old, sp, "new", 1, asStudent = true)
        assertEquals("x", m.id)
        assertEquals("999", m.phone)                 // c'era già: non si tocca
        assertEquals("luca@example.org", m.email)    // mancava: arriva
        assertEquals("mia nota", m.note)
        assertTrue(m.student)
        assertNull(m.member)                         // non socio: tessera e certificato non si inventano un socio
        val club = ProfileCards.merge(null, sp, "new", 1, asMember = true)
        assertEquals("new", club.id)
        assertEquals("Luca Verdi", club.name)
        assertEquals("4.2", club.member!!.ranking)
        assertEquals("", club.member!!.number)       // il numero di tessera del vecchio circolo non passa
        // Per telefono, se il nome non c'è.
        val byPhone = PlayerProfile("y", "Luchino", phone = "333 1234567")
        assertEquals(byPhone, ProfileCards.find(listOf(byPhone), sp.copy(lastName = "Rossi")))
    }
}
