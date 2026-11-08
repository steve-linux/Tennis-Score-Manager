// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

/**
 * Le statistiche dei campi per la segreteria, su un periodo: quanto è occupato ogni campo (ore prenotate su quelle
 * prenotabili, cioè aperte meno i periodi non prenotabili), quanto rende, quante prenotazioni sono arrivate online,
 * quando si gioca (giorno della settimana × ora) e chi prenota di più.
 */
object CourtStats {
    data class CourtRow(
        val court: String,
        /** Ore aperte, ore non prenotabili (corsi, tornei, manutenzione), ore prenotate (anche lezioni e tornei). */
        val openHours: Double, val blockedHours: Double, val bookedHours: Double,
        val cents: Long, val bookings: Int, val online: Int,
    ) {
        val bookable: Double get() = (openHours - blockedHours).coerceAtLeast(0.0)
        val occupancy: Double get() = if (bookable > 0) (bookedHours / bookable).coerceIn(0.0, 1.0) else 0.0
    }

    data class Report(
        val courts: List<CourtRow>,
        /** Occupazione da 0 a 1 per giorno della settimana (0 = lunedì) e ora ([firstHour] è la colonna 0). */
        val heat: List<List<Double>>,
        val firstHour: Int,
        /** I giocatori (id del database o nome scritto) con quante prenotazioni, i primi 5. */
        val top: List<Pair<String, Int>>,
        val tournamentHours: Double,
    ) {
        val cents: Long get() = courts.sumOf { it.cents }
        val bookings: Int get() = courts.sumOf { it.bookings }
        val online: Int get() = courts.sumOf { it.online }
        val occupancy: Double get() = courts.sumOf { it.bookable }.let { b -> if (b > 0) courts.sumOf { it.bookedHours } / b else 0.0 }
    }

    private fun overlap(s1: Long, e1: Long, s2: Long, e2: Long): Long = (minOf(e1, e2) - maxOf(s1, s2)).coerceAtLeast(0)

    /** Il periodo da [from] (compreso) a [to] (escluso), giorno per giorno, sui campi [courts]. */
    fun report(plan: CourtPlan, tournaments: List<ClubTournament>, courts: List<String>, from: Long, to: Long): Report {
        val s = plan.settings
        val open = s.openHour.coerceIn(0, 23)
        val close = s.closeHour.coerceIn(open + 1, 24)
        val hours = close - open
        val heatBooked = List(7) { DoubleArray(hours) }
        val heatAvail = List(7) { DoubleArray(hours) }
        val rows = courts.associateWith { doubleArrayOf(0.0, 0.0, 0.0) } // aperte, bloccate, prenotate (minuti)
        var tournamentMin = 0L
        var day = CourtPlans.dayStart(from)
        while (day < to) {
            val occ = CourtPlans.occupied(plan, tournaments, day)
            val wd = CourtPlans.weekday(day) - 1
            for (court in courts) {
                val busy = occ.filter { it.block == null && it.court == court }
                val blocks = occ.filter { CourtPlans.blockCovers(it, court) }
                for (h in 0 until hours) {
                    val hs = CourtPlans.at(day, open + h); val he = hs + 3_600_000L
                    val booked = busy.sumOf { overlap(it.start, it.end, hs, he) }.coerceAtMost(3_600_000L)
                    val blocked = blocks.sumOf { overlap(it.start, it.end, hs, he) }.coerceAtMost(3_600_000L - booked)
                    val r = rows.getValue(court)
                    r[0] += 60.0; r[1] += blocked / 60_000.0; r[2] += booked / 60_000.0
                    heatBooked[wd][h] += booked / 60_000.0
                    heatAvail[wd][h] += (3_600_000L - blocked) / 60_000.0
                }
                tournamentMin += occ.filter { it.tournament != null && it.court == court }.sumOf { overlap(it.start, it.end, day, CourtPlans.addDays(day, 1)) } / 60_000L
            }
            day = CourtPlans.addDays(day, 1)
        }
        val inPeriod = plan.bookings.filter { it.start in from until to && it.court in courts }
        val out = courts.map { ct ->
            val r = rows.getValue(ct)
            val bs = inPeriod.filter { it.court == ct && (it.kind == BookingKind.PLAY || it.kind == BookingKind.LESSON) }
            CourtRow(ct, r[0] / 60, r[1] / 60, r[2] / 60, bs.sumOf { it.cents.toLong() }, bs.size, bs.count { it.online != null })
        }
        val counts = HashMap<String, Int>()
        for (b in inPeriod.filter { it.kind == BookingKind.PLAY }) for (who in b.players + b.names.map { it.trim() }.filter { it.isNotEmpty() }) counts[who] = (counts[who] ?: 0) + 1
        val heat = (0 until 7).map { d -> (0 until hours).map { h -> if (heatAvail[d][h] > 0) (heatBooked[d][h] / heatAvail[d][h]).coerceIn(0.0, 1.0) else 0.0 } }
        return Report(out, heat, open, counts.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }).take(5).map { it.key to it.value }, tournamentMin / 60.0)
    }
}
