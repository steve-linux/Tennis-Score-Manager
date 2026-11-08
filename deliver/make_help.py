#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Stefano Spagnolo
# SPDX-License-Identifier: GPL-3.0-or-later
"""
Genera la guida dell'app (Altro › Aiuto) dalle guide del repository: per ogni lingua le sezioni per chi usa l'app
(non quelle su computer, compilazione, firmware e codice) in TennisScoreManager/app/src/main/assets/help/<lingua>.md.
Si lancia da solo con make_installer.py; a mano: python3 deliver/make_help.py
"""
import pathlib
import re

ROOT = pathlib.Path(__file__).resolve().parent.parent
OUT = ROOT / "TennisScoreManager" / "app" / "src" / "main" / "assets" / "help"
GUIDES = {"it": "GUIDA.md", "en": "GUIDE.en.md", "fr": "GUIDE.fr.md", "de": "GUIDE.de.md", "es": "GUIDE.es.md", "pt": "GUIDE.pt.md"}
# Capitoli per chi usa l'app: 3.1 (com'è fatta), voce e lingue, braccialetti e altri dispositivi, uso, TV, regole,
# problemi. Fuori: 0-3 (repository, computer, compilazione, installazione), 5 (firmware), 10 (codice).
KEEP = {"3.1", "4", "6", "7", "8", "9", "11"}
# Righe da togliere: problemi del computer o del firmware e percorsi del progetto.
DEV = ("Android Studio", "Arduino", "Gradle", "baud", "TennisScoreManager/", "udev", "dialout", "`Calls.kt`")
HEADING = re.compile(r"^(#{2,3}) (\d+(?:\.\d+)?)\.? ")


def chapter(num: str) -> str:
    """Il capitolo di una sezione: 6.4 sta nel 6; la 3.1 è un capitolo a sé (il 3 è l'installazione)."""
    return num if num == "3.1" else num.split(".")[0]


def extract(text: str) -> str:
    out = []
    keep = False
    for line in text.splitlines():
        m = HEADING.match(line)
        if m:
            keep = chapter(m.group(2)) in KEEP
            if keep:
                # La versione da cui c'è una funzione ("(app 2.5)", "(firmware 2.4, app 2.5)") nell'app non serve.
                line = re.sub(r"\s*\((?:app|firmware|App|Firmware)[^)]*\)\s*$", "", line)
                # La 3.1 diventa un capitolo: è la prima cosa da leggere.
                if m.group(2) == "3.1":
                    line = "## " + line[4:]
        elif line.startswith("# ") or line.startswith("## "):
            keep = False
        if keep:
            # Le righe per chi sviluppa (computer, firmware, codice) non servono a chi usa l'app.
            if any(k in line for k in DEV):
                continue
            line = re.sub(r" ?\(`[^`]*\.(?:kt|ino|cpp|h|html)`\)", "", line)
            out.append(line)
    return "\n".join(renumber(out)).strip() + "\n"


def renumber(lines: list[str]) -> list[str]:
    """Rinumera gli elenchi 1., 2., 3. dopo aver tolto qualche voce (le righe rientrate restano nella voce)."""
    n = 0
    res = []
    for line in lines:
        m = re.match(r"^(\d+)\. ", line)
        if m:
            n += 1
            line = f"{n}. " + line[m.end():]
        elif line.strip() and not line.startswith(" "):
            n = 0
        res.append(line)
    return res


def main() -> list[pathlib.Path]:
    OUT.mkdir(parents=True, exist_ok=True)
    written = []
    for lang, name in GUIDES.items():
        dest = OUT / f"{lang}.md"
        dest.write_text(extract((ROOT / "deliver" / name).read_text(encoding="utf-8")), encoding="utf-8")
        written.append(dest)
    return written


if __name__ == "__main__":
    for p in main():
        print(p.relative_to(ROOT), sum(1 for _ in p.open(encoding="utf-8")), "righe")
