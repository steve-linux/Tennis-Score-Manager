// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.video

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.media.MediaMetadataRetriever
import android.os.Build
import com.tennis.scoremanager.data.PoseFrame
import com.tennis.scoremanager.data.ServePose
import com.tennis.scoremanager.data.ServePoseResult
import com.tennis.scoremanager.data.TrainedStroke
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.abs
import kotlin.math.max

/**
 * Posizione del corpo con MoveNet SinglePose Thunder (17 punti), tutto sul telefono. Si usa dopo l'allenamento, clip
 * per clip: niente in diretta, così non pesa su batteria e registrazione.
 */
class PoseAnalyzer(context: Context) : Closeable {
    private val interpreter: Interpreter
    private val size: Int
    private val uint8: Boolean

    init {
        // Il modello scaricato a richiesta (PoseModel), mappato in memoria.
        val f = PoseModel.file(context)
        val model = FileInputStream(f).channel.use { it.map(FileChannel.MapMode.READ_ONLY, 0, it.size()) }
        interpreter = Interpreter(model, Interpreter.Options().setNumThreads(4))
        val input = interpreter.getInputTensor(0)
        size = input.shape()[1]
        uint8 = input.dataType() == DataType.UINT8
    }

    /**
     * I 17 punti in [bmp]: x e y in pixel dell'immagine, poi la sicurezza. [crop] è il quadrato (in pixel) da guardare:
     * il giocatore ingrandito si vede meglio; fuori dall'immagine è nero.
     */
    fun detect(bmp: Bitmap, crop: RectF, masks: List<RectF> = emptyList()): Array<FloatArray> {
        val input = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        Canvas(input).apply {
            drawColor(Color.BLACK)
            val m = Matrix().apply { setRectToRect(crop, RectF(0f, 0f, size.toFloat(), size.toFloat()), Matrix.ScaleToFit.FILL) }
            drawBitmap(bmp, m, Paint(Paint.FILTER_BITMAP_FLAG))
            // Le altre persone (in pixel di [bmp]) si coprono: così il modello non le scambia per chi serve.
            if (masks.isNotEmpty()) {
                val black = Paint().apply { color = Color.BLACK }
                for (r in masks) RectF(r).also { m.mapRect(it) }.let { drawRect(it, black) }
            }
        }
        val px = IntArray(size * size)
        input.getPixels(px, 0, size, 0, 0, size, size)
        input.recycle()
        val buf = ByteBuffer.allocateDirect(size * size * 3 * if (uint8) 1 else 4).order(ByteOrder.nativeOrder())
        for (p in px) {
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            if (uint8) {
                buf.put(r.toByte()); buf.put(g.toByte()); buf.put(b.toByte())
            } else {
                buf.putFloat(r.toFloat()); buf.putFloat(g.toFloat()); buf.putFloat(b.toFloat())
            }
        }
        buf.rewind()
        val out = Array(1) { Array(1) { Array(17) { FloatArray(3) } } }
        interpreter.run(buf, out)
        // MoveNet dà y, x, sicurezza nel quadrato d'ingresso (0-1).
        return Array(17) { i ->
            val (y, x, s) = out[0][0][i].let { Triple(it[0], it[1], it[2]) }
            floatArrayOf(crop.left + x * crop.width(), crop.top + y * crop.height(), s)
        }
    }

    override fun close() = interpreter.close()

    companion object {
        /** Quadrato intorno ai punti sicuri, largo 1,6 volte il corpo; null se non se ne vedono abbastanza. */
        fun cropAround(kp: Array<FloatArray>, w: Int, h: Int): RectF? {
            val good = kp.filter { it[2] >= ServePose.MIN_SCORE }
            if (good.size < 6) return null
            val x0 = good.minOf { it[0] }; val x1 = good.maxOf { it[0] }
            val y0 = good.minOf { it[1] }; val y1 = good.maxOf { it[1] }
            val side = max(x1 - x0, y1 - y0) * 1.6f
            val cx = (x0 + x1) / 2; val cy = (y0 + y1) / 2
            return RectF(cx - side / 2, cy - side / 2, cx + side / 2, cy + side / 2)
        }

        /** Centro orizzontale e larghezza dei punti sicuri (pixel), per riconoscere la stessa persona da un fotogramma all'altro. */
        fun spread(kp: Array<FloatArray>): Pair<Float, Float>? {
            val good = kp.filter { it[2] >= ServePose.MIN_SCORE }
            if (good.size < 6) return null
            val x0 = good.minOf { it[0] }; val x1 = good.maxOf { it[0] }
            return (x0 + x1) / 2 to (x1 - x0)
        }

        /**
         * Centro del busto (spalle e anche) e larghezza delle spalle, in pixel: non si sposta quando un braccio si alza,
         * quindi distingue bene due persone vicine. null se il busto non si vede.
         */
        fun torso(kp: Array<FloatArray>): Pair<Float, Float>? {
            val pts = listOf(ServePose.L_SHOULDER, ServePose.R_SHOULDER, ServePose.L_HIP, ServePose.R_HIP).filter { kp[it][2] >= ServePose.MIN_SCORE }
            if (pts.size < 2) return null
            val cx = pts.map { kp[it][0] }.average().toFloat()
            val sw = if (kp[ServePose.L_SHOULDER][2] >= ServePose.MIN_SCORE && kp[ServePose.R_SHOULDER][2] >= ServePose.MIN_SCORE) {
                abs(kp[ServePose.L_SHOULDER][0] - kp[ServePose.R_SHOULDER][0])
            } else 0f
            return cx to sw
        }

        /** Quanto un braccio è alzato sopra le spalle, in lunghezze del busto (chi serve arriva ben sopra 1). */
        fun armRaise(kp: Array<FloatArray>): Float {
            fun y(i: Int) = kp[i][1].takeIf { kp[i][2] >= ServePose.MIN_SCORE }
            val sh = listOfNotNull(y(ServePose.L_SHOULDER), y(ServePose.R_SHOULDER)).minOrNull() ?: return 0f
            val hip = listOfNotNull(y(ServePose.L_HIP), y(ServePose.R_HIP)).maxOrNull() ?: return 0f
            val wrist = listOfNotNull(y(ServePose.L_WRIST), y(ServePose.R_WRIST)).minOrNull() ?: return 0f
            val torso = (hip - sh).takeIf { it > 1f } ?: return 0f
            return (sh - wrist) / torso
        }

        /** Tutta l'immagine in un quadrato, con bande nere sopra e sotto. */
        fun full(w: Int, h: Int): RectF {
            val side = max(w, h).toFloat()
            return RectF((w - side) / 2, (h - side) / 2, (w + side) / 2, (h + side) / 2)
        }
    }
}

/**
 * Analisi di un servizio salvato: fotogrammi tra poco prima e poco dopo l'istante [at] dalla clip [dir] (pezzi da 4 s
 * con le loro ore d'inizio in [ReplayRecorder.STARTS]), punti del corpo, misure, e tre immagini con lo scheletro.
 */
object ServeClipAnalysis {
    const val RESULT = "pose.json"
    const val TROPHY = "key_trophy.jpg"
    const val KNEE = "key_knee.jpg"
    const val CONTACT = "key_contact.jpg"
    /** Dritto, rovescio e volée: preparazione e finale (l'impatto è [CONTACT]). */
    const val PREP = "key_prep.jpg"
    const val FINISH = "key_finish.jpg"
    /** Finestra intorno al servizio: col servizio riconosciuto si sa bene dov'è, senza si guarda più largo. */
    private const val STEP_MS = 66L
    /** Fotogrammi letti per volta: 8 in 1080p sono circa 33 MB. */
    private const val CHUNK = 8
    /** Seconda lettura intorno all'impatto di dritto, rovescio e volée: un fotogramma ogni 33 ms per ±0,2 s. */
    private const val REFINE_MS = 200L
    private const val REFINE_STEP_MS = 33L
    private const val REFINE_FRAMES = 7
    /** Senza colpo riconosciuto: dove ci si aspetta il colpo rispetto ad [at] (3 s prima del tasto). */
    private const val UNDETECTED_ANCHOR_MS = 700L

    fun analyze(
        pose: PoseAnalyzer, dir: File, at: Long, detected: Boolean, stroke: TrainedStroke = TrainedStroke.SERVE, onProgress: (Float) -> Unit = {},
    ): ServePoseResult {
        val parts = dir.listFiles { f -> f.extension == "mp4" }.orEmpty().sortedBy { it.name }
        val starts = runCatching { File(dir, ReplayRecorder.STARTS).readLines().mapNotNull { it.trim().toLongOrNull() } }.getOrDefault(emptyList())
        if (parts.isEmpty() || starts.size != parts.size) return ServePoseResult(emptyList(), null)
        val serve = stroke == TrainedStroke.SERVE
        // Il braccialetto dà l'ora del colpo circa 0,4 s dopo l'impatto: per gli altri colpi si guarda anche dopo, per il finale.
        val (from, to) = when {
            !detected -> at - 2_500 to at + 2_500
            serve -> at - 2_000 to at + 700
            else -> at - 2_200 to at + 900
        }
        val times = (from..to step STEP_MS).toList()
        val frames = ArrayList<PoseFrame>()
        var crop: RectF? = null
        val retrievers = HashMap<File, MediaMetadataRetriever>()
        // Il fotogramma all'ora [t] del telefono, dal pezzo che la contiene.
        fun frameAt(t: Long): Bitmap? {
            val i = starts.indexOfLast { it <= t }.takeIf { it >= 0 } ?: return null
            val r = retrievers.getOrPut(parts[i]) { MediaMetadataRetriever().apply { setDataSource(parts[i].absolutePath) } }
            val us = (t - starts[i]) * 1000
            return if (Build.VERSION.SDK_INT >= 27) r.getScaledFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST, 960, 540)
            else r.getFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST)
        }
        // Ricognizione: con più persone nell'inquadratura MoveNet ne segue una a caso. Si cercano le persone in tre
        // quadrati (sinistra, centro, destra) su alcuni fotogrammi, e si sceglie chi alza di più il braccio: chi serve.
        // Il ritaglio della ricognizione è in frazioni dell'altezza: i fotogrammi letti in fila sono più grandi.
        val sc = scout(pose, times, ::frameAt, stroke == TrainedStroke.SERVE)
        val start = sc.crop
        val people = sc.people
        var center: Pair<Float, Float>? = null
        var jumps = 0
        var done = 0
        fun process(bmp: Bitmap, t: Long) {
            val w = bmp.width; val h = bmp.height
            if (crop == null && center == null && start != null) {
                crop = RectF(start.left * h, start.top * h, start.right * h, start.bottom * h)
            }
            val masks = sc.others.map { o -> RectF((o.cx - o.half) * h, 0f, (o.cx + o.half) * h, h.toFloat()) }
            var kp = pose.detect(bmp, crop ?: PoseAnalyzer.full(w, h), masks)
            var sp = PoseAnalyzer.spread(kp)
            // Persa col ritaglio: si riprova un po' più largo intorno a dov'era, non su tutta l'immagine (dove
            // potrebbe prendere un'altra persona).
            if (sp == null && crop != null) {
                val c = crop!!
                val wider = RectF(c.centerX() - c.width() * 0.8f, c.centerY() - c.height() * 0.8f, c.centerX() + c.width() * 0.8f, c.centerY() + c.height() * 0.8f)
                kp = pose.detect(bmp, wider, masks)
                sp = PoseAnalyzer.spread(kp)
            }
            // Un salto del busto più largo delle spalle è un'altra persona: il fotogramma non vale, il ritaglio resta.
            val tr = if (sp != null) PoseAnalyzer.torso(kp) else null
            val prev = center
            val far = tr != null && prev != null && abs(tr.first - prev.first) > maxOf(prev.second, h * 0.03f) * 1.2f
            // Tre fotogrammi di fila nello stesso posto nuovo: si è spostato davvero (le altre persone sono coperte).
            jumps = if (far) jumps + 1 else 0
            val jumped = far && jumps < 3
            if (sp != null && !jumped) {
                crop = PoseAnalyzer.cropAround(kp, w, h)
                if (tr != null) center = tr
            }
            val scores = if (jumped) List(17) { 0f } else kp.map { it[2] }
            frames += PoseFrame(t - at, kp.map { it[0] / h }, kp.map { it[1] / h }, scores)
            onProgress(++done / (times.size + REFINE_FRAMES).toFloat())
        }
        // I fotogrammi alle ore [wantedTimes] (ore del telefono), pezzo per pezzo e letti in fila (Android 9+): chiederli uno
        // per uno farebbe ripartire ogni volta la decodifica dall'inizio del pezzo, dieci volte più lento.
        fun readInOrder(wantedTimes: List<Long>, each: (Bitmap, Long) -> Unit) {
            for ((pi, ts) in wantedTimes.groupBy { t -> starts.indexOfLast { it <= t } }.filterKeys { it >= 0 }.toSortedMap()) {
                val r = retrievers.getOrPut(parts[pi]) { MediaMetadataRetriever().apply { setDataSource(parts[pi].absolutePath) } }
                val count = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)?.toIntOrNull() ?: 0
                val durMs = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                if (Build.VERSION.SDK_INT < 28 || count <= 0 || durMs <= 0) {
                    for (t in ts) runCatching { frameAt(t) }.getOrNull()?.let { each(it, t); it.recycle() }
                    continue
                }
                val wanted = ts.groupBy { t -> ((t - starts[pi]) * count / durMs).toInt().coerceIn(0, count - 1) }
                val lo = wanted.keys.min()
                val hi = wanted.keys.max()
                val params = MediaMetadataRetriever.BitmapParams().apply { preferredConfig = Bitmap.Config.RGB_565 }
                var k = lo
                while (k <= hi) {
                    val n = minOf(CHUNK, hi - k + 1)
                    val list = runCatching { r.getFramesAtIndex(k, n, params) }.getOrDefault(emptyList())
                    list.forEachIndexed { j, bmp ->
                        wanted[k + j]?.forEach { t -> each(bmp, t) }
                        bmp.recycle()
                    }
                    if (list.isEmpty()) break
                    k += n
                }
            }
        }
        try {
            readInOrder(times, ::process)
            frames.sortBy { it.t }
            val metrics = if (serve) ServePose.metrics(frames) else null
            val volley = stroke == TrainedStroke.VOLLEY
            // Senza colpo riconosciuto [at] è 3 s prima del tasto: il colpo di solito è un po' dopo.
            fun ground() = ServePose.groundMetrics(
                frames, if (detected) ServePose.DETECTED_CONTACT else null, if (detected) 0 else UNDETECTED_ANCHOR_MS, volley,
            )
            var ground = if (serve) null else ground()
            // Intorno all'impatto un fotogramma ogni 66 ms è poco (il polso fa quasi un metro): si leggono anche quelli
            // in mezzo e si rifà il conto, cercando l'impatto lì vicino.
            if (ground != null) {
                val near = ground.contactT
                val extra = (near - REFINE_MS..near + REFINE_MS step REFINE_STEP_MS).filter { t -> frames.none { abs(it.t - t) < REFINE_STEP_MS / 2 } }
                readInOrder(extra.map { it + at }) { bmp, tAbs ->
                    val t = tAbs - at
                    val h = bmp.height
                    // Ritaglio intorno al corpo del fotogramma letto più vicino.
                    val ref = frames.minBy { abs(it.t - t) }
                    val kpRef = Array(17) { i -> floatArrayOf(ref.x[i] * h, ref.y[i] * h, ref.s[i]) }
                    val masks = sc.others.map { o -> RectF((o.cx - o.half) * h, 0f, (o.cx + o.half) * h, h.toFloat()) }
                    val c = PoseAnalyzer.cropAround(kpRef, bmp.width, h) ?: PoseAnalyzer.full(bmp.width, h)
                    val kp = pose.detect(bmp, c, masks)
                    frames += PoseFrame(t, kp.map { it[0] / h }, kp.map { it[1] / h }, kp.map { it[2] })
                    onProgress(++done / (times.size + REFINE_FRAMES).toFloat())
                }
                frames.sortBy { it.t }
                ground = ServePose.groundMetrics(frames, near - REFINE_MS..near + REFINE_MS, near, volley) ?: ground
            }
            onProgress(1f)
            val moments = when {
                metrics != null -> listOf(TROPHY to metrics.trophyT, KNEE to metrics.kneeT, CONTACT to metrics.contactT)
                ground != null -> listOf(PREP to ground.prepT, CONTACT to ground.contactT, FINISH to ground.finishT)
                else -> emptyList()
            }
            // Le tre immagini dei momenti chiave, con lo scheletro: si riprendono dal video (non si tengono tutte in memoria).
            run {
                for ((name, t) in moments) {
                    val bmp = runCatching { frameAt(t + at) }.getOrNull() ?: continue
                    val f = frames.first { it.t == t }
                    runCatching { File(dir, name).outputStream().use { skeleton(bmp, f).compress(Bitmap.CompressFormat.JPEG, 85, it) } }
                    bmp.recycle()
                }
            }
            return ServePoseResult(frames, metrics, people, ground)
        } finally {
            retrievers.values.forEach { runCatching { it.release() } }
        }
    }

    /**
     * Ricognizione su 8 fotogrammi della finestra: ritaglio iniziale intorno a chi serve (null = tutta l'immagine) e
     * quante persone diverse si sono viste.
     */
    /** Un'altra persona vista nella ricognizione: centro e mezza larghezza, in frazioni dell'altezza dell'immagine. */
    data class Other(val cx: Float, val half: Float)

    private data class Scout(val crop: RectF?, val people: Int, val others: List<Other>, val serverCx: Float?)

    private fun scout(pose: PoseAnalyzer, times: List<Long>, frameAt: (Long) -> Bitmap?, serve: Boolean = true): Scout {
        data class Seen(val cx: Float, val kp: Array<FloatArray>, val raise: Float, val width: Float, val frame: Int)
        val seen = ArrayList<Seen>()
        var w = 0; var h = 0
        for (k in 0 until 8) {
            val t = times[(times.size - 1) * k / 7]
            val bmp = runCatching { frameAt(t) }.getOrNull() ?: continue
            w = bmp.width; h = bmp.height
            val side = minOf(w, h).toFloat()
            val xs = if (w > h) listOf(0f, (w - side) / 2, w - side) else listOf(0f)
            for (x in xs) {
                val kp = pose.detect(bmp, RectF(x, (h - side) / 2, x + side, (h + side) / 2))
                if (kp.count { it[2] >= ServePose.MIN_SCORE } < 8) continue
                val tr = PoseAnalyzer.torso(kp) ?: continue
                // Lo stesso corpo visto da due quadrati vicini finisce nello stesso gruppo, qui sotto.
                seen += Seen(tr.first, kp, PoseAnalyzer.armRaise(kp), maxOf(tr.second, h * 0.03f), k)
            }
            bmp.recycle()
        }
        if (seen.isEmpty() || w == 0) return Scout(null, 0, emptyList(), null)
        // Persone: gruppi di avvistamenti col centro del busto vicino (meno di una larghezza di spalle e mezza).
        val groups = ArrayList<MutableList<Seen>>()
        for (s in seen.sortedBy { it.cx }) {
            val g = groups.lastOrNull()
            if (g != null && s.cx - g.last().cx < maxOf(g.last().width, s.width) * 1.5f) g += s else groups += mutableListOf(s)
        }
        // Chi colpisce: col servizio chi alza di più il braccio; con gli altri colpi chi muove di più il polso tra un
        // fotogramma e l'altro della ricognizione (lo swing), in larghezze di spalle.
        fun activity(g: List<Seen>): Float {
            var best = 0f
            for (w in listOf(ServePose.L_WRIST, ServePose.R_WRIST)) {
                val pts = g.sortedBy { it.frame }.filter { it.kp[w][2] >= ServePose.MIN_SCORE }
                for (i in 1 until pts.size) {
                    val dx = pts[i].kp[w][0] - pts[i - 1].kp[w][0]; val dy = pts[i].kp[w][1] - pts[i - 1].kp[w][1]
                    best = maxOf(best, kotlin.math.sqrt(dx * dx + dy * dy) / maxOf(pts[i].width, 1f))
                }
            }
            return best
        }
        val server = if (serve) groups.maxBy { g -> g.maxOf { it.raise } } else groups.maxBy { activity(it) }
        val best = server.maxBy { it.raise }
        val serverCx = server.map { it.cx }.average().toFloat()
        // Le altre persone: dove stanno di solito (mediana) e quanto sono larghe, senza mai coprire chi serve.
        val others = groups.filter { it !== server }.map { g ->
            val cx = g.map { it.cx }.sorted()[g.size / 2]
            val half = g.map { it.width }.sorted()[g.size / 2] * 1.2f
            Other(cx / h, minOf(half, abs(cx - serverCx) * 0.6f) / h)
        }
        val c = PoseAnalyzer.cropAround(best.kp, w, h)
        return Scout(c?.let { RectF(it.left / h, it.top / h, it.right / h, it.bottom / h) }, groups.size, others, serverCx / h)
    }

    /** Il fotogramma con lo scheletro disegnato sopra (punti sicuri e ossa fra punti sicuri). */
    private fun skeleton(src: Bitmap, f: PoseFrame): Bitmap {
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val c = Canvas(out)
        val h = out.height.toFloat()
        val bone = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(198, 244, 50); strokeWidth = h / 120; strokeCap = Paint.Cap.ROUND }
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        for ((a, b) in ServePose.BONES) {
            if (f.s[a] < ServePose.MIN_SCORE || f.s[b] < ServePose.MIN_SCORE) continue
            c.drawLine(f.x[a] * h, f.y[a] * h, f.x[b] * h, f.y[b] * h, bone)
        }
        for (i in 0 until 17) if (f.s[i] >= ServePose.MIN_SCORE) c.drawCircle(f.x[i] * h, f.y[i] * h, h / 90, dot)
        return out
    }

    fun keyImage(dir: File, name: String): Bitmap? = File(dir, name).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.absolutePath) }
}
