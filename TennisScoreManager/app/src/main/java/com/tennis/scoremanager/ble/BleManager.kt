package com.tennis.scoremanager.ble

import android.Manifest
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
import androidx.core.content.ContextCompat
import com.tennis.scoremanager.model.Side
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
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

enum class LinkState { IDLE, CONNECTING, READY, POWERED_OFF }

data class FoundBand(val address: String, val name: String, val rssi: Int)

data class BandInfo(
    val address: String,
    val name: String,
    val state: LinkState = LinkState.IDLE,
    val battery: Int? = null,
)

data class BandEvent(val side: Side, val type: Int)

/**
 * Gestisce i due braccialetti. Ogni lato (Giocatore 1 / Giocatore 2) ha al massimo un braccialetto:
 * il legame lato <-> indirizzo è l'unica fonte di verità, così i punti non finiscono mai al giocatore sbagliato.
 */
@SuppressLint("MissingPermission")
class BleManager(context: Context) {

    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val adapter: BluetoothAdapter? = app.getSystemService(BluetoothManager::class.java)?.adapter

    private val _found = MutableStateFlow<List<FoundBand>>(emptyList())
    val found: StateFlow<List<FoundBand>> = _found
    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning
    private val _bands = MutableStateFlow<Map<Side, BandInfo>>(emptyMap())
    val bands: StateFlow<Map<Side, BandInfo>> = _bands
    private val _events = MutableSharedFlow<BandEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<BandEvent> = _events
    private val _ready = MutableSharedFlow<Side>(extraBufferCapacity = 4)
    /** Emesso quando un braccialetto è connesso e pronto a ricevere. */
    val ready: SharedFlow<Side> = _ready
    private val _adapterOn = MutableStateFlow(adapter?.isEnabled == true)
    val adapterOn: StateFlow<Boolean> = _adapterOn

    private val links = mutableMapOf<Side, BandLink>()
    private var scanStopJob: Job? = null

    init {
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        ContextCompat.registerReceiver(app, object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                val st = i?.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                _adapterOn.value = st == BluetoothAdapter.STATE_ON
                if (st == BluetoothAdapter.STATE_ON) links.values.forEach { it.connect() }
                if (st == BluetoothAdapter.STATE_OFF) {
                    _scanning.value = false
                    links.values.forEach { it.onAdapterOff() }
                }
            }
        }, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    val isSupported: Boolean get() = adapter != null && app.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
    val isEnabled: Boolean get() = adapter?.isEnabled == true

    fun hasPermissions(): Boolean = requiredPermissions().all {
        ContextCompat.checkSelfPermission(app, it) == PackageManager.PERMISSION_GRANTED
    }

    fun startScan(): Boolean {
        val scanner = adapter?.bluetoothLeScanner ?: return false
        if (!hasPermissions() || !isEnabled) return false
        stopScan()
        _found.value = emptyList()
        val filters = listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(BandProtocol.SERVICE)).build())
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        return runCatching {
            scanner.startScan(filters, settings, scanCallback)
            _scanning.value = true
            scanStopJob = scope.launch { delay(15_000); stopScan() }
        }.isSuccess
    }

    fun stopScan() {
        scanStopJob?.cancel()
        if (_scanning.value) runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
        _scanning.value = false
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val addr = result.device.address
            val name = result.scanRecord?.deviceName ?: runCatching { result.device.name }.getOrNull() ?: "TSM-Band"
            _found.update { list -> (list.filterNot { it.address == addr } + FoundBand(addr, name, result.rssi)).sortedBy { it.name } }
        }
    }

    /** Associa (o toglie, con address null) il braccialetto a un giocatore. Un braccialetto non può stare su due lati. */
    fun assign(side: Side, address: String?, name: String?) {
        links.remove(side)?.close()
        if (address != null) {
            links.entries.firstOrNull { it.value.address == address }?.let { other ->
                links.remove(other.key)?.close()
            }
            val link = BandLink(side, address, name ?: address)
            links[side] = link
            link.connect()
        }
        publish()
    }

    /** Riprova a collegare i braccialetti associati (es. dopo aver concesso i permessi). */
    fun reconnectAll() = links.values.forEach { it.connect() }

    fun send(side: Side, payload: String) {
        links[side]?.send(payload)
    }

    fun isReady(side: Side): Boolean = links[side]?.state == LinkState.READY

    fun disconnectAll() {
        stopScan()
        links.values.forEach { it.close() }
        links.clear()
        publish()
    }

    private fun publish() {
        _bands.value = links.mapValues { (_, l) -> BandInfo(l.address, l.name, l.state, l.battery) }
    }

    private inner class BandLink(val side: Side, val address: String, val name: String) {
        var state = LinkState.IDLE
            private set
        var battery: Int? = null
        private var gatt: BluetoothGatt? = null
        private var closed = false
        private var retryJob: Job? = null
        private var setupWatchdog: Job? = null
        private val opLock = Mutex()
        @Volatile private var pendingOp: CompletableDeferred<Int>? = null
        private val wake = Channel<Unit>(Channel.CONFLATED)
        private var latest: String? = null
        private var lastSeq = -1
        private val sender: Job = scope.launch {
            for (tick in wake) {
                if (state != LinkState.READY) continue
                val msg = latest ?: continue
                latest = null
                if (!write(msg) && latest == null) latest = msg
            }
        }

        private fun changeState(s: LinkState) {
            state = s
            publish()
        }

        fun connect() {
            if (closed || gatt != null || !hasPermissions() || !isEnabled) return
            val dev = runCatching { adapter?.getRemoteDevice(address) }.getOrNull() ?: return
            retryJob?.cancel()
            if (state != LinkState.POWERED_OFF) changeState(LinkState.CONNECTING)
            gatt = runCatching { dev.connectGatt(app, false, callback, BluetoothDevice.TRANSPORT_LE) }.getOrNull()
            if (gatt == null) scheduleReconnect()
        }

        fun onAdapterOff() {
            pendingOp?.complete(-1)
            runCatching { gatt?.close() }
            gatt = null
            changeState(LinkState.IDLE)
        }

        fun send(payload: String) {
            latest = payload
            wake.trySend(Unit)
        }

        fun close() {
            closed = true
            retryJob?.cancel()
            sender.cancel()
            pendingOp?.complete(-1)
            runCatching { gatt?.disconnect() }
            runCatching { gatt?.close() }
            gatt = null
        }

        private fun scheduleReconnect() {
            if (closed) return
            retryJob?.cancel()
            retryJob = scope.launch {
                delay(if (state == LinkState.POWERED_OFF) 5_000 else 2_500)
                connect()
            }
        }

        private val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                scope.launch {
                    if (g !== gatt) return@launch
                    if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                        lastSeq = -1  // il braccialetto può essere stato riacceso: la sequenza riparte
                        setupWatchdog?.cancel()
                        setupWatchdog = scope.launch {
                            // Se la configurazione non finisce (callback persa) si ricomincia da capo.
                            delay(15_000)
                            if (gatt === g && state != LinkState.READY) runCatching { g.disconnect() }
                        }
                        delay(400)
                        runCatching { g.discoverServices() }
                    } else {
                        setupWatchdog?.cancel()
                        pendingOp?.complete(-1)
                        runCatching { g.close() }
                        gatt = null
                        if (state != LinkState.POWERED_OFF) changeState(LinkState.IDLE)
                        scheduleReconnect()
                    }
                }
            }

            override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                scope.launch { if (g === gatt) setup(g) }
            }

            override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
                pendingOp?.complete(status)
            }

            override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
                pendingOp?.complete(status)
            }

            override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
                pendingOp?.complete(status)
            }

            override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) handle(c.uuid, value)
                pendingOp?.complete(status)
            }

            @Suppress("OVERRIDE_DEPRECATION")
            override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
                if (Build.VERSION.SDK_INT >= 33) return
                @Suppress("DEPRECATION")
                if (status == BluetoothGatt.GATT_SUCCESS) handle(c.uuid, c.value ?: byteArrayOf())
                pendingOp?.complete(status)
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
                    BandProtocol.EVENT -> if (copy.size >= 2) {
                        val type = copy[0].toInt() and 0xFF
                        val seq = copy[1].toInt() and 0xFF
                        if (seq == lastSeq) return@launch // stessa pressione ricevuta due volte
                        lastSeq = seq
                        if (type == BandProtocol.EVT_POWER_OFF) changeState(LinkState.POWERED_OFF)
                        _events.tryEmit(BandEvent(side, type))
                    }
                    BandProtocol.BATTERY_LEVEL -> if (copy.isNotEmpty()) {
                        battery = (copy[0].toInt() and 0xFF).coerceIn(0, 100)
                        publish()
                    }
                }
            }
        }

        private suspend fun setup(g: BluetoothGatt) {
            val svc = g.getService(BandProtocol.SERVICE)
            if (svc == null) {
                runCatching { g.disconnect() }
                return
            }
            op { g.requestMtu(185) }
            // Senza notifiche i tasti del braccialetto andrebbero persi: meglio riconnettersi.
            val evt = svc.getCharacteristic(BandProtocol.EVENT)
            if (evt == null || !enableNotify(g, evt)) {
                runCatching { g.disconnect() }
                return
            }
            g.getService(BandProtocol.BATTERY_SERVICE)?.getCharacteristic(BandProtocol.BATTERY_LEVEL)?.let {
                enableNotify(g, it)
                op { g.readCharacteristic(it) }
            }
            if (g !== gatt) return
            setupWatchdog?.cancel()
            changeState(LinkState.READY)
            _ready.tryEmit(side)
            wake.trySend(Unit)
        }

        /** Android esegue un'operazione GATT alla volta: le serializziamo e aspettiamo la callback. */
        private suspend fun op(start: () -> Boolean): Boolean = opLock.withLock {
            val d = CompletableDeferred<Int>()
            pendingOp = d
            val started = runCatching(start).getOrDefault(false)
            val result = if (started) withTimeoutOrNull(5_000) { d.await() } else null
            pendingOp = null
            result == BluetoothGatt.GATT_SUCCESS
        }

        private suspend fun enableNotify(g: BluetoothGatt, c: BluetoothGattCharacteristic): Boolean {
            runCatching { g.setCharacteristicNotification(c, true) }
            val d = c.getDescriptor(BandProtocol.CCCD) ?: return false
            val value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            return op {
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

        private suspend fun write(msg: String): Boolean {
            val g = gatt ?: return false
            val c = g.getService(BandProtocol.SERVICE)?.getCharacteristic(BandProtocol.DISPLAY) ?: return false
            val bytes = msg.toByteArray(Charsets.US_ASCII).copyOf(minOf(msg.length, 180))
            return op {
                if (Build.VERSION.SDK_INT >= 33) {
                    g.writeCharacteristic(c, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothStatusCodes.SUCCESS
                } else {
                    @Suppress("DEPRECATION")
                    c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    @Suppress("DEPRECATION")
                    c.value = bytes
                    @Suppress("DEPRECATION")
                    g.writeCharacteristic(c)
                }
            }
        }
    }

    companion object {
        fun requiredPermissions(): Array<String> =
            if (Build.VERSION.SDK_INT >= 31) {
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                )
            } else {
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            }
    }
}
