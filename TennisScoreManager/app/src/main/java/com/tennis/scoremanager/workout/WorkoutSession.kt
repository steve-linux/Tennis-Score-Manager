// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.workout

import android.os.SystemClock
import com.tennis.scoremanager.ble.HeartMeasurement
import com.tennis.scoremanager.data.HeartTrack
import com.tennis.scoremanager.data.WorkoutRecord
import com.tennis.scoremanager.data.WorkoutStats
import com.tennis.scoremanager.model.Side
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.security.SecureRandom

enum class WorkoutPhase { IDLE, RUNNING, PAUSED, COOLDOWN }

/** Quello che si vede durante l'allenamento. */
data class WorkoutUi(
    val phase: WorkoutPhase = WorkoutPhase.IDLE,
    val elapsedS: Int = 0,
    val lap: Int = 1,
    val lapS: Int = 0,
    val bpm: Int? = null,
    /** Secondi di recupero che mancano dopo "Fine". */
    val cooldownLeft: Int = 0,
)

/**
 * Allenamento con una fascia, senza partita: un valore di battito al secondo (l'ultima misura arrivata negli ultimi
 * 3 secondi), gli intervalli RR per l'HRV, i giri. Dopo "Fine" registra ancora un minuto per il recupero, poi
 * consegna l'allenamento a [onFinished]. Le misure arrivano da qualunque telefono (anche dal satellite).
 */
class WorkoutSession(
    private val scope: CoroutineScope,
    private val onFinished: (WorkoutRecord) -> Unit,
) {
    private val _ui = MutableStateFlow(WorkoutUi())
    val ui: StateFlow<WorkoutUi> = _ui

    val active: Boolean get() = _ui.value.phase != WorkoutPhase.IDLE

    private var side: Side = Side.P1
    private var strapName: String? = null
    private var id = ""
    private var startedAt = 0L
    private var elapsed = 0
    private var t = ArrayList<Int>()
    private var bpm = ArrayList<Int>()
    private var rr = ArrayList<Int>()
    private var laps = ArrayList<Int>()
    private var cooldown = ArrayList<Int>()
    private var last: Int? = null
    private var lastAt = 0L
    private var ticker: Job? = null

    /** Misura di una fascia: conta solo quella del lato scelto all'avvio. */
    fun onReading(from: Side, m: HeartMeasurement) {
        if (from != side || !m.valid) return
        last = m.bpm
        lastAt = SystemClock.elapsedRealtime()
        if (_ui.value.phase == WorkoutPhase.RUNNING) rr.addAll(m.rrMs)
        _ui.value = _ui.value.copy(bpm = m.bpm)
    }

    fun start(side: Side, strapName: String?) {
        if (active) return
        this.side = side
        this.strapName = strapName
        id = "w" + System.currentTimeMillis() + "-" + ByteArray(3).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        startedAt = System.currentTimeMillis()
        elapsed = 0
        t = ArrayList(); bpm = ArrayList(); rr = ArrayList(); laps = ArrayList(); cooldown = ArrayList()
        _ui.value = WorkoutUi(WorkoutPhase.RUNNING, bpm = fresh())
        ticker?.cancel()
        ticker = scope.launch {
            var next = SystemClock.elapsedRealtime()
            while (true) {
                next += 1_000
                delay((next - SystemClock.elapsedRealtime()).coerceAtLeast(0))
                tick()
            }
        }
    }

    private fun fresh(): Int? = last?.takeIf { SystemClock.elapsedRealtime() - lastAt <= 3_000 }

    private fun tick() {
        val now = fresh()
        when (_ui.value.phase) {
            WorkoutPhase.RUNNING -> {
                now?.let { t.add(elapsed); bpm.add(it) }
                elapsed++
            }
            WorkoutPhase.COOLDOWN -> {
                // Senza misura si ripete l'ultima: il recupero si legge al sessantesimo secondo.
                (now ?: cooldown.lastOrNull())?.let { cooldown.add(it) }
                if (_ui.value.cooldownLeft <= 1) {
                    finish()
                    return
                }
            }
            else -> Unit
        }
        val lapStart = laps.lastOrNull() ?: 0
        _ui.value = _ui.value.copy(
            elapsedS = elapsed, lap = laps.size + 1, lapS = elapsed - lapStart, bpm = now,
            cooldownLeft = if (_ui.value.phase == WorkoutPhase.COOLDOWN) _ui.value.cooldownLeft - 1 else 0,
        )
    }

    fun pause() {
        if (_ui.value.phase == WorkoutPhase.RUNNING) _ui.value = _ui.value.copy(phase = WorkoutPhase.PAUSED)
    }

    fun resume() {
        if (_ui.value.phase == WorkoutPhase.PAUSED) _ui.value = _ui.value.copy(phase = WorkoutPhase.RUNNING)
    }

    fun lap() {
        if (_ui.value.phase != WorkoutPhase.RUNNING || elapsed == (laps.lastOrNull() ?: 0)) return
        laps.add(elapsed)
        _ui.value = _ui.value.copy(lap = laps.size + 1, lapS = 0)
    }

    /** "Fine": ancora un minuto per il recupero (si può saltare con [finish]). */
    fun end() {
        val p = _ui.value.phase
        if (p != WorkoutPhase.RUNNING && p != WorkoutPhase.PAUSED) return
        _ui.value = _ui.value.copy(phase = WorkoutPhase.COOLDOWN, cooldownLeft = WorkoutStats.RECOVERY_S)
    }

    /** Salva subito (anche senza aspettare il minuto di recupero). */
    fun finish() {
        if (!active) return
        ticker?.cancel()
        ticker = null
        val rec = WorkoutRecord(
            id = id, startedAt = startedAt, endedAt = System.currentTimeMillis(), durationS = elapsed,
            heart = HeartTrack(t = t.toList(), bpm = bpm.toList()), laps = laps.toList(),
            cooldown = cooldown.toList(), rr = rr.toList(), strapName = strapName,
        )
        _ui.value = WorkoutUi()
        onFinished(rec)
    }

    /** Annulla senza salvare. */
    fun discard() {
        ticker?.cancel()
        ticker = null
        _ui.value = WorkoutUi()
    }
}
