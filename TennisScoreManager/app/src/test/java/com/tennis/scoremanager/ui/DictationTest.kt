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

    @Test fun noteDayAndTimeAreShort() {
        val at = java.util.Calendar.getInstance().apply { set(2026, 10, 26, 18, 30) }.timeInMillis
        assertEquals("26.11.26", com.tennis.scoremanager.ui.screens.noteDay(at))
        assertEquals("18:30", com.tennis.scoremanager.ui.screens.noteTime(at))
    }
}
