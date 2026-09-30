package com.tennis.scoremanager.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.tennis.scoremanager.ble.BandProtocol
import com.tennis.scoremanager.model.Lang

/**
 * Testi dell'app, uno per lingua (vedi [stringsFor]). Le etichette dei cronometri restano in inglese come sul
 * tabellone ATP. I testi per i braccialetti vanno in maiuscolo e senza accenti (il font è ASCII).
 */
interface Strings {
    val appName: String get() = "Tennis Score Manager"

    /** Sigla del giocatore/squadra 1 o 2 ("G1", "P1"...), usata dove non c'è posto per il nome. */
    val playerTag: (Int) -> String

    /** Data lunga per riepilogo e partite salvate (SimpleDateFormat). */
    val datePattern: String

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
    val bandCharging: (Int) -> String
    val bandChargeFull: String
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
    val languageHint: String
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
    val stopVoiceTest: String
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

    // Notifica mentre la partita è in corso
    val notifChannel: String
    val notifText: (bands: Boolean, tv: Boolean) -> String

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

    // Tabellone TV: testi della pagina (maiuscolo, come un tabellone a LED)
    val tvVs: String get() = "VS"
    val tvGames: String get() = "GAMES"
    val tvSet: String get() = "SET"
    val tvSec: String get() = "SEC"
    val tvServe: String
    val tvChangeover: String
    val tvSetBreak: String
    val tvTiebreakBreak: String
    val tvWaiting: String
    val tvReady: String
    val tvSuspended: String
    val tvWinner: String
    val tvTiebreak: String get() = "TIE-BREAK"
    val tvMatchTiebreak: String get() = "MATCH TIE-BREAK"
    val tvLost: String
    val tvFullscreen: String

    // Tabellone TV: impostazioni sul telefono dell'arbitro
    val tvSection: String
    val tvEnable: String
    val tvEnableHint: String
    val tvAddress: String
    val tvNoNetwork: String
    val tvScreens: (Int) -> String
    val tvQrHint: String
    val tvLook: String
    val tvTitle: String
    val tvTitleHint: (String) -> String
    val tvColorOf: (String) -> String
    val tvShowClock: String
    val tvShowTimers: String
    val tvShowSets: String
    val tvShowMessages: String
    val tvShowServe: String
    val tvGhost: String
    val tvPreview: String
    val tvChromecastHint: String

    // Telefono usato come tabellone
    val displayMode: String
    val displayModeHint: String
    val displaySearching: String
    val displaySteps: String
    val displayManual: String
    val displayConnect: String
    val displayNotFound: String
    val displayOnMonitor: String
    val displayShowHere: String
    val displayBackAgain: String
    val displayRetry: String
}

object ItStrings : Strings {
    override val playerTag: (Int) -> String = { "G$it" }
    override val datePattern = "EEEE d MMMM yyyy"

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
    override val bandCharging: (Int) -> String = { "In carica $it%" }
    override val bandChargeFull = "Carica completa"
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
    override val languageHint = "Vale per le schermate, la voce dell'arbitro, il tabellone TV e i braccialetti."
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
    override val stopVoiceTest = "Ferma la prova"
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

    override val notifChannel = "Partita in corso"
    override val notifText: (Boolean, Boolean) -> String = { bands, tv ->
        "Partita in corso · " + when {
            bands && tv -> "braccialetti e tabellone TV attivi"
            tv -> "tabellone TV attivo"
            else -> "braccialetti attivi"
        }
    }

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

    override val tvServe = "SERVIZIO"
    override val tvChangeover = "CAMBIO CAMPO"
    override val tvSetBreak = "PAUSA SET"
    override val tvTiebreakBreak = "PAUSA"
    override val tvWaiting = "IN ATTESA DELLA PARTITA"
    override val tvReady = "IN ATTESA DEL VIA"
    override val tvSuspended = "PARTITA SOSPESA"
    override val tvWinner = "VINCE"
    override val tvLost = "CONNESSIONE PERSA - RICONNESSIONE..."
    override val tvFullscreen = "SCHERMO INTERO"

    override val tvSection = "Tabellone su TV"
    override val tvEnable = "Tabellone su TV o monitor"
    override val tvEnableHint = "Un altro telefono (o un computer, o un Chromecast) mostra il punteggio in diretta su un monitor. I telefoni devono stare sulla stessa rete: l'hotspot di uno dei due."
    override val tvAddress = "Indirizzo del tabellone"
    override val tvNoNetwork = "Nessuna rete: accendi l'hotspot su uno dei due telefoni e collega l'altro."
    override val tvScreens: (Int) -> String = { if (it == 0) "Nessun tabellone collegato" else if (it == 1) "1 tabellone collegato" else "$it tabelloni collegati" }
    override val tvQrHint = "Sull'altro telefono: apri Tennis Score Manager e tocca «Usa come tabellone» (si collega da solo), oppure inquadra il codice con la fotocamera e aprilo nel browser."
    override val tvLook = "Aspetto del tabellone"
    override val tvTitle = "Scritta in basso"
    override val tvTitleHint: (String) -> String = { if (it.isEmpty()) "Vuota: nessuna scritta" else "Vuota: «$it» (circolo e campo della pagina 1)" }
    override val tvColorOf: (String) -> String = { "Colore di $it" }
    override val tvShowClock = "Tempo partita"
    override val tvShowTimers = "Cronometro servizio e pause"
    override val tvShowSets = "Set conclusi"
    override val tvShowMessages = "Messaggi (palla break, set point...)"
    override val tvShowServe = "Pallina di chi serve"
    override val tvGhost = "Segmenti spenti visibili"
    override val tvPreview = "Anteprima su questo telefono"
    override val tvChromecastHint = "Con un Chromecast: sul telefono-tabellone usa «Trasmetti schermo» (Smart View sui Samsung). Il Chromecast vuole una rete con internet: accendi i dati mobili sul telefono che fa l'hotspot."

    override val displayMode = "Usa come tabellone"
    override val displayModeHint = "Questo telefono mostra il punteggio sul monitor (cavo HDMI o Chromecast)"
    override val displaySearching = "Cerco il telefono dell'arbitro…"
    override val displaySteps = "1. Accendi l'hotspot su uno dei due telefoni e collega l'altro.\n2. Sul telefono dell'arbitro: pagina 2 → «Tabellone su TV» acceso.\n3. Collega questo telefono al monitor (cavo USB-C/HDMI) o trasmetti lo schermo a un Chromecast."
    override val displayManual = "Indirizzo (es. 192.168.43.1:8080)"
    override val displayConnect = "Collega"
    override val displayNotFound = "Non trovato. Controlla che i due telefoni siano sulla stessa rete e che il tabellone sia acceso nell'app dell'arbitro."
    override val displayOnMonitor = "Il tabellone è sul monitor esterno"
    override val displayShowHere = "Mostra anche qui"
    override val displayBackAgain = "Premi di nuovo indietro per uscire"
    override val displayRetry = "Cerca di nuovo"
}

object EnStrings : Strings {
    override val playerTag: (Int) -> String = { "P$it" }
    override val datePattern = "EEEE, d MMMM yyyy"

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
    override val bandCharging: (Int) -> String = { "Charging $it%" }
    override val bandChargeFull = "Fully charged"
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
    override val languageHint = "Applies to the screens, the umpire's voice, the TV scoreboard and the wristbands."
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
    override val stopVoiceTest = "Stop the test"
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

    override val notifChannel = "Match in progress"
    override val notifText: (Boolean, Boolean) -> String = { bands, tv ->
        "Match in progress · " + when {
            bands && tv -> "wristbands and TV scoreboard on"
            tv -> "TV scoreboard on"
            else -> "wristbands on"
        }
    }

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

    override val tvServe = "SERVE"
    override val tvChangeover = "CHANGEOVER"
    override val tvSetBreak = "SET BREAK"
    override val tvTiebreakBreak = "BREAK"
    override val tvWaiting = "WAITING FOR THE MATCH"
    override val tvReady = "READY TO PLAY"
    override val tvSuspended = "MATCH SUSPENDED"
    override val tvWinner = "WINNER"
    override val tvLost = "CONNECTION LOST - RECONNECTING..."
    override val tvFullscreen = "FULL SCREEN"

    override val tvSection = "TV scoreboard"
    override val tvEnable = "Scoreboard on a TV or monitor"
    override val tvEnableHint = "Another phone (or a computer, or a Chromecast) shows the live score on a monitor. The phones must be on the same network: one phone's hotspot."
    override val tvAddress = "Scoreboard address"
    override val tvNoNetwork = "No network: turn on the hotspot on one phone and connect the other."
    override val tvScreens: (Int) -> String = { if (it == 0) "No scoreboard connected" else if (it == 1) "1 scoreboard connected" else "$it scoreboards connected" }
    override val tvQrHint = "On the other phone: open Tennis Score Manager and tap «Use as scoreboard» (it connects by itself), or scan the code with the camera and open it in the browser."
    override val tvLook = "Scoreboard look"
    override val tvTitle = "Bottom line"
    override val tvTitleHint: (String) -> String = { if (it.isEmpty()) "Empty: no text" else "Empty: «$it» (club and court from page 1)" }
    override val tvColorOf: (String) -> String = { "Colour of $it" }
    override val tvShowClock = "Match time"
    override val tvShowTimers = "Serve clock and breaks"
    override val tvShowSets = "Finished sets"
    override val tvShowMessages = "Messages (break point, set point...)"
    override val tvShowServe = "Ball next to the server"
    override val tvGhost = "Unlit segments visible"
    override val tvPreview = "Preview on this phone"
    override val tvChromecastHint = "With a Chromecast: on the scoreboard phone use «Cast screen» (Smart View on Samsung). The Chromecast needs a network with internet: turn on mobile data on the hotspot phone."

    override val displayMode = "Use as scoreboard"
    override val displayModeHint = "This phone shows the score on the monitor (HDMI cable or Chromecast)"
    override val displaySearching = "Looking for the umpire's phone…"
    override val displaySteps = "1. Turn on the hotspot on one phone and connect the other.\n2. On the umpire's phone: page 2 → «TV scoreboard» on.\n3. Connect this phone to the monitor (USB-C/HDMI cable) or cast the screen to a Chromecast."
    override val displayManual = "Address (e.g. 192.168.43.1:8080)"
    override val displayConnect = "Connect"
    override val displayNotFound = "Not found. Check that both phones are on the same network and the scoreboard is on in the umpire's app."
    override val displayOnMonitor = "The scoreboard is on the external monitor"
    override val displayShowHere = "Show here too"
    override val displayBackAgain = "Press back again to exit"
    override val displayRetry = "Search again"
}

fun stringsFor(lang: Lang): Strings = when (lang) {
    Lang.IT -> ItStrings
    Lang.EN -> EnStrings
    Lang.FR -> FrStrings
    Lang.DE -> DeStrings
    Lang.ES -> EsStrings
    Lang.PT -> PtStrings
}

val LocalStrings = staticCompositionLocalOf<Strings> { ItStrings }
