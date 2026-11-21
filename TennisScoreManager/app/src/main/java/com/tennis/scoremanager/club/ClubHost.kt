// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later
package com.tennis.scoremanager.club

import com.tennis.scoremanager.ClubFeature
import com.tennis.scoremanager.Navigator
import com.tennis.scoremanager.Role
import com.tennis.scoremanager.Screen
import com.tennis.scoremanager.data.ClubBook
import com.tennis.scoremanager.data.ClubServerConf
import com.tennis.scoremanager.data.ClubTournament
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
    val activeRole: MutableStateFlow<Role>

    /** Tornei, squadre e sfide: il planning ne guarda i campi occupati. */
    val competitions: ClubCompetitions

    /** Il server del circolo (facoltativo): le disdette delle prenotazioni online passano da qui. */
    val clubServer: MutableStateFlow<ClubServerConf>
    fun saveServer(f: (ClubServerConf) -> ClubServerConf)
    /** Manda un file alla casella della segreteria sul server del circolo [url]; true = arrivato. */
    suspend fun postToOffice(url: String, kind: String, text: String): Boolean
    /** Prende dalla casella della segreteria sul server i file arrivati (risultati, presenze, profili). */
    suspend fun fetchOfficeMail(conf: ClubServerConf)
    /** Da dove viene il file che si sta aprendo: l'indirizzo del server del circolo, o null (WhatsApp, file). */
    var incomingVia: String?

    // Le altre parti del circolo e le funzioni scelte (per il backup della segreteria).
    val school: ClubSchool
    val planning: ClubPlanning
    val clubFeatures: MutableStateFlow<Set<ClubFeature>>

    /** Un messaggio breve in basso sullo schermo. */
    fun toast(text: String)

    // Passaggio del profilo: chi lo manda, l'allenatore che lo riceve, le foto da ricaricare.
    fun me(): PlayerProfile?
    val coaching: com.tennis.scoremanager.coach.CoachArea
    val photosVersion: MutableStateFlow<Int>

    // Giocatori, registro delle quote e planning dei campi.
    val players: MutableStateFlow<List<PlayerProfile>>
    fun playerById(id: String?): PlayerProfile?
    fun refreshPlayers()
    fun savePlayer(p: PlayerProfile)
    /** Il nome di un iscritto al torneo (giocatore o coppia). */
    fun entryNameOf(t: ClubTournament, id: String?): String
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
