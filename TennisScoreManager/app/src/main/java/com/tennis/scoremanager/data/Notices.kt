// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import com.tennis.scoremanager.model.Lang
import com.tennis.scoremanager.ui.Strings
import java.io.File
import java.util.Calendar

/** Chi riceve un avviso: il suo testo (personale o quello di tutti) e i contatti. */
data class NoticeRecipient(val id: String, val name: String, val phone: String, val email: String, val text: String)

/**
 * Un avviso da mandare: [common] è il testo per tutti (email in copia nascosta, SMS a tutti, gruppo), i destinatari
 * hanno ciascuno il suo (WhatsApp o SMS uno per uno). [image] va con la condivisione (il tabellone).
 */
data class NoticePlan(
    val title: String,
    val subject: String,
    val common: String,
    val recipients: List<NoticeRecipient>,
    val image: File? = null,
    /** Il testo si può cambiare prima di mandarlo (messaggio libero): vale per tutti. */
    val editable: Boolean = false,
)

object Notices {
    /**
     * Il numero per WhatsApp (wa.me): solo cifre col prefisso internazionale. "+39 333…" e "0039 333…" restano;
     * un numero italiano senza prefisso (cellulare 3…, fisso 0…) prende il 39. null se non è un numero.
     */
    fun waNumber(raw: String): String? {
        val t = raw.trim()
        val digits = t.filter(Char::isDigit)
        if (digits.length < 6) return null
        return when {
            t.startsWith("+") -> digits
            digits.startsWith("00") -> digits.drop(2)
            (digits.startsWith("3") && digits.length in 9..10) || (digits.startsWith("0") && digits.length in 6..11) -> "39$digits"
            else -> digits
        }.takeIf { it.length >= 8 }
    }

    /** Il telefono a cui scrivere: il suo, o quello del genitore per un minorenne (o se lui non ne ha). */
    fun phoneOf(p: PlayerProfile, now: Long): String {
        val guardian = p.member?.guardianPhone.orEmpty()
        return if (guardian.isNotBlank() && (Members.minor(p, now) || p.phone.isBlank())) guardian else p.phone
    }

    private fun firstName(p: PlayerProfile): String = p.firstName.ifBlank { p.name }

    /** Il saluto: al genitore di un minorenne si scrive del figlio. */
    private fun hello(s: Strings, p: PlayerProfile, now: Long): String =
        if (Members.minor(p, now) && p.member?.guardianPhone?.isNotBlank() == true && p.phone.isBlank()) s.ntHelloParent(firstName(p)) else s.ntHello(firstName(p))

    private fun sign(club: String): List<String> = if (club.isBlank()) emptyList() else listOf("", club)

    fun recipient(p: PlayerProfile, text: String, now: Long) = NoticeRecipient(p.id, p.name, phoneOf(p, now), p.email, text)

    /** "sabato 14 novembre" (in tedesco "Samstag, 14. November", in inglese "Saturday 14 November"). */
    fun day(t: Long, lang: Lang): String {
        val pattern = if (lang == Lang.DE) "EEEE, d. MMMM" else "EEEE d MMMM"
        return java.text.SimpleDateFormat(pattern, Reports.locale(lang)).format(java.util.Date(t))
    }

    /** "sabato 14 novembre, 9:00 · Campo 2" (o "orario da definire"). */
    fun whenText(s: Strings, m: CTMatch, lang: Lang): String =
        listOfNotNull(m.at?.let { "${day(it, lang)}, ${Reports.time(it, lang)}" } ?: s.ntWhenTbd, m.court.ifBlank { null }).joinToString(" · ")

    /** Iscrizione al torneo: date, quota pagata o da pagare. */
    fun entryText(s: Strings, t: ClubTournament, p: PlayerProfile, book: ClubBook, club: String, lang: Lang, now: Long): String {
        val lines = mutableListOf(hello(s, p, now), s.ntEntered(t.name))
        t.start?.let { st -> lines += s.ctDates(day(st, lang), day(t.end ?: st, lang)) }
        if (t.feeCents > 0) {
            val fee = Fees.money(t.feeCents, lang.locale)
            lines += if (ClubTournaments.paid(t, p, book)) s.ntFeePaid(fee) else s.ntFeeDue(fee)
        }
        return (lines + sign(club)).joinToString("\n")
    }

    /** La prossima partita di [p] nel torneo. */
    fun matchText(s: Strings, t: ClubTournament, m: CTMatch, round: String, names: (String?) -> String, p: PlayerProfile, club: String, lang: Lang, now: Long): String =
        (listOf(hello(s, p, now), s.ntMatch(round, t.name), "${names(m.a)} – ${names(m.b)}", whenText(s, m, lang)) + sign(club)).joinToString("\n")

    /** L'ordine di gioco di un giorno (o tutte le partite in programma), per il gruppo. */
    fun orderOfPlay(s: Strings, t: ClubTournament, ms: List<CTMatch>, round: (CTMatch) -> String, names: (String?) -> String, title: String, lang: Lang, club: String): String {
        val lines = mutableListOf(title)
        for (m in ms.sortedWith(compareBy({ it.at ?: Long.MAX_VALUE }, { it.court }))) {
            val time = m.at?.let { Reports.time(it, lang) } ?: "--:--"
            lines += listOf(time, m.court.ifBlank { null }, "${names(m.a)} – ${names(m.b)}", "(${round(m)})").filterNotNull().joinToString(" ")
        }
        return (lines + sign(club)).joinToString("\n")
    }

    /** Il sollecito: cosa resta da pagare, voce per voce, e il totale. */
    fun duesText(s: Strings, p: PlayerProfile, open: List<Charge>, club: String, lang: Lang, now: Long, pay: List<String> = emptyList()): String {
        val lines = mutableListOf(hello(s, p, now), s.ntDues)
        for (c in open) lines += "• ${c.label}: ${Fees.money(c.left, lang.locale)}" + (c.due?.let { " (" + s.feeDueShort(day(it, lang)) + ")" } ?: "")
        if (open.size > 1) lines += s.ntTotal(Fees.money(open.sumOf { it.left }, lang.locale))
        if (pay.isNotEmpty()) lines += listOf(s.ntPayHow) + pay
        return (lines + sign(club)).joinToString("\n")
    }

    /** Cosa manca al socio per essere in regola. */
    fun memberText(s: Strings, p: PlayerProfile, issues: List<String>, club: String, now: Long): String =
        (listOf(hello(s, p, now), s.ntMemberTodo) + issues.map { "• $it" } + listOf(s.ntMemberHow) + sign(club)).joinToString("\n")

    /** "• Satispay: link", "• Bonifico: IT60…": i modi online del circolo, per gli avvisi. */
    fun payLines(s: Strings, book: ClubBook): List<String> =
        Fees.onlineTypes(book) { com.tennis.scoremanager.ui.payMethodName(s, it) }.map { "• ${it.name}: ${it.details.trim()}" }

    /** Le partite con l'orario in quel giorno. */
    fun onDay(ms: List<CTMatch>, dayMs: Long): List<CTMatch> {
        val d = Calendar.getInstance().apply { timeInMillis = dayMs }
        return ms.filter { m ->
            m.at?.let { Calendar.getInstance().apply { timeInMillis = it } }?.let { c -> c.get(Calendar.YEAR) == d.get(Calendar.YEAR) && c.get(Calendar.DAY_OF_YEAR) == d.get(Calendar.DAY_OF_YEAR) } == true
        }
    }
}
