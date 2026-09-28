package com.tennis.scoremanager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tennis.scoremanager.MatchController
import com.tennis.scoremanager.ble.BandSettings
import com.tennis.scoremanager.ble.BatteryModel
import com.tennis.scoremanager.ble.LinkState
import com.tennis.scoremanager.model.Side
import java.util.Locale

/**
 * Impostazioni di un braccialetto: si scrivono nel braccialetto (che le salva) e la stima dell'autonomia
 * si aggiorna mentre si muovono i cursori, prima ancora di rilasciarli.
 */
@Composable
fun BandSettingsPanel(c: MatchController, side: Side) {
    val s = LocalStrings.current
    val bands by c.ble.bands.collectAsState()
    val batteries by c.bandBattery.collectAsState()
    val baseMa by c.bandBaseMa.collectAsState()
    val band = bands[side] ?: return
    if (band.state != LinkState.READY) {
        Text(s.bandSettingsNeedLink, color = TsmColors.TextDim, fontSize = 13.sp)
        return
    }
    val settings = band.settings ?: run {
        Text(s.bandFirmwareOld, color = TsmColors.Orange, fontSize = 13.sp)
        return
    }
    // Bozza locale: i cursori la cambiano subito, il braccialetto riceve il valore al rilascio.
    var draft by remember(band.address, settings) { mutableStateOf(settings) }
    fun commit(v: BandSettings) {
        draft = v
        if (v != settings) c.writeBandSettings(side, v)
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        NameField(settings.name) { commit(draft.copy(name = it)) }

        SliderRow(Icons.Filled.BrightnessMedium, s.brightness, "${draft.brightness}%", draft.brightness, 5..100, 5,
            onChange = { draft = draft.copy(brightness = it) }, onDone = { commit(draft) })

        Label(Icons.Filled.Timer, s.scoreTime)
        val times = listOf(0, 2, 3, 5, 8)
        Segmented(
            times.map { SegOption(if (it == 0) s.off else "$it s") },
            selected = times.indexOf(draft.pointSeconds).takeIf { it >= 0 } ?: times.indexOfFirst { it >= draft.pointSeconds }.coerceAtLeast(0),
            onSelect = { commit(draft.copy(pointSeconds = times[it])) },
        )
        Text(s.scoreTimeHint, color = TsmColors.TextDim, fontSize = 12.sp)

        SliderRow(Icons.AutoMirrored.Filled.VolumeUp, s.beeperVolume, if (draft.volume == 0) s.mute else "${draft.volume}%",
            draft.volume, 0..100, 10, onChange = { draft = draft.copy(volume = it) }, onDone = { commit(draft) })

        SwitchRow(Icons.Filled.ScreenRotation, s.flipDisplay, s.flipDisplayHint, draft.flip) { commit(draft.copy(flip = it)) }

        Label(Icons.Filled.PowerSettingsNew, s.autoOff)
        Picker(s.pairTimeout, duration(draft.pairTimeoutS), listOf(15, 30, 60, 120, 180, 300).map { it to duration(it) }) {
            commit(draft.copy(pairTimeoutS = it))
        }
        Picker(s.lostTimeout, duration(draft.lostTimeoutS), listOf(60, 120, 180, 300, 600).map { it to duration(it) }) {
            commit(draft.copy(lostTimeoutS = it))
        }
        Picker(s.idleTimeout, duration(draft.idleTimeoutMin * 60), listOf(10, 15, 30, 45, 60).map { it to duration(it * 60) }) {
            commit(draft.copy(idleTimeoutMin = it))
        }

        Estimate(BatteryModel.estimate(draft, baseMa[band.address]), batteries[side]?.takeIf { !it.charging }?.percent ?: band.battery)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PanelButton(s.identify, Icons.Filled.FlashOn, Modifier.weight(1f)) { c.identifyBand(side) }
            PanelButton(s.powerOff, Icons.Filled.PowerSettingsNew, Modifier.weight(1f), danger = true) { c.powerOffBand(side) }
        }
        val other = bands[side.other]
        PanelButton(s.copyToOther, Icons.Filled.ContentCopy, Modifier.fillMaxWidth(),
            enabled = other?.state == LinkState.READY && other.settings != null) { c.copyBandSettings(side) }
        if (settings.firmware.isNotEmpty()) Text(s.bandFirmware(settings.firmware), color = TsmColors.TextDim, fontSize = 11.sp)
    }
}

/** "30 s", "1 min", "1 min 30 s". */
private fun duration(seconds: Int): String = when {
    seconds < 60 -> "$seconds s"
    seconds % 60 == 0 -> "${seconds / 60} min"
    else -> "${seconds / 60} min ${seconds % 60} s"
}

private fun ma(v: Double): String = String.format(Locale.getDefault(), if (v >= 10) "%.0f" else "%.1f", v)

@Composable
private fun Estimate(est: com.tennis.scoremanager.ble.PowerEstimate, percent: Int?) {
    val s = LocalStrings.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TsmColors.Ball.copy(alpha = 0.10f)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.BatteryStd, null, tint = TsmColors.Ball, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(s.estimateFull(BatteryModel.formatHours(est.hoursFull)), color = TsmColors.TextMain, fontWeight = FontWeight.Bold)
        }
        if (percent != null) Text(s.estimateNow(BatteryModel.formatHours(est.hoursAt(percent)), percent), color = TsmColors.Ball, fontWeight = FontWeight.SemiBold)
        Text(s.estimateBreakdown(ma(est.totalMa), ma(est.baseMa), ma(est.displayMa), ma(est.soundMa)), color = TsmColors.TextDim, fontSize = 12.sp)
        Text(if (est.measured) s.estimateMeasured else s.estimateTheory, color = TsmColors.TextDim, fontSize = 12.sp)
    }
}

@Composable
private fun NameField(current: String, onSave: (String) -> Unit) {
    val s = LocalStrings.current
    val focus = LocalFocusManager.current
    var text by remember(current) { mutableStateOf(current) }
    val clean = BandSettings.cleanName(text)
    fun save() {
        focus.clearFocus()
        if (clean.isNotEmpty() && clean != current) onSave(clean)
    }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it.take(BandSettings.NAME_MAX) },
        label = { Text(s.bandName) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { save() }),
        trailingIcon = {
            if (clean.isNotEmpty() && clean != current) {
                IconButton(onClick = { save() }) { Icon(Icons.Filled.Check, s.save, tint = TsmColors.Ball) }
            }
        },
    )
}

@Composable
private fun Label(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = TsmColors.TextDim, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = TsmColors.TextMain, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SliderRow(
    icon: ImageVector,
    title: String,
    valueText: String,
    value: Int,
    range: IntRange,
    step: Int,
    onChange: (Int) -> Unit,
    onDone: () -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = TsmColors.TextDim, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(title, color = TsmColors.TextMain, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(valueText, color = TsmColors.Ball, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value.toFloat(),
            // a scatti di [step] senza i puntini delle tacche
            onValueChange = { onChange((Math.round(it / step) * step).coerceIn(range)) },
            onValueChangeFinished = onDone,
            valueRange = range.first.toFloat()..range.last.toFloat(),
            colors = SliderDefaults.colors(thumbColor = TsmColors.Ball, activeTrackColor = TsmColors.Ball),
            modifier = Modifier.height(36.dp),
        )
    }
}

@Composable
private fun PanelButton(text: String, icon: ImageVector, modifier: Modifier, enabled: Boolean = true, danger: Boolean = false, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(46.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (danger) TsmColors.Danger.copy(alpha = 0.18f) else TsmColors.SurfaceHigh,
            contentColor = if (danger) TsmColors.Danger else TsmColors.TextMain,
        ),
    ) {
        Icon(icon, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
