// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.ui

import android.content.Context
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tennis.scoremanager.MatchController
import java.io.File

/** Stampa un PDF con la stampa di Android (stampante di casa, Wi-Fi, o "Salva come PDF"): niente PC. */
fun printPdf(context: Context, file: File, name: String) {
    val pm = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
    runCatching {
        pm.print(name, object : PrintDocumentAdapter() {
            override fun onLayout(old: PrintAttributes?, new: PrintAttributes?, cancel: CancellationSignal?, cb: LayoutResultCallback, extras: Bundle?) {
                if (cancel?.isCanceled == true) { cb.onLayoutCancelled(); return }
                cb.onLayoutFinished(PrintDocumentInfo.Builder(file.name).setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).build(), true)
            }

            override fun onWrite(pages: Array<out PageRange>?, dest: ParcelFileDescriptor, cancel: CancellationSignal?, cb: WriteResultCallback) {
                runCatching {
                    file.inputStream().use { i -> java.io.FileOutputStream(dest.fileDescriptor).use { o -> i.copyTo(o) } }
                    cb.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                }.onFailure { cb.onWriteFailed(it.message) }
            }
        }, null)
    }
}

/** Condivide un foglio Excel (salvarlo in Drive o nei File, aprirlo, mandarlo). */
fun shareXlsx(context: Context, c: MatchController, file: File?) {
    if (file == null) { android.widget.Toast.makeText(context, c.strings.rcFailed, android.widget.Toast.LENGTH_LONG).show(); return }
    runCatching { context.startActivity(c.shareFileIntent(file, com.tennis.scoremanager.data.Xlsx.MIME)) }
}

/**
 * Un PDF appena fatto (ricevuta, registro presenze): [onSend] lo manda a chi riguarda (se c'è), oppure lo si condivide
 * o lo si stampa da qui.
 */
@Composable
fun PdfActionsDialog(c: MatchController, file: File?, title: String, hint: String?, onSend: (() -> Unit)?, excel: (() -> File?)? = null, onDone: () -> Unit) {
    val s = LocalStrings.current
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (file == null) { Text(s.rcFailed, color = TsmColors.Orange); return@Column }
                hint?.let { Text(it, color = TsmColors.TextDim, fontSize = 13.sp) }
                if (onSend != null) BigButton(s.rcSend, Icons.AutoMirrored.Filled.Send, { onDone(); onSend() }, Modifier.fillMaxWidth())
                GhostButton(s.share, Icons.Filled.Share, { runCatching { context.startActivity(c.shareFileIntent(file, "application/pdf")) }; onDone() }, Modifier.fillMaxWidth())
                GhostButton(s.rcPrint, Icons.Filled.Print, { printPdf(context, file, title); onDone() }, Modifier.fillMaxWidth())
                // Lo stesso in un foglio Excel, da salvare (Drive, File) o aprire.
                if (excel != null) GhostButton(s.xlSave, Icons.Filled.TableChart, { shareXlsx(context, c, excel()); onDone() }, Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text(s.back) } },
    )
}
