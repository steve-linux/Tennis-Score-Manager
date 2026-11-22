// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later
package com.tennis.scoremanager.club

import android.app.Application
import com.tennis.scoremanager.data.Storage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Il server del circolo (facoltativo), dalla parte della segreteria: abbinamento, sincronizzazione del planning,
 * avvisi delle prenotazioni online, regole, inviti, tunnel, PIN dei giocatori, chat della segreteria.
 * Quello che serve del resto dell'app è in [ClubHost].
 */
class ClubServerArea(
    host: ClubHost,
    private val app: Application,
    private val storage: Storage,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
) : ClubHost by host {
    /** Il server del circolo per le prenotazioni dei giocatori; non abbinato = si prenota solo in segreteria. */
    override val clubServer = MutableStateFlow(storage.loadClubServer())
    internal val serverLock = kotlinx.coroutines.sync.Mutex()

    override fun saveServer(f: (com.tennis.scoremanager.data.ClubServerConf) -> com.tennis.scoremanager.data.ClubServerConf) {
        val n = synchronized(clubServer) { f(clubServer.value).also { clubServer.value = it } }
        scope.launch(io) { storage.saveClubServer(n) }
    }

    init {
        // Il controllo in background (notifiche delle prenotazioni online) solo con un server abbinato e gli avvisi accesi.
        scope.launch {
            clubServer.map { it.paired && it.alerts }.distinctUntilChanged().collect { com.tennis.scoremanager.service.ServerCheckJob.schedule(app, it) }
        }
        // Ogni cambio del planning, dei tornei, dei campi o delle regole va al server (dopo qualche secondo di calma).
        scope.launch {
            combine(courtPlan, competitions.clubTournaments, planning.clubCourts, clubServer.map { Triple(it.paired, it.rules, it.payInfo) }.distinctUntilChanged()) { _, _, _, _ -> Unit }
                .collectLatest { delay(3_000); if (clubServer.value.paired) syncServerNow() }
        }
    }

    /** Abbina la segreteria al server con il codice che il server ha scritto all'avvio. null = fatto, altrimenti il codice dell'errore. */
    suspend fun pairServer(url: String, code: String): String? {
        val u = com.tennis.scoremanager.data.ClubServers.cleanUrl(url)
        if (u.isEmpty()) return "BAD_URL"
        return try {
            val token = withContext(Dispatchers.IO) {
                com.tennis.scoremanager.data.ClubServerClient(u, null).info()
                com.tennis.scoremanager.data.ClubServerClient(u, null).pair(code).token
            }
            saveServer { it.copy(url = u, token = token, error = null, lastSync = 0, players = emptyList(), cancel = emptyList(), seen = null) }
            syncServerNow()
        } catch (e: com.tennis.scoremanager.data.ClubServerError) { e.code }
    }

    /**
     * Manda il planning al server e prende le prenotazioni online: le nuove entrano nel planning, quelle disdette
     * escono. Prima toglie dal server quelle tolte in segreteria. null = fatto, altrimenti il codice dell'errore.
     */
    suspend fun syncServerNow(): String? = serverLock.withLock {
        val conf = clubServer.value
        if (!conf.paired) return@withLock null
        val srv = com.tennis.scoremanager.data.ClubServers
        try {
            for (id in conf.cancel) {
                withContext(Dispatchers.IO) { try { srv.withServer(conf) { it.cancel(id) } } catch (e: com.tennis.scoremanager.data.ClubServerError) { if (e.code != "NOT_FOUND") throw e } }
                saveServer { it.copy(cancel = it.cancel - id) }
            }
            val sync = srv.buildSync(conf, courtPlan.value, competitions.clubTournaments.value, planning.clubCourts.value, storage.clubName, java.util.TimeZone.getDefault().id)
            val st = withContext(Dispatchers.IO) { srv.withServer(conf) { it.sync(sync) } }
            // La casella: risultati dal campo, presenze e proposte degli allenatori.
            runCatching { fetchOfficeMail(conf) }
            val (added, gone) = com.tennis.scoremanager.data.ClubServers.merge(courtPlan.value, st.bookings, { playerById(it) != null }, System.currentTimeMillis())
            gone.forEach { planning.deleteBooking(it, wholeSeries = false, fromServer = true) }
            if (added.isNotEmpty()) planning.putBookings(added)
            // Le novità per la segreteria: chi ha prenotato o disdetto online da quando si è guardato l'ultima volta.
            val news = com.tennis.scoremanager.data.ClubServers.onlineNews(clubServer.value.seen, st.bookings, System.currentTimeMillis(), conf.cancel.toSet()) { b ->
                listOf(b.name, b.court, planning.bookingLabel(com.tennis.scoremanager.data.CourtBooking("", b.court, b.start)).substringAfter(" · ")).joinToString(" · ")
            }
            if (clubServer.value.alerts) com.tennis.scoremanager.service.OnlineAlerts.post(app, strings, news.added, news.cancelled, news.firstDay)
            saveServer {
                it.copy(
                    lastSync = System.currentTimeMillis(), error = null, players = st.players, club = sync.club, seen = news.seen,
                    inbox = com.tennis.scoremanager.data.ClubServers.addNotices(it.inbox, news.notices),
                )
            }
            null
        } catch (e: com.tennis.scoremanager.data.ClubServerError) {
            saveServer { it.copy(error = e.code) }
            e.code
        }
    }

    fun setServerAlerts(on: Boolean) = saveServer { it.copy(alerts = on) }

    /** Un messaggio di Oggi letto (null = tutti): sparisce. */
    fun readOnlineNotice(id: String?) = saveServer { conf -> conf.copy(inbox = if (id == null) emptyList() else conf.inbox.filter { it.id != id }) }

    /** Aperto un messaggio di Oggi: sparisce e si va al planning di quel giorno. */
    fun openOnlineNotice(n: com.tennis.scoremanager.data.OnlineNotice) {
        readOnlineNotice(n.id)
        openPlanAt(com.tennis.scoremanager.data.CourtPlans.dayStart(n.start))
    }

    /** Il giorno su cui aprire il planning (dalla notifica di una prenotazione online). */
    val planDayRequest = MutableStateFlow<Long?>(null)

    fun openPlanAt(day: Long) {
        planDayRequest.value = day
        planning.openCourtsPlan()
    }

    fun saveServerRules(rules: com.tennis.scoremanager.data.BookingRules, payInfo: String, publicUrl: String = clubServer.value.publicUrl) =
        saveServer { it.copy(rules = rules, payInfo = payInfo.trim(), publicUrl = com.tennis.scoremanager.data.ClubServers.cleanUrl(publicUrl)) }

    /** L'invito per un giocatore: codice e scadenza (il link lo fa [com.tennis.scoremanager.data.ClubServers.inviteLink]). */
    suspend fun serverInvite(p: com.tennis.scoremanager.data.PlayerProfile): Result<com.tennis.scoremanager.data.SrvInvite> {
        val conf = clubServer.value
        if (!conf.paired) return Result.failure(com.tennis.scoremanager.data.ClubServerError("NOT_PAIRED"))
        return try {
            Result.success(withContext(Dispatchers.IO) { com.tennis.scoremanager.data.ClubServers.withServer(conf) { it.invite(p.id, p.name) } })
        } catch (e: com.tennis.scoremanager.data.ClubServerError) { Result.failure(e) }
    }

    /** Il server risponde a [url] (la rete del circolo o l'indirizzo da internet)? Per la lista dei controlli. */
    suspend fun serverAnswers(url: String): Boolean = url.isNotBlank() && withContext(Dispatchers.IO) {
        runCatching { com.tennis.scoremanager.data.ClubServerClient(com.tennis.scoremanager.data.ClubServers.cleanUrl(url), null).info() }.isSuccess
    }

    /** Il tunnel da internet del server: com'è messo e, con [on], accendilo o spegnilo. */
    suspend fun serverTunnel(on: Boolean? = null): Result<com.tennis.scoremanager.data.SrvTunnel> {
        val conf = clubServer.value
        if (!conf.paired) return Result.failure(com.tennis.scoremanager.data.ClubServerError("NOT_PAIRED"))
        return try {
            Result.success(withContext(Dispatchers.IO) { com.tennis.scoremanager.data.ClubServers.withServer(conf) { if (on == null) it.tunnel() else it.setTunnel(on) } })
        } catch (e: com.tennis.scoremanager.data.ClubServerError) { Result.failure(e) }
    }

    /** Il giocatore non prenota più online (le sue prenotazioni restano nel planning). */
    suspend fun serverRemovePlayer(id: String): String? {
        val conf = clubServer.value
        return try {
            withContext(Dispatchers.IO) { com.tennis.scoremanager.data.ClubServers.withServer(conf) { it.removePlayer(id) } }
            saveServer { it.copy(players = it.players.filter { p -> p.id != id }) }
            null
        } catch (e: com.tennis.scoremanager.data.ClubServerError) { e.code }
    }

    /** Il socio ha dimenticato il PIN: lo si azzera (rientra con un invito e se ne sceglie un altro). null = fatto. */
    suspend fun serverResetPin(id: String): String? {
        val conf = clubServer.value
        return try {
            withContext(Dispatchers.IO) { com.tennis.scoremanager.data.ClubServers.withServer(conf) { it.resetPin(id) } }
            saveServer { it.copy(players = it.players.map { p -> if (p.id == id) p.copy(hasPin = false) else p }) }
            null
        } catch (e: com.tennis.scoremanager.data.ClubServerError) { e.code }
    }

    /** Scollega il server: le prenotazioni online già nel planning restano, come quelle della segreteria. */
    fun unpairServer() {
        saveServer { com.tennis.scoremanager.data.ClubServerConf(rules = it.rules, payInfo = it.payInfo, publicUrl = it.publicUrl, alerts = it.alerts) }
        val p = courtPlan.value
        if (p.bookings.any { it.online != null }) {
            val np = p.copy(bookings = p.bookings.map { it.copy(online = null) })
            courtPlan.value = np
            scope.launch(io) { storage.saveCourtPlan(np) }
        }
    }

    /** La segreteria: i messaggi di una stanza, la bacheca, le stanze, togliere un messaggio. Codice d'errore o il risultato. */
    suspend fun <T> serverAdmin(f: (com.tennis.scoremanager.data.ClubServerClient) -> T): Result<T> {
        val conf = clubServer.value
        if (!conf.paired) return Result.failure(com.tennis.scoremanager.data.ClubServerError("NOT_PAIRED"))
        return try {
            Result.success(withContext(Dispatchers.IO) { com.tennis.scoremanager.data.ClubServers.withServer(conf) { f(it) } })
        } catch (e: com.tennis.scoremanager.data.ClubServerError) { Result.failure(e) }
    }
}
