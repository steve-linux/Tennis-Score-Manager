package com.tennis.scoremanager.ui

import com.tennis.scoremanager.ble.BandProtocol

/** "de Sinner", "d'Alcaraz". */
private fun de(name: String) = if (name.firstOrNull()?.lowercaseChar()?.let { it in "aeiouyhàâäéèêëîïôöûüœ" } == true) "d'$name" else "de $name"

/** Français (France, terminologie FFT). */
object FrStrings : Strings {
    override val playerTag: (Int) -> String = { "J$it" }
    override val datePattern = "EEEE d MMMM yyyy"

    override val setupTitle = "Nouveau match"
    override val setupSubtitle = "Configuration facultative : vous pouvez tout laisser vide et continuer."
    override val clubSection = "Club et court"
    override val clubName = "Nom du club de tennis"
    override val courtNumber = "Numéro du court"
    override val singles = "Simple"
    override val doubles = "Double"
    override val doublesHint = "En double, saisissez deux noms par équipe : le décompte suit les règles ITF du double."
    override val player1 = "Joueur 1"
    override val player2 = "Joueur 2"
    override val playerName = "Nom"
    override val clearFields = "Effacer les champs"
    override val next = "Suivant"
    override val back = "Retour"

    override val optionsTitle = "Mode et règles"
    override val modeSection = "Mode de jeu"
    override val modeReferee = "Arbitre"
    override val modeBands = "Bracelets"
    override val modeRefereeHint = "Les points se donnent sur le téléphone, comme le fait l'arbitre de chaise."
    override val modeBandsHint = "Chaque joueur marque le point avec KEY1 de son propre M5StickS3."
    override val requirements = "Prérequis"
    override val bluetooth = "Bluetooth"
    override val location = "Autorisation de localisation"
    override val locationServices = "Localisation activée"
    override val enable = "Activer"
    override val allow = "Autoriser"
    override val ok = "OK"
    override val bandFor: (String) -> String = { "Bracelet ${de(it)}" }
    override val noBand = "Aucun"
    override val bandConnected = "Connecté"
    override val bandConnecting = "Connexion…"
    override val bandIdle = "Non connecté"
    override val bandOff = "Éteint"
    override val battery = "Batterie"
    override val bandCharging: (Int) -> String = { "En charge $it %" }
    override val bandChargeFull = "Charge terminée"
    override val autoSearch = "Recherche automatique : allumez les bracelets (bouton latéral), ils s'associent tout seuls."
    override val autoSearchOff = "La recherche démarre dès que les prérequis ci-dessus sont remplis."
    override val identify = "Identifier"
    override val swapBands = "Échanger J1 ↔ J2"
    override val bandsOffAtEnd = "Éteindre les bracelets en fin de match et en quittant"
    override val bandsOffAtEndHint = "Un clic sur le bouton latéral les rallume."
    override val bandNotReady = "Bracelet non connecté"

    override val bandSettings = "Réglages du bracelet"
    override val settingsShort = "Réglages"
    override val bandSettingsNeedLink = "Connectez le bracelet pour voir et modifier ses réglages."
    override val bandFirmwareOld = "Le firmware de ce bracelet n'a pas de réglages : chargez TSM_Band.ino 2.0."
    override val bandFirmware: (String) -> String = { "Firmware $it" }
    override val bandName = "Nom"
    override val brightness = "Luminosité de l'écran"
    override val scoreTime = "Score affiché après chaque point"
    override val scoreTimeHint = "Le résumé de fin de jeu reste affiché 2 secondes de plus."
    override val beeperVolume = "Volume du bip"
    override val mute = "Muet"
    override val off = "Non"
    override val flipDisplay = "Écran retourné"
    override val flipDisplayHint = "Pour porter le bracelet à l'autre poignet."
    override val autoOff = "Extinction automatique"
    override val pairTimeout = "À l'allumage, si aucun téléphone ne se connecte"
    override val lostTimeout = "S'il perd la connexion avec le téléphone"
    override val idleTimeout = "S'il reste connecté mais inactif"
    override val estimateFull: (String) -> String = { "Autonomie estimée $it avec une charge complète" }
    override val estimateNow: (String, Int) -> String = { h, p -> "$h avec la charge actuelle ($p %)" }
    override val estimateBreakdown: (String, String, String, String) -> String = { tot, base, dsp, snd ->
        "Consommation moyenne $tot mA : carte et Bluetooth $base · écran $dsp · bip $snd"
    }
    override val estimateMeasured = "Consommation de base mesurée sur ce bracelet pendant l'utilisation."
    override val estimateTheory = "Estimation théorique : après 20 minutes d'utilisation, elle se corrige avec la consommation mesurée."
    override val copyToOther = "Copier sur l'autre bracelet"
    override val powerOff = "Éteindre"
    override val bandPowerOffTitle = "Éteindre le bracelet ?"
    override val bandPowerOffText: (String) -> String = { "$it s'éteint tout de suite. Pour le rallumer : un clic sur le bouton latéral." }
    override val languageSection = "Langue"
    override val languageHint = "S'applique aux écrans, à la voix de l'arbitre, au tableau d'affichage TV et aux bracelets."
    override val audioSection = "Audio et voix"
    override val voiceCalls = "Annonces vocales de l'arbitre"
    override val voiceFiles: (Int, Int) -> String = { n, tot -> "Fichiers générés : $n/$tot" }
    override val voiceFilesHint = "Tout fonctionne sans internet avec les voix installées sur le téléphone. Les enregistrements personnalisés (ZIP) ont toujours la priorité sur la synthèse vocale."
    override val generateVoice = "Générer les fichiers"
    override val generating: (Int, Int) -> String = { n, tot -> "Génération $n/$tot…" }
    override val voiceGenerationFailed = "Génération non terminée : les fichiers vocaux précédents restent inchangés."
    override val importVoiceZip = "Importer un ZIP"
    override val voiceImportFailed = "ZIP illisible ou incomplet : aucun enregistrement n'a été modifié."
    override val deleteCustomVoice = "Supprimer les enregistrements"
    override val deleteCustomConfirmTitle = "Supprimer les enregistrements ?"
    override val deleteCustomConfirmText: (Int) -> String = { n -> "Les enregistrements personnalisés en français ($n) sont effacés du téléphone. Pour les récupérer, importez de nouveau le ZIP." }
    override val ttsEngine = "Moteur de synthèse vocale"
    override val engineDefault = "Par défaut du téléphone"
    override val ttsVoice = "Voix"
    override val voiceAuto = "Automatique (meilleure hors ligne)"
    override val voiceName: (String) -> String = { "Voix $it" }
    override val online = "en ligne"
    override val offline = "hors ligne"
    override val voiceFilesMode = "Utiliser des fichiers audio pré-générés"
    override val voiceFilesModeHint = "Par défaut, chaque annonce est lue d'une seule phrase (plus naturel). Activez cette option pour lire les fichiers générés, par exemple pour emporter hors ligne une voix en ligne."
    override val customRecordings: (Int, Int) -> String = { n, tot -> "Enregistrements personnalisés : $n/$tot" }
    override val voiceReadme: (String, String) -> String = { langs, formats ->
        "TENNIS SCORE MANAGER - FICHIERS VOCAUX\n" +
            "Placez les enregistrements dans voice/<langue>/ ($langs), nommés d'après la clé.\n" +
            "Formats : $formats. Les noms des joueurs sont toujours lus par la synthèse vocale.\n" +
            "Le dossier tts/ contient les fichiers générés par l'application : vos enregistrements ont la priorité."
    }
    override val voiceReadmeKey = "CLÉ"
    override val testVoice = "Tester la voix"
    override val stopVoiceTest = "Arrêter le test"
    override val ttsMissing = "La voix française de la synthèse vocale n'est pas installée sur ce téléphone."
    override val installVoice = "Installer la voix"
    override val ttsEngineError = "Synthèse vocale indisponible : vérifiez le moteur dans les Paramètres d'Android."
    override val openTtsSettings = "Ouvrir les paramètres"
    override val formatSection = "Format du match"
    override val formatBestOfThree = "3 manches · jeu décisif à 7"
    override val formatBestOfThreeHint = "Au meilleur des trois manches, jeu décisif à 6-6 dans chaque manche."
    override val formatMatchTiebreak = "2 manches + super jeu décisif à 10"
    override val formatMatchTiebreakHint = "À une manche partout, la troisième manche est un super jeu décisif en 10 points (2 points d'écart)."
    override val noAd = "No-Ad (point décisif)"
    override val noAdHint = "À 40-40, on joue un seul point : celui qui le gagne remporte le jeu."
    override val coinToss = "Tirage au sort (toss)"
    override val tossCoin = "Lancer la pièce"
    override val tossWinner: (String) -> String = { "Tirage au sort gagné par : $it" }
    override val tossHint = "Le gagnant choisit : service, relance ou côté. Indiquez ici le choix."
    override val serving = "Au service"
    override val courtSides = "Côtés du court"
    override val umpireView = "Vue depuis la chaise d'arbitre"
    override val swapSides = "Inverser les côtés"
    override val firstServerOf: (String) -> String = { "Sert en premier ($it)" }
    override val left = "Gauche"
    override val right = "Droite"
    override val net = "FILET"
    override val umpireChair = "Arbitre de chaise"

    override val locationDialogTitle = "Activer la localisation"
    override val locationDialogText = "Sans la localisation, le lieu ne pourra pas figurer dans le résumé du match."
    override val continueWithout = "Continuer sans"
    override val bandsRequiredTitle = "Bluetooth et localisation obligatoires"
    override val bandsRequiredText = "Pour utiliser les bracelets, activez le Bluetooth, autorisez la localisation et laissez-la activée."
    override val bandsMissingTitle = "Bracelets non associés"
    override val bandsMissingText: (List<String>) -> String = {
        (if (it.size > 1) "Il manque les bracelets de : " else "Il manque le bracelet de : ") + it.joinToString(", ") + ". Continuer quand même ?"
    }
    override val continueAnyway = "Continuer"
    override val cancel = "Annuler"

    override val startMatch = "DÉBUT DU MATCH"
    override val startHint = "Appuyez sur le bouton pour commencer"
    override val startHintBands = "Appuyez sur le bouton ou sur KEY1 d'un bracelet"
    override val startButton = "Commencer le match"
    override val resumeSaved = "Reprendre un match suspendu"
    override val noSavedMatches = "Aucun match suspendu enregistré."
    override val savedMatchesTitle = "Matchs suspendus"
    override val delete = "Supprimer"
    override val deleteSavedTitle = "Supprimer le match suspendu ?"
    override val deleteSavedText: (String) -> String = { "Le match $it ne pourra plus être repris." }
    override val vs = "contre"

    override val matchTime = "Match Time"
    override val setsHeader = "SETS"
    override val gamesHeader = "JEUX"
    override val undoPoint = "Annuler le point"
    override val suspend = "Suspendre"
    override val resume = "Reprendre"
    override val audioOn = "Audio On"
    override val audioOff = "Audio Off"
    override val newMatch = "Nouveau match"
    override val suspendedOverlay = "MATCH SUSPENDU"
    override val newMatchConfirmTitle = "Nouveau match ?"
    override val newMatchConfirmText = "Le match en cours reste enregistré parmi les matchs suspendus et pourra être repris."
    override val endDialogTitle = "Jeu, set et match"
    override val endDialogText: (String, String, Boolean) -> String = { name, score, _ -> "Victoire de $name\n$score" }
    override val matchConcluded = "Match terminé"
    override val undoLastPoint = "Annuler le dernier point"
    override val serveOrderTitle: (Int) -> String = { "Ordre de service · manche $it" }
    override val whoServesFirst: (String) -> String = { "Qui sert en premier chez $it ?" }
    override val confirm = "Confirmer"
    override val backDisabled = "Pendant le match, utilisez « Nouveau match » ou « Quitter »."
    override val exit = "Quitter"
    override val exitConfirmTitle = "Quitter l'application ?"
    override val exitConfirmText = "Le match reste enregistré parmi les matchs suspendus et pourra être repris."
    override val exitConfirmBands = "Les bracelets seront éteints."

    override val msgChangeEnds = "CHANGEMENT DE CÔTÉ"
    override val msgTiebreak = "JEU DÉCISIF"
    override val msgMatchTiebreak = "SUPER JEU DÉCISIF"
    override val msgSetWon: (String) -> String = { "MANCHE $it" }
    override val msgSetPoint = "BALLE DE SET"
    override val msgMatchPoint = "BALLE DE MATCH"
    override val msgBreakPoint = "BALLE DE BREAK"
    override val msgDecidingPoint = "POINT DÉCISIF"
    override val msgPointUndone = "POINT ANNULÉ"
    override val msgSuspended = "MATCH SUSPENDU"
    override val msgResumed = "REPRISE DU MATCH"
    override val msgBandConnected: (String) -> String = { "BRACELET $it CONNECTÉ" }
    override val msgBandLost: (String) -> String = { "BRACELET $it DÉCONNECTÉ" }
    override val msgBandOff: (String, Int?) -> String = { n, why ->
        "BRACELET $n ÉTEINT" + when (why) {
            BandProtocol.OFF_IDLE -> " (INACTIF)"
            BandProtocol.OFF_BATTERY -> " (BATTERIE VIDE)"
            BandProtocol.OFF_TIMEOUT -> " (AUCUN TÉLÉPHONE)"
            else -> ""
        }
    }
    override val msgBandBatteryLow: (String, Int) -> String = { n, p -> "BRACELET $n : BATTERIE $p %" }
    override val bandBatteryLow = "BATTERIE FAIBLE"
    override val autonomy: (String) -> String = { "autonomie ~$it" }
    override val bandsBattery = "Batterie des bracelets"

    override val notifChannel = "Match en cours"
    override val notifText: (Boolean, Boolean) -> String = { bands, tv ->
        "Match en cours · " + when {
            bands && tv -> "bracelets et tableau TV actifs"
            tv -> "tableau TV actif"
            else -> "bracelets actifs"
        }
    }

    override val bandPaired = "ASSOCIE A"
    override val bandPlay = "JOUEZ"
    override val bandChangeEnds = "CHANGEMENT DE COTE"
    override val bandTiebreak = "JEU DECISIF"
    override val bandSet = "MANCHE"
    override val bandSuspended = "SUSPENDU"
    override val bandGameSetMatch = "JEU SET ET MATCH"
    override val bandMatchOver = "FIN DU MATCH"
    override val bandAppClosed = "APPLI FERMEE"
    override val bandOffFromApp = "EXTINCTION"

    override val summaryTitle = "Match terminé"
    override val winner = "Vainqueur"
    override val duration = "Durée"
    override val startTime = "Début"
    override val endTime = "Fin"
    override val date = "Date"
    override val club = "Club"
    override val court = "Court"
    override val place = "Lieu"
    override val placeUnavailable = "Position non disponible"
    override val format = "Format"
    override val pointsWon = "Points gagnés"
    override val gamesWon = "Jeux gagnés"
    override val result = "Résultat"
    override val saveHistory = "Enregistrer dans l'historique"
    override val summaryLeaveTitle = "Résumé non enregistré"
    override val summaryLeaveText = "Vous n'avez ni enregistré ni partagé le résumé du match : vous ne pourrez plus le revoir."
    override val share = "Partager"
    override val saveDialogTitle = "Enregistrer dans l'historique"
    override val fileName = "Nom"
    override val folder = "Dossier"
    override val chooseFolder = "Choisir un dossier"
    override val defaultFolder = "Dossier de l'application (par défaut)"
    override val formatReport = "Compte rendu (.txt)"
    override val formatData = "Données du match (.json)"
    override val formatImage = "Image (.png)"
    override val save = "Enregistrer"
    override val savedTo: (String) -> String = { "Enregistré dans $it" }
    override val saveError = "Échec de l'enregistrement"
    override val shareSubject = "Résultat du match de tennis"
    override val playerDefault: (Int) -> String = { "Joueur $it" }
    override val teamJoiner = " et "
    override val generatedWith = "Créé avec Tennis Score Manager"

    override val tvGames = "JEUX"
    override val tvSet = "MANCHES"
    override val tvServe = "SERVICE"
    override val tvChangeover = "CHANGEMENT DE CÔTÉ"
    override val tvSetBreak = "PAUSE SET"
    override val tvTiebreakBreak = "PAUSE"
    override val tvWaiting = "EN ATTENTE DU MATCH"
    override val tvReady = "PRÊTS À JOUER"
    override val tvSuspended = "MATCH SUSPENDU"
    override val tvWinner = "VAINQUEUR"
    override val tvTiebreak = "JEU DÉCISIF"
    override val tvMatchTiebreak = "SUPER JEU DÉCISIF"
    override val tvLost = "CONNEXION PERDUE - RECONNEXION..."
    override val tvFullscreen = "PLEIN ÉCRAN"
    override val tvPageTitle = "Tableau d'affichage TSM"

    override val tvSection = "Tableau d'affichage TV"
    override val tvEnable = "Tableau d'affichage sur TV ou écran"
    override val tvEnableHint = "Un autre téléphone (ou un ordinateur, ou un Chromecast) affiche le score en direct sur un écran. Les téléphones doivent être sur le même réseau : le point d'accès de l'un des deux."
    override val tvAddress = "Adresse du tableau d'affichage"
    override val tvNoNetwork = "Aucun réseau : activez le point d'accès sur l'un des deux téléphones et connectez-y l'autre."
    override val tvScreens: (Int) -> String = { if (it == 0) "Aucun tableau connecté" else if (it == 1) "1 tableau connecté" else "$it tableaux connectés" }
    override val tvQrHint = "Sur l'autre téléphone : ouvrez Tennis Score Manager et touchez « Utiliser comme tableau » (il se connecte tout seul), ou scannez le code avec l'appareil photo et ouvrez-le dans le navigateur."
    override val tvLook = "Apparence du tableau"
    override val tvTitle = "Texte en bas"
    override val tvTitleHint: (String) -> String = { if (it.isEmpty()) "Vide : aucun texte" else "Vide : « $it » (club et court de la page 1)" }
    override val tvColorOf: (String) -> String = { "Couleur ${de(it)}" }
    override val tvShowClock = "Durée du match"
    override val tvShowTimers = "Chrono du service et des pauses"
    override val tvShowSets = "Manches terminées"
    override val tvShowMessages = "Messages (balle de break, balle de set...)"
    override val tvShowServe = "Balle à côté du serveur"
    override val tvGhost = "Segments éteints visibles"
    override val tvPreview = "Aperçu sur ce téléphone"
    override val tvChromecastHint = "Avec un Chromecast : sur le téléphone-tableau, utilisez le bouton « Caster » des réglages rapides (« Diffusion de l'écran » jusqu'à Android 14, « Smart View » sur Samsung). Le Chromecast a besoin d'un réseau avec internet : activez les données mobiles sur le téléphone qui partage la connexion."

    override val displayMode = "Utiliser comme tableau"
    override val displayModeHint = "Ce téléphone affiche le score sur l'écran (câble HDMI ou Chromecast)"
    override val displaySearching = "Recherche du téléphone de l'arbitre…"
    override val displaySteps = "1. Activez le point d'accès sur l'un des deux téléphones et connectez-y l'autre.\n2. Sur le téléphone de l'arbitre : page 2 → « Tableau d'affichage TV » activé.\n3. Branchez ce téléphone à l'écran (câble USB-C/HDMI) ou castez l'écran sur un Chromecast."
    override val displayManual = "Adresse (ex. 192.168.43.1:8080)"
    override val displayConnect = "Connecter"
    override val displayNotFound = "Introuvable. Vérifiez que les deux téléphones sont sur le même réseau et que le tableau est activé dans l'application de l'arbitre."
    override val displayOnMonitor = "Le tableau est sur l'écran externe"
    override val displayShowHere = "Afficher aussi ici"
    override val displayBackAgain = "Appuyez encore sur retour pour quitter"
    override val displayRetry = "Chercher à nouveau"
}
