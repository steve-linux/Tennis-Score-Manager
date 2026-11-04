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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Il backup automatico della segreteria, una volta al giorno anche ad app chiusa: una copia sul telefono (le
 * ultime 14) e, se c'è, sul server del circolo; solo se qualcosa è cambiato dall'ultima.
 */
class OfficeBackupJob : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onStartJob(params: JobParameters): Boolean {
        val c = (application as TsmApp).controller
        scope.launch {
            runCatching { if (c.storage.officeBackupAuto) c.officeBackupNow(onlyIfChanged = true) }
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = true

    companion object {
        private const val JOB = 7306

        fun schedule(context: Context, on: Boolean) {
            val js = context.getSystemService(JobScheduler::class.java) ?: return
            if (!on) { js.cancel(JOB); return }
            if (js.getPendingJob(JOB) != null) return
            js.schedule(
                JobInfo.Builder(JOB, ComponentName(context, OfficeBackupJob::class.java))
                    .setPeriodic(24 * 3_600_000L).setPersisted(true).build(),
            )
        }
    }
}
