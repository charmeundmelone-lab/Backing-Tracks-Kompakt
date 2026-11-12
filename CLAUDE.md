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

## Übergabe an die nächste Session (geschrieben 2026-11-12, ca. 23:55)

**Stand:** `main`, alles committet und gepusht. Arbeitsliste und Entscheidungen: `PLAN.md`,
Abschnitt "Lyrics-Paket (Loop ab 12.11.)" (L1–L7 erledigt, L8–L10 offen).

**GERÄTE-REGEL: Am Kompakt nichts installieren oder antippen, bis Jan es freigibt.** Jan spielt
am Fr 13.11. den Gig "Bar Eriksson" (Set 1, 8 Songs, keiner mit Lyrics). Installiert ist der
Bau mit neuem Bühnenkopf und neuer Danach-Zeile (Commit 5eded7c). Neuere Commits (Setliste
zweizeilig, Probe stellt bereiten Song wieder her, Überspringen in der Trefferliste,
Pause-Ansicht zweizeilig) sind NICHT installiert. Daten-Sicherung vor den Tests:
Scratchpad der Session vom 12.11. (`bestand-vorher.json`), danach nur "Two Princes" geändert.

**Neu seit 12.11. (Lyrics-Paket):**
- Texte holen aus LRCLIB (`netz/Lrclib.kt`, User-Agent ohne persönliche Daten, Pausen,
  verständliche Fehler). Archiv: Knopf "Texte holen" neben Gigs/Archiv (alle ohne Text),
  Song-Editor › Lyrics: "Text suchen". Ablauf: Ist es dieser? › Anderer/Suchwort › Abschnitte
  prüfen › nächster Song › Fertig-Liste. Texte liegen danach fest in `bestand.json` (offline).
- Abschnitts-Erkennung `lyrics/Erkennung.kt` (Wiederholung = Refrain usw.), trifft nur teils,
  deshalb Seite "Abschnitte prüfen" (`ui/SeitenTexte.kt`, Etikett wählen springt weiter).
- Zeilen-Zeitplan `lyrics/Zeitplan.kt`: Zeitmarken des Originals auf Jans Einmess-Punkte
  gestreckt. Bühne: Etikett-Pille (Refrain schwarz + Strich, Bridge gestrichelt), "dann …",
  schwarzer Keil springt Zeile für Zeile, Seite blättert 1,5 s vorher. Am Gerät geprüft.
- Song-Felder neu: `zeilenZeiten`, `originalMs`, `markenVorlaeufig`. Vorläufige Punkte nur bei
  ±3 s gleicher Länge (bei Jans Tracks selten, die meisten sind gekürzt).
- Einmessen zeigt den laufenden Abschnitt (gesungene Zeile fett) und den Anfang des nächsten;
  "Weiter einmessen" (startet 5 s vor dem letzten Punkt) / "Neu einmessen".
- "Auf der Bühne testen" (Editor › Lyrics) = `Seite.ProbeBuehne`, Song ohne Set.
- Bühnenkopf: Titel immer 34 sp, bricht zweizeilig um statt zu schrumpfen; Restzeit oben rechts.

**Als Nächstes (nur mit Jans OK, frühestens nach dem Gig):**
1. Neuesten Bau installieren, Setliste (zweizeilige Zeilen) und Danach-Zeile am Gerät ansehen.
2. L8: Sammellauf "Texte holen" mit mehreren Songs, Bühne mit Offline+ an.
3. L9: Gesamttest aus Jans Sicht (Gitarre um, Kompakt am Mikroständer, 60–80 cm, kurze Blicke).
4. Two Princes hat Test-Einmesspunkte (aus dem Original umgerechnet) – Jan fragen oder neu
   einmessen lassen.

**Vor jedem Tipp am Kompakt:** `dumpsys window | grep mCurrentFocus` prüfen. Navigation per
`uiautomator dump` + Text suchen (Hilfsskript war `k.sh` im Scratchpad: tap "Text", shot, texte).
Lautstärketasten per adb: 25 = vor, 24 = zurück. Medienlautstärke für Tests leise stellen und
danach zurück (Jan: 5/15). Testdaten direkt ändern nur bei gestoppter App.

**Bekannte Kleinigkeiten:** Platzhalter-Lyrics in "Test Eins" und "Country Roads". Testsongs
"Test Eins/Zwei/Drei" liegen noch im Archiv. "Frei spielen" aus der alten App fehlt weiter.
App-Symbol offen (inkOS zeigt nur Namen, daher unwichtig).
