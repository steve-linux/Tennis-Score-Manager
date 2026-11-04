// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.desk

import android.content.Context
import android.util.Log
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.tennis.scoremanager.MatchController
import com.tennis.scoremanager.data.ClubBook
import com.tennis.scoremanager.ClubFeature
import com.tennis.scoremanager.data.BookingRules
import com.tennis.scoremanager.data.CTEntry
import com.tennis.scoremanager.data.ClubServerConf
import com.tennis.scoremanager.data.ClubServers
import com.tennis.scoremanager.data.SrvAdmin
import com.tennis.scoremanager.data.CourtBlock
import com.tennis.scoremanager.data.CourtInfo
import com.tennis.scoremanager.data.CourtSettings
import com.tennis.scoremanager.data.Course
import com.tennis.scoremanager.data.Ladder
import com.tennis.scoremanager.data.Fixture
import com.tennis.scoremanager.data.Team
import com.tennis.scoremanager.data.ClubTournament
import com.tennis.scoremanager.data.CourtBooking
import com.tennis.scoremanager.data.CourtPlan
import com.tennis.scoremanager.data.PayType
import com.tennis.scoremanager.data.PlayerProfile
import com.tennis.scoremanager.service.DeskService
import com.tennis.scoremanager.tv.TvServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap

/** I dati veri dell'app per la Segreteria da PC: ogni richiesta gira sul filo principale, come i tocchi sul telefono. */
class DeskBridge(private val c: MatchController) : DeskData {
    override fun <T> tx(f: () -> T): T = runBlocking(Dispatchers.Main.immediate) { f() }
    override val club: String get() = c.storage.clubName
    override val lang: String get() = c.options.value.lang.code
    override fun now(): Long = System.currentTimeMillis()
    override fun players(): List<PlayerProfile> = c.players.value
    override fun savePlayers(list: List<PlayerProfile>) = c.savePlayers(list)
    override fun book(): ClubBook = c.clubBook.value
    override fun saveBook(b: ClubBook) = c.saveClubBook(b)
    override fun payTypes(): List<PayType> = c.payTypes()
    override fun courts(): List<String> = c.clubCourts.value
    override fun plan(): CourtPlan = c.courtPlan.value
    override fun tournaments(): List<ClubTournament> = c.clubTournaments.value
    override fun saveBooking(b: CourtBooking, weeks: Int, charge: Boolean): List<Long> = c.saveBooking(b, weeks, charge)
    override fun deleteBooking(b: CourtBooking, series: Boolean) = c.deleteBooking(b, wholeSeries = series)
    override fun saveBlock(b: CourtBlock, delete: Boolean) = c.saveCourtBlock(b, delete)
    override fun features(): Set<ClubFeature> = c.clubFeatures.value
    override fun courses(): List<Course> = c.courses.value
    override fun teams(): List<Team> = c.teams.value
    override fun ladders(): List<Ladder> = c.ladders.value
    override fun saveCourse(c: Course) = this.c.saveCourse(c)
    override fun deleteCourse(c: Course) = this.c.deleteCourse(c.id)
    override fun courseFeeLabel(c: Course, month: Pair<Int, Int>?): String = this.c.courseFeeLabel(c, month)
    override fun chargeCourse(c: Course, month: Pair<Int, Int>?): Int = this.c.chargeCourse(c, month)
    override fun dues(charges: Set<String>): List<DeskDue> = c.duesRecipients(charges).map { DeskDue(it.id, it.name, it.phone, it.email, it.text) }
    override fun sendDues(charges: Set<String>) = c.noticeDues(charges)
    override fun saveTeam(t: Team) = c.saveTeam(t)
    override fun deleteTeam(t: Team) = c.deleteTeam(t.id)
    override fun teamNotice(t: Team, f: Fixture, kind: String) = when (kind) {
        "AVAILABILITY" -> c.noticeAvailability(t, f, missingOnly = false)
        "MISSING" -> c.noticeAvailability(t, f, missingOnly = true)
        "CALLUP" -> c.noticeCallUp(t, f)
        else -> c.noticeResult(t, f)
    }
    override fun setClubName(name: String) { c.storage.clubName = name }
    override fun saveCourtSettings(st: CourtSettings) = c.saveCourtSettings(st)
    override fun addCourt(info: CourtInfo): Boolean = c.addCourt(info)
    override fun renameCourt(old: String, new: String): Boolean = c.renameCourt(old, new)
    override fun saveCourtInfo(info: CourtInfo) = c.saveCourtInfo(info)
    override fun deleteCourt(name: String) = c.deleteCourt(name)
    override fun moveCourt(name: String, up: Boolean) = c.moveCourt(name, up)
    override fun savePayTypes(list: List<PayType>) = c.savePayTypes(list)
    override fun serverConf(): ClubServerConf = c.clubServer.value
    override fun saveServerRules(rules: BookingRules, payInfo: String, alerts: Boolean) {
        c.saveServerRules(rules, payInfo)
        c.setServerAlerts(alerts)
    }
    // Rete: gira sul filo della richiesta, mai su quello principale.
    override fun serverState(): SrvAdmin = ClubServers.withServer(c.clubServer.value) { it.state() }
    override fun serverSync(): String? = runBlocking(Dispatchers.Main.immediate) { c.syncServerNow() }
    override fun serverRemove(kind: String, id: String) {
        ClubServers.withServer(c.clubServer.value) { if (kind == "wait") it.removeWaitAdmin(id) else it.removeSeekAdmin(id) }
    }
    override fun receiptPdf(chargeId: String, paymentId: String): ByteArray? = c.receiptFile(chargeId, paymentId)?.let { f -> f.readBytes().also { f.delete() } }
    override fun paymentsCsv(year: Int): ByteArray? = c.paymentsCsv(year)?.readBytes()
    override fun saveTournament(t: ClubTournament) = c.saveClubTournament(t)
    override fun deleteTournament(t: ClubTournament) = c.deleteClubTournament(t.id)
    override fun addEntry(t: ClubTournament, people: List<PlayerProfile>) = c.addClubEntry(t, people)
    override fun removeEntry(t: ClubTournament, entry: CTEntry) = c.removeClubEntry(t, entry.id)
}

/**
 * La Segreteria da PC sul telefono: acceso solo quando serve. Si spegne da solo dopo mezz'ora senza nessuno collegato.
 * Il certificato sta nella cartella esclusa dai backup: la chiave privata non esce mai dal telefono.
 */
class DeskManager(context: Context, private val data: DeskData) {

    data class Ui(
        val running: Boolean = false, val urls: List<String> = emptyList(), val code: String = "", val waitingIp: String? = null,
        val connectedIp: String? = null, val fingerprint: String = "", val failed: Boolean = false,
    )

    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _ui = MutableStateFlow(Ui())
    val ui: StateFlow<Ui> = _ui
    private val auth = DeskAuth()
    private var server: DeskServer? = null
    private var ticker: Job? = null
    private val files = ConcurrentHashMap<String, ByteArray>()

    private fun asset(name: String): ByteArray? = files[name] ?: runCatching { app.assets.open("desk/$name").use { it.readBytes() } }.getOrNull()?.also { files[name] = it }

    fun start() {
        scope.launch {
            synchronized(this@DeskManager) {
                if (server != null) return@launch
                try {
                    val cert = DeskCert.loadOrCreate(app.noBackupFilesDir)
                    val s = DeskServer(cert, DeskApi(data, auth, qr = ::qrRows), ::asset, { TvServer.isLocalPeer(it) })
                    s.start()
                    server = s
                    _ui.value = Ui(running = true, fingerprint = cert.fingerprint)
                } catch (e: Exception) {
                    Log.w(TAG, "Avvio non riuscito", e)
                    _ui.value = Ui(failed = true)
                    return@launch
                }
            }
            DeskService.start(app)
            tick()
        }
    }

    /** Ogni secondo: codice, PC in attesa e indirizzi aggiornati; dopo mezz'ora vuota si spegne. */
    private fun tick() {
        ticker?.cancel()
        ticker = scope.launch {
            var active = System.currentTimeMillis()
            while (isActive) {
                val port = server?.port ?: break
                val connected = auth.connected
                if (connected != null || auth.pending != null) active = System.currentTimeMillis()
                _ui.value = _ui.value.copy(
                    urls = TvServer.localAddresses().map { "https://$it:$port" }, code = auth.shownCode, waitingIp = auth.pending?.ip, connectedIp = connected,
                )
                if (System.currentTimeMillis() - active > DeskAuth.IDLE_MS) { stop(); break }
                delay(1_000)
            }
        }
    }

    private fun qrRows(text: String): List<String> {
        val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 0))
        return (0 until m.height).map { y -> buildString { for (x in 0 until m.width) append(if (m[x, y]) '1' else '0') } }
    }

    /** Il telefono ha inquadrato un QR: false se non è quello di un PC in attesa. */
    fun scan(text: String): Boolean = auth.scan(text)

    /** La risposta del telefono al PC che chiede di entrare. */
    fun answer(allow: Boolean) = auth.answer(allow)

    /** Chiude il PC collegato; il server resta acceso con un codice nuovo. */
    fun disconnect() = auth.logout()

    @Synchronized
    fun stop() {
        ticker?.cancel()
        ticker = null
        auth.logout()
        server?.stop()
        server = null
        files.clear()
        _ui.value = Ui()
        DeskService.stop(app)
    }

    companion object { private const val TAG = "DeskManager" }
}
