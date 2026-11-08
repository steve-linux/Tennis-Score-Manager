// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import com.tennis.scoremanager.ui.Strings
import com.tennis.scoremanager.ui.directionName
import com.tennis.scoremanager.ui.serveSideName
import com.tennis.scoremanager.ui.serveTargetName
import com.tennis.scoremanager.ui.strokeLabel
import com.tennis.scoremanager.ui.wingName
import com.tennis.scoremanager.video.ServeClipAnalysis
import java.io.File

/**
 * Il report da dare all'allievo (PDF o immagine): colpi con la percentuale dentro e il suo andamento, movimento
 * (ultima sessione analizzata contro quella prima), un colpo dentro e uno fuori con le immagini dei momenti chiave,
 * partite e note. Qui solo i dati; il disegno è in [StudentReportPdf] e [StudentReportImage].
 */
object StudentReport {
    /** Un colpo nel periodo: sessioni, colpi, percentuale dentro, andamento (% per sessione) e righe per direzione o bersaglio. */
    data class StrokeBlock(
        val stroke: TrainedStroke, val sessions: Int, val shots: Int, val pct: Int?, val trend: List<Int>,
        val rows: List<Pair<String, ServeTraining.Count>>,
    )

    /** Un colpo d'esempio: le tre immagini dei momenti chiave (etichetta, file). */
    data class Example(val stroke: TrainedStroke, val inPlay: Boolean, val at: Long, val images: List<Pair<String, File>>)

    data class Match(val at: Long, val won: Boolean, val pointsPct: Int, val opponents: String)

    data class Report(
        val name: String, val fullName: String, val coach: String?, val club: String?, val from: Long?, val to: Long,
        val strokes: List<StrokeBlock>, val movement: List<Pair<TrainedStroke, List<Students.Change>>>,
        val examples: List<Example>, val won: Int, val lost: Int, val rating: Int?, val matches: List<Match>, val note: String,
    ) {
        val empty: Boolean get() = strokes.isEmpty() && matches.isEmpty() && note.isBlank()
    }

    /** Le righe del riepilogo di un colpo: bersaglio e lato col servizio, direzione con gli altri, dritto/rovescio con la volée. */
    fun rows(s: Strings, stroke: TrainedStroke, serves: List<ServeAttempt>): List<Pair<String, ServeTraining.Count>> {
        val b = ServeTraining.breakdown(serves, stroke)
        return b.bySide.map { serveSideName(s, it.key) to it.value } + b.byTarget.map { serveTargetName(s, it.key) to it.value } +
            b.byDirection.map { directionName(s, it.key) to it.value } + b.byWing.map { wingName(s, it.key) to it.value }
    }

    /**
     * Raccoglie il report di [p]: sessioni dal giorno [from] (null = tutte), partite dello stesso periodo. [labels]: le
     * etichette delle tre immagini per colpo.
     */
    fun build(
        storage: Storage, s: Strings, p: PlayerProfile, sessions: List<ServeSession>, matches: List<PlayerStats.MatchLine>,
        rating: Int?, coach: String?, club: String?, from: Long?, now: Long,
    ): Report {
        val mine = sessions.filter { it.student == p.id && (from == null || it.startedAt >= from) && it.serves.isNotEmpty() }.sortedBy { it.startedAt }
        val strokes = TrainedStroke.entries.mapNotNull { t ->
            val list = mine.filter { it.stroke == t }
            if (list.isEmpty()) return@mapNotNull null
            val all = list.flatMap { it.serves }
            StrokeBlock(t, list.size, all.size, ServeTraining.count(all).pct, list.mapNotNull { ServeTraining.count(it.serves).pct }, rows(s, t, all))
        }
        val movement = TrainedStroke.entries.mapNotNull { t ->
            val analysed = mine.filter { it.stroke == t }.sortedByDescending { it.startedAt }.mapNotNull { StudentAnalysis.means(storage, it) }.take(2)
            val last = analysed.getOrNull(0) ?: return@mapNotNull null
            t to StudentAnalysis.changes(s, last, analysed.getOrNull(1))
        }
        // Per colpo l'ultimo colpo dentro e l'ultimo fuori che hanno le immagini dell'analisi.
        val examples = TrainedStroke.entries.flatMap { t ->
            val names = if (t == TrainedStroke.SERVE) listOf(s.poseTrophy to ServeClipAnalysis.TROPHY, s.poseKnee to ServeClipAnalysis.KNEE, s.poseContact to ServeClipAnalysis.CONTACT)
            else listOf(s.posePrep to ServeClipAnalysis.PREP, s.poseContact to ServeClipAnalysis.CONTACT, s.poseFinish to ServeClipAnalysis.FINISH)
            listOf(true, false).mapNotNull { inPlay ->
                mine.filter { it.stroke == t }.sortedByDescending { it.startedAt }.asSequence().flatMap { sess ->
                    val dir = storage.serveClipsDir(sess)
                    sess.serves.reversed().asSequence().filter { it.inPlay == inPlay && it.clip != null }.map { a -> a to File(dir, a.clip!!) }
                }.mapNotNull { (a, d) ->
                    val imgs = names.map { (label, file) -> label to File(d, file) }.filter { it.second.exists() }
                    if (imgs.size == names.size) Example(t, inPlay, a.at, imgs) else null
                }.firstOrNull()
            }
        }
        val inPeriod = matches.filter { from == null || (it.rec.endedAt ?: it.rec.updatedAt) >= from }
        val keys = setOf(p.id) + p.aliases
        val list = inPeriod.reversed().take(5).map { m ->
            val side = com.tennis.scoremanager.model.Side.entries.firstOrNull { side -> PlayerStats.whos(m.rec.setup, side).any { it.key in keys } }
            Match(m.rec.endedAt ?: m.rec.updatedAt, m.won, m.pointsWonPct, side?.let { PlayerStats.names(m.rec.setup, it.other).joinToString(" / ") }.orEmpty())
        }
        return Report(
            // Con "tutto" il periodo parte dalla prima sessione o partita.
            name = p.name, fullName = p.fullName, coach = coach, club = club,
            from = from ?: (mine.map { it.startedAt } + inPeriod.map { it.rec.endedAt ?: it.rec.updatedAt }).minOrNull(), to = now,
            strokes = strokes, movement = movement, examples = examples,
            won = inPeriod.count { it.won }, lost = inPeriod.count { !it.won }, rating = rating, matches = list, note = p.note,
        )
    }

    /** "Dritto · 3 sessioni · 42 colpi · 71%". */
    fun strokeLine(s: Strings, b: StrokeBlock): String =
        listOfNotNull(strokeLabel(s, b.stroke), s.studentSessions(b.sessions), s.studentShots(b.shots), b.pct?.let { "$it%" }).joinToString(" · ")
}
