#!/bin/sh
# SPDX-FileCopyrightText: 2026 Stefano Spagnolo
# SPDX-License-Identifier: GPL-3.0-or-later

# Un nuovo codice di abbinamento per la segreteria (telefono nuovo, o "Scollega" nell'app): quella vecchia non
# entra più. Inviti, giocatori e prenotazioni restano.
set -eu
[ "$(id -u)" -eq 0 ] || { echo "sudo $0"; exit 1; }
systemctl stop tsm-server
runuser -u tsm -- /usr/bin/java -Xmx64m -cp /opt/tsm-server/tsm-server.jar com.tennis.scoremanager.server.MainKt --data /var/lib/tsm-server --reset-admin --no-serve
systemctl start tsm-server
echo
cat /var/lib/tsm-server/setup-code.txt
