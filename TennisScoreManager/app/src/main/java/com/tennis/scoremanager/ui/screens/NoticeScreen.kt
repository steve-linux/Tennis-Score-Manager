// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tennis.scoremanager.MatchController
import com.tennis.scoremanager.data.NoticeRecipient
import com.tennis.scoremanager.data.Notices
import com.tennis.scoremanager.ui.GhostButton
import com.tennis.scoremanager.ui.LocalStrings
import com.tennis.scoremanager.ui.ScreenScaffold
import com.tennis.scoremanager.ui.SectionCard
import com.tennis.scoremanager.ui.TsmColors

/** Apre l'app giusta; se non c'è, lo dice. */
private fun launch(context: Context, intent: Intent, noApp: String) {
    runCatching { context.startActivity(intent) }.onFailure { Toast.makeText(context, noApp, Toast.LENGTH_LONG).show() }
}

/** WhatsApp con la chat di quel numero e il messaggio già scritto (si preme Invia). */
private fun whatsApp(number: String, text: String) = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$number?text=" + Uri.encode(text)))

/**
 * WhatsApp con un file (la scheda partita) e il testo, dritto nella chat di quel numero ("jid"); senza WhatsApp si
 * sceglie l'app. Il permesso di leggere il file passa con l'intent.
 */
private fun whatsAppFile(ctx: Context, number: String, text: String, file: java.io.File): Intent {
    val uri = androidx.core.content.FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
    fun send(pkg: String?) = Intent(Intent.ACTION_SEND).apply {
        type = com.tennis.scoremanager.data.MatchCards.MIME
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, text)
        clipData = android.content.ClipData.newRawUri("", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (pkg != null) { setPackage(pkg); putExtra("jid", "$number@s.whatsapp.net") }
    }
    val pm = ctx.packageManager
    val pkg = listOf("com.whatsapp", "com.whatsapp.w4b").firstOrNull { runCatching { pm.getPackageInfo(it, 0) }.isSuccess }
    return if (pkg != null) send(pkg) else Intent.createChooser(send(null), null)
}

/** Email col file allegato (la scheda partita): si sceglie l'app di posta. */
private fun emailFile(ctx: Context, to: String, subject: String, text: String, file: java.io.File): Intent {
    val uri = androidx.core.content.FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
    return Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
        type = com.tennis.scoremanager.data.MatchCards.MIME
        putExtra(Intent.EXTRA_EMAIL, arrayOf(to))
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, text)
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = android.content.ClipData.newRawUri("", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }, null)
}

private fun sms(numbers: List<String>, text: String) =
    Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + numbers.joinToString(";") { Uri.encode(it) })).putExtra("sms_body", text)

private fun email(to: List<String>, bcc: List<String>, subject: String, text: String) = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
    if (to.isNotEmpty()) putExtra(Intent.EXTRA_EMAIL, to.toTypedArray())
    if (bcc.isNotEmpty()) putExtra(Intent.EXTRA_BCC, bcc.toTypedArray())
    putExtra(Intent.EXTRA_SUBJECT, subject)
    putExtra(Intent.EXTRA_TEXT, text)
}

/**
 * Avvisi: il testo per tutti (email in copia nascosta, SMS a tutti, condivisione con l'immagine nel gruppo o sui
 * social) e uno per uno, ciascuno col suo messaggio, su WhatsApp, SMS o email. La spunta ricorda chi è già stato
 * avvisato; l'invio lo fa sempre chi usa il telefono, nell'app che si apre.
 */
@Composable
fun NoticeScreen(c: MatchController) {
    val s = LocalStrings.current
    val context = LocalContext.current
    BackHandler { c.back() }
    val plan by c.notice.collectAsState()
    val p = plan ?: return
    var common by remember(p) { mutableStateOf(p.common) }
    val sent = remember(p) { mutableStateOf(setOf<String>()) }
    fun textOf(r: NoticeRecipient) = if (p.editable || r.text.isBlank()) common else r.text
    fun mark(r: NoticeRecipient) { sent.value = sent.value + r.id }
    // Prima di passare a WhatsApp, SMS o email il testo lascia il fuoco: tornando, la tastiera non si riapre da sola
    // sopra l'elenco di chi resta da avvisare.
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    fun open(i: Intent) { focus.clearFocus(); launch(context, i, s.ntNoApp) }
    ScreenScaffold(
        onHelp = { c.openHelp("7.14") },
        title = p.title,
        subtitle = p.subject,
        icon = Icons.Filled.Campaign,
        bottomBar = { GhostButton(s.back, Icons.AutoMirrored.Filled.ArrowBack, { c.back() }, Modifier.weight(1f)) },
    ) {
        if (p.recipients.isEmpty() && common.isBlank()) { Text(s.ntNobody, color = TsmColors.TextDim); return@ScreenScaffold }
        Text(s.ntHint, color = TsmColors.TextDim, fontSize = 13.sp)
        // Il testo per tutti: si può scrivere (messaggio libero) o è quello preparato dall'app.
        if (p.editable || common.isNotBlank()) {
            SectionCard(s.ntAll, Icons.Filled.Campaign) {
                OutlinedTextField(common, { common = it }, Modifier.fillMaxWidth(), minLines = 4, label = { Text(s.ntText) }, readOnly = !p.editable)
                val phones = p.recipients.mapNotNull { r -> r.phone.ifBlank { null } }.distinct()
                val mails = p.recipients.mapNotNull { r -> r.email.ifBlank { null } }.distinct()
                GhostButton(s.ntShare, Icons.Filled.Share, {
                    val img = p.image?.takeIf { it.exists() }
                    val send = if (img != null) c.shareFileIntent(img, "image/png", common) else Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, common).putExtra(Intent.EXTRA_SUBJECT, p.subject), null)
                    open(send)
                }, Modifier.fillMaxWidth())
                if (mails.isNotEmpty()) GhostButton(s.ntEmailAll(mails.size), Icons.Filled.Email, { open(email(emptyList(), mails, p.subject, common)) }, Modifier.fillMaxWidth())
                if (phones.isNotEmpty()) GhostButton(s.ntSmsAll(phones.size), Icons.Filled.Sms, { open(sms(phones, common)) }, Modifier.fillMaxWidth())
            }
        }
        if (p.recipients.isNotEmpty()) {
            SectionCard(s.ntOneByOne(sent.value.size, p.recipients.size), Icons.AutoMirrored.Filled.Send) {
                for (r in p.recipients) {
                    val done = r.id in sent.value
                    val wa = Notices.waNumber(r.phone)
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TsmColors.SurfaceHigh).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(r.name, color = TsmColors.TextMain, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            if (done) Icon(Icons.Filled.CheckCircle, s.ntSent, tint = TsmColors.Ok)
                        }
                        val contacts = listOf(r.phone, r.email).filter { it.isNotBlank() }.joinToString(" · ")
                        Text(contacts.ifBlank { s.ntNoContact }, color = if (contacts.isBlank()) TsmColors.Orange else TsmColors.TextDim, fontSize = 12.sp)
                        // Il messaggio personale, per controllarlo prima di mandarlo.
                        if (!p.editable && r.text.isNotBlank() && r.text != common) {
                            var open by remember { mutableStateOf(false) }
                            // Chiuso: le righe dopo il saluto (partita, giorno, campo); toccando, tutto il messaggio.
                            val short = r.text.lines().drop(1).filter { it.isNotBlank() }.take(3).joinToString("\n")
                            Text(if (open) r.text else short, color = TsmColors.TextMain, fontSize = 13.sp,
                                modifier = Modifier.clickable { open = !open }, maxLines = if (open) 30 else 3)
                        }
                        // Larghi uguali su una riga: con tre tasti "Email" non va più a capo a metà parola.
                        // La scheda partita allegata: va con WhatsApp e con l'email (l'SMS porta solo il testo).
                        val file = r.attachment?.takeIf { it.exists() }
                        if (file != null) Text(s.ntCardAttached(file.name), color = TsmColors.Ball, fontSize = 12.sp)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (wa != null) Chip(Icons.AutoMirrored.Filled.Chat, "WhatsApp", Modifier.weight(1f)) {
                                mark(r); open(if (file != null) whatsAppFile(context, wa, textOf(r), file) else whatsApp(wa, textOf(r)))
                            }
                            if (r.phone.isNotBlank()) Chip(Icons.Filled.Sms, "SMS", Modifier.weight(1f)) { mark(r); open(sms(listOf(r.phone), textOf(r))) }
                            if (r.email.isNotBlank()) Chip(Icons.Filled.Email, s.memberEmail, Modifier.weight(1f)) {
                                mark(r); open(if (file != null) emailFile(context, r.email, p.subject, textOf(r), file) else email(listOf(r.email), emptyList(), p.subject, textOf(r)))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Chip(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(10.dp), color = TsmColors.Surface) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = TsmColors.Ball)
            Text(" $label", color = TsmColors.TextMain, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, softWrap = false,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
    }
}
