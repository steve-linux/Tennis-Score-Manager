// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import com.tennis.scoremanager.model.Side
import kotlinx.serialization.Serializable
import java.util.Locale

/** Tipo di corda: decide le sensazioni più del marchio. */
@Serializable
enum class StringType { MONO, MULTI, SYNTHETIC, GUT, HYBRID }

/**
 * Un'incordatura della racchetta. Tensioni in kg (verticali e orizzontali), calibro in mm; null = non detto.
 * Nell'ibrido [crossModel] è la corda delle orizzontali.
 */
@Serializable
data class Stringing(
    val id: String,
    /** Giorno dell'incordatura (ms). */
    val date: Long,
    val type: StringType = StringType.MONO,
    val brand: String = "",
    val model: String = "",
    val gaugeMm: Double? = null,
    val mainsKg: Double? = null,
    val crossesKg: Double? = null,
    val crossModel: String = "",
    val notes: String = "",
)

/**
 * Una racchetta fisica (non il modello: due racchette uguali hanno incordature, pesi e ore diverse). [name] è il
 * soprannome per distinguerle ("A", "Quella nuova"). Peso e bilanciamento come li dichiara il produttore o come
 * li ha misurati il giocatore; [headCm2] la superficie del piatto, [pattern] lo schema ("16x19").
 * [stringings]: tutte le incordature, l'ultima è quella montata. [checkHours]: dopo quante ore di gioco
 * ricordare di controllare le corde (scelta del giocatore: il tempo da solo non dice la tensione rimasta).
 */
@Serializable
data class Racket(
    val id: String,
    val name: String = "",
    val brand: String = "",
    val model: String = "",
    val weightG: Int? = null,
    val balanceMm: Int? = null,
    val headCm2: Int? = null,
    val pattern: String = "",
    /** Di chi è (nome della pagina 1): la si propone a lui. Vuoto = di nessuno in particolare. */
    val owner: String = "",
    val stringings: List<Stringing> = emptyList(),
    val checkHours: Int = DEFAULT_CHECK_HOURS,
    val notes: String = "",
    /** Racchetta messa da parte: non si propone più, ma le sue partite restano nel confronto. */
    val retired: Boolean = false,
    val createdAt: Long = 0L,
) {
    /** L'incordatura montata adesso (la più recente). */
    val current: Stringing? get() = stringings.maxByOrNull { it.date }

    /** "A · Babolat Pure Drive", oppure solo il modello se non ha soprannome. */
    val label: String
        get() {
            val m = listOf(brand, model).filter { it.isNotBlank() }.joinToString(" ")
            return when {
                name.isBlank() -> m
                m.isBlank() -> name
                else -> "$name · $m"
            }
        }

    /** La configurazione di adesso, da salvare con la partita o l'allenamento. */
    fun use(): RacketUse = useAt(Long.MAX_VALUE)

    /**
     * La configurazione com'era il giorno [at]: l'incordatura montata allora (l'ultima fatta entro quel giorno;
     * se sono tutte dopo, la prima). Serve ad associare la racchetta a una partita già giocata.
     */
    fun useAt(at: Long): RacketUse = RacketUse(
        racketId = id, name = name, brand = brand, model = model, weightG = weightG, balanceMm = balanceMm,
        headCm2 = headCm2, pattern = pattern,
        stringing = stringings.filter { it.date <= at }.maxByOrNull { it.date } ?: stringings.minByOrNull { it.date },
    )

    companion object {
        const val DEFAULT_CHECK_HOURS = 20
    }
}

/**
 * Sensazioni dopo la sessione, da 1 a 5 (0 = non detto). [fatigue]: 5 = molto stanco, quindi più basso è meglio.
 */
@Serializable
data class Feel(
    val control: Int = 0,
    val comfort: Int = 0,
    val power: Int = 0,
    val fatigue: Int = 0,
) {
    val isEmpty: Boolean get() = control == 0 && comfort == 0 && power == 0 && fatigue == 0
}

/**
 * La racchetta come era quando si è giocato: una copia, non un riferimento, così la partita resta giusta anche se
 * poi si cambia la racchetta o la si reincorda. [feel]: le sensazioni di chi l'ha usata in quella sessione.
 */
@Serializable
data class RacketUse(
    val racketId: String,
    val name: String = "",
    val brand: String = "",
    val model: String = "",
    val weightG: Int? = null,
    val balanceMm: Int? = null,
    val headCm2: Int? = null,
    val pattern: String = "",
    val stringing: Stringing? = null,
    val feel: Feel? = null,
) {
    val label: String
        get() = Racket(racketId, name, brand, model).label
}

/** Modelli noti, per riempire la scheda senza cercare i dati: valori del produttore (versione recente, a racchetta non incordata). */
object RacketCatalog {
    data class Model(val brand: String, val model: String, val weightG: Int, val balanceMm: Int, val headCm2: Int, val pattern: String) {
        val label: String get() = "$brand $model"
    }

    val models = listOf(
        Model("Babolat", "Pure Drive", 300, 320, 645, "16x19"),
        Model("Babolat", "Pure Aero", 300, 320, 645, "16x19"),
        Model("Babolat", "Pure Strike 98 16x19", 305, 320, 632, "16x19"),
        Model("Babolat", "Pure Drive Team", 285, 330, 645, "16x19"),
        Model("Dunlop", "CX 200", 305, 320, 632, "16x19"),
        Model("Head", "Speed MP", 300, 320, 645, "16x19"),
        Model("Head", "Radical MP", 300, 320, 632, "16x19"),
        Model("Head", "Extreme MP", 300, 320, 645, "16x19"),
        Model("Head", "Boom MP", 295, 325, 645, "16x19"),
        Model("Wilson", "Pro Staff 97", 315, 310, 626, "16x19"),
        Model("Wilson", "Blade 98 16x19", 305, 320, 632, "16x19"),
        Model("Wilson", "Clash 100", 295, 325, 645, "16x19"),
        Model("Wilson", "Ultra 100", 300, 320, 645, "16x19"),
        Model("Yonex", "EZONE 100", 300, 320, 645, "16x19"),
        Model("Yonex", "VCORE 100", 300, 320, 645, "16x19"),
        Model("Yonex", "Percept 97", 310, 315, 626, "16x19"),
    )

    /** Corde molto diffuse: marca, modello, tipo e calibro più venduto. */
    data class StringModel(val brand: String, val model: String, val type: StringType, val gaugeMm: Double) {
        val label: String get() = "$brand $model"
    }

    val strings = listOf(
        StringModel("Babolat", "RPM Blast", StringType.MONO, 1.25),
        StringModel("Babolat", "VS Touch", StringType.GUT, 1.30),
        StringModel("Babolat", "Xcel", StringType.MULTI, 1.30),
        StringModel("Head", "Lynx Tour", StringType.MONO, 1.25),
        StringModel("Luxilon", "ALU Power", StringType.MONO, 1.25),
        StringModel("Solinco", "Hyperion", StringType.MONO, 1.25),
        StringModel("Solinco", "Tour Bite", StringType.MONO, 1.25),
        StringModel("Tecnifibre", "X-One Biphase", StringType.MULTI, 1.30),
        StringModel("Wilson", "NXT", StringType.MULTI, 1.30),
        StringModel("Wilson", "Synthetic Gut", StringType.SYNTHETIC, 1.30),
        StringModel("Yonex", "Poly Tour Pro", StringType.MONO, 1.25),
    )
}

/**
 * Racchetta virtuale: ore di gioco delle incordature e confronto tra configurazioni (racchetta, oppure racchetta
 * con una certa incordatura). Le sessioni sono le partite finite (una per lato che aveva una racchetta) e gli
 * allenamenti. I numeri dicono com'è andata con ciascuna, non quale sia la migliore: contano anche avversario,
 * superficie e forma del giorno.
 */
object RacketStats {

    /** Una sessione con una racchetta: [side] e [winner] solo per le partite. */
    data class Session(
        val at: Long,
        val use: RacketUse,
        val durationMs: Long,
        val match: MatchRecord? = null,
        val side: Side? = null,
        val winner: Side? = null,
        val workout: WorkoutRecord? = null,
    )

    fun sessions(matches: List<PlayerStats.Played>, workouts: List<WorkoutRecord>): List<Session> {
        val out = mutableListOf<Session>()
        for (m in matches) {
            for ((side, use) in m.rec.rackets) {
                out += Session(m.rec.startedAt ?: m.rec.updatedAt, use, m.rec.clockMs, m.rec, side, m.winner)
            }
        }
        for (w in workouts) {
            val use = w.racket ?: continue
            out += Session(w.startedAt, use, w.durationS * 1000L, workout = w)
        }
        return out.sortedBy { it.at }
    }

    /**
     * Ore giocate con un'incordatura: partite e allenamenti in cui era montata. Una partita conta una volta
     * sola anche se la stessa racchetta era segnata per tutti e due i lati.
     */
    fun hours(stringingId: String, sessions: List<Session>): Double =
        sessions.filter { it.use.stringing?.id == stringingId }
            .distinctBy { it.match?.id ?: it.workout?.id }
            .sumOf { it.durationMs } / 3_600_000.0

    /** Ore della racchetta, tutte le incordature insieme. */
    fun racketHours(racketId: String, sessions: List<Session>): Double =
        sessions.filter { it.use.racketId == racketId }
            .distinctBy { it.match?.id ?: it.workout?.id }
            .sumOf { it.durationMs } / 3_600_000.0

    /** Chiave della configurazione: la racchetta, e con [byStringing] anche l'incordatura. */
    fun key(use: RacketUse, byStringing: Boolean): String =
        if (byStringing) "${use.racketId}/${use.stringing?.id.orEmpty()}" else use.racketId

    /** Numeri di una configurazione. Le percentuali sono sul totale dei punti, non medie di percentuali. */
    data class ConfigStats(
        val key: String,
        /** La configurazione dell'ultima sessione: per nome e incordatura da mostrare. */
        val use: RacketUse,
        val sessions: Int,
        val matches: Int,
        val workouts: Int,
        val hours: Double,
        val winPct: Double?,
        val pointsWonPct: Double?,
        val firstServeInPct: Double?,
        val firstServeWonPct: Double?,
        val secondServeWonPct: Double?,
        val acesPerMatch: Double?,
        val doubleFaultsPerMatch: Double?,
        val winnersPerMatch: Double?,
        val unforcedPerMatch: Double?,
        /** Velocità media del polso al colpo (°/s) dai braccialetti. */
        val wristDps: Double?,
        val strokesPerMatch: Double?,
        val heartAvg: Double?,
        val control: Double?,
        val comfort: Double?,
        val power: Double?,
        val fatigue: Double?,
        /** Sessioni con le sensazioni dette. */
        val rated: Int,
    )

    private fun pct(a: Int, b: Int): Double? = if (b > 0) a * 100.0 / b else null

    private fun avgOf(values: List<Int>): Double? = values.filter { it > 0 }.takeIf { it.isNotEmpty() }?.average()

    fun configs(sessions: List<Session>, byStringing: Boolean): List<ConfigStats> =
        sessions.groupBy { key(it.use, byStringing) }.map { (k, list) -> stats(k, list) }
            .sortedWith(compareBy<ConfigStats> { it.use.label.lowercase(Locale.ROOT) }.thenBy { it.use.stringing?.date ?: 0L })

    fun stats(key: String, list: List<Session>): ConfigStats {
        val matches = list.filter { it.match != null && it.side != null && it.winner != null }
        val totals = matches.map { s ->
            val pts = MatchStats.points(s.match!!)
            Triple(s, MatchStats.totals(pts).getValue(s.side!!), MatchStats.hasDetail(s.match, pts))
        }
        val detail = totals.filter { it.third }.map { it.second }
        fun perMatch(f: (MatchStats.SideTotals) -> Int): Double? = detail.takeIf { it.isNotEmpty() }?.map(f)?.average()
        val all = totals.map { it.second }
        val strokes = matches.mapNotNull { s -> s.match!!.strokes[s.side!!]?.takeIf { it.total > 0 } }
        val gyroN = strokes.sumOf { st -> st.counts.indices.sumOf { i -> if (st.gyroSum.getOrElse(i) { 0L } > 0) st.count(i) else 0 } }
        val gyroSum = strokes.sumOf { it.gyroSum.sum() }
        val hearts = list.mapNotNull { s ->
            val track = s.workout?.heart ?: s.side?.let { s.match?.heart?.get(it) }
            track?.let { HeartStats.summary(it)?.avg }
        }
        val feels = list.mapNotNull { it.use.feel?.takeIf { f -> !f.isEmpty } }
        return ConfigStats(
            key = key,
            use = list.maxBy { it.at }.use,
            sessions = list.size,
            matches = matches.size,
            workouts = list.count { it.workout != null },
            hours = list.distinctBy { it.match?.id ?: it.workout?.id }.sumOf { it.durationMs } / 3_600_000.0,
            winPct = pct(matches.count { it.winner == it.side }, matches.size),
            pointsWonPct = pct(all.sumOf { it.pointsWon }, all.sumOf { it.points }),
            firstServeInPct = pct(detail.sumOf { it.firstServesIn }, detail.sumOf { it.servePoints }),
            firstServeWonPct = pct(detail.sumOf { it.firstServePointsWon }, detail.sumOf { it.firstServesIn }),
            secondServeWonPct = pct(detail.sumOf { it.secondServePointsWon }, detail.sumOf { it.secondServes }),
            acesPerMatch = perMatch { it.aces },
            doubleFaultsPerMatch = perMatch { it.doubleFaults },
            winnersPerMatch = perMatch { it.winners },
            unforcedPerMatch = perMatch { it.unforcedErrors },
            wristDps = if (gyroN > 0) gyroSum.toDouble() / gyroN else null,
            strokesPerMatch = strokes.takeIf { it.isNotEmpty() }?.map { it.total }?.average(),
            heartAvg = hearts.takeIf { it.isNotEmpty() }?.average(),
            control = avgOf(feels.map { it.control }),
            comfort = avgOf(feels.map { it.comfort }),
            power = avgOf(feels.map { it.power }),
            fatigue = avgOf(feels.map { it.fatigue }),
            rated = feels.size,
        )
    }
}
