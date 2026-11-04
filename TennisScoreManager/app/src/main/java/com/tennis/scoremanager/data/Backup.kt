// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Backup completo in un file ZIP che resta di chi lo fa (nessun cloud): partite, giocatori, sessioni, racchette,
 * allenamenti, tornei, foto, impostazioni, registrazioni della voce, analisi e immagini dei colpi; a scelta i video.
 * Non entrano il modello dell'analisi (si riscarica) e i file salvati dall'utente in Documenti (sono già suoi).
 *
 * Nel file: manifest.json, files/... (cartella interna), prefs/... (impostazioni), external/... (cartella dell'app),
 * documents/... (Allenamento colpi in Documenti).
 */
object Backup {
    const val VERSION = 1
    /** Cartelle interne che non entrano: il modello dell'analisi del movimento e i file di sistema. */
    private val SKIP_FILES = setOf("pose", "office_backups", "profileInstalled", "profileinstaller_profileWrittenFor_lastUpdateTime.dat")
    /** Impostazioni dell'app (shared_prefs). */
    private val PREFS = listOf("tsm.xml", "tv_server.xml")
    /** Nella cartella dell'app: la voce sempre, i video (moviola, highlights) a scelta. */
    private const val EXT_VIDEOS = "Movies"
    private const val EXT_VOICE = "voice"

    @Serializable
    data class Manifest(val version: Int = VERSION, val app: String = "", val createdAt: Long = 0, val videos: Boolean = false)

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private fun prefsDir(c: Context) = File(c.applicationInfo.dataDir, "shared_prefs")
    private fun extDir(c: Context): File? = c.getExternalFilesDir(null)
    private fun strokes(storage: Storage, folder: String) = File(storage.documentsRoot, folder)

    private fun isVideo(f: File) = f.extension.equals("mp4", ignoreCase = true)

    /** Quanto pesano i video che entrerebbero col backup (clip dei colpi, moviola, highlights), in byte. */
    fun videoBytes(c: Context, storage: Storage, strokesFolder: String): Long =
        listOfNotNull(extDir(c)?.let { File(it, EXT_VIDEOS) }, strokes(storage, strokesFolder))
            .sumOf { d -> d.walkTopDown().filter { it.isFile && isVideo(it) }.sumOf { it.length() } }

    /** Scrive il backup in [out]; [onProgress] riceve i file scritti. */
    fun write(c: Context, storage: Storage, out: OutputStream, videos: Boolean, version: String, strokesFolder: String, onProgress: (Int) -> Unit = {}) {
        var n = 0
        ZipOutputStream(out.buffered()).use { zip ->
            fun put(name: String, f: File) {
                zip.putNextEntry(ZipEntry(name).apply { time = f.lastModified() })
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
                onProgress(++n)
            }
            fun tree(prefix: String, root: File, keep: (File) -> Boolean) {
                if (!root.exists()) return
                root.walkTopDown().filter { it.isFile && keep(it) }.forEach { put(prefix + it.relativeTo(root).invariantSeparatorsPath, it) }
            }
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(json.encodeToString(Manifest.serializer(), Manifest(app = version, createdAt = System.currentTimeMillis(), videos = videos)).toByteArray())
            zip.closeEntry()
            c.filesDir.listFiles().orEmpty().filter { it.name !in SKIP_FILES }.forEach { f ->
                if (f.isDirectory) tree("files/${f.name}/", f) { true } else put("files/${f.name}", f)
            }
            PREFS.map { File(prefsDir(c), it) }.filter { it.exists() }.forEach { put("prefs/${it.name}", it) }
            extDir(c)?.let { ext ->
                tree("external/$EXT_VOICE/", File(ext, EXT_VOICE)) { true }
                if (videos) tree("external/$EXT_VIDEOS/", File(ext, EXT_VIDEOS)) { true }
            }
            // Le clip dei colpi: analisi e immagini sempre (sono piccole e le sessioni le usano), i video a scelta.
            tree("documents/$strokesFolder/", strokes(storage, strokesFolder)) { videos || !isVideo(it) }
        }
    }

    /** Legge solo il manifest (per chiedere conferma prima di ripristinare); null se non è un backup dell'app. */
    fun peek(input: InputStream): Manifest? = ZipInputStream(input.buffered()).use { zip ->
        val e = zip.nextEntry ?: return null
        if (e.name != "manifest.json") return null
        runCatching { json.decodeFromString(Manifest.serializer(), zip.readBytes().decodeToString()) }.getOrNull()
    }

    /**
     * Ripristina: prima estrae tutto in una cartella d'appoggio (se il file è rovinato non si tocca niente), poi
     * cancella i dati di adesso e mette quelli del backup. Dopo, l'app va riavviata (le impostazioni stanno in
     * memoria). false se il file non è un backup valido.
     */
    fun restore(c: Context, storage: Storage, input: InputStream): Boolean {
        val stage = File(c.cacheDir, "restore").apply { deleteRecursively(); mkdirs() }
        var manifest: Manifest? = null
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                if (e.isDirectory) continue
                // Niente percorsi che escono dalla cartella (../), niente nomi strani.
                if (e.name.contains("..") || e.name.startsWith("/")) return false.also { stage.deleteRecursively() }
                if (e.name == "manifest.json") {
                    manifest = runCatching { json.decodeFromString(Manifest.serializer(), zip.readBytes().decodeToString()) }.getOrNull()
                    continue
                }
                val f = File(stage, e.name)
                f.parentFile?.mkdirs()
                f.outputStream().use { zip.copyTo(it) }
            }
        }
        if (manifest == null || manifest!!.version > VERSION) {
            stage.deleteRecursively()
            return false
        }
        wipe(c, storage, videos = manifest!!.videos, strokesFolders = File(stage, "documents").list()?.toList().orEmpty())
        fun move(from: File, to: File) {
            if (!from.exists()) return
            from.walkTopDown().filter { it.isFile }.forEach { f ->
                val dest = File(to, f.relativeTo(from).path)
                dest.parentFile?.mkdirs()
                if (!f.renameTo(dest)) f.copyTo(dest, overwrite = true)
            }
        }
        move(File(stage, "files"), c.filesDir)
        move(File(stage, "prefs"), prefsDir(c))
        extDir(c)?.let { move(File(stage, "external"), it) }
        move(File(stage, "documents"), storage.documentsRoot)
        stage.deleteRecursively()
        return true
    }

    /**
     * Cancella i dati dell'app: cartella interna (non il modello dell'analisi), impostazioni, voce, analisi delle clip
     * dei colpi in Documenti ([strokesFolders]); con [videos] anche i video delle clip, la moviola e gli highlights. I file salvati
     * dall'utente in Documenti e i video della galleria restano.
     */
    fun wipe(c: Context, storage: Storage, videos: Boolean, strokesFolders: List<String>) {
        c.filesDir.listFiles().orEmpty().filter { it.name !in SKIP_FILES }.forEach { it.deleteRecursively() }
        PREFS.map { File(prefsDir(c), it) }.forEach { it.delete() }
        extDir(c)?.let { ext ->
            File(ext, EXT_VOICE).deleteRecursively()
            if (videos) File(ext, EXT_VIDEOS).deleteRecursively()
        }
        // Senza i video si tolgono solo analisi e immagini: i video delle clip restano dove sono.
        strokesFolders.forEach { name ->
            val d = strokes(storage, name)
            if (videos) d.deleteRecursively()
            else d.walkTopDown().filter { it.isFile && !isVideo(it) }.toList().forEach { it.delete() }
        }
    }
}
