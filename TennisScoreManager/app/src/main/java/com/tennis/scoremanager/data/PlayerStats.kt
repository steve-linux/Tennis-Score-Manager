// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import com.tennis.scoremanager.model.Side
import java.util.Locale

/**
 * Giocatori dello storico, riconosciuti dal nome scritto nella pagina 1 (maiuscole e spazi non contano).
 * I nomi lasciati vuoti ("Giocatore 1") non diventano giocatori. Nel doppio ogni giocatore conta per sé:
 * vince o perde con la sua coppia e ha come avversari i due dell'altra.
 */
object PlayerStats {

    /** Una partita dello storico col suo vincitore (null = non finita). */
    data class Played(val rec: MatchRecord, val winner: Side?)

    data class Player(val key: String, val name: String, val played: Int, val won: Int) {
        val lost: Int get() = played - won
    }

    /** Scontri diretti con un avversario: partite vinte e perse contro di lui. */
    data class Rival(val key: String, val name: String, val won: Int, val lost: Int)

    fun key(name: String): String = name.trim().lowercase(Locale.ROOT)

    /** Nomi scritti di un lato: uno nel singolare, fino a due nel doppio. */
    fun names(setup: SetupData, side: Side): List<String> {
        val all = if (side == Side.P1) listOf(setup.p1a, setup.p1b) else listOf(setup.p2a, setup.p2b)
        return all.take(if (setup.doubles) 2 else 1).map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun sideOf(rec: MatchRecord, key: String): Side? =
        Side.entries.firstOrNull { side -> names(rec.setup, side).any { key(it) == key } }

    /** Giocatori dalle partite finite, dal più presente; il nome è scritto come nella partita più recente. */
    fun players(matches: List<Played>): List<Player> {
        val out = LinkedHashMap<String, Player>()
        for (m in matches.filter { it.winner != null }.sortedByDescending { it.rec.endedAt ?: it.rec.updatedAt }) {
            for (side in Side.entries) {
                for (n in names(m.rec.setup, side)) {
                    val k = key(n)
                    val p = out[k] ?: Player(k, n, 0, 0)
                    out[k] = p.copy(played = p.played + 1, won = p.won + if (m.winner == side) 1 else 0)
                }
            }
        }
        return out.values.sortedWith(compareByDescending<Player> { it.played }.thenBy { it.name.lowercase(Locale.ROOT) })
    }

    fun matchesOf(matches: List<Played>, key: String): List<Played> = matches.filter { sideOf(it.rec, key) != null }

    /** Una partita di un giocatore per l'andamento: [detail] = statistiche della sua parte se la partita le ha. */
    data class MatchLine(val rec: MatchRecord, val won: Boolean, val pointsWonPct: Int, val detail: MatchStats.SideTotals?)

    /** Partite finite di [key], dalla più vecchia, con la percentuale di punti vinti e le statistiche dettagliate. */
    fun lines(matches: List<Played>, key: String): List<MatchLine> =
        matchesOf(matches, key).filter { it.winner != null }.sortedBy { it.rec.endedAt ?: it.rec.updatedAt }.map { m ->
            val side = sideOf(m.rec, key)!!
            val pts = MatchStats.points(m.rec)
            val t = MatchStats.totals(pts).getValue(side)
            MatchLine(
                rec = m.rec,
                won = m.winner == side,
                pointsWonPct = if (t.points > 0) Math.round(t.pointsWon * 100.0 / t.points).toInt() else 0,
                detail = t.takeIf { MatchStats.hasDetail(m.rec, pts) },
            )
        }

    /** Avversari di [key] con vittorie e sconfitte, da chi ci ha giocato di più. */
    fun rivals(matches: List<Played>, key: String): List<Rival> {
        val out = LinkedHashMap<String, Rival>()
        for (m in matchesOf(matches, key).filter { it.winner != null }.sortedByDescending { it.rec.endedAt ?: it.rec.updatedAt }) {
            val me = sideOf(m.rec, key) ?: continue
            for (n in names(m.rec.setup, me.other)) {
                val k = key(n)
                val r = out[k] ?: Rival(k, n, 0, 0)
                out[k] = if (m.winner == me) r.copy(won = r.won + 1) else r.copy(lost = r.lost + 1)
            }
        }
        return out.values.sortedWith(compareByDescending<Rival> { it.won + it.lost }.thenBy { it.name.lowercase(Locale.ROOT) })
    }
}
