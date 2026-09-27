package com.tennis.scoremanager.voice

import com.tennis.scoremanager.model.Lang
import com.tennis.scoremanager.model.MatchEvent
import com.tennis.scoremanager.model.MatchFormat
import com.tennis.scoremanager.model.RulesConfig
import com.tennis.scoremanager.model.ScoreEngine
import com.tennis.scoremanager.model.Side
import com.tennis.scoremanager.model.Transition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CallBuilderTest {

    private val names = object : CallNames {
        override fun side(side: Side) = if (side == Side.P1) "Rossi" else "Bianchi"
        override fun player(side: Side, index: Int) = listOf(listOf("Rossi", "Verdi"), listOf("Bianchi", "Neri"))[side.ordinal][index]
    }
    private val itb = CallBuilder(Lang.IT)
    private val en = CallBuilder(Lang.EN)

    private var state = ScoreEngine.initial(RulesConfig(firstServer = Side.P1))
    private var last: Transition? = null

    private fun point(w: Side): String {
        val step = ScoreEngine.pointWonBy(state, w)
        state = step.state
        last = step.transition
        return itb.render(itb.afterPoint(state, last!!, names))
    }

    private fun pointEn(w: Side): String {
        val step = ScoreEngine.pointWonBy(state, w)
        state = step.state
        last = step.transition
        return en.render(en.afterPoint(state, last!!, names))
    }

    private fun games(a: Int, b: Int) {
        var ga = 0
        var gb = 0
        while (ga < a || gb < b) {
            val w = if (ga < a && (ga <= gb || gb >= b)) { ga++; Side.P1 } else { gb++; Side.P2 }
            repeat(4) { state = ScoreEngine.pointWonBy(state, w).state }
        }
    }

    @Test
    fun startCall() {
        assertEquals("primo set Rossi al servizio gioco", itb.render(itb.start(state, names)))
        assertEquals(CallBuilder.TAG_PLAY, (itb.start(state, names).last() as Seg.Clip).tag)
        assertEquals("first set Rossi to serve play", en.render(en.start(state, names)))
    }

    @Test
    fun pointsReadFromServer() {
        assertEquals("quindici zero", point(Side.P1))
        assertEquals("quindici pari", point(Side.P2))
        assertEquals("quindici trenta", point(Side.P2))
        assertEquals("quindici quaranta", point(Side.P2))
        assertEquals("trenta quaranta", point(Side.P1))
        assertEquals("parità", point(Side.P1))
        assertEquals("vantaggio Bianchi", point(Side.P2))
        assertEquals("parità", point(Side.P1))
        assertEquals("vantaggio Rossi", point(Side.P1))
    }

    @Test
    fun receiverScoresFirstIsZeroFifteen() {
        assertEquals("zero quindici", point(Side.P2))
        point(Side.P2)
        assertEquals("zero quaranta", point(Side.P2))
    }

    @Test
    fun gameCallsAndChangeOfEnds() {
        repeat(3) { point(Side.P1) }
        assertEquals("gioco Rossi Rossi conduce un gioco a zero cambio campo", point(Side.P1))
        repeat(3) { point(Side.P2) }
        assertEquals("gioco Bianchi un gioco pari", point(Side.P2))
        repeat(3) { point(Side.P1) }
        assertEquals("gioco Rossi Rossi conduce due giochi a uno cambio campo", point(Side.P1))
    }

    @Test
    fun englishGameCalls() {
        repeat(3) { pointEn(Side.P1) }
        assertEquals("game Rossi Rossi leads one game to love change ends", pointEn(Side.P1))
        repeat(3) { pointEn(Side.P2) }
        assertEquals("game Bianchi one game all", pointEn(Side.P2))
    }

    @Test
    fun sixAllTiebreakAndTiebreakCalls() {
        games(5, 5)
        repeat(3) { point(Side.P1) }
        point(Side.P1)
        repeat(3) { point(Side.P2) }
        assertEquals("gioco Bianchi sei giochi pari tie-break", point(Side.P2))
        assertEquals("uno a zero Rossi", point(Side.P1))
        assertEquals("uno pari", point(Side.P2))
        assertEquals("due a uno Bianchi", point(Side.P2))
        point(Side.P2); point(Side.P1)
        // 6 punti giocati: 3-3 -> cambio campo dopo il punteggio
        assertEquals("tre pari cambio campo", point(Side.P1))
    }

    @Test
    fun tiebreakWinCallsSetStanding() {
        games(5, 5)
        repeat(4) { point(Side.P1) }
        repeat(4) { point(Side.P2) }
        repeat(6) { point(Side.P1) }
        assertEquals("gioco Rossi Rossi conduce un set a zero cambio campo", point(Side.P1))
    }

    @Test
    fun setAllCall() {
        games(6, 0)
        games(0, 5)
        repeat(3) { point(Side.P2) }
        // secondo set 0-6 = 6 game: nessun cambio a fine set
        assertEquals("gioco Bianchi un set pari", point(Side.P2))
    }

    @Test
    fun superTiebreakAnnounced() {
        state = ScoreEngine.initial(RulesConfig(format = MatchFormat.TWO_SETS_MATCH_TIEBREAK))
        games(6, 0)
        games(0, 5)
        repeat(3) { point(Side.P2) }
        assertEquals("gioco Bianchi un set pari super tie-break", point(Side.P2))
    }

    @Test
    fun matchEndReadsSetsFromWinnerSide() {
        games(6, 4)
        games(3, 6)
        games(6, 5)
        repeat(3) { point(Side.P1) }
        assertEquals("gioco, set, partita Rossi sei quattro tre sei sette cinque", point(Side.P1))
        assertTrue(state.isFinished)
    }

    @Test
    fun correctionCall() {
        val one = ScoreEngine.replay(state.rules, listOf(MatchEvent.Point(Side.P1)))
        assertEquals("correzione quindici zero", itb.render(itb.correction(one, names)))
        val fresh = ScoreEngine.initial(state.rules)
        assertEquals("correzione", itb.render(itb.correction(fresh, names)))
    }

    @Test
    fun noAdDeuceCall() {
        state = ScoreEngine.initial(RulesConfig(noAd = true))
        repeat(3) { point(Side.P1) }
        repeat(2) { point(Side.P2) }
        assertEquals("parità punto decisivo", point(Side.P2))
    }

    @Test
    fun doublesStartUsesIndividualServer() {
        val d = ScoreEngine.initial(RulesConfig(doubles = true, firstServer = Side.P2, firstServerP2 = 1))
        assertEquals("primo set Neri al servizio gioco", itb.render(itb.start(d, names)))
    }

    @Test
    fun catalogHasEveryKeyUsed() {
        val keys = Phrases.keys.toSet()
        assertTrue("score_3_2" in keys)
        assertTrue("games_6_5" in keys)
        assertTrue("games_all_6" in keys)
        assertTrue("num_30" in keys)
        assertEquals(keys.size, Phrases.keys.size)
        for (k in keys) {
            assertTrue(k, Phrases.text(k, Lang.IT).isNotBlank())
            assertTrue(k, Phrases.text(k, Lang.EN).isNotBlank())
        }
        assertEquals("tre giochi a due", Phrases.text("games_3_2", Lang.IT))
        assertEquals("one game to love", Phrases.text("games_1_0", Lang.EN))
        assertEquals("trenta quindici", Phrases.text("score_2_1", Lang.IT))
    }
}
