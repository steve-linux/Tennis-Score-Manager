// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.ble

import java.util.UUID

/** Una misura della fascia: [contact] null se la fascia non sa dire se tocca la pelle, [rrMs] gli intervalli tra i battiti. */
data class HeartMeasurement(val bpm: Int, val contact: Boolean?, val rrMs: List<Int> = emptyList()) {
    /** Da tenere: un battito plausibile e la fascia sulla pelle (o una fascia che non lo dice). */
    val valid: Boolean get() = bpm in 30..240 && contact != false
}

/**
 * Servizio cardio standard del Bluetooth (Heart Rate Service 0x180D): lo usano tutte le fasce toraciche
 * (Coospo, Magene, Polar, Garmin, Wahoo...), quindi niente codice per una marca sola.
 */
object HeartRateProtocol {
    private fun uuid16(v: Int): UUID = UUID.fromString(String.format("%08x-0000-1000-8000-00805f9b34fb", v))

    val SERVICE: UUID = uuid16(0x180D)
    val MEASUREMENT: UUID = uuid16(0x2A37)
    val BATTERY_SERVICE: UUID = uuid16(0x180F)
    val BATTERY_LEVEL: UUID = uuid16(0x2A19)
    val CCCD: UUID = uuid16(0x2902)

    /**
     * Heart Rate Measurement: un byte di flag, poi il battito (8 o 16 bit), l'energia (16 bit, se c'è) e gli
     * intervalli RR (16 bit ciascuno, in 1/1024 di secondo). null se il pacchetto è troppo corto.
     */
    fun parse(b: ByteArray): HeartMeasurement? {
        if (b.size < 2) return null
        val flags = b[0].toInt() and 0xFF
        fun u16(i: Int) = (b[i].toInt() and 0xFF) or ((b[i + 1].toInt() and 0xFF) shl 8)
        var i = 1
        val bpm = if (flags and 0x01 != 0) {
            if (b.size < 3) return null
            u16(1).also { i = 3 }
        } else {
            (b[1].toInt() and 0xFF).also { i = 2 }
        }
        // Bit 2 = la fascia sa dire se tocca la pelle, bit 1 = la tocca.
        val contact = if (flags and 0x04 != 0) flags and 0x02 != 0 else null
        if (flags and 0x08 != 0) i += 2  // energia spesa: non serve
        val rr = mutableListOf<Int>()
        if (flags and 0x10 != 0) {
            while (i + 1 < b.size) {
                rr += Math.round(u16(i) * 1000.0 / 1024).toInt()
                i += 2
            }
        }
        return HeartMeasurement(bpm, contact, rr)
    }
}
