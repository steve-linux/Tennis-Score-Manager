// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.ui

import com.tennis.scoremanager.ble.BandProtocol
import com.tennis.scoremanager.ble.BandSettings
import com.tennis.scoremanager.model.Lang
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier
import java.text.SimpleDateFormat
import java.util.Date

class StringsTest {

    /** Prova gli argomenti finché i tipi combaciano (le lambda controllano i tipi solo quando vengono chiamate). */
    private fun call(f: (List<Any?>) -> Any?, tries: List<List<Any?>>): String =
        tries.firstNotNullOfOrNull { args -> runCatching { f(args) as String }.getOrNull() } ?: error("nessun argomento valido")

    /** Ogni testo di ogni lingua, funzioni comprese (chiamate con argomenti di prova). */
    @Suppress("UNCHECKED_CAST")
    private fun texts(s: Strings): Map<String, String> =
        Strings::class.java.methods
            .filter { it.name.startsWith("get") && it.parameterCount == 0 && !Modifier.isStatic(it.modifiers) }
            .associate { m ->
                val name = m.name.removePrefix("get")
                val text = when (val v = m.invoke(s)) {
                    is String -> v
                    is Function1<*, *> -> call({ (v as Function1<Any?, Any?>)(it[0]) }, listOf(listOf(2), listOf("Rossi"), listOf(listOf("Rossi"))))
                    is Function2<*, *, *> -> call(
                        { (v as Function2<Any?, Any?, Any?>)(it[0], it[1]) },
                        listOf(listOf("Rossi", "6-4"), listOf("Rossi", 2), listOf(2, 3), listOf(true, false)),
                    )
                    is Function3<*, *, *, *> -> call(
                        { (v as Function3<Any?, Any?, Any?, Any?>)(it[0], it[1], it[2]) },
                        listOf(listOf("Rossi", "6-4", false)),
                    )
                    is Function4<*, *, *, *, *> -> call(
                        { (v as Function4<Any?, Any?, Any?, Any?, Any?>)(it[0], it[1], it[2], it[3]) },
                        listOf(listOf("1", "2", "3", "4")),
                    )
                    else -> error("$name: ${v?.javaClass}")
                }
                name to text
            }

    @Test
    fun everyLanguageHasEveryText() {
        val it = texts(ItStrings)
        assertTrue(it.size > 250)
        for (lang in Lang.entries) {
            val t = texts(stringsFor(lang))
            assertEquals(it.keys, t.keys)
            for ((k, v) in t) assertTrue("$lang $k", v.isNotBlank() || k == "TeamJoiner")
        }
    }

    @Test
    fun doublesAndTwoBandsUsePlural() {
        assertEquals("Vince Rossi\n6-4 6-3", ItStrings.endDialogText("Rossi", "6-4 6-3", false))
        assertEquals("Vincono Rossi e Bianchi\n6-4 6-3", ItStrings.endDialogText("Rossi e Bianchi", "6-4 6-3", true))
        assertEquals("Rossi and Bianchi win\n6-4", EnStrings.endDialogText("Rossi and Bianchi", "6-4", true))
        assertEquals("Ganan Rossi y Bianchi\n6-4", stringsFor(Lang.ES).endDialogText("Rossi y Bianchi", "6-4", true))
        for (lang in Lang.entries) {
            val s = stringsFor(lang)
            val one = s.bandsMissingText(listOf("Rossi"))
            val two = s.bandsMissingText(listOf("Rossi", "Bianchi"))
            assertTrue("$lang: $two", "Rossi, Bianchi" in two)
            // Al plurale cambia anche il resto della frase, non solo l'elenco dei nomi.
            assertTrue("$lang: $one / $two", two.replace(", Bianchi", "") != one)
        }
    }

    @Test
    fun newLanguagesAreActuallyTranslated() {
        val en = texts(EnStrings)
        for (lang in listOf(Lang.FR, Lang.DE, Lang.ES, Lang.PT)) {
            val t = texts(stringsFor(lang))
            val same = t.filter { (k, v) -> v == en[k] && v.any { c -> c.isLetter() } }.keys
            // Restano uguali solo nomi propri e sigle: Bluetooth, OK, Firmware, Audio On/Off, online...
            assertTrue("$lang copia l'inglese in: $same", same.size < 30)
            val sentences = same.filter { k -> (en[k] ?: "").count { c -> c == ' ' } >= 3 }
            assertTrue("$lang frasi non tradotte: $sentences", sentences.isEmpty())
        }
    }

    @Test
    fun bandTextsFitTheWristband() {
        for (lang in Lang.entries) {
            val s = stringsFor(lang)
            val band = listOf(
                s.bandPaired, s.bandPlay, s.bandChangeEnds, s.bandTiebreak, s.bandSet, s.bandSuspended,
                s.bandGameSetMatch, s.bandMatchOver, s.bandAppClosed, s.bandOffFromApp, s.bandBatteryLow,
            )
            for (b in band) {
                // Il braccialetto tronca la riga 1 a 18 caratteri: meglio che non serva.
                assertEquals("$lang «$b»", BandProtocol.clean(b, 99), BandProtocol.clean(b))
                assertTrue("$lang «$b»", BandProtocol.clean(b).isNotBlank())
            }
        }
    }

    @Test
    fun datePatternsAreValid() {
        for (lang in Lang.entries) {
            val d = SimpleDateFormat(stringsFor(lang).datePattern, lang.locale).format(Date(0))
            assertTrue("$lang $d", d.contains("1970"))
        }
    }

    @Test
    fun bandSettingsCarryTheLanguage() {
        val s = BandSettings.parse("fw=2.2;name=TSM-1A2B;bri=20;pt=3;vol=50;flip=0;pair=30;lost=180;idle=30;lang=fr")!!
        assertEquals("fr", s.lang)
        assertEquals("", BandSettings.parse("fw=2.1;name=TSM-1A2B;bri=20")!!.lang)
        assertEquals("lang=de", BandSettings.languageConfig("de"))
        // Le impostazioni scritte dal pannello non toccano la lingua (quella la manda l'app da sola).
        assertTrue("lang" !in s.encode())
    }
}
