#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Stefano Spagnolo
# SPDX-License-Identifier: GPL-3.0-or-later
#
# Pubblica il sito su Netlify, quando lo decidi tu: aggiunge a main un commit vuoto con [sito] nel messaggio
# e lo manda a GitHub; Netlify vede [sito] e pubblica website/ (vedi netlify.toml). Ogni volta è un deploy.
# Uso: bash deliver/pubblica_sito.sh   (dalla cartella del repository, sul ramo main)
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"
if [ "$(git branch --show-current)" != main ]; then
  echo "Serve il ramo main (ora sei su $(git branch --show-current)): git switch main" >&2
  exit 1
fi
if [ -n "$(git status --porcelain)" ]; then
  echo "Ci sono modifiche non salvate: prima fai commit o mettile da parte." >&2
  exit 1
fi
git fetch -q origin main
if [ "$(git rev-parse HEAD)" != "$(git rev-parse origin/main)" ]; then
  echo "main non è uguale a origin/main: prima git pull (o git push)." >&2
  exit 1
fi

echo "Si pubblica il sito di main ($(git log -1 --format='%h %s'))."
read -r -p "Pubblicare adesso? [s/N] " ok
case "$ok" in s|S|si|sì|Sì|SI) ;; *) echo "Niente da fare."; exit 0 ;; esac

git commit -q --allow-empty -m "Pubblica il sito [sito]"
git push -q origin main
echo "Fatto: Netlify pubblica tra poco su https://tennis-score-manager.netlify.app"
