// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.video

import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.tennis.scoremanager.TsmApp
import com.tennis.scoremanager.data.ServeAttempt
import com.tennis.scoremanager.data.ServeMetrics
import com.tennis.scoremanager.data.ServePose
import com.tennis.scoremanager.data.ServePoseResult
import com.tennis.scoremanager.data.ServeSession
import com.tennis.scoremanager.data.TrainedStroke
import com.tennis.scoremanager.ui.strokeLabel
import com.tennis.scoremanager.ui.BigButton
import com.tennis.scoremanager.ui.GhostButton
import com.tennis.scoremanager.ui.LocalStrings
import com.tennis.scoremanager.ui.SectionCard
import com.tennis.scoremanager.ui.Strings
import com.tennis.scoremanager.ui.TsmColors
import com.tennis.scoremanager.ui.TsmTheme
import com.tennis.scoremanager.ui.stringsFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Analisi del movimento di una sessione di allenamento al servizio: per ogni servizio con la clip, i punti del corpo
 * (MoveNet, sul telefono), le misure dei tre momenti chiave con le immagini, e il confronto tra dentro e fuori.
 */
class ServeAnalysisActivity : ComponentActivity() {
    private val controller get() = (application as TsmApp).controller
    private val s: Strings get() = stringsFor(controller.options.value.lang)
    private val json = Json { ignoreUnknownKeys = true }

    private val session = MutableStateFlow<ServeSession?>(null)
    /** Risultati per clip (cartella), letti dai file o appena calcolati. */
    private val results = MutableStateFlow<Map<String, ServePoseResult>>(emptyMap())
    /** Avanzamento: servizio in analisi (da 1), quanti in tutto e frazione del servizio. */
    private val progress = MutableStateFlow<Triple<Int, Int, Float>?>(null)
    private var job: Job? = null
    /** Modello sul telefono, avanzamento del download (null = fermo) ed eventuale errore. */
    private val modelReady = MutableStateFlow(false)
    private val modelProgress = MutableStateFlow<Float?>(null)
    private val modelError = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getStringExtra(EXTRA_ID).orEmpty()
        modelReady.value = PoseModel.installed(this)
        lifecycleScope.launch {
            val sess = withContext(Dispatchers.IO) { controller.storage.loadServeSessions().firstOrNull { it.id == id } }
            session.value = sess
            if (sess != null) results.value = withContext(Dispatchers.IO) { loadResults(sess) }
        }
        setContent { TsmTheme { androidx.compose.runtime.CompositionLocalProvider(LocalStrings provides s) { Content() } } }
    }

    private fun dir(sess: ServeSession, a: ServeAttempt): File? = a.clip?.let { File(controller.storage.serveClipsDir(sess.id), it) }

    private fun loadResults(sess: ServeSession): Map<String, ServePoseResult> = sess.serves.mapNotNull { a ->
        val f = dir(sess, a)?.let { File(it, ServeClipAnalysis.RESULT) }?.takeIf { it.exists() } ?: return@mapNotNull null
        runCatching { a.clip!! to json.decodeFromString(ServePoseResult.serializer(), f.readText()) }.getOrNull()
    }.toMap()

    /** Scarica il modello (una volta sola) e lo controlla; poi si può analizzare. */
    private fun downloadModel() {
        if (modelProgress.value != null) return
        modelError.value = null
        modelProgress.value = 0f
        lifecycleScope.launch {
            val r = withContext(Dispatchers.IO) { PoseModel.download(this@ServeAnalysisActivity) { modelProgress.value = it } }
            modelProgress.value = null
            when (r) {
                PoseModel.Result.Ok -> modelReady.value = true
                PoseModel.Result.Network -> modelError.value = s.poseModelFailed
                PoseModel.Result.BadFile -> modelError.value = s.poseModelBad
            }
        }
    }

    /** Cancella i risultati di questa sessione (le clip restano) e rianalizza tutto. */
    private fun redoAll() {
        val sess = session.value ?: return
        if (job?.isActive == true) return
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                for (a in sess.serves) {
                    val d = dir(sess, a) ?: continue
                    for (name in listOf(
                        ServeClipAnalysis.RESULT, ServeClipAnalysis.TROPHY, ServeClipAnalysis.KNEE, ServeClipAnalysis.CONTACT, ServeClipAnalysis.PREP, ServeClipAnalysis.FINISH,
                    )) File(d, name).delete()
                }
            }
            results.value = emptyMap()
            analyzeAll()
        }
    }

    /** Analizza i servizi con la clip che non hanno ancora il risultato, uno alla volta. */
    private fun analyzeAll() {
        val sess = session.value ?: return
        if (job?.isActive == true) return
        val todo = sess.serves.filter { it.clip != null && it.clip !in results.value.keys }
        if (todo.isEmpty()) return
        job = lifecycleScope.launch {
            withContext(Dispatchers.Default) {
                runCatching {
                    PoseAnalyzer(this@ServeAnalysisActivity).use { pose ->
                        for ((n, a) in todo.withIndex()) {
                            val d = dir(sess, a) ?: continue
                            progress.value = Triple(n + 1, todo.size, 0f)
                            val r = ServeClipAnalysis.analyze(pose, d, a.at, a.detected, sess.stroke) { f -> progress.value = Triple(n + 1, todo.size, f) }
                            runCatching { File(d, ServeClipAnalysis.RESULT).writeText(json.encodeToString(ServePoseResult.serializer(), r)) }
                            results.value = results.value + (a.clip!! to r)
                        }
                    }
                }.onFailure { Log.w("Analisi servizio", "analisi non riuscita", it) }
            }
            progress.value = null
        }
    }

    @Composable
    private fun Content() {
        val str = s
        val sess by session.collectAsState()
        val res by results.collectAsState()
        val prog by progress.collectAsState()
        var open by remember { mutableStateOf<String?>(null) }
        Column(
            Modifier.fillMaxSize().background(TsmColors.Background).systemBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(str.poseTitle, color = TsmColors.TextMain, fontWeight = FontWeight.Bold, fontSize = 24.sp)
            Text(if (session.collectAsState().value?.stroke?.let { it != TrainedStroke.SERVE } == true) str.poseHintGround else str.poseHint, color = TsmColors.TextDim, fontSize = 13.sp)
            val current = sess ?: return@Column
            val withClip = current.serves.filter { it.clip != null }
            val p = prog
            val ready by modelReady.collectAsState()
            val dl by modelProgress.collectAsState()
            val err by modelError.collectAsState()
            if (!PoseModel.supported()) {
                Text(str.poseUnsupported, color = TsmColors.Orange)
            } else if (!ready) {
                // Il modello non è nell'app: si scarica qui, una volta sola, solo se lo si vuole.
                SectionCard(str.poseModelTitle, Icons.Filled.Download) {
                    Text(str.poseModelHint, color = TsmColors.TextDim, fontSize = 13.sp)
                    val d = dl
                    if (d != null) {
                        Text(str.poseModelDownloading((d * 100).toInt()), color = TsmColors.TextMain)
                        LinearProgressIndicator(progress = { d }, modifier = Modifier.fillMaxWidth(), color = TsmColors.Ball)
                    } else {
                        BigButton(str.poseModelDownload, Icons.Filled.Download, { downloadModel() }, Modifier.fillMaxWidth())
                    }
                    err?.let { Text(it, color = TsmColors.Orange, fontSize = 13.sp) }
                }
            } else if (p != null) {
                Text(if (current.stroke == TrainedStroke.SERVE) str.poseAnalyzing(p.first, p.second) else str.poseAnalyzingShot(p.first, p.second), color = TsmColors.TextMain)
                LinearProgressIndicator(progress = { (p.first - 1 + p.third) / p.second }, modifier = Modifier.fillMaxWidth(), color = TsmColors.Ball)
            } else if (withClip.any { it.clip !in res.keys }) {
                BigButton(if (current.stroke == TrainedStroke.SERVE) str.poseAnalyzeAll else str.poseAnalyzeShots, Icons.Filled.Insights, { analyzeAll() }, Modifier.fillMaxWidth())
            } else if (withClip.isNotEmpty()) {
                Text(str.poseDone, color = TsmColors.Ok)
                // Dopo un aggiornamento dell'app l'analisi può venire meglio: si rifà da capo.
                GhostButton(str.poseRedo, Icons.Filled.Insights, { redoAll() }, Modifier.fillMaxWidth())
            }
            Comparison(current, res)
            current.serves.forEachIndexed { i, a ->
                ServeRow(current, i, a, a.clip?.let { res[it] }, open == a.clip) { open = if (open == a.clip) null else a.clip }
            }
            GhostButton(str.back, Icons.AutoMirrored.Filled.ArrowBack, { finish() }, Modifier.fillMaxWidth())
            if (ready && p == null) {
                androidx.compose.material3.TextButton(onClick = { PoseModel.delete(this@ServeAnalysisActivity); modelReady.value = false }) {
                    Text(str.poseModelDelete, color = TsmColors.TextDim)
                }
            }
        }
    }

    /** Quello che si mostra di un'analisi: misure, riassunto, affidabilità e le tre immagini (etichetta, file). */
    private data class Shown(val reliable: Boolean, val brief: String, val rows: List<Pair<String, String>>, val images: List<Pair<String, String>>)

    private fun shown(stroke: TrainedStroke, r: ServePoseResult): Shown? {
        val str = s
        if (stroke == TrainedStroke.SERVE) {
            val m = r.metrics ?: return null
            return Shown(
                m.reliable, "${str.poseKneeMetric} ${deg(m.kneeMinDeg)} · ${str.poseElbowMetric} ${deg(m.elbowContactDeg)}",
                listOf(
                    str.poseKneeMetric to deg(m.kneeMinDeg),
                    str.poseElbowMetric to deg(m.elbowContactDeg),
                    str.poseHeightMetric to str.poseHeightValue("%.2f".format(m.contactHeight)),
                    str.poseTossMetric to deg(m.tossArmDeg),
                    str.poseTiltMetric to deg(m.shoulderTiltDeg),
                    str.poseTimingMetric to "${m.trophyToContactMs} ms",
                ),
                listOf(str.poseTrophy to ServeClipAnalysis.TROPHY, str.poseKnee to ServeClipAnalysis.KNEE, str.poseContact to ServeClipAnalysis.CONTACT),
            )
        }
        val g = r.ground ?: return null
        return Shown(
            g.reliable, "${str.poseInFrontMetric} ${"%.2f".format(g.contactInFront)} · ${str.poseElbowMetric} ${deg(g.elbowContactDeg)}",
            listOf(
                str.poseKneeMetric to deg(g.kneeMinDeg),
                str.poseElbowMetric to deg(g.elbowContactDeg),
                str.poseHeightMetric to str.poseHeightValue("%.2f".format(g.contactHeight)),
                str.poseInFrontMetric to str.poseHeightValue("%.2f".format(g.contactInFront)),
                str.poseBackswingMetric to str.poseHeightValue("%.2f".format(g.backswing)),
                str.poseFinishMetric to str.poseHeightValue("%.2f".format(g.finishHeight)),
                str.poseTimingGround to "${g.prepToContactMs} ms",
            ),
            listOf(str.posePrep to ServeClipAnalysis.PREP, str.poseContact to ServeClipAnalysis.CONTACT, str.poseFinish to ServeClipAnalysis.FINISH),
        )
    }

    /** Medie dei colpi dentro e di quelli fuori, misura per misura (solo quelli con misure affidabili). */
    @Composable
    private fun Comparison(sess: ServeSession, res: Map<String, ServePoseResult>) {
        val str = s
        fun of(inPlay: Boolean) = sess.serves.filter { it.inPlay == inPlay }.mapNotNull { a -> a.clip?.let { res[it] } }
        val rows: List<Triple<String, String, String>>
        val nIn: Int
        val nOut: Int
        if (sess.stroke == TrainedStroke.SERVE) {
            val ins = ServePose.mean(of(true).mapNotNull { it.metrics }) ?: return
            val outs = ServePose.mean(of(false).mapNotNull { it.metrics }) ?: return
            nIn = ins.n; nOut = outs.n
            rows = listOf(
                Triple(str.poseKneeMetric, deg(ins.kneeMinDeg), deg(outs.kneeMinDeg)),
                Triple(str.poseElbowMetric, deg(ins.elbowContactDeg), deg(outs.elbowContactDeg)),
                Triple(str.poseHeightMetric, "%.2f".format(ins.contactHeight), "%.2f".format(outs.contactHeight)),
                Triple(str.poseTossMetric, deg(ins.tossArmDeg), deg(outs.tossArmDeg)),
                Triple(str.poseTiltMetric, deg(ins.shoulderTiltDeg), deg(outs.shoulderTiltDeg)),
                Triple(str.poseTimingMetric, "${ins.trophyToContactMs} ms", "${outs.trophyToContactMs} ms"),
            )
        } else {
            val ins = ServePose.groundMean(of(true).mapNotNull { it.ground }) ?: return
            val outs = ServePose.groundMean(of(false).mapNotNull { it.ground }) ?: return
            nIn = ins.n; nOut = outs.n
            rows = listOf(
                Triple(str.poseKneeMetric, deg(ins.kneeMinDeg), deg(outs.kneeMinDeg)),
                Triple(str.poseElbowMetric, deg(ins.elbowContactDeg), deg(outs.elbowContactDeg)),
                Triple(str.poseHeightMetric, "%.2f".format(ins.contactHeight), "%.2f".format(outs.contactHeight)),
                Triple(str.poseInFrontMetric, "%.2f".format(ins.contactInFront), "%.2f".format(outs.contactInFront)),
                Triple(str.poseBackswingMetric, "%.2f".format(ins.backswing), "%.2f".format(outs.backswing)),
                Triple(str.poseFinishMetric, "%.2f".format(ins.finishHeight), "%.2f".format(outs.finishHeight)),
                Triple(str.poseTimingGround, "${ins.prepToContactMs} ms", "${outs.prepToContactMs} ms"),
            )
        }
        SectionCard(str.poseInVsOut, Icons.Filled.AccessibilityNew) {
            Row(Modifier.fillMaxWidth()) {
                Text("", modifier = Modifier.weight(2f))
                Text("${str.serveIn} ($nIn)", color = TsmColors.Ok, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text("${str.serveOut} ($nOut)", color = TsmColors.Danger, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1f))
            }
            for ((label, a, b) in rows) {
                Row(Modifier.fillMaxWidth()) {
                    Text(label, color = TsmColors.TextMain, fontSize = 13.sp, modifier = Modifier.weight(2f))
                    Text(a, color = TsmColors.TextMain, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text(b, color = TsmColors.TextMain, fontSize = 13.sp, modifier = Modifier.weight(1f))
                }
            }
        }
    }

    /** Un colpo: esito e misure in breve; aperto, le tre immagini con lo scheletro e tutte le misure. */
    @Composable
    private fun ServeRow(sess: ServeSession, index: Int, a: ServeAttempt, r: ServePoseResult?, expanded: Boolean, onToggle: () -> Unit) {
        val str = s
        val v = r?.let { shown(sess.stroke, it) }
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(TsmColors.Surface).clickable(enabled = r != null) { onToggle() }.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(Modifier.fillMaxWidth()) {
                val title = if (sess.stroke == TrainedStroke.SERVE) str.poseServe(index + 1) else "${strokeLabel(str, sess.stroke)} ${index + 1}"
                Text(title, color = TsmColors.TextMain, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(if (a.inPlay) str.serveIn else str.serveOut, color = if (a.inPlay) TsmColors.Ok else TsmColors.Danger, fontWeight = FontWeight.Bold)
            }
            when {
                a.clip == null -> Text(str.poseNoClip, color = TsmColors.TextDim, fontSize = 13.sp)
                r == null -> Unit
                v == null -> Text(str.poseNoBody, color = TsmColors.Orange, fontSize = 13.sp)
                else -> {
                    if (!v.reliable) Text(str.poseUnreliable, color = TsmColors.Orange, fontSize = 13.sp)
                    if (r.people > 1) Text(str.poseManyPeople(r.people), color = TsmColors.Orange, fontSize = 13.sp)
                    Text(v.brief, color = TsmColors.TextDim, fontSize = 13.sp)
                    if (expanded) Details(sess, a, v)
                }
            }
        }
    }

    @Composable
    private fun Details(sess: ServeSession, a: ServeAttempt, v: Shown) {
        val str = s
        val d = dir(sess, a) ?: return
        val images = remember(d) { v.images.map { (label, name) -> label to ServeClipAnalysis.keyImage(d, name) } }
        // Il video della clip con lo scheletro sopra, nella moviola (rallentatore, fotogramma per fotogramma, zoom).
        GhostButton(str.poseWatch, Icons.Filled.PlayCircle, {
            val parts = d.listFiles { f -> f.extension == "mp4" }.orEmpty().sortedBy { it.name }
            startActivity(
                android.content.Intent(this, ReplayActivity::class.java)
                    .putExtra(ReplayActivity.EXTRA_FILES, parts.map { it.absolutePath }.toTypedArray())
                    .putExtra(ReplayActivity.EXTRA_POSE_DIR, d.absolutePath)
                    .putExtra(ReplayActivity.EXTRA_POSE_AT, a.at),
            )
        }, Modifier.fillMaxWidth())
        Text(str.poseZoomHint, color = TsmColors.TextDim, fontSize = 12.sp)
        var zoomed by remember { mutableStateOf<Bitmap?>(null) }
        for ((label, bmp) in images) {
            Text(label, color = TsmColors.TextMain, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            bmp?.let { b -> KeyImage(b) { zoomed = b } }
        }
        zoomed?.let { ZoomDialog(it) { zoomed = null } }
        for ((label, value) in v.rows) {
            Row(Modifier.fillMaxWidth()) {
                Text(label, color = TsmColors.TextMain, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text(value, color = TsmColors.Ball, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }

    @Composable
    private fun KeyImage(bmp: Bitmap, onClick: () -> Unit) {
        Image(
            bmp.asImageBitmap(), null, contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().aspectRatio(bmp.width / bmp.height.toFloat()).clip(RoundedCornerShape(10.dp)).clickable { onClick() },
        )
    }

    /** Immagine a tutto schermo: due dita per ingrandire (fino a 6×) e spostare, doppio tocco per tornare normale. */
    @Composable
    private fun ZoomDialog(bmp: Bitmap, onClose: () -> Unit) {
        var zoom by remember { mutableStateOf(1f) }
        var pan by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
        androidx.compose.ui.window.Dialog(onDismissRequest = onClose, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
            androidx.compose.foundation.layout.Box(
                Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)
                    .pointerInput(Unit) {
                        detectTransformGestures { _, move, scale, _ ->
                            zoom = (zoom * scale).coerceIn(1f, 6f)
                            val maxX = size.width * (zoom - 1) / 2
                            val maxY = size.height * (zoom - 1) / 2
                            pan = androidx.compose.ui.geometry.Offset((pan.x + move.x).coerceIn(-maxX, maxX), (pan.y + move.y).coerceIn(-maxY, maxY))
                        }
                    }
                    .pointerInput(Unit) { detectTapGestures(onDoubleTap = { zoom = 1f; pan = androidx.compose.ui.geometry.Offset.Zero }, onTap = { if (zoom == 1f) onClose() }) },
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) {
                Image(
                    bmp.asImageBitmap(), null, contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().aspectRatio(bmp.width / bmp.height.toFloat())
                        .graphicsLayer { scaleX = zoom; scaleY = zoom; translationX = pan.x; translationY = pan.y },
                )
            }
        }
    }

    private fun deg(v: Float) = "%.0f°".format(v)

    override fun onDestroy() {
        job?.cancel()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_ID = "session"
    }
}
