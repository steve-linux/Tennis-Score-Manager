package com.tennis.scoremanager.tv

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Piccolo server web sul telefono dell'arbitro, attivo solo con il tabellone TV acceso.
 *   /        la pagina del tabellone (assets/scoreboard.html)
 *   /events  aggiornamenti in diretta (Server-Sent Events): un JSON a ogni punto e ogni 5 secondi
 *   /state   l'ultimo JSON (serve anche alla ricerca automatica del telefono-tabellone)
 * Solo lettura: dal tabellone non si può cambiare niente. Si annuncia sulla rete come "_tsm._tcp"
 * e tiene agganciata la rete Wi-Fi anche se non ha internet (hotspot dell'altro telefono).
 */
class TvServer(context: Context) {

    companion object {
        const val PORT = 8080
        const val SERVICE_TYPE = "_tsm._tcp"
        private const val MAX_STREAMS = 12
        private const val STOP = "\u0000stop"
        private const val TAG = "TvServer"

        /**
         * Indirizzi IPv4 locali del telefono, prima Wi-Fi e hotspot. Esclusi i dati mobili e le VPN:
         * da lì il tabellone non si raggiunge.
         */
        fun localAddresses(): List<String> = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback && !skipInterface(it.name) }
                .sortedBy { interfaceRank(it.name) }
                .flatMap { nif ->
                    nif.inetAddresses.toList().filterIsInstance<Inet4Address>()
                        .filter { it.isSiteLocalAddress }
                        .mapNotNull { it.hostAddress }
                }
                .distinct()
        }.getOrDefault(emptyList())

        private fun skipInterface(name: String): Boolean =
            listOf("rmnet", "r_rmnet", "ccmni", "v4-", "dummy", "tun", "ppp", "ipsec", "clat").any { name.startsWith(it) }

        /** wlan0 = collegato a una rete; swlan/ap/wlan1 = hotspot di questo telefono; poi USB ed Ethernet. */
        private fun interfaceRank(name: String): Int = when {
            name == "wlan0" -> 0
            name.startsWith("swlan") || name.startsWith("ap") || name.startsWith("softap") || name.startsWith("wlan") -> 1
            name.startsWith("rndis") || name.startsWith("usb") || name.startsWith("eth") -> 2
            else -> 3
        }
    }

    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pool = Executors.newCachedThreadPool()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running
    private val _port = MutableStateFlow<Int?>(null)
    val port: StateFlow<Int?> = _port
    private val _clients = MutableStateFlow(0)
    /** Tabelloni collegati in questo momento. */
    val clients: StateFlow<Int> = _clients
    private val _addresses = MutableStateFlow<List<String>>(emptyList())
    val addresses: StateFlow<List<String>> = _addresses

    @Volatile private var latest: String = "{}"
    @Volatile private var server: ServerSocket? = null
    private val streams = CopyOnWriteArrayList<LinkedBlockingQueue<String>>()
    private var addressJob: Job? = null
    private var nsdListener: NsdManager.RegistrationListener? = null
    private var wifiCallback: ConnectivityManager.NetworkCallback? = null
    private val page: ByteArray by lazy { app.assets.open("scoreboard.html").use { it.readBytes() } }

    /** Indirizzo da mostrare e da mettere nel QR, es. http://192.168.43.1:8080/ (null = nessuna rete locale). */
    fun url(address: String? = _addresses.value.firstOrNull()): String? {
        val p = _port.value ?: return null
        return address?.let { "http://$it:$p/" }
    }

    @Synchronized
    fun start() {
        if (server != null) return
        val socket = (PORT until PORT + 10).firstNotNullOfOrNull { p ->
            runCatching { ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(p)) } }.getOrNull()
        } ?: run {
            Log.w(TAG, "Nessuna porta libera tra $PORT e ${PORT + 9}")
            return
        }
        server = socket
        _port.value = socket.localPort
        _running.value = true
        pool.execute { acceptLoop(socket) }
        registerNsd(socket.localPort)
        keepWifi()
        addressJob = scope.launch {
            while (isActive) {
                _addresses.value = localAddresses()
                delay(3_000)
            }
        }
        Log.i(TAG, "Tabellone su porta ${socket.localPort}")
    }

    @Synchronized
    fun stop() {
        val socket = server ?: return
        server = null
        runCatching { socket.close() }
        streams.forEach { it.offer(STOP) }
        streams.clear()
        _clients.value = 0
        _running.value = false
        _port.value = null
        addressJob?.cancel()
        addressJob = null
        unregisterNsd()
        releaseWifi()
    }

    /** Nuovo stato del tabellone: va subito a tutti i tabelloni collegati. */
    fun publish(json: String) {
        latest = json
        for (q in streams) {
            if (q.size > 16) q.clear()  // tabellone bloccato: meglio perdere i vecchi che riempire la memoria
            q.offer(json)
        }
    }

    // ---------------------------------------------------------------- HTTP

    private fun acceptLoop(socket: ServerSocket) {
        while (!socket.isClosed) {
            val client = try {
                socket.accept()
            } catch (e: IOException) {
                break
            }
            pool.execute { runCatching { handle(client) }.onFailure { Log.d(TAG, "richiesta interrotta: $it") } }
        }
    }

    private fun handle(sock: Socket) {
        sock.use { s ->
            s.soTimeout = 10_000
            s.tcpNoDelay = true
            val input = s.getInputStream()
            val requestLine = readLine(input) ?: return
            while (true) {
                val header = readLine(input) ?: return
                if (header.isEmpty()) break
            }
            val parts = requestLine.split(' ')
            if (parts.size < 2) return
            val method = parts[0]
            val path = parts[1].substringBefore('?')
            val out = BufferedOutputStream(s.getOutputStream())
            val head = method == "HEAD"
            if (method != "GET" && !head) {
                respond(out, "405 Method Not Allowed", "text/plain", "GET only".toByteArray(), head)
                return
            }
            when (path) {
                "/", "/index.html" -> respond(out, "200 OK", "text/html; charset=utf-8", page, head)
                "/state", "/state.json" -> respond(out, "200 OK", "application/json; charset=utf-8", latest.toByteArray(), head)
                "/events" -> if (head) respond(out, "200 OK", "text/event-stream", ByteArray(0), true) else stream(s, out)
                "/favicon.ico" -> respond(out, "204 No Content", "text/plain", ByteArray(0), head)
                else -> respond(out, "404 Not Found", "text/plain", "Not found".toByteArray(), head)
            }
        }
    }

    /** Riga della richiesta HTTP (max 4 KB), senza \r\n; null = connessione chiusa. */
    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        while (sb.length < 4096) {
            val c = input.read()
            if (c < 0) return if (sb.isEmpty()) null else sb.toString()
            if (c == '\n'.code) return sb.toString().trimEnd('\r')
            sb.append(c.toChar())
        }
        return sb.toString()
    }

    private fun respond(out: OutputStream, status: String, type: String, body: ByteArray, head: Boolean) {
        val headers = "HTTP/1.1 $status\r\n" +
            "Content-Type: $type\r\n" +
            "Content-Length: ${body.size}\r\n" +
            "Cache-Control: no-store\r\n" +
            "Access-Control-Allow-Origin: *\r\n" +
            "Connection: close\r\n\r\n"
        out.write(headers.toByteArray(Charsets.ISO_8859_1))
        if (!head) out.write(body)
        out.flush()
    }

    /** Flusso SSE: lo stato attuale subito, poi ogni aggiornamento; un commento ogni 15" se tutto tace. */
    private fun stream(s: Socket, out: OutputStream) {
        if (streams.size >= MAX_STREAMS) {
            respond(out, "503 Service Unavailable", "text/plain", "Troppi tabelloni".toByteArray(), false)
            return
        }
        val q = LinkedBlockingQueue<String>()
        streams += q
        _clients.value = streams.size
        try {
            s.soTimeout = 0
            out.write(
                ("HTTP/1.1 200 OK\r\n" +
                    "Content-Type: text/event-stream; charset=utf-8\r\n" +
                    "Cache-Control: no-store\r\n" +
                    "Access-Control-Allow-Origin: *\r\n" +
                    "Connection: keep-alive\r\n\r\n" +
                    "retry: 2000\n\n").toByteArray(Charsets.UTF_8),
            )
            out.write("data: $latest\n\n".toByteArray(Charsets.UTF_8))
            out.flush()
            while (server != null) {
                val msg = q.poll(15, TimeUnit.SECONDS)
                if (msg == STOP) break
                out.write((if (msg == null) ": ping\n\n" else "data: $msg\n\n").toByteArray(Charsets.UTF_8))
                out.flush()
            }
        } catch (e: IOException) {
            // tabellone chiuso o fuori portata: se ne va da solo
        } finally {
            streams -= q
            _clients.value = streams.size
        }
    }

    // ---------------------------------------------------------------- rete

    private fun registerNsd(port: Int) {
        val nsd = app.getSystemService(NsdManager::class.java) ?: return
        val info = NsdServiceInfo().apply {
            serviceName = "TSM Tabellone"
            serviceType = SERVICE_TYPE
            setPort(port)
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                Log.i(TAG, "Annunciato come ${info.serviceName}")
            }

            override fun onRegistrationFailed(info: NsdServiceInfo, error: Int) {
                Log.w(TAG, "Annuncio fallito: $error")
            }
            override fun onServiceUnregistered(info: NsdServiceInfo) {}
            override fun onUnregistrationFailed(info: NsdServiceInfo, error: Int) {}
        }
        runCatching { nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener) }
            .onSuccess { nsdListener = listener }
            .onFailure { Log.w(TAG, "NSD", it) }
    }

    private fun unregisterNsd() {
        val l = nsdListener ?: return
        nsdListener = null
        runCatching { app.getSystemService(NsdManager::class.java)?.unregisterService(l) }
    }

    /**
     * Collegato all'hotspot di un altro telefono (senza internet) Android potrebbe lasciare la rete Wi-Fi:
     * una richiesta "Wi-Fi anche senza internet" la tiene agganciata finché il tabellone è acceso.
     */
    private fun keepWifi() {
        val cm = app.getSystemService(ConnectivityManager::class.java) ?: return
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                _addresses.value = localAddresses()
            }

            override fun onLost(network: Network) {
                _addresses.value = localAddresses()
            }
        }
        runCatching { cm.requestNetwork(request, cb) }
            .onSuccess { wifiCallback = cb }
            .onFailure { Log.w(TAG, "requestNetwork", it) }
    }

    private fun releaseWifi() {
        val cb = wifiCallback ?: return
        wifiCallback = null
        runCatching { app.getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(cb) }
    }
}
