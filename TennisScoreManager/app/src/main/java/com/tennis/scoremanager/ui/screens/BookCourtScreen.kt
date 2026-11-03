// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.SportsTennis
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.tennis.scoremanager.MatchController
import com.tennis.scoremanager.data.CellState
import com.tennis.scoremanager.data.ClubServerError
import com.tennis.scoremanager.data.CourtInfo
import com.tennis.scoremanager.data.Fees
import com.tennis.scoremanager.data.SrvBookIn
import com.tennis.scoremanager.data.SrvDay
import com.tennis.scoremanager.data.SrvMe
import com.tennis.scoremanager.data.SrvMyBooking
import com.tennis.scoremanager.data.SrvQuote
import com.tennis.scoremanager.ui.BigButton
import com.tennis.scoremanager.ui.GhostButton
import com.tennis.scoremanager.ui.LocalStrings
import com.tennis.scoremanager.ui.ScreenScaffold
import com.tennis.scoremanager.ui.SegOption
import com.tennis.scoremanager.ui.Segmented
import com.tennis.scoremanager.ui.Strings
import com.tennis.scoremanager.ui.SwitchRow
import com.tennis.scoremanager.ui.TsmColors
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val B_TIME_W = 52
private const val B_CELL_W = 104
private const val B_CELL_H = 46

/** L'errore del server detto al giocatore. */
fun bookErrorText(s: Strings, code: String): String = when (code) {
    "NET", "BAD_URL" -> s.pbErrNet
    "BAD_CODE" -> s.pbErrInvite
    "NEED_URL" -> s.pbErrUrl
    "TOO_MANY_TRIES" -> s.pbErrTries
    "UNAUTHORIZED" -> s.pbErrAuth
    "CLOSED" -> s.pbErrClosed
    "UNKNOWN_COURT" -> s.pbErrCourt
    "PAST" -> s.pbErrPast
    "TOO_SOON" -> s.pbErrSoon
    "TOO_FAR" -> s.pbErrFar
    "NOT_SLOT" -> s.pbErrSlot
    "TOO_LONG" -> s.pbErrLong
    "BLOCKED" -> s.pbErrBlocked
    "TAKEN" -> s.pbErrTaken
    "TOO_MANY" -> s.pbErrMany
    "TOO_LATE" -> s.pbErrLate
    "NOT_SYNCED" -> s.pbErrSynced
    "FREE_NOW" -> s.pbErrFreeNow
    "TOO_MANY_WAITS" -> s.pbErrManyWaits
    "FULL" -> s.pbErrFull
    "TOO_MANY_SEEKS" -> s.pbErrManySeeks
    else -> s.srvErrOther(code)
}

private fun errCode(t: Throwable): String = (t as? ClubServerError)?.code ?: "NET"

/** Orari e giorni nel fuso del circolo: chi prenota da un altro paese vede le ore del circolo. */
private class ClubTime(zone: String, private val locale: Locale) {
    private val z: ZoneId = runCatching { ZoneId.of(zone) }.getOrDefault(ZoneId.systemDefault())
    private val hm = DateTimeFormatter.ofPattern("HH:mm", locale)
    private val wd = DateTimeFormatter.ofPattern("EEE", locale)
    private val dm = DateTimeFormatter.ofPattern("d MMM", locale)
    private val full = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", locale)
    fun time(t: Long): String = hm.format(Instant.ofEpochMilli(t).atZone(z))
    /** Minuti dalla mezzanotte, ora del circolo. */
    fun minutes(t: Long): Int = Instant.ofEpochMilli(t).atZone(z).let { it.hour * 60 + it.minute }
    fun hhmm(min: Int): String = "%02d:%02d".format(min / 60, min % 60)
    fun whenText(t: Long): String = full.format(Instant.ofEpochMilli(t).atZone(z)).replaceFirstChar { it.titlecase(locale) }
    fun weekday(d: String): String = runCatching { wd.format(LocalDate.parse(d)) }.getOrDefault(d)
    fun dayMonth(d: String): String = runCatching { dm.format(LocalDate.parse(d)) }.getOrDefault(d)
}

/**
 * Prenota un campo, per il giocatore: si entra con l'invito della segreteria (QR, link o codice), poi si
 * scelgono il giorno e una casella libera; in alto le proprie prenotazioni, da disdire finché si è in tempo.
 */
@Composable
fun BookCourtScreen(c: MatchController) {
    val s = LocalStrings.current
    BackHandler { c.back() }
    val account by c.bookingAccount.collectAsState()
    ScreenScaffold(
        title = s.pbTitle,
        subtitle = account.club.takeIf { account.joined && it.isNotBlank() }?.let { club -> account.name.ifBlank { null }?.let { "$club · $it" } ?: club },
        icon = Icons.Filled.CalendarMonth,
        bottomBar = { GhostButton(s.back, Icons.AutoMirrored.Filled.ArrowBack, { c.back() }, Modifier.weight(1f)) },
    ) {
        if (!account.joined) JoinClub(c) else BookingView(c)
    }
}

@Composable
private fun JoinClub(c: MatchController) {
    val s = LocalStrings.current
    val scope = rememberCoroutineScope()
    var invite by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    fun join(text: String) {
        busy = true; msg = null
        scope.launch { msg = c.joinBooking(text, url)?.let { bookErrorText(s, it) }; busy = false }
    }
    val scanner = rememberLauncherForActivityResult(ScanContract()) { r -> r.contents?.let { invite = it; join(it) } }
    Text(s.pbJoinIntro, color = TsmColors.TextDim, fontSize = 14.sp)
    BigButton(s.pbScan, Icons.Filled.QrCodeScanner, {
        scanner.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE).setPrompt(s.pbScanPrompt).setBeepEnabled(false).setOrientationLocked(true))
    }, Modifier.fillMaxWidth(), enabled = !busy)
    OutlinedTextField(invite, { invite = it }, label = { Text(s.pbInvite) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
    // L'indirizzo serve solo se si scrive il codice a mano (nel link e nel QR c'è già).
    if (invite.isNotBlank() && !invite.contains("#i=")) OutlinedTextField(url, { url = it }, label = { Text(s.pbServer) }, singleLine = true,
        modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
    GhostButton(s.pbJoin, Icons.Filled.Login, { join(invite) }, Modifier.fillMaxWidth(), enabled = !busy && invite.isNotBlank())
    msg?.let { Text(it, color = TsmColors.Orange, fontSize = 14.sp) }
}

@Composable
private fun BookingView(c: MatchController) {
    val s = LocalStrings.current
    val context = LocalContext.current
    val locale = c.options.value.lang.locale
    val scope = rememberCoroutineScope()
    var me by remember { mutableStateOf<SrvMe?>(null) }
    var day by remember { mutableStateOf<SrvDay?>(null) }
    var date by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var booking by remember { mutableStateOf<Pair<CourtInfo, Long>?>(null) }
    var cancel by remember { mutableStateOf<SrvMyBooking?>(null) }
    var waitFor by remember { mutableStateOf<Pair<CourtInfo, Long>?>(null) }
    var seeks by remember { mutableStateOf<List<com.tennis.scoremanager.data.SrvSeek>>(emptyList()) }
    var newSeek by remember { mutableStateOf(false) }
    var leave by remember { mutableStateOf(false) }
    var tick by remember { mutableStateOf(0) }

    // Le proprie prenotazioni e il giorno scelto, ora e poi ogni minuto (le caselle degli altri cambiano).
    LaunchedEffect(tick) {
        while (true) {
            c.booking { it.me() }.onSuccess { m ->
                me = m
                c.booking { it.seeks() }.onSuccess { seeks = it }
                val d = date?.takeIf { it in m.days } ?: m.days.firstOrNull()
                date = d
                if (d != null) c.booking { it.day(d) }.onSuccess { day = it; error = null }.onFailure { error = bookErrorText(s, errCode(it)) }
            }.onFailure { error = bookErrorText(s, errCode(it)) }
            kotlinx.coroutines.delay(60_000)
        }
    }
    fun reload() { tick++ }
    // Tornando sulla schermata (dall'app o dal telefono) si riprende tutto: intanto altri possono aver prenotato.
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) { if (me != null) reload() }

    val m = me
    if (m == null) {
        Text(error ?: "…", color = if (error != null) TsmColors.Orange else TsmColors.TextDim)
        if (error != null) GhostButton(s.pbLeave, Icons.AutoMirrored.Filled.Logout, { leave = true }, Modifier.fillMaxWidth())
    } else {
        val ct = remember(m.timeZone, locale) { ClubTime(m.timeZone, locale) }
        Text(s.pbMine, color = TsmColors.TextDim, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        if (m.bookings.isEmpty()) Text(s.pbNone, color = TsmColors.TextDim)
        for (b in m.bookings) Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(TsmColors.Surface).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("${b.court} · ${ct.whenText(b.start)}–${ct.time(b.start + b.minutes * 60_000L)}", color = TsmColors.TextMain, fontWeight = FontWeight.Bold)
                Text(listOfNotNull(if (b.cents > 0) Fees.money(b.cents, locale) else s.pbFreePrice, s.pbLights.takeIf { b.lights }, s.pbHeating.takeIf { b.heating }).joinToString(" · "),
                    color = TsmColors.TextDim, fontSize = 13.sp)
            }
            TextButton(onClick = { cancel = b }) { Text(s.pbCancel, color = TsmColors.Danger) }
        }

        // Le liste d'attesa: aperte, o liberate (Prenota, prima di un altro).
        for (w in m.waits) Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(TsmColors.Surface)
                .border(1.dp, if (w.freedCourt != null) TsmColors.Ball else Color.Transparent, RoundedCornerShape(14.dp)).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(if (w.freedCourt != null) s.pbWaitFreed else s.pbWaitList, color = if (w.freedCourt != null) TsmColors.Ball else TsmColors.TextMain, fontWeight = FontWeight.Bold)
                Text("${w.freedCourt ?: w.court ?: s.pbWaitAnyCourt} · ${ct.whenText(w.start)}", color = TsmColors.TextDim, fontSize = 13.sp)
            }
            w.freedCourt?.let { fc -> TextButton(onClick = { booking = (day?.courts?.firstOrNull { it.name == fc } ?: CourtInfo(fc)) to w.start }) { Text(s.pbBookNow, color = TsmColors.Ball) } }
            TextButton(onClick = { scope.launch { c.booking { it.removeWait(w.id) }; reload() } }) { Text(s.pbWaitRemove, color = TsmColors.TextDim) }
        }

        Text(s.pbDay, color = TsmColors.TextDim, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            m.days.forEachIndexed { i, d ->
                val on = d == date
                Column(
                    Modifier.clip(RoundedCornerShape(12.dp)).background(if (on) TsmColors.Ball else TsmColors.Surface)
                        .border(1.dp, if (on) TsmColors.Ball else TsmColors.Outline, RoundedCornerShape(12.dp))
                        .clickable { date = d; day = null; scope.launch { c.booking { it.day(d) }.onSuccess { day = it }.onFailure { error = bookErrorText(s, errCode(it)) } } }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(if (i == 0) s.pbToday else ct.weekday(d), color = if (on) TsmColors.OnBall else TsmColors.TextMain, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(ct.dayMonth(d), color = if (on) TsmColors.OnBall else TsmColors.TextDim, fontSize = 12.sp)
                }
            }
        }
        if (!m.rules.open) Text(s.pbClosed, color = TsmColors.Orange, fontSize = 13.sp)
        error?.let { Text(it, color = TsmColors.Orange, fontSize = 13.sp) }
        day?.takeIf { it.date == date }?.let { d -> DayGrid(s, d, ct, onBusy = { court, start -> waitFor = court to start }) { court, start -> booking = court to start } }

        Text(listOfNotNull(s.pbRules(m.rules.daysAhead, m.rules.maxFuture), s.pbCancelRule(m.rules.cancelHours), m.payInfo.ifBlank { null }?.let { s.pbPay(it) }).joinToString(" "),
            color = TsmColors.TextDim, fontSize = 12.sp)
        // Cerco compagno: gli annunci aperti degli altri e i propri; al completo il campo si prenota da solo.
        Text(s.pbSeekTitle, color = TsmColors.TextDim, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        if (seeks.isEmpty()) Text(s.pbSeekNone, color = TsmColors.TextDim, fontSize = 13.sp)
        for (k in seeks) Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(TsmColors.Surface)
                .border(1.dp, if (k.mine) TsmColors.Ball else Color.Transparent, RoundedCornerShape(14.dp)).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text("${if (k.mine) s.pbSeekYours else k.owner} · ${ct.weekday(k.date)} ${ct.dayMonth(k.date)} · ${ct.hhmm(k.fromMin)}–${ct.hhmm(k.toMin)}", color = TsmColors.TextMain, fontWeight = FontWeight.Bold)
            Text(listOf(if (k.doubles) s.pbSeekDoubles else s.pbSeekSingles, k.level, k.note).filter { it.isNotBlank() }.joinToString(" · "), color = TsmColors.TextDim, fontSize = 13.sp)
            Text(s.pbSeekWho(k.players.joinToString(", ")) + if (k.need > 0) " · " + s.pbSeekNeed(k.need) else "", color = TsmColors.TextDim, fontSize = 13.sp)
            if (k.bookedCourt != null && k.bookedStart != null) Text(s.pbSeekBooked("${k.bookedCourt} · ${ct.whenText(k.bookedStart!!)}"), color = TsmColors.Ball, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            else if (k.noCourt) Text(s.pbSeekNoCourt, color = TsmColors.Orange, fontSize = 13.sp)
            Row {
                fun act(f: (com.tennis.scoremanager.data.ClubServerClient) -> Any, done: String? = null) {
                    scope.launch {
                        c.booking { f(it) }.onSuccess { done?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() } }
                            .onFailure { Toast.makeText(context, bookErrorText(s, errCode(it)), Toast.LENGTH_LONG).show() }
                        reload()
                    }
                }
                if (!k.mine && !k.joined && k.need > 0) TextButton(onClick = { act({ it.joinSeek(k.id) }, s.pbSeekJoined) }) { Text(s.pbSeekJoin, color = TsmColors.Ball) }
                if (k.joined && k.bookedCourt == null) TextButton(onClick = { act({ it.leaveSeek(k.id) }) }) { Text(s.pbSeekLeave, color = TsmColors.TextDim) }
                if (k.mine) TextButton(onClick = { act({ it.removeSeek(k.id) }) }) { Text(s.pbSeekRemove, color = TsmColors.TextDim) }
            }
        }
        GhostButton(s.pbSeekNew, Icons.Filled.Groups, { newSeek = true }, Modifier.fillMaxWidth(), enabled = m.rules.open)

        // Promemoria e avvisi sul telefono (anche ad app chiusa).
        val acc by c.bookingAccount.collectAsState()
        val notifPerm = rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }
        SwitchRow(Icons.Filled.NotificationsActive, s.pbReminders, s.pbRemindersHint(m.rules.remindHours), acc.reminders) { on ->
            c.setBookingReminders(on)
            if (on && !com.tennis.scoremanager.service.OnlineAlerts.allowed(context) && android.os.Build.VERSION.SDK_INT >= 33) notifPerm.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        GhostButton(s.pbLeave, Icons.AutoMirrored.Filled.Logout, { leave = true }, Modifier.fillMaxWidth())

        booking?.let { (court, start) ->
            BookDialog(c, m, day?.slotMinutes ?: 60, court, start, ct) { done ->
                booking = null
                if (done) { Toast.makeText(context, s.pbBooked, Toast.LENGTH_SHORT).show(); reload() }
            }
        }
        if (newSeek) SeekDialog(c, m, day, date, ct) { done ->
            newSeek = false
            if (done) { Toast.makeText(context, s.pbSeekPublished, Toast.LENGTH_SHORT).show(); reload() }
        }
        waitFor?.let { (court, start) ->
            var any by remember(court.name, start) { mutableStateOf(false) }
            var err by remember(court.name, start) { mutableStateOf<String?>(null) }
            var busy by remember(court.name, start) { mutableStateOf(false) }
            AlertDialog(
                onDismissRequest = { waitFor = null },
                title = { Text(court.name) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(ct.whenText(start), color = TsmColors.TextMain, fontWeight = FontWeight.Bold)
                        Text(s.pbWaitText, color = TsmColors.TextDim, fontSize = 13.sp)
                        SwitchRow(Icons.Filled.SportsTennis, s.pbWaitAny, null, any) { any = it }
                        err?.let { Text(it, color = TsmColors.Orange, fontSize = 13.sp) }
                    }
                },
                confirmButton = {
                    TextButton(enabled = !busy, onClick = {
                        busy = true
                        scope.launch {
                            c.booking { it.addWait(com.tennis.scoremanager.data.SrvWaitIn(if (any) null else court.name, start, day?.slotMinutes ?: 60)) }
                                .onSuccess { waitFor = null; Toast.makeText(context, s.pbWaitAdded, Toast.LENGTH_SHORT).show(); reload() }
                                .onFailure { e ->
                                    val code = errCode(e)
                                    if (code == "FREE_NOW") { waitFor = null; Toast.makeText(context, s.pbErrFreeNow, Toast.LENGTH_LONG).show(); reload() }
                                    else err = bookErrorText(s, code)
                                }
                            busy = false
                        }
                    }) { Text(s.pbWaitAsk) }
                },
                dismissButton = { TextButton(onClick = { waitFor = null }) { Text(s.back) } },
            )
        }
        cancel?.let { b ->
            var err by remember(b.id) { mutableStateOf(if (b.canCancel) null else s.pbCallOffice) }
            var busy by remember(b.id) { mutableStateOf(false) }
            AlertDialog(
                onDismissRequest = { cancel = null },
                title = { Text(s.pbCancelAsk) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${b.court} · ${ct.whenText(b.start)}", color = TsmColors.TextMain, fontWeight = FontWeight.Bold)
                        err?.let { Text(it, color = TsmColors.Orange, fontSize = 13.sp) }
                    }
                },
                confirmButton = {
                    TextButton(enabled = b.canCancel && !busy, onClick = {
                        busy = true
                        scope.launch {
                            c.booking { it.cancelMine(b.id) }
                                .onSuccess { c.noteMyCancel(b.id); cancel = null; Toast.makeText(context, s.pbCancelled, Toast.LENGTH_SHORT).show(); reload() }
                                .onFailure { err = bookErrorText(s, errCode(it)) }
                            busy = false
                        }
                    }) { Text(s.pbCancel, color = if (b.canCancel) TsmColors.Danger else TsmColors.TextDim) }
                },
                dismissButton = { TextButton(onClick = { cancel = null }) { Text(s.back) } },
            )
        }
    }
    if (leave) AlertDialog(
        onDismissRequest = { leave = false },
        text = { Text(s.pbLeaveAsk) },
        confirmButton = { TextButton(onClick = { leave = false; c.leaveBooking() }) { Text(s.pbLeave, color = TsmColors.Danger) } },
        dismissButton = { TextButton(onClick = { leave = false }) { Text(s.back) } },
    )
}

/** Il giorno: un campo per colonna, un'ora per riga; si tocca una casella libera per prenotarla. */
@Composable
private fun DayGrid(s: Strings, d: SrvDay, ct: ClubTime, onBusy: (CourtInfo, Long) -> Unit, onFree: (CourtInfo, Long) -> Unit) {
    val starts = d.cells.map { it.start }.distinct().sorted()
    val cells = d.cells.associateBy { it.court to it.start }
    Column(Modifier.horizontalScroll(rememberScrollState())) {
        Row {
            Box(Modifier.width(B_TIME_W.dp))
            for (court in d.courts) Column(Modifier.width(B_CELL_W.dp).padding(horizontal = 4.dp, vertical = 6.dp)) {
                Text(court.name, color = TsmColors.TextMain, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(courtTraits(s, court), color = TsmColors.TextDim, fontSize = 11.sp, maxLines = 2, lineHeight = 13.sp)
            }
        }
        for (start in starts) Row(verticalAlignment = Alignment.CenterVertically) {
            Text(ct.time(start), color = TsmColors.TextDim, fontSize = 12.sp, modifier = Modifier.width(B_TIME_W.dp))
            for (court in d.courts) {
                val cell = cells[court.name to start]
                val state = cell?.state ?: CellState.PAST
                val (bg, fg, label) = when (state) {
                    CellState.FREE -> Triple(TsmColors.Ball.copy(alpha = 0.16f), TsmColors.Ball, s.pbFree)
                    CellState.MINE -> Triple(TsmColors.Player1, TsmColors.OnPlayer1, s.pbMineCell)
                    CellState.BUSY -> if (cell?.waiting == true) Triple(TsmColors.SurfaceHigh, TsmColors.Ball, s.pbWaiting) else Triple(TsmColors.SurfaceHigh, TsmColors.TextDim, s.pbBusy)
                    CellState.BLOCKED -> Triple(TsmColors.Surface, TsmColors.TextDim, s.pbBlocked)
                    CellState.PAST -> Triple(Color.Transparent, TsmColors.Outline, "")
                }
                val canBook = state == CellState.FREE && d.open
                // Occupata: ci si mette in lista d'attesa (se non ci si è già).
                val canWait = state == CellState.BUSY && d.open && cell?.waiting != true
                Box(
                    Modifier.width(B_CELL_W.dp).height(B_CELL_H.dp).padding(1.dp).clip(RoundedCornerShape(6.dp)).background(bg)
                        .border(1.dp, if (cell?.waiting == true) TsmColors.Ball else TsmColors.Outline.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                        .then(if (canBook) Modifier.clickable { onFree(court, start) } else if (canWait) Modifier.clickable { onBusy(court, start) } else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, color = fg, fontSize = 12.sp, fontWeight = if (state == CellState.FREE || state == CellState.MINE) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center, maxLines = 2, lineHeight = 13.sp)
                }
            }
        }
    }
}

/** La prenotazione di una casella: durata, riscaldamento, il prezzo prima di confermare. [onDone] true = prenotata. */
@Composable
private fun BookDialog(c: MatchController, me: SrvMe, slot: Int, court: CourtInfo, start: Long, ct: ClubTime, onDone: (Boolean) -> Unit) {
    val s = LocalStrings.current
    val locale = c.options.value.lang.locale
    val scope = rememberCoroutineScope()
    val options = (slot..maxOf(me.rules.maxMinutes, slot) step slot).toList()
    var minutes by remember { mutableStateOf(options.first()) }
    var heating by remember { mutableStateOf(false) }
    var quote by remember { mutableStateOf<SrvQuote?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(minutes, heating) {
        quote = null; err = null
        c.booking { it.quote(court.name, start, minutes, heating) }
            .onSuccess { q -> quote = q; err = q.problem?.let { bookErrorText(s, it) } }
            .onFailure { err = bookErrorText(s, errCode(it)) }
    }
    AlertDialog(
        onDismissRequest = { onDone(false) },
        title = { Text(court.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(ct.whenText(start), color = TsmColors.TextMain, fontWeight = FontWeight.Bold)
                Text(courtTraits(s, court), color = TsmColors.TextDim, fontSize = 13.sp)
                if (options.size > 1) {
                    Text(s.pbDuration, color = TsmColors.TextDim, fontSize = 13.sp)
                    Segmented(options.map { SegOption(s.bkMinutes(it)) }, options.indexOf(minutes), { minutes = options[it] }, columns = minOf(4, options.size))
                }
                if (court.heating) SwitchRow(Icons.Filled.Thermostat, s.pbHeating, null, heating) { heating = it }
                Text(s.pbPrice, color = TsmColors.TextDim, fontSize = 13.sp)
                Text(quote?.let { q -> (if (q.cents > 0) Fees.money(q.cents, locale) else s.pbFreePrice) + if (q.lights) " · " + s.pbLights else "" } ?: "…",
                    color = TsmColors.Ball, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                me.payInfo.ifBlank { null }?.let { Text(s.pbPay(it), color = TsmColors.TextDim, fontSize = 13.sp) }
                err?.let { Text(it, color = TsmColors.Orange, fontSize = 13.sp) }
            }
        },
        confirmButton = {
            TextButton(enabled = quote != null && quote?.problem == null && !busy, onClick = {
                busy = true
                scope.launch {
                    c.booking { it.book(SrvBookIn(court.name, start, minutes, heating)) }
                        .onSuccess { onDone(true) }
                        .onFailure { err = bookErrorText(s, errCode(it)) }
                    busy = false
                }
            }) { Text(s.pbConfirm) }
        },
        dismissButton = { TextButton(onClick = { onDone(false) }) { Text(s.back) } },
    )
}

/** L'annuncio di Cerco compagno: giorno, fascia (orari del planning), singolo o doppio, livello e nota. */
@Composable
private fun SeekDialog(c: MatchController, me: SrvMe, day: SrvDay?, date: String?, ct: ClubTime, onDone: (Boolean) -> Unit) {
    val s = LocalStrings.current
    val scope = rememberCoroutineScope()
    val step = day?.slotMinutes ?: 60
    val mins = day?.cells?.map { ct.minutes(it.start) }?.distinct()?.sorted()?.ifEmpty { null } ?: (8 until 22).map { it * 60 }
    var d by remember { mutableStateOf(date ?: me.days.first()) }
    var from by remember { mutableStateOf(mins.getOrElse(1) { mins.first() }) }
    var to by remember { mutableStateOf(minOf(from + 180, mins.last() + step)) }
    var doubles by remember { mutableStateOf(false) }
    var level by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    @Composable fun stepper(label: String, v: Int, onChange: (Int) -> Unit) = Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = TsmColors.TextMain, modifier = Modifier.weight(1f))
        androidx.compose.material3.IconButton(onClick = { onChange(v - step) }) { androidx.compose.material3.Icon(Icons.Filled.Remove, null) }
        Text(ct.hhmm(v), color = TsmColors.TextMain, fontWeight = FontWeight.Bold)
        androidx.compose.material3.IconButton(onClick = { onChange(v + step) }) { androidx.compose.material3.Icon(Icons.Filled.Add, null) }
    }
    AlertDialog(
        onDismissRequest = { onDone(false) },
        title = { Text(s.pbSeekTitle) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(s.pbSeekHint, color = TsmColors.TextDim, fontSize = 13.sp)
                Text(s.pbSeekDay, color = TsmColors.TextDim, fontSize = 13.sp)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (x in me.days) {
                        val on = x == d
                        Text("${ct.weekday(x)} ${ct.dayMonth(x)}", color = if (on) TsmColors.OnBall else TsmColors.TextMain, fontSize = 13.sp,
                            modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(if (on) TsmColors.Ball else TsmColors.SurfaceHigh).clickable { d = x }.padding(horizontal = 10.dp, vertical = 6.dp))
                    }
                }
                stepper(s.pbSeekFrom, from) { v -> from = v.coerceIn(mins.first(), mins.last()); if (to < from + step) to = from + step }
                stepper(s.pbSeekTo, to) { v -> to = v.coerceIn(from + step, mins.last() + step) }
                Segmented(listOf(SegOption(s.pbSeekSingles), SegOption(s.pbSeekDoubles)), if (doubles) 1 else 0, { doubles = it == 1 })
                OutlinedTextField(level, { level = it.take(30) }, label = { Text(s.pbSeekLevel) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(note, { note = it.take(140) }, label = { Text(s.pbSeekNote) }, modifier = Modifier.fillMaxWidth())
                err?.let { Text(it, color = TsmColors.Orange, fontSize = 13.sp) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    c.booking { it.addSeek(com.tennis.scoremanager.data.SrvSeekIn(d, from, to, doubles, level, note)) }
                        .onSuccess { onDone(true) }.onFailure { err = bookErrorText(s, errCode(it)) }
                    busy = false
                }
            }) { Text(s.pbSeekPublish) }
        },
        dismissButton = { TextButton(onClick = { onDone(false) }) { Text(s.back) } },
    )
}
