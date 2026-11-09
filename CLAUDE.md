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

## Übergabe an die nächste Session (geschrieben 2026-11-09, ca. 16:15)

**Stand:** `main`, alles committet und gepusht. GitHub-Bau: Tests und Release-APK bauen grün,
nur der Artefakt-Upload scheiterte am vollen Artefakt-Speicher des Kontos (Upload ist seitdem
`continue-on-error`). Auf dem Kompakt ist ein etwas älterer Bau installiert. Der neueste Bau
(eingerückte Folgezeilen in den Lyrics, breitere Markierungen „0:10“/„hält an“ in der Setliste)
ist im Repo, aber noch **nicht installiert**, weil Jan das Gerät gerade selbst benutzt hat.

**Vor jedem Tipp am Kompakt:** Jan fragen oder per `dumpsys window | grep mCurrentFocus` prüfen,
ob er gerade selbst tippt (am 9.11. kam ein Bildschirmwechsel von ihm, nicht von der App).

**Als Nächstes, in dieser Reihenfolge:**
1. **Sichtbarer Zurück-Knopf überall** (Jans Orientierungs-Frage, wichtigster offener Punkt):
   Setliste, Sets eines Gigs, Archiv und Mixer haben keinen „‹ Zurück“ auf dem Bildschirm, nur
   die Android-Zurück-Geste. Einheitlich oben links einbauen (z. B. im `Rahmen`-Unterkopf bzw.
   im schwarzen Band), ohne das „Jetzt“-Band zu verdrängen.
2. **Lautstärketasten-Hinweis** auch in Listen sichtbar machen (z. B. klein in der Blätterleiste).
3. **Set-Ende-Hinweis** nicht nur 5 s als Meldung, sondern bleibend auf der Bühne zeigen
   („Set 1 ist durch, Set 2 liegt bereit“), bis gespielt wird.
4. Neuen Bau installieren und am Gerät testen, was noch fehlt:
   - Ansage aufnehmen (Mikrofon-Erlaubnis muss Jan selbst bestätigen; Offline+-Schalter trennt
     das Mikrofon), danach Ansage in der Automatik (läuft nur rechts).
   - Einmessen-Bildschirm mit „Test Eins“ (8 s) oder einem echten Song.
   - Mixer mit dem Einzelspuren-Song „Country Roads“ (Spuren Click, Cues → rechts, Drums, Trax →
     links), Stummschalten, Stufen.
   - Probe im Editor, PDF-Import.
   - Wiedergabe mit ausgeschaltetem Bildschirm in der echten App (Ton-Test-App lief ohne Aussetzer).
5. Danach: App-Name mit Jan festlegen (ganz am Ende, Jans Wunsch), Symbol, Aufräumen der Testdaten
   (Gig „Testgig“, Ordner `Music/Backing Tracks/Kurztest` und `Einzelspuren` nur mit Jans OK löschen).

**Bekannte Kleinigkeiten:** Archiv-Raster A–Z ist mit Jetzt-Band knapp (Zellen schon auf 50 dp
verkleinert, am Gerät noch nicht geprüft). Platzhalter-Lyrics für Tests stehen in „Test Eins“ und
„Country Roads“ (selbst geschrieben, keine echten Songtexte). „Frei spielen“ aus der alten App ist
nicht übernommen.
