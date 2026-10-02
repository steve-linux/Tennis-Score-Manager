package com.tennis.scoremanager.voice

import com.tennis.scoremanager.model.Lang
import com.tennis.scoremanager.model.MatchFormat
import com.tennis.scoremanager.model.RulesConfig
import com.tennis.scoremanager.model.ScoreEngine
import com.tennis.scoremanager.model.Side
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** ZIP di registrazioni (quali file, in che lingua, ZIP rovinati) e lo zero dei set a fine partita. */
class VoiceFilesTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val audio = ByteArray(200) { (it * 7).toByte() }

    // ---------------------------------------------------------------- dove va ogni file dello ZIP

    @Test
    fun languageIsTheFolderThatContainsTheFile() {
        assertEquals(Lang.IT to "deuce.mp3", VoiceZip.target("it/deuce.mp3", Lang.FR))
        assertEquals(Lang.EN to "game.wav", VoiceZip.target("voice/EN/game.WAV", Lang.FR))
        assertEquals(Lang.FR to "deuce.wav", VoiceZip.target("Registrazioni\\fr\\deuce.wav", Lang.IT))
    }

    @Test
    fun filesOutsideLanguageFoldersUseTheCurrentLanguage() {
        assertEquals(Lang.DE to "deuce.ogg", VoiceZip.target("deuce.ogg", Lang.DE))
        assertEquals(Lang.DE to "deuce.ogg", VoiceZip.target("Registrazioni/deuce.ogg", Lang.DE))
    }

    @Test
    fun generatedFilesAndUnknownNamesAreIgnored() {
        // La cartella voice/ dell'app zippata così com'è: i file generati non diventano registrazioni.
        assertNull(VoiceZip.target("it/tts/deuce.wav", Lang.IT))
        assertNull(VoiceZip.target("voice/it/tts/deuce.wav", Lang.IT))
        assertNull(VoiceZip.target("tts/deuce.wav", Lang.IT))
        assertNull(VoiceZip.target("voice/it/.tts-new/deuce.wav", Lang.IT))
        assertNull(VoiceZip.target("it/vecchie/deuce.mp3", Lang.IT))
        assertNull(VoiceZip.target("it/sconosciuta.mp3", Lang.IT))
        assertNull(VoiceZip.target("it/deuce.txt", Lang.IT))
        assertNull(VoiceZip.target("__MACOSX/it/._deuce.mp3", Lang.IT))
        assertNull(VoiceZip.target("it/", Lang.IT))
    }

    // ---------------------------------------------------------------- estrazione e installazione

    private fun zip(vararg entries: Pair<String, ByteArray>, stored: Boolean = false): File {
        val f = tmp.newFile()
        ZipOutputStream(f.outputStream()).use { z ->
            for ((name, data) in entries) {
                val e = ZipEntry(name)
                if (stored) {
                    e.method = ZipEntry.STORED
                    e.size = data.size.toLong()
                    e.crc = CRC32().apply { update(data) }.value
                }
                z.putNextEntry(e)
                z.write(data)
                z.closeEntry()
            }
        }
        return f
    }

    @Test
    fun importOfTheAppVoiceFolderKeepsRecordings() {
        val base = tmp.newFolder("voice")
        File(base, "it").mkdirs()
        File(base, "it/deuce.wav").writeBytes(audio)
        val z = zip(
            "voice/it/deuce.mp3" to audio,
            "voice/it/tts/deuce.wav" to audio, // dopo it/deuce.mp3 in ordine alfabetico: prima lo sostituiva
            "voice/en/game.wav" to audio,
            "voice/it/play.wav" to ByteArray(10), // troppo piccolo: si ignora
            "advantage.ogg" to audio,
        )
        val staging = tmp.newFolder("staging")
        assertEquals(3, VoiceZip.extract(z, staging, Lang.DE))
        assertEquals(3, VoiceZip.install(staging) { File(base, it.code).apply { mkdirs() } })
        assertEquals(setOf("deuce.mp3"), File(base, "it").list()!!.toSet())
        assertTrue(File(base, "en/game.wav").isFile)
        assertTrue(File(base, "de/advantage.ogg").isFile)
    }

    @Test
    fun truncatedZipFailsBeforeTouchingAnything() {
        val full = zip("it/deuce.mp3" to audio, "it/game.mp3" to audio).readBytes()
        // Tagliato alla fine dei dati: ZipInputStream lo leggeva "tutto" senza accorgersene.
        val cut = tmp.newFile().apply { writeBytes(full.copyOf(full.size - 60)) }
        val staging = tmp.newFolder("staging")
        assertTrue(runCatching { VoiceZip.extract(cut, staging, Lang.IT) }.isFailure)
    }

    @Test
    fun corruptedEntryFailsTheCrcCheck() {
        val bytes = zip("it/deuce.wav" to audio, stored = true).readBytes()
        // Un byte del file audio cambiato (i dati iniziano dopo l'intestazione locale: 30 byte + nome).
        val at = 30 + "it/deuce.wav".length + 50
        bytes[at] = (bytes[at] + 1).toByte()
        val bad = tmp.newFile().apply { writeBytes(bytes) }
        assertTrue(runCatching { VoiceZip.extract(bad, tmp.newFolder("staging"), Lang.IT) }.isFailure)
    }

    // ---------------------------------------------------------------- zero dei set

    @Test
    fun setZeroIsLoveOnlyInEnglish() {
        assertEquals("love", Phrases.text(Phrases.SET_ZERO, Lang.EN))
        assertNull(Phrases.alias(Phrases.SET_ZERO, Lang.EN))
        for (lang in Lang.entries - Lang.EN) {
            assertEquals(Phrases.text("num_0", lang), Phrases.text(Phrases.SET_ZERO, lang))
            // Stesso testo: va bene il file di "num_0" (le registrazioni già fatte restano complete).
            assertEquals("num_0", Phrases.alias(Phrases.SET_ZERO, lang))
        }
        assertFalse(Phrases.alias("num_0", Lang.IT) != null)
    }

    @Test
    fun matchTiebreakKeepsZero() {
        val calls = CallBuilder(Lang.EN)
        var s = ScoreEngine.initial(RulesConfig(firstServer = Side.P1, format = MatchFormat.TWO_SETS_MATCH_TIEBREAK))
        fun game(w: Side) = repeat(4) { s = ScoreEngine.pointWonBy(s, w).state }
        repeat(6) { game(Side.P1) }
        repeat(6) { game(Side.P2) }
        repeat(9) { s = ScoreEngine.pointWonBy(s, Side.P1).state }
        val step = ScoreEngine.pointWonBy(s, Side.P1)
        assertEquals(
            "game, set and match Rossi six love love six ten zero",
            calls.render(calls.afterPoint(step.state, step.transition!!, names)),
        )
    }

    private val names = object : CallNames {
        override fun side(side: Side) = if (side == Side.P1) "Rossi" else "Bianchi"
        override fun player(side: Side, index: Int) = side(side)
    }
}
