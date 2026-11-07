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
data class Player(
    val id: String, val name: String, val tokens: List<String> = emptyList(), val since: Long = 0, val lastSeen: Long = 0,
    /** Le ultime 9 cifre del numero di telefono, per entrare con numero e PIN (vuoto = niente PIN). */
    val phone: String = "",
    /** Il PIN, mai in chiaro: "sale:impronta" (PBKDF2). */
    val pin: String? = null,
    /** PIN sbagliati di fila e fino a quando si aspetta prima di riprovare. */
    val pinFails: Int = 0,
    val pinLockUntil: Long = 0,
)

/** Un abbonamento alle notifiche push di un giocatore (un browser, un telefono): lo dà il browser. */
@Serializable
data class PushSub(val player: String, val endpoint: String, val p256dh: String, val auth: String, val lang: String = "it", val since: Long = 0)

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
    /** Le chiavi per le notifiche push (create al primo uso). */
    val vapid: WebPush.Vapid? = null,
    val pushSubs: List<PushSub> = emptyList(),
    /** Le prenotazioni di cui il promemoria è già partito. */
    val reminded: List<String> = emptyList(),
    /** Le liste d'attesa dei giocatori per le caselle occupate. */
    val waits: List<com.tennis.scoremanager.data.Wait> = emptyList(),
    /** Gli annunci di Cerco compagno. */
    val seeks: List<com.tennis.scoremanager.data.Seek> = emptyList(),
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

    /** Le ultime 9 cifre del numero: "+39 333 1234567", "0039…" e "3331234567" sono lo stesso numero. */
    fun phoneKey(raw: String): String = raw.filter(Char::isDigit).takeLast(9)

    /** Il PIN salvato: sale casuale e PBKDF2 (lento apposta, contro chi prova tutti i PIN avendo il file). */
    fun pinHash(pin: String, salt: String = token().take(16)): String {
        val spec = javax.crypto.spec.PBEKeySpec(pin.toCharArray(), salt.toByteArray(), 60_000, 256)
        val key = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return salt + ":" + key.joinToString("") { "%02x".format(it) }
    }

    fun pinOk(pin: String, saved: String): Boolean {
        val salt = saved.substringBefore(':')
        return MessageDigest.isEqual(pinHash(pin, salt).toByteArray(), saved.toByteArray())
    }
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
            // Scritto davvero sul disco prima di rispondere: al circolo può mancare la corrente.
            java.io.FileOutputStream(tmp).use { out -> out.write(json.encodeToString(State.serializer(), next).toByteArray()); out.fd.sync() }
            if (!tmp.renameTo(file)) { file.delete(); check(tmp.renameTo(file)) { "non riesco a scrivere ${file.path}" } }
            syncDir()
            state = next
        }
        return out
    }

    /** Il codice per abbinare la segreteria: nuovo se la segreteria non c'è, scritto in setup-code.txt. */
    fun ensureSetupCode(reset: Boolean = false): String? {
        if (state.admin != null && !reset) return null
        // Finché non si abbina resta lo stesso codice, anche se il server riparte.
        val old = File(dir, SETUP_FILE).takeIf { it.exists() }?.readText()?.trim()
        if (!reset && old != null && state.setup == Secrets.hash(old)) return old
        val code = Secrets.code()
        update { it.copy(admin = null, setup = Secrets.hash(code)) to Unit }
        File(dir, SETUP_FILE).apply {
            writeText("$code\n")
            setReadable(false, false); setReadable(true, true)
        }
        return code
    }

    fun forgetSetupCode() { File(dir, SETUP_FILE).delete(); syncDir() }

    /** Anche la cartella, perché la rinomina e le cancellazioni restino dopo uno spegnimento brusco. */
    private fun syncDir() {
        runCatching { java.nio.channels.FileChannel.open(dir.toPath(), java.nio.file.StandardOpenOption.READ).use { it.force(true) } }
    }

    companion object {
        const val SETUP_FILE = "setup-code.txt"

        fun applyTimeZone(id: String) {
            if (id in TimeZone.getAvailableIDs()) TimeZone.setDefault(TimeZone.getTimeZone(id))
        }
    }
}
