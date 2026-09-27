package com.tennis.scoremanager.ble

import java.text.Normalizer
import java.util.UUID

/**
 * Protocollo BLE condiviso con il firmware del braccialetto (TSM_Band.ino): tenere allineati gli UUID.
 *
 * Braccialetto -> telefono (notify su EVENT): 2 byte [tipo, sequenza].
 * Telefono -> braccialetto (write su DISPLAY): testo ASCII con campi separati da '|':
 *   P|<mio>|<avversario>|<servizio 0/1/2>|<intestazione>        punteggio del game (grande)
 *   G|<miei game>|<game avv>|<miei set>|<set avv>|<intestazione> riepilogo a fine game
 *   M|<riga 1>|<riga 2>|<secondi>                                messaggio
 * "mio" è sempre il giocatore che indossa il braccialetto; servizio 1 = serve lui, 2 = serve l'avversario.
 */
object BandProtocol {
    val SERVICE: UUID = UUID.fromString("7a1e0001-5c3b-4f6e-9d2a-3e7b1c9a0f10")
    val EVENT: UUID = UUID.fromString("7a1e0002-5c3b-4f6e-9d2a-3e7b1c9a0f10")
    val DISPLAY: UUID = UUID.fromString("7a1e0003-5c3b-4f6e-9d2a-3e7b1c9a0f10")
    val BATTERY_SERVICE: UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
    val BATTERY_LEVEL: UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
    val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    const val EVT_POINT = 1      // KEY1 pressione corta: punto a chi indossa il braccialetto
    const val EVT_UNDO = 2       // KEY2 pressione corta: annulla l'ultimo punto
    const val EVT_POWER_OFF = 3  // KEY2 pressione lunga: il braccialetto si spegne
    const val EVT_BATTERY = 4    // KEY1 pressione lunga: mostra la batteria (solo informativo)

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
        "M|${clean(line1)}|${clean(line2)}|$seconds"
}
