// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.voice

import android.content.Context
import android.net.Uri
import com.tennis.scoremanager.model.Lang
import com.tennis.scoremanager.ui.Strings
import java.io.File
import java.io.IOException
import java.util.zip.CRC32
import java.util.zip.CheckedInputStream
import java.util.zip.ZipException
import java.util.zip.ZipFile

/**
 * Cartella locale con i file audio delle chiamate (uso senza internet).
 *
 *   Android/data/com.tennis.scoremanager/files/voice/it/        registrazioni personalizzate (priorità)
 *   Android/data/com.tennis.scoremanager/files/voice/it/tts/    file generati dal TTS del telefono
 *
 * Il nome di ogni file è la chiave della frase (vedi LEGGIMI.txt), es. `score_1_0.mp3` = "quindici zero".
 * LEGGIMI.txt ha sempre questo nome (lo citano le guide); le spiegazioni in testa sono nella lingua dell'app.
 */
class VoicePack(private val context: Context) {

    companion object {
        val EXTS = listOf("wav", "mp3", "ogg", "m4a", "aac", "flac")

        /** File più piccoli di così sono vuoti o rovinati: non si usano. */
        const val MIN_BYTES = 64L
    }

    private val custom = mutableMapOf<Lang, Map<String, File>>()
    private val generated = mutableMapOf<Lang, Map<String, File>>()

    /** File che non si sono potuti riprodurre: esclusi fino al riavvio dell'app, o finché non vengono sostituiti. */
    private val broken = mutableSetOf<String>()
    private fun id(f: File) = "${f.absolutePath}|${f.length()}|${f.lastModified()}"

    val baseDir: File
        get() = File(context.getExternalFilesDir(null) ?: context.filesDir, "voice")

    fun dir(lang: Lang): File = File(baseDir, lang.name.lowercase()).apply { mkdirs() }
    fun ttsDir(lang: Lang): File = File(dir(lang), "tts").apply { mkdirs() }

    /** Registrazione personalizzata (voce vera) della frase, se c'è: ha sempre la precedenza. */
    @Synchronized
    fun customFor(key: String, lang: Lang): File? = lookup(custom.getOrPut(lang) { scan(dir(lang)) }, key, lang)

    /** File generato dal TTS del telefono (usato solo con l'opzione "File audio offline"). */
    @Synchronized
    fun generatedFor(key: String, lang: Lang): File? = lookup(generated.getOrPut(lang) { scan(ttsDir(lang)) }, key, lang)

    /** Se manca il file della frase va bene quello di una frase con lo stesso testo ([Phrases.alias]). */
    private fun lookup(files: Map<String, File>, key: String, lang: Lang): File? =
        files[key] ?: Phrases.alias(key, lang)?.let { files[it] }

    /** Il file non si riesce a riprodurre (rovinato o in un formato che il telefono non legge): non si usa più. */
    @Synchronized
    fun markBroken(f: File) {
        broken += id(f)
        for (m in listOf(custom, generated)) {
            for (l in m.keys.toList()) m[l] = m.getValue(l).filterValues { it != f }
        }
    }

    @Synchronized
    fun refresh(lang: Lang) {
        custom[lang] = scan(dir(lang))
        generated[lang] = scan(ttsDir(lang))
    }

    fun generatedCount(lang: Lang): Int = Phrases.keys.count { generatedFor(it, lang) != null }
    fun customCount(lang: Lang): Int = Phrases.keys.count { customFor(it, lang) != null }

    private fun scan(folder: File): Map<String, File> {
        val out = HashMap<String, File>()
        folder.listFiles()?.forEach { f ->
            if (f.isFile && f.extension.lowercase() in EXTS && f.length() > MIN_BYTES && f.nameWithoutExtension in Phrases.keys &&
                id(f) !in broken
            ) {
                out[f.nameWithoutExtension] = f
            }
        }
        return out
    }

    /**
     * Importa uno ZIP di registrazioni (quali file e in che lingua: [VoiceZip.target]). Lo ZIP viene prima copiato
     * ed estratto tutto in una cartella a parte: se è rovinato o troncato lancia un'eccezione e le registrazioni
     * restano com'erano. Ritorna quante registrazioni ha importato.
     */
    fun importZip(uri: Uri, fallback: Lang): Int {
        val tmp = File(context.cacheDir, "voice-import.zip")
        // Sotto voice/, così i file si spostano al loro posto senza copiarli di nuovo.
        val staging = File(baseDir, ".import")
        try {
            val input = context.contentResolver.openInputStream(uri) ?: throw IOException("ZIP non leggibile")
            input.use { i -> tmp.outputStream().use { i.copyTo(it) } }
            staging.deleteRecursively()
            staging.mkdirs()
            VoiceZip.extract(tmp, staging, fallback)
            return synchronized(this) { VoiceZip.install(staging) { dir(it) } }
        } finally {
            tmp.delete()
            staging.deleteRecursively()
            Lang.entries.forEach { refresh(it) }
        }
    }

    fun deleteCustom(lang: Lang) {
        dir(lang).listFiles()?.filter { it.isFile && it.extension.lowercase() in EXTS }?.forEach { it.delete() }
        refresh(lang)
    }

    /** Cartella dove si generano i file nuovi: quelli di prima restano in tts/ finché i nuovi non sono pronti. */
    fun newGeneratedDir(lang: Lang): File = File(dir(lang), ".tts-new").apply { deleteRecursively(); mkdirs() }

    /** Mette i file generati in [fresh] al posto di quelli di tts/; se non ci riesce restano quelli di prima. */
    @Synchronized
    fun installGenerated(lang: Lang, fresh: File): Boolean {
        val cur = ttsDir(lang)
        val old = File(dir(lang), ".tts-old").apply { deleteRecursively() }
        val ok = when {
            !cur.renameTo(old) -> false
            !fresh.renameTo(cur) -> { old.renameTo(cur); false }
            else -> { old.deleteRecursively(); true }
        }
        fresh.deleteRecursively()
        refresh(lang)
        return ok
    }

    /** Elenco delle frasi da registrare, scritto nella cartella voce (di nuovo a ogni cambio di lingua). */
    @Synchronized
    fun writeReadme(s: Strings) {
        val sb = StringBuilder()
        sb.appendLine(s.voiceReadme(Lang.entries.joinToString(", ") { it.code }, EXTS.joinToString()))
        // Una colonna per lingua, separate da tabulazioni: si apre bene anche come foglio di calcolo.
        sb.appendLine()
        sb.appendLine((listOf(s.voiceReadmeKey) + Lang.entries.map { it.label.uppercase() }).joinToString("\t"))
        for (k in Phrases.keys) {
            sb.appendLine((listOf(k) + Lang.entries.map { Phrases.text(k, it) }).joinToString("\t"))
        }
        runCatching { File(baseDir.apply { mkdirs() }, "LEGGIMI.txt").writeText(sb.toString()) }
    }
}

/** Lettura degli ZIP di registrazioni, senza Android: si prova nei test. */
internal object VoiceZip {

    /**
     * Dove va un file dello ZIP: (lingua, nome del file) oppure null se va ignorato.
     * - `it/deuce.mp3`, anche dentro altre cartelle (`voice/it/deuce.mp3`): lingua = la cartella che contiene il file;
     * - file fuori da ogni cartella di lingua (`deuce.mp3`, `Registrazioni/deuce.mp3`): lingua [fallback];
     * - si ignora quello che sta in una cartella `tts` (i file generati dall'app: importando la cartella voice/
     *   non devono diventare registrazioni) o più in fondo dentro una cartella di lingua (`it/vecchie/deuce.mp3`).
     */
    fun target(entryName: String, fallback: Lang): Pair<Lang, String>? {
        val parts = entryName.replace('\\', '/').split('/').filter { it.isNotEmpty() }
        val file = File(parts.lastOrNull() ?: return null)
        val ext = file.extension.lowercase()
        if (file.nameWithoutExtension !in Phrases.keys || ext !in VoicePack.EXTS) return null
        val folders = parts.dropLast(1).map { it.lowercase() }
        if ("tts" in folders) return null
        val parent = folders.lastOrNull()?.let { Lang.fromCode(it) }
        val lang = when {
            parent != null -> parent
            folders.any { Lang.fromCode(it) != null } -> return null
            else -> fallback
        }
        return lang to "${file.nameWithoutExtension}.$ext"
    }

    /**
     * Estrae i file validi dello ZIP in [staging]/`<lingua>`/. ZipFile legge l'indice in fondo all'archivio, quindi
     * uno ZIP troncato fallisce subito; un file rovinato fallisce sul controllo CRC. In entrambi i casi eccezione.
     * Ritorna quanti file ha estratto.
     */
    fun extract(zipFile: File, staging: File, fallback: Lang): Int {
        ZipFile(zipFile).use { zip ->
            for (entry in zip.entries()) {
                if (entry.isDirectory) continue
                val (lang, name) = target(entry.name, fallback) ?: continue
                val dir = File(staging, lang.code).apply { mkdirs() }
                val out = File(dir, name)
                val crc = CRC32()
                CheckedInputStream(zip.getInputStream(entry), crc).use { input -> out.outputStream().use { input.copyTo(it) } }
                if (entry.crc != -1L && crc.value != entry.crc) throw ZipException("CRC errato: ${entry.name}")
                if (out.length() <= VoicePack.MIN_BYTES) { out.delete(); continue }
                // La stessa frase in due formati: vale l'ultima.
                VoicePack.EXTS.forEach { e -> File(dir, "${out.nameWithoutExtension}.$e").takeIf { it != out }?.delete() }
            }
        }
        return staging.listFiles().orEmpty().sumOf { it.listFiles().orEmpty().size }
    }

    /** Sposta i file estratti nella cartella della loro lingua ([dir]), togliendo la stessa frase negli altri formati. */
    fun install(staging: File, dir: (Lang) -> File): Int {
        var n = 0
        for (langDir in staging.listFiles().orEmpty()) {
            val dest = dir(Lang.fromCode(langDir.name) ?: continue)
            for (f in langDir.listFiles().orEmpty()) {
                VoicePack.EXTS.forEach { File(dest, "${f.nameWithoutExtension}.$it").delete() }
                val to = File(dest, f.name)
                if (!f.renameTo(to)) f.copyTo(to, overwrite = true)
                n++
            }
        }
        return n
    }
}
