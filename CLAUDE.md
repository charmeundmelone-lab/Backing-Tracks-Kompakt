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

## Übergabe an die nächste Session (geschrieben 2026-11-09, ca. 16:30)

**Stand:** `main`, alles committet und gepusht. Der neueste Bau ist auf dem Kompakt installiert.

**Erledigt am 9.11. (zweite Session), am Gerät geprüft:**
- Titelbalken der Bühne springt nicht mehr: feste Höhe, Zeit mit gleich breiten Ziffern (`tnum`)
  in fester Breite, Titel einzeilig und verkleinert sich bis 22 sp (`EinzeilerPassend`).
- Standard am Songende ist jetzt `Ende.ANHALTEN` (nächster Song liegt bereit, startet nicht).
  Beschriftung der Karte unverändert. In der Setliste steht das Kästchen jetzt bei der
  Ausnahme: „weiter“ statt „hält an“. Im Testgig sind alle Einträge auf den neuen Standard gesetzt.
- Zurück-Knopf „‹“ oben links auf allen Seiten außer der Startseite (`ZurueckKnopf`, über
  `Rahmen` bzw. `KopfBand(zurueck = …)`), auch Mixer und Editor (dort einheitlich gerahmt).
- Blätterleiste zeigt „auch mit den Lautstärketasten“, sobald es mehr als eine Seite gibt.
- Set-Ende bleibt als Kasten auf der Bühne stehen (`Zustand.setDurch`), im Jetzt-Band steht
  „Set durch · bereit“. Weg erst, wenn gespielt, ein anderes Set gewählt oder ein Song von Hand
  bereitgelegt wird. Probehören lässt ihn stehen.
- Archiv-Raster A–Z passt mit Jetzt-Band (geprüft).

**Neue Testdaten:** Gig „Kurztest“ (Set 1: Test Eins, Test Zwei; Set 2: Put your records drums new,
Test Drei) zum schnellen Prüfen von Songende und Set-Ende. Löschen nur mit Jans OK.

**Vor jedem Tipp am Kompakt:** Jan fragen oder per `dumpsys window | grep mCurrentFocus` prüfen,
ob er gerade selbst tippt.

**Als Nächstes:**
1. Am Gerät testen, was noch fehlt:
   - Ansage aufnehmen (Mikrofon-Erlaubnis muss Jan selbst bestätigen; Offline+-Schalter trennt
     das Mikrofon), danach Ansage in der Automatik (läuft nur rechts).
   - Einmessen-Bildschirm mit „Test Eins“ (8 s) oder einem echten Song.
   - Mixer mit dem Einzelspuren-Song „Country Roads“ (Spuren Click, Cues → rechts, Drums, Trax →
     links), Stummschalten, Stufen.
   - Probe im Editor, PDF-Import.
   - Wiedergabe mit ausgeschaltetem Bildschirm in der echten App (Ton-Test-App lief ohne Aussetzer).
2. Danach: App-Name mit Jan festlegen (ganz am Ende, Jans Wunsch), Symbol, Aufräumen der Testdaten
   (Gigs „Testgig“ und „Kurztest“, Ordner `Music/Backing Tracks/Kurztest` und `Einzelspuren` nur
   mit Jans OK löschen).

**Bekannte Kleinigkeiten:** Platzhalter-Lyrics für Tests stehen in „Test Eins“ und „Country Roads“
(selbst geschrieben, keine echten Songtexte). „Frei spielen“ aus der alten App ist nicht übernommen.
Im Mixer gibt es jetzt „‹“ und „Fertig“, beide gehen zurück.
