// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later
package com.tennis.scoremanager.player

import android.app.Application
import com.tennis.scoremanager.data.Storage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Gli esercizi guidati dell'allenamento dei colpi: gli esempi della prima volta, quelli scelti e modificati.
 * Quello che serve del resto dell'app è in [PlayerHost].
 */
class DrillsArea(
    host: PlayerHost,
    private val app: Application,
    private val storage: Storage,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
) : PlayerHost by host {
    /** Gli esercizi guidati; la prima volta gli esempi, uno per colpo (si cambiano o si tolgono a piacere). */
    val drills = MutableStateFlow(storage.loadDrills() ?: com.tennis.scoremanager.data.Drills.templates({ strings.drillTemplateName(it.band) }, System.currentTimeMillis()))

    /** Esercizio scelto per il prossimo allenamento (Drill.id), null = libero. */
    val trainDrill = MutableStateFlow<String?>(null)

    fun drillById(id: String?): com.tennis.scoremanager.data.Drill? = id?.let { i -> drills.value.firstOrNull { it.id == i } }

    fun saveDrill(d: com.tennis.scoremanager.data.Drill) {
        val list = drills.value.filter { it.id != d.id } + d
        drills.value = list
        scope.launch(io) { storage.saveDrills(list) }
    }

    fun deleteDrill(id: String) {
        val list = drills.value.filter { it.id != id }
        drills.value = list
        if (trainDrill.value == id) trainDrill.value = null
        scope.launch(io) { storage.saveDrills(list) }
    }
}
