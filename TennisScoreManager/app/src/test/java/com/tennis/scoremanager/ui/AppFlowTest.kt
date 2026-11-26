// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later
package com.tennis.scoremanager.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.printToString
import com.tennis.scoremanager.MainActivity
import com.tennis.scoremanager.MatchController
import com.tennis.scoremanager.Screen
import com.tennis.scoremanager.TsmApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit

/**
 * L'app vera (MainActivity, dati vuoti, lingua italiana) dentro Robolectric, sul PC e nella build di GitHub:
 * il percorso di ogni partita, toccando gli stessi pulsanti che si toccano sul telefono.
 */
@RunWith(RobolectricTestRunner::class)
// Senza Conscrypt: Robolectric lo metterebbe come provider TLS di tutta la JVM e cambierebbe l'HTTPS dei test che girano dopo (DeskTest).
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [35], qualifiers = "w411dp-h914dp-xxhdpi")
class AppFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private val c: MatchController get() = (rule.activity.application as TsmApp).controller

    @Test
    fun newMatchPointUndoSuspendResume() {
        rule.waitForIdle()
        assertEquals(Screen.HOME, c.screen.value)
        tap("Nuova partita")
        assertEquals(Screen.SETUP, c.screen.value)
        tap("Avanti")
        assertEquals(Screen.OPTIONS, c.screen.value)
        tap("Avanti")
        tap("Continua senza") // la posizione per il riepilogo: qui no
        assertEquals(Screen.TOSS, c.screen.value)
        tap("Avanti")
        assertEquals(Screen.START, c.screen.value)
        tap("Inizia partita")
        assertEquals(Screen.MATCH, c.screen.value)

        // Quattro punti al Giocatore 1: primo game. Annulla toglie l'ultimo punto e riporta il game a 40-0.
        repeat(4) { tap("Giocatore 1") }
        assertScore(g1 = 1, pt1 = 0)
        tap("Annulla")
        assertScore(g1 = 0, pt1 = 3)
        shown("40")

        // Sospesa e lasciata: resta tra le partite da riprendere, salvata.
        tap("Sospendi")
        shown("PARTITA SOSPESA")
        assertTrue(c.live.value!!.record.suspended)
        tap("Esci")
        tap("Torna al menu")
        assertEquals(Screen.HOME, c.screen.value)
        val saved = c.storage.loadUnfinished().single()
        assertEquals(3, saved.events.count { it is com.tennis.scoremanager.model.MatchEvent.Point })

        // Ripresa da Nuova partita (dove stanno le sospese): stesso punteggio, e il punto successivo chiude il game.
        tap("Nuova partita")
        assertEquals(Screen.SETUP, c.screen.value)
        tap("Riprendi partita")
        tap("Giocatore 1 vs")
        assertEquals(Screen.MATCH, c.screen.value)
        assertScore(g1 = 0, pt1 = 3)
        if (c.live.value!!.record.suspended) tap("Riprendi")
        tap("Giocatore 1")
        assertScore(g1 = 1, pt1 = 0)
    }

    private fun assertScore(g1: Int, pt1: Int) {
        assertNotNull(c.live.value)
        val st = c.live.value!!.state
        assertEquals("game", g1, st.g1)
        assertEquals("punti", pt1, st.pt1)
    }

    private fun shown(text: String) {
        assertTrue("non si vede «$text»", rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty())
    }

    /**
     * Tocca il primo elemento toccabile con quel testo. Il clic va come azione dell'elemento: in Robolectric il tocco
     * simulato su schermo non arriva ai pulsanti. Prima l'orologio va avanti: l'app ignora i tocchi troppo ravvicinati
     * e i dialoghi si armano dopo un attimo.
     */
    private fun tap(text: String) {
        ShadowLooper.idleMainLooper(2, TimeUnit.SECONDS)
        rule.mainClock.advanceTimeBy(2_000)
        val nodes = rule.onAllNodes(hasText(text, substring = true) and hasClickAction())
        if (nodes.fetchSemanticsNodes().isEmpty()) error("nessun pulsante «$text» in ${c.screen.value}:\n" + rule.onAllNodes(isRoot()).printToString(maxDepth = 40))
        nodes.onFirst().performSemanticsAction(SemanticsActions.OnClick)
        rule.waitForIdle()
    }
}
