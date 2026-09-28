# Tennis Score Manager

App Android per tenere il punteggio del tennis secondo le regole ITF, con chiamate vocali da giudice di sedia (italiano e inglese, anche senza internet) e due braccialetti **M5StickS3** collegati in Bluetooth LE.

| Cartella | Contenuto |
|---|---|
| `TennisScoreManager/` | Progetto Android Studio (Kotlin + Jetpack Compose) |
| `firmware/TSM_Band/` | Sketch Arduino per i braccialetti M5StickS3 |
| `deliver/GUIDA.md` | **Guida passo passo**: installazione, Android Studio, Arduino IDE, uso dell'app, regole applicate |
| `deliver/installa_tsm.sh` | Tutto il progetto come blocchi `cat` (alternativa al clone), rigenerabile con `make_installer.py` |

## Avvio rapido (Fedora)

```bash
git clone https://github.com/steve-linux/Tennis-Score-Manager.git ~/AndroidStudioProjects/Tennis-Score-Manager
```

- **App**: Android Studio › *File › Open* › `~/AndroidStudioProjects/Tennis-Score-Manager/TennisScoreManager` › *Run*.
- **Braccialetti**: Arduino IDE › *File › Open* › `firmware/TSM_Band/TSM_Band.ino`, scheda **M5StickS3**, librerie **M5Unified** e **NimBLE-Arduino** (dettagli nella guida, capitolo 5).

## Build e test da terminale

```bash
cd TennisScoreManager
./gradlew testDebugUnitTest assembleDebug
```

Versioni: Gradle 8.14.3 · Android Gradle Plugin 8.13.2 · Kotlin 2.2.21 · compileSdk/targetSdk 36 · minSdk 26 (Android 8.0).

## Dove mettere le mani

| Cosa | File |
|---|---|
| Regole del punteggio (annulla = rigioca gli eventi) | `app/.../model/ScoreEngine.kt` + test in `app/src/test` |
| Frasi e chiamate vocali | `app/.../voice/Calls.kt`, correzioni di pronuncia in `voice/Pronunciation.kt` |
| Tempi (25/90/120/30 s), messaggi, flusso partita | `app/.../MatchController.kt` |
| Protocollo Bluetooth (condiviso col firmware) | `app/.../ble/BandProtocol.kt` e in cima a `TSM_Band.ino` |
| Impostazioni dei braccialetti, stima autonomia | `app/.../ble/BandProtocol.kt` (`BandSettings`), `ble/BatteryModel.kt`, `ui/BandSettingsPanel.kt` |
| Testi IT/EN, grafica | `app/.../ui/Strings.kt`, `ui/screens/`, `ui/Theme.kt` |
