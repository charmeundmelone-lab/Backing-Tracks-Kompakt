#!/bin/sh
# Werkzeug für Tests am Mudita Kompakt (Git-Bash, adb per USB).
#   sh werkzeug/geraet.sh fokus          welche App ist vorne? (VOR JEDEM TIPP PRÜFEN)
#   sh werkzeug/geraet.sh texte          sichtbare Texte mit Koordinaten
#   sh werkzeug/geraet.sh tap "Text" [n] Element mit genau diesem Text antippen (n = n-ter Treffer)
#   sh werkzeug/geraet.sh shot name      Bildschirmfoto nach .geraet/name.png
#   sh werkzeug/geraet.sh key 25|24      Lautstärke runter/hoch = in TrikTrak vor/zurück blättern
#   sh werkzeug/geraet.sh xy X Y         Tipp auf Koordinate (nur wenn nötig)
ADB="$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"
D="-s LD20241004967"
S="$(cd "$(dirname "$0")/.." && pwd)/.geraet"; mkdir -p "$S"
# Die USB-Verbindung setzt manchmal kurz aus: bis zu 10 s auf das Gerät warten.
timeout 10 "$ADB" $D wait-for-device 2>/dev/null || { echo "GERAET NICHT ERREICHBAR"; exit 2; }
W=$(cd "$S" && pwd -W 2>/dev/null || pwd)
dump() { MSYS_NO_PATHCONV=1 "$ADB" $D shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; MSYS_NO_PATHCONV=1 "$ADB" $D exec-out cat /sdcard/ui.xml > "$S/ui.xml"; }
case "$1" in
  fokus) "$ADB" $D shell dumpsys window | grep mCurrentFocus ;;
  shot) "$ADB" $D exec-out screencap -p > "$S/$2.png"; echo "$S/$2.png" ;;
  texte) dump; py -I -X utf8 -c "
import re
x=open(r'$W/ui.xml',encoding='utf-8').read()
for m in re.finditer(r'(?:text|content-desc)=\"([^\"]+)\"[^>]*bounds=\"(\[[^\"]+)\"',x): print(m.group(1),m.group(2))" ;;
  tap)
    dump
    xy=$(py -I -X utf8 -c "
import re,sys
x=open(r'$W/ui.xml',encoding='utf-8').read()
t=sys.argv[1]; n=int(sys.argv[2]) if len(sys.argv)>2 else 0
hits=[]
for m in re.finditer(r'<node [^>]*>',x):
  a=m.group(0)
  tx=re.search(r' text=\"([^\"]*)\"',a); cd=re.search(r'content-desc=\"([^\"]*)\"',a)
  if (tx and tx.group(1)==t) or (cd and cd.group(1)==t):
    b=re.search(r'bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"',a)
    hits.append(((int(b.group(1))+int(b.group(3)))//2,(int(b.group(2))+int(b.group(4)))//2))
print('%d %d'%hits[n] if len(hits)>n else '')
" "$2" "${3:-0}")
    if [ -z "$xy" ]; then echo "NICHT GEFUNDEN: $2"; exit 1; fi
    "$ADB" $D shell input tap $xy; echo "tap $2 @ $xy" ;;
  xy) "$ADB" $D shell input tap $2 $3 ;;
  key) "$ADB" $D shell input keyevent $2 ;;
  *) sed -n 2,8p "$0" ;;
esac
