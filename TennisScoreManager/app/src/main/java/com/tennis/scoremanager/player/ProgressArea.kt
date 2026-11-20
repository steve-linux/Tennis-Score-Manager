// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later
package com.tennis.scoremanager.player

import android.app.Application
import com.tennis.scoremanager.LiveMatch
import com.tennis.scoremanager.Screen
import com.tennis.scoremanager.data.PlayerStats
import com.tennis.scoremanager.data.Progress
import com.tennis.scoremanager.data.ProgressItem
import com.tennis.scoremanager.data.ProgressItems
import com.tennis.scoremanager.data.ProgressPdf
import com.tennis.scoremanager.data.Reports
import com.tennis.scoremanager.data.Storage
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * I progressi di chi gioca: i giocatori dello storico, il rating TSM, l'elenco da spuntare e il PDF dei progressi,
 * gli obiettivi. Quello che serve del resto dell'app è in [PlayerHost].
 */
class ProgressArea(
    host: PlayerHost,
    private val app: Application,
    private val storage: Storage,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
) : PlayerHost by host {
    /** Pagina 1 › Progressi: documenti stampabili, uno per gli allenamenti e uno per le partite di un giocatore. */
    fun openProgress() {
        refreshHistory()
        refreshWorkouts()
        nav.push(Screen.PROGRESS)
    }

    internal fun played() = history.value.map { PlayerStats.Played(it.record, it.state.winner) }

    /** Giocatori dello storico (chiave, nome), per scegliere di chi fare il documento delle partite. */
    /** Giocatori dello storico (chi è, nome): col nome del database se è un giocatore del database. */
    fun progressPlayers(): List<Pair<String, String>> = PlayerStats.players(played()).map { it.key to (playerById(it.key)?.name ?: it.name) }

    /** Rating TSM di uno sport, dalle partite finite dello storico (le amichevoli non contano). */
    fun ratingTable(sport: com.tennis.scoremanager.data.Sport): com.tennis.scoremanager.data.Rating.Table =
        com.tennis.scoremanager.data.Rating.compute(
            history.value.mapNotNull { com.tennis.scoremanager.data.Rating.gameOf(it.record, it.state.sets, it.state.winner) }, sport,
        )

    /** Rating dello sport di [m], con [m] dentro anche se lo storico non l'ha ancora ricaricata (appena finita). */
    fun ratingTableWith(m: LiveMatch): com.tennis.scoremanager.data.Rating.Table =
        com.tennis.scoremanager.data.Rating.compute(
            (history.value.filter { it.record.id != m.record.id } + m)
                .mapNotNull { com.tennis.scoremanager.data.Rating.gameOf(it.record, it.state.sets, it.state.winner) },
            m.record.setup.sport,
        )

    /** Sport con almeno una partita che conta per il rating, il più giocato per primo. */
    fun ratingSports(): List<com.tennis.scoremanager.data.Sport> =
        history.value.mapNotNull { com.tennis.scoremanager.data.Rating.gameOf(it.record, it.state.sets, it.state.winner)?.sport }
            .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }

    /** Data corta: nelle colonne del documento quella lunga ("martedì 20 ottobre 2026") non ci sta. */
    internal fun shortDate(): (Long) -> String {
        val fmt = java.text.SimpleDateFormat("dd/MM/yy", options.value.lang.locale)
        return { fmt.format(java.util.Date(it)) }
    }

    /** L'elenco da spuntare dei Progressi: allenamenti ([playerKey] null) o partite del giocatore, dal più recente. */
    fun progressItems(playerKey: String?): List<ProgressItem> {
        val s = strings
        val fmt = java.text.SimpleDateFormat("EEE dd/MM/yy HH:mm", options.value.lang.locale)
        val date: (Long) -> String = { fmt.format(java.util.Date(it)).replaceFirstChar { c -> c.uppercase() } }
        return if (playerKey == null) ProgressItems.workouts(workouts.value, date)
        else ProgressItems.matches(PlayerStats.lines(played(), playerKey), playerKey, s.progressWon, s.progressLost, date)
    }

    /** [playerKey] null = allenamenti; [ids] = quelli spuntati (null = tutti). */
    fun progressReport(playerKey: String?, ids: Set<String>? = null): Progress.Report {
        val s = strings
        val date = shortDate()
        return if (playerKey == null) {
            Progress.workouts(workouts.value.filter { ids == null || it.id in ids }, s, date)
        } else {
            val name = PlayerStats.players(played()).firstOrNull { it.key == playerKey }?.name ?: playerKey
            Progress.matches(PlayerStats.lines(played(), playerKey).filter { ids == null || it.rec.id in ids }, name, s, date)
        }
    }

    /** Il PDF dei progressi nella cartella di condivisione; null se non si è potuto scrivere. */
    fun progressPdf(playerKey: String?, ids: Set<String>? = null): File? {
        val r = progressReport(playerKey, ids)
        val s = strings
        val dir = File(app.cacheDir, "share").apply { mkdirs() }
        val name = (if (playerKey == null) s.progressWorkouts else "${s.progressMatches} ${r.title.substringAfter(": ")}")
            .replace(Regex("[\\/:*?\"<>|]"), "_")
        val file = File(dir, "$name.pdf")
        return runCatching {
            file.outputStream().use { ProgressPdf.write(r, s, "Tennis Score Manager · ${Reports.date(System.currentTimeMillis(), options.value.lang)}", it) }
            file
        }.getOrNull()
    }

    val goals = MutableStateFlow(storage.loadGoals())

    /** Le partite finite di chi usa l'app (Altro › Tu), dalla più vecchia; vuoto se non si sa chi è. */
    fun myLines(): List<com.tennis.scoremanager.data.PlayerStats.MatchLine> =
        myKey().takeIf { it.isNotEmpty() }?.let { com.tennis.scoremanager.data.PlayerStats.lines(played(), it) }.orEmpty()

    fun goalStatus(g: com.tennis.scoremanager.data.Goal): com.tennis.scoremanager.data.Goals.Status =
        com.tennis.scoremanager.data.Goals.status(g, myLines(), workouts.value.map { it.startedAt }, System.currentTimeMillis())

    internal fun putGoals(list: List<com.tennis.scoremanager.data.Goal>) {
        goals.value = list
        scope.launch(io) { storage.saveGoals(list) }
    }

    /** Un obiettivo nuovo; se è già raggiunto, lo si sa subito senza avviso. */
    fun addGoal(kind: com.tennis.scoremanager.data.GoalKind, target: Double, window: Int) {
        val now = System.currentTimeMillis()
        val g = com.tennis.scoremanager.data.Goal(com.tennis.scoremanager.data.Goals.newId(), kind, target, window, now)
        val reached = goalStatus(g).reached
        putGoals(goals.value + g.copy(reachedAt = if (reached) now else null, seen = reached))
    }

    fun deleteGoal(id: String) = putGoals(goals.value.filter { it.id != id })

    fun goalSeen(id: String) = putGoals(goals.value.map { if (it.id == id) it.copy(seen = true) else it })

    /** Dopo una partita o un allenamento: gli obiettivi raggiunti adesso prendono la data (e l'avviso in Gioca). */
    internal fun checkGoals() {
        val list = goals.value
        if (list.none { it.reachedAt == null }) return
        val next = com.tennis.scoremanager.data.Goals.markReached(list, myLines(), workouts.value.map { it.startedAt }, System.currentTimeMillis())
        if (next != list) putGoals(next)
    }
}
