package show.jangregersen.buehne.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import show.jangregersen.buehne.App
import show.jangregersen.buehne.daten.Eintrag
import show.jangregersen.buehne.daten.Ende
import show.jangregersen.buehne.daten.Song
import show.jangregersen.buehne.daten.dauerMs
import show.jangregersen.buehne.daten.naechsterNach
import show.jangregersen.buehne.daten.schonImGig
import show.jangregersen.buehne.daten.wasKommt
import show.jangregersen.buehne.daten.wiederholungsFrage
import java.text.Normalizer
import kotlin.math.abs

// ── Hauptbildschirm (15.11. abends, Jans Wunsch: Bedienung wie die alte App) ──
//
// Ein Bildschirm für den ganzen Abend: oben die Reiter „Set“ und „Archiv“, in der Mitte die
// Liste, unten immer die Player-Leiste. Optik wie Fassung 7 (Jan: „von den Textgrößen super“).
// Wischen nach rechts = kommt als Nächstes, nach links = ans Ende vom Set (wie in der alten App).
// Antippen eines Songs öffnet seine Einstellungen und startet nie Musik; Play gibt es nur unten.
// Der Entwurf dazu liegt in entwuerfe/wie-alt/.

private const val REITER_SET = 0
private const val REITER_ARCHIV = 1

/** Gewählter Reiter, solange die App läuft (die Seite selbst verschwindet, wenn eine andere oben liegt). */
object HauptZustand { var reiter by androidx.compose.runtime.mutableIntStateOf(REITER_SET) }

/** Ungefähre Höhe der Player-Leiste: Hinweise erscheinen knapp darüber. */
val PLAYER_HOEHE = 190.dp

/** So weit muss der Finger waagerecht wandern, bis ein Wischen zählt. */
private val WISCH_SCHWELLE = 64.dp
/** So weit rückt die Zeile zur Seite, sobald das Wischen zählt (E-Ink: nur zwei Bilder, kein Gleiten). */
private val WISCH_VERSATZ = 170.dp

@Composable
fun HauptSeite(navi: Navi) {
    val b by App.ablage.stand.collectAsState()
    val z by App.buehne.zustand.collectAsState()
    // Der Reiter bleibt gewählt, auch wenn man kurz eine andere Seite öffnet (Einstellungen, Rückfrage).
    var reiter by HauptZustand::reiter
    var klappe by remember { mutableStateOf(false) }
    val set = b.set(z.setId)
    val gig = b.gigVonSet(z.setId)

    // Zurück schließt zuerst die Set-Klappe bzw. führt vom Archiv zurück zum Set.
    BackHandler(enabled = klappe || reiter == REITER_ARCHIV) { if (klappe) klappe = false else reiter = REITER_SET }

    Column(Modifier.fillMaxSize()) {
        val ober = when {
            reiter == REITER_ARCHIV && set != null -> "Wischen plant in „${set.name}“ ein"
            reiter == REITER_ARCHIV -> "Archiv · ${b.songs.size} Songs"
            set != null && gig != null -> "${gig.name} · ${set.eintraege.count { it.gespielt }} von ${set.eintraege.size} gespielt · noch ${minuten(set.dauerMs(b, nurOffene = true))}"
            set != null -> "${set.eintraege.size} Songs"
            else -> "Kein Set gewählt"
        }
        HauptKopf(
            ober, set?.name ?: "Set", reiter, klappe,
            onSet = { if (reiter != REITER_SET) { reiter = REITER_SET; klappe = false } else klappe = !klappe },
            onArchiv = { reiter = REITER_ARCHIV; klappe = false }
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                reiter == REITER_ARCHIV -> ArchivBereich(navi, set?.id)
                set == null -> KeinSet(navi)
                else -> SetBereich(navi, set.id)
            }
            if (klappe && reiter == REITER_SET) SetKlappe(navi) { klappe = false }
            z.rueckgaengig?.let { RueckBand(it) }
        }
        PlayerLeiste(navi)
    }
}

// ── Kopf mit zwei Reitern ────────────────────────────────────────────────────

@Composable
private fun HauptKopf(ober: String, setName: String, reiter: Int, klappe: Boolean, onSet: () -> Unit, onArchiv: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(Schwarz).padding(start = 10.dp, end = 10.dp, top = 7.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Etikett(ober, Modifier.padding(start = 4.dp), Weiss)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Reiter(Modifier.weight(1.5f), reiter == REITER_SET, onSet) { f ->
                T(setName, titelStil(25.sp, f), Modifier.weight(1f, fill = false), zeilen = 1)
                Symbol(if (klappe) Sym.HOCH else Sym.RUNTER, f, 16.dp)
            }
            Reiter(Modifier.weight(1f), reiter == REITER_ARCHIV, onArchiv) { f -> T("Archiv", titelStil(25.sp, f), zeilen = 1) }
        }
    }
}

@Composable
private fun Reiter(modifier: Modifier, an: Boolean, onClick: () -> Unit, inhalt: @Composable RowScope.(Color) -> Unit) {
    val form = RoundedCornerShape(12.dp)
    Row(
        modifier.height(44.dp).clip(form).background(if (an) Weiss else Schwarz).border(2.5.dp, Weiss, form)
            .tipp(onClick = onClick).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
    ) { inhalt(if (an) Schwarz else Weiss) }
}

// ── Reiter „Set“ ─────────────────────────────────────────────────────────────

@Composable
private fun SetBereich(navi: Navi, setId: Long) {
    val b by App.ablage.stand.collectAsState()
    val z by App.buehne.zustand.collectAsState()
    val set = b.set(setId) ?: return
    // Jeder Besuch beginnt auf Seite 1, also bei dem, was als Nächstes kommt.
    val besuch = remember(setId) { System.nanoTime() }
    if (set.eintraege.isEmpty()) {
        Column(Modifier.padding(18.dp)) {
            T("Dieses Set ist noch leer.", titelStil(26.sp))
            Abstand(12.dp)
            TextKnopf("Songs dazu", Modifier.fillMaxWidth(), Art.VOLL, symbol = Sym.PLUS) { navi.zu(Seite.SetBearbeiten(set.id)) }
        }
        return
    }
    // Der laufende bzw. bereite Song steht unten in der Leiste, nicht in der Liste.
    val kommt = set.wasKommt(z.songId).filter { it.songId != z.songId }
    val gespielt = set.eintraege.filter { it.gespielt && it.songId != z.songId }
    val folgeSet = b.set(z.setDanach)
    // "hält an" nur zeigen, wenn es im Set vom Üblichen abweicht (sonst stünde es überall).
    val mitAutomatik = set.eintraege.any { it.ende == Ende.WEITER }
    Column(Modifier.fillMaxSize()) {
        z.setDurch?.let { SetEndeHinweis(it) }
        if (folgeSet != null) {
            Row(Modifier.padding(start = 10.dp, end = 10.dp, top = 6.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Schwarz).padding(horizontal = 14.dp, vertical = 8.dp)) {
                T("Nach diesem Song beginnt „${folgeSet.name}“", textStil(16.sp, Weiss, FontWeight.Bold), zeilen = 2)
            }
        }
        SeitenListe(
            eintraege = kommt + gespielt, zeilenHoehe = 64.dp, modifier = Modifier.weight(1f), schluessel = "haupt$setId·$besuch",
            fussHoehe = FUSS_HOEHE, oben = 8.dp,
            fuss = { s, n, _, zur, vor -> SeitenFuss(s, n, zur, vor, if (n > 1) "Seite ${s + 1} von $n · Lautstärketasten" else null) },
            leer = { T("Alle Songs sind gespielt.", titelStil(24.sp), Modifier.padding(18.dp)) }
        ) { i, e ->
            val s = b.song(e.songId) ?: return@SeitenListe
            val nr = set.eintraege.indexOfFirst { it.songId == e.songId } + 1
            key(e.songId) {
                if (e.gespielt) {
                    // Gespielt: nach rechts wischen spielt ihn nochmal (mit Rückfrage), links gibt es nicht.
                    WischZeile(e.songId, rechts = "NOCHMAL", links = null, onRechts = { einplanen(navi, setId, s, rechts = true) }, onLinks = {}) {
                        LiveZeile(nr, s, e, naechster = false, haeltAnZeigen = mitAutomatik) { navi.zu(Seite.SongKarte(setId, s.id)) }
                    }
                } else {
                    WischZeile(e.songId, rechts = "ALS NÄCHSTES", links = "ANS ENDE",
                        onRechts = { App.buehne.vorziehen(setId, s.id) }, onLinks = { App.buehne.ansEnde(setId, s.id) }) {
                        LiveZeile(nr, s, e, naechster = i == 0 && folgeSet == null, haeltAnZeigen = mitAutomatik) { navi.zu(Seite.SongKarte(setId, s.id)) }
                    }
                }
            }
        }
    }
}

/** Eine Zeile der Setliste, Aussehen wie Fassung 7. [naechster]: oben dicke Linie mit „Als Nächstes“. */
@Composable
private fun LiveZeile(nr: Int, s: Song, e: Eintrag, naechster: Boolean, haeltAnZeigen: Boolean, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(64.dp)) {
        Row(
            Modifier.padding(horizontal = 10.dp).fillMaxWidth().height(63.dp)
                .then(if (naechster) Modifier.drawBehind { drawRect(Schwarz, size = androidx.compose.ui.geometry.Size(size.width, 2.5.dp.toPx())) } else Modifier)
                .tipp(onClick = onClick).padding(start = 8.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(Modifier.width(26.dp), contentAlignment = Alignment.Center) {
                if (e.gespielt) Box(Modifier.size(22.dp).clip(RoundedCornerShape(11.dp)).background(Schwarz), contentAlignment = Alignment.Center) { Symbol(Sym.HAKEN, Weiss, 13.dp) }
                else T("$nr", titelStil(22.sp))
            }
            // Zweite Zeile: das Pausen-Kästchen (Tipp = nächste Pause), dazu Ansage und "hält an",
            // wenn es vom Üblichen abweicht. Gespielte Songs zeigen nur den Titel.
            val haeltAn = haeltAnZeigen && e.ende == Ende.ANHALTEN
            val ansage = s.ansageVorher.isNotBlank() || s.ansageNachher.isNotBlank()
            val zeile2 = !e.gespielt || haeltAn || ansage
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (zeile2) PassenderTitel(s.titel, { textStil(it) }, 22, 15, durchgestrichen = e.gespielt)
                else T(s.titel, textStil(22.sp).copy(lineHeight = 24.sp), zeilen = 2, durchgestrichen = e.gespielt)
                if (zeile2) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (ansage) Symbol(Sym.MIKRO, Schwarz, 15.dp)
                    if (!e.gespielt) PausenKaestchen(s, Schwarz)
                    if (haeltAn) Box(Modifier.border(1.5.dp, Schwarz, RoundedCornerShape(6.dp)).padding(horizontal = 5.dp, vertical = 1.dp)) { T("hält an", textStil(14.sp, gewicht = FontWeight.Bold), zeilen = 1) }
                }
            }
            T(zeit(s.dauerMs), titelStil(20.sp, gewicht = FontWeight.SemiBold), Modifier.width(44.dp), zeilen = 1)
        }
        Linie(Modifier.align(Alignment.BottomCenter).padding(horizontal = 10.dp), dicke = if (naechster) 2.5.dp else 1.dp)
        if (naechster) {
            Box(Modifier.align(Alignment.TopStart).padding(start = 44.dp).offset(y = (-7).dp).background(Weiss).padding(horizontal = 5.dp)) {
                T("ALS NÄCHSTES", etikettStil(groesse = 11.sp), zeilen = 1)
            }
        }
    }
}

/**
 * Wischen nach rechts oder links, wie in der alten App, aber für E-Ink: Die Zeile gleitet nicht
 * mit dem Finger, sondern springt einmal zur Seite, sobald das Wischen zählt; dahinter steht, was
 * beim Loslassen passiert. Nur klar waagerechtes Wischen zählt, ein Tipp bleibt ein Tipp.
 *
 * Gegen den Fehler der alten App ("trifft nach ein paar Mal den falschen Song"): Die Geste hängt
 * an [schluessel] (der Song-ID) und ruft die Aktionen immer in ihrer neuesten Fassung auf.
 */
@Composable
fun WischZeile(schluessel: Long, rechts: String?, links: String?, onRechts: () -> Unit, onLinks: () -> Unit, inhalt: @Composable () -> Unit) {
    var richtung by remember(schluessel) { mutableIntStateOf(0) }
    val r by rememberUpdatedState(onRechts)
    val l by rememberUpdatedState(onLinks)
    val rechtsAn by rememberUpdatedState(rechts != null)
    val linksAn by rememberUpdatedState(links != null)
    val schwelle = with(LocalDensity.current) { WISCH_SCHWELLE.toPx() }
    val slop = LocalViewConfiguration.current.touchSlop
    Box(
        Modifier.fillMaxWidth().clipToBounds().pointerInput(schluessel) {
            awaitEachGesture {
                // Initial: Diese Zeile sieht den Finger vor ihren Knöpfen. Ist es ein Wischen,
                // verbraucht sie die Bewegung, dann löst kein Knopf darunter mehr aus.
                val runter = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                var dx = 0f
                var dy = 0f
                var waagerecht = false
                while (true) {
                    val ev = awaitPointerEvent(PointerEventPass.Initial)
                    val c = ev.changes.firstOrNull { it.id == runter.id } ?: break
                    if (!c.pressed) {
                        if (waagerecht) {
                            c.consume()
                            val ri = richtung
                            richtung = 0
                            if (ri > 0) r() else if (ri < 0) l()
                        }
                        break
                    }
                    val d = c.positionChange()
                    dx += d.x; dy += d.y
                    if (!waagerecht) {
                        if (abs(dx) > slop && abs(dx) > abs(dy) * 1.5f) waagerecht = true
                        else if (abs(dy) > slop * 2) break
                    }
                    if (waagerecht) {
                        c.consume()
                        val neu = when { dx > schwelle && rechtsAn -> 1; dx < -schwelle && linksAn -> -1; else -> 0 }
                        if (neu != richtung) richtung = neu
                    }
                }
                if (richtung != 0) richtung = 0
            }
        }
    ) {
        if (richtung != 0) {
            Row(
                Modifier.matchParentSize().background(Schwarz).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp, if (richtung > 0) Alignment.Start else Alignment.End)
            ) {
                if (richtung > 0) { Symbol(Sym.RECHTS, Weiss, 16.dp); T(rechts ?: "", titelStil(22.sp, Weiss), zeilen = 1) }
                else { T(links ?: "", titelStil(22.sp, Weiss), zeilen = 1); Symbol(Sym.LINKS, Weiss, 16.dp) }
            }
        }
        val versatz = when { richtung > 0 -> WISCH_VERSATZ; richtung < 0 -> -WISCH_VERSATZ; else -> 0.dp }
        Box(Modifier.fillMaxWidth().offset(x = versatz).background(Weiss)) { inhalt() }
    }
}

/** Einplanen per Wischen, mit Rückfrage, wenn der Song heute schon gespielt oder anderswo eingeplant ist. */
private fun einplanen(navi: Navi, setId: Long, s: Song, rechts: Boolean) {
    val tun = { if (rechts) App.buehne.vorziehen(setId, s.id) else App.buehne.ansEnde(setId, s.id) }
    val frage = App.ablage.jetzt.wiederholungsFrage(setId, s.id)
    if (frage == null) { tun(); return }
    navi.zu(Seite.Frage(
        if (rechts) "Nochmal spielen?" else "Nochmal einplanen?",
        "„${s.titel}“ $frage. Willst du ihn trotzdem ${if (rechts) "als Nächstes spielen" else "ans Ende vom Set setzen"}?",
        if (rechts) "Ja, als Nächstes" else "Ja, ans Ende"
    ) { navi.zurueck(); tun() })
}

@Composable
private fun KeinSet(navi: Navi) {
    Column(Modifier.padding(18.dp)) {
        T("Noch kein Set gewählt.", titelStil(26.sp))
        Abstand(8.dp)
        T("Wähle einen Gig und darin ein Set.", textStil(17.sp))
        Abstand(14.dp)
        TextKnopf("Zu den Gigs", Modifier.fillMaxWidth(), Art.VOLL) { navi.stapel.clear(); navi.stapel.add(Seite.Gigs) }
    }
}

/** Set wechseln: klappt unter dem Reiter auf. Tipp = dieses Set (läuft ein Song, erst danach). */
@Composable
private fun SetKlappe(navi: Navi, onZu: () -> Unit) {
    val b by App.ablage.stand.collectAsState()
    val z by App.buehne.zustand.collectAsState()
    val gig = b.gigVonSet(z.setId)
    val set = b.set(z.setId)
    val form = RoundedCornerShape(14.dp)
    Box(Modifier.fillMaxSize().background(Weiss.copy(alpha = 0.8f)).tipp(onClick = onZu)) {
        Column(
            Modifier.padding(10.dp).fillMaxWidth().clip(form).background(Weiss).border(3.dp, Schwarz, form).tipp { }.padding(vertical = 6.dp)
        ) {
            Etikett(if (gig != null) "Sets · ${gig.name}" else "Sets", Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
            gig?.sets?.forEach { s ->
                val aktiv = s.id == z.setId
                val gesp = s.eintraege.count { it.gespielt }
                val text = when {
                    s.id == z.setDanach -> "beginnt nach diesem Song"
                    aktiv -> "aktiv · $gesp von ${s.eintraege.size} gespielt"
                    gesp > 0 -> "$gesp von ${s.eintraege.size} gespielt · noch ${minuten(s.dauerMs(b, nurOffene = true))}"
                    else -> "${s.eintraege.size} Songs · ${minuten(s.dauerMs(b))}"
                }
                val f = if (aktiv) Weiss else Schwarz
                Row(
                    Modifier.padding(horizontal = 8.dp, vertical = 2.dp).fillMaxWidth().height(56.dp).clip(RoundedCornerShape(10.dp))
                        .background(if (aktiv) Schwarz else Weiss).tipp { App.buehne.setWaehlen(s.id); onZu() }.padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        T(s.name, titelStil(24.sp, f), zeilen = 1)
                        T(text, textStil(14.sp, f), zeilen = 1)
                    }
                    if (aktiv) Symbol(Sym.HAKEN, Weiss, 18.dp)
                }
                if (!aktiv) Linie(Modifier.padding(horizontal = 14.dp))
            }
            Row(Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (set != null) TextKnopf("Set bearbeiten", Modifier.weight(1f), hoehe = 48.dp, groesse = 16.sp) { onZu(); navi.zu(Seite.SetBearbeiten(set.id)) }
                TextKnopf("Alle Gigs", Modifier.weight(1f), hoehe = 48.dp, groesse = 16.sp) {
                    onZu()
                    if (gig != null) navi.zumGig(gig.id) else { navi.stapel.clear(); navi.stapel.add(Seite.Gigs) }
                }
            }
        }
    }
}

/** 8 s nach dem Wischen: was passiert ist, mit „Rückgängig“. Liegt unten über der Liste, verdeckt das Ergebnis oben nicht. */
@Composable
private fun BoxScope.RueckBand(text: String) {
    Row(
        Modifier.align(Alignment.BottomCenter).padding(horizontal = 10.dp, vertical = 6.dp).fillMaxWidth()
            .clip(RoundedCornerShape(14.dp)).background(Schwarz).padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        T(text, textStil(16.sp, Weiss, FontWeight.Bold), Modifier.weight(1f), zeilen = 2)
        TextKnopf("Rückgängig", Modifier, Art.UMRISS, 42.dp, 16.sp, radius = 10.dp) { App.buehne.rueckgaengig() }
    }
}

// ── Reiter „Archiv“ ──────────────────────────────────────────────────────────

@Composable
private fun ArchivBereich(navi: Navi, setId: Long?) {
    val b by App.ablage.stand.collectAsState()
    var tempo by rememberSaveable { mutableIntStateOf(0) }
    var sprung by remember { mutableStateOf(false) }
    var zeige by remember { mutableStateOf<Zeige?>(null) }
    val songs = b.songs.sortedBy { Normalizer.normalize(it.titel.lowercase(), Normalizer.Form.NFD) }.filter { tempo == 0 || it.tempo == tempo }
    val imGig = if (setId != null) b.schonImGig(setId) else emptyMap()
    if (sprung) { Sprung(songs) { i -> if (i >= 0) zeige = Zeige(i); sprung = false }; return }
    Column(Modifier.fillMaxSize()) {
        Segmente(listOf("Alle", "Langsam", "Mittel", "Schnell"), tempo, Modifier.padding(start = 10.dp, end = 10.dp, top = 7.dp), hoehe = 38.dp, groesse = 15.sp) { tempo = it }
        val merk = "hauptarchiv$tempo"
        SeitenListe(
            eintraege = songs, zeilenHoehe = 62.dp, modifier = Modifier.weight(1f), schluessel = merk, zeige = zeige, oben = 4.dp,
            fussHoehe = FUSS_HOEHE,
            fuss = { s, n, pro, zur, vor ->
                val erster = songs.getOrNull(SeitenGedaechtnis.seiten[merk] ?: (s * pro))
                SeitenFuss(s, n, zur, vor, "${erster?.let { buchstabe(it.titel) } ?: "A"}–Z · Seite ${s + 1}/$n · springen", onMitte = { sprung = true })
            },
            leer = { T(if (b.songs.isEmpty()) "Noch keine Songs." else "Keine Songs mit diesem Tempo.", titelStil(24.sp), Modifier.padding(18.dp)) }
        ) { _, s ->
            key(s.id) {
                val fund = imGig[s.id]
                val wo = fund?.let { f -> f.firstOrNull { it.gespielt }?.let { "gespielt · ${it.setName}" } ?: "eingeplant · ${f.joinToString(", ") { it.setName }}" }
                WischZeile(
                    s.id, rechts = if (setId != null) "ALS NÄCHSTES" else null, links = if (setId != null) "ANS ENDE" else null,
                    onRechts = { if (setId != null) einplanen(navi, setId, s, rechts = true) },
                    onLinks = { if (setId != null) einplanen(navi, setId, s, rechts = false) }
                ) {
                    ArchivZeile(s, waehlen = false, drin = false, schonIm = wo) { navi.zu(Seite.Editor(s.id)) }
                }
            }
        }
    }
}

// ── Seitenfuß: schmal, damit möglichst viele Songs Platz haben ───────────────

private val FUSS_HOEHE = 30.dp

@Composable
private fun SeitenFuss(seite: Int, seiten: Int, zurueck: () -> Unit, vor: () -> Unit, text: String?, onMitte: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().height(FUSS_HOEHE).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        val links = seite > 0
        val rechts = seite < seiten - 1
        Box(Modifier.width(48.dp).fillMaxHeight().semantics { contentDescription = "Vorige Seite" }.tipp(aktiv = links, onClick = zurueck), contentAlignment = Alignment.Center) {
            if (links) Symbol(Sym.LINKS, Schwarz, 14.dp)
        }
        Box(Modifier.weight(1f).fillMaxHeight().then(if (onMitte != null) Modifier.tipp(onClick = onMitte) else Modifier), contentAlignment = Alignment.Center) {
            if (text != null) T(text, textStil(14.sp, gewicht = FontWeight.Bold), zeilen = 1)
        }
        Box(Modifier.width(48.dp).fillMaxHeight().semantics { contentDescription = "Nächste Seite" }.tipp(aktiv = rechts, onClick = vor), contentAlignment = Alignment.Center) {
            if (rechts) Symbol(Sym.RECHTS, Schwarz, 14.dp)
        }
    }
}

// ── Player-Leiste unten ──────────────────────────────────────────────────────

@Composable
private fun PlayerLeiste(navi: Navi) {
    val b by App.ablage.stand.collectAsState()
    val z by App.buehne.zustand.collectAsState()
    val song = b.song(z.songId)
    val set = b.set(z.setId)
    // Nur "angefangen ja/nein" beobachten, nicht jede Position (sonst zeichnet die Leiste alle 200 ms neu).
    val pos = App.buehne.position.collectAsState()
    val angefangen by remember { derivedStateOf { pos.value > 0 } }
    val angespielt = z.spielt || angefangen
    val pause = z.pause
    // Beenden braucht einen zweiten Tipp: Ein Fehltipp neben Play darf den Song nicht abbrechen.
    var bestaetigen by remember(z.songId) { mutableStateOf(false) }
    LaunchedEffect(bestaetigen) { if (bestaetigen) { kotlinx.coroutines.delay(4000); bestaetigen = false } }
    if (!angespielt && bestaetigen) bestaetigen = false
    val platz = set?.eintraege?.indexOfFirst { it.songId == song?.id }?.takeIf { it >= 0 }?.let { "${it + 1} / ${set.eintraege.size}" }

    Column(Modifier.fillMaxWidth().background(Schwarz).padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 12.dp)) {
        // Zeile 1: Zustand links, Zeit rechts. Feste Höhen überall: In der Leiste springt nichts.
        Row(Modifier.fillMaxWidth().height(38.dp), verticalAlignment = Alignment.Bottom) {
            val wort = when {
                song == null -> "Nichts bereit"
                pause != null -> "${pause.art} · gleich${platz?.let { " $it" } ?: ""}"
                z.probe -> "Probe"
                else -> (if (z.spielt) "Läuft" else if (angefangen) "Angehalten" else "Bereit") + (platz?.let { " · $it" } ?: "")
            }
            Etikett(wort, Modifier.weight(1f).padding(bottom = 4.dp), Weiss)
            if (song != null && pause != null) T(zeit(pause.restSek * 1000L), zeitStil(46.sp), zeilen = 1)
            else if (song != null) {
                Etikett(if (angespielt) "noch" else "Länge", Modifier.padding(end = 8.dp, bottom = 4.dp), Weiss)
                Restzeit(song, z.dauerMs, angespielt)
            }
        }
        // Zeile 2: der Titel, immer ganz, notfalls kleiner.
        Box(Modifier.fillMaxWidth().height(36.dp), contentAlignment = Alignment.CenterStart) {
            if (song != null) PassenderTitel(song.titel, { titelStil(it, Weiss) }, 31, 20)
            else T(if (z.setDurch != null) "Set zu Ende" else "Set wählen", titelStil(28.sp, Weiss), zeilen = 1)
        }
        // Zeile 3: Fortschritt, solange der Song läuft; sonst Kapo und Tonart zum Vorbereiten.
        Box(Modifier.fillMaxWidth().height(24.dp), contentAlignment = Alignment.CenterStart) {
            if (song != null && angespielt && pause == null) Fortschritt(song, z.dauerMs)
            else if (song != null) KapoChips(song)
        }
        Abstand(6.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Text nur, wenn es einen gibt; in der Pause zwischen den Songs nicht (dann zählt der Countdown).
            val textDa = song?.lyrics?.isNotBlank() == true && pause == null
            LeistenKnopf(Modifier.weight(1f), beschreibung = "Text zeigen", aktiv = textDa, onClick = { navi.zurBuehne() }) { f ->
                Symbol(Sym.ZEILEN, f, 20.dp)
                T(if (song != null && song.lyrics.isBlank()) "Kein Text" else "Text", textStil(15.sp, f, FontWeight.Bold), zeilen = 1)
            }
            if (pause != null) {
                LeistenKnopf(Modifier.weight(1.5f), voll = true, beschreibung = "Jetzt starten", onClick = { App.buehne.pauseUeberspringen() }) { f ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Symbol(Sym.PLAY, f, 18.dp)
                        T("Jetzt starten", textStil(18.sp, f, FontWeight.Bold), zeilen = 1)
                    }
                }
                LeistenKnopf(Modifier.weight(1f), beschreibung = "Anhalten", onClick = { App.buehne.stopp() }) { f ->
                    Symbol(Sym.PAUSE, f, 18.dp)
                    T("Anhalten", textStil(15.sp, f, FontWeight.Bold), zeilen = 1)
                }
            } else {
                val spielbar = song != null || set?.eintraege?.any { !it.gespielt } == true
                LeistenKnopf(Modifier.weight(1.5f), voll = true, aktiv = spielbar, beschreibung = if (z.spielt) "Pause" else "Abspielen", onClick = { App.buehne.playPause() }) { f ->
                    Symbol(if (z.spielt) Sym.PAUSE else Sym.PLAY, f, 26.dp)
                }
                when {
                    z.probe -> LeistenKnopf(Modifier.weight(1f), beschreibung = "Stopp", onClick = { App.buehne.stopp() }) { f ->
                        Symbol(Sym.STOP, f, 18.dp); T("Stopp", textStil(15.sp, f, FontWeight.Bold), zeilen = 1)
                    }
                    angespielt -> LeistenKnopf(Modifier.weight(1f), voll = bestaetigen, beschreibung = "Song beenden", onClick = {
                        if (bestaetigen) { bestaetigen = false; App.buehne.beenden() }
                        else { bestaetigen = true; App.buehne.hinweis("Nochmal auf „Nochmal“ tippen, dann ist der Song zu Ende.") }
                    }) { f ->
                        Symbol(Sym.STOP, f, 18.dp)
                        T(if (bestaetigen) "Nochmal" else "Beenden", textStil(15.sp, f, FontWeight.Bold), zeilen = 1)
                    }
                    else -> {
                        val weiterGeht = song != null && (set?.naechsterNach(song.id) != null || z.setDanach != null)
                        LeistenKnopf(Modifier.weight(1f), aktiv = weiterGeht, beschreibung = "Überspringen", onClick = { App.buehne.weiter() }) { f ->
                            Symbol(Sym.WEITER, f, 20.dp); T("Weiter", textStil(15.sp, f, FontWeight.Bold), zeilen = 1)
                        }
                    }
                }
            }
        }
    }
}

private fun zeitStil(g: TextUnit) = titelStil(g, Weiss).copy(lineHeight = g, fontFeatureSettings = "tnum")

@Composable
private fun Restzeit(song: Song, dauerMs: Long, angespielt: Boolean) {
    val pos by App.buehne.position.collectAsState()
    val dauer = if (dauerMs > 0) dauerMs else song.dauerMs
    T(zeit(if (angespielt) (dauer - pos).coerceAtLeast(0) else dauer), zeitStil(40.sp), zeilen = 1)
}

@Composable
private fun Fortschritt(song: Song, dauerMs: Long) {
    val pos = App.buehne.position.collectAsState()
    val dauer = if (dauerMs > 0) dauerMs else song.dauerMs
    // Nur neu zeichnen, wenn ein Kästchen dazukommt.
    val voll by remember(dauer) { derivedStateOf { if (dauer > 0) ((pos.value * 20) / dauer).toInt().coerceIn(0, 20) else 0 } }
    Kaestchen(20, voll, Weiss, Modifier.fillMaxWidth(), hoehe = 7.dp)
}

@Composable
private fun KapoChips(song: Song) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(if (song.kapo > 0) "Kapo ${song.kapo}" else "Kapo ohne", song.tonart.ifBlank { "–" }).forEach { t ->
            Box(Modifier.border(2.dp, Weiss, RoundedCornerShape(7.dp)).padding(horizontal = 8.dp, vertical = 1.dp)) {
                T(t, textStil(16.sp, Weiss, FontWeight.Bold), zeilen = 1)
            }
        }
    }
}

/** Knopf auf schwarzem Grund: weiß umrandet, [voll] = weiß gefüllt. Gedrückt = umgekehrt. */
@Composable
private fun LeistenKnopf(modifier: Modifier, voll: Boolean = false, aktiv: Boolean = true, beschreibung: String, onClick: () -> Unit, inhalt: @Composable ColumnScope.(Color) -> Unit) {
    val quelle = remember { MutableInteractionSource() }
    val gedrueckt by quelle.collectIsPressedAsState()
    val hell = voll != gedrueckt
    val rand = if (aktiv) Weiss else Weiss.copy(alpha = 0.4f)
    val vorne = (if (hell) Schwarz else Weiss).let { if (aktiv) it else it.copy(alpha = 0.4f) }
    val form = RoundedCornerShape(13.dp)
    Column(
        modifier.height(58.dp).clip(form).background(if (hell && aktiv) Weiss else Schwarz).border(2.5.dp, rand, form)
            .clickable(interactionSource = quelle, indication = null, enabled = aktiv, onClickLabel = beschreibung, onClick = onClick)
            .semantics { contentDescription = beschreibung },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) { inhalt(if (hell && aktiv) Schwarz else vorne) }
}
