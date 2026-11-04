// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.desk

import com.tennis.scoremanager.data.ClubBook
import com.tennis.scoremanager.ClubFeature
import com.tennis.scoremanager.data.CTEntry
import com.tennis.scoremanager.data.CourtBlock
import com.tennis.scoremanager.data.CourtPlans
import com.tennis.scoremanager.data.Course
import com.tennis.scoremanager.data.Courses
import com.tennis.scoremanager.data.Ladder
import com.tennis.scoremanager.data.Team
import com.tennis.scoremanager.data.ClubTournament
import com.tennis.scoremanager.data.ClubTournaments
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
    var pitches = listOf("Campo 1", "Campo 2")
    override fun courts() = pitches
    override fun plan() = courtPlan
    override fun tournaments() = cups
    override fun saveBooking(b: CourtBooking, weeks: Int, charge: Boolean): List<Long> {
        val (list, skipped) = if (weeks > 1) CourtPlans.weekly(courtPlan, cups, b, weeks) else listOf(b) to emptyList()
        courtPlan = courtPlan.copy(bookings = courtPlan.bookings.filter { o -> list.none { it.id == o.id } } + list)
        return skipped
    }
    override fun deleteBooking(b: CourtBooking, series: Boolean) {
        val gone = if (series && b.series != null) courtPlan.bookings.filter { it.series == b.series && it.start >= b.start } else listOf(b)
        courtPlan = courtPlan.copy(bookings = courtPlan.bookings - gone.toSet())
    }
    override fun saveBlock(b: CourtBlock, delete: Boolean) {
        courtPlan = courtPlan.copy(blocks = courtPlan.blocks.filter { it.id != b.id } + listOfNotNull(b.takeIf { !delete }))
    }
    var on = ClubFeature.entries.filter { it.base }.toSet()
    var school = emptyList<Course>()
    override fun features() = on
    override fun courses() = school
    override fun teams() = emptyList<Team>()
    override fun ladders() = emptyList<Ladder>()
    override fun saveCourse(c: Course) { school = school.filter { it.id != c.id } + c }
    override fun deleteCourse(c: Course) { school = school.filter { it.id != c.id } }
    override fun courseFeeLabel(c: Course, month: Pair<Int, Int>?): String = c.name + " · " + (month?.let { "%04d-%02d".format(it.first, it.second + 1) } ?: "stagione")
    override fun chargeCourse(c: Course, month: Pair<Int, Int>?): Int {
        val (b, n) = Courses.charge(ledger, c, list, courseFeeLabel(c, month), time)
        ledger = b
        return n
    }
    var sent: Set<String>? = null
    override fun dues(charges: Set<String>): List<DeskDue> = list.mapNotNull { p ->
        val open = Fees.of(ledger, p).filter { it.left > 0 && it.id in charges }
        if (open.isEmpty()) null else DeskDue(p.id, p.name, p.phone, p.email, open.joinToString("\n") { it.label })
    }
    override fun sendDues(charges: Set<String>) { sent = charges }
    override fun receiptPdf(chargeId: String, paymentId: String): ByteArray? =
        ledger.charges.firstOrNull { it.id == chargeId }?.payments?.firstOrNull { it.id == paymentId }?.let { "%PDF-${it.receipt}".toByteArray() }
    override fun paymentsCsv(year: Int): ByteArray? = "anno;$year\n".toByteArray()
    override fun saveTournament(t: ClubTournament) { cups = cups.filter { it.id != t.id } + t }
    override fun deleteTournament(t: ClubTournament) { cups = cups - t }
    override fun addEntry(t: ClubTournament, people: List<PlayerProfile>) =
        saveTournament(t.copy(entries = t.entries + CTEntry(ClubTournaments.newId("e"), people.map { it.id }, people.map { it.name }, at = time)))
    override fun removeEntry(t: ClubTournament, entry: CTEntry) = saveTournament(t.copy(entries = t.entries - entry))
}
