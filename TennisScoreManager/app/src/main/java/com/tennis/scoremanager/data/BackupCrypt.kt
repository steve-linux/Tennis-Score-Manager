// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import java.io.FilterOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.PushbackInputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Backup protetto da password: AES-256 (CTR) e un sigillo HMAC-SHA256 su tutto il file, con le chiavi ricavate dalla
 * password (PBKDF2, sale casuale per ogni file). Si cifra e decifra a flusso, così anche i backup coi video non
 * riempiono la memoria. Nel file: [MAGIC], sale, giri, IV, verifica della password, dati cifrati, sigillo (32 byte).
 */
object BackupCrypt {
    private val MAGIC = "TSMENC1\n".toByteArray()
    private const val ITERATIONS = 200_000
    private const val SALT = 16
    private const val IV = 16
    private const val CHECK = 16
    private const val TAG = 32

    /** La password è sbagliata (o il file non è un backup protetto). */
    class WrongPassword : IOException("wrong password")

    /** Il file comincia come un backup protetto. Legge solo l'inizio di [peek] (che va riaperto dopo). */
    fun isEncrypted(peek: InputStream): Boolean {
        val head = ByteArray(MAGIC.size)
        var n = 0
        while (n < head.size) { val r = peek.read(head, n, head.size - n); if (r < 0) break; n += r }
        return n == MAGIC.size && head.contentEquals(MAGIC)
    }

    private fun keys(password: CharArray, salt: ByteArray, iterations: Int): Pair<ByteArray, ByteArray> {
        val bits = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(password, salt, iterations, 512)).encoded
        return bits.copyOfRange(0, 32) to bits.copyOfRange(32, 64)
    }

    /** "La password è giusta" senza decifrare niente: un riassunto della chiave del sigillo. */
    private fun check(macKey: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(macKey + "check".toByteArray()).copyOf(CHECK)

    /** Scrive in [out] il backup cifrato con [password]: quello che si scrive nel flusso restituito. */
    fun encrypting(out: OutputStream, password: CharArray, iterations: Int = ITERATIONS): OutputStream {
        val rnd = SecureRandom()
        val salt = ByteArray(SALT).also(rnd::nextBytes)
        val iv = ByteArray(IV).also(rnd::nextBytes)
        val (encKey, macKey) = keys(password, salt, iterations)
        val cipher = Cipher.getInstance("AES/CTR/NoPadding").apply { init(Cipher.ENCRYPT_MODE, SecretKeySpec(encKey, "AES"), IvParameterSpec(iv)) }
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(macKey, "HmacSHA256")) }
        val header = MAGIC + salt + java.nio.ByteBuffer.allocate(4).putInt(iterations).array() + iv + check(macKey)
        out.write(header)
        mac.update(header)
        return object : FilterOutputStream(out) {
            private var closed = false
            override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)
            override fun write(b: ByteArray, off: Int, len: Int) {
                val enc = cipher.update(b, off, len) ?: return
                mac.update(enc)
                out.write(enc)
            }
            override fun close() {
                if (closed) return
                closed = true
                cipher.doFinal()?.takeIf { it.isNotEmpty() }?.let { mac.update(it); out.write(it) }
                out.write(mac.doFinal())
                out.close()
            }
        }
    }

    /**
     * Il backup in chiaro da [input] (che comincia col [MAGIC]); [WrongPassword] subito se la password non va. Il
     * sigillo si controlla alla fine: se il file è stato toccato, l'ultima lettura dà errore.
     */
    fun decrypting(input: InputStream, password: CharArray): InputStream {
        fun readFully(n: Int): ByteArray {
            val b = ByteArray(n); var got = 0
            while (got < n) { val r = input.read(b, got, n - got); if (r < 0) throw WrongPassword(); got += r }
            return b
        }
        val magic = readFully(MAGIC.size)
        if (!magic.contentEquals(MAGIC)) throw WrongPassword()
        val salt = readFully(SALT)
        val itBytes = readFully(4)
        val iterations = java.nio.ByteBuffer.wrap(itBytes).int
        if (iterations !in 1_000..10_000_000) throw WrongPassword()
        val iv = readFully(IV)
        val chk = readFully(CHECK)
        val (encKey, macKey) = keys(password, salt, iterations)
        if (!MessageDigest.isEqual(chk, check(macKey))) throw WrongPassword()
        val cipher = Cipher.getInstance("AES/CTR/NoPadding").apply { init(Cipher.DECRYPT_MODE, SecretKeySpec(encKey, "AES"), IvParameterSpec(iv)) }
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(macKey, "HmacSHA256")); update(magic + salt + itBytes + iv + chk) }
        val src = PushbackInputStream(input, TAG + 8192)
        return object : InputStream() {
            private var pending = ByteArray(0)
            private var pos = 0
            private var done = false
            /** Legge un pezzo cifrato tenendo sempre da parte gli ultimi [TAG] byte (il sigillo). */
            private fun fill(): Boolean {
                if (done) return false
                val buf = ByteArray(8192 + TAG)
                var n = 0
                while (n < buf.size) { val r = src.read(buf, n, buf.size - n); if (r < 0) break; n += r }
                if (n < buf.size) {
                    // Fine del file: gli ultimi 32 byte sono il sigillo.
                    if (n < TAG) throw IOException("truncated")
                    val body = buf.copyOf(n - TAG)
                    val tag = buf.copyOfRange(n - TAG, n)
                    mac.update(body)
                    if (!MessageDigest.isEqual(mac.doFinal(), tag)) throw IOException("backup damaged")
                    pending = (cipher.update(body) ?: ByteArray(0)) + (cipher.doFinal() ?: ByteArray(0))
                    pos = 0; done = true
                    return pending.isNotEmpty()
                }
                // Pieno: gli ultimi TAG byte tornano indietro, potrebbero essere il sigillo.
                src.unread(buf, buf.size - TAG, TAG)
                val body = buf.copyOf(buf.size - TAG)
                mac.update(body)
                pending = cipher.update(body) ?: ByteArray(0); pos = 0
                return true
            }
            override fun read(): Int {
                while (pos >= pending.size) if (!fill()) return -1
                return pending[pos++].toInt() and 0xff
            }
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (len == 0) return 0
                while (pos >= pending.size) if (!fill()) return -1
                val n = minOf(len, pending.size - pos)
                System.arraycopy(pending, pos, b, off, n); pos += n
                return n
            }
            override fun close() = input.close()
        }
    }
}
