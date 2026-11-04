// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.desk

import com.tennis.scoremanager.ClubFeature
import com.tennis.scoremanager.data.BlockReason
import com.tennis.scoremanager.data.BookingRules
import com.tennis.scoremanager.data.ClubServerConf
import com.tennis.scoremanager.data.SrvAdmin
import com.tennis.scoremanager.data.BookingKind
import com.tennis.scoremanager.data.CourtBlock
import com.tennis.scoremanager.data.Course
import com.tennis.scoremanager.data.CourseFee
import com.tennis.scoremanager.data.CourseSlot
import com.tennis.scoremanager.data.CourtStats
import com.tennis.scoremanager.data.Courses
import com.tennis.scoremanager.data.Ladder
import com.tennis.scoremanager.data.Availability
import com.tennis.scoremanager.data.Fixture
import com.tennis.scoremanager.data.Rubber
import com.tennis.scoremanager.data.Team
import com.tennis.scoremanager.data.Teams
import com.tennis.scoremanager.data.Charge
import com.tennis.scoremanager.data.ClubBook
import com.tennis.scoremanager.data.CTEntry
import com.tennis.scoremanager.data.CTFormat
import com.tennis.scoremanager.data.CTKind
import com.tennis.scoremanager.data.CTMatch
import com.tennis.scoremanager.data.ClubTournament
import com.tennis.scoremanager.data.ClubTournaments
import com.tennis.scoremanager.data.CourtBooking
import com.tennis.scoremanager.data.CourtInfo
import com.tennis.scoremanager.data.CourtSettings
import com.tennis.scoremanager.data.PayMethod
import com.tennis.scoremanager.data.Surface
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
    /** Salva il corso (e i suoi periodi non prenotabili nel planning) o lo toglie. */
    fun saveCourse(c: Course)
    fun deleteCourse(c: Course)
    /** La voce della quota del corso nel registro: per il mese (anno, mese 0-11) o, con null, la stagione. */
    fun courseFeeLabel(c: Course, month: Pair<Int, Int>?): String
    /** Addebita la quota del mese (o della stagione) agli allievi che non l'hanno; quanti addebiti nuovi. */
    fun chargeCourse(c: Course, month: Pair<Int, Int>?): Int
    /** Il sollecito per gli addebiti [charges]: a chi va e il suo messaggio, come nell'app. */
    fun dues(charges: Set<String>): List<DeskDue>
    /** Apre sul telefono gli avvisi del sollecito, da mandare da lì uno per uno. */
    fun sendDues(charges: Set<String>)
    /** Salva la squadra (e i campi tenuti per gli incontri in casa nel planning) o la toglie. */
    fun saveTeam(t: Team)
    fun deleteTeam(t: Team)
    /** Apre sul telefono l'avviso dell'incontro: disponibilità (a tutti o a chi non ha risposto), convocazione, risultato. */
    fun teamNotice(t: Team, f: Fixture, kind: String)
    /** Impostazioni del circolo, come in Orari e prezzi, Campi e Modi di pagamento nell'app. */
    fun setClubName(name: String)
    fun saveCourtSettings(st: CourtSettings)
    /** Un campo nuovo; false se il nome c'è già. */
    fun addCourt(info: CourtInfo): Boolean
    /** Cambia nome (anche in prenotazioni, chiusure e partite); false se il nome c'è già. */
    fun renameCourt(old: String, new: String): Boolean
    fun saveCourtInfo(info: CourtInfo)
    /** Toglie il campo con le sue prenotazioni da oggi in poi. */
    fun deleteCourt(name: String)
    fun moveCourt(name: String, up: Boolean)
    fun savePayTypes(list: List<PayType>)
    /** Il server delle prenotazioni: come è collegato e le sue regole. */
    fun serverConf(): ClubServerConf
    fun saveServerRules(rules: BookingRules, payInfo: String, alerts: Boolean)
    /** Dal server, fuori da [tx] (è rete): prenotazioni online, lista d'attesa, Cerco compagno. */
    fun serverState(): SrvAdmin
    /** Manda subito il planning e prende le prenotazioni nuove, fuori da [tx]; null = fatto, se no il codice dell'errore. */
    fun serverSync(): String?
    /** La segreteria toglie un'attesa ([kind] "wait") o un annuncio ("seek") dal server, fuori da [tx]. */
    fun serverRemove(kind: String, id: String)
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
@Serializable data class DeskInfo(val club: String, val lang: String, val today: String, val year: Int, val features: List<ClubFeature> = emptyList(), val courts: List<String> = emptyList())
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
@Serializable data class DeskDue(val id: String, val name: String, val phone: String, val email: String, val text: String)
@Serializable data class DeskIds(val ids: List<String> = emptyList())
@Serializable data class DeskStatsCourt(
    val court: String, val openHours: Double, val blockedHours: Double, val bookedHours: Double, val occupancy: Double, val cents: Long, val bookings: Int, val online: Int,
)
@Serializable data class DeskStats(
    val from: String, val to: String, val courts: List<DeskStatsCourt>, val occupancy: Double, val cents: Long, val bookings: Int, val online: Int,
    val tournamentHours: Double, val firstHour: Int, val heat: List<List<Double>>, val top: List<DeskTop>,
)
@Serializable data class DeskTop(val name: String, val count: Int)
@Serializable data class DeskSlot(val weekday: Int, val startMin: Int, val minutes: Int = 60, val court: String = "")
@Serializable data class DeskStudent(val id: String, val name: String, val present: Int, val marked: Int, val owedCents: Int)
@Serializable data class DeskLesson(val key: String, val date: String, val startMin: Int, val minutes: Int, val court: String, val cancelled: Boolean, val marked: Boolean, val present: List<String>)
/** Una quota del corso: il mese ("2026-11") o la stagione (""), quanti addebiti e quanti pagati. */
@Serializable data class DeskCourseFee(val month: String, val label: String, val charged: Int, val paid: Int)
@Serializable data class DeskCourse(
    val id: String, val name: String, val level: String, val coach: String?, val coachName: String, val from: String, val to: String,
    val maxStudents: Int, val feeCents: Int, val feeKind: CourseFee, val note: String, val slots: List<DeskSlot>,
    val students: List<DeskStudent>, val lessons: List<DeskLesson>, val fees: List<DeskCourseFee>,
)
@Serializable data class DeskCourseIn(
    val name: String, val level: String = "", val coach: String? = null, val coachName: String = "", val from: String, val to: String,
    val maxStudents: Int = 0, val feeCents: Int = 0, val feeKind: CourseFee = CourseFee.MONTHLY, val note: String = "", val slots: List<DeskSlot> = emptyList(),
)
@Serializable data class DeskMarkIn(val key: String, val present: List<String> = emptyList(), val cancelled: Boolean? = null)
@Serializable data class DeskChargeCourseIn(val month: String = "")
@Serializable data class DeskRubber(val doubles: Boolean, val ours: List<String>, val theirs: List<String>, val score: String, val won: Boolean?)
@Serializable data class DeskFixture(
    val id: String, val opponent: String, val date: String, val startMin: Int, val home: Boolean, val place: String, val meeting: String,
    val courts: List<String>, val hours: Int, val note: String, val availability: Map<String, Availability>, val callUp: List<String>,
    val rubbers: List<DeskRubber>, val won: Int, val lost: Int, val decided: Boolean, val outcome: Boolean?,
    /** Per ogni giocatore della rosa i problemi per quel giorno (tessera, certificato), come nell'app. */
    val problems: Map<String, List<DeskProblem>>,
)
@Serializable data class DeskTeamPlayer(val id: String, val name: String)
@Serializable data class DeskTeam(
    val id: String, val name: String, val championship: String, val season: String, val captain: String?, val roster: List<DeskTeamPlayer>,
    val singles: Int, val doubles: Int, val fitp: Boolean, val won: Int, val lost: Int, val drawn: Int, val fixtures: List<DeskFixture>,
)
@Serializable data class DeskTeamIn(
    val name: String, val championship: String = "", val season: String = "", val captain: String? = null, val singles: Int = 4, val doubles: Int = 2, val fitp: Boolean = true,
)
@Serializable data class DeskFixtureIn(
    val opponent: String, val date: String, val startMin: Int, val home: Boolean = true, val place: String = "", val meeting: String = "",
    val courts: List<String> = emptyList(), val hours: Int = 6, val note: String = "",
)
@Serializable data class DeskAnswerIn(val player: String, val answer: Availability? = null)
@Serializable data class DeskRubberIn(val index: Int, val ours: List<String> = emptyList(), val theirs: List<String> = emptyList(), val score: String = "", val won: Boolean? = null)
@Serializable data class DeskNoticeIn(val kind: String)
@Serializable data class DeskCourtInfo(
    val name: String, val surface: Surface? = null, val covered: Boolean = false, val lights: Boolean = true, val heating: Boolean = false,
    val centsPerHour: Int? = null, val centsPerHourLights: Int? = null, val heatingCentsPerHour: Int = 0, val futureBookings: Int = 0, val photos: Int = 0,
)
@Serializable data class DeskPayTypeFull(val id: String = "", val name: String, val details: String = "", val online: Boolean = false, val method: PayMethod = PayMethod.OTHER)
@Serializable data class DeskSettings(
    val club: String, val openHour: Int, val closeHour: Int, val slotMinutes: Int, val centsPerHour: Int, val centsPerHourLights: Int, val lightsFrom: Int,
    val tournamentMinutes: Int, val courts: List<DeskCourtInfo>, val payTypes: List<DeskPayTypeFull>,
)
@Serializable data class DeskHoursIn(
    val openHour: Int, val closeHour: Int, val slotMinutes: Int, val centsPerHour: Int = 0, val centsPerHourLights: Int = 0, val lightsFrom: Int = 18, val tournamentMinutes: Int = 90,
)
@Serializable data class DeskClubIn(val name: String)
@Serializable data class DeskCourtIn(val old: String = "", val info: DeskCourtInfo)
@Serializable data class DeskMoveIn(val name: String, val up: Boolean)
@Serializable data class DeskPayTypesIn(val list: List<DeskPayTypeFull>)
@Serializable data class DeskOnlineBooking(val id: String, val court: String, val date: String, val startMin: Int, val minutes: Int, val name: String, val cents: Int, val createdAt: String?)
@Serializable data class DeskWait(val id: String, val name: String, val court: String?, val date: String, val startMin: Int, val minutes: Int, val freedCourt: String?)
@Serializable data class DeskSeek(
    val id: String, val players: List<String>, val date: String, val fromMin: Int, val toMin: Int, val doubles: Boolean, val level: String, val note: String,
    val need: Int, val booked: Boolean, val noCourt: Boolean,
)
@Serializable data class DeskOnline(
    val paired: Boolean, val club: String, val url: String, val publicUrl: String, val lastSync: String?, val lastSyncMin: Int?, val error: String?,
    val rules: BookingRules, val payInfo: String, val alerts: Boolean, val courts: List<String>,
    /** Quello che sta sul server; [stateError] se non risponde. */
    val bookings: List<DeskOnlineBooking>, val waits: List<DeskWait>, val seeks: List<DeskSeek>, val stateError: String? = null,
)
@Serializable data class DeskRulesIn(val rules: BookingRules, val payInfo: String = "", val alerts: Boolean = true)
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
        // Il server delle prenotazioni è rete: si chiama fuori da tx, per non fermare l'app intanto.
        if (p.firstOrNull() == "online" && !(m == "PUT" && p == listOf("online", "rules"))) return online(m, p)
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
                m == "GET" && p == listOf("stats") -> stats(r.query["from"], r.query["to"])
                m == "POST" && p == listOf("dues") -> ok(ListSerializer(DeskDue.serializer()), data.dues(read(DeskIds.serializer(), r).ids.toSet()))
                m == "POST" && p == listOf("dues", "send") -> {
                    val ids = read(DeskIds.serializer(), r).ids.toSet()
                    if (data.dues(ids).isEmpty()) throw Fail(400, "NOTHING_DUE")
                    data.sendDues(ids)
                    done()
                }
                m == "PUT" && p == listOf("online", "rules") -> saveRules(read(DeskRulesIn.serializer(), r))
                m == "GET" && p == listOf("settings") -> settings()
                m == "PUT" && p == listOf("settings", "club") -> {
                    val name = text(read(DeskClubIn.serializer(), r).name, 80)
                    if (name.isEmpty()) throw Fail(400, "NAME_REQUIRED")
                    data.setClubName(name); settings()
                }
                m == "PUT" && p == listOf("settings", "hours") -> saveHours(read(DeskHoursIn.serializer(), r))
                m == "POST" && p == listOf("settings", "courts") -> saveCourt(read(DeskCourtIn.serializer(), r))
                m == "DELETE" && p == listOf("settings", "courts") -> {
                    val name = r.query["name"].orEmpty()
                    if (name !in data.courts()) throw Fail(404, "NOT_FOUND")
                    data.deleteCourt(name); settings()
                }
                m == "POST" && p == listOf("settings", "courts", "move") -> {
                    val i = read(DeskMoveIn.serializer(), r)
                    if (i.name !in data.courts()) throw Fail(404, "NOT_FOUND")
                    data.moveCourt(i.name, i.up); settings()
                }
                m == "PUT" && p == listOf("settings", "paytypes") -> savePayTypes(read(DeskPayTypesIn.serializer(), r).list)
                m == "GET" && p == listOf("teams") -> ok(ListSerializer(DeskTeam.serializer()), data.teams().sortedBy { it.name.lowercase() }.map(::teamOut))
                m == "POST" && p == listOf("teams") -> saveTeam(null, read(DeskTeamIn.serializer(), r))
                p.size >= 2 && p[0] == "teams" -> teamRoute(m, p.drop(1), r)
                m == "GET" && p == listOf("courses") -> ok(ListSerializer(DeskCourse.serializer()), data.courses().sortedBy { it.name.lowercase() }.map(::courseOut))
                m == "POST" && p == listOf("courses") -> saveCourse(null, read(DeskCourseIn.serializer(), r))
                p.size >= 2 && p[0] == "courses" -> courseRoute(m, p.drop(1), r)
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
        return ok(DeskInfo.serializer(), DeskInfo(data.club, data.lang, day(now)!!, Instant.ofEpochMilli(now).atZone(zone()).year, data.features().sortedBy { it.ordinal }, data.courts()))
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

    // --- Statistiche dei campi ---

    /** Come in Statistiche dei campi nell'app, dal giorno [from] al giorno [to] compresi. */
    private fun stats(from: String?, to: String?): Resp {
        val a = dayStart(from); val b = CourtPlans.addDays(dayStart(to), 1)
        if (b <= a || b - a > 400L * 86_400_000L) throw Fail(400, "BAD_DATE")
        val people = data.players()
        val rep = CourtStats.report(data.plan(), data.tournaments(), data.courts(), a, b)
        return ok(DeskStats.serializer(), DeskStats(
            from!!, to!!, rep.courts.map { DeskStatsCourt(it.court, it.openHours, it.blockedHours, it.bookedHours, it.occupancy, it.cents, it.bookings, it.online) },
            rep.occupancy, rep.cents, rep.bookings, rep.online, rep.tournamentHours, rep.firstHour, rep.heat,
            rep.top.map { (who, n) -> DeskTop(Players.byId(people, who)?.name ?: who, n) },
        ))
    }

    // --- Server delle prenotazioni ---

    private fun clock(t: Long): Pair<String, Int> {
        val z = Instant.ofEpochMilli(t).atZone(zone())
        return z.toLocalDate().toString() to z.hour * 60 + z.minute
    }

    private fun online(m: String, p: List<String>): Resp {
        val conf = data.tx { data.serverConf() }
        if (!conf.paired) throw Fail(409, "NOT_PAIRED")
        return when {
            m == "GET" && p == listOf("online") -> onlineOut()
            m == "POST" && p == listOf("online", "sync") -> {
                data.serverSync()?.let { throw Fail(502, it) }
                onlineOut()
            }
            m == "DELETE" && p.size == 3 && p[1] in setOf("waits", "seeks") -> {
                try { data.serverRemove(if (p[1] == "waits") "wait" else "seek", p[2]) } catch (e: Exception) {
                    throw Fail(502, (e as? com.tennis.scoremanager.data.ClubServerError)?.code ?: "NET")
                }
                onlineOut()
            }
            else -> throw Fail(404, "NOT_FOUND")
        }
    }

    private fun onlineOut(): Resp {
        val (conf, courts, people) = data.tx { Triple(data.serverConf(), data.courts(), data.players()) }
        var stateError: String? = null
        val st = try { data.serverState() } catch (e: Exception) {
            stateError = (e as? com.tennis.scoremanager.data.ClubServerError)?.code ?: "NET"; null
        }
        val now = data.now()
        val names = (st?.players.orEmpty().associate { it.id to it.name }) + people.associate { it.id to it.name }
        return ok(DeskOnline.serializer(), DeskOnline(
            conf.paired, conf.club, conf.url, conf.publicUrl, conf.lastSync.takeIf { it > 0 }?.let { day(it) }, conf.lastSync.takeIf { it > 0 }?.let { clock(it).second },
            conf.error, conf.rules, conf.payInfo, conf.alerts, courts,
            st?.bookings.orEmpty().filter { it.end > now }.map { b ->
                val (d, mm) = clock(b.start)
                DeskOnlineBooking(b.id, b.court, d, mm, b.minutes, b.name, b.cents, b.createdAt.takeIf { it > 0 }?.let { day(it) })
            },
            st?.waits.orEmpty().map { w -> val (d, mm) = clock(w.start); DeskWait(w.id, names[w.player] ?: w.player, w.court, d, mm, w.minutes, w.freedCourt) },
            st?.seeks.orEmpty().map { k -> DeskSeek(k.id, k.everyone.map { it.name }, k.date, k.fromMin, k.toMin, k.doubles, k.level, k.note, k.need, k.booking != null, k.noCourt) },
            stateError,
        ))
    }

    private fun saveRules(i: DeskRulesIn): Resp {
        val r0 = i.rules
        if (r0.courts.any { it !in data.courts() }) throw Fail(404, "NOT_FOUND")
        val rules = r0.copy(
            daysAhead = r0.daysAhead.coerceIn(0, 60), maxFuture = r0.maxFuture.coerceIn(1, 20), maxMinutes = r0.maxMinutes.coerceIn(30, 480),
            cancelHours = r0.cancelHours.coerceIn(0, 168), noticeMinutes = r0.noticeMinutes.coerceIn(0, 24 * 60), remindHours = r0.remindHours.coerceIn(0, 48),
        )
        data.saveServerRules(rules, text(i.payInfo, 300), i.alerts)
        return done()
    }

    // --- Impostazioni del circolo ---

    private fun settings(): Resp {
        val st = data.plan().settings
        val today = CourtPlans.dayStart(data.now())
        val bookings = data.plan().bookings
        return ok(DeskSettings.serializer(), DeskSettings(
            data.club, st.openHour, st.closeHour, st.slotMinutes, st.centsPerHour, st.centsPerHourLights, st.lightsFrom, st.tournamentMinutes,
            data.courts().map { n ->
                val c = st.info(n)
                DeskCourtInfo(n, c.surface, c.covered, c.lights, c.heating, c.centsPerHour, c.centsPerHourLights, c.heatingCentsPerHour,
                    bookings.count { it.court == n && it.start >= today }, c.photos.size)
            },
            data.payTypes().map { DeskPayTypeFull(it.id, it.name, it.details, it.online, it.method) },
        ))
    }

    private fun price(c: Int): Int = if (c in 0..MAX_CENTS) c else throw Fail(400, "BAD_AMOUNT")

    private fun saveHours(i: DeskHoursIn): Resp {
        if (i.openHour !in 0..23 || i.closeHour !in 1..24 || i.closeHour <= i.openHour || i.lightsFrom !in 0..24) throw Fail(400, "BAD_TIME")
        if (i.slotMinutes !in listOf(30, 60, 90) || i.tournamentMinutes !in listOf(60, 90, 120)) throw Fail(400, "BAD_TIME")
        val st = data.plan().settings
        data.saveCourtSettings(st.copy(
            openHour = i.openHour, closeHour = i.closeHour, slotMinutes = i.slotMinutes, centsPerHour = price(i.centsPerHour),
            centsPerHourLights = price(i.centsPerHourLights), lightsFrom = i.lightsFrom, tournamentMinutes = i.tournamentMinutes,
        ))
        return settings()
    }

    /** Un campo nuovo ([DeskCourtIn.old] vuoto) o cambiato, anche di nome; le foto restano quelle del telefono. */
    private fun saveCourt(i: DeskCourtIn): Resp {
        val x = i.info
        val name = text(x.name, 40)
        if (name.isEmpty()) throw Fail(400, "NAME_REQUIRED")
        listOfNotNull(x.centsPerHour, x.centsPerHourLights, x.heatingCentsPerHour).forEach(::price)
        val courts = data.courts()
        val old = i.old.takeIf { it.isNotEmpty() }
        if (old != null && old !in courts) throw Fail(404, "NOT_FOUND")
        val clash = courts.any { it != old && it.equals(name, ignoreCase = true) }
        if (clash) throw Fail(409, "COURT_EXISTS")
        val base = data.plan().settings.info(old ?: name)
        val info = base.copy(
            name = name, surface = x.surface, covered = x.covered, lights = x.lights, heating = x.heating,
            centsPerHour = x.centsPerHour, centsPerHourLights = x.centsPerHourLights, heatingCentsPerHour = if (x.heating) x.heatingCentsPerHour else 0,
        )
        if (old == null) { if (!data.addCourt(info)) throw Fail(409, "COURT_EXISTS") }
        else {
            if (old != name && !data.renameCourt(old, name)) throw Fail(409, "COURT_EXISTS")
            data.saveCourtInfo(info.copy(photos = data.plan().settings.info(name).photos))
        }
        return settings()
    }

    private fun savePayTypes(list: List<DeskPayTypeFull>): Resp {
        val old = data.payTypes().associateBy { it.id }
        val clean = list.map { t ->
            val name = text(t.name, 40)
            if (name.isEmpty()) throw Fail(400, "NAME_REQUIRED")
            // Il tipo per il registro resta quello che era (i nuovi sono "altro").
            PayType(t.id.takeIf { it in old } ?: Fees.newId("t"), name, text(t.details, 200), t.online, old[t.id]?.method ?: PayMethod.OTHER)
        }
        if (clean.isEmpty()) throw Fail(400, "BAD_REQUEST")
        if (clean.map { it.name.lowercase() }.toSet().size != clean.size) throw Fail(409, "PAYTYPE_EXISTS")
        data.savePayTypes(clean)
        return settings()
    }

    // --- Squadre e campionati ---

    private fun team(id: String): Team = data.teams().firstOrNull { it.id == id } ?: throw Fail(404, "NOT_FOUND")

    private fun teamOut(t: Team): DeskTeam {
        val people = data.players()
        val rec = Teams.record(t)
        return DeskTeam(
            t.id, t.name, t.championship, t.season, t.captain, t.roster.map { DeskTeamPlayer(it, Players.byId(people, it)?.name ?: it) },
            t.singles, t.doubles, t.fitp, rec.won, rec.lost, rec.drawn,
            t.fixtures.sortedBy { it.at }.map { f ->
                val start = CourtPlans.dayStart(f.at)
                val (w, l) = Teams.result(f)
                val problems = t.roster.associateWith { id ->
                    Players.byId(people, id)?.let { pp -> Teams.problems(t, pp, f.at).map { DeskProblem(pp.name, it.name, ClubTournaments.blocking(it)) } } ?: listOf(DeskProblem(id, "NO_PLAYER", true))
                }.filterValues { it.isNotEmpty() }
                DeskFixture(
                    f.id, f.opponent, day(f.at)!!, ((f.at - start) / 60_000L).toInt(), f.home, f.place, f.meeting, f.courts, f.hours, f.note, f.availability, f.callUp,
                    f.rubbers.map { DeskRubber(it.doubles, it.ours, it.theirs, it.score, it.won) }, w, l, Teams.decided(f), Teams.outcome(f), problems,
                )
            },
        )
    }

    private fun teamSaved(t: Team): Resp { data.saveTeam(t); return ok(DeskTeam.serializer(), teamOut(team(t.id))) }

    private fun saveTeam(old: Team?, i: DeskTeamIn): Resp {
        val name = text(i.name, 80)
        if (name.isEmpty()) throw Fail(400, "NAME_REQUIRED")
        val base = old ?: Team(Teams.newId("t"), name, createdAt = data.now())
        val captain = i.captain?.takeIf { it.isNotEmpty() }?.let { id -> if (id in base.roster) id else throw Fail(400, "BAD_REQUEST") }
        val t = base.copy(
            name = name, championship = text(i.championship, 80), season = text(i.season, 30), captain = captain,
            singles = i.singles.coerceIn(0, 9), doubles = i.doubles.coerceIn(0, 9), fitp = i.fitp,
        )
        data.saveTeam(t)
        return ok(DeskId.serializer(), DeskId(t.id))
    }

    private fun teamRoute(m: String, p: List<String>, r: Req): Resp {
        val t = team(p[0])
        val rest = p.drop(1)
        return when {
            m == "PUT" && rest.isEmpty() -> saveTeam(t, read(DeskTeamIn.serializer(), r))
            m == "DELETE" && rest.isEmpty() -> { data.deleteTeam(t); done() }
            m == "POST" && rest == listOf("roster") -> {
                val people = data.players()
                val add = read(DeskIds.serializer(), r).ids.map { Players.byId(people, it)?.id ?: throw Fail(404, "NOT_FOUND") }.filter { it !in t.roster }.distinct()
                teamSaved(t.copy(roster = t.roster + add))
            }
            m == "DELETE" && rest.size == 2 && rest[0] == "roster" -> {
                val id = rest[1]
                teamSaved(t.copy(roster = t.roster - id, captain = t.captain.takeIf { it != id }, fixtures = t.fixtures.map { f -> f.copy(callUp = f.callUp - id) }))
            }
            m == "POST" && rest == listOf("fixtures") -> saveFixture(t, null, read(DeskFixtureIn.serializer(), r))
            rest.size >= 2 && rest[0] == "fixtures" -> {
                val f = t.fixtures.firstOrNull { it.id == rest[1] } ?: throw Fail(404, "NOT_FOUND")
                fixtureRoute(m, t, f, rest.drop(2), r)
            }
            else -> throw Fail(404, "NOT_FOUND")
        }
    }

    private fun withFixture(t: Team, f: Fixture): Resp = teamSaved(t.copy(fixtures = (t.fixtures.filter { it.id != f.id } + f).sortedBy { it.at }))

    private fun saveFixture(t: Team, old: Fixture?, i: DeskFixtureIn): Resp {
        val opponent = text(i.opponent, 80)
        if (opponent.isEmpty()) throw Fail(400, "NAME_REQUIRED")
        if (i.startMin !in 0 until 24 * 60) throw Fail(400, "BAD_TIME")
        val at = dayStart(i.date) + i.startMin * 60_000L
        val courts = data.courts()
        if (i.courts.any { it !in courts }) throw Fail(404, "NOT_FOUND")
        val base = old ?: Fixture(Teams.newId("f"), opponent, at, rubbers = Teams.emptyRubbers(t))
        val f = base.copy(
            opponent = opponent, at = at, home = i.home, place = text(i.place, 120), meeting = text(i.meeting, 120),
            courts = if (i.home) i.courts.distinct() else emptyList(), hours = i.hours.coerceIn(1, 12), note = text(i.note, 500),
        )
        return withFixture(t, f)
    }

    private fun fixtureRoute(m: String, t: Team, f: Fixture, rest: List<String>, r: Req): Resp = when {
        m == "PUT" && rest.isEmpty() -> saveFixture(t, f, read(DeskFixtureIn.serializer(), r))
        m == "DELETE" && rest.isEmpty() -> teamSaved(t.copy(fixtures = t.fixtures - f))
        m == "POST" && rest == listOf("availability") -> {
            val i = read(DeskAnswerIn.serializer(), r)
            if (i.player !in t.roster) throw Fail(404, "NOT_FOUND")
            withFixture(t, f.copy(availability = if (i.answer == null) f.availability - i.player else f.availability + (i.player to i.answer)))
        }
        m == "POST" && rest == listOf("callup") -> {
            val ids = read(DeskIds.serializer(), r).ids
            if (ids.any { it !in t.roster }) throw Fail(400, "BAD_REQUEST")
            withFixture(t, f.copy(callUp = t.roster.filter { it in ids }))
        }
        m == "POST" && rest == listOf("rubber") -> {
            val i = read(DeskRubberIn.serializer(), r)
            val old = f.rubbers.getOrNull(i.index) ?: throw Fail(404, "NOT_FOUND")
            val size = if (old.doubles) 2 else 1
            if (i.ours.size > size || i.theirs.size > size || i.ours.any { it.isNotEmpty() && it !in t.roster }) throw Fail(400, "BAD_REQUEST")
            val withPlayers = f.copy(rubbers = f.rubbers.mapIndexed { k, x ->
                if (k == i.index) x.copy(ours = i.ours.filter { it.isNotEmpty() }, theirs = i.theirs.map { text(it, 80) }.filter { it.isNotEmpty() }) else x
            })
            val score = text(i.score, 60)
            withFixture(t, if (score.isEmpty() && i.won == null) withPlayers.copy(rubbers = withPlayers.rubbers.mapIndexed { k, x -> if (k == i.index) x.copy(score = "", won = null) else x })
                else Teams.setRubber(withPlayers, i.index, score, i.won))
        }
        m == "POST" && rest == listOf("notice") -> {
            val kind = read(DeskNoticeIn.serializer(), r).kind
            if (kind !in setOf("AVAILABILITY", "MISSING", "CALLUP", "RESULT")) throw Fail(400, "BAD_REQUEST")
            val who = when (kind) { "MISSING" -> Teams.noAnswer(t, f); "CALLUP" -> f.callUp; else -> t.roster }
            if (who.isEmpty()) throw Fail(409, "NOBODY")
            data.teamNotice(t, f, kind)
            done()
        }
        else -> throw Fail(404, "NOT_FOUND")
    }

    // --- Scuola tennis ---

    private fun course(id: String): Course = data.courses().firstOrNull { it.id == id } ?: throw Fail(404, "NOT_FOUND")

    /** I mesi della stagione ("2026-11"), o la stagione sola (""), con la voce della quota nel registro. */
    private fun feeMonths(c: Course): List<Pair<String, Pair<Int, Int>?>> =
        if (c.feeKind == CourseFee.SEASON) listOf("" to null) else Courses.months(c).map { (y, m) -> "%04d-%02d".format(y, m + 1) to (y to m) }

    private fun courseOut(c: Course): DeskCourse {
        val people = data.players(); val book = data.book(); val now = data.now()
        val students = c.students.map { id ->
            val p = Players.byId(people, id)
            val (yes, all) = Courses.attendanceOf(c, id, now)
            DeskStudent(id, p?.name ?: id, yes, all, p?.let { pp -> Fees.of(book, pp).filter { it.kind == FeeKind.COURSE && it.label.startsWith(c.name) }.sumOf { it.left } } ?: 0)
        }
        val lessons = Courses.lessons(c).map { l ->
            val start = CourtPlans.dayStart(l.start)
            DeskLesson(l.key, day(l.start)!!, ((l.start - start) / 60_000L).toInt(), l.minutes, l.court, l.key in c.cancelled, l.key in c.attendance, c.attendance[l.key].orEmpty().toList())
        }
        val keys = c.students.flatMap { id -> listOf(id) + (Players.byId(people, id)?.aliases.orEmpty()) }.toSet()
        val fees = if (c.feeCents <= 0) emptyList() else feeMonths(c).map { (key, month) ->
            val label = data.courseFeeLabel(c, month)
            val mine = book.charges.filter { it.label == label && !it.cancelled && it.player in keys }
            DeskCourseFee(key, label, mine.size, mine.count { it.settled })
        }
        return DeskCourse(
            c.id, c.name, c.level, c.coach, c.coachName, day(c.from)!!, day(c.to)!!, c.maxStudents, c.feeCents, c.feeKind, c.note,
            c.slots.map { DeskSlot(it.weekday, it.startMin, it.minutes, it.court) }, students, lessons, fees,
        )
    }

    private fun courseRoute(m: String, p: List<String>, r: Req): Resp {
        val c = course(p[0])
        val rest = p.drop(1)
        return when {
            m == "PUT" && rest.isEmpty() -> saveCourse(c, read(DeskCourseIn.serializer(), r))
            m == "DELETE" && rest.isEmpty() -> { data.deleteCourse(c); done() }
            m == "POST" && rest == listOf("students") -> {
                val people = data.players()
                val add = read(DeskIds.serializer(), r).ids.map { Players.byId(people, it)?.id ?: throw Fail(404, "NOT_FOUND") }.filter { it !in c.students }.distinct()
                if (c.maxStudents > 0 && c.students.size + add.size > c.maxStudents) throw Fail(409, "COURSE_FULL")
                courseSaved(c.copy(students = c.students + add))
            }
            m == "DELETE" && rest.size == 2 && rest[0] == "students" -> courseSaved(c.copy(students = c.students - rest[1]))
            m == "POST" && rest == listOf("lesson") -> {
                val i = read(DeskMarkIn.serializer(), r)
                if (Courses.lessons(c).none { it.key == i.key }) throw Fail(404, "NOT_FOUND")
                var n = if (i.cancelled != null) Courses.setCancelled(c, i.key, i.cancelled) else c
                if (i.cancelled == null) n = Courses.mark(n, i.key, i.present.filter { it in c.students }.toSet())
                courseSaved(n)
            }
            m == "POST" && rest == listOf("charge") -> {
                if (c.feeCents <= 0) throw Fail(409, "NO_FEE")
                val key = read(DeskChargeCourseIn.serializer(), r).month
                val month = feeMonths(c).firstOrNull { it.first == key } ?: throw Fail(404, "NOT_FOUND")
                ok(DeskCount.serializer(), DeskCount(data.chargeCourse(c, month.second)))
            }
            else -> throw Fail(404, "NOT_FOUND")
        }
    }

    private fun courseSaved(c: Course): Resp { data.saveCourse(c); return ok(DeskCourse.serializer(), courseOut(course(c.id))) }

    private fun saveCourse(old: Course?, i: DeskCourseIn): Resp {
        val name = text(i.name, 80)
        if (name.isEmpty()) throw Fail(400, "NAME_REQUIRED")
        val from = noon(i.from) ?: throw Fail(400, "BAD_DATE"); val to = noon(i.to) ?: throw Fail(400, "BAD_DATE")
        if (to < from || to - from > 400L * 86_400_000L) throw Fail(400, "BAD_DATE")
        if (i.feeCents != 0) cents(i.feeCents)
        val courts = data.courts()
        val slots = i.slots.map { s ->
            if (s.weekday !in 1..7 || s.startMin !in 0 until 24 * 60 || s.minutes !in 15..8 * 60) throw Fail(400, "BAD_TIME")
            if (s.court.isNotEmpty() && s.court !in courts) throw Fail(404, "NOT_FOUND")
            CourseSlot(s.weekday, s.startMin, s.minutes, s.court)
        }
        val coach = i.coach?.let { id -> Players.byId(data.players(), id)?.id ?: throw Fail(404, "NOT_FOUND") }
        val base = old ?: Course(Courses.newId("k"), name, from = from, to = to, createdAt = data.now())
        val c = base.copy(
            name = name, level = text(i.level, 60), coach = coach, coachName = if (coach != null) "" else text(i.coachName, 80), from = from, to = to,
            maxStudents = i.maxStudents.coerceIn(0, 200), feeCents = i.feeCents, feeKind = i.feeKind, note = text(i.note, 500), slots = slots,
        )
        data.saveCourse(c)
        return ok(DeskId.serializer(), DeskId(c.id))
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
