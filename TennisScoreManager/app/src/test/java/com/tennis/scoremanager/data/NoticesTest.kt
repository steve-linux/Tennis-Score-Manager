// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import com.tennis.scoremanager.model.Lang
import com.tennis.scoremanager.ui.stringsFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class NoticesTest {
    private fun day(y: Int, m: Int, d: Int, h: Int = 12, min: Int = 0): Long = Calendar.getInstance().apply { set(y, m - 1, d, h, min, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
    private val now = day(2026, 11, 1)
    private val s = stringsFor(Lang.IT)

    @Test
    fun whatsAppNumbers() {
        assertEquals("393331234567", Notices.waNumber("333 123 4567"))
        assertEquals("393331234567", Notices.waNumber("+39 333-1234567"))
        assertEquals("393331234567", Notices.waNumber("0039 333 1234567"))
        assertEquals("41791234567", Notices.waNumber("+41 79 123 45 67"))
        assertEquals("39021234567", Notices.waNumber("02 1234567"))
        assertNull(Notices.waNumber("123"))
        assertNull(Notices.waNumber(""))
    }

    @Test
    fun minorsGetTheParentsPhone() {
        val kid = PlayerProfile("k", "Luca", "Luca", "Rossi", phone = "333 1111111", birth = day(2014, 5, 1),
            member = Membership(guardian = "Anna Rossi", guardianPhone = "333 2222222"))
        assertEquals("333 2222222", Notices.phoneOf(kid, now))
        val adult = kid.copy(birth = day(1990, 5, 1))
        assertEquals("333 1111111", Notices.phoneOf(adult, now))
        // Senza telefono suo vale quello del genitore.
        assertEquals("333 2222222", Notices.phoneOf(adult.copy(phone = ""), now))
        // Al genitore si scrive "un avviso per Luca".
        val t = ClubTournament("t", "Sociale")
        assertTrue(Notices.entryText(s, t, kid.copy(phone = ""), ClubBook(), "", Lang.IT, now).startsWith("Buongiorno, un avviso per Luca:"))
    }

    @Test
    fun entryAndDuesTexts() {
        val p = PlayerProfile("p", "Mario Rossi", "Mario", "Rossi")
        val fee = Fee("f", "Iscrizione Sociale", 1_000, FeeKind.TOURNAMENT)
        val t = ClubTournament("t", "Sociale", start = day(2026, 11, 14), feeCents = 1_000, feeId = "f")
        val book = Fees.charge(ClubBook(fees = listOf(fee)), p, fee.name, fee.cents, now, fee)
        val text = Notices.entryText(s, t, p, book, "TC Prova", Lang.IT, now)
        assertTrue(text, text.startsWith("Ciao Mario,\nl'iscrizione al torneo Sociale è confermata.\nsabato 14 novembre"))
        assertTrue(text, text.contains("Iscrizione da pagare: 10,00"))
        assertTrue(text, text.endsWith("\n\nTC Prova"))
        val dues = Notices.duesText(s, p, book.charges, "", Lang.IT, now)
        assertEquals("Ciao Mario,\nrisulta ancora da pagare:\n• Iscrizione Sociale: 10,00 €", dues)
    }

    @Test
    fun matchTextAndDay() {
        val p = PlayerProfile("p", "Mario Rossi", "Mario", "Rossi")
        val m = CTMatch("m", 2, 0, a = "e1", b = "e2", court = "Campo 2", at = day(2026, 11, 14, 9, 30))
        val t = ClubTournament("t", "Sociale")
        val text = Notices.matchText(s, t, m, "Finale", { if (it == "e1") "Rossi" else "Bianchi" }, p, "", Lang.IT, now)
        assertEquals("Ciao Mario,\nFinale di Sociale:\nRossi – Bianchi\nsabato 14 novembre, 09:30 · Campo 2", text)
        assertEquals(listOf(m), Notices.onDay(listOf(m, m.copy(id = "x", at = day(2026, 11, 15))), day(2026, 11, 14)))
        assertEquals("orario da definire", Notices.whenText(s, m.copy(at = null, court = ""), Lang.IT))
    }
}
