package com.tennis.scoremanager.ble

import java.text.Normalizer
import java.util.UUID

/**
 * Protocollo BLE condiviso con il firmware del braccialetto (TSM_Band.ino): tenere allineati gli UUID.
 *
 * Braccialetto -> telefono (notify su EVENT): [tipo, sequenza] più, dal firmware 2.0, il motivo dello spegnimento;
 * dal firmware 2.3 anche [id di accensione (4 byte, little endian), età dell'evento in decimi di secondo]: vedi [parseEvent].
 * Telefono -> braccialetto (write su DISPLAY): testo ASCII con campi separati da '|':
 *   P|<mio>|<avversario>|<servizio 0/1/2>|<intestazione>        punteggio del game (grande)
 *   G|<miei game>|<game avv>|<miei set>|<set avv>|<intestazione> riepilogo a fine game
 *   M|<riga 1>|<riga 2>|<secondi>                                messaggio
 *   I|<riga 1>|<riga 2>|<secondi>|<RRGGBB>                       "Identifica": lampeggia e suona (firmware 2.0)
 *   O|<riga 1>|<riga 2>                                          si spegne (firmware 2.0)
 *   H|1                                                          confermo gli eventi (app 2.4): prima delle notifiche
 *   K|<sequenza>                                                 conferma di un evento (solo ai firmware 2.3)
 * "mio" è sempre il giocatore che indossa il braccialetto; servizio 1 = serve lui, 2 = serve l'avversario.
 * I firmware vecchi ignorano i tipi che non conoscono.
 *
 * Conferma dei tasti (firmware 2.3 + app 2.4): il braccialetto tiene in coda KEY1/KEY2 finché l'app non li conferma
 * e suona il bip di conferma solo allora; dopo una riconnessione rimanda quelli non confermati (fino a 6,5 s dalla
 * pressione) e a 8 s li dà per persi ("NON INVIATO"). L'app conferma anche i doppioni ma li applica una volta sola
 * ([EventDedupe]). Senza "H|1" (app vecchia) il firmware 2.3 suona all'invio come prima; senza id di accensione
 * (firmware vecchio) l'app non manda conferme.
 */
object BandProtocol {
    val SERVICE: UUID = UUID.fromString("7a1e0001-5c3b-4f6e-9d2a-3e7b1c9a0f10")
    val EVENT: UUID = UUID.fromString("7a1e0002-5c3b-4f6e-9d2a-3e7b1c9a0f10")
    val DISPLAY: UUID = UUID.fromString("7a1e0003-5c3b-4f6e-9d2a-3e7b1c9a0f10")
    /** Stato batteria ogni minuto (notify): vedi [BatteryModel.parse]. Assente nei firmware vecchi. */
    val STATUS: UUID = UUID.fromString("7a1e0004-5c3b-4f6e-9d2a-3e7b1c9a0f10")
    /** Impostazioni del braccialetto (lettura, scrittura, notify): vedi [BandSettings]. Dal firmware 2.0. */
    val CONFIG: UUID = UUID.fromString("7a1e0005-5c3b-4f6e-9d2a-3e7b1c9a0f10")
    val BATTERY_SERVICE: UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
    val BATTERY_LEVEL: UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
    val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    const val EVT_POINT = 1      // KEY1 pressione corta: punto a chi indossa il braccialetto
    const val EVT_UNDO = 2       // KEY2 pressione corta: annulla l'ultimo punto
    const val EVT_POWER_OFF = 3  // il braccialetto si spegne (terzo byte: OFF_*)
    const val EVT_BATTERY = 4    // KEY1 pressione lunga: mostra la batteria (solo informativo)

    // Motivo dello spegnimento (terzo byte di EVT_POWER_OFF)
    const val OFF_KEY = 0        // KEY2 tenuto premuto
    const val OFF_IDLE = 1       // collegato ma inattivo troppo a lungo
    const val OFF_BATTERY = 2    // batteria scarica
    const val OFF_APP = 3        // l'ha chiesto l'app
    const val OFF_TIMEOUT = 4    // nessun telefono

    /** Il font del braccialetto è ASCII: niente accenti, niente '|', maiuscolo. */
    fun clean(s: String, max: Int = 18): String {
        val plain = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        return plain.uppercase().replace('|', '/').filter { it.code in 32..126 }.take(max).trim()
    }

    fun point(mine: String, theirs: String, serve: Int, header: String) =
        "P|${clean(mine, 3)}|${clean(theirs, 3)}|$serve|${clean(header)}"

    fun games(myGames: Int, theirGames: Int, mySets: Int, theirSets: Int, header: String) =
        "G|$myGames|$theirGames|$mySets|$theirSets|${clean(header)}"

    fun message(line1: String, line2: String, seconds: Int) =
        "M|${clean(line1)}|${clean(line2, 28)}|$seconds"

    /** Lampeggio a tutto schermo nel colore del giocatore (0xRRGGBB), con un bip a ogni lampo. */
    fun identify(line1: String, line2: String, seconds: Int, rgb: Int) =
        "I|${clean(line1)}|${clean(line2, 28)}|$seconds|${"%06X".format(rgb and 0xFFFFFF)}"

    fun powerOff(line1: String, line2: String) = "O|${clean(line1)}|${clean(line2, 28)}"

    /** L'app conferma gli eventi su questa connessione. Va scritto prima di attivare le notifiche di EVENT. */
    const val HELLO = "H|1"

    /** Conferma dell'evento [seq]: il braccialetto suona il bip di conferma. */
    fun ack(seq: Int) = "K|$seq"

    /** Evento da EVENT; null se troppo corto. Gli eventi dei firmware prima della 2.3 hanno [boot] null. */
    fun parseEvent(b: ByteArray): RawBandEvent? {
        if (b.size < 2) return null
        fun u(i: Int) = b[i].toInt() and 0xFF
        val boot = if (b.size >= 7) u(3).toLong() or (u(4).toLong() shl 8) or (u(5).toLong() shl 16) or (u(6).toLong() shl 24) else null
        return RawBandEvent(
            type = u(0),
            seq = u(1),
            extra = if (b.size >= 3) u(2) else null,
            boot = boot,
            ageMs = if (b.size >= 8) u(7) * 100 else 0,
        )
    }
}

/**
 * Evento così come arriva dal braccialetto. [boot]: id casuale di quell'accensione (firmware 2.3), null prima;
 * [ageMs]: da quanto è stato premuto il tasto (più di zero se rimandato dopo una riconnessione).
 */
data class RawBandEvent(val type: Int, val seq: Int, val extra: Int?, val boot: Long?, val ageMs: Int)

/**
 * Eventi già ricevuti, per braccialetto e accensione. Un evento rimandato dopo una riconnessione (la conferma
 * era andata persa) si conferma di nuovo ma non si applica una seconda volta. Il braccialetto rimanda solo
 * eventi di meno di 8 s: la finestra di 30 s basta e la sequenza (8 bit) non fa in tempo a ripetersi.
 */
class EventDedupe(private val windowMs: Long = 30_000) {
    private val seen = mutableMapOf<String, MutableMap<Int, Long>>()

    /** true la prima volta che arriva quell'evento ([now] in ms, orologio monotono). */
    fun firstTime(address: String, boot: Long, seq: Int, now: Long): Boolean {
        seen.values.forEach { m -> m.values.removeAll { now - it > windowMs } }
        seen.values.removeAll { it.isEmpty() }
        val m = seen.getOrPut("$address/$boot") { mutableMapOf() }
        if (seq in m) return false
        m[seq] = now
        return true
    }
}

/**
 * Impostazioni salvate nel braccialetto. Testo sulla caratteristica CONFIG:
 * "fw=2.0;name=TSM-1A2B;bri=20;pt=3;vol=50;flip=0;pair=30;lost=180;idle=30", dal firmware 2.2 anche ";lang=it".
 * In scrittura bastano le chiavi da cambiare; il braccialetto risponde con tutte.
 * La lingua non passa da [encode]: la manda l'app da sola ([languageConfig]) e il braccialetto la salva senza anteprima.
 */
data class BandSettings(
    val name: String = "",
    /** Luminosità del display, 5-100 %. */
    val brightness: Int = 20,
    /** Secondi di punteggio acceso dopo ogni punto (0 = non mostrarlo; il riepilogo di fine game dura 2 s in più). */
    val pointSeconds: Int = 3,
    /** Volume del cicalino, 0-100 % (0 = muto). */
    val volume: Int = 50,
    /** Display capovolto, per portare il braccialetto sull'altro polso. */
    val flip: Boolean = false,
    /** Spegnimento se all'accensione nessun telefono si collega (s). */
    val pairTimeoutS: Int = 30,
    /** Spegnimento se il telefono si scollega (s). */
    val lostTimeoutS: Int = 180,
    /** Spegnimento se collegato ma inattivo (min). */
    val idleTimeoutMin: Int = 30,
    val firmware: String = "",
    /** Lingua dei testi del braccialetto ("it", "en", ...); vuota = firmware senza lingue (prima della 2.2). */
    val lang: String = "",
) {
    fun clamped() = copy(
        name = cleanName(name),
        brightness = brightness.coerceIn(5, 100),
        pointSeconds = pointSeconds.coerceIn(0, 10),
        volume = volume.coerceIn(0, 100),
        pairTimeoutS = pairTimeoutS.coerceIn(15, 600),
        lostTimeoutS = lostTimeoutS.coerceIn(30, 1800),
        idleTimeoutMin = idleTimeoutMin.coerceIn(5, 120),
    )

    /** Testo da scrivere sulla caratteristica CONFIG (il nome solo se c'è). */
    fun encode(): String {
        val c = clamped()
        return buildList {
            if (c.name.isNotEmpty()) add("name=${c.name}")
            add("bri=${c.brightness}")
            add("pt=${c.pointSeconds}")
            add("vol=${c.volume}")
            add("flip=${if (c.flip) 1 else 0}")
            add("pair=${c.pairTimeoutS}")
            add("lost=${c.lostTimeoutS}")
            add("idle=${c.idleTimeoutMin}")
        }.joinToString(";")
    }

    companion object {
        const val NAME_MAX = 12

        /** Solo la lingua: il braccialetto la salva in silenzio (niente "IMPOSTAZIONI OK"). */
        fun languageConfig(code: String) = "lang=$code"

        /** Nome valido per il braccialetto: ASCII stampabile, senza i separatori del protocollo, max 12. */
        fun cleanName(s: String): String {
            val plain = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
            return plain.filter { it.code in 32..126 && it !in "|;=" }.trim().take(NAME_MAX).trim()
        }

        fun parse(text: String): BandSettings? {
            val map = text.split(';').mapNotNull { part ->
                val kv = part.split('=', limit = 2)
                if (kv.size == 2) kv[0].trim() to kv[1].trim() else null
            }.toMap()
            if ("bri" !in map) return null
            val d = BandSettings()
            return BandSettings(
                name = map["name"] ?: "",
                brightness = map["bri"]?.toIntOrNull() ?: d.brightness,
                pointSeconds = map["pt"]?.toIntOrNull() ?: d.pointSeconds,
                volume = map["vol"]?.toIntOrNull() ?: d.volume,
                flip = map["flip"] == "1",
                pairTimeoutS = map["pair"]?.toIntOrNull() ?: d.pairTimeoutS,
                lostTimeoutS = map["lost"]?.toIntOrNull() ?: d.lostTimeoutS,
                idleTimeoutMin = map["idle"]?.toIntOrNull() ?: d.idleTimeoutMin,
                firmware = map["fw"] ?: "",
                lang = map["lang"] ?: "",
            )
        }
    }
}
