// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CardMembership
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import com.tennis.scoremanager.ClubFeature
import com.tennis.scoremanager.MatchController
import com.tennis.scoremanager.data.Courses
import com.tennis.scoremanager.data.CourtPlans
import com.tennis.scoremanager.data.Fees
import com.tennis.scoremanager.data.Members
import com.tennis.scoremanager.data.Reports
import com.tennis.scoremanager.ui.LocalStrings
import com.tennis.scoremanager.ui.TsmColors

/** Un riquadro di Oggi: titolo, qualche riga, e toccandolo si va dove si sistema. */
@Composable
private fun TodayCard(icon: ImageVector, title: String, lines: List<String>, alert: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(TsmColors.Surface).clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (alert) TsmColors.Orange else TsmColors.Ball)
        Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = TsmColors.TextMain, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            for (l in lines) Text(l, color = TsmColors.TextDim, fontSize = 13.sp, maxLines = 1)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = TsmColors.TextDim, modifier = Modifier.width(24.dp))
    }
}

/**
 * Circolo › Oggi: quello che serve oggi, a colpo d'occhio e solo per le funzioni accese. Chi gioca e quando,
 * cosa c'è da incassare, i soci da sistemare, le partite dei tornei, le lezioni, gli incontri della settimana e le
 * sfide scadute; ogni riquadro porta dove si sistema.
 */
@Composable
fun TodayTab(c: MatchController) {
    val s = LocalStrings.current
    val lang = c.options.collectAsState().value.lang
    val f by c.clubFeatures.collectAsState()
    val plan by c.courtPlan.collectAsState()
    val tournaments by c.clubTournaments.collectAsState()
    val players by c.players.collectAsState()
    val book by c.clubBook.collectAsState()
    val courses by c.courses.collectAsState()
    val teams by c.teams.collectAsState()
    val ladders by c.ladders.collectAsState()
    // Si aggiorna ogni minuto: lasciata aperta (un tablet in segreteria) passa da sola al giorno dopo.
    var now by remember { androidx.compose.runtime.mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(60_000)
            now = System.currentTimeMillis()
        }
    }
    val today = CourtPlans.dayStart(now)
    val tomorrow = CourtPlans.addDays(today, 1)
    fun hm(t: Long) = Reports.time(t, lang)
    var shown = 0

    com.tennis.scoremanager.ui.ClubFeaturesHint(c)
    Text(com.tennis.scoremanager.data.Notices.day(now, lang), color = TsmColors.TextDim, fontSize = 13.sp)

    // Le novità dal server delle prenotazioni: restano finché non si leggono (✓) o si aprono.
    val server by c.clubServer.collectAsState()
    if (ClubFeature.SERVER in f && server.inbox.isNotEmpty()) OnlineNews(c, server.inbox.asReversed())

    if (ClubFeature.PLANNING in f) {
        val occ = CourtPlans.occupied(plan, tournaments, today).filter { it.block == null }
        val next = occ.filter { it.end > now }.sortedBy { it.start }.take(4).map { o ->
            val who = o.booking?.let { b -> (b.players.mapNotNull { c.playerById(it)?.name } + b.names).joinToString(", ") } ?: o.tournament?.name.orEmpty()
            listOf(hm(o.start), o.court, who).filter { it.isNotBlank() }.joinToString(" · ")
        }
        val online = occ.count { it.booking?.online != null }
        TodayCard(Icons.Filled.CalendarMonth, if (occ.isEmpty()) s.tdCourtsNone else s.tdCourts(occ.size),
            next + listOfNotNull(s.tdOnline(online).takeIf { online > 0 })) { c.openCourtsPlan() }
        shown++
    }
    if (ClubFeature.PAYMENTS in f) {
        val (cents, people) = Fees.open(book)
        if (cents > 0) { TodayCard(Icons.Filled.Payments, s.tdToCollect(Fees.money(cents, lang.locale)), listOf(s.tdPeople(people))) { c.openPayments() }; shown++ }
    }
    if (ClubFeature.MEMBERS in f) {
        val issues = players.filter { it.member != null }.map { it to Members.issues(it, now) }.filter { it.second.isNotEmpty() }
        if (issues.isNotEmpty()) {
            val blocking = issues.count { (_, l) -> l.any { Members.blocking(it) } }
            TodayCard(Icons.Filled.CardMembership, s.tdMembers(issues.size), listOf(issues.take(3).joinToString(", ") { it.first.name }) + listOfNotNull(s.tdBlocking(blocking).takeIf { blocking > 0 }),
                alert = blocking > 0) { c.openMembers() }
            shown++
        }
    }
    if (ClubFeature.TOURNAMENTS in f) {
        val matches = tournaments.flatMap { t -> t.matches.filter { m -> m.at != null && m.at in today until tomorrow && m.winner == null && !m.bye }.map { t to it } }.sortedBy { it.second.at }
        if (matches.isNotEmpty()) {
            TodayCard(Icons.Filled.EmojiEvents, s.tdMatches(matches.size), matches.take(3).map { (t, m) -> listOf(hm(m.at!!), m.court, t.name).filter { it.isNotBlank() }.joinToString(" · ") }) {
                if (matches.map { it.first.id }.distinct().size == 1) c.openClubTournament(matches.first().first.id) else c.openClubTournaments()
            }
            shown++
        }
    }
    if (ClubFeature.SCHOOL in f) {
        val lessons = courses.flatMap { co -> Courses.lessons(co).filter { it.start in today until tomorrow && it.key !in co.cancelled }.map { co to it } }.sortedBy { it.second.start }
        if (lessons.isNotEmpty()) {
            TodayCard(Icons.Filled.School, s.tdLessons(lessons.size), lessons.take(3).map { (co, l) -> listOf(hm(l.start), co.name, l.court).filter { it.isNotBlank() }.joinToString(" · ") }) {
                if (lessons.map { it.first.id }.distinct().size == 1) c.openCourse(lessons.first().first.id) else c.openSchool()
            }
            shown++
        }
    }
    if (ClubFeature.TEAMS in f) {
        val week = teams.flatMap { t -> t.fixtures.filter { it.at in today until CourtPlans.addDays(today, 7) }.map { t to it } }.sortedBy { it.second.at }
        if (week.isNotEmpty()) {
            val (t0, f0) = week.first()
            TodayCard(Icons.Filled.Groups, s.tdFixtures(week.size), week.take(3).map { (t, fx) -> "${shortDate(fx.at, lang)} · ${t.name} – ${fx.opponent}" }) { c.openFixture(t0.id, f0.id) }
            shown++
        }
    }
    if (ClubFeature.LADDER in f) {
        val open = ladders.flatMap { l -> l.challenges.filter { it.open }.map { l to it } }
        val late = open.count { it.second.deadline < now }
        if (open.isNotEmpty()) {
            TodayCard(Icons.Filled.Leaderboard, s.tdChallenges(open.size), listOfNotNull(s.tdLate(late).takeIf { late > 0 }), alert = late > 0) {
                if (open.map { it.first.id }.distinct().size == 1) c.openLadder(open.first().first.id) else c.openLadders()
            }
            shown++
        }
    }
    if (shown == 0) Text(s.tdNothing, color = TsmColors.TextDim)
}

/** Le prenotazioni e le disdette online da leggere, le più nuove in alto: toccandone una si apre il planning di quel giorno. */
@Composable
private fun OnlineNews(c: MatchController, news: List<com.tennis.scoremanager.data.OnlineNotice>) {
    val s = LocalStrings.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(TsmColors.Surface).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.NotificationsActive, null, tint = TsmColors.Ball)
            Text(s.tdNewsTitle(news.size), color = TsmColors.TextMain, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f).padding(start = 12.dp))
        }
        Text(s.tdNewsHint, color = TsmColors.TextDim, fontSize = 12.sp)
        for (n in news.take(8)) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TsmColors.SurfaceHigh).clickable { c.openOnlineNotice(n) }.padding(start = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (n.cancelled) Icons.Filled.EventBusy else Icons.Filled.EventAvailable, null, tint = if (n.cancelled) TsmColors.Orange else TsmColors.Ok)
                Column(Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 8.dp)) {
                    Text(if (n.cancelled) s.tdNewsCancelled else s.tdNewsBooked, color = if (n.cancelled) TsmColors.Orange else TsmColors.Ok, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(n.label, color = TsmColors.TextMain, fontSize = 14.sp)
                }
                IconButton(onClick = { c.readOnlineNotice(n.id) }) { Icon(Icons.Filled.Check, s.tdNewsRead, tint = TsmColors.TextDim) }
            }
        }
        if (news.size > 1) TextButton(onClick = { c.readOnlineNotice(null) }) { Text(s.tdNewsReadAll) }
    }
}

