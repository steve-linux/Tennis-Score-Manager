// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.desk

import com.tennis.scoremanager.ClubFeature
import com.tennis.scoremanager.data.BlockReason
import com.tennis.scoremanager.data.BookingKind
import com.tennis.scoremanager.data.CourtBlock
import com.tennis.scoremanager.data.Course
import com.tennis.scoremanager.data.Courses
import com.tennis.scoremanager.data.Ladder
import com.tennis.scoremanager.data.Team
import com.tennis.scoremanager.data.Charge
import com.tennis.scoremanager.data.ClubBook
import com.tennis.scoremanager.data.CTEntry
import com.tennis.scoremanager.data.CTFormat
import com.tennis.scoremanager.data.CTKind
import com.tennis.scoremanager.data.CTMatch
import com.tennis.scoremanager.data.ClubTournament
import com.tennis.scoremanager.data.ClubTournaments
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
import kotlin.random.Random

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
    /**
     * Salva una prenotazione (nuova o cambiata), ripetuta per [weeks] settimane; con [charge] divide il prezzo tra
     * i giocatori e lo addebita. Restituisce le date saltate perché il campo era occupato.
     */
    fun saveBooking(b: CourtBooking, weeks: Int, charge: Boolean): List<Long>
    /** Toglie la prenotazione o, con [series], anche quelle dopo della stessa serie. */
    fun deleteBooking(b: CourtBooking, series: Boolean)
    /** Salva (o toglie, con [delete]) un periodo di campi non prenotabili. */
    fun saveBlock(b: CourtBlock, delete: Boolean)
    /** Le funzioni del circolo accese. */
    fun features(): Set<ClubFeature>
    fun courses(): List<Course>
    fun teams(): List<Team>
    fun ladders(): List<Ladder>
    /** La ricevuta di un pagamento in PDF, come quella dell'app. */
    fun receiptPdf(chargeId: String, paymentId: String): ByteArray?
    fun paymentsCsv(year: Int): ByteArray?
    /** Salva un torneo (nuovo o cambiato); con la quota d'iscrizione, la quota del registro lo segue. */
    fun saveTournament(t: ClubTournament)
    /** Toglie il torneo: le iscrizioni non pagate si annullano, i pagamenti restano. */
    fun deleteTournament(t: ClubTournament)
    /** Iscrive un giocatore o una coppia; con la quota, l'iscrizione si addebita a ciascuno. */
    fun addEntry(t: ClubTournament, people: List<PlayerProfile>)
    /** Ritira un'iscrizione; le sue quote non pagate si annullano. */
    fun removeEntry(t: ClubTournament, entry: CTEntry)
}

@Serializable data class DeskError(val error: String)
@Serializable data class DeskCodeIn(val code: String)
@Serializable data class DeskPairOut(val state: String, val id: String? = null, val token: String? = null)
/** Il QR da disegnare: una stringa di 0 e 1 per riga. */
@Serializable data class DeskQrOut(val id: String, val rows: List<String>)
@Serializable data class DeskInfo(val club: String, val lang: String, val today: String, val year: Int, val features: List<ClubFeature> = emptyList())
/** Una riga di Oggi: quando, dove, cosa (e il torneo da aprire). */
@Serializable data class DeskTodayLine(val text: String, val startMin: Int? = null, val court: String = "", val date: String? = null, val cup: String? = null)
@Serializable data class DeskToday(
    val bookings: Int, val online: Int, val next: List<DeskTodayLine>, val collectCents: Int, val collectPeople: Int,
    val members: Int, val blocking: Int, val memberNames: List<String>, val matches: List<DeskTodayLine>, val lessons: List<DeskTodayLine>,
    val fixtures: List<DeskTodayLine>, val challenges: Int, val late: Int,
)

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
    /** La quota da cui viene (null per gli addebiti singoli) e il tipo per filtrare: un FeeKind o COURT per i campi. */
    val fee: String? = null, val type: String = "OTHER",
)
@Serializable data class DeskPayment(val id: String, val cents: Int, val date: String, val method: String, val ref: String, val receipt: String)
@Serializable data class DeskFee(val id: String, val name: String, val cents: Int, val kind: FeeKind, val due: String?, val paid: Int, val total: Int)
@Serializable data class DeskPayType(val id: String, val name: String)
@Serializable data class DeskMoney(val year: Int, val incomeCents: Int, val openCents: Int, val openPeople: Int, val fees: List<DeskFee>, val charges: List<DeskCharge>, val payTypes: List<DeskPayType>)
@Serializable data class DeskFeeIn(val name: String, val cents: Int, val kind: FeeKind = FeeKind.OTHER, val due: String? = null, val chargeMembers: Boolean = false)
@Serializable data class DeskChargeIn(val player: String, val label: String, val cents: Int, val due: String? = null, val kind: FeeKind = FeeKind.OTHER)
@Serializable data class DeskPayIn(val cents: Int, val date: String, val type: String, val ref: String = "")
@Serializable data class DeskCount(val count: Int)

@Serializable data class DeskItem(
    val id: String, val court: String, val startMin: Int, val endMin: Int, val type: String, val label: String, val note: String = "",
    val kind: BookingKind? = null, val cents: Int = 0, val online: Boolean = false,
    /** Prenotazione: giocatori del database, nomi scritti, serie settimanale, prezzo addebitato. */
    val players: List<String> = emptyList(), val names: List<String> = emptyList(), val series: Boolean = false, val charged: Boolean = false,
    /** Partita di torneo: il torneo. Blocco: il motivo, e se si cambia da qui (non quelli di corsi e squadre). */
    val cup: String? = null, val reason: BlockReason? = null, val editable: Boolean = false,
)
@Serializable data class DeskDay(val date: String, val openHour: Int, val closeHour: Int, val slotMinutes: Int, val courts: List<String>, val items: List<DeskItem>)
/** La settimana da lunedì: i sette giorni come in [DeskDay]. */
@Serializable data class DeskWeek(val from: String, val openHour: Int, val closeHour: Int, val slotMinutes: Int, val courts: List<String>, val days: List<DeskDay>)
/** Un giorno del mese in breve: prenotazioni, partite, quanto sono occupati e chiusi i campi (in percentuale). */
@Serializable data class DeskDaySum(val date: String, val bookings: Int, val matches: Int, val busy: Int, val blocked: Int)
@Serializable data class DeskMonth(val first: String, val courts: List<String>, val days: List<DeskDaySum>)
@Serializable data class DeskBookingIn(
    val court: String, val date: String, val startMin: Int, val minutes: Int, val kind: BookingKind = BookingKind.PLAY,
    val players: List<String> = emptyList(), val names: List<String> = emptyList(), val note: String = "", val charge: Boolean = false,
    /** Ripetuta ogni settimana per tante settimane (1 = solo questa). */
    val weeks: Int = 1,
)
@Serializable data class DeskBooked(val skipped: List<String>)
@Serializable data class DeskBlock(
    val id: String = "", val label: String = "", val reason: BlockReason = BlockReason.MAINTENANCE, val courts: List<String> = emptyList(),
    val from: String = "", val to: String = "", val weekdays: List<Int> = emptyList(), val startMin: Int = 0, val endMin: Int = 24 * 60, val editable: Boolean = true,
)
@Serializable data class DeskQuote(val cents: Int, val lights: Boolean)

@Serializable data class DeskTournament(
    val id: String, val name: String, val category: String, val doubles: Boolean, val start: String?, val end: String?, val entries: List<String>, val matches: Int, val played: Int,
    val kind: CTKind = CTKind.SOCIAL, val format: CTFormat = CTFormat.KNOCKOUT, val drawn: Boolean = false, val champion: String? = null,
)
@Serializable data class DeskCupIn(
    val name: String, val kind: CTKind = CTKind.SOCIAL, val format: CTFormat = CTFormat.KNOCKOUT, val doubles: Boolean = false, val category: String = "",
    val start: String? = null, val end: String? = null, val feeCents: Int = 0, val requirePaid: Boolean = true, val rankLimit: String = "",
    val fitpCode: String = "", val referee: String = "", val groups: Int = 2, val qualify: Int = 2,
)
/** Un problema di un giocatore per il torneo; [blocking] = non entra nel sorteggio. */
@Serializable data class DeskProblem(val name: String, val code: String, val blocking: Boolean)
@Serializable data class DeskCupEntry(val id: String, val players: List<String>, val names: List<String>, val seed: Int?, val problems: List<DeskProblem>)
@Serializable data class DeskCupMatch(
    val id: String, val round: Int, val slot: Int, val group: Int?, val a: String?, val b: String?, val winner: String?, val score: String,
    val walkover: Boolean, val retired: Boolean, val bye: Boolean, val court: String, val date: String?, val startMin: Int?,
)
@Serializable data class DeskStanding(val entry: String, val played: Int, val won: Int, val setsWon: Int, val setsLost: Int, val gamesWon: Int, val gamesLost: Int)
@Serializable data class DeskCup(
    val id: String, val name: String, val kind: CTKind, val format: CTFormat, val doubles: Boolean, val category: String, val start: String?, val end: String?,
    val feeCents: Int, val requirePaid: Boolean, val rankLimit: String, val fitpCode: String, val referee: String, val groups: Int, val qualify: Int,
    val drawn: Boolean, val hasResults: Boolean, val eligible: Int, val champion: String?, val matchMinutes: Int,
    val entries: List<DeskCupEntry>, val matches: List<DeskCupMatch>, val standings: List<List<DeskStanding>>, val courts: List<String>,
)
@Serializable data class DeskId(val id: String)
@Serializable data class DeskEntryIn(val players: List<String>)
@Serializable data class DeskSeedIn(val seed: Int? = null)
/** Il risultato: [winner] è l'iscritto (CTEntry.id); se manca, si ricava dal punteggio. Tutto vuoto = cancella. */
@Serializable data class DeskResultIn(val winner: String? = null, val score: String = "", val walkover: Boolean = false, val retired: Boolean = false)
/** Dove e quando; [date] null = da programmare. Con [force] la si mette anche se il campo è occupato. */
@Serializable data class DeskScheduleIn(val court: String = "", val date: String? = null, val startMin: Int? = null, val force: Boolean = false)

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
                m == "GET" && p == listOf("day") -> ok(DeskDay.serializer(), dayOut(dayStart(r.query["date"])))
                m == "GET" && p == listOf("week") -> week(r.query["date"])
                m == "GET" && p == listOf("month") -> month(r.query["date"])
                m == "GET" && p == listOf("quote") -> quote(r.query)
                m == "GET" && p == listOf("today") -> today()
                m == "GET" && p.size == 5 && p[0] == "charges" && p[2] == "payments" && p[4] == "receipt" ->
                    Resp(200, data.receiptPdf(charge(p[1]).id, p[3]) ?: throw Fail(404, "NOT_FOUND"), "application/pdf")
                m == "POST" && p == listOf("bookings") -> book(read(DeskBookingIn.serializer(), r), null)
                m == "PUT" && p.size == 2 && p[0] == "bookings" -> book(read(DeskBookingIn.serializer(), r), p[1])
                m == "DELETE" && p.size == 2 && p[0] == "bookings" -> cancelBooking(p[1], r.query["series"] == "1")
                m == "GET" && p == listOf("blocks") -> blocks()
                m == "POST" && p == listOf("blocks") -> saveBlock(read(DeskBlock.serializer(), r))
                m == "DELETE" && p.size == 2 && p[0] == "blocks" -> {
                    data.saveBlock(block(p[1]), delete = true)
                    done()
                }
                m == "GET" && p == listOf("tournaments") -> tournaments()
                m == "POST" && p == listOf("tournaments") -> createCup(read(DeskCupIn.serializer(), r))
                p.size >= 2 && p[0] == "tournaments" -> cupRoute(m, p.drop(1), r)
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
        return ok(DeskInfo.serializer(), DeskInfo(data.club, data.lang, day(now)!!, Instant.ofEpochMilli(now).atZone(zone()).year, data.features().sortedBy { it.ordinal }))
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
        val kinds = book.fees.associate { it.id to it.kind }
        val courts = (data.courts() + data.plan().bookings.map { it.court }).distinct()
        val charges = book.charges.sortedWith(compareBy<Charge> { it.left == 0 }.thenByDescending { it.createdAt }).map { c ->
            DeskCharge(
                c.id, c.player, c.name, c.label, c.cents, c.paid, c.left, day(c.due), Fees.status(c, now).name,
                c.payments.map { DeskPayment(it.id, it.cents, day(it.at)!!, it.methodName.ifEmpty { it.method.name }, it.ref, it.receipt) },
                c.fee, type(c, kinds, courts),
            )
        }
        return ok(DeskMoney.serializer(), DeskMoney(y, Fees.income(book, y), openC, openP, fees, charges, data.payTypes().map { DeskPayType(it.id, it.name) }))
    }

    /**
     * Il tipo di un addebito: quello segnato, o quello della sua quota; gli addebiti dei campi cominciano col nome del
     * campo; quelli vecchi fatti a mano con "FITP" o "tessera" nella descrizione sono tessere.
     */
    private fun type(c: Charge, kinds: Map<String, FeeKind>, courts: List<String>): String {
        (c.kind ?: c.fee?.let { kinds[it] })?.let { return it.name }
        if (courts.any { c.label.startsWith("$it · ") }) return "COURT"
        val l = c.label.lowercase()
        return if ("fitp" in l || "tessera" in l) FeeKind.FITP.name else FeeKind.OTHER.name
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
        val book = Fees.charge(data.book(), p, label, cents(i.cents), data.now(), kind = i.kind)
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

    private fun dayOut(start: Long): DeskDay {
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
                    b.players.filter { Players.byId(people, it) != null }, b.names, b.series != null, b.charges.isNotEmpty(),
                )
                o.block != null -> DeskItem(
                    o.block.id, if (o.court == CourtPlans.ALL) "" else o.court, from, to, "BLOCK", o.block.label,
                    reason = o.block.reason, editable = editable(o.block),
                )
                else -> {
                    val t = o.tournament
                    val who = listOf(o.match?.a, o.match?.b).map { id -> t?.entries?.firstOrNull { it.id == id }?.names?.joinToString(" / ") ?: "…" }.joinToString(" – ")
                    DeskItem(o.match?.id.orEmpty(), o.court, from, to, "TOURNAMENT", t?.name.orEmpty(), who, cup = t?.id)
                }
            }
        }
        val s = plan.settings
        return DeskDay(day(start + 12 * 3_600_000L)!!, s.openHour, s.closeHour, s.slotMinutes, data.courts(), items)
    }

    private fun localDay(date: String?): LocalDate = runCatching { LocalDate.parse(date.orEmpty()) }.getOrElse { throw Fail(400, "BAD_DATE") }

    private fun week(date: String?): Resp {
        val monday = localDay(date).with(java.time.DayOfWeek.MONDAY)
        val days = (0L until 7L).map { dayOut(dayStart(monday.plusDays(it).toString())) }
        val s = data.plan().settings
        return ok(DeskWeek.serializer(), DeskWeek(monday.toString(), s.openHour, s.closeHour, s.slotMinutes, data.courts(), days))
    }

    /** Il mese giorno per giorno, in breve: per vedere a colpo d'occhio i giorni pieni, vuoti e chiusi. */
    private fun month(date: String?): Resp {
        val first = localDay(date).withDayOfMonth(1)
        val plan = data.plan(); val cups = data.tournaments(); val courts = data.courts()
        val s = plan.settings
        val open = (s.closeHour - s.openHour).coerceAtLeast(1) * 60
        val days = (0 until first.lengthOfMonth()).map { i ->
            val d = first.plusDays(i.toLong())
            val start = dayStart(d.toString())
            val from = start + s.openHour * 3_600_000L; val to = start + s.closeHour * 3_600_000L
            val occ = CourtPlans.occupied(plan, cups, start + 12 * 3_600_000L)
            fun minutes(o: com.tennis.scoremanager.data.Occupied) = ((minOf(o.end, to) - maxOf(o.start, from)).coerceAtLeast(0) / 60_000L).toInt()
            val used = occ.filter { it.block == null }.sumOf(::minutes)
            val closed = occ.filter { it.block != null }.sumOf { o -> minutes(o) * (if (o.court == CourtPlans.ALL) courts.size else 1) }
            val cap = (open * courts.size).coerceAtLeast(1)
            DeskDaySum(d.toString(), occ.count { it.booking != null }, occ.count { it.match != null }, (used * 100 / cap).coerceAtMost(100), (closed * 100 / cap).coerceAtMost(100))
        }
        return ok(DeskMonth.serializer(), DeskMonth(first.toString(), courts, days))
    }

    private fun quote(q: Map<String, String>): Resp {
        val s = data.plan().settings
        val start = dayStart(q["date"]) + (q["startMin"]?.toLongOrNull() ?: 0) * 60_000L
        val minutes = q["minutes"]?.toIntOrNull()?.coerceIn(0, MAX_MINUTES) ?: 60
        val kind = runCatching { BookingKind.valueOf(q["kind"].orEmpty()) }.getOrDefault(BookingKind.PLAY)
        return ok(DeskQuote.serializer(), DeskQuote(CourtPlans.price(s, start, minutes, kind, q["court"].orEmpty()), CourtPlans.needsLights(s, start, minutes)))
    }

    /** Una prenotazione nuova ([id] null, anche ripetuta ogni settimana) o cambiata (campo, ora, durata, giocatori). */
    private fun book(i: DeskBookingIn, id: String?): Resp {
        if (i.court !in data.courts()) throw Fail(404, "NOT_FOUND")
        if (i.minutes !in 5..MAX_MINUTES || i.startMin !in 0 until 24 * 60) throw Fail(400, "BAD_TIME")
        val plan = data.plan()
        val old = id?.let { x -> plan.bookings.firstOrNull { it.id == x } ?: throw Fail(404, "NOT_FOUND") }
        val start = CourtPlans.at(dayStart(i.date), 0) + i.startMin * 60_000L
        val end = start + i.minutes * 60_000L
        if (CourtPlans.blockedBy(plan, i.court, start, end, i.kind).isNotEmpty()) throw Fail(409, "BLOCKED")
        if (CourtPlans.conflicts(plan, data.tournaments(), i.court, start, end, except = old?.id).isNotEmpty()) throw Fail(409, "CONFLICT")
        val people = data.players()
        val ids = i.players.filter { Players.byId(people, it) != null }
        val names = i.names.map { text(it, 80) }.filter { it.isNotEmpty() }.take(8)
        val lights = CourtPlans.needsLights(plan.settings, start, i.minutes)
        val price = CourtPlans.price(plan.settings, start, i.minutes, i.kind, i.court)
        val b = old?.copy(court = i.court, start = start, minutes = i.minutes, kind = i.kind, players = ids, names = names, note = text(i.note, 500), lights = lights, cents = price)
            ?: CourtBooking(CourtPlans.newId("b"), i.court, start, i.minutes, i.kind, ids, names, text(i.note, 500), lights, price)
        val skipped = data.saveBooking(b, if (old == null) i.weeks.coerceIn(1, 52) else 1, i.charge)
        return ok(DeskBooked.serializer(), DeskBooked(skipped.mapNotNull { day(it) }))
    }

    private fun cancelBooking(id: String, series: Boolean): Resp {
        data.deleteBooking(data.plan().bookings.firstOrNull { it.id == id } ?: throw Fail(404, "NOT_FOUND"), series)
        return done()
    }

    // --- Campi non prenotabili ---

    /** Quelli di corsi e squadre si cambiano col corso o con la squadra, nell'app. */
    private fun editable(b: CourtBlock) = b.course == null && b.team == null

    private fun block(id: String): CourtBlock {
        val b = data.plan().blocks.firstOrNull { it.id == id } ?: throw Fail(404, "NOT_FOUND")
        if (!editable(b)) throw Fail(409, "MANAGED")
        return b
    }

    private fun blocks(): Resp = ok(ListSerializer(DeskBlock.serializer()), data.plan().blocks.sortedBy { it.from }.map { b ->
        DeskBlock(b.id, b.label, b.reason, b.courts, day(b.from)!!, day(b.to)!!, b.weekdays.sorted(), b.startMin, b.endMin, editable(b))
    })

    private fun saveBlock(i: DeskBlock): Resp {
        val old = i.id.takeIf { it.isNotEmpty() }?.let(::block)
        val from = CourtPlans.dayStart(noon(i.from) ?: throw Fail(400, "BAD_DATE"))
        val to = CourtPlans.dayStart(noon(i.to) ?: throw Fail(400, "BAD_DATE"))
        if (to < from) throw Fail(400, "BAD_DATE")
        if (i.startMin !in 0 until 24 * 60 || i.endMin !in 1..24 * 60 || i.endMin <= i.startMin) throw Fail(400, "BAD_TIME")
        val courts = data.courts()
        if (i.courts.any { it !in courts }) throw Fail(404, "NOT_FOUND")
        if (i.reason == BlockReason.COURSE) throw Fail(400, "BAD_REQUEST")
        val b = (old ?: CourtBlock(CourtPlans.newId("k"), "", from = from, to = to)).copy(
            label = text(i.label, 40), reason = i.reason, courts = i.courts.distinct(), from = from, to = to,
            weekdays = i.weekdays.filter { it in 1..7 }.toSet(), startMin = i.startMin, endMin = i.endMin,
        )
        data.saveBlock(b, delete = false)
        return done()
    }

    // --- Oggi ---

    /** Quello che serve oggi, come nella scheda Oggi dell'app: solo per le funzioni accese. */
    private fun today(): Resp {
        val now = data.now(); val f = data.features()
        val today = CourtPlans.dayStart(now); val tomorrow = CourtPlans.addDays(today, 1)
        val people = data.players()
        val tournaments = data.tournaments()
        fun min(t: Long) = ((t - today) / 60_000L).toInt()
        var bookings = 0; var online = 0; var next = emptyList<DeskTodayLine>()
        if (ClubFeature.PLANNING in f) {
            val occ = CourtPlans.occupied(data.plan(), tournaments, today).filter { it.block == null }
            bookings = occ.size
            online = occ.count { it.booking?.online != null }
            next = occ.filter { it.end > now }.sortedBy { it.start }.take(4).map { o ->
                val who = o.booking?.let { b -> (b.players.mapNotNull { Players.byId(people, it)?.name } + b.names).joinToString(", ") } ?: o.tournament?.name.orEmpty()
                DeskTodayLine(who, min(o.start), o.court)
            }
        }
        val (cents, owing) = if (ClubFeature.PAYMENTS in f) Fees.open(data.book()) else 0 to 0
        val issues = if (ClubFeature.MEMBERS in f) people.filter { it.member != null }.map { it to Members.issues(it, now) }.filter { it.second.isNotEmpty() } else emptyList()
        val matches = if (ClubFeature.TOURNAMENTS in f) tournaments.flatMap { t ->
            t.matches.filter { m -> m.at != null && m.at in today until tomorrow && m.winner == null && !m.bye }.map { m -> Triple(t, m, m.at!!) }
        }.sortedBy { it.third }.map { (t, m, at) -> DeskTodayLine(t.name, min(at), m.court, cup = t.id) } else emptyList()
        val lessons = if (ClubFeature.SCHOOL in f) data.courses().flatMap { co ->
            Courses.lessons(co).filter { it.start in today until tomorrow && it.key !in co.cancelled }.map { co to it }
        }.sortedBy { it.second.start }.map { (co, l) -> DeskTodayLine(co.name, min(l.start), l.court) } else emptyList()
        val fixtures = if (ClubFeature.TEAMS in f) data.teams().flatMap { t ->
            t.fixtures.filter { it.at in today until CourtPlans.addDays(today, 7) }.map { t to it }
        }.sortedBy { it.second.at }.map { (t, x) -> DeskTodayLine("${t.name} – ${x.opponent}", date = day(x.at)) } else emptyList()
        val open = if (ClubFeature.LADDER in f) data.ladders().flatMap { l -> l.challenges.filter { it.open } } else emptyList()
        return ok(DeskToday.serializer(), DeskToday(
            bookings, online, next, cents, owing, issues.size, issues.count { (_, l) -> l.any(Members::blocking) }, issues.take(3).map { it.first.name },
            matches, lessons, fixtures, open.size, open.count { it.deadline < now },
        ))
    }

    // --- Tornei ---

    private fun tournaments(): Resp = ok(ListSerializer(DeskTournament.serializer()), data.tournaments().sortedByDescending { it.start ?: it.createdAt }.map { t ->
        val real = t.matches.filter { !it.bye }
        DeskTournament(
            t.id, t.name, t.category, t.doubles, day(t.start), day(t.end), t.entries.map { it.names.joinToString(" / ") }, real.size, real.count { it.winner != null },
            t.kind, t.format, t.drawn, t.champion?.let { c -> t.entries.firstOrNull { it.id == c }?.names?.joinToString(" / ") },
        )
    })

    private fun cup(id: String): ClubTournament = data.tournaments().firstOrNull { it.id == id } ?: throw Fail(404, "NOT_FOUND")

    /** Un risultato vero (non un bye): dopo, il sorteggio non si rifà più. */
    private fun hasResults(t: ClubTournament) = t.matches.any { it.winner != null && !it.bye }

    private fun cupRoute(m: String, p: List<String>, r: Req): Resp {
        val t = cup(p[0])
        val rest = p.drop(1)
        return when {
            m == "GET" && rest.isEmpty() -> cupOut(t)
            m == "PUT" && rest.isEmpty() -> editCup(t, read(DeskCupIn.serializer(), r))
            m == "DELETE" && rest.isEmpty() -> { data.deleteTournament(t); done() }
            m == "POST" && rest == listOf("entries") -> addEntry(t, read(DeskEntryIn.serializer(), r))
            m == "DELETE" && rest.size == 2 && rest[0] == "entries" -> removeEntry(t, rest[1])
            m == "PUT" && rest.size == 3 && rest[0] == "entries" && rest[2] == "seed" -> seed(t, rest[1], read(DeskSeedIn.serializer(), r))
            m == "POST" && rest == listOf("draw") -> draw(t)
            m == "POST" && rest == listOf("undraw") -> {
                if (hasResults(t)) throw Fail(409, "HAS_RESULTS")
                save(t.copy(matches = emptyList(), drawnAt = null))
            }
            m == "POST" && rest == listOf("knockout") -> {
                if (t.format != CTFormat.GROUPS || !t.groupsDone || t.matches.any { it.round > 0 }) throw Fail(409, "NOT_READY")
                val now = data.now()
                save(t.copy(matches = t.matches.filter { it.round == 0 } + ClubTournaments.knockoutFromGroups(t, Random(now), now)))
            }
            m == "POST" && rest.size == 3 && rest[0] == "matches" && rest[2] == "result" -> result(t, rest[1], read(DeskResultIn.serializer(), r))
            m == "POST" && rest.size == 3 && rest[0] == "matches" && rest[2] == "schedule" -> schedule(t, rest[1], read(DeskScheduleIn.serializer(), r))
            else -> throw Fail(404, "NOT_FOUND")
        }
    }

    private fun save(t: ClubTournament): Resp { data.saveTournament(t); return cupOut(cup(t.id)) }

    private fun cupOut(t: ClubTournament): Resp {
        val people = data.players(); val book = data.book(); val now = data.now()
        val entries = t.entries.map { e ->
            val problems = e.players.mapIndexed { i, id -> id to e.names.getOrElse(i) { "" } }.flatMap { (id, name) ->
                val p = Players.byId(people, id) ?: return@flatMap listOf(DeskProblem(name, "NO_PLAYER", true))
                ClubTournaments.problems(t, p, book, now).map { DeskProblem(p.name, it.name, ClubTournaments.blocking(it)) }
            }
            DeskCupEntry(e.id, e.players, e.players.mapIndexed { i, id -> Players.byId(people, id)?.name ?: e.names.getOrElse(i) { "" } }, e.seed, problems)
        }
        val matches = t.matches.sortedWith(compareBy({ it.round }, { it.group ?: 0 }, { it.slot })).map { x ->
            val start = x.at?.let { CourtPlans.dayStart(it) }
            DeskCupMatch(
                x.id, x.round, x.slot, x.group, x.a, x.b, x.winner, x.score, x.walkover, x.retired, x.bye, x.court,
                x.at?.let { at -> day(at) }, if (x.at != null && start != null) ((x.at - start) / 60_000L).toInt() else null,
            )
        }
        val standings = ClubTournaments.groupMembers(t).indices.map { g ->
            ClubTournaments.standings(t, g).map { DeskStanding(it.entry, it.played, it.won, it.setsWon, it.setsLost, it.gamesWon, it.gamesLost) }
        }
        return ok(DeskCup.serializer(), DeskCup(
            t.id, t.name, t.kind, t.format, t.doubles, t.category, day(t.start), day(t.end), t.feeCents, t.requirePaid, t.rankLimit, t.fitpCode, t.referee,
            t.groups, t.qualify, t.drawn, hasResults(t), ClubTournaments.eligible(t, people, book, now).size, t.champion, data.plan().settings.tournamentMinutes,
            entries, matches, standings, data.courts(),
        ))
    }

    /** I campi del modulo; formula, doppio e gironi solo prima del sorteggio. */
    private fun fill(t: ClubTournament, i: DeskCupIn): ClubTournament {
        val name = text(i.name, 120)
        if (name.isEmpty()) throw Fail(400, "NAME_REQUIRED")
        val start = noon(i.start); val end = noon(i.end)
        if (start != null && end != null && end < start) throw Fail(400, "BAD_DATE")
        if (i.feeCents != 0) cents(i.feeCents)
        val base = t.copy(
            name = name, kind = i.kind, category = text(i.category, 80), start = start, end = end, feeCents = i.feeCents, requirePaid = i.requirePaid,
            rankLimit = text(i.rankLimit, 10), fitpCode = text(i.fitpCode, 40), referee = text(i.referee, 80), qualify = i.qualify.coerceIn(1, 8),
        )
        return if (t.drawn) base else base.copy(format = i.format, doubles = i.doubles, groups = i.groups.coerceIn(1, 16))
    }

    private fun createCup(i: DeskCupIn): Resp {
        val now = data.now()
        val t = fill(ClubTournament(ClubTournaments.newId("t"), "", createdAt = now), i)
        data.saveTournament(t)
        return ok(DeskId.serializer(), DeskId(t.id))
    }

    private fun editCup(t: ClubTournament, i: DeskCupIn): Resp = save(fill(t, i))

    private fun addEntry(t: ClubTournament, i: DeskEntryIn): Resp {
        val people = data.players()
        if (i.players.size != (if (t.doubles) 2 else 1) || i.players.toSet().size != i.players.size) throw Fail(400, "BAD_REQUEST")
        val list = i.players.map { Players.byId(people, it) ?: throw Fail(404, "NOT_FOUND") }
        val inside = t.entries.flatMap { it.players }.toSet()
        if (list.any { p -> p.id in inside || p.aliases.any { it in inside } }) throw Fail(409, "ALREADY_IN")
        data.addEntry(t, list)
        return cupOut(cup(t.id))
    }

    private fun removeEntry(t: ClubTournament, id: String): Resp {
        val e = t.entries.firstOrNull { it.id == id } ?: throw Fail(404, "NOT_FOUND")
        if (t.matches.any { it.a == id || it.b == id }) throw Fail(409, "IN_DRAW")
        data.removeEntry(t, e)
        return cupOut(cup(t.id))
    }

    private fun seed(t: ClubTournament, id: String, i: DeskSeedIn): Resp {
        if (t.entries.none { it.id == id }) throw Fail(404, "NOT_FOUND")
        val n = i.seed?.takeIf { it > 0 }?.coerceAtMost(64)
        return save(t.copy(entries = t.entries.map { if (it.id == id) it.copy(seed = n) else it }))
    }

    /** Il sorteggio come sul telefono: gli iscritti in regola, in ordine per le teste di serie. */
    private fun draw(t: ClubTournament): Resp {
        if (hasResults(t)) throw Fail(409, "HAS_RESULTS")
        val now = data.now(); val people = data.players()
        val ok = ClubTournaments.eligible(t, people, data.book(), now)
        if (ok.size < 2) throw Fail(409, "TOO_FEW")
        val ids = ClubTournaments.ordered(ok, people).map { it.id }
        val ms = if (t.format == CTFormat.GROUPS) ClubTournaments.groupsDraw(ids, t.groups) else ClubTournaments.knockout(ids, Random(now), now)
        return save(t.copy(matches = ms, drawnAt = now))
    }

    private fun match(t: ClubTournament, id: String): CTMatch = t.matches.firstOrNull { it.id == id } ?: throw Fail(404, "NOT_FOUND")

    private fun result(t: ClubTournament, id: String, i: DeskResultIn): Resp {
        val m = match(t, id)
        if (m.a == null || m.b == null) throw Fail(409, "NOT_READY")
        val score = text(i.score, 60)
        if (i.winner == null && score.isEmpty() && !i.walkover && !i.retired) return save(ClubTournaments.setResult(t, id, null, ""))
        val w = i.winner ?: when (ClubTournaments.winnerFromScore(score)) { "a" -> m.a; "b" -> m.b; else -> null }
        if (w == null || (w != m.a && w != m.b)) throw Fail(400, "NO_WINNER")
        return save(ClubTournaments.setResult(t, id, w, score, i.walkover, i.retired))
    }

    private fun schedule(t: ClubTournament, id: String, i: DeskScheduleIn): Resp {
        match(t, id)
        if (i.date == null) return save(t.copy(matches = t.matches.map { if (it.id == id) it.copy(court = "", at = null) else it }))
        if (i.court.isNotEmpty() && i.court !in data.courts()) throw Fail(404, "NOT_FOUND")
        val min = i.startMin ?: throw Fail(400, "BAD_TIME")
        if (min !in 0 until 24 * 60) throw Fail(400, "BAD_TIME")
        val at = dayStart(i.date) + min * 60_000L
        if (i.court.isNotEmpty() && !i.force) {
            val plan = data.plan()
            val end = at + plan.settings.tournamentMinutes * 60_000L
            val others = data.tournaments().map { x -> if (x.id != t.id) x else x.copy(matches = x.matches.map { if (it.id == id) it.copy(at = null) else it }) }
            if (CourtPlans.conflicts(plan, others, i.court, at, end).isNotEmpty() || CourtPlans.blockedBy(plan, i.court, at, end, BookingKind.PLAY).isNotEmpty()) throw Fail(409, "CONFLICT")
        }
        return save(t.copy(matches = t.matches.map { if (it.id == id) it.copy(court = i.court, at = at) else it }))
    }

    companion object {
        private const val JSON = "application/json; charset=utf-8"
        private const val MAX_CENTS = 10_000_000
        private const val MAX_MINUTES = 12 * 60
    }
}
