#!/bin/sh
# SPDX-FileCopyrightText: 2026 Stefano Spagnolo
# SPDX-License-Identifier: GPL-3.0-or-later

# Installa (o aggiorna) il server del circolo TSM su Raspberry Pi OS, Debian o Ubuntu:
#   sudo ./install.sh            installa o aggiorna (i dati restano)
#   sudo ./install.sh --port N   con un'altra porta (8080 di solito)
#   sudo ./install.sh --remove   lo toglie (i dati in /var/lib/tsm-server restano)
# Va lanciato dalla cartella del kit, accanto a tsm-server.jar.
set -eu

DIR=$(cd "$(dirname "$0")" && pwd)
DATA=/var/lib/tsm-server
PORT=""
REMOVE=0
while [ $# -gt 0 ]; do
    case "$1" in
        --port) PORT="${2:-}"; shift 2 ;;
        --remove) REMOVE=1; shift ;;
        *) shift ;;
    esac
done

# I messaggi nella lingua del sistema (italiano, inglese, francese, tedesco, spagnolo, portoghese).
L=$(printf '%s' "${LC_ALL:-${LC_MESSAGES:-${LANG:-en}}}" | cut -c1-2)
msg() {
    case "$L:$1" in
        it:root) echo "Serve sudo: sudo $0" ;;
        fr:root) echo "Il faut sudo : sudo $0" ;;
        de:root) echo "Bitte mit sudo starten: sudo $0" ;;
        es:root) echo "Hace falta sudo: sudo $0" ;;
        pt:root) echo "É preciso sudo: sudo $0" ;;
        *:root) echo "Run it with sudo: sudo $0" ;;
        it:jar) echo "Manca tsm-server.jar accanto allo script." ;;
        fr:jar) echo "tsm-server.jar manque à côté du script." ;;
        de:jar) echo "tsm-server.jar fehlt neben dem Skript." ;;
        es:jar) echo "Falta tsm-server.jar junto al script." ;;
        pt:jar) echo "Falta o tsm-server.jar ao lado do script." ;;
        *:jar) echo "tsm-server.jar is missing next to the script." ;;
        it:java) echo "Installo Java (può volerci qualche minuto)…" ;;
        fr:java) echo "Installation de Java (quelques minutes)…" ;;
        de:java) echo "Java wird installiert (das kann ein paar Minuten dauern)…" ;;
        es:java) echo "Instalando Java (puede tardar unos minutos)…" ;;
        pt:java) echo "A instalar o Java (pode demorar alguns minutos)…" ;;
        *:java) echo "Installing Java (this can take a few minutes)…" ;;
        it:start) echo "Avvio il server…" ;;
        fr:start) echo "Démarrage du serveur…" ;;
        de:start) echo "Server wird gestartet…" ;;
        es:start) echo "Arrancando el servidor…" ;;
        pt:start) echo "A arrancar o servidor…" ;;
        *:start) echo "Starting the server…" ;;
        it:ok) echo "Server del circolo pronto. Nell'app: Planning dei campi › Orari e prezzi › Server del circolo." ;;
        fr:ok) echo "Serveur du club prêt. Dans l'app : Planning des terrains › Horaires et tarifs › Serveur du club." ;;
        de:ok) echo "Vereinsserver bereit. In der App: Platzbelegung › Zeiten und Preise › Vereinsserver." ;;
        es:ok) echo "Servidor del club listo. En la app: Planning de pistas › Horarios y precios › Servidor del club." ;;
        pt:ok) echo "Servidor do clube pronto. Na app: Agenda das quadras › Horários e preços › Servidor do clube." ;;
        *:ok) echo "Club server ready. In the app: Court planner › Hours and prices › Club server." ;;
        it:addr) echo "Indirizzo del server:" ;;
        fr:addr) echo "Adresse du serveur :" ;;
        de:addr) echo "Serveradresse:" ;;
        es:addr) echo "Dirección del servidor:" ;;
        pt:addr) echo "Endereço do servidor:" ;;
        *:addr) echo "Server address:" ;;
        it:code) echo "Codice di abbinamento:" ;;
        fr:code) echo "Code d'appairage :" ;;
        de:code) echo "Kopplungscode:" ;;
        es:code) echo "Código de emparejamiento:" ;;
        pt:code) echo "Código de emparelhamento:" ;;
        *:code) echo "Pairing code:" ;;
        it:paired) echo "La segreteria è già abbinata (nuovo codice: sudo ./reset-admin.sh)." ;;
        fr:paired) echo "Le secrétariat est déjà appairé (nouveau code : sudo ./reset-admin.sh)." ;;
        de:paired) echo "Das Büro ist schon gekoppelt (neuer Code: sudo ./reset-admin.sh)." ;;
        es:paired) echo "La secretaría ya está emparejada (código nuevo: sudo ./reset-admin.sh)." ;;
        pt:paired) echo "A secretaria já está emparelhada (novo código: sudo ./reset-admin.sh)." ;;
        *:paired) echo "The office is already paired (new code: sudo ./reset-admin.sh)." ;;
        it:fail) echo "Il server non è partito: guarda con  journalctl -u tsm-server -n 50" ;;
        fr:fail) echo "Le serveur n'a pas démarré : voir  journalctl -u tsm-server -n 50" ;;
        de:fail) echo "Der Server ist nicht gestartet: siehe  journalctl -u tsm-server -n 50" ;;
        es:fail) echo "El servidor no ha arrancado: mira  journalctl -u tsm-server -n 50" ;;
        pt:fail) echo "O servidor não arrancou: vê  journalctl -u tsm-server -n 50" ;;
        *:fail) echo "The server did not start: see  journalctl -u tsm-server -n 50" ;;
        it:removed) echo "Server tolto. I dati restano in $DATA (per toglierli: sudo rm -r $DATA)." ;;
        fr:removed) echo "Serveur retiré. Les données restent dans $DATA (pour les effacer : sudo rm -r $DATA)." ;;
        de:removed) echo "Server entfernt. Die Daten bleiben in $DATA (löschen: sudo rm -r $DATA)." ;;
        es:removed) echo "Servidor quitado. Los datos quedan en $DATA (para borrarlos: sudo rm -r $DATA)." ;;
        pt:removed) echo "Servidor removido. Os dados ficam em $DATA (para os apagar: sudo rm -r $DATA)." ;;
        *:removed) echo "Server removed. The data stays in $DATA (to delete it: sudo rm -r $DATA)." ;;
    esac
}

[ "$(id -u)" -eq 0 ] || { msg root; exit 1; }

if [ "$REMOVE" -eq 1 ]; then
    systemctl disable --now tsm-server 2>/dev/null || true
    rm -f /etc/systemd/system/tsm-server.service
    rm -rf /opt/tsm-server
    systemctl daemon-reload
    msg removed
    exit 0
fi

[ -f "$DIR/tsm-server.jar" ] || { msg jar; exit 1; }

# Java: Debian 12 (bookworm) ha la 17, Debian 13 (trixie) la 21; il server va con entrambe.
if ! command -v java >/dev/null 2>&1; then
    msg java
    apt-get update
    DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends default-jre-headless
fi

id tsm >/dev/null 2>&1 || useradd --system --home-dir "$DATA" --no-create-home --shell /usr/sbin/nologin tsm
install -d -m 755 /opt/tsm-server
install -m 644 "$DIR/tsm-server.jar" /opt/tsm-server/tsm-server.jar
install -m 644 "$DIR/tsm-server.service" /etc/systemd/system/tsm-server.service
[ -f /etc/default/tsm-server ] || install -m 644 "$DIR/tsm-server.default" /etc/default/tsm-server
if [ -n "$PORT" ]; then sed -i "s/^TSM_PORT=.*/TSM_PORT=$PORT/" /etc/default/tsm-server; fi
PORT=$(sed -n 's/^TSM_PORT=//p' /etc/default/tsm-server)

msg start
systemctl daemon-reload
systemctl enable tsm-server >/dev/null 2>&1
systemctl restart tsm-server

# Sul Pi Zero Java parte in una ventina di secondi: si aspetta che il server risponda.
i=0
until wget -q -O /dev/null "http://127.0.0.1:$PORT/api/v1/info" 2>/dev/null || curl -fs -o /dev/null "http://127.0.0.1:$PORT/api/v1/info" 2>/dev/null; do
    i=$((i + 1))
    if [ $i -gt 90 ] || ! systemctl is-active --quiet tsm-server; then msg fail; exit 1; fi
    sleep 1
done

echo
msg ok
msg addr
for a in $(hostname -I 2>/dev/null); do
    case "$a" in *:*) ;; *) echo "  http://$a:$PORT" ;; esac
done
echo "  http://$(hostname).local:$PORT"
if [ -f "$DATA/setup-code.txt" ]; then
    msg code
    echo "  $(cat "$DATA/setup-code.txt")"
else
    msg paired
fi
