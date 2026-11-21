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
- [x] L2 Logik: LRC lesen, Abschnitte erkennen, Zeitplan (Zeile + Seite), Vorschlag Einmess-Punkte; Unit-Tests
- [x] L3 Netz: LRCLIB-Suche (INTERNET-Recht, User-Agent, Pausen, Fehlertexte, Offline+-Hinweis)
- [x] L4 Ablauf "Texte holen" (Archiv-Feld, Ist es dieser?, Anderer/Suchwort, Nicht gefunden, Fertig) + "Text suchen" im Song
- [x] L5 Seite "Abschnitte prüfen" (Etikett ändern, mit vorigem zusammen)
- [x] L6 Bühne: Pillen, Refrain-/Bridge-Strich, "dann …", Zeilen-Keil, Vorschau, Vorlauf
- [x] L7 Einmessen: Etiketten, vorläufige Punkte, Zeilenzeiten neu strecken
- [ ] L8 Gerätetest aller neuen Abläufe am Kompakt (echte Songs). Pflicht: Bühne mit Offline+ an / ohne Netz prüfen (Jan 12.11.: App muss komplett offline gehen; Texte liegen nach "Ja" in bestand.json)
- [ ] L9 Gesamttest der App auf Logikfehler (alle Seiten, Show-Automatik, Daten), Fehler beheben.
  Prüfmaßstab (Jan, 12.11.): testen, als wäre ich Jan beim Auftritt. Sänger mit umgehängter
  Gitarre, Kompakt am Mikroständer (ca. 60–80 cm Abstand), Hände meist an der Gitarre, nur
  kurze Blicke und einzelne Tipps zwischen zwei Akkorden. Alles Wichtige muss aus der Entfernung
  lesbar sein, kein Tipp darf etwas Unumkehrbares auslösen, nichts darf springen oder verwirren.
  Dazu (Jan 12.11.): alles verständlich und intuitiv, jeder Knopf sagt in einfachen Worten, was
  passiert, jede Seite erklärt sich ohne Anleitung.
- [ ] L10 Übergabe in CLAUDE.md, Gedächtnis, Loop beenden

Stand 12.11. ca. 23:35 (Jan geht schlafen, Loop läuft weiter bis zum Wochenlimit):
- Am Gerät geprüft: Texte holen (einzeln, Two Princes), Abschnitte prüfen, Bühnentest mit
  mitlaufendem Keil + Blättern + geteilter Strophe, Einmessen mit Text, Weiter/Neu einmessen.
  Two Princes hat Text + Test-Einmesspunkte (aus dem Original umgerechnet, nicht von Jan).
- Zuletzt installiert: neue Danach-Zeile (Titel volle Breite, 2 Zeilen) – am Gerät NICHT mehr
  angesehen. Nach dem Neuinstallieren lag kein Song mehr bereit (Jan wählt das Set morgen neu).
- **GERÄTE-REGEL bis Jan es freigibt: nichts mehr am Kompakt installieren oder antippen.**
  Jan spielt am Fr 13.11. den Gig "Bar Eriksson" (8 Songs, keiner hat bisher Lyrics).
- Offen für L8/L9: Sammellauf "Texte holen" (mehrere Songs) am Gerät, Offline+-Test,
  Setliste: lange Titel werden neben den Kästchen (0:30, weiter) abgeschnitten → Kästchen in
  zweite Zeile; Seite "Keine Lyrics" auf der Bühne nutzt den Platz nicht.
  (Nachtrag 12.11. 23:50: Setliste zweizeilig und Sammellauf am Gerät geprüft, erledigt.)

### Umbau 13.11. nachts: eigener Ablauf + Zeilen-Tipps (Jans Wünsche, wörtlich sinngemäß)

1. "Es muss immer ersichtlich sein, welche Zeile ich gerade singen soll" – beim Einmessen UND
   auf der Bühne, mit Kontext oben und unten.
2. Jan singt oft NICHT in Originalstruktur: lässt Refrain/Strophe weg, bringt Teile an anderer
   Stelle wieder, lässt Teile ganz weg – individuell pro Song.
3. Einmessen ganz genau: "das Gesungene eintippen" – jede Zeile tippen, wenn sie gesungen wird.
4. Teile frei verschieben/einfügen (auch Instrumental ohne Text), vor UND nach dem Einmessen,
   "muss immer nachträglich möglich sein".

Umsetzung (Code fertig, baut, Unit-Tests grün, am Gerät NOCH NICHT getestet):
- Modell `lyrics/Zeitplan.kt`: Songtext = Vorrat an Abschnitten. **Ablauf** = Liste `Teil`
  (abschnitt-Index oder `Ablauf.INSTRUMENTAL`, `startMs` -1 = nicht eingemessen, `zeilenMs` =
  getippte Zeilenanfänge) in Spielreihenfolge, gespeichert im Song-Feld `marken`
  ("a:ms" oder "a:ms:z1|z2|…", kommagetrennt; leer = alle Abschnitte in Textreihenfolge).
  Alte Daten ("a:ms") werden weiter gelesen. `Zeitplan.zeilenStarts`: getippte Zeilen exakt,
  Rest im Abstand des Originals (LRCLIB-Zeitmarken), sonst nach Textlänge.
- Seite **Teile & Ablauf** (`ui/SeitenTexte.kt`, `TeileUndAblauf`, Seite.Pruefen; auch nach
  "Ja, übernehmen" in Texte holen): Teil antippen → ↑ ↓, Doppeln, Weg, Etikett (Palette, springt
  weiter, "Mit vorigem verbinden" = Text-Bruchstücke zusammenlegen), + Instrumental, + Teil
  (aus dem Vorrat). Änderung nach dem Einmessen → Zeiten ab dieser Stelle gelöscht, Hinweis
  "ab Teil N bitte weiter einmessen".
- **Einmessen** (`ui/SeiteEinmessen.kt`, ersetzt das alte in SeitenSong.kt): großer Knopf zeigt
  die nächste Zeile ("Tippen, wenn du singst") bzw. "<Teil> beginnt" + erste Zeile; Tipp =
  Zeilenanfang. Oben Kontext: Zeile davor, laufende (fett, voller Keil), nächste (hohler Keil),
  Anfang des nächsten Teils. Darunter: Anderer Teil (Zeit sofort gemerkt, dann wählen → wird
  eingefügt), Auslassen (nächsten Teil aus dem Ablauf nehmen), Tipp weg, −10 s.
  Nicht laufend: Ablauf-Liste mit Zeiten, Song starten / Bühne prüfen / Weiter / Neu einmessen.
- **Bühne** (`ui/SeiteBuehne.kt`): Seiten in Ablauf-Reihenfolge (Wiederholungen, Instrumental-
  Seite), Keil nach `zeilenStarts`, Blättern 1,5 s vorher.

Prüfung 13.11. vormittags (ohne Gerät; baut, 49 Unit-Tests grün, am Gerät NOCH NICHT getestet):
- Einmess-Regeln stehen jetzt in `lyrics/Einmessung.kt` (ohne Bildschirm, Tests in
  `EinmessungTest.kt`); `SeiteEinmessen.kt` ruft sie nur noch auf.
- Kein Tipp löscht mehr Arbeit ohne Rückfrage: "Neu einmessen" fragt erst ("Ja, von vorn"),
  und löscht nur, wenn der Song wirklich startet (vorher: Zeiten weg, auch wenn ein Set-Song
  lief und die Probe verweigert wurde). "Weiter einmessen" löscht nichts mehr, spielt 5 s vor
  dem letzten Tipp weiter. In Teile & Ablauf kommt eine Rückfrage, sobald eine Änderung
  eingemessene Zeiten kosten würde. "Weg" lässt die Zeiten der anderen Teile stehen (der Track
  ändert sich ja nicht).
- Tipp-Zeit genau (`Buehne.positionJetzt()`, vorher bis 200 ms zu früh). Doppeltipp unter
  300 ms zählt nicht.
- "Anderer Teil": Steht der gewählte Teil ohnehin als Nächstes im Ablauf, wird er genommen
  statt doppelt eingefügt (Fall: letzte Zeile nicht getippt, Refrain beginnt).
- Text von Hand ändern (Editor, PDF anhängen) zieht den Ablauf mit
  (`Ablauf.nachTextAenderung`), vorher zeigten die Teile danach auf falsche Abschnitte.
- Alte Daten (nur eingemessene Abschnitte gespeichert) bekommen beim Laden alle Abschnitte
  (`Ablauf.ausAltemStand`, Kennzeichen `"ablauf": 1` im Song).
- Teile & Ablauf: "Mit vorigem verbinden" nur, wenn der Teil darüber im Text direkt davor
  steht; Etikett-Modus überspringt Instrumental.
- Bühne: Auf einer Instrumental-Seite steht unten immer die erste Zeile des nächsten Teils.
- Einmessen: Die Laufzeit im Kopf zeichnet allein neu (Vorgriff auf A2, E-Ink-Ruhe).

Arbeitsliste ab hier (für die nächste Session):
- [x] A1 (13.11. früh am Gerät, Ergebnis unten) Neuen Bau am Kompakt testen (nur nach Jans OK; Gig "Bar Eriksson" am Fr 13.11.!):
  Two Princes: Teile & Ablauf (verschieben, doppeln, Instrumental, Etikett, verbinden),
  Einmessen Zeile für Zeile mit Anderer Teil/Auslassen/Tipp weg/−10 s, danach "Auf der Bühne
  prüfen": Keil exakt, Wiederholung und Instrumental-Seite richtig, "dann …" stimmt.
- [ ] A2 (Lesbarkeit erledigt, Ergebnis unten; OFFEN: E-Ink-Ruhe muss Jan mit eigenen Augen sehen) Darstellung am Gerät prüfen: Einmessen-Kontext lesbar (21 sp), nichts abgeschnitten,
  langer Teilname auf dem großen Knopf, E-Ink ruhig (Einmessen zeichnet alle 200 ms neu → falls
  Flackern: Position nur bei Zeilenwechsel neu zeichnen, wie auf der Bühne mit derivedStateOf).
- [ ] A3 (Code geprüft: Meldung "Kein Internet. Ist WLAN an und der Offline+-Schalter aus?" ist da, `netz/Lrclib.kt`; OFFEN: am Gerät mit Offline+) L8-Rest: Bühne mit Offline+ an (Jan schaltet; Schalter ist Hardware), Texte holen
  ohne Netz → verständliche Meldung.
- [ ] A4 (Technik-Durchlauf erledigt, Ergebnis unten; OFFEN: Jans Test mit Gitarre am Mikroständer) L9 Gesamttest aus Jans Sicht (Gitarre um, Kompakt am Mikroständer 60–80 cm, kurze
  Blicke, Tipps zwischen zwei Akkorden): Set mit Test-Gig "Testlauf" (liegt schon an: Set 1 mit
  Test Eins; Test Zwei hat Ansagen, Two Princes hat Text), Show-Automatik, Pause, Ansage,
  Bühne ↔ Setliste während der Wiedergabe. Danach Test-Gig mit Jans OK löschen.
- [x] A5 (13.11., vorgezogen, braucht kein Gerät; entfernt) Aufräumen: `Abschnitte.markenLesen/markenSchreiben/markenNachZusammenlegen` und
  `Seitenlauf` sind Altlast (nur noch LogikTest) – entfernen oder auf Ablauf umstellen.
- [x] A6 (Jan, 13.11.: kein Regler; beim Öffnen volle Medienlautstärke, bleibt so, solange die App offen ist. Umgesetzt in `audio/VolleLautstaerke.kt`, am Gerät geprüft) Offene Idee (nicht umgesetzt, Jan fragen): In TrikTrak blättern die Lautstärketasten,
  Jan kann dort die Medienlautstärke nicht ändern → evtl. Lautstärke-Anzeige/-Regler in der App.
- [x] A7 (13.11. mittags: CLAUDE.md neu geschrieben, Arbeitsliste B am Ende dieser Datei) Übergabe in CLAUDE.md + PLAN.md nachziehen.

Gerätetest 13.11. früh (Bau von ca. 07:00 installiert, Jan hatte freigegeben):
- A1 an Two Princes: Verschieben fragt vor Zeitverlust (Abbrechen und Ja geprüft), Doppeln,
  + Instrumental, Etikett (Instrumental gesperrt), Weg (fragt bei eingemessenem Teil).
  Einmessen: Zeilen tippen, Letzter Tipp weg, 10 s zurück, Anderer Teil (nimmt den nächsten
  Teil, ohne ihn zu doppeln), Teil auslassen, Neu einmessen fragt, Weiter einmessen löscht
  nichts. Bühne: Keil + Rahmen auf der getippten Zeile, Seite blättert nach dem eigenen Ablauf,
  "dann …" stimmt, Instrumental-Seite mit Vorschau. Nur per Unit-Test geprüft, nicht am Gerät:
  "Mit vorigem verbinden" (ändert den Text) und der Doppeltipp-Schutz (adb tippt zu langsam).
- A2, am Gerät gefunden und behoben: Liste in Teile & Ablauf sprang beim Antippen (gewählter
  Teil rutschte nach oben, dadurch falscher Teil getroffen) → `Zeige.nurWennVerdeckt`; jetzt
  5 Teile pro Seite. Einmessen: vier Knöpfe waren abgeschnitten → zwei Reihen ("Anderer Teil",
  "Teil auslassen", "Letzter Tipp weg", "10 s zurück"); Kontext zeigt nur ganze Zeilen.
  Bühne: Rahmen um die laufende Zeile (Keil allein war aus 60–80 cm zu klein).
- A4 Technik: Test-Set gespielt, Bühne ↔ Liste während der Wiedergabe, Set-Ende, Song mit
  Ansagen + "hält an" → nächster Song bereit. Pause-Ansicht heute nicht noch einmal gespielt;
  ihr Code (spiel/Buehne.kt, AnsageAnsicht) ist gegenüber dem Bau vom 12.11. unverändert.
- Stand der Testdaten: Two Princes hat jetzt 11 Teile (Teil 5 = Instrumental), 4 eingemessen
  (von Claude per adb getippt, also Zufallszeiten: Jan misst neu ein). Section 5 heißt jetzt
  "Strophe 3" (vorher zweites "Strophe 1", durch Umbenennen neu gezählt). Test-Gig "Testlauf":
  Set 1 enthält andere Testsongs als vorher, alles gespielt. Sicherungen in `.geraet/`:
  `bestand-2026-11-13.json` (vor allen Tests), `alt-2026-11-13.apk` (Bau vom 12.11.).

### Jans Rückmeldung 13.11. vormittags (nach eigenem Test) – NICHTS umsetzen, erst vorschlagen

Jan wörtlich sinngemäß: "Ich möchte nicht, dass du irgendetwas änderst. Schlag mir sinnvoll vor,
wie es weitergeht." Stand: nur Vorschläge, Entscheidung offen.
1. Einmessen selbst "hat gut geklappt", der Keil läuft am Text entlang.
2. Teile neu ordnen am Kompakt: Jan kommt durcheinander. Er sieht pro Teil nur den ersten
   Textschnipsel, nicht den ganzen Text. Muss ausgeschlossen sein, dass er irgendwo
   durcheinandergerät.
3. Jan ist kurzsichtig: Der Bühnentext ist für ihn schwer lesbar (trotz Stufe 24–31 sp).
4. Lautstärke: in der App immer volle Medienlautstärke, von vornherein (früher aus Versehen
   halbe Lautstärke gehabt). Achtung: Am Gerät standen am 13.11. 5/15, CLAUDE.md nannte das
   bisher "Jan nutzt 5/15". Mit Jan klären.
5. Jans Idee: den "Editor" (Text, Teile, Reihenfolge) auf den Laptop auslagern. Und: seine
   Songteil-Labels aus Studio One übernehmen.

Geprüft (nur gelesen, 13.11.): Studio-One-Songs liegen in `Documents/Studio One/Songs*/*/*.song`.
Eine `.song` ist ein ZIP; in `Song/song.xml` steht der Arranger-Track:
`<ArrangerEvent start="140" length="32" name="Vers" …/>` (Start und Länge in Viertelnoten),
dazu `TempoMapSegment tempo="0.526…"` (Sekunden pro Viertel) und der Marker-Track
(`<MarkerEvent name="Chorus" start="…"/>`). 108 von 145 Songs haben Arranger-Teile mit Namen
(Intro, Vers, Chorus, Bridge, Instr, Tag, Outro, END), 11 haben benannte Marker (z. B.
ENGISHMAN), keiner nutzt die Lyrics-Spur. Damit lassen sich Jans Teile samt Reihenfolge UND
Startzeit auf dem Track auslesen. Vorbehalte: Das Format ist nicht offiziell beschrieben (aus
Jans Dateien abgelesen, Studio One 7); der Versatz zwischen Song-Anfang und exportierter Datei
muss an einem echten Song geprüft werden; welche `.song` zu welchem Track gehört, muss Jan
einmal zuordnen (Ordner heißen oft nur nach Datum).

Volle Lautstärke (13.11., Jans ausdrücklicher Auftrag: "sobald ich die App öffne … und das soll so
bleiben, solange die App geöffnet ist"): `audio/VolleLautstaerke.kt`. Gilt, solange die App zu
sehen ist oder ein Song/eine Pause läuft (auch bei dunklem Bildschirm); wird sie von außen
verstellt, springt sie zurück auf voll. Beim Verlassen der App kommt die Lautstärke von vorher
zurück (Claudes Entscheidung, damit Kopfhörer in anderen Apps nicht plötzlich auf voll stehen;
Jan weiß es, kann geändert werden). Am Gerät geprüft: 5 → öffnen 15 → von außen 7 gesetzt → 15 →
Home 5 → öffnen 15. NICHT geprüft: mit Jans Kabel/Anlage (Android kann mit Kopfhörer-Ausgang
eine Schutzgrenze setzen; dann zeigt die App "Die Lautstärke lässt sich nicht auf voll stellen").
Tests am Gerät: vorher `run-as show.jangregersen.buehne touch files/test-leise` (dann fasst die
App die Lautstärke nicht an), danach die Datei wieder löschen.

Fertige Songs (Jan, 13.11.): In Studio One liegen auch Entwürfe. Maßgeblich sind die fertigen
Dateien in `C:\Users\HUAWEI\Music\Backing Tracks\Nothingphone` (42 WAV; Jan: Ordner darf in
"Mudita" umbenannt werden, noch NICHT umbenannt). Abgleich nach Namen (nur gelesen): 31 der 42
haben einen gleichnamigen Studio-One-Song, 27 davon mit Arranger-Teilen. Ohne automatischen
Treffer: Dont look back in anger, Ein Hoch auf uns, Englishman in New York (heißt in Studio One
"ENGISHMAN", hat Marker statt Arranger-Teile), Friday Love, Grenade, Put your records drums new,
Remember us (2×), Sunny, Talking Revolution, Thats alright Mama. Für die müsste Jan die
Studio-One-Datei einmal von Hand zuordnen oder die Teile ohne Studio One anlegen.

### Laptop-Programm "TrikTrak Werkstatt" (begonnen 13.11. vormittags, Jans Auftrag)

Entscheidungen von Jan (13.11.):
- "Alles Sinnvolle in ein Laptop-Programm, das wir später mit dem Mudita synchronisieren."
- Text und Teile: NUR am Laptop (am Mudita ansehen und einmessen, nicht mehr umbauen).
- Gigs, Setlisten, Songdaten, neue Songs, Ansagen: am Laptop UND weiter am Mudita.
- Maßgeblich sind die fertigen Songs in `Music\Backing Tracks\Nothingphone`, nicht die Entwürfe.
- Claudes Entscheidung (Jan fragte, was am sinnvollsten ist): Bau in drei Stufen.
  Stufe 1 Text & Teile + Studio One, Stufe 2 Gigs & Setlisten, Stufe 3 neue Songs, Songdaten,
  Ansagen.

Stand Stufe 1 (baut, 11 + 6 neue Tests grün, im Browser an einer Datenkopie geprüft):
- Modul `werkstatt/` (Kotlin/JVM, kleiner Server nur auf 127.0.0.1, Seite im Browser). Benutzt
  DENSELBEN Code wie die App (`lyrics/*`, `daten/Modell.kt`, `Aenderungen.kt`, neu
  `daten/BestandJson.kt`, `lyrics/Abgleich.kt`), nichts doppelt.
- Start: Doppelklick auf `TrikTrak-Werkstatt.bat` (Fenster offen lassen). Bauen:
  `./gradlew --no-daemon -q :werkstatt:installDist`, Tests `:werkstatt:test`. Anschluss 47811
  (8765 ist auf Jans Laptop belegt), weicht bei Bedarf aus.
- Arbeitsdaten AUSSERHALB des Repos: `C:\Users\HUAWEI\TrikTrak-Werkstatt\` (bestand.json,
  zustand.json, sicherungen/). Das Repo ist öffentlich: nie Songtexte einchecken.
- Kann: "Vom Mudita holen" (liest nur, per adb), Songliste mit Suche/Filter, pro Song Karten in
  Ablauf-Reihenfolge mit VOLLEM Text, Ziehen/▲▼/Doppeln/Weg, anderen Text auf einen Platz legen,
  Teil an einer Zeile teilen, an vorigen hängen, umbenennen, Text bearbeiten, Rückgängig (50
  Schritte), Rückfrage vor Zeitverlust. Studio One: Datei wird nach Namen gefunden oder von Hand
  gewählt, Teile mit Zeiten angezeigt, "Ablauf daraus vorschlagen", Text ein Fach weiter/zurück.
- Studio-One-Format (an Jans Dateien geprüft): Zeiten je Angabe in Vierteln (`timeFormat="2"`)
  ODER Sekunden (`timeFormat="0"`), beides kommt vor. ZIP mit Latin-1 öffnen (Umlaute in
  Dateinamen). Jan exportiert genau den Bereich seiner Arranger-Teile: Bei 15 von 21 Songs endet
  der letzte Teil auf 0,1 s genau am Ende der fertigen WAV. Weicht es über 3 s ab (z. B.
  Wonderwall, Jolene), warnt das Programm.
- Jans Labels sind lose ("Vers" steht bei ihm auch über Refrains). Deshalb legt der Vorschlag den
  Internet-Text der Reihe nach in seine Teile; Namen entscheiden nur, wo sicher kein Gesang ist.
  Probe: Bei "Two Princes" ergibt der Vorschlag exakt Jans selbst eingetippte Reihenfolge, Zeiten
  0–2 s neben seinen Tipps. "There she goes" stimmt ebenfalls.

OFFEN in Stufe 1:
- [ ] "Zum Mudita senden": App-Seite fehlt (App liest beim Start `files/eingang.json` und führt mit
  `Abgleich.textUndTeile` zusammen; Laptop schreibt die Datei per adb). Erst NACH dem Gig am
  13.11. installieren. Bis dahin bleibt alles nur auf dem Laptop.
- [ ] "Vom Mudita holen" mit angeschlossenem Gerät testen (am 13.11. war es gerade abgezogen;
  die Fehlermeldung "Kein Mudita gefunden" ist geprüft).
- [ ] Text aus dem Internet holen (LRCLIB) in der Werkstatt (Code `netz/Lrclib.kt` ist eingebunden,
  Seite fehlt). Bisher: Text einfügen von Hand.
- [ ] Jans eigene Labels für Teile ohne Gesang auf der Bühne zeigen (Intro, Solo, Outro statt
  "Instrumental").
- [ ] Am Mudita "Teile & Ablauf" auf Ansehen mit vollem Text zurückbauen (Jans Wunsch).
- [ ] Jan probiert die Werkstatt aus und sagt, wo er hängen bleibt.
- [ ] Lesbarkeit auf der Bühne (kurzsichtig): Probeseiten in drei Größen, noch nicht begonnen.

### Arbeitsliste B (ab 13.11. mittags, für die nächste Session) – HIER WEITERMACHEN

Reihenfolge ist Empfehlung. Was Jan zuerst will, geht vor. Reste der alten Liste A stehen unten.

Laptop-Programm, Stufe 1 fertig machen:
- [x] B1 (13.11. nachmittags: 46 Songs geholt, Sicherung beim zweiten Holen da, Laptop-Änderung übersteht Holen per Test `holenVorDerUebernahme`) "Vom Mudita holen" mit angeschlossenem, entsperrtem Kompakt testen (liest nur). Prüfen:
  Songs erscheinen, Sicherung liegt in `TrikTrak-Werkstatt\sicherungen\`, eine Laptop-Änderung an
  Text/Teilen übersteht ein zweites Holen (`Arbeit.vomGeraet` + `Abgleich.textUndTeile`).
- [x] B2 (13.11. nachmittags gebaut, App-Fassung 2 installiert, am Gerät mit "Test Eins" hin und zurück geprüft, Daten danach Byte für Byte wie vorher. App sieht alle 2 s nach, nur wenn sie zu sehen ist, nichts spielt und keine Text-/Einmess-Seite offen ist; Werkstatt wartet 10 s auf die Übernahme) "Zum Mudita senden". Entwurf: Laptop schreibt die geänderten Songs als
  `files/eingang.json` aufs Gerät (per adb: `push` nach `/data/local/tmp`, dann `run-as … cp`;
  `run-as` geht, weil Debug-Bau). Die App liest die Datei beim Start/Wiederkehren, führt pro Song
  mit `Abgleich.textUndTeile(geraet, laptop)` zusammen, löscht die Datei und zeigt einen Hinweis.
  Nicht `bestand.json` direkt überschreiben: Die App hält den Bestand im Speicher und schriebe
  ihn wieder drüber. Laptop sendet nur, wenn die installierte App das kann (`versionCode`
  hochzählen und per `dumpsys package` prüfen). Nach dem Senden `geaendert` leeren und frisch
  holen. Am Gerät erst installieren, wenn Jan es freigibt (vorher `bestand.json` sichern).
- [x] B3 (13.11.: Reiter "Text aus dem Internet", bis 8 Treffer, Rückfrage, wenn eingemessene Zeiten verloren gingen; mit der Testkopie geprüft) Text aus dem Internet in der Werkstatt holen (LRCLIB). `netz/Lrclib.kt`,
  `lyrics/Erkennung.kt` und `Uebernahme.kt` sind schon eingebunden; es fehlen der Aufruf in
  `Main.kt` und die Seite (Treffer zeigen, wählen, übernehmen). Bis dahin: Text von Hand einfügen.
- [x] B4 (13.11. ca. 15:10 mit Jans OK installiert, am Gerät mit Foto geprüft. Jan wählte "Alles weg": auch Texte holen, Text bearbeiten und "Aus PDF" am Mudita entfernt; Teile-Seite zeigt vollen Text, Lautstärketasten blättern. Seite TexteHolen und TeileUndAblauf sind jetzt unerreichbar, später löschen) Am Mudita "Teile & Ablauf" zurückbauen auf Ansehen mit vollem Text (Jans Wunsch: Text und
  Teile nur am Laptop). Erst wenn B2 läuft, sonst kann er nirgends mehr ordnen.

Lesbarkeit (Jan ist kurzsichtig, Bühnentext 24–31 sp ist ihm zu klein):
- [x] B5 (13.11. ca. 15:30: JAN WÄHLT B, also "halbe Seite" mit 40 sp und 3 Zeilen. Seiten A 30 sp/6 Zeilen, B 40 sp/3 Zeilen, C große Zeile 52 sp + nächste 34 sp stehen in der App "Entwürfe" ganz vorne, installiert. Fehlt nur: Jan schaut am Mikroständer) Drei Probeseiten in verschiedenen Schriftgrößen als Bilder in die App "Entwürfe" legen
  (`entwuerfe/werkzeug/render.sh`, `bau.sh betrachter`), Jan stellt den Kompakt an den
  Mikroständer und sagt, welche er lesen kann. TrikTrak dafür nicht anfassen.
- [ ] B6 (Jan hat am 13.11. Seite B gewählt = Claudes Favorit "halbe Seite", Vorlage `entwuerfe/lesbarkeit/probe.html#b`. 13.11. 15:29 GEBAUT, NICHT INSTALLIERT, Commit 8ab856d: fest 40 sp, Zeilenabstand 1,2, lange Zeilen brechen um, Abschnitte gleichmäßig auf Seiten verteilt (`seitenTeilen` in Abschnitte.kt), mitten im Teil blättert die Seite erst 0,3 s vor der Zeile statt 1,5 s, Keil und Vorschau größer. 13.11. 16:20 auf Jans Wunsch VOR dem Gig installiert; am Ständer war ihm 40 "etwas zu groß" für schnelle Songs, 17:26 mit 34 sp installiert ("erstmal gut, bis uns etwas Besseres einfällt"). Rückweg: `.geraet/vor-b6-2026-11-13.apk`. Jan dann: "da ist noch mehr Platz nach unten". 17:40 installiert: Lyrics in Barlow Condensed (Schmal) 34 sp, Zeilenabstand 1,12, schmalere Ränder, Seiten werden voll gemacht statt gleichmäßig verteilt (6 Bildschirmzeilen; Bed of Roses Strophe 1 auf 3 statt 7 Seiten, Foto `.geraet/b6-112.png`). Set danach wieder auf "There she goes" bereit. Fehlt: Jans Eindruck nach dem Gig) Danach die Bühne umbauen. Claudes Favorit "halbe Seite": deutlich größere Schrift, 4–5
  Zeilen pro Seite, Text steht ruhig, nur Rahmen/Keil wandern, es wird öfter geblättert.
  Alternative "große Zeile": laufende Zeile riesig, darunter die nächste (wechselt bei jeder
  Zeile). Jan hat noch nicht gewählt.

Werkstatt verbessern (nach Jans erstem Ausprobieren):
- [ ] B7 Jan probiert die Werkstatt aus (Doppelklick `TrikTrak-Werkstatt.bat`) und sagt, wo er
  hängen bleibt. Noch nicht mit ihm zusammen angesehen.
- [x] B8 (14.11.: Werkstatt-Karte ohne Gesang hat "Auf der Bühne: Instrumental | Intro | Solo | Zwischenspiel | Outro | Eigener Name"; `Abgleich.ohneGesangBenennen` legt einen leeren Abschnitt "[Name]" hinten an, Zeiten bleiben; mit Testkopie geprüft. Kompakt zeigt den Namen schon mit der installierten App, weil die Bühne `Ablauf.name` nutzt; am Gerät noch nicht gesehen. Grenze: "Text ab hier ↓" schiebt benannte Plätze wie Text mit, siehe B9) Jans eigene Labels für Teile ohne Gesang auf der Bühne zeigen (Intro, Solo, Outro statt
  "Instrumental"). Weg: leerer Textabschnitt mit diesem Namen (`[Intro]` ohne Zeilen), ein Teil
  ohne Zeilen gilt überall schon als Instrumental.
- [x] B9 (14.11., Jans Wahl: Karten ohne Gesang bleiben an ihrer Zeit, nur gesungener Text rückt; unbenannte Instrumental-Fächer hinter dem letzten Text nehmen Text auf; Knopf auf Karten ohne Gesang ausgeblendet; Test `soloBleibtStehenNurTextRueckt`) "Text ab hier ↓" schiebt bisher die ganze Folge samt leerer Fächer. Mit Jan klären, ob
  nur die Texte wandern sollen.
- [x] B10 (14.11. geprüft: schon im Code, `StudioOne.ausXml` nimmt benannte Marker, wenn kein Arranger-Track da ist, Hinweis "Teile aus den Markern gelesen"; Knopf "Andere Datei" wählt von Hand) Für Songs ohne gleichnamige Studio-One-Datei (11 von 42, Liste oben unter "Fertige
  Songs") die Datei von Hand wählen lassen; für Songs mit Markern statt Arranger-Teilen prüfen.

Stufen 2 und 3:
- [x] B11 (14.11. gebaut, NICHT am Gerät geprüft: Jans Regel "Mudita schützen" in `daten/GigAbgleich.kt` (Abdruck ohne gespielt, Laptop-Neues mit negativen Nummern, Mudita vergibt echte); App-Fassung 4 übernimmt `gigs` aus eingang.json; Werkstatt merkt Basis je Gig (`Arbeit.gigsGeaendert`), holt vor dem Senden, Konflikte → Mudita-Stand bleibt, Laptop-Fassung in `sicherungen/laptop-gig-*`; Seite "Gigs" mit Gigs, Sets, Songs, Ziehen, "danach weiter", Datum, Auto-Weiter. Tests: GigAbgleichTest + 6 Werkstatt-Tests; Seite mit Testkopie durchgeklickt. Senden von Gigs geht erst, wenn App-Fassung 4 auf dem Mudita ist) Stufe 2: Gigs und Setlisten am Laptop (bleiben auch am Mudita). Braucht eine Regel für
  beidseitige Änderungen (Vorschlag: pro Set gewinnt die neuere Änderung, Zeitstempel fehlen noch
  im Modell).
- [x] B12 (14.11.; Ansagen nimmt Jan nur am Mudita auf, kein Laptop-Teil nötig; Kopieren am Gerät geprüft: Werkstatt-Seite "Neue Songs" vergleicht den Laptop-Ordner mit dem Mudita-Ordner (Größe per stat), kopiert Angekreuztes per adb push (erst .kommt, dann umbenennen) und sendet neuSuchen; die App liest dann alle freigegebenen Ordner neu (Ablage.neuSuchen → Import.ordnerHolen, vorhandene Songs bekommen nur die neue Länge). Tonart/Kapo = B22. Titel und BPM jetzt auch in der Tabelle Songdaten. Offen: Ansagen am Laptop) Stufe 3: neue Songs aus `Music\Backing Tracks\Nothingphone` aufs Gerät, Songdaten
  (Titel, Tonart, Kapo, BPM), Ansagen.

Lücken, die Claude am 13.11. gefunden hat (Jan hat B13–B15 gewählt, Akku-Warnung abgelehnt):
- [x] B13 (13.11.) Sicherheitsnetz: Werkstatt holt beim Start, wenn der Mudita dranhängt; jede
  Änderung auf dem Mudita landet als `sicherungen/mudita-*.json` (60 bleiben); Knopf
  "Sicherungen" spielt einen Stand ganz zurück (App hebt den Stand davor als
  `bestand-vor-wiederherstellung-*.json` auf). Am Gerät mit gleichem Stand geprüft.
  Grenze: Musikdateien selbst sichert das nicht (die liegen auf dem Mudita, Originale in
  `Music\Backing Tracks\Nothingphone`). Nach Neuinstallation mit Datenverlust müssten die Dateien
  neu verknüpft werden.
- [x] B14 (13.11.: Knopf "Gig-Check", mit ungefährer Set-Länge inkl. Ansagen/Pausen; Musikdatei prüft nur der Mudita) Gig-Check am Laptop: pro Gig und Set eine Liste, welche Songs noch keinen Text haben,
  nicht eingemessen sind oder deren Musikdatei fehlt.
- [x] B15 (13.11.) Desktop-Symbol "TrikTrak Werkstatt" (startet die `.bat` mit kleinem Fenster).
- [x] B16 (13.11.) Doppelstart der Werkstatt: Der zweite Start öffnet nur die laufende Seite,
  statt eine zweite Werkstatt auf dieselben Dateien loszulassen.

Reste der Liste A und Kleines:
- [ ] A2-Rest: Jan schaut, ob das E-Ink beim Einmessen ruhig bleibt (kein Flackern).
- [ ] A3-Rest: Bühne mit Offline+ an (Hardware-Schalter, Jan schaltet), "Texte holen" ohne Netz.
- [ ] A4-Rest: Jans Gesamttest mit Gitarre am Mikroständer.
- [ ] Volle Lautstärke mit Jans Kabel an der Anlage prüfen (Schutzgrenze bei Kopfhörer-Ausgang?).
  Jan fragen, ob "beim Verlassen der App zurück auf die alte Lautstärke" für ihn passt.
- [ ] Test-Gig "Testlauf" und Testsongs löschen, nur mit Jans OK.
- [ ] Ordner `Nothingphone` in "Mudita" umbenennen (Jan erlaubt es). Vorher prüfen, was den Pfad
  benutzt (Werkstatt noch nicht, alte Skripte im Ordner, Jans Gewohnheit).
- [ ] Nicht am Gerät geprüft, nur per Unit-Test: "Mit vorigem verbinden", Doppeltipp-Schutz.

### Gig 13.11. abends (Bar Eriksson) – Rückmeldung 14.11.
- [x] B17 (14.11.) Ausgangspegel: Jan fand den Kompakt lauter als das Nothing Phone (gleicher
  USB-Adapter "USB PnP Audio Device", beide auf voll). Ausgelesen per `dumpsys audio` /
  `media.audio_policy`: Kompakt 15/15 = 0 dB, Nothing 3a 16/16 = −2 dB (seine Kurve endet dort),
  keine Schutzsperre an beiden, alte App schwächt nichts ab. TrikTrak gibt jetzt 2 dB weniger aus
  (`Mischpult.AUSGANG_DB`, gilt auch für Ansagen), Medienlautstärke bleibt voll. Gebaut, Tests
  grün, am 14.11. 01:51 installiert (Daten gleich).
- [ ] Laut Protokoll steckte der Adapter am Gig nur 20:04–20:22 am Kompakt, ab 20:23 am Nothing
  Phone. Jan fragen, warum er gewechselt hat (er nannte "Bedienung zwischen den Songs").
- [ ] "Perfect K1.wav": Band-Kanal (links) ist stumm (−108 dB), nur Click drauf. Auf Laptop und
  Kompakt dieselbe Datei. Jan muss neu exportieren. War nicht im Gig-Set.
- Jans Rückmeldung zur Bedienung (14.11., gesammelt, NOCH NICHTS UMBAUEN, erst Vorschläge mit Favorit):
  - Zurück-Taste landet falsch, alle drei Fälle erlebt: (a) Setliste → Song-Karte → "Jetzt
    bereitlegen" → Bühne, Zurück führt zur Song-Karte statt zur Setliste (`zurBuehne` legt die Bühne
    auf die Karte); (b) Setliste ↔ "Sets" im Kreis, der Stapel wächst (`navi.zu` statt zurück);
    (c) einmal zu oft Zurück und TrikTrak ging zu (`BackHandler` → `beenden()` auf Gigs).
  - Anderen Song wählen, alle vier Lagen erlebt: Song im Set vorziehen, Song nicht im Set (Archiv),
    während Pause/Ansage umentscheiden ("Erst den laufenden Song stoppen"), zu viele Schritte
    (Setliste → Karte → Bereitlegen → Bühne → Play).
  - Unklare Beschriftung auf der Song-Karte: "Bereitlegen" / "Als Nächstes" (spielt es sofort?
    rückt es vor?) und "Wieder offen".
  - Grund für den Wechsel aufs Nothing Phone am Gig um 20:23: Bedienung zu umständlich.
  - Bühnentext live: "Gut lesbar, passt" (34 sp schmal, volle Seiten).
  - Songwechsel lief grundsätzlich wie gewünscht. Aber in der Pause will Jan manchmal den Timer
    anhalten (noch etwas erzählen) oder mit einem anderen Song weitermachen. Befund: "Stopp" in der
    Pause (`Buehne.stopp`) hält die Automatik schon an und legt den nächsten Song bereit, die
    Beschriftung sagt das nicht. Anderer Song aus der Pause heraus: Liste → Karte bietet nur
    "Als Nächstes".
  - Wunsch: In der Pause groß sehen, welcher Kapo und welche Tonart der nächste Song hat, zum
    Vorbereiten. Heute nur klein in der Infozeile (`songInfo`, 16 sp). Daten: nur 10 von 46 Songs
    haben einen Kapo, 8 eine Tonart, der Rest müsste eingetragen werden (Werkstatt Stufe 3 oder Kompakt).
  - Jans Wahl: Kapo und Tonart für alle Songs am Laptop in der Werkstatt eintragen (Tabelle mit
    allen Songs, dann "Zum Mudita senden").

Jans Entscheidungen 14.11. nachts (Vorschläge mit Favorit, jeweils "so bauen"):
- [x] B18 (gebaut 14.11., nicht am Gerät geprüft) Zurück-Taste fester Weg: Bühne → Setliste → Sets des Gigs → Gigs, egal wie man kam.
  Song-Karte, Mixer, Archiv → Seite, von der sie geöffnet wurden. Auf der Gig-Übersicht schließt
  Zurück die App NICHT (passiert nichts). Setliste ↔ Sets darf den Stapel nicht wachsen lassen.
- [x] B19 (gebaut 14.11., `SeiteSongWahl.kt`; Archiv-Tipp schaltet nicht mehr aus, öffnet die Auswahl; "Jetzt spielen" rückt den Song vor den bisher nächsten; nicht am Gerät geprüft) Anderen Song wählen: Tipp auf Song in der Setliste zeigt zuerst zwei große Knöpfe
  "Jetzt spielen" (legt auf der Bühne bereit, Play drückt Jan selbst; geht auch in der Pause, die
  Pause endet) und "Danach spielen" (hinter den laufenden). Einstellungen dahinter unter kleinem
  Knopf "Einstellungen". Setliste bekommt "Aus dem Archiv" (A–Z) mit denselben zwei Knöpfen, Song
  wird ins Set eingefügt. Solange ein Song läuft: nur "Danach spielen".
- [x] B20 (gebaut 14.11.) Beschriftungen: "Jetzt spielen", "Danach spielen", "Wieder offen" → "Nochmal einplanen";
  Pause: "Stopp" → "Timer anhalten", "Liste" → "Anderer Song", "Starten" → "Jetzt starten".
- [x] B21 (gebaut 14.11., `KapoTonart` in `SeiteSongWahl.kt`, auch auf der Auswahlseite) Kapo/Tonart groß: Pausen-Bildschirm mit kleinerer Restzeit und zwei großen Kästen
  (Kapo, Tonart; Kapo 0 = "ohne", Tonart leer = "–"), dieselben Kästen auf der Bühne, solange ein
  Song bereitliegt und nicht läuft.
- [x] B22 (gebaut 14.11., in Test-Werkstatt geprüft; `Abgleich.textUndTeile` übernimmt jetzt Tonart+Kapo vom Laptop, braucht die neue App) Werkstatt-Seite "Songdaten": Tabelle aller Songs (Titel, Tonart, Kapo), durchtippen,
  dann "Zum Mudita senden". OHNE Vorschläge aus Dateinamen (Jans Wahl).
- Gerätetest 14.11. 02:25–02:35 (installiert 02:30, Daten vor/nach gleich): Setliste ↔ Sets ohne
  Kreis, Zurück Bühne → Setliste → Sets → Gigs, auf Gigs bleibt die App offen; Auswahlseite (im Set,
  aus dem Archiv), "Jetzt spielen", "Danach spielen" fügt hinter dem bereiten Song ein; Pause mit
  Kapo/Tonart-Kästen und neuen Knöpfen (Foto `.geraet/b21-pause.png`); Kästen auf der Bühne vor dem
  Start. Seite war mit Jetzt-Band zu lang → kompakter gemacht. NICHT geprüft: "Timer anhalten"
  (kam zu spät), Werkstatt-Neufassung (`installDist` erst, wenn Jans Werkstatt zu ist).
  Rest: "Test Eins" steht jetzt im Test-Gig "Testlauf". Kleinigkeit offen: Auswahlseite sagt bei
  bereitem Song "Zeigt den Song mit Text", auch bei Songs ohne Text.

Jans Meldung 14.11. morgens (vom Gig) – selbstständig behoben:
- [x] B23 Mail-Ton kam über die Anlage → `audio/ShowRuhe.kt`: "Bitte nicht stören" (nur Medien
  dürfen durch, keine Mail/SMS/Anrufe/Wecker), solange TrikTrak die Lautstärke hält; danach genau
  die alte Einstellung zurück (Merker `files/ruhe-vorher.txt`, auch nach Absturz). Braucht einmal
  Jans Erlaubnis (Einstellungen → Zugriff während „Bitte nicht stören“ → TrikTrak); bis dahin
  Karte "Show-Ruhe erlauben" auf der Gigs-Seite. Manifest: ACCESS_NOTIFICATION_POLICY.
- [x] B24 Kein Ton nach Android-Frage "über den Schwellenwert?" → Ursache: Gehörschutz-Sperre für
  Kopfhörer/USB (Kompakt: `mSafeUsbMediaVolumeIndex=20` = Stufe 2 von 15, praktisch stumm).
  Android blockiert das Hochstellen durch die App dann ohne Meldung und schaltet die Sperre nach
  einer Spielzeit wieder ein (`mMusicActiveMs` zählt). Jetzt: `VolleLautstaerke.gedrosselt` →
  großer Hinweis über allen Seiten mit Knopf "Ton freigeben" (stellt mit FLAG_SHOW_UI auf voll,
  Android zeigt dann sein Fenster mit OK). Am Gerät nur simuliert (`files/test-gedrosselt`
  zusammen mit `files/test-leise`); ob Androids Fenster beim Knopf wirklich kommt, zeigt sich erst,
  wenn die Sperre wieder aktiv ist.
- B23 am Gerät geprüft (14.11. 09:05): Jan hat den Zugriff erlaubt. TrikTrak offen → ZEN_MODE_IMPORTANT_INTERRUPTIONS,
  nur PRIORITY_CATEGORY_MEDIA; Home → ZEN_MODE_OFF, alte Regel (Wecker+Medien, Unterhaltungen "none") genau zurück,
  Merker weg, Lautstärke wieder 12. Nicht geprüft: ein echter Mail-Ton (Shell-Test hätte eine Benachrichtigung
  in Jans Liste hinterlassen).
- Gerätetest 14.11. 14:14–14:20 (App-Fassung 4 installiert, Daten vorher/nachher Byte für Byte gleich):
  B11 Gig senden: "Testlauf" am Laptop umbenannt + Song umgestellt → kam an, gespielt-Haken blieben.
  Konfliktfall: Laptop benennt um, am Mudita "automatisch ins nächste" umgeschaltet → Mudita-Stand
  blieb, Meldung kam, Laptop-Fassung in sicherungen/laptop-gig-*. Danach über den Laptop exakt
  zurückgestellt. B12: Vergleich fand zuerst fälschlich alles "neu" – Ursache: doppelte
  Anführungszeichen kommen unter Windows nicht heil bei adb an → `Geraet.shText` mit einfachen
  Anführungszeichen. Danach 42/42 "gleich"; "Can't judge a book K0.wav" (Apostroph!) testweise neu
  kopiert (kein .kommt-Rest, Datei neu geschrieben), TrikTrak hat neu eingelesen (Ordner-Freigaben
  14:18:33 erneuert), keine Datenänderung. Noch nicht geprüft: ein wirklich neuer Song (nach Jans
  Neu-Export von "Perfect").

Jans Wünsche 14.11. nachmittags (Arbeitsliste C):
- [x] C1 Pfeile "‹ Voriger" / "Nächster ›" auf den Einstellungsseiten (`SongPfeile` in
  `ui/SeitenSong.kt`): Einstellungen im Set blättern in der Setliste, Bearbeiten aus dem Archiv
  von A bis Z (wie die Archivliste), aus dem Set geöffnet in der Setliste (`Seite.Editor.setId`).
  `Navi.songWechseln` stellt alle Song-Seiten im Stapel um, Zurück führt so zum gezeigten Song.
  Reiter bleibt beim Blättern; Mittippen/Probe/Aufnahme gehören zum alten Song (`key(songId)`).
- [x] C2 "Einstellungen sofort speichern": war schon so (Ablage schreibt 0,3 s nach jeder Änderung,
  dazu beim Verlassen). Jetzt sichtbar: "gespeichert" unter "3 von 14", beim Pfeil-Wechsel wird
  sofort geschrieben, Knopf "Fertig" auf der Bearbeiten-Seite entfernt (sah nach Speichern aus).
  Daten-Reiter dafür enger (Felder 44 dp, ohne Überschrift "Tempo"), Probe/Mixer/Löschen sichtbar.
- [x] C3 Tempo-Filter: Jan wollte erst Kapo, korrigierte sich auf Tempo. Filter gab es schon, kein
  Song hatte einen Stempel. BPM aus dem Click gemessen (`werkzeug/ClickTempo.java`, rechter Kanal,
  häufigster Abstand), Grenzen < 90 Langsam, 90–119 Mittel, ab 120 Schnell; gefühltes Tempo bei
  Achtel-Click/6/8 (Perfect 180, Tennessee Whiskey 150, Bed of Roses 160); Mrs. Robinson und
  Can't judge a book (Click 200) → Schnell. Jans eigener Stempel bleibt ("All night Long" Schnell).
  Ergebnis: 14 Langsam, 18 Mittel, 11 Schnell, Testsongs ohne. BPM-Feld = Click-Tempo im Ohr.
  Werkstatt "Songdaten" hat Spalte "Tempo", `Abgleich.textUndTeile` übernimmt den Stempel.
- Gerätetest 14.11. 17:05–17:12: App-Fassung 5 installiert (Daten vorher/nachher Byte für Byte
  gleich), Pfeile auf der Bearbeiten-Seite (Fotos `.geraet/editor5b.png`), Stempel per
  Test-Werkstatt gesendet: auf dem Mudita nur BPM/Tempo geändert, Gigs gleich, Filter "Schnell"
  zeigt 11 (`.geraet/archiv-schnell.png`). Jans Werkstatt: Songs 3 und 14 sind dort seit 13.11.
  "geändert, nicht gesendet" → BPM/Stempel dort nachgetragen, sonst hätte das nächste Senden sie
  geleert (Grundproblem: bei offenen Songs gewinnen die Laptop-Songdaten). NICHT am Gerät
  geprüft: Pfeile auf der Einstellungen-Seite im Set (Jan hat nur "El Circo" mit leerem Set).

Gig "El Circo" Sa 14.11. abends (C4–C6):
- [x] C4 Setliste: 3 Sets à ~40 min, 30 s Pause zwischen Songs, 60 s vor Kapo-Wechsel, Show-Automatik
  ("weiter" bei jedem Eintrag, Gig "automatisch ins nächste"). Spannungskurve: Set 1 ruhig
  anfangen (K3-Block Landslide/Crazy/Valerie/That's alright Mama, dann ohne Kapo), Set 2 stark
  (Jolene, dann K2-Block bis Take on Me), Set 3 ruhiger (K2-Balladen, dann ohne Kapo bis
  Sweet Caroline). Je Set ein Kapo-Wechsel; Set 2 endet und Set 3 beginnt mit Kapo 2.
  Wichtig: Jan korrigierte während der Planung Kapo/Tempo vieler Songs am Mudita (neue Pfeile) und
  löschte Ein Hoch auf uns, Sunny, Remember us (ohne Kapo), Country Roads (Einzelspuren) → Plan
  neu gerechnet. Danach nahm er "Sitting Waiting Wishing" aus Set 1 (Set 1 jetzt 35:45).
  Weg: Sets/Songs/"weiter" per Test-Werkstatt (`gig`-API), Pausen per Tippen am Mudita
  (Pause gehört zum Song, nicht zum Eintrag; Werkstatt kann sie nicht). Achtung beim Tippen:
  Antippen eines Sets in der Set-Übersicht macht es AKTIV und legt seinen ersten Song bereit →
  am Ende Set 1 wieder antippen. Lange Titel brechen um und schieben die Pause-Knöpfe nach unten.
- [x] C5 Jolene.wav (818 s, ab 190 s nur Rauschen −63 dB, Loop in Studio One) auf 191 s gekürzt
  (`werkzeug/WavEnde.java`, Kopf unverändert, Anfang Byte für Byte gleich). Original:
  `Music\Backing Tracks\Sicherung-Originale\Jolene-vor-Kuerzung-2026-11-14.wav`.
- [x] C6 Erster echter Lauf "Neue Songs" (geänderte Datei): klappt, ABER "neu einlesen" holt
  gelöschte Songs zurück, solange ihre Dateien im Musikordner liegen. Umgangen durch kurzes
  Parken der 4 Dateien in `/sdcard/Download/TrikTrak-geparkt` (danach zurück, Ordner weg).
  Offen (Verbesserung): App sollte beim Neu-Einlesen bekannte, gelöschte Dateien nicht wieder
  anlegen (z. B. Liste "gelöscht" im Bestand) – mit Jan klären.

15.11. nachts:
- [x] C7 Werkstatt überschreibt Songdaten vom Mudita nicht mehr (Jan: "Was ich am Mudita einstelle,
  bleibt"; Anlass: Bed of Roses Kapo 2 am Mudita, Laptop-Fassung Kapo 0, Song am Laptop offen).
  `Arbeit.songBasis` + `songdatenMischen`: pro Feld Laptop nur, wenn dort geändert. Alte offene
  Songs ohne Basis nehmen die Mudita-Werte. 3 neue Tests. Jans Werkstatt einmal geholt: Songs 3/14
  waren nur wegen Kapo "offen" (Text längst angekommen), jetzt nichts offen.
- [x] Automatische Mudita-Sicherung (Windows-Aufgabe "TrikTrak-Sicherung", alle 5 Min., siehe CLAUDE.md).

## Arbeitsliste D: "TrikTrak einfach" (15.11., nach El Circo)

Jans Rückmeldung zu El Circo: TrikTrak lief den ganzen Abend, aber beim Spielen unübersichtlich,
zu viele Tipps (Liste → Song → Menü). Auftrag: Weltklasse-Bedienung, einfach, wenig Tipps,
schlank, Lesbarkeit mindestens wie bisher (Jan ist kurzsichtig, Lesbarkeit war "sehr gut").
Entwurf: `entwuerfe/einfach/` (8 Seiten, Bilder in `bilder/`), von Jan angenommen.

- [x] D1 Fehler: Set antippen, während ein Song lief, machte das andere Set sofort aktiv (Song ohne
  Haken, Automatik stand, nächster Song aus dem falschen Set; erklärt vermutlich die fehlenden
  Haken in Set 1/2 nach El Circo). Jetzt: Set beginnt nach dem laufenden Song (`Zustand.setDanach`,
  `setDanachBeginnen`), in der Pause zwischen Songs sofort. Set-Karte zeigt "kommt danach".
- [x] D2 Eine Regel "Antippen = kommt als Nächstes" (`Bestand.vorziehen`, `Buehne.vorziehen`):
  läuft ein Song → danach; Pause → ersetzt den wartenden Song, Countdown läuft weiter; nichts
  läuft → sofort bereit. Danach automatisch zurück zur Bühne, 8 s "Rückgängig". Auswahlseite
  (SeiteSongWahl) entfernt. Gilt in Setliste, Bühne ("Dann"-Liste) und Archiv ("Wunschsong").
- [x] D3 Bühne: drei Knöpfe (Liste · Play/Pause · Beenden bzw. Weiter). Beenden braucht zweiten
  Tipp und macht weiter wie am Songende (`Buehne.beenden`). Ohne Text: Kapo/Tonart 44 sp wie
  bisher + "Dann"-Liste der folgenden Songs (antippbar) statt leerer Fläche; BPM-Kasten entfällt.
  Lautstärketasten auf Bühne ohne Text tun nichts mehr (vorher Android-Lautstärkebalken).
- [x] D4 Pause-Seite: "Jetzt starten" breit, darunter "Anderer Song" | "Anhalten" (vorher drei
  abgeschnittene Knöpfe).
- [x] D5 Setliste live: ein Kopf (Gig · gespielt · Restzeit, Set-Name ▾ = Sets, "Bühne"), oben was
  kommt (`SetListe.wasKommt`), gespielte in einer Zeile unten (antippbar), immer ab Seite 1,
  zweite Zeile nur bei Abweichung ("hält an", Ansage), unten nur "Wunschsong".
- [x] D6 "Set bearbeiten" (neue Seite, über ⋯ am Set): Songs dazu (Archiv: Tipp = ans Ende),
  Umstellen, Tipp = Einstellungen des Songs; Pause/Ende je Zeile sichtbar.
- [x] D7 Am Gig-Tag (bis 6 Uhr zählt der Vortag) öffnet TrikTrak gleich auf der Bühne, erstes
  offenes Set bereit (`Buehne.gigHeute`).
- [x] D8 Abend-Protokoll `files/protokoll.txt` (Start, zu Ende, beendet nach m:ss, übersprungen).
- [x] D9 Testlauf-Skript `werkzeug/testlauf.sh` + Testmodus (`files/testmodus` → test-bestand.json,
  Spielwiese `Bestand.testlauf`, Jans Daten unberührt, vorher/nachher-Vergleich). Symbol-Knöpfe
  haben jetzt Namen (contentDescription), damit das Skript sie findet.
- [x] D10 Installiert (Jans Ja), drei Testläufe am Gerät. Nachgebessert: angehaltener Song heißt
  "Angehalten · noch m:ss"; Rückgängig im Danach-Block bzw. bei "Als Nächstes"; Bühnen-Hinweise
  über "Danach" (verdeckten Kapo/Countdown); Wunschsong-Liste klar beschriftet; Pause-Seite ohne
  doppelte Zeile; neue Songs übernehmen das übliche Songende des Sets (`ueblichesEnde`, sonst hätte
  ein Wunschsong die Automatik gestoppt). Letzter Lauf: 17/17 OK, Jans Daten unverändert.
- [ ] D11 Jan probiert Fassung 6 selbst (mit echtem Set, mit Ton). Ungetestet am Gerät: Bühne mit
  echtem Text während der Wiedergabe, Ansagen, Start am Gig-Tag mit einem neuen Gig.

## Arbeitsliste E: Pausen, Titel, Wiederholung (15.11. mittags, Jans Rückmeldung zu Fassung 6)

Jan: "Im Prinzip läuft es gar nicht schlecht." Menüführung noch hakelig (zu viele Seiten), das
kommt später. Jetzt umsetzen:

- [x] E1 Pausen schnell umschalten: 0 s · 10 s · 30 s · 1 min · 2 min, jeder Song ohne Eintrag
  10 s (Jans Nachtrag: "0 Sekunden kann auch noch mit rein, standardmäßig 10"). Gespeichert:
  0 = kein Eintrag (10 s), -1 = `KEINE_PAUSE` (0 s), sonst Sekunden, damit alte Daten ohne
  Umschreiben gelten (`PAUSE_WAHL`, `Song.pauseDanach`, `pauseSpeichern`, `naechstePause`;
  Werkstatt-Spielzeit nimmt `pauseDanach`). Einstellungen-Karte und Bearbeiten:
  fünf Knöpfe nebeneinander (`PausenWahl`). "Set bearbeiten": Pausen-Knopf rechts in jeder Zeile,
  ein Tipp schaltet weiter (10 s → 30 s → 1 min → 2 min → 0 s → 10 s). Automatik nimmt immer die Pause des fertigen Songs (früher bei 0 die
  des nächsten). Jans Daten 15.11.: 11× 0 (→ 10 s), 24× 30 s, 7× 1 min.
- [x] E2 Songtitel immer ganz sichtbar, Zeile gleich hoch (`PassenderTitel`: Schrift wird kleiner
  statt "…" oder Umbruch). Einstellungen-Karte 30→18 sp, Bearbeiten, Archiv, Setliste (laufender
  Song), Umstellen, Bühne "Dann"-Liste.
- [x] E3 Im Gig keine Wiederholung ohne Rückfrage (`schonImGig`, `wiederholungsFrage`): Archiv
  (Wunschsong und Songs dazu) zeigt eingeplante Songs grau mit "eingeplant · Set 2" bzw.
  "gespielt · Set 1"; Tipp fragt "Nochmal spielen?" / "Nochmal einplanen?". Ein offener Song im
  selben Set wird ohne Frage nach vorn geholt. Setliste: gespielten Song antippen fragt auch.
- [x] E4 Fassung 7 installiert (Jans Ja, 15.11. ~09:25), `testlauf.sh ablauf` 25/25 OK, neue
  Schritte: Pausen-Knopf rundum, Karte mit fünf Pausen, Archiv grau, Rückfrage. Danach auf Jans
  Wunsch die 3 El-Circo-Sets wie am Abend zurückgeholt (Stand 15.11. 00:41, alle ungespielt; seine
  6 nachts in Set 1 ergänzten Songs sind raus) über `eingang.json` mit `"ganz": true`; Stand davor
  auf dem Mudita als `bestand-vor-wiederherstellung-1794731333417.json` und in
  `.geraet/bestand-vor-sets-zurueck.json`.
- [x] E6 Pausen-Kästchen in der normalen Setliste (Jan fand die Pausen in "Set bearbeiten" nicht):
  kleines umrandetes Kästchen "Pause 30 s" wie in Fassung 5 (13 sp), unter jedem offenen Song und
  beim laufenden Song; Tipp schaltet weiter (`PausenKaestchen`), unsichtbar vergrößerte Tippfläche.
- [ ] E5 Menüführung schlanker (Jan: "zu viele Menüs, zwischen denen man hin und her schaltet"),
  später. Erst Vorschläge mit Favorit.

## Arbeitsliste F: Bedienung wie die alte App (15.11. abends, Jans Auftrag)

Jan: "Ich kann mit der App so nicht arbeiten. Da war die alte Backing Track App wesentlich besser."
Entscheidung: Bedienung wie die alte App, aber Optik und Textgrößen wie Fassung 7 ("super"),
Pausen-Kästchen bleiben, alles für E-Ink und die kleine Größe. Vermisst hat er: alles auf einem
Bildschirm, Player-Leiste unten, Archiv als zweiter Reiter, Wischen (rechts = ganz nach oben/als
Nächstes, links = ganz nach unten), "aber diesmal perfekt funktionierend". Entwurf: `entwuerfe/wie-alt/`.

- [x] F1 Hauptbildschirm `ui/SeiteHaupt.kt` (ersetzt die Live-Setliste, `Seite.Setliste`): Kopf mit
  Reitern "Set ⌄" und "Archiv", Liste in der Mitte, feste Player-Leiste unten (Zustand, Restzeit,
  Titel, Fortschritt bzw. Kapo/Tonart, Knöpfe Text · Play/Pause · Beenden (2 Tipps) bzw. Weiter; in
  der Pause Countdown, "Jetzt starten", "Anhalten"). Reiter bleibt beim Zurückkommen (`HauptZustand`).
- [x] F2 Wischen (`WischZeile`): rechts = `Buehne.vorziehen`, links = neu `Buehne.ansEnde` /
  `Bestand.ansEnde`, je 8 s Rückgängig (Band unten über der Liste). E-Ink: Zeile springt einmal zur
  Seite, sobald es zählt (64 dp), dahinter steht, was passiert. Geste hängt an der Song-ID und ruft
  die Aktionen über `rememberUpdatedState` (Fehler der alten App: Stale Capture). Gespielte Songs:
  rechts = nochmal (Rückfrage). Archiv: gleiche Gesten, mit Rückfrage bei Wiederholung.
- [x] F3 Antippen eines Songs = Einstellungen (SongKarte bzw. Editor), startet nie Musik. Set
  wechseln über die Set-Klappe (Tipp auf den Set-Reiter), dort auch "Set bearbeiten" und "Alle Gigs".
- [x] F4 Text: Knopf in der Leiste öffnet die Bühne (Text-Ansicht wie bisher); am Songende geht es
  von selbst zurück (`BuehneSeite(zurueckAmEnde)`). In der Pause ist "Text" aus.
- [x] F5 App öffnet am Gig-Tag auf dem Hauptbildschirm; Gig-Sets und Wunschsong führen dorthin.
  `werkzeug/testlauf.sh` neu (Wischen per `geraet.sh wisch`), versionCode 8. Tests grün.
- [x] F6 Installiert (15.11. 17:43), `testlauf.sh schnell` 23/23. Nachgebessert: Schild „Nächster“ (abgeschnitten neben „hält an“ → schwarze Nummer, Schild nur wenn es passt), Pausen-Countdown 40 sp. Testskript gibt „Bitte nicht stören“ jetzt sauber zurück.
- [x] F7 Jan hat Fassung 8 am 20.11. mit dem El-Circo-Set ausprobiert: "prinzipiell schön", Wünsche → Arbeitsliste G.

## Arbeitsliste G: Jans Rückmeldung zu Fassung 8 (20.11.)

Jan öffnete das El-Circo-Set. "Prinzipiell finde ich es schön so, wie es ist" (sensibel: nur
diese Punkte ändern). Er wählte bei der Leiste Variante A.

- [x] G1 Liste: Der Song, der jetzt dran ist (läuft oder liegt bereit), steht oben in der Liste,
  schwarz hinterlegt mit Schild „JETZT“ (`JetztZeile`). Bisher stand er nur in der Leiste.
- [x] G2 Leiste (Variante A): groß „DANACH <Titel>“ (bzw. „Set-Ende“ oder „Set B: <Titel>“);
  Zustand, Zeit, Fortschritt bzw. Kapo/Tonart und Knöpfe gehören zum jetzigen Song.
- [x] G3 Text von selbst: Startet ein Set-Song mit Text ab 0:00, geht die Text-Ansicht auf
  (`Zustand.startNr`, `HauptZustand.gesehenerStart`); am Songende zurück (wie bisher). Ohne Text
  bleibt die Set-Ansicht. „Liste“ auf der Bühne führt zurück, der Text springt nicht wieder auf.
- [x] G4 Stopp (ein Tipp, Leiste und Bühne): hält an, springt an den Anfang, Song bleibt dran
  und wird nicht abgehakt; von der Bühne zurück zur Set-Ansicht. Danach erscheint „Weiter“.
  Wischen nach rechts legt dann sofort den anderen Song bereit. „Beenden“ mit zwei Tipps ist weg.
- [x] G5 Blättern per Wischen hoch/runter in allen Seitenlisten (`seitenWischen`), springt
  ohne Gleiten; die letzte Zeile einer Seite steht auf der nächsten oben noch einmal.
  Lautstärketasten blättern weiter mit, sind aber nicht mehr angeschrieben.
- [x] G6 Archiv dichter: Zeilen 48 dp (`ArchivZeile(kompakt)`), Tempo-Leiste 32 dp: 4–5 statt 3 Songs.
- [x] G7 Fassung 9 (versionCode 9) installiert 20.11. 16:05 (Jans Ja), `testlauf.sh ablauf`
  25/26; der eine Fehler war das Testskript (8-s-Song schon zu Ende), Foto korrekt, Schritt
  angepasst. Rückweg `.geraet/fassung8-2026-11-15.apk`, Kopie `.geraet/fassung9-2026-11-20.apk`.
- [x] G8 Jan probierte Fassung 9: Schrift teils abgeschnitten, zu wenige Songs → Arbeitsliste H.

## Arbeitsliste H: Set-Ansicht schöner und lesbar (20.11. nachmittags)

Jan: "Schriften werden abgeschnitten dargestellt teilweise. Zu wenige Songs in Set-Ansicht.
Kompakter und schöner, Schriftgrößen möglichst beibehalten." Danach, als 6 Songs zu sehen waren:
"Lieber nur 5 Songs auf eine Seite, dafür bessere Lesbarkeit in den Sets."

- [x] H1 Abgeschnittene Unterlängen (g, p, y): `PassenderTitel` zeichnet mit `TextOverflow.Visible`
  (die Breite ist schon geprüft). Schildchen haben wieder normale Zeilenhöhe, dafür keine Luft im
  Rahmen (gekürzte Zeilenhöhe kappte dort die Buchstaben). Neu `TextStyle.knapp()` in Bausteine.kt.
- [x] H2 Set-Zeilen neu: Titel allein in der Zeile, mittig, groß (30 sp, notfalls kleiner bis 18),
  Nummer 26 sp; rechts Länge (24 sp) und darunter die Pause als „‖ 30 s“ (Tipp = nächste Pause)
  bzw. „hält an“. Mikro vor dem Titel. 5 Songs pro Seite (64 dp, Jans Wahl). JETZT-Zeile schwarz
  ohne Schild (Beschreibung „Jetzt dran“ für Tests).
- [x] H3 Fußzeile im Set weg, dafür Seitenpunkte rechts (`SeitenListe(seitenPunkte)`, wie inkOS).
- [x] H4 Kopf flacher (Reiter 40 dp), Leiste flacher: Zustand und Kapo/Tonart links übereinander
  neben der großen Zeit, Knöpfe 54 dp (`PLAYER_HOEHE` 160).
- [x] H5 Archiv: 6 statt 3 Songs (Zeilen 47 dp, Tempo-Leiste 30 dp), Infozeile mit Abstand.
- [x] H6 Fassung 10 (versionCode 10) installiert, `testlauf.sh ablauf` 26/26. Kopie
  `.geraet/fassung10-2026-11-20.apk`, Rückweg `.geraet/fassung9-2026-11-20.apk`.
- [x] H7 Jan zu Fassung 10 (21.11.): alte App "haushoch überlegen" → Arbeitsliste I.

## Arbeitsliste I: Bedienung wie die alte Backing-Tracks-App (21.11.)

Jan: "Die alte App ist immer noch haushoch überlegen ... ich komme da total durcheinander."
Beispiele: Schrift wird je nach Titellänge kleiner ("total doof"); Tipp auf einen Song öffnet den
Editor, er will ihn auswählen und spielen. Auftrag: alte App ansehen (GitHub
charmeundmelone-lab/Backing-Tracks, lokal C:\Users\HUAWEI\Backing-Tracks), Plan machen, selbst
nachbessern, Prototyp zum Testen geben. Lesbarkeit vor Menge.

Was die alte App ausmacht (aus MainScreen.kt, GigManagementScreen.kt, PlayerViewModel.kt):
- Ein Bildschirm: Reiter oben, Liste, Player unten. Schrift fest (Titel 20 sp, Ellipse).
- Setliste behält die Reihenfolge; gespielte Songs bleiben an ihrem Platz, nur blass; der
  aktuelle Song ist markiert. Zeile: Nummer · Titel, darunter Tonart · Kapo · Dauer.
- Tipp auf einen Song = auswählen (`loadSetAsQueue` → `selectSong`): er liegt bereit, Play startet.
  Gespielter Song: Rückfrage "Heute bereits gespielt. Trotzdem verwenden?". Wischen rechts =
  als Nächstes, links = ans Ende. Editor nur übers Archiv.
- Player: Restzeit, aktueller Titel groß, darunter "⏭ nächster Song · Capo"; Knöpfe immer gleich
  (Play/Pause breit, Stopp, Loop). Stopp = anhalten, an den Anfang. Mit Text: Text geht beim
  Start auf, am Songende zu.

Plan (Fassung 11):
- [x] I1 Nirgends mehr kleinere Schrift: `PassenderTitel` kürzt zu lange Titel mit "…", Größe fest.
- [x] I2 Setliste wie alt: Reihenfolge bleibt, gespielte Songs blass mit Haken an ihrem Platz,
  aktueller Song schwarz. Zeile: Nummer · Titel (fest, groß), darunter Kapo · Tonart · Länge und
  das Pausen-Schildchen. 5 Songs pro Seite; die Seite folgt dem aktuellen Song nur, wenn er
  sonst nicht zu sehen wäre.
- [x] I3 Tipp auf einen Song = auswählen (liegt bereit, Play startet). Läuft gerade ein Song,
  kommt der angetippte als Nächstes (Hinweis mit Rückgängig, nichts bricht ab). Gespielt:
  Rückfrage. Lang drücken = Einstellungen. Wischen bleibt.
- [x] I4 Archiv nach derselben Regel: Tipp = auswählen (ins Set), lang drücken = Bearbeiten.
- [x] I5 Leiste wie alt: aktueller Titel groß + Restzeit, darunter "Danach: … · Kapo",
  Fortschritt als Linie, immer dieselben drei Knöpfe Text · Play/Pause · Stopp (in der Pause:
  Play = jetzt starten, Stopp = Automatik anhalten). "Weiter" entfällt (anderen Song antippen).
- [x] I6 Testlauf anpassen, Fotos am echten Set prüfen, selbst nachbessern, installieren.
- [x] I7 Selbst nachgebessert: Leiste flacher (5 Songs passen auch beim echten Set), Text „Alle
  Songs gespielt“ statt „Set wählen“, ausgegrauter Play-Knopf zeigt sein Zeichen, Text-Ansicht hat
  dieselben drei Knöpfe (Stopp ausgegraut statt „Weiter“). `geraet.sh lang` + `testlauf.sh`
  (lange drücken, Tipp wählt aus): 28/28. Fassung 11 installiert 21.11., Kopie
  `.geraet/fassung11-2026-11-21.apk`, Rückweg `.geraet/fassung10-2026-11-20.apk`.
- [x] I8 Jan probiert Fassung 11 (21.11. abends): „viel besser“. Rückmeldung → Arbeitsliste J.

## Arbeitsliste J – Fassung 12 (21.11. abends, Jans Rückmeldung zu Fassung 11)

Jan: „viel besser“. Fehler: Set ließ sich nicht öffnen („Noch kein Set gewählt“), solange ein
Song angehalten war; nach Stopp ging es. Wünsche: Kapo-Filter. Akku sehr hoch. In langen Pausen
kam die Bildschirmsperre, gelegentlich knackte es über die PA. Unsinniges finden und berichten.

- [x] J1 Set wählen bei angehaltenem Song: Nur ein laufender Song schiebt das Set nach hinten.
  Angehalten = Song wird beendet (kein Haken), das neue Set liegt sofort bereit. Hauptbildschirm
  zeigt notfalls das gewählte Folge-Set statt „Noch kein Set gewählt“.
- [x] J2 Kapo-Filter in beiden Archiv-Ansichten (alle · ohne · nur vorkommende Bünde).
- [x] J3 Show-Modus: Set aktiv und in der letzten Stunde gespielt/getippt (versteckt: 15 min)
  → Bildschirm bleibt an (jede Seite), Lautstärke + „Bitte nicht stören“ bleiben, Dienst hält
  den Prozess, stille Tonspur (`audio/Stille.kt`) hält die USB-Ausgabe wach (Android legt sie
  sonst 3 s nach dem letzten Ton schlafen, gemessen am Kompakt).
- [x] J4 Akku: Takt im Leerlauf 1×/s statt 5×/s, angehaltene Mischpulte schlafen 500 ms statt
  30 ms, Bildschirm nur noch im Show-Modus wach (früher immer auf dem Hauptbildschirm).
- [x] J5 Testlauf: Schritte „set-trotz-pause“, „wieder-set-b“, „kapo-filter“. Gebaut, Tests grün.
- [x] J7 (Jan: „1 2“) Set antippen, während ein Song läuft: Reiter „Set“ zeigt das gewählte Set als
  Vorschau (Band „Vorschau · beginnt nach diesem Song“ + Knopf „Zu Set …“, umgekehrt „Ansehen“).
  Tipp in der Vorschau stellt den Song dort nach vorne, der laufende bleibt unberührt.
- [x] J8 Letztes Set gemerkt (files/letztes-set.txt): nach Neustart liegt es bereit, die App
  öffnet auf dem Hauptbildschirm. Restzeit-Anzeige bleibt (Jan mag sie), erst messen, falls
  der Akku weiter leer läuft.
- [x] J6a Installiert 21.11. ~18:20 (Jans Ja), Daten gleich. testlauf 13 OK / 20 FEHLT: alles Folgefehler aus Schritt 9 (8-s-Testsong war vor dem Tipp auf „Pause“ schon zu Ende, Set B begann dann richtig). set-trotz-pause und kapo-filter OK. Vorschau am Gerät NICHT geprüft. Lehre: force-stop im aufraeumen lässt „Bitte nicht stören“ an, weil der Show-Modus es nach Home noch 15 min hält → aufraeumen anpassen. Jan behält Fassung 12 für den Gig am 21.11. abends.
- [ ] J6 Mit Jans Ja installieren, testlauf, Stille per `dumpsys media.audio_flinger`
  prüfen (Standby: no während der Pause), dann Jan an der PA probieren lassen.
