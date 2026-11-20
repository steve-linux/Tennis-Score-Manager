// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later
package com.tennis.scoremanager.club

import com.tennis.scoremanager.Navigator
import com.tennis.scoremanager.Screen
import com.tennis.scoremanager.data.ClubBook
import com.tennis.scoremanager.data.CourtPlan
import com.tennis.scoremanager.data.MatchOptions
import com.tennis.scoremanager.data.NoticePlan
import com.tennis.scoremanager.data.PlayerProfile
import com.tennis.scoremanager.data.SetupData
import com.tennis.scoremanager.ui.Strings
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

/**
 * Quello che le parti del circolo (vedi [ClubCompetitions]) chiedono al resto dell'app, cioè a MatchController.
 * Tenerlo corto rende chiaro da cosa dipendono; una voce nuova qui è una dipendenza nuova.
 */
interface ClubHost {
    val strings: Strings
    val nav: Navigator
    val options: MutableStateFlow<MatchOptions>

    // Giocatori, registro delle quote e planning dei campi.
    val players: MutableStateFlow<List<PlayerProfile>>
    fun playerById(id: String?): PlayerProfile?
    fun refreshPlayers()
    val clubBook: MutableStateFlow<ClubBook>
    fun saveClubBook(book: ClubBook)
    val courtPlan: MutableStateFlow<CourtPlan>

    // Nuova partita col segnapunti.
    fun updateSetup(transform: (SetupData) -> SetupData)
    fun updateOptions(transform: (MatchOptions) -> MatchOptions)
    fun go(to: Screen)

    // Tabellone TV.
    val tvClubTournament: MutableStateFlow<String?>
    fun showClubOnTv(id: String?)
    fun publishTv()

    // Avvisi ai giocatori e casella sul server del circolo.
    /** La firma degli avvisi: il nome del circolo. */
    val clubSign: String
    fun openNotice(plan: NoticePlan)
    fun serverMail(to: String, kind: String, file: File)
    fun challengeCardFile(ladderId: String, challengeId: String): File?
}
