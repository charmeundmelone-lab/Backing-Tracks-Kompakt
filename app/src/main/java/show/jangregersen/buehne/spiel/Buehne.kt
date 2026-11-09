package show.jangregersen.buehne.spiel

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import show.jangregersen.buehne.audio.Engine
import show.jangregersen.buehne.audio.Spuren
import show.jangregersen.buehne.audio.Warnton
import show.jangregersen.buehne.daten.Ablage
import show.jangregersen.buehne.daten.Ende
import show.jangregersen.buehne.daten.Song
import show.jangregersen.buehne.daten.eintragAendern
import show.jangregersen.buehne.daten.ersterOffener
import show.jangregersen.buehne.daten.naechsterNach
import java.io.File

private const val TAG = "Buehne"

/**
 * Herz der App: welcher Song liegt bereit oder läuft, welches Set ist aktiv, und die
 * Show-Automatik zwischen den Songs. Lebt in der Application (nicht in einer Activity), damit
 * Bildschirmwechsel die Wiedergabe nie berühren. Ein Vordergrund-Dienst hält den Prozess
 * am Leben, solange gespielt wird.
 */
@UnstableApi
class Buehne(private val app: Application, private val ablage: Ablage) {

    data class Pause(
        val restSek: Int,
        val gesamtSek: Int,
        /** "Ansage" oder "Pause". */
        val art: String,
        val beschreibung: String
    )

    data class Zustand(
        val songId: Long? = null,
        val setId: Long? = null,
        val spielt: Boolean = false,
        val dauerMs: Long = 0,
        val pause: Pause? = null,
        /** Song läuft außerhalb eines Sets (Probehören zu Hause). */
        val probe: Boolean = false,
        val fehler: String? = null,
        val hinweis: String? = null
    )

    private val engine = Engine(app)
    private val haupt = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _zustand = MutableStateFlow(Zustand())
    val zustand: StateFlow<Zustand> = _zustand.asStateFlow()

    /** Wiedergabeposition, alle 200 ms aktualisiert. Bewusst getrennt vom Zustand. */
    private val _position = MutableStateFlow(0L)
    val position: StateFlow<Long> = _position.asStateFlow()

    private var pauseJob: Job? = null
    private var hinweisJob: Job? = null
    private var endeBehandelt = false

    val engineZumAnhoeren: Engine get() = engine

    init {
        engine.onFehler = { text -> _zustand.update { it.copy(fehler = text) } }
        haupt.launch {
            while (true) {
                takt()
                delay(200)
            }
        }
    }

    // ── Takt: Position, Songende ─────────────────────────────────────────────

    private fun takt() {
        val z = _zustand.value
        if (engine.hatSpuren) {
            _position.value = engine.positionMs
            val d = engine.dauerMs
            if (d > 0 && d != z.dauerMs) _zustand.update { it.copy(dauerMs = d) }
        }
        if (z.pause == null && z.spielt && engine.istZuEnde && !endeBehandelt) {
            endeBehandelt = true
            songEnde()
            return
        }
        if (z.pause != null || z.spielt) return
        val b = ablage.jetzt
        // Song wurde gelöscht oder aus dem Set genommen, während er nur bereit lag.
        if (z.songId != null && b.song(z.songId) == null) {
            engine.entladen(); _position.value = 0
            _zustand.update { it.copy(songId = null, dauerMs = 0) }
            return
        }
        // Set wurde gewählt, als es noch leer war, und hat inzwischen Songs: ersten bereitlegen.
        if (z.setId != null && z.songId == null && !z.probe) {
            val erster = b.set(z.setId)?.ersterOffener() ?: return
            b.song(erster.songId)?.let { laden(it, z.setId) }
        }
    }

    private fun songEnde() {
        val z = _zustand.value
        val songId = z.songId ?: return
        val setId = z.setId
        if (z.probe || setId == null) {
            engine.stop(); _position.value = 0
            _zustand.update { it.copy(spielt = false) }
            dienstAus()
            return
        }
        ablage.aendern { it.eintragAendern(setId, songId) { e -> e.copy(gespielt = true) } }
        val b = ablage.jetzt
        val set = b.set(setId) ?: return
        val ende = set.eintraege.firstOrNull { it.songId == songId }?.ende ?: Ende.WEITER
        val naechster = set.naechsterNach(songId)
        if (naechster == null) {
            setFertig(setId)
            return
        }
        val kommt = b.song(naechster.songId) ?: return
        if (ende == Ende.ANHALTEN) {
            laden(kommt, setId)
            _zustand.update { it.copy(spielt = false) }
        } else {
            val fertig = b.song(songId)
            if (fertig != null) automatik(fertig, kommt, setId) else { laden(kommt, setId); abspielen() }
        }
    }

    private fun setFertig(setId: Long) {
        val b = ablage.jetzt
        val gig = b.gigVonSet(setId)
        val idx = gig?.sets?.indexOfFirst { it.id == setId } ?: -1
        val folgeSet = gig?.sets?.getOrNull(idx + 1)
        engine.stop(); _position.value = 0
        _zustand.update { it.copy(spielt = false) }
        dienstAus()
        val name = b.set(setId)?.name ?: "Set"
        if (gig != null && gig.autoWeiter && folgeSet != null && folgeSet.ersterOffener() != null) {
            setWaehlen(folgeSet.id)
            hinweis("„$name“ ist durch. „${folgeSet.name}“ liegt bereit.")
        } else {
            hinweis("„$name“ ist durch.")
        }
    }

    // ── Show-Automatik ───────────────────────────────────────────────────────

    private fun automatik(fertig: Song, kommt: Song, setId: Long) {
        pauseJob?.cancel()
        laden(kommt, setId)
        _zustand.update { it.copy(spielt = false) }
        pauseJob = haupt.launch {
            var lief = false
            if (segment(fertig.ansageNachher, "Ansage nach „${fertig.titel}“")) lief = true
            if (segment(kommt.ansageVorher, "Ansage vor „${kommt.titel}“")) lief = true
            val sek = fertig.pauseSek.takeIf { it > 0 } ?: kommt.pauseSek
            if (!lief && sek > 0) {
                val gesamtMs = sek * 1000L
                val warnVorMs = (gesamtMs / 3).coerceAtMost(5 * 60_000L)
                var warnGespielt = false
                val start = System.currentTimeMillis()
                while (true) {
                    val rest = gesamtMs - (System.currentTimeMillis() - start)
                    if (rest <= 0) break
                    pauseSetzen(Pause(((rest + 999) / 1000).toInt(), sek, "Pause", "Stille Pause bis zum nächsten Song"))
                    if (!warnGespielt && rest <= warnVorMs) {
                        warnGespielt = true
                        engine.notizSpielen(Warnton.datei(app).absolutePath, 0.5f) { }
                    }
                    delay(100)
                }
            }
            nachPauseStarten()
        }
    }

    /** Spielt eine Ansage rechts ab, zählt dabei herunter. false = keine (lesbare) Ansage da. */
    private suspend fun segment(pfad: String, beschreibung: String): Boolean {
        if (pfad.isBlank()) return false
        val f = File(pfad)
        if (!f.exists() || f.length() == 0L) {
            _zustand.update { it.copy(fehler = "$beschreibung fehlt, bitte neu aufnehmen") }
            return false
        }
        var fertig = false
        engine.notizSpielen(pfad) { fertig = true }
        while (!fertig) {
            val dauer = engine.notizDauerMs.takeIf { it > 0 } ?: 1000L
            val rest = (dauer - engine.notizPositionMs).coerceAtLeast(0)
            pauseSetzen(Pause(((rest + 999) / 1000).toInt(), ((dauer + 999) / 1000).toInt(), "Ansage", beschreibung))
            delay(100)
        }
        return true
    }

    private fun pauseSetzen(p: Pause) { if (_zustand.value.pause != p) _zustand.update { it.copy(pause = p) } }

    private fun nachPauseStarten() {
        _zustand.update { it.copy(pause = null) }
        abspielen()
    }

    // ── Bedienung ────────────────────────────────────────────────────────────

    fun playPause() {
        val z = _zustand.value
        if (z.pause != null) { pauseUeberspringen(); return }
        if (!engine.hatSpuren) {
            val setId = z.setId ?: return
            val erster = ablage.jetzt.set(setId)?.ersterOffener() ?: run { hinweis("Alle Songs im Set sind gespielt."); return }
            ablage.jetzt.song(erster.songId)?.let { laden(it, setId) }
        }
        if (engine.spielt) { engine.pause(); _zustand.update { it.copy(spielt = false) } }
        else abspielen()
    }

    fun stopp() {
        val z = _zustand.value
        if (z.pause != null) {
            pauseJob?.cancel(); engine.notizStoppen()
            _zustand.update { it.copy(pause = null, spielt = false) }
            hinweis("Automatik angehalten. Der nächste Song liegt bereit.")
            return
        }
        engine.stop(); _position.value = 0
        _zustand.update { it.copy(spielt = false) }
    }

    /** Pause/Ansage abbrechen und den nächsten Song sofort starten. */
    fun pauseUeberspringen() {
        if (_zustand.value.pause == null) return
        pauseJob?.cancel(); engine.notizStoppen()
        nachPauseStarten()
    }

    /** Nächsten Song bereitlegen (laufender gilt als gespielt). */
    fun weiter() {
        val z = _zustand.value
        if (z.pause != null) { pauseUeberspringen(); return }
        val setId = z.setId ?: return
        val songId = z.songId
        if (songId != null) ablage.aendern { it.eintragAendern(setId, songId) { e -> e.copy(gespielt = true) } }
        val naechster = ablage.jetzt.set(setId)?.naechsterNach(songId)
        if (naechster == null) { setFertig(setId); return }
        ablage.jetzt.song(naechster.songId)?.let { laden(it, setId) }
        _zustand.update { it.copy(spielt = false) }
    }

    /** Set aktiv machen. Läuft gerade ein Song, wird er nicht unterbrochen. */
    fun setWaehlen(setId: Long) {
        val z = _zustand.value
        if (z.spielt || z.pause != null) { _zustand.update { it.copy(setId = setId, probe = false) }; return }
        val erster = ablage.jetzt.set(setId)?.ersterOffener()
        if (erster == null) {
            engine.entladen()
            _zustand.update { it.copy(setId = setId, songId = null, probe = false, dauerMs = 0) }
            return
        }
        ablage.jetzt.song(erster.songId)?.let { laden(it, setId) }
    }

    /** Einen bestimmten Song im Set bereitlegen (nur wenn nichts läuft). */
    fun bereitlegen(setId: Long, songId: Long): Boolean {
        val z = _zustand.value
        if (z.spielt || z.pause != null) return false
        ablage.jetzt.song(songId)?.let { laden(it, setId) }
        return true
    }

    /** Probehören außerhalb eines Sets. Verweigert, solange ein Set-Song läuft. */
    fun probe(songId: Long, abMs: Long = 0): Boolean {
        val z = _zustand.value
        if ((z.spielt || z.pause != null) && !z.probe) { hinweis("Erst den laufenden Song stoppen."); return false }
        val s = ablage.jetzt.song(songId) ?: return false
        laden(s, null, probe = true)
        if (abMs > 0) engine.seek(abMs)
        abspielen()
        return true
    }

    fun probeEnde() {
        val z = _zustand.value
        if (!z.probe) return
        engine.stop(); engine.entladen(); _position.value = 0
        _zustand.update { it.copy(songId = null, spielt = false, probe = false, dauerMs = 0) }
        dienstAus()
        z.setId?.let { setWaehlen(it) }
    }

    fun springen(deltaMs: Long) { if (engine.hatSpuren) { engine.seek(engine.positionMs + deltaMs); _position.value = engine.positionMs } }

    fun mixGeaendert(songId: Long) {
        if (engine.geladeneSongId == songId) ablage.jetzt.song(songId)?.let { engine.mixSetzen(it.mix) }
    }

    fun fehlerWeg() { _zustand.update { it.copy(fehler = null) } }

    fun hinweis(text: String) {
        _zustand.update { it.copy(hinweis = text) }
        hinweisJob?.cancel()
        hinweisJob = haupt.launch { delay(5000); _zustand.update { it.copy(hinweis = null) } }
    }

    fun notizAnhoeren(pfad: String, fertig: () -> Unit) = engine.notizSpielen(pfad) { fertig() }
    fun notizStoppen() = engine.notizStoppen()

    // ── Intern ───────────────────────────────────────────────────────────────

    private fun laden(song: Song, setId: Long?, probe: Boolean = false) {
        endeBehandelt = false
        val spuren = Spuren.finden(app, song.pfad)
        if (spuren.isEmpty()) {
            _zustand.update { it.copy(fehler = "Keine Audiodatei für „${song.titel}“ gefunden. Ist der Ordner noch da?") }
        }
        engine.laden(song.id, spuren, song.mix)
        _position.value = 0
        _zustand.update { it.copy(songId = song.id, setId = setId ?: it.setId, probe = probe, dauerMs = song.dauerMs, spielt = false) }
        if (!probe && setId != null) vorladen(setId, song.id)
    }

    private fun vorladen(setId: Long, songId: Long) {
        val n = ablage.jetzt.set(setId)?.naechsterNach(songId) ?: return
        val s = ablage.jetzt.song(n.songId) ?: return
        haupt.launch {
            val spuren = withContext(Dispatchers.IO) { Spuren.finden(app, s.pfad) }
            if (_zustand.value.songId == songId) engine.vorladen(s.id, spuren, s.mix)
        }
    }

    private fun abspielen() {
        if (!engine.hatSpuren) return
        endeBehandelt = false
        engine.play()
        _zustand.update { it.copy(spielt = true) }
        dienstAn()
    }

    private fun dienstAn() {
        runCatching { ContextCompat.startForegroundService(app, Intent(app, WiedergabeDienst::class.java)) }
            .onFailure { Log.w(TAG, "Dienst nicht startbar: ${it.message}") }
    }

    private fun dienstAus() { runCatching { app.stopService(Intent(app, WiedergabeDienst::class.java)) } }
}
