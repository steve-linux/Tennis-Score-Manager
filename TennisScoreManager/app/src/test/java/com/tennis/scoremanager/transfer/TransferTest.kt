// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.transfer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferTest {

    @Test
    fun serverSendsOnlyWithTheRightToken() {
        var served = 0
        val server = TransferServer(payload = { "{\"version\":1}".toByteArray() }, onServed = { served++ })
        val port = server.start()
        try {
            val url = TransferServer.url("127.0.0.1", port, server.token)
            assertTrue(TransferServer.isTransferUrl(url))
            assertEquals("{\"version\":1}", TransferServer.fetch(url).decodeToString())
            assertEquals(1, served)
            val wrong = TransferServer.url("127.0.0.1", port, "0".repeat(32))
            assertTrue(runCatching { TransferServer.fetch(wrong) }.isFailure)
            assertEquals(1, served)
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
