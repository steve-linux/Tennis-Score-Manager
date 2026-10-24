// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.tennis.scoremanager.MatchController
import com.tennis.scoremanager.data.Reports
import com.tennis.scoremanager.ui.ConfirmDialog
import com.tennis.scoremanager.ui.GhostButton
import com.tennis.scoremanager.ui.LocalStrings
import com.tennis.scoremanager.ui.ScreenScaffold
import com.tennis.scoremanager.ui.SectionCard
import com.tennis.scoremanager.ui.TsmColors
import com.tennis.scoremanager.video.VideoLibrary
import com.tennis.scoremanager.video.VideoPlayerActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Pagina 1 › Video: le partite registrate dal telefono-telecamera, da rivedere punto per punto. */
@Composable
fun VideosScreen(c: MatchController) {
    val s = LocalStrings.current
    val context = LocalContext.current
    val o by c.options.collectAsState()
    BackHandler { c.back() }
    var tick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        tick++
        onPauseOrDispose { }
    }
    val list by produceState(emptyList<VideoLibrary.Entry>(), tick) { value = withContext(Dispatchers.IO) { VideoLibrary.list(context) } }
    var toDelete by remember { mutableStateOf<VideoLibrary.Entry?>(null) }
    ScreenScaffold(
        title = s.videoTitle,
        subtitle = s.videoHint,
        icon = Icons.Filled.VideoLibrary,
        bottomBar = { GhostButton(s.back, Icons.AutoMirrored.Filled.ArrowBack, { c.back() }, Modifier.weight(1f)) },
    ) {
        SectionCard(s.videoTitle, Icons.Filled.VideoLibrary) {
            if (list.isEmpty()) Text(s.videoNone, color = TsmColors.TextDim)
            for (e in list) {
                val ch = e.chapters
                val at = ch.recordedAt.takeIf { it > 0 } ?: e.chaptersFile.lastModified()
                Surface(
                    onClick = {
                        context.startActivity(Intent(context, VideoPlayerActivity::class.java).putExtra(VideoPlayerActivity.EXTRA_CHAPTERS, e.chaptersFile.absolutePath))
                    },
                    shape = RoundedCornerShape(12.dp), color = TsmColors.SurfaceHigh, modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                            Text("${Reports.date(at, o.lang)} ${Reports.time(at, o.lang)}", color = TsmColors.TextMain, fontWeight = FontWeight.SemiBold)
                            Text(
                                listOfNotNull(
                                    ch.names.takeIf { it.size == 2 }?.joinToString(" – "),
                                    s.videoPoints(ch.points.size),
                                    ch.points.lastOrNull()?.score,
                                ).joinToString(" · "),
                                color = TsmColors.TextDim, fontSize = 12.sp,
                            )
                        }
                        IconButton(onClick = { toDelete = e }) { Icon(Icons.Filled.Delete, null, tint = TsmColors.TextDim) }
                    }
                }
            }
        }
    }
    toDelete?.let { e ->
        ConfirmDialog(
            Icons.Filled.Delete, s.videoDeleteTitle, s.videoDeleteText, s.delete,
            onConfirm = {
                VideoLibrary.delete(context, e)
                toDelete = null
                tick++
            },
            onDismiss = { toDelete = null },
            danger = true,
        )
    }
}
