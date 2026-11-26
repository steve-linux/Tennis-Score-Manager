// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later
package com.tennis.scoremanager.player

import android.app.Application
import com.tennis.scoremanager.data.PlayerStats
import com.tennis.scoremanager.data.Storage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * La cura del database dei giocatori: le domande sui giocatori arrivati da un altro telefono ("è lui?"), unire due
 * schede della stessa persona, separare un omonimo, togliere o cancellare un allievo.
 * Quello che serve del resto dell'app è in [PlayerHost].
 */
class PlayersArea(
    host: PlayerHost,
    private val app: Application,
    private val storage: Storage,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
) : PlayerHost by host {
    /** Domande in sospeso sui giocatori arrivati da un altro telefono ("è lui?"). */
    val playerQuestions = MutableStateFlow(storage.loadPlayerQuestions())

    /**
     * Risposta a una domanda: [localId] = è il nostro giocatore (i due identificativi si legano e le sue partite
     * arrivate diventano sue); null = un'altra persona, che si aggiunge col suo identificativo.
     */
    fun answerPlayerQuestion(q: com.tennis.scoremanager.data.Players.Question, localId: String?) {
        val rest = playerQuestions.value.filter { it.incomingId != q.incomingId }
        playerQuestions.value = rest
        scope.launch {
            withContext(io) {
                var people = storage.loadPlayers()
                if (localId != null) {
                    people = people.map { if (it.id == localId) it.copy(aliases = it.aliases + q.incomingId) else it }
                    storage.savePlayers(people)
                    storage.remapPlayer(setOf(q.incomingId), localId)
                } else if (people.none { it.id == q.incomingId }) {
                    storage.savePlayers(people + com.tennis.scoremanager.data.PlayerProfile(q.incomingId, q.name, nickname = q.name, createdAt = System.currentTimeMillis()))
                }
                storage.savePlayerQuestions(rest)
            }
            refreshPlayers()
            refreshHistory()
            refreshSaved()
        }
    }

    /**
     * Unisce [other] a [keep]: è la stessa persona scritta in due modi o creata due volte. Le partite e le sessioni di
     * [other] diventano di [keep], che tiene anche i suoi identificativi (così le partite che arriveranno da altri
     * telefoni lo riconoscono).
     */
    fun mergePlayers(keep: String, other: String) {
        val a = playerById(keep) ?: return
        val b = playerById(other) ?: return
        if (a.id == b.id) return
        val merged = a.copy(
            aliases = a.aliases + b.aliases + b.id, student = a.student || b.student,
            hand = a.hand ?: b.hand, racket = a.racket ?: b.racket,
            phone = a.phone.ifBlank { b.phone }, email = a.email.ifBlank { b.email }, birth = a.birth ?: b.birth, member = a.member ?: b.member,
            sex = a.sex.ifBlank { b.sex }, heightCm = a.heightCm ?: b.heightCm, weightKg = a.weightKg ?: b.weightKg, club = a.club.ifBlank { b.club }, backhand = a.backhand ?: b.backhand,
            note = listOf(a.note, b.note).filter { it.isNotBlank() }.joinToString("\n"),
            coachNotes = (a.coachNotes + b.coachNotes).distinctBy { it.id }.sortedByDescending { it.at },
        )
        val list = players.value.filter { it.id != b.id && it.id != a.id } + merged
        players.value = list.sortedBy { it.name.lowercase() }
        if (trainStudent.value == b.id) setTrainStudent(a.id)
        scope.launch {
            withContext(io) {
                storage.savePlayers(list)
                storage.remapPlayer(setOf(b.id) + b.aliases, a.id)
                saveClubBook(com.tennis.scoremanager.data.Fees.remap(clubBook.value, setOf(b.id) + b.aliases, a.id))
                storage.loadServeSessions().filter { it.student == b.id }.forEach { storage.saveServeSession(it.copy(student = a.id)) }
            }
            refreshHistory()
            refreshSaved()
            refreshServeSessions()
        }
    }

    /**
     * Separa un omonimo: le partite [matchIds] di [id] sono di un'altra persona con lo stesso nome, che nasce ora.
     * Restituisce il giocatore nuovo.
     */
    fun splitPlayer(id: String, matchIds: Set<String>): com.tennis.scoremanager.data.PlayerProfile? {
        val p = playerById(id) ?: return null
        if (matchIds.isEmpty()) return null
        val twin = com.tennis.scoremanager.data.PlayerProfile(com.tennis.scoremanager.data.Players.newId(), p.name, nickname = p.name, createdAt = System.currentTimeMillis())
        val list = players.value + twin
        players.value = list.sortedBy { it.name.lowercase() }
        scope.launch {
            withContext(io) {
                storage.savePlayers(list)
                storage.remapPlayer(setOf(p.id) + p.aliases, twin.id, matchIds)
            }
            refreshHistory()
            refreshSaved()
        }
        return twin
    }

    /** Per distinguere due omonimi: quante partite ha e l'ultima volta (null senza partite). */
    fun playerHint(p: com.tennis.scoremanager.data.PlayerProfile): String? {
        val lines = studentMatches(p)
        val last = lines.lastOrNull() ?: return if (p.student) strings.studentsTitle else null
        return strings.playerMatchesHint(lines.size, com.tennis.scoremanager.data.Reports.date(last.rec.endedAt ?: last.rec.updatedAt, options.value.lang))
    }

    /** Toglie un allievo dall'elenco: resta un giocatore, e le sue sessioni restano sue. */
    fun deleteStudent(id: String) {
        val p = playerById(id) ?: return
        if (trainStudent.value == id) setTrainStudent(null)
        savePlayer(p.copy(student = false))
    }

    /**
     * Cancella l'allievo e quello che il telefono ha di suo: il giocatore, le sessioni con video e analisi, note e
     * foto; la sua racchetta resta tra le racchette ma non è più sua. Le partite dello storico no: sono anche degli
     * avversari (vi resta il nome).
     */
    fun purgeStudent(id: String) {
        val st = playerById(id) ?: return
        val list = players.value.filter { it.id != st.id }
        players.value = list
        if (trainStudent.value == id) setTrainStudent(null)
        photos.removePhoto(st.name)
        // Dal registro del circolo via gli addebiti non pagati; i pagamenti fatti restano col nome (contabilità).
        saveClubBook(com.tennis.scoremanager.data.Fees.forget(clubBook.value, setOf(st.id) + st.aliases))
        racketArea.rackets.value.filter { it.owner.isNotBlank() && PlayerStats.key(it.owner) == PlayerStats.key(st.name) }.forEach { racketArea.saveRacket(it.copy(owner = "")) }
        scope.launch {
            withContext(io) {
                storage.savePlayers(list)
                storage.loadServeSessions().filter { it.student == st.id }.forEach { storage.deleteServeSession(it) }
            }
            refreshServeSessions()
        }
    }
}
