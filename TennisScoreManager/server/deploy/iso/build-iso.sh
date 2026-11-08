#!/bin/sh
# SPDX-FileCopyrightText: 2026 Stefano Spagnolo
# SPDX-License-Identifier: GPL-3.0-or-later

# Costruisce out/tsm-server.iso (Debian 12 live, PC a 64 bit) con dentro il server del circolo TSM.
# Serve podman; prima il kit:  ./gradlew :server:serverKit  (dalla cartella TennisScoreManager).
# Scarica Debian e i pacchetti (circa 1 GB la prima volta). Non serve root: basta podman.
set -eu
HERE=$(cd "$(dirname "$0")" && pwd)
KIT="$HERE/../../build/kit/tsm-server-kit.tar.gz"
[ -f "$KIT" ] || { echo "Manca il kit / kit missing: ./gradlew :server:serverKit"; exit 1; }
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
tar xzf "$KIT" -C "$WORK"
podman run --rm --privileged \
    -v "$HERE:/iso:Z" -v "$WORK/tsm-server:/kit:ro,Z" \
    docker.io/library/debian:bookworm sh /iso/make-iso.sh
echo "ISO: $HERE/out/tsm-server.iso"
