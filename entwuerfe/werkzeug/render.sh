#!/bin/sh
# Rendert alle Entwürfe aus entwuerfe/canvas pixelgenau (480x800) nach entwuerfe/betrachter/assets.
# Reihenfolge = Reihenfolge im Betrachter. Braucht node, py (mit Pillow nicht nötig) und
# Playwrights chromium_headless_shell. Startet kurz einen lokalen Server auf Port 8098.
set -e
E="$(cd "$(dirname "$0")/.." && pwd)"; W="$E/werkzeug"; C="$E/canvas"; A="$E/betrachter/assets"
T="$(mktemp -d)"; mkdir -p "$T/html"
REIHE="Signal Setliste Ansage SetWechseln SongKarte Verschieben Gigs Archiv ArchivSprung Editor Kalibrierung Mixer Main MuditaPur"
HEAD='<!doctype html><html lang="de"><head><meta charset="utf-8"><link href="https://fonts.googleapis.com/css2?family=Literata:ital,opsz,wght@0,7..72,400;0,7..72,600;1,7..72,400&family=Lato:wght@400;700;900&family=Barlow+Condensed:wght@600;700;800&family=Barlow+Semi+Condensed:wght@500;600;700&display=swap" rel="stylesheet"><style>body{margin:0;background:#fff;overflow:hidden}input{font-family:"Barlow Semi Condensed",sans-serif}</style></head><body>'
n=1; for f in $REIHE; do num=$(printf "%02d" $n)
  node "$W/daten.js" "$C/$f.dc.html" > "$T/d.json"
  { echo "$HEAD"; PYTHONIOENCODING=utf-8 py -I -X utf8 "$W/expand.py" "$C/$f.dc.html" "$T/d.json"; echo '</body></html>'; } > "$T/html/$num-$f.html"
  n=$((n+1)); done
(cd "$T/html" && py -I -m http.server 8098 --bind 127.0.0.1 >/dev/null 2>&1 &)
sleep 2 2>/dev/null || ping -n 3 127.0.0.1 >/dev/null
H=$(ls -d ~/AppData/Local/ms-playwright/chromium_headless_shell-*/*/ | tail -1); EXE=$(ls "$H"*.exe | head -1)
rm -f "$A"/*.png; mkdir -p "$A"
for f in "$T"/html/*.html; do b=$(basename "$f" .html)
  "$EXE" --headless --disable-gpu --hide-scrollbars --user-data-dir="$T/profil" --window-size=360,600 \
    --force-device-scale-factor=1.3333333 --virtual-time-budget=6000 --screenshot="$A/$b.png" "http://127.0.0.1:8098/$b.html" >/dev/null 2>&1
done
powershell -NoProfile -Command "Get-NetTCPConnection -LocalPort 8098 -State Listen -ErrorAction SilentlyContinue | ForEach-Object { Stop-Process -Id \$_.OwningProcess -Force }"
rm -rf "$T"; ls "$A"
