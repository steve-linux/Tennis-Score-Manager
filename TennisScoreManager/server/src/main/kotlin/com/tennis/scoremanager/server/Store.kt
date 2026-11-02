// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.server

import com.tennis.scoremanager.data.ClubSync
import com.tennis.scoremanager.data.OnlineBooking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.TimeZone

/** Un giocatore invitato: [id] è il suo id nel database dell'app; [tokens] le impronte delle chiavi dei suoi telefoni. */
@Serializable
data class Player(val id: String, val name: String, val tokens: List<String> = emptyList(), val since: Long = 0, val lastSeen: Long = 0)

/** Un invito non ancora usato: si salva solo l'impronta del codice. */
@Serializable
data class Invite(val code: String, val player: String, val name: String, val expires: Long)

/**
 * Tutto quello che il server ricorda, in un solo file JSON. Codici e chiavi non ci sono mai in chiaro: solo le
 * loro impronte SHA-256.
 */
@Serializable
data class State(
    /** L'impronta della chiave della segreteria (null = non ancora abbinata). */
    val admin: String? = null,
    /** L'impronta del codice di abbinamento della segreteria, finché non la si abbina. */
    val setup: String? = null,
    val sync: ClubSync = ClubSync(),
    val syncedAt: Long = 0,
    val players: List<Player> = emptyList(),
    val invites: List<Invite> = emptyList(),
    val bookings: List<OnlineBooking> = emptyList(),
)

object Secrets {
    private val rnd = SecureRandom()
    /** Lettere e cifre che non si confondono (niente 0/O, 1/I/L). */
    private const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

    fun token(): String = ByteArray(32).also { rnd.nextBytes(it) }.let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }

    fun code(len: Int = 10): String = buildString { repeat(len) { append(ALPHABET[rnd.nextInt(ALPHABET.length)]) } }

    /** Il codice scritto dall'utente: maiuscolo, senza spazi né trattini. */
    fun normalize(code: String): String = code.uppercase().filter { it.isLetterOrDigit() }

    fun hash(s: String): String = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    fun id(prefix: String): String = prefix + token().take(12)
}

/** Lo stato su disco, cambiato un passo alla volta e riscritto con file temporaneo e rinomina. */
class Store(val dir: File, private val clock: () -> Long = System::currentTimeMillis) {
    private val file = File(dir, "state.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    @Volatile var state: State = load()
        private set

    init { applyTimeZone(state.sync.timeZone) }

    fun now(): Long = clock()

    private fun load(): State {
        dir.mkdirs()
        if (!file.exists()) return State()
        return json.decodeFromString(State.serializer(), file.readText())
    }

    @Synchronized
    fun <T> update(f: (State) -> Pair<State, T>): T {
        val (next, out) = f(state)
        if (next != state) {
            val tmp = File(dir, "state.json.tmp")
            tmp.writeText(json.encodeToString(State.serializer(), next))
            if (!tmp.renameTo(file)) { file.delete(); check(tmp.renameTo(file)) { "non riesco a scrivere ${file.path}" } }
            state = next
        }
        return out
    }

    /** Il codice per abbinare la segreteria: nuovo se la segreteria non c'è, scritto in setup-code.txt. */
    fun ensureSetupCode(reset: Boolean = false): String? {
        if (state.admin != null && !reset) return null
        val code = Secrets.code()
        update { it.copy(admin = null, setup = Secrets.hash(code)) to Unit }
        File(dir, SETUP_FILE).apply {
            writeText("$code\n")
            setReadable(false, false); setReadable(true, true)
        }
        return code
    }

    fun forgetSetupCode() { File(dir, SETUP_FILE).delete() }

    companion object {
        const val SETUP_FILE = "setup-code.txt"

        fun applyTimeZone(id: String) {
            if (id in TimeZone.getAvailableIDs()) TimeZone.setDefault(TimeZone.getTimeZone(id))
        }
    }
}
