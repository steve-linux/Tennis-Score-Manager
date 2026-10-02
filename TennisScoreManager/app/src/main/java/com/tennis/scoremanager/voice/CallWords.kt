package com.tennis.scoremanager.voice

import com.tennis.scoremanager.model.Lang

/**
 * Parole e ordine delle chiamate dell'arbitro in una lingua, presi dai testi ufficiali per i giudici di sedia:
 * ITF (inglese), FFT «L'arbitrage en 255 questions» e ITF in francese, RFET «Deberes y procedimientos» (spagnolo),
 * DTB (tedesco), FPT (portoghese). Tutto in lettere: i motori vocali leggono male cifre e trattini ("6-4").
 */
internal abstract class CallWords {
    /** Frasi fisse: una per ciascuna chiave di [Phrases.FIXED_KEYS]. */
    abstract val fixed: Map<String, String>

    /** Numeri da 0 a [Phrases.MAX_NUMBER] (tie-break e punteggi dei set a fine partita). */
    abstract val numbers: List<String>

    /** Punti del game: 0, 15, 30, 40. */
    abstract val points: List<String>

    fun number(n: Int): String = numbers.getOrElse(n) { n.toString() }

    /** Lo zero nei set letti a fine partita ([Phrases.SET_ZERO]): di norma è il numero. */
    open val setZero: String get() = number(0)

    /** Punteggio del game dal lato di chi serve (mai 0-0 né 40-40): "quindici zero", "trenta pari". */
    open fun score(s: Int, r: Int): String =
        if (s == r) "${points[s]} ${fixed.getValue("all")}" else "${points[s]} ${points[r]}"

    /** Game del set dal lato di chi conduce: "tre giochi a due". */
    abstract fun games(a: Int, b: Int): String

    /** Game pari: "due giochi pari". */
    abstract fun gamesAll(n: Int): String

    /** "Rossi al servizio" (nome prima) oppure "Au service Dupont" (nome dopo). */
    open val nameBeforeToServe: Boolean = true

    /**
     * 10-6 … 10-9 detti di seguito suonano come un numero solo: "dix huit" = diciotto, "diez ocho" = "dieciocho",
     * "dez oito" = "dezoito" (verificato trascrivendo le voci Google). Lì si dice "a" anche dove di solito no.
     */
    protected fun soundsLikeOneNumber(a: Int, b: Int): Boolean = a == 10 && b in 6..9

    /** Nel tie-break, tra i due numeri si dice "a" ("tre a uno Rossi")? */
    open fun tiebreakTo(a: Int, b: Int): Boolean = soundsLikeOneNumber(a, b)

    /** Nei set letti a fine partita, tra i due numeri si dice "a"? */
    open fun setScoreTo(a: Int, b: Int): Boolean = soundsLikeOneNumber(a, b)

    companion object {
        fun of(lang: Lang): CallWords = when (lang) {
            Lang.IT -> ItWords
            Lang.EN -> EnWords
            Lang.FR -> FrWords
            Lang.DE -> DeWords
            Lang.ES -> EsWords
            Lang.PT -> PtWords
        }
    }
}

internal object ItWords : CallWords() {
    override val fixed = mapOf(
        "first_set" to "primo set",
        "to_serve" to "al servizio",
        "play" to "gioco",
        "deuce" to "parità",
        "advantage" to "vantaggio",
        "deciding_point" to "punto decisivo",
        "game" to "gioco",
        "leads" to "conduce",
        "sets_1_0" to "un set a zero",
        "sets_all_1" to "un set pari",
        "tiebreak" to "tie-break",
        "match_tiebreak" to "super tie-break",
        "to" to "a",
        "all" to "pari",
        "game_set_match" to "gioco, set, partita",
        "change_ends" to "cambio campo",
        "correction" to "correzione",
    )
    override val numbers = listOf(
        "zero", "uno", "due", "tre", "quattro", "cinque", "sei", "sette", "otto", "nove", "dieci",
        "undici", "dodici", "tredici", "quattordici", "quindici", "sedici", "diciassette", "diciotto",
        "diciannove", "venti", "ventuno", "ventidue", "ventitré", "ventiquattro", "venticinque",
        "ventisei", "ventisette", "ventotto", "ventinove", "trenta",
    )
    override val points = listOf("zero", "quindici", "trenta", "quaranta")
    private fun g(n: Int) = if (n == 1) "un gioco" else "${number(n)} giochi"
    override fun games(a: Int, b: Int) = "${g(a)} a ${number(b)}"
    override fun gamesAll(n: Int) = "${g(n)} pari"
    override fun tiebreakTo(a: Int, b: Int) = true
    override fun setScoreTo(a: Int, b: Int) = false
}

internal object EnWords : CallWords() {
    override val fixed = mapOf(
        "first_set" to "first set",
        "to_serve" to "to serve",
        "play" to "play",
        "deuce" to "deuce",
        "advantage" to "advantage",
        "deciding_point" to "deciding point",
        "game" to "game",
        "leads" to "leads",
        "sets_1_0" to "one set to love",
        "sets_all_1" to "one set all",
        "tiebreak" to "tie-break",
        "match_tiebreak" to "match tie-break",
        "to" to "to",
        "all" to "all",
        "game_set_match" to "game, set and match",
        "change_ends" to "change ends",
        "correction" to "correction",
    )
    override val numbers = listOf(
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
        "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen",
        "nineteen", "twenty", "twenty-one", "twenty-two", "twenty-three", "twenty-four", "twenty-five",
        "twenty-six", "twenty-seven", "twenty-eight", "twenty-nine", "thirty",
    )
    override val points = listOf("love", "fifteen", "thirty", "forty")
    // ITF: i set si leggono "six love, six four"; nel tie-break invece "one zero".
    override val setZero = "love"
    private fun g(n: Int) = if (n == 1) "one game" else "${number(n)} games"
    override fun games(a: Int, b: Int) = "${g(a)} to ${if (b == 0) "love" else number(b)}"
    override fun gamesAll(n: Int) = "${g(n)} all"
    override fun tiebreakTo(a: Int, b: Int) = false
    override fun setScoreTo(a: Int, b: Int) = false
}

/** Francese FFT: il set è la "manche" (tranne in "jeu, set et match"), 15-15 è "quinze A", il nome va dopo "au service". */
internal object FrWords : CallWords() {
    override val fixed = mapOf(
        "first_set" to "première manche",
        "to_serve" to "au service",
        "play" to "jouez",
        "deuce" to "égalité",
        "advantage" to "avantage",
        "deciding_point" to "point décisif",
        "game" to "jeu",
        "leads" to "mène",
        "sets_1_0" to "une manche à zéro",
        "sets_all_1" to "une manche partout",
        "tiebreak" to "jeu décisif",
        "match_tiebreak" to "super jeu décisif",
        "to" to "à",
        "all" to "partout",
        "game_set_match" to "jeu, set et match",
        "change_ends" to "changement de côté",
        "correction" to "correction",
    )
    override val numbers = listOf(
        "zéro", "un", "deux", "trois", "quatre", "cinq", "six", "sept", "huit", "neuf", "dix",
        "onze", "douze", "treize", "quatorze", "quinze", "seize", "dix-sept", "dix-huit",
        "dix-neuf", "vingt", "vingt et un", "vingt-deux", "vingt-trois", "vingt-quatre", "vingt-cinq",
        "vingt-six", "vingt-sept", "vingt-huit", "vingt-neuf", "trente",
    )
    override val points = listOf("zéro", "quinze", "trente", "quarante")
    override fun score(s: Int, r: Int) = if (s == r) "${points[s]} A" else "${points[s]}-${points[r]}"
    private fun g(n: Int) = if (n == 1) "un jeu" else "${number(n)} jeux"
    override fun games(a: Int, b: Int) = "${g(a)} à ${number(b)}"
    override fun gamesAll(n: Int) = "${g(n)} partout"
    override val nameBeforeToServe = false
}

/**
 * Spagnolo RFET: "iguales" per i pari e per 40-40, "gana" per chi conduce, "al servicio" prima del nome,
 * "jueguen" per iniziare. "uno" in fondo alla frase ("dos juegos a uno"), "un" davanti al nome ("un juego").
 */
internal object EsWords : CallWords() {
    override val fixed = mapOf(
        "first_set" to "primer set",
        "to_serve" to "al servicio",
        "play" to "jueguen",
        "deuce" to "iguales",
        "advantage" to "ventaja",
        "deciding_point" to "punto decisivo",
        "game" to "juego",
        "leads" to "gana",
        "sets_1_0" to "un set a cero",
        "sets_all_1" to "un set iguales",
        "tiebreak" to "tie-break",
        "match_tiebreak" to "súper tie-break",
        "to" to "a",
        "all" to "iguales",
        "game_set_match" to "juego, set y partido",
        "change_ends" to "cambio de lado",
        "correction" to "corrección",
    )
    override val numbers = listOf(
        "cero", "uno", "dos", "tres", "cuatro", "cinco", "seis", "siete", "ocho", "nueve", "diez",
        "once", "doce", "trece", "catorce", "quince", "dieciséis", "diecisiete", "dieciocho",
        "diecinueve", "veinte", "veintiuno", "veintidós", "veintitrés", "veinticuatro", "veinticinco",
        "veintiséis", "veintisiete", "veintiocho", "veintinueve", "treinta",
    )
    override val points = listOf("cero", "quince", "treinta", "cuarenta")
    private fun g(n: Int) = if (n == 1) "un juego" else "${number(n)} juegos"
    override fun games(a: Int, b: Int) = "${g(a)} a ${number(b)}"
    override fun gamesAll(n: Int) = "${g(n)} iguales"
    override val nameBeforeToServe = false
}

/**
 * Tedesco DTB (modulo «Korrekte Ansagen» del BTV, documentazione Swiss Tennis): "Aufschlag" prima del nome,
 * "beide" per i pari, i punteggi con "zu" ("drei zu zwei", come si legge "3:2"), ma i punti del game senza.
 * "eins" da solo, "ein" davanti al nome ("ein Spiel beide").
 */
internal object DeWords : CallWords() {
    override val fixed = mapOf(
        "first_set" to "erster Satz",
        "to_serve" to "Aufschlag",
        "play" to "spielen",
        "deuce" to "Einstand",
        "advantage" to "Vorteil",
        "deciding_point" to "entscheidender Punkt",
        "game" to "Spiel",
        "leads" to "führt",
        "sets_1_0" to "eins zu null in Sätzen",
        "sets_all_1" to "ein Satz beide",
        "tiebreak" to "Tie-Break",
        "match_tiebreak" to "Match-Tie-Break",
        "to" to "zu",
        "all" to "beide",
        "game_set_match" to "Spiel, Satz und Sieg",
        "change_ends" to "Seitenwechsel",
        "correction" to "Korrektur",
    )
    override val numbers = listOf(
        "null", "eins", "zwei", "drei", "vier", "fünf", "sechs", "sieben", "acht", "neun", "zehn",
        "elf", "zwölf", "dreizehn", "vierzehn", "fünfzehn", "sechzehn", "siebzehn", "achtzehn",
        "neunzehn", "zwanzig", "einundzwanzig", "zweiundzwanzig", "dreiundzwanzig", "vierundzwanzig", "fünfundzwanzig",
        "sechsundzwanzig", "siebenundzwanzig", "achtundzwanzig", "neunundzwanzig", "dreißig",
    )
    override val points = listOf("null", "fünfzehn", "dreißig", "vierzig")
    override fun games(a: Int, b: Int) = "${number(a)} zu ${number(b)}"
    override fun gamesAll(n: Int) = if (n == 1) "ein Spiel beide" else "${number(n)} beide"
    override val nameBeforeToServe = false
    override fun tiebreakTo(a: Int, b: Int) = true
    override fun setScoreTo(a: Int, b: Int) = true
}

/**
 * Portoghese: struttura del copione ufficiale FPT («Deveres e Procedimentos para Árbitros», l'unico pubblicato),
 * con parole che vanno bene in Portogallo e in Brasile ("jogo" e non "game", "partida" e non "encontro"),
 * "iguais" per i pari e grafia brasiliana dei numeri (la voce preferita è pt-BR).
 */
internal object PtWords : CallWords() {
    override val fixed = mapOf(
        "first_set" to "primeiro set",
        "to_serve" to "ao serviço",
        "play" to "joguem",
        "deuce" to "iguais",
        "advantage" to "vantagem",
        "deciding_point" to "ponto decisivo",
        "game" to "jogo",
        "leads" to "vence por",
        "sets_1_0" to "um set a zero",
        // "um set a um" suona "um sete a um" (7-1): al singolare "set" si pronuncia come "sete".
        "sets_all_1" to "sets iguais",
        "tiebreak" to "tie-break",
        "match_tiebreak" to "tie-break decisivo",
        "to" to "a",
        "all" to "iguais",
        "game_set_match" to "jogo, set e partida",
        "change_ends" to "troca de lado",
        "correction" to "correção",
    )
    override val numbers = listOf(
        "zero", "um", "dois", "três", "quatro", "cinco", "seis", "sete", "oito", "nove", "dez",
        "onze", "doze", "treze", "catorze", "quinze", "dezesseis", "dezessete", "dezoito",
        "dezenove", "vinte", "vinte e um", "vinte e dois", "vinte e três", "vinte e quatro", "vinte e cinco",
        "vinte e seis", "vinte e sete", "vinte e oito", "vinte e nove", "trinta",
    )
    override val points = listOf("zero", "quinze", "trinta", "quarenta")
    override fun score(s: Int, r: Int) = if (s == r) "${points[s]} iguais" else "${points[s]}-${points[r]}"
    private fun g(n: Int) = if (n == 1) "um jogo" else "${number(n)} jogos"
    override fun games(a: Int, b: Int) = "${g(a)} a ${number(b)}"
    override fun gamesAll(n: Int) = if (n == 1) "um jogo igual" else "${g(n)} iguais"
    override fun tiebreakTo(a: Int, b: Int) = true
}
