package com.tennis.scoremanager.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.tennis.scoremanager.ble.BandProtocol
import com.tennis.scoremanager.model.Lang

/** Testi dell'app nelle due lingue. Le etichette dei cronometri restano in inglese come sul tabellone ATP. */
interface Strings {
    val appName: String get() = "Tennis Score Manager"

    // Pagina 1
    val setupTitle: String
    val setupSubtitle: String
    val clubSection: String
    val clubName: String
    val courtNumber: String
    val singles: String
    val doubles: String
    val doublesHint: String
    val player1: String
    val player2: String
    val playerName: String
    val clearFields: String
    val next: String
    val back: String

    // Pagina 2
    val optionsTitle: String
    val modeSection: String
    val modeReferee: String
    val modeBands: String
    val modeRefereeHint: String
    val modeBandsHint: String
    val requirements: String
    val bluetooth: String
    val location: String
    val locationServices: String
    val enable: String
    val allow: String
    val ok: String
    val bandFor: (String) -> String
    val noBand: String
    val bandConnected: String
    val bandConnecting: String
    val bandIdle: String
    val bandOff: String
    val battery: String
    val autoSearch: String
    val autoSearchOff: String
    val identify: String
    val swapBands: String
    val bandsOffAtEnd: String
    val bandsOffAtEndHint: String
    val bandNotReady: String

    // Impostazioni del braccialetto
    val bandSettings: String
    val settingsShort: String
    val bandSettingsNeedLink: String
    val bandFirmwareOld: String
    val bandFirmware: (String) -> String
    val bandName: String
    val brightness: String
    val scoreTime: String
    val scoreTimeHint: String
    val beeperVolume: String
    val mute: String
    val off: String
    val flipDisplay: String
    val flipDisplayHint: String
    val autoOff: String
    val pairTimeout: String
    val lostTimeout: String
    val idleTimeout: String
    val estimateFull: (String) -> String
    val estimateNow: (String, Int) -> String
    val estimateBreakdown: (String, String, String, String) -> String
    val estimateMeasured: String
    val estimateTheory: String
    val copyToOther: String
    val powerOff: String
    val languageSection: String
    val italian: String
    val english: String
    val audioSection: String
    val voiceCalls: String
    val voiceFiles: (Int, Int) -> String
    val voiceFilesHint: String
    val generateVoice: String
    val generating: (Int, Int) -> String
    val importVoiceZip: String
    val deleteCustomVoice: String
    val ttsEngine: String
    val engineDefault: String
    val ttsVoice: String
    val voiceAuto: String
    val voiceName: (String) -> String
    val online: String
    val offline: String
    val voiceFilesMode: String
    val voiceFilesModeHint: String
    val customRecordings: (Int, Int) -> String
    val testVoice: String
    val ttsMissing: String
    val installVoice: String
    val formatSection: String
    val formatBestOfThree: String
    val formatBestOfThreeHint: String
    val formatMatchTiebreak: String
    val formatMatchTiebreakHint: String
    val noAd: String
    val noAdHint: String
    val coinToss: String
    val tossCoin: String
    val tossWinner: (String) -> String
    val tossHint: String
    val serving: String
    val courtSides: String
    val umpireView: String
    val swapSides: String
    val firstServerOf: (String) -> String
    val left: String
    val right: String
    val net: String
    val umpireChair: String

    val locationDialogTitle: String
    val locationDialogText: String
    val continueWithout: String
    val bandsRequiredTitle: String
    val bandsRequiredText: String
    val bandsMissingTitle: String
    val bandsMissingText: (String) -> String
    val continueAnyway: String
    val cancel: String

    // Pagina 3
    val startMatch: String
    val startHint: String
    val startHintBands: String
    val startButton: String
    val resumeSaved: String
    val noSavedMatches: String
    val savedMatchesTitle: String
    val delete: String
    val vs: String

    // Partita
    val matchTime: String
    val shotClock: String get() = "Shot Clock"
    val changeoverTime: String get() = "Changeover Time"
    val setBreakTime: String get() = "Set Break Time"
    val tiebreakTime: String get() = "Tie-Break Time"
    val setsHeader: String
    val gamesHeader: String
    val onServe: String get() = "On Serve"
    val undoPoint: String
    val suspend: String
    val resume: String
    val audioOn: String
    val audioOff: String
    val newMatch: String
    val suspendedOverlay: String
    val newMatchConfirmTitle: String
    val newMatchConfirmText: String
    val endDialogTitle: String
    val endDialogText: (String, String) -> String
    val matchConcluded: String
    val undoLastPoint: String
    val serveOrderTitle: (Int) -> String
    val whoServesFirst: (String) -> String
    val confirm: String
    val backDisabled: String
    val exit: String
    val exitConfirmTitle: String
    val exitConfirmText: String
    val exitConfirmBands: String

    // Messaggi del riquadro arancione
    val msgChangeEnds: String
    val msgTiebreak: String
    val msgMatchTiebreak: String
    val msgSetWon: (String) -> String
    val msgSetPoint: String
    val msgMatchPoint: String
    val msgBreakPoint: String
    val msgDecidingPoint: String
    val msgPointUndone: String
    val msgSuspended: String
    val msgResumed: String
    val msgBandConnected: (String) -> String
    val msgBandLost: (String) -> String
    val msgBandOff: (String, Int?) -> String
    val msgBandBatteryLow: (String, Int) -> String
    val bandBatteryLow: String
    val autonomy: (String) -> String
    val bandsBattery: String

    // Braccialetti (solo ASCII, poche lettere)
    val bandPaired: String
    val bandPlay: String
    val bandChangeEnds: String
    val bandTiebreak: String
    val bandSet: String
    val bandSuspended: String
    val bandGameSetMatch: String
    val bandMatchOver: String
    val bandAppClosed: String
    val bandOffFromApp: String

    // Riepilogo
    val summaryTitle: String
    val winner: String
    val duration: String
    val startTime: String
    val endTime: String
    val date: String
    val club: String
    val court: String
    val place: String
    val placeUnavailable: String
    val format: String
    val pointsWon: String
    val gamesWon: String
    val result: String
    val saveHistory: String
    val share: String
    val saveDialogTitle: String
    val fileName: String
    val folder: String
    val chooseFolder: String
    val defaultFolder: String
    val formatReport: String
    val formatData: String
    val formatImage: String
    val save: String
    val savedTo: (String) -> String
    val saveError: String
    val shareSubject: String
    val playerDefault: (Int) -> String
    val teamJoiner: String
    val generatedWith: String
}

object ItStrings : Strings {
    override val setupTitle = "Nuova partita"
    override val setupSubtitle = "Configurazione facoltativa: puoi lasciare tutto vuoto e andare avanti."
    override val clubSection = "Circolo e campo"
    override val clubName = "Nome circolo tennis"
    override val courtNumber = "Numero campo"
    override val singles = "Singolare"
    override val doubles = "Doppio"
    override val doublesHint = "Nel doppio inserisci due nomi per squadra: il conteggio segue le regole ITF del doppio."
    override val player1 = "Giocatore 1"
    override val player2 = "Giocatore 2"
    override val playerName = "Nome"
    override val clearFields = "Svuota campi"
    override val next = "Avanti"
    override val back = "Indietro"

    override val optionsTitle = "Modalità e regole"
    override val modeSection = "Modalità di gioco"
    override val modeReferee = "Arbitro"
    override val modeBands = "Braccialetti"
    override val modeRefereeHint = "Il punteggio si assegna dal telefono, come il giudice di sedia."
    override val modeBandsHint = "Ogni giocatore assegna il punto con KEY1 del proprio M5StickS3."
    override val requirements = "Requisiti"
    override val bluetooth = "Bluetooth"
    override val location = "Permesso posizione"
    override val locationServices = "Posizione attiva"
    override val enable = "Attiva"
    override val allow = "Consenti"
    override val ok = "OK"
    override val bandFor: (String) -> String = { "Braccialetto di $it" }
    override val noBand = "Nessuno"
    override val bandConnected = "Connesso"
    override val bandConnecting = "Connessione…"
    override val bandIdle = "Non connesso"
    override val bandOff = "Spento"
    override val battery = "Batteria"
    override val autoSearch = "Ricerca automatica: accendi i braccialetti (tasto laterale), si associano da soli."
    override val autoSearchOff = "La ricerca parte quando i requisiti qui sopra sono a posto."
    override val identify = "Identifica"
    override val swapBands = "Scambia G1 ↔ G2"
    override val bandsOffAtEnd = "Spegni i braccialetti a fine partita e all'uscita"
    override val bandsOffAtEndHint = "Si riaccendono con un clic sul tasto laterale."
    override val bandNotReady = "Braccialetto non collegato"

    override val bandSettings = "Impostazioni braccialetto"
    override val settingsShort = "Impostazioni"
    override val bandSettingsNeedLink = "Collega il braccialetto per vedere e cambiare le impostazioni."
    override val bandFirmwareOld = "Il firmware di questo braccialetto non ha le impostazioni: carica TSM_Band.ino 2.0."
    override val bandFirmware: (String) -> String = { "Firmware $it" }
    override val bandName = "Nome"
    override val brightness = "Luminosità display"
    override val scoreTime = "Punteggio visibile dopo ogni punto"
    override val scoreTimeHint = "Il riepilogo di fine game resta 2 secondi in più."
    override val beeperVolume = "Volume cicalino"
    override val mute = "Muto"
    override val off = "No"
    override val flipDisplay = "Display capovolto"
    override val flipDisplayHint = "Per portare il braccialetto sull'altro polso."
    override val autoOff = "Spegnimento automatico"
    override val pairTimeout = "All'accensione, se nessun telefono si collega"
    override val lostTimeout = "Se perde il collegamento col telefono"
    override val idleTimeout = "Se resta collegato ma inattivo"
    override val estimateFull: (String) -> String = { "Autonomia stimata $it da carica piena" }
    override val estimateNow: (String, Int) -> String = { h, p -> "$h con la carica attuale ($p%)" }
    override val estimateBreakdown: (String, String, String, String) -> String = { tot, base, dsp, snd ->
        "Consumo medio $tot mA: scheda e Bluetooth $base · display $dsp · cicalino $snd"
    }
    override val estimateMeasured = "Base misurata su questo braccialetto durante l'uso."
    override val estimateTheory = "Stima teorica: dopo 20 minuti di uso si corregge col consumo misurato."
    override val copyToOther = "Copia sull'altro braccialetto"
    override val powerOff = "Spegni"
    override val languageSection = "Lingua"
    override val italian = "Italiano"
    override val english = "English"
    override val audioSection = "Audio e voce"
    override val voiceCalls = "Chiamate vocali dell'arbitro"
    override val voiceFiles: (Int, Int) -> String = { n, tot -> "File generati: $n/$tot" }
    override val voiceFilesHint = "Tutto funziona senza internet con le voci installate sul telefono. Le registrazioni personalizzate (ZIP) hanno sempre la precedenza sulla sintesi vocale."
    override val generateVoice = "Genera file"
    override val generating: (Int, Int) -> String = { n, tot -> "Generazione $n/$tot…" }
    override val importVoiceZip = "Importa ZIP"
    override val deleteCustomVoice = "Rimuovi registrazioni"
    override val ttsEngine = "Motore sintesi vocale"
    override val engineDefault = "Predefinito del telefono"
    override val ttsVoice = "Voce"
    override val voiceAuto = "Automatica (migliore offline)"
    override val voiceName: (String) -> String = { "Voce $it" }
    override val online = "online"
    override val offline = "offline"
    override val voiceFilesMode = "Usa file audio pre-generati"
    override val voiceFilesModeHint = "Di norma ogni chiamata è letta in un'unica frase (più naturale). Attivalo per usare i file generati, ad esempio per portarti offline una voce online."
    override val customRecordings: (Int, Int) -> String = { n, tot -> "Registrazioni personalizzate: $n/$tot" }
    override val testVoice = "Prova voce"
    override val ttsMissing = "Voce italiana della sintesi vocale non installata sul telefono."
    override val installVoice = "Installa voce"
    override val formatSection = "Formato partita"
    override val formatBestOfThree = "3 set · tie-break a 7"
    override val formatBestOfThreeHint = "Al meglio dei tre set, tie-break sul 6-6 in ogni set."
    override val formatMatchTiebreak = "2 set + super tie-break a 10"
    override val formatMatchTiebreakHint = "Sull'1-1 il terzo set è un match tie-break a 10 punti (2 di scarto)."
    override val noAd = "No-Ad (punto decisivo)"
    override val noAdHint = "Sul 40-40 si gioca un solo punto: chi lo vince vince il game."
    override val coinToss = "Sorteggio (Coin Toss)"
    override val tossCoin = "Lancia la moneta"
    override val tossWinner: (String) -> String = { "Vince il sorteggio: $it" }
    override val tossHint = "Chi vince sceglie: servizio, risposta o campo. Imposta qui la scelta."
    override val serving = "Al servizio"
    override val courtSides = "Lati del campo"
    override val umpireView = "Vista dal giudice di sedia"
    override val swapSides = "Inverti lati"
    override val firstServerOf: (String) -> String = { "Serve per primo ($it)" }
    override val left = "Sinistra"
    override val right = "Destra"
    override val net = "RETE"
    override val umpireChair = "Giudice di sedia"

    override val locationDialogTitle = "Abilita la posizione"
    override val locationDialogText = "Se non abiliti la posizione non potrai averla nei dati riepilogativi della partita."
    override val continueWithout = "Continua senza"
    override val bandsRequiredTitle = "Bluetooth e posizione obbligatori"
    override val bandsRequiredText = "Per usare i braccialetti devi attivare il Bluetooth, concedere il permesso di posizione e tenere attiva la posizione."
    override val bandsMissingTitle = "Braccialetti non associati"
    override val bandsMissingText: (String) -> String = { "Manca il braccialetto per: $it. Continuare lo stesso?" }
    override val continueAnyway = "Continua"
    override val cancel = "Annulla"

    override val startMatch = "INIZIO PARTITA"
    override val startHint = "Premi il pulsante per iniziare"
    override val startHintBands = "Premi il pulsante oppure KEY1 su un braccialetto"
    override val startButton = "Inizia partita"
    override val resumeSaved = "Riprendi partita sospesa"
    override val noSavedMatches = "Nessuna partita sospesa salvata."
    override val savedMatchesTitle = "Partite sospese"
    override val delete = "Elimina"
    override val vs = "vs"

    override val matchTime = "Match Time"
    override val setsHeader = "SET"
    override val gamesHeader = "GAME"
    override val undoPoint = "Annulla punto"
    override val suspend = "Sospendi"
    override val resume = "Riprendi"
    override val audioOn = "Audio On"
    override val audioOff = "Audio Off"
    override val newMatch = "Nuova partita"
    override val suspendedOverlay = "PARTITA SOSPESA"
    override val newMatchConfirmTitle = "Nuova partita?"
    override val newMatchConfirmText = "La partita in corso resta salvata tra le partite sospese e potrai riprenderla."
    override val endDialogTitle = "Gioco, set, partita"
    override val endDialogText: (String, String) -> String = { name, score -> "Vince $name\n$score" }
    override val matchConcluded = "Partita conclusa"
    override val undoLastPoint = "Annulla ultimo punto"
    override val serveOrderTitle: (Int) -> String = { "Ordine di servizio · set $it" }
    override val whoServesFirst: (String) -> String = { "Chi serve per primo in $it?" }
    override val confirm = "Conferma"
    override val backDisabled = "Durante la partita usa «Nuova partita» o «Esci»."
    override val exit = "Esci"
    override val exitConfirmTitle = "Uscire dall'app?"
    override val exitConfirmText = "La partita resta salvata tra le partite sospese e potrai riprenderla."
    override val exitConfirmBands = "I braccialetti vengono spenti."

    override val msgChangeEnds = "CAMBIO CAMPO"
    override val msgTiebreak = "TIE-BREAK"
    override val msgMatchTiebreak = "SUPER TIE-BREAK"
    override val msgSetWon: (String) -> String = { "SET $it" }
    override val msgSetPoint = "SET POINT"
    override val msgMatchPoint = "MATCH POINT"
    override val msgBreakPoint = "PALLA BREAK"
    override val msgDecidingPoint = "PUNTO DECISIVO"
    override val msgPointUndone = "PUNTO ANNULLATO"
    override val msgSuspended = "PARTITA SOSPESA"
    override val msgResumed = "PARTITA RIPRESA"
    override val msgBandConnected: (String) -> String = { "BRACCIALETTO $it CONNESSO" }
    override val msgBandLost: (String) -> String = { "BRACCIALETTO $it DISCONNESSO" }
    override val msgBandOff: (String, Int?) -> String = { n, why ->
        "BRACCIALETTO $n SPENTO" + when (why) {
            BandProtocol.OFF_IDLE -> " (INATTIVO)"
            BandProtocol.OFF_BATTERY -> " (BATTERIA SCARICA)"
            BandProtocol.OFF_TIMEOUT -> " (NESSUN TELEFONO)"
            else -> ""
        }
    }
    override val msgBandBatteryLow: (String, Int) -> String = { n, p -> "BRACCIALETTO $n: BATTERIA $p%" }
    override val bandBatteryLow = "BATTERIA BASSA"
    override val autonomy: (String) -> String = { "autonomia ~$it" }
    override val bandsBattery = "Batteria braccialetti"

    override val bandPaired = "ASSOCIATO A"
    override val bandPlay = "GIOCO"
    override val bandChangeEnds = "CAMBIO CAMPO"
    override val bandTiebreak = "TIE-BREAK"
    override val bandSet = "SET"
    override val bandSuspended = "SOSPESA"
    override val bandGameSetMatch = "GAME SET MATCH"
    override val bandMatchOver = "FINE PARTITA"
    override val bandAppClosed = "APP CHIUSA"
    override val bandOffFromApp = "SPEGNIMENTO"

    override val summaryTitle = "Partita conclusa"
    override val winner = "Vincitore"
    override val duration = "Durata"
    override val startTime = "Inizio"
    override val endTime = "Fine"
    override val date = "Data"
    override val club = "Circolo"
    override val court = "Campo"
    override val place = "Luogo"
    override val placeUnavailable = "Posizione non disponibile"
    override val format = "Formato"
    override val pointsWon = "Punti vinti"
    override val gamesWon = "Game vinti"
    override val result = "Risultato"
    override val saveHistory = "Salva nello storico"
    override val share = "Condividi"
    override val saveDialogTitle = "Salva nello storico"
    override val fileName = "Nome"
    override val folder = "Cartella"
    override val chooseFolder = "Scegli cartella"
    override val defaultFolder = "Cartella dell'app (predefinita)"
    override val formatReport = "Resoconto (.txt)"
    override val formatData = "Dati partita (.json)"
    override val formatImage = "Immagine (.png)"
    override val save = "Salva"
    override val savedTo: (String) -> String = { "Salvato in $it" }
    override val saveError = "Salvataggio non riuscito"
    override val shareSubject = "Risultato partita di tennis"
    override val playerDefault: (Int) -> String = { "Giocatore $it" }
    override val teamJoiner = " e "
    override val generatedWith = "Creato con Tennis Score Manager"
}

object EnStrings : Strings {
    override val setupTitle = "New match"
    override val setupSubtitle = "Optional setup: you can leave everything empty and go on."
    override val clubSection = "Club and court"
    override val clubName = "Tennis club name"
    override val courtNumber = "Court number"
    override val singles = "Singles"
    override val doubles = "Doubles"
    override val doublesHint = "In doubles enter two names per team: scoring follows the ITF doubles rules."
    override val player1 = "Player 1"
    override val player2 = "Player 2"
    override val playerName = "Name"
    override val clearFields = "Clear fields"
    override val next = "Next"
    override val back = "Back"

    override val optionsTitle = "Mode and rules"
    override val modeSection = "Play mode"
    override val modeReferee = "Umpire"
    override val modeBands = "Wristbands"
    override val modeRefereeHint = "Points are scored on the phone, like a chair umpire."
    override val modeBandsHint = "Each player scores with KEY1 on their own M5StickS3."
    override val requirements = "Requirements"
    override val bluetooth = "Bluetooth"
    override val location = "Location permission"
    override val locationServices = "Location on"
    override val enable = "Turn on"
    override val allow = "Allow"
    override val ok = "OK"
    override val bandFor: (String) -> String = { "$it's wristband" }
    override val noBand = "None"
    override val bandConnected = "Connected"
    override val bandConnecting = "Connecting…"
    override val bandIdle = "Not connected"
    override val bandOff = "Off"
    override val battery = "Battery"
    override val autoSearch = "Searching automatically: switch the wristbands on (side button), they pair by themselves."
    override val autoSearchOff = "The search starts once the requirements above are met."
    override val identify = "Identify"
    override val swapBands = "Swap P1 ↔ P2"
    override val bandsOffAtEnd = "Switch the wristbands off at match end and on exit"
    override val bandsOffAtEndHint = "One click on the side button switches them back on."
    override val bandNotReady = "Wristband not connected"

    override val bandSettings = "Wristband settings"
    override val settingsShort = "Settings"
    override val bandSettingsNeedLink = "Connect the wristband to see and change its settings."
    override val bandFirmwareOld = "This wristband's firmware has no settings: upload TSM_Band.ino 2.0."
    override val bandFirmware: (String) -> String = { "Firmware $it" }
    override val bandName = "Name"
    override val brightness = "Display brightness"
    override val scoreTime = "Score shown after each point"
    override val scoreTimeHint = "The end-of-game summary stays 2 seconds longer."
    override val beeperVolume = "Beeper volume"
    override val mute = "Mute"
    override val off = "Off"
    override val flipDisplay = "Flip display"
    override val flipDisplayHint = "To wear the wristband on the other wrist."
    override val autoOff = "Automatic power-off"
    override val pairTimeout = "At power-on, if no phone connects"
    override val lostTimeout = "If the phone link is lost"
    override val idleTimeout = "If connected but idle"
    override val estimateFull: (String) -> String = { "Estimated battery life $it from full" }
    override val estimateNow: (String, Int) -> String = { h, p -> "$h at the current charge ($p%)" }
    override val estimateBreakdown: (String, String, String, String) -> String = { tot, base, dsp, snd ->
        "Average draw $tot mA: board and Bluetooth $base · display $dsp · beeper $snd"
    }
    override val estimateMeasured = "Base draw measured on this wristband while in use."
    override val estimateTheory = "Theoretical estimate: after 20 minutes of use it switches to the measured draw."
    override val copyToOther = "Copy to the other wristband"
    override val powerOff = "Power off"
    override val languageSection = "Language"
    override val italian = "Italiano"
    override val english = "English"
    override val audioSection = "Audio and voice"
    override val voiceCalls = "Umpire voice calls"
    override val voiceFiles: (Int, Int) -> String = { n, tot -> "Generated files: $n/$tot" }
    override val voiceFilesHint = "Everything works offline with the voices installed on the phone. Custom recordings (ZIP) always take precedence over text-to-speech."
    override val generateVoice = "Generate files"
    override val generating: (Int, Int) -> String = { n, tot -> "Generating $n/$tot…" }
    override val importVoiceZip = "Import ZIP"
    override val deleteCustomVoice = "Remove recordings"
    override val ttsEngine = "Speech engine"
    override val engineDefault = "Phone default"
    override val ttsVoice = "Voice"
    override val voiceAuto = "Automatic (best offline)"
    override val voiceName: (String) -> String = { "Voice $it" }
    override val online = "online"
    override val offline = "offline"
    override val voiceFilesMode = "Use pre-generated audio files"
    override val voiceFilesModeHint = "By default each call is read as one sentence (more natural). Turn this on to play the generated files, e.g. to take an online voice offline."
    override val customRecordings: (Int, Int) -> String = { n, tot -> "Custom recordings: $n/$tot" }
    override val testVoice = "Test voice"
    override val ttsMissing = "English text-to-speech voice is not installed on this phone."
    override val installVoice = "Install voice"
    override val formatSection = "Match format"
    override val formatBestOfThree = "3 sets · tie-break to 7"
    override val formatBestOfThreeHint = "Best of three sets, tie-break at 6-6 in every set."
    override val formatMatchTiebreak = "2 sets + match tie-break to 10"
    override val formatMatchTiebreakHint = "At one set all the third set is a 10-point match tie-break (win by 2)."
    override val noAd = "No-Ad (deciding point)"
    override val noAdHint = "At deuce a single deciding point is played."
    override val coinToss = "Coin toss"
    override val tossCoin = "Toss the coin"
    override val tossWinner: (String) -> String = { "Toss won by: $it" }
    override val tossHint = "The winner chooses serve, receive or end. Set the choice here."
    override val serving = "Serving"
    override val courtSides = "Court ends"
    override val umpireView = "Chair umpire's view"
    override val swapSides = "Swap ends"
    override val firstServerOf: (String) -> String = { "Serves first ($it)" }
    override val left = "Left"
    override val right = "Right"
    override val net = "NET"
    override val umpireChair = "Chair umpire"

    override val locationDialogTitle = "Turn on location"
    override val locationDialogText = "Without location it cannot be included in the match summary."
    override val continueWithout = "Continue without"
    override val bandsRequiredTitle = "Bluetooth and location required"
    override val bandsRequiredText = "To use the wristbands turn on Bluetooth, allow location and keep location on."
    override val bandsMissingTitle = "Wristbands not assigned"
    override val bandsMissingText: (String) -> String = { "No wristband for: $it. Continue anyway?" }
    override val continueAnyway = "Continue"
    override val cancel = "Cancel"

    override val startMatch = "MATCH START"
    override val startHint = "Press the button to start"
    override val startHintBands = "Press the button or KEY1 on a wristband"
    override val startButton = "Start match"
    override val resumeSaved = "Resume suspended match"
    override val noSavedMatches = "No suspended match saved."
    override val savedMatchesTitle = "Suspended matches"
    override val delete = "Delete"
    override val vs = "vs"

    override val matchTime = "Match Time"
    override val setsHeader = "SETS"
    override val gamesHeader = "GAMES"
    override val undoPoint = "Undo point"
    override val suspend = "Suspend"
    override val resume = "Resume"
    override val audioOn = "Audio On"
    override val audioOff = "Audio Off"
    override val newMatch = "New match"
    override val suspendedOverlay = "MATCH SUSPENDED"
    override val newMatchConfirmTitle = "New match?"
    override val newMatchConfirmText = "The current match stays saved among suspended matches and can be resumed."
    override val endDialogTitle = "Game, set and match"
    override val endDialogText: (String, String) -> String = { name, score -> "$name wins\n$score" }
    override val matchConcluded = "Match concluded"
    override val undoLastPoint = "Undo last point"
    override val serveOrderTitle: (Int) -> String = { "Serving order · set $it" }
    override val whoServesFirst: (String) -> String = { "Who serves first for $it?" }
    override val confirm = "Confirm"
    override val backDisabled = "During the match use «New match» or «Exit»."
    override val exit = "Exit"
    override val exitConfirmTitle = "Exit the app?"
    override val exitConfirmText = "The match stays saved among suspended matches and can be resumed."
    override val exitConfirmBands = "The wristbands are switched off."

    override val msgChangeEnds = "CHANGE ENDS"
    override val msgTiebreak = "TIE-BREAK"
    override val msgMatchTiebreak = "MATCH TIE-BREAK"
    override val msgSetWon: (String) -> String = { "SET $it" }
    override val msgSetPoint = "SET POINT"
    override val msgMatchPoint = "MATCH POINT"
    override val msgBreakPoint = "BREAK POINT"
    override val msgDecidingPoint = "DECIDING POINT"
    override val msgPointUndone = "POINT UNDONE"
    override val msgSuspended = "MATCH SUSPENDED"
    override val msgResumed = "MATCH RESUMED"
    override val msgBandConnected: (String) -> String = { "WRISTBAND $it CONNECTED" }
    override val msgBandLost: (String) -> String = { "WRISTBAND $it DISCONNECTED" }
    override val msgBandOff: (String, Int?) -> String = { n, why ->
        "WRISTBAND $n OFF" + when (why) {
            BandProtocol.OFF_IDLE -> " (IDLE)"
            BandProtocol.OFF_BATTERY -> " (BATTERY EMPTY)"
            BandProtocol.OFF_TIMEOUT -> " (NO PHONE)"
            else -> ""
        }
    }
    override val msgBandBatteryLow: (String, Int) -> String = { n, p -> "WRISTBAND $n: BATTERY $p%" }
    override val bandBatteryLow = "LOW BATTERY"
    override val autonomy: (String) -> String = { "about $it left" }
    override val bandsBattery = "Wristband battery"

    override val bandPaired = "PAIRED WITH"
    override val bandPlay = "PLAY"
    override val bandChangeEnds = "CHANGE ENDS"
    override val bandTiebreak = "TIE-BREAK"
    override val bandSet = "SET"
    override val bandSuspended = "SUSPENDED"
    override val bandGameSetMatch = "GAME SET MATCH"
    override val bandMatchOver = "MATCH OVER"
    override val bandAppClosed = "APP CLOSED"
    override val bandOffFromApp = "POWER OFF"

    override val summaryTitle = "Match concluded"
    override val winner = "Winner"
    override val duration = "Duration"
    override val startTime = "Start"
    override val endTime = "End"
    override val date = "Date"
    override val club = "Club"
    override val court = "Court"
    override val place = "Place"
    override val placeUnavailable = "Location not available"
    override val format = "Format"
    override val pointsWon = "Points won"
    override val gamesWon = "Games won"
    override val result = "Result"
    override val saveHistory = "Save to history"
    override val share = "Share"
    override val saveDialogTitle = "Save to history"
    override val fileName = "Name"
    override val folder = "Folder"
    override val chooseFolder = "Choose folder"
    override val defaultFolder = "App folder (default)"
    override val formatReport = "Report (.txt)"
    override val formatData = "Match data (.json)"
    override val formatImage = "Image (.png)"
    override val save = "Save"
    override val savedTo: (String) -> String = { "Saved to $it" }
    override val saveError = "Saving failed"
    override val shareSubject = "Tennis match result"
    override val playerDefault: (Int) -> String = { "Player $it" }
    override val teamJoiner = " and "
    override val generatedWith = "Made with Tennis Score Manager"
}

fun stringsFor(lang: Lang): Strings = if (lang == Lang.IT) ItStrings else EnStrings

val LocalStrings = staticCompositionLocalOf<Strings> { ItStrings }
