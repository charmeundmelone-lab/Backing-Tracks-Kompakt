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
import show.jangregersen.buehne.daten.schieben
import show.jangregersen.buehne.daten.verschieben
import show.jangregersen.buehne.daten.wasKommt

// ── Setliste live (15.11.: eine Regel) ───────────────────────────────────────
//
// Antippen = kommt als Nächstes dran, danach geht es von selbst zurück zur Bühne (8 s Rückgängig).
// Oben steht, was kommt (ab dem laufenden Song); die gespielten liegen unten in einer Zeile.
// Vorbereiten (Pausen, Ansagen, Umstellen, Songs dazu) liegt auf "Set bearbeiten", nicht hier.

private object GespieltMarke

@Composable
fun SetlisteSeite(navi: Navi) {
    val b by App.ablage.stand.collectAsState()
    val z by App.buehne.zustand.collectAsState()
    val set = b.set(z.setId)
    val gig = b.gigVonSet(z.setId)
    var gespieltZeigen by remember { mutableStateOf(false) }
    // Jeder Besuch beginnt auf Seite 1, also beim laufenden Song (nicht dort, wo man zuletzt war).
    val besuch = remember { System.nanoTime() }

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
    val gespielt = set.eintraege.filter { it.gespielt && it.songId != z.songId }
    val kommt = set.wasKommt(z.songId)
    val ober = "${gig.name} · ${set.eintraege.count { it.gespielt }} von ${set.eintraege.size} gespielt · noch ${minuten(set.dauerMs(b, nurOffene = true))}"
    // "hält an" nur zeigen, wenn es im Set vom Üblichen abweicht (sonst stünde es überall).
    val mitAutomatik = set.eintraege.any { it.ende == Ende.WEITER }

    Column(Modifier.fillMaxSize()) {
        SetKopf(ober, set.name, onSets = { navi.zumGig(gig.id) }, onBuehne = { navi.zurBuehne() })
        if (set.eintraege.isEmpty()) {
            Column(Modifier.padding(18.dp)) {
                T("Dieses Set ist noch leer.", titelStil(26.sp))
                Abstand(12.dp)
                TextKnopf("Songs hinzufügen", Modifier.fillMaxWidth(), Art.VOLL, symbol = Sym.PLUS) { navi.zu(Seite.SetBearbeiten(set.id)) }
            }
            return@Column
        }
        val zeilen: List<Any> = if (gespieltZeigen) gespielt else kommt + (if (gespielt.isNotEmpty()) listOf(GespieltMarke) else emptyList())
        SeitenListe(
            eintraege = zeilen, zeilenHoehe = 64.dp, modifier = Modifier.weight(1f), schluessel = "live${set.id}·$besuch·$gespieltZeigen",
            fussHoehe = AKTIONSLEISTE_HOEHE,
            fuss = { s, n, _, zur, vor ->
                AktionsLeiste(s, n, zur, vor, listOf(
                    if (gespieltZeigen) Aktion("Was kommt", voll = true) { gespieltZeigen = false }
                    else Aktion("Wunschsong", Sym.PLUS) { navi.zu(Seite.Archiv(set.id, wunsch = true)) }
                ))
            },
            leer = {
                Column(Modifier.padding(18.dp)) {
                    T("Noch nichts gespielt.", titelStil(26.sp))
                }
            }
        ) { _, x ->
            if (x is Eintrag) {
                val s = b.song(x.songId) ?: return@SeitenListe
                val nr = set.eintraege.indexOfFirst { it.songId == x.songId } + 1
                val status = when {
                    z.pause != null -> "kommt nach der Pause"
                    z.spielt -> "läuft"
                    else -> "bereit"
                }
                SetZeile(nr, s, x, aktuell = x.songId == z.songId, status = status, haeltAnZeigen = mitAutomatik) { antippen(navi, set.id, s.id) }
            } else GespieltZeile(gespielt, b.songs) { gespieltZeigen = true }
        }
    }
}

/** Die eine Regel: Antippen = kommt als Nächstes dran; danach zurück zur Bühne. */
private fun antippen(navi: Navi, setId: Long, songId: Long) {
    if (songId != App.buehne.zustand.value.songId) App.buehne.vorziehen(setId, songId)
    navi.zurBuehne()
}

/** Ein Kopf statt zwei: Set-Name (Tipp = Sets des Gigs) und der Weg zur Bühne. */
@Composable
private fun SetKopf(ober: String, setName: String, onSets: () -> Unit, onBuehne: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(Schwarz).padding(start = 18.dp, end = 10.dp, top = 9.dp, bottom = 9.dp)) {
        Etikett(ober, farbe = Weiss)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.weight(1f).height(44.dp).tipp(onClick = onSets), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                T(setName, titelStil(28.sp, Weiss), Modifier.weight(1f, fill = false), zeilen = 1)
                Symbol(Sym.RUNTER, Weiss, 18.dp)
            }
            WeisserKnopfImBand("Bühne", Sym.ZEILEN, onClick = onBuehne)
        }
    }
}

@Composable
private fun SetZeile(nr: Int, s: Song, e: Eintrag, aktuell: Boolean, status: String, haeltAnZeigen: Boolean, onClick: () -> Unit) {
    if (aktuell) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 2.dp).fillMaxWidth().height(60.dp).clip(RoundedCornerShape(12.dp))
                .background(Schwarz).tipp(onClick = onClick).padding(start = 8.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(Modifier.width(26.dp), contentAlignment = Alignment.Center) { Symbol(Sym.PLAY, Weiss, 22.dp) }
            Column(Modifier.weight(1f)) {
                T(s.titel, textStil(22.sp, Weiss, FontWeight.Bold), zeilen = 1)
                T(status, textStil(15.sp, Weiss, FontWeight.Medium), zeilen = 1)
            }
            T(zeit(s.dauerMs), titelStil(22.sp, Weiss))
        }
        Abstand(2.dp)
        return
    }
    Row(
        Modifier.padding(horizontal = 10.dp).fillMaxWidth().height(63.dp).tipp(onClick = onClick).padding(start = 8.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(Modifier.width(26.dp), contentAlignment = Alignment.Center) {
            if (e.gespielt) Box(Modifier.size(22.dp).clip(RoundedCornerShape(11.dp)).background(Schwarz), contentAlignment = Alignment.Center) { Symbol(Sym.HAKEN, Weiss, 13.dp) }
            else T("$nr", titelStil(22.sp))
        }
        // Zweite Zeile nur, wenn etwas vom Üblichen abweicht: hält an, Ansage.
        val haeltAn = haeltAnZeigen && e.ende == Ende.ANHALTEN
        val ansage = s.ansageVorher.isNotBlank() || s.ansageNachher.isNotBlank()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            T(s.titel, textStil(22.sp).copy(lineHeight = 24.sp), zeilen = if (haeltAn || ansage) 1 else 2, durchgestrichen = e.gespielt)
            if (haeltAn || ansage) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (ansage) Symbol(Sym.MIKRO, Schwarz, 15.dp)
                if (haeltAn) Box(Modifier.border(1.5.dp, Schwarz, RoundedCornerShape(6.dp)).padding(horizontal = 5.dp, vertical = 1.dp)) { T("hält an", textStil(14.sp, gewicht = FontWeight.Bold), zeilen = 1) }
            }
        }
        T(zeit(s.dauerMs), titelStil(20.sp, gewicht = FontWeight.SemiBold), Modifier.width(44.dp), zeilen = 1)
    }
    Linie(Modifier.padding(horizontal = 10.dp))
}

@Composable
private fun GespieltZeile(gespielt: List<Eintrag>, songs: List<Song>, onClick: () -> Unit) {
    val titel = gespielt.mapNotNull { e -> songs.firstOrNull { it.id == e.songId }?.titel }
    Row(
        Modifier.padding(horizontal = 10.dp).fillMaxWidth().height(63.dp).tipp(onClick = onClick).padding(start = 8.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(Modifier.size(26.dp).clip(RoundedCornerShape(13.dp)).background(Schwarz), contentAlignment = Alignment.Center) { Symbol(Sym.HAKEN, Weiss, 14.dp) }
        Column(Modifier.weight(1f)) {
            T("${gespielt.size} gespielt", textStil(20.sp, gewicht = FontWeight.Bold), zeilen = 1)
            T(titel.joinToString(", "), textStil(15.sp), zeilen = 1)
        }
        Symbol(Sym.RECHTS, Schwarz, 16.dp)
    }
}

fun pauseText(sek: Int): String = if (sek <= 0) "keine" else if (sek < 60) "0:%02d".format(sek) else "%d:%02d".format(sek / 60, sek % 60)

// ── Set bearbeiten (Vorbereiten) ─────────────────────────────────────────────

@Composable
fun SetBearbeitenSeite(navi: Navi, setId: Long) {
    val b by App.ablage.stand.collectAsState()
    val set = b.set(setId) ?: run { WegHier(navi); return }
    var verschieben by remember { mutableStateOf(false) }
    var inHand by remember { mutableStateOf<Long?>(null) }
    if (verschieben) {
        VerschiebenAnsicht(set.id, set.eintraege, b.songs, inHand, onNehmen = { inHand = it }, onFertig = { verschieben = false; inHand = null })
        return
    }
    Column(Modifier.fillMaxSize()) {
        KopfBand("${set.name} · ${set.eintraege.size} Songs · ${minuten(set.dauerMs(b))}", "Bearbeiten", zurueck = { navi.zurueck() },
            rechts = { WeisserKnopfImBand("Fertig") { navi.zurueck() } })
        if (set.eintraege.isNotEmpty()) T("Antippen = Pause, Ansage, Mixer. Rechts: Pause danach.", textStil(15.sp), Modifier.padding(start = 18.dp, end = 18.dp, top = 8.dp), zeilen = 2)
        SeitenListe(
            eintraege = set.eintraege, zeilenHoehe = 60.dp, modifier = Modifier.weight(1f), schluessel = "bearb$setId",
            fussHoehe = AKTIONSLEISTE_HOEHE,
            fuss = { s, n, _, zur, vor ->
                AktionsLeiste(s, n, zur, vor, listOf(
                    Aktion("Songs", Sym.PLUS, voll = true) { navi.zu(Seite.Archiv(setId)) },
                    Aktion("Umstellen") { verschieben = true }
                ))
            },
            leer = {
                Column(Modifier.padding(18.dp)) {
                    T("Dieses Set ist noch leer.", titelStil(26.sp))
                    Abstand(12.dp)
                    TextKnopf("Songs hinzufügen", Modifier.fillMaxWidth(), Art.VOLL, symbol = Sym.PLUS) { navi.zu(Seite.Archiv(setId)) }
                }
            }
        ) { i, e ->
            val s = b.song(e.songId) ?: return@SeitenListe
            BearbeitenZeile(i + 1, s, e) { navi.zu(Seite.SongKarte(setId, s.id)) }
        }
    }
}

@Composable
private fun BearbeitenZeile(nr: Int, s: Song, e: Eintrag, onClick: () -> Unit) {
    Row(
        Modifier.padding(horizontal = 10.dp).fillMaxWidth().height(59.dp).tipp(onClick = onClick).padding(start = 8.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(Modifier.width(26.dp), contentAlignment = Alignment.Center) {
            if (e.gespielt) Box(Modifier.size(22.dp).clip(RoundedCornerShape(11.dp)).background(Schwarz), contentAlignment = Alignment.Center) { Symbol(Sym.HAKEN, Weiss, 13.dp) }
            else T("$nr", titelStil(22.sp))
        }
        T(s.titel, textStil(22.sp), Modifier.weight(1f), zeilen = 1)
        if (s.ansageVorher.isNotBlank() || s.ansageNachher.isNotBlank()) Symbol(Sym.MIKRO, Schwarz, 16.dp)
        Column(horizontalAlignment = Alignment.End) {
            T(pauseText(s.pauseSek), titelStil(19.sp, gewicht = FontWeight.SemiBold), zeilen = 1)
            T(if (e.ende == Ende.WEITER) "weiter" else "hält an", textStil(13.sp, gewicht = FontWeight.Bold), zeilen = 1)
        }
    }
    Linie(Modifier.padding(horizontal = 10.dp))
}

@Composable
private fun VerschiebenAnsicht(setId: Long, eintraege: List<Eintrag>, songs: List<Song>, inHand: Long?, onNehmen: (Long?) -> Unit, onFertig: () -> Unit) {
    val songVon = { id: Long -> songs.firstOrNull { it.id == id } }
    Column(Modifier.fillMaxSize()) {
        KopfBand("Set bearbeiten", "Reihenfolge", rechts = { WeisserKnopfImBand("Fertig", onClick = onFertig) })
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
