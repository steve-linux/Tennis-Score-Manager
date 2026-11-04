// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.server

import com.tennis.scoremanager.data.BookingRules
import com.tennis.scoremanager.data.Busy
import com.tennis.scoremanager.data.ClubSync
import com.tennis.scoremanager.data.CourtSettings
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

class SeekApiTest {
    private val zone = ZoneId.of("Europe/Rome")
    private fun at(day: Int, h: Int) = ZonedDateTime.of(2026, 11, day, h, 0, 0, 0, zone).toInstant().toEpochMilli()

    private var now = at(9, 8)
    private lateinit var store: Store
    private lateinit var api: Api
    private val sent = mutableListOf<Pair<String, String>>()
    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var admin: String

    @Before fun setUp() {
        store = Store(Files.createTempDirectory("tsm-seek").toFile()) { now }
        api = Api(store, Limiter { now }, pusher = { _, sub, payload, _ -> sent += sub.player to payload; 201 }, async = false)
        admin = field(call("POST", "/admin/pair", """{"code":"${store.ensureSetupCode()}"}""").body, "token")
        sync()
    }

    private fun sync(busy: List<Busy> = emptyList()) {
        val s = ClubSync(club = "TC Prova", courts = listOf("Campo 1"), settings = CourtSettings(openHour = 8, closeHour = 22), busy = busy, rules = BookingRules(maxFuture = 3))
        call("PUT", "/admin/sync", Json.encodeToString(ClubSync.serializer(), s), admin)
    }

    private fun call(method: String, path: String, body: String = "", token: String? = null) = api.handle(Req(method, "/api/v1$path", body = body, token = token, ip = "1.1.1.1"))
    private fun field(j: String, k: String) = Json.parseToJsonElement(j).jsonObject[k]!!.jsonPrimitive.content

    private fun player(id: String, name: String): String {
        val code = field(call("POST", "/admin/invites", """{"player":"$id","name":"$name"}""", admin).body, "code")
        val t = field(call("POST", "/claim", """{"code":"$code"}""").body, "token")
        call("POST", "/push/subscribe", """{"endpoint":"https://push.example/$id","p256dh":"${WebPush.newVapid().publicKey}","auth":"${WebPush.enc(ByteArray(16))}","lang":"it"}""", t)
        return t
    }

    private fun seeks(t: String) = json.decodeFromString(ListSerializer(SrvSeek.serializer()), call("GET", "/seeks", token = t).body)
    private fun me(t: String) = json.decodeFromString(SrvMe.serializer(), call("GET", "/me", token = t).body)

    @Test fun theOfficeSeesAndRemovesSeeksAndWaits() {
        val mario = player("p1", "Mario")
        sync(listOf(Busy("Campo 1", at(10, 9), at(10, 10))))
        val seek = field(call("POST", "/seeks", """{"date":"2026-11-14","fromMin":540,"toMin":720}""", mario).body, "id")
        assertEquals(201, call("POST", "/waits", """{"court":"Campo 1","start":${at(10, 9)},"minutes":60}""", mario).status)
        val st = json.decodeFromString(com.tennis.scoremanager.data.SrvAdmin.serializer(), call("GET", "/admin/state", token = admin).body)
        assertEquals(listOf(seek), st.seeks.map { it.id })
        assertEquals("p1", st.waits.single().player)
        // Solo la segreteria: col token del giocatore no.
        assertEquals(401, call("DELETE", "/admin/seeks/$seek", token = mario).status)
        assertEquals(200, call("DELETE", "/admin/seeks/$seek", token = admin).status)
        assertEquals(200, call("DELETE", "/admin/waits/${st.waits.single().id}", token = admin).status)
        val after = json.decodeFromString(com.tennis.scoremanager.data.SrvAdmin.serializer(), call("GET", "/admin/state", token = admin).body)
        assertTrue(after.seeks.isEmpty() && after.waits.isEmpty())
    }

    @Test fun singlesFindsACourtAndBooksIt() {
        val mario = player("p1", "Mario"); val luca = player("p2", "Luca"); sent.clear()
        // Sabato 14 è troppo lontano (7 giorni avanti da lunedì 9 vanno fino a lunedì 16: va bene).
        val r = call("POST", "/seeks", """{"date":"2026-11-14","fromMin":540,"toMin":720,"level":"NC","note":"partita tranquilla"}""", mario)
        assertEquals(201, r.status)
        val id = field(r.body, "id")
        // Luca lo vede, Mario lo vede come suo.
        assertEquals(1, seeks(luca).single().need)
        assertTrue(seeks(mario).single().mine)
        // Il campo è occupato dalle 9 alle 10: si prende il primo libero, alle 10.
        sync(listOf(Busy("Campo 1", at(14, 9), at(14, 10))))
        assertEquals(200, call("POST", "/seeks/$id/join", token = luca).status)
        val k = seeks(mario).single()
        assertEquals("Campo 1", k.bookedCourt)
        assertEquals(at(14, 10), k.bookedStart)
        assertEquals(listOf("Mario", "Luca"), k.players)
        // La prenotazione è di Mario, col nome di tutti e due; tutti e due avvisati.
        assertEquals(at(14, 10), me(mario).bookings.single().start)
        assertEquals(setOf("p1", "p2"), sent.map { it.first }.toSet())
        assertTrue(sent.all { it.second.contains("Partita trovata") })
        // Al completo non lo vedono più gli altri.
        val anna = player("p3", "Anna")
        assertTrue(seeks(anna).isEmpty())
        assertEquals("NOT_FOUND", field(call("POST", "/seeks/nessuno/join", token = anna).body, "error"))
    }

    @Test fun noFreeCourtTellsThemToBook() {
        val mario = player("p1", "Mario"); val luca = player("p2", "Luca"); sent.clear()
        sync(listOf(Busy("Campo 1", at(14, 8), at(14, 22))))
        val id = field(call("POST", "/seeks", """{"date":"2026-11-14","fromMin":540,"toMin":720}""", mario).body, "id")
        call("POST", "/seeks/$id/join", token = luca)
        assertTrue(seeks(mario).single().noCourt)
        assertTrue(sent.all { it.second.contains("nessun campo libero") })
    }

    @Test fun doublesNeedsFourAndOwnerIsTold() {
        val mario = player("p1", "Mario"); val luca = player("p2", "Luca"); val anna = player("p3", "Anna"); val bea = player("p4", "Bea"); sent.clear()
        val id = field(call("POST", "/seeks", """{"date":"2026-11-12","fromMin":1080,"toMin":1260,"doubles":true}""", mario).body, "id")
        call("POST", "/seeks/$id/join", token = luca)
        assertEquals("p1", sent.single().first)
        assertTrue(sent.single().second.contains("Luca"))
        call("POST", "/seeks/$id/join", token = anna)
        assertEquals(1, seeks(bea).single().need)
        call("POST", "/seeks/$id/join", token = bea)
        assertEquals(at(12, 18), seeks(mario).single().bookedStart)
    }

    @Test fun limitsLeaveAndPast() {
        val mario = player("p1", "Mario"); val luca = player("p2", "Luca")
        assertEquals("TOO_FAR", field(call("POST", "/seeks", """{"date":"2026-11-30","fromMin":540,"toMin":720}""", mario).body, "error"))
        assertEquals("NOT_SLOT", field(call("POST", "/seeks", """{"date":"2026-11-12","fromMin":540,"toMin":570}""", mario).body, "error"))
        for (d in 10..12) assertEquals(201, call("POST", "/seeks", """{"date":"2026-11-$d","fromMin":540,"toMin":720}""", mario).status)
        assertEquals("TOO_MANY_SEEKS", field(call("POST", "/seeks", """{"date":"2026-11-13","fromMin":540,"toMin":720}""", mario).body, "error"))
        // Luca dice ci sto e poi si toglie... ma in singolo il primo "ci sto" completa: proviamo in un doppio.
        val id = field(call("POST", "/seeks", """{"date":"2026-11-13","fromMin":540,"toMin":720,"doubles":true}""", luca).body, "id")
        call("POST", "/seeks/$id/join", token = mario)
        call("POST", "/seeks/$id/leave", token = mario)
        assertEquals(3, seeks(luca).first { it.id == id }.need)
        // Passato il giorno, gli annunci spariscono.
        now = at(11, 9); api.tick()
        assertTrue(seeks(mario).none { it.date == "2026-11-10" })
    }
}
