// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

// I campi, le prenotazioni e i blocchi sono nel modulo courtcore (CourtTypes.kt), con orari e prezzi (CourtTime).

/** Una casella occupata nel planning: una prenotazione, una partita di torneo o un blocco (non si cambiano da qui). */
data class Occupied(
    val court: String, val start: Long, val end: Long, val booking: CourtBooking? = null, val tournament: ClubTournament? = null, val match: CTMatch? = null,
    val block: CourtBlock? = null,
)

object CourtPlans {
    fun newId(prefix: String): String = prefix + java.util.UUID.randomUUID().toString().take(12)

    fun dayStart(t: Long): Long = CourtTime.dayStart(t)
    fun at(day: Long, hour: Int, minute: Int = 0): Long = CourtTime.at(day, hour, minute)
    fun addDays(t: Long, days: Int): Long = CourtTime.addDays(t, days)

    fun slots(day: Long, s: CourtSettings): List<Long> = CourtTime.slots(day, s)

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

    fun weekday(t: Long): Int = CourtTime.weekday(t)

    fun blockActive(b: CourtBlock, day: Long): Boolean = CourtTime.blockActive(b, day)

    /** I blocchi del giorno, campo per campo ([courts] = i campi del circolo, per i blocchi "tutti i campi"). */
    fun blocksOn(plan: CourtPlan, day: Long, courts: List<String>?): List<Occupied> = plan.blocks.filter { blockActive(it, day) }.flatMap { b ->
        val (start, end) = CourtTime.blockSpan(b, day)
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

    fun price(s: CourtSettings, start: Long, minutes: Int, kind: BookingKind, court: String = "", heating: Boolean = false): Int =
        CourtTime.price(s, start, minutes, kind, court, heating)

    fun needsLights(s: CourtSettings, start: Long, minutes: Int): Boolean = CourtTime.needsLights(s, start, minutes)

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

    fun split(cents: Int, n: Int): List<Int> = CourtTime.split(cents, n)

    /** Quanto è occupato ogni campo nel giorno (minuti), per vedere a colpo d'occhio i campi liberi. */
    fun busyMinutes(occ: List<Occupied>, court: String): Int = occ.filter { it.court == court && it.block == null }.sumOf { ((it.end - it.start) / 60_000L).toInt() }
}
