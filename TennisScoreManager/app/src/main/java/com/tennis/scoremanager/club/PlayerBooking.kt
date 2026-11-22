// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later
package com.tennis.scoremanager.club

import android.app.Application
import com.tennis.scoremanager.data.Storage
import com.tennis.scoremanager.ui.Strings
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Prenota un campo, dalla parte del giocatore: i circoli in cui è entrato (col server del circolo), le prenotazioni,
 * gli annunci di Cerco compagno, le chat e il controllo in background con le notifiche. La segreteria (planning,
 * server abbinato) resta in MatchController; qui arrivano solo le poche cose che servono, come funzioni.
 */
class PlayerBooking(
    private val app: Application,
    private val storage: Storage,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val stringsOf: () -> Strings,
    private val localeOf: () -> Locale,
    /** Il server all'indirizzo dato risponde? */
    private val serverAnswers: suspend (String) -> Boolean,
    /** La casella dei file (convocazioni, schede corso) mandati al giocatore da quel circolo. */
    private val fetchMail: suspend (com.tennis.scoremanager.data.BookingAccount) -> Unit,
    /** "sab 8 nov · 17:00": il giorno e l'ora di una prenotazione. */
    private val bookingLabel: (com.tennis.scoremanager.data.CourtBooking) -> String,
    /** Apre la schermata Prenota un campo. */
    private val openBookCourt: () -> Unit,
) {
    private val strings: Strings get() = stringsOf()
    private val locale: Locale get() = localeOf()

    /** I circoli in cui il giocatore è entrato (anche più d'uno); [bookingAccount] è quello che si sta guardando. */
    val bookingAccounts = MutableStateFlow(storage.loadBookingAccounts())
    val bookingAccount = MutableStateFlow(storage.loadBookingAccount().takeIf { it.joined } ?: bookingAccounts.value.firstOrNull() ?: com.tennis.scoremanager.data.BookingAccount())

    /**
     * Salva il circolo [a] (riconosciuto dall'indirizzo [url]): aggiornato al suo posto, o in fondo se è nuovo.
     * Non più collegato (uscito, accesso tolto) = tolto dall'elenco; se era quello guardato, si passa al primo che resta.
     */
    private fun saveBookingAccount(a: com.tennis.scoremanager.data.BookingAccount, url: String = bookingAccount.value.url) {
        val list = bookingAccounts.value
        val next = when {
            !a.joined -> list.filter { it.url != url }
            list.any { it.url == url } -> list.map { if (it.url == url) a else it }
            else -> list + a
        }
        bookingAccounts.value = next
        val cur = bookingAccount.value
        bookingAccount.value = when {
            cur.url == url -> if (a.joined) a else next.firstOrNull() ?: com.tennis.scoremanager.data.BookingAccount()
            !cur.joined -> a.takeIf { it.joined } ?: cur
            else -> cur
        }
        val shown = bookingAccount.value
        scope.launch(io) { storage.saveBookingAccounts(next); storage.saveBookingAccount(shown) }
    }

    /** Il server di ciascun circolo risponde? (indirizzo → sì o no; manca = non ancora controllato). */
    val bookingReach = MutableStateFlow<Map<String, Boolean>>(emptyMap())

    private fun markReach(url: String, ok: Boolean) {
        if (bookingReach.value[url] != ok) bookingReach.value = bookingReach.value + (url to ok)
    }

    /** Si chiede a ogni server dei propri circoli se risponde (le funzioni online si accendono e spengono da sole). */
    suspend fun checkBookingServers() {
        for (a in bookingAccounts.value) markReach(a.url, serverAnswers(a.url))
    }

    /** Si guarda un altro dei propri circoli. */
    fun selectBooking(url: String) {
        val a = bookingAccounts.value.firstOrNull { it.url == url } ?: return
        bookingAccount.value = a
        scope.launch(io) { storage.saveBookingAccount(a) }
    }

    /**
     * Entra nel circolo con l'invito: il link o il QR (con dentro l'indirizzo), oppure il codice e [url].
     * null = fatto, altrimenti il codice dell'errore.
     */
    suspend fun joinBooking(invite: String, url: String): String? {
        val (u0, code) = com.tennis.scoremanager.data.ClubServers.parseInvite(invite) ?: return "BAD_CODE"
        val u = u0 ?: com.tennis.scoremanager.data.ClubServers.cleanUrl(url).ifEmpty { return "NEED_URL" }
        return try {
            val (t, me) = withContext(Dispatchers.IO) {
                val t = com.tennis.scoremanager.data.ClubServerClient(u, null).claim(code)
                t to com.tennis.scoremanager.data.ClubServerClient(u, t.token).me()
            }
            addBookingAccount(com.tennis.scoremanager.data.BookingAccount(u, t.token, t.name, t.player, me.club))
            null
        } catch (e: com.tennis.scoremanager.data.ClubServerError) { e.code }
    }

    /** Un circolo nuovo (o lo stesso, rientrando): si aggiunge all'elenco e si guarda subito. */
    private fun addBookingAccount(a: com.tennis.scoremanager.data.BookingAccount) {
        val old = bookingAccounts.value.firstOrNull { it.url == a.url }
        // Rientrando nello stesso circolo restano le impostazioni (promemoria) e le notizie già viste.
        saveBookingAccount(old?.copy(token = a.token, name = a.name, player = a.player, club = a.club) ?: a, a.url)
        selectBooking(a.url)
    }

    /**
     * Rientra con numero e PIN: [club] è l'indirizzo del circolo o il vecchio link d'invito (da lì si prende
     * l'indirizzo). null = fatto, altrimenti il codice dell'errore.
     */
    suspend fun loginBooking(club: String, phone: String, pin: String): String? {
        val u = com.tennis.scoremanager.data.ClubServers.cleanUrl(club.substringBefore("#").substringBefore("?").trimEnd('/')).ifEmpty { return "NEED_URL" }
        return try {
            val (t, me) = withContext(Dispatchers.IO) {
                val t = com.tennis.scoremanager.data.ClubServerClient(u, null).login(phone, pin)
                t to com.tennis.scoremanager.data.ClubServerClient(u, t.token).me()
            }
            addBookingAccount(com.tennis.scoremanager.data.BookingAccount(u, t.token, t.name, t.player, me.club))
            null
        } catch (e: com.tennis.scoremanager.data.ClubServerError) { e.code }
    }

    /** Numero e PIN per rientrare da soli in questo circolo. null = fatto, altrimenti il codice dell'errore. */
    suspend fun setBookingPin(phone: String, pin: String): String? = booking { it.setPin(phone, pin) }.exceptionOrNull()?.let { (it as? com.tennis.scoremanager.data.ClubServerError)?.code ?: "NET" }

    /**
     * Una chiamata del giocatore al server del suo circolo. Se il server non riconosce più la chiave (accesso
     * tolto dalla segreteria) si esce dal circolo: serve un nuovo invito.
     */
    suspend fun <T> booking(f: (com.tennis.scoremanager.data.ClubServerClient) -> T): Result<T> = bookingWith(bookingAccount.value, f)

    /** Come [booking], per un circolo qualsiasi dei propri (il controllo in background li guarda tutti). */
    internal suspend fun <T> bookingWith(a: com.tennis.scoremanager.data.BookingAccount, f: (com.tennis.scoremanager.data.ClubServerClient) -> T): Result<T> {
        if (!a.joined) return Result.failure(com.tennis.scoremanager.data.ClubServerError("UNAUTHORIZED"))
        return try {
            Result.success(withContext(Dispatchers.IO) { f(com.tennis.scoremanager.data.ClubServerClient(a.url, a.token)) }).also { markReach(a.url, true) }
        } catch (e: com.tennis.scoremanager.data.ClubServerError) {
            // Non risponde (rete): le sue funzioni si spengono finché non torna; ogni altro errore vuol dire che risponde.
            markReach(a.url, e.code != "NET" && e.code != "BAD_URL")
            if (e.code == "UNAUTHORIZED") saveBookingAccount(com.tennis.scoremanager.data.BookingAccount(), a.url)
            Result.failure(e)
        }
    }

    init {
        // Promemoria e avvisi del giocatore anche ad app chiusa, solo se è entrato in un circolo e li vuole.
        scope.launch {
            bookingAccounts.map { l -> l.any { it.joined && it.reminders } }.distinctUntilChanged().collect { com.tennis.scoremanager.service.ServerCheckJob.schedulePlayer(app, it) }
        }
        // Con l'app aperta, ogni tre minuti: i server dei propri circoli rispondono?
        scope.launch {
            while (true) {
                if (bookingAccounts.value.isNotEmpty()) checkBookingServers()
                delay(180_000)
            }
        }
    }

    fun setBookingReminders(on: Boolean) = saveBookingAccount(bookingAccount.value.copy(reminders = on))

    /** Notifiche dei nuovi annunci di Cerco compagno in questo circolo (gli annunci restano nelle novità e in Compagni). */
    fun setSeekAlerts(on: Boolean) = saveBookingAccount(bookingAccount.value.copy(seekAlerts = on))

    /** Disdetta da qui: non è una notizia da dare al giocatore. */
    fun noteMyCancel(id: String) = saveBookingAccount(bookingAccount.value.copy(cancelledHere = (bookingAccount.value.cancelledHere + id).distinct()))

    /** Il controllo del giocatore (in background), circolo per circolo: promemoria della partita e prenotazioni tolte dalla segreteria. */
    suspend fun checkMyBookings() {
        val many = bookingAccounts.value.size > 1
        for (a0 in bookingAccounts.value) checkMyBookings(a0, many)
    }

    /** [many]: si è in più circoli, e negli avvisi si dice di quale. */
    private suspend fun checkMyBookings(a: com.tennis.scoremanager.data.BookingAccount, many: Boolean) {
        if (!a.joined) return
        val me = bookingWith(a) { it.me() }.getOrNull() ?: return
        // La casella dei file (convocazioni, schede corso) da questo circolo.
        runCatching { fetchMail(a) }
        val now = System.currentTimeMillis()
        val club = if (many) me.club.ifBlank { a.club }.ifBlank { null } else null
        fun label(court: String, start: Long) = listOfNotNull(club, bookingLabel(com.tennis.scoremanager.data.CourtBooking("", court, start))).joinToString(" · ")
        val news = com.tennis.scoremanager.data.ClubServers.myNews(a, me.bookings, me.rules.remindHours, now) { b -> label(b.court, b.start) }
        val (freed, acc) = com.tennis.scoremanager.data.ClubServers.freedNews(news.account, me.waits)
        val seeks = bookingWith(a) { it.seeks() }.getOrNull()
        val sk = seeks?.let { com.tennis.scoremanager.data.ClubServers.seekNews(acc, it) }
        // Gli annunci nuovi degli altri: nelle novità di Gioca e, se si vogliono gli avvisi, una notifica.
        val (acc2, freshSeeks) = seeks?.let { com.tennis.scoremanager.data.ClubServers.noteSeeks(sk?.account ?: acc, it) } ?: ((sk?.account ?: acc) to emptyList())
        saveBookingAccount(acc2, a.url)
        // Gli annunci che non sono più aperti (al completo, tolti): via le loro notifiche, che altrimenti confondono.
        if (seeks != null) closedSeeks(a, acc2).forEach { com.tennis.scoremanager.service.OnlineAlerts.cancelSeek(app, it) }
        // Le chat: bacheca e annunci in cui si è, con i messaggi nuovi.
        saveBookingAccount(checkChats(acc2, me, seeks, club), a.url)
        if (a.reminders) {
            com.tennis.scoremanager.service.OnlineAlerts.postPlayer(
                app, strings, news.remind.map { it.label }, news.removed.map { it.label },
                freed.map { w -> label(w.freedCourt!!, w.start) },
            )
            if (sk != null) com.tennis.scoremanager.service.OnlineAlerts.postSeeks(
                app, strings,
                sk.joined.map { it.players.last() + " · " + it.date },
                sk.booked.map { label(it.bookedCourt!!, it.bookedStart!!) + " · " + it.players.joinToString(", ") },
                sk.noCourt.map { it.date + " · " + it.players.joinToString(", ") },
            )
            if (a.seekAlerts) for (k in freshSeeks) com.tennis.scoremanager.service.OnlineAlerts.postNewSeek(app, strings, k.id, a.url, seekLine(k, club))
        }
    }

    /** Un annuncio in una riga: "Mario · sab 8 nov 17:00–20:00 · singolo · ne manca 1" (col circolo davanti se serve). */
    fun seekLine(k: com.tennis.scoremanager.data.SrvSeek, club: String? = null): String {
        val s = strings
        fun hm(m: Int) = "%02d:%02d".format(m / 60, m % 60)
        val day = runCatching {
            java.time.format.DateTimeFormatter.ofPattern("EEE d MMM", locale).format(java.time.LocalDate.parse(k.date))
        }.getOrDefault(k.date)
        return listOfNotNull(club, k.owner, "$day ${hm(k.fromMin)}–${hm(k.toMin)}", if (k.doubles) s.pbSeekDoubles else s.pbSeekSingles, k.level.ifBlank { null }, s.pbSeekNeed(k.need)).joinToString(" · ")
    }

    /** Gli annunci letti dal telefono (dalla schermata): si aggiornano le novità senza notifica. */
    fun noteSeeks(seeks: List<com.tennis.scoremanager.data.SrvSeek>) {
        val a = bookingAccount.value
        if (!a.joined) return
        val next = com.tennis.scoremanager.data.ClubServers.noteSeeks(a, seeks).first
        saveBookingAccount(next, a.url)
        closedSeeks(a, next).forEach { com.tennis.scoremanager.service.OnlineAlerts.cancelSeek(app, it) }
    }

    /** Gli annunci che c'erano in [before] e non sono più aperti in [after]. */
    private fun closedSeeks(before: com.tennis.scoremanager.data.BookingAccount, after: com.tennis.scoremanager.data.BookingAccount): List<String> {
        val open = after.openSeeks.map { it.id }.toSet()
        return before.openSeeks.map { it.id }.filter { it !in open }
    }

    /** Annunci letti in [url] ([ids] null = tutti quelli aperti): spariscono dalle novità. */
    fun readSeeks(url: String, ids: List<String>? = null) {
        val a = bookingAccounts.value.firstOrNull { it.url == url } ?: return
        val add = ids ?: a.openSeeks.map { it.id }
        // Letti: anche le loro notifiche non servono più.
        add.forEach { com.tennis.scoremanager.service.OnlineAlerts.cancelSeek(app, it) }
        if (add.all { it in a.seekRead }) return
        saveBookingAccount(a.copy(seekRead = (a.seekRead + add).distinct()), url)
    }

    /** La scheda di Prenota un campo da aprire (2 = Compagni), chiesta dalle novità. */
    val bookTabRequest = MutableStateFlow<Int?>(null)
    /** Da Gioca o da una notifica: Prenota un campo apre subito questa chat ("board", "club", "d-…"). */
    val chatRequest = MutableStateFlow<String?>(null)

    /** Aperto un annuncio dalle novità: si va in Compagni di quel circolo e l'annuncio è letto. */
    fun openSeekNews(url: String, id: String) {
        readSeeks(url, listOf(id))
        selectBooking(url)
        bookTabRequest.value = 2
        openBookCourt()
    }

    // ---- Chat del circolo (giocatore e segreteria) ----

    /** Letta la chat [room] del circolo [url] fino ad [at]: sparisce dalle novità. */
    fun markChatRead(url: String, room: String, at: Long) {
        val a = bookingAccounts.value.firstOrNull { it.url == url } ?: return
        com.tennis.scoremanager.service.OnlineAlerts.cancelChat(app, room)
        if ((a.chatRead[room] ?: 0) >= at && room !in a.chatNews) return
        saveBookingAccount(a.copy(chatRead = a.chatRead + (room to maxOf(at, a.chatRead[room] ?: 0)), chatNews = a.chatNews - room, boardUnread = if (room == "board") 0 else a.boardUnread,
            clubUnread = if (room == "club") 0 else a.clubUnread), url)
    }

    /**
     * Le chat con messaggi nuovi, nel controllo in background: la bacheca (se la segreteria ha scritto) e gli
     * annunci in cui si è. Si avvisa una volta per messaggio; l'account torna con le novità aggiornate.
     */
    private suspend fun checkChats(a: com.tennis.scoremanager.data.BookingAccount, me: com.tennis.scoremanager.data.SrvMe, seeks: List<com.tennis.scoremanager.data.SrvSeek>?, club: String?): com.tennis.scoremanager.data.BookingAccount {
        val chats = bookingWith(a) { it.myChats() }.getOrNull()
        val rooms = buildList {
            if (me.boardLast > 0) add(Triple("board", "", me.boardLast))
            seeks?.filter { (it.mine || it.joined) && it.chatLast > 0 }?.forEach { add(Triple("s-" + it.id, seekLine(it), it.chatLast)) }
            // La chat del circolo (se se ne vogliono le notifiche) e le private.
            chats?.filter { it.last > 0 && (it.room != "club" || me.clubChat) }?.forEach {
                add(Triple(it.room, if (it.room == "club") strings.pbClubChat else it.title, it.last))
            }
        }
        var acc = a
        for ((room, title, last) in rooms) {
            if (last <= (acc.chatRead[room] ?: 0)) continue
            val since = maxOf(acc.chatRead[room] ?: 0, acc.chatNotified[room] ?: 0)
            if (last > since) {
                val fresh = bookingWith(a) { it.chat(room, since) }.getOrNull().orEmpty().filter { !it.mine }
                if (fresh.isNotEmpty() && a.reminders) {
                    val m = fresh.last()
                    com.tennis.scoremanager.service.OnlineAlerts.postChat(app, strings, room, a.url,
                        when {
                            room == "board" -> listOfNotNull(club, strings.pbBoardTitle).joinToString(" · ")
                            room == "club" -> listOfNotNull(club, strings.pbClubChat).joinToString(" · ")
                            room.startsWith("d-") -> strings.pbDmNew
                            else -> strings.pbChatNew
                        },
                        "${m.name}: ${m.text}")
                }
                acc = acc.copy(chatNotified = acc.chatNotified + (room to last))
            }
            // La bacheca ha la sua striscia in cima a Gioca, col numero degli avvisi da leggere.
            if (room == "board") {
                val n = bookingWith(a) { it.chat(room, acc.chatRead[room] ?: 0) }.getOrNull()?.count { !it.mine }
                if (n != null) acc = acc.copy(boardUnread = n)
            } else acc = acc.copy(chatNews = acc.chatNews + (room to title))
        }
        // La chat del circolo in Gioca (anche con le notifiche spente): l'ultimo messaggio e quanti da leggere.
        val clubRoom = chats?.firstOrNull { it.room == "club" }
        if (clubRoom != null && clubRoom.last > acc.clubPreviewAt) {
            val read = acc.chatRead["club"] ?: 0
            bookingWith(a) { it.chat("club", 0) }.getOrNull()?.let { l ->
                acc = acc.copy(clubPreview = l.lastOrNull()?.let { "${it.name}: ${it.text}" }.orEmpty().take(140),
                    clubPreviewAt = clubRoom.last, clubUnread = l.count { !it.mine && it.at > read })
            }
        }
        return acc
    }

    private var newsCheckedAt = 0L
    /** Aprendo Gioca: le novità dei circoli (chat, bacheca, annunci) subito, al massimo una volta al minuto. */
    fun refreshClubNews() {
        val now = System.currentTimeMillis()
        if (bookingAccounts.value.isEmpty() || now - newsCheckedAt < 60_000) return
        newsCheckedAt = now
        scope.launch { checkMyBookings() }
    }

    /** Esce dal circolo su questo telefono (per rientrare serve un nuovo invito). */
    fun leaveBooking() {
        val a = bookingAccount.value
        scope.launch {
            if (a.joined) runCatching { withContext(Dispatchers.IO) { com.tennis.scoremanager.data.ClubServerClient(a.url, a.token).logout() } }
        }
        saveBookingAccount(com.tennis.scoremanager.data.BookingAccount())
    }
}
