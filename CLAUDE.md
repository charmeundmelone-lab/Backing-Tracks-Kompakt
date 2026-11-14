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

## Übergabe an die nächste Session (geschrieben 2026-11-13, ca. 15:15, ergänzt 19:30, vor Jans Gig)

**Neu 14.11. nachts (2):** B18–B22 gebaut und mit Jans Ja installiert + am Gerät geprüft (02:30, siehe PLAN.md "Gerätetest 14.11."; Zurück fester Weg, Auswahlseite "Jetzt spielen / Danach spielen", Beschriftungen, Kapo+Tonart groß, Werkstatt-Seite "Songdaten"). Werkstatt: `installDist` geht erst, wenn Jans laufende Werkstatt (seit 13.11. 16:12) zu ist. Details am Ende von PLAN.md.

**Neu 14.11. nachts:** B17 Ausgangspegel −2 dB gebaut und mit Jans Ja installiert (Commit da716e1, 01:51, Daten danach Byte für Byte gleich, Sicherung `.geraet/bestand-2026-11-14-vor-b17.json`; nicht per Ohr geprüft), offene Gig-Fragen am Ende von PLAN.md ("Gig 13.11. abends"). Der Punkt "Jan fragen, wie der Gig lief" ist damit zum Teil erledigt.

**Ganz kurz, wo es weitergeht (Stand 19:30):**
- B5 erledigt (Jan wählte Seite B). **B6 ist installiert, auf Jans Wunsch noch VOR dem Gig**
  (Commit `f66cadf`, 17:40): Bühne fest 34 sp in Barlow Condensed (Schmal), Zeilenabstand 1,12,
  Seiten werden voll gemacht (6 Bildschirmzeilen), lange Zeilen brechen eingerückt um, mitten im
  Teil blättert die Seite erst 0,3 s vor der Zeile. Weg dahin: 40 sp war Jan am Ständer "etwas zu
  groß" für schnelle Songs, 34 "erstmal gut, bis uns etwas Besseres einfällt", dann "da ist noch
  mehr Platz nach unten" → schmalere Schrift, volle Seiten. Foto: `.geraet/b6-112.png`.
  Rückweg zur Fassung von 15:10: `.geraet/vor-b6-2026-11-13.apk`, Daten `.geraet/bestand-2026-11-13-k.json`.
- B7 läuft: Jan hat die Werkstatt zum ersten Mal selbst benutzt. Hänger und Lösungen: (1) Er fand
  das Programm nicht (Desktop zugedeckt: Windows+D). (2) "Bed of Roses" aus dem Internet zerfiel in
  13 Teile mit falschen Namen → `Erkennung.kt` fasst jetzt zwei kurze Stücke (je ≤ 5 Zeilen) ohne
  Refrain dazwischen zusammen (nur in Liedern mit Refrain), abgewandelter Refrain (≥ 50 % gleiche
  Zeilen) bleibt Refrain, zweite Bridge heißt "Bridge 2". Ergebnis passt zu Jans Studio-One-Teilen.
  (3) "An vorigen hängen" stand nur im Vorrat → jede Karte hat jetzt "↑ Mit Teil darüber
  zusammenfügen" und "Umbenennen". Jan hat Bed of Roses neu geholt und selbst zum Mudita gesendet
  (kam an, 9 Teile). Noch nicht ausprobiert: Studio-One-Vorschlag (bei Bed of Roses ist die
  `.song` eine andere Fassung, 7:28 statt 6:50), Gig-Check, Sicherungen.
- Lehre: Eine eigene Test-Werkstatt belegt Port 47811; Jans Start weicht dann auf 47812 aus, sein
  alter Browser-Tab zeigt aber die Testinstanz. Testinstanzen nur starten, wenn Jans Werkstatt aus
  ist, und danach prüfen, dass der Prozess wirklich weg ist (`Get-NetTCPConnection` OHNE
  `-State Listen`, der Filter findet hier nichts).

**Stand:** `main`, alles committet und gepusht. App und Werkstatt bauen, alle Tests grün
(`:app:testDebugUnitTest` + `:werkstatt:test`). Am Kompakt läuft der Bau von `f66cadf` (B6 inklusive).

**13.11. nachmittags (diese Session):**
- B1 geprüft, B2 "Zum Mudita senden" gebaut und am Gerät hin und zurück getestet.
- Lücken gesucht und mit Jan entschieden: B13 Sicherheitsnetz (Sicherungen + Wiederherstellen),
  B14 Gig-Check, B15 Desktop-Symbol, B16 Doppelstart-Schutz. Akku-Warnung wollte Jan nicht.
- B3 Text aus dem Internet in der Werkstatt. B4 am Mudita: Text und Teile nur noch ansehen und
  einmessen (Jan: "Alles weg", also auch Texte holen, Text bearbeiten, Aus PDF). Installiert.
- B5 vorbereitet: Probeseiten A (30 sp, 6 Zeilen), B (40 sp, 3 Zeilen), C (große Zeile 52 sp +
  nächste Zeile 34 sp) liegen in der App "Entwürfe" ganz vorne. Jan wählte um 15:30 **B**.
  Quelle: `entwuerfe/lesbarkeit/probe.html` (Text erfunden).
- Jan hat die Werkstatt noch nie selbst bedient (B7). Seine echten Werkstatt-Daten sind geholt.

**Was am 13.11. vormittags passiert ist (kurz):**
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
- Start für Jan: Desktop-Symbol "TrikTrak Werkstatt" (oder `TrikTrak-Werkstatt.bat` im Repo). Es
  öffnet `http://127.0.0.1:47811` im Browser, das schwarze Fenster läuft klein unten mit.
- Bauen: `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew --no-daemon -q :werkstatt:test :werkstatt:installDist`.
  Nach jeder Änderung an der Werkstatt `installDist` laufen lassen, sonst startet die `.bat` den
  alten Stand.
- Arbeitsdaten AUSSERHALB des Repos: `C:\Users\HUAWEI\TrikTrak-Werkstatt\` (`bestand.json`,
  `zustand.json`, `sicherungen/`). Am 13.11. 15:12 vom Mudita geholt (46 Songs).
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
- Installiert: Bau von Commit `f66cadf` (13.11. 17:40, mit Jans Ja): B6-Bühne (siehe oben), dazu
  alles aus `25ef0f5` (versionCode 3: `eingang.json` Senden/Wiederherstellen, B4 Text/Teile nur
  ansehen). Daten nach jeder Installation Byte für Byte gleich. Vorsicht: Lautstärketaste auf der
  Setliste öffnete das Mudita-Schnellmenü (mit `key 4` geschlossen, nichts umgeschaltet).
  "Jetzt bereitlegen" auf der Song-Karte springt direkt auf die Bühne. App "Entwürfe" mit Probe A/B/C.
  Fassung von 15:10 (vor B6): `.geraet/vor-b6-2026-11-13.apk`.
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
1. Jan fragen, wie der Gig am 13.11. abends lief. Zum ersten Mal live: B2/B4, volle Lautstärke
   UND die neue Bühne (34 sp schmal, volle Seiten, Blättern mitten im Teil erst zur Zeile).
   Lesbar? Zu oft geblättert? Rahmen/Keil zu sehen? Probleme gehen vor allem anderen.
2. B7 weiter: Studio-One-Vorschlag an einem Song mit passender `.song` (nicht Bed of Roses),
   Gig-Check, Sicherungen. Zuschauen, wo Jan hängt.
3. Danach B8–B12 (siehe PLAN.md). B11 Gigs am Laptop ist der nächste große Brocken.

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
