# Pubblicare Tennis Score Manager su F-Droid

F-Droid accetta solo app libere che si possono compilare dal sorgente. Tennis Score Manager ha tutto quello che serve: mancano solo tre passi da fare su GitHub e GitLab (capitolo 2).

## 1. Cosa è già pronto nel repository

- **Licenza** GPL-3.0-or-later: file `LICENSE` e intestazione `SPDX-License-Identifier` in ogni sorgente. L'app mostra versione, licenza e link al codice in fondo alla prima pagina.
- **Solo librerie libere**: AndroidX, Jetpack Compose, Kotlin, ZXing (tutte Apache 2.0). Niente Google Play Services, Firebase, pubblicità o statistiche, quindi nessuna *anti-feature* da dichiarare.
- **Si compila dal sorgente** con Gradle (`./gradlew assembleRelease`). L'unico file binario è il jar del Gradle wrapper, che F-Droid controlla da solo.
- **Testi della scheda e icona** in `fastlane/metadata/android/`, nelle sei lingue (`it-IT`, `en-US`, `fr-FR`, `de-DE`, `es-ES`, `pt-BR`): titolo, descrizione breve (massimo 80 caratteri), descrizione completa, novità della versione in `changelogs/<versionCode>.txt`, icona in `en-US/images/icon.png`. F-Droid li rilegge dal repository a ogni versione.
- In `app/build.gradle.kts` il blocco `dependenciesInfo` è spento: F-Droid lo chiede, altrimenti l'APK firmato contiene un elenco delle librerie cifrato per Google Play.

## 2. Cosa resta da fare

1. **Rendere pubblico il repository**: GitHub › *Settings* › *General* › in fondo *Danger Zone* › *Change repository visibility* › *Public*. F-Droid compila solo da sorgenti pubblici. La storia del repository è stata controllata: niente password, chiavi o file privati.
2. **Mettere l'etichetta alla versione**: F-Droid compila da un tag. Dal clone aggiornato:
   ```bash
   git tag v2.4.1
   git push origin v2.4.1
   ```
   oppure su GitHub › *Releases* › *Draft a new release* › tag `v2.4.1` › *Publish release*.
3. **Chiedere l'inserimento in F-Droid**, in uno dei due modi:
   - **Il più semplice**: un account su GitLab e una richiesta *Request For Packaging* su <https://gitlab.com/fdroid/rfp/-/issues> con il link al repository. Un volontario prepara il file del capitolo 3; può volerci qualche settimana.
   - **Il più rapido**: fork di <https://gitlab.com/fdroid/fdroiddata>, aggiungere il file `metadata/com.tennis.scoremanager.yml` del capitolo 3 e aprire una *merge request*. I controlli automatici di F-Droid lo compilano e dicono se manca qualcosa.

Dopo l'approvazione F-Droid compila l'app e la pubblica nel suo catalogo, di solito entro qualche giorno.

## 3. Il file per fdroiddata

```yaml
Categories:
  - Sports & Health
License: GPL-3.0-or-later
AuthorName: Stefano Spagnolo
SourceCode: https://github.com/steve-linux/Tennis-Score-Manager
IssueTracker: https://github.com/steve-linux/Tennis-Score-Manager/issues

AutoName: Tennis Score Manager

RepoType: git
Repo: https://github.com/steve-linux/Tennis-Score-Manager.git

Builds:
  - versionName: 2.4.1
    versionCode: 9
    commit: v2.4.1
    subdir: TennisScoreManager/app
    gradle:
      - yes

AutoUpdateMode: Version
UpdateCheckMode: Tags
CurrentVersion: 2.4.1
CurrentVersionCode: 9
```

Con `UpdateCheckMode: Tags` e `AutoUpdateMode: Version`, ogni nuovo tag `vX.Y.Z` con un `versionCode` più alto viene compilato e pubblicato da F-Droid senza fare altro.

## 4. Da sapere

- **Firma**: l'APK di F-Droid lo firma F-Droid con la sua chiave. Su un telefono che ha già l'app installata da Android Studio, prima di installare quella di F-Droid bisogna disinstallarla: stesso nome di pacchetto, firma diversa. Disinstallando si perdono le partite sospese e lo storico salvato nella cartella predefinita dell'app (`Android/data/com.tennis.scoremanager/files/Storico`): copialo prima, se serve. I riepiloghi salvati in un'altra cartella scelta col selettore restano.
- **Nome del pacchetto** `com.tennis.scoremanager`: F-Droid lo userà per sempre, cambiarlo dopo vorrebbe dire pubblicare un'app nuova. Se si vuole un nome legato a un proprio dominio o all'account GitHub (per esempio `io.github.steve_linux.tsm`), va cambiato **prima** della pubblicazione (`applicationId` in `app/build.gradle.kts`).
- **Schermate** (facoltative, ma rendono la scheda più chiara): file PNG o JPG presi dal telefono in `fastlane/metadata/android/en-US/images/phoneScreenshots/` con nomi `1.png`, `2.png`… Le altre lingue usano quelle inglesi se non hanno le proprie.
- **Ogni nuova versione**: aumentare `versionCode` e `versionName` in `app/build.gradle.kts`, scrivere `changelogs/<versionCode>.txt` nelle sei lingue (massimo 500 caratteri), poi il tag `vX.Y.Z`.
- **Firmware dei braccialetti**: F-Droid distribuisce solo l'app. I braccialetti si programmano dal sorgente con Arduino IDE (guida, capitolo 5).
