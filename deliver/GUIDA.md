# Tennis Score Manager — guida passo passo

App Android (Kotlin + Jetpack Compose) per tenere il punteggio del tennis secondo le regole ITF, con chiamate vocali da giudice di sedia e due braccialetti **M5StickS3** collegati in Bluetooth LE.

## 0. Cosa c'è nel repository

| Percorso | A cosa serve |
|---|---|
| `TennisScoreManager/` | Il progetto Android Studio. |
| `firmware/TSM_Band/TSM_Band.ino` | Il firmware del braccialetto. |
| `deliver/installa_tsm.sh` | In alternativa al clone: crea **tutto** il progetto Android e lo sketch con soli blocchi `cat << 'TSM_EOF'` (il jar del Gradle wrapper è in base64). |
| `deliver/GUIDA.md` | Questa guida. |

Versioni usate e verificate: Gradle 8.14.3 · Android Gradle Plugin 8.13.2 · Kotlin 2.2.21 · Compose BOM 2025.12.00 · compileSdk/targetSdk 36 · minSdk 26 (Android 8.0). Firmware: M5Unified ≥ 0.2.12, NimBLE-Arduino ≥ 2.1, core ESP32 3.x.

---

## 1. Scaricare il progetto su Fedora

**Con git (consigliato)**:

```bash
git clone https://github.com/steve-linux/Tennis-Score-Manager.git ~/AndroidStudioProjects/Tennis-Score-Manager
```

Il progetto da aprire in Android Studio è la sottocartella `TennisScoreManager`; lo sketch è in `firmware/TSM_Band/`. Per gli aggiornamenti basta `git pull`.

**Oppure con lo script** (tutto come blocchi `cat`):

```bash
bash installa_tsm.sh
```

- Il progetto va in `/home/stefano/AndroidStudioProjects/TennisScoreManager`.
- Se quella cartella esiste già viene **spostata** in `TennisScoreManager.backup-AAAAMMGG-hhmmss` (i vecchi layout XML e le vecchie classi farebbero fallire la build nuova).
- Lo sketch va in `~/Arduino/TSM_Band/TSM_Band.ino`.
- Per usare altre cartelle: `bash installa_tsm.sh /percorso/progetto /percorso/sketch`.

## 2. Aprire e compilare in Android Studio

1. Avvia Android Studio: `/opt/android-studio/bin/studio.sh`.
2. **File › Open** → scegli la cartella `TennisScoreManager` (quella del clone o quella creata dallo script) → **Trust Project**.
3. Aspetta la sincronizzazione Gradle (la prima volta scarica Gradle, il plugin Android e le librerie: serve internet solo adesso).
4. Se compare *"Failed to find target android-36"* clicca il link **Install missing platform** (oppure **Tools › SDK Manager › SDK Platforms › Android 16 (API 36)**).
5. Se Android Studio propone l'**AGP Upgrade Assistant**, puoi ignorarlo: queste versioni sono state compilate e testate così.
6. JDK di Gradle: **Settings › Build, Execution, Deployment › Build Tools › Gradle › Gradle JDK** = *jbr-21* (quello incluso, è il predefinito).
7. **Build › Make Project**: deve finire con *BUILD SUCCESSFUL*.
8. Facoltativo: i test (47: regole, voce, batteria, impostazioni dei braccialetti) si lanciano con tasto destro su `app/src/test` › **Run Tests**.

## 3. Installare l'app sul telefono

1. Sul telefono: **Impostazioni › Info telefono** → tocca 7 volte *Numero build* → **Opzioni sviluppatore › Debug USB** attivo.
2. Colleghi il cavo, accetti l'impronta RSA, scegli il telefono in alto e premi **▶ Run**.
   - Se Fedora non vede il telefono: `sudo dnf install android-tools` (regole udev) e ricollega; in alternativa **Device Manager › Pair devices using Wi-Fi** (debug wireless, Android 11+).
3. In alternativa **Build › Build App Bundle(s) / APK(s) › Build APK(s)**, copia l'APK sul telefono e installalo (va consentita l'installazione da origini sconosciute).

## 4. La voce (funziona senza internet)

- Ogni chiamata è letta **in un'unica frase** dalla sintesi vocale del telefono, con una voce installata: niente internet e prosodia naturale. I nomi dei giocatori entrano nella frase.
- Pagina 2 › **Audio e voce**:
  - **Motore sintesi vocale**: *Predefinito del telefono* (sui Samsung è Samsung TTS) oppure un motore a scelta, ad esempio *Servizi di sintesi vocale di Google*. Nei test Google italiano suona più naturale.
  - **Voce**: *Automatica* (la migliore offline) oppure una voce precisa (con Google: Voce ITB, ITC, ITD, KDA; quelle *online* richiedono internet).
  - **Prova voce**: legge una sequenza di chiamate di esempio con i nomi inseriti.
- Se manca la voce italiana: **Impostazioni › Gestione generale › Lingua › Sintesi vocale** (o *Output sintesi vocale*) → scarica la voce italiana del motore scelto.
- **Pronuncia**: alcuni motori sbagliano parole del tennis ("primo set" letto "primo settembre", "tie-break" letto "time break"). L'app le corregge da sola (`voice/Pronunciation.kt`); le correzioni sono state verificate trascrivendo l'audio reale di Samsung e Google.
- **Registrazioni personalizzate** (la voce più naturale in assoluto: la tua o quella di un arbitro): nella cartella `Android/data/com.tennis.scoremanager/files/voice/` c'è `LEGGIMI.txt` con l'elenco delle 84 chiavi e delle frasi. Registra i file con quei nomi (`score_1_0.mp3` = "quindici zero"…), mettili in uno ZIP con le cartelle `it/` ed `en/` e usa **Importa ZIP**. Le registrazioni hanno sempre la precedenza sulla sintesi; i nomi restano letti dalla sintesi.
- **Usa file audio pre-generati** (facoltativo): con **Genera file** l'app crea una volta gli 84 file con la voce scelta (anche una voce *online*, se in quel momento c'è internet) e poi li usa al posto della sintesi continua. Suona più "a pezzi", ma è utile per portarsi offline una voce online.
- **Cassa esterna**: basta accoppiarla al telefono in Bluetooth; la voce esce sul canale multimediale (regola il volume media).

## 5. Firmware dei braccialetti (Arduino IDE su Fedora)

1. Installa Arduino IDE 2 (Flatpak `cc.arduino.IDE2` o AppImage) e dai i permessi alla porta seriale:
   ```bash
   sudo usermod -aG dialout $USER
   ```
   poi esci e rientra nella sessione.
2. **File › Preferences › Additional boards manager URLs**:
   `https://static-cdn.m5stack.com/resource/arduino/package_m5stack_index.json`
3. **Boards Manager** → cerca **M5Stack** → installa la versione **≥ 3.2.5**.
4. **Library Manager** → installa **M5Unified** (accetta "Install all" per M5GFX) e **NimBLE-Arduino** di *h2zero* (≥ 2.1).
5. **File › Open** → `firmware/TSM_Band/TSM_Band.ino` del clone (oppure `~/Arduino/TSM_Band/TSM_Band.ino` se hai usato lo script).
6. **Tools › Board › M5Stack › M5StickS3**, **Tools › Port** → `/dev/ttyACM0`.
   *(Senza il pacchetto M5Stack va bene anche "ESP32S3 Dev Module": USB CDC On Boot = Enabled, Flash Size = 8MB, Partition = 8M with spiffs.)*
7. **Upload (→)**. Se la porta non compare o il caricamento fallisce: tieni premuto a lungo il **tasto laterale** (modalità download) e riprova.
   **Dopo il caricamento**, se il display resta nero (il braccialetto è rimasto in modalità programmazione), premi **una volta** il tasto laterale: riparte con il programma nuovo.
8. All'avvio il braccialetto mostra il suo nome, es. **TSM-3FA2** (si può cambiare dall'app, vedi 6.1). Ripeti per il secondo braccialetto.

> **Firmware 2.0**: le impostazioni, *Identifica* e lo spegnimento dall'app funzionano solo con lo sketch nuovo. Carica `TSM_Band.ino` su **entrambi** i braccialetti; con un firmware vecchio l'app lo dice nel pannello delle impostazioni e il resto continua a funzionare.

## 6. Usare i braccialetti

| Tasto | Azione |
|---|---|
| **KEY1** (frontale) corto | punto a chi indossa il braccialetto (avvia anche la partita dalla pagina INIZIO PARTITA); un bip conferma |
| **KEY1** lungo (1 s) | mostra la batteria (e tiene acceso il braccialetto se sta per spegnersi per inattività) |
| **KEY2** corto | annulla l'ultimo punto (anche dal popup di fine partita); due bip più bassi |
| **KEY2** lungo (2 s) | spegne il braccialetto |
| Tasto laterale | un clic accende; doppio clic spegne (funzione hardware) |

- All'accensione lampeggia **PAIRING...** (acceso 0,35 s ogni 2 s per risparmiare). L'app si collega da sola ai braccialetti che conosce appena è aperta; quelli nuovi li trova sulla pagina 2.
- Collegato: **PAIRING OK** per 3 secondi con due bip, poi **ASSOCIATO A** + il nome del giocatore.
- A ogni punto il display si accende con il punteggio del game in grande (a sinistra il tuo, a destra l'avversario; la pallina verde indica chi serve), poi si spegne. A fine game mostra game e set.

**Spegnimento automatico** (i tempi si cambiano dall'app, 6.1):

| Situazione | Cosa fa il braccialetto | Predefinito |
|---|---|---|
| Acceso, ma nessun telefono si collega | lampeggia PAIRING, poi si spegne | **30 s** |
| Telefono perso (spento, fuori portata, Bluetooth spento, app chiusa di colpo) | lampeggia RICONNESSIONE e si ricollega da solo appena può; altrimenti si spegne | 3 min |
| Collegato ma inattivo (nessun punto, nessun messaggio) | 30 s prima avvisa con **INATTIVO · TIENI PREMUTO KEY1** e un bip, poi si spegne | 30 min |
| Partita conclusa (confermata sul telefono) o **Esci** dall'app | mostra FINE PARTITA / APP CHIUSA e si spegne subito | attivo (si può disattivare, 7.2) |
| Batteria scarica (sotto 3,30 V per due letture di fila) | mostra BATTERIA SCARICA e si spegne, per non restare acceso a metà | sempre |

- Negli ultimi 10 secondi prima dello spegnimento senza telefono mostra **SPEGNIMENTO · 8s - PREMI UN TASTO** con un bip: un tasto qualsiasi rimanda lo spegnimento da capo.
- Quando si spegne da solo lo dice al telefono: in partita il riquadro arancione mostra *BRACCIALETTO 1 SPENTO (INATTIVO)*, *(BATTERIA SCARICA)*…

**Durata della batteria**: la voce che pesa di più è la scheda ESP32-S3 col Bluetooth collegato (circa 35 mA): il core Arduino è compilato senza il risparmio energetico profondo (light sleep) quando il Bluetooth è acceso, quindi non si può scendere molto sotto. Display, luminosità e bip aggiungono pochi mA. Con la batteria da 250 mAh la stima è **circa 7 ore da carica piena**, più di qualsiasi partita al meglio dei tre set. Il pannello delle impostazioni mostra la stima in tempo reale e, dopo 20 minuti di uso, la corregge con il consumo **misurato** su quel braccialetto. Il registro completo è in `files/battery_log.csv` dell'app.

Il resto del risparmio: CPU a 80 MHz, display spento quando non serve, Bluetooth a basso consumo (la radio si sveglia 2-3 volte al secondo, il tasto parte comunque entro ~120 ms), cicalino acceso solo durante i bip, microfono/IMU/5V spenti.

### 6.1 Impostazioni del braccialetto

Dalla pagina 2 (**Impostazioni** sotto il braccialetto di ciascun giocatore) o durante la partita (tocca **G1**/**G2** in alto, oppure l'icona dei cursori). Si salvano **nel braccialetto** e restano anche spegnendolo; a ogni modifica il braccialetto mostra il nome con *IMPOSTAZIONI OK*.

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

Sotto c'è la **stima dell'autonomia** (da carica piena e con la carica attuale) con il consumo diviso per voce: cambia mentre muovi i cursori, prima ancora di confermare. Poi **Identifica**, **Spegni** e **Copia sull'altro braccialetto** (stesse impostazioni, il nome resta il suo).

## 7. Come si usa l'app

1. **Nuova partita** (facoltativa): circolo, campo, singolare/doppio, nomi (nel doppio due nomi per squadra). **Avanti**.
2. **Modalità e regole**:
   - *Arbitro* o *Braccialetti*. Con i braccialetti sono obbligatori Bluetooth acceso, permesso posizione e posizione attiva: le righe dei **Requisiti** si aggiornano in tempo reale (anche se spegni Bluetooth o posizione dalla tendina) e diventano rosse con il pulsante per sistemarle.
   - La **ricerca è automatica e continua** finché la pagina è aperta: accendi i braccialetti e vanno da soli nei posti liberi (prima Giocatore 1, poi Giocatore 2). **Identifica** fa lampeggiare quel braccialetto nel colore del giocatore (giallo o rosso) con dei bip, così vedi subito quale hai in mano; **Scambia G1 ↔ G2** li inverte senza scollegarli. Dal menu a tendina puoi sempre sceglierne un altro o *Nessuno* (quello tolto a mano non viene rimesso dalla ricerca). Un braccialetto non può stare su due giocatori.
   - **Spegni i braccialetti a fine partita e all'uscita** (attivo di serie): alla conferma di *Partita conclusa* e con *Esci* i braccialetti si spengono invece di aspettare l'inattività.
   - In modalità arbitro, premendo **Avanti** senza posizione compare l'invito ad attivarla (altrimenti il luogo non sarà nel riepilogo).
   - Lingua IT/EN, voce on/off, formato (*3 set con tie-break a 7* oppure *2 set + super tie-break a 10*), No-Ad.
   - **Sorteggio**: la moneta gira e indica chi vince; imposti chi serve e i lati del campo **visti dal giudice di sedia** (schema del campo con **Inverti lati**). Nel doppio scegli anche chi serve per primo in ogni squadra.
3. **INIZIO PARTITA** lampeggia: premi il pulsante o KEY1 di un braccialetto. La voce dice *"Primo set" · "[nome] al servizio" · "gioco"* con 2 secondi tra le frasi; il **Match Time** parte su "gioco".
4. **Partita**: in alto a sinistra il tempo partita, a destra il countdown (**Shot Clock** 25 s, **Changeover Time**, **Set Break Time**; rosso negli ultimi 5 s). Il riquadro arancione si accende 5 s per *cambio campo, tie-break, set point, match point, palla break…*. I due tasti quadrati (giallo = Giocatore 1, rosso = Giocatore 2) stanno dal lato in cui si trovano davvero i giocatori e si scambiano a ogni cambio campo; sotto chi serve compare **On Serve**. Sotto: *Annulla punto*, *Sospendi/Riprendi*, audio (icona dell'altoparlante, barrata = spento), *Nuova partita*, **Esci**. In modalità braccialetti, sotto i tempi ci sono **G1**/**G2** con batteria e autonomia: toccandoli si aprono le impostazioni del braccialetto.
5. All'ultimo punto compare il popup **Partita conclusa / Annulla ultimo punto**. "Partita conclusa" si conferma **solo dal telefono**.
6. **Riepilogo**: vincitore, nomi, punteggio set per set con i punti del tie-break, durata, ora di inizio e fine, data, circolo, campo, luogo, formato, punti e game vinti. Pulsanti **Salva nello storico** (nome file + cartella a scelta + formati .txt/.json/.png), **Condividi** (immagine 1080×1350 + testo per WhatsApp/Instagram/…), **Nuova partita**, **Esci**.

**Uscire**: **Esci** (dalla partita chiede conferma, e c'è anche nel riepilogo) chiude davvero l'app; lo stesso se la togli dalle app recenti. Alla riapertura riparte dalla prima pagina.

**Salvataggi**: la partita si salva da sola a ogni punto. *Sospendi* ferma i tempi; se esci, se il telefono si spegne o l'app viene chiusa, la partita si ritrova in **Riprendi partita sospesa** (pagina 3) e riparte con *Riprendi*. *Annulla punto* ricalcola tutto dall'inizio, quindi funziona anche dopo la fine di un game, di un set o della partita.

## 8. Regole applicate (ITF) e scelte concordate

- **Game**: 0-15-30-40, parità, vantaggio, gioco. **No-Ad**: sul 40-40 punto decisivo ("parità, punto decisivo").
- **Set**: 6 game con 2 di scarto (7-5); sul **6-6 tie-break**.
- **Tie-break**: a 7 con 2 di scarto; chi è di turno serve il 1° punto, poi 2 punti a testa; cambio campo **ogni 6 punti** e alla fine; chi ha servito per primo nel tie-break **riceve** nel primo game del set successivo.
- **Match tie-break** (formato 2 set): sull'1-1 si gioca a 10 punti con 2 di scarto.
- **Cambio campo** (regola ITF 10): dopo il 1°, 3°, 5°… game di ogni set. A fine set si cambia solo se il set ha un numero dispari di game (6-3, 7-6); altrimenti (6-4) si cambia dopo il primo game del set successivo. Il tie-break conta come un game.
- **Tempi**: shot clock 25 s tra i punti; changeover 90 s; set break 120 s a fine set. In più, come richiesto (non è regola ITF): **30 s** per spostarsi dopo il 1° game di ogni set, a ogni cambio campo nel tie-break e sul 6-6; finita qualsiasi pausa parte lo shot clock.
- **Doppio**: rotazione del servizio A1-B1-A2-B2 per tutto il set, anche nel tie-break; all'inizio di ogni set l'app chiede l'ordine (si può cambiare, come da regolamento).
- **Chiamate**: punteggio letto da chi serve ("quindici zero", "zero quaranta", "quindici pari", "parità", "vantaggio Rossi"); a fine game "gioco Rossi, Rossi conduce tre giochi a due" / "due giochi pari" + "cambio campo" quando si cambia; sul 6-6 "gioco Rossi, sei giochi pari, tie-break"; nel tie-break si legge da chi conduce ("tre a uno Rossi", "sei pari"); fine set "gioco Rossi, Rossi conduce un set a zero" / "un set pari"; fine partita "gioco, set, partita Rossi, sei quattro, tre sei, sette cinque"; "correzione" + punteggio quando si annulla un punto.

**Punti che ho sistemato rispetto alla richiesta originale, secondo ITF:**
1. **Sul 6-6 non si cambia campo** (sono 12 game, numero pari): l'app fa la pausa di 30 s e dice "tie-break", ma non dice "cambio campo" e non scambia i tasti. Il primo cambio è dopo 6 punti del tie-break.
2. Il cambio a fine set dipende dal numero di game del set (vedi sopra), non avviene sempre.
3. A fine set vinto senza tie-break uso la stessa formula del tie-break ("gioco Rossi, Rossi conduce un set a zero"). Molti arbitri dicono "gioco e set Rossi, sei quattro": si può cambiare facilmente in `Calls.kt`.
4. All'1-1 nel formato con super tie-break la voce aggiunge "super tie-break".

## 9. Dove mettere le mani

| Cosa | File |
|---|---|
| Regole del punteggio | `model/ScoreEngine.kt` (+ test in `app/src/test`) |
| Frasi e chiamate vocali | `voice/Calls.kt` |
| Tempi (25/90/120/30 s), messaggi, flusso partita | `MatchController.kt` |
| Testi dell'app IT/EN | `ui/Strings.kt` |
| Protocollo Bluetooth (UUID, messaggi, impostazioni) | `ble/BandProtocol.kt` e in cima a `TSM_Band.ino` |
| Stima dell'autonomia | `ble/BatteryModel.kt` |
| Pannello impostazioni braccialetto | `ui/BandSettingsPanel.kt` |
| Grafica | `ui/screens/*.kt`, colori in `ui/Theme.kt` |


## 10. Problemi comuni

- **Braccialetto non trovato**: Bluetooth e posizione attivi (righe verdi)? Il braccialetto sta lampeggiando PAIRING? (Se è già collegato a un altro telefono non si vede.) Se nel frattempo si è spento da solo (30 s), riaccendilo con un clic sul tasto laterale.
- **Nel pannello impostazioni c'è "firmware senza impostazioni"**: quel braccialetto ha ancora lo sketch vecchio, ricaricalo (capitolo 5).
- **La voce non parla o legge male**: volume multimediale, *Audio On*, voce italiana installata; prova un altro motore o un'altra voce in *Audio e voce*.
- **Schermo spento durante la partita**: in modalità braccialetti un servizio in primo piano (notifica "Partita in corso") tiene attivi Bluetooth, voce e cronometri; in modalità arbitro lo schermo resta acceso.
- **Sync Gradle fallita per il JDK**: imposta Gradle JDK = jbr-21 (punto 2.6).
