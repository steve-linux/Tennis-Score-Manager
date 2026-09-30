# Tennis Score Manager — step-by-step guide

[Italiano](GUIDA.md) · **English** · [Français](GUIDE.fr.md) · [Deutsch](GUIDE.de.md) · [Español](GUIDE.es.md) · [Português](GUIDE.pt.md)

Android app (Kotlin + Jetpack Compose) for keeping the score in tennis according to the ITF rules, with chair umpire voice calls **in six languages** (Italian, English, French, German, Spanish, Portuguese: chapter 4.1), two **M5StickS3** wristbands connected over Bluetooth LE and an **LED scoreboard on a TV or monitor** (chapter 8).

## 0. What's in the repository

| Path | What it's for |
|---|---|
| `TennisScoreManager/` | The Android Studio project. |
| `firmware/TSM_Band/TSM_Band.ino` | The wristband firmware. |
| `deliver/installa_tsm.sh` | An alternative to cloning: it creates the **whole** Android project and the sketch using only `cat << 'TSM_EOF'` blocks (the Gradle wrapper jar is in base64). |
| `deliver/GUIDE.en.md` | This guide (in English); the Italian original is `deliver/GUIDA.md`, the other translations are `deliver/GUIDE.fr.md`, `.de`, `.es`, `.pt`. |

Versions used and verified: app **2.3** · firmware **2.2.1** · Gradle 8.14.3 · Android Gradle Plugin 8.13.2 · Kotlin 2.2.21 · Compose BOM 2025.12.00 · compileSdk/targetSdk 36 · minSdk 26 (Android 8.0). Firmware: M5Unified ≥ 0.2.12, NimBLE-Arduino ≥ 2.1, ESP32 core 3.x.

---

## 1. Preparing the computer and downloading the project

**Windows 10/11**, **Ubuntu** (22.04 or newer) or **Fedora** will all do; on other Linux distributions follow the Ubuntu or Fedora steps with that distribution's package manager. In the commands, `~` is your home folder: on Linux `/home/<user>`, on Windows `C:\Users\<user>`.

### 1.1 Programs to install

| | Windows 10/11 (PowerShell) | Ubuntu | Fedora |
|---|---|---|---|
| **Git** (download and update the project) | `winget install --id Git.Git -e` | `sudo apt install git` | `sudo dnf install git` |
| **Android Studio** (the app) | `winget install --id Google.AndroidStudio -e` | `sudo snap install android-studio --classic` | `.tar.gz` archive from developer.android.com/studio, extracted for example to `~/android-studio` |
| **Arduino IDE 2** (the wristbands) | `winget install --id ArduinoSA.IDE.stable -e` | Flatpak `cc.arduino.IDE2` (see below) | `flatpak install flathub cc.arduino.IDE2` |
| **Phone connected by cable** | the manufacturer's USB driver, if needed (see below) | `sudo apt install android-sdk-platform-tools-common` | `sudo dnf install android-tools` |
| **Wristband serial port** | nothing to do | `sudo usermod -aG dialout $USER` | `sudo usermod -aG dialout $USER` |

- **Windows**: `winget` is already included in up-to-date Windows 10/11 and is run from *PowerShell* or *Terminal*. You can also download the normal installers from git-scm.com, developer.android.com/studio and arduino.cc/en/software.
- **Ubuntu, Arduino IDE**: if Flatpak isn't installed yet: `sudo apt install flatpak`, then `flatpak remote-add --if-not-exists flathub https://dl.flathub.org/repo/flathub.flatpakrepo` and `flatpak install flathub cc.arduino.IDE2`; log out and back in so that it appears in the menu. The AppImage from arduino.cc works too, but on recent Ubuntu releases it needs extra packages and options: Flatpak is simpler.
- **Linux, Android Studio from the archive**: start it with `bin/studio.sh` inside the extracted folder (e.g. `~/android-studio/bin/studio.sh`). On Ubuntu the snap is fine too; on Fedora there is also the Flatpak `com.google.AndroidStudio`, but the official archive causes fewer problems with a connected phone.
- **Linux, `dialout` group** (serial port) and phone packages (udev rules): once they are installed, **log out and back in** (or reboot), otherwise the port and the phone remain inaccessible.
- **Windows, phone driver**: many phones work straight away. If Android Studio doesn't see the phone, install the manufacturer's driver: *Samsung Android USB Driver* (from the Samsung Developer site) for Samsung phones, *Google USB Driver* (Android Studio › **Tools › SDK Manager › SDK Tools**) for Pixels. The wristband needs no driver: it shows up as port `COM3`, `COM4`…
- The first time it starts, Android Studio runs a setup wizard: choose **Standard** and let it download the Android SDK (you need internet, a few GB).

### 1.2 Downloading the project

The GitHub repository is **private**: you need a GitHub account that the owner has given access to.

**A. With git (recommended: you can then update with `git pull`)**

Linux (Ubuntu, Fedora), in the Terminal:

```bash
mkdir -p ~/AndroidStudioProjects
cd ~/AndroidStudioProjects
git clone https://github.com/steve-linux/Tennis-Score-Manager.git
```

Windows, in PowerShell:

```powershell
mkdir -Force $HOME\AndroidStudioProjects
cd $HOME\AndroidStudioProjects
git clone https://github.com/steve-linux/Tennis-Score-Manager.git
```

- **Signing in**: on Windows git opens the GitHub sign-in window by itself. On Linux GitHub doesn't accept your password in `git clone`: the easiest way is GitHub CLI (`sudo apt install gh` or `sudo dnf install gh`), then `gh auth login` and, in the `~/AndroidStudioProjects` folder, `gh repo clone steve-linux/Tennis-Score-Manager`.
- The repository ends up in `~/AndroidStudioProjects/Tennis-Score-Manager`. The project to open in Android Studio is its subfolder **`TennisScoreManager`**; the wristband sketch is in `firmware/TSM_Band/`.
- To update: `git pull` inside `Tennis-Score-Manager`.

**B. Without git**: on the project's GitHub page (signed in) choose **Code › Download ZIP**, then extract the ZIP, for example into `~/AndroidStudioProjects`. For a new version, download the ZIP again.

**C. With the script** `deliver/installa_tsm.sh`, which contains the whole project in a single text file (`cat << 'TSM_EOF'` blocks). Run it on Linux or, on Windows, in *Git Bash* (it comes with Git):

```bash
bash installa_tsm.sh
```

- The project goes to `~/AndroidStudioProjects/TennisScoreManager`, the sketch to `~/Arduino/TSM_Band/TSM_Band.ino`.
- If the project folder already exists, it is **moved** to `TennisScoreManager.backup-YYYYMMDD-hhmmss` (the old XML layouts and old classes would make the new build fail).
- To use other folders: `bash installa_tsm.sh /path/to/project /path/to/sketch`.

## 2. Opening and building in Android Studio

1. Start Android Studio: on Windows from the Start menu, on Ubuntu (snap) from the applications menu, from the archive with `bin/studio.sh` (1.1).
2. **File › Open** (or **Open** in the welcome window) → choose the `TennisScoreManager` folder (inside the clone or the ZIP, or the one created by the script) → **Trust Project**.
3. Wait for the Gradle sync (the first time it downloads Gradle, the Android plugin and the libraries: this is the only time you need internet).
4. If *"Failed to find target android-36"* appears, click the **Install missing platform** link (or **Tools › SDK Manager › SDK Platforms › Android 16 (API 36)**).
5. If Android Studio offers the **AGP Upgrade Assistant**, you can ignore it: these versions were built and tested as they are.
6. Gradle JDK: **Settings › Build, Execution, Deployment › Build Tools › Gradle › Gradle JDK** = *jbr-21* (the bundled one, which is the default).
7. **Build › Make Project**: it must end with *BUILD SUCCESSFUL*.
8. Optional: the tests (74: rules, voice, languages, battery and charging, wristband settings, TV scoreboard) are run by right-clicking `app/src/test` › **Run Tests**.

## 3. Installing the app on the phone

1. On the phone: **Settings › About phone** → tap *Build number* 7 times → turn on **Developer options › USB debugging**.
2. Plug in the cable, accept the RSA key fingerprint, select the phone at the top and press **▶ Run**.
   - If the computer doesn't see the phone: on Windows install the manufacturer's USB driver, on Linux the package with the udev rules (1.1); then plug the cable in again. Alternatively, with no cable or driver: **Device Manager › Pair devices using Wi-Fi** (wireless debugging, Android 11+, computer and phone on the same network).
3. Alternatively, **Build › Build App Bundle(s) / APK(s) › Build APK(s)**, copy the APK to the phone and install it (installing apps from unknown sources must be allowed).

## 4. The voice (works without internet)

- Each call is read **as a single sentence** by the phone's text-to-speech engine, using an installed voice: no internet and natural prosody. The players' names are part of the sentence.
- Page 2 › **Audio and voice**:
  - **Speech engine**: *Phone default* (on Samsung phones this is Samsung TTS) or an engine of your choice, for example *Speech Services by Google*. In our tests, which were done in Italian, Google sounded more natural.
  - **Voice**: *Automatic (best offline)* or a specific voice (with Google the voices are listed by code, as *Voice* followed by a few letters; those marked *online* need internet).
  - **Test voice**: reads a sequence of sample calls with the names you entered. While it is reading, the button becomes **Stop the test**: tap it again to stop (it also stops by itself when you leave the page). Text-to-speech can't be paused mid-sentence, so the button stops it; pressing it again starts over from the beginning.
- If the voice for the chosen language is missing: **Settings › General management › Language › Text-to-speech** (or *Text-to-speech output*) → download the voice for that language for the chosen engine (the app also shows an **Install voice** button).
- **Pronunciation**: some engines get tennis words wrong (in Italian, "primo set" read as "primo settembre", i.e. "first of September", and "tie-break" read as "time break"). The app corrects them by itself (`voice/Pronunciation.kt`); the corrections were checked by transcribing the real audio from Samsung and Google.
- **Custom recordings** (the most natural voice of all: yours or an umpire's): the folder `Android/data/com.tennis.scoremanager/files/voice/` contains `LEGGIMI.txt` (the "read me" file) listing the 84 keys and their phrases. Record the files with those names (`score_1_0.mp3` = "fifteen love"…), put them in a ZIP with one folder per language (`it/`, `en/`, `fr/`, `de/`, `es/`, `pt/`) and use **Import ZIP**. `LEGGIMI.txt` has one tab-separated column per language, so it also opens neatly as a spreadsheet. Recordings always take precedence over text-to-speech; the names are still read by text-to-speech.
- **Use pre-generated audio files** (optional): with **Generate files** the app creates the 84 files once with the chosen voice (even an *online* voice, if there is internet at that moment) and then plays them instead of running text-to-speech live. It sounds more "stitched together", but it is useful for taking an online voice offline.
- **External speaker**: just pair it with the phone over Bluetooth; the voice comes out on the media channel (adjust the media volume).

### 4.1 Languages (app 2.3, firmware 2.2)

Page 2 › **Language**: Italiano, English, Français, Deutsch, Español, Português (each language is written in that language, so you can find it even when the app is set to a language you can't read). The choice applies at once to the **screens, umpire's voice, TV scoreboard, summary/sharing and wristbands**. Dates follow the language (*mercredi 30 septembre 2026*, *Mittwoch, 30. September 2026*…).

The calls are not word-for-word translations: they follow each federation's official texts for chair umpires, namely the FFT *L'arbitrage en 255 questions* and the ITF in French, the DTB/BTV and Swiss Tennis materials, RFET *Deberes y procedimientos*, and FPT *Deveres e Procedimentos* (the only published Portuguese script). English follows the ITF.

| | Italiano | English | Français | Deutsch | Español | Português |
|---|---|---|---|---|---|---|
| Start | primo set · Rossi al servizio · gioco | first set · Rossi to serve · play | première manche · au service Rossi · jouez | erster Satz · Aufschlag Rossi · spielen | primer set · al servicio Rossi · jueguen | primeiro set · Rossi ao serviço · joguem |
| 15-0 · 15-15 | quindici zero · quindici pari | fifteen love · fifteen all | quinze-zéro · quinze A | fünfzehn null · fünfzehn beide | quince cero · quince iguales | quinze-zero · quinze iguais |
| 40-40 · advantage | parità · vantaggio Rossi | deuce · advantage Rossi | égalité · avantage Rossi | Einstand · Vorteil Rossi | iguales · ventaja Rossi | iguais · vantagem Rossi |
| End of game | gioco Rossi, Rossi conduce tre giochi a due | game Rossi, Rossi leads three games to two | jeu Rossi, Rossi mène trois jeux à deux | Spiel Rossi, Rossi führt drei zu zwei | juego Rossi, Rossi gana tres juegos a dos | jogo Rossi, Rossi vence por três jogos a dois |
| Games level | due giochi pari | two games all | deux jeux partout | zwei beide | dos juegos iguales | dois jogos iguais |
| Tie-break | tre a uno Rossi · sei pari | three one Rossi · six all | trois un Rossi · six partout | drei zu eins Rossi · sechs beide | tres uno Rossi · seis iguales | três a um Rossi · seis iguais |
| Set | un set a zero · un set pari | one set to love · one set all | une manche à zéro · une manche partout | eins zu null in Sätzen · ein Satz beide | un set a cero · un set iguales | um set a zero · sets iguais |
| End of match | gioco, set, partita Rossi | game, set and match Rossi | jeu, set et match Rossi | Spiel, Satz und Sieg Rossi | juego, set y partido Rossi | jogo, set e partida Rossi |
| Change of ends | cambio campo | change ends | changement de côté | Seitenwechsel | cambio de lado | troca de lado |

Particulars:
- In French and Spanish the name comes **after** "au service"/"al servicio", in German after "Aufschlag". French calls the set **manche** (except in "jeu, set et match") and the tie-break **jeu décisif**.
- **10-6 … 10-9** in the match tie-break: in French, Spanish and Portuguese the call is "dix **à** huit", "diez **a** ocho", "dez **a** oito", because "dix huit" / "diez ocho" / "dez oito" sound like *eighteen*.
- **Portuguese**: a single option, with a Brazilian interface and preferred voice (if the Brazilian voice is missing, the European Portuguese one is used) and the calls from the FPT script, using words that are valid in both countries ("jogo" rather than "game", "partida"). "Um set a um" became "sets iguais": in the singular, *set* is pronounced like *sete* (seven) and it sounded like 7-1.
- **Pronunciation** (`voice/Pronunciation.kt`): every phrase in every language was read by the Google voices and transcribed with whisper. Corrections added: in German "Tie-Break" is made to be read as "Taibreak", with a comma in front of it (otherwise you get "Teilbreg" or "bei Detailbreak"); in French the lone "à" of the pre-generated files is made to be read as "a" (otherwise the voice says "a accent grave"). **Samsung TTS has not been tried in the new languages yet.**
- **Wristbands** (firmware 2.2): the app sends the language by itself on every connection and whenever you change it; the wristband stores it and uses it even when it isn't connected (looking for the phone, charging, powering off), without accents because the font is ASCII (*EN CHARGE*, *LAEDT*, *CARGANDO*…). With firmware 2.1 or earlier, the messages sent by the app are translated, but the wristband's own messages stay in Italian.

## 5. Wristband firmware (Arduino IDE)

1. Install Arduino IDE 2 (1.1). On Linux you also need permission for the serial port (`dialout` group, 1.1), after logging out and back in.
2. **File › Preferences › Additional boards manager URLs**:
   `https://static-cdn.m5stack.com/resource/arduino/package_m5stack_index.json`
3. **Boards Manager** → search for **M5Stack** → install version **≥ 3.2.5**.
4. **Library Manager** → install **M5Unified** (accept "Install all" for M5GFX) and **NimBLE-Arduino** by *h2zero* (≥ 2.1).
5. **File › Open** → `firmware/TSM_Band/TSM_Band.ino` from the clone or the ZIP (or `~/Arduino/TSM_Band/TSM_Band.ino` if you used the script).
6. **Tools › Board › M5Stack › M5StickS3**, **Tools › Port** → the wristband's port: on Linux `/dev/ttyACM0`, on Windows `COM3`, `COM4`… (the one that appears when you plug in the cable).
   *(Without the M5Stack package, "ESP32S3 Dev Module" also works: USB CDC On Boot = Enabled, Flash Size = 8MB, Partition = 8M with spiffs.)*
7. **Upload (→)**. If the port doesn't appear or the upload fails: try another USB-C cable (some are charge-only), then press and hold the **side button** (download mode) and try again; in download mode the COM port number may change on Windows.
   **After the upload**, if the display stays black (the wristband has stayed in programming mode), press the side button **once**: it restarts with the new program.
8. At start-up the wristband shows its name, e.g. **TSM-3FA2** (it can be changed from the app, see 6.1). Repeat for the second wristband.

> **Firmware 2.2.1**: the countdown before switching off is in the app's language too (6). **Firmware 2.2**: wristband texts in the app's language (4.1). **Firmware 2.1**: the charging screen (6.2); the settings, *Identify* and powering off from the app need at least 2.0. Upload `TSM_Band.ino` to **both** wristbands; with old firmware the app says so in the settings panel and everything else keeps working.

## 6. Using the wristbands

| Button | Action |
|---|---|
| **KEY1** (front) short | point to the wearer (it also starts the match from the MATCH START page); a beep confirms |
| **KEY1** long (1 s) | shows the battery (and keeps the wristband on if it is about to switch off through inactivity) |
| **KEY2** short | undoes the last point (also from the end-of-match popup); two lower beeps |
| **KEY2** long (2 s) | switches the wristband off (with the cable plugged in it shows *STILL CHARGING*: it charges even when off) |
| Side button | one click switches it on; a double click switches it off (hardware function) |

- At power-on **PAIRING...** flashes (on for 0.35 s every 2 s to save power). As soon as it is open, the app connects by itself to the wristbands it knows; new ones are found on page 2.
- Connected: **PAIRED** for 3 seconds with two beeps, then **PAIRED WITH** + the player's name.
- At every point the display lights up with the game score in large digits (yours on the left, the opponent's on the right; the green ball shows who is serving), then switches off. At the end of a game it shows games and sets.

**Automatic power-off** (the times can be changed from the app, 6.1):

| Situation | What the wristband does | Default |
|---|---|---|
| On, but no phone connects | flashes PAIRING, then switches off | **30 s** |
| Phone lost (switched off, out of range, Bluetooth off, app closed abruptly) | flashes RECONNECTING and reconnects by itself as soon as it can; otherwise it switches off | 3 min |
| Connected but idle (no points, no messages) | 30 s beforehand it warns with **IDLE · HOLD KEY1** and a beep, then switches off | 30 min |
| Match concluded (confirmed on the phone) or **Exit** in the app | shows MATCH OVER / APP CLOSED and switches off immediately | on (can be turned off, 7.2) |
| Battery empty (below 3.30 V for two readings in a row) | shows BATTERY EMPTY and switches off, rather than staying on half-working | always |

- In the last 10 seconds before switching off for lack of a phone it shows **POWERING OFF · 8s - PRESS ANY KEY** with a beep: any button postpones the power-off and restarts the countdown.
- When it switches itself off it tells the phone: during a match the orange box shows *WRISTBAND 1 OFF (IDLE)*, *(BATTERY EMPTY)*…

**Battery life**: the biggest consumer is the ESP32-S3 board with Bluetooth connected (about 35 mA): the Arduino core is built without deep power saving (light sleep) while Bluetooth is on, so it can't go much lower. Display, brightness and beeps add a few mA. With the 250 mAh battery the estimate is **about 7 hours from a full charge**, more than any best-of-three-sets match. The settings panel shows the estimate in real time and, after 20 minutes of use, corrects it with the draw **measured** on that wristband. The full log is in the app's `files/battery_log.csv`.

The rest of the power saving: CPU at 80 MHz, display off when not needed, Bluetooth Low Energy (the radio wakes up 2-3 times a second, and a button press still gets through within ~120 ms), beeper powered only during beeps, microphone/IMU/5V off.

### 6.1 Wristband settings

From page 2 (**Settings** under each player's wristband) or during the match (tap **P1**/**P2** at the top, or the sliders icon). They are saved **in the wristband** and are kept even when it is switched off; after every change the wristband shows its name with *SETTINGS SAVED*.

| Setting | Values | Default |
|---|---|---|
| Name | up to 12 characters (e.g. the player's name or "P1") | TSM-xxxx |
| Display brightness | 5-100 % | 20 % |
| Score shown after each point | Off, 2, 3, 5, 8 s (the end-of-game summary lasts 2 s longer) | 3 s |
| Beeper volume | Mute-100 % | 50 % |
| Flip display | to wear it on the other wrist | off |
| Power-off: at power-on, if no phone connects | 15 s - 5 min | 30 s |
| Power-off: if the phone link is lost | 1-10 min | 3 min |
| Power-off: if connected but idle | 10-60 min | 30 min |

Below them is the **battery life estimate** (from a full charge and at the current charge) with the draw broken down by item: it changes as you move the sliders, even before you confirm. Then **Identify**, **Power off** and **Copy to the other wristband** (same settings; the other wristband keeps its own name).

### 6.2 Charging (firmware 2.1)

Plug in the USB-C cable: the wristband beeps and shows the **charging screen** for 30 seconds, then the display switches off and every 10 seconds lights up again for 1.5 s (a quick glance, like a charger's indicator light). **Any button** lights it up for another 30 seconds. If the wristband was off, switch it on with one click of the side button to see it (it charges anyway, even when off).

```
 TSM-3FA2   USB 5.01V            ← name and cable voltage
 ┌──────────┐
 │██████ ⚡  │▌   78%             ← battery icon and charge percentage
 └──────────┘
      CHARGING                   ← or FULLY CHARGED / USB POWERED
 4.12V  42 MIN IN  ~25 MIN LEFT  ← battery voltage, time on charge, estimated end
```

- **Percentage**: while charging, the measured voltage is higher than the real one (≈0.1 V); the wristband corrects for this, never lets the percentage go down and only reaches **100 %** when the charger says it has finished. When charging is complete the label becomes **FULLY CHARGED** with the time it took (*CHARGED IN 1H 25*) and the display stays off (no flashes at night).
- **Estimated end**: the power chip (PM1) doesn't measure the charging current, so the time left is worked out from how much the charge has risen in the last 10 minutes: it appears after 10 minutes, rounded to 5, and it is only an estimate.
- **USB POWERED**: the cable is there but the battery isn't charging (and isn't full): a poor cable or power supply, or a disconnected battery.
- **With the cable plugged in it never switches itself off** (no power-off for a missing phone or inactivity, no *battery empty*); you can switch it off with a long press of KEY2. When the cable is unplugged it shows **USB UNPLUGGED · BATTERY 97%** and from then on the normal power-off times apply again (6).
- **When connected to the phone** (e.g. a power bank during a match) the score takes priority: just a short *CHARGING 78%* message, and a long press of KEY1 shows *FULLY CHARGED* or *CHARGING*.
- **In the app**: page 2 and the wristband settings show *Charging 78%* or *Fully charged*; during a match the P1/P2 pills show the ⚡ symbol. The `files/battery_log.csv` log also has two extra columns (USB voltage, fully charged): useful for checking how long charging really takes.
- The wristband and the app now calculate the percentage with the **same curve** for the LiPo (the wristband used to use a less accurate straight line), so they show the same number.

> Still to be checked with real wristbands: charging could not be tested on the hardware. In particular, how often the charger reports *fully charged* and how accurate the percentage is while charging.

## 7. Using the app

1. **New match** (optional): club, court, singles/doubles, names (in doubles, two names per team). **Next**.
2. **Mode and rules**:
   - *Umpire* or *Wristbands*. With the wristbands, Bluetooth on, location permission and location on are required: the **Requirements** rows update in real time (even if you turn Bluetooth or location off from the Quick Settings panel) and turn red, with a button to fix them.
   - The **search is automatic and continuous** while the page is open: switch the wristbands on and they fill the free slots by themselves (Player 1 first, then Player 2). **Identify** makes that wristband flash in the player's colour (yellow or red) with beeps, so you can see at once which one you are holding; **Swap P1 ↔ P2** swaps them without disconnecting them. From the drop-down menu you can always choose another one or *None* (a wristband removed by hand is not put back by the search). A wristband can't be assigned to both players.
   - **Switch the wristbands off at match end and on exit** (on by default): when *Match concluded* is confirmed and with *Exit*, the wristbands switch off instead of waiting for the idle timeout.
   - In Umpire mode, if you press **Next** without location you are asked to turn it on (otherwise the place won't appear in the summary).
   - Language (six languages, 4.1), voice on/off, format (*3 sets · tie-break to 7* or *2 sets + match tie-break to 10*), No-Ad.
   - **Coin toss**: the coin spins and shows who wins; you set who serves and the court ends **as seen by the chair umpire** (court diagram with **Swap ends**). In doubles you also choose who serves first in each team.
3. **MATCH START** flashes: press the button or KEY1 on a wristband. The voice says *"First set" · "[name] to serve" · "play"* with 2 seconds between the phrases; the **Match Time** starts on "play".
4. **Match**: top left is the match time, top right the countdown (**Shot Clock** 25 s, **Changeover Time**, **Set Break Time**; red in the last 5 s). The orange box lights up for 5 s for *change ends, tie-break, set point, match point, break point…*. The two square buttons (yellow = Player 1, red = Player 2) are on the side where the players actually are and swap over at every change of ends; **On Serve** appears under the server. At the bottom: *Undo point*, *Suspend/Resume*, audio (speaker icon, crossed out = off), *New match*, **Exit**. In Wristbands mode, under the timers there are **P1**/**P2** with battery level and remaining battery life: tap them to open the wristband settings.
5. At the last point the **Match concluded / Undo last point** popup appears. "Match concluded" can be confirmed **only on the phone**.
6. **Summary**: winner, names, set-by-set score with tie-break points, duration, start and end time, date, club, court, place, format, points and games won. Buttons: **Save to history** (file name + folder of your choice + .txt/.json/.png formats), **Share** (1080×1350 image + text for WhatsApp/Instagram/…), **New match**, **Exit**.

**Exiting**: **Exit** (it asks for confirmation during a match, and it is also on the summary) really closes the app; so does swiping it away from the recent apps. When reopened, it starts again from the first page.

**Saving**: the match saves itself at every point. *Suspend* stops the timers; if you exit, the phone switches off or the app is closed, the match can be found under **Resume suspended match** (page 3) and restarts with *Resume*. *Undo point* recalculates everything from the beginning, so it works even after the end of a game, a set or the match.

## 8. Scoreboard on a TV or monitor

The umpire's phone acts as a **small server** on the Wi-Fi network: the scoreboard is an LED-style web page (7-segment digits, yellow against red, games and sets in the centre, finished sets and match time bottom left, **SERVE: 25 SEC** bottom right) that updates by itself at every point. The monitor doesn't need to be "smart" and no club network is needed: the **hotspot** of one of the two phones is enough.

### 8.1 The possible routes

| How it gets to the monitor | What you need | Pros | Cons |
|---|---|---|---|
| **Second phone with video output** + USB-C/HDMI cable, TSM app in *Use as scoreboard* | a phone with video output over USB-C (DisplayPort Alt Mode) | no internet; the scoreboard fills the whole monitor in 16:9 and the phone stays free | many phones do **not** output video: as a rule Galaxy S/Note/Tab S do (with DeX: choose *Screen mirroring* or turn off DeX's automatic start), almost all Galaxy A models don't. Look for "DisplayPort" / "video output" in the spec sheet |
| **Chromecast** (or Google TV Streamer) on the monitor + any phone with TSM in *Use as scoreboard* and **Screen Cast** (Smart View on Samsung) | a Chromecast set up once with Google Home on the hotspot's network | any phone will do, no long cable | the Chromecast needs **internet** (mobile data on the hotspot); about 1 s of delay; casting shows the phone's screen (in landscape) |
| **Browser** on any device connected to the monitor (laptop, tablet, TV box, Fire TV Stick…) | scan the QR code or type in the address | no app to install | the screen switches off by itself unless you set it not to; you have to tap **FULL SCREEN** every time you open it |

**Why not over Bluetooth**: the umpire's phone is already handling the two wristbands over Bluetooth, where button timing matters; Wi-Fi is separate, faster and has a longer range. **Why not straight from the umpire's phone to the Chromecast** (with no second phone): it can be done, but it needs a "receiver" app registered with Google (Google Cast Developer Console, a one-off $5) and published on an https site; it is a possible next step.

**Network tips**:
- The scoreboard sends very little data (one message at every point and every 5 seconds) and uses no internet data.
- If the umpire's phone provides the hotspot, choose the **5 GHz** band in the hotspot settings if available: the wristbands' Bluetooth works at 2.4 GHz, so they won't interfere with each other.
- With a Chromecast it is best for the umpire's phone to provide the hotspot, with **mobile data on**; the scoreboard phone and the Chromecast connect to that hotspot.
- Without a Chromecast the opposite also works (hotspot on the scoreboard phone, as in the original idea): the umpire's app stays attached to the Wi-Fi even if it has no internet.

### 8.2 On the umpire's phone

1. Page 2 › **TV scoreboard** › turn on **Scoreboard on a TV or monitor**.
2. The **address** (e.g. `192.168.43.1:8080`), the **QR code** and the number of connected scoreboards appear. If it says *No network*, turn on the hotspot or connect to the other phone's hotspot.
3. **Preview on this phone** opens the scoreboard in the phone's own browser.
4. **Scoreboard look**: each player's colour (8 colours), match time, serve clock and breaks, finished sets, messages (break point, set point, change ends…), ball next to the server, unlit segments visible, bottom line (empty = club and court from page 1). Changes reach the monitor straight away.
5. During the match, **TV · 1** (connected scoreboards) appears at the top centre: tap it to see the address and QR code again.

While the scoreboard is on, a foreground service (notification *Match in progress · TV scoreboard on*) keeps the server running even with the screen off, in Umpire mode too. The scoreboard is **read-only**: nothing can be changed from it.

What it shows besides the score: *WAITING FOR THE MATCH* before the start, *READY TO PLAY* on the MATCH START page, **TIE-BREAK** / **MATCH TIE-BREAK** in place of *VS*, a flashing *MATCH SUSPENDED*, **WINNER [name]** at the end of the match with all the sets; advantages read **AD** on the LED digits too.

### 8.3 On the scoreboard phone

1. Page 1 › at the bottom, **Use as scoreboard**.
2. The phone **finds the umpire's phone by itself** (network announcement and hotspot scan, a few seconds) and remembers the last address. If it can't find it: check the hotspot and *TV scoreboard*, then tap **Search again**, or type in the address shown on the umpire's phone and tap **Connect**.
3. The scoreboard goes full screen, in landscape, with the screen always on.
   - **With the HDMI cable**: the scoreboard goes to the monitor in the monitor's format; the phone shows *The scoreboard is on the external monitor* with brightness at minimum (**Show here too** to see it on the phone as well). Unplug the cable and it comes back to the phone.
   - **With the Chromecast**: open the Quick Settings panel › **Screen Cast** / **Smart View** › choose the Chromecast.
4. If the umpire's phone disappears (out of range, app closed), *CONNECTION LOST - RECONNECTING...* appears and after 20 seconds it searches again by itself, even if the address has changed.
5. To exit: press **back twice**.

**From a browser** (laptop, TV box): scan the QR code or type in the address, then tap **FULL SCREEN** (it appears when you move the mouse or touch the screen). Set the screen timeout to *Never*: on an http page the browser can't keep the screen on by itself.

**Preview without phones**: `TennisScoreManager/app/src/main/assets/scoreboard.html?demo=1` in a browser (also `&state=ad`, `tb`, `end`, `idle`, `doubles`) shows the scoreboard with test data (the demo texts are in Italian).

## 9. Rules applied (ITF) and agreed choices

- **Game**: 0-15-30-40, deuce, advantage, game. **No-Ad**: at 40-40, a deciding point ("deuce, deciding point").
- **Set**: 6 games with a 2-game margin (7-5); at **6-6, a tie-break**.
- **Tie-break**: to 7 with a 2-point margin; the player whose turn it is serves the 1st point, then 2 points each; change of ends **every 6 points** and at the end; whoever served first in the tie-break **receives** in the first game of the next set.
- **Match tie-break** (2-set format): at one set all, played to 10 points with a 2-point margin.
- **Change of ends** (ITF Rule 10): after the 1st, 3rd, 5th… game of each set. At the end of a set, ends are changed only if the set had an odd number of games (6-3, 7-6); otherwise (6-4) they are changed after the first game of the next set. The tie-break counts as one game.
- **Times**: shot clock 25 s between points; changeover 90 s; set break 120 s at the end of a set. In addition, as requested (not an ITF rule): **30 s** to change ends after the 1st game of each set, at every change of ends in the tie-break and at 6-6; when any break ends, the shot clock starts.
- **Doubles**: serving rotation A1-B1-A2-B2 for the whole set, tie-break included; at the start of each set the app asks for the order (it can be changed, as the rules allow).
- **Calls**: the score is called from the server's side ("fifteen love", "love forty", "fifteen all", "deuce", "advantage Rossi"); at the end of a game "game Rossi, Rossi leads three games to two" / "two games all" + "change ends" when ends are changed; at 6-6 "game Rossi, six games all, tie-break"; in the tie-break the score is called from the leader's side ("three one Rossi", "six all"); end of set "game Rossi, Rossi leads one set to love" / "one set all"; end of match "game, set and match Rossi, six four, three six, seven five"; "correction" + the score when a point is undone.

**Choices made compared with the original request, to follow the ITF:**
1. **No change of ends at 6-6** (that makes 12 games, an even number): the app takes the 30 s break and says "tie-break", but it doesn't say "change ends" and doesn't swap the buttons. The first change is after 6 points of the tie-break.
2. The change of ends at the end of a set depends on the number of games in the set (see above); it doesn't always happen.
3. At the end of a set won without a tie-break, the app uses the same formula as after a tie-break ("game Rossi, Rossi leads one set to love"). Many umpires say "game and first set Rossi, six four" instead: this is easy to change in `Calls.kt`.
4. At one set all in the match tie-break format, the voice adds "match tie-break".

## 10. Where to make changes

| What | File |
|---|---|
| Scoring rules | `model/ScoreEngine.kt` (+ tests in `app/src/test`) |
| Voice phrases and calls | `voice/Calls.kt` (how calls are built), `voice/CallWords.kt` (words and word order for each language) |
| Pronunciation fixes | `voice/Pronunciation.kt` |
| Times (25/90/120/30 s), messages, match flow | `MatchController.kt` |
| App texts | `ui/Strings.kt` (Italian, English), `ui/StringsFr.kt`, `StringsDe.kt`, `StringsEs.kt`, `StringsPt.kt` |
| Adding a language | an entry in `model/Rules.kt` (`Lang`), a `ui/StringsXx.kt`, an `XxWords` in `voice/CallWords.kt`, a row in `TXT[]` in `TSM_Band.ino`: the tests in `LanguagesTest` and `StringsTest` tell you what's missing |
| Bluetooth protocol (UUIDs, messages, settings) | `ble/BandProtocol.kt` and the top of `TSM_Band.ino` |
| Battery life estimate | `ble/BatteryModel.kt` |
| Wristband settings panel | `ui/BandSettingsPanel.kt` |
| Graphics | `ui/screens/*.kt`, colours in `ui/Theme.kt` |
| TV scoreboard: page and look | `app/src/main/assets/scoreboard.html` |
| TV scoreboard: data sent, server, settings | `tv/TvModels.kt`, `tv/TvServer.kt`, `ui/TvSection.kt` |
| Phone used as a scoreboard (search, external monitor) | `tv/DisplayActivity.kt`, `tv/ScoreboardFinder.kt` |
| Wristband charging screen | `TSM_Band.ino` (`pollPower`, `drawCharge`) |


## 11. Common problems

- **Wristband not found**: are Bluetooth and location on (green rows)? Is the wristband flashing PAIRING? (If it is already connected to another phone, it isn't visible.) If in the meantime it has switched itself off (30 s), switch it back on with one click of the side button.
- **The settings panel says "This wristband's firmware has no settings"**: that wristband still has the old sketch; upload the new one (chapter 5).
- **Android Studio doesn't see the phone**: is USB debugging on and has the RSA key fingerprint been accepted on the phone (3)? On Windows you sometimes need the manufacturer's driver, on Linux the udev rules and a new session (1.1). Or use wireless debugging (3).
- **Arduino IDE doesn't show the wristband's port**: use a USB-C data cable, not a charge-only one; on Linux, the `dialout` group and a new session (1.1); then download mode (5, step 7).
- **The voice doesn't speak or reads badly**: media volume, *Audio On*, voice for the chosen language installed (4); try another engine or another voice in *Audio and voice*.
- **Screen goes off during the match**: in Wristbands mode a foreground service (notification "Match in progress") keeps Bluetooth, voice and timers running; in Umpire mode the screen stays on.
- **Gradle sync fails because of the JDK**: set Gradle JDK = jbr-21 (step 2.6).
- **The scoreboard phone can't find the umpire's phone**: are they on the same network? (one of the two provides the hotspot, the other is connected to it). Is *TV scoreboard* on on the umpire's phone? Try typing the address by hand. Some hotspots isolate connected devices from each other ("client isolation"): if so, turn it off.
- **The phone's browser won't open the address** while mobile data is on: Android sends the traffic over mobile data because the hotspot has no internet. Use the app in *Use as scoreboard* (it handles this by itself) or turn off mobile data on that phone.
- **Black monitor with the cable**: that phone has no video output over USB-C (8.1), or Samsung DeX has started: choose *Screen mirroring*.
