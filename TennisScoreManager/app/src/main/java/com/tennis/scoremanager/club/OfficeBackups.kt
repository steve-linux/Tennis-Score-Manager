// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later
package com.tennis.scoremanager.club

import android.app.Application
import com.tennis.scoremanager.ClubFeature
import com.tennis.scoremanager.data.Storage
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext

/**
 * I backup della segreteria: tutto quello del circolo in un file (sul telefono e, se c'è, sul server del circolo),
 * il backup automatico di ogni giorno e il ripristino. Quello che serve del resto dell'app è in [ClubHost].
 */
class OfficeBackups(
    host: ClubHost,
    private val app: Application,
    private val storage: Storage,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
) : ClubHost by host {
    /** Tutto quello della segreteria, adesso (vedi [com.tennis.scoremanager.data.OfficeSnapshot]). */
    fun officeSnapshot(): com.tennis.scoremanager.data.OfficeSnapshot = com.tennis.scoremanager.data.OfficeSnapshot(
        createdAt = System.currentTimeMillis(), app = com.tennis.scoremanager.BuildConfig.VERSION_NAME, club = storage.clubName,
        courts = planning.clubCourts.value, features = clubFeatures.value.map { it.name }.sorted(), players = players.value, book = clubBook.value,
        plan = courtPlan.value, tournaments = competitions.clubTournaments.value, courses = school.courses.value, teams = competitions.teams.value, ladders = competitions.ladders.value,
        serverRules = clubServer.value.rules, payInfo = clubServer.value.payInfo,
    )

    fun officeBackupBytes(): ByteArray = com.tennis.scoremanager.data.OfficeBackup.encode(officeSnapshot())

    fun officeBackupName(at: Long = System.currentTimeMillis()): String = com.tennis.scoremanager.data.OfficeBackup.fileName(storage.clubName, at)

    /** Le copie sul telefono, dalla più nuova. */
    fun officeBackups(): List<com.tennis.scoremanager.data.OfficeBackupFile> = com.tennis.scoremanager.data.OfficeBackup.list(storage.officeBackupDir)

    fun officeBackupFile(name: String): File? =
        com.tennis.scoremanager.data.OfficeBackup.safeName(name)?.let { File(storage.officeBackupDir, it) }?.takeIf { it.isFile }

    /**
     * Una copia sul telefono (e sul server del circolo, se c'è). Con [onlyIfChanged] si salta quando non è cambiato
     * niente dall'ultima. Restituisce il nome del file, o null se saltata. La rete gira fuori dal filo principale.
     */
    suspend fun officeBackupNow(onlyIfChanged: Boolean): String? {
        val snap = officeSnapshot()
        val print = com.tennis.scoremanager.data.OfficeBackup.fingerprint(snap)
        if (onlyIfChanged && print == storage.officeBackupPrint && officeBackups().isNotEmpty()) return null
        val bytes = com.tennis.scoremanager.data.OfficeBackup.encode(snap)
        val name = officeBackupName(snap.createdAt)
        withContext(io) { com.tennis.scoremanager.data.OfficeBackup.store(storage.officeBackupDir, name, bytes) }
        storage.officeBackupPrint = print
        storage.officeBackupAt = snap.createdAt
        val conf = clubServer.value
        if (conf.paired) storage.officeBackupServerError = withContext(io) {
            try { com.tennis.scoremanager.data.ClubServers.withServer(conf) { it.putBackup(name, bytes) }; "" } catch (e: com.tennis.scoremanager.data.ClubServerError) { e.code }
        }
        return name
    }

    /** Il backup automatico, una volta al giorno (anche ad app chiusa). */
    fun setOfficeBackupAuto(on: Boolean) {
        storage.officeBackupAuto = on
        com.tennis.scoremanager.service.DailyBackupJob.schedule(app, storage)
    }

    /**
     * Rimette la segreteria com'era nel backup [s]. Prima fa una copia di adesso (così si torna indietro). Il
     * collegamento al server resta quello di adesso: dal backup prende solo le regole.
     */
    suspend fun restoreOffice(s: com.tennis.scoremanager.data.OfficeSnapshot) {
        officeBackupNow(onlyIfChanged = false)
        players.value = s.players.sortedBy { it.name.lowercase() }
        clubBook.value = s.book
        courtPlan.value = s.plan
        competitions.clubTournaments.value = s.tournaments
        school.courses.value = s.courses
        competitions.teams.value = s.teams
        competitions.ladders.value = s.ladders
        storage.clubName = s.club
        planning.setClubCourts(s.courts)
        s.features?.let { names ->
            val set = names.mapNotNull { n -> ClubFeature.entries.firstOrNull { it.name == n } }.toSet()
            clubFeatures.value = set
            storage.clubFeatures = set.map { it.name }.toSet()
        }
        s.serverRules?.let { r -> saveServer { it.copy(rules = r, payInfo = s.payInfo) } }
        withContext(io) {
            storage.savePlayers(s.players); storage.saveClubBook(s.book); storage.saveCourtPlan(s.plan); storage.saveClubTournaments(s.tournaments)
            storage.saveCourses(s.courses); storage.saveTeams(s.teams); storage.saveLadders(s.ladders)
        }
        storage.officeBackupPrint = ""
    }
}
