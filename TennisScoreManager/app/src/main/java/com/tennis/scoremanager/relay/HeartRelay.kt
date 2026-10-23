// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.relay

import android.os.Build
import com.tennis.scoremanager.ble.HeartMeasurement
import com.tennis.scoremanager.ble.HeartRateManager
import com.tennis.scoremanager.ble.StrapInfo
import com.tennis.scoremanager.ble.StrapState
import com.tennis.scoremanager.model.Side
import com.tennis.scoremanager.tv.TvServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.security.SecureRandom

enum class RelayRole { OFF, MAIN, SATELLITE }

/**
 * Stato del collegamento tra i telefoni, per le schermate. Principale: [url] da mostrare nel QR e [satellites]
 * collegati. Satellite: [connected] al principale e [pending] misure in attesa di conferma.
 */
data class RelayUi(
    val role: RelayRole = RelayRole.OFF,
    val url: String? = null,
    val satellites: List<String> = emptyList(),
    val connected: Boolean = false,
    val pending: Int = 0,
)

/** Come un satellite vede una fascia, con l'ora (del principale) in cui l'ha detto. */
data class RemoteStrap(val status: RelayStrapStatus, val satellite: String, val at: Long)

/**
 * Due telefoni per le fasce cardio, uno per fondo campo. Il principale tiene la partita e le fasce come sempre;
 * il satellite si collega alle stesse fasce e gli gira le misure. Al cambio campo non serve fare niente: ogni fascia
 * resta collegata a chi la sente finché il segnale regge, poi la prende il telefono più vicino (le fasce che
 * accettano più collegamenti restano su tutti e due, e il principale usa le misure di entrambi).
 *
 * Ogni misura del satellite porta l'ID della partita del principale e un numero progressivo: il satellite la
 * tiene finché il principale non conferma, così un'interruzione del Wi-Fi non perde niente (le misure arrivano
 * dopo, con la loro ora, e riempiono i buchi del grafico).
 */
class HeartRelay(
    private val scope: CoroutineScope,
    private val heart: HeartRateManager,
    /** ID della partita in corso sul principale ("" = nessuna). */
    matchIds: Flow<String>,
    /** Misura arrivata da un satellite: indirizzo della fascia, misura, ora (orologio di questo telefono), partita. */
    private val onRemoteReading: (addr: String, m: HeartMeasurement, at: Long, match: String) -> Unit,
    /** Il satellite smette: rimette le fasce di questo telefono. */
    private val onSatelliteStopped: () -> Unit,
) {
    private val net = Dispatchers.IO.limitedParallelism(1)

    private val _ui = MutableStateFlow(RelayUi())
    val ui: StateFlow<RelayUi> = _ui
    private val _remote = MutableStateFlow<Map<String, RemoteStrap>>(emptyMap())
    /** Principale: come i satelliti vedono le fasce, per indirizzo (il migliore se sono più d'uno). */
    val remote: StateFlow<Map<String, RemoteStrap>> = _remote

    private var currentMatch = ""

    // ---------------------------------------------------------------- principale

    private var hub: RelayHub? = null
    private val inbox = RelayInbox()
    /** Satelliti collegati: id della connessione -> (id del satellite, nome del telefono). */
    private val sats = mutableMapOf<Int, Pair<String, String>>()
    private var lastStraps: List<RelayStrap> = emptyList()

    init {
        scope.launch {
            matchIds.distinctUntilChanged().collect { id ->
                currentMatch = id
                if (_ui.value.role == RelayRole.MAIN) broadcast(RelayMsg(RelayMsg.MATCH, match = id))
            }
        }
        scope.launch {
            heart.straps.map { m -> m.map { (side, st) -> RelayStrap(st.address, st.name, side.ordinal) }.sortedBy { it.side } }
                .distinctUntilChanged()
                .collect { list ->
                    lastStraps = list
                    if (_ui.value.role == RelayRole.MAIN) broadcast(RelayMsg(RelayMsg.STRAPS, straps = list))
                }
        }
        scope.launch {
            while (true) {
                delay(2_000)
                when (_ui.value.role) {
                    RelayRole.MAIN -> dropStaleRemote()
                    RelayRole.SATELLITE -> satelliteTick()
                    RelayRole.OFF -> Unit
                }
            }
        }
    }

    /** Apre il server e prepara il QR. false = nessuna rete locale (Wi-Fi o hotspot) a cui il satellite possa collegarsi. */
    fun startMain(): Boolean {
        if (_ui.value.role == RelayRole.MAIN && hub != null) return true
        stopSatellite()
        val host = TvServer.localAddresses().firstOrNull() ?: return false
        val h = RelayHub(
            onMessage = { id, conn, m -> onHubMessage(id, conn, m) },
            onClosed = { id -> scope.launch { satelliteGone(id) } },
        )
        val port = runCatching { h.start() }.getOrNull() ?: return false
        hub = h
        _ui.value = RelayUi(RelayRole.MAIN, url = RelayCodec.url(host, port, h.token))
        return true
    }

    fun stopMain() {
        hub?.let { h -> scope.launch(net) { h.stop() } }
        hub = null
        sats.clear()
        _remote.value = emptyMap()
        if (_ui.value.role == RelayRole.MAIN) _ui.value = RelayUi()
    }

    /** Dal thread di rete: il ping si risponde subito (l'ora conta), il resto passa al thread principale. */
    private fun onHubMessage(id: Int, conn: RelayConnection, m: RelayMsg) {
        if (m.t == RelayMsg.PING) {
            conn.send(RelayMsg(RelayMsg.PONG, c = m.c, m = System.currentTimeMillis()))
            return
        }
        scope.launch {
            when (m.t) {
                RelayMsg.HELLO -> {
                    sats[id] = m.sat!! to (m.name ?: "")
                    publishSatellites()
                    send(id, RelayMsg(RelayMsg.WELCOME, match = currentMatch, straps = lastStraps))
                }
                RelayMsg.HR -> {
                    val sat = sats[id]?.first ?: return@launch
                    val seq = m.seq ?: return@launch
                    val addr = m.addr
                    val bpm = m.bpm
                    if (inbox.accept(sat, seq) && addr != null && bpm != null && m.at != null) {
                        onRemoteReading(addr, HeartMeasurement(bpm, m.contact), m.at, m.match.orEmpty())
                    }
                    send(id, RelayMsg(RelayMsg.ACK, seq = inbox.last(sat)))
                }
                RelayMsg.STATUS -> {
                    val name = sats[id]?.second ?: return@launch
                    val now = System.currentTimeMillis()
                    var map = _remote.value
                    for (st in m.status.orEmpty()) {
                        val old = map[st.addr]
                        // Con due satelliti sulla stessa fascia vince chi la sente meglio.
                        val better = old == null || old.satellite == name || now - old.at > STALE_MS ||
                            st.ready && (!old.status.ready || (st.rssi ?: -127) > (old.status.rssi ?: -127))
                        if (better) map = map + (st.addr to RemoteStrap(st, name, now))
                    }
                    _remote.value = map
                }
            }
        }
    }

    private fun satelliteGone(id: Int) {
        val name = sats.remove(id)?.second ?: return
        _remote.value = _remote.value.filterValues { it.satellite != name }
        publishSatellites()
    }

    private fun publishSatellites() {
        if (_ui.value.role == RelayRole.MAIN) _ui.value = _ui.value.copy(satellites = sats.values.map { it.second })
    }

    private fun dropStaleRemote() {
        val now = System.currentTimeMillis()
        val fresh = _remote.value.filterValues { now - it.at <= STALE_MS }
        if (fresh.size != _remote.value.size) _remote.value = fresh
    }

    private fun send(id: Int, m: RelayMsg) {
        val h = hub ?: return
        scope.launch(net) { h.send(id, m) }
    }

    private fun broadcast(m: RelayMsg) {
        val h = hub ?: return
        scope.launch(net) { h.broadcast(m) }
    }

    // ---------------------------------------------------------------- satellite

    private class Reading(val addr: String, val m: HeartMeasurement, val localAt: Long, val match: String)

    private var client: RelayClient? = null
    private val outbox = RelayOutbox<Reading>()
    private val clock = ClockSync()
    /** Ultimo numero già spedito su questa connessione: dopo una riconnessione si riparte da quelli non confermati. */
    private var sentUpTo = 0L
    private var satId = ""
    private var pingJob: Job? = null

    /** Testo del QR del principale: questo telefono diventa satellite. false se il QR non è quello giusto. */
    fun startSatellite(text: String): Boolean {
        val (host, port, token) = RelayCodec.parseUrl(text) ?: return false
        stopMain()
        stopSatellite()
        satId = ByteArray(8).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        heart.disconnectAll()
        val hello = RelayMsg(RelayMsg.HELLO, token = token, sat = satId, name = deviceName())
        val c = RelayClient(
            host, port, hello = { hello },
            onConnected = { scope.launch { onSatConnected() } },
            onMessage = { m -> onSatMessage(m) },
            onDisconnected = { scope.launch { _ui.value = _ui.value.copy(connected = false) } },
        )
        client = c
        _ui.value = RelayUi(RelayRole.SATELLITE)
        c.start()
        return true
    }

    fun stopSatellite() {
        if (_ui.value.role != RelayRole.SATELLITE) return
        val c = client
        scope.launch(net) { c?.stop() }
        client = null
        pingJob?.cancel()
        clock.clear()
        _ui.value = RelayUi()
        onSatelliteStopped()
    }

    private fun onSatConnected() {
        sentUpTo = outbox.pending().firstOrNull()?.first?.minus(1) ?: sentUpTo
        _ui.value = _ui.value.copy(connected = true)
        pingJob?.cancel()
        pingJob = scope.launch {
            while (true) {
                val now = System.currentTimeMillis()
                scope.launch(net) { client?.send(RelayMsg(RelayMsg.PING, c = now)) }
                delay(if (clock.offset == null) 1_000 else 5_000)
            }
        }
    }

    private fun onSatMessage(m: RelayMsg) {
        // L'ora d'arrivo del pong si prende qui, sul thread di rete, prima di qualunque attesa.
        val receivedAt = System.currentTimeMillis()
        scope.launch {
            when (m.t) {
                RelayMsg.WELCOME -> {
                    currentMatch = m.match.orEmpty()
                    follow(m.straps.orEmpty())
                }
                RelayMsg.STRAPS -> follow(m.straps.orEmpty())
                RelayMsg.MATCH -> currentMatch = m.match.orEmpty()
                RelayMsg.PONG -> if (m.c != null && m.m != null) {
                    clock.add(m.c, m.m, receivedAt)
                    flush()
                }
                RelayMsg.ACK -> m.seq?.let {
                    outbox.ack(it)
                    _ui.value = _ui.value.copy(pending = outbox.size)
                }
            }
        }
    }

    /** Il satellite segue le fasce del principale, sugli stessi lati. */
    private fun follow(list: List<RelayStrap>) {
        val want = list.mapNotNull { r -> Side.entries.getOrNull(r.side)?.let { it to r } }.toMap()
        val have = heart.straps.value
        for (side in Side.entries) {
            val r = want[side]
            if (r == null) {
                if (have[side] != null) heart.assign(side, null, null)
            } else if (have[side]?.address != r.addr) {
                heart.assign(side, r.addr, r.name)
            }
        }
    }

    /** Satellite: ogni misura delle fasce seguite va nella coda per il principale. */
    fun onLocalReading(side: Side, m: HeartMeasurement) {
        if (_ui.value.role != RelayRole.SATELLITE) return
        val addr = heart.straps.value[side]?.address ?: return
        outbox.add(Reading(addr, m, System.currentTimeMillis(), currentMatch))
        _ui.value = _ui.value.copy(pending = outbox.size)
        flush()
    }

    /** Spedisce le misure non ancora spedite su questa connessione (serve l'orologio allineato). */
    private fun flush() {
        val c = client ?: return
        val offset = clock.offset ?: return
        if (!_ui.value.connected) return
        val todo = outbox.pending().filter { it.first > sentUpTo }
        if (todo.isEmpty()) return
        sentUpTo = todo.last().first
        scope.launch(net) {
            for ((seq, r) in todo) {
                val msg = RelayMsg(
                    RelayMsg.HR, seq = seq, match = r.match, addr = r.addr, bpm = r.m.bpm, contact = r.m.contact,
                    at = r.localAt + offset,
                )
                if (!c.send(msg)) break  // caduta: alla riconnessione si rimanda da quelle non confermate
            }
        }
    }

    private fun satelliteTick() {
        val c = client ?: return
        val status = heart.straps.value.values.map { it.toStatus() }
        if (_ui.value.connected) scope.launch(net) { c.send(RelayMsg(RelayMsg.STATUS, status = status)) }
        flush()
    }

    private fun StrapInfo.toStatus() = RelayStrapStatus(address, state == StrapState.READY, bpm, rssi, received)

    private fun deviceName(): String = listOf(Build.MANUFACTURER, Build.MODEL).filter { it.isNotBlank() }.distinct().joinToString(" ")

    fun stop() {
        stopMain()
        stopSatellite()
    }

    companion object {
        /** Stato di una fascia vista dal satellite: dopo 6 s senza notizie non vale più. */
        const val STALE_MS = 6_000L
        /** Misure del satellite più vecchie di così non sono "dal vivo": vanno a riempire i buchi. */
        const val LIVE_MS = 8_000L
    }
}
