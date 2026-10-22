// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import android.content.Context
import android.util.Log
import com.tennis.scoremanager.tv.TvSettings
import kotlinx.serialization.json.Json
import java.io.File

/** Preferenze e partite salvate in locale (memoria interna dell'app). */
class Storage(context: Context) {

    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("tsm", Context.MODE_PRIVATE)

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    private val matchesDir: File get() = File(app.filesDir, "matches").apply { mkdirs() }
    private val lastFinishedFile: File get() = File(app.filesDir, "last_finished.json")
    /** Foto dei giocatori, una per nome (chiave di PlayerStats.key): JPEG quadrati e piccoli. */
    private val photosDir: File get() = File(app.filesDir, "players").apply { mkdirs() }

    fun photoFile(key: String): File {
        val hash = java.security.MessageDigest.getInstance("SHA-1").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(photosDir, "$hash.jpg")
    }

    /** Tutte le foto per chiave del nome (servono al trasferimento). Le chiavi stanno in un indice accanto. */
    fun photoKeys(): List<String> = runCatching { File(photosDir, "index.txt").readLines().filter { it.isNotBlank() } }.getOrDefault(emptyList())
        .filter { photoFile(it).exists() }

    @Synchronized
    fun savePhoto(key: String, jpeg: ByteArray) {
        runCatching {
            photoFile(key).writeBytes(jpeg)
            val idx = File(photosDir, "index.txt")
            val keys = (runCatching { idx.readLines() }.getOrDefault(emptyList()) + key).filter { it.isNotBlank() }.distinct()
            idx.writeText(keys.joinToString("\n"))
        }.onFailure { Log.e("Storage", "Salvataggio foto fallito", it) }
    }

    @Synchronized
    fun deletePhoto(key: String) {
        photoFile(key).delete()
    }

    /** Storico: le partite finite, una per file, restano sul telefono finché non le si elimina. */
    private val finishedDir: File get() = File(app.filesDir, "finished").apply { mkdirs() }

    var setup: SetupData
        get() = read(prefs.getString("setup", null)) ?: SetupData()
        set(v) = prefs.edit().putString("setup", json.encodeToString(SetupData.serializer(), v)).apply()

    var options: MatchOptions
        get() = prefs.getString("options", null)?.let {
            runCatching { json.decodeFromString(MatchOptions.serializer(), it) }.getOrNull()
        } ?: MatchOptions()
        set(v) = prefs.edit().putString("options", json.encodeToString(MatchOptions.serializer(), v)).apply()

    /** Tabellone TV: impostazioni del telefono, non della partita. */
    var tv: TvSettings
        get() = prefs.getString("tv", null)?.let {
            runCatching { json.decodeFromString(TvSettings.serializer(), it) }.getOrNull()
        } ?: TvSettings()
        set(v) = prefs.edit().putString("tv", json.encodeToString(TvSettings.serializer(), v)).apply()

    /** Ultimo indirizzo a cui si è collegato questo telefono usato come tabellone ("192.168.43.1:8080"). */
    var lastScoreboardHost: String?
        get() = prefs.getString("display_host", null)
        set(v) = prefs.edit().putString("display_host", v).apply()

    private fun read(s: String?): SetupData? =
        s?.let { runCatching { json.decodeFromString(SetupData.serializer(), it) }.getOrNull() }

    fun bandAddress(p1: Boolean): String? = prefs.getString(if (p1) "band_p1" else "band_p2", null)
    fun bandName(p1: Boolean): String? = prefs.getString(if (p1) "band_p1_name" else "band_p2_name", null)
    fun setBand(p1: Boolean, address: String?, name: String?) {
        prefs.edit()
            .putString(if (p1) "band_p1" else "band_p2", address)
            .putString(if (p1) "band_p1_name" else "band_p2_name", name)
            .apply()
    }

    /** Telecomandi Bluetooth: descrittore del dispositivo (identità stabile di Android) e nome mostrato. */
    fun remoteDescriptor(p1: Boolean): String? = prefs.getString(if (p1) "remote_p1" else "remote_p2", null)
    fun remoteName(p1: Boolean): String? = prefs.getString(if (p1) "remote_p1_name" else "remote_p2_name", null)
    /** Tasto premuto all'associazione: dà il punto; gli altri tasti dello stesso telecomando annullano (null = tutti punto). */
    fun remoteKey(p1: Boolean): Int? = prefs.getInt(if (p1) "remote_p1_key" else "remote_p2_key", -1).takeIf { it >= 0 }
    fun setRemote(p1: Boolean, descriptor: String?, name: String?, key: Int?) {
        prefs.edit()
            .putString(if (p1) "remote_p1" else "remote_p2", descriptor)
            .putString(if (p1) "remote_p1_name" else "remote_p2_name", name)
            .putInt(if (p1) "remote_p1_key" else "remote_p2_key", key ?: -1)
            .apply()
    }

    /** Consumo di base misurato sul campo per ogni braccialetto (mA), per stimare l'autonomia. */
    fun bandBaseMa(address: String): Double? =
        prefs.getFloat("base_ma_$address", -1f).takeIf { it > 0f }?.toDouble()

    fun setBandBaseMa(address: String, ma: Double) {
        prefs.edit().putFloat("base_ma_$address", ma.toFloat()).apply()
    }

    var historyTree: String?
        get() = prefs.getString("history_tree", null)
        set(v) = prefs.edit().putString("history_tree", v).apply()

    /** Id della partita il cui riepilogo è a schermo (null dopo "Nuova partita" o "Esci"). */
    var summaryOpen: String?
        get() = prefs.getString("summary_open", null)
        set(v) = prefs.edit().putString("summary_open", v).apply()

    /** Scrittura atomica: prima su file temporaneo, poi rinomina (sicuro anche se il telefono si spegne). */
    @Synchronized
    fun saveMatch(rec: MatchRecord) {
        runCatching {
            val f = File(matchesDir, "${rec.id}.json")
            val tmp = File(matchesDir, "${rec.id}.tmp")
            tmp.writeText(json.encodeToString(MatchRecord.serializer(), rec))
            if (!tmp.renameTo(f)) {
                f.delete()
                tmp.renameTo(f)
            }
        }.onFailure { Log.e("Storage", "Salvataggio partita fallito", it) }
    }

    fun loadUnfinished(): List<MatchRecord> =
        matchesDir.listFiles { f -> f.extension == "json" }.orEmpty()
            .mapNotNull { f -> runCatching { json.decodeFromString(MatchRecord.serializer(), f.readText()) }.getOrNull() }
            .filter { !it.finished }
            .sortedByDescending { it.updatedAt }

    @Synchronized
    fun deleteMatch(id: String) {
        File(matchesDir, "$id.json").delete()
    }

    @Synchronized
    fun saveFinished(rec: MatchRecord) {
        runCatching {
            val f = File(finishedDir, "${rec.id}.json")
            val tmp = File(finishedDir, "${rec.id}.tmp")
            tmp.writeText(json.encodeToString(MatchRecord.serializer(), rec))
            if (!tmp.renameTo(f)) {
                f.delete()
                tmp.renameTo(f)
            }
        }.onFailure { Log.e("Storage", "Salvataggio nello storico fallito", it) }
    }

    /** Partite dello storico, dalla più recente. */
    fun loadFinished(): List<MatchRecord> =
        finishedDir.listFiles { f -> f.extension == "json" }.orEmpty()
            .mapNotNull { f -> runCatching { json.decodeFromString(MatchRecord.serializer(), f.readText()) }.getOrNull() }
            .sortedByDescending { it.endedAt ?: it.updatedAt }

    fun hasFinished(id: String): Boolean = File(finishedDir, "$id.json").exists()

    @Synchronized
    fun deleteFinished(id: String) {
        File(finishedDir, "$id.json").delete()
    }

    fun clearLastFinished() {
        lastFinishedFile.delete()
    }

    fun saveLastFinished(rec: MatchRecord) {
        runCatching { lastFinishedFile.writeText(json.encodeToString(MatchRecord.serializer(), rec)) }
    }

    fun loadLastFinished(): MatchRecord? =
        runCatching { json.decodeFromString(MatchRecord.serializer(), lastFinishedFile.readText()) }.getOrNull()

    /** Cartella predefinita dello storico se l'utente non ne sceglie una. */
    val defaultHistoryDir: File
        get() = File(app.getExternalFilesDir(null) ?: app.filesDir, "Storico").apply { mkdirs() }
}
