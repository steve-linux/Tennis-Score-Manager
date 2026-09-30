package com.tennis.scoremanager.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import com.tennis.scoremanager.model.Lang
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
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
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

enum class TtsStatus { INIT, READY, MISSING_LANGUAGE, ERROR }

/** Motore di sintesi vocale installato sul telefono (Samsung, Google, ...). */
data class EngineOption(val pkg: String, val label: String)

/** Voce disponibile per la lingua corrente. */
data class VoiceOption(val name: String, val online: Boolean, val quality: Int)

/**
 * Legge le chiamate. Di norma ogni chiamata è detta dalla sintesi vocale in un'unica frase (suona naturale e
 * funziona offline con le voci installate); le registrazioni personalizzate, se ci sono, hanno la precedenza.
 * Con [useGeneratedFiles] si usano invece i file generati una volta dal TTS (utile con una voce online).
 * L'audio esce dal canale "media", quindi va anche su una cassa Bluetooth collegata al telefono.
 */
class Announcer(context: Context, private val voice: VoicePack) : TextToSpeech.OnInitListener {

    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private var enginePkg: String? = null
    private var preferredVoice: String? = null
    private var tts: TextToSpeech? = TextToSpeech(app, this)
    private val pending = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()
    private var job: Job? = null
    private var player: MediaPlayer? = null

    private val _status = MutableStateFlow(TtsStatus.INIT)
    val status: StateFlow<TtsStatus> = _status
    private val _engines = MutableStateFlow<List<EngineOption>>(emptyList())
    val engines: StateFlow<List<EngineOption>> = _engines
    private val _voices = MutableStateFlow<List<VoiceOption>>(emptyList())
    val voices: StateFlow<List<VoiceOption>> = _voices
    private val _currentVoice = MutableStateFlow<String?>(null)
    val currentVoice: StateFlow<String?> = _currentVoice
    private val _currentEngine = MutableStateFlow<String?>(null)
    val currentEngineFlow: StateFlow<String?> = _currentEngine
    private val _playing = MutableStateFlow<String?>(null)
    /** Chiamata in corso: il [name] passato ad [announce] ("" se senza nome); null = nessuna. */
    val playing: StateFlow<String?> = _playing

    var enabled: Boolean = true
        set(value) {
            field = value
            if (!value) stop()
        }

    var useGeneratedFiles: Boolean = false

    var lang: Lang = Lang.IT
        set(value) {
            if (field != value) {
                field = value
                applyLanguage(value)
            }
        }

    /** Pacchetto del motore effettivamente in uso (serve alle correzioni di pronuncia). */
    private val currentEngine: String?
        get() = enginePkg ?: runCatching { tts?.defaultEngine }.getOrNull()

    /** Sceglie motore (null = predefinito del telefono) e voce (null = la migliore offline). */
    fun configure(engine: String?, voiceName: String?) {
        preferredVoice = voiceName
        if (engine != enginePkg) {
            enginePkg = engine
            stop()
            runCatching { tts?.shutdown() }
            _status.value = TtsStatus.INIT
            tts = TextToSpeech(app, this, engine)
        } else {
            applyLanguage(lang)
        }
    }

    override fun onInit(status: Int) {
        val t = tts
        if (status != TextToSpeech.SUCCESS || t == null) {
            _status.value = TtsStatus.ERROR
            return
        }
        t.setAudioAttributes(attrs)
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
        _engines.value = runCatching { t.engines.map { EngineOption(it.name, it.label) } }.getOrDefault(emptyList())
        _currentEngine.value = currentEngine
        applyLanguage(lang)
    }

    /** Imposta la lingua e la voce: quella scelta se esiste, altrimenti la migliore installata sul telefono. */
    private fun applyLanguage(l: Lang) {
        val t = tts ?: return
        if (_status.value == TtsStatus.ERROR) return
        val loc = l.locale
        val res = runCatching { t.setLanguage(loc) }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
        if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
            _voices.value = emptyList()
            _status.value = TtsStatus.MISSING_LANGUAGE
            return
        }
        val all: List<Voice> = runCatching { t.voices?.toList() }.getOrNull().orEmpty()
            .filter { it.locale.language == loc.language && "notInstalled" !in it.features }
        _voices.value = all
            .map { VoiceOption(it.name, it.isNetworkConnectionRequired, it.quality) }
            .sortedWith(compareBy({ it.online }, { it.name }))
        val chosen = all.firstOrNull { it.name == preferredVoice }
            ?: all.filter { !it.isNetworkConnectionRequired }
                .maxWithOrNull(compareBy({ it.locale.country == loc.country }, { it.quality }))
        chosen?.let { runCatching { t.voice = it } }
        _currentVoice.value = runCatching { t.voice?.name }.getOrNull()
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

    private fun fileFor(key: String, l: Lang): File? =
        voice.customFor(key, l) ?: if (useGeneratedFiles) voice.generatedFor(key, l) else null

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
                    val f = fileFor(s.key, l)
                    if (f != null) {
                        flush()
                        parts += Part.Audio(f, s.tag)
                    } else {
                        if (s.tag != null) { flush(); tag = s.tag }
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
     * [name] distingue la chiamata in [playing] (es. la prova voce, che il suo tasto può fermare).
     */
    fun announce(segs: List<Seg>, force: Boolean = false, name: String = "", onTag: (String) -> Unit = {}) {
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
        // LAZY: [job] è assegnato prima che parta, così anche una chiamata che finisce subito azzera [playing].
        val j = scope.launch(start = CoroutineStart.LAZY) {
            try {
                for (p in plan(segs, l)) {
                    p.tag?.let { fired += it; onTag(it) }
                    when (p) {
                        is Part.Silence -> delay(p.ms)
                        is Part.Audio -> playFile(p.file)
                        is Part.Speech -> speak(Pronunciation.fix(p.text, l, currentEngine))
                    }
                }
            } finally {
                tags.filter { it !in fired }.forEach(onTag)
                if (job === coroutineContext[Job]) {
                    job = null
                    _playing.value = null
                }
            }
        }
        job = j
        _playing.value = name
        j.start()
    }

    fun stop() {
        job?.cancel()
        job = null
        _playing.value = null
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
     * Genera i file vocali di [l] con la voce scelta (anche una voce online: dopo funzionano senza rete).
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
        voice.deleteGenerated(l)
        val dir = voice.ttsDir(l)
        val keys = Phrases.keys
        val engine = currentEngine
        var ok = 0
        for ((i, key) in keys.withIndex()) {
            val f = File(dir, "$key.wav")
            val id = "gen_${key}_${UUID.randomUUID()}"
            val done = CompletableDeferred<Boolean>()
            pending[id] = done
            val r = t.synthesizeToFile(Pronunciation.fix(Phrases.text(key, l), l, engine), Bundle(), f, id)
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
