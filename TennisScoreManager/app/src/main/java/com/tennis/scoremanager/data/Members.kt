// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import kotlinx.serialization.Serializable
import java.util.Calendar

/** Tessera FITP: atleta agonista, atleta non agonista, altro (socio, dirigente, tecnico…). */
@Serializable
enum class FitpCard { AGONIST, NON_AGONIST, OTHER }

/** Certificato medico sportivo. */
@Serializable
enum class MedicalKind { AGONISTIC, NON_AGONISTIC }

/**
 * Il giocatore come socio del circolo (sul dispositivo della segreteria). Del certificato medico si tengono solo tipo
 * e scadenza, mai il documento: è un dato sanitario. Le date sono a mezzogiorno, così non cambiano col fuso orario.
 * Non passa mai ad altri telefoni con le partite.
 */
@Serializable
data class Membership(
    /** Socio dal (data dell'iscrizione). */
    val since: Long? = null,
    /** Numero della tessera del circolo. */
    val number: String = "",
    val fitpNumber: String = "",
    val fitpCard: FitpCard? = null,
    /** Anno della tessera FITP (vale per l'anno solare). */
    val fitpYear: Int? = null,
    /** Classifica FITP com'è scritta ("4.1", "3.3", "4.NC"). */
    val ranking: String = "",
    val medical: MedicalKind? = null,
    val medicalUntil: Long? = null,
    /** Quando ha dato il consenso al trattamento dei dati (null = non ancora). */
    val privacyAt: Long? = null,
    /** Consenso a foto e video (riprese degli allenamenti, foto dei tornei). */
    val photosOk: Boolean = false,
    /** Per i minorenni: il genitore o tutore e il suo telefono. */
    val guardian: String = "",
    val guardianPhone: String = "",
)

object Members {
    /** Cosa manca a un socio per essere in regola. */
    enum class Issue { NO_PRIVACY, NO_FITP, NO_MEDICAL, MEDICAL_EXPIRED, MEDICAL_SOON, NO_GUARDIAN, NO_CONTACT }

    /** Avviso di certificato in scadenza: un mese prima. */
    const val SOON_DAYS = 30

    fun year(now: Long): Int = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.YEAR)

    /** Gli anni compiuti al giorno [now] (null senza data di nascita). */
    fun age(birth: Long?, now: Long): Int? {
        birth ?: return null
        val b = Calendar.getInstance().apply { timeInMillis = birth }
        val n = Calendar.getInstance().apply { timeInMillis = now }
        var a = n.get(Calendar.YEAR) - b.get(Calendar.YEAR)
        if (n.get(Calendar.DAY_OF_YEAR) < b.get(Calendar.DAY_OF_YEAR)) a--
        return a
    }

    fun minor(p: PlayerProfile, now: Long): Boolean = (age(p.birth, now) ?: 99) < 18

    /** Il certificato vale ancora il giorno [now] (vale fino a fine giornata della scadenza). */
    fun medicalValid(m: Membership, now: Long): Boolean = m.medicalUntil?.let { endOfDay(it) >= now } == true

    private fun endOfDay(t: Long): Long = Calendar.getInstance().apply {
        timeInMillis = t
        set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59)
    }.timeInMillis

    /** Cosa c'è da sistemare per [p] il giorno [now]; vuoto se è in regola (o se non è socio). */
    fun issues(p: PlayerProfile, now: Long): List<Issue> {
        val m = p.member ?: return emptyList()
        val out = mutableListOf<Issue>()
        if (m.privacyAt == null) out += Issue.NO_PRIVACY
        if (m.fitpYear != year(now) || m.fitpNumber.isBlank()) out += Issue.NO_FITP
        when {
            m.medicalUntil == null -> out += Issue.NO_MEDICAL
            !medicalValid(m, now) -> out += Issue.MEDICAL_EXPIRED
            endOfDay(m.medicalUntil) - now < SOON_DAYS * 86_400_000L -> out += Issue.MEDICAL_SOON
        }
        if (minor(p, now) && (m.guardian.isBlank() || m.guardianPhone.isBlank())) out += Issue.NO_GUARDIAN
        if (p.phone.isBlank() && p.email.isBlank() && m.guardianPhone.isBlank()) out += Issue.NO_CONTACT
        return out
    }

    /** I problemi gravi (non si gioca): certificato mancante o scaduto, tessera dell'anno mancante. */
    fun blocking(i: Issue): Boolean = i == Issue.NO_MEDICAL || i == Issue.MEDICAL_EXPIRED || i == Issue.NO_FITP

    /**
     * La classifica come numero, per le teste di serie: più basso = più forte ("2.8" → 2.8; "4.NC" → 4.9; vuota o
     * illeggibile = null).
     */
    fun rankingValue(r: String): Double? {
        val t = r.trim().uppercase().replace(',', '.')
        if (t.isEmpty()) return null
        Regex("^([1-4])\\.NC$").find(t)?.let { return it.groupValues[1].toDouble() + 0.9 }
        if (t == "NC") return 4.9
        return Regex("^([1-4])(?:\\.(\\d))?$").find(t)?.let { m ->
            m.groupValues[1].toDouble() + (m.groupValues[2].toIntOrNull() ?: 0) / 10.0
        }
    }
}
