#!/bin/sh
# SPDX-FileCopyrightText: 2026 Stefano Spagnolo
# SPDX-License-Identifier: GPL-3.0-or-later

# Il server del circolo anche da internet, in HTTPS (serve anche per installare la pagina su iPhone):
#   sudo ./setup-https.sh tcprova.duckdns.org --duckdns IL-TUO-TOKEN [--email tu@esempio.it]
#   sudo ./setup-https.sh prenota.tcprova.it [--email …]     (un dominio tuo, già puntato all'IP del circolo)
#   sudo ./setup-https.sh --remove
# Caddy prende da solo il certificato gratuito di Let's Encrypt e lo rinnova. Sul router del circolo vanno
# inoltrate al Pi le porte TCP 80 e 443. Dalla rete del circolo il server resta anche su http://…:8080.
set -eu

DIR=$(cd "$(dirname "$0")" && pwd)
DOMAIN=""; TOKEN=""; EMAIL=""; REMOVE=0
while [ $# -gt 0 ]; do
    case "$1" in
        --duckdns) TOKEN="${2:-}"; shift 2 ;;
        --email) EMAIL="${2:-}"; shift 2 ;;
        --remove) REMOVE=1; shift ;;
        -*) shift ;;
        *) DOMAIN="$1"; shift ;;
    esac
done

L=$(printf '%s' "${LC_ALL:-${LC_MESSAGES:-${LANG:-en}}}" | cut -c1-2)
msg() {
    case "$L:$1" in
        it:usage) echo "Uso: sudo $0 nome.duckdns.org --duckdns TOKEN   (oppure il tuo dominio)" ;;
        fr:usage) echo "Usage : sudo $0 nom.duckdns.org --duckdns JETON   (ou votre domaine)" ;;
        de:usage) echo "Aufruf: sudo $0 name.duckdns.org --duckdns TOKEN   (oder deine Domain)" ;;
        es:usage) echo "Uso: sudo $0 nombre.duckdns.org --duckdns TOKEN   (o tu dominio)" ;;
        pt:usage) echo "Uso: sudo $0 nome.duckdns.org --duckdns TOKEN   (ou o teu domínio)" ;;
        *:usage) echo "Usage: sudo $0 name.duckdns.org --duckdns TOKEN   (or your own domain)" ;;
        it:server) echo "Prima installa il server: sudo ./install.sh" ;;
        fr:server) echo "Installez d'abord le serveur : sudo ./install.sh" ;;
        de:server) echo "Zuerst den Server installieren: sudo ./install.sh" ;;
        es:server) echo "Primero instala el servidor: sudo ./install.sh" ;;
        pt:server) echo "Primeiro instala o servidor: sudo ./install.sh" ;;
        *:server) echo "Install the server first: sudo ./install.sh" ;;
        it:token) echo "Con un nome .duckdns.org serve il token di DuckDNS: --duckdns TOKEN" ;;
        fr:token) echo "Avec un nom .duckdns.org il faut le jeton DuckDNS : --duckdns JETON" ;;
        de:token) echo "Mit einem .duckdns.org-Namen braucht es den DuckDNS-Token: --duckdns TOKEN" ;;
        es:token) echo "Con un nombre .duckdns.org hace falta el token de DuckDNS: --duckdns TOKEN" ;;
        pt:token) echo "Com um nome .duckdns.org é preciso o token do DuckDNS: --duckdns TOKEN" ;;
        *:token) echo "A .duckdns.org name needs the DuckDNS token: --duckdns TOKEN" ;;
        it:duckok) echo "DuckDNS aggiornato: $DOMAIN punta all'indirizzo internet del circolo." ;;
        fr:duckok) echo "DuckDNS mis à jour : $DOMAIN pointe vers l'adresse internet du club." ;;
        de:duckok) echo "DuckDNS aktualisiert: $DOMAIN zeigt auf die Internetadresse des Vereins." ;;
        es:duckok) echo "DuckDNS actualizado: $DOMAIN apunta a la dirección de internet del club." ;;
        pt:duckok) echo "DuckDNS atualizado: $DOMAIN aponta para o endereço de internet do clube." ;;
        *:duckok) echo "DuckDNS updated: $DOMAIN points to the club's internet address." ;;
        it:duckko) echo "DuckDNS ha detto di no: controlla nome e token su duckdns.org." ;;
        fr:duckko) echo "DuckDNS a refusé : vérifiez le nom et le jeton sur duckdns.org." ;;
        de:duckko) echo "DuckDNS hat abgelehnt: Name und Token auf duckdns.org prüfen." ;;
        es:duckko) echo "DuckDNS ha dicho que no: revisa nombre y token en duckdns.org." ;;
        pt:duckko) echo "O DuckDNS recusou: verifica o nome e o token em duckdns.org." ;;
        *:duckko) echo "DuckDNS refused: check the name and token on duckdns.org." ;;
        it:caddy) echo "Installo e configuro Caddy (HTTPS)…" ;;
        fr:caddy) echo "Installation et configuration de Caddy (HTTPS)…" ;;
        de:caddy) echo "Caddy (HTTPS) wird installiert und eingerichtet…" ;;
        es:caddy) echo "Instalando y configurando Caddy (HTTPS)…" ;;
        pt:caddy) echo "A instalar e configurar o Caddy (HTTPS)…" ;;
        *:caddy) echo "Installing and setting up Caddy (HTTPS)…" ;;
        it:router) echo "Sul router del circolo inoltra le porte TCP 80 e 443 a questo indirizzo (meglio fisso):" ;;
        fr:router) echo "Sur la box du club, redirigez les ports TCP 80 et 443 vers cette adresse (fixe, de préférence) :" ;;
        de:router) echo "Am Vereinsrouter die TCP-Ports 80 und 443 an diese Adresse weiterleiten (am besten fest):" ;;
        es:router) echo "En el router del club redirige los puertos TCP 80 y 443 a esta dirección (mejor fija):" ;;
        pt:router) echo "No router do clube reencaminha as portas TCP 80 e 443 para este endereço (de preferência fixo):" ;;
        *:router) echo "On the club router forward TCP ports 80 and 443 to this address (ideally a fixed one):" ;;
        it:certok) echo "Certificato HTTPS pronto. Nell'app, Server del circolo › Indirizzo da internet:" ;;
        fr:certok) echo "Certificat HTTPS prêt. Dans l'app, Serveur du club › Adresse depuis internet :" ;;
        de:certok) echo "HTTPS-Zertifikat bereit. In der App, Vereinsserver › Adresse aus dem Internet:" ;;
        es:certok) echo "Certificado HTTPS listo. En la app, Servidor del club › Dirección desde internet:" ;;
        pt:certok) echo "Certificado HTTPS pronto. Na app, Servidor do clube › Endereço pela internet:" ;;
        *:certok) echo "HTTPS certificate ready. In the app, Club server › Address from the internet:" ;;
        it:certwait) echo "Il certificato non c'è ancora: arriva da solo appena le porte 80 e 443 sono aperte (Caddy riprova). Controlla con: journalctl -u caddy -n 30" ;;
        fr:certwait) echo "Pas encore de certificat : il arrive tout seul dès que les ports 80 et 443 sont ouverts (Caddy réessaie). Vérifiez avec : journalctl -u caddy -n 30" ;;
        de:certwait) echo "Noch kein Zertifikat: Es kommt von selbst, sobald die Ports 80 und 443 offen sind (Caddy versucht es weiter). Prüfen mit: journalctl -u caddy -n 30" ;;
        es:certwait) echo "Aún no hay certificado: llega solo en cuanto los puertos 80 y 443 estén abiertos (Caddy reintenta). Revisa con: journalctl -u caddy -n 30" ;;
        pt:certwait) echo "Ainda não há certificado: chega sozinho quando as portas 80 e 443 estiverem abertas (o Caddy volta a tentar). Verifica com: journalctl -u caddy -n 30" ;;
        *:certwait) echo "No certificate yet: it arrives by itself once ports 80 and 443 are open (Caddy keeps trying). Check with: journalctl -u caddy -n 30" ;;
        it:removed) echo "HTTPS tolto: il server resta solo sulla rete del circolo." ;;
        fr:removed) echo "HTTPS retiré : le serveur reste seulement sur le réseau du club." ;;
        de:removed) echo "HTTPS entfernt: Der Server bleibt nur im Vereinsnetz." ;;
        es:removed) echo "HTTPS quitado: el servidor queda solo en la red del club." ;;
        pt:removed) echo "HTTPS removido: o servidor fica só na rede do clube." ;;
        *:removed) echo "HTTPS removed: the server stays on the club network only." ;;
    esac
}

[ "$(id -u)" -eq 0 ] || { echo "sudo $0"; exit 1; }

if [ "$REMOVE" -eq 1 ]; then
    systemctl disable --now tsm-duckdns.timer 2>/dev/null || true
    rm -f /etc/systemd/system/tsm-duckdns.service /etc/systemd/system/tsm-duckdns.timer /usr/local/sbin/tsm-duckdns /etc/default/tsm-duckdns
    if grep -qs "TSM club server" /etc/caddy/Caddyfile; then systemctl disable --now caddy 2>/dev/null || true; fi
    systemctl daemon-reload
    msg removed
    exit 0
fi

[ -n "$DOMAIN" ] || { msg usage; exit 1; }
[ -f /etc/default/tsm-server ] || { msg server; exit 1; }
PORT=$(sed -n 's/^TSM_PORT=//p' /etc/default/tsm-server); PORT=${PORT:-8080}

# DuckDNS: il nome segue l'indirizzo di casa, che il provider può cambiare.
case "$DOMAIN" in
    *.duckdns.org)
        [ -n "$TOKEN" ] || { msg token; exit 1; }
        command -v curl >/dev/null 2>&1 || { apt-get update; DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends curl ca-certificates; }
        umask 077
        printf 'DUCKDNS_DOMAIN=%s\nDUCKDNS_TOKEN=%s\n' "${DOMAIN%.duckdns.org}" "$TOKEN" > /etc/default/tsm-duckdns
        umask 022
        install -m 755 "$DIR/tsm-duckdns" /usr/local/sbin/tsm-duckdns
        install -m 644 "$DIR/tsm-duckdns.service" "$DIR/tsm-duckdns.timer" /etc/systemd/system/
        systemctl daemon-reload
        systemctl enable tsm-duckdns.timer >/dev/null 2>&1
        if /usr/local/sbin/tsm-duckdns >/dev/null 2>&1; then msg duckok; else msg duckko; exit 1; fi
        systemctl start tsm-duckdns.timer
        ;;
esac

msg caddy
command -v caddy >/dev/null 2>&1 || { apt-get update; DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends caddy; }
if [ -f /etc/caddy/Caddyfile ] && ! grep -qs "TSM club server" /etc/caddy/Caddyfile; then cp /etc/caddy/Caddyfile /etc/caddy/Caddyfile.before-tsm; fi
{
    echo "# TSM club server: HTTPS from the internet (setup-https.sh)."
    if [ -n "$EMAIL" ]; then printf '{\n\temail %s\n}\n\n' "$EMAIL"; fi
    printf '%s {\n' "$DOMAIN"
    printf '\tencode gzip\n'
    printf '\theader Strict-Transport-Security "max-age=31536000"\n'
    printf '\treverse_proxy 127.0.0.1:%s\n' "$PORT"
    printf '}\n'
} > /etc/caddy/Caddyfile
# Il controllo parla solo se c'è un errore.
caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile >/dev/null 2>&1 || { caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile; exit 1; }
systemctl enable caddy >/dev/null 2>&1
systemctl restart caddy

echo
msg router
for a in $(hostname -I 2>/dev/null); do case "$a" in *:*) ;; *) echo "  $a" ;; esac; done
echo
# Caddy chiede il certificato appena parte: se le porte sono già aperte, arriva in pochi secondi.
i=0
until curl -fs -o /dev/null --max-time 5 --resolve "$DOMAIN:443:127.0.0.1" "https://$DOMAIN/api/v1/info" 2>/dev/null; do
    i=$((i + 1))
    if [ $i -gt 30 ]; then msg certwait; exit 0; fi
    sleep 2
done
msg certok
echo "  https://$DOMAIN"
