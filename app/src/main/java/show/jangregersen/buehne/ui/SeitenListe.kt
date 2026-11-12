package show.jangregersen.buehne.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Liste ohne Scrollen: so viele Zeilen wie auf den Bildschirm passen, Rest auf weiteren Seiten.
 * Blättern mit den Pfeilen unten oder den Lautstärketasten.
 */
@Composable
fun <T> SeitenListe(
    eintraege: List<T>,
    zeilenHoehe: Dp,
    modifier: Modifier = Modifier,
    schluessel: Any? = Unit,
    /** Eintrag, dessen Seite gezeigt werden soll (neues Objekt = neuer Sprung). */
    zeige: Zeige? = null,
    fussHoehe: Dp = 78.dp,
    oben: Dp = 4.dp,
    fuss: @Composable (seite: Int, seiten: Int, proSeite: Int, zurueck: () -> Unit, vor: () -> Unit) -> Unit = { s, n, _, z, v -> Blaetterleiste(s, n, z, v) },
    leer: @Composable () -> Unit = {},
    zeile: @Composable (index: Int, eintrag: T) -> Unit
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val pro = ((maxHeight - fussHoehe - oben) / zeilenHoehe).toInt().coerceAtLeast(1)
        val seiten = maxOf(1, (eintraege.size + pro - 1) / pro)
        // Gemerkt wird der oberste sichtbare Eintrag (nicht die Seitennummer): ändert sich die
        // Zahl der Zeilen pro Seite (z. B. weil oben das Jetzt-Band erscheint), bleibt er sichtbar.
        var anker by remember(schluessel) { mutableIntStateOf(SeitenGedaechtnis.seiten[schluessel] ?: 0) }
        LaunchedEffect(schluessel, zeige) { val i = zeige?.index; if (i != null && i >= 0) anker = i }
        // Die Seite beginnt genau beim Anker (ein A–Z-Sprung landet oben auf der Seite).
        val start = anker.coerceIn(0, (eintraege.size - 1).coerceAtLeast(0))
        val seite = if (start + pro >= eintraege.size) seiten - 1 else ((start + pro - 1) / pro).coerceAtMost(seiten - 1)
        SeitenGedaechtnis.seiten[schluessel] = start
        val zurueck = { if (start > 0) anker = (start - pro).coerceAtLeast(0) }
        val vor = { if (start + pro < eintraege.size) anker = start + pro }
        Lautstaerketasten { v -> if (v) vor() else zurueck() }
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.height(oben))
            if (eintraege.isEmpty()) leer()
            else Column(Modifier.fillMaxWidth().height(zeilenHoehe * pro)) {
                eintraege.subList(start, minOf(eintraege.size, start + pro)).forEachIndexed { i, e -> zeile(start + i, e) }
            }
            Spacer(Modifier.weight(1f))
            fuss(seite, seiten, pro, zurueck, vor)
        }
    }
}

/** Merkt sich pro Liste den obersten sichtbaren Eintrag, solange die App läuft. */
object SeitenGedaechtnis { val seiten = mutableMapOf<Any?, Int>() }

/** Sprungwunsch für [SeitenListe]; absichtlich keine data class, damit jeder Sprung neu ist. */
class Zeige(val index: Int)

@Composable
fun FussZeile(inhalt: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 14.dp)) { inhalt() }
}
