package show.jangregersen.buehne.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import show.jangregersen.buehne.App
import show.jangregersen.buehne.daten.Eintrag
import show.jangregersen.buehne.daten.Ende
import show.jangregersen.buehne.daten.Song
import show.jangregersen.buehne.daten.dauerMs
import show.jangregersen.buehne.daten.gespieltZuruecksetzen
import show.jangregersen.buehne.daten.schieben
import show.jangregersen.buehne.daten.verschieben

@Composable
fun SetlisteSeite(navi: Navi) {
    val b by App.ablage.stand.collectAsState()
    val z by App.buehne.zustand.collectAsState()
    val set = b.set(z.setId)
    val gig = b.gigVonSet(z.setId)
    var verschieben by remember { mutableStateOf(false) }
    var inHand by remember { mutableStateOf<Long?>(null) }

    if (set == null || gig == null) {
        Rahmen(navi, "Setliste", "Kein Set gewählt") {
            Column(Modifier.padding(18.dp)) {
                T("Wähle zuerst einen Gig und darin ein Set.", textStil(17.sp))
                Abstand(14.dp)
                TextKnopf("Zu den Gigs", Modifier.fillMaxWidth(), Art.VOLL) { navi.stapel.clear(); navi.stapel.add(Seite.Gigs) }
            }
        }
        return
    }
    val nr = gig.sets.indexOfFirst { it.id == set.id } + 1
    val gespielt = set.eintraege.count { it.gespielt }
    val rest = set.dauerMs(b, nurOffene = true)
    // Kurz halten: der Gig steht eine Seite weiter oben, wichtig sind Fortschritt und Restzeit.
    val ober = "$gespielt/${set.eintraege.size} · noch ${minuten(rest)}"
    val startZeige = remember { Zeige(set.eintraege.indexOfFirst { it.songId == z.songId }.coerceAtLeast(0)) }

    if (verschieben) {
        VerschiebenAnsicht(navi, set.id, set.eintraege, b.songs, inHand, onNehmen = { inHand = it }, onFertig = { verschieben = false; inHand = null })
        return
    }

    // Alles, was man im Set live braucht, liegt sichtbar auf dieser Seite, ohne Menü:
    // oben rechts "Sets" (anderes Set), unten Blättern · Song dazu · Umstellen.
    Rahmen(navi, ober, set.name, rechts = {
        TextKnopf("Sets", Modifier.width(86.dp), hoehe = 44.dp, groesse = 16.sp) { navi.zumGig(gig.id) }
    }) {
        SeitenListe(
            eintraege = set.eintraege, zeilenHoehe = 66.dp, modifier = Modifier.weight(1f), schluessel = "set${set.id}", zeige = startZeige,
            fussHoehe = AKTIONSLEISTE_HOEHE,
            fuss = { s, n, _, zur, vor ->
                AktionsLeiste(s, n, zur, vor, listOf(
                    Aktion("Archiv", Sym.PLUS, voll = true) { navi.zu(Seite.Archiv(set.id)) },
                    Aktion("Umstellen") { verschieben = true }
                ))
            },
            leer = {
                Column(Modifier.padding(18.dp)) {
                    T("Dieses Set ist noch leer.", titelStil(26.sp))
                    Abstand(12.dp)
                    TextKnopf("Songs hinzufügen", Modifier.fillMaxWidth(), Art.VOLL, symbol = Sym.PLUS) { navi.zu(Seite.Archiv(set.id)) }
                }
            }
        ) { i, e ->
            val s = b.song(e.songId) ?: return@SeitenListe
            SetZeile(i + 1, s, e, aktuell = e.songId == z.songId, spielt = z.spielt) { navi.zu(Seite.SongWahl(set.id, s.id)) }
        }
    }
}

@Composable
private fun SetZeile(nr: Int, s: Song, e: Eintrag, aktuell: Boolean, spielt: Boolean, onClick: () -> Unit) {
    if (aktuell) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 2.dp).fillMaxWidth().height(62.dp).clip(RoundedCornerShape(12.dp))
                .background(Schwarz).tipp(onClick = onClick).padding(start = 8.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(Modifier.width(26.dp), contentAlignment = Alignment.Center) { Symbol(Sym.PLAY, Weiss, 22.dp) }
            Column(Modifier.weight(1f)) {
                T(s.titel, textStil(22.sp, Weiss, FontWeight.Bold), zeilen = 1)
                T(listOf(if (spielt) "läuft" else "bereit", songInfo(s)).filter { it.isNotBlank() }.joinToString(" · "), textStil(14.sp, Weiss, FontWeight.Medium), zeilen = 1)
            }
            T(zeit(s.dauerMs), titelStil(22.sp, Weiss))
        }
        Abstand(2.dp)
        return
    }
    Row(
        Modifier.padding(horizontal = 10.dp).fillMaxWidth().height(64.dp).tipp(onClick = onClick).padding(start = 8.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(Modifier.width(26.dp), contentAlignment = Alignment.Center) {
            if (e.gespielt) Box(Modifier.size(22.dp).clip(RoundedCornerShape(11.dp)).background(Schwarz), contentAlignment = Alignment.Center) { Symbol(Sym.HAKEN, Weiss, 13.dp) }
            else T("$nr", titelStil(22.sp))
        }
        // Titel über die volle Breite (nichts daneben, was ihn abschneidet); Pause, Automatik
        // und Ansage stehen klein in der zweiten Zeile.
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val mitZeile2 = s.ansageVorher.isNotBlank() || s.ansageNachher.isNotBlank() || s.pauseSek > 0 || e.ende == Ende.WEITER || songInfo(s).isNotBlank()
            // Ohne zweite Zeile darf ein langer Titel umbrechen statt abgeschnitten zu werden.
            T(s.titel, textStil(22.sp).copy(lineHeight = 24.sp), zeilen = if (mitZeile2) 1 else 2, durchgestrichen = e.gespielt)
            if (mitZeile2) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (s.ansageVorher.isNotBlank() || s.ansageNachher.isNotBlank()) Symbol(Sym.MIKRO, Schwarz, 15.dp)
                if (s.pauseSek > 0) Box(Modifier.border(1.5.dp, Schwarz, RoundedCornerShape(6.dp)).padding(horizontal = 5.dp, vertical = 1.dp)) { T("Pause ${pauseText(s.pauseSek)}", textStil(13.sp, gewicht = FontWeight.Bold), zeilen = 1) }
                if (e.ende == Ende.WEITER) Box(Modifier.border(1.5.dp, Schwarz, RoundedCornerShape(6.dp)).padding(horizontal = 5.dp, vertical = 1.dp)) { T("weiter", textStil(13.sp, gewicht = FontWeight.Bold), zeilen = 1) }
                songInfo(s).takeIf { it.isNotBlank() }?.let { T(it, textStil(14.sp, gewicht = FontWeight.Medium), Modifier.weight(1f, fill = false), zeilen = 1) }
            }
        }
        T(zeit(s.dauerMs), titelStil(20.sp, gewicht = FontWeight.SemiBold), Modifier.width(42.dp), zeilen = 1)
    }
    Linie(Modifier.padding(horizontal = 10.dp))
}

fun pauseText(sek: Int): String = if (sek <= 0) "keine" else if (sek < 60) "0:%02d".format(sek) else "%d:%02d".format(sek / 60, sek % 60)

@Composable
private fun VerschiebenAnsicht(navi: Navi, setId: Long, eintraege: List<Eintrag>, songs: List<Song>, inHand: Long?, onNehmen: (Long?) -> Unit, onFertig: () -> Unit) {
    val songVon = { id: Long -> songs.firstOrNull { it.id == id } }
    Column(Modifier.fillMaxSize()) {
        KopfBand("Setliste", "Reihenfolge", rechts = { WeisserKnopfImBand("Fertig", onClick = onFertig) })
        val hand = inHand?.let { songVon(it) }
        if (hand != null) {
            val pos = eintraege.indexOfFirst { it.songId == hand.id } + 1
            Row(
                Modifier.padding(start = 10.dp, end = 10.dp, top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).gestrichelt(14.dp, 2.5.dp).padding(8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Etikett("In der Hand · Platz $pos")
                    T(hand.titel, textStil(22.sp, gewicht = FontWeight.Bold), zeilen = 1)
                }
                SymbolKnopf(Sym.HOCH, "Einen Platz nach oben", Modifier.width(48.dp), hoehe = 48.dp, groesse = 18.dp) { App.ablage.aendern { it.schieben(setId, hand.id, -1) } }
                SymbolKnopf(Sym.RUNTER, "Einen Platz nach unten", Modifier.width(48.dp), hoehe = 48.dp, groesse = 18.dp) { App.ablage.aendern { it.schieben(setId, hand.id, 1) } }
                SymbolKnopf(Sym.KREUZ, "Loslassen", Modifier.width(48.dp), Art.OHNE, hoehe = 48.dp, groesse = 16.dp) { onNehmen(null) }
            }
            T("Tippe auf den Song, vor den er soll.", textStil(15.sp), Modifier.padding(start = 18.dp, top = 8.dp))
        } else {
            T("Tippe den Song an, den du verschieben willst.", textStil(16.sp), Modifier.padding(start = 18.dp, end = 18.dp, top = 12.dp))
        }
        val liste = eintraege.filter { it.songId != inHand }
        SeitenListe(
            eintraege = liste, zeilenHoehe = 56.dp, modifier = Modifier.weight(1f), schluessel = "versch$setId", oben = 4.dp,
            fussHoehe = if (hand != null) 140.dp else 78.dp,
            fuss = { s, n, _, z, v ->
                Column {
                    if (hand != null) TextKnopf("Ans Ende vom Set", Modifier.padding(horizontal = 10.dp).fillMaxWidth(), Art.DUENN, hoehe = 48.dp, groesse = 16.sp) {
                        App.ablage.aendern { it.verschieben(setId, hand.id, null) }; onNehmen(null)
                    }
                    Blaetterleiste(s, n, z, v, mitteKlein = if (hand != null) "Blättern nimmt den Song mit" else null)
                }
            }
        ) { _, e ->
            val s = songVon(e.songId) ?: return@SeitenListe
            val nr = eintraege.indexOf(e) + 1
            Row(
                Modifier.padding(horizontal = 10.dp).fillMaxWidth().height(56.dp).tipp {
                    if (hand == null) onNehmen(s.id) else { App.ablage.aendern { it.verschieben(setId, hand.id, s.id) }; onNehmen(null) }
                }.padding(start = 6.dp, end = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (hand != null) Symbol(Sym.RECHTS, Schwarz, 14.dp)
                T("$nr", titelStil(20.sp), Modifier.width(24.dp))
                T(s.titel, textStil(21.sp), Modifier.weight(1f), zeilen = 1, durchgestrichen = e.gespielt)
                if (hand != null) Etikett("davor")
            }
            Linie(Modifier.padding(horizontal = 10.dp))
        }
    }
}
