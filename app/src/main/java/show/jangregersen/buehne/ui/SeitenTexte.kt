package show.jangregersen.buehne.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import kotlinx.coroutines.launch
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import show.jangregersen.buehne.App
import show.jangregersen.buehne.daten.Song
import show.jangregersen.buehne.daten.songAendern
import show.jangregersen.buehne.lyrics.Abschnitte
import show.jangregersen.buehne.lyrics.Ablauf
import show.jangregersen.buehne.lyrics.Teil
import show.jangregersen.buehne.lyrics.Uebernahme
import show.jangregersen.buehne.netz.Lrclib

// ── Etikett eines Abschnitts (auch auf der Bühne) ────────────────────────────

/**
 * Jede Abschnittsart sieht anders aus, damit man sie aus dem Augenwinkel erkennt:
 * Refrain schwarz gefüllt, Strophe mit Rand, Vorrefrain mit dünnem Rand, Bridge gestrichelt.
 */
@Composable
fun AbschnittPille(name: String, modifier: Modifier = Modifier, hoehe: Dp = 34.dp, groesse: TextUnit = 19.sp, breite: Dp? = null, innen: Dp = 13.dp, sperrung: TextUnit = 1.2.sp) {
    val art = Abschnitte.art(name)
    val radius = hoehe / 2
    val form = RoundedCornerShape(radius)
    val voll = art == Abschnitte.Art.REFRAIN
    val rand = when (art) {
        Abschnitte.Art.REFRAIN -> Modifier.clip(form).background(Schwarz)
        Abschnitte.Art.BRIDGE -> Modifier.gestrichelt(radius, 2.5.dp)
        Abschnitte.Art.VORREFRAIN -> Modifier.border(1.5.dp, Schwarz, form)
        Abschnitte.Art.STROPHE -> Modifier.border(2.5.dp, Schwarz, form)
        Abschnitte.Art.ANDERS -> Modifier.border(2.5.dp, Schwarz, form)
    }
    Box(
        modifier.height(hoehe).then(if (breite != null) Modifier.width(breite) else Modifier).then(rand).padding(horizontal = innen),
        contentAlignment = Alignment.Center
    ) {
        T(name.uppercase(), TextStyle(fontFamily = Halbschmal, fontWeight = FontWeight.Bold, fontSize = groesse, letterSpacing = sperrung, color = if (voll) Weiss else Schwarz), zeilen = 1)
    }
}

// ── Texte holen ──────────────────────────────────────────────────────────────

private enum class Modus { LADEN, FRAGE, LISTE, NICHT, FEHLER, PRUEFEN, FERTIG }

/**
 * Holt Songtexte aus der freien Datenbank, Song für Song: Treffer zeigen, Jan bestätigt mit
 * einem Tipp, danach prüft er die Abschnitte. [ids] = Songs in Reihenfolge, [einzeln] = aus dem
 * Song-Editor für genau einen Song.
 */
@Composable
fun TexteHolenSeite(navi: Navi, ids: List<Long>, einzeln: Boolean) {
    val b by App.ablage.stand.collectAsState()
    var index by remember { mutableIntStateOf(0) }
    var modus by remember { mutableStateOf(Modus.LADEN) }
    var treffer by remember { mutableStateOf<List<Lrclib.Treffer>>(emptyList()) }
    var gewaehlt by remember { mutableIntStateOf(0) }
    var suchwort by remember { mutableStateOf("") }
    var anfrage by remember { mutableIntStateOf(0) }
    var fehler by remember { mutableStateOf("") }
    val uebernommen = remember { mutableStateListOf<Long>() }
    val offen = remember { mutableStateListOf<Pair<Long, String>>() }

    val song = b.song(ids.getOrNull(index))
    fun weiter() {
        if (index + 1 >= ids.size) { if (einzeln) navi.zurueck() else modus = Modus.FERTIG }
        else { index++; suchwort = ""; anfrage++ }
    }
    fun ueberspringen(grund: String) { song?.let { s -> offen.removeAll { it.first == s.id }; offen.add(s.id to grund) }; weiter() }

    LaunchedEffect(index, anfrage) {
        val s = b.song(ids.getOrNull(index)) ?: return@LaunchedEffect
        if (modus == Modus.FERTIG) return@LaunchedEffect
        modus = Modus.LADEN
        try {
            val l = Lrclib.suchen(s.titel, suchwort.ifBlank { s.interpret }, s.dauerMs).filter { Uebernahme.vorschau(it) != null }
            treffer = l; gewaehlt = 0
            modus = if (l.isEmpty()) Modus.NICHT else if (suchwort.isNotBlank()) Modus.LISTE else Modus.FRAGE
        } catch (e: Lrclib.Fehler) { fehler = e.meldung; modus = Modus.FEHLER }
    }

    if (modus == Modus.FERTIG || song == null) { FertigAnsicht(navi, uebernommen.size, offen, b); return }

    Lautstaerketasten { }
    Column(Modifier.fillMaxSize()) {
        KopfBand(
            if (modus == Modus.PRUEFEN) "Teile & Ablauf prüfen" else if (einzeln) "Text suchen" else "Texte holen · ${index + 1} / ${ids.size}",
            song.titel,
            rechts = {
                if (modus == Modus.PRUEFEN) WeisserKnopfImBand("Fertig") { weiter() }
                else WeisserKnopfImBand(if (einzeln) "Abbrechen" else "Ende") { if (einzeln) navi.zurueck() else modus = Modus.FERTIG }
            }
        )
        if (!einzeln && modus != Modus.PRUEFEN) {
            Box(Modifier.padding(start = 14.dp, end = 14.dp, top = 8.dp)) { Fortschritt(index, ids.size) }
        }
        when (modus) {
            Modus.LADEN -> Mitte {
                Etikett("Suche läuft")
                Abstand(6.dp)
                T("Frage die Songtext-Datenbank nach „${Lrclib.titelSauber(song.titel)}“ …", textStil(20.sp))
            }
            Modus.FEHLER -> Mitte {
                T("Das hat nicht geklappt", titelStil(34.sp))
                Abstand(10.dp)
                T(fehler, textStil(20.sp, gewicht = FontWeight.Medium))
                Spacer(Modifier.weight(1f))
                TextKnopf("Noch mal versuchen", Modifier.fillMaxWidth(), Art.VOLL, 60.dp, 20.sp) { anfrage++ }
                Abstand(10.dp)
                TextKnopf(if (einzeln) "Zurück" else "Überspringen", Modifier.fillMaxWidth()) { if (einzeln) navi.zurueck() else ueberspringen("nicht erreicht") }
            }
            Modus.FRAGE -> {
                val t = treffer.getOrNull(gewaehlt)
                if (t == null) { modus = Modus.NICHT; return@Column }
                FrageAnsicht(song, t, einzeln,
                    ja = {
                        val neu = Uebernahme.uebernehmen(song, t)
                        if (neu != null) {
                            App.ablage.aendern { it.songAendern(song.id) { neu } }
                            uebernommen.add(song.id); offen.removeAll { it.first == song.id }
                            modus = Modus.PRUEFEN
                        }
                    },
                    anderer = { modus = Modus.LISTE },
                    ueberspringen = { if (einzeln) navi.zurueck() else ueberspringen("übersprungen") })
            }
            Modus.LISTE -> ListeAnsicht(song, treffer, suchwort,
                suchen = { w -> suchwort = w; anfrage++ },
                waehlen = { i -> gewaehlt = i; modus = Modus.FRAGE },
                ueberspringen = { if (einzeln) navi.zurueck() else ueberspringen("übersprungen") })
            Modus.NICHT -> Mitte {
                T("Nicht gefunden", titelStil(40.sp))
                Abstand(10.dp)
                T(if (suchwort.isBlank()) "Die Songtext-Datenbank kennt diesen Titel so nicht. Mit einem Suchwort klappt es oft doch, zum Beispiel mit dem Interpreten."
                  else "Auch mit „$suchwort“ nichts gefunden. Vielleicht anders geschrieben?", textStil(20.sp, gewicht = FontWeight.Medium))
                Abstand(14.dp)
                Row(Modifier.fillMaxWidth().border(2.5.dp, Schwarz, RoundedCornerShape(14.dp)).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    T("Sonst später im Song unter Lyrics mit „Aus PDF“, zum Beispiel ein PDF von Ultimate Guitar.", textStil(17.sp, gewicht = FontWeight.Medium))
                }
                Spacer(Modifier.weight(1f))
                TextKnopf("Suchwort eingeben", Modifier.fillMaxWidth(), Art.VOLL, 60.dp, 20.sp) { treffer = emptyList(); modus = Modus.LISTE }
                Abstand(10.dp)
                TextKnopf(if (einzeln) "Zurück" else "Überspringen", Modifier.fillMaxWidth()) { if (einzeln) navi.zurueck() else ueberspringen("nicht gefunden") }
            }
            Modus.PRUEFEN -> TeileUndAblauf(song)
            Modus.FERTIG -> {}
        }
    }
}

@Composable
private fun ColumnScope.Mitte(inhalt: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.weight(1f).fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 24.dp, bottom = 14.dp), content = inhalt)
}

@Composable
private fun Fortschritt(index: Int, anzahl: Int) {
    if (anzahl <= 24) Kaestchen(anzahl, index, Schwarz, Modifier.fillMaxWidth(), 6.dp)
    else Row(Modifier.fillMaxWidth().height(6.dp).border(1.5.dp, Schwarz, RoundedCornerShape(3.dp))) {
        if (index > 0) Box(Modifier.weight(index.toFloat()).height(6.dp).background(Schwarz))
        Box(Modifier.weight((anzahl - index).toFloat().coerceAtLeast(0.01f)))
    }
}

@Composable
private fun ColumnScope.FrageAnsicht(song: Song, t: Lrclib.Treffer, einzeln: Boolean, ja: () -> Unit, anderer: () -> Unit, ueberspringen: () -> Unit) {
    val v = remember(t) { Uebernahme.vorschau(t) }
    Column(Modifier.weight(1f).fillMaxWidth().clipToBounds().padding(start = 14.dp, end = 14.dp, top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Etikett("Ist es dieser?")
        Column(Modifier.fillMaxWidth().border(2.5.dp, Schwarz, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            T(t.interpret, titelStil(34.sp), zeilen = 2)
            T(t.titel, textStil(21.sp), zeilen = 2)
            Linie(dicke = 2.dp)
            T("„${v?.ersteZeile ?: ""}“", textStil(19.sp, gewicht = FontWeight.Medium).copy(fontStyle = FontStyle.Italic), zeilen = 2)
            if (v != null) T("${v.abschnitte.size} ${if (v.abschnitte.size == 1) "Abschnitt" else "Abschnitte"}" + if (v.refrain) " · Refrain erkannt" else "", textStil(17.sp, gewicht = FontWeight.Medium), zeilen = 1)
        }
        val gleich = song.dauerMs > 0 && t.dauerMs > 0 && kotlin.math.abs(song.dauerMs - t.dauerMs) <= 3000
        // Längen-Vergleich in einer Zeile; bei gleicher Länge ein schwarzes Kästchen (dann blättert
        // die Bühne ohne Einmessen von selbst).
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            T("Original ${zeit(t.dauerMs)}", textStil(17.sp, gewicht = FontWeight.Medium), zeilen = 1)
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                if (gleich) Box(Modifier.clip(RoundedCornerShape(8.dp)).background(Schwarz).padding(horizontal = 8.dp, vertical = 2.dp)) {
                    T("gleich lang", textStil(15.sp, Weiss, FontWeight.Bold), zeilen = 1)
                }
            }
            T("dein Track ${zeit(song.dauerMs)}", textStil(17.sp, gewicht = FontWeight.Medium), zeilen = 1)
        }
    }
    Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TextKnopf(if (einzeln && song.lyrics.isNotBlank()) "Ja, alten Text ersetzen" else "Ja, übernehmen", Modifier.fillMaxWidth(), Art.VOLL, 64.dp, 22.sp, onClick = ja)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TextKnopf("Anderer", Modifier.weight(1f), hoehe = 56.dp, groesse = 19.sp, onClick = anderer)
            TextKnopf(if (einzeln) "Abbrechen" else "Überspringen", Modifier.weight(1f), hoehe = 56.dp, groesse = 19.sp, onClick = ueberspringen)
        }
    }
}

@Composable
private fun ColumnScope.ListeAnsicht(song: Song, treffer: List<Lrclib.Treffer>, suchwort: String, suchen: (String) -> Unit, waehlen: (Int) -> Unit, ueberspringen: () -> Unit) {
    var text by remember(suchwort) { mutableStateOf(suchwort) }
    val fokus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (treffer.isEmpty()) runCatching { fokus.requestFocus() } }
    Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 10.dp)) {
        Etikett("Suchwort dazu, z. B. der Interpret")
        Abstand(6.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).height(52.dp).border(2.5.dp, Schwarz, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                if (text.isEmpty()) T("z. B. Gnarls Barkley", textStil(20.sp, gewicht = FontWeight.Medium).copy(color = Schwarz.copy(alpha = 0.45f)), zeilen = 1)
                BasicTextField(
                    value = text, onValueChange = { text = it }, singleLine = true, textStyle = textStil(21.sp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { suchen(text.trim()) }),
                    modifier = Modifier.fillMaxWidth().focusRequester(fokus)
                )
            }
            TextKnopf("Suchen", Modifier.width(90.dp), Art.VOLL, 52.dp, 18.sp) { suchen(text.trim()) }
        }
    }
    if (treffer.isEmpty()) {
        Column(Modifier.weight(1f).padding(18.dp)) { T("Suchwort eintippen, dann „Suchen“.", textStil(18.sp, gewicht = FontWeight.Medium)) }
    } else {
        Etikett("${treffer.size} Treffer · dein Track ${zeit(song.dauerMs)}", Modifier.padding(start = 18.dp, top = 10.dp))
        SeitenListe(treffer, 62.dp, Modifier.weight(1f), schluessel = "treffer${song.id}$suchwort", oben = 2.dp,
            fuss = { sn, n, _, z, v -> Blaetterleiste(sn, n, z, v, mitteText = "Überspringen", mitteKlein = if (n > 1) "Seite ${sn + 1}/$n" else "keiner passt", onMitte = ueberspringen) }
        ) { i, t ->
            Row(
                Modifier.padding(horizontal = 10.dp).fillMaxWidth().height(61.dp).tipp { waehlen(i) }.padding(start = 8.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    T(t.interpret, textStil(21.sp), zeilen = 1)
                    T("${t.titel} · ${zeit(t.dauerMs)}", textStil(16.sp, gewicht = FontWeight.Medium), zeilen = 1)
                }
                Box(Modifier.size(48.dp).border(2.5.dp, Schwarz, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { Symbol(Sym.HAKEN, Schwarz, 18.dp) }
            }
            Linie(Modifier.padding(horizontal = 10.dp))
        }
    }
    if (treffer.isEmpty()) Box(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) { TextKnopf("Überspringen", Modifier.fillMaxWidth(), onClick = ueberspringen) }
}

@Composable
private fun FertigAnsicht(navi: Navi, anzahl: Int, offen: List<Pair<Long, String>>, b: show.jangregersen.buehne.daten.Bestand) {
    Lautstaerketasten { }
    val noch = offen.mapNotNull { (id, grund) -> b.song(id)?.takeIf { it.lyrics.isBlank() }?.let { it to grund } }
    Column(Modifier.fillMaxSize()) {
        KopfBand("Texte holen", "Fertig")
        Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                T("$anzahl", titelStil(56.sp, gewicht = FontWeight.ExtraBold)); T(if (anzahl == 1) "Text übernommen" else "Texte übernommen", textStil(22.sp), Modifier.padding(bottom = 8.dp))
            }
            if (noch.isNotEmpty()) T("${noch.size} übersprungen oder nicht gefunden", textStil(20.sp, gewicht = FontWeight.Medium))
        }
        if (noch.isNotEmpty()) {
            Etikett("Tipp = Song öffnen und Text später holen", Modifier.padding(start = 18.dp, top = 14.dp))
            SeitenListe(noch, 62.dp, Modifier.weight(1f), schluessel = "texteFertig", fussHoehe = 150.dp, fuss = { s, n, _, z, v ->
                Column {
                    if (n > 1) Blaetterleiste(s, n, z, v)
                    Box(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) { TextKnopf("Zum Archiv", Modifier.fillMaxWidth(), Art.VOLL, 60.dp, 20.sp) { navi.zurueck() } }
                }
            }) { _, (s, grund) ->
                Row(
                    Modifier.padding(horizontal = 10.dp).fillMaxWidth().height(61.dp).tipp { navi.ersetzen(Seite.Editor(s.id, 1)) }.padding(start = 8.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) { T(s.titel, textStil(21.sp), zeilen = 1); T(grund, textStil(16.sp, gewicht = FontWeight.Medium), zeilen = 1) }
                    Symbol(Sym.RECHTS, Schwarz, 18.dp)
                }
                Linie(Modifier.padding(horizontal = 10.dp))
            }
        } else {
            Spacer(Modifier.weight(1f))
            Box(Modifier.padding(14.dp)) { TextKnopf("Zum Archiv", Modifier.fillMaxWidth(), Art.VOLL, 60.dp, 20.sp) { navi.zurueck() } }
        }
    }
}

// ── Teile & Ablauf ───────────────────────────────────────────────────────────

private val ETIKETTEN = listOf("Strophe", "Vorrefrain", "Refrain", "Bridge", "Intro", "Solo", "Outro")
private enum class TeileModus { ORDNEN, ETIKETT, EINFUEGEN }

/**
 * Die Teile in Bühnen-Reihenfolge. Tipp auf einen Teil wählt ihn; unten: verschieben (↑ ↓),
 * doppeln, weglassen, Instrumental oder einen anderen Teil einfügen, Etikett ändern. Der Songtext
 * bleibt dabei als Vorrat erhalten. Ändert sich der Ablauf nach dem Einmessen, gelten die Zeiten
 * ab der geänderten Stelle nicht mehr (dort weiter einmessen).
 */
@Composable
fun TeileUndAblauf(song: Song) {
    val b by App.ablage.stand.collectAsState()
    val aktuell = b.song(song.id) ?: song
    val abschnitte = remember(aktuell.lyrics) { Abschnitte.zerlegen(aktuell.lyrics) }
    val ablauf = remember(aktuell.marken, abschnitte) { Ablauf.von(aktuell.marken, abschnitte) }
    var wahl by remember(song.id) { mutableIntStateOf(0) }
    var modus by remember { mutableStateOf(TeileModus.ORDNEN) }
    if (wahl > ablauf.size - 1) wahl = (ablauf.size - 1).coerceAtLeast(0)
    val gewaehlt = ablauf.getOrNull(wahl)

    // Rückfrage, bevor eingemessene Zeiten verloren gehen: (Text, was bei "Ja" passiert).
    var frage by remember(song.id) { mutableStateOf<Pair<String, () -> Unit>?>(null) }

    fun schreiben(l: List<Teil>, lyrics: String) =
        App.ablage.aendern { it.songAendern(song.id) { s -> s.copy(lyrics = lyrics, marken = Ablauf.schreiben(l), markenVorlaeufig = s.markenVorlaeufig && Ablauf.hatZeiten(l)) } }

    /**
     * Ändert den Ablauf ab Teil [ab]. Die Zeiten dahinter passen dann nicht mehr. Sind dort schon
     * welche eingemessen, wird erst gefragt (kein Tipp löscht Arbeit ohne Rückfrage).
     */
    fun aendern(neu: List<Teil>, ab: Int, lyrics: String = aktuell.lyrics, neueWahl: Int = wahl) {
        val verloren = ablauf.drop(ab).count { it.eingemessen }
        if (verloren == 0) { schreiben(neu, lyrics); wahl = neueWahl; return }
        frage = "Das löscht die Zeiten ab Teil ${ab + 1} ($verloren eingemessen). Trotzdem ändern?" to {
            schreiben(Ablauf.abHierOhneZeit(neu, ab), lyrics); wahl = neueWahl
        }
    }
    fun einfuegen(t: Teil) { val l = ablauf.toMutableList(); l.add(wahl + 1, t); aendern(l, wahl + 1, neueWahl = wahl + 1) }

    Column(Modifier.fillMaxSize()) {
        if (modus == TeileModus.EINFUEGEN) {
            TeilWahl(abschnitte, Modifier.weight(1f), frage = "Welchen Teil einfügen? Er kommt nach Teil ${wahl + 1}.", abbrechen = { modus = TeileModus.ORDNEN }) { a ->
                einfuegen(Teil(a)); modus = TeileModus.ORDNEN
            }
            return@Column
        }
        T(if (modus == TeileModus.ETIKETT) "Etikett für den gewählten Teil. Danach springt die Auswahl weiter." else "Reihenfolge auf der Bühne. Teil antippen, dann unten wählen, was passieren soll.",
            textStil(16.sp, gewicht = FontWeight.Medium), Modifier.padding(start = 18.dp, end = 14.dp, top = 8.dp, bottom = 2.dp))
        SeitenListe(ablauf, 54.dp, Modifier.weight(1f), schluessel = "teile${song.id}", zeige = remember(wahl) { Zeige(wahl, nurWennVerdeckt = true) }, fussHoehe = if (modus == TeileModus.ETIKETT) 176.dp else 150.dp, oben = 2.dp,
            fuss = { s, n, _, z, v ->
                Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Bei einer Rückfrage weicht diese Zeile, damit Frage und Knöpfe in den Fuß passen.
                    if (frage == null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Etikett(if (gewaehlt != null) "Teil ${wahl + 1}: ${Ablauf.name(abschnitte, gewaehlt)}" else "", Modifier.weight(1f))
                        if (n > 1) {
                            SymbolKnopf(Sym.LINKS, "Vorige Seite", Modifier.width(46.dp), hoehe = 34.dp, groesse = 15.dp, aktiv = s > 0, onClick = z)
                            T("${s + 1}/$n", textStil(15.sp, gewicht = FontWeight.Bold))
                            SymbolKnopf(Sym.RECHTS, "Nächste Seite", Modifier.width(46.dp), hoehe = 34.dp, groesse = 15.dp, aktiv = s < n - 1, onClick = v)
                        }
                    }
                    val f = frage
                    if (f != null) {
                        T(f.first, textStil(17.sp, gewicht = FontWeight.Bold), Modifier.padding(horizontal = 4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextKnopf("Abbrechen", Modifier.weight(1f), hoehe = 50.dp, groesse = 17.sp) { frage = null }
                            TextKnopf("Ja, ändern", Modifier.weight(1f), Art.VOLL, 50.dp, 17.sp) { frage = null; f.second() }
                        }
                    } else if (modus == TeileModus.ETIKETT) {
                        listOf(ETIKETTEN.take(3), ETIKETTEN.drop(3)).forEach { reihe ->
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                reihe.forEach { e ->
                                    Box(Modifier.weight(1f).tipp {
                                        val a = gewaehlt?.abschnitt
                                        if (a != null && a != Ablauf.INSTRUMENTAL) {
                                            App.ablage.aendern { it.songAendern(song.id) { s -> s.copy(lyrics = Abschnitte.umbenennen(s.lyrics, a, e)) } }
                                            // Weiter zum nächsten Teil mit Text (Instrumental hat kein Etikett).
                                            (wahl + 1 until ablauf.size).firstOrNull { ablauf[it].abschnitt != Ablauf.INSTRUMENTAL }?.let { wahl = it }
                                        }
                                    }) { AbschnittPille(e, Modifier.fillMaxWidth(), hoehe = 40.dp, groesse = 15.sp, innen = 4.dp, sperrung = 0.5.sp) }
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Nur wenn der Teil darüber im Text direkt davor steht (dann ist "voriger" eindeutig).
                            val a = gewaehlt?.abschnitt ?: -1
                            val verbindbar = a >= 1 && ablauf.getOrNull(wahl - 1)?.abschnitt == a - 1
                            TextKnopf("Mit vorigem verbinden", Modifier.weight(1.6f), Art.GESTRICHELT, 42.dp, 15.sp, aktiv = verbindbar) {
                                // Betroffen ist jeder Teil, der auf einen der beiden Abschnitte zeigt.
                                val ab = ablauf.indexOfFirst { it.abschnitt == a || it.abschnitt == a - 1 }
                                aendern(Ablauf.nachZusammenlegen(ablauf, a), ab, Abschnitte.mitVorigem(aktuell.lyrics, a), neueWahl = wahl - 1)
                            }
                            TextKnopf("Fertig", Modifier.weight(1f), Art.VOLL, 42.dp, 16.sp) { modus = TeileModus.ORDNEN }
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            SymbolKnopf(Sym.HOCH, "Nach oben", Modifier.weight(1f), hoehe = 44.dp, groesse = 18.dp, aktiv = wahl > 0) {
                                val l = ablauf.toMutableList(); val x = l[wahl]; l[wahl] = l[wahl - 1]; l[wahl - 1] = x
                                aendern(l, wahl - 1, neueWahl = wahl - 1)
                            }
                            SymbolKnopf(Sym.RUNTER, "Nach unten", Modifier.weight(1f), hoehe = 44.dp, groesse = 18.dp, aktiv = wahl < ablauf.size - 1) {
                                val l = ablauf.toMutableList(); val x = l[wahl]; l[wahl] = l[wahl + 1]; l[wahl + 1] = x
                                aendern(l, wahl, neueWahl = wahl + 1)
                            }
                            TextKnopf("Doppeln", Modifier.weight(1.3f), hoehe = 44.dp, groesse = 15.sp, aktiv = gewaehlt != null) { gewaehlt?.let { einfuegen(Teil(it.abschnitt)) } }
                            TextKnopf("Weg", Modifier.weight(1f), Art.GESTRICHELT, 44.dp, 15.sp, aktiv = ablauf.size > 1) {
                                // Weglassen ändert die Zeiten der anderen Teile nicht (der Track bleibt gleich).
                                val ohne = ablauf.filterIndexed { i, _ -> i != wahl }
                                if (gewaehlt?.eingemessen == true) frage = "Teil ${wahl + 1} ist eingemessen. Weglassen löscht seine Zeiten." to { schreiben(ohne, aktuell.lyrics) }
                                else schreiben(ohne, aktuell.lyrics)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextKnopf("Etikett", Modifier.weight(1f), hoehe = 44.dp, groesse = 15.sp, aktiv = gewaehlt != null && gewaehlt.abschnitt != Ablauf.INSTRUMENTAL) { modus = TeileModus.ETIKETT }
                            TextKnopf("+ Instrumental", Modifier.weight(1.3f), hoehe = 44.dp, groesse = 15.sp) { einfuegen(Teil(Ablauf.INSTRUMENTAL)) }
                            TextKnopf("+ Teil", Modifier.weight(0.9f), hoehe = 44.dp, groesse = 15.sp) { modus = TeileModus.EINFUEGEN }
                        }
                    }
                }
            }
        ) { i, t ->
            val an = i == wahl
            Row(
                Modifier.padding(horizontal = 10.dp).fillMaxWidth().height(52.dp).clip(RoundedCornerShape(12.dp))
                    .then(if (an) Modifier.border(3.dp, Schwarz, RoundedCornerShape(12.dp)) else Modifier)
                    .tipp(aktiv = frage == null) { wahl = i }.padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                T("${i + 1}", titelStil(17.sp), Modifier.width(22.dp), zeilen = 1)
                AbschnittPille(Ablauf.name(abschnitte, t), hoehe = 36.dp, groesse = 15.sp, breite = 118.dp)
                Column(Modifier.weight(1f)) {
                    T(if (t.abschnitt == Ablauf.INSTRUMENTAL) "ohne Gesang" else abschnitte[t.abschnitt].zeilen.firstOrNull { it.isNotBlank() } ?: "(ohne Text)", textStil(16.sp, gewicht = FontWeight.Medium), zeilen = 1)
                    T(if (t.eingemessen) "eingemessen · ${zeit(t.startMs)}" else "${show.jangregersen.buehne.lyrics.Zeitplan.zeilen(abschnitte, t).size} Zeilen", textStil(13.sp, gewicht = FontWeight.Medium), zeilen = 1)
                }
            }
            Abstand(2.dp)
        }
    }
}

/**
 * "Teile & Ablauf" am Mudita: nur ansehen, mit vollem Text, in Bühnen-Reihenfolge (Jans
 * Entscheidung 13.11.: Text und Teile entstehen nur noch in der Werkstatt am Laptop).
 * Die Lautstärketasten blättern.
 */
@Composable
fun PruefenSeite(navi: Navi, songId: Long) {
    val b by App.ablage.stand.collectAsState()
    val song = b.song(songId) ?: run { LaunchedEffect(Unit) { navi.zurueck() }; return }
    val abschnitte = remember(song.lyrics) { Abschnitte.zerlegen(song.lyrics) }
    val ablauf = remember(song.marken, abschnitte) { Ablauf.von(song.marken, abschnitte) }
    val scroll = androidx.compose.foundation.rememberScrollState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    Lautstaerketasten { vor -> scope.launch { scroll.scrollTo((scroll.value + (if (vor) 1 else -1) * (scroll.viewportSize * 0.85f).toInt()).coerceIn(0, scroll.maxValue)) } }
    Column(Modifier.fillMaxSize()) {
        KopfBand("Teile & Ablauf", song.titel, rechts = { WeisserKnopfImBand("Fertig") { navi.zurueck() } })
        Column(Modifier.weight(1f).verticalScroll(scroll).padding(horizontal = 14.dp, vertical = 10.dp)) {
            T("Ändern geht in der TrikTrak Werkstatt am Laptop, danach dort „Zum Mudita senden“.", textStil(15.sp))
            Abstand(8.dp)
            if (abschnitte.isEmpty()) T("Noch kein Text.", textStil(20.sp))
            ablauf.forEachIndexed { i, t ->
                Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    T("${i + 1}", titelStil(19.sp), Modifier.width(26.dp), zeilen = 1)
                    AbschnittPille(Ablauf.name(abschnitte, t), hoehe = 36.dp, groesse = 16.sp)
                    Spacer(Modifier.weight(1f))
                    T(if (t.eingemessen) "eingemessen · ${zeit(t.startMs)}" else "nicht eingemessen", textStil(14.sp, gewicht = FontWeight.Medium), zeilen = 1)
                }
                if (t.abschnitt == Ablauf.INSTRUMENTAL) T("ohne Gesang", textStil(19.sp, gewicht = FontWeight.Medium), Modifier.padding(start = 34.dp))
                else abschnitte[t.abschnitt].zeilen.filter { it.isNotBlank() }.forEach { z ->
                    T(z, textStil(19.sp, gewicht = FontWeight.Medium), Modifier.padding(start = 34.dp))
                }
                Abstand(6.dp)
                Box(Modifier.fillMaxWidth().height(1.5.dp).background(Schwarz))
            }
            Abstand(40.dp)
        }
    }
}
