// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.video

import com.tennis.scoremanager.data.Backhand
import com.tennis.scoremanager.data.MatchRecord
import com.tennis.scoremanager.data.Names
import com.tennis.scoremanager.data.PlayerProfile
import com.tennis.scoremanager.data.PlayerStats
import com.tennis.scoremanager.data.Players
import com.tennis.scoremanager.data.Rating
import com.tennis.scoremanager.data.Reports
import com.tennis.scoremanager.model.ScoreEngine
import com.tennis.scoremanager.model.Side
import com.tennis.scoremanager.ui.Strings

/**
 * L'intro degli highlights, come la presentazione dei giocatori prima delle partite dei tornei: torneo e turno in
 * alto, i due giocatori ai lati (foto o avatar, nome e COGNOME) e al centro il confronto coi dati che l'app ha già
 * (classifica, rating TSM, altezza, rovescio, circolo, bilancio, forma e scontri diretti prima di questa partita).
 * Qui si decide cosa mostrare; il disegno, nello stesso stile delle statistiche, è in [StatsCardRenderer.intro].
 */
object HighlightIntro {
    /** Quante partite per la forma. */
    const val FORM = 5

    /** Durata dell'intro nel video, dissolvenze comprese. */
    const val MS = 9_000L

    /** Un giocatore: [first] piccolo sopra, [last] grande (come "Jannik / SINNER"). */
    data class Person(val first: String, val last: String, val female: Boolean?)

    /** Una riga del confronto: i valori dei due lati già scritti ("–" se manca). */
    data class Row(val label: String, val a: String, val b: String)

    data class Card(
        /** In alto: il torneo, o il circolo, o "Highlights". */
        val title: String,
        /** Sotto il titolo: categoria e turno, o il campo. */
        val subtitle: String,
        /** In alto a destra: la data (e il circolo se il titolo è il torneo). */
        val place: List<String>,
        /** Nello stesso ordine dei nomi delle statistiche ([Names.players]): le foto valgono per tutte e due. */
        val people: Map<Side, List<Person>>,
        val rows: List<Row>,
        /** Scontri diretti prima di questa partita (solo singolare, e solo se si sono già incontrati). */
        val h2h: Pair<Int, Int>?,
        val h2hLabel: String = "",
        /** L'ultimo scontro diretto prima di questa partita: data e punteggio visto dal lato 1 ("12/10/2026 · 6-4 6-3"). */
        val lastMeeting: String? = null,
        val lastMeetingLabel: String = "",
        /** In fondo al pannello di ogni lato, in grande: la classifica (nel doppio "4.1 / 4.3"), e la sua scritta. */
        val ranks: Map<Side, String> = emptyMap(),
        val rankLabel: String = "",
    )

    /**
     * [history]: le partite finite dello storico; contano solo quelle finite prima di [before] (l'inizio della
     * partita del video), così bilancio, rating e scontri diretti sono quelli della vigilia. [date] è già scritta.
     */
    fun card(
        rec: MatchRecord, s: Strings, profiles: List<PlayerProfile>, history: List<MatchRecord>, before: Long, date: String,
        female: (String) -> Boolean = { false },
        /** Come scrivere la data dell'ultimo scontro diretto. */
        day: (Long) -> String = { "" },
    ): Card {
        val su = rec.setup
        val names = Names(su, s)
        val past = history.filter { it.id != rec.id && (it.endedAt ?: it.updatedAt) <= before }
            .map { it to ScoreEngine.replay(it.rules, it.events) }
        val played = past.map { (r, st) -> PlayerStats.Played(r, st.winner) }
        val rating = Rating.compute(past.mapNotNull { (r, st) -> Rating.gameOf(r, st.sets, st.winner) }, su.sport)

        // I giocatori veri di ogni posto (chi non ha nome resta "Giocatore 1" e non ha dati).
        val ids = mapOf(Side.P1 to listOf(su.p1aId, su.p1bId), Side.P2 to listOf(su.p2aId, su.p2bId))
        val setRanks = mapOf(Side.P1 to listOf(su.p1aRank, su.p1bRank), Side.P2 to listOf(su.p2aRank, su.p2bRank))
        val sexes = mapOf(Side.P1 to listOf(su.p1aSex, su.p1bSex), Side.P2 to listOf(su.p2aSex, su.p2bSex))
        class Slot(val shown: String, val who: PlayerStats.Who?, val profile: PlayerProfile?, val rank: String, val sex: String)
        val slots = Side.entries.associateWith { side ->
            val real = PlayerStats.whos(su, side)
            names.players(side).mapIndexed { i, shown ->
                val who = real.firstOrNull { it.name == shown }
                val id = ids.getValue(side)[i]
                val profile = Players.byId(profiles, id) ?: who?.let { w -> Players.byName(profiles, w.name).singleOrNull() }
                Slot(shown, who, profile, setRanks.getValue(side)[i].trim(), sexes.getValue(side)[i])
            }
        }

        val people = slots.mapValues { (_, list) ->
            list.map { sl ->
                val p = sl.profile
                val (first, last) = if (p != null && p.complete && p.nickname.isBlank()) p.firstName.trim() to p.lastName.trim() else split(sl.shown)
                val sex = sl.sex.ifEmpty { p?.sex.orEmpty() }
                Person(first, last, when (sex) { "F" -> true; "M" -> false; else -> if (sl.who != null && female(sl.shown)) true else null })
            }
        }

        // Una riga per dato, solo se almeno un lato ce l'ha; nel doppio i due valori del lato con "/".
        fun row(label: String, value: (Slot) -> String?): Row? {
            val v = Side.entries.associateWith { side -> slots.getValue(side).map { value(it)?.takeIf { x -> x.isNotBlank() } } }
            if (v.values.flatten().all { it == null }) return null
            fun text(side: Side) = v.getValue(side).joinToString(" / ") { it ?: "–" }
            return Row(label, text(Side.P1), text(Side.P2))
        }
        val rankRow = row(s.memberRankingLabel) { it.rank.ifEmpty { it.profile?.member?.ranking.orEmpty() } }
        val rows = listOfNotNull(
            row(s.ratingTitle) { sl -> sl.who?.let { rating.player(it.key)?.rating?.toString() } },
            row(s.introHeight) { sl -> sl.profile?.heightCm?.let { "$it cm" } },
            row(s.playerBackhand) { sl -> when (sl.profile?.backhand) { Backhand.ONE -> s.backhandOne; Backhand.TWO -> s.backhandTwo; null -> null } },
            row(s.introClub) { it.profile?.club },
            row(s.introRecord) { sl ->
                sl.who?.let { w ->
                    val mine = PlayerStats.matchesOf(played, w.key).filter { it.winner != null }
                    if (mine.isEmpty()) null else {
                        val won = mine.count { it.winner == PlayerStats.sideOf(it.rec, w.key) }
                        "$won–${mine.size - won}"
                    }
                }
            },
            // Le ultime 5 partite prima di questa, dalla più vecchia alla più recente: "V V S V V".
            row(s.introForm) { sl ->
                sl.who?.let { w ->
                    PlayerStats.matchesOf(played, w.key).filter { it.winner != null }.sortedBy { it.rec.endedAt ?: it.rec.updatedAt }.takeLast(FORM)
                        .takeIf { it.isNotEmpty() }
                        ?.joinToString(" ") { if (it.winner == PlayerStats.sideOf(it.rec, w.key)) s.formWin else s.formLoss }
                }
            },
        )

        // Scontri diretti: nel singolare, tra due giocatori veri che si sono già incontrati.
        val h2h = if (su.doubles) null else {
            val a = slots.getValue(Side.P1).firstOrNull()?.who
            val b = slots.getValue(Side.P2).firstOrNull()?.who
            if (a == null || b == null) null
            else PlayerStats.rivals(played, a.key).firstOrNull { it.key == b.key }?.let { it.won to it.lost }
        }
        // L'ultima volta che si sono incontrati: quando e come è finita, dal lato del giocatore a sinistra.
        val lastMeeting = if (h2h == null) null else {
            val a = slots.getValue(Side.P1).first().who!!
            val b = slots.getValue(Side.P2).first().who!!
            past.filter { (r, st) -> st.winner != null && PlayerStats.sideOf(r, a.key)?.let { PlayerStats.sideOf(r, b.key) == it.other } == true }
                .maxByOrNull { (r, _) -> r.endedAt ?: r.updatedAt }
                ?.let { (r, st) -> listOf(day(r.endedAt ?: r.updatedAt), Reports.scoreLine(st, PlayerStats.sideOf(r, a.key)!!)).filter { it.isNotBlank() }.joinToString(" · ") }
        }

        val event = su.event.trim()
        val club = su.club.trim()
        val court = su.court.trim().takeIf { it.isNotEmpty() }?.let { "${s.court} $it" }.orEmpty()
        val title = event.ifEmpty { club }.ifEmpty { s.hlTitle }
        val subtitle = if (event.isNotEmpty()) listOf(su.category.trim(), su.round.trim()).filter { it.isNotEmpty() }.joinToString(" · ") else court
        val place = listOf(date, if (event.isNotEmpty()) listOf(club, court).filter { it.isNotEmpty() }.joinToString(" · ") else "").filter { it.isNotEmpty() }
        val ranks = if (rankRow == null) emptyMap() else mapOf(Side.P1 to rankRow.a, Side.P2 to rankRow.b)
        return Card(title, subtitle, place, people, rows, h2h, s.introH2h, lastMeeting, s.introLastMeeting, ranks, s.memberRankingLabel)
    }

    /** "Mario Rossi" → ("Mario", "Rossi"); un nome solo resta tutto grande. */
    fun split(name: String): Pair<String, String> {
        val t = name.trim()
        val i = t.lastIndexOf(' ')
        return if (i <= 0) "" to t else t.substring(0, i).trim() to t.substring(i + 1)
    }
}
