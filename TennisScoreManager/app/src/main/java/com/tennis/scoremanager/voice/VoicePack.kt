package com.tennis.scoremanager.voice

import android.content.Context
import android.net.Uri
import com.tennis.scoremanager.model.Lang
import java.io.File
import java.util.zip.ZipInputStream

/**
 * Cartella locale con i file audio delle chiamate (uso senza internet).
 *
 *   Android/data/com.tennis.scoremanager/files/voice/it/        registrazioni personalizzate (priorità)
 *   Android/data/com.tennis.scoremanager/files/voice/it/tts/    file generati dal TTS del telefono
 *
 * Il nome di ogni file è la chiave della frase (vedi LEGGIMI.txt), es. `score_1_0.mp3` = "quindici zero".
 */
class VoicePack(private val context: Context) {

    private val exts = listOf("wav", "mp3", "ogg", "m4a", "aac", "flac")
    private val custom = mutableMapOf<Lang, Map<String, File>>()
    private val generated = mutableMapOf<Lang, Map<String, File>>()

    val baseDir: File
        get() = File(context.getExternalFilesDir(null) ?: context.filesDir, "voice")

    fun dir(lang: Lang): File = File(baseDir, lang.name.lowercase()).apply { mkdirs() }
    fun ttsDir(lang: Lang): File = File(dir(lang), "tts").apply { mkdirs() }

    /** Registrazione personalizzata (voce vera) della frase, se c'è: ha sempre la precedenza. */
    @Synchronized
    fun customFor(key: String, lang: Lang): File? = custom.getOrPut(lang) { scan(dir(lang)) }[key]

    /** File generato dal TTS del telefono (usato solo con l'opzione "File audio offline"). */
    @Synchronized
    fun generatedFor(key: String, lang: Lang): File? = generated.getOrPut(lang) { scan(ttsDir(lang)) }[key]

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
            if (f.isFile && f.extension.lowercase() in exts && f.length() > 64 && f.nameWithoutExtension in Phrases.keys) {
                out[f.nameWithoutExtension] = f
            }
        }
        return out
    }

    /** Importa uno ZIP di registrazioni: `it/score_1_0.mp3`, `fr/deuce.wav` o file alla radice (lingua corrente). */
    fun importZip(uri: Uri, fallback: Lang): Int {
        var n = 0
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) continue
                    val path = entry.name.replace('\\', '/')
                    val file = File(path.substringAfterLast('/'))
                    val key = file.nameWithoutExtension
                    val ext = file.extension.lowercase()
                    if (key !in Phrases.keys || ext !in exts) continue
                    val folders = path.lowercase().split('/').dropLast(1)
                    val lang = Lang.entries.firstOrNull { it.code in folders } ?: fallback
                    exts.forEach { File(dir(lang), "$key.$it").delete() }
                    File(dir(lang), "$key.$ext").outputStream().use { zip.copyTo(it) }
                    n++
                }
            }
        }
        Lang.entries.forEach { refresh(it) }
        return n
    }

    fun deleteCustom(lang: Lang) {
        dir(lang).listFiles()?.filter { it.isFile && it.extension.lowercase() in exts }?.forEach { it.delete() }
        refresh(lang)
    }

    fun deleteGenerated(lang: Lang) {
        ttsDir(lang).listFiles()?.forEach { it.delete() }
        refresh(lang)
    }

    /** Elenco delle frasi da registrare, scritto nella cartella voce. */
    fun writeReadme() {
        val sb = StringBuilder()
        sb.appendLine("TENNIS SCORE MANAGER - FILE VOCALI")
        sb.appendLine("Metti le registrazioni in voice/<lingua>/ (${Lang.entries.joinToString(", ") { it.code }}) con il nome della chiave.")
        sb.appendLine("Formati: ${exts.joinToString()}. I nomi dei giocatori sono sempre letti dal TTS.")
        sb.appendLine("La cartella tts/ contiene i file generati dall'app: le tue registrazioni hanno la precedenza.")
        // Una colonna per lingua, separate da tabulazioni: si apre bene anche come foglio di calcolo.
        sb.appendLine()
        sb.appendLine((listOf("CHIAVE") + Lang.entries.map { it.label.uppercase() }).joinToString("\t"))
        for (k in Phrases.keys) {
            sb.appendLine((listOf(k) + Lang.entries.map { Phrases.text(k, it) }).joinToString("\t"))
        }
        runCatching { File(baseDir.apply { mkdirs() }, "LEGGIMI.txt").writeText(sb.toString()) }
    }
}
