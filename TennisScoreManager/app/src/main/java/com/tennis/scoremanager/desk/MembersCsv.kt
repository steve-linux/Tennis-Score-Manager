// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.desk

import com.tennis.scoremanager.data.Membership
import com.tennis.scoremanager.data.PlayerProfile
import com.tennis.scoremanager.data.Players
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * L'elenco dei soci da un foglio di calcolo (CSV): il primo inserimento, che dal telefono sarebbe lungo. Le colonne si
 * riconoscono dal titolo, in sei lingue e in qualsiasi ordine. I dati si leggono e si mostrano prima: entrano solo
 * dopo la conferma. Un socio già presente si completa, senza cancellare niente di quello che ha.
 */
object MembersCsv {
    const val MAX_ROWS = 5000

    data class Row(
        val line: Int, val first: String = "", val last: String = "", val phone: String = "", val email: String = "", val birth: Long? = null,
        val number: String = "", val fitp: String = "", val ranking: String = "", val medicalUntil: Long? = null,
        /** Il giocatore già presente, se c'è. */
        val match: String? = null, val error: String? = null,
    )

    private val COLUMNS: Map<String, List<String>> = mapOf(
        "first" to listOf("nome", "first name", "firstname", "given name", "prenom", "vorname", "nombre", "primeiro nome"),
        "last" to listOf("cognome", "last name", "lastname", "surname", "family name", "nom de famille", "nachname", "familienname", "apellido", "apellidos", "apelido", "sobrenome"),
        "full" to listOf("nome e cognome", "cognome e nome", "nominativo", "socio", "full name", "name", "nom", "nombre completo", "nome completo"),
        "phone" to listOf("telefono", "cellulare", "tel", "phone", "mobile", "telephone", "portable", "telefon", "handy", "movil", "telemovel", "telefone"),
        "email" to listOf("email", "e mail", "mail", "posta elettronica", "courriel", "correo", "correo electronico"),
        "birth" to listOf("data di nascita", "nascita", "nato il", "birth date", "date of birth", "birthday", "date de naissance", "geburtsdatum", "fecha de nacimiento", "data de nascimento"),
        "number" to listOf("tessera", "tessera circolo", "numero tessera", "n tessera", "numero socio", "member number", "membership number", "numero de membre", "mitgliedsnummer", "numero de socio", "numero de socio",
            // Le colonne dell'esportazione della Segreteria da PC, nelle sei lingue.
            "tessera del circolo", "club card", "carte du club", "vereinsausweis", "carne del club", "cartao do clube"),
        "fitp" to listOf("fitp", "tessera fitp", "numero fitp", "n fitp", "fit", "federation number", "licence", "lizenz", "licencia", "licenca",
            "federation card", "licence federale", "verbandslizenz", "licencia federativa", "carteira federativa"),
        "ranking" to listOf("classifica", "ranking", "classement", "rangliste", "clasificacion", "classificacao"),
        "medical" to listOf("scadenza certificato", "certificato medico", "certificato", "scadenza visita", "medical until", "medical certificate", "certificat medical", "arztliches attest", "certificado medico",
            "certificato fino al", "certificat jusqu au", "attest bis", "certificado hasta", "atestado ate"),
    )

    private val DATES = listOf("d/M/uuuu", "uuuu-M-d", "d-M-uuuu", "d.M.uuuu", "d/M/uu").map { DateTimeFormatter.ofPattern(it) }

    /** Una data di un foglio di calcolo, a mezzogiorno come le altre date dei soci; null se non si capisce. */
    fun date(text: String, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): Long? {
        val t = text.trim()
        for (f in DATES) {
            val d = runCatching { LocalDate.parse(t, f) }.getOrNull() ?: continue
            return d.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        }
        return null
    }

    private fun plain(s: String): String = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "").lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()

    /** Il separatore della riga dei titoli: Excel in italiano usa il punto e virgola. */
    private fun delimiter(header: String): Char = listOf(';', '\t', ',').maxBy { d -> header.count { it == d } }

    /** Le righe del testo, con i campi tra virgolette (che possono contenere separatori e a capo). */
    fun table(text: String): List<Pair<Int, List<String>>> {
        val t = text.removePrefix("﻿")
        val d = delimiter(t.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty())
        val rows = mutableListOf<Pair<Int, List<String>>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var line = 1
        var start = 1
        var i = 0
        fun endRow() {
            row.add(cell.toString().trim()); cell.setLength(0)
            if (row.any { it.isNotEmpty() }) rows += start to row
            row = mutableListOf()
        }
        while (i < t.length) {
            val c = t[i]
            when {
                quoted && c == '"' && t.getOrNull(i + 1) == '"' -> { cell.append('"'); i++ }
                c == '"' && (quoted || cell.isBlank()) -> { quoted = !quoted; if (quoted) cell.setLength(0) }
                !quoted && c == d -> { row.add(cell.toString().trim()); cell.setLength(0) }
                !quoted && c == '\r' -> {}
                !quoted && c == '\n' -> { endRow(); line++; start = line }
                else -> { if (c == '\n') line++; cell.append(c) }
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) endRow()
        return rows
    }

    /** Legge il foglio. null = non si riconoscono le colonne del nome. */
    fun parse(text: String, players: List<PlayerProfile>): List<Row>? {
        val rows = table(text)
        val head = rows.firstOrNull()?.second ?: return null
        val cols = head.map { h -> plain(h).let { p -> COLUMNS.entries.firstOrNull { p in it.value }?.key } }
        fun at(r: List<String>, key: String) = cols.indexOf(key).takeIf { it >= 0 }?.let { r.getOrNull(it) }.orEmpty().trim()
        val hasNames = "full" in cols || ("first" in cols && "last" in cols)
        if (!hasNames) return null
        return rows.drop(1).take(MAX_ROWS).map { (line, r) ->
            var first = at(r, "first"); var last = at(r, "last")
            if (first.isEmpty() || last.isEmpty()) splitFull(at(r, "full"))?.let { (f, l) -> first = first.ifEmpty { f }; last = last.ifEmpty { l } }
            val birthText = at(r, "birth"); val medText = at(r, "medical")
            val birth = date(birthText); val med = date(medText)
            val base = Row(line, first, last, at(r, "phone"), at(r, "email").lowercase(), birth, at(r, "number"), at(r, "fitp"), at(r, "ranking"), med)
            val err = when {
                first.isEmpty() || last.isEmpty() -> "NAME"
                birthText.isNotEmpty() && birth == null -> "DATE"
                medText.isNotEmpty() && med == null -> "DATE"
                else -> null
            }
            base.copy(match = find(players, base)?.id, error = err)
        }
    }

    /** "Rossi Mario" o "Rossi, Mario": il cognome è la prima parola solo con la virgola; se no l'ultima. */
    private fun splitFull(full: String): Pair<String, String>? {
        val t = full.trim()
        if (',' in t) return t.substringAfter(',').trim().takeIf { it.isNotEmpty() }?.let { it to t.substringBefore(',').trim() }
        val w = t.split(Regex("\\s+")).filter { it.isNotEmpty() }
        return if (w.size < 2) null else w.dropLast(1).joinToString(" ") to w.last()
    }

    private fun same(a: String, b: String) = plain(a) == plain(b)

    /** Lo stesso giocatore: tessera FITP uguale, o stesso nome con la stessa data di nascita (o senza date). */
    private fun find(players: List<PlayerProfile>, r: Row): PlayerProfile? {
        if (r.fitp.isNotEmpty()) players.firstOrNull { it.member?.fitpNumber?.trim()?.equals(r.fitp, ignoreCase = true) == true }?.let { return it }
        val named = players.filter { same(it.firstName, r.first) && same(it.lastName, r.last) && r.first.isNotEmpty() }
        return named.firstOrNull { p ->
            p.birth == null || r.birth == null || LocalDate.ofEpochDay(p.birth / 86_400_000L) == LocalDate.ofEpochDay(r.birth / 86_400_000L)
        }
    }

    /** I giocatori da salvare per le righe senza errori. Quelli presenti diventano soci e prendono solo i dati mancanti. */
    fun apply(rows: List<Row>, players: List<PlayerProfile>, now: Long): List<PlayerProfile> {
        val done = LinkedHashMap<String, PlayerProfile>()
        for (r in rows.filter { it.error == null }) {
            val old = r.match?.let { id -> done[id] ?: players.firstOrNull { it.id == id } }
            val p = old ?: PlayerProfile(Players.newId(), PlayerProfile.display(r.first, r.last, ""), r.first, r.last, createdAt = now)
            val m = p.member ?: Membership(since = now)
            done[p.id] = p.copy(
                phone = p.phone.ifBlank { r.phone }, email = p.email.ifBlank { r.email }, birth = p.birth ?: r.birth,
                member = m.copy(
                    number = m.number.ifBlank { r.number }, fitpNumber = m.fitpNumber.ifBlank { r.fitp }, ranking = m.ranking.ifBlank { r.ranking },
                    medicalUntil = m.medicalUntil ?: r.medicalUntil,
                ),
            )
        }
        return done.values.toList()
    }
}
