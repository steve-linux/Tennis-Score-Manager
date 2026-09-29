package com.tennis.scoremanager.ble

/**
 * Stato inviato dal braccialetto ogni minuto: "mv=3987;chg=0;up=1234;dsp=56", dal firmware 2.1 anche
 * ";usb=5010;full=0;pct=71". [charging] = alimentato dal cavo USB (dal 2.1 anche a carica completa).
 */
data class BandStatus(
    val millivolts: Int,
    val charging: Boolean,
    val uptimeS: Long,
    val displayS: Long,
    /** Tensione USB in mV (0 = senza cavo); null = firmware prima della 2.1. */
    val usbMv: Int? = null,
    /** Carica completa, col cavo ancora collegato. */
    val full: Boolean = false,
    /** Percentuale mostrata dal braccialetto: in carica è quella della carica (la tensione lì è falsata). */
    val percent: Int? = null,
)

/** Consumo medio stimato (mA) diviso per voce; [measured] = la base viene da una misura sul campo. */
data class PowerEstimate(val baseMa: Double, val displayMa: Double, val soundMa: Double, val measured: Boolean) {
    val totalMa: Double get() = baseMa + displayMa + soundMa
    /** Ore di autonomia da carica piena. */
    val hoursFull: Double get() = BatteryModel.CAPACITY_MAH / totalMa
    /** Ore di autonomia con la carica indicata. */
    fun hoursAt(percent: Int): Double = hoursFull * percent.coerceIn(0, 100) / 100.0
}

/**
 * Batteria LiPo del braccialetto (250 mAh): la carica si ricava dalla tensione con la curva di scarica
 * tipica (a basso carico), molto più fedele della retta 3,30-4,15 V usata da M5Unified.
 * L'autonomia si stima dal consumo reale misurato durante l'uso.
 */
object BatteryModel {

    const val CAPACITY_MAH = 250.0

    /**
     * ESP32-S3 a 80 MHz con il Bluetooth collegato e il display spento. Il core Arduino è compilato senza
     * gestione del risparmio energetico (niente light sleep col Bluetooth acceso), quindi questa è la voce
     * che pesa di più. Valore stimato: appena c'è una misura sul campo si usa quella ([baseFromMeasure]).
     */
    const val BASE_MA = 35.0

    // Uso tipico in partita per ogni braccialetto: ~60 punti e ~10 game all'ora, ~1 minuto di messaggi,
    // ~40 bip (i tasti premuti da chi lo indossa più gli avvisi).
    const val POINTS_PER_HOUR = 60
    const val GAMES_PER_HOUR = 10
    const val MESSAGE_S_PER_HOUR = 60
    const val BEEPS_PER_HOUR = 40

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
            usbMv = map["usb"]?.toIntOrNull(),
            full = map["full"] == "1",
            percent = map["pct"]?.toIntOrNull()?.takeIf { it in 0..100 },
        )
    }

    /** Percentuale da mostrare: in carica quella del braccialetto (firmware 2.1), altrimenti dalla tensione. */
    fun shownPercent(st: BandStatus): Int = when {
        st.full -> 100
        st.charging && st.percent != null -> st.percent
        else -> soc(st.millivolts)
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
     * Calo della carica in % all'ora (positivo), dalla retta dei minimi quadrati sui campioni (tempo in ms, carica %).
     * Servono almeno 20 minuti di dati e un calo misurabile, altrimenti null.
     */
    fun drainPerHour(samples: List<Pair<Long, Int>>): Double? {
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
        return -slope
    }

    /** Ore di autonomia rimaste secondo il calo misurato (null finché non si può stimare). */
    fun hoursLeft(samples: List<Pair<Long, Int>>): Double? {
        val drain = drainPerHour(samples) ?: return null
        val xs = samples.map { (it.first - samples.first().first) / 3_600_000.0 }
        val my = samples.map { it.second.toDouble() }.average()
        val now = my - drain * (xs.last() - xs.average())
        return (now / drain).coerceAtLeast(0.0)
    }

    /** Display: controller più retroilluminazione, in mA mentre è acceso. */
    fun displayOnMa(brightness: Int): Double = 3.0 + 0.25 * brightness.coerceIn(0, 100)

    /** Frazione di tempo col display acceso in partita (punteggio dopo ogni punto, riepilogo a fine game, messaggi). */
    fun displayDuty(pointSeconds: Int): Double {
        val shown = if (pointSeconds <= 0) 0 else POINTS_PER_HOUR * pointSeconds + GAMES_PER_HOUR * (pointSeconds + 2)
        return (shown + MESSAGE_S_PER_HOUR) / 3600.0
    }

    /** Cicalino: codec e amplificatore restano accesi ~1,6 s per bip. */
    fun soundMa(volume: Int): Double =
        if (volume <= 0) 0.0 else BEEPS_PER_HOUR * 1.6 / 3600.0 * (15.0 + 0.6 * volume.coerceIn(0, 100))

    /** Consumo medio con le impostazioni scelte; [measuredBaseMa] sostituisce la base stimata se c'è. */
    fun estimate(s: BandSettings, measuredBaseMa: Double? = null): PowerEstimate = PowerEstimate(
        baseMa = measuredBaseMa ?: BASE_MA,
        displayMa = displayOnMa(s.brightness) * displayDuty(s.pointSeconds),
        soundMa = soundMa(s.volume),
        measured = measuredBaseMa != null,
    )

    /**
     * Consumo di base ricavato da una misura: calo % all'ora × capacità, meno il display
     * (acceso per [displayDuty] del tempo, misurato dal braccialetto) e il cicalino.
     */
    fun baseFromMeasure(drainPctPerHour: Double, displayDuty: Double, s: BandSettings): Double {
        val total = drainPctPerHour * CAPACITY_MAH / 100.0
        return (total - displayOnMa(s.brightness) * displayDuty - soundMa(s.volume)).coerceIn(10.0, 150.0)
    }

    /** "~7 h 10 min" oppure "~40 min". */
    fun formatHours(h: Double): String {
        val min = Math.round(h * 60).toInt()
        return when {
            min < 60 -> "~$min min"
            min % 60 == 0 || min >= 600 -> "~${Math.round(h)} h"
            else -> "~${min / 60} h ${min % 60} min"
        }
    }
}
