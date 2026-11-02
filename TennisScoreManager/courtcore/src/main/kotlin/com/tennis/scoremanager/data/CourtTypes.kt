// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import kotlinx.serialization.Serializable


/** La superficie del campo. */
@Serializable
enum class Surface { CLAY, GRASS, HARD, OTHER }

/**
 * Com'è fatto un campo e quanto costa: coperto o scoperto, luci, riscaldamento; i prezzi all'ora (null = quelli
 * generali del planning) e il supplemento all'ora per il riscaldamento.
 */
@Serializable
data class CourtInfo(
    val name: String,
    val surface: Surface? = null,
    val covered: Boolean = false,
    val lights: Boolean = true,
    val heating: Boolean = false,
    val centsPerHour: Int? = null,
    val centsPerHourLights: Int? = null,
    val heatingCentsPerHour: Int = 0,
    /** Le foto del campo (file nella cartella dell'app, files/court_photos). */
    val photos: List<String> = emptyList(),
)

/** Come si paga la prenotazione: al campo (contanti o POS in segreteria) o online (Satispay, PayPal, bonifico…). */
@Serializable
enum class PayWhere { COURT, ONLINE }

/** Per cosa è occupato il campo: gioco, lezione, chiuso (manutenzione), altro. Le partite dei tornei si aggiungono da sole. */
@Serializable
enum class BookingKind { PLAY, LESSON, CLOSED, OTHER }

/**
 * Una prenotazione: campo, inizio e durata, chi gioca (giocatori del database e/o nomi scritti), prezzo e, se
 * addebitato, gli addebiti nel registro. [series] lega le prenotazioni ripetute ogni settimana.
 */
@Serializable
data class CourtBooking(
    val id: String,
    val court: String,
    val start: Long,
    val minutes: Int = 60,
    val kind: BookingKind = BookingKind.PLAY,
    val players: List<String> = emptyList(),
    val names: List<String> = emptyList(),
    val note: String = "",
    val lights: Boolean = false,
    val cents: Int = 0,
    val charges: List<String> = emptyList(),
    val series: String? = null,
    val heating: Boolean = false,
    val payWhere: PayWhere = PayWhere.COURT,
) {
    val end: Long get() = start + minutes * 60_000L
}

/** Orari del circolo e prezzi: dalle [openHour] alle [closeHour], caselle da [slotMinutes]; prezzi all'ora. */
@Serializable
data class CourtSettings(
    val openHour: Int = 8,
    val closeHour: Int = 23,
    val slotMinutes: Int = 60,
    val centsPerHour: Int = 0,
    val centsPerHourLights: Int = 0,
    /** Da quest'ora servono le luci (e vale il prezzo con le luci). */
    val lightsFrom: Int = 18,
    /** Quanto dura di solito una partita di torneo nel planning. */
    val tournamentMinutes: Int = 90,
    /** Com'è fatto ogni campo (per nome); quelli che mancano hanno i valori generali. */
    val courts: List<CourtInfo> = emptyList(),
) {
    fun info(court: String): CourtInfo = courts.firstOrNull { it.name == court } ?: CourtInfo(court)
}

/** Perché i campi non si possono prenotare: un corso (scuola tennis), un torneo, la manutenzione, altro. */
@Serializable
enum class BlockReason { COURSE, TOURNAMENT, MAINTENANCE, OTHER }

/**
 * Campi non prenotabili: [courts] (vuoto = tutti) dal giorno [from] al giorno [to], nei giorni della settimana
 * [weekdays] (1 = lunedì … 7 = domenica; vuoto = tutti), dalle [startMin] alle [endMin] (minuti dalla mezzanotte).
 * Nei blocchi di un corso si possono mettere le sue lezioni; in quelli di un torneo si vedono le sue partite.
 */
@Serializable
data class CourtBlock(
    val id: String,
    val label: String,
    val reason: BlockReason = BlockReason.OTHER,
    val courts: List<String> = emptyList(),
    val from: Long,
    val to: Long,
    val weekdays: Set<Int> = emptySet(),
    val startMin: Int = 0,
    val endMin: Int = 24 * 60,
    val tournament: String? = null,
    /** Il corso della scuola tennis che l'ha creato (si aggiorna e si toglie col corso). */
    val course: String? = null,
    /** La squadra il cui incontro in casa l'ha creato. */
    val team: String? = null,
)

@Serializable
data class CourtPlan(val settings: CourtSettings = CourtSettings(), val bookings: List<CourtBooking> = emptyList(), val blocks: List<CourtBlock> = emptyList())
