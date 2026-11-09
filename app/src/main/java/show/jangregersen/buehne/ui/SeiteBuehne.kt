package show.jangregersen.buehne.ui

import androidx.compose.foundation.background
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
import show.jangregersen.buehne.lyrics.Seitenlauf

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

@Composable
private fun BuehnenKopf(song: Song, ober: String, dauerMs: Long, spielt: Boolean) {
    val pos by App.buehne.position.collectAsState()
    val dauer = if (dauerMs > 0) dauerMs else song.dauerMs
    val rest = (dauer - pos).coerceAtLeast(0)
    val voll = if (dauer > 0) ((pos * 20) / dauer).toInt().coerceIn(0, 20) else 0
    // Feste Höhe und feste Breite für die Zeit: Der Kopf darf sich beim Herunterzählen nie
    // verändern, sonst baut das E-Ink jede Sekunde den ganzen Balken neu auf.
    val zeitStil = titelStil(46.sp, Weiss).copy(lineHeight = 46.sp, fontFeatureSettings = "tnum")
    val messer = rememberTextMeasurer()
    val zeitBreite = with(LocalDensity.current) {
        val muster = zeit(maxOf(dauer, song.dauerMs)).replace(Regex("[0-9]"), "0")
        messer.measure(muster, zeitStil).size.width.toDp()
    }
    Column(Modifier.fillMaxWidth().background(Schwarz).padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row {
            Etikett(ober, Modifier.weight(1f), Weiss)
            Etikett(if (spielt) "noch" else "Länge", farbe = Weiss)
        }
        Row(Modifier.height(50.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            EinzeilerPassend(song.titel, titelStil(40.sp, Weiss), kleinste = 22f, modifier = Modifier.weight(1f))
            Box(Modifier.width(zeitBreite), contentAlignment = Alignment.BottomEnd) {
                T(zeit(if (spielt || pos > 0) rest else dauer), zeitStil, zeilen = 1)
            }
        }
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
        Etikett("Keine Lyrics · unter Bearbeiten › Lyrics einfügen")
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
    Column(Modifier.padding(horizontal = 18.dp)) {
        Linie(dicke = 2.5.dp)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Etikett("Danach")
                T(naechster?.titel ?: if (setEnde) "Set zu Ende" else "–", titelStil(27.sp), zeilen = 1)
            }
            if (naechster != null) {
                val (sym, text) = when {
                    ende == Ende.ANHALTEN -> Sym.STOP to "hält an"
                    aktuell.ansageNachher.isNotBlank() -> Sym.MIKRO to "Ansage ${zeit(aktuell.ansageNachherMs)}"
                    naechster.ansageVorher.isNotBlank() -> Sym.MIKRO to "Ansage ${zeit(naechster.ansageVorherMs)}"
                    (aktuell.pauseSek.takeIf { it > 0 } ?: naechster.pauseSek) > 0 -> null to "Pause ${pauseText(aktuell.pauseSek.takeIf { it > 0 } ?: naechster.pauseSek)}"
                    else -> null to "ohne Pause"
                }
                Row(
                    Modifier.border(2.dp, Schwarz, RoundedCornerShape(10.dp)).padding(horizontal = 9.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (sym != null) Symbol(sym, Schwarz, 16.dp)
                    T(text, textStil(15.sp, gewicht = FontWeight.Bold), zeilen = 1)
                }
            }
        }
    }
}

// ── Lyrics: abschnittsweise Seiten ───────────────────────────────────────────

@Composable
private fun LyricsBereich(song: Song, dauerMs: Long) {
    val messer = rememberTextMeasurer()
    val dichte = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize().padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 6.dp)) {
        val breitePx = with(dichte) { maxWidth.roundToPx() }
        val hoehePx = with(dichte) { (maxHeight - 26.dp).roundToPx() }
        val seiten = remember(song.lyrics, breitePx, hoehePx) {
            seitenBauen(song.lyrics, breitePx, hoehePx,
                messen = { zeilen, sp -> messer.measure(zeilen.joinToString("\n") { it.ifBlank { " " } }, lyricsStil(sp), constraints = Constraints(maxWidth = breitePx)).size.height },
                breiteVon = { zeile, sp -> messer.measure(zeile, lyricsStil(sp)).size.width })
        }
        val marken = remember(song.marken) { Abschnitte.markenLesen(song.marken) }
        val pos by App.buehne.position.collectAsState()
        val auto = Seitenlauf.seiteFuer(seiten, marken, pos, if (dauerMs > 0) dauerMs else song.dauerMs)
        var hand by remember(song.id) { mutableStateOf<Pair<Int, Int>?>(null) } // (Hand-Seite, Auto-Seite beim Blättern)
        val gezeigt = hand?.let { (h, a) -> if (a == auto) h else null } ?: auto
        val blaettern = { d: Int -> hand = (gezeigt + d).coerceIn(0, seiten.size - 1) to auto }
        Lautstaerketasten { v -> blaettern(if (v) 1 else -1) }
        val seite = seiten.getOrNull(gezeigt) ?: return@BoxWithConstraints
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().height(26.dp), verticalAlignment = Alignment.CenterVertically) {
                Etikett(seite.name + if (seite.teile > 1) " · ${seite.teil}/${seite.teile}" else "", Modifier.weight(1f))
                if (marken.isEmpty()) Etikett("von Hand blättern  ")
                SeitenPunkte(gezeigt, seiten.size)
            }
            Box(Modifier.fillMaxSize()) {
                Column {
                    seite.zeilen.forEach { z ->
                        if (z.isBlank()) Spacer(Modifier.height(10.dp))
                        else T(z, lyricsStil(seite.schriftSp))
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
private fun SeitenPunkte(aktiv: Int, anzahl: Int) {
    if (anzahl > 12) { Etikett("${aktiv + 1}/$anzahl"); return }
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(anzahl) { i ->
            val m = Modifier.size(8.dp).clip(RoundedCornerShape(4.dp))
            Box(if (i == aktiv) m.background(Schwarz) else m.border(1.5.dp, Schwarz, RoundedCornerShape(4.dp)))
        }
    }
}

private fun lyricsStil(sp: Float) = TextStyle(fontFamily = Halbschmal, fontWeight = FontWeight.SemiBold, fontSize = sp.sp, lineHeight = (sp * 1.26f).sp, color = Schwarz, textIndent = androidx.compose.ui.text.style.TextIndent(restLine = (sp * 0.8f).sp))

/**
 * Baut die Bildschirmseiten: eine Schriftgröße für den ganzen Song. Gesucht wird die größte
 * (31 bis 24), bei der keine Zeile umbricht und jeder Abschnitt auf eine Seite passt. Lange
 * Zeilen dürfen erst bei 24 umbrechen. Was dann noch nicht passt, wird geteilt, bevorzugt an
 * Leerzeilen.
 */
private fun seitenBauen(text: String, breite: Int, hoehe: Int, messen: (List<String>, Float) -> Int, breiteVon: (String, Float) -> Int): List<LyricsSeite> {
    val abschnitte = Abschnitte.zerlegen(text)
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
        val gesamt = teile.sumOf { t -> t.count { it.isNotBlank() } }.coerceAtLeast(1)
        var vorher = 0
        teile.forEachIndexed { ti, t ->
            seiten.add(LyricsSeite(ai, a.name, ti + 1, teile.size, if (t.isEmpty()) listOf("— instrumental —") else t, schrift, vorher.toFloat() / gesamt))
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
            T(kommt.titel, titelStil(34.sp), zeilen = 1)
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

