// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later
package com.tennis.scoremanager.player

import com.tennis.scoremanager.LiveMatch
import com.tennis.scoremanager.Navigator
import com.tennis.scoremanager.data.MatchOptions
import com.tennis.scoremanager.data.PlayerProfile
import com.tennis.scoremanager.data.WorkoutRecord
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
}
