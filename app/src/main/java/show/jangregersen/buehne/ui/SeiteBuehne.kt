package show.jangregersen.buehne.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import show.jangregersen.buehne.lyrics.Zeitplan
import show.jangregersen.buehne.lyrics.Ablauf
import show.jangregersen.buehne.lyrics.Abschnitt
import show.jangregersen.buehne.lyrics.Teil
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import show.jangregersen.buehne.App
import show.jangregersen.buehne.daten.Ende
import show.jangregersen.buehne.daten.Song
import show.jangregersen.buehne.daten.pauseDanach
import show.jangregersen.buehne.daten.ersterOffener
import show.jangregersen.buehne.daten.naechsterNach
import show.jangregersen.buehne.daten.wasKommt
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import show.jangregersen.buehne.lyrics.Abschnitte
import show.jangregersen.buehne.lyrics.LyricsSeite

/** Ein Song auf der Bühne zum Ausprobieren (ohne Set): Lyrics, Blättern und Zeilen-Keil prüfen. */
@Composable
fun ProbeBuehneSeite(navi: Navi, songId: Long) {
    // Erst starten, wenn die vorige Seite weg ist (die beendet beim Verlassen ihre eigene Probe).
    LaunchedEffect(songId) { App.buehne.probe(songId) }
    DisposableEffect(Unit) { onDispose { App.buehne.probeEnde() } }
    BuehneSeite(navi)
}

@Composable
fun BuehneSeite(navi: Navi) {
    val b by App.ablage.stand.collectAsState()
    val z by App.buehne.zustand.collectAsState()
    val song = b.song(z.songId)
    val set = b.set(z.setId)

    val pause = z.pause
    if (pause != null && song != null) { AnsageAnsicht(navi, pause, song); return }

    if (song == null) {
        Column(Modifier.fillMaxSize()) {
            KopfBand("Bühne", "Nichts bereit")
            z.setDurch?.let { SetEndeHinweis(it) }
            Column(Modifier.padding(18.dp).weight(1f)) {
                T("Wähle ein Set, dann liegt hier der erste Song bereit.", textStil(18.sp))
            }
            Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextKnopf("Setliste", Modifier.weight(1f), symbol = Sym.LISTE) { navi.zurSetliste() }
                TextKnopf("Gigs", Modifier.weight(1f)) { navi.stapel.clear(); navi.stapel.add(Seite.Gigs) }
            }
        }
        return
    }

    val platz = set?.eintraege?.indexOfFirst { it.songId == song.id }?.let { if (it >= 0) "${it + 1} / ${set.eintraege.size}" else null }
    val naechsterEintrag = set?.naechsterNach(song.id)
    // Gewähltes Folge-Set (B: Set-Wechsel nur nach dem laufenden Song): "Danach" zeigt dessen ersten Song.
    val folgeSet = b.set(z.setDanach)
    val naechster = if (folgeSet != null) b.song(folgeSet.ersterOffener()?.songId) else b.song(naechsterEintrag?.songId)
    val endeHier = if (folgeSet != null) Ende.ANHALTEN else set?.eintraege?.firstOrNull { it.songId == song.id }?.ende ?: Ende.ANHALTEN
    val pos by App.buehne.position.collectAsState()
    val angespielt = z.spielt || pos > 0
    // Beenden braucht einen zweiten Tipp (15.11.): Ein Fehltipp neben Play darf den Song nicht abbrechen.
    var bestaetigen by remember(song.id) { mutableStateOf(false) }
    LaunchedEffect(bestaetigen) { if (bestaetigen) { kotlinx.coroutines.delay(4000); bestaetigen = false } }
    if (!angespielt && bestaetigen) bestaetigen = false

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        val zustandWort = if (z.probe) "Probe" else if (z.spielt) "Jetzt" else if (pos > 0) "Angehalten" else "Bereit"
        BuehnenKopf(song, if (z.probe) zustandWort else "$zustandWort${platz?.let { " · $it" } ?: ""}", z.dauerMs, z.spielt)
        z.setDurch?.let { SetEndeHinweis(it) }
        // B21: Solange der Song nur bereitliegt, stehen Kapo und Tonart groß oben (zum Vorbereiten).
        // Beim Start verschwinden sie einmal, danach bleibt die Seite ruhig.
        // Ohne Text stehen Tonart und Kapo ohnehin groß im Inhalt (OhneText), dann nicht doppelt.
        if (!z.spielt && pos == 0L && !z.probe && song.lyrics.isNotBlank()) KapoTonart(song, 30.sp, 10.dp, Modifier.padding(start = 14.dp, end = 14.dp, top = 10.dp))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (song.lyrics.isNotBlank()) LyricsBereich(song, z.dauerMs)
            else OhneText(song, if (z.probe || folgeSet != null) emptyList() else set?.wasKommt(song.id)?.drop(2).orEmpty(), set?.id)
        }
        if (!z.probe) DanachBlock(naechster, song, endeHier, naechster == null, folgeSet?.name, z.rueckgaengig != null)
        // Drei Knöpfe, immer an derselben Stelle (15.11.): Liste · Play/Pause · Beenden bzw. Überspringen.
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 14.dp, top = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Knopf(Modifier.weight(1f), hoehe = 62.dp, beschreibung = "Zur Setliste", onClick = { if (z.probe) navi.zurueck() else navi.zurSetliste() }) { f ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Symbol(Sym.LISTE, f, 20.dp)
                    T(if (z.probe) "Zurück" else "Liste", textStil(15.sp, f, FontWeight.Bold), zeilen = 1)
                }
            }
            SymbolKnopf(if (z.spielt) Sym.PAUSE else Sym.PLAY, if (z.spielt) "Pause" else "Abspielen", Modifier.weight(1.5f), Art.VOLL, hoehe = 62.dp, groesse = 26.dp) { App.buehne.playPause() }
            when {
                z.probe -> SymbolKnopf(Sym.STOP, "Stopp", Modifier.weight(1f), hoehe = 62.dp, groesse = 20.dp) { App.buehne.stopp() }
                angespielt -> Knopf(Modifier.weight(1f), if (bestaetigen) Art.VOLL else Art.UMRISS, 62.dp, beschreibung = "Song beenden", onClick = {
                    if (bestaetigen) { bestaetigen = false; App.buehne.beenden() } else bestaetigen = true
                }) { f ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Symbol(Sym.STOP, f, 18.dp)
                        T(if (bestaetigen) "Nochmal" else "Beenden", textStil(15.sp, f, FontWeight.Bold), zeilen = 1)
                    }
                }
                else -> Knopf(Modifier.weight(1f), hoehe = 62.dp, aktiv = naechster != null || folgeSet != null, beschreibung = "Überspringen", onClick = { App.buehne.weiter() }) { f ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Symbol(Sym.WEITER, f, 20.dp)
                        T("Weiter", textStil(15.sp, f, FontWeight.Bold), zeilen = 1)
                    }
                }
            }
        }
    }
    if (bestaetigen) {
        val was = when {
            folgeSet != null -> "Dann liegt „${folgeSet.name}“ bereit."
            naechster == null -> "Dann ist das Set zu Ende."
            endeHier == Ende.ANHALTEN -> "Dann liegt „${naechster.titel}“ bereit."
            song.ansageNachher.isNotBlank() || naechster.ansageVorher.isNotBlank() -> "Dann Ansage und „${naechster.titel}“."
            else -> "Dann Pause ${pauseText(song.pauseDanach)} und „${naechster.titel}“."
        }
        Column(Modifier.fillMaxSize().padding(bottom = 84.dp), verticalArrangement = Arrangement.Bottom) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 10.dp).clip(RoundedCornerShape(14.dp)).background(Schwarz).tipp { bestaetigen = false }.padding(14.dp)) {
                T("Nochmal auf „Beenden“ tippen, um den Song zu beenden. $was", textStil(17.sp, Weiss, FontWeight.Bold), zeilen = 3)
            }
        }
    }
    }
}

/**
 * Bühne ohne Text (die meisten Songs): Kapo und Tonart groß wie bisher, darunter, was nach dem
 * "Danach"-Song kommt. Antippen holt einen Song nach vorn (Jans eine Regel, 15.11.).
 */
@Composable
private fun OhneText(song: Song, dann: List<show.jangregersen.buehne.daten.Eintrag>, setId: Long?) {
    val b by App.ablage.stand.collectAsState()
    // Die Lautstärketasten haben hier nichts zu blättern: nicht an Android weitergeben (sonst
    // blitzt der Lautstärkebalken auf und der Pegel springt kurz).
    Lautstaerketasten { }
    Column(Modifier.fillMaxSize().padding(top = 12.dp)) {
        KapoTonart(song, 44.sp, 12.dp, Modifier.padding(horizontal = 14.dp))
        if (dann.isEmpty() || setId == null) return@Column
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(top = 10.dp)) {
            val zeilen = ((maxHeight - 30.dp) / 56.dp).toInt().coerceAtLeast(0)
            if (zeilen == 0) return@BoxWithConstraints
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().height(30.dp).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Etikett("Dann", Modifier.weight(1f))
                    Etikett("Antippen = als Nächstes")
                }
                Linie(Modifier.padding(horizontal = 10.dp))
                val set = b.set(setId)
                dann.take(zeilen).forEach { e ->
                    val s = b.song(e.songId) ?: return@forEach
                    val nr = (set?.eintraege?.indexOfFirst { it.songId == e.songId } ?: -1) + 1
                    Row(
                        Modifier.padding(horizontal = 10.dp).fillMaxWidth().height(55.dp).tipp { App.buehne.vorziehen(setId, s.id) }.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        T(if (nr > 0) "$nr" else "", titelStil(22.sp), Modifier.width(26.dp), zeilen = 1)
                        PassenderTitel(s.titel, { textStil(it) }, 24, 16, Modifier.weight(1f))
                        T(zeit(s.dauerMs), titelStil(20.sp, gewicht = FontWeight.SemiBold), zeilen = 1)
                    }
                    Linie(Modifier.padding(horizontal = 10.dp))
                }
            }
        }
    }
}

/** Eine Größe für alle Songtitel im Bühnenkopf. */
const val TITEL_SP = 34f

@Composable
private fun BuehnenKopf(song: Song, ober: String, dauerMs: Long, spielt: Boolean) {
    val pos by App.buehne.position.collectAsState()
    val dauer = if (dauerMs > 0) dauerMs else song.dauerMs
    val rest = (dauer - pos).coerceAtLeast(0)
    val voll = if (dauer > 0) ((pos * 20) / dauer).toInt().coerceIn(0, 20) else 0
    // Feste Höhe und feste Breite für die Zeit: Der Kopf darf sich beim Herunterzählen nie
    // verändern, sonst baut das E-Ink jede Sekunde den ganzen Balken neu auf.
    val zeitStil = titelStil(38.sp, Weiss).copy(lineHeight = 38.sp, fontFeatureSettings = "tnum")
    val messer = rememberTextMeasurer()
    val zeitBreite = with(LocalDensity.current) {
        val muster = zeit(maxOf(dauer, song.dauerMs)).replace(Regex("[0-9]"), "0")
        messer.measure(muster, zeitStil).size.width.toDp()
    }
    // Titel immer in derselben Größe (Jan, 12.11.: "einheitlich, auf keinen Fall durcheinander").
    // Lange Titel brechen in eine zweite Zeile um statt zu schrumpfen; die Kopfhöhe steht damit
    // pro Song fest. Die Restzeit steht oben rechts in fester Breite.
    Column(Modifier.fillMaxWidth().background(Schwarz).padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Etikett(ober, Modifier.weight(1f).padding(bottom = 4.dp), Weiss)
            Etikett(if (spielt || pos > 0) "noch" else "Länge", Modifier.padding(end = 8.dp, bottom = 4.dp), Weiss)
            Box(Modifier.width(zeitBreite), contentAlignment = Alignment.BottomEnd) {
                T(zeit(if (spielt || pos > 0) rest else dauer), zeitStil, zeilen = 1)
            }
        }
        T(song.titel, titelStil(TITEL_SP.sp, Weiss).copy(lineHeight = (TITEL_SP * 1.02f).sp), zeilen = 2)
        Kaestchen(20, voll, Weiss, hoehe = 6.dp)
    }
}

/** Bleibt stehen, bis wieder gespielt oder ein anderes Set gewählt wird. */
@Composable
private fun SetEndeHinweis(text: String) {
    Row(
        Modifier.padding(start = 14.dp, end = 14.dp, top = 10.dp).fillMaxWidth()
            .border(3.dp, Schwarz, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Symbol(Sym.HAKEN, Schwarz, 24.dp)
        Column(Modifier.weight(1f)) {
            Etikett("Set-Ende")
            T(text, textStil(18.sp, gewicht = FontWeight.Bold), zeilen = 2)
        }
    }
}

@Composable
private fun DanachBlock(naechster: Song?, aktuell: Song, ende: Int, setEnde: Boolean, folgeSet: String? = null, rueckgaengig: Boolean = false) {
    // Immer sichtbar, immer an derselben Stelle. Der nächste Titel bekommt die volle Breite und
    // bricht lieber um, als abgeschnitten zu werden (Jan: "wirklich gut lesbar").
    Column(Modifier.padding(start = 18.dp, end = 18.dp, bottom = 6.dp)) {
        Linie(dicke = 2.5.dp)
        // Feste Höhe: Ob rechts der Pausen-Hinweis oder "Rückgängig" steht, nichts verschiebt sich.
        Row(Modifier.fillMaxWidth().padding(top = 5.dp).height(34.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Etikett(if (folgeSet != null) "Danach · $folgeSet" else "Danach", Modifier.weight(1f))
            // Nach dem Antippen eines Songs (8 s): Rückgängig genau dort, wo man das Ergebnis sieht.
            if (rueckgaengig) TextKnopf("Rückgängig", Modifier, Art.VOLL, 34.dp, 16.sp, radius = 10.dp) { App.buehne.rueckgaengig() }
            else if (naechster != null) {
                val (sym, text) = when {
                    ende == Ende.ANHALTEN -> Sym.STOP to "hält an"
                    aktuell.ansageNachher.isNotBlank() -> Sym.MIKRO to "Ansage ${zeit(aktuell.ansageNachherMs)}"
                    naechster.ansageVorher.isNotBlank() -> Sym.MIKRO to "Ansage ${zeit(naechster.ansageVorherMs)}"
                    else -> null to "Pause ${pauseText(aktuell.pauseDanach)}"
                }
                Row(
                    Modifier.border(2.dp, Schwarz, RoundedCornerShape(10.dp)).padding(horizontal = 9.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (sym != null) Symbol(sym, Schwarz, 16.dp)
                    T(text, textStil(16.sp, gewicht = FontWeight.Bold), zeilen = 1)
                }
            }
        }
        T(naechster?.titel ?: if (setEnde) "Set zu Ende" else "–", titelStil(28.sp).copy(lineHeight = 29.sp), Modifier.fillMaxWidth().padding(top = 2.dp), zeilen = 2)
    }
}

// ── Lyrics: abschnittsweise Seiten ───────────────────────────────────────────

/** Breite der Spalte links neben dem Text: Strich (Refrain/Bridge) und Zeilen-Keil. */
private val RINNE = 30.dp

/**
 * Eine Seite = ein Abschnitt (zu lange werden geteilt). Mit Einmess-Punkten blättert die Seite
 * 1,5 s vor dem Abschnitt um, und ein schwarzer Keil links springt Zeile für Zeile im Songtempo
 * mit (E-Ink-tauglich: kein Laufen, nur der Keil ändert sich). Ohne Punkte blättert Jan von Hand
 * (Tipp links/rechts oder Lautstärketasten).
 */
@Composable
private fun LyricsBereich(song: Song, dauerMs: Long) {
    val messer = rememberTextMeasurer()
    val dichte = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize().padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 4.dp)) {
        val textBreitePx = with(dichte) { (maxWidth - RINNE).roundToPx() }
        val hoehePx = with(dichte) { (maxHeight - 40.dp).roundToPx() }
        val abschnitte = remember(song.lyrics) { Abschnitte.zerlegen(song.lyrics) }
        // Seiten in Jans Ablauf (Teile weg, wiederholt, umgestellt, Instrumental).
        val ablauf = remember(song.marken, abschnitte) { Ablauf.von(song.marken, abschnitte) }
        val seiten = remember(abschnitte, ablauf, textBreitePx, hoehePx) {
            seitenBauen(ablauf.map { t -> Abschnitt(Ablauf.name(abschnitte, t), if (t.abschnitt == Ablauf.INSTRUMENTAL) emptyList() else abschnitte[t.abschnitt].zeilen) }, textBreitePx, hoehePx,
                messen = { zeilen, sp -> messer.measure(zeilen.joinToString("\n") { it.ifBlank { " " } }, lyricsStil(sp, true), constraints = Constraints(maxWidth = textBreitePx)).size.height })
        }
        if (seiten.isEmpty()) return@BoxWithConstraints
        val mitZeiten = Ablauf.hatZeiten(ablauf)
        val dauer = if (dauerMs > 0) dauerMs else song.dauerMs
        val starts = remember(song.lyrics, song.marken, song.zeilenZeiten, dauer) {
            Zeitplan.zeilenStarts(abschnitte, ablauf, Zeitplan.zeitenLesen(song.zeilenZeiten), dauer)
        }
        // collectAsState: nur so merkt derivedStateOf, dass sich die Position ändert.
        val position = App.buehne.position.collectAsState()
        // Nur neu zeichnen, wenn Seite oder Zeile wechseln (nicht bei jedem 200-ms-Takt).
        val auto by remember(seiten, starts) { derivedStateOf { Zeitplan.seiteFuer(seiten, ablauf, starts, position.value + Zeitplan.SEITE_VORLAUF_MS, position.value + Zeitplan.ZEILE_VORLAUF_MS) } }
        val zeile by remember(starts) { derivedStateOf { Zeitplan.aktiveZeile(ablauf, starts, position.value + Zeitplan.ZEILE_VORLAUF_MS) } }
        var hand by remember(song.id) { mutableStateOf<Pair<Int, Int>?>(null) } // (Hand-Seite, Auto-Seite beim Blättern)
        val vonHand = hand?.let { (h, a) -> if (a == auto && h != auto) h else null }
        val gezeigt = (vonHand ?: auto).coerceIn(0, seiten.size - 1)
        val blaettern = { d: Int -> hand = (gezeigt + d).coerceIn(0, seiten.size - 1) to auto }
        Lautstaerketasten { v -> blaettern(if (v) 1 else -1) }
        val seite = seiten[gezeigt]
        val naechste = seiten.getOrNull(gezeigt + 1)
        val art = Abschnitte.art(seite.name)

        // Keil: voll = diese Zeile läuft, hohl = kommt gleich (Seite schon umgeblättert).
        val z = zeile
        val keil: Pair<Int, Boolean> = when {
            !mitZeiten || vonHand != null -> -1 to false
            z != null && z.first == seite.abschnitt && z.second in seite.ersteZeile until seite.ersteZeile + seite.textZeilen -> (z.second - seite.ersteZeile) to true
            // Teil hat begonnen (Vorspiel) oder Seite schon umgeblättert: erste Zeile kommt gleich.
            (starts.getOrNull(seite.abschnitt)?.size ?: 0) > seite.ersteZeile -> 0 to false
            else -> -1 to false
        }

        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().height(40.dp).padding(start = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AbschnittPille(seite.name + if (seite.teile > 1) " ${seite.teil}/${seite.teile}" else "")
                Spacer(Modifier.weight(1f))
                when {
                    naechste == null -> T("Schluss", textStil(17.sp, gewicht = FontWeight.Bold), zeilen = 1)
                    naechste.abschnitt == seite.abschnitt -> T("weiter ›", textStil(17.sp, gewicht = FontWeight.Bold), zeilen = 1)
                    else -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        T("dann", textStil(16.sp, gewicht = FontWeight.Medium), zeilen = 1)
                        T(naechste.name.uppercase(), etikettStil(), zeilen = 1)
                    }
                }
            }
            // Kein Abstand unter der Etiketten-Zeile: jede Bildschirmzeile Text zählt (Jan: "zu wenig Text pro Seite").
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.height(IntrinsicSize.Min)) {
                        // Strich links: Refrain dick, Bridge gestrichelt.
                        Box(Modifier.width(6.dp).fillMaxHeight()) {
                            when (art) {
                                Abschnitte.Art.REFRAIN -> Box(Modifier.width(6.dp).fillMaxHeight().background(Schwarz))
                                Abschnitte.Art.BRIDGE -> Box(Modifier.width(6.dp).fillMaxHeight().strichelLinie())
                                else -> {}
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            var nr = 0
                            seite.zeilen.forEach { zl ->
                                if (zl.isBlank()) Spacer(Modifier.height(10.dp))
                                else {
                                    val i = nr++
                                    // Die laufende Zeile bekommt zusätzlich einen Rahmen (samt Umbruch): aus 60–80 cm
                                    // ist der Keil allein zu klein. Der Rahmen wird nur gezeichnet, nichts verschiebt sich.
                                    val laeuft = keil.second && i == keil.first
                                    Row(if (laeuft) Modifier.drawBehind {
                                        val r = 8.dp.toPx()
                                        drawRoundRect(Schwarz, topLeft = androidx.compose.ui.geometry.Offset(-2.dp.toPx(), 0f), size = androidx.compose.ui.geometry.Size(size.width + 10.dp.toPx(), size.height),
                                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r), style = Stroke(width = 2.5.dp.toPx()))
                                    } else Modifier) {
                                        Box(Modifier.width(RINNE - 6.dp).padding(end = 4.dp).height(with(dichte) { (seite.schriftSp * ZEILEN_FAKTOR).sp.toDp() }), contentAlignment = Alignment.Center) {
                                            if (i == keil.first) Keil(keil.second)
                                        }
                                        T(zl, lyricsStil(seite.schriftSp, art == Abschnitte.Art.REFRAIN))
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    // Vorschau: auf der letzten Zeile der Seite kommt unten die erste Zeile der nächsten.
                    // Auf einer Seite ohne Gesang (Instrumental) steht sie immer da: Womit geht es weiter?
                    val ohneGesang = ablauf.getOrNull(seite.abschnitt)?.let { Zeitplan.zeilen(abschnitte, it).isEmpty() } == true
                    val letzte = ohneGesang || (keil.second && keil.first == seite.textZeilen - 1)
                    val vorschau = naechste?.zeilen?.firstOrNull { it.isNotBlank() }
                    val platz = hoehePx - seite.hoehePx
                    if (letzte && naechste != null && vorschau != null && platz > with(dichte) { 58.dp.roundToPx() }) {
                        Row(Modifier.fillMaxWidth().padding(start = 6.dp, bottom = 4.dp).border(2.dp, Schwarz, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            T(if (naechste.abschnitt == seite.abschnitt) "weiter" else naechste.name.uppercase(), etikettStil(), zeilen = 1)
                            // Auf der Instrumental-Seite ist Platz: die nächste Zeile darf dort zweizeilig stehen.
                            T(vorschau, textStil(24.sp, gewicht = FontWeight.SemiBold), Modifier.weight(1f), zeilen = if (ohneGesang) 2 else 1)
                        }
                    }
                }
                Row(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f).fillMaxHeight().tipp { blaettern(-1) })
                    Box(Modifier.weight(1f).fillMaxHeight().tipp { blaettern(1) })
                }
            }
        }
    }
}

@Composable
private fun Keil(voll: Boolean) {
    Canvas(Modifier.size(width = 19.dp, height = 27.dp)) {
        val p = Path().apply { moveTo(0f, 0f); lineTo(size.width, size.height / 2); lineTo(0f, size.height); close() }
        if (voll) drawPath(p, Schwarz) else drawPath(p, Schwarz, style = Stroke(width = 2.5.dp.toPx()))
    }
}

private fun Modifier.strichelLinie(): Modifier = this.drawBehind {
    val dicke = 3.dp.toPx(); val stueck = 8.dp.toPx(); val luecke = 6.dp.toPx()
    var y = 0f
    while (y < size.height) {
        drawRect(Schwarz, topLeft = androidx.compose.ui.geometry.Offset(0f, y), size = androidx.compose.ui.geometry.Size(dicke, minOf(stueck, size.height - y)))
        y += stueck + luecke
    }
}

private fun lyricsStil(sp: Float, fett: Boolean = false) = TextStyle(fontFamily = Schmal, fontWeight = if (fett) FontWeight.Bold else FontWeight.SemiBold, fontSize = sp.sp, lineHeight = (sp * ZEILEN_FAKTOR).sp, color = Schwarz, textIndent = androidx.compose.ui.text.style.TextIndent(restLine = (sp * 0.6f).sp))

/** Schrift der Bühne. Jan wählte am 13.11. Probeseite B (40), am Ständer war ihm 40 dann zu groß für schnelle Songs: 34 "erstmal gut" (vorher 24–31). */
private const val BUEHNE_SP = 34f
/** Zeilenabstand der Lyrics (mal Schriftgröße). */
private const val ZEILEN_FAKTOR = 1.12f

/**
 * Baut die Bildschirmseiten: eine Schriftgröße für alle Songs (34, damit nichts springt). Lange
 * Zeilen brechen um (Rest eingerückt). Ein Abschnitt, der nicht auf eine Seite passt, wird auf
 * so wenige Seiten wie nötig verteilt, möglichst gleich voll. Nur wenn eine einzelne Zeile nicht
 * einmal allein auf die Seite passt, wird die Schrift kleiner.
 */
private fun seitenBauen(abschnitte: List<Abschnitt>, breite: Int, hoehe: Int, messen: (List<String>, Float) -> Int): List<LyricsSeite> {
    if (abschnitte.isEmpty()) return emptyList()
    val alle = abschnitte.flatMap { it.zeilen }.filter { it.isNotBlank() }
    val schrift = listOf(BUEHNE_SP, 30f, 28f).firstOrNull { sp -> alle.all { messen(listOf(it), sp) <= hoehe } } ?: 24f
    val seiten = mutableListOf<LyricsSeite>()
    abschnitte.forEachIndexed { ai, a ->
        val teile = show.jangregersen.buehne.lyrics.seitenTeilen(a.zeilen) { messen(it, schrift) <= hoehe }
        var vorher = 0
        teile.forEachIndexed { ti, t ->
            val zeilen = if (t.isEmpty()) listOf("— instrumental —") else t
            seiten.add(LyricsSeite(ai, a.name, ti + 1, teile.size, zeilen, schrift, ersteZeile = vorher, hoehePx = messen(zeilen, schrift)))
            vorher += t.count { it.isNotBlank() }
        }
    }
    return seiten
}

// ── Ansage / Pause zwischen den Songs ────────────────────────────────────────

@Composable
private fun AnsageAnsicht(navi: Navi, pause: show.jangregersen.buehne.spiel.Buehne.Pause, kommt: Song) {
    val b = App.ablage.jetzt
    val set = b.set(App.buehne.zustand.value.setId)
    val platz = set?.eintraege?.indexOfFirst { it.songId == kommt.id }?.let { if (it >= 0) "${it + 1} / ${set.eintraege.size}" else "" } ?: ""
    Lautstaerketasten { }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().background(Schwarz).padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (pause.art == "Ansage") Symbol(Sym.MIKRO, Weiss, 22.dp)
            Column {
                Etikett("Zwischen den Songs", farbe = Weiss)
                T(if (pause.art == "Ansage") "Ansage läuft im Ohr" else "Stille Pause", titelStil(26.sp, Weiss), zeilen = 1)
            }
        }
        // B21: Restzeit kleiner als früher (150 sp), dafür Kapo und Tonart des nächsten Songs groß,
        // damit Jan sich in der Pause vorbereiten kann.
        Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Etikett("Gleich geht es weiter in")
            T(zeit(pause.restSek * 1000L), titelStil(92.sp).copy(lineHeight = 90.sp, fontFeatureSettings = "tnum"), zeilen = 1)
            val voll = if (pause.gesamtSek > 0) (((pause.gesamtSek - pause.restSek) * 10) / pause.gesamtSek).coerceIn(0, 10) else 0
            // Die Beschreibungszeile darunter ist seit 15.11. weg: Sie wiederholte nur den Kopf und
            // klebte bei wenig Platz an der Trennlinie.
            Kaestchen(10, voll, Schwarz, Modifier.width(250.dp), 8.dp)
        }
        Column(Modifier.padding(horizontal = 18.dp)) {
            Linie(dicke = 2.5.dp)
            val rueck by App.buehne.zustand.collectAsState()
            // Feste Höhe: "Rückgängig" (8 s nach "Anderer Song") verschiebt nichts.
            Row(Modifier.fillMaxWidth().padding(top = 4.dp).height(36.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Etikett("Als Nächstes${if (platz.isNotEmpty()) " · $platz" else ""} · ${zeit(kommt.dauerMs)}", Modifier.weight(1f))
                if (rueck.rueckgaengig != null) TextKnopf("Rückgängig", Modifier, Art.VOLL, 34.dp, 16.sp, radius = 10.dp) { App.buehne.rueckgaengig() }
            }
            T(kommt.titel, titelStil(34.sp).copy(lineHeight = 35.sp), zeilen = 2)
            Abstand(10.dp)
            KapoTonart(kommt)
            Abstand(12.dp)
        }
        // B20: Jeder Knopf sagt, was passiert. Seit 15.11. zweizeilig, damit kein Text abgeschnitten
        // wird. "Anderer Song" führt zur Liste: der angetippte Song ersetzt den wartenden, der
        // Countdown läuft weiter.
        Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Knopf(Modifier.fillMaxWidth(), Art.VOLL, 58.dp, onClick = { App.buehne.pauseUeberspringen() }) { f ->
                Symbol(Sym.PLAY, f, 20.dp); T("Jetzt starten", textStil(21.sp, f, FontWeight.Bold), zeilen = 1)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextKnopf("Anderer Song", Modifier.weight(1f), hoehe = 54.dp, groesse = 18.sp) { navi.zurSetliste() }
                TextKnopf("Anhalten", Modifier.weight(1f), hoehe = 54.dp, groesse = 18.sp) { App.buehne.stopp() }
            }
        }
    }
}


/**
 * Kapo und Tonart als zwei große Kästen (B21). Kapo 0 = "ohne", Tonart leer = "–", damit die Kästen
 * immer gleich stehen und nichts springt.
 */
@Composable
fun KapoTonart(song: Song, groesse: TextUnit = 44.sp, innen: Dp = 12.dp, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf("Kapo" to (if (song.kapo > 0) "${song.kapo}" else "ohne"), "Tonart" to song.tonart.ifBlank { "–" }).forEach { (et, wert) ->
            Column(Modifier.weight(1f).border(2.5.dp, Schwarz, RoundedCornerShape(14.dp)).padding(horizontal = innen, vertical = innen * 0.6f)) {
                Etikett(et)
                T(wert, titelStil(groesse).copy(lineHeight = groesse * 1.02f), zeilen = 1)
            }
        }
    }
}
