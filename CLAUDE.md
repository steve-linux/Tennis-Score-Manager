# Tennis Score Manager – istruzioni per Claude

Struttura del repo e file da toccare per ogni funzione: vedi [README.md](README.md) («Dove mettere le mani»). Qui solo le regole di lavoro.

## Lingua e testi

- Si parla e si scrive in italiano: risposte, commenti nel codice, messaggi di commit.
- Ogni testo visibile esiste in **sei lingue** (it, en, fr, de, es, pt) e va aggiornato in tutte insieme:
  - app: `ui/Strings.kt` + `StringsFr/De/Es/Pt.kt` (`StringsTest` controlla che nessuna lingua copi l'inglese);
  - guide: `deliver/GUIDA.md` + `deliver/GUIDE.*.md`;
  - F-Droid: `fastlane/metadata/android/<lingua>/` (descrizioni e `changelogs/<versionCode>.txt`);
  - sito: `website/index.html` (italiano) e `website/{en,fr,de,es,pt}/index.html`, stessa struttura in tutte.
- Termini già decisi: «golden point» in tutte le lingue tranne lo spagnolo («punto de oro»); la voce dell'arbitro dice comunque «punto decisivo» e i suoi equivalenti.
- Nelle schermate per sito e store si usano nomi di prova (Rossi, Bianchi, Verdi, Neri), mai nomi veri.

## Git e pubblicazione

- **Commit e push solo quando l'utente lo chiede.** Mai su `main` senza richiesta esplicita.
- `main` = versione pubblicata: F-Droid la compila dai tag e Netlify pubblica `website/` come sito ufficiale (`netlify.toml`).
- Le novità si sviluppano su rami `feature/...`; Netlify ne fa anteprime private (Branch deploys) per i rami aggiunti nelle sue impostazioni.
- Una versione nuova esce solo dopo la prova in campo coi braccialetti veri, non basta che compili.

## Build e prove

```bash
cd TennisScoreManager
./gradlew testDebugUnitTest assembleDebug
```

- Serve un JDK 17+ in `JAVA_HOME` (quello di Android Studio, cartella `jbr`, va bene).
- Prima di dire che una modifica funziona: test verdi e, se tocca l'interfaccia, prova sul telefono (`adb install -r app/build/outputs/apk/debug/app-debug.apk`, conserva i dati).
- Il sito non ha build: si prova servendo `website/` con un server statico qualsiasi e guardandolo anche a larghezza telefono.

## Firmware dei braccialetti (`firmware/TSM_Band/`)

- Richiede il core Arduino ESP32 **3.x** (M5Unified, NimBLE-Arduino); il protocollo Bluetooth è condiviso con l'app (`ble/BandProtocol.kt` e la testa di `TSM_Band.ino`): si cambiano insieme.
- `swing_detector.*` e `stroke_classifier.*` arrivano dal progetto SwingSense: le correzioni vanno riportate anche lì.
- Monitor seriale: aprire la porta con DTR e RTS alti (come fa `pio device monitor`); abbassare DTR prima di RTS resetta lo stick o lo manda in modalità download.
- Dopo ogni modifica sotto `TennisScoreManager/` o `firmware/TSM_Band/` rigenerare l'installer, che li contiene tutti e due: `git add` e poi `python3 deliver/make_installer.py` (usa `git ls-files`).
