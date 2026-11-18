// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigatorTest {

    @Test fun indietroTornaDaDoveSiEArrivati() {
        val n = Navigator()
        n.push(Screen.CLUB_TOURNAMENTS)
        n.push(Screen.CLUB_TOURNAMENT)
        n.push(Screen.STUDENT)
        n.back()
        assertEquals(Screen.CLUB_TOURNAMENT, n.screen.value)
        n.back()
        n.back()
        assertEquals(Screen.HOME, n.screen.value)
        n.back()
        assertEquals("dalla prima schermata non si va oltre", Screen.HOME, n.screen.value)
    }

    @Test fun laPartitaTornaDaDoveEraPartita() {
        val n = Navigator()
        n.push(Screen.CLUB_TOURNAMENTS)
        n.push(Screen.CLUB_TOURNAMENT)
        // Gioca col segnapunti: i tre passi, poi la partita.
        n.push(Screen.SETUP)
        n.push(Screen.OPTIONS)
        n.push(Screen.TOSS)
        n.push(Screen.START)
        n.play(Screen.MATCH)
        assertEquals(listOf(Screen.HOME, Screen.CLUB_TOURNAMENTS, Screen.CLUB_TOURNAMENT), n.trail)
        // Finita: il riepilogo prende il posto della partita, "Fatto" torna al torneo.
        n.replace(Screen.SUMMARY)
        n.back()
        assertEquals(Screen.CLUB_TOURNAMENT, n.screen.value)
    }

    @Test fun riprendereDaUnaSchermataCiTorna() {
        val n = Navigator()
        n.push(Screen.HISTORY)
        n.play(Screen.MATCH)
        n.back()
        assertEquals(Screen.HISTORY, n.screen.value)
    }

    @Test fun nuovaPartitaDallaPartitaTornaAllOrigine() {
        val n = Navigator()
        n.push(Screen.SETUP)
        n.push(Screen.TOSS)
        n.push(Screen.START)
        n.play(Screen.MATCH)
        n.replace(Screen.SETUP)
        n.back()
        assertEquals(Screen.HOME, n.screen.value)
    }

    @Test fun unaSchermataGiaSullaStradaNonSiRipete() {
        val n = Navigator()
        n.push(Screen.STUDENTS)
        n.push(Screen.STUDENT)
        n.push(Screen.PLAYERS)
        n.push(Screen.STUDENT)
        assertEquals(listOf(Screen.HOME, Screen.STUDENTS), n.trail)
        n.home()
        assertEquals(emptyList<Screen>(), n.trail)
    }
}
