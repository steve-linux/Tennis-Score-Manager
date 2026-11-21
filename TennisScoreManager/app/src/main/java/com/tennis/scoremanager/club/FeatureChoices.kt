// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later
package com.tennis.scoremanager.club

import com.tennis.scoremanager.ClubFeature
import com.tennis.scoremanager.PlayerFeature
import com.tennis.scoremanager.data.Storage
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Le funzioni scelte: "Cosa usi" per Tu e Allenatore (le schede che non servono spariscono) e le funzioni del
 * circolo (squadre, scuola, sfide, server...). Finché non si sceglie, a chi usa già l'app non sparisce niente.
 * Quello che serve del resto dell'app è in [ClubHost].
 */
class FeatureChoices(
    host: ClubHost,
    private val storage: Storage,
) : ClubHost by host {
    // ---- Cosa usi (Tu e Allenatore) ----

    /** Finché non si sceglie, tutto acceso: a chi usa già l'app non sparisce niente. */
    val playerFeatures = MutableStateFlow(
        storage.playerFeatures?.mapNotNull { n -> PlayerFeature.entries.firstOrNull { it.name == n } }?.toSet() ?: PlayerFeature.entries.toSet(),
    )

    fun uses(f: PlayerFeature): Boolean = f in playerFeatures.value

    fun setPlayerFeature(f: PlayerFeature, on: Boolean) = setPlayerFeatures(if (on) playerFeatures.value + f else playerFeatures.value - f)

    fun setPlayerFeatures(set: Set<PlayerFeature>) {
        playerFeatures.value = set
        storage.playerFeatures = set.map { it.name }.toSet()
    }

    /** La prima presentazione propone cosa tenere dalla risposta a "come la userai" (solo se non si è mai scelto). */
    fun suggestPlayerFeatures(base: Set<PlayerFeature>) {
        if (storage.playerFeatures == null) setPlayerFeatures(base + PlayerFeature.PROGRESS + PlayerFeature.RACKETS)
    }

    // ---- Funzioni del circolo ----

    /**
     * Le funzioni accese. Finché il circolo non sceglie: quelle di base più quelle in cui ci sono già dati (squadre,
     * corsi, classifiche, server abbinato), così a chi le usa non sparisce niente.
     */
    override val clubFeatures = MutableStateFlow(
        storage.clubFeatures?.mapNotNull { n -> ClubFeature.entries.firstOrNull { it.name == n } }?.toSet()
            ?: (ClubFeature.entries.filter { it.base } + listOfNotNull(
                ClubFeature.TEAMS.takeIf { competitions.teams.value.isNotEmpty() },
                ClubFeature.SCHOOL.takeIf { school.courses.value.isNotEmpty() },
                ClubFeature.LADDER.takeIf { competitions.ladders.value.isNotEmpty() },
                ClubFeature.SERVER.takeIf { clubServer.value.paired },
            )).toSet(),
    )

    fun has(f: ClubFeature): Boolean = f in clubFeatures.value

    fun setFeature(f: ClubFeature, on: Boolean) {
        val n = if (on) clubFeatures.value + f else clubFeatures.value - f
        clubFeatures.value = n
        storage.clubFeatures = n.map { it.name }.toSet()
    }
}
