// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.remote

enum class Gesture { POINT, UNDO }

/**
 * Riconosce i gesti di un telecomandino a un tasto, uno per dispositivo ([dev]):
 * - un clic = [Gesture.POINT], dato dopo [doubleMs] se non arriva un secondo clic;
 * - doppio clic = [Gesture.UNDO], subito alla seconda pressione (il punto del primo clic non si dà);
 * - pressione lunga ([longMs]) = [Gesture.UNDO], appena scade se il telecomando ripete il tasto, altrimenti al rilascio;
 * - tasto per annullare (`undoKey`, l'altro tasto dei telecomandi a due tasti) = [Gesture.UNDO] subito; se c'è un clic
 *   in attesa annulla quello, che non diventa punto (come chi si corregge subito dopo aver premuto).
 *
 * Molti telecomandi da selfie mandano due tasti per ogni pressione (es. VOLUME SU + INVIO): i tasti che arrivano
 * entro [mergeMs] dalla fine della pressione precedente, o mentre un altro tasto è ancora giù, fanno parte della
 * stessa pressione. I tempi sono in millisecondi (SystemClock.uptimeMillis, come KeyEvent).
 */
class PressDetector(
    private val doubleMs: Long = 450,
    private val longMs: Long = 900,
    private val mergeMs: Long = 60,
) {
    private class Dev {
        val held = mutableSetOf<Int>()
        var downAt = 0L
        var lastUpAt = Long.MIN_VALUE / 2
        /** La pressione in corso ha già dato il suo gesto (doppio clic o pressione lunga): il rilascio non conta. */
        var consumed = false
        /** Clic singolo in attesa: diventa un punto a questa ora se nel frattempo non arriva il secondo. */
        var pointAt: Long? = null
    }

    private val devs = mutableMapOf<String, Dev>()

    /** Tasto premuto, anche ripetuto dal telecomando tenendolo giù. [undoKey] = è il tasto per annullare. */
    fun down(dev: String, key: Int, t: Long, undoKey: Boolean = false): Gesture? {
        val d = devs.getOrPut(dev) { Dev() }
        if (key in d.held) {
            // Ripetizione del tasto tenuto giù.
            if (!d.consumed && t - d.downAt >= longMs) {
                d.consumed = true
                d.pointAt = null
                return Gesture.UNDO
            }
            return null
        }
        val samePress = d.held.isNotEmpty() || t - d.lastUpAt < mergeMs
        d.held += key
        if (samePress) return null
        d.downAt = t
        d.consumed = false
        val pending = d.pointAt
        if (undoKey) {
            d.consumed = true
            d.pointAt = null
            return if (pending != null && t <= pending) null else Gesture.UNDO
        }
        if (pending != null && t <= pending) {
            d.pointAt = null
            d.consumed = true
            return Gesture.UNDO
        }
        return null
    }

    /** Tasto rilasciato. Un rilascio senza pressione (es. tasto premuto prima dell'associazione) non conta. */
    fun up(dev: String, key: Int, t: Long): Gesture? {
        val d = devs[dev] ?: return null
        if (!d.held.remove(key)) return null
        if (d.held.isNotEmpty()) return null
        d.lastUpAt = t
        if (d.consumed) return null
        if (t - d.downAt >= longMs) {
            d.consumed = true
            return Gesture.UNDO
        }
        d.pointAt = t + doubleMs
        return null
    }

    /** Clic singoli scaduti: diventano punti. */
    fun due(t: Long): List<String> {
        val out = mutableListOf<String>()
        for ((dev, d) in devs) {
            val at = d.pointAt ?: continue
            if (t >= at) {
                d.pointAt = null
                out += dev
            }
        }
        return out
    }

    /** Prima ora a cui chiamare [due] (null = nessun clic in attesa). */
    fun nextDeadline(): Long? = devs.values.mapNotNull { it.pointAt }.minOrNull()

    /** Dimentica tutto (telecomando tolto, partita finita...). */
    fun reset() = devs.clear()
}
