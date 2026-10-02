package com.tennis.scoremanager.tv

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import javax.net.SocketFactory

/**
 * Telefono dell'arbitro trovato: [network] è la rete da usare per raggiungerlo (null = quella normale).
 * [id] identifica il telefono (null = versione dell'app senza), [title] è la scritta del tabellone (circolo e campo).
 */
data class FoundScoreboard(
    val host: String,
    val port: Int,
    val network: Network?,
    val id: String? = null,
    val title: String = "",
) {
    val url: String get() = "http://$host:$port/?display=app"
    val label: String get() = "$host:$port"

    /**
     * Lo stesso tabellone di prima, per riprendere dopo un'interruzione senza saltare su un altro campo della
     * stessa rete: stesso telefono, oppure stesso indirizzo, oppure stessa scritta (stesso circolo e campo).
     */
    fun sameAs(prev: FoundScoreboard): Boolean =
        (id != null && id == prev.id) || label == prev.label || (title.isNotEmpty() && title == prev.title)
}

/**
 * Cerca il telefono dell'arbitro sulla rete locale, senza chiedere nulla all'utente:
 *   1. l'ultimo indirizzo che ha funzionato;
 *   2. l'annuncio "_tsm._tcp" sulla rete (NSD/mDNS);
 *   3. una scansione della rete dell'hotspot (al massimo 1024 indirizzi, porta 8080 e seguenti).
 * Ogni candidato si conferma chiedendo /state: deve rispondere il JSON del tabellone ("tsm":1).
 */
class ScoreboardFinder(context: Context) {

    private val app = context.applicationContext
    private val cm = app.getSystemService(ConnectivityManager::class.java)

    /**
     * [accept] scarta i tabelloni che non vanno bene (questo stesso telefono, un altro campo) e la ricerca continua:
     * vale anche per [lastKnown].
     */
    suspend fun find(
        lastKnown: String?,
        timeoutMs: Long = 20_000,
        accept: (FoundScoreboard) -> Boolean = { true },
    ): FoundScoreboard? = withContext(Dispatchers.IO) {
        lastKnown?.let { parseAddress(it) }?.let { (h, p) -> verify(h, p)?.takeIf(accept)?.let { return@withContext it } }
        val result = CompletableDeferred<FoundScoreboard?>()
        val job = launch {
            launch { nsd(accept)?.let { result.complete(it) } }
            for (port in listOf(TvServer.PORT, TvServer.PORT + 1, TvServer.PORT + 2)) {
                if (result.isCompleted) break
                scan(port, accept)?.let { result.complete(it) }
            }
            // scansione finita senza risultato: qualche secondo ancora per l'annuncio NSD
            delay(3_000)
            result.complete(null)
        }
        val found = withTimeoutOrNull(timeoutMs) { result.await() }
        job.cancel()
        found
    }


    /** Conferma che a [host]:[port] c'è un tabellone TSM (con la rete giusta per raggiungerlo). */
    fun verify(host: String, port: Int): FoundScoreboard? {
        val network = networkFor(host)
        val factory = network?.socketFactory ?: SocketFactory.getDefault()
        return runCatching {
            factory.createSocket().use { s ->
                s.connect(InetSocketAddress(host, port), 700)
                s.soTimeout = 1_500
                s.getOutputStream().write("GET /state HTTP/1.0\r\nHost: $host\r\n\r\n".toByteArray())
                val response = readAll(s.getInputStream(), 16_384)
                if ("\"tsm\":1" in response) {
                    val (id, title) = identity(response)
                    FoundScoreboard(host, port, network, id, title)
                } else null
            }
        }.getOrNull()
    }

    // ---------------------------------------------------------------- NSD

    private suspend fun nsd(accept: (FoundScoreboard) -> Boolean): FoundScoreboard? {
        val nsd = app.getSystemService(NsdManager::class.java) ?: return null
        val services = kotlinx.coroutines.channels.Channel<NsdServiceInfo>(8)
        val listener = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(info: NsdServiceInfo) { services.trySend(info) }
            override fun onDiscoveryStarted(type: String) {}
            override fun onDiscoveryStopped(type: String) {}
            override fun onServiceLost(info: NsdServiceInfo) {}
            override fun onStartDiscoveryFailed(type: String, error: Int) { services.close() }
            override fun onStopDiscoveryFailed(type: String, error: Int) {}
        }
        runCatching { nsd.discoverServices(TvServer.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener) }.onFailure { return null }
        try {
            for (info in services) {
                val resolved = resolve(nsd, info) ?: continue
                @Suppress("DEPRECATION")
                val host = (resolved.host as? Inet4Address)?.hostAddress ?: continue
                verify(host, resolved.port)?.takeIf(accept)?.let { return it }
            }
        } finally {
            runCatching { nsd.stopServiceDiscovery(listener) }
        }
        return null
    }

    @Suppress("DEPRECATION")
    private suspend fun resolve(nsd: NsdManager, info: NsdServiceInfo): NsdServiceInfo? {
        val done = CompletableDeferred<NsdServiceInfo?>()
        runCatching {
            nsd.resolveService(info, object : NsdManager.ResolveListener {
                override fun onResolveFailed(i: NsdServiceInfo, error: Int) { done.complete(null) }
                override fun onServiceResolved(i: NsdServiceInfo) { done.complete(i) }
            })
        }.onFailure { return null }
        return withTimeoutOrNull(5_000) { done.await() }
    }

    // ---------------------------------------------------------------- scansione

    /** Reti IPv4 locali: indirizzo di questo telefono e lunghezza del prefisso (es. 192.168.43.12/24). */
    private data class Lan(val self: Int, val prefix: Int, val iface: String)

    private fun lans(): List<Lan> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback && !it.name.startsWith("rmnet") && !it.name.startsWith("dummy") && !it.name.startsWith("tun") }
            .flatMap { nif ->
                nif.interfaceAddresses.mapNotNull { a ->
                    val ip = a.address as? Inet4Address ?: return@mapNotNull null
                    if (!ip.isSiteLocalAddress || a.networkPrefixLength !in 20..30) return@mapNotNull null
                    Lan(toInt(ip), a.networkPrefixLength.toInt(), nif.name)
                }
            }
    }.getOrDefault(emptyList())

    private suspend fun scan(port: Int, accept: (FoundScoreboard) -> Boolean): FoundScoreboard? = coroutineScope {
        val gate = Semaphore(48)
        for (lan in lans()) {
            val mask = -1 shl (32 - lan.prefix)
            val base = lan.self and mask
            val size = (1 shl (32 - lan.prefix)).coerceAtMost(1024)
            // prima il probabile router/hotspot (.1), poi il resto
            val hosts = (listOf(base + 1) + (1 until size - 1).map { base + it }).distinct().filter { it != lan.self }
            val found = CompletableDeferred<FoundScoreboard?>()
            val jobs = hosts.map { ipInt ->
                async {
                    gate.withPermit {
                        ensureActive()
                        if (!found.isCompleted) verify(fromInt(ipInt), port)?.takeIf(accept)?.let { found.complete(it) }
                    }
                }
            }
            launch { jobs.awaitAll(); found.complete(null) }
            val hit = found.await()
            jobs.forEach { it.cancel() }
            if (hit != null) {
                Log.i("ScoreboardFinder", "Trovato ${hit.label} su ${lan.iface}")
                return@coroutineScope hit
            }
        }
        null
    }

    /**
     * La rete (Network) da cui si raggiunge [host]: serve quando questo telefono è collegato all'hotspot
     * dell'altro e Android, vedendola senza internet, manderebbe il traffico sui dati mobili.
     * Se il telefono è lui stesso l'hotspot la rete non è una "Network" di Android: null va bene.
     */
    fun networkFor(host: String): Network? {
        val target = runCatching { toInt(java.net.InetAddress.getByName(host) as Inet4Address) }.getOrNull() ?: return null
        @Suppress("DEPRECATION")
        val all = cm?.allNetworks.orEmpty()
        return all.firstOrNull { n ->
            cm?.getLinkProperties(n)?.linkAddresses.orEmpty().any { la ->
                val ip = la.address as? Inet4Address ?: return@any false
                val p = la.prefixLength
                val mask = if (p == 0) 0 else -1 shl (32 - p)
                (toInt(ip) and mask) == (target and mask)
            }
        }
    }

    private fun readAll(input: java.io.InputStream, max: Int): String {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(2048)
        while (out.size() < max) {
            val n = input.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.toString("UTF-8")
    }

    private fun toInt(ip: Inet4Address): Int = ip.address.fold(0) { acc, b -> (acc shl 8) or (b.toInt() and 0xFF) }

    private fun fromInt(v: Int): String = "${v ushr 24 and 0xFF}.${v ushr 16 and 0xFF}.${v ushr 8 and 0xFF}.${v and 0xFF}"

    companion object {
        /** Identità e scritta del tabellone dalla risposta di /state (intestazioni + JSON); vuote se mancano. */
        fun identity(response: String): Pair<String?, String> {
            val head = response.substringBefore("\r\n\r\n")
            val id = head.lineSequence().firstOrNull { it.startsWith(TvServer.ID_HEADER + ":", ignoreCase = true) }
                ?.substringAfter(':')?.trim()?.takeIf { it.isNotEmpty() }
            val title = runCatching {
                Json.parseToJsonElement(response.substringAfter("\r\n\r\n")).jsonObject["title"]?.jsonPrimitive?.contentOrNull
            }.getOrNull().orEmpty()
            return id to title
        }

        /** "192.168.43.1", "192.168.43.1:8081" o "http://192.168.43.1:8080/" -> host e porta. */
        fun parseAddress(text: String): Pair<String, Int>? {
            val t = text.trim().removePrefix("http://").removePrefix("https://").substringBefore('/')
            if (t.isEmpty()) return null
            val host = t.substringBefore(':')
            val port = t.substringAfter(':', "").toIntOrNull() ?: TvServer.PORT
            return if (host.isNotEmpty() && port in 1..65535) host to port else null
        }

        /** Per i log: modello e Android, utile se la ricerca fallisce su un telefono particolare. */
        val device: String get() = "${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})"
    }
}
