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
import android.location.LocationManager
import android.os.Build
import android.os.ParcelUuid
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.tennis.scoremanager.data.LocationHelper
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

data class FoundBand(val address: String, val name: String, val rssi: Int, val lastSeen: Long = 0L)

data class BandInfo(
    val address: String,
    val name: String,
    val state: LinkState = LinkState.IDLE,
    /** Percentuale: dalla tensione se il firmware la manda, altrimenti quella del braccialetto. */
    val battery: Int? = null,
    val millivolts: Int? = null,
    val charging: Boolean = false,
    /** Carica completa col cavo ancora collegato (firmware 2.1). */
    val chargeFull: Boolean = false,
    /** Impostazioni lette dal braccialetto; null = firmware senza impostazioni (prima della 2.0) o non ancora lette. */
    val settings: BandSettings? = null,
)

/** [reason] solo per EVT_POWER_OFF dai firmware 2.0: vedi BandProtocol.OFF_*. */
data class BandEvent(val side: Side, val type: Int, val reason: Int? = null)

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
    private val _status = MutableSharedFlow<Pair<Side, BandStatus>>(extraBufferCapacity = 8)
    /** Stato batteria ricevuto dai braccialetti (per stima autonomia e registro consumi). */
    val status: SharedFlow<Pair<Side, BandStatus>> = _status
    private val _adapterOn = MutableStateFlow(adapter?.isEnabled == true)
    val adapterOn: StateFlow<Boolean> = _adapterOn
    private val _locationOn = MutableStateFlow(LocationHelper.isEnabled(app))
    /** Posizione del telefono attiva: senza, Android non restituisce i braccialetti trovati. Si aggiorna da sola. */
    val locationOn: StateFlow<Boolean> = _locationOn

    private val links = mutableMapOf<Side, BandLink>()
    private var scanJob: Job? = null
    /** Ricerca automatica voluta (pagina dei braccialetti aperta): riparte da sola se Bluetooth o posizione tornano. */
    private var autoScan = false

    init {
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        ContextCompat.registerReceiver(app, object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                val st = i?.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                _adapterOn.value = st == BluetoothAdapter.STATE_ON
                if (st == BluetoothAdapter.STATE_ON) {
                    links.values.forEach { it.connect() }
                    if (autoScan) startScan()
                }
                if (st == BluetoothAdapter.STATE_OFF) {
                    stopScan()
                    links.values.forEach { it.onAdapterOff() }
                }
            }
        }, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        // La posizione si può spegnere dalla tendina senza che l'app vada in pausa: la si segue in tempo reale.
        val locFilter = IntentFilter().apply {
            addAction(LocationManager.MODE_CHANGED_ACTION)
            addAction(LocationManager.PROVIDERS_CHANGED_ACTION)
        }
        ContextCompat.registerReceiver(app, object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) = refreshLocation()
        }, locFilter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    /** Ricontrolla la posizione (anche a ogni ritorno nell'app). Quando torna attiva la ricerca riparte. */
    fun refreshLocation() {
        val on = LocationHelper.isEnabled(app)
        val was = _locationOn.value
        _locationOn.value = on
        if (on && !was && autoScan) startScan()
    }

    val isSupported: Boolean get() = adapter != null && app.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
    val isEnabled: Boolean get() = adapter?.isEnabled == true

    fun hasPermissions(): Boolean = requiredPermissions().all {
        ContextCompat.checkSelfPermission(app, it) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Ricerca automatica e continua dei braccialetti finché [on] (la pagina dei braccialetti è aperta).
     * I braccialetti che non si sentono più da 10 s spariscono dall'elenco (spenti, o già collegati: da
     * collegati non trasmettono più).
     */
    fun setAutoScan(on: Boolean) {
        autoScan = on
        if (on) startScan() else stopScan()
    }

    private fun startScan(): Boolean {
        val scanner = adapter?.bluetoothLeScanner ?: return false
        if (!hasPermissions() || !isEnabled) return false
        stopScan()
        val filters = listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(BandProtocol.SERVICE)).build())
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        return runCatching {
            scanner.startScan(filters, settings, scanCallback)
            _scanning.value = true
            scanJob = scope.launch {
                var sinceRestart = 0L
                while (true) {
                    delay(2_000)
                    val now = SystemClock.elapsedRealtime()
                    _found.update { list -> list.filter { now - it.lastSeen < 10_000 } }
                    // Android declassa le ricerche che durano più di 30 minuti: si riparte ogni 10.
                    sinceRestart += 2_000
                    if (sinceRestart >= 10 * 60_000L) {
                        sinceRestart = 0
                        runCatching {
                            scanner.stopScan(scanCallback)
                            scanner.startScan(filters, settings, scanCallback)
                        }
                    }
                }
            }
        }.isSuccess
    }

    private fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        if (_scanning.value) runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
        _scanning.value = false
        _found.value = emptyList()
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val addr = result.device.address
            val name = result.scanRecord?.deviceName ?: runCatching { result.device.name }.getOrNull() ?: "TSM-Band"
            val band = FoundBand(addr, name, result.rssi, SystemClock.elapsedRealtime())
            _found.update { list -> (list.filterNot { it.address == addr } + band).sortedBy { it.name } }
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

    /** Scambia i due braccialetti tra Giocatore 1 e Giocatore 2 senza scollegarli. */
    fun swapSides() {
        val a = links.remove(Side.P1)
        val b = links.remove(Side.P2)
        a?.let { it.side = Side.P2; links[Side.P2] = it }
        b?.let { it.side = Side.P1; links[Side.P1] = it }
        publish()
    }

    /** Riprova a collegare i braccialetti associati (es. dopo aver concesso i permessi). */
    fun reconnectAll() = links.values.forEach { it.connect() }

    /** Punteggi e messaggi: se ne arrivano altri prima dell'invio vale l'ultimo. */
    fun send(side: Side, payload: String) {
        links[side]?.send(payload)
    }

    /** Comando che non deve andare perso (Identifica, spegnimento): scritto subito, true se è arrivato. */
    suspend fun command(side: Side, payload: String): Boolean = links[side]?.command(payload) ?: false

    /** Scrive le impostazioni nel braccialetto; true se le ha ricevute (poi risponde con quelle applicate). */
    suspend fun writeSettings(side: Side, settings: BandSettings): Boolean = links[side]?.writeConfig(settings.encode()) ?: false

    suspend fun writeLanguage(side: Side, code: String): Boolean = links[side]?.writeConfig(BandSettings.languageConfig(code)) ?: false

    fun isReady(side: Side): Boolean = links[side]?.state == LinkState.READY

    fun disconnectAll() {
        setAutoScan(false)
        links.values.forEach { it.close() }
        links.clear()
        publish()
    }

    private fun publish() {
        _bands.value = links.mapValues { (_, l) ->
            BandInfo(
                address = l.address,
                name = l.settings?.name?.takeIf { it.isNotEmpty() } ?: l.name,
                state = l.state,
                battery = l.status?.let { BatteryModel.shownPercent(it) } ?: l.battery,
                millivolts = l.status?.millivolts,
                charging = l.status?.charging == true,
                chargeFull = l.status?.full == true,
                settings = l.settings,
            )
        }
    }

    private inner class BandLink(var side: Side, val address: String, val name: String) {
        var state = LinkState.IDLE
            private set
        var battery: Int? = null
        var status: BandStatus? = null
        var settings: BandSettings? = null
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
                        val reason = if (type == BandProtocol.EVT_POWER_OFF && copy.size >= 3) copy[2].toInt() and 0xFF else null
                        _events.tryEmit(BandEvent(side, type, reason))
                    }
                    BandProtocol.CONFIG -> BandSettings.parse(String(copy, Charsets.US_ASCII))?.let {
                        settings = it
                        publish()
                    }
                    BandProtocol.BATTERY_LEVEL -> if (copy.isNotEmpty()) {
                        battery = (copy[0].toInt() and 0xFF).coerceIn(0, 100)
                        publish()
                    }
                    BandProtocol.STATUS -> BatteryModel.parse(String(copy, Charsets.US_ASCII))?.let {
                        status = it
                        publish()
                        _status.tryEmit(side to it)
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
            svc.getCharacteristic(BandProtocol.STATUS)?.let {
                enableNotify(g, it)
                op { g.readCharacteristic(it) }
            }
            svc.getCharacteristic(BandProtocol.CONFIG)?.let {
                enableNotify(g, it)
                op { g.readCharacteristic(it) }
            }
            // Connessione a basso consumo (intervallo ~100 ms, latenza 2): la radio del braccialetto
            // si sveglia molto meno spesso; un tasto arriva comunque entro ~125 ms.
            runCatching { g.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_LOW_POWER) }
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

        suspend fun command(msg: String): Boolean = state == LinkState.READY && write(msg)

        suspend fun writeConfig(text: String): Boolean = state == LinkState.READY && write(text, BandProtocol.CONFIG)

        private suspend fun write(msg: String, uuid: UUID = BandProtocol.DISPLAY): Boolean {
            val g = gatt ?: return false
            val c = g.getService(BandProtocol.SERVICE)?.getCharacteristic(uuid) ?: return false
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
