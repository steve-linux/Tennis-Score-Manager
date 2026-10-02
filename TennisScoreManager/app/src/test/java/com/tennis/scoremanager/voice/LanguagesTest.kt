package com.tennis.scoremanager.voice

import com.tennis.scoremanager.model.Lang
import com.tennis.scoremanager.model.MatchFormat
import com.tennis.scoremanager.model.MatchState
import com.tennis.scoremanager.model.RulesConfig
import com.tennis.scoremanager.model.ScoreEngine
import com.tennis.scoremanager.model.Side
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Le stesse partite chiamate in ogni lingua, con le formule dei testi ufficiali (vedi CallWords). */
class LanguagesTest {

    private val names = object : CallNames {
        override fun side(side: Side) = if (side == Side.P1) "Rossi" else "Bianchi"
        override fun player(side: Side, index: Int) = listOf(listOf("Rossi", "Verdi"), listOf("Bianchi", "Neri"))[side.ordinal][index]
    }

    /** Gioca la partita con [lang] e registra la chiamata dopo ogni punto. */
    private inner class Match(val lang: Lang, rules: RulesConfig = RulesConfig(firstServer = Side.P1)) {
        val calls = CallBuilder(lang)
        var state: MatchState = ScoreEngine.initial(rules)

        fun start() = calls.render(calls.start(state, names))

        fun point(w: Side): String {
            val step = ScoreEngine.pointWonBy(state, w)
            state = step.state
            return calls.render(calls.afterPoint(state, step.transition!!, names))
        }

        fun games(a: Int, b: Int) {
            var ga = 0
            var gb = 0
            while (ga < a || gb < b) {
                val w = if (ga < a && (ga <= gb || gb >= b)) { ga++; Side.P1 } else { gb++; Side.P2 }
                repeat(4) { state = ScoreEngine.pointWonBy(state, w).state }
            }
        }
    }

    private data class Expected(
        val start: String,
        val points: List<String>,
        val firstGame: String,
        val gamesAll: String,
        val tiebreak: List<String>,
        val matchEnd: String,
        val noAd: String,
        val superTiebreakEnd: String,
    )

    /** Punti: 15-0, 15-15, 15-30, 15-40, 30-40, deuce, vantaggio Bianchi. Tie-break: 6-6 annunciato, 1-0, 1-1, 1-2. */
    private fun check(lang: Lang, e: Expected) {
        assertEquals(e.start, Match(lang).start())

        val m = Match(lang)
        val order = listOf(Side.P1, Side.P2, Side.P2, Side.P2, Side.P1, Side.P1, Side.P2)
        assertEquals(e.points, order.map { m.point(it) })

        val g = Match(lang)
        repeat(3) { g.point(Side.P1) }
        assertEquals(e.firstGame, g.point(Side.P1))
        repeat(3) { g.point(Side.P2) }
        assertEquals(e.gamesAll, g.point(Side.P2))

        val t = Match(lang)
        t.games(5, 5)
        repeat(4) { t.point(Side.P1) }
        repeat(3) { t.point(Side.P2) }
        assertEquals(e.tiebreak, listOf(t.point(Side.P2), t.point(Side.P1), t.point(Side.P2), t.point(Side.P2)))

        val end = Match(lang)
        end.games(6, 4)
        end.games(3, 6)
        end.games(6, 5)
        repeat(3) { end.point(Side.P1) }
        assertEquals(e.matchEnd, end.point(Side.P1))

        val na = Match(lang, RulesConfig(noAd = true))
        repeat(3) { na.point(Side.P1) }
        repeat(2) { na.point(Side.P2) }
        assertEquals(e.noAd, na.point(Side.P2))

        // Match tie-break vinto 10-8: "dix huit", "diez ocho", "dez oito" suonerebbero 18.
        val st = Match(lang, RulesConfig(format = MatchFormat.TWO_SETS_MATCH_TIEBREAK))
        st.games(6, 0)
        st.games(0, 6)
        repeat(8) { st.point(Side.P1); st.point(Side.P2) }
        st.point(Side.P1)
        assertEquals(e.superTiebreakEnd, st.point(Side.P1))
    }

    @Test
    fun italian() = check(
        Lang.IT,
        Expected(
            start = "primo set Rossi al servizio gioco",
            points = listOf("quindici zero", "quindici pari", "quindici trenta", "quindici quaranta", "trenta quaranta", "parità", "vantaggio Bianchi"),
            firstGame = "gioco Rossi Rossi conduce un gioco a zero cambio campo",
            gamesAll = "gioco Bianchi un gioco pari",
            tiebreak = listOf("gioco Bianchi sei giochi pari tie-break", "uno a zero Rossi", "uno pari", "due a uno Bianchi"),
            matchEnd = "gioco, set, partita Rossi sei quattro tre sei sette cinque",
            noAd = "parità punto decisivo",
            superTiebreakEnd = "gioco, set, partita Rossi sei zero zero sei dieci otto",
        ),
    )

    @Test
    fun english() = check(
        Lang.EN,
        Expected(
            start = "first set Rossi to serve play",
            points = listOf("fifteen love", "fifteen all", "fifteen thirty", "fifteen forty", "thirty forty", "deuce", "advantage Bianchi"),
            firstGame = "game Rossi Rossi leads one game to love change ends",
            gamesAll = "game Bianchi one game all",
            tiebreak = listOf("game Bianchi six games all tie-break", "one zero Rossi", "one all", "two one Bianchi"),
            matchEnd = "game, set and match Rossi six four three six seven five",
            noAd = "deuce deciding point",
            superTiebreakEnd = "game, set and match Rossi six love love six ten eight",
        ),
    )

    @Test
    fun french() = check(
        Lang.FR,
        Expected(
            start = "première manche au service Rossi jouez",
            points = listOf("quinze-zéro", "quinze A", "quinze-trente", "quinze-quarante", "trente-quarante", "égalité", "avantage Bianchi"),
            firstGame = "jeu Rossi Rossi mène un jeu à zéro changement de côté",
            gamesAll = "jeu Bianchi un jeu partout",
            tiebreak = listOf("jeu Bianchi six jeux partout jeu décisif", "un zéro Rossi", "un partout", "deux un Bianchi"),
            matchEnd = "jeu, set et match Rossi six quatre trois six sept cinq",
            noAd = "égalité point décisif",
            superTiebreakEnd = "jeu, set et match Rossi six zéro zéro six dix à huit",
        ),
    )

    @Test
    fun german() = check(
        Lang.DE,
        Expected(
            start = "erster Satz Aufschlag Rossi spielen",
            points = listOf("fünfzehn null", "fünfzehn beide", "fünfzehn dreißig", "fünfzehn vierzig", "dreißig vierzig", "Einstand", "Vorteil Bianchi"),
            firstGame = "Spiel Rossi Rossi führt eins zu null Seitenwechsel",
            gamesAll = "Spiel Bianchi ein Spiel beide",
            tiebreak = listOf("Spiel Bianchi sechs beide Tie-Break", "eins zu null Rossi", "eins beide", "zwei zu eins Bianchi"),
            matchEnd = "Spiel, Satz und Sieg Rossi sechs zu vier drei zu sechs sieben zu fünf",
            noAd = "Einstand entscheidender Punkt",
            superTiebreakEnd = "Spiel, Satz und Sieg Rossi sechs zu null null zu sechs zehn zu acht",
        ),
    )

    @Test
    fun spanish() = check(
        Lang.ES,
        Expected(
            start = "primer set al servicio Rossi jueguen",
            points = listOf("quince cero", "quince iguales", "quince treinta", "quince cuarenta", "treinta cuarenta", "iguales", "ventaja Bianchi"),
            firstGame = "juego Rossi Rossi gana un juego a cero cambio de lado",
            gamesAll = "juego Bianchi un juego iguales",
            tiebreak = listOf("juego Bianchi seis juegos iguales tie-break", "uno cero Rossi", "uno iguales", "dos uno Bianchi"),
            matchEnd = "juego, set y partido Rossi seis cuatro tres seis siete cinco",
            noAd = "iguales punto decisivo",
            superTiebreakEnd = "juego, set y partido Rossi seis cero cero seis diez a ocho",
        ),
    )

    @Test
    fun portuguese() = check(
        Lang.PT,
        Expected(
            start = "primeiro set Rossi ao serviço joguem",
            points = listOf("quinze-zero", "quinze iguais", "quinze-trinta", "quinze-quarenta", "trinta-quarenta", "iguais", "vantagem Bianchi"),
            firstGame = "jogo Rossi Rossi vence por um jogo a zero troca de lado",
            gamesAll = "jogo Bianchi um jogo igual",
            tiebreak = listOf("jogo Bianchi seis jogos iguais tie-break", "um a zero Rossi", "um iguais", "dois a um Bianchi"),
            matchEnd = "jogo, set e partida Rossi seis quatro três seis sete cinco",
            noAd = "iguais ponto decisivo",
            superTiebreakEnd = "jogo, set e partida Rossi seis zero zero seis dez a oito",
        ),
    )

    @Test
    fun everyLanguageHasTheWholeCatalog() {
        for (lang in Lang.entries) {
            assertEquals(lang.name, Phrases.FIXED_KEYS.toSet(), CallWords.of(lang).fixed.keys)
            assertEquals(lang.name, Phrases.MAX_NUMBER + 1, CallWords.of(lang).numbers.size)
            assertEquals(lang.name, 4, CallWords.of(lang).points.size)
            for (k in Phrases.keys) {
                val t = Phrases.text(k, lang)
                assertTrue("$lang $k", t.isNotBlank() && "_" !in t)
                // Mai cifre: i motori vocali le leggono come orari, date o sottrazioni.
                assertFalse("$lang $k: $t", t.any { it.isDigit() })
            }
        }
    }

    @Test
    fun fixedKeysAreUnique() {
        assertEquals(Phrases.FIXED_KEYS.size, Phrases.FIXED_KEYS.toSet().size)
        assertEquals(Phrases.keys.size, Phrases.keys.toSet().size)
    }

    @Test
    fun langCodes() {
        for (lang in Lang.entries) assertEquals(lang, Lang.fromCode(lang.code))
        assertEquals(Lang.FR, Lang.fromCode("FR"))
        assertEquals(null, Lang.fromCode("xx"))
    }
}
