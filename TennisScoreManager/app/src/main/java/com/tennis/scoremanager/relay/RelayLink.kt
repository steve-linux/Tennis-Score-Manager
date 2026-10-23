// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.relay

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/** Una connessione TCP a righe: ogni riga un [RelayMsg]. Scrive da qualunque thread. */
class RelayConnection(private val socket: Socket) {
    private val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8))
    private val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))

    val closed: Boolean get() = socket.isClosed

    /** false se la connessione è caduta. */
    fun send(m: RelayMsg): Boolean = synchronized(writer) {
        runCatching {
            writer.write(RelayCodec.encode(m))
            writer.write("\n")
            writer.flush()
        }.isSuccess
    }

    /** Blocca fino al prossimo messaggio; null = connessione chiusa o muta da troppo tempo. */
    fun read(): RelayMsg? {
        while (true) {
            val line = runCatching { reader.readLine() }.getOrNull() ?: return null
            RelayCodec.decode(line)?.let { return it }
        }
    }

    fun close() {
        runCatching { socket.close() }
    }

    companion object {
        /** Senza niente per 20 s (i ping sono ogni 5 s) la connessione si considera persa. */
        const val SILENCE_MS = 20_000
    }
}

/**
 * Telefono principale: server che accetta i satelliti col codice giusto. [onMessage] e [onClosed] arrivano dai
 * thread di rete: chi li riceve li riporta sul thread principale.
 */
class RelayHub(
    private val onMessage: (id: Int, conn: RelayConnection, m: RelayMsg) -> Unit,
    private val onClosed: (id: Int) -> Unit,
) {
    private var server: ServerSocket? = null
    private val ids = AtomicInteger(0)
    private val conns = ConcurrentHashMap<Int, RelayConnection>()
    val token: String = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }

    /** Avvia il server su una porta libera e la restituisce. */
    fun start(): Int {
        val ss = ServerSocket().apply {
            reuseAddress = true
            bind(InetSocketAddress(0))
        }
        server = ss
        thread(name = "tsm-relay-hub", isDaemon = true) {
            while (!ss.isClosed) {
                val socket = runCatching { ss.accept() }.getOrNull() ?: break
                thread(name = "tsm-relay-in", isDaemon = true) { serve(socket) }
            }
        }
        return ss.localPort
    }

    private fun serve(socket: Socket) {
        socket.soTimeout = RelayConnection.SILENCE_MS
        socket.tcpNoDelay = true
        val conn = runCatching { RelayConnection(socket) }.getOrNull() ?: return
        val hello = conn.read()
        if (hello?.t != RelayMsg.HELLO || hello.token != token || hello.sat.isNullOrEmpty()) {
            conn.close()
            return
        }
        val id = ids.incrementAndGet()
        conns[id] = conn
        onMessage(id, conn, hello)
        while (true) {
            val m = conn.read() ?: break
            onMessage(id, conn, m)
        }
        conn.close()
        conns.remove(id)
        onClosed(id)
    }

    fun send(id: Int, m: RelayMsg) {
        conns[id]?.send(m)
    }

    fun broadcast(m: RelayMsg) = conns.values.forEach { it.send(m) }

    fun stop() {
        runCatching { server?.close() }
        server = null
        conns.values.forEach { it.close() }
        conns.clear()
    }
}

/**
 * Telefono satellite: si collega al principale e, se la connessione cade (fuori portata del Wi-Fi, principale
 * spento), riprova ogni pochi secondi finché non si chiama [stop].
 */
class RelayClient(
    private val host: String,
    private val port: Int,
    private val hello: () -> RelayMsg,
    private val onConnected: (RelayConnection) -> Unit,
    private val onMessage: (RelayMsg) -> Unit,
    private val onDisconnected: () -> Unit,
) {
    @Volatile private var running = false
    @Volatile private var conn: RelayConnection? = null

    fun start() {
        if (running) return
        running = true
        thread(name = "tsm-relay-client", isDaemon = true) {
            while (running) {
                val c = runCatching {
                    val s = Socket()
                    s.connect(InetSocketAddress(host, port), 5_000)
                    s.soTimeout = RelayConnection.SILENCE_MS
                    s.tcpNoDelay = true
                    RelayConnection(s)
                }.getOrNull()
                if (c != null && c.send(hello())) {
                    conn = c
                    onConnected(c)
                    while (running) onMessage(c.read() ?: break)
                    conn = null
                    c.close()
                    onDisconnected()
                } else {
                    c?.close()
                }
                if (running) Thread.sleep(RETRY_MS)
            }
        }
    }

    /** false se in questo momento non c'è collegamento (il messaggio resta da rimandare a chi lo chiama). */
    fun send(m: RelayMsg): Boolean = conn?.send(m) ?: false

    fun stop() {
        running = false
        conn?.close()
        conn = null
    }

    companion object {
        private const val RETRY_MS = 3_000L
    }
}
