// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import kotlinx.serialization.Serializable

/** I messaggi tra la segreteria (l'app) e il server del circolo, /api/v1/…: li usano entrambi. */

@Serializable data class SrvError(val error: String)
@Serializable data class SrvInfo(val club: String, val api: Int, val paired: Boolean, val synced: Boolean, val timeZone: String, val payInfo: String)
@Serializable data class SrvCode(val code: String)
@Serializable data class SrvToken(val token: String, val name: String = "", val player: String = "")
@Serializable data class SrvInviteIn(val player: String, val name: String, val days: Int = 14)
@Serializable data class SrvInvite(val code: String, val expires: Long)
@Serializable data class SrvPlayer(val id: String, val name: String, val since: Long, val lastSeen: Long, val devices: Int)
@Serializable data class SrvAdmin(val syncedAt: Long, val bookings: List<OnlineBooking>, val players: List<SrvPlayer>)
@Serializable data class SrvOk(val ok: Boolean = true)

/** Il giocatore: le sue prenotazioni, il giorno, il prezzo prima di confermare (la pagina web e l'app). */
@Serializable data class SrvMyBooking(val id: String, val court: String, val start: Long, val minutes: Int, val cents: Int, val lights: Boolean, val heating: Boolean, val canCancel: Boolean)
@Serializable data class SrvMe(
    val name: String, val club: String, val timeZone: String, val rules: BookingRules, val payInfo: String,
    /** I giorni prenotabili (yyyy-MM-dd nel fuso del circolo), da oggi. */
    val days: List<String>, val bookings: List<SrvMyBooking>,
)
@Serializable data class SrvDay(val date: String, val timeZone: String, val slotMinutes: Int, val open: Boolean, val courts: List<CourtInfo>, val cells: List<Cell>)
@Serializable data class SrvBookIn(val court: String, val start: Long, val minutes: Int, val heating: Boolean = false)
@Serializable data class SrvQuote(val cents: Int, val lights: Boolean, val heating: Boolean, val problem: String? = null)

