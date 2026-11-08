// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URI
import java.nio.file.Files

class TunnelTest {
    private val dir = Files.createTempDirectory("tsm-tunnel").toFile()
    private val request = File(dir, "tunnel-request")
    private val status = File(dir, "status.json")
    private val store = Store(dir)
    private val api = Api(store, async = false, tunnel = Tunnel(request, status))
    private val code = store.ensureSetupCode()!!

    private fun call(method: String, path: String, body: String = "", token: String? = null) = api.handle(Req(method, "/api/v1$path", body = body, token = token))
    private fun admin() = Regex("\"token\":\"([^\"]+)\"").find(call("POST", "/admin/pair", """{"code":"$code"}""").body)!!.groupValues[1]

    @Test fun soloLaSegreteriaToccaIlTunnel() {
        assertEquals(401, call("GET", "/admin/tunnel").status)
        assertEquals(401, call("POST", "/admin/tunnel", """{"on":true}""").status)
        assertFalse(request.exists())
    }

    @Test fun senzaAiutanteLoDice() {
        val k = admin()
        assertTrue(call("GET", "/admin/tunnel", token = k).body.contains("\"available\":false"))
        assertEquals(409, call("POST", "/admin/tunnel", """{"on":true}""", k).status)
        assertFalse(request.exists())
    }

    @Test fun accendeSpegneELeggeLoStato() {
        val k = admin()
        status.writeText("""{"state":"OFF"}""")
        assertEquals(200, call("POST", "/admin/tunnel", """{"on":true}""", k).status)
        assertEquals("on\n", request.readText())
        status.writeText("""{"state":"ON","url":"https://tsm-circolo.tail1.ts.net","link":"","code":""}""")
        val on = call("GET", "/admin/tunnel", token = k).body
        assertTrue(on, "\"available\":true" in on && "tail1.ts.net" in on)
        call("POST", "/admin/tunnel", """{"on":false}""", k)
        assertEquals("off\n", request.readText())
        assertEquals(400, call("POST", "/admin/tunnel", "storia", k).status)
    }

    private fun freePort() = ServerSocket(0).use { it.localPort }

    private fun post(port: Int, path: String, body: String, header: Pair<String, String>? = null): Pair<Int, String> {
        val c = URI("http://127.0.0.1:$port/api/v1$path").toURL().openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.doOutput = true
        header?.let { c.setRequestProperty(it.first, it.second) }
        c.outputStream.use { it.write(body.toByteArray()) }
        val code = c.responseCode
        return code to ((if (code < 400) c.inputStream else c.errorStream)?.bufferedReader()?.readText().orEmpty())
    }

    /** Il punto delicato: dal tunnel non si abbina, anche se Tailscale non scrivesse nessuna intestazione. */
    @Test fun dalTunnelNonSiAbbinaMai() {
        val lan = freePort(); val tun = freePort()
        val server = start(store, "127.0.0.1", lan, tun)
        try {
            val body = """{"code":"$code"}"""
            val far = post(tun, "/admin/pair", body)
            assertEquals(far.second, 403, far.first)
            assertTrue(far.second, "PAIR_LOCAL_ONLY" in far.second)
            assertEquals(403, post(tun, "/admin/pair", body, "X-Forwarded-For" to "127.0.0.1").first)
            assertEquals(200, post(lan, "/admin/pair", body).first)
        } finally { server.stop(0) }
    }
}
