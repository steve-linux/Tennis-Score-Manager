// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import kotlinx.serialization.Serializable

/** La risposta di un giocatore alla richiesta di disponibilità per un incontro. */
@Serializable
enum class Availability { YES, NO, MAYBE }

/**
 * Una partita dell'incontro: singolare o doppio, i nostri ([ours], giocatori del database) contro i loro (nomi
 * scritti), il punteggio dal nostro lato e chi ha vinto ([won] true = noi; null = da giocare).
 */
@Serializable
data class Rubber(
    val doubles: Boolean = false,
    val ours: List<String> = emptyList(),
    val theirs: List<String> = emptyList(),
    val score: String = "",
    val won: Boolean? = null,
    /** La partita segnata col segnapunti (MatchRecord.id). */
    val record: String? = null,
    /** Non giocata, vinta a tavolino: il punteggio è quello del regolamento (6-0 6-0). */
    val walkover: Boolean = false,
)

/**
 * Un incontro del campionato: avversario, giorno e ora, in casa o in trasferta, dove e il ritrovo, le partite
 * ([rubbers], tanti singolari e doppi quanti ne prevede la formula), le disponibilità e i convocati. In casa si
 * possono tenere dei campi ([courts]): nel planning diventano non prenotabili per l'incontro.
 */
@Serializable
data class Fixture(
    val id: String,
    val opponent: String,
    val at: Long,
    val home: Boolean = true,
    val place: String = "",
    val meeting: String = "",
    val rubbers: List<Rubber> = emptyList(),
    val availability: Map<String, Availability> = emptyMap(),
    val callUp: List<String> = emptyList(),
    val courts: List<String> = emptyList(),
    /** Ore per cui i campi restano occupati in casa. */
    val hours: Int = 6,
    val note: String = "",
)

/** Una squadra del circolo: campionato, capitano, rosa (nell'ordine della lista) e incontri. */
@Serializable
data class Team(
    val id: String,
    val name: String,
    val championship: String = "",
    val season: String = "",
    val captain: String? = null,
    val roster: List<String> = emptyList(),
    /** La formula degli incontri: quanti singolari e quanti doppi. */
    val singles: Int = 4,
    val doubles: Int = 2,
    /** Campionato FITP: in rosa e tra i convocati si controllano tessera agonistica e certificato. */
    val fitp: Boolean = true,
    val fixtures: List<Fixture> = emptyList(),
    val createdAt: Long = 0,
)

object Teams {
    fun newId(prefix: String): String = prefix + java.util.UUID.randomUUID().toString().take(12)

    /** Le partite vuote secondo la formula: prima i singolari, poi i doppi. */
    fun emptyRubbers(t: Team): List<Rubber> = List(t.singles.coerceAtLeast(0)) { Rubber(false) } + List(t.doubles.coerceAtLeast(0)) { Rubber(true) }

    /** Il risultato dell'incontro: partite vinte e perse da noi (quelle da giocare non contano). */
    fun result(f: Fixture): Pair<Int, Int> = f.rubbers.count { it.won == true } to f.rubbers.count { it.won == false }

    /** Incontro finito: tutte le partite hanno un vincitore (o una squadra ha già vinto la maggioranza). */
    fun decided(f: Fixture): Boolean {
        if (f.rubbers.isEmpty()) return false
        val (w, l) = result(f)
        val half = f.rubbers.size / 2
        return f.rubbers.all { it.won != null } || w > half || l > half
    }

    /** Vinto (true), perso (false), pari o ancora aperto (null). */
    fun outcome(f: Fixture): Boolean? {
        if (!decided(f)) return null
        val (w, l) = result(f)
        return if (w == l) null else w > l
    }

    /** Il bilancio della squadra: incontri vinti, persi, pareggiati. */
    data class Record(val won: Int, val lost: Int, val drawn: Int)

    fun record(t: Team): Record {
        val done = t.fixtures.filter { decided(it) }
        return Record(done.count { outcome(it) == true }, done.count { outcome(it) == false }, done.count { outcome(it) == null })
    }

    /**
     * Perché un giocatore non può giocare l'incontro del giorno [day] in un campionato FITP: tessera agonistica
     * dell'anno e certificato agonistico valido quel giorno (gli stessi controlli dei tornei FITP).
     */
    fun problems(t: Team, p: PlayerProfile, day: Long): List<ClubTournaments.Problem> {
        if (!t.fitp) return if (p.member?.let { Members.medicalValid(it, day) } == true) emptyList() else listOf(ClubTournaments.Problem.NO_MEDICAL)
        val fake = ClubTournament("", "", kind = CTKind.FITP, start = day, requirePaid = false)
        return ClubTournaments.problems(fake, p, ClubBook(), day)
    }

    /** Chi ha detto di sì (prima) o forse, nell'ordine della rosa: i candidati per la convocazione. */
    fun candidates(t: Team, f: Fixture): List<String> =
        t.roster.filter { f.availability[it] == Availability.YES } + t.roster.filter { f.availability[it] == Availability.MAYBE }

    /** Chi non ha ancora risposto alla disponibilità. */
    fun noAnswer(t: Team, f: Fixture): List<String> = t.roster.filter { it !in f.availability }

    /** Il risultato di una partita, dal punteggio se non lo si dice (dal nostro lato). */
    fun setRubber(f: Fixture, index: Int, score: String, won: Boolean?, record: String? = null, walkover: Boolean = false): Fixture {
        val w = won ?: ClubTournaments.winnerFromScore(score)?.let { it == "a" }
        val sc = if (walkover && w != null) walkoverScore(w) else score.trim()
        return f.copy(rubbers = f.rubbers.mapIndexed { i, r -> if (i == index) r.copy(score = sc, won = w, record = record ?: r.record, walkover = walkover && w != null) else r })
    }

    /** Il punteggio di una partita vinta a tavolino, dal nostro lato: 6-0 6-0 o 0-6 0-6. */
    fun walkoverScore(won: Boolean): String = if (won) "6-0 6-0" else "0-6 0-6"

    /** I periodi non prenotabili degli incontri in casa con dei campi tenuti. */
    fun blocks(t: Team): List<CourtBlock> = t.fixtures.filter { it.home && it.courts.isNotEmpty() }.map { f ->
        val startMin = java.util.Calendar.getInstance().apply { timeInMillis = f.at }.let { it.get(java.util.Calendar.HOUR_OF_DAY) * 60 + it.get(java.util.Calendar.MINUTE) }
        CourtBlock(
            id = "team-${t.id}-${f.id}", label = "${t.name} – ${f.opponent}", reason = BlockReason.TOURNAMENT, courts = f.courts,
            from = CourtPlans.dayStart(f.at), to = CourtPlans.dayStart(f.at), startMin = startMin, endMin = (startMin + f.hours * 60).coerceAtMost(24 * 60), team = t.id,
        )
    }
}
