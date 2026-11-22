// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later
package com.tennis.scoremanager.ui

import android.app.Application
import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performSemanticsAction
import com.tennis.scoremanager.HomeTab
import org.junit.Assert.assertEquals
import androidx.test.core.app.ApplicationProvider
import com.tennis.scoremanager.MatchController
import com.tennis.scoremanager.Role
import com.tennis.scoremanager.Screen
import com.tennis.scoremanager.ble.BleManager
import com.tennis.scoremanager.ble.HeartRateManager
import com.tennis.scoremanager.data.Storage
import com.tennis.scoremanager.voice.Announcer
import com.tennis.scoremanager.voice.VoicePack
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

/**
 * Avvio dell'app e tutte le schermate, per ogni ruolo, con un MatchController creato nel test sopra le preferenze
 * scelte prima dell'avvio (MainActivity creerebbe il suo con le preferenze vuote).
 */
@RunWith(RobolectricTestRunner::class)
// Senza Conscrypt: Robolectric lo metterebbe come provider TLS di tutta la JVM e cambierebbe l'HTTPS dei test che girano dopo (DeskTest).
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [35], qualifiers = "w411dp-h914dp-xxhdpi", application = Application::class)
class StartupTest {
    @get:Rule val rule = createComposeRule()

    private val app: Application = ApplicationProvider.getApplicationContext()

    private fun controller(role: Role, tv: Boolean): MatchController {
        app.getSharedPreferences("tsm", Context.MODE_PRIVATE).edit()
            .putString("active_role", role.name)
            .apply { if (tv) putString("tv", """{"enabled":true}""") }
            .commit()
        val voice = VoicePack(app)
        val ble = BleManager(app)
        return MatchController(app, Storage(app), ble, HeartRateManager(app, ble), voice, Announcer(app, voice))
    }

    private fun show(c: MatchController) = rule.setContent {
        val options by c.options.collectAsState()
        TsmTheme { CompositionLocalProvider(LocalStrings provides stringsFor(options.lang)) { AppRoot(c) } }
    }

    /**
     * Apre ogni schermata e ogni scheda della prima schermata nel ruolo dato, col tabellone TV acceso: all'avvio
     * publishTv gira già dentro MatchController, prima che le aree siano tutte create (il crash del 21 nov 2026).
     * Dati vuoti: le schermate che mostrano un elemento scelto (allievo, corso...) devono reggere anche senza.
     */
    private fun openAll(role: Role) {
        val c = controller(role, tv = true)
        show(c)
        rule.waitForIdle()
        for (tab in role.tabs) {
            c.home(tab)
            rule.waitForIdle()
        }
        for (s in Screen.entries) {
            try {
                c.go(s)
                rule.waitForIdle()
            } catch (e: Throwable) {
                throw AssertionError("$role: la schermata $s non si apre", e)
            }
            c.home()
            rule.waitForIdle()
        }
    }

    @Test fun player() = openAll(Role.PLAYER)

    @Test fun coach() = openAll(Role.COACH)

    @Test fun club() = openAll(Role.CLUB)

    /** Il pulsante del ruolo in alto nella prima schermata: un tocco apre i ruoli, un altro passa all'Allenatore. */
    @Test fun roleButtonSwitchesRole() {
        val c = controller(Role.PLAYER, tv = false)
        show(c)
        rule.waitForIdle()
        val s = stringsFor(c.options.value.lang)
        rule.onNode(hasContentDescription("${s.roleSwitch}: ${s.moreProfile}")).performSemanticsAction(SemanticsActions.OnClick)
        rule.waitForIdle()
        rule.onNode(hasText(s.roleCoach), useUnmergedTree = true).assertExists()
        rule.onAllNodes(hasText(s.roleCoach))[0].performSemanticsAction(SemanticsActions.OnClick)
        rule.waitForIdle()
        assertEquals(Role.COACH, c.activeRole.value)
        assertEquals(HomeTab.STUDENTS, c.homeTab.value)
        rule.onNode(hasContentDescription("${s.roleSwitch}: ${s.roleCoach}")).assertExists()
    }
}
