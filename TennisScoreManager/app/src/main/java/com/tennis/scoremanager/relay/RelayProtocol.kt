// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.relay

import com.tennis.scoremanager.ble.BandStatus
import com.tennis.scoremanager.ble.RawStroke
import com.tennis.scoremanager.video.PointMark
import com.tennis.scoremanager.video.ScoreOverlay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Due telefoni, uno per fondo campo: il principale tiene la partita, il satellite si collega alle fasce dei
 * giocatori vicini a lui e gli gira le misure. Una riga JSON per messaggio, su una connessione TCP nella rete locale
 * (Wi-Fi del circolo o hotspot del principale).
 *
 * Principale → satellite: [WELCOME] (fasce e braccialetti da seguire, partita in corso), [STRAPS], [BANDS], [MATCH],
 * [BAND_OUT] (punteggio, comando o impostazioni per un braccialetto), [PONG], [ACK].
 * Satellite → principale: [HELLO], [HR] (una misura), [BAND_EVT] (tasto), [STROKE] (colpo), [BAND_STATUS]
 * (batteria di un braccialetto), [STATUS] (come vede fasce e braccialetti), [PING].
 * [HR], [BAND_EVT] e [STROKE] hanno un numero progressivo e il principale li conferma con [ACK].
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
    /** Braccialetti da seguire (stesso formato delle fasce) e come li vede il satellite. */
    val bands: List<RelayStrap>? = null,
    val bandStatus: List<RelayBandStatus>? = null,
    /** Tasto del braccialetto: tipo (BandProtocol.EVT_*), motivo dello spegnimento, accensione e numero (firmware 2.3). */
    val evt: Int? = null,
    val reason: Int? = null,
    val boot: Long? = null,
    val bseq: Int? = null,
    val stroke: RawStroke? = null,
    val battery: BandStatus? = null,
    /** Per il braccialetto: testo da scrivere e tipo ("show", "cmd", "cfg"). */
    val payload: String? = null,
    val kind: String? = null,
    /** Indirizzo (host:porta) del tabellone del principale, da mostrare sul satellite. */
    val tv: String? = null,
    /** "camera" = telefono-telecamera: riceve il punteggio, non segue fasce e braccialetti. */
    val role: String? = null,
    /** Punteggio per la grafica della telecamera e punto appena giocato (con l'ora del principale). */
    val score: ScoreOverlay? = null,
    val point: PointMark? = null,
    /** Moviola: richiesta [rid] di [rlen] secondi; pezzi del video in base64 ([data]), file [file] di [files], parte [part] di [parts]. */
    val rid: String? = null,
    val rlen: Int? = null,
    val data: String? = null,
    val file: Int? = null,
    val files: Int? = null,
    val part: Int? = null,
    val parts: Int? = null,
    /** Moviola: -1 = dall'ultimo punto segnato a adesso, 0 = l'ultimo segnato, 1 = quello prima (null = ultimi [rlen] s). */
    val back: Int? = null,
    /** Telecamera comandata dall'arbitro: comando ([CamCmd]) e livello di zoom per [CamCmd.ZOOM]. */
    val cmd: String? = null,
    val zoom: Float? = null,
    /** Telecamera → principale: come sta (registrazione, diretta, zoom, batteria, spazio). */
    val cam: CamState? = null,
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
        const val BANDS = "bands"
        const val BAND_EVT = "bevt"
        const val STROKE = "stroke"
        const val BAND_STATUS = "bstat"
        const val BAND_OUT = "bout"
        const val TV = "tv"
        const val SCORE = "score"
        const val POINT = "point"
        const val ROLE_CAMERA = "camera"
        const val CHALLENGE = "challenge"
        const val REPLAY = "replay"
        const val REPLAY_FAIL = "replayfail"
        /** Principale → telecamera: un comando ([cmd]). */
        const val CAM_CMD = "camcmd"
        /** Telecamera → principale: lo stato ([cam]), a ogni cambio e ogni pochi secondi. */
        const val CAM_STATE = "camstate"
        /** Telecamera → principale: un fotogramma dell'inquadratura, JPEG in base64 ([data]), solo se chiesto. */
        const val CAM_FRAME = "camframe"
    }
}

/** Fascia che il satellite deve seguire: [side] 0 = giocatore 1, 1 = giocatore 2. */
@Serializable
data class RelayStrap(val addr: String, val name: String, val side: Int)

/** Come il satellite vede una fascia: collegata, battito e segnale (dBm). */
@Serializable
data class RelayStrapStatus(
    val addr: String,
    val ready: Boolean,
    val bpm: Int? = null,
    val rssi: Int? = null,
)

/** Come il satellite vede un braccialetto: collegato a lui e la carica. */
@Serializable
data class RelayBandStatus(val addr: String, val ready: Boolean, val battery: Int? = null)

/**
 * Comandi della telecamera dal telefono dell'arbitro. [PREVIEW] va ripetuto finché si guarda l'inquadratura: se non
 * arriva più per [PREVIEW_TTL_MS] la telecamera smette di mandare fotogrammi (anche se il principale sparisce).
 */
object CamCmd {
    const val PREVIEW = "preview"
    const val PREVIEW_OFF = "preview_off"
    const val REC_START = "rec_start"
    const val REC_STOP = "rec_stop"
    const val LIVE_START = "live_start"
    const val LIVE_STOP = "live_stop"
    const val OVERLAY_ON = "overlay_on"
    const val OVERLAY_OFF = "overlay_off"
    const val ZOOM = "zoom"
    /** Obiettivo successivo (principale → grandangolo → …). */
    const val LENS_NEXT = "lens_next"
    /** Livella dell'assistente inquadratura accesa o spenta. */
    const val ASSIST_ON = "assist_on"
    const val ASSIST_OFF = "assist_off"
    const val PREVIEW_TTL_MS = 8_000L
}

/**
 * Stato della telecamera per il telefono dell'arbitro. [recMs]: da quanto registra; [live] 0 spenta, 1 in
 * collegamento, 2 in onda; [liveReady] se la chiave della diretta c'è; zoom attuale e limiti; batteria in % e spazio
 * libero in MB (null se non si sanno).
 */
@Serializable
data class CamState(
    val recording: Boolean = false,
    val recMs: Long = 0L,
    val live: Int = 0,
    val liveReady: Boolean = false,
    val overlay: Boolean = false,
    val zoom: Float = 1f,
    val zoomMin: Float = 1f,
    val zoomMax: Float = 1f,
    val battery: Int? = null,
    val freeMb: Long? = null,
    /** Ultimo messaggio della telecamera (salvato, diretta non riuscita…), da mostrare anche all'arbitro. */
    val message: String? = null,
    /** Obiettivo in uso ([lens] da 0, 0 = principale) su [lenses]. */
    val lens: Int = 0,
    val lenses: Int = 1,
    /**
     * Assistente inquadratura acceso: gradi verso il basso ([tilt]) e di rotazione oraria ([roll]) del telefono, e
     * inclinazione consigliata ([tiltTarget]). null = livella spenta.
     */
    val tilt: Float? = null,
    val roll: Float? = null,
    val tiltTarget: Float? = null,
)

object RelayCodec {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    fun encode(m: RelayMsg): String = json.encodeToString(RelayMsg.serializer(), m)

    /** null se la riga non è un messaggio valido (si ignora, non si chiude la connessione). */
    fun decode(line: String): RelayMsg? = runCatching { json.decodeFromString(RelayMsg.serializer(), line) }.getOrNull()

    const val SCHEME = "tsm-relay://"

    /**
     * Indirizzo del principale per il QR; con [hotspot] anche nome e password della sua rete, così il satellite ci
     * si collega da solo.
     */
    fun url(host: String, port: Int, token: String, hotspot: HotspotInfo? = null): String {
        val base = "$SCHEME$host:$port/$token"
        if (hotspot == null) return base
        return base + "?s=" + enc(hotspot.ssid) + (hotspot.pass?.let { "&p=" + enc(it) } ?: "")
    }

    /** Il testo del QR, o null se non è un QR del telefono principale. */
    fun parseUrl(text: String): RelayTarget? {
        val m = Regex("""^${Regex.escape(SCHEME)}([0-9.]+):(\d{1,5})/([0-9a-f]{32})(?:\?(.*))?$""").matchEntire(text.trim()) ?: return null
        val port = m.groupValues[2].toInt().takeIf { it in 1..65535 } ?: return null
        val query = m.groupValues[4].split("&").filter { "=" in it }
            .associate { it.substringBefore("=") to runCatching { dec(it.substringAfter("=")) }.getOrNull() }
        val hotspot = query["s"]?.takeIf { it.isNotEmpty() }?.let { HotspotInfo(it, query["p"]?.takeIf { p -> p.isNotEmpty() }) }
        return RelayTarget(m.groupValues[1], port, m.groupValues[3], hotspot)
    }

    private fun enc(v: String) = java.net.URLEncoder.encode(v, "UTF-8")

    private fun dec(v: String) = java.net.URLDecoder.decode(v, "UTF-8")
}

/** Dove sta il principale: indirizzo, porta, codice e (se l'ha creata lui) la sua rete. */
data class RelayTarget(val host: String, val port: Int, val token: String, val hotspot: HotspotInfo? = null)

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
