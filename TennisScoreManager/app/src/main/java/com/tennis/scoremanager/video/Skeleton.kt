// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.video

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.tennis.scoremanager.data.PoseFrame
import com.tennis.scoremanager.data.ServePose

/** Lo scheletro sopra il video: x e y dei punti sono in frazioni dell'altezza dell'immagine (il riquadro del video). */
fun DrawScope.drawSkeleton(f: PoseFrame, bone: Color, joint: Color = Color.White) {
    val h = size.height
    for ((a, b) in ServePose.BONES) {
        if (f.s[a] < ServePose.MIN_SCORE || f.s[b] < ServePose.MIN_SCORE) continue
        drawLine(bone, Offset(f.x[a] * h, f.y[a] * h), Offset(f.x[b] * h, f.y[b] * h), strokeWidth = h / 110, cap = StrokeCap.Round)
    }
    for (i in 0 until 17) if (f.s[i] >= ServePose.MIN_SCORE) drawCircle(joint, h / 120, Offset(f.x[i] * h, f.y[i] * h))
}
