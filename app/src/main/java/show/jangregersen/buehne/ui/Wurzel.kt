package show.jangregersen.buehne.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import show.jangregersen.buehne.App

// ── Navigation ───────────────────────────────────────────────────────────────

sealed class Seite {
    data object Gigs : Seite()
    data class Archiv(val zielSetId: Long? = null) : Seite()
    data class GigSets(val gigId: Long) : Seite()
    data object Setliste : Seite()
    data object Buehne : Seite()
    data class SongKarte(val setId: Long, val songId: Long) : Seite()
    data class Editor(val songId: Long, val reiter: Int = 0) : Seite()
    data class Einmessen(val songId: Long) : Seite()
    data class Mixer(val songId: Long) : Seite()
    class Eingabe(val titel: String, val start: String, val zahl: Boolean = false, val hinweis: String = "", val ok: (String) -> Unit) : Seite()
    class Frage(val titel: String, val text: String, val ja: String, val aktion: () -> Unit) : Seite()
}

class Navi {
    val stapel = mutableStateListOf<Seite>(Seite.Gigs)
    val oben: Seite get() = stapel.last()
    fun zu(s: Seite) { stapel.add(s) }
    fun ersetzen(s: Seite) { stapel[stapel.size - 1] = s }
    fun zurueck(): Boolean { if (stapel.size <= 1) return false; stapel.removeAt(stapel.size - 1); return true }
    /** Zur Bühne: liegt sie schon im Stapel, dorthin zurück, sonst obendrauf. */
    fun zurBuehne() {
        val i = stapel.indexOfLast { it == Seite.Buehne }
        if (i >= 0) while (stapel.size > i + 1) stapel.removeAt(stapel.size - 1) else zu(Seite.Buehne)
    }
    fun zurSetliste() {
        val i = stapel.indexOfLast { it == Seite.Setliste }
        if (i >= 0) while (stapel.size > i + 1) stapel.removeAt(stapel.size - 1) else zu(Seite.Setliste)
    }
}

/** Lautstärketasten: die gerade sichtbare Seite meldet sich hier an (true = vor/runter). */
object Tasten {
    var handler: ((vor: Boolean) -> Boolean)? = null
}

@Composable
fun Lautstaerketasten(handler: (vor: Boolean) -> Unit) {
    DisposableEffect(handler) {
        val h: (Boolean) -> Boolean = { handler(it); true }
        Tasten.handler = h
        onDispose { if (Tasten.handler === h) Tasten.handler = null }
    }
}

// ── Wurzel ───────────────────────────────────────────────────────────────────

@Composable
fun Wurzel(navi: Navi, beenden: () -> Unit) {
    BackHandler { if (!navi.zurueck()) beenden() }
    Box(Modifier.fillMaxSize().background(Weiss)) {
        when (val s = navi.oben) {
            Seite.Gigs -> GigsSeite(navi)
            is Seite.Archiv -> ArchivSeite(navi, s.zielSetId)
            is Seite.GigSets -> GigSetsSeite(navi, s.gigId)
            Seite.Setliste -> SetlisteSeite(navi)
            Seite.Buehne -> BuehneSeite(navi)
            is Seite.SongKarte -> SongKarteSeite(navi, s.setId, s.songId)
            is Seite.Editor -> EditorSeite(navi, s.songId, s.reiter)
            is Seite.Einmessen -> EinmessenSeite(navi, s.songId)
            is Seite.Mixer -> MixerSeite(navi, s.songId)
            is Seite.Eingabe -> EingabeSeite(navi, s)
            is Seite.Frage -> FrageSeite(navi, s)
        }
        Meldungen()
    }
}

@Composable
private fun Meldungen() {
    val z by App.buehne.zustand.collectAsState()
    Column(Modifier.fillMaxSize().padding(top = 40.dp), verticalArrangement = Arrangement.Top) {
        val fehler = z.fehler
        if (fehler != null) {
            Row(
                Modifier.fillMaxWidth().padding(10.dp).clip(RoundedCornerShape(14.dp)).background(Weiss)
                    .border(3.dp, Schwarz, RoundedCornerShape(14.dp)).tipp { App.buehne.fehlerWeg() }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Etikett("Problem")
                    T(fehler, textStil(16.sp))
                }
                Symbol(Sym.KREUZ, Schwarz, 18.dp)
            }
        }
    }
    // Hinweise unten über der Bedienleiste: sie verdecken so nie Kopfband, Zurück oder "Fertig".
    // Kein Klick-Empfänger, Tipps gehen durch.
    val h = z.hinweis
    if (h != null) {
        Column(Modifier.fillMaxSize().padding(bottom = 104.dp), verticalArrangement = Arrangement.Bottom) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 10.dp).clip(RoundedCornerShape(14.dp)).background(Schwarz).padding(14.dp)) {
                T(h, textStil(16.sp, Weiss, FontWeight.Bold))
            }
        }
    }
}

// ── Seitenrahmen mit "Jetzt"-Band ────────────────────────────────────────────

/**
 * Liegt ein Song bereit oder läuft, zeigt das schwarze Band oben immer ihn (Tipp = Bühne).
 * Sonst steht dort der Seitentitel.
 */
@Composable
fun Rahmen(navi: Navi, ober: String, titel: String, rechts: (@Composable () -> Unit)? = null, inhalt: @Composable ColumnScope.() -> Unit) {
    val z by App.buehne.zustand.collectAsState()
    val b by App.ablage.stand.collectAsState()
    val song = b.song(z.songId)
    // Sichtbarer Zurück-Knopf auf jeder Seite außer der ersten (Gigs).
    val zurueck: (() -> Unit)? = if (navi.stapel.size > 1) ({ navi.zurueck() }) else null
    Column(Modifier.fillMaxSize()) {
        if (song != null) {
            JetztBand(navi, song.titel)
            Row(
                Modifier.fillMaxWidth().padding(start = if (zurueck != null) 10.dp else 18.dp, end = 10.dp, top = 8.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (zurueck != null) ZurueckKnopf(aufSchwarz = false, onClick = zurueck)
                Column(Modifier.weight(1f)) {
                    Etikett(ober)
                    T(titel, titelStil(24.sp), zeilen = 1)
                }
                if (rechts != null) rechts()
            }
        } else {
            KopfBand(ober, titel, rechts = rechts?.let { r -> { WeissUmrahmt(r) } }, zurueck = zurueck)
        }
        inhalt()
    }
}

@Composable
private fun WeissUmrahmt(inhalt: @Composable () -> Unit) = Box(Modifier.clip(RoundedCornerShape(10.dp)).background(Weiss)) { inhalt() }

@Composable
fun JetztBand(navi: Navi, titel: String) {
    val z by App.buehne.zustand.collectAsState()
    val pos by App.buehne.position.collectAsState()
    val ober = when {
        z.pause != null -> "${z.pause!!.art} · noch ${zeit(z.pause!!.restSek * 1000L)}"
        z.spielt -> "Jetzt · noch ${zeit(z.dauerMs - pos)}"
        z.setDurch != null -> "Set durch · bereit · ${zeit(z.dauerMs)}"
        else -> "Bereit · ${zeit(z.dauerMs)}"
    }
    KopfBand(ober, titel, rechts = { WeisserKnopfImBand("Bühne", Sym.ZEILEN) { navi.zurBuehne() } }, onClick = { navi.zurBuehne() })
}
