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
Später am selben Tag: alle 14 Bildschirme gezeichnet und am Kompakt geprüft, USB-Audio-Weg
ohne Kabel geprüft (alles vorhanden). Noch kein App-Code. Nächster Schritt laut `PLAN.md`:
Kern aus der alten App übernehmen und durchsehen, dann Bildschirm für Bildschirm bauen.
