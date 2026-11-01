// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.tennis.scoremanager.model.Lang
import com.tennis.scoremanager.ui.Strings
import com.tennis.scoremanager.ui.payMethodName
import java.io.OutputStream

/**
 * La ricevuta di un pagamento, su un foglio A5: circolo, numero e data, da chi, per cosa, importo e metodo, lo spazio
 * per la firma. È un documento non fiscale e lo dice: le ricevute fiscali le fa chi tiene la contabilità.
 */
object ReceiptPdf {
    private const val W = 420
    private const val H = 595
    private const val M = 36f

    fun write(c: Charge, p: Payment, club: String, s: Strings, lang: Lang, out: OutputStream) {
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, 1).create())
        val cv = page.canvas
        fun paint(size: Float, color: Int = 0xFF111827.toInt(), bold: Boolean = false, align: Paint.Align = Paint.Align.LEFT) = Paint().apply {
            isAntiAlias = true; textSize = size; this.color = color; textAlign = align
            typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
        }
        val grey = 0xFF6B7280.toInt()
        val rule = Paint().apply { color = 0xFFD1D5DB.toInt(); strokeWidth = 1f }
        var y = M + 20f
        cv.drawText(club.ifBlank { s.receiptTitle }, M, y, paint(18f, bold = true))
        y += 26f
        cv.drawText(s.payReceiptNo(p.receipt), M, y, paint(12f, bold = true))
        cv.drawText(Reports.date(p.at, lang), W - M, y, paint(10f, grey, align = Paint.Align.RIGHT))
        y += 12f
        cv.drawLine(M, y, W - M, y, rule)
        y += 28f
        // Le righe: etichetta grigia sopra, valore sotto.
        fun field(label: String, value: String) {
            if (value.isBlank()) return
            cv.drawText(label, M, y, paint(9f, grey))
            y += 15f
            cv.drawText(value, M, y, paint(12f))
            y += 24f
        }
        field(s.receiptReceivedFrom, c.name)
        field(s.receiptFor, c.label)
        field(s.receiptMethod, listOf(payMethodName(s, p.method), p.ref).filter { it.isNotBlank() }.joinToString(" · "))
        cv.drawText(s.receiptAmount, M, y, paint(9f, grey))
        y += 26f
        cv.drawText(Fees.money(p.cents, lang.locale), M, y, paint(24f, bold = true))
        y += 40f
        // Firma di chi incassa.
        val sx = W - M - 150f
        cv.drawLine(sx, y + 30f, W - M, y + 30f, rule)
        cv.drawText(s.receiptSignature, sx, y + 44f, paint(9f, grey))
        cv.drawText(s.receiptNotFiscal, M, H - M - 14f, paint(9f, grey, bold = true))
        cv.drawText(s.generatedWith, M, H - M, paint(8f, grey))
        doc.finishPage(page)
        doc.writeTo(out)
        doc.close()
    }
}
