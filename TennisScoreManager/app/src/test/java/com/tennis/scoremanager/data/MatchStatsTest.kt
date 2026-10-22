// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import com.tennis.scoremanager.data.MatchStats.Marker
import com.tennis.scoremanager.model.MatchEvent
import com.tennis.scoremanager.model.PointKind
import com.tennis.scoremanager.model.RulesConfig
import com.tennis.scoremanager.model.Side
import com.tennis.scoremanager.model.TiebreakKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Statistiche e cronologia su partite costruite punto per punto (serve per primo il Giocatore 1). */
class MatchStatsTest {

    private val p1 = Side.P1
    private val p2 = Side.P2

    private fun rec(vararg winners: Side, strokes: Int? = null) = MatchRecord(
        id = "m", setup = SetupData(), options = MatchOptions(), rules = RulesConfig(firstServer = Side.P1),
        events = winners.map { MatchEvent.Point(it, strokes = strokes) },
    )

    private fun game(w: Side) = Array(4) { w }

    /** G1 tiene il servizio a zero, poi strappa il servizio a G2 a zero, poi tiene da 0-30. */
    private val holdBreakHold = arrayOf(*game(p1), *game(p1), p2, p2, p1, p1, p1, p1)

    /** Primo game, serve G1: ace, doppio fallo, vincente di G1 sulla seconda, errore di G2, errore di G1, punto semplice. */
    @Test
    fun detailedServeAndRallyStats() {
        fun pt(w: Side, k: PointKind?, second: Boolean = false) = MatchEvent.Point(w, kind = k, secondServe = second)
        val r = MatchRecord(
            id = "m", setup = SetupData(), options = MatchOptions(detailedStats = true), rules = RulesConfig(firstServer = p1),
            events = listOf(
                pt(p1, PointKind.ACE),
                pt(p2, PointKind.DOUBLE_FAULT, second = true),
                pt(p1, PointKind.WINNER, second = true),
                pt(p1, PointKind.ERROR),
                pt(p2, PointKind.ERROR),
                pt(p1, null),
            ),
        )
        val pts = MatchStats.points(r)
        assertTrue(MatchStats.hasDetail(r, pts))
        val t = MatchStats.totals(pts)
        val a = t.getValue(p1)
        val b = t.getValue(p2)
        assertEquals(1, a.aces); assertEquals(1, a.doubleFaults); assertEquals(0, b.aces)
        assertEquals(4, a.firstServesIn); assertEquals(6, a.servePoints)
        assertEquals(3, a.firstServePointsWon)
        assertEquals(1, a.secondServePointsWon); assertEquals(2, a.secondServes)
        assertEquals(1, a.winners); assertEquals(0, b.winners)
        // L'errore è di chi perde il punto.
        assertEquals(1, a.errors); assertEquals(1, b.errors)
        assertEquals(1, a.gamesWon)
    }

    @Test
    fun unforcedAndForcedErrorsAreAlsoErrors() {
        fun pt(w: Side, k: PointKind) = MatchEvent.Point(w, kind = k)
        val r = MatchRecord(
            id = "m", setup = SetupData(), options = MatchOptions(detailedStats = true), rules = RulesConfig(firstServer = p1),
            events = listOf(pt(p1, PointKind.UNFORCED_ERROR), pt(p1, PointKind.FORCED_ERROR), pt(p1, PointKind.ERROR), pt(p2, PointKind.UNFORCED_ERROR)),
        )
        val t = MatchStats.totals(MatchStats.points(r))
        val b = t.getValue(p2)
        assertEquals(3, b.errors)
        assertEquals(1, b.unforcedErrors)
        assertEquals(1, b.forcedErrors)
        assertEquals(1, t.getValue(p1).unforcedErrors)
    }

    @Test
    fun noDetailForPlainMatches() {
        val r = rec(*holdBreakHold)
        assertFalse(MatchStats.hasDetail(r, MatchStats.points(r)))
    }

    @Test
    fun serveReturnAndBreakPoints() {
        val t = MatchStats.totals(MatchStats.points(rec(*holdBreakHold)))
        val a = t.getValue(p1)
        val b = t.getValue(p2)
        assertEquals(8, a.servePointsWon); assertEquals(10, a.servePoints)
        assertEquals(2, a.serviceGamesWon); assertEquals(2, a.serviceGames)
        assertEquals(4, a.returnPointsWon); assertEquals(4, a.returnPoints)
        assertEquals(1, a.returnGamesWon); assertEquals(1, a.returnGames)
        // Sul 40-0 del secondo game G1 ha palla break e la trasforma subito.
        assertEquals(1, a.breakPointsWon); assertEquals(1, a.breakPoints)
        assertEquals(0, b.breakPointsSaved); assertEquals(1, b.breakPointsFaced)
        assertEquals(12, a.pointsWon); assertEquals(14, a.points)
        assertEquals(3, a.gamesWon); assertEquals(0, b.gamesWon)
        assertEquals(8, a.maxStreak); assertEquals(2, b.maxStreak)
        assertEquals(8, a.last10); assertEquals(2, b.last10)
    }

    @Test
    fun chronologyShowsBreaksAndBreakPoints() {
        val games = MatchStats.chronology(MatchStats.points(rec(*holdBreakHold)))
        assertEquals(3, games.size)
        val g2 = games[1]
        assertEquals(p2, g2.server)
        assertTrue(g2.broken)
        assertEquals(2, g2.games1); assertEquals(0, g2.games2)
        assertEquals(listOf("15" to "0", "30" to "0", "40" to "0"), g2.steps.map { it.p1 to it.p2 })
        assertEquals(listOf(null, null, Marker.BREAK_POINT), g2.steps.map { it.marker })
        assertFalse(games[2].broken)
        assertEquals(listOf("0" to "15", "0" to "30", "15" to "30", "30" to "30", "40" to "30"), games[2].steps.map { it.p1 to it.p2 })
    }

    @Test
    fun setPointAndMatchPoint() {
        val pts = MatchStats.points(rec(*Array(48) { p1 }))  // 6-0 6-0
        val games = MatchStats.chronology(pts)
        assertEquals(12, games.size)
        assertEquals(Marker.SET_POINT, games[5].steps.last().marker)   // 40-0 sul 5-0: set point (vale più della palla break)
        assertEquals(Marker.MATCH_POINT, games[11].steps.last().marker)
        assertTrue(games[5].closesSet); assertFalse(games[5].closesMatch); assertFalse(games[4].closesSet)
        assertTrue(games[11].closesMatch)
        assertEquals(6, MatchStats.totals(pts, set = 1).getValue(p1).gamesWon)
        assertEquals(24, MatchStats.totals(pts, set = 0).getValue(p1).points)
    }

    @Test
    fun tiebreakCountsAsOneGameButNotAsServiceGame() {
        // Ogni game al servizio fino al 6-6 (serve chi è di turno), poi tie-break 7-0 per G1.
        val holds = (0 until 12).flatMap { g -> game(if (g % 2 == 0) p1 else p2).toList() }
        val pts = MatchStats.points(rec(*(holds + List(7) { p1 }).toTypedArray()))
        val tb = MatchStats.chronology(pts).last()
        assertEquals(TiebreakKind.SET, tb.tiebreak)
        assertEquals(7, tb.games1); assertEquals(6, tb.games2)
        assertEquals(7, tb.tb1); assertEquals(0, tb.tb2)
        assertFalse(tb.broken)
        val a = MatchStats.totals(pts).getValue(p1)
        assertEquals(7, a.gamesWon); assertEquals(13, a.games)
        assertEquals(6, a.serviceGames)
    }

    @Test
    fun savedMatchPoint() {
        // 6-0 5-0, G2 al servizio sotto 0-40: annulla i tre match point (40-0, 40-15, 40-30) e tiene il game.
        val pts = MatchStats.points(rec(*Array(24 + 20 + 3) { p1 }, *game(p2), p2))
        assertEquals(3, MatchStats.totals(pts).getValue(p2).matchPointsSaved)
        val last = MatchStats.chronology(pts).last()
        assertEquals(p2, last.server)
        assertFalse(last.broken)
        // Dopo 15-0 e 30-0 niente; 40-0, 40-15 e 40-30 sono match point; sulla parità non più.
        assertEquals(listOf(null, null, Marker.MATCH_POINT, Marker.MATCH_POINT, Marker.MATCH_POINT, null), last.steps.take(6).map { it.marker })
    }

    @Test
    fun rallyPerSet() {
        val pts = MatchStats.points(rec(*holdBreakHold, strokes = 5))
        assertEquals(5.0, MatchStats.rally(pts, set = 0)!!.average, 1e-9)
        assertNull(MatchStats.rally(pts, set = 1))
        assertNull(MatchStats.rally(MatchStats.points(rec(*holdBreakHold))))
    }
}
