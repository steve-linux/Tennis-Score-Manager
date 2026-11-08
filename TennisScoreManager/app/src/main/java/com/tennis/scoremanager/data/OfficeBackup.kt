// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Il backup della segreteria, e solo quello: giocatori e soci, quote e pagamenti, planning, tornei, scuola tennis,
 * squadre, classifiche a sfida, campi, funzioni accese e regole delle prenotazioni online. Non entrano partite,
 * colpi, video e il resto dell'app (per quello c'è il backup completo), né la chiave del server (si riabbina).
 * Un solo file compresso (.tsmclub): si scarica dal PC, si condivide dal telefono, si tiene sul server del circolo.
 */
@Serializable
data class OfficeSnapshot(
    val version: Int = OfficeBackup.VERSION,
    val createdAt: Long = 0,
    val app: String = "",
    val club: String = "",
    val courts: List<String> = emptyList(),
    val features: List<String>? = null,
    val players: List<PlayerProfile> = emptyList(),
    val book: ClubBook = ClubBook(),
    val plan: CourtPlan = CourtPlan(),
    val tournaments: List<ClubTournament> = emptyList(),
    val courses: List<Course> = emptyList(),
    val teams: List<Team> = emptyList(),
    val ladders: List<Ladder> = emptyList(),
    val serverRules: BookingRules? = null,
    val payInfo: String = "",
)

/** Quanto c'è in un backup, per vederlo prima di ripristinarlo. */
@Serializable
data class OfficeSummary(
    val createdAt: Long, val club: String, val app: String, val members: Int, val players: Int, val charges: Int, val payments: Int,
    val bookings: Int, val tournaments: Int, val courses: Int, val teams: Int, val ladders: Int, val courts: Int,
)

/** Un backup salvato (sul telefono o sul server): nome del file, quando, quanto pesa. */
@Serializable
data class OfficeBackupFile(val name: String, val at: Long, val size: Long)

object OfficeBackup {
    const val VERSION = 1
    /** Le copie automatiche che restano sul telefono (e sul server): due settimane. */
    const val KEEP = 14
    const val EXT = ".tsmclub"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    fun encode(s: OfficeSnapshot): ByteArray = ByteArrayOutputStream().also { b ->
        GZIPOutputStream(b).use { it.write(json.encodeToString(OfficeSnapshot.serializer(), s).toByteArray()) }
    }.toByteArray()

    /** Legge un backup (compresso, o il JSON com'è); null se non è un backup della segreteria. */
    fun decode(bytes: ByteArray): OfficeSnapshot? = runCatching {
        val text = if (bytes.size > 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()) GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() } else bytes
        json.decodeFromString(OfficeSnapshot.serializer(), text.toString(Charsets.UTF_8))
    }.getOrNull()?.takeIf { it.version in 1..VERSION && it.createdAt > 0 }

    fun summary(s: OfficeSnapshot) = OfficeSummary(
        s.createdAt, s.club, s.app, s.players.count { it.member != null }, s.players.size, s.book.charges.size, s.book.charges.sumOf { it.payments.size },
        s.plan.bookings.size, s.tournaments.size, s.courses.size, s.teams.size, s.ladders.size, s.courts.size,
    )

    /** "TSM segreteria CT Sovizzo 2026-11-04 2130.tsmclub": si capisce cos'è anche in una cartella di Drive. */
    fun fileName(club: String, at: Long): String {
        val t = java.text.SimpleDateFormat("yyyy-MM-dd HHmm", java.util.Locale.ROOT).format(java.util.Date(at))
        val c = club.replace(Regex("[\\\\/:*?\"<>|]"), " ").trim().take(40)
        return listOf("TSM segreteria", c, t).filter { it.isNotEmpty() }.joinToString(" ") + EXT
    }

    /** Le copie in [dir], dalla più nuova. */
    fun list(dir: File): List<OfficeBackupFile> = dir.listFiles { f -> f.isFile && f.name.endsWith(EXT) }.orEmpty()
        .map { OfficeBackupFile(it.name, it.lastModified(), it.length()) }.sortedByDescending { it.at }

    /** Salva una copia in [dir] e tiene solo le ultime [keep]. */
    fun store(dir: File, name: String, bytes: ByteArray, keep: Int = KEEP): File {
        dir.mkdirs()
        val f = File(dir, name)
        val tmp = File(dir, "$name.tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(f)) { f.writeBytes(bytes); tmp.delete() }
        list(dir).drop(keep).forEach { File(dir, it.name).delete() }
        return f
    }

    /**
     * L'impronta del contenuto, senza l'ora del backup: se è uguale a quella dell'ultima copia non è cambiato niente
     * e la copia automatica si salta. Così ogni copia resta completa (si ripristina da sola) e se ne fa una nuova
     * solo quando serve: le 14 tenute coprono più giorni.
     */
    fun fingerprint(s: OfficeSnapshot): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        md.update(json.encodeToString(OfficeSnapshot.serializer(), s.copy(createdAt = 0, app = "")).toByteArray())
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /** Solo un nome di file di [dir] (niente cartelle o percorsi strani da fuori). */
    fun safeName(name: String): String? = name.takeIf { it.endsWith(EXT) && !it.contains('/') && !it.contains('\\') && !it.startsWith(".") && it.length < 200 }
}
