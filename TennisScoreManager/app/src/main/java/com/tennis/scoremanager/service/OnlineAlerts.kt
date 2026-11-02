// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.tennis.scoremanager.MainActivity
import com.tennis.scoremanager.R
import com.tennis.scoremanager.TsmApp
import com.tennis.scoremanager.ui.Strings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** La notifica alla segreteria: prenotazioni online arrivate e disdette. Toccandola si apre il planning su quel giorno. */
object OnlineAlerts {
    private const val CHANNEL = "online_bookings"
    private const val ID = 7301
    /** Il giorno da aprire nel planning (millisecondi), nell'intent della notifica. */
    const val EXTRA_PLAN_DAY = "tsm.planDay"

    fun allowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun post(context: Context, s: Strings, added: List<String>, cancelled: List<String>, day: Long?) {
        if (added.isEmpty() && cancelled.isEmpty() || !allowed(context)) return
        val nm = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CHANNEL, s.srvAlertChannel, NotificationManager.IMPORTANCE_DEFAULT))
        val lines = added.map { s.srvAlertNew(it) } + cancelled.map { s.srvAlertGone(it) }
        val open = PendingIntent.getActivity(
            context, ID,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .apply { day?.let { putExtra(EXTRA_PLAN_DAY, it) } },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_tennis)
            // Chiusa si legge subito cosa è successo; aperta, tutte le righe.
            .setContentTitle(if (lines.size == 1) s.srvAlertChannel else s.srvAlertMany(lines.size))
            .setContentText(lines.first())
            .setStyle(if (lines.size == 1) NotificationCompat.BigTextStyle().bigText(lines.first()) else NotificationCompat.InboxStyle().also { st -> lines.take(6).forEach { st.addLine(it) } })
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify(ID, n)
    }
}

/**
 * Ogni 15 minuti circa (quando c'è rete), anche ad app chiusa: manda il planning al server e prende le
 * prenotazioni online, come fa l'app aperta; le novità arrivano come notifica. Solo con un server abbinato e le
 * notifiche accese nel dialogo del server.
 */
class ServerCheckJob : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onStartJob(params: JobParameters): Boolean {
        val c = (application as TsmApp).controller
        scope.launch {
            c.syncServerNow()
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = true

    companion object {
        private const val JOB = 7302

        /** Accende o spegne il controllo periodico. */
        fun schedule(context: Context, on: Boolean) {
            val js = context.getSystemService(JobScheduler::class.java) ?: return
            if (!on) { js.cancel(JOB); return }
            if (js.getPendingJob(JOB) != null) return
            js.schedule(
                JobInfo.Builder(JOB, ComponentName(context, ServerCheckJob::class.java))
                    .setPeriodic(15 * 60_000L)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPersisted(true)
                    .build(),
            )
        }
    }
}
