// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.ui.screens

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SportsTennis
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Refresh
import com.tennis.scoremanager.MatchController
import com.tennis.scoremanager.data.ClubServerError
import com.tennis.scoremanager.data.ClubServers
import com.tennis.scoremanager.data.PlayerProfile
import com.tennis.scoremanager.data.Reports
import com.tennis.scoremanager.data.SrvInvite
import com.tennis.scoremanager.data.SrvTunnel
import com.tennis.scoremanager.ui.GhostButton
import com.tennis.scoremanager.ui.LocalStrings
import com.tennis.scoremanager.ui.QrCode
import com.tennis.scoremanager.ui.SectionCard
import com.tennis.scoremanager.ui.Strings
import com.tennis.scoremanager.ui.SwitchRow
import com.tennis.scoremanager.ui.TsmColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * L'accesso da internet senza toccare il router: la segreteria lo accende da qui e, le prime volte, apre i link di
 * Tailscale. Quando è pronto l'indirizzo da internet si compila da solo.
 */
@Composable
private fun TunnelSection(c: MatchController, publicUrl: String, onPublicUrl: (String) -> Unit) {
    val s = LocalStrings.current
    val uri = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var t by remember { mutableStateOf<SrvTunnel?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var kick by remember { mutableStateOf(0) }
    LaunchedEffect(kick) {
        while (true) {
            c.serverTunnel().onSuccess { t = it; err = null }.onFailure { err = (it as? ClubServerError)?.code ?: "NET" }
            val state = t?.state
            delay(if (state == "WORKING" || state == "LOGIN" || state == "ENABLE") 3_000 else 30_000)
        }
    }
    val done = t?.takeIf { it.state == "ON" }?.url.orEmpty()
    LaunchedEffect(done) { if (done.isNotEmpty() && done != publicUrl) onPublicUrl(done) }
    fun set(on: Boolean) {
        busy = true
        scope.launch {
            c.serverTunnel(on).onSuccess { t = it; err = null }.onFailure { err = (it as? ClubServerError)?.code ?: "NET" }
            if (!on && publicUrl.endsWith(".ts.net")) onPublicUrl("")
            busy = false
            kick++
        }
    }
    Text(s.srvTunnelTitle, fontWeight = FontWeight.Bold)
    val now = t
    when {
        err == "NOT_FOUND" || now?.available == false -> Text(s.srvTunnelUpdate, color = TsmColors.TextDim, fontSize = 13.sp)
        err != null -> Text(srvErrorText(s, err!!), color = TsmColors.Orange, fontSize = 13.sp)
        now == null -> Text(s.srvTunnelHint, color = TsmColors.TextDim, fontSize = 13.sp)
        now.state == "ON" -> {
            Text(s.srvTunnelReady(now.url), fontSize = 13.sp)
            Text(s.srvTunnelKey, color = TsmColors.TextDim, fontSize = 12.sp)
            GhostButton(s.srvTunnelOff, Icons.Filled.Public, { set(false) }, Modifier.fillMaxWidth(), enabled = !busy)
        }
        now.state == "LOGIN" || now.state == "ENABLE" -> {
            Text(if (now.state == "LOGIN") s.srvTunnelLogin else s.srvTunnelEnable, fontSize = 13.sp)
            GhostButton(s.srvTunnelOpen, Icons.Filled.Public, { uri.openUri(now.link) }, Modifier.fillMaxWidth(), enabled = now.link.isNotEmpty())
            GhostButton(s.srvTunnelOff, Icons.Filled.Public, { set(false) }, Modifier.fillMaxWidth(), enabled = !busy)
        }
        now.state == "WORKING" -> {
            Text(s.srvTunnelWorking, color = TsmColors.TextDim, fontSize = 13.sp)
            GhostButton(s.srvTunnelOff, Icons.Filled.Public, { set(false) }, Modifier.fillMaxWidth(), enabled = !busy)
        }
        else -> {
            Text(s.srvTunnelHint, color = TsmColors.TextDim, fontSize = 13.sp)
            if (now.state == "ERROR") Text(s.srvTunnelFail, color = TsmColors.Orange, fontSize = 13.sp)
            GhostButton(s.srvTunnelOn, Icons.Filled.Public, { set(true) }, Modifier.fillMaxWidth(), enabled = !busy)
        }
    }
}

/** L'errore del server detto in parole. */
fun srvErrorText(s: Strings, code: String): String = when (code) {
    "NET", "BAD_URL" -> s.srvErrNet
    "BAD_CODE" -> s.srvErrCode
    "UNAUTHORIZED" -> s.srvErrAuth
    "NOT_TSM" -> s.srvErrNotTsm
    "PAIR_LOCAL_ONLY" -> s.srvErrPairLocal
    else -> s.srvErrOther(code)
}

private fun whenText(t: Long, lang: com.tennis.scoremanager.model.Lang): String = shortDate(t, lang) + " " + Reports.time(t, lang)

/**
 * Nel planning, sotto i tasti: lo stato del server (solo se abbinato), che si tocca per aprirne le impostazioni.
 * Mentre il planning è aperto, ogni minuto si manda e si riprende tutto, così le prenotazioni online arrivano da sole.
 */
@Composable
fun ServerStatusLine(c: MatchController, onClick: () -> Unit) {
    val s = LocalStrings.current
    val conf by c.clubServer.collectAsState()
    val lang = c.options.value.lang
    if (!conf.paired) return
    LaunchedEffect(conf.url) {
        while (true) { c.syncServerNow(); kotlinx.coroutines.delay(60_000) }
    }
    val err = conf.error
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(if (err == null) Icons.Filled.Cloud else Icons.Filled.CloudOff, null, tint = if (err == null) TsmColors.Ball else TsmColors.Orange)
        Text(
            if (err != null) srvErrorText(s, err) else if (conf.lastSync > 0) s.srvLastSync(whenText(conf.lastSync, lang)) else s.srvNeverSynced,
            color = if (err == null) TsmColors.TextDim else TsmColors.Orange, fontSize = 12.sp,
        )
    }
}

/** Una riga dei controlli: ✓ verde se va, ✗ arancione con cosa fare, grigio se è facoltativo o si sta controllando. */
@Composable
private fun CheckRow(ok: Boolean?, label: String, hint: String?, optional: Boolean = false) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(
            when (ok) { true -> Icons.Filled.CheckCircle; false -> if (optional) Icons.Filled.RemoveCircleOutline else Icons.Filled.Cancel; null -> Icons.Filled.HourglassEmpty },
            null, tint = when (ok) { true -> TsmColors.Ok; false -> if (optional) TsmColors.TextDim else TsmColors.Orange; null -> TsmColors.TextDim },
        )
        Column(Modifier.weight(1f)) {
            Text(label, color = TsmColors.TextMain, fontSize = 14.sp)
            if (hint != null) Text(hint, color = if (ok == false && !optional) TsmColors.Orange else TsmColors.TextDim, fontSize = 12.sp)
        }
    }
}

/**
 * I controlli del server, uno per riga: abbinato, risponde in rete e da internet, planning inviato, prenotazioni
 * aperte, campi, indirizzo da internet, avvisi. Si ricontrollano da soli ogni 30 secondi (e col tasto).
 */
@Composable
private fun ServerChecks(c: MatchController, rules: com.tennis.scoremanager.data.BookingRules, publicUrl: String, notifOk: Boolean) {
    val s = LocalStrings.current
    val conf by c.clubServer.collectAsState()
    var local by remember { mutableStateOf<Boolean?>(null) }
    var remote by remember { mutableStateOf<Boolean?>(null) }
    var round by remember { mutableStateOf(0) }
    LaunchedEffect(round, publicUrl) {
        while (true) {
            local = null; remote = null
            local = c.serverAnswers(conf.url)
            remote = if (publicUrl.isBlank()) false else c.serverAnswers(publicUrl)
            delay(30_000)
        }
    }
    SectionCard(s.srvChkTitle, Icons.Filled.Checklist) {
        // Abbinato sì, ma il server non riconosce più il telefono (es. chiavetta rifatta): va abbinato di nuovo.
        val known = conf.paired && conf.error != "UNAUTHORIZED"
        CheckRow(known, s.srvChkPaired, if (!known) s.srvErrAuth else null)
        CheckRow(local, s.srvChkLocal, if (local == false) s.srvChkLocalNo else if (local == null) s.srvChkChecking else null)
        val synced = conf.lastSync > 0 && conf.error == null
        CheckRow(synced, s.srvChkSync, if (!synced) (conf.error?.let { srvErrorText(s, it) } ?: s.srvChkSyncNo) else null)
        CheckRow(rules.open, s.srvChkOpen, if (!rules.open) s.srvChkOpenNo else null)
        CheckRow(true, s.srvChkCourts, rules.courts.ifEmpty { null }?.joinToString(", ") ?: s.srvChkCourtsAll)
        CheckRow(publicUrl.isNotBlank(), s.srvChkPublic, if (publicUrl.isBlank()) s.srvChkPublicNo else publicUrl)
        if (publicUrl.isNotBlank()) CheckRow(remote, s.srvChkRemote, if (remote == false) s.srvChkRemoteNo else if (remote == null) s.srvChkChecking else null)
        val alerts = conf.alerts && notifOk
        CheckRow(alerts, s.srvChkAlerts, if (!alerts) s.srvChkAlertsNo else null, optional = true)
        GhostButton(s.srvChkAgain, Icons.Filled.Refresh, { round++ }, Modifier.fillMaxWidth())
    }
}

/** Abbinare il server, oppure (abbinato) lo stato, le regole per i giocatori e lo scollegamento. */
@Composable
fun ClubServerDialog(c: MatchController, onDone: () -> Unit) {
    val s = LocalStrings.current
    val lang = c.options.value.lang
    val conf by c.clubServer.collectAsState()
    val courts by c.clubCourts.collectAsState()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    var url by remember { mutableStateOf(conf.url) }
    var code by remember { mutableStateOf("") }
    var rules by remember(conf.paired) { mutableStateOf(conf.rules) }
    var pay by remember(conf.paired) { mutableStateOf(conf.payInfo) }
    var publicUrl by remember(conf.paired) { mutableStateOf(conf.publicUrl) }
    var unpair by remember { mutableStateOf(false) }
    var chat by remember { mutableStateOf<Pair<String, String>?>(null) }
    var rooms by remember { mutableStateOf<List<com.tennis.scoremanager.data.SrvChatRoom>?>(null) }
    val context = LocalContext.current
    var notifOk by remember { mutableStateOf(com.tennis.scoremanager.service.OnlineAlerts.allowed(context)) }
    val notifPerm = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { notifOk = it }

    @Composable fun stepper(label: String, v: Int, min: Int, max: Int, step: Int = 1, fmt: (Int) -> String = { it.toString() }, onChange: (Int) -> Unit) =
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = TsmColors.TextMain, fontSize = 14.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = { onChange((v - step).coerceAtLeast(min)) }) { Icon(Icons.Filled.Remove, null) }
            Text(fmt(v), color = TsmColors.TextMain, fontWeight = FontWeight.Bold)
            IconButton(onClick = { onChange((v + step).coerceAtMost(max)) }) { Icon(Icons.Filled.Add, null) }
        }

    fun save() { if (conf.paired) c.saveServerRules(rules, pay, publicUrl) }

    AlertDialog(
        onDismissRequest = { save(); onDone() },
        title = { Text(s.srvTitle) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!conf.paired) {
                    Text(s.srvIntro, color = TsmColors.TextDim, fontSize = 13.sp)
                    OutlinedTextField(url, { url = it }, label = { Text(s.srvAddress) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), supportingText = { Text(s.srvAddressHint, fontSize = 11.sp) })
                    OutlinedTextField(code, { code = it }, label = { Text(s.srvSetupCode) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters), supportingText = { Text(s.srvSetupHint, fontSize = 11.sp) })
                    GhostButton(s.srvPair, Icons.Filled.Link, {
                        busy = true; msg = null
                        scope.launch { msg = c.pairServer(url, code)?.let { srvErrorText(s, it) }; busy = false }
                    }, Modifier.fillMaxWidth(), enabled = !busy && url.isNotBlank() && code.isNotBlank())
                } else {
                    Text(s.srvConnected(conf.club.ifBlank { conf.url }), color = TsmColors.Ball, fontWeight = FontWeight.Bold)
                    ServerChecks(c, rules, publicUrl, notifOk)
                    Text(conf.url, color = TsmColors.TextDim, fontSize = 12.sp)
                    val err = conf.error
                    Text(
                        if (err != null) srvErrorText(s, err) else if (conf.lastSync > 0) s.srvLastSync(whenText(conf.lastSync, lang)) else s.srvNeverSynced,
                        color = if (err == null) TsmColors.TextDim else TsmColors.Orange, fontSize = 13.sp,
                    )
                    GhostButton(s.srvSyncNow, Icons.Filled.Sync, {
                        busy = true; msg = null
                        scope.launch { c.saveServerRules(rules, pay, publicUrl); msg = c.syncServerNow()?.let { srvErrorText(s, it) } ?: s.srvSynced; busy = false }
                    }, Modifier.fillMaxWidth(), enabled = !busy)
                    OutlinedTextField(publicUrl, { publicUrl = it }, label = { Text(s.srvPublicUrl) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), supportingText = { Text(s.srvPublicUrlHint, fontSize = 11.sp) })
                    TunnelSection(c, publicUrl) { publicUrl = it; c.saveServerRules(rules, pay, it) }
                    SwitchRow(Icons.Filled.NotificationsActive, s.srvAlerts, s.srvAlertsHint, conf.alerts) { on ->
                        c.setServerAlerts(on)
                        if (on && !com.tennis.scoremanager.service.OnlineAlerts.allowed(context) && android.os.Build.VERSION.SDK_INT >= 33)
                            notifPerm.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                    if (conf.alerts && !notifOk) Text(s.srvAlertsOff, color = TsmColors.Orange, fontSize = 12.sp)
                    Text(s.srvPlayers(conf.players.size), color = TsmColors.TextMain, fontSize = 14.sp)
                    Text(s.srvInviteHow, color = TsmColors.TextDim, fontSize = 12.sp)
                    // La bacheca (scrive solo la segreteria) e le chat degli annunci, da controllare.
                    GhostButton(s.srvBoard, Icons.Filled.Campaign, { chat = "board" to s.srvBoard }, Modifier.fillMaxWidth())
                    GhostButton(s.srvChats, Icons.Filled.Forum, {
                        scope.launch { c.serverAdmin { it.adminChats() }.onSuccess { rooms = it }.onFailure { e -> msg = srvErrorText(s, (e as? com.tennis.scoremanager.data.ClubServerError)?.code ?: "NET") } }
                    }, Modifier.fillMaxWidth())

                    SwitchRow(Icons.Filled.SportsTennis, s.srvOpen, s.srvOpenHint, rules.open) { rules = rules.copy(open = it) }
                    stepper(s.srvDaysAhead, rules.daysAhead, 0, 60) { rules = rules.copy(daysAhead = it) }
                    stepper(s.srvMaxFuture, rules.maxFuture, 1, 10) { rules = rules.copy(maxFuture = it) }
                    stepper(s.srvMaxMinutes, rules.maxMinutes, 30, 240, 30, { s.bkMinutes(it) }) { rules = rules.copy(maxMinutes = it) }
                    stepper(s.srvCancelHours, rules.cancelHours, 0, 72) { rules = rules.copy(cancelHours = it) }
                    stepper(s.srvNotice, rules.noticeMinutes, 0, 240, 15) { rules = rules.copy(noticeMinutes = it) }
                    stepper(s.srvRemind, rules.remindHours, 0, 24) { rules = rules.copy(remindHours = it) }
                    if (courts.size > 1) {
                        Text(s.srvCourts, color = TsmColors.TextDim, fontSize = 13.sp)
                        val on = rules.courts.ifEmpty { courts }
                        for (ct in courts) SwitchRow(Icons.Filled.SportsTennis, ct, null, ct in on) { yes ->
                            val list = (if (yes) on + ct else on - ct).distinct().ifEmpty { listOf(ct) }
                            // Tutti i campi = lista vuota, così un campo nuovo è subito prenotabile.
                            rules = rules.copy(courts = if (courts.all { it in list }) emptyList() else courts.filter { it in list })
                        }
                    }
                    OutlinedTextField(pay, { pay = it }, label = { Text(s.srvPayInfo) }, modifier = Modifier.fillMaxWidth())
                    GhostButton(s.srvUnpair, Icons.Filled.LinkOff, { unpair = true }, Modifier.fillMaxWidth(), enabled = !busy)
                }
                msg?.let { Text(it, color = if (it == s.srvSynced) TsmColors.Ok else TsmColors.Orange, fontSize = 13.sp) }
            }
        },
        confirmButton = { TextButton(onClick = { save(); onDone() }) { Text(s.ok) } },
    )
    rooms?.let { list ->
        AlertDialog(
            onDismissRequest = { rooms = null },
            title = { Text(s.srvChats, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(s.srvChatsHint, color = TsmColors.TextDim, fontSize = 12.sp)
                    val chats = list.filter { it.room != "board" }
                    if (chats.isEmpty()) Text(s.srvChatsNone, color = TsmColors.TextDim)
                    for (r in chats) {
                        val t = if (r.room == "club") s.pbClubChat else r.title
                        GhostButton("$t (${r.count})", Icons.Filled.Forum, { chat = r.room to t }, Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = { TextButton(onClick = { rooms = null }) { Text(s.ok) } },
        )
    }
    chat?.let { (room, title) ->
        val board = room == "board"
        com.tennis.scoremanager.ui.ChatDialog(
            title = title,
            load = { c.serverAdmin { it.adminChat(room) } },
            send = if (board) { t -> c.serverAdmin { it.postBoard(t) }.exceptionOrNull()?.let { (it as? com.tennis.scoremanager.data.ClubServerError)?.code ?: "NET" } } else null,
            delete = { id -> c.serverAdmin { it.deleteChat(id) }.exceptionOrNull()?.let { (it as? com.tennis.scoremanager.data.ClubServerError)?.code ?: "NET" } },
            hint = if (board) s.srvBoardHint else s.srvChatsHint,
            errText = { srvErrorText(s, it) },
        ) { chat = null }
    }
    if (unpair) AlertDialog(
        onDismissRequest = { unpair = false },
        text = { Text(s.srvUnpairAsk) },
        confirmButton = { TextButton(onClick = { unpair = false; c.unpairServer() }) { Text(s.srvUnpair, color = TsmColors.Danger) } },
        dismissButton = { TextButton(onClick = { unpair = false }) { Text(s.back) } },
    )
}

/** Nella scheda del giocatore, se il circolo ha il server: l'invito con il QR, o chi prenota già online. */
@Composable
fun PlayerOnlineSection(c: MatchController, p: PlayerProfile) {
    val s = LocalStrings.current
    val lang = c.options.value.lang
    val conf by c.clubServer.collectAsState()
    if (!conf.paired) return
    val scope = rememberCoroutineScope()
    var invite by remember { mutableStateOf<SrvInvite?>(null) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    val linked = conf.players.firstOrNull { it.id == p.id }
    SectionCard(s.srvTitle, Icons.Filled.Cloud) {
        linked?.let { Text(s.srvLinked(whenText(maxOf(it.lastSeen, it.since), lang)), color = TsmColors.Ok, fontSize = 13.sp) }
        GhostButton(s.srvInvite, Icons.Filled.QrCode2, {
            busy = true; msg = null
            scope.launch { c.serverInvite(p).onSuccess { invite = it }.onFailure { msg = srvErrorText(s, (it as? com.tennis.scoremanager.data.ClubServerError)?.code ?: "NET") }; busy = false }
        }, Modifier.fillMaxWidth(), enabled = !busy)
        if (linked?.hasPin == true) {
            Text(s.srvPinSet, color = TsmColors.TextDim, fontSize = 13.sp)
            GhostButton(s.srvPinReset, Icons.Filled.Password, {
                busy = true; msg = null
                scope.launch { msg = c.serverResetPin(p.id)?.let { srvErrorText(s, it) } ?: s.srvPinResetDone; busy = false }
            }, Modifier.fillMaxWidth(), enabled = !busy)
        }
        if (linked != null) GhostButton(s.srvRevoke, Icons.Filled.LinkOff, {
            busy = true; msg = null
            scope.launch { msg = c.serverRemovePlayer(p.id)?.let { srvErrorText(s, it) }; busy = false }
        }, Modifier.fillMaxWidth(), enabled = !busy)
        msg?.let { Text(it, color = TsmColors.Orange, fontSize = 13.sp) }
    }
    invite?.let { inv -> InviteDialog(c, inv) { invite = null } }
}

@Composable
private fun InviteDialog(c: MatchController, inv: SrvInvite, onDone: () -> Unit) {
    val s = LocalStrings.current
    val lang = c.options.value.lang
    val context = LocalContext.current
    val conf by c.clubServer.collectAsState()
    val link = ClubServers.inviteLink(conf, inv.code)
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(s.srvInvite) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                QrCode(link, 220.dp)
                Text(s.srvInviteCode(inv.code.chunked(5).joinToString("-")), color = TsmColors.TextMain, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(s.srvInviteUntil(shortDate(inv.expires, lang)), color = TsmColors.TextDim, fontSize = 13.sp)
                Text(s.srvInviteHint, color = TsmColors.TextDim, fontSize = 13.sp)
                if (conf.publicUrl.isBlank()) Text(s.srvInviteLocalOnly, color = TsmColors.Orange, fontSize = 13.sp)
                GhostButton(s.srvShare, Icons.Filled.Share, {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, s.srvShareText(conf.club.ifBlank { s.srvTitle }, link))
                    context.startActivity(Intent.createChooser(send, s.srvShare))
                }, Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text(s.ok) } },
    )
}
