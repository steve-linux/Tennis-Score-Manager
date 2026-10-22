// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tennis.scoremanager.MatchController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Foto del giocatore in un cerchio col bordo del suo colore; senza foto le iniziali. Le foto stanno solo su
 * questo telefono (passano a un altro col trasferimento) e si ritrovano dal nome.
 */
@Composable
fun PlayerAvatar(c: MatchController, name: String, color: Color, size: Dp, modifier: Modifier = Modifier) {
    val version by c.photosVersion.collectAsState()
    val photo by produceState<ImageBitmap?>(null, name, version) {
        value = withContext(Dispatchers.IO) { c.loadPhoto(name)?.asImageBitmap() }
    }
    Box(
        modifier.size(size).clip(CircleShape).background(color.copy(alpha = 0.22f)).border(if (size < 40.dp) 1.5.dp else 2.dp, color, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        val p = photo
        when {
            p != null -> Image(p, null, contentScale = ContentScale.Crop, modifier = Modifier.size(size))
            name.isNotBlank() -> Text(initials(name), color = color, fontWeight = FontWeight.Black, fontSize = (size.value * 0.38f).sp)
            else -> Icon(Icons.Filled.Person, null, tint = color, modifier = Modifier.size(size * 0.55f))
        }
    }
}

private fun initials(name: String): String =
    name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2).joinToString("") { it.take(1).uppercase() }

/**
 * Avatar da toccare per mettere la foto: senza foto apre subito il selettore di foto di Android (niente permessi),
 * con la foto chiede se cambiarla o toglierla. Senza nome non si può: la foto si ritrova dal nome.
 */
@Composable
fun EditablePlayerAvatar(c: MatchController, name: String, color: Color, size: Dp, modifier: Modifier = Modifier) {
    val s = LocalStrings.current
    val version by c.photosVersion.collectAsState()
    val hasPhoto = remember(name, version) { c.loadPhoto(name) != null }
    var ask by remember { mutableStateOf(false) }
    var needName by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) c.setPhoto(name, uri)
    }
    fun pick() = picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    Box(modifier.clickable {
        when {
            name.isBlank() -> needName = true
            hasPhoto -> ask = true
            else -> pick()
        }
    }) {
        PlayerAvatar(c, name, color, size)
        if (!hasPhoto) {
            Box(
                Modifier.align(Alignment.BottomEnd).size(size * 0.36f).clip(CircleShape).background(TsmColors.SurfaceHigh),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.AddAPhoto, null, tint = TsmColors.TextMain, modifier = Modifier.size(size * 0.22f)) }
        }
    }
    if (ask) {
        AlertDialog(
            onDismissRequest = { ask = false },
            icon = { PlayerAvatar(c, name, color, size) },
            title = { Text(name) },
            text = { Text(s.photoHint) },
            confirmButton = { TextButton(onClick = { ask = false; pick() }) { Text(s.photoChange) } },
            dismissButton = { TextButton(onClick = { ask = false; c.removePhoto(name) }) { Text(s.photoRemove) } },
        )
    }
    if (needName) {
        AlertDialog(
            onDismissRequest = { needName = false },
            text = { Text(s.photoNeedsName) },
            confirmButton = { TextButton(onClick = { needName = false }) { Text(s.ok) } },
        )
    }
}
