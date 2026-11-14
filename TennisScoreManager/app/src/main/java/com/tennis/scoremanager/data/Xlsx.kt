// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Un foglio Excel (.xlsx) minimo, senza librerie: un solo foglio, testi e numeri, la prima riga in grassetto. Si apre
 * con Excel, Fogli Google, LibreOffice e Numbers.
 */
object Xlsx {
    const val MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
        .filter { it == '\t' || it == '\n' || it >= ' ' }

    /** "A", "B", … "Z", "AA"… per la colonna [i] (da 0). */
    fun col(i: Int): String {
        var n = i + 1
        val sb = StringBuilder()
        while (n > 0) { val r = (n - 1) % 26; sb.insert(0, 'A' + r); n = (n - 1) / 26 }
        return sb.toString()
    }

    /** Il foglio in XML: ogni cella un testo o un numero (Int, Long, Double). */
    fun sheetXml(rows: List<List<Any?>>): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
        rows.forEachIndexed { r, row ->
            append("<row r=\"${r + 1}\">")
            row.forEachIndexed { c, v ->
                val ref = col(c) + (r + 1)
                val style = if (r == 0) " s=\"1\"" else ""
                when (v) {
                    null -> {}
                    is Number -> append("<c r=\"$ref\"$style><v>$v</v></c>")
                    else -> append("<c r=\"$ref\" t=\"inlineStr\"$style><is><t xml:space=\"preserve\">${esc(v.toString())}</t></is></c>")
                }
            }
            append("</row>")
        }
        append("</sheetData></worksheet>")
    }

    fun write(sheetName: String, rows: List<List<Any?>>, out: OutputStream) {
        val name = esc(sheetName.replace(Regex("[\\\\/?*\\[\\]:]"), " ").take(31).ifBlank { "Sheet1" })
        ZipOutputStream(out).use { z ->
            fun put(path: String, text: String) { z.putNextEntry(ZipEntry(path)); z.write(text.toByteArray(Charsets.UTF_8)); z.closeEntry() }
            put("[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/></Types>""")
            put("_rels/.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
            put("xl/workbook.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="$name" sheetId="1" r:id="rId1"/></sheets></workbook>""")
            put("xl/_rels/workbook.xml.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>""")
            put("xl/styles.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts><fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills><borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="2"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/></cellXfs></styleSheet>""")
            put("xl/worksheets/sheet1.xml", sheetXml(rows))
        }
    }

    /** Il registro presenze del corso come righe: allievo, una colonna per lezione passata (✓ ✗ —), presenze, lezioni. */
    fun registerRows(c: Course, names: (String) -> String, studentLabel: String, presentLabel: String, lessonsLabel: String, now: Long): List<List<Any?>> {
        val lessons = Courses.lessons(c).filter { it.start <= now }
        val df = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.ROOT)
        val header = listOf<Any?>(studentLabel) + lessons.map { df.format(java.util.Date(it.start)) } + listOf(presentLabel, lessonsLabel)
        return listOf(header) + c.students.sortedBy { names(it).lowercase() }.map { sid ->
            val (pres, tot) = Courses.attendanceOf(c, sid, now)
            listOf<Any?>(names(sid)) + lessons.map { AttendancePdf.mark(c, it.key, sid) } + listOf(pres, tot)
        }
    }
}
