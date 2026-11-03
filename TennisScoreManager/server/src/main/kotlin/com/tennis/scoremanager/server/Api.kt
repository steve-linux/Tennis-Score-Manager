// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.server

import com.tennis.scoremanager.data.BookingRules
import com.tennis.scoremanager.data.Cell
import com.tennis.scoremanager.data.ClubSync
import com.tennis.scoremanager.data.CourtInfo
import com.tennis.scoremanager.data.CourtOnline
import com.tennis.scoremanager.data.OnlineBooking
import com.tennis.scoremanager.data.SrvAdmin
import com.tennis.scoremanager.data.SrvBookIn
import com.tennis.scoremanager.data.SrvDay
import com.tennis.scoremanager.data.SrvMe
import com.tennis.scoremanager.data.SrvMyBooking
import com.tennis.scoremanager.data.SrvQuote
import com.tennis.scoremanager.data.SrvCode
import com.tennis.scoremanager.data.SrvError
import com.tennis.scoremanager.data.SrvInfo
import com.tennis.scoremanager.data.SrvInvite
import com.tennis.scoremanager.data.SrvInviteIn
import com.tennis.scoremanager.data.SrvOk
import com.tennis.scoremanager.data.SrvPlayer
import com.tennis.scoremanager.data.SrvPushKey
import com.tennis.scoremanager.data.SrvWait
import com.tennis.scoremanager.data.SrvWaitIn
import com.tennis.scoremanager.data.Wait
import com.tennis.scoremanager.data.WaitProblem
import com.tennis.scoremanager.data.SrvPushSub
import com.tennis.scoremanager.data.SrvToken
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

const val API_VERSION = 1

/** Una risposta: codice HTTP e corpo JSON. */
data class Resp(val status: Int, val body: String)

/** Una richiesta già letta: metodo, percorso, parametri, corpo, chiave (Authorization: Bearer) e indirizzo. */
data class Req(
    val method: String, val path: String, val query: Map<String, String> = emptyMap(), val body: String = "", val token: String? = null, val ip: String = "",
    /** Arrivata da internet, attraverso Caddy (HTTPS), e non dalla rete del circolo. */
    val viaProxy: Boolean = false,
)

class ApiError(val status: Int, val code: String) : Exception(code)

/** Manda una notifica push e restituisce il codice HTTP del servizio push. */
fun interface Pusher { fun send(vapid: WebPush.Vapid, sub: PushSub, payload: String, ttlSeconds: Int): Int }

/** Il contatto nelle notifiche (richiesto da Apple): un indirizzo https o mailto. */
val PUSH_SUBJECT: String = System.getenv("TSM_PUSH_CONTACT") ?: "https://github.com/steve-linux/Tennis-Score-Manager"

/**
 * Le chiamate del server, /api/v1/…: la segreteria si abbina col codice di setup, manda il planning e crea gli
 * inviti; i giocatori usano l'invito, guardano i giorni e prenotano o disdicono.
 */
class Api(
    private val store: Store, private val limiter: Limiter = Limiter(),
    /** Abbinare la segreteria anche da internet (TSM_PAIR_REMOTE=1); di solito solo dalla rete del circolo. */
    private val pairRemote: Boolean = System.getenv("TSM_PAIR_REMOTE") == "1",
    /** Chi manda le notifiche push (nei test, uno finto che le conta). */
    private val pusher: Pusher = Pusher { v, sub, payload, ttl -> WebPush.send(v, PUSH_SUBJECT, sub, payload, ttl) },
    /** Le notifiche partono in un filo a parte, per non far aspettare chi prenota (nei test, subito). */
    private val async: Boolean = true,
) {
    private val sender = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "push").apply { isDaemon = true } }
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

    private fun <T> ok(ser: KSerializer<T>, v: T, status: Int = 200) = Resp(status, json.encodeToString(ser, v))
    private fun <T> read(ser: KSerializer<T>, r: Req): T =
        try { json.decodeFromString(ser, r.body) } catch (_: Exception) { throw ApiError(400, "BAD_REQUEST") }

    fun handle(r: Req): Resp = try {
        route(r)
    } catch (e: ApiError) {
        Resp(e.status, json.encodeToString(SrvError.serializer(), SrvError(e.code)))
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
            r.method == "POST" && p == listOf("waits") -> addWait(player(r), r)
            r.method == "DELETE" && p.size == 2 && p[0] == "waits" -> removeWait(player(r), p[1])
            r.method == "POST" && p == listOf("logout") -> logout(player(r), r.token!!)
            r.method == "GET" && p == listOf("push", "key") -> { player(r); ok(SrvPushKey.serializer(), SrvPushKey(vapid().publicKey)) }
            r.method == "POST" && p == listOf("push", "subscribe") -> subscribe(player(r), r)
            r.method == "POST" && p == listOf("push", "unsubscribe") -> unsubscribe(player(r), r)
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
        return ok(SrvInfo.serializer(), SrvInfo(s.sync.club, API_VERSION, s.admin != null, s.syncedAt > 0, s.sync.timeZone, s.sync.payInfo))
    }

    private fun pair(r: Req): Resp {
        guard(r)
        // Il codice di abbinamento non si prova da internet: si abbina dal circolo, con l'indirizzo di casa.
        if (r.viaProxy && !pairRemote) throw ApiError(403, "PAIR_LOCAL_ONLY")
        val code = Secrets.normalize(read(SrvCode.serializer(), r).code)
        val token = Secrets.token()
        val done = store.update { s ->
            if (s.setup == null || Secrets.hash(code) != s.setup) s to false
            else s.copy(admin = Secrets.hash(token), setup = null) to true
        }
        if (!done) { limiter.fail(r.ip); throw ApiError(403, "BAD_CODE") }
        store.forgetSetupCode()
        return ok(SrvToken.serializer(), SrvToken(token))
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
        // Il planning nuovo può aver liberato delle caselle.
        checkWaits(now)
        return adminState()
    }

    private fun adminState(): Resp {
        val s = store.state
        return ok(SrvAdmin.serializer(), SrvAdmin(s.syncedAt, s.bookings.sortedBy { it.start }, s.players.map { SrvPlayer(it.id, it.name, it.since, it.lastSeen, it.tokens.size) }))
    }

    private fun invite(r: Req): Resp {
        val i = read(SrvInviteIn.serializer(), r)
        if (i.player.isBlank() || i.name.isBlank()) throw ApiError(400, "BAD_REQUEST")
        val code = Secrets.code()
        val expires = store.now() + i.days.coerceIn(1, 90) * 86_400_000L
        store.update { s ->
            // Un nuovo invito per lo stesso giocatore sostituisce il vecchio.
            s.copy(invites = s.invites.filter { it.player != i.player } + Invite(Secrets.hash(code), i.player, i.name.trim().take(60), expires)) to Unit
        }
        return ok(SrvInvite.serializer(), SrvInvite(code, expires))
    }

    private fun adminCancel(id: String): Resp {
        val gone = store.update { s -> s.copy(bookings = s.bookings.filter { it.id != id }) to s.bookings.firstOrNull { it.id == id } }
            ?: throw ApiError(404, "NOT_FOUND")
        // Tolta (o spostata) dalla segreteria: il giocatore lo sa subito, se la partita non è già passata.
        if (gone.start > store.now()) push(gone.player, ttl = 24 * 3600) { PushTexts.removed(it.lang, gone, store.state.sync.timeZone) }
        checkWaits(store.now())
        return ok(SrvOk.serializer(), SrvOk())
    }

    // --- Notifiche push ---

    private fun vapid(): WebPush.Vapid = store.state.vapid ?: store.update { s -> (s.vapid ?: WebPush.newVapid()).let { s.copy(vapid = it) to it } }

    /** Manda a tutti i telefoni del giocatore; gli abbonamenti scaduti (404, 410) si tolgono. */
    private fun push(player: String, ttl: Int, payload: (PushSub) -> String) {
        val subs = store.state.pushSubs.filter { it.player == player }
        if (subs.isEmpty()) return
        val v = vapid()
        val job = Runnable {
            val dead = subs.filter { sub -> pusher.send(v, sub, payload(sub), ttl).let { it == 404 || it == 410 } }.map { it.endpoint }.toSet()
            if (dead.isNotEmpty()) store.update { s -> s.copy(pushSubs = s.pushSubs.filter { it.endpoint !in dead }) to Unit }
        }
        if (async) sender.execute(job) else job.run()
    }

    private fun subscribe(pl: Player, r: Req): Resp {
        val sub = read(SrvPushSub.serializer(), r)
        val host = runCatching { java.net.URI(sub.endpoint) }.getOrNull()
        if (host?.scheme != "https" || host.host.isNullOrBlank() || sub.endpoint.length > 1000 || sub.p256dh.isBlank() || sub.auth.isBlank()) throw ApiError(400, "BAD_REQUEST")
        runCatching { require(WebPush.dec(sub.p256dh).size == 65 && WebPush.dec(sub.auth).size == 16) }.getOrElse { throw ApiError(400, "BAD_REQUEST") }
        val now = store.now()
        val ps = PushSub(pl.id, sub.endpoint, sub.p256dh, sub.auth, sub.lang.take(2).lowercase(), now)
        // Lo stesso browser sostituisce il suo abbonamento; al massimo 5 per giocatore.
        store.update { s -> s.copy(pushSubs = (s.pushSubs.filter { it.endpoint != ps.endpoint } + ps).let { all -> all.filter { it.player != pl.id } + all.filter { it.player == pl.id }.takeLast(5) }) to Unit }
        // Una prima notifica: così si vede subito che funziona.
        val job = Runnable { pusher.send(vapid(), ps, PushTexts.welcome(ps.lang, store.state.sync.club), 3600) }
        if (async) sender.execute(job) else job.run()
        return ok(SrvOk.serializer(), SrvOk())
    }

    private fun unsubscribe(pl: Player, r: Req): Resp {
        val sub = read(SrvPushSub.serializer(), r)
        store.update { s -> s.copy(pushSubs = s.pushSubs.filter { !(it.player == pl.id && it.endpoint == sub.endpoint) }) to Unit }
        return ok(SrvOk.serializer(), SrvOk())
    }

    /**
     * Ogni minuto: i promemoria delle partite che cominciano entro le ore decise dal circolo. Chi prenota già dentro
     * quelle ore non lo riceve (lo sa); ognuno parte una volta sola.
     */
    fun tick(now: Long = store.now()) {
        val hours = store.state.sync.rules.remindHours
        val due = store.update { s ->
            val window = hours * 3_600_000L
            val inWindow = if (hours <= 0) emptyList() else s.bookings.filter { it.id !in s.reminded && it.start > now && it.start - now <= window }
            val send = inWindow.filter { it.createdAt < it.start - window }
            val ids = s.bookings.map { it.id }.toSet()
            s.copy(reminded = (s.reminded.filter { it in ids } + inWindow.map { it.id }).distinct()) to send
        }
        for (b in due) push(b.player, ttl = ((b.start - now) / 1000).toInt().coerceAtLeast(60)) { PushTexts.reminder(it.lang, b, store.state.sync.timeZone) }
        checkWaits(now)
    }

    /** Il giocatore non entra più (le sue prenotazioni restano, la segreteria le vede e le toglie se serve). */
    private fun removePlayer(id: String): Resp {
        store.update { s -> s.copy(players = s.players.filter { it.id != id }, invites = s.invites.filter { it.player != id }, pushSubs = s.pushSubs.filter { it.player != id }, waits = s.waits.filter { it.player != id }) to Unit }
        return ok(SrvOk.serializer(), SrvOk())
    }

    // --- Giocatori ---

    private fun claim(r: Req): Resp {
        guard(r)
        val h = Secrets.hash(Secrets.normalize(read(SrvCode.serializer(), r).code))
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
        return ok(SrvToken.serializer(), SrvToken(token, inv.name, inv.player))
    }

    private fun zone(): ZoneId = ZoneId.of(store.state.sync.timeZone)

    private fun mine(pl: Player, b: OnlineBooking, now: Long) = SrvMyBooking(
        b.id, b.court, b.start, b.minutes, b.cents, b.lights, b.heating,
        canCancel = CourtOnline.checkCancel(store.state.sync, store.state.bookings, pl.id, b.id, now) == null,
    )

    private fun me(pl: Player): Resp {
        val s = store.state
        val now = store.now()
        val today = Instant.ofEpochMilli(now).atZone(zone()).toLocalDate()
        val days = (0..s.sync.rules.daysAhead.coerceIn(0, 60)).map { today.plusDays(it.toLong()).toString() }
        val waits = s.waits.filter { it.player == pl.id && it.start > now }.sortedBy { it.start }.map { SrvWait(it.id, it.court, it.start, it.minutes, it.freedCourt) }
        return ok(SrvMe.serializer(), SrvMe(pl.name, s.sync.club, s.sync.timeZone, s.sync.rules, s.sync.payInfo, days, CourtOnline.future(s.bookings, pl.id, now).map { mine(pl, it, now) }, waits))
    }

    private fun day(pl: Player, date: String?): Resp {
        val s = store.state
        if (s.syncedAt == 0L) throw ApiError(503, "NOT_SYNCED")
        val d = try { LocalDate.parse(date ?: "") } catch (_: Exception) { throw ApiError(400, "BAD_DATE") }
        val day = d.atStartOfDay(zone()).toInstant().toEpochMilli()
        val courts = CourtOnline.bookableCourts(s.sync).map { s.sync.settings.info(it).copy(photos = emptyList()) }
        return ok(SrvDay.serializer(), SrvDay(d.toString(), s.sync.timeZone, s.sync.settings.slotMinutes, s.sync.rules.open, courts, CourtOnline.grid(s.sync, s.bookings, pl.id, day, store.now(), s.waits)))
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
        return ok(SrvQuote.serializer(), SrvQuote(b.cents, b.lights, b.heating, CourtOnline.check(s.sync, s.bookings, pl.id, court, start, minutes, now)?.name))
    }

    private fun book(pl: Player, r: Req): Resp {
        val b = read(SrvBookIn.serializer(), r)
        if (store.state.syncedAt == 0L) throw ApiError(503, "NOT_SYNCED")
        val now = store.now()
        // Controllo e salvataggio insieme: due giocatori sulla stessa casella, uno solo la prende.
        val res = store.update { s ->
            val problem = CourtOnline.check(s.sync, s.bookings, pl.id, b.court, b.start, b.minutes, now)
            if (problem != null) s to Result.failure(ApiError(409, problem.name))
            else {
                val nb = CourtOnline.make(s.sync, Secrets.id("w"), pl.id, pl.name, b.court, b.start, b.minutes, b.heating, now)
                // Prenotato: le sue attese per quell'ora non servono più.
                s.copy(bookings = s.bookings + nb, waits = s.waits.filter { !(it.player == pl.id && it.start == b.start && (it.court == null || it.court == b.court)) }) to Result.success(nb)
            }
        }
        val nb = res.getOrElse { throw it }
        return ok(SrvMyBooking.serializer(), mine(pl, nb, now), 201)
    }

    private fun cancel(pl: Player, id: String): Resp {
        val now = store.now()
        val problem = store.update { s ->
            val p = CourtOnline.checkCancel(s.sync, s.bookings, pl.id, id, now)
            (if (p == null) s.copy(bookings = s.bookings.filter { it.id != id }) else s) to p
        }
        if (problem != null) throw ApiError(if (problem.name == "NOT_FOUND") 404 else 409, problem.name)
        checkWaits(now)
        return ok(SrvOk.serializer(), SrvOk())
    }

    // --- Liste d'attesa ---

    private fun addWait(pl: Player, r: Req): Resp {
        val w = read(SrvWaitIn.serializer(), r)
        if (store.state.syncedAt == 0L) throw ApiError(503, "NOT_SYNCED")
        val now = store.now()
        val res = store.update { s ->
            val problem = CourtOnline.checkWait(s.sync, s.bookings, pl.id, w.court, w.start, w.minutes, now)
            val open = s.waits.filter { it.player == pl.id && it.freedAt == null && !(it.court == w.court && it.start == w.start) }
            when {
                problem != null -> s to Result.failure(ApiError(409, if (problem.first == WaitProblem.FREE_NOW) "FREE_NOW" else problem.second!!.name))
                // Con il massimo di prenotazioni, liberarsi un campo non servirebbe: non si potrebbe prenotarlo.
                CourtOnline.future(s.bookings, pl.id, now).size >= s.sync.rules.maxFuture.coerceAtLeast(1) -> s to Result.failure(ApiError(409, "TOO_MANY"))
                open.size >= 3 -> s to Result.failure(ApiError(409, "TOO_MANY_WAITS"))
                else -> {
                    val nw = Wait(Secrets.id("a"), pl.id, w.court, w.start, w.minutes, now)
                    s.copy(waits = s.waits.filter { !(it.player == pl.id && it.court == w.court && it.start == w.start) } + nw) to Result.success(nw)
                }
            }
        }
        val nw = res.getOrElse { throw it }
        return ok(SrvWait.serializer(), SrvWait(nw.id, nw.court, nw.start, nw.minutes), 201)
    }

    private fun removeWait(pl: Player, id: String): Resp {
        store.update { s -> s.copy(waits = s.waits.filter { !(it.id == id && it.player == pl.id) }) to Unit }
        return ok(SrvOk.serializer(), SrvOk())
    }

    /**
     * Le caselle che si sono liberate: a chi aspetta arriva l'avviso (una volta), e la sua attesa resta "liberata"
     * finché non prenota o la casella passa. Le attese di caselle già cominciate si tolgono.
     */
    fun checkWaits(now: Long = store.now()) {
        val freed = store.update { s ->
            val alive = s.waits.filter { it.start > now }
            val news = mutableListOf<Pair<Wait, String>>()
            val next = alive.map { w ->
                if (w.freedAt != null) {
                    // Liberata ma ripresa da un altro: si torna ad aspettare.
                    if (CourtOnline.freedCourt(s.sync, s.bookings, w, now) == null) w.copy(freedAt = null, freedCourt = null) else w
                } else {
                    val court = CourtOnline.freedCourt(s.sync, s.bookings, w, now)
                    if (court == null) w else w.copy(freedAt = now, freedCourt = court).also { news += it to court }
                }
            }
            s.copy(waits = next) to news.toList()
        }
        for ((w, court) in freed) push(w.player, ttl = ((w.start - now) / 1000).toInt().coerceAtLeast(60)) { PushTexts.freed(it.lang, w, court, store.state.sync.timeZone) }
    }

    private fun logout(pl: Player, token: String): Resp {
        val h = Secrets.hash(token)
        store.update { s -> s.copy(players = s.players.map { if (it.id == pl.id) it.copy(tokens = it.tokens - h) else it }) to Unit }
        return ok(SrvOk.serializer(), SrvOk())
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

