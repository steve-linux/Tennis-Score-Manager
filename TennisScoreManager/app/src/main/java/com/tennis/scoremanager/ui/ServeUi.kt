// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.ui

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SportsTennis
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tennis.scoremanager.MatchController
import com.tennis.scoremanager.data.Reports
import com.tennis.scoremanager.data.ServeAttempt
import com.tennis.scoremanager.data.ServeKind
import com.tennis.scoremanager.data.ServeSession
import com.tennis.scoremanager.data.ServeSide
import com.tennis.scoremanager.data.ServeTarget
import com.tennis.scoremanager.data.ServeTraining
import com.tennis.scoremanager.data.ShotDirection
import com.tennis.scoremanager.data.TrainedStroke
import com.tennis.scoremanager.data.Wing
import com.tennis.scoremanager.video.CameraActivity
import com.tennis.scoremanager.video.ReplayActivity

fun serveSideName(s: Strings, v: ServeSide) = if (v == ServeSide.DEUCE) s.serveSideDeuce else s.serveSideAd
fun serveKindName(s: Strings, v: ServeKind) = if (v == ServeKind.FIRST) s.serveFirst else s.serveSecond
fun serveTargetName(s: Strings, v: ServeTarget?) = when (v) {
    ServeTarget.T -> s.serveTargetT
    ServeTarget.BODY -> s.serveTargetBody
    ServeTarget.WIDE -> s.serveTargetWide
    null -> s.serveTargetNone
}

fun strokeLabel(s: Strings, v: TrainedStroke) = s.strokeName(v.band)
fun directionName(s: Strings, v: ShotDirection?) = when (v) {
    ShotDirection.CROSS -> s.dirCross
    ShotDirection.MIDDLE -> s.dirMiddle
    ShotDirection.LINE -> s.dirLine
    null -> s.dirNone
}
fun wingName(s: Strings, v: Wing) = if (v == Wing.FOREHAND) s.wingFh else s.wingBh

/** Un passo di un esercizio a parole: "20 · Prima · Destra · Al T", "20 · Incrociato". */
fun stepLabel(s: Strings, step: com.tennis.scoremanager.data.DrillStep): String = listOfNotNull(
    step.count.toString(), step.wing?.let { wingName(s, it) }, step.direction?.let { directionName(s, it) },
    step.kind?.let { serveKindName(s, it) }, step.side?.let { serveSideName(s, it) }, step.target?.let { serveTargetName(s, it) },
).joinToString(" · ")

/** I passi di un esercizio in una riga: "20 · Incrociato, poi 20 · Lungolinea". */
fun drillSummary(s: Strings, d: com.tennis.scoremanager.data.Drill): String = d.steps.joinToString(s.drillThen) { stepLabel(s, it) }

/** L'esercizio da seguire col colpo scelto (o libero), e dove si cambiano gli esercizi. */
@Composable
fun DrillRow(c: MatchController, stroke: TrainedStroke) {
    val s = LocalStrings.current
    val drills by c.drills.collectAsState()
    val chosen by c.trainDrill.collectAsState()
    val mine = drills.filter { it.stroke == stroke }
    val cur = mine.firstOrNull { it.id == chosen }
    var pick by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp)).background(TsmColors.SurfaceHigh)
            .clickable { pick = true }.padding(10.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.FormatListNumbered, null, tint = if (cur != null) TsmColors.Ball else TsmColors.TextDim, modifier = Modifier.size(36.dp))
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(s.drillChoose, color = TsmColors.TextDim, fontSize = 12.sp)
            Text(cur?.name ?: s.drillFree, color = TsmColors.TextMain, fontWeight = FontWeight.Bold)
            cur?.let { Text(drillSummary(s, it), color = TsmColors.TextDim, fontSize = 12.sp) }
        }
        Text(s.trainChange, color = TsmColors.Ball, fontWeight = FontWeight.Bold)
    }
    if (pick) AlertDialog(
        onDismissRequest = { pick = false },
        title = { Text(s.drillChoose) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                @Composable fun option(label: String, hint: String?, id: String?) = Column(
                    Modifier.fillMaxWidth().clickable { c.trainDrill.value = id; pick = false }.padding(vertical = 8.dp),
                ) {
                    Text(label, color = if (id == cur?.id) TsmColors.Ball else TsmColors.TextMain, fontWeight = FontWeight.Bold)
                    hint?.let { Text(it, color = TsmColors.TextDim, fontSize = 12.sp) }
                }
                option(s.drillFree, null, null)
                for (d in mine) option(d.name, drillSummary(s, d), d.id)
            }
        },
        confirmButton = { TextButton(onClick = { pick = false; c.go(com.tennis.scoremanager.Screen.DRILLS) }) { Text(s.drillsTitle) } },
        dismissButton = { TextButton(onClick = { pick = false }) { Text(s.back) } },
    )
}

/**
 * Pagina Allenamento: scelta del colpo (servizio, dritto, rovescio, volée), avvio e le ultime sessioni di quel colpo;
 * le altre in "Tutte le sessioni".
 */
@Composable
fun ServeTrainingCard(c: MatchController) {
    val s = LocalStrings.current
    val context = LocalContext.current
    val sessions by c.serveSessions.collectAsState()
    val stroke by c.trainStroke.collectAsState()
    var help by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf<ServeSession?>(null) }
    LaunchedEffect(Unit) { c.refreshServeSessions(); c.refreshStudents() }
    val trainee by c.trainStudent.collectAsState()
    // Solo le sessioni del colpo scelto e di chi si allena (senza allievo: quelle senza allievo).
    val mine = sessions.filter { it.stroke == stroke && it.student == trainee }
    SectionCard(s.strokeTrainingTitle, Icons.Filled.SportsTennis) {
        Text(s.strokeTrainingHint, color = TsmColors.TextDim, fontSize = 13.sp)
        TraineeRow(c)
        val strokes = TrainedStroke.entries
        // Ogni colpo col numero delle sue sessioni: l'elenco qui sotto mostra solo quelle del colpo scelto.
        Segmented(
            strokes.map { t -> SegOption(strokeLabel(s, t) + sessions.count { it.stroke == t && it.student == trainee }.takeIf { it > 0 }?.let { " · $it" }.orEmpty()) },
            strokes.indexOf(stroke), { c.trainStroke.value = strokes[it] }, columns = 2,
        )
        DrillRow(c, stroke)
        BigButton(s.serveStart, Icons.Filled.Videocam, {
            context.startActivity(
                Intent(context, CameraActivity::class.java).putExtra(CameraActivity.EXTRA_SERVE, true).putExtra(CameraActivity.EXTRA_STROKE, stroke.name),
            )
        }, Modifier.fillMaxWidth())
        GhostButton(s.servePlaceShow, Icons.Filled.Videocam, { help = true }, Modifier.fillMaxWidth())
        if (mine.isNotEmpty()) {
            Text(s.strokeSessionsTitle(strokeLabel(s, stroke)), color = TsmColors.TextMain, fontWeight = FontWeight.Bold)
            StrokeTrend(mine) { c.home(com.tennis.scoremanager.HomeTab.PROGRESS) }
            for (sess in mine.take(RECENT_SESSIONS)) SessionRow(c, sess) { open = sess }
            // Sempre, anche con poche sessioni: lì ci sono cartella, spazio occupato e la selezione per cancellare.
            GhostButton(s.strokeAllSessions(mine.size), Icons.Filled.FolderOpen, { c.openStrokeSessions(stroke) }, Modifier.fillMaxWidth())
        } else if (sessions.isNotEmpty()) {
            Text(s.strokeNone, color = TsmColors.TextDim, fontSize = 13.sp)
        }
    }
    if (help) ServePlacementDialog(stroke) { help = false }
    open?.let { sess -> ServeSessionDialog(c, sess) { open = null } }
}

/** Chi si allena: un allievo (le sessioni, le cartelle e l'andamento sono suoi) o nessuno. */
@Composable
fun TraineeRow(c: MatchController) {
    val s = LocalStrings.current
    val trainee by c.trainStudent.collectAsState()
    val students by c.students.collectAsState()
    val who = students.firstOrNull { it.id == trainee }
    var pick by remember { mutableStateOf(false) }
    var create by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp)).background(TsmColors.SurfaceHigh)
            .clickable { pick = true }.padding(10.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        if (who != null) PlayerAvatar(c, who.name, TsmColors.Ball, 36.dp)
        else Icon(Icons.Filled.Person, null, tint = TsmColors.TextDim, modifier = Modifier.size(36.dp))
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(s.trainWho, color = TsmColors.TextDim, fontSize = 12.sp)
            Text(who?.name ?: s.trainNoStudent, color = TsmColors.TextMain, fontWeight = FontWeight.Bold)
        }
        Text(s.trainChange, color = TsmColors.Ball, fontWeight = FontWeight.Bold)
    }
    if (pick) {
        AlertDialog(
            onDismissRequest = { pick = false },
            title = { Text(s.trainWho) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    @Composable fun option(label: String, id: String?, name: String?) = Row(
                        Modifier.fillMaxWidth().clickable { c.setTrainStudent(id); pick = false }.padding(vertical = 8.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        if (name != null) PlayerAvatar(c, name, if (id == trainee) TsmColors.Ball else TsmColors.TextDim, 32.dp)
                        else Icon(Icons.Filled.Person, null, tint = TsmColors.TextDim, modifier = Modifier.size(32.dp))
                        Text(label, color = if (id == trainee) TsmColors.Ball else TsmColors.TextMain, fontWeight = if (id == trainee) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(start = 10.dp))
                    }
                    option(s.trainNoStudent, null, null)
                    for (st in students) option(st.name, st.id, st.name)
                }
            },
            confirmButton = { TextButton(onClick = { pick = false; create = true }) { Text(s.studentNew) } },
            dismissButton = { TextButton(onClick = { pick = false; c.openStudents() }) { Text(s.studentManage) } },
        )
    }
    if (create) StudentEditDialog(c, null) { saved -> create = false; saved?.let { c.setTrainStudent(it.id) } }
}

/** Nuovo allievo o modifica di un giocatore (vedi [PlayerEditDialog]): dagli allievi nasce un allievo. */
@Composable
fun StudentEditDialog(c: MatchController, student: com.tennis.scoremanager.data.PlayerProfile?, onDone: (com.tennis.scoremanager.data.PlayerProfile?) -> Unit) =
    PlayerEditDialog(c, student, asStudent = true, onDone = onDone)

/**
 * Giocatore nuovo o modifica: nome e cognome obbligatori (identificano la persona), soprannome facoltativo (se c'è, è
 * il nome che si vede in partita), mano e racchetta. Creandone uno nuovo si propongono i giocatori che ci sono già con
 * quel nome, per non fare doppioni; [prefill] è il nome scritto in Nuova partita.
 */
@Composable
fun PlayerEditDialog(
    c: MatchController, player: com.tennis.scoremanager.data.PlayerProfile?, asStudent: Boolean = false, prefill: String = "",
    onDone: (com.tennis.scoremanager.data.PlayerProfile?) -> Unit,
) {
    val s = LocalStrings.current
    // Dal nome scritto: due parole o più sono nome e cognome, una sola è il soprannome.
    val words = prefill.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    var first by remember { mutableStateOf(player?.firstName ?: if (words.size >= 2) words.first() else "") }
    var last by remember { mutableStateOf(player?.lastName ?: if (words.size >= 2) words.drop(1).joinToString(" ") else "") }
    var nick by remember { mutableStateOf(player?.nickname ?: if (words.size == 1) words[0] else "") }
    var hand by remember { mutableStateOf(player?.hand) }
    var racket by remember { mutableStateOf(player?.racket) }
    var newRacket by remember { mutableStateOf<com.tennis.scoremanager.data.Racket?>(null) }
    val rackets by c.rackets.collectAsState()
    // Un giocatore nuovo può essere uno che c'è già (ha giocato partite): lo si sceglie invece di rifarlo.
    var existing by remember { mutableStateOf<com.tennis.scoremanager.data.PlayerProfile?>(null) }
    val players by c.players.collectAsState()
    @Composable fun field(v: String, label: String, onChange: (String) -> Unit) = androidx.compose.material3.OutlinedTextField(
        v, { onChange(it.take(40)) }, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Words),
    )
    AlertDialog(
        onDismissRequest = { onDone(null) },
        title = { Text(if (player != null) s.studentEdit else if (asStudent) s.studentNew else s.playerNewTitle) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                field(first, s.playerFirstName) { first = it }
                field(last, s.playerLastName) { last = it }
                field(nick, s.playerNickname) { nick = it }
                Text(s.playerNameRule, color = TsmColors.TextDim, fontSize = 12.sp)
                if (player == null) {
                    val q = listOf("$first $last".trim(), nick.trim(), last.trim()).firstOrNull { it.length >= 2 }.orEmpty()
                    val hits = com.tennis.scoremanager.data.Players.search(players.filter { !asStudent || !it.student }, q).take(4)
                    for (p in hits) {
                        val on = existing?.id == p.id
                        Row(
                            Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                                .background(if (on) TsmColors.Ball else TsmColors.SurfaceHigh)
                                .clickable {
                                    existing = if (on) null else p
                                    if (!on) {
                                        if (p.firstName.isNotBlank()) first = p.firstName
                                        if (p.lastName.isNotBlank()) last = p.lastName
                                        nick = p.nickname.ifBlank { if (p.complete) "" else p.name }
                                        hand = p.hand ?: hand; racket = p.racket ?: racket
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            PlayerAvatar(c, p.name, if (on) TsmColors.OnBall else TsmColors.Ball, 28.dp)
                            Text(
                                " " + p.name + (p.fullName.takeIf { it.isNotBlank() && it != p.name }?.let { " ($it)" } ?: "") + (c.playerHint(p)?.let { " · $it" } ?: ""),
                                color = if (on) TsmColors.OnBall else TsmColors.TextMain, fontSize = 13.sp, modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    if (hits.isNotEmpty()) Text(if (existing != null) s.studentFromExisting else s.studentPickExisting, color = TsmColors.TextDim, fontSize = 12.sp)
                }
                // Un altro con lo stesso nome e cognome: si può (omonimo), ma lo si dice.
                val twins = com.tennis.scoremanager.data.Players.sameFullName(players, first, last, except = player?.id ?: existing?.id)
                if (first.isNotBlank() && last.isNotBlank() && twins.isNotEmpty()) Text(s.playerTwinWarning("${first.trim()} ${last.trim()}"), color = TsmColors.Orange, fontSize = 12.sp)
                Text(s.studentHand, color = TsmColors.TextDim, fontSize = 13.sp)
                val hands = listOf(null, com.tennis.scoremanager.data.Hand.RIGHT, com.tennis.scoremanager.data.Hand.LEFT)
                Segmented(listOf(SegOption("—"), SegOption(s.handRight), SegOption(s.handLeft)), hands.indexOf(hand), { hand = hands[it] })
                // La sua racchetta, tra quelle di Le mie racchette (le messe da parte no, tranne quella già sua).
                val choices = rackets.filter { !it.retired || it.id == racket }
                Text(s.studentRacket, color = TsmColors.TextDim, fontSize = 13.sp)
                if (choices.isNotEmpty()) Segmented(
                    listOf(SegOption("—")) + choices.map { SegOption(it.label.ifBlank { "?" }) },
                    (choices.indexOfFirst { it.id == racket } + 1).coerceAtLeast(0), { i -> racket = if (i == 0) null else choices[i - 1].id }, columns = 1,
                )
                // Una racchetta che non c'è ancora si crea da qui, senza passare da Le mie racchette.
                GhostButton(s.racketNew, androidx.compose.material.icons.Icons.Filled.SportsTennis, {
                    newRacket = com.tennis.scoremanager.data.Racket(c.newRacketId(), owner = com.tennis.scoremanager.data.PlayerProfile.display(first, last, nick))
                }, Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = first.isNotBlank() && last.isNotBlank(), onClick = {
                val base = player ?: existing
                    ?: com.tennis.scoremanager.data.PlayerProfile(com.tennis.scoremanager.data.Players.newId(), "", createdAt = System.currentTimeMillis())
                val p = base.copy(
                    firstName = first.trim(), lastName = last.trim(), nickname = nick.trim(),
                    name = com.tennis.scoremanager.data.PlayerProfile.display(first, last, nick),
                    hand = hand, racket = racket, student = base.student || asStudent && player == null,
                )
                c.savePlayer(p)
                // Una racchetta di nessuno diventa sua: così la si propone anche nelle sue partite.
                racket?.let { c.racketById(it) }?.takeIf { it.owner.isBlank() }?.let { c.saveRacket(it.copy(owner = p.name)) }
                onDone(p)
            }) { Text(s.ok) }
        },
        dismissButton = { TextButton(onClick = { onDone(null) }) { Text(s.back) } },
    )
    newRacket?.let { r ->
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { newRacket = null },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        ) {
            androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = TsmColors.Background) {
                com.tennis.scoremanager.ui.screens.RacketEditor(c, r, isNew = true) {
                    newRacket = null
                    // Salvata: è la sua.
                    if (c.racketById(r.id) != null) racket = r.id
                }
            }
        }
    }
}

/** Sotto "Inizia" le ultime sessioni del colpo scelto; le altre nella pagina di tutte le sessioni. */
const val RECENT_SESSIONS = 3

/** Una sessione in elenco: data e ora, colpi dentro e percentuale. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SessionRow(
    c: MatchController, sess: ServeSession, extra: String? = null, modifier: Modifier = Modifier, onLongClick: (() -> Unit)? = null, onClick: () -> Unit,
) {
    val s = LocalStrings.current
    val lang = c.options.value.lang
    val n = ServeTraining.count(sess.serves)
    Row(modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(vertical = 6.dp)) {
        Column(Modifier.weight(1f)) {
            Text("${Reports.date(sess.startedAt, lang)} ${Reports.time(sess.startedAt, lang)}", color = TsmColors.TextMain, fontSize = 14.sp)
            if (extra != null) Text(extra, color = TsmColors.TextDim, fontSize = 12.sp)
        }
        Text(s.serveCountLine(n.inPlay, n.total) + (n.pct?.let { " · $it%" } ?: ""), color = TsmColors.Ball, fontSize = 14.sp)
    }
}

/**
 * Percentuale di colpi dentro, sessione dopo sessione (dalla più vecchia), come una linea; con meno di due sessioni
 * non si disegna. [onClick] apre Progressi.
 */
@Composable
fun StrokeTrend(sessions: List<ServeSession>, title: String? = null, onClick: (() -> Unit)? = null) {
    val s = LocalStrings.current
    val pcts = sessions.sortedBy { it.startedAt }.mapNotNull { ServeTraining.count(it.serves).pct }.takeLast(20)
    if (pcts.size < 2) return
    Column(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)) {
        Row(Modifier.fillMaxWidth()) {
            Text(title ?: s.strokeTrend, color = if (title != null) TsmColors.TextMain else TsmColors.TextDim, fontSize = if (title != null) 14.sp else 12.sp,
                fontWeight = if (title != null) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f).padding(end = 8.dp))
            Text("${pcts.first()}% → ${pcts.last()}%", color = TsmColors.TextMain, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        val line = TsmColors.Ball
        val grid = TsmColors.TextDim.copy(alpha = 0.3f)
        // Scala sui valori (almeno 20 punti), così si vede anche un passo dall'80 all'83 %.
        val lo = (minOf(pcts.min(), pcts.max() - 20).coerceAtLeast(0)).toFloat()
        val hi = (maxOf(pcts.max(), pcts.min() + 20).coerceAtMost(100)).toFloat().coerceAtLeast(lo + 1)
        Canvas(Modifier.fillMaxWidth().height(56.dp).padding(vertical = 6.dp)) {
            drawLine(grid, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1f)
            drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1f)
            val dx = size.width / (pcts.size - 1)
            val pts = pcts.mapIndexed { i, p -> Offset(i * dx, size.height * (1 - (p - lo) / (hi - lo))) }
            for (i in 1 until pts.size) drawLine(line, pts[i - 1], pts[i], strokeWidth = 4f)
            for (p in pts) drawCircle(line, 5f, p)
        }
    }
}

/**
 * Dove mettere il telefono: uno schema dall'alto (metà campo, chi serve, il telefono a fianco e l'alternativa
 * dietro, tratteggiata) e le indicazioni.
 */
@Composable
fun ServePlacementDialog(stroke: TrainedStroke = TrainedStroke.SERVE, onClose: () -> Unit) {
    val s = LocalStrings.current
    val serve = stroke == TrainedStroke.SERVE
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(s.servePlaceTitle + " · " + strokeLabel(s, stroke)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PlacementDiagram(stroke)
                Text(
                    "● " + (if (serve) s.servePlaceServer else s.placeHitter) + "   ▮ " + s.servePlacePhone + (if (serve) "   ▯ " + s.servePlaceAlt else ""),
                    fontSize = 12.sp, color = TsmColors.TextDim,
                )
                Text(
                    when (stroke) {
                        TrainedStroke.SERVE -> s.servePlaceText
                        TrainedStroke.VOLLEY -> s.placeVolleyText
                        else -> s.placeGroundText
                    },
                    fontSize = 13.sp, color = TsmColors.TextMain,
                )
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(s.ok) } },
    )
}

/**
 * Metà campo vista dall'alto, rete in alto. Servizio: chi serve (giallo) a destra del segno centrale, il telefono
 * (verde) a fianco e l'alternativa dietro. Dritto e rovescio: chi colpisce dietro la linea di fondo, il telefono di
 * lato fuori dal corridoio. Volée: chi gioca a rete, il telefono di lato alla sua altezza.
 */
@Composable
private fun PlacementDiagram(stroke: TrainedStroke) {
    // 16 m di larghezza (campo da doppio con spazio ai lati) e 17,5 m di profondità: la metà campo e i 5 m dietro la linea.
    // Al massimo 260 dp d'altezza: con il telefono in orizzontale (telecamera) resta spazio anche per le indicazioni.
    Canvas(Modifier.fillMaxWidth().heightIn(max = 260.dp).aspectRatio(16f / 17.5f, matchHeightConstraintsFirst = true)) {
        val m = size.width / 16f
        val left = 2.5f * m
        val right = left + 10.97f * m
        val net = 0.6f * m
        val base = net + 11.885f * m
        val single = 1.37f * m
        val line = Color.White.copy(alpha = 0.8f)
        val st = Stroke(width = 2f)
        drawRect(TsmColors.Court, Offset(left, net), Size(right - left, base - net))
        drawRect(line, Offset(left, net), Size(right - left, base - net), style = st)
        drawLine(line, Offset(left + single, net), Offset(left + single, base), 2f)
        drawLine(line, Offset(right - single, net), Offset(right - single, base), 2f)
        val service = net + 6.40f * m
        drawLine(line, Offset(left + single, service), Offset(right - single, service), 2f)
        val mid = (left + right) / 2
        drawLine(line, Offset(mid, net), Offset(mid, service), 2f)
        drawLine(Color.White, Offset(left - 0.9f * m, net), Offset(right + 0.9f * m, net), 4f)
        if (stroke != TrainedStroke.SERVE) {
            val hitter = if (stroke == TrainedStroke.VOLLEY) Offset(mid, net + 2f * m) else Offset(mid, base + 0.6f * m)
            drawCircle(TsmColors.Player1, 0.45f * m, hitter)
            val phone = Offset(right + 1f * m, hitter.y)
            drawRect(TsmColors.Ball, Offset(phone.x - 0.3f * m, phone.y - 0.5f * m), Size(0.6f * m, 1f * m))
            drawLine(TsmColors.Ball, phone, hitter, 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
            return@Canvas
        }
        // Chi serve: dietro la linea di fondo, a destra del centro (servizio da destra).
        val server = Offset(mid + 1f * m, base + 0.5f * m)
        drawCircle(TsmColors.Player1, 0.45f * m, server)
        // Il telefono: 3,5 m a destra di chi serve, puntato su di lui.
        val phone = Offset(server.x + 3.5f * m, base + 0.2f * m)
        drawRect(TsmColors.Ball, Offset(phone.x - 0.3f * m, phone.y - 0.5f * m), Size(0.6f * m, 1f * m))
        drawLine(TsmColors.Ball, phone, server, 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
        // Alternativa: dietro chi serve, un po' di lato (per vedere dove va la palla).
        val behind = Offset(server.x + 1.2f * m, base + 3.8f * m)
        drawRect(TsmColors.Ball.copy(alpha = 0.45f), Offset(behind.x - 0.3f * m, behind.y - 0.5f * m), Size(0.6f * m, 1f * m), style = st)
    }
}

/** Riepilogo di una sessione: totali, per lato, prima e seconda, bersaglio, polso; sotto, le clip da rivedere. */
@Composable
fun ServeSummaryContent(s: Strings, serves: List<ServeAttempt>, stroke: TrainedStroke = TrainedStroke.SERVE) {
    val b = ServeTraining.breakdown(serves, stroke)
    Text(s.serveCountLine(b.all.inPlay, b.all.total) + (b.all.pct?.let { " · $it%" } ?: ""), color = TsmColors.Ball, fontWeight = FontWeight.Bold, fontSize = 22.sp)
    @Composable
    fun rows(title: String, map: Map<String, ServeTraining.Count>) {
        if (map.isEmpty()) return
        Text(title, color = TsmColors.TextDim, fontSize = 13.sp)
        for ((name, n) in map) {
            Row(Modifier.fillMaxWidth()) {
                Text(name, color = TsmColors.TextMain, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text(s.serveCountLine(n.inPlay, n.total) + (n.pct?.let { " · $it%" } ?: ""), color = TsmColors.TextMain, fontSize = 14.sp)
            }
        }
    }
    rows(s.serveBySide, b.bySide.mapKeys { serveSideName(s, it.key) })
    rows(s.serveByKind, b.byKind.mapKeys { serveKindName(s, it.key) })
    rows(s.serveByTarget, b.byTarget.mapKeys { serveTargetName(s, it.key) })
    rows(s.byWing, b.byWing.mapKeys { wingName(s, it.key) })
    rows(s.byDirection, b.byDirection.mapKeys { directionName(s, it.key) })
    val gin = ServeTraining.meanGyro(serves.filter { it.inPlay })
    val gout = ServeTraining.meanGyro(serves.filter { !it.inPlay })
    if (gin != null && gout != null) Text(s.serveWrist(gin, gout), color = TsmColors.TextDim, fontSize = 13.sp)
}

/** Sessione salvata: riepilogo, clip da rivedere (tutte, solo dentro, solo fuori) ed eliminazione. */
@Composable
fun ServeSessionDialog(c: MatchController, sess: ServeSession, onClose: () -> Unit) {
    val s = LocalStrings.current
    val context = LocalContext.current
    var ask by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(s.serveSummary + " · " + strokeLabel(s, sess.stroke)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                sess.drillName?.let { Text(s.drillSession(it), color = TsmColors.Ball, fontWeight = FontWeight.Bold) }
                ServeSummaryContent(s, sess.serves, sess.stroke)
                ServeReplayButtons(c, sess)
                // Posizione del corpo nelle clip: misure dei momenti chiave, dentro contro fuori.
                if (sess.serves.any { it.clip != null }) {
                    GhostButton(s.poseTitle, Icons.Filled.AccessibilityNew, {
                        context.startActivity(Intent(context, com.tennis.scoremanager.video.ServeAnalysisActivity::class.java)
                            .putExtra(com.tennis.scoremanager.video.ServeAnalysisActivity.EXTRA_ID, sess.id))
                    }, Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(s.ok) } },
        dismissButton = {
            TextButton(onClick = { ask = true }) {
                Icon(Icons.Filled.Delete, null, tint = TsmColors.Danger)
                Text(" " + s.serveDelete, color = TsmColors.Danger)
            }
        },
    )
    if (ask) {
        AlertDialog(
            onDismissRequest = { ask = false },
            text = { Text(s.serveDeleteAsk) },
            confirmButton = { TextButton(onClick = { ask = false; c.deleteServeSession(sess); onClose() }) { Text(s.serveDelete, color = TsmColors.Danger) } },
            dismissButton = { TextButton(onClick = { ask = false }) { Text(s.back) } },
        )
    }
}

/** Rivedi tutti / solo dentro / solo fuori, nella moviola (pausa, rallentatore, fotogramma per fotogramma). */
@Composable
fun ServeReplayButtons(c: MatchController, sess: ServeSession) {
    val s = LocalStrings.current
    val context = LocalContext.current
    if (sess.serves.none { it.clip != null }) {
        Text(s.serveNoClips, color = TsmColors.TextDim, fontSize = 13.sp)
        return
    }
    fun play(list: List<ServeAttempt>) {
        val files = c.serveClipFiles(sess, list)
        if (files.isEmpty()) return
        context.startActivity(Intent(context, ReplayActivity::class.java).putExtra(ReplayActivity.EXTRA_FILES, files.map { it.absolutePath }.toTypedArray()))
    }
    GhostButton(s.servePlayAll, Icons.Filled.PlayArrow, { play(sess.serves) }, Modifier.fillMaxWidth())
    if (sess.serves.any { it.inPlay && it.clip != null }) GhostButton(s.servePlayIn, Icons.Filled.PlayArrow, { play(sess.serves.filter { it.inPlay }) }, Modifier.fillMaxWidth())
    if (sess.serves.any { !it.inPlay && it.clip != null }) GhostButton(s.servePlayOut, Icons.Filled.PlayArrow, { play(sess.serves.filter { !it.inPlay }) }, Modifier.fillMaxWidth())
}
