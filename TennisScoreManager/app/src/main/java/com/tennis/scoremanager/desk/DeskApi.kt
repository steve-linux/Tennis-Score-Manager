// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.desk

import com.tennis.scoremanager.data.BookingKind
import com.tennis.scoremanager.data.Charge
import com.tennis.scoremanager.data.ClubBook
import com.tennis.scoremanager.data.ClubTournament
import com.tennis.scoremanager.data.CourtBooking
import com.tennis.scoremanager.data.CourtPlan
import com.tennis.scoremanager.data.CourtPlans
import com.tennis.scoremanager.data.Fee
import com.tennis.scoremanager.data.FeeKind
import com.tennis.scoremanager.data.Fees
import com.tennis.scoremanager.data.FitpCard
import com.tennis.scoremanager.data.MedicalKind
import com.tennis.scoremanager.data.Members
import com.tennis.scoremanager.data.Membership
import com.tennis.scoremanager.data.PayType
import com.tennis.scoremanager.data.PlayerProfile
import com.tennis.scoremanager.data.Players
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Quello che la Segreteria da PC legge e cambia: i dati restano nell'app, sul telefono. */
interface DeskData {
    /** Esegue [f] da solo rispetto al resto dell'app, così telefono e PC non si pestano i piedi. */
    fun <T> tx(f: () -> T): T
    val club: String
    val lang: String
    fun now(): Long
    fun players(): List<PlayerProfile>
    fun savePlayers(list: List<PlayerProfile>)
    fun book(): ClubBook
    fun saveBook(b: ClubBook)
    fun payTypes(): List<PayType>
    fun courts(): List<String>
    fun plan(): CourtPlan
    fun tournaments(): List<ClubTournament>
    /** Salva una prenotazione nuova; con [charge] divide il prezzo tra i giocatori e lo addebita. */
    fun saveBooking(b: CourtBooking, charge: Boolean)
    fun deleteBooking(b: CourtBooking)
    fun paymentsCsv(year: Int): ByteArray?
}

@Serializable data class DeskError(val error: String)
@Serializable data class DeskCodeIn(val code: String)
@Serializable data class DeskPairOut(val state: String, val id: String? = null, val token: String? = null)
/** Il QR da disegnare: una stringa di 0 e 1 per riga. */
@Serializable data class DeskQrOut(val id: String, val rows: List<String>)
@Serializable data class DeskInfo(val club: String, val lang: String, val today: String, val year: Int)

@Serializable data class DeskPerson(
    val id: String = "", val name: String = "", val firstName: String = "", val lastName: String = "", val nickname: String = "",
    val phone: String = "", val email: String = "", val birth: String? = null, val sex: String = "", val note: String = "",
    val member: Boolean = false, val since: String? = null, val number: String = "", val fitpNumber: String = "", val fitpCard: FitpCard? = null, val fitpYear: Int? = null,
    val ranking: String = "", val medical: MedicalKind? = null, val medicalUntil: String? = null, val privacy: String? = null, val photosOk: Boolean = false,
    val guardian: String = "", val guardianPhone: String = "",
    /** Solo in uscita: cosa manca e quanto deve. */
    val issues: List<String> = emptyList(), val owedCents: Int = 0,
)

@Serializable data class DeskCsvIn(val text: String)
@Serializable data class DeskCsvRow(val line: Int, val firstName: String, val lastName: String, val phone: String, val email: String, val birth: String?, val fitp: String, val update: Boolean, val error: String?)
@Serializable data class DeskCsvOut(val rows: List<DeskCsvRow>, val added: Int = 0, val updated: Int = 0, val done: Boolean = false)

@Serializable data class DeskCharge(
    val id: String, val player: String, val name: String, val label: String, val cents: Int, val paidCents: Int, val leftCents: Int, val due: String?, val status: String,
    val payments: List<DeskPayment>,
)
@Serializable data class DeskPayment(val id: String, val cents: Int, val date: String, val method: String, val ref: String, val receipt: String)
@Serializable data class DeskFee(val id: String, val name: String, val cents: Int, val kind: FeeKind, val due: String?, val paid: Int, val total: Int)
@Serializable data class DeskPayType(val id: String, val name: String)
@Serializable data class DeskMoney(val year: Int, val incomeCents: Int, val openCents: Int, val openPeople: Int, val fees: List<DeskFee>, val charges: List<DeskCharge>, val payTypes: List<DeskPayType>)
@Serializable data class DeskFeeIn(val name: String, val cents: Int, val kind: FeeKind = FeeKind.OTHER, val due: String? = null, val chargeMembers: Boolean = false)
@Serializable data class DeskChargeIn(val player: String, val label: String, val cents: Int, val due: String? = null)
@Serializable data class DeskPayIn(val cents: Int, val date: String, val type: String, val ref: String = "")
@Serializable data class DeskCount(val count: Int)

@Serializable data class DeskItem(
    val id: String, val court: String, val startMin: Int, val endMin: Int, val type: String, val label: String, val note: String = "",
    val kind: BookingKind? = null, val cents: Int = 0, val online: Boolean = false,
)
@Serializable data class DeskDay(val date: String, val openHour: Int, val closeHour: Int, val slotMinutes: Int, val courts: List<String>, val items: List<DeskItem>)
@Serializable data class DeskBookingIn(
    val court: String, val date: String, val startMin: Int, val minutes: Int, val kind: BookingKind = BookingKind.PLAY,
    val players: List<String> = emptyList(), val names: List<String> = emptyList(), val note: String = "", val charge: Boolean = false,
)
@Serializable data class DeskQuote(val cents: Int, val lights: Boolean)

@Serializable data class DeskTournament(val id: String, val name: String, val category: String, val doubles: Boolean, val start: String?, val end: String?, val entries: List<String>, val matches: Int, val played: Int)

/**
 * Le chiamate della Segreteria da PC. Tutto tranne l'abbinamento chiede la chiave della sessione, in
 * un'intestazione Authorization: i siti esterni non possono aggiungerla, e la pagina non usa cookie.
 */
class DeskApi(
    private val data: DeskData, private val auth: DeskAuth, private val zone: () -> ZoneId = ZoneId::systemDefault,
    /** Il testo come QR, riga per riga. */
    private val qr: (String) -> List<String> = { listOf(it) },
) {

    data class Req(val method: String, val path: String, val query: Map<String, String> = emptyMap(), val body: String = "", val token: String? = null, val ip: String = "")
    class Resp(val status: Int, val body: ByteArray, val type: String = JSON)
    private class Fail(val status: Int, val code: String) : Exception(code)

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

    private fun <T> ok(ser: KSerializer<T>, v: T) = Resp(200, json.encodeToString(ser, v).toByteArray())
    private fun done() = Resp(200, "{}".toByteArray())
    private fun <T> read(ser: KSerializer<T>, r: Req): T = try { json.decodeFromString(ser, r.body) } catch (_: Exception) { throw Fail(400, "BAD_REQUEST") }

    fun handle(r: Req): Resp = try {
        route(r)
    } catch (e: Fail) {
        Resp(e.status, json.encodeToString(DeskError.serializer(), DeskError(e.code)).toByteArray())
    } catch (_: Exception) {
        // Nessun dettaglio verso il browser: né dati, né percorsi.
        Resp(500, json.encodeToString(DeskError.serializer(), DeskError("SERVER")).toByteArray())
    }

    private fun route(r: Req): Resp {
        val p = r.path.trim('/').split('/').filter { it.isNotEmpty() }
        val m = r.method
        // Prima di entrare.
        if (m == "POST" && p == listOf("pair", "qr")) return pairQr(r.ip)
        if (m == "POST" && p == listOf("pair")) return pair(r)
        if (m == "GET" && p.size == 2 && p[0] == "pair") return pairState(p[1], r.ip)
        if (!auth.check(r.token, r.ip)) throw Fail(401, "UNAUTHORIZED")
        if (m == "POST" && p == listOf("logout")) { auth.logout(); return done() }
        return data.tx {
            when {
                m == "GET" && p == listOf("info") -> info()
                m == "GET" && p == listOf("people") -> people()
                m == "POST" && p == listOf("people") -> savePerson(read(DeskPerson.serializer(), r), null)
                m == "PUT" && p.size == 2 && p[0] == "people" -> savePerson(read(DeskPerson.serializer(), r), p[1])
                m == "POST" && p == listOf("people", "import", "preview") -> importCsv(read(DeskCsvIn.serializer(), r), false)
                m == "POST" && p == listOf("people", "import") -> importCsv(read(DeskCsvIn.serializer(), r), true)
                m == "GET" && p == listOf("money") -> money(r.query["year"]?.toIntOrNull())
                m == "GET" && p == listOf("payments.csv") -> paymentsCsv(r.query["year"]?.toIntOrNull())
                m == "POST" && p == listOf("fees") -> addFee(read(DeskFeeIn.serializer(), r))
                m == "POST" && p == listOf("charges") -> addCharge(read(DeskChargeIn.serializer(), r))
                m == "POST" && p.size == 3 && p[0] == "charges" && p[2] == "pay" -> pay(p[1], read(DeskPayIn.serializer(), r))
                m == "POST" && p.size == 3 && p[0] == "charges" && p[2] == "cancel" -> { data.saveBook(Fees.cancel(data.book(), charge(p[1]).id)); done() }
                m == "DELETE" && p.size == 4 && p[0] == "charges" && p[2] == "payments" -> { data.saveBook(Fees.unpay(data.book(), charge(p[1]).id, p[3])); done() }
                m == "GET" && p == listOf("day") -> day(r.query["date"])
                m == "GET" && p == listOf("quote") -> quote(r.query)
                m == "POST" && p == listOf("bookings") -> book(read(DeskBookingIn.serializer(), r))
                m == "DELETE" && p.size == 2 && p[0] == "bookings" -> cancelBooking(p[1])
                m == "GET" && p == listOf("tournaments") -> tournaments()
                else -> throw Fail(404, "NOT_FOUND")
            }
        }
    }

    // --- Abbinamento ---

    private fun pair(r: Req): Resp = when (val x = auth.submit(read(DeskCodeIn.serializer(), r).code, r.ip)) {
        is DeskAuth.Result.Waiting -> ok(DeskPairOut.serializer(), DeskPairOut("WAIT", id = x.id))
        DeskAuth.Result.Wrong -> throw Fail(403, "BAD_CODE")
        DeskAuth.Result.Blocked -> throw Fail(429, "TOO_MANY_TRIES")
        DeskAuth.Result.Busy -> throw Fail(423, "BUSY")
    }

    private fun pairQr(ip: String): Resp {
        val q = auth.qr(ip) ?: throw Fail(423, "BUSY")
        return ok(DeskQrOut.serializer(), DeskQrOut(q.id, qr(q.text)))
    }

    private fun pairState(id: String, ip: String): Resp = when (val x = auth.poll(id, ip)) {
        DeskAuth.Poll.Wait -> ok(DeskPairOut.serializer(), DeskPairOut("WAIT"))
        DeskAuth.Poll.Confirm -> ok(DeskPairOut.serializer(), DeskPairOut("CONFIRM"))
        DeskAuth.Poll.Refused -> throw Fail(403, "REFUSED")
        is DeskAuth.Poll.Granted -> ok(DeskPairOut.serializer(), DeskPairOut("OK", token = x.token))
    }

    // --- Date ---

    private fun day(t: Long?): String? = t?.let { Instant.ofEpochMilli(it).atZone(zone()).toLocalDate().toString() }

    /** Una data del modulo, a mezzogiorno come quelle dell'app. */
    private fun noon(s: String?): Long? {
        if (s.isNullOrBlank()) return null
        return runCatching { LocalDate.parse(s).atTime(12, 0).atZone(zone()).toInstant().toEpochMilli() }.getOrElse { throw Fail(400, "BAD_DATE") }
    }

    private fun cents(c: Int): Int = if (c in 1..MAX_CENTS) c else throw Fail(400, "BAD_AMOUNT")
    private fun text(s: String, max: Int): String = s.trim().take(max)

    private fun info(): Resp {
        val now = data.now()
        return ok(DeskInfo.serializer(), DeskInfo(data.club, data.lang, day(now)!!, Instant.ofEpochMilli(now).atZone(zone()).year))
    }

    // --- Giocatori e soci ---

    private fun person(p: PlayerProfile, book: ClubBook, now: Long): DeskPerson {
        val m = p.member
        return DeskPerson(
            p.id, p.name, p.firstName, p.lastName, p.nickname, p.phone, p.email, day(p.birth), p.sex, p.note,
            m != null, day(m?.since), m?.number.orEmpty(), m?.fitpNumber.orEmpty(), m?.fitpCard, m?.fitpYear, m?.ranking.orEmpty(), m?.medical, day(m?.medicalUntil), day(m?.privacyAt),
            m?.photosOk == true, m?.guardian.orEmpty(), m?.guardianPhone.orEmpty(),
            Members.issues(p, now).map { it.name }, Fees.owed(book, p),
        )
    }

    private fun people(): Resp {
        val now = data.now(); val book = data.book()
        return ok(ListSerializer(DeskPerson.serializer()), data.players().sortedBy { it.name.lowercase() }.map { person(it, book, now) })
    }

    private fun savePerson(i: DeskPerson, id: String?): Resp {
        val first = text(i.firstName, 80); val last = text(i.lastName, 80); val nick = text(i.nickname, 40)
        if (first.isEmpty() || last.isEmpty()) throw Fail(400, "NAME_REQUIRED")
        val now = data.now()
        val old = id?.let { x -> Players.byId(data.players(), x) ?: throw Fail(404, "NOT_FOUND") }
        val oldM = old?.member
        val m = if (!i.member) null else (oldM ?: Membership()).copy(
            since = noon(i.since) ?: oldM?.since ?: now, number = text(i.number, 40), fitpNumber = text(i.fitpNumber, 40), fitpCard = i.fitpCard, fitpYear = i.fitpYear,
            ranking = text(i.ranking, 12), medical = i.medical, medicalUntil = noon(i.medicalUntil), privacyAt = noon(i.privacy), photosOk = i.photosOk,
            guardian = text(i.guardian, 120), guardianPhone = text(i.guardianPhone, 40),
        )
        // Quello che il PC non mostra (racchetta, foto, alias, statistiche) resta com'era.
        val base = old ?: PlayerProfile(Players.newId(), "", createdAt = now)
        val p = base.copy(
            name = PlayerProfile.display(first, last, nick), firstName = first, lastName = last, nickname = nick,
            phone = text(i.phone, 40), email = text(i.email, 120), birth = noon(i.birth), note = text(i.note, 2000), member = m,
        )
        data.savePlayers(listOf(p))
        return ok(DeskPerson.serializer(), person(p, data.book(), now))
    }

    private fun importCsv(i: DeskCsvIn, commit: Boolean): Resp {
        val players = data.players()
        val rows = MembersCsv.parse(i.text, players) ?: throw Fail(400, "CSV_NO_COLUMNS")
        val out = rows.map { DeskCsvRow(it.line, it.first, it.last, it.phone, it.email, day(it.birth), it.fitp, it.match != null, it.error) }
        if (!commit) return ok(DeskCsvOut.serializer(), DeskCsvOut(out))
        val saved = MembersCsv.apply(rows, players, data.now())
        data.savePlayers(saved)
        val known = players.map { it.id }.toSet()
        return ok(DeskCsvOut.serializer(), DeskCsvOut(out, saved.count { it.id !in known }, saved.count { it.id in known }, true))
    }

    // --- Quote e pagamenti ---

    private fun charge(id: String): Charge = data.book().charges.firstOrNull { it.id == id } ?: throw Fail(404, "NOT_FOUND")

    private fun money(year: Int?): Resp {
        val now = data.now(); val book = data.book()
        val y = year ?: Instant.ofEpochMilli(now).atZone(zone()).year
        val (openC, openP) = Fees.open(book)
        val fees = book.fees.map { f -> val (a, b) = Fees.feeProgress(book, f); DeskFee(f.id, f.name, f.cents, f.kind, day(f.due), a, b) }
        val charges = book.charges.sortedWith(compareBy<Charge> { it.left == 0 }.thenByDescending { it.createdAt }).map { c ->
            DeskCharge(
                c.id, c.player, c.name, c.label, c.cents, c.paid, c.left, day(c.due), Fees.status(c, now).name,
                c.payments.map { DeskPayment(it.id, it.cents, day(it.at)!!, it.methodName.ifEmpty { it.method.name }, it.ref, it.receipt) },
            )
        }
        return ok(DeskMoney.serializer(), DeskMoney(y, Fees.income(book, y), openC, openP, fees, charges, data.payTypes().map { DeskPayType(it.id, it.name) }))
    }

    private fun paymentsCsv(year: Int?): Resp {
        val y = year ?: throw Fail(400, "BAD_REQUEST")
        return Resp(200, data.paymentsCsv(y) ?: throw Fail(404, "NOT_FOUND"), "text/csv; charset=utf-8")
    }

    private fun addFee(i: DeskFeeIn): Resp {
        val name = text(i.name, 120)
        if (name.isEmpty()) throw Fail(400, "NAME_REQUIRED")
        val now = data.now()
        val fee = Fee(Fees.newId("f"), name, cents(i.cents), i.kind, noon(i.due), now)
        var book = data.book().let { it.copy(fees = it.fees + fee) }
        var count = 0
        if (i.chargeMembers) {
            val (b, n) = Fees.chargeAll(book, fee, data.players().filter { it.member != null }, now)
            book = b; count = n
        }
        data.saveBook(book)
        return ok(DeskCount.serializer(), DeskCount(count))
    }

    private fun addCharge(i: DeskChargeIn): Resp {
        val p = Players.byId(data.players(), i.player) ?: throw Fail(404, "NOT_FOUND")
        val label = text(i.label, 120)
        if (label.isEmpty()) throw Fail(400, "NAME_REQUIRED")
        val book = Fees.charge(data.book(), p, label, cents(i.cents), data.now())
        val made = book.charges.last().copy(due = noon(i.due))
        data.saveBook(book.copy(charges = book.charges.dropLast(1) + made))
        return done()
    }

    private fun pay(id: String, i: DeskPayIn): Resp {
        val c = charge(id)
        if (cents(i.cents) > c.left) throw Fail(400, "BAD_AMOUNT")
        val type = data.payTypes().firstOrNull { it.id == i.type } ?: throw Fail(400, "BAD_TYPE")
        val at = noon(i.date) ?: throw Fail(400, "BAD_DATE")
        val (book, _) = Fees.pay(data.book(), c.id, i.cents, at, type.method, text(i.ref, 120), type.name) ?: throw Fail(400, "BAD_AMOUNT")
        data.saveBook(book)
        return done()
    }

    // --- Planning ---

    private fun dayStart(date: String?): Long = CourtPlans.dayStart(noon(date) ?: throw Fail(400, "BAD_DATE"))

    private fun day(date: String?): Resp {
        val start = dayStart(date)
        val plan = data.plan()
        val people = data.players()
        val items = CourtPlans.occupied(plan, data.tournaments(), start + 12 * 3_600_000L).map { o ->
            val from = ((o.start - start) / 60_000L).toInt().coerceAtLeast(0)
            val to = ((o.end - start) / 60_000L).toInt()
            val b = o.booking
            when {
                b != null -> DeskItem(
                    b.id, b.court, from, to, "BOOKING",
                    (b.players.mapNotNull { Players.byId(people, it)?.name } + b.names).joinToString(", "), b.note, b.kind, b.cents, b.online != null,
                )
                o.block != null -> DeskItem(o.block.id, if (o.court == CourtPlans.ALL) "" else o.court, from, to, "BLOCK", o.block.label)
                else -> DeskItem(o.match?.id.orEmpty(), o.court, from, to, "TOURNAMENT", o.tournament?.name.orEmpty())
            }
        }
        val s = plan.settings
        return ok(DeskDay.serializer(), DeskDay(date!!, s.openHour, s.closeHour, s.slotMinutes, data.courts(), items))
    }

    private fun quote(q: Map<String, String>): Resp {
        val s = data.plan().settings
        val start = dayStart(q["date"]) + (q["startMin"]?.toLongOrNull() ?: 0) * 60_000L
        val minutes = q["minutes"]?.toIntOrNull()?.coerceIn(0, MAX_MINUTES) ?: 60
        val kind = runCatching { BookingKind.valueOf(q["kind"].orEmpty()) }.getOrDefault(BookingKind.PLAY)
        return ok(DeskQuote.serializer(), DeskQuote(CourtPlans.price(s, start, minutes, kind, q["court"].orEmpty()), CourtPlans.needsLights(s, start, minutes)))
    }

    private fun book(i: DeskBookingIn): Resp {
        if (i.court !in data.courts()) throw Fail(404, "NOT_FOUND")
        if (i.minutes !in 5..MAX_MINUTES || i.startMin !in 0 until 24 * 60) throw Fail(400, "BAD_TIME")
        val plan = data.plan()
        val start = CourtPlans.at(dayStart(i.date), 0) + i.startMin * 60_000L
        val end = start + i.minutes * 60_000L
        if (CourtPlans.blockedBy(plan, i.court, start, end, i.kind).isNotEmpty()) throw Fail(409, "BLOCKED")
        if (CourtPlans.conflicts(plan, data.tournaments(), i.court, start, end).isNotEmpty()) throw Fail(409, "CONFLICT")
        val people = data.players()
        val ids = i.players.filter { Players.byId(people, it) != null }
        data.saveBooking(
            CourtBooking(
                CourtPlans.newId("b"), i.court, start, i.minutes, i.kind, ids, i.names.map { text(it, 80) }.filter { it.isNotEmpty() }.take(8), text(i.note, 500),
                CourtPlans.needsLights(plan.settings, start, i.minutes), CourtPlans.price(plan.settings, start, i.minutes, i.kind, i.court),
            ),
            i.charge,
        )
        return done()
    }

    private fun cancelBooking(id: String): Resp {
        data.deleteBooking(data.plan().bookings.firstOrNull { it.id == id } ?: throw Fail(404, "NOT_FOUND"))
        return done()
    }

    // --- Tornei (solo da guardare) ---

    private fun tournaments(): Resp = ok(ListSerializer(DeskTournament.serializer()), data.tournaments().map { t ->
        val real = t.matches.filter { !it.bye }
        DeskTournament(t.id, t.name, t.category, t.doubles, day(t.start), day(t.end), t.entries.map { it.names.joinToString(" / ") }, real.size, real.count { it.winner != null })
    })

    companion object {
        private const val JSON = "application/json; charset=utf-8"
        private const val MAX_CENTS = 10_000_000
        private const val MAX_MINUTES = 12 * 60
    }
}
