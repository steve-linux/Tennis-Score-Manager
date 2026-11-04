// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.desk

import java.io.ByteArrayOutputStream
import java.io.File
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext

/**
 * L'identità HTTPS del telefono per la Segreteria da PC: una chiave EC P-256 e un certificato autofirmato, creati la
 * prima volta e tenuti fuori dai backup. Su una rete locale nessuna autorità firma un indirizzo come 192.168.1.20: il
 * browser avvisa una volta, poi ricorda il certificato; se cambiasse, avviserebbe di nuovo. L'impronta si può
 * confrontare con quella mostrata dal telefono.
 */
class DeskCert private constructor(val key: PrivateKey, val cert: X509Certificate) {

    /** L'impronta SHA-256 del certificato, come la mostrano i browser: "AB:CD:…". */
    val fingerprint: String get() = MessageDigest.getInstance("SHA-256").digest(cert.encoded).joinToString(":") { "%02X".format(it) }

    fun sslContext(): SSLContext {
        val ks = KeyStore.getInstance("PKCS12").apply { load(null, null) }
        ks.setKeyEntry("tsm", key, PASS, arrayOf(cert))
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(ks, PASS) }
        return SSLContext.getInstance("TLS").apply { init(kmf.keyManagers, null, null) }
    }

    companion object {
        private val PASS = "tsm".toCharArray()
        private const val NAME = "Tennis Score Manager"
        private const val YEARS = 10

        /** Il certificato di [dir]; se manca o è rovinato se ne crea uno nuovo. */
        fun loadOrCreate(dir: File, now: Long = System.currentTimeMillis()): DeskCert {
            val keyFile = File(dir, "desk_tls.key")
            val certFile = File(dir, "desk_tls.crt")
            runCatching {
                val key = KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(keyFile.readBytes()))
                val cert = certFile.inputStream().use { CertificateFactory.getInstance("X.509").generateCertificate(it) } as X509Certificate
                cert.checkValidity(Date(now))
                return DeskCert(key, cert)
            }
            val made = create(now)
            dir.mkdirs()
            keyFile.delete()
            keyFile.createNewFile()
            keyFile.setReadable(false, false); keyFile.setReadable(true, true)
            keyFile.writeBytes(made.key.encoded)
            certFile.writeBytes(made.cert.encoded)
            return made
        }

        fun create(now: Long = System.currentTimeMillis()): DeskCert {
            val pair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1"), SecureRandom()) }.generateKeyPair()
            val alg = seq(oid(1, 2, 840, 10045, 4, 3, 2))  // ecdsa-with-SHA256
            val name = seq(set(seq(oid(2, 5, 4, 3), tlv(0x0C, NAME.toByteArray()))))  // CN
            val serial = BigInteger(159, SecureRandom()).add(BigInteger.ONE)
            val from = now - 86_400_000L
            val to = now + YEARS * 365L * 86_400_000L
            val ext = seq(
                seq(oid(2, 5, 29, 19), tlv(0x01, byteArrayOf(-1)), tlv(0x04, seq())),  // basicConstraints critico: non è una CA
                seq(oid(2, 5, 29, 15), tlv(0x01, byteArrayOf(-1)), tlv(0x04, tlv(0x03, byteArrayOf(7, 0x80.toByte())))),  // keyUsage: digitalSignature
                seq(oid(2, 5, 29, 37), tlv(0x04, seq(oid(1, 3, 6, 1, 5, 5, 7, 3, 1)))),  // extKeyUsage: serverAuth
            )
            val tbs = seq(
                tlv(0xA0, tlv(0x02, byteArrayOf(2))),  // versione 3
                tlv(0x02, serial.toByteArray()),
                alg, name, seq(time(from), time(to)), name,
                pair.public.encoded,
                tlv(0xA3, ext),
            )
            val sig = Signature.getInstance("SHA256withECDSA").apply { initSign(pair.private); update(tbs) }.sign()
            val der = seq(tbs, alg, tlv(0x03, byteArrayOf(0) + sig))
            val cert = CertificateFactory.getInstance("X.509").generateCertificate(der.inputStream()) as X509Certificate
            return DeskCert(pair.private, cert)
        }

        // --- DER: quel poco che serve per scrivere un certificato ---

        private fun tlv(tag: Int, body: ByteArray): ByteArray {
            val out = ByteArrayOutputStream()
            out.write(tag)
            when {
                body.size < 128 -> out.write(body.size)
                body.size < 256 -> { out.write(0x81); out.write(body.size) }
                else -> { out.write(0x82); out.write(body.size shr 8); out.write(body.size and 0xFF) }
            }
            out.write(body)
            return out.toByteArray()
        }

        private fun seq(vararg parts: ByteArray) = tlv(0x30, parts.fold(ByteArray(0)) { a, b -> a + b })
        private fun set(vararg parts: ByteArray) = tlv(0x31, parts.fold(ByteArray(0)) { a, b -> a + b })

        private fun oid(vararg arcs: Long): ByteArray {
            val out = ByteArrayOutputStream()
            out.write((arcs[0] * 40 + arcs[1]).toInt())
            for (a in arcs.drop(2)) {
                var shift = 63 - java.lang.Long.numberOfLeadingZeros(a or 1)
                shift -= shift % 7
                while (shift > 0) { out.write((((a shr shift) and 0x7F) or 0x80).toInt()); shift -= 7 }
                out.write((a and 0x7F).toInt())
            }
            return tlv(0x06, out.toByteArray())
        }
        private fun oid(vararg arcs: Int) = oid(*arcs.map { it.toLong() }.toLongArray())

        private fun time(t: Long): ByteArray =
            tlv(0x17, SimpleDateFormat("yyMMddHHmmss'Z'", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(t)).toByteArray())
    }
}
