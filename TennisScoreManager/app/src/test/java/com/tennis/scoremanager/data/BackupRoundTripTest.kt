// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later
package com.tennis.scoremanager.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Backup e ripristino con i file veri dell'app (Robolectric): si salva, si cancella tutto, si ripristina e i dati
 * tornano uguali. Un backup sbagliato (password errata, file troncato) non deve cancellare i dati di adesso.
 */
@RunWith(RobolectricTestRunner::class)
// Senza Conscrypt: Robolectric lo metterebbe come provider TLS di tutta la JVM e cambierebbe l'HTTPS dei test che girano dopo (DeskTest).
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [35], application = Application::class)
class BackupRoundTripTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private lateinit var storage: Storage
    private lateinit var match: MatchRecord

    @Before
    fun data() {
        storage = Storage(app)
        val text = requireNotNull(javaClass.getResource("/formati/2.6.0/match_suspended.json")).readText()
        match = Storage.JSON.decodeFromString(MatchRecord.serializer(), text)
        storage.saveMatch(match)
        storage.setup = SetupData(club = "Circolo Prova")
        File(app.filesDir, "players.json").writeText("""[{"id":"p1","name":"Mario Rossi"}]""")
    }

    private fun backup(password: String? = null): ByteArray =
        ByteArrayOutputStream().also { Backup.write(app, storage, it, videos = false, version = "test", strokesFolder = "Colpi", password = password?.toCharArray()) }.toByteArray()

    private fun restore(bytes: ByteArray, password: String? = null): Boolean =
        Backup.openPlain({ ByteArrayInputStream(bytes) }, password?.toCharArray())!!.use { Backup.restore(app, storage, it) }

    private fun prefsText() = File(app.applicationInfo.dataDir, "shared_prefs/tsm.xml").readText()

    private fun assertDataBack() {
        assertEquals(match, storage.loadUnfinished().single())
        assertTrue(File(app.filesDir, "players.json").readText().contains("Mario Rossi"))
        assertTrue(prefsText().contains("Circolo Prova"))
    }

    @Test
    fun plain() {
        val b = backup()
        Backup.wipe(app, storage, videos = false, strokesFolders = emptyList())
        assertTrue(storage.loadUnfinished().isEmpty())
        assertFalse(File(app.filesDir, "players.json").exists())
        assertTrue(restore(b))
        assertDataBack()
    }

    @Test
    fun withPassword() {
        val b = backup("segreta")
        assertTrue(Backup.isLocked { ByteArrayInputStream(b) })
        try {
            restore(b, "sbagliata")
            fail("password sbagliata accettata")
        } catch (_: BackupCrypt.WrongPassword) {
        } catch (_: java.io.IOException) {
        }
        assertDataBack() // niente cancellato
        Backup.wipe(app, storage, videos = false, strokesFolders = emptyList())
        assertTrue(restore(b, "segreta"))
        assertDataBack()
    }

    @Test
    fun brokenFileTouchesNothing() {
        val b = backup("segreta")
        val cut = b.copyOf(b.size - 40) // ultima parte persa (sigillo compreso)
        runCatching { restore(cut, "segreta") }.onSuccess { assertFalse("file troncato ripristinato", it) }
        assertDataBack()
        assertFalse(runCatching { restore("non è un backup".toByteArray()) }.getOrDefault(false))
        assertDataBack()
    }
}
