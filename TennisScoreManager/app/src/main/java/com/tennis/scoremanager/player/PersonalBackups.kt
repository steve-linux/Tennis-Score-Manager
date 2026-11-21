// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later
package com.tennis.scoremanager.player

import android.app.Application
import com.tennis.scoremanager.data.Storage
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext

/**
 * I backup di tutti i dati (senza video) in Documenti › Tennis Score Manager › Backup: automatici ogni giorno, anche
 * protetti da password.
 * Quello che serve del resto dell'app è in [PlayerHost].
 */
class PersonalBackups(
    host: PlayerHost,
    private val app: Application,
    private val storage: Storage,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
) : PlayerHost by host {
    /** Il backup automatico di tutti i dati (senza video), una volta al giorno. */
    fun setPersonalBackupAuto(on: Boolean) {
        storage.personalBackupAuto = on
        com.tennis.scoremanager.service.DailyBackupJob.schedule(app, storage)
    }

    fun personalBackups(): List<File> = com.tennis.scoremanager.data.Backup.autoList(storage)

    /**
     * Una copia automatica di tutti i dati, senza video, in Documenti/Tennis Score Manager/Backup. Con [onlyIfChanged]
     * si salta se non è cambiato niente. Restituisce il file, o null se saltata o non riuscita.
     */
    /**
     * Mette (o toglie, con null) la password dei backup. Con la password nuova fa subito una copia protetta e, se
     * riesce, toglie le copie automatiche di prima senza password (avevano gli stessi dati in chiaro).
     */
    suspend fun setBackupPassword(pw: String?): Boolean {
        storage.backupPassword = pw
        if (pw == null) return true
        val made = personalBackupNow(onlyIfChanged = false) ?: return false
        withContext(io) { personalBackups().filter { it != made && !it.name.endsWith(com.tennis.scoremanager.data.Backup.LOCKED_EXT) }.forEach { it.delete() } }
        return true
    }

    suspend fun personalBackupNow(onlyIfChanged: Boolean): File? = withContext(io) {
        // Un'app appena installata non ha niente da salvare: una copia vuota diventerebbe la "più recente" in Documenti
        // proprio quando si vuole ripristinare quella di prima (dalla prima apertura). Le copie automatiche partono dopo.
        if (onlyIfChanged && !storage.onboarded) return@withContext null
        val folder = strings.strokeFolderName
        val print = com.tennis.scoremanager.data.Backup.fingerprint(app, storage, folder)
        if (onlyIfChanged && print == storage.personalBackupPrint && personalBackups().isNotEmpty()) return@withContext null
        val now = System.currentTimeMillis()
        runCatching { com.tennis.scoremanager.data.Backup.writeAuto(app, storage, com.tennis.scoremanager.BuildConfig.VERSION_NAME, folder, now, storage.backupPassword?.toCharArray()) }.getOrNull()?.also {
            storage.personalBackupPrint = print
            storage.lastBackupAt = now
        }
    }
}
