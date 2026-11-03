// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.server

import com.tennis.scoremanager.data.BookingRules
import com.tennis.scoremanager.data.ClubSync
import com.tennis.scoremanager.data.CourtSettings
import com.tennis.scoremanager.data.SrvMe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files
import java.time.ZoneId
import java.time.ZonedDateTime

class WaitApiTest {
    private val zone = ZoneId.of("Europe/Rome")
    private fun at(day: Int, h: Int) = ZonedDateTime.of(2026, 11, day, h, 0, 0, 0, zone).toInstant().toEpochMilli()

    private var now = at(9, 8)
    private lateinit var store: Store
    private lateinit var api: Api
    private val sent = mutableListOf<String>()
    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var admin: String

    @Before fun setUp() {
        store = Store(Files.createTempDirectory("tsm-wait").toFile()) { now }
        api = Api(store, Limiter { now }, pusher = { _, _, payload, _ -> sent += payload; 201 }, async = false)
        admin = field(call("POST", "/admin/pair", """{"code":"${store.ensureSetupCode()}"}""").body, "token")
        val sync = ClubSync(club = "TC Prova", courts = listOf("Campo 1", "Campo 2"), settings = CourtSettings(openHour = 8, closeHour = 22), rules = BookingRules(maxFuture = 5))
        call("PUT", "/admin/sync", Json.encodeToString(ClubSync.serializer(), sync), admin)
    }

    private fun call(method: String, path: String, body: String = "", token: String? = null) = api.handle(Req(method, "/api/v1$path", body = body, token = token, ip = "1.1.1.1"))
    private fun field(j: String, k: String) = Json.parseToJsonElement(j).jsonObject[k]!!.jsonPrimitive.content

    private fun player(id: String, name: String): String {
        val code = field(call("POST", "/admin/invites", """{"player":"$id","name":"$name"}""", admin).body, "code")
        val t = field(call("POST", "/claim", """{"code":"$code"}""").body, "token")
        call("POST", "/push/subscribe", """{"endpoint":"https://push.example/$id","p256dh":"${WebPush.newVapid().publicKey}","auth":"${WebPush.enc(ByteArray(16))}","lang":"it"}""", t)
        return t
    }

    private fun book(t: String, court: String, start: Long) = call("POST", "/bookings", """{"court":"$court","start":$start,"minutes":60}""", t)
    private fun me(t: String) = json.decodeFromString(SrvMe.serializer(), call("GET", "/me", token = t).body)

    @Test fun waitThenFreedThenBooked() {
        val mario = player("p1", "Mario"); val luca = player("p2", "Luca"); sent.clear()
        val id = field(book(mario, "Campo 1", at(11, 18)).body, "id")
        // Libero: si prenota, non si aspetta.
        assertEquals("FREE_NOW", field(call("POST", "/waits", """{"court":"Campo 2","start":${at(11, 18)},"minutes":60}""", luca).body, "error"))
        assertEquals(201, call("POST", "/waits", """{"court":"Campo 1","start":${at(11, 18)},"minutes":60}""", luca).status)
        // La casella occupata dice a Luca che sta aspettando.
        val cells = Json.parseToJsonElement(api.handle(Req("GET", "/api/v1/day", mapOf("date" to "2026-11-11"), token = luca, ip = "1.1.1.1")).body).jsonObject["cells"]!!.jsonArray.map { it.jsonObject }
        assertEquals("true", cells.first { it["court"]!!.jsonPrimitive.content == "Campo 1" && it["start"]!!.jsonPrimitive.content == at(11, 18).toString() }["waiting"]!!.jsonPrimitive.content)
        // Mario disdice: Luca è avvisato, una volta.
        call("DELETE", "/bookings/$id", token = mario)
        assertEquals(1, sent.size)
        assertTrue(sent.single().contains("Si è liberato un campo"))
        assertEquals("Campo 1", me(luca).waits.single().freedCourt)
        api.tick(); assertEquals(1, sent.size)
        // Luca prenota: l'attesa sparisce.
        assertEquals(201, book(luca, "Campo 1", at(11, 18)).status)
        assertTrue(me(luca).waits.isEmpty())
    }

    @Test fun freedThenTakenAgainGoesBackToWaiting() {
        val mario = player("p1", "Mario"); val luca = player("p2", "Luca"); val anna = player("p3", "Anna"); sent.clear()
        val id = field(book(mario, "Campo 1", at(11, 18)).body, "id")
        call("POST", "/waits", """{"court":"Campo 1","start":${at(11, 18)},"minutes":60}""", luca)
        call("DELETE", "/bookings/$id", token = mario)
        // Anna è più veloce.
        book(anna, "Campo 1", at(11, 18))
        api.checkWaits()
        assertNull(me(luca).waits.single().freedCourt)
    }

    @Test fun anyCourtWait() {
        val mario = player("p1", "Mario"); val anna = player("p3", "Anna"); val luca = player("p2", "Luca"); sent.clear()
        book(mario, "Campo 1", at(12, 10))
        val id2 = field(book(anna, "Campo 2", at(12, 10)).body, "id")
        assertEquals(201, call("POST", "/waits", """{"start":${at(12, 10)},"minutes":60}""", luca).status)
        call("DELETE", "/bookings/$id2", token = anna)
        assertEquals("Campo 2", me(luca).waits.single().freedCourt)
        assertTrue(sent.single().contains("Campo 2"))
    }

    @Test fun limitsAndTooFar() {
        val mario = player("p1", "Mario"); val luca = player("p2", "Luca")
        for (d in 10..13) book(mario, "Campo 1", at(d, 18))
        for (d in 10..12) assertEquals(201, call("POST", "/waits", """{"court":"Campo 1","start":${at(d, 18)},"minutes":60}""", luca).status)
        assertEquals("TOO_MANY_WAITS", field(call("POST", "/waits", """{"court":"Campo 1","start":${at(13, 18)},"minutes":60}""", luca).body, "error"))
        assertEquals("TOO_FAR", field(call("POST", "/waits", """{"court":"Campo 1","start":${at(25, 18)},"minutes":60}""", luca).body, "error"))
        // Si toglie da sola quando l'ora è passata.
        now = at(10, 19); api.tick()
        assertEquals(2, me(luca).waits.size)
    }
}
