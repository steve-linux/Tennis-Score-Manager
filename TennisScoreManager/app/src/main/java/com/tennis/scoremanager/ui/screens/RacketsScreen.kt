// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.ui.screens

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SportsTennis
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tennis.scoremanager.MatchController
import com.tennis.scoremanager.data.Feel
import com.tennis.scoremanager.data.Racket
import com.tennis.scoremanager.data.RacketCatalog
import com.tennis.scoremanager.data.RacketStats
import com.tennis.scoremanager.data.RacketUse
import com.tennis.scoremanager.data.Reports
import com.tennis.scoremanager.data.StringType
import com.tennis.scoremanager.data.Stringing
import com.tennis.scoremanager.model.Lang
import com.tennis.scoremanager.ui.BigButton
import com.tennis.scoremanager.ui.ConfirmDialog
import com.tennis.scoremanager.ui.GhostButton
import com.tennis.scoremanager.ui.LocalStrings
import com.tennis.scoremanager.ui.Picker
import com.tennis.scoremanager.ui.ScreenScaffold
import com.tennis.scoremanager.ui.SectionCard
import com.tennis.scoremanager.ui.SegOption
import com.tennis.scoremanager.ui.Segmented
import com.tennis.scoremanager.ui.Strings
import com.tennis.scoremanager.ui.SwitchRow
import com.tennis.scoremanager.ui.TsmColors
import java.util.Calendar
import java.util.Locale

/**
 * Pagina 1 › Racchette: le racchette del giocatore con le incordature (ore di gioco e soglia di controllo) e il
 * confronto tra due configurazioni. Le schede di modifica sono pagine a sé, così la tastiera non copre i campi.
 */
@Composable
fun RacketsScreen(c: MatchController) {
    val s = LocalStrings.current
    val rackets by c.rackets.collectAsState()
    val history by c.history.collectAsState()
    val workouts by c.workouts.collectAsState()
    val o by c.options.collectAsState()
    var tab by rememberSaveable { mutableStateOf(0) }
    var editing by remember { mutableStateOf<Racket?>(null) }
    /** Incordatura in modifica, con la sua racchetta. */
    var stringing by remember { mutableStateOf<Pair<String, Stringing>?>(null) }
    val sessions = remember(history, workouts) { c.racketSessions() }

    val e = editing
    val st = stringing
    if (e != null) {
        BackHandler { editing = null }
        RacketEditor(c, e, isNew = rackets.none { it.id == e.id }) { editing = null }
        return
    }
    if (st != null) {
        BackHandler { stringing = null }
        StringingEditor(c, st.first, st.second, o.lang) { stringing = null }
        return
    }
    BackHandler { c.back() }
    ScreenScaffold(
        title = s.racketTitle,
        subtitle = s.racketHint,
        icon = Icons.Filled.SportsTennis,
        bottomBar = { GhostButton(s.back, Icons.AutoMirrored.Filled.ArrowBack, { c.back() }, Modifier.weight(1f)) },
    ) {
        Segmented(listOf(SegOption(s.racketMine), SegOption(s.racketCompare)), selected = tab, onSelect = { tab = it })
        if (tab == 0) {
            if (rackets.isEmpty()) Text(s.racketEmpty, color = TsmColors.TextDim)
            for (r in rackets.sortedBy { it.retired }) {
                RacketCard(r, sessions, o.lang,
                    onEdit = { editing = r },
                    onNewStringing = { stringing = r.id to newStringing(c, r) },
                    onStringing = { stringing = r.id to it },
                )
            }
            BigButton(s.racketAdd, Icons.Filled.Add, {
                editing = Racket(id = c.newRacketId(), pattern = "16x19", createdAt = System.currentTimeMillis())
            }, Modifier.fillMaxWidth())
            Text(s.stringCheckNote, color = TsmColors.TextDim, fontSize = 12.sp)
        } else {
            CompareTab(sessions, o.lang)
        }
    }
}

/** Nuova incordatura: si parte da quella montata (spesso si cambia solo la tensione), con la data di oggi. */
private fun newStringing(c: MatchController, r: Racket): Stringing =
    r.current?.copy(id = c.newStringingId(), date = System.currentTimeMillis(), notes = "")
        ?: Stringing(id = c.newStringingId(), date = System.currentTimeMillis())

// ---------------------------------------------------------------- testi

/** 23 → "23", 23.5 → "23,5", 1.25 → "1,25" (fino a due decimali, con la virgola delle lingue che la usano). */
internal fun num(v: Double, lang: Lang): String =
    String.format(lang.locale, "%.2f", v).trimEnd('0').trimEnd(',', '.')

/** "300 g · 320 mm · 645 cm² · 16x19". */
internal fun specsLine(weightG: Int?, balanceMm: Int?, headCm2: Int?, pattern: String): String =
    listOfNotNull(weightG?.let { "$it g" }, balanceMm?.let { "$it mm" }, headCm2?.let { "$it cm²" }, pattern.takeIf { it.isNotBlank() })
        .joinToString(" · ")

/** "Monofilamento · Babolat RPM Blast 1,25 mm · 23/22 kg". */
internal fun stringLine(st: Stringing, s: Strings, lang: Lang): String {
    val name = listOf(st.brand, st.model).filter { it.isNotBlank() }.joinToString(" ")
    val cross = st.crossModel.takeIf { st.type == StringType.HYBRID && it.isNotBlank() }?.let { " / $it" }.orEmpty()
    val tension = when {
        st.mainsKg != null && st.crossesKg != null && st.mainsKg != st.crossesKg -> "${num(st.mainsKg, lang)}/${num(st.crossesKg, lang)} kg"
        st.mainsKg != null -> "${num(st.mainsKg, lang)} kg"
        st.crossesKg != null -> "${num(st.crossesKg, lang)} kg"
        else -> null
    }
    return listOfNotNull(
        s.stringTypeName(st.type.ordinal),
        (name + cross).takeIf { it.isNotBlank() }?.let { n -> st.gaugeMm?.let { "$n ${num(it, lang)} mm" } ?: n }
            ?: st.gaugeMm?.let { "${num(it, lang)} mm" },
        tension,
    ).joinToString(" · ")
}

/** Ore con una cifra decimale sotto le 10 ("2,5"), intere sopra. */
private fun hoursText(h: Double, lang: Lang): String =
    if (h < 0.05) "0" else if (h < 10) String.format(lang.locale, "%.1f", h) else "${Math.round(h)}"

// ---------------------------------------------------------------- elenco

@Composable
private fun RacketCard(
    r: Racket,
    sessions: List<RacketStats.Session>,
    lang: Lang,
    onEdit: () -> Unit,
    onNewStringing: () -> Unit,
    onStringing: (Stringing) -> Unit,
) {
    val s = LocalStrings.current
    SectionCard(r.label.ifBlank { s.racketNew }, Icons.Filled.SportsTennis, accent = if (r.retired) TsmColors.TextDim else TsmColors.Ball) {
        val specs = specsLine(r.weightG, r.balanceMm, r.headCm2, r.pattern)
        if (specs.isNotEmpty()) Text(specs, color = TsmColors.TextDim, fontSize = 13.sp)
        if (r.owner.isNotBlank()) Text(r.owner, color = TsmColors.TextDim, fontSize = 13.sp)
        if (r.retired) Text(s.racketRetired, color = TsmColors.Orange, fontSize = 13.sp)
        val cur = r.current
        if (cur == null) {
            Text(s.stringNone, color = TsmColors.TextDim)
        } else {
            Text(stringLine(cur, s, lang), color = TsmColors.TextMain, fontWeight = FontWeight.SemiBold)
            Text("${s.stringDate}: ${Reports.date(cur.date, lang)}", color = TsmColors.TextDim, fontSize = 12.sp)
            // Ore dall'ultima incordatura, con la barra fino alla soglia scelta dal giocatore.
            val h = RacketStats.hours(cur.id, sessions)
            val max = r.checkHours.coerceAtLeast(1)
            val over = h >= max
            Text(s.stringHours(hoursText(h, lang), max), color = if (over) TsmColors.Orange else TsmColors.TextMain, fontSize = 13.sp)
            Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(TsmColors.SurfaceHigh)) {
                Box(Modifier.fillMaxWidth((h / max).toFloat().coerceIn(0f, 1f)).height(8.dp).clip(RoundedCornerShape(4.dp))
                    .background(if (over) TsmColors.Orange else TsmColors.Ball))
            }
            if (over) Text(s.stringCheck, color = TsmColors.Orange, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton(s.racketEdit, Icons.Filled.Edit, onEdit, Modifier.weight(1f))
            GhostButton(s.stringNew, Icons.Filled.Add, onNewStringing, Modifier.weight(1f))
        }
        if (r.stringings.isNotEmpty()) {
            Text(s.stringHistory, color = TsmColors.TextDim, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            for (st in r.stringings.sortedByDescending { it.date }) {
                Surface(onClick = { onStringing(st) }, shape = RoundedCornerShape(12.dp), color = TsmColors.SurfaceHigh, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text(stringLine(st, s, lang), color = TsmColors.TextMain, fontSize = 13.sp)
                        Text("${Reports.date(st.date, lang)} · ${hoursText(RacketStats.hours(st.id, sessions), lang)} h",
                            color = TsmColors.TextDim, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- schede

@Composable
private fun TextInput(
    value: String,
    label: String,
    keyboard: KeyboardType = KeyboardType.Text,
    max: Int = 40,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.take(max)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            capitalization = if (keyboard == KeyboardType.Text) KeyboardCapitalization.Words else KeyboardCapitalization.None,
            keyboardType = keyboard,
        ),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = TsmColors.Ball, focusedLabelColor = TsmColors.Ball, cursorColor = TsmColors.Ball),
        modifier = modifier,
    )
}

private fun String.toIntOrNullTrim(): Int? = trim().toIntOrNull()

/** Accetta sia "1,25" sia "1.25". */
private fun String.toDoubleOrNullComma(): Double? = trim().replace(',', '.').toDoubleOrNull()

private fun Double?.field(lang: Lang): String = this?.let { num(it, lang) } ?: ""

@Composable
private fun RacketEditor(c: MatchController, start: Racket, isNew: Boolean, onClose: () -> Unit) {
    val s = LocalStrings.current
    val lang = c.options.collectAsState().value.lang
    var name by remember { mutableStateOf(start.name) }
    var brand by remember { mutableStateOf(start.brand) }
    var model by remember { mutableStateOf(start.model) }
    var weight by remember { mutableStateOf(start.weightG?.toString().orEmpty()) }
    var balance by remember { mutableStateOf(start.balanceMm?.toString().orEmpty()) }
    var head by remember { mutableStateOf(start.headCm2?.toString().orEmpty()) }
    var pattern by remember { mutableStateOf(start.pattern) }
    var owner by remember { mutableStateOf(start.owner) }
    var check by remember { mutableStateOf(start.checkHours.toString()) }
    var retired by remember { mutableStateOf(start.retired) }
    var confirmDelete by remember { mutableStateOf(false) }
    fun build() = start.copy(
        name = name.trim(), brand = brand.trim(), model = model.trim(),
        weightG = weight.toIntOrNullTrim(), balanceMm = balance.toIntOrNullTrim(), headCm2 = head.toIntOrNullTrim(),
        pattern = pattern.trim(), owner = owner.trim(),
        checkHours = check.toIntOrNullTrim()?.coerceIn(1, 999) ?: Racket.DEFAULT_CHECK_HOURS, retired = retired,
    )
    ScreenScaffold(
        title = if (isNew) s.racketNew else s.racketEdit,
        subtitle = start.label.takeIf { !isNew && it.isNotBlank() },
        icon = Icons.Filled.SportsTennis,
        bottomBar = {
            GhostButton(s.cancel, Icons.AutoMirrored.Filled.ArrowBack, onClose, Modifier.weight(1f))
            BigButton(s.save, Icons.Filled.Save, {
                c.saveRacket(build())
                onClose()
            }, Modifier.weight(1f))
        },
    ) {
        SectionCard(s.racketCatalog, Icons.Filled.Inventory2) {
            Picker(s.racketCatalog, listOf(brand, model).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "–" },
                RacketCatalog.models.map { it to it.label }) { m ->
                brand = m.brand
                model = m.model
                weight = m.weightG.toString()
                balance = m.balanceMm.toString()
                head = m.headCm2.toString()
                pattern = m.pattern
            }
            Text(s.racketCatalogHint, color = TsmColors.TextDim, fontSize = 12.sp)
        }
        SectionCard(s.racketField, Icons.Filled.SportsTennis) {
            TextInput(name, s.racketName) { name = it }
            TextInput(brand, s.racketBrand) { brand = it }
            TextInput(model, s.racketModel) { model = it }
            // Uno per riga: "Bilanciamento (mm)" a metà larghezza andrebbe a capo.
            TextInput(weight, s.racketWeight, KeyboardType.Number, 4) { weight = it }
            TextInput(balance, s.racketBalance, KeyboardType.Number, 3) { balance = it }
            TextInput(head, s.racketHead, KeyboardType.Number, 3) { head = it }
            Text(s.racketPattern, color = TsmColors.TextDim, fontSize = 13.sp)
            val patterns = listOf("16x19", "18x20", "16x18", "18x16")
            Segmented(patterns.map { SegOption(it) }, selected = patterns.indexOf(pattern), onSelect = { pattern = patterns[it] })
            TextInput(pattern, s.racketPattern, max = 10) { pattern = it }
            TextInput(owner, s.racketOwner) { owner = it }
        }
        SectionCard(s.stringTitle, Icons.Filled.Timer) {
            TextInput(check, s.racketCheckHours, KeyboardType.Number, 3) { check = it }
            Text(s.stringCheckNote, color = TsmColors.TextDim, fontSize = 12.sp)
            SwitchRow(Icons.Filled.Inventory2, s.racketRetired, s.racketRetiredHint, retired) { retired = it }
        }
        if (!isNew) GhostButton(s.delete, Icons.Filled.Delete, { confirmDelete = true }, Modifier.fillMaxWidth())
    }
    if (confirmDelete) {
        ConfirmDialog(
            Icons.Filled.Delete, s.racketDeleteTitle, s.racketDeleteText, s.delete,
            onConfirm = {
                confirmDelete = false
                c.deleteRacket(start.id)
                onClose()
            },
            onDismiss = { confirmDelete = false },
            danger = true,
        )
    }
}

@Composable
private fun StringingEditor(c: MatchController, racketId: String, start: Stringing, lang: Lang, onClose: () -> Unit) {
    val s = LocalStrings.current
    val context = LocalContext.current
    val racket = c.racketById(racketId)
    val isNew = racket?.stringings?.none { it.id == start.id } ?: true
    var type by remember { mutableStateOf(start.type) }
    var brand by remember { mutableStateOf(start.brand) }
    var model by remember { mutableStateOf(start.model) }
    var cross by remember { mutableStateOf(start.crossModel) }
    var gauge by remember { mutableStateOf(start.gaugeMm.field(lang)) }
    var mains by remember { mutableStateOf(start.mainsKg.field(lang)) }
    var crosses by remember { mutableStateOf(start.crossesKg.field(lang)) }
    var date by remember { mutableStateOf(start.date) }
    var notes by remember { mutableStateOf(start.notes) }
    var confirmDelete by remember { mutableStateOf(false) }
    ScreenScaffold(
        title = if (isNew) s.stringNew else s.stringTitle,
        subtitle = racket?.label,
        icon = Icons.Filled.SportsTennis,
        bottomBar = {
            GhostButton(s.cancel, Icons.AutoMirrored.Filled.ArrowBack, onClose, Modifier.weight(1f))
            BigButton(s.save, Icons.Filled.Save, {
                val m = mains.toDoubleOrNullComma()
                c.saveStringing(racketId, start.copy(
                    type = type, brand = brand.trim(), model = model.trim(), crossModel = if (type == StringType.HYBRID) cross.trim() else "",
                    gaugeMm = gauge.toDoubleOrNullComma(), mainsKg = m,
                    // Una tensione sola vale per verticali e orizzontali.
                    crossesKg = crosses.toDoubleOrNullComma() ?: m,
                    date = date, notes = notes.trim(),
                ))
                onClose()
            }, Modifier.weight(1f))
        },
    ) {
        SectionCard(s.stringTitle, Icons.Filled.SportsTennis) {
            Picker(s.stringCatalog, listOf(brand, model).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "–" },
                RacketCatalog.strings.map { it to it.label }) { m ->
                brand = m.brand
                model = m.model
                type = m.type
                gauge = num(m.gaugeMm, lang)
            }
            Text(s.stringType, color = TsmColors.TextDim, fontSize = 13.sp)
            Segmented(StringType.entries.map { SegOption(s.stringTypeName(it.ordinal)) }, selected = type.ordinal,
                onSelect = { type = StringType.entries[it] }, columns = 2)
            TextInput(brand, s.racketBrand) { brand = it }
            TextInput(model, s.racketModel) { model = it }
            if (type == StringType.HYBRID) TextInput(cross, s.stringCross) { cross = it }
            TextInput(gauge, s.stringGauge, KeyboardType.Decimal, 5) { gauge = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextInput(mains, s.stringMains, KeyboardType.Decimal, 5, Modifier.weight(1f)) { mains = it }
                TextInput(crosses, s.stringCrosses, KeyboardType.Decimal, 5, Modifier.weight(1f)) { crosses = it }
            }
            GhostButton("${s.stringDate}: ${Reports.date(date, lang)}", Icons.Filled.CalendarMonth, {
                val cal = Calendar.getInstance().apply { timeInMillis = date }
                DatePickerDialog(context, { _, y, m, d ->
                    // Mezzogiorno: la data resta la stessa anche cambiando fuso orario.
                    date = Calendar.getInstance().apply { set(y, m, d, 12, 0, 0) }.timeInMillis
                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
            }, Modifier.fillMaxWidth())
            OutlinedTextField(
                value = notes, onValueChange = { notes = it.take(300) }, label = { Text(s.stringNotes) }, minLines = 2,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = TsmColors.Ball, focusedLabelColor = TsmColors.Ball, cursorColor = TsmColors.Ball),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (!isNew) GhostButton(s.delete, Icons.Filled.Delete, { confirmDelete = true }, Modifier.fillMaxWidth())
    }
    if (confirmDelete) {
        ConfirmDialog(
            Icons.Filled.Delete, s.stringDeleteTitle, s.racketDeleteText, s.delete,
            onConfirm = {
                confirmDelete = false
                c.deleteStringing(racketId, start.id)
                onClose()
            },
            onDismiss = { confirmDelete = false },
            danger = true,
        )
    }
}

// ---------------------------------------------------------------- confronto

/** Una riga del confronto: [better] true = più alto è meglio, false = più basso, null = non si dice. */
private class Row2(
    val label: String, val unit: String, val a: Double?, val b: Double?, val better: Boolean?, val decimals: Int = 0, val max: Double? = null,
    /** Testo del valore al posto di numero e unità. */
    val text: ((Double) -> String)? = null,
)

/** Tempo di gioco da ore: "25 min", "3 h 05". */
private fun playTime(h: Double): String {
    val min = Math.round(h * 60)
    return if (min < 60) "$min min" else String.format(Locale.ROOT, "%d h %02d", min / 60, min % 60)
}

@Composable
private fun CompareTab(sessions: List<RacketStats.Session>, lang: Lang) {
    val s = LocalStrings.current
    var byStringing by rememberSaveable { mutableStateOf(false) }
    Segmented(listOf(SegOption(s.compareByRacket), SegOption(s.compareByString)), selected = if (byStringing) 1 else 0,
        onSelect = { byStringing = it == 1 })
    val configs = remember(sessions, byStringing) { RacketStats.configs(sessions, byStringing) }
    if (configs.size < 2) {
        Text(s.compareTooFew, color = TsmColors.TextDim)
        return
    }
    fun label(cs: RacketStats.ConfigStats): String {
        val st = cs.use.stringing
        return if (byStringing && st != null) "${cs.use.label} · ${stringLine(st, s, lang)} · ${Reports.date(st.date, lang)}" else cs.use.label
    }
    var keyA by rememberSaveable(byStringing) { mutableStateOf(configs[0].key) }
    var keyB by rememberSaveable(byStringing) { mutableStateOf(configs[1].key) }
    val a = configs.firstOrNull { it.key == keyA } ?: configs[0]
    val b = configs.firstOrNull { it.key == keyB } ?: configs[1]
    val options = configs.map { it.key to label(it) }
    Picker(s.compareA, label(a), options) { keyA = it }
    Picker(s.compareB, label(b), options) { keyB = it }

    val rows = listOf(
        Row2(s.compareSessions, "", a.sessions.toDouble(), b.sessions.toDouble(), null),
        Row2(s.racketHours, "", a.hours, b.hours, null, text = ::playTime),
        Row2(s.progressWins, "%", a.winPct, b.winPct, true, max = 100.0),
        Row2(s.progressPointsWon, "%", a.pointsWonPct, b.pointsWonPct, true, max = 100.0),
        Row2(s.statFirstServeIn, "%", a.firstServeInPct, b.firstServeInPct, true, max = 100.0),
        Row2(s.statFirstServeWon, "%", a.firstServeWonPct, b.firstServeWonPct, true, max = 100.0),
        Row2(s.statSecondServeWon, "%", a.secondServeWonPct, b.secondServeWonPct, true, max = 100.0),
        Row2(s.comparePerMatch(s.statAces), "", a.acesPerMatch, b.acesPerMatch, true, 1),
        Row2(s.comparePerMatch(s.statDoubleFaults), "", a.doubleFaultsPerMatch, b.doubleFaultsPerMatch, false, 1),
        Row2(s.comparePerMatch(s.statWinners), "", a.winnersPerMatch, b.winnersPerMatch, true, 1),
        Row2(s.comparePerMatch(s.statUnforcedErrors), "", a.unforcedPerMatch, b.unforcedPerMatch, false, 1),
        Row2(s.statAvgWristSpeed, "°/s", a.wristDps, b.wristDps, null),
        Row2(s.comparePerMatch(s.compareStrokes), "", a.strokesPerMatch, b.strokesPerMatch, null),
        Row2(s.heartAvg, "bpm", a.heartAvg, b.heartAvg, null),
    ).filter { it.a != null || it.b != null }
    val feel = listOf(
        Row2(s.feelControl, "", a.control, b.control, true, 1, 5.0),
        Row2(s.feelComfort, "", a.comfort, b.comfort, true, 1, 5.0),
        Row2(s.feelPower, "", a.power, b.power, true, 1, 5.0),
        Row2(s.feelFatigue, "", a.fatigue, b.fatigue, false, 1, 5.0),
    ).filter { it.a != null || it.b != null }

    SectionCard(s.racketCompare, Icons.AutoMirrored.Filled.CompareArrows) {
        Legend(TsmColors.Player1, "A", label(a))
        Legend(TsmColors.Player2, "B", label(b))
        rows.forEach { CompareRow(it, lang) }
    }
    if (feel.isNotEmpty()) {
        SectionCard(s.feelTitle, Icons.Filled.Mood) {
            Text("A: ${s.compareRated(a.rated)} · B: ${s.compareRated(b.rated)}", color = TsmColors.TextDim, fontSize = 12.sp)
            feel.forEach { CompareRow(it, lang) }
        }
    }
    Text(s.compareNote, color = TsmColors.TextDim, fontSize = 12.sp)
}

@Composable
private fun Legend(color: Color, tag: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(18.dp).clip(RoundedCornerShape(5.dp)).background(color), contentAlignment = Alignment.Center) {
            Text(tag, color = TsmColors.Background, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Spacer8()
        Text(text, color = TsmColors.TextMain, fontSize = 13.sp)
    }
}

@Composable
private fun Spacer8() = Box(Modifier.width(8.dp))

/** Etichetta, poi una barra per configurazione col valore; il valore migliore (se si può dire) in grassetto. */
@Composable
private fun CompareRow(r: Row2, lang: Lang) {
    val top = r.max ?: maxOf(r.a ?: 0.0, r.b ?: 0.0).takeIf { it > 0 } ?: 1.0
    val best = if (r.a != null && r.b != null && r.better != null && r.a != r.b) {
        if ((r.a > r.b) == r.better) 0 else 1
    } else -1
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(r.label, color = TsmColors.TextDim, fontSize = 12.sp)
        for ((i, v) in listOf(r.a, r.b).withIndex()) {
            val color = if (i == 0) TsmColors.Player1 else TsmColors.Player2
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(TsmColors.SurfaceHigh)) {
                    if (v != null) Box(Modifier.fillMaxWidth((v / top).toFloat().coerceIn(0.02f, 1f)).height(10.dp).clip(RoundedCornerShape(5.dp)).background(color))
                }
                Text(
                    v?.let { r.text?.invoke(it) } ?: v?.let { (if (r.decimals == 0) "${Math.round(it)}" else String.format(lang.locale, "%.${r.decimals}f", it)) + if (r.unit.isNotEmpty()) " ${r.unit}" else "" } ?: "–",
                    color = TsmColors.TextMain, fontSize = 13.sp, fontWeight = if (best == i) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.End, modifier = Modifier.width(76.dp),
                )
            }
        }
    }
}

// ---------------------------------------------------------------- usati da altre schermate

/** Scelta della racchetta: "Nessuna" e le racchette non messe da parte (quelle di [owner] per prime). */
@Composable
internal fun RacketPicker(c: MatchController, selected: String, owner: String = "", onSelect: (String) -> Unit) {
    val s = LocalStrings.current
    val rackets by c.rackets.collectAsState()
    val k = owner.trim().lowercase(Locale.ROOT)
    val shown = rackets.filter { !it.retired || it.id == selected }
        .sortedByDescending { k.isNotEmpty() && it.owner.trim().lowercase(Locale.ROOT) == k }
    val current = rackets.firstOrNull { it.id == selected }
    Picker(s.racketField, current?.label?.ifBlank { s.racketField } ?: s.racketNone,
        listOf("" to s.racketNone) + shown.map { it.id to it.label.ifBlank { s.racketField } }) { onSelect(it) }
}

/**
 * Racchetta di una sessione già giocata (riepilogo della partita o dell'allenamento): quale era, con che corde,
 * e le sensazioni da 1 a 5. Senza racchette registrate non compare.
 */
@Composable
internal fun RacketSessionCard(
    c: MatchController,
    title: String,
    use: RacketUse?,
    lang: Lang,
    accent: Color = TsmColors.Ball,
    onRacket: (String) -> Unit,
    onFeel: (Feel) -> Unit,
) {
    val s = LocalStrings.current
    val rackets by c.rackets.collectAsState()
    if (rackets.isEmpty() && use == null) return
    SectionCard(title, Icons.Filled.SportsTennis, accent = accent) {
        // Una racchetta tolta dall'elenco resta nella partita: la si mostra col suo nome.
        if (use != null && rackets.none { it.id == use.racketId }) {
            Text(use.label, color = TsmColors.TextMain, fontWeight = FontWeight.SemiBold)
        } else {
            RacketPicker(c, use?.racketId.orEmpty()) { onRacket(it) }
        }
        if (use == null) return@SectionCard
        val specs = specsLine(use.weightG, use.balanceMm, use.headCm2, use.pattern)
        if (specs.isNotEmpty()) Text(specs, color = TsmColors.TextDim, fontSize = 12.sp)
        use.stringing?.let { Text(stringLine(it, s, lang), color = TsmColors.TextDim, fontSize = 12.sp) }
        Text(s.feelHint, color = TsmColors.TextDim, fontSize = 12.sp)
        val f = use.feel ?: Feel()
        FeelRow(s.feelControl, f.control, accent) { onFeel(f.copy(control = it)) }
        FeelRow(s.feelComfort, f.comfort, accent) { onFeel(f.copy(comfort = it)) }
        FeelRow(s.feelPower, f.power, accent) { onFeel(f.copy(power = it)) }
        FeelRow(s.feelFatigue, f.fatigue, accent) { onFeel(f.copy(fatigue = it)) }
    }
}

/** Da 1 a 5; ritoccando il valore scelto lo si toglie. */
@Composable
private fun FeelRow(label: String, value: Int, accent: Color, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = TsmColors.TextMain, fontSize = 14.sp, modifier = Modifier.weight(1f))
        for (v in 1..5) {
            val on = v <= value
            Surface(
                onClick = { onChange(if (v == value) 0 else v) },
                shape = RoundedCornerShape(10.dp),
                color = if (on) accent else TsmColors.SurfaceHigh,
                modifier = Modifier.padding(start = 4.dp).size(34.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("$v", color = if (on) TsmColors.Background else TsmColors.TextDim, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}
