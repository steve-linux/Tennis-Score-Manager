package com.tennis.scoremanager.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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

/**
 * Servizio in primo piano durante la partita con i braccialetti: tiene attivo il processo
 * (Bluetooth, voce e cronometri) anche con lo schermo spento o l'app in secondo piano.
 */
class MatchService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    /** App tolta dalle recenti durante la partita: si chiude come con "Esci" (partita salvata tra le sospese). */
    override fun onTaskRemoved(rootIntent: Intent?) {
        (application as TsmApp).controller.onTaskRemoved { stopSelf() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Partita in corso", NotificationManager.IMPORTANCE_LOW))
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_tennis)
            .setContentTitle("Tennis Score Manager")
            .setContentText("Partita in corso · braccialetti attivi")
            .setOngoing(true)
            .setContentIntent(open)
            .build()
        try {
            ServiceCompat.startForeground(
                this, 1, notification,
                if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0,
            )
        } catch (e: Exception) {
            Log.w("MatchService", "Servizio in primo piano non avviato", e)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    companion object {
        private const val CHANNEL = "match"

        fun start(ctx: Context) {
            // Il tipo "connectedDevice" richiede il permesso Bluetooth: senza, Android chiuderebbe l'app.
            if (Build.VERSION.SDK_INT >= 31 &&
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
            ) return
            runCatching { ContextCompat.startForegroundService(ctx, Intent(ctx, MatchService::class.java)) }
                .onFailure { Log.w("MatchService", "start", it) }
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, MatchService::class.java))
        }
    }
}
