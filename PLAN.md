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
- [ ] B1 "Vom Mudita holen" mit angeschlossenem, entsperrtem Kompakt testen (liest nur). Prüfen:
  Songs erscheinen, Sicherung liegt in `TrikTrak-Werkstatt\sicherungen\`, eine Laptop-Änderung an
  Text/Teilen übersteht ein zweites Holen (`Arbeit.vomGeraet` + `Abgleich.textUndTeile`).
- [ ] B2 "Zum Mudita senden". Entwurf: Laptop schreibt die geänderten Songs als
  `files/eingang.json` aufs Gerät (per adb: `push` nach `/data/local/tmp`, dann `run-as … cp`;
  `run-as` geht, weil Debug-Bau). Die App liest die Datei beim Start/Wiederkehren, führt pro Song
  mit `Abgleich.textUndTeile(geraet, laptop)` zusammen, löscht die Datei und zeigt einen Hinweis.
  Nicht `bestand.json` direkt überschreiben: Die App hält den Bestand im Speicher und schriebe
  ihn wieder drüber. Laptop sendet nur, wenn die installierte App das kann (`versionCode`
  hochzählen und per `dumpsys package` prüfen). Nach dem Senden `geaendert` leeren und frisch
  holen. Am Gerät erst installieren, wenn Jan es freigibt (vorher `bestand.json` sichern).
- [ ] B3 Text aus dem Internet in der Werkstatt holen (LRCLIB). `netz/Lrclib.kt`,
  `lyrics/Erkennung.kt` und `Uebernahme.kt` sind schon eingebunden; es fehlen der Aufruf in
  `Main.kt` und die Seite (Treffer zeigen, wählen, übernehmen). Bis dahin: Text von Hand einfügen.
- [ ] B4 Am Mudita "Teile & Ablauf" zurückbauen auf Ansehen mit vollem Text (Jans Wunsch: Text und
  Teile nur am Laptop). Erst wenn B2 läuft, sonst kann er nirgends mehr ordnen.

Lesbarkeit (Jan ist kurzsichtig, Bühnentext 24–31 sp ist ihm zu klein):
- [ ] B5 Drei Probeseiten in verschiedenen Schriftgrößen als Bilder in die App "Entwürfe" legen
  (`entwuerfe/werkzeug/render.sh`, `bau.sh betrachter`), Jan stellt den Kompakt an den
  Mikroständer und sagt, welche er lesen kann. TrikTrak dafür nicht anfassen.
- [ ] B6 Danach die Bühne umbauen. Claudes Favorit "halbe Seite": deutlich größere Schrift, 4–5
  Zeilen pro Seite, Text steht ruhig, nur Rahmen/Keil wandern, es wird öfter geblättert.
  Alternative "große Zeile": laufende Zeile riesig, darunter die nächste (wechselt bei jeder
  Zeile). Jan hat noch nicht gewählt.

Werkstatt verbessern (nach Jans erstem Ausprobieren):
- [ ] B7 Jan probiert die Werkstatt aus (Doppelklick `TrikTrak-Werkstatt.bat`) und sagt, wo er
  hängen bleibt. Noch nicht mit ihm zusammen angesehen.
- [ ] B8 Jans eigene Labels für Teile ohne Gesang auf der Bühne zeigen (Intro, Solo, Outro statt
  "Instrumental"). Weg: leerer Textabschnitt mit diesem Namen (`[Intro]` ohne Zeilen), ein Teil
  ohne Zeilen gilt überall schon als Instrumental.
- [ ] B9 "Text ab hier ↓" schiebt bisher die ganze Folge samt leerer Fächer. Mit Jan klären, ob
  nur die Texte wandern sollen.
- [ ] B10 Für Songs ohne gleichnamige Studio-One-Datei (11 von 42, Liste oben unter "Fertige
  Songs") die Datei von Hand wählen lassen; für Songs mit Markern statt Arranger-Teilen prüfen.

Stufen 2 und 3:
- [ ] B11 Stufe 2: Gigs und Setlisten am Laptop (bleiben auch am Mudita). Braucht eine Regel für
  beidseitige Änderungen (Vorschlag: pro Set gewinnt die neuere Änderung, Zeitstempel fehlen noch
  im Modell).
- [ ] B12 Stufe 3: neue Songs aus `Music\Backing Tracks\Nothingphone` aufs Gerät, Songdaten
  (Titel, Tonart, Kapo, BPM), Ansagen.

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
