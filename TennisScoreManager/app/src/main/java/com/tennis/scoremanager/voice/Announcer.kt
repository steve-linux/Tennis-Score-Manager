package com.tennis.scoremanager.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.tennis.scoremanager.model.Lang
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

enum class TtsStatus { INIT, READY, MISSING_LANGUAGE, ERROR }

/**
 * Legge le chiamate: file audio locali quando esistono, altrimenti TTS; i nomi sempre col TTS.
 * L'audio esce dal canale "media", quindi va anche su una cassa Bluetooth collegata al telefono.
 */
class Announcer(context: Context, private val voice: VoicePack) : TextToSpeech.OnInitListener {

    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private var tts: TextToSpeech? = TextToSpeech(app, this)
    private val pending = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()
    private var job: Job? = null
    private var player: MediaPlayer? = null

    private val _status = MutableStateFlow(TtsStatus.INIT)
    val status: StateFlow<TtsStatus> = _status

    var enabled: Boolean = true
        set(value) {
            field = value
            if (!value) stop()
        }

    var lang: Lang = Lang.IT
        set(value) {
            if (field != value) {
                field = value
                applyLanguage(value)
            }
        }

    override fun onInit(status: Int) {
        val t = tts
        if (status != TextToSpeech.SUCCESS || t == null) {
            _status.value = TtsStatus.ERROR
            return
        }
        t.setAudioAttributes(attrs)
        t.setSpeechRate(0.95f)
        t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                utteranceId?.let { pending.remove(it)?.complete(true) }
            }

            @Suppress("OVERRIDE_DEPRECATION")
            override fun onError(utteranceId: String?) {
                utteranceId?.let { pending.remove(it)?.complete(false) }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                utteranceId?.let { pending.remove(it)?.complete(false) }
            }

            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                utteranceId?.let { pending.remove(it)?.complete(false) }
            }
        })
        applyLanguage(lang)
    }

    private fun locale(l: Lang): Locale = if (l == Lang.IT) Locale.ITALY else Locale.UK

    /** Imposta la lingua preferendo una voce installata sul telefono (niente rete). */
    private fun applyLanguage(l: Lang) {
        val t = tts ?: return
        if (_status.value == TtsStatus.ERROR) return
        val loc = locale(l)
        val res = runCatching { t.setLanguage(loc) }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
        if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
            _status.value = TtsStatus.MISSING_LANGUAGE
            return
        }
        runCatching {
            t.voices
                ?.filter { it.locale.language == loc.language && !it.isNetworkConnectionRequired && "notInstalled" !in it.features }
                ?.maxWithOrNull(compareBy({ it.locale.country == loc.country }, { it.quality }))
                ?.let { t.voice = it }
        }
        _status.value = TtsStatus.READY
    }

    private sealed interface Part {
        val tag: String?

        data class Speech(val text: String, override val tag: String?) : Part
        data class Audio(val file: File, override val tag: String?) : Part
        data class Silence(val ms: Long) : Part {
            override val tag: String? get() = null
        }
    }

    /** Unisce i pezzi consecutivi senza file in un'unica frase TTS: suona molto più naturale. */
    private fun plan(segs: List<Seg>, l: Lang): List<Part> {
        val parts = mutableListOf<Part>()
        val text = StringBuilder()
        var tag: String? = null
        fun flush() {
            if (text.isNotBlank()) parts += Part.Speech(text.toString().trim(), tag)
            text.clear()
            tag = null
        }
        for (s in segs) {
            when (s) {
                is Seg.Pause -> { flush(); parts += Part.Silence(s.ms) }
                is Seg.Clip -> {
                    val f = voice.fileFor(s.key, l)
                    if (f != null) {
                        flush()
                        parts += Part.Audio(f, s.tag)
                    } else {
                        if (s.tag != null) flush()
                        if (s.tag != null) tag = s.tag
                        text.append(Phrases.text(s.key, l)).append(' ')
                    }
                }
                is Seg.Say -> {
                    if (s.tag != null) { flush(); tag = s.tag }
                    text.append(s.text).append(' ')
                }
            }
        }
        flush()
        return parts
    }

    /**
     * Legge una chiamata interrompendo quella in corso. [onTag] viene chiamato quando parte un pezzo
     * con tag (es. "gioco" che avvia il tempo partita); se l'audio è spento o la chiamata viene
     * interrotta, i tag vengono comunque notificati subito. [force] legge anche ad audio spento (prova voce).
     */
    fun announce(segs: List<Seg>, force: Boolean = false, onTag: (String) -> Unit = {}) {
        val tags = segs.mapNotNull {
            when (it) {
                is Seg.Clip -> it.tag
                is Seg.Say -> it.tag
                is Seg.Pause -> null
            }
        }
        stop()
        // In Logcat (filtro "Announcer") si legge ogni chiamata: comodo per controllare le frasi.
        Log.d("Announcer", CallBuilder(lang).render(segs))
        if ((!enabled && !force) || segs.isEmpty()) {
            tags.forEach(onTag)
            return
        }
        val l = lang
        val fired = mutableSetOf<String>()
        job = scope.launch {
            try {
                for (p in plan(segs, l)) {
                    p.tag?.let { fired += it; onTag(it) }
                    when (p) {
                        is Part.Silence -> delay(p.ms)
                        is Part.Audio -> playFile(p.file)
                        is Part.Speech -> speak(p.text)
                    }
                }
            } finally {
                tags.filter { it !in fired }.forEach(onTag)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        runCatching { tts?.stop() }
        pending.values.forEach { it.complete(false) }
        pending.clear()
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
    }

    private suspend fun speak(text: String) {
        val t = tts ?: return
        if (_status.value != TtsStatus.READY) return
        val id = UUID.randomUUID().toString()
        val done = CompletableDeferred<Boolean>()
        pending[id] = done
        val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1f) }
        if (t.speak(text, TextToSpeech.QUEUE_ADD, params, id) != TextToSpeech.SUCCESS) {
            pending.remove(id)
            return
        }
        withTimeoutOrNull(3_000L + text.length * 150L) { done.await() }
        pending.remove(id)
    }

    private suspend fun playFile(file: File) {
        val mp = MediaPlayer()
        player = mp
        try {
            mp.setAudioAttributes(attrs)
            mp.setDataSource(file.absolutePath)
            mp.prepare()
            withTimeoutOrNull(mp.duration.toLong().coerceAtLeast(500L) + 1_500L) {
                suspendCancellableCoroutine<Unit> { cont ->
                    mp.setOnCompletionListener { if (cont.isActive) cont.resume(Unit) }
                    mp.setOnErrorListener { _, _, _ -> if (cont.isActive) cont.resume(Unit); true }
                    mp.start()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("Announcer", "File audio non leggibile: ${file.name}", e)
        } finally {
            if (player === mp) player = null
            runCatching { mp.release() }
        }
    }

    /**
     * Genera i file vocali di [l] con il TTS del telefono (una volta sola, poi funzionano offline).
     * Ritorna quanti file sono stati creati.
     */
    suspend fun generateVoicePack(l: Lang, onProgress: (Int, Int) -> Unit): Int {
        val t = tts ?: return 0
        withTimeoutOrNull(5_000) { while (_status.value == TtsStatus.INIT) delay(100) }
        if (_status.value == TtsStatus.ERROR) return 0
        stop()
        applyLanguage(l)
        if (_status.value != TtsStatus.READY) {
            applyLanguage(lang)
            return 0
        }
        val dir = voice.ttsDir(l)
        val keys = Phrases.keys
        var ok = 0
        for ((i, key) in keys.withIndex()) {
            val f = File(dir, "$key.wav")
            val id = "gen_${key}_${UUID.randomUUID()}"
            val done = CompletableDeferred<Boolean>()
            pending[id] = done
            val r = t.synthesizeToFile(Phrases.text(key, l), Bundle(), f, id)
            val good = r == TextToSpeech.SUCCESS && withTimeoutOrNull(20_000) { done.await() } == true
            pending.remove(id)
            if (good && f.length() > 64) ok++ else f.delete()
            onProgress(i + 1, keys.size)
        }
        applyLanguage(lang)
        voice.refresh(l)
        return ok
    }

    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
    }
}
