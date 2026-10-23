// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.relay

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Due telefoni, uno per fondo campo: il principale tiene la partita, il satellite si collega alle fasce dei
 * giocatori vicini a lui e gli gira le misure. Una riga JSON per messaggio, su una connessione TCP nella rete locale
 * (Wi-Fi del circolo o hotspot del principale).
 *
 * Principale → satellite: [WELCOME] (fasce da seguire e partita in corso), [STRAPS], [MATCH], [PONG], [ACK].
 * Satellite → principale: [HELLO], [HR] (una misura), [STATUS] (come vede le fasce), [PING].
 */
@Serializable
data class RelayMsg(
    val t: String,
    /** Codice del QR: senza, il principale chiude la connessione. */
    val token: String? = null,
    /** Satellite che parla: cambia a ogni avvio, così i numeri [seq] di prima non si confondono. */
    val sat: String? = null,
    /** ID della partita del principale ("" = nessuna partita in corso). */
    val match: String? = null,
    val seq: Long? = null,
    /** Orologio del satellite all'invio del ping ([c]) e del principale alla risposta ([m]). */
    val c: Long? = null,
    val m: Long? = null,
    val addr: String? = null,
    val bpm: Int? = null,
    val contact: Boolean? = null,
    /** Ora della misura, già sull'orologio del principale. */
    val at: Long? = null,
    val straps: List<RelayStrap>? = null,
    val status: List<RelayStrapStatus>? = null,
    val name: String? = null,
) {
    companion object {
        const val HELLO = "hello"
        const val WELCOME = "welcome"
        const val STRAPS = "straps"
        const val MATCH = "match"
        const val PING = "ping"
        const val PONG = "pong"
        const val HR = "hr"
        const val ACK = "ack"
        const val STATUS = "status"
    }
}

/** Fascia che il satellite deve seguire: [side] 0 = giocatore 1, 1 = giocatore 2. */
@Serializable
data class RelayStrap(val addr: String, val name: String, val side: Int)

/** Come il satellite vede una fascia: segnale (dBm) e misure arrivate nell'ultimo minuto (%). */
@Serializable
data class RelayStrapStatus(
    val addr: String,
    val ready: Boolean,
    val bpm: Int? = null,
    val rssi: Int? = null,
    val received: Int? = null,
)

object RelayCodec {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    fun encode(m: RelayMsg): String = json.encodeToString(RelayMsg.serializer(), m)

    /** null se la riga non è un messaggio valido (si ignora, non si chiude la connessione). */
    fun decode(line: String): RelayMsg? = runCatching { json.decodeFromString(RelayMsg.serializer(), line) }.getOrNull()

    const val SCHEME = "tsm-relay://"

    fun url(host: String, port: Int, token: String) = "$SCHEME$host:$port/$token"

    /** (host, porta, codice) dal testo del QR, o null se non è un QR del telefono principale. */
    fun parseUrl(text: String): Triple<String, Int, String>? {
        val m = Regex("""^${Regex.escape(SCHEME)}([0-9.]+):(\d{1,5})/([0-9a-f]{32})$""").matchEntire(text.trim()) ?: return null
        val port = m.groupValues[2].toInt().takeIf { it in 1..65535 } ?: return null
        return Triple(m.groupValues[1], port, m.groupValues[3])
    }
}

/**
 * Scarto tra l'orologio del principale e quello del satellite, dai ping: per ogni ping si stima
 * `principale − (invio + arrivo) / 2`, e si tiene la stima del ping più veloce tra gli ultimi (meno attesa in rete,
 * meno errore).
 */
class ClockSync(private val keep: Int = 8) {
    private val samples = ArrayDeque<Pair<Long, Long>>()  // (andata e ritorno, scarto)

    fun add(sentAt: Long, masterAt: Long, receivedAt: Long) {
        val rtt = receivedAt - sentAt
        if (rtt < 0) return
        samples.addLast(rtt to masterAt - (sentAt + receivedAt) / 2)
        while (samples.size > keep) samples.removeFirst()
    }

    /** Da sommare all'ora del satellite per avere quella del principale; null finché non è arrivato nessun ping. */
    val offset: Long? get() = samples.minByOrNull { it.first }?.second

    fun clear() = samples.clear()
}

/**
 * Misure del satellite non ancora confermate dal principale: si numerano, si tengono finché il principale non
 * conferma (conferma cumulativa: "ho tutto fino a N") e dopo un'interruzione si rimandano tutte.
 * Il principale scarta i numeri già visti, quindi rimandare è sempre sicuro.
 */
class RelayOutbox<T>(private val max: Int = 20_000) {
    private val items = ArrayDeque<Pair<Long, T>>()
    private var next = 1L

    fun add(item: T): Long {
        val seq = next++
        items.addLast(seq to item)
        // Ore di misure senza principale: si perdono le più vecchie, non si riempie la memoria.
        while (items.size > max) items.removeFirst()
        return seq
    }

    fun ack(upTo: Long) {
        while (items.isNotEmpty() && items.first().first <= upTo) items.removeFirst()
    }

    fun pending(): List<Pair<Long, T>> = items.toList()

    val size: Int get() = items.size
}

/**
 * Lato principale: per ogni satellite l'ultimo numero ricevuto. Le misure arrivano in ordine (TCP); dopo una
 * riconnessione il satellite rimanda anche quelle già arrivate, che qui si riconoscono e si saltano.
 */
class RelayInbox {
    private val lastSeq = mutableMapOf<String, Long>()

    /** true se la misura è nuova (da usare), false se è un doppione. */
    fun accept(sat: String, seq: Long): Boolean {
        val last = lastSeq[sat] ?: 0L
        if (seq <= last) return false
        lastSeq[sat] = seq
        return true
    }

    fun last(sat: String): Long = lastSeq[sat] ?: 0L
}
