package show.jangregersen.buehne.ui

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import show.jangregersen.buehne.App
import show.jangregersen.buehne.audio.Aufnahme
import show.jangregersen.buehne.audio.PdfLyrics
import show.jangregersen.buehne.audio.Rolle
import show.jangregersen.buehne.audio.Spuren
import show.jangregersen.buehne.daten.Ende
import show.jangregersen.buehne.daten.Song
import show.jangregersen.buehne.daten.Tempo
import show.jangregersen.buehne.daten.alsNaechstes
import show.jangregersen.buehne.daten.ausSetNehmen
import show.jangregersen.buehne.daten.eintragAendern
import show.jangregersen.buehne.daten.ohneSong
import show.jangregersen.buehne.daten.songAendern
import show.jangregersen.buehne.lyrics.Abschnitte
import java.io.File

val PAUSEN = listOf(0, 10, 20, 30, 45, 60, 90, 120, 180, 300, 600, 900, 1200, 1800, 2700, 3600)

private fun pauseSchritt(aktuell: Int, richtung: Int): Int {
    val i = PAUSEN.indexOfFirst { it >= aktuell }.let { if (it < 0) PAUSEN.size - 1 else it }
    val basis = if (PAUSEN.getOrNull(i) == aktuell) i else if (richtung > 0) i - 1 else i
    return PAUSEN[(basis + richtung).coerceIn(0, PAUSEN.size - 1)]
}

private fun songAendern(id: Long, f: (Song) -> Song) = App.ablage.aendern { it.songAendern(id, f) }

// ── Song-Karte im Set ────────────────────────────────────────────────────────

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun SongKarteSeite(navi: Navi, setId: Long, songId: Long) {
    val b by App.ablage.stand.collectAsState()
    val z by App.buehne.zustand.collectAsState()
    val song = b.song(songId) ?: run { WegHier(navi); return }
    val set = b.set(setId) ?: run { WegHier(navi); return }
    val e = set.eintraege.firstOrNull { it.songId == songId } ?: run { WegHier(navi); return }
    val platz = set.eintraege.indexOf(e) + 1
    val istAktuell = z.songId == songId
    val laeuftEtwas = z.spielt || z.pause != null
    var hoert by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { if (hoert) App.buehne.notizStoppen() } }
    Lautstaerketasten { }

    Column(Modifier.fillMaxSize()) {
        val aktuellSong = b.song(z.songId)
        if (aktuellSong != null) JetztBand(navi, aktuellSong.titel)
        Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 14.dp, top = 12.dp, bottom = 10.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Etikett("Platz $platz in ${set.name}${if (e.gespielt) " · gespielt" else ""}")
                T(song.titel, titelStil(32.sp), zeilen = 2)
                T(listOf(songInfo(song), zeit(song.dauerMs)).joinToString(" · "), textStil(16.sp), zeilen = 1)
            }
            SymbolKnopf(Sym.KREUZ, "Schließen", Modifier.width(48.dp), hoehe = 48.dp, groesse = 16.dp) { navi.zurueck() }
        }
        Linie(Modifier.padding(horizontal = 14.dp), 2.5.dp)
        Column(Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when {
                istAktuell -> {}
                !laeuftEtwas -> TextKnopf("Jetzt bereitlegen", Modifier.fillMaxWidth(), Art.VOLL, 52.dp, symbol = Sym.PLAY) {
                    App.buehne.bereitlegen(setId, songId); navi.zurBuehne()
                }
                else -> TextKnopf("Als Nächstes spielen", Modifier.fillMaxWidth(), Art.VOLL, 52.dp, symbol = Sym.WEITER) {
                    App.ablage.aendern { it.alsNaechstes(setId, songId, z.songId) }
                    App.buehne.hinweis("„${song.titel}“ kommt als Nächstes.")
                    navi.zurueck()
                }
            }
            Column {
                Etikett(if (song.ansageVorher.isNotBlank()) "Ansage vorher · ${zeit(song.ansageVorherMs)}" else "Keine Ansage vorher")
                Abstand(6.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextKnopf(if (hoert) "Stopp" else "Anhören", Modifier.weight(1f), hoehe = 48.dp, groesse = 16.sp, symbol = if (hoert) Sym.STOP else Sym.PLAY, aktiv = song.ansageVorher.isNotBlank()) {
                        if (hoert) { App.buehne.notizStoppen(); hoert = false } else { hoert = true; App.buehne.notizAnhoeren(song.ansageVorher) { hoert = false } }
                    }
                    TextKnopf(if (song.ansageVorher.isBlank()) "Aufnehmen" else "Neu aufnehmen", Modifier.weight(1f), hoehe = 48.dp, groesse = 16.sp, symbol = Sym.AUFNAHME) {
                        navi.zu(Seite.Editor(songId, 2))
                    }
                }
            }
            Column {
                Etikett("Pause danach")
                Abstand(6.dp)
                Stufen(pauseText(song.pauseSek), { songAendern(songId) { it.copy(pauseSek = pauseSchritt(it.pauseSek, -1)) } }, { songAendern(songId) { it.copy(pauseSek = pauseSchritt(it.pauseSek, 1)) } })
            }
            Column {
                Etikett("Am Songende")
                Abstand(6.dp)
                Segmente(listOf("Weiter im Set", "Anhalten"), if (e.ende == Ende.ANHALTEN) 1 else 0) { i ->
                    App.ablage.aendern { it.eintragAendern(setId, songId) { x -> x.copy(ende = if (i == 1) Ende.ANHALTEN else Ende.WEITER) } }
                }
            }
        }
        Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextKnopf("Bearbeiten", Modifier.weight(1f), Art.DUENN, 48.dp, 15.sp) { navi.zu(Seite.Editor(songId)) }
                TextKnopf("Mixer", Modifier.weight(1f), Art.DUENN, 48.dp, 15.sp) { navi.zu(Seite.Mixer(songId)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (e.gespielt) TextKnopf("Noch nicht gespielt", Modifier.weight(1f), Art.DUENN, 48.dp, 15.sp) {
                    App.ablage.aendern { it.eintragAendern(setId, songId) { x -> x.copy(gespielt = false) } }
                }
                TextKnopf("Aus dem Set nehmen", Modifier.weight(1f), Art.GESTRICHELT, 48.dp, 15.sp, aktiv = !istAktuell) {
                    navi.zu(Seite.Frage("Aus dem Set nehmen?", "„${song.titel}“ verschwindet aus „${set.name}“. Im Archiv bleibt er.", "Herausnehmen") {
                        App.ablage.aendern { it.ausSetNehmen(setId, songId) }; navi.zurueck(); navi.zurueck()
                    })
                }
            }
        }
    }
}

@Composable
fun Stufen(wert: String, minus: () -> Unit, plus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SymbolKnopf(Sym.MINUS, "weniger", Modifier.width(60.dp), hoehe = 48.dp, groesse = 18.dp, onClick = minus)
        T(wert, titelStil(30.sp), Modifier.weight(1f), zeilen = 1, mitte = true)
        SymbolKnopf(Sym.PLUS, "mehr", Modifier.width(60.dp), hoehe = 48.dp, groesse = 18.dp, onClick = plus)
    }
}

// ── Editor ───────────────────────────────────────────────────────────────────

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun EditorSeite(navi: Navi, songId: Long, startReiter: Int) {
    val b by App.ablage.stand.collectAsState()
    val song = b.song(songId) ?: run { WegHier(navi); return }
    var reiter by remember { mutableStateOf(startReiter) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ZurueckKnopf(aufSchwarz = false) { navi.zurueck() }
            T(song.titel, titelStil(26.sp), Modifier.weight(1f), zeilen = 1)
            TextKnopf("Fertig", art = Art.VOLL, hoehe = 44.dp) { navi.zurueck() }
        }
        Linie(dicke = 2.5.dp)
        Segmente(listOf("Daten", "Lyrics", "Ansage"), reiter, Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp), hoehe = 42.dp) { reiter = it }
        when (reiter) {
            0 -> DatenReiter(navi, song)
            1 -> LyricsReiter(navi, song)
            else -> AnsageReiter(song)
        }
    }
}

@Composable
private fun Feld(etikett: String, wert: String, modifier: Modifier = Modifier, zahl: Boolean = false, onWert: (String) -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Etikett(etikett)
        BasicTextField(
            value = wert, onValueChange = onWert, singleLine = true,
            textStyle = textStil(21.sp),
            keyboardOptions = KeyboardOptions(keyboardType = if (zahl) KeyboardType.Number else KeyboardType.Text, capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth().height(46.dp).border(2.5.dp, Schwarz, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 9.dp)
        )
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun DatenReiter(navi: Navi, song: Song) {
    val ctx = LocalContext.current
    val z by App.buehne.zustand.collectAsState()
    val hoertProbe = z.probe && z.songId == song.id && z.spielt
    val tipps = remember { mutableStateListOf<Long>() }
    var spuren by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(song.pfad) {
        spuren = withContext(Dispatchers.IO) {
            val s = Spuren.finden(ctx, song.pfad)
            when {
                s.isEmpty() -> "Audiodatei nicht gefunden"
                s.size == 1 && s[0].rolle == Rolle.STEREO -> "Stereo-Datei · links Band, rechts Click"
                else -> "${s.size} Spuren · ${s.count { it.rolle == Rolle.OHR }} davon im Ohr"
            }
        }
    }
    DisposableEffect(Unit) { onDispose { if (App.buehne.zustand.value.probe) App.buehne.probeEnde() } }
    Lautstaerketasten { }
    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Feld("Titel", song.titel) { t -> songAendern(song.id) { it.copy(titel = t) } }
        Feld("Interpret", song.interpret) { t -> songAendern(song.id) { it.copy(interpret = t) } }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Feld("Tonart", song.tonart, Modifier.weight(1f)) { t -> songAendern(song.id) { it.copy(tonart = t) } }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Etikett("Kapo")
                KleinStufen("${song.kapo}", { songAendern(song.id) { it.copy(kapo = (it.kapo - 1).coerceAtLeast(0)) } }, { songAendern(song.id) { it.copy(kapo = (it.kapo + 1).coerceAtMost(11)) } })
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Etikett("Tempo")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) {
                    KleinStufen(if (song.bpm > 0) "${song.bpm} BPM" else "– BPM", { songAendern(song.id) { it.copy(bpm = (it.bpm - 1).coerceAtLeast(0)) } }, { songAendern(song.id) { it.copy(bpm = (it.bpm + 1).coerceAtMost(300)) } })
                }
                TextKnopf("Mittippen", Modifier.width(120.dp), hoehe = 46.dp, groesse = 16.sp) {
                    val jetzt = System.currentTimeMillis()
                    if (tipps.isNotEmpty() && jetzt - tipps.last() > 2500) tipps.clear()
                    tipps.add(jetzt); while (tipps.size > 6) tipps.removeAt(0)
                    if (tipps.size >= 3) {
                        val schnitt = (tipps.last() - tipps.first()).toDouble() / (tipps.size - 1)
                        val bpm = (60000.0 / schnitt).toInt().coerceIn(30, 300)
                        songAendern(song.id) { it.copy(bpm = bpm) }
                    }
                }
            }
        }
        Segmente(listOf("–", "Langsam", "Mittel", "Schnell"), song.tempo, hoehe = 40.dp, groesse = 15.sp) { t -> songAendern(song.id) { it.copy(tempo = t) } }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Symbol(Sym.ORDNER, Schwarz, 20.dp)
            T(spuren ?: "…", textStil(14.sp), Modifier.weight(1f), zeilen = 2)
        }
        Box(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextKnopf(if (hoertProbe) "Stopp" else "Probe", Modifier.weight(1f), if (hoertProbe) Art.VOLL else Art.UMRISS, 48.dp, 15.sp, symbol = if (hoertProbe) Sym.STOP else Sym.PLAY) {
                if (hoertProbe) App.buehne.probeEnde() else App.buehne.probe(song.id)
            }
            TextKnopf("Mixer", Modifier.weight(1f), Art.UMRISS, 48.dp, 15.sp) { navi.zu(Seite.Mixer(song.id)) }
            TextKnopf("Löschen", Modifier.weight(1f), Art.GESTRICHELT, 48.dp, 15.sp) {
                navi.zu(Seite.Frage("Song löschen?", "„${song.titel}“ verschwindet aus dem Archiv und aus allen Sets. Die Audiodatei auf dem Handy bleibt.", "Löschen") {
                    listOf(song.ansageVorher, song.ansageNachher).filter { it.isNotBlank() }.forEach { runCatching { File(it).delete() } }
                    App.ablage.aendern { it.ohneSong(song.id) }
                    navi.zurueck(); navi.zurueck()
                })
            }
        }
    }
}

@Composable
private fun KleinStufen(wert: String, minus: () -> Unit, plus: () -> Unit) {
    val form = RoundedCornerShape(12.dp)
    Row(Modifier.fillMaxWidth().height(46.dp).clip(form).border(2.5.dp, Schwarz, form), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(44.dp).height(46.dp).tipp(onClick = minus), contentAlignment = Alignment.Center) { Symbol(Sym.MINUS, Schwarz, 16.dp) }
        Box(Modifier.width(2.dp).height(46.dp).background(Schwarz))
        T(wert, titelStil(24.sp), Modifier.weight(1f), zeilen = 1, mitte = true)
        Box(Modifier.width(2.dp).height(46.dp).background(Schwarz))
        Box(Modifier.width(44.dp).height(46.dp).tipp(onClick = plus), contentAlignment = Alignment.Center) { Symbol(Sym.PLUS, Schwarz, 16.dp) }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun LyricsReiter(navi: Navi, song: Song) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val abschnitte = remember(song.lyrics) { Abschnitte.zerlegen(song.lyrics) }
    val marken = remember(song.marken) { Abschnitte.markenLesen(song.marken) }
    val pdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            val text = withContext(Dispatchers.IO) { runCatching { PdfLyrics.lesen(ctx, uri) }.getOrDefault("") }
            if (text.isBlank()) App.buehne.hinweis("In diesem PDF steht kein lesbarer Text (eingescannt?).")
            else {
                val neu = if (song.lyrics.isBlank()) text else song.lyrics.trimEnd() + "\n\n" + text
                songAendern(song.id) { it.copy(lyrics = neu) }
                App.buehne.hinweis(if (song.lyrics.isBlank()) "Text aus dem PDF übernommen. Bitte kurz prüfen." else "Text aus dem PDF unten angehängt.")
            }
        }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        T(if (abschnitte.isEmpty()) "Noch kein Text. Abschnitte mit [Strophe], [Refrain] … beginnen." else "${abschnitte.size} Abschnitte · ${marken.size} eingemessen", textStil(15.sp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TextKnopf("Aus PDF holen", Modifier.weight(1f), hoehe = 48.dp, groesse = 16.sp) { pdf.launch(arrayOf("application/pdf")) }
            TextKnopf("Einmessen", Modifier.weight(1f), Art.VOLL, 48.dp, 16.sp, aktiv = abschnitte.isNotEmpty()) { navi.zu(Seite.Einmessen(song.id)) }
        }
        BasicTextField(
            value = song.lyrics, onValueChange = { t -> songAendern(song.id) { it.copy(lyrics = t) } },
            textStyle = textStil(18.sp, gewicht = FontWeight.Medium),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth().weight(1f).border(2.5.dp, Schwarz, RoundedCornerShape(12.dp)).padding(12.dp)
        )
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun AnsageReiter(song: Song) {
    val ctx = LocalContext.current
    val z by App.buehne.zustand.collectAsState()
    var laufend by remember { mutableStateOf<Triple<MediaRecorder, File, Boolean>?>(null) }
    var seit by remember { mutableLongStateOf(0L) }
    var hoert by remember { mutableStateOf<Boolean?>(null) }
    var warteAuf by remember { mutableStateOf<Boolean?>(null) }

    fun starten(vorher: Boolean) {
        if ((z.spielt || z.pause != null) && !z.probe) { App.buehne.hinweis("Während ein Song läuft, kann nicht aufgenommen werden."); return }
        val f = Aufnahme.dateiFuer(ctx, song.id, vorher)
        val r = Aufnahme.starten(ctx, f)
        if (r == null) { App.buehne.hinweis("Aufnahme startet nicht. Ist der Offline+-Schalter an? Er trennt das Mikrofon."); return }
        laufend = Triple(r, f, vorher); seit = System.currentTimeMillis()
    }

    val erlaubnis = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        val v = warteAuf; warteAuf = null
        if (ok && v != null) starten(v) else if (!ok) App.buehne.hinweis("Ohne Mikrofon-Erlaubnis keine Ansagen.")
    }

    fun stoppen() {
        val (r, f, vorher) = laufend ?: return
        laufend = null
        val ms = Aufnahme.stoppen(r, f)
        if (ms < 400) { f.delete(); App.buehne.hinweis("Aufnahme war leer. Mikrofon frei? Offline+-Schalter aus?"); return }
        songAendern(song.id) { s ->
            if (vorher) { if (s.ansageVorher.isNotBlank()) File(s.ansageVorher).delete(); s.copy(ansageVorher = f.absolutePath, ansageVorherMs = ms) }
            else { if (s.ansageNachher.isNotBlank()) File(s.ansageNachher).delete(); s.copy(ansageNachher = f.absolutePath, ansageNachherMs = ms) }
        }
    }

    DisposableEffect(Unit) { onDispose { laufend?.let { (r, f, _) -> Aufnahme.stoppen(r, f); f.delete() }; App.buehne.notizStoppen() } }
    Lautstaerketasten { }

    val l = laufend
    if (l != null) {
        var jetzt by remember { mutableLongStateOf(System.currentTimeMillis()) }
        LaunchedEffect(Unit) { while (true) { jetzt = System.currentTimeMillis(); delay(250) } }
        Column(Modifier.fillMaxSize().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Etikett(if (l.third) "Aufnahme · Ansage vor dem Song" else "Aufnahme · Ansage nach dem Song")
            T(zeit(jetzt - seit), titelStil(110.sp).copy(lineHeight = 110.sp))
            T("Sprich jetzt. Die Ansage läuft später nur rechts im Ohr.", textStil(16.sp), mitte = true)
            Abstand(24.dp)
            TextKnopf("Aufnahme beenden", Modifier.fillMaxWidth(), Art.VOLL, 72.dp, 20.sp, symbol = Sym.STOP) { stoppen() }
        }
        return
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        for (vorher in listOf(true, false)) {
            val pfad = if (vorher) song.ansageVorher else song.ansageNachher
            val ms = if (vorher) song.ansageVorherMs else song.ansageNachherMs
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Etikett(if (vorher) "Ansage vor dem Song" else "Ansage nach dem Song")
                T(if (pfad.isBlank()) "keine" else zeit(ms), titelStil(28.sp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val hoertHier = hoert == vorher
                    TextKnopf(if (hoertHier) "Stopp" else "Anhören", Modifier.weight(1f), hoehe = 48.dp, groesse = 15.sp, symbol = if (hoertHier) Sym.STOP else Sym.PLAY, aktiv = pfad.isNotBlank()) {
                        if (hoertHier) { App.buehne.notizStoppen(); hoert = null } else { hoert = vorher; App.buehne.notizAnhoeren(pfad) { hoert = null } }
                    }
                    TextKnopf("Aufnehmen", Modifier.weight(1f), Art.VOLL, 48.dp, 15.sp, symbol = Sym.AUFNAHME) {
                        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) starten(vorher)
                        else { warteAuf = vorher; erlaubnis.launch(Manifest.permission.RECORD_AUDIO) }
                    }
                    SymbolKnopf(Sym.KREUZ, "Ansage löschen", Modifier.width(52.dp), Art.GESTRICHELT, 48.dp, 16.dp, aktiv = pfad.isNotBlank()) {
                        File(pfad).delete()
                        songAendern(song.id) { s -> if (vorher) s.copy(ansageVorher = "", ansageVorherMs = 0) else s.copy(ansageNachher = "", ansageNachherMs = 0) }
                    }
                }
            }
            Linie()
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Etikett("Stille Pause danach (nur ohne Ansage)")
            Stufen(pauseText(song.pauseSek), { songAendern(song.id) { it.copy(pauseSek = pauseSchritt(it.pauseSek, -1)) } }, { songAendern(song.id) { it.copy(pauseSek = pauseSchritt(it.pauseSek, 1)) } })
        }
    }
}

// ── Lyrics einmessen ─────────────────────────────────────────────────────────

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun EinmessenSeite(navi: Navi, songId: Long) {
    val b by App.ablage.stand.collectAsState()
    val z by App.buehne.zustand.collectAsState()
    val pos by App.buehne.position.collectAsState()
    val song = b.song(songId) ?: run { WegHier(navi); return }
    val abschnitte = remember(song.lyrics) { Abschnitte.zerlegen(song.lyrics) }
    val marken = Abschnitte.markenLesen(song.marken)
    val laeuft = z.probe && z.songId == songId
    val naechster = (0 until abschnitte.size).firstOrNull { it !in marken }
    DisposableEffect(Unit) { onDispose { App.buehne.probeEnde() } }
    Lautstaerketasten { }
    fun markenSetzen(m: Map<Int, Long>) = songAendern(songId) { it.copy(marken = Abschnitte.markenSchreiben(m)) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().background(Schwarz).padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Etikett("Lyrics einmessen", farbe = Weiss)
                T(song.titel, titelStil(26.sp, Weiss), zeilen = 1)
            }
            T(if (laeuft) "${zeit(pos)} / ${zeit(z.dauerMs)}" else zeit(song.dauerMs), titelStil(22.sp, Weiss))
        }
        T(if (laeuft) "Tippe genau dann, wenn der nächste Abschnitt anfängt." else "Starte den Song und tippe bei jedem Abschnittsbeginn auf den großen Knopf.", textStil(15.sp), Modifier.padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 4.dp))
        val fenster = run {
            val mitte = naechster ?: abschnitte.size - 1
            val start = (mitte - 2).coerceAtLeast(0)
            start until minOf(abschnitte.size, start + 7)
        }
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            for (i in fenster) {
                val gesetzt = marken[i]
                val istNaechster = i == naechster
                val form = RoundedCornerShape(10.dp)
                Row(
                    Modifier.fillMaxWidth().height(40.dp).then(if (istNaechster) Modifier.clip(form).gestrichelt(10.dp, 2.5.dp) else Modifier).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(Modifier.width(22.dp), contentAlignment = Alignment.Center) {
                        when {
                            gesetzt != null -> Box(Modifier.size(18.dp).clip(RoundedCornerShape(9.dp)).background(Schwarz), contentAlignment = Alignment.Center) { Symbol(Sym.HAKEN, Weiss, 11.dp) }
                            istNaechster -> Symbol(Sym.PLAY, Schwarz, 14.dp)
                            else -> Box(Modifier.size(12.dp).border(2.dp, Schwarz, RoundedCornerShape(6.dp)))
                        }
                    }
                    T(abschnitte[i].name, textStil(18.sp, gewicht = if (istNaechster) FontWeight.Bold else FontWeight.SemiBold), Modifier.weight(1f), zeilen = 1)
                    T(gesetzt?.let { zeit(it) } ?: "–", titelStil(18.sp, gewicht = FontWeight.SemiBold))
                }
            }
        }
        Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!laeuft) {
                Knopf(Modifier.fillMaxWidth(), Art.VOLL, 92.dp, 16.dp, onClick = { App.buehne.probe(songId) }) { f ->
                    Symbol(Sym.PLAY, f, 26.dp); T(if (marken.isEmpty()) "Song starten" else "Song von vorn starten", titelStil(30.sp, f))
                }
            } else if (naechster != null) {
                Knopf(Modifier.fillMaxWidth(), Art.VOLL, 92.dp, 16.dp, onClick = { markenSetzen(marken + (naechster to App.buehne.position.value)) }) { f ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Etikett("Jetzt beginnt", farbe = f)
                        T(abschnitte[naechster].name, titelStil(36.sp, f), zeilen = 1)
                    }
                }
            } else {
                Knopf(Modifier.fillMaxWidth(), Art.UMRISS, 92.dp, 16.dp, onClick = { App.buehne.probeEnde(); navi.zurueck() }) { f ->
                    Symbol(Sym.HAKEN, f, 24.dp); T("Alles eingemessen", titelStil(30.sp, f))
                }
            }
        }
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TextKnopf("Letzten Tipp löschen", Modifier.weight(1f), hoehe = 50.dp, groesse = 15.sp, aktiv = marken.isNotEmpty()) {
                val letzter = marken.maxByOrNull { it.value }?.key
                if (letzter != null) markenSetzen(marken - letzter)
            }
            TextKnopf("10 s zurück", Modifier.weight(1f), hoehe = 50.dp, groesse = 15.sp, aktiv = laeuft) { App.buehne.springen(-10_000) }
        }
    }
}

// ── Mixer ────────────────────────────────────────────────────────────────────

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun MixerSeite(navi: Navi, songId: Long) {
    val ctx = LocalContext.current
    val b by App.ablage.stand.collectAsState()
    val song = b.song(songId) ?: run { WegHier(navi); return }
    var kanaele by remember { mutableStateOf<List<Pair<String, Rolle>>?>(null) }
    LaunchedEffect(song.pfad) { kanaele = withContext(Dispatchers.IO) { Spuren.kanaele(Spuren.finden(ctx, song.pfad)) } }
    Lautstaerketasten { }
    fun setzen(name: String, wert: Int) {
        songAendern(songId) { it.copy(mix = it.mix + (name to wert)) }
        App.buehne.mixGeaendert(songId)
    }
    Column(Modifier.fillMaxSize()) {
        KopfBand("Mixer · gilt nur für diesen Song", song.titel, rechts = { WeisserKnopfImBand("Fertig") { navi.zurueck() } }, zurueck = { navi.zurueck() })
        val k = kanaele
        Column(Modifier.weight(1f).padding(top = 4.dp)) {
            if (k == null) T("…", textStil(18.sp), Modifier.padding(18.dp))
            else if (k.isEmpty()) T("Keine Audiodatei gefunden.", textStil(18.sp), Modifier.padding(18.dp))
            else for (rolle in listOf(Rolle.BAND, Rolle.OHR)) {
                val gruppe = k.filter { it.second == rolle }
                if (gruppe.isEmpty()) continue
                Row(Modifier.padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Etikett(if (rolle == Rolle.BAND) "Fürs Publikum · links" else "Nur im Ohr · rechts")
                    Linie(Modifier.weight(1f), 1.5.dp)
                }
                for ((name, _) in gruppe.take(if (rolle == Rolle.BAND) 5 else 2)) {
                    val roh = song.mix[name] ?: 10
                    val stumm = roh < 0
                    val stufe = kotlin.math.abs(roh).coerceIn(0, 10)
                    Row(Modifier.padding(horizontal = 12.dp).height(50.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        T(name.replaceFirstChar { it.uppercase() }, titelStil(20.sp), Modifier.width(66.dp), zeilen = 1, durchgestrichen = stumm)
                        SymbolKnopf(Sym.MINUS, "leiser", Modifier.width(40.dp), hoehe = 44.dp, groesse = 16.dp) { setzen(name, (stufe - 1).coerceAtLeast(1).let { if (stumm) -it else it }) }
                        Row(Modifier.weight(1f).height(26.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
                            for (i in 0 until 10) {
                                val m = Modifier.weight(1f).height((8 + i * 2).dp)
                                Box(if (!stumm && i < stufe) m.background(Schwarz) else m.border(1.5.dp, Schwarz))
                            }
                        }
                        SymbolKnopf(Sym.PLUS, "lauter", Modifier.width(40.dp), hoehe = 44.dp, groesse = 16.dp) { setzen(name, (stufe + 1).coerceAtMost(10).let { if (stumm) -it else it }) }
                        Knopf(Modifier.width(48.dp), if (stumm) Art.VOLL else Art.UMRISS, 44.dp, 10.dp, onClick = { setzen(name, if (stumm) stufe.coerceAtLeast(1) else -stufe.coerceAtLeast(1)) }) { f ->
                            T("AUS", titelStil(15.sp, f))
                        }
                    }
                }
            }
        }
        TextKnopf("Alles auf Standard", Modifier.padding(14.dp).fillMaxWidth(), hoehe = 50.dp, groesse = 16.sp) {
            songAendern(songId) { it.copy(mix = emptyMap()) }; App.buehne.mixGeaendert(songId)
        }
    }
}


// ── Eingabe und Rückfrage ────────────────────────────────────────────────────

@Composable
fun EingabeSeite(navi: Navi, s: Seite.Eingabe) {
    var text by remember(s) { mutableStateOf(s.start) }
    val fokus = remember { FocusRequester() }
    LaunchedEffect(s) { runCatching { fokus.requestFocus() } }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        T(s.titel, titelStil(30.sp))
        if (s.hinweis.isNotBlank()) T(s.hinweis, textStil(16.sp))
        BasicTextField(
            value = text, onValueChange = { text = it }, singleLine = true, textStyle = textStil(24.sp),
            keyboardOptions = KeyboardOptions(keyboardType = if (s.zahl) KeyboardType.Number else KeyboardType.Text, capitalization = KeyboardCapitalization.Sentences, imeAction = androidx.compose.ui.text.input.ImeAction.Done),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { s.ok(text) }),
            modifier = Modifier.fillMaxWidth().height(58.dp).border(2.5.dp, Schwarz, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 12.dp).focusRequester(fokus)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TextKnopf("Abbrechen", Modifier.weight(1f), Art.GESTRICHELT) { navi.zurueck() }
            TextKnopf("OK", Modifier.weight(1f), Art.VOLL) { s.ok(text) }
        }
    }
}

@Composable
fun FrageSeite(navi: Navi, s: Seite.Frage) {
    Lautstaerketasten { }
    Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Abstand(30.dp)
        T(s.titel, titelStil(34.sp))
        T(s.text, textStil(18.sp))
        Abstand(10.dp)
        TextKnopf(s.ja, Modifier.fillMaxWidth(), Art.VOLL) { s.aktion() }
        TextKnopf("Abbrechen", Modifier.fillMaxWidth()) { navi.zurueck() }
    }
}
