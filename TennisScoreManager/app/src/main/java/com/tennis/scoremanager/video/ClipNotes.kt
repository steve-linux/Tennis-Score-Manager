// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.video

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Path
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tennis.scoremanager.ui.Strings
import com.tennis.scoremanager.ui.TsmColors
import java.io.File
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Note su una clip: un fotogramma con frecce, cerchi e segni a mano ("nota_<ora>.png") e, se c'è, la nota vocale
 * con lo stesso nome ("nota_<ora>.m4a"). Stanno nella cartella della clip, accanto al video, e restano anche con
 * "Solo i video" (si cancellano solo gli mp4).
 */
object ClipNotes {
    data class Note(val image: File, val voice: File?, val at: Long)

    fun list(dir: File): List<Note> = dir.listFiles { f -> f.name.startsWith("nota_") && f.extension == "png" }.orEmpty()
        .map { png -> Note(png, File(dir, png.nameWithoutExtension + ".m4a").takeIf { it.exists() }, png.nameWithoutExtension.removePrefix("nota_").toLongOrNull() ?: png.lastModified()) }
        .sortedByDescending { it.at }

    fun delete(n: Note) { n.image.delete(); n.voice?.delete() }

    /** Immagine piccola per l'elenco (non serve intera). */
    fun thumbnail(f: File, maxWidth: Int = 900): Bitmap? = runCatching {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.absolutePath, o)
        var sample = 1
        while (o.outWidth / (sample * 2) >= maxWidth) sample *= 2
        BitmapFactory.decodeFile(f.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()

    /** Condivide la nota (immagine ed eventuale voce): copie nella cartella "share" che il FileProvider dà. */
    fun share(ctx: Context, n: Note) {
        val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
        val files = listOfNotNull(n.image, n.voice).map { f -> File(dir, f.name).also { f.copyTo(it, overwrite = true) } }
        val uris = ArrayList(files.map { androidx.core.content.FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", it) })
        val send = if (uris.size == 1) Intent(Intent.ACTION_SEND).apply { type = "image/png"; putExtra(Intent.EXTRA_STREAM, uris[0]) }
        else Intent(Intent.ACTION_SEND_MULTIPLE).apply { type = "*/*"; putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris) }
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(send, null))
    }

    /** I segni, in frazioni della larghezza e dell'altezza dell'immagine (così valgono a ogni dimensione). */
    sealed class Mark(val color: Int) {
        class Arrow(color: Int, val a: Offset, val b: Offset) : Mark(color)
        class Ring(color: Int, val center: Offset, val edge: Offset) : Mark(color)
        class Free(color: Int, val points: List<Offset>) : Mark(color)
    }

    /** L'immagine coi segni, alla risoluzione del fotogramma. */
    fun render(frame: Bitmap, marks: List<Mark>): Bitmap {
        val out = frame.copy(Bitmap.Config.ARGB_8888, true)
        val c = android.graphics.Canvas(out)
        val w = out.width.toFloat(); val h = out.height.toFloat()
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = h / 90; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
        fun px(o: Offset) = Offset(o.x * w, o.y * h)
        for (m in marks) {
            p.color = m.color
            when (m) {
                is Mark.Arrow -> {
                    val a = px(m.a); val b = px(m.b)
                    c.drawLine(a.x, a.y, b.x, b.y, p)
                    for (seg in arrowHead(a, b, h / 25)) c.drawLine(seg[0], seg[1], seg[2], seg[3], p)
                }
                is Mark.Ring -> { val o = px(m.center); val e = px(m.edge); c.drawCircle(o.x, o.y, hypot(e.x - o.x, e.y - o.y), p) }
                is Mark.Free -> if (m.points.size > 1) {
                    val path = Path()
                    m.points.map(::px).forEachIndexed { i, o -> if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y) }
                    c.drawPath(path, p)
                }
            }
        }
        return out
    }

    /** Le due alette della freccia che arriva in [b]. */
    private fun arrowHead(a: Offset, b: Offset, len: Float): List<FloatArray> {
        val ang = atan2(b.y - a.y, b.x - a.x)
        return listOf(0.5f, -0.5f).map { d ->
            floatArrayOf(b.x, b.y, b.x - len * cos(ang + d), b.y - len * sin(ang + d))
        }
    }

    fun drawMarks(scope: DrawScope, marks: List<Mark>) = with(scope) {
        val w = size.width; val h = size.height
        val sw = h / 90
        fun px(o: Offset) = Offset(o.x * w, o.y * h)
        for (m in marks) {
            val col = Color(m.color)
            when (m) {
                is Mark.Arrow -> {
                    val a = px(m.a); val b = px(m.b)
                    drawLine(col, a, b, sw, StrokeCap.Round)
                    for (seg in arrowHead(a, b, h / 25)) drawLine(col, Offset(seg[0], seg[1]), Offset(seg[2], seg[3]), sw, StrokeCap.Round)
                }
                is Mark.Ring -> { val o = px(m.center); val e = px(m.edge); drawCircle(col, hypot(e.x - o.x, e.y - o.y), o, style = Stroke(sw)) }
                is Mark.Free -> m.points.map(::px).zipWithNext { p, q -> drawLine(col, p, q, sw, StrokeCap.Round) }
            }
        }
    }
}

private enum class Tool { ARROW, RING, FREE }

/**
 * Annotare un fotogramma: frecce, cerchi e segni a mano in tre colori, la nota vocale, poi Salva (nella cartella della
 * clip) o Condividi. [askMic] chiede il permesso del microfono e risponde se c'è.
 */
@Composable
fun AnnotateScreen(frame: Bitmap, dir: File, s: Strings, askMic: ((Boolean) -> Unit) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val stem = remember { "nota_${System.currentTimeMillis()}" }
    val voice = remember { File(dir, "$stem.m4a") }
    val marks = remember { mutableStateListOf<ClipNotes.Mark>() }
    var tool by remember { mutableStateOf(Tool.ARROW) }
    val colors = listOf(TsmColors.Ball, TsmColors.Danger, Color.White)
    var color by remember { mutableStateOf(colors[0]) }
    var draft by remember { mutableStateOf<ClipNotes.Mark?>(null) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var hasVoice by remember { mutableStateOf(false) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var saved by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    fun stopRec() { recorder?.let { r -> runCatching { r.stop() }.onFailure { voice.delete() }; r.release() }; recorder = null; hasVoice = voice.exists() && voice.length() > 0 }
    fun startRec() {
        runCatching {
            @Suppress("DEPRECATION")
            val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(64_000)
            r.setAudioSamplingRate(44_100)
            r.setOutputFile(voice.absolutePath)
            r.prepare(); r.start()
            recorder = r
        }.onFailure { voice.delete(); message = s.anNoMic }
    }
    fun save(): ClipNotes.Note? {
        stopRec()
        val png = File(dir, "$stem.png")
        val ok = runCatching { png.outputStream().use { ClipNotes.render(frame, marks).compress(Bitmap.CompressFormat.PNG, 100, it) } }.isSuccess
        if (!ok) return null
        saved = true
        return ClipNotes.Note(png, voice.takeIf { it.exists() }, System.currentTimeMillis())
    }
    // Chiusa senza salvare: la voce registrata non resta da sola.
    DisposableEffect(Unit) { onDispose { stopRec(); player?.release(); if (!saved) voice.delete() } }

    Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(message ?: s.anHint, color = TsmColors.TextDim, fontSize = 12.sp, modifier = Modifier.weight(1f))
            NoteBtn(Icons.Filled.Close, null, false) { onClose() }
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.aspectRatio(frame.width / frame.height.toFloat())) {
                Image(frame.asImageBitmap(), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                Canvas(
                    Modifier.fillMaxSize().pointerInput(tool, color) {
                        fun norm(o: Offset) = Offset((o.x / size.width).coerceIn(0f, 1f), (o.y / size.height).coerceIn(0f, 1f))
                        var start = Offset.Zero
                        val pts = ArrayList<Offset>()
                        detectDragGestures(
                            onDragStart = { o -> start = norm(o); pts.clear(); pts += start },
                            onDragEnd = { draft?.let { marks += it }; draft = null },
                            onDragCancel = { draft = null },
                        ) { change, _ ->
                            val p = norm(change.position)
                            val c = color.toArgb()
                            draft = when (tool) {
                                Tool.ARROW -> ClipNotes.Mark.Arrow(c, start, p)
                                Tool.RING -> ClipNotes.Mark.Ring(c, start, p)
                                Tool.FREE -> { pts += p; ClipNotes.Mark.Free(c, pts.toList()) }
                            }
                        }
                    },
                ) { ClipNotes.drawMarks(this, marks + listOfNotNull(draft)) }
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            NoteBtn(Icons.Filled.NorthEast, s.anArrow, tool == Tool.ARROW) { tool = Tool.ARROW }
            NoteBtn(Icons.Filled.Circle, s.anCircle, tool == Tool.RING) { tool = Tool.RING }
            NoteBtn(Icons.Filled.Draw, s.anDraw, tool == Tool.FREE) { tool = Tool.FREE }
            for (col in colors) Box(
                Modifier.size(32.dp).clip(CircleShape).background(col)
                    .border(3.dp, if (col == color) TsmColors.Orange else Color.Transparent, CircleShape).clickable { color = col },
            )
            NoteBtn(Icons.AutoMirrored.Filled.Undo, s.anUndo, false) { if (marks.isNotEmpty()) marks.removeAt(marks.lastIndex) }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (recorder != null) NoteBtn(Icons.Filled.Stop, s.anVoiceStop, true) { stopRec() }
            else NoteBtn(Icons.Filled.Mic, s.anVoice, false) { player?.release(); player = null; askMic { ok -> if (ok) startRec() else message = s.anNoMic } }
            if (hasVoice && recorder == null) NoteBtn(Icons.Filled.PlayArrow, s.anVoicePlay, false) {
                player?.release()
                player = runCatching { MediaPlayer().apply { setDataSource(voice.absolutePath); prepare(); start() } }.getOrNull()
            }
            NoteBtn(Icons.Filled.Save, s.save, false) {
                if (save() != null) { android.widget.Toast.makeText(ctx, s.anSaved, android.widget.Toast.LENGTH_SHORT).show(); onClose() }
            }
            NoteBtn(Icons.Filled.Share, s.share, false) { save()?.let { ClipNotes.share(ctx, it); onClose() } }
        }
    }
}

@Composable
private fun NoteBtn(icon: ImageVector?, label: String?, on: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(10.dp), color = if (on) TsmColors.Orange else TsmColors.SurfaceHigh) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            icon?.let { Icon(it, null, tint = if (on) TsmColors.OnOrange else TsmColors.TextMain) }
            label?.let { Text(if (icon != null) " $it" else it, color = if (on) TsmColors.OnOrange else TsmColors.TextMain, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1) }
        }
    }
}
