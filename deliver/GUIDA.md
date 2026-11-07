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

Versioni usate e verificate: app **2.5.0** · firmware **2.3** · Gradle 8.14.3 · Android Gradle Plugin 8.13.2 · Kotlin 2.2.21 · Compose BOM 2025.12.00 · compileSdk/targetSdk 36 · minSdk 26 (Android 8.0). Firmware: M5Unified ≥ 0.2.12, NimBLE-Arduino ≥ 2.1, core ESP32 3.x. Licenza: GNU GPL versione 3 o successiva (`LICENSE`); versione e licenza sono anche in fondo alla scheda **Altro** dell'app.

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

### 3.1 Come è organizzata l'app (app 2.5)

Alla **prima accensione** l'app fa tre domande: la lingua, come la userai (segnare il punteggio, giocare coi braccialetti, allenarti, riprendere le partite) e il tuo nome; si può saltare e rifare da **Altro › Presentazione dell'app**. In basso ci sono sempre **cinque schede**:

- **Gioca**: **Nuova partita**, la partita sospesa da riprendere, **Come l'ultima volta** (stessi giocatori, campo e regole: le regole stanno in una riga e si va dritti al sorteggio; *Cambia modalità o regole* le riapre), il **torneo tra amici** (Americano, Mexicano) e **Usa questo telefono come** tabellone o telecamera. In alto lo stato dei dispositivi (braccialetti, fasce, telecamera, TV): toccandolo si va a collegarli.
- **Allenati**: allenamento con la fascia cardio e **allenamento dei colpi** (servizio, dritto, rovescio, volée) con le sessioni salvate.
- **Video**: **Riprendi con questo telefono**, **I tuoi video** (capitoli, highlights, filtri, importa) e il telefono-telecamera.
- **Progressi**: **Rating TSM · Progressi** (col tuo nome mostra subito il tuo rating), **Storico**, **Le mie racchette**.
- **Altro**: **Tu** (il tuo nome), **Giocatori** (il database: 7.7), **Profilo e dati** (backup, ripristino, cancellazione: 7.8), **Dispositivi** (braccialetti, telecomandi, fasce, secondo telefono, tabellone TV), **Lingua e voce**, **Aiuto** (questa guida, con la ricerca e i rimandi tra le sezioni da toccare), racchette, trasferimento delle partite, presentazione, versione e licenza.

**Nuova partita** sono due passi: **chi gioca** (singolo o doppio, i giocatori scelti dal database, con i recenti da toccare, e lo sport; circolo, campo e torneo in *Altri dettagli*) e **come segni il punteggio e le regole**. Tutto è facoltativo: si può andare avanti anche senza nomi. Da ogni schermata **Indietro** torna alla scheda da cui si era partiti.

## 4. La voce (funziona senza internet)

- Ogni chiamata è letta **in un'unica frase** dalla sintesi vocale del telefono, con una voce installata: niente internet e prosodia naturale. I nomi dei giocatori entrano nella frase.
- Altro › **Lingua e voce** › **Audio e voce**:
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

Altro › **Lingua e voce** › **Lingua**: Italiano, English, Français, Deutsch, Español, Português (ogni lingua è scritta nella lingua stessa, così la si ritrova anche con l'app in una lingua che non si legge). La scelta vale insieme per **schermate, voce dell'arbitro, tabellone TV, riepilogo/condivisione e braccialetti**. Le date seguono la lingua (*mercredi 30 septembre 2026*, *Mittwoch, 30. September 2026*…).

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

- All'accensione lampeggia **PAIRING...** (acceso 0,35 s ogni 2 s per risparmiare). L'app si collega da sola ai braccialetti che conosce appena è aperta; quelli nuovi li trova in **Altro › Dispositivi** (o in **Modalità e regole** con la modalità *Braccialetti*).
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

Da **Altro › Dispositivi** o da **Modalità e regole** (**Impostazioni** sotto il braccialetto di ciascun giocatore) o durante la partita (tocca **G1**/**G2** in alto, oppure l'icona dei cursori). Si salvano **nel braccialetto** e restano anche spegnendolo; a ogni modifica il braccialetto mostra il nome con *IMPOSTAZIONI OK*. Col firmware 2.4 c'è anche la sezione **Colpi** (6.4).

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
- **Nell'app**: la riga del braccialetto (in *Dispositivi* e in *Modalità e regole*) e le sue impostazioni mostrano *In carica 78%* o *Carica completa*; in partita le pastiglie G1/G2 hanno il simbolo ⚡. Anche il registro `files/battery_log.csv` ha due colonne in più (tensione USB, carica completa): utile per verificare in quanto si ricarica davvero.
- Braccialetto e app ora calcolano la percentuale con la **stessa curva** della LiPo (prima il braccialetto usava una retta meno precisa), quindi mostrano lo stesso numero.

> Da verificare coi braccialetti veri: la ricarica non si è potuta provare sull'hardware. In particolare quanto spesso il caricabatterie segnala *carica completa* e quanto è precisa la percentuale in carica.

### 6.3 Senza braccialetti: telecomandi Bluetooth (app 2.5)

Il sistema più economico per far segnare i punti ai giocatori: due **telecomandini Bluetooth da selfie** (quelli col tasto di scatto, spesso venduti come *AB Shutter3*), da 1 a 3 € l'uno. Android li vede come una tastiera: niente firmware da caricare e nessun permesso per l'app. Va bene anche qualsiasi altra pulsantiera o tastiera Bluetooth (o USB).

1. Accendi il telecomando e accoppialo dalle **impostazioni Bluetooth** del telefono, come una cuffia (*Altro › Dispositivi › Impostazioni Bluetooth* le apre). Ripeti col secondo.
2. *Modalità e regole* › modalità **Telecomandi** › **Associa** sotto il Giocatore 1 › premi il tasto del suo telecomando che darà il punto; poi lo stesso per il Giocatore 2. A ogni clic di un telecomando associato la sua riga mostra **Tasto ricevuto**: così si prova prima di giocare. **Scambia G1 ↔ G2** li inverte.
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
2. *Modalità e regole* (o *Altro › Dispositivi*) › **Battito cardiaco** › attiva **Fasce cardio** e scegli la fascia di ciascun giocatore (si sceglie una volta sola). **Scambia** le inverte.
3. Facoltativi **età, peso e sesso** di chi la indossa: servono per le calorie (formula di Keytel, errore tipico ±20%) e, con l'età, per le zone cardio (massimo stimato 208 − 0,7 × età).

- Sotto ogni fascia: stato, battito, **segnale** in dBm (più vicino a 0 = più forte) e **stacchi**, cioè le volte in cui il battito non è arrivato per più di 3 secondi, con i secondi persi (*2 stacchi (14 s)*). Contano dal «gioco» della partita; prima, da quando la fascia è stata scelta. Servono a capire fin dove arriva la fascia.
- In partita una riga mostra il battito dal vivo (*G1 · 142 bpm*). Nel riepilogo (7): battito medio e massimo, calorie, grafico nel tempo e tempo nelle zone cardio. Le pause non contano.
- **Portata**: prova in casa con la H6, telefono fermo: collegata fino a 21 m, ma a −100 dBm, al limite; di schiena il segnale cala di 5-10 dBm. In singolare il giocatore lontano sta a 24-30 m dal telefono: per non perdere dati usa il **secondo telefono** (6.6).

### 6.6 Secondo telefono a fondo campo (app 2.5)

Fasce e braccialetti accettano **un telefono alla volta** e arrivano a pochi metri meno della lunghezza del campo. Con un secondo telefono all'altro fondo campo ogni giocatore ne ha sempre uno vicino: il secondo (**satellite**) sente fasce e braccialetti dei giocatori dalla sua parte e fa da ponte con il **principale**, che tiene la partita.

1. **Principale**: Altro › **Dispositivi** › **Secondo telefono** › **Usa un secondo telefono**. La prima volta chiede il permesso *Dispositivi nelle vicinanze* (fino ad Android 12 la posizione). Il telefono crea una **rete sua, solo locale**: niente SIM né dati, e non serve il Wi-Fi del circolo. Compare un QR.
2. **Satellite**: Altro › **Dispositivi** › **Secondo telefono** › **Fai da secondo telefono** › inquadra il QR. Android chiede una volta di collegarsi alla rete del principale (*AndroidShare_…*): tocca **Connetti**. Il satellite tiene i dati mobili. Deve dire *Collegato al telefono principale*.
3. Il satellite segue da solo le fasce e i braccialetti scelti sul principale, sugli stessi lati: non si sceglie niente. Lascialo a fondo campo con l'app aperta.

- **Cambio campo**: non serve fare niente. Ogni fascia e ogni braccialetto resta al telefono a cui è collegato finché il segnale regge, poi lo prende quello più vicino (qualche secondo). Sul principale compare *Dal secondo telefono*; durante il passaggio un braccialetto può risultare scollegato per un attimo.
- **Niente dati persi**: battito, tasti e colpi del satellite portano l'**ID della partita** e un numero progressivo, e il satellite li tiene finché il principale non li conferma. Se il Wi-Fi tra i telefoni cade, il battito arriva dopo e riempie i buchi del grafico; i colpi contano se sono della stessa partita. Un **tasto** del braccialetto arrivato più di 5 secondi dopo invece non dà il punto: premi di nuovo. Lo stesso tasto arrivato da due strade conta una volta sola.
- Dal principale ai braccialetti passano punteggio, messaggi, *Identifica*, spegnimento e impostazioni. Le impostazioni conviene cambiarle col braccialetto collegato al principale.
- **Tabellone**: sul satellite **Mostra il tabellone** apre il tabellone della partita a schermo intero (come in 8.3), già collegato al principale.
- Se il principale non riesce a creare la rete (hotspot normale già acceso), usa il Wi-Fi in cui si trova: in quel caso i due telefoni devono stare sulla stessa rete. Con Android 9 o prima il satellite mostra nome e password della rete, da scegliere a mano nelle impostazioni Wi-Fi.
- **Smetti** chiude il collegamento e la rete; il satellite torna alle sue fasce e ai suoi braccialetti.

## 7. Come si usa l'app

1. **Nuova partita** (facoltativa): **Tennis** o **Padel** (app 2.5: stesse regole, tempi e chiamate; scegliendo Padel si impostano da soli doppio e golden point (punto d'oro), che si possono cambiare (al posto del golden point, in *Modalità e regole*, c'è anche lo *star point* della FIP: al massimo due vantaggi, poi un punto decisivo), e il riepilogo dice *Padel*), circolo, campo, singolare/doppio, nomi (nel doppio due nomi per squadra). **Cerca l'indirizzo del campo** (spento di serie) aggiunge al riepilogo l'indirizzo oltre alle coordinate: lo chiede al servizio di geocodifica del telefono, che con i servizi Google manda le coordinate a Google. **Avanti**. Con *Padel* compare anche **Torneo Americano o Mexicano**: da 4 a 16 giocatori e da 1 a 4 campi, partite a punti (16, 24 o 32: i punti delle due coppie sommati fanno il totale) o a tempo. Nell'*Americano* le coppie cambiano a ogni turno perché ognuno giochi con e contro più persone possibile; nel *Mexicano*, dal secondo turno, si gioca per classifica (1° e 4° contro 2° e 3° nello stesso campo). A ogni turno si scrive il risultato di ogni campo (nelle partite a punti basta un numero, l'altro si completa) oppure lo si conta con *Conta punto per punto*, con la voce e, a tempo, il cronometro; con più giocatori dei posti in campo si riposa a turno. **Turno successivo** parte quando tutti i risultati ci sono; la **Classifica** conta i punti fatti da ciascuno, poi le vittorie e la differenza punti, e si condivide come testo.
2. **Modalità e regole**:
   - *Arbitro*, *Braccialetti* o *Telecomandi* (6.3). Con i braccialetti sono obbligatori Bluetooth acceso, permesso posizione e posizione attiva: le righe dei **Requisiti** si aggiornano in tempo reale (anche se spegni Bluetooth o posizione dalla tendina) e diventano rosse con il pulsante per sistemarle.
   - La **ricerca è automatica e continua** finché la pagina è aperta: accendi i braccialetti e vanno da soli nei posti liberi (prima Giocatore 1, poi Giocatore 2). **Identifica** fa lampeggiare quel braccialetto nel colore del giocatore (giallo o rosso) con dei bip, così vedi subito quale hai in mano; **Scambia G1 ↔ G2** li inverte senza scollegarli. Dal menu a tendina puoi sempre sceglierne un altro o *Nessuno* (quello tolto a mano non viene rimesso dalla ricerca). Un braccialetto non può stare su due giocatori.
   - **Spegni i braccialetti a fine partita e all'uscita** (attivo di serie): alla conferma di *Partita conclusa* e con *Esci* i braccialetti si spengono invece di aspettare l'inattività.
   - In modalità arbitro, premendo **Avanti** senza posizione compare l'invito ad attivarla (altrimenti il luogo non sarà nel riepilogo).
   - Formato (*3 set*, *5 set*, *2 set + super tie-break a 10*, *pro set* a 8 o 10 game, *solo super tie-break a 10*), game per set (6 oppure 4 nei set brevi, con tie-break sul 4-4), tie-break nei set sì/no (no: il set va avanti finché uno ha 2 game di scarto), No-Ad. Lingua e voce sono in *Altro › Lingua e voce* (4.1).
   - **Sorteggio**: la moneta gira e indica chi vince; imposti chi serve e i lati del campo **visti dal giudice di sedia** (schema del campo con **Inverti lati**). Nel doppio scegli anche chi serve per primo in ogni squadra.
3. **INIZIO PARTITA** lampeggia: premi il pulsante, KEY1 di un braccialetto o un telecomando. La voce dice *"Primo set" · "[nome] al servizio" · "gioco"* con 1 secondo tra le frasi; il **Match Time** parte su "gioco".
4. **Partita**: in alto a sinistra il tempo partita, a destra il countdown (**Shot Clock** 25 s, **Changeover Time**, **Set Break Time**; rosso negli ultimi 5 s). Il riquadro arancione si accende 5 s per *cambio campo, tie-break, set point, match point, palla break…*. I due tasti quadrati (giallo = Giocatore 1, rosso = Giocatore 2) stanno dal lato in cui si trovano davvero i giocatori e si scambiano a ogni cambio campo; sotto chi serve compare **On Serve**. Sotto: *Annulla punto*, *Sospendi/Riprendi*, audio (icona dell'altoparlante, barrata = spento), *Nuova partita*, **Esci**. In modalità braccialetti, sotto i tempi ci sono **G1**/**G2** con batteria e autonomia: toccandoli si aprono le impostazioni del braccialetto. Un doppio tocco su *Annulla punto* toglie un solo punto (il secondo tocco entro 1 s non conta); subito dopo un cambio di pagina il secondo tocco di un doppio tocco viene ignorato, così non finisce sul pulsante della pagina nuova. Il **lucchetto** in basso blocca lo schermo (per esempio col telefono in tasca o in panchina): i tocchi non fanno niente, braccialetti e telecomandi sì; si sblocca tenendo premuto il lucchetto.
5. All'ultimo punto compare il popup **Partita conclusa / Annulla ultimo punto**. "Partita conclusa" si conferma **solo dal telefono**. Per mezzo secondo dopo la comparsa i suoi pulsanti ignorano i tocchi: un doppio tocco sull'ultimo punto non annulla niente.
6. **Riepilogo**: vincitore, nomi, punteggio set per set con i punti del tie-break, durata, ora di inizio e fine, data, circolo, campo, luogo, formato, punti e game vinti. Pulsanti **Salva file** (nome file + cartella a scelta + formati .txt/.json/.png e **PDF completo** con risultato, statistiche della partita e di ogni set, cronologia e foto). **Condividi** chiede se mandare l'immagine col testo (per WhatsApp e i social) o il PDF completo. Nel Riassunto, **Foto della partita** tiene fino a 10 foto ricordo (dalla galleria o scattate): restano sul telefono, vanno nel PDF e passano all'altro telefono col trasferimento, mai nell'immagine per i social, **Condividi** (immagine 1080×1350 + testo per WhatsApp/Instagram/…), **Nuova partita**, **Esci**. Sotto le foto, **Note** accoglie un testo libero (fino a 2000 caratteri): si salva da solo, va nel PDF, nel testo condiviso e nei file .txt e .json, e passa all'altro telefono col trasferimento; non va nell'immagine per i social. La partita finita entra da sola nello **Storico** (scheda **Progressi**): elenco delle partite, da riaprire col loro riepilogo o eliminare, e scheda **Giocatori** con vittorie, sconfitte e scontri diretti di chi ha il nome scritto in *Nuova partita* (maiuscole e spazi non contano; nel doppio conta ogni giocatore). **Importa** aggiunge le partite salvate come .json con *Salva file* su un altro telefono: le finite vanno nello storico, quelle non finite tra le partite sospese da riprendere. **Trasferisci** (in alto a destra nello Storico) sposta storico e partite sospese su un altro telefono con un QR: i due telefoni devono stare sulla stessa rete Wi-Fi o uno sull'hotspot dell'altro; su quello che li ha tocchi *Invia*, **scegli le partite da mandare** (si parte con l'ultima spuntata; ci sono *Tutte*, *Nessuna*, *Ultimi 7 giorni* e *Di un giocatore…*), tocchi *Invia (N)* e lasci aperta la finestra col codice, sull'altro *Ricevi* e inquadri il codice (serve il permesso della fotocamera). Così si continua su un altro telefono anche una partita in corso: sospendila, trasferisci e riprendila. Le partite si aggiungono a quelle che il telefono ha già. **Foto dei giocatori**: tocca il cerchio accanto al nome in *Nuova partita* (o nel dettaglio del giocatore nello Storico) e scegli una foto; si ritaglia quadrata e si ritrova dal nome. Compare nello Storico, nel riepilogo e sul tabellone TV, resta solo su questo telefono (passa all'altro col trasferimento) e non finisce nell'immagine condivisa. Toccando di nuovo il cerchio la si cambia o la si toglie. Se l'app si chiude mentre sei sul riepilogo (Android che la chiude in secondo piano, un aggiornamento, un riavvio del telefono), riaprendola lo ritrovi; non se l'hai lasciato tu con *Nuova partita*, *Esci* o togliendo l'app dalle recenti. La cartella predefinita è **Documenti/Tennis Score Manager** (da Android 10; su Android 8 e 9 la cartella dell'app): si apre da qualsiasi app di gestione file e resta anche disinstallando l'app. Salvando due volte con lo stesso nome il secondo diventa *nome (1)*; nell'immagine i testi lunghi (nomi, indirizzo) si adattano alla larghezza.

   Il riepilogo ha tre schede. **Riassunto**: i dati qui sopra e, coi braccialetti 2.4, colpi, velocità del polso e colpi per punto (6.4). **Statistiche**, per la partita o per un set: i due giocatori a confronto con barre che partono dal centro (colorata quella di chi è avanti) per punti e game vinti al servizio e in risposta, palle break salvate e vinte, totale punti e game, punti di fila, ultimi 10 punti, match point annullati; coi braccialetti 2.4 anche colpi per tipo e velocità media del polso. **Cronologia**: set per set, ogni game con la pallina di chi serve, *SERVIZIO PERSO* sui break, il punteggio dopo ogni punto con **PB** (palla break), **SP** (set point) e **MP** (match point) e l'esito (*GAME*, *BREAK*, *SET*, *PARTITA*). Ace, doppi falli, prime di servizio, vincenti ed errori ci sono se in *Modalità e regole* accendi le **statistiche dettagliate**: al posto dei due tasti grandi compaiono *Ace* e *Fallo* (la seconda volta diventa *Doppio fallo*) e, per ciascun giocatore, *Vincente*, *Errore* (il punto va all'altro) e *Punto*; *Annulla punto* toglie anche un fallo. È pensata per chi segna da bordo campo. Dopo *Errore* compare per 6 secondi *Gratuito / Forzato*; con braccialetti o telecomandi, dopo ogni punto compare *Com'è finito il punto?* (Ace, Vincente, Errore): rispondere è facoltativo, il punteggio non cambia. Nello Storico il dettaglio di un giocatore mostra l'**andamento**: punti vinti partita per partita e le medie di ace, doppi falli, prime, vincenti ed errori. Le statistiche di ogni partita contano anche palle game, set point e match point trasformati, game vinti a zero, game di fila, set vinti da sotto di un break e, nella scheda **Tempi**, la durata di ogni set e il game più lungo.

   **Rivincita** (in fondo al riassunto): propone lo stesso giorno della settimana e la stessa ora, la settimana dopo, e il circolo della partita; si cambiano data, ora e luogo e si aggiunge una nota. **Manda l'invito** apre la condivisione (WhatsApp, SMS, email…) con il messaggio pronto: chi contro chi, quando, dove e com'è finita l'ultima volta. **Metti in calendario** crea l'evento nell'app del calendario, **Prepara la partita** riporta in *Nuova partita* giocatori, circolo, campo e regole (il torneo no). Niente account: l'invito passa dai canali che usi già.

**Uscire**: **Esci** (dalla partita chiede conferma, e c'è anche nel riepilogo) chiude davvero l'app; lo stesso se la togli dalle app recenti. Alla riapertura riparte dalla scheda **Gioca**.

**Ritiro**: il tasto **Arbitro** (il martelletto) sotto il punteggio, alla voce **Ritiro**, chiede chi si ritira e chiude la partita col punteggio di quel momento, senza i punti del game in corso (es. `6-3 2-1 rit.`). Vince l'avversario, la voce lo annuncia e, prima di **Partita conclusa**, **Annulla il ritiro** rimette tutto com'era. Nei tornei il risultato entra da solo con il ritiro segnato; la partita conta come vittoria ma non pesa sul rating TSM.

**Richiami dell'arbitro**: sempre in **Arbitro**, **Violazione di condotta** e **Violazione di tempo** chiedono a chi va il richiamo e, sotto ogni nome, dicono già cosa comporta. Condotta: il 1° è un avvertimento, il 2° dà un punto all'avversario, dal 3° un game (nel tie-break, il tie-break). Tempo: il 1° è un avvertimento, poi chi risponde perde il punto e chi serve perde il servizio (con le statistiche dettagliate diventa una prima fuori, o il doppio fallo sulla seconda). La voce e la TV lo annunciano, **Annulla** toglie l'ultimo richiamo e il riepilogo dice quanti ce ne sono stati. Un ritiro prima del primo punto risulta `w.o.`.

**Pause, riscaldamento e squalifica**: sempre in **Arbitro**, prima del primo punto c'è **Riscaldamento** (5 minuti, il tempo partita parte col primo punto). **Medical timeout** (3 minuti) e **Bagno o cambio d'abiti** (5 minuti) chiedono per chi è la pausa e fanno partire il tempo, che si vede in alto e sulla TV; il riepilogo dice quante pause ha chiesto ciascuno e **Annulla** toglie l'ultima. **Squalifica** chiude la partita come il ritiro, col segno `squal.`; nei tornei il risultato entra così. Nei tornei, nella scheda della partita, c'è anche il **Doppio w.o.** (non si presenta nessuno dei due): non passa nessuno e l'avversario del turno dopo passa da solo. Nei gironi un w.o. conta 6-0 6-0 e, col ritiro o la squalifica, il set interrotto e quelli che mancano vanno a chi vince. Nelle squadre, in una partita dell'incontro, **W.o. a nostro favore** e **W.o. a loro favore** scrivono 6-0 6-0 o 0-6 0-6.

**Correzioni**: se ha servito chi non doveva (nel doppio anche il compagno sbagliato), **Arbitro › Correggi chi serve** fa servire da adesso chi tocchi tu; con **Correggi i lati del campo** si scambiano i lati. Il punteggio resta com'è, come vuole il regolamento: i punti giocati valgono. **Annulla** toglie la correzione.

**Altro in Arbitro**: **Imposta punteggio** mette set, game, punti e chi serve (per una partita iniziata senza l'app o da rimettere in pari col campo) e si continua da lì; quei punti non entrano nelle statistiche. Nel doppio **Ordine di risposta** dice chi risponde da destra (parità) in ogni squadra: si sceglie anche a inizio set, insieme all'ordine di servizio, e sotto il tasto di chi riceve compare chi risponde. **Aggiungi una nota** scrive una riga con l'ora e il punteggio nelle note della partita (riepilogo, PDF e testo condiviso). Quando lo shot clock arriva a zero compare **25 secondi passati: violazione di tempo?** con i due giocatori: un tocco e il richiamo è segnato.

**Cambio palle**: nelle regole della partita si sceglie quando (7 / 9, 9 / 11, 11 / 13: la prima volta dopo tanti game, poi ogni tanti; il tie-break conta come un game). Al momento giusto la voce e la TV dicono **Palle nuove**; se cade all'inizio di un tie-break si rimanda al secondo game del set dopo. Con **Animazione del cambio palle** acceso, sul telefono e sul tabellone TV parte un'animazione di qualche secondo (tocca per chiuderla); **Prova l'animazione** la fa vedere subito.

**Salvataggi**: la partita si salva da sola a ogni punto. Nella scheda **Gioca** una partita sospesa si elimina **tenendo premuto** il suo *Riprendi*; tutte le sospese sono in **Progressi › Storico › Sospese**, da riprendere o eliminare (spuntandone più d'una, anche insieme). *Sospendi* ferma i tempi; se esci, se il telefono si spegne o l'app viene chiusa, la partita si ritrova in **Riprendi** (scheda **Gioca**, o *Riprendi partita sospesa* sulla schermata INIZIO PARTITA) e riparte con *Riprendi*. *Annulla punto* ricalcola tutto dall'inizio, quindi funziona anche dopo la fine di un game, di un set o della partita. Il cestino di una partita sospesa chiede conferma. Se la partita è iniziata da un braccialetto a schermo bloccato (lì Android non dà la posizione), il luogo si prende appena riapri l'app.

### 7.1 Allenamento con la fascia (app 2.5)

Per allenarsi con la fascia cardio senza una partita: **Allenati** › **Allenamento con la fascia**. Si usa **una fascia**, quella del Giocatore 1, con età, peso e sesso per calorie e zone; si sceglie direttamente lì (con l'interruttore *Fasce cardio*, Bluetooth e permessi se mancano).

1. **Avvia**: in grande il battito dal vivo, sotto il tempo e il giro in corso.
2. **Giro** chiude un pezzo dell'allenamento e ne apre un altro (come il tasto giro di un cronometro): uno scatto, un esercizio, un giro di pista. **Pausa / Riprendi** ferma il tempo; le pause non contano.
3. **Fine**: l'app registra ancora **un minuto di recupero** (stai fermo) per misurare quanto scende il battito; **Salva subito** lo salta.

- **Riepilogo**: durata, battito medio e massimo, calorie, **HRV (RMSSD)** dagli intervalli tra un battito e l'altro mandati dalla fascia (servono almeno una trentina di battiti puliti; confrontala con i tuoi allenamenti simili, non con quelli degli altri), **recupero in 1 minuto** (più scende, meglio è), grafico del battito, zone cardio e, per ogni giro, durata, media, massimo e recupero.
- **CSV** (secondo per secondo, con il giro e il minuto di recupero) e **PDF** di una pagina con grafico, torta delle zone e giri, da condividere.
- Gli allenamenti restano nella stessa schermata, a parte dalle partite; si riaprono o si eliminano. Il battito arriva anche dal secondo telefono (6.6) e la registrazione continua a schermo spento.

### 7.2 Progressi (app 2.5)

**Progressi** › **Rating TSM · Progressi**: un documento per vedere se migliori, **separato** per allenamenti e partite.

- **Allenamenti**: durata, battito medio e massimo, calorie, HRV, recupero.
- **Partite** di un giocatore dello storico (scelto dall'elenco): partite vinte, punti vinti, ace, doppi falli, prime in campo, vincenti, errori gratuiti. Ace e gli altri valori dettagliati ci sono solo per le partite giocate con le statistiche dettagliate; sono totali per partita.
- Per ogni voce la media della **prima metà** del periodo e quella della **seconda**: ▲ verde se migliora, ▼ rossa se peggiora (meno doppi falli o errori è un miglioramento), = se resta uguale (meno del 5% di differenza) o se non si può dire (battito). Servono almeno 2 allenamenti o partite.
- **Cosa confrontare**: i **Filtri** scelgono il periodo (tutto, 30 giorni, 3 mesi, 1 anno), i **giorni della settimana** (per esempio solo i mercoledì, anche più di uno) e, per le partite, **torneo**, **circolo** e **avversario** presi dallo storico; sotto, l'elenco **Da confrontare** da spuntare (*Tutti* / *Nessuno*). Confronto, PDF e stampa usano solo quelli spuntati.
- **Rating TSM**: in *Partite*, sopra i filtri, il rating del giocatore (si parte da 1500) con le partite contate, la variazione dell'ultima e l'andamento, e la **Classifica TSM** di tutti i giocatori dello storico. È un Elo calcolato sul telefono: sale quando vinci, di più contro chi ha un rating più alto e con un punteggio netto; nel doppio conta la media della coppia; tennis e padel hanno classifiche separate. Nelle prime 5 partite è *provvisorio* e si muove di più. Nel riepilogo di ogni partita la scheda **Rating TSM** mostra quanto ha guadagnato o perso ciascuno; con **Amichevole** la partita resta nello storico ma non conta.
- **PDF** (con l'andamento di ogni voce e l'elenco completo, su più pagine se serve) da condividere, o **Stampa** con il servizio di stampa di Android (anche *Salva come PDF*).

**Obiettivi** (in cima a Progressi): scegli una voce (partite vinte, punti vinti, prime di servizio, ace, doppi falli, errori gratuiti, partite in 30 giorni, allenamenti in 7 giorni) e un traguardo; la barra si riempie con la media delle tue ultime 3, 5 o 10 partite. Quando lo raggiungi compare l'avviso in **Gioca**. **Il mio anno** (anche nella scheda Progressi): partite, vittorie, ore in campo, il rivale col bilancio, le partite mese per mese, la serie più lunga e gli allenamenti, in un'immagine da condividere. Servono tutti e due che l'app sappia chi sei (Altro › Tu).

**Calendario** (in Progressi, sotto *Il mio anno*): il mese coi pallini gialli delle partite e rossi degli allenamenti, e da quante settimane di fila giochi o ti alleni; tocca un giorno per vedere cosa hai fatto e aprire la partita.

### 7.3 Video: telefono-telecamera, highlights e moviola (app 2.5)

Un secondo telefono su un treppiede riprende la partita e mette sul video il punteggio dell'arbitro.

1. **Arbitro**: Altro › **Dispositivi** › **Secondo telefono** › **Usa un secondo telefono** (QR, come in 6.6).
2. **Telecamera**: Altro › **Dispositivi** › **Secondo telefono** › **Fai da telecamera**, inquadra il QR, poi **Apri la telecamera** (permessi di fotocamera e microfono; serve un telefono che registri in 1080p). Con *Punteggio collegato* la grafica del punteggio è sul video (**Grafica** la accende o la spegne); senza arbitro registra solo il video.
   - **Registra / Ferma**: il video va in galleria (*Film/Tennis Score Manager*) con i capitoli, uno per punto.
   - **Diretta**: trasmissione RTMP su YouTube, Twitch, Facebook o un server tuo; server e chiave dello stream in *Diretta: server*.
   - **Inquadra** (assistente inquadratura): scrivi quanti metri dietro la linea di fondo e a che altezza hai messo il telefono e su che campo giochi; l'app dice l'inclinazione verso il basso che tiene nel video tutte e due le linee di fondo e il giocatore lontano, e se il campo ci sta in larghezza. Spesso il limite è proprio la larghezza: dalla recinzione (circa 6 m) l'obiettivo principale taglia gli angoli vicini, il **grandangolo** no (**Usa l'obiettivo…**, oppure il tasto **Obiettivo**, a registrazione ferma). Con **Mostra la livella** sull'anteprima compaiono l'orizzonte e le indicazioni (*punta più in basso di 2°*, *gira in senso orario*…), verdi quando va bene, con una vibrazione quando il telefono è in posizione. La livella si vede anche nel riquadro **Telecamera** del telefono dell'arbitro. Destra e sinistra i sensori non le sanno: controlla a occhio che la rete sia al centro.
   - **Sagoma** (sagoma del campo): sull'anteprima compaiono le linee del campo come le vede l'obiettivo, e seguono il telefono mentre lo inclini. Muovi i cursori (**Distanza**, **Altezza**, **Direzione**, **Spostamento laterale** e, se serve, **Campo visivo** e **Correzione inclinazione**) finché combaciano con le linee vere: così si sistema anche la centratura, e l'inclinazione consigliata diventa precisa. La taratura viene ricordata per il campo della partita (*Circolo · Campo* dell'arbitro) e ripresa la volta dopo. Sagoma e cursori ci sono anche nel riquadro **Telecamera** dell'arbitro. Più in alto è il telefono, meglio si vede la metà lontana del campo: da 2,5 m è schiacciata appena sopra la rete.
3. **Telecamera in alto, comandata dall'arbitro**: sul telefono dell'arbitro **Telecamera** (accanto a *Moviola* in partita, oppure *Inquadratura e comandi della telecamera* in Altro › **Dispositivi** › **Secondo telefono**, utile per sistemarla prima di iniziare) mostra l'inquadratura del telefono-telecamera com'è nel video, grafica compresa, con **Registra / Ferma**, **Diretta**, **Grafica** e lo **zoom**. Si vedono anche durata della registrazione, batteria e spazio libero (in arancione se sono bassi). L'immagine è piccola e a scatti (circa 3 fotogrammi al secondo) e passa solo mentre la si guarda; la chiave della diretta va messa sul telefono-telecamera.

- **Video** › **I tuoi video**: i video registrati, da rivedere punto per punto, game per game o set per set. **Capitoli YouTube** prepara i capitoli da incollare nella descrizione. **Importa video** prende un video fatto con un'altra fotocamera (il file resta dov'è): i punti si segnano guardandolo, toccando a fine punto chi l'ha vinto e, se vuoi, com'è finito (*Annulla ultimo* toglie l'ultimo); formato e regole sono quelli di *Modalità e regole*.
- **Filtri allenatore** (icona del filtro): solo i punti di un giocatore, vinti o persi, al servizio o in risposta, sulla prima o sulla seconda, per come sono finiti, sulle palle break o sui set e match point. **Riproduci** li mette in fila.
- **Highlights**: montaggio automatico dei punti più belli (ace, vincenti, palle break, set e match point, scambi lunghi coi braccialetti) nell'ordine di precedenza scelto con le frecce, per al massimo 1, 3, 5, 10 o 15 minuti o un'**Altra durata** fino a 90. Se non ci sta tutto prima si accorciano i pezzi, poi restano fuori i punti meno importanti. Il risultato va in galleria.
  - **Statistiche a fine video**: 2 o 3 schermate da 10 secondi come nelle dirette dei tornei: servizio e palle break; punti, errori e scambi corti e lunghi; battito, se c'era la fascia. La partita è quella dello storico giocata mentre si registrava, o quella dei punti segnati su un video importato. Ai lati la foto del giocatore; senza foto un ritratto stilizzato di uomo o di donna (scelta **Uomo / Donna** in *Nuova partita*) (senza scelta, quello dell'uomo; vale in tutta l'app, anche in *Nuova partita*, storico, riepilogo, PDF e tabellone TV); sotto, la percentuale di punti vinti. In alto torneo, categoria e turno (*Altri dettagli* in *Nuova partita*), oppure circolo e campo.
  - **Musica** sotto le statistiche, con dissolvenza in entrata e in uscita: *Nowhere Land* di Kevin MacLeod (incompetech.com, licenza CC BY 4.0: se pubblichi il video, citalo nei crediti), oppure un brano tuo con **Scegli un brano** (se ne usa l'inizio; usa solo musica che hai il diritto di mettere nei tuoi video).
  - In fondo a ogni schermata delle statistiche c'è la scritta **Creato con Tennis Score Manager** con l'indirizzo del sito.
- **Moviola**: sul telefono dell'arbitro, con la telecamera collegata, **Moviola** chiede alla telecamera il punto contestato (*Ultimo punto*, *Punto prima* o *Dall'ultimo punto segnato*) e lo mostra, con pausa e fotogramma per fotogramma. **Anche sul tabellone** lo fa vedere anche sul tabellone TV.

### 7.4 Racchette: racchetta virtuale (app 2.5)

**Progressi** (o **Altro**) › **Le mie racchette**: ogni racchetta fisica ha la sua scheda (due racchette dello stesso modello sono due schede: corde, peso e ore cambiano).

- **Scheda**: soprannome (A, B…), marca, modello, peso, bilanciamento, piatto corde e schema; si possono scrivere a mano o prendere **dal catalogo** (dati del produttore, da correggere se hai misurato la tua). *Messa da parte*: non si propone più, ma resta nei confronti.
- **Incordature**: tipo (monofilamento, multifilamento, sintetico, budello, ibrido), marca e modello (anche dalle corde comuni), calibro, tensione delle verticali e delle orizzontali, data e note. *Nuova incordatura* parte da quella montata. Per ognuna l'app somma le **ore di gioco** delle partite e degli allenamenti associati e avvisa quando si supera la **soglia di controllo** scelta (20 ore di serie): dal solo tempo non può sapere quanta tensione è rimasta.
- **Prima di giocare**: in *Nuova partita*, sotto la classifica di ogni giocatore, si sceglie la sua racchetta; nell'allenamento la si sceglie sopra la fascia. La partita (punti, colpi dei braccialetti, battito) e l'allenamento si salvano con una **copia** della racchetta com'era quel giorno, corde e tensione comprese: reincordarla dopo non cambia le sessioni passate.
- **Nel riepilogo** della partita (scheda *Riassunto*) e dell'allenamento si vede la racchetta usata, la si può associare anche dopo (si prende l'incordatura montata quel giorno) e si danno le **sensazioni** da 1 a 5: controllo, comfort, potenza e fatica.
- **Confronto**: *per racchetta* o *per incordatura* (stessa racchetta con tensioni o corde diverse), si scelgono le configurazioni A e B. Barre affiancate per sessioni, ore, partite vinte, punti vinti, prime in campo, punti vinti con la prima e con la seconda, ace, doppi falli, vincenti ed errori gratuiti a partita, velocità media del polso e colpi dei braccialetti, battito medio, e le sensazioni medie. Il valore migliore è in grassetto, ma l'app non sceglie una vincitrice: contano anche avversario, superficie, condizioni e forma.

### 7.5 Allenamento dei colpi: servizio, dritto, rovescio e volée (app 2.5)

Un telefono sul cavalletto riprende chi serve; il braccialetto al polso riconosce il servizio e coi tasti si dice com'è andato. Basta un telefono, senza arbitro.

1. **Allenati** › **Allenamento dei colpi**, scegli il colpo (**Servizio**, **Dritto**, **Rovescio**, **Volée**) › **Inizia** (serve il braccialetto collegato come in 6.1; senza, si segna dai tasti sullo schermo).
2. **Dove mettere il telefono** (la prima volta si apre da solo, poi dal tasto *Dove mettere il telefono?*):
   - **a fianco di chi serve**, dal lato del braccio che colpisce (a destra per un destro), a **3-4 m**, all'altezza della linea di fondo o mezzo metro dietro;
   - obiettivo a **1-1,2 m** da terra, telefono **in orizzontale** e dritto, puntato su chi serve;
   - nell'inquadratura deve stare **tutta la persona, dai piedi alla racchetta in alto** (circa 2,7 m): se la testa della racchetta esce, allontana il telefono o passa al **grandangolo** (tasto *Obiettivo*);
   - per vedere anche dove va la palla, in alternativa: 3-4 m **dietro** chi serve, un po' di lato, a 1,5-2 m d'altezza;
   - mai davanti a chi serve né sulla traiettoria della palla.
3. Scegli **lato** (destra/sinistra), **prima o seconda** e, se vuoi, il **bersaglio** (al T, al corpo, esterno): valgono per i servizi seguenti.
4. Servi e premi **KEY1 = dentro** o **KEY2 = fuori**; **KEY1 tenuto premuto** annulla l'ultimo. La voce dice il conteggio (*Dentro. 8 su 12*) e il braccialetto lo mostra. Se il braccialetto ha riconosciuto il servizio, l'istante è quello; altrimenti si prende qualche secondo prima del tasto. Per questo, all'avvio l'app **accende da sola il riconoscimento dei colpi** sul braccialetto (firmware 2.4; non serve la calibrazione, conta solo l'istante) e alla fine lo rimette com'era; sullo schermo *Riconoscimento colpi acceso* lo conferma. Con un firmware più vecchio l'istante si stima dal tasto.
   - **Lo schermo**: in alto una riga sola (dentro su tentativi, l'ultimo colpo e tre icone: braccialetto, riconoscimento dei colpi, memoria col numero di clip e lo spazio); le spiegazioni compaiono per qualche secondo all'inizio e toccando **i**, così l'inquadratura resta libera. La telecamera tiene in **memoria** gli ultimi due minuti e salva **solo i colpi segnati** (qualche secondo prima e dopo): niente video lunghi da tagliare.
   - **Pausa** spegne telecamera e memoria (batteria, calore) mentre raccogli le palline: i tasti del braccialetto non segnano niente, KEY1 o **Riprendi** fanno ripartire. Dopo **5 minuti senza colpi** la pausa si mette da sola (la voce lo dice) e allora riparte anche al primo colpo riconosciuto.
5. Per ogni servizio si salva una **clip** di qualche secondo intorno al colpo. **Fine** mostra il riepilogo: dentro su tentativi, per lato, per prima e seconda, per bersaglio e, se il braccialetto ha riconosciuto i servizi, la rotazione media del polso sui servizi dentro e su quelli fuori. **Rivedi tutti / solo i dentro / solo i fuori** apre le clip nella moviola (pausa, rallentatore, fotogramma per fotogramma).
6. Sotto **Inizia** ci sono le ultime 3 sessioni **del colpo scelto** (sui tasti dei colpi il numero delle sessioni, es. *Rovescio · 4*) e la linea dei **colpi dentro, sessione dopo sessione**. **Tutte le sessioni** apre quelle del colpo scelto, divise per mese, con lo spazio occupato dai video e se sono già analizzate. Tocca una sessione per il riepilogo e le clip; **tienila premuta** per selezionarla (l'icona in alto le seleziona tutte), poi **Elimina** (sessione e video) o **Solo i video** (libera spazio: restano conteggi, misure dell'analisi e immagini dei momenti chiave). In **Progressi** c'è l'andamento di ogni colpo.
   - **Dove stanno i video**: in **Documenti/Tennis Score Manager/Allenamento colpi**, con le cartelle **Servizio**, **Dritto**, **Rovescio** e **Volée** (create all'avvio) e dentro una cartella per sessione (data e ora) e una per colpo, con `video.mp4` (si apre e si condivide da qualsiasi app), l'analisi e le tre immagini. Si vedono con qualsiasi file manager e restano anche disinstallando l'app (come archivio: reinstallandola, le sessioni nell'app non tornano). Su Android 8-10 la stessa struttura sta nella cartella dell'app. Le clip delle sessioni di prima della 2.5 si spostano lì da sole al primo avvio.
7. **Analisi del movimento** (dalla sessione salvata): **Analizza i servizi** trova la posizione del corpo nelle clip (modello MoveNet: la prima volta l'app chiede di scaricarlo, 7 MB, dalla pagina del progetto su GitHub; poi tutto sul telefono, niente internet, qualche secondo per servizio; si può eliminare in fondo alla schermata. Non c'è sui dispositivi con processore x86) e misura tre momenti: **caricamento** (braccio del lancio in alto), **ginocchia** più piegate e **impatto**. Per ogni servizio: le tre immagini con lo scheletro disegnato e le misure (ginocchio più piegato, gomito all'impatto, altezza dell'impatto rispetto alla statura, braccio del lancio, spalle al caricamento, tempo dal caricamento all'impatto); in alto il confronto **dentro e fuori**. Sono stime da un video di lato: valgono soprattutto per confrontare i tuoi servizi tra loro. Il corpo si riconosce bene con tutta la persona nell'inquadratura, di lato e con buona luce; racchetta e palla non si misurano.
   Tocca un servizio per aprirlo: **Guarda il video con lo scheletro** apre la clip nella moviola col corpo disegnato sopra, al rallentatore, con i tasti per saltare a **Caricamento**, **Ginocchia** e **Impatto** e quello per togliere o rimettere lo scheletro; due dita ingrandiscono video e scheletro insieme (fino a 6×), doppio tocco per tornare normale. **Confronta con un altro colpo** mette accanto lo stesso colpo di un altro giorno o della stessa sessione (solo quelli analizzati e col video, della stessa persona): i due video con lo scheletro, uno sopra l'altro (affiancati col telefono in orizzontale), allineati sull'impatto e in loop sullo stesso tratto, con velocità, fotogramma per fotogramma e i momenti chiave; **Sovrapponi** disegna nel primo anche lo scheletro del secondo (in azzurro), con le anche nello stesso punto e il busto della stessa lunghezza, così si vede dove il movimento cambia. Anche le tre immagini si toccano per vederle a tutto schermo e ingrandirle. Con **più persone** nell'inquadratura l'app segue chi serve (chi alza di più il braccio) e copre le altre; lo dice sotto il servizio: controlla nelle immagini. **Rifai l'analisi** la ripete da capo (le clip restano), utile dopo un aggiornamento dell'app.

**Dritto, rovescio e volée** funzionano allo stesso modo (KEY1 dentro, KEY2 fuori, clip per ogni colpo, riepilogo e analisi), con queste differenze:
- **Dove mettere il telefono**: di lato a chi colpisce, perpendicolare alla direzione dei colpi, a 4-5 m e fuori dal corridoio, all'altezza del punto d'impatto; obiettivo a circa 1 m; tutto lo swing nell'inquadratura (il polso si muove di circa 2 m avanti e indietro). Per la volée: di lato a chi gioca a rete, a 3-4 m, all'altezza del giocatore. Sempre dallo stesso lato per tutta la sessione, e mai sulla traiettoria della palla.
- Al posto di lato, prima/seconda e bersaglio si sceglie la **direzione** voluta (**incrociato**, **al centro**, **lungolinea**) e, per la volée, se è **di dritto** o **di rovescio**; il riepilogo li conta a parte.
- **Analisi del movimento**: i tre momenti sono **preparazione** (dove il polso si ferma in fondo all'apertura), **impatto** (polso più veloce; lì intorno l'app rilegge un fotogramma ogni 33 ms) e **finale** (polso più in alto dopo l'impatto; nella volée, dove il polso si ferma). Se nella finestra ci sono più colpi vale quello riconosciuto dal braccialetto; i polsi scambiati da MoveNet (succede di lato) si rimettono a posto da soli. Riprendi **di lato**: di fronte l'apertura va in profondità e non si vede. Le misure (*—* se il gomito o le gambe non si vedono): ginocchio più piegato, gomito all'impatto, altezza dell'impatto, quanto **davanti al corpo** si colpisce, **ampiezza della preparazione**, altezza del finale (tutte in rapporto alla statura) e il tempo dalla preparazione all'impatto. Il video con lo scheletro ha i tasti **Preparazione**, **Impatto** e **Finale**.

### 7.6 Allievi: la parte per l'allenatore (app 2.5)

- **Allenati › Allievi** › **Nuovo allievo**: nome (lo stesso delle partite: così foto, storico e rating si ritrovano da soli) e mano.
- **Chi si allena**: in Allenati, sopra *Inizia*, scegli l'allievo (o *Nessun allievo*). La sessione è sua: le ultime sessioni, *Tutte le sessioni*, l'andamento in Progressi e i numeri sui tasti dei colpi sono solo i suoi, e le clip vanno in **Allenamento colpi/<colpo>/<allievo>/<data e ora>**.
- **La scheda dell'allievo** (tocca il nome in *Allievi*):
  - **Colpi**: per servizio, dritto, rovescio e volée le sessioni, i colpi, l'ultima volta e la linea dei colpi dentro; toccando un colpo si aprono le sue sessioni.
  - **Movimento**: le misure dell'analisi (6.4 e 7.5) dell'ultima sessione analizzata accanto a quella prima, per vedere cosa cambia.
  - **Partite**: vittorie e sconfitte, rating TSM e le ultime partite dallo storico col suo nome.
  - **Note dell'allenatore**: cosa lavorare, obiettivi; si salvano da sole.
  - **Racchetta**: nella scheda (e in *Modifica*) si sceglie la sua racchetta tra *Le mie racchette*: va con ogni sessione com'era quel giorno, corde e tensione comprese, e la scheda mostra l'incordatura montata. Una racchetta di nessuno diventa sua, così la si propone anche nelle sue partite. Se la racchetta non c'è ancora, **Nuova racchetta** la crea da lì (con il giocatore come proprietario) e la sceglie.
  - **Report per l'allievo**: scegli *Ultimi 30 giorni* o *Tutto*, poi **PDF completo** (colpi con la percentuale dentro e l'andamento, il movimento, per ogni colpo uno dentro e uno fuori con le tre immagini dei momenti chiave, partite, rating e le tue note) o **Immagine per WhatsApp** (una pagina sola con i numeri dei colpi e l'impatto di un colpo). Si apre subito la condivisione: WhatsApp, email o dove vuoi. Il report porta il tuo nome come allenatore (da *Tu*) e il circolo (da *Circolo › Campi*).
  - **Allena ora** lo sceglie e torna in Allenati, pronto per *Inizia*. **Togli l'allievo** lo toglie dall'elenco: le sue sessioni restano, senza allievo. **Cancella l'allievo e i suoi dati** toglie invece tutto quello che il telefono ha di suo: sessioni con video e analisi, note e foto (la racchetta resta, non più sua; le partite dello storico restano, perché ci sono anche gli avversari).

### 7.7 Giocatori: il database (app 2.5)

Ogni persona che gioca o si allena è un **giocatore** del database, con un identificativo che resta lo stesso su ogni telefono: così le statistiche, il rating e lo storico sono di quella persona anche se c'è un omonimo, e anche dopo aver passato partite da un telefono all'altro.

- **Nome, cognome e soprannome**: nome e cognome sono obbligatori e identificano la persona; il **soprannome** (facoltativo) è come la si chiama in campo: partita, tabellone, voce e statistiche usano il soprannome se c'è, sennò nome e cognome. I giocatori nati dallo storico hanno un nome solo, che diventa il soprannome: in *Giocatori* sono *da completare* e nella scheda c'è **Completa nome e cognome**.
- **La scheda** (*Nuovo giocatore* o *Modifica*): obbligatori solo **nome e cognome**; il resto è facoltativo, ma ognuno ha il suo perché:
  - **Soprannome**: è il nome che si vede e si sente in partita, sul tabellone e nella voce ("Rossi" invece di "Mario Rossi").
  - **Foto** e **uomo/donna**: la foto compare in Nuova partita, nello Storico, nel riepilogo e sul tabellone TV; senza foto, uomo/donna sceglie il ritratto. Uomo/donna serve anche alle calorie.
  - **Data di nascita**: l'età si vede nella scheda e serve alle calorie; per i soci decide se è minorenne (avvisi al genitore, consensi).
  - **Altezza e peso**: con età e sesso fanno calcolare da sole le **calorie della fascia cardio** quando il giocatore gioca o, se è il tuo, si allena (senza, l'app le chiede a parte o non le calcola).
  - **Circolo di appartenenza**: si vede nella scheda; utile per gli ospiti e per gli avversari di altri circoli, per sapere chi sono.
  - **Mano e rovescio** (a una o due mani): descrivono il giocatore nella sua scheda, utile all'allenatore; l'analisi del movimento riconosce da sola il braccio che colpisce.
  - **Allievo**: lo mette tra gli allievi dell'allenatore (Allenati › Allievi, *Chi si allena*), con sessioni, movimento e report.
  - **Telefono ed email**: servono agli **avvisi** (WhatsApp, SMS, email: tornei, quote, convocazioni) anche se non è socio.
  - **Racchetta**: va con ogni sua sessione e partita com'era quel giorno (corde e tensione) e se ne confrontano le statistiche; se non c'è, *Nuova racchetta* la crea da lì.
  - Classifica, tessere e certificato non sono qui: sono nei *Dati del socio*, perché servono ai tornei e alle quote.
- **Nuova partita**: in campo vanno i giocatori del database. Ogni posto ha **Scegli il giocatore**, che apre l'elenco con la ricerca (prima soci e allievi; chi è già in campo non c'è) e in cima **Giocatore nuovo**, che chiede nome, cognome e il resto e poi lo mette in campo; sotto, i giocatori recenti da toccare al volo. Scelto, il posto mostra foto e nome con **Cambia** e la **X**; la classifica di un socio si scrive da sola. Un nome di prima senza scheda (rivincite, partite vecchie) va scelto dall'elenco o creato prima di *Avanti*.
- **Al primo avvio** l'app crea un giocatore per ogni nome delle partite già giocate.
- **Altro › Giocatori**: tutti, con la ricerca; **Nuovo giocatore** ne crea uno coi suoi dati (anche un allievo: poi nella scheda si accende *Allievo*); *omonimo* in arancione dove due giocatori hanno lo stesso nome. La scheda è quella degli allievi (7.6) con in più:
  - **Allievo**: lo mette (o lo toglie) tra gli allievi.
  - **Unisci con un altro giocatore**: la stessa persona scritta in due modi ("Rossi" e "Mario Rossi") o creata due volte; partite e sessioni passano a lui.
  - **Separa un omonimo** (in *Partite*): spunti le partite di un'altra persona con lo stesso nome, che diventa un giocatore nuovo.
- **Partite da un altro telefono** (trasferimento o *Importa*): un giocatore che il telefono conosce si aggancia da solo; uno nuovo si aggiunge col suo identificativo; se ha il nome di uno dei tuoi l'app chiede una volta **Chi è?** (*Sì, è lui* lega i due, così le volte dopo lo riconosce; *No, un'altra persona* lo aggiunge). Le partite delle versioni vecchie, senza giocatori, si agganciano per nome.
- La **foto** e **uomo/donna** valgono ancora per il nome: due omonimi le condividono.

### 7.8 Tu, e Profilo e dati (app 2.5)

- **Tu** è il tuo giocatore del database: Progressi e rating partono da lui. Alla prima apertura bastano nome e cognome (e il soprannome); poi **Altro › Tu** apre la tua scheda. Se non l'hai ancora scelto, *Chi sei?* propone i giocatori che ci sono (hai già giocato partite) o **Sono nuovo**. Nella scheda di un altro giocatore, **Sono io** lo rende tuo.
- **Altro › Profilo e dati**:
  - **Crea il backup**: un file ZIP con partite, giocatori, allievi, sessioni dei colpi con le analisi, racchette, allenamenti, tornei, foto, impostazioni e registrazioni della voce; con **Includi i video** anche clip dei colpi, moviola e highlights (il file diventa grande). Lo salvi dove vuoi (Download, un PC, una chiavetta): niente cloud. Non entrano il modello dell'analisi del movimento (si riscarica) e i file che hai salvato tu in Documenti.
  - **Ripristina**: rimette i dati di un backup su questo telefono, anche nuovo; sostituisce tutto quello che c'è (un backup senza video lascia i video che ci sono). Poi l'app si chiude: riaprendola ritrovi i dati del backup.
  - **Cancella tutti i dati**: l'app torna come appena installata (due conferme; con **Anche i video** pure clip, moviola e highlights). I file salvati in Documenti e i video della galleria restano.

**Backup automatico ogni giorno**: senza video, solo se è cambiato qualcosa. Le ultime 5 copie vanno in Documenti › Tennis Score Manager › Backup, che resta anche se disinstalli l'app; dall'elenco *Copie automatiche* ognuna si **manda** (Drive, email…) o si **ripristina**. Per sicurezza tienine una anche fuori dal telefono: su Drive, sul PC o facendo sincronizzare quella cartella.

### 7.9 Ruoli: Tu, Allenatore, Circolo (app 2.6)

- In **Altro › Profilo e dati › Ruoli** (e alla prima apertura) scegli chi usa l'app; puoi averne più di uno. I ruoli cambiano le schede della prima schermata, **i dati sono gli stessi**; con più ruoli, in alto c'è il ruolo attivo: toccandolo passi a un altro.
  - **Tu**: Gioca, Allenati, Video, Progressi, Altro (l'app di sempre).
  - **Cosa usi** (Altro): si tiene solo quello che serve. Braccialetti e telecomandi, fasce cardio, allenamento dei colpi e allievi, video e telecamera, progressi e rating, racchette, padel: quello che si spegne non compare, e le schede rimaste vuote (Allenati, Video) spariscono; lo storico resta sempre (senza Progressi va in Gioca). All'inizio è tutto acceso; alla prima presentazione l'app propone cosa tenere dalla risposta a "come la userai". Altro è diviso in gruppi: Dispositivi e voce, Dati, Aiuto.
  - **Allenatore**: **Allievi** (elenco, chi si allena e l'allenamento dei colpi), **Gioca** (le partite tra allievi, il torneo tra amici), Video, Altro.
  - **Circolo**: **Oggi** (le prenotazioni del giorno, cosa c'è da incassare, i soci da sistemare, le partite dei tornei, le lezioni, gli incontri e le sfide: ogni riquadro si tocca per andarci), **Tornei**, **Campi** (nome del circolo e campi, uno per riga: in Nuova partita, sotto il campo, si toccano e scrivono circolo e campo; il tabellone TV), **Giocatori**, Altro.
  - **Funzioni del circolo** (in fondo alle schede del Circolo): il circolo sceglie cosa usa. Di base Soci, Quote e pagamenti, Planning dei campi e Tornei del circolo; in più Squadre e campionati, Scuola tennis, Classifica a sfida, Statistiche dei campi, Server del circolo e Segreteria da PC. Le funzioni spente non compaiono nei menu e i dati restano; all'inizio sono accese anche quelle in cui ci sono già dati.
  - Il **?** in alto in molte schermate apre la guida proprio alla sezione di quella schermata.
- Il tabellone c'è in ogni ruolo: nessuno ti vieta di segnare una partita.
- **Prima apertura**: lingua, ruoli, il tuo nome (nome, cognome, soprannome), come usi l'app (con *Tu*), il circolo (con *Circolo*), i tuoi dati con il **promemoria del backup** (ogni 30 giorni la prima schermata ti ricorda di farlo; lo trovi anche in Profilo e dati).

### 7.10 Esercizi guidati (app 2.6)

- Un **esercizio** è un colpo e dei passi in fila: per esempio *20 dritti incrociati, poi 20 lungolinea*, o per il servizio *10 prime da destra al T, poi 10 esterne…*. Ci sono quattro esempi pronti, uno per colpo.
- **Esercizi** (dalla scheda *Allievi* dell'allenatore, o dalla scelta sotto *Inizia*): **Nuovo esercizio**, nome, colpo e passi; per ogni passo quanti colpi (− e + di 5 in 5) e, secondo il colpo, lato, prima o seconda e bersaglio, direzione, volée di dritto o di rovescio (*—* = lascia com'è). Toccando un esercizio lo cambi o lo elimini.
- In **Allenati**, sotto i colpi, **Esercizio** sceglie quello da seguire (o *Libero*). Durante l'allenamento l'app imposta da sola le scelte del passo, in alto mostra *Passo 1 di 2 · 7/20*, a fine passo la voce dice il prossimo (e il braccialetto lo mostra) e alla fine *Esercizio finito*. Annullando un colpo si torna al passo giusto. Il riepilogo conta per direzione e bersaglio, e la sessione ricorda il nome dell'esercizio.

### 7.11 Soci del circolo (app 2.6)

Col ruolo **Circolo**, in **Giocatori › Soci** c'è l'elenco degli iscritti, pensato per il dispositivo della segreteria.

- **Nuovo socio**: scegli un giocatore che c'è già (chi ha giocato partite o è un allievo) o creane uno nuovo. Dalla scheda di un giocatore si può anche toccare **Iscrivi come socio**.
- **Dati del socio**: telefono ed email (servono per gli avvisi), data di nascita, socio dal, tessera del circolo; **tessera FITP** con numero, tipo (agonista, non agonista, altro), anno e classifica; **certificato medico** con tipo e scadenza; consenso al trattamento dei dati e a foto e video. Per i **minorenni** anche il genitore o tutore e il suo telefono.
- Del certificato si salvano **solo il tipo e la scadenza**, non il documento: è un dato sanitario e resta nel fascicolo del circolo.
- Sotto ogni socio l'app dice cosa manca: consenso privacy, tessera FITP dell'anno, certificato mancante, scaduto o in scadenza entro un mese, dati del genitore, un contatto. In rosso quello che impedisce di giocare (tessera e certificato), in arancione il resto. **Da sistemare** mostra solo chi ha qualcosa da fare.
- I dati da socio restano sul telefono: quando le partite passano a un altro telefono viaggiano solo nome, cognome, soprannome e mano. **Togli dai soci** cancella tessere, certificato e consensi; il giocatore e le sue partite restano.

### 7.12 Quote e pagamenti (app 2.6)

In **Giocatori › Quote e pagamenti** (ruolo Circolo) si tiene il registro del circolo. L'app **registra** i pagamenti, non li incassa: niente carte né conti collegati.

- **Nuova quota**: nome (es. *Quota sociale 2027*), tipo (quota sociale, tessera FITP, corso, torneo, altro), importo e, se serve, la scadenza. Toccando una quota, **Addebita a tutti i soci** la mette a chi non l'ha ancora; sotto si vede quanti hanno pagato.
- Nella scheda del socio, **Quote e pagamenti** mostra cosa deve: *pagato*, *da pagare*, *mancano 100,00 €*, in rosso *in ritardo* dopo la scadenza. **Nuovo addebito** aggiunge una quota del circolo o un altro importo (una lezione, un'incordatura).
- Toccando un addebito, **Registra pagamento**: importo (di solito quanto manca: si può pagare anche a rate), data, metodo (contanti, bonifico, POS, Satispay, altro) e riferimento (CRO, causale). Con **Ricevuta subito** si apre la ricevuta in PDF da mandare o stampare, numerata per anno (*1/2027*, a gennaio si riparte da 1). È un **documento non fiscale**: per le ricevute fiscali e la contabilità vale quello che dice il vostro commercialista.
- Un pagamento sbagliato si annulla (il numero della ricevuta non si riusa); un addebito senza pagamenti si elimina, con dei pagamenti si **annulla**: non si deve più e i pagamenti restano nel registro.
- In alto: incassato nell'anno e quanto c'è da incassare; **Da incassare** elenca chi deve, dal debito più grande. Anche in *Soci* chi deve qualcosa è tra i *da sistemare*.
- **Esporta i pagamenti (CSV)**: tutti i pagamenti dell'anno con data, ricevuta, nome, descrizione, metodo, riferimento e importo, per il foglio di calcolo del commercialista.
- Cancellando un giocatore spariscono i suoi addebiti non pagati; i pagamenti fatti restano col nome, perché sono contabilità. Il registro è nel backup di *Profilo e dati*.

### 7.13 Tornei del circolo (app 2.6)

Col ruolo **Circolo**, in **Tornei › Tornei del circolo** si gestiscono i tornei **sociali** e quelli **FITP**: iscrizioni, quote, sorteggio, tabellone, gironi e risultati. Per i tornei FITP l'app aiuta il circolo e il giudice arbitro, ma non sostituisce i sistemi ufficiali della Federazione.

- **Nuovo torneo**: nome, sociale o FITP, singolare o doppio, categoria, **eliminazione diretta** o **gironi + tabellone** (quanti gironi e quanti passano da ciascuno), date, **quota d'iscrizione** per giocatore (diventa una quota in *Quote e pagamenti*), *nel tabellone solo chi ha pagato*, **classifica massima** (es. *4.1*: entrano i 4.1 e chi è sotto; vuoto = open). Per i FITP anche il codice del torneo e il giudice arbitro.
- **Iscrivi**: si sceglie dal database (prima i soci) o si crea un giocatore; nel doppio si scelgono i due. Con la quota, l'iscrizione si addebita a ciascuno e si incassa dalla sua scheda (anche con la ricevuta).
- Sotto ogni iscritto l'app dice se è **in regola**. **FITP**: tessera agonistica dell'anno, certificato agonistico valido il giorno d'inizio, classifica nel limite. **Sociale**: certificato valido (chi non è socio si segnala, ma può giocare). Per tutti: l'iscrizione pagata, se la si chiede. In rosso chi non entra nel sorteggio.
- **Teste di serie**: dalla classifica FITP (più forte = prima); toccando un iscritto la si sceglie anche a mano. Due teste di serie fino a 8 posti, poi una ogni quattro.
- **Sorteggio**: entrano solo gli iscritti in regola. Nel tabellone la testa di serie 1 va in cima, la 2 in fondo, la 3 e la 4 (e poi le 5-8) sorteggiate tra i loro posti; i **bye** vanno alle prime teste di serie e passano da soli. Con i gironi: distribuzione a serpentina per classifica e tutti contro tutti; a gironi finiti, **Tabellone finale** con i primi come teste di serie e i secondi nella metà opposta al primo del loro girone. Finché non c'è un risultato, **Rifai il sorteggio**.
  - **Modifica tabellone** (scheda *Tabellone*, anche dal PC): dopo il sorteggio tocchi un giocatore o un *bye*, poi l'altro posto, e i due si scambiano; nei gironi si scambiano due iscritti. Chi si iscrive dopo il sorteggio resta fuori finché, scelto un posto, non lo metti al posto di un altro: chi esce resta iscritto ma fuori dal tabellone. Si tocca solo ciò che non ha risultati, e i gironi si fermano quando esiste il tabellone finale. Nei tornei FITP modifica solo se lo consente il giudice arbitro: il numero di modifiche resta scritto.
- **Tabellone**: i gironi con la classifica (vittorie, differenza set e game, poi lo scontro diretto; in evidenza chi passa) e il tabellone, un turno per colonna (si scorre di lato). **Partite**: prima quelle da giocare in ordine di orario, poi le giocate.
- Toccando una partita: **Gioca col segnapunti** apre Nuova partita con giocatori, classifiche, torneo, turno e campo già scritti; a partita finita il risultato entra da solo nel tabellone. Oppure il risultato a mano (dal punteggio l'app capisce chi ha vinto), **w.o.** o **ritiro**; poi campo e orario. Correggendo un risultato, il turno dopo si aggiorna.

- **Sul tabellone TV**: con il tabellone TV acceso (*Dispositivi*), nel torneo **Mostra sul tabellone TV** copre il punteggio con il torneo, in pagine che si alternano ogni 15 secondi: **tabellone** (in giallo chi ha vinto), **gironi** con la classifica (in evidenza chi passa) e **ordine di gioco** di oggi (o le prossime partite in programma), con campo, ora e risultati. Ogni risultato, anche quello che arriva dal segnapunti, si vede subito. **Togli dal tabellone TV** torna al punteggio.

### 7.14 Avvisi ai partecipanti e ai soci (app 2.6)

L'app prepara i messaggi, già scritti e personali ("Ciao Mario, …"), e apre l'app giusta: **l'invio lo fai tu**, premendo Invia. Niente servizi esterni né abbonamenti: si usano WhatsApp, SMS, email e la condivisione del telefono.

- **Dove**: nel torneo, **Avvisi ai partecipanti**: *Iscrizione e quota* (conferma e quota pagata o da pagare), *Prossima partita* (a ciascuno turno, avversario, giorno, ora e campo), *Ordine di gioco di un giorno*, *Tabellone* (un'immagine col tabellone e i gironi), *Messaggio libero*. In **Quote e pagamenti**, **Sollecita chi deve** (voce per voce, con le scadenze). In **Soci**, **Avvisa i soci**: a chi deve sistemare qualcosa (certificato, tessera, consensi) o un messaggio a tutti.
- **A tutti**: il testo per tutti (l'ordine di gioco, l'elenco degli iscritti, il tabellone). **Condividi** lo manda in un gruppo WhatsApp, su Telegram, Instagram, Facebook… con l'immagine se c'è; **Email a tutti** li mette in copia nascosta; **SMS a tutti** apre un SMS con tutti i numeri.
- **Uno per uno**: per ciascuno il suo messaggio (toccandolo si legge tutto) e i tasti **WhatsApp** (si apre la chat con quel numero e il testo già scritto), **SMS** ed **Email**. La spunta ricorda chi hai già avvisato.
- I numeri senza prefisso valgono come italiani (+39). Per i **minorenni** si scrive al telefono del genitore (*Dati del socio*), con "un avviso per Luca". Chi non ha né telefono né email è segnalato: aggiungili nella sua scheda.

### 7.15 Campi e planning (app 2.6)

Col ruolo **Circolo**, nella scheda **Campi**.

- **I campi**: si crea un campo (**Nuovo campo**), lo si mette in ordine e lo si tocca per cambiarlo: nome (rinominandolo, prenotazioni, periodi e partite dei tornei lo seguono), superficie (**terra, erba, cemento**, altro), **coperto o scoperto**, **luci**, **riscaldamento**, i **prezzi** suoi (all'ora, con le luci, supplemento riscaldamento all'ora; vuoti = quelli generali) e **una o più foto** dalla galleria. **Elimina il campo** toglie anche le sue prenotazioni da oggi in poi.
- **Planning dei campi**: il giorno con un campo per colonna e un'ora per riga (◀ ▶ e il calendario per cambiare giorno). Tocca una casella libera per prenotare: campo, giorno, ora e durata, tipo (gioco, lezione, chiuso, altro), giocatori dal database e ospiti, nota. Il **prezzo** si calcola da solo (luci dall'ora scelta, riscaldamento se lo si chiede) e si può cambiare; **Addebita ai giocatori** lo divide in parti uguali e lo mette in *Quote e pagamenti*. **Come si paga**: *al campo* o *online*; a prenotazione addebitata, **Pagato: …** la segna pagata con uno dei modi di pagamento del circolo. **Ogni settimana per N settimane** ripete la prenotazione (le date col campo occupato si saltano e l'app le dice). **Avvisa i giocatori** manda campo, giorno, ora e come si paga.
- Le **partite dei tornei** con campo e orario compaiono da sole; l'app non lascia prenotare sopra un'altra prenotazione o una partita.
- **Non prenotabili**: periodi in cui i campi non si prenotano, per **corsi**, **tornei**, **manutenzione** o altro: campi (o tutti), dal giorno al giorno, giorni della settimana, fascia oraria o tutto il giorno; per un torneo si prendono nome e date dal torneo. Nei periodi dei corsi si mettono le loro lezioni (toccando la casella si apre una lezione già pronta).
- **Statistiche dei campi** (nel planning): per le ultime 4 settimane, il mese in corso o quello scorso, l'**occupazione** di ogni campo (ore prenotate su quelle prenotabili: i periodi non prenotabili non contano), gli **incassi** (prezzo delle prenotazioni di gioco e lezione, pagate o no), quante prenotazioni e quante arrivate online, le ore dei tornei, una mappa **giorno × ora** che mostra quando si gioca di più e chi prenota di più.
- **Orari e prezzi**: apertura e chiusura, durata di una casella (30, 60, 90 minuti), prezzo all'ora generale e con le luci, da che ora servono le luci, quanto dura nel planning una partita di torneo.
- **Modi di pagamento** (in *Quote e pagamenti*): li decide chi gestisce il circolo. Si parte da contanti, POS, bonifico e Satispay e se ne aggiungono altri (PayPal, Revolut, Bancomat Pay, un link SumUp…): nome, link/IBAN/numero e se si paga a distanza. Si scelgono quando si registra un pagamento (il nome va su ricevute e CSV); quelli a distanza, con i loro dettagli, vanno negli avvisi e nei solleciti. L'app non incassa: registra e manda le istruzioni.

### 7.16 Scuola tennis (app 2.6)

Col ruolo **Circolo**, in **Giocatori › Scuola tennis**.

- **Nuovo corso**: nome (es. *SAT Under 10 · Gruppo A*), livello, istruttore (dal database o scritto), stagione (dal… al…), **orari** settimanali (giorno, ora, durata, campo: anche più di uno), posti massimi, **quota** al mese o per la stagione, note. Negli orari con un campo, **quel campo diventa non prenotabile** nel planning per tutta la stagione (le lezioni del corso sì); cambiando o eliminando il corso, il planning si aggiorna.
- **Allievi**: si aggiungono dal database (soci o no) fino ai posti; per ciascuno la **presenza** (presente 3 su 4, in arancione sotto il 75 %) e cosa deve pagare. Nella scheda del giocatore ci sono i suoi corsi.
- **Lezioni**: il calendario nasce dagli orari. Tocca una lezione per l'**appello** (chi c'era) o per segnarla **annullata** (pioggia, festa…); **Annulla e avvisa gli allievi** prepara il messaggio con giorno e ora. *Avvisa gli allievi* manda un messaggio libero. Ai minorenni si scrive al genitore.
- **Quote**: per ogni mese della stagione (o per la stagione) **Addebita agli allievi** mette la quota a chi non l'ha ancora; si vede quanti hanno pagato e si incassa come le altre quote, con la ricevuta.

### 7.17 Squadre e campionati (app 2.6)

Col ruolo **Circolo**, in **Tornei › Squadre e campionati**.

- **Nuova squadra**: nome, campionato, stagione, **campionato FITP** (si controllano tessera agonistica e certificato agonistico del giorno dell'incontro), formula (quanti singolari e doppi per incontro), capitano. In **Rosa** si aggiungono i giocatori e si mettono nell'ordine della lista; in rosso chi non è in regola per il prossimo incontro. Il bilancio (vinti, persi, pari) è accanto al nome.
- **Nuovo incontro**: avversario, giorno e ora, in casa o in trasferta, dove si gioca, **ritrovo**, note. In casa si possono **tenere dei campi** per qualche ora: nel planning diventano non prenotabili.
- **Disponibilità**: *Chiedi la disponibilità a tutti* manda il messaggio (rispondono SÌ, NO o FORSE); le risposte si segnano con un tocco, e *Richiedi a chi non ha risposto* insiste solo con loro.
- **Convocati**: prima chi ha detto sì, poi i forse; in rosso chi non è in regola quel giorno. **Manda la convocazione** scrive a ciascuno giorno, ora, dove e ritrovo, e prepara per il gruppo l'elenco dei convocati.
- **Partite**: per ogni singolare e doppio i nostri (dai convocati) e i loro, poi il punteggio (dal nostro lato: l'app capisce chi ha vinto) o **Gioca col segnapunti**, e a partita finita il risultato torna da solo. In alto il risultato dell'incontro (es. *4-2 vinto*); **Manda il risultato** lo prepara partita per partita.

### 7.18 Server del circolo per le prenotazioni (facoltativo, app 2.6)

**Facoltativo**: senza server non cambia niente, si prenota in segreteria come sempre. Con un piccolo server al circolo (un Raspberry Pi Zero 2 W o un vecchio PC) i giocatori invitati **prenotano i campi da soli**, dal telefono, **anche da iPhone**, da una pagina web del circolo che si aggiunge alla schermata Home come un'app.

**Guida semplice per la segreteria** (avvio dalla chiavetta, abbinamento, account Tailscale, i tre modi): [GUIDA_SERVER.md](https://github.com/steve-linux/Tennis-Score-Manager/blob/main/deliver/GUIDA_SERVER.md).

**Numero e PIN, più circoli**: dopo il primo invito il giocatore può scegliersi **numero di telefono e PIN** (nella pagina del circolo e nell'app, in **Le mie**). Così, se cambia telefono o esce, rientra da solo con **Hai già un PIN?**, senza un nuovo invito. Dopo 5 PIN sbagliati si aspetta un quarto d'ora; dopo 20 il PIN non vale più e la segreteria lo azzera dalla scheda del socio (**Azzera il PIN**). Nell'app si può entrare in **più circoli**: **Aggiungi un circolo** in **Le mie**, poi si sceglie il circolo in alto; promemoria e avvisi arrivano da tutti, col nome del circolo.

- **Il server**: si avvia con `java -jar tsm-server.jar` (serve Java 17 o più). All'avvio scrive l'indirizzo e un **codice di abbinamento** (anche nel file *setup-code.txt* nella cartella dei dati).
- **Abbinare la segreteria**: *Planning dei campi › Orari e prezzi › Server del circolo*: indirizzo del server (es. `http://192.168.1.50:8080`) e codice, poi **Abbina**. Da lì il planning (campi, orari, prezzi, non prenotabili, prenotazioni della segreteria e partite dei tornei, senza nomi) va al server da solo dopo ogni modifica; con il planning aperto, ogni minuto arrivano le prenotazioni online.
- **Le regole per i giocatori**, nello stesso dialogo: prenotazioni aperte o chiuse, fin quanti giorni avanti, quante prenotazioni a testa, durata massima, fino a quante ore prima si disdice da soli, fino a quanti minuti prima si prenota, quali campi si prenotano online e come si paga (il testo lo leggono i giocatori).
- **Invitare un giocatore**: nella sua scheda, **Invito per prenotare online**: un QR e un codice che valgono una volta per 14 giorni (o **Condividi il link**). Il giocatore inquadra il QR. Su iPhone la pagina **TSM** lo guida: *Condividi › Aggiungi alla schermata Home*, poi apre TSM dalla Home e incolla il codice (Safari e l'app sulla Home non condividono i dati, quindi l'invito si usa nell'app installata). Nella scheda si vede chi prenota online; **Togli l'accesso online** lo scollega.
- **Con l'app TSM su Android** il giocatore prenota anche da *Gioca › Prenota un campo*: inquadra il QR dell'invito (o incolla il link), poi vede le sue prenotazioni, sceglie il giorno e tocca una casella libera; durata, riscaldamento e prezzo prima di confermare, **Disdici** finché si è in tempo, **Esci da questo circolo** per scollegare il telefono. Le regole sono le stesse della pagina web.
- La pagina e l'app hanno **tre schede**: **Prenota** (giorni e caselle), **Le mie** (prenotazioni, liste d'attesa, promemoria) e **Compagni** (Cerco compagno).
- Nell'app, in **Le mie**, **Invita** manda a chi gioca con te il campo, il giorno e l'ora già scritti (su WhatsApp o dove vuoi); dopo una prenotazione l'app ti porta lì da sola.
- **Le prenotazioni online** entrano nel planning col nome del giocatore e la scritta *online*, con il prezzo del campo; si addebitano e si incassano come le altre. Se la segreteria la toglie o la sposta, sul server si libera; se il giocatore la disdice, sparisce dal planning. Gli altri giocatori vedono solo *Occupato*, mai i nomi.
- **Su un Raspberry Pi Zero 2 W** (o un PC con Debian/Ubuntu): con *Raspberry Pi Imager* si scrive sulla microSD **Raspberry Pi OS Lite (64-bit)**, mettendo nelle impostazioni il nome `tsm-circolo`, la Wi-Fi del circolo e SSH acceso. Poi dal computer si copia il kit (`tsm-server-kit.tar.gz`) sul Pi, si entra con `ssh` e si lancia `tar xzf tsm-server-kit.tar.gz && cd tsm-server && sudo ./install.sh`: installa Java, avvia il server a ogni accensione e scrive **indirizzo** (anche `http://tsm-circolo.local:8080`) e **codice di abbinamento**. Rilanciato con un kit nuovo, aggiorna il server senza toccare i dati; `sudo ./reset-admin.sh` dà un codice nuovo per la segreteria.
- **Su un PC con la chiavetta USB**: l'immagine `tsm-server.iso` (Debian live) si scrive con `sudo ./make-usb.sh /dev/sdX` (su Windows con **Rufus**, con una *partizione persistente*): la chiavetta ha una partizione **persistence** dove restano i dati. Si avvia il PC dalla chiavetta (meglio col cavo di rete): sullo schermo compaiono indirizzo e codice. Senza la partizione persistence il server dimentica tutto allo spegnimento (e lo scrive sullo schermo).
- **Copia dei dati**: tutto è in un solo file, `/var/lib/tsm-server/state.json`; conviene copiarlo ogni tanto e prima di riscrivere la chiavetta.
- **Promemoria ai giocatori**: il circolo sceglie quante ore prima della partita arriva (*Promemoria ai giocatori*, 0 = nessuno; di solito 2). Nella pagina TSM il giocatore tocca **Attiva i promemoria** (su iPhone dall'app TSM sulla schermata Home, con iOS 16.4 o più recente e l'indirizzo https del circolo) e riceve subito una notifica di prova; nell'app TSM su Android c'è **Promemoria sul telefono**. Arriva anche un avviso se la segreteria toglie o sposta una sua prenotazione (non se la disdice lui).
- **Lista d'attesa**: toccando una casella **Occupato** (nella pagina TSM o in *Prenota un campo* nell'app) il giocatore sceglie **Avvisami se si libera**, per quel campo o per un campo qualsiasi alla stessa ora (al massimo 3 attese). Se la prenotazione viene disdetta o spostata gli arriva subito l'avviso e nella pagina compare **Prenota**: vince chi prenota per primo; se un altro arriva prima, si torna ad aspettare. Le attese spariscono da sole quando l'ora passa.
- **Cerco compagno**: nella pagina TSM e in *Prenota un campo* il giocatore pubblica un annuncio (giorno, fascia oraria, singolo o doppio, livello, nota; al massimo 3 aperti). Gli altri lo vedono e toccano **Ci sto**; chi l'ha messo riceve un avviso. Al completo (2 giocatori nel singolo, 4 nel doppio) il server **prenota da solo** il primo campo libero nella fascia, con le regole delle prenotazioni online, e avvisa tutti; se non c'è un campo libero lo dice, e lo prenotano loro. Gli annunci passati spariscono da soli.
- **Chat dell'annuncio**: chi ha messo l'annuncio e chi ha toccato **Ci sto** hanno una **Chat** (sulla scheda dell'annuncio, col pallino se c'è del nuovo) per mettersi d'accordo; gli altri non la vedono. Arriva una notifica a ogni messaggio. Solo testo; i messaggi restano 30 giorni.
- **Chat tra soci** (scheda **Chat** in *Prenota un campo* e nella pagina del circolo): la **Chat del circolo**, dove leggono e scrivono tutti i soci collegati (le notifiche si possono spegnere), e i **Messaggi privati**. Si scrive in privato solo a chi ha acceso **Possono scrivermi i soci** (spento di partenza); chi riceve può sempre rispondere. Con **Blocca** un socio non ti scrive più e non leggi più i suoi messaggi; con la bandierina un messaggio si **segnala** alla segreteria, che delle chat private vede solo i messaggi segnalati. In **Gioca** c'è la voce **Chat del circolo** (sotto *Prenota un campo*, con l'ultimo messaggio) e, quando ci sono messaggi nuovi, una striscia in cima che apre la chat; lo stesso in cima alla pagina del circolo. Tenendo premuto un proprio messaggio (nella pagina: i puntini ⋯) lo si **modifica**, nei primi 15 minuti, o lo si **elimina**: agli altri resta «Messaggio eliminato» (se era stato segnalato, la segreteria ne vede ancora il testo).
- **Bacheca del circolo**: la segreteria scrive un avviso (*Server del circolo › Bacheca del circolo*, o **Prenotazioni online** dal PC) e arriva a tutti i giocatori con una notifica; loro lo leggono in cima a **Prenota**, senza poter rispondere. Da *Chat e segnalazioni* la segreteria legge le chat e toglie i messaggi fuori luogo. Le nuove arrivano anche nelle novità di **Gioca**.
- **Se il server non risponde**: l'app se ne accorge da sola (ogni pochi minuti) e in *Prenota un campo* scrive che il circolo non risponde, mostrando le proprie prenotazioni dell'ultimo controllo; quando torna, tutto si riaccende da solo. Il resto dell'app non cambia. Chi non è mai entrato non vede *Prenota un campo*: in **Altro** c'è **Ho un invito del circolo**.
- **Avvisi alla segreteria**: con *Avvisami delle prenotazioni online* (acceso di solito) il telefono della segreteria riceve una notifica quando un giocatore prenota o disdice, anche ad app chiusa: ogni 15 minuti circa, quando c'è rete. Toccandola si apre il planning sul giorno della prenotazione. Quelle tolte dalla segreteria stessa non danno notifica.
- **Da casa, senza toccare il router** (consigliato). In *Server del circolo*, con il telefono abbinato, tocca **Attiva** in *Accesso da internet senza toccare il router*. Il server installa Tailscale, poi l'app chiede di aprire Tailscale per entrare (o creare un account gratuito) e, la prima volta, per confermare Funnel. Quando è pronto, **Indirizzo da internet** si compila da solo (`https://tsm-circolo….ts.net`): HTTPS incluso, niente DuckDNS, funziona anche con CGNAT.
  - Il collegamento parte dal server verso fuori, quindi il router non si tocca e i giocatori non installano niente. Il traffico passa dai sistemi di Tailscale, un servizio esterno; Funnel è ancora in beta e ha limiti di traffico, che per le prenotazioni di un circolo non dovrebbero pesare. Il primo accesso all'indirizzo può richiedere qualche minuto.
  - L'accesso a Tailscale scade di norma dopo 180 giorni: per evitarlo, su login.tailscale.com, in *Machines*, scegli *Disable key expiry* per il server. Se scade, l'app mostra di nuovo **Apri Tailscale**.
  - **Abbinare** la segreteria resta possibile solo dalla rete del circolo. **Disattiva** chiude il collegamento. Sui server già installati, prima rilancia `install.sh` con il kit nuovo.
- **Da casa** (internet, in HTTPS: serve anche per mettere la pagina sulla schermata Home dell'iPhone). Tutto gratuito:
  1. Su **duckdns.org** (si entra con un account GitHub, Google…) si crea un nome, es. `tcprova.duckdns.org`, e si copia il **token**.
  2. Sul **router del circolo** si dà al Pi un indirizzo fisso e si inoltrano a quell'indirizzo le porte **TCP 80 e 443**.
  3. Sul Pi, dalla cartella del kit: `sudo ./setup-https.sh tcprova.duckdns.org --duckdns IL-TOKEN` (dalla chiavetta: `sudo /opt/tsm-server/kit/setup-https.sh …`). Installa **Caddy**, che prende da solo il certificato **Let's Encrypt** e lo rinnova; ogni 5 minuti dice a DuckDNS l'indirizzo di casa, se il provider lo cambia. Con un dominio proprio si scrive quello, senza `--duckdns`.
  4. Nell'app, *Server del circolo › Indirizzo da internet*: `https://tcprova.duckdns.org`. Da lì gli inviti usano questo indirizzo, e la segreteria fuori dal circolo arriva al server da lì.
  - L'**abbinamento** della segreteria si fa solo dalla rete del circolo (con l'indirizzo `http://…:8080`), mai da internet.
  - Se dal telefono col 4G/5G non si arriva, la linea del circolo è probabilmente dietro **CGNAT** (succede con molte linee FWA e mobili): l'indirizzo internet che mostra il router è diverso da quello che dà un sito come *mio-ip* in rete. Allora si chiede al provider un **IP pubblico** (spesso gratis), oppure si usa un tunnel (Tailscale Funnel, Cloudflare Tunnel: servizi di terzi) o un piccolo server in affitto.
  - `sudo ./setup-https.sh --remove` toglie l'accesso da internet.

### 7.19 Classifica a sfida (app 2.6)

Col ruolo **Circolo**, in **Tornei › Classifica a sfida**.

- **Nuova classifica** (anche più di una: maschile, femminile, under…), poi **Aggiungi giocatori**: il primo aggiunto è il numero 1; **Sistema la classifica** li sposta a mano o li toglie.
- **Nuova sfida**: si sceglie chi sfida e poi chi è sfidato, al massimo *N* posti più su (**Regole**: quanti posti, di solito 3, e in quanti giorni va giocata, di solito 14). Ognuno ha una sola sfida aperta per volta. **Avvisa** manda ai due chi sfida chi ed entro quando.
- **Gioca col segnapunti**: lo sfidante è il Giocatore 1; a fine partita il risultato torna da solo. Oppure **Risultato** a mano (il punteggio dal lato dello sfidante: l'app capisce chi ha vinto).
- Se vince lo **sfidante prende il posto** dello sfidato e chi stava in mezzo scende di uno; se vince lo sfidato non cambia niente. In **Sfide giocate** la cronologia, con i posti di prima e di dopo.

### 7.20 Segreteria da PC (app 2.6)

Inserire soci e pagamenti è più comodo con tastiera e mouse. **Un computer (Windows, Mac o Linux) sulla stessa rete del telefono** apre nel browser una pagina servita dal telefono, senza installare niente e senza server: **i dati restano sul telefono**, il computer li vede e li modifica finché è collegato e non ne tiene copie. Lo usa una persona alla volta. Il *server del circolo* (7.18) è un'altra cosa e serve solo alle prenotazioni dei giocatori.

- **Accendere**: *Funzioni del circolo* › **Segreteria da PC**, poi la voce nella scheda dei campi e **Accendi**. Il telefono mostra indirizzo (es. `https://192.168.1.20:8443`) e codice.
- **Sul computer**: apri l'indirizzo nel browser. Lo mostra il telefono, e con **Condividi l'indirizzo** lo mandi al computer (WhatsApp Web, email) senza ricopiarlo: il link da solo non dà accesso. La prima volta avvisa che la connessione non è privata: è normale, perché il certificato lo ha creato il telefono e non un'autorità. Scegli *Avanzate › Procedi*. Se l'avviso torna senza che tu abbia cambiato telefono o reinstallato l'app, non proseguire: confronta l'impronta SHA-256 nel browser con quella sul telefono.
- **Entrare**: la pagina mostra un QR. Sul telefono tocca **Inquadra il QR del computer**, inquadralo e tocca **Consenti**, controllando l'indirizzo del computer: il PC entra da solo. Senza fotocamera, apri *Entra col codice* sul computer e scrivi il codice del telefono.
- **Cosa si fa**: **Oggi** (la prima pagina: chi gioca, cosa incassare, soci da sistemare, partite e lezioni del giorno), **Soci** (cerca, aggiungi, modifica, *Da sistemare*, *Certificato da rinnovare*, **Stampa** ed **Esporta CSV** dell'elenco che vedi), **Importa da CSV** dei soci da Excel o Fogli (prima un'anteprima; i soci già presenti si completano senza cancellare niente), **Quote e pagamenti** (quote, addebiti, pagamenti, **Ricevuta** in PDF, esportazione CSV, stampa), **Planning** (vista **Giorno**, **Settimana** o **Mese**; prenoti, cambi, ripeti ogni settimana e cancelli anche tutta la serie; **Non prenotabili** per chiusure e manutenzione; stampa), **Tornei** (crea e modifica, iscrivi cercando tra i giocatori coi problemi di certificato, tessera e quota in vista, teste di serie, sorteggio, tabellone e gironi coi risultati, giorno, ora e campo di ogni partita col Planning che avvisa se il campo è occupato, e **Stampa** di iscritti, tabellone e programma). **Scuola tennis** (corsi con orari e campi, allievi, appello delle lezioni, lezioni annullate, quote del mese o della stagione da addebitare), **Statistiche dei campi** (occupazione, incassi, campo per campo, giorni e ore più pieni, chi prenota di più, per periodo, da stampare). Con **Prepara sollecito** in Quote e pagamenti scegli a chi scrivere tra gli addebiti che vedi: il messaggio si apre sul telefono e lo mandi da lì. **Squadre e campionati** (rosa, incontri in casa o in trasferta coi campi tenuti, disponibilità, convocati, partite e risultato; gli avvisi si aprono sul telefono). **Prenotazioni online**, col server del circolo (prossime prenotazioni dei giocatori, lista d'attesa, annunci di Cerco compagno da togliere se serve, regole per i giocatori, **Invia ora**), **Impostazioni** (nome del circolo, orari e prezzi, campi con superficie, luci e prezzi propri, modi di pagamento). Avvisi WhatsApp, foto dei campi e segnapunti dal vivo restano nell'app.
- **Backup della segreteria** (sul PC, sotto Statistiche; sul telefono in Campi › Backup della segreteria): un solo file `.tsmclub` con soci e giocatori, quote e pagamenti, planning, tornei, scuola tennis, squadre, campi e impostazioni (non partite, colpi e video: per quelli c'è il backup completo). **Scarica sul PC** o, dal telefono, **mandalo** dove vuoi (Google Drive, email, una chiavetta). Col **backup automatico** il telefono fa una copia al giorno solo se è cambiato qualcosa e tiene le ultime 14; col server del circolo le tiene anche lui. **Ripristina** mostra prima cosa c'è nel file e fa una copia di adesso, per tornare indietro. Il file contiene dati personali dei soci: tienilo in un posto tuo.
- **Sicurezza**: tutto viaggia cifrato (HTTPS, TLS 1.2 o 1.3). Servono il QR o il codice, usa e getta e a scadenza, e il consenso sul telefono; cinque errori cambiano il codice e bloccano i tentativi. Un solo computer alla volta, legato al suo indirizzo. Risponde solo alla rete locale, mai ai dati mobili. La sessione finisce con **Esci**, chiudendo la scheda, dopo 30 minuti ferma o dopo 12 ore. Senza nessuno collegato si spegne da solo dopo 30 minuti; la notifica ricorda che è acceso e ha il tasto **Spegni**. La chiave del certificato non entra nei backup. Su reti pubbliche o condivise meglio usare l'hotspot del telefono.
- **Consigli**: tieni il telefono acceso e in carica vicino al router. Se la pagina dice che il telefono non risponde, controlla che sia sulla stessa rete: alcune reti ospiti isolano i dispositivi fra loro, e allora va usato l'hotspot del telefono.

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

1. Altro › **Dispositivi** › **Tabellone su TV** › attiva **Tabellone su TV o monitor**. Su Android 13 e successivi l'app chiede il permesso delle notifiche: serve per la notifica del servizio che tiene acceso il tabellone.
2. Compaiono l'**indirizzo** (es. `192.168.43.1:8080`), il **QR** e quanti tabelloni sono collegati. Se c'è scritto *Nessuna rete*, accendi l'hotspot o collegati a quello dell'altro telefono.
3. **Anteprima su questo telefono** apre il tabellone nel browser del telefono stesso.
4. **Aspetto del tabellone**: colore di ciascun giocatore (8 colori), tempo partita, cronometro servizio e pause, set conclusi, messaggi (palla break, set point, cambio campo…), pallina di chi serve, segmenti spenti visibili, scritta in basso (vuota = circolo e campo di *Nuova partita*). Le modifiche arrivano subito sul monitor.
5. In partita, in alto al centro c'è **TV · 1** (tabelloni collegati): toccandolo si rivedono indirizzo e QR.

Col tabellone acceso un servizio in primo piano (notifica *Partita in corso · tabellone TV attivo*) tiene vivo il server anche a schermo spento, pure in modalità arbitro. Il tabellone è **solo lettura**: da lì non si può cambiare niente. Resta attivo anche sul riepilogo e sulle altre pagine finché il tabellone è acceso (notifica *Tabellone TV attivo*), così il risultato finale resta sul monitor anche con il telefono bloccato.

Cosa mostra, oltre al punteggio: *IN ATTESA DELLA PARTITA* prima di iniziare, *IN ATTESA DEL VIA* sulla pagina INIZIO PARTITA, **TIE-BREAK** / **MATCH TIE-BREAK** al posto di *VS*, *PARTITA SOSPESA* lampeggiante, **VINCE [nome]** a fine partita con tutti i set; i vantaggi si leggono **AD** anche sulle cifre a LED. Una partita vinta al super tie-break finisce con 1-0 nei game e 2-1 nei set, con [10-8] tra i set conclusi; i messaggi lunghi si rimpiccioliscono per stare nello schermo.

### 8.3 Sul telefono-tabellone

1. **Gioca** › **Usa come tabellone**.
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
- **Il telecomando non fa niente**: è associato al giocatore (*Altro › Dispositivi* › *Telecomandi*, riga col suo nome)? L'app deve essere aperta sulla partita, a schermo acceso (6.3). Se la riga dice *Non collegato*, premi un tasto per svegliarlo oppure riaccoppialo dalle impostazioni Bluetooth.
- **I colpi vengono confusi o risultano non riconosciuti**: il braccialetto va portato come durante la calibrazione (stesso polso, stesso verso); altrimenti rifai la calibrazione indossandolo come in partita (6.4).
- **Il braccialetto mostra MEMORIA PIENA**: un esempio di calibrazione non si è potuto salvare. Cancella gli esempi di un colpo (cestino) e rifalli.
- **Capire perché due colpi si confondono**: col braccialetto collegato al computer apri il monitor seriale (115200 baud) e scrivi `d`: compaiono gli esempi salvati e gli ultimi 40 colpi, ciascuno con le sue 11 misure. Gli ultimi colpi restano finché il braccialetto non si spegne o si riavvia.
- **Sync Gradle fallita per il JDK**: imposta Gradle JDK = jbr-21 (punto 2.6).
- **Il telefono-tabellone non trova l'arbitro**: stessa rete? (uno dei due fa l'hotspot, l'altro è collegato). *Tabellone su TV* acceso sull'arbitro? Prova l'indirizzo a mano. Alcuni hotspot isolano i dispositivi collegati tra loro ("isolamento client"): se c'è, disattivalo.
- **Il browser del telefono non apre l'indirizzo** con i dati mobili accesi: Android manda il traffico sui dati perché l'hotspot non ha internet. Usa l'app in *Usa come tabellone* (lo gestisce da sola) oppure spegni i dati mobili su quel telefono.
- **Monitor nero col cavo**: quel telefono non ha l'uscita video sulla USB-C (8.1), oppure è partito Samsung DeX: scegli *Duplica schermo*.
