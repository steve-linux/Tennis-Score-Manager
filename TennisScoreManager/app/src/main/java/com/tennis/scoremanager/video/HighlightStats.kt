// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.video

import com.tennis.scoremanager.data.HeartStats
import com.tennis.scoremanager.data.MatchOptions
import com.tennis.scoremanager.data.MatchRecord
import com.tennis.scoremanager.data.MatchStats
import com.tennis.scoremanager.data.Names
import com.tennis.scoremanager.data.PlayerStats
import com.tennis.scoremanager.data.Reports
import com.tennis.scoremanager.data.SetupData
import com.tennis.scoremanager.model.MatchEvent
import com.tennis.scoremanager.model.PointKind
import com.tennis.scoremanager.model.ScoreEngine
import com.tennis.scoremanager.model.Side
import com.tennis.scoremanager.ui.Strings

/**
 * Le schermate delle statistiche in coda agli highlights, come nelle dirette dei tornei: solo i dati che l'app ha
 * già (quelli del riepilogo). Qui si decide cosa mostrare; il disegno è in [StatsCardRenderer].
 */
object HighlightStats {
    /** Una riga: valori dei due lati già scritti e [best], il lato evidenziato (null = pari o non confrontabile). */
    data class Row(val label: String, val a: String, val b: String, val best: Side?)

    data class Section(val title: String, val rows: List<Row>)

    /** Un giocatore nel pannello laterale: [names] uno (singolare) o due (doppio), con le classifiche. */
    data class Player(val names: List<String>, val ranks: List<String>, val pointsWonPct: Int, val female: List<Boolean?> = emptyList())

    data class Card(
        val title: String,
        /** "Risultato finale · 1:23:05" */
        val subtitle: String,
        val sideNames: Map<Side, String>,
        /** Game di ogni set, dal lato di ciascuno ("6", "3", "7(5)"...). */
        val sets: Map<Side, List<String>>,
        val winner: Side?,
        /** In alto a destra: torneo, poi categoria e turno (o circolo e campo se il torneo non c'è). */
        val eventLines: List<String>,
        val players: Map<Side, Player>,
        val pages: List<List<Section>>,
    )

    /** Secondi di ogni schermata nel video. */
    const val PAGE_MS = 10_000L

    /** Da 9 colpi in su lo scambio è lungo, come nelle statistiche dei tornei ("over 9 shots"). */
    private const val LONG_RALLY = 9

    /**
     * La partita del video: quella dello storico che si sovrappone di più nel tempo al video, altrimenti (video
     * importati coi punti segnati dopo) una ricostruita dai punti; null se non si può sapere. Nella partita
     * ricostruita torneo, classifiche e sesso vengono da [current] (la pagina 1) se i nomi sono gli stessi.
     */
    fun record(history: List<MatchRecord>, ch: VideoChapters, videoMs: Long, current: SetupData? = null): MatchRecord? {
        val from = ch.recordedAt
        val to = from + videoMs
        val best = history.filter { it.startedAt != null }.maxByOrNull { r ->
            val start = r.startedAt ?: 0L
            val end = r.endedAt ?: r.updatedAt
            minOf(end, to) - maxOf(start, from)
        }
        if (best != null && from > 0) {
            val overlap = minOf(best.endedAt ?: best.updatedAt, to) - maxOf(best.startedAt ?: 0L, from)
            if (overlap > 0) return best
        }
        val rules = ch.rules ?: return null
        if (ch.points.isEmpty()) return null
        val events = ch.points.map { p ->
            MatchEvent.Point(
                winner = if (p.winner == 1) Side.P2 else Side.P1,
                kind = p.kind?.let { k -> runCatching { PointKind.valueOf(k) }.getOrNull() },
                secondServe = p.secondServe,
                strokes = p.strokes,
            )
        }
        return MatchRecord(
            id = "video", rules = rules, events = events,
            // Durata: fino all'ultimo punto segnato nel video.
            clockMs = ch.points.maxOf { it.at },
            setup = setupFor(ch.names, current),
            options = MatchOptions(detailedStats = events.any { it.kind != null }),
        )
    }

    private fun setupFor(names: List<String>, current: SetupData?): SetupData {
        val a = names.getOrElse(0) { "" }
        val b = names.getOrElse(1) { "" }
        fun same(x: String, y: String) = PlayerStats.key(x) == PlayerStats.key(y)
        return if (current != null && !current.doubles && same(current.p1a, a) && same(current.p2a, b)) current
        else SetupData(p1a = a, p2a = b)
    }

    fun card(rec: MatchRecord, s: Strings): Card {
        val names = Names(rec.setup, s)
        val state = ScoreEngine.replay(rec.rules, rec.events)
        val pts = MatchStats.points(rec)
        val t = MatchStats.totals(pts)
        val a = t.getValue(Side.P1)
        val b = t.getValue(Side.P2)
        val detail = MatchStats.hasDetail(rec, pts)
        val su = rec.setup

        val serve = Section(s.statServe, listOfNotNull(
            count(s.statAces, a.aces, b.aces).takeIf { detail },
            count(s.statDoubleFaults, a.doubleFaults, b.doubleFaults, fewerIsBetter = true).takeIf { detail },
            pct(s.statFirstServeIn, a.firstServesIn, a.servePoints, b.firstServesIn, b.servePoints).takeIf { detail },
            pct(s.statFirstServeWon, a.firstServePointsWon, a.firstServesIn, b.firstServePointsWon, b.firstServesIn).takeIf { detail },
            pct(s.statSecondServeWon, a.secondServePointsWon, a.secondServes, b.secondServePointsWon, b.secondServes).takeIf { detail },
            pct(s.statServicePoints, a.servePointsWon, a.servePoints, b.servePointsWon, b.servePoints),
            pct(s.statServiceGames, a.serviceGamesWon, a.serviceGames, b.serviceGamesWon, b.serviceGames),
        ))
        val breaks = Section(s.cardBreakPoints, listOf(
            ratio(s.statBreakWon, a.breakPointsWon, a.breakPoints, b.breakPointsWon, b.breakPoints),
            ratio(s.statBreakSaved, a.breakPointsSaved, a.breakPointsFaced, b.breakPointsSaved, b.breakPointsFaced),
        ))
        val points = Section(s.statPoints, listOfNotNull(
            count(s.statWinners, a.winners, b.winners).takeIf { detail },
            count(s.statUnforcedErrors, a.unforcedErrors, b.unforcedErrors, fewerIsBetter = true).takeIf { detail },
            count(s.statForcedErrors, a.forcedErrors, b.forcedErrors, fewerIsBetter = true).takeIf { detail },
            pct(s.statReturnPoints, a.returnPointsWon, a.returnPoints, b.returnPointsWon, b.returnPoints),
            pct(s.statTotalPoints, a.pointsWon, a.points, b.pointsWon, b.points),
            count(s.statStreak, a.maxStreak, b.maxStreak),
        ))
        // Punti vinti con scambi corti e lunghi: solo se i braccialetti hanno contato i colpi.
        val counted = pts.filter { (it.strokes ?: 0) > 0 }
        val rallies = if (counted.isEmpty()) null else {
            fun won(side: Side, long: Boolean) = counted.count { it.winner == side && (it.strokes!! >= LONG_RALLY) == long }
            Section(s.cardRallies, listOf(
                count(s.cardShortRallies, won(Side.P1, false), won(Side.P2, false)),
                count(s.cardLongRallies, won(Side.P1, true), won(Side.P2, true)),
            ))
        }
        val heart = Side.entries.associateWith { HeartStats.summary(rec.heart[it]) }
        val heartSection = if (heart.values.all { it == null }) null else {
            val ha = heart[Side.P1]
            val hb = heart[Side.P2]
            Section(s.heartStat, listOfNotNull(
                plain("${s.heartAvg} (bpm)", ha?.avg, hb?.avg),
                plain("${s.heartMax} (bpm)", ha?.max, hb?.max),
                plain("${s.heartCalories} (kcal)", ha?.kcal, hb?.kcal).takeIf { ha?.kcal != null || hb?.kcal != null },
            ))
        }
        val pages = listOf(
            listOf(serve, breaks),
            listOfNotNull(points, rallies),
            listOfNotNull(heartSection),
        ).filter { page -> page.any { it.rows.isNotEmpty() } }

        val winner = state.winner
        // Set finiti e, se la partita non è finita, i game del set in corso.
        val sets = Side.entries.associateWith { side ->
            state.sets.map { set -> Reports.setText(set, side).substringBefore('-').trim() } +
                if (state.isFinished) emptyList() else listOf("${state.games(side)}")
        }
        val eventLines = if (su.event.isNotBlank()) {
            listOf(su.event.trim(), listOf(su.category, su.round).map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" · "))
        } else {
            listOf(su.club.trim(), su.court.trim().takeIf { it.isNotEmpty() }?.let { "${s.court} $it" } ?: "")
        }.filter { it.isNotEmpty() }
        val players = Side.entries.associateWith { side ->
            val tot = if (side == Side.P1) a else b
            val ranks = if (side == Side.P1) listOf(su.p1aRank, su.p1bRank) else listOf(su.p2aRank, su.p2bRank)
            val sexes = if (side == Side.P1) listOf(su.p1aSex, su.p1bSex) else listOf(su.p2aSex, su.p2bSex)
            val n = names.players(side).size
            Player(
                names.players(side), ranks.take(n).map { it.trim() }, percent(tot.pointsWon, tot.points) ?: 0,
                sexes.take(n).map { when (it) { "F" -> true; "M" -> false; else -> null } },
            )
        }
        return Card(
            title = s.cardTitle(s.periodMatch),
            subtitle = (if (state.isFinished) s.cardFinal else s.cardPartial) + if (rec.clockMs > 0) " · ${Reports.duration(rec.clockMs)}" else "",
            sideNames = Side.entries.associateWith { names.short(it) },
            sets = sets,
            winner = winner,
            eventLines = eventLines,
            players = players,
            pages = pages,
        )
    }

    /** Nomi veri dei giocatori di un lato (per le foto: chi non ha nome non ha foto). */
    fun photoNames(rec: MatchRecord, side: Side): List<String> = PlayerStats.names(rec.setup, side)

    private fun percent(v: Int, of: Int): Int? = if (of > 0) Math.round(v * 100.0 / of).toInt() else null

    private fun better(a: Double?, b: Double?, fewerIsBetter: Boolean = false): Side? {
        if (a == null || b == null || a == b) return null
        return if ((a > b) != fewerIsBetter) Side.P1 else Side.P2
    }

    private fun count(label: String, a: Int, b: Int, fewerIsBetter: Boolean = false) =
        Row(label, "$a", "$b", better(a.toDouble(), b.toDouble(), fewerIsBetter))

    private fun plain(label: String, a: Int?, b: Int?) =
        Row(label, a?.toString() ?: "–", b?.toString() ?: "–", better(a?.toDouble(), b?.toDouble()))

    private fun pct(label: String, a: Int, aOf: Int, b: Int, bOf: Int): Row {
        val pa = percent(a, aOf)
        val pb = percent(b, bOf)
        return Row(label, pa?.let { "$it%" } ?: "–", pb?.let { "$it%" } ?: "–", better(pa?.toDouble(), pb?.toDouble()))
    }

    /** "1/3": chi ha la quota più alta è avanti; senza tentativi non si confronta. */
    private fun ratio(label: String, a: Int, aOf: Int, b: Int, bOf: Int): Row {
        val ra = if (aOf > 0) a.toDouble() / aOf else null
        val rb = if (bOf > 0) b.toDouble() / bOf else null
        return Row(label, "$a/$aOf", "$b/$bOf", better(ra, rb))
    }
}
