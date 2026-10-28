// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tennis.scoremanager.video.Framing

/** Colore di quanto si è lontani dal giusto: verde a posto, arancione poco, rosso da correggere. */
fun gradeColor(grade: Int): Color = when (grade) {
    0 -> TsmColors.Ok
    1 -> TsmColors.Orange
    else -> TsmColors.Danger
}

private fun deg(v: Float) = "%.1f°".format(v)

/** Nome dell'obiettivo [index] (0 = principale, poi i grandangoli dal meno al più largo). */
fun lensName(s: Strings, index: Int, count: Int): String = when {
    index == 0 -> s.lensMain
    count > 2 -> "${s.lensWide} $index"
    else -> s.lensWide
}

/**
 * Livella: inclinazione rispetto a quella consigliata ([target], null se non calcolata) e rotazione laterale, coi
 * consigli in parole. Si usa sull'anteprima del telefono-telecamera e nel riquadro Telecamera dell'arbitro.
 */
@Composable
fun LevelReadout(tilt: Float, roll: Float, target: Float?, modifier: Modifier = Modifier) {
    val s = LocalStrings.current
    val pitchErr = target?.let { tilt - it }
    val pg = pitchErr?.let { Framing.grade(it) } ?: 0
    val rg = Framing.grade(roll)
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val pitchHint = when {
            pitchErr == null -> null
            pg == 0 -> s.assistOk
            pitchErr < 0 -> s.assistAimLower(deg(-pitchErr))
            else -> s.assistAimHigher(deg(pitchErr))
        }
        Text(
            "${s.assistPitch} ${deg(tilt)}" + (target?.let { " → ${deg(it)}" } ?: "") + (pitchHint?.let { " · $it" } ?: ""),
            color = if (target == null) TsmColors.TextMain else gradeColor(pg), fontWeight = FontWeight.Bold, fontSize = 14.sp,
        )
        val rollHint = when {
            rg == 0 -> s.assistOk
            roll > 0 -> s.assistTurnCcw(deg(roll))
            else -> s.assistTurnCw(deg(-roll))
        }
        Text("${s.assistLevel} ${deg(roll)} · $rollHint", color = gradeColor(rg), fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

/**
 * L'orizzonte vero sull'anteprima: una linea che resta orizzontale nel mondo mentre il telefono gira, con due tacche
 * fisse ai lati dello schermo. Quando la linea passa sulle tacche il telefono è dritto.
 */
@Composable
fun HorizonOverlay(roll: Float, modifier: Modifier = Modifier) {
    val color = gradeColor(Framing.grade(roll))
    Canvas(modifier.fillMaxSize()) {
        val c = Offset(size.width / 2, size.height / 2)
        val half = size.width * 0.35f
        val tick = size.width * 0.06f
        drawLine(Color.White.copy(alpha = 0.7f), Offset(0f, c.y), Offset(tick, c.y), strokeWidth = 4f)
        drawLine(Color.White.copy(alpha = 0.7f), Offset(size.width - tick, c.y), Offset(size.width, c.y), strokeWidth = 4f)
        rotate(-roll, c) {
            drawLine(color, Offset(c.x - half, c.y), Offset(c.x + half, c.y), strokeWidth = 5f)
        }
    }
}

/**
 * Impostazioni dell'assistente: dove sta il telefono e su che campo. Mostra subito inclinazione consigliata,
 * se il campo ci sta con l'obiettivo in uso e quale conviene; [onUseLens] passa a quello consigliato.
 */
@Composable
fun FramingDialog(
    storage: com.tennis.scoremanager.data.Storage,
    lenses: List<Framing.Lens>,
    lensIndex: Int,
    levelOn: Boolean,
    canChangeLens: Boolean,
    onUseLens: (Int) -> Unit,
    onLevel: (Boolean) -> Unit,
    outlineOn: Boolean = false,
    onOutline: ((Boolean) -> Unit)? = null,
    onDone: () -> Unit,
) {
    val s = LocalStrings.current
    var dText by remember { mutableStateOf(trimNum(storage.framingDistance)) }
    var hText by remember { mutableStateOf(trimNum(storage.framingHeight)) }
    var court by remember { mutableStateOf(runCatching { Framing.Court.valueOf(storage.framingCourt) }.getOrDefault(Framing.Court.TENNIS_DOUBLES)) }
    fun dist(t: String) = t.replace(',', '.').toFloatOrNull()?.takeIf { it in 0.3f..60f }
    fun high(t: String) = t.replace(',', '.').toFloatOrNull()?.takeIf { it in 0.3f..20f }
    val d = dist(dText)
    val h = high(hText)
    AlertDialog(
        onDismissRequest = onDone,
        icon = { Icon(Icons.Filled.Straighten, null, tint = TsmColors.Ball) },
        title = { Text(s.assistTitle) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(s.assistHint, fontSize = 13.sp, color = TsmColors.TextDim)
                val num = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                OutlinedTextField(dText, { dText = it; dist(it)?.let { v -> storage.framingDistance = v } }, label = { Text(s.assistDistance) }, singleLine = true, keyboardOptions = num, isError = d == null, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(hText, { hText = it; high(it)?.let { v -> storage.framingHeight = v } }, label = { Text(s.assistHeight) }, singleLine = true, keyboardOptions = num, isError = h == null, modifier = Modifier.fillMaxWidth())
                Text(s.assistCourt, fontSize = 13.sp, color = TsmColors.TextDim)
                val courts = Framing.Court.entries
                Segmented(
                    courts.map { SegOption(when (it) { Framing.Court.TENNIS_SINGLES -> s.courtTennisSingles; Framing.Court.TENNIS_DOUBLES -> s.courtTennisDoubles; Framing.Court.PADEL -> s.courtPadel }) },
                    courts.indexOf(court),
                    { court = courts[it]; storage.framingCourt = court.name },
                    columns = 3,
                )
                if (d != null && h != null) {
                    val lens = lenses.getOrNull(lensIndex)
                    val p = Framing.plan(d, h, court, lens)
                    Text(s.assistTiltIs(deg(p.tilt)), color = TsmColors.TextMain, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    val name = lensName(s, lensIndex, lenses.size)
                    when {
                        lens == null -> Unit
                        p.fits -> Text(s.assistFitsWith(name), color = TsmColors.Ok, fontSize = 13.sp)
                        !p.fitsV -> Text(s.assistTooTall, color = TsmColors.Orange, fontSize = 13.sp)
                        else -> {
                            Text(s.assistCornersOut(name), color = TsmColors.Orange, fontSize = 13.sp)
                            Framing.minDistance(h, court, lens)?.let { m ->
                                Text(s.assistMinDistance(name, "%.1f m".format(m)), color = TsmColors.TextDim, fontSize = 13.sp)
                            }
                        }
                    }
                    // Il consigliato: il più stretto in cui ci sta tutto; se non è quello in uso, un tasto per passarci.
                    val best = Framing.bestLens(d, h, court, lenses)?.let { lenses.indexOf(it) } ?: -1
                    if (best >= 0 && best != lensIndex) {
                        GhostButton(s.assistUseLens(lensName(s, best, lenses.size)), Icons.Filled.CameraAlt, { onUseLens(best) }, Modifier.fillMaxWidth(), enabled = canChangeLens)
                        if (!canChangeLens) Text(s.cameraLensBusy, color = TsmColors.TextDim, fontSize = 12.sp)
                    }
                }
                Text(s.assistCenter, fontSize = 12.sp, color = TsmColors.TextDim)
                // Fase 2: la sagoma del campo da far combaciare con le linee vere (centratura compresa).
                onOutline?.let { set ->
                    GhostButton(if (outlineOn) s.outlineHide else s.outlineShow, Icons.Filled.GridOn, { set(!outlineOn); onDone() }, Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onLevel(!levelOn); onDone() }) { Text(if (levelOn) s.assistHideLevel else s.assistShowLevel) }
                TextButton(onClick = onDone) { Text(s.ok) }
            }
        },
    )
}

private fun trimNum(v: Float): String = if (v % 1f == 0f) v.toInt().toString() else "%.1f".format(v)

/**
 * Sagoma del campo sopra l'immagine 16:9 (anteprima della telecamera o fotogramma sul telefono dell'arbitro): le linee
 * come le vedrebbe l'obiettivo con questa taratura e l'inclinazione letta adesso dal sensore.
 */
@Composable
fun CourtOutline(calib: Framing.Calib, hFov: Float, vFov: Float, pitchDown: Float, roll: Float, modifier: Modifier = Modifier) {
    val lines = Framing.project(calib, Framing.Lens("", hFov, vFov), pitchDown, roll)
    Canvas(modifier.fillMaxSize()) {
        val stroke = Stroke(width = 2.5f * density)
        for (line in lines) {
            val path = Path()
            line.forEachIndexed { i, (x, y) ->
                if (i == 0) path.moveTo(x * size.width, y * size.height) else path.lineTo(x * size.width, y * size.height)
            }
            drawPath(path, TsmColors.Ball.copy(alpha = 0.85f), style = stroke)
        }
    }
}

/**
 * Cursori della taratura: [onChange] a ogni spostamento (il chiamante decide quanto spesso mandarla). In alto le
 * misure del posto, sotto le correzioni fini.
 */
@Composable
fun CalibrationPanel(calib: Framing.Calib, onChange: (Framing.Calib) -> Unit, modifier: Modifier = Modifier) {
    val s = LocalStrings.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(s.outlineHint, color = TsmColors.TextDim, fontSize = 12.sp)
        CalibSlider(s.calibDistance, "%.1f m".format(calib.distance), calib.distance, 0.5f..20f) { onChange(calib.copy(distance = it)) }
        CalibSlider(s.calibHeight, "%.1f m".format(calib.height), calib.height, 0.3f..8f) { onChange(calib.copy(height = it)) }
        CalibSlider(s.calibPan, deg(calib.pan), calib.pan, -25f..25f) { onChange(calib.copy(pan = it)) }
        CalibSlider(s.calibLateral, "%.1f m".format(calib.lateral), calib.lateral, -4f..4f) { onChange(calib.copy(lateral = it)) }
        CalibSlider(s.calibFov, "%d %%".format((calib.fovScale * 100).toInt()), calib.fovScale, 0.8f..1.25f) { onChange(calib.copy(fovScale = it)) }
        CalibSlider(s.calibPitch, deg(calib.pitchOffset), calib.pitchOffset, -6f..6f) { onChange(calib.copy(pitchOffset = it)) }
        TextButton(onClick = { onChange(calib.copy(pan = 0f, lateral = 0f, fovScale = 1f, pitchOffset = 0f)) }) { Text(s.outlineReset) }
    }
}

@Composable
private fun CalibSlider(label: String, value: String, v: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column {
        Row {
            Text(label, color = TsmColors.TextMain, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text(value, color = TsmColors.Ball, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = v.coerceIn(range), onValueChange = onChange, valueRange = range,
            colors = SliderDefaults.colors(thumbColor = TsmColors.Ball, activeTrackColor = TsmColors.Ball),
        )
    }
}
