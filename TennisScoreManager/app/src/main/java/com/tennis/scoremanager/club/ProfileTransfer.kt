// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later
package com.tennis.scoremanager.club

import android.app.Application
import android.graphics.BitmapFactory
import com.tennis.scoremanager.Role
import com.tennis.scoremanager.data.PlayerStats
import com.tennis.scoremanager.data.Storage
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Il passaggio del profilo di un giocatore a un circolo o a un allenatore: il file .tsm (con la foto) o il QR (senza),
 * la casella della segreteria sul server, l'anteprima per la segreteria da PC, l'arrivo del profilo e dell'allievo
 * proposto dall'allenatore. Quello che serve del resto dell'app è in [ClubHost].
 */
class ProfileTransfer(
    host: ClubHost,
    private val app: Application,
    private val storage: Storage,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
) : ClubHost by host {
    /** La foto del giocatore (JPEG), se c'è. */
    fun photoBytes(name: String): ByteArray? = PlayerStats.key(name).takeIf { it.isNotEmpty() }?.let { storage.photoFile(it) }?.takeIf { it.exists() }?.let { runCatching { it.readBytes() }.getOrNull() }

    /** Chi manda un profilo: il circolo, o il nome di chi usa l'app. */
    internal fun profileSender(): String = if (activeRole.value == Role.CLUB) storage.clubName.ifBlank { strings.roleClub } else (me()?.name ?: storage.myName)

    /** Il profilo di [p] coi dati scelti: il file da mandare e il testo che lo accompagna. */
    fun profileToSend(p: com.tennis.scoremanager.data.PlayerProfile, fields: Set<com.tennis.scoremanager.data.ProfileField>): Pair<File, String>? {
        val card = com.tennis.scoremanager.data.ProfileCards.card(p, fields, photoBytes(p.name), profileSender(), System.currentTimeMillis())
        val dir = File(app.cacheDir, "share/cards").apply { mkdirs() }
        val f = runCatching { File(dir, com.tennis.scoremanager.data.ProfileCards.fileName(strings.pfFilePrefix, p.name)).apply { writeText(com.tennis.scoremanager.data.ProfileCards.encode(card)) } }.getOrNull() ?: return null
        return f to strings.pfShareText(p.name)
    }

    /** Il profilo di [p] (con la foto, se scelta) nella casella della segreteria sul server [url]; avvisa com'è andata. */
    fun profileToOffice(url: String, p: com.tennis.scoremanager.data.PlayerProfile, fields: Set<com.tennis.scoremanager.data.ProfileField>) {
        val text = profileText(p, fields)
        scope.launch { toast(if (postToOffice(url, "profile", text)) strings.pfOfficeSent else strings.pfOfficeFailed) }
    }

    /** Il file del profilo di [p] coi dati [fields] (null = quelli proposti), con la foto se scelta. */
    fun profileText(p: com.tennis.scoremanager.data.PlayerProfile, fields: Set<com.tennis.scoremanager.data.ProfileField>? = null): String =
        com.tennis.scoremanager.data.ProfileCards.encode(com.tennis.scoremanager.data.ProfileCards.card(p, fields ?: com.tennis.scoremanager.data.ProfileCards.defaults(p, photoBytes(p.name) != null), photoBytes(p.name), profileSender(), System.currentTimeMillis()))

    /** Il profilo arrivato al PC, come lo si mostra prima di aggiungerlo: chi è, cosa porta, se c'è già. */
    fun profilePreview(card: com.tennis.scoremanager.data.ProfileCard): com.tennis.scoremanager.desk.DeskIncoming {
        val s = strings; val lang = options.value.lang; val sp = card.profile
        val match = com.tennis.scoremanager.data.ProfileCards.find(players.value, sp)
        val lines = listOfNotNull(sp.name, card.from.ifBlank { null }?.let { s.pfFrom(it) },
            listOf(sp.phone, sp.email).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { null },
            sp.birth?.let { "${s.memberBirth}: ${com.tennis.scoremanager.data.Reports.date(it, lang)}" }, sp.club.ifBlank { null },
            listOf(sp.guardian, sp.guardianPhone).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { null }?.let { "${s.coGuardian}: $it" },
            listOf(sp.fitpNumber, sp.ranking).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { null }?.let { "${s.pfFitp}: $it" },
            if (card.photo != null) s.pfPhoto else null)
        return com.tennis.scoremanager.desk.DeskIncoming("PROFILE", s.pfIncomingTitle, lines, match?.let { s.pfExisting(it.name) } ?: s.pfNew, true)
    }

    /** Dal PC: il profilo entra tra i giocatori (completa quello che c'è già); l'id del giocatore. */
    fun applyProfileFromDesk(card: com.tennis.scoremanager.data.ProfileCard, asMember: Boolean, asStudent: Boolean): String =
        applyProfile(card, com.tennis.scoremanager.data.ProfileCards.find(players.value, card.profile)?.id, asMember, asStudent).id

    /** Il profilo di [p] per il QR (senza foto). */
    fun profileQr(p: com.tennis.scoremanager.data.PlayerProfile, fields: Set<com.tennis.scoremanager.data.ProfileField>): String =
        com.tennis.scoremanager.data.ProfileCards.encodeQr(com.tennis.scoremanager.data.ProfileCards.card(p, fields, null, profileSender(), System.currentTimeMillis()))

    /** Il testo letto da un QR: se è un profilo si apre, se no si dice che non lo è. */
    fun receiveProfileQr(text: String?) {
        val card = text?.let { com.tennis.scoremanager.data.ProfileCards.decode(it) }
        if (card == null) toast(strings.pfNotProfile) else coaching.incomingProfile.value = card
    }

    /**
     * Il profilo arrivato entra tra i giocatori: nuovo, o a completare [intoId] (solo quello che manca). La foto si
     * mette se non ce n'è già una; all'allenatore la classifica va anche nello storico delle classifiche.
     */
    fun applyProfile(card: com.tennis.scoremanager.data.ProfileCard, intoId: String?, asMember: Boolean, asStudent: Boolean): com.tennis.scoremanager.data.PlayerProfile {
        coaching.incomingProfile.value = null
        val old = intoId?.let { playerById(it) }
        val pl = com.tennis.scoremanager.data.ProfileCards.merge(old, card.profile, com.tennis.scoremanager.data.Players.newId(), System.currentTimeMillis(), asMember, asStudent)
        savePlayer(pl)
        card.photo?.let { b64 ->
            val key = PlayerStats.key(pl.name)
            if (key.isNotEmpty() && !storage.photoFile(key).exists()) runCatching { java.util.Base64.getDecoder().decode(b64) }.getOrNull()
                ?.takeIf { it.size in 100..2_000_000 && BitmapFactory.decodeByteArray(it, 0, it.size) != null }
                ?.let { storage.savePhoto(key, it); photosVersion.value++ }
        }
        if (pl.student && card.profile.ranking.isNotBlank() && coaching.coachBook.value.rankings.none { it.student == pl.id }) coaching.addRanking(pl.id, card.profile.ranking)
        toast(if (old == null) strings.pfAdded(pl.name) else strings.pfCompleted(pl.name))
        return pl
    }

    /** Segreteria: l'allievo proposto entra tra i giocatori (e nel corso scelto). */
    fun applyProposal(p: com.tennis.scoremanager.data.StudentProposal, courseId: String?) {
        coaching.incomingProposal.value = null
        val cs = p.student
        val existing = playerById(cs.id) ?: com.tennis.scoremanager.data.Players.sameFullName(players.value, cs.firstName, cs.lastName).singleOrNull()
        val pl = (existing ?: com.tennis.scoremanager.data.PlayerProfile(cs.id, cs.name, cs.firstName, cs.lastName, cs.nickname, createdAt = System.currentTimeMillis())).let {
            it.copy(phone = it.phone.ifBlank { cs.phone }, email = it.email.ifBlank { cs.email },
                guardian = it.guardian.ifBlank { cs.guardian }, guardianPhone = it.guardianPhone.ifBlank { cs.guardianPhone }, consentAt = it.consentAt ?: cs.consentAt)
        }
        savePlayer(pl)
        courseId?.let { school.course(it) }?.let { co -> if (pl.id !in co.students) school.saveCourse(co.copy(students = co.students + pl.id)) }
        toast(strings.ccProposalApplied(pl.name))
    }
}
