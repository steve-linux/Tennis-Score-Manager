package com.tennis.scoremanager.ble

import java.text.Normalizer
import java.util.UUID

/**
 * Protocollo BLE condiviso con il firmware del braccialetto (TSM_Band.ino): tenere allineati gli UUID.
 *
 * Braccialetto -> telefono (notify su EVENT): [tipo, sequenza] più, dal firmware 2.0, il motivo dello spegnimento.
 * Telefono -> braccialetto (write su DISPLAY): testo ASCII con campi separati da '|':
 *   P|<mio>|<avversario>|<servizio 0/1/2>|<intestazione>        punteggio del game (grande)
 *   G|<miei game>|<game avv>|<miei set>|<set avv>|<intestazione> riepilogo a fine game
 *   M|<riga 1>|<riga 2>|<secondi>                                messaggio
 *   I|<riga 1>|<riga 2>|<secondi>|<RRGGBB>                       "Identifica": lampeggia e suona (firmware 2.0)
 *   O|<riga 1>|<riga 2>                                          si spegne (firmware 2.0)
 * "mio" è sempre il giocatore che indossa il braccialetto; servizio 1 = serve lui, 2 = serve l'avversario.
 * I firmware vecchi ignorano i tipi che non conoscono.
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
