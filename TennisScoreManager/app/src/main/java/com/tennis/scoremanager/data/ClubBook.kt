// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import kotlinx.serialization.Serializable
import java.util.Calendar
import java.util.Locale

/** Che cosa si paga: la quota sociale, la tessera FITP (che passa dal circolo), un corso, un torneo, altro. */
@Serializable
enum class FeeKind { MEMBERSHIP, FITP, COURSE, TOURNAMENT, OTHER }

@Serializable
enum class PayMethod { CASH, TRANSFER, POS, SATISPAY, OTHER }

/**
 * Un modo di pagamento che il circolo accetta, deciso da chi lo gestisce ("Satispay", "PayPal", "Bonifico"…):
 * [details] il link, l'IBAN o il numero; [online] = si paga a distanza, e allora va negli avvisi. [method] è il tipo
 * per il registro (OTHER per quelli aggiunti).
 */
@Serializable
data class PayType(val id: String, val name: String, val details: String = "", val online: Boolean = false, val method: PayMethod = PayMethod.OTHER)

/** Una quota del circolo ("Quota sociale 2027", 150 €): si addebita ai soci. Importi in centesimi. */
@Serializable
data class Fee(
    val id: String,
    val name: String,
    val cents: Int,
    val kind: FeeKind = FeeKind.OTHER,
    /** Da pagare entro (null = senza scadenza). */
    val due: Long? = null,
    val createdAt: Long = 0,
    /** Il torneo a cui appartiene (quota d'iscrizione), se c'è. */
    val tournament: String? = null,
)

/** Un versamento, con il numero della ricevuta ("12/2026"). */
@Serializable
data class Payment(
    val id: String,
    val cents: Int,
    val at: Long,
    val method: PayMethod,
    /** CRO del bonifico, causale, numero dello scontrino del POS… */
    val ref: String = "",
    val receipt: String = "",
    /** Il nome del modo di pagamento del circolo ("PayPal"); vuoto = quello di [method]. */
    val methodName: String = "",
)

/**
 * Quello che una persona deve: una quota (o un importo libero) e i suoi versamenti. [name] è il nome di quel giorno,
 * così il registro resta leggibile anche se il giocatore viene cancellato (i pagamenti fatti si tengono).
 */
@Serializable
data class Charge(
    val id: String,
    /** PlayerProfile.id ("" se il giocatore è stato cancellato). */
    val player: String,
    val name: String,
    val fee: String? = null,
    val label: String,
    val cents: Int,
    val createdAt: Long,
    val due: Long? = null,
    val payments: List<Payment> = emptyList(),
    /** Annullato (storno): non si deve più, i pagamenti fatti restano. */
    val cancelled: Boolean = false,
    /** Il tipo di un addebito senza quota (corso, tessera FITP…), per filtrarli; null se non si sa. */
    val kind: FeeKind? = null,
) {
    val paid: Int get() = payments.sumOf { it.cents }
    val left: Int get() = if (cancelled) 0 else (cents - paid).coerceAtLeast(0)
    val settled: Boolean get() = left == 0
}

/** Il registro del circolo: quote, addebiti coi pagamenti e la numerazione delle ricevute (una per anno). */
@Serializable
data class ClubBook(
    val fees: List<Fee> = emptyList(),
    val charges: List<Charge> = emptyList(),
    val receiptYear: Int = 0,
    val receiptNext: Int = 1,
    /** I modi di pagamento del circolo (vuoto = quelli proposti: contanti, POS, bonifico, Satispay). */
    val payTypes: List<PayType> = emptyList(),
)

object Fees {
    /** I modi proposti finché il circolo non sceglie i suoi ([names]: i nomi nella lingua dell'app). */
    fun defaultTypes(names: (PayMethod) -> String): List<PayType> = listOf(
        PayType("cash", names(PayMethod.CASH), method = PayMethod.CASH),
        PayType("pos", names(PayMethod.POS), method = PayMethod.POS),
        PayType("transfer", names(PayMethod.TRANSFER), online = true, method = PayMethod.TRANSFER),
        PayType("satispay", names(PayMethod.SATISPAY), online = true, method = PayMethod.SATISPAY),
    )

    /** I modi di pagamento del circolo, o quelli proposti. */
    fun types(book: ClubBook, names: (PayMethod) -> String): List<PayType> = book.payTypes.ifEmpty { defaultTypes(names) }

    /** I modi a distanza con link, IBAN o numero: "Satispay: link". */
    fun onlineTypes(book: ClubBook, names: (PayMethod) -> String): List<PayType> = types(book, names).filter { it.online && it.details.isNotBlank() }

    enum class Status { PAID, PARTIAL, UNPAID, LATE, CANCELLED }

    fun newId(prefix: String): String = prefix + java.util.UUID.randomUUID().toString().take(12)

    private fun year(t: Long): Int = Calendar.getInstance().apply { timeInMillis = t }.get(Calendar.YEAR)

    fun status(c: Charge, now: Long): Status = when {
        c.cancelled -> Status.CANCELLED
        c.settled -> Status.PAID
        c.due != null && endOfDay(c.due) < now -> Status.LATE
        c.paid > 0 -> Status.PARTIAL
        else -> Status.UNPAID
    }

    private fun endOfDay(t: Long): Long = Calendar.getInstance().apply {
        timeInMillis = t
        set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59)
    }.timeInMillis

    /** Gli addebiti di una persona (anche quelli col suo vecchio identificativo, se è stata unita a un'altra). */
    fun of(book: ClubBook, p: PlayerProfile): List<Charge> {
        val keys = setOf(p.id) + p.aliases
        return book.charges.filter { it.player in keys }.sortedByDescending { it.createdAt }
    }

    /** Quanto deve ancora, in centesimi. */
    fun owed(book: ClubBook, p: PlayerProfile): Int = of(book, p).sumOf { it.left }

    /** Addebita [fee] ai [people] che non l'hanno ancora; restituisce il registro e quanti addebiti nuovi. */
    fun chargeAll(book: ClubBook, fee: Fee, people: List<PlayerProfile>, now: Long): Pair<ClubBook, Int> {
        val already = book.charges.filter { it.fee == fee.id && !it.cancelled }.map { it.player }.toSet()
        val new = people.filter { p -> p.id !in already && p.aliases.none { it in already } }.map { p ->
            Charge(newId("c"), p.id, p.name, fee.id, fee.name, fee.cents, now, fee.due)
        }
        return book.copy(charges = book.charges + new) to new.size
    }

    /** Un addebito singolo. */
    fun charge(book: ClubBook, p: PlayerProfile, label: String, cents: Int, now: Long, fee: Fee? = null, kind: FeeKind? = null): ClubBook =
        book.copy(charges = book.charges + Charge(newId("c"), p.id, p.name, fee?.id, label, cents, now, fee?.due, kind = kind))

    /**
     * Registra un versamento di [cents] sull'addebito [chargeId] col numero di ricevuta dell'anno del pagamento
     * ("12/2026"; a gennaio si riparte da 1). null se l'addebito non c'è o l'importo non va.
     */
    fun pay(book: ClubBook, chargeId: String, cents: Int, at: Long, method: PayMethod, ref: String, methodName: String = ""): Pair<ClubBook, Payment>? {
        val c = book.charges.firstOrNull { it.id == chargeId } ?: return null
        if (cents <= 0 || c.cancelled) return null
        val y = year(at)
        val n = if (book.receiptYear == y) book.receiptNext else 1
        val pay = Payment(newId("p"), cents, at, method, ref.trim(), "$n/$y", methodName.trim())
        val charges = book.charges.map { if (it.id == chargeId) it.copy(payments = it.payments + pay) else it }
        return book.copy(charges = charges, receiptYear = y, receiptNext = n + 1) to pay
    }

    /** Toglie un pagamento registrato per sbaglio (il numero della ricevuta non si riusa). */
    fun unpay(book: ClubBook, chargeId: String, paymentId: String): ClubBook =
        book.copy(charges = book.charges.map { c -> if (c.id == chargeId) c.copy(payments = c.payments.filter { it.id != paymentId }) else c })

    /** Annulla un addebito con dei pagamenti (storno), cancella uno senza. */
    fun cancel(book: ClubBook, chargeId: String): ClubBook {
        val c = book.charges.firstOrNull { it.id == chargeId } ?: return book
        return if (c.payments.isEmpty()) book.copy(charges = book.charges - c)
        else book.copy(charges = book.charges.map { if (it.id == chargeId) it.copy(cancelled = true) else it })
    }

    /** I pagamenti di un anno, dal più vecchio, con l'addebito di ciascuno. */
    fun payments(book: ClubBook, year: Int): List<Pair<Charge, Payment>> =
        book.charges.flatMap { c -> c.payments.filter { year(it.at) == year }.map { c to it } }.sortedBy { it.second.at }

    fun income(book: ClubBook, year: Int): Int = payments(book, year).sumOf { it.second.cents }

    /** Quanto c'è da incassare e da quante persone. */
    fun open(book: ClubBook): Pair<Int, Int> {
        val open = book.charges.filter { it.left > 0 }
        return open.sumOf { it.left } to open.map { it.player.ifEmpty { it.name } }.distinct().size
    }

    /** Quante persone hanno pagato [fee] e a quante è stata addebitata. */
    fun feeProgress(book: ClubBook, fee: Fee): Pair<Int, Int> {
        val list = book.charges.filter { it.fee == fee.id && !it.cancelled }
        return list.count { it.settled } to list.size
    }

    /** Quando un giocatore viene cancellato: via gli addebiti senza pagamenti, gli altri restano col nome (registro). */
    fun forget(book: ClubBook, keys: Set<String>): ClubBook = book.copy(
        charges = book.charges.mapNotNull { c ->
            if (c.player !in keys) c else if (c.payments.isEmpty()) null else c.copy(player = "")
        },
    )

    /** Due giocatori uniti: gli addebiti dell'altro passano a chi resta. */
    fun remap(book: ClubBook, from: Set<String>, to: String): ClubBook =
        book.copy(charges = book.charges.map { if (it.player in from) it.copy(player = to) else it })

    /** "150,00 €" nella lingua dell'app. */
    fun money(cents: Int, locale: Locale): String = java.text.NumberFormat.getCurrencyInstance(locale).apply {
        currency = java.util.Currency.getInstance("EUR")
    }.format(cents / 100.0)

    /** "150", "150,5", "150.50", "1.234,56" → centesimi; null se non è un importo. */
    fun parse(text: String): Int? {
        var t = text.trim().replace("€", "").replace(" ", "")
        if (t.isEmpty()) return null
        // Con entrambi i separatori, l'ultimo è quello dei decimali.
        val lastComma = t.lastIndexOf(','); val lastDot = t.lastIndexOf('.')
        t = when {
            lastComma >= 0 && lastDot >= 0 -> if (lastComma > lastDot) t.replace(".", "").replace(',', '.') else t.replace(",", "")
            lastComma >= 0 -> t.replace(',', '.')
            else -> t
        }
        if (!Regex("^\\d{1,6}(\\.\\d{1,2})?$").matches(t)) return null
        return Math.round(t.toDouble() * 100).toInt()
    }

    /**
     * I pagamenti dell'anno in CSV per il commercialista: punto e virgola e, nelle lingue con la virgola decimale,
     * importi con la virgola (si aprono così in un foglio di calcolo).
     */
    fun csv(book: ClubBook, year: Int, header: String, method: (PayMethod) -> String, date: (Long) -> String, locale: Locale): String {
        val sep = java.text.DecimalFormatSymbols.getInstance(locale).decimalSeparator
        fun q(s: String) = if (s.any { it == ';' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
        val rows = payments(book, year).map { (c, p) ->
            listOf(date(p.at), p.receipt, c.name, c.label, p.methodName.ifBlank { method(p.method) }, p.ref, "%d%c%02d".format(p.cents / 100, sep, p.cents % 100)).joinToString(";") { q(it) }
        }
        return (listOf(header) + rows).joinToString("\r\n") + "\r\n"
    }
}
