// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * I dati di una persona da passare a un nuovo circolo o allenatore: solo quelli scelti da chi la manda (vedi
 * [ProfileField]). Note, diario, valutazioni, pagamenti e consensi dati ad altri non passano mai.
 */
@Serializable
data class SharedProfile(
    val firstName: String = "",
    val lastName: String = "",
    val nickname: String = "",
    val phone: String = "",
    val email: String = "",
    val birth: Long? = null,
    val sex: String = "",
    val hand: Hand? = null,
    val backhand: Backhand? = null,
    val club: String = "",
    val heightCm: Int? = null,
    val weightKg: Int? = null,
    val guardian: String = "",
    val guardianPhone: String = "",
    val fitpNumber: String = "",
    val fitpCard: FitpCard? = null,
    val fitpYear: Int? = null,
    val ranking: String = "",
    val medical: MedicalKind? = null,
    val medicalUntil: Long? = null,
) {
    val name: String get() = PlayerProfile.display(firstName, lastName, nickname)
    val hasFitp: Boolean get() = fitpNumber.isNotBlank() || fitpCard != null || ranking.isNotBlank()
}

/** Il profilo da passare: un file .tsm (anche con la foto) o un QR (senza foto). */
@Serializable
data class ProfileCard(
    val tsm: String = ProfileCards.TYPE,
    val v: Int = 1,
    val sentAt: Long = 0,
    /** Chi lo manda: la persona stessa, o il circolo / l'allenatore di prima. */
    val from: String = "",
    val profile: SharedProfile,
    /** La foto, JPEG in base64 (solo nel file). */
    val photo: String? = null,
)

/** I gruppi di dati che chi manda il profilo può scegliere (nome, cognome e soprannome vanno sempre). */
enum class ProfileField { PHOTO, CONTACTS, BIRTH, PLAY, BODY, GUARDIAN, FITP, MEDICAL }

object ProfileCards {
    const val TYPE = "tsm-profile"

    // Il tipo e la versione si scrivono sempre (si riconosce il file); i dati vuoti no, così il QR resta piccolo.
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false; prettyPrint = true }
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    private val compact = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

    fun encode(c: ProfileCard): String = json.encodeToString(ProfileCard.serializer(), c)
    /** Per il QR: senza foto e senza spazi. */
    fun encodeQr(c: ProfileCard): String = compact.encodeToString(ProfileCard.serializer(), c.copy(photo = null))
    fun decode(text: String): ProfileCard? = runCatching { json.decodeFromString(ProfileCard.serializer(), text.trim()) }.getOrNull()
        ?.takeIf { it.tsm == TYPE && (it.profile.firstName.isNotBlank() || it.profile.lastName.isNotBlank() || it.profile.nickname.isNotBlank()) }

    /** Quello che si propone di mandare: tutto quello che c'è, tranne il certificato medico (si sceglie a mano). */
    fun defaults(p: PlayerProfile, hasPhoto: Boolean): Set<ProfileField> = buildSet {
        if (hasPhoto) add(ProfileField.PHOTO)
        if (p.phone.isNotBlank() || p.email.isNotBlank()) add(ProfileField.CONTACTS)
        if (p.birth != null || p.sex.isNotBlank()) add(ProfileField.BIRTH)
        if (p.hand != null || p.backhand != null || p.club.isNotBlank()) add(ProfileField.PLAY)
        if (guardianOf(p).first.isNotBlank() || guardianOf(p).second.isNotBlank()) add(ProfileField.GUARDIAN)
        val m = p.member
        if (m != null && (m.fitpNumber.isNotBlank() || m.ranking.isNotBlank() || m.fitpCard != null)) add(ProfileField.FITP)
    }

    /** I gruppi che hanno qualcosa da mandare (gli altri non si propongono neanche). */
    fun available(p: PlayerProfile, hasPhoto: Boolean): List<ProfileField> = ProfileField.entries.filter { f ->
        when (f) {
            ProfileField.PHOTO -> hasPhoto
            ProfileField.CONTACTS -> p.phone.isNotBlank() || p.email.isNotBlank()
            ProfileField.BIRTH -> p.birth != null || p.sex.isNotBlank()
            ProfileField.PLAY -> p.hand != null || p.backhand != null || p.club.isNotBlank()
            ProfileField.BODY -> p.heightCm != null || p.weightKg != null
            ProfileField.GUARDIAN -> guardianOf(p).let { it.first.isNotBlank() || it.second.isNotBlank() }
            ProfileField.FITP -> p.member?.let { it.fitpNumber.isNotBlank() || it.ranking.isNotBlank() || it.fitpCard != null } == true
            ProfileField.MEDICAL -> p.member?.medical != null || p.member?.medicalUntil != null
        }
    }

    private fun guardianOf(p: PlayerProfile) = p.guardian.ifBlank { p.member?.guardian.orEmpty() } to p.guardianPhone.ifBlank { p.member?.guardianPhone.orEmpty() }

    /** Il profilo di [p] con solo i gruppi [fields]. */
    fun card(p: PlayerProfile, fields: Set<ProfileField>, photo: ByteArray?, from: String, now: Long): ProfileCard {
        val m = p.member
        val (g, gp) = guardianOf(p)
        fun <T> on(f: ProfileField, v: T, none: T): T = if (f in fields) v else none
        val sp = SharedProfile(
            firstName = p.firstName.ifBlank { if (p.complete) "" else p.name }, lastName = p.lastName, nickname = p.nickname,
            phone = on(ProfileField.CONTACTS, p.phone, ""), email = on(ProfileField.CONTACTS, p.email, ""),
            birth = on(ProfileField.BIRTH, p.birth, null), sex = on(ProfileField.BIRTH, p.sex, ""),
            hand = on(ProfileField.PLAY, p.hand, null), backhand = on(ProfileField.PLAY, p.backhand, null), club = on(ProfileField.PLAY, p.club, ""),
            heightCm = on(ProfileField.BODY, p.heightCm, null), weightKg = on(ProfileField.BODY, p.weightKg, null),
            guardian = on(ProfileField.GUARDIAN, g, ""), guardianPhone = on(ProfileField.GUARDIAN, gp, ""),
            fitpNumber = on(ProfileField.FITP, m?.fitpNumber.orEmpty(), ""), fitpCard = on(ProfileField.FITP, m?.fitpCard, null),
            fitpYear = on(ProfileField.FITP, m?.fitpYear, null), ranking = on(ProfileField.FITP, m?.ranking.orEmpty(), ""),
            medical = on(ProfileField.MEDICAL, m?.medical, null), medicalUntil = on(ProfileField.MEDICAL, m?.medicalUntil, null),
        )
        val pic = photo?.takeIf { ProfileField.PHOTO in fields }?.let { java.util.Base64.getEncoder().encodeToString(it) }
        return ProfileCard(sentAt = now, from = from.trim(), profile = sp, photo = pic)
    }

    /** Il giocatore che c'è già: stesso nome e cognome, o (se il nome non basta) stesso telefono o email. */
    fun find(players: List<PlayerProfile>, sp: SharedProfile): PlayerProfile? {
        if (sp.firstName.isNotBlank() && sp.lastName.isNotBlank()) Players.sameFullName(players, sp.firstName, sp.lastName).firstOrNull()?.let { return it }
        fun norm(x: String) = x.filter { it.isDigit() }.takeLast(9)
        if (sp.phone.isNotBlank()) players.firstOrNull { it.phone.isNotBlank() && norm(it.phone) == norm(sp.phone) }?.let { return it }
        if (sp.email.isNotBlank()) players.firstOrNull { it.email.equals(sp.email, ignoreCase = true) }?.let { return it }
        return null
    }

    /**
     * Il giocatore col profilo arrivato: uno nuovo, o [old] completato (si riempie solo quello che manca, niente si
     * cancella). Tessera, classifica e certificato vanno nei dati da socio se lo è (o se lo diventa con [asMember]).
     */
    fun merge(old: PlayerProfile?, sp: SharedProfile, newId: String, now: Long, asMember: Boolean = false, asStudent: Boolean = false): PlayerProfile {
        val base = old ?: PlayerProfile(newId, sp.name, sp.firstName.trim(), sp.lastName.trim(), sp.nickname.trim(), createdAt = now)
        val p = base.copy(
            firstName = base.firstName.ifBlank { sp.firstName.trim() }, lastName = base.lastName.ifBlank { sp.lastName.trim() },
            nickname = base.nickname.ifBlank { sp.nickname.trim() },
            phone = base.phone.ifBlank { sp.phone.trim() }, email = base.email.ifBlank { sp.email.trim() },
            birth = base.birth ?: sp.birth, sex = base.sex.ifBlank { sp.sex },
            hand = base.hand ?: sp.hand, backhand = base.backhand ?: sp.backhand, club = base.club.ifBlank { sp.club.trim() },
            heightCm = base.heightCm ?: sp.heightCm, weightKg = base.weightKg ?: sp.weightKg,
            guardian = base.guardian.ifBlank { sp.guardian.trim() }, guardianPhone = base.guardianPhone.ifBlank { sp.guardianPhone.trim() },
            student = base.student || asStudent,
        )
        val m0 = p.member ?: if (asMember) Membership(since = now) else null
        val m = m0?.copy(
            fitpNumber = m0.fitpNumber.ifBlank { sp.fitpNumber.trim() }, fitpCard = m0.fitpCard ?: sp.fitpCard, fitpYear = m0.fitpYear ?: sp.fitpYear,
            ranking = m0.ranking.ifBlank { sp.ranking.trim() }, medical = m0.medical ?: sp.medical, medicalUntil = m0.medicalUntil ?: sp.medicalUntil,
            guardian = m0.guardian.ifBlank { sp.guardian.trim() }, guardianPhone = m0.guardianPhone.ifBlank { sp.guardianPhone.trim() },
        )
        val named = p.copy(member = m)
        // Il nome che si vede segue nome, cognome e soprannome se il giocatore è nuovo o non era completo.
        return if (old == null || !old.complete) named.copy(name = PlayerProfile.display(named.firstName, named.lastName, named.nickname).ifBlank { named.name }) else named
    }

    fun fileName(prefix: String, name: String): String = CourseCards.fileName(prefix, name)
}
