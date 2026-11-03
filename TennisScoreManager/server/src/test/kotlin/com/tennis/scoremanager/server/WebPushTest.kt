// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec

class WebPushTest {
    /** L'esempio dell'RFC 8291, appendice A: stesso risultato byte per byte. */
    @Test fun rfc8291Example() {
        val out = WebPush.encrypt(
            plaintext = "When I grow up, I want to be a watermelon".toByteArray(),
            uaPublic = WebPush.dec("BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4"),
            authSecret = WebPush.dec("BTBZMqHH6r4Tts7J_aSIgg"),
            asPrivate = WebPush.dec("yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw"),
            asPublic = WebPush.dec("BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8"),
            salt = WebPush.dec("DGv6ra1nlYgDCS1FRnbzlw"),
        )
        assertEquals(
            "DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A_yl95bQpu6cVPTpK4Mqgkf1CXztLVBSt2Ks3oZwbuwXPXLWyouBWLVWGNWQexSgSxsj_Qulcy4a-fN",
            WebPush.enc(out),
        )
    }

    /** La firma VAPID si verifica con la chiave pubblica che il browser riceve. */
    @Test fun vapidSignatureVerifies() {
        val v = WebPush.newVapid()
        val h = WebPush.vapidHeader(v, "https://web.push.apple.com/QGuQyavXutnMH", "https://example.org", 1_800_000_000_000)
        val jwt = h.removePrefix("vapid t=").substringBefore(",")
        val (head, claims, sig) = jwt.split('.')
        assertTrue(String(WebPush.dec(claims)).contains("\"aud\":\"https://web.push.apple.com\""))
        val raw = WebPush.dec(sig)
        assertEquals(64, raw.size)
        // Di nuovo in DER per la verifica di Java.
        fun derInt(b: ByteArray): ByteArray { val t = b.dropWhile { it == 0.toByte() }.toByteArray(); val v2 = if (t.isNotEmpty() && t[0] < 0) byteArrayOf(0) + t else t; return byteArrayOf(2, v2.size.toByte()) + v2 }
        val seq = derInt(raw.copyOf(32)) + derInt(raw.copyOfRange(32, 64))
        val der = byteArrayOf(0x30, seq.size.toByte()) + seq
        val p256 = AlgorithmParameters.getInstance("EC").run { init(ECGenParameterSpec("secp256r1")); getParameterSpec(ECParameterSpec::class.java) }
        val pubRaw = WebPush.dec(v.publicKey)
        val pub = KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(ECPoint(BigInteger(1, pubRaw.copyOfRange(1, 33)), BigInteger(1, pubRaw.copyOfRange(33, 65))), p256))
        val ok = Signature.getInstance("SHA256withECDSA").run { initVerify(pub); update("$head.$claims".toByteArray()); verify(der) }
        assertTrue(ok)
        assertEquals(65, pubRaw.size)
    }
}
