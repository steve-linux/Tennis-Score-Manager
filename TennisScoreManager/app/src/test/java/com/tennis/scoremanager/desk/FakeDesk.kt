// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.desk

import com.tennis.scoremanager.data.ClubBook
import com.tennis.scoremanager.ClubFeature
import com.tennis.scoremanager.data.BookingRules
import com.tennis.scoremanager.data.CTEntry
import com.tennis.scoremanager.data.ClubServerConf
import com.tennis.scoremanager.data.ClubServerError
import com.tennis.scoremanager.data.SrvAdmin
import com.tennis.scoremanager.data.CourtBlock
import com.tennis.scoremanager.data.CourtInfo
import com.tennis.scoremanager.data.CourtPlans
import com.tennis.scoremanager.data.Course
import com.tennis.scoremanager.data.Courses
import com.tennis.scoremanager.data.Ladder
import com.tennis.scoremanager.data.Fixture
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
    override var club = "TC Prova"
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
    var squads = emptyList<Team>()
    override fun teams() = squads
    override fun saveTeam(t: Team) { squads = squads.filter { it.id != t.id } + t }
    override fun deleteTeam(t: Team) { squads = squads.filter { it.id != t.id } }
    var noticed: String? = null
    override fun teamNotice(t: Team, f: Fixture, kind: String) { noticed = kind }
    override fun setClubName(name: String) { club = name }
    override fun saveCourtSettings(st: CourtSettings) { courtPlan = courtPlan.copy(settings = st) }
    override fun addCourt(info: CourtInfo): Boolean {
        if (pitches.any { it.equals(info.name, ignoreCase = true) }) return false
        pitches = pitches + info.name; saveCourtInfo(info); return true
    }
    override fun renameCourt(old: String, new: String): Boolean {
        if (pitches.any { it != old && it.equals(new, ignoreCase = true) }) return false
        pitches = pitches.map { if (it == old) new else it }
        courtPlan = courtPlan.copy(bookings = courtPlan.bookings.map { if (it.court == old) it.copy(court = new) else it },
            settings = courtPlan.settings.copy(courts = courtPlan.settings.courts.map { if (it.name == old) it.copy(name = new) else it }))
        return true
    }
    override fun saveCourtInfo(info: CourtInfo) { courtPlan = courtPlan.copy(settings = courtPlan.settings.copy(courts = courtPlan.settings.courts.filter { it.name != info.name } + info)) }
    override fun deleteCourt(name: String) { pitches = pitches - name; courtPlan = courtPlan.copy(bookings = courtPlan.bookings.filter { it.court != name }) }
    override fun moveCourt(name: String, up: Boolean) {
        val l = pitches.toMutableList(); val i = l.indexOf(name); val j = if (up) i - 1 else i + 1
        if (i >= 0 && j in l.indices) { l[i] = l[j].also { l[j] = l[i] }; pitches = l }
    }
    override fun savePayTypes(list: List<PayType>) { ledger = ledger.copy(payTypes = list) }
    var server = ClubServerConf()
    var remote = SrvAdmin(0, emptyList(), emptyList())
    var remoteDown = false
    var removed = mutableListOf<String>()
    override fun serverConf() = server
    override fun saveServerRules(rules: BookingRules, payInfo: String, alerts: Boolean) { server = server.copy(rules = rules, payInfo = payInfo, alerts = alerts) }
    override fun serverState(): SrvAdmin = if (remoteDown) throw ClubServerError("NET") else remote
    override fun serverSync(): String? = if (remoteDown) "NET" else { server = server.copy(lastSync = time); null }
    override fun serverRemove(kind: String, id: String) { removed += "$kind:$id" }
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
