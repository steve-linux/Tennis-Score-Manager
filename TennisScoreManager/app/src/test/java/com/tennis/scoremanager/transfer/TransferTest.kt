// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.transfer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class TransferTest {

    @Test
    fun serverSendsOnlyWithTheRightToken() {
        // onServed arriva dal thread del server dopo l'invio: chi riceve può aver già finito di leggere.
        val served = AtomicInteger()
        val firstServed = CountDownLatch(1)
        val server = TransferServer(
            payload = { "{\"version\":1}".toByteArray() },
            onServed = { served.incrementAndGet(); firstServed.countDown() },
        )
        val port = server.start()
        try {
            val url = TransferServer.url("127.0.0.1", port, server.token)
            assertTrue(TransferServer.isTransferUrl(url))
            assertEquals("{\"version\":1}", TransferServer.fetch(url).decodeToString())
            assertTrue(firstServed.await(5, TimeUnit.SECONDS))
            assertEquals(1, served.get())
            val wrong = TransferServer.url("127.0.0.1", port, "0".repeat(32))
            assertTrue(runCatching { TransferServer.fetch(wrong) }.isFailure)
            assertEquals(1, served.get())
        } finally {
            server.stop()
        }
    }

    @Test
    fun otherQrCodesAreRejected() {
        assertFalse(TransferServer.isTransferUrl("https://example.com/tsm-transfer/" + "a".repeat(32)))
        assertFalse(TransferServer.isTransferUrl("http://192.168.1.5:8080/"))
        assertFalse(TransferServer.isTransferUrl("WIFI:S:casa;T:WPA;P:segreta;;"))
    }
}
