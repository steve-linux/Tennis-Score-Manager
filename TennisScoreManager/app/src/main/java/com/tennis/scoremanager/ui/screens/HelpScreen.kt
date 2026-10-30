// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tennis.scoremanager.MatchController
import com.tennis.scoremanager.data.HelpBlock
import com.tennis.scoremanager.data.HelpDoc
import com.tennis.scoremanager.data.HelpSection
import com.tennis.scoremanager.data.HelpSpan
import com.tennis.scoremanager.ui.GhostButton
import com.tennis.scoremanager.ui.LocalStrings
import com.tennis.scoremanager.ui.ScreenScaffold
import com.tennis.scoremanager.ui.SectionCard
import com.tennis.scoremanager.ui.TsmColors

/**
 * Altro › Aiuto: la guida completa nella lingua dell'app (la stessa del repository, senza le parti per chi sviluppa),
 * con l'indice, la ricerca e i rimandi tra sezioni toccabili.
 */
@Composable
fun HelpScreen(c: MatchController) {
    val s = LocalStrings.current
    val context = LocalContext.current
    val lang = c.options.value.lang
    val sections = remember(lang) {
        val text = runCatching { context.assets.open("help/${lang.code}.md").bufferedReader().readText() }
            .recoverCatching { context.assets.open("help/en.md").bufferedReader().readText() }.getOrDefault("")
        HelpDoc.parse(text)
    }
    var query by rememberSaveable { mutableStateOf("") }
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    val open = sections.firstOrNull { it.id == openId }
    // Indietro: dalla sezione all'indice, dall'indice ad Altro.
    BackHandler { if (open != null) openId = null else c.back() }
    // Una sezione nuova parte dall'alto (la pagina ricorda lo scorrimento).
    androidx.compose.runtime.key(openId) { ScreenScaffold(
        title = open?.let { "${it.id} ${it.title}" } ?: s.moreHelp,
        subtitle = if (open == null) s.moreHelpHint else null,
        icon = Icons.AutoMirrored.Filled.Help,
        bottomBar = {
            GhostButton(s.back, Icons.AutoMirrored.Filled.ArrowBack, { if (open != null) openId = null else c.back() }, Modifier.weight(1f))
            if (open != null) {
                val next = sections.getOrNull(sections.indexOf(open) + 1)
                if (next != null) GhostButton(s.helpNext, Icons.AutoMirrored.Filled.ArrowForward, { openId = next.id }, Modifier.weight(1f))
            }
        },
    ) {
        if (open != null) {
            Section(open) { ref -> openId = ref }
            return@ScreenScaffold
        }
        OutlinedTextField(
            query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text(s.helpSearch) }, leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, null) } },
        )
        if (query.isNotBlank()) {
            val hits = HelpDoc.search(sections, query)
            if (hits.isEmpty()) Text(s.helpNoResults, color = TsmColors.TextDim)
            for (h in hits) {
                Column(Modifier.fillMaxWidth().background(TsmColors.Surface, RoundedCornerShape(14.dp)).clickable { openId = h.id }.padding(14.dp)) {
                    Text("${h.id} ${h.title}", color = TsmColors.TextMain, fontWeight = FontWeight.Bold)
                    Text(HelpDoc.snippet(h, query), color = TsmColors.TextDim, fontSize = 13.sp)
                }
            }
            return@ScreenScaffold
        }
        // Indice: un riquadro per capitolo, coi paragrafi dentro.
        var chapter: HelpSection? = null
        val groups = LinkedHashMap<HelpSection, MutableList<HelpSection>>()
        for (sec in sections) {
            if (sec.level == 2 || chapter == null) { chapter = sec; groups[sec] = mutableListOf() } else groups[chapter]!!.add(sec)
        }
        for ((ch, subs) in groups) {
            SectionCard("${ch.id} ${ch.title}", Icons.AutoMirrored.Filled.Help) {
                IndexRow(s.helpRead) { openId = ch.id }
                for (sub in subs) IndexRow("${sub.id} ${sub.title}") { openId = sub.id }
            }
        }
    } }
}

@Composable
private fun IndexRow(text: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp)) {
        Text(text, color = TsmColors.TextMain, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = TsmColors.TextDim)
    }
}

/** Il testo di una sezione: paragrafi, elenchi rientrati, tabelle come righe; i rimandi aprono la loro sezione. */
@Composable
private fun Section(sec: HelpSection, onRef: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (b in sec.blocks) when (b) {
            is HelpBlock.Para -> Text(styled(b.spans, onRef), color = TsmColors.TextMain, fontSize = 15.sp, modifier = Modifier.padding(start = (b.indent * 18).dp))
            is HelpBlock.Bullet -> Row(Modifier.padding(start = (b.indent * 18).dp)) {
                Text(b.number?.let { "$it." } ?: "•", color = TsmColors.Ball, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text(styled(b.spans, onRef), color = TsmColors.TextMain, fontSize = 15.sp, modifier = Modifier.weight(1f))
            }
            is HelpBlock.Table -> Column(
                Modifier.fillMaxWidth().background(TsmColors.Surface, RoundedCornerShape(12.dp)).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Su un telefono le colonne non ci stanno: ogni riga è la prima cella e sotto le altre.
                for ((i, row) in b.rows.withIndex()) {
                    val head = i == 0
                    Column {
                        Text(styled(row.first(), onRef), color = if (head) TsmColors.TextDim else TsmColors.Ball, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        for (cell in row.drop(1)) Text(styled(cell, onRef), color = if (head) TsmColors.TextDim else TsmColors.TextMain, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

private fun styled(spans: List<HelpSpan>, onRef: (String) -> Unit): AnnotatedString = buildAnnotatedString {
    for (sp in spans) {
        val style = SpanStyle(
            fontWeight = if (sp.bold) FontWeight.Bold else null,
            fontStyle = if (sp.italic) FontStyle.Italic else null,
            fontFamily = if (sp.code) FontFamily.Monospace else null,
            background = if (sp.code) TsmColors.Surface else androidx.compose.ui.graphics.Color.Unspecified,
        )
        val ref = sp.ref
        if (ref != null) {
            withLink(LinkAnnotation.Clickable(ref, TextLinkStyles(SpanStyle(color = TsmColors.Ball, textDecoration = TextDecoration.Underline))) { onRef(ref) }) {
                withStyle(style) { append(sp.text) }
            }
        } else {
            withStyle(style) { append(sp.text) }
        }
    }
}
