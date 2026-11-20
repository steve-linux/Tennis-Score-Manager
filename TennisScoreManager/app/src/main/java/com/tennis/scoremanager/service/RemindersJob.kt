// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.service

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
import android.os.Build
import androidx.core.app.NotificationCompat
import com.tennis.scoremanager.MainActivity
import com.tennis.scoremanager.R
import com.tennis.scoremanager.TsmApp
import com.tennis.scoremanager.data.Reminders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Ogni ora, se i promemoria sono accesi: guarda se c'è qualcosa da ricordare (vedi [Reminders]) e lo notifica. */
class RemindersJob : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onStartJob(params: JobParameters): Boolean {
        val c = (application as TsmApp).controller
        scope.launch {
            runCatching { post(this@RemindersJob, c) }
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = true

    companion object {
        private const val JOB = 7307
        private const val CHANNEL = "reminders"
        private const val ID = 6100
        const val EXTRA_TARGET = "tsm.reminder"

        fun schedule(context: Context, on: Boolean) {
            val js = context.getSystemService(JobScheduler::class.java) ?: return
            if (!on) { js.cancel(JOB); return }
            if (js.getPendingJob(JOB) != null) return
            js.schedule(JobInfo.Builder(JOB, ComponentName(context, RemindersJob::class.java)).setPeriodic(3_600_000L).setPersisted(true).build())
        }

        /** Avviso di qualcosa arrivato dal server del circolo (convocazione, scheda, risultato registrato). */
        fun notifyInbox(context: Context, title: String, text: String) {
            if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return
            val nm = context.getSystemService(NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CHANNEL, title, NotificationManager.IMPORTANCE_DEFAULT))
            val open = PendingIntent.getActivity(context, ID - 1,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra(EXTRA_TARGET, "inbox"),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            nm.notify(ID - 1 - (title.hashCode() and 0xff), NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_stat_tennis)
                .setContentTitle(title).setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text)).setContentIntent(open).setAutoCancel(true).build())
        }

        /** I promemoria di adesso, uno per notifica; ciascuno una volta sola. */
        fun post(context: Context, c: com.tennis.scoremanager.MatchController) {
            if (!c.storage.remindersOn) return
            if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return
            val s = c.strings
            val shown = c.storage.remindersShown
            val items = Reminders.due(System.currentTimeMillis(), c.activeRole.value == com.tennis.scoremanager.Role.COACH, c.coaching.coachBook.value, c.receivedCards.value, shown) { id -> c.playerById(id)?.name ?: "—" }
            if (items.isEmpty()) return
            val nm = context.getSystemService(NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CHANNEL, s.rmChannel, NotificationManager.IMPORTANCE_DEFAULT))
            for (it in items) {
                val title = when (it.kind) {
                    Reminders.Kind.TOMORROW -> s.rmTomorrow(it.count)
                    Reminders.Kind.SEND -> s.rmSend
                    Reminders.Kind.MATCH -> s.rmMatch
                }
                val open = PendingIntent.getActivity(context, ID + (it.key.hashCode() and 0xfff),
                    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra(EXTRA_TARGET, it.target),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                nm.notify(ID + (it.key.hashCode() and 0xfff), NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_stat_tennis)
                    .setContentTitle(title).setContentText(it.text).setStyle(NotificationCompat.BigTextStyle().bigText(it.text))
                    .setContentIntent(open).setAutoCancel(true).build())
            }
            // Le chiavi vecchie si buttano: bastano le ultime 60.
            c.storage.remindersShown = (shown + items.map { it.key }).toList().takeLast(60).toSet()
        }
    }
}
