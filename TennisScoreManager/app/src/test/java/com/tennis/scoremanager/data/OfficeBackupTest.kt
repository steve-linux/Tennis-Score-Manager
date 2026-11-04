// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class OfficeBackupTest {
    private val snap = OfficeSnapshot(createdAt = 1000, club = "TC Prova", courts = listOf("Campo 1"), players = listOf(PlayerProfile("p1", "Mario Rossi", member = Membership())))

    @Test fun compressedAndBack() {
        val bytes = OfficeBackup.encode(snap)
        assertEquals(0x1f.toByte(), bytes[0])
        assertEquals(snap, OfficeBackup.decode(bytes))
        assertEquals(1, OfficeBackup.summary(snap).members)
        // Anche il JSON com'è; niente se non è un backup.
        assertEquals("TC Prova", OfficeBackup.decode("""{"createdAt":5,"club":"TC Prova"}""".toByteArray())?.club)
        assertNull(OfficeBackup.decode("ciao".toByteArray()))
        assertNull(OfficeBackup.decode("""{"version":99,"createdAt":5}""".toByteArray()))
    }

    @Test fun sameContentSameFingerprint() {
        // L'ora del backup non conta: se non è cambiato niente, la copia automatica si salta.
        assertEquals(OfficeBackup.fingerprint(snap), OfficeBackup.fingerprint(snap.copy(createdAt = 9999, app = "2.6")))
        assertNotEquals(OfficeBackup.fingerprint(snap), OfficeBackup.fingerprint(snap.copy(club = "TC Altro")))
    }

    @Test fun keepsTheLastCopies() {
        val dir = Files.createTempDirectory("tsm-office").toFile()
        for (i in 1..16) OfficeBackup.store(dir, "TSM segreteria %02d.tsmclub".format(i), byteArrayOf(1), keep = 14).setLastModified(i * 1000L)
        val list = OfficeBackup.list(dir)
        assertEquals(14, list.size)
        assertEquals("TSM segreteria 16.tsmclub", list.first().name)
        assertTrue(OfficeBackup.fileName("CT Sovizzo / A", 0).let { it.startsWith("TSM segreteria CT Sovizzo") && "/" !in it && it.endsWith(".tsmclub") })
        assertNull(OfficeBackup.safeName("../x.tsmclub"))
        dir.deleteRecursively()
    }
}
