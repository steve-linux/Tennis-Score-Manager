package com.tennis.scoremanager.data

import com.tennis.scoremanager.ble.BandProtocol
import com.tennis.scoremanager.model.Lang
import com.tennis.scoremanager.model.RulesConfig
import com.tennis.scoremanager.model.SetScore
import com.tennis.scoremanager.model.Side
import com.tennis.scoremanager.ui.EnStrings
import com.tennis.scoremanager.ui.ItStrings
import com.tennis.scoremanager.ui.stringsFor
import org.junit.Assert.assertEquals
import org.junit.Test

class DataTest {

    @Test
    fun singlesNamesAndDefaults() {
        val n = Names(SetupData(p1a = "  Rossi ", p2a = ""), ItStrings)
        assertEquals("Rossi", n.side(Side.P1))
        assertEquals("Giocatore 2", n.side(Side.P2))
        assertEquals(listOf("Rossi"), n.players(Side.P1))
        assertEquals("Player 2", Names(SetupData(), EnStrings).side(Side.P2))
    }

    @Test
    fun doublesNamesStayOnTheirSide() {
        val su = SetupData(doubles = true, p1a = "Rossi", p1b = "Verdi", p2a = "Bianchi", p2b = "")
        val n = Names(su, ItStrings)
        assertEquals("Rossi e Verdi", n.side(Side.P1))
        assertEquals("Rossi / Verdi", n.short(Side.P1))
        assertEquals("Bianchi", n.side(Side.P2))
        assertEquals(listOf("Bianchi", "Giocatore 2B"), n.players(Side.P2))
        assertEquals("Verdi", n.player(Side.P1, 1))
        assertEquals("Giocatore 1", Names(SetupData(doubles = true), ItStrings).side(Side.P1))
        assertEquals("Rossi and Verdi", Names(su, EnStrings).side(Side.P1))
    }

    @Test
    fun setNotation() {
        assertEquals("6-4", Reports.setText(SetScore(6, 4), Side.P1))
        assertEquals("4-6", Reports.setText(SetScore(6, 4), Side.P2))
        assertEquals("7-6(5)", Reports.setText(SetScore(7, 6, 7, 5), Side.P1))
        assertEquals("6-7(5)", Reports.setText(SetScore(7, 6, 7, 5), Side.P2))
        assertEquals("[10-8]", Reports.setText(SetScore(1, 0, 10, 8, matchTiebreak = true), Side.P1))
        assertEquals("[8-10]", Reports.setText(SetScore(1, 0, 10, 8, matchTiebreak = true), Side.P2))
        assertEquals("1:02:03", Reports.duration(3_723_000))
    }

    @Test
    fun bandTextIsPlainAscii() {
        assertEquals("NICCOLO FORTE", BandProtocol.clean("Niccolò Forté"))
        assertEquals("A/B", BandProtocol.clean("a|b"))
        assertEquals("P|15|AD|1|TIE-BREAK", BandProtocol.point("15", "AD", 1, "Tie-break"))
        assertEquals("M|GAME SET MATCH|6-4 7-5|15", BandProtocol.message("Game set match", "6-4 7-5", 15))
    }

    @Test
    fun formatLineUsesTheLanguageForNoAd() {
        val rec = MatchRecord(id = "m", setup = SetupData(), options = MatchOptions(), rules = RulesConfig(noAd = true))
        assertEquals("3 set · tie-break a 7 · No-Ad · Singolare", Reports.formatLabel(rec, ItStrings))
        assertEquals("3 sets · tie-break a 7 · Sin ventaja · Individual", Reports.formatLabel(rec, stringsFor(Lang.ES)))
        assertEquals("3 sets · tie-break a 7 · Sem vantagem · Simples", Reports.formatLabel(rec, stringsFor(Lang.PT)))
    }
}
