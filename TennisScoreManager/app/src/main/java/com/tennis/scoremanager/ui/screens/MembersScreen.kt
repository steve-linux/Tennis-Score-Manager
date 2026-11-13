// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.ui.screens

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CardMembership
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VerifiedUser
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tennis.scoremanager.MatchController
import com.tennis.scoremanager.data.FitpCard
import com.tennis.scoremanager.data.MedicalKind
import com.tennis.scoremanager.data.Members
import com.tennis.scoremanager.data.Membership
import com.tennis.scoremanager.data.PlayerProfile
import com.tennis.scoremanager.data.Players
import com.tennis.scoremanager.data.Reports
import com.tennis.scoremanager.ui.BigButton
import com.tennis.scoremanager.ui.GhostButton
import com.tennis.scoremanager.ui.LocalStrings
import com.tennis.scoremanager.ui.PlayerAvatar
import com.tennis.scoremanager.ui.PlayerEditDialog
import com.tennis.scoremanager.ui.ScreenScaffold
import com.tennis.scoremanager.ui.SectionCard
import com.tennis.scoremanager.ui.SegOption
import com.tennis.scoremanager.ui.Segmented
import com.tennis.scoremanager.ui.Strings
import com.tennis.scoremanager.ui.SwitchRow
import com.tennis.scoremanager.ui.TsmColors
import java.util.Calendar

/** Il testo di un problema del socio ("certificato scaduto"). */
fun issueText(s: Strings, p: PlayerProfile, i: Members.Issue, now: Long, lang: com.tennis.scoremanager.model.Lang): String = when (i) {
    Members.Issue.NO_PRIVACY -> s.issueNoPrivacy
    Members.Issue.NO_FITP -> s.issueNoFitp(Members.year(now))
    Members.Issue.NO_MEDICAL -> s.issueNoMedical
    Members.Issue.MEDICAL_EXPIRED -> s.issueMedicalExpired
    Members.Issue.MEDICAL_SOON -> s.issueMedicalSoon(shortDate(p.member?.medicalUntil, lang))
    Members.Issue.NO_GUARDIAN -> s.issueNoGuardian
    Members.Issue.NO_CONTACT -> s.issueNoContact
}

/** "20 nov 2026": le date dei soci, senza il giorno della settimana. */
fun shortDate(t: Long?, lang: com.tennis.scoremanager.model.Lang): String {
    t ?: return ""
    val loc = Reports.locale(lang)
    return java.text.SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(loc, "dMMMyyyy"), loc).format(java.util.Date(t))
}

/**
 * Circolo › Soci: chi è iscritto, con quello che manca per essere in regola (tessera FITP dell'anno, certificato,
 * consenso privacy, genitore per i minorenni, un contatto). "Da sistemare" mostra solo chi ha qualcosa da fare.
 */
@Composable
fun MembersScreen(c: MatchController) {
    val s = LocalStrings.current
    BackHandler { c.back() }
    val players by c.players.collectAsState()
    val lang = c.options.collectAsState().value.lang
    val now = remember { System.currentTimeMillis() }
    var todoOnly by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    var notify by remember { mutableStateOf(false) }
    val book by c.clubBook.collectAsState()
    val members = players.filter { it.member != null }
    val issues = remember(members) { members.associate { it.id to Members.issues(it, now) } }
    // Quanto deve ciascuno al circolo: anche questo è da sistemare.
    val owed = remember(members, book) { members.associate { it.id to com.tennis.scoremanager.data.Fees.owed(book, it) } }
    fun todo(p: PlayerProfile) = issues[p.id].orEmpty().isNotEmpty() || (owed[p.id] ?: 0) > 0
    val todo = members.count(::todo)
    val shown = (if (query.isBlank()) members else Players.search(members, query))
        .filter { !todoOnly || todo(it) }
    ScreenScaffold(
        onHelp = { c.openHelp("7.11") },
        title = s.membersTitle,
        subtitle = s.membersHint,
        icon = Icons.Filled.CardMembership,
        bottomBar = {
            GhostButton(s.back, Icons.AutoMirrored.Filled.ArrowBack, { c.back() }, Modifier.weight(1f))
            BigButton(s.membersNew, Icons.Filled.PersonAdd, { adding = true }, Modifier.weight(1f))
        },
    ) {
        if (members.isEmpty()) {
            Text(s.membersNone, color = TsmColors.TextDim)
        } else {
            Text(s.membersCount(members.size, todo), color = if (todo > 0) TsmColors.Orange else TsmColors.Ok, fontWeight = FontWeight.Bold)
            Segmented(listOf(SegOption(s.membersAll), SegOption(s.membersTodo)), if (todoOnly) 1 else 0, { todoOnly = it == 1 })
            GhostButton(s.ntMembersButton, Icons.Filled.Campaign, { notify = true }, Modifier.fillMaxWidth())
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(s.playersSearch) }, leadingIcon = { Icon(Icons.Filled.Search, null) })
        }
        for (p in shown) {
            val m = p.member ?: continue
            val list = issues[p.id].orEmpty()
            Row(Modifier.fillMaxWidth().clickable { c.openStudent(p.id) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                PlayerAvatar(c, p.name, TsmColors.Ball, 44.dp)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(p.name, color = TsmColors.TextMain, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    val line = listOfNotNull(
                        m.fitpYear?.let { "FITP $it" }, m.ranking.takeIf { it.isNotBlank() },
                        // Il certificato con un problema lo dice già la riga sotto.
                        m.medicalUntil?.takeIf { list.none { i -> i == Members.Issue.MEDICAL_SOON || i == Members.Issue.MEDICAL_EXPIRED } }?.let { s.memberMedicalShort(shortDate(it, lang)) },
                    ).joinToString(" · ")
                    if (line.isNotEmpty()) Text(line, color = TsmColors.TextDim, fontSize = 13.sp)
                    owed[p.id]?.takeIf { it > 0 }?.let { Text(s.issueFeeDue(com.tennis.scoremanager.data.Fees.money(it, lang.locale)), color = TsmColors.Orange, fontSize = 13.sp) }
                    if (list.isEmpty() && (owed[p.id] ?: 0) == 0) Text(s.memberOk, color = TsmColors.Ok, fontSize = 13.sp)
                    else Text(
                        list.joinToString(" · ") { issueText(s, p, it, now, lang) },
                        color = if (list.any(Members::blocking)) TsmColors.Danger else TsmColors.Orange, fontSize = 13.sp,
                    )
                }
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = TsmColors.TextDim)
            }
        }
    }
    if (notify) AlertDialog(
        onDismissRequest = { notify = false },
        title = { Text(s.ntChoose) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton(s.ntMembersTodo, Icons.Filled.VerifiedUser, { notify = false; c.noticeMembersTodo() }, Modifier.fillMaxWidth())
                GhostButton(s.ntMembersAll, Icons.Filled.Campaign, { notify = false; c.noticeMembersAll() }, Modifier.fillMaxWidth())
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { notify = false }) { Text(s.back) } },
    )
    if (adding) MemberPickDialog(c) { picked ->
        adding = false
        picked?.let { p ->
            c.savePlayer(p.copy(member = p.member ?: Membership(since = noon(System.currentTimeMillis()))))
            c.openStudent(p.id)
        }
    }
}

/** Mezzogiorno di quel giorno: la data non cambia col fuso orario. */
private fun noon(t: Long): Long = Calendar.getInstance().apply {
    timeInMillis = t
    set(Calendar.HOUR_OF_DAY, 12); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

/** Chi si iscrive: un giocatore che c'è già (non socio) o uno nuovo. */
@Composable
private fun MemberPickDialog(c: MatchController, onDone: (PlayerProfile?) -> Unit) {
    val s = LocalStrings.current
    val players by c.players.collectAsState()
    var query by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    val free = players.filter { it.member == null }
    val list = if (query.isBlank()) free else Players.search(free, query)
    AlertDialog(
        onDismissRequest = { onDone(null) },
        title = { Text(s.membersPickTitle) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(s.membersPickHint, color = TsmColors.TextDim, fontSize = 13.sp)
                GhostButton(s.membersNewPlayer, Icons.Filled.PersonAdd, { creating = true }, Modifier.fillMaxWidth())
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(s.playersSearch) }, leadingIcon = { Icon(Icons.Filled.Search, null) })
                for (p in list.take(40)) {
                    Row(Modifier.fillMaxWidth().clickable { onDone(p) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        PlayerAvatar(c, p.name, TsmColors.Ball, 36.dp)
                        Column(Modifier.padding(start = 10.dp)) {
                            Text(p.name, color = TsmColors.TextMain, fontWeight = FontWeight.Bold)
                            p.fullName.takeIf { it.isNotBlank() && it != p.name }?.let { Text(it, color = TsmColors.TextDim, fontSize = 12.sp) }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { onDone(null) }) { Text(s.back) } },
    )
    if (creating) PlayerEditDialog(c, null) { p -> creating = false; if (p != null) onDone(p) }
}

/** Nella scheda del giocatore: i dati da socio, o il tasto per iscriverlo (col ruolo Circolo). */
@Composable
fun MemberSection(c: MatchController, p: PlayerProfile) {
    val s = LocalStrings.current
    val lang = c.options.collectAsState().value.lang
    val roles by c.roles.collectAsState()
    var edit by remember { mutableStateOf(false) }
    var remove by remember { mutableStateOf(false) }
    val m = p.member
    if (m == null) {
        if (com.tennis.scoremanager.Role.CLUB in roles) GhostButton(s.memberEnroll, Icons.Filled.CardMembership, {
            c.savePlayer(p.copy(member = Membership(since = noon(System.currentTimeMillis()))))
            edit = true
        }, Modifier.fillMaxWidth())
    } else {
        val now = System.currentTimeMillis()
        SectionCard(s.memberSection, Icons.Filled.CardMembership) {
            @Composable fun row(label: String, value: String?) {
                if (value.isNullOrBlank()) return
                // Etichetta e valore si dividono la riga: un valore lungo va a capo a destra, l'etichetta non si schiaccia.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(label, color = TsmColors.TextDim, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text(value, color = TsmColors.TextMain, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.2f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.End)
                }
            }
            val issues = Members.issues(p, now)
            if (issues.isEmpty()) Text(s.memberOk, color = TsmColors.Ok, fontWeight = FontWeight.Bold)
            for (i in issues) Text(issueText(s, p, i, now, lang), color = if (Members.blocking(i)) TsmColors.Danger else TsmColors.Orange, fontSize = 13.sp)
            row(s.memberSince, m.since?.let { shortDate(it, lang) })
            row(s.memberNumber, m.number)
            row(s.memberFitpNumber, listOfNotNull(m.fitpNumber.ifBlank { null }, m.fitpCard?.let { fitpName(s, it) }, m.fitpYear?.toString()).joinToString(" · "))
            row(s.memberRankingLabel, m.ranking)
            row(s.memberMedical, listOfNotNull(m.medical?.let { medicalName(s, it) }, m.medicalUntil?.let { s.memberValidUntil(shortDate(it, lang)) }).joinToString(" · "))
            row(s.memberPrivacy, m.privacyAt?.let { shortDate(it, lang) })
            row(s.memberPhotos, if (m.photosOk) s.memberYes else s.off)
            row(s.memberBirth, p.birth?.let { b -> shortDate(b, lang) + (Members.age(b, now)?.let { " ($it)" } ?: "") })
            row(s.memberPhone, p.phone)
            row(s.memberEmail, p.email)
            row(s.memberGuardian, listOf(m.guardian, m.guardianPhone).filter { it.isNotBlank() }.joinToString(" · "))
            GhostButton(s.memberEdit, Icons.Filled.VerifiedUser, { edit = true }, Modifier.fillMaxWidth())
            TextButton(onClick = { remove = true }) { Text(s.memberRemove, color = TsmColors.Danger) }
        }
    }
    if (edit) c.playerById(p.id)?.let { cur -> MemberEditDialog(c, cur) { edit = false } }
    if (remove) AlertDialog(
        onDismissRequest = { remove = false },
        text = { Text(s.memberRemoveAsk(p.name)) },
        confirmButton = { TextButton(onClick = { remove = false; c.savePlayer(p.copy(member = null)) }) { Text(s.memberRemove, color = TsmColors.Danger) } },
        dismissButton = { TextButton(onClick = { remove = false }) { Text(s.back) } },
    )
}

fun fitpName(s: Strings, k: FitpCard) = when (k) {
    FitpCard.AGONIST -> s.memberFitpAgonist
    FitpCard.NON_AGONIST -> s.memberFitpNonAgonist
    FitpCard.OTHER -> s.memberFitpOther
}

fun medicalName(s: Strings, k: MedicalKind) = when (k) {
    MedicalKind.AGONISTIC -> s.memberMedicalAgonistic
    MedicalKind.NON_AGONISTIC -> s.memberMedicalNon
}

/** Una data da scegliere col calendario; la X la toglie. [startYearsAgo]: da dove parte il calendario se è vuota. */
@Composable
private fun DateField(label: String, value: Long?, lang: com.tennis.scoremanager.model.Lang, startYearsAgo: Int = 0, onPick: (Long?) -> Unit) {
    val s = LocalStrings.current
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        GhostButton("$label: ${value?.let { shortDate(it, lang) } ?: s.memberNotSet}", Icons.Filled.CalendarMonth, {
            val cal = Calendar.getInstance().apply { timeInMillis = value ?: System.currentTimeMillis(); if (value == null) add(Calendar.YEAR, -startYearsAgo) }
            DatePickerDialog(context, { _, y, mo, d -> onPick(Calendar.getInstance().apply { set(y, mo, d, 12, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis) },
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }, Modifier.weight(1f))
        if (value != null) IconButton(onClick = { onPick(null) }) { Icon(Icons.Filled.Close, null, tint = TsmColors.TextDim) }
    }
}

/** I dati del socio: contatti, tessere, certificato, consensi; per i minorenni il genitore. */
@Composable
private fun MemberEditDialog(c: MatchController, p: PlayerProfile, onDone: () -> Unit) {
    val s = LocalStrings.current
    val lang = c.options.collectAsState().value.lang
    val m0 = p.member ?: Membership()
    var phone by remember { mutableStateOf(p.phone) }
    var email by remember { mutableStateOf(p.email) }
    var birth by remember { mutableStateOf(p.birth) }
    var m by remember { mutableStateOf(m0) }
    val now = System.currentTimeMillis()
    val year = Members.year(now)
    @Composable fun field(v: String, label: String, type: KeyboardType = KeyboardType.Text, onChange: (String) -> Unit) = OutlinedTextField(
        v, { onChange(it.take(60)) }, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = type),
    )
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(s.memberEdit) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                field(phone, s.memberPhone, KeyboardType.Phone) { phone = it }
                field(email, s.memberEmail, KeyboardType.Email) { email = it.trim() }
                DateField(s.memberBirth, birth, lang, startYearsAgo = 30) { birth = it }
                if ((Members.age(birth, now) ?: 99) < 18) {
                    Text(s.memberMinor, color = TsmColors.Orange, fontSize = 13.sp)
                    field(m.guardian, s.memberGuardian) { m = m.copy(guardian = it) }
                    field(m.guardianPhone, s.memberGuardianPhone, KeyboardType.Phone) { m = m.copy(guardianPhone = it) }
                }
                DateField(s.memberSince, m.since, lang) { m = m.copy(since = it) }
                field(m.number, s.memberNumber) { m = m.copy(number = it) }
                Text("FITP", color = TsmColors.TextMain, fontWeight = FontWeight.Bold)
                field(m.fitpNumber, s.memberFitpNumber, KeyboardType.Number) { m = m.copy(fitpNumber = it.trim()) }
                val cards = listOf(null) + FitpCard.entries
                Segmented(cards.map { SegOption(it?.let { k -> fitpName(s, k) } ?: "—") }, cards.indexOf(m.fitpCard), { m = m.copy(fitpCard = cards[it]) }, columns = 2)
                // L'anno della tessera: quest'anno o il prossimo (i rinnovi si fanno a fine anno); un anno vecchio resta visibile.
                val years = (listOf(null, year, year + 1) + listOfNotNull(m.fitpYear)).distinct()
                Text(s.memberFitpYear, color = TsmColors.TextDim, fontSize = 13.sp)
                Segmented(years.map { SegOption(it?.toString() ?: "—") }, years.indexOf(m.fitpYear), { m = m.copy(fitpYear = years[it]) })
                field(m.ranking, s.memberRanking) { m = m.copy(ranking = it.trim().uppercase()) }
                Text(s.memberMedical, color = TsmColors.TextMain, fontWeight = FontWeight.Bold)
                val kinds = listOf(null) + MedicalKind.entries
                Segmented(kinds.map { SegOption(it?.let { k -> medicalName(s, k) } ?: "—") }, kinds.indexOf(m.medical), { m = m.copy(medical = kinds[it]) })
                DateField(s.memberMedicalUntil, m.medicalUntil, lang) { m = m.copy(medicalUntil = it) }
                Text(s.memberMedicalNote, color = TsmColors.TextDim, fontSize = 12.sp)
                SwitchRow(Icons.Filled.VerifiedUser, s.memberPrivacy, m.privacyAt?.let { shortDate(it, lang) }, m.privacyAt != null) { on ->
                    m = m.copy(privacyAt = if (on) noon(System.currentTimeMillis()) else null)
                }
                SwitchRow(Icons.Filled.PhotoCamera, s.memberPhotos, null, m.photosOk) { m = m.copy(photosOk = it) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                c.savePlayer(p.copy(phone = phone.trim(), email = email.trim(), birth = birth, member = m))
                onDone()
            }) { Text(s.ok) }
        },
        dismissButton = { TextButton(onClick = onDone) { Text(s.back) } },
    )
}
