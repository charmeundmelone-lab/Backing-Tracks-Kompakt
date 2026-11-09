package show.jangregersen.buehne.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import show.jangregersen.buehne.App
import show.jangregersen.buehne.audio.Import
import show.jangregersen.buehne.daten.Song
import show.jangregersen.buehne.daten.Tempo
import show.jangregersen.buehne.daten.ausSetNehmen
import show.jangregersen.buehne.daten.songsHinzufuegen
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

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun ArchivSeite(navi: Navi, zielSetId: Long?) {
    val b by App.ablage.stand.collectAsState()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tempo by rememberSaveable { mutableIntStateOf(0) }
    var sprung by remember { mutableStateOf(false) }
    var zeige by remember { mutableStateOf<Zeige?>(null) }
    var holt by remember { mutableStateOf<String?>(null) }

    val alle = b.songs.sortedBy { Normalizer.normalize(it.titel.lowercase(), Normalizer.Form.NFD) }
    val songs = alle.filter { tempo == 0 || it.tempo == tempo }
    val zielSet = b.set(zielSetId)
    val imSet = zielSet?.eintraege?.map { it.songId }?.toSet() ?: emptySet()

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

    val ober = if (zielSet != null) "Hinzufügen zu" else "Archiv · ${b.songs.size} Songs"
    val titel = if (zielSet != null) "${zielSet.name} · ${zielSet.eintraege.size} Songs" else "Archiv"
    Rahmen(navi, ober, titel, rechts = {
        if (zielSet != null) WeisserKnopfImBand("Fertig") { navi.zurueck() }
        else WeisserKnopfImBand("Songs holen", Sym.ORDNER) { waehler.launch(null) }
    }) {
        if (sprung) {
            Sprung(songs) { i -> if (i >= 0) zeige = Zeige(i); sprung = false }
            return@Rahmen
        }
        if (zielSet == null) Segmente(listOf("Gigs", "Archiv"), 1, Modifier.padding(start = 14.dp, end = 14.dp, top = 10.dp)) { if (it == 0) navi.ersetzen(Seite.Gigs) }
        Segmente(listOf("Alle", "Langsam", "Mittel", "Schnell"), tempo, Modifier.padding(start = 14.dp, end = 14.dp, top = 8.dp), hoehe = 40.dp, groesse = 15.sp) { tempo = it }
        SeitenListe(
            eintraege = songs, zeilenHoehe = 62.dp, modifier = Modifier.weight(1f), schluessel = "archiv$tempo$zielSetId", zeige = zeige, oben = 6.dp,
            fuss = { s, n, pro, z, v ->
                val erster = songs.getOrNull(s * pro)
                Blaetterleiste(s, n, z, v, mitteText = "${erster?.let { buchstabe(it.titel) } ?: "A"}–Z · Seite ${s + 1}/$n", mitteKlein = "tippen zum Springen", onMitte = { sprung = true })
            },
            leer = {
                Column(Modifier.padding(18.dp)) {
                    T(if (b.songs.isEmpty()) "Noch keine Songs." else "Keine Songs mit diesem Tempo.", titelStil(26.sp))
                    Abstand(6.dp)
                    if (b.songs.isEmpty()) {
                        T("Tippe oben auf „Songs holen“ und wähle den Ordner mit deinen Backing-Tracks. Einzelne Audiodateien werden je ein Stereo-Song (links Band, rechts Click), Unterordner werden Songs aus Einzelspuren.", textStil(16.sp))
                        Abstand(14.dp)
                        TextKnopf("Ordner wählen", Modifier.fillMaxWidth(), Art.VOLL, symbol = Sym.ORDNER) { waehler.launch(null) }
                    }
                }
            }
        ) { _, s ->
            ArchivZeile(s, zielSet != null, s.id in imSet) {
                if (zielSet == null) navi.zu(Seite.Editor(s.id))
                else if (s.id in imSet) App.ablage.aendern { it.ausSetNehmen(zielSet.id, s.id) }
                else App.ablage.aendern { it.songsHinzufuegen(zielSet.id, listOf(s.id)) }
            }
        }
    }
}

@Composable
private fun ArchivZeile(s: Song, waehlen: Boolean, drin: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.padding(horizontal = 10.dp).fillMaxWidth().height(61.dp).tipp(onClick = onClick).padding(start = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(Modifier.weight(1f)) {
            T(s.titel, textStil(21.sp), zeilen = 1)
            songInfo(s).takeIf { it.isNotBlank() }?.let { T(it, textStil(14.sp, gewicht = FontWeight.Medium), zeilen = 1) }
        }
        T(if (s.dauerMs > 0) zeit(s.dauerMs) else "", titelStil(19.sp, gewicht = FontWeight.SemiBold))
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
private fun Sprung(songs: List<Song>, wahl: (Int) -> Unit) {
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
                    val m = Modifier.weight(1f).height(50.dp).clip(form)
                    if (n > 0) Column(
                        m.border(2.5.dp, Schwarz, form).tipp { wahl(songs.indexOfFirst { buchstabe(it.titel) == c }) },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
                    ) {
                        T(c.toString(), titelStil(24.sp))
                        T(if (n == 1) "1 Song" else "$n Songs", textStil(12.sp), zeilen = 1)
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
