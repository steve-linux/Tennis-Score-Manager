# Tennis Score Manager — guide pas à pas

[Italiano](GUIDA.md) · [English](GUIDE.en.md) · **Français** · [Deutsch](GUIDE.de.md) · [Español](GUIDE.es.md) · [Português](GUIDE.pt.md)

Application Android (Kotlin + Jetpack Compose) pour tenir le score au tennis selon les règles ITF, avec les annonces vocales d'un arbitre de chaise **en six langues** (italien, anglais, français, allemand, espagnol, portugais : chapitre 4.1), deux bracelets **M5StickS3** connectés en Bluetooth LE et un **tableau d'affichage à LED sur TV ou écran** (chapitre 8).

## 0. Contenu du dépôt

| Chemin | À quoi il sert |
|---|---|
| `TennisScoreManager/` | Le projet Android Studio. |
| `firmware/TSM_Band/TSM_Band.ino` | Le firmware du bracelet. |
| `deliver/installa_tsm.sh` | Alternative au clone : crée **tout** le projet Android et le sketch uniquement avec des blocs `cat << 'TSM_EOF'` (le jar du Gradle wrapper est en base64). |
| `deliver/GUIDE.fr.md` | Ce guide (en français) ; l'original italien est `deliver/GUIDA.md`, les autres traductions sont `deliver/GUIDE.en.md`, `.de`, `.es`, `.pt`. |

Versions utilisées et vérifiées : app **2.3.1** · firmware **2.2.2** · Gradle 8.14.3 · Android Gradle Plugin 8.13.2 · Kotlin 2.2.21 · Compose BOM 2025.12.00 · compileSdk/targetSdk 36 · minSdk 26 (Android 8.0). Firmware : M5Unified ≥ 0.2.12, NimBLE-Arduino ≥ 2.1, core ESP32 3.x.

---

## 1. Préparer l'ordinateur et télécharger le projet

**Windows 10/11**, **Ubuntu** (22.04 ou plus récent) ou **Fedora** conviennent ; sur les autres distributions Linux, les étapes sont celles d'Ubuntu ou de Fedora avec leur gestionnaire de paquets. Dans les commandes, `~` désigne votre dossier personnel : sous Linux `/home/<utilisateur>`, sous Windows `C:\Users\<utilisateur>`.

### 1.1 Logiciels à installer

| | Windows 10/11 (PowerShell) | Ubuntu | Fedora |
|---|---|---|---|
| **Git** (télécharger et mettre à jour le projet) | `winget install --id Git.Git -e` | `sudo apt install git` | `sudo dnf install git` |
| **Android Studio** (l'application) | `winget install --id Google.AndroidStudio -e` | `sudo snap install android-studio --classic` | archive `.tar.gz` de developer.android.com/studio, extraite par exemple dans `~/android-studio` |
| **Arduino IDE 2** (les bracelets) | `winget install --id ArduinoSA.IDE.stable -e` | Flatpak `cc.arduino.IDE2` (voir ci-dessous) | `flatpak install flathub cc.arduino.IDE2` |
| **Téléphone branché par câble** | pilote USB du fabricant, si nécessaire (voir ci-dessous) | `sudo apt install android-sdk-platform-tools-common` | `sudo dnf install android-tools` |
| **Port série du bracelet** | rien à faire | `sudo usermod -aG dialout $USER` | `sudo usermod -aG dialout $USER` |

- **Windows** : `winget` est déjà présent dans Windows 10/11 à jour et s'utilise depuis *PowerShell* ou *Terminal*. Vous pouvez aussi télécharger les installateurs classiques sur git-scm.com, developer.android.com/studio et arduino.cc/en/software.
- **Ubuntu, Arduino IDE** : si Flatpak n'est pas encore installé : `sudo apt install flatpak`, puis `flatpak remote-add --if-not-exists flathub https://dl.flathub.org/repo/flathub.flatpakrepo` et `flatpak install flathub cc.arduino.IDE2` ; fermez puis rouvrez la session pour qu'il apparaisse dans le menu. L'AppImage d'arduino.cc fonctionne aussi, mais sur les Ubuntu récentes elle demande des paquets et des options supplémentaires : Flatpak est plus simple.
- **Linux, Android Studio depuis l'archive** : il se lance avec `bin/studio.sh` dans le dossier extrait (ex. `~/android-studio/bin/studio.sh`). Sur Ubuntu, le snap convient aussi ; sur Fedora, il existe également le Flatpak `com.google.AndroidStudio`, mais l'archive officielle pose moins de problèmes avec le téléphone branché.
- **Linux, groupe `dialout`** (port série) et paquets pour le téléphone (règles udev) : après les avoir installés, **fermez puis rouvrez la session** (ou redémarrez), sinon le port et le téléphone restent inaccessibles.
- **Windows, pilote du téléphone** : beaucoup de téléphones fonctionnent tout de suite. Si Android Studio ne voit pas le téléphone, installez le pilote du fabricant : *Samsung Android USB Driver* (sur le site Samsung Developer) pour les Samsung, *Google USB Driver* (Android Studio › **Tools › SDK Manager › SDK Tools**) pour les Pixel. Le bracelet n'a pas besoin de pilote : il apparaît comme port `COM3`, `COM4`…
- Au premier démarrage, Android Studio lance un assistant : choisissez **Standard** et laissez-le télécharger le SDK Android (il faut internet, quelques Go).

### 1.2 Télécharger le projet

Le dépôt GitHub est **privé** : il faut un compte GitHub auquel le propriétaire a donné accès.

**A. Avec git (recommandé : ensuite, on met à jour avec `git pull`)**

Linux (Ubuntu, Fedora), dans le Terminal :

```bash
mkdir -p ~/AndroidStudioProjects
cd ~/AndroidStudioProjects
git clone https://github.com/steve-linux/Tennis-Score-Manager.git
```

Windows, dans PowerShell :

```powershell
mkdir -Force $HOME\AndroidStudioProjects
cd $HOME\AndroidStudioProjects
git clone https://github.com/steve-linux/Tennis-Score-Manager.git
```

- **Accès** : sous Windows, git ouvre tout seul la fenêtre de connexion à GitHub. Sous Linux, GitHub n'accepte pas le mot de passe dans `git clone` : le plus simple est GitHub CLI (`sudo apt install gh` ou `sudo dnf install gh`), puis `gh auth login` et, dans le dossier `~/AndroidStudioProjects`, `gh repo clone steve-linux/Tennis-Score-Manager`.
- Le dépôt arrive dans `~/AndroidStudioProjects/Tennis-Score-Manager`. Le projet à ouvrir dans Android Studio est son sous-dossier **`TennisScoreManager`** ; le sketch du bracelet se trouve dans `firmware/TSM_Band/`.
- Mettre à jour : `git pull` dans `Tennis-Score-Manager`.

**B. Sans git** : sur la page GitHub du projet (une fois connecté) **Code › Download ZIP**, puis extrayez le ZIP, par exemple dans `~/AndroidStudioProjects`. Pour une nouvelle version, on télécharge à nouveau le ZIP.

**C. Avec le script** `deliver/installa_tsm.sh`, qui contient tout le projet dans un seul fichier texte (blocs `cat << 'TSM_EOF'`). Il se lance sous Linux ou, sous Windows, dans *Git Bash* (fourni avec Git) :

```bash
bash installa_tsm.sh
```

- Le projet va dans `~/AndroidStudioProjects/TennisScoreManager`, le sketch dans `~/Arduino/TSM_Band/TSM_Band.ino`.
- Si le dossier du projet existe déjà, il est **déplacé** vers `TennisScoreManager.backup-AAAAMMJJ-hhmmss` (les anciens layouts XML et les anciennes classes feraient échouer la nouvelle build).
- Pour utiliser d'autres dossiers : `bash installa_tsm.sh /chemin/du/projet /chemin/du/sketch`.

## 2. Ouvrir et compiler dans Android Studio

1. Lancez Android Studio : sous Windows depuis le menu Démarrer, sous Ubuntu (snap) depuis le menu des applications, depuis l'archive avec `bin/studio.sh` (1.1).
2. **File › Open** (ou **Open** dans la fenêtre d'accueil) → choisissez le dossier `TennisScoreManager` (dans le clone ou le ZIP, ou celui créé par le script) → **Trust Project**.
3. Attendez la synchronisation Gradle (la première fois, elle télécharge Gradle, le plugin Android et les bibliothèques : internet n'est nécessaire qu'à ce moment-là).
4. Si *"Failed to find target android-36"* apparaît, cliquez sur le lien **Install missing platform** (ou **Tools › SDK Manager › SDK Platforms › Android 16 (API 36)**).
5. Si Android Studio propose l'**AGP Upgrade Assistant**, vous pouvez l'ignorer : ces versions ont été compilées et testées telles quelles.
6. JDK de Gradle : **Settings › Build, Execution, Deployment › Build Tools › Gradle › Gradle JDK** = *jbr-21* (celui qui est fourni, c'est la valeur par défaut).
7. **Build › Make Project** : doit se terminer par *BUILD SUCCESSFUL*.
8. Facultatif : les tests (76 : règles, voix, langues, batterie et charge, réglages des bracelets, tableau TV) se lancent par un clic droit sur `app/src/test` › **Run Tests**.

## 3. Installer l'application sur le téléphone

1. Sur le téléphone : **Paramètres › À propos du téléphone** → touchez 7 fois *Numéro de build* → **Options pour les développeurs › Débogage USB** activé (sur Samsung : *À propos du téléphone › Informations sur le logiciel › Numéro de version*, puis **Options de développement › Débogage USB**).
2. Branchez le câble, acceptez l'empreinte RSA, choisissez le téléphone en haut et appuyez sur **▶ Run**.
   - Si l'ordinateur ne voit pas le téléphone : sous Windows le pilote USB du fabricant, sous Linux le paquet avec les règles udev (1.1) ; puis rebranchez le câble. Autre solution, sans câble ni pilote : **Device Manager › Pair devices using Wi-Fi** (débogage sans fil, Android 11+, ordinateur et téléphone sur le même réseau).
3. Autre possibilité : **Build › Build App Bundle(s) / APK(s) › Build APK(s)**, copiez l'APK sur le téléphone et installez-le (il faut autoriser l'installation d'applications de sources inconnues).

## 4. La voix (fonctionne sans internet)

- Chaque annonce est lue **d'une seule phrase** par la synthèse vocale du téléphone, avec une voix installée : pas d'internet et une prosodie naturelle. Les noms des joueurs font partie de la phrase.
- Page 2 › **Audio et voix** :
  - **Moteur de synthèse vocale** : *Par défaut du téléphone* (sur les Samsung, c'est Samsung TTS) ou un moteur au choix, par exemple *Services vocaux Google*. Lors des tests, faits en italien, Google sonnait plus naturel.
  - **Voix** : *Automatique (meilleure hors ligne)* ou une voix précise (Google désigne ses voix par un code, que l'application affiche sous la forme « Voix » + code ; celles marquées *en ligne* nécessitent internet).
  - **Tester la voix** : lit une séquence d'annonces d'exemple avec les noms saisis. Pendant la lecture, le bouton devient **Arrêter le test** : touchez-le à nouveau pour l'interrompre (la lecture s'arrête aussi d'elle-même quand vous quittez la page). La synthèse vocale ne peut pas être mise en pause au milieu d'une phrase, donc le bouton l'arrête ; si vous appuyez de nouveau, elle reprend depuis le début.
- S'il manque la voix de la langue choisie : **Paramètres › Gestion globale › Langue › Synthèse vocale** (ou *Sortie de la synthèse vocale*) → téléchargez la voix de cette langue pour le moteur choisi (l'application affiche aussi le bouton **Installer la voix**).
- **Prononciation** : certains moteurs lisent mal des mots du tennis (en italien, « primo set » était lu « primo settembre », « tie-break » était lu « time break »). L'application les corrige d'elle-même (`voice/Pronunciation.kt`) ; les corrections ont été vérifiées en transcrivant l'audio réel de Samsung et de Google.
- **Enregistrements personnalisés** (la voix la plus naturelle qui soit : la vôtre ou celle d'un arbitre) : le dossier `Android/data/com.tennis.scoremanager/files/voice/` contient `LEGGIMI.txt` (le fichier « lisez-moi », dont les explications en tête sont dans la langue de l'application et sont réécrites quand vous la changez) avec la liste des 84 clés et des phrases. Enregistrez les fichiers sous ces noms (`score_1_0.mp3` = « quinze-zéro »…), mettez-les dans un ZIP avec un dossier par langue (`it/`, `en/`, `fr/`, `de/`, `es/`, `pt/`) et utilisez **Importer un ZIP**. `LEGGIMI.txt` a une colonne par langue, séparées par des tabulations : il s'ouvre donc aussi très bien comme feuille de calcul. Les enregistrements ont toujours la priorité sur la synthèse ; les noms restent lus par la synthèse.
- **Utiliser des fichiers audio pré-générés** (facultatif) : avec **Générer les fichiers**, l'application crée une fois pour toutes les 84 fichiers avec la voix choisie (même une voix *en ligne*, si internet est disponible à ce moment-là), puis les utilise à la place de la synthèse en continu. Le rendu est plus « haché », mais c'est utile pour emporter hors ligne une voix en ligne.
- **Enceinte externe** : il suffit de l'appairer au téléphone en Bluetooth ; la voix sort sur le canal multimédia (réglez le volume multimédia).

### 4.1 Langues (app 2.3, firmware 2.2)

Page 2 › **Langue** : Italiano, English, Français, Deutsch, Español, Português (chaque langue est écrite dans sa propre langue, pour qu'on la retrouve même quand l'application est dans une langue qu'on ne lit pas). Le choix s'applique à la fois aux **écrans, à la voix de l'arbitre, au tableau d'affichage TV, au récapitulatif et au partage, et aux bracelets**. Les dates suivent la langue (*mercredi 30 septembre 2026*, *Mittwoch, 30. September 2026*…).

Les annonces ne sont pas des traductions mot à mot : elles suivent les textes officiels pour les arbitres de chaise de chaque fédération, à savoir *L'arbitrage en 255 questions* de la FFT et l'ITF en français, les documents DTB/BTV et Swiss Tennis, *Deberes y procedimientos* de la RFET, *Deveres e Procedimentos* de la FPT (le seul script portugais publié). L'anglais suit l'ITF.

| | Italiano | English | Français | Deutsch | Español | Português |
|---|---|---|---|---|---|---|
| Début | primo set · Rossi al servizio · gioco | first set · Rossi to serve · play | première manche · au service Rossi · jouez | erster Satz · Aufschlag Rossi · spielen | primer set · al servicio Rossi · jueguen | primeiro set · Rossi ao serviço · joguem |
| 15-0 · 15-15 | quindici zero · quindici pari | fifteen love · fifteen all | quinze-zéro · quinze A | fünfzehn null · fünfzehn beide | quince cero · quince iguales | quinze-zero · quinze iguais |
| 40-40 · avantage | parità · vantaggio Rossi | deuce · advantage Rossi | égalité · avantage Rossi | Einstand · Vorteil Rossi | iguales · ventaja Rossi | iguais · vantagem Rossi |
| Fin de jeu | gioco Rossi, Rossi conduce tre giochi a due | game Rossi, Rossi leads three games to two | jeu Rossi, Rossi mène trois jeux à deux | Spiel Rossi, Rossi führt drei zu zwei | juego Rossi, Rossi gana tres juegos a dos | jogo Rossi, Rossi vence por três jogos a dois |
| Jeux à égalité | due giochi pari | two games all | deux jeux partout | zwei beide | dos juegos iguales | dois jogos iguais |
| Jeu décisif | tre a uno Rossi · sei pari | three one Rossi · six all | trois un Rossi · six partout | drei zu eins Rossi · sechs beide | tres uno Rossi · seis iguales | três a um Rossi · seis iguais |
| Manche | un set a zero · un set pari | one set to love · one set all | une manche à zéro · une manche partout | eins zu null in Sätzen · ein Satz beide | un set a cero · un set iguales | um set a zero · sets iguais |
| Fin du match | gioco, set, partita Rossi | game, set and match Rossi | jeu, set et match Rossi | Spiel, Satz und Sieg Rossi | juego, set y partido Rossi | jogo, set e partida Rossi |
| Changement de côté | cambio campo | change ends | changement de côté | Seitenwechsel | cambio de lado | troca de lado |

Particularités :
- En français et en espagnol, le nom vient **après** « au service »/« al servicio », en allemand après « Aufschlag ». Le français appelle le set **manche** (sauf dans « jeu, set et match ») et le tie-break **jeu décisif**.
- **10-6 … 10-9** dans le super jeu décisif : en français, en espagnol et en portugais, on dit « dix **à** huit », « diez **a** ocho », « dez **a** oito », parce que « dix huit » / « diez ocho » / « dez oito » s'entendent comme *dix-huit*.
- **Portugais** : une seule option, avec l'interface et la voix préférée du Brésil (si la voix brésilienne manque, on utilise la voix portugaise) et les annonces du script FPT, avec des mots valables dans les deux pays (« jogo » et non « game », « partida »). « Um set a um » est devenu « sets iguais » : au singulier, *set* se prononce comme *sete* et on entendait 7-1.
- **Prononciation** (`voice/Pronunciation.kt`) : toutes les phrases de toutes les langues ont été lues par les voix Google et transcrites avec whisper. Corrections ajoutées : en allemand, « Tie-Break » est lu « Taibreak », précédé d'une virgule (sinon « Teilbreg » ou « bei Detailbreak ») ; en français, le « à » isolé des fichiers pré-générés est lu « a » (sinon « a accent grave »). **Samsung TTS n'a pas encore été testé dans les nouvelles langues.**
- **Bracelets** (firmware 2.2) : l'application envoie d'elle-même la langue à chaque connexion et quand vous la changez ; le bracelet l'enregistre et l'utilise aussi hors connexion (recherche du téléphone, charge, extinction), sans accents car la police est en ASCII (*EN CHARGE*, *LAEDT*, *CARGANDO*…). Avec un firmware 2.1 ou antérieur, les messages envoyés par l'application sont traduits, mais ceux du bracelet lui-même restent en italien. Un bracelet qui vient d'être programmé démarre en italien ; depuis le firmware 2.2.2, son message de connexion est réécrit dans la langue de l'application dès que celle-ci la lui envoie, environ une seconde après la connexion.

## 5. Firmware des bracelets (Arduino IDE)

1. Installez Arduino IDE 2 (1.1). Sous Linux, il faut aussi l'autorisation d'accès au port série (groupe `dialout`, 1.1), après avoir fermé puis rouvert la session.
2. **File › Preferences › Additional boards manager URLs** :
   `https://static-cdn.m5stack.com/resource/arduino/package_m5stack_index.json`
3. **Boards Manager** → cherchez **M5Stack** → installez la version **≥ 3.2.5**.
4. **Library Manager** → installez **M5Unified** (acceptez « Install all » pour M5GFX) et **NimBLE-Arduino** de *h2zero* (≥ 2.1).
5. **File › Open** → `firmware/TSM_Band/TSM_Band.ino` du clone ou du ZIP (ou `~/Arduino/TSM_Band/TSM_Band.ino` si vous avez utilisé le script).
6. **Tools › Board › M5Stack › M5StickS3**, **Tools › Port** → le port du bracelet : sous Linux `/dev/ttyACM0`, sous Windows `COM3`, `COM4`… (celui qui apparaît quand vous branchez le câble).
   *(Sans le paquet M5Stack, « ESP32S3 Dev Module » convient aussi : USB CDC On Boot = Enabled, Flash Size = 8MB, Partition = 8M with spiffs.)*
7. **Upload (→)**. Si le port n'apparaît pas ou si le téléversement échoue : essayez un autre câble USB-C (certains ne servent qu'à charger), puis maintenez longuement le **bouton latéral** (mode téléchargement) et réessayez ; en mode téléchargement, sous Windows, le numéro du port COM peut changer.
   **Après le téléversement**, si l'écran reste noir (le bracelet est resté en mode programmation), appuyez **une fois** sur le bouton latéral : il redémarre avec le nouveau programme.
8. Au démarrage, le bracelet affiche son nom, ex. **TSM-3FA2** (modifiable depuis l'application, voir 6.1). Recommencez pour le second bracelet.

> **Firmware 2.2.2** : le message de connexion passe à la langue de l'application dès qu'elle arrive (4.1) ; *VINCULANDO…/VINCULADA* en espagnol et *PAREADA* en portugais comme dans l'application, *MANCHES* en français sur l'écran des jeux. **Firmware 2.2.1** : le compte à rebours avant l'extinction est lui aussi dans la langue de l'application (6). **Firmware 2.2** : les textes du bracelet dans la langue de l'application (4.1). **Firmware 2.1** : l'écran de charge (6.2) ; les réglages, *Identifier* et l'extinction depuis l'application demandent au moins la 2.0. Chargez `TSM_Band.ino` sur **les deux** bracelets ; avec un ancien firmware, l'application le signale dans le panneau des réglages et tout le reste continue de fonctionner.

## 6. Utiliser les bracelets

| Bouton | Action |
|---|---|
| **KEY1** (en façade), appui court | point pour le joueur qui porte le bracelet (lance aussi le match depuis la page DÉBUT DU MATCH) ; un bip confirme |
| **KEY1**, appui long (1 s) | affiche la batterie (et garde le bracelet allumé s'il est sur le point de s'éteindre pour inactivité) |
| **KEY2**, appui court | annule le dernier point (aussi depuis la fenêtre de fin de match) ; deux bips plus graves |
| **KEY2**, appui long (2 s) | éteint le bracelet (câble branché, il affiche *LA CHARGE CONTINUE* : il se recharge même éteint) |
| Bouton latéral | un clic allume ; un double clic éteint (fonction matérielle) |

- À l'allumage, **APPAIRAGE...** clignote (allumé 0,35 s toutes les 2 s, pour économiser la batterie). L'application se connecte d'elle-même aux bracelets qu'elle connaît dès qu'elle est ouverte ; les nouveaux, elle les trouve sur la page 2.
- Une fois connecté : **APPAIRE** pendant 3 secondes avec deux bips, puis **ASSOCIE A** + le nom du joueur.
- À chaque point, l'écran s'allume avec le score du jeu en grand (à gauche le vôtre, à droite celui de l'adversaire ; la balle verte indique qui sert), puis s'éteint. À la fin d'un jeu, il affiche les jeux et les manches.

**Extinction automatique** (les délais se modifient depuis l'application, 6.1) :

| Situation | Ce que fait le bracelet | Par défaut |
|---|---|---|
| Allumé, mais aucun téléphone ne se connecte | APPAIRAGE clignote, puis il s'éteint | **30 s** |
| Téléphone perdu (éteint, hors de portée, Bluetooth désactivé, application fermée brutalement) | RECONNEXION clignote et il se reconnecte tout seul dès qu'il le peut ; sinon il s'éteint | 3 min |
| Connecté mais inactif (aucun point, aucun message) | 30 s avant, il prévient avec **INACTIF · MAINTENIR KEY1** et un bip, puis il s'éteint | 30 min |
| Match terminé (confirmé sur le téléphone) ou **Quitter** dans l'application | affiche FIN DU MATCH / APPLI FERMEE et s'éteint aussitôt | activé (désactivable, 7.2) |
| Batterie vide (sous 3,30 V sur deux lectures consécutives) | affiche BATTERIE VIDE et s'éteint, pour ne pas rester allumé à moitié | toujours |

- Pendant les 10 dernières secondes avant une extinction faute de téléphone, il affiche **EXTINCTION · 8s - APPUYER SUR UNE TOUCHE** avec un bip : n'importe quel bouton relance le délai depuis le début.
- Quand il s'éteint tout seul, il le signale au téléphone : en match, le cadre orange affiche *BRACELET 1 ÉTEINT (INACTIF)*, *(BATTERIE VIDE)*…

**Autonomie de la batterie** : le poste le plus lourd est la carte ESP32-S3 avec le Bluetooth connecté (environ 35 mA) : le core Arduino est compilé sans l'économie d'énergie profonde (light sleep) quand le Bluetooth est actif, on ne peut donc pas descendre beaucoup plus bas. L'écran, la luminosité et les bips n'ajoutent que quelques mA. Avec la batterie de 250 mAh, l'estimation est d'**environ 7 heures avec une charge complète**, plus que n'importe quel match au meilleur des trois manches. Le panneau des réglages affiche l'estimation en temps réel et, après 20 minutes d'utilisation, la corrige avec la consommation **mesurée** sur ce bracelet. Le journal complet se trouve dans `files/battery_log.csv` de l'application.

Les autres économies : CPU à 80 MHz, écran éteint quand il ne sert pas, Bluetooth basse consommation (la radio se réveille 2 à 3 fois par seconde, l'appui sur le bouton part quand même en ~120 ms), buzzer alimenté uniquement pendant les bips, micro/IMU/5V éteints.

### 6.1 Réglages du bracelet

Depuis la page 2 (**Réglages** sous le bracelet de chaque joueur) ou pendant le match (touchez **J1**/**J2** en haut, ou l'icône des curseurs). Ils sont enregistrés **dans le bracelet** et sont conservés même quand on l'éteint ; à chaque modification, le bracelet affiche son nom avec *REGLAGES OK*.

| Réglage | Valeurs | Par défaut |
|---|---|---|
| Nom | jusqu'à 12 caractères (ex. le nom du joueur ou « J1 ») | TSM-xxxx |
| Luminosité de l'écran | 5-100 % | 20 % |
| Score affiché après chaque point | Non, 2, 3, 5, 8 s (le résumé de fin de jeu dure 2 s de plus) | 3 s |
| Volume du bip | Muet-100 % | 50 % |
| Écran retourné | pour porter le bracelet à l'autre poignet | non |
| Extinction : à l'allumage sans téléphone | 15 s - 5 min | 30 s |
| Extinction : téléphone perdu | 1-10 min | 3 min |
| Extinction : connecté mais inactif | 10-60 min | 30 min |

En dessous se trouve l'**estimation de l'autonomie** (avec une charge complète et avec la charge actuelle), avec la consommation détaillée par poste : elle change pendant que vous déplacez les curseurs, avant même de valider. Puis **Identifier**, **Éteindre** et **Copier sur l'autre bracelet** (mêmes réglages, mais chaque bracelet garde son nom).

### 6.2 Charge (firmware 2.1)

Branchez le câble USB-C : le bracelet émet un bip et affiche l'**écran de charge** pendant 30 secondes, puis l'écran s'éteint et se rallume 1,5 s toutes les 10 secondes (un coup d'œil, comme le voyant d'un chargeur). **N'importe quel bouton** le rallume pour 30 secondes de plus. Si le bracelet était éteint, allumez-le d'un clic sur le bouton latéral pour voir cet écran (la charge a lieu de toute façon, même bracelet éteint).

```
 TSM-3FA2   USB 5.01V               ← nom et tension du câble
 ┌──────────┐
 │██████ ⚡  │▌   78%                ← icône de batterie et pourcentage de charge
 └──────────┘
      EN CHARGE                     ← ou CHARGE TERMINEE / ALIMENTE PAR USB
 4.12V  DEPUIS 42 MIN  FIN ~25 MIN  ← tension de la batterie, durée de charge, fin estimée
```

- **Pourcentage** : en charge, la tension mesurée est plus élevée que la tension réelle (≈0,1 V) ; le bracelet la corrige, ne fait jamais baisser le pourcentage et n'atteint **100 %** que lorsque le chargeur indique qu'il a terminé. Une fois la charge complète, le texte devient **CHARGE TERMINEE** avec le temps nécessaire (*CHARGEE EN 1H 25*) et l'écran reste éteint (pas de flashs la nuit).
- **Fin estimée** : la puce d'alimentation (PM1) ne mesure pas le courant de charge, donc le temps restant est déduit de la progression de la charge sur les 10 dernières minutes : il apparaît au bout de 10 minutes, arrondi à 5, et reste une estimation.
- **ALIMENTE PAR USB** : le câble est branché mais la batterie ne se charge pas (et n'est pas pleine) : câble ou chargeur trop faibles, ou batterie débranchée.
- **Câble branché, il ne s'éteint pas tout seul** (pas d'extinction pour absence de téléphone ou inactivité, pas d'extinction *batterie vide*) ; on peut l'éteindre par un appui long sur KEY2. Une fois le câble débranché, il affiche **USB DEBRANCHE · BATTERIE 97%** et les délais normaux d'extinction repartent de là (6).
- **Connecté au téléphone** (ex. avec une batterie externe pendant le match), le score a la priorité : seulement un court message *EN CHARGE 78%*, et un appui long sur KEY1 affiche *CHARGE TERMINEE* ou *EN CHARGE*.
- **Dans l'application** : la page 2 et les réglages du bracelet affichent *En charge 78 %* ou *Charge terminée* ; en match, les pastilles J1/J2 portent le symbole ⚡. Le journal `files/battery_log.csv` a aussi deux colonnes supplémentaires (tension USB, charge terminée) : utile pour vérifier en combien de temps il se recharge vraiment.
- Le bracelet et l'application calculent désormais le pourcentage avec la **même courbe** LiPo (avant, le bracelet utilisait une droite moins précise) : ils affichent donc le même chiffre.

> À vérifier avec les vrais bracelets : la charge n'a pas pu être testée sur le matériel. En particulier, à quelle fréquence le chargeur signale *charge terminée* et quelle est la précision du pourcentage pendant la charge.

## 7. Utiliser l'application

1. **Nouveau match** (facultatif) : club, court, simple/double, noms (en double, deux noms par équipe). **Suivant**.
2. **Mode et règles** :
   - *Arbitre* ou *Bracelets*. Avec les bracelets, le Bluetooth activé, l'autorisation de localisation et la localisation activée sont obligatoires : les lignes des **Prérequis** se mettent à jour en temps réel (même si vous coupez le Bluetooth ou la localisation depuis le volet des réglages rapides) et deviennent rouges, avec le bouton pour corriger.
   - La **recherche est automatique et continue** tant que la page est ouverte : allumez les bracelets et ils se placent tout seuls sur les emplacements libres (d'abord Joueur 1, puis Joueur 2). **Identifier** fait clignoter le bracelet concerné dans la couleur du joueur (jaune ou rouge) avec des bips, pour voir tout de suite lequel vous avez en main ; **Échanger J1 ↔ J2** les inverse sans les déconnecter. Dans le menu déroulant, vous pouvez toujours en choisir un autre ou *Aucun* (un bracelet retiré à la main n'est pas remis par la recherche). Un bracelet ne peut pas être attribué à deux joueurs.
   - **Éteindre les bracelets en fin de match et en quittant** (activé par défaut) : à la confirmation de *Match terminé* et avec *Quitter*, les bracelets s'éteignent au lieu d'attendre l'inactivité.
   - En mode arbitre, si vous appuyez sur **Suivant** sans localisation, l'application vous invite à l'activer (sinon le lieu ne figurera pas dans le récapitulatif).
   - Langue (six langues, 4.1), voix activée ou non, format (*3 manches · jeu décisif à 7* ou *2 manches + super jeu décisif à 10*), No-Ad.
   - **Tirage au sort** : la pièce tourne et désigne le gagnant ; vous indiquez qui sert et les côtés du court **vus depuis la chaise d'arbitre** (schéma du court avec **Inverser les côtés**). En double, choisissez aussi qui sert en premier dans chaque équipe.
3. **DÉBUT DU MATCH** clignote : appuyez sur le bouton ou sur KEY1 d'un bracelet. La voix annonce *« Première manche » · « au service [nom] » · « jouez »* avec 2 secondes entre les phrases ; le **Match Time** démarre sur « jouez ».
4. **Match** : en haut à gauche la durée du match, à droite le compte à rebours (**Shot Clock** 25 s, **Changeover Time**, **Set Break Time** ; en rouge pendant les 5 dernières secondes). Le cadre orange s'allume 5 s pour *changement de côté, jeu décisif, balle de set, balle de match, balle de break…*. Les deux boutons carrés (jaune = Joueur 1, rouge = Joueur 2) sont du côté où se trouvent réellement les joueurs et s'inversent à chaque changement de côté ; sous le serveur apparaît **On Serve**. En dessous : *Annuler le point*, *Suspendre/Reprendre*, audio (icône du haut-parleur, barrée = coupé), *Nouveau match*, **Quitter**. En mode bracelets, sous les temps se trouvent **J1**/**J2** avec la batterie et l'autonomie : les toucher ouvre les réglages du bracelet.
5. Au dernier point apparaît la fenêtre **Match terminé / Annuler le dernier point**. « Match terminé » se confirme **uniquement sur le téléphone**.
6. **Récapitulatif** : vainqueur, noms, score manche par manche avec les points du jeu décisif, durée, heures de début et de fin, date, club, court, lieu, format, points et jeux gagnés. Boutons **Enregistrer dans l'historique** (nom du fichier + dossier au choix + formats .txt/.json/.png), **Partager** (image 1080×1350 + texte pour WhatsApp/Instagram/…), **Nouveau match**, **Quitter**.

**Quitter l'application** : le bouton **Quitter** (pendant le match, il demande confirmation ; il existe aussi dans le récapitulatif) ferme vraiment l'application ; même chose si vous la retirez des applications récentes. À la réouverture, elle repart de la première page.

**Sauvegardes** : le match s'enregistre tout seul à chaque point. *Suspendre* arrête les temps ; si vous quittez, si le téléphone s'éteint ou si l'application est fermée, le match se retrouve dans **Reprendre un match suspendu** (page 3) et repart avec *Reprendre*. *Annuler le point* recalcule tout depuis le début, et fonctionne donc même après la fin d'un jeu, d'une manche ou du match.

## 8. Tableau d'affichage sur TV ou écran

Le téléphone de l'arbitre fait office de **petit serveur** sur le réseau Wi-Fi : le tableau d'affichage est une page web façon LED (chiffres à 7 segments, jaune contre rouge, jeux et manches au centre, manches terminées et durée du match en bas à gauche, **SERVICE: 25 SEC** en bas à droite) qui se met à jour toute seule à chaque point. L'écran n'a pas besoin d'être « smart » et aucun réseau du club n'est nécessaire : le **point d'accès** (partage de connexion) de l'un des deux téléphones suffit.

### 8.1 Les solutions possibles

| Comment l'image arrive à l'écran | Ce qu'il faut | Avantages | Inconvénients |
|---|---|---|---|
| **Deuxième téléphone avec sortie vidéo** + câble USB-C/HDMI, app TSM en mode *Utiliser comme tableau* | un téléphone qui sort la vidéo par l'USB-C (DisplayPort Alt Mode) | pas d'internet ; le tableau occupe tout l'écran en 16:9 et le téléphone reste libre | beaucoup de téléphones **ne** sortent **pas** la vidéo : en général oui pour les Galaxy S/Note/Tab S (avec DeX : choisissez *Duplication d'écran* ou désactivez le démarrage automatique de DeX), non pour presque tous les Galaxy A. Cherchez « DisplayPort » / « sortie vidéo » dans la fiche technique |
| **Chromecast** (ou Google TV Streamer) sur l'écran + n'importe quel téléphone avec TSM en mode *Utiliser comme tableau* et **Caster** (*Diffusion de l'écran* jusqu'à Android 14, Smart View sur les Samsung) | un Chromecast configuré une fois avec Google Home sur le réseau du point d'accès | n'importe quel téléphone convient, pas de long câble | le Chromecast a besoin d'**internet** (données mobiles sur le point d'accès) ; environ 1 s de retard ; la diffusion montre l'écran du téléphone (à l'horizontale) |
| **Navigateur** sur n'importe quel appareil branché à l'écran (ordinateur portable, tablette, box TV, Fire TV Stick…) | scanner le QR code ou taper l'adresse | aucune application à installer | l'écran se met en veille tout seul si vous ne le réglez pas ; il faut toucher **PLEIN ÉCRAN** à chaque ouverture |

**Pourquoi pas en Bluetooth** : le téléphone de l'arbitre gère déjà les deux bracelets en Bluetooth, où la réactivité des boutons compte ; le Wi-Fi est séparé, plus rapide et porte plus loin. **Pourquoi pas directement du téléphone de l'arbitre au Chromecast** (sans deuxième téléphone) : c'est faisable, mais il faut une application « récepteur » enregistrée auprès de Google (Google Cast Developer Console, 5 $ une fois pour toutes) et publiée sur un site https ; c'est une évolution possible.

**Conseils réseau** :
- Le tableau échange très peu de données (un message à chaque point et toutes les 5 secondes) et ne consomme pas de trafic internet.
- Si le téléphone de l'arbitre fait point d'accès, choisissez dans les réglages du point d'accès la bande **5 GHz** si elle est disponible : le Bluetooth des bracelets travaille à 2,4 GHz, et ainsi ils ne se gênent pas.
- Avec le Chromecast, il vaut mieux que le point d'accès soit celui du téléphone de l'arbitre, avec les **données mobiles activées** ; le téléphone-tableau et le Chromecast se connectent à ce point d'accès.
- Sans Chromecast, l'inverse convient aussi (point d'accès sur le téléphone-tableau, comme dans l'idée d'origine) : l'application de l'arbitre reste connectée à ce Wi-Fi même s'il n'a pas accès à internet.

### 8.2 Sur le téléphone de l'arbitre

1. Page 2 › **Tableau d'affichage TV** › activez **Tableau d'affichage sur TV ou écran**.
2. L'**adresse** (ex. `192.168.43.1:8080`), le **QR code** et le nombre de tableaux connectés apparaissent. S'il est écrit *Aucun réseau*, activez le point d'accès ou connectez-vous à celui de l'autre téléphone.
3. **Aperçu sur ce téléphone** ouvre le tableau dans le navigateur du téléphone lui-même.
4. **Apparence du tableau** : couleur de chaque joueur (8 couleurs), durée du match, chrono du service et des pauses, manches terminées, messages (balle de break, balle de set, changement de côté…), balle à côté du serveur, segments éteints visibles, texte en bas (vide = club et court de la page 1). Les modifications arrivent aussitôt sur l'écran.
5. En match, en haut au centre, se trouve **TV · 1** (tableaux connectés) : touchez-le pour revoir l'adresse et le QR code.

Tant que le tableau est activé, un service au premier plan (notification *Match en cours · tableau TV actif*) maintient le serveur en marche même écran éteint, y compris en mode arbitre. Le tableau est en **lecture seule** : on ne peut rien y modifier.

Ce qu'il affiche en plus du score : *EN ATTENTE DU MATCH* avant le début, *PRÊTS À JOUER* sur la page DÉBUT DU MATCH, **JEU DÉCISIF** / **SUPER JEU DÉCISIF** à la place de *VS*, *MATCH SUSPENDU* en clignotant, **VAINQUEUR [nom]** en fin de match avec toutes les manches ; les avantages s'affichent **AD**, y compris sur les chiffres à LED.

### 8.3 Sur le téléphone-tableau

1. Page 1 › tout en bas, **Utiliser comme tableau**.
2. Le téléphone **cherche tout seul** le téléphone de l'arbitre (annonce sur le réseau et balayage du point d'accès, quelques secondes) et mémorise la dernière adresse. S'il ne le trouve pas : vérifiez le point d'accès et *Tableau d'affichage TV*, puis **Chercher à nouveau**, ou tapez l'adresse affichée par l'arbitre et **Connecter**.
3. Le tableau passe en plein écran, à l'horizontale, avec l'écran toujours allumé.
   - **Avec le câble HDMI** : le tableau s'affiche sur l'écran externe dans son format ; le téléphone affiche *Le tableau est sur l'écran externe* avec la luminosité au minimum (**Afficher aussi ici** pour le voir aussi sur le téléphone). Si on débranche le câble, il revient sur le téléphone.
   - **Avec le Chromecast** : ouvrez le volet des réglages rapides › **Caster** (ou **Diffusion de l'écran**) / **Smart View** › choisissez le Chromecast.
4. Si le téléphone de l'arbitre disparaît (hors de portée, application fermée), *CONNEXION PERDUE - RECONNEXION...* apparaît et, au bout de 20 secondes, le téléphone-tableau le recherche tout seul, même s'il a changé d'adresse.
5. Pour quitter : **retour deux fois**.

**Depuis un navigateur** (ordinateur portable, box TV) : scannez le QR code ou tapez l'adresse, puis touchez **PLEIN ÉCRAN** (le bouton apparaît quand on bouge la souris ou qu'on touche l'écran). Réglez la mise en veille de l'écran sur *jamais* : sur une page http, le navigateur ne peut pas le garder allumé tout seul.

**Aperçu sans téléphone** : `TennisScoreManager/app/src/main/assets/scoreboard.html?demo=1` dans un navigateur (aussi `&lang=en`, `fr`, `de`, `es`, `pt` et `&state=ad`, `tb`, `end`, `idle`, `doubles`) affiche le tableau avec des données de test.

## 9. Règles appliquées (ITF) et choix convenus

- **Jeu** : 0-15-30-40, égalité, avantage, jeu. **No-Ad** : à 40-40, point décisif (« égalité, point décisif »).
- **Manche** : 6 jeux avec 2 jeux d'écart (7-5) ; à **6-6, jeu décisif**.
- **Jeu décisif** : en 7 points avec 2 points d'écart ; le joueur dont c'est le tour sert le 1er point, puis 2 points chacun ; changement de côté **tous les 6 points** et à la fin ; celui qui a servi en premier dans le jeu décisif **relance** dans le premier jeu de la manche suivante.
- **Super jeu décisif** (format 2 manches) : à une manche partout, on joue en 10 points avec 2 points d'écart.
- **Changement de côté** (règle ITF 10) : après le 1er, 3e, 5e… jeu de chaque manche. En fin de manche, on ne change que si la manche compte un nombre impair de jeux (6-3, 7-6) ; sinon (6-4), on change après le premier jeu de la manche suivante. Le jeu décisif compte pour un jeu.
- **Temps** : shot clock de 25 s entre les points ; changeover de 90 s ; set break de 120 s en fin de manche. En plus, à la demande (ce n'est pas une règle ITF) : **30 s** pour changer de côté après le 1er jeu de chaque manche, à chaque changement de côté dans le jeu décisif et à 6-6 ; à la fin de toute pause, le shot clock démarre.
- **Double** : rotation du service A1-B1-A2-B2 pendant toute la manche, jeu décisif compris ; au début de chaque manche, l'application demande l'ordre (il peut changer, comme le prévoit le règlement).
- **Annonces** : score annoncé en commençant par le serveur (« quinze-zéro », « zéro-quarante », « quinze A », « égalité », « avantage Rossi ») ; en fin de jeu, « jeu Rossi, Rossi mène trois jeux à deux » / « deux jeux partout » + « changement de côté » quand on change ; à 6-6, « jeu Rossi, six jeux partout, jeu décisif » ; dans le jeu décisif, le score est annoncé en commençant par celui qui mène (« trois un Rossi », « six partout ») ; en fin de manche, « jeu Rossi, Rossi mène une manche à zéro » / « une manche partout » ; en fin de match, « jeu, set et match Rossi, six quatre, trois six, sept cinq » ; « correction » + le score quand on annule un point.

**Choix faits par rapport à la demande initiale, pour suivre l'ITF :**
1. **À 6-6, on ne change pas de côté** (12 jeux, un nombre pair) : l'application fait la pause de 30 s et annonce « jeu décisif », mais n'annonce pas « changement de côté » et n'inverse pas les boutons. Le premier changement a lieu après 6 points du jeu décisif.
2. Le changement de côté en fin de manche dépend du nombre de jeux de la manche (voir ci-dessus), il n'a pas lieu à chaque fois.
3. À la fin d'une manche gagnée sans jeu décisif, l'application utilise la même formule qu'après un jeu décisif (« jeu Rossi, Rossi mène une manche à zéro »). Beaucoup d'arbitres annoncent plutôt le gain de la manche suivi de son score (« six quatre ») : c'est facile à modifier dans `Calls.kt`.
4. À une manche partout, dans le format avec super jeu décisif, la voix ajoute « super jeu décisif ».

## 10. Où intervenir dans le code

| Quoi | Fichier |
|---|---|
| Règles du score | `model/ScoreEngine.kt` (+ tests dans `app/src/test`) |
| Phrases et annonces vocales | `voice/Calls.kt` (construction), `voice/CallWords.kt` (mots et ordre de chaque langue) |
| Corrections de prononciation | `voice/Pronunciation.kt` |
| Temps (25/90/120/30 s), messages, déroulement du match | `MatchController.kt` |
| Textes de l'application | `ui/Strings.kt` (italien, anglais), `ui/StringsFr.kt`, `StringsDe.kt`, `StringsEs.kt`, `StringsPt.kt` |
| Ajouter une langue | une entrée dans `model/Rules.kt` (`Lang`), un `ui/StringsXx.kt`, un `XxWords` dans `voice/CallWords.kt`, une ligne dans `TXT[]` de `TSM_Band.ino` : les tests `LanguagesTest` et `StringsTest` indiquent ce qui manque |
| Protocole Bluetooth (UUID, messages, réglages) | `ble/BandProtocol.kt` et en tête de `TSM_Band.ino` |
| Estimation de l'autonomie | `ble/BatteryModel.kt` |
| Panneau des réglages du bracelet | `ui/BandSettingsPanel.kt` |
| Graphisme | `ui/screens/*.kt`, couleurs dans `ui/Theme.kt` |
| Tableau TV : page et apparence | `app/src/main/assets/scoreboard.html` |
| Tableau TV : données envoyées, serveur, réglages | `tv/TvModels.kt`, `tv/TvServer.kt`, `ui/TvSection.kt` |
| Téléphone utilisé comme tableau (recherche, écran externe) | `tv/DisplayActivity.kt`, `tv/ScoreboardFinder.kt` |
| Écran de charge du bracelet | `TSM_Band.ino` (`pollPower`, `drawCharge`) |


## 11. Problèmes courants

- **Bracelet introuvable** : Bluetooth et localisation activés (lignes vertes) ? Le bracelet clignote-t-il APPAIRAGE ? (S'il est déjà connecté à un autre téléphone, il n'est pas visible.) S'il s'est éteint tout seul entre-temps (30 s), rallumez-le d'un clic sur le bouton latéral.
- **Le panneau des réglages affiche « Le firmware de ce bracelet n'a pas de réglages »** : ce bracelet a encore l'ancien sketch, rechargez-le (chapitre 5).
- **Android Studio ne voit pas le téléphone** : Débogage USB activé et empreinte RSA acceptée sur le téléphone (3) ? Sous Windows, il faut parfois le pilote du fabricant, sous Linux les règles udev et une nouvelle session (1.1). Ou bien le débogage sans fil (3).
- **Arduino IDE n'affiche pas le port du bracelet** : câble USB-C de données et pas seulement de charge ; sous Linux, groupe `dialout` et nouvelle session (1.1) ; puis le mode téléchargement (5, point 7).
- **La voix ne parle pas ou lit mal** : volume multimédia, *Audio On*, voix de la langue choisie installée (4) ; essayez un autre moteur ou une autre voix dans *Audio et voix*.
- **Écran éteint pendant le match** : en mode bracelets, un service au premier plan (notification « Match en cours ») garde actifs le Bluetooth, la voix et les chronomètres ; en mode arbitre, l'écran reste allumé.
- **Échec de la synchronisation Gradle à cause du JDK** : réglez Gradle JDK = jbr-21 (point 2.6).
- **Le téléphone-tableau ne trouve pas l'arbitre** : même réseau ? (l'un des deux fait point d'accès, l'autre y est connecté). *Tableau d'affichage TV* activé sur le téléphone de l'arbitre ? Essayez l'adresse à la main. Certains points d'accès isolent les appareils connectés les uns des autres (« isolation des clients ») : si c'est le cas, désactivez-la.
- **Le navigateur du téléphone n'ouvre pas l'adresse** quand les données mobiles sont activées : Android envoie le trafic sur les données mobiles parce que le point d'accès n'a pas internet. Utilisez l'application en mode *Utiliser comme tableau* (elle gère cela toute seule) ou désactivez les données mobiles sur ce téléphone.
- **Écran noir avec le câble** : ce téléphone n'a pas de sortie vidéo sur l'USB-C (8.1), ou Samsung DeX a démarré : choisissez *Duplication d'écran*.
