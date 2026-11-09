package show.jangregersen.buehne.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import show.jangregersen.buehne.R

// ── Schrift und Farbe: nur Schwarz und Weiß ─────────────────────────────────

val Schwarz = Color.Black
val Weiss = Color.White

val Schmal = FontFamily(
    Font(R.font.barlow_cond_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_cond_bold, FontWeight.Bold),
    Font(R.font.barlow_cond_extrabold, FontWeight.ExtraBold)
)
val Halbschmal = FontFamily(
    Font(R.font.barlow_semi_medium, FontWeight.Medium),
    Font(R.font.barlow_semi_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_semi_bold, FontWeight.Bold)
)

fun titelStil(groesse: TextUnit, farbe: Color = Schwarz, gewicht: FontWeight = FontWeight.Bold) =
    TextStyle(fontFamily = Schmal, fontWeight = gewicht, fontSize = groesse, color = farbe, lineHeight = groesse * 1.05f)

fun textStil(groesse: TextUnit, farbe: Color = Schwarz, gewicht: FontWeight = FontWeight.SemiBold) =
    TextStyle(fontFamily = Halbschmal, fontWeight = gewicht, fontSize = groesse, color = farbe, lineHeight = groesse * 1.22f)

fun etikettStil(farbe: Color = Schwarz, groesse: TextUnit = 11.sp) =
    TextStyle(fontFamily = Halbschmal, fontWeight = FontWeight.Bold, fontSize = groesse, color = farbe, letterSpacing = 0.16.em)

@Composable
fun T(text: String, stil: TextStyle, modifier: Modifier = Modifier, zeilen: Int = Int.MAX_VALUE, mitte: Boolean = false, durchgestrichen: Boolean = false) {
    BasicText(
        text = text,
        style = stil.copy(
            textAlign = if (mitte) TextAlign.Center else stil.textAlign,
            textDecoration = if (durchgestrichen) TextDecoration.LineThrough else null
        ),
        modifier = modifier,
        maxLines = zeilen,
        overflow = if (zeilen == Int.MAX_VALUE) TextOverflow.Clip else TextOverflow.Ellipsis
    )
}

@Composable
fun Etikett(text: String, modifier: Modifier = Modifier, farbe: Color = Schwarz) =
    T(text.uppercase(), etikettStil(farbe), modifier, zeilen = 1)

// ── Tippen ohne Wellen-Effekt; gedrückt = invertiert (E-Ink-Rückmeldung) ─────

@Composable
fun Modifier.tipp(aktiv: Boolean = true, onClick: () -> Unit): Modifier {
    val quelle = remember { MutableInteractionSource() }
    return this.clickable(interactionSource = quelle, indication = null, enabled = aktiv, onClick = onClick)
}

enum class Art { UMRISS, VOLL, GESTRICHELT, DUENN, OHNE }

@Composable
fun Knopf(
    modifier: Modifier = Modifier,
    art: Art = Art.UMRISS,
    hoehe: Dp = 56.dp,
    radius: Dp = 14.dp,
    aktiv: Boolean = true,
    beschreibung: String? = null,
    onClick: () -> Unit,
    inhalt: @Composable RowScope.(farbe: Color) -> Unit
) {
    val quelle = remember { MutableInteractionSource() }
    val gedrueckt by quelle.collectIsPressedAsState()
    val voll = (art == Art.VOLL) != gedrueckt
    val grund = if (voll) Schwarz else Weiss
    val vorne = if (voll) Weiss else Schwarz
    val form = RoundedCornerShape(radius)
    var m = modifier.height(hoehe).clip(form).background(grund)
    m = when (art) {
        Art.UMRISS, Art.VOLL -> m.border(2.5.dp, Schwarz, form)
        Art.DUENN -> m.border(1.5.dp, Schwarz, form)
        Art.GESTRICHELT -> m.gestrichelt(radius)
        Art.OHNE -> m
    }
    Row(
        modifier = m
            .clickable(interactionSource = quelle, indication = null, enabled = aktiv, onClickLabel = beschreibung, onClick = onClick)
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) { inhalt(if (aktiv) vorne else vorne.copy(alpha = 0.35f)) }
}

@Composable
fun TextKnopf(text: String, modifier: Modifier = Modifier, art: Art = Art.UMRISS, hoehe: Dp = 56.dp, groesse: TextUnit = 17.sp, symbol: Sym? = null, aktiv: Boolean = true, onClick: () -> Unit) {
    Knopf(modifier, art, hoehe, aktiv = aktiv, onClick = onClick) { f ->
        if (symbol != null) Symbol(symbol, f, 20.dp)
        T(text, textStil(groesse, f, FontWeight.Bold), zeilen = 1)
    }
}

@Composable
fun SymbolKnopf(sym: Sym, beschreibung: String, modifier: Modifier = Modifier, art: Art = Art.UMRISS, hoehe: Dp = 56.dp, groesse: Dp = 22.dp, aktiv: Boolean = true, onClick: () -> Unit) {
    Knopf(modifier, art, hoehe, aktiv = aktiv, beschreibung = beschreibung, onClick = onClick) { f -> Symbol(sym, f, groesse) }
}

fun Modifier.gestrichelt(radius: Dp, breite: Dp = 1.5.dp): Modifier = this.drawWithStrich(radius, breite)

private fun Modifier.drawWithStrich(radius: Dp, breite: Dp): Modifier = this.drawBehind {
    val w = breite.toPx()
    drawRoundRect(
        color = Schwarz,
        topLeft = Offset(w / 2, w / 2),
        size = Size(size.width - w, size.height - w),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius.toPx()),
        style = Stroke(width = w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
    )
}

@Composable
fun Linie(modifier: Modifier = Modifier, dicke: Dp = 1.dp) =
    Box(modifier.fillMaxWidth().height(dicke).background(Schwarz))

// ── Symbole (selbst gezeichnet, Strichstärke wie Mudita) ────────────────────

enum class Sym { PLAY, PAUSE, STOP, WEITER, LISTE, MIKRO, HAKEN, PLUS, MINUS, KREUZ, LINKS, RECHTS, HOCH, RUNTER, ORDNER, PUNKTE, ZEILEN, AUFNAHME, ZURUECK10 }

@Composable
fun Symbol(sym: Sym, farbe: Color, groesse: Dp = 22.dp) {
    Canvas(Modifier.size(groesse)) { zeichne(sym, farbe) }
}

private fun DrawScope.zeichne(sym: Sym, f: Color) {
    val w = size.width; val h = size.height
    val s = w * 0.12f
    val strich = Stroke(width = s, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun pfad(vararg p: Pair<Float, Float>, zu: Boolean = false, fuellen: Boolean = false) {
        val pa = Path().apply { moveTo(p[0].first * w, p[0].second * h); p.drop(1).forEach { lineTo(it.first * w, it.second * h) }; if (zu) close() }
        if (fuellen) drawPath(pa, f) else drawPath(pa, f, style = strich)
    }
    when (sym) {
        Sym.PLAY -> pfad(0.2f to 0.1f, 0.88f to 0.5f, 0.2f to 0.9f, zu = true, fuellen = true)
        Sym.PAUSE -> { drawRect(f, Offset(0.15f * w, 0.08f * h), Size(0.25f * w, 0.84f * h)); drawRect(f, Offset(0.6f * w, 0.08f * h), Size(0.25f * w, 0.84f * h)) }
        Sym.STOP -> drawRoundRect(f, Offset(0.12f * w, 0.12f * h), Size(0.76f * w, 0.76f * h), androidx.compose.ui.geometry.CornerRadius(0.08f * w))
        Sym.WEITER -> { pfad(0.08f to 0.12f, 0.62f to 0.5f, 0.08f to 0.88f, zu = true, fuellen = true); drawRect(f, Offset(0.7f * w, 0.12f * h), Size(0.2f * w, 0.76f * h)) }
        Sym.LISTE -> {
            for (y in listOf(0.2f, 0.5f, 0.8f)) { drawCircle(f, 0.07f * w, Offset(0.1f * w, y * h)); pfad(0.32f to y, 0.92f to y) }
        }
        Sym.ZEILEN -> { pfad(0.1f to 0.2f, 0.9f to 0.2f); pfad(0.1f to 0.5f, 0.9f to 0.5f); pfad(0.1f to 0.8f, 0.6f to 0.8f) }
        Sym.MIKRO -> {
            drawRoundRect(f, Offset(0.32f * w, 0.05f * h), Size(0.36f * w, 0.52f * h), androidx.compose.ui.geometry.CornerRadius(0.18f * w), style = Stroke(s))
            drawArc(f, 0f, 180f, false, Offset(0.15f * w, 0.18f * h), Size(0.7f * w, 0.55f * h), style = strich)
            pfad(0.5f to 0.74f, 0.5f to 0.95f)
        }
        Sym.HAKEN -> pfad(0.12f to 0.52f, 0.4f to 0.8f, 0.9f to 0.2f)
        Sym.PLUS -> { pfad(0.5f to 0.1f, 0.5f to 0.9f); pfad(0.1f to 0.5f, 0.9f to 0.5f) }
        Sym.MINUS -> pfad(0.1f to 0.5f, 0.9f to 0.5f)
        Sym.KREUZ -> { pfad(0.15f to 0.15f, 0.85f to 0.85f); pfad(0.85f to 0.15f, 0.15f to 0.85f) }
        Sym.LINKS -> pfad(0.68f to 0.1f, 0.3f to 0.5f, 0.68f to 0.9f)
        Sym.RECHTS -> pfad(0.32f to 0.1f, 0.7f to 0.5f, 0.32f to 0.9f)
        Sym.HOCH -> pfad(0.1f to 0.7f, 0.5f to 0.3f, 0.9f to 0.7f)
        Sym.RUNTER -> pfad(0.1f to 0.3f, 0.5f to 0.7f, 0.9f to 0.3f)
        Sym.ORDNER -> pfad(0.08f to 0.25f, 0.4f to 0.25f, 0.5f to 0.35f, 0.92f to 0.35f, 0.92f to 0.82f, 0.08f to 0.82f, zu = true)
        Sym.PUNKTE -> for (x in listOf(0.18f, 0.5f, 0.82f)) drawCircle(f, 0.1f * w, Offset(x * w, 0.5f * h))
        Sym.AUFNAHME -> drawCircle(f, 0.4f * w, Offset(0.5f * w, 0.5f * h))
        Sym.ZURUECK10 -> { pfad(0.55f to 0.15f, 0.2f to 0.5f, 0.55f to 0.85f); pfad(0.9f to 0.15f, 0.55f to 0.5f, 0.9f to 0.85f) }
    }
}

// ── Kästchen-Fortschritt ─────────────────────────────────────────────────────

@Composable
fun Kaestchen(anzahl: Int, voll: Int, farbe: Color, modifier: Modifier = Modifier, hoehe: Dp = 6.dp) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(anzahl) { i ->
            val m = Modifier.weight(1f).height(hoehe)
            Box(if (i < voll) m.background(farbe) else m.border(1.5.dp, farbe))
        }
    }
}

// ── Kopfband und Blätterleiste ───────────────────────────────────────────────

@Composable
fun KopfBand(ober: String, titel: String, rechts: (@Composable () -> Unit)? = null, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().background(Schwarz)
            .then(if (onClick != null) Modifier.tipp(onClick = onClick) else Modifier)
            .padding(start = 18.dp, end = 10.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Etikett(ober, farbe = Weiss)
            T(titel, titelStil(26.sp, Weiss), zeilen = 1)
        }
        if (rechts != null) rechts()
    }
}

@Composable
fun WeisserKnopfImBand(text: String, symbol: Sym? = null, onClick: () -> Unit) {
    Row(
        Modifier.height(44.dp).clip(RoundedCornerShape(10.dp)).background(Weiss).border(2.5.dp, Schwarz, RoundedCornerShape(10.dp)).tipp(onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        if (symbol != null) Symbol(symbol, Schwarz, 18.dp)
        T(text, textStil(16.sp, Schwarz, FontWeight.Bold), zeilen = 1)
    }
}

@Composable
fun Blaetterleiste(seite: Int, seiten: Int, zurueck: () -> Unit, vor: () -> Unit, mitteText: String? = null, mitteKlein: String? = null, onMitte: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SymbolKnopf(Sym.LINKS, "Vorige Seite", Modifier.width(64.dp), aktiv = seite > 0, onClick = zurueck)
        val mitte = Modifier.weight(1f).height(56.dp)
        Column(
            (if (onMitte != null) mitte.clip(RoundedCornerShape(14.dp)).gestrichelt(14.dp).tipp(onClick = onMitte) else mitte),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            T(mitteText ?: "Seite ${seite + 1} von ${maxOf(1, seiten)}", textStil(17.sp, gewicht = FontWeight.Bold), zeilen = 1)
            if (mitteKlein != null) T(mitteKlein, textStil(12.sp), zeilen = 1)
            else if (seiten in 2..8) Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(seiten) { i ->
                    val m = Modifier.width(18.dp).height(5.dp).clip(RoundedCornerShape(3.dp))
                    Box(if (i == seite) m.background(Schwarz) else m.border(1.5.dp, Schwarz, RoundedCornerShape(3.dp)))
                }
            }
        }
        SymbolKnopf(Sym.RECHTS, "Nächste Seite", Modifier.width(64.dp), art = Art.VOLL, aktiv = seite < seiten - 1, onClick = vor)
    }
}

@Composable
fun Abstand(h: Dp) = Spacer(Modifier.height(h))

@Composable
fun Segmente(optionen: List<String>, gewaehlt: Int, modifier: Modifier = Modifier, hoehe: Dp = 44.dp, groesse: TextUnit = 16.sp, onWahl: (Int) -> Unit) {
    val form = RoundedCornerShape(12.dp)
    Row(modifier.fillMaxWidth().height(hoehe).clip(form).border(2.5.dp, Schwarz, form)) {
        optionen.forEachIndexed { i, o ->
            val an = i == gewaehlt
            if (i > 0) Box(Modifier.width(2.dp).height(hoehe).background(Schwarz))
            Box(Modifier.weight(1f).height(hoehe).background(if (an) Schwarz else Weiss).tipp { onWahl(i) }, contentAlignment = Alignment.Center) {
                T(o, textStil(groesse, if (an) Weiss else Schwarz, FontWeight.Bold), zeilen = 1)
            }
        }
    }
}

fun zeit(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

fun minuten(ms: Long): String {
    val min = ((ms + 30_000) / 60_000).toInt()
    return if (min >= 60) "%d:%02d h".format(min / 60, min % 60) else "$min min"
}
