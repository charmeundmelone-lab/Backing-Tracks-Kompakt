package show.jangregersen.buehne.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import show.jangregersen.buehne.App
import show.jangregersen.buehne.daten.Eintrag
import show.jangregersen.buehne.daten.Ende
import show.jangregersen.buehne.daten.Song
import show.jangregersen.buehne.daten.dauerMs
import show.jangregersen.buehne.daten.ersterOffener
import show.jangregersen.buehne.daten.naechster
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
// Antippen wählt einen Song aus (21.11.), lang drücken öffnet seine Einstellungen; Play gibt es nur unten.
// Der Entwurf dazu liegt in entwuerfe/wie-alt/.

private const val REITER_SET = 0
private const val REITER_ARCHIV = 1

/** Gewählter Reiter, solange die App läuft (die Seite selbst verschwindet, wenn eine andere oben liegt). */
object HauptZustand {
    var reiter by androidx.compose.runtime.mutableIntStateOf(REITER_SET)
    /** Letzter Song-Start, auf den schon reagiert wurde (Text öffnet sich nur einmal pro Start). */
    var gesehenerStart = 0
    /**
     * 21.11. (Jan): Wählt man ein anderes Set, während ein Song läuft, beginnt es erst danach. Bis
     * dahin zeigt der Reiter „Set“ dieses Folge-Set als Vorschau (true) statt des laufenden.
     */
    var folgeZeigen by androidx.compose.runtime.mutableStateOf(false)
    /** Kapo-Filter im Archiv: -1 = alle, 0 = ohne, sonst der Bund. */
    var kapo by androidx.compose.runtime.mutableIntStateOf(-1)
}

/** Ungefähre Höhe der Player-Leiste: Hinweise erscheinen knapp darüber. */
val PLAYER_HOEHE = 160.dp

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
    // Gibt es das aktive Set nicht mehr (z. B. gelöscht), aber ein gewähltes kommt danach: das zeigen
    // statt „Noch kein Set gewählt“ (21.11.).
    // Vorschau des Folge-Sets nur, solange es wirklich danach beginnt (sonst von selbst zurück).
    LaunchedEffect(z.setDanach) { if (z.setDanach == null) HauptZustand.folgeZeigen = false }
    val vorschau = HauptZustand.folgeZeigen && b.set(z.setDanach) != null
    val set = if (vorschau) b.set(z.setDanach) else b.set(z.setId) ?: b.set(z.setDanach)
    val gig = b.gigVonSet(z.setId)

    // 20.11. (Jan): Startet ein Song mit Text, geht der Text von selbst auf; am Songende kommt
    // man von selbst hierher zurück (BuehneSeite). Ohne Text bleibt die Set-Ansicht.
    LaunchedEffect(z.startNr) {
        if (z.startNr != HauptZustand.gesehenerStart) {
            HauptZustand.gesehenerStart = z.startNr
            val s = b.song(z.songId)
            if (z.spielt && !z.probe && z.pause == null && s != null && s.lyrics.isNotBlank()) navi.zurBuehne()
        }
    }

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
        Modifier.fillMaxWidth().background(Schwarz).padding(start = 10.dp, end = 10.dp, top = 5.dp, bottom = 7.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
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
        modifier.height(40.dp).clip(form).background(if (an) Weiss else Schwarz).border(2.5.dp, Weiss, form)
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
    if (set.eintraege.isEmpty()) {
        Column(Modifier.padding(18.dp)) {
            T("Dieses Set ist noch leer.", titelStil(26.sp))
            Abstand(12.dp)
            TextKnopf("Songs dazu", Modifier.fillMaxWidth(), Art.VOLL, symbol = Sym.PLUS) { navi.zu(Seite.SetBearbeiten(set.id)) }
        }
        return
    }
    // 21.11. (Jan: "die alte App ist haushoch überlegen"): Die Setliste bleibt in ihrer Reihenfolge
    // wie in der alten App. Gespielte Songs bleiben an ihrem Platz und werden blass, der Song, der
    // jetzt dran ist, ist schwarz. Die Seite folgt ihm nur, wenn er sonst nicht zu sehen wäre.
    val eintraege = set.eintraege
    val folgeSet = b.set(z.setDanach)
    val laufendesSet = b.set(z.setId)
    // Vorschau: dieses Set beginnt erst nach dem laufenden Song (21.11.).
    val istVorschau = setId != z.setId && laufendesSet != null
    // "hält an" nur zeigen, wenn es im Set vom Üblichen abweicht (sonst stünde es überall).
    val mitAutomatik = eintraege.any { it.ende == Ende.WEITER }
    val jetztIndex = if (istVorschau) -1 else eintraege.indexOfFirst { it.songId == z.songId }
    val zeige = remember(setId, z.songId) { if (jetztIndex >= 0) Zeige(jetztIndex, nurWennVerdeckt = true) else null }
    Column(Modifier.fillMaxSize()) {
        z.setDurch?.let { SetEndeHinweis(it) }
        // Band oben: was nach dem laufenden Song kommt, mit Knopf zum Hin- und Herschauen.
        if (istVorschau || folgeSet != null) {
            Row(
                Modifier.padding(start = 10.dp, end = 10.dp, top = 6.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Schwarz)
                    .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                T(if (istVorschau) "Vorschau · beginnt nach diesem Song" else "Nach diesem Song beginnt „${folgeSet?.name}“", textStil(16.sp, Weiss, FontWeight.Bold), Modifier.weight(1f), zeilen = 2)
                if (istVorschau) TextKnopf("Zu „${laufendesSet?.name}“", Modifier, Art.UMRISS, 42.dp, 16.sp, radius = 10.dp) { HauptZustand.folgeZeigen = false }
                else TextKnopf("Ansehen", Modifier, Art.UMRISS, 42.dp, 16.sp, radius = 10.dp) { HauptZustand.folgeZeigen = true }
            }
        }
        SeitenListe(
            eintraege = eintraege, zeilenHoehe = SET_ZEILE, modifier = Modifier.weight(1f), schluessel = "haupt$setId",
            zeige = zeige, fussHoehe = 0.dp, oben = 2.dp, seitenPunkte = true,
            fuss = { _, _, _, _, _ -> }
        ) { i, e ->
            val s = b.song(e.songId) ?: return@SeitenListe
            val jetzt = !istVorschau && e.songId == z.songId
            // Antippen (wie die alte App): Song auswählen. Er liegt dann bereit, Play startet ihn.
            // Läuft gerade ein Song, wird nichts abgebrochen: der angetippte kommt als Nächstes.
            // Gespielt: erst Rückfrage. Lang drücken: Einstellungen des Songs.
            val tippen = {
                when {
                    jetzt -> App.buehne.hinweis("„${s.titel}“ ist schon dran. Lang drücken: Einstellungen.")
                    e.gespielt -> einplanen(navi, setId, s, rechts = true)
                    else -> App.buehne.vorziehen(setId, s.id)
                }
            }
            val lang = { navi.zu(Seite.SongKarte(setId, s.id)) }
            key(e.songId) {
                when {
                    jetzt -> SetZeile(i + 1, s, e, SetArt.JETZT, mitAutomatik, tippen, lang)
                    e.gespielt -> WischZeile(e.songId, rechts = "NOCHMAL", links = null, onRechts = { einplanen(navi, setId, s, rechts = true) }, onLinks = {}) {
                        SetZeile(i + 1, s, e, SetArt.GESPIELT, mitAutomatik, tippen, lang)
                    }
                    else -> WischZeile(e.songId, rechts = "ALS NÄCHSTES", links = "ANS ENDE",
                        onRechts = { App.buehne.vorziehen(setId, s.id) }, onLinks = { App.buehne.ansEnde(setId, s.id) }) {
                        // 27.11.: Angetippt, während ein Song läuft = Schild DANACH, der Song bleibt an seinem Platz.
                        SetZeile(i + 1, s, e, if (e.songId == z.danachIn(setId)) SetArt.DANACH else SetArt.OFFEN, mitAutomatik, tippen, lang)
                    }
                }
            }
        }
    }
}

// ── Zeilen der Setliste (21.11., nach der alten App) ─────────────────────────
// Feste Schriftgrößen (nichts wird kleiner, zu Lange bekommen "…"). Oben der Titel groß, darunter
// Kapo · Tonart · Länge wie in der alten App, rechts daneben die Pause (Tipp = nächste Pause).
// 5 Songs pro Seite (Jan: lieber weniger Songs, dafür lesbar).

/** Höhe jeder Zeile im Reiter „Set“ (SeitenListe braucht eine feste Höhe). */
private val SET_ZEILE = 63.dp

private enum class SetArt { OFFEN, JETZT, GESPIELT, DANACH }

/** Kleines Schild mit Rand (z. B. „hält an“), so flach wie möglich. */
@Composable
private fun Schildchen(text: String, farbe: Color) {
    // Normale Zeilenhöhe (sonst kappt der Rahmen die Buchstaben), dafür keine Luft im Rahmen.
    Box(Modifier.border(1.5.dp, farbe, RoundedCornerShape(6.dp)).padding(horizontal = 5.dp)) {
        T(text, textStil(14.sp, farbe, FontWeight.Bold), zeilen = 1)
    }
}

/** „Kapo 2 · G · 3:36“ wie die Meta-Zeile der alten App; ohne Kapo steht es ausdrücklich da. */
private fun metaZeile(s: Song) = listOf(
    if (s.kapo > 0) "Kapo ${s.kapo}" else "ohne Kapo",
    s.tonart.trim(),
    if (s.dauerMs > 0) zeit(s.dauerMs) else ""
).filter { it.isNotBlank() }.joinToString(" · ")

/**
 * Eine Zeile der Setliste. JETZT = schwarz hinterlegt, GESPIELT = blass mit Haken, DANACH (27.11.) =
 * dicker Rahmen mit schwarzem Schild „DANACH“ oben links: angetippt, kommt als Nächstes, bleibt aber
 * an seinem Platz (Entwurf `entwuerfe/danach`).
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun SetZeile(nr: Int, s: Song, e: Eintrag, art: SetArt, mitAutomatik: Boolean, onTipp: () -> Unit, onLang: () -> Unit) {
    val jetzt = art == SetArt.JETZT
    val gespielt = art == SetArt.GESPIELT
    val danach = art == SetArt.DANACH
    val vorne = when { jetzt -> Weiss; gespielt -> Grau; else -> Schwarz }
    val haeltAn = mitAutomatik && e.ende == Ende.ANHALTEN
    val ansage = s.ansageVorher.isNotBlank() || s.ansageNachher.isNotBlank()
    val quelle = remember { MutableInteractionSource() }
    val form = RoundedCornerShape(12.dp)
    Box(Modifier.fillMaxWidth().height(SET_ZEILE)) {
        Row(
            Modifier.fillMaxSize()
                .then(when {
                    jetzt -> Modifier.padding(start = 6.dp, end = 12.dp, top = 1.dp, bottom = 1.dp).clip(form).background(Schwarz).semantics { contentDescription = "Jetzt dran" }
                    danach -> Modifier.padding(start = 6.dp, end = 12.dp, top = 4.dp, bottom = 0.dp).clip(form).border(3.dp, Schwarz, form).semantics { contentDescription = "Danach dran" }
                    else -> Modifier.padding(horizontal = 10.dp)
                })
                .combinedClickable(interactionSource = quelle, indication = null, onClick = onTipp, onLongClick = onLang)
                .padding(start = if (jetzt || danach) 12.dp else 8.dp, end = if (jetzt || danach) 8.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Links: Nummer (jetzt: weißes Kästchen), gespielt: Haken.
            Box(Modifier.width(32.dp), contentAlignment = Alignment.Center) {
                when {
                    jetzt -> Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(Weiss), contentAlignment = Alignment.Center) {
                        T("$nr", titelStil(24.sp).knapp(), zeilen = 1, ueberlauf = TextOverflow.Visible)
                    }
                    gespielt -> Box(Modifier.size(26.dp).clip(RoundedCornerShape(13.dp)).border(2.dp, Grau, RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) { Symbol(Sym.HAKEN, Grau, 13.dp) }
                    else -> T("$nr", titelStil(24.sp).knapp(), zeilen = 1, ueberlauf = TextOverflow.Visible)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                PassenderTitel(s.titel, { titelStil(it, vorne).knapp() }, 26)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    T(metaZeile(s), textStil(16.sp, vorne, FontWeight.SemiBold), Modifier.weight(1f, fill = false), zeilen = 1)
                    if (!gespielt) {
                        if (ansage) Symbol(Sym.MIKRO, vorne, 15.dp)
                        if (haeltAn) Schildchen("hält an", vorne) else PausenKaestchen(s, vorne, kompakt = true)
                    }
                }
            }
        }
        if (danach) {
            // Das Schild sitzt rechts auf der oberen Rahmenkante: links stünde es im Titel (am Gerät gesehen).
            Box(Modifier.align(Alignment.TopEnd).padding(end = 24.dp).clip(RoundedCornerShape(5.dp)).background(Schwarz).padding(horizontal = 7.dp)) {
                T("DANACH", textStil(12.sp, Weiss, FontWeight.Bold).knapp(), zeilen = 1, ueberlauf = TextOverflow.Visible)
            }
        }
        if (!jetzt && !danach) Linie(Modifier.align(Alignment.BottomCenter).padding(horizontal = 10.dp))
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
                        .background(if (aktiv) Schwarz else Weiss).tipp { App.buehne.setWaehlen(s.id); HauptZustand.folgeZeigen = App.buehne.zustand.value.setDanach == s.id; onZu() }.padding(horizontal = 10.dp),
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
    // Kapo-Filter (21.11.): bleibt gewählt, auch wenn man kurz zum Set wechselt.
    var kapo by HauptZustand::kapo
    var sprung by remember { mutableStateOf(false) }
    var zeige by remember { mutableStateOf<Zeige?>(null) }
    val songs = b.songs.sortedBy { Normalizer.normalize(it.titel.lowercase(), Normalizer.Form.NFD) }.filter { (tempo == 0 || it.tempo == tempo) && passtKapo(it, kapo) }
    val imGig = if (setId != null) b.schonImGig(setId) else emptyMap()
    if (sprung) { Sprung(songs) { i -> if (i >= 0) zeige = Zeige(i); sprung = false }; return }
    Column(Modifier.fillMaxSize()) {
        // 20.11. (Jan: "mehr Songs im Archiv ist gut"): schmalere Tempo-Leiste und kompakte Zeilen.
        Segmente(listOf("Alle", "Langsam", "Mittel", "Schnell"), tempo, Modifier.padding(start = 10.dp, end = 10.dp, top = 4.dp), hoehe = 30.dp, groesse = 15.sp) { tempo = it }
        KapoLeiste(b.songs, kapo, Modifier.padding(start = 10.dp, end = 10.dp, top = 4.dp)) { kapo = it }
        val merk = "hauptarchiv$tempo$kapo"
        SeitenListe(
            eintraege = songs, zeilenHoehe = 47.dp, modifier = Modifier.weight(1f), schluessel = merk, zeige = zeige, oben = 2.dp,
            fussHoehe = FUSS_HOEHE,
            fuss = { s, n, pro, zur, vor ->
                val erster = songs.getOrNull(SeitenGedaechtnis.seiten[merk] ?: (s * pro))
                SeitenFuss(s, n, zur, vor, "${erster?.let { buchstabe(it.titel) } ?: "A"}–Z · Seite ${s + 1}/$n · springen", onMitte = { sprung = true })
            },
            leer = { T(if (b.songs.isEmpty()) "Noch keine Songs." else "Keine Songs mit diesem Filter.", titelStil(24.sp), Modifier.padding(18.dp)) }
        ) { _, s ->
            key(s.id) {
                val fund = imGig[s.id]
                val wo = fund?.let { f -> f.firstOrNull { it.gespielt }?.let { "gespielt · ${it.setName}" } ?: "eingeplant · ${f.joinToString(", ") { it.setName }}" }
                WischZeile(
                    s.id, rechts = if (setId != null) "ALS NÄCHSTES" else null, links = if (setId != null) "ANS ENDE" else null,
                    onRechts = { if (setId != null) einplanen(navi, setId, s, rechts = true) },
                    onLinks = { if (setId != null) einplanen(navi, setId, s, rechts = false) }
                ) {
                    // 21.11. (wie die Setliste): Tipp = für das Set auswählen, lang drücken = Bearbeiten.
                    ArchivZeile(s, waehlen = false, drin = false, schonIm = wo, kompakt = true, onLang = { navi.zu(Seite.Editor(s.id)) }) {
                        if (setId != null) einplanen(navi, setId, s, rechts = true) else navi.zu(Seite.Editor(s.id))
                    }
                }
            }
        }
    }
}

// ── Seitenfuß: schmal, damit möglichst viele Songs Platz haben ───────────────

private val FUSS_HOEHE = 26.dp

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
    val platz = set?.eintraege?.indexOfFirst { it.songId == song?.id }?.takeIf { it >= 0 }?.let { "${it + 1} / ${set.eintraege.size}" }
    val folgeSet = b.set(z.setDanach)
    val danach = when {
        song == null -> null
        folgeSet != null -> b.song(folgeSet.ersterOffener()?.songId)
        else -> b.song(set?.let { s -> s.naechster(song.id, z.danachIn(s.id)) }?.songId)
    }

    // 21.11. wie die alte App: oben der jetzige Song groß mit seiner Zeit, darunter "Danach: …".
    // Feste Schriftgrößen, feste Höhen, immer dieselben drei Knöpfe an derselben Stelle.
    Column(Modifier.fillMaxWidth().background(Schwarz).padding(start = 12.dp, end = 12.dp, top = 5.dp, bottom = 8.dp)) {
        // Fortschritt als dünne Linie (nur solange der Song läuft; sonst bleibt der Platz leer).
        Box(Modifier.fillMaxWidth().height(5.dp)) {
            if (song != null && angespielt && pause == null) Fortschritt(song, z.dauerMs)
        }
        Abstand(3.dp)
        // Zeile 1: Zustand links, Kapo und Tonart des jetzigen Songs rechts.
        Row(Modifier.fillMaxWidth().height(19.dp), verticalAlignment = Alignment.CenterVertically) {
            val wort = when {
                song == null -> "Nichts bereit"
                pause != null -> "${pause.art} · gleich${platz?.let { " $it" } ?: ""}"
                z.probe -> "Probe"
                else -> (if (z.spielt) "Läuft" else if (angefangen) "Angehalten" else "Bereit") + (platz?.let { " · $it" } ?: "")
            }
            Etikett(wort, Modifier.weight(1f), Weiss)
            if (song != null) Etikett(listOf(if (song.kapo > 0) "Kapo ${song.kapo}" else "ohne Kapo", song.tonart.trim()).filter { it.isNotBlank() }.joinToString(" · "), farbe = Weiss)
        }
        // Zeile 2: der jetzige Song groß, rechts seine Restzeit (bzw. Länge, in der Pause der Countdown).
        Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f)) {
                val ohneSong = when { z.setDurch != null -> "Set zu Ende"; set != null && set.eintraege.isNotEmpty() && set.eintraege.none { !it.gespielt } -> "Alle Songs gespielt"; set != null -> "Set ist leer"; else -> "Set wählen" }
                PassenderTitel(song?.titel ?: ohneSong, { titelStil(it, Weiss).knapp() }, 26)
            }
            if (song != null && pause != null) T(zeit(pause.restSek * 1000L), zeitStil(34.sp), zeilen = 1)
            else if (song != null) Restzeit(song, z.dauerMs, angespielt, 34.sp)
        }
        // Zeile 3: was danach kommt (mit Kapo, wie in der alten App).
        Row(Modifier.fillMaxWidth().height(26.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (song != null) {
                Etikett("Danach", farbe = Weiss)
                val text = when {
                    danach == null -> "Set-Ende"
                    folgeSet != null -> "${folgeSet.name}: ${danach.titel}"
                    else -> danach.titel
                } + (danach?.let { if (it.kapo > 0) " · Kapo ${it.kapo}" else " · ohne Kapo" } ?: "")
                Box(Modifier.weight(1f)) { PassenderTitel(text, { textStil(it, Weiss, FontWeight.Bold) }, 18) }
            }
        }
        Abstand(4.dp)
        // Immer dieselben drei Knöpfe: Text · Play/Pause · Stopp. In der Pause zwischen zwei Songs
        // startet Play sofort und Stopp hält die Automatik an.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val textDa = song?.lyrics?.isNotBlank() == true && pause == null
            LeistenKnopf(Modifier.weight(1f), beschreibung = "Text zeigen", aktiv = textDa, onClick = { navi.zurBuehne() }) { f ->
                Symbol(Sym.ZEILEN, f, 20.dp)
                T(if (song != null && song.lyrics.isBlank()) "Kein Text" else "Text", textStil(15.sp, f, FontWeight.Bold), zeilen = 1)
            }
            val spielbar = pause != null || song != null || set?.eintraege?.any { !it.gespielt } == true
            LeistenKnopf(Modifier.weight(1.5f), voll = true, aktiv = spielbar,
                beschreibung = when { pause != null -> "Jetzt starten"; z.spielt -> "Pause"; else -> "Abspielen" },
                onClick = { if (pause != null) App.buehne.pauseUeberspringen() else App.buehne.playPause() }) { f ->
                Symbol(if (z.spielt && pause == null) Sym.PAUSE else Sym.PLAY, f, 26.dp)
                if (pause != null) T("Jetzt starten", textStil(14.sp, f, FontWeight.Bold), zeilen = 1)
            }
            // Stopp: anhalten und an den Anfang, der Song bleibt dran (20.11.); in der Pause: Automatik anhalten.
            // 27.11.: Läuft der Song, will Stopp einen zweiten Tipp (Knopf wird weiß: „Nochmal tippen“).
            val scharf = z.stoppScharf && z.spielt
            LeistenKnopf(Modifier.weight(1f), voll = scharf, aktiv = angespielt || pause != null || z.probe,
                beschreibung = when { pause != null -> "Anhalten"; scharf -> "Nochmal tippen zum Stoppen"; else -> "Stopp" },
                onClick = { App.buehne.stoppTipp() }) { f ->
                if (scharf) {
                    T("Nochmal", textStil(15.sp, f, FontWeight.Bold), zeilen = 1)
                    T("tippen", textStil(15.sp, f, FontWeight.Bold), zeilen = 1)
                } else {
                    Symbol(Sym.STOP, f, 18.dp)
                    T(if (pause != null) "Anhalten" else "Stopp", textStil(15.sp, f, FontWeight.Bold), zeilen = 1)
                }
            }
        }
    }
}

private fun zeitStil(g: TextUnit) = titelStil(g, Weiss).copy(lineHeight = g, fontFeatureSettings = "tnum")

@Composable
private fun Restzeit(song: Song, dauerMs: Long, angespielt: Boolean, groesse: TextUnit = 40.sp) {
    val pos by App.buehne.position.collectAsState()
    val dauer = if (dauerMs > 0) dauerMs else song.dauerMs
    T(zeit(if (angespielt) (dauer - pos).coerceAtLeast(0) else dauer), zeitStil(groesse), zeilen = 1)
}

@Composable
private fun Fortschritt(song: Song, dauerMs: Long) {
    val pos = App.buehne.position.collectAsState()
    val dauer = if (dauerMs > 0) dauerMs else song.dauerMs
    // Nur neu zeichnen, wenn ein Kästchen dazukommt.
    val voll by remember(dauer) { derivedStateOf { if (dauer > 0) ((pos.value * 20) / dauer).toInt().coerceIn(0, 20) else 0 } }
    Kaestchen(20, voll, Weiss, Modifier.fillMaxWidth(), hoehe = 5.dp)
}

@Composable
private fun KapoChips(song: Song) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(if (song.kapo > 0) "Kapo ${song.kapo}" else "Kapo ohne", song.tonart.ifBlank { "–" }).forEach { t ->
            Box(Modifier.border(2.dp, Weiss, RoundedCornerShape(7.dp)).padding(horizontal = 8.dp)) {
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
    // Ausgegraut immer hellgrau auf Schwarz (vorher war das Zeichen im gefüllten Knopf unsichtbar).
    val vorne = if (aktiv) (if (hell) Schwarz else Weiss) else Weiss.copy(alpha = 0.4f)
    val form = RoundedCornerShape(13.dp)
    Column(
        modifier.height(54.dp).clip(form).background(if (hell && aktiv) Weiss else Schwarz).border(2.5.dp, rand, form)
            .clickable(interactionSource = quelle, indication = null, enabled = aktiv, onClickLabel = beschreibung, onClick = onClick)
            .semantics { contentDescription = beschreibung },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) { inhalt(if (hell && aktiv) Schwarz else vorne) }
}
