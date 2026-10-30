// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import kotlinx.serialization.Serializable

@Serializable
enum class Hand { RIGHT, LEFT }

/**
 * Un allievo dell'allenatore: le sessioni di allenamento dei colpi gli vanno assegnate all'avvio. Il nome è lo stesso
 * delle partite (foto, storico e rating si ritrovano da lì).
 */
@Serializable
data class Student(
    val id: String,
    val name: String,
    val hand: Hand? = null,
    val note: String = "",
    val createdAt: Long = 0,
    /** La sua racchetta (Racket.id): va con le sue sessioni di allenamento, com'era quel giorno. */
    val racket: String? = null,
)

object Students {
    /** Nome valido come cartella: senza i caratteri che i file system rifiutano. */
    fun folderName(name: String): String = name.replace(Regex("""[\\/:*?"<>|]"""), " ").replace(Regex("\\s+"), " ").trim().ifEmpty { "?" }

    /** Le sessioni di un allievo ([id] null = quelle senza allievo). */
    fun sessionsOf(all: List<ServeSession>, id: String?): List<ServeSession> = all.filter { it.student == id }

    /** Per un colpo: quante sessioni e colpi, la percentuale dentro della prima e dell'ultima sessione, l'ultima volta. */
    data class StrokeLine(val stroke: TrainedStroke, val sessions: Int, val shots: Int, val firstPct: Int?, val lastPct: Int?, val lastAt: Long)

    fun strokeLines(sessions: List<ServeSession>): List<StrokeLine> =
        TrainedStroke.entries.mapNotNull { t ->
            val mine = sessions.filter { it.stroke == t && it.serves.isNotEmpty() }.sortedBy { it.startedAt }
            if (mine.isEmpty()) return@mapNotNull null
            StrokeLine(
                t, mine.size, mine.sumOf { it.serves.size },
                ServeTraining.count(mine.first().serves).pct, ServeTraining.count(mine.last().serves).pct, mine.last().startedAt,
            )
        }

    /** Una misura dell'analisi in due sessioni: [unit] "°" o "" (frazioni di statura), [better] il verso buono (+1, -1, 0 = nessuno). */
    data class Change(val label: String, val before: Float?, val now: Float?, val unit: String, val better: Int)
}
