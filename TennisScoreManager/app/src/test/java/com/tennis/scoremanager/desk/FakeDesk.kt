// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.desk

import com.tennis.scoremanager.data.ClubBook
import com.tennis.scoremanager.data.ClubTournament
import com.tennis.scoremanager.data.CourtBooking
import com.tennis.scoremanager.data.CourtPlan
import com.tennis.scoremanager.data.CourtSettings
import com.tennis.scoremanager.data.Fees
import com.tennis.scoremanager.data.PayType
import com.tennis.scoremanager.data.PlayerProfile

/** I dati del circolo in memoria, al posto dell'app. */
class FakeDesk(var time: Long = System.currentTimeMillis()) : DeskData {
    var list = emptyList<PlayerProfile>()
    var ledger = ClubBook()
    var courtPlan = CourtPlan(CourtSettings(centsPerHour = 1200, centsPerHourLights = 1600))
    var cups = emptyList<ClubTournament>()
    override fun <T> tx(f: () -> T): T = synchronized(this) { f() }
    override val club = "TC Prova"
    override var lang = "it"
    override fun now() = time
    override fun players() = list
    override fun savePlayers(list: List<PlayerProfile>) { val ids = list.map { it.id }.toSet(); this.list = this.list.filter { it.id !in ids } + list }
    override fun book() = ledger
    override fun saveBook(b: ClubBook) { ledger = b }
    override fun payTypes() = Fees.types(ledger) { it.name }
    override fun courts() = listOf("Campo 1", "Campo 2")
    override fun plan() = courtPlan
    override fun tournaments() = cups
    override fun saveBooking(b: CourtBooking, charge: Boolean) { courtPlan = courtPlan.copy(bookings = courtPlan.bookings + b) }
    override fun deleteBooking(b: CourtBooking) { courtPlan = courtPlan.copy(bookings = courtPlan.bookings - b) }
    override fun paymentsCsv(year: Int): ByteArray? = "anno;$year\n".toByteArray()
}
