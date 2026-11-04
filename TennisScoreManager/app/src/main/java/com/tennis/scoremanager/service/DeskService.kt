// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.tennis.scoremanager.MainActivity
import com.tennis.scoremanager.R
import com.tennis.scoremanager.TsmApp

/** Tiene vivo il server della Segreteria da PC anche a schermo spento, con la notifica sempre visibile e il tasto per spegnerlo. */
class DeskService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    /** App tolta dalle recenti: niente server che resta acceso a insaputa di chi l'ha chiusa. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        (application as TsmApp).controller.desk.stop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val c = (application as TsmApp).controller
        if (intent?.action == ACTION_STOP) {
            c.desk.stop()
            return START_NOT_STICKY
        }
        val s = c.strings
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CHANNEL, s.deskTitle, NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 2, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 3, Intent(this, DeskService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_tennis)
            .setContentTitle(s.deskTitle)
            .setContentText(s.deskNotif)
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, s.deskStop, stop)
            .build()
        try {
            ServiceCompat.startForeground(this, 2, notification, if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0)
        } catch (e: Exception) {
            Log.w("DeskService", "Servizio in primo piano non avviato", e)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    companion object {
        private const val CHANNEL = "desk"
        private const val ACTION_STOP = "com.tennis.scoremanager.DESK_STOP"

        fun start(ctx: Context) {
            runCatching { ContextCompat.startForegroundService(ctx, Intent(ctx, DeskService::class.java)) }.onFailure { Log.w("DeskService", "start", it) }
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, DeskService::class.java))
        }
    }
}
