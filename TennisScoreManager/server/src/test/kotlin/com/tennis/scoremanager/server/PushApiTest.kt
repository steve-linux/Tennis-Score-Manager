// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.server

import com.tennis.scoremanager.data.BookingRules
import com.tennis.scoremanager.data.ClubSync
import com.tennis.scoremanager.data.CourtSettings
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

class PushApiTest {
    private val zone = ZoneId.of("Europe/Rome")
    private fun at(day: Int, h: Int) = ZonedDateTime.of(2026, 11, day, h, 0, 0, 0, zone).toInstant().toEpochMilli()

    private var now = at(9, 8)
    private lateinit var store: Store
    private lateinit var api: Api
    /** Le notifiche "mandate": a chi e cosa. */
    private val sent = mutableListOf<Pair<String, String>>()
    private var answer = 201

    @Before fun setUp() {
        store = Store(Files.createTempDirectory("tsm-push").toFile()) { now }
        api = Api(store, Limiter { now }, pusher = { _, sub, payload, _ -> sent += sub.endpoint to payload; answer }, async = false)
    }

    private fun call(method: String, path: String, body: String = "", token: String? = null) = api.handle(Req(method, "/api/v1$path", body = body, token = token, ip = "1.1.1.1"))
    private fun field(json: String, k: String) = Json.parseToJsonElement(json).jsonObject[k]!!.jsonPrimitive.content

    /** Segreteria abbinata, planning mandato (promemoria 2 ore prima), un giocatore entrato. */
    private fun ready(): Pair<String, String> {
        val admin = field(call("POST", "/admin/pair", """{"code":"${store.ensureSetupCode()}"}""").body, "token")
        val sync = ClubSync(club = "TC Prova", courts = listOf("Campo 1"), settings = CourtSettings(openHour = 8, closeHour = 22), rules = BookingRules(maxFuture = 3, remindHours = 2))
        call("PUT", "/admin/sync", Json.encodeToString(ClubSync.serializer(), sync), admin)
        val code = field(call("POST", "/admin/invites", """{"player":"p1","name":"Mario"}""", admin).body, "code")
        return admin to field(call("POST", "/claim", """{"code":"$code"}""").body, "token")
    }

    private val key = WebPush.newVapid().publicKey
    private val auth = WebPush.enc(ByteArray(16) { it.toByte() })
    private fun subscribe(token: String, endpoint: String = "https://web.push.apple.com/abc") =
        call("POST", "/push/subscribe", """{"endpoint":"$endpoint","p256dh":"$key","auth":"$auth","lang":"it"}""", token)

    @Test fun subscribeSendsAWelcome() {
        val (_, mario) = ready()
        assertEquals(65, WebPush.dec(field(call("GET", "/push/key", token = mario).body, "key")).size)
        assertEquals(200, subscribe(mario).status)
        assertEquals(1, sent.size)
        assertTrue(sent.single().second.contains("Promemoria attivi"))
        assertEquals(400, subscribe(mario, "http://non-sicuro.example/x").status)
        assertEquals(401, call("POST", "/push/subscribe", "{}").status)
    }

    @Test fun reminderOnceInTheWindow() {
        val (_, mario) = ready()
        subscribe(mario); sent.clear()
        call("POST", "/bookings", """{"court":"Campo 1","start":${at(10, 18)},"minutes":60}""", mario)
        now = at(10, 15); api.tick()
        assertTrue(sent.isEmpty())
        now = at(10, 16); api.tick()
        assertEquals(1, sent.size)
        assertTrue(sent.single().second.contains("Promemoria della partita"))
        assertTrue(sent.single().second.contains("Campo 1"))
        now = at(10, 17); api.tick()
        assertEquals(1, sent.size)
    }

    @Test fun noReminderWhenBookedInsideTheWindow() {
        val (_, mario) = ready()
        subscribe(mario); sent.clear()
        now = at(9, 9)
        call("POST", "/bookings", """{"court":"Campo 1","start":${at(9, 10)},"minutes":60}""", mario)
        api.tick()
        assertTrue(sent.isEmpty())
    }

    @Test fun removedByTheOfficeAndDeadSubscriptions() {
        val (admin, mario) = ready()
        subscribe(mario); sent.clear()
        val id = field(call("POST", "/bookings", """{"court":"Campo 1","start":${at(11, 18)},"minutes":60}""", mario).body, "id")
        answer = 410
        assertEquals(200, call("DELETE", "/admin/bookings/$id", token = admin).status)
        assertTrue(sent.single().second.contains("tolta dalla segreteria"))
        // Il servizio push ha detto che l'abbonamento non c'è più: si toglie.
        assertTrue(store.state.pushSubs.isEmpty())
    }

    @Test fun playerCancelGivesNoNotification() {
        val (_, mario) = ready()
        subscribe(mario); sent.clear()
        val id = field(call("POST", "/bookings", """{"court":"Campo 1","start":${at(12, 18)},"minutes":60}""", mario).body, "id")
        assertEquals(200, call("DELETE", "/bookings/$id", token = mario).status)
        assertTrue(sent.isEmpty())
    }
}
