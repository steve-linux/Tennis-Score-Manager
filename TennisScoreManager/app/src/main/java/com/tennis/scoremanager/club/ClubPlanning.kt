// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later
package com.tennis.scoremanager.club

import android.app.Application
import com.tennis.scoremanager.Screen
import com.tennis.scoremanager.data.Storage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import androidx.core.graphics.scale

/**
 * Il planning dei campi: i campi del circolo con caratteristiche, foto e prezzi, le prenotazioni (con gli addebiti
 * nel registro), i periodi non prenotabili. Il resto che serve è in [ClubHost].
 */
class ClubPlanning(
    host: ClubHost,
    private val app: Application,
    private val storage: Storage,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
) : ClubHost by host {

    override val courtPlan = MutableStateFlow(storage.loadCourtPlan())

    fun openCourtsPlan() {
        refreshPlayers()
        nav.push(Screen.COURTS_PLAN)
    }

    fun saveCourtSettings(st: com.tennis.scoremanager.data.CourtSettings) {
        val p = courtPlan.value.copy(settings = st)
        courtPlan.value = p
        scope.launch(io) { storage.saveCourtPlan(p) }
    }

    internal fun putBookings(list: List<com.tennis.scoremanager.data.CourtBooking>, remove: Set<String> = emptySet()) {
        val ids = list.map { it.id }.toSet() + remove
        val p = courtPlan.value.copy(bookings = courtPlan.value.bookings.filter { it.id !in ids } + list)
        courtPlan.value = p
        scope.launch(io) { storage.saveCourtPlan(p) }
    }

    /** "Campo 1 · sab 14 nov 18:00": la descrizione negli addebiti e negli avvisi. */
    fun bookingLabel(b: com.tennis.scoremanager.data.CourtBooking): String {
        val lang = options.value.lang
        val loc = lang.locale
        val day = java.text.SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(loc, "EEEdMMM"), loc).format(java.util.Date(b.start))
        return "${b.court} · $day ${com.tennis.scoremanager.data.Reports.time(b.start, lang)}"
    }

    /**
     * Salva una prenotazione (nuova o cambiata), ripetuta per [weeks] settimane; con [charge] il prezzo si divide tra
     * i giocatori del database e va nel registro. Restituisce le date saltate perché il campo era occupato.
     */
    fun saveBooking(b: com.tennis.scoremanager.data.CourtBooking, weeks: Int, charge: Boolean): List<Long> {
        val (list, skipped) = if (weeks > 1) com.tennis.scoremanager.data.CourtPlans.weekly(courtPlan.value, competitions.clubTournaments.value, b, weeks) else listOf(b) to emptyList()
        val old = courtPlan.value.bookings.firstOrNull { it.id == b.id }
        // Una prenotazione online spostata (campo, ora, durata) diventa della segreteria: sul server si toglie.
        val keepOnline = old?.online?.takeIf { old.court == b.court && old.start == b.start && old.minutes == b.minutes }
        old?.online?.let { id -> if (keepOnline == null) saveServer { it.copy(cancel = (it.cancel + id).distinct()) } }
        var book = clubBook.value
        // Cambiando una prenotazione, gli addebiti di prima non pagati si tolgono e si rifanno.
        old?.charges?.forEach { id -> if (book.charges.firstOrNull { it.id == id }?.payments.isNullOrEmpty()) book = com.tennis.scoremanager.data.Fees.cancel(book, id) }
        val now = System.currentTimeMillis()
        val saved = list.map { x ->
            val people = x.players.mapNotNull { playerById(it) }
            if (!charge || x.cents <= 0 || people.isEmpty()) x.copy(charges = old?.takeIf { it.id == x.id }?.charges?.filter { id -> book.charges.any { it.id == id } }.orEmpty())
            else {
                val parts = com.tennis.scoremanager.data.CourtPlans.split(x.cents, people.size)
                val before = book.charges.map { it.id }.toSet()
                people.forEachIndexed { i, p -> book = com.tennis.scoremanager.data.Fees.charge(book, p, bookingLabel(x), parts[i], now) }
                x.copy(charges = book.charges.map { it.id }.filter { it !in before })
            }
        }
        saveClubBook(book)
        putBookings(saved.map { it.copy(online = if (it.id == b.id) keepOnline else null) })
        return skipped
    }

    /** Com'è fatto un campo e i suoi prezzi. */
    /** I modi di pagamento del circolo (quelli proposti finché non se ne sceglie uno). */
    fun payTypes(): List<com.tennis.scoremanager.data.PayType> = com.tennis.scoremanager.data.Fees.types(clubBook.value) { com.tennis.scoremanager.ui.payMethodName(strings, it) }

    fun savePayTypes(list: List<com.tennis.scoremanager.data.PayType>) = saveClubBook(clubBook.value.copy(payTypes = list))

    fun saveCourtInfo(info: com.tennis.scoremanager.data.CourtInfo) {
        val st = courtPlan.value.settings
        saveCourtSettings(st.copy(courts = st.courts.filter { it.name != info.name } + info))
    }

    /** I campi del circolo, nell'ordine in cui si vedono (Nuova partita, planning, tornei). */
    val clubCourts = MutableStateFlow(storage.clubCourts)

    fun setClubCourts(list: List<String>) {
        val clean = list.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        storage.clubCourts = clean
        clubCourts.value = clean
    }

    /** Un campo nuovo con le sue caratteristiche; false se il nome c'è già. */
    fun addCourt(info: com.tennis.scoremanager.data.CourtInfo): Boolean {
        val name = info.name.trim()
        if (name.isEmpty() || clubCourts.value.any { it.equals(name, ignoreCase = true) }) return false
        setClubCourts(clubCourts.value + name)
        saveCourtInfo(info.copy(name = name))
        return true
    }

    /**
     * Rinomina un campo: lo seguono caratteristiche e prezzi, prenotazioni, periodi non prenotabili e partite dei
     * tornei. false se il nome nuovo è già di un altro campo.
     */
    fun renameCourt(old: String, new: String): Boolean {
        val n = new.trim()
        if (n.isEmpty()) return false
        if (n == old) return true
        if (clubCourts.value.any { it != old && it.equals(n, ignoreCase = true) }) return false
        setClubCourts(clubCourts.value.map { if (it == old) n else it })
        val p = courtPlan.value
        val np = p.copy(
            settings = p.settings.copy(courts = p.settings.courts.map { if (it.name == old) it.copy(name = n) else it }),
            bookings = p.bookings.map { if (it.court == old) it.copy(court = n) else it },
            blocks = p.blocks.map { b -> b.copy(courts = b.courts.map { if (it == old) n else it }) },
        )
        courtPlan.value = np
        scope.launch(io) { storage.saveCourtPlan(np) }
        for (t in competitions.clubTournaments.value) if (t.matches.any { it.court == old }) competitions.saveClubTournament(t.copy(matches = t.matches.map { if (it.court == old) it.copy(court = n) else it }))
        return true
    }

    /** Le prenotazioni da oggi in poi su quel campo (quelle che sparirebbero eliminandolo). */
    fun futureBookings(court: String): Int {
        val today = com.tennis.scoremanager.data.CourtPlans.dayStart(System.currentTimeMillis())
        return courtPlan.value.bookings.count { it.court == court && it.start >= today }
    }

    /** Elimina un campo: via le sue prenotazioni future (con gli addebiti non pagati) e le sue caratteristiche. */
    fun deleteCourt(court: String) {
        courtPlan.value.settings.info(court).photos.forEach { n -> scope.launch(io) { courtPhotoFile(n).delete() } }
        val today = com.tennis.scoremanager.data.CourtPlans.dayStart(System.currentTimeMillis())
        courtPlan.value.bookings.filter { it.court == court && it.start >= today }.forEach { deleteBooking(it, false) }
        setClubCourts(clubCourts.value - court)
        val p = courtPlan.value
        val np = p.copy(
            settings = p.settings.copy(courts = p.settings.courts.filter { it.name != court }),
            blocks = p.blocks.mapNotNull { b -> if (court !in b.courts) b else b.copy(courts = b.courts - court).takeIf { it.courts.isNotEmpty() } },
        )
        courtPlan.value = np
        scope.launch(io) { storage.saveCourtPlan(np) }
    }

    /** La cartella delle foto dei campi (nei dati dell'app: va nel backup). */
    internal val courtPhotosDir: File get() = File(app.filesDir, "court_photos").apply { mkdirs() }

    fun courtPhotoFile(name: String): File = File(courtPhotosDir, name)

    /** Aggiunge al campo le foto scelte, ridotte (lato lungo 1600 px) e in JPEG. */
    fun addCourtPhotos(court: String, uris: List<android.net.Uri>) {
        if (uris.isEmpty()) return
        scope.launch {
            val names = withContext(io) {
                uris.mapNotNull { uri ->
                    runCatching {
                        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        app.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
                        var sample = 1
                        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1600) sample *= 2
                        val bmp = app.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return@runCatching null
                        val k = 1600f / maxOf(bmp.width, bmp.height)
                        val out = if (k < 1f) bmp.scale((bmp.width * k).toInt(), (bmp.height * k).toInt()) else bmp
                        val name = "c" + java.util.UUID.randomUUID().toString().take(12) + ".jpg"
                        courtPhotoFile(name).outputStream().use { out.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, it) }
                        if (out !== bmp) out.recycle()
                        bmp.recycle()
                        name
                    }.getOrNull()
                }
            }
            val info = courtPlan.value.settings.info(court)
            saveCourtInfo(info.copy(photos = info.photos + names))
        }
    }

    fun removeCourtPhoto(court: String, name: String) {
        val info = courtPlan.value.settings.info(court)
        saveCourtInfo(info.copy(photos = info.photos - name))
        scope.launch(io) { courtPhotoFile(name).delete() }
    }

    /** Sposta un campo di un posto nell'elenco ([up] = verso l'inizio). */
    fun moveCourt(court: String, up: Boolean) {
        val l = clubCourts.value.toMutableList()
        val i = l.indexOf(court); val j = if (up) i - 1 else i + 1
        if (i < 0 || j !in l.indices) return
        l[i] = l[j].also { l[j] = l[i] }
        setClubCourts(l)
    }

    /** Salva (o toglie, con [delete]) un periodo di campi non prenotabili. */
    fun saveCourtBlock(b: com.tennis.scoremanager.data.CourtBlock, delete: Boolean = false) {
        val p = courtPlan.value.copy(blocks = courtPlan.value.blocks.filter { it.id != b.id } + listOfNotNull(b.takeIf { !delete }))
        courtPlan.value = p
        scope.launch(io) { storage.saveCourtPlan(p) }
    }

    /**
     * Toglie la prenotazione (o tutta la serie da quella in poi); gli addebiti non pagati spariscono. Quelle fatte
     * online si tolgono anche dal server ([fromServer]: le ha già tolte lui).
     */
    fun deleteBooking(b: com.tennis.scoremanager.data.CourtBooking, wholeSeries: Boolean, fromServer: Boolean = false) {
        val gone = if (wholeSeries && b.series != null) courtPlan.value.bookings.filter { it.series == b.series && it.start >= b.start } else listOf(b)
        if (!fromServer) gone.mapNotNull { it.online }.takeIf { it.isNotEmpty() }?.let { ids -> saveServer { it.copy(cancel = (it.cancel + ids).distinct()) } }
        var book = clubBook.value
        gone.flatMap { it.charges }.forEach { id -> if (book.charges.firstOrNull { it.id == id }?.payments.isNullOrEmpty()) book = com.tennis.scoremanager.data.Fees.cancel(book, id) }
        saveClubBook(book)
        putBookings(emptyList(), gone.map { it.id }.toSet())
    }

    /** Il campo e com'è fatto: "Campo 1 · terra · coperto". */
    fun courtLine(court: String): String {
        val i = courtPlan.value.settings.info(court)
        val s = strings
        return listOfNotNull(court, i.surface?.let { com.tennis.scoremanager.ui.screens.surfaceName(s, it) }, if (i.covered) s.crtCovered else null).joinToString(" · ")
    }

    /** Registra il pagamento di tutti gli addebiti aperti della prenotazione (al campo o con Satispay). */
    fun payBooking(b: com.tennis.scoremanager.data.CourtBooking, type: com.tennis.scoremanager.data.PayType) {
        var book = clubBook.value
        val now = System.currentTimeMillis()
        for (id in b.charges) {
            val ch = book.charges.firstOrNull { it.id == id } ?: continue
            if (ch.left > 0) com.tennis.scoremanager.data.Fees.pay(book, id, ch.left, now, type.method, "", type.name)?.let { book = it.first }
        }
        saveClubBook(book)
    }

    /** Avvisa i giocatori della prenotazione: campo, giorno e ora, e come si paga. */
    fun noticeBooking(b: com.tennis.scoremanager.data.CourtBooking) {
        val s = strings; val now = System.currentTimeMillis(); val lang = options.value.lang
        val heads = (b.players.size + b.names.size).coerceAtLeast(1)
        val each = com.tennis.scoremanager.data.Fees.money(com.tennis.scoremanager.data.CourtPlans.split(b.cents, heads).firstOrNull() ?: 0, lang.locale)
        val pay = if (b.cents <= 0) emptyList() else if (b.payWhere == com.tennis.scoremanager.data.PayWhere.ONLINE)
            listOf(s.ntPayOnline(each)) + com.tennis.scoremanager.data.Notices.payLines(s, clubBook.value) else listOf(s.ntPayCourt(each))
        val text = (listOf(s.bkNotice("${courtLine(b.court)} · ${bookingLabel(b).substringAfter(" · ")}"), (b.names + b.players.mapNotNull { playerById(it)?.name }).distinct().joinToString(" · "))
            + pay + listOfNotNull(b.note.ifBlank { null }, clubSign.ifBlank { null })).joinToString("\n")
        val rec = b.players.mapNotNull { playerById(it) }.map { com.tennis.scoremanager.data.Notices.recipient(it, text, now) }
        openNotice(com.tennis.scoremanager.data.NoticePlan(s.bkTitle, bookingLabel(b), text, rec))
    }
}
