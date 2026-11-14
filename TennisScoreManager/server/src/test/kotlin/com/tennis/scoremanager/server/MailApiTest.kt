// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.server

import com.tennis.scoremanager.data.BookingRules
import com.tennis.scoremanager.data.ClubSync
import com.tennis.scoremanager.data.CourtSettings
import com.tennis.scoremanager.data.SrvChatMsg
import com.tennis.scoremanager.data.SrvMe
import com.tennis.scoremanager.data.SrvSeek
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files
import java.time.ZoneId
import java.time.ZonedDateTime
import com.tennis.scoremanager.data.SrvMail

/** La casella dei file: la segreteria lascia una scheda a un giocatore, il giocatore un risultato alla segreteria. */
class MailApiTest {
    private val zone = ZoneId.of("Europe/Rome")
    private var now = ZonedDateTime.of(2026, 11, 9, 8, 0, 0, 0, zone).toInstant().toEpochMilli()
    private lateinit var store: Store
    private lateinit var api: Api
    private lateinit var admin: String
    private val json = Json { ignoreUnknownKeys = true }

    @Before fun setUp() {
        store = Store(Files.createTempDirectory("tsm-mail").toFile()) { now }
        api = Api(store, Limiter(max = 1000) { now }, pusher = { _, _, _, _ -> 201 }, async = false)
        admin = field(call("POST", "/admin/pair", """{"code":"${store.ensureSetupCode()}"}""").body, "token")
    }

    private fun call(method: String, path: String, body: String = "", token: String? = null) = api.handle(Req(method, "/api/v1$path", body = body, token = token, ip = "1.1.1.1"))
    private fun field(j: String, k: String) = Json.parseToJsonElement(j).jsonObject[k]!!.jsonPrimitive.content
    private fun mail(path: String, t: String) = json.decodeFromString(ListSerializer(SrvMail.serializer()), call("GET", path, token = t).body)
    private fun player(id: String, name: String): String {
        val code = field(call("POST", "/admin/invites", """{"player":"$id","name":"$name"}""", admin).body, "code")
        return field(call("POST", "/claim", """{"code":"$code"}""").body, "token")
    }

    @Test fun ogniFileArrivaSoloAlSuoDestinatario() {
        val mario = player("p1", "Mario"); val luca = player("p2", "Luca")
        assertEquals(200, call("POST", "/admin/mail", """{"to":"p1","kind":"card","text":"{\"x\":1}"}""", admin).status)
        assertEquals(404, call("POST", "/admin/mail", """{"to":"nessuno","kind":"card","text":"{}"}""", admin).status)
        assertEquals(400, call("POST", "/admin/mail", """{"to":"p1","kind":"virus","text":"{}"}""", admin).status)
        assertEquals(1, mail("/mail", mario).size)
        assertTrue(mail("/mail", luca).isEmpty())
        // Senza chiave niente; un giocatore non legge la casella della segreteria.
        assertEquals(401, call("GET", "/mail").status)
        assertTrue(call("GET", "/admin/mail", token = mario).status in setOf(401, 403))
        val m = mail("/mail", mario).single()
        assertEquals("card", m.kind)
        // Luca non può togliere il file di Mario; Mario sì.
        call("DELETE", "/mail/${m.id}", token = luca)
        assertEquals(1, mail("/mail", mario).size)
        call("DELETE", "/mail/${m.id}", token = mario)
        assertTrue(mail("/mail", mario).isEmpty())
    }

    @Test fun ilRisultatoTornaAllaSegreteriaColMittente() {
        val mario = player("p1", "Mario")
        assertEquals(200, call("POST", "/mail", """{"to":"p2","kind":"result","text":"{\"r\":1}"}""", mario).status)
        val got = mail("/admin/mail", admin).single()
        assertEquals("p1", got.from)
        assertEquals("Mario", got.fromName)
        assertEquals("result", got.kind)
        call("DELETE", "/admin/mail/${got.id}", token = admin)
        assertTrue(mail("/admin/mail", admin).isEmpty())
        // Dopo trenta giorni un file non preso sparisce.
        call("POST", "/mail", """{"kind":"result","text":"{}"}""", mario)
        now += 31L * 86_400_000L
        assertTrue(mail("/admin/mail", admin).isEmpty())
    }
}
