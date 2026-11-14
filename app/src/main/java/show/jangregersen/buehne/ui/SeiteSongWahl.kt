package show.jangregersen.buehne.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import show.jangregersen.buehne.App
import show.jangregersen.buehne.daten.Song
import show.jangregersen.buehne.daten.alsNaechstes
import show.jangregersen.buehne.daten.einfuegenNach
import show.jangregersen.buehne.daten.eintragAendern
import show.jangregersen.buehne.daten.songsHinzufuegen
import show.jangregersen.buehne.daten.verschieben

// ── Song wählen (B19, Jans Wahl 14.11.) ─────────────────────────────────────
//
// Ein Tipp auf einen Song (Setliste oder Archiv) zeigt zuerst nur das, was live zählt:
// "Jetzt spielen" und "Danach spielen". Die Einstellungen (Pause, Songende, Ansage, Mixer) liegen
// dahinter. Jeder Knopf sagt in einer kleinen Zeile darunter, was genau passiert.

@Composable
fun SongWahlSeite(navi: Navi, setId: Long, songId: Long) {
    val b by App.ablage.stand.collectAsState()
    val z by App.buehne.zustand.collectAsState()
    val song = b.song(songId) ?: run { WegHier(navi); return }
    val set = b.set(setId) ?: run { WegHier(navi); return }
    val e = set.eintraege.firstOrNull { it.songId == songId }
    val imSet = e != null
    val istAktuell = z.songId == songId
    val bereit = b.song(z.songId)
    Lautstaerketasten { }

    Column(Modifier.fillMaxSize()) {
        if (bereit != null) JetztBand(navi, bereit.titel)
        Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 14.dp, top = 10.dp, bottom = 8.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Etikett(
                    (if (e == null) "Noch nicht im Set" else "Platz ${set.eintraege.indexOf(e) + 1}${if (e.gespielt) " · gespielt" else ""}") + " · ${zeit(song.dauerMs)}"
                )
                T(song.titel, titelStil(30.sp), zeilen = 2)
            }
            SymbolKnopf(Sym.KREUZ, "Schließen", Modifier.width(52.dp), hoehe = 52.dp, groesse = 16.dp) { navi.zurueck() }
        }
        Linie(Modifier.padding(horizontal = 14.dp), 2.5.dp)
        Column(Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            KapoTonart(song, 26.sp, 8.dp)
            when {
                istAktuell -> {
                    T(if (z.spielt) "Dieser Song läuft gerade." else "Dieser Song liegt schon auf der Bühne bereit.", textStil(18.sp, gewicht = FontWeight.Bold))
                    WahlKnopf("Zur Bühne", "Dort startest du ihn mit Play.", Art.VOLL, Sym.ZEILEN) { navi.zurBuehne() }
                }
                z.spielt -> {
                    WahlKnopf("Danach spielen", "Kommt direkt nach dem laufenden Song.", Art.VOLL, Sym.WEITER) { danach(navi, setId, song, imSet) }
                    T("„Jetzt spielen“ geht erst, wenn der laufende Song zu Ende oder gestoppt ist.", textStil(15.sp))
                }
                else -> {
                    WahlKnopf("Jetzt spielen", if (z.pause != null) "Pause endet. Liegt bereit, Play drückst du." else "Liegt auf der Bühne bereit, Play drückst du.", Art.VOLL, Sym.PLAY) {
                        jetzt(navi, setId, songId)
                    }
                    if (bereit != null) WahlKnopf("Danach spielen", "Kommt nach „${bereit.titel}“.", Art.UMRISS, Sym.WEITER) { danach(navi, setId, song, imSet) }
                }
            }
            if (!imSet) WahlKnopf("Ans Ende vom Set", "Nur einplanen, kommt erst am Schluss.", Art.DUENN, Sym.PLUS) {
                App.ablage.aendern { it.songsHinzufuegen(setId, listOf(songId)) }
                App.buehne.hinweis("„${song.titel}“ steht jetzt am Ende vom Set.")
                navi.zurueck()
            }
            if (e != null && e.gespielt && !istAktuell) WahlKnopf("Nochmal einplanen", "Gilt wieder als nicht gespielt, die Automatik spielt ihn noch einmal.", Art.DUENN) {
                App.ablage.aendern { it.eintragAendern(setId, songId) { x -> x.copy(gespielt = false) } }
                App.buehne.hinweis("„${song.titel}“ ist wieder eingeplant.")
            }
        }
        if (imSet) TextKnopf("Einstellungen: Pause, Ansage, Mixer", Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp).fillMaxWidth(), Art.DUENN, 50.dp, 15.sp) {
            navi.zu(Seite.SongKarte(setId, songId))
        }
    }
}

/** Song sofort an die Reihe: vor den Song, der gerade bereitliegt, dann bereitlegen und zur Bühne. */
private fun jetzt(navi: Navi, setId: Long, songId: Long) {
    val z = App.buehne.zustand.value
    val set = App.ablage.jetzt.set(setId)
    // Vorgezogene Songs rücken vor den bisher nächsten: danach geht das Set dort weiter, wo es war.
    val vor = z.songId?.takeIf { id -> id != songId && set?.eintraege?.any { it.songId == id && !it.gespielt } == true }
    App.ablage.aendern { b ->
        var n = b.einfuegenNach(setId, songId, null)
        if (vor != null) n = n.verschieben(setId, songId, vor)
        n.eintragAendern(setId, songId) { it.copy(gespielt = false) }
    }
    if (App.buehne.jetztBereitlegen(setId, songId)) navi.zurBuehne()
    else App.buehne.hinweis("Erst den laufenden Song stoppen.")
}

private fun danach(navi: Navi, setId: Long, song: Song, imSet: Boolean) {
    val aktuell = App.buehne.zustand.value.songId
    App.ablage.aendern { if (imSet) it.alsNaechstes(setId, song.id, aktuell) else it.einfuegenNach(setId, song.id, aktuell) }
    App.buehne.hinweis("„${song.titel}“ kommt als Nächstes.")
    navi.zurSetliste()
}

@Composable
private fun WahlKnopf(text: String, erklaerung: String, art: Art, symbol: Sym? = null, onClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        TextKnopf(text, Modifier.fillMaxWidth(), art, 54.dp, 21.sp, symbol, onClick = onClick)
        T(erklaerung, textStil(15.sp), Modifier.padding(horizontal = 6.dp), zeilen = 2)
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
