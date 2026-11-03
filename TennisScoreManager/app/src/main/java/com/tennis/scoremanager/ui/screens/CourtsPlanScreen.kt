// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.ui.screens

import android.app.TimePickerDialog
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.SportsTennis
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Euro
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tennis.scoremanager.MatchController
import com.tennis.scoremanager.data.BookingKind
import com.tennis.scoremanager.data.CourtBooking
import com.tennis.scoremanager.data.CourtPlans
import com.tennis.scoremanager.data.CourtSettings
import com.tennis.scoremanager.data.Fees
import com.tennis.scoremanager.data.Occupied
import com.tennis.scoremanager.data.PlayerProfile
import com.tennis.scoremanager.data.Players
import com.tennis.scoremanager.data.Reports
import com.tennis.scoremanager.ui.GhostButton
import com.tennis.scoremanager.ui.LocalStrings
import com.tennis.scoremanager.ui.PlayerEditDialog
import com.tennis.scoremanager.ui.ScreenScaffold
import com.tennis.scoremanager.ui.SegOption
import com.tennis.scoremanager.ui.Segmented
import com.tennis.scoremanager.ui.Strings
import com.tennis.scoremanager.ui.SwitchRow
import com.tennis.scoremanager.ui.TsmColors
import com.tennis.scoremanager.ui.ctRoundName
import java.util.Calendar

private const val CELL_W = 118
private const val CELL_H = 54
private const val TIME_W = 52

fun bookingKindName(s: Strings, k: BookingKind): String = when (k) {
    BookingKind.PLAY -> s.bkPlay
    BookingKind.LESSON -> s.bkLesson
    BookingKind.CLOSED -> s.bkClosed
    BookingKind.OTHER -> s.bkOther
}

private fun kindColor(o: Occupied): Color = when {
    o.block != null -> TsmColors.Outline.copy(alpha = 0.55f)
    o.match != null -> TsmColors.Player1.copy(alpha = 0.35f)
    o.booking?.kind == BookingKind.LESSON -> TsmColors.Cyan.copy(alpha = 0.35f)
    o.booking?.kind == BookingKind.CLOSED -> TsmColors.Danger.copy(alpha = 0.3f)
    o.booking?.kind == BookingKind.OTHER -> TsmColors.Outline
    else -> TsmColors.Ball.copy(alpha = 0.3f)
}

/** Chi c'è in una casella: i giocatori, o la partita del torneo, o il motivo della chiusura. */
private fun occupiedLabel(c: MatchController, s: Strings, o: Occupied): String {
    o.match?.let { m ->
        val t = o.tournament!!
        fun n(id: String?) = t.entries.firstOrNull { it.id == id }?.let { e -> e.players.mapIndexed { i, p -> c.playerById(p)?.name ?: e.names.getOrNull(i).orEmpty() }.joinToString("/") } ?: "—"
        return "${t.name} · ${ctRoundName(s, t, m)}\n${n(m.a)} – ${n(m.b)}"
    }
    o.block?.let { k -> return s.blkCell(k.label.ifBlank { blockReasonName(s, k.reason) }) }
    val b = o.booking ?: return ""
    val who = (b.players.mapNotNull { c.playerById(it)?.name } + b.names).joinToString(" · ")
    return listOf(if (b.kind == BookingKind.PLAY) "" else bookingKindName(s, b.kind), who + if (b.online != null) " · " + s.srvOnlineTag else "", b.note).filter { it.isNotBlank() }.joinToString("\n")
}

/**
 * Circolo › Campi › Planning: il giorno, un campo per colonna e un'ora per riga. Si tocca una casella libera per
 * prenotare, una prenotazione per cambiarla; le partite dei tornei con campo e orario ci sono già.
 */
@Composable
fun CourtsPlanScreen(c: MatchController) {
    val s = LocalStrings.current
    val context = LocalContext.current
    BackHandler { c.back() }
    val plan by c.courtPlan.collectAsState()
    val tournaments by c.clubTournaments.collectAsState()
    val lang = c.options.value.lang
    val courts by c.clubCourts.collectAsState()
    var day by rememberSaveable { mutableStateOf(CourtPlans.dayStart(System.currentTimeMillis())) }
    // Dalla notifica di una prenotazione online: il planning si apre sul suo giorno.
    val askedDay by c.planDayRequest.collectAsState()
    androidx.compose.runtime.LaunchedEffect(askedDay) { askedDay?.let { day = CourtPlans.dayStart(it); c.planDayRequest.value = null } }
    var editing by remember { mutableStateOf<CourtBooking?>(null) }
    var settings by remember { mutableStateOf(false) }
    var blocks by remember { mutableStateOf(false) }
    var manage by remember { mutableStateOf(false) }
    var server by remember { mutableStateOf(false) }
    var editBlock by remember { mutableStateOf<com.tennis.scoremanager.data.CourtBlock?>(null) }
    val occ = CourtPlans.occupied(plan, tournaments, day)
    val slots = CourtPlans.slots(day, plan.settings)
    val stepMs = plan.settings.slotMinutes * 60_000L
    ScreenScaffold(
        title = s.planTitle,
        subtitle = com.tennis.scoremanager.data.Notices.day(day, lang),
        icon = Icons.Filled.CalendarMonth,
        action = { IconButton(onClick = { settings = true }) { Icon(Icons.Filled.Settings, s.planSettings, tint = TsmColors.Ball) } },
        bottomBar = { GhostButton(s.back, Icons.AutoMirrored.Filled.ArrowBack, { c.back() }, Modifier.weight(1f)) },
    ) {
        // Il giorno: indietro, oggi, il calendario, avanti.
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { day = CourtPlans.addDays(day, -1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = TsmColors.TextMain) }
            // Il giorno prende lo spazio che resta: le due frecce restano a vista.
            DateButton(s.planDay, day, lang, Modifier.weight(1f)) { day = CourtPlans.dayStart(it) }
            IconButton(onClick = { day = CourtPlans.addDays(day, 1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = TsmColors.TextMain) }
        }
        if (day != CourtPlans.dayStart(System.currentTimeMillis())) GhostButton(s.planToday, Icons.Filled.Today, { day = CourtPlans.dayStart(System.currentTimeMillis()) }, Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton(s.crtTitle, Icons.Filled.SportsTennis, { manage = true }, Modifier.weight(1f))
            GhostButton(s.blkShort, Icons.Filled.Block, { blocks = true }, Modifier.weight(1f))
        }
        val features by c.clubFeatures.collectAsState()
        if (com.tennis.scoremanager.ClubFeature.STATS in features) GhostButton(s.stTitle, Icons.Filled.BarChart, { c.go(com.tennis.scoremanager.Screen.COURT_STATS) }, Modifier.fillMaxWidth())
        // Il server del circolo, se c'è: lo stato, e ogni minuto le prenotazioni online.
        if (com.tennis.scoremanager.ClubFeature.SERVER in features) ServerStatusLine(c) { server = true }
        // Quanto è libero ogni campo, a colpo d'occhio.
        Text(courts.joinToString(" · ") { ct -> "$ct ${s.planBusy(CourtPlans.busyMinutes(occ, ct) / 60)}" }, color = TsmColors.TextDim, fontSize = 12.sp)
        Column(Modifier.horizontalScroll(rememberScrollState())) {
            Row {
                Box(Modifier.width(TIME_W.dp))
                for (ct in courts) Column(Modifier.width(CELL_W.dp).padding(horizontal = 4.dp, vertical = 6.dp)) {
                    Text(ct, color = TsmColors.TextMain, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(courtTraits(s, plan.settings.info(ct)), color = TsmColors.TextDim, fontSize = 11.sp, maxLines = 2, lineHeight = 13.sp)
                }
            }
            for (slot in slots) {
                Row {
                    Text(Reports.time(slot, lang), color = TsmColors.TextDim, fontSize = 12.sp, modifier = Modifier.width(TIME_W.dp).padding(top = 4.dp))
                    for (ct in courts) {
                        val o = occ.firstOrNull { it.block == null && it.court == ct && it.start < slot + stepMs && it.end > slot }
                            ?: occ.firstOrNull { CourtPlans.blockCovers(it, ct) && it.start < slot + stepMs && it.end > slot }
                        val starts = o != null && (o.start >= slot || slot == slots.first())
                        Box(
                            Modifier.width(CELL_W.dp).height(CELL_H.dp).padding(1.dp).clip(RoundedCornerShape(6.dp))
                                .background(o?.let(::kindColor) ?: TsmColors.Surface)
                                .border(1.dp, TsmColors.Outline.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                .clickable {
                                    when {
                                        o?.booking != null -> editing = o.booking
                                        o?.tournament != null -> c.openClubTournament(o.tournament.id)
                                        // Nel blocco di un corso si mettono le sue lezioni; gli altri blocchi si aprono per cambiarli.
                                        o?.block?.reason == com.tennis.scoremanager.data.BlockReason.COURSE ->
                                            editing = CourtBooking(CourtPlans.newId("b"), ct, slot, plan.settings.slotMinutes, BookingKind.LESSON, note = o.block.label)
                                        o?.block != null -> editBlock = o.block
                                        else -> editing = CourtBooking(CourtPlans.newId("b"), ct, slot, plan.settings.slotMinutes)
                                    }
                                }.padding(4.dp),
                        ) {
                            if (o == null) Icon(Icons.Filled.Add, null, tint = TsmColors.Outline, modifier = Modifier.align(Alignment.Center))
                            else if (starts) Text(occupiedLabel(c, s, o), color = TsmColors.TextMain, fontSize = 11.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 13.sp)
                        }
                    }
                }
            }
        }
    }
    editing?.let { b ->
        BookingDialog(c, b, isNew = plan.bookings.none { it.id == b.id }) { skipped ->
            editing = null
            if (!skipped.isNullOrEmpty()) Toast.makeText(context, s.bkSkipped(skipped.joinToString(", ") { shortDate(it, lang) }), Toast.LENGTH_LONG).show()
        }
    }
    if (settings) PlanSettingsDialog(c, plan.settings) { settings = false }
    if (manage) CourtsManageDialog(c) { manage = false }
    if (server) ClubServerDialog(c) { server = false }
    if (blocks) BlocksDialog(c, onEdit = { blocks = false; editBlock = it }) { blocks = false }
    editBlock?.let { b -> BlockEditDialog(c, b, isNew = plan.blocks.none { it.id == b.id }) { editBlock = null } }
}

/** Prenotazione nuova o da cambiare. [onDone] riceve le date saltate di una ripetizione (null = annullata). */
@Composable
private fun BookingDialog(c: MatchController, b0: CourtBooking, isNew: Boolean, onDone: (List<Long>?) -> Unit) {
    val s = LocalStrings.current
    val context = LocalContext.current
    val lang = c.options.value.lang
    val plan by c.courtPlan.collectAsState()
    val tournaments by c.clubTournaments.collectAsState()
    val courts = c.storage.clubCourts
    var b by remember { mutableStateOf(b0) }
    var weeks by remember { mutableStateOf(1) }
    var guests by remember { mutableStateOf(b0.names.joinToString(", ")) }
    var priceText by remember { mutableStateOf(if (isNew) "" else plain(b0.cents, lang)) }
    var manualPrice by remember { mutableStateOf(!isNew) }
    var charge by remember { mutableStateOf(b0.charges.isNotEmpty() || isNew) }
    var picking by remember { mutableStateOf(false) }
    var askDelete by remember { mutableStateOf(false) }
    val info = plan.settings.info(b.court)
    val autoPrice = CourtPlans.price(plan.settings, b.start, b.minutes, b.kind, b.court, b.heating)
    // Il prezzo calcolato finché non lo si scrive a mano (una prenotazione salvata tiene il suo).
    val cents = if (manualPrice) Fees.parse(priceText) ?: 0 else autoPrice
    val conflicts = CourtPlans.conflicts(plan, tournaments, b.court, b.start, b.end, except = b.id)
    val blocked = CourtPlans.blockedBy(plan, b.court, b.start, b.end, b.kind)
    AlertDialog(
        onDismissRequest = { onDone(null) },
        title = { Text(if (isNew) s.bkNew else c.bookingLabel(b0)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (courts.size > 1) Segmented(courts.map { SegOption(it) }, courts.indexOf(b.court), { b = b.copy(court = courts[it]) }, columns = minOf(3, courts.size))
                DateButton(s.planDay, b.start, lang) { d ->
                    val old = Calendar.getInstance().apply { timeInMillis = b.start }
                    b = b.copy(start = CourtPlans.at(d, old.get(Calendar.HOUR_OF_DAY), old.get(Calendar.MINUTE)))
                }
                GhostButton("${s.ctTime}: ${Reports.time(b.start, lang)} – ${Reports.time(b.end, lang)}", Icons.Filled.Schedule, {
                    val cal = Calendar.getInstance().apply { timeInMillis = b.start }
                    TimePickerDialog(context, { _, h, m -> b = b.copy(start = CourtPlans.at(b.start, h, m)) }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
                }, Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(s.bkDuration, color = TsmColors.TextMain, modifier = Modifier.weight(1f))
                    IconButton(onClick = { b = b.copy(minutes = (b.minutes - 30).coerceAtLeast(30)) }) { Icon(Icons.Filled.Remove, null) }
                    Text(s.bkMinutes(b.minutes), color = TsmColors.TextMain, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { b = b.copy(minutes = (b.minutes + 30).coerceAtMost(600)) }) { Icon(Icons.Filled.Add, null) }
                }
                val kinds = BookingKind.entries
                Segmented(kinds.map { SegOption(bookingKindName(s, it)) }, kinds.indexOf(b.kind), { b = b.copy(kind = kinds[it]) }, columns = 2)
                if (conflicts.isNotEmpty()) Text(s.bkConflict, color = TsmColors.Danger, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                for (k in blocked) Text(s.blkBlocked(k.label.ifBlank { blockReasonName(s, k.reason) }), color = TsmColors.Danger, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                if (b.kind != BookingKind.CLOSED) {
                    // Chi gioca: dal database (si possono addebitare) e ospiti scritti a mano.
                    for (id in b.players) c.playerById(id)?.let { p ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(p.name, color = TsmColors.TextMain, modifier = Modifier.weight(1f))
                            IconButton(onClick = { b = b.copy(players = b.players - id) }) { Icon(Icons.Filled.Close, null, tint = TsmColors.TextDim) }
                        }
                    }
                    if (b.players.size < 4) GhostButton(s.bkAddPlayer, Icons.Filled.PersonAdd, { picking = true }, Modifier.fillMaxWidth())
                    OutlinedTextField(guests, { guests = it.take(80) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(s.bkGuests) })
                }
                OutlinedTextField(b.note, { b = b.copy(note = it.take(80)) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(s.bkNote) })
                if (b.kind == BookingKind.PLAY || b.kind == BookingKind.LESSON) {
                    if (info.heating) SwitchRow(Icons.Filled.Thermostat, s.crtHeating, info.heatingCentsPerHour.takeIf { it > 0 }?.let { s.crtHeatingPrice(Fees.money(it, lang.locale)) }, b.heating) { b = b.copy(heating = it); manualPrice = false }
                    MoneyField(if (manualPrice) priceText else plain(autoPrice, lang), s.bkPrice) { priceText = it; manualPrice = true }
                    if (CourtPlans.needsLights(plan.settings, b.start, b.minutes)) Text(if (info.lights) s.bkLights else s.crtNoLightsWarn, color = if (info.lights) TsmColors.TextDim else TsmColors.Orange, fontSize = 12.sp)
                    // Come si paga: al campo o con Satispay (va nell'avviso ai giocatori).
                    if (cents > 0) {
                        Text(s.bkPayWhere, color = TsmColors.TextDim, fontSize = 13.sp)
                        val ways = com.tennis.scoremanager.data.PayWhere.entries
                        Segmented(listOf(SegOption(s.bkPayCourt), SegOption(s.bkPayOnline)), ways.indexOf(b.payWhere), { b = b.copy(payWhere = ways[it]) })
                        if (b.payWhere == com.tennis.scoremanager.data.PayWhere.ONLINE) {
                            c.clubBook.collectAsState().value
                            val ons = c.payTypes().filter { it.online && it.details.isNotBlank() }
                            Text(if (ons.isEmpty()) s.payOptionsNone else ons.joinToString(" · ") { it.name }, color = if (ons.isEmpty()) TsmColors.Orange else TsmColors.TextDim, fontSize = 12.sp)
                        }
                    }
                    // Prenotazione già addebitata: si segna pagata in un tocco.
                    val book by c.clubBook.collectAsState()
                    val left = b0.charges.sumOf { id -> book.charges.firstOrNull { it.id == id }?.left ?: 0 }
                    if (!isNew && b0.charges.isNotEmpty()) {
                        if (left == 0) Text(s.chargePaid, color = TsmColors.Ok, fontWeight = FontWeight.Bold)
                        else {
                            Text(s.issueFeeDue(Fees.money(left, lang.locale)), color = TsmColors.Orange, fontWeight = FontWeight.Bold)
                            // Un tasto per ogni modo di pagamento del circolo.
                            for (t in c.payTypes()) GhostButton(s.bkPaidWith(t.name), Icons.Filled.Euro, { c.payBooking(b0, t) }, Modifier.fillMaxWidth())
                        }
                    }
                    // L'addebito va in Quote e pagamenti: solo se il circolo le usa.
                    if (b.players.isNotEmpty() && cents > 0 && c.has(com.tennis.scoremanager.ClubFeature.PAYMENTS)) SwitchRow(Icons.Filled.Euro, s.bkCharge(b.players.size), null, charge) { charge = it }
                }
                if (isNew) Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Repeat, null, tint = TsmColors.TextDim)
                    Text(if (weeks == 1) s.bkRepeatNo else s.bkRepeatWeeks(weeks), color = TsmColors.TextMain, modifier = Modifier.weight(1f).padding(start = 8.dp))
                    IconButton(onClick = { weeks = (weeks - 1).coerceAtLeast(1) }) { Icon(Icons.Filled.Remove, null) }
                    IconButton(onClick = { weeks = (weeks + 1).coerceAtMost(40) }) { Icon(Icons.Filled.Add, null) }
                }
                if (!isNew) {
                    if (b0.players.isNotEmpty()) GhostButton(s.bkNotify, Icons.Filled.Campaign, { onDone(null); c.noticeBooking(b0) }, Modifier.fillMaxWidth())
                    TextButton(onClick = { askDelete = true }) { Text(s.bkDelete, color = TsmColors.Danger) }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = conflicts.isEmpty() && blocked.isEmpty(), onClick = {
                val saved = b.copy(
                    names = guests.split(',').map { it.trim() }.filter { it.isNotEmpty() }, cents = if (b.kind == BookingKind.PLAY || b.kind == BookingKind.LESSON) cents else 0,
                    lights = CourtPlans.needsLights(plan.settings, b.start, b.minutes) && info.lights, heating = b.heating && info.heating,
                )
                onDone(c.saveBooking(saved, weeks, charge && saved.cents > 0 && saved.players.isNotEmpty() && c.has(com.tennis.scoremanager.ClubFeature.PAYMENTS)))
            }) { Text(s.ok) }
        },
        dismissButton = { TextButton(onClick = { onDone(null) }) { Text(s.back) } },
    )
    if (picking) PlayerPickDialog(c, b.players.toSet()) { p -> picking = false; p?.let { b = b.copy(players = b.players + it.id) } }
    if (askDelete) AlertDialog(
        onDismissRequest = { askDelete = false },
        text = { Text(s.bkDeleteAsk) },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = { askDelete = false; c.deleteBooking(b0, false); onDone(null) }) { Text(s.bkDeleteOne, color = TsmColors.Danger) }
                if (b0.series != null) TextButton(onClick = { askDelete = false; c.deleteBooking(b0, true); onDone(null) }) { Text(s.bkDeleteSeries, color = TsmColors.Danger) }
            }
        },
        dismissButton = { TextButton(onClick = { askDelete = false }) { Text(s.back) } },
    )
}

/** Sceglie un giocatore dal database (o ne crea uno), esclusi quelli già scelti. */
@Composable
fun PlayerPickDialog(c: MatchController, except: Set<String>, title: String? = null, query0: String = "", onDone: (PlayerProfile?) -> Unit) {
    val s = LocalStrings.current
    val players by c.players.collectAsState()
    var query by remember { mutableStateOf(query0) }
    var creating by remember { mutableStateOf(false) }
    val free = players.filter { it.id !in except }
    // Prima soci e allievi, poi gli altri; con la ricerca, chi corrisponde meglio.
    val list = if (query.isBlank()) free.sortedWith(compareBy({ if (it.member != null || it.student) 0 else 1 }, { it.name.lowercase() })) else Players.search(free, query)
    AlertDialog(
        onDismissRequest = { onDone(null) },
        title = { Text(title ?: s.bkAddPlayer) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                GhostButton(s.membersNewPlayer, Icons.Filled.PersonAdd, { creating = true }, Modifier.fillMaxWidth())
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(s.playersSearch) }, leadingIcon = { Icon(Icons.Filled.Search, null) })
                if (list.isEmpty()) Text(s.playersNone, color = TsmColors.TextDim, fontSize = 13.sp)
                for (p in list.take(60)) Row(Modifier.fillMaxWidth().clickable { onDone(p) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    com.tennis.scoremanager.ui.PlayerAvatar(c, p.name, TsmColors.Ball, 36.dp)
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(p.name, color = TsmColors.TextMain, fontWeight = FontWeight.Bold)
                        val info = listOfNotNull(p.fullName.takeIf { it.isNotBlank() && it != p.name }, p.member?.ranking?.ifBlank { null }, s.studentsTitle.takeIf { p.student }, s.membersTitle.takeIf { p.member != null }).joinToString(" · ")
                        if (info.isNotBlank()) Text(info, color = TsmColors.TextDim, fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { onDone(null) }) { Text(s.back) } },
    )
    if (creating) PlayerEditDialog(c, null, prefill = query) { p -> creating = false; if (p != null) onDone(p) }
}

/** Orari, caselle, prezzi e luci del planning. */
@Composable
private fun PlanSettingsDialog(c: MatchController, st0: CourtSettings, onDone: () -> Unit) {
    val s = LocalStrings.current
    val lang = c.options.value.lang
    var st by remember { mutableStateOf(st0) }
    var price by remember { mutableStateOf(plain(st0.centsPerHour, lang)) }
    var priceL by remember { mutableStateOf(plain(st0.centsPerHourLights, lang)) }
    var courtsOpen by remember { mutableStateOf(false) }
    var serverOpen by remember { mutableStateOf(false) }
    @Composable fun stepper(label: String, v: Int, min: Int, max: Int, onChange: (Int) -> Unit) = Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = TsmColors.TextMain, modifier = Modifier.weight(1f))
        IconButton(onClick = { onChange((v - 1).coerceAtLeast(min)) }) { Icon(Icons.Filled.Remove, null) }
        Text("%02d:00".format(v), color = TsmColors.TextMain, fontWeight = FontWeight.Bold)
        IconButton(onClick = { onChange((v + 1).coerceAtMost(max)) }) { Icon(Icons.Filled.Add, null) }
    }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(s.planSettings) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                stepper(s.planOpen, st.openHour, 0, st.closeHour - 1) { st = st.copy(openHour = it) }
                stepper(s.planClose, st.closeHour, st.openHour + 1, 24) { st = st.copy(closeHour = it) }
                Text(s.planSlot, color = TsmColors.TextDim, fontSize = 13.sp)
                val slots = listOf(30, 60, 90)
                Segmented(slots.map { SegOption(s.bkMinutes(it)) }, slots.indexOf(st.slotMinutes), { st = st.copy(slotMinutes = slots[it]) })
                MoneyField(price, s.planPrice) { price = it }
                MoneyField(priceL, s.planPriceLights) { priceL = it }
                stepper(s.planLightsFrom, st.lightsFrom, 0, 24) { st = st.copy(lightsFrom = it) }
                GhostButton(s.crtTitle, Icons.Filled.SportsTennis, { courtsOpen = true }, Modifier.fillMaxWidth())
                if (c.has(com.tennis.scoremanager.ClubFeature.SERVER)) {
                    GhostButton(s.srvTitle, Icons.Filled.Cloud, { serverOpen = true }, Modifier.fillMaxWidth())
                    Text(if (c.clubServer.value.paired) s.srvConnected(c.clubServer.value.club.ifBlank { c.clubServer.value.url }) else s.srvButtonHint, color = TsmColors.TextDim, fontSize = 12.sp)
                }
                Text(s.planTournamentMinutes, color = TsmColors.TextDim, fontSize = 13.sp)
                val tm = listOf(60, 90, 120)
                Segmented(tm.map { SegOption(s.bkMinutes(it)) }, tm.indexOf(st.tournamentMinutes), { st = st.copy(tournamentMinutes = tm[it]) })
            }
        },
        confirmButton = {
            TextButton(enabled = Fees.parse(price.ifBlank { "0" }) != null && Fees.parse(priceL.ifBlank { "0" }) != null, onClick = {
                // I campi si cambiano nel loro dialogo: qui si tengono come sono adesso.
                c.saveCourtSettings(st.copy(courts = c.courtPlan.value.settings.courts, centsPerHour = Fees.parse(price.ifBlank { "0" }) ?: 0, centsPerHourLights = Fees.parse(priceL.ifBlank { "0" }) ?: 0))
                onDone()
            }) { Text(s.ok) }
        },
        dismissButton = { TextButton(onClick = onDone) { Text(s.back) } },
    )
    if (courtsOpen) CourtsManageDialog(c) { courtsOpen = false }
    if (serverOpen) ClubServerDialog(c) { serverOpen = false }
}

fun surfaceName(s: Strings, x: com.tennis.scoremanager.data.Surface): String = when (x) {
    com.tennis.scoremanager.data.Surface.CLAY -> s.crtClay
    com.tennis.scoremanager.data.Surface.GRASS -> s.crtGrass
    com.tennis.scoremanager.data.Surface.HARD -> s.crtHard
    com.tennis.scoremanager.data.Surface.OTHER -> s.bkOther
}

/** "Terra · coperto · luci · riscaldato". */
fun courtTraits(s: Strings, i: com.tennis.scoremanager.data.CourtInfo): String = listOfNotNull(
    i.surface?.let { surfaceName(s, it) }, if (i.covered) s.crtCovered else s.crtOpen, s.crtLightsShort.takeIf { i.lights }, s.crtHeatedShort.takeIf { i.heating },
).joinToString(" · ")

/**
 * I campi del circolo: si creano, si mettono in ordine, si toccano per cambiarli (nome, caratteristiche, prezzi,
 * foto) o eliminarli.
 */
@Composable
fun CourtsManageDialog(c: MatchController, onDone: () -> Unit) {
    val s = LocalStrings.current
    val lang = c.options.value.lang
    val courts by c.clubCourts.collectAsState()
    val plan by c.courtPlan.collectAsState()
    var edit by remember { mutableStateOf<com.tennis.scoremanager.data.CourtInfo?>(null) }
    var creating by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(s.crtTitle) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (courts.isEmpty()) Text(s.clubNoCourts, color = TsmColors.TextDim)
                for ((idx, ct) in courts.withIndex()) {
                    val i = plan.settings.info(ct)
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(TsmColors.SurfaceHigh).clickable { edit = i }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        i.photos.firstOrNull()?.let { CourtThumb(c, it, 48) }
                        Column(Modifier.weight(1f).padding(start = 8.dp)) {
                            Text(ct, color = TsmColors.TextMain, fontWeight = FontWeight.Bold)
                            Text(
                                listOfNotNull(courtTraits(s, i), i.centsPerHour?.let { Fees.money(it, lang.locale) + "/h" }, i.photos.size.takeIf { it > 0 }?.let { s.crtPhotosCount(it) }).joinToString(" · "),
                                color = TsmColors.TextDim, fontSize = 12.sp,
                            )
                        }
                        if (idx > 0) IconButton(onClick = { c.moveCourt(ct, true) }) { Icon(Icons.Filled.KeyboardArrowUp, null, tint = TsmColors.TextDim) }
                    }
                }
                GhostButton(s.crtNew, Icons.Filled.Add, { creating = true }, Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text(s.ok) } },
    )
    edit?.let { i -> CourtInfoDialog(c, i, isNew = false) { edit = null } }
    if (creating) CourtInfoDialog(c, com.tennis.scoremanager.data.CourtInfo(""), isNew = true) { creating = false }
}

/** Una foto del campo, quadrata e ritagliata. */
@Composable
private fun CourtThumb(c: MatchController, name: String, size: Int, onClick: (() -> Unit)? = null) {
    val bmp = remember(name) { runCatching { android.graphics.BitmapFactory.decodeFile(c.courtPhotoFile(name).absolutePath, android.graphics.BitmapFactory.Options().apply { inSampleSize = 4 }) }.getOrNull() }
    bmp ?: return
    androidx.compose.foundation.Image(
        bmp.asImageBitmap(), null, contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        modifier = Modifier.width(size.dp).height(size.dp).clip(RoundedCornerShape(8.dp)).let { m -> if (onClick != null) m.clickable { onClick() } else m },
    )
}

/** Com'è fatto un campo: nome, superficie, coperto, luci, riscaldamento, i suoi prezzi (vuoti = quelli generali) e le foto. */
@Composable
private fun CourtInfoDialog(c: MatchController, i0: com.tennis.scoremanager.data.CourtInfo, isNew: Boolean, onDone: () -> Unit) {
    val s = LocalStrings.current
    val context = LocalContext.current
    val lang = c.options.value.lang
    val plan by c.courtPlan.collectAsState()
    var i by remember { mutableStateOf(i0) }
    var name by remember { mutableStateOf(i0.name) }
    var price by remember { mutableStateOf(i0.centsPerHour?.let { plain(it, lang) }.orEmpty()) }
    var priceL by remember { mutableStateOf(i0.centsPerHourLights?.let { plain(it, lang) }.orEmpty()) }
    var heat by remember { mutableStateOf(i0.heatingCentsPerHour.takeIf { it > 0 }?.let { plain(it, lang) }.orEmpty()) }
    var askDelete by remember { mutableStateOf(false) }
    var big by remember { mutableStateOf<String?>(null) }
    val ok = name.isNotBlank() && listOf(price, priceL, heat).all { it.isBlank() || Fees.parse(it) != null }
    // Le foto si leggono dal planning: si aggiungono e si tolgono subito, anche prima di OK.
    val photos = if (isNew) emptyList() else plan.settings.info(i0.name).photos
    val pick = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia(6)) { uris ->
        c.addCourtPhotos(i0.name, uris)
    }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(if (isNew) s.crtNew else i0.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it.take(30) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(s.crtName) })
                val surfaces = listOf(null) + com.tennis.scoremanager.data.Surface.entries
                Segmented(surfaces.map { SegOption(it?.let { x -> surfaceName(s, x) } ?: "—") }, surfaces.indexOf(i.surface), { i = i.copy(surface = surfaces[it]) }, columns = 3)
                Segmented(listOf(SegOption(s.crtOpen), SegOption(s.crtCovered)), if (i.covered) 1 else 0, { i = i.copy(covered = it == 1) })
                SwitchRow(Icons.Filled.Lightbulb, s.crtLights, null, i.lights) { i = i.copy(lights = it) }
                SwitchRow(Icons.Filled.Thermostat, s.crtHeating, null, i.heating) { i = i.copy(heating = it) }
                Text(s.crtPricesHint, color = TsmColors.TextDim, fontSize = 12.sp)
                MoneyField(price, s.planPrice) { price = it }
                if (i.lights) MoneyField(priceL, s.planPriceLights) { priceL = it }
                if (i.heating) MoneyField(heat, s.crtHeatingSupplement) { heat = it }
                Text(s.crtPhotos, color = TsmColors.TextMain, fontWeight = FontWeight.Bold)
                if (isNew) Text(s.crtPhotosAfter, color = TsmColors.TextDim, fontSize = 12.sp)
                else {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (p in photos) Box {
                            CourtThumb(c, p, 84) { big = p }
                            IconButton(onClick = { c.removeCourtPhoto(i0.name, p) }, modifier = Modifier.align(Alignment.TopEnd).width(28.dp).height(28.dp)) {
                                Icon(Icons.Filled.Close, s.crtPhotoRemove, tint = Color.White, modifier = Modifier.background(Color(0x99000000), RoundedCornerShape(14.dp)))
                            }
                        }
                    }
                    GhostButton(s.crtPhotoAdd, Icons.Filled.AddPhotoAlternate, {
                        pick.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }, Modifier.fillMaxWidth())
                    TextButton(onClick = { askDelete = true }) { Text(s.crtDelete, color = TsmColors.Danger) }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = ok, onClick = {
                val n = name.trim()
                val saved = i.copy(
                    name = n, centsPerHour = price.ifBlank { null }?.let(Fees::parse), centsPerHourLights = priceL.ifBlank { null }?.let(Fees::parse),
                    heatingCentsPerHour = heat.ifBlank { null }?.let(Fees::parse) ?: 0,
                )
                val done = if (isNew) c.addCourt(saved) else c.renameCourt(i0.name, n).also { okRename ->
                    // Le foto sono quelle di adesso (aggiunte o tolte mentre il dialogo era aperto).
                    if (okRename) c.saveCourtInfo(saved.copy(photos = c.courtPlan.value.settings.info(n).photos))
                }
                if (done) onDone() else Toast.makeText(context, s.crtExists, Toast.LENGTH_LONG).show()
            }) { Text(s.ok) }
        },
        dismissButton = { TextButton(onClick = onDone) { Text(s.back) } },
    )
    if (askDelete) AlertDialog(
        onDismissRequest = { askDelete = false },
        text = { Text(s.crtDeleteAsk(i0.name, c.futureBookings(i0.name))) },
        confirmButton = { TextButton(onClick = { askDelete = false; c.deleteCourt(i0.name); onDone() }) { Text(s.crtDelete, color = TsmColors.Danger) } },
        dismissButton = { TextButton(onClick = { askDelete = false }) { Text(s.back) } },
    )
    big?.let { p ->
        androidx.compose.ui.window.Dialog(onDismissRequest = { big = null }, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
            val bmp = remember(p) { runCatching { android.graphics.BitmapFactory.decodeFile(c.courtPhotoFile(p).absolutePath) }.getOrNull() }
            Box(Modifier.fillMaxWidth().background(Color.Black).clickable { big = null }, contentAlignment = Alignment.Center) {
                bmp?.let { androidx.compose.foundation.Image(it.asImageBitmap(), null, modifier = Modifier.fillMaxWidth(), contentScale = androidx.compose.ui.layout.ContentScale.Fit) }
            }
        }
    }
}

fun blockReasonName(s: Strings, r: com.tennis.scoremanager.data.BlockReason): String = when (r) {
    com.tennis.scoremanager.data.BlockReason.COURSE -> s.blkCourse
    com.tennis.scoremanager.data.BlockReason.TOURNAMENT -> s.blkTournament
    com.tennis.scoremanager.data.BlockReason.MAINTENANCE -> s.blkMaintenance
    com.tennis.scoremanager.data.BlockReason.OTHER -> s.bkOther
}

/** "L M M G V S D" nella lingua dell'app (1 = lunedì). */
private fun dayInitial(d: Int, lang: com.tennis.scoremanager.model.Lang): String {
    val cal = Calendar.getInstance().apply { set(Calendar.DAY_OF_WEEK, if (d == 7) Calendar.SUNDAY else d + 1) }
    return java.text.SimpleDateFormat("EEE", lang.locale).format(cal.time).take(3)
}

/** "Lun Mer Ven · 15:00-18:00 · Campo 1, Campo 2 · 1 set – 31 mag". */
private fun blockLine(s: Strings, b: com.tennis.scoremanager.data.CourtBlock, lang: com.tennis.scoremanager.model.Lang): String = listOf(
    blockReasonName(s, b.reason),
    if (b.weekdays.isEmpty()) s.blkEveryDay else b.weekdays.sorted().joinToString(" ") { dayInitial(it, lang) },
    if (b.startMin == 0 && b.endMin == 24 * 60) s.blkAllDay else "%02d:%02d-%02d:%02d".format(b.startMin / 60, b.startMin % 60, b.endMin / 60, b.endMin % 60),
    if (b.courts.isEmpty()) s.blkAllCourts else b.courts.joinToString(", "),
    if (CourtPlans.dayStart(b.from) == CourtPlans.dayStart(b.to)) shortDate(b.from, lang) else "${shortDate(b.from, lang)} – ${shortDate(b.to, lang)}",
).joinToString(" · ")

/** I periodi di campi non prenotabili: si toccano per cambiarli, se ne crea uno nuovo. */
@Composable
private fun BlocksDialog(c: MatchController, onEdit: (com.tennis.scoremanager.data.CourtBlock) -> Unit, onDone: () -> Unit) {
    val s = LocalStrings.current
    val lang = c.options.value.lang
    val plan by c.courtPlan.collectAsState()
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(s.blkTitle) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(s.blkHint, color = TsmColors.TextDim, fontSize = 13.sp)
                if (plan.blocks.isEmpty()) Text(s.blkNone, color = TsmColors.TextDim)
                for (b in plan.blocks.sortedBy { it.from }) {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(TsmColors.SurfaceHigh).clickable { onEdit(b) }.padding(10.dp)) {
                        Text(b.label.ifBlank { blockReasonName(s, b.reason) }, color = TsmColors.TextMain, fontWeight = FontWeight.Bold)
                        Text(blockLine(s, b, lang), color = TsmColors.TextDim, fontSize = 12.sp)
                    }
                }
                GhostButton(s.blkNew, Icons.Filled.Add, {
                    val today = CourtPlans.dayStart(System.currentTimeMillis())
                    onEdit(com.tennis.scoremanager.data.CourtBlock(CourtPlans.newId("k"), "", from = today, to = today))
                }, Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text(s.ok) } },
    )
}

/** Un periodo non prenotabile: nome, motivo (dal torneo prende nome e date), campi, date, giorni e orario. */
@Composable
private fun BlockEditDialog(c: MatchController, b0: com.tennis.scoremanager.data.CourtBlock, isNew: Boolean, onDone: () -> Unit) {
    val s = LocalStrings.current
    val context = LocalContext.current
    val lang = c.options.value.lang
    val courts = c.storage.clubCourts
    val tournaments by c.clubTournaments.collectAsState()
    var b by remember { mutableStateOf(b0) }
    fun time(min: Int, onPick: (Int) -> Unit) = TimePickerDialog(context, { _, h, m -> onPick(h * 60 + m) }, (min / 60).coerceAtMost(23), min % 60, true).show()
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(if (isNew) s.blkNew else b0.label.ifBlank { s.blkTitle }) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(b.label, { b = b.copy(label = it.take(40)) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(s.blkLabel) })
                val reasons = com.tennis.scoremanager.data.BlockReason.entries
                Segmented(reasons.map { SegOption(blockReasonName(s, it)) }, reasons.indexOf(b.reason), { b = b.copy(reason = reasons[it]) }, columns = 2)
                // Dal torneo: nome e date già pronti.
                if (b.reason == com.tennis.scoremanager.data.BlockReason.TOURNAMENT) for (t in tournaments.filter { it.start != null && it.champion == null }) {
                    GhostButton(s.blkFromTournament(t.name), Icons.Filled.CalendarMonth, {
                        b = b.copy(label = t.name, tournament = t.id, from = CourtPlans.dayStart(t.start!!), to = CourtPlans.dayStart(t.end ?: t.start))
                    }, Modifier.fillMaxWidth())
                }
                SwitchRow(Icons.Filled.Block, s.blkAllCourts, null, b.courts.isEmpty()) { all -> b = b.copy(courts = if (all) emptyList() else courts.take(1)) }
                if (b.courts.isNotEmpty()) for (ct in courts) SwitchRow(Icons.Filled.Block, ct, null, ct in b.courts) { on ->
                    val list = if (on) b.courts + ct else b.courts - ct
                    b = b.copy(courts = list.ifEmpty { listOf(ct) })
                }
                DateButton(s.blkFrom, b.from, lang) { d -> b = b.copy(from = CourtPlans.dayStart(d), to = maxOf(CourtPlans.dayStart(d), b.to)) }
                DateButton(s.blkTo, b.to, lang) { d -> b = b.copy(to = maxOf(CourtPlans.dayStart(d), b.from)) }
                Text(s.blkDays, color = TsmColors.TextDim, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (d in 1..7) {
                        val on = d in b.weekdays
                        Text(
                            dayInitial(d, lang), color = if (on) TsmColors.OnBall else TsmColors.TextMain, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (on) TsmColors.Ball else TsmColors.SurfaceHigh)
                                .clickable { b = b.copy(weekdays = if (on) b.weekdays - d else b.weekdays + d) }.padding(horizontal = 6.dp, vertical = 8.dp),
                        )
                    }
                }
                if (b.weekdays.isEmpty()) Text(s.blkEveryDay, color = TsmColors.TextDim, fontSize = 12.sp)
                val allDay = b.startMin == 0 && b.endMin == 24 * 60
                SwitchRow(Icons.Filled.Schedule, s.blkAllDay, null, allDay) { on -> b = if (on) b.copy(startMin = 0, endMin = 24 * 60) else b.copy(startMin = 15 * 60, endMin = 18 * 60) }
                if (!allDay) {
                    GhostButton("${s.blkStart}: %02d:%02d".format(b.startMin / 60, b.startMin % 60), Icons.Filled.Schedule, { time(b.startMin) { m -> b = b.copy(startMin = m, endMin = maxOf(b.endMin, m + 30)) } }, Modifier.fillMaxWidth())
                    GhostButton("${s.blkEnd}: %02d:%02d".format(b.endMin / 60, b.endMin % 60), Icons.Filled.Schedule, { time(b.endMin) { m -> b = b.copy(endMin = maxOf(m, b.startMin + 30)) } }, Modifier.fillMaxWidth())
                }
                if (!isNew) TextButton(onClick = { c.saveCourtBlock(b0, delete = true); onDone() }) { Text(s.blkDelete, color = TsmColors.Danger) }
            }
        },
        confirmButton = { TextButton(onClick = { c.saveCourtBlock(b.copy(label = b.label.trim())); onDone() }) { Text(s.ok) } },
        dismissButton = { TextButton(onClick = onDone) { Text(s.back) } },
    )
}
