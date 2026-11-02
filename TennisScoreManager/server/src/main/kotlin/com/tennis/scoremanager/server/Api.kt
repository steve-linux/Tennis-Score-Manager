// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.server

import com.tennis.scoremanager.data.BookingRules
import com.tennis.scoremanager.data.Cell
import com.tennis.scoremanager.data.ClubSync
import com.tennis.scoremanager.data.CourtInfo
import com.tennis.scoremanager.data.CourtOnline
import com.tennis.scoremanager.data.OnlineBooking
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

const val API_VERSION = 1

@Serializable data class ErrorOut(val error: String)
@Serializable data class InfoOut(val club: String, val api: Int, val paired: Boolean, val synced: Boolean, val timeZone: String, val payInfo: String)
@Serializable data class CodeIn(val code: String)
@Serializable data class TokenOut(val token: String, val name: String = "", val player: String = "")
@Serializable data class InviteIn(val player: String, val name: String, val days: Int = 14)
@Serializable data class InviteOut(val code: String, val expires: Long)
@Serializable data class MyBooking(val id: String, val court: String, val start: Long, val minutes: Int, val cents: Int, val lights: Boolean, val heating: Boolean, val canCancel: Boolean)
@Serializable data class MeOut(
    val name: String, val club: String, val timeZone: String, val rules: BookingRules, val payInfo: String,
    /** I giorni prenotabili (yyyy-MM-dd nel fuso del circolo), da oggi. */
    val days: List<String>, val bookings: List<MyBooking>,
)
@Serializable data class DayOut(val date: String, val timeZone: String, val slotMinutes: Int, val open: Boolean, val courts: List<CourtInfo>, val cells: List<Cell>)
@Serializable data class BookIn(val court: String, val start: Long, val minutes: Int, val heating: Boolean = false)
@Serializable data class PlayerOut(val id: String, val name: String, val since: Long, val lastSeen: Long, val devices: Int)
@Serializable data class AdminOut(val syncedAt: Long, val bookings: List<OnlineBooking>, val players: List<PlayerOut>)
@Serializable data class QuoteOut(val cents: Int, val lights: Boolean, val heating: Boolean, val problem: String? = null)
@Serializable data class OkOut(val ok: Boolean = true)

/** Una risposta: codice HTTP e corpo JSON. */
data class Resp(val status: Int, val body: String)

/** Una richiesta già letta: metodo, percorso, parametri, corpo, chiave (Authorization: Bearer) e indirizzo. */
data class Req(val method: String, val path: String, val query: Map<String, String> = emptyMap(), val body: String = "", val token: String? = null, val ip: String = "")

class ApiError(val status: Int, val code: String) : Exception(code)

/**
 * Le chiamate del server, /api/v1/…: la segreteria si abbina col codice di setup, manda il planning e crea gli
 * inviti; i giocatori usano l'invito, guardano i giorni e prenotano o disdicono.
 */
class Api(private val store: Store, private val limiter: Limiter = Limiter()) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

    private fun <T> ok(ser: KSerializer<T>, v: T, status: Int = 200) = Resp(status, json.encodeToString(ser, v))
    private fun <T> read(ser: KSerializer<T>, r: Req): T =
        try { json.decodeFromString(ser, r.body) } catch (_: Exception) { throw ApiError(400, "BAD_REQUEST") }

    fun handle(r: Req): Resp = try {
        route(r)
    } catch (e: ApiError) {
        Resp(e.status, json.encodeToString(ErrorOut.serializer(), ErrorOut(e.code)))
    }

    private fun route(r: Req): Resp {
        val p = r.path.removePrefix("/api/v1").trimEnd('/').split('/').filter { it.isNotEmpty() }
        return when {
            r.method == "GET" && p == listOf("info") -> info()
            r.method == "POST" && p == listOf("admin", "pair") -> pair(r)
            r.method == "PUT" && p == listOf("admin", "sync") -> { admin(r); sync(r) }
            r.method == "GET" && p == listOf("admin", "state") -> { admin(r); adminState() }
            r.method == "POST" && p == listOf("admin", "invites") -> { admin(r); invite(r) }
            r.method == "DELETE" && p.size == 3 && p[0] == "admin" && p[1] == "bookings" -> { admin(r); adminCancel(p[2]) }
            r.method == "DELETE" && p.size == 3 && p[0] == "admin" && p[1] == "players" -> { admin(r); removePlayer(p[2]) }
            r.method == "POST" && p == listOf("claim") -> claim(r)
            r.method == "GET" && p == listOf("me") -> me(player(r))
            r.method == "GET" && p == listOf("day") -> day(player(r), r.query["date"])
            r.method == "GET" && p == listOf("quote") -> quote(player(r), r.query)
            r.method == "POST" && p == listOf("bookings") -> book(player(r), r)
            r.method == "DELETE" && p.size == 2 && p[0] == "bookings" -> cancel(player(r), p[1])
            r.method == "POST" && p == listOf("logout") -> logout(player(r), r.token!!)
            else -> throw ApiError(404, "NOT_FOUND")
        }
    }

    // --- Chiavi ---

    private fun guard(r: Req) { if (limiter.blocked(r.ip)) throw ApiError(429, "TOO_MANY_TRIES") }

    private fun admin(r: Req) {
        guard(r)
        val a = store.state.admin
        if (a == null || r.token == null || Secrets.hash(r.token) != a) { limiter.fail(r.ip); throw ApiError(401, "UNAUTHORIZED") }
    }

    private fun player(r: Req): Player {
        guard(r)
        val h = r.token?.let(Secrets::hash)
        val pl = h?.let { t -> store.state.players.firstOrNull { t in it.tokens } }
        if (pl == null) { limiter.fail(r.ip); throw ApiError(401, "UNAUTHORIZED") }
        val now = store.now()
        // L'ultimo accesso, al più una volta l'ora per non riscrivere il file a ogni pagina.
        if (now - pl.lastSeen > 3_600_000L) store.update { s -> s.copy(players = s.players.map { if (it.id == pl.id) it.copy(lastSeen = now) else it }) to Unit }
        return pl
    }

    // --- Segreteria ---

    private fun info(): Resp {
        val s = store.state
        return ok(InfoOut.serializer(), InfoOut(s.sync.club, API_VERSION, s.admin != null, s.syncedAt > 0, s.sync.timeZone, s.sync.payInfo))
    }

    private fun pair(r: Req): Resp {
        guard(r)
        val code = Secrets.normalize(read(CodeIn.serializer(), r).code)
        val token = Secrets.token()
        val done = store.update { s ->
            if (s.setup == null || Secrets.hash(code) != s.setup) s to false
            else s.copy(admin = Secrets.hash(token), setup = null) to true
        }
        if (!done) { limiter.fail(r.ip); throw ApiError(403, "BAD_CODE") }
        store.forgetSetupCode()
        return ok(TokenOut.serializer(), TokenOut(token))
    }

    private fun sync(r: Req): Resp {
        val sync = read(ClubSync.serializer(), r)
        if (sync.timeZone !in java.util.TimeZone.getAvailableIDs()) throw ApiError(400, "BAD_TIME_ZONE")
        Store.applyTimeZone(sync.timeZone)
        val now = store.now()
        store.update { s ->
            s.copy(
                sync = sync, syncedAt = now,
                // Le prenotazioni finite da più di 60 giorni e gli inviti scaduti non servono più.
                bookings = s.bookings.filter { it.end > now - 60L * 86_400_000L },
                invites = s.invites.filter { it.expires > now },
            ) to Unit
        }
        return adminState()
    }

    private fun adminState(): Resp {
        val s = store.state
        return ok(AdminOut.serializer(), AdminOut(s.syncedAt, s.bookings.sortedBy { it.start }, s.players.map { PlayerOut(it.id, it.name, it.since, it.lastSeen, it.tokens.size) }))
    }

    private fun invite(r: Req): Resp {
        val i = read(InviteIn.serializer(), r)
        if (i.player.isBlank() || i.name.isBlank()) throw ApiError(400, "BAD_REQUEST")
        val code = Secrets.code()
        val expires = store.now() + i.days.coerceIn(1, 90) * 86_400_000L
        store.update { s ->
            // Un nuovo invito per lo stesso giocatore sostituisce il vecchio.
            s.copy(invites = s.invites.filter { it.player != i.player } + Invite(Secrets.hash(code), i.player, i.name.trim().take(60), expires)) to Unit
        }
        return ok(InviteOut.serializer(), InviteOut(code, expires))
    }

    private fun adminCancel(id: String): Resp {
        val found = store.update { s -> s.copy(bookings = s.bookings.filter { it.id != id }) to s.bookings.any { it.id == id } }
        if (!found) throw ApiError(404, "NOT_FOUND")
        return ok(OkOut.serializer(), OkOut())
    }

    /** Il giocatore non entra più (le sue prenotazioni restano, la segreteria le vede e le toglie se serve). */
    private fun removePlayer(id: String): Resp {
        store.update { s -> s.copy(players = s.players.filter { it.id != id }, invites = s.invites.filter { it.player != id }) to Unit }
        return ok(OkOut.serializer(), OkOut())
    }

    // --- Giocatori ---

    private fun claim(r: Req): Resp {
        guard(r)
        val h = Secrets.hash(Secrets.normalize(read(CodeIn.serializer(), r).code))
        val token = Secrets.token()
        val now = store.now()
        val inv = store.update { s ->
            val inv = s.invites.firstOrNull { it.code == h && it.expires > now } ?: return@update s to null
            val old = s.players.firstOrNull { it.id == inv.player }
            // Fino a tre telefoni per giocatore: il quarto manda fuori il più vecchio.
            val pl = (old ?: Player(inv.player, inv.name, since = now)).let { it.copy(name = inv.name, tokens = (it.tokens + Secrets.hash(token)).takeLast(3), lastSeen = now) }
            s.copy(players = s.players.filter { it.id != pl.id } + pl, invites = s.invites - inv) to inv
        }
        if (inv == null) { limiter.fail(r.ip); throw ApiError(403, "BAD_CODE") }
        return ok(TokenOut.serializer(), TokenOut(token, inv.name, inv.player))
    }

    private fun zone(): ZoneId = ZoneId.of(store.state.sync.timeZone)

    private fun mine(pl: Player, b: OnlineBooking, now: Long) = MyBooking(
        b.id, b.court, b.start, b.minutes, b.cents, b.lights, b.heating,
        canCancel = CourtOnline.checkCancel(store.state.sync, store.state.bookings, pl.id, b.id, now) == null,
    )

    private fun me(pl: Player): Resp {
        val s = store.state
        val now = store.now()
        val today = Instant.ofEpochMilli(now).atZone(zone()).toLocalDate()
        val days = (0..s.sync.rules.daysAhead.coerceIn(0, 60)).map { today.plusDays(it.toLong()).toString() }
        return ok(MeOut.serializer(), MeOut(pl.name, s.sync.club, s.sync.timeZone, s.sync.rules, s.sync.payInfo, days, CourtOnline.future(s.bookings, pl.id, now).map { mine(pl, it, now) }))
    }

    private fun day(pl: Player, date: String?): Resp {
        val s = store.state
        if (s.syncedAt == 0L) throw ApiError(503, "NOT_SYNCED")
        val d = try { LocalDate.parse(date ?: "") } catch (_: Exception) { throw ApiError(400, "BAD_DATE") }
        val day = d.atStartOfDay(zone()).toInstant().toEpochMilli()
        val courts = CourtOnline.bookableCourts(s.sync).map { s.sync.settings.info(it).copy(photos = emptyList()) }
        return ok(DayOut.serializer(), DayOut(d.toString(), s.sync.timeZone, s.sync.settings.slotMinutes, s.sync.rules.open, courts, CourtOnline.grid(s.sync, s.bookings, pl.id, day, store.now())))
    }

    /** Quanto costerebbe e se si può fare, prima di confermare. */
    private fun quote(pl: Player, q: Map<String, String>): Resp {
        val s = store.state
        if (s.syncedAt == 0L) throw ApiError(503, "NOT_SYNCED")
        val court = q["court"] ?: throw ApiError(400, "BAD_REQUEST")
        val start = q["start"]?.toLongOrNull() ?: throw ApiError(400, "BAD_REQUEST")
        val minutes = q["minutes"]?.toIntOrNull() ?: throw ApiError(400, "BAD_REQUEST")
        val now = store.now()
        val b = CourtOnline.make(s.sync, "", pl.id, pl.name, court, start, minutes, q["heating"] == "true", now)
        return ok(QuoteOut.serializer(), QuoteOut(b.cents, b.lights, b.heating, CourtOnline.check(s.sync, s.bookings, pl.id, court, start, minutes, now)?.name))
    }

    private fun book(pl: Player, r: Req): Resp {
        val b = read(BookIn.serializer(), r)
        if (store.state.syncedAt == 0L) throw ApiError(503, "NOT_SYNCED")
        val now = store.now()
        // Controllo e salvataggio insieme: due giocatori sulla stessa casella, uno solo la prende.
        val res = store.update { s ->
            val problem = CourtOnline.check(s.sync, s.bookings, pl.id, b.court, b.start, b.minutes, now)
            if (problem != null) s to Result.failure(ApiError(409, problem.name))
            else {
                val nb = CourtOnline.make(s.sync, Secrets.id("w"), pl.id, pl.name, b.court, b.start, b.minutes, b.heating, now)
                s.copy(bookings = s.bookings + nb) to Result.success(nb)
            }
        }
        val nb = res.getOrElse { throw it }
        return ok(MyBooking.serializer(), mine(pl, nb, now), 201)
    }

    private fun cancel(pl: Player, id: String): Resp {
        val now = store.now()
        val problem = store.update { s ->
            val p = CourtOnline.checkCancel(s.sync, s.bookings, pl.id, id, now)
            (if (p == null) s.copy(bookings = s.bookings.filter { it.id != id }) else s) to p
        }
        if (problem != null) throw ApiError(if (problem.name == "NOT_FOUND") 404 else 409, problem.name)
        return ok(OkOut.serializer(), OkOut())
    }

    private fun logout(pl: Player, token: String): Resp {
        val h = Secrets.hash(token)
        store.update { s -> s.copy(players = s.players.map { if (it.id == pl.id) it.copy(tokens = it.tokens - h) else it }) to Unit }
        return ok(OkOut.serializer(), OkOut())
    }
}

/** Troppi tentativi sbagliati dallo stesso indirizzo: 20 in 15 minuti, poi si aspetta. */
class Limiter(private val max: Int = 20, private val windowMs: Long = 15 * 60_000L, private val clock: () -> Long = System::currentTimeMillis) {
    private val fails = HashMap<String, MutableList<Long>>()

    @Synchronized fun fail(ip: String) { fails.getOrPut(ip) { mutableListOf() } += clock() }

    @Synchronized fun blocked(ip: String): Boolean {
        val now = clock()
        val list = fails[ip] ?: return false
        list.removeAll { now - it > windowMs }
        if (list.isEmpty()) fails.remove(ip)
        return list.size >= max
    }
}

