#!/usr/bin/env python3
"""Baut die eigenstaendige Bedienungsanleitung: ersetzt {{IMG:datei}} im Template
durch data:-URIs, damit die HTML allein verschickt werden kann."""
import base64, pathlib, re, sys

HERE = pathlib.Path(__file__).resolve().parent
TPL = HERE / "template.html"
OUT = HERE / "bedienungsanleitung.html"
JPG = pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else HERE / "build-images"

MIME = {".jpg": "image/jpeg", ".jpeg": "image/jpeg", ".png": "image/png"}


def embed(match):
    name = match.group(1)
    path = JPG / name
    if not path.exists():
        raise SystemExit(f"Bild fehlt: {path}")
    mime = MIME[path.suffix.lower()]
    data = base64.b64encode(path.read_bytes()).decode("ascii")
    return f"data:{mime};base64,{data}"


html = TPL.read_text(encoding="utf-8")
html, n = re.subn(r"\{\{IMG:([^}]+)\}\}", embed, html)
OUT.write_text(html, encoding="utf-8")
print(f"{n} Bilder eingebettet -> {OUT} ({OUT.stat().st_size/1024/1024:.2f} MB)")
