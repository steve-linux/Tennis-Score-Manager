# Tennis Score Manager — Schritt-für-Schritt-Anleitung

[Italiano](GUIDA.md) · [English](GUIDE.en.md) · [Français](GUIDE.fr.md) · **Deutsch** · [Español](GUIDE.es.md) · [Português](GUIDE.pt.md)

Android-App (Kotlin + Jetpack Compose) zum Zählen im Tennis nach den ITF-Regeln, mit Ansagen wie vom Stuhlschiedsrichter **in sechs Sprachen** (Italienisch, Englisch, Französisch, Deutsch, Spanisch, Portugiesisch: Kapitel 4.1), zwei per Bluetooth LE verbundenen **M5StickS3**-Armbändern (oder zwei **Bluetooth-Selfie-Fernbedienungen** für wenige Euro: Kapitel 6.3) und einer **LED-Anzeigetafel auf TV oder Monitor** (Kapitel 8).

## 0. Was im Repository liegt

| Pfad | Wozu er dient |
|---|---|
| `TennisScoreManager/` | Das Android-Studio-Projekt. |
| `firmware/TSM_Band/TSM_Band.ino` | Die Firmware des Armbands. |
| `deliver/installa_tsm.sh` | Alternative zum Klonen: erzeugt das **gesamte** Android-Projekt und den Sketch allein aus `cat << 'TSM_EOF'`-Blöcken (das JAR des Gradle-Wrappers liegt base64-kodiert darin). |
| `deliver/GUIDE.de.md` | Diese Anleitung (auf Deutsch); das italienische Original ist `deliver/GUIDA.md`, die weiteren Übersetzungen sind `deliver/GUIDE.en.md`, `.fr`, `.es`, `.pt`. |

Verwendete und geprüfte Versionen: App **2.5.0** · Firmware **2.3** · Gradle 8.14.3 · Android Gradle Plugin 8.13.2 · Kotlin 2.2.21 · Compose BOM 2025.12.00 · compileSdk/targetSdk 36 · minSdk 26 (Android 8.0). Firmware: M5Unified ≥ 0.2.12, NimBLE-Arduino ≥ 2.1, ESP32-Core 3.x. Lizenz: GNU GPL Version 3 oder später (`LICENSE`); Version und Lizenz zeigt die App unten auf der ersten Seite.

---

## 1. Computer vorbereiten und Projekt herunterladen

Geeignet sind **Windows 10/11**, **Ubuntu** (22.04 oder neuer) und **Fedora**; auf anderen Linux-Distributionen gelten die Schritte für Ubuntu oder Fedora mit dem jeweiligen Paketmanager. In den Befehlen steht `~` für deinen persönlichen Ordner: unter Linux `/home/<benutzer>`, unter Windows `C:\Users\<benutzer>`.

### 1.1 Zu installierende Programme

| | Windows 10/11 (PowerShell) | Ubuntu | Fedora |
|---|---|---|---|
| **Git** (Projekt herunterladen und aktualisieren) | `winget install --id Git.Git -e` | `sudo apt install git` | `sudo dnf install git` |
| **Android Studio** (die App) | `winget install --id Google.AndroidStudio -e` | `sudo snap install android-studio --classic` | `.tar.gz`-Archiv von developer.android.com/studio, z. B. nach `~/android-studio` entpackt |
| **Arduino IDE 2** (die Armbänder) | `winget install --id ArduinoSA.IDE.stable -e` | Flatpak `cc.arduino.IDE2` (siehe unten) | `flatpak install flathub cc.arduino.IDE2` |
| **Telefon per Kabel angeschlossen** | USB-Treiber des Herstellers, falls nötig (siehe unten) | `sudo apt install android-sdk-platform-tools-common` | `sudo dnf install android-tools` |
| **Serielle Schnittstelle des Armbands** | nichts zu tun | `sudo usermod -aG dialout $USER` | `sudo usermod -aG dialout $USER` |

- **Windows**: `winget` ist in aktuellen Windows-10/11-Installationen schon vorhanden und wird in der *PowerShell* oder im *Terminal* verwendet. Du kannst auch die normalen Installationsprogramme von git-scm.com, developer.android.com/studio und arduino.cc/en/software herunterladen.
- **Ubuntu, Arduino IDE**: Falls Flatpak noch fehlt: `sudo apt install flatpak`, dann `flatpak remote-add --if-not-exists flathub https://dl.flathub.org/repo/flathub.flatpakrepo` und `flatpak install flathub cc.arduino.IDE2`; melde dich ab und wieder an, damit es im Menü erscheint. Das AppImage von arduino.cc funktioniert auch, braucht auf aktuellen Ubuntu-Versionen aber zusätzliche Pakete und Optionen: Flatpak ist einfacher.
- **Linux, Android Studio aus dem Archiv**: Gestartet wird es mit `bin/studio.sh` im entpackten Ordner (z. B. `~/android-studio/bin/studio.sh`). Unter Ubuntu geht auch das Snap; unter Fedora gibt es außerdem das Flatpak `com.google.AndroidStudio`, aber das offizielle Archiv macht mit angeschlossenem Telefon weniger Probleme.
- **Linux, Gruppe `dialout`** (serielle Schnittstelle) und Pakete für das Telefon (udev-Regeln): Nach der Installation **melde dich ab und wieder an** (oder starte neu), sonst bleiben Schnittstelle und Telefon unzugänglich.
- **Windows, Telefontreiber**: Viele Telefone funktionieren sofort. Wenn Android Studio das Telefon nicht sieht, installiere den Treiber des Herstellers: *Samsung Android USB Driver* (von der Samsung-Developer-Website) für Samsung-Geräte, *Google USB Driver* (Android Studio › **Tools › SDK Manager › SDK Tools**) für Pixel. Das Armband braucht keinen Treiber: Es erscheint als Port `COM3`, `COM4` …
- Beim ersten Start öffnet Android Studio einen Einrichtungsassistenten: Wähle **Standard** und lass ihn das Android SDK herunterladen (braucht Internet, einige GB).

### 1.2 Projekt herunterladen

Das Repository auf GitHub ist **privat**: Du brauchst ein GitHub-Konto, dem der Eigentümer Zugriff gegeben hat.

**A. Mit git (empfohlen: Aktualisieren geht dann mit `git pull`)**

Linux (Ubuntu, Fedora), im Terminal:

```bash
mkdir -p ~/AndroidStudioProjects
cd ~/AndroidStudioProjects
git clone https://github.com/steve-linux/Tennis-Score-Manager.git
```

Windows, in der PowerShell:

```powershell
mkdir -Force $HOME\AndroidStudioProjects
cd $HOME\AndroidStudioProjects
git clone https://github.com/steve-linux/Tennis-Score-Manager.git
```

- **Anmeldung**: Unter Windows öffnet git von selbst das Anmeldefenster von GitHub. Unter Linux akzeptiert GitHub bei `git clone` kein Passwort: Am einfachsten ist GitHub CLI (`sudo apt install gh` oder `sudo dnf install gh`), dann `gh auth login` und im Ordner `~/AndroidStudioProjects` `gh repo clone steve-linux/Tennis-Score-Manager`.
- Das Repository landet in `~/AndroidStudioProjects/Tennis-Score-Manager`. Das Projekt, das du in Android Studio öffnest, ist sein Unterordner **`TennisScoreManager`**; der Sketch des Armbands liegt in `firmware/TSM_Band/`.
- Aktualisieren: `git pull` im Ordner `Tennis-Score-Manager`.

**B. Ohne git**: Auf der GitHub-Seite des Projekts (angemeldet) **Code › Download ZIP**, dann das ZIP entpacken, z. B. nach `~/AndroidStudioProjects`. Für eine neue Version lädst du das ZIP erneut herunter.

**C. Mit dem Skript** `deliver/installa_tsm.sh`, das das ganze Projekt in einer einzigen Textdatei enthält (`cat << 'TSM_EOF'`-Blöcke). Es läuft unter Linux oder, unter Windows, in der *Git Bash* (wird mit Git installiert):

```bash
bash installa_tsm.sh
```

- Das Projekt kommt nach `~/AndroidStudioProjects/TennisScoreManager`, der Sketch nach `~/Arduino/TSM_Band/TSM_Band.ino`.
- Gibt es den Projektordner schon, wird er nach `TennisScoreManager.backup-JJJJMMTT-hhmmss` **verschoben** (alte XML-Layouts und alte Klassen würden den neuen Build scheitern lassen).
- Für andere Ordner: `bash installa_tsm.sh /pfad/zum/projekt /pfad/zum/sketch`.

## 2. In Android Studio öffnen und bauen

1. Starte Android Studio: unter Windows über das Startmenü, unter Ubuntu (Snap) über das Anwendungsmenü, aus dem Archiv mit `bin/studio.sh` (1.1).
2. **File › Open** (oder **Open** im Begrüßungsfenster) → den Ordner `TennisScoreManager` wählen (im Klon oder im ZIP, oder den vom Skript erzeugten) → **Trust Project**.
3. Warte auf die Gradle-Synchronisierung (beim ersten Mal lädt sie Gradle, das Android-Plugin und die Bibliotheken herunter: Internet braucht es nur jetzt).
4. Erscheint *"Failed to find target android-36"*, klicke auf den Link **Install missing platform** (oder **Tools › SDK Manager › SDK Platforms › Android 16 (API 36)**).
5. Schlägt Android Studio den **AGP Upgrade Assistant** vor, kannst du ihn ignorieren: Diese Versionen wurden genau so gebaut und getestet.
6. JDK für Gradle: **Settings › Build, Execution, Deployment › Build Tools › Gradle › Gradle JDK** = *jbr-21* (das mitgelieferte, ist voreingestellt).
7. **Build › Make Project**: muss mit *BUILD SUCCESSFUL* enden.
8. Optional: Die Tests (95: Regeln, Stimme, Sprachen, Akku und Laden, Armband-Einstellungen, TV-Anzeigetafel) startest du per Rechtsklick auf `app/src/test` › **Run Tests**.

## 3. App auf dem Telefon installieren

1. Auf dem Telefon: **Einstellungen › Über das Telefon** (bei Samsung **Telefoninfo › Softwareinformationen**) → 7-mal auf *Build-Nummer* tippen → **Entwickleroptionen › USB-Debugging** einschalten.
2. Kabel anschließen, den RSA-Fingerabdruck bestätigen, oben das Telefon auswählen und **▶ Run** drücken.
   - Wenn der Computer das Telefon nicht sieht: unter Windows den USB-Treiber des Herstellers, unter Linux das Paket mit den udev-Regeln (1.1); dann das Kabel neu einstecken. Alternativ ohne Kabel und Treiber: **Device Manager › Pair devices using Wi-Fi** (Debugging über WLAN, Android 11+, Computer und Telefon im selben Netz).
3. Alternativ **Build › Build App Bundle(s) / APK(s) › Build APK(s)**, die APK auf das Telefon kopieren und installieren (die Installation unbekannter Apps muss erlaubt sein).

## 4. Die Stimme (funktioniert ohne Internet)

- Jede Ansage wird **in einem einzigen Satz** von der Sprachausgabe des Telefons mit einer installierten Stimme gesprochen: kein Internet und eine natürliche Satzmelodie. Die Namen der Spieler sind Teil des Satzes.
- Seite 2 › **Audio und Stimme**:
  - **Sprachausgabe-Engine**: *Standard des Telefons* (bei Samsung ist das Samsung TTS) oder eine Engine deiner Wahl, z. B. *Sprachdienste von Google*. In den Tests, auf Italienisch durchgeführt, klang Google natürlicher.
  - **Stimme**: *Automatisch (beste offline)* oder eine bestimmte Stimme (Google führt seine Stimmen mit einem Code auf, als *Stimme …*; die als *online* markierten brauchen Internet).
  - **Stimme testen**: spricht eine Folge von Beispielansagen mit den eingegebenen Namen. Während des Sprechens wird die Taste zu **Test beenden**: Tippe erneut darauf, um abzubrechen (der Test stoppt auch von selbst, wenn du die Seite verlässt). Die Sprachausgabe lässt sich nicht mitten im Satz anhalten, deshalb beendet die Taste sie; drückst du erneut, beginnt sie wieder von vorn.
- Fehlt die Stimme der gewählten Sprache: **Einstellungen › Allgemeine Verwaltung › Sprache › Text-zu-Sprache** (oder *Text-zu-Sprache-Ausgabe*) → die Stimme dieser Sprache für die gewählte Engine herunterladen (die App zeigt auch die Taste **Stimme installieren**). Startet die Sprachausgabe gar nicht, sagt die App das mit einer eigenen Meldung und der Taste **Einstellungen öffnen** (Sprachausgabe-Einstellungen von Android).
- **Aussprache**: Manche Engines sprechen Tenniswörter falsch aus (auf Italienisch z. B. „primo set“ als „primo settembre“, „tie-break“ als „time break“). Die App korrigiert sie selbst (`voice/Pronunciation.kt`); die Korrekturen wurden geprüft, indem das echte Audio von Samsung und Google transkribiert wurde.
- **Eigene Aufnahmen** (die natürlichste Stimme überhaupt: deine oder die eines Schiedsrichters): Im Ordner `Android/data/io.github.steve_linux.tsm/files/voice/` liegt `LEGGIMI.txt` (die „Lies mich“-Datei; die Erklärungen oben stehen in der Sprache der App und werden bei einem Sprachwechsel neu geschrieben) mit der Liste der 85 Schlüssel und Sätze. Nimm die Dateien unter diesen Namen auf (`score_1_0.mp3` = „fünfzehn null“ …), pack sie in ein ZIP mit einem Ordner pro Sprache (`it/`, `en/`, `fr/`, `de/`, `es/`, `pt/`) und verwende **ZIP importieren**. `LEGGIMI.txt` hat eine Spalte pro Sprache, durch Tabulatoren getrennt, und lässt sich daher auch gut als Tabelle öffnen. Aufnahmen haben immer Vorrang vor der Sprachausgabe; die Namen spricht weiterhin die Sprachausgabe. Im ZIP zählt der Ordner, in dem die Datei liegt (auch innerhalb anderer Ordner, z. B. `voice/de/`); Dateien außerhalb eines Sprachordners gehen an die aktuelle Sprache, `tts/`-Ordner werden ignoriert – man kann also auch den Ordner `voice/` der App zippen. Ein beschädigtes oder unvollständiges ZIP ändert nichts (*ZIP nicht lesbar oder unvollständig*). **Aufnahmen entfernen** fragt nach. Lässt sich eine Datei nicht abspielen, spricht die Sprachausgabe diesen Satz, und die Datei wird bis zum Neustart der App nicht mehr verwendet.
- **Vorab erzeugte Audiodateien verwenden** (optional): Mit **Dateien erzeugen** legt die App einmalig die 85 Dateien mit der gewählten Stimme an (auch mit einer *Online*-Stimme, wenn gerade Internet da ist) und verwendet sie dann statt der laufenden Sprachausgabe. Das klingt eher „zusammengestückelt“, ist aber praktisch, um eine Online-Stimme offline mitzunehmen. Die bisherigen Dateien bleiben, bis die neuen fertig sind (kommt die Erzeugung nicht ans Ende: *Erzeugung nicht abgeschlossen*); währenddessen sind Sprache, Engine, Stimme, Stimmtest und Audio gesperrt.
- **Externer Lautsprecher**: einfach per Bluetooth mit dem Telefon koppeln; die Stimme kommt über den Medienkanal (Medienlautstärke einstellen).
- **Bluetooth-Kopfhörer oder -Ohrhörer**: werden genauso gekoppelt, und nur du hörst die Stimme, ohne die Nachbarplätze zu stören; praktisch auch für alle, die vom Platzrand aus zählen. Die Armbänder können gleichzeitig verbunden bleiben.
- **Zwei Audiogeräte gleichzeitig**: Viele Telefone geben den Ton auf zwei Bluetooth-Geräten zugleich aus. Bei Samsung: beide verbinden, Schnelleinstellungen › **Medienausgabe** öffnen und beide anhaken. So hat jeder Spieler seine eigenen Ohrhörer, oder der Lautsprecher spielt für den Platz und Kopfhörer für die Person, die zählt.

### 4.1 Sprachen (App 2.3, Firmware 2.2)

Seite 2 › **Sprache**: Italiano, English, Français, Deutsch, Español, Português (jede Sprache ist in der Sprache selbst geschrieben, so findet man sie auch dann wieder, wenn die App auf eine Sprache eingestellt ist, die man nicht lesen kann). Die Wahl gilt zugleich für **Bildschirme, Schiedsrichterstimme, TV-Anzeigetafel, Zusammenfassung/Teilen und Armbänder**. Das Datum folgt der Sprache (*mercredi 30 septembre 2026*, *Mittwoch, 30. September 2026* …).

Die Ansagen sind keine Wort-für-Wort-Übersetzungen: Sie folgen den offiziellen Texten für Stuhlschiedsrichter des jeweiligen Verbands, also FFT *L'arbitrage en 255 questions* und ITF auf Französisch, den Unterlagen von DTB/BTV und Swiss Tennis, RFET *Deberes y procedimientos* und FPT *Deveres e Procedimentos* (dem einzigen veröffentlichten portugiesischen Ansagetext). Das Englische folgt der ITF. Im Englischen heißt die Null in den Satzergebnissen am Matchende *love* („six love, six four“), wie bei den Punkten.

| | Italiano | English | Français | Deutsch | Español | Português |
|---|---|---|---|---|---|---|
| Beginn | primo set · Rossi al servizio · gioco | first set · Rossi to serve · play | première manche · au service Rossi · jouez | erster Satz · Aufschlag Rossi · spielen | primer set · al servicio Rossi · jueguen | primeiro set · Rossi ao serviço · joguem |
| 15:0 · 15:15 | quindici zero · quindici pari | fifteen love · fifteen all | quinze-zéro · quinze A | fünfzehn null · fünfzehn beide | quince cero · quince iguales | quinze-zero · quinze iguais |
| 40:40 · Vorteil | parità · vantaggio Rossi | deuce · advantage Rossi | égalité · avantage Rossi | Einstand · Vorteil Rossi | iguales · ventaja Rossi | iguais · vantagem Rossi |
| Spielende | gioco Rossi, Rossi conduce tre giochi a due | game Rossi, Rossi leads three games to two | jeu Rossi, Rossi mène trois jeux à deux | Spiel Rossi, Rossi führt drei zu zwei | juego Rossi, Rossi gana tres juegos a dos | jogo Rossi, Rossi vence por três jogos a dois |
| Gleichstand in Spielen | due giochi pari | two games all | deux jeux partout | zwei beide | dos juegos iguales | dois jogos iguais |
| Tie-Break | tre a uno Rossi · sei pari | three one Rossi · six all | trois un Rossi · six partout | drei zu eins Rossi · sechs beide | tres uno Rossi · seis iguales | três a um Rossi · seis iguais |
| Satz | un set a zero · un set pari | one set to love · one set all | une manche à zéro · une manche partout | eins zu null in Sätzen · ein Satz beide | un set a cero · un set iguales | um set a zero · sets iguais |
| Matchende | gioco, set, partita Rossi | game, set and match Rossi | jeu, set et match Rossi | Spiel, Satz und Sieg Rossi | juego, set y partido Rossi | jogo, set e partida Rossi |
| Seitenwechsel | cambio campo | change ends | changement de côté | Seitenwechsel | cambio de lado | troca de lado |

Besonderheiten:
- Im Französischen und Spanischen steht der Name **nach** „au service“/„al servicio“, im Deutschen nach „Aufschlag“. Das Französische nennt den Satz **manche** (außer in „jeu, set et match“) und den Tie-Break **jeu décisif**.
- **10:6 … 10:9** im Match-Tie-Break: Auf Französisch, Spanisch und Portugiesisch sagt man „dix **à** huit“, „diez **a** ocho“, „dez **a** oito“, weil „dix huit“ / „diez ocho“ / „dez oito“ wie *achtzehn* klingen.
- **Portugiesisch**: nur eine Option, mit Oberfläche und bevorzugter Stimme aus Brasilien (fehlt die brasilianische Stimme, wird die portugiesische verwendet) und den Ansagen aus dem FPT-Text, mit Wörtern, die in beiden Ländern gelten („jogo“ und nicht „game“, „partida“). Aus „Um set a um“ wurde „sets iguais“: Im Singular klingt *set* wie *sete* (sieben), und es hörte sich nach 7:1 an.
- **Aussprache** (`voice/Pronunciation.kt`): Alle Sätze aller Sprachen wurden von den Google-Stimmen vorgelesen und mit whisper transkribiert. Hinzugekommene Korrekturen: Im Deutschen wird „Tie-Break“ als „Taibreak“ gesprochen, mit einem Komma davor (sonst „Teilbreg“ oder „bei Detailbreak“); im Französischen wird das einzelne „à“ der vorab erzeugten Dateien als „a“ gesprochen (sonst „a accent grave“). **Samsung TTS ist in den neuen Sprachen noch nicht getestet.**
- **Armbänder** (Firmware 2.2): Die App schickt die Sprache von selbst bei jeder Verbindung und bei jeder Änderung; das Armband speichert sie und verwendet sie auch ohne Verbindung (Telefonsuche, Laden, Ausschalten), ohne Umlaute und Akzente, weil die Schrift nur ASCII kennt (*EN CHARGE*, *LAEDT*, *CARGANDO* …). Mit Firmware 2.1 oder älter sind die Meldungen, die die App schickt, übersetzt; die internen Texte des Armbands bleiben italienisch. Ein frisch programmiertes Armband startet auf Italienisch; ab Firmware 2.2.2 wird seine Verbindungsmeldung in der Sprache der App neu gezeichnet, sobald die App sie schickt, etwa eine Sekunde nach dem Verbinden.

## 5. Firmware der Armbänder (Arduino IDE)

1. Installiere Arduino IDE 2 (1.1). Unter Linux brauchst du außerdem die Berechtigung für die serielle Schnittstelle (Gruppe `dialout`, 1.1), nachdem du dich ab- und wieder angemeldet hast.
2. **File › Preferences › Additional boards manager URLs**:
   `https://static-cdn.m5stack.com/resource/arduino/package_m5stack_index.json`
3. **Boards Manager** → nach **M5Stack** suchen → Version **≥ 3.2.5** installieren.
4. **Library Manager** → **M5Unified** installieren ("Install all" für M5GFX bestätigen) und **NimBLE-Arduino** von *h2zero* (≥ 2.1).
5. **File › Open** → `firmware/TSM_Band/TSM_Band.ino` aus dem Klon oder dem ZIP (oder `~/Arduino/TSM_Band/TSM_Band.ino`, wenn du das Skript verwendet hast). Ab Firmware 2.4 enthält der Ordner `TSM_Band` auch `swing_detector.cpp/.h` und `stroke_classifier.cpp/.h`: Sie müssen neben `TSM_Band.ino` liegen (wenn du nur die `.ino`-Datei kopierst, schlägt das Kompilieren fehl).
6. **Tools › Board › M5Stack › M5StickS3**, **Tools › Port** → der Port des Armbands: unter Linux `/dev/ttyACM0`, unter Windows `COM3`, `COM4` … (der, der beim Einstecken des Kabels erscheint).
   *(Ohne das M5Stack-Paket geht auch "ESP32S3 Dev Module": USB CDC On Boot = Enabled, Flash Size = 8MB, Partition = 8M with spiffs.)*
7. **Upload (→)**. Wenn der Port nicht erscheint oder das Hochladen fehlschlägt: ein anderes USB-C-Kabel probieren (manche taugen nur zum Laden), dann die **Seitentaste** lange gedrückt halten (Download-Modus) und es erneut versuchen; im Download-Modus kann sich unter Windows die Nummer des COM-Ports ändern.
   **Nach dem Hochladen**: Bleibt das Display schwarz (das Armband ist im Programmiermodus geblieben), drücke **einmal** die Seitentaste: Es startet mit dem neuen Programm.
8. Beim Start zeigt das Armband seinen Namen, z. B. **TSM-3FA2** (in der App änderbar, siehe 6.1). Wiederhole das Ganze für das zweite Armband.

> **Firmware 2.4** (mit App 2.5): Schlagerkennung (6.4); die Kalibrierungsbeispiele bleiben im Armband gespeichert. **Firmware 2.3** (mit App 2.4): Der Piepton von KEY1/KEY2 kommt, wenn das Telefon den Tastendruck empfangen hat; Tastendrücke während einer kurzen Unterbrechung werden gesendet, sobald die Verbindung wieder steht; *NICHT GESENDET*, wenn das Telefon nicht bestätigt (6). Nach dem Ausschalten mit angeschlossenem Kabel schalten es auch KEY1 oder KEY2 wieder ein; fehlgeschlagene Akkumessungen kommen nicht mehr als 0 % in der App an. Mit einer älteren App funktioniert ein 2.3-Armband wie bisher. **Firmware 2.2.2**: Die Verbindungsmeldung wechselt in die Sprache der App, sobald diese ankommt (4.1); Spanisch *VINCULANDO…/VINCULADA* und Portugiesisch *PAREADA* wie in der App, Französisch *MANCHES* auf dem Spielstand-Bildschirm. **Firmware 2.2.1**: auch der Countdown vor dem Ausschalten in der Sprache der App (6). **Firmware 2.2**: die Texte des Armbands in der Sprache der App (4.1). **Firmware 2.1**: der Ladebildschirm (6.2); die Einstellungen, *Erkennen* und das Ausschalten aus der App brauchen mindestens 2.0. Lade `TSM_Band.ino` auf **beide** Armbänder; bei alter Firmware sagt die App das im Einstellungsbereich, und alles andere funktioniert weiter.

## 6. Die Armbänder benutzen

| Taste | Aktion |
|---|---|
| **KEY1** (vorn) kurz | Punkt für den Träger des Armbands (startet auch das Match auf der Seite MATCHBEGINN); ein Piepton bestätigt (Firmware 2.3 + App 2.4: sobald das Telefon ihn empfangen hat, etwa eine halbe Sekunde später) |
| **KEY1** lang (1 s) | zeigt den Akku (und hält das Armband eingeschaltet, wenn es sich gleich wegen Inaktivität ausschalten würde) |
| **KEY2** kurz | nimmt den letzten Punkt zurück (auch im Popup am Matchende); zwei tiefere Pieptöne (mit Firmware 2.3 bei der Bestätigung durch das Telefon) |
| **KEY2** lang (2 s) | schaltet das Armband aus (bei angeschlossenem Kabel steht dort *LAEDT WEITER*: Es lädt auch ausgeschaltet; ab Firmware 2.3 schalten es auch KEY1 oder KEY2 wieder ein) |
| Seitentaste | ein Klick schaltet ein; Doppelklick schaltet aus (Hardwarefunktion) |

- Beim Einschalten blinkt **KOPPELN...** (0,35 s an alle 2 s, um Strom zu sparen). Die App verbindet sich von selbst mit den Armbändern, die sie kennt, sobald sie geöffnet ist; neue findet sie auf Seite 2.
- Verbunden: **GEKOPPELT** für 3 Sekunden mit zwei Pieptönen, dann **GEKOPPELT MIT** + der Name des Spielers.
- Bei jedem Punkt geht das Display an und zeigt den Spielstand des laufenden Spiels groß (links deiner, rechts der des Gegners; der grüne Ball zeigt, wer aufschlägt), dann geht es wieder aus. Am Spielende zeigt es Spiele und Sätze. Bestätigt das Telefon einen Tastendruck nicht innerhalb von 8 Sekunden (Verbindung verloren), erscheint **NICHT GESENDET** in Rot mit zwei tiefen Pieptönen: Drücke erneut, sobald die Verbindung wieder steht. Ein Tastendruck während einer kurzen Unterbrechung geht dagegen nicht verloren: Er wird gesendet, sobald sich das Armband wieder verbindet, und erst dann piept es.

**Automatisches Ausschalten** (die Zeiten änderst du in der App, 6.1):

| Situation | Was das Armband macht | Standard |
|---|---|---|
| Eingeschaltet, aber kein Telefon verbindet sich | blinkt KOPPELN, schaltet sich dann aus | **30 s** |
| Telefon verloren (aus, außer Reichweite, Bluetooth aus, App abrupt beendet) | blinkt VERBINDE NEU und verbindet sich von selbst wieder, sobald es kann; sonst schaltet es sich aus | 3 min |
| Verbunden, aber inaktiv (kein Punkt, keine Meldung) | warnt 30 s vorher mit **INAKTIV · KEY1 GEDRUECKT HALTEN** und einem Piepton, schaltet sich dann aus | 30 min |
| Match beendet (am Telefon bestätigt) oder **Beenden** in der App | zeigt MATCH BEENDET / APP BEENDET und schaltet sich sofort aus | an (abschaltbar, 7.2) |
| Akku leer (unter 3,30 V bei zwei Messungen hintereinander) | zeigt AKKU LEER und schaltet sich aus, statt halb eingeschaltet zu bleiben | immer |

- In den letzten 10 Sekunden vor dem Ausschalten ohne Telefon zeigt es **AUSSCHALTEN · 8s - TASTE DRUECKEN** mit einem Piepton: Jede beliebige Taste lässt die Frist bis zum Ausschalten von vorn beginnen.
- Wenn es sich von selbst ausschaltet, meldet es das dem Telefon: Im Match zeigt der orange Kasten *ARMBAND 1 AUS (INAKTIV)*, *(AKKU LEER)* … Nach dem Ausschalten am Matchende sucht das Telefon nicht mehr ständig danach und verbindet sich von selbst, sobald du es wieder einschaltest.

**Akkulaufzeit**: Am meisten verbraucht die ESP32-S3-Platine bei verbundenem Bluetooth (etwa 35 mA): Der Arduino-Core ist so kompiliert, dass der tiefe Stromsparmodus (Light Sleep) bei eingeschaltetem Bluetooth nicht verfügbar ist, deshalb lässt sich der Verbrauch kaum weiter senken. Display, Helligkeit und Pieptöne kommen mit wenigen mA dazu. Mit dem 250-mAh-Akku liegt die Schätzung bei **etwa 7 Stunden bei voller Ladung**, mehr als jedes Match auf zwei Gewinnsätze. Der Einstellungsbereich zeigt die Schätzung live und korrigiert sie nach 20 Minuten Nutzung mit dem an diesem Armband **gemessenen** Verbrauch. Das vollständige Protokoll liegt in `files/battery_log.csv` der App.

Die übrigen Stromsparmaßnahmen: CPU mit 80 MHz, Display aus, wenn es nicht gebraucht wird, Bluetooth Low Energy (das Funkmodul wacht 2- bis 3-mal pro Sekunde auf, ein Tastendruck geht trotzdem innerhalb von ~120 ms raus), Piepser nur während der Pieptöne an, Mikrofon und 5 V aus, Bewegungssensor (IMU) nur mit Schlagerkennung an (6.4).

### 6.1 Armband-Einstellungen

Auf Seite 2 (**Einstellungen** unter dem Armband jedes Spielers) oder während des Matches (oben auf **S1**/**S2** tippen oder auf das Schieberegler-Symbol). Sie werden **im Armband** gespeichert und bleiben auch nach dem Ausschalten erhalten; bei jeder Änderung zeigt das Armband seinen Namen mit *EINSTELLUNGEN OK*. Mit Firmware 2.4 gibt es außerdem den Abschnitt **Schläge** (6.4).

| Einstellung | Werte | Standard |
|---|---|---|
| Name | bis zu 12 Zeichen (z. B. der Name des Spielers oder „S1“) | TSM-xxxx |
| Displayhelligkeit | 5–100 % | 20 % |
| Spielstand nach jedem Punkt sichtbar | Aus, 2, 3, 5, 8 s (die Zusammenfassung am Spielende bleibt 2 s länger) | 3 s |
| Lautstärke des Piepsers | Stumm–100 % | 50 % |
| Display gedreht | um es am anderen Handgelenk zu tragen | nein |
| Ausschalten: beim Einschalten ohne Telefon | 15 s – 5 min | 30 s |
| Ausschalten: Telefon verloren | 1–10 min | 3 min |
| Ausschalten: verbunden, aber inaktiv | 10–60 min | 30 min |

Darunter steht die **geschätzte Laufzeit** (bei voller Ladung und mit der aktuellen Ladung) mit dem Verbrauch nach Posten aufgeschlüsselt: Sie ändert sich schon, während du die Regler bewegst, noch vor dem Bestätigen. Dann folgen **Erkennen**, **Ausschalten** und **Auf das andere Armband kopieren** (gleiche Einstellungen, das andere Armband behält seinen Namen). **Ausschalten** fragt nach, auch während eines Matches.

### 6.2 Laden (Firmware 2.1)

Schließ das USB-C-Kabel an: Das Armband piept und zeigt 30 Sekunden lang den **Ladebildschirm**, dann geht das Display aus und leuchtet alle 10 Sekunden für 1,5 s auf (ein kurzer Blick, wie die Kontrollleuchte eines Ladegeräts). **Jede beliebige Taste** schaltet ihn für weitere 30 Sekunden ein. War das Armband aus, schalte es mit einem Klick auf die Seitentaste ein, um ihn zu sehen (geladen wird trotzdem, auch im ausgeschalteten Zustand).

```
 TSM-3FA2   USB 5.01V              ← Name und Spannung am Kabel
 ┌──────────┐
 │██████ ⚡  │▌   78%               ← Akkusymbol und Ladestand
 └──────────┘
        LAEDT                      ← oder VOLL GELADEN / USB-STROM
 4.12V  SEIT 42 MIN  ENDE ~25 MIN  ← Akkuspannung, bisherige Ladedauer, geschätztes Ende
```

- **Prozentwert**: Beim Laden liegt die gemessene Spannung höher als die tatsächliche (≈0,1 V); das Armband korrigiert das, lässt den Wert nie sinken und erreicht **100 %** erst, wenn das Ladegerät meldet, dass es fertig ist. Bei voller Ladung wechselt die Anzeige auf **VOLL GELADEN** mit der benötigten Zeit (*GELADEN IN 1H 25*), und das Display bleibt aus (kein Aufleuchten in der Nacht).
- **Geschätztes Ende**: Der Power-Chip (PM1) misst den Ladestrom nicht, deshalb wird die Restzeit daraus abgeleitet, wie stark die Ladung in den letzten 10 Minuten gestiegen ist: Sie erscheint nach 10 Minuten, auf 5 gerundet, und ist eine Schätzung.
- **USB-STROM**: Das Kabel steckt, aber der Akku lädt nicht (und ist nicht voll): schwaches Kabel oder Netzteil, oder der Akku ist abgeklemmt.
- **Mit Kabel schaltet es sich nicht von selbst aus** (kein Ausschalten wegen fehlendem Telefon oder Inaktivität, kein *AKKU LEER*); ausschalten kannst du es mit KEY2 lang. Nach dem Abziehen des Kabels zeigt es **USB GETRENNT · AKKU 97%**, und ab dann laufen wieder die normalen Ausschaltzeiten (6).
- **Mit dem Telefon verbunden** (z. B. an einer Powerbank im Match) hat der Spielstand Vorrang: nur eine kurze Meldung *LAEDT 78%*, und KEY1 lang zeigt *VOLL GELADEN* oder *LAEDT*.
- **In der App**: Seite 2 und die Armband-Einstellungen zeigen *Lädt 78 %* oder *Voll geladen*; im Match tragen die Felder S1/S2 das Symbol ⚡. Auch das Protokoll `files/battery_log.csv` hat zwei Spalten mehr (USB-Spannung, voll geladen): praktisch, um nachzuprüfen, wie lange das Laden wirklich dauert.
- Armband und App berechnen den Prozentwert jetzt mit **derselben Kurve** des LiPo-Akkus (vorher nutzte das Armband eine weniger genaue Gerade), deshalb zeigen beide dieselbe Zahl.

> Mit echten Armbändern noch zu prüfen: Das Laden konnte nicht auf der Hardware getestet werden. Vor allem, wie oft das Ladegerät *voll geladen* meldet und wie genau der Prozentwert beim Laden ist.

### 6.3 Ohne Armbänder: Bluetooth-Fernbedienungen (App 2.5)

Die günstigste Lösung, damit die Spieler selbst zählen: zwei **Bluetooth-Selfie-Fernbedienungen** (die mit dem Auslöseknopf, oft als *AB Shutter3* verkauft), 1 bis 3 € pro Stück. Android sieht sie als Tastatur: keine Firmware zum Aufspielen und keine Berechtigungen für die App. Jedes andere Bluetooth- (oder USB-)Tastenfeld oder jede Tastatur geht auch.

1. Die Fernbedienung einschalten und in den **Bluetooth-Einstellungen** des Telefons koppeln, wie einen Kopfhörer (Seite 2 › *Bluetooth-Einstellungen* öffnet sie). Mit der zweiten wiederholen.
2. Seite 2 › Modus **Fernbedienungen** › **Zuordnen** unter Spieler 1 › die Taste seiner Fernbedienung drücken, die den Punkt geben soll; dann dasselbe für Spieler 2. Bei jedem Klick einer zugeordneten Fernbedienung zeigt ihre Zeile **Taste empfangen**: So lässt sich vor dem Spiel testen. **S1 ↔ S2 tauschen** vertauscht sie.
3. **MATCHBEGINN**: Ein Klick auf eine Fernbedienung startet das Match, wie KEY1 an den Armbändern. Oben sind **S1**/**S2** farbig, wenn die Fernbedienungen verbunden sind.

| Geste | Aktion |
|---|---|
| ein Klick | Punkt für den, der die Fernbedienung hält (kommt eine halbe Sekunde später: Die App wartet, ob es ein Doppelklick ist) |
| Doppelklick | macht den letzten Punkt rückgängig (auch im Popup am Matchende) |
| 1 s gedrückt halten | macht den letzten Punkt rückgängig |
| die andere Taste (Fernbedienungen mit zwei Tasten) | macht den letzten Punkt sofort rückgängig; innerhalb einer halben Sekunde nach einem Klick gedrückt, verwirft sie diesen Klick |

- Es gelten die Schutzregeln der Armbänder: Zwei Punkte im Abstand von weniger als 2 Sekunden zählen einmal, zwei kurz aufeinanderfolgende Rücknahmen entfernen nur einen Punkt, *Match beendet* wird nur am Telefon bestätigt.
- Viele Selfie-Fernbedienungen haben zwei Knöpfe (*iOS* und *Android*): Die beim Zuordnen gedrückte Taste gibt den Punkt, die andere macht rückgängig. Bei der CAMKIX (eine *AB Shutter3*) sendet der große Knopf LAUTER und der kleine EINGABE: Ordne sie mit dem großen Knopf zu, dann wird der kleine zu „rückgängig“. Sendet eine Fernbedienung bei jedem Druck zwei Tasten, bleibt ein Druck ein Klick.
- **Der Bildschirm muss mit der App im Vordergrund an bleiben** (während des Matches hält die App ihn selbst an). Anders als bei den Armbändern kommen die Tasten einer Bluetooth-Tastatur nur bei der sichtbaren App an: Bei gesperrtem Bildschirm oder einer anderen geöffneten App zählen sie nicht. Bei langen Matches das Telefon laden lassen.
- Trennt sich eine Fernbedienung während des Matches (aus, leer, zu weit weg), zeigt der orange Kasten *FERNBEDIENUNG 1 GETRENNT* und *VERBUNDEN*, wenn sie zurück ist. Viele Fernbedienungen schlafen nach einigen Minuten ohne Klick ein: Der erste Klick weckt sie und geht manchmal verloren. Sagt die Stimme den Punkt nicht an, noch einmal drücken.
- Reichweite: etwa zehn Meter, wie Bluetooth-Kopfhörer; mit dem Telefon am Netz, in Platzmitte, sind beide Seiten abgedeckt. Batterie: meist eine Knopfzelle CR2032, die monatelang hält.

### 6.4 Schlagerkennung (Firmware 2.4, App 2.5)

Mit Firmware 2.4 erkennt das Armband **Vorhand, Rückhand, Aufschlag und Volley** mit seinem Bewegungssensor (IMU) und schickt sie an die App, die sie pro Spieler zählt. Die Erkennung lernt von der Person, die das Armband trägt: Zuerst ist eine kurze **Kalibrierung** nötig, deren Beispiele im Armband gespeichert bleiben.

1. **Befestige das Armband am Handgelenk**, immer gleich (gleiches Handgelenk, gleiche Richtung). Die Erkennung beruht auf der Richtung der Bewegungen: Ein gegenüber der Kalibrierung gedrehtes Armband verwechselt die Schläge.
2. Armband-Einstellungen (6.1) › **Schläge** › **Kalibrierung**: Tippe neben *Vorhand* auf **Kalibrieren**. Das Armband zeigt *VORHAND · KALIBRIERUNG 0/20*.
3. Spiele **5-10 Vorhände** mit einer kurzen Pause dazwischen: Bei jedem Schlag steigt der Zähler, auf dem Armband und in der App (mit Piepton, wenn die Lautstärke nicht auf null steht). Ist ein Schlag misslungen, tippe auf **Letzten rückgängig**. Dann **Fertig**.
4. Wiederhole das für Rückhand, Aufschlag und Volley. Der Papierkorb löscht die Beispiele eines Schlags, um sie neu zu machen. Es werden höchstens 20 Beispiele pro Schlag behalten (darüber hinaus wird das älteste ersetzt). 3 Beispiele für mindestens zwei Schläge genügen (*Bereit: Es erkennt die kalibrierten Schläge*), aber mehr und vielfältigere Beispiele ergeben eine bessere Erkennung.
5. Schalte **Schlagerkennung** ein. Mit **Schlag auf dem Armband anzeigen** erscheint der Schlag kurz mit der Sicherheit der Erkennung (z. B. *RUECKHAND 87%*), nur wenn das Display gerade weder Spielstand noch Meldung zeigt.

- Während des Matches zeigt eine Zeile unter den Zeiten die Schläge jedes Spielers (*S1 VH 12 · RH 8 · A 5 · V 3*). Sie zählen erst ab Matchbeginn, nicht beim Einspielen oder Kalibrieren.
- Die Zusammenfassung (7) enthält die Schläge nach Art, das **Handgelenk-Tempo** (Durchschnitt und Maximum in °/s: wie kräftig geschlagen wurde, nicht die Ballgeschwindigkeit) und die **Schläge pro Punkt** (Länge der Ballwechsel). Hat nur ein Spieler ein Armband mit Schlägen, zählen die Ballwechsel nur seine Schläge.
- Ein Schlag, der keinem Beispiel ähnelt, wird nicht angezeigt und zählt als *nicht erkannt*: Passiert das oft, prüfe, wie du das Armband trägst, oder füge Beispiele hinzu.
- Langsame Bewegungen (sich neu aufstellen, den Ball aufheben) zählen nicht: Ein Schlag muss 400 °/s Handgelenkdrehung und 2,5 g Beschleunigung überschreiten.
- **Akku**: Der Sensor braucht etwa 1 mA mehr, rund zwanzig Minuten bei voller Ladung (die Schätzung in den Einstellungen berücksichtigt das). Ohne Schlagerkennung bleibt der Sensor aus.
- Bei sehr harten Schlägen erreicht das Gyroskop sein Maximum (2000 °/s): Die Erkennung leidet nicht darunter, aber das angezeigte Tempo ist etwas zu niedrig.

### 6.5 Brustgurte (App 2.5)

Herzfrequenz und Kalorien von **Bluetooth-Brustgurten** mit dem Standard-Herzfrequenzdienst: Coospo (getestet mit dem H6), Magene, Polar, Garmin, Wahoo… Sie funktionieren in jedem Modus, auch ohne Armbänder.

1. Befeuchte die Elektroden und lege den Gurt an: Er schaltet sich bei Hautkontakt selbst ein.
2. Seite 2 › **Herzfrequenz** › **Brustgurte** einschalten und für jeden Spieler den Gurt wählen (nur einmal). **Tauschen** vertauscht sie.
3. Optional **Alter, Gewicht und Geschlecht** der Person: für die Kalorien (Keytel-Formel, typischer Fehler ±20 %) und, mit dem Alter, für die Herzfrequenzzonen (geschätztes Maximum 208 − 0,7 × Alter).

- Unter jedem Gurt: Status, Herzfrequenz, **Signal** in dBm (näher an 0 = stärker) und **Aussetzer**, also wie oft die Herzfrequenz länger als 3 Sekunden ausblieb, mit den verlorenen Sekunden (*2 Aussetzer (14 s)*). Sie zählen ab dem „Spiel“ des Matches, vorher ab der Wahl des Gurts. Sie zeigen, wie weit der Gurt reicht.
- Im Match zeigt eine Zeile die Live-Herzfrequenz (*S1 · 142 bpm*). In der Zusammenfassung (7): durchschnittliche und maximale Herzfrequenz, Kalorien, Verlauf und Zeit in den Herzfrequenzzonen. Pausen zählen nicht.
- **Reichweite**: Test drinnen mit dem H6, Telefon ruhend: verbunden bis 21 m, aber bei −100 dBm, an der Grenze; mit dem Rücken zum Telefon sinkt das Signal um 5-10 dBm. Im Einzel ist der ferne Spieler 24-30 m vom Telefon entfernt: Damit keine Daten verloren gehen, nutze das **zweite Telefon** (6.6).

### 6.6 Zweites Telefon hinter der anderen Grundlinie (App 2.5)

Gurte und Armbänder nehmen **jeweils nur ein Telefon** an und reichen ein paar Meter weniger als die Platzlänge. Mit einem zweiten Telefon hinter der anderen Grundlinie hat jeder Spieler immer eines in der Nähe: Das zweite (**Satellit**) empfängt Gurte und Armbänder der Spieler auf seiner Seite und überbrückt zum **Haupttelefon**, das das Match führt.

1. **Haupttelefon**: Seite 2 › **Zweites Telefon** › **Zweites Telefon verwenden**. Beim ersten Mal fragt es nach der Berechtigung *Geräte in der Nähe* (bis Android 12 nach dem Standort). Das Telefon erstellt ein **eigenes, nur lokales Netz**: keine SIM, keine Daten, kein Vereins-WLAN nötig. Ein QR-Code erscheint.
2. **Satellit**: Seite 2 › **Zweites Telefon** › **Als zweites Telefon dienen** › QR-Code scannen. Android fragt einmal, ob es sich mit dem Netz des Haupttelefons (*AndroidShare_…*) verbinden soll: **Verbinden** tippen. Der Satellit behält seine mobilen Daten. Er muss *Mit dem Haupttelefon verbunden* anzeigen.
3. Der Satellit folgt selbst den auf dem Haupttelefon gewählten Gurten und Armbändern, auf denselben Seiten: nichts zu wählen. Lass ihn mit geöffneter App hinter der Grundlinie liegen.

- **Seitenwechsel**: nichts zu tun. Jeder Gurt und jedes Armband bleibt beim verbundenen Telefon, solange das Signal hält, dann übernimmt das nähere (ein paar Sekunden). Das Haupttelefon zeigt *Über zweites Telefon*; während der Übergabe kann ein Armband kurz als getrennt erscheinen.
- **Keine Daten verloren**: Herzfrequenz, Tasten und Schläge des Satelliten tragen die **Match-ID** und eine laufende Nummer, und der Satellit behält sie, bis das Haupttelefon sie bestätigt. Fällt das WLAN zwischen den Telefonen aus, kommt die Herzfrequenz später an und füllt die Lücken im Verlauf; Schläge zählen, wenn sie zum selben Match gehören. Eine **Taste** des Armbands, die mehr als 5 Sekunden später ankommt, gibt keinen Punkt: noch einmal drücken. Dieselbe Taste auf zwei Wegen zählt nur einmal.
- Vom Haupttelefon zu den Armbändern gehen Spielstand, Meldungen, *Identifizieren*, Ausschalten und Einstellungen. Einstellungen ändert man besser mit dem Armband am Haupttelefon.
- **Anzeigetafel**: Auf dem Satelliten öffnet **Anzeigetafel zeigen** die Anzeigetafel des Matches im Vollbild (wie in 8.3), bereits mit dem Haupttelefon verbunden.
- Kann das Haupttelefon das Netz nicht erstellen (normaler Hotspot schon an), nutzt es das WLAN, in dem es ist: Dann müssen beide Telefone im selben Netz sein. Mit Android 9 oder älter zeigt der Satellit Netzname und Passwort, die man in den WLAN-Einstellungen von Hand wählt.
- **Beenden** schließt Verbindung und Netz; der Satellit kehrt zu seinen eigenen Gurten und Armbändern zurück.

## 7. So benutzt du die App

1. **Neues Match** (optional): **Tennis** oder **Padel** (App 2.5: gleiche Regeln, Zeiten und Ansagen; mit Padel werden Doppel und Golden Point von selbst eingestellt, beides änderbar (statt Golden Point gibt es auf Seite 2 auch den *Star Point* der FIP: höchstens zwei Vorteile, dann ein entscheidender Punkt), und die Zusammenfassung nennt *Padel*), Verein, Platz, Einzel/Doppel, Namen (im Doppel zwei Namen pro Team). **Adresse des Platzes suchen** (standardmäßig aus) ergänzt den Spielbericht um die Adresse neben den Koordinaten: Sie kommt vom Geocoding-Dienst des Telefons, der mit Google-Diensten die Koordinaten an Google sendet. **Weiter**. Mit *Padel* erscheint auch **Americano- oder Mexicano-Turnier**: 4 bis 16 Spieler und 1 bis 4 Plätze, Spiele nach Punkten (16, 24 oder 32: die Punkte beider Paare ergeben zusammen die Summe) oder nach Zeit. Beim *Americano* wechseln die Paare jede Runde, damit jeder mit und gegen möglichst viele spielt; beim *Mexicano* wird ab der zweiten Runde nach Tabelle gespielt (1. und 4. gegen 2. und 3. auf demselben Platz). In jeder Runde trägt man das Ergebnis jedes Platzes ein (nach Punkten reicht eine Zahl, die andere ergänzt sich) oder zählt es mit *Punkt für Punkt zählen*, mit Ansage und, nach Zeit, Stoppuhr; bei mehr Spielern als Plätzen wird abwechselnd pausiert. **Nächste Runde** startet, wenn alle Ergebnisse da sind; die **Tabelle** zählt die Punkte jedes Spielers, dann Siege und Punktedifferenz, und lässt sich als Text teilen.
2. **Modus und Regeln**:
   - *Schiedsrichter*, *Armbänder* oder *Fernbedienungen* (6.3). Mit den Armbändern sind eingeschaltetes Bluetooth, Standortberechtigung und aktivierter Standort Pflicht: Die Zeilen unter **Voraussetzungen** aktualisieren sich live (auch wenn du Bluetooth oder Standort in den Schnelleinstellungen ausschaltest) und werden rot, mit einer Schaltfläche zum Beheben.
   - Die **Suche läuft automatisch und ohne Unterbrechung**, solange die Seite offen ist: Schalte die Armbänder ein, und sie belegen von selbst die freien Plätze (erst Spieler 1, dann Spieler 2). **Erkennen** lässt das jeweilige Armband in der Farbe des Spielers (gelb oder rot) blinken und piepen, so siehst du sofort, welches du in der Hand hast; **S1 ↔ S2 tauschen** vertauscht sie, ohne die Verbindung zu trennen. Im Auswahlmenü kannst du jederzeit ein anderes wählen oder *Keins* (ein von Hand entferntes Armband setzt die Suche nicht wieder ein). Ein Armband kann nicht zwei Spielern zugeordnet sein.
   - **Armbänder am Matchende und beim Beenden ausschalten** (standardmäßig an): Beim Bestätigen von *Match beendet* und mit *Beenden* schalten sich die Armbänder aus, statt auf die Inaktivität zu warten.
   - Im Schiedsrichtermodus erscheint beim Tippen auf **Weiter** ohne Standort die Aufforderung, ihn einzuschalten (sonst fehlt der Ort in der Zusammenfassung).
   - Sprache (sechs Sprachen, 4.1), Stimme an/aus, Format (*2 Gewinnsätze*, *3 Gewinnsätze*, *2 Sätze + Match-Tie-Break bis 10*, *Pro-Satz* bis 8 oder 10 Spiele, *nur Match-Tie-Break bis 10*), Spiele pro Satz (6, oder 4 bei Kurzsätzen mit Tie-Break bei 4 beide), Tie-Break in den Sätzen an/aus (aus: der Satz läuft, bis jemand 2 Spiele vorne liegt), No-Ad.
   - **Wahl (Münzwurf)**: Die Münze dreht sich und zeigt, wer gewinnt; du stellst ein, wer aufschlägt, und die Platzseiten **vom Schiedsrichterstuhl aus gesehen** (Platzskizze mit **Seiten tauschen**). Im Doppel wählst du außerdem, wer in jedem Team zuerst aufschlägt.
3. **MATCHBEGINN** blinkt: Drücke die Schaltfläche, KEY1 an einem Armband oder eine Fernbedienung. Die Stimme sagt *„erster Satz“ · „Aufschlag [Name]“ · „spielen“* mit 1 Sekunde zwischen den Sätzen; die **Match Time** startet bei „spielen“.
4. **Match**: oben links die Matchdauer, rechts der Countdown (**Shot Clock** 25 s, **Changeover Time**, **Set Break Time**; rot in den letzten 5 s). Der orange Kasten leuchtet 5 s lang auf bei *Seitenwechsel, Tie-Break, Satzball, Matchball, Breakball …*. Die beiden quadratischen Tasten (gelb = Spieler 1, rot = Spieler 2) liegen auf der Seite, auf der die Spieler tatsächlich stehen, und tauschen bei jedem Seitenwechsel die Plätze; unter dem Aufschläger erscheint **On Serve**. Darunter: *Punkt zurück*, *Unterbrechen/Fortsetzen*, Audio (Lautsprechersymbol, durchgestrichen = aus), *Neues Match*, **Beenden**. Im Armbandmodus stehen unter den Zeiten **S1**/**S2** mit Akku und Restlaufzeit: Ein Tippen darauf öffnet die Armband-Einstellungen. Ein Doppeltipp auf *Punkt zurück* nimmt nur einen Punkt zurück (ein zweiter Tipp innerhalb 1 s zählt nicht); direkt nach einem Seitenwechsel wird der zweite Tipp eines Doppeltipps ignoriert, damit er nicht auf einer Schaltfläche der neuen Seite landet. Das **Schloss** unten sperrt den Bildschirm (etwa mit dem Telefon in der Tasche oder auf der Bank): Berührungen bewirken nichts, Armbänder und Fernbedienungen schon; Entsperren durch langes Drücken auf das Schloss.
5. Beim letzten Punkt erscheint das Popup **Match beendet / Letzten Punkt zurücknehmen**. „Match beendet“ wird **nur am Telefon** bestätigt. Eine halbe Sekunde nach dem Erscheinen ignorieren seine Schaltflächen Tipps: Ein Doppeltipp auf den letzten Punkt nimmt nichts zurück.
6. **Zusammenfassung**: Sieger, Namen, Ergebnis Satz für Satz mit den Tie-Break-Punkten, Dauer, Beginn und Ende, Datum, Verein, Platz, Ort, Format, gewonnene Punkte und Spiele. Schaltflächen **Dateien speichern** (Dateiname + frei wählbarer Ordner + Formate .txt/.json/.png und **vollständiges PDF** mit Ergebnis, Statistik des Matches und jedes Satzes, Verlauf und Fotos). **Teilen** fragt, ob das Bild mit Text (für WhatsApp und soziale Netze) oder das vollständige PDF gesendet wird. In der Übersicht hält **Fotos vom Match** bis zu 10 Erinnerungsfotos (aus der Galerie oder direkt aufgenommen): Sie bleiben auf dem Telefon, kommen ins PDF und mit der Übertragung auf das andere Telefon, nie ins Bild für soziale Netze, **Teilen** (Bild 1080×1350 + Text für WhatsApp/Instagram/…), **Neues Match**, **Beenden**. Unter den Fotos nimmt **Notizen** freien Text auf (bis zu 2000 Zeichen): Er speichert sich von selbst, kommt ins PDF, in den geteilten Text und in die .txt- und .json-Dateien und mit der Übertragung auf das andere Telefon; ins Bild für soziale Netze kommt er nicht. Ein beendetes Match kommt von selbst in den **Verlauf** (Taste unter dem Titel auf Seite 1): die Liste der Matches, mit ihrer Zusammenfassung wieder zu öffnen oder zu löschen, und der Reiter **Spieler** mit Siegen, Niederlagen und direktem Vergleich aller, deren Name auf Seite 1 steht (Groß-/Kleinschreibung und Leerzeichen zählen nicht; im Doppel zählt jeder Spieler). **Importieren** fügt Matches hinzu, die auf einem anderen Telefon mit *Dateien speichern* als .json gespeichert wurden: beendete kommen in den Verlauf, unbeendete zu den unterbrochenen Matches zum Fortsetzen. **Übertragen** (oben rechts im Verlauf) bringt Verlauf und unterbrochene Matches per QR-Code auf ein anderes Telefon: beide Telefone müssen im selben WLAN sein oder eines im Hotspot des anderen; auf dem Telefon mit den Daten tippst du *Senden* und lässt das Fenster mit dem Code offen, auf dem anderen *Empfangen* und scannst den Code (Kameraberechtigung nötig). So lässt sich auch ein laufendes Match auf einem anderen Telefon fortsetzen: unterbrechen, übertragen, fortsetzen. Die Matches kommen zu den vorhandenen hinzu. **Spielerfotos**: Tippe auf den Kreis neben dem Namen auf Seite 1 (oder im Spielerdetail im Verlauf) und wähle ein Foto; es wird quadratisch zugeschnitten und über den Namen wiedergefunden. Es erscheint im Verlauf, in der Zusammenfassung und auf der TV-Anzeigetafel, bleibt nur auf diesem Telefon (es kommt bei der Übertragung mit) und landet nicht im geteilten Bild. Erneutes Tippen auf den Kreis ändert oder entfernt es. Schließt sich die App, während du auf der Zusammenfassung bist (Android schließt sie im Hintergrund, ein Update, ein Neustart des Telefons), findest du sie beim erneuten Öffnen wieder; nicht, wenn du sie selbst mit *Neues Match*, *Beenden* oder durch Entfernen der App aus den letzten Apps verlassen hast. Der Standardordner ist **Documents/Tennis Score Manager** (ab Android 10; unter Android 8 und 9 der App-Ordner): Er lässt sich mit jeder Dateien-App öffnen und bleibt auch nach dem Deinstallieren der App. Wird zweimal unter demselben Namen gespeichert, heißt die zweite Datei *Name (1)*; im Bild werden lange Texte (Namen, Adresse) an die Breite angepasst.

   Die Zusammenfassung hat drei Reiter. **Übersicht**: die Daten oben und, mit Armbändern 2.4, Schläge, Handgelenk-Tempo und Schläge pro Punkt (6.4). **Statistiken**, für das Match oder einen Satz: die beiden Spieler im Vergleich mit Balken, die von der Mitte ausgehen (farbig bei dem, der vorne liegt), für gewonnene Aufschlag- und Returnpunkte und -spiele, abgewehrte und genutzte Breakbälle, gewonnene Punkte und Spiele gesamt, Punkte in Folge, letzte 10 Punkte, abgewehrte Matchbälle; mit Armbändern 2.4 auch Schläge nach Art und durchschnittliches Handgelenk-Tempo. **Punkt für Punkt**: Satz für Satz jedes Spiel mit dem Ball beim Aufschläger, *AUFSCHLAG VERLOREN* bei Breaks, der Spielstand nach jedem Punkt mit **BB** (Breakball), **SB** (Satzball) und **MB** (Matchball) und dem Ausgang (*SPIEL*, *BREAK*, *SATZ*, *MATCH*). Asse, Doppelfehler, erste Aufschläge, Winner und Fehler gibt es, wenn du auf Seite 2 die **Detaillierte Statistik** einschaltest: statt der zwei großen Tasten erscheinen *Ass* und *Aufschlagfehler* (beim zweiten Mal *Doppelfehler*) und für jeden Spieler *Winner*, *Fehler* (der Punkt geht an die andere Seite) und *Punkt*; *Punkt zurück* nimmt auch einen Aufschlagfehler zurück. Gedacht für jemanden, der vom Spielfeldrand zählt. Nach *Fehler* erscheint 6 Sekunden lang *Unerzwungen / Erzwungen*; mit Armbändern oder Fernbedienungen erscheint nach jedem Punkt *Wie endete der Punkt?* (Ass, Winner, Fehler): Antworten ist freiwillig, der Spielstand ändert sich nicht. Im Verlauf zeigt das Spielerdetail den **Verlauf**: gewonnene Punkte Match für Match und die Durchschnitte von Assen, Doppelfehlern, ersten Aufschlägen, Winnern und Fehlern. Die Statistik jedes Matches zählt auch verwandelte Spiel-, Satz- und Matchbälle, Zu-null-Spiele, Spiele in Folge, nach Break-Rückstand gewonnene Sätze und in der Karte **Zeiten** die Dauer jedes Satzes und das längste Spiel.

   **Revanche** (unten in der Übersicht): schlägt denselben Wochentag und dieselbe Uhrzeit in der Woche darauf und den Verein des Matches vor; Datum, Uhrzeit und Ort lassen sich ändern, eine Notiz hinzufügen. **Einladung senden** öffnet das Teilen (WhatsApp, SMS, E-Mail…) mit der fertigen Nachricht: wer gegen wen, wann, wo und wie es letztes Mal ausging. **In den Kalender** legt den Termin in der Kalender-App an, **Match vorbereiten** holt Spieler, Verein, Platz und Regeln auf Seite 1 zurück (das Turnier nicht). Kein Konto: Die Einladung läuft über die Kanäle, die du schon nutzt.

**App verlassen**: **Beenden** (im Match mit Rückfrage, auch in der Zusammenfassung vorhanden) schließt die App wirklich; ebenso, wenn du sie aus den zuletzt verwendeten Apps entfernst. Beim nächsten Öffnen startet sie auf der ersten Seite.

**Speichern**: Das Match speichert sich bei jedem Punkt von selbst. *Unterbrechen* hält die Zeiten an; wenn du die App verlässt, das Telefon ausgeht oder die App geschlossen wird, findest du das Match unter **Unterbrochenes Match fortsetzen** (Seite 3) wieder und setzt es mit *Fortsetzen* fort. *Punkt zurück* berechnet alles von Anfang an neu und funktioniert daher auch nach dem Ende eines Spiels, eines Satzes oder des Matches. Der Papierkorb eines unterbrochenen Matches fragt nach. Wurde das Match bei gesperrtem Bildschirm über ein Armband gestartet (dann liefert Android keinen Standort), wird der Ort ermittelt, sobald du die App wieder öffnest.

### 7.1 Training mit dem Gurt (App 2.5)

Zum Trainieren mit dem Brustgurt ohne Match: Seite 1 › **Training**. Es wird **ein Gurt** verwendet, der von Spieler 1, mit Alter, Gewicht und Geschlecht für Kalorien und Zonen; man wählt ihn direkt dort (mit dem Schalter *Brustgurte*, Bluetooth und Berechtigungen, falls sie fehlen).

1. **Starten**: groß die Live-Herzfrequenz, darunter die Zeit und die laufende Runde.
2. **Runde** schließt einen Abschnitt des Trainings und beginnt den nächsten (wie die Rundentaste einer Stoppuhr): ein Sprint, eine Übung, eine Bahnrunde. **Pause / Weiter** hält die Zeit an; Pausen zählen nicht.
3. **Beenden**: Die App zeichnet noch **eine Minute Erholung** auf (ruhig bleiben), um zu messen, wie stark die Herzfrequenz sinkt; **Jetzt speichern** überspringt sie.

- **Zusammenfassung**: Dauer, durchschnittliche und maximale Herzfrequenz, Kalorien, **HRV (RMSSD)** aus den Schlagabständen, die der Gurt sendet (braucht etwa dreißig saubere Schläge; vergleiche sie mit deinen ähnlichen Trainings, nicht mit denen anderer), **Erholung in 1 Minute** (je stärker sie sinkt, desto besser), Verlauf, Herzfrequenzzonen und für jede Runde Dauer, Durchschnitt, Maximum und Erholung.
- **CSV** (Sekunde für Sekunde, mit Runde und Erholungsminute) und ein einseitiges **PDF** mit Verlauf, Zonen-Torte und Runden zum Teilen.
- Trainings bleiben auf demselben Bildschirm, getrennt von den Matches; man kann sie wieder öffnen oder löschen. Die Herzfrequenz kommt auch vom zweiten Telefon (6.6), und die Aufzeichnung läuft bei ausgeschaltetem Bildschirm weiter.

### 7.2 Fortschritte (App 2.5)

Seite 1 › **Fortschritte**: ein Dokument, um zu sehen, ob du besser wirst, **getrennt** für Trainings und Matches.

- **Trainings**: Dauer, durchschnittliche und maximale Herzfrequenz, Kalorien, HRV, Erholung.
- **Matches** eines Spielers aus dem Verlauf (aus der Liste gewählt): gewonnene Matches, gewonnene Punkte, Asse, Doppelfehler, erste Aufschläge im Feld, Winner, unerzwungene Fehler. Asse und die anderen detaillierten Werte gibt es nur bei Matches mit detaillierter Statistik; es sind Summen pro Match.
- Für jeden Punkt der Durchschnitt der **ersten Hälfte** des Zeitraums und der **zweiten**: grünes ▲, wenn es besser wird, rotes ▼, wenn es schlechter wird (weniger Doppelfehler oder Fehler ist eine Verbesserung), =, wenn es gleich bleibt (unter 5 % Unterschied) oder nicht beurteilt werden kann (Herzfrequenz). Es braucht mindestens 2 Trainings oder Matches.
- **Was verglichen wird**: Die **Filter** wählen den Zeitraum (alles, 30 Tage, 3 Monate, 1 Jahr), die **Wochentage** (zum Beispiel nur mittwochs, auch mehrere) und bei Matches **Turnier**, **Club** und **Gegner** aus dem Verlauf; darunter die Liste **Zum Vergleich** zum Abhaken (*Alle* / *Keine*). Vergleich, PDF und Druck verwenden nur die abgehakten.
- **TSM-Wertung**: Unter *Matches*, über den Filtern, die Wertung des Spielers (alle starten bei 1500) mit den gezählten Matches, der Änderung im letzten und dem Verlauf, dazu die **TSM-Rangliste** aller Spieler der Historie. Es ist ein Elo, auf dem Telefon berechnet: Sie steigt, wenn du gewinnst, stärker gegen besser bewertete Spieler und bei klarem Ergebnis; im Doppel zählt der Durchschnitt des Paares; Tennis und Padel haben getrennte Ranglisten. In den ersten 5 Matches ist sie *vorläufig* und bewegt sich stärker. In der Zusammenfassung jedes Matches zeigt die Karte **TSM-Wertung**, wie viel jeder gewonnen oder verloren hat; mit **Freundschaftsspiel** bleibt das Match in der Historie, zählt aber nicht.
- **PDF** (mit dem Verlauf jedes Punkts und der vollständigen Liste, bei Bedarf auf mehreren Seiten) zum Teilen, oder **Drucken** mit dem Android-Druckdienst (auch *Als PDF speichern*).

### 7.3 Videos: Kamera-Telefon, Highlights und Zeitlupe (App 2.5)

Ein zweites Telefon auf einem Stativ filmt das Match und blendet den Spielstand des Schiedsrichters ins Video ein.

1. **Schiedsrichter**: Seite 2 › **Zweites Telefon** › **Zweites Telefon verwenden** (QR, wie in 6.6).
2. **Kamera**: Seite 2 › **Zweites Telefon** › **Als Kamera dienen**, QR scannen, dann **Kamera öffnen** (Berechtigungen für Kamera und Mikrofon; das Telefon muss in 1080p aufnehmen). Bei *Spielstand verbunden* ist die Einblendung im Video (**Einblendung** schaltet sie ein oder aus); ohne Schiedsrichter nimmt es nur das Video auf.
   - **Aufnehmen / Stopp**: Das Video kommt in die Galerie (*Filme/Tennis Score Manager*) mit Kapiteln, eines pro Punkt.
   - **Live**: RTMP-Übertragung zu YouTube, Twitch, Facebook oder einem eigenen Server; Server und Stream-Schlüssel unter *Live: Server*.
   - **Ausrichten** (Bildausrichtung): Gib ein, wie viele Meter hinter der Grundlinie und wie hoch das Telefon steht und auf welchem Platz gespielt wird; die App nennt die Neigung nach unten, bei der beide Grundlinien und der entfernte Spieler im Video bleiben, und ob der Platz in der Breite passt. Oft begrenzt die Breite: vom Zaun aus (etwa 6 m) schneidet das Hauptobjektiv die nahen Ecken ab, das **Weitwinkel** nicht (**Objektiv … verwenden** oder die Taste **Objektiv**, bei gestoppter Aufnahme). Mit **Wasserwaage zeigen** erscheinen in der Vorschau der Horizont und Hinweise (*2° tiefer zielen*, *im Uhrzeigersinn drehen*…), grün wenn es passt, mit einer Vibration, wenn das Telefon richtig steht. Die Wasserwaage erscheint auch im Fenster **Kamera** auf dem Telefon des Schiedsrichters. Links und rechts kennen die Sensoren nicht: mit dem Auge prüfen, dass das Netz in der Mitte ist.
   - **Umriss** (Platzumriss): In der Vorschau erscheinen die Platzlinien so, wie das Objektiv sie sieht, und sie folgen dem Telefon beim Neigen. Die Schieberegler (**Abstand**, **Höhe**, **Richtung**, **Seitlicher Versatz** und bei Bedarf **Bildwinkel** und **Neigungskorrektur**) bewegen, bis sie auf den echten Linien liegen: So stimmt auch die Mitte, und die empfohlene Neigung wird genau. Die Einstellung wird für den Platz des Matches (*Verein · Platz* des Schiedsrichters) gespeichert und beim nächsten Mal übernommen. Umriss und Regler gibt es auch im Fenster **Kamera** des Schiedsrichters. Je höher das Telefon, desto besser sieht man die entfernte Platzhälfte: aus 2,5 m ist sie knapp über dem Netz zusammengedrückt.
3. **Kamera oben, vom Schiedsrichter gesteuert**: Auf dem Telefon des Schiedsrichters zeigt **Kamera** (neben *Zeitlupe* während des Matches, oder *Kamerabild und Steuerung* auf Seite 2 › **Zweites Telefon**, praktisch zum Ausrichten vor dem Start) das Bild des Kamera-Telefons wie im Video, mit Grafik, dazu **Aufnehmen / Stopp**, **Live**, **Einblendung** und **Zoom**. Außerdem Aufnahmedauer, Akku und freier Speicher (orange, wenn knapp). Das Bild ist klein und ruckelt (etwa 3 Bilder pro Sekunde) und wird nur gesendet, solange man es ansieht; der Stream-Schlüssel wird auf dem Kamera-Telefon eingetragen.

- **Videos** (Seite 1): die aufgenommenen Videos, Punkt für Punkt, Spiel für Spiel oder Satz für Satz anzusehen. **YouTube-Kapitel** bereitet die Kapitel zum Einfügen in die Beschreibung vor. **Video importieren** nimmt ein Video einer anderen Kamera (die Datei bleibt, wo sie ist): Die Punkte zählst du beim Ansehen, indem du am Ende jedes Punkts tippst, wer ihn gewonnen hat und, wenn du willst, wie er endete (*Letzten rückgängig* entfernt den letzten); Format und Regeln sind die von Seite 2.
- **Trainerfilter** (Filtersymbol): nur die Punkte eines Spielers, gewonnen oder verloren, bei eigenem Aufschlag oder Return, beim ersten oder zweiten Aufschlag, nach ihrem Ende, bei Breakbällen oder Satz- und Matchbällen. **Abspielen** reiht sie aneinander.
- **Highlights**: automatischer Zusammenschnitt der schönsten Punkte (Asse, Winner, Breakbälle, Satz- und Matchbälle, lange Ballwechsel mit den Armbändern) in der mit den Pfeilen gewählten Reihenfolge, für höchstens 1, 3, 5, 10 oder 15 Minuten oder eine **Andere Länge** bis 90. Passt nicht alles hinein, werden zuerst die Stücke gekürzt, dann bleiben die unwichtigeren Punkte draußen. Das Ergebnis kommt in die Galerie.
  - **Statistiken am Videoende**: 2 oder 3 Bildschirme zu je 10 Sekunden wie in Turnierübertragungen: Aufschlag und Breakbälle; Punkte, Fehler sowie kurze und lange Ballwechsel; Puls, falls ein Gurt getragen wurde. Das Match ist das im Verlauf, das während der Aufnahme gespielt wurde, oder das der Punkte eines importierten Videos. An den Seiten das Foto des Spielers; ohne Foto ein stilisiertes Porträt von Mann oder Frau (Wahl **Mann / Frau** auf Seite 1) (ohne Wahl der Mann; gilt in der ganzen App, auch auf Seite 1, im Verlauf, im Spielbericht, im PDF und auf der Anzeigetafel); darunter der Anteil gewonnener Punkte. Oben Turnier, Kategorie und Runde (Karte **Turnier** auf Seite 1) oder Club und Platz.
  - **Musik** unter den Statistiken, mit Ein- und Ausblendung: *Nowhere Land* von Kevin MacLeod (incompetech.com, Lizenz CC BY 4.0: Wenn du das Video veröffentlichst, nenne ihn), oder ein eigener Titel mit **Titel wählen** (es wird der Anfang verwendet; nur Musik verwenden, die du in deinen Videos nutzen darfst).
  - Unten auf jeder Statistikseite steht **Erstellt mit Tennis Score Manager** mit der Adresse der Website.
- **Zeitlupe**: Auf dem Telefon des Schiedsrichters fragt **Zeitlupe** bei verbundener Kamera den strittigen Punkt ab (*Letzter Punkt*, *Punkt davor* oder *Seit dem letzten gezählten Punkt*) und zeigt ihn, mit Pause und Bild für Bild. **Auch auf der Anzeigetafel** zeigt ihn zusätzlich auf der TV-Anzeigetafel.

### 7.4 Schläger: virtueller Schläger (App 2.5)

Seite 1 › **Schläger** › **Meine Schläger**: jeder echte Schläger hat seine eigene Karte (zwei Schläger desselben Modells sind zwei Karten: Saiten, Gewicht und Stunden unterscheiden sich).

- **Karte**: Spitzname (A, B…), Marke, Modell, Gewicht, Balance, Kopfgröße und Besaitungsbild, von Hand eingegeben oder **aus dem Katalog** übernommen (Herstellerangaben, zu korrigieren, wenn du deinen gemessen hast). *Ausgemustert*: wird nicht mehr angeboten, bleibt aber im Vergleich.
- **Besaitungen**: Typ (Monofil, Multifil, Synthetik, Naturdarm, Hybrid), Marke und Modell (auch aus den gängigen Saiten), Stärke, Spannung der Längs- und Quersaiten, Datum und Notizen. *Neue Besaitung* startet von der aktuellen. Für jede zählt die App die **Spielstunden** der zugeordneten Matches und Trainings und warnt beim Überschreiten der gewählten **Prüfgrenze** (standardmäßig 20 Stunden): aus der Zeit allein kennt sie die verbleibende Spannung nicht.
- **Vor dem Spiel**: auf Seite 1 unter der Rangliste jedes Spielers seinen Schläger wählen; im Training über dem Brustgurt. Match (Punkte, Schläge der Armbänder, Puls) und Training werden mit einer **Kopie** des Schlägers gespeichert, wie er an dem Tag war, samt Saite und Spannung: späteres Neubesaiten ändert vergangene Einheiten nicht.
- **In der Zusammenfassung** des Matches (Reiter *Übersicht*) und des Trainings sieht man den benutzten Schläger, kann ihn auch nachträglich zuordnen (es gilt die an dem Tag aufgezogene Besaitung) und das **Spielgefühl** von 1 bis 5 bewerten: Kontrolle, Komfort, Power und Ermüdung.
- **Vergleich**: *nach Schläger* oder *nach Besaitung* (gleicher Schläger mit anderen Spannungen oder Saiten), Konfigurationen A und B wählen. Balken nebeneinander für Einheiten, Stunden, gewonnene Matches, gewonnene Punkte, erste Aufschläge im Feld, Punkte nach erstem und zweitem Aufschlag, Asse, Doppelfehler, Winner und unerzwungene Fehler pro Match, mittlere Handgelenkgeschwindigkeit und Schläge der Armbänder, mittlerer Puls und mittleres Spielgefühl. Der bessere Wert ist fett, aber die App kürt keinen Sieger: Gegner, Belag, Bedingungen und Form zählen auch.

## 8. Anzeigetafel auf TV oder Monitor

Das Telefon des Schiedsrichters arbeitet als **kleiner Server** im WLAN: Die Anzeigetafel ist eine Webseite im LED-Stil (7-Segment-Ziffern, Gelb gegen Rot, Spiele und Sätze in der Mitte, beendete Sätze und Matchdauer unten links, **AUFSCHLAG: 25 SEK** unten rechts), die sich bei jedem Punkt von selbst aktualisiert. Der Monitor muss nicht „smart“ sein, und ein Vereinsnetz ist nicht nötig: Der **Hotspot** eines der beiden Telefone genügt.

### 8.1 Die möglichen Wege

| Wie es auf den Monitor kommt | Was du brauchst | Vorteile | Nachteile |
|---|---|---|---|
| **Zweites Telefon mit Videoausgang** + USB-C/HDMI-Kabel, TSM-App in *Als Anzeigetafel verwenden* | ein Telefon, das über USB-C Video ausgibt (DisplayPort Alt Mode) | kein Internet; die Anzeigetafel füllt den ganzen Monitor in 16:9, und das Telefon bleibt frei | viele Telefone geben **kein** Video aus: meist ja bei Galaxy S/Note/Tab S (mit DeX: *Bildschirmspiegelung* wählen oder den automatischen Start von DeX ausschalten), nein bei fast allen Galaxy A. Im Datenblatt nach „DisplayPort“ / „Videoausgang“ suchen |
| **Chromecast** (oder Google TV Streamer) am Monitor + ein beliebiges Telefon mit TSM in *Als Anzeigetafel verwenden* und **Streamen** (*Bildschirm übertragen* bis Android 14, Smart View bei Samsung) | Chromecast einmalig mit Google Home im Netz des Hotspots eingerichtet | jedes Telefon geht, kein langes Kabel | der Chromecast braucht **Internet** (mobile Daten am Hotspot); etwa 1 s Verzögerung; die Übertragung zeigt den Bildschirm des Telefons (im Querformat) |
| **Browser** auf einem beliebigen Gerät am Monitor (Laptop, Tablet, TV-Box, Fire TV Stick …) | QR-Code scannen oder Adresse eintippen | keine App zu installieren | der Bildschirm schaltet sich von selbst aus, wenn du das nicht einstellst; bei jedem Öffnen **VOLLBILD** antippen |

**Warum nicht über Bluetooth**: Das Telefon des Schiedsrichters bedient schon die beiden Armbänder per Bluetooth, wo es auf das Timing der Tasten ankommt; das WLAN ist davon getrennt, schneller und reicht weiter. **Warum nicht allein vom Telefon des Schiedsrichters zum Chromecast** (ohne zweites Telefon): Das ginge, braucht aber eine bei Google registrierte „Receiver“-App (Google Cast Developer Console, einmalig 5 $), die auf einer https-Website veröffentlicht ist; ein möglicher nächster Schritt.

**Netzwerktipps**:
- Die Anzeigetafel überträgt sehr wenig Daten (eine Nachricht bei jedem Punkt und alle 5 Sekunden) und verbraucht kein Internet-Datenvolumen.
- Wenn das Telefon des Schiedsrichters den Hotspot macht, wähle in den Hotspot-Einstellungen das **5-GHz**-Band, falls vorhanden: Das Bluetooth der Armbänder arbeitet bei 2,4 GHz, so stören sie sich nicht gegenseitig.
- Mit dem Chromecast sollte das Telefon des Schiedsrichters den Hotspot machen, mit **eingeschalteten mobilen Daten**; Anzeigetafel-Telefon und Chromecast verbinden sich mit diesem Hotspot.
- Ohne Chromecast geht es auch umgekehrt (Hotspot auf dem Anzeigetafel-Telefon, wie in der ursprünglichen Idee): Die App des Schiedsrichters bleibt mit dem WLAN verbunden, auch wenn es kein Internet hat.

### 8.2 Auf dem Telefon des Schiedsrichters

1. Seite 2 › **TV-Anzeigetafel** › **Anzeigetafel auf TV oder Monitor** einschalten. Ab Android 13 fragt die App nach der Berechtigung für Benachrichtigungen: Sie wird für die Benachrichtigung des Dienstes gebraucht, der die Anzeigetafel am Laufen hält.
2. Es erscheinen die **Adresse** (z. B. `192.168.43.1:8080`), der **QR-Code** und wie viele Anzeigetafeln verbunden sind. Steht dort *Kein Netz*, schalte den Hotspot ein oder verbinde dich mit dem des anderen Telefons.
3. **Vorschau auf diesem Telefon** öffnet die Anzeigetafel im Browser des Telefons selbst.
4. **Aussehen der Anzeigetafel**: Farbe jedes Spielers (8 Farben), Matchdauer, Aufschlaguhr und Pausen, beendete Sätze, Meldungen (Breakball, Satzball, Seitenwechsel …), Ball beim Aufschläger, ausgeschaltete Segmente sichtbar, Text unten (leer = Verein und Platz von Seite 1). Änderungen erscheinen sofort auf dem Monitor.
5. Im Match steht oben in der Mitte **TV · 1** (verbundene Anzeigetafeln): Ein Tippen darauf zeigt Adresse und QR-Code wieder an.

Bei eingeschalteter Anzeigetafel hält ein Vordergrunddienst (Benachrichtigung *Match läuft · TV-Anzeigetafel aktiv*) den Server auch bei ausgeschaltetem Bildschirm am Laufen, auch im Schiedsrichtermodus. Die Anzeigetafel ist **nur zum Anzeigen** da: Dort lässt sich nichts ändern. Er bleibt auch auf der Zusammenfassung und den anderen Seiten aktiv, solange die Anzeigetafel eingeschaltet ist (Benachrichtigung *TV-Anzeigetafel aktiv*); so bleibt das Endergebnis auch bei gesperrtem Telefon auf dem Monitor.

Was sie außer dem Spielstand zeigt: *WARTEN AUF DAS MATCH* vor Beginn, *BEREIT ZUM SPIELEN* auf der Seite MATCHBEGINN, **TIE-BREAK** / **MATCH-TIE-BREAK** anstelle von *VS*, blinkend *MATCH UNTERBROCHEN*, **SIEGER [Name]** am Matchende mit allen Sätzen; Vorteil wird als **AD** angezeigt, auch auf den LED-Ziffern. Ein im Match-Tie-Break gewonnenes Match endet mit 1:0 bei den Spielen und 2:1 bei den Sätzen, [10-8] steht bei den beendeten Sätzen; lange Meldungen werden verkleinert, damit sie auf den Bildschirm passen.

### 8.3 Auf dem Anzeigetafel-Telefon

1. Seite 1 › ganz unten **Als Anzeigetafel verwenden**.
2. Das Telefon **sucht selbst** nach dem Telefon des Schiedsrichters (Ankündigung im Netz und Scan des Hotspots, wenige Sekunden) und merkt sich die letzte Adresse. Findet es ihn nicht: Hotspot und *TV-Anzeigetafel* prüfen, dann **Erneut suchen**, oder die vom Schiedsrichter angezeigte Adresse eintippen und **Verbinden**. Es findet es auch, wenn auf diesem Telefon ebenfalls *Anzeigetafel auf TV oder Monitor* eingeschaltet ist; verbindet sich das WLAN erst später, sucht es von selbst erneut.
3. Die Anzeigetafel läuft im Vollbild, im Querformat, mit dauerhaft eingeschaltetem Bildschirm.
   - **Mit HDMI-Kabel**: Die Anzeigetafel erscheint im Format des Monitors auf dem Monitor; das Telefon zeigt *Die Anzeigetafel ist auf dem externen Monitor* bei minimaler Helligkeit (**Auch hier zeigen**, um sie auch auf dem Telefon zu sehen). Nach dem Abziehen des Kabels kehrt sie auf das Telefon zurück.
   - **Mit Chromecast**: Schnelleinstellungen öffnen › **Streamen** (oder **Bildschirm übertragen**) / **Smart View** › den Chromecast wählen.
4. Verschwindet das Telefon des Schiedsrichters (außer Reichweite, App geschlossen), erscheint *VERBINDUNG VERLOREN - NEUER VERSUCH...*, und nach 20 Sekunden sucht es von selbst erneut danach, auch wenn sich die Adresse geändert hat. Auch nach einer Unterbrechung des Hotspots verbindet es sich von selbst wieder, sobald das Netz zurück ist. Es wechselt nie zum Telefon eines anderen Platzes: Um den Platz zu wechseln, *Als Anzeigetafel verwenden* verlassen und erneut suchen.
5. Zum Beenden: **zweimal Zurück**.

**Aus einem Browser** (Laptop, TV-Box): QR-Code scannen oder Adresse eintippen, dann **VOLLBILD** antippen (erscheint, wenn du die Maus bewegst oder den Bildschirm berührst). Stell das automatische Ausschalten des Bildschirms auf *nie*: Auf einer http-Seite kann der Browser den Bildschirm nicht selbst eingeschaltet halten. Nach einer Unterbrechung verbindet sich die Seite von selbst wieder; zu viele offene Anzeigetafeln sperren eine neue nicht mehr aus (die älteste wird geschlossen).

**Vorschau ohne Telefone**: `TennisScoreManager/app/src/main/assets/scoreboard.html?demo=1` in einem Browser (auch `&lang=en`, `fr`, `de`, `es`, `pt` und `&state=ad`, `tb`, `end`, `idle`, `doubles`, `mtb`, `long`) zeigt die Anzeigetafel mit Testdaten.

## 9. Angewandte Regeln (ITF) und abgestimmte Entscheidungen

- **Spiel**: 0-15-30-40, Einstand, Vorteil, Spiel. **No-Ad**: bei 40:40 entscheidender Punkt („Einstand, entscheidender Punkt“).
- **Satz**: 6 Spiele mit 2 Spielen Abstand (7:5); bei **6:6 Tie-Break**.
- **Tie-Break**: bis 7 mit 2 Punkten Abstand; wer an der Reihe ist, schlägt den 1. Punkt auf, danach je 2 Punkte; Seitenwechsel **alle 6 Punkte** und am Ende; wer im Tie-Break zuerst aufgeschlagen hat, hat im ersten Spiel des folgenden Satzes **Rückschlag**.
- **Match-Tie-Break** (Format mit 2 Sätzen): bei 1:1 wird bis 10 Punkte mit 2 Punkten Abstand gespielt.
- **Seitenwechsel** (ITF-Regel 10): nach dem 1., 3., 5. … Spiel jedes Satzes. Am Satzende wird nur gewechselt, wenn der Satz eine ungerade Zahl von Spielen hatte (6:3, 7:6); sonst (6:4) wird nach dem ersten Spiel des folgenden Satzes gewechselt. Der Tie-Break zählt als ein Spiel.
- **Zeiten**: Shot Clock 25 s zwischen den Punkten; Changeover 90 s; Set Break 120 s am Satzende. Zusätzlich, wie gewünscht (keine ITF-Regel): **30 s** Wechselzeit nach dem 1. Spiel jedes Satzes, bei jedem Seitenwechsel im Tie-Break und bei 6:6; nach jeder Pause startet die Shot Clock.
- **Doppel**: Aufschlagfolge A1-B1-A2-B2 für den ganzen Satz, auch im Tie-Break; zu Beginn jedes Satzes fragt die App nach der Reihenfolge (sie darf laut Regelwerk geändert werden).
- **Ansagen**: Spielstand aus Sicht des Aufschlägers („fünfzehn null“, „null vierzig“, „fünfzehn beide“, „Einstand“, „Vorteil Rossi“); am Spielende „Spiel Rossi, Rossi führt drei zu zwei“ / „zwei beide“ + „Seitenwechsel“, wenn gewechselt wird; bei 6:6 „Spiel Rossi, sechs beide, Tie-Break“; im Tie-Break aus Sicht des Führenden („drei zu eins Rossi“, „sechs beide“); Satzende „Spiel Rossi, Rossi führt eins zu null in Sätzen“ / „ein Satz beide“; Matchende „Spiel, Satz und Sieg Rossi, sechs zu vier, drei zu sechs, sieben zu fünf“; „Korrektur“ + Spielstand, wenn ein Punkt zurückgenommen wird.

**Entscheidungen gegenüber dem ursprünglichen Wunsch, um der ITF zu folgen:**
1. **Bei 6:6 wird nicht gewechselt** (12 Spiele, also eine gerade Zahl): Die App macht die 30-s-Pause und sagt „Tie-Break“, aber nicht „Seitenwechsel“, und tauscht die Tasten nicht. Der erste Wechsel kommt nach 6 Punkten des Tie-Breaks.
2. Der Wechsel am Satzende hängt von der Zahl der Spiele im Satz ab (siehe oben), er findet nicht immer statt.
3. Am Ende eines ohne Tie-Break gewonnenen Satzes verwendet die App dieselbe Formel wie nach einem Tie-Break („Spiel Rossi, Rossi führt eins zu null in Sätzen“). Viele Schiedsrichter fassen stattdessen Satzgewinn und Satzergebnis in einer Ansage zusammen (in Italien etwa „gioco e set Rossi, sei quattro“): Das lässt sich in `Calls.kt` leicht ändern.
4. Bei 1:1 im Format mit Match-Tie-Break fügt die Stimme „Match-Tie-Break“ hinzu.

## 10. Wo du im Code ansetzt

| Was | Datei |
|---|---|
| Zählregeln | `model/ScoreEngine.kt` (+ Tests in `app/src/test`) |
| Sätze und Ansagen der Stimme | `voice/Calls.kt` (Aufbau), `voice/CallWords.kt` (Wörter und Reihenfolge jeder Sprache) |
| Aussprachekorrekturen | `voice/Pronunciation.kt` |
| Zeiten (25/90/120/30 s), Meldungen, Matchablauf | `MatchController.kt` |
| Texte der App | `ui/Strings.kt` (Italienisch, Englisch), `ui/StringsFr.kt`, `StringsDe.kt`, `StringsEs.kt`, `StringsPt.kt` |
| Eine Sprache hinzufügen | Eintrag in `model/Rules.kt` (`Lang`), eine `ui/StringsXx.kt`, ein `XxWords` in `voice/CallWords.kt`, eine Zeile in `TXT[]` von `TSM_Band.ino`: Die Tests in `LanguagesTest` und `StringsTest` zeigen, was fehlt |
| Bluetooth-Protokoll (UUIDs, Nachrichten, Einstellungen) | `ble/BandProtocol.kt` und am Anfang von `TSM_Band.ino` |
| Laufzeitschätzung | `ble/BatteryModel.kt` |
| Einstellungsbereich des Armbands | `ui/BandSettingsPanel.kt` |
| Grafik | `ui/screens/*.kt`, Farben in `ui/Theme.kt` |
| TV-Anzeigetafel: Seite und Aussehen | `app/src/main/assets/scoreboard.html` |
| TV-Anzeigetafel: gesendete Daten, Server, Einstellungen | `tv/TvModels.kt`, `tv/TvServer.kt`, `ui/TvSection.kt` |
| Telefon als Anzeigetafel (Suche, externer Monitor) | `tv/DisplayActivity.kt`, `tv/ScoreboardFinder.kt` |
| Ladebildschirm des Armbands | `TSM_Band.ino` (`pollPower`, `drawCharge`) |
| Bluetooth-Fernbedienungen (Gesten, Zuordnung) | `remote/PressDetector.kt` (+ Tests), `remote/RemoteInput.kt` |
| Statistiken und Punkt-für-Punkt-Verlauf der Zusammenfassung | `data/MatchStats.kt` (+ Tests), `ui/screens/MatchStatsPanels.kt` |
| Verlauf, Spieler und Import | `data/PlayerStats.kt` (+ Tests), `ui/screens/HistoryScreen.kt`, `MatchController.kt` (`importMatches`) |
| Übertragung zwischen Telefonen (QR) | `transfer/Transfer.kt` (server e `TransferBundle`), `MatchController.kt` (`startSending`, `receiveFrom`), `ui/screens/HistoryScreen.kt` (`SendDialog`) |
| Spielerfotos | `ui/PlayerPhoto.kt`, `data/Storage.kt` (`savePhoto`), `MatchController.kt` (`setPhoto`), `/photo/` in `tv/TvServer.kt` und `scoreboard.html` |
| Americano- und Mexicano-Turniere | `data/Tournament.kt` (+ Tests), `ui/screens/TournamentScreen.kt` |
| Schlagerkennung | `TSM_Band.ino` (Abschnitt „colpi“), `swing_detector.*` und `stroke_classifier.*` (aus SwingSense kopiert); App: `ble/BandProtocol.kt`, `ui/BandSettingsPanel.kt`, `data/Reports.kt` |
| Brustgurte (Bluetooth, Herzfrequenz, Kalorien, Zonen, Aussetzer) | `ble/HeartRateManager.kt`, `ble/HeartRateProtocol.kt`, `ble/StrapGaps.kt`, `data/HeartStats.kt` (+ Tests), `ui/HeartSection.kt` |
| Zweites Telefon (Protokoll, Netz, Brücke für Gurte und Armbänder) | `relay/RelayProtocol.kt`, `relay/RelayLink.kt` (+ Tests), `relay/Hotspot.kt`, `relay/PhoneRelay.kt`, `ui/RelaySection.kt`; in `ble/BleManager.kt` `setRemote`, `relayOut`, `inject*` |
| Training (Aufzeichnung, Runden, HRV, Erholung, CSV und PDF) | `workout/WorkoutSession.kt`, `data/Workout.kt` (+ Tests), `data/WorkoutReport.kt`, `ui/screens/WorkoutScreen.kt` |
| Fortschritte (Vergleich erste/zweite Hälfte, PDF, Drucken) | `data/Progress.kt` (+ Tests), `data/ProgressPdf.kt`, `ui/screens/ProgressScreen.kt` |


## 11. Häufige Probleme

- **Armband nicht gefunden**: Bluetooth und Standort an (grüne Zeilen)? Blinkt das Armband KOPPELN? (Ist es schon mit einem anderen Telefon verbunden, ist es nicht sichtbar.) Hat es sich inzwischen von selbst ausgeschaltet (30 s), schalte es mit einem Klick auf die Seitentaste wieder ein.
- **Das Armband zeigt NICHT GESENDET**: Das Telefon hat den Tastendruck nicht innerhalb von 8 Sekunden bestätigt (Verbindung verloren oder App geschlossen). Drücke erneut, sobald das Armband wieder verbunden ist. Seltener Fall: War der Tastendruck angekommen und ging nur die Bestätigung verloren, zählt der Punkt doppelt; die Stimme sagt den Spielstand an, korrigiere mit *Punkt zurück*.
- **Im Einstellungsbereich steht „Die Firmware dieses Armbands hat keine Einstellungen“**: Auf diesem Armband ist noch der alte Sketch, lade ihn neu hoch (Kapitel 5).
- **Android Studio sieht das Telefon nicht**: USB-Debugging an und RSA-Fingerabdruck auf dem Telefon bestätigt (3)? Unter Windows braucht es manchmal den Treiber des Herstellers, unter Linux die udev-Regeln und eine neue Sitzung (1.1). Oder Debugging über WLAN (3).
- **Arduino IDE zeigt den Port des Armbands nicht**: USB-C-Datenkabel, nicht nur ein Ladekabel; unter Linux Gruppe `dialout` und neue Sitzung (1.1); dann der Download-Modus (5, Schritt 7).
- **Die Stimme spricht nicht oder spricht falsch**: Medienlautstärke, *Audio On*, Stimme der gewählten Sprache installiert (4); probiere eine andere Engine oder eine andere Stimme unter *Audio und Stimme*.
- **Bildschirm während des Matches aus**: Im Armbandmodus hält ein Vordergrunddienst (Benachrichtigung „Match läuft“) Bluetooth, Stimme und Uhren aktiv; im Schiedsrichter- und Fernbedienungsmodus bleibt der Bildschirm an (mit den Fernbedienungen muss er das: 6.3).
- **Die Fernbedienung tut nichts**: Ist sie dem Spieler zugeordnet (Seite 2, Zeile mit seinem Namen)? Die App muss auf dem Match geöffnet sein, bei eingeschaltetem Bildschirm (6.3). Steht in der Zeile *Nicht verbunden*, eine Taste drücken, um sie aufzuwecken, oder sie in den Bluetooth-Einstellungen neu koppeln.
- **Schläge werden verwechselt oder nicht erkannt**: Trage das Armband wie bei der Kalibrierung (gleiches Handgelenk, gleiche Richtung); sonst kalibriere neu und trage es dabei wie im Match (6.4).
- **Das Armband zeigt SPEICHER VOLL**: Ein Kalibrierungsbeispiel konnte nicht gespeichert werden. Lösche die Beispiele eines Schlags (Papierkorb) und mache sie neu.
- **Herausfinden, warum zwei Schläge verwechselt werden**: Schließe das Armband an den Computer an, öffne den seriellen Monitor (115200 Baud) und tippe `d`: Du bekommst die gespeicherten Beispiele und die letzten 40 Schläge, jeder mit seinen 11 Messwerten. Die letzten Schläge bleiben erhalten, bis sich das Armband ausschaltet oder neu startet.
- **Gradle-Sync scheitert wegen des JDK**: Gradle JDK = jbr-21 setzen (Schritt 2.6).
- **Das Anzeigetafel-Telefon findet den Schiedsrichter nicht**: Selbes Netz? (Eines der beiden macht den Hotspot, das andere ist damit verbunden.) *TV-Anzeigetafel* beim Schiedsrichter eingeschaltet? Probiere die Adresse von Hand. Manche Hotspots isolieren die verbundenen Geräte voneinander („Client-Isolierung“): Falls vorhanden, schalte sie aus.
- **Der Browser des Telefons öffnet die Adresse nicht**, wenn die mobilen Daten an sind: Android leitet den Verkehr über die mobilen Daten, weil der Hotspot kein Internet hat. Nutze die App mit *Als Anzeigetafel verwenden* (sie regelt das selbst) oder schalte die mobilen Daten auf diesem Telefon aus.
- **Monitor bleibt mit Kabel schwarz**: Dieses Telefon hat keinen Videoausgang über USB-C (8.1), oder Samsung DeX ist gestartet: *Bildschirmspiegelung* wählen.
