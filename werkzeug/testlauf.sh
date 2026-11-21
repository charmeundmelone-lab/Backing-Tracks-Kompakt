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
#   sh werkzeug/testlauf.sh schnell        bauen, installieren, ablauf in einem Rutsch (nur mit Jans Ja)
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

# wische "Text" rechts|links: über die Zeile mit diesem Text wischen (Hauptbildschirm).
wische() {
  vorne || abbruch "TrikTrak ist nicht vorne."
  $G wisch "$1" "$2" >/dev/null || { echo "FEHLT Zeile „$1“"; FEHLER=$((FEHLER + 1)); BERICHT="$BERICHT|FEHLT Zeile $1"; return 1; }
}

# lange "Text": lange auf die Zeile mit diesem Text drücken (Einstellungen).
lange() {
  vorne || abbruch "TrikTrak ist nicht vorne."
  $G lang "$1" >/dev/null || { echo "FEHLT Zeile „$1“"; FEHLER=$((FEHLER + 1)); BERICHT="$BERICHT|FEHLT Zeile $1"; return 1; }
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
  # Erst Home: TrikTrak verlässt sich normal und gibt dabei "Bitte nicht stören" und die
  # Lautstärke zurück. (Lehre 15.11.: Ein hartes force-stop ließ "Bitte nicht stören" an,
  # Anrufe kamen nicht mehr durch.) Erst danach hart beenden.
  adbs shell input keyevent 3
  ping -n 3 127.0.0.1 >/dev/null 2>&1 || sleep 2
  adbs shell am force-stop $PKG
  runas cp files/protokoll-test.txt files/protokoll-test-letzter.txt 2>/dev/null
  adbs exec-out run-as $PKG cat files/protokoll-test.txt > "$O/protokoll-test.txt" 2>/dev/null
  runas rm -f files/testmodus files/test-leise files/test-bestand.json files/protokoll-test.txt
  if adbs shell dumpsys notification | grep -q "mZenMode=ZEN_MODE_OFF"; then echo "OK    „Bitte nicht stören“ ist aus"
  else echo "ACHTUNG: „Bitte nicht stören“ ist noch an – TrikTrak öffnen und mit Home verlassen"; FEHLER=$((FEHLER + 1)); fi
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

  # Hauptbildschirm (Fassung 9, 20.11.): Liste mit schwarz markiertem Song „JETZT“, Leiste nennt
  # „Danach“, Text geht beim Start von selbst auf und am Songende zu, Stopp = an den Anfang.
  # Testdaten: Set A = Test Eins (mit Text, Automatik), Test Zwei (Automatik), Test Drei (hält an);
  # Set B = Test Drei, Test Eins. Alle Testsongs 8 s, Pause 20 s.
  # 1 Öffnen am Gig-Tag = Hauptbildschirm, erstes Set, erster Song markiert, Leiste zeigt „Danach“
  pruefe start 10 "Set A" "Archiv" "Jetzt dran" "Test Eins" "Danach" "Test Zwei" "Bereit" "Abspielen"
  vorne || abbruch "TrikTrak kam nicht nach vorne (Bildschirm gesperrt?)."
  # 2 Play: Test Eins hat Text, also geht der Text von selbst auf; anhalten, dann Stopp = an den Anfang
  tippe "Abspielen"; tippe "Pause"
  pruefe text-automatisch 5 "Test Eins" "Liste" "Angehalten" "Stopp"
  tippe "Stopp"
  pruefe stopp-anfang 5 "Archiv" "Jetzt dran" "Test Eins" "Bereit" "Abspielen"
  # 3 Pausen-Kästchen: Tipp schaltet weiter (Test Zwei, das zweite Kästchen: 20 s → 30 s)
  tippe "Pause umschalten" 1
  pruefe pause-kaestchen 4 "30 s"
  # 4 Wischen nach links = ans Ende, dann Rückgängig
  wische "Test Zwei" links
  pruefe wisch-links 5 "Test Zwei“ steht jetzt am Ende" "Rückgängig"
  tippe "Rückgängig"
  pruefe rueckgaengig 5 "Zurückgenommen"
  # 5 Wischen nach rechts, solange nichts läuft = liegt sofort bereit (wird JETZT)
  wische "Test Drei" rechts
  pruefe wisch-rechts 5 "Test Drei“ liegt bereit" "Rückgängig"
  # 6 Lang drücken = Einstellungen (startet keine Musik); Tipp = auswählen (21.11., wie die alte App)
  lange "Test Zwei"
  pruefe antippen-karte 5 "Test Zwei" "0 s" "10 s" "30 s" "1 min" "2 min"
  tippe "Schließen"
  tippe "Test Zwei"
  pruefe tipp-waehlt-aus 5 "Test Zwei“ liegt bereit" "Rückgängig"
  tippe "Rückgängig"
  pruefe tipp-rueckgaengig 5 "Zurückgenommen"
  # 7 Song ohne Text: bleibt auf dem Hauptbildschirm; hält am Ende an, danach ist Test Eins dran
  tippe "Abspielen"
  pruefe ohne-text-bleibt 4 "Archiv" "Läuft" "Test Drei" "Stopp"
  pruefe songende-haelt-an 15 "Jetzt dran" "Test Eins" "Bereit" "Danach" "Test Zwei"
  # 8 Song mit Text und Automatik: Text geht auf, am Songende zu, dann Pause in der Leiste
  tippe "Abspielen"
  pruefe text-auf 5 "Test Eins" "Liste" "Stopp"
  pruefe text-zu-pause 20 "Pause · gleich" "Test Zwei" "Jetzt starten" "Anhalten"
  # Die Testpause ist kurz (20 s): ist sie schon abgelaufen, läuft der Song von selbst.
  texte | grep -qF "Jetzt starten" && tippe "Jetzt starten"; tippe "Pause"
  pruefe jetzt-starten 5 "Test Zwei" "Stopp" "Archiv"
  # 9 Set wechseln über den Set-Reiter, während ein Song läuft: beginnt erst danach
  tippe "Set A"
  pruefe set-klappe 5 "Sets · Testlauf" "Set B" "Set bearbeiten" "Alle Gigs"
  tippe "Set B"
  pruefe set-danach 5 "Nach diesem Song beginnt „Set B“" "Danach"
  tippe "Abspielen"
  pruefe set-b-bereit 15 "Set B" "Bereit · 1 / 2" "Test Drei"
  # 9b (21.11., Jans Fehler): Song angespielt und angehalten, dann anderes Set wählen. Früher kam
  # „Noch kein Set gewählt“ bzw. das Set kam nie; jetzt ist das gewählte Set sofort da.
  tippe "Abspielen"; tippe "Pause"
  tippe "Set B"; tippe "Set A"
  pruefe set-trotz-pause 5 "Set A" "Testlauf ·" "Archiv"
  tippe "Set A"; tippe "Set B"
  pruefe wieder-set-b 5 "Set B" "Bereit · 1 / 2" "Test Drei"
  # 10 Reiter Archiv: nach rechts wischen = liegt bereit
  tippe "Archiv"
  pruefe archiv 5 "Wischen plant in „Set B“ ein" "Langsam" "springen"
  fuss=$(texte | grep -o "^A–Z · Seite [^[]* springen" | head -1)
  tippe "$fuss"; tippe "T"
  wische "Take on Me" rechts
  pruefe archiv-wisch-rechts 5 "Take on Me" "liegt bereit" "Rückgängig"
  # 11 Nach oben wischen blättert im Archiv (bis Test Zwei zu sehen ist); schon eingeplant = Rückfrage
  v=4; while [ $v -gt 0 ] && ! texte | grep -qF "Test Zwei"; do vorne && adbs shell input swipe 240 430 240 230 300; v=$((v - 1)); done
  wische "Test Zwei" links
  pruefe rueckfrage 5 "Nochmal einplanen?" "Test Zwei" "Ja, ans Ende" "Abbrechen"
  tippe "Abbrechen"
  pruefe abbrechen 5 "Archiv" "Langsam"
  # 11b Kapo-Filter (21.11.): nur Songs mit Kapo 2, dann wieder alle
  tippe "2"
  pruefe kapo-filter 5 "Kapo" "alle" "Kapo 2"
  tippe "alle"
  # 12 Text geht von selbst auf; „Liste“ führt zurück und der Text springt nicht wieder auf
  tippe "Set B"
  wische "Test Eins" rechts
  pruefe text-knopf 5 "Jetzt dran" "Test Eins" "Bereit" "Text zeigen"
  tippe "Abspielen"
  pruefe text-auf-set-b 5 "Test Eins" "Liste" "Stopp"
  tippe "Zur Setliste"
  # Der Testsong ist nur 8 s lang: beim Nachsehen kann er schon zu Ende sein, darum kein "Stopp".
  pruefe liste-bleibt 4 "Archiv" "Set B"
  pruefe zurueck-nach-songende 20 "Archiv" "Set B" "Abspielen"
  # 13 Vorbereiten: Set bearbeiten über die Set-Klappe
  tippe "Set B"
  tippe "Set bearbeiten"
  pruefe set-bearbeiten 5 "Bearbeiten" "Umstellen" "Songs"
  tippe "Fertig"
  pruefe zurueck-haupt 5 "Archiv" "Set B"

  aufraeumen
  bericht
}

case "${1:-}" in
  bauen) bauen ;;
  installieren) installieren ;;
  ablauf) ablauf ;;
  alles) bauen; ablauf ;;
  schnell) bauen; installieren; ablauf ;;
  *) sed -n 2,15p "$0" ;;
esac
