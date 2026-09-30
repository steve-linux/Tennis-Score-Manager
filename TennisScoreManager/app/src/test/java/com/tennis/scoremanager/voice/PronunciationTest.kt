package com.tennis.scoremanager.voice

import com.tennis.scoremanager.model.Lang
import org.junit.Assert.assertEquals
import org.junit.Test

class PronunciationTest {

    @Test
    fun italianFixes() {
        assertEquals("primo sèt", Pronunciation.fix("primo set", Lang.IT, null))
        assertEquals("gioco, sèt, partita Rossi", Pronunciation.fix("gioco, set, partita Rossi", Lang.IT, null))
        assertEquals("gioco Rossi sei giochi pari taibrèk", Pronunciation.fix("gioco Rossi sei giochi pari tie-break", Lang.IT, null))
        assertEquals("un sèt pari super tie-break", Pronunciation.fix("un set pari super tie-break", Lang.IT, null))
        // "Settimo" e i nomi che contengono "set" non vanno toccati
        assertEquals("Setti conduce", Pronunciation.fix("Setti conduce", Lang.IT, null))
    }

    @Test
    fun samsungIsolatedGioco() {
        assertEquals("giuoco", Pronunciation.fix("gioco", Lang.IT, Pronunciation.SAMSUNG))
        assertEquals("gioco", Pronunciation.fix("gioco", Lang.IT, "com.google.android.tts"))
        assertEquals("gioco Rossi", Pronunciation.fix("gioco Rossi", Lang.IT, Pronunciation.SAMSUNG))
    }

    @Test
    fun englishUntouched() {
        assertEquals("first set Rossi to serve", Pronunciation.fix("first set Rossi to serve", Lang.EN, Pronunciation.SAMSUNG))
    }

    @Test
    fun germanTieBreak() {
        assertEquals("Taibreak", Pronunciation.fix("Tie-Break", Lang.DE, null))
        assertEquals("Match-Taibreak", Pronunciation.fix("Match-Tie-Break", Lang.DE, null))
        assertEquals("sechs beide, Taibreak", Pronunciation.fix("sechs beide Tie-Break", Lang.DE, null))
    }

    @Test
    fun frenchIsolatedA() {
        assertEquals("a", Pronunciation.fix("à", Lang.FR, null))
        assertEquals("dix à huit Rossi", Pronunciation.fix("dix à huit Rossi", Lang.FR, null))
    }

    @Test
    fun otherLanguagesUntouched() {
        assertEquals("juego Rossi seis juegos iguales tie-break", Pronunciation.fix("juego Rossi seis juegos iguales tie-break", Lang.ES, null))
        assertEquals("primeiro set", Pronunciation.fix("primeiro set", Lang.PT, null))
    }
}
