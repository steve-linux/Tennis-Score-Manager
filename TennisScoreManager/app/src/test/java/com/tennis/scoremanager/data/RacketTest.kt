// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import com.tennis.scoremanager.model.MatchEvent
import com.tennis.scoremanager.model.PointKind
import com.tennis.scoremanager.model.RulesConfig
import com.tennis.scoremanager.model.Side
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RacketTest {

    private val hour = 3_600_000L
    private val day = 86_400_000L

    private val mono23 = Stringing("s1", date = 10 * day, brand = "Babolat", model = "RPM Blast", gaugeMm = 1.25, mainsKg = 23.0, crossesKg = 22.0)
    private val mono21 = mono23.copy(id = "s2", date = 20 * day, mainsKg = 21.0, crossesKg = 21.0)
    private val a = Racket("rA", name = "A", brand = "Babolat", model = "Pure Drive", stringings = listOf(mono23, mono21))
    private val b = Racket("rB", name = "B", brand = "Head", model = "Speed MP", stringings = listOf(Stringing("s3", 5 * day, StringType.MULTI)))

    /** Partita finita in cui P1 vince [p1] punti su [p1 + p2], con la racchetta [use] per P1. */
    private fun match(id: String, at: Long, use: RacketUse?, p1: Int, p2: Int, minutes: Int = 60, events: List<MatchEvent>? = null): PlayerStats.Played {
        val ev = events ?: (List(p1) { MatchEvent.Point(Side.P1) } + List(p2) { MatchEvent.Point(Side.P2) })
        val rec = MatchRecord(
            id = id, setup = SetupData(p1a = "Rossi", p2a = "Bianchi"), options = MatchOptions(), rules = RulesConfig(),
            events = ev, clockMs = minutes * 60_000L, startedAt = at, finished = true,
            rackets = use?.let { mapOf(Side.P1 to it) } ?: emptyMap(),
        )
        return PlayerStats.Played(rec, if (p1 >= p2) Side.P1 else Side.P2)
    }

    @Test
    fun currentStringingIsTheMostRecent() {
        assertEquals("s2", a.current?.id)
        assertEquals("A · Babolat Pure Drive", a.label)
        assertEquals("Babolat Pure Drive", a.copy(name = "").label)
    }

    @Test
    fun useAtPicksTheStringingMountedThatDay() {
        assertEquals("s1", a.useAt(15 * day).stringing?.id)
        assertEquals("s2", a.useAt(25 * day).stringing?.id)
        // Partita prima di ogni incordatura registrata: la più vecchia.
        assertEquals("s1", a.useAt(1 * day).stringing?.id)
        assertNull(Racket("x").use().stringing)
    }

    @Test
    fun sessionKeepsItsCopyWhenTheRacketChanges() {
        val use = a.use()
        val restrung = a.copy(stringings = a.stringings + mono23.copy(id = "s9", date = 30 * day, mainsKg = 25.0))
        assertEquals("s9", restrung.use().stringing?.id)
        assertEquals(21.0, use.stringing?.mainsKg)
    }

    @Test
    fun hoursCountSessionsOfThatStringingOnce() {
        val both = match("m1", 21 * day, a.useAt(21 * day), 4, 0, minutes = 90).let { p ->
            // Stessa racchetta segnata per tutti e due i lati: la partita conta una volta.
            p.copy(rec = p.rec.copy(rackets = mapOf(Side.P1 to a.useAt(21 * day), Side.P2 to a.useAt(21 * day))))
        }
        val older = match("m2", 12 * day, a.useAt(12 * day), 4, 0, minutes = 60)
        val w = WorkoutRecord("w1", startedAt = 22 * day, durationS = 1800, racket = a.useAt(22 * day))
        val sessions = RacketStats.sessions(listOf(both, older), listOf(w))
        assertEquals(2.0, RacketStats.hours("s2", sessions), 1e-9)
        assertEquals(1.0, RacketStats.hours("s1", sessions), 1e-9)
        assertEquals(3.0, RacketStats.racketHours("rA", sessions), 1e-9)
        assertEquals(4, sessions.size)
    }

    @Test
    fun configsGroupByRacketOrByStringing() {
        val sessions = RacketStats.sessions(
            listOf(
                match("m1", 12 * day, a.useAt(12 * day), 4, 0),
                match("m2", 22 * day, a.useAt(22 * day), 0, 4),
                match("m3", 22 * day, b.use(), 4, 4),
                match("m4", 23 * day, null, 4, 0),
            ),
            emptyList(),
        )
        val byRacket = RacketStats.configs(sessions, byStringing = false)
        assertEquals(listOf("rA", "rB"), byRacket.map { it.key })
        val ra = byRacket.first { it.key == "rA" }
        assertEquals(2, ra.matches)
        assertEquals(50.0, ra.winPct!!, 1e-9)
        assertEquals(50.0, ra.pointsWonPct!!, 1e-9)
        val byString = RacketStats.configs(sessions, byStringing = true)
        assertEquals(3, byString.size)
        assertEquals(100.0, byString.first { it.key == "rA/s1" }.winPct!!, 1e-9)
        assertEquals(0.0, byString.first { it.key == "rA/s2" }.pointsWonPct!!, 1e-9)
    }

    @Test
    fun serveStatsOnlyFromDetailedMatches() {
        // P1 serve il primo game: ace, doppio fallo (seconda), punto vinto sulla prima, punto vinto sulla seconda.
        val ev = listOf(
            MatchEvent.Point(Side.P1, kind = PointKind.ACE),
            MatchEvent.Point(Side.P2, kind = PointKind.DOUBLE_FAULT, secondServe = true),
            MatchEvent.Point(Side.P1),
            MatchEvent.Point(Side.P1, secondServe = true),
        )
        val detailed = match("m1", 12 * day, a.useAt(12 * day), 0, 0, events = ev)
        val plain = match("m2", 13 * day, a.useAt(13 * day), 4, 0)
        val cs = RacketStats.configs(RacketStats.sessions(listOf(detailed, plain), emptyList()), false).single()
        assertEquals(2, cs.matches)
        // Solo la partita dettagliata: 2 prime dentro su 4 punti al servizio, entrambe vinte.
        assertEquals(50.0, cs.firstServeInPct!!, 1e-9)
        assertEquals(100.0, cs.firstServeWonPct!!, 1e-9)
        assertEquals(1.0, cs.acesPerMatch!!, 1e-9)
        assertEquals(1.0, cs.doubleFaultsPerMatch!!, 1e-9)
    }

    @Test
    fun feelAveragesIgnoreMissingValues() {
        val s = RacketStats.sessions(
            emptyList(),
            listOf(
                WorkoutRecord("w1", startedAt = 1, durationS = 600, racket = b.use().copy(feel = Feel(control = 4, comfort = 2))),
                WorkoutRecord("w2", startedAt = 2, durationS = 600, racket = b.use().copy(feel = Feel(control = 2, fatigue = 5))),
                WorkoutRecord("w3", startedAt = 3, durationS = 600, racket = b.use()),
            ),
        )
        val cs = RacketStats.configs(s, false).single()
        assertEquals(3, cs.workouts)
        assertEquals(2, cs.rated)
        assertEquals(3.0, cs.control!!, 1e-9)
        assertEquals(2.0, cs.comfort!!, 1e-9)
        assertNull(cs.power)
        assertEquals(5.0, cs.fatigue!!, 1e-9)
        assertEquals(0.5, cs.hours, 1e-9)
    }

    @Test
    fun oldSavesWithoutRacketsStillLoad() {
        val json = Json { ignoreUnknownKeys = true }
        val rec = json.decodeFromString(MatchRecord.serializer(), """{"id":"m1","setup":{},"options":{},"rules":{}}""")
        assertEquals(emptyMap<Side, RacketUse>(), rec.rackets)
        val w = json.decodeFromString(WorkoutRecord.serializer(), """{"id":"w1","startedAt":1}""")
        assertNull(w.racket)
        // E le nuove si rileggono uguali.
        val withRacket = rec.copy(rackets = mapOf(Side.P2 to a.use().copy(feel = Feel(power = 3))))
        assertEquals(withRacket, json.decodeFromString(MatchRecord.serializer(), json.encodeToString(MatchRecord.serializer(), withRacket)))
    }
}
