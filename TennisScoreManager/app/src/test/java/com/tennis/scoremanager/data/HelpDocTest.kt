// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HelpDocTest {
    private fun guide(lang: String) = HelpDoc.parse(File("src/main/assets/help/$lang.md").readText())

    @Test
    fun everyLanguageHasTheSameSections() {
        val it = guide("it").map { it.id }
        assertTrue("$it", it.containsAll(listOf("3.1", "4", "6", "6.4", "7", "7.5", "8", "11")))
        // Niente installazione, firmware né codice: sono per chi sviluppa.
        assertFalse(it.any { id -> id.split('.')[0] in setOf("0", "1", "2", "5", "10") })
        for (lang in listOf("en", "fr", "de", "es", "pt")) assertEquals(lang, it, guide(lang).map { it.id })
        // Ogni sezione ha qualcosa da leggere.
        for (s in guide("it")) assertTrue(s.id, s.blocks.isNotEmpty() || s.level == 2)
    }

    @Test
    fun inlineStylesAndCrossReferences() {
        val spans = HelpDoc.inline("Premi **KEY1** e poi *tenuto* il `tasto` (6.4) o (es. 4.1, NC)", setOf("6.4", "4.1"))
        assertEquals(HelpSpan("KEY1", bold = true), spans[1])
        assertEquals(HelpSpan("tenuto", italic = true), spans[3])
        assertEquals(HelpSpan("tasto", code = true), spans[5])
        assertEquals("6.4", spans.single { it.ref != null }.ref)
        assertEquals("Premi KEY1 e poi tenuto il tasto (6.4) o (es. 4.1, NC)", spans.joinToString("") { it.text })
    }

    @Test
    fun listsTablesAndSearch() {
        val doc = HelpDoc.parse(
            """
            ## 6. Braccialetti
            Testo.
            | Tasto | Cosa fa |
            |---|---|
            | **KEY1** | punto |
            1. Primo
               - dentro
            2. Secondo
            ### 6.1 Impostazioni della Volée
            - voce
            """.trimIndent(),
        )
        val b = doc[0].blocks
        assertTrue(b[1] is HelpBlock.Table)
        assertEquals(2, (b[1] as HelpBlock.Table).rows.size)
        assertEquals(HelpBlock.Bullet(listOf(HelpSpan("dentro")), 1), b[3])
        assertEquals(2, (b[4] as HelpBlock.Bullet).number)
        // Senza accenti e in minuscolo; i titoli prima.
        assertEquals(listOf("6.1"), HelpDoc.search(doc, "volee").map { it.id })
        assertEquals(listOf("6"), HelpDoc.search(doc, "key1 punto").map { it.id })
        assertTrue(HelpDoc.search(guide("it"), "telecomando").isNotEmpty())
    }
}
