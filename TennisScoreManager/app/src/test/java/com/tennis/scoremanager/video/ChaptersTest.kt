// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChaptersTest {
    // Set 0: game 0 (3 punti), game 1 (2 punti); set 1: game 2 (2 punti). Un punto ogni 20 s.
    private val pts = listOf(
        0 to 0, 0 to 0, 0 to 0, 0 to 1, 0 to 1, 1 to 2, 1 to 2,
    ).mapIndexed { i, (set, game) -> PointMark(at = (i + 1) * 20_000L, score = "s$i", winner = i % 2, set = set, game = game) }

    @Test
    fun pointAtPosition() {
        assertEquals(0, Chapters.indexAt(pts, 0))
        assertEquals(0, Chapters.indexAt(pts, 19_999))
        assertEquals(1, Chapters.indexAt(pts, 20_000))
        assertEquals(6, Chapters.indexAt(pts, 999_999))
        assertEquals(-1, Chapters.indexAt(emptyList(), 5))
        assertEquals(0L, Chapters.start(pts, 0))
        assertEquals(40_000L, Chapters.start(pts, 2))
    }

    @Test
    fun gamesAndSets() {
        assertEquals(3, Chapters.nextGame(pts, 1))
        assertEquals(5, Chapters.nextGame(pts, 3))
        assertNull(Chapters.nextGame(pts, 6))
        assertEquals(5, Chapters.nextSet(pts, 0))
        assertNull(Chapters.nextSet(pts, 5))
        // Indietro: a metà game torna al suo inizio, all'inizio del game va a quello prima.
        assertEquals(3, Chapters.prevGame(pts, 4, atStart = false))
        assertEquals(0, Chapters.prevGame(pts, 3, atStart = true))
        assertEquals(0, Chapters.prevSet(pts, 5, atStart = true))
        assertEquals(5, Chapters.prevSet(pts, 6, atStart = false))
    }

    @Test
    fun youtubeChaptersPerGame() {
        val text = Chapters.youtube(pts, "Inizio") { set, game -> "Set $set · Game $game" }
        assertEquals("0:00 Inizio\n1:00 Set 1 · Game 2\n1:40 Set 2 · Game 1", text)
        assertEquals("1:02:03", Chapters.time(3_723_000))
    }
}
