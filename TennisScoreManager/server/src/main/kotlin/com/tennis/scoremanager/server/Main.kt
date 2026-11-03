// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.server

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.util.concurrent.Executors

private const val MAX_BODY = 512 * 1024

private val USAGE = """
    TSM – server del circolo per le prenotazioni dei campi

    java -jar tsm-server.jar [--host 0.0.0.0] [--port 8080] [--data /var/lib/tsm-server] [--reset-admin]

      --host         l'indirizzo su cui ascoltare (127.0.0.1 se davanti c'è Caddy per l'HTTPS)
      --port         la porta (8080)
      --data         la cartella dei dati: state.json e setup-code.txt
      --reset-admin  nuovo codice di abbinamento per la segreteria (quella vecchia non entra più)
      --no-serve     non avviare il server (con --reset-admin: solo il codice nuovo)
""".trimIndent()

fun main(args: Array<String>) {
    val opts = args.toList()
    fun opt(name: String, def: String) = opts.indexOf(name).takeIf { it >= 0 && it + 1 < opts.size }?.let { opts[it + 1] } ?: def
    if ("--help" in opts || "-h" in opts) { println(USAGE); return }
    val host = opt("--host", "0.0.0.0")
    val port = opt("--port", "8080").toInt()
    val data = File(opt("--data", System.getenv("TSM_DATA") ?: "tsm-data"))

    val store = Store(data)
    val t = Texts.forSystem()
    store.ensureSetupCode(reset = "--reset-admin" in opts)?.let { code ->
        println(t.code(code, File(data, Store.SETUP_FILE).path))
        println(t.app)
    }
    // Solo il codice nuovo (reset-admin.sh), senza avviare il server.
    if ("--no-serve" in opts) return
    val server = start(store, host, port)
    println(t.listening("$host:$port", data.absolutePath))
    for (a in addresses(host)) println("  http://$a:$port")
    Runtime.getRuntime().addShutdownHook(Thread { server.stop(1) })
}

/** Gli indirizzi da scrivere nell'app: quelli di rete del computer, se ascolta su tutti (0.0.0.0). */
fun addresses(host: String): List<String> {
    if (host != "0.0.0.0") return listOf(host)
    return runCatching {
        java.net.NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback && !it.isVirtual }
            .flatMap { it.inetAddresses.toList() }.filterIsInstance<java.net.Inet4Address>().map { it.hostAddress }
    }.getOrDefault(emptyList()).ifEmpty { listOf("127.0.0.1") }
}

/** Avvia il server: /api/v1/… per l'app e la pagina web, il resto è la pagina web (PWA). */
fun start(store: Store, host: String, port: Int): HttpServer {
    val api = Api(store)
    // Ogni minuto i promemoria delle partite.
    java.util.concurrent.Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "promemoria").apply { isDaemon = true } }
        .scheduleAtFixedRate({ runCatching { api.tick() }.onFailure { System.err.println("Promemoria: $it") } }, 30, 60, java.util.concurrent.TimeUnit.SECONDS)
    val server = HttpServer.create(InetSocketAddress(host, port), 0)
    server.executor = Executors.newFixedThreadPool(4)
    server.createContext("/") { ex ->
        try {
            if (ex.requestURI.path.startsWith("/api/")) serveApi(api, ex) else serveStatic(ex)
        } catch (e: Exception) {
            System.err.println("Errore su ${ex.requestURI}: $e")
            runCatching { send(ex, 500, "{\"error\":\"SERVER\"}".toByteArray(), "application/json") }
        } finally {
            ex.close()
        }
    }
    server.start()
    return server
}

/** Dietro Caddy, che chiama da 127.0.0.1, l'indirizzo vero è quello che Caddy scrive in X-Forwarded-For. */
private fun forwardedFor(ex: HttpExchange): String? =
    ex.requestHeaders.getFirst("X-Forwarded-For")?.split(',')?.lastOrNull()?.trim()?.takeIf { it.isNotEmpty() && ex.remoteAddress.address.isLoopbackAddress }

/** L'indirizzo di chi chiama. */
private fun clientIp(ex: HttpExchange): String = forwardedFor(ex) ?: ex.remoteAddress.address.hostAddress

private fun serveApi(api: Api, ex: HttpExchange) {
    val body = ex.requestBody.use { it.readNBytes(MAX_BODY + 1) }
    if (body.size > MAX_BODY) { send(ex, 413, "{\"error\":\"TOO_LARGE\"}".toByteArray(), "application/json"); return }
    val query = ex.requestURI.rawQuery.orEmpty().split('&').filter { '=' in it }.associate {
        val (k, v) = it.split('=', limit = 2); URLDecoder.decode(k, Charsets.UTF_8) to URLDecoder.decode(v, Charsets.UTF_8)
    }
    val token = ex.requestHeaders.getFirst("Authorization")?.takeIf { it.startsWith("Bearer ") }?.removePrefix("Bearer ")?.trim()
    val r = api.handle(Req(ex.requestMethod, ex.requestURI.path, query, body.toString(Charsets.UTF_8), token, clientIp(ex), viaProxy = forwardedFor(ex) != null))
    ex.responseHeaders.add("Cache-Control", "no-store")
    send(ex, r.status, r.body.toByteArray(), "application/json; charset=utf-8")
}

private val TYPES = mapOf(
    "html" to "text/html; charset=utf-8", "js" to "text/javascript; charset=utf-8", "css" to "text/css; charset=utf-8",
    "webmanifest" to "application/manifest+json", "json" to "application/json", "svg" to "image/svg+xml", "png" to "image/png",
)

/** I file della pagina web, dentro il jar (cartella web). */
private fun serveStatic(ex: HttpExchange) {
    if (ex.requestMethod != "GET" && ex.requestMethod != "HEAD") { send(ex, 405, ByteArray(0), "text/plain"); return }
    val path = ex.requestURI.path.trimStart('/').ifEmpty { "index.html" }
    if (path.contains("..") || !path.matches(Regex("[A-Za-z0-9._/-]+"))) { send(ex, 404, ByteArray(0), "text/plain"); return }
    val bytes = Api::class.java.getResourceAsStream("/web/$path")?.use { it.readBytes() }
    if (bytes == null) { send(ex, 404, "Not found".toByteArray(), "text/plain"); return }
    ex.responseHeaders.add("Cache-Control", "no-cache")
    send(ex, 200, bytes, TYPES[path.substringAfterLast('.')] ?: "application/octet-stream")
}

private fun send(ex: HttpExchange, status: Int, bytes: ByteArray, type: String) {
    ex.responseHeaders.apply {
        set("Content-Type", type)
        set("X-Content-Type-Options", "nosniff")
        set("Referrer-Policy", "no-referrer")
        set("Content-Security-Policy", "default-src 'self'; img-src 'self' data:; frame-ancestors 'none'")
    }
    if (ex.requestMethod == "HEAD") { ex.sendResponseHeaders(status, -1); return }
    ex.sendResponseHeaders(status, if (bytes.isEmpty()) -1 else bytes.size.toLong())
    if (bytes.isNotEmpty()) ex.responseBody.use { it.write(bytes) }
}

/** I messaggi del server nella lingua del sistema (LANG), come lo script d'installazione. */
private class Texts(val code: (String, String) -> String, val app: String, val listening: (String, String) -> String) {
    companion object {
        fun forSystem(): Texts = when ((System.getenv("LC_ALL") ?: System.getenv("LC_MESSAGES") ?: System.getenv("LANG") ?: "en").take(2)) {
            "it" -> Texts({ c, f -> "Codice di abbinamento della segreteria: $c (anche in $f)" }, "Nell'app: Planning dei campi › Orari e prezzi › Server del circolo › Abbina.",
                { a, d -> "Server del circolo in ascolto su $a (dati in $d)" })
            "fr" -> Texts({ c, f -> "Code d'appairage du secrétariat : $c (aussi dans $f)" }, "Dans l'app : Planning des terrains › Horaires et tarifs › Serveur du club › Appairer.",
                { a, d -> "Serveur du club à l'écoute sur $a (données dans $d)" })
            "de" -> Texts({ c, f -> "Kopplungscode für das Büro: $c (auch in $f)" }, "In der App: Platzbelegung › Zeiten und Preise › Vereinsserver › Koppeln.",
                { a, d -> "Vereinsserver hört auf $a (Daten in $d)" })
            "es" -> Texts({ c, f -> "Código de emparejamiento de la secretaría: $c (también en $f)" }, "En la app: Planning de pistas › Horarios y precios › Servidor del club › Emparejar.",
                { a, d -> "Servidor del club escuchando en $a (datos en $d)" })
            "pt" -> Texts({ c, f -> "Código de emparelhamento da secretaria: $c (também em $f)" }, "Na app: Agenda das quadras › Horários e preços › Servidor do clube › Emparelhar.",
                { a, d -> "Servidor do clube à escuta em $a (dados em $d)" })
            else -> Texts({ c, f -> "Office pairing code: $c (also in $f)" }, "In the app: Court planner › Hours and prices › Club server › Pair.",
                { a, d -> "Club server listening on $a (data in $d)" })
        }
    }
}
