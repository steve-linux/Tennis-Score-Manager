#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Stefano Spagnolo
# SPDX-License-Identifier: GPL-3.0-or-later
"""
Genera i testi dell'app che usa anche la Segreteria da PC (tornei, Oggi, campi non prenotabili, statistiche, scuola tennis, squadre, impostazioni, server), presi da
Strings*.kt nelle sei lingue: TennisScoreManager/app/src/main/assets/desk/app-i18n.js. Così sul PC si leggono le
stesse parole del telefono. Si lancia da solo con make_installer.py; a mano: python3 deliver/make_desk_texts.py
"""
import json
import pathlib
import re

ROOT = pathlib.Path(__file__).resolve().parent.parent
UI = ROOT / "TennisScoreManager" / "app" / "src" / "main" / "java" / "com" / "tennis" / "scoremanager" / "ui"
OUT = ROOT / "TennisScoreManager" / "app" / "src" / "main" / "assets" / "desk" / "app-i18n.js"
# I testi che servono: per prefisso, più qualcuno preso da solo.
PREFIXES = ("ct", "blk", "td", "st", "sc", "tm", "plan", "crt", "pt", "srv", "pb")
SINGLE = {"bkOther"}
# Troppo complicati da tradurre in JavaScript da soli: li ha i18n.js, scritti a mano.
SKIP = {"scStudentsCount", "crtDeleteAsk"}
LANGS = ("it", "en", "fr", "de", "es", "pt")


def sources():
    main = (UI / "Strings.kt").read_text(encoding="utf-8")
    it, en = main.index("object ItStrings"), main.index("object EnStrings")
    out = {"it": main[it:en], "en": main[en:]}
    for lang, f in (("fr", "StringsFr.kt"), ("de", "StringsDe.kt"), ("es", "StringsEs.kt"), ("pt", "StringsPt.kt")):
        out[lang] = (UI / f).read_text(encoding="utf-8")
    return out


def js_string(body):
    """Il corpo di una stringa Kotlin (senza virgolette) come template JavaScript."""
    def inner(m):
        e = re.sub(r'if \((\w+) == (\w+)\) "([^"]*)" else "([^"]*)"',
                   lambda x: f"{x[1]} === {x[2]} ? {json.dumps(x[3], ensure_ascii=False)} : {json.dumps(x[4], ensure_ascii=False)}", m[1])
        return "${" + e + "}"
    body = re.sub(r'\$\{((?:[^{}"]|"[^"]*")*)\}', inner, body)
    body = re.sub(r"\$(\w+)", r"${\1}", body)
    return "`" + body.replace("`", "\\`") + "`"


def js_expr(e):
    e = e.strip()
    m = re.fullmatch(r'if \((\w+) == (\w+)\) (\w+) else "(.*)"', e)
    if m:
        return f"{m[1]} === {m[2]} ? {m[3]} : {js_string(m[4])}"
    m = re.fullmatch(r'if \((\w+) == (\w+)\) "(.*?)" else "(.*)"', e)
    if m:
        return f"{m[1]} === {m[2]} ? {js_string(m[3])} : {js_string(m[4])}"
    m = re.fullmatch(r'"(.*)"', e)
    if m:
        return js_string(m[1])
    raise SystemExit("Testo che non so tradurre in JavaScript: " + e)


def wanted(key):
    return key not in SKIP and (key in SINGLE or any(key.startswith(p) and key[len(p):len(p) + 1].isupper() for p in PREFIXES))


def texts(src):
    d = {}
    for m in re.finditer(r'override val (\w+) = "((?:[^"\\]|\\.)*)"\s*$', src, re.M):
        if wanted(m[1]) and m[1] not in d:
            d[m[1]] = json.dumps(json.loads('"' + m[2] + '"'), ensure_ascii=False)
    for m in re.finditer(r"override val (\w+): \(.*?\) -> String = \{(.*)\}\s*$", src, re.M):
        if not wanted(m[1]) or m[1] in d:
            continue
        body = m[2].strip()
        two = re.fullmatch(r"(\w+), (\w+) -> (.*)", body)
        d[m[1]] = f"({two[1]}, {two[2]}) => {js_expr(two[3])}" if two else f"(it) => {js_expr(body)}"
    return d


def main():
    src = sources()
    out = {lang: texts(src[lang]) for lang in LANGS}
    keys = set(out["it"])
    for lang in LANGS:
        if set(out[lang]) != keys:
            raise SystemExit(f"{lang}: testi diversi dall'italiano: {sorted(keys ^ set(out[lang]))}")
    js = ["// SPDX-FileCopyrightText: 2026 Stefano Spagnolo", "// SPDX-License-Identifier: GPL-3.0-or-later",
          "// Fatto da deliver/make_desk_texts.py coi testi dell'app (Strings*.kt): non cambiarlo a mano.", "'use strict';", "", "const APP_I18N = {"]
    for lang in LANGS:
        js.append(f"  {lang}: {{")
        js += [f"    {k}: {out[lang][k]}," for k in sorted(out[lang])]
        js.append("  },")
    js += ["};", "for (const l in APP_I18N) Object.assign(DESK_I18N[l], APP_I18N[l]);", ""]
    OUT.write_text("\n".join(js), encoding="utf-8")
    return [OUT]


if __name__ == "__main__":
    print(f"{len(main())} file, {OUT.relative_to(ROOT)}")
