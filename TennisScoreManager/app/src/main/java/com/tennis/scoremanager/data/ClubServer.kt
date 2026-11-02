// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

/**
 * Il server del circolo visto dalla segreteria (facoltativo: [url] vuoto = nessun server, si prenota in
 * segreteria come sempre). [token] è la chiave della segreteria; [cancel] le prenotazioni online tolte dal
 * planning ma non ancora tolte dal server.
 */
@Serializable
data class ClubServerConf(
    val url: String = "",
    val token: String = "",
    /** L'indirizzo da internet (https://…, con Caddy): per gli inviti e per la segreteria fuori dal circolo. */
    val publicUrl: String = "",
    val club: String = "",
    val rules: BookingRules = BookingRules(),
    val payInfo: String = "",
    val lastSync: Long = 0,
    /** L'ultimo errore (codice del server, o NET se non risponde); null = tutto bene. */
    val error: String? = null,
    val players: List<SrvPlayer> = emptyList(),
    val cancel: List<String> = emptyList(),
) {
    val paired: Boolean get() = url.isNotBlank() && token.isNotBlank()

    /** Dove cercare il server: prima nella rete del circolo, poi da internet. */
    val addresses: List<String> get() = listOf(url, publicUrl).filter { it.isNotBlank() }.distinct()
}

/**
 * Il giocatore che prenota dall'app (non la segreteria): il server del suo circolo e la chiave che gli ha dato
 * l'invito. Vuoto = non è entrato in nessun circolo.
 */
@Serializable
data class BookingAccount(val url: String = "", val token: String = "", val name: String = "", val player: String = "", val club: String = "") {
    val joined: Boolean get() = url.isNotBlank() && token.isNotBlank()
}

object ClubServers {
    /**
     * Il link dell'invito (https://circolo/#i=CODICE, anche col QR) diviso in indirizzo e codice; un codice da
     * solo dà indirizzo null. null = non è un invito.
     */
    fun parseInvite(text: String): Pair<String?, String>? {
        val t = text.trim()
        val i = t.indexOf("#i=")
        if (i >= 0) {
            val code = t.substring(i + 3).takeWhile { it.isLetterOrDigit() || it == '-' }.filter { it.isLetterOrDigit() }.uppercase()
            val url = cleanUrl(t.substring(0, i).substringBefore('?').trimEnd('/'))
            return if (code.length >= 6 && url.isNotEmpty()) url to code else null
        }
        val code = t.filter { it.isLetterOrDigit() }.uppercase()
        return if (t.all { it.isLetterOrDigit() || it == '-' } && code.length in 6..16) null to code else null
    }

    /** L'indirizzo come lo si scrive: senza / in fondo, http:// se manca. */
    fun cleanUrl(u: String): String {
        val t = u.trim().trimEnd('/')
        return if (t.isEmpty() || t.startsWith("http://") || t.startsWith("https://")) t else "http://$t"
    }

    /** Il link dell'invito: la pagina del circolo col codice dopo il # (non arriva nei log del server). */
    fun inviteLink(conf: ClubServerConf, code: String): String = conf.publicUrl.ifBlank { conf.url } + "/#i=" + code

    /**
     * La chiamata [f] al primo indirizzo che risponde: quello del circolo e, se non c'è rete per arrivarci (la
     * segreteria è a casa), quello da internet. Gli altri errori (chiave, regole) non cambiano indirizzo.
     */
    fun <T> withServer(conf: ClubServerConf, f: (ClubServerClient) -> T): T {
        var last: ClubServerError? = null
        for (u in conf.addresses) {
            try { return f(ClubServerClient(u, conf.token)) } catch (e: ClubServerError) {
                if (e.code != "NET") throw e
                last = e
            }
        }
        throw last ?: ClubServerError("NET")
    }

    /**
     * Quello che si manda al server: i campi, gli orari, i blocchi e tutto l'occupato della segreteria
     * (prenotazioni e partite di torneo), senza nomi; le prenotazioni online il server le conosce già.
     */
    fun buildSync(conf: ClubServerConf, plan: CourtPlan, tournaments: List<ClubTournament>, courts: List<String>, club: String, timeZone: String): ClubSync {
        val busy = plan.bookings.filter { it.online == null }.map { Busy(it.court, it.start, it.end) } +
            tournaments.flatMap { t ->
                t.matches.filter { m -> m.court.isNotBlank() && m.at != null && !m.bye }.map { m -> Busy(m.court, m.at!!, m.at + plan.settings.tournamentMinutes * 60_000L) }
            }
        val now = System.currentTimeMillis()
        return ClubSync(
            club = club, timeZone = timeZone, courts = courts,
            settings = plan.settings.copy(courts = plan.settings.courts.map { it.copy(photos = emptyList()) }),
            // Solo quello che conta da ieri in poi: il passato al server non serve.
            blocks = plan.blocks.filter { it.to >= now - 86_400_000L },
            busy = busy.filter { it.end >= now - 86_400_000L },
            rules = conf.rules.copy(courts = conf.rules.courts.filter { it in courts }), payInfo = conf.payInfo,
        )
    }

    /**
     * Le prenotazioni online nel planning: quelle nuove entrano (col giocatore del database se c'è), quelle già
     * entrate restano come le ha sistemate la segreteria. Restituisce anche quelle sparite dal server (disdette dal
     * giocatore), da togliere dal planning; le vecchie che il server ha già dimenticato restano.
     */
    fun merge(plan: CourtPlan, online: List<OnlineBooking>, known: (String) -> Boolean, now: Long): Pair<List<CourtBooking>, List<CourtBooking>> {
        val have = plan.bookings.mapNotNull { b -> b.online?.let { it to b } }.toMap()
        val ids = online.map { it.id }.toSet()
        val added = online.filter { it.id !in have }.map { o ->
            CourtBooking(
                id = "o" + o.id, court = o.court, start = o.start, minutes = o.minutes, kind = BookingKind.PLAY,
                players = if (known(o.player)) listOf(o.player) else emptyList(), names = if (known(o.player)) emptyList() else listOf(o.name),
                lights = o.lights, cents = o.cents, heating = o.heating, online = o.id,
            )
        }
        val gone = plan.bookings.filter { it.online != null && it.online !in ids && it.end > now - 30L * 86_400_000L }
        return added to gone
    }
}

/** Una risposta sbagliata del server: il codice (BAD_CODE, UNAUTHORIZED…) o NET se non risponde. */
class ClubServerError(val code: String) : Exception(code)

/** Le chiamate della segreteria al server, in HTTP semplice (rete del circolo) o HTTPS (da internet). */
class ClubServerClient(private val url: String, private val token: String?) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun <I, O> call(method: String, path: String, inSer: KSerializer<I>?, body: I?, outSer: KSerializer<O>): O {
        val c = try { URL("$url/api/v1$path").openConnection() as HttpURLConnection } catch (_: Exception) { throw ClubServerError("BAD_URL") }
        try {
            c.requestMethod = method
            c.connectTimeout = 8_000; c.readTimeout = 15_000
            c.setRequestProperty("Accept", "application/json")
            token?.let { c.setRequestProperty("Authorization", "Bearer $it") }
            if (inSer != null && body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                c.outputStream.use { it.write(json.encodeToString(inSer, body).toByteArray()) }
            }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
            if (code !in 200..299) throw ClubServerError(runCatching { json.decodeFromString(SrvError.serializer(), text).error }.getOrDefault("HTTP_$code"))
            return try { json.decodeFromString(outSer, text) } catch (_: Exception) { throw ClubServerError("NOT_TSM") }
        } catch (e: ClubServerError) {
            throw e
        } catch (_: Exception) {
            throw ClubServerError("NET")
        } finally {
            c.disconnect()
        }
    }

    fun info(): SrvInfo = call<Unit, SrvInfo>("GET", "/info", null, null, SrvInfo.serializer())
    fun pair(code: String): SrvToken = call("POST", "/admin/pair", SrvCode.serializer(), SrvCode(code), SrvToken.serializer())
    fun sync(s: ClubSync): SrvAdmin = call("PUT", "/admin/sync", ClubSync.serializer(), s, SrvAdmin.serializer())
    fun state(): SrvAdmin = call<Unit, SrvAdmin>("GET", "/admin/state", null, null, SrvAdmin.serializer())
    fun invite(player: String, name: String): SrvInvite = call("POST", "/admin/invites", SrvInviteIn.serializer(), SrvInviteIn(player, name), SrvInvite.serializer())
    fun cancel(id: String): SrvOk = call<Unit, SrvOk>("DELETE", "/admin/bookings/$id", null, null, SrvOk.serializer())
    fun removePlayer(id: String): SrvOk = call<Unit, SrvOk>("DELETE", "/admin/players/$id", null, null, SrvOk.serializer())

    // Il giocatore (con la chiave avuta dall'invito).
    private fun enc(v: String) = java.net.URLEncoder.encode(v, "UTF-8")
    fun claim(code: String): SrvToken = call("POST", "/claim", SrvCode.serializer(), SrvCode(code), SrvToken.serializer())
    fun me(): SrvMe = call<Unit, SrvMe>("GET", "/me", null, null, SrvMe.serializer())
    fun day(date: String): SrvDay = call<Unit, SrvDay>("GET", "/day?date=${enc(date)}", null, null, SrvDay.serializer())
    fun quote(court: String, start: Long, minutes: Int, heating: Boolean): SrvQuote =
        call<Unit, SrvQuote>("GET", "/quote?court=${enc(court)}&start=$start&minutes=$minutes&heating=$heating", null, null, SrvQuote.serializer())
    fun book(b: SrvBookIn): SrvMyBooking = call("POST", "/bookings", SrvBookIn.serializer(), b, SrvMyBooking.serializer())
    fun cancelMine(id: String): SrvOk = call<Unit, SrvOk>("DELETE", "/bookings/${enc(id)}", null, null, SrvOk.serializer())
    fun logout(): SrvOk = call<Unit, SrvOk>("POST", "/logout", null, null, SrvOk.serializer())
}
