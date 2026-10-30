// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

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
    val sportSection: String
    val sportTennis: String
    val sportPadel: String
    val padelHint: String
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
    val modeRemotes: String
    val modeRemotesHint: String
    val remotesHowTo: String
    val bluetoothSettings: String
    val remoteFor: (String) -> String
    val noRemote: String
    val remoteConnected: String
    val remoteNotConnected: String
    val assignRemote: String
    val removeRemote: String
    val pressRemoteKey: String
    val remotePressed: String
    val remotesScreenOn: String
    val requirements: String
    val bluetooth: String
    val location: String
    val locationServices: String
    val enable: String
    val allow: String
    val ok: String
    // Fasce cardio
    val heartSection: String
    val heartRate: String
    val heartRateHint: String
    val heartHowTo: String
    val strapFor: (String) -> String
    val noStrap: String
    val strapSearching: String
    val strapConnected: String
    val strapConnecting: String
    val strapWaiting: String
    val strapNoContact: String
    val strapRelayed: String
    val strapNoDrops: String
    val strapDrops: (Int, Int) -> String
    val relayTitle: String
    val relayHint: String
    val relayMain: String
    val relayJoin: String
    val relayShowCode: String
    val relayShowQr: String
    val relayScanPrompt: String
    val relayNoSatellite: String
    val relaySatellites: (String) -> String
    val relayStop: String
    val relaySatelliteOn: String
    val relayConnected: String
    val relayDisconnected: String
    val relayPending: (Int) -> String
    val relayPreparing: String
    val relayShowBoard: String
    val workoutTitle: String
    val workoutHint: String
    val workoutPickStrap: String
    val workoutStart: String
    val workoutPause: String
    val workoutResume: String
    val workoutLap: String
    val workoutEnd: String
    val workoutRecovering: (Int) -> String
    val workoutRecoveringHint: String
    val workoutSaveNow: String
    val workoutHistory: String
    val workoutNone: String
    val workoutDuration: String
    val workoutHrv: String
    val workoutHrvHint: String
    val workoutRecovery: String
    val workoutRecoveryHint: String
    val workoutLaps: String
    val workoutAvgShort: String
    val workoutMaxShort: String
    val workoutRecShort: String
    val workoutShareCsv: String
    val workoutSharePdf: String
    val workoutDeleteTitle: String
    val workoutDeleteText: String
    val workoutChart: String
    val progressTitle: String
    val progressFilters: String
    val progressPeriod: String
    val progressPeriodAll: String
    val progress30: String
    val progress90: String
    val progress365: String
    val progressWeekdays: String
    val progressOpponent: String
    val progressAnyOption: String
    val progressChoose: String
    val progressSelected: (Int, Int) -> String
    val progressSelectAll: String
    val progressSelectNone: String
    val progressNoneMatch: String
    val progressHint: String
    val progressWorkouts: String
    val progressMatches: String
    val progressPlayer: String
    val progressNoPlayers: String
    val progressCount: (Int) -> String
    val progressTooFew: String
    val progressFirstHalf: String
    val progressSecondHalf: String
    val progressTrend: String
    val progressBetter: String
    val progressWorse: String
    val progressSame: String
    val progressList: String
    val progressDate: String
    val progressResult: String
    val progressWon: String
    val progressLost: String
    val progressPointsShort: String
    val progressDfShort: String
    val progressWinnersShort: String
    val progressUeShort: String
    val progressWins: String
    val progressPointsWon: String
    val progressPdf: String
    val progressPrint: String
    val relayWifiOff: String
    val relayWifiOn: String
    val relayJoinCamera: String
    val cameraRoleOn: String
    val cameraOpen: String
    val cameraLiveFailed: (String) -> String
    val cameraNotSupported: String
    val cameraSaved: (Int) -> String
    val cameraSaveFailed: (String) -> String
    val cameraPermissions: String
    val cameraConnecting: String
    val cameraNoReferee: String
    val cameraWaitingScore: String
    val cameraScoreOk: String
    val cameraRec: String
    val cameraStopRec: String
    val cameraGoLive: String
    val cameraStopLive: String
    val cameraOverlay: String
    val cameraStreamSettings: String
    val cameraClose: String
    val cameraStopFirst: String
    /** Telecamera comandata dal telefono dell'arbitro: inquadratura e comandi. */
    val camRemoteTitle: String
    val camRemoteOpen: String
    val camRemoteHint: String
    val camRemoteWaiting: String
    val camRemoteGone: String
    val camRemoteZoom: String
    val camRemoteBattery: (Int) -> String
    val camRemoteFree: (String) -> String
    val cameraNoStreamKey: String
    /** Assistente inquadratura (telefono-telecamera) e scelta dell'obiettivo. */
    val assistTitle: String
    val assistButton: String
    val assistHint: String
    val assistDistance: String
    val assistHeight: String
    val assistCourt: String
    val courtTennisSingles: String
    val courtTennisDoubles: String
    val courtPadel: String
    val assistTiltIs: (String) -> String
    val assistFitsWith: (String) -> String
    val assistCornersOut: (String) -> String
    val assistMinDistance: (String, String) -> String
    val assistTooTall: String
    val assistUseLens: (String) -> String
    val assistShowLevel: String
    val assistHideLevel: String
    val assistPitch: String
    val assistLevel: String
    val assistAimLower: (String) -> String
    val assistAimHigher: (String) -> String
    val assistTurnCw: (String) -> String
    val assistTurnCcw: (String) -> String
    val assistOk: String
    val assistNoSensor: String
    val assistCenter: String
    val lensMain: String
    val lensWide: String
    val cameraLens: String
    val cameraLensBusy: String
    /** Sagoma del campo sull'anteprima della telecamera e sua taratura. */
    val outlineButton: String
    val outlineTitle: String
    val outlineShow: String
    val outlineHide: String
    val outlineHint: String
    val calibDistance: String
    val calibHeight: String
    val calibPan: String
    val calibLateral: String
    val calibFov: String
    val calibPitch: String
    val outlineLoaded: (String) -> String
    val outlineReset: String
    val outlineDone: String
    val cameraStreamHint: String
    val cameraStreamUrl: String
    val cameraStreamKey: String
    val videoTitle: String
    val videoHint: String
    val videoNone: String
    val videoPoints: (Int) -> String
    val videoDeleteTitle: String
    val videoDeleteText: String
    val videoYoutubeChapters: String
    val videoNoPoints: String
    val videoPoint: String
    val videoGame: String
    val videoSet: String
    val videoReplay: String
    val videoStart: String
    val videoKind: (String) -> String
    val videoImport: String
    val videoImportHint: String
    val videoTagHowTo: String
    val videoTagTooEarly: String
    val videoPointFor: (String) -> String
    val videoUndoLast: String
    val videoTagSetupTitle: String
    val videoFirstServer: String
    val videoTagSetupHint: String
    val hlTitle: String
    val hlBig: String
    val hlRallies: String
    val hlMax: String
    val hlCustom: String
    val hlMusic: String
    val hlMusicPick: String
    val hlMusicReset: String
    val hlMusicHint: String
    val hlMusicCredit: (String) -> String
    val eventSection: String
    val eventName: String
    val eventCategory: String
    val eventRound: String
    val eventHint: String
    val playerRank: String
    val hlStats: String
    val hlStatsHint: (Int, Int) -> String
    val hlStatsNone: String
    val cardTitle: (String) -> String
    val cardFinal: String
    val cardPartial: String
    val cardBreakPoints: String
    val cardRallies: String
    val cardShortRallies: String
    val cardLongRallies: String
    val hlNone: String
    val hlPreview: (Int, String) -> String
    val hlHint: String
    val hlCreate: String
    val hlMaking: (Int) -> String
    val hlDone: String
    val hlFailed: String
    val hlOrderHint: String
    val hlUp: String
    val hlDown: String
    val coachTitle: String
    val coachPlayer: String
    val coachAll: String
    val coachPoint: String
    val coachWon: String
    val coachLost: String
    val coachServe: String
    val coachServing: String
    val coachReturning: String
    val coachServeKind: String
    val coachFirst: String
    val coachSecond: String
    val coachEnd: String
    val coachSituation: String
    val coachBreak: String
    val coachSetMatch: String
    val coachApply: String
    val coachClear: String
    val coachCount: (Int) -> String
    val coachSummary: (Int, Int) -> String
    val coachPlay: String
    val coachNone: String
    val replayTitle: String
    val replayHint: String
    val replayLastPoint: String
    val replayPrevPoint: String
    val replaySinceLast: String
    val replayWaiting: String
    val replayFailed: String
    val replayClose: String
    val replayOnBoard: String
    val replayAlsoOnBoard: String
    val replayNoCamera: String
    val cameraReplaySent: String
    val cameraReplayNone: String
    val relayOwnNetwork: (String) -> String
    val relayJoinWaiting: String
    val relayJoinRefused: String
    val relayJoinLost: String
    val relayJoinByHand: (String, String) -> String
    val heartAge: String
    val heartWeight: String
    val heartMale: String
    val heartFemale: String
    val heartProfileHint: String
    val heartStat: String
    val heartAvg: String
    val heartMax: String
    val heartCalories: String
    val heartChart: String
    /** "medio 138 · max 176 bpm" */
    val heartLine: (Int, Int) -> String
    /** "~620 kcal (stima)" */
    val caloriesLine: (Int) -> String
    val heartStatsHint: String
    val heartZones: String
    /** "Z3 Moderata": zone da 0 a 4. */
    val zoneName: (Int) -> String
    val heartZonesHint: String
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

    // Colpi (braccialetti con firmware 2.4)
    val strokes: String
    /** Nome del colpo BandProtocol.STROKE_* ("Dritto"). */
    val strokeName: (Int) -> String
    /** Sigla del colpo per la schermata partita ("D"). */
    val strokeAbbr: (Int) -> String
    val strokeOther: String
    val strokeRecognition: String
    val strokeRecognitionHint: String
    val strokeOnBand: String
    val strokeOnBandHint: String
    val strokeCalibration: String
    val strokeCalibrationHint: String
    val calibrate: String
    val calibrateStop: String
    val undoExample: String
    val clearExamples: String
    val strokesReady: String
    val strokesNotReady: String
    val strokesFirmwareOld: String
    /** Riepilogo: velocità di rotazione del polso al picco del colpo (°/s). */
    val wristSpeed: String
    val maxShort: String
    /** Riepilogo: colpi per punto (lunghezza degli scambi). */
    val strokesPerPoint: String
    val rallyAverage: String
    val rallyLongest: String
    val rallyShort: String
    val rallyMedium: String
    val rallyLong: String
    // Riepilogo: schede, statistiche e cronologia punto per punto
    val tabSummary: String
    val tabStats: String
    val tabChronology: String
    val periodMatch: String
    /** "Set 1", "Set 2"... */
    val periodSet: (Int) -> String
    val statsHint: String
    val statsHintDetailed: String
    val statServe: String
    val statReturn: String
    val statPoints: String
    val statGames: String
    val statServicePoints: String
    val statServiceGames: String
    val statBreakSaved: String
    val statReturnPoints: String
    val statReturnGames: String
    val statBreakWon: String
    val statTotalPoints: String
    val statStreak: String
    val statLast10: String
    val statMatchPointsSaved: String
    val statTotalGames: String
    val statGamePointsWon: String
    val statSetPointsWon: String
    val statMatchPointsWon: String
    val statLoveGames: String
    val statBestGameRun: String
    val statSetsFromBreakDown: String
    val statTimes: String
    val statSetDurations: String
    val statLongestGame: String
    /** "12' (14 punti)" */
    val longestGameValue: (String, Int) -> String
    val statAces: String
    val statDoubleFaults: String
    val statFirstServeIn: String
    val statFirstServeWon: String
    val statSecondServeWon: String
    val statWinners: String
    val statErrors: String
    val statUnforcedErrors: String
    val statForcedErrors: String
    val detailAskOutcome: String
    val detailAskErrorType: String
    val detailUnforced: String
    val detailForced: String
    val detailWinnerOf: (String) -> String
    val detailErrorOf: (String) -> String
    val trendTitle: String
    val trendHint: String
    val trendPointsAvg: (Int) -> String
    val trendDetailTitle: (Int) -> String
    val detailedStats: String
    val detailedStatsHint: String
    val btnAce: String
    val btnFault: String
    val btnDoubleFault: String
    val btnWinner: String
    val btnError: String
    val btnPoint: String
    val firstServe: String
    val secondServe: String
    /** "servizio di Rossi": sotto il tasto Ace. */
    val serveOf: (String) -> String
    val statStrokesTotal: String
    val statAvgWristSpeed: String
    val serveLost: String
    val markerBreak: String
    val markerSet: String
    val markerMatch: String
    val chronoGame: String
    val chronoBreak: String
    val chronoSet: String
    val chronoMatch: String
    val chronoTiebreak: String
    val powerOff: String
    /** Conferma di "Spegni" dal pannello del braccialetto (nome del braccialetto). */
    val bandPowerOffTitle: String
    val bandPowerOffText: (String) -> String
    val languageSection: String
    val languageHint: String
    val audioSection: String
    val voiceCalls: String
    val voiceFiles: (Int, Int) -> String
    val voiceFilesHint: String
    val generateVoice: String
    val generating: (Int, Int) -> String
    /** "Genera file" non arrivato in fondo: restano i file di prima. */
    val voiceGenerationFailed: String
    val importVoiceZip: String
    val voiceImportFailed: String
    val deleteCustomVoice: String
    val deleteCustomConfirmTitle: String
    val deleteCustomConfirmText: (Int) -> String
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
    /** Intestazione di LEGGIMI.txt nella cartella voce (lingue, formati) e titolo della colonna delle chiavi. */
    val voiceReadme: (String, String) -> String
    val voiceReadmeKey: String
    val testVoice: String
    val stopVoiceTest: String
    val ttsMissing: String
    val installVoice: String
    /** Il motore di sintesi vocale non parte: installare una voce non serve. */
    val ttsEngineError: String
    val openTtsSettings: String
    val formatSection: String
    val formatBestOfThree: String
    val formatBestOfThreeHint: String
    val formatMatchTiebreak: String
    val formatMatchTiebreakHint: String
    val formatBestOfFive: String
    val formatBestOfFiveHint: String
    val formatProSet: String
    val formatProSetHint: String
    val formatMatchTiebreakOnly: String
    val formatMatchTiebreakOnlyHint: String
    val gamesPerSetTitle: String
    val gamesShort: String
    val gamesShortHint: String
    val setTiebreak: String
    val labelShortSets: String
    val labelTiebreak7: String
    val labelNoTiebreak: String
    val gamesN: (Int) -> String
    val setTiebreakHint: (Int) -> String
    val labelSets: (Int) -> String
    val labelProSet: (Int) -> String
    val noAd: String
    val noAdHint: String
    val goldenPoint: String
    val goldenPointHint: String
    val starPoint: String
    val starPointHint: String
    /** No-Ad nella riga del formato (riepilogo, resoconto, immagine). */
    val noAdShort: String get() = "No-Ad"
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
    val addressLookup: String
    val addressLookupHint: String
    val bandsRequiredTitle: String
    val bandsRequiredText: String
    val bandsMissingTitle: String
    /** Giocatori senza braccialetto (uno o due: singolare o plurale). */
    val bandsMissingText: (List<String>) -> String
    val remotesMissingTitle: String
    val remotesMissingText: (List<String>) -> String
    val continueAnyway: String
    val cancel: String

    // Pagina 3
    val startMatch: String
    val startHint: String
    val startHintBands: String
    val startHintRemotes: String
    val startButton: String
    val resumeSaved: String
    val noSavedMatches: String
    val savedMatchesTitle: String
    val delete: String
    /** Conferma prima di eliminare una partita sospesa ("Rossi vs Bianchi"). */
    val deleteSavedTitle: String
    val deleteSavedText: (String) -> String
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
    val lockScreen: String
    val lockedTitle: String
    val unlockHint: String
    val suspend: String
    val resume: String
    val audioOn: String
    val audioOff: String
    val newMatch: String
    val suspendedOverlay: String
    val newMatchConfirmTitle: String
    val newMatchConfirmText: String
    val endDialogTitle: String
    /** Vincitore, punteggio, doppio (il verbo va al plurale: "Vincono Rossi e Bianchi"). */
    val endDialogText: (String, String, Boolean) -> String
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
    val msgSecondServe: String
    val msgFaultUndone: String
    val msgSuspended: String
    val msgResumed: String
    val msgBandConnected: (String) -> String
    val msgBandLost: (String) -> String
    val msgRemoteConnected: (String) -> String
    val msgRemoteLost: (String) -> String
    val msgBandOff: (String, Int?) -> String
    val msgBandBatteryLow: (String, Int) -> String
    val bandBatteryLow: String
    val autonomy: (String) -> String
    val bandsBattery: String

    // Notifica mentre la partita è in corso
    val notifChannel: String
    val notifText: (bands: Boolean, tv: Boolean) -> String
    /** Nessuna partita in corso (riepilogo, nuova partita) ma tabellone TV acceso. */
    val notifTvOnly: String

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
    val historyTitle: String
    val historySubtitle: String
    val historyImport: String
    val historyMatches: String
    val historyPlayers: String
    val historyEmpty: String
    val historyPlayersEmpty: String
    /** "Vinte 3 · perse 1" */
    val historyRecord: (Int, Int) -> String
    val historyHeadToHead: String
    val historyDeleteTitle: String
    val historyDeleteText: String
    /** Partite importate: nello storico e tra le sospese. */
    val historyImported: (Int, Int) -> String
    val historyImportFailed: String
    // Trasferimento tra telefoni con QR (Storico › Trasferisci)
    val transferTitle: String
    val transferHint: String
    val transferSend: String
    val transferReceive: String
    val transferShowQr: String
    val transferSent: String
    val transferClose: String
    val transferScanPrompt: String
    val transferNoNetwork: String
    val transferBadCode: String
    val transferFailed: String
    val transferEmpty: String
    // Foto dei giocatori
    val photoHint: String
    val photoChange: String
    val photoRemove: String
    val photoNeedsName: String
    val photoError: String
    val matchPhotosTitle: String
    val matchPhotosHint: String
    val addPhotos: String
    val takePhoto: String
    val matchPhotosFull: String
    val matchNotesTitle: String
    /** Rating TSM (Elo dalle partite dello storico) e classifica. */
    val ratingTitle: String
    val ratingHint: String
    val ratingProvisional: (Int) -> String
    val ratingMatches: (Int, Int) -> String
    val ratingLast: (String) -> String
    val ratingRanking: String
    val ratingNone: String
    val ratingFriendly: String
    val ratingFriendlyHint: String
    val ratingInMatch: String
    val ratingNotCounted: String
    val ratingShowAll: String
    val ratingShowLess: String
    /** Rivincita: invito da condividere, calendario e stessa partita in Nuova partita. */
    val rematchTitle: String
    val rematchHint: String
    val rematchAsk: String
    val rematchVersus: String
    val rematchLast: (String, String) -> String
    val rematchWhen: String
    val rematchPlace: String
    val rematchNote: String
    val rematchNoteHint: String
    val rematchSend: String
    val rematchCalendar: String
    val rematchPrepare: String
    val rematchEvent: (String) -> String
    val rematchNoCalendar: String
    val rematchPreview: String
    /** Allenamento al servizio: telefono a fianco di chi serve, braccialetto per dentro e fuori. */
    val serveTitle: String
    val serveHint: String
    val serveStart: String
    val serveNoBand: String
    val serveBandOk: (String) -> String
    val serveSideDeuce: String
    val serveSideAd: String
    val serveFirst: String
    val serveSecond: String
    val serveTargetT: String
    val serveTargetBody: String
    val serveTargetWide: String
    val serveTargetNone: String
    val serveIn: String
    val serveOut: String
    val serveUndo: String
    val serveSayIn: (Int, Int) -> String
    val serveSayOut: (Int, Int) -> String
    val serveSayUndo: String
    val serveWaiting: String
    val serveDetected: String
    val serveEnd: String
    val serveSummary: String
    val serveCountLine: (Int, Int) -> String
    val serveBySide: String
    val serveByKind: String
    val serveByTarget: String
    val serveWrist: (Int, Int) -> String
    val servePlayAll: String
    val servePlayIn: String
    val servePlayOut: String
    val serveSessions: String
    val serveDelete: String
    val serveDeleteAsk: String
    val servePlaceTitle: String
    val servePlaceText: String
    val servePlaceShow: String
    val serveNoClips: String
    val servePlaceServer: String
    val servePlacePhone: String
    val servePlaceAlt: String
    /** Analisi del movimento del servizio (posizione del corpo dalle clip). */
    val poseTitle: String
    val poseHint: String
    val poseAnalyzeAll: String
    val poseAnalyzing: (Int, Int) -> String
    val poseNoBody: String
    val poseUnreliable: String
    val poseTrophy: String
    val poseKnee: String
    val poseContact: String
    val poseKneeMetric: String
    val poseElbowMetric: String
    val poseHeightMetric: String
    val poseTossMetric: String
    val poseTiltMetric: String
    val poseTimingMetric: String
    val poseHeightValue: (String) -> String
    val poseInVsOut: String
    val poseServe: (Int) -> String
    val poseNoClip: String
    val poseDone: String
    /** Modello MoveNet scaricato a richiesta. */
    val poseModelTitle: String
    val poseModelHint: String
    val poseModelDownload: String
    val poseModelDownloading: (Int) -> String
    val poseModelFailed: String
    val poseModelBad: String
    val poseModelDelete: String
    val poseUnsupported: String
    val poseWatch: String
    val poseManyPeople: (Int) -> String
    val poseZoomHint: String
    val poseRedo: String
    /** Allenamento di dritto, rovescio e volée (oltre al servizio). */
    val strokeTrainingTitle: String
    /** Cartella in Documenti/Tennis Score Manager con le quattro cartelle dei colpi. */
    val strokeFolderName: String
    /** Nome della cartella Documenti come la mostra il telefono. */
    val documentsFolder: String
    val strokeAllSessions: (Int) -> String
    val strokeSessionsTitle: (String) -> String
    val strokeSessionsWhere: (String) -> String
    val strokeSpace: (String, Int) -> String
    val strokeSelectAll: String
    val strokeSelectHint: String
    val strokeDeleteN: (Int) -> String
    val strokeVideosN: (Int) -> String
    val strokeDeleteAsk: (Int) -> String
    val strokeVideosAsk: (Int) -> String
    val strokeNoVideos: String
    val strokeAnalysed: String
    val strokeTrend: String
    val strokeNone: String
    val progressStrokes: String
    val progressStrokesHint: String
    val strokeTrainingHint: String
    val dirCross: String
    val dirMiddle: String
    val dirLine: String
    val dirNone: String
    val wingFh: String
    val wingBh: String
    val byDirection: String
    val byWing: String
    val strokeWaiting: String
    val strokeDetected: String
    val placeHitter: String
    val placeGroundText: String
    val placeVolleyText: String
    val posePrep: String
    val poseFinish: String
    val poseInFrontMetric: String
    val poseBackswingMetric: String
    val poseFinishMetric: String
    val poseTimingGround: String
    val poseHintGround: String
    val poseAnalyzeShots: String
    val poseAnalyzingShot: (Int, Int) -> String
    /** Prima schermata a cinque schede (Gioca, Allenati, Video, Progressi, Altro). */
    val tabPlay: String
    val tabTrain: String
    val tabVideo: String
    val tabProgress: String
    val tabMore: String
    val playNewMatchHint: String
    val playRematch: (String) -> String
    val playResume: (String) -> String
    val playTournamentHint: String
    val playUseAs: String
    val playAsBoardHint: String
    val playAsCamera: String
    val playAsCameraHint: String
    val trainCardio: String
    val trainCardioHint: String
    val videoRecord: String
    val videoRecordHint: String
    val videoLibrary: String
    val videoLibraryHint: String
    val progressHistoryHint: String
    val progressTrendHint: String
    val progressRacketsHint: String
    val moreDevices: String
    val moreDevicesHint: String
    val moreVoice: String
    val moreVoiceHint: String
    val moreRacketsHint: String
    val moreTransfer: String
    val moreTransferHint: String
    val statusBands: (Int) -> String
    val statusStraps: (Int) -> String
    val statusCamera: String
    val statusTv: String
    val statusNone: String
    val playTournament: String
    val step1Hint: String
    val step2Hint: String
    val moreDetails: String
    val moreDetailsHint: String
    val recentPlayers: String
    /** Prima accensione guidata e profilo Tu. */
    val onbWelcome: String
    val onbLang: String
    val onbUseTitle: String
    val onbUseReferee: String
    val onbUseRefereeHint: String
    val onbUseBands: String
    val onbUseBandsHint: String
    val onbUseTrain: String
    val onbUseTrainHint: String
    val onbUseVideo: String
    val onbUseVideoHint: String
    val onbNameTitle: String
    val onbNameHint: String
    val onbSkip: String
    val onbStart: String
    val moreProfile: String
    val moreProfileHint: String
    val moreTour: String
    val moreTourHint: String
    val profileName: String
    val matchNotesHint: String
    val formatPdf: String
    val shareChoose: String
    val shareChooseHint: String
    val shareImage: String
    val sharePdf: String
    // Tornei Americano e Mexicano
    val tournamentButton: String
    val tournamentTitle: String
    val tournamentSubtitle: String
    val tournamentNew: String
    val tournamentAmericano: String
    val tournamentMexicano: String
    val tournamentPlayersCount: (Int) -> String
    val tournamentRound: (Int) -> String
    val tournamentFinished: String
    val tournamentKindTitle: String
    val tournamentName: String
    val tournamentAmericanoHint: String
    val tournamentMexicanoHint: String
    val tournamentPlayers: String
    val tournamentPlayersHint: String
    val tournamentAddPlayer: String
    val tournamentNeedPlayers: String
    val tournamentCourts: String
    val tournamentCourtsHint: (Int, Int) -> String
    val tournamentScoring: String
    val tournamentScoringPoints: String
    val tournamentScoringTime: String
    val tournamentMinutesN: (Int) -> String
    val tournamentPointsN: (Int) -> String
    val tournamentTimeHint: String
    val tournamentPointsHint: String
    val tournamentSuggested: (Int) -> String
    val tournamentCreate: String
    val tournamentShare: String
    val tournamentNextRound: String
    val tournamentRounds: String
    val tournamentStandings: String
    val tournamentResting: String
    val tournamentFinish: String
    val tournamentMissingResults: String
    val tournamentFinishText: String
    val tournamentDeleteTitle: String
    val tournamentDeleteText: String
    val tournamentCourt: (Int) -> String
    val tournamentScoreHint: (Int) -> String
    val tournamentCount: String
    val tournamentStartTimer: String
    val tournamentTimeUp: String
    val tournamentMatchOver: String
    val tournamentSave: String
    val tournamentPointsShort: String
    val tournamentPlayedShort: String
    val tournamentWonShort: String
    val tournamentStandingsHint: String
    val tournamentRoundsPlayed: (Int) -> String
    /** "Nuova partita" o "Esci" dal riepilogo senza averlo salvato né condiviso. */
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
    val shareSubjectPadel: String
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
    /** Titolo della pagina (scheda del browser, trasmissione dello schermo). */
    val tvPageTitle: String

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

    // In fondo ad Altro: versione, licenza (GPL) e link al codice sorgente

    // Racchetta virtuale
    val racketTitle: String
    val racketHint: String
    val racketMine: String
    val racketCompare: String
    val racketAdd: String
    val racketNew: String
    val racketEdit: String
    val racketNone: String
    val racketEmpty: String
    val racketField: String
    val racketSetupHint: String
    val racketCatalog: String
    val racketCatalogHint: String
    val racketName: String
    val racketBrand: String
    val racketModel: String
    val racketWeight: String
    val racketBalance: String
    val racketHead: String
    val racketPattern: String
    val racketOwner: String
    val racketCheckHours: String
    val racketRetired: String
    val racketRetiredHint: String
    val racketDeleteTitle: String
    val racketDeleteText: String
    val stringTitle: String
    val stringNew: String
    val stringHistory: String
    val stringNone: String
    val stringType: String
    val stringTypeName: (Int) -> String
    val stringCatalog: String
    val stringCross: String
    val stringGauge: String
    val stringMains: String
    val stringCrosses: String
    val stringDate: String
    val stringNotes: String
    val stringHours: (String, Int) -> String
    val stringCheck: String
    val stringCheckNote: String
    val stringDeleteTitle: String
    val racketHours: String
    val racketSessions: (Int) -> String
    val compareByRacket: String
    val compareByString: String
    val compareA: String
    val compareB: String
    val compareTooFew: String
    val compareNote: String
    val compareSessions: String
    val comparePerMatch: (String) -> String
    val compareStrokes: String
    val compareRated: (Int) -> String
    val feelTitle: String
    val feelHint: String
    val feelControl: String
    val feelComfort: String
    val feelPower: String
    val feelFatigue: String
    val racketUsed: String

    val aboutLicense: (String) -> String
    val aboutSource: String
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
    override val sportSection = "Sport"
    override val sportTennis = "Tennis"
    override val sportPadel = "Padel"
    override val padelHint = "Stesse regole del tennis: doppio e golden point (punto d'oro) sono già impostati, ma si possono cambiare."
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
    override val modeRemotes = "Telecomandi"
    override val modeRemotesHint = "Ogni giocatore assegna il punto con un telecomandino Bluetooth da selfie (1–3 €): un clic = punto, doppio clic o pressione lunga = annulla. Se il telecomando ha due tasti, l'altro annulla con un clic."
    override val remotesHowTo = "1. Accoppia i telecomandi nelle impostazioni Bluetooth del telefono, come una tastiera. 2. Tocca «Associa» sotto il giocatore e premi sul suo telecomando il tasto per il punto: un eventuale secondo tasto annullerà."
    override val bluetoothSettings = "Impostazioni Bluetooth"
    override val remoteFor: (String) -> String = { "Telecomando di $it" }
    override val noRemote = "Nessuno"
    override val remoteConnected = "Collegato"
    override val remoteNotConnected = "Non collegato: premi un tasto per svegliarlo"
    override val assignRemote = "Associa"
    override val removeRemote = "Togli"
    override val pressRemoteKey = "Premi un tasto del telecomando…"
    override val remotePressed = "Tasto ricevuto"
    override val remotesScreenOn = "Durante la partita tieni l'app aperta: lo schermo resta acceso da solo. A schermo bloccato o con un'altra app davanti i tasti non arrivano."
    override val requirements = "Requisiti"
    override val bluetooth = "Bluetooth"
    override val location = "Permesso posizione"
    override val locationServices = "Posizione attiva"
    override val enable = "Attiva"
    override val allow = "Consenti"
    override val ok = "OK"
    // Fasce cardio
    override val heartSection = "Battito cardiaco"
    override val heartRate = "Fasce cardio"
    override val heartRateHint = "Battito e calorie da fasce toraciche Bluetooth (Coospo, Magene, Polar, Garmin…), con qualsiasi modalità."
    override val heartHowTo = "Bagna gli elettrodi, indossa la fascia e sceglila qui sotto: la fascia di ogni giocatore va scelta una volta sola."
    override val strapFor: (String) -> String = { "Fascia di $it" }
    override val noStrap = "Nessuna"
    override val strapSearching = "Ricerca delle fasce…"
    override val strapConnected = "Collegata"
    override val strapConnecting = "Collegamento…"
    override val strapWaiting = "In attesa: indossala e bagna gli elettrodi"
    override val strapNoContact = "Non tocca la pelle"
    override val strapRelayed = "Dal secondo telefono"
    override val strapNoDrops = "nessuno stacco"
    override val strapDrops: (Int, Int) -> String = { n, s -> "$n ${if (n == 1) "stacco" else "stacchi"} ($s s)" }
    override val relayTitle = "Secondo telefono"
    override val relayHint = "Con un telefono per fondo campo fasce e braccialetti hanno sempre un telefono vicino: il secondo li sente e fa da ponte con questo, che tiene la partita, e può mostrare il tabellone. Questo telefono crea una rete sua, senza SIM né dati, e l'altro ci entra da solo inquadrando il codice."
    override val relayMain = "Usa un secondo telefono"
    override val relayJoin = "Fai da secondo telefono"
    override val relayShowCode = "Mostra il codice"
    override val relayShowQr = "Sull'altro telefono, in pagina 2 › Secondo telefono, tocca Fai da secondo telefono e inquadra questo codice."
    override val relayScanPrompt = "Inquadra il codice del telefono principale"
    override val relayNoSatellite = "Nessun secondo telefono collegato"
    override val relaySatellites: (String) -> String = { "Secondo telefono collegato: $it" }
    override val relayStop = "Smetti"
    override val relaySatelliteOn = "Questo telefono fa da secondo telefono: segue le fasce scelte sul principale e gli gira il battito. Lascialo a fondo campo con l'app aperta."
    override val relayConnected = "Collegato al telefono principale"
    override val relayDisconnected = "Telefono principale non raggiungibile: riprovo…"
    override val relayPending: (Int) -> String = { "$it misure da inviare" }
    override val relayPreparing = "Preparazione della rete…"
    override val relayShowBoard = "Mostra il tabellone"
    override val workoutTitle = "Allenamento"
    override val workoutHint = "Battito con la fascia cardio, senza partita: giri, HRV e recupero."
    override val workoutPickStrap = "Scegli la fascia qui sotto: bagna gli elettrodi e indossala."
    override val workoutStart = "Avvia"
    override val workoutPause = "Pausa"
    override val workoutResume = "Riprendi"
    override val workoutLap = "Giro"
    override val workoutEnd = "Fine"
    override val workoutRecovering: (Int) -> String = { "Recupero: ancora $it s" }
    override val workoutRecoveringHint = "Resta fermo: si misura quanto scende il battito nel primo minuto."
    override val workoutSaveNow = "Salva subito"
    override val workoutHistory = "Allenamenti salvati"
    override val workoutNone = "Nessun allenamento salvato."
    override val workoutDuration = "Durata"
    override val workoutHrv = "HRV (RMSSD)"
    override val workoutHrvHint = "Variabilità tra un battito e l'altro, misurata durante l'allenamento: confrontala con i tuoi allenamenti simili, non con quelli degli altri."
    override val workoutRecovery = "Recupero in 1 minuto"
    override val workoutRecoveryHint = "Recupero: quanto scende il battito nel minuto dopo la fine (più scende, meglio è)."
    override val workoutLaps = "Giri"
    override val workoutAvgShort = "media"
    override val workoutMaxShort = "max"
    override val workoutRecShort = "recupero"
    override val workoutShareCsv = "CSV"
    override val workoutSharePdf = "PDF"
    override val workoutDeleteTitle = "Eliminare l'allenamento?"
    override val workoutDeleteText = "Verrà tolto da questo telefono."
    override val workoutChart = "Battito durante l'allenamento"
    override val progressTitle = "Progressi"
    override val progressFilters = "Filtri"
    override val progressPeriod = "Periodo"
    override val progressPeriodAll = "Tutto"
    override val progress30 = "30 giorni"
    override val progress90 = "3 mesi"
    override val progress365 = "1 anno"
    override val progressWeekdays = "Giorni della settimana"
    override val progressOpponent = "Avversario"
    override val progressAnyOption = "Tutti"
    override val progressChoose = "Da confrontare"
    override val progressSelected: (Int, Int) -> String = { a, b -> "$a scelti su $b" }
    override val progressSelectAll = "Tutti"
    override val progressSelectNone = "Nessuno"
    override val progressNoneMatch = "Niente con questi filtri."
    override val progressHint = "Confronta allenamenti e partite nel tempo per vedere se migliori: documento da condividere o stampare."
    override val progressWorkouts = "Allenamenti"
    override val progressMatches = "Partite"
    override val progressPlayer = "Giocatore"
    override val progressNoPlayers = "Nessuna partita finita nello storico."
    override val progressCount: (Int) -> String = { n -> if (n == 1) "1 voce" else "$n voci" }
    override val progressTooFew = "Servono almeno 2 allenamenti o partite per confrontare."
    override val progressFirstHalf = "Prima metà"
    override val progressSecondHalf = "Seconda metà"
    override val progressTrend = "Tendenza"
    override val progressBetter = "migliora"
    override val progressWorse = "peggiora"
    override val progressSame = "stabile"
    override val progressList = "Elenco"
    override val progressDate = "Data"
    override val progressResult = "Esito"
    override val progressWon = "Vinta"
    override val progressLost = "Persa"
    override val progressPointsShort = "Punti"
    override val progressDfShort = "DF"
    override val progressWinnersShort = "Vinc."
    override val progressUeShort = "EG"
    override val progressWins = "Partite vinte"
    override val progressPointsWon = "Punti vinti"
    override val progressPdf = "PDF"
    override val progressPrint = "Stampa"
    override val relayWifiOff = "Il Wi-Fi di questo telefono è spento: accendilo (senza collegarti a nessuna rete) per entrare nella rete del principale."
    override val relayWifiOn = "Attiva il Wi-Fi"
    override val relayJoinCamera = "Fai da telecamera"
    override val cameraRoleOn = "Questo telefono fa da telecamera: riceve il punteggio dall'arbitro e lo mette sul video."
    override val cameraOpen = "Apri la telecamera"
    override val cameraLiveFailed: (String) -> String = { "Diretta non riuscita: $it" }
    override val cameraNotSupported = "Questo telefono non riesce a registrare in 1080p."
    override val cameraSaved: (Int) -> String = { n -> "Video salvato in Film/Tennis Score Manager, con $n punti segnati." }
    override val cameraSaveFailed: (String) -> String = { "Video salvato solo in $it" }
    override val cameraPermissions = "Servono i permessi di fotocamera e microfono."
    override val cameraConnecting = "Collegamento…"
    override val cameraNoReferee = "Senza arbitro: solo video"
    override val cameraWaitingScore = "In attesa del punteggio"
    override val cameraScoreOk = "Punteggio collegato"
    override val cameraRec = "Registra"
    override val cameraStopRec = "Ferma"
    override val cameraGoLive = "Diretta"
    override val cameraStopLive = "Ferma diretta"
    override val cameraOverlay = "Grafica"
    override val cameraStreamSettings = "Diretta: server"
    override val cameraClose = "Chiudi"
    override val cameraStopFirst = "Prima ferma registrazione e diretta."
    override val camRemoteTitle = "Telecamera"
    override val camRemoteOpen = "Inquadratura e comandi della telecamera"
    override val camRemoteHint = "Quello che vede il telefono-telecamera, come finisce nel video. I comandi valgono come sul suo schermo."
    override val camRemoteWaiting = "In attesa dell'inquadratura…"
    override val camRemoteGone = "Telecamera non collegata"
    override val camRemoteZoom = "Zoom"
    override val camRemoteBattery: (Int) -> String = { "Batteria $it%" }
    override val camRemoteFree: (String) -> String = { "Libero $it" }
    override val cameraNoStreamKey = "Manca la chiave della diretta: mettila sul telefono-telecamera (Impostazioni)."
    override val assistTitle = "Assistente inquadratura"
    override val assistButton = "Inquadra"
    override val assistHint = "Dove hai messo il telefono: l'app calcola l'inclinazione che tiene nel video tutte e due le linee di fondo e il giocatore lontano, e dice quale obiettivo usare."
    override val assistDistance = "Distanza dietro la linea di fondo (m)"
    override val assistHeight = "Altezza dell'obiettivo da terra (m)"
    override val assistCourt = "Campo"
    override val courtTennisSingles = "Tennis singolare"
    override val courtTennisDoubles = "Tennis doppio"
    override val courtPadel = "Padel"
    override val assistTiltIs: (String) -> String = { "Inclinazione consigliata: $it verso il basso" }
    override val assistFitsWith: (String) -> String = { "Ci sta tutto il campo con l'obiettivo $it." }
    override val assistCornersOut: (String) -> String = { "Gli angoli vicini del campo restano fuori anche con l'obiettivo $it: allontana il telefono dalla linea di fondo." }
    override val assistMinDistance: (String, String) -> String = { a, b -> "Con l'obiettivo $a servono almeno $b dietro la linea di fondo." }
    override val assistTooTall = "In altezza non ci sta tutto: allontana il telefono o alzalo."
    override val assistUseLens: (String) -> String = { "Usa l'obiettivo $it" }
    override val assistShowLevel = "Mostra la livella"
    override val assistHideLevel = "Nascondi la livella"
    override val assistPitch = "Inclinazione"
    override val assistLevel = "Livella"
    override val assistAimLower: (String) -> String = { "punta più in basso di $it" }
    override val assistAimHigher: (String) -> String = { "punta più in alto di $it" }
    override val assistTurnCw: (String) -> String = { "gira in senso orario di $it" }
    override val assistTurnCcw: (String) -> String = { "gira in senso antiorario di $it" }
    override val assistOk = "a posto"
    override val assistNoSensor = "Questo telefono non ha il sensore di gravità: la livella non c'è."
    override val assistCenter = "Destra e sinistra i sensori non le sanno: guarda sull'anteprima che la rete sia al centro."
    override val lensMain = "principale"
    override val lensWide = "grandangolo"
    override val cameraLens = "Obiettivo"
    override val cameraLensBusy = "Ferma registrazione e diretta per cambiare obiettivo."
    override val outlineButton = "Sagoma"
    override val outlineTitle = "Sagoma del campo"
    override val outlineShow = "Mostra la sagoma del campo"
    override val outlineHide = "Nascondi la sagoma"
    override val outlineHint = "Sposta i cursori finché le linee disegnate coincidono con quelle vere: allora distanza, altezza e direzione sono quelle giuste e l'inclinazione consigliata diventa precisa. La taratura viene ricordata per questo campo."
    override val calibDistance = "Distanza"
    override val calibHeight = "Altezza"
    override val calibPan = "Direzione"
    override val calibLateral = "Spostamento laterale"
    override val calibFov = "Campo visivo"
    override val calibPitch = "Correzione inclinazione"
    override val outlineLoaded: (String) -> String = { "Taratura di $it ripresa" }
    override val outlineReset = "Azzera le correzioni"
    override val outlineDone = "Fine"
    override val cameraStreamHint = "Per YouTube: YouTube Studio › Trasmetti dal vivo › copia la chiave dello stream. Per altri servizi (Twitch, Facebook, un server tuo) cambia anche il server (rtmp:// o rtmps://)."
    override val cameraStreamUrl = "Server RTMP"
    override val cameraStreamKey = "Chiave dello stream"
    override val videoTitle = "Video"
    override val videoHint = "Le partite registrate dal telefono-telecamera, da rivedere punto per punto."
    override val videoNone = "Nessun video: registra una partita col telefono-telecamera (pagina 2 › Secondo telefono › Fai da telecamera)."
    override val videoPoints: (Int) -> String = { n -> if (n == 1) "1 punto" else "$n punti" }
    override val videoDeleteTitle = "Eliminare il video?"
    override val videoDeleteText = "Il video viene tolto anche dalla galleria, insieme ai suoi capitoli."
    override val videoYoutubeChapters = "Capitoli YouTube"
    override val videoNoPoints = "Questo video non ha punti segnati: era registrato senza il punteggio dell'arbitro."
    override val videoPoint = "Punto"
    override val videoGame = "Game"
    override val videoSet = "Set"
    override val videoReplay = "Rivedi il punto"
    override val videoStart = "Inizio"
    override val videoKind: (String) -> String = { k -> when (k) { "ACE" -> "Ace"; "DOUBLE_FAULT" -> "Doppio fallo"; "WINNER" -> "Vincente"; "UNFORCED_ERROR" -> "Errore gratuito"; "FORCED_ERROR" -> "Errore forzato"; "ERROR" -> "Errore"; else -> k } }
    override val videoImport = "Importa video"
    override val videoImportHint = "Un video dal telefono (anche fatto con un'altra fotocamera): i punti si segnano guardandolo. Il file resta dov'è."
    override val videoTagHowTo = "Guarda il video e, a fine punto, tocca chi l'ha vinto; poi, se vuoi, com'è finito. Il punteggio si calcola da solo."
    override val videoTagTooEarly = "Porta il video dopo l'ultimo punto segnato."
    override val videoPointFor: (String) -> String = { "Punto $it" }
    override val videoUndoLast = "Annulla ultimo"
    override val videoTagSetupTitle = "Chi gioca?"
    override val videoFirstServer = "Chi serve per primo"
    override val videoTagSetupHint = "Formato e regole sono quelli di pagina 2."
    override val hlTitle = "Highlights"
    override val hlBig = "Palle break, set e match point"
    override val hlRallies = "Scambi lunghi (dai braccialetti)"
    override val hlMax = "Durata massima"
    override val hlCustom = "Altra durata (minuti)"
    override val hlMusic = "Musica delle statistiche"
    override val hlMusicPick = "Scegli un brano"
    override val hlMusicReset = "Usa quella dell'app"
    override val hlMusicHint = "Un brano dal telefono: se ne usa l'inizio, con dissolvenza. Usa solo musica che hai il diritto di mettere nei tuoi video."
    override val hlMusicCredit: (String) -> String = { "Se pubblichi il video, nei crediti scrivi: $it" }
    override val eventSection = "Torneo"
    override val eventName = "Nome del torneo"
    override val eventCategory = "Categoria o tabellone (es. Open, Lim. 4.1, Over 45)"
    override val eventRound = "Turno (es. Quarti, Finale)"
    override val eventHint = "Facoltativi: compaiono nel riepilogo, nel PDF e nelle statistiche degli highlights."
    override val playerRank = "Classifica (es. 4.1, NC)"
    override val hlStats = "Statistiche a fine video"
    override val hlStatsHint: (Int, Int) -> String = { n, sec -> "$n schermate con le statistiche della partita ($sec s) dopo i punti." }
    override val hlStatsNone = "Statistiche non disponibili: la partita del video non è nello storico."
    override val cardTitle: (String) -> String = { "Statistiche ${it.lowercase()}" }
    override val cardFinal = "Risultato finale"
    override val cardPartial = "Parziale"
    override val cardBreakPoints = "Palle break"
    override val cardRallies = "Scambi"
    override val cardShortRallies = "Punti vinti fino a 8 colpi"
    override val cardLongRallies = "Punti vinti da 9 colpi in su"
    override val hlNone = "Nessun punto adatto: segna ace, vincenti o gioca con i braccialetti."
    override val hlPreview: (Int, String) -> String = { n, t -> "$n punti · $t" }
    override val hlHint = "Se supera la durata, prima si accorciano i pezzi, poi restano fuori i punti meno importanti. Il video va in galleria."
    override val hlCreate = "Crea"
    override val hlMaking: (Int) -> String = { "Montaggio… $it%" }
    override val hlDone = "Highlights pronti, salvati in Film/Tennis Score Manager."
    override val hlFailed = "Montaggio non riuscito."
    override val hlOrderHint = "In alto le voci con la precedenza: se il video supera la durata, restano fuori prima quelle in fondo."
    override val hlUp = "Sposta su"
    override val hlDown = "Sposta giù"
    override val coachTitle = "Filtri allenatore"
    override val coachPlayer = "Giocatore"
    override val coachAll = "Tutti"
    override val coachPoint = "Punti"
    override val coachWon = "Vinti"
    override val coachLost = "Persi"
    override val coachServe = "Servizio"
    override val coachServing = "Al servizio"
    override val coachReturning = "In risposta"
    override val coachServeKind = "Battuta"
    override val coachFirst = "Prima"
    override val coachSecond = "Seconda"
    override val coachEnd = "Com'è finito"
    override val coachSituation = "Situazione"
    override val coachBreak = "Palle break"
    override val coachSetMatch = "Set e match point"
    override val coachApply = "Applica"
    override val coachClear = "Togli il filtro"
    override val coachCount: (Int) -> String = { n -> if (n == 1) "1 punto" else "$n punti" }
    override val coachSummary: (Int, Int) -> String = { n, w -> "$n punti · $w vinti (${if (n > 0) w * 100 / n else 0}%)" }
    override val coachPlay = "Riproduci"
    override val coachNone = "Nessun punto con questo filtro"
    override val replayTitle = "Moviola"
    override val replayHint = "Dal telefono-telecamera: il punto contestato dall'inizio, anche se è passato un po' di tempo."
    override val replayLastPoint = "Ultimo punto"
    override val replayPrevPoint = "Punto prima"
    override val replaySinceLast = "Dall'ultimo punto segnato"
    override val replayWaiting = "In arrivo dalla telecamera…"
    override val replayFailed = "Moviola non arrivata: controlla che la telecamera sia aperta e collegata."
    override val replayClose = "Chiudi"
    override val replayOnBoard = "Tabellone"
    override val replayAlsoOnBoard = "Anche sul tabellone"
    override val replayNoCamera = "Nessuna telecamera collegata."
    override val cameraReplaySent = "Moviola mandata all'arbitro"
    override val cameraReplayNone = "Moviola non disponibile: niente video in memoria"
    override val relayOwnNetwork: (String) -> String = { "Rete creata da questo telefono: $it" }
    override val relayJoinWaiting = "Conferma sullo schermo il collegamento alla rete del telefono principale."
    override val relayJoinRefused = "Collegamento alla rete rifiutato: tocca Smetti e inquadra di nuovo il codice."
    override val relayJoinLost = "Rete del telefono principale persa: riprovo…"
    override val relayJoinByHand: (String, String) -> String = { n, p -> "Dalle impostazioni Wi-Fi collegati alla rete $n (password $p)." }
    override val heartAge = "Età"
    override val heartWeight = "Peso (kg)"
    override val heartMale = "Uomo"
    override val heartFemale = "Donna"
    override val heartProfileHint = "Età, peso e sesso servono solo a stimare le calorie."
    override val heartStat = "Battito"
    override val heartAvg = "Battito medio"
    override val heartMax = "Battito massimo"
    override val heartCalories = "Calorie (stima)"
    override val heartChart = "Battito durante la partita"
    override val heartLine: (Int, Int) -> String = { a, m -> "medio $a · max $m bpm" }
    override val caloriesLine: (Int) -> String = { "~$it kcal (stima)" }
    override val heartStatsHint = "Calorie stimate dal battito con la formula di Keytel: l'errore tipico è del 20%."
    override val heartZones = "Tempo nelle zone cardio"
    override val zoneName: (Int) -> String = { "Z${it + 1} " + listOf("Molto leggera", "Leggera", "Moderata", "Intensa", "Massima").getOrElse(it) { "" } }
    override val heartZonesHint = "Zone in percentuale del battito massimo stimato dall'età (208 − 0,7 × età): serve l'età di chi indossa la fascia."
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
    override val strokes = "Colpi"
    override val strokeName: (Int) -> String = { listOf("Dritto", "Rovescio", "Servizio", "Volée").getOrElse(it) { "?" } }
    override val strokeAbbr: (Int) -> String = { listOf("D", "R", "S", "V").getOrElse(it) { "?" } }
    override val strokeOther = "Non riconosciuti"
    override val strokeRecognition = "Riconoscimento colpi"
    override val strokeRecognitionHint = "Il braccialetto riconosce dritto, rovescio, servizio e volée di chi lo indossa. Il sensore di movimento acceso consuma un po' di più."
    override val strokeOnBand = "Mostra il colpo sul braccialetto"
    override val strokeOnBandHint = "Per un attimo, solo se il display non mostra altro."
    override val strokeCalibration = "Calibrazione"
    override val strokeCalibrationHint = "Indossa il braccialetto come in partita, tocca Calibra e fai 5-10 colpi di quel tipo, con una pausa tra uno e l'altro. Gli esempi restano nel braccialetto."
    override val calibrate = "Calibra"
    override val calibrateStop = "Fine"
    override val undoExample = "Annulla l'ultimo"
    override val clearExamples = "Cancella gli esempi"
    override val strokesReady = "Pronto: riconosce i colpi calibrati."
    override val strokesNotReady = "Servono almeno 3 esempi per 2 colpi."
    override val strokesFirmwareOld = "Per riconoscere i colpi carica il firmware TSM_Band 2.4."
    override val wristSpeed = "Velocità del polso"
    override val maxShort = "max"
    override val strokesPerPoint = "Colpi per punto"
    override val rallyAverage = "media"
    override val rallyLongest = "più lungo"
    override val rallyShort = "brevi"
    override val rallyMedium = "medi"
    override val rallyLong = "lunghi"
    override val tabSummary = "Riassunto"
    override val tabStats = "Statistiche"
    override val tabChronology = "Cronologia"
    override val periodMatch = "Partita"
    override val periodSet: (Int) -> String = { "Set $it" }
    override val statsHint = "Calcolate dai punti segnati nell'app e, coi braccialetti, dai colpi. Ace, doppi falli, prime di servizio ed errori non si possono sapere."
    override val statsHintDetailed = "Calcolate dai punti segnati nell'app, con ace, falli, vincenti ed errori delle statistiche dettagliate e, coi braccialetti, dai colpi. Un punto dato col tasto «Punto» (o da braccialetti e telecomandi) conta senza dettaglio."
    override val statServe = "Servizio"
    override val statReturn = "Risposta"
    override val statPoints = "Punti"
    override val statGames = "Game"
    override val statServicePoints = "Punti vinti al servizio"
    override val statServiceGames = "Game vinti al servizio"
    override val statBreakSaved = "Palle break salvate"
    override val statReturnPoints = "Punti vinti in risposta"
    override val statReturnGames = "Game vinti in risposta"
    override val statBreakWon = "Palle break vinte"
    override val statTotalPoints = "Totale punti vinti"
    override val statStreak = "Punti di fila (max)"
    override val statLast10 = "Ultimi 10 punti"
    override val statMatchPointsSaved = "Match point annullati"
    override val statTotalGames = "Totale game vinti"
    override val statGamePointsWon = "Palle game trasformate"
    override val statSetPointsWon = "Set point trasformati"
    override val statMatchPointsWon = "Match point trasformati"
    override val statLoveGames = "Game vinti a zero"
    override val statBestGameRun = "Game di fila (max)"
    override val statSetsFromBreakDown = "Set vinti da sotto di un break"
    override val statTimes = "Tempi"
    override val statSetDurations = "Durata dei set"
    override val statLongestGame = "Game più lungo"
    override val longestGameValue: (String, Int) -> String = { t, n -> "$t ($n punti)" }
    override val statAces = "Ace"
    override val statDoubleFaults = "Doppi falli"
    override val statFirstServeIn = "Prime di servizio in campo"
    override val statFirstServeWon = "Punti vinti con la prima"
    override val statSecondServeWon = "Punti vinti con la seconda"
    override val statWinners = "Vincenti"
    override val statErrors = "Errori"
    override val statUnforcedErrors = "Errori gratuiti"
    override val statForcedErrors = "Errori forzati"
    override val detailAskOutcome = "Com'è finito il punto?"
    override val detailAskErrorType = "Errore gratuito o forzato?"
    override val detailUnforced = "Gratuito"
    override val detailForced = "Forzato"
    override val detailWinnerOf: (String) -> String = { x -> "Vincente $x" }
    override val detailErrorOf: (String) -> String = { x -> "Errore $x" }
    override val trendTitle = "Andamento"
    override val trendHint = "Punti vinti partita per partita (ultime 12; piena = partita vinta, linea = 50%)."
    override val trendPointsAvg: (Int) -> String = { n -> "Punti vinti in media: $n%" }
    override val trendDetailTitle: (Int) -> String = { n -> "Medie per partita ($n con statistiche dettagliate)" }
    override val detailedStats = "Statistiche dettagliate"
    override val detailedStatsHint = "Per chi segna da bordo campo: al posto dei due tasti grandi ci sono ace, fallo e, per ciascun giocatore, vincente, errore e punto. Servono per ace, doppi falli, prime di servizio, vincenti ed errori."
    override val btnAce = "Ace"
    override val btnFault = "Fallo"
    override val btnDoubleFault = "Doppio fallo"
    override val btnWinner = "Vincente"
    override val btnError = "Errore"
    override val btnPoint = "Punto"
    override val firstServe = "prima"
    override val secondServe = "seconda"
    override val serveOf: (String) -> String = { "servizio di $it" }
    override val statStrokesTotal = "Colpi totali"
    override val statAvgWristSpeed = "Velocità media del polso"
    override val serveLost = "Servizio perso"
    override val markerBreak = "PB"
    override val markerSet = "SP"
    override val markerMatch = "MP"
    override val chronoGame = "GAME"
    override val chronoBreak = "BREAK"
    override val chronoSet = "SET"
    override val chronoMatch = "PARTITA"
    override val chronoTiebreak = "Tie-break"
    override val powerOff = "Spegni"
    override val bandPowerOffTitle = "Spegnere il braccialetto?"
    override val bandPowerOffText: (String) -> String = { "$it si spegne subito. Per riaccenderlo: un clic sul tasto laterale." }
    override val languageSection = "Lingua"
    override val languageHint = "Vale per le schermate, la voce dell'arbitro, il tabellone TV e i braccialetti."
    override val audioSection = "Audio e voce"
    override val voiceCalls = "Chiamate vocali dell'arbitro"
    override val voiceFiles: (Int, Int) -> String = { n, tot -> "File generati: $n/$tot" }
    override val voiceFilesHint = "Tutto funziona senza internet con le voci installate sul telefono. Le registrazioni personalizzate (ZIP) hanno sempre la precedenza sulla sintesi vocale."
    override val generateVoice = "Genera file"
    override val generating: (Int, Int) -> String = { n, tot -> "Generazione $n/$tot…" }
    override val voiceGenerationFailed = "Generazione non completata: i file vocali di prima restano com'erano."
    override val importVoiceZip = "Importa ZIP"
    override val voiceImportFailed = "ZIP non leggibile o incompleto: nessuna registrazione è cambiata."
    override val deleteCustomVoice = "Rimuovi registrazioni"
    override val deleteCustomConfirmTitle = "Rimuovere le registrazioni?"
    override val deleteCustomConfirmText: (Int) -> String = { n -> "Le registrazioni personalizzate in italiano ($n) vengono cancellate dal telefono. Per riaverle bisogna importare di nuovo lo ZIP." }
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
    override val voiceReadme: (String, String) -> String = { langs, formats ->
        "TENNIS SCORE MANAGER - FILE VOCALI\n" +
            "Metti le registrazioni in voice/<lingua>/ ($langs) con il nome della chiave.\n" +
            "Formati: $formats. I nomi dei giocatori sono sempre letti dal TTS.\n" +
            "La cartella tts/ contiene i file generati dall'app: le tue registrazioni hanno la precedenza."
    }
    override val voiceReadmeKey = "CHIAVE"
    override val testVoice = "Prova voce"
    override val stopVoiceTest = "Ferma la prova"
    override val ttsMissing = "Voce italiana della sintesi vocale non installata sul telefono."
    override val installVoice = "Installa voce"
    override val ttsEngineError = "Sintesi vocale non disponibile: controlla il motore in Impostazioni di Android."
    override val openTtsSettings = "Apri impostazioni"
    override val formatSection = "Formato partita"
    override val formatBestOfThree = "3 set · tie-break a 7"
    override val formatBestOfThreeHint = "Al meglio dei tre set, tie-break sul 6-6 in ogni set."
    override val formatMatchTiebreak = "2 set + super tie-break a 10"
    override val formatMatchTiebreakHint = "Sull'1-1 il terzo set è un match tie-break a 10 punti (2 di scarto)."
    override val formatBestOfFive = "5 set · tie-break a 7"
    override val formatBestOfFiveHint = "Al meglio dei cinque set, tie-break sul 6-6 in ogni set."
    override val formatProSet = "Pro set"
    override val formatProSetHint = "Un solo set a 8 o 10 game con 2 di scarto, tie-break sull'8-8 o sul 10-10."
    override val formatMatchTiebreakOnly = "Solo super tie-break a 10"
    override val formatMatchTiebreakOnlyHint = "La partita è un unico match tie-break a 10 punti (2 di scarto)."
    override val gamesPerSetTitle = "Game per set"
    override val gamesShort = "4 game (set brevi)"
    override val gamesShortHint = "Set brevi: vince il set chi arriva a 4 game con 2 di scarto; tie-break sul 4-4."
    override val setTiebreak = "Tie-break nei set"
    override val labelShortSets = "set a 4 game"
    override val labelTiebreak7 = "tie-break a 7"
    override val labelNoTiebreak = "senza tie-break"
    override val gamesN: (Int) -> String = { "$it game" }
    override val setTiebreakHint: (Int) -> String = { "Tie-break a 7 quando i game sono $it pari. Spento, il set continua finché uno non ha 2 game di scarto." }
    override val labelSets: (Int) -> String = { "$it set" }
    override val labelProSet: (Int) -> String = { "pro set a $it" }
    override val noAd = "No-Ad (punto decisivo)"
    override val noAdHint = "Sul 40-40 si gioca un solo punto: chi lo vince vince il game."
    override val goldenPoint = "Golden point"
    override val goldenPointHint = "Punto d'oro: sul 40-40 si gioca un solo punto e chi lo vince vince il game. Toglilo per giocare ai vantaggi."
    override val starPoint = "Star point"
    override val starPointHint = "Regola FIP dal 2026: sul 40-40 si giocano al massimo due vantaggi; alla terza parità un solo punto decide il game. Spenti golden point e star point si gioca ai vantaggi."
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
    override val addressLookup = "Cerca l'indirizzo del campo"
    override val addressLookupHint = "Nel riepilogo, accanto alle coordinate. Usa il servizio di geocodifica del telefono: con i servizi Google le coordinate vengono inviate a Google."
    override val bandsRequiredTitle = "Bluetooth e posizione obbligatori"
    override val bandsRequiredText = "Per usare i braccialetti devi attivare il Bluetooth, concedere il permesso di posizione e tenere attiva la posizione."
    override val bandsMissingTitle = "Braccialetti non associati"
    override val bandsMissingText: (List<String>) -> String = {
        (if (it.size > 1) "Mancano i braccialetti per: " else "Manca il braccialetto per: ") + it.joinToString(", ") + ". Continuare lo stesso?"
    }
    override val remotesMissingTitle = "Telecomandi non associati"
    override val remotesMissingText: (List<String>) -> String = {
        (if (it.size > 1) "Mancano i telecomandi per: " else "Manca il telecomando per: ") + it.joinToString(", ") + ". Continuare lo stesso?"
    }
    override val continueAnyway = "Continua"
    override val cancel = "Annulla"

    override val startMatch = "INIZIO PARTITA"
    override val startHint = "Premi il pulsante per iniziare"
    override val startHintBands = "Premi il pulsante oppure KEY1 su un braccialetto"
    override val startHintRemotes = "Premi il pulsante oppure un telecomando"
    override val startButton = "Inizia partita"
    override val resumeSaved = "Riprendi partita sospesa"
    override val noSavedMatches = "Nessuna partita sospesa salvata."
    override val savedMatchesTitle = "Partite sospese"
    override val delete = "Elimina"
    override val deleteSavedTitle = "Eliminare la partita sospesa?"
    override val deleteSavedText: (String) -> String = { "La partita $it non si potrà più riprendere." }
    override val vs = "vs"

    override val matchTime = "Match Time"
    override val setsHeader = "SET"
    override val gamesHeader = "GAME"
    override val undoPoint = "Annulla punto"
    override val lockScreen = "Blocca schermo"
    override val lockedTitle = "Schermo bloccato"
    override val unlockHint = "Tieni premuto il lucchetto per sbloccare. Braccialetti e telecomandi continuano a funzionare."
    override val suspend = "Sospendi"
    override val resume = "Riprendi"
    override val audioOn = "Audio On"
    override val audioOff = "Audio Off"
    override val newMatch = "Nuova partita"
    override val suspendedOverlay = "PARTITA SOSPESA"
    override val newMatchConfirmTitle = "Nuova partita?"
    override val newMatchConfirmText = "La partita in corso resta salvata tra le partite sospese e potrai riprenderla."
    override val endDialogTitle = "Gioco, set, partita"
    override val endDialogText: (String, String, Boolean) -> String = { name, score, team -> "${if (team) "Vincono" else "Vince"} $name\n$score" }
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
    override val msgSecondServe = "SECONDA DI SERVIZIO"
    override val msgFaultUndone = "FALLO ANNULLATO"
    override val msgSuspended = "PARTITA SOSPESA"
    override val msgResumed = "PARTITA RIPRESA"
    override val msgBandConnected: (String) -> String = { "BRACCIALETTO $it CONNESSO" }
    override val msgBandLost: (String) -> String = { "BRACCIALETTO $it DISCONNESSO" }
    override val msgRemoteConnected: (String) -> String = { "TELECOMANDO $it COLLEGATO" }
    override val msgRemoteLost: (String) -> String = { "TELECOMANDO $it SCOLLEGATO" }
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
    override val notifTvOnly = "Tabellone TV attivo"

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
    override val saveHistory = "Salva file"
    override val historyTitle = "Storico"
    override val historySubtitle = "Partite finite salvate su questo telefono"
    override val historyImport = "Importa"
    override val historyMatches = "Partite"
    override val historyPlayers = "Giocatori"
    override val historyEmpty = "Ancora nessuna partita finita. Ogni partita conclusa entra qui da sola; con «Importa» aggiungi quelle salvate come JSON su un altro telefono."
    override val historyPlayersEmpty = "I giocatori compaiono qui quando finisci una partita coi loro nomi scritti in Nuova partita."
    override val historyRecord: (Int, Int) -> String = { w, l -> "Vinte $w · perse $l" }
    override val historyHeadToHead = "Scontri diretti"
    override val historyDeleteTitle = "Eliminare la partita?"
    override val historyDeleteText = "La partita sparisce dallo storico di questo telefono. I file già salvati o condivisi restano."
    override val historyImported: (Int, Int) -> String = { d, o -> "Importate: $d nello storico, $o tra le partite sospese" }
    override val historyImportFailed = "Nessuna partita importata: scegli i file JSON salvati dall'app."
    override val transferTitle = "Trasferisci"
    override val transferHint = "Sposta storico e partite sospese su un altro telefono con un codice QR. I due telefoni devono essere sulla stessa rete Wi-Fi, oppure uno collegato all'hotspot dell'altro. Su quello nuovo le partite si aggiungono a quelle che ha già."
    override val transferSend = "Invia"
    override val transferReceive = "Ricevi"
    override val transferShowQr = "Sull'altro telefono apri Storico › Trasferisci › Ricevi e inquadra questo codice. Lascia aperta questa finestra finché non ha finito."
    override val transferSent = "Inviato all'altro telefono. Puoi chiudere."
    override val transferClose = "Chiudi"
    override val transferScanPrompt = "Inquadra il codice dell'altro telefono"
    override val transferNoNetwork = "Questo telefono non è su una rete Wi-Fi o un hotspot: collega i due telefoni alla stessa rete e riprova."
    override val transferBadCode = "Questo non è un codice di Tennis Score Manager."
    override val transferFailed = "Trasferimento non riuscito: i due telefoni sono sulla stessa rete? Sull'altro la finestra col codice deve restare aperta."
    override val transferEmpty = "L'altro telefono non ha partite da inviare."
    override val photoHint = "La foto resta su questo telefono e si ritrova dal nome del giocatore; passa a un altro telefono col trasferimento dello storico."
    override val photoChange = "Cambia foto"
    override val photoRemove = "Togli foto"
    override val photoNeedsName = "Scrivi prima il nome del giocatore: la foto si ritrova dal nome."
    override val photoError = "Non riesco a leggere questa foto."
    override val matchPhotosTitle = "Foto della partita"
    override val matchPhotosHint = "Fino a 10 foto ricordo: restano su questo telefono, finiscono nel PDF e passano all'altro telefono col trasferimento."
    override val addPhotos = "Aggiungi foto"
    override val takePhoto = "Scatta"
    override val matchPhotosFull = "Ci sono già 10 foto per questa partita."
    override val matchNotesTitle = "Note"
    override val ratingTitle = "Rating TSM"
    override val ratingHint = "Calcolato sul telefono dalle partite finite dello storico: sale quando vinci, di più contro chi ha un rating più alto e con un punteggio netto. Nel doppio conta la media della coppia. Le amichevoli non contano."
    override val ratingProvisional: (Int) -> String = { "provvisorio: ancora $it partite" }
    override val ratingMatches: (Int, Int) -> String = { a, b -> "$a partite, $b vinte" }
    override val ratingLast: (String) -> String = { "Ultima partita: $it" }
    override val ratingRanking = "Classifica TSM"
    override val ratingNone = "Ancora nessuna partita che conti per il rating."
    override val ratingFriendly = "Amichevole"
    override val ratingFriendlyHint = "Non conta per il rating TSM"
    override val ratingInMatch = "Rating TSM dopo questa partita"
    override val ratingNotCounted = "Questa partita non conta per il rating TSM."
    override val ratingShowAll = "Mostra tutti"
    override val ratingShowLess = "Mostra meno"
    override val rematchTitle = "Rivincita"
    override val rematchHint = "Proponi un'altra partita: l'invito pronto da mandare su WhatsApp o dove preferisci, la data in calendario e la stessa partita pronta in Nuova partita."
    override val rematchAsk = "Rivincita?"
    override val rematchVersus = "contro"
    override val rematchLast: (String, String) -> String = { a, b -> "Ultima volta: $a per $b" }
    override val rematchWhen = "Quando"
    override val rematchPlace = "Dove"
    override val rematchNote = "Nota (facoltativa)"
    override val rematchNoteHint = "es. campo già prenotato, porto io le palline"
    override val rematchSend = "Manda l'invito"
    override val rematchCalendar = "Metti in calendario"
    override val rematchPrepare = "Prepara la partita"
    override val rematchEvent: (String) -> String = { "Rivincita: $it" }
    override val rematchNoCalendar = "Nessuna app di calendario su questo telefono."
    override val rematchPreview = "Il messaggio"
    override val serveTitle = "Allenamento al servizio"
    override val serveHint = "Telefono sul cavalletto a fianco di chi serve, braccialetto al polso: KEY1 dentro, KEY2 fuori, KEY1 tenuto premuto annulla l'ultimo. Ogni servizio diventa una clip da rivedere."
    override val serveStart = "Inizia"
    override val serveNoBand = "Nessun braccialetto collegato: segna dentro e fuori coi tasti sullo schermo."
    override val serveBandOk: (String) -> String = { "Braccialetto $it: KEY1 dentro, KEY2 fuori, KEY1 tenuto premuto annulla" }
    override val serveSideDeuce = "Destra"
    override val serveSideAd = "Sinistra"
    override val serveFirst = "Prima"
    override val serveSecond = "Seconda"
    override val serveTargetT = "Al T"
    override val serveTargetBody = "Al corpo"
    override val serveTargetWide = "Esterno"
    override val serveTargetNone = "Nessuno"
    override val serveIn = "Dentro"
    override val serveOut = "Fuori"
    override val serveUndo = "Annulla l'ultimo"
    override val serveSayIn: (Int, Int) -> String = { a, b -> "Dentro. $a su $b" }
    override val serveSayOut: (Int, Int) -> String = { a, b -> "Fuori. $a su $b dentro" }
    override val serveSayUndo = "Annullato"
    override val serveWaiting = "Servi: il braccialetto riconosce il servizio"
    override val serveDetected = "Servizio riconosciuto: dentro o fuori?"
    override val serveEnd = "Fine"
    override val serveSummary = "Riepilogo dell'allenamento"
    override val serveCountLine: (Int, Int) -> String = { a, b -> "$a su $b dentro" }
    override val serveBySide = "Per lato"
    override val serveByKind = "Prima e seconda"
    override val serveByTarget = "Per bersaglio"
    override val serveWrist: (Int, Int) -> String = { a, b -> "Polso: in media $a °/s sui servizi dentro, $b °/s su quelli fuori" }
    override val servePlayAll = "Rivedi tutti"
    override val servePlayIn = "Rivedi solo i dentro"
    override val servePlayOut = "Rivedi solo i fuori"
    override val serveSessions = "Sessioni"
    override val serveDelete = "Elimina"
    override val serveDeleteAsk = "Eliminare la sessione e le sue clip?"
    override val servePlaceTitle = "Dove mettere il telefono"
    override val servePlaceText = "• A fianco di chi serve, dal lato del braccio che colpisce (a destra per un destro), a 3-4 m di distanza, all'altezza della linea di fondo o mezzo metro dietro.\n• Obiettivo a 1-1,2 m da terra, telefono in orizzontale e dritto, puntato su chi serve.\n• Nell'inquadratura ci deve stare tutta la persona, dai piedi alla racchetta in alto (circa 2,7 m): se la testa della racchetta esce, allontana il telefono o usa il grandangolo.\n• Per vedere anche dove va la palla: 3-4 m dietro chi serve, un po' di lato, a 1,5-2 m d'altezza.\n• Mai davanti a chi serve né sulla traiettoria della palla."
    override val servePlaceShow = "Dove mettere il telefono?"
    override val serveNoClips = "Nessuna clip: senza telecamera si contano solo dentro e fuori."
    override val servePlaceServer = "chi serve"
    override val servePlacePhone = "telefono"
    override val servePlaceAlt = "in alternativa"
    override val poseTitle = "Analisi del movimento"
    override val poseHint = "L'app trova la posizione del corpo nelle clip, tutto sul telefono, e misura tre momenti: caricamento, ginocchia più piegate e impatto. Sono stime da un video di lato: valgono soprattutto per confrontare i tuoi servizi dentro con quelli fuori."
    override val poseAnalyzeAll = "Analizza i servizi"
    override val poseAnalyzing: (Int, Int) -> String = { a, b -> "Analisi del servizio $a di $b…" }
    override val poseNoBody = "Corpo non riconosciuto: inquadra tutta la persona, di lato, con buona luce."
    override val poseUnreliable = "Misure poco sicure: il corpo si vede male"
    override val poseTrophy = "Caricamento"
    override val poseKnee = "Ginocchia"
    override val poseContact = "Impatto"
    override val poseKneeMetric = "Ginocchio più piegato"
    override val poseElbowMetric = "Gomito all'impatto"
    override val poseHeightMetric = "Altezza dell'impatto"
    override val poseTossMetric = "Braccio del lancio (dalla verticale)"
    override val poseTiltMetric = "Spalle al caricamento"
    override val poseTimingMetric = "Dal caricamento all'impatto"
    override val poseHeightValue: (String) -> String = { "$it × la statura" }
    override val poseInVsOut = "Dentro e fuori a confronto"
    override val poseServe: (Int) -> String = { "Servizio $it" }
    override val poseNoClip = "Senza clip"
    override val poseDone = "Analisi finita"
    override val poseModelTitle = "Modello per riconoscere il corpo"
    override val poseModelHint = "Per l'analisi serve il modello MoveNet di Google (7 MB, licenza Apache 2.0). Si scarica una volta sola dalla pagina del progetto su GitHub e resta sul telefono; le clip non lasciano mai il telefono."
    override val poseModelDownload = "Scarica il modello (7 MB)"
    override val poseModelDownloading: (Int) -> String = { "Download del modello… $it%" }
    override val poseModelFailed = "Download non riuscito: controlla la connessione e riprova."
    override val poseModelBad = "Il file scaricato non è quello atteso e non è stato usato: riprova più tardi."
    override val poseModelDelete = "Elimina il modello (7 MB)"
    override val poseUnsupported = "L'analisi del movimento non è disponibile su questo dispositivo (processore x86)."
    override val poseWatch = "Guarda il video con lo scheletro"
    override val poseManyPeople: (Int) -> String = { "$it persone nell'inquadratura: seguito chi colpisce (controlla nelle immagini)." }
    override val poseZoomHint = "Tocca un'immagine per ingrandirla; due dita per lo zoom, doppio tocco per tornare normale."
    override val poseRedo = "Rifai l'analisi"
    override val strokeTrainingTitle = "Allenamento dei colpi"
    override val strokeFolderName = "Allenamento colpi"
    override val documentsFolder = "Documenti"
    override val strokeAllSessions: (Int) -> String = { n -> "Tutte le sessioni ($n)" }
    override val strokeSessionsTitle: (String) -> String = { "Sessioni · $it" }
    override val strokeSessionsWhere: (String) -> String = { "Video e analisi in $it" }
    override val strokeSpace: (String, Int) -> String = { mb, n -> "$n sessioni · video: $mb" }
    override val strokeSelectAll = "Seleziona tutte"
    override val strokeSelectHint = "Tieni premuta una sessione per selezionarla, poi cancella le sessioni o solo i loro video."
    override val strokeDeleteN: (Int) -> String = { n -> "Elimina ($n)" }
    override val strokeVideosN: (Int) -> String = { n -> "Solo i video ($n)" }
    override val strokeDeleteAsk: (Int) -> String = { n -> "Eliminare $n sessioni con i loro video? Non si può annullare." }
    override val strokeVideosAsk: (Int) -> String = { n -> "Eliminare i video di $n sessioni? Restano i conteggi, le misure dell'analisi e le immagini dei momenti chiave." }
    override val strokeNoVideos = "video eliminati"
    override val strokeAnalysed = "analizzata"
    override val strokeTrend = "Colpi dentro, sessione dopo sessione"
    override val strokeNone = "Ancora nessuna sessione di questo colpo."
    override val progressStrokes = "Allenamento dei colpi"
    override val progressStrokesHint = "Percentuale di colpi dentro, sessione dopo sessione."
    override val strokeTrainingHint = "Telefono sul cavalletto, braccialetto al polso: scegli il colpo e poi KEY1 dentro, KEY2 fuori, KEY1 tenuto premuto annulla l'ultimo. Ogni colpo diventa una clip da rivedere e da analizzare."
    override val dirCross = "Incrociato"
    override val dirMiddle = "Al centro"
    override val dirLine = "Lungolinea"
    override val dirNone = "Nessuna"
    override val wingFh = "Di dritto"
    override val wingBh = "Di rovescio"
    override val byDirection = "Per direzione"
    override val byWing = "Di dritto e di rovescio"
    override val strokeWaiting = "Colpisci: il braccialetto riconosce il colpo"
    override val strokeDetected = "Colpo riconosciuto: dentro o fuori?"
    override val placeHitter = "chi colpisce"
    override val placeGroundText = "• Di lato a chi colpisce, perpendicolare alla direzione dei colpi, a 4-5 m: fuori dal corridoio, all'altezza del punto in cui si colpisce (di solito un passo dietro la linea di fondo).\n• Obiettivo a circa 1 m da terra, telefono in orizzontale e dritto.\n• Nell'inquadratura deve stare tutto lo swing: dalla preparazione al finale il polso si muove di circa 2 m avanti e indietro; se esce, allontana il telefono o usa il grandangolo.\n• Tienilo dallo stesso lato per tutta la sessione, così i colpi si confrontano.\n• Mai sulla traiettoria della palla: fuori dal campo, protetto dalla recinzione o da un carrello."
    override val placeVolleyText = "• Di lato a chi gioca a rete, a 3-4 m, fuori dal corridoio, all'altezza del giocatore (circa 2 m dalla rete).\n• Obiettivo a circa 1 m da terra, telefono in orizzontale e dritto.\n• Il giocatore intero nell'inquadratura, con lo spazio per il passo in avanti verso la rete.\n• Mai sulla traiettoria della palla."
    override val posePrep = "Preparazione"
    override val poseFinish = "Finale"
    override val poseInFrontMetric = "Impatto davanti al corpo"
    override val poseBackswingMetric = "Ampiezza della preparazione"
    override val poseFinishMetric = "Altezza del finale"
    override val poseTimingGround = "Dalla preparazione all'impatto"
    override val poseHintGround = "L'app trova la posizione del corpo nelle clip, tutto sul telefono, e misura tre momenti: preparazione (polso più indietro), impatto e finale. Sono stime da un video di lato: valgono soprattutto per confrontare i tuoi colpi dentro con quelli fuori."
    override val poseAnalyzeShots = "Analizza i colpi"
    override val poseAnalyzingShot: (Int, Int) -> String = { a, b -> "Analisi del colpo $a di $b…" }
    override val tabPlay = "Gioca"
    override val tabTrain = "Allenati"
    override val tabVideo = "Video"
    override val tabProgress = "Progressi"
    override val tabMore = "Altro"
    override val playNewMatchHint = "Chi gioca, regole e come segni il punteggio"
    override val playRematch: (String) -> String = { "Rivincita: $it" }
    override val playResume: (String) -> String = { "Riprendi: $it" }
    override val playTournamentHint = "Americano o Mexicano, a coppie che girano"
    override val playUseAs = "Usa questo telefono come"
    override val playAsBoardHint = "Lo schermo diventa il tabellone di una partita"
    override val playAsCamera = "Telefono-telecamera"
    override val playAsCameraHint = "Riprende la partita col punteggio dell'arbitro"
    override val trainCardio = "Allenamento con la fascia"
    override val trainCardioHint = "Battito, giri, HRV e recupero"
    override val videoRecord = "Riprendi con questo telefono"
    override val videoRecordHint = "Registra un video, anche senza arbitro"
    override val videoLibrary = "I tuoi video"
    override val videoLibraryHint = "Capitoli punto per punto, highlights, filtri, importa"
    override val progressHistoryHint = "Le partite finite: riepiloghi, PDF, giocatori"
    override val progressTrendHint = "Rating TSM, classifica e confronto nel tempo"
    override val progressRacketsHint = "Statistiche e sensazioni per racchetta"
    override val moreDevices = "Dispositivi"
    override val moreDevicesHint = "Braccialetti, fasce, telecomandi, secondo telefono, tabellone TV"
    override val moreVoice = "Lingua e voce"
    override val moreVoiceHint = "Lingua dell'app, voce che annuncia il punteggio"
    override val moreRacketsHint = "Corde, tensioni e incordature"
    override val moreTransfer = "Trasferisci le partite"
    override val moreTransferHint = "Su un altro telefono, con un codice"
    override val statusBands: (Int) -> String = { "Braccialetti $it/2" }
    override val statusStraps: (Int) -> String = { "Fasce $it" }
    override val statusCamera = "Telecamera collegata"
    override val statusTv = "Tabellone TV"
    override val statusNone = "Nessun dispositivo collegato"
    override val playTournament = "Torneo tra amici"
    override val step1Hint = "Passo 1 di 2 · Chi gioca. Tutto facoltativo: puoi andare avanti anche senza nomi."
    override val step2Hint = "Passo 2 di 2 · Come segni il punteggio e le regole della partita."
    override val moreDetails = "Altri dettagli"
    override val moreDetailsHint = "Circolo, campo e torneo, se vuoi"
    override val recentPlayers = "Giocatori recenti"
    override val onbWelcome = "Benvenuto in Tennis Score Manager"
    override val onbLang = "Scegli la lingua"
    override val onbUseTitle = "Come userai TSM?"
    override val onbUseReferee = "Segno il punteggio di una partita"
    override val onbUseRefereeHint = "Dal telefono, da arbitro o mentre giochi"
    override val onbUseBands = "Gioco coi braccialetti"
    override val onbUseBandsHint = "Ogni giocatore segna i punti dal polso"
    override val onbUseTrain = "Mi alleno"
    override val onbUseTrainHint = "Fascia cardio, servizio, dritto, rovescio, volée"
    override val onbUseVideo = "Riprendo le partite"
    override val onbUseVideoHint = "Video, highlights e moviola"
    override val onbNameTitle = "Come ti chiami?"
    override val onbNameHint = "Serve per i tuoi progressi e il rating TSM. Puoi lasciarlo vuoto e scriverlo dopo in Altro › Tu."
    override val onbSkip = "Salta"
    override val onbStart = "Inizia"
    override val moreProfile = "Tu"
    override val moreProfileHint = "Il tuo nome, per progressi e rating"
    override val moreTour = "Presentazione dell'app"
    override val moreTourHint = "Le prime domande, di nuovo"
    override val profileName = "Il tuo nome"
    override val matchNotesHint = "Scrivi qui le tue note sulla partita: finiscono nel PDF, nel testo condiviso e nei file salvati."
    override val formatPdf = "PDF completo (risultato, statistiche, cronologia, foto)"
    override val shareChoose = "Cosa condividi?"
    override val shareChooseHint = "L'immagine col testo va bene per WhatsApp e i social; il PDF ha anche statistiche, cronologia e foto della partita."
    override val shareImage = "Immagine"
    override val sharePdf = "PDF completo"
    override val tournamentButton = "Torneo Americano o Mexicano"
    override val tournamentTitle = "Torneo"
    override val tournamentSubtitle = "Americano e Mexicano: coppie che cambiano a ogni turno"
    override val tournamentNew = "Nuovo torneo"
    override val tournamentAmericano = "Americano"
    override val tournamentMexicano = "Mexicano"
    override val tournamentPlayersCount: (Int) -> String = { n -> "$n giocatori" }
    override val tournamentRound: (Int) -> String = { n -> "Turno $n" }
    override val tournamentFinished = "Concluso"
    override val tournamentKindTitle = "Tipo di torneo"
    override val tournamentName = "Nome del torneo (facoltativo)"
    override val tournamentAmericanoHint = "Le coppie cambiano a ogni turno: ognuno gioca con e contro più persone possibile."
    override val tournamentMexicanoHint = "Primo turno come nell'Americano, poi per classifica: nello stesso campo 1° e 4° contro 2° e 3°."
    override val tournamentPlayers = "Giocatori"
    override val tournamentPlayersHint = "Da 4 a 16, ognuno col suo nome: la classifica è personale. Chi ha la foto nello storico la ritrova qui."
    override val tournamentAddPlayer = "Aggiungi giocatore"
    override val tournamentNeedPlayers = "Servono almeno 4 giocatori, tutti con nomi diversi."
    override val tournamentCourts = "Campi"
    override val tournamentCourtsHint: (Int, Int) -> String = { a, b -> "Partite per turno: $a · a riposo a ogni turno: $b" }
    override val tournamentScoring = "Punteggio"
    override val tournamentScoringPoints = "A punti"
    override val tournamentScoringTime = "A tempo"
    override val tournamentMinutesN: (Int) -> String = { n -> "$n minuti" }
    override val tournamentPointsN: (Int) -> String = { n -> "$n punti" }
    override val tournamentTimeHint = "Si gioca per i minuti scelti, poi si scrive il risultato (o lo si conta punto per punto col cronometro)."
    override val tournamentPointsHint = "Ogni scambio vale un punto e si serve 4 volte a testa a turno; la partita finisce quando i punti delle due coppie sommati fanno il totale (es. 13-11 a 24)."
    override val tournamentSuggested: (Int) -> String = { n -> "Turni consigliati: $n (ognuno gioca una volta con tutti)." }
    override val tournamentCreate = "Inizia il torneo"
    override val tournamentShare = "Condividi classifica"
    override val tournamentNextRound = "Turno successivo"
    override val tournamentRounds = "Turni"
    override val tournamentStandings = "Classifica"
    override val tournamentResting = "A riposo"
    override val tournamentFinish = "Termina torneo"
    override val tournamentMissingResults = "Mancano dei risultati in questo turno: scrivili prima di passare al successivo."
    override val tournamentFinishText = "La classifica resta com'è e il torneo va tra quelli conclusi."
    override val tournamentDeleteTitle = "Eliminare il torneo?"
    override val tournamentDeleteText = "Il torneo e la sua classifica spariscono da questo telefono."
    override val tournamentCourt: (Int) -> String = { n -> "Campo $n" }
    override val tournamentScoreHint: (Int) -> String = { n -> "I due punteggi sommati devono fare $n." }
    override val tournamentCount = "Conta punto per punto"
    override val tournamentStartTimer = "Avvia il cronometro"
    override val tournamentTimeUp = "Tempo scaduto: finite lo scambio e salvate il risultato."
    override val tournamentMatchOver = "Partita finita: salva il risultato."
    override val tournamentSave = "Salva risultato"
    override val tournamentPointsShort = "Punti"
    override val tournamentPlayedShort = "G"
    override val tournamentWonShort = "V"
    override val tournamentStandingsHint = "Punti = punti fatti dalla propria coppia in tutte le partite; a parità contano le vittorie (V) e la differenza punti. G = partite giocate."
    override val tournamentRoundsPlayed: (Int) -> String = { n -> "Turni giocati: $n" }
    override val share = "Condividi"
    override val saveDialogTitle = "Salva file"
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
    override val shareSubjectPadel = "Risultato partita di padel"
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
    override val tvPageTitle = "Tabellone TSM"

    override val tvSection = "Tabellone su TV"
    override val tvEnable = "Tabellone su TV o monitor"
    override val tvEnableHint = "Un altro telefono (o un computer, o un Chromecast) mostra il punteggio in diretta su un monitor. I telefoni devono stare sulla stessa rete: l'hotspot di uno dei due."
    override val tvAddress = "Indirizzo del tabellone"
    override val tvNoNetwork = "Nessuna rete: accendi l'hotspot su uno dei due telefoni e collega l'altro."
    override val tvScreens: (Int) -> String = { if (it == 0) "Nessun tabellone collegato" else if (it == 1) "1 tabellone collegato" else "$it tabelloni collegati" }
    override val tvQrHint = "Sull'altro telefono: apri Tennis Score Manager e tocca «Usa come tabellone» (si collega da solo), oppure inquadra il codice con la fotocamera e aprilo nel browser."
    override val tvLook = "Aspetto del tabellone"
    override val tvTitle = "Scritta in basso"
    override val tvTitleHint: (String) -> String = { if (it.isEmpty()) "Vuota: nessuna scritta" else "Vuota: «$it» (circolo e campo di Nuova partita)" }
    override val tvColorOf: (String) -> String = { "Colore di $it" }
    override val tvShowClock = "Tempo partita"
    override val tvShowTimers = "Cronometro servizio e pause"
    override val tvShowSets = "Set conclusi"
    override val tvShowMessages = "Messaggi (palla break, set point...)"
    override val tvShowServe = "Pallina di chi serve"
    override val tvGhost = "Segmenti spenti visibili"
    override val tvPreview = "Anteprima su questo telefono"
    override val tvChromecastHint = "Con un Chromecast: sul telefono-tabellone usa il pulsante «Trasmetti» delle impostazioni rapide («Trasmissione schermo» fino ad Android 14, «Smart View» sui Samsung). Il Chromecast vuole una rete con internet: accendi i dati mobili sul telefono che fa l'hotspot."

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

    // Racchetta virtuale
    override val racketTitle = "Racchette"
    override val racketHint = "Confronta statistiche e sensazioni con racchette, corde o tensioni diverse."
    override val racketMine = "Le mie racchette"
    override val racketCompare = "Confronto"
    override val racketAdd = "Aggiungi racchetta"
    override val racketNew = "Nuova racchetta"
    override val racketEdit = "Modifica"
    override val racketNone = "Nessuna"
    override val racketEmpty = "Nessuna racchetta. Aggiungi le tue racchette con corde e tensione: poi scegli quella usata prima di ogni partita o allenamento."
    override val racketField = "Racchetta"
    override val racketSetupHint = "Scegli la racchetta di ciascun giocatore: la partita ricorderà racchetta, corde e tensione di oggi."
    override val racketCatalog = "Dal catalogo"
    override val racketCatalogHint = "Dati del produttore, indicativi: se hai misurato la tua racchetta correggili."
    override val racketName = "Soprannome (A, B…)"
    override val racketBrand = "Marca"
    override val racketModel = "Modello"
    override val racketWeight = "Peso (g)"
    override val racketBalance = "Bilanciamento (mm)"
    override val racketHead = "Piatto corde (cm²)"
    override val racketPattern = "Schema corde"
    override val racketOwner = "Di chi è (nome scritto in Nuova partita)"
    override val racketCheckHours = "Controllo corde dopo (ore)"
    override val racketRetired = "Messa da parte"
    override val racketRetiredHint = "Non si propone più prima di giocare, ma resta nei confronti."
    override val racketDeleteTitle = "Eliminare la racchetta?"
    override val racketDeleteText = "Le partite e gli allenamenti già giocati ne conservano una copia e restano nel confronto."
    override val stringTitle = "Incordatura"
    override val stringNew = "Nuova incordatura"
    override val stringHistory = "Storico incordature"
    override val stringNone = "Nessuna incordatura registrata."
    override val stringType = "Tipo di corda"
    override val stringTypeName: (Int) -> String = { listOf("Monofilamento", "Multifilamento", "Sintetico", "Budello", "Ibrido").getOrElse(it) { "" } }
    override val stringCatalog = "Corde comuni"
    override val stringCross = "Corda delle orizzontali"
    override val stringGauge = "Calibro (mm)"
    override val stringMains = "Verticali (kg)"
    override val stringCrosses = "Orizzontali (kg)"
    override val stringDate = "Data dell'incordatura"
    override val stringNotes = "Note"
    override val stringHours: (String, Int) -> String = { h, max -> "$h h di gioco dall'ultima incordatura · controllo a $max h" }
    override val stringCheck = "Ore di controllo raggiunte: dai un'occhiata alle corde."
    override val stringCheckNote = "Le ore sono quelle delle partite e degli allenamenti associati alla racchetta. Dal solo tempo l'app non può sapere quanta tensione è rimasta."
    override val stringDeleteTitle = "Eliminare l'incordatura?"
    override val racketHours = "Ore di gioco"
    override val racketSessions: (Int) -> String = { n -> if (n == 1) "1 sessione" else "$n sessioni" }
    override val compareByRacket = "Per racchetta"
    override val compareByString = "Per incordatura"
    override val compareA = "Configurazione A"
    override val compareB = "Configurazione B"
    override val compareTooFew = "Per confrontare servono partite o allenamenti con almeno due configurazioni diverse. Scegli la racchetta in Nuova partita o nell'allenamento, oppure associala dal riepilogo."
    override val compareNote = "I numeri dicono com'è andata con ciascuna configurazione, non quale sia la migliore: contano anche avversario, superficie, condizioni e forma del giorno."
    override val compareSessions = "Sessioni"
    override val comparePerMatch: (String) -> String = { "$it a partita" }
    override val compareStrokes = "Colpi dei braccialetti"
    override val compareRated: (Int) -> String = { n -> if (n == 1) "sensazioni da 1 sessione" else "sensazioni da $n sessioni" }
    override val feelTitle = "Sensazioni"
    override val feelHint = "Come ti sei trovato? Da 1 (poco) a 5 (molto); ritocca per togliere."
    override val feelControl = "Controllo"
    override val feelComfort = "Comfort"
    override val feelPower = "Potenza"
    override val feelFatigue = "Fatica"
    override val racketUsed = "Racchetta usata"

    override val aboutLicense: (String) -> String = { "Tennis Score Manager $it · software libero con licenza GNU GPL versione 3 o successiva, senza alcuna garanzia." }
    override val aboutSource = "Codice sorgente"
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
    override val sportSection = "Sport"
    override val sportTennis = "Tennis"
    override val sportPadel = "Padel"
    override val padelHint = "Same rules as tennis: doubles and golden point are already set, but you can change them."
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
    override val modeRemotes = "Remotes"
    override val modeRemotesHint = "Each player scores with a Bluetooth selfie remote (€1–3): one click = point, double click or long press = undo. If the remote has two buttons, the other one undoes with a single click."
    override val remotesHowTo = "1. Pair the remotes in the phone's Bluetooth settings, like a keyboard. 2. Tap “Assign” under the player and press the button for the point on their remote: a second button, if any, will undo."
    override val bluetoothSettings = "Bluetooth settings"
    override val remoteFor: (String) -> String = { "$it's remote" }
    override val noRemote = "None"
    override val remoteConnected = "Connected"
    override val remoteNotConnected = "Not connected: press a key to wake it up"
    override val assignRemote = "Assign"
    override val removeRemote = "Remove"
    override val pressRemoteKey = "Press a key on the remote…"
    override val remotePressed = "Key received"
    override val remotesScreenOn = "Keep the app open during the match: the screen stays on by itself. With the screen locked or another app in front, the keys don't get through."
    override val requirements = "Requirements"
    override val bluetooth = "Bluetooth"
    override val location = "Location permission"
    override val locationServices = "Location on"
    override val enable = "Turn on"
    override val allow = "Allow"
    override val ok = "OK"
    // Fasce cardio
    override val heartSection = "Heart rate"
    override val heartRate = "Heart rate straps"
    override val heartRateHint = "Heart rate and calories from Bluetooth chest straps (Coospo, Magene, Polar, Garmin…), in any mode."
    override val heartHowTo = "Wet the electrodes, put the strap on and pick it below: each player's strap only needs picking once."
    override val strapFor: (String) -> String = { "$it's strap" }
    override val noStrap = "None"
    override val strapSearching = "Searching for straps…"
    override val strapConnected = "Connected"
    override val strapConnecting = "Connecting…"
    override val strapWaiting = "Waiting: put it on and wet the electrodes"
    override val strapNoContact = "Not touching the skin"
    override val strapRelayed = "Via second phone"
    override val strapNoDrops = "no dropouts"
    override val strapDrops: (Int, Int) -> String = { n, s -> "$n ${if (n == 1) "dropout" else "dropouts"} ($s s)" }
    override val relayTitle = "Second phone"
    override val relayHint = "With one phone behind each baseline, straps and bands always have a phone nearby: the second one hears them and acts as a bridge to this one, which keeps the match, and it can show the scoreboard. This phone creates its own network, with no SIM or data needed, and the other one joins it by itself by scanning the code."
    override val relayMain = "Use a second phone"
    override val relayJoin = "Act as second phone"
    override val relayShowCode = "Show the code"
    override val relayShowQr = "On the other phone, in page 2 › Second phone, tap Act as second phone and scan this code."
    override val relayScanPrompt = "Scan the main phone's code"
    override val relayNoSatellite = "No second phone connected"
    override val relaySatellites: (String) -> String = { "Second phone connected: $it" }
    override val relayStop = "Stop"
    override val relaySatelliteOn = "This phone is acting as second phone: it follows the straps picked on the main phone and passes the heart rate on. Leave it behind the baseline with the app open."
    override val relayConnected = "Connected to the main phone"
    override val relayDisconnected = "Main phone not reachable: retrying…"
    override val relayPending: (Int) -> String = { "$it readings to send" }
    override val relayPreparing = "Setting up the network…"
    override val relayShowBoard = "Show the scoreboard"
    override val workoutTitle = "Workout"
    override val workoutHint = "Heart rate with the chest strap, no match: laps, HRV and recovery."
    override val workoutPickStrap = "Pick the strap below: wet the electrodes and put it on."
    override val workoutStart = "Start"
    override val workoutPause = "Pause"
    override val workoutResume = "Resume"
    override val workoutLap = "Lap"
    override val workoutEnd = "Finish"
    override val workoutRecovering: (Int) -> String = { "Recovery: $it s left" }
    override val workoutRecoveringHint = "Stay still: it measures how much your heart rate drops in the first minute."
    override val workoutSaveNow = "Save now"
    override val workoutHistory = "Saved workouts"
    override val workoutNone = "No saved workouts."
    override val workoutDuration = "Duration"
    override val workoutHrv = "HRV (RMSSD)"
    override val workoutHrvHint = "Variability between one beat and the next, measured during the workout: compare it with your own similar workouts, not with other people's."
    override val workoutRecovery = "Recovery in 1 minute"
    override val workoutRecoveryHint = "Recovery: how much the heart rate drops in the minute after the end (the more, the better)."
    override val workoutLaps = "Laps"
    override val workoutAvgShort = "avg"
    override val workoutMaxShort = "max"
    override val workoutRecShort = "recovery"
    override val workoutShareCsv = "CSV"
    override val workoutSharePdf = "PDF"
    override val workoutDeleteTitle = "Delete the workout?"
    override val workoutDeleteText = "It will be removed from this phone."
    override val workoutChart = "Heart rate during the workout"
    override val progressTitle = "Progress"
    override val progressFilters = "Filters"
    override val progressPeriod = "Period"
    override val progressPeriodAll = "All time"
    override val progress30 = "30 days"
    override val progress90 = "3 months"
    override val progress365 = "1 year"
    override val progressWeekdays = "Days of the week"
    override val progressOpponent = "Opponent"
    override val progressAnyOption = "All"
    override val progressChoose = "To compare"
    override val progressSelected: (Int, Int) -> String = { a, b -> "$a of $b selected" }
    override val progressSelectAll = "All"
    override val progressSelectNone = "None"
    override val progressNoneMatch = "Nothing matches these filters."
    override val progressHint = "Compare workouts and matches over time to see whether you are improving: a document to share or print."
    override val progressWorkouts = "Workouts"
    override val progressMatches = "Matches"
    override val progressPlayer = "Player"
    override val progressNoPlayers = "No finished matches in the history."
    override val progressCount: (Int) -> String = { n -> if (n == 1) "1 entry" else "$n entries" }
    override val progressTooFew = "At least 2 workouts or matches are needed to compare."
    override val progressFirstHalf = "First half"
    override val progressSecondHalf = "Second half"
    override val progressTrend = "Trend"
    override val progressBetter = "better"
    override val progressWorse = "worse"
    override val progressSame = "steady"
    override val progressList = "List"
    override val progressDate = "Date"
    override val progressResult = "Result"
    override val progressWon = "Won"
    override val progressLost = "Lost"
    override val progressPointsShort = "Points"
    override val progressDfShort = "DF"
    override val progressWinnersShort = "Win."
    override val progressUeShort = "UE"
    override val progressWins = "Matches won"
    override val progressPointsWon = "Points won"
    override val progressPdf = "PDF"
    override val progressPrint = "Print"
    override val relayWifiOff = "This phone's Wi-Fi is off: turn it on (no need to join any network) to get onto the main phone's network."
    override val relayWifiOn = "Turn on Wi-Fi"
    override val relayJoinCamera = "Act as camera"
    override val cameraRoleOn = "This phone acts as a camera: it gets the score from the umpire and puts it on the video."
    override val cameraOpen = "Open the camera"
    override val cameraLiveFailed: (String) -> String = { "Live stream failed: $it" }
    override val cameraNotSupported = "This phone cannot record in 1080p."
    override val cameraSaved: (Int) -> String = { n -> "Video saved in Movies/Tennis Score Manager, with $n points marked." }
    override val cameraSaveFailed: (String) -> String = { "Video saved only in $it" }
    override val cameraPermissions = "Camera and microphone permissions are needed."
    override val cameraConnecting = "Connecting…"
    override val cameraNoReferee = "No umpire: video only"
    override val cameraWaitingScore = "Waiting for the score"
    override val cameraScoreOk = "Score connected"
    override val cameraRec = "Record"
    override val cameraStopRec = "Stop"
    override val cameraGoLive = "Go live"
    override val cameraStopLive = "Stop live"
    override val cameraOverlay = "Overlay"
    override val cameraStreamSettings = "Live: server"
    override val cameraClose = "Close"
    override val cameraStopFirst = "Stop recording and live stream first."
    override val camRemoteTitle = "Camera"
    override val camRemoteOpen = "Camera view and controls"
    override val camRemoteHint = "What the camera phone sees, as it ends up in the video. The controls work as on its own screen."
    override val camRemoteWaiting = "Waiting for the picture…"
    override val camRemoteGone = "Camera not connected"
    override val camRemoteZoom = "Zoom"
    override val camRemoteBattery: (Int) -> String = { "Battery $it%" }
    override val camRemoteFree: (String) -> String = { "Free $it" }
    override val cameraNoStreamKey = "No live stream key: set it on the camera phone (Settings)."
    override val assistTitle = "Framing assistant"
    override val assistButton = "Framing"
    override val assistHint = "Where you put the phone: the app works out the tilt that keeps both baselines and the far player in the video, and tells you which lens to use."
    override val assistDistance = "Distance behind the baseline (m)"
    override val assistHeight = "Lens height above the ground (m)"
    override val assistCourt = "Court"
    override val courtTennisSingles = "Tennis singles"
    override val courtTennisDoubles = "Tennis doubles"
    override val courtPadel = "Padel"
    override val assistTiltIs: (String) -> String = { "Suggested tilt: $it downwards" }
    override val assistFitsWith: (String) -> String = { "The whole court fits with the $it lens." }
    override val assistCornersOut: (String) -> String = { "The near corners of the court stay out even with the $it lens: move the phone further back from the baseline." }
    override val assistMinDistance: (String, String) -> String = { a, b -> "With the $a lens you need at least $b behind the baseline." }
    override val assistTooTall = "Not everything fits vertically: move the phone back or raise it."
    override val assistUseLens: (String) -> String = { "Use the $it lens" }
    override val assistShowLevel = "Show the level"
    override val assistHideLevel = "Hide the level"
    override val assistPitch = "Tilt"
    override val assistLevel = "Level"
    override val assistAimLower: (String) -> String = { "aim $it lower" }
    override val assistAimHigher: (String) -> String = { "aim $it higher" }
    override val assistTurnCw: (String) -> String = { "turn $it clockwise" }
    override val assistTurnCcw: (String) -> String = { "turn $it anticlockwise" }
    override val assistOk = "OK"
    override val assistNoSensor = "This phone has no gravity sensor: no level."
    override val assistCenter = "The sensors can't tell left from right: check on the preview that the net is in the middle."
    override val lensMain = "main"
    override val lensWide = "wide"
    override val cameraLens = "Lens"
    override val cameraLensBusy = "Stop recording and live stream to change lens."
    override val outlineButton = "Outline"
    override val outlineTitle = "Court outline"
    override val outlineShow = "Show the court outline"
    override val outlineHide = "Hide the outline"
    override val outlineHint = "Move the sliders until the drawn lines match the real ones: then distance, height and direction are right and the suggested tilt becomes accurate. The calibration is remembered for this court."
    override val calibDistance = "Distance"
    override val calibHeight = "Height"
    override val calibPan = "Direction"
    override val calibLateral = "Sideways offset"
    override val calibFov = "Field of view"
    override val calibPitch = "Tilt correction"
    override val outlineLoaded: (String) -> String = { "Calibration for $it restored" }
    override val outlineReset = "Reset corrections"
    override val outlineDone = "Done"
    override val cameraStreamHint = "For YouTube: YouTube Studio › Go live › copy the stream key. For other services (Twitch, Facebook, your own server) also change the server (rtmp:// or rtmps://)."
    override val cameraStreamUrl = "RTMP server"
    override val cameraStreamKey = "Stream key"
    override val videoTitle = "Videos"
    override val videoHint = "Matches recorded by the camera phone, to watch point by point."
    override val videoNone = "No videos: record a match with the camera phone (page 2 › Second phone › Act as camera)."
    override val videoPoints: (Int) -> String = { n -> if (n == 1) "1 point" else "$n points" }
    override val videoDeleteTitle = "Delete the video?"
    override val videoDeleteText = "The video is also removed from the gallery, together with its chapters."
    override val videoYoutubeChapters = "YouTube chapters"
    override val videoNoPoints = "This video has no points marked: it was recorded without the umpire's score."
    override val videoPoint = "Point"
    override val videoGame = "Game"
    override val videoSet = "Set"
    override val videoReplay = "Replay the point"
    override val videoStart = "Start"
    override val videoKind: (String) -> String = { k -> when (k) { "ACE" -> "Ace"; "DOUBLE_FAULT" -> "Double fault"; "WINNER" -> "Winner"; "UNFORCED_ERROR" -> "Unforced error"; "FORCED_ERROR" -> "Forced error"; "ERROR" -> "Error"; else -> k } }
    override val videoImport = "Import video"
    override val videoImportHint = "A video from the phone (even shot with another camera): the points are marked while watching it. The file stays where it is."
    override val videoTagHowTo = "Watch the video and, at the end of each point, tap who won it; then, if you like, how it ended. The score is worked out by itself."
    override val videoTagTooEarly = "Move the video past the last marked point."
    override val videoPointFor: (String) -> String = { "Point $it" }
    override val videoUndoLast = "Undo last"
    override val videoTagSetupTitle = "Who is playing?"
    override val videoFirstServer = "Who serves first"
    override val videoTagSetupHint = "Format and rules are the ones on page 2."
    override val hlTitle = "Highlights"
    override val hlBig = "Break, set and match points"
    override val hlRallies = "Long rallies (from the wristbands)"
    override val hlMax = "Maximum length"
    override val hlCustom = "Other length (minutes)"
    override val hlMusic = "Statistics music"
    override val hlMusicPick = "Choose a track"
    override val hlMusicReset = "Use the app's own"
    override val hlMusicHint = "A track from the phone: its beginning is used, with fades. Only use music you have the right to put in your videos."
    override val hlMusicCredit: (String) -> String = { "If you publish the video, credit: $it" }
    override val eventSection = "Tournament"
    override val eventName = "Tournament name"
    override val eventCategory = "Category or draw (e.g. Open, 4.1 limit, Over 45)"
    override val eventRound = "Round (e.g. Quarterfinal, Final)"
    override val eventHint = "Optional: they appear in the summary, the PDF and the highlights statistics."
    override val playerRank = "Ranking (e.g. 4.1, NR)"
    override val hlStats = "Statistics at the end of the video"
    override val hlStatsHint: (Int, Int) -> String = { n, sec -> "$n screens with the match statistics ($sec s) after the points." }
    override val hlStatsNone = "Statistics not available: the video's match is not in the history."
    override val cardTitle: (String) -> String = { "$it statistics" }
    override val cardFinal = "Final"
    override val cardPartial = "Partial"
    override val cardBreakPoints = "Break points"
    override val cardRallies = "Rallies"
    override val cardShortRallies = "Points won up to 8 shots"
    override val cardLongRallies = "Points won with 9+ shots"
    override val hlNone = "No suitable points: mark aces, winners or play with the wristbands."
    override val hlPreview: (Int, String) -> String = { n, t -> "$n points · $t" }
    override val hlHint = "If it is too long, clips are shortened first, then the least important points are left out. The video goes to the gallery."
    override val hlCreate = "Create"
    override val hlMaking: (Int) -> String = { "Editing… $it%" }
    override val hlDone = "Highlights ready, saved in Movies/Tennis Score Manager."
    override val hlFailed = "Editing failed."
    override val hlOrderHint = "Items at the top take precedence: if the video is too long, those at the bottom are left out first."
    override val hlUp = "Move up"
    override val hlDown = "Move down"
    override val coachTitle = "Coach filters"
    override val coachPlayer = "Player"
    override val coachAll = "All"
    override val coachPoint = "Points"
    override val coachWon = "Won"
    override val coachLost = "Lost"
    override val coachServe = "Serve"
    override val coachServing = "Serving"
    override val coachReturning = "Returning"
    override val coachServeKind = "Serve"
    override val coachFirst = "First"
    override val coachSecond = "Second"
    override val coachEnd = "How it ended"
    override val coachSituation = "Situation"
    override val coachBreak = "Break points"
    override val coachSetMatch = "Set and match points"
    override val coachApply = "Apply"
    override val coachClear = "Remove filter"
    override val coachCount: (Int) -> String = { n -> if (n == 1) "1 point" else "$n points" }
    override val coachSummary: (Int, Int) -> String = { n, w -> "$n points · $w won (${if (n > 0) w * 100 / n else 0}%)" }
    override val coachPlay = "Play"
    override val coachNone = "No points with this filter"
    override val replayTitle = "Replay"
    override val replayHint = "From the camera phone: the challenged point from the start, even if some time has passed."
    override val replayLastPoint = "Last point"
    override val replayPrevPoint = "Previous point"
    override val replaySinceLast = "Since the last scored point"
    override val replayWaiting = "Coming from the camera…"
    override val replayFailed = "Replay did not arrive: check that the camera is open and connected."
    override val replayClose = "Close"
    override val replayOnBoard = "Scoreboard"
    override val replayAlsoOnBoard = "Also on the scoreboard"
    override val replayNoCamera = "No camera connected."
    override val cameraReplaySent = "Replay sent to the umpire"
    override val cameraReplayNone = "Replay not available: no video in memory"
    override val relayOwnNetwork: (String) -> String = { "Network created by this phone: $it" }
    override val relayJoinWaiting = "Confirm on screen the connection to the main phone's network."
    override val relayJoinRefused = "Network connection declined: tap Stop and scan the code again."
    override val relayJoinLost = "Main phone's network lost: retrying…"
    override val relayJoinByHand: (String, String) -> String = { n, p -> "In the Wi-Fi settings, connect to the network $n (password $p)." }
    override val heartAge = "Age"
    override val heartWeight = "Weight (kg)"
    override val heartMale = "Man"
    override val heartFemale = "Woman"
    override val heartProfileHint = "Age, weight and sex are only used to estimate calories."
    override val heartStat = "Heart rate"
    override val heartAvg = "Average heart rate"
    override val heartMax = "Maximum heart rate"
    override val heartCalories = "Calories (estimate)"
    override val heartChart = "Heart rate during the match"
    override val heartLine: (Int, Int) -> String = { a, m -> "average $a · max $m bpm" }
    override val caloriesLine: (Int) -> String = { "~$it kcal (estimate)" }
    override val heartStatsHint = "Calories estimated from heart rate with the Keytel formula: the typical error is 20%."
    override val heartZones = "Time in heart rate zones"
    override val zoneName: (Int) -> String = { "Z${it + 1} " + listOf("Very light", "Light", "Moderate", "Hard", "Maximum").getOrElse(it) { "" } }
    override val heartZonesHint = "Zones as a percentage of the maximum heart rate estimated from age (208 − 0.7 × age): the wearer's age is needed."
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
    override val strokes = "Strokes"
    override val strokeName: (Int) -> String = { listOf("Forehand", "Backhand", "Serve", "Volley").getOrElse(it) { "?" } }
    override val strokeAbbr: (Int) -> String = { listOf("F", "B", "S", "V").getOrElse(it) { "?" } }
    override val strokeOther = "Not recognized"
    override val strokeRecognition = "Stroke recognition"
    override val strokeRecognitionHint = "The wristband recognizes the wearer's forehand, backhand, serve and volley. The motion sensor uses a little more battery."
    override val strokeOnBand = "Show the stroke on the wristband"
    override val strokeOnBandHint = "Briefly, only when the display shows nothing else."
    override val strokeCalibration = "Calibration"
    override val strokeCalibrationHint = "Wear the wristband as in a match, tap Calibrate and play 5-10 strokes of that type, with a short pause between them. The examples are stored in the wristband."
    override val calibrate = "Calibrate"
    override val calibrateStop = "Done"
    override val undoExample = "Undo the last one"
    override val clearExamples = "Clear the examples"
    override val strokesReady = "Ready: it recognizes the calibrated strokes."
    override val strokesNotReady = "At least 3 examples for 2 strokes are needed."
    override val strokesFirmwareOld = "To recognize strokes, upload the TSM_Band 2.4 firmware."
    override val wristSpeed = "Wrist speed"
    override val maxShort = "max"
    override val strokesPerPoint = "Strokes per point"
    override val rallyAverage = "average"
    override val rallyLongest = "longest"
    override val rallyShort = "short"
    override val rallyMedium = "medium"
    override val rallyLong = "long"
    override val tabSummary = "Summary"
    override val tabStats = "Statistics"
    override val tabChronology = "Point by point"
    override val periodMatch = "Match"
    override val periodSet: (Int) -> String = { "Set $it" }
    override val statsHint = "Worked out from the points scored in the app and, with the wristbands, from the strokes. Aces, double faults, first serves and errors can't be known."
    override val statsHintDetailed = "Worked out from the points scored in the app, with the aces, faults, winners and errors of the detailed stats and, with the wristbands, from the strokes. A point given with “Point” (or by wristbands and remotes) counts without detail."
    override val statServe = "Serve"
    override val statReturn = "Return"
    override val statPoints = "Points"
    override val statGames = "Games"
    override val statServicePoints = "Service points won"
    override val statServiceGames = "Service games won"
    override val statBreakSaved = "Break points saved"
    override val statReturnPoints = "Return points won"
    override val statReturnGames = "Return games won"
    override val statBreakWon = "Break points won"
    override val statTotalPoints = "Total points won"
    override val statStreak = "Points in a row (max)"
    override val statLast10 = "Last 10 points"
    override val statMatchPointsSaved = "Match points saved"
    override val statTotalGames = "Total games won"
    override val statGamePointsWon = "Game points converted"
    override val statSetPointsWon = "Set points converted"
    override val statMatchPointsWon = "Match points converted"
    override val statLoveGames = "Love games won"
    override val statBestGameRun = "Games in a row (max)"
    override val statSetsFromBreakDown = "Sets won from a break down"
    override val statTimes = "Times"
    override val statSetDurations = "Set durations"
    override val statLongestGame = "Longest game"
    override val longestGameValue: (String, Int) -> String = { t, n -> "$t ($n points)" }
    override val statAces = "Aces"
    override val statDoubleFaults = "Double faults"
    override val statFirstServeIn = "First serves in"
    override val statFirstServeWon = "Points won on 1st serve"
    override val statSecondServeWon = "Points won on 2nd serve"
    override val statWinners = "Winners"
    override val statErrors = "Errors"
    override val statUnforcedErrors = "Unforced errors"
    override val statForcedErrors = "Forced errors"
    override val detailAskOutcome = "How did the point end?"
    override val detailAskErrorType = "Unforced or forced error?"
    override val detailUnforced = "Unforced"
    override val detailForced = "Forced"
    override val detailWinnerOf: (String) -> String = { x -> "Winner $x" }
    override val detailErrorOf: (String) -> String = { x -> "Error $x" }
    override val trendTitle = "Trend"
    override val trendHint = "Points won match by match (last 12; filled = match won, line = 50%)."
    override val trendPointsAvg: (Int) -> String = { n -> "Average points won: $n%" }
    override val trendDetailTitle: (Int) -> String = { n -> "Averages per match ($n with detailed stats)" }
    override val detailedStats = "Detailed stats"
    override val detailedStatsHint = "For someone scoring from the side of the court: instead of the two big buttons there are ace, fault and, for each player, winner, error and point. Needed for aces, double faults, first serves, winners and errors."
    override val btnAce = "Ace"
    override val btnFault = "Fault"
    override val btnDoubleFault = "Double fault"
    override val btnWinner = "Winner"
    override val btnError = "Error"
    override val btnPoint = "Point"
    override val firstServe = "1st serve"
    override val secondServe = "2nd serve"
    override val serveOf: (String) -> String = { "serving: $it" }
    override val statStrokesTotal = "Total strokes"
    override val statAvgWristSpeed = "Average wrist speed"
    override val serveLost = "Lost serve"
    override val markerBreak = "BP"
    override val markerSet = "SP"
    override val markerMatch = "MP"
    override val chronoGame = "GAME"
    override val chronoBreak = "BREAK"
    override val chronoSet = "SET"
    override val chronoMatch = "MATCH"
    override val chronoTiebreak = "Tiebreak"
    override val powerOff = "Power off"
    override val bandPowerOffTitle = "Power off the wristband?"
    override val bandPowerOffText: (String) -> String = { "$it switches off now. To turn it back on: one click on the side button." }
    override val languageSection = "Language"
    override val languageHint = "Applies to the screens, the umpire's voice, the TV scoreboard and the wristbands."
    override val audioSection = "Audio and voice"
    override val voiceCalls = "Umpire voice calls"
    override val voiceFiles: (Int, Int) -> String = { n, tot -> "Generated files: $n/$tot" }
    override val voiceFilesHint = "Everything works offline with the voices installed on the phone. Custom recordings (ZIP) always take precedence over text-to-speech."
    override val generateVoice = "Generate files"
    override val generating: (Int, Int) -> String = { n, tot -> "Generating $n/$tot…" }
    override val voiceGenerationFailed = "Generation not completed: the previous voice files are unchanged."
    override val importVoiceZip = "Import ZIP"
    override val voiceImportFailed = "ZIP unreadable or incomplete: no recordings were changed."
    override val deleteCustomVoice = "Remove recordings"
    override val deleteCustomConfirmTitle = "Remove the recordings?"
    override val deleteCustomConfirmText: (Int) -> String = { n -> "The custom English recordings ($n) are deleted from the phone. To get them back, import the ZIP again." }
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
    override val voiceReadme: (String, String) -> String = { langs, formats ->
        "TENNIS SCORE MANAGER - VOICE FILES\n" +
            "Put your recordings in voice/<language>/ ($langs), named after the key.\n" +
            "Formats: $formats. Player names are always read by text-to-speech.\n" +
            "The tts/ folder holds the files generated by the app: your recordings take precedence."
    }
    override val voiceReadmeKey = "KEY"
    override val testVoice = "Test voice"
    override val stopVoiceTest = "Stop the test"
    override val ttsMissing = "English text-to-speech voice is not installed on this phone."
    override val installVoice = "Install voice"
    override val ttsEngineError = "Text-to-speech is not available: check the engine in Android Settings."
    override val openTtsSettings = "Open settings"
    override val formatSection = "Match format"
    override val formatBestOfThree = "3 sets · tie-break to 7"
    override val formatBestOfThreeHint = "Best of three sets, tie-break at 6-6 in every set."
    override val formatMatchTiebreak = "2 sets + match tie-break to 10"
    override val formatMatchTiebreakHint = "At one set all the third set is a 10-point match tie-break (win by 2)."
    override val formatBestOfFive = "5 sets · tie-break to 7"
    override val formatBestOfFiveHint = "Best of five sets, tie-break at 6-6 in every set."
    override val formatProSet = "Pro set"
    override val formatProSetHint = "A single set to 8 or 10 games, win by 2, tie-break at 8-8 or 10-10."
    override val formatMatchTiebreakOnly = "Match tie-break to 10 only"
    override val formatMatchTiebreakOnlyHint = "The match is a single 10-point match tie-break (win by 2)."
    override val gamesPerSetTitle = "Games per set"
    override val gamesShort = "4 games (short sets)"
    override val gamesShortHint = "Short sets: the first to 4 games with a 2-game lead wins the set; tie-break at 4-4."
    override val setTiebreak = "Tie-break in sets"
    override val labelShortSets = "sets to 4 games"
    override val labelTiebreak7 = "tie-break to 7"
    override val labelNoTiebreak = "no tie-break"
    override val gamesN: (Int) -> String = { "$it games" }
    override val setTiebreakHint: (Int) -> String = { "Tie-break to 7 at $it-$it. Off, the set goes on until someone leads by 2 games." }
    override val labelSets: (Int) -> String = { "$it sets" }
    override val labelProSet: (Int) -> String = { "pro set to $it" }
    override val noAd = "No-Ad (deciding point)"
    override val noAdHint = "At deuce a single deciding point is played."
    override val goldenPoint = "Golden point"
    override val goldenPointHint = "At deuce a single point decides the game. Turn it off to play advantages."
    override val starPoint = "Star point"
    override val starPointHint = "FIP rule since 2026: at deuce at most two advantages are played; at the third deuce a single point decides the game. With golden point and star point both off, advantages are played."
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
    override val addressLookup = "Look up the court address"
    override val addressLookupHint = "Shown in the summary next to the coordinates. Uses the phone's geocoding service: with Google services the coordinates are sent to Google."
    override val bandsRequiredTitle = "Bluetooth and location required"
    override val bandsRequiredText = "To use the wristbands turn on Bluetooth, allow location and keep location on."
    override val bandsMissingTitle = "Wristbands not assigned"
    override val bandsMissingText: (List<String>) -> String = {
        (if (it.size > 1) "No wristbands for: " else "No wristband for: ") + it.joinToString(", ") + ". Continue anyway?"
    }
    override val remotesMissingTitle = "Remotes not assigned"
    override val remotesMissingText: (List<String>) -> String = {
        (if (it.size > 1) "Missing remotes for: " else "Missing remote for: ") + it.joinToString(", ") + ". Continue anyway?"
    }
    override val continueAnyway = "Continue"
    override val cancel = "Cancel"

    override val startMatch = "MATCH START"
    override val startHint = "Press the button to start"
    override val startHintBands = "Press the button or KEY1 on a wristband"
    override val startHintRemotes = "Press the button or a remote"
    override val startButton = "Start match"
    override val resumeSaved = "Resume suspended match"
    override val noSavedMatches = "No suspended match saved."
    override val savedMatchesTitle = "Suspended matches"
    override val delete = "Delete"
    override val deleteSavedTitle = "Delete the suspended match?"
    override val deleteSavedText: (String) -> String = { "The match $it can no longer be resumed." }
    override val vs = "vs"

    override val matchTime = "Match Time"
    override val setsHeader = "SETS"
    override val gamesHeader = "GAMES"
    override val undoPoint = "Undo point"
    override val lockScreen = "Lock screen"
    override val lockedTitle = "Screen locked"
    override val unlockHint = "Press and hold the lock to unlock. Wristbands and remotes keep working."
    override val suspend = "Suspend"
    override val resume = "Resume"
    override val audioOn = "Audio On"
    override val audioOff = "Audio Off"
    override val newMatch = "New match"
    override val suspendedOverlay = "MATCH SUSPENDED"
    override val newMatchConfirmTitle = "New match?"
    override val newMatchConfirmText = "The current match stays saved among suspended matches and can be resumed."
    override val endDialogTitle = "Game, set and match"
    override val endDialogText: (String, String, Boolean) -> String = { name, score, team -> "$name ${if (team) "win" else "wins"}\n$score" }
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
    override val msgSecondServe = "SECOND SERVE"
    override val msgFaultUndone = "FAULT UNDONE"
    override val msgSuspended = "MATCH SUSPENDED"
    override val msgResumed = "MATCH RESUMED"
    override val msgBandConnected: (String) -> String = { "WRISTBAND $it CONNECTED" }
    override val msgBandLost: (String) -> String = { "WRISTBAND $it DISCONNECTED" }
    override val msgRemoteConnected: (String) -> String = { "REMOTE $it CONNECTED" }
    override val msgRemoteLost: (String) -> String = { "REMOTE $it DISCONNECTED" }
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
    override val notifTvOnly = "TV scoreboard on"

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
    override val saveHistory = "Save files"
    override val historyTitle = "History"
    override val historySubtitle = "Finished matches saved on this phone"
    override val historyImport = "Import"
    override val historyMatches = "Matches"
    override val historyPlayers = "Players"
    override val historyEmpty = "No finished matches yet. Every match you finish lands here by itself; with “Import” you add matches saved as JSON on another phone."
    override val historyPlayersEmpty = "Players show up here when you finish a match with their names written in New match."
    override val historyRecord: (Int, Int) -> String = { w, l -> "Won $w · lost $l" }
    override val historyHeadToHead = "Head to head"
    override val historyDeleteTitle = "Delete the match?"
    override val historyDeleteText = "The match disappears from this phone's history. Files already saved or shared stay."
    override val historyImported: (Int, Int) -> String = { d, o -> "Imported: $d into history, $o into suspended matches" }
    override val historyImportFailed = "No match imported: pick JSON files saved by the app."
    override val transferTitle = "Transfer"
    override val transferHint = "Move history and suspended matches to another phone with a QR code. Both phones must be on the same Wi-Fi network, or one connected to the other's hotspot. On the new phone the matches are added to the ones it already has."
    override val transferSend = "Send"
    override val transferReceive = "Receive"
    override val transferShowQr = "On the other phone open History › Transfer › Receive and scan this code. Keep this window open until it has finished."
    override val transferSent = "Sent to the other phone. You can close."
    override val transferClose = "Close"
    override val transferScanPrompt = "Scan the other phone's code"
    override val transferNoNetwork = "This phone is not on a Wi-Fi network or hotspot: connect both phones to the same network and try again."
    override val transferBadCode = "This is not a Tennis Score Manager code."
    override val transferFailed = "Transfer failed: are both phones on the same network? On the other one the window with the code must stay open."
    override val transferEmpty = "The other phone has no matches to send."
    override val photoHint = "The photo stays on this phone and is found again from the player's name; it moves to another phone with the history transfer."
    override val photoChange = "Change photo"
    override val photoRemove = "Remove photo"
    override val photoNeedsName = "Write the player's name first: the photo is found again from the name."
    override val photoError = "This photo can't be read."
    override val matchPhotosTitle = "Match photos"
    override val matchPhotosHint = "Up to 10 souvenir photos: they stay on this phone, go into the PDF and move to the other phone with the transfer."
    override val addPhotos = "Add photos"
    override val takePhoto = "Take photo"
    override val matchPhotosFull = "This match already has 10 photos."
    override val matchNotesTitle = "Notes"
    override val ratingTitle = "TSM rating"
    override val ratingHint = "Worked out on the phone from the finished matches in the history: it goes up when you win, more against higher-rated players and with a clear score. In doubles the pair's average counts. Friendlies don't count."
    override val ratingProvisional: (Int) -> String = { "provisional: $it more matches" }
    override val ratingMatches: (Int, Int) -> String = { a, b -> "$a matches, $b won" }
    override val ratingLast: (String) -> String = { "Last match: $it" }
    override val ratingRanking = "TSM ranking"
    override val ratingNone = "No match counts for the rating yet."
    override val ratingFriendly = "Friendly"
    override val ratingFriendlyHint = "Doesn't count for the TSM rating"
    override val ratingInMatch = "TSM rating after this match"
    override val ratingNotCounted = "This match doesn't count for the TSM rating."
    override val ratingShowAll = "Show all"
    override val ratingShowLess = "Show less"
    override val rematchTitle = "Rematch"
    override val rematchHint = "Suggest another match: an invite ready to send on WhatsApp or wherever you like, the date in your calendar and the same match ready in New match."
    override val rematchAsk = "Rematch?"
    override val rematchVersus = "vs"
    override val rematchLast: (String, String) -> String = { a, b -> "Last time: $a to $b" }
    override val rematchWhen = "When"
    override val rematchPlace = "Where"
    override val rematchNote = "Note (optional)"
    override val rematchNoteHint = "e.g. court already booked, I'll bring the balls"
    override val rematchSend = "Send the invite"
    override val rematchCalendar = "Add to calendar"
    override val rematchPrepare = "Set up the match"
    override val rematchEvent: (String) -> String = { "Rematch: $it" }
    override val rematchNoCalendar = "No calendar app on this phone."
    override val rematchPreview = "The message"
    override val serveTitle = "Serve practice"
    override val serveHint = "Phone on the tripod beside the server, wristband on the wrist: KEY1 in, KEY2 out, KEY1 held down undoes the last one. Every serve becomes a clip to watch again."
    override val serveStart = "Start"
    override val serveNoBand = "No wristband connected: mark in and out with the buttons on the screen."
    override val serveBandOk: (String) -> String = { "Wristband $it: KEY1 in, KEY2 out, KEY1 held down undoes" }
    override val serveSideDeuce = "Deuce side"
    override val serveSideAd = "Ad side"
    override val serveFirst = "First"
    override val serveSecond = "Second"
    override val serveTargetT = "Down the T"
    override val serveTargetBody = "Body"
    override val serveTargetWide = "Wide"
    override val serveTargetNone = "None"
    override val serveIn = "In"
    override val serveOut = "Out"
    override val serveUndo = "Undo the last one"
    override val serveSayIn: (Int, Int) -> String = { a, b -> "In. $a of $b" }
    override val serveSayOut: (Int, Int) -> String = { a, b -> "Out. $a of $b in" }
    override val serveSayUndo = "Undone"
    override val serveWaiting = "Serve: the wristband recognises the serve"
    override val serveDetected = "Serve recognised: in or out?"
    override val serveEnd = "Done"
    override val serveSummary = "Practice summary"
    override val serveCountLine: (Int, Int) -> String = { a, b -> "$a of $b in" }
    override val serveBySide = "By side"
    override val serveByKind = "First and second"
    override val serveByTarget = "By target"
    override val serveWrist: (Int, Int) -> String = { a, b -> "Wrist: on average $a °/s on serves in, $b °/s on serves out" }
    override val servePlayAll = "Watch all"
    override val servePlayIn = "Watch only the ins"
    override val servePlayOut = "Watch only the outs"
    override val serveSessions = "Sessions"
    override val serveDelete = "Delete"
    override val serveDeleteAsk = "Delete the session and its clips?"
    override val servePlaceTitle = "Where to put the phone"
    override val servePlaceText = "• Beside the server, on the hitting-arm side (right for a right-hander), 3-4 m away, level with the baseline or half a metre behind.\n• Lens 1-1.2 m above the ground, phone in landscape and level, pointed at the server.\n• The whole person must be in the frame, from the feet to the racket at the top (about 2.7 m): if the racket head goes out, move the phone back or use the wide lens.\n• To also see where the ball goes: 3-4 m behind the server, a little to the side, 1.5-2 m high.\n• Never in front of the server or in the ball's path."
    override val servePlaceShow = "Where to put the phone?"
    override val serveNoClips = "No clips: without the camera only ins and outs are counted."
    override val servePlaceServer = "server"
    override val servePlacePhone = "phone"
    override val servePlaceAlt = "alternative"
    override val poseTitle = "Movement analysis"
    override val poseHint = "The app finds the body position in the clips, all on the phone, and measures three moments: loading, deepest knee bend and contact. They are estimates from a side view: they are most useful to compare your serves in with your serves out."
    override val poseAnalyzeAll = "Analyse the serves"
    override val poseAnalyzing: (Int, Int) -> String = { a, b -> "Analysing serve $a of $b…" }
    override val poseNoBody = "Body not recognised: frame the whole person, from the side, in good light."
    override val poseUnreliable = "Unreliable measurements: the body is hard to see"
    override val poseTrophy = "Loading"
    override val poseKnee = "Knees"
    override val poseContact = "Contact"
    override val poseKneeMetric = "Deepest knee bend"
    override val poseElbowMetric = "Elbow at contact"
    override val poseHeightMetric = "Contact height"
    override val poseTossMetric = "Tossing arm (from vertical)"
    override val poseTiltMetric = "Shoulders at loading"
    override val poseTimingMetric = "From loading to contact"
    override val poseHeightValue: (String) -> String = { "$it × body height" }
    override val poseInVsOut = "Ins and outs compared"
    override val poseServe: (Int) -> String = { "Serve $it" }
    override val poseNoClip = "No clip"
    override val poseDone = "Analysis done"
    override val poseModelTitle = "Model to recognise the body"
    override val poseModelHint = "The analysis needs Google's MoveNet model (7 MB, Apache 2.0 licence). It is downloaded once from the project's GitHub page and stays on the phone; the clips never leave the phone."
    override val poseModelDownload = "Download the model (7 MB)"
    override val poseModelDownloading: (Int) -> String = { "Downloading the model… $it%" }
    override val poseModelFailed = "Download failed: check the connection and try again."
    override val poseModelBad = "The downloaded file is not the expected one and was not used: try again later."
    override val poseModelDelete = "Delete the model (7 MB)"
    override val poseUnsupported = "Movement analysis isn't available on this device (x86 processor)."
    override val poseWatch = "Watch the video with the skeleton"
    override val poseManyPeople: (Int) -> String = { "$it people in the frame: the hitter was followed (check in the images)." }
    override val poseZoomHint = "Tap an image to enlarge it; two fingers to zoom, double tap to go back."
    override val poseRedo = "Analyse again"
    override val strokeTrainingTitle = "Stroke practice"
    override val strokeFolderName = "Stroke practice"
    override val documentsFolder = "Documents"
    override val strokeAllSessions: (Int) -> String = { n -> "All sessions ($n)" }
    override val strokeSessionsTitle: (String) -> String = { "Sessions · $it" }
    override val strokeSessionsWhere: (String) -> String = { "Videos and analysis in $it" }
    override val strokeSpace: (String, Int) -> String = { mb, n -> "$n sessions · videos: $mb" }
    override val strokeSelectAll = "Select all"
    override val strokeSelectHint = "Press and hold a session to select it, then delete the sessions or only their videos."
    override val strokeDeleteN: (Int) -> String = { n -> "Delete ($n)" }
    override val strokeVideosN: (Int) -> String = { n -> "Videos only ($n)" }
    override val strokeDeleteAsk: (Int) -> String = { n -> "Delete $n sessions with their videos? This cannot be undone." }
    override val strokeVideosAsk: (Int) -> String = { n -> "Delete the videos of $n sessions? Counts, analysis measurements and key-moment pictures stay." }
    override val strokeNoVideos = "videos deleted"
    override val strokeAnalysed = "analysed"
    override val strokeTrend = "Shots in, session after session"
    override val strokeNone = "No sessions of this stroke yet."
    override val progressStrokes = "Stroke practice"
    override val progressStrokesHint = "Percentage of shots in, session after session."
    override val strokeTrainingHint = "Phone on the tripod, wristband on the wrist: choose the stroke, then KEY1 in, KEY2 out, KEY1 held down undoes the last one. Every shot becomes a clip to watch again and analyse."
    override val dirCross = "Cross-court"
    override val dirMiddle = "Middle"
    override val dirLine = "Down the line"
    override val dirNone = "None"
    override val wingFh = "Forehand"
    override val wingBh = "Backhand"
    override val byDirection = "By direction"
    override val byWing = "Forehand and backhand"
    override val strokeWaiting = "Hit: the wristband recognises the shot"
    override val strokeDetected = "Shot recognised: in or out?"
    override val placeHitter = "hitter"
    override val placeGroundText = "• Beside the hitter, at right angles to the direction of the shots, 4-5 m away: outside the alley, level with where the ball is hit (usually a step behind the baseline).\n• Lens about 1 m above the ground, phone in landscape and level.\n• The whole swing must be in the frame: from preparation to finish the wrist moves about 2 m back and forth; if it goes out, move the phone back or use the wide lens.\n• Keep it on the same side for the whole session, so the shots can be compared.\n• Never in the ball's path: off the court, protected by the fence or a ball cart."
    override val placeVolleyText = "• Beside the net player, 3-4 m away, outside the alley, level with the player (about 2 m from the net).\n• Lens about 1 m above the ground, phone in landscape and level.\n• The whole player in the frame, with room for the step forward towards the net.\n• Never in the ball's path."
    override val posePrep = "Preparation"
    override val poseFinish = "Finish"
    override val poseInFrontMetric = "Contact in front of the body"
    override val poseBackswingMetric = "Preparation size"
    override val poseFinishMetric = "Finish height"
    override val poseTimingGround = "From preparation to contact"
    override val poseHintGround = "The app finds the body position in the clips, all on the phone, and measures three moments: preparation (wrist furthest back), contact and finish. They are estimates from a side view: most useful to compare your shots in with your shots out."
    override val poseAnalyzeShots = "Analyse the shots"
    override val poseAnalyzingShot: (Int, Int) -> String = { a, b -> "Analysing shot $a of $b…" }
    override val tabPlay = "Play"
    override val tabTrain = "Train"
    override val tabVideo = "Video"
    override val tabProgress = "Progress"
    override val tabMore = "More"
    override val playNewMatchHint = "Who plays, rules and how you keep score"
    override val playRematch: (String) -> String = { "Rematch: $it" }
    override val playResume: (String) -> String = { "Resume: $it" }
    override val playTournamentHint = "Americano or Mexicano, with rotating pairs"
    override val playUseAs = "Use this phone as"
    override val playAsBoardHint = "The screen becomes a match scoreboard"
    override val playAsCamera = "Camera phone"
    override val playAsCameraHint = "Films the match with the umpire's score"
    override val trainCardio = "Workout with the strap"
    override val trainCardioHint = "Heart rate, laps, HRV and recovery"
    override val videoRecord = "Record with this phone"
    override val videoRecordHint = "Record a video, even without an umpire"
    override val videoLibrary = "Your videos"
    override val videoLibraryHint = "Point-by-point chapters, highlights, filters, import"
    override val progressHistoryHint = "Finished matches: summaries, PDF, players"
    override val progressTrendHint = "TSM rating, ranking and comparison over time"
    override val progressRacketsHint = "Statistics and feel per racket"
    override val moreDevices = "Devices"
    override val moreDevicesHint = "Wristbands, straps, remotes, second phone, TV scoreboard"
    override val moreVoice = "Language and voice"
    override val moreVoiceHint = "App language, voice that calls the score"
    override val moreRacketsHint = "Strings, tensions and restringing"
    override val moreTransfer = "Transfer matches"
    override val moreTransferHint = "To another phone, with a code"
    override val statusBands: (Int) -> String = { "Wristbands $it/2" }
    override val statusStraps: (Int) -> String = { "Straps $it" }
    override val statusCamera = "Camera connected"
    override val statusTv = "TV scoreboard"
    override val statusNone = "No device connected"
    override val playTournament = "Friends tournament"
    override val step1Hint = "Step 1 of 2 · Who plays. All optional: you can go on even without names."
    override val step2Hint = "Step 2 of 2 · How you keep score and the match rules."
    override val moreDetails = "More details"
    override val moreDetailsHint = "Club, court and tournament, if you like"
    override val recentPlayers = "Recent players"
    override val onbWelcome = "Welcome to Tennis Score Manager"
    override val onbLang = "Choose the language"
    override val onbUseTitle = "How will you use TSM?"
    override val onbUseReferee = "I keep score of a match"
    override val onbUseRefereeHint = "On the phone, as umpire or while playing"
    override val onbUseBands = "I play with the wristbands"
    override val onbUseBandsHint = "Each player scores points from the wrist"
    override val onbUseTrain = "I train"
    override val onbUseTrainHint = "Heart-rate strap, serve, forehand, backhand, volley"
    override val onbUseVideo = "I film matches"
    override val onbUseVideoHint = "Videos, highlights and replay"
    override val onbNameTitle = "What's your name?"
    override val onbNameHint = "It's used for your progress and TSM rating. You can leave it empty and add it later in More › You."
    override val onbSkip = "Skip"
    override val onbStart = "Start"
    override val moreProfile = "You"
    override val moreProfileHint = "Your name, for progress and rating"
    override val moreTour = "App introduction"
    override val moreTourHint = "The first questions, again"
    override val profileName = "Your name"
    override val matchNotesHint = "Write your notes on the match here: they go into the PDF, the shared text and the saved files."
    override val formatPdf = "Full PDF (result, statistics, log, photos)"
    override val shareChoose = "What do you share?"
    override val shareChooseHint = "The image with text is right for WhatsApp and social media; the PDF also has statistics, the log and the match photos."
    override val shareImage = "Image"
    override val sharePdf = "Full PDF"
    override val tournamentButton = "Americano or Mexicano tournament"
    override val tournamentTitle = "Tournament"
    override val tournamentSubtitle = "Americano and Mexicano: partners change every round"
    override val tournamentNew = "New tournament"
    override val tournamentAmericano = "Americano"
    override val tournamentMexicano = "Mexicano"
    override val tournamentPlayersCount: (Int) -> String = { n -> "$n players" }
    override val tournamentRound: (Int) -> String = { n -> "Round $n" }
    override val tournamentFinished = "Finished"
    override val tournamentKindTitle = "Tournament type"
    override val tournamentName = "Tournament name (optional)"
    override val tournamentAmericanoHint = "Partners change every round: everyone plays with and against as many people as possible."
    override val tournamentMexicanoHint = "First round as in the Americano, then by standings: on the same court 1st and 4th against 2nd and 3rd."
    override val tournamentPlayers = "Players"
    override val tournamentPlayersHint = "From 4 to 16, each with their own name: the standings are individual. Players with a photo in the history get it here too."
    override val tournamentAddPlayer = "Add player"
    override val tournamentNeedPlayers = "At least 4 players are needed, all with different names."
    override val tournamentCourts = "Courts"
    override val tournamentCourtsHint: (Int, Int) -> String = { a, b -> "Matches per round: $a · sitting out each round: $b" }
    override val tournamentScoring = "Scoring"
    override val tournamentScoringPoints = "By points"
    override val tournamentScoringTime = "By time"
    override val tournamentMinutesN: (Int) -> String = { n -> "$n minutes" }
    override val tournamentPointsN: (Int) -> String = { n -> "$n points" }
    override val tournamentTimeHint = "Play for the chosen minutes, then write the result (or count it point by point with the timer)."
    override val tournamentPointsHint = "Every rally is a point and each player serves 4 times in turn; the match ends when the two pairs' points add up to the total (e.g. 13-11 at 24)."
    override val tournamentSuggested: (Int) -> String = { n -> "Suggested rounds: $n (everyone partners everyone once)." }
    override val tournamentCreate = "Start the tournament"
    override val tournamentShare = "Share standings"
    override val tournamentNextRound = "Next round"
    override val tournamentRounds = "Rounds"
    override val tournamentStandings = "Standings"
    override val tournamentResting = "Sitting out"
    override val tournamentFinish = "Finish tournament"
    override val tournamentMissingResults = "Some results of this round are missing: write them before moving on."
    override val tournamentFinishText = "The standings stay as they are and the tournament moves to the finished ones."
    override val tournamentDeleteTitle = "Delete the tournament?"
    override val tournamentDeleteText = "The tournament and its standings disappear from this phone."
    override val tournamentCourt: (Int) -> String = { n -> "Court $n" }
    override val tournamentScoreHint: (Int) -> String = { n -> "The two scores must add up to $n." }
    override val tournamentCount = "Count point by point"
    override val tournamentStartTimer = "Start the timer"
    override val tournamentTimeUp = "Time is up: finish the rally and save the result."
    override val tournamentMatchOver = "Match over: save the result."
    override val tournamentSave = "Save result"
    override val tournamentPointsShort = "Pts"
    override val tournamentPlayedShort = "P"
    override val tournamentWonShort = "W"
    override val tournamentStandingsHint = "Points = points scored by your pair in all matches; ties are broken by wins (W) and point difference. P = matches played."
    override val tournamentRoundsPlayed: (Int) -> String = { n -> "Rounds played: $n" }
    override val share = "Share"
    override val saveDialogTitle = "Save files"
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
    override val shareSubjectPadel = "Padel match result"
    override val playerDefault: (Int) -> String = { "Player $it" }
    override val teamJoiner = " and "
    override val generatedWith = "Made with Tennis Score Manager"

    override val tvSet = "SETS"
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
    override val tvPageTitle = "TSM Scoreboard"

    override val tvSection = "TV scoreboard"
    override val tvEnable = "Scoreboard on a TV or monitor"
    override val tvEnableHint = "Another phone (or a computer, or a Chromecast) shows the live score on a monitor. The phones must be on the same network: one phone's hotspot."
    override val tvAddress = "Scoreboard address"
    override val tvNoNetwork = "No network: turn on the hotspot on one phone and connect the other."
    override val tvScreens: (Int) -> String = { if (it == 0) "No scoreboard connected" else if (it == 1) "1 scoreboard connected" else "$it scoreboards connected" }
    override val tvQrHint = "On the other phone: open Tennis Score Manager and tap «Use as scoreboard» (it connects by itself), or scan the code with the camera and open it in the browser."
    override val tvLook = "Scoreboard look"
    override val tvTitle = "Bottom line"
    override val tvTitleHint: (String) -> String = { if (it.isEmpty()) "Empty: no text" else "Empty: «$it» (club and court from New match)" }
    override val tvColorOf: (String) -> String = { "Colour of $it" }
    override val tvShowClock = "Match time"
    override val tvShowTimers = "Serve clock and breaks"
    override val tvShowSets = "Finished sets"
    override val tvShowMessages = "Messages (break point, set point...)"
    override val tvShowServe = "Ball next to the server"
    override val tvGhost = "Unlit segments visible"
    override val tvPreview = "Preview on this phone"
    override val tvChromecastHint = "With a Chromecast: on the scoreboard phone use the «Cast» tile in Quick Settings («Screen Cast» up to Android 14, «Smart View» on Samsung). The Chromecast needs a network with internet: turn on mobile data on the hotspot phone."

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

    // Racchetta virtuale
    override val racketTitle = "Rackets"
    override val racketHint = "Compare stats and feel across different rackets, strings or tensions."
    override val racketMine = "My rackets"
    override val racketCompare = "Comparison"
    override val racketAdd = "Add racket"
    override val racketNew = "New racket"
    override val racketEdit = "Edit"
    override val racketNone = "None"
    override val racketEmpty = "No rackets yet. Add your rackets with strings and tension, then pick the one you use before each match or workout."
    override val racketField = "Racket"
    override val racketSetupHint = "Pick each player's racket: the match will remember today's racket, strings and tension."
    override val racketCatalog = "From catalogue"
    override val racketCatalogHint = "Manufacturer specs, approximate: correct them if you measured your racket."
    override val racketName = "Nickname (A, B…)"
    override val racketBrand = "Brand"
    override val racketModel = "Model"
    override val racketWeight = "Weight (g)"
    override val racketBalance = "Balance (mm)"
    override val racketHead = "Head size (cm²)"
    override val racketPattern = "String pattern"
    override val racketOwner = "Owner (name as in New match)"
    override val racketCheckHours = "Check strings after (hours)"
    override val racketRetired = "Retired"
    override val racketRetiredHint = "No longer offered before playing, but kept in comparisons."
    override val racketDeleteTitle = "Delete the racket?"
    override val racketDeleteText = "Matches and workouts already played keep a copy of it and stay in the comparison."
    override val stringTitle = "Stringing"
    override val stringNew = "New stringing"
    override val stringHistory = "Stringing history"
    override val stringNone = "No stringing recorded."
    override val stringType = "String type"
    override val stringTypeName: (Int) -> String = { listOf("Polyester", "Multifilament", "Synthetic gut", "Natural gut", "Hybrid").getOrElse(it) { "" } }
    override val stringCatalog = "Common strings"
    override val stringCross = "Cross string"
    override val stringGauge = "Gauge (mm)"
    override val stringMains = "Mains (kg)"
    override val stringCrosses = "Crosses (kg)"
    override val stringDate = "Stringing date"
    override val stringNotes = "Notes"
    override val stringHours: (String, Int) -> String = { h, max -> "$h h played since the last stringing · check at $max h" }
    override val stringCheck = "Check hours reached: have a look at the strings."
    override val stringCheckNote = "Hours come from the matches and workouts linked to the racket. From time alone the app cannot tell how much tension is left."
    override val stringDeleteTitle = "Delete the stringing?"
    override val racketHours = "Hours played"
    override val racketSessions: (Int) -> String = { n -> if (n == 1) "1 session" else "$n sessions" }
    override val compareByRacket = "By racket"
    override val compareByString = "By stringing"
    override val compareA = "Setup A"
    override val compareB = "Setup B"
    override val compareTooFew = "To compare you need matches or workouts with at least two different setups. Pick the racket in New match or in the workout, or link it from the summary."
    override val compareNote = "The numbers show how each setup went, not which one is best: opponent, surface, conditions and form matter too."
    override val compareSessions = "Sessions"
    override val comparePerMatch: (String) -> String = { "$it per match" }
    override val compareStrokes = "Wristband strokes"
    override val compareRated: (Int) -> String = { n -> if (n == 1) "feel from 1 session" else "feel from $n sessions" }
    override val feelTitle = "Feel"
    override val feelHint = "How did it feel? From 1 (low) to 5 (high); tap again to clear."
    override val feelControl = "Control"
    override val feelComfort = "Comfort"
    override val feelPower = "Power"
    override val feelFatigue = "Fatigue"
    override val racketUsed = "Racket used"

    override val aboutLicense: (String) -> String = { "Tennis Score Manager $it · free software under the GNU GPL version 3 or later, with absolutely no warranty." }
    override val aboutSource = "Source code"
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
