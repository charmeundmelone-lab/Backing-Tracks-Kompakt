package show.jangregersen.buehne.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import show.jangregersen.buehne.App
import show.jangregersen.buehne.audio.Import
import show.jangregersen.buehne.daten.Song
import show.jangregersen.buehne.daten.schonImGig
import show.jangregersen.buehne.daten.songsHinzufuegen
import show.jangregersen.buehne.daten.wiederholungsFrage
import show.jangregersen.buehne.daten.Tempo
import show.jangregersen.buehne.daten.jokerUmschalten
import show.jangregersen.buehne.daten.nochNichtDran
import java.text.Normalizer

fun buchstabe(titel: String): Char {
    val c = Normalizer.normalize(titel.trim().take(1), Normalizer.Form.NFD).firstOrNull()?.uppercaseChar() ?: '#'
    return if (c in 'A'..'Z') c else '#'
}

fun songInfo(s: Song): String = listOfNotNull(
    s.tonart.takeIf { it.isNotBlank() },
    if (s.kapo > 0) "Kapo ${s.kapo}" else null,
    if (s.bpm > 0) "${s.bpm} BPM" else null
).joinToString(" · ").ifEmpty { s.interpret }

@Composable
fun ArchivSeite(navi: Navi, zielSetId: Long?, wunsch: Boolean = false, jokerGigId: Long? = null) {
    val b by App.ablage.stand.collectAsState()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tempo by rememberSaveable { mutableIntStateOf(0) }
    var kapo by rememberSaveable { mutableIntStateOf(-1) }
    var nochNicht by rememberSaveable { mutableStateOf(false) }
    var sprung by remember { mutableStateOf(false) }
    var zeige by remember { mutableStateOf<Zeige?>(null) }
    var holt by remember { mutableStateOf<String?>(null) }

    val alle = b.songs.sortedBy { Normalizer.normalize(it.titel.lowercase(), Normalizer.Form.NFD) }
    val zielSet = b.set(zielSetId)
    // 27.11.: Joker eines Gigs festlegen (Tipp = Joker ja/nein).
    val jokerGig = b.gig(jokerGigId)
    // Für den Filter „noch nicht dran“ und die grauen Hinweise: das Set bzw. der Gig, um den es geht.
    val gigSet = zielSet?.id ?: jokerGig?.sets?.firstOrNull()?.id
    val gefiltert = alle.filter { (tempo == 0 || it.tempo == tempo) && passtKapo(it, kapo) }
    val filterAn = nochNicht && gigSet != null
    val songs = if (filterAn) b.nochNichtDran(gigSet, gefiltert) else gefiltert
    val merk = "archiv$tempo$kapo$zielSetId$jokerGigId$filterAn"
    val imSet = zielSet?.eintraege?.map { it.songId }?.toSet() ?: emptySet()
    // Jans Wunsch 15.11.: Was im Gig schon eingeplant ist, steht grau da; nochmal nur mit Rückfrage.
    val imGig = if (gigSet != null) b.schonImGig(gigSet) else emptyMap()

    val waehler = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            holt = "Ordner wird gelesen …"
            scope.launch {
                val e = withContext(Dispatchers.IO) { Import.ordnerHolen(ctx, uri, App.ablage) { name -> holt = name } }
                holt = null
                App.buehne.hinweis(if (e.neu + e.aktualisiert == 0) "In diesem Ordner sind keine Audiodateien." else (if (e.neu == 1) "1 neuer Song geholt" else "${e.neu} neue Songs geholt") + if (e.aktualisiert > 0) ", ${e.aktualisiert} schon bekannt." else ".")
            }
        }
    }

    if (holt != null) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
            Etikett("Songs werden geholt")
            Abstand(8.dp)
            T(holt ?: "", titelStil(30.sp), zeilen = 2)
            Abstand(12.dp)
            T("Bitte warten. Bei vielen großen Dateien dauert das ein paar Minuten.", textStil(16.sp))
        }
        return
    }

    val ober = when {
        jokerGig != null -> "Joker für ${jokerGig.name}"
        zielSet != null && wunsch -> "Wunschsong für ${zielSet.name}"
        zielSet != null -> "Songs dazu für"
        else -> "Archiv · ${b.songs.size} Songs"
    }
    val titel = when {
        jokerGig != null -> "${jokerGig.joker.size} Joker"
        zielSet != null && wunsch -> "Antippen = als Nächstes"
        zielSet != null -> "${zielSet.name} · ${zielSet.eintraege.size} ${if (zielSet.eintraege.size == 1) "Song" else "Songs"}"
        else -> "Archiv"
    }
    Rahmen(navi, ober, titel, rechts = {
        if ((zielSet != null && !wunsch) || jokerGig != null) WeisserKnopfImBand("Fertig") { navi.zurueck() }
        else if (zielSet == null) WeisserKnopfImBand("Songs holen", Sym.ORDNER) { waehler.launch(null) }
    }) {
        if (sprung) {
            Sprung(songs) { i -> if (i >= 0) zeige = Zeige(i); sprung = false }
            return@Rahmen
        }
        // Texte holen gibt es seit 13.11. nur noch in der Werkstatt am Laptop.
        if (zielSet == null && jokerGig == null) Segmente(listOf("Gigs", "Archiv"), 1, Modifier.padding(start = 14.dp, end = 14.dp, top = 10.dp)) { if (it == 0) navi.ersetzen(Seite.Gigs) }
        if (jokerGig != null) T("Tipp = Joker dazu oder wieder weg. Live stehen die Joker im Reiter „Joker“.", textStil(15.sp), Modifier.padding(start = 14.dp, end = 14.dp, top = 8.dp), zeilen = 2)
        // 27.11.: rechts neben der Tempo-Leiste der Filter „noch nicht dran“ (nur mit Gig).
        Row(Modifier.padding(start = 14.dp, end = 14.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.weight(1f)) { Segmente(listOf("Alle", "Langsam", "Mittel", "Schnell"), tempo, hoehe = 40.dp, groesse = 15.sp) { tempo = it } }
            if (gigSet != null) NochNichtSchalter(nochNicht, 40.dp) { nochNicht = !nochNicht }
        }
        KapoLeiste(alle, kapo, Modifier.padding(start = 14.dp, end = 14.dp, top = 6.dp), hoehe = 40.dp) { kapo = it }
        SeitenListe(
            eintraege = songs, zeilenHoehe = 62.dp, modifier = Modifier.weight(1f), schluessel = merk, zeige = zeige, oben = 6.dp,
            fuss = { s, n, pro, z, v ->
                val erster = songs.getOrNull(SeitenGedaechtnis.seiten[merk] ?: (s * pro))
                Blaetterleiste(s, n, z, v, mitteText = "${erster?.let { buchstabe(it.titel) } ?: "A"}–Z · Seite ${s + 1}/$n", mitteKlein = "tippen zum Springen", onMitte = { sprung = true })
            },
            leer = {
                Column(Modifier.padding(18.dp)) {
                    T(if (b.songs.isEmpty()) "Noch keine Songs." else if (filterAn && gefiltert.isNotEmpty()) "Alles schon im Gig." else "Keine Songs mit diesem Filter.", titelStil(26.sp))
                    Abstand(6.dp)
                    if (b.songs.isEmpty()) {
                        T("Tippe oben auf „Songs holen“ und wähle den Ordner mit deinen Backing-Tracks. Einzelne Audiodateien werden je ein Stereo-Song (links Band, rechts Click), Unterordner werden Songs aus Einzelspuren.", textStil(16.sp))
                        Abstand(14.dp)
                        TextKnopf("Ordner wählen", Modifier.fillMaxWidth(), Art.VOLL, symbol = Sym.ORDNER) { waehler.launch(null) }
                    }
                }
            }
        ) { _, s ->
            // Live (Wunschsong): Antippen = kommt als Nächstes, zurück zur Bühne (Jans eine Regel).
            // Vorbereiten: Antippen setzt den Song ans Ende vom Set. Herausnehmen geht nur über die
            // Einstellungen mit Rückfrage (früher nahm ein zweiter Tipp live Songs aus Versehen raus).
            val fund = imGig[s.id]
            val wo = fund?.let { f -> f.firstOrNull { it.gespielt }?.let { "gespielt · ${it.setName}" } ?: "eingeplant · ${f.joinToString(", ") { it.setName }}" }
            ArchivZeile(s, (zielSet != null && !wunsch) || jokerGig != null, if (jokerGig != null) s.id in jokerGig.joker else s.id in imSet, wo) {
                val frage = zielSet?.let { b.wiederholungsFrage(it.id, s.id) }
                when {
                    jokerGig != null -> {
                        val war = s.id in jokerGig.joker
                        App.ablage.aendern { it.jokerUmschalten(jokerGig.id, s.id) }
                        App.buehne.hinweis(if (war) "„${s.titel}“ ist kein Joker mehr." else "„${s.titel}“ ist jetzt Joker.")
                    }
                    zielSet == null -> navi.zu(Seite.Editor(s.id))
                    wunsch && frage != null -> navi.zu(Seite.Frage("Nochmal spielen?", "„${s.titel}“ $frage. Willst du ihn trotzdem nochmal spielen?", "Ja, nochmal spielen") {
                        App.buehne.vorziehen(zielSet.id, s.id); navi.zurSetliste()
                    })
                    wunsch -> { App.buehne.vorziehen(zielSet.id, s.id); navi.zurSetliste() }
                    s.id in imSet -> App.buehne.hinweis("„${s.titel}“ ist schon in diesem Set.")
                    frage != null -> navi.zu(Seite.Frage("Nochmal einplanen?", "„${s.titel}“ $frage. Willst du ihn trotzdem auch in „${zielSet.name}“ haben?", "Ja, auch hier") {
                        App.ablage.aendern { it.songsHinzufuegen(zielSet.id, listOf(s.id)) }
                        navi.zurueck()
                        App.buehne.hinweis("„${s.titel}“ steht jetzt am Ende vom Set.")
                    })
                    else -> {
                        App.ablage.aendern { it.songsHinzufuegen(zielSet.id, listOf(s.id)) }
                        App.buehne.hinweis("„${s.titel}“ steht jetzt am Ende vom Set.")
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun ArchivZeile(s: Song, waehlen: Boolean, drin: Boolean, schonIm: String?, kompakt: Boolean = false, onLang: (() -> Unit)? = null, onClick: () -> Unit) {
    val farbe = if (schonIm != null) Grau else Schwarz
    Row(
        Modifier.padding(horizontal = 10.dp).fillMaxWidth().height(if (kompakt) 46.dp else 61.dp)
            .then(if (onLang != null) Modifier.combinedClickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null, onClick = onClick, onLongClick = onLang) else Modifier.tipp(onClick = onClick))
            .padding(start = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(Modifier.weight(1f)) {
            PassenderTitel(s.titel, { textStil(it, farbe).let { st -> if (kompakt) st.knapp() else st } }, 21, 15)
            (schonIm ?: listOf(songInfo(s), if (s.lyrics.isBlank()) "ohne Text" else "").filter { it.isNotBlank() }.joinToString(" · "))
                .takeIf { it.isNotBlank() }?.let { T(it, textStil(if (kompakt) 13.sp else 14.sp, farbe, if (schonIm != null) FontWeight.Bold else FontWeight.Medium).let { st -> st }, zeilen = 1) }
        }
        T(if (s.dauerMs > 0) zeit(s.dauerMs) else "", titelStil(19.sp, farbe, FontWeight.SemiBold))
        if (waehlen) {
            val form = RoundedCornerShape(12.dp)
            Box(Modifier.size(48.dp).clip(form).background(if (drin) Schwarz else Weiss).border(2.5.dp, Schwarz, form), contentAlignment = Alignment.Center) {
                Symbol(if (drin) Sym.HAKEN else Sym.PLUS, if (drin) Weiss else Schwarz, 18.dp)
            }
        } else if (s.tempo != Tempo.OHNE) {
            T(Tempo.name(s.tempo).take(1), etikettStil())
        }
    }
    Linie(Modifier.padding(horizontal = 10.dp))
}

@Composable
fun Sprung(songs: List<Song>, wahl: (Int) -> Unit) {
    val zeichen = ('A'..'Z').toList() + '#'
    val zahl = songs.groupingBy { buchstabe(it.titel) }.eachCount()
    Lautstaerketasten { }
    Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        T("Springe zu …", titelStil(24.sp))
        zeichen.chunked(5).forEach { reihe ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                reihe.forEach { c ->
                    val n = zahl[c] ?: 0
                    val form = RoundedCornerShape(12.dp)
                    val m = Modifier.weight(1f).height(54.dp).clip(form)
                    if (n > 0) Column(
                        m.border(2.5.dp, Schwarz, form).tipp { wahl(songs.indexOfFirst { buchstabe(it.titel) == c }) },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
                    ) {
                        T(c.toString(), titelStil(24.sp))
                        T("$n", textStil(12.sp), zeilen = 1)
                    }
                    else Box(m.gestrichelt(12.dp), contentAlignment = Alignment.Center) { T(c.toString(), titelStil(24.sp, gewicht = FontWeight.SemiBold)) }
                }
                repeat(5 - reihe.size) { Box(Modifier.weight(1f)) }
            }
        }
        Abstand(4.dp)
        TextKnopf("Zurück zur Liste", Modifier.fillMaxWidth(), hoehe = 52.dp) { wahl(-1) }
    }
}

/** Passt der Song zum Kapo-Filter? [kapo] -1 = alle, 0 = ohne Kapo, sonst der Bund. */
fun passtKapo(s: Song, kapo: Int) = kapo < 0 || s.kapo.coerceAtLeast(0) == kapo

/**
 * Kapo-Filter (21.11., Jan: „filtern nach Kapoposition ist wichtig“). Zeigt nur die Bünde, die im
 * Archiv wirklich vorkommen, damit jedes Feld breit genug zum Treffen bleibt.
 */
/**
 * Schalter „noch nicht dran“ (27.11.): an = schwarz, im Archiv stehen dann nur Songs, die im Gig
 * weder gespielt noch eingeplant sind.
 */
@Composable
fun NochNichtSchalter(an: Boolean, hoehe: Dp = 30.dp, onClick: () -> Unit) {
    val form = RoundedCornerShape(9.dp)
    Box(
        Modifier.height(hoehe).clip(form).background(if (an) Schwarz else Weiss).border(2.dp, Schwarz, form)
            .tipp(onClick = onClick).semantics { contentDescription = if (an) "noch nicht dran: an" else "noch nicht dran: aus" }
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) { T("noch nicht dran", textStil(14.sp, if (an) Weiss else Schwarz, FontWeight.Bold), zeilen = 1) }
}

@Composable
fun KapoLeiste(alle: List<Song>, kapo: Int, modifier: Modifier = Modifier, hoehe: Dp = 30.dp, onWahl: (Int) -> Unit) {
    val werte = (alle.map { it.kapo.coerceAtLeast(0) } + listOfNotNull(kapo.takeIf { it >= 0 })).distinct().sorted()
    if (werte.size < 2) return
    val optionen = listOf("alle") + werte.map { if (it == 0) "ohne" else "$it" }
    val gewaehlt = if (kapo < 0) 0 else werte.indexOf(kapo) + 1
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        T("Kapo", textStil(15.sp, gewicht = FontWeight.Bold), zeilen = 1)
        Box(Modifier.weight(1f)) {
            Segmente(optionen, gewaehlt, hoehe = hoehe, groesse = 15.sp) { i -> onWahl(if (i == 0) -1 else werte[i - 1]) }
        }
    }
}
