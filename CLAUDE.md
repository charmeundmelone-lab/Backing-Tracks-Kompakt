# Backing-Tracks Kompakt (TrikTrak) — Projektkontext für Claude

Zwei Programme in einem Repo: die App **TrikTrak** für den Mudita Kompakt (`app/`) und das
Laptop-Programm **TrikTrak Werkstatt** (`werkstatt/`). Beide benutzen denselben Text- und
Ablauf-Code.

## Session-Start

1. Branch prüfen: `git branch` muss `* main` zeigen. Nur `main`, kein Feature-Branching.
2. Unten "Übergabe an die nächste Session" lesen. Das reicht zum Weitermachen.
3. In `PLAN.md` nur lesen, was die Aufgabe braucht: die **Arbeitsliste B** ganz am Ende, bei
   Bedarf die Abschnitte "Laptop-Programm" und "Jans Rückmeldung 13.11.". Der Rest ist Geschichte.

## Arbeitsweise

- Antworten auf Deutsch, einfach erklärt (Jan ist kein Entwickler), keine Floskeln.
- Fragen an Jan als anklickbare Optionen, eine pro Runde. Jan diktiert oft: "Modita/Monitor"
  heißt Mudita.
- **Wenn Jan durcheinanderkommt oder unsicher ist: erst Vorschläge mit Favorit, nichts ungefragt
  umbauen.** Ein klares "Ja, so will ich es" von ihm ist der Auftrag.
- Jans Maßstab (immer): Er singt mit Gitarre, der Kompakt hängt am Mikroständer (60–80 cm), er
  ist **kurzsichtig**. Die Zeile, die er gerade singt, muss eindeutig erkennbar sein, mit Kontext.
  Sein Ablauf weicht oft vom Original ab. Alles groß lesbar, nichts springt, jeder Knopf sagt in
  einfachen Worten, was passiert, kein Tipp löst etwas Unumkehrbares aus.
- Optik: Die Session darf optische Anpassungen selbst entscheiden, solange Richtung B "Signal"
  bleibt (Schwarz auf Weiß, Barlow Condensed, schwarzes Kopfband). Jede sichtbare Änderung am
  Gerät per Foto prüfen und Jan kurz zeigen.
- Nach jedem fertigen Punkt: committen, pushen, in `PLAN.md` abhaken. Tokens sparen: nur nötige
  Dateien lesen, am Gerät meist `geraet.sh texte` statt Fotos.
- Das Repo ist **öffentlich**: nie Songtexte, `bestand.json` oder Jans Daten einchecken.

## Übergabe an die nächste Session (geschrieben 2026-11-13, mittags)

**Stand:** `main`, alles committet und gepusht. App und Werkstatt bauen, alle Tests grün
(`:app:testDebugUnitTest` 55 Tests, `:werkstatt:test` 11 Tests).

**Was am 13.11. passiert ist (kurz):**
1. Neuer Code "eigener Ablauf + Zeilen-Tipps" geprüft, Fehler behoben, Tests ergänzt, am Kompakt
   getestet (A1, A5, A6 erledigt). Jan: "Das Einmessen selbst hat gut geklappt."
2. Jan kam beim **Ordnen der Teile am Kompakt durcheinander** (er sieht dort nur den ersten
   Textschnipsel) und der **Bühnentext ist ihm zu klein**.
3. Daraus Jans Auftrag: ein **Laptop-Programm**. Stufe 1 ist gebaut (siehe unten).
4. Neu in der App: volle Medienlautstärke, solange sie offen ist.

**Jans Entscheidungen zum Laptop-Programm:**
- Text und Teile: NUR am Laptop. Am Mudita ansehen und einmessen, nicht mehr umbauen.
- Gigs, Setlisten, Songdaten, neue Songs, Ansagen: am Laptop UND weiter am Mudita.
- Maßgeblich sind die fertigen Songs in `C:\Users\HUAWEI\Music\Backing Tracks\Nothingphone`
  (42 WAV), nicht die Entwürfe in Studio One. Den Ordner darf man in "Mudita" umbenennen (noch
  nicht geschehen, vorher prüfen, ob etwas den Pfad benutzt).
- Reihenfolge (Claudes Entscheidung, Jan fragte, was sinnvoll ist): Stufe 1 Text & Teile + Studio
  One, Stufe 2 Gigs & Setlisten, Stufe 3 neue Songs, Songdaten, Ansagen.

**TrikTrak Werkstatt, Stand Stufe 1:**
- Start für Jan: Doppelklick auf `TrikTrak-Werkstatt.bat` im Repo-Ordner. Es öffnet die Seite
  `http://127.0.0.1:47811` im Browser (Anschluss weicht aus, wenn belegt). Fenster offen lassen.
- Bauen: `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew --no-daemon -q :werkstatt:test :werkstatt:installDist`.
  Nach jeder Änderung an der Werkstatt `installDist` laufen lassen, sonst startet die `.bat` den
  alten Stand.
- Arbeitsdaten AUSSERHALB des Repos: `C:\Users\HUAWEI\TrikTrak-Werkstatt\` (`bestand.json`,
  `zustand.json`, `sicherungen/`). Am 13.11. noch leer: Jan muss einmal "Vom Mudita holen".
- Kann: Daten vom Mudita holen (liest nur), Songliste, pro Song Karten in Ablauf-Reihenfolge mit
  vollem Text, Ziehen/▲▼/Doppeln/Weg, anderen Text auf einen Platz legen, Teil an einer Zeile
  teilen, an vorigen hängen, umbenennen, Text bearbeiten, Rückgängig, Rückfrage vor Zeitverlust.
  Studio One: Datei nach Namen finden oder wählen, Teile mit Zeiten zeigen, "Ablauf daraus
  vorschlagen", Text ein Fach weiter/zurück schieben.
- Seit 13.11. nachmittags auch: "Zum Mudita senden" (legt `files/eingang.json` ab, die App führt
  selbst zusammen, `bestand.json` fasst der Laptop nie an), holt beim Start automatisch, wenn der
  Mudita dranhängt, legt bei jedem Holen eine genaue Kopie `sicherungen/mudita-*.json` ab (nur bei
  Änderung), Knopf "Sicherungen" spielt eine Kopie ganz zurück (`"ganz": true`, App ab Fassung 3).
  Zweiter Start öffnet nur die laufende Seite (`/api/wer`). Desktop-Symbol "TrikTrak Werkstatt".
- Selbst testen ohne Jans Daten anzufassen: Kopie einer Sicherung in einen Scratch-Ordner legen,
  dann `java -Dtriktrak.daten=<Ordner mit /> -Dfile.encoding=UTF-8 -cp "werkstatt/build/install/werkstatt/lib/*" show.jangregersen.werkstatt.MainKt --ohne-browser`
  im Hintergrund starten, im eingebauten Browser `http://127.0.0.1:47811` öffnen oder per `curl`
  die `/api/…`-Aufrufe testen (POST braucht den Kopf `X-TrikTrak: 1`). Danach den Prozess beenden
  (PowerShell: `Get-NetTCPConnection -State Listen | ? LocalPort -in 47811..47820 | % { Stop-Process -Id $_.OwningProcess -Force }`;
  `netstat | grep LISTEN` findet hier nichts, Windows ist deutsch: "ABHÖREN"). Achtung: Hängt der
  Mudita dran, holt die Werkstatt beim Start (nur lesen) und legt in ihrem Ordner Sicherungen an.

**Studio One (an Jans Dateien geprüft, wichtig für alles Weitere):**
- Songs: `C:\Users\HUAWEI\Documents\Studio One\Songs*\<Ordner>\<Name>.song`, ZIP, darin
  `Song/song.xml`. Nur lesen. ZIP mit Latin-1 öffnen (Umlaute in Dateinamen im ZIP).
- Zeiten je Angabe in Vierteln (`timeFormat="2"`, mal Tempo = Sekunden pro Viertel) ODER in
  Sekunden (`timeFormat="0"`). Beides kommt vor, auch gemischt in einer Datei.
- Jan exportiert genau den Bereich seiner Arranger-Teile: Bei 15 von 21 Songs endet der letzte
  Teil auf 0,1 s am Ende der WAV. Die Zeiten passen also zum Track. Über 3 s Abweichung = andere
  Fassung, das Programm warnt (z. B. Wonderwall, Jolene).
- **Jans Labels sind lose** ("Vers" steht bei ihm auch über Refrains, Tippfehler wie "OURTO",
  "EN>D"). Deshalb: Seine Teile sind Zeit-Fächer, der Internet-Text wird der Reihe nach
  hineingelegt; Namen entscheiden nur, wo sicher kein Gesang ist. Probe: Bei "Two Princes" ergibt
  das exakt Jans selbst eingetippte Reihenfolge, Zeiten 0–2 s neben seinen Tipps.
- 31 der 42 fertigen Songs haben eine gleichnamige `.song`, 27 mit Arranger-Teilen. Der Rest
  braucht eine Auswahl von Hand (Knopf "Andere Datei") oder hat nur Marker.

**Kompakt (Gerät):**
- **GERÄTE-REGEL:** Nichts installieren oder antippen, bevor Jan es im Chat freigibt. Vor JEDEM
  Tipp `sh werkzeug/geraet.sh fokus`. Läuft TrikTrak mit einem Song: Finger weg. Jan spielte am
  Abend des 13.11. den Gig "Bar Eriksson"; danach fragen, wie es lief (Lautstärke, Pausen).
- Installiert: App-Fassung 3 (versionCode, 13.11. ca. 15 Uhr, mit Jans OK vor dem Gig): kann
  `eingang.json` (Senden und Wiederherstellen). Am Gerät beides geprüft, Daten danach unverändert.
  Rückweg zur Gig-Fassung vom Vormittag: `.geraet/vor-b2-2026-11-13.apk`, Daten von 14:50:
  `.geraet/bestand-2026-11-13-e.json`.
- Rückweg: `.geraet/alt-2026-11-13.apk` (Bau vom 12.11.), `.geraet/bestand-2026-11-13.json` (vor
  allen Tests), `.geraet/bestand-2026-11-13-d.json` (09:27, nach Jans eigenem Einmessen).
- Daten laut letzter Sicherung: 46 Songs, Gig "Bar Eriksson" (Set 1, 8 Songs), Test-Gig
  "Testlauf" (nur mit Jans OK löschen). "Two Princes": Jan hat selbst neuen Text geholt und
  eingemessen (10 Teile, alle eingemessen). Text haben außerdem: Country Roads (2×), Test Eins,
  There she goes.
- **Lautstärke:** TrikTrak stellt die Medienlautstärke beim Öffnen auf voll (15/15) und hält sie,
  solange die App zu sehen ist oder etwas läuft; beim Verlassen kommt die alte zurück
  (`audio/VolleLautstaerke.kt`). Noch nicht mit Jans Kabel an der Anlage geprüft. Für Tests mit
  Ton VORHER `adb … shell run-as show.jangregersen.buehne touch files/test-leise`, dann leise
  stellen, testen, **danach die Datei löschen und prüfen, dass sie weg ist**.
- Kompakt hat eine PIN-Sperre: Claude kann nicht entsperren. Gesperrt geht nur Sichern und
  Installieren, kein Tippen.

**Werkzeuge:**
- App bauen: `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew --no-daemon -q :app:testDebugUnitTest :app:assembleDebug`,
  danach `df -h /c` (Platte war lange fast voll; am 13.11. mittags 17 GB frei. Kein Emulator).
- Gerät: `"$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe" -s LD20241004967 …`. Vor dem
  Installieren Daten sichern: `MSYS_NO_PATHCONV=1 adb … exec-out run-as show.jangregersen.buehne cat files/bestand.json > .geraet/bestand-DATUM.json`,
  dann `adb … install -r app/build/outputs/apk/debug/app-debug.apk`.
- Steuern: `sh werkzeug/geraet.sh fokus | texte | tap "Text" [n] | shot name | key 25 | xy X Y`
  (Bilder in `.geraet/`, nicht im Repo). Starten: `adb … shell monkey -p show.jangregersen.buehne -c android.intent.category.LAUNCHER 1`.
  Symbol-Knöpfe (▲▼, Play) stehen nicht in `texte`: Koordinaten aus einem Foto nehmen.
- Am Gerät navigieren: Archiv → "A–Z"-Knopf → Buchstabe → `key 25` blättert. Listen merken sich
  die Seite, also vor `tap` mit `texte` prüfen, was zu sehen ist. In "Hinzufügen zu Set" schaltet
  ein Tipp auf einen Song ihn ein UND aus.
- Stolperstein in dieser Umgebung: Bash-Heredocs mit Kotlin/JS-Inhalt scheitern manchmal mit
  "unexpected EOF". Dateien dann mit dem Write-Werkzeug schreiben, Python-Hilfsskripte ebenso.
  `cmd /c datei.bat` findet Dateien im aktuellen Ordner hier nicht: vollen Pfad angeben.

**Code-Landkarte (nur das Nötige lesen):**
- Gemeinsam (App + Werkstatt, müssen frei von Android-Teilen bleiben): `lyrics/Abschnitte.kt`
  (Text ↔ Abschnitte), `lyrics/Zeitplan.kt` (Teil, Ablauf, Zeitplan, `nachTextAenderung`),
  `lyrics/Einmessung.kt` (Regeln des Einmessens), `lyrics/Abgleich.kt` (Laptop ↔ Kompakt:
  `folgeSetzen`, `textUndTeile`), `lyrics/Erkennung.kt`, `lyrics/Uebernahme.kt`,
  `daten/Modell.kt`, `daten/Aenderungen.kt`, `daten/BestandJson.kt`, `netz/Lrclib.kt`.
  Wer dort ändert, baut `:app:testDebugUnitTest` UND `:werkstatt:test`.
- Nur App: `ui/SeiteEinmessen.kt`, `ui/SeitenTexte.kt` (Texte holen, Teile & Ablauf),
  `ui/SeiteBuehne.kt` (Bühne, LyricsBereich), `ui/SeitenListe.kt`, `spiel/Buehne.kt`,
  `audio/VolleLautstaerke.kt`, `daten/Ablage.kt`.
- Nur Werkstatt (`werkstatt/src/main/kotlin/…/werkstatt/`): `Main.kt` (Server, alle `/api/`-
  Aufrufe), `Arbeit.kt` (Arbeitskopie, Sicherungen, Rückgängig, `Texte.teilen`), `StudioOne.kt`
  (Datei lesen, `Vorschlag`), `Geraet.kt` (adb, liest nur); Seite in
  `werkstatt/src/main/resources/web/` (`index.html`, `stil.css`, `app.js`).
- Tests: `app/src/test/…/LyricsTest.kt`, `EinmessungTest.kt` (auch Ablauf-Pflege und Abgleich),
  `LogikTest.kt`; `werkstatt/src/test/…/WerkstattTest.kt`.

**Als Nächstes (Reihenfolge und Details: `PLAN.md`, Arbeitsliste B am Ende):**
1. Jan fragen, wie der Gig lief, und ob er die Werkstatt schon ausprobiert hat.
2. B1 "Vom Mudita holen" mit angeschlossenem Gerät testen, B2 "Zum Mudita senden" bauen.
3. B5 Lesbarkeit: Probeseiten in drei Größen auf den Kompakt, Jan wählt.

## Gerät: Grundwissen

- Mudita Kompakt, E-Ink, 480×800, Dichte 213. USB-adb, Seriennummer `LD20241004967`. Wird es
  nicht erkannt: `adb kill-server`, `adb start-server`, kurz warten. Die Verbindung setzt manchmal
  kurz aus (`geraet.sh` wartet bis 10 s).
- Nie `am start -S` / force-stop auf WebView-Apps (schwarzer Bildschirm bis zum Neustart).
- In TrikTrak blättern die Lautstärketasten. Mudita bremst nachinstallierte Apps im Hintergrund;
  die Wiedergabe läuft als Vordergrund-Dienst trotzdem durch.
- Entwürfe: `sh entwuerfe/werkzeug/render.sh` rendert Vorschau-Bilder, `sh entwuerfe/werkzeug/bau.sh betrachter`
  baut die App "Entwürfe" (zeigt PNGs auf dem Kompakt, ohne TrikTrak anzufassen). Canvas:
  https://claude.ai/artifact/7BXLnocEYEibvLqdEqzeHo, Quelle in `entwuerfe/canvas/`.

## Bekannte Kleinigkeiten

Testsongs "Test Eins/Zwei/Drei" im Archiv, Platzhalter-Lyrics in "Test Eins" und "Country Roads",
"Frei spielen" aus der alten App fehlt, App-Symbol offen (inkOS zeigt nur Namen). Der Abschnitt
"Strophe 1" kann in einem Text zweimal vorkommen (Wiederholung); beim Umbenennen werden Strophen
neu durchgezählt.
