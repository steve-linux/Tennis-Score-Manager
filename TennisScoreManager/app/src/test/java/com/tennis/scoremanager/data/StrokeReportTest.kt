// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import com.tennis.scoremanager.ble.BandProtocol
import com.tennis.scoremanager.model.Lang
import com.tennis.scoremanager.model.MatchEvent
import com.tennis.scoremanager.model.RulesConfig
import com.tennis.scoremanager.model.Side
import com.tennis.scoremanager.ui.ItStrings
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Colpi nel riepilogo: velocità del polso per tipo di colpo e colpi per punto. */
class StrokeReportTest {

    private fun record(events: List<MatchEvent> = emptyList(), strokes: Map<Side, StrokeStats> = emptyMap()) =
        MatchRecord(id = "m1", setup = SetupData(), options = MatchOptions(), rules = RulesConfig(), events = events, strokes = strokes)

    @Test
    fun wristSpeedPerStroke() {
        val st = StrokeStats().add(BandProtocol.STROKE_FOREHAND, 1400).add(BandProtocol.STROKE_FOREHAND, 1500)
            .add(BandProtocol.STROKE_SERVE, 2010)
        val rec = record(strokes = mapOf(Side.P1 to st))
        assertEquals("Dritto 1450 °/s (max 1500) · Servizio 2010 °/s (max 2010)", Reports.wristSpeedLine(rec, Side.P1, ItStrings))
        assertNull(Reports.wristSpeedLine(rec, Side.P2, ItStrings))
    }

    @Test
    fun rallyLengths() {
        val points = listOf(2, 3, 5, 12, 4, 1, 9, 6).map { MatchEvent.Point(Side.P1, strokes = it) } +
            MatchEvent.Point(Side.P2)  // punto senza colpi contati: non conta
        val st = Reports.rallyStats(record(points))!!
        assertEquals(8, st.points)
        assertEquals(5.25, st.average, 1e-9)
        assertEquals(12, st.longest)
        assertEquals(38, st.shortPct)   // 3 su 8
        assertEquals(37, st.mediumPct)  // 3 su 8 (il resto, così la somma fa 100)
        assertEquals(25, st.longPct)    // 2 su 8
        assertEquals(
            "media 5,3 · più lungo 12 · brevi (1-3) 38% · medi (4-8) 37% · lunghi (9+) 25%",
            Reports.rallyLine(st, ItStrings, Lang.IT),
        )
        assertNull(Reports.rallyStats(record(listOf(MatchEvent.Point(Side.P1)))))
    }

    @Test
    fun matchesSavedBeforeStillLoad() {
        // Partita salvata dalla prima versione dei colpi: punti senza "strokes", conteggi senza velocità.
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val old = """{"id":"m1","setup":{},"options":{},"rules":{},
            "events":[{"type":"point","winner":"P1","at":5}],
            "strokes":{"P1":{"counts":[3,0,1,0],"unknown":1}}}"""
        val rec = json.decodeFromString(MatchRecord.serializer(), old)
        assertNull((rec.events.single() as MatchEvent.Point).strokes)
        val st = rec.strokes.getValue(Side.P1)
        assertEquals(3, st.count(BandProtocol.STROKE_FOREHAND))
        assertNull(st.avgGyro(BandProtocol.STROKE_FOREHAND))  // nessuna velocità salvata: non si inventa uno zero
        assertNull(Reports.wristSpeedLine(rec, Side.P1, ItStrings))
        // E i colpi nuovi si aggiungono senza problemi.
        assertEquals(1200, st.add(BandProtocol.STROKE_SERVE, 1200).maxGyro(BandProtocol.STROKE_SERVE))
    }
}
