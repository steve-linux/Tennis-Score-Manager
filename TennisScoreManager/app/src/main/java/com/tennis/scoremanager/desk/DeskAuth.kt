// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.desk

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Chi può entrare nella Segreteria da PC. Due passi: il telefono inquadra il QR sullo schermo del PC (oppure sul PC si
 * scrive il codice che si legge sul telefono, usa e getta) e poi si tocca "Consenti" sul telefono. Una sola sessione alla volta, legata all'indirizzo del PC; la chiave vive in
 * memoria come impronta e sparisce spegnendo la Segreteria. Tutto sotto un solo lucchetto: i metodi sono brevi.
 */
class DeskAuth(private val clock: () -> Long = System::currentTimeMillis, private val onChange: () -> Unit = {}) {

    /** Un PC che ha scritto il codice giusto e aspetta il "Consenti" sul telefono. */
    data class Pending(val id: String, val ip: String, val at: Long, val approved: Boolean = false)

    /** Il QR mostrato da un PC: [secret] sta solo dentro il QR, [id] serve al PC per chiedere com'è andata. */
    data class Qr(val id: String, val secret: String, val ip: String, val at: Long) {
        val text: String get() = QR_PREFIX + secret
    }

    private class Session(val hash: String, val ip: String, val since: Long, var last: Long)

    private val rnd = SecureRandom()
    private var code: String = ""
    private var codeAt = 0L
    private var wrong = 0
    private var blockedUntil = 0L
    private var strikes = 0
    private var session: Session? = null
    private var pendingPair: Pending? = null
    /** I QR aperti, uno per indirizzo. */
    private val qrs = LinkedHashMap<String, Qr>()

    /** Il codice da mostrare sul telefono, sempre valido; "" se c'è già un PC collegato o in attesa. */
    @get:Synchronized val shownCode: String get() {
        if (session != null || pendingPair != null) return ""
        if (code.isEmpty() || clock() - codeAt > CODE_MS) newCode()
        return code
    }

    /** Il PC in attesa di "Consenti" (non ancora approvato). */
    @get:Synchronized val pending: Pending? get() = expirePending()?.takeIf { !it.approved }

    /** L'indirizzo del PC collegato (null = nessuno). */
    @get:Synchronized val connected: String? get() = validSession()?.ip

    /** Fine dell'ultima attività: serve a spegnere il server quando nessuno lo usa. */
    @get:Synchronized val lastUse: Long get() = session?.last ?: 0

    private fun newCode() {
        code = buildString { repeat(CODE_LEN) { append(ALPHABET[rnd.nextInt(ALPHABET.length)]) } }
        codeAt = clock()
        wrong = 0
    }

    private fun expirePending(): Pending? {
        val p = pendingPair ?: return null
        if (clock() - p.at > PENDING_MS) { pendingPair = null; return null }
        return p
    }

    /**
     * Prova del codice. Riuscita: il PC aspetta il consenso e riceve l'id dell'attesa. Dopo [MAX_WRONG] errori il codice
     * cambia, e ogni volta che succede si aspetta di più: da fuori non si può indovinarlo a tentativi.
     */
    @Synchronized
    fun submit(typed: String, ip: String): Result {
        val now = clock()
        if (now < blockedUntil) return Result.Blocked
        if (session != null || expirePending() != null) return Result.Busy
        if (code.isEmpty() || now - codeAt > CODE_MS) newCode()
        val clean = typed.uppercase().filter { it.isLetterOrDigit() }.toByteArray()
        if (!MessageDigest.isEqual(clean, code.toByteArray())) {
            if (++wrong >= MAX_WRONG) {
                strikes++
                blockedUntil = now + BLOCK_MS * (1L shl (strikes - 1).coerceAtMost(6))
                newCode()
            }
            return Result.Wrong
        }
        code = ""
        strikes = 0
        val p = Pending(token(), ip, now)
        pendingPair = p
        onChange()
        return Result.Waiting(p.id)
    }

    /**
     * Il QR per il PC [ip]: lo stesso finché è giovane, poi uno nuovo. Pochi e a scadenza: chi è in rete può chiederne,
     * ma non serve a niente senza un telefono che lo inquadri. null se c'è già un PC collegato o in attesa.
     */
    @Synchronized
    fun qr(ip: String): Qr? {
        val now = clock()
        if (validSession() != null || expirePending() != null) return null
        qrs.values.removeAll { now - it.at > QR_MS }
        qrs[ip]?.takeIf { now - it.at < QR_MS / 2 }?.let { return it }
        qrs.remove(ip)
        if (qrs.size >= MAX_QRS) qrs.remove(qrs.keys.first())
        return Qr(token(), token(), ip, now).also { qrs[ip] = it }
    }

    /** Il telefono ha inquadrato [text]: se è un QR aperto, quel PC aspetta il "Consenti". */
    @Synchronized
    fun scan(text: String): Boolean {
        val now = clock()
        if (validSession() != null || expirePending() != null) return false
        val found = qrs.values.firstOrNull { now - it.at <= QR_MS && same(it.text, text.trim()) } ?: return false
        qrs.clear()
        code = ""
        pendingPair = Pending(found.id, found.ip, now)
        onChange()
        return true
    }

    /** La risposta del telefono al PC in attesa. */
    @Synchronized
    fun answer(allow: Boolean) {
        val p = expirePending() ?: return
        pendingPair = if (allow) p.copy(approved = true) else null
        onChange()
    }

    /** Il PC chiede com'è andata. Appena approvato riceve la chiave, una volta sola e solo dallo stesso indirizzo. */
    @Synchronized
    fun poll(id: String, ip: String): Poll {
        val now0 = clock()
        val p = expirePending()
        if (p == null || !same(p.id, id)) {
            val open = qrs.values.any { same(it.id, id) && it.ip == ip && now0 - it.at <= QR_MS }
            return if (open) Poll.Wait else Poll.Refused
        }
        if (p.ip != ip) return Poll.Refused
        if (!p.approved) return Poll.Confirm
        pendingPair = null
        val t = token()
        val now = clock()
        session = Session(hash(t), ip, now, now)
        onChange()
        return Poll.Granted(t)
    }

    private fun validSession(): Session? {
        val s = session ?: return null
        val now = clock()
        if (now - s.last > IDLE_MS || now - s.since > MAX_MS) { session = null; onChange(); return null }
        return s
    }

    /** La chiave è quella giusta, non scaduta e arriva dal PC abbinato. */
    @Synchronized
    fun check(token: String?, ip: String): Boolean {
        val s = validSession() ?: return false
        if (token == null || !same(s.hash, hash(token)) || s.ip != ip) return false
        s.last = clock()
        return true
    }

    /** Chiude la sessione (dal PC o dal telefono) e, con un nuovo abbinamento, si riparte da un codice nuovo. */
    @Synchronized
    fun logout() {
        session = null
        pendingPair = null
        code = ""
        qrs.clear()
        onChange()
    }

    private fun token(): String = ByteArray(32).also { rnd.nextBytes(it) }.let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
    private fun hash(s: String): String = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(s.toByteArray()))
    private fun same(a: String, b: String) = MessageDigest.isEqual(a.toByteArray(), b.toByteArray())

    sealed interface Result {
        data class Waiting(val id: String) : Result
        data object Wrong : Result
        data object Blocked : Result
        data object Busy : Result
    }

    sealed interface Poll {
        /** Nessuno ha ancora inquadrato il QR. */
        data object Wait : Poll
        /** Si aspetta il "Consenti" sul telefono. */
        data object Confirm : Poll
        data object Refused : Poll
        data class Granted(val token: String) : Poll
    }

    companion object {
        /** Lettere e cifre che non si confondono (niente 0/O, 1/I/L). */
        private const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
        const val CODE_LEN = 8
        const val CODE_MS = 5 * 60_000L
        const val PENDING_MS = 2 * 60_000L
        const val QR_PREFIX = "tsm-desk:"
        const val QR_MS = 2 * 60_000L
        private const val MAX_QRS = 16
        const val MAX_WRONG = 5
        const val BLOCK_MS = 30_000L
        /** Fermo da mezz'ora, o dopo 12 ore comunque: si riabbina. */
        const val IDLE_MS = 30 * 60_000L
        const val MAX_MS = 12 * 3_600_000L
    }
}
