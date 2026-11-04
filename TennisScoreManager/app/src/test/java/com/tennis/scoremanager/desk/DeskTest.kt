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
import com.tennis.scoremanager.data.FeeKind
import com.tennis.scoremanager.data.Courses
import java.io.File
import java.net.InetAddress
import java.net.Socket
import java.net.URI
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
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
        // La pagina filtra per tipo: l'addebito porta la quota da cui viene e il suo tipo.
        val (_, money) = req("GET", "/money", token = k)
        assertTrue(money, "\"fee\":\"${c.fee}\"" in money && "\"type\":\"MEMBERSHIP\"" in money)
        assertEquals(400, req("POST", "/charges/${c.id}/pay", """{"cents":20000,"date":"2026-11-04","type":"cash"}""", k).first)
        assertEquals(400, req("POST", "/charges/${c.id}/pay", """{"cents":-5,"date":"2026-11-04","type":"cash"}""", k).first)
        assertEquals(400, req("POST", "/charges/${c.id}/pay", """{"cents":100,"date":"2026-11-04","type":"boh"}""", k).first)
        assertEquals(200, req("POST", "/charges/${c.id}/pay", """{"cents":15000,"date":"2026-11-04","type":"cash","ref":"x"}""", k).first)
        val paid = data.ledger.charges.single()
        assertEquals(0, paid.left)
        assertEquals("1/2026", paid.payments.single().receipt)
        assertEquals(200, req("DELETE", "/charges/${c.id}/payments/${paid.payments.single().id}", token = k).first)
        assertEquals(15000, data.ledger.charges.single().left)
        val mario = data.list.first { it.lastName == "Rossi" }.id
        assertEquals(200, req("POST", "/charges", """{"player":"$mario","label":"Rinnovo","cents":2000,"kind":"FITP"}""", k).first)
        assertEquals(FeeKind.FITP, data.ledger.charges.last().kind)
        data.ledger = data.ledger.copy(charges = data.ledger.charges + c.copy(id = "x1", fee = null, label = "Tessera 2027") + c.copy(id = "x2", fee = null, label = "${data.courts().first()} · Mer 4 Nov 12:00"))
        val (_, more) = req("GET", "/money", token = k)
        assertEquals(2, Regex("\"type\":\"FITP\"").findAll(more).count())
        assertTrue(more, "\"type\":\"COURT\"" in more)
    }

    @Test fun torneoDaCapoAFine() {
        val k = login()
        for ((n, r) in listOf("Anna" to "4.1", "Bea" to "4.3", "Cora" to "", "Dina" to "4.2", "Ely" to "")) {
            assertEquals(200, req("POST", "/people", """{"firstName":"$n","lastName":"X","member":true,"ranking":"$r","medicalUntil":"2030-01-01"}""", k).first)
        }
        req("POST", "/people", """{"firstName":"Zeno","lastName":"X","member":true}""", k)
        val ids = data.list.associate { it.firstName to it.id }
        assertEquals(400, req("POST", "/tournaments", """{"name":"  "}""", k).first)
        val (st, made) = req("POST", "/tournaments", """{"name":"Sociale 2027","category":"Singolare","start":"2027-05-01","end":"2027-05-10"}""", k)
        assertEquals(200, st)
        val id = field(made, "id")!!
        for (n in listOf("Anna", "Bea", "Cora", "Dina", "Ely", "Zeno")) assertEquals(200, req("POST", "/tournaments/$id/entries", """{"players":["${ids[n]}"]}""", k).first)
        assertEquals(409, req("POST", "/tournaments/$id/entries", """{"players":["${ids["Anna"]}"]}""", k).first)
        assertEquals(400, req("POST", "/tournaments/$id/entries", """{"players":["${ids["Anna"]}","${ids["Bea"]}"]}""", k).first)
        // Zeno non ha il certificato: resta iscritto ma non entra nel sorteggio.
        val (_, cup) = req("GET", "/tournaments/$id", token = k)
        assertTrue(cup, "\"code\":\"NO_MEDICAL\"" in cup && "\"eligible\":5" in cup)
        val cora = data.cups.single().entries.first { it.names == listOf("Cora X") }.id
        assertEquals(200, req("PUT", "/tournaments/$id/entries/$cora/seed", """{"seed":1}""", k).first)
        assertEquals(200, req("POST", "/tournaments/$id/draw", token = k).first)
        var t = data.cups.single()
        assertEquals(8, t.matches.count { it.round == 1 } * 2)
        // La testa di serie scelta a mano va in cima, i bye contro le prime teste di serie.
        assertEquals(cora, t.matches.first { it.round == 1 && it.slot == 0 }.a)
        assertTrue(t.matches.first { it.round == 1 && it.slot == 0 }.bye)
        assertFalse(t.entries.first { it.names == listOf("Zeno X") }.id in t.matches.flatMap { listOf(it.a, it.b) })
        val m = t.matches.first { it.round == 1 && !it.bye }
        assertEquals(400, req("POST", "/tournaments/$id/matches/${m.id}/result", """{"score":"6-6"}""", k).first)
        assertEquals(200, req("POST", "/tournaments/$id/matches/${m.id}/result", """{"score":"4-6 2-6"}""", k).first)
        t = data.cups.single()
        assertEquals(m.b, t.matches.first { it.id == m.id }.winner)
        assertEquals(m.b, t.matches.first { it.round == 2 && it.slot == m.slot / 2 }.let { if (m.slot % 2 == 0) it.a else it.b })
        assertEquals(409, req("POST", "/tournaments/$id/draw", token = k).first)
        assertEquals(409, req("POST", "/tournaments/$id/undraw", token = k).first)
        assertEquals(409, req("DELETE", "/tournaments/$id/entries/$cora", token = k).first)
        // Il programma: il campo occupato da una prenotazione si segnala, e con force si mette lo stesso.
        req("POST", "/bookings", """{"court":"Campo 1","date":"2027-05-02","startMin":600,"minutes":60,"names":["Rossi"]}""", k)
        val next = t.matches.first { it.round == 2 }
        val at = """{"court":"Campo 1","date":"2027-05-02","startMin":630}"""
        assertEquals(409, req("POST", "/tournaments/$id/matches/${next.id}/schedule", at, k).first)
        assertEquals(200, req("POST", "/tournaments/$id/matches/${next.id}/schedule", at.replace("}", ",\"force\":true}"), k).first)
        assertEquals(200, req("POST", "/tournaments/$id/matches/${next.id}/schedule", at.replace("Campo 1", "Campo 2"), k).first)
        assertEquals("Campo 2", data.cups.single().matches.first { it.id == next.id }.court)
        val (_, plan) = req("GET", "/day", token = k, q = mapOf("date" to "2027-05-02"))
        assertTrue(plan, "\"TOURNAMENT\"" in plan && "Sociale 2027" in plan)
        // A sorteggio fatto la formula non cambia, il nome sì.
        assertEquals(200, req("PUT", "/tournaments/$id", """{"name":"Sociale di primavera","format":"GROUPS","start":"2027-05-01"}""", k).first)
        assertEquals("Sociale di primavera", data.cups.single().name)
        assertEquals(com.tennis.scoremanager.data.CTFormat.KNOCKOUT, data.cups.single().format)
        assertEquals(200, req("DELETE", "/tournaments/$id", token = k).first)
        assertTrue(data.cups.isEmpty())
    }

    @Test fun torneoAGironi() {
        val k = login()
        for (n in listOf("A", "B", "C", "D")) req("POST", "/people", """{"firstName":"$n","lastName":"Y","member":true,"medicalUntil":"2030-01-01"}""", k)
        val id = field(req("POST", "/tournaments", """{"name":"Gironi","format":"GROUPS","groups":2,"qualify":1}""", k).second, "id")!!
        for (p in data.list) req("POST", "/tournaments/$id/entries", """{"players":["${p.id}"]}""", k)
        assertEquals(409, req("POST", "/tournaments/$id/knockout", token = k).first)
        assertEquals(200, req("POST", "/tournaments/$id/draw", token = k).first)
        for (m in data.cups.single().matches) assertEquals(200, req("POST", "/tournaments/$id/matches/${m.id}/result", """{"score":"6-1 6-1"}""", k).first)
        val (_, cup) = req("GET", "/tournaments/$id", token = k)
        assertTrue(cup, "\"standings\":[[{" in cup)
        assertEquals(200, req("POST", "/tournaments/$id/knockout", token = k).first)
        val fin = data.cups.single().matches.single { it.round == 1 }
        assertEquals(data.cups.single().matches.filter { it.round == 0 }.map { it.a }.toSet(), setOf(fin.a, fin.b))
    }

    @Test fun prenotazioneCambiataRipetutaEChiusure() {
        val k = login()
        val b = """{"court":"Campo 1","date":"2026-11-10","startMin":600,"minutes":60,"names":["Rossi"],"weeks":4}"""
        // Ripetuta 4 settimane: la terza c'è già un'altra prenotazione e si salta.
        assertEquals(200, req("POST", "/bookings", b.replace("2026-11-10", "2026-11-24").replace(",\"weeks\":4", ""), k).first)
        val (st, out) = req("POST", "/bookings", b, k)
        assertEquals(200, st)
        assertTrue(out, "2026-11-24" in out)
        val series = data.courtPlan.bookings.filter { it.series != null }
        assertEquals(3, series.size)
        // Spostata di un'ora e su un altro campo: resta lei (stesso id, stessa serie).
        val first = series.minByOrNull { it.start }!!
        assertEquals(200, req("PUT", "/bookings/${first.id}", b.replace("Campo 1", "Campo 2").replace("600", "660").replace(",\"weeks\":4", ""), k).first)
        val moved = data.courtPlan.bookings.first { it.id == first.id }
        assertEquals("Campo 2", moved.court)
        assertEquals(first.series, moved.series)
        assertEquals(first.start + 3_600_000L, moved.start)
        // Su un campo occupato no.
        val other = data.courtPlan.bookings.first { it.series == null }
        assertEquals(409, req("PUT", "/bookings/${moved.id}", b.replace("2026-11-10", "2026-11-24").replace(",\"weeks\":4", ""), k).first)
        assertEquals(other, data.courtPlan.bookings.first { it.id == other.id })
        // Cancellata con quelle dopo della serie.
        assertEquals(200, req("DELETE", "/bookings/${moved.id}", token = k, q = mapOf("series" to "1")).first)
        assertEquals(listOf(other.id), data.courtPlan.bookings.map { it.id })
        // Chiusura del Campo 1 il martedì mattina per un mese: lì non si prenota più.
        assertEquals(400, req("POST", "/blocks", """{"label":"Manutenzione","from":"2026-12-01","to":"2026-11-01"}""", k).first)
        assertEquals(200, req("POST", "/blocks", """{"label":"Terra nuova","courts":["Campo 1"],"from":"2026-11-01","to":"2026-11-30","weekdays":[2],"startMin":480,"endMin":720}""", k).first)
        assertEquals(409, req("POST", "/bookings", b.replace(",\"weeks\":4", "").replace("2026-11-10", "2026-11-17"), k).first)
        assertEquals(200, req("POST", "/bookings", b.replace(",\"weeks\":4", "").replace("2026-11-10", "2026-11-18"), k).first)
        val (_, day) = req("GET", "/day", token = k, q = mapOf("date" to "2026-11-17"))
        assertTrue(day, "\"type\":\"BLOCK\"" in day && "Terra nuova" in day && "\"editable\":true" in day)
        val id = data.courtPlan.blocks.single().id
        // I blocchi dei corsi si cambiano col corso, nell'app.
        data.courtPlan = data.courtPlan.copy(blocks = data.courtPlan.blocks + data.courtPlan.blocks.single().copy(id = "corso", course = "c1"))
        assertEquals(409, req("DELETE", "/blocks/corso", token = k).first)
        assertEquals(200, req("DELETE", "/blocks/$id", token = k).first)
        assertEquals(listOf("corso"), data.courtPlan.blocks.map { it.id })
    }

    @Test fun oggiERicevuta() {
        val k = login()
        data.time = java.time.LocalDateTime.of(2026, 11, 10, 9, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        req("POST", "/people", """{"firstName":"Mario","lastName":"Rossi","member":true}""", k)
        req("POST", "/bookings", """{"court":"Campo 1","date":"2026-11-10","startMin":600,"minutes":60,"names":["Bianchi"]}""", k)
        req("POST", "/fees", """{"name":"Quota","cents":5000,"chargeMembers":true}""", k)
        val (st, out) = req("GET", "/today", token = k)
        assertEquals(200, st)
        assertTrue(out, "\"bookings\":1" in out && "Bianchi" in out && "\"startMin\":600" in out)
        assertTrue(out, "\"collectCents\":5000" in out && "\"members\":1" in out && "Mario Rossi" in out)
        // Con Quote e pagamenti spento non si vede quanto c'è da incassare.
        data.on = data.on - com.tennis.scoremanager.ClubFeature.PAYMENTS
        assertTrue("\"collectCents\":0" in req("GET", "/today", token = k).second)
        assertTrue(req("GET", "/info", token = k).second.let { "PLANNING" in it && "PAYMENTS" !in it })
        val c = data.ledger.charges.single()
        req("POST", "/charges/${c.id}/pay", """{"cents":5000,"date":"2026-11-10","type":"cash"}""", k)
        val pay = data.ledger.charges.single().payments.single()
        val r = api.handle(DeskApi.Req("GET", "/charges/${c.id}/payments/${pay.id}/receipt", token = k, ip = ip))
        assertEquals(200, r.status)
        assertEquals("application/pdf", r.type)
        assertEquals(404, req("GET", "/charges/${c.id}/payments/nessuno/receipt", token = k).first)
    }

    @Test fun settimanaEMese() {
        val k = login()
        // 2026-11-11 è un mercoledì: la settimana parte da lunedì 9.
        req("POST", "/bookings", """{"court":"Campo 1","date":"2026-11-11","startMin":600,"minutes":60,"names":["Rossi"]}""", k)
        req("POST", "/blocks", """{"label":"Chiuso","from":"2026-11-15","to":"2026-11-15"}""", k)
        val (st, w) = req("GET", "/week", token = k, q = mapOf("date" to "2026-11-11"))
        assertEquals(200, st)
        assertTrue(w, "\"from\":\"2026-11-09\"" in w && "\"date\":\"2026-11-15\"" in w && "Rossi" in w)
        assertEquals(7, Regex("\"date\":").findAll(w).count())
        val (_, m) = req("GET", "/month", token = k, q = mapOf("date" to "2026-11-20"))
        assertTrue(m, "\"first\":\"2026-11-01\"" in m)
        assertEquals(30, Regex("\"date\":").findAll(m).count())
        // Un'ora su un campo, con due campi aperti 15 ore: 3%. La domenica chiusa tutta.
        assertTrue(m, "{\"date\":\"2026-11-11\",\"bookings\":1,\"matches\":0,\"busy\":3,\"blocked\":0}" in m)
        assertTrue(m, "{\"date\":\"2026-11-15\",\"bookings\":0,\"matches\":0,\"busy\":0,\"blocked\":100}" in m)
        assertEquals(400, req("GET", "/week", token = k, q = mapOf("date" to "boh")).first)
    }

    @Test fun scuolaTennisDalPc() {
        val k = login()
        data.time = java.time.LocalDateTime.of(2026, 11, 20, 9, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        for (n in listOf("Anna", "Bea", "Cora")) req("POST", "/people", """{"firstName":"$n","lastName":"X","member":true}""", k)
        val ids = data.list.associate { it.firstName to it.id }
        assertEquals(400, req("POST", "/courses", """{"name":" ","from":"2026-11-01","to":"2026-12-31"}""", k).first)
        // Lunedì e mercoledì alle 17 sul Campo 1, novembre e dicembre, 40 euro al mese, al massimo 2 allievi.
        val body = """{"name":"SAT","from":"2026-11-02","to":"2026-12-30","feeCents":4000,"maxStudents":2,"slots":[{"weekday":1,"startMin":1020,"court":"Campo 1"},{"weekday":3,"startMin":1020}]}"""
        val (st, made) = req("POST", "/courses", body, k)
        assertEquals(200, st)
        val id = field(made, "id")!!
        assertEquals(400, req("PUT", "/courses/$id", body.replace("\"weekday\":3", "\"weekday\":9"), k).first)
        assertEquals(200, req("POST", "/courses/$id/students", """{"ids":["${ids["Anna"]}","${ids["Bea"]}"]}""", k).first)
        assertEquals(409, req("POST", "/courses/$id/students", """{"ids":["${ids["Cora"]}"]}""", k).first)
        val c = data.school.single()
        val lessons = Courses.lessons(c)
        assertEquals(18, lessons.size)
        val first = lessons.first()
        // Appello: Anna c'era, Cora no (non è allieva) e non conta.
        assertEquals(200, req("POST", "/courses/$id/lesson", """{"key":"${first.key}","present":["${ids["Anna"]}","${ids["Cora"]}"]}""", k).first)
        assertEquals(setOf(ids["Anna"]), data.school.single().attendance[first.key])
        assertEquals(200, req("POST", "/courses/$id/lesson", """{"key":"${lessons[1].key}","cancelled":true}""", k).first)
        assertTrue(lessons[1].key in data.school.single().cancelled)
        assertEquals(404, req("POST", "/courses/$id/lesson", """{"key":"boh","cancelled":true}""", k).first)
        val (_, list) = req("GET", "/courses", token = k)
        assertTrue(list, "\"present\":1,\"marked\":1" in list && "\"month\":\"2026-11\"" in list && "\"month\":\"2026-12\"" in list)
        // La quota di novembre: due addebiti, la seconda volta nessuno.
        assertEquals("""{"count":2}""", req("POST", "/courses/$id/charge", """{"month":"2026-11"}""", k).second)
        assertEquals("""{"count":0}""", req("POST", "/courses/$id/charge", """{"month":"2026-11"}""", k).second)
        assertEquals(404, req("POST", "/courses/$id/charge", """{"month":"2027-05"}""", k).first)
        assertTrue(req("GET", "/courses", token = k).second.contains("\"charged\":2,\"paid\":0"))
        assertEquals(200, req("DELETE", "/courses/$id/students/${ids["Bea"]}", token = k).first)
        assertEquals(listOf(ids["Anna"]), data.school.single().students)
        assertEquals(200, req("DELETE", "/courses/$id", token = k).first)
        assertTrue(data.school.isEmpty())
    }

    @Test fun sollecitiEStatistiche() {
        val k = login()
        req("POST", "/people", """{"firstName":"Mario","lastName":"Rossi","member":true,"phone":"333 1"}""", k)
        req("POST", "/people", """{"firstName":"Luca","lastName":"Bianchi","member":true}""", k)
        req("POST", "/fees", """{"name":"Quota","cents":5000,"chargeMembers":true}""", k)
        val mario = data.ledger.charges.first { it.name == "Mario Rossi" }.id
        val (_, prev) = req("POST", "/dues", """{"ids":["$mario"]}""", k)
        assertTrue(prev, "Mario Rossi" in prev && "Luca" !in prev && "333 1" in prev)
        assertEquals(400, req("POST", "/dues/send", """{"ids":[]}""", k).first)
        assertEquals(200, req("POST", "/dues/send", """{"ids":["$mario"]}""", k).first)
        assertEquals(setOf(mario), data.sent)
        req("POST", "/bookings", """{"court":"Campo 1","date":"2026-11-10","startMin":600,"minutes":60,"names":["Bianchi"]}""", k)
        val (st, out) = req("GET", "/stats", token = k, q = mapOf("from" to "2026-11-01", "to" to "2026-11-30"))
        assertEquals(200, st)
        assertTrue(out, "\"bookings\":1" in out && "\"cents\":1200" in out && "Bianchi" in out && "\"heat\":[[" in out)
        assertEquals(400, req("GET", "/stats", token = k, q = mapOf("from" to "2026-11-30", "to" to "2026-11-01")).first)
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

    @Test fun csvEsportatoSiReimporta() {
        val k = login()
        // Le colonne che scrive Esporta CSV dei Soci (in portoghese e in francese).
        for (csv in listOf(
            "Sobrenome;Nome;Telefone;E-mail;Data de nascimento;Sócio do clube;Sócio desde;Cartão do clube;Carteira federativa;Ano do cartão;Classificação;Atestado até;Deve\r\n" +
                "Rossi;Mario;333 1;m@x.it;01/03/1980;Sim;;77;123;;4.1;31/12/2026;\r\n",
            "Nom de famille;Prénom;Téléphone;E-mail;Date de naissance;Carte du club;Licence fédérale;Classement;Certificat jusqu'au\r\n" +
                "Verdi;Anna;333 2;;;78;456;4.2;30/06/2027\r\n",
        )) {
            val body = kotlinx.serialization.json.Json.encodeToString(DeskCsvIn.serializer(), DeskCsvIn(csv))
            assertTrue(req("POST", "/people/import", body, k).second.contains("\"added\":1"))
        }
        val m = data.list.first { it.firstName == "Mario" }.member!!
        assertEquals(listOf("77", "123", "4.1"), listOf(m.number, m.fitpNumber, m.ranking))
        assertNotNull(m.medicalUntil)
        val a = data.list.first { it.firstName == "Anna" }
        assertEquals(listOf("Verdi", "333 2", "456"), listOf(a.lastName, a.phone, a.member!!.fitpNumber))
        assertNotNull(a.member!!.medicalUntil)
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
            // HttpsURLConnection (c'è anche nei test Android, java.net.http no); il percorso va scritto così com'è.
            class Got(val code: Int, val headers: Map<String, List<String>>)
            fun get(p: String): Got {
                val c = URI("https://127.0.0.1:$port").toURL().let { java.net.URL(it, p) }.openConnection() as javax.net.ssl.HttpsURLConnection
                c.sslSocketFactory = ctx.socketFactory
                c.hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true }
                return try { Got(c.responseCode, c.headerFields.filterKeys { it != null }.mapKeys { it.key.lowercase() }) } finally { c.disconnect() }
            }
            val page = get("/")
            assertEquals(200, page.code)
            assertTrue(page.headers.getValue("content-security-policy").first().startsWith("default-src 'none'"))
            assertEquals("DENY", page.headers.getValue("x-frame-options").first())
            assertEquals(401, get("/api/people").code)
            assertEquals(404, get("/../../etc/passwd").code)
            assertEquals(404, get("/nascosto.kt").code)
            // Sulla stessa porta in chiaro non si parla.
            val plain = runCatching { Socket("127.0.0.1", port).use { s -> s.getOutputStream().write("GET / HTTP/1.0\r\n\r\n".toByteArray()); s.soTimeout = 3000; s.getInputStream().readBytes() } }
            assertFalse(plain.getOrDefault(ByteArray(0)).toString(Charsets.ISO_8859_1).contains("HTTP/1.1 200"))
            val ssl = (ctx.socketFactory as SSLSocketFactory).createSocket("127.0.0.1", port) as javax.net.ssl.SSLSocket
            ssl.use { it.enabledProtocols = arrayOf("TLSv1.1"); assertTrue(runCatching { it.startHandshake() }.isFailure) }
        } finally { server.stop() }
    }
}
