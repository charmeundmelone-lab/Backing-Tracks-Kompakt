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

## Übergabe an die nächste Session (geschrieben 2026-11-13, ca. 00:30)

**Stand:** `main`, committet und gepusht. Der Code baut, alle Unit-Tests sind grün
(`./gradlew --no-daemon -q :app:testDebugUnitTest`). Aufgaben: `PLAN.md`, Abschnitt
"Umbau 13.11. nachts" → Arbeitsliste **A1–A7**. Dort stehen auch Jans Wünsche dazu.

**GERÄTE-REGEL:** Am Kompakt nichts installieren oder antippen, bevor Jan es im Chat freigibt.
Jan spielt am **Fr 13.11.** den Gig "Bar Eriksson" (Set 1, 8 Songs, keiner mit Lyrics) und hat
den Kompakt zuletzt selbst benutzt (Set lief). Vor JEDEM Tipp: `sh werkzeug/geraet.sh fokus`.
Läuft TrikTrak mit einem Song, sofort Finger weg.

**Was installiert ist:** Bau vom 12.11. ca. 23:50 (Setliste zweizeilig, Danach-Zeile, Texte holen,
altes Einmessen). Der Umbau "eigener Ablauf + Zeilen-Tipps" (PLAN.md) ist NICHT installiert.
Testdaten auf dem Gerät: Gig "Testlauf" (Set 1 mit "Test Eins", nur mit Jans OK löschen),
"Two Princes" hat Text + Einmess-Punkte (aus dem Original umgerechnet, nicht von Jan getippt).
Medienlautstärke: Jan nutzt 5/15. Für Tests leise stellen und **danach sofort zurück auf 5**
(in TrikTrak blättern die Lautstärketasten, Jan kann dort nicht selbst lauter drehen).

**Werkzeuge:**
- Bauen: `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew --no-daemon -q :app:assembleDebug`,
  danach `df -h /c` (Platte fast voll, ca. 6 GB frei).
- Installieren: `"$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe" -s LD20241004967 install -r app/build/outputs/apk/debug/app-debug.apk`
  (vorher Daten sichern: `MSYS_NO_PATHCONV=1 adb … exec-out run-as show.jangregersen.buehne cat files/bestand.json > .geraet/bestand-DATUM.json`).
- Gerät steuern: `sh werkzeug/geraet.sh fokus | texte | tap "Text" | shot name | key 25`
  (Bilder in `.geraet/`, nicht im Repo). Starten: `adb … shell monkey -p show.jangregersen.buehne -c android.intent.category.LAUNCHER 1`.
- Archiv-Navigation: A–Z-Knopf unten → Buchstabe → `key 25` blättert eine Seite weiter.
- Song zum Testen ohne Set: Editor › Lyrics › "Auf der Bühne testen".

**Code-Landkarte (nur das Nötige lesen):** `lyrics/Zeitplan.kt` (Teil, Ablauf, Zeitplan),
`lyrics/Erkennung.kt` (Etiketten-Vorschlag), `lyrics/Abschnitte.kt` (Text ↔ Abschnitte),
`ui/SeiteEinmessen.kt`, `ui/SeitenTexte.kt` (Texte holen, Teile & Ablauf, AbschnittPille),
`ui/SeiteBuehne.kt` (Bühne, LyricsBereich), `netz/Lrclib.kt`, Tests in `app/src/test/…/LyricsTest.kt`.

**Jans Maßstab (immer):** aus 60–80 cm am Mikroständer lesbar, Titel und nächster Song groß und
vollständig, nichts springt, jeder Knopf sagt in einfachen Worten, was passiert, kein Tipp löst
etwas Unumkehrbares aus. Antworten auf Deutsch, einfach, ohne Floskeln; Fragen als anklickbare
Optionen, eine pro Runde.

**Optik (Jan, 13.11.):** Wo nötig, darf die Session optische Anpassungen selbst entscheiden und
umsetzen (Größen, Abstände, Anordnung, Darstellung von Etiketten/Keil/Kontext), solange Richtung
B "Signal" erhalten bleibt (Schwarz auf Weiß, Barlow Condensed, schwarzes Kopfband, Bühnenschrift
Stufe 3). Jede sichtbare Änderung am Gerät per Foto prüfen und Jan kurz zeigen.

**Bekannte Kleinigkeiten:** Testsongs "Test Eins/Zwei/Drei" im Archiv, Platzhalter-Lyrics in
"Test Eins" und "Country Roads", "Frei spielen" aus der alten App fehlt, App-Symbol offen
(inkOS zeigt nur Namen).
