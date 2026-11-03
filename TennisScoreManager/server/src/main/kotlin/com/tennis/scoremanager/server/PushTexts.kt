// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.server

import com.tennis.scoremanager.data.OnlineBooking
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** I testi delle notifiche ai giocatori, nella lingua del loro telefono (quella della pagina quando si sono abbonati). */
object PushTexts {
    private data class T(val welcome: String, val welcomeBody: String, val reminder: String, val removed: String, val freed: String)

    private val texts = mapOf(
        "it" to T("Promemoria attivi", "Ti avviseremo prima delle partite e se la segreteria cambia una prenotazione.", "Promemoria della partita", "Prenotazione tolta dalla segreteria", "Si è liberato un campo: prenotalo!"),
        "en" to T("Reminders on", "We will remind you before your matches and tell you if the office changes a booking.", "Match reminder", "Booking removed by the club office", "A court is free: book it!"),
        "fr" to T("Rappels activés", "Nous vous préviendrons avant vos matchs et si le secrétariat modifie une réservation.", "Rappel du match", "Réservation retirée par le secrétariat", "Un terrain s'est libéré : réservez-le !"),
        "de" to T("Erinnerungen an", "Wir erinnern dich vor deinen Spielen und melden, wenn das Büro eine Buchung ändert.", "Erinnerung an dein Spiel", "Buchung vom Vereinsbüro entfernt", "Ein Platz ist frei geworden: Buch ihn!"),
        "es" to T("Recordatorios activados", "Te avisaremos antes de tus partidos y si la secretaría cambia una reserva.", "Recordatorio del partido", "Reserva quitada por la secretaría", "Se ha liberado una pista: ¡resérvala!"),
        "pt" to T("Lembretes ativos", "Vamos avisar-te antes dos jogos e se a secretaria mudar uma reserva.", "Lembrete do jogo", "Reserva retirada pela secretaria", "Ficou livre uma quadra: reserva-a!"),
    )

    private fun t(lang: String) = texts[lang] ?: texts.getValue("en")

    /** "Campo 1 · mar 3 nov 10:00", con l'ora del circolo. */
    fun label(b: OnlineBooking, zone: String, lang: String): String {
        val z = runCatching { ZoneId.of(zone) }.getOrDefault(ZoneId.of("Europe/Rome"))
        val f = DateTimeFormatter.ofPattern("EEE d MMM HH:mm", Locale.forLanguageTag(lang))
        return "${b.court} · ${f.format(Instant.ofEpochMilli(b.start).atZone(z))}"
    }

    private fun esc(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"")

    /** Il contenuto della notifica: titolo, testo, il giorno da aprire e un'etichetta (le notifiche uguali si sostituiscono). */
    private fun json(title: String, body: String, tag: String, start: Long?) =
        """{"title":"${esc(title)}","body":"${esc(body)}","tag":"${esc(tag)}"${start?.let { ",\"start\":$it" } ?: ""}}"""

    fun welcome(lang: String, club: String) = json(t(lang).welcome, (club.ifBlank { "TSM" }) + ": " + t(lang).welcomeBody, "welcome", null)
    fun reminder(lang: String, b: OnlineBooking, zone: String) = json(t(lang).reminder, label(b, zone, lang), "r-${b.id}", b.start)
    fun freed(lang: String, w: com.tennis.scoremanager.data.Wait, court: String, zone: String) =
        json(t(lang).freed, label(OnlineBooking("", court, w.start, w.minutes, w.player, ""), zone, lang), "w-${w.id}", w.start)
    fun removed(lang: String, b: OnlineBooking, zone: String) = json(t(lang).removed, label(b, zone, lang), "x-${b.id}", b.start)
}
