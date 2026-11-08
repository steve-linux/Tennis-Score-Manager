// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.video

import com.tennis.scoremanager.data.MatchOptions
import com.tennis.scoremanager.data.MatchRecord
import com.tennis.scoremanager.data.SetupData
import com.tennis.scoremanager.model.MatchEvent
import com.tennis.scoremanager.model.MatchFormat
import com.tennis.scoremanager.model.PointKind
import com.tennis.scoremanager.model.RulesConfig
import com.tennis.scoremanager.model.Side
import com.tennis.scoremanager.ui.ItStrings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HighlightStatsTest {

    private val rules = RulesConfig(format = MatchFormat.MATCH_TIEBREAK_ONLY)

    private fun rec(id: String, start: Long?, end: Long?, events: List<MatchEvent> = emptyList(), detailed: Boolean = false, setup: SetupData = SetupData()) =
        MatchRecord(id = id, setup = setup, options = MatchOptions(detailedStats = detailed), rules = rules, events = events,
            startedAt = start, endedAt = end, updatedAt = end ?: 0L, finished = true)

    @Test
    fun picksTheMatchThatOverlapsTheVideo() {
        val ch = VideoChapters("v.mp4", emptyList(), recordedAt = 10_000L)
        val before = rec("prima", 0L, 9_000L)
        val during = rec("durante", 12_000L, 40_000L)
        val after = rec("dopo", 70_000L, 90_000L)
        assertEquals("durante", HighlightStats.record(listOf(before, during, after), ch, videoMs = 50_000L)?.id)
        // Nessuna partita in quel momento e niente regole nel video: non si sa.
        assertNull(HighlightStats.record(listOf(before, after), ch.copy(recordedAt = 50_000L), videoMs = 5_000L))
    }

    @Test
    fun importedVideoRebuildsTheMatchFromThePoints() {
        val marks = List(10) { PointMark(at = it * 1000L, score = "", winner = 0, kind = if (it == 0) "ACE" else null) }
        val ch = VideoChapters("v.mp4", marks, imported = true, rules = rules, names = listOf("Rossi", "Bianchi"))
        val r = HighlightStats.record(emptyList(), ch, videoMs = 60_000L)
        assertNotNull(r)
        assertEquals(10, r!!.events.size)
        assertEquals("Rossi", r.setup.p1a)
        assertTrue(r.options.detailedStats)
    }

    @Test
    fun rebuiltMatchTakesPage1DataWhenNamesMatch() {
        val marks = List(10) { PointMark(at = it * 1000L, score = "", winner = 1) }
        val ch = VideoChapters("v.mp4", marks, imported = true, rules = rules, names = listOf("Rossi", "Bianchi"))
        val page1 = SetupData(p1a = "rossi ", p2a = "Bianchi", p2aSex = "F", event = "Open")
        val card = HighlightStats.card(HighlightStats.record(emptyList(), ch, 60_000L, page1)!!, ItStrings)
        assertEquals(listOf(true), card.players.getValue(Side.P2).female)
        assertEquals(listOf(null), card.players.getValue(Side.P1).female)
        assertEquals("Open", card.eventLines.first())
        // Altri nomi: la pagina 1 non c'entra.
        val other = HighlightStats.record(emptyList(), ch, 60_000L, page1.copy(p2a = "Verdi"))!!
        assertEquals("", other.setup.event)
    }

    @Test
    fun cardShowsOnlyWhatTheAppKnowsAndHighlightsTheBetterSide() {
        // Rossi vince 10-2 il super tie-break con 2 ace, sul 1° e sul 4° punto (li serve lui: 1, poi 4 e 5).
        val events = List(10) { i -> MatchEvent.Point(Side.P1, kind = if (i == 0 || i == 3) PointKind.ACE else null) } +
            listOf(MatchEvent.Point(Side.P2), MatchEvent.Point(Side.P2))
        val ordered = events.take(5) + events.drop(10) + events.drop(5).take(5)
        val setup = SetupData(p1a = "Rossi", p2a = "Bianchi", event = "Open d'autunno", category = "Lim. 4.1", round = "Finale", p1aRank = "4.1")
        val card = HighlightStats.card(rec("m", 0L, 1L, ordered, detailed = true, setup = setup), ItStrings)

        assertEquals("Statistiche partita", card.title)
        assertEquals(listOf("Open d'autunno", "Lim. 4.1 · Finale"), card.eventLines)
        assertEquals(Side.P1, card.winner)
        assertEquals(listOf("4.1"), card.players.getValue(Side.P1).ranks)
        assertEquals(83, card.players.getValue(Side.P1).pointsWonPct)  // 10 su 12

        val rows = card.pages.flatten().flatMap { it.rows }.associateBy { it.label }
        val aces = rows.getValue(ItStrings.statAces)
        assertEquals("2" to "0", aces.a to aces.b)
        assertEquals(Side.P1, aces.best)
        // Niente braccialetti e niente fascia: nessuna riga sugli scambi né sul battito.
        assertTrue(card.pages.flatten().none { it.title == ItStrings.cardRallies || it.title == ItStrings.heartStat })
    }

    @Test
    fun withoutDetailedStatsNoAces() {
        val events = List(10) { MatchEvent.Point(Side.P2) }
        val card = HighlightStats.card(rec("m", 0L, 1L, events), ItStrings)
        val labels = card.pages.flatten().flatMap { it.rows }.map { it.label }
        assertTrue(ItStrings.statAces !in labels)
        assertTrue(ItStrings.statTotalPoints in labels)
        // Senza torneo in alto a destra non c'è niente da scrivere (niente circolo).
        assertTrue(card.eventLines.isEmpty())
        assertEquals(Side.P2, card.winner)
    }
}
