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
    private data class T(val welcome: String, val welcomeBody: String, val reminder: String, val removed: String, val freed: String, val joined: String, val found: String, val noCourt: String, val newSeek: String = "", val singles: String = "", val doubles: String = "", val board: String = "", val chat: String = "", val club: String = "", val dm: String = "")

    private val texts = mapOf(
        "it" to T("Promemoria attivi", "Ti avviseremo prima delle partite e se la segreteria cambia una prenotazione.", "Promemoria della partita", "Prenotazione tolta dalla segreteria", "Si è liberato un campo: prenotalo!", "Qualcuno risponde al tuo annuncio", "Partita trovata: campo prenotato!", "Giocatori trovati, ma nessun campo libero: prenotatelo voi", "Cerco compagno", "singolo", "doppio", "Bacheca del circolo", "Nuovo messaggio: cerco compagno", "Chat del circolo", "Messaggio da un socio"),
        "en" to T("Reminders on", "We will remind you before your matches and tell you if the office changes a booking.", "Match reminder", "Booking removed by the club office", "A court is free: book it!", "Someone answered your post", "Match found: court booked!", "Players found, but no free court: book one yourselves", "Looking for a partner", "singles", "doubles", "Club notice board", "New message: looking for a partner", "Club chat", "Message from a member"),
        "fr" to T("Rappels activés", "Nous vous préviendrons avant vos matchs et si le secrétariat modifie une réservation.", "Rappel du match", "Réservation retirée par le secrétariat", "Un terrain s'est libéré : réservez-le !", "Quelqu'un répond à votre annonce", "Match trouvé : terrain réservé !", "Joueurs trouvés, mais aucun terrain libre : réservez-en un vous-mêmes", "Je cherche un partenaire", "simple", "double", "Tableau du club", "Nouveau message : je cherche un partenaire", "Discussion du club", "Message d'un membre"),
        "de" to T("Erinnerungen an", "Wir erinnern dich vor deinen Spielen und melden, wenn das Büro eine Buchung ändert.", "Erinnerung an dein Spiel", "Buchung vom Vereinsbüro entfernt", "Ein Platz ist frei geworden: Buch ihn!", "Jemand antwortet auf deine Anzeige", "Spiel gefunden: Platz gebucht!", "Spieler gefunden, aber kein Platz frei: Bucht selbst einen", "Partner gesucht", "Einzel", "Doppel", "Vereinsbrett", "Neue Nachricht: Partner gesucht", "Vereinschat", "Nachricht von einem Mitglied"),
        "es" to T("Recordatorios activados", "Te avisaremos antes de tus partidos y si la secretaría cambia una reserva.", "Recordatorio del partido", "Reserva quitada por la secretaría", "Se ha liberado una pista: ¡resérvala!", "Alguien responde a tu anuncio", "Partido encontrado: ¡pista reservada!", "Jugadores encontrados, pero ninguna pista libre: reservadla vosotros", "Busco compañero", "individual", "dobles", "Tablón del club", "Nuevo mensaje: busco compañero", "Chat del club", "Mensaje de un socio"),
        "pt" to T("Lembretes ativos", "Vamos avisar-te antes dos jogos e se a secretaria mudar uma reserva.", "Lembrete do jogo", "Reserva retirada pela secretaria", "Ficou livre uma quadra: reserva-a!", "Alguém respondeu ao teu anúncio", "Jogo encontrado: quadra reservada!", "Jogadores encontrados, mas nenhuma quadra livre: reservem vocês", "Procuro parceiro", "singulares", "pares", "Mural do clube", "Nova mensagem: procuro parceiro", "Chat do clube", "Mensagem de um sócio"),
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
    private fun day(date: String, lang: String): String =
        runCatching { DateTimeFormatter.ofPattern("EEE d MMM", Locale.forLanguageTag(lang)).format(java.time.LocalDate.parse(date)) }.getOrDefault(date)

    fun seekJoined(lang: String, s: com.tennis.scoremanager.data.Seek, who: String) = json(t(lang).joined, "$who · ${day(s.date, lang)}", "s-${s.id}", null)
    fun seekFound(lang: String, s: com.tennis.scoremanager.data.Seek, court: String, start: Long, zone: String) =
        json(t(lang).found, label(OnlineBooking("", court, start, 60, "", ""), zone, lang) + " · " + s.everyone.joinToString(", ") { it.name }, "s-${s.id}", start)
    /** Un annuncio nuovo per tutti gli altri: "Mario · sab 8 nov 17:00–20:00 · singolo". */
    fun seekNew(lang: String, s: com.tennis.scoremanager.data.Seek): String {
        fun hm(m: Int) = "%02d:%02d".format(m / 60, m % 60)
        val kind = if (s.doubles) t(lang).doubles else t(lang).singles
        return json(t(lang).newSeek, listOf(s.owner.name, day(s.date, lang) + " " + hm(s.fromMin) + "–" + hm(s.toMin), kind, s.level).filter { it.isNotBlank() }.joinToString(" · "), "n-${s.id}", null)
    }
    /** Un messaggio: in bacheca (dalla segreteria) o nella chat di un annuncio; i messaggi della stessa stanza si sostituiscono. */
    fun chat(lang: String, room: String, from: String, text: String) =
        json(when { room == "board" -> t(lang).board; room == "club" -> t(lang).club; room.startsWith("d-") -> t(lang).dm; else -> t(lang).chat },
            "$from: " + text.take(160), "c-$room", null)
    fun seekNoCourt(lang: String, s: com.tennis.scoremanager.data.Seek) = json(t(lang).noCourt, day(s.date, lang) + " · " + s.everyone.joinToString(", ") { it.name }, "s-${s.id}", null)

    fun freed(lang: String, w: com.tennis.scoremanager.data.Wait, court: String, zone: String) =
        json(t(lang).freed, label(OnlineBooking("", court, w.start, w.minutes, w.player, ""), zone, lang), "w-${w.id}", w.start)
    fun removed(lang: String, b: OnlineBooking, zone: String) = json(t(lang).removed, label(b, zone, lang), "x-${b.id}", b.start)
}
