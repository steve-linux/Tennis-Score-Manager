// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import java.time.LocalDate
import java.time.ZoneId

/**
 * Le festività nazionali (non quelle locali, come il santo patrono) del Paese del telefono, col nome nella sua lingua:
 * Italia, Francia, Germania, Spagna, Portogallo, Brasile, Regno Unito, Stati Uniti. Le feste mobili partono dalla Pasqua.
 */
object Holidays {
    /** La domenica di Pasqua dell'anno (calendario gregoriano). */
    fun easter(year: Int): LocalDate {
        val a = year % 19; val b = year / 100; val c = year % 100
        val d = b / 4; val e = b % 4; val f = (b + 8) / 25; val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30; val i = c / 4; val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7; val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31; val day = (h + l - 7 * m + 114) % 31 + 1
        return LocalDate.of(year, month, day)
    }

    /** Il Paese del telefono; se manca, quello della lingua dell'app. */
    fun country(language: String = java.util.Locale.getDefault().language): String =
        java.util.Locale.getDefault().country.uppercase().ifBlank { mapOf("it" to "IT", "fr" to "FR", "de" to "DE", "es" to "ES", "pt" to "BR", "en" to "GB")[language] ?: "IT" }

    /** Le feste dell'anno nel Paese [country]: giorno → nome. */
    fun of(year: Int, country: String): Map<LocalDate, String> {
        val e = easter(year)
        fun d(m: Int, day: Int) = LocalDate.of(year, m, day)
        /** L'[n]-esimo [dow] (1 = lunedì) del mese; n = -1 l'ultimo. */
        fun nth(m: Int, dow: Int, n: Int): LocalDate {
            if (n < 0) { var x = d(m, 1).plusMonths(1).minusDays(1); while (x.dayOfWeek.value != dow) x = x.minusDays(1); return x }
            var x = d(m, 1); while (x.dayOfWeek.value != dow) x = x.plusDays(1); return x.plusWeeks((n - 1).toLong())
        }
        return when (country) {
            "IT", "SM", "VA" -> mapOf(d(1, 1) to "Capodanno", d(1, 6) to "Epifania", e to "Pasqua", e.plusDays(1) to "Lunedì dell'Angelo",
                d(4, 25) to "Festa della Liberazione", d(5, 1) to "Festa del Lavoro", d(6, 2) to "Festa della Repubblica", d(8, 15) to "Ferragosto",
                d(11, 1) to "Ognissanti", d(12, 8) to "Immacolata Concezione", d(12, 25) to "Natale", d(12, 26) to "Santo Stefano")
            "FR" -> mapOf(d(1, 1) to "Jour de l'an", e.plusDays(1) to "Lundi de Pâques", d(5, 1) to "Fête du Travail", d(5, 8) to "Victoire 1945",
                e.plusDays(39) to "Ascension", e.plusDays(50) to "Lundi de Pentecôte", d(7, 14) to "Fête nationale", d(8, 15) to "Assomption",
                d(11, 1) to "Toussaint", d(11, 11) to "Armistice", d(12, 25) to "Noël")
            "DE", "AT" -> mapOf(d(1, 1) to "Neujahr", e.minusDays(2) to "Karfreitag", e.plusDays(1) to "Ostermontag", d(5, 1) to "Tag der Arbeit",
                e.plusDays(39) to "Christi Himmelfahrt", e.plusDays(50) to "Pfingstmontag", d(10, 3) to "Tag der Deutschen Einheit",
                d(12, 25) to "1. Weihnachtstag", d(12, 26) to "2. Weihnachtstag")
            "ES" -> mapOf(d(1, 1) to "Año Nuevo", d(1, 6) to "Reyes", e.minusDays(2) to "Viernes Santo", d(5, 1) to "Día del Trabajo",
                d(8, 15) to "Asunción", d(10, 12) to "Fiesta Nacional", d(11, 1) to "Todos los Santos", d(12, 6) to "Día de la Constitución",
                d(12, 8) to "Inmaculada Concepción", d(12, 25) to "Navidad")
            "PT" -> mapOf(d(1, 1) to "Ano Novo", e.minusDays(2) to "Sexta-feira Santa", e to "Páscoa", d(4, 25) to "Dia da Liberdade",
                d(5, 1) to "Dia do Trabalhador", e.plusDays(60) to "Corpo de Deus", d(6, 10) to "Dia de Portugal", d(8, 15) to "Assunção",
                d(10, 5) to "Implantação da República", d(11, 1) to "Todos os Santos", d(12, 1) to "Restauração da Independência",
                d(12, 8) to "Imaculada Conceição", d(12, 25) to "Natal")
            "BR" -> mapOf(d(1, 1) to "Confraternização Universal", e.minusDays(48) to "Carnaval", e.minusDays(47) to "Carnaval",
                e.minusDays(2) to "Sexta-feira Santa", d(4, 21) to "Tiradentes", d(5, 1) to "Dia do Trabalho", e.plusDays(60) to "Corpus Christi",
                d(9, 7) to "Independência", d(10, 12) to "Nossa Senhora Aparecida", d(11, 2) to "Finados", d(11, 15) to "Proclamação da República",
                d(11, 20) to "Consciência Negra", d(12, 25) to "Natal")
            "US" -> mapOf(d(1, 1) to "New Year's Day", nth(1, 1, 3) to "Martin Luther King Jr. Day", nth(2, 1, 3) to "Presidents' Day",
                nth(5, 1, -1) to "Memorial Day", d(6, 19) to "Juneteenth", d(7, 4) to "Independence Day", nth(9, 1, 1) to "Labor Day",
                nth(10, 1, 2) to "Columbus Day", d(11, 11) to "Veterans Day", nth(11, 4, 4) to "Thanksgiving", d(12, 25) to "Christmas Day")
            else -> mapOf(d(1, 1) to "New Year's Day", e.minusDays(2) to "Good Friday", e.plusDays(1) to "Easter Monday",
                nth(5, 1, 1) to "Early May bank holiday", nth(5, 1, -1) to "Spring bank holiday", nth(8, 1, -1) to "Summer bank holiday",
                d(12, 25) to "Christmas Day", d(12, 26) to "Boxing Day")
        }
    }

    /** La festa del giorno di [t] (null = giorno normale). */
    fun on(t: Long, country: String, zone: ZoneId = ZoneId.systemDefault()): String? {
        val day = java.time.Instant.ofEpochMilli(t).atZone(zone).toLocalDate()
        return of(day.year, country)[day]
    }
}
