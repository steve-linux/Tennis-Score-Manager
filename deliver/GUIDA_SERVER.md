# Server del circolo — guida per la segreteria

**Italiano** · [English](GUIDE_SERVER.en.md) · [Français](GUIDE_SERVER.fr.md) · [Deutsch](GUIDE_SERVER.de.md) · [Español](GUIDE_SERVER.es.md) · [Português](GUIDE_SERVER.pt.md)

Il server del circolo è facoltativo: serve solo se volete che i giocatori prenotino i campi da soli, dal loro telefono. Senza server l'app funziona lo stesso: le prenotazioni le segna la segreteria.

Ci sono tre modi di usarlo. Scegliete quello che vi serve:

| Modo | I giocatori prenotano… | Cosa serve |
|---|---|---|
| **A. Solo al circolo** | quando sono collegati al Wi-Fi del circolo | il PC del server acceso |
| **B. Da internet con Tailscale** | da qualsiasi posto | in più, un account gratuito Tailscale (10 minuti, una volta sola) |
| **C. Da internet con il router** | da qualsiasi posto | in più, qualcuno che sappia configurare il router del circolo |

Il consiglio: partite dal modo **A**. Se volete le prenotazioni da casa, passate al **B**: non bisogna toccare il router.

---

## 1. Accendere il server (vale per tutti i modi)

Vi serve un vecchio PC (o un Raspberry Pi preparato da un tecnico) e la **chiavetta TSM-SERVER**.

1. Collegate il PC alla rete del circolo, meglio col cavo. Su un portatile senza cavo va bene anche il Wi-Fi: la prima volta, quando lo schermo dice **Nessuna rete**, scrivete `user`, Invio, `live`, Invio, poi `sudo nmtui` (dopo l'accesso lo schermo ricorda tutti i comandi); scegliete **Attiva una connessione** (o *Activate a connection*), la rete del circolo e scrivete la sua password. Resta salvata sulla chiavetta: dalle volte dopo si collega da solo. Per uscire: **Esci**, poi `exit`.
2. Inserite la chiavetta e accendete il PC. Se parte Windows, al riavvio premete il tasto del menu di avvio (di solito F12, F9 o Esc: lo dice lo schermo all'accensione) e scegliete la chiavetta USB.
3. Dopo circa un minuto sullo schermo compare:

   ```
   TSM – Server del circolo / Club server
   http://192.168.1.50:8080
   Codice di abbinamento / Pairing code: 123456
   ```

   I numeri saranno diversi. Scrivete su un foglio l'**indirizzo** (la riga `http://…`) e il **codice**.
4. Lasciate il PC acceso. Non serve entrare né scrivere niente: il server lavora da solo. Se spegnete il PC, i giocatori non prenotano finché non lo riaccendete; i dati restano sulla chiavetta.

Se lo schermo dice **ATTENZIONE: nessuna partizione persistence**, la chiavetta non è stata preparata bene: i dati si perderebbero allo spegnimento. Fatela rifare.

## 2. Abbinare il telefono della segreteria (una volta sola)

1. Con il telefono collegato al **Wi-Fi del circolo**, aprite TSM e andate in **Campi › Server del circolo**.
2. In **Indirizzo del server** scrivete l'indirizzo letto sullo schermo del PC, in **Codice di abbinamento** il codice.
3. Toccate **Abbina**. Il codice vale una volta sola: sullo schermo del PC adesso c'è scritto **Segreteria abbinata**.

Se un giorno cambiate telefono, serve un codice nuovo: chiedete al tecnico di entrare sul PC del server e scrivere `sudo tsm-reset-admin`.

## 3. Modo A: prenotazioni al circolo

Sempre in **Campi › Server del circolo**:

1. Accendete **Prenotazioni online aperte**.
2. Scegliete i **Campi prenotabili online** e, se volete, le regole (quanti giorni avanti, quante prenotazioni per giocatore…).
3. Toccate **Invia ora**: il planning arriva sul server.
4. Per ogni giocatore: aprite la sua scheda e toccate **Invito per prenotare online**. Il giocatore inquadra il QR con la fotocamera e prenota dalla pagina del circolo (su iPhone: Condividi › Aggiungi alla schermata Home, e la trova come un'app).

Dalla segreteria sul PC è uguale: **Soci**, aprite il socio e cliccate **Invito per prenotare online**. Oltre al QR ci sono **Copia il link**, **Manda con WhatsApp** e **Manda per email**: il messaggio si apre già scritto e lo mandate voi.

Le prenotazioni dei giocatori arrivano da sole nel planning della segreteria, segnate **online**.

## 4. Modo B: da internet con Tailscale

Tailscale è un servizio che collega il server a internet "dall'interno", senza aprire porte sul router. Funziona anche con le connessioni che non hanno un indirizzo pubblico (molte linee FWA e 4G/5G). Serve un account gratuito.

**Prima:** il server deve avere internet (la rete del circolo esce su internet) e il telefono della segreteria deve essere già abbinato (capitolo 2).

1. In **Campi › Server del circolo**, nel riquadro **Accesso da internet senza toccare il router**, toccate **Attiva**.
2. Aspettate qualche minuto: la prima volta il server scarica Tailscale (**Il server sta preparando il collegamento**).
3. Compare **Apri Tailscale**: toccatelo. Si apre la pagina di Tailscale nel browser:
   - se l'account c'è già, entrate;
   - se no, createlo: si entra con un account che avete già (per esempio Google, Microsoft o Apple). Conviene usare l'account del circolo, non quello personale di chi fa la segreteria.
   - Tailscale chiede di collegare un dispositivo chiamato **tsm-circolo**: confermate.
4. Tornate su TSM. Dopo poco compare **Ultimo passo: apri Tailscale e conferma l'attivazione di Funnel**. Toccate di nuovo **Apri Tailscale** e confermate (Funnel è la funzione che rende il server raggiungibile da internet).
5. Tornate su TSM: compare **Attivo: https://tsm-circolo.….ts.net**. Quello è l'indirizzo da internet, e TSM lo mette da solo in **Indirizzo da internet**. Da adesso gli inviti funzionano anche da casa.
6. **Una cosa da fare subito**: Tailscale ogni sei mesi chiede di rientrare. Per evitarlo, sul computer andate su **login.tailscale.com**, nella sezione **Machines** trovate **tsm-circolo**, aprite il menu (i tre puntini) e scegliete **Disable key expiry**.

Per spegnere l'accesso da internet: **Disattiva**. Le prenotazioni al circolo (modo A) continuano a funzionare.

Sullo schermo del PC del server, quando l'accesso è attivo, c'è anche la riga **Da internet / From the internet**.

Il piano gratuito di Tailscale è pensato per l'uso personale e piccolo: prima di usarlo per il circolo, date un'occhiata alle condizioni sul loro sito.

**Se qualcosa non va**
- **Non è andata: controlla che il server abbia internet**: il PC del server non esce su internet. Controllate il cavo o il Wi-Fi del circolo e toccate di nuovo **Attiva**.
- **Per usarlo aggiorna il server**: la chiavetta è vecchia, senza Tailscale. Fatela rifare con l'immagine nuova.
- Se dopo mesi l'indirizzo da internet smette di andare, aprite **Campi › Server del circolo**: se compare di nuovo **Apri Tailscale**, rientrate col vostro account.

## 5. Modo C: da internet con il router

È il modo classico: non dipende da Tailscale, ma serve qualcuno che sappia configurare il router del circolo e che la linea abbia un indirizzo pubblico.

Da passare al tecnico:
1. Un nome gratuito su **duckdns.org** (es. `tcrossi.duckdns.org`) e il suo token.
2. Sul router, le porte **80** e **443** inoltrate verso il PC del server.
3. Sul PC del server, entrando con utente `user` e password `live` (da cambiare con `passwd`):

   ```
   sudo /opt/tsm-server/kit/setup-https.sh tcrossi.duckdns.org --duckdns TOKEN
   ```

4. In TSM, in **Indirizzo da internet**, scrivete `https://tcrossi.duckdns.org`.

Per sapere se la linea ha un indirizzo pubblico: confrontate l'indirizzo WAN mostrato dalla pagina del router con quello di un sito tipo "qual è il mio IP". Se sono diversi, o quello del router comincia con `100.64`–`100.127`, il modo C non funziona: usate il **B**.

## 6. Promemoria per la segreteria

- Il PC del server resta acceso; la chiavetta non va tolta.
- Ogni tanto, da **Campi › Backup della segreteria**, fate un backup: una copia sta anche sul server.
- Se volete fermare le prenotazioni per un po' (manutenzione, chiusura), spegnete **Prenotazioni online aperte**: i giocatori vedono il planning ma non prenotano.
- Un giocatore che non deve più prenotare: nella sua scheda, **Togli l'accesso online**.
