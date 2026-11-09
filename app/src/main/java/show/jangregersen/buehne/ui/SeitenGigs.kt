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
import show.jangregersen.buehne.daten.Gig
import show.jangregersen.buehne.daten.SetListe
import show.jangregersen.buehne.daten.dauerMs
import show.jangregersen.buehne.daten.gespieltZuruecksetzen
import show.jangregersen.buehne.daten.gigAendern
import show.jangregersen.buehne.daten.mitGig
import show.jangregersen.buehne.daten.ohneGig
import show.jangregersen.buehne.daten.ohneSet
import show.jangregersen.buehne.daten.setAendern
import show.jangregersen.buehne.daten.setSchieben
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

// ── Datum ────────────────────────────────────────────────────────────────────

private val WOCHENTAG = listOf("MO", "DI", "MI", "DO", "FR", "SA", "SO")
private val MONAT = listOf("JAN", "FEB", "MÄR", "APR", "MAI", "JUN", "JUL", "AUG", "SEP", "OKT", "NOV", "DEZ")

fun datumKurz(tag: Long): String {
    if (tag <= 0) return "ohne Datum"
    val d = LocalDate.ofEpochDay(tag)
    return "${d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.GERMAN).take(2)} ${d.dayOfMonth}.${d.monthValue}."
}

fun datumLesen(text: String): Long? {
    val t = text.trim()
    if (t.isEmpty()) return 0L
    val teile = t.split('.', '/', '-', ' ').filter { it.isNotBlank() }.map { it.toIntOrNull() ?: return null }
    if (teile.size < 2) return null
    val jahrJetzt = LocalDate.now().year
    val jahr = when {
        teile.size < 3 -> jahrJetzt
        teile[2] < 100 -> 2000 + teile[2]
        else -> teile[2]
    }
    return runCatching { LocalDate.of(jahr, teile[1], teile[0]).toEpochDay() }.getOrNull()
}

fun datumText(tag: Long): String = if (tag <= 0) "" else LocalDate.ofEpochDay(tag).let { "%02d.%02d.%d".format(it.dayOfMonth, it.monthValue, it.year) }

// ── Gigs ─────────────────────────────────────────────────────────────────────

@Composable
fun GigsSeite(navi: Navi) {
    val b by App.ablage.stand.collectAsState()
    val heute = LocalDate.now().toEpochDay()
    val gigs = b.gigs.sortedWith(compareBy({ if (it.datum <= 0) Long.MAX_VALUE else it.datum }, { it.name.lowercase() }))
    val naechster = gigs.firstOrNull { it.datum >= heute }?.id
    Rahmen(navi, "TrikTrak", "Gigs") {
        Segmente(listOf("Gigs", "Archiv"), 0, Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp)) { if (it == 1) navi.ersetzen(Seite.Archiv()) }
        SeitenListe(
            eintraege = gigs, zeilenHoehe = 88.dp, modifier = Modifier.weight(1f), schluessel = "gigs", oben = 10.dp,
            fuss = { s, n, _, z, v ->
                FussZeile {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextKnopf("Neuer Gig", Modifier.weight(1f), Art.VOLL, symbol = Sym.PLUS) { neuerGig(navi) }
                        if (n > 1) {
                            SymbolKnopf(Sym.LINKS, "Vorige Seite", Modifier.width(56.dp), aktiv = s > 0, onClick = z)
                            SymbolKnopf(Sym.RECHTS, "Nächste Seite", Modifier.width(56.dp), aktiv = s < n - 1, onClick = v)
                        }
                    }
                }
            },
            leer = {
                Column(Modifier.padding(18.dp)) {
                    T("Noch keine Gigs.", titelStil(26.sp))
                    Abstand(6.dp)
                    T("Leg einen Gig an, gib ihm Sets und füll sie mit Songs aus dem Archiv.", textStil(17.sp))
                }
            }
        ) { _, g -> GigKarte(g, g.id == naechster, b.songs.size) { navi.zu(Seite.GigSets(g.id)) } }
    }
}

@Composable
private fun GigKarte(g: Gig, naechster: Boolean, @Suppress("UNUSED_PARAMETER") songsGesamt: Int, onClick: () -> Unit) {
    val b = App.ablage.jetzt
    val songs = g.sets.sumOf { it.eintraege.size }
    val dauer = g.sets.sumOf { it.dauerMs(b) }
    val form = RoundedCornerShape(14.dp)
    Row(
        Modifier.padding(horizontal = 14.dp, vertical = 5.dp).fillMaxWidth().height(78.dp).clip(form)
            .border(if (naechster) 3.dp else 1.5.dp, Schwarz, form).tipp(onClick = onClick).padding(start = 12.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val d = if (g.datum > 0) LocalDate.ofEpochDay(g.datum) else null
        val grund = if (naechster) Schwarz else Weiss
        val vorne = if (naechster) Weiss else Schwarz
        Column(
            Modifier.width(50.dp).height(60.dp).clip(RoundedCornerShape(10.dp)).background(grund).border(2.dp, Schwarz, RoundedCornerShape(10.dp)),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
        ) {
            if (d != null) {
                T(WOCHENTAG[d.dayOfWeek.value - 1], etikettStil(vorne, 11.sp), zeilen = 1)
                T("%02d".format(d.dayOfMonth), titelStil(26.sp, vorne), zeilen = 1)
                T(MONAT[d.monthValue - 1], etikettStil(vorne, 11.sp), zeilen = 1)
            } else T("–", titelStil(26.sp, vorne))
        }
        Column(Modifier.weight(1f)) {
            T(g.name, titelStil(24.sp), zeilen = 1)
            T("${g.sets.size} ${if (g.sets.size == 1) "Set" else "Sets"} · $songs Songs · ${minuten(dauer)}", textStil(14.sp), zeilen = 1)
        }
        Symbol(Sym.RECHTS, Schwarz, 16.dp)
    }
}

private fun neuerGig(navi: Navi) {
    navi.zu(Seite.Eingabe("Neuer Gig", "", hinweis = "Name, zum Beispiel „Sommerfest“") { name ->
        if (name.isBlank()) return@Eingabe
        navi.ersetzen(Seite.Eingabe("Datum für „$name“", "", zahl = true, hinweis = "TT.MM.JJJJ, leer lassen = ohne Datum") { text ->
            val tag = datumLesen(text) ?: 0L
            val a = App.ablage
            val gigId = a.neueId(); val setId = a.neueId()
            a.aendern { it.mitGig(Gig(gigId, name.trim(), tag, listOf(SetListe(setId, "Set 1")))) }
            navi.zurueck()
            navi.zu(Seite.GigSets(gigId))
        })
    })
}

// ── Sets eines Gigs ──────────────────────────────────────────────────────────

@Composable
fun GigSetsSeite(navi: Navi, gigId: Long) {
    val b by App.ablage.stand.collectAsState()
    val z by App.buehne.zustand.collectAsState()
    val gig = b.gig(gigId) ?: run { WegHier(navi); return }
    var menue by remember { mutableStateOf(false) }
    var setMenue by remember { mutableStateOf<Long?>(null) }
    val songs = gig.sets.sumOf { it.eintraege.size }
    Rahmen(navi, "${datumKurz(gig.datum)} · ${gig.sets.size} ${if (gig.sets.size == 1) "Set" else "Sets"} · $songs Songs", gig.name, rechts = {
        SymbolKnopf(Sym.PUNKTE, "Gig bearbeiten", Modifier.width(52.dp), Art.OHNE, hoehe = 44.dp) { menue = true }
    }) {
        if (menue) {
            Menue(listOf(
                "Umbenennen" to { menue = false; navi.zu(Seite.Eingabe("Gig umbenennen", gig.name) { n -> if (n.isNotBlank()) App.ablage.aendern { it.gigAendern(gigId) { g -> g.copy(name = n.trim()) } }; navi.zurueck() }) },
                "Datum ändern" to { menue = false; navi.zu(Seite.Eingabe("Datum", datumText(gig.datum), zahl = true, hinweis = "TT.MM.JJJJ, leer = ohne Datum") { t -> val d = datumLesen(t); if (d != null) App.ablage.aendern { it.gigAendern(gigId) { g -> g.copy(datum = d) } }; navi.zurueck() }) },
                "Gig löschen" to { menue = false; navi.zu(Seite.Frage("Gig löschen?", "„${gig.name}“ mit allen Sets wird gelöscht. Die Songs bleiben im Archiv.", "Löschen") { App.ablage.aendern { it.ohneGig(gigId) }; navi.zurueck(); navi.zurueck() }) }
            )) { menue = false }
            return@Rahmen
        }
        val offen = setMenue?.let { id -> gig.sets.firstOrNull { it.id == id } }
        if (offen != null) {
            val idx = gig.sets.indexOf(offen)
            Menue(listOfNotNull(
                "Umbenennen" to { setMenue = null; navi.zu(Seite.Eingabe("Set umbenennen", offen.name) { n -> if (n.isNotBlank()) App.ablage.aendern { it.setAendern(offen.id) { s -> s.copy(name = n.trim()) } }; navi.zurueck() }) },
                if (idx > 0) "Nach vorne" to { App.ablage.aendern { it.setSchieben(gigId, offen.id, -1) }; setMenue = null } else null,
                if (idx < gig.sets.size - 1) "Nach hinten" to { App.ablage.aendern { it.setSchieben(gigId, offen.id, 1) }; setMenue = null } else null,
                "Alle wieder auf „nicht gespielt“" to { App.ablage.aendern { it.gespieltZuruecksetzen(offen.id) }; setMenue = null },
                "Set löschen" to { setMenue = null; navi.zu(Seite.Frage("Set löschen?", "„${offen.name}“ wird gelöscht. Die Songs bleiben im Archiv.", "Löschen") { App.ablage.aendern { it.ohneSet(offen.id) }; navi.zurueck() }) }
            ), titel = offen.name) { setMenue = null }
            return@Rahmen
        }
        SeitenListe(
            eintraege = gig.sets, zeilenHoehe = 106.dp, modifier = Modifier.weight(1f), schluessel = "sets$gigId", oben = 8.dp,
            fussHoehe = 140.dp,
            fuss = { s, n, _, zz, v ->
                Column {
                    Row(
                        Modifier.padding(horizontal = 14.dp).fillMaxWidth().height(52.dp).tipp { App.ablage.aendern { it.gigAendern(gigId) { g -> g.copy(autoWeiter = !g.autoWeiter) } } },
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Haekchen(gig.autoWeiter)
                        T("Nach dem Set automatisch ins nächste", textStil(17.sp, gewicht = FontWeight.Bold))
                    }
                    FussZeile {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            TextKnopf("Neues Set", Modifier.weight(1f), hoehe = 52.dp, symbol = Sym.PLUS) {
                                val id = App.ablage.neueId()
                                App.ablage.aendern { it.gigAendern(gigId) { g -> g.copy(sets = g.sets + SetListe(id, "Set ${g.sets.size + 1}")) } }
                            }
                            if (n > 1) {
                                SymbolKnopf(Sym.LINKS, "Vorige Seite", Modifier.width(56.dp), hoehe = 52.dp, aktiv = s > 0, onClick = zz)
                                SymbolKnopf(Sym.RECHTS, "Nächste Seite", Modifier.width(56.dp), hoehe = 52.dp, aktiv = s < n - 1, onClick = v)
                            }
                        }
                    }
                }
            }
        ) { i, set ->
            SetKarte(i + 1, set, aktiv = set.id == z.setId, laeuft = set.id == z.setId && z.spielt, onMenue = { setMenue = set.id }) {
                App.buehne.setWaehlen(set.id)
                navi.zu(Seite.Setliste)
            }
        }
    }
}

@Composable
private fun SetKarte(nr: Int, set: SetListe, aktiv: Boolean, laeuft: Boolean, onMenue: () -> Unit, onClick: () -> Unit) {
    val b = App.ablage.jetzt
    val gespielt = set.eintraege.count { it.gespielt }
    val gesamt = set.eintraege.size
    val grund = if (aktiv) Schwarz else Weiss
    val vorne = if (aktiv) Weiss else Schwarz
    val form = RoundedCornerShape(14.dp)
    val etikett = when {
        laeuft -> "läuft"
        aktiv -> "aktiv"
        gesamt > 0 && gespielt == gesamt -> "gespielt"
        else -> ""
    }
    Column(
        Modifier.padding(horizontal = 14.dp, vertical = 5.dp).fillMaxWidth().height(96.dp).clip(form).background(grund)
            .border(2.5.dp, Schwarz, form).tipp(onClick = onClick).padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            T("$nr · ${set.name}", titelStil(25.sp, vorne), Modifier.weight(1f), zeilen = 1)
            if (etikett.isNotEmpty()) Etikett(etikett, farbe = vorne)
            Box(Modifier.size(40.dp).tipp(onClick = onMenue), contentAlignment = Alignment.Center) { Symbol(Sym.PUNKTE, vorne, 20.dp) }
        }
        Kaestchen(12, if (gesamt == 0) 0 else (gespielt * 12 + gesamt - 1) / gesamt, vorne, Modifier.padding(end = 8.dp), 8.dp)
        val rest = set.dauerMs(b, nurOffene = true)
        T(if (gesamt == 0) "noch leer" else "$gespielt von $gesamt · ${if (gespielt in 1 until gesamt) "noch " else ""}${minuten(if (gespielt > 0) rest else set.dauerMs(b))}", textStil(15.sp, vorne), zeilen = 1)
    }
}

/** Seite ohne Inhalt mehr (z. B. gelöscht): sicher zurück, außerhalb der Komposition. */
@Composable
fun WegHier(navi: Navi) {
    androidx.compose.runtime.LaunchedEffect(Unit) { navi.zurueck() }
}

@Composable
fun Haekchen(an: Boolean) {
    val form = RoundedCornerShape(6.dp)
    Box(Modifier.size(28.dp).clip(form).background(if (an) Schwarz else Weiss).border(2.5.dp, Schwarz, form), contentAlignment = Alignment.Center) {
        if (an) Symbol(Sym.HAKEN, Weiss, 16.dp)
    }
}

/** Einfaches Menü als ganze Seite: große Zeilen, kein schwebendes Fenster (ruhig auf E-Ink). */
@Composable
fun Menue(punkte: List<Pair<String, () -> Unit>>, titel: String? = null, schliessen: () -> Unit) {
    Lautstaerketasten { }
    Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (titel != null) T(titel, titelStil(24.sp), Modifier.padding(start = 4.dp, bottom = 4.dp))
        punkte.forEach { (text, aktion) -> TextKnopf(text, Modifier.fillMaxWidth(), hoehe = 54.dp, onClick = aktion) }
        TextKnopf("Abbrechen", Modifier.fillMaxWidth(), Art.GESTRICHELT, hoehe = 54.dp, onClick = schliessen)
    }
}
