// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.desk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.InetAddress
import java.net.Socket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509ExtendedTrustManager

class DeskTest {
    private var t = 1_000_000L
    private val auth = DeskAuth({ t })
    private val data = FakeDesk()
    private val api = DeskApi(data, auth)
    private val ip = "192.168.1.40"

    private fun req(m: String, p: String, body: String = "", token: String? = null, from: String = ip, q: Map<String, String> = emptyMap()) =
        api.handle(DeskApi.Req(m, p, q, body, token, from)).let { it.status to it.body.toString(Charsets.UTF_8) }

    private fun field(json: String, k: String) = Regex("\"$k\":\"([^\"]+)\"").find(json)?.groupValues?.get(1)

    /** Codice giusto e "Consenti": la chiave della sessione. */
    private fun login(): String {
        val id = field(req("POST", "/pair", """{"code":"${auth.shownCode}"}""").second, "id")!!
        auth.answer(true)
        return field(req("GET", "/pair/$id").second, "token")!!
    }

    @Test fun senzaChiaveNonSiVedeNiente() {
        for ((m, p) in listOf("GET" to "/people", "GET" to "/money", "GET" to "/day", "POST" to "/fees", "GET" to "/info", "DELETE" to "/bookings/x")) {
            assertEquals(p, 401, req(m, p).first)
            assertEquals(p, 401, req(m, p, token = "sbagliata").first)
        }
    }

    @Test fun ilCodiceDaSoloNonBasta() {
        val id = field(req("POST", "/pair", """{"code":"${auth.shownCode}"}""").second, "id")!!
        assertEquals("CONFIRM", field(req("GET", "/pair/$id").second, "state"))
        assertEquals(ip, auth.pending?.ip)
        auth.answer(false)
        assertEquals(403, req("GET", "/pair/$id").first)
        assertNull(auth.connected)
    }

    @Test fun laChiaveEUnaSolaEDalloStessoPc() {
        val id = field(req("POST", "/pair", """{"code":"${auth.shownCode}"}""").second, "id")!!
        auth.answer(true)
        assertEquals(403, req("GET", "/pair/$id", from = "192.168.1.66").first)
        val token = field(req("GET", "/pair/$id").second, "token")!!
        assertEquals(403, req("GET", "/pair/$id").first)
        assertEquals(200, req("GET", "/info", token = token).first)
        assertEquals(401, req("GET", "/info", token = token, from = "192.168.1.66").first)
    }

    /** Il PC chiede il QR: col generatore di prova la riga è il testo stesso. */
    private fun qr(from: String = ip): Pair<String, String> {
        val out = req("POST", "/pair/qr", from = from).second
        return field(out, "id")!! to Regex("tsm-desk:[^\"]+").find(out)!!.value
    }

    @Test fun conIlQrSiEntraSoloDopoIlConsenti() {
        val (id, text) = qr()
        assertEquals("WAIT", field(req("GET", "/pair/$id").second, "state"))
        assertTrue(auth.scan(text))
        assertEquals(ip, auth.pending?.ip)
        assertEquals("CONFIRM", field(req("GET", "/pair/$id").second, "state"))
        auth.answer(true)
        val token = field(req("GET", "/pair/$id").second, "token")!!
        assertEquals(200, req("GET", "/info", token = token).first)
    }

    @Test fun qrRifiutatoESbagliatoENonRiusabile() {
        val (id, text) = qr()
        assertFalse(auth.scan("tsm-desk:inventato"))
        assertFalse(auth.scan("https://altro.example"))
        assertNull(auth.pending)
        assertTrue(auth.scan(text))
        auth.answer(false)
        assertEquals(403, req("GET", "/pair/$id").first)
        assertFalse("un QR vale una volta", auth.scan(text))
    }

    @Test fun qrScadeEDaUnAltroPcNonSiEntra() {
        val (id, text) = qr()
        assertEquals(403, req("GET", "/pair/$id", from = "192.168.1.66").first)
        val (other, otherText) = qr("192.168.1.66")
        assertNotEquals(text, otherText)
        assertTrue(auth.scan(otherText))
        auth.answer(true)
        assertEquals(403, req("GET", "/pair/$other").first)
        val t2 = field(req("GET", "/pair/$other", from = "192.168.1.66").second, "token")!!
        assertEquals(423, req("POST", "/pair/qr").first)
        assertEquals(200, req("POST", "/logout", token = t2, from = "192.168.1.66").first)
        val (_, old) = qr()
        t += DeskAuth.QR_MS + 1
        assertFalse(auth.scan(old))
    }

    @Test fun ilCodiceNonSiRiusaESiCambia() {
        val c = auth.shownCode
        assertEquals(DeskAuth.CODE_LEN, c.length)
        t += DeskAuth.CODE_MS + 1
        assertNotEquals(c, auth.shownCode)
        assertEquals(403, req("POST", "/pair", """{"code":"$c"}""").first)
    }

    @Test fun iTentativiVengonoFermati() {
        val c = auth.shownCode
        repeat(DeskAuth.MAX_WRONG) { assertEquals(403, req("POST", "/pair", """{"code":"AAAAAAAA"}""").first) }
        assertNotEquals("dopo troppi errori il codice cambia", c, auth.shownCode)
        assertEquals(429, req("POST", "/pair", """{"code":"${auth.shownCode}"}""").first)
        t += DeskAuth.BLOCK_MS + 1
        assertEquals(200, req("POST", "/pair", """{"code":"${auth.shownCode}"}""").first)
    }

    @Test fun unaSolaSessioneEScadenza() {
        val token = login()
        assertEquals("", auth.shownCode)
        assertEquals(423, req("POST", "/pair", """{"code":"X"}""", from = "192.168.1.66").first)
        t += DeskAuth.IDLE_MS + 1
        assertEquals(401, req("GET", "/info", token = token).first)
        assertTrue(auth.shownCode.isNotEmpty())
    }

    @Test fun scadenzaMassimaAncheSeSiLavora() {
        val token = login()
        repeat(30) { t += DeskAuth.IDLE_MS - 1; req("GET", "/info", token = token) }
        assertEquals(401, req("GET", "/info", token = token).first)
    }

    @Test fun logoutChiude() {
        val token = login()
        assertEquals(200, req("POST", "/logout", token = token).first)
        assertEquals(401, req("GET", "/info", token = token).first)
    }

    @Test fun socioNuovoECambiato() {
        val k = login()
        assertEquals(400, req("POST", "/people", """{"firstName":"Mario"}""", k).first)
        val (st, out) = req("POST", "/people", """{"firstName":"Mario","lastName":"Rossi","member":true,"birth":"1980-03-01","medicalUntil":"2020-01-01"}""", k)
        assertEquals(200, st)
        val id = field(out, "id")!!
        val p = data.list.single()
        assertEquals("Mario Rossi", p.name)
        assertNotNull(p.member)
        assertTrue(out, "MEDICAL_EXPIRED" in out)
        // Quello che il PC non vede non si perde.
        data.list = listOf(p.copy(racket = "r1", aliases = setOf("vecchio")))
        assertEquals(200, req("PUT", "/people/$id", """{"firstName":"Mario","lastName":"Rossi","nickname":"Mago","member":true}""", k).first)
        assertEquals("r1", data.list.single().racket)
        assertEquals(setOf("vecchio"), data.list.single().aliases)
        assertEquals("Mago", data.list.single().name)
        assertEquals(404, req("PUT", "/people/nessuno", """{"firstName":"A","lastName":"B"}""", k).first)
    }

    @Test fun quotaAddebitoEPagamento() {
        val k = login()
        req("POST", "/people", """{"firstName":"Mario","lastName":"Rossi","member":true}""", k)
        req("POST", "/people", """{"firstName":"Luca","lastName":"Bianchi","member":false}""", k)
        val (_, n) = req("POST", "/fees", """{"name":"Quota 2027","cents":15000,"kind":"MEMBERSHIP","chargeMembers":true}""", k)
        assertTrue(n, "\"count\":1" in n)
        val c = data.ledger.charges.single()
        assertEquals(400, req("POST", "/charges/${c.id}/pay", """{"cents":20000,"date":"2026-11-04","type":"cash"}""", k).first)
        assertEquals(400, req("POST", "/charges/${c.id}/pay", """{"cents":-5,"date":"2026-11-04","type":"cash"}""", k).first)
        assertEquals(400, req("POST", "/charges/${c.id}/pay", """{"cents":100,"date":"2026-11-04","type":"boh"}""", k).first)
        assertEquals(200, req("POST", "/charges/${c.id}/pay", """{"cents":15000,"date":"2026-11-04","type":"cash","ref":"x"}""", k).first)
        val paid = data.ledger.charges.single()
        assertEquals(0, paid.left)
        assertEquals("1/2026", paid.payments.single().receipt)
        assertEquals(200, req("DELETE", "/charges/${c.id}/payments/${paid.payments.single().id}", token = k).first)
        assertEquals(15000, data.ledger.charges.single().left)
    }

    @Test fun prenotazioniSenzaSovrapposizioni() {
        val k = login()
        val b = """{"court":"Campo 1","date":"2026-11-10","startMin":600,"minutes":60,"names":["Rossi"]}"""
        assertEquals(200, req("POST", "/bookings", b, k).first)
        assertEquals(409, req("POST", "/bookings", b, k).first)
        assertEquals(200, req("POST", "/bookings", b.replace("Campo 1", "Campo 2"), k).first)
        assertEquals(404, req("POST", "/bookings", b.replace("Campo 1", "Campo 9"), k).first)
        assertEquals(400, req("POST", "/bookings", b.replace("\"minutes\":60", "\"minutes\":0"), k).first)
        val (st, day) = req("GET", "/day", token = k, q = mapOf("date" to "2026-11-10"))
        assertEquals(200, st)
        assertTrue(day, "\"startMin\":600" in day && "Rossi" in day)
        assertEquals(1200, data.courtPlan.bookings.first().cents)
        assertEquals(200, req("DELETE", "/bookings/${data.courtPlan.bookings.first().id}", token = k).first)
        assertEquals(1, data.courtPlan.bookings.size)
    }

    @Test fun csvDaExcelItaliano() {
        val k = login()
        val csv = "﻿Cognome;Nome;Cellulare;Data di nascita;Tessera FITP;Scadenza certificato\r\n" +
            "Rossi;Mario;333 1234567;01/03/1980;123;31/12/2026\r\n" +
            "\"Dell'Orto; Jr\";Anna;;2/4/1990;;\r\n" +
            "Verdi;;;;;\r\n" +
            "Neri;Elsa;;31/31/1990;;\r\n"
        val body = kotlinx.serialization.json.Json.encodeToString(DeskCsvIn.serializer(), DeskCsvIn(csv))
        val (st, prev) = req("POST", "/people/import/preview", body, k)
        assertEquals(prev, 200, st)
        assertTrue("l'anteprima non salva niente", data.list.isEmpty())
        assertTrue(prev, "\"error\":\"NAME\"" in prev && "\"error\":\"DATE\"" in prev)
        val (_, out) = req("POST", "/people/import", body, k)
        assertTrue(out, "\"added\":2" in out)
        val mario = data.list.first { it.firstName == "Mario" }
        assertEquals("123", mario.member!!.fitpNumber)
        assertEquals("333 1234567", mario.phone)
        assertEquals("Dell'Orto; Jr", data.list.first { it.firstName == "Anna" }.lastName)
        // Rifatto: non si duplica e non si cancella niente di quello che c'è.
        data.savePlayers(listOf(mario.copy(phone = "999")))
        val (_, again) = req("POST", "/people/import", body, k)
        assertTrue(again, "\"added\":0" in again && "\"updated\":2" in again)
        assertEquals(2, data.list.size)
        assertEquals("999", data.list.first { it.firstName == "Mario" }.phone)
        assertEquals(400, req("POST", "/people/import/preview", """{"text":"a;b\n1;2"}""", k).first)
    }

    @Test fun certificatoValidoESalvato() {
        val dir = File.createTempFile("desk", "").also { it.delete() }
        val a = DeskCert.loadOrCreate(dir)
        a.cert.verify(a.cert.publicKey)
        a.cert.checkValidity()
        assertEquals("non è una CA", -1, a.cert.basicConstraints)
        assertEquals("il certificato resta lo stesso", a.fingerprint, DeskCert.loadOrCreate(dir).fingerprint)
        dir.deleteRecursively()
    }

    @Test fun hostSoloNumericiOLocalhost() {
        assertTrue(DeskServer.hostOk("192.168.1.20:8443"))
        assertTrue(DeskServer.hostOk("localhost:8443"))
        assertFalse(DeskServer.hostOk("sito-cattivo.example:8443"))
        assertFalse(DeskServer.hostOk(null))
    }

    /** Prova vera: TLS, richiesta, risposta e intestazioni di sicurezza. */
    @Test fun httpsDavvero() {
        val cert = DeskCert.create()
        val server = DeskServer(cert, api, { if (it == "index.html") "<html>".toByteArray() else null }, { a: InetAddress -> a.isLoopbackAddress })
        val port = server.start(18443)
        try {
            // Come il browser dopo aver accettato il certificato: niente controllo del nome.
            val trust = object : X509ExtendedTrustManager() {
                override fun checkClientTrusted(c: Array<X509Certificate>, a: String) {}
                override fun checkServerTrusted(c: Array<X509Certificate>, a: String) {}
                override fun checkClientTrusted(c: Array<X509Certificate>, a: String, s: Socket) {}
                override fun checkServerTrusted(c: Array<X509Certificate>, a: String, s: Socket) {}
                override fun checkClientTrusted(c: Array<X509Certificate>, a: String, e: SSLEngine) {}
                override fun checkServerTrusted(c: Array<X509Certificate>, a: String, e: SSLEngine) {}
                override fun getAcceptedIssuers() = emptyArray<X509Certificate>()
            }
            val ctx = SSLContext.getInstance("TLS").apply { init(null, arrayOf<TrustManager>(trust), null) }
            val http = HttpClient.newBuilder().sslContext(ctx).sslParameters(SSLParameters().apply { endpointIdentificationAlgorithm = "" }).version(HttpClient.Version.HTTP_1_1).build()
            fun get(p: String) = http.send(HttpRequest.newBuilder(URI("https://127.0.0.1:$port$p")).build(), HttpResponse.BodyHandlers.ofString())
            val page = get("/")
            assertEquals(200, page.statusCode())
            assertTrue(page.headers().firstValue("content-security-policy").get().startsWith("default-src 'none'"))
            assertEquals("DENY", page.headers().firstValue("x-frame-options").get())
            assertEquals(401, get("/api/people").statusCode())
            assertEquals(404, get("/../../etc/passwd").statusCode())
            assertEquals(404, get("/nascosto.kt").statusCode())
            // Sulla stessa porta in chiaro non si parla.
            val plain = runCatching { Socket("127.0.0.1", port).use { s -> s.getOutputStream().write("GET / HTTP/1.0\r\n\r\n".toByteArray()); s.soTimeout = 3000; s.getInputStream().readBytes() } }
            assertFalse(plain.getOrDefault(ByteArray(0)).toString(Charsets.ISO_8859_1).contains("HTTP/1.1 200"))
            val ssl = (ctx.socketFactory as SSLSocketFactory).createSocket("127.0.0.1", port) as javax.net.ssl.SSLSocket
            ssl.use { it.enabledProtocols = arrayOf("TLSv1.1"); assertTrue(runCatching { it.startHandshake() }.isFailure) }
        } finally { server.stop() }
    }
}
