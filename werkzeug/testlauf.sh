#!/bin/sh
# Testlauf für TrikTrak (15.11., Jans Wunsch: "Testen muss schneller gehen").
#
#   sh werkzeug/testlauf.sh bauen          Tests (App + Werkstatt) und App bauen
#   sh werkzeug/testlauf.sh installieren   Mudita-Daten sichern, App installieren, Daten vergleichen
#                                          (GERÄTE-REGEL: nur mit Jans Ja im Chat)
#   sh werkzeug/testlauf.sh ablauf         Bedienung am Mudita im TESTMODUS durchspielen: eigene
#                                          Spielwiese (Gig "Testlauf"), Jans Daten bleiben unberührt.
#                                          Fotos + Bericht nach .geraet/testlauf/ (uebersicht.html)
#   sh werkzeug/testlauf.sh alles          bauen, dann ablauf (installiert NICHT)
#
# Der Ablauf bricht sofort ab, wenn TrikTrak gerade einen Song spielt, wenn eine andere App nach
# vorne kommt (Jan benutzt den Mudita) oder wenn der Bildschirm gesperrt ist. Am Ende werden
# Jans Daten (bestand.json) vorher/nachher Byte für Byte verglichen.
set -u
# Umlaute beim Vergleichen (grep -i) richtig behandeln.
export LC_ALL=C.UTF-8
R="$(cd "$(dirname "$0")/.." && pwd)"
ADB="$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"
D="-s LD20241004967"
PKG=show.jangregersen.buehne
G="sh $R/werkzeug/geraet.sh"
O="$R/.geraet/testlauf"
export JAVA_HOME="${JAVA_HOME:-/c/Program Files/Android/Android Studio/jbr}"

adbs() { MSYS_NO_PATHCONV=1 "$ADB" $D "$@"; }
runas() { adbs shell run-as $PKG "$@"; }
sichern() { adbs exec-out run-as $PKG cat files/bestand.json > "$1"; }
# Spielt TrikTrak gerade hörbar? (Der Wiedergabe-Dienst bleibt auch nach dem Anhalten bestehen,
# darum zählt nur eine Tonausgabe von TrikTraks Nutzerkennung im Zustand "started".)
spielt() {
  uid=$(adbs shell dumpsys package $PKG | grep -m1 -oE "userId=[0-9]+" | cut -d= -f2)
  adbs shell dumpsys audio | grep "AudioPlaybackConfiguration" | grep "u/pid:$uid/" | grep -q "state:started"
}
vorne() { adbs shell dumpsys window | grep mCurrentFocus | grep -q $PKG; }

bauen() {
  echo "== Tests und Bau"
  (cd "$R" && ./gradlew --no-daemon -q :app:testDebugUnitTest :werkstatt:test :app:assembleDebug) || { echo "BAU FEHLGESCHLAGEN"; exit 1; }
  echo "OK    Tests grün, App gebaut"
}

installieren() {
  echo "== Installieren"
  timeout 10 "$ADB" $D wait-for-device 2>/dev/null || { echo "GERAET NICHT ERREICHBAR"; exit 2; }
  if spielt; then echo "ABBRUCH: TrikTrak spielt gerade einen Song."; exit 3; fi
  stempel=$(date +%Y-%m-%d-%H%M)
  sichern "$R/.geraet/bestand-$stempel-vor-install.json"
  # Windows-Pfad nötig: adbs schaltet die Pfad-Umwandlung von Git-Bash aus.
  apk=$(cygpath -w "$R/app/build/outputs/apk/debug/app-debug.apk")
  erg=$(adbs install -r "$apk" 2>&1 | tail -1)
  case "$erg" in Success*) echo "OK    App installiert" ;; *) echo "INSTALLATION FEHLGESCHLAGEN: $erg" ;; esac
  sichern "$O-nach-install.json"
  if cmp -s "$R/.geraet/bestand-$stempel-vor-install.json" "$O-nach-install.json"; then echo "OK    Jans Daten nach dem Installieren unverändert"; else echo "ACHTUNG: Daten weichen ab"; fi
  rm -f "$O-nach-install.json"
}

# ── Ablauf ───────────────────────────────────────────────────────────────────

N=0; FEHLER=0; BERICHT=""
texte() { $G texte 2>/dev/null; }

# warte VERSUCHE TEXT…: bis alle Texte sichtbar sind (Groß/klein egal). Ein Versuch ≈ 1–2 s.
warte() {
  v=$1; shift
  while [ "$v" -gt 0 ]; do
    T=$(texte); ok=1
    for x in "$@"; do printf '%s\n' "$T" | grep -qiF -- "$x" || { ok=0; break; }; done
    [ $ok = 1 ] && return 0
    v=$((v - 1))
  done
  return 1
}

abbruch() { echo "ABBRUCH: $1"; aufraeumen; bericht; exit 4; }

tippe() {
  vorne || abbruch "TrikTrak ist nicht vorne (Jan benutzt den Mudita oder er ist gesperrt)."
  $G tap "$1" "${2:-0}" >/dev/null || { echo "FEHLT Knopf „$1“"; FEHLER=$((FEHLER + 1)); BERICHT="$BERICHT|FEHLT Knopf $1"; return 1; }
}

# Zweimal tippen (Beenden): der zweite Tipp sofort auf dieselbe Stelle, ohne neu zu suchen.
tippe_zweimal() {
  vorne || abbruch "TrikTrak ist nicht vorne."
  xy=$($G tap "$1" | sed -n 's/^tap .* @ //p')
  [ -n "$xy" ] || { echo "FEHLT Knopf „$1“"; FEHLER=$((FEHLER + 1)); return 1; }
  $G xy $xy >/dev/null
}

# pruefe NAME VERSUCHE TEXT…: warten, Foto, Ergebnis merken.
pruefe() {
  name=$1; shift; v=$1; shift
  N=$((N + 1)); bild=$(printf '%02d-%s' $N "$name")
  if warte "$v" "$@"; then erg="OK   "; else erg="FEHLT"; FEHLER=$((FEHLER + 1)); fi
  adbs exec-out screencap -p > "$O/$bild.png"
  echo "$erg $name"; [ "$erg" = "FEHLT" ] && echo "      erwartet: $*"
  BERICHT="$BERICHT|$erg|$bild|$name|$*"
}

aufraeumen() {
  # Nie mitten in einem Song beenden: erst anhalten (Pause-Knopf), dann App schließen.
  if spielt && vorne; then $G tap "Pause" >/dev/null 2>&1; fi
  adbs shell am force-stop $PKG
  runas cp files/protokoll-test.txt files/protokoll-test-letzter.txt 2>/dev/null
  adbs exec-out run-as $PKG cat files/protokoll-test.txt > "$O/protokoll-test.txt" 2>/dev/null
  runas rm -f files/testmodus files/test-leise files/test-bestand.json files/protokoll-test.txt
  adbs shell input keyevent 3
  sichern "$O/nachher.json"
  if cmp -s "$O/vorher.json" "$O/nachher.json"; then echo "OK    Jans Daten unverändert (bestand.json vorher = nachher)"
  else echo "ACHTUNG: bestand.json hat sich verändert (Jan hat parallel gearbeitet?) – vorher.json/nachher.json vergleichen"; FEHLER=$((FEHLER + 1)); fi
}

bericht() {
  {
    echo '<!doctype html><meta charset="utf-8"><title>Testlauf</title><style>body{font-family:sans-serif;background:#eee;margin:20px}div{display:inline-block;width:250px;margin:8px;vertical-align:top}img{width:240px;border:1px solid #999}b.f{color:#b00}</style>'
    echo "<h2>TrikTrak Testlauf $(date '+%d.%m.%Y %H:%M') · $FEHLER Fehler</h2>"
    printf '%s' "$BERICHT" | tr '|' '\n' | awk 'BEGIN{RS="";FS="\n"}{for(i=1;i<=NF;i+=4){ if($i=="")continue; e=$i; b=$(i+1); n=$(i+2); x=$(i+3); cls=(e~/FEHLT/)?" class=f":""; printf "<div><b%s>%s %s</b><br><img src=\"%s.png\"><br><small>%s</small></div>\n", cls, e, n, b, x }}'
  } > "$O/uebersicht.html"
  echo "== Ergebnis: $FEHLER Fehler · Fotos und Übersicht: $O/uebersicht.html"
}

ablauf() {
  echo "== Ablauf im Testmodus"
  timeout 10 "$ADB" $D wait-for-device 2>/dev/null || { echo "GERAET NICHT ERREICHBAR"; exit 2; }
  if spielt; then echo "ABBRUCH: TrikTrak spielt gerade einen Song. Finger weg."; exit 3; fi
  rm -rf "$O"; mkdir -p "$O"
  sichern "$O/vorher.json"
  [ -s "$O/vorher.json" ] || { echo "ABBRUCH: bestand.json nicht lesbar"; exit 3; }
  # Spielwiese anlegen: Testmodus + leise (TrikTrak dreht sonst die Lautstärke voll auf).
  runas rm -f files/test-bestand.json files/protokoll-test.txt
  runas touch files/testmodus files/test-leise
  adbs shell am force-stop $PKG
  adbs shell monkey -p $PKG -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1

  # 1 Öffnen am Gig-Tag = gleich bühnenbereit
  pruefe start 10 "Test Eins" "Bereit" "Kapo" "Weiter"
  vorne || abbruch "TrikTrak kam nicht nach vorne (Bildschirm gesperrt?)."
  # 2 Play, dann anhalten (die Testsongs sind nur 8 s lang)
  tippe "Abspielen"; tippe "Pause"
  pruefe angespielt 5 "Test Eins" "Beenden" "Danach" "Test Zwei"
  # 3 Liste: eine Regel
  tippe "Zur Setliste"
  pruefe liste 5 "Set A" "Wunschsong" "Test Zwei" "Test Drei"
  # Pausen-Kästchen in der Setliste: Tipp schaltet weiter (Test Zwei: 20 s → 30 s)
  tippe "Pause umschalten" 1
  pruefe liste-pause 4 "Pause 30 s"
  tippe "Test Drei"
  pruefe antippen-als-naechstes 5 "Danach" "Test Drei" "Rückgängig"
  tippe "Rückgängig"
  pruefe rueckgaengig 5 "Zurückgenommen"
  # 4 Beenden mit zweitem Tipp
  tippe "Song beenden"
  pruefe beenden-erster-tipp 4 "Nochmal" "Pause 20 s"
  # Nach 4 s klappt der Knopf wieder zurück: abwarten, dann beide Tipps direkt hintereinander.
  v=8; while [ $v -gt 0 ] && texte | grep -q "Nochmal"; do v=$((v - 1)); done
  tippe_zweimal "Song beenden"
  pruefe pause-seite 5 "Stille Pause" "Test Zwei" "Jetzt starten" "Anderer Song" "Anhalten"
  # 5 In der Pause anderer Song: Countdown läuft weiter
  tippe "Anderer Song"
  pruefe liste-in-pause 5 "kommt nach der Pause" "Test Drei"
  tippe "Test Drei"
  pruefe pause-mit-anderem-song 5 "Stille Pause" "Test Drei" "Rückgängig"
  tippe "Jetzt starten"; tippe "Pause"
  pruefe jetzt-starten 5 "Test Drei" "Beenden"
  # 6 Set wechseln während ein Song läuft: beginnt erst danach
  tippe "Zur Setliste"; tippe "Set A"
  pruefe sets 5 "Set B" "Set A"
  tippe "2 · Set B"
  pruefe set-danach 5 "beginnt nach diesem Song" "Danach · Set B"
  tippe "Abspielen"
  pruefe set-b-bereit 15 "Set B" "Bereit · 1 / 2" "Test Drei"
  # 7 Wunschsong aus dem Archiv
  tippe "Zur Setliste"; tippe "Wunschsong"
  pruefe archiv-wunsch 5 "Wunschsong für Set B" "Antippen = als Nächstes"
  tippe "tippen zum Springen"; tippe "T"
  tippe "Take on Me"
  pruefe wunsch-bereit 5 "Take on Me" "Bereit" "Rückgängig"
  # 8 Lautstärketaste auf der Bühne ohne Text: nichts passiert, Bühne bleibt
  $G key 25 >/dev/null
  pruefe lautstaerketaste 4 "Take on Me" "Bereit"
  # 9 Vorbereiten ist getrennt: Set bearbeiten
  tippe "Zur Setliste"; tippe "Set B"; tippe "Set-Menü" 1
  tippe "Set bearbeiten"
  pruefe set-bearbeiten 5 "Bearbeiten" "Umstellen" "Songs" "20 s" "dann weiter"
  # 10 Pause mit einem Tipp umschalten (Take on Me steht auf 30 s): 1 min, 2 min, 0 s, 10 s
  tippe "Pause umschalten"
  pruefe pause-umschalten 4 "1 min"
  tippe "Pause umschalten"; tippe "Pause umschalten"; tippe "Pause umschalten"
  pruefe pause-rundum 4 "10 s" "20 s"
  # 11 Karte: fünf Pausen-Knöpfe, Titel ganz
  tippe "Take on Me"
  pruefe karte-pausen 5 "Take on Me" "0 s" "10 s" "30 s" "1 min" "2 min"
  tippe "Schließen"
  # 12 Schon im Gig: grau, Rückfrage vor dem Einplanen
  tippe "Songs"
  pruefe archiv-songs-dazu 5 "Songs dazu für"
  tippe "tippen zum Springen"; tippe "T"
  pruefe archiv-grau 5 "Take on Me" "eingeplant · Set B"
  $G key 25 >/dev/null   # eine Seite weiter (Lautstärke leiser = vor)
  warte 5 "Test Zwei" >/dev/null
  tippe "Test Zwei"
  pruefe rueckfrage 5 "Nochmal einplanen?" "Test Zwei" "Ja, auch hier" "Abbrechen"
  tippe "Abbrechen"
  pruefe abbrechen 5 "Songs dazu für"

  aufraeumen
  bericht
}

case "${1:-}" in
  bauen) bauen ;;
  installieren) installieren ;;
  ablauf) ablauf ;;
  alles) bauen; ablauf ;;
  *) sed -n 2,14p "$0" ;;
esac
