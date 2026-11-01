// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Locale

class ClubBookTest {
    private fun day(y: Int, m: Int, d: Int): Long = Calendar.getInstance().apply { set(y, m - 1, d, 12, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
    private val now = day(2026, 11, 1)
    private val rossi = PlayerProfile("a", "Rossi", "Mario", "Rossi", member = Membership())
    private val bianchi = PlayerProfile("b", "Bianchi", "Anna", "Bianchi", member = Membership(), aliases = setOf("b-old"))
    private val fee = Fee("f1", "Quota sociale 2027", 15_000, FeeKind.MEMBERSHIP, due = day(2027, 1, 31))

    @Test
    fun chargeAllSkipsWhoAlreadyHasIt() {
        val (b1, n1) = Fees.chargeAll(ClubBook(fees = listOf(fee)), fee, listOf(rossi), now)
        assertEquals(1, n1)
        val (b2, n2) = Fees.chargeAll(b1, fee, listOf(rossi, bianchi), now)
        assertEquals(1, n2)
        assertEquals(2, b2.charges.size)
        assertEquals(15_000, Fees.owed(b2, bianchi))
        assertEquals(30_000 to 2, Fees.open(b2))
    }

    @Test
    fun paymentsReceiptsAndStatus() {
        var b = Fees.chargeAll(ClubBook(), fee, listOf(rossi), now).first
        val id = b.charges.single().id
        assertEquals(Fees.Status.UNPAID, Fees.status(b.charges.single(), now))
        val (b1, p1) = Fees.pay(b, id, 5_000, day(2026, 12, 20), PayMethod.CASH, "")!!
        assertEquals("1/2026", p1.receipt)
        assertEquals(Fees.Status.PARTIAL, Fees.status(b1.charges.single(), now))
        assertEquals(10_000, b1.charges.single().left)
        // A gennaio la numerazione riparte.
        val (b2, p2) = Fees.pay(b1, id, 10_000, day(2027, 1, 5), PayMethod.TRANSFER, " CRO 123 ")!!
        assertEquals("1/2027", p2.receipt)
        assertEquals("CRO 123", p2.ref)
        assertEquals(Fees.Status.PAID, Fees.status(b2.charges.single(), now))
        assertEquals(5_000, Fees.income(b2, 2026))
        assertEquals(10_000, Fees.income(b2, 2027))
        assertEquals(1 to 1, Fees.feeProgress(b2, fee))
        // Importo zero o addebito che non c'è: niente.
        assertNull(Fees.pay(b2, id, 0, now, PayMethod.CASH, ""))
        assertNull(Fees.pay(b2, "x", 100, now, PayMethod.CASH, ""))
        // Tolto un pagamento, il numero non torna indietro.
        b = Fees.unpay(b2, id, p2.id)
        assertEquals(10_000, b.charges.single().left)
        assertEquals("2/2027", Fees.pay(b, id, 100, day(2027, 2, 1), PayMethod.POS, "")!!.second.receipt)
    }

    @Test
    fun lateAndCancelled() {
        val b = Fees.charge(ClubBook(), rossi, "Corso", 8_000, now, fee.copy(due = day(2026, 10, 15)))
        val c = b.charges.single()
        assertEquals(Fees.Status.LATE, Fees.status(c, now))
        // Senza pagamenti si cancella, con pagamenti si annulla (storno) e non si deve più.
        assertTrue(Fees.cancel(b, c.id).charges.isEmpty())
        val paid = Fees.pay(b, c.id, 1_000, now, PayMethod.CASH, "")!!.first
        val cancelled = Fees.cancel(paid, c.id).charges.single()
        assertEquals(Fees.Status.CANCELLED, Fees.status(cancelled, now))
        assertEquals(0, cancelled.left)
    }

    @Test
    fun forgetAndRemap() {
        var b = Fees.chargeAll(ClubBook(), fee, listOf(rossi, bianchi), now).first
        val rid = b.charges.first { it.player == "a" }.id
        b = Fees.pay(b, rid, 15_000, now, PayMethod.CASH, "")!!.first
        // Rossi cancellato: il pagamento resta col nome, l'addebito di Bianchi non c'entra.
        val f = Fees.forget(b, setOf("a"))
        assertEquals(listOf("", "b"), f.charges.map { it.player }.sorted())
        assertEquals("Rossi", f.charges.first { it.player == "" }.name)
        // Senza pagamenti, l'addebito sparisce.
        assertEquals(1, Fees.forget(b, setOf("b")).charges.size)
        // Uniti: gli addebiti del vecchio identificativo passano a chi resta.
        val old = b.copy(charges = b.charges.map { if (it.player == "b") it.copy(player = "b-old") else it })
        assertEquals(15_000, Fees.owed(old, bianchi))
        assertEquals(setOf("a"), Fees.remap(b, setOf("b"), "a").charges.map { it.player }.toSet())
    }

    @Test
    fun parseAndMoney() {
        assertEquals(15_000, Fees.parse("150"))
        assertEquals(15_050, Fees.parse("150,5"))
        assertEquals(15_050, Fees.parse("150.50 €"))
        assertEquals(123_456, Fees.parse("1.234,56"))
        assertEquals(123_456, Fees.parse("1,234.56"))
        assertNull(Fees.parse("abc"))
        assertNull(Fees.parse("1,234"))
        assertNull(Fees.parse(""))
        assertTrue(Fees.money(15_050, Locale.ITALY).contains("150,50"))
    }

    @Test
    fun csvForTheAccountant() {
        var b = Fees.charge(ClubBook(), rossi, "Quota; sociale", 15_050, now)
        b = Fees.pay(b, b.charges.single().id, 15_050, now, PayMethod.TRANSFER, "CRO 1")!!.first
        val csv = Fees.csv(b, 2026, "Data;Ricevuta;Nome;Descrizione;Metodo;Riferimento;Importo", { it.name }, { "01/11/2026" }, Locale.ITALY)
        assertEquals("Data;Ricevuta;Nome;Descrizione;Metodo;Riferimento;Importo\r\n01/11/2026;1/2026;Rossi;\"Quota; sociale\";TRANSFER;CRO 1;150,50\r\n", csv)
    }
}
