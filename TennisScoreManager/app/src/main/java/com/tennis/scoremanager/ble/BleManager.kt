// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

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
import android.util.Log
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
    /** La ricerca è davvero avviata nel sistema (startScan riuscito e non fallito dopo). */
    private var scanRunning = false
    private var scanStartJob: Job? = null
    private var scanStopJob: Job? = null
    private val scanThrottle = ScanThrottle()
    /** Eventi già ricevuti (per braccialetto e accensione): vale anche tra una connessione e l'altra. */
    private val dedupe = EventDedupe()

    init {
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        ContextCompat.registerReceiver(app, object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                val st = i?.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                _adapterOn.value = st == BluetoothAdapter.STATE_ON
                if (st == BluetoothAdapter.STATE_ON) {
                    links.values.forEach { it.connect() }
                    if (autoScan) ensureScan()
                }
                if (st == BluetoothAdapter.STATE_OFF) {
                    stopScanNow()
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
        // Fino ad Android 11 senza posizione la ricerca non restituisce niente: quando torna la si riavvia.
        if (on && !was && autoScan) restartScan()
    }

    val isSupported: Boolean get() = adapter != null && app.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
    val isEnabled: Boolean get() = adapter?.isEnabled == true

    private fun granted(p: String) = ContextCompat.checkSelfPermission(app, p) == PackageManager.PERMISSION_GRANTED

    /** Per collegarsi a un braccialetto già associato: da Android 12 basta BLUETOOTH_CONNECT (la posizione no). */
    fun canConnect(): Boolean = Build.VERSION.SDK_INT < 31 || granted(Manifest.permission.BLUETOOTH_CONNECT)

    /**
     * Per la ricerca: da Android 12 BLUETOOTH_SCAN, dichiarato "neverForLocation" nel manifest, quindi la posizione
     * non serve (va bene anche quella approssimativa); prima di Android 12 serve la posizione precisa.
     */
    fun canScan(): Boolean =
        if (Build.VERSION.SDK_INT >= 31) granted(Manifest.permission.BLUETOOTH_SCAN) else granted(Manifest.permission.ACCESS_FINE_LOCATION)

    /** Tutto quello che serve ai braccialetti (ricerca e collegamento). La richiesta ([requiredPermissions]) chiede anche la posizione. */
    fun hasPermissions(): Boolean = canScan() && canConnect()

    /**
     * Ricerca automatica e continua dei braccialetti finché [on] (la pagina dei braccialetti è aperta).
     * I braccialetti che non si sentono più da 10 s spariscono dall'elenco (spenti, o già collegati: da
     * collegati non trasmettono più).
     */
    fun setAutoScan(on: Boolean) {
        autoScan = on
        if (on) {
            scanStopJob?.cancel()
            ensureScan()
        } else {
            // Si ferma con un attimo di ritardo: una pausa breve (dialogo dei permessi, Bluetooth da attivare)
            // non deve costare un nuovo avvio. Android ne permette 5 in 30 s, poi la ricerca non parte e basta.
            scanStopJob?.cancel()
            scanStopJob = scope.launch {
                delay(SCAN_STOP_GRACE_MS)
                stopScanNow()
            }
        }
    }

    /** Avvia la ricerca se è voluta e non è già avviata (o in partenza), rispettando il limite di avvii di Android. */
    private fun ensureScan(retryInMs: Long = 0) {
        if (!autoScan || scanRunning || scanStartJob?.isActive == true) return
        if (!canScan() || !isEnabled || adapter?.bluetoothLeScanner == null) {
            _scanning.value = false
            return
        }
        _scanning.value = true  // in partenza: per chi guarda è già "ricerca in corso"
        scanStartJob = scope.launch {
            delay(maxOf(retryInMs, scanThrottle.delayBeforeStart(SystemClock.elapsedRealtime())))
            startScanNow()
        }
    }

    private fun restartScan() {
        if (scanRunning) {
            scanJob?.cancel()
            runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
            scanRunning = false
        }
        ensureScan()
    }

    private fun startScanNow() {
        val scanner = adapter?.bluetoothLeScanner
        if (!autoScan || scanner == null || !canScan() || !isEnabled) {
            _scanning.value = false
            return
        }
        val ok = runCatching { scanner.startScan(scanFilters, scanSettings, scanCallback) }.isSuccess
        scanThrottle.recordStart(SystemClock.elapsedRealtime())
        if (!ok) {
            _scanning.value = false
            ensureScan(retryInMs = SCAN_RETRY_MS)
            return
        }
        scanRunning = true
        _scanning.value = true
        scanJob?.cancel()
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
                    restartScan()
                    break
                }
            }
        }
    }

    private fun stopScanNow() {
        scanStopJob?.cancel()
        scanStartJob?.cancel()
        scanJob?.cancel()
        scanJob = null
        if (scanRunning) runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
        scanRunning = false
        _scanning.value = false
        _found.value = emptyList()
    }

    private val scanFilters = listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(BandProtocol.SERVICE)).build())
    private val scanSettings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val addr = result.device.address
            val name = result.scanRecord?.deviceName ?: runCatching { result.device.name }.getOrNull() ?: "TSM-Band"
            val band = FoundBand(addr, name, result.rssi, SystemClock.elapsedRealtime())
            _found.update { list -> (list.filterNot { it.address == addr } + band).sortedBy { it.name } }
            // Un braccialetto associato che trasmette è acceso e libero: lo si collega subito.
            scope.launch { links.values.firstOrNull { it.address == addr }?.onSeen() }
        }

        override fun onScanFailed(errorCode: Int) {
            scope.launch {
                Log.w(TAG, "ricerca fallita: $errorCode")
                if (errorCode == SCAN_FAILED_ALREADY_STARTED) return@launch  // è già avviata: i risultati arrivano
                scanJob?.cancel()
                scanRunning = false
                _scanning.value = false
                // Troppi avvii (Android 13+ lo dice, prima tace) o errore del sistema: si riprova più tardi.
                ensureScan(retryInMs = if (errorCode == SCAN_FAILED_TOO_FREQUENTLY) 30_000 else SCAN_RETRY_MS)
            }
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

    /**
     * Riprova a collegare i braccialetti associati (es. dopo aver concesso i permessi, o tornando nell'app):
     * un braccialetto che non rispondeva si riprova subito; uno spento resta in attesa in background.
     */
    fun reconnectAll() = links.values.forEach { it.retryNow() }

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

    private enum class OpKind { MTU, DESCRIPTOR, WRITE, READ }

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
        @Volatile private var pendingKind: OpKind? = null
        private val wake = Channel<Unit>(Channel.CONFLATED)
        private var latest: String? = null
        private var lastSeq = -1
        /** Tentativo in corso in background (connectGatt con autoConnect): aspetta il braccialetto senza scadenza. */
        private var background = false
        /** Tentativi di fila finiti senza arrivare a READY. */
        private var failures = 0
        @Volatile private var mtu = DEFAULT_MTU
        /** Fino a quando non si scrive sul display: "PAIRING OK" resta a schermo i suoi 3 secondi. */
        private var quietUntil = 0L
        private val acks = Channel<Int>(Channel.UNLIMITED)
        private val sender: Job = scope.launch {
            for (tick in wake) {
                if (state != LinkState.READY) continue
                val wait = quietUntil - SystemClock.elapsedRealtime()
                if (wait > 0) delay(wait)
                if (state != LinkState.READY) continue
                val msg = latest ?: continue
                latest = null
                if (!write(msg) && latest == null) latest = msg
            }
        }
        /** Conferme dei tasti: partono subito, anche durante la configurazione (il braccialetto rimanda gli eventi appena ascoltiamo). */
        private val acker: Job = scope.launch {
            // Persa? Il braccialetto rimanda l'evento alla prossima connessione e lo si conferma di nuovo.
            for (seq in acks) if (gatt != null) write(BandProtocol.ack(seq))
        }

        private fun changeState(s: LinkState) {
            state = s
            publish()
        }

        /**
         * Tentativo diretto (veloce, ma Android lo chiude dopo ~30 s) finché il braccialetto risponde; dopo uno
         * spegnimento o [DIRECT_TRIES] tentativi a vuoto si aspetta in background: Android collega da solo il
         * braccialetto quando torna a trasmettere, senza riprovare ogni pochi secondi per sempre.
         */
        fun connect(direct: Boolean = false) {
            if (closed || !canConnect() || !isEnabled) return
            if (gatt != null) {
                // Un tentativo diretto prende il posto solo di un'attesa in background.
                if (!direct || !background || state == LinkState.READY) return
                runCatching { gatt?.close() }
                gatt = null
            }
            val dev = runCatching { adapter?.getRemoteDevice(address) }.getOrNull() ?: return
            retryJob?.cancel()
            val bg = !direct && (state == LinkState.POWERED_OFF || failures >= DIRECT_TRIES)
            background = bg
            if (state != LinkState.POWERED_OFF) changeState(if (bg) LinkState.IDLE else LinkState.CONNECTING)
            gatt = runCatching { dev.connectGatt(app, bg, callback, BluetoothDevice.TRANSPORT_LE) }.getOrNull()
            if (gatt == null) scheduleReconnect(wasBackground = bg, afterLoss = false)
        }

        /** Dall'app (ritorno in primo piano, permessi): chi non rispondeva si riprova subito, chi è spento no. */
        fun retryNow() {
            if (state == LinkState.POWERED_OFF) {
                connect()
            } else {
                failures = 0
                connect(direct = true)
            }
        }

        /** La ricerca lo vede trasmettere: è acceso e libero, il tentativo diretto è più rapido dell'attesa in background. */
        fun onSeen() {
            if (!background || state == LinkState.READY) return
            failures = 0
            connect(direct = true)
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
            acker.cancel()
            pendingOp?.complete(-1)
            runCatching { gatt?.disconnect() }
            runCatching { gatt?.close() }
            gatt = null
        }

        private fun scheduleReconnect(wasBackground: Boolean, afterLoss: Boolean) {
            if (closed) return
            retryJob?.cancel()
            retryJob = scope.launch {
                delay(
                    when {
                        wasBackground -> 30_000   // l'attesa in background è finita male da sola: niente raffiche
                        afterLoss -> 500          // collegamento appena perso: il braccialetto trasmette già di nuovo
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
                        lastSeq = -1  // firmware vecchi: il braccialetto può essere stato riacceso, la sequenza riparte
                        background = false
                        mtu = DEFAULT_MTU
                        quietUntil = SystemClock.elapsedRealtime() + PAIRED_MSG_MS
                        setupWatchdog?.cancel()
                        setupWatchdog = scope.launch {
                            // Se la configurazione non finisce (callback persa) si ricomincia da capo.
                            delay(15_000)
                            if (gatt === g && state != LinkState.READY) runCatching { g.disconnect() }
                        }
                        // Pausa prima della scoperta dei servizi (le librerie BLE di riferimento ne fanno una simile):
                        // su alcuni telefoni la scoperta fallisce se parte mentre il braccialetto chiede i suoi
                        // parametri di connessione. Dal firmware 2.3 un tasto premuto intanto resta in coda.
                        delay(400)
                        runCatching { g.discoverServices() }
                    } else {
                        setupWatchdog?.cancel()
                        pendingOp?.complete(-1)
                        runCatching { g.close() }
                        gatt = null
                        val wasReady = state == LinkState.READY
                        if (wasReady) failures = 0 else failures++
                        if (state != LinkState.POWERED_OFF) changeState(LinkState.IDLE)
                        scheduleReconnect(wasBackground = background, afterLoss = wasReady)
                    }
                }
            }

            override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                scope.launch { if (g === gatt) setup(g) }
            }

            override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) this@BandLink.mtu = mtu
                completeOp(OpKind.MTU, status)
            }

            override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
                completeOp(OpKind.DESCRIPTOR, status)
            }

            override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
                completeOp(OpKind.WRITE, status)
            }

            override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) handle(c.uuid, value, fromRead = true)
                completeOp(OpKind.READ, status)
            }

            @Suppress("OVERRIDE_DEPRECATION")
            override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
                if (Build.VERSION.SDK_INT >= 33) return
                @Suppress("DEPRECATION")
                if (status == BluetoothGatt.GATT_SUCCESS) handle(c.uuid, c.value ?: byteArrayOf(), fromRead = true)
                completeOp(OpKind.READ, status)
            }

            override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
                handle(c.uuid, value, fromRead = false)
            }

            @Suppress("OVERRIDE_DEPRECATION")
            override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
                if (Build.VERSION.SDK_INT >= 33) return
                @Suppress("DEPRECATION")
                handle(c.uuid, c.value ?: byteArrayOf(), fromRead = false)
            }
        }

        private fun handle(uuid: UUID, value: ByteArray, fromRead: Boolean) {
            val copy = value.copyOf()
            scope.launch {
                // Con l'MTU minimo (23) le notifiche si fermano a 20 byte: impostazioni e stato arriverebbero
                // tronchi. Una notifica piena si rilegge: la lettura prende il valore intero, a pezzi.
                if (!fromRead && (uuid == BandProtocol.CONFIG || uuid == BandProtocol.STATUS) && copy.size >= mtu - 3) {
                    readFull(uuid)
                    return@launch
                }
                when (uuid) {
                    BandProtocol.EVENT -> onEvent(copy)
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

        private fun onEvent(bytes: ByteArray) {
            val ev = BandProtocol.parseEvent(bytes) ?: return
            if (ev.boot != null) {
                // Firmware 2.3: si conferma sempre (anche un doppione: la conferma di prima può essere andata
                // persa), ma si applica una volta sola.
                acks.trySend(ev.seq)
                if (!dedupe.firstTime(address, ev.boot, ev.seq, SystemClock.elapsedRealtime())) {
                    Log.i(TAG, "${side.name}: evento ${ev.seq} già ricevuto, solo confermato")
                    return
                }
                if (ev.ageMs > 0) Log.i(TAG, "${side.name}: evento ${ev.seq} rimandato dopo ${ev.ageMs} ms")
            } else {
                if (ev.seq == lastSeq) return // stessa pressione ricevuta due volte
                lastSeq = ev.seq
            }
            if (ev.type == BandProtocol.EVT_POWER_OFF) changeState(LinkState.POWERED_OFF)
            val reason = if (ev.type == BandProtocol.EVT_POWER_OFF) ev.extra else null
            _events.tryEmit(BandEvent(side, ev.type, reason))
        }

        private suspend fun readFull(uuid: UUID) {
            val g = gatt ?: return
            val c = g.getService(BandProtocol.SERVICE)?.getCharacteristic(uuid) ?: return
            op(OpKind.READ) { g.readCharacteristic(c) }
        }

        private suspend fun setup(g: BluetoothGatt) {
            val svc = g.getService(BandProtocol.SERVICE)
            val evt = svc?.getCharacteristic(BandProtocol.EVENT)
            if (svc == null || evt == null) {
                runCatching { g.disconnect() }
                return
            }
            // Prima di tutto i tasti: finché le notifiche di EVENT non sono attive il braccialetto non può
            // mandarli (i firmware prima della 2.3 li perdono). "H|1" va prima: con le notifiche il firmware 2.3
            // rimanda subito i tasti in coda e deve già sapere che li confermiamo. I firmware vecchi lo ignorano.
            write(BandProtocol.HELLO)
            // Senza notifiche i tasti del braccialetto andrebbero persi: meglio riconnettersi.
            if (!enableNotify(g, evt)) {
                runCatching { g.disconnect() }
                return
            }
            // Senza risposta resta l'MTU minimo: le notifiche lunghe si rileggono (vedi handle()).
            op(OpKind.MTU) { g.requestMtu(185) }
            g.getService(BandProtocol.BATTERY_SERVICE)?.getCharacteristic(BandProtocol.BATTERY_LEVEL)?.let {
                enableNotify(g, it)
                op(OpKind.READ) { g.readCharacteristic(it) }
            }
            svc.getCharacteristic(BandProtocol.STATUS)?.let {
                enableNotify(g, it)
                op(OpKind.READ) { g.readCharacteristic(it) }
            }
            svc.getCharacteristic(BandProtocol.CONFIG)?.let {
                enableNotify(g, it)
                op(OpKind.READ) { g.readCharacteristic(it) }
            }
            // Connessione a basso consumo (intervallo ~100 ms, latenza 2): la radio del braccialetto
            // si sveglia molto meno spesso; un tasto arriva comunque entro ~125 ms.
            runCatching { g.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_LOW_POWER) }
            if (g !== gatt) return
            setupWatchdog?.cancel()
            failures = 0
            changeState(LinkState.READY)
            _ready.tryEmit(side)
            wake.trySend(Unit)  // un messaggio rimasto in sospeso parte quando finisce "PAIRING OK" (quietUntil)
        }

        /** Android esegue un'operazione GATT alla volta: le serializziamo e aspettiamo la callback. */
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
            val d = c.getDescriptor(BandProtocol.CCCD) ?: return false
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

        suspend fun command(msg: String): Boolean = state == LinkState.READY && write(msg)

        suspend fun writeConfig(text: String): Boolean = state == LinkState.READY && write(text, BandProtocol.CONFIG)

        private suspend fun write(msg: String, uuid: UUID = BandProtocol.DISPLAY): Boolean {
            val g = gatt ?: return false
            val c = g.getService(BandProtocol.SERVICE)?.getCharacteristic(uuid) ?: return false
            val bytes = msg.toByteArray(Charsets.US_ASCII).copyOf(minOf(msg.length, 180))
            return op(OpKind.WRITE) {
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
        private const val TAG = "BleManager"
        /** Tentativi diretti a vuoto prima di aspettare il braccialetto in background (~1,5 minuti). */
        private const val DIRECT_TRIES = 3
        private const val DEFAULT_MTU = 23
        /** Il firmware mostra "PAIRING OK" per 3 s dal collegamento: prima nessun messaggio sul display. */
        private const val PAIRED_MSG_MS = 3_200L
        private const val SCAN_STOP_GRACE_MS = 1_500L
        private const val SCAN_RETRY_MS = 5_000L
        private const val SCAN_FAILED_ALREADY_STARTED = 1  // ScanCallback.SCAN_FAILED_ALREADY_STARTED
        private const val SCAN_FAILED_TOO_FREQUENTLY = 6   // ScanCallback.SCAN_FAILED_SCANNING_TOO_FREQUENTLY (API 33)

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

/**
 * Android blocca la sesta ricerca avviata in 30 s (fino ad Android 12 in silenzio: nessun errore, nessun
 * risultato). Si tiene il conto degli avvii e si aspetta, con un margine: al massimo [maxStarts] ogni [windowMs].
 */
internal class ScanThrottle(private val maxStarts: Int = 4, private val windowMs: Long = 30_000) {
    private val starts = ArrayDeque<Long>()

    /** Millisecondi da aspettare prima del prossimo avvio (0 = subito). */
    fun delayBeforeStart(now: Long): Long {
        while (starts.isNotEmpty() && now - starts.first() >= windowMs) starts.removeFirst()
        return if (starts.size < maxStarts) 0 else starts.first() + windowMs - now + 500
    }

    fun recordStart(now: Long) {
        starts.addLast(now)
        while (starts.size > maxStarts) starts.removeFirst()
    }
}
