package com.tennis.scoremanager.ble

/** Stato inviato dal braccialetto ogni minuto: "mv=3987;chg=0;up=1234;dsp=56". */
data class BandStatus(val millivolts: Int, val charging: Boolean, val uptimeS: Long, val displayS: Long)

/**
 * Batteria LiPo del braccialetto (250 mAh): la carica si ricava dalla tensione con la curva di scarica
 * tipica (a basso carico), molto più fedele della retta 3,30-4,15 V usata da M5Unified.
 * L'autonomia si stima dal consumo reale misurato durante l'uso.
 */
object BatteryModel {

    private val curve = listOf(
        4200 to 100, 4150 to 95, 4110 to 90, 4080 to 85, 4020 to 80, 3980 to 75, 3950 to 70,
        3910 to 65, 3870 to 60, 3850 to 55, 3840 to 50, 3820 to 45, 3800 to 40, 3790 to 35,
        3770 to 30, 3750 to 25, 3730 to 20, 3710 to 15, 3690 to 10, 3610 to 5, 3270 to 0,
    )

    fun parse(text: String): BandStatus? {
        val map = text.split(';').mapNotNull { part ->
            val kv = part.split('=', limit = 2)
            if (kv.size == 2) kv[0].trim() to kv[1].trim() else null
        }.toMap()
        val mv = map["mv"]?.toIntOrNull() ?: return null
        return BandStatus(
            millivolts = mv,
            charging = map["chg"] == "1",
            uptimeS = map["up"]?.toLongOrNull() ?: 0,
            displayS = map["dsp"]?.toLongOrNull() ?: 0,
        )
    }

    /** Percentuale di carica (0-100) dalla tensione in mV. */
    fun soc(mv: Int): Int {
        if (mv >= curve.first().first) return 100
        if (mv <= curve.last().first) return 0
        for (i in 0 until curve.lastIndex) {
            val (vHi, pHi) = curve[i]
            val (vLo, pLo) = curve[i + 1]
            if (mv in vLo..vHi) return pLo + (mv - vLo) * (pHi - pLo) / (vHi - vLo)
        }
        return 0
    }

    /**
     * Ore di autonomia rimaste, dalla retta dei minimi quadrati sui campioni (tempo in ms, carica %).
     * Servono almeno 20 minuti di dati e un calo misurabile, altrimenti null.
     */
    fun hoursLeft(samples: List<Pair<Long, Int>>): Double? {
        if (samples.size < 3) return null
        val t0 = samples.first().first
        val span = samples.last().first - t0
        if (span < 20 * 60_000L) return null
        val xs = samples.map { (it.first - t0) / 3_600_000.0 }
        val ys = samples.map { it.second.toDouble() }
        val mx = xs.average()
        val my = ys.average()
        val den = xs.sumOf { (it - mx) * (it - mx) }
        if (den <= 0.0) return null
        val slope = xs.indices.sumOf { (xs[it] - mx) * (ys[it] - my) } / den // % all'ora (negativo)
        if (slope >= -0.5) return null // calo troppo piccolo per stimare
        val now = my + slope * (xs.last() - mx)
        return (now / -slope).coerceAtLeast(0.0)
    }
}
