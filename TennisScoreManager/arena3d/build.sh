#!/bin/sh
# SPDX-FileCopyrightText: 2026 Stefano Spagnolo
# SPDX-License-Identifier: GPL-3.0-or-later
# TSM Arena 3D: impacchetta arena3d.js con three.js in un file solo per le assets dell'app (la TV non ha internet).
#   cd TennisScoreManager/arena3d && npm ci && sh build.sh
set -e
cd "$(dirname "$0")"
npx esbuild arena3d.js --bundle --format=iife --global-name=Arena3D --target=es2018 --minify \
  --legal-comments=eof --outfile=../app/src/main/assets/arena/arena3d.js
