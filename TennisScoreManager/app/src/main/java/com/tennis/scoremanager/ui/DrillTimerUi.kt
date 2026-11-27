// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tennis.scoremanager.MatchController
import com.tennis.scoremanager.data.DrillLibrary
import com.tennis.scoremanager.data.DrillTimer
import com.tennis.scoremanager.voice.Seg
import kotlinx.coroutines.delay

/**
 * Il timer a voce di un esercizio senza colpi da contare, a tutto schermo: il tempo che manca in grande, la fase
 * (preparati, lavoro, recupero) col suo colore e il giro. All'inizio la voce dice nome e obiettivo, poi "via", il
 * recupero, gli ultimi dieci secondi dei lavori lunghi e il conto alla rovescia. Lo schermo resta acceso.
 */
@Composable
fun DrillTimerDialog(c: MatchController, item: DrillLibrary.Item, t: DrillLibrary.Text, onDone: () -> Unit) {
    val s = LocalStrings.current
    val plan = item.timer ?: return
    val view = LocalView.current
    val audio = c.options.collectAsState().value.audio
    var sec by remember { mutableIntStateOf(0) }
    var paused by remember { mutableStateOf(false) }
    fun say(text: String) {
        if (c.options.value.audio) c.announcer.announce(listOf(Seg.Say(text)), force = true)
    }
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false; c.announcer.stop() }
    }
    LaunchedEffect(Unit) { say(listOf(t.name, t.goal).joinToString(" ") { it.trimEnd('.') + "." }) }
    // Un secondo alla volta (in pausa si ferma): a ogni secondo la voce dice quello che capita.
    LaunchedEffect(paused) {
        while (!paused && sec < plan.total) {
            delay(1000)
            sec++
            for (cue in DrillTimer.cues(plan, sec)) say(
                when (cue) {
                    is DrillTimer.Cue.Go -> if (cue.last && cue.rounds > 1) s.dtSayLast else s.dtSayGo(cue.round, cue.rounds)
                    is DrillTimer.Cue.Rest -> s.dtSayRest(cue.seconds)
                    is DrillTimer.Cue.Count -> cue.n.toString()
                    DrillTimer.Cue.TenLeft -> s.dtSayTen
                    DrillTimer.Cue.Done -> s.dtSayDone
                },
            )
        }
    }
    val st = DrillTimer.state(plan, sec)
    val (label, color) = when (st.kind) {
        DrillTimer.Kind.PREP -> s.dtPrep to TsmColors.Orange
        DrillTimer.Kind.WORK -> s.dtWork to TsmColors.Ok
        DrillTimer.Kind.REST -> s.dtRest to TsmColors.Cyan
        DrillTimer.Kind.DONE -> s.dtDone to TsmColors.Ball
    }
    Dialog(onDismissRequest = onDone, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxSize().background(TsmColors.Background).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(t.name, color = TsmColors.TextMain, fontWeight = FontWeight.Bold, fontSize = 22.sp, textAlign = TextAlign.Center)
            Text(s.dtPlan(plan.rounds, plan.work, plan.rest), color = TsmColors.TextDim, fontSize = 14.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.weight(1f))
            Text(label.uppercase(), color = color, fontWeight = FontWeight.Black, fontSize = 40.sp)
            if (st.kind != DrillTimer.Kind.DONE) {
                Text("%d:%02d".format(st.left / 60, st.left % 60), color = color, fontWeight = FontWeight.Black, fontSize = 120.sp)
                if (st.kind != DrillTimer.Kind.PREP) Text(s.dtRound(st.round, plan.rounds), color = TsmColors.TextMain, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.weight(1f))
            if (!audio) Text(s.dtVoiceOff, color = TsmColors.TextDim, fontSize = 13.sp, textAlign = TextAlign.Center)
            if (st.kind == DrillTimer.Kind.DONE) {
                BigButton(s.back, Icons.AutoMirrored.Filled.ArrowBack, onDone, Modifier.fillMaxWidth(), color = LocalArea.current, onColor = TsmColors.OnArea)
            } else {
                BigButton(
                    if (paused) s.dtResume else s.dtPause, if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    { paused = !paused; if (paused) c.announcer.stop() }, Modifier.fillMaxWidth(), color = LocalArea.current, onColor = TsmColors.OnArea,
                )
                GhostButton(s.dtStop, Icons.Filled.Stop, onDone, Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
