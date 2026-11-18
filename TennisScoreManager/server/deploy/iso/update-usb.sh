#!/bin/sh
# SPDX-FileCopyrightText: 2026 Stefano Spagnolo
# SPDX-License-Identifier: GPL-3.0-or-later

# Aggiorna il server su una chiavetta TSM-SERVER già in uso, SENZA cancellare i dati (giocatori, prenotazioni,
# abbinamento): mette il programma nuovo nella partizione "persistence", che sulla chiavetta ricopre il sistema.
# Al prossimo avvio parte il server nuovo. Da un PC Linux, con la chiavetta inserita e il PC del circolo spento:
#   tar xzf tsm-server-kit.tar.gz && sudo tsm-server/update-usb.sh /dev/sdX
#   (oppure dalla cartella iso: sudo ./update-usb.sh /dev/sdX [tsm-server-kit.tar.gz])
# Updates the server on a TSM-SERVER stick already in use WITHOUT erasing its data.
set -eu
DEV="${1:-}"
HERE=$(cd "$(dirname "$0")" && pwd)
KIT="${2:-$HERE/tsm-server-kit.tar.gz}"
[ -f "$KIT" ] || KIT="$HERE/../../build/kit/tsm-server-kit.tar.gz"

[ "$(id -u)" -eq 0 ] || { echo "sudo $0 /dev/sdX"; exit 1; }
[ -n "$DEV" ] && [ -b "$DEV" ] || { echo "Uso / usage: sudo $0 /dev/sdX [tsm-server-kit.tar.gz]"; lsblk -o NAME,SIZE,TRAN,LABEL,MODEL; exit 1; }
[ -f "$KIT" ] || [ -f "$HERE/tsm-server.jar" ] || { echo "Manca il kit / kit missing: tsm-server-kit.tar.gz"; exit 1; }

# La partizione dei dati della chiavetta (etichetta "persistence"), mai un disco di sistema.
PART=$(lsblk -lnpo NAME,LABEL "$DEV" | awk '$2 == "persistence" { print $1; exit }')
[ -n "$PART" ] || { echo "Su $DEV non c'è la partizione persistence: non è una chiavetta TSM-SERVER. / No persistence partition on $DEV."; exit 1; }
if lsblk -no MOUNTPOINT "$DEV" | grep -qE '^/(boot|home)?$'; then
    echo "$DEV contiene il sistema: mi fermo. / $DEV holds the system: stopping."
    exit 1
fi

WORK=$(mktemp -d)
MNT=$(mktemp -d)
cleanup() { umount "$MNT" 2>/dev/null || true; rmdir "$MNT" 2>/dev/null || true; rm -rf "$WORK"; }
trap cleanup EXIT
if [ -f "$HERE/tsm-server.jar" ] && [ -z "${2:-}" ]; then
    # Lanciato dal kit già scompattato: il programma è qui accanto.
    mkdir -p "$WORK/tsm-server" && cp -a "$HERE/." "$WORK/tsm-server/"
else
    tar xzf "$KIT" -C "$WORK"
fi
[ -f "$WORK/tsm-server/tsm-server.jar" ] || { echo "Kit non valido / invalid kit"; exit 1; }

umount "$PART" 2>/dev/null || true
mount "$PART" "$MNT"
[ -f "$MNT/persistence.conf" ] || { echo "Manca persistence.conf: chiavetta non preparata da make-usb.sh. / Not a TSM-SERVER stick."; exit 1; }
# Debian live tiene le modifiche in rw/ (overlay); le chiavette più vecchie direttamente nella radice.
if [ -d "$MNT/rw" ]; then ROOT="$MNT/rw"; else ROOT="$MNT"; fi

DEST="$ROOT/opt/tsm-server"
mkdir -p "$DEST/kit"
if [ -f "$DEST/tsm-server.jar" ]; then cp -p "$DEST/tsm-server.jar" "$DEST/tsm-server.jar.prima"; fi
cp "$WORK/tsm-server/tsm-server.jar" "$DEST/tsm-server.jar"
cp "$WORK/tsm-server/tsm-tunnel" "$DEST/tsm-tunnel"
chmod 755 "$DEST/tsm-tunnel"
cp -a "$WORK/tsm-server/." "$DEST/kit/"
chmod 644 "$DEST/tsm-server.jar"
sync
echo
echo "Fatto: il server nuovo parte al prossimo avvio del PC del circolo. I dati sono rimasti."
echo "Done: the new server starts the next time the club PC boots. The data has been kept."
