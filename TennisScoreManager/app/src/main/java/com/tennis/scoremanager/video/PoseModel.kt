// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.video

import android.content.Context
import android.os.Build
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Il modello MoveNet (Google, Apache 2.0) non sta nell'app: si scarica una volta sola, se lo si vuole, dalla release
 * del progetto su GitHub, e si usa solo se l'impronta sha256 è quella giusta. Le clip non lasciano mai il telefono.
 */
object PoseModel {
    const val URL = "https://github.com/steve-linux/Tennis-Score-Manager/releases/download/pose-model-1/movenet_singlepose_thunder_int8_v1.tflite"
    const val SHA256 = "b72fed22707cd6fb94b5a248b9bddb9c062b9f445471b4fa263407cf6d222011"
    const val SIZE_BYTES = 7_126_768L

    fun file(context: Context): File = File(context.filesDir, "pose/movenet_thunder_int8.tflite")

    fun installed(context: Context): Boolean = file(context).let { it.exists() && it.length() == SIZE_BYTES }

    /** LiteRT c'è solo per i processori ARM (le versioni x86 non sono nell'app). */
    fun supported(): Boolean = Build.SUPPORTED_ABIS.any { it == "arm64-v8a" || it == "armeabi-v7a" }

    fun delete(context: Context) {
        file(context).delete()
    }

    sealed class Result {
        data object Ok : Result()
        data object Network : Result()
        /** Scaricato ma non è il file atteso (cambiato o rovinato): non si usa. */
        data object BadFile : Result()
    }

    /** Scarica in un file temporaneo, controlla dimensione e sha256, poi lo mette al suo posto. Bloccante: da IO. */
    fun download(context: Context, onProgress: (Float) -> Unit): Result {
        val dest = file(context)
        dest.parentFile?.mkdirs()
        val tmp = File(dest.parentFile, dest.name + ".part")
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            var conn = URL(URL).openConnection() as HttpURLConnection
            // GitHub manda a un altro indirizzo per i file delle release: si segue a mano (anche da https a https).
            var hops = 0
            while (conn.responseCode in 300..399 && hops < 5) {
                val next = conn.getHeaderField("Location") ?: break
                conn.disconnect()
                conn = URL(next).openConnection() as HttpURLConnection
                hops++
            }
            if (conn.responseCode != 200) return Result.Network
            var read = 0L
            conn.inputStream.use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        digest.update(buf, 0, n)
                        read += n
                        if (read > SIZE_BYTES) return Result.BadFile
                        onProgress(read / SIZE_BYTES.toFloat())
                    }
                }
            }
            conn.disconnect()
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            if (read != SIZE_BYTES || hash != SHA256) return Result.BadFile
            if (!tmp.renameTo(dest)) {
                dest.delete()
                if (!tmp.renameTo(dest)) return Result.Network
            }
            Result.Ok
        } catch (e: Exception) {
            Result.Network
        } finally {
            tmp.delete()
        }
    }
}
