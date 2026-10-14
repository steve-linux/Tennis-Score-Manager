// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.ui

import android.view.KeyEvent
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import com.tennis.scoremanager.MatchController

/**
 * Un dialogo è una finestra a parte: mentre è aperto i tasti dei telecomandi arrivano a lui e non a MainActivity.
 * Messo nel contenuto di un dialogo che compare durante il gioco (fine partita, ordine di servizio nel doppio),
 * gira i tasti ai telecomandi: il doppio clic annulla anche da lì e nessun punto va perso.
 */
@Composable
fun RemoteKeysInDialog(c: MatchController) {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.parent as? DialogWindowProvider)?.window
        val original = window?.callback
        if (window != null && original != null) {
            window.callback = object : Window.Callback by original {
                override fun dispatchKeyEvent(event: KeyEvent): Boolean =
                    c.remotes.onKey(event) || original.dispatchKeyEvent(event)
            }
        }
        onDispose { if (window != null && original != null) window.callback = original }
    }
}
