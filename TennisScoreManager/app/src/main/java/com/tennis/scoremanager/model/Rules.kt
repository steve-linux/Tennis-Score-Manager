package com.tennis.scoremanager.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Giocatore 1 (giallo) e Giocatore 2 (rosso). Nel doppio indica la squadra. */
@Serializable
enum class Side {
    P1, P2;

    val other: Side get() = if (this == P1) P2 else P1
}

@Serializable
enum class Lang { IT, EN }

@Serializable
enum class MatchFormat {
    /** Al meglio dei tre set, tie-break a 7 punti sul 6-6 in ogni set. */
    BEST_OF_THREE,

    /** Due set con tie-break a 7 sul 6-6; sull'1-1 si gioca un match tie-break (super tie-break) a 10. */
    TWO_SETS_MATCH_TIEBREAK,
}

/** Regole della partita decise prima dell'inizio (formato, sorteggio, lati del campo). */
@Serializable
data class RulesConfig(
    val format: MatchFormat = MatchFormat.BEST_OF_THREE,
    /** No-Ad: sul 40-40 si gioca il punto decisivo. */
    val noAd: Boolean = false,
    val doubles: Boolean = false,
    /** Chi serve il primo game della partita. */
    val firstServer: Side = Side.P1,
    /** Vista dal giudice di sedia: il Giocatore 1 inizia sul lato sinistro? */
    val p1StartsLeft: Boolean = true,
    /** Doppio: quale giocatore della squadra (0 o 1) serve per primo nel primo set. */
    val firstServerP1: Int = 0,
    val firstServerP2: Int = 0,
)

/** Eventi della partita: lo stato si ricalcola sempre rigiocandoli (undo sicuro a ogni livello). */
@Serializable
sealed class MatchEvent {
    @Serializable
    @SerialName("point")
    data class Point(val winner: Side, val at: Long = 0L) : MatchEvent()

    /** Doppio: ordine di servizio scelto all'inizio di un set (ammesso solo prima del primo punto del set). */
    @Serializable
    @SerialName("serveOrder")
    data class ServeOrder(val setNumber: Int, val firstP1: Int, val firstP2: Int) : MatchEvent()
}

/** Punteggio di un set concluso. Per il match tie-break g1/g2 valgono 1-0 e i punti stanno in tb1/tb2. */
@Serializable
data class SetScore(
    val g1: Int,
    val g2: Int,
    val tb1: Int? = null,
    val tb2: Int? = null,
    val matchTiebreak: Boolean = false,
) {
    val winner: Side get() = if (g1 > g2) Side.P1 else Side.P2
    val hasTiebreak: Boolean get() = tb1 != null && tb2 != null

    fun games(side: Side): Int = if (side == Side.P1) g1 else g2
    fun tb(side: Side): Int? = if (side == Side.P1) tb1 else tb2

    /** Numeri da leggere/mostrare per questo set: game, oppure punti per il match tie-break. */
    fun shown(side: Side): Int = if (matchTiebreak) tb(side) ?: 0 else games(side)
}

enum class TiebreakKind { NONE, SET, MATCH }
