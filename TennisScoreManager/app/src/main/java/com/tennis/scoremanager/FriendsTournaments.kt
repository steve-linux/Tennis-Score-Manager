// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager

import android.content.Intent
import com.tennis.scoremanager.data.Reports
import com.tennis.scoremanager.data.Storage
import com.tennis.scoremanager.data.Tournament
import com.tennis.scoremanager.data.TournamentKind
import com.tennis.scoremanager.data.TournamentScoring
import com.tennis.scoremanager.data.Tournaments
import com.tennis.scoremanager.model.Lang
import com.tennis.scoremanager.ui.Strings
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Il torneo tra amici (Americano e Mexicano): l'elenco dei tornei, quello aperto, i turni e la classifica da
 * condividere. Vive per conto suo accanto a [MatchController]: usa solo i file, la navigazione e la voce.
 */
class FriendsTournaments(
    private val storage: Storage,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val nav: Navigator,
    private val strings: () -> Strings,
    private val lang: () -> Lang,
    /** Dice il punteggio di una partita contata punto per punto (se la voce è accesa). */
    private val sayScore: (Int, Int) -> Unit,
) {
    /** Tutti i tornei salvati, dal più recente. */
    val all = MutableStateFlow<List<Tournament>>(emptyList())
    /** Torneo aperto; null = elenco dei tornei e creazione di uno nuovo. */
    val current = MutableStateFlow<Tournament?>(null)

    /** Apre la schermata dei tornei; si riapre da solo l'ultimo torneo non concluso. */
    fun show() {
        refresh()
        if (current.value == null) {
            scope.launch {
                withContext(io) { storage.loadTournaments() }.firstOrNull { !it.finished }?.let { current.value = it }
            }
        }
        nav.push(Screen.TOURNAMENT)
    }

    private fun refresh() {
        scope.launch { all.value = withContext(io) { storage.loadTournaments() } }
    }

    private fun save(t: Tournament) {
        current.value = t
        scope.launch {
            withContext(io) { storage.saveTournament(t) }
            refresh()
        }
    }

    fun create(
        name: String, kind: TournamentKind, players: List<String>, courts: Int,
        scoring: TournamentScoring, pointsPerMatch: Int, minutes: Int,
    ) {
        val now = System.currentTimeMillis()
        val t = Tournament(
            id = "t$now", name = name.trim(), kind = kind, players = players.map { it.trim() },
            courts = courts, scoring = scoring, pointsPerMatch = pointsPerMatch, minutes = minutes, createdAt = now,
        )
        save(t.copy(rounds = listOf(Tournaments.nextRound(t))))
    }

    fun open(t: Tournament) {
        current.value = t
    }

    /** Torna all'elenco dei tornei (quello aperto resta salvato). */
    fun close() {
        current.value = null
        refresh()
    }

    /** Risultato di una partita del turno [round]; null = ancora da giocare. */
    fun setResult(round: Int, court: Int, a: Int?, b: Int?) {
        val t = current.value ?: return
        val rounds = t.rounds.map { r ->
            if (r.number != round) r
            else r.copy(matches = r.matches.map { if (it.court == court) it.copy(scoreA = a, scoreB = b) else it })
        }
        save(t.copy(rounds = rounds))
    }

    /** Turno successivo, solo se tutte le partite del turno in corso hanno il risultato. */
    fun nextRound(): Boolean {
        val t = current.value ?: return false
        if (t.current?.complete == false) return false
        save(t.copy(rounds = t.rounds + Tournaments.nextRound(t)))
        return true
    }

    /** Conclude il torneo. L'ultimo turno, se non è stato giocato per niente, si toglie. */
    fun finish() {
        val t = current.value ?: return
        val rounds = t.rounds.filterIndexed { i, r -> i < t.rounds.size - 1 || r.matches.any { it.played } }
        save(t.copy(rounds = rounds, finished = true))
    }

    fun delete(id: String) {
        if (current.value?.id == id) current.value = null
        scope.launch {
            withContext(io) { storage.deleteTournament(id) }
            refresh()
        }
    }

    /** La voce dice il punteggio di una partita contata punto per punto. */
    fun announce(a: Int, b: Int) = sayScore(a, b)

    /** Classifica del torneo come testo da condividere. */
    fun shareIntent(): Intent? {
        val t = current.value ?: return null
        val s = strings()
        val kind = if (t.kind == TournamentKind.AMERICANO) s.tournamentAmericano else s.tournamentMexicano
        val text = buildString {
            appendLine(listOf(t.name.ifBlank { kind }, kind).distinct().joinToString(" · "))
            appendLine(s.tournamentRoundsPlayed(t.rounds.count { r -> r.matches.any { it.played } }))
            Tournaments.standings(t).forEachIndexed { i, st ->
                appendLine("${i + 1}. ${st.name} – ${st.points} (${s.tournamentWonShort} ${st.won}, ${st.diff.let { if (it > 0) "+$it" else "$it" }})")
            }
            append("Tennis Score Manager · ${Reports.siteUrl(lang())}")
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        return Intent.createChooser(send, s.tournamentShare)
    }
}
