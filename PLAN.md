# Backing-Track-App für den Mudita Kompakt — Plan (Entwurf, 2026-11-09)

Stand: GrillMe Welle 1 mit Jan, noch kein Code.

## Entscheidungen von Jan

- Der Kompakt wird **alleiniger Zuspieler** auf der Bühne. Das alte Handy fällt weg.
- Ton: **erst Stereo wie bisher** (links Band fürs Publikum, rechts Click/Ansagen fürs Ohr),
  **USB-Multitrack zum CQ20B später** als zweiter Schritt.
- Mit einem Blick am Mikrofonständer lesbar: **jetziger + nächster Song, Restzeit, Lyrics**.
- Lyrics laufen **abschnittsweise** (ganze Strophe/Refrain als ruhige Seite, Umblättern beim
  Abschnittswechsel, Zeitpunkte aus der bisherigen Kalibrierung). Kein weiches Scrollen.
- Bedienung wie bisher, **Show-Automatik** trägt das Set. Das Verhalten der alten App ist gut
  und wird übernommen.
- **Loop fällt weg** (wird nie benutzt).
- Alles andere aus der alten App funktioniert laut Jan und wird übernommen: Archiv mit
  Ordner-Import, Song-Editor, Tempo-Filter, PDF-Lyrics, Gigs/Sets (Umschalten, Sortieren,
  gespielt, Restzeit), Ansagen, Pausen, Warnton, End-Aktionen, Mixer, Teleprompter-Kalibrierung.
- **Neu anfangen**: keine Datenübernahme vom alten Handy.
- **Vorbereitung komplett am Kompakt** (kein Laptop-Programm).
- **Schwarz auf Weiß**, wie Papier, Frontlicht für dunkle Bühnen.

## Gerätefakten (per adb gelesen)

- Android 12, arm64, 480×800 px bei 213 dpi (360×600 dp), 16 GB frei.
- Audio-Ausgänge laut System: Lautsprecher, Hörer, Bluetooth, USB-Audio (USB_DEVICE/USB_HEADSET).
  Ob eine Klinkenbuchse verbaut ist, lässt sich aus der Systemdatei nicht sicher ablesen →
  Ton-Test mit Jans echtem Kabel klärt das.
- Bekannte Kompakt-Fallen: Offline+-Schalter trennt die Mikrofone (Ansagen aufnehmen geht dann
  nicht), Mudita bremst nachinstallierte Apps im Hintergrund (RUN_ANY_IN_BACKGROUND,
  DuraSpeed), Lautstärketasten sind in vielen Apps zum Blättern belegt.

## Technik

- **Neue App mit eigenem Paketnamen in eigenem Repo** (`charmeundmelone-lab/Backing-Tracks-Kompakt`,
  privat, von Jan am 2026-11-09 so entschieden). Die alte App bleibt unverändert als Vorlage. Bewährter Kern wird übernommen
  und dabei durchgesehen ("sauber machen"): AudioEngine (ExoPlayer, Click rechts),
  Show-Automatik samt der Fixes vom 31.10., Datenmodell Songs/Gigs/Sets ohne Loop.
- **Oberfläche komplett neu für E-Ink**: keine Animationen, keine Wischgesten als einziger Weg,
  Seiten statt Scrollen, große Lato-Schrift, nur Schwarz/Weiß.
- Reihenfolge:
  1. Ton-Test am Kompakt: Stereo-Track über Jans Kabel, Click nur rechts. Scheitert das, ist
     alles andere egal.
  2. Entwürfe für den Bühnen-Bildschirm zeigen und mit Jan schärfen.
  3. Kern übernehmen und prüfen, dann Bildschirm für Bildschirm bauen, am Kompakt testen.

## Ton-Test am Kompakt (2026-11-09, erledigt)

Mini-App `show.jangregersen.tontest` (ohne Gradle gebaut, bleibt für den Kabeltest installiert):
Stereo-AudioTrack, links Akkord, rechts Click 120 BPM, Lautstärke 12 %.

- 48 kHz und 44,1 kHz laufen, Gerät arbeitet intern mit 48 kHz (256 Frames pro Puffer).
- 0 Aussetzer in 20 s, 15 s und 55 s. Wiedergabeuhr und echte Zeit laufen gleich
  (Abweichung unter 60 ms über 55 s).
- **Bildschirm 30 s aus: Ton läuft ohne Aussetzer weiter** (Vordergrund-Dienst, ohne eigene
  Ausnahmen für Muditas Hintergrund-Sperren).
- Latenz ca. 150–240 ms (großer Puffer, für Backing-Tracks ohne Belang).
- Ausgänge laut System: Hörer (mono), Lautsprecher (stereo), Telefon. USB-Audio meldet das
  System, ein USB-Gerät war nicht angesteckt (Port war mit dem Laptop belegt).
- **Nicht getestet:** der Weg über Jans echtes Kabel ins Pult. Das geht erst ohne
  Laptop-Kabel; Kommando dafür (Laptop per WLAN-adb oder Jan startet die App selbst):
  `adb shell am start -n show.jangregersen.tontest/.Start --ei sek 60 --ei rate 44100`.

## Design-Entscheidung (Claude, von Jan freigegeben: "volle Kontrolle")

Entwürfe: https://claude.ai/artifact/7BXLnocEYEibvLqdEqzeHo (sieben Bildschirme, 360×600 dp).
Auf dem Kompakt: App "Entwürfe" (`show.jangregersen.entwurf`), zeigt dieselben Bilder pixelgenau.

**Gewählt: Richtung B "Signal".** Jan hat B am 2026-11-09 am Kompakt geprüft: "sieht richtig gut aus".
Begründung aus dem Vergleich am echten Gerät:
- Schmale Schrift (Barlow Condensed / Semi Condensed) bringt bei gleicher Größe mehr
  Zeichen pro Zeile. Bei Richtung A (Serifen, Literata) brachen schon die Platzhalter-Zeilen
  und der Songtitel um, bei B nicht.
- Schwarzes Kopfband trennt "was läuft" sofort vom Rest, auch aus dem Augenwinkel.
- Nur Schwarz und Weiß, keine Grautöne (E-Ink zeigt Grau unruhig), keine Animationen.
- Lyrics 27 dp halbfett, Titel 38 dp, Restzeit 46 dp.

Festgelegte Bausteine:
- Kopfband: JETZT · Platz/Gesamt, Titel, Restzeit, Fortschritt als 20 Kästchen
  (gefüllt = gespielt). Ändert sich nur alle 5 %, also selten ein neues E-Ink-Bild.
- Lyrics abschnittsweise, Abschnittsname + Seitenpunkte. Schrift passt sich pro Abschnitt an
  (so groß wie möglich, nicht unter 24 dp). Zu lange Abschnitte werden an Leerzeilen geteilt,
  die Umblätter-Zeit wird anteilig nach Zeilen aus der Kalibrierung berechnet.
- Fußbereich: DANACH + nächster Song + Übergang (Ansage 0:20 / Pause 0:30 / nahtlos).
- Bedienleiste: Liste · Stopp · Pause/Play (schwarz) · Weiter, je 56 dp hoch.

## Bedienen ohne Scrollen

- **Bühne ↔ Setliste jederzeit, auch während der Song läuft** (Jans ausdrücklicher Wunsch):
  Knopf "Liste" in der Bedienleiste der Bühne und im Ansage-Bildschirm; in der Setliste ist
  das Kopfband der laufende Song mit Knopf "Bühne". Der Wechsel berührt die Wiedergabe nie.
  Tippen auf einen Song in der Setliste öffnet seine Karte (Ansage/Pause, als Nächstes),
  startet ihn aber nicht (Lehre aus der alten App: selectSong() würgte laufende Musik ab).
- **Listen sind Seiten**: feste Zeilenzahl pro Seite (Setliste 6 Songs à 64 dp),
  Blättern mit großen Knöpfen ‹ › unten, mit den Lautstärketasten (wie in Books) und per
  Wischen als dritter Weg. Seitenanzeige "Seite 1 von 2" mit Balken.
- **Springen statt Suchen** im Archiv: Tipp auf die Seitenanzeige öffnet ein A–Z-Raster
  (Buchstaben ohne Songs gestrichelt), darüber der Tempo-Filter.
- **Song verschieben = Aufnehmen und Ablegen**: "Verschieben" an, Song antippen → er ist
  "in der Hand" (gestrichelte Karte oben). Dann Zielsong antippen = "davor einfügen", oder
  "Ans Ende vom Set". Blättern nimmt den Song mit. Für kleine Schritte ▲ ▼ an der Karte.
  Kein Ziehen nötig.
- **Ansage/Pause** bekommt einen eigenen ruhigen Bildschirm: riesiger Countdown, nächster
  Song, Knöpfe Liste · Stopp · Starten.

## Bedienzonen (2026-11-09 abends, Claude, von Jan freigegeben: "voll auf deine Expertise")

Ziel: Profi auf der Bühne, kein Suchen in Menüs. Jede Live-Aktion höchstens zwei Tipps weit,
jede Bedienung hat auf jeder Seite denselben Platz.

1. **Oben, schwarzes Band = was läuft.** Immer der bereite oder laufende Song, Tipp = Bühne.
2. **Darunter die Seitenzeile:** links „‹“ zurück, Mitte Titel, rechts genau EIN Knopf für den
   wichtigsten Seitenwechsel (Setliste: „Sets“, Archiv: „Fertig“ bzw. „Songs holen“).
3. **Mitte = Inhalt als Seiten**, nie scrollen. Blättern auch mit den Lautstärketasten.
4. **Unten die feste Bedienleiste, daumennah:** außen immer die Blätterpfeile ‹ ›, dazwischen
   die Aktionen der Seite. Bühne: Liste · Stopp · Play · Weiter. Setliste: + Song · Umstellen.
5. **Hinweise erscheinen unten über der Leiste**, nie über Kopfband oder Knöpfen, und lassen
   Tipps durch. Fehler bleiben oben, bis man sie wegtippt.
6. Menüs („…“) nur noch für Seltenes, das live nie gebraucht wird (Set umbenennen, löschen).

Live-Wege:
- **Song dazuholen:** Setliste → „+ Song“ → Song antippen. Fertig. Oben steht ein Umschalter
  „Als Nächstes | Ans Ende“. Liegt ein Song aus dem Set bereit oder läuft, ist „Als Nächstes“
  vorgewählt; mehrere nacheinander behalten ihre Reihenfolge (2, 3, 4 …). Ein Haken-Tipp nimmt
  den Song wieder heraus, außer dem gerade bereiten.
- **Umstellen:** Setliste → „Umstellen“ → Song aufnehmen → vor Zielsong ablegen.
- **Song vorziehen:** Song antippen → „Als Nächstes“ (Karte), unterbricht nie.
- **Set wechseln:** Setliste → „Sets“.

## Schriftgröße (2026-11-09 abends)

Jan ist kurzsichtig und fand Schriften teils zu klein ("mach das Beste"). Entscheidung: Stufe 3
"Bühnenschrift" als Standard (`Schriftbild` in `ui/Bausteine.kt`): nichts unter 17 sp,
Etiketten 16 sp statt 11 sp, Text bis 30 sp um 16 % größer; Titel, Restzeit und Countdown
(ab 30 sp) unverändert. Damit alles passt, statt kleinerer Schrift weniger Text:
- Song-Karte: Beschriftungen von Pause und Songende links in der Zeile, unten eine Zeile
  Bearbeiten · Mixer · Entfernen, "Wieder offen" neben dem Hauptknopf.
- Hinweis "auch mit den Lautstärketasten" entfernt, Setliste oben nur "4/8 · noch 16 min",
  A–Z-Kästchen zeigen nur die Zahl, kurze Texte ("Keine Lyrics", "Tipp löschen").
Alle 16 Bildschirme in Stufe 3 am Kompakt fotografiert und geprüft, nichts abgeschnitten.
Zum Vergleichen bleibt `files/schrift.txt` (0–3) als Überschreibung.

## Weitere Bildschirme (2026-11-09, Richtung B)

Alle 14 Entwürfe liegen im Canvas und als Bilder in der App "Entwürfe" (Reihenfolge:
Bühne, Setliste, Ansage, Set wechseln, Song-Karte, Verschieben, Gigs, Archiv, A–Z, Editor,
Einmessen, Mixer, dann die verworfenen Richtungen A und C).

- **Kopfband-Regel:** Das schwarze Band oben zeigt immer den laufenden Song, sobald etwas
  läuft (Tipp darauf = zur Bühne). Läuft nichts, steht dort der Titel der Seite.
- **Gigs:** Karten mit Datumsblock, der nächste Gig hat einen schwarzen Datumsblock.
  Umschalter Gigs | Archiv oben. "Neuer Gig" unten.
- **Set wechseln:** ein Gig mit seinen Sets als große Karten (12 Fortschrittskästchen,
  laufendes Set schwarz). Schalter "Nach dem Set automatisch ins nächste", Sets sortieren,
  neues Set.
- **Song-Karte im Set** (Tipp auf einen Song, unterbricht nie): Als Nächstes spielen,
  Ansage anhören/neu aufnehmen, Pause danach (− keine +), Am Songende Weiter/Anhalten,
  Song bearbeiten, aus dem Set nehmen. Für gespielte Songs zusätzlich "Wieder auf
  nicht gespielt" (alter offener Wunsch aus der Backing-Tracks-App; im Entwurf nicht gezeichnet,
  weil dort ein offener Song gezeigt wird).
- **Archiv:** Seiten mit Anfangsbuchstabe groß oben links. Im Modus "Hinzufügen zu Set"
  hat jeder Song ein Plus, schon enthaltene einen schwarzen Haken. Mitte unten "A–Z · Seite"
  öffnet den Sprung.
- **Song-Editor:** drei Reiter (Daten, Lyrics, Ansage). Daten: Titel, Interpret, Tonart,
  Kapo (− +), Tempo (− + und Mittippen), unten die gefundenen Audiodateien mit Haken.
- **Lyrics einmessen:** Song läuft, Liste der Abschnitte, riesiger Knopf
  "JETZT BEGINNT Strophe 2". Letzten Tipp löschen, 10 s zurück.
- **Mixer:** pro Song, getrennt nach "Fürs Publikum · links" (Drums, Bass, Keys, Vocals) und
  "Nur im Ohr · rechts" (Click, Ansage). Stufen statt Schieberegler: − / 10 Balken / + / AUS.
- Arbeitstitel im Entwurf: "Bühnenblatt" (der App-Name ist noch nicht entschieden).

Werkzeug: `entwuerfe/werkzeug/render.sh` rendert alle Canvas-Dateien neu nach
`betrachter/assets`, danach `bau.sh betrachter` und per adb installieren.

## Offen (Welle 2)

- Kabeltest ins Pult: Jan verzichtet darauf, sein Kabel lief bisher immer. Ohne Kabel geprüft
  (2026-11-09): Kompakt meldet USB-Host-Betrieb (`android.hardware.usb.host`), USB-Audio-Treiber
  fest im Kernel (`CONFIG_SND_USB_AUDIO=y`), USB-Audio-HAL (`audio.usb.default.so`) und
  USB-Ausgang in der Audio-Policy. Der erste echte Gig-Probelauf ersetzt den Kabeltest.
- App-Name: erledigt, "TrikTrak" (Jan, 9.11.).
- Nächster Schritt: Kern aus der alten App übernehmen und dabei durchsehen, dann Bildschirm für
  Bildschirm bauen.

## Stand der App (2026-11-09 abends)

App-Code liegt in `app/` (Paket `show.jangregersen.buehne`, Name auf dem Gerät "TrikTrak"
(von Jan am 9.11. gewählt)). Gebaut mit Compose (nur foundation, kein Material),
eigene Ton-Engine (Mischpult, seit 9.11. abends statt Media3), Daten als JSON-Datei (`files/bestand.json`) statt Room.

Aufbau:
- `daten/`: Modell (Song, Gig, SetListe, Eintrag), Ablage (JSON, verzögertes Sichern),
  reine Änderungsfunktionen (verschieben, als Nächstes, nächster Song …).
- `audio/`: Engine (Schnittstelle für die Bühne), Mischpult (ein Faden, eine AudioTrack, alle Spuren
  sample-genau gemischt, Band fest links, Click/Cue/Ansage fest rechts, live regelbar), Quellen
  (WAV direkt, sonst MediaCodec), Spuren (Stereo-Datei oder Einzelspuren,
  Rolle aus dem Dateinamen), Import (Ordner-Regel der alten App, liest "KeyA K2" aus Namen),
  Aufnahme, Warnton, PDF-Lyrics.
- `spiel/Buehne.kt`: zentraler Steuerteil in der Application (überlebt Bildschirmwechsel),
  Show-Automatik (Ansage nach, Ansage vor, sonst stille Pause mit Warnton im letzten Drittel),
  Songende per STATE_ENDED, Vorladen des nächsten Songs, Set-Ende mit automatischem Wechsel.
  `WiedergabeDienst` hält den Prozess als Vordergrund-Dienst am Leben.
- `lyrics/`: Abschnitte (Labels wie [Chorus] werden deutsch), Einmess-Punkte, Seitenlauf.
- `ui/`: alle 14 Bildschirme aus den Entwürfen, Seitenlisten ohne Scrollen,
  Lautstärketasten blättern.

Am Kompakt getestet (9.11.): Import von 42 Stereo-Songs aus "Nothingphone" (in Sekunden),
Einzelspuren-Ordner, A–Z-Sprung, Gig anlegen mit Datum, Songs ins Set, Reihenfolge per
Aufnehmen/Ablegen über zwei Seiten, Song-Karte, Pause einstellen, Show-Automatik
(Song → 10 s Pause mit Countdown → nächster Song startet → "hält an" → nächster liegt bereit),
Lyrics abschnittsweise nach Einmess-Punkten und von Hand.
Noch nicht am Gerät getestet: Ansage aufnehmen (braucht Mikrofon-Erlaubnis von Jan),
Einmessen-Bildschirm, Mixer mit Einzelspuren, Probe im Editor, PDF-Import,
Wiedergabe mit ausgeschaltetem Bildschirm in der echten App, Kabel ins Pult.
Nicht übernommen: "Frei spielen" während einer Pause (selten gebraucht, kann nachkommen).
Testdaten auf dem Kompakt: Gig "Testgig", Ordner Music/Backing Tracks/Kurztest (3 Songs à 8 s).

Nachtrag 9.11. abends: Mixer, Editor-Probe, Einmessen, PDF-Import und Wiedergabe mit
ausgeschaltetem Bildschirm am Gerät geprüft. Ansage-Aufnahme und Ansage in der Automatik
(nur rechts) ebenfalls geprüft. Alle Gerätetests aus Welle 1 sind damit durch.

## Lyrics-Paket (Loop ab 12.11.)

Auftrag von Jan (12.11. abends, "vertraue dir voll und ganz", in einer Loop fertig machen, danach
die ganze App auf Herz und Nieren testen). Entwürfe: `entwuerfe/texte-holen/` (entwurf.html,
buehne.html, Bilder im Betrachter vorne). Quelle der Texte: LRCLIB (frei, ohne Konto,
User-Agent mit App-Namen Pflicht, Suche `/api/search?q=`, nacheinander mit Pause anfragen).
Test 12.11. mit Jans Titeln: 37/41 sofort, mit Suchwort 40/41.

Entscheidungen (Claude, Jan hat freie Hand gegeben):
- **Titel im Kopfband:** überall eine feste Größe statt Verkleinern. Langer Titel bricht in eine
  zweite Zeile um (gleiche Größe), die Kopfhöhe steht pro Song fest und ändert sich nie während
  des Songs. Restzeit wandert in die obere Zeile rechts.
- **Danach-Zeile:** immer sichtbar, aber kompakt in einer Zeile (kein Umbau gegen Songende, kein
  Neuaufbau der Seiten). Gewinn: mehr Platz für Lyrics.
- **Etiketten:** Pille oben links (Refrain schwarz, Strophe Rand, Bridge gestrichelt, Vorrefrain
  Rand dünn), rechts "dann <nächster Abschnitt>". Refrain-Text fett mit Strich links, Bridge
  gestrichelter Strich.
- **Orientierung (Ersatz fürs Mitscrollen der alten App, E-Ink-tauglich):** kein Laufen des
  Textes. Seite = Abschnitt, Blättern mit 1,5 s Vorlauf vor dem Einmess-Punkt. Innerhalb der
  Seite zeigt ein schwarzer Keil links die aktuelle Zeile und springt Zeile für Zeile im
  Songtempo (nur dieser kleine Bereich ändert sich). Zeilenzeiten: aus den Zeitmarken der
  Datenbank, auf Jans Einmess-Punkte gestreckt; ohne Zeitmarken nach Textlänge verteilt.
  Auf der letzten Zeile erscheint unten die erste Zeile des nächsten Abschnitts als Vorschau.
- **Automatisch eingemessen:** Passt die Länge des Originals bis auf ±3 s zur Länge des Tracks,
  setzt die App die Einmess-Punkte aus den Zeitmarken vor (als "vorläufig" gekennzeichnet,
  im Einmessen korrigierbar).
- **Abschnitte erkennen:** Wiederholter Block = Refrain, wiederholt direkt davor = Vorrefrain,
  einmaliger Block nach dem 2. Refrain = Bridge, aufeinanderfolgende Strophen-Stücke werden
  zusammengelegt. Danach immer die Seite "Abschnitte prüfen".
- Lyrics bleiben Text mit `[Etikett]`-Zeilen (von Hand änderbar). Zeitmarken liegen getrennt
  im Song (`zeilenZeiten`, `originalMs`) und gelten nur, solange die Zeilenzahl passt.

Arbeitsliste (abhaken mit [x], Datum dazu):
- [x] L1 Titel im Kopfband einheitlich (Bühne + Song-Karte), Danach kompakt (12.11., Gerätetest in L8)
- [ ] L2 Logik: LRC lesen, Abschnitte erkennen, Zeitplan (Zeile + Seite), Vorschlag Einmess-Punkte; Unit-Tests
- [ ] L3 Netz: LRCLIB-Suche (INTERNET-Recht, User-Agent, Pausen, Fehlertexte, Offline+-Hinweis)
- [ ] L4 Ablauf "Texte holen" (Archiv-Feld, Ist es dieser?, Anderer/Suchwort, Nicht gefunden, Fertig) + "Text suchen" im Song
- [ ] L5 Seite "Abschnitte prüfen" (Etikett ändern, mit vorigem zusammen)
- [ ] L6 Bühne: Pillen, Refrain-/Bridge-Strich, "dann …", Zeilen-Keil, Vorschau, Vorlauf
- [ ] L7 Einmessen: Etiketten, vorläufige Punkte, Zeilenzeiten neu strecken
- [ ] L8 Gerätetest aller neuen Abläufe am Kompakt (echte Songs)
- [ ] L9 Gesamttest der App auf Logikfehler (alle Seiten, Show-Automatik, Daten), Fehler beheben.
  Prüfmaßstab (Jan, 12.11.): testen, als wäre ich Jan beim Auftritt. Sänger mit umgehängter
  Gitarre, Kompakt am Mikroständer (ca. 60–80 cm Abstand), Hände meist an der Gitarre, nur
  kurze Blicke und einzelne Tipps zwischen zwei Akkorden. Alles Wichtige muss aus der Entfernung
  lesbar sein, kein Tipp darf etwas Unumkehrbares auslösen, nichts darf springen oder verwirren.
- [ ] L10 Übergabe in CLAUDE.md, Gedächtnis, Loop beenden
