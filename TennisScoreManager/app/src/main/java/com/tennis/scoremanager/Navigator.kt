// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * La strada fatta tra le schermate: "Indietro" torna a quella da cui si è arrivati, non a una scelta fissa. I passi di
 * una partita nuova (chi gioca, regole, sorteggio, inizio) spariscono quando si comincia a giocare: finita o lasciata,
 * la partita torna da dove era partita (la prima schermata, un torneo, una squadra, la classifica a sfida...).
 */
class Navigator(val screen: MutableStateFlow<Screen> = MutableStateFlow(Screen.HOME)) {
    private val stack = ArrayList<Screen>()

    /** Le schermate da cui si è arrivati, dalla prima. */
    val trail: List<Screen> get() = stack.toList()

    /**
     * Va a [to]; Indietro tornerà qui. Una schermata che è già sulla strada non si ripete: si torna a quel punto (la
     * strada non gira in tondo).
     */
    fun push(to: Screen) {
        val from = screen.value
        if (from == to) return
        val i = stack.indexOf(to)
        if (i >= 0) while (stack.size > i) stack.removeAt(stack.lastIndex) else stack += from
        screen.value = to
    }

    /** [to] prende il posto di quella di adesso, che Indietro non ritrova (la partita finita lascia il posto al riepilogo). */
    fun replace(to: Screen) {
        screen.value = to
    }

    /** Si gioca: via i passi della partita nuova, così Indietro e "Fatto" tornano da dove si era partiti. */
    fun play(to: Screen) {
        val from = screen.value
        stack.removeAll { it in STEPS }
        if (from != to && from !in STEPS) {
            stack.remove(to)
            stack += from
        }
        screen.value = to
    }

    /** Indietro: la schermata di prima, o la prima schermata. */
    fun back() {
        screen.value = if (stack.isEmpty()) Screen.HOME else stack.removeAt(stack.lastIndex)
    }

    /** La prima schermata, da capo. */
    fun home() {
        stack.clear()
        screen.value = Screen.HOME
    }

    companion object {
        /** I passi di una partita nuova, prima del gioco. */
        val STEPS = setOf(Screen.SETUP, Screen.OPTIONS, Screen.TOSS, Screen.START)
    }
}
