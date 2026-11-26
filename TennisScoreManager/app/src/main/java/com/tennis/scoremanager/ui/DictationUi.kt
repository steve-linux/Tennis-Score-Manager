// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.tennis.scoremanager.MatchController

/** C'è un'app che fa la dettatura (quella di Google o un'altra)? Senza, il microfono non compare. */
fun canDictate(context: Context): Boolean =
    runCatching { context.packageManager.queryIntentActivities(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0).isNotEmpty() }.getOrDefault(false)

/** Aggiunge [add] in fondo a [text], con uno spazio o un a capo se serve. */
fun appendDictated(text: String, add: String): String {
    val a = add.trim().replaceFirstChar { it.uppercase() }
    if (a.isEmpty()) return text
    return when {
        text.isBlank() -> a
        text.endsWith("\n") || text.endsWith(" ") -> text + a
        text.trimEnd().endsWith(".") || text.trimEnd().endsWith("!") || text.trimEnd().endsWith("?") -> "$text $a"
        else -> "$text. $a"
    }
}

/**
 * La dettatura di Android (sui telefoni recenti anche senza rete) nella lingua dell'app: si parla e [onText] riceve il
 * testo. Restituisce la funzione che la avvia, o null se sul telefono non c'è nessuna app di dettatura.
 */
@Composable
fun rememberDictation(c: MatchController, onText: (String) -> Unit): (() -> Unit)? {
    val context = LocalContext.current
    val s = LocalStrings.current
    val lang = c.options.collectAsState().value.lang
    val available = remember { canDictate(context) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.takeIf { it.isNotBlank() }?.let(onText)
    }
    if (!available) return null
    return {
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang.locale.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PROMPT, s.dictPrompt)
        }
        runCatching { launcher.launch(i) }.onFailure { Toast.makeText(context, s.dictNoApp, Toast.LENGTH_LONG).show() }
    }
}

/** Il microfono nel campo di testo: il testo detto va in fondo a quello che c'è. */
@Composable
fun DictateIcon(c: MatchController, modifier: Modifier = Modifier, onText: (String) -> Unit) {
    val s = LocalStrings.current
    val start = rememberDictation(c, onText) ?: return
    IconButton(onClick = start, modifier = modifier) { Icon(Icons.Filled.Mic, s.dictMic, tint = TsmColors.Ball) }
}
