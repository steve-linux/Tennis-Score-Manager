// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import com.tennis.scoremanager.ble.BandProtocol
import com.tennis.scoremanager.model.Lang
import com.tennis.scoremanager.model.MatchEvent
import com.tennis.scoremanager.model.MatchFormat
import com.tennis.scoremanager.model.RulesConfig
import com.tennis.scoremanager.model.Side
import com.tennis.scoremanager.ui.Strings
import com.tennis.scoremanager.voice.CallNames
import kotlinx.serialization.Serializable

/** Il padel si conta come il tennis (ITF/FIP): cambiano solo i nomi e le impostazioni proposte. */
@Serializable
enum class Sport { TENNIS, PADEL }

/** Pagina 1: dati facoltativi. p1a/p1b sono sempre del Giocatore 1, p2a/p2b del Giocatore 2. */
@Serializable
data class SetupData(
    val club: String = "",
    val court: String = "",
    val doubles: Boolean = false,
    val p1a: String = "",
    val p1b: String = "",
    val p2a: String = "",
    val p2b: String = "",
    val sport: Sport = Sport.TENNIS,
)

/** REMOTES = telecomandini Bluetooth da selfie al posto dei braccialetti. */
@Serializable
enum class PlayMode { REFEREE, BANDS, REMOTES }

/** Pagina 2: modalità, lingua, audio, formato e sorteggio. */
@Serializable
data class MatchOptions(
    val mode: PlayMode = PlayMode.REFEREE,
    val lang: Lang = Lang.IT,
    val audio: Boolean = true,
    val format: MatchFormat = MatchFormat.BEST_OF_THREE,
    /** Game per set: 6 o 4 (set brevi); 8 o 10 nel pro set. */
    val gamesPerSet: Int = 6,
    val setTiebreak: Boolean = true,
    val noAd: Boolean = false,
    /** Statistiche dettagliate: ace, falli, vincenti ed errori si segnano dalla schermata del punteggio. */
    val detailedStats: Boolean = false,
    /** Star point del padel; con [noAd] acceso non conta (l'interfaccia ne lascia acceso uno solo). */
    val starPoint: Boolean = false,
    val tossWinner: Side? = null,
    val firstServer: Side = Side.P1,
    val p1Left: Boolean = true,
    val firstServerP1: Int = 0,
    val firstServerP2: Int = 0,
    /** Motore di sintesi vocale (pacchetto) e voce scelti; null = automatico. */
    val ttsEngine: String? = null,
    val ttsVoice: String? = null,
    /** true = legge i file generati invece della sintesi continua. */
    val voiceFiles: Boolean = false,
    /** Spegne i braccialetti quando si conferma la fine della partita (e quando si esce dall'app). */
    val bandsOffAtEnd: Boolean = true,
    /** Cerca l'indirizzo del campo dalle coordinate (Geocoder: con i servizi Google le coordinate vanno a Google). Spento di default. */
    val addressLookup: Boolean = false,
    /** Fasce cardio Bluetooth: battito e calorie, in qualsiasi modalità. */
    val heartRate: Boolean = false,
)

@Serializable
data class MatchLocation(val lat: Double, val lon: Double, val address: String? = null)

/** Partita salvata: basta rigiocare gli eventi per riavere lo stato esatto. */
@Serializable
data class MatchRecord(
    val id: String,
    val setup: SetupData,
    val options: MatchOptions,
    val rules: RulesConfig,
    val events: List<MatchEvent> = emptyList(),
    /** Tempo partita accumulato (ms) fino all'ultima pausa. */
    val clockMs: Long = 0L,
    /** Ora del telefono in cui la voce ha detto "gioco". */
    val startedAt: Long? = null,
    /** Ora del telefono dell'ultimo punto che ha deciso la partita. */
    val endedAt: Long? = null,
    val suspended: Boolean = false,
    val finished: Boolean = false,
    val location: MatchLocation? = null,
    val updatedAt: Long = 0L,
    /** Carica dei braccialetti (%) all'inizio e alla fine della partita, per verificare l'autonomia. */
    val batteryStart: Map<Side, Int> = emptyMap(),
    val batteryEnd: Map<Side, Int> = emptyMap(),
    /** Colpi riconosciuti dai braccialetti (firmware 2.4) per giocatore o squadra. */
    val strokes: Map<Side, StrokeStats> = emptyMap(),
    /** Battito dalle fasce cardio, per lato (nel doppio: chi indossa la fascia). */
    val heart: Map<Side, HeartTrack> = emptyMap(),
    /** Note libere scritte nel riepilogo: vanno nel PDF, nel testo condiviso e nei salvataggi. */
    val notes: String = "",
)

/** Chi indossa la fascia: serve solo a stimare le calorie. */
@Serializable
data class HeartProfile(val age: Int, val weightKg: Int, val female: Boolean = false)

/**
 * Battito durante la partita: un valore ogni [HeartStats.STEP_S] secondi (media delle misure della fascia), [t] in
 * secondi dall'inizio della partita. Le pause (sospensione, fascia staccata) restano buchi tra un tempo e l'altro.
 */
@Serializable
data class HeartTrack(
    val t: List<Int> = emptyList(),
    val bpm: List<Int> = emptyList(),
    val profile: HeartProfile? = null,
) {
    val size: Int get() = minOf(t.size, bpm.size)

    fun add(sec: Int, value: Int): HeartTrack = copy(t = t + sec, bpm = bpm + value)

    /**
     * Misure arrivate in ritardo (dal telefono satellite, dopo un'interruzione del Wi-Fi): (secondo, battito).
     * Si raggruppano a [step] secondi come quelle dal vivo e vanno solo dove la traccia ha un buco (nessun valore
     * a mezzo passo o meno), al loro posto in ordine di tempo.
     */
    fun fill(points: List<Pair<Int, Int>>, step: Int): HeartTrack {
        val n = size
        val pairs = (0 until n).map { t[it] to bpm[it] }.toMutableList()
        val groups = points.filter { it.first >= 0 }.groupBy { it.first / step }.toSortedMap()
        for ((slot, values) in groups) {
            val sec = slot * step + step - 1
            if (pairs.any { kotlin.math.abs(it.first - sec) <= step / 2 }) continue
            val avg = Math.round(values.map { it.second }.average()).toInt()
            val at = pairs.indexOfFirst { it.first > sec }.let { if (it < 0) pairs.size else it }
            pairs.add(at, sec to avg)
        }
        if (pairs.size == n) return this
        return copy(t = pairs.map { it.first }, bpm = pairs.map { it.second })
    }
}

/**
 * Colpi di un giocatore: [counts] nell'ordine di BandProtocol.STROKE_*, [unknown] quelli non riconosciuti.
 * [gyroSum]/[gyroMax]: velocità di rotazione del polso al picco (°/s) per tipo di colpo, per media e massimo.
 */
@Serializable
data class StrokeStats(
    val counts: List<Int> = List(BandProtocol.STROKE_COUNT) { 0 },
    val unknown: Int = 0,
    val gyroSum: List<Long> = List(BandProtocol.STROKE_COUNT) { 0L },
    val gyroMax: List<Int> = List(BandProtocol.STROKE_COUNT) { 0 },
) {
    val total: Int get() = counts.sum() + unknown

    fun count(stroke: Int): Int = counts.getOrElse(stroke) { 0 }

    /** Media della velocità del polso per quel colpo; null se non ce ne sono (o partita salvata senza velocità). */
    fun avgGyro(stroke: Int): Int? {
        val n = count(stroke)
        val sum = gyroSum.getOrElse(stroke) { 0L }
        return if (n > 0 && sum > 0) (sum / n).toInt() else null
    }

    fun maxGyro(stroke: Int): Int? = gyroMax.getOrElse(stroke) { 0 }.takeIf { it > 0 }

    fun add(stroke: Int, peakGyroDps: Int): StrokeStats {
        if (stroke !in counts.indices) return copy(unknown = unknown + 1)
        fun <T> List<T>.at(i: Int, f: (T) -> T) = mapIndexed { j, v -> if (j == i) f(v) else v }
        val sums = gyroSum.takeIf { it.size == counts.size } ?: List(counts.size) { 0L }
        val maxs = gyroMax.takeIf { it.size == counts.size } ?: List(counts.size) { 0 }
        return copy(
            counts = counts.at(stroke) { it + 1 },
            gyroSum = sums.at(stroke) { it + peakGyroDps },
            gyroMax = maxs.at(stroke) { maxOf(it, peakGyroDps) },
        )
    }
}

/** Nomi mostrati e letti, sempre legati al lato giusto. */
class Names(private val setup: SetupData, private val strings: Strings) : CallNames {

    private fun n(v: String) = v.trim()

    /** Nomi dei giocatori di un lato: 1 nel singolare, 2 nel doppio. */
    fun players(side: Side): List<String> {
        val num = if (side == Side.P1) 1 else 2
        val a = n(if (side == Side.P1) setup.p1a else setup.p2a)
        val b = n(if (side == Side.P1) setup.p1b else setup.p2b)
        if (!setup.doubles) return listOf(a.ifEmpty { strings.playerDefault(num) })
        return listOf(a.ifEmpty { "${strings.playerDefault(num)}A" }, b.ifEmpty { "${strings.playerDefault(num)}B" })
    }

    /** Nome del lato: il giocatore, oppure "Rossi e Bianchi" nel doppio (se non ci sono nomi: "Giocatore 1"). */
    override fun side(side: Side): String {
        val num = if (side == Side.P1) 1 else 2
        if (!setup.doubles) return players(side).first()
        val a = n(if (side == Side.P1) setup.p1a else setup.p2a)
        val b = n(if (side == Side.P1) setup.p1b else setup.p2b)
        return when {
            a.isEmpty() && b.isEmpty() -> strings.playerDefault(num)
            a.isEmpty() || b.isEmpty() -> a.ifEmpty { b }
            else -> a + strings.teamJoiner + b
        }
    }

    /** Versione compatta per il tabellone: "Rossi / Bianchi". */
    fun short(side: Side): String = if (setup.doubles) players(side).joinToString(" / ") else side(side)

    override fun player(side: Side, index: Int): String = players(side).getOrElse(index) { side(side) }
}
