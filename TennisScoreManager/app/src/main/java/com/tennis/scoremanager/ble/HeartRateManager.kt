// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.tennis.scoremanager.model.Side
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

enum class StrapState { IDLE, CONNECTING, READY }

data class FoundStrap(val address: String, val name: String, val rssi: Int, val lastSeen: Long = 0L)

/**
 * [bpm] null se la fascia non manda niente da qualche secondo; [contact] false = non tocca la pelle (asciutta, allentata).
 * [rssi] segnale in dBm (più vicino a 0 = più forte), [received] secondi con almeno una misura nell'ultimo minuto (%):
 * dicono fin dove arriva la fascia.
 */
data class StrapInfo(
    val address: String,
    val name: String,
    val state: StrapState = StrapState.IDLE,
    val bpm: Int? = null,
    val contact: Boolean? = null,
    val battery: Int? = null,
    val rssi: Int? = null,
    val received: Int? = null,
    /** Il battito arriva dal telefono satellite (questo telefono non sente la fascia). */
    val relayed: Boolean = false,
)

/**
 * Fasce cardio Bluetooth (servizio standard 0x180D), una per lato. Stesso schema dei braccialetti ([BleManager]),
 * ma la fascia manda solo: nessun messaggio da scriverle, nessuna conferma.
 */
@SuppressLint("MissingPermission")
class HeartRateManager(context: Context, private val ble: BleManager) {

    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val adapter: BluetoothAdapter? = app.getSystemService(BluetoothManager::class.java)?.adapter

    private val _found = MutableStateFlow<List<FoundStrap>>(emptyList())
    val found: StateFlow<List<FoundStrap>> = _found
    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning
    private val _straps = MutableStateFlow<Map<Side, StrapInfo>>(emptyMap())
    val straps: StateFlow<Map<Side, StrapInfo>> = _straps
    private val _readings = MutableSharedFlow<Pair<Side, HeartMeasurement>>(extraBufferCapacity = 32)
    /** Ogni misura arrivata (circa una al secondo per fascia). */
    val readings: SharedFlow<Pair<Side, HeartMeasurement>> = _readings

    private val links = mutableMapOf<Side, StrapLink>()
    private var autoScan = false
    private var scanRunning = false
    private var scanStartJob: Job? = null
    private var scanStopJob: Job? = null
    private var scanJob: Job? = null
    // Android permette 5 avvii di ricerca ogni 30 s per tutta l'app: 4 li tengono i braccialetti, 1 le fasce.
    private val scanThrottle = ScanThrottle(maxStarts = 1)

    init {
        ContextCompat.registerReceiver(app, object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                when (i?.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                    BluetoothAdapter.STATE_ON -> {
                        links.values.forEach { it.connect() }
                        if (autoScan) ensureScan()
                    }
                    BluetoothAdapter.STATE_OFF -> {
                        stopScanNow()
                        links.values.forEach { it.onAdapterOff() }
                    }
                }
            }
        }, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        // Un battito vecchio non si mostra: dopo [STALE_MS] senza misure la fascia è "in attesa". Intanto si
        // aggiornano segnale e misure ricevute.
        scope.launch {
            while (true) {
                delay(2_000)
                links.values.forEach { it.readRssi() }
                if (links.isNotEmpty()) publish()
            }
        }
    }

    private val isEnabled: Boolean get() = adapter?.isEnabled == true

    private fun granted(p: String) = ContextCompat.checkSelfPermission(app, p) == PackageManager.PERMISSION_GRANTED

    /** Ricerca continua finché [on] (la sezione delle fasce è aperta). */
    fun setAutoScan(on: Boolean) {
        autoScan = on
        scanStopJob?.cancel()
        if (on) {
            ensureScan()
        } else {
            scanStopJob = scope.launch {
                delay(1_500)
                stopScanNow()
            }
        }
    }

    private fun ensureScan(retryInMs: Long = 0) {
        if (!autoScan || scanRunning || scanStartJob?.isActive == true) return
        if (!ble.canScan() || !isEnabled || adapter?.bluetoothLeScanner == null) {
            _scanning.value = false
            return
        }
        _scanning.value = true
        scanStartJob = scope.launch {
            delay(maxOf(retryInMs, scanThrottle.delayBeforeStart(SystemClock.elapsedRealtime())))
            startScanNow()
        }
    }

    private fun startScanNow() {
        val scanner = adapter?.bluetoothLeScanner
        if (!autoScan || scanner == null || !ble.canScan() || !isEnabled) {
            _scanning.value = false
            return
        }
        val filters = listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(HeartRateProtocol.SERVICE)).build())
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        val ok = runCatching { scanner.startScan(filters, settings, scanCallback) }.isSuccess
        scanThrottle.recordStart(SystemClock.elapsedRealtime())
        if (!ok) {
            _scanning.value = false
            ensureScan(retryInMs = 5_000)
            return
        }
        scanRunning = true
        _scanning.value = true
        scanJob?.cancel()
        scanJob = scope.launch {
            while (true) {
                delay(2_000)
                val now = SystemClock.elapsedRealtime()
                _found.update { list -> list.filter { now - it.lastSeen < 10_000 } }
            }
        }
    }

    private fun stopScanNow() {
        scanStartJob?.cancel()
        scanJob?.cancel()
        if (scanRunning) runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
        scanRunning = false
        _scanning.value = false
        _found.value = emptyList()
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val addr = result.device.address
            val name = result.scanRecord?.deviceName ?: runCatching { result.device.name }.getOrNull() ?: addr
            val strap = FoundStrap(addr, name, result.rssi, SystemClock.elapsedRealtime())
            _found.update { list -> (list.filterNot { it.address == addr } + strap).sortedBy { it.name } }
            scope.launch { links.values.firstOrNull { it.address == addr }?.onSeen() }
        }

        override fun onScanFailed(errorCode: Int) {
            scope.launch {
                Log.w(TAG, "ricerca fallita: $errorCode")
                if (errorCode == 1) return@launch  // già avviata
                scanJob?.cancel()
                scanRunning = false
                _scanning.value = false
                ensureScan(retryInMs = 30_000)
            }
        }
    }

    /** Associa (o toglie, con address null) la fascia a un lato. Una fascia non può stare su due lati. */
    fun assign(side: Side, address: String?, name: String?) {
        links.remove(side)?.close()
        if (address != null) {
            links.entries.firstOrNull { it.value.address == address }?.let { links.remove(it.key)?.close() }
            links[side] = StrapLink(side, address, name ?: address).also { it.connect() }
        }
        publish()
    }

    fun swapSides() {
        val a = links.remove(Side.P1)
        val b = links.remove(Side.P2)
        a?.let { it.side = Side.P2; links[Side.P2] = it }
        b?.let { it.side = Side.P1; links[Side.P1] = it }
        publish()
    }

    fun reconnectAll() = links.values.forEach { it.retryNow() }

    fun disconnectAll() {
        setAutoScan(false)
        links.values.forEach { it.close() }
        links.clear()
        publish()
    }

    private fun publish() {
        _straps.value = links.mapValues { (_, l) ->
            val fresh = l.fresh()
            val ready = l.state == StrapState.READY
            StrapInfo(
                address = l.address,
                name = l.name,
                state = l.state,
                bpm = l.last?.bpm?.takeIf { fresh && l.last?.valid == true },
                contact = l.last?.contact?.takeIf { fresh },
                battery = l.battery,
                rssi = l.rssi.takeIf { ready },
                received = l.receivedPercent().takeIf { ready },
            )
        }
    }

    private enum class OpKind { DESCRIPTOR, READ }

    private inner class StrapLink(var side: Side, val address: String, val name: String) {
        var state = StrapState.IDLE
            private set
        var battery: Int? = null
        var last: HeartMeasurement? = null
        private var lastAt = 0L
        var rssi: Int? = null
        private var readySince = 0L
        /** Secondi (dall'avvio del telefono) in cui è arrivata almeno una misura, solo l'ultimo minuto. */
        private val seconds = ArrayDeque<Long>()
        private var gatt: BluetoothGatt? = null
        private var closed = false
        private var retryJob: Job? = null
        private var watchdog: Job? = null
        private var background = false
        private var failures = 0
        private val opLock = Mutex()
        @Volatile private var pendingOp: CompletableDeferred<Int>? = null
        @Volatile private var pendingKind: OpKind? = null

        fun fresh(): Boolean = last != null && SystemClock.elapsedRealtime() - lastAt <= STALE_MS

        fun readRssi() {
            if (state == StrapState.READY) runCatching { gatt?.readRemoteRssi() }
        }

        /** Secondi con almeno una misura nell'ultimo minuto (o da quando è collegata, se è meno di un minuto). */
        fun receivedPercent(): Int? {
            val nowS = SystemClock.elapsedRealtime() / 1000
            while (seconds.isNotEmpty() && seconds.first() <= nowS - RECEIVED_WINDOW_S) seconds.removeFirst()
            // Il secondo in corso non conta: la misura può ancora arrivare.
            val span = minOf(RECEIVED_WINDOW_S, nowS - readySince / 1000)
            if (span < 5) return null
            return (seconds.count { it < nowS } * 100 / span).toInt().coerceAtMost(100)
        }

        private fun changeState(s: StrapState) {
            state = s
            publish()
        }

        /**
         * Prima tentativi diretti; dopo [DIRECT_TRIES] a vuoto si aspetta in background (Android collega la fascia
         * da solo quando torna a trasmettere: indossata e con gli elettrodi bagnati).
         */
        fun connect(direct: Boolean = false) {
            if (closed || !ble.canConnect() || !isEnabled) return
            if (gatt != null) {
                if (!direct || !background || state == StrapState.READY) return
                runCatching { gatt?.close() }
                gatt = null
            }
            val dev = runCatching { adapter?.getRemoteDevice(address) }.getOrNull() ?: return
            retryJob?.cancel()
            background = !direct && failures >= DIRECT_TRIES
            changeState(if (background) StrapState.IDLE else StrapState.CONNECTING)
            gatt = runCatching { dev.connectGatt(app, background, callback, BluetoothDevice.TRANSPORT_LE) }.getOrNull()
            if (gatt == null) scheduleReconnect(afterLoss = false)
        }

        fun retryNow() {
            failures = 0
            connect(direct = true)
        }

        fun onSeen() {
            if (!background || state == StrapState.READY) return
            failures = 0
            connect(direct = true)
        }

        fun onAdapterOff() {
            pendingOp?.complete(-1)
            runCatching { gatt?.close() }
            gatt = null
            changeState(StrapState.IDLE)
        }

        fun close() {
            closed = true
            retryJob?.cancel()
            watchdog?.cancel()
            pendingOp?.complete(-1)
            runCatching { gatt?.disconnect() }
            runCatching { gatt?.close() }
            gatt = null
        }

        private fun scheduleReconnect(afterLoss: Boolean) {
            if (closed) return
            val wasBackground = background
            retryJob?.cancel()
            retryJob = scope.launch {
                delay(
                    when {
                        wasBackground -> 30_000
                        afterLoss -> 500
                        else -> 2_500
                    },
                )
                connect()
            }
        }

        private fun completeOp(kind: OpKind, status: Int) {
            if (pendingKind == kind) pendingOp?.complete(status)
        }

        private val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                scope.launch {
                    if (g !== gatt) return@launch
                    if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                        background = false
                        watchdog?.cancel()
                        watchdog = scope.launch {
                            delay(15_000)
                            if (gatt === g && state != StrapState.READY) runCatching { g.disconnect() }
                        }
                        delay(400)
                        runCatching { g.discoverServices() }
                    } else {
                        watchdog?.cancel()
                        pendingOp?.complete(-1)
                        runCatching { g.close() }
                        gatt = null
                        val wasReady = state == StrapState.READY
                        if (wasReady) failures = 0 else failures++
                        rssi = null
                        changeState(StrapState.IDLE)
                        scheduleReconnect(afterLoss = wasReady)
                    }
                }
            }

            override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                scope.launch { if (g === gatt) setup(g) }
            }

            override fun onReadRemoteRssi(g: BluetoothGatt, rssi: Int, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) scope.launch { if (g === gatt) this@StrapLink.rssi = rssi }
            }

            override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
                completeOp(OpKind.DESCRIPTOR, status)
            }

            override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) handle(c.uuid, value)
                completeOp(OpKind.READ, status)
            }

            @Suppress("OVERRIDE_DEPRECATION")
            override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
                if (Build.VERSION.SDK_INT >= 33) return
                @Suppress("DEPRECATION")
                if (status == BluetoothGatt.GATT_SUCCESS) handle(c.uuid, c.value ?: byteArrayOf())
                completeOp(OpKind.READ, status)
            }

            override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
                handle(c.uuid, value)
            }

            @Suppress("OVERRIDE_DEPRECATION")
            override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
                if (Build.VERSION.SDK_INT >= 33) return
                @Suppress("DEPRECATION")
                handle(c.uuid, c.value ?: byteArrayOf())
            }
        }

        private fun handle(uuid: UUID, value: ByteArray) {
            val copy = value.copyOf()
            scope.launch {
                when (uuid) {
                    HeartRateProtocol.MEASUREMENT -> HeartRateProtocol.parse(copy)?.let { m ->
                        last = m
                        lastAt = SystemClock.elapsedRealtime()
                        val s = lastAt / 1000
                        if (seconds.lastOrNull() != s) seconds.addLast(s)
                        publish()
                        _readings.tryEmit(side to m)
                    }
                    HeartRateProtocol.BATTERY_LEVEL -> if (copy.isNotEmpty()) {
                        battery = (copy[0].toInt() and 0xFF).coerceIn(0, 100)
                        publish()
                    }
                }
            }
        }

        private suspend fun setup(g: BluetoothGatt) {
            val hr = g.getService(HeartRateProtocol.SERVICE)?.getCharacteristic(HeartRateProtocol.MEASUREMENT)
            if (hr == null || !enableNotify(g, hr)) {
                runCatching { g.disconnect() }
                return
            }
            g.getService(HeartRateProtocol.BATTERY_SERVICE)?.getCharacteristic(HeartRateProtocol.BATTERY_LEVEL)?.let {
                op(OpKind.READ) { g.readCharacteristic(it) }
                if (it.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) enableNotify(g, it)
            }
            // Una misura al secondo non chiede una connessione veloce: meno consumo, per la fascia e per il telefono.
            runCatching { g.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_LOW_POWER) }
            if (g !== gatt) return
            watchdog?.cancel()
            failures = 0
            readySince = SystemClock.elapsedRealtime()
            seconds.clear()
            changeState(StrapState.READY)
        }

        private suspend fun op(kind: OpKind, start: () -> Boolean): Boolean = opLock.withLock {
            val d = CompletableDeferred<Int>()
            pendingKind = kind
            pendingOp = d
            val started = runCatching(start).getOrDefault(false)
            val result = if (started) withTimeoutOrNull(5_000) { d.await() } else null
            pendingOp = null
            pendingKind = null
            result == BluetoothGatt.GATT_SUCCESS
        }

        private suspend fun enableNotify(g: BluetoothGatt, c: BluetoothGattCharacteristic): Boolean {
            runCatching { g.setCharacteristicNotification(c, true) }
            val d = c.getDescriptor(HeartRateProtocol.CCCD) ?: return false
            val value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            return op(OpKind.DESCRIPTOR) {
                if (Build.VERSION.SDK_INT >= 33) {
                    g.writeDescriptor(d, value) == BluetoothStatusCodes.SUCCESS
                } else {
                    @Suppress("DEPRECATION")
                    d.value = value
                    @Suppress("DEPRECATION")
                    g.writeDescriptor(d)
                }
            }
        }
    }

    companion object {
        private const val TAG = "HeartRateManager"
        private const val DIRECT_TRIES = 3
        /** Le fasce mandano una misura al secondo: dopo 6 secondi senza, il battito non è più quello di adesso. */
        private const val STALE_MS = 6_000L
        private const val RECEIVED_WINDOW_S = 60L
    }
}
