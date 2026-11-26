// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later
package com.tennis.scoremanager.coach

import com.tennis.scoremanager.Navigator
import com.tennis.scoremanager.Screen
import com.tennis.scoremanager.club.ClubSchool
import com.tennis.scoremanager.data.MatchOptions
import com.tennis.scoremanager.data.NoticePlan
import com.tennis.scoremanager.data.PlayerProfile
import com.tennis.scoremanager.data.ServeSession
import com.tennis.scoremanager.data.SetupData
import com.tennis.scoremanager.ui.Strings
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

/**
 * Quello che il lavoro dell'allenatore ([CoachArea]) chiede al resto dell'app, cioè a MatchController.
 * Una voce nuova qui è una dipendenza nuova.
 */
interface CoachHost {
    val strings: Strings
    val nav: Navigator
    val options: MutableStateFlow<MatchOptions>
    fun go(to: Screen)
    /** Un messaggio breve in basso sullo schermo. */
    fun toast(text: String)

    // Giocatori e allievi.
    fun me(): PlayerProfile?
    fun playerById(id: String?): PlayerProfile?
    fun refreshPlayers()
    fun savePlayers(changed: List<PlayerProfile>)
    /** Gli allenamenti dei colpi, per i progressi degli allievi. */
    val serveSessions: MutableStateFlow<List<ServeSession>>
    /** [notes]: con le note dell'allenatore (null = come l'ultima volta nel dialogo del report). */
    fun studentReportFile(p: PlayerProfile, days: Int?, pdf: Boolean, notes: Boolean? = null): File?
    /** Partita tra due allievi: Nuova partita già pronta. */
    fun updateSetup(transform: (SetupData) -> SetupData)

    // La scuola del circolo (corsi mandati all'allenatore) e gli avvisi.
    val school: ClubSchool
    /** La firma degli avvisi: il nome del circolo. */
    val clubSign: String
    fun openNotice(plan: NoticePlan)
    fun serverMail(to: String, kind: String, file: File)
    /** Da dove viene il file che si sta aprendo: l'indirizzo del server del circolo, o null (WhatsApp, file). */
    var incomingVia: String?
}
