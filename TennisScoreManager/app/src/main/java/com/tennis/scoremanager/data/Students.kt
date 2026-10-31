// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import kotlinx.serialization.Serializable

@Serializable
enum class Hand { RIGHT, LEFT }

/**
 * Un giocatore del database: chi gioca le partite e chi si allena. Ha un identificativo casuale (UUID) che resta lo
 * stesso su ogni telefono, così le partite passate da un telefono all'altro restano sue anche con un omonimo. Gli
 * allievi dell'allenatore sono giocatori con [student].
 */
@Serializable
data class PlayerProfile(
    val id: String,
    /** Come lo si chiama in partita, sul tabellone e nelle statistiche: il soprannome, o "Nome Cognome" (vedi [display]). */
    val name: String,
    /** Nome e cognome: obbligatori per un giocatore nuovo; vuoti nei giocatori nati dallo storico (da completare). */
    val firstName: String = "",
    val lastName: String = "",
    /** Soprannome facoltativo: se c'è, è il nome che si vede. */
    val nickname: String = "",
    val hand: Hand? = null,
    val note: String = "",
    val createdAt: Long = 0,
    /** La sua racchetta (Racket.id): va con le sue sessioni di allenamento, com'era quel giorno. */
    val racket: String? = null,
    /** Allievo dell'allenatore: compare in Allenati › Allievi e si sceglie in "Chi si allena". */
    val student: Boolean = false,
    /** Altri identificativi che sono lui: lo stesso giocatore creato su un altro telefono, o unito a questo. */
    val aliases: Set<String> = emptySet(),
) {
    /** Nome e cognome, per esteso. */
    val fullName: String get() = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ")

    /** Ha nome e cognome (quelli nati dallo storico ne hanno uno solo finché non li si completa). */
    val complete: Boolean get() = firstName.isNotBlank() && lastName.isNotBlank()

    companion object {
        /** Il nome che si vede: il soprannome se c'è, altrimenti nome e cognome. */
        fun display(first: String, last: String, nick: String): String =
            nick.trim().ifEmpty { listOf(first.trim(), last.trim()).filter { it.isNotEmpty() }.joinToString(" ") }
    }
}

object Players {
    fun newId(): String = java.util.UUID.randomUUID().toString()

    /** Il giocatore di un identificativo, anche se è uno dei suoi alias. */
    fun byId(players: List<PlayerProfile>, id: String): PlayerProfile? =
        if (id.isEmpty()) null else players.firstOrNull { it.id == id } ?: players.firstOrNull { id in it.aliases }

    /**
     * I giocatori che si chiamano così: per il nome che si vede, il soprannome o nome e cognome (maiuscole e spazi non
     * contano).
     */
    fun byName(players: List<PlayerProfile>, name: String): List<PlayerProfile> {
        val k = PlayerStats.key(name)
        if (k.isEmpty()) return emptyList()
        return players.filter { p -> listOf(p.name, p.nickname, p.fullName).any { it.isNotBlank() && PlayerStats.key(it) == k } }
    }

    /** Omonimi veri: stesso nome e cognome (esclusi [except]). */
    fun sameFullName(players: List<PlayerProfile>, first: String, last: String, except: String? = null): List<PlayerProfile> {
        val k = PlayerStats.key("$first $last")
        return players.filter { it.id != except && it.complete && PlayerStats.key(it.fullName) == k }
    }

    /** Senza accenti e in minuscolo, per cercare. */
    fun fold(s: String): String = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase().trim()

    /** Giocatori che corrispondono a quello che si sta scrivendo: prima chi inizia così, poi chi lo contiene. */
    fun search(players: List<PlayerProfile>, text: String): List<PlayerProfile> {
        val q = fold(text)
        if (q.isEmpty()) return emptyList()
        fun texts(p: PlayerProfile) = listOf(p.name, p.nickname, p.fullName, p.lastName).filter { it.isNotBlank() }.map(::fold)
        val hits = players.filter { p -> texts(p).any { it.contains(q) } }
        return hits.sortedWith(compareBy<PlayerProfile> { p -> texts(p).none { it.startsWith(q) } }.thenBy { it.name.lowercase() })
    }

    /** I posti di una partita: lato, secondo del doppio, nome scritto e identificativo. */
    data class Slot(val side: com.tennis.scoremanager.model.Side, val second: Boolean, val name: String, val id: String, val fromNext: Boolean = false)

    fun slots(su: SetupData): List<Slot> = buildList {
        add(Slot(com.tennis.scoremanager.model.Side.P1, false, su.p1a, su.p1aId))
        add(Slot(com.tennis.scoremanager.model.Side.P2, false, su.p2a, su.p2aId))
        if (su.doubles) {
            add(Slot(com.tennis.scoremanager.model.Side.P1, true, su.p1b, su.p1bId))
            add(Slot(com.tennis.scoremanager.model.Side.P2, true, su.p2b, su.p2bId))
        }
    }

    fun withId(su: SetupData, slot: Slot, id: String): SetupData = when {
        slot.side == com.tennis.scoremanager.model.Side.P1 && !slot.second -> su.copy(p1aId = id)
        slot.side == com.tennis.scoremanager.model.Side.P1 -> su.copy(p1bId = id)
        !slot.second -> su.copy(p2aId = id)
        else -> su.copy(p2bId = id)
    }

    /**
     * Dà un giocatore a ogni nome scritto senza identificativo: quello del database con quel nome se è uno solo, uno
     * nuovo se non c'è. Con più giocatori di quel nome (omonimi) il posto resta col solo nome: lo si sceglie a mano.
     * Restituisce la partita aggiornata e i giocatori nuovi da salvare.
     */
    fun assign(su: SetupData, players: List<PlayerProfile>, now: Long): Pair<SetupData, List<PlayerProfile>> {
        var out = su
        val created = ArrayList<PlayerProfile>()
        for (slot in slots(su)) {
            if (slot.id.isNotEmpty() || slot.name.isBlank()) continue
            val same = byName(players + created, slot.name)
            val id = when (same.size) {
                0 -> PlayerProfile(newId(), slot.name.trim(), nickname = slot.name.trim(), createdAt = now).also { created += it }.id
                1 -> same[0].id
                else -> continue
            }
            out = withId(out, slot, id)
        }
        return out to created
    }

    /**
     * Una domanda all'arrivo di partite da un altro telefono: il giocatore [incomingId] si chiama come uno o più dei
     * nostri ([candidates]). È lo stesso (lo si lega) o un'altra persona (lo si aggiunge col suo identificativo)?
     */
    @Serializable
    data class Question(val incomingId: String, val name: String, val candidates: List<String>)

    /**
     * Una partita arrivata da un altro telefono: ogni posto prende il nostro identificativo se il giocatore lo
     * conosciamo (anche come alias); un giocatore che non conosciamo diventa nostro col suo identificativo, così i due
     * telefoni restano allineati, a meno che non ci sia già qualcuno col suo nome: allora resta così e si chiede.
     * I posti senza identificativo (versioni vecchie) li sistema poi [assign]. Restituisce la partita, i giocatori nuovi
     * e le domande.
     */
    fun resolveIncoming(su: SetupData, players: List<PlayerProfile>, now: Long): Triple<SetupData, List<PlayerProfile>, List<Question>> {
        var out = su
        val created = ArrayList<PlayerProfile>()
        val questions = ArrayList<Question>()
        for (slot in slots(su)) {
            if (slot.id.isEmpty() || slot.name.isBlank()) continue
            val known = byId(players + created, slot.id)
            if (known != null) {
                if (known.id != slot.id) out = withId(out, slot, known.id)
                continue
            }
            val same = byName(players, slot.name)
            if (same.isEmpty()) created += PlayerProfile(slot.id, slot.name.trim(), nickname = slot.name.trim(), createdAt = now)
            else if (questions.none { it.incomingId == slot.id }) questions += Question(slot.id, slot.name.trim(), same.map { it.id })
        }
        return Triple(out, created, questions)
    }

    /**
     * Completa i nostri giocatori con le schede arrivate da un altro telefono (per identificativo o alias): nome,
     * cognome, soprannome e mano solo dove da noi mancano; gli alias si aggiungono.
     */
    fun fillFrom(players: List<PlayerProfile>, info: List<PlayerProfile>): List<PlayerProfile> = players.map { p ->
        val other = info.firstOrNull { it.id == p.id || it.id in p.aliases || p.id in it.aliases } ?: return@map p
        val first = p.firstName.ifBlank { other.firstName }
        val last = p.lastName.ifBlank { other.lastName }
        // Il soprannome di chi l'ha completato vale solo se da noi il giocatore non era completo.
        val nick = if (p.complete) p.nickname else other.nickname.ifBlank { if (other.complete) "" else p.nickname }
        p.copy(
            firstName = first, lastName = last, nickname = nick, name = if (p.complete) p.name else PlayerProfile.display(first, last, nick),
            hand = p.hand ?: other.hand, aliases = p.aliases + other.aliases + other.id - p.id,
        )
    }

    /** Posti con un nome di più giocatori (omonimi) e senza identificativo: chi è va scelto. */
    fun ambiguous(su: SetupData, players: List<PlayerProfile>): List<Slot> =
        slots(su).filter { it.id.isEmpty() && it.name.isNotBlank() && byName(players, it.name).size > 1 }
}

object Students {
    /** Nome valido come cartella: senza i caratteri che i file system rifiutano. */
    fun folderName(name: String): String = name.replace(Regex("""[\\/:*?"<>|]"""), " ").replace(Regex("\\s+"), " ").trim().ifEmpty { "?" }

    /** Da che parte gioca un giocatore (o uno dei suoi alias) in una partita. */
    fun sideIn(rec: MatchRecord, p: PlayerProfile): com.tennis.scoremanager.model.Side? {
        val keys = setOf(p.id) + p.aliases
        return com.tennis.scoremanager.model.Side.entries.firstOrNull { side -> PlayerStats.whos(rec.setup, side).any { it.key in keys } }
    }

    /** Le sessioni di un allievo ([id] null = quelle senza allievo). */
    fun sessionsOf(all: List<ServeSession>, id: String?): List<ServeSession> = all.filter { it.student == id }

    /** Per un colpo: quante sessioni e colpi, la percentuale dentro della prima e dell'ultima sessione, l'ultima volta. */
    data class StrokeLine(val stroke: TrainedStroke, val sessions: Int, val shots: Int, val firstPct: Int?, val lastPct: Int?, val lastAt: Long)

    fun strokeLines(sessions: List<ServeSession>): List<StrokeLine> =
        TrainedStroke.entries.mapNotNull { t ->
            val mine = sessions.filter { it.stroke == t && it.serves.isNotEmpty() }.sortedBy { it.startedAt }
            if (mine.isEmpty()) return@mapNotNull null
            StrokeLine(
                t, mine.size, mine.sumOf { it.serves.size },
                ServeTraining.count(mine.first().serves).pct, ServeTraining.count(mine.last().serves).pct, mine.last().startedAt,
            )
        }

    /** Una misura dell'analisi in due sessioni: [unit] "°" o "" (frazioni di statura), [better] il verso buono (+1, -1, 0 = nessuno). */
    data class Change(val label: String, val before: Float?, val now: Float?, val unit: String, val better: Int)
}
