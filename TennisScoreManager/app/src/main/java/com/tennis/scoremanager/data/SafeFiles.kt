// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.data

import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Lettura e scrittura dei file di dati senza perderli: una scrittura non lascia mai un file a metà, e un file che non
 * si legge non viene sovrascritto in silenzio dal salvataggio dopo.
 */
object SafeFiles {
    /** Le copie messe da parte: "players.json.unreadable-1794332985659". */
    const val ASIDE = ".unreadable-"

    /**
     * Scrive [text] in [file]: prima in un file d'appoggio scritto fino in fondo sul disco (fsync, contro lo spegnimento
     * brusco), poi lo scambio col nome vero, che è atomico.
     */
    fun write(file: File, text: String) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        FileOutputStream(tmp).use { out ->
            out.write(text.toByteArray())
            out.fd.sync()
        }
        if (!tmp.renameTo(file)) {
            file.delete()
            if (!tmp.renameTo(file)) throw IOException("Non riesco a scrivere ${file.name}")
        }
    }

    /**
     * Legge [file] con [decode]; senza file, [default]. Un file che c'è ma non si legge (rovinato, o scritto da una
     * versione più nuova) non diventa vuoto in silenzio: se ne mette da parte una copia, che i salvataggi dopo non
     * toccano, e si chiama [onUnreadable] col suo nome.
     */
    fun <T> read(file: File, default: T, decode: (String) -> T, onUnreadable: (String) -> Unit = {}): T {
        if (!file.exists()) return default
        return try {
            decode(file.readText())
        } catch (e: Exception) {
            setAside(file)
            onUnreadable(file.name)
            default
        }
    }

    /** Le copie messe da parte di [file], dalla più vecchia. */
    fun asideCopies(file: File): List<File> =
        file.parentFile?.listFiles { f -> f.name.startsWith(file.name + ASIDE) }.orEmpty().sortedBy { it.name }

    /** Una copia di [file] accanto, una sola per contenuto: la stessa lettura fallita più volte non ne fa altre. */
    private fun setAside(file: File) {
        val dir = file.parentFile ?: return
        runCatching {
            val bytes = file.readBytes()
            if (asideCopies(file).none { it.length() == bytes.size.toLong() && it.readBytes().contentEquals(bytes) }) {
                File(dir, file.name + ASIDE + System.currentTimeMillis()).writeBytes(bytes)
            }
        }
    }
}
