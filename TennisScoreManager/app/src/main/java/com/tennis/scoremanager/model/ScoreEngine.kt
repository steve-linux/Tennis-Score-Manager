package com.tennis.scoremanager.model

/**
 * Stato della partita in un istante. È immutabile: ogni punto produce un nuovo stato.
 *
 * Il servizio non è memorizzato game per game ma ricavato dal "turno di servizio" nel set:
 * turno = game giocati nel set (+ turni del tie-break). Nei turni pari serve chi ha iniziato il set.
 * Così la stessa formula copre singolare, doppio (rotazione a 4) e tie-break (1 punto, poi 2 a testa).
 */
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
) {
    val isFinished: Boolean get() = winner != null
    val setNumber: Int get() = sets.size + 1
    val inTiebreak: Boolean get() = tiebreak != TiebreakKind.NONE
    val tiebreakTarget: Int get() = if (tiebreak == TiebreakKind.MATCH) 10 else 7

    fun games(side: Side): Int = if (side == Side.P1) g1 else g2
    fun points(side: Side): Int = if (side == Side.P1) pt1 else pt2
    fun setsWon(side: Side): Int = sets.count { it.winner == side }
    fun order(side: Side): Int = if (side == Side.P1) order1 else order2
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

    fun initial(rules: RulesConfig): MatchState = MatchState(rules = rules)

    /** Ricostruisce lo stato applicando in ordine tutti gli eventi. */
    fun replay(rules: RulesConfig, events: List<MatchEvent>): MatchState =
        events.fold(initial(rules)) { s, e -> apply(s, e).state }

    fun apply(state: MatchState, event: MatchEvent): Step = when (event) {
        is MatchEvent.Point -> pointWonBy(state, event.winner)
        is MatchEvent.ServeOrder -> Step(applyServeOrder(state, event), null)
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

        val gameWon = if (s.rules.noAd) pw >= 4 else pw >= 4 && pw - po >= 2
        if (!gameWon) return Step(played, Transition(pointWinner = w))

        val g1 = s.g1 + if (w == Side.P1) 1 else 0
        val g2 = s.g2 + if (w == Side.P2) 1 else 0
        val gw = if (w == Side.P1) g1 else g2
        val go = if (w == Side.P1) g2 else g1
        val total = g1 + g2

        if (gw >= 6 && gw - go >= 2) {
            return closeSet(played, w, SetScore(g1, g2), fromTiebreak = false)
        }
        if (g1 == 6 && g2 == 6) {
            // 6-6: dodicesimo game (pari) quindi nessun cambio campo; parte il tie-break.
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
        if (won >= 2) {
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
