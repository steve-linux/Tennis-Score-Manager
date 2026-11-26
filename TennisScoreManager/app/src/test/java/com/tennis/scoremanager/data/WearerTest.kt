// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import com.tennis.scoremanager.model.RulesConfig
import com.tennis.scoremanager.model.Side
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Quattro fasce nel doppio: chi le porta, e il battito dei compagni salvato accanto a quello di prima. */
class WearerTest {

    private fun rec() = MatchRecord(id = "m", setup = SetupData(doubles = true), options = MatchOptions(), rules = RulesConfig())

    @Test
    fun mainWearersKeepTheSideNumbers() {
        // Il secondo telefono manda il numero: 0 e 1 devono restare i lati di prima.
        assertEquals(Side.P1.ordinal, Wearer.P1.ordinal)
        assertEquals(Side.P2.ordinal, Wearer.P2.ordinal)
        assertEquals(Wearer.P2B, Wearer.of(Side.P2, partner = true))
        assertEquals(1, Wearer.P1B.index)
    }

    @Test
    fun partnerTracksGoBesideTheOldOnes() {
        val tracks = mapOf(
            Wearer.P1 to HeartTrack(listOf(0, 5), listOf(120, 130)),
            Wearer.P1B to HeartTrack(listOf(0, 5), listOf(140, 150)),
            Wearer.P2B to HeartTrack(listOf(0), listOf(110)),
        )
        val r = rec().withHeart(tracks)
        assertEquals(setOf(Side.P1), r.heart.keys)
        assertEquals(setOf(Side.P1, Side.P2), r.heartPartner.keys)
        assertEquals(tracks, r.heartTracks())
        assertEquals(listOf(140, 150), r.heartOf(Wearer.P1B)?.bpm)
        assertNull(r.heartOf(Wearer.P2))
    }

    @Test
    fun savedAndReadBackWithPartners() {
        val r = rec().withHeart(mapOf(Wearer.P2 to HeartTrack(listOf(0), listOf(100)), Wearer.P2B to HeartTrack(listOf(0), listOf(160))))
        val back = Storage.JSON.decodeFromString(MatchRecord.serializer(), Storage.JSON.encodeToString(MatchRecord.serializer(), r))
        assertEquals(r.heartTracks(), back.heartTracks())
    }

    @Test
    fun oldMatchesWithoutPartnersStillOpen() {
        // Una partita salvata prima: solo "heart", per lato.
        val old = Storage.JSON.encodeToString(MatchRecord.serializer(), rec().copy(heart = mapOf(Side.P1 to HeartTrack(listOf(0), listOf(90)))))
            .replace(",\"heartPartner\":{}", "")
        val back = Storage.JSON.decodeFromString(MatchRecord.serializer(), old)
        assertTrue(back.heartPartner.isEmpty())
        assertEquals(listOf(90), back.heartOf(Wearer.P1)?.bpm)
    }
}
