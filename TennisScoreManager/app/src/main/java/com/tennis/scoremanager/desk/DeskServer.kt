// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.desk

import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket

/**
 * Il server della Segreteria da PC: HTTPS sulla rete locale, solo TLS 1.2 e 1.3. Non contiene dati: serve la pagina
 * ([asset]) e passa le richieste /api/ a [api]. Risponde solo a chi è in rete locale ([allowed]) e solo se la richiesta
 * è rivolta a un indirizzo numerico o a localhost (niente "DNS rebinding": un sito esterno non può parlargli).
 */
class DeskServer(
    private val cert: DeskCert,
    private val api: DeskApi,
    private val asset: (String) -> ByteArray?,
    private val allowed: (InetAddress) -> Boolean,
) {
    @Volatile private var socket: SSLServerSocket? = null
    private var pool: ExecutorService? = null

    /** La porta in ascolto (null = spento). */
    val port: Int? get() = socket?.localPort

    @Synchronized
    fun start(first: Int = PORT): Int {
        socket?.let { return it.localPort }
        val factory = cert.sslContext().serverSocketFactory
        val s = (first until first + 10).firstNotNullOfOrNull { p ->
            runCatching {
                factory.createServerSocket(p, 32) as SSLServerSocket
            }.getOrNull()
        } ?: throw IOException("Nessuna porta libera")
        s.enabledProtocols = s.supportedProtocols.filter { it == "TLSv1.3" || it == "TLSv1.2" }.toTypedArray()
        val workers = Executors.newFixedThreadPool(WORKERS)
        pool = workers
        socket = s
        Thread({ acceptLoop(s, workers) }, "desk-accept").apply { isDaemon = true }.start()
        return s.localPort
    }

    @Synchronized
    fun stop() {
        runCatching { socket?.close() }
        socket = null
        pool?.shutdownNow()
        pool = null
    }

    private fun acceptLoop(s: SSLServerSocket, workers: ExecutorService) {
        while (!s.isClosed) {
            val client = try { s.accept() } catch (_: IOException) {
                if (s.isClosed) return
                runCatching { Thread.sleep(300) }
                continue
            }
            if (!allowed(client.inetAddress)) { runCatching { client.close() }; continue }
            runCatching { workers.execute { runCatching { handle(client as SSLSocket) } } }.onFailure { runCatching { client.close() } }
        }
    }

    private fun handle(sock: Socket) {
        sock.use { s ->
            s.soTimeout = TIMEOUT_MS
            (s as SSLSocket).startHandshake()
            val input = s.getInputStream()
            val out = BufferedOutputStream(s.getOutputStream())
            val first = readLine(input) ?: return
            val parts = first.split(' ')
            if (parts.size < 3) return respond(out, 400, TEXT, ByteArray(0))
            val headers = HashMap<String, String>()
            for (i in 0 until MAX_HEADERS) {
                val h = readLine(input) ?: return
                if (h.isEmpty()) break
                val c = h.indexOf(':')
                if (c > 0) headers[h.substring(0, c).trim().lowercase()] = h.substring(c + 1).trim()
                if (i == MAX_HEADERS - 1) return respond(out, 431, TEXT, ByteArray(0))
            }
            if (!hostOk(headers["host"])) return respond(out, 421, TEXT, ByteArray(0))
            val len = headers["content-length"]?.toIntOrNull() ?: 0
            if (len < 0 || len > MAX_BODY) return respond(out, 413, TEXT, ByteArray(0))
            val body = ByteArray(len)
            var got = 0
            while (got < len) {
                val n = input.read(body, got, len - got)
                if (n < 0) return
                got += n
            }
            val target = parts[1]
            val path = target.substringBefore('?')
            val query = target.substringAfter('?', "").split('&').filter { '=' in it }.associate {
                val (k, v) = it.split('=', limit = 2)
                URLDecoder.decode(k, "UTF-8") to URLDecoder.decode(v, "UTF-8")
            }
            if (path.startsWith("/api/")) {
                val token = headers["authorization"]?.takeIf { it.startsWith("Bearer ") }?.removePrefix("Bearer ")?.trim()
                val r = api.handle(DeskApi.Req(parts[0], path.removePrefix("/api"), query, body.toString(Charsets.UTF_8), token, peer(s)))
                respond(out, r.status, r.type, r.body)
            } else serveAsset(out, parts[0], path)
        }
    }

    private fun peer(s: Socket): String = s.inetAddress.hostAddress.orEmpty()

    private fun serveAsset(out: OutputStream, method: String, path: String) {
        if (method != "GET" && method != "HEAD") return respond(out, 405, TEXT, ByteArray(0))
        val name = if (path == "/") "index.html" else path.removePrefix("/")
        val type = TYPES[name.substringAfterLast('.')]
        val data = if (type != null && SAFE_NAME.matches(name)) asset(name) else null
        if (data == null) return respond(out, 404, TEXT, ByteArray(0))
        respond(out, 200, type!!, if (method == "HEAD") ByteArray(0) else data, data.size)
    }

    private fun respond(out: OutputStream, status: Int, type: String, body: ByteArray, length: Int = body.size) {
        val head = "HTTP/1.1 $status ${REASON[status] ?: "Error"}\r\n" +
            "Content-Type: $type\r\n" +
            "Content-Length: $length\r\n" +
            "Cache-Control: no-store\r\n" +
            "X-Content-Type-Options: nosniff\r\n" +
            "X-Frame-Options: DENY\r\n" +
            "Referrer-Policy: no-referrer\r\n" +
            "Cross-Origin-Resource-Policy: same-origin\r\n" +
            "Content-Security-Policy: default-src 'none'; script-src 'self'; style-src 'self'; img-src 'self'; connect-src 'self'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'\r\n" +
            "Connection: close\r\n\r\n"
        out.write(head.toByteArray(Charsets.ISO_8859_1))
        out.write(body)
        out.flush()
    }

    /** Riga della richiesta (max 8 KB), senza \r\n; null = connessione chiusa o riga troppo lunga. */
    private fun readLine(input: InputStream): String? {
        val b = ByteArrayOutputStream()
        while (b.size() < 8192) {
            val c = input.read()
            if (c < 0) return null
            if (c == '\n'.code) return b.toString("ISO-8859-1").trimEnd('\r')
            b.write(c)
        }
        return null
    }

    companion object {
        const val PORT = 8443
        private const val WORKERS = 6
        private const val TIMEOUT_MS = 15_000
        private const val MAX_HEADERS = 64
        // I backup della segreteria arrivano interi (compressi, in base64).
        const val MAX_BODY = 16 * 1024 * 1024
        private const val TEXT = "text/plain; charset=utf-8"
        private val SAFE_NAME = Regex("^[a-z0-9-]+\\.[a-z]+$")
        private val IPV4_HOST = Regex("^\\d{1,3}(\\.\\d{1,3}){3}(:\\d+)?$")
        private val TYPES = mapOf(
            "html" to "text/html; charset=utf-8", "js" to "text/javascript; charset=utf-8", "css" to "text/css; charset=utf-8",
            "png" to "image/png", "svg" to "image/svg+xml",
        )
        private val REASON = mapOf(
            200 to "OK", 400 to "Bad Request", 401 to "Unauthorized", 403 to "Forbidden", 404 to "Not Found", 405 to "Method Not Allowed",
            409 to "Conflict", 413 to "Payload Too Large", 421 to "Misdirected Request", 423 to "Locked", 429 to "Too Many Requests", 431 to "Headers Too Large", 500 to "Server Error",
        )

        fun hostOk(host: String?): Boolean = host != null && (IPV4_HOST.matches(host) || host == "localhost" || host.startsWith("localhost:"))
    }
}
