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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tennis.scoremanager.MatchController
import com.tennis.scoremanager.data.Progress
import com.tennis.scoremanager.data.ProgressFilter
import com.tennis.scoremanager.data.ProgressItem
import com.tennis.scoremanager.ui.RatingSection
import com.tennis.scoremanager.ui.GhostButton
import com.tennis.scoremanager.ui.LocalStrings
import com.tennis.scoremanager.ui.Picker
import com.tennis.scoremanager.ui.ScreenScaffold
import com.tennis.scoremanager.ui.SectionCard
import com.tennis.scoremanager.ui.SegOption
import com.tennis.scoremanager.ui.Segmented
import com.tennis.scoremanager.ui.TsmColors
import java.io.File
import java.text.DateFormatSymbols
import java.util.Calendar

/**
 * Pagina 1 › Progressi: un documento per gli allenamenti e uno per le partite di un giocatore, per vedere se i numeri
 * migliorano. Si sceglie cosa confrontare: filtri (periodo, giorni della settimana e, per le partite, torneo, circolo
 * e avversario) e un elenco da spuntare. A schermo il confronto tra prima e seconda metà; PDF da condividere o stampare.
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
    // Filtri e spunte valgono per la scheda e il giocatore scelti: cambiandoli si riparte da tutto.
    var filter by remember(matches, key) { mutableStateOf(ProgressFilter()) }
    var excluded by remember(matches, key) { mutableStateOf(emptySet<String>()) }
    val items = remember(history, workouts, matches, key) { if (matches && key == null) emptyList() else c.progressItems(key) }
    val now = remember { System.currentTimeMillis() }
    val shown = items.filter { filter.accepts(it, now) }
    val selected = shown.map { it.id }.filter { it !in excluded }.toSet()
    val report = remember(history, workouts, matches, key, selected) { if (matches && key == null) null else c.progressReport(key, selected) }
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
            RatingSection(c, key)
        }
        if (items.isNotEmpty()) {
            FilterCard(filter, items, matches) { filter = it }
            SelectionCard(shown, excluded, onToggle = { id -> excluded = if (id in excluded) excluded - id else excluded + id },
                onAll = { excluded = excluded - shown.map { it.id }.toSet() }, onNone = { excluded = excluded + shown.map { it.id } })
        }
        report?.let { r -> ReportCard(r) }
        if (report != null && selected.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton(s.progressPdf, Icons.Filled.PictureAsPdf, {
                    c.progressPdf(key, selected)?.let { context.startActivity(c.shareFileIntent(it, "application/pdf")) }
                }, Modifier.weight(1f))
                GhostButton(s.progressPrint, Icons.Filled.Print, {
                    c.progressPdf(key, selected)?.let { printPdf(context, it) }
                }, Modifier.weight(1f))
            }
        }
    }
}

/** Periodo, giorni della settimana (uno o più) e, per le partite, torneo, circolo e avversario dello storico. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterCard(f: ProgressFilter, items: List<ProgressItem>, matches: Boolean, onChange: (ProgressFilter) -> Unit) {
    val s = LocalStrings.current
    val locale = LocalConfiguration.current.locales[0]
    SectionCard(s.progressFilters, Icons.Filled.FilterList) {
        Text(s.progressPeriod, color = TsmColors.TextDim, fontSize = 13.sp)
        val periods = listOf<Int?>(null, 30, 90, 365)
        Segmented(
            listOf(SegOption(s.progressPeriodAll), SegOption(s.progress30), SegOption(s.progress90), SegOption(s.progress365)),
            selected = periods.indexOf(f.days),
            onSelect = { onChange(f.copy(days = periods[it])) },
            columns = 4,
        )
        Text(s.progressWeekdays, color = TsmColors.TextDim, fontSize = 13.sp)
        // Dal lunedì alla domenica, coi nomi brevi della lingua del telefono.
        val names = remember(locale) { DateFormatSymbols(locale).shortWeekdays }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (d in listOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY)) {
                val on = d in f.weekdays
                FilterChip(
                    selected = on,
                    onClick = { onChange(f.copy(weekdays = if (on) f.weekdays - d else f.weekdays + d)) },
                    label = { Text(names[d].replaceFirstChar { it.uppercase() }) },
                )
            }
        }
        if (matches) {
            // Torneo, circolo e avversario: solo quelli che ci sono nelle partite del giocatore.
            val menus: List<Triple<String, String?, Pair<(ProgressItem) -> String, (String?) -> ProgressFilter>>> = listOf(
                Triple(s.eventSection, f.event, Pair({ i: ProgressItem -> i.event }, { v: String? -> f.copy(event = v) })),
                Triple(s.club, f.club, Pair({ i: ProgressItem -> i.club }, { v: String? -> f.copy(club = v) })),
                Triple(s.progressOpponent, f.opponent, Pair({ i: ProgressItem -> i.opponent }, { v: String? -> f.copy(opponent = v) })),
            )
            for ((label, value, fns) in menus) {
                val (field, set) = fns
                val choices = ProgressFilter.choices(items, field)
                if (choices.isEmpty()) continue
                Picker(label, value ?: s.progressAnyOption, listOf<Pair<String?, String>>(null to s.progressAnyOption) + choices.map { it to it }) { v ->
                    onChange(set(v))
                }
            }
        }
    }
}

/** L'elenco da spuntare: si confrontano solo quelli spuntati (all'inizio tutti). */
@Composable
private fun SelectionCard(shown: List<ProgressItem>, excluded: Set<String>, onToggle: (String) -> Unit, onAll: () -> Unit, onNone: () -> Unit) {
    val s = LocalStrings.current
    SectionCard(s.progressChoose, Icons.Filled.Checklist) {
        if (shown.isEmpty()) {
            Text(s.progressNoneMatch, color = TsmColors.Orange)
            return@SectionCard
        }
        val chosen = shown.count { it.id !in excluded }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(s.progressSelected(chosen, shown.size), color = TsmColors.TextDim, fontSize = 13.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = onAll) { Text(s.progressSelectAll) }
            TextButton(onClick = onNone) { Text(s.progressSelectNone) }
        }
        for (it in shown) {
            Row(Modifier.fillMaxWidth().clickable { onToggle(it.id) }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(it.id !in excluded, { _ -> onToggle(it.id) })
                Column(Modifier.weight(1f)) {
                    Text(it.title, color = TsmColors.TextMain, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (it.detail.isNotEmpty()) Text(it.detail, color = TsmColors.TextDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
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
