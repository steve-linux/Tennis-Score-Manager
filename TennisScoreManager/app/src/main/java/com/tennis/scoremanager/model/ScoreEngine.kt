// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.model

/**
 * Stato della partita in un istante. È immutabile: ogni punto produce un nuovo stato.
 *
 * Il servizio non è memorizzato game per game ma ricavato dal "turno di servizio" nel set:
 * turno = game giocati nel set (+ turni del tie-break). Nei turni pari serve chi ha iniziato il set.
 * Così la stessa formula copre singolare, doppio (rotazione a 4) e tie-break (1 punto, poi 2 a testa).
 */
/** Punti di ciascuno alla terza parità (5-5), quella che con lo star point decide il game. */
private const val STAR_POINT_PTS = 5

data class MatchState(
    val rules: RulesConfig,
    val sets: List<SetScore> = emptyList(),
    val g1: Int = 0,
    val g2: Int = 0,
    /** Punti del game corrente (0,1,2,3,4...) oppure punti del tie-break. */
    val pt1: Int = 0,
    val pt2: Int = 0,
    val tiebreak: TiebreakKind = TiebreakKind.NONE,
    /** Squadra/giocatore che serve il primo game del set corrente. */
    val setStartServer: Side = rules.firstServer,
    /** Doppio: indice (0/1) di chi serve per primo, per squadra, nel set corrente. */
    val order1: Int = rules.firstServerP1,
    val order2: Int = rules.firstServerP2,
    /** Vista arbitro: il Giocatore 1 è sul lato sinistro? */
    val p1Left: Boolean = rules.p1StartsLeft,
    val winner: Side? = null,
    val pointsPlayed: Int = 0,
    /** Chi si è ritirato (null = partita finita sul campo o ancora in corso). */
    val retired: Side? = null,
    /** Il ritiro è una squalifica. */
    val disqualified: Boolean = false,
    /** Doppio: chi risponde da destra in ciascuna squadra nel set in corso (null = non detto). */
    val recv1: Int? = null,
    val recv2: Int? = null,
    /** I richiami fatti finora, per tipo: [Penalty] li conta per sapere cosa comporta il prossimo. */
    val codes1: Int = 0,
    val codes2: Int = 0,
    val times1: Int = 0,
    val times2: Int = 0,
) {
    val isFinished: Boolean get() = winner != null
    val setNumber: Int get() = sets.size + 1
    val inTiebreak: Boolean get() = tiebreak != TiebreakKind.NONE
    val tiebreakTarget: Int get() = if (tiebreak == TiebreakKind.MATCH) 10 else 7

    fun games(side: Side): Int = if (side == Side.P1) g1 else g2
    fun points(side: Side): Int = if (side == Side.P1) pt1 else pt2
    fun setsWon(side: Side): Int = sets.count { !it.partial && it.winner == side }
    fun order(side: Side): Int = if (side == Side.P1) order1 else order2

    /** Doppio: chi risponde adesso nella squadra che riceve (da destra sui punti pari del game); null = non detto. */
    val receiverPlayer: Int?
        get() = (if (receiver == Side.P1) recv1 else recv2)?.let { if ((pt1 + pt2) % 2 == 0) it else 1 - it }
    fun penalties(side: Side, kind: PenaltyKind): Int = when (kind) {
        PenaltyKind.CODE -> if (side == Side.P1) codes1 else codes2
        PenaltyKind.TIME -> if (side == Side.P1) times1 else times2
    }
    fun leftSide(): Side = if (p1Left) Side.P1 else Side.P2

    /** Turno di servizio corrente all'interno del set. */
    val serviceTurn: Int
        get() = if (inTiebreak) g1 + g2 + (pt1 + pt2 + 1) / 2 else g1 + g2

    fun serverOfTurn(turn: Int): Side = if (turn % 2 == 0) setStartServer else setStartServer.other

    /** Doppio: quale giocatore (0/1) della squadra [side] serve al turno [turn]. */
    fun playerOfTurn(turn: Int, side: Side): Int = (order(side) + turn / 2) % 2

    val server: Side get() = serverOfTurn(serviceTurn)
    val receiver: Side get() = server.other

    /** Doppio: indice del giocatore al servizio nella sua squadra (nel singolare è sempre 0). */
    val serverPlayer: Int get() = if (rules.doubles) playerOfTurn(serviceTurn, server) else 0

    /** Punteggio "da tabellone" del game: 0 15 30 40 AD, oppure i punti del tie-break. */
    fun pointLabel(side: Side): String {
        if (inTiebreak) return points(side).toString()
        val p = points(side)
        val o = points(side.other)
        if (p >= 3 && o >= 3) {
            return when {
                p == o -> "40"
                p > o -> "AD"
                else -> "40"
            }
        }
        return POINT_LABELS[p.coerceAtMost(3)]
    }

    val isDeuce: Boolean get() = !inTiebreak && pt1 >= 3 && pt1 == pt2

    /** Parità in cui il prossimo punto decide il game: No-Ad, oppure la terza parità con lo star point. */
    val isDecidingPoint: Boolean
        get() = isDeuce && (rules.noAd || (rules.starPoint && pt1 >= STAR_POINT_PTS))

    companion object {
        val POINT_LABELS = listOf("0", "15", "30", "40")
    }
}

/** Cosa è successo con l'ultimo punto: serve a chiamate vocali, pause, messaggi e braccialetti. */
data class Transition(
    val pointWinner: Side,
    val gameWinner: Side? = null,
    val setWinner: Side? = null,
    val matchWinner: Side? = null,
    /** Cambio campo dopo questo punto/game. */
    val changeEnds: Boolean = false,
    /** Si è arrivati al 6-6: inizia il tie-break. */
    val tiebreakStarted: Boolean = false,
    /** Set pari nel formato con match tie-break: inizia il super tie-break. */
    val matchTiebreakStarted: Boolean = false,
    /** Il game appena vinto era il primo del set. */
    val firstGameOfSet: Boolean = false,
    /** Il punto è stato giocato in un tie-break (di set o di match). */
    val inTiebreak: Boolean = false,
)

data class Step(val state: MatchState, val transition: Transition?)

object ScoreEngine {

    fun initial(rules: RulesConfig): MatchState =
        MatchState(rules = rules, tiebreak = if (rules.format == MatchFormat.MATCH_TIEBREAK_ONLY) TiebreakKind.MATCH else TiebreakKind.NONE)

    /** Ricostruisce lo stato applicando in ordine tutti gli eventi. */
    fun replay(rules: RulesConfig, events: List<MatchEvent>): MatchState =
        events.fold(initial(rules)) { s, e -> apply(s, e).state }

    fun apply(state: MatchState, event: MatchEvent): Step = when (event) {
        is MatchEvent.Point -> pointWonBy(state, event.winner)
        is MatchEvent.ServeOrder -> Step(applyServeOrder(state, event), null)
        is MatchEvent.Retired -> Step(retire(state, event.side).let { if (it.retired == event.side && !state.isFinished) it.copy(disqualified = event.disqualified) else it }, null)
        is MatchEvent.Penalty -> penalty(state, event.side, event.kind)
        is MatchEvent.Break -> Step(state, null)
        is MatchEvent.ServerFix -> Step(fixServer(state, event.side, event.player), null)
        is MatchEvent.EndsFix -> Step(if (state.isFinished) state else state.copy(p1Left = !state.p1Left), null)
        is MatchEvent.ScoreSet -> Step(setScore(state, event), null)
        is MatchEvent.ReceiveOrder ->
            Step(if (state.isFinished || event.setNumber != state.setNumber) state else state.copy(recv1 = event.deuceP1.coerceIn(0, 1), recv2 = event.deuceP2.coerceIn(0, 1)), null)
    }

    /** Nel set numero [setsDone] + 1 si gioca solo il match tie-break? */
    fun matchTiebreakSet(rules: RulesConfig, setsDone: Int): Boolean =
        rules.format == MatchFormat.MATCH_TIEBREAK_ONLY || (rules.format == MatchFormat.TWO_SETS_MATCH_TIEBREAK && setsDone == 2)

    /** Con questi game del set in corso si gioca il tie-break di set? */
    fun tiebreakAt(rules: RulesConfig, g1: Int, g2: Int): Boolean = rules.setTiebreak && g1 == rules.gamesPerSet && g2 == rules.gamesPerSet

    /** Un set finito, giocato fino in fondo con queste regole (6-4, 7-5, 7-6; senza tie-break, due game di scarto). */
    fun validSet(rules: RulesConfig, set: SetScore): Boolean {
        val n = rules.gamesPerSet
        val hi = maxOf(set.g1, set.g2); val lo = minOf(set.g1, set.g2)
        return when {
            hi == n && lo <= n - 2 -> true
            hi > n && hi - lo == 2 && (lo >= n - 1) && (!rules.setTiebreak || hi == n + 1) -> true
            rules.setTiebreak && hi == n + 1 && lo == n -> true
            else -> false
        }
    }

    /**
     * Il punteggio messo a mano si può giocare: i set sono finiti e nessuno ha già vinto la partita, il set in corso
     * non è chiuso e il game (o il tie-break) nemmeno.
     */
    fun validScore(rules: RulesConfig, sets: List<SetScore>, g1: Int, g2: Int, pt1: Int, pt2: Int): Boolean {
        if (listOf(g1, g2, pt1, pt2).any { it < 0 }) return false
        if (sets.any { !validSet(rules, it) }) return false
        if (Side.entries.any { side -> sets.count { it.winner == side } >= rules.setsToWin }) return false
        val matchTb = matchTiebreakSet(rules, sets.size)
        val n = rules.gamesPerSet
        val hi = maxOf(g1, g2); val lo = minOf(g1, g2)
        if (!matchTb) {
            if (hi >= n && hi - lo >= 2) return false
            if (rules.setTiebreak && hi > n) return false
        }
        val tb = matchTb || tiebreakAt(rules, g1, g2)
        val ph = maxOf(pt1, pt2); val pl = minOf(pt1, pt2)
        return if (tb) !(ph >= (if (matchTb) 10 else 7) && ph - pl >= 2)
        else if (rules.noAd) ph <= 3 else ph <= 3 || (ph == 4 && pl == 3)
    }

    /** Il punteggio messo a mano: si continua da lì, con chi serve deciso adesso. */
    private fun setScore(s: MatchState, e: MatchEvent.ScoreSet): MatchState {
        if (s.isFinished || !validScore(s.rules, e.sets, e.g1, e.g2, e.pt1, e.pt2)) return s
        val matchTb = matchTiebreakSet(s.rules, e.sets.size)
        val tb = when {
            matchTb -> TiebreakKind.MATCH
            tiebreakAt(s.rules, e.g1, e.g2) -> TiebreakKind.SET
            else -> TiebreakKind.NONE
        }
        val set = s.copy(
            sets = e.sets.map { it.copy(partial = false, matchTiebreak = false) },
            g1 = if (matchTb) 0 else e.g1, g2 = if (matchTb) 0 else e.g2, pt1 = e.pt1, pt2 = e.pt2, tiebreak = tb,
            recv1 = null, recv2 = null,
        )
        return fixServer(set, e.server, e.serverPlayer)
    }

    /**
     * Da adesso serve [side] (nel doppio [player]): si sposta chi ha iniziato il set (e l'ordine della squadra), così
     * il turno di ora tocca a lui e i successivi seguono da qui.
     */
    private fun fixServer(s: MatchState, side: Side, player: Int): MatchState {
        if (s.isFinished) return s
        val turn = s.serviceTurn
        val fixed = s.copy(setStartServer = if (turn % 2 == 0) side else side.other)
        if (!s.rules.doubles) return fixed
        val order = Math.floorMod(player.coerceIn(0, 1) - turn / 2, 2)
        return if (side == Side.P1) fixed.copy(order1 = order) else fixed.copy(order2 = order)
    }

    /** Cosa comporterebbe adesso un richiamo [kind] a [side] (per mostrarlo prima di confermare). */
    fun penaltyOutcome(s: MatchState, side: Side, kind: PenaltyKind): PenaltyOutcome {
        val n = s.penalties(side, kind) + 1
        return when {
            n == 1 -> PenaltyOutcome.WARNING
            kind == PenaltyKind.CODE -> if (n == 2) PenaltyOutcome.POINT else PenaltyOutcome.GAME
            side == s.server -> PenaltyOutcome.SERVE
            else -> PenaltyOutcome.POINT
        }
    }

    /**
     * Il richiamo: si conta e, se tocca, il punto o il game va all'avversario. La perdita del servizio non cambia il
     * punteggio da sola: la segna chi tiene il punteggio come una prima fuori (o il doppio fallo).
     */
    private fun penalty(s: MatchState, side: Side, kind: PenaltyKind): Step {
        if (s.isFinished) return Step(s, null)
        val outcome = penaltyOutcome(s, side, kind)
        val counted = when (kind) {
            PenaltyKind.CODE -> if (side == Side.P1) s.copy(codes1 = s.codes1 + 1) else s.copy(codes2 = s.codes2 + 1)
            PenaltyKind.TIME -> if (side == Side.P1) s.copy(times1 = s.times1 + 1) else s.copy(times2 = s.times2 + 1)
        }
        return when (outcome) {
            PenaltyOutcome.WARNING, PenaltyOutcome.SERVE -> Step(counted, null)
            PenaltyOutcome.POINT -> pointWonBy(counted, side.other)
            PenaltyOutcome.GAME -> {
                // Punti all'avversario finché il game (o il tie-break) non è suo.
                var step = pointWonBy(counted, side.other)
                while (step.transition != null && step.transition!!.gameWinner == null) step = pointWonBy(step.state, side.other)
                step
            }
        }
    }

    /**
     * Il ritiro: il set in corso resta scritto com'era (nel match tie-break, coi suoi punti), senza vincitore. I punti
     * del game in corso non entrano nel risultato, come sul referto.
     */
    private fun retire(s: MatchState, side: Side): MatchState {
        if (s.isFinished) return s
        val inMatchTiebreak = s.tiebreak == TiebreakKind.MATCH
        // Ritiro nel primo game della partita, a punti già giocati: si scrive "0-0 rit." (senza nessun punto è un w.o.).
        val started = if (inMatchTiebreak) s.pt1 + s.pt2 > 0 else s.g1 + s.g2 > 0 || (s.sets.isEmpty() && s.pointsPlayed > 0)
        val open = when {
            !started -> emptyList()
            inMatchTiebreak -> listOf(SetScore(0, 0, s.pt1, s.pt2, matchTiebreak = true, partial = true))
            else -> listOf(SetScore(s.g1, s.g2, partial = true))
        }
        return s.copy(sets = s.sets + open, g1 = 0, g2 = 0, pt1 = 0, pt2 = 0, tiebreak = TiebreakKind.NONE, winner = side.other, retired = side)
    }

    /** L'ordine di servizio del doppio si può cambiare solo prima del primo punto del set. */
    fun canChangeServeOrder(s: MatchState): Boolean =
        s.rules.doubles && !s.isFinished && s.g1 == 0 && s.g2 == 0 && s.pt1 == 0 && s.pt2 == 0

    private fun applyServeOrder(s: MatchState, e: MatchEvent.ServeOrder): MatchState {
        if (!canChangeServeOrder(s) || e.setNumber != s.setNumber) return s
        return s.copy(order1 = e.firstP1.coerceIn(0, 1), order2 = e.firstP2.coerceIn(0, 1))
    }

    fun pointWonBy(s: MatchState, w: Side): Step {
        if (s.isFinished) return Step(s, null)
        val pt1 = s.pt1 + if (w == Side.P1) 1 else 0
        val pt2 = s.pt2 + if (w == Side.P2) 1 else 0
        val pw = if (w == Side.P1) pt1 else pt2
        val po = if (w == Side.P1) pt2 else pt1
        val played = s.copy(pt1 = pt1, pt2 = pt2, pointsPlayed = s.pointsPlayed + 1)

        if (s.inTiebreak) {
            if (pw >= s.tiebreakTarget && pw - po >= 2) {
                val set = if (s.tiebreak == TiebreakKind.MATCH) {
                    SetScore(
                        g1 = if (w == Side.P1) 1 else 0,
                        g2 = if (w == Side.P2) 1 else 0,
                        tb1 = pt1, tb2 = pt2, matchTiebreak = true,
                    )
                } else {
                    SetScore(
                        g1 = s.g1 + if (w == Side.P1) 1 else 0,
                        g2 = s.g2 + if (w == Side.P2) 1 else 0,
                        tb1 = pt1, tb2 = pt2,
                    )
                }
                return closeSet(played, w, set, fromTiebreak = true)
            }
            // Nel tie-break si cambia campo ogni 6 punti giocati.
            val change = (pt1 + pt2) % 6 == 0
            return Step(
                played.copy(p1Left = if (change) !s.p1Left else s.p1Left),
                Transition(pointWinner = w, changeEnds = change, inTiebreak = true),
            )
        }

        val gameWon = when {
            s.rules.noAd -> pw >= 4
            // Star point: la terza parità è il 5-5 in punti, quindi chi arriva a 6 vince comunque.
            s.rules.starPoint -> pw >= 4 && (pw - po >= 2 || pw >= STAR_POINT_PTS + 1)
            else -> pw >= 4 && pw - po >= 2
        }
        if (!gameWon) return Step(played, Transition(pointWinner = w))

        val g1 = s.g1 + if (w == Side.P1) 1 else 0
        val g2 = s.g2 + if (w == Side.P2) 1 else 0
        val gw = if (w == Side.P1) g1 else g2
        val go = if (w == Side.P1) g2 else g1
        val total = g1 + g2

        val n = s.rules.gamesPerSet
        if (gw >= n && gw - go >= 2) {
            return closeSet(played, w, SetScore(g1, g2), fromTiebreak = false)
        }
        if (s.rules.setTiebreak && g1 == n && g2 == n) {
            // 6-6 (o 4-4, 8-8...): game totali pari, quindi nessun cambio campo; parte il tie-break.
            return Step(
                played.copy(g1 = g1, g2 = g2, pt1 = 0, pt2 = 0, tiebreak = TiebreakKind.SET),
                Transition(pointWinner = w, gameWinner = w, tiebreakStarted = true),
            )
        }
        // Cambio campo dopo ogni game dispari del set (1°, 3°, 5°...).
        val change = total % 2 == 1
        return Step(
            played.copy(g1 = g1, g2 = g2, pt1 = 0, pt2 = 0, p1Left = if (change) !s.p1Left else s.p1Left),
            Transition(pointWinner = w, gameWinner = w, changeEnds = change, firstGameOfSet = total == 1),
        )
    }

    private fun closeSet(s: MatchState, w: Side, set: SetScore, fromTiebreak: Boolean): Step {
        val sets = s.sets + set
        val won = sets.count { it.winner == w }
        if (won >= s.rules.setsToWin) {
            val final = s.copy(
                sets = sets, g1 = 0, g2 = 0, pt1 = 0, pt2 = 0,
                tiebreak = TiebreakKind.NONE, winner = w,
            )
            return Step(
                final,
                Transition(pointWinner = w, gameWinner = w, setWinner = w, matchWinner = w, inTiebreak = fromTiebreak),
            )
        }
        // Il tie-break conta come un game: un set 7-6 ha 13 game (dispari) e fa cambiare campo.
        val gamesInSet = set.g1 + set.g2
        val change = gamesInSet % 2 == 1
        // Serve per primo nel nuovo set chi non ha servito l'ultimo turno
        // (dopo un tie-break: chi ha ricevuto il primo punto del tie-break).
        val nextStart = s.serverOfTurn(gamesInSet)
        val nextTiebreak =
            if (s.rules.format == MatchFormat.TWO_SETS_MATCH_TIEBREAK && sets.size == 2) TiebreakKind.MATCH
            else TiebreakKind.NONE
        val next = s.copy(
            sets = sets, g1 = 0, g2 = 0, pt1 = 0, pt2 = 0,
            tiebreak = nextTiebreak,
            setStartServer = nextStart,
            // Nel set nuovo l'ordine di risposta si sceglie di nuovo.
            recv1 = null, recv2 = null,
            order1 = naturalNextServer(s, Side.P1),
            order2 = naturalNextServer(s, Side.P2),
            p1Left = if (change) !s.p1Left else s.p1Left,
        )
        return Step(
            next,
            Transition(
                pointWinner = w, gameWinner = w, setWinner = w,
                changeEnds = change,
                matchTiebreakStarted = nextTiebreak == TiebreakKind.MATCH,
                inTiebreak = fromTiebreak,
            ),
        )
    }

    /**
     * Doppio: se nessuno cambia l'ordine a inizio set, la rotazione prosegue:
     * per ogni squadra serve il compagno di chi ha servito per ultimo.
     * [s] è lo stato con l'ultimo punto del set già contato ma con i game non ancora aggiornati.
     */
    private fun naturalNextServer(s: MatchState, side: Side): Int {
        // Ultimo punto del tie-break = punto n. (pt1+pt2-1), turno (k+1)/2; fuori dal tie-break il game appena vinto.
        val lastTurn = if (s.inTiebreak) s.g1 + s.g2 + (s.pt1 + s.pt2) / 2 else s.g1 + s.g2
        var turn = lastTurn
        while (turn >= 0 && s.serverOfTurn(turn) != side) turn--
        if (turn < 0) return s.order(side)
        return 1 - s.playerOfTurn(turn, side)
    }

    /** Il prossimo punto vinto da [side] chiuderebbe set o partita? (per "set point" / "match point"). */
    fun lookahead(s: MatchState, side: Side): Transition? =
        if (s.isFinished) null else pointWonBy(s, side).transition

    /** Palla break: il ricevitore vincerebbe il game col prossimo punto (fuori dal tie-break). */
    fun isBreakPoint(s: MatchState): Boolean {
        if (s.isFinished || s.inTiebreak) return false
        return lookahead(s, s.receiver)?.gameWinner == s.receiver
    }
}
