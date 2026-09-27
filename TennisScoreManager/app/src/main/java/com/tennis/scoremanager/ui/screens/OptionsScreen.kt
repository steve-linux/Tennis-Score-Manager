package com.tennis.scoremanager.ui.screens

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.speech.tts.TextToSpeech
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.SportsTennis
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tennis.scoremanager.MatchController
import com.tennis.scoremanager.Screen
import com.tennis.scoremanager.ble.BandInfo
import com.tennis.scoremanager.ble.BleManager
import com.tennis.scoremanager.ble.FoundBand
import com.tennis.scoremanager.ble.LinkState
import com.tennis.scoremanager.data.LocationHelper
import com.tennis.scoremanager.data.MatchOptions
import com.tennis.scoremanager.data.Names
import com.tennis.scoremanager.data.PlayMode
import com.tennis.scoremanager.model.Lang
import com.tennis.scoremanager.model.MatchFormat
import com.tennis.scoremanager.model.Side
import com.tennis.scoremanager.ui.BigButton
import com.tennis.scoremanager.ui.GhostButton
import com.tennis.scoremanager.ui.LocalStrings
import com.tennis.scoremanager.ui.RequirementRow
import com.tennis.scoremanager.ui.ScreenScaffold
import com.tennis.scoremanager.ui.SectionCard
import com.tennis.scoremanager.ui.SegOption
import com.tennis.scoremanager.ui.Segmented
import com.tennis.scoremanager.ui.Strings
import com.tennis.scoremanager.ui.SwitchRow
import com.tennis.scoremanager.ui.TsmColors
import com.tennis.scoremanager.voice.Phrases
import com.tennis.scoremanager.voice.TtsStatus
import kotlinx.coroutines.launch
import kotlin.random.Random

/** Pagina 2: modalità, braccialetti, lingua, voce, formato e sorteggio. */
@Composable
fun OptionsScreen(c: MatchController) {
    val s = LocalStrings.current
    val context = LocalContext.current
    val o by c.options.collectAsState()
    val su by c.setup.collectAsState()
    val env by c.envTick.collectAsState()
    val btOn by c.ble.adapterOn.collectAsState()
    var permTick by remember { mutableIntStateOf(0) }
    val names = remember(su, s) { Names(su, s) }

    val blePerms = remember(env, permTick) { c.ble.hasPermissions() }
    val locPerm = remember(env, permTick) { LocationHelper.hasPermission(context) }
    val locOn = remember(env, permTick) { LocationHelper.isEnabled(context) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permTick++
        c.ble.reconnectAll()
    }
    val btLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { permTick++ }

    fun requestBandPermissions() {
        val extra = if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.POST_NOTIFICATIONS) else emptyArray()
        permLauncher.launch(BleManager.requiredPermissions() + extra)
    }

    fun requestLocation() = permLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    fun openLocationSettings() = runCatching { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
    fun enableBluetooth() {
        if (!blePerms) requestBandPermissions() else runCatching { btLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }
    }

    var locationDialog by remember { mutableStateOf(false) }
    var bandsRequired by remember { mutableStateOf(false) }
    var missingBands by remember { mutableStateOf<String?>(null) }
    val bandsReadyEnv = blePerms && btOn && locPerm && locOn
    BackHandler { c.back() }

    fun onNext() {
        if (o.mode == PlayMode.BANDS) {
            if (!bandsReadyEnv) {
                bandsRequired = true
                return
            }
            val bands = c.ble.bands.value
            val missing = Side.entries.filter { bands[it] == null }.map { names.short(it) }
            if (missing.isNotEmpty()) {
                missingBands = missing.joinToString(", ")
                return
            }
        } else if (!(locPerm && locOn)) {
            locationDialog = true
            return
        }
        c.go(Screen.START)
    }

    ScreenScaffold(
        title = s.optionsTitle,
        subtitle = null,
        icon = Icons.Filled.Tune,
        bottomBar = {
            GhostButton(s.back, Icons.AutoMirrored.Filled.ArrowBack, { c.back() }, Modifier.weight(1f))
            BigButton(s.next, Icons.AutoMirrored.Filled.ArrowForward, { onNext() }, Modifier.weight(1f))
        },
    ) {
        // Modalità
        SectionCard(s.modeSection, Icons.Filled.SportsTennis) {
            Segmented(
                listOf(SegOption(s.modeReferee, Icons.Filled.Gavel), SegOption(s.modeBands, Icons.Filled.Watch)),
                selected = if (o.mode == PlayMode.BANDS) 1 else 0,
                onSelect = { i -> c.updateOptions { it.copy(mode = if (i == 1) PlayMode.BANDS else PlayMode.REFEREE) } },
            )
            Text(if (o.mode == PlayMode.BANDS) s.modeBandsHint else s.modeRefereeHint, color = TsmColors.TextDim)
            if (o.mode == PlayMode.BANDS) {
                Text(s.requirements, style = MaterialTheme.typography.titleMedium, color = TsmColors.TextMain)
                RequirementRow(Icons.Filled.Bluetooth, s.bluetooth, btOn, s.enable) { enableBluetooth() }
                RequirementRow(Icons.Filled.LocationOn, s.location, blePerms && locPerm, s.allow) { requestBandPermissions() }
                RequirementRow(Icons.Filled.MyLocation, s.locationServices, locOn, s.enable) { openLocationSettings() }
                BandsSection(c, names, enabled = bandsReadyEnv)
            }
        }

        // Lingua
        SectionCard(s.languageSection, Icons.Filled.Language) {
            Segmented(
                listOf(SegOption("🇮🇹  ${s.italian}"), SegOption("🇬🇧  ${s.english}")),
                selected = if (o.lang == Lang.IT) 0 else 1,
                onSelect = { i -> c.updateOptions { it.copy(lang = if (i == 0) Lang.IT else Lang.EN) } },
            )
        }

        // Audio e voce
        VoiceSection(c, o)

        // Formato
        SectionCard(s.formatSection, Icons.Filled.EmojiEvents) {
            FormatOption(s.formatBestOfThree, s.formatBestOfThreeHint, o.format == MatchFormat.BEST_OF_THREE) {
                c.updateOptions { it.copy(format = MatchFormat.BEST_OF_THREE) }
            }
            FormatOption(s.formatMatchTiebreak, s.formatMatchTiebreakHint, o.format == MatchFormat.TWO_SETS_MATCH_TIEBREAK) {
                c.updateOptions { it.copy(format = MatchFormat.TWO_SETS_MATCH_TIEBREAK) }
            }
            SwitchRow(Icons.Filled.Timer, s.noAd, s.noAdHint, o.noAd) { v -> c.updateOptions { it.copy(noAd = v) } }
        }

        // Sorteggio
        CoinTossSection(c, o, names, su.doubles)
    }

    if (locationDialog) {
        AlertDialog(
            onDismissRequest = { locationDialog = false },
            icon = { Icon(Icons.Filled.LocationOn, null, tint = TsmColors.Orange) },
            title = { Text(s.locationDialogTitle) },
            text = { Text(s.locationDialogText) },
            confirmButton = {
                Button(onClick = {
                    locationDialog = false
                    if (!locPerm) requestLocation() else openLocationSettings()
                }) { Text(s.enable) }
            },
            dismissButton = {
                TextButton(onClick = {
                    locationDialog = false
                    c.go(Screen.START)
                }) { Text(s.continueWithout) }
            },
        )
    }
    if (bandsRequired) {
        AlertDialog(
            onDismissRequest = { bandsRequired = false },
            icon = { Icon(Icons.Filled.Bluetooth, null, tint = TsmColors.Orange) },
            title = { Text(s.bandsRequiredTitle) },
            text = { Text(s.bandsRequiredText) },
            confirmButton = {
                Button(onClick = {
                    bandsRequired = false
                    when {
                        !(blePerms && locPerm) -> requestBandPermissions()
                        !btOn -> enableBluetooth()
                        !locOn -> openLocationSettings()
                    }
                }) { Text(s.enable) }
            },
            dismissButton = { TextButton(onClick = { bandsRequired = false }) { Text(s.cancel) } },
        )
    }
    missingBands?.let { list ->
        AlertDialog(
            onDismissRequest = { missingBands = null },
            icon = { Icon(Icons.Filled.Watch, null, tint = TsmColors.Orange) },
            title = { Text(s.bandsMissingTitle) },
            text = { Text(s.bandsMissingText(list)) },
            confirmButton = {
                Button(onClick = {
                    missingBands = null
                    c.go(Screen.START)
                }) { Text(s.continueAnyway) }
            },
            dismissButton = { TextButton(onClick = { missingBands = null }) { Text(s.cancel) } },
        )
    }
}

@Composable
private fun BandsSection(c: MatchController, names: Names, enabled: Boolean) {
    val s = LocalStrings.current
    val found by c.ble.found.collectAsState()
    val scanning by c.ble.scanning.collectAsState()
    val bands by c.ble.bands.collectAsState()
    var searched by remember { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        BigButton(
            if (scanning) s.searching else s.searchBands,
            Icons.AutoMirrored.Filled.BluetoothSearching,
            onClick = {
                searched = true
                if (scanning) c.ble.stopScan() else c.ble.startScan()
            },
            enabled = enabled,
            modifier = Modifier.weight(1f),
            color = TsmColors.SurfaceHigh,
            onColor = TsmColors.TextMain,
        )
        if (scanning) {
            Spacer(Modifier.width(12.dp))
            CircularProgressIndicator(Modifier.size(28.dp), color = TsmColors.Ball, strokeWidth = 3.dp)
        }
    }
    if (searched && !scanning && found.isEmpty()) Text(s.bandNotFound, color = TsmColors.Orange)
    for (side in Side.entries) {
        BandPicker(c, side, names.short(side), bands[side], found, bands)
    }
}

@Composable
private fun BandPicker(c: MatchController, side: Side, playerName: String, current: BandInfo?, found: List<FoundBand>, all: Map<Side, BandInfo>) {
    val s = LocalStrings.current
    var open by remember { mutableStateOf(false) }
    val accent = TsmColors.player(side)
    Column(Modifier.fillMaxWidth()) {
        Text(s.bandFor(playerName), color = accent, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Box {
            OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                val (icon, tint) = when (current?.state) {
                    LinkState.READY -> Icons.Filled.BluetoothConnected to TsmColors.Ok
                    LinkState.CONNECTING -> Icons.Filled.Bluetooth to TsmColors.Orange
                    LinkState.POWERED_OFF -> Icons.Filled.PowerSettingsNew to TsmColors.Danger
                    else -> Icons.Filled.BluetoothDisabled to TsmColors.TextDim
                }
                Icon(icon, null, tint = tint)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(current?.name ?: s.noBand, color = TsmColors.TextMain, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (current != null) {
                        val st = when (current.state) {
                            LinkState.READY -> s.bandConnected
                            LinkState.CONNECTING -> s.bandConnecting
                            LinkState.POWERED_OFF -> s.bandOff
                            LinkState.IDLE -> s.bandIdle
                        }
                        Text(st + (current.battery?.let { " · ${s.battery} $it%" } ?: ""), color = TsmColors.TextDim, fontSize = 12.sp)
                    }
                }
                Icon(Icons.Filled.ArrowDropDown, null, tint = TsmColors.TextDim)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                DropdownMenuItem(text = { Text(s.noBand) }, onClick = {
                    open = false
                    c.assignBand(side, null, null)
                })
                val options = (found.map { it.address to "${it.name}  (${it.rssi} dBm)" } +
                    all.values.map { it.address to it.name }).distinctBy { it.first }
                for ((address, label) in options) {
                    val usedBy = all.entries.firstOrNull { it.value.address == address && it.key != side }?.key
                    DropdownMenuItem(
                        text = { Text(label + (usedBy?.let { " → ${if (it == Side.P1) "G1" else "G2"}" } ?: "")) },
                        leadingIcon = { Icon(Icons.Filled.Watch, null) },
                        onClick = {
                            open = false
                            c.assignBand(side, address, label.substringBefore("  ("))
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun VoiceSection(c: MatchController, o: MatchOptions) {
    val s = LocalStrings.current
    val context = LocalContext.current
    val generated by c.voiceCount.collectAsState()
    val custom by c.customVoiceCount.collectAsState()
    val progress by c.voiceProgress.collectAsState()
    val tts by c.announcer.status.collectAsState()
    val engines by c.announcer.engines.collectAsState()
    val voices by c.announcer.voices.collectAsState()
    val currentVoice by c.announcer.currentVoice.collectAsState()
    val currentEngine by c.announcer.currentEngineFlow.collectAsState()
    val zipLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { c.importVoiceZip(it) } }
    val total = Phrases.keys.size

    SectionCard(s.audioSection, Icons.AutoMirrored.Filled.VolumeUp) {
        SwitchRow(Icons.Filled.RecordVoiceOver, s.voiceCalls, null, o.audio) { v -> c.updateOptions { it.copy(audio = v) } }

        // Motore: Samsung, Google, ... (i nomi delle voci cambiano col motore, quindi si riparte da "automatica")
        val engineLabel = engines.firstOrNull { it.pkg == (o.ttsEngine ?: currentEngine) }?.label
        Picker(
            label = s.ttsEngine,
            value = if (o.ttsEngine == null) "${s.engineDefault}${engineLabel?.let { " ($it)" } ?: ""}" else engineLabel ?: o.ttsEngine,
            options = listOf<Pair<String?, String>>(null to s.engineDefault) + engines.map { it.pkg to it.label },
        ) { pkg -> c.updateOptions { it.copy(ttsEngine = pkg, ttsVoice = null) } }

        Picker(
            label = s.ttsVoice,
            value = if (o.ttsVoice == null) "${s.voiceAuto}${currentVoice?.let { " · ${voiceLabel(it, s)}" } ?: ""}"
            else voiceLabel(o.ttsVoice, s),
            options = listOf<Pair<String?, String>>(null to s.voiceAuto) +
                voices.map { it.name to "${voiceLabel(it.name, s)} · ${if (it.online) s.online else s.offline}" },
        ) { name -> c.updateOptions { it.copy(ttsVoice = name) } }

        if (tts == TtsStatus.MISSING_LANGUAGE || tts == TtsStatus.ERROR) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.ttsMissing, color = TsmColors.Orange, modifier = Modifier.weight(1f), fontSize = 13.sp)
                TextButton(onClick = {
                    runCatching { context.startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)) }
                }) { Text(s.installVoice) }
            }
        }
        SmallAction(s.testVoice, Icons.Filled.PlayCircle, Modifier.fillMaxWidth()) { c.testVoice() }
        Text(s.voiceFilesHint, color = TsmColors.TextDim, fontSize = 13.sp)

        // Registrazioni personalizzate (voce vera): sempre prioritarie
        Text(s.customRecordings(custom, total), color = if (custom > 0) TsmColors.Ok else TsmColors.TextDim, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallAction(s.importVoiceZip, Icons.Filled.FolderZip, Modifier.weight(1f)) {
                zipLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
            }
            SmallAction(s.deleteCustomVoice, Icons.Filled.DeleteOutline, Modifier.weight(1f), enabled = custom > 0) { c.deleteCustomVoice() }
        }

        // File pre-generati (facoltativi)
        SwitchRow(Icons.Filled.FolderZip, s.voiceFilesMode, s.voiceFilesModeHint, o.voiceFiles) { v -> c.updateOptions { it.copy(voiceFiles = v) } }
        if (o.voiceFiles) {
            Text(s.voiceFiles(generated, total), color = if (generated == total) TsmColors.Ok else TsmColors.Orange, fontWeight = FontWeight.Bold)
            progress?.let { (i, n) ->
                Text(s.generating(i, n), color = TsmColors.TextMain)
                LinearProgressIndicator(progress = { if (n == 0) 0f else i / n.toFloat() }, modifier = Modifier.fillMaxWidth(), color = TsmColors.Ball)
            }
            SmallAction(s.generateVoice, Icons.Filled.RecordVoiceOver, Modifier.fillMaxWidth(), enabled = progress == null) { c.generateVoice() }
        }
        Text(c.voice.baseDir.absolutePath, color = TsmColors.TextDim, fontSize = 11.sp)
    }
}

/** "it-it-x-itb-local" -> "Voce ITB"; gli altri nomi restano come sono. */
private fun voiceLabel(name: String, s: Strings): String =
    if ("-x-" in name) s.voiceName(name.substringAfter("-x-").substringBefore('-').uppercase()) else name

/** Campo a tendina semplice: etichetta, valore attuale e voci del menu (chiave, testo). */
@Composable
private fun <T> Picker(label: String, value: String, options: List<Pair<T, String>>, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Text(label, color = TsmColors.TextDim, fontSize = 13.sp)
        Spacer(Modifier.height(4.dp))
        Box {
            OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Text(value, color = TsmColors.TextMain, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(Icons.Filled.ArrowDropDown, null, tint = TsmColors.TextDim)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                for ((key, text) in options) {
                    DropdownMenuItem(text = { Text(text) }, onClick = {
                        open = false
                        onSelect(key)
                    })
                }
            }
        }
    }
}

@Composable
private fun SmallAction(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(46.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = TsmColors.SurfaceHigh, contentColor = TsmColors.TextMain),
    ) {
        Icon(icon, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun FormatOption(title: String, hint: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .border(2.dp, if (selected) TsmColors.Ball else TsmColors.Outline, RoundedCornerShape(14.dp))
            .background(if (selected) TsmColors.Ball.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(22.dp).clip(CircleShape).border(2.dp, if (selected) TsmColors.Ball else TsmColors.TextDim, CircleShape),
            contentAlignment = Alignment.Center,
        ) { if (selected) Box(Modifier.size(12.dp).clip(CircleShape).background(TsmColors.Ball)) }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, color = TsmColors.TextMain, fontWeight = FontWeight.Bold)
            Text(hint, color = TsmColors.TextDim, fontSize = 13.sp)
        }
    }
}

@Composable
private fun CoinTossSection(c: MatchController, o: MatchOptions, names: Names, doubles: Boolean) {
    val s = LocalStrings.current
    val scope = rememberCoroutineScope()
    val angle = remember { Animatable(if (o.tossWinner == Side.P2) 180f else 0f) }
    var flipping by remember { mutableStateOf(false) }

    SectionCard(s.coinToss, Icons.Filled.Casino) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Coin(angle.value, names)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BigButton(s.tossCoin, Icons.Filled.Casino, enabled = !flipping, onClick = {
                    flipping = true
                    scope.launch {
                        val w = if (Random.nextBoolean()) Side.P1 else Side.P2
                        angle.snapTo(angle.value % 360f)
                        val target = angle.value - angle.value % 360f + 360f * 6 + if (w == Side.P2) 180f else 0f
                        angle.animateTo(target, tween(1700, easing = FastOutSlowInEasing))
                        c.updateOptions { it.copy(tossWinner = w, firstServer = w) }
                        flipping = false
                    }
                })
                o.tossWinner?.let {
                    if (!flipping) Text(s.tossWinner(names.short(it)), color = TsmColors.player(it), fontWeight = FontWeight.Black)
                }
            }
        }
        Text(s.tossHint, color = TsmColors.TextDim, fontSize = 13.sp)

        Text(s.serving, style = MaterialTheme.typography.titleMedium, color = TsmColors.TextMain)
        Segmented(
            Side.entries.map { SegOption(names.short(it), Icons.Filled.SportsTennis, TsmColors.player(it), TsmColors.onPlayer(it)) },
            selected = o.firstServer.ordinal,
            onSelect = { i -> c.updateOptions { it.copy(firstServer = Side.entries[i]) } },
        )
        if (doubles) {
            for (side in Side.entries) {
                Text(s.firstServerOf(names.short(side)), color = TsmColors.player(side), fontSize = 13.sp)
                Segmented(
                    names.players(side).map { SegOption(it, null, TsmColors.player(side), TsmColors.onPlayer(side)) },
                    selected = if (side == Side.P1) o.firstServerP1 else o.firstServerP2,
                    onSelect = { i -> c.updateOptions { if (side == Side.P1) it.copy(firstServerP1 = i) else it.copy(firstServerP2 = i) } },
                )
            }
        }

        Text("${s.courtSides} · ${s.umpireView}", style = MaterialTheme.typography.titleMedium, color = TsmColors.TextMain)
        CourtDiagram(o.p1Left, o.firstServer, names, s)
        GhostButton(s.swapSides, Icons.Filled.SwapHoriz, { c.updateOptions { it.copy(p1Left = !it.p1Left) } }, Modifier.fillMaxWidth())
    }
}

/** Moneta che gira: fronte = Giocatore 1 (giallo), retro = Giocatore 2 (rosso). */
@Composable
private fun Coin(angle: Float, names: Names) {
    val a = ((angle % 360f) + 360f) % 360f
    val back = a in 90f..270f
    val side = if (back) Side.P2 else Side.P1
    Box(
        Modifier.size(96.dp).graphicsLayer {
            rotationY = angle
            cameraDistance = 14f * density
        }.clip(CircleShape).background(
            Brush.radialGradient(listOf(Color(0xFFFFF3B0), Color(0xFFD4A017), Color(0xFF8C6A00))),
        ).border(4.dp, TsmColors.player(side), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.graphicsLayer { if (back) rotationY = 180f }) {
            Text(if (side == Side.P1) "1" else "2", fontSize = 34.sp, fontWeight = FontWeight.Black, color = Color(0xFF3B2B00))
            Text(names.short(side).take(10), fontSize = 10.sp, color = Color(0xFF3B2B00), maxLines = 1)
        }
    }
}

/** Campo visto dall'alto con il giudice di sedia a bordo rete: i giocatori sono a sinistra e a destra. */
@Composable
fun CourtDiagram(p1Left: Boolean, server: Side, names: Names, s: Strings) {
    val left = if (p1Left) Side.P1 else Side.P2
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().aspectRatio(2.1f).clip(RoundedCornerShape(14.dp)).background(TsmColors.Court)) {
            Canvas(Modifier.fillMaxSize()) {
                val pad = 10.dp.toPx()
                val w = size.width
                val h = size.height
                val line = TsmColors.CourtLine
                drawRect(line, topLeft = Offset(pad, pad), size = Size(w - 2 * pad, h - 2 * pad), style = Stroke(3f))
                val alley = (h - 2 * pad) * 0.125f
                drawLine(line, Offset(pad, pad + alley), Offset(w - pad, pad + alley), 2f)
                drawLine(line, Offset(pad, h - pad - alley), Offset(w - pad, h - pad - alley), 2f)
                val cx = w / 2
                val svc = (w - 2 * pad) / 2 * 0.538f
                drawLine(line, Offset(cx - svc, pad + alley), Offset(cx - svc, h - pad - alley), 2f)
                drawLine(line, Offset(cx + svc, pad + alley), Offset(cx + svc, h - pad - alley), 2f)
                drawLine(line, Offset(cx - svc, h / 2), Offset(cx + svc, h / 2), 2f)
                drawLine(Color.White, Offset(cx, pad - 6f), Offset(cx, h - pad + 6f), 6f)
            }
            Row(Modifier.fillMaxSize()) {
                for (side in listOf(left, left.other)) {
                    Column(
                        Modifier.weight(1f).fillMaxSize().padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Box(
                            Modifier.clip(RoundedCornerShape(10.dp)).background(TsmColors.player(side)).padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Text(
                                names.short(side), color = TsmColors.onPlayer(side), fontWeight = FontWeight.Bold,
                                maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, fontSize = 13.sp,
                            )
                        }
                        // Lo spazio della pallina c'è sempre, così i due nomi restano allineati.
                        Spacer(Modifier.height(4.dp))
                        Icon(
                            Icons.Filled.SportsTennis, null,
                            tint = if (side == server) TsmColors.Ball else Color.Transparent,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth()) {
            Text("◀ ${s.left}", color = TsmColors.TextDim, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text("▲ ${s.umpireChair}", color = TsmColors.TextMain, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text("${s.right} ▶", color = TsmColors.TextDim, fontSize = 12.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        }
    }
}
