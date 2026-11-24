// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.video

import com.tennis.scoremanager.data.Backhand
import com.tennis.scoremanager.data.Hand
import com.tennis.scoremanager.data.MatchOptions
import com.tennis.scoremanager.data.MatchRecord
import com.tennis.scoremanager.data.PlayerProfile
import com.tennis.scoremanager.data.SetupData
import com.tennis.scoremanager.model.MatchEvent
import com.tennis.scoremanager.model.MatchFormat
import com.tennis.scoremanager.model.RulesConfig
import com.tennis.scoremanager.model.Side
import com.tennis.scoremanager.ui.ItStrings
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HighlightIntroTest {

    private val rules = RulesConfig(format = MatchFormat.MATCH_TIEBREAK_ONLY)
    private val setup = SetupData(club = "TC Verona", p1a = "Mario Rossi", p1aId = "r", p1aRank = "4.1", p2a = "Bianchi")

    private fun day(y: Int, m: Int, d: Int) = Calendar.getInstance().apply { clear(); set(y, m - 1, d, 12, 0) }.timeInMillis

    /** Un super tie-break finito 10-0 per [winner]. */
    private fun rec(id: String, end: Long, winner: Side, su: SetupData = setup) = MatchRecord(
        id = id, setup = su, options = MatchOptions(), rules = rules, events = List(10) { MatchEvent.Point(winner = winner) },
        startedAt = end - 3_600_000, endedAt = end, updatedAt = end, finished = true,
    )

    private val rossi = PlayerProfile(
        id = "r", name = "Mario Rossi", firstName = "Mario", lastName = "Rossi", birth = day(1990, 6, 15), heightCm = 183,
        hand = Hand.LEFT, backhand = Backhand.TWO, club = "CT Padova",
    )

    @Test
    fun showsPlayersDataAndHistoryBeforeTheMatch() {
        val start = day(2026, 11, 10)
        val now = rec("oggi", start + 3_600_000, Side.P1)
        val history = listOf(
            rec("a", day(2026, 9, 1), Side.P1),
            rec("b", day(2026, 10, 1), Side.P2),
            now,
            rec("dopo", day(2026, 12, 1), Side.P1),
        )
        val c = HighlightIntro.card(now.copy(startedAt = start), ItStrings, listOf(rossi), history, start, "10 novembre 2026", day = { if (it in day(2026, 10, 1)..day(2026, 10, 2)) "ottobre-data" else "altra" })
        assertEquals("TC VERONA".lowercase(), c.title.lowercase())
        assertEquals(listOf("10 novembre 2026"), c.place)
        assertEquals(listOf(HighlightIntro.Person("Mario", "Rossi", null)), c.people.getValue(Side.P1))
        assertEquals(listOf(HighlightIntro.Person("", "Bianchi", null)), c.people.getValue(Side.P2))
        val rows = c.rows.associate { it.label to (it.a to it.b) }
        assertEquals(mapOf(Side.P1 to "4.1", Side.P2 to "–"), c.ranks)
        // Età e mano non si mostrano più (scelta del 24 nov): restano altezza, rovescio, circolo e bilancio.
        assertEquals(null, rows[ItStrings.heartAge])
        assertEquals("183 cm" to "–", rows[ItStrings.introHeight])
        assertEquals(false, c.rows.any { it.a == ItStrings.handLeft })
        assertEquals("CT Padova" to "–", rows[ItStrings.introClub])
        // Solo le due partite di prima: né questa né quella dopo.
        assertEquals("1–1" to "1–1", rows[ItStrings.introRecord])
        assertEquals(1 to 1, c.h2h)
        // Forma: le due partite di prima, dalla più vecchia (vinta) alla più recente (persa).
        assertEquals("V S" to "S V", rows[ItStrings.introForm])
        // L'ultima volta: la partita di ottobre, data e punteggio.
        assertEquals(true, c.lastMeeting?.startsWith("ottobre-data"))
    }

    @Test
    fun withoutDataOnlyNamesAndEvent() {
        val su = SetupData(p1a = "Verdi", p2a = "Neri", event = "Open d'autunno", category = "Lim. 4.1", round = "Finale")
        val r = rec("x", day(2026, 11, 10), Side.P2, su)
        val c = HighlightIntro.card(r, ItStrings, emptyList(), listOf(r), day(2026, 11, 10), "10 novembre 2026")
        assertEquals("Open d'autunno", c.title)
        assertEquals("Lim. 4.1 · Finale", c.subtitle)
        assertEquals(emptyList<HighlightIntro.Row>(), c.rows)
        assertEquals(emptyMap<Side, String>(), c.ranks)
        assertNull(c.h2h)
    }

    @Test
    fun splitsNames() {
        assertEquals("Mario" to "Rossi", HighlightIntro.split("Mario Rossi"))
        assertEquals("Anna Maria" to "Verdi", HighlightIntro.split(" Anna Maria Verdi "))
        assertEquals("" to "Neri", HighlightIntro.split("Neri"))
    }
}
