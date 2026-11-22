// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later
package com.tennis.scoremanager.club

import android.app.Application
import com.tennis.scoremanager.data.Storage
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map

/**
 * Gli avvisi del circolo: iscritti, partite e convocazioni con la scheda partita, sorteggio, quote da pagare,
 * soci, ricevute dei pagamenti e CSV dell'anno. La finestra che li manda (openNotice) è di MatchController.
 * Quello che serve del resto dell'app è in [ClubHost].
 */
class ClubNotices(
    host: ClubHost,
    private val app: Application,
    private val storage: Storage,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
) : ClubHost by host {
    internal fun peopleOf(t: com.tennis.scoremanager.data.ClubTournament, entryIds: Collection<String?>): List<com.tennis.scoremanager.data.PlayerProfile> =
        t.entries.filter { it.id in entryIds }.flatMap { it.players }.distinct().mapNotNull { playerById(it) }
    /** A ogni iscritto: iscrizione confermata e quota pagata o da pagare. */
    fun noticeEntries(t: com.tennis.scoremanager.data.ClubTournament) {
        val s = strings; val lang = options.value.lang; val now = System.currentTimeMillis()
        val people = peopleOf(t, t.entries.map { it.id })
        val rec = people.map { p -> com.tennis.scoremanager.data.Notices.recipient(p, com.tennis.scoremanager.data.Notices.entryText(s, t, p, clubBook.value, clubSign, lang, now), now) }
        // Per il gruppo: l'elenco degli iscritti.
        val common = (listOf(s.ntEntriesList(t.name)) + t.entries.map { "• " + entryNameOf(t, it.id) } + listOfNotNull(clubSign.ifBlank { null })).joinToString("\n")
        openNotice(com.tennis.scoremanager.data.NoticePlan(s.ntKindEntries, t.name, common, rec))
    }

    /**
     * Le partite da giocare: a ciascun giocatore la sua (turno, avversario, giorno, ora, campo); per il gruppo
     * l'ordine di gioco. [day] = solo quel giorno, null = tutte quelle già con i due giocatori.
     */
    fun noticeMatches(t: com.tennis.scoremanager.data.ClubTournament, day: Long?) {
        val s = strings; val lang = options.value.lang; val now = System.currentTimeMillis()
        val open = t.matches.filter { !it.played && it.a != null && it.b != null }
        val ms = if (day != null) com.tennis.scoremanager.data.Notices.onDay(open, day) else open
        val names = { id: String? -> entryNameOf(t, id) }
        val title = if (day != null) s.ntOrderTitle(t.name, com.tennis.scoremanager.data.Notices.day(day, lang)) else s.ntOrderAll(t.name)
        val common = com.tennis.scoremanager.data.Notices.orderOfPlay(s, t, ms, { competitions.clubRoundName(t, it) }, names, title, lang, clubSign)
        openNotice(com.tennis.scoremanager.data.NoticePlan(if (day != null) s.ntKindOrder else s.ntKindNext, title, common, convocations(t, ms)))
    }

    /** Convoca i giocatori di una partita sola, con la sua scheda partita. */
    fun noticeMatch(t: com.tennis.scoremanager.data.ClubTournament, m: com.tennis.scoremanager.data.CTMatch) {
        val title = "${competitions.clubRoundName(t, m)}: ${entryNameOf(t, m.a)} – ${entryNameOf(t, m.b)}"
        val common = com.tennis.scoremanager.data.Notices.orderOfPlay(strings, t, listOf(m), { competitions.clubRoundName(t, it) }, { entryNameOf(t, it) },
            listOfNotNull(t.name, m.at?.let { com.tennis.scoremanager.data.Notices.day(it, options.value.lang) }).joinToString(" · "), options.value.lang, clubSign)
        openNotice(com.tennis.scoremanager.data.NoticePlan(strings.ctConvoke, title, common, convocations(t, listOf(m))))
    }

    /** A ciascun giocatore la sua partita (turno, avversario, giorno, ora, campo) con la scheda partita allegata. */
    internal fun convocations(t: com.tennis.scoremanager.data.ClubTournament, ms: List<com.tennis.scoremanager.data.CTMatch>): List<com.tennis.scoremanager.data.NoticeRecipient> {
        val s = strings; val lang = options.value.lang; val now = System.currentTimeMillis()
        val names = { id: String? -> entryNameOf(t, id) }
        return ms.flatMap { m ->
            val card = matchCardFile(t.id, m.id)
            card?.let { f -> peopleOf(t, listOf(m.a, m.b)).forEach { p -> serverMail(p.id, "card", f) } }
            peopleOf(t, listOf(m.a, m.b)).map { p ->
                val text = com.tennis.scoremanager.data.Notices.matchText(s, t, m, competitions.clubRoundName(t, m), names, p, clubSign, lang, now, card = card != null)
                com.tennis.scoremanager.data.Notices.recipient(p, text, now).copy(attachment = card)
            }
        }
    }

    /**
     * La scheda partita (vedi [com.tennis.scoremanager.data.MatchCard]) in un file da allegare. Il codice nasce alla
     * prima convocazione e resta lo stesso nelle successive (un risultato tornato vale anche dopo uno spostamento).
     * Null se la partita è già giocata o non ha ancora i due giocatori.
     */
    fun matchCardFile(tournamentId: String, matchId: String): File? {
        val t = competitions.clubTournament(tournamentId) ?: return null
        val m = t.matches.firstOrNull { it.id == matchId } ?: return null
        if (m.played || m.a == null || m.b == null) return null
        val code = m.cardCode ?: com.tennis.scoremanager.data.MatchCards.newCode().also { code ->
            competitions.saveClubTournament(t.copy(matches = t.matches.map { if (it.id == matchId) it.copy(cardCode = code) else it }))
        }
        val card = com.tennis.scoremanager.data.MatchCards.forTournament(
            t, m, code, System.currentTimeMillis(), storage.clubName, storage.clubPhone, competitions.clubRoundName(t, m),
        ) { playerById(it) } ?: return null
        return writeCardFile(card)
    }

    internal fun writeCardFile(card: com.tennis.scoremanager.data.MatchCard): File? {
        // Senza il telefono della segreteria il risultato non sa dove tornare: lo si dice a chi convoca.
        if (card.office.isBlank()) toast(strings.mcNoOffice)
        val dir = File(app.cacheDir, "share/cards").apply { mkdirs() }
        return runCatching { File(dir, com.tennis.scoremanager.data.MatchCards.fileName(card)).apply { writeText(com.tennis.scoremanager.data.MatchCards.encode(card)) } }.getOrNull()
    }

    /** La scheda di una partita di un incontro a squadre (codice come per i tornei: nasce alla prima convocazione). */
    fun rubberCardFile(teamId: String, fixtureId: String, index: Int): File? {
        val t = competitions.team(teamId) ?: return null
        val f = t.fixtures.firstOrNull { it.id == fixtureId } ?: return null
        val r = f.rubbers.getOrNull(index) ?: return null
        val code = r.cardCode ?: com.tennis.scoremanager.data.MatchCards.newCode().also { code ->
            competitions.saveFixture(t, f.copy(rubbers = f.rubbers.mapIndexed { k, x -> if (k == index) x.copy(cardCode = code) else x }))
        }
        val card = com.tennis.scoremanager.data.MatchCards.forRubber(
            t, f, index, code, System.currentTimeMillis(), storage.clubName, storage.clubPhone, competitions.rubberName(t, index),
        ) { playerById(it) } ?: return null
        return writeCardFile(card)
    }

    /** La scheda di una sfida della classifica. */
    override fun challengeCardFile(ladderId: String, challengeId: String): File? {
        val l = competitions.ladder(ladderId) ?: return null
        val ch = l.challenges.firstOrNull { it.id == challengeId } ?: return null
        val code = ch.cardCode ?: com.tennis.scoremanager.data.MatchCards.newCode().also { code ->
            competitions.saveLadder(l.copy(challenges = l.challenges.map { if (it.id == challengeId) it.copy(cardCode = code) else it }))
        }
        val card = com.tennis.scoremanager.data.MatchCards.forChallenge(
            l, ch, code, System.currentTimeMillis(), storage.clubName, storage.clubPhone, strings.ldPositions(ch.challengerPos, ch.defenderPos),
        ) { playerById(it) } ?: return null
        return writeCardFile(card)
    }

    /** Convoca i nostri giocatori di una partita dell'incontro, con la scheda partita per chi tiene il punteggio. */
    fun noticeRubber(t: com.tennis.scoremanager.data.Team, f: com.tennis.scoremanager.data.Fixture, index: Int) {
        val s = strings
        val r = f.rubbers.getOrNull(index) ?: return
        val card = rubberCardFile(t.id, f.id, index)
        val ours = r.ours.mapNotNull { playerById(it)?.name }.joinToString(" / ")
        val theirs = r.theirs.filter { it.isNotBlank() }.joinToString(" / ").ifBlank { f.opponent }
        val lines = listOfNotNull(s.ntMatch(competitions.rubberName(t, index), "${t.name} – ${f.opponent}"), "$ours – $theirs", competitions.fixtureWhen(f), s.ntCardHowTo.takeIf { card != null })
        val now = System.currentTimeMillis()
        card?.let { f -> r.ours.forEach { serverMail(it, "card", f) } }
        val rec = r.ours.mapNotNull { playerById(it) }.map { p ->
            val text = (listOf(s.ntHello(p.firstName.ifBlank { p.name })) + lines + listOfNotNull(clubSign.ifBlank { null }?.let { "\n$it" })).joinToString("\n")
            com.tennis.scoremanager.data.Notices.recipient(p, text, now).copy(attachment = card)
        }
        openNotice(com.tennis.scoremanager.data.NoticePlan(s.ctConvoke, "${competitions.rubberName(t, index)}: $ours – $theirs", lines.joinToString("\n"), rec))
    }

    /** Il tabellone in un'immagine, per il gruppo e i social; a ciascuno lo stesso testo. */
    fun noticeDraw(t: com.tennis.scoremanager.data.ClubTournament) {
        val s = strings; val now = System.currentTimeMillis()
        val dir = File(app.cacheDir, "share").apply { mkdirs() }
        val f = File(dir, "${s.ctTabDraw} ${t.name}.png".replace(Regex("[\\\\/:*?\"<>|]"), "_"))
        val sub = listOfNotNull(clubSign.ifBlank { null }, t.category.ifBlank { null }, if (t.kind == com.tennis.scoremanager.data.CTKind.FITP) "FITP" else s.ctSocial).joinToString(" · ")
        val ok = runCatching {
            val b = com.tennis.scoremanager.data.ClubDrawImage.render(t, { entryNameOf(t, it) }, s, sub, s.generatedWith)
            f.outputStream().use { b.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            b.recycle()
        }.isSuccess
        val common = (listOf(s.ntDrawText(t.name)) + t.champion?.let { listOf(s.ctChampion(entryNameOf(t, it))) }.orEmpty() + listOfNotNull(clubSign.ifBlank { null })).joinToString("\n")
        val rec = peopleOf(t, t.entries.map { it.id }).map { com.tennis.scoremanager.data.Notices.recipient(it, common, now) }
        openNotice(com.tennis.scoremanager.data.NoticePlan(s.ntKindDraw, t.name, common, rec, image = f.takeIf { ok }))
    }

    /** Messaggio libero agli iscritti: il testo si scrive nella schermata degli avvisi. */
    fun noticeFreeTournament(t: com.tennis.scoremanager.data.ClubTournament) {
        val now = System.currentTimeMillis()
        val rec = peopleOf(t, t.entries.map { it.id }).map { com.tennis.scoremanager.data.Notices.recipient(it, "", now) }
        openNotice(com.tennis.scoremanager.data.NoticePlan(strings.ntKindFree, t.name, "${t.name}\n\n", rec, editable = true))
    }

    /** Sollecito a chi deve qualcosa al circolo, voce per voce; con [only] solo quegli addebiti (dalla Segreteria da PC). */
    fun noticeDues(only: Set<String>? = null) {
        openNotice(com.tennis.scoremanager.data.NoticePlan(strings.ntDuesButton, strings.paymentsTitle, "", duesRecipients(only)))
    }

    /** Chi deve qualcosa e il suo messaggio, voce per voce; con [only] solo quegli addebiti. */
    fun duesRecipients(only: Set<String>? = null): List<com.tennis.scoremanager.data.NoticeRecipient> {
        val s = strings; val lang = options.value.lang; val now = System.currentTimeMillis()
        val book = clubBook.value
        return players.value.mapNotNull { p ->
            val open = com.tennis.scoremanager.data.Fees.of(book, p).filter { it.left > 0 && (only == null || it.id in only) }
            if (open.isEmpty()) null else com.tennis.scoremanager.data.Notices.recipient(p, com.tennis.scoremanager.data.Notices.duesText(s, p, open, clubSign, lang, now, com.tennis.scoremanager.data.Notices.payLines(s, book)), now)
        }
    }

    /** Ai soci che hanno qualcosa da sistemare (certificato, tessera, consensi…), a ciascuno il suo. */
    fun noticeMembersTodo() {
        val s = strings; val lang = options.value.lang; val now = System.currentTimeMillis()
        val rec = players.value.filter { it.member != null }.mapNotNull { p ->
            val issues = com.tennis.scoremanager.data.Members.issues(p, now).filter { it != com.tennis.scoremanager.data.Members.Issue.NO_CONTACT }
            if (issues.isEmpty()) null
            else com.tennis.scoremanager.data.Notices.recipient(p, com.tennis.scoremanager.data.Notices.memberText(s, p, issues.map { com.tennis.scoremanager.ui.screens.issueText(s, p, it, now, lang) }, clubSign, now), now)
        }
        openNotice(com.tennis.scoremanager.data.NoticePlan(s.ntMembersTodo, s.membersTitle, "", rec))
    }

    /** Messaggio libero a tutti i soci. */
    fun noticeMembersAll() {
        val now = System.currentTimeMillis()
        val rec = players.value.filter { it.member != null }.map { com.tennis.scoremanager.data.Notices.recipient(it, "", now) }
        openNotice(com.tennis.scoremanager.data.NoticePlan(strings.ntMembersAll, clubSign.ifBlank { strings.membersTitle }, clubSign.ifBlank { "" }.let { if (it.isBlank()) "" else "$it\n\n" }, rec, editable = true))
    }

    /** Ricevuta (non fiscale) di un pagamento, in PDF da condividere o stampare. */
    fun receiptFile(chargeId: String, paymentId: String): File? {
        val c = clubBook.value.charges.firstOrNull { it.id == chargeId } ?: return null
        val p = c.payments.firstOrNull { it.id == paymentId } ?: return null
        val s = strings
        val dir = File(app.cacheDir, "share").apply { mkdirs() }
        val f = File(dir, "${s.receiptTitle} ${p.receipt.replace('/', '-')} ${c.name}.pdf".replace(Regex("[\\\\/:*?\"<>|]"), "_"))
        return runCatching {
            f.outputStream().use { com.tennis.scoremanager.data.ReceiptPdf.write(c, p, storage.clubName, s, options.value.lang, it) }
            f
        }.getOrNull()
    }

    /** I pagamenti di un anno in CSV, per il commercialista. */
    fun paymentsCsv(year: Int): File? {
        val s = strings
        val lang = options.value.lang
        val dir = File(app.cacheDir, "share").apply { mkdirs() }
        val f = File(dir, "${s.paymentsCsvName} $year.csv")
        return runCatching {
            // Date in cifre (01/11/2026): il foglio di calcolo le riconosce come date.
            val day = java.text.SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(lang.locale, "ddMMyyyy"), lang.locale)
            val text = com.tennis.scoremanager.data.Fees.csv(clubBook.value, year, s.paymentsCsvHeader, { com.tennis.scoremanager.ui.payMethodName(s, it) }, { day.format(java.util.Date(it)) }, lang.locale)
            // Il BOM fa riconoscere al foglio di calcolo le lettere accentate.
            f.writeText("\uFEFF" + text)
            f
        }.getOrNull()
    }
}
