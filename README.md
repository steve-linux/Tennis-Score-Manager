# Tennis Score Manager

**Guida / Guide / Anleitung / Guía / Guia:** [Italiano](deliver/GUIDA.md) · [English](deliver/GUIDE.en.md) · [Français](deliver/GUIDE.fr.md) · [Deutsch](deliver/GUIDE.de.md) · [Español](deliver/GUIDE.es.md) · [Português](deliver/GUIDE.pt.md)

App Android per tenere il punteggio del tennis secondo le regole ITF, con chiamate vocali da giudice di sedia (italiano, inglese, francese, tedesco, spagnolo e portoghese, anche senza internet), due braccialetti **M5StickS3** collegati in Bluetooth LE e un tabellone a LED su TV o monitor (secondo telefono col cavo HDMI, Chromecast o browser, sull'hotspot di un telefono).

| Cartella | Contenuto |
|---|---|
| `TennisScoreManager/` | Progetto Android Studio (Kotlin + Jetpack Compose) |
| `firmware/TSM_Band/` | Sketch Arduino per i braccialetti M5StickS3 |
| `deliver/GUIDA.md` | **Guida passo passo**: installazione su Windows, Ubuntu e Fedora, Android Studio, Arduino IDE, uso dell'app, regole applicate. Tradotta in `deliver/GUIDE.en.md`, `.fr`, `.de`, `.es`, `.pt` |
| `deliver/installa_tsm.sh` | Tutto il progetto come blocchi `cat` (alternativa al clone), rigenerabile con `make_installer.py` |

## Avvio rapido

Servono Git, Android Studio e Arduino IDE 2: come installarli su Windows, Ubuntu e Fedora è nella guida, capitolo 1. Il repository è privato, serve un account GitHub con accesso.

```bash
git clone https://github.com/steve-linux/Tennis-Score-Manager.git
```

- **App**: Android Studio › *File › Open* › la sottocartella `TennisScoreManager` del clone › *Run*.
- **Braccialetti**: Arduino IDE › *File › Open* › `firmware/TSM_Band/TSM_Band.ino`, scheda **M5StickS3**, librerie **M5Unified** e **NimBLE-Arduino** (dettagli nella guida, capitolo 5).

## Build e test da terminale

```bash
cd TennisScoreManager
./gradlew testDebugUnitTest assembleDebug
```

Su Windows `.\gradlew testDebugUnitTest assembleDebug` da PowerShell. Serve un JDK 17 o più recente (`JAVA_HOME`): va bene quello incluso in Android Studio, nella sua cartella `jbr`.

Versioni: Gradle 8.14.3 · Android Gradle Plugin 8.13.2 · Kotlin 2.2.21 · compileSdk/targetSdk 36 · minSdk 26 (Android 8.0).

## Dove mettere le mani

| Cosa | File |
|---|---|
| Regole del punteggio (annulla = rigioca gli eventi) | `app/.../model/ScoreEngine.kt` + test in `app/src/test` |
| Frasi e chiamate vocali | `app/.../voice/Calls.kt`, parole e ordine di ogni lingua in `voice/CallWords.kt`, correzioni di pronuncia in `voice/Pronunciation.kt` |
| Tempi (25/90/120/30 s), messaggi, flusso partita | `app/.../MatchController.kt` |
| Protocollo Bluetooth (condiviso col firmware) | `app/.../ble/BandProtocol.kt` e in cima a `TSM_Band.ino` |
| Impostazioni dei braccialetti, stima autonomia | `app/.../ble/BandProtocol.kt` (`BandSettings`), `ble/BatteryModel.kt`, `ui/BandSettingsPanel.kt` |
| Testi nelle sei lingue, grafica | `app/.../ui/Strings.kt` (+ `StringsFr/De/Es/Pt.kt`), `ui/screens/`, `ui/Theme.kt` |
| Tabellone TV (pagina, server, telefono-tabellone) | `app/src/main/assets/scoreboard.html`, `app/.../tv/`, `ui/TvSection.kt` — anteprima: `scoreboard.html?demo=1` |
| Schermata di ricarica del braccialetto | `TSM_Band.ino` (`pollPower`, `drawCharge`) |
