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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import show.jangregersen.buehne.App
import show.jangregersen.buehne.daten.Eintrag
import show.jangregersen.buehne.daten.Ende
import show.jangregersen.buehne.daten.Song
import show.jangregersen.buehne.daten.PAUSE_WAHL
import show.jangregersen.buehne.daten.dauerMs
import show.jangregersen.buehne.daten.naechstePause
import show.jangregersen.buehne.daten.pauseDanach
import show.jangregersen.buehne.daten.pauseSpeichern
import show.jangregersen.buehne.daten.schieben
import show.jangregersen.buehne.daten.songAendern
import show.jangregersen.buehne.daten.verschieben
import show.jangregersen.buehne.daten.wasKommt

/**
 * Jans Wunsch 15.11.: das kleine Pausen-Kästchen aus Fassung 5 (gleiche Größe), jetzt antippbar.
 * Jeder Tipp: 10 s → 30 s → 1 min → 2 min → 0 s → 10 s. Die Tippfläche ist etwas größer als das
 * Kästchen (unsichtbarer Rand), damit man es mit dem Finger sicher trifft.
 */
@Composable
fun PausenKaestchen(s: Song, farbe: androidx.compose.ui.graphics.Color, kompakt: Boolean = false) {
    val p = s.pauseDanach
    // kompakt (Hauptbildschirm, 20.11.): gleiche Schrift, kurz als Pausenzeichen + Dauer ("‖ 30 s"),
    // ohne Luft darüber/darunter; steht rechtsbündig unter der Songlänge.
    val stil = textStil(13.sp, farbe, FontWeight.Bold)
    Box(
        Modifier.semantics { contentDescription = "Pause umschalten" }
            .tipp { App.ablage.aendern { it.songAendern(s.id) { x -> x.copy(pauseSek = pauseSpeichern(naechstePause(x.pauseDanach))) } } }
            .padding(end = if (kompakt) 0.dp else 8.dp, top = if (kompakt) 0.dp else 3.dp, bottom = if (kompakt) 0.dp else 3.dp)
    ) {
        if (kompakt) Row(
            Modifier.border(1.5.dp, farbe, RoundedCornerShape(6.dp)).padding(horizontal = 5.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Symbol(Sym.PAUSE, farbe, 10.dp)
            T(if (p > 0) pauseText(p) else "0 s", stil, zeilen = 1)
        }
        else Box(Modifier.border(1.5.dp, farbe, RoundedCornerShape(6.dp)).padding(horizontal = 5.dp, vertical = 1.dp)) {
            T(if (p > 0) "Pause ${pauseText(p)}" else "ohne Pause", stil, zeilen = 1)
        }
    }
}

fun pauseText(sek: Int): String = when {
    sek <= 0 -> "0 s"
    sek < 60 -> "$sek s"
    sek % 60 == 0 -> "${sek / 60} min"
    else -> "%d:%02d min".format(sek / 60, sek % 60)
}

/** Die fünf Pausen als Knopfreihe (Jans Wahl 15.11.). Steht ein alter Wert drin, ist keiner gewählt. */
@Composable
fun PausenWahl(song: Song, modifier: Modifier = Modifier, hoehe: androidx.compose.ui.unit.Dp = 50.dp) {
    Segmente(PAUSE_WAHL.map { pauseText(it) }, PAUSE_WAHL.indexOf(song.pauseDanach), modifier, hoehe = hoehe, groesse = 16.sp) { i ->
        App.ablage.aendern { it.songAendern(song.id) { s -> s.copy(pauseSek = pauseSpeichern(PAUSE_WAHL[i])) } }
    }
}

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
        if (set.eintraege.isNotEmpty()) T("Rechts tippen = Pause danach wechseln. Songname = Ansage, Mixer.", textStil(15.sp), Modifier.padding(start = 18.dp, end = 18.dp, top = 8.dp), zeilen = 2)
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
        Column(Modifier.weight(1f)) {
            PassenderTitel(s.titel, { textStil(it) }, 22, 15)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (s.ansageVorher.isNotBlank() || s.ansageNachher.isNotBlank()) Symbol(Sym.MIKRO, Schwarz, 14.dp)
                T(if (e.ende == Ende.WEITER) "dann weiter" else "hält an", textStil(13.sp, gewicht = FontWeight.Bold), zeilen = 1)
            }
        }
        // Jans Wunsch 15.11.: Pause mit einem Tipp umschalten, 10 s → 30 s → 1 min → 2 min → 0 s → 10 s.
        val form = RoundedCornerShape(12.dp)
        Box(
            Modifier.width(78.dp).height(48.dp).clip(form).border(2.5.dp, Schwarz, form)
                .semantics { contentDescription = "Pause umschalten" }
                .tipp { App.ablage.aendern { it.songAendern(s.id) { x -> x.copy(pauseSek = pauseSpeichern(naechstePause(x.pauseDanach))) } } },
            contentAlignment = Alignment.Center
        ) {
            T(pauseText(s.pauseDanach), titelStil(22.sp), zeilen = 1)
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
                PassenderTitel(s.titel, { textStil(it) }, 21, 15, Modifier.weight(1f), durchgestrichen = e.gespielt)
                if (hand != null) Etikett("davor")
            }
            Linie(Modifier.padding(horizontal = 10.dp))
        }
    }
}
