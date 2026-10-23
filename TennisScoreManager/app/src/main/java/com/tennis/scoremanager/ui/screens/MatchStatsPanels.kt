// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tennis.scoremanager.ble.BandProtocol
import com.tennis.scoremanager.data.HeartStats
import com.tennis.scoremanager.data.HeartTrack
import com.tennis.scoremanager.data.MatchRecord
import com.tennis.scoremanager.data.MatchStats
import com.tennis.scoremanager.data.Names
import com.tennis.scoremanager.data.Reports
import com.tennis.scoremanager.model.Side
import com.tennis.scoremanager.model.TiebreakKind
import com.tennis.scoremanager.ui.LocalStrings
import com.tennis.scoremanager.ui.SegOption
import com.tennis.scoremanager.ui.Segmented
import com.tennis.scoremanager.ui.Strings
import com.tennis.scoremanager.ui.TsmColors

/** Come si mostra un dato: percentuale con "vinti/giocati", rapporto "5/9" o numero semplice. */
private enum class StatKind { PERCENT, RATIO, COUNT }

private data class Stat(val label: String, val a: Int, val aOf: Int, val b: Int, val bOf: Int, val kind: StatKind) {
    constructor(label: String, a: Int, b: Int) : this(label, a, 0, b, 0, StatKind.COUNT)
}

private fun pct(v: Int, of: Int) = if (of > 0) Math.round(v * 100.0 / of).toInt() else 0

/** Testo del valore e lunghezza della barra (0..1) per un lato. */
private fun shown(st: Stat, v: Int, of: Int, other: Int): Pair<String, Float> = when (st.kind) {
    StatKind.PERCENT -> "${pct(v, of)}% ($v/$of)" to (if (of > 0) v.toFloat() / of else 0f)
    StatKind.RATIO -> "$v/$of" to (if (of > 0) v.toFloat() / of else 0f)
    StatKind.COUNT -> "$v" to (if (maxOf(v, other) > 0) v.toFloat() / maxOf(v, other) else 0f)
}

/**
 * Statistiche come nelle pagine dei risultati: per la partita o per set, valori dei due giocatori ai lati e barre
 * che partono dal centro (colorata quella di chi è avanti). Solo dati che l'app conosce davvero.
 */
@Composable
fun MatchStatsTab(rec: MatchRecord, names: Names) {
    val s = LocalStrings.current
    val pts = remember(rec) { MatchStats.points(rec) }
    val sets = (pts.maxOfOrNull { it.set } ?: -1) + 1
    var period by remember(rec.id) { mutableStateOf<Int?>(null) }
    val t = MatchStats.totals(pts, period)
    val a = t.getValue(Side.P1)
    val b = t.getValue(Side.P2)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (sets > 1) {
            Segmented(
                listOf(SegOption(s.periodMatch)) + (1..sets).map { SegOption(s.periodSet(it)) },
                selected = period?.plus(1) ?: 0,
                onSelect = { period = if (it == 0) null else it - 1 },
            )
        }
        NamesHeader(names)
        val detail = remember(rec) { MatchStats.hasDetail(rec, pts) }
        StatSection(s.statServe, (if (detail) listOf(
            Stat(s.statAces, a.aces, b.aces),
            Stat(s.statDoubleFaults, a.doubleFaults, b.doubleFaults),
            Stat(s.statFirstServeIn, a.firstServesIn, a.servePoints, b.firstServesIn, b.servePoints, StatKind.PERCENT),
            Stat(s.statFirstServeWon, a.firstServePointsWon, a.firstServesIn, b.firstServePointsWon, b.firstServesIn, StatKind.PERCENT),
            Stat(s.statSecondServeWon, a.secondServePointsWon, a.secondServes, b.secondServePointsWon, b.secondServes, StatKind.PERCENT),
        ) else emptyList()) + listOf(
            Stat(s.statServicePoints, a.servePointsWon, a.servePoints, b.servePointsWon, b.servePoints, StatKind.PERCENT),
            Stat(s.statServiceGames, a.serviceGamesWon, a.serviceGames, b.serviceGamesWon, b.serviceGames, StatKind.PERCENT),
            Stat(s.statBreakSaved, a.breakPointsSaved, a.breakPointsFaced, b.breakPointsSaved, b.breakPointsFaced, StatKind.RATIO),
        ))
        StatSection(s.statReturn, listOf(
            Stat(s.statReturnPoints, a.returnPointsWon, a.returnPoints, b.returnPointsWon, b.returnPoints, StatKind.PERCENT),
            Stat(s.statReturnGames, a.returnGamesWon, a.returnGames, b.returnGamesWon, b.returnGames, StatKind.PERCENT),
            Stat(s.statBreakWon, a.breakPointsWon, a.breakPoints, b.breakPointsWon, b.breakPoints, StatKind.RATIO),
        ))
        StatSection(s.statPoints, listOf(
            Stat(s.statTotalPoints, a.pointsWon, a.points, b.pointsWon, b.points, StatKind.PERCENT),
        ) + (if (detail) listOf(
            Stat(s.statWinners, a.winners, b.winners),
            Stat(s.statErrors, a.errors, b.errors),
        ) + (if (a.unforcedErrors + b.unforcedErrors + a.forcedErrors + b.forcedErrors > 0) listOf(
            Stat(s.statUnforcedErrors, a.unforcedErrors, b.unforcedErrors),
            Stat(s.statForcedErrors, a.forcedErrors, b.forcedErrors),
        ) else emptyList()) else emptyList()) + listOf(
            Stat(s.statStreak, a.maxStreak, b.maxStreak),
            Stat(s.statLast10, a.last10, b.last10),
            Stat(s.statMatchPointsSaved, a.matchPointsSaved, b.matchPointsSaved),
            Stat(s.statGamePointsWon, a.gamePointsWon, a.gamePoints, b.gamePointsWon, b.gamePoints, StatKind.RATIO),
            Stat(s.statSetPointsWon, a.setPointsWon, a.setPoints, b.setPointsWon, b.setPoints, StatKind.RATIO),
            Stat(s.statMatchPointsWon, a.matchPointsWon, a.matchPoints, b.matchPointsWon, b.matchPoints, StatKind.RATIO),
        ))
        StatSection(s.statGames, listOf(
            Stat(s.statTotalGames, a.gamesWon, a.games, b.gamesWon, b.games, StatKind.PERCENT),
            Stat(s.statLoveGames, a.loveGames, b.loveGames),
            Stat(s.statBestGameRun, a.bestGameRun, b.bestGameRun),
            Stat(s.statSetsFromBreakDown, a.setsFromBreakDown, b.setsFromBreakDown),
        ))
        // Tempi: durata dei set e game più lungo, dalle ore dei punti (solo sulla partita intera).
        if (period == null) {
            val durations = remember(rec) { MatchStats.setDurations(pts) }
            val longest = remember(rec) { MatchStats.longestGame(pts) }
            if (durations.any { it != null } || longest != null) {
                StatCard(s.statTimes) {
                    if (durations.any { it != null }) {
                        Text("${s.statSetDurations}: " + durations.joinToString(" · ") { d -> d?.let { minutes(it) } ?: "–" },
                            color = TsmColors.TextMain, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                    longest?.let { (ms, n) ->
                        Text("${s.statLongestGame}: ${s.longestGameValue(minutes(ms), n)}", color = TsmColors.TextMain, fontSize = 13.sp,
                            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
        // Colpi per tipo e velocità: i braccialetti li contano sulla partita intera, non per set.
        if (period == null) strokeStats(rec, s)?.let { StatSection(s.strokes, it) }
        // Battito delle fasce cardio: sulla partita intera, con il grafico nel tempo.
        if (period == null) heartStats(rec, s)?.let { rows ->
            StatSection(s.heartStat, rows)
            StatCard(s.heartChart) { HeartChart(rec.heart) }
            StatCard(s.heartZones) { HeartZones(rec.heart) { names.short(it) } }
        }
        MatchStats.rally(pts, period)?.let { r ->
            StatCard(s.strokesPerPoint) {
                Text(Reports.rallyLine(r, s, rec.options.lang), color = TsmColors.TextMain, fontSize = 13.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth())
            }
        }
        Text(if (detail) s.statsHintDetailed else s.statsHint, color = TsmColors.TextDim, fontSize = 12.sp)
    }
}

/** "38'" oppure "1 h 05'". */
private fun minutes(ms: Long): String {
    val m = Math.round(ms / 60_000.0).toInt()
    return if (m >= 60) "${m / 60} h ${"%02d".format(m % 60)}'" else "$m'"
}

private fun strokeStats(rec: MatchRecord, s: Strings): List<Stat>? {
    val a = rec.strokes[Side.P1]
    val b = rec.strokes[Side.P2]
    if ((a?.total ?: 0) + (b?.total ?: 0) == 0) return null
    fun avg(st: com.tennis.scoremanager.data.StrokeStats?): Int {
        val n = st?.counts?.sum() ?: 0
        val sum = st?.gyroSum?.sum() ?: 0L
        return if (n > 0 && sum > 0) (sum / n).toInt() else 0
    }
    return listOf(Stat(s.statStrokesTotal, a?.total ?: 0, b?.total ?: 0)) +
        (0 until BandProtocol.STROKE_COUNT).map { Stat(s.strokeName(it), a?.count(it) ?: 0, b?.count(it) ?: 0) } +
        listOfNotNull(Stat("${s.statAvgWristSpeed} (°/s)", avg(a), avg(b)).takeIf { it.a > 0 || it.b > 0 })
}

private fun heartStats(rec: MatchRecord, s: Strings): List<Stat>? {
    val a = HeartStats.summary(rec.heart[Side.P1])
    val b = HeartStats.summary(rec.heart[Side.P2])
    if (a == null && b == null) return null
    return listOfNotNull(
        Stat("${s.heartAvg} (bpm)", a?.avg ?: 0, b?.avg ?: 0),
        Stat("${s.heartMax} (bpm)", a?.max ?: 0, b?.max ?: 0),
        Stat("${s.heartCalories} (kcal)", a?.kcal ?: 0, b?.kcal ?: 0).takeIf { it.a > 0 || it.b > 0 },
    )
}

/** Battito nel tempo, una linea per lato nel suo colore; i buchi (pause, fascia staccata) interrompono la linea. */
@Composable
internal fun HeartChart(heart: Map<Side, HeartTrack>) {
    val tracks = Side.entries.mapNotNull { side -> heart[side]?.takeIf { it.size > 1 }?.let { side to it } }
    if (tracks.isEmpty()) return
    val all = tracks.flatMap { (_, t) -> t.bpm.take(t.size) }
    val lo = (all.min() - 5).coerceAtLeast(30)
    val hi = all.max() + 5
    val end = tracks.maxOf { (_, t) -> t.t[t.size - 1] }.coerceAtLeast(1)
    Row(Modifier.fillMaxWidth().height(140.dp)) {
        Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
            Text("$hi", color = TsmColors.TextDim, fontSize = 10.sp)
            Text("$lo", color = TsmColors.TextDim, fontSize = 10.sp)
        }
        Spacer(Modifier.width(6.dp))
        Canvas(Modifier.weight(1f).fillMaxHeight()) {
            fun x(sec: Int) = sec.toFloat() / end * size.width
            fun y(bpm: Int) = size.height - (bpm - lo).toFloat() / (hi - lo) * size.height
            for ((side, t) in tracks) {
                val path = Path()
                for (i in 0 until t.size) {
                    val gap = i == 0 || t.t[i] - t.t[i - 1] > 2 * HeartStats.STEP_S
                    if (gap) path.moveTo(x(t.t[i]), y(t.bpm[i])) else path.lineTo(x(t.t[i]), y(t.bpm[i]))
                }
                drawPath(path, TsmColors.player(side), style = Stroke(width = 2.dp.toPx()))
            }
        }
    }
    Text(Reports.duration(end * 1000L), color = TsmColors.TextDim, fontSize = 10.sp, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
}

/** Dalla zona 1 alla 5: dal grigio al rosso. */
private val ZoneColors = listOf(Color(0xFF9AA8BD), Color(0xFF4CAF50), Color(0xFFC6F432), Color(0xFFFF9800), Color(0xFFFF3B30))

/** Una torta per lato con il tempo in ciascuna zona, e sotto la legenda con le percentuali dei due lati. */
@Composable
internal fun HeartZones(heart: Map<Side, HeartTrack>, label: (Side) -> String) {
    val s = LocalStrings.current
    val zones = Side.entries.associateWith { side ->
        val t = heart[side]
        t?.profile?.let { HeartStats.zoneSeconds(t, it.age) }?.takeIf { it.sum() > 0 }
    }
    if (zones.values.all { it == null }) {
        Text(s.heartZonesHint, color = TsmColors.TextDim, fontSize = 12.sp)
        return
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        for (side in Side.entries) {
            val z = zones[side] ?: continue
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Canvas(Modifier.size(110.dp)) {
                    val total = z.sum().toFloat()
                    var start = -90f
                    val w = 22.dp.toPx()
                    for (i in z.indices) {
                        val sweep = z[i] / total * 360f
                        if (sweep > 0f) {
                            drawArc(ZoneColors[i], start, sweep, useCenter = false, style = Stroke(width = w),
                                topLeft = androidx.compose.ui.geometry.Offset(w / 2, w / 2),
                                size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w))
                        }
                        start += sweep
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(label(side), color = TsmColors.player(side), fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
        }
    }
    for (i in 0 until HeartStats.ZONES) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(ZoneColors[i]))
            Spacer(Modifier.width(8.dp))
            Text(s.zoneName(i), color = TsmColors.TextMain, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text(
                Side.entries.mapNotNull { side -> zones[side]?.let { z -> "${s.playerTag(side.ordinal + 1)} ${pct(z[i], z.sum())}%" } }
                    .joinToString(" · "),
                color = TsmColors.TextDim, fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun NamesHeader(names: Names) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(names.short(Side.P1), color = TsmColors.player(Side.P1), fontWeight = FontWeight.Bold, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text(names.short(Side.P2), color = TsmColors.player(Side.P2), fontWeight = FontWeight.Bold, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
    }
}

@Composable
internal fun StatCard(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(TsmColors.Surface).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title.uppercase(), color = TsmColors.TextDim, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        content()
    }
}

@Composable
private fun StatSection(title: String, rows: List<Stat>) = StatCard(title) { rows.forEach { StatBar(it) } }

@Composable
private fun StatBar(st: Stat) {
    val (ta, fa) = shown(st, st.a, st.aOf, st.b)
    val (tb, fb) = shown(st, st.b, st.bOf, st.a)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(ta, color = TsmColors.TextMain, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(st.label, color = TsmColors.TextMain, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                modifier = Modifier.weight(2f))
            Text(tb, color = TsmColors.TextMain, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End,
                modifier = Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth().height(6.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            HalfBar(fa, if (fa >= fb && fa > 0) TsmColors.player(Side.P1) else TsmColors.TextDim.copy(alpha = 0.5f), fromRight = true,
                modifier = Modifier.weight(1f))
            HalfBar(fb, if (fb >= fa && fb > 0) TsmColors.player(Side.P2) else TsmColors.TextDim.copy(alpha = 0.5f), fromRight = false,
                modifier = Modifier.weight(1f))
        }
    }
}

/** Mezza barra che parte dal centro: a sinistra cresce verso sinistra ([fromRight]), a destra verso destra. */
@Composable
private fun HalfBar(fraction: Float, color: Color, fromRight: Boolean, modifier: Modifier) {
    Box(
        modifier.height(6.dp).clip(RoundedCornerShape(3.dp)).background(TsmColors.SurfaceHigh),
        contentAlignment = if (fromRight) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(6.dp).clip(RoundedCornerShape(3.dp)).background(color))
    }
}

/** Cronologia punto per punto, set per set: chi serve, punteggio in game, servizi persi e palle importanti. */
@Composable
fun MatchChronologyTab(rec: MatchRecord, names: Names) {
    val s = LocalStrings.current
    val games = remember(rec) { MatchStats.chronology(MatchStats.points(rec)) }
    val sets = (games.maxOfOrNull { it.set } ?: -1) + 1
    var set by remember(rec.id) { mutableStateOf(0) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (sets > 1) {
            Segmented((1..sets).map { SegOption(s.periodSet(it)) }, selected = set.coerceIn(0, sets - 1), onSelect = { set = it })
        }
        NamesHeader(names)
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(TsmColors.Surface).padding(vertical = 4.dp),
        ) {
            games.filter { it.set == set }.forEachIndexed { i, g ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(TsmColors.Outline.copy(alpha = 0.5f)))
                GameRow(g, s)
            }
        }
    }
}

@Composable
private fun GameRow(g: MatchStats.GameLog, s: Strings) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Punteggio sempre al centro; pallina e "servizio perso" dalla parte di chi serviva.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                if (g.broken && g.server == Side.P1) Badge(s.serveLost)
                ServeBall(g.server == Side.P1)
            }
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = if (g.winner == Side.P1) TsmColors.player(Side.P1) else TsmColors.TextMain)) { append("${g.games1}") }
                    append(" - ")
                    withStyle(SpanStyle(color = if (g.winner == Side.P2) TsmColors.player(Side.P2) else TsmColors.TextMain)) { append("${g.games2}") }
                    if (g.tiebreak == TiebreakKind.SET && g.tb1 != null && g.tb2 != null) append("  (${g.tb1}-${g.tb2})")
                },
                color = TsmColors.TextMain, fontWeight = FontWeight.Black, fontSize = 16.sp,
                modifier = Modifier.padding(horizontal = 10.dp),
            )
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                ServeBall(g.server == Side.P2)
                if (g.broken && g.server == Side.P2) Badge(s.serveLost)
            }
        }
        Text(
            buildAnnotatedString {
                if (g.tiebreak != TiebreakKind.NONE) {
                    withStyle(SpanStyle(color = TsmColors.Orange, fontWeight = FontWeight.Bold)) { append(s.chronoTiebreak.uppercase()) }
                    append("  ")
                }
                g.steps.forEachIndexed { i, st ->
                    if (i > 0) append(", ")
                    append("${st.p1}:${st.p2}")
                    st.marker?.let { m ->
                        val (label, color) = when (m) {
                            MatchStats.Marker.BREAK_POINT -> s.markerBreak to TsmColors.Outline
                            MatchStats.Marker.SET_POINT -> s.markerSet to Color(0xFF1E88E5)
                            MatchStats.Marker.MATCH_POINT -> s.markerMatch to TsmColors.Danger
                        }
                        append(" ")
                        withStyle(SpanStyle(background = color, color = TsmColors.TextMain, fontWeight = FontWeight.Bold)) { append(" $label ") }
                    }
                }
                if (g.winner != null) {
                    if (g.steps.isNotEmpty()) append(", ")
                    val end = when {
                        g.closesMatch -> s.chronoMatch
                        g.closesSet -> s.chronoSet
                        g.broken -> s.chronoBreak
                        else -> s.chronoGame
                    }
                    withStyle(SpanStyle(color = TsmColors.player(g.winner), fontWeight = FontWeight.Bold)) { append(end) }
                }
            },
            color = TsmColors.TextDim, fontSize = 11.sp, lineHeight = 16.sp, textAlign = TextAlign.Center,
        )
    }
}

/** Pallina di chi serve il game, come sul braccialetto (lo spazio resta anche quando non c'è). */
@Composable
private fun ServeBall(visible: Boolean) {
    Box(Modifier.size(12.dp).clip(CircleShape).background(if (visible) TsmColors.Ball else Color.Transparent))
}

@Composable
private fun Badge(text: String) {
    Text(
        text.uppercase(), color = TsmColors.TextMain, fontSize = 9.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(TsmColors.Danger).padding(horizontal = 5.dp, vertical = 1.dp),
    )
}
