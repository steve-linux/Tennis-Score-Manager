// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later
package com.tennis.scoremanager.data

import com.tennis.scoremanager.model.MatchEvent
import com.tennis.scoremanager.model.MatchFormat
import com.tennis.scoremanager.model.MatchState
import com.tennis.scoremanager.model.PenaltyKind
import com.tennis.scoremanager.model.ScoreEngine
import com.tennis.scoremanager.model.Side
import com.tennis.scoremanager.tv.TvSettings
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Aggiornamento dell'app: i file scritti da una versione uscita si leggono ancora, senza perdere niente.
 * Storage salta in silenzio le partite che non riesce a leggere, quindi un formato cambiato male le farebbe sparire:
 * qui invece il test si ferma. Gli esempi in `resources/formati/<versione>/` li ha scritti il codice di quella versione
 * (generatore in `FixtureExport.kt.txt`), con nomi di prova. A ogni versione nuova se ne aggiunge una cartella.
 */
class FormatUpgradeTest {
    private val json = Storage.JSON

    private fun text(version: String, name: String) =
        requireNotNull(javaClass.getResource("/formati/$version/$name")) { "manca formati/$version/$name" }.readText()

    /** Legge e riscrive: il file rifatto dall'app di oggi deve rileggersi uguale. */
    private fun <T> read(version: String, name: String, s: KSerializer<T>): T {
        val v = json.decodeFromString(s, text(version, name))
        assertEquals("$version/$name riscritto", v, json.decodeFromString(s, json.encodeToString(s, v)))
        return v
    }

    @Test
    fun matchesFrom260() {
        val m = read("2.6.0", "match_finished.json", MatchRecord.serializer())
        assertTrue(m.finished)
        assertEquals(10, m.events.size)
        // Tutti i tipi di evento della 2.6.0 (punto, ordine di servizio e di risposta, richiamo, pausa, correzioni, punteggio, ritiro).
        val kinds = m.events.map { it::class }.toSet()
        assertEquals(9, kinds.size)
        assertEquals(PenaltyKind.TIME, m.events.filterIsInstance<MatchEvent.Penalty>().single().kind)
        assertEquals(Side.P2, m.events.filterIsInstance<MatchEvent.Retired>().single().side)
        assertEquals("Via Prova 1", m.location?.address)
        assertEquals(90, m.batteryStart[Side.P1])
        assertEquals("Partita di prova", m.notes)
        assertTrue(m.friendly)

        val s = read("2.6.0", "match_suspended.json", MatchRecord.serializer())
        assertTrue(s.suspended)
        assertEquals(4, s.events.size)
    }

    @Test
    fun playersAndTrainingFrom260() {
        assertEquals(listOf("Mario Rossi", "Luca Bianchi"), read("2.6.0", "players.json", ListSerializer(PlayerProfile.serializer())).map { it.name })
        assertEquals("w1", read("2.6.0", "workout.json", WorkoutRecord.serializer()).id)
        assertEquals("s1", read("2.6.0", "serve_session.json", ServeSession.serializer()).id)
        assertEquals(10, read("2.6.0", "drills.json", ListSerializer(Drill.serializer())).single().steps.single().count)
        assertEquals(60.0, read("2.6.0", "goals.json", ListSerializer(Goal.serializer())).single().target, 0.0)
        assertEquals("st1", read("2.6.0", "rackets.json", ListSerializer(Racket.serializer())).single().stringings.single().id)
        assertEquals(4, read("2.6.0", "tournament.json", Tournament.serializer()).players.size)
    }

    @Test
    fun clubFrom260() {
        val book = read("2.6.0", "club_book.json", ClubBook.serializer())
        assertEquals(15000, book.fees.single().cents)
        assertEquals(15000, book.charges.single().payments.single().cents)
        assertEquals("Squadra A", read("2.6.0", "club_teams.json", ListSerializer(Team.serializer())).single().name)
        assertEquals("Corso bambini", read("2.6.0", "club_school.json", ListSerializer(Course.serializer())).single().name)
        assertEquals("Classifica sociale", read("2.6.0", "club_ladders.json", ListSerializer(Ladder.serializer())).single().name)
        assertEquals("Torneo sociale", read("2.6.0", "club_tournaments.json", ListSerializer(ClubTournament.serializer())).single().name)
    }

    /** Stesso formato di expected_scores.txt della 2.4.3. */
    private fun desc(m: MatchState) = "sets=" + m.sets.joinToString(" ") { "${it.g1}-${it.g2}" + (it.tb1?.let { t -> "(${t}-${it.tb2})" } ?: "") } +
        " games=${m.g1}-${m.g2} points=${m.pt1}-${m.pt2} winner=${m.winner} played=${m.pointsPlayed} p1Left=${m.p1Left}"

    /**
     * 2.4.3, l'ultima uscita prima della 2.6.0: partita in corso (files/matches) e ultima finita (files/last_finished.json),
     * doppio al meglio di due set con match tie-break. Rigiocando gli eventi il punteggio di oggi è quello della 2.4.3.
     */
    @Test
    fun matchesFrom243() {
        val expected = text("2.4.3", "expected_scores.txt").lines().filter { it.isNotBlank() }.associate { it.substringBefore(' ') to it.substringAfter(' ') }
        val m = read("2.4.3", "last_finished.json", MatchRecord.serializer())
        assertTrue(m.finished)
        assertEquals("Paolo Verdi", m.setup.p1b)
        assertEquals(MatchFormat.TWO_SETS_MATCH_TIEBREAK, m.rules.format)
        assertEquals(60, m.batteryEnd[Side.P2])
        assertEquals(expected["finished"], desc(ScoreEngine.replay(m.rules, m.events)))

        val s = read("2.4.3", "match_suspended.json", MatchRecord.serializer())
        assertTrue(s.suspended)
        assertEquals(expected["suspended"], desc(ScoreEngine.replay(s.rules, s.events)))
    }

    /** Le impostazioni che la 2.4.3 teneva nelle preferenze come testo JSON (setup, options, tv). */
    @Test
    fun settingsFrom243() {
        assertEquals("Circolo Prova", read("2.4.3", "prefs_setup.json", SetupData.serializer()).club)
        val o = read("2.4.3", "prefs_options.json", MatchOptions.serializer())
        assertEquals(PlayMode.BANDS, o.mode)
        assertEquals(Side.P2, o.tossWinner)
        assertTrue(o.voiceFiles)
        val tv = read("2.4.3", "prefs_tv.json", TvSettings.serializer())
        assertTrue(tv.enabled)
        assertEquals("Torneo di prova", tv.title)
        assertEquals("#00FF00", tv.color1)
    }
}
