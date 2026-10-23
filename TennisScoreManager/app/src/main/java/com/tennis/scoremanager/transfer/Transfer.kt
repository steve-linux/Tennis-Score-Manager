// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.transfer

import com.tennis.scoremanager.data.MatchRecord
import kotlinx.serialization.Serializable
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.URL
import java.security.SecureRandom
import kotlin.concurrent.thread

/** Quello che passa da un telefono all'altro: storico, partite sospese e foto dei giocatori (JPEG in base64 per nome). */
@Serializable
data class TransferBundle(
    val version: Int = 1,
    val matches: List<MatchRecord> = emptyList(),
    val photos: Map<String, String> = emptyMap(),
    /** Foto ricordo per id della partita (JPEG in base64). */
    val matchPhotos: Map<String, List<String>> = emptyMap(),
)

/**
 * Trasferimento tra due telefoni sulla stessa rete (Wi-Fi o hotspot): chi invia apre un piccolo server http e
 * mostra un QR con l'indirizzo; chi riceve lo inquadra e scarica il pacchetto. Il QR contiene solo l'indirizzo
 * (le partite non ci starebbero) e un codice casuale: senza quel codice il server risponde 404. Il server vive
 * finché resta aperta la finestra col QR.
 */
class TransferServer(private val payload: () -> ByteArray, private val onServed: () -> Unit) {
    private var socket: ServerSocket? = null
    val token: String = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }

    /** Avvia il server su una porta libera e la restituisce. */
    fun start(): Int {
        val ss = ServerSocket().apply {
            reuseAddress = true
            bind(InetSocketAddress(0))
        }
        socket = ss
        thread(name = "tsm-transfer", isDaemon = true) {
            while (!ss.isClosed) {
                val client = runCatching { ss.accept() }.getOrNull() ?: break
                runCatching {
                    client.soTimeout = 10_000
                    client.use { c ->
                        val reader = BufferedReader(InputStreamReader(c.getInputStream(), Charsets.US_ASCII))
                        val path = reader.readLine()?.split(" ")?.getOrNull(1).orEmpty()
                        // Si leggono le intestazioni fino alla riga vuota, poi si risponde.
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (line.isEmpty()) break
                        }
                        val out = c.getOutputStream()
                        if (path == "$PATH$token") {
                            val body = payload()
                            out.write(header(200, "OK", body.size).toByteArray(Charsets.US_ASCII))
                            out.write(body)
                            out.flush()
                            onServed()
                        } else {
                            out.write(header(404, "Not Found", 0).toByteArray(Charsets.US_ASCII))
                            out.flush()
                        }
                    }
                }
            }
        }
        return ss.localPort
    }

    fun stop() {
        runCatching { socket?.close() }
        socket = null
    }

    private fun header(code: Int, text: String, length: Int) =
        "HTTP/1.1 $code $text\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: $length\r\nConnection: close\r\n\r\n"

    companion object {
        const val PATH = "/tsm-transfer/"

        fun url(host: String, port: Int, token: String) = "http://$host:$port$PATH$token"

        /** Il testo letto dal QR è un indirizzo di trasferimento di questa app? */
        fun isTransferUrl(text: String): Boolean =
            Regex("""^http://[0-9.]+:\d+${Regex.escape(PATH)}[0-9a-f]{32}$""").matches(text.trim())

        /** Scarica il pacchetto (da chiamare fuori dal thread principale). */
        fun fetch(url: String): ByteArray {
            val conn = URL(url.trim()).openConnection() as HttpURLConnection
            conn.connectTimeout = 8_000
            conn.readTimeout = 30_000
            try {
                check(conn.responseCode == 200) { "HTTP ${conn.responseCode}" }
                return conn.inputStream.use { it.readBytes() }
            } finally {
                conn.disconnect()
            }
        }
    }
}
