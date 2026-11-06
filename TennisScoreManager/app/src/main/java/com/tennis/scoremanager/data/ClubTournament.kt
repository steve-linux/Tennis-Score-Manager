// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import kotlinx.serialization.Serializable
import java.util.Calendar
import kotlin.random.Random

/** Torneo sociale (interno al circolo) o FITP (con le regole della Federazione su tessera, certificato e classifica). */
@Serializable
enum class CTKind { SOCIAL, FITP }

/** Eliminazione diretta, oppure gironi all'italiana e poi tabellone a eliminazione. */
@Serializable
enum class CTFormat { KNOCKOUT, GROUPS }

/** Un iscritto: un giocatore (singolare) o una coppia (doppio); [names] com'erano all'iscrizione. */
@Serializable
data class CTEntry(
    val id: String,
    val players: List<String>,
    val names: List<String>,
    /** Testa di serie scelta a mano (1 = la prima); null = dalla classifica. */
    val seed: Int? = null,
    val at: Long = 0,
)

/**
 * Una partita. [round] 0 = gironi ([group] il girone), da 1 il tabellone (1 = primo turno). [a] e [b] sono gli
 * iscritti (CTEntry.id); nel tabellone null = ancora da decidere, o bye nel primo turno. [score] è dal lato di [a].
 */
@Serializable
data class CTMatch(
    val id: String,
    val round: Int,
    val slot: Int,
    val group: Int? = null,
    val a: String? = null,
    val b: String? = null,
    val winner: String? = null,
    val score: String = "",
    /** Vinta a tavolino (l'altro non si è presentato) o per ritiro durante la partita. */
    val walkover: Boolean = false,
    val retired: Boolean = false,
    val court: String = "",
    val at: Long? = null,
    /** La partita segnata col segnapunti (MatchRecord.id), se è stata giocata dal vivo. */
    val record: String? = null,
) {
    val played: Boolean get() = winner != null
    val bye: Boolean get() = round == 1 && (a == null) != (b == null)
}

@Serializable
data class ClubTournament(
    val id: String,
    val name: String,
    val kind: CTKind = CTKind.SOCIAL,
    val format: CTFormat = CTFormat.KNOCKOUT,
    val doubles: Boolean = false,
    /** Categoria o tabellone ("Singolare maschile", "Over 45", "Lim. 4.1"). */
    val category: String = "",
    val start: Long? = null,
    val end: Long? = null,
    /** Quota d'iscrizione per giocatore (centesimi) e la quota corrispondente nel registro del circolo. */
    val feeCents: Int = 0,
    val feeId: String? = null,
    /** Nel tabellone solo chi ha pagato l'iscrizione. */
    val requirePaid: Boolean = true,
    /** Classifica massima ("4.1" = possono iscriversi i 4.1 e chi è sotto); vuota = open. */
    val rankLimit: String = "",
    /** Torneo FITP: codice del torneo e giudice arbitro. */
    val fitpCode: String = "",
    val referee: String = "",
    /** Gironi: quanti e quanti passano da ciascuno al tabellone. */
    val groups: Int = 2,
    val qualify: Int = 2,
    val entries: List<CTEntry> = emptyList(),
    val matches: List<CTMatch> = emptyList(),
    /** Quando è stato fatto il sorteggio (null = iscrizioni aperte). */
    val drawnAt: Long? = null,
    /** Quante modifiche a mano ha avuto il tabellone dopo il sorteggio: nei tornei FITP resta scritto. */
    val manualEdits: Int = 0,
    val createdAt: Long = 0,
) {
    val drawn: Boolean get() = drawnAt != null
    /** Turni del tabellone (1 = primo turno). */
    val rounds: Int get() = matches.filter { it.round > 0 }.maxOfOrNull { it.round } ?: 0
    /** Il vincitore: chi ha vinto la finale. */
    val champion: String? get() = if (rounds == 0) null else matches.firstOrNull { it.round == rounds }?.winner
    val groupsDone: Boolean get() = matches.filter { it.round == 0 }.let { it.isNotEmpty() && it.all(CTMatch::played) }
}

object ClubTournaments {
    /** Perché un giocatore non può entrare nel tabellone ([blocking]) o cosa conviene controllare. */
    enum class Problem { NOT_MEMBER, NO_MEDICAL, MEDICAL_NOT_AGONISTIC, NO_FITP_CARD, NOT_AGONIST_CARD, RANK_TOO_HIGH, UNPAID }

    fun blocking(p: Problem): Boolean = p != Problem.NOT_MEMBER

    fun newId(prefix: String): String = prefix + java.util.UUID.randomUUID().toString().take(12)

    private fun year(t: Long): Int = Calendar.getInstance().apply { timeInMillis = t }.get(Calendar.YEAR)

    /** Ha pagato l'iscrizione al torneo (o non c'è quota). */
    fun paid(t: ClubTournament, p: PlayerProfile, book: ClubBook): Boolean {
        if (t.feeCents <= 0 || t.feeId == null) return true
        val keys = setOf(p.id) + p.aliases
        val mine = book.charges.filter { it.fee == t.feeId && it.player in keys }
        return mine.isNotEmpty() && mine.all { it.settled }
    }

    /**
     * I problemi di un giocatore per [t], al giorno d'inizio (o a [now] se non c'è). FITP: tessera agonistica
     * dell'anno, certificato agonistico valido, classifica nel limite. Sociale: certificato valido e, solo da
     * controllare, se non è socio. Per tutti: l'iscrizione pagata, se la si chiede.
     */
    fun problems(t: ClubTournament, p: PlayerProfile, book: ClubBook, now: Long): List<Problem> {
        val day = t.start ?: now
        val m = p.member
        val out = mutableListOf<Problem>()
        if (t.kind == CTKind.SOCIAL && m == null) out += Problem.NOT_MEMBER
        if (m == null || !Members.medicalValid(m, day)) out += Problem.NO_MEDICAL
        else if (t.kind == CTKind.FITP && m.medical != MedicalKind.AGONISTIC) out += Problem.MEDICAL_NOT_AGONISTIC
        if (t.kind == CTKind.FITP) {
            if (m == null || m.fitpNumber.isBlank() || m.fitpYear != year(day)) out += Problem.NO_FITP_CARD
            else if (m.fitpCard != FitpCard.AGONIST) out += Problem.NOT_AGONIST_CARD
        }
        Members.rankingValue(t.rankLimit)?.let { limit ->
            // Più basso = più forte: chi è più forte del limite non entra; senza classifica vale come NC.
            if ((Members.rankingValue(m?.ranking.orEmpty()) ?: 4.9) < limit - 1e-9) out += Problem.RANK_TOO_HIGH
        }
        if (t.requirePaid && !paid(t, p, book)) out += Problem.UNPAID
        return out
    }

    /** Gli iscritti che possono entrare nel tabellone (nessun problema grave per nessuno dei giocatori). */
    fun eligible(t: ClubTournament, players: List<PlayerProfile>, book: ClubBook, now: Long): List<CTEntry> = t.entries.filter { e ->
        e.players.all { id -> Players.byId(players, id)?.let { p -> problems(t, p, book, now).none(::blocking) } == true }
    }

    /** La classifica di un iscritto per le teste di serie: la migliore della coppia (senza = 5, in fondo). */
    fun rank(e: CTEntry, players: List<PlayerProfile>): Double =
        e.players.mapNotNull { Players.byId(players, it)?.member?.ranking?.let(Members::rankingValue) }.let { r ->
            if (r.isEmpty()) 5.0 else if (r.size == 1 && e.players.size == 2) (r[0] + 5.0) / 2 else r.average()
        }

    /** Ordine per le teste di serie: prima quelle scelte a mano, poi per classifica, a pari classifica chi si è iscritto prima. */
    fun ordered(entries: List<CTEntry>, players: List<PlayerProfile>): List<CTEntry> =
        entries.sortedWith(compareBy<CTEntry>({ it.seed ?: Int.MAX_VALUE }, { rank(it, players) }, { it.at }))

    fun drawSize(n: Int): Int { var s = 1; while (s < n) s *= 2; return s }

    /** Teste di serie: due fino a 8 posti, poi una ogni quattro (16 → 4, 32 → 8). */
    fun seedCount(size: Int): Int = if (size <= 2) 0 else maxOf(2, size / 4)

    /** Per ogni posto del tabellone, il numero di testa di serie che ci va (posto 0 = la 1, ultimo = la 2). */
    fun seedOrder(size: Int): List<Int> {
        var order = listOf(1)
        var n = 1
        while (n < size) {
            n *= 2
            // Le coppie si alternano (1-8, 5-4, 3-6, 7-2): così la 2 finisce in fondo al tabellone.
            order = order.flatMapIndexed { i, k -> if (i % 2 == 0) listOf(k, n + 1 - k) else listOf(n + 1 - k, k) }
        }
        return order
    }

    private fun knockoutMatches(slots: List<String?>, now: Long): List<CTMatch> {
        val size = slots.size
        val out = mutableListOf<CTMatch>()
        var round = 1
        var count = size / 2
        while (count >= 1) {
            for (i in 0 until count) {
                out += if (round == 1) CTMatch(newId("m"), 1, i, a = slots[2 * i], b = slots[2 * i + 1]) else CTMatch(newId("m"), round, i)
            }
            round++
            count /= 2
        }
        return propagate(out)
    }

    /**
     * Il tabellone a eliminazione: teste di serie nei loro posti (la 1 in alto, la 2 in fondo, 3-4 e 5-8 sorteggiate
     * tra i loro posti), i bye contro le prime teste di serie, gli altri a sorte.
     */
    fun knockout(ordered: List<String>, random: Random, now: Long): List<CTMatch> {
        val n = ordered.size
        if (n < 2) return emptyList()
        val size = drawSize(n)
        val order = seedOrder(size)
        val posOf = IntArray(size + 1).also { a -> order.forEachIndexed { pos, k -> a[k] = pos } }
        val slots = arrayOfNulls<String>(size)
        val taken = BooleanArray(size)
        val seeds = minOf(seedCount(size), n)
        // Teste di serie: 1 e 2 fisse, poi i gruppi 3-4, 5-8, 9-16 sorteggiati tra i loro posti.
        var k = 1
        while (k <= seeds) {
            val tierEnd = if (k <= 2) k else minOf(seeds, k * 2 - 2)
            val positions = (k..tierEnd).map { posOf[it] }.shuffled(random)
            for ((i, s) in (k..tierEnd).withIndex()) { slots[positions[i]] = ordered[s - 1]; taken[positions[i]] = true }
            k = tierEnd + 1
        }
        // I bye vanno contro le teste di serie, in ordine (e oltre, contro i posti dei numeri dopo).
        val byes = size - n
        for (b in 1..byes) taken[posOf[b] xor 1] = true
        val rest = ordered.drop(seeds).shuffled(random)
        var r = 0
        for (pos in 0 until size) if (!taken[pos] && slots[pos] == null) { slots[pos] = rest[r++]; taken[pos] = true }
        return knockoutMatches(slots.toList(), now)
    }

    /** Gironi a serpentina per classifica (1 nel girone A, 2 nel B… e poi a ritroso), tutti contro tutti. */
    fun groupsDraw(ordered: List<String>, groups: Int): List<CTMatch> {
        val g = groups.coerceIn(1, maxOf(1, ordered.size / 2))
        val lists = List(g) { mutableListOf<String>() }
        ordered.forEachIndexed { i, e ->
            val row = i / g
            val col = i % g
            lists[if (row % 2 == 0) col else g - 1 - col] += e
        }
        val out = mutableListOf<CTMatch>()
        for ((gi, members) in lists.withIndex()) {
            var slot = 0
            for (pair in roundRobin(members)) out += CTMatch(newId("m"), 0, slot++, group = gi, a = pair.first, b = pair.second)
        }
        return out
    }

    /** Tutti contro tutti col metodo del cerchio: le partite turno per turno, così chi ha giocato riposa. */
    fun <T> roundRobin(list: List<T>): List<Pair<T, T>> {
        val items: MutableList<T?> = list.toMutableList<T?>().also { if (it.size % 2 == 1) it += null }
        val n = items.size
        val out = mutableListOf<Pair<T, T>>()
        repeat(n - 1) {
            for (i in 0 until n / 2) {
                val a = items[i]; val b = items[n - 1 - i]
                if (a != null && b != null) out += a to b
            }
            items.add(1, items.removeAt(n - 1))
        }
        return out
    }

    /** I membri di ogni girone, nell'ordine del sorteggio. */
    fun groupMembers(t: ClubTournament): List<List<String>> =
        t.matches.filter { it.round == 0 }.groupBy { it.group ?: 0 }.toSortedMap().values.map { ms -> ms.flatMap { listOfNotNull(it.a, it.b) }.distinct() }

    /** I set di un punteggio dal lato di a: "6-4 3-6 10-8" → [(6,4),(3,6),(10,8)]; "7-6(5)" vale 7-6. */
    fun sets(score: String): List<Pair<Int, Int>> =
        Regex("(\\d{1,2})\\s*[-/]\\s*(\\d{1,2})").findAll(score.replace(Regex("\\(\\d+\\)"), "")).map { it.groupValues[1].toInt() to it.groupValues[2].toInt() }.toList()

    /** Chi ha vinto secondo il punteggio (più set; a pari set, più game): "a", "b" o null. */
    fun winnerFromScore(score: String): String? {
        val s = sets(score)
        if (s.isEmpty()) return null
        val wa = s.count { it.first > it.second }; val wb = s.count { it.second > it.first }
        if (wa != wb) return if (wa > wb) "a" else "b"
        val ga = s.sumOf { it.first }; val gb = s.sumOf { it.second }
        return if (ga == gb) null else if (ga > gb) "a" else "b"
    }

    data class Standing(val entry: String, val played: Int, val won: Int, val setsWon: Int, val setsLost: Int, val gamesWon: Int, val gamesLost: Int)

    /** La classifica di un girone: vittorie, poi differenza set, poi differenza game, poi lo scontro diretto. */
    fun standings(t: ClubTournament, group: Int): List<Standing> {
        val members = groupMembers(t).getOrNull(group).orEmpty()
        val ms = t.matches.filter { it.round == 0 && it.group == group && it.played }
        val table = members.map { e ->
            var played = 0; var won = 0; var sw = 0; var sl = 0; var gw = 0; var gl = 0
            for (m in ms) {
                val mine = when (e) { m.a -> true; m.b -> false; else -> continue }
                played++
                if (m.winner == e) won++
                for ((x, y) in sets(m.score)) {
                    val (f, a) = if (mine) x to y else y to x
                    gw += f; gl += a
                    if (f > a) sw++ else if (a > f) sl++
                }
            }
            Standing(e, played, won, sw, sl, gw, gl)
        }
        fun beat(x: String, y: String) = ms.any { (it.a == x && it.b == y || it.a == y && it.b == x) && it.winner == x }
        return table.sortedWith { x, y ->
            compareValuesBy(y, x, { it.won }, { it.setsWon - it.setsLost }, { it.gamesWon - it.gamesLost }).takeIf { it != 0 }
                ?: if (beat(x.entry, y.entry)) -1 else if (beat(y.entry, x.entry)) 1 else 0
        }
    }

    /**
     * Il tabellone dopo i gironi: i primi come teste di serie (A1, B1…), i secondi nella metà opposta al primo del
     * loro girone, così due dello stesso girone si ritrovano solo in finale (con due gironi) o il più tardi possibile.
     */
    fun knockoutFromGroups(t: ClubTournament, random: Random, now: Long): List<CTMatch> {
        val tables = (0 until groupMembers(t).size).map { standings(t, it).map(Standing::entry) }
        val q = t.qualify.coerceAtLeast(1)
        val winners = tables.mapNotNull { it.getOrNull(0) }
        val others = (1 until q).flatMap { k -> tables.mapIndexedNotNull { g, list -> list.getOrNull(k)?.let { it to g } } }
        val n = winners.size + others.size
        if (n < 2) return emptyList()
        val size = drawSize(n)
        val order = seedOrder(size)
        val posOf = IntArray(size + 1).also { a -> order.forEachIndexed { pos, k -> a[k] = pos } }
        val slots = arrayOfNulls<String>(size)
        val taken = BooleanArray(size)
        val groupHalf = mutableMapOf<Int, Boolean>()
        winners.forEachIndexed { i, w -> val pos = posOf[i + 1]; slots[pos] = w; taken[pos] = true; groupHalf[i] = pos < size / 2 }
        for (b in 1..(size - n)) taken[posOf[b] xor 1] = true
        for ((e, g) in others) {
            val top = groupHalf[g]?.not()
            // Nella metà opposta al primo del girone; se non c'è posto, dove c'è.
            val free = (0 until size).filter { !taken[it] }
            val pos = free.filter { top == null || (it < size / 2) == top }.let { if (it.isEmpty()) free else it }.let { it[random.nextInt(it.size)] }
            slots[pos] = e; taken[pos] = true
        }
        return knockoutMatches(slots.toList(), now)
    }

    /**
     * Porta i vincitori al turno dopo (anche i bye del primo turno). Se un risultato cambia, la partita dopo che
     * non ha più quel giocatore perde il suo risultato.
     */
    fun propagate(matches: List<CTMatch>): List<CTMatch> {
        // Solo il tabellone: le partite dei gironi non hanno un turno dopo (e i loro slot si ripetono tra gironi).
        val byKey = matches.filter { it.round > 0 }.associateBy { it.round to it.slot }.toMutableMap()
        // Bye: passa da solo.
        for ((k, m) in byKey.toMap()) if (m.round == 1 && m.bye && m.winner == null) byKey[k] = m.copy(winner = m.a ?: m.b)
        val rounds = matches.maxOfOrNull { it.round } ?: 0
        for (r in 1 until rounds) {
            for (m in byKey.values.filter { it.round == r }.sortedBy { it.slot }) {
                val key = r + 1 to m.slot / 2
                val next = byKey[key] ?: continue
                var n = if (m.slot % 2 == 0) next.copy(a = m.winner) else next.copy(b = m.winner)
                if (n.winner != null && n.winner != n.a && n.winner != n.b) n = n.copy(winner = null, score = "", walkover = false, retired = false, record = null)
                byKey[key] = n
            }
        }
        return matches.map { if (it.round == 0) it else byKey[it.round to it.slot] ?: it }
    }

    // --- Modifica a mano dopo il sorteggio ---

    /** Un posto del primo turno: [side] 0 = a, 1 = b. */
    data class Seat(val match: String, val side: Int)

    private fun nextMatch(t: ClubTournament, m: CTMatch) = t.matches.firstOrNull { it.round == m.round + 1 && it.slot == m.slot / 2 }

    /** Si può cambiare chi gioca questa partita del primo turno? No se c'è un risultato che andrebbe perso. */
    fun seatLocked(t: ClubTournament, m: CTMatch): Boolean =
        m.round != 1 || if (m.bye) nextMatch(t, m)?.played == true else m.played

    /** Da quando esiste il tabellone, i gironi non si toccano più: da lì sono usciti i qualificati. */
    fun groupsLocked(t: ClubTournament): Boolean = t.matches.any { it.round > 0 }

    /** Questo iscritto può cambiare girone? No se ha già un risultato. */
    fun groupEntryLocked(t: ClubTournament, entry: String): Boolean =
        groupsLocked(t) || t.matches.any { it.round == 0 && it.played && (it.a == entry || it.b == entry) }

    /** Gli iscritti rimasti fuori: possono prendere il posto di qualcuno. */
    fun outsideDraw(t: ClubTournament): List<CTEntry> {
        val inside = t.matches.flatMap { listOf(it.a, it.b) }.toSet()
        return t.entries.filter { it.id !in inside }
    }

    private fun CTMatch.at(side: Int) = if (side == 0) a else b
    private fun CTMatch.with(side: Int, entry: String?) = (if (side == 0) copy(a = entry) else copy(b = entry)).copy(winner = null)

    /** Applica i nuovi posti: i bye si rifanno da soli, e una partita senza nessuno non può esistere. */
    private fun reseat(t: ClubTournament, changed: Map<String, CTMatch>): ClubTournament? {
        val ms = t.matches.map { changed[it.id] ?: it }
        if (ms.any { it.round == 1 && it.a == null && it.b == null }) return null
        return t.copy(matches = propagate(ms), manualEdits = t.manualEdits + 1)
    }

    /** Scambia due posti del primo turno (anche un bye). null = non si può. */
    fun swapSeats(t: ClubTournament, x: Seat, y: Seat): ClubTournament? {
        val mx = t.matches.firstOrNull { it.id == x.match } ?: return null
        val my = t.matches.firstOrNull { it.id == y.match } ?: return null
        if (x == y || seatLocked(t, mx) || seatLocked(t, my)) return null
        val ex = mx.at(x.side); val ey = my.at(y.side)
        if (ex == null && ey == null) return null
        if (mx.id == my.id) return reseat(t, mapOf(mx.id to mx.with(x.side, ey).with(y.side, ex)))
        return reseat(t, mapOf(mx.id to mx.with(x.side, ey), my.id to my.with(y.side, ex)))
    }

    /** Mette al posto [x] (anche un bye) un iscritto rimasto fuori. null = non si può. */
    fun replaceSeat(t: ClubTournament, x: Seat, entry: String): ClubTournament? {
        val m = t.matches.firstOrNull { it.id == x.match } ?: return null
        if (seatLocked(t, m) || outsideDraw(t).none { it.id == entry }) return null
        return reseat(t, mapOf(m.id to m.with(x.side, entry)))
    }

    /** Sostituisce gli iscritti in tutte le partite dei gironi secondo [map]. */
    private fun remapGroups(t: ClubTournament, map: Map<String, String>): ClubTournament = t.copy(
        matches = t.matches.map { m -> if (m.round != 0) m else m.copy(a = m.a?.let { map[it] ?: it }, b = m.b?.let { map[it] ?: it }) },
        manualEdits = t.manualEdits + 1,
    )

    private fun inGroups(t: ClubTournament, entry: String) = t.matches.any { it.round == 0 && (it.a == entry || it.b == entry) }

    /** Scambia di girone due iscritti. null = non si può. */
    fun swapGroupEntries(t: ClubTournament, x: String, y: String): ClubTournament? {
        if (x == y || !inGroups(t, x) || !inGroups(t, y) || groupEntryLocked(t, x) || groupEntryLocked(t, y)) return null
        return remapGroups(t, mapOf(x to y, y to x))
    }

    /** Nel girone, un iscritto rimasto fuori prende il posto di [old]. null = non si può. */
    fun replaceGroupEntry(t: ClubTournament, old: String, entry: String): ClubTournament? {
        if (!inGroups(t, old) || groupEntryLocked(t, old) || outsideDraw(t).none { it.id == entry }) return null
        return remapGroups(t, mapOf(old to entry))
    }

    /** Il risultato di una partita (anche per correggerlo); il tabellone si aggiorna. */
    fun setResult(t: ClubTournament, matchId: String, winner: String?, score: String, walkover: Boolean = false, retired: Boolean = false, record: String? = null): ClubTournament {
        val ms = t.matches.map { m ->
            if (m.id != matchId) m
            else m.copy(winner = winner?.takeIf { it == m.a || it == m.b }, score = score.trim(), walkover = walkover, retired = retired, record = record ?: m.record)
        }
        return t.copy(matches = propagate(ms))
    }

    /** Nome del turno del tabellone dal numero di giocatori rimasti: 2 = finale, 4 = semifinali… */
    fun playersInRound(t: ClubTournament, round: Int): Int = t.matches.count { it.round == round } * 2
}
