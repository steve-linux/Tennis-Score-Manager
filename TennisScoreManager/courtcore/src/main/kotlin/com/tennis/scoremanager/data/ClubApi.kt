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
@Serializable data class SrvPlayer(val id: String, val name: String, val since: Long, val lastSeen: Long, val devices: Int, val hasPin: Boolean = false)
/** Il giocatore sceglie numero di telefono e PIN, per rientrare da solo su un altro telefono. */
@Serializable data class SrvPinIn(val phone: String, val pin: String)
/** Le scelte del giocatore sulla pagina: [seekAlerts] = notifiche dei nuovi annunci di Cerco compagno. */
@Serializable data class SrvPrefsIn(val seekAlerts: Boolean)

/**
 * Un messaggio di una chat del circolo. Le stanze sono "board" (la bacheca: scrive solo la segreteria, leggono
 * tutti) e "s-ID" (la chat di un annuncio di Cerco compagno: chi l'ha messo e chi ha detto ci sto).
 */
@Serializable data class SrvChatMsg(val id: String, val name: String, val text: String, val at: Long, val mine: Boolean = false, val office: Boolean = false)
@Serializable data class SrvChatIn(val text: String)
/** Una stanza vista dalla segreteria: per la moderazione. */
@Serializable data class SrvChatRoom(val room: String, val title: String, val count: Int, val last: Long)
@Serializable data class SrvAdmin(
    val syncedAt: Long, val bookings: List<OnlineBooking>, val players: List<SrvPlayer>,
    /** Per la segreteria: chi è in lista d'attesa e gli annunci di Cerco compagno ancora da giocare (vuoti dai server vecchi). */
    val waits: List<Wait> = emptyList(), val seeks: List<Seek> = emptyList(),
)
@Serializable data class SrvOk(val ok: Boolean = true)

/**
 * Il tunnel da internet del server. [state]: OFF, WORKING, LOGIN e ENABLE (la segreteria apre [link]), ON (con [url]),
 * ERROR (con [code]). [available] è falso se sul server manca l'aiutante del tunnel: va aggiornato il kit.
 */
@Serializable data class SrvTunnel(val available: Boolean = false, val state: String = "OFF", val url: String = "", val link: String = "", val code: String = "")
@Serializable data class SrvTunnelIn(val on: Boolean)
/** I backup della segreteria tenuti dal server (gli ultimi 14): il file compresso in base64. */
@Serializable data class SrvBackup(val name: String, val at: Long, val size: Long)
@Serializable data class SrvBackupData(val name: String, val data: String)

/** Il giocatore: le sue prenotazioni, il giorno, il prezzo prima di confermare (la pagina web e l'app). */
@Serializable data class SrvMyBooking(val id: String, val court: String, val start: Long, val minutes: Int, val cents: Int, val lights: Boolean, val heating: Boolean, val canCancel: Boolean)
@Serializable data class SrvMe(
    val name: String, val club: String, val timeZone: String, val rules: BookingRules, val payInfo: String,
    /** I giorni prenotabili (yyyy-MM-dd nel fuso del circolo), da oggi. */
    val days: List<String>, val bookings: List<SrvMyBooking>,
    /** Le liste d'attesa del giocatore: aperte, o già liberate (si prenota). */
    val waits: List<SrvWait> = emptyList(),
    /** Ha scelto numero e PIN: rientra da solo; [phone] sono le ultime cifre del numero, per riconoscerlo. */
    val hasPin: Boolean = false,
    val phone: String = "",
    /** Notifiche dei nuovi annunci di Cerco compagno (spente dal giocatore = niente). */
    val seekAlerts: Boolean = true,
    /** Quando la segreteria ha scritto l'ultima volta in bacheca (0 = mai). */
    val boardLast: Long = 0,
)
/** Una lista d'attesa vista dal giocatore: [freedCourt] non null = si è liberato quel campo. */
@Serializable data class SrvWait(val id: String, val court: String? = null, val start: Long, val minutes: Int, val freedCourt: String? = null)
@Serializable data class SrvWaitIn(val court: String? = null, val start: Long, val minutes: Int)
@Serializable data class SrvDay(val date: String, val timeZone: String, val slotMinutes: Int, val open: Boolean, val courts: List<CourtInfo>, val cells: List<Cell>)
@Serializable data class SrvBookIn(val court: String, val start: Long, val minutes: Int, val heating: Boolean = false)
@Serializable data class SrvQuote(val cents: Int, val lights: Boolean, val heating: Boolean, val problem: String? = null)


/** Le notifiche push della pagina web: la chiave pubblica del server e l'abbonamento che dà il browser. */
@Serializable data class SrvPushKey(val key: String)
@Serializable data class SrvPushSub(val endpoint: String, val p256dh: String = "", val auth: String = "", val lang: String = "it")

/** Cerco compagno: un annuncio come lo vede il giocatore. [players] sono i nomi di chi c'è già, [need] quanti mancano. */
@Serializable data class SrvSeek(
    val id: String, val mine: Boolean, val joined: Boolean, val owner: String,
    /** Il giorno (yyyy-MM-dd, fuso del circolo) e la fascia in minuti dalla mezzanotte. */
    val date: String, val fromMin: Int, val toMin: Int,
    val doubles: Boolean, val level: String, val note: String,
    val players: List<String>, val need: Int,
    /** Al completo: il campo prenotato da solo (o [noCourt] se non ce n'era uno libero). */
    val bookedCourt: String? = null, val bookedStart: Long? = null, val noCourt: Boolean = false,
    /** Quando è arrivato l'ultimo messaggio nella sua chat (0 = nessuno): per dire "nuovi messaggi". */
    val chatLast: Long = 0,
)
@Serializable data class SrvSeekIn(val date: String, val fromMin: Int, val toMin: Int, val doubles: Boolean = false, val level: String = "", val note: String = "")
