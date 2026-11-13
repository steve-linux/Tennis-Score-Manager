// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.service

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import com.tennis.scoremanager.TsmApp
import com.tennis.scoremanager.data.Storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * I backup automatici, una volta al giorno anche ad app chiusa e solo se qualcosa è cambiato: quello della
 * segreteria (sul telefono e, se c'è, sul server del circolo) e quello di tutti i dati senza video (in Documenti).
 */
class DailyBackupJob : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onStartJob(params: JobParameters): Boolean {
        val c = (application as TsmApp).controller
        scope.launch {
            // La segreteria solo per chi fa il circolo: per gli altri sarebbe una copia vuota.
            runCatching { if (c.storage.officeBackupAuto && com.tennis.scoremanager.Role.CLUB in c.roles.value) c.officeBackupNow(onlyIfChanged = true) }
            runCatching { if (c.storage.personalBackupAuto) c.personalBackupNow(onlyIfChanged = true) }
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = true

    companion object {
        private const val JOB = 7306

        /** Acceso se almeno uno dei due backup automatici lo è. */
        fun schedule(context: Context, storage: Storage) {
            val js = context.getSystemService(JobScheduler::class.java) ?: return
            if (!storage.officeBackupAuto && !storage.personalBackupAuto) { js.cancel(JOB); return }
            if (js.getPendingJob(JOB) != null) return
            js.schedule(
                JobInfo.Builder(JOB, ComponentName(context, DailyBackupJob::class.java))
                    .setPeriodic(24 * 3_600_000L).setPersisted(true).build(),
            )
        }
    }
}
