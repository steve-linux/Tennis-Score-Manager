// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.relay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** Principale e satellite veri, su questo computer: saluto col codice, ping e misure con conferma. */
class RelayLinkTest {

    @Test
    fun satelliteTalksToMain() {
        val atMain = LinkedBlockingQueue<RelayMsg>()
        lateinit var hub: RelayHub
        hub = RelayHub(
            onMessage = { id, conn, m ->
                atMain.add(m)
                when (m.t) {
                    RelayMsg.HELLO -> conn.send(RelayMsg(RelayMsg.WELCOME, match = "m1-abc"))
                    RelayMsg.PING -> conn.send(RelayMsg(RelayMsg.PONG, c = m.c, m = 42L))
                    RelayMsg.HR -> hub.send(id, RelayMsg(RelayMsg.ACK, seq = m.seq))
                }
            },
            onClosed = {},
        )
        val port = hub.start()
        val atSat = LinkedBlockingQueue<RelayMsg>()
        val connected = LinkedBlockingQueue<Boolean>()
        val client = RelayClient(
            "127.0.0.1", port,
            hello = { RelayMsg(RelayMsg.HELLO, token = hub.token, sat = "s1", name = "prova") },
            onConnected = { connected.add(true) },
            onMessage = { atSat.add(it) },
            onDisconnected = {},
        )
        try {
            client.start()
            assertNotNull(connected.poll(5, TimeUnit.SECONDS))
            assertEquals(RelayMsg.HELLO, atMain.poll(5, TimeUnit.SECONDS)?.t)
            assertEquals("m1-abc", atSat.poll(5, TimeUnit.SECONDS)?.match)
            assertTrue(client.send(RelayMsg(RelayMsg.PING, c = 7L)))
            assertEquals(RelayMsg(RelayMsg.PONG, c = 7L, m = 42L), atSat.poll(5, TimeUnit.SECONDS))
            assertEquals(RelayMsg.PING, atMain.poll(5, TimeUnit.SECONDS)?.t)
            assertTrue(client.send(RelayMsg(RelayMsg.HR, seq = 1, addr = "AA", bpm = 150, at = 1L, match = "m1-abc")))
            assertEquals(150, atMain.poll(5, TimeUnit.SECONDS)?.bpm)
            assertEquals(1L, atSat.poll(5, TimeUnit.SECONDS)?.seq)
        } finally {
            client.stop()
            hub.stop()
        }
    }

    @Test
    fun wrongCodeIsRefused() {
        val atMain = LinkedBlockingQueue<RelayMsg>()
        val hub = RelayHub(onMessage = { _, _, m -> atMain.add(m) }, onClosed = {})
        val port = hub.start()
        val client = RelayClient(
            "127.0.0.1", port,
            hello = { RelayMsg(RelayMsg.HELLO, token = "ffffffffffffffffffffffffffffffff", sat = "s1") },
            onConnected = {}, onMessage = {}, onDisconnected = {},
        )
        try {
            client.start()
            Thread.sleep(500)
            assertEquals(0, atMain.size)
        } finally {
            client.stop()
            hub.stop()
        }
    }
}
