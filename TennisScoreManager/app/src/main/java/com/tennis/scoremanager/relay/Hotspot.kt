// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.relay

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Rete del principale vista dal satellite: in attesa della conferma, collegata, persa, rifiutata dall'utente. */
enum class JoinState { WAITING, CONNECTED, LOST, REFUSED }

/** Nome e password della rete creata dal telefono principale. */
data class HotspotInfo(val ssid: String, val pass: String?)

/**
 * Permessi per creare la rete (principale) e per collegarsi da soli (satellite): da Android 13 "Dispositivi nelle
 * vicinanze" (senza posizione), prima la posizione.
 */
object HotspotPermissions {
    fun required(): Array<String> =
        if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES) else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

    fun granted(context: Context): Boolean =
        required().all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
}

/**
 * Telefono principale: hotspot "solo locale" di Android. Niente SIM né dati, nome e password casuali scelti dal
 * sistema; resta acceso finché non si chiama [stop] (o il sistema lo spegne: [onStopped]).
 */
class LocalHotspot(context: Context, private val onStopped: () -> Unit) {
    private val app = context.applicationContext
    private val wifi = app.getSystemService(WifiManager::class.java)
    private var reservation: WifiManager.LocalOnlyHotspotReservation? = null

    /** null se il telefono non può (hotspot normale già acceso, Wi-Fi che non regge le due cose, permesso negato). */
    @SuppressLint("MissingPermission")
    suspend fun start(): HotspotInfo? {
        if (wifi == null || !HotspotPermissions.granted(app)) return null
        stop()
        return withTimeoutOrNull(15_000) {
            suspendCancellableCoroutine { cont ->
                val cb = object : WifiManager.LocalOnlyHotspotCallback() {
                    override fun onStarted(r: WifiManager.LocalOnlyHotspotReservation) {
                        reservation = r
                        val info = info(r)
                        if (cont.isActive) cont.resume(info) else r.close()
                    }

                    override fun onStopped() {
                        reservation = null
                        onStopped()
                    }

                    override fun onFailed(reason: Int) {
                        Log.w(TAG, "hotspot non avviato: $reason")
                        if (cont.isActive) cont.resume(null)
                    }
                }
                runCatching { wifi.startLocalOnlyHotspot(cb, Handler(Looper.getMainLooper())) }
                    .onFailure {
                        Log.w(TAG, "hotspot non avviato", it)
                        if (cont.isActive) cont.resume(null)
                    }
            }
        }
    }

    fun stop() {
        runCatching { reservation?.close() }
        reservation = null
    }

    @Suppress("DEPRECATION")
    private fun info(r: WifiManager.LocalOnlyHotspotReservation): HotspotInfo? {
        if (Build.VERSION.SDK_INT >= 30) {
            val c = r.softApConfiguration
            val ssid = c.ssid ?: return null
            return HotspotInfo(ssid.removeSurrounding("\""), c.passphrase)
        }
        val c = r.wifiConfiguration ?: return null
        val ssid = c.SSID ?: return null
        return HotspotInfo(ssid.removeSurrounding("\""), c.preSharedKey?.removeSurrounding("\""))
    }

    companion object {
        private const val TAG = "LocalHotspot"
    }
}

/**
 * Satellite, da Android 10: chiede al sistema la rete del principale (compare una conferma una volta). La rete
 * resta solo per questa app: i dati mobili continuano a funzionare, e i collegamenti al principale vanno creati
 * con [network] (con un socket normale finirebbero sui dati mobili).
 */
class HotspotJoiner(context: Context, private val onChange: (JoinState) -> Unit) {
    private val cm = context.applicationContext.getSystemService(ConnectivityManager::class.java)
    private var callback: ConnectivityManager.NetworkCallback? = null

    @Volatile var network: Network? = null
        private set

    /** false se questo Android non lo permette (prima del 10): ci si collega a mano dalle impostazioni. */
    fun join(info: HotspotInfo): Boolean {
        if (Build.VERSION.SDK_INT < 29 || cm == null) return false
        release()
        val spec = WifiNetworkSpecifier.Builder().setSsid(info.ssid).apply { info.pass?.let { setWpa2Passphrase(it) } }.build()
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .setNetworkSpecifier(spec)
            .build()
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(n: Network) {
                network = n
                onChange(JoinState.CONNECTED)
            }

            override fun onLost(n: Network) {
                if (network == n) network = null
                onChange(JoinState.LOST)
            }

            override fun onUnavailable() {
                network = null
                onChange(JoinState.REFUSED)
            }
        }
        callback = cb
        onChange(JoinState.WAITING)
        return runCatching { cm.requestNetwork(request, cb) }.onFailure { Log.w(TAG, "rete non chiesta", it) }.isSuccess
    }

    /** Lascia la rete del principale (il telefono torna alla sua rete di prima). */
    fun release() {
        callback?.let { runCatching { cm?.unregisterNetworkCallback(it) } }
        callback = null
        network = null
    }

    companion object {
        private const val TAG = "HotspotJoiner"
    }
}
