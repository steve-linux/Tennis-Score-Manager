// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ActivityNotFoundException
import android.text.format.DateFormat
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SportsTennis
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tennis.scoremanager.LiveMatch
import com.tennis.scoremanager.MatchController
import com.tennis.scoremanager.data.Reports
import com.tennis.scoremanager.data.Rematch
import java.util.Calendar

/** Riepilogo di una partita finita: "Rivincita" apre l'invito da mandare, il calendario e la partita pronta. */
@Composable
fun RematchCard(c: MatchController, m: LiveMatch) {
    val s = LocalStrings.current
    if (!m.record.finished) return
    var open by remember { mutableStateOf(false) }
    SectionCard(s.rematchTitle, Icons.Filled.Replay) {
        Text(s.rematchHint, color = TsmColors.TextDim, fontSize = 13.sp)
        GhostButton(s.rematchTitle, Icons.Filled.Replay, { open = true }, Modifier.fillMaxWidth())
    }
    if (open) RematchDialog(c, m) { open = false }
}

@Composable
private fun RematchDialog(c: MatchController, m: LiveMatch, onClose: () -> Unit) {
    val s = LocalStrings.current
    val context = LocalContext.current
    val lang = c.options.value.lang
    val rec = m.record
    var at by remember {
        mutableLongStateOf(Rematch.suggestedTime(rec.startedAt ?: rec.updatedAt, System.currentTimeMillis()))
    }
    var place by remember { mutableStateOf(c.rematchPlace(rec)) }
    var note by remember { mutableStateOf("") }
    val text = c.rematchText(m, at, place, note)
    fun pickDate() {
        val cal = Calendar.getInstance().apply { timeInMillis = at }
        DatePickerDialog(context, { _, y, mo, d ->
            at = Calendar.getInstance().apply { timeInMillis = at; set(y, mo, d) }.timeInMillis
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }
    fun pickTime() {
        val cal = Calendar.getInstance().apply { timeInMillis = at }
        TimePickerDialog(context, { _, h, min ->
            at = Calendar.getInstance().apply { timeInMillis = at; set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, min) }.timeInMillis
        }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), DateFormat.is24HourFormat(context)).show()
    }
    AlertDialog(
        onDismissRequest = onClose,
        icon = { androidx.compose.material3.Icon(Icons.Filled.Replay, null, tint = TsmColors.Ball) },
        title = { Text(s.rematchTitle) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(s.rematchWhen, color = TsmColors.TextDim, fontSize = 13.sp)
                GhostButton(Reports.date(at, lang), Icons.Filled.Event, { pickDate() }, Modifier.fillMaxWidth())
                GhostButton(Reports.time(at, lang), Icons.Filled.Schedule, { pickTime() }, Modifier.fillMaxWidth())
                OutlinedTextField(place, { place = it }, label = { Text(s.rematchPlace) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    note, { note = it.take(200) }, label = { Text(s.rematchNote) }, placeholder = { Text(s.rematchNoteHint, color = TsmColors.TextDim) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences), modifier = Modifier.fillMaxWidth(),
                )
                // Anteprima: quello che arriva a chi lo riceve.
                Text(s.rematchPreview, color = TsmColors.TextDim, fontSize = 13.sp)
                Text(
                    text, color = TsmColors.TextMain, fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(TsmColors.SurfaceHigh).padding(10.dp),
                )
                GhostButton(s.rematchSend, Icons.AutoMirrored.Filled.Send, { context.startActivity(c.rematchShareIntent(text)) }, Modifier.fillMaxWidth())
                GhostButton(s.rematchCalendar, Icons.Filled.CalendarMonth, {
                    try {
                        context.startActivity(c.rematchCalendarIntent(m, at, place, text))
                    } catch (_: ActivityNotFoundException) {
                        Toast.makeText(context, s.rematchNoCalendar, Toast.LENGTH_SHORT).show()
                    }
                }, Modifier.fillMaxWidth())
                GhostButton(s.rematchPrepare, Icons.Filled.SportsTennis, {
                    onClose()
                    c.prepareRematch(rec)
                }, Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(s.back) } },
    )
}
