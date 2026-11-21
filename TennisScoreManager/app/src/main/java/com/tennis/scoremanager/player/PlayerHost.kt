// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later
package com.tennis.scoremanager.player

import com.tennis.scoremanager.LiveMatch
import com.tennis.scoremanager.Navigator
import com.tennis.scoremanager.ble.HeartRateManager
import com.tennis.scoremanager.data.HeartProfile
import com.tennis.scoremanager.data.MatchOptions
import com.tennis.scoremanager.data.MatchRecord
import com.tennis.scoremanager.data.PlayerProfile
import com.tennis.scoremanager.data.SetupData
import com.tennis.scoremanager.data.WorkoutRecord
import com.tennis.scoremanager.model.Side
import com.tennis.scoremanager.ui.Strings
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Quello che le parti di chi gioca (vedi [ProgressArea]) chiedono al resto dell'app, cioè a MatchController.
 * Una voce nuova qui è una dipendenza nuova.
 */
interface PlayerHost {
    val strings: Strings
    val nav: Navigator
    val options: MutableStateFlow<MatchOptions>
    fun playerById(id: String?): PlayerProfile?
    /** Chi usa l'app nelle statistiche: il suo giocatore, o il suo nome. */
    fun myKey(): String

    // Lo storico delle partite finite e gli allenamenti con la fascia.
    val history: MutableStateFlow<List<LiveMatch>>
    fun refreshHistory()
    val workouts: MutableStateFlow<List<WorkoutRecord>>
    fun refreshWorkouts()
    /** Le partite sospese, da ricaricare dopo un trasferimento. */
    fun refreshSaved()
    /** Aggiunge allo storico partite arrivate da fuori: (finite aggiunte, sospese aggiunte). */
    fun importRecords(recs: List<MatchRecord>, info: List<PlayerProfile> = emptyList()): Pair<Int, Int>
    /** Cresce quando cambiano le foto dei giocatori (le schermate le ricaricano). */
    val photosVersion: MutableStateFlow<Int>

    /** I progressi: le partite giocate, per le sessioni di ciascuna racchetta. */
    val progress: ProgressArea
    /** Nuova partita (togliendo una racchetta la si toglie anche da lì). */
    fun updateSetup(transform: (SetupData) -> SetupData)
    /** Un messaggio breve in basso sullo schermo. */
    fun toast(text: String)
    /** Il riepilogo aperto (le foto ricordo vanno a quella partita). */
    val summary: MutableStateFlow<LiveMatch?>
    /** Ridisegna il tabellone TV (le foto dei giocatori ci compaiono). */
    fun publishTv()

    // Allenamento con la fascia.
    fun me(): PlayerProfile?
    /** Le fasce cardio collegate. */
    val heart: HeartRateManager
    /** Età, peso e sesso per lato, per calorie e zone del battito. */
    val heartProfiles: MutableStateFlow<Map<Side, HeartProfile>>
    fun heartProfileOf(p: PlayerProfile?): HeartProfile?
    fun setHeartProfile(side: Side, p: HeartProfile?)
    /** Serve il servizio in primo piano (braccialetti, tabellone, fascia, allenamento)? */
    fun needsService(): Boolean
    val racketArea: RacketsArea
}
