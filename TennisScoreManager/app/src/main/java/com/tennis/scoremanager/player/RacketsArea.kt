// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later
package com.tennis.scoremanager.player

import android.app.Application
import com.tennis.scoremanager.Screen
import com.tennis.scoremanager.data.Racket
import com.tennis.scoremanager.data.RacketStats
import com.tennis.scoremanager.data.RacketUse
import com.tennis.scoremanager.data.SetupData
import com.tennis.scoremanager.data.Storage
import com.tennis.scoremanager.data.Stringing
import com.tennis.scoremanager.model.Side
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Le racchette: elenco con le incordature, la racchetta proposta per gli allenamenti, le sessioni giocate con
 * ciascuna. Le sensazioni e la racchetta di una partita già giocata le scrive MatchController (riepilogo).
 * Quello che serve del resto dell'app è in [PlayerHost].
 */
class RacketsArea(
    host: PlayerHost,
    private val app: Application,
    private val storage: Storage,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
) : PlayerHost by host {
    /** Racchette con le incordature, in ordine di creazione. */
    val rackets = MutableStateFlow(storage.loadRackets())
    /** Racchetta proposta per gli allenamenti ("" = nessuna). */
    val workoutRacket = MutableStateFlow(storage.workoutRacket)

    fun racketById(id: String): Racket? = id.takeIf { it.isNotEmpty() }?.let { k -> rackets.value.firstOrNull { it.id == k } }

    /** Le racchette scelte in pagina 1, com'erano all'inizio della partita. */
    internal fun racketsFor(su: SetupData): Map<Side, RacketUse> = buildMap {
        racketById(su.p1Racket)?.let { put(Side.P1, it.use()) }
        racketById(su.p2Racket)?.let { put(Side.P2, it.use()) }
    }

    fun openRackets() {
        refreshHistory()
        refreshWorkouts()
        nav.push(Screen.RACKETS)
    }

    internal fun storeRackets(list: List<Racket>) {
        rackets.value = list
        scope.launch(io) { storage.saveRackets(list) }
    }

    fun newRacketId(): String = "r${System.currentTimeMillis()}"

    fun newStringingId(): String = "s${System.currentTimeMillis()}"

    /** Aggiunge o aggiorna una racchetta (stesso id). */
    fun saveRacket(r: Racket) {
        val list = rackets.value
        storeRackets(if (list.any { it.id == r.id }) list.map { if (it.id == r.id) r else it } else list + r)
    }

    /** Toglie la racchetta dall'elenco; le partite giocate la conservano (ne hanno una copia). */
    fun deleteRacket(id: String) {
        storeRackets(rackets.value.filter { it.id != id })
        updateSetup { it.copy(p1Racket = if (it.p1Racket == id) "" else it.p1Racket, p2Racket = if (it.p2Racket == id) "" else it.p2Racket) }
        if (workoutRacket.value == id) setWorkoutRacket("")
    }

    fun saveStringing(racketId: String, st: Stringing) {
        val r = racketById(racketId) ?: return
        val list = if (r.stringings.any { it.id == st.id }) r.stringings.map { if (it.id == st.id) st else it } else r.stringings + st
        saveRacket(r.copy(stringings = list.sortedBy { it.date }))
    }

    fun deleteStringing(racketId: String, stringingId: String) {
        val r = racketById(racketId) ?: return
        saveRacket(r.copy(stringings = r.stringings.filter { it.id != stringingId }))
    }

    fun setWorkoutRacket(id: String) {
        workoutRacket.value = id
        storage.workoutRacket = id
    }

    /** Partite dello storico e allenamenti, come sessioni con una racchetta. */
    fun racketSessions(): List<RacketStats.Session> = RacketStats.sessions(progress.played(), workouts.value)
}
