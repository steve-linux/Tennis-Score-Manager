#!/bin/sh
# SPDX-FileCopyrightText: 2026 Stefano Spagnolo
# SPDX-License-Identifier: GPL-3.0-or-later

# Dentro il contenitore Debian (lo lancia build-iso.sh): il sistema con mmdebstrap (senza root), il kernel con
# live-boot, e un'ISO ibrida (BIOS e UEFI, da CD o da chiavetta) fatta da grub-mkrescue.
set -eu
apt-get update
DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
    mmdebstrap squashfs-tools-ng uidmap xorriso mtools grub-common grub-pc-bin grub-efi-amd64-bin ca-certificates
W=/work
rm -rf "$W" && mkdir -p "$W/iso/live" "$W/iso/boot/grub"
PKGS=$(grep -v '^#' /iso/config/package-lists/tsm.list.chroot | tr '\n' ',' | sed 's/,*$//')

# Il server dentro il sistema: gli stessi file del kit per il Raspberry Pi.
mkdir -p "$W/root/opt/tsm-server" "$W/root/etc/default" "$W/root/etc/systemd/system" "$W/root/usr/local/sbin"
cp -a /iso/config/includes.chroot/. "$W/root/"
cp /kit/tsm-server.jar "$W/root/opt/tsm-server/"
cp /kit/tsm-server.service "$W/root/etc/systemd/system/"
cp /kit/tsm-server.default "$W/root/etc/default/tsm-server"
sed 's#^cat /var/lib/tsm-server/setup-code.txt$#cat /var/lib/tsm-server/setup-code.txt; /usr/local/sbin/tsm-issue#' /kit/reset-admin.sh > "$W/root/usr/local/sbin/tsm-reset-admin"
cp /iso/config/hooks/normal/9000-tsm.hook.chroot "$W/hook.sh"

mmdebstrap --mode=unshare --variant=apt --components=main \
    --include="linux-image-amd64,live-boot,live-config,live-config-systemd,systemd-sysv,locales,keyboard-configuration,console-setup,sudo,user-setup,$PKGS" \
    --customize-hook="sync-in $W/root /" \
    --customize-hook="upload $W/hook.sh /tmp/hook.sh" \
    --customize-hook='chroot "$1" sh /tmp/hook.sh' \
    --customize-hook='rm -f "$1/tmp/hook.sh"' \
    --customize-hook="sync-out /boot $W/boot" \
    bookworm "$W/iso/live/filesystem.squashfs"

cp "$(ls $W/boot/vmlinuz-* | tail -1)" "$W/iso/live/vmlinuz"
cp "$(ls $W/boot/initrd.img-* | tail -1)" "$W/iso/live/initrd.img"
cat > "$W/iso/boot/grub/grub.cfg" <<'GRUB'
set timeout=3
set default=0
menuentry "TSM - Server del circolo / Club server" {
    linux /live/vmlinuz boot=live components persistence quiet hostname=tsm-circolo locales=it_IT.UTF-8 keyboard-layouts=it timezone=Europe/Rome live-config.noautologin
    initrd /live/initrd.img
}
menuentry "TSM - English keyboard" {
    linux /live/vmlinuz boot=live components persistence quiet hostname=tsm-circolo locales=en_US.UTF-8 keyboard-layouts=us timezone=Europe/Rome live-config.noautologin
    initrd /live/initrd.img
}
GRUB
mkdir -p /iso/out
grub-mkrescue -o /iso/out/tsm-server.iso "$W/iso" -- -volid TSM-SERVER
ls -l /iso/out/tsm-server.iso
