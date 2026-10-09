// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.voice

import com.tennis.scoremanager.model.Lang

/**
 * Correzioni di pronuncia per la sintesi vocale, verificate trascrivendo l'audio dei motori reali:
 * - Samsung legge "primo set" come "primo settembre": "sèt" (con l'accento) non viene espanso da nessun motore;
 * - "tie-break" diventa "die breaka" (Samsung) o "time break" (Google): "taibrèk" è letto giusto da entrambi;
 * - Samsung pronuncia male "gioco" isolato (sembra "Giacomo"); dentro una frase va bene, da solo si usa "giuoco";
 * - tedesco (Google): "Tie-Break" diventa "Teilbreg" o "Tea Break", "Taibreak" è letto giusto; senza una virgola
 *   prima, "sechs beide Taibreak" si impasta in "sechs bei Detailbreak";
 * - francese (Google): "à" da sola (il file vocale della chiave "to") è letta "a accento grave".
 * Il testo mostrato a schermo e nel file LEGGIMI resta quello normale.
 */
object Pronunciation {
    const val SAMSUNG = "com.samsung.SMT"

    private val wordSet = Regex("\\bset\\b", RegexOption.IGNORE_CASE)
    private val tieBreak = Regex("(?<!super )tie-break", RegexOption.IGNORE_CASE)
    private val tieBreakDe = Regex("tie-break", RegexOption.IGNORE_CASE)
    private val beforeTieBreakDe = Regex("(?<=[^\\s,-]) +(?=tie-break)", RegexOption.IGNORE_CASE)

    fun fix(text: String, lang: Lang, engine: String?): String = when (lang) {
        Lang.IT -> {
            var t = tieBreak.replace(text, "taibrèk")
            t = wordSet.replace(t, "sèt")
            if (engine == SAMSUNG && t.trim().trimEnd('.', '!', ',').equals("gioco", ignoreCase = true)) t = "giuoco"
            t
        }
        Lang.DE -> tieBreakDe.replace(beforeTieBreakDe.replace(text, ", "), "Taibreak")
        Lang.FR -> if (text.trim() == "à") "a" else text
        else -> text
    }
}
