// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Dritto e rovesci veri (punti del corpo di MoveNet, un fotogramma ogni 66 ms): i momenti attesi li ho guardati sui
 * fotogrammi, con un fotogramma di tolleranza.
 */
class GroundPoseRealTest {
    private fun load(name: String): List<PoseFrame> =
        Json.decodeFromString(ListSerializer(PoseFrame.serializer()), javaClass.getResource("/pose/$name")!!.readText())

    private fun check(name: String, prep: LongRange, contact: LongRange, finish: LongRange, anchor: Long = 0) {
        val m = ServePose.groundMetrics(load(name), anchor = anchor)
        println("$name: $m")
        assertNotNull(name, m)
        assertTrue("$name preparazione ${m!!.prepT}", m.prepT in prep)
        assertTrue("$name impatto ${m.contactT}", m.contactT in contact)
        assertTrue("$name finale ${m.finishT}", m.finishT in finish)
    }

    @Test
    fun forehandSeenFromTheFront() = check("forehand_front.json", -850L..-650L, -460L..-320L, -130L..340L)

    /**
     * Lo stesso dritto letto dall'app con la seconda lettura a 33 ms e senza colpo riconosciuto (si cerca verso +0,7 s):
     * a +633 ms il video ha uno stacco con lo zoom, che non è un colpo.
     */
    @Test
    fun forehandWithACutInTheVideo() = check("forehand_front_refined.json", -850L..-650L, -460L..-320L, -130L..340L, anchor = 700)

    /** Nel finale i due polsi stanno vicini e MoveNet li scambia: il salto (+567 ms) non è l'impatto. */
    @Test
    fun forehandWithWristsSwappedInTheFinish() = check("forehand_front_swap.json", -700L..-120L, -60L..80L, 270L..670L, anchor = 700)

    /**
     * Rovescio a due mani ripreso di fronte: l'apertura va in profondità e non si vede (per questo si riprende di
     * lato), e a 66 ms l'impatto può cadere un fotogramma dopo (nell'app lo sistema la seconda lettura a 33 ms).
     */
    @Test
    fun twoHandedBackhandFromTheFront() = check("backhand_two_hands.json", 0L..2000L, 2050L..2190L, 2240L..2460L)

    @Test
    fun oneHandedBackhand() = check("backhand_one_hand.json", -600L..-380L, -330L..-180L, -130L..10L)
}
