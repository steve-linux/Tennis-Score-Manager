// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.ui.screens

import android.app.DatePickerDialog
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Euro
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tennis.scoremanager.MatchController
import com.tennis.scoremanager.data.Charge
import com.tennis.scoremanager.data.Fee
import com.tennis.scoremanager.data.FeeKind
import com.tennis.scoremanager.data.Fees
import com.tennis.scoremanager.data.Members
import com.tennis.scoremanager.data.PayMethod
import com.tennis.scoremanager.data.PlayerProfile
import com.tennis.scoremanager.ui.BigButton
import com.tennis.scoremanager.ui.GhostButton
import com.tennis.scoremanager.ui.LocalStrings
import com.tennis.scoremanager.ui.ScreenScaffold
import com.tennis.scoremanager.ui.SectionCard
import com.tennis.scoremanager.ui.SegOption
import com.tennis.scoremanager.ui.Segmented
import com.tennis.scoremanager.ui.TsmColors
import com.tennis.scoremanager.ui.chargeStatusColor
import com.tennis.scoremanager.ui.chargeStatusName
import com.tennis.scoremanager.ui.feeKindName
import com.tennis.scoremanager.ui.payMethodName
import java.util.Calendar

/** Mezzogiorno del giorno scelto: la data non cambia col fuso orario. */
private fun noonOf(y: Int, m: Int, d: Int): Long = Calendar.getInstance().apply { set(y, m, d, 12, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis

private fun today(): Long = Calendar.getInstance().run { noonOf(get(Calendar.YEAR), get(Calendar.MONTH), get(Calendar.DAY_OF_MONTH)) }

/** Un bottone con la data; toccandolo si sceglie dal calendario. */
@Composable
internal fun DateButton(label: String, value: Long?, lang: com.tennis.scoremanager.model.Lang, modifier: Modifier = Modifier.fillMaxWidth(), onPick: (Long) -> Unit) {
    val s = LocalStrings.current
    val context = LocalContext.current
    GhostButton("$label: ${value?.let { shortDate(it, lang) } ?: s.memberNotSet}", Icons.Filled.CalendarMonth, {
        val cal = Calendar.getInstance().apply { timeInMillis = value ?: System.currentTimeMillis() }
        DatePickerDialog(context, { _, y, m, d -> onPick(noonOf(y, m, d)) }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }, modifier)
}

@Composable
internal fun MoneyField(text: String, label: String, onChange: (String) -> Unit) {
    val s = LocalStrings.current
    val bad = text.isNotBlank() && Fees.parse(text) == null
    OutlinedTextField(
        text, { onChange(it.take(12)) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(label) }, isError = bad,
        supportingText = if (bad) ({ Text(s.moneyInvalid) }) else null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), leadingIcon = { Icon(Icons.Filled.Euro, null) },
    )
}

/** "150,00" per riempire un campo importo. */
internal fun plain(cents: Int, lang: com.tennis.scoremanager.model.Lang): String {
    val sep = java.text.DecimalFormatSymbols.getInstance(lang.locale).decimalSeparator
    return "%d%c%02d".format(cents / 100, sep, cents % 100)
}

/**
 * Circolo › Quote e pagamenti: le quote del circolo (si addebitano a tutti i soci in un colpo), chi deve ancora
 * pagare, quanto si è incassato nell'anno e l'esportazione dei pagamenti per il commercialista.
 */
@Composable
fun PaymentsScreen(c: MatchController) {
    val s = LocalStrings.current
    val context = LocalContext.current
    BackHandler { c.back() }
    val book by c.clubBook.collectAsState()
    val players by c.players.collectAsState()
    val lang = c.options.value.lang
    val now = remember { System.currentTimeMillis() }
    val year = Members.year(now)
    var editFee by remember { mutableStateOf<Fee?>(null) }
    var newFee by remember { mutableStateOf(false) }
    var exportYear by remember { mutableStateOf(year) }
    var editType by remember { mutableStateOf<com.tennis.scoremanager.data.PayType?>(null) }
    ScreenScaffold(
        onHelp = { c.openHelp("7.12") },
        title = s.paymentsTitle,
        subtitle = s.paymentsHint,
        icon = Icons.Filled.Payments,
        bottomBar = {
            GhostButton(s.back, Icons.AutoMirrored.Filled.ArrowBack, { c.back() }, Modifier.weight(1f))
            BigButton(s.feeNew, Icons.Filled.Add, { newFee = true }, Modifier.weight(1f))
        },
    ) {
        val (openCents, openPeople) = Fees.open(book)
        Text(s.paymentsIncome(year, Fees.money(Fees.income(book, year), lang.locale)), color = TsmColors.Ok, fontWeight = FontWeight.Bold)
        if (openCents > 0) Text(s.paymentsOpen(Fees.money(openCents, lang.locale), openPeople), color = TsmColors.Orange, fontWeight = FontWeight.Bold)

        SectionCard(s.feesTitle, Icons.Filled.Receipt) {
            if (book.fees.isEmpty()) Text(s.feesNone, color = TsmColors.TextDim, fontSize = 13.sp)
            for (f in book.fees.sortedByDescending { it.createdAt }) {
                val (paid, total) = Fees.feeProgress(book, f)
                Row(Modifier.fillMaxWidth().clickable { editFee = f }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(f.name, color = TsmColors.TextMain, fontWeight = FontWeight.Bold)
                        Text(
                            listOfNotNull(Fees.money(f.cents, lang.locale), feeKindName(s, f.kind), f.due?.let { s.feeDueShort(shortDate(it, lang)) },
                                s.feeChargedLine(paid, total).takeIf { total > 0 }).joinToString(" · "),
                            color = TsmColors.TextDim, fontSize = 13.sp,
                        )
                    }
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = TsmColors.TextDim)
                }
            }
        }

        // Chi deve ancora pagare, dal debito più grande.
        SectionCard(s.dueTitle, Icons.Filled.Payments, accent = TsmColors.Orange) {
            val open = book.charges.filter { it.left > 0 }.groupBy { it.player.ifEmpty { "#" + it.name } }
                .map { (key, list) -> Triple(key, list, list.sumOf { it.left }) }.sortedByDescending { it.third }
            if (open.isEmpty()) Text(s.dueNone, color = TsmColors.TextDim, fontSize = 13.sp)
            else GhostButton(s.ntDuesButton, Icons.Filled.Campaign, { c.noticeDues() }, Modifier.fillMaxWidth())
            for ((key, list, left) in open) {
                val p = players.firstOrNull { it.id == key || key in it.aliases }
                Row(
                    Modifier.fillMaxWidth().clickable(enabled = p != null) { p?.let { c.openStudent(it.id) } }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(p?.name ?: list.first().name, color = TsmColors.TextMain, fontWeight = FontWeight.Bold)
                        Text(list.joinToString(" · ") { it.label }, color = TsmColors.TextDim, fontSize = 13.sp)
                    }
                    val late = list.any { Fees.status(it, now) == Fees.Status.LATE }
                    Text(Fees.money(left, lang.locale), color = if (late) TsmColors.Danger else TsmColors.Orange, fontWeight = FontWeight.Bold)
                }
            }
        }

        // I modi di pagamento li sceglie il circolo: quelli a distanza, con link, IBAN o numero, vanno negli avvisi.
        SectionCard(s.ptTitle, Icons.Filled.Euro) {
            Text(s.ptHint, color = TsmColors.TextDim, fontSize = 13.sp)
            val types = c.payTypes()
            for ((i, t) in types.withIndex()) {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(TsmColors.SurfaceHigh).clickable { editType = t }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(t.name, color = TsmColors.TextMain, fontWeight = FontWeight.Bold)
                        Text(listOfNotNull(s.ptOnlineShort.takeIf { t.online }, t.details.ifBlank { null }).joinToString(" · ").ifBlank { s.ptAtClub }, color = TsmColors.TextDim, fontSize = 12.sp, maxLines = 2)
                    }
                    if (i > 0) androidx.compose.material3.IconButton(onClick = { c.savePayTypes(types.toMutableList().also { l -> l[i] = l[i - 1].also { l[i - 1] = l[i] } }) }) {
                        Icon(Icons.Filled.KeyboardArrowUp, null, tint = TsmColors.TextDim)
                    }
                }
            }
            GhostButton(s.ptNew, Icons.Filled.Add, { editType = com.tennis.scoremanager.data.PayType(Fees.newId("t"), "") }, Modifier.fillMaxWidth())
        }

        SectionCard(s.paymentsExport, Icons.Filled.TableChart) {
            Text(s.paymentsExportHint, color = TsmColors.TextDim, fontSize = 13.sp)
            val years = listOf(year - 1, year)
            Segmented(years.map { SegOption(it.toString()) }, years.indexOf(exportYear), { exportYear = years[it] })
            GhostButton(s.paymentsExport, Icons.Filled.TableChart, {
                val f = c.paymentsCsv(exportYear)
                if (f == null) Toast.makeText(context, s.reportFailed, Toast.LENGTH_LONG).show()
                else context.startActivity(c.shareFileIntent(f, "text/csv"))
            }, Modifier.fillMaxWidth())
        }
    }
    if (newFee) FeeDialog(c, null) { newFee = false }
    editType?.let { t -> PayTypeDialog(c, t) { editType = null } }
    editFee?.let { f -> FeeDialog(c, f) { editFee = null } }
}

/** Quota nuova o da cambiare: nome, tipo, importo, scadenza; da qui la si addebita a tutti i soci. */
@Composable
private fun FeeDialog(c: MatchController, fee: Fee?, onDone: () -> Unit) {
    val s = LocalStrings.current
    val lang = c.options.value.lang
    val players by c.players.collectAsState()
    var name by remember { mutableStateOf(fee?.name.orEmpty()) }
    var kind by remember { mutableStateOf(fee?.kind ?: FeeKind.MEMBERSHIP) }
    var amount by remember { mutableStateOf(fee?.let { plain(it.cents, lang) }.orEmpty()) }
    var due by remember { mutableStateOf(fee?.due) }
    var askAll by remember { mutableStateOf(false) }
    var askDelete by remember { mutableStateOf(false) }
    val cents = Fees.parse(amount)
    fun current(): Fee? {
        val v = cents ?: return null
        if (name.isBlank()) return null
        return (fee ?: Fee(Fees.newId("f"), "", 0, createdAt = System.currentTimeMillis())).copy(name = name.trim(), kind = kind, cents = v, due = due)
    }
    fun save(f: Fee) {
        val book = c.clubBook.value
        c.saveClubBook(book.copy(fees = book.fees.filter { it.id != f.id } + f))
    }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(if (fee == null) s.feeNew else fee.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it.take(60) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(s.feeName) })
                val kinds = FeeKind.entries
                Segmented(kinds.map { SegOption(feeKindName(s, it)) }, kinds.indexOf(kind), { kind = kinds[it] }, columns = 3)
                MoneyField(amount, s.feeAmount) { amount = it }
                DateButton(s.feeDue, due, lang) { due = it }
                if (fee != null) {
                    val members = players.filter { it.member != null }
                    GhostButton(s.feeChargeAll, Icons.Filled.GroupAdd, { askAll = true }, Modifier.fillMaxWidth(), enabled = members.isNotEmpty())
                    TextButton(onClick = { askDelete = true }) { Text(s.feeDelete, color = TsmColors.Danger) }
                }
            }
        },
        confirmButton = { TextButton(enabled = current() != null, onClick = { current()?.let(::save); onDone() }) { Text(s.ok) } },
        dismissButton = { TextButton(onClick = onDone) { Text(s.back) } },
    )
    if (askAll) {
        val f = current() ?: fee!!
        val members = players.filter { it.member != null }
        val todo = Fees.chargeAll(c.clubBook.value, f, members, 0L).second
        AlertDialog(
            onDismissRequest = { askAll = false },
            text = { Text(if (todo == 0) s.feeChargeAllDone else s.feeChargeAllAsk(f.name, todo)) },
            confirmButton = {
                TextButton(onClick = {
                    askAll = false
                    if (todo > 0) {
                        save(f)
                        c.saveClubBook(Fees.chargeAll(c.clubBook.value, f, members, System.currentTimeMillis()).first)
                    }
                    onDone()
                }) { Text(if (todo == 0) s.ok else s.feeChargeAll) }
            },
            dismissButton = { if (todo > 0) TextButton(onClick = { askAll = false }) { Text(s.back) } },
        )
    }
    if (askDelete && fee != null) AlertDialog(
        onDismissRequest = { askDelete = false },
        text = { Text(s.feeDeleteAsk(fee.name)) },
        confirmButton = {
            TextButton(onClick = {
                val book = c.clubBook.value
                c.saveClubBook(book.copy(fees = book.fees.filter { it.id != fee.id }))
                askDelete = false
                onDone()
            }) { Text(s.feeDelete, color = TsmColors.Danger) }
        },
        dismissButton = { TextButton(onClick = { askDelete = false }) { Text(s.back) } },
    )
}

/** Nella scheda del giocatore: le sue quote coi pagamenti; da qui si addebita, si incassa e si fa la ricevuta. */
@Composable
fun ChargesSection(c: MatchController, p: PlayerProfile) {
    val s = LocalStrings.current
    val book by c.clubBook.collectAsState()
    val roles by c.roles.collectAsState()
    val lang = c.options.value.lang
    val mine = Fees.of(book, p)
    if (mine.isEmpty() && (p.member == null || com.tennis.scoremanager.Role.CLUB !in roles)) return
    val now = System.currentTimeMillis()
    var adding by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf<String?>(null) }
    SectionCard(s.paymentsTitle, Icons.Filled.Payments) {
        val owed = mine.sumOf { it.left }
        if (owed > 0) Text(s.issueFeeDue(Fees.money(owed, lang.locale)), color = TsmColors.Orange, fontWeight = FontWeight.Bold)
        for (ch in mine) {
            val st = Fees.status(ch, now)
            Row(Modifier.fillMaxWidth().clickable { open = ch.id }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(ch.label, color = TsmColors.TextMain, fontWeight = FontWeight.Bold)
                    Text(chargeStatusName(s, st, Fees.money(ch.left, lang.locale)), color = chargeStatusColor(st), fontSize = 13.sp)
                }
                Text(Fees.money(ch.cents, lang.locale), color = TsmColors.TextMain)
            }
        }
        GhostButton(s.chargeNew, Icons.Filled.Add, { adding = true }, Modifier.fillMaxWidth())
    }
    if (adding) NewChargeDialog(c, p) { adding = false }
    open?.let { id -> book.charges.firstOrNull { it.id == id }?.let { ChargeDialog(c, it) { open = null } } ?: run { open = null } }
}

/** Un addebito nuovo: una delle quote del circolo o un importo libero. */
@Composable
private fun NewChargeDialog(c: MatchController, p: PlayerProfile, onDone: () -> Unit) {
    val s = LocalStrings.current
    val book by c.clubBook.collectAsState()
    val lang = c.options.value.lang
    var free by remember { mutableStateOf(book.fees.isEmpty()) }
    var label by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(s.chargeNew) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!free) {
                    for (f in book.fees.sortedByDescending { it.createdAt }) {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(TsmColors.SurfaceHigh).clickable {
                                c.saveClubBook(Fees.charge(c.clubBook.value, p, f.name, f.cents, System.currentTimeMillis(), f))
                                onDone()
                            }.padding(10.dp),
                        ) {
                            Text(f.name, color = TsmColors.TextMain, modifier = Modifier.weight(1f))
                            Text(Fees.money(f.cents, lang.locale), color = TsmColors.Ball, fontWeight = FontWeight.Bold)
                        }
                    }
                    GhostButton(s.chargeFree, Icons.Filled.Euro, { free = true }, Modifier.fillMaxWidth())
                } else {
                    OutlinedTextField(label, { label = it.take(60) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(s.chargeLabel) })
                    MoneyField(amount, s.feeAmount) { amount = it }
                }
            }
        },
        confirmButton = {
            if (free) TextButton(enabled = label.isNotBlank() && (Fees.parse(amount) ?: 0) > 0, onClick = {
                c.saveClubBook(Fees.charge(c.clubBook.value, p, label.trim(), Fees.parse(amount)!!, System.currentTimeMillis()))
                onDone()
            }) { Text(s.ok) }
        },
        dismissButton = { TextButton(onClick = onDone) { Text(s.back) } },
    )
}

/** Un addebito: i pagamenti con la ricevuta, "Registra pagamento", annulla. */
@Composable
private fun ChargeDialog(c: MatchController, ch: Charge, onDone: () -> Unit) {
    val s = LocalStrings.current
    val context = LocalContext.current
    val lang = c.options.value.lang
    var paying by remember { mutableStateOf(false) }
    var unpay by remember { mutableStateOf<String?>(null) }
    var cancel by remember { mutableStateOf(false) }
    fun share(paymentId: String) {
        val f = c.receiptFile(ch.id, paymentId)
        if (f == null) Toast.makeText(context, s.reportFailed, Toast.LENGTH_LONG).show()
        else context.startActivity(c.shareFileIntent(f, "application/pdf"))
    }
    val st = Fees.status(ch, System.currentTimeMillis())
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(ch.label) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${Fees.money(ch.cents, lang.locale)} · ${chargeStatusName(s, st, Fees.money(ch.left, lang.locale))}", color = chargeStatusColor(st), fontWeight = FontWeight.Bold)
                ch.due?.let { Text(s.feeDueShort(shortDate(it, lang)), color = TsmColors.TextDim, fontSize = 13.sp) }
                for (p in ch.payments) {
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(TsmColors.SurfaceHigh).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${Fees.money(p.cents, lang.locale)} · ${com.tennis.scoremanager.ui.paymentName(s, p)}", color = TsmColors.TextMain, fontWeight = FontWeight.Bold)
                            Text(listOf(shortDate(p.at, lang), s.payReceiptNo(p.receipt), p.ref).filter { it.isNotBlank() }.joinToString(" · "), color = TsmColors.TextDim, fontSize = 12.sp)
                        }
                        androidx.compose.material3.IconButton(onClick = { share(p.id) }) { Icon(Icons.Filled.Receipt, s.payReceipt, tint = TsmColors.Ball) }
                        androidx.compose.material3.IconButton(onClick = { unpay = p.id }) { Icon(Icons.Filled.Delete, s.payDelete, tint = TsmColors.TextDim) }
                    }
                }
                if (ch.left > 0) BigButton(s.payRecord, Icons.Filled.Euro, { paying = true }, Modifier.fillMaxWidth())
                if (!ch.cancelled) TextButton(onClick = { cancel = true }) { Text(if (ch.payments.isEmpty()) s.chargeDelete else s.chargeCancel, color = TsmColors.Danger) }
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text(s.ok) } },
    )
    if (paying) PayDialog(c, ch, onPaid = { id -> paying = false; share(id) }) { paying = false }
    unpay?.let { pid ->
        AlertDialog(
            onDismissRequest = { unpay = null },
            text = { Text(s.payDeleteAsk) },
            confirmButton = { TextButton(onClick = { c.saveClubBook(Fees.unpay(c.clubBook.value, ch.id, pid)); unpay = null }) { Text(s.payDelete, color = TsmColors.Danger) } },
            dismissButton = { TextButton(onClick = { unpay = null }) { Text(s.back) } },
        )
    }
    if (cancel) AlertDialog(
        onDismissRequest = { cancel = false },
        text = { Text(if (ch.payments.isEmpty()) s.chargeDeleteAsk else s.chargeCancelAsk) },
        confirmButton = {
            TextButton(onClick = { c.saveClubBook(Fees.cancel(c.clubBook.value, ch.id)); cancel = false; onDone() }) {
                Text(if (ch.payments.isEmpty()) s.chargeDelete else s.chargeCancel, color = TsmColors.Danger)
            }
        },
        dismissButton = { TextButton(onClick = { cancel = false }) { Text(s.back) } },
    )
}

/** Registra un versamento: importo (di solito quanto manca), data, metodo, riferimento. Poi si offre la ricevuta. */
@Composable
private fun PayDialog(c: MatchController, ch: Charge, onPaid: (String) -> Unit, onDone: () -> Unit) {
    val s = LocalStrings.current
    val lang = c.options.value.lang
    var amount by remember { mutableStateOf(plain(ch.left, lang)) }
    var at by remember { mutableStateOf(today()) }
    // I modi di pagamento del circolo: il primo è quello proposto.
    val types = remember { c.payTypes() }
    var type by remember { mutableStateOf(types.first()) }
    val method = type.method
    var ref by remember { mutableStateOf("") }
    var receipt by remember { mutableStateOf(true) }
    val cents = Fees.parse(amount)
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(s.payRecord) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(ch.label, color = TsmColors.TextDim, fontSize = 13.sp)
                MoneyField(amount, s.payAmount) { amount = it }
                DateButton(s.payDate, at, lang) { at = it }
                Segmented(types.map { SegOption(it.name) }, types.indexOf(type), { type = types[it] }, columns = minOf(3, types.size))
                if (method != PayMethod.CASH) OutlinedTextField(ref, { ref = it.take(60) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(s.payRef) })
                com.tennis.scoremanager.ui.SwitchRow(Icons.Filled.Receipt, s.payReceiptNow, null, receipt) { receipt = it }
            }
        },
        confirmButton = {
            TextButton(enabled = cents != null && cents > 0, onClick = {
                val r = Fees.pay(c.clubBook.value, ch.id, cents!!, at, method, if (method == PayMethod.CASH) "" else ref, type.name) ?: return@TextButton onDone()
                c.saveClubBook(r.first)
                if (receipt) onPaid(r.second.id) else onDone()
            }) { Text(s.ok) }
        },
        dismissButton = { TextButton(onClick = onDone) { Text(s.back) } },
    )
}

/** Un modo di pagamento del circolo: nome, link/IBAN/numero, se si paga a distanza. */
@Composable
private fun PayTypeDialog(c: MatchController, t0: com.tennis.scoremanager.data.PayType, onDone: () -> Unit) {
    val s = LocalStrings.current
    var t by remember { mutableStateOf(t0) }
    val types = c.payTypes()
    val isNew = types.none { it.id == t0.id }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(if (isNew) s.ptNew else t0.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(t.name, { t = t.copy(name = it.take(30)) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(s.ptName) })
                OutlinedTextField(t.details, { t = t.copy(details = it.take(200)) }, Modifier.fillMaxWidth(), label = { Text(s.ptDetails) })
                com.tennis.scoremanager.ui.SwitchRow(Icons.Filled.Euro, s.ptOnline, s.ptOnlineHint, t.online) { t = t.copy(online = it) }
                if (!isNew && types.size > 1) TextButton(onClick = { c.savePayTypes(types.filter { it.id != t0.id }); onDone() }) { Text(s.ptDelete, color = TsmColors.Danger) }
            }
        },
        confirmButton = {
            TextButton(enabled = t.name.isNotBlank(), onClick = {
                val saved = t.copy(name = t.name.trim(), details = t.details.trim())
                c.savePayTypes(if (isNew) types + saved else types.map { if (it.id == saved.id) saved else it })
                onDone()
            }) { Text(s.ok) }
        },
        dismissButton = { TextButton(onClick = onDone) { Text(s.back) } },
    )
}
