package com.tennis.scoremanager.voice

import com.tennis.scoremanager.model.Lang

/**
 * Correzioni di pronuncia per la sintesi vocale, verificate trascrivendo l'audio dei motori reali:
 * - Samsung legge "primo set" come "primo settembre": "sèt" (con l'accento) non viene espanso da nessun motore;
 * - "tie-break" diventa "die breaka" (Samsung) o "time break" (Google): "taibrèk" è letto giusto da entrambi;
 * - Samsung pronuncia male "gioco" isolato (sembra "Giacomo"); dentro una frase va bene, da solo si usa "giuoco".
 * Il testo mostrato a schermo e nel file LEGGIMI resta quello normale.
 */
object Pronunciation {
    const val SAMSUNG = "com.samsung.SMT"

    private val wordSet = Regex("\\bset\\b", RegexOption.IGNORE_CASE)
    private val tieBreak = Regex("(?<!super )tie-break", RegexOption.IGNORE_CASE)

    fun fix(text: String, lang: Lang, engine: String?): String {
        if (lang != Lang.IT) return text
        var t = tieBreak.replace(text, "taibrèk")
        t = wordSet.replace(t, "sèt")
        if (engine == SAMSUNG && t.trim().trimEnd('.', '!', ',').equals("gioco", ignoreCase = true)) t = "giuoco"
        return t
    }
}
