// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import kotlinx.serialization.Serializable

/**
 * Le prenotazioni online dei giocatori, col server del circolo (facoltativo: senza server si prenota in
 * segreteria come sempre). La segreteria manda al server il planning ([ClubSync]); il server accetta le
 * prenotazioni dei giocatori invitati con queste regole, le stesse che l'app usa per mostrarle.
 */

/** Un periodo già occupato in segreteria (prenotazione, lezione, partita di torneo): il server non sa da chi. */
@Serializable
data class Busy(val court: String, val start: Long, val end: Long)

/** Le regole per i giocatori, decise dal circolo. */
@Serializable
data class BookingRules(
    /** Le prenotazioni online sono aperte (spente = si vede il planning ma non si prenota). */
    val open: Boolean = true,
    /** Fin quanti giorni avanti si prenota (0 = solo oggi). */
    val daysAhead: Int = 7,
    /** Quante prenotazioni ancora da giocare può avere ogni giocatore. */
    val maxFuture: Int = 2,
    /** La prenotazione più lunga. */
    val maxMinutes: Int = 120,
    /** Fino a quante ore prima si disdice da soli (dopo, si chiama la segreteria). */
    val cancelHours: Int = 24,
    /** Quanti minuti prima dell'inizio si può ancora prenotare. */
    val noticeMinutes: Int = 30,
    /** I campi prenotabili online (vuoto = tutti). */
    val courts: List<String> = emptyList(),
)

/** Quello che la segreteria manda al server: il circolo, i campi, gli orari e prezzi, i blocchi, l'occupato e le regole. */
@Serializable
data class ClubSync(
    val club: String = "",
    /** Il fuso orario del circolo (es. Europe/Rome): giorni e orari si contano lì, anche se il giocatore è altrove. */
    val timeZone: String = "Europe/Rome",
    val courts: List<String> = emptyList(),
    val settings: CourtSettings = CourtSettings(),
    val blocks: List<CourtBlock> = emptyList(),
    val busy: List<Busy> = emptyList(),
    val rules: BookingRules = BookingRules(),
    /** Come si paga (testo libero: "al campo, Satispay…"), da mostrare ai giocatori. */
    val payInfo: String = "",
)

/** Una prenotazione fatta online da un giocatore: [player] è l'id del giocatore nel database dell'app. */
@Serializable
data class OnlineBooking(
    val id: String,
    val court: String,
    val start: Long,
    val minutes: Int,
    val player: String,
    val name: String,
    val cents: Int = 0,
    val lights: Boolean = false,
    val heating: Boolean = false,
    val createdAt: Long = 0,
) {
    val end: Long get() = start + minutes * 60_000L
}

/** Perché una prenotazione online non si può fare. */
@Serializable
enum class BookingProblem { CLOSED, UNKNOWN_COURT, PAST, TOO_SOON, TOO_FAR, NOT_SLOT, TOO_LONG, BLOCKED, TAKEN, TOO_MANY }

/** Perché una prenotazione online non si può disdire. */
@Serializable
enum class CancelProblem { NOT_FOUND, NOT_YOURS, TOO_LATE }

/** Lo stato di una casella per chi guarda: libera, occupata, sua, non prenotabile (blocco o campo non online), passata. */
@Serializable
enum class CellState { FREE, BUSY, MINE, BLOCKED, PAST }

@Serializable
data class Cell(val court: String, val start: Long, val state: CellState, val booking: String? = null)

object CourtOnline {
    /** I campi che si prenotano online. */
    fun bookableCourts(sync: ClubSync): List<String> = if (sync.rules.courts.isEmpty()) sync.courts else sync.courts.filter { it in sync.rules.courts }

    private fun overlaps(court: String, start: Long, end: Long, c: String, s: Long, e: Long) = court == c && s < end && e > start

    /** Il campo è occupato tra [start] e [end] (in segreteria o online)? */
    fun taken(sync: ClubSync, bookings: List<OnlineBooking>, court: String, start: Long, end: Long, except: String? = null): Boolean =
        sync.busy.any { overlaps(court, start, end, it.court, it.start, it.end) } ||
            bookings.any { it.id != except && overlaps(court, start, end, it.court, it.start, it.end) }

    fun blocked(sync: ClubSync, court: String, start: Long, end: Long): Boolean =
        sync.blocks.any { CourtTime.blocks(it, court, start, end, BookingKind.PLAY) }

    /** Le prenotazioni del giocatore ancora da giocare. */
    fun future(bookings: List<OnlineBooking>, player: String, now: Long): List<OnlineBooking> =
        bookings.filter { it.player == player && it.end > now }.sortedBy { it.start }

    /** Si può prenotare? null = sì, altrimenti il primo motivo per cui no. */
    fun check(sync: ClubSync, bookings: List<OnlineBooking>, player: String, court: String, start: Long, minutes: Int, now: Long): BookingProblem? {
        val r = sync.rules
        val s = sync.settings
        val end = start + minutes * 60_000L
        return when {
            !r.open -> BookingProblem.CLOSED
            court !in bookableCourts(sync) -> BookingProblem.UNKNOWN_COURT
            start < now -> BookingProblem.PAST
            start < now + r.noticeMinutes * 60_000L -> BookingProblem.TOO_SOON
            CourtTime.dayStart(start) > CourtTime.dayStart(CourtTime.addDays(now, r.daysAhead.coerceAtLeast(0))) -> BookingProblem.TOO_FAR
            start !in CourtTime.slots(start, s) || minutes < s.slotMinutes || minutes % s.slotMinutes != 0 ||
                end > CourtTime.at(start, s.closeHour) -> BookingProblem.NOT_SLOT
            minutes > maxOf(r.maxMinutes, s.slotMinutes) -> BookingProblem.TOO_LONG
            blocked(sync, court, start, end) -> BookingProblem.BLOCKED
            taken(sync, bookings, court, start, end) -> BookingProblem.TAKEN
            future(bookings, player, now).size >= r.maxFuture.coerceAtLeast(1) -> BookingProblem.TOO_MANY
            else -> null
        }
    }

    /** La prenotazione già fatta online, pronta da salvare (prezzo e luci come in segreteria). */
    fun make(sync: ClubSync, id: String, player: String, name: String, court: String, start: Long, minutes: Int, heating: Boolean, now: Long): OnlineBooking {
        val h = heating && sync.settings.info(court).heating
        return OnlineBooking(
            id = id, court = court, start = start, minutes = minutes, player = player, name = name,
            cents = CourtTime.price(sync.settings, start, minutes, BookingKind.PLAY, court, h),
            lights = CourtTime.needsLights(sync.settings, start, minutes) && sync.settings.info(court).lights, heating = h, createdAt = now,
        )
    }

    /** Si può disdire da soli? null = sì. */
    fun checkCancel(sync: ClubSync, bookings: List<OnlineBooking>, player: String, id: String, now: Long): CancelProblem? {
        val b = bookings.firstOrNull { it.id == id } ?: return CancelProblem.NOT_FOUND
        return when {
            b.player != player -> CancelProblem.NOT_YOURS
            b.start - now < sync.rules.cancelHours * 3_600_000L -> CancelProblem.TOO_LATE
            else -> null
        }
    }

    /** Il giorno per il giocatore: ogni casella di ogni campo prenotabile, senza dire chi ha prenotato le altre. */
    fun grid(sync: ClubSync, bookings: List<OnlineBooking>, player: String, day: Long, now: Long): List<Cell> {
        val step = sync.settings.slotMinutes.coerceIn(15, 180) * 60_000L
        val slots = CourtTime.slots(day, sync.settings)
        return bookableCourts(sync).flatMap { court ->
            slots.map { start ->
                val end = start + step
                val mine = bookings.firstOrNull { it.player == player && overlaps(court, start, end, it.court, it.start, it.end) }
                val state = when {
                    mine != null -> CellState.MINE
                    taken(sync, bookings, court, start, end) -> CellState.BUSY
                    blocked(sync, court, start, end) -> CellState.BLOCKED
                    start < now -> CellState.PAST
                    else -> CellState.FREE
                }
                Cell(court, start, state, mine?.id)
            }
        }
    }
}
