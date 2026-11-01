// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import android.content.Context
import android.util.Log
import com.tennis.scoremanager.tv.TvSettings
import kotlinx.serialization.builtins.ListSerializer
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
    /** Sesso di un giocatore, dal nome come la foto: "M", "F" o "" (non detto). Serve all'avatar senza foto. */
    fun playerSex(key: String): String = prefs.getString("sex_$key", "") ?: ""

    fun setPlayerSex(key: String, sex: String) {
        prefs.edit().apply { if (sex.isEmpty()) remove("sex_$key") else putString("sex_$key", sex) }.apply()
    }

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

    /** Allenamenti dei colpi: una sessione per file; le clip stanno in Documenti (vedi [serveClipsDir]). */
    private val servesDir: File get() = File(app.filesDir, "serves").apply { mkdirs() }

    /**
     * Cartella principale che si vede coi file manager: Documenti/Tennis Score Manager da Android 11 (lì l'app scrive e
     * legge i suoi file senza permessi); su Android 8-10 la stessa struttura nella cartella dell'app, come per i file
     * salvati.
     */
    val documentsRoot: File
        get() = if (android.os.Build.VERSION.SDK_INT >= 30) {
            File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS), "Tennis Score Manager")
        } else {
            File(app.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS) ?: app.filesDir, "Tennis Score Manager")
        }

    /** Allenamento colpi/<colpo>, coi nomi nella lingua dell'app. */
    fun strokeFolder(stroke: TrainedStroke, base: String, name: (Int) -> String): String = "$base/${name(stroke.band)}"

    /** Crea le quattro cartelle dei colpi (Servizio, Dritto, Rovescio, Volée); false se Documenti non si può scrivere. */
    fun ensureStrokeFolders(base: String, name: (Int) -> String): Boolean =
        TrainedStroke.entries.map { File(documentsRoot, strokeFolder(it, base, name)) }.all { it.isDirectory || it.mkdirs() }

    /**
     * Cartella nuova per una sessione: Allenamento colpi/<colpo>/[<allievo>/]<data e ora>; null se Documenti non si può
     * scrivere.
     */
    fun newServeFolder(stroke: TrainedStroke, startedAt: Long, base: String, name: (Int) -> String, student: String? = null): String? {
        if (!ensureStrokeFolders(base, name)) return null
        val stamp = java.text.SimpleDateFormat("yyyy-MM-dd HH.mm.ss", java.util.Locale.ROOT).format(java.util.Date(startedAt))
        val who = student?.let { "/" + Students.folderName(it) }.orEmpty()
        val rel = "${strokeFolder(stroke, base, name)}$who/$stamp"
        return rel.takeIf { File(documentsRoot, it).let { d -> d.isDirectory || d.mkdirs() } }
    }

    /** Dove stavano le clip prima della 2.5: la cartella dei video dell'app. */
    private fun legacyClipsDir(id: String): File =
        File(app.getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES) ?: app.filesDir, "serves/$id")

    /** Cartella delle clip della sessione (una sottocartella per colpo). */
    fun serveClipsDir(sess: ServeSession): File =
        (sess.folder?.let { File(documentsRoot, it) } ?: legacyClipsDir(sess.id)).apply { mkdirs() }

    /**
     * Le sessioni di prima con le clip nella cartella dell'app: si spostano nelle cartelle dei colpi in Documenti,
     * unendo i pezzi di ogni clip in un video solo. La cartella vecchia si cancella solo se tutto è stato copiato.
     */
    fun migrateServeSessions(base: String, name: (Int) -> String) {
        for (sess in loadServeSessions().filter { it.folder == null }) {
            val old = legacyClipsDir(sess.id)
            val rel = newServeFolder(sess.stroke, sess.startedAt, base, name) ?: return
            val dest = File(documentsRoot, rel)
            val ok = runCatching {
                for (clip in old.listFiles { f -> f.isDirectory }.orEmpty()) {
                    val out = File(dest, clip.name).apply { mkdirs() }
                    if (!com.tennis.scoremanager.video.ClipJoin.joinDir(clip, out)) error("clip ${clip.name}")
                }
                true
            }.getOrDefault(false)
            if (!ok) {
                dest.deleteRecursively()
                continue
            }
            saveServeSession(sess.copy(folder = rel))
            old.deleteRecursively()
        }
    }

    /** Spazio occupato dai video di una sessione, in byte. */
    fun serveVideoBytes(sess: ServeSession): Long =
        serveClipsDir(sess).walkTopDown().filter { it.isFile && it.extension == "mp4" }.sumOf { it.length() }

    /** Cancella solo i video di una sessione: restano i conteggi, le misure dell'analisi e le immagini dei momenti. */
    fun deleteServeVideos(sess: ServeSession) {
        serveClipsDir(sess).walkTopDown().filter { it.isFile && it.extension == "mp4" }.toList().forEach { it.delete() }
    }

    @Synchronized
    fun saveServeSession(sess: ServeSession) {
        runCatching {
            val f = File(servesDir, "${sess.id}.json")
            val tmp = File(servesDir, "${sess.id}.tmp")
            tmp.writeText(json.encodeToString(ServeSession.serializer(), sess))
            if (!tmp.renameTo(f)) {
                f.delete()
                tmp.renameTo(f)
            }
        }.onFailure { Log.e("Storage", "Salvataggio allenamento al servizio fallito", it) }
    }

    fun loadServeSessions(): List<ServeSession> =
        servesDir.listFiles { f -> f.extension == "json" }.orEmpty()
            .mapNotNull { f -> runCatching { json.decodeFromString(ServeSession.serializer(), f.readText()) }.getOrNull() }
            .sortedByDescending { it.startedAt }

    /** Cancella la sessione e le sue clip. */
    @Synchronized
    fun deleteServeSession(sess: ServeSession) {
        File(servesDir, "${sess.id}.json").delete()
        val dir = serveClipsDir(sess)
        dir.deleteRecursively()
        // La cartella dell'allievo rimasta vuota se ne va con l'ultima sessione.
        if (sess.student != null) dir.parentFile?.takeIf { it.list().isNullOrEmpty() }?.delete()
    }

    /** Esercizi guidati, in un file solo. */
    private val drillsFile: File get() = File(app.filesDir, "drills.json")

    fun loadDrills(): List<Drill>? =
        runCatching { json.decodeFromString(ListSerializer(Drill.serializer()), drillsFile.readText()) }.getOrNull()

    @Synchronized
    fun saveDrills(list: List<Drill>) {
        runCatching {
            val tmp = File(app.filesDir, "drills.tmp")
            tmp.writeText(json.encodeToString(ListSerializer(Drill.serializer()), list))
            if (!tmp.renameTo(drillsFile)) {
                drillsFile.delete()
                tmp.renameTo(drillsFile)
            }
        }.onFailure { Log.e("Storage", "Salvataggio esercizi fallito", it) }
    }

    /** Le squadre del circolo, coi loro incontri, in un file solo. */
    private val teamsFile: File get() = File(app.filesDir, "club_teams.json")

    fun loadTeams(): List<Team> =
        runCatching { json.decodeFromString(ListSerializer(Team.serializer()), teamsFile.readText()) }.getOrDefault(emptyList())

    @Synchronized
    fun saveTeams(list: List<Team>) {
        runCatching {
            val tmp = File(app.filesDir, "club_teams.tmp")
            tmp.writeText(json.encodeToString(ListSerializer(Team.serializer()), list))
            if (!tmp.renameTo(teamsFile)) {
                teamsFile.delete()
                tmp.renameTo(teamsFile)
            }
        }.onFailure { Log.e("Storage", "Salvataggio delle squadre fallito", it) }
    }

    /** I corsi della scuola tennis, in un file solo. */
    private val schoolFile: File get() = File(app.filesDir, "club_school.json")

    fun loadCourses(): List<Course> =
        runCatching { json.decodeFromString(ListSerializer(Course.serializer()), schoolFile.readText()) }.getOrDefault(emptyList())

    @Synchronized
    fun saveCourses(list: List<Course>) {
        runCatching {
            val tmp = File(app.filesDir, "club_school.tmp")
            tmp.writeText(json.encodeToString(ListSerializer(Course.serializer()), list))
            if (!tmp.renameTo(schoolFile)) {
                schoolFile.delete()
                tmp.renameTo(schoolFile)
            }
        }.onFailure { Log.e("Storage", "Salvataggio dei corsi fallito", it) }
    }

    /** Il planning dei campi: orari, prezzi e prenotazioni. */
    private val courtPlanFile: File get() = File(app.filesDir, "club_courts.json")

    fun loadCourtPlan(): CourtPlan =
        runCatching { json.decodeFromString(CourtPlan.serializer(), courtPlanFile.readText()) }.getOrDefault(CourtPlan())

    @Synchronized
    fun saveCourtPlan(plan: CourtPlan) {
        runCatching {
            val tmp = File(app.filesDir, "club_courts.tmp")
            tmp.writeText(json.encodeToString(CourtPlan.serializer(), plan))
            if (!tmp.renameTo(courtPlanFile)) {
                courtPlanFile.delete()
                tmp.renameTo(courtPlanFile)
            }
        }.onFailure { Log.e("Storage", "Salvataggio del planning dei campi fallito", it) }
    }

    /** I tornei del circolo (sociali e FITP), in un file solo. */
    private val clubTournamentsFile: File get() = File(app.filesDir, "club_tournaments.json")

    fun loadClubTournaments(): List<ClubTournament> =
        runCatching { json.decodeFromString(ListSerializer(ClubTournament.serializer()), clubTournamentsFile.readText()) }.getOrDefault(emptyList())

    @Synchronized
    fun saveClubTournaments(list: List<ClubTournament>) {
        runCatching {
            val tmp = File(app.filesDir, "club_tournaments.tmp")
            tmp.writeText(json.encodeToString(ListSerializer(ClubTournament.serializer()), list))
            if (!tmp.renameTo(clubTournamentsFile)) {
                clubTournamentsFile.delete()
                tmp.renameTo(clubTournamentsFile)
            }
        }.onFailure { Log.e("Storage", "Salvataggio dei tornei del circolo fallito", it) }
    }

    /** Il registro del circolo: quote, addebiti, pagamenti e numerazione delle ricevute. */
    private val clubBookFile: File get() = File(app.filesDir, "club_book.json")

    fun loadClubBook(): ClubBook =
        runCatching { json.decodeFromString(ClubBook.serializer(), clubBookFile.readText()) }.getOrDefault(ClubBook())

    @Synchronized
    fun saveClubBook(book: ClubBook) {
        runCatching {
            val tmp = File(app.filesDir, "club_book.tmp")
            tmp.writeText(json.encodeToString(ClubBook.serializer(), book))
            if (!tmp.renameTo(clubBookFile)) {
                clubBookFile.delete()
                tmp.renameTo(clubBookFile)
            }
        }.onFailure { Log.e("Storage", "Salvataggio del registro del circolo fallito", it) }
    }

    /** Il database dei giocatori, in un file solo (gli allievi sono giocatori con student). */
    private val playersFile: File get() = File(app.filesDir, "players.json")
    /** Gli allievi della prima versione, prima del database: si leggono una volta e diventano giocatori. */
    private val studentsFile: File get() = File(app.filesDir, "students.json")

    @Synchronized
    fun loadPlayers(): List<PlayerProfile> {
        if (!playersFile.exists() && studentsFile.exists()) {
            val old = runCatching { json.decodeFromString(ListSerializer(PlayerProfile.serializer()), studentsFile.readText()) }.getOrDefault(emptyList())
            savePlayers(old.map { it.copy(student = true) })
            studentsFile.delete()
        }
        // Nati dallo storico con un nome solo: quel nome è il soprannome (le partite non cambiano), nome e cognome da completare.
        return runCatching { json.decodeFromString(ListSerializer(PlayerProfile.serializer()), playersFile.readText()) }.getOrDefault(emptyList())
            .map { if (it.firstName.isBlank() && it.lastName.isBlank() && it.nickname.isBlank()) it.copy(nickname = it.name) else it }
    }

    @Synchronized
    fun savePlayers(list: List<PlayerProfile>) {
        runCatching {
            val tmp = File(app.filesDir, "players.tmp")
            tmp.writeText(json.encodeToString(ListSerializer(PlayerProfile.serializer()), list))
            if (!tmp.renameTo(playersFile)) {
                playersFile.delete()
                tmp.renameTo(playersFile)
            }
        }.onFailure { Log.e("Storage", "Salvataggio giocatori fallito", it) }
    }

    /** Domande in sospeso sui giocatori arrivati da un altro telefono con un nome che abbiamo già. */
    private val questionsFile: File get() = File(app.filesDir, "player_questions.json")

    fun loadPlayerQuestions(): List<Players.Question> =
        runCatching { json.decodeFromString(ListSerializer(Players.Question.serializer()), questionsFile.readText()) }.getOrDefault(emptyList())

    @Synchronized
    fun savePlayerQuestions(list: List<Players.Question>) {
        runCatching { if (list.isEmpty()) questionsFile.delete() else questionsFile.writeText(json.encodeToString(ListSerializer(Players.Question.serializer()), list)) }
    }

    /**
     * Riscrive le partite salvate (storico e sospese): i posti con un identificativo in [from] passano a [to]; con
     * [onlyMatches] solo quelle partite (per separare un omonimo). Restituisce quante partite sono cambiate.
     */
    @Synchronized
    fun remapPlayer(from: Set<String>, to: String, onlyMatches: Set<String>? = null): Int {
        var changed = 0
        fun fix(rec: MatchRecord, save: (MatchRecord) -> Unit) {
            if (onlyMatches != null && rec.id !in onlyMatches) return
            var su = rec.setup
            for (slot in Players.slots(su)) if (slot.id in from) su = Players.withId(su, slot, to)
            if (su != rec.setup) {
                save(rec.copy(setup = su))
                changed++
            }
        }
        loadFinished().forEach { fix(it, ::saveFinished) }
        matchesDir.listFiles { f -> f.extension == "json" }.orEmpty()
            .mapNotNull { f -> runCatching { json.decodeFromString(MatchRecord.serializer(), f.readText()) }.getOrNull() }
            .forEach { fix(it, ::saveMatch) }
        return changed
    }

    /**
     * Dà un giocatore del database a ogni nome scritto nelle partite salvate (storico e sospese) che non lo ha ancora:
     * al primo avvio tutte, poi solo quelle arrivate da versioni vecchie. Dalla più vecchia, così lo stesso nome è
     * sempre la stessa persona; con due giocatori dello stesso nome (omonimi) quel posto resta col solo nome.
     * Restituisce quante partite sono cambiate.
     */
    @Synchronized
    fun assignPlayerIds(now: Long = System.currentTimeMillis()): Int {
        var players = loadPlayers()
        var changed = 0
        fun fix(rec: MatchRecord, save: (MatchRecord) -> Unit) {
            // Un identificativo che è l'alias di un nostro giocatore (unito, o legato a un altro telefono) diventa il suo.
            var canon = rec.setup
            for (slot in Players.slots(canon)) {
                val p = Players.byId(players, slot.id) ?: continue
                if (p.id != slot.id) canon = Players.withId(canon, slot, p.id)
            }
            val (su, created) = Players.assign(canon, players, now)
            if (su == rec.setup) return
            players = players + created
            save(rec.copy(setup = su))
            changed++
        }
        val finished = loadFinished().sortedBy { it.startedAt ?: it.updatedAt }
        val before = players.size
        for (rec in finished) fix(rec, ::saveFinished)
        val open = matchesDir.listFiles { f -> f.extension == "json" }.orEmpty()
            .mapNotNull { f -> runCatching { json.decodeFromString(MatchRecord.serializer(), f.readText()) }.getOrNull() }
        for (rec in open.sortedBy { it.startedAt ?: it.updatedAt }) fix(rec, ::saveMatch)
        if (players.size != before) savePlayers(players)
        return changed
    }

    /** Chi si allena con l'allenamento dei colpi (PlayerProfile.id; vuoto = nessun allievo). */
    var trainStudent: String
        get() = prefs.getString("train_student", null) ?: ""
        set(v) = prefs.edit().putString("train_student", v).apply()

    /** Racchetta virtuale: tutte le racchette (con le incordature) in un file solo, sono poche. */
    private val racketsFile: File get() = File(app.filesDir, "rackets.json")

    fun loadRackets(): List<Racket> =
        runCatching { json.decodeFromString(ListSerializer(Racket.serializer()), racketsFile.readText()) }.getOrDefault(emptyList())

    @Synchronized
    fun saveRackets(list: List<Racket>) {
        runCatching {
            val tmp = File(app.filesDir, "rackets.tmp")
            tmp.writeText(json.encodeToString(ListSerializer(Racket.serializer()), list))
            if (!tmp.renameTo(racketsFile)) {
                racketsFile.delete()
                tmp.renameTo(racketsFile)
            }
        }.onFailure { Log.e("Storage", "Salvataggio racchette fallito", it) }
    }

    /** Racchetta scelta per gli allenamenti ("" = nessuna). */
    var workoutRacket: String
        get() = prefs.getString("workout_racket", null) ?: ""
        set(v) = prefs.edit().putString("workout_racket", v).apply()

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
    /** Prima accensione fatta (lingua, uso, nome) e il nome di chi usa l'app (per Progressi e rating; "" = non detto). */
    var onboarded: Boolean
        get() = prefs.getBoolean("onboarded", false)
        set(v) = prefs.edit().putBoolean("onboarded", v).apply()
    var myName: String
        get() = prefs.getString("my_name", null) ?: ""
        set(v) = prefs.edit().putString("my_name", v.trim()).apply()

    /** Ruoli scelti (Role.name) e quello attivo; di serie Tu, come prima dei ruoli. */
    var roles: Set<String>
        get() = prefs.getStringSet("roles", null)?.toSet()?.takeIf { it.isNotEmpty() } ?: setOf("PLAYER")
        set(v) = prefs.edit().putStringSet("roles", v).apply()
    var activeRole: String
        get() = prefs.getString("active_role", null) ?: "PLAYER"
        set(v) = prefs.edit().putString("active_role", v).apply()

    /** Circolo: nome e campi (uno per riga), per i tornei, i tabelloni e Nuova partita. */
    var clubName: String
        get() = prefs.getString("club_name", null) ?: ""
        set(v) = prefs.edit().putString("club_name", v.trim()).apply()
    var clubCourts: List<String>
        get() = prefs.getString("club_courts", null)?.split('\n')?.filter { it.isNotBlank() } ?: emptyList()
        set(v) = prefs.edit().putString("club_courts", v.map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")).apply()

    /** Promemoria del backup (ogni 30 giorni) e quando si è fatto l'ultimo (ms, 0 = mai). */
    var backupReminder: Boolean
        get() = prefs.getBoolean("backup_reminder", false)
        set(v) = prefs.edit().putBoolean("backup_reminder", v).apply()
    var lastBackupAt: Long
        get() = prefs.getLong("last_backup", 0L)
        set(v) = prefs.edit().putLong("last_backup", v).apply()

    /** Tu: il tuo giocatore del database (PlayerProfile.id; vuoto = non ancora scelto). */
    var myPlayerId: String
        get() = prefs.getString("my_player", null) ?: ""
        set(v) = prefs.edit().putString("my_player", v).apply()

    /** Telefono-telecamera: obiettivo scelto (id della fotocamera, null = quello di serie). */
    var cameraLensId: String?
        get() = prefs.getString("camera_lens", null)
        set(v) = prefs.edit().putString("camera_lens", v).apply()
    /** Assistente inquadratura: metri dietro la linea di fondo, metri da terra, campo (Framing.Court). */
    var framingDistance: Float
        get() = prefs.getFloat("framing_distance", 6f)
        set(v) = prefs.edit().putFloat("framing_distance", v).apply()
    var framingHeight: Float
        get() = prefs.getFloat("framing_height", 2.5f)
        set(v) = prefs.edit().putFloat("framing_height", v).apply()
    var framingCourt: String
        get() = prefs.getString("framing_court", null) ?: "TENNIS_DOUBLES"
        set(v) = prefs.edit().putString("framing_court", v).apply()
    /** Taratura completa della sagoma (distanza, altezza e campo sono quelli qui sopra). */
    var framingCalib: com.tennis.scoremanager.video.Framing.Calib
        get() = com.tennis.scoremanager.video.Framing.Calib(
            framingDistance, framingHeight, framingCourt,
            prefs.getFloat("framing_pan", 0f), prefs.getFloat("framing_lateral", 0f),
            prefs.getFloat("framing_fov", 1f), prefs.getFloat("framing_pitch_off", 0f),
        )
        set(v) {
            prefs.edit().putFloat("framing_distance", v.distance).putFloat("framing_height", v.height).putString("framing_court", v.court)
                .putFloat("framing_pan", v.pan).putFloat("framing_lateral", v.lateral).putFloat("framing_fov", v.fovScale)
                .putFloat("framing_pitch_off", v.pitchOffset).apply()
        }

    /** Taratura salvata per un campo ("Circolo · Campo" dal punteggio dell'arbitro), null se non c'è. */
    fun framingCalibFor(place: String): com.tennis.scoremanager.video.Framing.Calib? =
        prefs.getString("framing_calib_" + place.trim().lowercase(), null)?.let {
            runCatching { json.decodeFromString(com.tennis.scoremanager.video.Framing.Calib.serializer(), it) }.getOrNull()
        }

    fun saveFramingCalibFor(place: String, c: com.tennis.scoremanager.video.Framing.Calib) {
        if (place.isBlank()) return
        prefs.edit().putString("framing_calib_" + place.trim().lowercase(), json.encodeToString(com.tennis.scoremanager.video.Framing.Calib.serializer(), c)).apply()
    }

    private fun read(s: String?): SetupData? =
        s?.let { runCatching { json.decodeFromString(SetupData.serializer(), it) }.getOrNull() }

    fun bandAddress(p1: Boolean): String? = prefs.getString(if (p1) "band_p1" else "band_p2", null)

    /**
     * Braccialetti a cui l'allenamento dei colpi ha acceso il riconoscimento (indirizzi): finito l'allenamento si
     * rispegne, anche più tardi se intanto il braccialetto era spento.
     */
    var strokesAutoOn: Set<String>
        get() = prefs.getStringSet("strokes_auto_on", null).orEmpty().toSet()
        set(v) = prefs.edit().putStringSet("strokes_auto_on", v).apply()
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
