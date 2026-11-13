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
import show.jangregersen.buehne.daten.naechsterNach
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
    val naechster = b.song(naechsterEintrag?.songId)
    val endeHier = set?.eintraege?.firstOrNull { it.songId == song.id }?.ende ?: Ende.ANHALTEN

    Column(Modifier.fillMaxSize()) {
        BuehnenKopf(song, if (z.probe) "Probe" else if (z.spielt) "Jetzt${platz?.let { " · $it" } ?: ""}" else "Bereit${platz?.let { " · $it" } ?: ""}", z.dauerMs, z.spielt)
        z.setDurch?.let { SetEndeHinweis(it) }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (song.lyrics.isNotBlank()) LyricsBereich(song, z.dauerMs)
            else SongDaten(song)
        }
        if (!z.probe) DanachBlock(naechster, song, endeHier, naechsterEintrag == null)
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 14.dp, top = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Knopf(Modifier.weight(1f), beschreibung = "Zur Setliste", onClick = { if (z.probe) navi.zurueck() else navi.zurSetliste() }) { f ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Symbol(Sym.LISTE, f, 20.dp)
                    T(if (z.probe) "Zurück" else "Liste", textStil(12.sp, f, FontWeight.Bold))
                }
            }
            SymbolKnopf(Sym.STOP, "Stopp", Modifier.weight(1f), groesse = 20.dp) { App.buehne.stopp() }
            SymbolKnopf(if (z.spielt) Sym.PAUSE else Sym.PLAY, if (z.spielt) "Pause" else "Abspielen", Modifier.weight(1f), Art.VOLL, groesse = 22.dp) { App.buehne.playPause() }
            SymbolKnopf(Sym.WEITER, "Nächster Song", Modifier.weight(1f), groesse = 22.dp, aktiv = !z.probe && naechster != null) { App.buehne.weiter() }
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
            Etikett(if (spielt) "noch" else "Länge", Modifier.padding(end = 8.dp, bottom = 4.dp), Weiss)
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
private fun SongDaten(song: Song) {
    // Nur Werte zeigen, die es wirklich gibt: leere Kästchen wären auf der Bühne nur Rauschen.
    val werte = listOfNotNull(
        song.tonart.takeIf { it.isNotBlank() }?.let { "Tonart" to it },
        if (song.kapo > 0) "Kapo" to "${song.kapo}" else null,
        if (song.bpm > 0) "Tempo" to "${song.bpm}" else null
    )
    Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (song.interpret.isNotBlank()) T(song.interpret, titelStil(26.sp, gewicht = FontWeight.SemiBold), zeilen = 1)
        werte.chunked(2).forEach { reihe ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                reihe.forEach { (e, w) -> GrossWert(e, w, Modifier.weight(1f)) }
                if (reihe.size == 1) Box(Modifier.weight(1f))
            }
        }
        Etikett("Keine Lyrics")
    }
}

@Composable
private fun GrossWert(etikett: String, wert: String, modifier: Modifier, klein: Boolean = false) {
    Column(modifier.border(2.5.dp, Schwarz, RoundedCornerShape(14.dp)).padding(12.dp)) {
        Etikett(etikett)
        T(wert, titelStil(if (klein) 26.sp else 44.sp), zeilen = 2)
    }
}

@Composable
private fun DanachBlock(naechster: Song?, aktuell: Song, ende: Int, setEnde: Boolean) {
    // Immer sichtbar, immer an derselben Stelle. Der nächste Titel bekommt die volle Breite und
    // bricht lieber um, als abgeschnitten zu werden (Jan: "wirklich gut lesbar").
    Column(Modifier.padding(start = 18.dp, end = 18.dp, bottom = 6.dp)) {
        Linie(dicke = 2.5.dp)
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Etikett("Danach", Modifier.weight(1f))
            if (naechster != null) {
                val (sym, text) = when {
                    ende == Ende.ANHALTEN -> Sym.STOP to "hält an"
                    aktuell.ansageNachher.isNotBlank() -> Sym.MIKRO to "Ansage ${zeit(aktuell.ansageNachherMs)}"
                    naechster.ansageVorher.isNotBlank() -> Sym.MIKRO to "Ansage ${zeit(naechster.ansageVorherMs)}"
                    (aktuell.pauseSek.takeIf { it > 0 } ?: naechster.pauseSek) > 0 -> null to "Pause ${pauseText(aktuell.pauseSek.takeIf { it > 0 } ?: naechster.pauseSek)}"
                    else -> null to "ohne Pause"
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
private val RINNE = 26.dp

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
    BoxWithConstraints(Modifier.fillMaxSize().padding(start = 12.dp, end = 16.dp, top = 10.dp, bottom = 4.dp)) {
        val textBreitePx = with(dichte) { (maxWidth - RINNE).roundToPx() }
        val hoehePx = with(dichte) { (maxHeight - 44.dp).roundToPx() }
        val abschnitte = remember(song.lyrics) { Abschnitte.zerlegen(song.lyrics) }
        // Seiten in Jans Ablauf (Teile weg, wiederholt, umgestellt, Instrumental).
        val ablauf = remember(song.marken, abschnitte) { Ablauf.von(song.marken, abschnitte) }
        val seiten = remember(abschnitte, ablauf, textBreitePx, hoehePx) {
            seitenBauen(ablauf.map { t -> Abschnitt(Ablauf.name(abschnitte, t), if (t.abschnitt == Ablauf.INSTRUMENTAL) emptyList() else abschnitte[t.abschnitt].zeilen) }, textBreitePx, hoehePx,
                messen = { zeilen, sp -> messer.measure(zeilen.joinToString("\n") { it.ifBlank { " " } }, lyricsStil(sp, true), constraints = Constraints(maxWidth = textBreitePx)).size.height },
                breiteVon = { zeile, sp -> messer.measure(zeile, lyricsStil(sp, true)).size.width })
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
        val auto by remember(seiten, starts) { derivedStateOf { Zeitplan.seiteFuer(seiten, ablauf, starts, position.value + Zeitplan.SEITE_VORLAUF_MS) } }
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
            Abstand(4.dp)
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
                                    Row {
                                        Box(Modifier.width(RINNE - 6.dp).padding(end = 4.dp).height(with(dichte) { (seite.schriftSp * 1.26f).sp.toDp() }), contentAlignment = Alignment.Center) {
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
                    if (letzte && naechste != null && vorschau != null && platz > with(dichte) { 52.dp.roundToPx() }) {
                        Row(Modifier.fillMaxWidth().padding(start = 6.dp, bottom = 4.dp).border(2.dp, Schwarz, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            T(if (naechste.abschnitt == seite.abschnitt) "weiter" else naechste.name.uppercase(), etikettStil(), zeilen = 1)
                            T(vorschau, textStil(19.sp, gewicht = FontWeight.SemiBold), zeilen = 1)
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
    Canvas(Modifier.size(width = 13.dp, height = 18.dp)) {
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

private fun lyricsStil(sp: Float, fett: Boolean = false) = TextStyle(fontFamily = Halbschmal, fontWeight = if (fett) FontWeight.Bold else FontWeight.SemiBold, fontSize = sp.sp, lineHeight = (sp * 1.26f).sp, color = Schwarz, textIndent = androidx.compose.ui.text.style.TextIndent(restLine = (sp * 0.8f).sp))

/**
 * Baut die Bildschirmseiten: eine Schriftgröße für den ganzen Song. Gesucht wird die größte
 * (31 bis 24), bei der keine Zeile umbricht und jeder Abschnitt auf eine Seite passt. Lange
 * Zeilen dürfen erst bei 24 umbrechen. Was dann noch nicht passt, wird geteilt, bevorzugt an
 * Leerzeilen.
 */
private fun seitenBauen(abschnitte: List<Abschnitt>, breite: Int, hoehe: Int, messen: (List<String>, Float) -> Int, breiteVon: (String, Float) -> Int): List<LyricsSeite> {
    if (abschnitte.isEmpty()) return emptyList()
    val groessen = listOf(31f, 30f, 29f, 28f, 27f, 26f, 25f, 24f)
    val breitesteBei31 = abschnitte.flatMap { it.zeilen }.filter { it.isNotBlank() }.maxOfOrNull { breiteVon(it, 31f) } ?: 0
    fun ohneUmbruch(sp: Float) = breitesteBei31 * sp / 31f <= breite
    fun passtHoch(sp: Float) = abschnitte.all { it.zeilen.isEmpty() || messen(it.zeilen, sp) <= hoehe }
    val schrift = groessen.firstOrNull { sp -> (ohneUmbruch(sp) || sp == 24f) && passtHoch(sp) } ?: 24f
    val seiten = mutableListOf<LyricsSeite>()
    abschnitte.forEachIndexed { ai, a ->
        val teile = mutableListOf<List<String>>()
        if (a.zeilen.isEmpty() || messen(a.zeilen, schrift) <= hoehe) teile.add(a.zeilen)
        else {
            var aktuell = mutableListOf<String>()
            for (z in a.zeilen) {
                val probe = aktuell + z
                if (aktuell.isNotEmpty() && messen(probe, schrift) > hoehe) {
                    val leer = aktuell.indexOfLast { it.isBlank() }
                    if (leer > 0 && leer >= aktuell.size - 4) {
                        teile.add(aktuell.subList(0, leer).toList()); aktuell = aktuell.subList(leer + 1, aktuell.size).toMutableList()
                    } else { teile.add(aktuell); aktuell = mutableListOf() }
                }
                if (!(aktuell.isEmpty() && z.isBlank())) aktuell.add(z)
            }
            if (aktuell.isNotEmpty()) teile.add(aktuell)
        }
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
        Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Etikett("Gleich geht es weiter in")
            T(zeit(pause.restSek * 1000L), titelStil(150.sp).copy(lineHeight = 146.sp), zeilen = 1)
            val voll = if (pause.gesamtSek > 0) (((pause.gesamtSek - pause.restSek) * 10) / pause.gesamtSek).coerceIn(0, 10) else 0
            Kaestchen(10, voll, Schwarz, Modifier.width(250.dp), 8.dp)
            Abstand(8.dp)
            T(pause.beschreibung, textStil(14.sp), Modifier.padding(horizontal = 18.dp), zeilen = 1, mitte = true)
        }
        Column(Modifier.padding(horizontal = 18.dp)) {
            Linie(dicke = 2.5.dp)
            Abstand(10.dp)
            Etikett("Als Nächstes${if (platz.isNotEmpty()) " · $platz" else ""}")
            T(kommt.titel, titelStil(34.sp).copy(lineHeight = 35.sp), zeilen = 2)
            T(listOf(songInfo(kommt), zeit(kommt.dauerMs)).joinToString(" · "), textStil(16.sp), zeilen = 1)
            Abstand(12.dp)
        }
        Row(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Knopf(Modifier.weight(1f), beschreibung = "Zur Setliste", onClick = { navi.zurSetliste() }) { f ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) { Symbol(Sym.LISTE, f, 20.dp); T("Liste", textStil(12.sp, f, FontWeight.Bold)) }
            }
            TextKnopf("Stopp", Modifier.weight(1f)) { App.buehne.stopp() }
            Knopf(Modifier.weight(1.6f), Art.VOLL, onClick = { App.buehne.pauseUeberspringen() }) { f ->
                T("Starten", textStil(17.sp, f, FontWeight.Bold)); Symbol(Sym.WEITER, f, 18.dp)
            }
        }
    }
}

