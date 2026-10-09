#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Stefano Spagnolo
# SPDX-License-Identifier: GPL-3.0-or-later
"""Genera installa_tsm.sh: ogni file del progetto come blocco `cat << 'TSM_EOF'` (il jar del wrapper in base64)."""
import base64
import pathlib
import subprocess

ROOT = pathlib.Path(__file__).resolve().parent.parent
PROJ = ROOT / "TennisScoreManager"
FW = ROOT / "firmware" / "TSM_Band" / "TSM_Band.ino"
OUT = ROOT / "deliver" / "installa_tsm.sh"
DELIM = "TSM_EOF"

# File tracciati da git dentro il progetto Android (niente build/, .gradle/, local.properties)
files = subprocess.run(
    ["git", "-C", str(ROOT), "ls-files", "TennisScoreManager"], capture_output=True, text=True, check=True
).stdout.split()
files = [f for f in files if not f.endswith("local.properties")]

lines = [
    "#!/usr/bin/env bash",
    "# ============================================================================",
    "#  Tennis Score Manager - installazione completa del progetto (app + firmware)",
    "#  Tennis Score Manager - complete project installer (app + firmware)",
    "#  Uso / usage:  bash installa_tsm.sh [cartella_progetto] [cartella_sketch]",
    "#  Predefinite / defaults: ~/AndroidStudioProjects/TennisScoreManager",
    "#                          ~/Arduino/TSM_Band",
    "#  Se la cartella del progetto esiste già viene spostata in un backup datato.",
    "#  An existing project folder is moved to a dated backup first.",
    "#  Linux, macOS, or Windows in Git Bash.",
    "# ============================================================================",
    "set -euo pipefail",
    'DEST="${1:-$HOME/AndroidStudioProjects/TennisScoreManager}"',
    'FWDIR="${2:-$HOME/Arduino/TSM_Band}"',
    "",
    'if [ -d "$DEST" ] && [ -n "$(ls -A "$DEST" 2>/dev/null)" ]; then',
    '  BACKUP="${DEST}.backup-$(date +%Y%m%d-%H%M%S)"',
    '  echo ">> Cartella esistente spostata in / existing folder moved to: $BACKUP"',
    '  mv "$DEST" "$BACKUP"',
    "fi",
    'mkdir -p "$DEST"',
    "",
]

dirs = sorted({str(pathlib.Path(f).relative_to("TennisScoreManager").parent) for f in files} - {"."})
lines.append('echo ">> Creo le cartelle / creating folders"')
for d in dirs:
    lines.append(f'mkdir -p "$DEST/{d}"')
lines.append("")

count = 0
for f in files:
    rel = str(pathlib.Path(f).relative_to("TennisScoreManager"))
    path = ROOT / f
    data = path.read_bytes()
    lines.append(f"# ---------------------------------------------------------------- {rel}")
    if rel.endswith(".jar"):
        b64 = base64.encodebytes(data).decode().rstrip("\n")
        lines.append(f"base64 -d > \"$DEST/{rel}\" << '{DELIM}'")
        lines.append(b64)
        lines.append(DELIM)
    else:
        text = data.decode("utf-8")
        assert DELIM not in text, rel
        if not text.endswith("\n"):
            text += "\n"
        lines.append(f"cat > \"$DEST/{rel}\" << '{DELIM}'")
        lines.append(text.rstrip("\n"))
        lines.append(DELIM)
    lines.append("")
    count += 1

lines.append('chmod +x "$DEST/gradlew"')
lines.append("")
fw = FW.read_text()
assert DELIM not in fw
lines.append("# ---------------------------------------------------------------- firmware M5StickS3")
lines.append('mkdir -p "$FWDIR"')
lines.append(f"cat > \"$FWDIR/TSM_Band.ino\" << '{DELIM}'")
lines.append(fw.rstrip("\n"))
lines.append(DELIM)
lines.append("")
lines.append(f'echo ">> Fatto / done: {count} file del progetto / project files in $DEST"')
lines.append('echo ">> Sketch del braccialetto / wristband sketch: $FWDIR/TSM_Band.ino"')
lines.append('echo ">> Ora apri la cartella del progetto con Android Studio / now open the project folder in Android Studio (File > Open)."')

OUT.write_text("\n".join(lines) + "\n")
OUT.chmod(0o755)
print(f"{OUT} ({count} file + firmware, {OUT.stat().st_size // 1024} KB)")
