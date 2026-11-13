// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class SafeFilesTest {

    private fun dir(): File = Files.createTempDirectory("safefiles").toFile().apply { deleteOnExit() }

    @Test fun scriveERilegge() {
        val f = File(dir(), "players.json")
        SafeFiles.write(f, "[1,2]")
        assertEquals("[1,2]", f.readText())
        SafeFiles.write(f, "[3]")
        assertEquals("[3]", f.readText())
        assertFalse("niente file d'appoggio rimasti", File(f.parentFile, "players.json.tmp").exists())
    }

    @Test fun senzaFileIlValorePredefinito() {
        val f = File(dir(), "goals.json")
        var reported = false
        assertEquals(listOf<Int>(), SafeFiles.read<List<Int>>(f, listOf(), { error("non si legge") }) { reported = true })
        assertFalse("un file che non c'è non è un problema", reported)
    }

    @Test fun unFileIllegibileSiMetteDaParteUnaVolta() {
        val f = File(dir(), "club_book.json").apply { writeText("{ rovinato") }
        val names = mutableListOf<String>()
        fun load() = SafeFiles.read(f, "vuoto", { if (it.startsWith("{ \"ok\"")) it else throw IllegalArgumentException(it) }) { names += it }
        assertEquals("vuoto", load())
        assertEquals("vuoto", load())
        assertEquals(listOf("club_book.json", "club_book.json"), names)
        // Una copia sola, con il contenuto di prima: il salvataggio dopo non la tocca.
        val aside = SafeFiles.asideCopies(f)
        assertEquals(1, aside.size)
        assertEquals("{ rovinato", aside.single().readText())
        SafeFiles.write(f, "{ \"ok\": 1 }")
        assertEquals("{ \"ok\": 1 }", load())
        assertTrue(aside.single().exists())
        assertEquals("{ rovinato", aside.single().readText())
    }
}
