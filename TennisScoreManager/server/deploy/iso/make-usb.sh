#!/bin/sh
# SPDX-FileCopyrightText: 2026 Stefano Spagnolo
# SPDX-License-Identifier: GPL-3.0-or-later

# Scrive tsm-server.iso su una chiavetta USB e aggiunge la partizione "persistence" dove il server tiene i dati
# (senza, a ogni spegnimento si perde tutto). CANCELLA TUTTA LA CHIAVETTA.
#   sudo ./make-usb.sh /dev/sdX [out/tsm-server.iso]
# Su Windows lo stesso si fa con Rufus: immagine tsm-server.iso, "Dimensione partizione persistente" > 0.
set -eu
DEV="${1:-}"
HERE=$(cd "$(dirname "$0")" && pwd)
ISO="${2:-$HERE/out/tsm-server.iso}"

[ "$(id -u)" -eq 0 ] || { echo "sudo $0 /dev/sdX"; exit 1; }
[ -n "$DEV" ] && [ -b "$DEV" ] || { echo "Uso / usage: sudo $0 /dev/sdX   (lsblk per vedere i dischi / to list disks)"; lsblk -d -o NAME,SIZE,TRAN,RM,MODEL; exit 1; }
[ -f "$ISO" ] || { echo "Manca / missing: $ISO"; exit 1; }

# Solo dischi rimovibili o USB, mai quello del sistema.
NAME=$(basename "$DEV")
RM=$(cat "/sys/block/$NAME/removable" 2>/dev/null || echo 0)
TRAN=$(lsblk -dno TRAN "$DEV" 2>/dev/null || true)
if [ "$RM" != 1 ] && [ "$TRAN" != usb ]; then
    echo "$DEV non sembra una chiavetta USB: mi fermo. / $DEV does not look like a USB stick: stopping."
    exit 1
fi
if lsblk -no MOUNTPOINT "$DEV" | grep -qE '^/(boot|home)?$'; then
    echo "$DEV contiene il sistema: mi fermo. / $DEV holds the system: stopping."
    exit 1
fi

lsblk -o NAME,SIZE,MODEL,LABEL,MOUNTPOINT "$DEV"
echo
echo "Tutto quello che c'è su $DEV verrà CANCELLATO. Scrivi SI per continuare."
echo "Everything on $DEV will be ERASED. Type YES to go on."
read -r ANSWER
case "$ANSWER" in SI|SÌ|YES|OUI|JA|SÍ|SIM) ;; *) echo "Annullato / cancelled"; exit 1 ;; esac

for p in $(lsblk -lno NAME "$DEV" | tail -n +2); do umount "/dev/$p" 2>/dev/null || true; done
dd if="$ISO" of="$DEV" bs=4M conv=fsync status=progress
sync

# La partizione dei dati nello spazio che resta, col nome che Debian live cerca.
printf ',,L\n' | sfdisk --append --no-reread "$DEV"
partprobe "$DEV" 2>/dev/null || blockdev --rereadpt "$DEV" 2>/dev/null || true
sleep 2
PART=$(lsblk -lnpo NAME "$DEV" | tail -n 1)
[ "$PART" != "$DEV" ] || { echo "Non trovo la partizione nuova / new partition not found"; exit 1; }
mkfs.ext4 -F -q -L persistence "$PART"
MNT=$(mktemp -d)
mount "$PART" "$MNT"
echo "/ union" > "$MNT/persistence.conf"
umount "$MNT"
rmdir "$MNT"
sync
echo
echo "Chiavetta pronta: avvia il PC del circolo da USB. / Stick ready: boot the club PC from USB."
