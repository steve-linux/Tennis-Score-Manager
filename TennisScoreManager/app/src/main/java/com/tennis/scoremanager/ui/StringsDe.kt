// SPDX-FileCopyrightText: 2026 Stefano Spagnolo
// SPDX-License-Identifier: GPL-3.0-or-later

package com.tennis.scoremanager.ui

import com.tennis.scoremanager.ble.BandProtocol

/** Deutsch (DTB-Begriffe: Stuhlschiedsrichter, Satzball, Seitenwechsel). */
object DeStrings : Strings {
    override val playerTag: (Int) -> String = { "S$it" }
    override val datePattern = "EEEE, d. MMMM yyyy"

    override val setupTitle = "Neues Match"
    override val setupSubtitle = "Einrichtung optional: Du kannst alles leer lassen und weitermachen."
    override val clubSection = "Verein und Platz"
    override val clubName = "Name des Tennisvereins"
    override val courtNumber = "Platznummer"
    override val singles = "Einzel"
    override val doubles = "Doppel"
    override val doublesHint = "Im Doppel zwei Namen pro Team eingeben: Die Zählung folgt den ITF-Regeln für das Doppel."
    override val player1 = "Spieler 1"
    override val player2 = "Spieler 2"
    override val playerName = "Name"
    override val clearFields = "Felder leeren"
    override val next = "Weiter"
    override val back = "Zurück"

    override val optionsTitle = "Modus und Regeln"
    override val modeSection = "Spielmodus"
    override val modeReferee = "Schiedsrichter"
    override val modeBands = "Armbänder"
    override val modeRefereeHint = "Die Punkte werden am Telefon vergeben, wie vom Stuhlschiedsrichter."
    override val modeBandsHint = "Jeder Spieler vergibt den Punkt mit KEY1 an seinem eigenen M5StickS3."
    override val requirements = "Voraussetzungen"
    override val bluetooth = "Bluetooth"
    override val location = "Standortberechtigung"
    override val locationServices = "Standort aktiviert"
    override val enable = "Einschalten"
    override val allow = "Erlauben"
    override val ok = "OK"
    override val bandFor: (String) -> String = { "Armband von $it" }
    override val noBand = "Keins"
    override val bandConnected = "Verbunden"
    override val bandConnecting = "Verbinde…"
    override val bandIdle = "Nicht verbunden"
    override val bandOff = "Aus"
    override val battery = "Akku"
    override val bandCharging: (Int) -> String = { "Lädt $it %" }
    override val bandChargeFull = "Voll geladen"
    override val autoSearch = "Automatische Suche: Schalte die Armbänder ein (Seitentaste), sie koppeln sich von selbst."
    override val autoSearchOff = "Die Suche startet, sobald die Voraussetzungen oben erfüllt sind."
    override val identify = "Erkennen"
    override val swapBands = "S1 ↔ S2 tauschen"
    override val bandsOffAtEnd = "Armbänder am Matchende und beim Beenden ausschalten"
    override val bandsOffAtEndHint = "Ein Klick auf die Seitentaste schaltet sie wieder ein."
    override val bandNotReady = "Armband nicht verbunden"

    override val bandSettings = "Armband-Einstellungen"
    override val settingsShort = "Einstellungen"
    override val bandSettingsNeedLink = "Verbinde das Armband, um seine Einstellungen zu sehen und zu ändern."
    override val bandFirmwareOld = "Die Firmware dieses Armbands hat keine Einstellungen: Lade TSM_Band.ino 2.0 hoch."
    override val bandFirmware: (String) -> String = { "Firmware $it" }
    override val bandName = "Name"
    override val brightness = "Displayhelligkeit"
    override val scoreTime = "Spielstand nach jedem Punkt sichtbar"
    override val scoreTimeHint = "Die Zusammenfassung am Spielende bleibt 2 Sekunden länger."
    override val beeperVolume = "Lautstärke des Piepsers"
    override val mute = "Stumm"
    override val off = "Aus"
    override val flipDisplay = "Display gedreht"
    override val flipDisplayHint = "Um das Armband am anderen Handgelenk zu tragen."
    override val autoOff = "Automatisches Ausschalten"
    override val pairTimeout = "Beim Einschalten, wenn sich kein Telefon verbindet"
    override val lostTimeout = "Wenn die Verbindung zum Telefon verloren geht"
    override val idleTimeout = "Wenn es verbunden, aber inaktiv bleibt"
    override val estimateFull: (String) -> String = { "Geschätzte Laufzeit $it bei voller Ladung" }
    override val estimateNow: (String, Int) -> String = { h, p -> "$h mit der aktuellen Ladung ($p %)" }
    override val estimateBreakdown: (String, String, String, String) -> String = { tot, base, dsp, snd ->
        "Mittlerer Verbrauch $tot mA: Platine und Bluetooth $base · Display $dsp · Piepser $snd"
    }
    override val estimateMeasured = "Grundverbrauch an diesem Armband während der Nutzung gemessen."
    override val estimateTheory = "Theoretische Schätzung: Nach 20 Minuten Nutzung wird sie mit dem gemessenen Verbrauch korrigiert."
    override val copyToOther = "Auf das andere Armband kopieren"
    override val powerOff = "Ausschalten"
    override val bandPowerOffTitle = "Armband ausschalten?"
    override val bandPowerOffText: (String) -> String = { "$it schaltet sich sofort aus. Zum Einschalten: ein Klick auf die Seitentaste." }
    override val languageSection = "Sprache"
    override val languageHint = "Gilt für die Bildschirme, die Stimme des Schiedsrichters, die TV-Anzeigetafel und die Armbänder."
    override val audioSection = "Audio und Stimme"
    override val voiceCalls = "Ansagen des Schiedsrichters"
    override val voiceFiles: (Int, Int) -> String = { n, tot -> "Erzeugte Dateien: $n/$tot" }
    override val voiceFilesHint = "Alles funktioniert ohne Internet mit den auf dem Telefon installierten Stimmen. Eigene Aufnahmen (ZIP) haben immer Vorrang vor der Sprachausgabe."
    override val generateVoice = "Dateien erzeugen"
    override val generating: (Int, Int) -> String = { n, tot -> "Erzeuge $n/$tot…" }
    override val voiceGenerationFailed = "Erzeugung nicht abgeschlossen: Die bisherigen Sprachdateien bleiben unverändert."
    override val importVoiceZip = "ZIP importieren"
    override val voiceImportFailed = "ZIP nicht lesbar oder unvollständig: Keine Aufnahme wurde geändert."
    override val deleteCustomVoice = "Aufnahmen entfernen"
    override val deleteCustomConfirmTitle = "Aufnahmen entfernen?"
    override val deleteCustomConfirmText: (Int) -> String = { n -> "Die eigenen deutschen Aufnahmen ($n) werden vom Telefon gelöscht. Um sie zurückzubekommen, importiere das ZIP erneut." }
    override val ttsEngine = "Sprachausgabe-Engine"
    override val engineDefault = "Standard des Telefons"
    override val ttsVoice = "Stimme"
    override val voiceAuto = "Automatisch (beste offline)"
    override val voiceName: (String) -> String = { "Stimme $it" }
    override val online = "online"
    override val offline = "offline"
    override val voiceFilesMode = "Vorab erzeugte Audiodateien verwenden"
    override val voiceFilesModeHint = "Normalerweise wird jede Ansage in einem Satz gesprochen (natürlicher). Schalte es ein, um die erzeugten Dateien abzuspielen, z. B. um eine Online-Stimme offline mitzunehmen."
    override val customRecordings: (Int, Int) -> String = { n, tot -> "Eigene Aufnahmen: $n/$tot" }
    override val voiceReadme: (String, String) -> String = { langs, formats ->
        "TENNIS SCORE MANAGER - SPRACHDATEIEN\n" +
            "Lege die Aufnahmen in voice/<sprache>/ ($langs) ab, benannt nach dem Schlüssel.\n" +
            "Formate: $formats. Die Spielernamen liest immer die Sprachausgabe.\n" +
            "Der Ordner tts/ enthält die von der App erzeugten Dateien: Deine Aufnahmen haben Vorrang."
    }
    override val voiceReadmeKey = "SCHLÜSSEL"
    override val testVoice = "Stimme testen"
    override val stopVoiceTest = "Test beenden"
    override val ttsMissing = "Die deutsche Stimme der Sprachausgabe ist auf dem Telefon nicht installiert."
    override val installVoice = "Stimme installieren"
    override val ttsEngineError = "Sprachausgabe nicht verfügbar: Prüfe die Engine in den Android-Einstellungen."
    override val openTtsSettings = "Einstellungen öffnen"
    override val formatSection = "Matchformat"
    override val formatBestOfThree = "2 Gewinnsätze · Tie-Break bis 7"
    override val formatBestOfThreeHint = "Auf zwei Gewinnsätze, Tie-Break bei 6 beide in jedem Satz."
    override val formatMatchTiebreak = "2 Sätze + Match-Tie-Break bis 10"
    override val formatMatchTiebreakHint = "Bei 1:1 Sätzen entscheidet ein Match-Tie-Break bis 10 Punkte (2 Punkte Abstand)."
    override val noAd = "No-Ad (entscheidender Punkt)"
    override val noAdHint = "Bei Einstand wird ein einziger Punkt gespielt: Wer ihn gewinnt, gewinnt das Spiel."
    override val coinToss = "Wahl (Münzwurf)"
    override val tossCoin = "Münze werfen"
    override val tossWinner: (String) -> String = { "Wahl gewonnen: $it" }
    override val tossHint = "Wer die Wahl gewinnt, entscheidet: Aufschlag, Rückschlag oder Seite. Hier einstellen."
    override val serving = "Aufschlag"
    override val courtSides = "Platzseiten"
    override val umpireView = "Sicht vom Schiedsrichterstuhl"
    override val swapSides = "Seiten tauschen"
    override val firstServerOf: (String) -> String = { "Schlägt zuerst auf ($it)" }
    override val left = "Links"
    override val right = "Rechts"
    override val net = "NETZ"
    override val umpireChair = "Stuhlschiedsrichter"

    override val locationDialogTitle = "Standort einschalten"
    override val locationDialogText = "Ohne Standort kann der Ort nicht in der Match-Zusammenfassung stehen."
    override val continueWithout = "Ohne weiter"
    override val bandsRequiredTitle = "Bluetooth und Standort erforderlich"
    override val bandsRequiredText = "Für die Armbänder Bluetooth einschalten, die Standortberechtigung erteilen und den Standort eingeschaltet lassen."
    override val bandsMissingTitle = "Armbänder nicht zugeordnet"
    override val bandsMissingText: (List<String>) -> String = {
        (if (it.size > 1) "Es fehlen die Armbänder für: " else "Es fehlt das Armband für: ") + it.joinToString(", ") + ". Trotzdem fortfahren?"
    }
    override val continueAnyway = "Fortfahren"
    override val cancel = "Abbrechen"

    override val startMatch = "MATCHBEGINN"
    override val startHint = "Drücke die Taste, um zu beginnen"
    override val startHintBands = "Drücke die Taste oder KEY1 an einem Armband"
    override val startButton = "Match starten"
    override val resumeSaved = "Unterbrochenes Match fortsetzen"
    override val noSavedMatches = "Kein unterbrochenes Match gespeichert."
    override val savedMatchesTitle = "Unterbrochene Matches"
    override val delete = "Löschen"
    override val deleteSavedTitle = "Unterbrochenes Match löschen?"
    override val deleteSavedText: (String) -> String = { "Das Match $it kann danach nicht mehr fortgesetzt werden." }
    override val vs = "gegen"

    override val matchTime = "Match Time"
    override val setsHeader = "SÄTZE"
    override val gamesHeader = "SPIELE"
    override val undoPoint = "Punkt zurück"
    override val suspend = "Unterbrechen"
    override val resume = "Fortsetzen"
    override val audioOn = "Audio On"
    override val audioOff = "Audio Off"
    override val newMatch = "Neues Match"
    override val suspendedOverlay = "MATCH UNTERBROCHEN"
    override val newMatchConfirmTitle = "Neues Match?"
    override val newMatchConfirmText = "Das laufende Match bleibt bei den unterbrochenen Matches gespeichert und kann fortgesetzt werden."
    override val endDialogTitle = "Spiel, Satz und Sieg"
    override val endDialogText: (String, String, Boolean) -> String = { name, score, _ -> "Sieg für $name\n$score" }
    override val matchConcluded = "Match beendet"
    override val undoLastPoint = "Letzten Punkt zurücknehmen"
    override val serveOrderTitle: (Int) -> String = { "Aufschlagfolge · Satz $it" }
    override val whoServesFirst: (String) -> String = { "Wer schlägt bei $it zuerst auf?" }
    override val confirm = "Bestätigen"
    override val backDisabled = "Während des Matches «Neues Match» oder «Beenden» verwenden."
    override val exit = "Beenden"
    override val exitConfirmTitle = "App beenden?"
    override val exitConfirmText = "Das Match bleibt bei den unterbrochenen Matches gespeichert und kann fortgesetzt werden."
    override val exitConfirmBands = "Die Armbänder werden ausgeschaltet."

    override val msgChangeEnds = "SEITENWECHSEL"
    override val msgTiebreak = "TIE-BREAK"
    override val msgMatchTiebreak = "MATCH-TIE-BREAK"
    override val msgSetWon: (String) -> String = { "SATZ $it" }
    override val msgSetPoint = "SATZBALL"
    override val msgMatchPoint = "MATCHBALL"
    override val msgBreakPoint = "BREAKBALL"
    override val msgDecidingPoint = "ENTSCHEIDENDER PUNKT"
    override val msgPointUndone = "PUNKT ZURÜCKGENOMMEN"
    override val msgSuspended = "MATCH UNTERBROCHEN"
    override val msgResumed = "MATCH FORTGESETZT"
    override val msgBandConnected: (String) -> String = { "ARMBAND $it VERBUNDEN" }
    override val msgBandLost: (String) -> String = { "ARMBAND $it GETRENNT" }
    override val msgBandOff: (String, Int?) -> String = { n, why ->
        "ARMBAND $n AUS" + when (why) {
            BandProtocol.OFF_IDLE -> " (INAKTIV)"
            BandProtocol.OFF_BATTERY -> " (AKKU LEER)"
            BandProtocol.OFF_TIMEOUT -> " (KEIN TELEFON)"
            else -> ""
        }
    }
    override val msgBandBatteryLow: (String, Int) -> String = { n, p -> "ARMBAND $n: AKKU $p %" }
    override val bandBatteryLow = "AKKU SCHWACH"
    override val autonomy: (String) -> String = { "noch ~$it" }
    override val bandsBattery = "Akku der Armbänder"

    override val notifChannel = "Laufendes Match"
    override val notifText: (Boolean, Boolean) -> String = { bands, tv ->
        "Match läuft · " + when {
            bands && tv -> "Armbänder und TV-Anzeigetafel aktiv"
            tv -> "TV-Anzeigetafel aktiv"
            else -> "Armbänder aktiv"
        }
    }
    override val notifTvOnly = "TV-Anzeigetafel aktiv"

    override val bandPaired = "GEKOPPELT MIT"
    override val bandPlay = "SPIELEN"
    override val bandChangeEnds = "SEITENWECHSEL"
    override val bandTiebreak = "TIE-BREAK"
    override val bandSet = "SATZ"
    override val bandSuspended = "UNTERBROCHEN"
    override val bandGameSetMatch = "SPIEL SATZ SIEG"
    override val bandMatchOver = "MATCH BEENDET"
    override val bandAppClosed = "APP BEENDET"
    override val bandOffFromApp = "AUSSCHALTEN"

    override val summaryTitle = "Match beendet"
    override val winner = "Sieger"
    override val duration = "Dauer"
    override val startTime = "Beginn"
    override val endTime = "Ende"
    override val date = "Datum"
    override val club = "Verein"
    override val court = "Platz"
    override val place = "Ort"
    override val placeUnavailable = "Standort nicht verfügbar"
    override val format = "Format"
    override val pointsWon = "Gewonnene Punkte"
    override val gamesWon = "Gewonnene Spiele"
    override val result = "Ergebnis"
    override val saveHistory = "Im Verlauf speichern"
    override val summaryLeaveTitle = "Zusammenfassung nicht gespeichert"
    override val summaryLeaveText = "Die Zusammenfassung des Matches wurde weder gespeichert noch geteilt: Danach ist sie nicht mehr abrufbar."
    override val share = "Teilen"
    override val saveDialogTitle = "Im Verlauf speichern"
    override val fileName = "Name"
    override val folder = "Ordner"
    override val chooseFolder = "Ordner wählen"
    override val defaultFolder = "App-Ordner (Standard)"
    override val formatReport = "Bericht (.txt)"
    override val formatData = "Matchdaten (.json)"
    override val formatImage = "Bild (.png)"
    override val save = "Speichern"
    override val savedTo: (String) -> String = { "Gespeichert in $it" }
    override val saveError = "Speichern fehlgeschlagen"
    override val shareSubject = "Ergebnis des Tennismatches"
    override val playerDefault: (Int) -> String = { "Spieler $it" }
    override val teamJoiner = " und "
    override val generatedWith = "Erstellt mit Tennis Score Manager"

    override val tvGames = "SPIELE"
    override val tvSet = "SÄTZE"
    override val tvSec = "SEK"
    override val tvServe = "AUFSCHLAG"
    override val tvChangeover = "SEITENWECHSEL"
    override val tvSetBreak = "SATZPAUSE"
    override val tvTiebreakBreak = "PAUSE"
    override val tvWaiting = "WARTEN AUF DAS MATCH"
    override val tvReady = "BEREIT ZUM SPIELEN"
    override val tvSuspended = "MATCH UNTERBROCHEN"
    override val tvWinner = "SIEGER"
    override val tvMatchTiebreak = "MATCH-TIE-BREAK"
    override val tvLost = "VERBINDUNG VERLOREN - NEUER VERSUCH..."
    override val tvFullscreen = "VOLLBILD"
    override val tvPageTitle = "TSM-Anzeigetafel"

    override val tvSection = "TV-Anzeigetafel"
    override val tvEnable = "Anzeigetafel auf TV oder Monitor"
    override val tvEnableHint = "Ein anderes Telefon (oder ein Computer oder ein Chromecast) zeigt den Spielstand live auf einem Monitor. Die Telefone müssen im selben Netz sein: im Hotspot eines der beiden."
    override val tvAddress = "Adresse der Anzeigetafel"
    override val tvNoNetwork = "Kein Netz: Schalte auf einem der beiden Telefone den Hotspot ein und verbinde das andere."
    override val tvScreens: (Int) -> String = { if (it == 0) "Keine Anzeigetafel verbunden" else if (it == 1) "1 Anzeigetafel verbunden" else "$it Anzeigetafeln verbunden" }
    override val tvQrHint = "Auf dem anderen Telefon: Tennis Score Manager öffnen und «Als Anzeigetafel verwenden» tippen (verbindet sich von selbst), oder den Code mit der Kamera scannen und im Browser öffnen."
    override val tvLook = "Aussehen der Anzeigetafel"
    override val tvTitle = "Text unten"
    override val tvTitleHint: (String) -> String = { if (it.isEmpty()) "Leer: kein Text" else "Leer: «$it» (Verein und Platz von Seite 1)" }
    override val tvColorOf: (String) -> String = { "Farbe von $it" }
    override val tvShowClock = "Matchdauer"
    override val tvShowTimers = "Aufschlaguhr und Pausen"
    override val tvShowSets = "Beendete Sätze"
    override val tvShowMessages = "Meldungen (Breakball, Satzball...)"
    override val tvShowServe = "Ball beim Aufschläger"
    override val tvGhost = "Ausgeschaltete Segmente sichtbar"
    override val tvPreview = "Vorschau auf diesem Telefon"
    override val tvChromecastHint = "Mit einem Chromecast: Auf dem Anzeigetafel-Telefon die Kachel «Streamen» in den Schnelleinstellungen verwenden («Bildschirm übertragen» bis Android 14, «Smart View» bei Samsung). Der Chromecast braucht ein Netz mit Internet: Schalte die mobilen Daten auf dem Hotspot-Telefon ein."

    override val displayMode = "Als Anzeigetafel verwenden"
    override val displayModeHint = "Dieses Telefon zeigt den Spielstand auf dem Monitor (HDMI-Kabel oder Chromecast)"
    override val displaySearching = "Suche das Telefon des Schiedsrichters…"
    override val displaySteps = "1. Schalte auf einem der beiden Telefone den Hotspot ein und verbinde das andere.\n2. Auf dem Telefon des Schiedsrichters: Seite 2 → «TV-Anzeigetafel» ein.\n3. Verbinde dieses Telefon mit dem Monitor (USB-C/HDMI-Kabel) oder übertrage den Bildschirm auf einen Chromecast."
    override val displayManual = "Adresse (z. B. 192.168.43.1:8080)"
    override val displayConnect = "Verbinden"
    override val displayNotFound = "Nicht gefunden. Prüfe, ob beide Telefone im selben Netz sind und die Anzeigetafel in der App des Schiedsrichters eingeschaltet ist."
    override val displayOnMonitor = "Die Anzeigetafel ist auf dem externen Monitor"
    override val displayShowHere = "Auch hier zeigen"
    override val displayBackAgain = "Zum Beenden noch einmal Zurück drücken"
    override val displayRetry = "Erneut suchen"
}
