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

## Übergabe an die nächste Session (geschrieben 2026-11-15, ca. 00:55)

**Stand in einem Satz:** Arbeitsliste B fertig bis auf B7 (Jan bedient die Werkstatt selbst)
und einen Test mit einer wirklich neuen Datei; Arbeitsliste C (C1–C6, Ende von PLAN.md) fertig.
`main`, alles committet und gepusht, alle Tests grün (`:app:testDebugUnitTest`,
`:werkstatt:test` mit 24 Tests). Auf dem Kompakt läuft **App-Fassung 5** (versionCode 5, Bau
von `94cb219`, App-Code seither unverändert), installiert 14.11. 17:08. Die Werkstatt hinter
Jans Desktop-Symbol ist auf dem neuesten Stand (`installDist` nach `94cb219`).

**Neu am 15.11. nachts (C7):** Werkstatt überschreibt Songdaten vom Mudita nicht mehr (Jans
Regel: "Was ich am Mudita einstelle, bleibt"). Pro offenem Song merkt sie sich die Songdaten
vom Mudita als Basis (`Arbeit.songBasis`, in `zustand.json`); beim Holen/Senden gilt pro Feld
(Titel, Tonart, Kapo, BPM, Tempo): am Laptop geändert → Laptop, sonst → Mudita
(`songdatenMischen`). Text und Teile weiter vom Laptop. Tests in `WerkstattTest` (27 grün).
Nebenbefund: Songs 3/14 waren nur wegen Kapo scheinbar offen, ihr Text war längst auf dem
Mudita; in Jans Werkstatt ist jetzt nichts mehr offen, Bed of Roses dort Kapo 2. Werkstatt
eingespielt (`installDist`). Restlücke: Ändert Jan am Mudita einen Wert, während eine Sendung
dort noch unübernommen liegt (z. B. Bearbeiten-Seite offen), gilt beim Übernehmen der Stand vom
Senden; dafür müsste die App selbst mischen (nicht gebaut).

**Neu am 15.11. nachts:** Automatische Mudita-Sicherung (Windows-Aufgabe "TrikTrak-Sicherung",
alle 5 Minuten, nur bei Änderung, nur lesend) nach `TrikTrak-Werkstatt\sicherungen\mudita-*.json`,
Log `TrikTrak-Werkstatt\sicherung.log`. Eingerichtet und getestet, Details unten bei "TrikTrak
Werkstatt". Bei Fragen zu verlorenen Daten zuerst dort nachsehen.

**Was am 14.11. nachmittags/abends dazukam (Details PLAN.md C1–C6):**
- C1/C2: Pfeile "‹ Voriger" / "Nächster ›" auf Einstellungen (im Set: Setliste) und Bearbeiten
  (Archiv A–Z bzw. Setliste), "gespeichert" sichtbar, Knopf "Fertig" weg. Jan hat die Pfeile
  sofort benutzt und alle Songs selbst durchgesehen.
- C3: Alle Songs haben BPM (aus dem Click gemessen, `werkzeug/ClickTempo.java`) und
  Tempo-Stempel; Werkstatt "Songdaten" hat Spalte "Tempo", `Abgleich.textUndTeile` übernimmt sie.
  Danach hat Jan am Mudita viele Kapo- und einige Tempo-Werte korrigiert (z. B. viele Songs
  Kapo 2, Riptide Kapo 5) und gelöscht: Ein Hoch auf uns, Sunny, Remember us (ohne Kapo),
  Country Roads (Einzelspuren). Jetzt 42 Songs. Diese Werte am Mudita gelten.
- C4: Gig "El Circo" (Sa 14.11.) mit 3 Sets, Show-Automatik, Pausen 30 s / 60 s vor
  Kapo-Wechsel, je Set ein Kapo-Block (Set 1 K3, Set 2 K2 nach Jolene, Set 3 K2 zuerst).
- C5: `Jolene.wav` auf 3:11 gekürzt (`werkzeug/WavEnde.java`), Original in
  `Music\Backing Tracks\Sicherung-Originale\`.
- C6: "Neue Songs" klappt mit geänderter Datei, ABER das Neu-Einlesen holt gelöschte Songs zurück,
  solange ihre Dateien im Musikordner liegen. Umgehung: Dateien vorher parken (PLAN.md C6).

**Stand nach dem Gig (Mudita-Daten 15.11. 00:41, `.geraet/bestand-2026-11-15.json`):**
In Set 3 sind The World I've known, Tennessee Whiskey und Bed of Roses als gespielt markiert,
in Set 1 und 2 nichts. Jan hat "Englishman in New York" an den Anfang von Set 1 gezogen.
"All night Long" ist NICHT in Set 1 (mein Vorschlag war: ans Ende von Set 1, Two Princes Pause
1:00). Wie der Abend lief, ist unbekannt: Jan fragen.

**Als Nächstes:**
1. Jan nach "El Circo" fragen: Lief TrikTrak den ganzen Abend? Warum nur Set 3 gespielt
   markiert (Sets 1/2 auf dem Nothing? oder Haken nur bei Set 3?). Automatik mit Pausen,
   Warnton, "Timer anhalten", Kapo-Wechsel, Show-Ruhe, Lautstärke. Probleme gehen vor allem.
2. C6 verbessern (mit Jan klären): beim Neu-Einlesen gelöschte Dateien nicht wieder anlegen
   (z. B. Liste "gelöscht" im Bestand), damit "Neue Songs" ohne Parken geht.
3. B7: Jan probiert die Werkstatt selbst (Songdaten, Gigs, Neue Songs, Teile benennen,
   Text ab hier ↓). Zuschauen, wo er hängt; erst Vorschläge mit Favorit, dann bauen.
4. "Perfect K1.wav": Band-Kanal links stumm (−108 dB), Jan muss neu exportieren; dann über
   "Neue Songs" (mit Parken, siehe C6) kopieren.
5. Offen, nicht dringend: "Timer anhalten" am Gerät ansehen (B20); OK-Fenster bei "Ton freigeben" (B24);
   Testsongs "Test Eins/Zwei/Drei" (nur mit Jans OK löschen); tote Seiten
   TexteHolen/TeileUndAblauf.

**Lehren 14.11.:**
- Jan arbeitet oft gleichzeitig am Mudita. Vor jedem Schreiben frisch sichern und mit der
  letzten Sicherung vergleichen (`cmp`), bei Abweichung erst nachsehen, was er geändert hat.
- In der Set-Übersicht macht Antippen eines Sets es AKTIV und legt seinen ersten Song bereit:
  nach Arbeiten an anderen Sets das richtige Set wieder antippen.
- Die Pause gehört zum Song (`pauseSek`), nicht zum Set-Eintrag; die Werkstatt kann sie nicht
  setzen. Am Mudita tippen; lange Titel brechen um und schieben die Knöpfe nach unten.
- TrikTrak übernimmt `eingang.json` nicht, solange eine Bearbeiten-/Einmessen-Seite offen ist.


**Was am 13./14.11. vormittags dazukam (Details und Gerätetests am Ende von PLAN.md):**
- Gig 13.11. "Bar Eriksson": Jan wechselte nach 18 Minuten aufs Nothing Phone, weil die
  Bedienung hakte. Daraus B18–B22: Zurück folgt einem festen Weg (Bühne → Setliste → Sets →
  Gigs, auf Gigs geht die App nicht mehr zu); Tipp auf einen Song öffnet die Auswahlseite
  (`ui/SeiteSongWahl.kt`) mit "Jetzt spielen" (legt bereit, auch aus der Pause; rückt vor den
  bisher nächsten) und "Danach spielen"; Einstellungen dahinter; klare Knöpfe ("Nochmal
  einplanen", "Timer anhalten", "Anderer Song", "Jetzt starten"); Kapo und Tonart groß in der
  Pause und auf der Bühne vor dem Start (`KapoTonart`); Archiv-Tipp nimmt keinen Song mehr aus
  Versehen aus dem Set.
- B17: Kompakt war 2 dB lauter als das Nothing Phone 3a (gemessen per `dumpsys`: Nothing-Kurve
  endet bei −2 dB). `Mischpult.AUSGANG_DB = -2`, Medienlautstärke bleibt voll.
- B23 Show-Ruhe (`audio/ShowRuhe.kt`): "Bitte nicht stören" nur für Medien, solange TrikTrak die
  Lautstärke hält; danach genau die alte Regel zurück (Merker `files/ruhe-vorher.txt`). Jan hat
  den Zugriff erteilt (Einstellungen → Zugriff während „Bitte nicht stören“ → TrikTrak).
- B24: Androids Gehörschutz-Sperre für Kopfhörer/USB (Kompakt: Stufe 2 von 15, praktisch stumm)
  blockiert das Hochstellen ohne Meldung und kommt nach Spielzeit wieder. Jetzt großer Hinweis
  "Ton gedrosselt" mit Knopf "Ton freigeben" (`VolleLautstaerke.gedrosselt`, `freigeben()`).
  Simulation: `files/test-leise` + `files/test-gedrosselt`.
- Werkstatt: Songdaten-Tabelle (Titel, Tonart, Kapo, BPM; `Abgleich.textUndTeile` übernimmt sie);
  Plätze ohne Gesang benennen (B8, leerer Abschnitt "[Intro]", `Abgleich.ohneGesangBenennen`);
  B9 "Text ab hier ↓" rückt nur gesungenen Text, Intro/Solo bleiben an ihrer Zeit;
  B11 Seite "Gigs" (Regel "Mudita schützen" in `daten/GigAbgleich.kt`: Laptop ersetzt einen Gig
  nur, wenn er am Mudita seit dem Holen unverändert ist; sonst bleibt der Mudita-Stand, die
  Laptop-Fassung landet in `sicherungen/laptop-gig-*`; neue Gigs/Sets am Laptop haben negative
  Nummern, der Mudita vergibt echte); B12 Seite "Neue Songs" (vergleicht
  `Music\Backing Tracks\Nothingphone` mit dem Mudita-Ordner, kopiert per adb, App liest dann
  alle freigegebenen Ordner neu). Ansagen nimmt Jan nur am Mudita auf (seine Wahl).
- Gig- und Song-Senden verlangt App-Fassung 4 (`Geraet.GIGS_AB`), sonst sendet die Werkstatt
  nichts. Vor dem Senden holt sie frisch und meldet Konflikte.

**Jans Entscheidungen, die weiter gelten:**
- Text und Teile NUR am Laptop. Gigs, Setlisten, Songdaten, neue Songs am Laptop UND am Mudita.
  Ansagen nur am Mudita.
- Maßgeblich sind die fertigen Songs in `C:\Users\HUAWEI\Music\Backing Tracks\Nothingphone`
  (42 WAV). Umbenennen in "Mudita" ist erlaubt, aber jetzt hängen die Werkstatt
  (`NeueSongs.laptopOrdner`) und der Ordner auf dem Mudita daran: vorher anpassen.
- Bühne: 34 sp Barlow Condensed (Schmal), volle Seiten; live "gut lesbar, passt" (13.11.).
- Lautstärke in TrikTrak immer voll (minus 2 dB in der App).

**TrikTrak Werkstatt:**
- Start für Jan: Desktop-Symbol "TrikTrak Werkstatt" (oder `TrikTrak-Werkstatt.bat`), öffnet
  `http://127.0.0.1:47811`. Seiten: Songliste mit Teile-Karten, Text, Text aus dem Internet,
  Gigs, Gig-Check, Songdaten, Neue Songs, Sicherungen.
- Bauen: `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew --no-daemon -q :werkstatt:test :werkstatt:installDist`.
  `installDist` nur, wenn Jans Werkstatt aus ist (sonst ist die jar gesperrt), danach startet die
  `.bat` den neuen Stand.
- Arbeitsdaten außerhalb des Repos: `C:\Users\HUAWEI\TrikTrak-Werkstatt\` (`bestand.json`,
  `zustand.json` mit `geaendert`, `gigsGeaendert`, `gigsGesendet`, `sicherungen/`). Holt beim
  Start automatisch, wenn der Mudita dranhängt.
- **Automatische Sicherung (seit 15.11., Jans Wunsch):** Windows-Aufgabe "TrikTrak-Sicherung"
  alle 5 Minuten → `TrikTrak-Werkstatt\TrikTrak-Sicherung-Start.exe` (ohne Fenster) →
  `TrikTrak-Sicherung.ps1`. Hängt der Mudita per USB dran, holt sie `bestand.json` und legt
  `sicherungen\mudita-JJJJMMTT-HHMMSS.json` an, nur bei Änderung (gleiches Format wie die
  Werkstatt, erscheint auf deren Seite "Sicherungen"; die neuesten 60 bleiben). Nur lesend.
  Log: `TrikTrak-Werkstatt\sicherung.log`. Quellen im Repo: `werkzeug/TrikTrak-Sicherung.ps1`
  und `werkzeug/TrikTrak-Sicherung-Start.cs` (Bauanleitung im Kopf); nach Änderung ps1 in den
  Datenordner kopieren. Aus/an: `Disable-ScheduledTask` / `Enable-ScheduledTask -TaskName "TrikTrak-Sicherung"`.
  Ersetzt NICHT das frische Sichern direkt vor eigenem Schreiben/Installieren.
- Selbst testen, ohne Jans Daten anzufassen: jars aus `werkstatt/build/install/werkstatt/lib/`
  in einen Scratch-Ordner kopieren, `java -Dtriktrak.daten=<Ordner mit /> -Dfile.encoding=UTF-8 -cp "lib/*" show.jangregersen.werkstatt.MainKt --ohne-browser`
  im Hintergrund starten (leerer Datenordner = frisch vom Mudita holen), per `curl` oder im
  eingebauten Browser testen (POST braucht `X-TrikTrak: 1`). Nur starten, wenn Jans Werkstatt aus
  ist; danach beenden (`Get-NetTCPConnection -LocalPort 47811 -State Listen | % { Stop-Process -Id $_.OwningProcess -Force }`)
  und prüfen, dass kein `java.exe` mit "werkstatt" mehr läuft. Sendet die Testinstanz, ändert
  das den echten Mudita: nur am Test-Gig "Testlauf", danach exakt zurückstellen und mit der
  Sicherung vergleichen (`cmp`).
- **Lehre 14.11.:** Unter Windows kommen doppelte Anführungszeichen nicht heil bei `adb shell`
  an. Pfade für die Mudita-Shell immer mit `Geraet.shText` (einfache Anführungszeichen) bauen.

**Studio One (an Jans Dateien geprüft):**
- Songs: `C:\Users\HUAWEI\Documents\Studio One\Songs*\<Ordner>\<Name>.song`, ZIP, darin
  `Song/song.xml`. Nur lesen, ZIP mit Latin-1 öffnen. Zeiten in Vierteln (`timeFormat="2"`) oder
  Sekunden (`timeFormat="0"`), auch gemischt. Ohne Arranger-Track nimmt die Werkstatt benannte
  Marker. Jan exportiert genau seine Arranger-Teile; über 3 s Längenunterschied = andere Fassung.
- Jans Labels sind lose ("Vers" auch über Refrains, "OURTO"): seine Teile sind Zeit-Fächer, der
  Text wird der Reihe nach hineingelegt.

**Kompakt (Gerät):**
- **GERÄTE-REGEL:** Installieren und Tippen nur mit Jans Ja im Chat (gilt pro Auftrag). Vor JEDEM
  Tipp `sh werkzeug/geraet.sh fokus`. Läuft TrikTrak mit einem Song: Finger weg. Jan nutzt den
  Kompakt selbst und hat oft Bluetooth-Kopfhörer verbunden.
- Vor jeder Installation `bestand.json` sichern und danach per `cmp` vergleichen (bisher immer
  Byte für Byte gleich). Rückwege: `.geraet/fassung5-2026-11-14.apk` (jetzt installiert), `.geraet/fassung4-2026-11-14.apk`,
  `.geraet/vor-b6-2026-11-13.apk`, `.geraet/vor-b2-2026-11-13.apk`, `.geraet/alt-2026-11-13.apk`;
  Daten zuletzt `.geraet/bestand-2026-11-15.json` (nach dem Gig), davor
  `.geraet/bestand-2026-11-14-elcirco-fertig.json`. Ältere Fassungen können
  `eingang.json` mit Gigs nicht lesen.
- **Lautstärke:** TrikTrak stellt die Medienlautstärke beim Öffnen für das aktuelle Ausgabegerät
  auf voll und gibt beim Verlassen genau dessen alte Stufe zurück (Lautsprecher stand zuletzt
  auf 12, Bluetooth-Kopfhörer auf 2). Für Tests mit Ton VORHER `files/test-leise` anlegen,
  danach löschen und prüfen. Gleichzeitig schaltet TrikTrak "Bitte nicht stören" (nur Medien) an
  und beim Verlassen zurück; prüfen mit `dumpsys notification | grep mZenMode`.
- Werte aus `dumpsys audio`: USB-Adapter "USB PnP Audio Device" läuft auf 15/15 = 0 dB;
  Gehörschutz-Sperre `mSafeUsbMediaVolumeIndex=20` (Stufe 2), Zustand zuletzt INACTIVE.
- Nothing Phone 3a ist per WLAN-adb erreichbar (Jan schaltet "Kabelloses Debugging" ein, dann
  `adb mdns services` → `adb connect`). Diente zum Vergleich der Lautstärke-Kurven.
- PIN-Sperre: Claude kann nicht entsperren. Gesperrt geht nur Sichern und Installieren.

**Werkzeuge:**
- App bauen: `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew --no-daemon -q :app:testDebugUnitTest :app:assembleDebug`,
  danach `df -h /c` (14.11.: 17 GB frei). JSON-Tests gehören in `werkstatt/src/test` (in
  App-Unit-Tests ist `org.json` nur eine Attrappe).
- Gerät: `"$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe" -s LD20241004967 …`, Sichern:
  `MSYS_NO_PATHCONV=1 adb … exec-out run-as show.jangregersen.buehne cat files/bestand.json > .geraet/bestand-DATUM.json`,
  Installieren: `adb … install -r app/build/outputs/apk/debug/app-debug.apk`.
- Steuern: `sh werkzeug/geraet.sh fokus | texte | tap "Text" [n] | shot name | key 25 | xy X Y`
  (Bilder in `.geraet/`). Starten: `adb … shell monkey -p show.jangregersen.buehne -c android.intent.category.LAUNCHER 1`.
  Symbol-Knöpfe stehen nicht in `texte`: Koordinaten aus einem Foto. Bühne unten: Liste ≈ (64,757),
  Play ≈ (286,757).
- Bash-Heredocs mit Kotlin/JS scheitern hier manchmal: Dateien mit dem Write-Werkzeug schreiben.
  Kein Python auf dem Laptop; für Auswertungen Java-Einzeldateien (`java Datei.java`) aus dem JBR.

**Code-Landkarte (nur das Nötige lesen):**
- Gemeinsam (App + Werkstatt, frei von Android-Teilen halten; Liste in `werkstatt/build.gradle.kts`):
  `lyrics/*` (Abschnitte, Zeitplan, Einmessung, Abgleich, Erkennung, Uebernahme),
  `daten/Modell.kt`, `daten/Aenderungen.kt`, `daten/BestandJson.kt`, `daten/GigAbgleich.kt`,
  `netz/Lrclib.kt`. Wer dort ändert, baut beide Testläufe.
- Nur App: `ui/Wurzel.kt` (Navi mit festem Weg, Hinweis "Ton gedrosselt"), `ui/SeiteSongWahl.kt`,
  `ui/SeiteBuehne.kt` (Bühne, Pausen-Bildschirm), `ui/SeiteSetliste.kt`, `ui/SeitenSong.kt`
  (Einstellungen-Karte, Mixer), `ui/SeitenArchiv.kt`, `ui/SeitenGigs.kt` (Karte "Show-Ruhe
  erlauben"), `spiel/Buehne.kt` (Zustand, Automatik, `jetztBereitlegen`), `audio/Mischpult.kt`
  (`AUSGANG_DB`), `audio/VolleLautstaerke.kt`, `audio/ShowRuhe.kt`, `audio/Import.kt`,
  `daten/Ablage.kt` (`eingangUebernehmen`, `neuSuchen`).
- Nur Werkstatt (`werkstatt/src/main/kotlin/…/werkstatt/`): `Main.kt` (alle `/api/`-Aufrufe,
  `sendenAlles`, `gigTun`), `Arbeit.kt` (Arbeitskopie, Gig-Basis, `gigsDarauflegen`),
  `StudioOne.kt` (`Vorschlag`, `nachHinten`/`nachVorn`), `Geraet.kt` (adb, `shText`,
  `musikKopieren`), `NeueSongs.kt`; Seite in `werkstatt/src/main/resources/web/`.
- Tests: `app/src/test/…/LyricsTest.kt`, `EinmessungTest.kt`, `LogikTest.kt`,
  `GigAbgleichTest.kt`; `werkstatt/src/test/…/WerkstattTest.kt`.

**Nicht TrikTrak, aber am 14.11. besprochen:** Jan hat eine Idee "Songaufbau" (Takte pro Teil aus
der Originalaufnahme erkennen → Studio-One-Vorlage für Drumloops) als eigenes Projekt
vorgemerkt. Notiz: `C:\Users\HUAWEI\Projektideen\Songaufbau.md`. Nicht in TrikTrak einbauen,
solange Jan es nicht sagt.

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

Testsongs "Test Eins/Zwei/Drei" im Archiv (alle drei im Test-Gig "Testlauf"), Platzhalter-Lyrics in "Test Eins" und "Country Roads",
"Frei spielen" aus der alten App fehlt, App-Symbol offen (inkOS zeigt nur Namen). Der Abschnitt
"Strophe 1" kann in einem Text zweimal vorkommen (Wiederholung); beim Umbenennen werden Strophen
neu durchgezählt.
