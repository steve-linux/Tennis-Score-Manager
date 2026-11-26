// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later
package com.tennis.scoremanager.player

import android.app.Application
import android.content.ClipData
import android.content.Intent
import androidx.core.content.FileProvider
import com.tennis.scoremanager.Screen
import com.tennis.scoremanager.data.Feel
import com.tennis.scoremanager.data.RacketUse
import com.tennis.scoremanager.data.Reports
import com.tennis.scoremanager.data.Storage
import com.tennis.scoremanager.data.WorkoutRecord
import com.tennis.scoremanager.data.WorkoutReport
import com.tennis.scoremanager.data.WorkoutStats
import com.tennis.scoremanager.model.Side
import com.tennis.scoremanager.service.MatchService
import com.tennis.scoremanager.workout.WorkoutSession
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * L'allenamento con la fascia cardio, senza partita: avvio con la fascia e la racchetta proposta, fine e salvataggio,
 * riepilogo con sensazioni e racchetta, condivisione in CSV o PDF. L'elenco degli allenamenti salvati ([workouts]) e
 * le letture della fascia restano a MatchController, che passa i battiti a [workout]. Il resto è in [PlayerHost].
 */
class WorkoutArea(
    host: PlayerHost,
    private val app: Application,
    private val storage: Storage,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
) : PlayerHost by host {
    /** Allenamento con la fascia, senza partita (schermata a parte, pulsante in pagina 1). */
    val workout = WorkoutSession(scope) { rec -> onWorkoutFinished(rec) }
    /** Allenamento aperto nel riepilogo (null = schermata dell'allenamento in corso e dell'elenco). */
    val workoutDetail = MutableStateFlow<WorkoutRecord?>(null)
    internal var workoutSide = com.tennis.scoremanager.data.Wearer.P1
    /** Racchetta dell'allenamento in corso, presa all'avvio. */
    internal var workoutUse: RacketUse? = null


    fun openWorkouts() {
        workoutDetail.value = null
        refreshWorkouts()
        nav.push(Screen.WORKOUT)
    }

    /** La fascia dell'allenamento: quella del Giocatore 1 o l'unica scelta. false se non ce n'è nessuna. */
    fun startWorkout(): Boolean {
        val straps = heart.straps.value
        val side = com.tennis.scoremanager.data.Wearer.entries.firstOrNull { straps[it] != null } ?: return false
        workoutSide = side
        // Ti alleni tu: età, peso e sesso dalla tua scheda, se ci sono.
        heartProfileOf(me())?.let { setHeartProfile(side, it) }
        workoutUse = racketArea.racketById(racketArea.workoutRacket.value)?.use()
        workout.start(side, straps[side]?.name)
        MatchService.start(app)
        return true
    }

    internal fun onWorkoutFinished(rec: WorkoutRecord) {
        val w = rec.copy(heart = rec.heart.copy(profile = heartProfiles.value[workoutSide]), racket = workoutUse)
        scope.launch {
            withContext(io) { storage.saveWorkout(w) }
            refreshWorkouts()
        }
        workoutDetail.value = w
        if (!needsService()) MatchService.stop(app)
    }

    fun deleteWorkout(id: String) {
        scope.launch {
            withContext(io) { storage.deleteWorkout(id) }
            workoutDetail.value = null
            refreshWorkouts()
        }
    }

    /** Condivide l'allenamento come CSV (secondo per secondo) o PDF di riepilogo. */
    fun workoutShareIntent(w: WorkoutRecord, pdf: Boolean): Intent? {
        val s = strings
        val dir = File(app.cacheDir, "share").apply { mkdirs() }
        val stamp = java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm", java.util.Locale.ROOT).format(java.util.Date(w.startedAt))
        val file = File(dir, "${s.workoutTitle}_$stamp.${if (pdf) "pdf" else "csv"}")
        runCatching {
            file.outputStream().use { o ->
                if (pdf) WorkoutReport.pdf(w, s, options.value.lang, o) else o.write(WorkoutStats.csv(w).toByteArray())
            }
        }.onFailure { return null }
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = if (pdf) "application/pdf" else "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "${s.workoutTitle} ${Reports.date(w.startedAt, options.value.lang)}")
            clipData = ClipData.newRawUri("", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, s.share)
    }

    /** Come [setMatchRacket] per l'allenamento aperto. */
    fun setWorkoutRacketOf(racketId: String) {
        val w = workoutDetail.value ?: return
        val use = racketArea.racketById(racketId)?.useAt(w.startedAt)?.copy(feel = w.racket?.feel)
        saveWorkout(w.copy(racket = use))
    }

    internal fun saveWorkout(rec: WorkoutRecord) {
        workoutDetail.value = rec
        workouts.value = workouts.value.map { if (it.id == rec.id) rec else it }
        scope.launch(io) { storage.saveWorkout(rec) }
    }

    /** Sensazioni dell'allenamento aperto nel riepilogo. */
    fun setWorkoutFeel(feel: Feel) {
        val w = workoutDetail.value ?: return
        val use = w.racket ?: return
        saveWorkout(w.copy(racket = use.copy(feel = feel)))
    }
}
