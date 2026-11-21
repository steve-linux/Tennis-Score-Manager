// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later
package com.tennis.scoremanager.player

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.tennis.scoremanager.MatchController
import com.tennis.scoremanager.R
import com.tennis.scoremanager.data.PlayerStats
import com.tennis.scoremanager.data.Storage
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.graphics.scale

/**
 * Le foto: quella di ogni giocatore (ritagliata quadrata, anche sul tabellone TV), il ritratto di uomo o di donna
 * per chi non ce l'ha, e le foto ricordo della partita nel riepilogo. Quello che serve del resto dell'app è in [PlayerHost].
 */
class PhotosArea(
    host: PlayerHost,
    private val app: Application,
    private val storage: Storage,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
) : PlayerHost by host {
    /** Foto del giocatore col nome [name], ritagliata quadrata e rimpicciolita; null se non c'è. */
    fun loadPhoto(name: String): Bitmap? {
        val key = PlayerStats.key(name)
        if (key.isEmpty()) return null
        val f = storage.photoFile(key)
        return if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null
    }

    /** Foto scelta dalla galleria o scattata: si salva quadrata (centro dell'immagine) a [MatchController.PHOTO_PX] pixel. */
    fun setPhoto(name: String, uri: Uri) {
        val key = PlayerStats.key(name)
        if (key.isEmpty()) return
        scope.launch {
            val ok = withContext(io) {
                runCatching {
                    // ImageDecoder (Android 9+) raddrizza le foto girate (EXIF); prima si legge com'è.
                    val src = if (Build.VERSION.SDK_INT >= 28) {
                        ImageDecoder.decodeBitmap(ImageDecoder.createSource(app.contentResolver, uri)) { d, info, _ ->
                            val side = minOf(info.size.width, info.size.height)
                            d.setTargetSampleSize((side / (MatchController.PHOTO_PX * 2)).coerceAtLeast(1))
                            d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        }
                    } else {
                        app.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: error("foto illeggibile")
                    }
                    val side = minOf(src.width, src.height)
                    val square = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
                    val small = square.scale(MatchController.PHOTO_PX, MatchController.PHOTO_PX)
                    val out = ByteArrayOutputStream()
                    small.compress(Bitmap.CompressFormat.JPEG, 85, out)
                    storage.savePhoto(key, out.toByteArray())
                }.isSuccess
            }
            if (ok) photosVersion.value++ else toast(strings.photoError)
            publishTv()
        }
    }

    /** Donna solo se lo si è detto per quel nome; altrimenti (uomo o non detto) il ritratto è quello dell'uomo. */
    fun isFemale(name: String): Boolean = storage.playerSex(PlayerStats.key(name)) == "F"

    /** "M", "F" o "" per il giocatore [name]: vale per tutti i suoi avatar, finché non ha una foto. */
    fun setPlayerSex(name: String, sex: String) {
        val key = PlayerStats.key(name)
        if (key.isEmpty()) return
        storage.setPlayerSex(key, sex)
        photosVersion.value++
        publishTv()
    }

    /** Il ritratto di uomo o di donna per chi non ha la foto (PDF e highlights). */
    fun avatarBitmap(female: Boolean): Bitmap =
        BitmapFactory.decodeResource(app.resources, if (female) R.drawable.avatar_woman else R.drawable.avatar_man)

    fun removePhoto(name: String) {
        val key = PlayerStats.key(name)
        if (key.isEmpty()) return
        scope.launch {
            withContext(io) { storage.deletePhoto(key) }
            photosVersion.value++
            publishTv()
        }
    }


    /** Cresce a ogni foto della partita aggiunta o tolta. */
    val matchPhotosVersion = MutableStateFlow(0)

    fun matchPhotos(id: String): List<File> = storage.matchPhotos(id)

    /** Foto aggiunte al riepilogo a schermo: rimpicciolite a [MatchController.MATCH_PHOTO_PX] sul lato lungo, fino a 10 per partita. */
    fun addMatchPhotos(uris: List<Uri>) {
        val id = summary.value?.record?.id ?: return
        val s = strings
        scope.launch {
            val (added, full) = withContext(io) {
                var added = 0
                var full = false
                for (uri in uris) {
                    if (storage.matchPhotos(id).size >= Storage.MAX_MATCH_PHOTOS) { full = true; break }
                    val jpeg = runCatching { scaledJpeg(uri, MatchController.MATCH_PHOTO_PX) }.getOrNull() ?: continue
                    if (storage.addMatchPhoto(id, jpeg)) added++
                }
                added to full
            }
            matchPhotosVersion.value++
            if (full) toast(s.matchPhotosFull)
            else if (added == 0) toast(s.photoError)
        }
    }

    fun removeMatchPhoto(file: File) {
        scope.launch {
            withContext(io) { file.delete() }
            matchPhotosVersion.value++
        }
    }

    /** Foto [uri] rimpicciolita (lato lungo [maxSide]) in JPEG; ImageDecoder (Android 9+) la raddrizza. */
    internal fun scaledJpeg(uri: Uri, maxSide: Int): ByteArray {
        val src = if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(app.contentResolver, uri)) { d, info, _ ->
                val long = maxOf(info.size.width, info.size.height)
                d.setTargetSampleSize((long / maxSide).coerceAtLeast(1))
                d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            app.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: error("foto illeggibile")
        }
        val scale = minOf(1f, maxSide.toFloat() / maxOf(src.width, src.height))
        val img = if (scale < 1f) src.scale((src.width * scale).toInt(), (src.height * scale).toInt()) else src
        return ByteArrayOutputStream().also { img.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
    }

    /** File dove la fotocamera salva la foto da aggiungere al riepilogo (nella cache condivisibile). */
    fun cameraUri(): Uri {
        val f = File(File(app.cacheDir, "share").apply { mkdirs() }, "camera.jpg")
        return FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", f)
    }
}
