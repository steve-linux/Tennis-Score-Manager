// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.SportsTennis
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tennis.scoremanager.MatchController
import com.tennis.scoremanager.data.CourtPlans
import com.tennis.scoremanager.data.CourtStats
import com.tennis.scoremanager.data.Fees
import com.tennis.scoremanager.ui.GhostButton
import com.tennis.scoremanager.ui.LocalStrings
import com.tennis.scoremanager.ui.ScreenScaffold
import com.tennis.scoremanager.ui.SectionCard
import com.tennis.scoremanager.ui.SegOption
import com.tennis.scoremanager.ui.Segmented
import com.tennis.scoremanager.ui.TsmColors
import java.text.DateFormatSymbols
import java.util.Calendar
import kotlin.math.roundToInt

/** Il periodo: le ultime 4 settimane (fino a oggi compreso), il mese in corso, il mese scorso. */
private fun period(kind: Int, now: Long): Pair<Long, Long> {
    val today = CourtPlans.dayStart(now)
    val tomorrow = CourtPlans.addDays(today, 1)
    val monthStart = Calendar.getInstance().apply { timeInMillis = today; set(Calendar.DAY_OF_MONTH, 1) }.timeInMillis
    return when (kind) {
        0 -> CourtPlans.addDays(tomorrow, -28) to tomorrow
        1 -> monthStart to Calendar.getInstance().apply { timeInMillis = monthStart; add(Calendar.MONTH, 1) }.timeInMillis
        else -> Calendar.getInstance().apply { timeInMillis = monthStart; add(Calendar.MONTH, -1) }.timeInMillis to monthStart
    }
}

/** I soldi senza centesimi quando sono tondi: "4.656 €" sta nella casella, "4.656,00 €" no. */
private fun money0(cents: Long, locale: java.util.Locale): String =
    if (cents % 100 == 0L) java.text.NumberFormat.getCurrencyInstance(locale).apply { currency = java.util.Currency.getInstance("EUR"); maximumFractionDigits = 0; minimumFractionDigits = 0 }.format(cents / 100)
    else Fees.money(cents.toInt(), locale)

private fun pct(x: Double) = "${(x * 100).roundToInt()}%"
private fun hours(x: Double) = if (x == x.roundToInt().toDouble()) x.roundToInt().toString() else "%.1f".format(x)

/** Per la segreteria: quanto sono occupati i campi, quanto rendono, quando si gioca e chi prenota di più. */
@Composable
fun CourtStatsScreen(c: MatchController) {
    val s = LocalStrings.current
    BackHandler { c.back() }
    val plan by c.courtPlan.collectAsState()
    val tournaments by c.clubTournaments.collectAsState()
    val courts by c.clubCourts.collectAsState()
    val locale = c.options.value.lang.locale
    var kind by rememberSaveable { mutableIntStateOf(0) }
    val (from, to) = period(kind, System.currentTimeMillis())
    val r = remember(plan, tournaments, courts, from, to) { CourtStats.report(plan, tournaments, courts, from, to) }
    ScreenScaffold(
        title = s.stTitle,
        subtitle = shortDate(from, c.options.value.lang) + " – " + shortDate(CourtPlans.addDays(to, -1), c.options.value.lang),
        icon = Icons.Filled.BarChart,
        bottomBar = { GhostButton(s.back, Icons.AutoMirrored.Filled.ArrowBack, { c.back() }, Modifier.weight(1f)) },
    ) {
        // Il mese in corso e quello prima, col loro nome: più corti e più chiari di "questo mese".
        val month = java.text.SimpleDateFormat("LLLL", locale)
        val now0 = System.currentTimeMillis()
        val monthNames = listOf(period(1, now0).first, period(2, now0).first).map { month.format(java.util.Date(it)).replaceFirstChar { ch -> ch.titlecase(locale) } }
        Segmented(listOf(SegOption(s.stLast4w), SegOption(monthNames[0]), SegOption(monthNames[1])), kind, { kind = it })
        if (courts.isEmpty()) { Text(s.clubNoCourts, color = TsmColors.TextDim); return@ScreenScaffold }

        // In breve: occupazione, incassi, prenotazioni (e quante online).
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            @Composable fun tile(label: String, value: String, modifier: Modifier) = Column(
                modifier.clip(RoundedCornerShape(14.dp)).background(TsmColors.Surface).padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(value, color = TsmColors.Ball, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text(label, color = TsmColors.TextDim, fontSize = 12.sp, textAlign = TextAlign.Center)
            }
            tile(s.stOccupancy, pct(r.occupancy), Modifier.weight(1f))
            tile(s.stRevenue, money0(r.cents, locale), Modifier.weight(1f))
            tile(s.stBookings, r.bookings.toString(), Modifier.weight(1f))
        }
        if (r.online > 0) Text(s.stOnline(r.online), color = TsmColors.TextDim, fontSize = 13.sp)
        if (r.tournamentHours > 0) Text(s.stTournaments(hours(r.tournamentHours)), color = TsmColors.TextDim, fontSize = 13.sp)

        SectionCard(s.stByCourt, Icons.Filled.SportsTennis) {
            for (row in r.courts) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(row.court, color = TsmColors.TextMain, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(pct(row.occupancy), color = TsmColors.Ball, fontWeight = FontWeight.Bold)
                }
                // La barra dell'occupazione.
                Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(TsmColors.SurfaceHigh)) {
                    Box(Modifier.fillMaxWidth(row.occupancy.toFloat()).height(8.dp).clip(RoundedCornerShape(4.dp)).background(TsmColors.Ball))
                }
                Text(
                    listOfNotNull(
                        s.stHoursOf(hours(row.bookedHours), hours(row.bookable)),
                        s.stBlocked(hours(row.blockedHours)).takeIf { row.blockedHours > 0 },
                        money0(row.cents, locale),
                        s.stCount(row.bookings),
                    ).joinToString(" · "),
                    color = TsmColors.TextDim, fontSize = 12.sp,
                )
            }
        }

        // Quando si gioca: una casella per giorno e ora, più chiara quanto più è pieno.
        SectionCard(s.stHeat, Icons.Filled.BarChart) {
            Text(s.stHeatHint, color = TsmColors.TextDim, fontSize = 12.sp)
            val days = DateFormatSymbols.getInstance(locale).shortWeekdays
            val names = listOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY).map { days[it] }
            // Tutta in larghezza: una colonna per ora, le ore scritte una sì e una no.
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Box(Modifier.width(34.dp))
                    for (h in r.heat.first().indices) Text(if (h % 2 == 0) "${r.firstHour + h}" else "", color = TsmColors.TextDim, fontSize = 10.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                }
                r.heat.forEachIndexed { d, line ->
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(names[d], color = TsmColors.TextDim, fontSize = 11.sp, modifier = Modifier.width(34.dp))
                        for (v in line) Box(
                            Modifier.weight(1f).height(20.dp).clip(RoundedCornerShape(3.dp))
                                .background(if (v <= 0.0) TsmColors.SurfaceHigh else TsmColors.Ball.copy(alpha = (0.15 + 0.85 * v).toFloat())),
                        )
                    }
                }
            }
        }

        if (r.top.isNotEmpty()) SectionCard(s.stTop, Icons.Filled.Groups) {
            for ((who, n) in r.top) Row {
                Text(c.playerById(who)?.name ?: who, color = TsmColors.TextMain, modifier = Modifier.weight(1f))
                Text(s.stCount(n), color = TsmColors.TextDim)
            }
        }
        if (r.bookings == 0) Text(s.stNone, color = TsmColors.TextDim)
        Text(s.stRevenueHint, color = TsmColors.TextDim, fontSize = 11.sp)
    }
}
