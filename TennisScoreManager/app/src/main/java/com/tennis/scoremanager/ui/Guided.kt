// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tennis.scoremanager.HomeTab
import com.tennis.scoremanager.Screen

/** L'area di ogni schermata (il colore in alto): giocare, circolo, allenarsi, progressi, persone, tornei, impostazioni. */
fun areaOf(screen: Screen, tab: HomeTab): Color = when (screen) {
    Screen.HOME -> areaOf(tab)
    Screen.SETUP, Screen.OPTIONS, Screen.TOSS, Screen.START, Screen.MATCH, Screen.SUMMARY, Screen.TOURNAMENT -> TsmColors.AreaPlay
    Screen.HISTORY, Screen.PROGRESS, Screen.VIDEOS, Screen.RACKETS -> TsmColors.AreaProgress
    Screen.WORKOUT, Screen.STROKE_SESSIONS, Screen.DRILLS, Screen.ARENA -> TsmColors.AreaTrain
    Screen.DEVICES, Screen.VOICE, Screen.HELP, Screen.DATA, Screen.SETTINGS, Screen.PLAYERS -> TsmColors.AreaSettings
    Screen.STUDENTS, Screen.STUDENT, Screen.LESSONS, Screen.COACH_COURSE, Screen.MEMBERS, Screen.PAYMENTS, Screen.SCHOOL, Screen.COURSE -> TsmColors.AreaPeople
    Screen.BOOK_COURT, Screen.COURTS_PLAN, Screen.COURT_STATS, Screen.NOTICE, Screen.DESK -> TsmColors.AreaClub
    Screen.CLUB_TOURNAMENTS, Screen.CLUB_TOURNAMENT, Screen.TEAMS, Screen.TEAM, Screen.FIXTURE, Screen.LADDERS, Screen.LADDER -> TsmColors.AreaTournaments
}

/** Il colore di una scheda della prima schermata. */
fun areaOf(tab: HomeTab): Color = when (tab) {
    HomeTab.PLAY -> TsmColors.AreaPlay
    HomeTab.BOOKING, HomeTab.COURTS, HomeTab.TODAY -> TsmColors.AreaClub
    HomeTab.TRAIN -> TsmColors.AreaTrain
    HomeTab.PROGRESS -> TsmColors.AreaProgress
    HomeTab.STUDENTS, HomeTab.PEOPLE -> TsmColors.AreaPeople
    HomeTab.TOURNAMENTS -> TsmColors.AreaTournaments
    HomeTab.MORE -> TsmColors.AreaSettings
}

/**
 * Sotto-schede di una schermata lunga (la scheda del giocatore): una riga di etichette che scorre di lato se non ci
 * sta, così in tedesco o francese i nomi restano interi.
 */
@Composable
fun SubTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val area = LocalArea.current
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { i, label ->
            val on = i == selected
            Text(
                label, color = if (on) TsmColors.OnArea else TsmColors.TextMain, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, fontSize = 14.sp,
                maxLines = 1,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(if (on) area else TsmColors.SurfaceHigh).clickable { onSelect(i) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}

/** Una cosa da fare adesso, nel riquadro [TodoCard]. */
data class TodoItem(val icon: ImageVector, val title: String, val hint: String?, val onClick: () -> Unit)

/** In cima alla prima schermata: tutti gli avvisi in un riquadro solo, invece di tante righe sparse. */
@Composable
fun TodoCard(items: List<TodoItem>) {
    if (items.isEmpty()) return
    val s = LocalStrings.current
    StripeBox(TsmColors.AreaTodo) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.NotificationsActive, null, tint = TsmColors.AreaTodo)
            Text(s.todoTitle(items.size), color = TsmColors.TextMain, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(start = 10.dp))
        }
        for (it in items) Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TsmColors.SurfaceHigh).clickable(onClick = it.onClick).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(it.icon, null, tint = TsmColors.AreaTodo, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(it.title, color = TsmColors.TextMain, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                it.hint?.let { h -> Text(h, color = TsmColors.TextDim, fontSize = 12.sp) }
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = TsmColors.TextDim, modifier = Modifier.size(18.dp))
        }
    }
}

/** Un riquadro con la striscia colorata a sinistra: il colore dice di che area è. */
@Composable
fun StripeBox(color: Color, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(RoundedCornerShape(18.dp)).background(TsmColors.Surface)) {
        Box(Modifier.width(5.dp).fillMaxHeight().background(color))
        Column(Modifier.weight(1f).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

/**
 * Il segno del passo nelle procedure guidate (nuovo torneo, nuova prenotazione, collegare i braccialetti...): una
 * barretta per passo, colorata fin dove si è arrivati, e "Passo 2 di 3 · Chi gioca".
 */
@Composable
fun StepHeader(step: Int, titles: List<String>) {
    val s = LocalStrings.current
    val area = LocalArea.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (i in titles.indices) Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(if (i <= step) area else TsmColors.Outline))
        }
        Text(s.wzStep(step + 1, titles.size) + " · " + titles[step], color = area, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

/** Il tasto "Avanti" delle procedure guidate dentro un dialogo. */
@Composable
fun WizardNext(enabled: Boolean = true, onClick: () -> Unit) {
    androidx.compose.material3.TextButton(enabled = enabled, onClick = onClick) { Text(LocalStrings.current.next + " ›") }
}

/** Il tasto "Indietro" di un passo (al primo passo il dialogo usa il suo, che chiude). */
@Composable
fun WizardBack(onClick: () -> Unit) {
    androidx.compose.material3.TextButton(onClick = onClick) { Text("‹ " + LocalStrings.current.back) }
}

/**
 * Una pagina ancora vuota spiega cosa fare: l'icona dell'area, un titolo, i passi numerati e il pulsante per il primo.
 */
@Composable
fun EmptyGuide(icon: ImageVector, title: String, steps: List<String>, button: String? = null, onClick: () -> Unit = {}) {
    val area = LocalArea.current
    StripeBox(area) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(area), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = TsmColors.OnArea)
            }
            Text(title, color = TsmColors.TextMain, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(start = 12.dp))
        }
        steps.forEachIndexed { i, st ->
            Row {
                Text("${i + 1}.", color = area, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.width(22.dp))
                Text(st, color = TsmColors.TextMain, fontSize = 14.sp)
            }
        }
        if (button != null) {
            Spacer(Modifier.height(2.dp))
            BigButton(button, icon, onClick, Modifier.fillMaxWidth(), color = area, onColor = TsmColors.OnArea)
        }
    }
}

/**
 * Una riga che si apre e si chiude (le pagine con tante parti, come Dispositivi): titolo, come sta adesso e la
 * freccia. Il contenuto si vede solo da aperta.
 */
@Composable
fun ExpandRow(icon: ImageVector, title: String, summary: String?, open: Boolean, ok: Boolean? = null, onToggle: () -> Unit) {
    val area = LocalArea.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(if (open) TsmColors.SurfaceHigh else TsmColors.Surface).clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = area, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(title, color = TsmColors.TextMain, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            summary?.let {
                Text(it, fontSize = 13.sp, color = when (ok) { true -> TsmColors.Ok; false -> TsmColors.Orange; null -> TsmColors.TextDim })
            }
        }
        Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = TsmColors.TextDim)
    }
}

/**
 * Una di due scelte affiancate e uguali (per esempio "Un colpo" o "Tutta la lezione"): icona, titolo e una riga che dice
 * cosa fa; quella scelta [on] ha il colore dell'area.
 */
@Composable
fun ChoiceTile(icon: ImageVector, title: String, hint: String, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val area = LocalArea.current
    Column(
        modifier.fillMaxHeight().clip(RoundedCornerShape(16.dp)).background(if (on) area else TsmColors.SurfaceHigh).clickable(onClick = onClick).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, tint = if (on) TsmColors.OnArea else area, modifier = Modifier.size(26.dp))
        Text(title, color = if (on) TsmColors.OnArea else TsmColors.TextMain, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 18.sp)
        Text(hint, color = if (on) TsmColors.OnArea else TsmColors.TextDim, fontSize = 12.sp, lineHeight = 15.sp)
    }
}
