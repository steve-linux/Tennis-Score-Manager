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

/** Le chat del circolo: quella di ogni annuncio di Cerco compagno e la bacheca della segreteria. */
class ChatApiTest {
    private val zone = ZoneId.of("Europe/Rome")
    private var now = ZonedDateTime.of(2026, 11, 9, 8, 0, 0, 0, zone).toInstant().toEpochMilli()
    private lateinit var store: Store
    private lateinit var api: Api
    private lateinit var admin: String
    private val sent = mutableListOf<Pair<String, String>>()
    private val json = Json { ignoreUnknownKeys = true }

    @Before fun setUp() {
        store = Store(Files.createTempDirectory("tsm-chat").toFile()) { now }
        api = Api(store, Limiter(max = 1000) { now }, pusher = { _, sub, payload, _ -> sent += sub.player to payload; 201 }, async = false)
        admin = field(call("POST", "/admin/pair", """{"code":"${store.ensureSetupCode()}"}""").body, "token")
        val s = ClubSync(club = "TC Prova", courts = listOf("Campo 1"), settings = CourtSettings(openHour = 8, closeHour = 22), rules = BookingRules(maxFuture = 3))
        call("PUT", "/admin/sync", Json.encodeToString(ClubSync.serializer(), s), admin)
    }

    private fun call(method: String, path: String, body: String = "", token: String? = null) = api.handle(Req(method, "/api/v1$path", body = body, token = token, ip = "1.1.1.1"))
    private fun field(j: String, k: String) = Json.parseToJsonElement(j).jsonObject[k]!!.jsonPrimitive.content
    private fun msgs(room: String, t: String) = json.decodeFromString(ListSerializer(SrvChatMsg.serializer()), call("GET", "/chat/$room", token = t).body)

    private fun player(id: String, name: String): String {
        val code = field(call("POST", "/admin/invites", """{"player":"$id","name":"$name"}""", admin).body, "code")
        val t = field(call("POST", "/claim", """{"code":"$code"}""").body, "token")
        call("POST", "/push/subscribe", """{"endpoint":"https://push.example/$id","p256dh":"${WebPush.newVapid().publicKey}","auth":"${WebPush.enc(ByteArray(16))}","lang":"it"}""", t)
        return t
    }

    private fun seek(t: String, doubles: Boolean = true) =
        field(call("POST", "/seeks", """{"date":"2026-11-12","fromMin":1080,"toMin":1260,"doubles":$doubles}""", t).body, "id")

    @Test fun laChatDiUnAnnuncioESoloDiChiCiSta() {
        val mario = player("p1", "Mario"); val luca = player("p2", "Luca"); val anna = player("p3", "Anna")
        val id = seek(mario)
        call("POST", "/seeks/$id/join", token = luca)
        sent.clear()
        assertEquals(201, call("POST", "/chat/s-$id", """{"text":"  Porto io le palle  "}""", mario).status)
        val got = msgs("s-$id", luca)
        assertEquals(listOf("Porto io le palle"), got.map { it.text })
        assertEquals("Mario", got.single().name)
        assertTrue(msgs("s-$id", mario).single().mine)
        // Avvisato Luca, non Mario; Anna non c'entra e non legge né scrive.
        assertEquals(listOf("p2"), sent.map { it.first })
        assertEquals(403, call("GET", "/chat/s-$id", token = anna).status)
        assertEquals(403, call("POST", "/chat/s-$id", """{"text":"ciao"}""", anna).status)
        assertEquals(400, call("POST", "/chat/s-$id", """{"text":"   "}""", luca).status)
        // L'annuncio dice quando è arrivato l'ultimo messaggio.
        val k = json.decodeFromString(ListSerializer(SrvSeek.serializer()), call("GET", "/seeks", token = luca).body).single()
        assertEquals(now, k.chatLast)
        // Solo quelli dopo un certo momento.
        now += 1000
        call("POST", "/chat/s-$id", """{"text":"Perfetto"}""", luca)
        val after = api.handle(Req("GET", "/api/v1/chat/s-$id", query = mapOf("after" to (now - 500).toString()), token = mario, ip = "1.1.1.1")).body
        assertEquals(listOf("Perfetto"), json.decodeFromString(ListSerializer(SrvChatMsg.serializer()), after).map { it.text })
    }

    @Test fun inBachecaScriveSoloLaSegreteriaELeggonoTutti() {
        val mario = player("p1", "Mario"); val luca = player("p2", "Luca")
        assertEquals(403, call("POST", "/chat/board", """{"text":"ciao a tutti"}""", mario).status)
        sent.clear()
        assertEquals(201, call("POST", "/admin/chat/board", """{"text":"Sabato campi chiusi per il torneo"}""", admin).status)
        val b = msgs("board", luca).single()
        assertTrue(b.office)
        assertEquals("TC Prova", b.name)
        assertEquals(setOf("p1", "p2"), sent.map { it.first }.toSet())
        assertEquals(now, json.decodeFromString(SrvMe.serializer(), call("GET", "/me", token = mario).body).boardLast)
        assertEquals(401, call("POST", "/admin/chat/board", """{"text":"x"}""").status)
    }

    @Test fun laSegreteriaVedeEToglieIMessaggi() {
        val mario = player("p1", "Mario"); val luca = player("p2", "Luca")
        val id = seek(mario)
        call("POST", "/seeks/$id/join", token = luca)
        val m = field(call("POST", "/chat/s-$id", """{"text":"messaggio da togliere"}""", luca).body, "id")
        val rooms = call("GET", "/admin/chats", token = admin).body
        assertTrue(rooms, "s-$id" in rooms && "Mario" in rooms)
        assertTrue(call("GET", "/admin/chat/s-$id", token = admin).body.contains("messaggio da togliere"))
        assertEquals(200, call("DELETE", "/admin/chat/msg/$m", token = admin).status)
        assertTrue(msgs("s-$id", mario).isEmpty())
        assertEquals(401, call("GET", "/admin/chats").status)
    }

    @Test fun chiScriveARafficaSiFermaEIVecchiSeNeVanno() {
        val mario = player("p1", "Mario"); val luca = player("p2", "Luca")
        val id = seek(mario)
        call("POST", "/seeks/$id/join", token = luca)
        repeat(CHAT_BURST) { assertEquals(201, call("POST", "/chat/s-$id", """{"text":"m$it"}""", luca).status) }
        assertEquals(429, call("POST", "/chat/s-$id", """{"text":"troppi"}""", luca).status)
        now += 11 * 60_000L
        assertEquals(201, call("POST", "/chat/s-$id", """{"text":"di nuovo"}""", luca).status)
        call("POST", "/admin/chat/board", """{"text":"vecchio avviso"}""", admin)
        // Passati i giorni il server li toglie da solo.
        now += (CHAT_DAYS + 1) * 86_400_000L
        api.tick(now)
        assertTrue(store.state.chats.isEmpty())
    }

    @Test fun laChatDelCircoloLaScrivonoTutti() {
        val mario = player("p1", "Mario"); val luca = player("p2", "Luca"); player("p3", "Anna")
        call("POST", "/prefs", """{"clubChat":false}""", luca)
        sent.clear()
        assertEquals(201, call("POST", "/chat/club", """{"text":"Chi gioca sabato?"}""", mario).status)
        // Avvisata Anna; Luca ha spento le notifiche della chat del circolo, ma la legge.
        assertEquals(listOf("p3"), sent.map { it.first })
        assertEquals("Chi gioca sabato?", msgs("club", luca).single().text)
        assertTrue(call("GET", "/chats", token = luca).body.contains("\"room\":\"club\""))
        assertTrue(call("GET", "/admin/chats", token = admin).body.contains("\"room\":\"club\""))
    }

    @Test fun iMessaggiPrivatiSoloAChiAccetta() {
        val mario = player("p1", "Mario"); val luca = player("p2", "Luca")
        val room = "d-p1~p2"
        // Di partenza nessuno è nell'elenco e non gli si scrive.
        assertEquals("[]", call("GET", "/people", token = mario).body)
        assertEquals(403, call("POST", "/chat/$room", """{"text":"ciao"}""", mario).status)
        call("POST", "/prefs", """{"dmOpen":true}""", luca)
        assertTrue(call("GET", "/people", token = mario).body.contains(room))
        sent.clear()
        assertEquals(201, call("POST", "/chat/$room", """{"text":"Partita domani?"}""", mario).status)
        assertEquals(listOf("p2"), sent.map { it.first })
        // Luca risponde anche se Mario non ha acceso "Possono scrivermi".
        assertEquals(201, call("POST", "/chat/$room", """{"text":"Volentieri"}""", luca).status)
        assertEquals(listOf("Partita domani?", "Volentieri"), msgs(room, mario).map { it.text })
        assertTrue(call("GET", "/chats", token = mario).body.contains("\"title\":\"Luca\""))
        // Un terzo non legge, e la segreteria non vede la chat privata.
        val anna = player("p3", "Anna")
        assertEquals(403, call("GET", "/chat/$room", token = anna).status)
        assertTrue(room !in call("GET", "/admin/chats", token = admin).body)
    }

    @Test fun bloccareESegnalare() {
        val mario = player("p1", "Mario"); val luca = player("p2", "Luca")
        val room = "d-p1~p2"
        call("POST", "/prefs", """{"dmOpen":true}""", luca)
        val m = field(call("POST", "/chat/$room", """{"text":"messaggio molesto"}""", mario).body, "id")
        // Mario non può segnalare il suo; Luca sì, e la segreteria vede solo quello.
        assertEquals(403, call("POST", "/chat/msg/$m/report", token = mario).status)
        assertEquals(200, call("POST", "/chat/msg/$m/report", token = luca).status)
        assertTrue(call("GET", "/admin/chats", token = admin).body.contains(room))
        assertEquals(listOf("messaggio molesto"), json.decodeFromString(ListSerializer(SrvChatMsg.serializer()), call("GET", "/admin/chat/$room", token = admin).body).map { it.text })
        // Luca blocca Mario: non gli scrive più, non lo legge più, e Mario non lo trova nell'elenco.
        assertEquals(200, call("POST", "/block", """{"player":"p1","on":true}""", luca).status)
        val blocked = call("POST", "/chat/$room", """{"text":"ancora"}""", mario)
        assertEquals(403, blocked.status)
        assertTrue("USER_BLOCKED" in blocked.body)
        assertTrue(msgs(room, luca).isEmpty())
        assertEquals("[]", call("GET", "/people", token = mario).body)
        assertEquals("Mario", json.decodeFromString(SrvMe.serializer(), call("GET", "/me", token = luca).body).blocked.single().name)
        call("POST", "/block", """{"player":"p1","on":false}""", luca)
        assertEquals(201, call("POST", "/chat/$room", """{"text":"scusa"}""", mario).status)
    }
}
