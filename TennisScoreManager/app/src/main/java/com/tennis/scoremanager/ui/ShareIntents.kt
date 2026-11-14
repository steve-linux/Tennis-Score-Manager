// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.ui

import android.content.Context
import android.content.Intent

/**
 * WhatsApp con un file (la scheda partita) e il testo, dritto nella chat di quel numero ("jid"); senza WhatsApp si
 * sceglie l'app. Il permesso di leggere il file passa con l'intent.
 */
fun whatsAppFile(ctx: Context, number: String, text: String, file: java.io.File): Intent {
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
fun emailFile(ctx: Context, to: String, subject: String, text: String, file: java.io.File): Intent {
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


/** Il file col testo, a chi si vuole (senza un numero a cui mandarlo dritto): si sceglie l'app. */
fun shareFile(ctx: Context, text: String, file: java.io.File): Intent {
    val uri = androidx.core.content.FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
    return Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
        type = com.tennis.scoremanager.data.MatchCards.MIME
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, text)
        clipData = android.content.ClipData.newRawUri("", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }, null)
}
