// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException

class BackupCryptTest {
    private val plain = ByteArray(300_123) { (it * 31 + 7).toByte() }
    private fun sealed(pw: String = "segreta1", data: ByteArray = plain): ByteArray {
        val out = ByteArrayOutputStream()
        BackupCrypt.encrypting(out, pw.toCharArray(), iterations = 1_000).use { it.write(data) }
        return out.toByteArray()
    }

    @Test fun roundTripAndRecognised() {
        val enc = sealed()
        assertTrue(BackupCrypt.isEncrypted(ByteArrayInputStream(enc)))
        assertFalse(BackupCrypt.isEncrypted(ByteArrayInputStream("PK\u0003\u0004zip".toByteArray())))
        assertFalse(enc.copyOfRange(100, 200).contentEquals(plain.copyOfRange(0, 100)))
        assertArrayEquals(plain, BackupCrypt.decrypting(ByteArrayInputStream(enc), "segreta1".toCharArray()).readBytes())
        // Anche vuoto e a pezzi piccoli.
        assertArrayEquals(ByteArray(0), BackupCrypt.decrypting(ByteArrayInputStream(sealed(data = ByteArray(0))), "segreta1".toCharArray()).readBytes())
        val one = BackupCrypt.decrypting(ByteArrayInputStream(enc), "segreta1".toCharArray())
        val got = ByteArrayOutputStream(); while (true) { val b = one.read(); if (b < 0) break; got.write(b) }
        assertArrayEquals(plain, got.toByteArray())
    }

    @Test fun lockedBackupOpensOnlyWithThePassword() {
        // Un backup minimo (il manifest), cifrato come lo fa Backup.write.
        val zip = ByteArrayOutputStream().also { o ->
            java.util.zip.ZipOutputStream(o).use { z -> z.putNextEntry(java.util.zip.ZipEntry("manifest.json")); z.write("""{"version":1,"app":"2.6","createdAt":5}""".toByteArray()); z.closeEntry() }
        }.toByteArray()
        val locked = sealed(data = zip)
        val open = { ByteArrayInputStream(locked) }
        assertTrue(Backup.isLocked(open))
        assertFalse(Backup.isLocked { ByteArrayInputStream(zip) })
        try { Backup.openPlain(open, null); fail() } catch (_: BackupCrypt.WrongPassword) {}
        try { Backup.openPlain(open, "no".toCharArray()); fail() } catch (_: BackupCrypt.WrongPassword) {}
        assertTrue(Backup.openPlain(open, "segreta1".toCharArray())!!.use { Backup.peek(it) }!!.app == "2.6")
        // In chiaro si apre come prima, password o no.
        assertTrue(Backup.openPlain({ ByteArrayInputStream(zip) }, null)!!.use { Backup.peek(it) }!!.createdAt == 5L)
    }

    @Test fun wrongPasswordIsCaughtAtOnce() {
        try { BackupCrypt.decrypting(ByteArrayInputStream(sealed()), "sbagliata".toCharArray()); fail() } catch (_: BackupCrypt.WrongPassword) {}
    }

    @Test fun tamperedFileIsRefused() {
        val enc = sealed()
        enc[enc.size / 2] = (enc[enc.size / 2].toInt() xor 1).toByte()
        try { BackupCrypt.decrypting(ByteArrayInputStream(enc), "segreta1".toCharArray()).readBytes(); fail() } catch (e: IOException) { assertFalse(e is BackupCrypt.WrongPassword) }
        // Troncato.
        try { BackupCrypt.decrypting(ByteArrayInputStream(sealed().copyOf(1000)), "segreta1".toCharArray()).readBytes(); fail() } catch (_: IOException) {}
    }
}
