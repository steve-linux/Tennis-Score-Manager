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
}
