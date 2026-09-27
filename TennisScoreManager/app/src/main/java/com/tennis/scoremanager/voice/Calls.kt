package com.tennis.scoremanager.voice

import com.tennis.scoremanager.model.Lang
import com.tennis.scoremanager.model.MatchState
import com.tennis.scoremanager.model.Side
import com.tennis.scoremanager.model.Transition

/** Un pezzo di chiamata: frase registrata (file locale o TTS di riserva), testo TTS (nomi) o pausa. */
sealed interface Seg {
    /** Frase del catalogo: se esiste il file audio locale si usa quello, altrimenti TTS. */
    data class Clip(val key: String, val tag: String? = null) : Seg

    /** Testo sempre letto dal TTS (nomi dei giocatori). */
    data class Say(val text: String, val tag: String? = null) : Seg

    data class Pause(val ms: Long) : Seg
}

/** Nomi usati nelle chiamate: squadra/giocatore per lato, e singolo giocatore del doppio. */
interface CallNames {
    fun side(side: Side): String
    fun player(side: Side, index: Int): String
}

/**
 * Catalogo delle frasi fisse. Ogni chiave corrisponde a un file `<chiave>.wav|.mp3|.ogg|.m4a`
 * nella cartella voce della lingua; il testo serve per generare il file o come riserva TTS.
 */
object Phrases {
    const val MAX_NUMBER = 30

    private val numIt = listOf(
        "zero", "uno", "due", "tre", "quattro", "cinque", "sei", "sette", "otto", "nove", "dieci",
        "undici", "dodici", "tredici", "quattordici", "quindici", "sedici", "diciassette", "diciotto",
        "diciannove", "venti", "ventuno", "ventidue", "ventitré", "ventiquattro", "venticinque",
        "ventisei", "ventisette", "ventotto", "ventinove", "trenta",
    )
    private val numEn = listOf(
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
        "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen",
        "nineteen", "twenty", "twenty-one", "twenty-two", "twenty-three", "twenty-four", "twenty-five",
        "twenty-six", "twenty-seven", "twenty-eight", "twenty-nine", "thirty",
    )
    private val pointIt = listOf("zero", "quindici", "trenta", "quaranta")
    private val pointEn = listOf("love", "fifteen", "thirty", "forty")

    private val fixed: Map<String, Pair<String, String>> = linkedMapOf(
        "first_set" to ("primo set" to "first set"),
        "to_serve" to ("al servizio" to "to serve"),
        "play" to ("gioco" to "play"),
        "deuce" to ("parità" to "deuce"),
        "advantage" to ("vantaggio" to "advantage"),
        "deciding_point" to ("punto decisivo" to "deciding point"),
        "game" to ("gioco" to "game"),
        "leads" to ("conduce" to "leads"),
        "sets_1_0" to ("un set a zero" to "one set to love"),
        "sets_all_1" to ("un set pari" to "one set all"),
        "tiebreak" to ("tie-break" to "tie-break"),
        "match_tiebreak" to ("super tie-break" to "match tie-break"),
        "to" to ("a" to "to"),
        "all" to ("pari" to "all"),
        "game_set_match" to ("gioco, set, partita" to "game, set and match"),
        "change_ends" to ("cambio campo" to "change ends"),
        "correction" to ("correzione" to "correction"),
    )

    private fun numberWord(n: Int, lang: Lang): String =
        (if (lang == Lang.IT) numIt else numEn).getOrElse(n) { n.toString() }

    private fun gamesWord(n: Int, lang: Lang): String = when (lang) {
        Lang.IT -> if (n == 1) "un gioco" else "${numberWord(n, lang)} giochi"
        Lang.EN -> if (n == 1) "one game" else "${numberWord(n, lang)} games"
    }

    /** Tutte le chiavi del catalogo, nell'ordine in cui vengono generate. */
    val keys: List<String> by lazy {
        buildList {
            addAll(fixed.keys)
            for (s in 0..3) for (r in 0..3) {
                if ((s == 0 && r == 0) || (s == 3 && r == 3)) continue
                add("score_${s}_$r")
            }
            // Game durante il set: chi conduce ha al massimo 5 game, oppure 6-5.
            for (a in 1..6) for (b in 0 until a) {
                if (a == 6 && b != 5) continue
                add("games_${a}_$b")
            }
            for (n in 1..6) add("games_all_$n")
            for (n in 0..MAX_NUMBER) add("num_$n")
        }
    }

    fun text(key: String, lang: Lang): String {
        fixed[key]?.let { return if (lang == Lang.IT) it.first else it.second }
        val parts = key.split('_')
        return when {
            key.startsWith("score_") -> {
                val s = parts[1].toInt()
                val r = parts[2].toInt()
                val words = if (lang == Lang.IT) pointIt else pointEn
                if (s == r) "${words[s]} ${if (lang == Lang.IT) "pari" else "all"}" else "${words[s]} ${words[r]}"
            }
            key.startsWith("games_all_") -> {
                val n = parts[2].toInt()
                "${gamesWord(n, lang)} ${if (lang == Lang.IT) "pari" else "all"}"
            }
            key.startsWith("games_") -> {
                val a = parts[1].toInt()
                val b = parts[2].toInt()
                if (lang == Lang.IT) "${gamesWord(a, lang)} a ${numberWord(b, lang)}"
                else "${gamesWord(a, lang)} to ${if (b == 0) "love" else numberWord(b, lang)}"
            }
            key.startsWith("num_") -> numberWord(parts[1].toInt(), lang)
            else -> key
        }
    }
}

/** Costruisce le chiamate dell'arbitro secondo le regole concordate. */
class CallBuilder(private val lang: Lang) {

    companion object {
        /** Tag del segmento "gioco"/"play" iniziale: fa partire il tempo partita. */
        const val TAG_PLAY = "play"
        private const val SHORT = 300L
        private const val MEDIUM = 500L
        private const val START_GAP = 2000L
    }

    private fun num(n: Int): Seg = if (n in 0..Phrases.MAX_NUMBER) Seg.Clip("num_$n") else Seg.Say(n.toString())

    private fun serverName(s: MatchState, names: CallNames): String =
        if (s.rules.doubles) names.player(s.server, s.serverPlayer) else names.side(s.server)

    /** "Primo set" · "[nome] al servizio" · "gioco" con due secondi tra una frase e l'altra. */
    fun start(s: MatchState, names: CallNames): List<Seg> = listOf(
        Seg.Clip("first_set"),
        Seg.Pause(START_GAP),
        Seg.Say(serverName(s, names)),
        Seg.Clip("to_serve"),
        Seg.Pause(START_GAP),
        Seg.Clip("play", tag = TAG_PLAY),
    )

    /** Ripresa dopo una sospensione. */
    fun resume(): List<Seg> = listOf(Seg.Clip("play"))

    fun afterPoint(after: MatchState, t: Transition, names: CallNames): List<Seg> {
        val out = mutableListOf<Seg>()
        when {
            t.matchWinner != null -> out += matchEnd(after, t.matchWinner, names)
            t.setWinner != null -> {
                out += Seg.Clip("game")
                out += Seg.Say(names.side(t.setWinner))
                out += Seg.Pause(SHORT)
                out += setsStanding(after, names)
                if (t.matchTiebreakStarted) {
                    out += Seg.Pause(SHORT)
                    out += Seg.Clip("match_tiebreak")
                }
            }
            t.gameWinner != null -> {
                out += Seg.Clip("game")
                out += Seg.Say(names.side(t.gameWinner))
                out += Seg.Pause(SHORT)
                out += gamesStanding(after, names)
                if (t.tiebreakStarted) out += Seg.Clip("tiebreak")
            }
            after.inTiebreak -> out += tiebreakScore(after, names)
            else -> out += pointScore(after, names)
        }
        if (t.changeEnds && t.matchWinner == null) {
            out += Seg.Pause(MEDIUM)
            out += Seg.Clip("change_ends")
        }
        return out
    }

    /** "Correzione" seguita dal punteggio attuale. */
    fun correction(s: MatchState, names: CallNames): List<Seg> = listOf(Seg.Clip("correction")) + standing(s, names)

    /** Punteggio attuale, detto nel modo più utile per il momento della partita. */
    fun standing(s: MatchState, names: CallNames): List<Seg> = when {
        s.isFinished -> emptyList()
        s.inTiebreak -> tiebreakScore(s, names)
        s.pt1 == 0 && s.pt2 == 0 -> when {
            s.g1 != 0 || s.g2 != 0 -> gamesStanding(s, names)
            s.sets.isNotEmpty() -> setsStanding(s, names)
            else -> emptyList()
        }
        else -> pointScore(s, names)
    }

    /** Game normale: sempre dal punto di vista di chi serve ("quindici zero", "zero quaranta"). */
    fun pointScore(s: MatchState, names: CallNames): List<Seg> {
        val sp = s.points(s.server)
        val rp = s.points(s.receiver)
        if (sp >= 3 && rp >= 3) {
            if (sp == rp) {
                return if (s.rules.noAd) listOf(Seg.Clip("deuce"), Seg.Clip("deciding_point")) else listOf(Seg.Clip("deuce"))
            }
            val leader = if (sp > rp) s.server else s.receiver
            return listOf(Seg.Clip("advantage"), Seg.Say(names.side(leader)))
        }
        return listOf(Seg.Clip("score_${sp}_$rp"))
    }

    /** Tie-break: sempre dal punto di vista di chi conduce ("tre a uno Rossi", "sei pari"). */
    fun tiebreakScore(s: MatchState, names: CallNames): List<Seg> {
        val a = maxOf(s.pt1, s.pt2)
        val b = minOf(s.pt1, s.pt2)
        if (a == b) return listOf(num(a), Seg.Clip("all"))
        val leader = if (s.pt1 > s.pt2) Side.P1 else Side.P2
        return when (lang) {
            Lang.IT -> listOf(num(a), Seg.Clip("to"), num(b), Seg.Say(names.side(leader)))
            Lang.EN -> listOf(num(a), num(b), Seg.Say(names.side(leader)))
        }
    }

    /** "[nome] conduce tre giochi a due" oppure "due giochi pari". */
    fun gamesStanding(s: MatchState, names: CallNames): List<Seg> {
        if (s.g1 == s.g2) return listOf(Seg.Clip("games_all_${s.g1.coerceIn(1, 6)}"))
        val leader = if (s.g1 > s.g2) Side.P1 else Side.P2
        val a = maxOf(s.g1, s.g2)
        val b = minOf(s.g1, s.g2)
        return listOf(Seg.Say(names.side(leader)), Seg.Clip("leads"), Seg.Clip("games_${a}_$b"))
    }

    /** "[nome] conduce un set a zero" oppure "un set pari". */
    fun setsStanding(s: MatchState, names: CallNames): List<Seg> {
        val s1 = s.setsWon(Side.P1)
        val s2 = s.setsWon(Side.P2)
        if (s1 == s2) return listOf(Seg.Clip("sets_all_1"))
        val leader = if (s1 > s2) Side.P1 else Side.P2
        return listOf(Seg.Say(names.side(leader)), Seg.Clip("leads"), Seg.Clip("sets_1_0"))
    }

    /** "Gioco, set, partita [nome], sei quattro, tre sei, sette cinque" (punteggi dal lato del vincitore). */
    fun matchEnd(s: MatchState, winner: Side, names: CallNames): List<Seg> {
        val out = mutableListOf<Seg>(Seg.Clip("game_set_match"), Seg.Say(names.side(winner)))
        for (set in s.sets) {
            out += Seg.Pause(SHORT)
            out += num(set.shown(winner))
            out += num(set.shown(winner.other))
        }
        return out
    }

    /** Testo leggibile della chiamata (per test, log e riserva TTS). */
    fun render(segs: List<Seg>): String = segs.mapNotNull {
        when (it) {
            is Seg.Clip -> Phrases.text(it.key, lang)
            is Seg.Say -> it.text
            is Seg.Pause -> null
        }
    }.joinToString(" ")
}
