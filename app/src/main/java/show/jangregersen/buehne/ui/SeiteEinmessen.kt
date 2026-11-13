package show.jangregersen.buehne.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import show.jangregersen.buehne.App
import show.jangregersen.buehne.daten.songAendern
import show.jangregersen.buehne.lyrics.Abschnitt
import show.jangregersen.buehne.lyrics.Abschnitte
import show.jangregersen.buehne.lyrics.Ablauf
import show.jangregersen.buehne.lyrics.Einmessung
import show.jangregersen.buehne.lyrics.Teil
import show.jangregersen.buehne.lyrics.Zeitplan

/**
 * Einmessen, Zeile für Zeile: Der Song läuft, Jan tippt jede Zeile in dem Moment, in dem er sie
 * zu singen beginnt. Auf dem großen Knopf steht immer die Zeile, die als Nächstes kommt; endet ein
 * Teil, die erste Zeile des nächsten Teils im Ablauf. Abweichungen gehen live: "Anderer Teil"
 * (einfügen, z. B. Refrain noch mal) und "Auslassen". Oben steht der Text mit Kontext: Zeile davor,
 * die laufende (fett, schwarzer Keil), die nächste (hohler Keil) und was danach kommt.
 */
@Composable
fun EinmessenSeite(navi: Navi, songId: Long) {
    val b by App.ablage.stand.collectAsState()
    val z by App.buehne.zustand.collectAsState()
    val song = b.song(songId) ?: run { WegHier(navi); return }
    val abschnitte = remember(song.lyrics) { Abschnitte.zerlegen(song.lyrics) }
    val ablauf = remember(song.marken, abschnitte) { Ablauf.von(song.marken, abschnitte) }
    val laeuft = z.probe && z.songId == songId && z.spielt
    val dauer = if (z.dauerMs > 0) z.dauerMs else song.dauerMs
    var wahlAb by remember { mutableStateOf<Long?>(null) }
    var neuFrage by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { App.buehne.probeEnde() } }
    Lautstaerketasten { }

    // Jede eigene Änderung macht den Ablauf "echt" (vorläufig = aus dem Original übernommen).
    fun setzen(l: List<Teil>) = App.ablage.aendern { it.songAendern(songId) { s -> s.copy(marken = Ablauf.schreiben(l), markenVorlaeufig = false) } }
    fun getippt(t: Teil): List<Long> = Einmessung.getippt(abschnitte, t)

    // Stand: letzter eingemessener Teil und das nächste Ziel (Teil, Zeile).
    val k = Einmessung.letzter(ablauf)
    val ziel = Einmessung.ziel(abschnitte, ablauf)
    val vorlaeufig = song.markenVorlaeufig && Ablauf.hatZeiten(ablauf)

    fun tippen() {
        val neu = Einmessung.tippen(abschnitte, ablauf, App.buehne.positionJetzt())
        if (neu != ablauf) setzen(neu)
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().background(Schwarz).padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Etikett("Einmessen", farbe = Weiss)
                T(song.titel, titelStil(26.sp, Weiss), zeilen = 1)
            }
            if (laeuft) LaufZeit(dauer) else T(zeit(dauer), titelStil(22.sp, Weiss))
        }
        if (laeuft) {
            val ab = wahlAb
            if (ab != null) {
                TeilWahl(abschnitte, Modifier.weight(1f), abbrechen = { wahlAb = null }) { a ->
                    setzen(Einmessung.teilBeginnt(abschnitte, ablauf, a, ab)); wahlAb = null
                }
            } else {
                KontextAnsicht(abschnitte, ablauf, k, k?.let { getippt(ablauf[it]).size - 1 }, ziel, Modifier.weight(1f))
                Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (ziel != null) {
                        val zt = ablauf[ziel.first]
                        val zeilen = Zeitplan.zeilen(abschnitte, zt)
                        Knopf(Modifier.fillMaxWidth(), Art.VOLL, 112.dp, 16.dp, onClick = { tippen() }) { f ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 12.dp)) {
                                Etikett(if (ziel.second == 0) "${Ablauf.name(abschnitte, zt)} beginnt" else "Tippen, wenn du singst", farbe = f)
                                T(if (zeilen.isEmpty()) "Tippen, wenn es losgeht" else zeilen[ziel.second], titelStil(25.sp, f).copy(lineHeight = 27.sp), zeilen = 2, mitte = true)
                            }
                        }
                    } else {
                        Knopf(Modifier.fillMaxWidth(), Art.UMRISS, 112.dp, 16.dp, onClick = { App.buehne.probeEnde() }) { f ->
                            Symbol(Sym.HAKEN, f, 24.dp); T("Alles getippt · Stopp", titelStil(26.sp, f))
                        }
                    }
                    // Zwei Reihen: In einer Reihe waren die vier Beschriftungen am Gerät abgeschnitten.
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextKnopf("Anderer Teil", Modifier.weight(1f), hoehe = 46.dp, groesse = 17.sp) { wahlAb = App.buehne.positionJetzt() }
                        TextKnopf("Teil auslassen", Modifier.weight(1f), hoehe = 46.dp, groesse = 17.sp, aktiv = Einmessung.kannAuslassen(abschnitte, ablauf)) {
                            val zi = ziel?.first ?: return@TextKnopf
                            setzen(Einmessung.auslassen(abschnitte, ablauf))
                            App.buehne.hinweis("„${Ablauf.name(abschnitte, ablauf[zi])}“ ausgelassen. Zurück geht es mit „Anderer Teil“.")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextKnopf("Letzter Tipp weg", Modifier.weight(1f), hoehe = 46.dp, groesse = 17.sp, aktiv = k != null) {
                            setzen(Einmessung.tippWeg(abschnitte, ablauf))
                        }
                        TextKnopf("10 s zurück", Modifier.weight(1f), hoehe = 46.dp, groesse = 17.sp) {
                            App.buehne.springen(-10_000)
                            val neu = Einmessung.zurueck(ablauf, App.buehne.positionJetzt())
                            if (neu != ablauf) setzen(neu)
                        }
                    }
                }
                Abstand(14.dp)
            }
        } else {
            T(when {
                !Ablauf.hatZeiten(ablauf) -> "Starte den Song und tippe jede Zeile in dem Moment, in dem du sie singst. Andere Reihenfolge? Vorher unter „Teile & Ablauf“ ordnen oder live mit „Anderer Teil“."
                vorlaeufig -> "Die Zeiten stammen aus dem Original (gleiche Länge). Prüfe sie einmal auf der Bühne. Singst du es anders: neu einmessen."
                ziel == null -> "Alles eingemessen. Prüfe es auf der Bühne."
                else -> "Dein Ablauf mit Zeiten. Prüfe ihn auf der Bühne oder miss dort weiter, wo du aufgehört hast."
            }, textStil(16.sp, gewicht = FontWeight.Medium), Modifier.padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 4.dp))
            AblaufListe(abschnitte, ablauf, Modifier.weight(1f))
            Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (neuFrage) {
                    T("Alle getippten Zeiten löschen und von vorn einmessen?", textStil(19.sp, gewicht = FontWeight.Bold), Modifier.padding(horizontal = 4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TextKnopf("Abbrechen", Modifier.weight(1f), hoehe = 58.dp, groesse = 18.sp) { neuFrage = false }
                        TextKnopf("Ja, von vorn", Modifier.weight(1f), Art.VOLL, 58.dp, 18.sp) {
                            neuFrage = false
                            if (App.buehne.probe(songId)) setzen(Ablauf.abHierOhneZeit(ablauf, 0))
                        }
                    }
                } else if (!Ablauf.hatZeiten(ablauf)) {
                    Knopf(Modifier.fillMaxWidth(), Art.VOLL, 80.dp, 16.dp, onClick = { App.buehne.probe(songId) }) { f ->
                        Symbol(Sym.PLAY, f, 26.dp); T("Song starten", titelStil(30.sp, f))
                    }
                    TextKnopf("Teile & Ablauf ordnen", Modifier.fillMaxWidth(), hoehe = 50.dp, groesse = 17.sp) { navi.zu(Seite.Pruefen(songId)) }
                } else {
                    if (vorlaeufig) Knopf(Modifier.fillMaxWidth(), Art.VOLL, 58.dp, 16.dp, onClick = { App.ablage.aendern { it.songAendern(songId) { s -> s.copy(markenVorlaeufig = false) } }; navi.zurueck() }) { f ->
                        Symbol(Sym.HAKEN, f, 22.dp); T("Passt so", titelStil(26.sp, f))
                    }
                    TextKnopf("Auf der Bühne prüfen", Modifier.fillMaxWidth(), if (vorlaeufig) Art.UMRISS else Art.VOLL, 58.dp, 20.sp, symbol = Sym.PLAY) { navi.zu(Seite.ProbeBuehne(songId)) }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Spielt kurz vor dem letzten Tipp weiter. Gelöscht wird dabei nichts.
                        TextKnopf("Weiter einmessen", Modifier.weight(1f), hoehe = 52.dp, groesse = 16.sp, aktiv = ziel != null) {
                            App.buehne.probe(songId, Einmessung.weiterAb(abschnitte, ablauf))
                        }
                        TextKnopf("Neu einmessen", Modifier.weight(1f), Art.GESTRICHELT, 52.dp, 16.sp) { neuFrage = true }
                    }
                }
            }
        }
    }
}

/** Laufzeit im Kopf. Eigener Baustein, damit der 200-ms-Takt nur diese eine Zahl neu zeichnet. */
@Composable
private fun LaufZeit(dauer: Long) {
    val pos by App.buehne.position.collectAsState()
    T("${zeit(pos)} / ${zeit(dauer)}", titelStil(22.sp, Weiss))
}

/**
 * Text mit Kontext: eine Zeile vor der laufenden, die laufende (fett, voller Keil), danach die
 * folgenden Zeilen; die als Nächstes zu tippende hat einen hohlen Keil. Endet der Teil, folgt der
 * Anfang des nächsten Teils mit seinem Etikett.
 */
@Composable
private fun KontextAnsicht(abschnitte: List<Abschnitt>, ablauf: List<Teil>, k: Int?, laufend: Int?, ziel: Pair<Int, Int>?, modifier: Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        // Nur so viele Zeilen, wie ganz hineinpassen (eine halb abgeschnittene Zeile wäre nur Unruhe).
        val zeilenHoehe = 33.dp
        val fuerZeilen = maxHeight - 48.dp
        val platz = (fuerZeilen / zeilenHoehe).toInt().coerceAtLeast(2)
        val dannFolgt = ziel != null && ziel.first != k
        Column(Modifier.fillMaxSize().padding(start = 10.dp, end = 14.dp, top = 8.dp).clipToBounds(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val teil = k?.let { ablauf[it] }
            Row(Modifier.padding(start = 8.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Etikett("Gerade")
                if (teil != null) AbschnittPille(Ablauf.name(abschnitte, teil), hoehe = 32.dp, groesse = 16.sp) else T("Vorspiel", textStil(19.sp, gewicht = FontWeight.Bold))
            }
            var gezeigt = 0
            if (teil != null) {
                val zeilen = Zeitplan.zeilen(abschnitte, teil)
                if (zeilen.isEmpty()) { T("— instrumental —", textStil(21.sp, gewicht = FontWeight.Medium), Modifier.padding(start = 30.dp)); gezeigt = 1 }
                val von = ((laufend ?: 0) - 1).coerceAtLeast(0)
                zeilen.forEachIndexed { i, s ->
                    if (i < von || gezeigt >= platz) return@forEachIndexed
                    val keil = when {
                        i == laufend -> true
                        ziel != null && ziel.first == k && ziel.second == i -> false
                        else -> null
                    }
                    KontextZeile(s, keil, fett = i == laufend)
                    gezeigt++
                }
            }
            // Nächster Teil (wenn das Ziel dort liegt, oder noch nichts eingemessen ist).
            if (dannFolgt && ziel != null) {
                val n = ablauf[ziel.first]
                Row(Modifier.padding(start = 8.dp, top = 6.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Etikett(if (teil == null) "Zuerst" else "Dann")
                    AbschnittPille(Ablauf.name(abschnitte, n), hoehe = 30.dp, groesse = 15.sp)
                }
                val zeilen = Zeitplan.zeilen(abschnitte, n)
                if (zeilen.isEmpty()) KontextZeile("— instrumental —", false, fett = false)
                val rest = ((fuerZeilen - zeilenHoehe * gezeigt - 44.dp) / zeilenHoehe).toInt().coerceIn(1, 3)
                zeilen.take(rest).forEachIndexed { i, s -> KontextZeile(s, if (i == 0) false else null, fett = false) }
            }
        }
    }
}

@Composable
private fun KontextZeile(text: String, keil: Boolean?, fett: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(26.dp), contentAlignment = Alignment.Center) { if (keil != null) KeilKlein(keil) }
        T(text, textStil(21.sp, gewicht = if (fett) FontWeight.ExtraBold else FontWeight.Medium), zeilen = 1)
    }
}

@Composable
fun KeilKlein(voll: Boolean) {
    Canvas(Modifier.size(width = 12.dp, height = 16.dp)) {
        val p = Path().apply { moveTo(0f, 0f); lineTo(size.width, size.height / 2); lineTo(0f, size.height); close() }
        if (voll) drawPath(p, Schwarz) else drawPath(p, Schwarz, style = Stroke(width = 2.5.dp.toPx()))
    }
}

/** Auswahl eines Teils: jeder Abschnitt einmal (gleiche Wiederholungen zusammengefasst) und Instrumental. */
@Composable
fun ColumnScope.TeilWahl(abschnitte: List<Abschnitt>, modifier: Modifier, frage: String = "Welcher Teil beginnt? Die Zeit ist schon gemerkt.", abbrechen: () -> Unit, waehlen: (Int) -> Unit) {
    val eintraege = remember(abschnitte) {
        abschnitte.indices.filter { i -> (0 until i).none { j -> abschnitte[j].name == abschnitte[i].name && abschnitte[j].zeilen == abschnitte[i].zeilen } } + Ablauf.INSTRUMENTAL
    }
    T(frage, textStil(16.sp, gewicht = FontWeight.Bold), Modifier.padding(start = 18.dp, end = 14.dp, top = 10.dp))
    SeitenListe(eintraege, 60.dp, modifier, schluessel = "teilwahl", oben = 4.dp, fussHoehe = 82.dp,
        fuss = { s, n, _, zz, v -> Blaetterleiste(s, n, zz, v, mitteText = "Abbrechen", mitteKlein = if (n > 1) "Seite ${s + 1}/$n" else null, onMitte = abbrechen) }
    ) { _, a ->
        Row(
            Modifier.padding(horizontal = 10.dp).fillMaxWidth().height(58.dp).tipp { waehlen(a) }.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AbschnittPille(if (a == Ablauf.INSTRUMENTAL) "Instrumental" else abschnitte[a].name, hoehe = 38.dp, groesse = 15.sp, breite = 128.dp)
            T(if (a == Ablauf.INSTRUMENTAL) "Solo, Zwischenspiel, ohne Gesang" else abschnitte[a].zeilen.firstOrNull { it.isNotBlank() } ?: "", textStil(17.sp, gewicht = FontWeight.Medium), Modifier.weight(1f), zeilen = 1)
        }
        Linie(Modifier.padding(horizontal = 10.dp))
    }
}

/** Der Ablauf als Liste mit Zeiten (ohne Scrollen, Seiten mit den Lautstärketasten). */
@Composable
private fun AblaufListe(abschnitte: List<Abschnitt>, ablauf: List<Teil>, modifier: Modifier) {
    SeitenListe(ablauf, 46.dp, modifier.padding(horizontal = 14.dp), schluessel = "ablauf", oben = 4.dp, fussHoehe = 50.dp,
        fuss = { s, n, _, zz, v ->
            if (n > 1) Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                SymbolKnopf(Sym.LINKS, "Vorige Seite", Modifier.width(56.dp), hoehe = 40.dp, groesse = 16.dp, aktiv = s > 0, onClick = zz)
                T("${ablauf.size} Teile · Seite ${s + 1}/$n", textStil(15.sp, gewicht = FontWeight.Bold), Modifier.weight(1f), mitte = true)
                SymbolKnopf(Sym.RECHTS, "Nächste Seite", Modifier.width(56.dp), hoehe = 40.dp, groesse = 16.dp, aktiv = s < n - 1, onClick = v)
            }
        }
    ) { i, t ->
        Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            T("${i + 1}", titelStil(18.sp), Modifier.width(26.dp), zeilen = 1)
            Box(Modifier.weight(1f)) { AbschnittPille(Ablauf.name(abschnitte, t), hoehe = 32.dp, groesse = 15.sp) }
            val n = Zeitplan.zeilen(abschnitte, t).size
            if (t.eingemessen && t.zeilenMs.size > 1) T("${t.zeilenMs.size}/$n Z.", textStil(14.sp, gewicht = FontWeight.Medium), zeilen = 1)
            T(if (t.eingemessen) zeit(t.startMs) else "–", titelStil(18.sp, gewicht = FontWeight.SemiBold), zeilen = 1)
        }
    }
}
