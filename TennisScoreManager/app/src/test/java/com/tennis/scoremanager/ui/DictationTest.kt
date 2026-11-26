// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class DictationTest {
    @Test fun dictatedTextJoinsWhatIsThere() {
        assertEquals("Servizio più sciolto", appendDictated("", "servizio più sciolto"))
        assertEquals("Bene il dritto. Rovescio da rivedere", appendDictated("Bene il dritto", " rovescio da rivedere "))
        assertEquals("Bene il dritto. Rovescio", appendDictated("Bene il dritto.", "rovescio"))
        assertEquals("Riga\nNuova", appendDictated("Riga\n", "nuova"))
        assertEquals("Testo", appendDictated("Testo", "  "))
    }

    @Test fun dictatedNoteGoesOnItsOwnLineWithTheTime() {
        val stamp = "📅 26.11.26  🕐 18:10"
        assertEquals("$stamp\nServizio più sciolto", appendDictatedLine("", "servizio più sciolto", stamp))
        // La più recente in cima.
        assertEquals("$stamp\nRovescio da rivedere\nPrima nota", appendDictatedLine("\nPrima nota\n\n", " rovescio da rivedere ", stamp))
        assertEquals("Testo", appendDictatedLine("Testo", "  ", stamp))
        // Data corta e ora, uguali in ogni lingua.
        val at = java.util.Calendar.getInstance().apply { set(2026, 10, 26, 18, 30) }.timeInMillis
        assertEquals("📅 26.11.26  🕐 18:30", com.tennis.scoremanager.ui.screens.noteStamp(at))
    }
}
