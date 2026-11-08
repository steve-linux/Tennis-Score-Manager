// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.server

import java.math.BigInteger
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPrivateKeySpec
import java.security.spec.ECPublicKeySpec
import java.time.Duration
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Le notifiche push della pagina web (anche su iPhone, dalla schermata Home), senza servizi né librerie: il
 * messaggio cifrato come vuole il browser (RFC 8291, aes128gcm) e firmato col nostro paio di chiavi (VAPID,
 * RFC 8292), poi mandato al servizio push del browser (Apple, Google, Mozilla).
 */
object WebPush {
    private val rnd = SecureRandom()
    private val b64 = Base64.getUrlEncoder().withoutPadding()
    private val unb64 = Base64.getUrlDecoder()

    fun enc(b: ByteArray): String = b64.encodeToString(b)
    fun dec(s: String): ByteArray = unb64.decode(s.trim().trimEnd('='))

    private val p256: ECParameterSpec = AlgorithmParameters.getInstance("EC").run {
        init(ECGenParameterSpec("secp256r1")); getParameterSpec(ECParameterSpec::class.java)
    }

    /** Il paio di chiavi del server: privata (32 byte) e pubblica (65 byte, punto non compresso), in base64url. */
    @kotlinx.serialization.Serializable
    data class Vapid(val privateKey: String, val publicKey: String)

    fun newVapid(): Vapid {
        val kp = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1"), rnd) }.generateKeyPair()
        val d = (kp.private as ECPrivateKey).s
        return Vapid(enc(fixed(d.toByteArray(), 32)), enc(raw(kp.public as ECPublicKey)))
    }

    private fun fixed(b: ByteArray, n: Int): ByteArray = when {
        b.size == n -> b
        b.size > n -> b.copyOfRange(b.size - n, b.size)
        else -> ByteArray(n - b.size) + b
    }

    private fun raw(k: ECPublicKey): ByteArray = byteArrayOf(4) + fixed(k.w.affineX.toByteArray(), 32) + fixed(k.w.affineY.toByteArray(), 32)

    private fun publicKey(raw: ByteArray): PublicKey {
        require(raw.size == 65 && raw[0] == 4.toByte()) { "chiave pubblica non valida" }
        val point = ECPoint(BigInteger(1, raw.copyOfRange(1, 33)), BigInteger(1, raw.copyOfRange(33, 65)))
        return KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(point, p256))
    }

    private fun privateKey(raw: ByteArray): PrivateKey = KeyFactory.getInstance("EC").generatePrivate(ECPrivateKeySpec(BigInteger(1, raw), p256))

    private fun hmac(key: ByteArray, data: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").run { init(SecretKeySpec(key, "HmacSHA256")); doFinal(data) }

    /**
     * Il messaggio cifrato per un abbonamento ([uaPublic], [authSecret] dati dal browser). [asPrivate]/[asPublic] e
     * [salt] sono a caso a ogni messaggio; si passano solo nei test (l'esempio dell'RFC 8291).
     */
    fun encrypt(
        plaintext: ByteArray, uaPublic: ByteArray, authSecret: ByteArray,
        asPrivate: ByteArray? = null, asPublic: ByteArray? = null, salt: ByteArray = ByteArray(16).also { rnd.nextBytes(it) },
    ): ByteArray {
        val (priv, pub) = if (asPrivate != null && asPublic != null) privateKey(asPrivate) to asPublic else {
            val kp = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1"), rnd) }.generateKeyPair()
            kp.private to raw(kp.public as ECPublicKey)
        }
        val ecdh = KeyAgreement.getInstance("ECDH").run { init(priv); doPhase(publicKey(uaPublic), true); generateSecret() }
        // HKDF in due passi, come nell'RFC 8291 §3.4.
        val prkKey = hmac(authSecret, ecdh)
        val ikm = hmac(prkKey, "WebPush: info".toByteArray() + byteArrayOf(0) + uaPublic + pub + byteArrayOf(1))
        val prk = hmac(salt, ikm)
        val cek = hmac(prk, "Content-Encoding: aes128gcm".toByteArray() + byteArrayOf(0, 1)).copyOf(16)
        val nonce = hmac(prk, "Content-Encoding: nonce".toByteArray() + byteArrayOf(0, 1)).copyOf(12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, SecretKeySpec(cek, "AES"), GCMParameterSpec(128, nonce)) }
        // Un solo blocco: il testo e il delimitatore 0x02 dell'ultimo blocco.
        val body = cipher.doFinal(plaintext + byteArrayOf(2))
        val rs = byteArrayOf(0, 0, 0x10, 0)
        return salt + rs + byteArrayOf(pub.size.toByte()) + pub + body
    }

    /** L'intestazione Authorization VAPID per il servizio push di [endpoint] (valida 12 ore). */
    fun vapidHeader(vapid: Vapid, endpoint: String, subject: String, now: Long): String {
        val u = URI(endpoint)
        val aud = "${u.scheme}://${u.host}" + if (u.port > 0) ":${u.port}" else ""
        val header = enc("""{"typ":"JWT","alg":"ES256"}""".toByteArray())
        val claims = enc("""{"aud":"$aud","exp":${now / 1000 + 12 * 3600},"sub":"$subject"}""".toByteArray())
        val signingInput = "$header.$claims"
        val der = Signature.getInstance("SHA256withECDSA").run { initSign(privateKey(dec(vapid.privateKey))); update(signingInput.toByteArray()); sign() }
        return "vapid t=$signingInput.${enc(derToRaw(der))}, k=${vapid.publicKey}"
    }

    /** La firma ECDSA da DER (quella di Java) a R||S di 64 byte (quella del JWT). */
    internal fun derToRaw(der: ByteArray): ByteArray {
        var i = 2
        if (der[1].toInt() and 0x80 != 0) i += der[1].toInt() and 0x7f
        fun int(): ByteArray { check(der[i] == 0x02.toByte()); val len = der[i + 1].toInt(); val v = der.copyOfRange(i + 2, i + 2 + len); i += 2 + len; return fixed(v, 32) }
        return int() + int()
    }

    private val http: HttpClient by lazy { HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build() }

    /**
     * Manda [payload] all'abbonamento; restituisce il codice HTTP del servizio push (201 = preso; 404 e 410 =
     * abbonamento scaduto, da togliere), o 0 se non si arriva.
     */
    fun send(vapid: Vapid, subject: String, sub: PushSub, payload: String, ttlSeconds: Int, now: Long = System.currentTimeMillis()): Int = try {
        val body = encrypt(payload.toByteArray(), dec(sub.p256dh), dec(sub.auth))
        val req = HttpRequest.newBuilder(URI(sub.endpoint)).timeout(Duration.ofSeconds(20))
            .header("TTL", ttlSeconds.toString()).header("Urgency", "high")
            .header("Content-Encoding", "aes128gcm").header("Content-Type", "application/octet-stream")
            .header("Authorization", vapidHeader(vapid, sub.endpoint, subject, now))
            .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build()
        http.send(req, HttpResponse.BodyHandlers.discarding()).statusCode()
    } catch (e: Exception) {
        System.err.println("Push non mandata a ${URI(sub.endpoint).host}: $e")
        0
    }
}
