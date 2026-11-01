// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import kotlinx.serialization.Serializable
import java.util.Calendar

/** La superficie del campo. */
@Serializable
enum class Surface { CLAY, GRASS, HARD, OTHER }

/**
 * Com'è fatto un campo e quanto costa: coperto o scoperto, luci, riscaldamento; i prezzi all'ora (null = quelli
 * generali del planning) e il supplemento all'ora per il riscaldamento.
 */
@Serializable
data class CourtInfo(
    val name: String,
    val surface: Surface? = null,
    val covered: Boolean = false,
    val lights: Boolean = true,
    val heating: Boolean = false,
    val centsPerHour: Int? = null,
    val centsPerHourLights: Int? = null,
    val heatingCentsPerHour: Int = 0,
    /** Le foto del campo (file nella cartella dell'app, files/court_photos). */
    val photos: List<String> = emptyList(),
)

/** Come si paga la prenotazione: al campo (contanti o POS in segreteria) o online (Satispay, PayPal, bonifico…). */
@Serializable
enum class PayWhere { COURT, ONLINE }

/** Per cosa è occupato il campo: gioco, lezione, chiuso (manutenzione), altro. Le partite dei tornei si aggiungono da sole. */
@Serializable
enum class BookingKind { PLAY, LESSON, CLOSED, OTHER }

/**
 * Una prenotazione: campo, inizio e durata, chi gioca (giocatori del database e/o nomi scritti), prezzo e, se
 * addebitato, gli addebiti nel registro. [series] lega le prenotazioni ripetute ogni settimana.
 */
@Serializable
data class CourtBooking(
    val id: String,
    val court: String,
    val start: Long,
    val minutes: Int = 60,
    val kind: BookingKind = BookingKind.PLAY,
    val players: List<String> = emptyList(),
    val names: List<String> = emptyList(),
    val note: String = "",
    val lights: Boolean = false,
    val cents: Int = 0,
    val charges: List<String> = emptyList(),
    val series: String? = null,
    val heating: Boolean = false,
    val payWhere: PayWhere = PayWhere.COURT,
) {
    val end: Long get() = start + minutes * 60_000L
}

/** Orari del circolo e prezzi: dalle [openHour] alle [closeHour], caselle da [slotMinutes]; prezzi all'ora. */
@Serializable
data class CourtSettings(
    val openHour: Int = 8,
    val closeHour: Int = 23,
    val slotMinutes: Int = 60,
    val centsPerHour: Int = 0,
    val centsPerHourLights: Int = 0,
    /** Da quest'ora servono le luci (e vale il prezzo con le luci). */
    val lightsFrom: Int = 18,
    /** Quanto dura di solito una partita di torneo nel planning. */
    val tournamentMinutes: Int = 90,
    /** Com'è fatto ogni campo (per nome); quelli che mancano hanno i valori generali. */
    val courts: List<CourtInfo> = emptyList(),
) {
    fun info(court: String): CourtInfo = courts.firstOrNull { it.name == court } ?: CourtInfo(court)
}

/** Perché i campi non si possono prenotare: un corso (scuola tennis), un torneo, la manutenzione, altro. */
@Serializable
enum class BlockReason { COURSE, TOURNAMENT, MAINTENANCE, OTHER }

/**
 * Campi non prenotabili: [courts] (vuoto = tutti) dal giorno [from] al giorno [to], nei giorni della settimana
 * [weekdays] (1 = lunedì … 7 = domenica; vuoto = tutti), dalle [startMin] alle [endMin] (minuti dalla mezzanotte).
 * Nei blocchi di un corso si possono mettere le sue lezioni; in quelli di un torneo si vedono le sue partite.
 */
@Serializable
data class CourtBlock(
    val id: String,
    val label: String,
    val reason: BlockReason = BlockReason.OTHER,
    val courts: List<String> = emptyList(),
    val from: Long,
    val to: Long,
    val weekdays: Set<Int> = emptySet(),
    val startMin: Int = 0,
    val endMin: Int = 24 * 60,
    val tournament: String? = null,
    /** Il corso della scuola tennis che l'ha creato (si aggiorna e si toglie col corso). */
    val course: String? = null,
    /** La squadra il cui incontro in casa l'ha creato. */
    val team: String? = null,
)

@Serializable
data class CourtPlan(val settings: CourtSettings = CourtSettings(), val bookings: List<CourtBooking> = emptyList(), val blocks: List<CourtBlock> = emptyList())

/** Una casella occupata nel planning: una prenotazione, una partita di torneo o un blocco (non si cambiano da qui). */
data class Occupied(
    val court: String, val start: Long, val end: Long, val booking: CourtBooking? = null, val tournament: ClubTournament? = null, val match: CTMatch? = null,
    val block: CourtBlock? = null,
)

object CourtPlans {
    fun newId(prefix: String): String = prefix + java.util.UUID.randomUUID().toString().take(12)

    /** Mezzanotte del giorno di [t]. */
    fun dayStart(t: Long): Long = Calendar.getInstance().apply {
        timeInMillis = t; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun at(day: Long, hour: Int, minute: Int = 0): Long = Calendar.getInstance().apply {
        timeInMillis = dayStart(day); set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute)
    }.timeInMillis

    fun addDays(t: Long, days: Int): Long = Calendar.getInstance().apply { timeInMillis = t; add(Calendar.DAY_OF_YEAR, days) }.timeInMillis

    /** Gli inizi delle caselle del giorno, dall'apertura alla chiusura. */
    fun slots(day: Long, s: CourtSettings): List<Long> {
        val step = s.slotMinutes.coerceIn(15, 180)
        val out = mutableListOf<Long>()
        var m = s.openHour * 60
        while (m + step <= s.closeHour * 60) { out += at(day, m / 60, m % 60); m += step }
        return out
    }

    /** Tutto ciò che occupa i campi in quel giorno: prenotazioni e partite di torneo con campo e orario. */
    fun occupied(plan: CourtPlan, tournaments: List<ClubTournament>, day: Long): List<Occupied> {
        val from = dayStart(day); val to = addDays(from, 1)
        val b = plan.bookings.filter { it.start < to && it.end > from }.map { Occupied(it.court, it.start, it.end, booking = it) }
        val t = tournaments.flatMap { tt ->
            tt.matches.filter { m -> m.court.isNotBlank() && m.at != null && m.at in from until to && !m.bye }
                .map { m -> Occupied(m.court, m.at!!, m.at + plan.settings.tournamentMinutes * 60_000L, tournament = tt, match = m) }
        }
        val k = blocksOn(plan, day, courts = null)
        return (b + t + k).sortedBy { it.start }
    }

    /** 1 = lunedì … 7 = domenica. */
    fun weekday(t: Long): Int = Calendar.getInstance().apply { timeInMillis = t }.get(Calendar.DAY_OF_WEEK).let { if (it == Calendar.SUNDAY) 7 else it - 1 }

    /** Il blocco vale quel giorno: dentro le date e in uno dei suoi giorni della settimana. */
    fun blockActive(b: CourtBlock, day: Long): Boolean {
        val d = dayStart(day)
        return d >= dayStart(b.from) && d <= dayStart(b.to) && (b.weekdays.isEmpty() || weekday(d) in b.weekdays)
    }

    /** I blocchi del giorno, campo per campo ([courts] = i campi del circolo, per i blocchi "tutti i campi"). */
    fun blocksOn(plan: CourtPlan, day: Long, courts: List<String>?): List<Occupied> = plan.blocks.filter { blockActive(it, day) }.flatMap { b ->
        val start = at(day, b.startMin / 60, b.startMin % 60)
        val end = at(day, 0) + b.endMin * 60_000L
        val list = if (b.courts.isEmpty()) courts ?: listOf(ALL) else b.courts
        list.map { Occupied(it, start, end, block = b) }
    }

    /** Il campo "tutti": un blocco senza campi scelti vale per ognuno. */
    const val ALL = "*"

    /** Il blocco copre quel campo (anche se è un blocco di tutti i campi). */
    fun blockCovers(o: Occupied, court: String): Boolean = o.block != null && (o.court == court || o.court == ALL)

    /**
     * Si può prenotare lì? I blocchi fermano le prenotazioni, tranne le lezioni nei blocchi dei corsi; le
     * prenotazioni di chiusura (manutenzione) si possono mettere sempre.
     */
    fun blockedBy(plan: CourtPlan, court: String, start: Long, end: Long, kind: BookingKind): List<CourtBlock> =
        blocksOn(plan, start, null).filter { blockCovers(it, court) && it.start < end && it.end > start }
            .mapNotNull { it.block }.filter { b -> kind != BookingKind.CLOSED && !(kind == BookingKind.LESSON && b.reason == BlockReason.COURSE) }.distinct()

    /** Cosa si sovrappone a [court] tra [start] e [end] (escluso [except], la prenotazione che si sta cambiando). */
    fun conflicts(plan: CourtPlan, tournaments: List<ClubTournament>, court: String, start: Long, end: Long, except: String? = null): List<Occupied> =
        occupied(plan, tournaments, start).filter { it.block == null && it.court == court && (except == null || it.booking?.id != except) && it.start < end && it.end > start }

    /**
     * Il prezzo: le ore prima delle luci al prezzo normale, quelle dopo al prezzo con le luci (un campo senza luci
     * resta al prezzo normale), più il riscaldamento se lo si chiede. Prezzi del campo, o quelli generali. Chiuso e
     * altro: 0.
     */
    fun price(s: CourtSettings, start: Long, minutes: Int, kind: BookingKind, court: String = "", heating: Boolean = false): Int {
        if (kind == BookingKind.CLOSED || kind == BookingKind.OTHER) return 0
        val info = s.info(court)
        val base = info.centsPerHour ?: s.centsPerHour
        val dark = if (info.lights) info.centsPerHourLights ?: s.centsPerHourLights else base
        val lightsAt = at(start, s.lightsFrom)
        val end = start + minutes * 60_000L
        val darkMs = (end - maxOf(start, lightsAt)).coerceAtLeast(0)
        val dayMs = (minutes * 60_000L - darkMs).coerceAtLeast(0)
        val heat = if (heating && info.heating) minutes * 60_000L * info.heatingCentsPerHour else 0L
        return Math.round((dayMs * base + darkMs * dark + heat) / 3_600_000.0).toInt()
    }

    /** Servono le luci se la prenotazione arriva oltre l'ora delle luci. */
    fun needsLights(s: CourtSettings, start: Long, minutes: Int): Boolean = start + minutes * 60_000L > at(start, s.lightsFrom)

    /**
     * La stessa prenotazione ogni settimana per [weeks] settimane (lei compresa): restituisce quelle che si possono
     * fare e le date saltate perché il campo è già occupato.
     */
    fun weekly(plan: CourtPlan, tournaments: List<ClubTournament>, b: CourtBooking, weeks: Int): Pair<List<CourtBooking>, List<Long>> {
        val series = b.series ?: newId("s")
        val ok = mutableListOf<CourtBooking>(); val skipped = mutableListOf<Long>()
        var cur = plan
        for (w in 0 until weeks.coerceIn(1, 52)) {
            val start = addDays(b.start, 7 * w)
            val nb = b.copy(id = if (w == 0) b.id else newId("b"), start = start, series = series, charges = emptyList())
            if (conflicts(cur, tournaments, nb.court, nb.start, nb.end, except = b.id.takeIf { w == 0 }).isEmpty() && blockedBy(cur, nb.court, nb.start, nb.end, nb.kind).isEmpty()) {
                ok += nb; cur = cur.copy(bookings = cur.bookings + nb)
            } else skipped += start
        }
        return ok to skipped
    }

    /** Divide [cents] tra [n] persone; i centesimi in più ai primi (10 € in 3: 3,34 + 3,33 + 3,33). */
    fun split(cents: Int, n: Int): List<Int> {
        if (n <= 0) return emptyList()
        val base = cents / n; val extra = cents % n
        return List(n) { base + if (it < extra) 1 else 0 }
    }

    /** Quanto è occupato ogni campo nel giorno (minuti), per vedere a colpo d'occhio i campi liberi. */
    fun busyMinutes(occ: List<Occupied>, court: String): Int = occ.filter { it.court == court && it.block == null }.sumOf { ((it.end - it.start) / 60_000L).toInt() }
}
