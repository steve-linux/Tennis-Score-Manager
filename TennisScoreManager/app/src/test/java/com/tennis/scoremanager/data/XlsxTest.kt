// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

class XlsxTest {
    @Test fun columnLetters() {
        assertEquals("A", Xlsx.col(0)); assertEquals("Z", Xlsx.col(25)); assertEquals("AA", Xlsx.col(26)); assertEquals("AZ", Xlsx.col(51)); assertEquals("BA", Xlsx.col(52))
    }

    @Test fun workbookHasTheSheetWithEscapedTextAndNumbers() {
        val out = ByteArrayOutputStream()
        val rows = listOf(listOf<Any?>("Allievo", "Importo"), listOf<Any?>("Neri & <figli>", 250.0), listOf<Any?>("Blu", 3))
        Xlsx.write("Ricevute 2026", rows, out)
        // Una copia da aprire a mano (build/xlsx-test.xlsx).
        java.io.File("build/xlsx-test.xlsx").writeBytes(out.toByteArray())
        val entries = mutableMapOf<String, String>()
        ZipInputStream(out.toByteArray().inputStream()).use { z -> while (true) { val e = z.nextEntry ?: break; entries[e.name] = z.readBytes().decodeToString() } }
        assertTrue(entries.keys.containsAll(listOf("[Content_Types].xml", "_rels/.rels", "xl/workbook.xml", "xl/worksheets/sheet1.xml", "xl/styles.xml")))
        val sheet = entries["xl/worksheets/sheet1.xml"]!!
        assertTrue("Neri &amp; &lt;figli&gt;" in sheet)
        assertTrue("<c r=\"B2\"><v>250.0</v></c>" in sheet)
        assertTrue("name=\"Ricevute 2026\"" in entries["xl/workbook.xml"]!!)
    }

    @Test fun registerRowsHaveMarksAndTotals() {
        val c = Course("c", "U10", students = listOf("b", "a"), from = 0, to = 0)
        val rows = Xlsx.registerRows(c, { it.uppercase() }, "Allievo", "Presenze", "Lezioni", 0)
        assertEquals(listOf<Any?>("Allievo", "Presenze", "Lezioni"), rows[0])
        assertEquals("A", rows[1][0])
    }
}
