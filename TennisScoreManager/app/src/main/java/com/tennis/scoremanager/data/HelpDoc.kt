// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import java.text.Normalizer

/** Un pezzo di testo della guida, con lo stile: [ref] è il numero di una sezione da aprire toccandolo ("6.4"). */
data class HelpSpan(val text: String, val bold: Boolean = false, val italic: Boolean = false, val code: Boolean = false, val ref: String? = null)

/** Un blocco della guida: paragrafo, voce di elenco (puntato o numerato, [indent] = livello) o tabella. */
sealed interface HelpBlock {
    data class Para(val spans: List<HelpSpan>, val indent: Int = 0) : HelpBlock
    data class Bullet(val spans: List<HelpSpan>, val indent: Int, val number: Int? = null) : HelpBlock
    data class Table(val rows: List<List<List<HelpSpan>>>) : HelpBlock
}

/** Una sezione: [id] "6.4", livello 2 (capitolo) o 3, titolo senza numero, i blocchi e il testo semplice per cercare. */
data class HelpSection(val id: String, val level: Int, val title: String, val blocks: List<HelpBlock>, val plain: String)

/**
 * La guida dell'app (assets/help/<lingua>.md, fatta da deliver/make_help.py dalle guide del repository): un Markdown
 * semplice con titoli ## e ###, elenchi, tabelle, **grassetto**, *corsivo* e `codice`.
 */
object HelpDoc {
    private val heading = Regex("""^(#{2,3}) (\d+(?:\.\d+)?)\.? (.*)$""")
    private val numbered = Regex("""^(\s*)(\d+)\. (.*)$""")
    private val bullet = Regex("""^(\s*)[-*] (.*)$""")

    fun parse(md: String): List<HelpSection> {
        data class Open(val id: String, val level: Int, val title: String, val lines: MutableList<String> = mutableListOf())
        val raw = ArrayList<Open>()
        for (line in md.lines()) {
            val h = heading.matchEntire(line)
            if (h != null) raw += Open(h.groupValues[2], h.groupValues[1].length, h.groupValues[3].trim())
            else raw.lastOrNull()?.lines?.add(line)
        }
        val ids = raw.map { it.id }.toSet()
        return raw.map { o ->
            val blocks = blocks(o.lines, ids)
            HelpSection(o.id, o.level, o.title, blocks, plainOf(o.title, blocks))
        }
    }

    private fun blocks(lines: List<String>, ids: Set<String>): List<HelpBlock> {
        val out = ArrayList<HelpBlock>()
        var table: MutableList<List<List<HelpSpan>>>? = null
        for (line in lines) {
            if (line.trimStart().startsWith("|")) {
                val cells = line.trim().trim('|').split('|').map { it.trim() }
                // La riga |---|---| separa l'intestazione: non si mostra.
                if (cells.all { c -> c.isNotEmpty() && c.all { it == '-' || it == ':' } }) continue
                if (table == null) table = mutableListOf<List<List<HelpSpan>>>().also { out += HelpBlock.Table(it) }
                table += cells.map { inline(it, ids) }
                continue
            }
            table = null
            if (line.isBlank()) continue
            // Rientro: 2-3 spazi per livello.
            fun level(spaces: Int) = if (spaces == 0) 0 else (spaces + 2) / 3
            val n = numbered.matchEntire(line)
            val b = bullet.matchEntire(line)
            out += when {
                n != null -> HelpBlock.Bullet(inline(n.groupValues[3], ids), level(n.groupValues[1].length), n.groupValues[2].toInt())
                b != null -> HelpBlock.Bullet(inline(b.groupValues[2], ids), level(b.groupValues[1].length))
                else -> HelpBlock.Para(inline(line.trim(), ids), level(line.length - line.trimStart().length))
            }
        }
        return out
    }

    /**
     * Grassetto, corsivo e codice; i rimandi tra parentesi a sezioni che ci sono ("(6.4)", "(4.1, 6.3)") diventano
     * toccabili.
     */
    fun inline(text: String, ids: Set<String> = emptySet()): List<HelpSpan> {
        val out = ArrayList<HelpSpan>()
        val sb = StringBuilder()
        var bold = false
        var italic = false
        fun flush() {
            if (sb.isEmpty()) return
            out += refs(sb.toString(), bold, italic, ids)
            sb.clear()
        }
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == '`' -> {
                    val end = text.indexOf('`', i + 1)
                    if (end < 0) { sb.append(c); i++; continue }
                    flush()
                    out += HelpSpan(text.substring(i + 1, end), bold, italic, code = true)
                    i = end + 1
                    continue
                }
                text.startsWith("**", i) -> { flush(); bold = !bold; i += 2; continue }
                // Un asterisco da solo è corsivo solo se apre o chiude una parola (non "5 * 3").
                c == '*' && (italic || (i + 1 < text.length && !text[i + 1].isWhitespace())) -> { flush(); italic = !italic; i++; continue }
                else -> sb.append(c)
            }
            i++
        }
        flush()
        return out
    }

    private val refRegex = Regex("""(?<=\(|, )(\d+\.\d+|\d+)(?=[),])""")

    private fun refs(s: String, bold: Boolean, italic: Boolean, ids: Set<String>): List<HelpSpan> {
        if (ids.isEmpty()) return listOf(HelpSpan(s, bold, italic))
        val out = ArrayList<HelpSpan>()
        var last = 0
        for (m in refRegex.findAll(s)) {
            if (m.value !in ids) continue
            if (m.range.first > last) out += HelpSpan(s.substring(last, m.range.first), bold, italic)
            out += HelpSpan(m.value, bold, italic, ref = m.value)
            last = m.range.last + 1
        }
        if (last < s.length) out += HelpSpan(s.substring(last), bold, italic)
        return out
    }

    private fun plainOf(title: String, blocks: List<HelpBlock>): String = buildString {
        append(title).append('\n')
        for (b in blocks) {
            when (b) {
                is HelpBlock.Para -> b.spans.forEach { append(it.text) }
                is HelpBlock.Bullet -> b.spans.forEach { append(it.text) }
                is HelpBlock.Table -> b.rows.forEach { r -> r.forEach { c -> c.forEach { append(it.text).append(' ') } } }
            }
            append('\n')
        }
    }

    /** Senza accenti e in minuscolo: "volee" trova "Volée". */
    fun fold(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()

    /** Le sezioni che contengono tutte le parole di [query], i titoli prima. */
    fun search(sections: List<HelpSection>, query: String): List<HelpSection> {
        val words = fold(query).split(Regex("\\s+")).filter { it.length >= 2 }
        if (words.isEmpty()) return emptyList()
        val hits = sections.filter { s -> fold(s.plain).let { p -> words.all { it in p } } }
        return hits.sortedByDescending { s -> words.count { it in fold(s.title) } }
    }

    /** Un pezzo di testo intorno alla prima parola trovata, per l'elenco dei risultati. */
    fun snippet(section: HelpSection, query: String, width: Int = 120): String {
        val text = section.plain.substringAfter('\n').replace('\n', ' ')
        val w = fold(query).split(Regex("\\s+")).firstOrNull { it.length >= 2 } ?: return text.take(width)
        val at = fold(text).indexOf(w).takeIf { it >= 0 } ?: return text.take(width)
        val from = (at - width / 3).coerceAtLeast(0)
        return (if (from > 0) "…" else "") + text.substring(from, (from + width).coerceAtMost(text.length)).trim() + if (from + width < text.length) "…" else ""
    }
}
