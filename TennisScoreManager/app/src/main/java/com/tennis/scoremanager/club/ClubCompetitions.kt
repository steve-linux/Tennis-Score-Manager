// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later
package com.tennis.scoremanager.club

import com.tennis.scoremanager.Screen
import com.tennis.scoremanager.data.MatchRecord
import com.tennis.scoremanager.data.SetupData
import com.tennis.scoremanager.data.Storage
import com.tennis.scoremanager.model.Side
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Le competizioni del circolo: tornei sociali e FITP, squadre e campionati, classifica a sfida. Il segnapunti parte
 * da qui (Gioca col segnapunti) e il risultato torna qui da solo ([linkClubMatch], [clubMatchFinished] e simili, che
 * MatchController chiama all'inizio e alla fine della partita). Quello che serve del resto dell'app è in [ClubHost].
 */
class ClubCompetitions(
    host: ClubHost,
    private val storage: Storage,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
) : ClubHost by host {
    // ---- Tornei del circolo (sociali e FITP) ----

    val clubTournaments = MutableStateFlow(storage.loadClubTournaments())
    val openClubTournamentId = MutableStateFlow<String?>(null)
    /** Partita del tabellone che si sta per giocare col segnapunti: (torneo, partita). */
    internal var pendingClubMatch: Pair<String, String>? = null

    fun openClubTournaments() {
        refreshPlayers()
        nav.push(Screen.CLUB_TOURNAMENTS)
    }

    fun openClubTournament(id: String) {
        openClubTournamentId.value = id
        nav.push(Screen.CLUB_TOURNAMENT)
    }

    fun clubTournament(id: String?): com.tennis.scoremanager.data.ClubTournament? = id?.let { i -> clubTournaments.value.firstOrNull { it.id == i } }

    fun saveClubTournament(t: com.tennis.scoremanager.data.ClubTournament) {
        val list = clubTournaments.value.filter { it.id != t.id } + t
        clubTournaments.value = list
        // Il torneo sul tabellone si aggiorna subito.
        if (tvClubTournament.value == t.id) publishTv()
        // La quota d'iscrizione è una quota del registro: nome e importo seguono il torneo.
        if (t.feeCents > 0) {
            val book = clubBook.value
            val fee = book.fees.firstOrNull { it.id == t.feeId }
            val name = strings.ctFeeName(t.name)
            val want = (fee ?: com.tennis.scoremanager.data.Fee(t.feeId ?: com.tennis.scoremanager.data.Fees.newId("f"), name, t.feeCents, com.tennis.scoremanager.data.FeeKind.TOURNAMENT, createdAt = System.currentTimeMillis(), tournament = t.id))
                .copy(name = name, cents = t.feeCents, due = t.start)
            if (want != fee) saveClubBook(book.copy(fees = book.fees.filter { it.id != want.id } + want))
            if (t.feeId != want.id) return saveClubTournament(t.copy(feeId = want.id))
        }
        scope.launch(io) { storage.saveClubTournaments(list) }
    }

    /** Toglie il torneo; dal registro spariscono le iscrizioni non pagate (quelle pagate restano: contabilità). */
    fun deleteClubTournament(id: String) {
        val t = clubTournament(id) ?: return
        val list = clubTournaments.value.filter { it.id != id }
        clubTournaments.value = list
        if (tvClubTournament.value == id) showClubOnTv(null)
        t.feeId?.let { f -> dropUnpaidFees(f, null) }
        scope.launch(io) { storage.saveClubTournaments(list) }
    }

    internal fun dropUnpaidFees(feeId: String, players: Set<String>?) {
        var book = clubBook.value
        for (ch in book.charges.filter { it.fee == feeId && (players == null || it.player in players) && it.payments.isEmpty() }) book = com.tennis.scoremanager.data.Fees.cancel(book, ch.id)
        saveClubBook(book)
    }

    /** Iscrive un giocatore (o una coppia); con la quota, l'iscrizione si addebita a ciascuno. */
    fun addClubEntry(t: com.tennis.scoremanager.data.ClubTournament, people: List<com.tennis.scoremanager.data.PlayerProfile>) {
        val now = System.currentTimeMillis()
        val e = com.tennis.scoremanager.data.CTEntry(com.tennis.scoremanager.data.ClubTournaments.newId("e"), people.map { it.id }, people.map { it.name }, at = now)
        val saved = t.copy(entries = t.entries + e)
        saveClubTournament(saved)
        val cur = clubTournament(t.id) ?: return
        val fee = clubBook.value.fees.firstOrNull { it.id == cur.feeId } ?: return
        var book = clubBook.value
        for (p in people) if (book.charges.none { it.fee == fee.id && it.player == p.id && !it.cancelled }) book = com.tennis.scoremanager.data.Fees.charge(book, p, fee.name, fee.cents, now, fee)
        saveClubBook(book)
    }

    fun removeClubEntry(t: com.tennis.scoremanager.data.ClubTournament, entryId: String) {
        val e = t.entries.firstOrNull { it.id == entryId } ?: return
        saveClubTournament(t.copy(entries = t.entries - e))
        t.feeId?.let { dropUnpaidFees(it, e.players.toSet()) }
    }

    /** Il sorteggio: entrano gli iscritti in regola, in ordine di classifica; gironi o tabellone. */
    fun drawClubTournament(t: com.tennis.scoremanager.data.ClubTournament): Boolean {
        val now = System.currentTimeMillis()
        val ok = com.tennis.scoremanager.data.ClubTournaments.eligible(t, players.value, clubBook.value, now)
        if (ok.size < 2) return false
        val ids = com.tennis.scoremanager.data.ClubTournaments.ordered(ok, players.value).map { it.id }
        val ms = if (t.format == com.tennis.scoremanager.data.CTFormat.GROUPS) com.tennis.scoremanager.data.ClubTournaments.groupsDraw(ids, t.groups)
        else com.tennis.scoremanager.data.ClubTournaments.knockout(ids, kotlin.random.Random(now), now)
        saveClubTournament(t.copy(matches = ms, drawnAt = now))
        return true
    }

    /** Finiti i gironi: il tabellone con chi passa. */
    fun clubKnockoutFromGroups(t: com.tennis.scoremanager.data.ClubTournament) {
        val ko = com.tennis.scoremanager.data.ClubTournaments.knockoutFromGroups(t, kotlin.random.Random(System.currentTimeMillis()), System.currentTimeMillis())
        saveClubTournament(t.copy(matches = t.matches.filter { it.round == 0 } + ko))
    }

    /** Rifà il sorteggio da capo (si può finché non c'è un risultato). */
    fun undoClubDraw(t: com.tennis.scoremanager.data.ClubTournament) = saveClubTournament(t.copy(matches = emptyList(), drawnAt = null, manualEdits = 0))

    /** Una modifica a mano del tabellone: sempre sul torneo aggiornato; false = non si può. */
    fun editClubDraw(t: com.tennis.scoremanager.data.ClubTournament, change: (com.tennis.scoremanager.data.ClubTournament) -> com.tennis.scoremanager.data.ClubTournament?): Boolean {
        val done = change(clubTournament(t.id) ?: t) ?: return false
        saveClubTournament(done)
        return true
    }

    fun setClubResult(
        t: com.tennis.scoremanager.data.ClubTournament, matchId: String, winner: String?, score: String, walkover: Boolean, retired: Boolean,
        disqualified: Boolean = false, noShow: Boolean = false,
    ) = saveClubTournament(com.tennis.scoremanager.data.ClubTournaments.setResult(t, matchId, winner, score, walkover, retired, disqualified = disqualified, noShow = noShow))

    fun setClubSchedule(t: com.tennis.scoremanager.data.ClubTournament, matchId: String, court: String, at: Long?) =
        saveClubTournament(t.copy(matches = t.matches.map { if (it.id == matchId) it.copy(court = court, at = at) else it }))

    /** Nome del turno: finale, semifinali, quarti, ottavi… o "Girone A". */
    fun clubRoundName(t: com.tennis.scoremanager.data.ClubTournament, m: com.tennis.scoremanager.data.CTMatch): String = com.tennis.scoremanager.ui.ctRoundName(strings, t, m)

    /**
     * Gioca una partita del tabellone col segnapunti: Nuova partita si apre con giocatori, torneo, turno e campo già
     * scritti; a partita finita il risultato torna da solo nel tabellone.
     */
    fun playClubMatch(t: com.tennis.scoremanager.data.ClubTournament, m: com.tennis.scoremanager.data.CTMatch) {
        val a = t.entries.firstOrNull { it.id == m.a } ?: return
        val b = t.entries.firstOrNull { it.id == m.b } ?: return
        fun p(e: com.tennis.scoremanager.data.CTEntry, i: Int) = e.players.getOrNull(i)?.let { playerById(it) }
        fun name(e: com.tennis.scoremanager.data.CTEntry, i: Int) = p(e, i)?.name ?: e.names.getOrNull(i).orEmpty()
        fun rank(e: com.tennis.scoremanager.data.CTEntry, i: Int) = p(e, i)?.member?.ranking.orEmpty()
        updateSetup {
            it.copy(
                club = storage.clubName.ifBlank { it.club }, court = m.court.ifBlank { it.court }, doubles = t.doubles,
                p1a = name(a, 0), p1b = if (t.doubles) name(a, 1) else "", p2a = name(b, 0), p2b = if (t.doubles) name(b, 1) else "",
                p1aId = a.players.getOrNull(0).orEmpty(), p1bId = if (t.doubles) a.players.getOrNull(1).orEmpty() else "",
                p2aId = b.players.getOrNull(0).orEmpty(), p2bId = if (t.doubles) b.players.getOrNull(1).orEmpty() else "",
                p1aRank = rank(a, 0), p1bRank = if (t.doubles) rank(a, 1) else "", p2aRank = rank(b, 0), p2bRank = if (t.doubles) rank(b, 1) else "",
                event = t.name, category = t.category, round = clubRoundName(t, m),
            )
        }
        // Le regole del torneo (se la segreteria le ha fissate), come nella scheda partita.
        t.rules?.let { r -> updateOptions { it.copy(format = r.format, gamesPerSet = r.gamesPerSet, setTiebreak = r.setTiebreak, noAd = r.noAd) } }
        pendingClubMatch = t.id to m.id
        go(Screen.SETUP)
    }

    /** La partita appena iniziata è quella del tabellone scelta con [playClubMatch]. */
    internal fun linkClubMatch(recordId: String, su: SetupData) {
        val (tid, mid) = pendingClubMatch ?: return
        pendingClubMatch = null
        val t = clubTournament(tid) ?: return
        val m = t.matches.firstOrNull { it.id == mid } ?: return
        // Solo se in campo ci sono proprio loro: chi è tornato indietro e ha iniziato un'altra partita non la lega al torneo.
        val a = t.entries.firstOrNull { it.id == m.a }?.players?.firstOrNull()
        val b = t.entries.firstOrNull { it.id == m.b }?.players?.firstOrNull()
        if (a == null || b == null || su.p1aId != a || su.p2aId != b) return
        saveClubTournament(t.copy(matches = t.matches.map { if (it.id == mid) it.copy(record = recordId) else it }))
    }

    /** A partita finita, il risultato va nel tabellone (Giocatore 1 = il primo iscritto della partita). */
    internal fun clubMatchFinished(rec: MatchRecord, state: com.tennis.scoremanager.model.MatchState) {
        for (t in clubTournaments.value) {
            val m = t.matches.firstOrNull { it.record == rec.id } ?: continue
            val w = when (state.winner) { Side.P1 -> m.a; Side.P2 -> m.b; else -> null }
            saveClubTournament(com.tennis.scoremanager.data.ClubTournaments.setResult(t, m.id, w, com.tennis.scoremanager.data.Reports.scoreLine(state, Side.P1), walkover = com.tennis.scoremanager.data.Reports.isWalkover(state), retired = state.retired != null && !state.disqualified && !com.tennis.scoremanager.data.Reports.isWalkover(state), record = rec.id, disqualified = state.disqualified && !com.tennis.scoremanager.data.Reports.isWalkover(state)))
        }
    }

    // ---- Squadre e campionati ----

    val teams = MutableStateFlow(storage.loadTeams())
    val openTeamId = MutableStateFlow<String?>(null)
    val openFixtureId = MutableStateFlow<String?>(null)
    /** Partita di un incontro da giocare col segnapunti: (squadra, incontro, partita). */
    internal var pendingTeamRubber: Triple<String, String, Int>? = null

    fun openTeams() {
        refreshPlayers()
        nav.push(Screen.TEAMS)
    }

    fun openTeam(id: String) {
        openTeamId.value = id
        nav.push(Screen.TEAM)
    }

    fun openFixture(teamId: String, fixtureId: String) {
        openTeamId.value = teamId
        openFixtureId.value = fixtureId
        nav.push(Screen.FIXTURE)
    }

    fun team(id: String?): com.tennis.scoremanager.data.Team? = id?.let { i -> teams.value.firstOrNull { it.id == i } }

    /** Salva la squadra e rifà i campi tenuti per gli incontri in casa nel planning. */
    fun saveTeam(t: com.tennis.scoremanager.data.Team) {
        val list = teams.value.filter { it.id != t.id } + t
        teams.value = list
        scope.launch(io) { storage.saveTeams(list) }
        val p = courtPlan.value
        val np = p.copy(blocks = p.blocks.filter { it.team != t.id } + com.tennis.scoremanager.data.Teams.blocks(t))
        if (np != p) {
            courtPlan.value = np
            scope.launch(io) { storage.saveCourtPlan(np) }
        }
    }

    fun saveFixture(t: com.tennis.scoremanager.data.Team, f: com.tennis.scoremanager.data.Fixture) =
        saveTeam(t.copy(fixtures = (t.fixtures.filter { it.id != f.id } + f).sortedBy { it.at }))

    fun deleteTeam(id: String) {
        val list = teams.value.filter { it.id != id }
        teams.value = list
        scope.launch(io) { storage.saveTeams(list) }
        val p = courtPlan.value.let { it.copy(blocks = it.blocks.filter { b -> b.team != id }) }
        courtPlan.value = p
        scope.launch(io) { storage.saveCourtPlan(p) }
    }

    fun teamsOf(p: com.tennis.scoremanager.data.PlayerProfile): List<com.tennis.scoremanager.data.Team> {
        val keys = setOf(p.id) + p.aliases
        return teams.value.filter { t -> t.roster.any { it in keys } || t.captain in keys }
    }

    /** "domenica 11 aprile, 09:00 · in casa" o "· trasferta: TC Altro, via…". */
    fun fixtureWhen(f: com.tennis.scoremanager.data.Fixture): String {
        val s = strings; val lang = options.value.lang
        return "${com.tennis.scoremanager.data.Notices.day(f.at, lang)}, ${com.tennis.scoremanager.data.Reports.time(f.at, lang)} · " +
            (if (f.home) s.tmHome else s.tmAway) + (f.place.ifBlank { null }?.let { ": $it" } ?: "")
    }

    internal fun teamNotice(title: String, t: com.tennis.scoremanager.data.Team, f: com.tennis.scoremanager.data.Fixture, who: List<String>, body: (com.tennis.scoremanager.data.PlayerProfile) -> List<String>, common: String) {
        val now = System.currentTimeMillis()
        val rec = who.mapNotNull { playerById(it) }.map { p ->
            val hello = strings.ntHello(p.firstName.ifBlank { p.name })
            com.tennis.scoremanager.data.Notices.recipient(p, (listOf(hello) + body(p) + listOfNotNull(clubSign.ifBlank { null }?.let { "\n$it" })).joinToString("\n"), now)
        }
        openNotice(com.tennis.scoremanager.data.NoticePlan(title, "${t.name} – ${f.opponent}", common, rec))
    }

    /** Chiede la disponibilità a tutta la rosa (o a chi non ha ancora risposto, con [missingOnly]). */
    fun noticeAvailability(t: com.tennis.scoremanager.data.Team, f: com.tennis.scoremanager.data.Fixture, missingOnly: Boolean) {
        val s = strings
        val who = if (missingOnly) com.tennis.scoremanager.data.Teams.noAnswer(t, f) else t.roster
        val lines = listOf(s.tmAskAvailability("${t.name} – ${f.opponent}"), fixtureWhen(f), s.tmReplyYesNo)
        teamNotice(s.tmAvailabilityTitle, t, f, who, { lines }, (listOf("${t.name} – ${f.opponent}", fixtureWhen(f), s.tmReplyYesNo)).joinToString("\n"))
    }

    /** La convocazione ai convocati: incontro, giorno, ora, dove e il ritrovo. */
    fun noticeCallUp(t: com.tennis.scoremanager.data.Team, f: com.tennis.scoremanager.data.Fixture) {
        val s = strings
        val names = f.callUp.mapNotNull { playerById(it)?.name }
        val lines = listOfNotNull(s.tmCalledUp("${t.name} – ${f.opponent}"), fixtureWhen(f), f.meeting.ifBlank { null }?.let { s.tmMeetingLine(it) }, f.note.ifBlank { null })
        val common = (listOf(s.tmCallUpTitle + ": ${t.name} – ${f.opponent}", fixtureWhen(f)) + listOfNotNull(f.meeting.ifBlank { null }?.let { s.tmMeetingLine(it) }) + names.map { "• $it" }).joinToString("\n")
        teamNotice(s.tmCallUpTitle, t, f, f.callUp, { lines }, common)
    }

    /** Il risultato dell'incontro, partita per partita, per il gruppo della squadra. */
    fun noticeResult(t: com.tennis.scoremanager.data.Team, f: com.tennis.scoremanager.data.Fixture) {
        val (w, l) = com.tennis.scoremanager.data.Teams.result(f)
        val text = (listOf("${t.name} – ${f.opponent} $w-$l") + f.rubbers.mapIndexed { i, r ->
            val ours = r.ours.mapNotNull { playerById(it)?.name }.joinToString(" / ").ifBlank { "—" }
            "${rubberName(t, i)}: $ours – ${r.theirs.joinToString(" / ").ifBlank { "—" }} ${r.score}${if (r.walkover) " " + strings.ctWalkover else ""}".trim()
        } + listOfNotNull(clubSign.ifBlank { null })).joinToString("\n")
        val now = System.currentTimeMillis()
        openNotice(com.tennis.scoremanager.data.NoticePlan(strings.tmResultTitle, "${t.name} – ${f.opponent}", text, t.roster.mapNotNull { playerById(it) }.map { com.tennis.scoremanager.data.Notices.recipient(it, text, now) }))
    }

    /** "Singolare 1", "Doppio 2". */
    fun rubberName(t: com.tennis.scoremanager.data.Team, i: Int): String = if (i < t.singles) strings.tmSingleN(i + 1) else strings.tmDoubleN(i - t.singles + 1)

    /** Gioca una partita dell'incontro col segnapunti: i nostri come Giocatore 1, gli avversari come Giocatore 2. */
    fun playTeamRubber(t: com.tennis.scoremanager.data.Team, f: com.tennis.scoremanager.data.Fixture, index: Int) {
        val r = f.rubbers.getOrNull(index) ?: return
        val ours = r.ours.map { playerById(it) }
        fun n(i: Int) = ours.getOrNull(i)?.name.orEmpty()
        fun id(i: Int) = r.ours.getOrNull(i).orEmpty()
        updateSetup {
            it.copy(
                club = storage.clubName.ifBlank { it.club }, doubles = r.doubles,
                p1a = n(0), p1b = if (r.doubles) n(1) else "", p2a = r.theirs.getOrNull(0).orEmpty(), p2b = if (r.doubles) r.theirs.getOrNull(1).orEmpty() else "",
                p1aId = id(0), p1bId = if (r.doubles) id(1) else "", p2aId = "", p2bId = "",
                p1aRank = ours.getOrNull(0)?.member?.ranking.orEmpty(), p1bRank = if (r.doubles) ours.getOrNull(1)?.member?.ranking.orEmpty() else "", p2aRank = "", p2bRank = "",
                event = t.championship.ifBlank { t.name }, category = "${t.name} – ${f.opponent}", round = rubberName(t, index),
            )
        }
        pendingTeamRubber = Triple(t.id, f.id, index)
        go(Screen.SETUP)
    }

    internal fun linkTeamRubber(recordId: String, su: SetupData) {
        val (tid, fid, i) = pendingTeamRubber ?: return
        pendingTeamRubber = null
        val t = team(tid) ?: return
        val f = t.fixtures.firstOrNull { it.id == fid } ?: return
        val r = f.rubbers.getOrNull(i) ?: return
        // Solo se in campo c'è proprio il nostro giocatore di quella partita.
        if (r.ours.firstOrNull() != su.p1aId) return
        saveFixture(t, f.copy(rubbers = f.rubbers.mapIndexed { k, x -> if (k == i) x.copy(record = recordId) else x }))
    }

    internal fun teamRubberFinished(rec: MatchRecord, state: com.tennis.scoremanager.model.MatchState) {
        for (t in teams.value) for (f in t.fixtures) {
            val i = f.rubbers.indexOfFirst { it.record == rec.id }
            if (i < 0) continue
            val won = when (state.winner) { Side.P1 -> true; Side.P2 -> false; else -> null }
            // Ritiro prima del primo punto: è un w.o., col punteggio del regolamento.
            val wo = com.tennis.scoremanager.data.Reports.isWalkover(state)
            saveFixture(t, com.tennis.scoremanager.data.Teams.setRubber(f, i, com.tennis.scoremanager.data.Reports.resultLine(state, Side.P1, strings), won, rec.id, walkover = wo))
            return
        }
    }

    // ---- Classifica a sfida ----

    val ladders = MutableStateFlow(storage.loadLadders())
    val openLadderId = MutableStateFlow<String?>(null)
    internal var pendingLadder: Pair<String, String>? = null

    fun openLadders() { refreshPlayers(); nav.push(Screen.LADDERS) }

    fun openLadder(id: String) { openLadderId.value = id; refreshPlayers(); nav.push(Screen.LADDER) }

    fun ladder(id: String?): com.tennis.scoremanager.data.Ladder? = ladders.value.firstOrNull { it.id == id }

    fun saveLadder(l: com.tennis.scoremanager.data.Ladder) {
        val list = ladders.value.filter { it.id != l.id } + l
        ladders.value = list.sortedBy { it.createdAt }
        scope.launch(io) { storage.saveLadders(list) }
    }

    fun deleteLadder(id: String) {
        val list = ladders.value.filter { it.id != id }
        ladders.value = list
        scope.launch(io) { storage.saveLadders(list) }
    }

    /** La sfida col segnapunti: lo sfidante è il Giocatore 1, lo sfidato il Giocatore 2; il risultato torna da solo. */
    fun playChallenge(l: com.tennis.scoremanager.data.Ladder, ch: com.tennis.scoremanager.data.Challenge) {
        val a = playerById(ch.challenger); val b = playerById(ch.defender)
        updateSetup {
            it.copy(
                club = storage.clubName.ifBlank { it.club }, doubles = false,
                p1a = a?.name.orEmpty(), p1b = "", p2a = b?.name.orEmpty(), p2b = "",
                p1aId = ch.challenger, p1bId = "", p2aId = ch.defender, p2bId = "",
                p1aRank = a?.member?.ranking.orEmpty(), p1bRank = "", p2aRank = b?.member?.ranking.orEmpty(), p2bRank = "",
                event = l.name, category = strings.ldTitle, round = strings.ldPositions(ch.challengerPos, ch.defenderPos),
            )
        }
        pendingLadder = l.id to ch.id
        go(Screen.SETUP)
    }

    internal fun linkLadderMatch(recordId: String, su: SetupData) {
        val (lid, cid) = pendingLadder ?: return
        pendingLadder = null
        val l = ladder(lid) ?: return
        val ch = l.challenges.firstOrNull { it.id == cid && it.open } ?: return
        // Solo se in campo ci sono proprio loro due.
        if (su.p1aId != ch.challenger || su.p2aId != ch.defender) return
        saveLadder(l.copy(challenges = l.challenges.map { if (it.id == cid) it.copy(record = recordId) else it }))
    }

    internal fun ladderMatchFinished(rec: MatchRecord, state: com.tennis.scoremanager.model.MatchState) {
        for (l in ladders.value) {
            val ch = l.challenges.firstOrNull { it.open && it.record == rec.id } ?: continue
            val won = when (state.winner) { Side.P1 -> true; Side.P2 -> false; else -> return }
            saveLadder(com.tennis.scoremanager.data.Ladders.result(l, ch.id, com.tennis.scoremanager.data.Reports.resultLine(state, Side.P1, strings), won, System.currentTimeMillis(), rec.id))
            return
        }
    }

    /** L'avviso ai due giocatori: chi sfida chi e entro quando. */
    fun notifyChallenge(l: com.tennis.scoremanager.data.Ladder, ch: com.tennis.scoremanager.data.Challenge) {
        val s = strings
        val now = System.currentTimeMillis()
        val a = playerById(ch.challenger)?.name.orEmpty(); val b = playerById(ch.defender)?.name.orEmpty()
        val text = listOf(l.name, s.ldVs(a, b), s.ldBy(com.tennis.scoremanager.ui.screens.shortDate(ch.deadline, options.value.lang)), storage.clubName).filter { it.isNotBlank() }.joinToString("\n")
        // Con la scheda partita: chi tiene il punteggio la apre con TSM e il risultato torna qui.
        val card = challengeCardFile(l.id, ch.id)
        val personal = listOfNotNull(l.name, s.ldVs(a, b), s.ldBy(com.tennis.scoremanager.ui.screens.shortDate(ch.deadline, options.value.lang)),
            s.ntCardHowTo.takeIf { card != null }, storage.clubName).filter { it.isNotBlank() }.joinToString("\n")
        card?.let { f -> listOf(ch.challenger, ch.defender).forEach { serverMail(it, "card", f) } }
        val rec = listOfNotNull(playerById(ch.challenger), playerById(ch.defender)).map { com.tennis.scoremanager.data.Notices.recipient(it, personal, now).copy(attachment = card) }
        openNotice(com.tennis.scoremanager.data.NoticePlan(s.ldNoticeTitle, l.name, text, rec))
    }
}
