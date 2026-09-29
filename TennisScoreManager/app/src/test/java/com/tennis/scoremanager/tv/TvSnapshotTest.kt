package com.tennis.scoremanager.tv

import com.tennis.scoremanager.CountdownKind
import com.tennis.scoremanager.LiveMatch
import com.tennis.scoremanager.Screen
import com.tennis.scoremanager.data.MatchOptions
import com.tennis.scoremanager.data.MatchRecord
import com.tennis.scoremanager.data.SetupData
import com.tennis.scoremanager.model.Lang
import com.tennis.scoremanager.model.MatchEvent
import com.tennis.scoremanager.model.RulesConfig
import com.tennis.scoremanager.model.ScoreEngine
import com.tennis.scoremanager.model.Side
import com.tennis.scoremanager.ui.ItStrings
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TvSnapshotTest {

    private val setup = SetupData(club = "TC Roma", court = "3", p1a = "Stefano", p2a = "Mario")
    private val rules = RulesConfig()

    private fun match(points: List<Side>, started: Boolean = true, suspended: Boolean = false): LiveMatch {
        val events = points.map { MatchEvent.Point(it) }
        val rec = MatchRecord(
            id = "m1", setup = setup, options = MatchOptions(), rules = rules, events = events,
            startedAt = if (started) 1L else null, suspended = suspended,
        )
        return LiveMatch(rec, ScoreEngine.replay(rules, events))
    }

    private fun input(
        screen: Screen = Screen.MATCH,
        match: LiveMatch? = null,
        countdown: CountdownKind? = null,
        message: String? = null,
        tv: TvSettings = TvSettings(enabled = true),
    ) = TvInput(
        screen = screen, setup = setup, lang = Lang.IT, firstServer = Side.P2, match = match,
        clockMs = 65_000, clockRunning = true, countdown = countdown, countdownLeftMs = 20_000,
        message = message, tv = tv, strings = ItStrings, seq = 7,
    )

    @Test
    fun idleBeforeAnyMatch() {
        val s = TvSnapshots.build(input(screen = Screen.SETUP))
        assertEquals("idle", s.phase)
        assertEquals(listOf("", ""), s.points)
        assertNull(s.server)
        assertEquals(0L, s.clockMs)
        assertEquals("TC Roma · Campo 3", s.title)
        assertEquals(listOf("Stefano", "Mario"), s.players.map { it.name })
    }

    @Test
    fun readyOnStartScreenShowsFirstServer() {
        val s = TvSnapshots.build(input(screen = Screen.START))
        assertEquals("ready", s.phase)
        assertEquals(listOf("0", "0"), s.points)
        assertEquals(1, s.server)
    }

    @Test
    fun pointsGamesAndAdvantage() {
        // 1-0 in game, poi 40-40 e vantaggio Mario
        val pts = List(4) { Side.P1 } + listOf(Side.P1, Side.P1, Side.P1, Side.P2, Side.P2, Side.P2, Side.P2)
        val s = TvSnapshots.build(input(match = match(pts), countdown = CountdownKind.SHOT_CLOCK, message = "Palla break"))
        assertEquals("play", s.phase)
        assertEquals(listOf(1, 0), s.games)
        assertEquals(listOf("40", "AD"), s.points)
        assertEquals("SERVIZIO", s.countdown!!.label)
        assertTrue(s.countdown!!.shot)
        assertEquals(20_000L, s.countdown!!.leftMs)
        assertEquals("Palla break", s.message)
        assertTrue(s.clockRunning)
    }

    @Test
    fun finishedShowsLastSetAndWinner() {
        // 6-0 6-0 per Stefano
        val s = TvSnapshots.build(input(screen = Screen.SUMMARY, match = match(List(48) { Side.P1 }), countdown = CountdownKind.SHOT_CLOCK, message = "x"))
        assertEquals("finished", s.phase)
        assertEquals(0, s.winner)
        assertEquals(listOf(6, 0), s.games)
        assertEquals(listOf(2, 0), s.sets)
        assertEquals(2, s.done.size)
        assertEquals(listOf("", ""), s.points)
        assertNull(s.server)
        assertNull(s.countdown)
        assertNull(s.message)
        assertFalse(s.clockRunning)
    }

    @Test
    fun suspendedHidesTimersAndMessages() {
        val s = TvSnapshots.build(input(match = match(listOf(Side.P1), suspended = true), countdown = CountdownKind.SHOT_CLOCK, message = "x"))
        assertEquals("suspended", s.phase)
        assertEquals(listOf("15", "0"), s.points)
        assertNull(s.countdown)
        assertNull(s.message)
    }

    @Test
    fun settingsHideTimersAndMessages() {
        val tv = TvSettings(enabled = true, showTimers = false, showMessages = false, title = " Torneo sociale ", color1 = "#2F80FF")
        val s = TvSnapshots.build(input(match = match(emptyList()), countdown = CountdownKind.CHANGEOVER, message = "x", tv = tv))
        assertNull(s.countdown)
        assertNull(s.message)
        assertEquals("Torneo sociale", s.title)
        assertEquals("#2F80FF", s.players[0].color)
        assertFalse(s.show.timers)
    }

    @Test
    fun jsonIsOneLineWithSignature() {
        val s = TvSnapshots.build(input(match = match(listOf(Side.P2))))
        val json = Json { encodeDefaults = true }.encodeToString(TvSnapshot.serializer(), s)
        assertFalse('\n' in json)  // una riga sola: così viaggia in un unico evento SSE
        assertTrue("\"tsm\":1" in json)  // la ricerca del tabellone riconosce il server da questo
    }

    @Test
    fun defaultTitle() {
        assertEquals("", TvSnapshots.defaultTitle(SetupData(), ItStrings))
        assertEquals("Campo Centrale", TvSnapshots.defaultTitle(SetupData(court = "Campo Centrale"), ItStrings))
        assertEquals("TC Roma", TvSnapshots.defaultTitle(SetupData(club = "TC Roma"), ItStrings))
    }

    @Test
    fun manualAddresses() {
        assertEquals("192.168.43.1" to 8080, ScoreboardFinder.parseAddress("192.168.43.1"))
        assertEquals("192.168.43.1" to 8081, ScoreboardFinder.parseAddress(" http://192.168.43.1:8081/ "))
        assertNull(ScoreboardFinder.parseAddress(""))
        assertNull(ScoreboardFinder.parseAddress("10.0.0.2:99999"))
    }
}
