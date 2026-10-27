# Tennis Score Manager — guida passo passo

**Italiano** · [English](GUIDE.en.md) · [Français](GUIDE.fr.md) · [Deutsch](GUIDE.de.md) · [Español](GUIDE.es.md) · [Português](GUIDE.pt.md)

App Android (Kotlin + Jetpack Compose) per tenere il punteggio del tennis secondo le regole ITF, con chiamate vocali da giudice di sedia **in sei lingue** (italiano, inglese, francese, tedesco, spagnolo, portoghese: capitolo 4.1), due braccialetti **M5StickS3** collegati in Bluetooth LE (oppure due **telecomandini Bluetooth da selfie** da pochi euro: capitolo 6.3) e un **tabellone a LED su TV o monitor** (capitolo 8).

## 0. Cosa c'è nel repository

| Percorso | A cosa serve |
|---|---|
| `TennisScoreManager/` | Il progetto Android Studio. |
| `firmware/TSM_Band/TSM_Band.ino` | Il firmware del braccialetto. |
| `deliver/installa_tsm.sh` | In alternativa al clone: crea **tutto** il progetto Android e lo sketch con soli blocchi `cat << 'TSM_EOF'` (il jar del Gradle wrapper è in base64). |
| `deliver/GUIDA.md` | Questa guida (in italiano); le traduzioni sono `deliver/GUIDE.en.md`, `.fr`, `.de`, `.es`, `.pt`. |

Versioni usate e verificate: app **2.5.0** · firmware **2.3** · Gradle 8.14.3 · Android Gradle Plugin 8.13.2 · Kotlin 2.2.21 · Compose BOM 2025.12.00 · compileSdk/targetSdk 36 · minSdk 26 (Android 8.0). Firmware: M5Unified ≥ 0.2.12, NimBLE-Arduino ≥ 2.1, core ESP32 3.x. Licenza: GNU GPL versione 3 o successiva (`LICENSE`); versione e licenza sono anche in fondo alla prima pagina dell'app.

---

## 1. Preparare il computer e scaricare il progetto

Va bene **Windows 10/11**, **Ubuntu** (22.04 o più recente) o **Fedora**; su altre distribuzioni Linux i passi sono quelli di Ubuntu o di Fedora con il loro gestore di pacchetti. Nei comandi `~` è la tua cartella personale: su Linux `/home/<utente>`, su Windows `C:\Users\<utente>`.

### 1.1 Programmi da installare

| | Windows 10/11 (PowerShell) | Ubuntu | Fedora |
|---|---|---|---|
| **Git** (scaricare e aggiornare il progetto) | `winget install --id Git.Git -e` | `sudo apt install git` | `sudo dnf install git` |
| **Android Studio** (l'app) | `winget install --id Google.AndroidStudio -e` | `sudo snap install android-studio --classic` | archivio `.tar.gz` da developer.android.com/studio, estratto ad esempio in `~/android-studio` |
| **Arduino IDE 2** (i braccialetti) | `winget install --id ArduinoSA.IDE.stable -e` | Flatpak `cc.arduino.IDE2` (vedi sotto) | `flatpak install flathub cc.arduino.IDE2` |
| **Telefono collegato col cavo** | driver USB del produttore, se serve (vedi sotto) | `sudo apt install android-sdk-platform-tools-common` | `sudo dnf install android-tools` |
| **Porta seriale del braccialetto** | niente da fare | `sudo usermod -aG dialout $USER` | `sudo usermod -aG dialout $USER` |

- **Windows**: `winget` c'è già in Windows 10/11 aggiornati e si usa da *PowerShell* o *Terminale*. Si possono anche scaricare gli installer normali da git-scm.com, developer.android.com/studio e arduino.cc/en/software.
- **Ubuntu, Arduino IDE**: se Flatpak non c'è ancora: `sudo apt install flatpak`, poi `flatpak remote-add --if-not-exists flathub https://dl.flathub.org/repo/flathub.flatpakrepo` e `flatpak install flathub cc.arduino.IDE2`; esci e rientra nella sessione perché compaia nel menu. L'AppImage di arduino.cc funziona anche, ma sulle Ubuntu recenti vuole pacchetti e opzioni in più: Flatpak è più semplice.
- **Linux, Android Studio dall'archivio**: si avvia con `bin/studio.sh` dentro la cartella estratta (es. `~/android-studio/bin/studio.sh`). Su Ubuntu anche lo snap va bene; su Fedora c'è pure il Flatpak `com.google.AndroidStudio`, ma l'archivio ufficiale dà meno problemi con il telefono collegato.
- **Linux, gruppo `dialout`** (porta seriale) e pacchetti per il telefono (regole udev): dopo averli installati **esci e rientra nella sessione** (o riavvia), altrimenti porta e telefono restano inaccessibili.
- **Windows, driver del telefono**: molti telefoni funzionano subito. Se Android Studio non vede il telefono installa il driver del produttore: *Samsung Android USB Driver* (dal sito Samsung Developer) per i Samsung, *Google USB Driver* (Android Studio › **Tools › SDK Manager › SDK Tools**) per i Pixel. Il braccialetto non ha bisogno di driver: compare come porta `COM3`, `COM4`…
- Al primo avvio Android Studio fa partire una procedura guidata: scegli **Standard** e lascia che scarichi l'SDK Android (serve internet, qualche GB).

### 1.2 Scaricare il progetto

Il repository su GitHub è **privato**: serve un account GitHub a cui il proprietario ha dato l'accesso.

**A. Con git (consigliato: poi si aggiorna con `git pull`)**

Linux (Ubuntu, Fedora), nel Terminale:

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

- **Accesso**: su Windows git apre da solo la finestra di accesso a GitHub. Su Linux GitHub non accetta la password in `git clone`: il modo più semplice è GitHub CLI (`sudo apt install gh` oppure `sudo dnf install gh`), poi `gh auth login` e, nella cartella `~/AndroidStudioProjects`, `gh repo clone steve-linux/Tennis-Score-Manager`.
- Il repository finisce in `~/AndroidStudioProjects/Tennis-Score-Manager`. Il progetto da aprire in Android Studio è la sua sottocartella **`TennisScoreManager`**; lo sketch del braccialetto è in `firmware/TSM_Band/`.
- Aggiornare: `git pull` dentro `Tennis-Score-Manager`.

**B. Senza git**: sulla pagina GitHub del progetto (con l'accesso fatto) **Code › Download ZIP**, poi estrai lo ZIP, ad esempio in `~/AndroidStudioProjects`. Per una versione nuova si riscarica lo ZIP.

**C. Con lo script** `deliver/installa_tsm.sh`, che contiene tutto il progetto in un unico file di testo (blocchi `cat << 'TSM_EOF'`). Si lancia su Linux o, su Windows, in *Git Bash* (arriva con Git):

```bash
bash installa_tsm.sh
```

- Il progetto va in `~/AndroidStudioProjects/TennisScoreManager`, lo sketch in `~/Arduino/TSM_Band/TSM_Band.ino`.
- Se la cartella del progetto esiste già viene **spostata** in `TennisScoreManager.backup-AAAAMMGG-hhmmss` (i vecchi layout XML e le vecchie classi farebbero fallire la build nuova).
- Per usare altre cartelle: `bash installa_tsm.sh /percorso/progetto /percorso/sketch`.

## 2. Aprire e compilare in Android Studio

1. Avvia Android Studio: su Windows dal menu Start, su Ubuntu (snap) dal menu delle applicazioni, dall'archivio con `bin/studio.sh` (1.1).
2. **File › Open** (o **Open** nella finestra di benvenuto) → scegli la cartella `TennisScoreManager` (dentro il clone o lo ZIP, oppure quella creata dallo script) → **Trust Project**.
3. Aspetta la sincronizzazione Gradle (la prima volta scarica Gradle, il plugin Android e le librerie: serve internet solo adesso).
4. Se compare *"Failed to find target android-36"* clicca il link **Install missing platform** (oppure **Tools › SDK Manager › SDK Platforms › Android 16 (API 36)**).
5. Se Android Studio propone l'**AGP Upgrade Assistant**, puoi ignorarlo: queste versioni sono state compilate e testate così.
6. JDK di Gradle: **Settings › Build, Execution, Deployment › Build Tools › Gradle › Gradle JDK** = *jbr-21* (quello incluso, è il predefinito).
7. **Build › Make Project**: deve finire con *BUILD SUCCESSFUL*.
8. Facoltativo: i test (95: regole, voce, lingue, batteria e ricarica, impostazioni dei braccialetti, tabellone TV) si lanciano con tasto destro su `app/src/test` › **Run Tests**.

## 3. Installare l'app sul telefono

1. Sul telefono: **Impostazioni › Info telefono** → tocca 7 volte *Numero build* → **Opzioni sviluppatore › Debug USB** attivo.
2. Colleghi il cavo, accetti l'impronta RSA, scegli il telefono in alto e premi **▶ Run**.
   - Se il computer non vede il telefono: su Windows il driver USB del produttore, su Linux il pacchetto con le regole udev (1.1); poi ricollega il cavo. In alternativa, senza cavo né driver: **Device Manager › Pair devices using Wi-Fi** (debug wireless, Android 11+, computer e telefono sulla stessa rete).
3. In alternativa **Build › Build App Bundle(s) / APK(s) › Build APK(s)**, copia l'APK sul telefono e installalo (va consentita l'installazione da origini sconosciute).

## 4. La voce (funziona senza internet)

- Ogni chiamata è letta **in un'unica frase** dalla sintesi vocale del telefono, con una voce installata: niente internet e prosodia naturale. I nomi dei giocatori entrano nella frase.
- Pagina 2 › **Audio e voce**:
  - **Motore sintesi vocale**: *Predefinito del telefono* (sui Samsung è Samsung TTS) oppure un motore a scelta, ad esempio *Servizi di sintesi vocale di Google*. Nei test Google italiano suona più naturale.
  - **Voce**: *Automatica* (la migliore offline) oppure una voce precisa (con Google: Voce ITB, ITC, ITD, KDA; quelle *online* richiedono internet).
  - **Prova voce**: legge una sequenza di chiamate di esempio con i nomi inseriti. Mentre legge il tasto diventa **Ferma la prova**: toccalo di nuovo per interromperla (si ferma da sola anche uscendo dalla pagina). La sintesi vocale non si può mettere in pausa a metà frase, quindi il tasto la ferma; ripremendolo riparte dall'inizio.
- Se manca la voce della lingua scelta: **Impostazioni › Gestione generale › Lingua › Sintesi vocale** (o *Output sintesi vocale*) → scarica la voce di quella lingua per il motore scelto (l'app mostra anche il tasto **Installa voce**). Se invece la sintesi vocale non parte proprio, l'app lo dice con un messaggio a parte e il tasto **Apri impostazioni** (impostazioni della sintesi vocale di Android).
- **Pronuncia**: alcuni motori sbagliano parole del tennis ("primo set" letto "primo settembre", "tie-break" letto "time break"). L'app le corregge da sola (`voice/Pronunciation.kt`); le correzioni sono state verificate trascrivendo l'audio reale di Samsung e Google.
- **Registrazioni personalizzate** (la voce più naturale in assoluto: la tua o quella di un arbitro): nella cartella `Android/data/io.github.steve_linux.tsm/files/voice/` c'è `LEGGIMI.txt` con l'elenco delle 85 chiavi e delle frasi (le spiegazioni in testa sono nella lingua dell'app e si riscrivono quando la cambi). Registra i file con quei nomi (`score_1_0.mp3` = "quindici zero"…), mettili in uno ZIP con una cartella per lingua (`it/`, `en/`, `fr/`, `de/`, `es/`, `pt/`) e usa **Importa ZIP**. `LEGGIMI.txt` ha una colonna per lingua separata da tabulazioni, quindi si apre bene anche come foglio di calcolo. Le registrazioni hanno sempre la precedenza sulla sintesi; i nomi restano letti dalla sintesi. Nello ZIP conta la cartella che contiene il file (anche dentro altre cartelle, es. `voice/it/`); i file fuori da una cartella di lingua vanno alla lingua corrente e le cartelle `tts/` si ignorano, quindi si può zippare anche la cartella `voice/` dell'app. Uno ZIP rovinato o incompleto non cambia niente (*ZIP non leggibile o incompleto*). **Rimuovi registrazioni** chiede conferma. Se un file non si riesce a riprodurre, quella frase la dice la sintesi vocale e il file non si usa più fino al riavvio dell'app.
- **Usa file audio pre-generati** (facoltativo): con **Genera file** l'app crea una volta gli 85 file con la voce scelta (anche una voce *online*, se in quel momento c'è internet) e poi li usa al posto della sintesi continua. Suona più "a pezzi", ma è utile per portarsi offline una voce online. I file di prima restano finché quelli nuovi non sono pronti (se la generazione non arriva in fondo: *Generazione non completata*); mentre genera, lingua, motore, voce, prova voce e audio sono bloccati.
- **Cassa esterna**: basta accoppiarla al telefono in Bluetooth; la voce esce sul canale multimediale (regola il volume media).
- **Cuffie o auricolari Bluetooth**: si accoppiano allo stesso modo e la voce la senti solo tu, senza disturbare i campi vicini; vanno bene anche per chi segna da bordo campo. I braccialetti possono restare collegati insieme alle cuffie.
- **Due dispositivi audio insieme**: molti telefoni mandano l'audio a due dispositivi Bluetooth contemporaneamente. Sui Samsung: connetti entrambi, apri il pannello rapido › **Uscita multimediale** e spunta tutti e due. Così ogni giocatore può avere i suoi auricolari, oppure la cassa suona per il campo e le cuffie per chi segna.

### 4.1 Lingue (app 2.3, firmware 2.2)

Pagina 2 › **Lingua**: Italiano, English, Français, Deutsch, Español, Português (ogni lingua è scritta nella lingua stessa, così la si ritrova anche con l'app in una lingua che non si legge). La scelta vale insieme per **schermate, voce dell'arbitro, tabellone TV, riepilogo/condivisione e braccialetti**. Le date seguono la lingua (*mercredi 30 septembre 2026*, *Mittwoch, 30. September 2026*…).

Le chiamate non sono traduzioni parola per parola: seguono i testi ufficiali per i giudici di sedia di ciascuna federazione, cioè FFT *L'arbitrage en 255 questions* e ITF in francese, i materiali DTB/BTV e Swiss Tennis, RFET *Deberes y procedimientos*, FPT *Deveres e Procedimentos* (l'unico copione portoghese pubblicato). L'inglese segue l'ITF. In inglese lo zero dei set a fine partita si dice *love* ("six love, six four"), come nei punti.

| | Italiano | English | Français | Deutsch | Español | Português |
|---|---|---|---|---|---|---|
| Inizio | primo set · Rossi al servizio · gioco | first set · Rossi to serve · play | première manche · au service Rossi · jouez | erster Satz · Aufschlag Rossi · spielen | primer set · al servicio Rossi · jueguen | primeiro set · Rossi ao serviço · joguem |
| 15-0 · 15-15 | quindici zero · quindici pari | fifteen love · fifteen all | quinze-zéro · quinze A | fünfzehn null · fünfzehn beide | quince cero · quince iguales | quinze-zero · quinze iguais |
| 40-40 · vantaggio | parità · vantaggio Rossi | deuce · advantage Rossi | égalité · avantage Rossi | Einstand · Vorteil Rossi | iguales · ventaja Rossi | iguais · vantagem Rossi |
| Fine game | gioco Rossi, Rossi conduce tre giochi a due | game Rossi, Rossi leads three games to two | jeu Rossi, Rossi mène trois jeux à deux | Spiel Rossi, Rossi führt drei zu zwei | juego Rossi, Rossi gana tres juegos a dos | jogo Rossi, Rossi vence por três jogos a dois |
| Game pari | due giochi pari | two games all | deux jeux partout | zwei beide | dos juegos iguales | dois jogos iguais |
| Tie-break | tre a uno Rossi · sei pari | three one Rossi · six all | trois un Rossi · six partout | drei zu eins Rossi · sechs beide | tres uno Rossi · seis iguales | três a um Rossi · seis iguais |
| Set | un set a zero · un set pari | one set to love · one set all | une manche à zéro · une manche partout | eins zu null in Sätzen · ein Satz beide | un set a cero · un set iguales | um set a zero · sets iguais |
| Fine partita | gioco, set, partita Rossi | game, set and match Rossi | jeu, set et match Rossi | Spiel, Satz und Sieg Rossi | juego, set y partido Rossi | jogo, set e partida Rossi |
| Cambio campo | cambio campo | change ends | changement de côté | Seitenwechsel | cambio de lado | troca de lado |

Particolarità:
- In francese e spagnolo il nome va **dopo** "au service"/"al servicio", in tedesco dopo "Aufschlag". Il francese chiama il set **manche** (tranne in "jeu, set et match") e il tie-break **jeu décisif**.
- **10-6 … 10-9** nel super tie-break: in francese, spagnolo e portoghese si dice "dix **à** huit", "diez **a** ocho", "dez **a** oito", perché "dix huit" / "diez ocho" / "dez oito" si sentono come *diciotto*.
- **Portoghese**: una sola opzione, con interfaccia e voce preferita del Brasile (se manca la voce brasiliana si usa quella portoghese) e le chiamate del copione FPT, con parole valide nei due paesi ("jogo" e non "game", "partida"). "Um set a um" è diventato "sets iguais": al singolare *set* si pronuncia come *sete* e sembrava 7-1.
- **Pronuncia** (`voice/Pronunciation.kt`): tutte le frasi di tutte le lingue sono state fatte leggere alle voci Google e trascritte con whisper. Correzioni aggiunte: in tedesco "Tie-Break" si fa leggere "Taibreak", con una virgola davanti (altrimenti "Teilbreg" o "bei Detailbreak"); in francese la "à" isolata dei file pre-generati si fa leggere "a" (altrimenti "a accent grave"). **Samsung TTS nelle nuove lingue non è ancora stato provato.**
- **Braccialetti** (firmware 2.2): l'app manda da sola la lingua a ogni collegamento e quando la cambi; il braccialetto la salva e la usa anche da scollegato (ricerca del telefono, ricarica, spegnimento), senza accenti perché il font è ASCII (*EN CHARGE*, *LAEDT*, *CARGANDO*…). Con un firmware 2.1 o precedente i messaggi mandati dall'app sono tradotti, quelli interni del braccialetto restano in italiano. Un braccialetto appena programmato parte in italiano; dal firmware 2.2.2 il suo messaggio di collegamento viene riscritto nella lingua dell'app appena questa gliela manda, circa un secondo dopo il collegamento.

## 5. Firmware dei braccialetti (Arduino IDE)

1. Installa Arduino IDE 2 (1.1). Su Linux serve anche il permesso sulla porta seriale (gruppo `dialout`, 1.1), dopo essere usciti e rientrati nella sessione.
2. **File › Preferences › Additional boards manager URLs**:
   `https://static-cdn.m5stack.com/resource/arduino/package_m5stack_index.json`
3. **Boards Manager** → cerca **M5Stack** → installa la versione **≥ 3.2.5**.
4. **Library Manager** → installa **M5Unified** (accetta "Install all" per M5GFX) e **NimBLE-Arduino** di *h2zero* (≥ 2.1).
5. **File › Open** → `firmware/TSM_Band/TSM_Band.ino` del clone o dello ZIP (oppure `~/Arduino/TSM_Band/TSM_Band.ino` se hai usato lo script). Dal firmware 2.4 la cartella `TSM_Band` contiene anche `swing_detector.cpp/.h` e `stroke_classifier.cpp/.h`: devono stare accanto a `TSM_Band.ino` (copiando solo il file `.ino` la compilazione fallisce).
6. **Tools › Board › M5Stack › M5StickS3**, **Tools › Port** → la porta del braccialetto: su Linux `/dev/ttyACM0`, su Windows `COM3`, `COM4`… (quella che compare quando colleghi il cavo).
   *(Senza il pacchetto M5Stack va bene anche "ESP32S3 Dev Module": USB CDC On Boot = Enabled, Flash Size = 8MB, Partition = 8M with spiffs.)*
7. **Upload (→)**. Se la porta non compare o il caricamento fallisce: prova un altro cavo USB-C (alcuni servono solo a ricaricare), poi tieni premuto a lungo il **tasto laterale** (modalità download) e riprova; in modalità download su Windows il numero della porta COM può cambiare.
   **Dopo il caricamento**, se il display resta nero (il braccialetto è rimasto in modalità programmazione), premi **una volta** il tasto laterale: riparte con il programma nuovo.
8. All'avvio il braccialetto mostra il suo nome, es. **TSM-3FA2** (si può cambiare dall'app, vedi 6.1). Ripeti per il secondo braccialetto.

> **Firmware 2.4** (con l'app 2.5): riconoscimento dei colpi (6.4); gli esempi di calibrazione restano nel braccialetto. **Firmware 2.3** (con l'app 2.4): il bip di KEY1/KEY2 suona quando il telefono ha ricevuto il tocco, i tocchi fatti durante una breve interruzione partono appena si ricollega, *NON INVIATO* se il telefono non conferma (6); dopo lo spegnimento col cavo collegato si riaccende anche con KEY1 o KEY2; le letture della batteria fallite non arrivano più all'app come 0 %. Con un'app più vecchia il braccialetto 2.3 funziona come prima. **Firmware 2.2.2**: il messaggio di collegamento passa alla lingua dell'app appena arriva (4.1); *VINCULANDO…/VINCULADA* in spagnolo e *PAREADA* in portoghese come nell'app, *MANCHES* in francese nella schermata dei game. **Firmware 2.2.1**: anche il conto alla rovescia prima dello spegnimento nella lingua dell'app (6). **Firmware 2.2**: i testi del braccialetto nella lingua dell'app (4.1). **Firmware 2.1**: la schermata di ricarica (6.2); le impostazioni, *Identifica* e lo spegnimento dall'app richiedono almeno il 2.0. Carica `TSM_Band.ino` su **entrambi** i braccialetti; con un firmware vecchio l'app lo dice nel pannello delle impostazioni e il resto continua a funzionare.

## 6. Usare i braccialetti

| Tasto | Azione |
|---|---|
| **KEY1** (frontale) corto | punto a chi indossa il braccialetto (avvia anche la partita dalla pagina INIZIO PARTITA); un bip conferma (firmware 2.3 + app 2.4: quando il telefono l'ha ricevuto, circa mezzo secondo dopo) |
| **KEY1** lungo (1 s) | mostra la batteria (e tiene acceso il braccialetto se sta per spegnersi per inattività) |
| **KEY2** corto | annulla l'ultimo punto (anche dal popup di fine partita); due bip più bassi (con il firmware 2.3 alla conferma del telefono) |
| **KEY2** lungo (2 s) | spegne il braccialetto (col cavo collegato scrive *LA CARICA CONTINUA*: si ricarica anche spento; dal firmware 2.3 si riaccende anche con KEY1 o KEY2) |
| Tasto laterale | un clic accende; doppio clic spegne (funzione hardware) |

- All'accensione lampeggia **PAIRING...** (acceso 0,35 s ogni 2 s per risparmiare). L'app si collega da sola ai braccialetti che conosce appena è aperta; quelli nuovi li trova sulla pagina 2.
- Collegato: **PAIRING OK** per 3 secondi con due bip, poi **ASSOCIATO A** + il nome del giocatore.
- A ogni punto il display si accende con il punteggio del game in grande (a sinistra il tuo, a destra l'avversario; la pallina verde indica chi serve), poi si spegne. A fine game mostra game e set. Se il telefono non conferma un tocco entro 8 secondi (collegamento perso) compare **NON INVIATO** in rosso con due bip bassi: premi di nuovo quando è ricollegato. Un tocco fatto durante un'interruzione breve invece non si perde: parte appena il braccialetto si ricollega, e il bip arriva in quel momento.

**Spegnimento automatico** (i tempi si cambiano dall'app, 6.1):

| Situazione | Cosa fa il braccialetto | Predefinito |
|---|---|---|
| Acceso, ma nessun telefono si collega | lampeggia PAIRING, poi si spegne | **30 s** |
| Telefono perso (spento, fuori portata, Bluetooth spento, app chiusa di colpo) | lampeggia RICONNESSIONE e si ricollega da solo appena può; altrimenti si spegne | 3 min |
| Collegato ma inattivo (nessun punto, nessun messaggio) | 30 s prima avvisa con **INATTIVO · TIENI PREMUTO KEY1** e un bip, poi si spegne | 30 min |
| Partita conclusa (confermata sul telefono) o **Esci** dall'app | mostra FINE PARTITA / APP CHIUSA e si spegne subito | attivo (si può disattivare, 7.2) |
| Batteria scarica (sotto 3,30 V per due letture di fila) | mostra BATTERIA SCARICA e si spegne, per non restare acceso a metà | sempre |

- Negli ultimi 10 secondi prima dello spegnimento senza telefono mostra **SPEGNIMENTO · 8s - PREMI UN TASTO** con un bip: un tasto qualsiasi rimanda lo spegnimento da capo.
- Quando si spegne da solo lo dice al telefono: in partita il riquadro arancione mostra *BRACCIALETTO 1 SPENTO (INATTIVO)*, *(BATTERIA SCARICA)*… Dopo lo spegnimento a fine partita il telefono smette di cercarlo di continuo e si ricollega da solo quando lo riaccendi.

**Durata della batteria**: la voce che pesa di più è la scheda ESP32-S3 col Bluetooth collegato (circa 35 mA): il core Arduino è compilato senza il risparmio energetico profondo (light sleep) quando il Bluetooth è acceso, quindi non si può scendere molto sotto. Display, luminosità e bip aggiungono pochi mA. Con la batteria da 250 mAh la stima è **circa 7 ore da carica piena**, più di qualsiasi partita al meglio dei tre set. Il pannello delle impostazioni mostra la stima in tempo reale e, dopo 20 minuti di uso, la corregge con il consumo **misurato** su quel braccialetto. Il registro completo è in `files/battery_log.csv` dell'app.

Il resto del risparmio: CPU a 80 MHz, display spento quando non serve, Bluetooth a basso consumo (la radio si sveglia 2-3 volte al secondo, il tasto parte comunque entro ~120 ms), cicalino acceso solo durante i bip, microfono e 5V spenti, sensore di movimento (IMU) acceso solo col riconoscimento dei colpi (6.4).

### 6.1 Impostazioni del braccialetto

Dalla pagina 2 (**Impostazioni** sotto il braccialetto di ciascun giocatore) o durante la partita (tocca **G1**/**G2** in alto, oppure l'icona dei cursori). Si salvano **nel braccialetto** e restano anche spegnendolo; a ogni modifica il braccialetto mostra il nome con *IMPOSTAZIONI OK*. Col firmware 2.4 c'è anche la sezione **Colpi** (6.4).

| Impostazione | Valori | Predefinito |
|---|---|---|
| Nome | fino a 12 caratteri (es. il nome del giocatore o "G1") | TSM-xxxx |
| Luminosità display | 5-100 % | 20 % |
| Punteggio visibile dopo ogni punto | No, 2, 3, 5, 8 s (il riepilogo di fine game dura 2 s in più) | 3 s |
| Volume cicalino | Muto-100 % | 50 % |
| Display capovolto | per portarlo sull'altro polso | no |
| Spegnimento: all'accensione senza telefono | 15 s - 5 min | 30 s |
| Spegnimento: telefono perso | 1-10 min | 3 min |
| Spegnimento: collegato ma inattivo | 10-60 min | 30 min |

Sotto c'è la **stima dell'autonomia** (da carica piena e con la carica attuale) con il consumo diviso per voce: cambia mentre muovi i cursori, prima ancora di confermare. Poi **Identifica**, **Spegni** e **Copia sull'altro braccialetto** (stesse impostazioni, il nome resta il suo). **Spegni** chiede conferma, anche a partita in corso.

### 6.2 Ricarica (firmware 2.1)

Collega il cavo USB-C: il braccialetto fa un bip e mostra la **schermata di carica** per 30 secondi, poi il display si spegne e ogni 10 secondi si riaccende per 1,5 s (un'occhiata, come la spia di un caricatore). **Un tasto qualsiasi** la riaccende per altri 30 secondi. Se il braccialetto era spento, accendilo con un clic sul tasto laterale per vederla (la carica avviene comunque, anche da spento).

```
 TSM-3FA2   USB 5.01V           ← nome e tensione del cavo
 ┌──────────┐
 │██████ ⚡  │▌   78%             ← pila e percentuale di carica
 └──────────┘
      IN CARICA                  ← oppure CARICA COMPLETA / ALIMENTATO DA USB
 4.12V  DA 42 MIN  FINE ~25 MIN  ← tensione batteria, da quanto è in carica, fine stimata
```

- **Percentuale**: in carica la tensione misurata è più alta di quella vera (≈0,1 V), il braccialetto la corregge, non la fa mai scendere e arriva a **100 %** solo quando il caricabatterie dice che ha finito. A carica completa la scritta diventa **CARICA COMPLETA** con il tempo impiegato (*CARICATA IN 1H 25*) e il display resta spento (niente lampi di notte).
- **Fine stimata**: il chip di alimentazione (PM1) non misura la corrente di carica, quindi il tempo che manca si ricava da quanto è salita la carica negli ultimi 10 minuti: compare dopo 10 minuti, arrotondato a 5, ed è una stima.
- **ALIMENTATO DA USB**: il cavo c'è ma la batteria non si carica (e non è piena): cavo o alimentatore scarsi, o batteria scollegata.
- **Col cavo non si spegne da solo** (niente spegnimento per telefono assente o inattività, niente *batteria scarica*); si può spegnere con KEY2 lungo. Staccato il cavo mostra **USB SCOLLEGATO · BATTERIA 97%** e da lì ripartono i tempi normali di spegnimento (6).
- **Collegato al telefono** (es. un powerbank in partita) il punteggio ha la precedenza: solo un messaggio breve *IN CARICA 78%*, e KEY1 lungo mostra *CARICA COMPLETA* o *IN CARICA*.
- **Nell'app**: pagina 2 e impostazioni del braccialetto mostrano *In carica 78%* o *Carica completa*; in partita le pastiglie G1/G2 hanno il simbolo ⚡. Anche il registro `files/battery_log.csv` ha due colonne in più (tensione USB, carica completa): utile per verificare in quanto si ricarica davvero.
- Braccialetto e app ora calcolano la percentuale con la **stessa curva** della LiPo (prima il braccialetto usava una retta meno precisa), quindi mostrano lo stesso numero.

> Da verificare coi braccialetti veri: la ricarica non si è potuta provare sull'hardware. In particolare quanto spesso il caricabatterie segnala *carica completa* e quanto è precisa la percentuale in carica.

### 6.3 Senza braccialetti: telecomandi Bluetooth (app 2.5)

Il sistema più economico per far segnare i punti ai giocatori: due **telecomandini Bluetooth da selfie** (quelli col tasto di scatto, spesso venduti come *AB Shutter3*), da 1 a 3 € l'uno. Android li vede come una tastiera: niente firmware da caricare e nessun permesso per l'app. Va bene anche qualsiasi altra pulsantiera o tastiera Bluetooth (o USB).

1. Accendi il telecomando e accoppialo dalle **impostazioni Bluetooth** del telefono, come una cuffia (pagina 2 › *Impostazioni Bluetooth* le apre). Ripeti col secondo.
2. Pagina 2 › modalità **Telecomandi** › **Associa** sotto il Giocatore 1 › premi il tasto del suo telecomando che darà il punto; poi lo stesso per il Giocatore 2. A ogni clic di un telecomando associato la sua riga mostra **Tasto ricevuto**: così si prova prima di giocare. **Scambia G1 ↔ G2** li inverte.
3. **INIZIO PARTITA**: un clic su un telecomando avvia la partita, come KEY1 dei braccialetti. In alto **G1**/**G2** sono colorati quando i telecomandi sono collegati.

| Gesto | Azione |
|---|---|
| un clic | punto a chi ha il telecomando (arriva dopo mezzo secondo: l'app aspetta di vedere se è un doppio clic) |
| doppio clic | annulla l'ultimo punto (anche dal popup di fine partita) |
| tenere premuto 1 s | annulla l'ultimo punto |
| l'altro tasto (telecomandi a due tasti) | annulla l'ultimo punto, subito; premuto entro mezzo secondo da un clic, annulla quel clic |

- Valgono le protezioni dei braccialetti: due punti a meno di 2 secondi contano una volta sola, due annulla ravvicinati tolgono un solo punto, *Partita conclusa* si conferma solo dal telefono.
- Molti telecomandi da selfie hanno due tasti (*iOS* e *Android*): il tasto premuto all'associazione dà il punto, l'altro annulla. Sul CAMKIX (un *AB Shutter3*) il tasto grande manda VOLUME SU e il piccolo INVIO: associalo col tasto grande e il piccolo diventa «annulla». Se un telecomando manda due tasti a ogni pressione, una pressione resta un clic.
- **Lo schermo deve restare acceso con l'app davanti** (in partita l'app lo tiene acceso da sola). A differenza dei braccialetti, i tasti di una tastiera Bluetooth arrivano solo all'app visibile: a schermo bloccato o con un'altra app aperta non contano. Per partite lunghe tieni il telefono in carica.
- Se un telecomando si scollega in partita (spento, scarico, troppo lontano) il riquadro arancione mostra *TELECOMANDO 1 SCOLLEGATO*, e *COLLEGATO* quando torna. Molti telecomandi si addormentano dopo qualche minuto senza clic: il primo clic li risveglia e a volte va perso. Se la voce non dice il punto, premi ancora.
- Portata: una decina di metri, come le cuffie Bluetooth; col telefono vicino alla rete, a metà campo, si coprono entrambi i lati. Pila: di solito una CR2032, che dura mesi.

### 6.4 Riconoscimento dei colpi (firmware 2.4, app 2.5)

Col firmware 2.4 il braccialetto riconosce **dritto, rovescio, servizio e volée** con il sensore di movimento (IMU) e li manda all'app, che li conta per giocatore. Il riconoscimento impara da chi indossa il braccialetto: prima serve una breve **calibrazione**, i cui esempi restano salvati nel braccialetto.

1. **Fissa il braccialetto al polso**, sempre nello stesso modo (stesso polso, stesso verso). Il riconoscimento si basa sulla direzione dei movimenti: un braccialetto girato rispetto alla calibrazione confonde i colpi.
2. Impostazioni del braccialetto (6.1) › **Colpi** › **Calibrazione**: tocca **Calibra** accanto a *Dritto*. Il braccialetto mostra *DRITTO · CALIBRAZIONE 0/20*.
3. Fai **5-10 dritti** con una pausa tra uno e l'altro: a ogni colpo il contatore sale, sul braccialetto e nell'app (e c'è un bip, se il volume non è a zero). Se un colpo è venuto male, tocca **Annulla l'ultimo**. Poi **Fine**.
4. Ripeti per rovescio, servizio e volée. Il cestino cancella gli esempi di un colpo, per rifarli. Si tengono al massimo 20 esempi per colpo (oltre, il più vecchio viene sostituito). Bastano 3 esempi per almeno due colpi (*Pronto: riconosce i colpi calibrati*), ma più esempi e più varietà danno un riconoscimento migliore.
5. Attiva **Riconoscimento colpi**. Con **Mostra il colpo sul braccialetto** il colpo compare per un attimo con la sicurezza del riconoscimento (es. *ROVESCIO 87%*), solo se il display non sta mostrando punteggio o messaggi.

- In partita, sotto i tempi, una riga mostra i colpi di ciascun giocatore (*G1 D 12 · R 8 · S 5 · V 3*). Contano solo a partita iniziata, non nel riscaldamento né in calibrazione.
- Nel riepilogo (7) ci sono i colpi per tipo, la **velocità del polso** (media e massima, in °/s: quanto è spinto il colpo, non la velocità della palla) e i **colpi per punto** (lunghezza degli scambi). Se solo un giocatore ha il braccialetto coi colpi, gli scambi contano solo i suoi.
- Un colpo che non somiglia a nessun esempio non viene mostrato e conta come *non riconosciuto*: se capita spesso, controlla come porti il braccialetto o aggiungi esempi.
- I movimenti lenti (riprendere posizione, raccogliere la palla) non contano: un colpo deve superare 400 °/s di rotazione del polso e 2,5 g di accelerazione.
- **Batteria**: il sensore acceso consuma circa 1 mA in più, una ventina di minuti di autonomia su una carica piena (la stima nelle impostazioni ne tiene conto). Col riconoscimento spento il sensore resta spento.
- Nei colpi molto forti il giroscopio arriva al suo massimo (2000 °/s): il riconoscimento non ne soffre, ma la velocità indicata è un po' sottostimata.

### 6.5 Fasce cardio (app 2.5)

Battito e calorie da **fasce toraciche Bluetooth** che usano il servizio cardio standard: Coospo (provata la H6), Magene, Polar, Garmin, Wahoo… Valgono in tutte le modalità, anche senza braccialetti.

1. Bagna gli elettrodi e indossa la fascia: si accende da sola a contatto con la pelle.
2. Pagina 2 › **Battito cardiaco** › attiva **Fasce cardio** e scegli la fascia di ciascun giocatore (si sceglie una volta sola). **Scambia** le inverte.
3. Facoltativi **età, peso e sesso** di chi la indossa: servono per le calorie (formula di Keytel, errore tipico ±20%) e, con l'età, per le zone cardio (massimo stimato 208 − 0,7 × età).

- Sotto ogni fascia: stato, battito, **segnale** in dBm (più vicino a 0 = più forte) e **stacchi**, cioè le volte in cui il battito non è arrivato per più di 3 secondi, con i secondi persi (*2 stacchi (14 s)*). Contano dal «gioco» della partita; prima, da quando la fascia è stata scelta. Servono a capire fin dove arriva la fascia.
- In partita una riga mostra il battito dal vivo (*G1 · 142 bpm*). Nel riepilogo (7): battito medio e massimo, calorie, grafico nel tempo e tempo nelle zone cardio. Le pause non contano.
- **Portata**: prova in casa con la H6, telefono fermo: collegata fino a 21 m, ma a −100 dBm, al limite; di schiena il segnale cala di 5-10 dBm. In singolare il giocatore lontano sta a 24-30 m dal telefono: per non perdere dati usa il **secondo telefono** (6.6).

### 6.6 Secondo telefono a fondo campo (app 2.5)

Fasce e braccialetti accettano **un telefono alla volta** e arrivano a pochi metri meno della lunghezza del campo. Con un secondo telefono all'altro fondo campo ogni giocatore ne ha sempre uno vicino: il secondo (**satellite**) sente fasce e braccialetti dei giocatori dalla sua parte e fa da ponte con il **principale**, che tiene la partita.

1. **Principale**: pagina 2 › **Secondo telefono** › **Usa un secondo telefono**. La prima volta chiede il permesso *Dispositivi nelle vicinanze* (fino ad Android 12 la posizione). Il telefono crea una **rete sua, solo locale**: niente SIM né dati, e non serve il Wi-Fi del circolo. Compare un QR.
2. **Satellite**: pagina 2 › **Secondo telefono** › **Fai da secondo telefono** › inquadra il QR. Android chiede una volta di collegarsi alla rete del principale (*AndroidShare_…*): tocca **Connetti**. Il satellite tiene i dati mobili. Deve dire *Collegato al telefono principale*.
3. Il satellite segue da solo le fasce e i braccialetti scelti sul principale, sugli stessi lati: non si sceglie niente. Lascialo a fondo campo con l'app aperta.

- **Cambio campo**: non serve fare niente. Ogni fascia e ogni braccialetto resta al telefono a cui è collegato finché il segnale regge, poi lo prende quello più vicino (qualche secondo). Sul principale compare *Dal secondo telefono*; durante il passaggio un braccialetto può risultare scollegato per un attimo.
- **Niente dati persi**: battito, tasti e colpi del satellite portano l'**ID della partita** e un numero progressivo, e il satellite li tiene finché il principale non li conferma. Se il Wi-Fi tra i telefoni cade, il battito arriva dopo e riempie i buchi del grafico; i colpi contano se sono della stessa partita. Un **tasto** del braccialetto arrivato più di 5 secondi dopo invece non dà il punto: premi di nuovo. Lo stesso tasto arrivato da due strade conta una volta sola.
- Dal principale ai braccialetti passano punteggio, messaggi, *Identifica*, spegnimento e impostazioni. Le impostazioni conviene cambiarle col braccialetto collegato al principale.
- **Tabellone**: sul satellite **Mostra il tabellone** apre il tabellone della partita a schermo intero (come in 8.3), già collegato al principale.
- Se il principale non riesce a creare la rete (hotspot normale già acceso), usa il Wi-Fi in cui si trova: in quel caso i due telefoni devono stare sulla stessa rete. Con Android 9 o prima il satellite mostra nome e password della rete, da scegliere a mano nelle impostazioni Wi-Fi.
- **Smetti** chiude il collegamento e la rete; il satellite torna alle sue fasce e ai suoi braccialetti.

## 7. Come si usa l'app

1. **Nuova partita** (facoltativa): **Tennis** o **Padel** (app 2.5: stesse regole, tempi e chiamate; scegliendo Padel si impostano da soli doppio e golden point (punto d'oro), che si possono cambiare (al posto del golden point, a pagina 2, c'è anche lo *star point* della FIP: al massimo due vantaggi, poi un punto decisivo), e il riepilogo dice *Padel*), circolo, campo, singolare/doppio, nomi (nel doppio due nomi per squadra). **Cerca l'indirizzo del campo** (spento di serie) aggiunge al riepilogo l'indirizzo oltre alle coordinate: lo chiede al servizio di geocodifica del telefono, che con i servizi Google manda le coordinate a Google. **Avanti**. Con *Padel* compare anche **Torneo Americano o Mexicano**: da 4 a 16 giocatori e da 1 a 4 campi, partite a punti (16, 24 o 32: i punti delle due coppie sommati fanno il totale) o a tempo. Nell'*Americano* le coppie cambiano a ogni turno perché ognuno giochi con e contro più persone possibile; nel *Mexicano*, dal secondo turno, si gioca per classifica (1° e 4° contro 2° e 3° nello stesso campo). A ogni turno si scrive il risultato di ogni campo (nelle partite a punti basta un numero, l'altro si completa) oppure lo si conta con *Conta punto per punto*, con la voce e, a tempo, il cronometro; con più giocatori dei posti in campo si riposa a turno. **Turno successivo** parte quando tutti i risultati ci sono; la **Classifica** conta i punti fatti da ciascuno, poi le vittorie e la differenza punti, e si condivide come testo.
2. **Modalità e regole**:
   - *Arbitro*, *Braccialetti* o *Telecomandi* (6.3). Con i braccialetti sono obbligatori Bluetooth acceso, permesso posizione e posizione attiva: le righe dei **Requisiti** si aggiornano in tempo reale (anche se spegni Bluetooth o posizione dalla tendina) e diventano rosse con il pulsante per sistemarle.
   - La **ricerca è automatica e continua** finché la pagina è aperta: accendi i braccialetti e vanno da soli nei posti liberi (prima Giocatore 1, poi Giocatore 2). **Identifica** fa lampeggiare quel braccialetto nel colore del giocatore (giallo o rosso) con dei bip, così vedi subito quale hai in mano; **Scambia G1 ↔ G2** li inverte senza scollegarli. Dal menu a tendina puoi sempre sceglierne un altro o *Nessuno* (quello tolto a mano non viene rimesso dalla ricerca). Un braccialetto non può stare su due giocatori.
   - **Spegni i braccialetti a fine partita e all'uscita** (attivo di serie): alla conferma di *Partita conclusa* e con *Esci* i braccialetti si spengono invece di aspettare l'inattività.
   - In modalità arbitro, premendo **Avanti** senza posizione compare l'invito ad attivarla (altrimenti il luogo non sarà nel riepilogo).
   - Lingua (sei lingue, 4.1), voce on/off, formato (*3 set*, *5 set*, *2 set + super tie-break a 10*, *pro set* a 8 o 10 game, *solo super tie-break a 10*), game per set (6 oppure 4 nei set brevi, con tie-break sul 4-4), tie-break nei set sì/no (no: il set va avanti finché uno ha 2 game di scarto), No-Ad.
   - **Sorteggio**: la moneta gira e indica chi vince; imposti chi serve e i lati del campo **visti dal giudice di sedia** (schema del campo con **Inverti lati**). Nel doppio scegli anche chi serve per primo in ogni squadra.
3. **INIZIO PARTITA** lampeggia: premi il pulsante, KEY1 di un braccialetto o un telecomando. La voce dice *"Primo set" · "[nome] al servizio" · "gioco"* con 1 secondo tra le frasi; il **Match Time** parte su "gioco".
4. **Partita**: in alto a sinistra il tempo partita, a destra il countdown (**Shot Clock** 25 s, **Changeover Time**, **Set Break Time**; rosso negli ultimi 5 s). Il riquadro arancione si accende 5 s per *cambio campo, tie-break, set point, match point, palla break…*. I due tasti quadrati (giallo = Giocatore 1, rosso = Giocatore 2) stanno dal lato in cui si trovano davvero i giocatori e si scambiano a ogni cambio campo; sotto chi serve compare **On Serve**. Sotto: *Annulla punto*, *Sospendi/Riprendi*, audio (icona dell'altoparlante, barrata = spento), *Nuova partita*, **Esci**. In modalità braccialetti, sotto i tempi ci sono **G1**/**G2** con batteria e autonomia: toccandoli si aprono le impostazioni del braccialetto. Un doppio tocco su *Annulla punto* toglie un solo punto (il secondo tocco entro 1 s non conta); subito dopo un cambio di pagina il secondo tocco di un doppio tocco viene ignorato, così non finisce sul pulsante della pagina nuova. Il **lucchetto** in basso blocca lo schermo (per esempio col telefono in tasca o in panchina): i tocchi non fanno niente, braccialetti e telecomandi sì; si sblocca tenendo premuto il lucchetto.
5. All'ultimo punto compare il popup **Partita conclusa / Annulla ultimo punto**. "Partita conclusa" si conferma **solo dal telefono**. Per mezzo secondo dopo la comparsa i suoi pulsanti ignorano i tocchi: un doppio tocco sull'ultimo punto non annulla niente.
6. **Riepilogo**: vincitore, nomi, punteggio set per set con i punti del tie-break, durata, ora di inizio e fine, data, circolo, campo, luogo, formato, punti e game vinti. Pulsanti **Salva file** (nome file + cartella a scelta + formati .txt/.json/.png e **PDF completo** con risultato, statistiche della partita e di ogni set, cronologia e foto). **Condividi** chiede se mandare l'immagine col testo (per WhatsApp e i social) o il PDF completo. Nel Riassunto, **Foto della partita** tiene fino a 10 foto ricordo (dalla galleria o scattate): restano sul telefono, vanno nel PDF e passano all'altro telefono col trasferimento, mai nell'immagine per i social, **Condividi** (immagine 1080×1350 + testo per WhatsApp/Instagram/…), **Nuova partita**, **Esci**. Sotto le foto, **Note** accoglie un testo libero (fino a 2000 caratteri): si salva da solo, va nel PDF, nel testo condiviso e nei file .txt e .json, e passa all'altro telefono col trasferimento; non va nell'immagine per i social. La partita finita entra da sola nello **Storico** (pulsante sotto il titolo della pagina 1): elenco delle partite, da riaprire col loro riepilogo o eliminare, e scheda **Giocatori** con vittorie, sconfitte e scontri diretti di chi ha il nome scritto a pagina 1 (maiuscole e spazi non contano; nel doppio conta ogni giocatore). **Importa** aggiunge le partite salvate come .json con *Salva file* su un altro telefono: le finite vanno nello storico, quelle non finite tra le partite sospese da riprendere. **Trasferisci** (in alto a destra nello Storico) sposta storico e partite sospese su un altro telefono con un QR: i due telefoni devono stare sulla stessa rete Wi-Fi o uno sull'hotspot dell'altro; su quello che li ha tocchi *Invia* e lasci aperta la finestra col codice, sull'altro *Ricevi* e inquadri il codice (serve il permesso della fotocamera). Così si continua su un altro telefono anche una partita in corso: sospendila, trasferisci e riprendila. Le partite si aggiungono a quelle che il telefono ha già. **Foto dei giocatori**: tocca il cerchio accanto al nome a pagina 1 (o nel dettaglio del giocatore nello Storico) e scegli una foto; si ritaglia quadrata e si ritrova dal nome. Compare nello Storico, nel riepilogo e sul tabellone TV, resta solo su questo telefono (passa all'altro col trasferimento) e non finisce nell'immagine condivisa. Toccando di nuovo il cerchio la si cambia o la si toglie. Se l'app si chiude mentre sei sul riepilogo (Android che la chiude in secondo piano, un aggiornamento, un riavvio del telefono), riaprendola lo ritrovi; non se l'hai lasciato tu con *Nuova partita*, *Esci* o togliendo l'app dalle recenti. La cartella predefinita è **Documenti/Tennis Score Manager** (da Android 10; su Android 8 e 9 la cartella dell'app): si apre da qualsiasi app di gestione file e resta anche disinstallando l'app. Salvando due volte con lo stesso nome il secondo diventa *nome (1)*; nell'immagine i testi lunghi (nomi, indirizzo) si adattano alla larghezza.

   Il riepilogo ha tre schede. **Riassunto**: i dati qui sopra e, coi braccialetti 2.4, colpi, velocità del polso e colpi per punto (6.4). **Statistiche**, per la partita o per un set: i due giocatori a confronto con barre che partono dal centro (colorata quella di chi è avanti) per punti e game vinti al servizio e in risposta, palle break salvate e vinte, totale punti e game, punti di fila, ultimi 10 punti, match point annullati; coi braccialetti 2.4 anche colpi per tipo e velocità media del polso. **Cronologia**: set per set, ogni game con la pallina di chi serve, *SERVIZIO PERSO* sui break, il punteggio dopo ogni punto con **PB** (palla break), **SP** (set point) e **MP** (match point) e l'esito (*GAME*, *BREAK*, *SET*, *PARTITA*). Ace, doppi falli, prime di servizio, vincenti ed errori ci sono se a pagina 2 accendi le **statistiche dettagliate**: al posto dei due tasti grandi compaiono *Ace* e *Fallo* (la seconda volta diventa *Doppio fallo*) e, per ciascun giocatore, *Vincente*, *Errore* (il punto va all'altro) e *Punto*; *Annulla punto* toglie anche un fallo. È pensata per chi segna da bordo campo. Dopo *Errore* compare per 6 secondi *Gratuito / Forzato*; con braccialetti o telecomandi, dopo ogni punto compare *Com'è finito il punto?* (Ace, Vincente, Errore): rispondere è facoltativo, il punteggio non cambia. Nello Storico il dettaglio di un giocatore mostra l'**andamento**: punti vinti partita per partita e le medie di ace, doppi falli, prime, vincenti ed errori. Le statistiche di ogni partita contano anche palle game, set point e match point trasformati, game vinti a zero, game di fila, set vinti da sotto di un break e, nella scheda **Tempi**, la durata di ogni set e il game più lungo.

**Uscire**: **Esci** (dalla partita chiede conferma, e c'è anche nel riepilogo) chiude davvero l'app; lo stesso se la togli dalle app recenti. Alla riapertura riparte dalla prima pagina.

**Salvataggi**: la partita si salva da sola a ogni punto. *Sospendi* ferma i tempi; se esci, se il telefono si spegne o l'app viene chiusa, la partita si ritrova in **Riprendi partita sospesa** (pagina 3) e riparte con *Riprendi*. *Annulla punto* ricalcola tutto dall'inizio, quindi funziona anche dopo la fine di un game, di un set o della partita. Il cestino di una partita sospesa chiede conferma. Se la partita è iniziata da un braccialetto a schermo bloccato (lì Android non dà la posizione), il luogo si prende appena riapri l'app.

### 7.1 Allenamento con la fascia (app 2.5)

Per allenarsi con la fascia cardio senza una partita: pagina 1 › **Allenamento**. Si usa **una fascia**, quella del Giocatore 1, con età, peso e sesso per calorie e zone; si sceglie direttamente lì (con l'interruttore *Fasce cardio*, Bluetooth e permessi se mancano).

1. **Avvia**: in grande il battito dal vivo, sotto il tempo e il giro in corso.
2. **Giro** chiude un pezzo dell'allenamento e ne apre un altro (come il tasto giro di un cronometro): uno scatto, un esercizio, un giro di pista. **Pausa / Riprendi** ferma il tempo; le pause non contano.
3. **Fine**: l'app registra ancora **un minuto di recupero** (stai fermo) per misurare quanto scende il battito; **Salva subito** lo salta.

- **Riepilogo**: durata, battito medio e massimo, calorie, **HRV (RMSSD)** dagli intervalli tra un battito e l'altro mandati dalla fascia (servono almeno una trentina di battiti puliti; confrontala con i tuoi allenamenti simili, non con quelli degli altri), **recupero in 1 minuto** (più scende, meglio è), grafico del battito, zone cardio e, per ogni giro, durata, media, massimo e recupero.
- **CSV** (secondo per secondo, con il giro e il minuto di recupero) e **PDF** di una pagina con grafico, torta delle zone e giri, da condividere.
- Gli allenamenti restano nella stessa schermata, a parte dalle partite; si riaprono o si eliminano. Il battito arriva anche dal secondo telefono (6.6) e la registrazione continua a schermo spento.

### 7.2 Progressi (app 2.5)

Pagina 1 › **Progressi**: un documento per vedere se migliori, **separato** per allenamenti e partite.

- **Allenamenti**: durata, battito medio e massimo, calorie, HRV, recupero.
- **Partite** di un giocatore dello storico (scelto dall'elenco): partite vinte, punti vinti, ace, doppi falli, prime in campo, vincenti, errori gratuiti. Ace e gli altri valori dettagliati ci sono solo per le partite giocate con le statistiche dettagliate; sono totali per partita.
- Per ogni voce la media della **prima metà** del periodo e quella della **seconda**: ▲ verde se migliora, ▼ rossa se peggiora (meno doppi falli o errori è un miglioramento), = se resta uguale (meno del 5% di differenza) o se non si può dire (battito). Servono almeno 2 allenamenti o partite.
- **PDF** (con l'andamento di ogni voce e l'elenco completo, su più pagine se serve) da condividere, o **Stampa** con il servizio di stampa di Android (anche *Salva come PDF*).

### 7.3 Video: telefono-telecamera, highlights e moviola (app 2.5)

Un secondo telefono su un treppiede riprende la partita e mette sul video il punteggio dell'arbitro.

1. **Arbitro**: pagina 2 › **Secondo telefono** › **Usa un secondo telefono** (QR, come in 6.6).
2. **Telecamera**: pagina 2 › **Secondo telefono** › **Fai da telecamera**, inquadra il QR, poi **Apri la telecamera** (permessi di fotocamera e microfono; serve un telefono che registri in 1080p). Con *Punteggio collegato* la grafica del punteggio è sul video (**Grafica** la accende o la spegne); senza arbitro registra solo il video.
   - **Registra / Ferma**: il video va in galleria (*Film/Tennis Score Manager*) con i capitoli, uno per punto.
   - **Diretta**: trasmissione RTMP su YouTube, Twitch, Facebook o un server tuo; server e chiave dello stream in *Diretta: server*.

- **Video** (pagina 1): i video registrati, da rivedere punto per punto, game per game o set per set. **Capitoli YouTube** prepara i capitoli da incollare nella descrizione. **Importa video** prende un video fatto con un'altra fotocamera (il file resta dov'è): i punti si segnano guardandolo, toccando a fine punto chi l'ha vinto e, se vuoi, com'è finito (*Annulla ultimo* toglie l'ultimo); formato e regole sono quelli di pagina 2.
- **Filtri allenatore** (icona del filtro): solo i punti di un giocatore, vinti o persi, al servizio o in risposta, sulla prima o sulla seconda, per come sono finiti, sulle palle break o sui set e match point. **Riproduci** li mette in fila.
- **Highlights**: montaggio automatico dei punti più belli (ace, vincenti, palle break, set e match point, scambi lunghi coi braccialetti) nell'ordine di precedenza scelto con le frecce, per al massimo 1, 3, 5, 10 o 15 minuti o un'**Altra durata** fino a 90. Se non ci sta tutto prima si accorciano i pezzi, poi restano fuori i punti meno importanti. Il risultato va in galleria.
  - **Statistiche a fine video**: 2 o 3 schermate da 10 secondi come nelle dirette dei tornei: servizio e palle break; punti, errori e scambi corti e lunghi; battito, se c'era la fascia. La partita è quella dello storico giocata mentre si registrava, o quella dei punti segnati su un video importato. Ai lati la foto del giocatore; senza foto un ritratto stilizzato di uomo o di donna (scelta **Uomo / Donna** in pagina 1) o una sagoma neutra; sotto, la percentuale di punti vinti. In alto torneo, categoria e turno (scheda **Torneo** in pagina 1), oppure circolo e campo.
  - **Musica** sotto le statistiche, con dissolvenza in entrata e in uscita: *Nowhere Land* di Kevin MacLeod (incompetech.com, licenza CC BY 4.0: se pubblichi il video, citalo nei crediti), oppure un brano tuo con **Scegli un brano** (se ne usa l'inizio; usa solo musica che hai il diritto di mettere nei tuoi video).
- **Moviola**: sul telefono dell'arbitro, con la telecamera collegata, **Moviola** chiede alla telecamera il punto contestato (*Ultimo punto*, *Punto prima* o *Dall'ultimo punto segnato*) e lo mostra, con pausa e fotogramma per fotogramma. **Anche sul tabellone** lo fa vedere anche sul tabellone TV.

### 7.4 Racchette: racchetta virtuale (app 2.5)

Pagina 1 › **Racchette** › **Le mie racchette**: ogni racchetta fisica ha la sua scheda (due racchette dello stesso modello sono due schede: corde, peso e ore cambiano).

- **Scheda**: soprannome (A, B…), marca, modello, peso, bilanciamento, piatto corde e schema; si possono scrivere a mano o prendere **dal catalogo** (dati del produttore, da correggere se hai misurato la tua). *Messa da parte*: non si propone più, ma resta nei confronti.
- **Incordature**: tipo (monofilamento, multifilamento, sintetico, budello, ibrido), marca e modello (anche dalle corde comuni), calibro, tensione delle verticali e delle orizzontali, data e note. *Nuova incordatura* parte da quella montata. Per ognuna l'app somma le **ore di gioco** delle partite e degli allenamenti associati e avvisa quando si supera la **soglia di controllo** scelta (20 ore di serie): dal solo tempo non può sapere quanta tensione è rimasta.
- **Prima di giocare**: in pagina 1, sotto la classifica di ogni giocatore, si sceglie la sua racchetta; nell'allenamento la si sceglie sopra la fascia. La partita (punti, colpi dei braccialetti, battito) e l'allenamento si salvano con una **copia** della racchetta com'era quel giorno, corde e tensione comprese: reincordarla dopo non cambia le sessioni passate.
- **Nel riepilogo** della partita (scheda *Riassunto*) e dell'allenamento si vede la racchetta usata, la si può associare anche dopo (si prende l'incordatura montata quel giorno) e si danno le **sensazioni** da 1 a 5: controllo, comfort, potenza e fatica.
- **Confronto**: *per racchetta* o *per incordatura* (stessa racchetta con tensioni o corde diverse), si scelgono le configurazioni A e B. Barre affiancate per sessioni, ore, partite vinte, punti vinti, prime in campo, punti vinti con la prima e con la seconda, ace, doppi falli, vincenti ed errori gratuiti a partita, velocità media del polso e colpi dei braccialetti, battito medio, e le sensazioni medie. Il valore migliore è in grassetto, ma l'app non sceglie una vincitrice: contano anche avversario, superficie, condizioni e forma.

## 8. Tabellone su TV o monitor

Il telefono dell'arbitro fa da **piccolo server** sulla rete Wi-Fi: il tabellone è una pagina web in stile LED (cifre a 7 segmenti, giallo contro rosso, game e set al centro, set conclusi e tempo partita in basso a sinistra, **SERVIZIO: 25 SEC** in basso a destra) che si aggiorna da sola a ogni punto. Il monitor non deve essere "smart" e non serve una rete del circolo: basta l'**hotspot** di uno dei due telefoni.

### 8.1 Le strade possibili

| Come arriva al monitor | Cosa serve | Pro | Contro |
|---|---|---|---|
| **Secondo telefono con uscita video** + cavo USB-C/HDMI, app TSM in *Usa come tabellone* | un telefono che esce in video dalla USB-C (DisplayPort Alt Mode) | niente internet; il tabellone occupa tutto il monitor in 16:9 e il telefono resta libero | molti telefoni **non** escono in video: in genere sì i Galaxy S/Note/Tab S (con DeX: scegli *Duplica schermo* o disattiva l'avvio automatico di DeX), no quasi tutti i Galaxy A. Controlla "DisplayPort" / "uscita video" nella scheda tecnica |
| **Chromecast** (o Google TV Streamer) sul monitor + un telefono qualsiasi con TSM in *Usa come tabellone* e **Trasmetti** (*Trasmissione schermo* fino ad Android 14, Smart View sui Samsung) | Chromecast configurato una volta con Google Home sulla rete dell'hotspot | qualsiasi telefono va bene, nessun cavo lungo | il Chromecast vuole **internet** (dati mobili sull'hotspot); ritardo di circa 1 s; la trasmissione mostra lo schermo del telefono (in orizzontale) |
| **Browser** su qualsiasi apparecchio collegato al monitor (portatile, tablet, TV box, Fire TV Stick…) | inquadrare il QR o scrivere l'indirizzo | nessuna app da installare | lo schermo si spegne da solo se non lo imposti; si tocca **SCHERMO INTERO** a ogni apertura |

**Perché non via Bluetooth**: il telefono dell'arbitro regge già i due braccialetti in Bluetooth, dove contano i tempi dei tasti; il Wi-Fi è separato, più veloce e arriva più lontano. **Perché non dal solo telefono dell'arbitro al Chromecast** (senza secondo telefono): si può fare, ma serve una app "ricevitore" registrata presso Google (Google Cast Developer Console, 5 $ una tantum) e pubblicata su un sito https; è un passo successivo possibile.

**Consigli di rete**:
- Il tabellone manda pochissimi dati (un messaggio a ogni punto e ogni 5 secondi), non consuma traffico internet.
- Se il telefono dell'arbitro fa l'hotspot, nelle impostazioni dell'hotspot scegli la banda **5 GHz** se c'è: il Bluetooth dei braccialetti lavora a 2,4 GHz e così non si disturbano.
- Con il Chromecast conviene che l'hotspot lo faccia il telefono dell'arbitro con i **dati mobili accesi**; telefono-tabellone e Chromecast si collegano a quell'hotspot.
- Senza Chromecast va bene anche il contrario (hotspot sul telefono-tabellone, come nell'idea originale): l'app dell'arbitro tiene agganciato il Wi-Fi anche se non ha internet.

### 8.2 Sul telefono dell'arbitro

1. Pagina 2 › **Tabellone su TV** › attiva **Tabellone su TV o monitor**. Su Android 13 e successivi l'app chiede il permesso delle notifiche: serve per la notifica del servizio che tiene acceso il tabellone.
2. Compaiono l'**indirizzo** (es. `192.168.43.1:8080`), il **QR** e quanti tabelloni sono collegati. Se c'è scritto *Nessuna rete*, accendi l'hotspot o collegati a quello dell'altro telefono.
3. **Anteprima su questo telefono** apre il tabellone nel browser del telefono stesso.
4. **Aspetto del tabellone**: colore di ciascun giocatore (8 colori), tempo partita, cronometro servizio e pause, set conclusi, messaggi (palla break, set point, cambio campo…), pallina di chi serve, segmenti spenti visibili, scritta in basso (vuota = circolo e campo della pagina 1). Le modifiche arrivano subito sul monitor.
5. In partita, in alto al centro c'è **TV · 1** (tabelloni collegati): toccandolo si rivedono indirizzo e QR.

Col tabellone acceso un servizio in primo piano (notifica *Partita in corso · tabellone TV attivo*) tiene vivo il server anche a schermo spento, pure in modalità arbitro. Il tabellone è **solo lettura**: da lì non si può cambiare niente. Resta attivo anche sul riepilogo e sulle altre pagine finché il tabellone è acceso (notifica *Tabellone TV attivo*), così il risultato finale resta sul monitor anche con il telefono bloccato.

Cosa mostra, oltre al punteggio: *IN ATTESA DELLA PARTITA* prima di iniziare, *IN ATTESA DEL VIA* sulla pagina INIZIO PARTITA, **TIE-BREAK** / **MATCH TIE-BREAK** al posto di *VS*, *PARTITA SOSPESA* lampeggiante, **VINCE [nome]** a fine partita con tutti i set; i vantaggi si leggono **AD** anche sulle cifre a LED. Una partita vinta al super tie-break finisce con 1-0 nei game e 2-1 nei set, con [10-8] tra i set conclusi; i messaggi lunghi si rimpiccioliscono per stare nello schermo.

### 8.3 Sul telefono-tabellone

1. Pagina 1 › in fondo **Usa come tabellone**.
2. Il telefono **cerca da solo** il telefono dell'arbitro (annuncio sulla rete e scansione dell'hotspot, pochi secondi) e si ricorda l'ultimo indirizzo. Se non lo trova: controlla hotspot e *Tabellone su TV*, poi **Cerca di nuovo**, oppure scrivi l'indirizzo mostrato dall'arbitro e **Collega**. Lo trova anche se su questo telefono è acceso *Tabellone su TV o monitor*; se la rete Wi-Fi arriva dopo, cerca di nuovo da solo.
3. Il tabellone va a schermo intero, in orizzontale, con lo schermo sempre acceso.
   - **Con il cavo HDMI**: il tabellone va sul monitor nel suo formato; il telefono mostra *Il tabellone è sul monitor esterno* con la luminosità al minimo (**Mostra anche qui** per vederlo anche sul telefono). Staccando il cavo torna sul telefono.
   - **Con il Chromecast**: apri la tendina › **Trasmetti** (o **Trasmissione schermo**) / **Smart View** › scegli il Chromecast.
4. Se il telefono dell'arbitro sparisce (fuori portata, app chiusa) compare *CONNESSIONE PERSA - RICONNESSIONE...* e dopo 20 secondi lo ricerca da solo, anche se ha cambiato indirizzo. Anche dopo un'interruzione dell'hotspot si ricollega da solo appena torna la rete. Non passa mai al telefono di un altro campo: per cambiare campo esci da *Usa come tabellone* e cerca di nuovo.
5. Per uscire: **indietro due volte**.

**Da un browser** (portatile, TV box): inquadra il QR o scrivi l'indirizzo, poi tocca **SCHERMO INTERO** (compare muovendo il mouse o toccando lo schermo). Imposta lo spegnimento dello schermo su *mai*: in una pagina http il browser non può tenerlo acceso da solo. Dopo un'interruzione la pagina si ricollega da sola; troppi tabelloni aperti non bloccano più quello nuovo (si chiude il più vecchio).

**Anteprima senza telefoni**: `TennisScoreManager/app/src/main/assets/scoreboard.html?demo=1` in un browser (anche `&lang=en`, `fr`, `de`, `es`, `pt` e `&state=ad`, `tb`, `end`, `idle`, `doubles`, `mtb`, `long`) mostra il tabellone con dati di prova.

## 9. Regole applicate (ITF) e scelte concordate

- **Game**: 0-15-30-40, parità, vantaggio, gioco. **No-Ad**: sul 40-40 punto decisivo ("parità, punto decisivo").
- **Set**: 6 game con 2 di scarto (7-5); sul **6-6 tie-break**.
- **Tie-break**: a 7 con 2 di scarto; chi è di turno serve il 1° punto, poi 2 punti a testa; cambio campo **ogni 6 punti** e alla fine; chi ha servito per primo nel tie-break **riceve** nel primo game del set successivo.
- **Match tie-break** (formato 2 set): sull'1-1 si gioca a 10 punti con 2 di scarto.
- **Cambio campo** (regola ITF 10): dopo il 1°, 3°, 5°… game di ogni set. A fine set si cambia solo se il set ha un numero dispari di game (6-3, 7-6); altrimenti (6-4) si cambia dopo il primo game del set successivo. Il tie-break conta come un game.
- **Tempi**: shot clock 25 s tra i punti; changeover 90 s; set break 120 s a fine set. In più, come richiesto (non è regola ITF): **30 s** per spostarsi dopo il 1° game di ogni set, a ogni cambio campo nel tie-break e sul 6-6; finita qualsiasi pausa parte lo shot clock.
- **Doppio**: rotazione del servizio A1-B1-A2-B2 per tutto il set, anche nel tie-break; all'inizio di ogni set l'app chiede l'ordine (si può cambiare, come da regolamento).
- **Chiamate**: punteggio letto da chi serve ("quindici zero", "zero quaranta", "quindici pari", "parità", "vantaggio Rossi"); a fine game "gioco Rossi, Rossi conduce tre giochi a due" / "due giochi pari" + "cambio campo" quando si cambia; sul 6-6 "gioco Rossi, sei giochi pari, tie-break"; nel tie-break si legge da chi conduce ("tre a uno Rossi", "sei pari"); fine set "gioco Rossi, Rossi conduce un set a zero" / "un set pari"; fine partita "gioco, set, partita Rossi, sei quattro, tre sei, sette cinque"; "correzione" + punteggio quando si annulla un punto.

**Scelte fatte rispetto alla richiesta originale, per seguire l'ITF:**
1. **Sul 6-6 non si cambia campo** (sono 12 game, numero pari): l'app fa la pausa di 30 s e dice "tie-break", ma non dice "cambio campo" e non scambia i tasti. Il primo cambio è dopo 6 punti del tie-break.
2. Il cambio a fine set dipende dal numero di game del set (vedi sopra), non avviene sempre.
3. A fine set vinto senza tie-break l'app usa la stessa formula del tie-break ("gioco Rossi, Rossi conduce un set a zero"). Molti arbitri dicono "gioco e set Rossi, sei quattro": si può cambiare facilmente in `Calls.kt`.
4. All'1-1 nel formato con super tie-break la voce aggiunge "super tie-break".

## 10. Dove mettere le mani

| Cosa | File |
|---|---|
| Regole del punteggio | `model/ScoreEngine.kt` (+ test in `app/src/test`) |
| Frasi e chiamate vocali | `voice/Calls.kt` (costruzione), `voice/CallWords.kt` (parole e ordine di ogni lingua) |
| Correzioni di pronuncia | `voice/Pronunciation.kt` |
| Tempi (25/90/120/30 s), messaggi, flusso partita | `MatchController.kt` |
| Testi dell'app | `ui/Strings.kt` (italiano, inglese), `ui/StringsFr.kt`, `StringsDe.kt`, `StringsEs.kt`, `StringsPt.kt` |
| Aggiungere una lingua | voce in `model/Rules.kt` (`Lang`), un `ui/StringsXx.kt`, un `XxWords` in `voice/CallWords.kt`, una riga in `TXT[]` di `TSM_Band.ino`: i test in `LanguagesTest` e `StringsTest` dicono cosa manca |
| Protocollo Bluetooth (UUID, messaggi, impostazioni) | `ble/BandProtocol.kt` e in cima a `TSM_Band.ino` |
| Stima dell'autonomia | `ble/BatteryModel.kt` |
| Pannello impostazioni braccialetto | `ui/BandSettingsPanel.kt` |
| Grafica | `ui/screens/*.kt`, colori in `ui/Theme.kt` |
| Tabellone TV: pagina e aspetto | `app/src/main/assets/scoreboard.html` |
| Tabellone TV: dati inviati, server, impostazioni | `tv/TvModels.kt`, `tv/TvServer.kt`, `ui/TvSection.kt` |
| Telefono usato come tabellone (ricerca, monitor esterno) | `tv/DisplayActivity.kt`, `tv/ScoreboardFinder.kt` |
| Schermata di ricarica del braccialetto | `TSM_Band.ino` (`pollPower`, `drawCharge`) |
| Telecomandi Bluetooth (gesti, associazione) | `remote/PressDetector.kt` (+ test), `remote/RemoteInput.kt` |
| Statistiche e cronologia del riepilogo | `data/MatchStats.kt` (+ test), `ui/screens/MatchStatsPanels.kt` |
| Storico, giocatori e importazione | `data/PlayerStats.kt` (+ test), `ui/screens/HistoryScreen.kt`, `MatchController.kt` (`importMatches`) |
| Trasferimento tra telefoni (QR) | `transfer/Transfer.kt` (server e `TransferBundle`), `MatchController.kt` (`startSending`, `receiveFrom`), `ui/screens/HistoryScreen.kt` (`SendDialog`) |
| Foto dei giocatori | `ui/PlayerPhoto.kt`, `data/Storage.kt` (`savePhoto`), `MatchController.kt` (`setPhoto`), `/photo/` in `tv/TvServer.kt` e `scoreboard.html` |
| Tornei Americano e Mexicano | `data/Tournament.kt` (+ test), `ui/screens/TournamentScreen.kt` |
| Riconoscimento dei colpi | `TSM_Band.ino` (sezione "colpi"), `swing_detector.*` e `stroke_classifier.*` (copiati da SwingSense); app: `ble/BandProtocol.kt`, `ui/BandSettingsPanel.kt`, `data/Reports.kt` |
| Fasce cardio (Bluetooth, battito, calorie, zone, stacchi) | `ble/HeartRateManager.kt`, `ble/HeartRateProtocol.kt`, `ble/StrapGaps.kt`, `data/HeartStats.kt` (+ test), `ui/HeartSection.kt` |
| Secondo telefono (protocollo, rete, ponte per fasce e braccialetti) | `relay/RelayProtocol.kt`, `relay/RelayLink.kt` (+ test), `relay/Hotspot.kt`, `relay/PhoneRelay.kt`, `ui/RelaySection.kt`; in `ble/BleManager.kt` `setRemote`, `relayOut`, `inject*` |
| Allenamento (registrazione, giri, HRV, recupero, CSV e PDF) | `workout/WorkoutSession.kt`, `data/Workout.kt` (+ test), `data/WorkoutReport.kt`, `ui/screens/WorkoutScreen.kt` |
| Progressi (confronto prima/seconda metà, PDF, stampa) | `data/Progress.kt` (+ test), `data/ProgressPdf.kt`, `ui/screens/ProgressScreen.kt` |


## 11. Problemi comuni

- **Braccialetto non trovato**: Bluetooth e posizione attivi (righe verdi)? Il braccialetto sta lampeggiando PAIRING? (Se è già collegato a un altro telefono non si vede.) Se nel frattempo si è spento da solo (30 s), riaccendilo con un clic sul tasto laterale.
- **Il braccialetto mostra NON INVIATO**: il telefono non ha confermato il tocco entro 8 secondi (collegamento perso o app chiusa). Premi di nuovo quando il braccialetto è ricollegato. Caso raro: se il tocco era arrivato e si è persa solo la conferma, il punto conta due volte; la voce dice il punteggio, si corregge con *Annulla punto*.
- **Nel pannello impostazioni c'è "Il firmware di questo braccialetto non ha le impostazioni"**: quel braccialetto ha ancora lo sketch vecchio, ricaricalo (capitolo 5).
- **Android Studio non vede il telefono**: Debug USB attivo e impronta RSA accettata sul telefono (3)? Su Windows serve a volte il driver del produttore, su Linux le regole udev e una nuova sessione (1.1). Oppure il debug wireless (3).
- **Arduino IDE non mostra la porta del braccialetto**: cavo USB-C dati e non solo di ricarica; su Linux gruppo `dialout` e nuova sessione (1.1); poi la modalità download (5, punto 7).
- **La voce non parla o legge male**: volume multimediale, *Audio On*, voce della lingua scelta installata (4); prova un altro motore o un'altra voce in *Audio e voce*.
- **Schermo spento durante la partita**: in modalità braccialetti un servizio in primo piano (notifica "Partita in corso") tiene attivi Bluetooth, voce e cronometri; in modalità arbitro e telecomandi lo schermo resta acceso (con i telecomandi deve restarlo: 6.3).
- **Il telecomando non fa niente**: è associato al giocatore (pagina 2, riga col suo nome)? L'app deve essere aperta sulla partita, a schermo acceso (6.3). Se la riga dice *Non collegato*, premi un tasto per svegliarlo oppure riaccoppialo dalle impostazioni Bluetooth.
- **I colpi vengono confusi o risultano non riconosciuti**: il braccialetto va portato come durante la calibrazione (stesso polso, stesso verso); altrimenti rifai la calibrazione indossandolo come in partita (6.4).
- **Il braccialetto mostra MEMORIA PIENA**: un esempio di calibrazione non si è potuto salvare. Cancella gli esempi di un colpo (cestino) e rifalli.
- **Capire perché due colpi si confondono**: col braccialetto collegato al computer apri il monitor seriale (115200 baud) e scrivi `d`: compaiono gli esempi salvati e gli ultimi 40 colpi, ciascuno con le sue 11 misure. Gli ultimi colpi restano finché il braccialetto non si spegne o si riavvia.
- **Sync Gradle fallita per il JDK**: imposta Gradle JDK = jbr-21 (punto 2.6).
- **Il telefono-tabellone non trova l'arbitro**: stessa rete? (uno dei due fa l'hotspot, l'altro è collegato). *Tabellone su TV* acceso sull'arbitro? Prova l'indirizzo a mano. Alcuni hotspot isolano i dispositivi collegati tra loro ("isolamento client"): se c'è, disattivalo.
- **Il browser del telefono non apre l'indirizzo** con i dati mobili accesi: Android manda il traffico sui dati perché l'hotspot non ha internet. Usa l'app in *Usa come tabellone* (lo gestisce da sola) oppure spegni i dati mobili su quel telefono.
- **Monitor nero col cavo**: quel telefono non ha l'uscita video sulla USB-C (8.1), oppure è partito Samsung DeX: scegli *Duplica schermo*.
