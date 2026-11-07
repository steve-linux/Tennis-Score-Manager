// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.server

import com.tennis.scoremanager.data.SrvMe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files

/** Numero e PIN: il giocatore se li sceglie dopo l'invito e poi rientra da solo, anche da un altro telefono. */
class PinApiTest {
    private var now = 1_800_000_000_000L
    private lateinit var store: Store
    private lateinit var api: Api
    private lateinit var admin: String
    private val json = Json { ignoreUnknownKeys = true }

    @Before fun setUp() {
        store = Store(Files.createTempDirectory("tsm-pin").toFile()) { now }
        api = Api(store, Limiter(max = 1000) { now }, async = false)
        admin = field(call("POST", "/admin/pair", """{"code":"${store.ensureSetupCode()}"}""").body, "token")
    }

    private fun call(method: String, path: String, body: String = "", token: String? = null, ip: String = "1.1.1.1") =
        api.handle(Req(method, "/api/v1$path", body = body, token = token, ip = ip))
    private fun field(j: String, k: String) = Json.parseToJsonElement(j).jsonObject[k]!!.jsonPrimitive.content
    private fun me(t: String) = json.decodeFromString(SrvMe.serializer(), call("GET", "/me", token = t).body)

    private fun player(id: String, name: String): String {
        val code = field(call("POST", "/admin/invites", """{"player":"$id","name":"$name"}""", admin).body, "code")
        return field(call("POST", "/claim", """{"code":"$code"}""").body, "token")
    }

    private fun login(phone: String, pin: String) = call("POST", "/login", """{"phone":"$phone","pin":"$pin"}""")

    @Test fun conNumeroEPinSiRientraDaUnAltroTelefono() {
        val t = player("p1", "Mario Rossi")
        assertFalse(me(t).hasPin)
        assertEquals(200, call("POST", "/pin", """{"phone":"+39 333 123 4567","pin":"2468"}""", t).status)
        assertTrue(me(t).hasPin)
        assertEquals("567", me(t).phone)
        // Il numero si riconosce scritto in un altro modo.
        val r = login("3331234567", "2468")
        assertEquals(r.body, 200, r.status)
        val t2 = field(r.body, "token")
        assertEquals("Mario Rossi", me(t2).name)
        assertEquals("il primo telefono resta dentro", 200, call("GET", "/me", token = t).status)
        // Il PIN non è mai nel file in chiaro.
        assertFalse(store.dir.walkTopDown().filter { it.isFile }.any { "2468" in it.readText() })
    }

    @Test fun pinENumeroControllati() {
        val t = player("p1", "Mario Rossi")
        assertEquals(400, call("POST", "/pin", """{"phone":"3331234567","pin":"12"}""", t).status)
        assertEquals(400, call("POST", "/pin", """{"phone":"3331234567","pin":"12ab"}""", t).status)
        assertEquals(400, call("POST", "/pin", """{"phone":"123","pin":"1234"}""", t).status)
        assertEquals(401, call("POST", "/pin", """{"phone":"3331234567","pin":"1234"}""").status)
        // Lo stesso numero non serve a due giocatori.
        call("POST", "/pin", """{"phone":"3331234567","pin":"1234"}""", t)
        val other = player("p2", "Luca Bianchi")
        val taken = call("POST", "/pin", """{"phone":"0039 333 1234567","pin":"9999"}""", other)
        assertEquals(409, taken.status)
        assertTrue("PHONE_TAKEN" in taken.body)
    }

    @Test fun troppiPinSbagliatiBloccanoPoiLoAnnullano() {
        val t = player("p1", "Mario Rossi")
        call("POST", "/pin", """{"phone":"3331234567","pin":"2468"}""", t)
        assertEquals(403, login("3339999999", "2468").status)
        repeat(PIN_TRIES) { assertEquals(403, login("3331234567", "0000").status) }
        // Bloccato un quarto d'ora, anche col PIN giusto.
        assertEquals(429, login("3331234567", "2468").status)
        now += 16 * 60_000L
        assertEquals(200, login("3331234567", "2468").status)
        // Dopo il giusto si riparte da zero; fino a PIN_MAX sbagliati il PIN non vale più.
        var last = ""
        repeat(PIN_MAX) { i ->
            if (i > 0 && i % PIN_TRIES == 0) now += 16 * 60_000L
            last = login("3331234567", "0000").body
        }
        assertTrue(last, "PIN_RESET" in last)
        assertEquals(403, login("3331234567", "2468").status)
        assertFalse(me(t).hasPin)
    }

    @Test fun laSegreteriaVedeEAzzeraIlPin() {
        val t = player("p1", "Mario Rossi")
        call("POST", "/pin", """{"phone":"3331234567","pin":"2468"}""", t)
        assertTrue(call("GET", "/admin/state", token = admin).body.contains("\"hasPin\":true"))
        assertEquals(401, call("DELETE", "/admin/players/p1/pin").status)
        assertEquals(200, call("DELETE", "/admin/players/p1/pin", token = admin).status)
        assertEquals(403, login("3331234567", "2468").status)
        assertFalse(me(t).hasPin)
        assertEquals("il telefono già dentro resta dentro", 200, call("GET", "/me", token = t).status)
    }
}
