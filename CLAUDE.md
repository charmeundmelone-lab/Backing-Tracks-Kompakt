# Backing-Tracks Kompakt — Projektkontext für Claude

## Session-Start

1. Branch prüfen: `git branch` muss `* main` zeigen. Nur `main`, kein Feature-Branching.
2. `PLAN.md` lesen (Entscheidungen, Design, Bedienkonzept, offene Punkte).
3. Abschnitt "Letzter Stand" unten lesen.

## Arbeitsweise

- Antworten auf Deutsch, einfach erklärt (Jan ist kein Entwickler), keine Floskeln.
- Planungsfragen als anklickbare Optionen, eine Frage pro Runde (GrillMe-Stil).
- Bei Neubau nach Vorbild: nicht Bekanntes neu abfragen, sondern fragen, was in der alten App
  funktioniert und was nicht. Das Verhalten der alten App gilt als gut, Loop fällt weg.
- Design: Jan hat Claude die volle Kontrolle übergeben. Richtung B "Signal" ist bestätigt.
- Bühne ↔ Setliste muss jederzeit gehen, auch während der Wiedergabe, ohne sie zu stören.

## Gerät und Werkzeuge

- Mudita Kompakt per USB-adb: `"$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe" -s LD20241004967`.
  Wird es nicht erkannt: `adb kill-server`, `adb start-server`, kurz warten.
- Kompakt hat eine PIN-Sperre: Bildschirm nicht ausschalten, solange Claude noch daran testen
  will. Vor Tipps prüfen, welche App vorne ist (Jan nutzt das Gerät selbst).
- Nie `am start -S` / force-stop auf WebView-Apps: danach startet der WebView-Renderer nicht mehr
  (schwarzer Bildschirm), nur ein Neustart hilft. Vorschauen deshalb als PNG.
- Mudita bremst nachinstallierte Apps im Hintergrund. Der Ton-Test lief trotzdem durch
  (Vordergrund-Dienst). Für die echte App beim Live-Test erneut prüfen.
- Laptop-Platte C: ist fast voll (ca. 7 GB frei). Nach jedem Bau `df -h /c` prüfen, kein Emulator.
- Mini-Apps ohne Gradle bauen: `sh entwuerfe/werkzeug/bau.sh betrachter` bzw. `tontest`.
- Vorschau-Bilder der Entwürfe: `sh entwuerfe/werkzeug/render.sh` (node + Headless-Chromium
  aus `~/AppData/Local/ms-playwright`), dann `bau.sh betrachter` und `adb install -r`.
- Canvas online: https://claude.ai/artifact/7BXLnocEYEibvLqdEqzeHo. Die Dateien in
  `entwuerfe/canvas/` sind die Quelle; nach Änderungen im Canvas dort nachziehen.

## Letzter Stand

**2026-11-09:** Repo angelegt. Planung (GrillMe Welle 1), Ton-Test, Design B und Bedienkonzept
ohne Scrollen sind fertig und in `PLAN.md`. Entwürfe online:
https://claude.ai/artifact/7BXLnocEYEibvLqdEqzeHo, auf dem Kompakt als App "Entwürfe".
Später am selben Tag: alle 14 Bildschirme gezeichnet, dann die App gebaut (`app/`), am Kompakt
installiert und die Kernabläufe getestet, Details in `PLAN.md` unter "Stand der App".
Lokal bauen: `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew --no-daemon
:app:assembleDebug` (local.properties mit `sdk.dir=C:/Users/HUAWEI/AppData/Local/Android/Sdk`),
installieren mit `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
Testdaten direkt ändern: `adb exec-out run-as show.jangregersen.buehne cat files/bestand.json`
(Git-Bash: bei Gerätepfaden `MSYS_NO_PATHCONV=1` setzen).

## Übergabe an die nächste Session (geschrieben 2026-11-09, ca. 19:50)

**Stand:** `main`, alles committet und gepusht. Repo ist seit 9.11. öffentlich (Jans Wunsch).
Neuester Bau ist auf dem Kompakt installiert.

**Erledigt am 9.11. abends (dritte Session):**
- Ton-Fehler behoben (Jan: "prasselnd, zu langsam, grauenhaft"). Ursache: Einzelspuren liefen
  über je einen ExoPlayer mit eigener AudioTrack und lagen bis 0,3 s auseinander. Neu:
  `audio/Mischpult.kt` (ein Faden, eine AudioTrack float, alle Spuren blockweise gemeinsam
  gemischt) und `audio/Quellen.kt` (WAV direkt 8/16/24/32 bit + float, sonst MediaCodec,
  Umrechner bei abweichender Rate). Media3 ist raus. Stereo-Datei war schon vorher bitgenau,
  jetzt in voller 24-Bit-Auflösung.
- Prüfweg Mitschnitt: `touch files/mitschnitt.an` (run-as), dann schreibt jedes Mischpult 20 s
  float32-Stereo nach `files/mitschnitt-song<ID>-<rate>.f32`. Vergleich am Laptop:
  `RATE=44100 node werkzeug/mitschnitt-vergleich.js mit.f32 L:Drums.wav L:Trax.wav R:Click.wav R:Cues.wav`
  bzw. `STEREO:datei.wav`. Ergebnis 9.11.: Stereo, Einzelspuren und Mixer (Click aus, Drums −2)
  exakt. Danach Flag wieder löschen.
- Bedienzonen (PLAN.md "Bedienzonen"): Setliste ohne Menü, unten ‹ + Song · Umstellen ›, oben
  rechts "Sets". Archiv im Hinzufügen-Modus mit "Als Nächstes | Ans Ende". Hinweise unten.
- Am Gerät geprüft: Play/Pause/Songende, Mixer, Editor-Probe, Einmessen inkl. "10 s zurück",
  PDF-Import, Wiedergabe mit Bildschirm aus (61 s, Echtzeit, 0 Aussetzer, Wakelock aktiv).
- Fehler behoben: Einmessen zeigte nach Songende weiter "Jetzt beginnt" und speicherte 0:00.

**Jans Fehlerbild (geklärt 9.11. ca. 20:00):** Stereo-Song aus der Setliste, über den Lautsprecher,
"viel langsamer und ruckelig", mit der alten Media3-Engine. Neue Engine danach auf der Bühne bei
eingeschaltetem Bildschirm geprüft: ganzer Song (Am I wrong, 2:26) in Echtzeit, 0 Aussetzer laut
`track.underrunCount`, Mitschnitt (Careless Whisper) bitgenau. Die alte Engine ist nicht mehr
nachmessbar; Ursache dort vermutlich Ruckeln der Abspiel-Fäden neben dem E-Ink-Neuzeichnen.
Wenn Jan es mit der neuen Engine noch hört: `adb logcat -s Mischpult` zeigt "lief leer".

**Als Nächstes:**
1. Erledigt 9.11. ca. 20:15: Jan hat die neue Engine am Kompakt gehört, "klingt sauber".
2. Erledigt 9.11. ca. 20:20: Jan hat die Mikrofon-Erlaubnis erteilt und für "Test Zwei" (Kurztest)
   Ansage vorher und nachher aufgenommen (je ~7 s). Automatik geprüft: Test Eins endet → Ansage
   vor Test Zwei läuft (AudioFlinger: L -inf, R 0 dB, also nur rechts) → Test Zwei startet selbst.
   Im Kurztest stehen jetzt beide Einträge von Set 1 auf "Weiter im Set". Nicht geprüft: Ansage
   nach dem letzten Song eines Sets (spielt die App bewusst nur, wenn ein nächster Song folgt).
3. App-Name mit Jan, Symbol, Aufräumen der Testdaten (Gigs "Testgig", "Kurztest", Ordner
   `Music/Backing Tracks/Kurztest` und `Einzelspuren` nur mit Jans OK).

**Vor jedem Tipp am Kompakt:** `dumpsys window | grep mCurrentFocus` prüfen. Beim Navigieren
per adb Texte über `uiautomator dump` suchen statt feste Koordinaten: das schwarze Band
verschiebt das Layout (so öffnete sich einmal versehentlich "Neuer Gig", abgebrochen).
Testdaten direkt ändern nur bei gestoppter App (`am force-stop` ist bei dieser App erlaubt).

**Bekannte Kleinigkeiten:** Platzhalter-Lyrics in "Test Eins" und "Country Roads" (selbst
geschrieben). "Frei spielen" aus der alten App fehlt weiter.
