// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.tv

import com.tennis.scoremanager.CountdownKind
import com.tennis.scoremanager.LiveMatch
import com.tennis.scoremanager.Screen
import com.tennis.scoremanager.data.Names
import com.tennis.scoremanager.data.SetupData
import com.tennis.scoremanager.model.Lang
import com.tennis.scoremanager.model.Side
import com.tennis.scoremanager.model.TiebreakKind
import com.tennis.scoremanager.ui.Strings
import kotlinx.serialization.Serializable

/** Tabellone su TV: impostazioni del telefono dell'arbitro (non della singola partita). */
@Serializable
data class TvSettings(
    val enabled: Boolean = false,
    /** Riga facoltativa in basso (torneo, circolo...); vuota = circolo e campo della pagina 1. */
    val title: String = "",
    /** Colori dei due giocatori sul tabellone (#RRGGBB). */
    val color1: String = TvColors.YELLOW,
    val color2: String = TvColors.RED,
    val showClock: Boolean = true,
    /** Cronometro dei 25" di servizio e delle pause. */
    val showTimers: Boolean = true,
    /** Punteggi dei set conclusi, es. [6-4] [3-6]. */
    val showSets: Boolean = true,
    /** Palla break, set point, match point, cambio campo... */
    val showMessages: Boolean = true,
    /** Pallina accanto a chi serve. */
    val showServe: Boolean = true,
    /** Segmenti spenti visibili, come un tabellone a LED vero. */
    val ghostSegments: Boolean = true,
)

object TvColors {
    const val YELLOW = "#FFD600"
    const val RED = "#FF3030"
    /** Tavolozza proposta nelle impostazioni (colori ben visibili su fondo nero). */
    val palette = listOf(YELLOW, RED, "#2F80FF", "#22D65A", "#22D3EE", "#FF9800", "#F5F5F5", "#FF3DCC")
}

/**
 * Quello che il tabellone deve mostrare in un istante. Viaggia in JSON verso la pagina (scoreboard.html):
 * i tempi arrivano come valore più "sta correndo", così la pagina li fa scorrere da sola tra un invio e l'altro.
 * Indici delle liste: 0 = Giocatore 1, 1 = Giocatore 2.
 */
@Serializable
data class TvSnapshot(
    /** Firma per riconoscere il server TSM durante la ricerca. */
    val tsm: Int = 1,
    val seq: Long,
    val lang: String,
    /** idle (nessuna partita), ready (in attesa del via), play, suspended, finished. */
    val phase: String,
    val title: String,
    val players: List<TvPlayer>,
    val server: Int? = null,
    /** Punti del game: "0" "15" "30" "40" "AD" o i punti del tie-break; "" = spento. */
    val points: List<String>,
    val games: List<Int>,
    val sets: List<Int>,
    val done: List<TvSet>,
    /** "", "set" o "match". */
    val tiebreak: String,
    val winner: Int? = null,
    val clockMs: Long,
    val clockRunning: Boolean,
    val countdown: TvCountdown? = null,
    val message: String? = null,
    val show: TvShow,
    val labels: TvLabels,
    /** Moviola mandata al tabellone dall'arbitro (null = niente): copre il punteggio finché non la si toglie. */
    val replay: TvReplay? = null,
    /** Torneo del circolo mandato al tabellone (null = niente): pagine che girano da sole sopra il punteggio. */
    val club: TvClub? = null,
    /** Animazione del cambio palle: un numero nuovo = parte (null = niente). */
    val balls: Long? = null,
)

/**
 * Il torneo sul tabellone: [pages] che si alternano ogni [seconds] secondi (tabellone, gironi, ordine di gioco).
 * [id] cambia quando si manda un altro torneo, così la pagina riparte dalla prima.
 */
@Serializable
data class TvClub(val id: String, val title: String, val subtitle: String, val seconds: Int, val pages: List<TvClubPage>, val labels: TvClubLabels)

/** Una pagina: "draw" ([rounds]), "groups" ([groups]) o "order" ([rows]). */
@Serializable
data class TvClubPage(
    val kind: String,
    val title: String,
    val rounds: List<TvRound> = emptyList(),
    val groups: List<TvGroup> = emptyList(),
    val rows: List<TvOrderRow> = emptyList(),
)

@Serializable
data class TvRound(val name: String, val matches: List<TvDrawMatch>)

/** [win] = 0 (vince a), 1 (vince b), null = da giocare. */
@Serializable
data class TvDrawMatch(val a: String, val b: String, val score: String = "", val win: Int? = null)

@Serializable
data class TvGroup(val name: String, val rows: List<TvStanding>)

@Serializable
data class TvStanding(val pos: Int, val name: String, val won: Int, val played: Int, val sets: String, val games: String, val q: Boolean)

/** Una riga dell'ordine di gioco: ora, campo, i due, turno, punteggio; [done] = giocata. */
@Serializable
data class TvOrderRow(val time: String, val court: String, val a: String, val b: String, val round: String, val score: String = "", val done: Boolean = false, val win: Int? = null)

@Serializable
data class TvClubLabels(val won: String, val sets: String, val games: String, val noMatches: String)

/**
 * Moviola sul tabellone: i pezzi /replay/[id]/0.mp4 ... ([parts]), in loop, a [speed] (1, 0,5, 0,25), con la scritta
 * [label]. Segue i comandi dell'arbitro: a ogni [sync] nuovo il tabellone va al pezzo [part], a [posMs], e si ferma
 * ([paused]) o riparte.
 */
@Serializable
data class TvReplay(
    val id: String,
    val parts: Int,
    val speed: Float,
    val label: String,
    val paused: Boolean = false,
    val part: Int = 0,
    val posMs: Long = 0,
    val sync: Int = 0,
)

@Serializable
data class TvPlayer(
    val name: String,
    val color: String,
    /** Foto dei giocatori del lato (indirizzi sul server del tabellone); vuota = nessuna. */
    val photos: List<String> = emptyList(),
)

@Serializable
data class TvSet(val g1: Int, val g2: Int, val tb1: Int? = null, val tb2: Int? = null, val mtb: Boolean = false)

/** [shot] = i 25" tra un punto e l'altro (in rosso come sul tabellone di riferimento). */
@Serializable
data class TvCountdown(val label: String, val leftMs: Long, val shot: Boolean)

@Serializable
data class TvShow(val clock: Boolean, val timers: Boolean, val sets: Boolean, val messages: Boolean, val serve: Boolean, val ghost: Boolean)

@Serializable
data class TvLabels(
    val vs: String,
    val games: String,
    val set: String,
    val sec: String,
    val waiting: String,
    val ready: String,
    val suspended: String,
    val winner: String,
    val tiebreak: String,
    val matchTiebreak: String,
    val lost: String,
    val fullscreen: String,
    /** Titolo della pagina (document.title). */
    val page: String,
    /** La scritta dell'animazione del cambio palle. */
    val newBalls: String = "",
)

/** Stato dell'app da cui si ricava il tabellone. */
data class TvInput(
    val screen: Screen,
    val setup: SetupData,
    val lang: Lang,
    val firstServer: Side,
    /** Partita in corso, oppure quella appena finita mentre si guarda il riepilogo. */
    val match: LiveMatch?,
    val clockMs: Long,
    val clockRunning: Boolean,
    val countdown: CountdownKind?,
    val countdownLeftMs: Long,
    val message: String?,
    val tv: TvSettings,
    val strings: Strings,
    val seq: Long,
    /** Indirizzi delle foto dei giocatori di ogni lato. */
    val photos: Map<Side, List<String>> = emptyMap(),
    val replay: TvReplay? = null,
    val club: TvClub? = null,
    val balls: Long? = null,
)

object TvSnapshots {

    fun build(i: TvInput): TvSnapshot {
        val s = i.strings
        val m = i.match
        val st = m?.state
        val names = Names(m?.record?.setup ?: i.setup, s)
        val phase = when {
            m == null || st == null -> if (i.screen == Screen.START) "ready" else "idle"
            st.isFinished -> "finished"
            m.record.suspended -> "suspended"
            m.record.startedAt == null -> "ready"
            else -> "play"
        }
        val sides = listOf(Side.P1, Side.P2)
        val points = when {
            st == null -> if (phase == "ready") listOf("0", "0") else listOf("", "")
            st.isFinished -> listOf("", "")
            else -> sides.map { st.pointLabel(it) }
        }
        // A partita finita i game del set in corso sono azzerati: si mostrano quelli dell'ultimo set. Il match tie-break
        // vale come set vinto 1-0, come sui tabelloni a LED (una cifra per i game); i punti, es. [10-8], stanno tra i set conclusi.
        val games = when {
            st == null -> listOf(0, 0)
            st.isFinished -> st.sets.lastOrNull()?.let { set -> sides.map { set.games(it) } } ?: listOf(0, 0)
            else -> sides.map { st.games(it) }
        }
        val server = when {
            phase == "finished" || phase == "idle" -> null
            st != null -> st.server.ordinal
            else -> i.firstServer.ordinal
        }
        val countdown = i.countdown?.takeIf { phase == "play" && i.tv.showTimers }?.let { k ->
            TvCountdown(
                label = when (k) {
                    CountdownKind.SHOT_CLOCK -> s.tvServe
                    CountdownKind.CHANGEOVER -> s.tvChangeover
                    CountdownKind.SET_BREAK -> s.tvSetBreak
                    CountdownKind.TIEBREAK_BREAK -> s.tvTiebreakBreak
                    CountdownKind.MEDICAL -> s.tvMedical
                    CountdownKind.TOILET -> s.tvToilet
                    CountdownKind.WARMUP -> s.tvWarmup
                },
                leftMs = i.countdownLeftMs.coerceAtLeast(0),
                shot = k == CountdownKind.SHOT_CLOCK,
            )
        }
        return TvSnapshot(
            seq = i.seq,
            lang = i.lang.code,
            phase = phase,
            title = i.tv.title.trim().ifEmpty { defaultTitle(m?.record?.setup ?: i.setup, s) },
            players = listOf(
                TvPlayer(names.short(Side.P1), i.tv.color1, i.photos[Side.P1].orEmpty()),
                TvPlayer(names.short(Side.P2), i.tv.color2, i.photos[Side.P2].orEmpty()),
            ),
            server = server,
            points = points,
            games = games,
            sets = sides.map { st?.setsWon(it) ?: 0 },
            done = st?.sets.orEmpty().map { TvSet(it.g1, it.g2, it.tb1, it.tb2, it.matchTiebreak) },
            tiebreak = when (st?.tiebreak) {
                TiebreakKind.SET -> "set"
                TiebreakKind.MATCH -> "match"
                else -> ""
            },
            winner = st?.winner?.ordinal,
            clockMs = if (phase == "idle") 0 else i.clockMs,
            clockRunning = phase == "play" && i.clockRunning,
            countdown = countdown,
            message = i.message?.takeIf { phase == "play" && i.tv.showMessages },
            show = TvShow(i.tv.showClock, i.tv.showTimers, i.tv.showSets, i.tv.showMessages, i.tv.showServe, i.tv.ghostSegments),
            labels = labels(s),
            replay = i.replay,
            club = i.club,
            balls = i.balls,
        )
    }

    /** Testi fissi della pagina; `scoreboard.html` ne ha una copia per lingua (id="texts") da usare prima del primo stato. */
    fun labels(s: Strings) = TvLabels(
        vs = s.tvVs, games = s.tvGames, set = s.tvSet, sec = s.tvSec,
        waiting = s.tvWaiting, ready = s.tvReady, suspended = s.tvSuspended, winner = s.tvWinner,
        tiebreak = s.tvTiebreak, matchTiebreak = s.tvMatchTiebreak, lost = s.tvLost, fullscreen = s.tvFullscreen,
        page = s.tvPageTitle, newBalls = s.newBallsCall,
    )

    /** "Circolo Tennis · Campo 3" dai dati della pagina 1 (un numero da solo diventa "Campo 3"). */
    fun defaultTitle(su: SetupData, s: Strings): String {
        val court = su.court.trim().let { if (it.isNotEmpty() && it.all(Char::isDigit)) "${s.court} $it" else it }
        return listOf(su.club.trim(), court).filter { it.isNotEmpty() }.joinToString(" · ")
    }
}
