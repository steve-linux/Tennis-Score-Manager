package com.tennis.scoremanager.data

import com.tennis.scoremanager.model.Lang
import com.tennis.scoremanager.model.MatchEvent
import com.tennis.scoremanager.model.MatchFormat
import com.tennis.scoremanager.model.RulesConfig
import com.tennis.scoremanager.model.Side
import com.tennis.scoremanager.ui.Strings
import com.tennis.scoremanager.voice.CallNames
import kotlinx.serialization.Serializable

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
)

@Serializable
enum class PlayMode { REFEREE, BANDS }

/** Pagina 2: modalità, lingua, audio, formato e sorteggio. */
@Serializable
data class MatchOptions(
    val mode: PlayMode = PlayMode.REFEREE,
    val lang: Lang = Lang.IT,
    val audio: Boolean = true,
    val format: MatchFormat = MatchFormat.BEST_OF_THREE,
    val noAd: Boolean = false,
    val tossWinner: Side? = null,
    val firstServer: Side = Side.P1,
    val p1Left: Boolean = true,
    val firstServerP1: Int = 0,
    val firstServerP2: Int = 0,
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
)

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
