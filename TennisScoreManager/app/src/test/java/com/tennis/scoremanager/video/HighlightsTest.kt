// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.video

import com.tennis.scoremanager.model.RulesConfig
import com.tennis.scoremanager.model.Side
import org.junit.Assert.assertEquals
import org.junit.Test

class HighlightsTest {

    private fun p(at: Long, kind: String? = null, pressure: String? = null, strokes: Int? = null) =
        PointMark(at, "", 0, kind, pressure = pressure, strokes = strokes)

    @Test
    fun picksByReasonAndKeepsTimeOrder() {
        val pts = listOf(
            p(20_000),                              // niente
            p(40_000, kind = "ACE"),                // ace
            p(60_000, pressure = "BREAK_POINT"),    // palla break
            p(65_000, kind = "WINNER"),             // vincente, 5 s dopo il punto prima
            p(90_000, strokes = 12),                // scambio lungo
        )
        val clips = Highlights.select(pts, null, 0)
        assertEquals(listOf(1, 2, 3, 4), clips.map { it.index })
        assertEquals(Highlights.Reason.BREAK_POINT, clips[1].reason)
        // Il vincente parte dopo la fine del punto prima (+1 s), non 12 s prima.
        assertEquals(61_000L, clips[2].startMs)
        assertEquals(67_000L, clips[2].endMs)
        assertEquals(28_000L, clips[0].startMs)
    }

    @Test
    fun orderDecidesWhoStays() {
        val pts = listOf(p(20_000, kind = "ACE"), p(40_000, pressure = "BREAK_POINT"), p(60_000, kind = "WINNER"))
        // Al minimo 7 s l'uno: con 14 s ne stanno due. Di serie vincono palla break e (tra ace e vincente) l'ace.
        assertEquals(listOf(0, 1), Highlights.select(pts, null, 0, Highlights.Options(maxMs = 14_000)).map { it.index })
        // Vincenti in cima, poi ace: la palla break resta fuori.
        val order = listOf(Highlights.Category.WINNERS, Highlights.Category.ACES, Highlights.Category.BIG_POINTS, Highlights.Category.LONG_RALLIES)
        assertEquals(listOf(0, 2), Highlights.select(pts, null, 0, Highlights.Options(order = order, maxMs = 14_000)).map { it.index })
    }

    @Test
    fun pointWithTwoReasonsCountsWithTheHigher() {
        // Ace su palla break: con gli ace in cima conta come ace.
        val pt = p(20_000, kind = "ACE", pressure = "BREAK_POINT")
        val aceFirst = Highlights.Options(order = listOf(Highlights.Category.ACES, Highlights.Category.BIG_POINTS, Highlights.Category.WINNERS, Highlights.Category.LONG_RALLIES))
        assertEquals(Highlights.Reason.ACE, Highlights.reason(pt, "BREAK_POINT", aceFirst))
        assertEquals(Highlights.Reason.BREAK_POINT, Highlights.reason(pt, "BREAK_POINT", Highlights.Options()))
    }

    @Test
    fun overLimitClipsGetShorterFirst() {
        val pts = listOf(p(20_000, kind = "ACE"), p(40_000, pressure = "SET_POINT"), p(60_000, kind = "WINNER"))
        // 14 s l'uno fanno 42 s: con 30 s di limite si accorciano a 10 s (8 prima della fine + 2 dopo) e restano tutti.
        val clips = Highlights.select(pts, null, 0, Highlights.Options(maxMs = 30_000))
        assertEquals(listOf(0, 1, 2), clips.map { it.index })
        assertEquals(listOf(10_000L, 10_000L, 10_000L), clips.map { it.durationMs })
    }

    @Test
    fun stillOverLimitDropsLeastImportant() {
        val pts = listOf(p(20_000, kind = "ACE"), p(40_000, pressure = "SET_POINT"), p(60_000, kind = "WINNER"))
        // Anche corti al minimo (5 + 2 = 7 s) fanno 21 s: con 20 s resta fuori la voce più in basso (di serie i vincenti).
        val clips = Highlights.select(pts, null, 0, Highlights.Options(maxMs = 20_000))
        assertEquals(listOf(0, 1), clips.map { it.index })
        assertEquals(7_000L, clips[0].durationMs)
    }

    @Test
    fun optionsFilterAndPressureFromRules() {
        // Video segnato senza "pressure": la si ricalcola con le regole. G1 vince il suo game di servizio (punti 0-3),
        // poi in risposta va 0-40 (punti 4-6): il punto 7 si gioca su palla break.
        val rules = RulesConfig()
        val raw = mutableListOf<PointMark>()
        val winners = List(8) { 0 }
        for ((i, w) in winners.withIndex()) {
            val after = PointMarks.stateAfter(rules, raw + PointMark(0, "", w), raw.size + 1)
            raw += PointMarks.mark((i + 1) * 20_000L, after, if (w == 0) Side.P1 else Side.P2, null)
        }
        val clips = Highlights.select(raw, rules, 0, Highlights.Options(enabled = setOf(Highlights.Category.BIG_POINTS)))
        assertEquals(listOf(7), clips.map { it.index })
        assertEquals(Highlights.Reason.BREAK_POINT, clips.single().reason)
    }
}
