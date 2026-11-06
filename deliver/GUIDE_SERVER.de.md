# Vereinsserver — Anleitung für das Büro

[Italiano](GUIDA_SERVER.md) · [English](GUIDE_SERVER.en.md) · [Français](GUIDE_SERVER.fr.md) · **Deutsch** · [Español](GUIDE_SERVER.es.md) · [Português](GUIDE_SERVER.pt.md)

Der Vereinsserver ist freiwillig: Er wird nur gebraucht, wenn die Spieler die Plätze selbst buchen sollen, vom eigenen Telefon aus. Ohne Server funktioniert die App genauso: Die Buchungen trägt das Büro ein.

Es gibt drei Arten, ihn zu nutzen. Wählt die passende:

| Art | Die Spieler buchen… | Was man braucht |
|---|---|---|
| **A. Nur im Verein** | wenn sie im WLAN des Vereins sind | den eingeschalteten Server-PC |
| **B. Aus dem Internet mit Tailscale** | von überall | dazu ein kostenloses Tailscale-Konto (10 Minuten, einmalig) |
| **C. Aus dem Internet über den Router** | von überall | dazu jemanden, der den Router des Vereins einstellen kann |

Unser Rat: Beginnt mit Art **A**. Wenn ihr Buchungen von zu Hause wollt, wechselt zu **B**: Am Router muss niemand etwas ändern.

---

## 1. Den Server starten (für alle Arten)

Ihr braucht einen alten PC (oder einen von einem Techniker vorbereiteten Raspberry Pi) und den **USB-Stick TSM-SERVER**.

1. Verbindet den PC mit dem Vereinsnetz, am besten per Kabel.
2. Steckt den Stick ein und schaltet den PC ein. Startet Windows, dann neu starten, die Taste für das Startmenü drücken (meist F12, F9 oder Esc: Der Bildschirm zeigt sie beim Einschalten an) und den USB-Stick wählen.
3. Nach etwa einer Minute erscheint auf dem Bildschirm:

   ```
   TSM – Server del circolo / Club server
   http://192.168.1.50:8080
   Codice di abbinamento / Pairing code: 123456
   ```

   Eure Zahlen sind andere. Schreibt die **Adresse** (die Zeile `http://…`) und den **Code** auf.
4. Lasst den PC eingeschaltet. Anmelden oder etwas eintippen ist nicht nötig: Der Server arbeitet von selbst. Schaltet ihr den PC aus, können die Spieler nicht buchen, bis er wieder läuft; die Daten bleiben auf dem Stick.

Steht auf dem Bildschirm **WARNING: no persistence partition**, wurde der Stick nicht richtig vorbereitet: Die Daten gingen beim Ausschalten verloren. Lasst ihn neu machen.

## 2. Das Telefon des Büros koppeln (einmalig)

1. Mit dem Telefon im **WLAN des Vereins** TSM öffnen und zu **Plätze › Vereinsserver** gehen.
2. Unter **Serveradresse** die Adresse vom PC-Bildschirm eintragen, unter **Kopplungscode** den Code.
3. **Koppeln** antippen. Der Code gilt nur einmal: Auf dem PC-Bildschirm steht jetzt **Segreteria abbinata / Office paired**.

Wechselt ihr eines Tages das Telefon, braucht ihr einen neuen Code: Bittet den Techniker, sich am Server-PC anzumelden und `sudo tsm-reset-admin` einzugeben.

## 3. Art A: Buchungen im Verein

Weiterhin unter **Plätze › Vereinsserver**:

1. **Online-Buchung offen** einschalten.
2. Die **Online buchbaren Plätze** wählen und, wenn ihr wollt, die Regeln (wie viele Tage im Voraus, wie viele Buchungen pro Spieler…).
3. **Jetzt senden** antippen: Der Belegungsplan geht an den Server.
4. Für jeden Spieler: seine Karte öffnen und **Einladung zum Online-Buchen** antippen. Der Spieler scannt den QR-Code mit der Kamera und bucht über die Seite des Vereins (auf dem iPhone: Teilen › Zum Home-Bildschirm, dann öffnet sie sich wie eine App).

Die Buchungen der Spieler landen von selbst im Plan des Büros, markiert mit **online**.

## 4. Art B: aus dem Internet mit Tailscale

Tailscale ist ein Dienst, der den Server „von innen" mit dem Internet verbindet, ohne Ports am Router zu öffnen. Er funktioniert auch bei Anschlüssen ohne öffentliche Adresse (viele LTE/5G- und Funkanschlüsse). Ihr braucht ein kostenloses Konto.

**Vorher:** Der Server muss Internet haben (das Vereinsnetz kommt ins Internet), und das Telefon des Büros muss schon gekoppelt sein (Kapitel 2).

1. Unter **Plätze › Vereinsserver**, im Feld **Internetzugang ohne Router-Einstellungen**, **Einschalten** antippen.
2. Ein paar Minuten warten: Beim ersten Mal lädt der Server Tailscale herunter (**Der Server bereitet die Verbindung vor**).
3. **Tailscale öffnen** erscheint: antippen. Die Seite von Tailscale öffnet sich im Browser:
   - gibt es das Konto schon, meldet euch an;
   - sonst legt eines an: Man meldet sich mit einem vorhandenen Konto an (zum Beispiel Google, Microsoft oder Apple). Besser das Konto des Vereins als das private Konto der Person im Büro.
   - Tailscale fragt, ob ein Gerät namens **tsm-circolo** verbunden werden soll: bestätigen.
4. Zurück zu TSM. Bald erscheint **Letzter Schritt: Öffne Tailscale und bestätige die Aktivierung von Funnel**. Noch einmal **Tailscale öffnen** antippen und bestätigen (Funnel ist die Funktion, die den Server aus dem Internet erreichbar macht).
5. Zurück zu TSM: **Aktiv: https://tsm-circolo.….ts.net** erscheint. Das ist die Adresse aus dem Internet, und TSM trägt sie von selbst unter **Adresse aus dem Internet** ein. Ab jetzt funktionieren die Einladungen auch von zu Hause.
6. **Gleich erledigen**: Alle sechs Monate will Tailscale eine neue Anmeldung. Um das zu vermeiden, an einem Computer **login.tailscale.com** öffnen, unter **Machines** **tsm-circolo** suchen, das Menü (die drei Punkte) öffnen und **Disable key expiry** wählen.

Den Zugang aus dem Internet abschalten: **Ausschalten**. Die Buchungen im Verein (Art A) funktionieren weiter.

Solange der Zugang aktiv ist, zeigt der Bildschirm des Server-PCs auch die Zeile **Da internet / From the internet**.

Der kostenlose Tarif von Tailscale ist für private und kleine Nutzung gedacht: Werft vor dem Einsatz im Verein einen Blick auf die Bedingungen auf deren Website.

**Wenn etwas nicht klappt**
- **Das hat nicht geklappt: Prüfe, ob der Server Internet hat**: Der Server-PC kommt nicht ins Internet. Kabel oder Vereins-WLAN prüfen und erneut **Einschalten** antippen.
- **Aktualisiere dafür den Server**: Der Stick ist alt, ohne Tailscale. Lasst ihn mit dem neuen Abbild neu machen.
- Hört die Adresse aus dem Internet nach Monaten auf zu funktionieren, öffnet **Plätze › Vereinsserver**: Erscheint wieder **Tailscale öffnen**, meldet euch mit eurem Konto neu an.

## 5. Art C: aus dem Internet über den Router

Das ist der klassische Weg: Er hängt nicht von Tailscale ab, aber ihr braucht jemanden, der den Router des Vereins einstellen kann, und der Anschluss muss eine öffentliche Adresse haben.

Für den Techniker:
1. Einen kostenlosen Namen auf **duckdns.org** (z. B. `tcrossi.duckdns.org`) und sein Token.
2. Am Router die Ports **80** und **443** an den Server-PC weitergeleitet.
3. Am Server-PC, angemeldet mit Benutzer `user` und Passwort `live` (mit `passwd` ändern):

   ```
   sudo /opt/tsm-server/kit/setup-https.sh tcrossi.duckdns.org --duckdns TOKEN
   ```

4. In TSM unter **Adresse aus dem Internet** `https://tcrossi.duckdns.org` eintragen.

Ob der Anschluss eine öffentliche Adresse hat: Vergleicht die WAN-Adresse auf der Seite des Routers mit der einer Website wie „Wie ist meine IP". Sind sie verschieden oder beginnt die des Routers mit `100.64`–`100.127`, funktioniert Art C nicht: Nehmt **B**.

## 6. Merkzettel für das Büro

- Der Server-PC bleibt eingeschaltet; der Stick wird nicht abgezogen.
- Ab und zu unter **Plätze › Sicherung des Büros** eine Sicherung machen: Eine Kopie liegt auch auf dem Server.
- Um die Buchungen eine Zeit lang zu stoppen (Wartung, Schließung), **Online-Buchung offen** ausschalten: Die Spieler sehen den Plan, buchen aber nicht.
- Ein Spieler, der nicht mehr buchen soll: in seiner Karte **Online-Zugang entfernen**.
