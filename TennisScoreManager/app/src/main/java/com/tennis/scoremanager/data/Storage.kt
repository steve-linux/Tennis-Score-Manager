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
    companion object {
        /** Foto ricordo per partita: bastano per il PDF e lo tengono sui 3-4 MB. */
        const val MAX_MATCH_PHOTOS = 10
    }


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

    /** Foto ricordo di una partita: una cartella per partita, JPEG rimpiccioliti, al massimo [MAX_MATCH_PHOTOS]. */
    private fun matchPhotoDir(id: String): File = File(File(app.filesDir, "matchphotos"), id)

    fun matchPhotos(id: String): List<File> =
        matchPhotoDir(id).listFiles { f -> f.extension == "jpg" }.orEmpty().sortedBy { it.name }

    @Synchronized
    fun addMatchPhoto(id: String, jpeg: ByteArray): Boolean {
        val dir = matchPhotoDir(id).apply { mkdirs() }
        if (matchPhotos(id).size >= MAX_MATCH_PHOTOS) return false
        return runCatching { File(dir, "${System.currentTimeMillis()}.jpg").writeBytes(jpeg) }.isSuccess
    }

    @Synchronized
    fun deleteMatchPhotos(id: String) {
        matchPhotoDir(id).deleteRecursively()
    }

    /** Tornei Americano e Mexicano, uno per file. */
    private val tournamentsDir: File get() = File(app.filesDir, "tournaments").apply { mkdirs() }

    @Synchronized
    fun saveTournament(t: Tournament) {
        runCatching {
            val f = File(tournamentsDir, "${t.id}.json")
            val tmp = File(tournamentsDir, "${t.id}.tmp")
            tmp.writeText(json.encodeToString(Tournament.serializer(), t))
            if (!tmp.renameTo(f)) {
                f.delete()
                tmp.renameTo(f)
            }
        }.onFailure { Log.e("Storage", "Salvataggio torneo fallito", it) }
    }

    /** Tornei, dal più recente. */
    fun loadTournaments(): List<Tournament> =
        tournamentsDir.listFiles { f -> f.extension == "json" }.orEmpty()
            .mapNotNull { f -> runCatching { json.decodeFromString(Tournament.serializer(), f.readText()) }.getOrNull() }
            .sortedByDescending { it.createdAt }

    @Synchronized
    fun deleteTournament(id: String) {
        File(tournamentsDir, "$id.json").delete()
    }

    /** Allenamenti con la fascia: a parte dalle partite, uno per file. */
    private val workoutsDir: File get() = File(app.filesDir, "workouts").apply { mkdirs() }

    @Synchronized
    fun saveWorkout(w: WorkoutRecord) {
        runCatching {
            val f = File(workoutsDir, "${w.id}.json")
            val tmp = File(workoutsDir, "${w.id}.tmp")
            tmp.writeText(json.encodeToString(WorkoutRecord.serializer(), w))
            if (!tmp.renameTo(f)) {
                f.delete()
                tmp.renameTo(f)
            }
        }.onFailure { Log.e("Storage", "Salvataggio allenamento fallito", it) }
    }

    /** Allenamenti, dal più recente. */
    fun loadWorkouts(): List<WorkoutRecord> =
        workoutsDir.listFiles { f -> f.extension == "json" }.orEmpty()
            .mapNotNull { f -> runCatching { json.decodeFromString(WorkoutRecord.serializer(), f.readText()) }.getOrNull() }
            .sortedByDescending { it.startedAt }

    @Synchronized
    fun deleteWorkout(id: String) {
        File(workoutsDir, "$id.json").delete()
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

    /** Telefono-telecamera: server RTMP della diretta (di serie YouTube) e chiave di trasmissione. */
    var streamUrl: String
        get() = prefs.getString("stream_url", null) ?: "rtmp://a.rtmp.youtube.com/live2"
        set(v) = prefs.edit().putString("stream_url", v.trim()).apply()
    var streamKey: String
        get() = prefs.getString("stream_key", null) ?: ""
        set(v) = prefs.edit().putString("stream_key", v.trim()).apply()
    /** Highlights: ordine delle voci (dalla più importante) e quelle accese, come l'ultima volta. */
    var highlightOrder: List<String>
        get() = prefs.getString("hl_order", null)?.split(',')?.filter { it.isNotBlank() } ?: emptyList()
        set(v) = prefs.edit().putString("hl_order", v.joinToString(",")).apply()
    var highlightEnabled: Set<String>?
        get() = prefs.getString("hl_enabled", null)?.split(',')?.filter { it.isNotBlank() }?.toSet()
        set(v) = prefs.edit().putString("hl_enabled", v?.joinToString(",")).apply()
    /** Brano scelto per le statistiche degli highlights: una copia sul telefono, col nome da mostrare. */
    val highlightMusicFile: File get() = File(app.filesDir, "hl_music")
    var highlightMusicName: String?
        get() = prefs.getString("hl_music_name", null)?.takeIf { highlightMusicFile.exists() }
        set(v) {
            if (v == null) highlightMusicFile.delete()
            prefs.edit().putString("hl_music_name", v).apply()
        }

    /** Highlights: schermate delle statistiche in coda al montaggio. */
    var highlightStats: Boolean
        get() = prefs.getBoolean("hl_stats", true)
        set(v) = prefs.edit().putBoolean("hl_stats", v).apply()

    /** Moviola mandata subito anche al tabellone. */
    var replayOnBoard: Boolean
        get() = prefs.getBoolean("replay_on_board", false)
        set(v) = prefs.edit().putBoolean("replay_on_board", v).apply()
    var cameraOverlay: Boolean
        get() = prefs.getBoolean("camera_overlay", true)
        set(v) = prefs.edit().putBoolean("camera_overlay", v).apply()

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
    fun strapAddress(p1: Boolean): String? = prefs.getString(if (p1) "strap_p1" else "strap_p2", null)
    fun strapName(p1: Boolean): String? = prefs.getString(if (p1) "strap_p1_name" else "strap_p2_name", null)
    fun setStrap(p1: Boolean, address: String?, name: String?) {
        prefs.edit()
            .putString(if (p1) "strap_p1" else "strap_p2", address)
            .putString(if (p1) "strap_p1_name" else "strap_p2_name", name)
            .apply()
    }

    /** Chi indossa la fascia del lato (per le calorie); null = non detto. */
    fun heartProfile(p1: Boolean): HeartProfile? =
        prefs.getString(if (p1) "heart_p1" else "heart_p2", null)?.let { runCatching { json.decodeFromString(HeartProfile.serializer(), it) }.getOrNull() }

    fun setHeartProfile(p1: Boolean, p: HeartProfile?) {
        prefs.edit().putString(if (p1) "heart_p1" else "heart_p2", p?.let { json.encodeToString(HeartProfile.serializer(), it) }).apply()
    }

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
