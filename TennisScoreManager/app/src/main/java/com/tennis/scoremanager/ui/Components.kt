package com.tennis.scoremanager.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tennis.scoremanager.MatchController
import com.tennis.scoremanager.Screen
import com.tennis.scoremanager.ui.screens.MatchScreen
import com.tennis.scoremanager.ui.screens.OptionsScreen
import com.tennis.scoremanager.ui.screens.SetupScreen
import com.tennis.scoremanager.ui.screens.StartScreen
import com.tennis.scoremanager.ui.screens.SummaryScreen

@Composable
fun AppRoot(c: MatchController) {
    val screen by c.screen.collectAsState()
    Surface(color = TsmColors.Background, modifier = Modifier.fillMaxSize()) {
        AnimatedContent(targetState = screen, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "screen") { s ->
            when (s) {
                Screen.SETUP -> SetupScreen(c)
                Screen.OPTIONS -> OptionsScreen(c)
                Screen.START -> StartScreen(c)
                Screen.MATCH -> MatchScreen(c)
                Screen.SUMMARY -> SummaryScreen(c)
            }
        }
    }
}

/** Schermata standard: intestazione, contenuto scorrevole, barra pulsanti in basso. */
@Composable
fun ScreenScaffold(
    title: String,
    subtitle: String?,
    icon: ImageVector,
    bottomBar: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(TsmColors.Ball),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = TsmColors.OnBall) }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.headlineMedium, color = TsmColors.TextMain)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TsmColors.TextDim)
            }
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            content()
            Spacer(Modifier.height(8.dp))
        }
        Row(
            Modifier.fillMaxWidth().background(TsmColors.Surface).padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = bottomBar,
        )
    }
}

@Composable
fun SectionCard(title: String, icon: ImageVector, accent: Color = TsmColors.Ball, content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = TsmColors.Surface),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, color = TsmColors.TextMain)
            }
            content()
        }
    }
}

data class SegOption(val label: String, val icon: ImageVector? = null, val color: Color = TsmColors.Ball, val onColor: Color = TsmColors.OnBall)

/** Selettore a segmenti (uno solo attivo). */
@Composable
fun Segmented(options: List<SegOption>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).border(1.dp, TsmColors.Outline, RoundedCornerShape(14.dp)),
    ) {
        options.forEachIndexed { i, o ->
            val sel = i == selected
            Row(
                Modifier.weight(1f)
                    .background(if (sel) o.color else Color.Transparent)
                    .clickable(role = Role.RadioButton) { onSelect(i) }
                    .padding(vertical = 12.dp, horizontal = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (o.icon != null) {
                    Icon(o.icon, null, tint = if (sel) o.onColor else TsmColors.TextDim, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    o.label,
                    color = if (sel) o.onColor else TsmColors.TextMain,
                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

@Composable
fun SwitchRow(icon: ImageVector, title: String, hint: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onChange(!checked) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = TsmColors.TextDim)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = TsmColors.TextMain, style = MaterialTheme.typography.titleMedium)
            if (hint != null) Text(hint, color = TsmColors.TextDim, style = MaterialTheme.typography.bodyMedium)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = TsmColors.Ball, checkedThumbColor = TsmColors.OnBall),
        )
    }
}

@Composable
fun BigButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = TsmColors.Ball,
    onColor: Color = TsmColors.OnBall,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(54.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = onColor),
    ) {
        Icon(icon, null)
        Spacer(Modifier.width(8.dp))
        Text(
            text, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center, lineHeight = 17.sp,
        )
    }
}

@Composable
fun GhostButton(text: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(54.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Icon(icon, null, tint = TsmColors.TextMain)
        Spacer(Modifier.width(8.dp))
        Text(text, color = TsmColors.TextMain, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Riga stato con pallino verde/rosso e un pulsante per sistemare. */
@Composable
fun RequirementRow(icon: ImageVector, label: String, ok: Boolean, action: String, onAction: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(icon, null, tint = if (ok) TsmColors.Ok else TsmColors.Danger)
        Spacer(Modifier.width(10.dp))
        Text(label, color = TsmColors.TextMain, modifier = Modifier.weight(1f))
        if (ok) {
            Text("✓", color = TsmColors.Ok, fontWeight = FontWeight.Black, fontSize = 20.sp)
        } else {
            Button(
                onClick = onAction,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TsmColors.Orange, contentColor = TsmColors.OnOrange),
            ) { Text(action) }
        }
    }
}

@Composable
fun Pill(text: String, color: Color, onColor: Color, icon: ImageVector? = null) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(color).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = onColor, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, color = onColor, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}
