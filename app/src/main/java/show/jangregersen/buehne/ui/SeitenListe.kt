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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import kotlin.math.abs
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Liste ohne Scrollen: so viele Zeilen wie auf den Bildschirm passen, Rest auf weiteren Seiten.
 * Blättern per Wischen nach oben/unten, mit den Pfeilen unten oder den Lautstärketasten.
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
        // 20.11. (Jan: "immer so, dass ich die Übersicht behalte"): Die letzte Zeile einer Seite
        // steht auf der nächsten Seite noch einmal oben. Ein Schritt ist darum eine Zeile kürzer.
        val schritt = if (pro > 2) pro - 1 else pro
        val seiten = if (eintraege.size <= pro) 1 else 1 + (eintraege.size - pro + schritt - 1) / schritt
        // Gemerkt wird der oberste sichtbare Eintrag (nicht die Seitennummer): ändert sich die
        // Zahl der Zeilen pro Seite (z. B. weil oben das Jetzt-Band erscheint), bleibt er sichtbar.
        var anker by remember(schluessel) { mutableIntStateOf(SeitenGedaechtnis.seiten[schluessel] ?: 0) }
        if (zeige?.nurWennVerdeckt == true) {
            // Auswahl in der Liste: Die Seite bleibt stehen (nichts springt unter dem Finger weg).
            // Geblättert wird nur, wenn der Eintrag sonst nicht zu sehen wäre, und dann seitenweise.
            LaunchedEffect(schluessel, zeige, pro) {
                val i = zeige.index
                val s = anker.coerceIn(0, (eintraege.size - 1).coerceAtLeast(0))
                if (i >= 0 && i !in s until s + pro) anker = (s + Math.floorDiv(i - s, schritt) * schritt).coerceAtLeast(0)
            }
        } else LaunchedEffect(schluessel, zeige) { val i = zeige?.index; if (i != null && i >= 0) anker = i }
        // Die Seite beginnt genau beim Anker (ein A–Z-Sprung landet oben auf der Seite).
        val start = anker.coerceIn(0, (eintraege.size - 1).coerceAtLeast(0))
        val seite = if (start + pro >= eintraege.size) seiten - 1 else ((start + schritt - 1) / schritt).coerceAtMost(seiten - 1)
        SeitenGedaechtnis.seiten[schluessel] = start
        val zurueck = { if (start > 0) anker = (start - schritt).coerceAtLeast(0) }
        val vor = { if (start + pro < eintraege.size) anker = start + schritt }
        Lautstaerketasten { v -> if (v) vor() else zurueck() }
        Column(Modifier.fillMaxSize().seitenWischen(onVor = vor, onZurueck = zurueck)) {
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

/** So weit muss der Finger senkrecht wandern, bis ein Wischen eine Seite weiterblättert. */
private val BLAETTER_SCHWELLE = 48.dp

/**
 * Blättern per Wischen (20.11., statt Lautstärketasten): nach oben wischen = nächste Seite,
 * nach unten = vorige. Kein Gleiten (E-Ink): Die Seite springt beim Loslassen. Ist es klar ein
 * senkrechtes Wischen, verbraucht die Liste die Bewegung, dann löst kein Song-Tipp darunter aus.
 * Waagerechtes Wischen bleibt bei den Zeilen (als Nächstes / ans Ende).
 */
@Composable
private fun Modifier.seitenWischen(onVor: () -> Unit, onZurueck: () -> Unit): Modifier {
    val vor by rememberUpdatedState(onVor)
    val zur by rememberUpdatedState(onZurueck)
    val schwelle = with(LocalDensity.current) { BLAETTER_SCHWELLE.toPx() }
    val slop = LocalViewConfiguration.current.touchSlop
    return pointerInput(Unit) {
        awaitEachGesture {
            val runter = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var dx = 0f
            var dy = 0f
            var senkrecht = false
            while (true) {
                val ev = awaitPointerEvent(PointerEventPass.Initial)
                val c = ev.changes.firstOrNull { it.id == runter.id } ?: break
                if (!c.pressed) {
                    if (senkrecht) {
                        c.consume()
                        if (dy < -schwelle) vor() else if (dy > schwelle) zur()
                    }
                    break
                }
                val d = c.positionChange()
                dx += d.x; dy += d.y
                if (!senkrecht) {
                    if (abs(dy) > slop && abs(dy) > abs(dx) * 1.5f) senkrecht = true
                    else if (abs(dx) > slop * 2) break
                }
                if (senkrecht) c.consume()
            }
        }
    }
}

/** Merkt sich pro Liste den obersten sichtbaren Eintrag, solange die App läuft. */
object SeitenGedaechtnis { val seiten = mutableMapOf<Any?, Int>() }

/** Sprungwunsch für [SeitenListe]; absichtlich keine data class, damit jeder Sprung neu ist. */
class Zeige(val index: Int, val nurWennVerdeckt: Boolean = false)

@Composable
fun FussZeile(inhalt: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 14.dp)) { inhalt() }
}
