// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.ui.screens

import android.content.Context
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tennis.scoremanager.MatchController
import com.tennis.scoremanager.data.Progress
import com.tennis.scoremanager.ui.GhostButton
import com.tennis.scoremanager.ui.LocalStrings
import com.tennis.scoremanager.ui.Picker
import com.tennis.scoremanager.ui.ScreenScaffold
import com.tennis.scoremanager.ui.SectionCard
import com.tennis.scoremanager.ui.SegOption
import com.tennis.scoremanager.ui.Segmented
import com.tennis.scoremanager.ui.TsmColors
import java.io.File

/**
 * Pagina 1 › Progressi: un documento per gli allenamenti e uno per le partite di un giocatore, per vedere se i numeri
 * migliorano. A schermo il confronto tra prima e seconda metà del periodo; PDF da condividere o stampare.
 */
@Composable
fun ProgressScreen(c: MatchController) {
    val s = LocalStrings.current
    val context = LocalContext.current
    BackHandler { c.back() }
    val history by c.history.collectAsState()
    val workouts by c.workouts.collectAsState()
    var matches by rememberSaveable { mutableStateOf(false) }
    val players = remember(history) { c.progressPlayers() }
    var player by rememberSaveable { mutableStateOf<String?>(null) }
    val key = if (matches) (player ?: players.firstOrNull()?.first) else null
    val report = remember(history, workouts, matches, key) { if (matches && key == null) null else c.progressReport(key) }
    ScreenScaffold(
        title = s.progressTitle,
        subtitle = s.progressHint,
        icon = Icons.AutoMirrored.Filled.TrendingUp,
        bottomBar = { GhostButton(s.back, Icons.AutoMirrored.Filled.ArrowBack, { c.back() }, Modifier.weight(1f)) },
    ) {
        Segmented(
            listOf(SegOption(s.progressWorkouts), SegOption(s.progressMatches)),
            selected = if (matches) 1 else 0,
            onSelect = { matches = it == 1 },
        )
        if (matches) {
            if (players.isEmpty()) {
                Text(s.progressNoPlayers, color = TsmColors.TextDim)
            } else {
                Picker(s.progressPlayer, players.firstOrNull { it.first == key }?.second ?: "", players) { player = it }
            }
        }
        report?.let { r -> ReportCard(r) }
        if (report != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton(s.progressPdf, Icons.Filled.PictureAsPdf, {
                    c.progressPdf(key)?.let { context.startActivity(c.shareFileIntent(it, "application/pdf")) }
                }, Modifier.weight(1f))
                GhostButton(s.progressPrint, Icons.Filled.Print, {
                    c.progressPdf(key)?.let { printPdf(context, it) }
                }, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ReportCard(r: Progress.Report) {
    val s = LocalStrings.current
    SectionCard(r.title, Icons.AutoMirrored.Filled.TrendingUp) {
        Text("${r.subtitle} · ${s.progressCount(r.count)}", color = TsmColors.TextDim, fontSize = 12.sp)
        if (r.count < 2) {
            Text(s.progressTooFew, color = TsmColors.Orange)
            return@SectionCard
        }
        Text("${s.progressFirstHalf} → ${s.progressSecondHalf}", color = TsmColors.TextDim, fontSize = 12.sp)
        for (m in r.metrics) {
            val h = m.halves ?: continue
            val (arrow, col) = when (m.verdict) {
                1 -> "▲" to TsmColors.Ok
                -1 -> "▼" to TsmColors.Danger
                else -> "=" to TsmColors.TextDim
            }
            Row(Modifier.fillMaxWidth()) {
                Text(m.label, color = TsmColors.TextMain, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text("${m.format(h.first)} → ${m.format(h.second)}${if (m.unit.isNotEmpty()) " ${m.unit}" else ""}  ",
                    color = TsmColors.TextMain, fontSize = 14.sp)
                Text(arrow, color = col, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Stampa il PDF con il servizio di stampa di Android (anche "Salva come PDF"). */
private fun printPdf(context: Context, file: File) {
    val pm = context.getSystemService(PrintManager::class.java) ?: return
    val adapter = object : PrintDocumentAdapter() {
        override fun onLayout(old: PrintAttributes?, new: PrintAttributes, cancel: CancellationSignal, cb: LayoutResultCallback, extras: Bundle?) {
            if (cancel.isCanceled) return cb.onLayoutCancelled()
            cb.onLayoutFinished(PrintDocumentInfo.Builder(file.name).setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).build(), true)
        }

        override fun onWrite(pages: Array<out PageRange>, dest: ParcelFileDescriptor, cancel: CancellationSignal, cb: WriteResultCallback) {
            runCatching {
                file.inputStream().use { input -> ParcelFileDescriptor.AutoCloseOutputStream(dest).use { input.copyTo(it) } }
            }.onSuccess { cb.onWriteFinished(arrayOf(PageRange.ALL_PAGES)) }.onFailure { cb.onWriteFailed(it.message) }
        }
    }
    pm.print(file.nameWithoutExtension, adapter, null)
}
