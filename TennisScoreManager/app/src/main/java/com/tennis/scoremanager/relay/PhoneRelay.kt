// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.relay

import android.content.Context
import android.os.Build
import android.util.Log
import com.tennis.scoremanager.ble.BandStatus
import com.tennis.scoremanager.ble.BleManager
import com.tennis.scoremanager.ble.HeartMeasurement
import com.tennis.scoremanager.ble.HeartRateManager
import com.tennis.scoremanager.ble.LinkState
import com.tennis.scoremanager.ble.RemoteBand
import com.tennis.scoremanager.ble.StrapInfo
import com.tennis.scoremanager.ble.StrapState
import com.tennis.scoremanager.model.Side
import com.tennis.scoremanager.tv.TvServer
import com.tennis.scoremanager.video.PointMark
import com.tennis.scoremanager.video.ScoreOverlay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import com.tennis.scoremanager.video.ReplayRecorder
import java.io.File
import java.net.Socket
import java.security.SecureRandom

enum class RelayRole { OFF, MAIN, SATELLITE }

/**
 * Stato del collegamento tra i telefoni, per le schermate. Principale: [url] da mostrare nel QR e [satellites]
 * collegati. Satellite: [connected] al principale e [pending] dati in attesa di conferma.
 */
data class RelayUi(
    val role: RelayRole = RelayRole.OFF,
    val url: String? = null,
    val satellites: List<String> = emptyList(),
    val connected: Boolean = false,
    val pending: Int = 0,
    /** Principale: rete creata da questo telefono (null = si usa il Wi-Fi in cui si trova). Satellite: rete del principale. */
    val hotspot: HotspotInfo? = null,
    /** Satellite: a che punto è il collegamento alla rete del principale (null = non serve). */
    val join: JoinState? = null,
    /** Satellite con Android 9 o prima: la rete del principale va scelta a mano nelle impostazioni Wi-Fi. */
    val joinByHand: Boolean = false,
    /** Satellite: indirizzo del tabellone del principale (host:porta), da mostrare a schermo intero. */
    val tv: String? = null,
    /** Satellite che fa da telecamera (riceve il punteggio, non segue fasce e braccialetti). */
    val camera: Boolean = false,
    /** Principale: telecamere collegate (per la moviola). */
    val cameras: Int = 0,
    /** Satellite col Wi-Fi spento: non può entrare nella rete del principale (Android non chiede nemmeno). */
    val wifiOff: Boolean = false,
)

/** Come un satellite vede una fascia, con l'ora (del principale) in cui l'ha detto. */
data class RemoteStrap(val status: RelayStrapStatus, val satellite: String, val at: Long)

/** Come un satellite vede un braccialetto: [conn] è la connessione da cui passano i messaggi per lui. */
private data class RemoteBandEntry(val status: RelayBandStatus, val conn: Int, val at: Long, val battery: BandStatus? = null)

/**
 * Due telefoni, uno per fondo campo. Il principale tiene la partita; il satellite si collega alle stesse fasce e agli
 * stessi braccialetti e fa da ponte: gli gira battito, tasti e colpi, e porta ai braccialetti punteggio e messaggi.
 * Fasce e braccialetti accettano un telefono alla volta: al cambio campo non serve fare niente, ognuno resta a chi lo
 * sente finché il segnale regge, poi lo prende il telefono più vicino.
 *
 * Misure, tasti e colpi del satellite portano l'ID della partita del principale e un numero progressivo: il
 * satellite li tiene finché il principale non conferma, così un'interruzione del Wi-Fi non perde niente. Un tasto
 * arrivato in ritardo però non vale più (un punto dato un minuto dopo sarebbe sbagliato): conta solo se è fresco.
 */
class PhoneRelay(
    context: Context,
    private val scope: CoroutineScope,
    private val heart: HeartRateManager,
    private val ble: BleManager,
    /** ID della partita in corso sul principale ("" = nessuna). */
    matchIds: Flow<String>,
    /** Porta del tabellone del principale (null = spento). */
    tvPorts: Flow<Int?>,
    /** Misura arrivata da un satellite: indirizzo della fascia, misura, ora (orologio di questo telefono), partita. */
    private val onRemoteReading: (addr: String, m: HeartMeasurement, at: Long, match: String) -> Unit,
    /** Il satellite smette: rimette fasce e braccialetti di questo telefono. */
    private val onSatelliteStopped: () -> Unit,
) {
    private val net = Dispatchers.IO.limitedParallelism(1)
    private val wifi = context.applicationContext.getSystemService(android.net.wifi.WifiManager::class.java)

    private val _ui = MutableStateFlow(RelayUi())
    val ui: StateFlow<RelayUi> = _ui
    private val _remote = MutableStateFlow<Map<String, RemoteStrap>>(emptyMap())
    /** Principale: come i satelliti vedono le fasce, per indirizzo (il migliore se sono più d'uno). */
    val remote: StateFlow<Map<String, RemoteStrap>> = _remote

    val isSatellite: Boolean get() = _ui.value.role == RelayRole.SATELLITE

    private val _score = MutableStateFlow<ScoreOverlay?>(null)
    /** Telecamera: il punteggio da disegnare (null finché il principale non l'ha mandato). */
    val score: StateFlow<ScoreOverlay?> = _score
    private val _points = MutableSharedFlow<PointMark>(extraBufferCapacity = 64)
    /** Telecamera: ogni punto giocato, con l'ora già sull'orologio di questo telefono. */
    val points: SharedFlow<PointMark> = _points
    private var lastScore: ScoreOverlay? = null

    private var currentMatch = ""

    // ---------------------------------------------------------------- principale

    private var hub: RelayHub? = null
    private var mainHost: String? = null
    private var tvPort: Int? = null
    private val hotspot = LocalHotspot(context) { scope.launch { onHotspotStopped() } }
    private val joiner = HotspotJoiner(context) { st -> scope.launch { onJoinState(st) } }
    private val inbox = RelayInbox()
    /** Satelliti collegati: id della connessione -> (id del satellite, nome del telefono). */
    private val sats = mutableMapOf<Int, Pair<String, String>>()
    /** Connessioni dei telefoni-telecamera. */
    private val cameraConns = mutableSetOf<Int>()
    private var lastStraps: List<RelayStrap> = emptyList()
    private var lastBands: List<RelayStrap> = emptyList()
    private var remoteBands = mapOf<String, RemoteBandEntry>()

    init {
        ble.relayOut = { addr, payload, kind -> toBand(addr, payload, kind) }
        scope.launch {
            matchIds.distinctUntilChanged().collect { id ->
                currentMatch = id
                if (_ui.value.role == RelayRole.MAIN) broadcast(RelayMsg(RelayMsg.MATCH, match = id))
            }
        }
        scope.launch {
            tvPorts.distinctUntilChanged().collect { port ->
                tvPort = port
                if (_ui.value.role == RelayRole.MAIN) broadcast(RelayMsg(RelayMsg.TV, tv = tvAddress()))
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
            ble.bands.map { m -> m.map { (side, b) -> RelayStrap(b.address, b.name, side.ordinal) }.sortedBy { it.side } }
                .distinctUntilChanged()
                .collect { list ->
                    lastBands = list
                    if (_ui.value.role == RelayRole.MAIN) broadcast(RelayMsg(RelayMsg.BANDS, bands = list))
                }
        }
        // Satellite: tasti, colpi e batteria dei braccialetti seguiti vanno al principale.
        scope.launch {
            ble.events.collect { e ->
                if (!isSatellite) return@collect
                val addr = ble.bands.value[e.side]?.address ?: return@collect
                queue(RelayMsg(RelayMsg.BAND_EVT, addr = addr, evt = e.type, reason = e.reason, boot = e.boot, bseq = e.seq))
            }
        }
        scope.launch {
            ble.strokes.collect { s ->
                if (!isSatellite) return@collect
                val addr = ble.bands.value[s.side]?.address ?: return@collect
                queue(RelayMsg(RelayMsg.STROKE, addr = addr, stroke = s.stroke))
            }
        }
        scope.launch {
            ble.status.collect { (side, st) ->
                if (!isSatellite || !_ui.value.connected) return@collect
                val addr = ble.bands.value[side]?.address ?: return@collect
                val c = client ?: return@collect
                scope.launch(net) { c.send(RelayMsg(RelayMsg.BAND_STATUS, addr = addr, battery = st)) }
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

    /**
     * Apre il server e prepara il QR. Con [useHotspot] prima prova a creare una rete sua (nei circoli il Wi-Fi spesso
     * non c'è o non arriva ai campi); se il telefono non può, usa la rete in cui si trova. false = nessuna rete.
     */
    suspend fun startMain(useHotspot: Boolean): Boolean {
        if (_ui.value.role == RelayRole.MAIN && hub != null) return true
        stopSatellite()
        _ui.value = RelayUi(RelayRole.MAIN)
        val before = TvServer.localAddresses().toSet()
        val info = if (useHotspot) hotspot.start() else null
        // L'indirizzo dell'hotspot compare qualche istante dopo l'avvio: è quello che prima non c'era.
        var host: String? = null
        if (info != null) {
            for (i in 0 until 20) {
                host = (TvServer.localAddresses() - before).firstOrNull()
                if (host != null) break
                delay(250)
            }
        }
        val hotspotOn = info != null && host != null
        if (!hotspotOn) {
            hotspot.stop()
            host = TvServer.localAddresses().firstOrNull()
        }
        val h = RelayHub(
            onMessage = { id, conn, m -> onHubMessage(id, conn, m) },
            onClosed = { id -> scope.launch { satelliteGone(id) } },
        )
        val port = host?.let { runCatching { h.start() }.getOrNull() }
        if (_ui.value.role != RelayRole.MAIN || host == null || port == null) {
            // Annullato nel frattempo, o nessuna rete.
            hotspot.stop()
            h.stop()
            if (_ui.value.role == RelayRole.MAIN) _ui.value = RelayUi()
            return false
        }
        hub = h
        mainHost = host
        val own = info.takeIf { hotspotOn }
        _ui.value = RelayUi(RelayRole.MAIN, url = RelayCodec.url(host!!, port, h.token, own), hotspot = own)
        return true
    }

    /** Il sistema ha spento la rete del principale: i satelliti non la raggiungono più, si riparte da capo. */
    private fun onHotspotStopped() {
        if (_ui.value.role == RelayRole.MAIN && _ui.value.hotspot != null) {
            Log.w(TAG, "hotspot spento dal sistema")
            stopMain()
        }
    }

    fun stopMain() {
        hub?.let { h -> scope.launch(net) { h.stop() } }
        hub = null
        mainHost = null
        hotspot.stop()
        sats.clear()
        _remote.value = emptyMap()
        setRemoteBands(emptyMap())
        if (_ui.value.role == RelayRole.MAIN) _ui.value = RelayUi()
    }

    private fun tvAddress(): String? {
        val host = mainHost ?: return null
        val port = tvPort ?: return null
        return "$host:$port"
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
                    if (m.role == RelayMsg.ROLE_CAMERA) cameraConns += id
                    publishSatellites()
                    send(id, RelayMsg(RelayMsg.WELCOME, match = currentMatch, straps = lastStraps, bands = lastBands, tv = tvAddress(), score = lastScore))
                }
                RelayMsg.HR, RelayMsg.BAND_EVT, RelayMsg.STROKE -> {
                    val sat = sats[id]?.first ?: return@launch
                    val seq = m.seq ?: return@launch
                    if (inbox.accept(sat, seq)) onQueued(m)
                    send(id, RelayMsg(RelayMsg.ACK, seq = inbox.last(sat)))
                }
                RelayMsg.STATUS -> onStatus(id, m)
                RelayMsg.REPLAY -> onReplayPart(m)
                RelayMsg.REPLAY_FAIL -> m.rid?.let { if (it == replayId) _replay.value = Replay.Failed }
                RelayMsg.BAND_STATUS -> {
                    val addr = m.addr ?: return@launch
                    val st = m.battery ?: return@launch
                    remoteBands[addr]?.let { setRemoteBands(remoteBands + (addr to it.copy(battery = st))) }
                    ble.injectStatus(addr, st)
                }
            }
        }
    }

    /** Dati del satellite arrivati per la prima volta (i doppioni dopo una riconnessione non arrivano qui). */
    private fun onQueued(m: RelayMsg) {
        val addr = m.addr ?: return
        val at = m.at ?: return
        when (m.t) {
            RelayMsg.HR -> m.bpm?.let { onRemoteReading(addr, HeartMeasurement(it, m.contact), at, m.match.orEmpty()) }
            // Un tasto vale solo se è fresco: dopo un'interruzione del Wi-Fi il punto non è più quello di adesso.
            RelayMsg.BAND_EVT -> if (System.currentTimeMillis() - at <= EVENT_MAX_AGE_MS) {
                m.evt?.let { ble.injectEvent(addr, it, m.reason, m.boot, m.bseq) }
            } else {
                Log.i(TAG, "tasto del satellite arrivato dopo ${System.currentTimeMillis() - at} ms: ignorato")
            }
            // I colpi contano anche in ritardo, ma solo nella loro partita.
            RelayMsg.STROKE -> if (m.match == currentMatch && currentMatch.isNotEmpty()) m.stroke?.let { ble.injectStroke(addr, it) }
        }
    }

    private fun onStatus(id: Int, m: RelayMsg) {
        val name = sats[id]?.second ?: return
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
        var bands = remoteBands
        for (b in m.bandStatus.orEmpty()) {
            val old = bands[b.addr]
            if (old == null || old.conn == id || now - old.at > STALE_MS || b.ready && !old.status.ready) {
                bands = bands + (b.addr to RemoteBandEntry(b, id, now, old?.battery.takeIf { old?.conn == id }))
            }
        }
        setRemoteBands(bands)
    }

    private fun setRemoteBands(map: Map<String, RemoteBandEntry>) {
        remoteBands = map
        ble.setRemote(map.mapValues { (_, e) -> RemoteBand(e.status.ready, e.battery, e.status.battery) })
    }

    /** Principale: il punteggio per la grafica delle telecamere (e per quelle che si collegano dopo). */
    fun publishScore(o: ScoreOverlay?) {
        if (o == lastScore) return
        lastScore = o
        if (_ui.value.role == RelayRole.MAIN && o != null) broadcast(RelayMsg(RelayMsg.SCORE, score = o))
    }

    /** Principale: un punto appena giocato, per i capitoli del video ([PointMark.at] sull'orologio di questo telefono). */
    fun publishPoint(p: PointMark) {
        if (_ui.value.role == RelayRole.MAIN) broadcast(RelayMsg(RelayMsg.POINT, point = p))
    }

    // ---------------------------------------------------------------- moviola

    /** Moviola sul telefono dell'arbitro: in attesa, pronta (i pezzi da vedere in fila) o non riuscita. */
    sealed class Replay {
        /** [back] punto chiesto (0 = ultimo), oppure null e gli ultimi [seconds] secondi. */
        data class Waiting(val back: Int?, val seconds: Int) : Replay()
        data class Ready(val files: List<File>) : Replay()
        data object Failed : Replay()
    }

    private val _replay = MutableStateFlow<Replay?>(null)
    val replay: StateFlow<Replay?> = _replay
    private var replayId: String? = null
    private var replayDir: File? = null
    private var replayTimeout: Job? = null

    /**
     * Principale: chiede alla telecamera la moviola del punto [back] (0 = l'ultimo segnato, 1 = quello prima),
     * oppure, con [back] null, gli ultimi [seconds] secondi. false se non c'è nessuna telecamera.
     */
    fun requestReplay(back: Int?, seconds: Int, cacheDir: File): Boolean {
        val conns = cameraConns.filter { it in sats }
        if (conns.isEmpty()) return false
        val rid = System.currentTimeMillis().toString(36)
        replayId = rid
        replayDir = File(cacheDir, "replays/$rid").apply { deleteRecursively(); mkdirs() }
        _replay.value = Replay.Waiting(back, seconds)
        // Una telecamera basta: la prima.
        send(conns.first(), RelayMsg(RelayMsg.CHALLENGE, rid = rid, rlen = seconds, back = back, at = System.currentTimeMillis()))
        replayTimeout?.cancel()
        replayTimeout = scope.launch {
            delay(REPLAY_TIMEOUT_MS)
            if (_replay.value is Replay.Waiting && replayId == rid) _replay.value = Replay.Failed
        }
        return true
    }

    fun closeReplay() {
        replayTimeout?.cancel()
        replayId = null
        _replay.value = null
    }

    /** I pezzi arrivano in ordine (TCP): si aggiungono al file; all'ultima parte dell'ultimo file la moviola è pronta. */
    private fun onReplayPart(m: RelayMsg) {
        if (m.rid == null || m.rid != replayId) return
        val dir = replayDir ?: return
        val idx = m.file ?: return
        val f = File(dir, "part_$idx.mp4")
        runCatching { m.data?.let { f.appendBytes(android.util.Base64.decode(it, android.util.Base64.NO_WRAP)) } }
            .onFailure { _replay.value = Replay.Failed; return }
        if (idx == (m.files ?: 1) - 1 && m.part == (m.parts ?: 1) - 1) {
            replayTimeout?.cancel()
            _replay.value = Replay.Ready((0 until (m.files ?: 1)).map { File(dir, "part_$it.mp4") }.filter { it.exists() })
        }
    }

    /** Telecamera: una richiesta di moviola, con l'ora già sull'orologio del telefono-telecamera. */
    data class Challenge(val rid: String, val at: Long, val seconds: Int, val back: Int?)

    private val _challenges = MutableSharedFlow<Challenge>(extraBufferCapacity = 4)
    val challenges: SharedFlow<Challenge> = _challenges

    /** Telecamera: manda i pezzi della moviola al principale, a fette da 256 kB. */
    fun sendReplay(rid: String, parts: List<File>) {
        val c = client ?: return
        scope.launch(net) {
            if (parts.isEmpty()) {
                c.send(RelayMsg(RelayMsg.REPLAY_FAIL, rid = rid))
                return@launch
            }
            for ((i, f) in parts.withIndex()) {
                val bytes = runCatching { f.readBytes() }.getOrNull() ?: ByteArray(0)
                val n = maxOf(1, (bytes.size + REPLAY_CHUNK - 1) / REPLAY_CHUNK)
                for (k in 0 until n) {
                    val slice = bytes.copyOfRange(k * REPLAY_CHUNK, minOf(bytes.size, (k + 1) * REPLAY_CHUNK))
                    val data = android.util.Base64.encodeToString(slice, android.util.Base64.NO_WRAP)
                    if (!c.send(RelayMsg(RelayMsg.REPLAY, rid = rid, data = data, file = i, files = parts.size, part = k, parts = n))) return@launch
                }
            }
        }
    }

    /** Principale: punteggio, comando o impostazioni per un braccialetto che sente solo un satellite. */
    private fun toBand(addr: String, payload: String, kind: String): Boolean {
        val e = remoteBands[addr]?.takeIf { it.status.ready } ?: return false
        if (e.conn !in sats) return false
        send(e.conn, RelayMsg(RelayMsg.BAND_OUT, addr = addr, payload = payload, kind = kind))
        return true
    }

    private fun satelliteGone(id: Int) {
        val name = sats.remove(id)?.second ?: return
        _remote.value = _remote.value.filterValues { it.satellite != name }
        setRemoteBands(remoteBands.filterValues { it.conn != id })
        publishSatellites()
    }

    private fun publishSatellites() {
        cameraConns.retainAll(sats.keys)
        if (_ui.value.role == RelayRole.MAIN) _ui.value = _ui.value.copy(satellites = sats.values.map { it.second }, cameras = cameraConns.size)
    }

    private fun dropStaleRemote() {
        val now = System.currentTimeMillis()
        val fresh = _remote.value.filterValues { now - it.at <= STALE_MS }
        if (fresh.size != _remote.value.size) _remote.value = fresh
        val bands = remoteBands.filterValues { now - it.at <= STALE_MS }
        if (bands.size != remoteBands.size) setRemoteBands(bands)
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

    private var client: RelayClient? = null
    /** Misure, tasti e colpi da confermare; [RelayMsg.at] è sull'orologio di questo telefono finché non partono. */
    private val outbox = RelayOutbox<RelayMsg>()
    private val clock = ClockSync()
    /** Ultimo numero già spedito su questa connessione: dopo una riconnessione si riparte da quelli non confermati. */
    private var sentUpTo = 0L
    private var satId = ""
    private var pingJob: Job? = null

    /**
     * Testo del QR del principale: questo telefono diventa satellite. Con [camera] fa da telefono-telecamera:
     * riceve punteggio e punti ma non prende fasce e braccialetti. false se il QR non è quello giusto.
     */
    fun startSatellite(text: String, camera: Boolean = false): Boolean {
        val target = RelayCodec.parseUrl(text) ?: return false
        val (host, port, token) = target
        stopMain()
        stopSatellite()
        satId = ByteArray(8).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        heart.disconnectAll()
        ble.disconnectAll()
        val hello = RelayMsg(RelayMsg.HELLO, token = token, sat = satId, name = deviceName(), role = RelayMsg.ROLE_CAMERA.takeIf { camera })
        val c = RelayClient(
            host, port, hello = { hello },
            onConnected = { scope.launch { onSatConnected() } },
            onMessage = { m -> onSatMessage(m) },
            onDisconnected = { scope.launch { _ui.value = _ui.value.copy(connected = false) } },
            newSocket = { joiner.network?.socketFactory?.createSocket() ?: Socket() },
        )
        client = c
        _ui.value = RelayUi(RelayRole.SATELLITE, hotspot = target.hotspot, camera = camera)
        _score.value = null
        // La rete del principale: ci si entra da soli (Android 10+, con una conferma), altrimenti a mano.
        target.hotspot?.let { if (!joiner.join(it)) _ui.value = _ui.value.copy(joinByHand = true) }
        c.start()
        return true
    }

    private fun onJoinState(st: JoinState) {
        if (isSatellite) _ui.value = _ui.value.copy(join = st)
    }

    fun stopSatellite() {
        if (!isSatellite) return
        val c = client
        scope.launch(net) { c?.stop() }
        client = null
        joiner.release()
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
                    if (!_ui.value.camera) {
                        follow(m.straps.orEmpty())
                        followBands(m.bands.orEmpty())
                    }
                    m.score?.let { _score.value = it }
                    _ui.value = _ui.value.copy(tv = m.tv)
                }
                RelayMsg.STRAPS -> if (!_ui.value.camera) follow(m.straps.orEmpty())
                RelayMsg.BANDS -> if (!_ui.value.camera) followBands(m.bands.orEmpty())
                RelayMsg.SCORE -> m.score?.let { _score.value = it }
                // L'ora della richiesta arriva sull'orologio del principale.
                RelayMsg.CHALLENGE -> if (m.rid != null && m.at != null) {
                    _challenges.tryEmit(Challenge(m.rid, m.at - (clock.offset ?: 0L), m.rlen ?: ReplayRecorder.DEFAULT_S, m.back))
                }
                // L'ora del punto arriva sull'orologio del principale: la si riporta su quello di questo telefono.
                RelayMsg.POINT -> m.point?.let { p -> _points.tryEmit(p.copy(at = p.at - (clock.offset ?: 0L))) }
                RelayMsg.MATCH -> currentMatch = m.match.orEmpty()
                RelayMsg.TV -> _ui.value = _ui.value.copy(tv = m.tv)
                RelayMsg.BAND_OUT -> {
                    val side = ble.bands.value.entries.firstOrNull { it.value.address == m.addr }?.key ?: return@launch
                    val payload = m.payload ?: return@launch
                    ble.raw(side, payload, m.kind ?: "show")
                }
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

    /** ...e i suoi braccialetti. */
    private fun followBands(list: List<RelayStrap>) {
        val want = list.mapNotNull { r -> Side.entries.getOrNull(r.side)?.let { it to r } }.toMap()
        val have = ble.bands.value
        for (side in Side.entries) {
            val r = want[side]
            if (r == null) {
                if (have[side] != null) ble.assign(side, null, null)
            } else if (have[side]?.address != r.addr) {
                ble.assign(side, r.addr, r.name)
            }
        }
    }

    /** Satellite: ogni misura delle fasce seguite va nella coda per il principale. */
    fun onLocalReading(side: Side, m: HeartMeasurement) {
        if (!isSatellite) return
        val addr = heart.straps.value[side]?.address ?: return
        queue(RelayMsg(RelayMsg.HR, addr = addr, bpm = m.bpm, contact = m.contact))
    }

    private fun queue(m: RelayMsg) {
        outbox.add(m.copy(at = System.currentTimeMillis(), match = currentMatch))
        _ui.value = _ui.value.copy(pending = outbox.size)
        flush()
    }

    /** Spedisce quello che non è ancora partito su questa connessione (serve l'orologio allineato). */
    private fun flush() {
        val c = client ?: return
        val offset = clock.offset ?: return
        if (!_ui.value.connected) return
        val todo = outbox.pending().filter { it.first > sentUpTo }
        if (todo.isEmpty()) return
        sentUpTo = todo.last().first
        scope.launch(net) {
            for ((seq, m) in todo) {
                // caduta: alla riconnessione si rimanda da quelli non confermati
                if (!c.send(m.copy(seq = seq, at = (m.at ?: 0L) + offset))) break
            }
        }
    }

    private fun satelliteTick() {
        // Rete del principale da raggiungere col Wi-Fi spento: lo si dice (si accende dal pannello del Wi-Fi).
        val off = _ui.value.hotspot != null && wifi?.isWifiEnabled == false
        if (off != _ui.value.wifiOff) _ui.value = _ui.value.copy(wifiOff = off)
        val c = client ?: return
        // La telecamera non sente fasce e braccialetti: niente da dire al principale.
        if (_ui.value.camera) return
        val straps = heart.straps.value.values.map { it.toStatus() }
        // "Pronto" solo se collegato davvero a questo telefono.
        val bands = ble.bands.value.values.map { RelayBandStatus(it.address, it.state == LinkState.READY && !it.relayed, it.battery) }
        if (_ui.value.connected) scope.launch(net) { c.send(RelayMsg(RelayMsg.STATUS, status = straps, bandStatus = bands)) }
        flush()
    }

    private fun StrapInfo.toStatus() = RelayStrapStatus(address, state == StrapState.READY, bpm, rssi)

    private fun deviceName(): String = listOf(Build.MANUFACTURER, Build.MODEL).filter { it.isNotBlank() }.distinct().joinToString(" ")

    fun stop() {
        stopMain()
        stopSatellite()
    }

    companion object {
        private const val TAG = "PhoneRelay"
        /** Stato di una fascia o di un braccialetto visto dal satellite: dopo 6 s senza notizie non vale più. */
        const val STALE_MS = 6_000L
        /** Misure del satellite più vecchie di così non sono "dal vivo": vanno a riempire i buchi. */
        const val LIVE_MS = 8_000L
        /** Moviola non arrivata entro questo tempo: non riuscita. */
        private const val REPLAY_TIMEOUT_MS = 45_000L
        private const val REPLAY_CHUNK = 256 * 1024
        /** Un tasto arrivato dal satellite più tardi di così non dà (o toglie) il punto. */
        const val EVENT_MAX_AGE_MS = 5_000L
    }
}
