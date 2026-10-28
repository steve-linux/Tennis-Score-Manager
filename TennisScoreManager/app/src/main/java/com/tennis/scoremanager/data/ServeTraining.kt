// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import kotlinx.serialization.Serializable

/** Lato da cui si serve: destra (parità) o sinistra (vantaggio). */
@Serializable
enum class ServeSide { DEUCE, AD }

@Serializable
enum class ServeKind { FIRST, SECOND }

/** Dove si voleva mandare il servizio: al centro (T), al corpo o esterno. */
@Serializable
enum class ServeTarget { T, BODY, WIDE }

/**
 * Un servizio dell'allenamento: quando ([at], ora del telefono), se è entrato, con che impostazioni, se il braccialetto
 * l'aveva riconosciuto ([detected]) e coi picchi del polso; [clip] è la cartella dei pezzi di video (null finché non
 * è salvata, o se non c'è la telecamera).
 */
@Serializable
data class ServeAttempt(
    val at: Long,
    val inPlay: Boolean,
    val side: ServeSide,
    val kind: ServeKind,
    val target: ServeTarget? = null,
    val detected: Boolean = false,
    val gyroDps: Int? = null,
    val accelG: Double? = null,
    val clip: String? = null,
)

@Serializable
data class ServeSession(
    val id: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val serves: List<ServeAttempt> = emptyList(),
)

/**
 * Allenamento al servizio: un telefono sul cavalletto a fianco di chi serve, il braccialetto al polso. Il braccialetto
 * riconosce il servizio; KEY1 dice "dentro", KEY2 "fuori", la pressione lunga di KEY1 annulla l'ultimo.
 */
object ServeTraining {
    /** Un servizio riconosciuto vale per il tasto premuto entro questo tempo. */
    const val MATCH_WINDOW_MS = 12_000L
    /** Senza servizio riconosciuto: si suppone che sia partito poco prima del tasto. */
    const val PRESS_DELAY_MS = 3_000L
    /** Video tenuto prima e dopo l'istante del servizio. */
    const val CLIP_BEFORE_MS = 3_000L
    const val CLIP_AFTER_MS = 3_000L

    /** Servizio riconosciuto dal braccialetto: ora e picchi del polso. */
    data class Detected(val at: Long, val gyroDps: Int, val accelG: Double)

    /**
     * L'istante del servizio per un tasto premuto a [pressAt]: quello riconosciuto, se è arrivato nei
     * [MATCH_WINDOW_MS] prima del tasto e non è già stato usato; altrimenti poco prima del tasto.
     */
    fun anchor(pressAt: Long, detected: Detected?, lastUsedAt: Long?): Pair<Long, Detected?> {
        val d = detected?.takeIf { it.at in (pressAt - MATCH_WINDOW_MS)..pressAt && it.at != lastUsedAt }
        return (d?.at ?: (pressAt - PRESS_DELAY_MS)) to d
    }

    /** Conteggio: servizi, quanti dentro, e la percentuale (null senza servizi). */
    data class Count(val total: Int, val inPlay: Int) {
        val pct: Int? get() = if (total == 0) null else Math.round(inPlay * 100.0 / total).toInt()
    }

    fun count(serves: List<ServeAttempt>): Count = Count(serves.size, serves.count { it.inPlay })

    /** Righe del riepilogo: per lato, per prima e seconda, e per bersaglio (solo quelli usati). */
    data class Breakdown(
        val all: Count,
        val bySide: Map<ServeSide, Count>,
        val byKind: Map<ServeKind, Count>,
        val byTarget: Map<ServeTarget, Count>,
    )

    fun breakdown(serves: List<ServeAttempt>): Breakdown = Breakdown(
        count(serves),
        ServeSide.entries.associateWith { s -> count(serves.filter { it.side == s }) }.filterValues { it.total > 0 },
        ServeKind.entries.associateWith { k -> count(serves.filter { it.kind == k }) }.filterValues { it.total > 0 },
        ServeTarget.entries.associateWith { t -> count(serves.filter { it.target == t }) }.filterValues { it.total > 0 },
    )

    /** Media dei picchi del polso dei servizi riconosciuti (null se nessuno): dentro e fuori, per confrontarli. */
    fun meanGyro(serves: List<ServeAttempt>): Int? =
        serves.mapNotNull { it.gyroDps }.takeIf { it.isNotEmpty() }?.average()?.let { Math.round(it).toInt() }
}
