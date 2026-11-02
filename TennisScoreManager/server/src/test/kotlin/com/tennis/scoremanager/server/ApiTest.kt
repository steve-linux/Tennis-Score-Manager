// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.server

import com.tennis.scoremanager.data.BookingRules
import com.tennis.scoremanager.data.Busy
import com.tennis.scoremanager.data.ClubSync
import com.tennis.scoremanager.data.CourtSettings
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.ZoneId
import java.time.ZonedDateTime

class ApiTest {
    private val zone = ZoneId.of("Europe/Rome")
    /** Lunedì 9 novembre 2026 alle [h], ora del circolo. */
    private fun mon(h: Int, plusDays: Long = 0) = ZonedDateTime.of(2026, 11, 9, h, 0, 0, 0, zone).plusDays(plusDays).toInstant().toEpochMilli()

    private var now = mon(10)
    private lateinit var dir: File
    private lateinit var store: Store
    private lateinit var api: Api
    private val json = Json { encodeDefaults = true }

    @Before fun setUp() {
        dir = Files.createTempDirectory("tsm-server").toFile()
        store = Store(dir) { now }
        api = Api(store, Limiter(max = 5) { now })
    }

    private fun call(method: String, path: String, body: String = "", token: String? = null, query: Map<String, String> = emptyMap(), ip: String = "1.2.3.4") =
        api.handle(Req(method, "/api/v1$path", query, body, token, ip))

    private fun obj(r: Resp): JsonObject = Json.parseToJsonElement(r.body).jsonObject

    private fun pairAndSync(): String {
        val code = store.ensureSetupCode()!!
        val r = call("POST", "/admin/pair", """{"code":"${code.lowercase().chunked(5).joinToString("-")}"}""")
        assertEquals(200, r.status)
        val admin = obj(r)["token"]!!.jsonPrimitive.content
        val sync = ClubSync(
            club = "TC Prova", courts = listOf("Campo 1", "Campo 2"),
            settings = CourtSettings(openHour = 8, closeHour = 22, centsPerHour = 1000, centsPerHourLights = 1500),
            busy = listOf(Busy("Campo 2", mon(15), mon(16))), rules = BookingRules(maxFuture = 1),
        )
        assertEquals(200, call("PUT", "/admin/sync", json.encodeToString(ClubSync.serializer(), sync), admin).status)
        return admin
    }

    private fun invite(admin: String, player: String = "p1", name: String = "Mario"): String {
        val r = call("POST", "/admin/invites", """{"player":"$player","name":"$name"}""", admin)
        assertEquals(200, r.status)
        val code = obj(r)["code"]!!.jsonPrimitive.content
        val c = call("POST", "/claim", """{"code":"$code"}""")
        assertEquals(200, c.status)
        return obj(c)["token"]!!.jsonPrimitive.content
    }

    @Test fun pairingOnlyOnceAndKeysAreHashed() {
        val admin = pairAndSync()
        assertFalse(File(dir, Store.SETUP_FILE).exists())
        assertEquals(403, call("POST", "/admin/pair", """{"code":"AAAAAAAAAA"}""").status)
        assertEquals(401, call("PUT", "/admin/sync", "{}", "sbagliata").status)
        assertFalse(File(dir, "state.json").readText().contains(admin))
        // Riparte dal file: la chiave vale ancora.
        val again = Api(Store(dir) { now })
        assertEquals(200, again.handle(Req("GET", "/api/v1/admin/state", token = admin)).status)
    }

    @Test fun inviteWorksOnce() {
        val admin = pairAndSync()
        val r = call("POST", "/admin/invites", """{"player":"p1","name":"Mario"}""", admin)
        val code = obj(r)["code"]!!.jsonPrimitive.content
        assertEquals(200, call("POST", "/claim", """{"code":"$code"}""").status)
        assertEquals(403, call("POST", "/claim", """{"code":"$code"}""").status)
    }

    @Test fun inviteExpires() {
        val admin = pairAndSync()
        val code = obj(call("POST", "/admin/invites", """{"player":"p1","name":"Mario","days":1}""", admin))["code"]!!.jsonPrimitive.content
        now += 2 * 86_400_000L
        assertEquals(403, call("POST", "/claim", """{"code":"$code"}""").status)
    }

    @Test fun bookSeeAndCancel() {
        val admin = pairAndSync()
        val mario = invite(admin)
        val luca = invite(admin, "p2", "Luca")

        val me = obj(call("GET", "/me", token = mario))
        assertEquals("Mario", me["name"]!!.jsonPrimitive.content)
        assertEquals("2026-11-09", me["days"]!!.jsonArray.first().jsonPrimitive.content)

        assertEquals(409, call("POST", "/bookings", """{"court":"Campo 2","start":${mon(15)},"minutes":60}""", mario).status)
        val b = call("POST", "/bookings", """{"court":"Campo 1","start":${mon(15, 2)},"minutes":60}""", mario)
        assertEquals(201, b.status)
        val id = obj(b)["id"]!!.jsonPrimitive.content
        assertEquals("1000", obj(b)["cents"]!!.jsonPrimitive.content)
        // Una sola prenotazione per volta (maxFuture = 1).
        assertEquals("TOO_MANY", obj(call("POST", "/bookings", """{"court":"Campo 1","start":${mon(16, 2)},"minutes":60}""", mario))["error"]!!.jsonPrimitive.content)
        // Luca la vede occupata, senza nome.
        val day = call("GET", "/day", token = luca, query = mapOf("date" to "2026-11-11"))
        assertEquals(200, day.status)
        val cell = obj(day)["cells"]!!.jsonArray.map { it.jsonObject }.first { it["court"]!!.jsonPrimitive.content == "Campo 1" && it["start"]!!.jsonPrimitive.content == mon(15, 2).toString() }
        assertEquals("BUSY", cell["state"]!!.jsonPrimitive.content)
        assertFalse(day.body.contains("Mario"))
        assertEquals("TAKEN", obj(call("POST", "/bookings", """{"court":"Campo 1","start":${mon(15, 2)},"minutes":60}""", luca))["error"]!!.jsonPrimitive.content)
        // Luca non la può disdire; Mario sì.
        assertEquals(409, call("DELETE", "/bookings/$id", token = luca).status)
        assertEquals(200, call("DELETE", "/bookings/$id", token = mario).status)
        // La segreteria vede le prenotazioni coi nomi.
        call("POST", "/bookings", """{"court":"Campo 1","start":${mon(18, 1)},"minutes":60}""", luca)
        val st = obj(call("GET", "/admin/state", token = admin))
        assertEquals("Luca", st["bookings"]!!.jsonArray.single().jsonObject["name"]!!.jsonPrimitive.content)
        // Troppo tardi per disdire da soli (meno di 24 ore).
        val late = obj(call("POST", "/bookings", """{"court":"Campo 2","start":${mon(18)},"minutes":60}""", mario))["id"]!!.jsonPrimitive.content
        assertEquals("TOO_LATE", obj(call("DELETE", "/bookings/$late", token = mario))["error"]!!.jsonPrimitive.content)
        assertEquals(200, call("DELETE", "/admin/bookings/$late", token = admin).status)
    }

    @Test fun removedPlayerAndLogout() {
        val admin = pairAndSync()
        val mario = invite(admin)
        assertEquals(200, call("POST", "/logout", token = mario).status)
        assertEquals(401, call("GET", "/me", token = mario).status)
        val again = invite(admin)
        assertEquals(200, call("DELETE", "/admin/players/p1", token = admin).status)
        assertEquals(401, call("GET", "/me", token = again).status)
    }

    @Test fun tooManyWrongTriesWait() {
        pairAndSync()
        repeat(5) { assertEquals(403, call("POST", "/claim", """{"code":"ZZZZZZZZZZ"}""", ip = "9.9.9.9").status) }
        assertEquals(429, call("POST", "/claim", """{"code":"ZZZZZZZZZZ"}""", ip = "9.9.9.9").status)
        assertEquals(403, call("POST", "/claim", """{"code":"ZZZZZZZZZZ"}""", ip = "8.8.8.8").status)
        now += 16 * 60_000L
        assertEquals(403, call("POST", "/claim", """{"code":"ZZZZZZZZZZ"}""", ip = "9.9.9.9").status)
    }

    @Test fun infoBeforeAndAfterSync() {
        assertTrue(obj(call("GET", "/info"))["paired"].toString() == "false")
        pairAndSync()
        assertEquals("TC Prova", obj(call("GET", "/info"))["club"]!!.jsonPrimitive.content)
    }

    @Test fun setupCodeStaysUntilPairedAndChangesOnReset() {
        val a = store.ensureSetupCode()!!
        assertEquals(a, Store(dir) { now }.ensureSetupCode())
        val b = Store(dir) { now }.ensureSetupCode(reset = true)!!
        assertTrue(a != b)
        assertEquals(b, File(dir, Store.SETUP_FILE).readText().trim())
    }
}
