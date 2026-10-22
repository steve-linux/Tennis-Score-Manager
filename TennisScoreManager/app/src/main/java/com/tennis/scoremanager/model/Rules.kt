// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.Locale

/** Giocatore 1 (giallo) e Giocatore 2 (rosso). Nel doppio indica la squadra. */
@Serializable
enum class Side {
    P1, P2;

    val other: Side get() = if (this == P1) P2 else P1
}

/**
 * Lingue di app, chiamate e braccialetti. [code] va al tabellone TV e al braccialetto; [locale] sceglie la voce
 * della sintesi vocale (preferita, se c'è, quella del paese) e il formato delle date. [label] si scrive nella lingua stessa.
 */
@Serializable
enum class Lang(val code: String, val locale: Locale, val label: String, val flag: String) {
    IT("it", Locale.ITALY, "Italiano", "🇮🇹"),
    EN("en", Locale.UK, "English", "🇬🇧"),
    FR("fr", Locale.FRANCE, "Français", "🇫🇷"),
    DE("de", Locale.GERMANY, "Deutsch", "🇩🇪"),
    ES("es", Locale.forLanguageTag("es-ES"), "Español", "🇪🇸"),
    PT("pt", Locale.forLanguageTag("pt-BR"), "Português", "🇧🇷");

    companion object {
        fun fromCode(code: String?): Lang? = entries.firstOrNull { it.code == code?.lowercase() }
    }
}

@Serializable
enum class MatchFormat {
    /** Al meglio dei tre set, tie-break a 7 punti sul 6-6 in ogni set. */
    BEST_OF_THREE,

    /** Due set con tie-break a 7 sul 6-6; sull'1-1 si gioca un match tie-break (super tie-break) a 10. */
    TWO_SETS_MATCH_TIEBREAK,

    /** Al meglio dei cinque set. */
    BEST_OF_FIVE,

    /** Un solo set lungo (pro set) a [RulesConfig.gamesPerSet] game, di solito 8 o 10. */
    PRO_SET,

    /** Solo un match tie-break a 10 punti. */
    MATCH_TIEBREAK_ONLY,
}

/** Regole della partita decise prima dell'inizio (formato, sorteggio, lati del campo). */
@Serializable
data class RulesConfig(
    val format: MatchFormat = MatchFormat.BEST_OF_THREE,
    /** Game per vincere un set (con 2 di scarto): 6, 4 nei set brevi, 8 o 10 nel pro set. */
    val gamesPerSet: Int = 6,
    /** Tie-break a 7 quando i game arrivano pari a [gamesPerSet]; false = il set va avanti finché uno ha 2 game di scarto. */
    val setTiebreak: Boolean = true,
    /** No-Ad: sul 40-40 si gioca il punto decisivo. */
    val noAd: Boolean = false,
    /**
     * Star point (padel, regola FIP dal 2026): sul 40-40 si giocano al massimo due vantaggi; alla terza parità
     * si gioca un solo punto che decide il game. Ignorato se è acceso [noAd].
     */
    val starPoint: Boolean = false,
    val doubles: Boolean = false,
    /** Chi serve il primo game della partita. */
    val firstServer: Side = Side.P1,
    /** Vista dal giudice di sedia: il Giocatore 1 inizia sul lato sinistro? */
    val p1StartsLeft: Boolean = true,
    /** Doppio: quale giocatore della squadra (0 o 1) serve per primo nel primo set. */
    val firstServerP1: Int = 0,
    val firstServerP2: Int = 0,
) {
    /** Set da vincere per vincere la partita (il match tie-break conta come un set). */
    val setsToWin: Int
        get() = when (format) {
            MatchFormat.BEST_OF_FIVE -> 3
            MatchFormat.PRO_SET, MatchFormat.MATCH_TIEBREAK_ONLY -> 1
            else -> 2
        }
}

/**
 * Come è finito un punto, per le statistiche dettagliate. Chi l'ha fatto si ricava dal vincitore e da chi serve:
 * ace e doppio fallo sono di chi serve, il vincente di chi vince il punto, l'errore di chi lo perde.
 */
@Serializable
enum class PointKind { ACE, DOUBLE_FAULT, WINNER, ERROR }

/** Eventi della partita: lo stato si ricalcola sempre rigiocandoli (undo sicuro a ogni livello). */
@Serializable
sealed class MatchEvent {
    @Serializable
    @SerialName("point")
    /**
     * [strokes]: colpi riconosciuti dai braccialetti durante lo scambio (firmware 2.4); null = non contati.
     * [kind]: come è finito il punto (statistiche dettagliate); null = non detto. [secondServe]: la prima era fuori.
     */
    data class Point(
        val winner: Side,
        val at: Long = 0L,
        val strokes: Int? = null,
        val kind: PointKind? = null,
        val secondServe: Boolean = false,
    ) : MatchEvent()

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
