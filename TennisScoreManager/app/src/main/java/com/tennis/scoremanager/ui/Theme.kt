// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.tennis.scoremanager.model.Side

/** Colori fissi dell'app: giallo = Giocatore 1, rosso = Giocatore 2. */
object TsmColors {
    val Background = Color(0xFF0B1220)
    val Surface = Color(0xFF151D2E)
    val SurfaceHigh = Color(0xFF1E2940)
    val Outline = Color(0xFF33415C)
    val Ball = Color(0xFFC6F432)
    val OnBall = Color(0xFF15210A)
    val Player1 = Color(0xFFFFD600)
    val OnPlayer1 = Color(0xFF231C00)
    val Player2 = Color(0xFFE53935)
    val OnPlayer2 = Color(0xFFFFFFFF)
    val Orange = Color(0xFFFF9800)
    val OnOrange = Color(0xFF231300)
    val Danger = Color(0xFFFF3B30)
    val TextMain = Color(0xFFF1F5FB)
    val TextDim = Color(0xFF9AA8BD)
    val Court = Color(0xFF1F7A4D)
    val CourtLine = Color(0xFFE8F5E9)
    val Ok = Color(0xFF4CAF50)

    fun player(side: Side) = if (side == Side.P1) Player1 else Player2
    fun onPlayer(side: Side) = if (side == Side.P1) OnPlayer1 else OnPlayer2
}

private val scheme = darkColorScheme(
    primary = TsmColors.Ball,
    onPrimary = TsmColors.OnBall,
    secondary = TsmColors.Orange,
    onSecondary = TsmColors.OnOrange,
    background = TsmColors.Background,
    onBackground = TsmColors.TextMain,
    surface = TsmColors.Surface,
    onSurface = TsmColors.TextMain,
    surfaceVariant = TsmColors.SurfaceHigh,
    onSurfaceVariant = TsmColors.TextDim,
    surfaceContainer = TsmColors.Surface,
    surfaceContainerHigh = TsmColors.SurfaceHigh,
    surfaceContainerHighest = TsmColors.SurfaceHigh,
    outline = TsmColors.Outline,
    error = TsmColors.Danger,
)

private val typography = Typography(
    headlineMedium = TextStyle(fontWeight = FontWeight.Black, fontSize = 26.sp, letterSpacing = 0.5.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
)

@Composable
fun TsmTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
