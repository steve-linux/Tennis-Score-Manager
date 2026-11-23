// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.arena

import com.tennis.scoremanager.ble.BandProtocol
import com.tennis.scoremanager.ble.RawStroke
import com.tennis.scoremanager.ble.RawSwing
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * TSM Arena: tennis sulla TV coi braccialetti come racchette. Il gioco è la pagina assets/arena.html, servita dal
 * server del tabellone ([com.tennis.scoremanager.tv.TvServer], /arena); l'app le manda impostazioni e colpi su
 * /arena/events. Qui le impostazioni salvate e i messaggi per la pagina (JSON con il campo "t" = tipo).
 */
@Serializable
data class ArenaSettings(
    /** Giocatore 1 (braccialetto P1): PlayerProfile.id, null = tu. */
    val p1: String? = null,
    /** Giocatore 2 (braccialetto P2) nella partita a due: PlayerProfile.id, null = Giocatore 2. */
    val p2: String? = null,
    /** Due giocatori, un braccialetto ciascuno; altrimenti Giocatore 1 contro il telefono (CPU). */
    val twoPlayers: Boolean = false,
    /** Livello della CPU: 1 facile, 2 normale, 3 difficile. */
    val level: Int = 2,
    /** Game per vincere la partita. */
    val games: Int = 3,
    /** Il punto vale solo col colpo giusto (dritto o rovescio), se il braccialetto lo riconosce. */
    val strict: Boolean = true,
    /** Voce dell'arbitro sulla TV. */
    val voice: Boolean = true,
    /** Ritardo dell'immagine della TV: 0 basso (monitor, modalità gioco), 1 medio, 2 alto. */
    val tvLag: Int = 1,
    /** Grafica: 0 = 2D, 1 = 3D semplice (low-poly), 2 = 3D realistica. La TV scende da sola se va a scatti. */
    val graphics: Int = 2,
    /** Volume del gioco sulla TV, 0-100: suoni e voce dell'arbitro. */
    val volume: Int = 80,
)

object Arena {
    /**
     * Avvio dell'app sul telefono: cambia a ogni avvio (e quindi a ogni aggiornamento). La TV rimasta aperta se ne
     * accorge e ricarica la pagina, sennò continuerebbe con il gioco della versione di prima.
     */
    val REV: Long = System.currentTimeMillis()

    /** Partite possibili: game per vincere. */
    val GAME_CHOICES = listOf(1, 3, 5)

    /** Ritardo dell'immagine della TV, in ms, per [ArenaSettings.tvLag]. */
    fun tvLagMs(level: Int): Int = when (level) {
        0 -> 20
        2 -> 140
        else -> 70
    }

    /**
     * Forza del colpo 0,15..1 dal picco di rotazione dei primi ms: sotto la soglia del firmware (400 °/s) non arriva
     * niente, verso i 1600 °/s è già un colpo pieno.
     */
    fun strength(peakGyroDps: Int): Double = ((peakGyroDps - 400) / 1200.0).coerceIn(0.15, 1.0)

    /** Impostazioni per la pagina: le riceve per prime ogni TV che si collega. */
    fun config(s: ArenaSettings, lang: String, name1: String, name2: String, band1: Boolean, band2: Boolean): String =
        buildJsonObject {
            put("t", "cfg")
            put("lang", lang)
            put("level", s.level.coerceIn(1, 3))
            put("games", s.games.coerceIn(1, 9))
            put("strict", s.strict)
            put("voice", s.voice)
            put("vol", s.volume.coerceIn(0, 100))
            put("rev", REV)
            put("lagMs", tvLagMs(s.tvLag))
            put("two", s.twoPlayers)
            put("gfx", s.graphics.coerceIn(0, 2))
            putJsonObject("p1") {
                put("name", name1)
                put("band", band1)
            }
            putJsonObject("p2") {
                put("name", name2)
                put("band", band2)
            }
        }.toString()

    /** Inizio di un colpo: [side] 1 o 2. */
    fun swing(side: Int, sw: RawSwing): String = buildJsonObject {
        put("t", "swing")
        put("side", side)
        put("s", Math.round(strength(sw.peakGyroDps) * 100) / 100.0)
        put("gyro", sw.peakGyroDps)
        put("after", sw.afterMs)
    }.toString()

    /** Colpo riconosciuto (arriva ~0,4 s dopo l'inizio): "k" come BandProtocol.STROKE_*, 255 = sconosciuto. */
    fun stroke(side: Int, st: RawStroke): String = buildJsonObject {
        put("t", "stroke")
        put("side", side)
        put("k", st.stroke)
        put("conf", st.confidence)
        put("gyro", st.peakGyroDps)
    }.toString()

    /** Tasto del braccialetto: KEY1 avvia o fa la rivincita, KEY2 mette in pausa. */
    fun key(side: Int, type: Int): String = buildJsonObject {
        put("t", "key")
        put("side", side)
        put("k", if (type == BandProtocol.EVT_UNDO) 2 else 1)
    }.toString()

    /** Comando dal telefono: [action] "pause", "resume" o "start" (partita nuova da zero). */
    fun control(action: String): String = buildJsonObject {
        put("t", "ctl")
        put("a", action)
    }.toString()

    /** Il gioco si chiude dal telefono: la TV torna alla schermata d'attesa. */
    fun closed(): String = buildJsonObject { put("t", "closed") }.toString()
}
