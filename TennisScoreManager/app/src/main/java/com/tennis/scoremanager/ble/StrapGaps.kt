// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.ble

/** Stacchi di una fascia: quante volte il battito non è arrivato per più di [StrapGaps.GAP_MS] e quanti secondi in tutto. */
data class GapStats(val drops: Int, val lostS: Int)

/**
 * Conta i buchi tra una misura e l'altra di una fascia, da qualunque telefono arrivino. Il Bluetooth LE ritrasmette
 * i pacchetti persi: una misura o arriva o la fascia si è staccata, quindi i buchi sono il dato che dice se la
 * fascia è troppo lontana. Le fasce mandano circa una misura al secondo: un buco di 2 secondi capita anche
 * da vicino, da 3 in su è uno stacco.
 */
class StrapGaps {
    private var last = 0L
    private var drops = 0
    private var lostMs = 0L

    /** Misura arrivata all'ora [atMs] (anche fuori ordine: una più vecchia dell'ultima non conta). */
    fun onReading(atMs: Long) {
        if (last != 0L && atMs > last) {
            val gap = atMs - last
            if (gap > GAP_MS) {
                drops++
                lostMs += gap - EXPECTED_MS
            }
        }
        if (atMs > last) last = atMs
    }

    /** null prima della prima misura. Uno stacco ancora in corso conta già, coi secondi fino a [nowMs]. */
    fun stats(nowMs: Long): GapStats? {
        if (last == 0L) return null
        val open = nowMs - last
        return if (open > GAP_MS) {
            GapStats(drops + 1, ((lostMs + open - EXPECTED_MS) / 1000).toInt())
        } else {
            GapStats(drops, (lostMs / 1000).toInt())
        }
    }

    fun reset() {
        last = 0L
        drops = 0
        lostMs = 0L
    }

    companion object {
        const val GAP_MS = 3_000L
        /** Il secondo che ci si aspetta comunque tra una misura e l'altra non è perso. */
        private const val EXPECTED_MS = 1_000L
    }
}
