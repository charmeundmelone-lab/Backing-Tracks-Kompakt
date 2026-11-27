package show.jangregersen.buehne.spiel

import android.app.Application
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
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
import show.jangregersen.buehne.audio.Stille
import show.jangregersen.buehne.audio.Warnton
import show.jangregersen.buehne.daten.Ablage
import show.jangregersen.buehne.daten.Ende
import show.jangregersen.buehne.daten.Song
import show.jangregersen.buehne.daten.pauseDanach
import show.jangregersen.buehne.daten.eintragAendern
import show.jangregersen.buehne.daten.ersterOffener
import show.jangregersen.buehne.daten.naechsterNach
import show.jangregersen.buehne.daten.setAendern
import show.jangregersen.buehne.daten.vorziehen
import show.jangregersen.buehne.daten.ansEnde
import java.io.File

private const val TAG = "Buehne"

/**
 * So lange nach dem letzten Ton oder Antippen gilt die Show noch als laufend (21.11.): Bildschirm
 * bleibt an, Lautstärke und „Bitte nicht stören“ bleiben, die Tonausgabe bleibt wach. Deckt lange
 * Ansagen und eine normale Set-Pause ab. Danach darf der Mudita wieder schlafen (Akku).
 */
private const val SHOW_NACHLAUF_MS = 60 * 60_000L
/** Ist TrikTrak nicht zu sehen (Home gedrückt, Bildschirm von Hand aus), endet die Show schneller. */
private const val SHOW_NACHLAUF_VERSTECKT_MS = 15 * 60_000L

/** „Noch nie“ für [Buehne.letzteAktion]: weit genug zurück, dass jede Nachlaufzeit abgelaufen ist. */
private const val NIE = Long.MIN_VALUE / 4

/** So lange wartet der Stopp-Knopf auf den zweiten Tipp, während ein Song läuft (27.11.). */
private const val STOPP_SCHARF_MS = 4000L

/**
 * Herz der App: welcher Song liegt bereit oder läuft, welches Set ist aktiv, und die
 * Show-Automatik zwischen den Songs. Lebt in der Application (nicht in einer Activity), damit
 * Bildschirmwechsel die Wiedergabe nie berühren. Ein Vordergrund-Dienst hält den Prozess
 * am Leben, solange gespielt wird.
 */
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
        val hinweis: String? = null,
        /** Bleibender Hinweis auf der Bühne nach dem letzten Song eines Sets, bis wieder gespielt wird. */
        val setDurch: String? = null,
        /** Set, das nach dem laufenden Song beginnt (gewählt, während ein Song lief). */
        val setDanach: Long? = null,
        /** Hinweis mit Knopf "Rückgängig" nach dem Antippen eines Songs (verschwindet nach 8 s). */
        val rueckgaengig: String? = null,
        /** Zählt jeden frischen Start eines Set-Songs (ab 0:00). Der Hauptbildschirm öffnet dann den Text (20.11.). */
        val startNr: Int = 0,
        /** Stopp wurde einmal getippt, während der Song läuft: der nächste Tipp hält an (27.11.). */
        val stoppScharf: Boolean = false
    )

    /** Stand vor dem letzten Antippen, für "Rückgängig". */
    private class Vorher(val setId: Long, val eintraege: List<show.jangregersen.buehne.daten.Eintrag>, val songId: Long?, val laeuft: Boolean, val songIdDanach: Long?)
    private var vorher: Vorher? = null
    private var rueckJob: Job? = null

    private val engine = Engine(app)
    private val haupt = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _zustand = MutableStateFlow(Zustand())
    val zustand: StateFlow<Zustand> = _zustand.asStateFlow()

    /** Wiedergabeposition, alle 200 ms aktualisiert. Bewusst getrennt vom Zustand. */
    private val _position = MutableStateFlow(0L)
    val position: StateFlow<Long> = _position.asStateFlow()

    private var pauseJob: Job? = null
    private var hinweisJob: Job? = null
    private var scharfJob: Job? = null
    private var endeBehandelt = false
    /** Song, der vor dem Probehören bereitlag. */
    private var vorProbe: Long? = null

    val engineZumAnhoeren: Engine get() = engine

    /** Show läuft: ein Set ist aktiv und es wurde in der letzten Stunde gespielt oder getippt (21.11.). */
    private val _show = MutableStateFlow(false)
    val showLaeuft: StateFlow<Boolean> = _show.asStateFlow()
    /**
     * Zeitpunkt des letzten Tons oder Tipps in einer laufenden Show. Beim Start „nie“ (27.11.): Vorher
     * zählte schon das Öffnen der App als Show, und „Bitte nicht stören“ blieb danach 15 min an, auch
     * wenn Jan TrikTrak nur kurz angesehen hatte (Anrufe kamen stumm an).
     */
    @Volatile private var letzteAktion = NIE
    private var dienstLaeuft = false

    /** TrikTrak ist gerade zu sehen (MainActivity onStart/onStop). */
    @Volatile var sichtbar = false

    /**
     * Jan hat den Bildschirm berührt oder die App geöffnet (MainActivity). Verlängert nur eine Show,
     * die schon läuft (nach dem ersten Song oder in der Set-Pause). Anfangen kann sie nur mit Ton.
     */
    fun beruehrt() { if (_show.value) letzteAktion = SystemClock.elapsedRealtime() }

    /**
     * Zuletzt gewähltes Set (21.11., Jan: nach einem Neustart hieß es „Noch kein Set gewählt“).
     * Steht in einer kleinen Datei, nicht im Bestand; beim Start liegt dieses Set wieder bereit.
     */
    private val merkDatei: File get() = File(app.filesDir, if (ablage.testmodus) "letztes-set-test.txt" else "letztes-set.txt")
    private var gemerktesSet: Long? = null

    init {
        engine.onFehler = { text -> _zustand.update { it.copy(fehler = text) } }
        runCatching { merkDatei.readText().trim().toLong() }.getOrNull()
            ?.takeIf { ablage.jetzt.set(it) != null }
            ?.let { runCatching { setWaehlen(it) }.onFailure { e -> Log.w(TAG, "Letztes Set: ${e.message}") } }
        gemerktesSet = _zustand.value.setId
        haupt.launch {
            while (true) {
                takt()
                // Akku (21.11.): nur während Song oder Pause fünfmal pro Sekunde nachsehen, sonst einmal.
                val z = _zustand.value
                delay(if (z.spielt || z.pause != null) 200 else 1000)
            }
        }
    }

    private fun showPruefen(z: Zustand) {
        val jetzt = SystemClock.elapsedRealtime()
        val laeuft = z.spielt || z.pause != null
        if (laeuft) letzteAktion = jetzt
        val setDa = z.setId != null && ablage.jetzt.set(z.setId) != null
        val an = laeuft || (setDa && jetzt - letzteAktion < if (sichtbar) SHOW_NACHLAUF_MS else SHOW_NACHLAUF_VERSTECKT_MS)
        if (an != _show.value) _show.value = an
        if (an) Stille.an() else Stille.aus()
        // Der Dienst hält den Prozess auch in der Set-Pause am Leben, nicht nur während ein Song läuft.
        if (an && !dienstLaeuft) dienstAn() else if (!an && dienstLaeuft) dienstAus()
    }

    // ── Takt: Position, Songende ─────────────────────────────────────────────

    private fun takt() {
        val z = _zustand.value
        showPruefen(z)
        // Stopp „scharf“ gilt nur, solange der Song läuft (Songende, Pause-Knopf usw. nehmen es zurück).
        if (z.stoppScharf && !z.spielt) { scharfJob?.cancel(); _zustand.update { it.copy(stoppScharf = false) } }
        if (z.setId != null && z.setId != gemerktesSet && !z.probe) {
            gemerktesSet = z.setId
            runCatching { merkDatei.writeText(z.setId.toString()) }
        }
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

    private fun songEnde(art: String = "zu Ende") {
        val z = _zustand.value
        val songId = z.songId ?: return
        val setId = z.setId
        if (z.probe || setId == null) {
            engine.stop(); _position.value = 0
            _zustand.update { it.copy(spielt = false) }
            dienstAus()
            return
        }
        protokoll(art, songId, setId, if (art == "zu Ende") null else engine.positionMs)
        ablage.aendern { it.eintragAendern(setId, songId) { e -> e.copy(gespielt = true) } }
        if (setDanachBeginnen()) return
        val b = ablage.jetzt
        val set = b.set(setId) ?: return
        val ende = set.eintraege.firstOrNull { it.songId == songId }?.ende ?: Ende.ANHALTEN
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
        val text = if (gig != null && gig.autoWeiter && folgeSet != null && folgeSet.ersterOffener() != null) {
            setWaehlen(folgeSet.id)
            "„$name“ ist durch. „${folgeSet.name}“ liegt bereit."
        } else {
            "„$name“ ist durch."
        }
        _zustand.update { it.copy(setDurch = text) }
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
            // Seit 15.11.: Jeder Song hat eine Pause danach (10 s, 30 s, 1 min, 2 min; ohne Eintrag 10 s).
            val sek = fertig.pauseDanach
            if (!lief) {
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
        if (z.stoppScharf) { scharfJob?.cancel(); _zustand.update { it.copy(stoppScharf = false) } }
        // Play, während Stopp gerade ausblendet: Song beginnt wieder von vorn (Stopp = an den Anfang).
        if (engine.blendetAus) { engine.stop(); _position.value = 0; abspielen(); return }
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

    /**
     * Stopp-Knopf (27.11., Jan: „Stopp sicherer machen“). Er liegt direkt neben Play; ein Fehlgriff
     * schnitt bisher den Song ab, und Play begann ihn von vorn. Jetzt gilt: Läuft ein Song, macht der
     * erste Tipp den Knopf „scharf“ (Aufschrift „Nochmal tippen“, [STOPP_SCHARF_MS] lang), erst der
     * zweite hält an. Dann blendet der Song aus ([AUSBLENDEN_MS]) statt hart abzubrechen und steht
     * wieder am Anfang. Angehalten, in der Pause zwischen zwei Songs und beim Probehören reicht ein
     * Tipp (dort bricht nichts Hörbares ab). true = hat angehalten.
     */
    fun stoppTipp(): Boolean {
        val z = _zustand.value
        if (z.pause != null || z.probe || !z.spielt) { stopp(); return true }
        scharfJob?.cancel()
        if (!z.stoppScharf) {
            _zustand.update { it.copy(stoppScharf = true) }
            scharfJob = haupt.launch { delay(STOPP_SCHARF_MS); _zustand.update { it.copy(stoppScharf = false) } }
            return false
        }
        val songId = z.songId
        // spielt = false sofort: Läuft der Song während des Ausblendens zu Ende, zählt das nicht als Songende.
        _zustand.update { it.copy(stoppScharf = false, spielt = false) }
        engine.ausblendenUndStoppen { if (_zustand.value.songId == songId && !_zustand.value.spielt) _position.value = 0 }
        return true
    }

    /** Pause/Ansage abbrechen und den nächsten Song sofort starten. */
    fun pauseUeberspringen() {
        if (_zustand.value.pause == null) return
        pauseJob?.cancel(); engine.notizStoppen()
        nachPauseStarten()
    }

    /** Nächsten Song bereitlegen (laufender gilt als gespielt). Auf der Bühne: "Überspringen". */
    fun weiter() {
        val z = _zustand.value
        if (z.pause != null) { pauseUeberspringen(); return }
        val setId = z.setId ?: return
        val songId = z.songId
        if (songId != null) {
            protokoll("übersprungen", songId, setId, null)
            ablage.aendern { it.eintragAendern(setId, songId) { e -> e.copy(gespielt = true) } }
        }
        if (setDanachBeginnen()) return
        val naechster = ablage.jetzt.set(setId)?.naechsterNach(songId)
        if (naechster == null) { setFertig(setId); return }
        ablage.jetzt.song(naechster.songId)?.let { laden(it, setId) }
        _zustand.update { it.copy(spielt = false) }
    }

    /** Läuft gerade ein Song oder wurde er angespielt und pausiert (nicht die Pause zwischen Songs)? */
    fun angespielt(): Boolean {
        val z = _zustand.value
        return z.spielt || (z.pause == null && engine.hatSpuren && engine.positionMs > 0)
    }

    /**
     * "Beenden" auf der Bühne (zweiter Tipp): Der Song gilt als fertig, danach geht es genau so
     * weiter wie am normalen Songende (Pause/Ansage, Automatik, Set-Ende).
     */
    fun beenden() {
        val z = _zustand.value
        if (z.pause != null || z.songId == null) return
        if (z.probe) { stopp(); return }
        if (!angespielt()) return
        engine.pause()
        endeBehandelt = true
        songEnde("beendet")
    }

    /**
     * Gewähltes Set beginnt nach dem laufenden Song: jetzt bereitlegen. true = ist passiert.
     * Danach hält die App an (kein Automatik-Übergang in ein anderes Set).
     */
    private fun setDanachBeginnen(): Boolean {
        val danach = _zustand.value.setDanach ?: return false
        pauseJob?.cancel(); engine.notizStoppen()
        engine.stop(); _position.value = 0
        _zustand.update { it.copy(setDanach = null, spielt = false, pause = null) }
        dienstAus()
        setWaehlen(danach)
        val name = ablage.jetzt.set(danach)?.name ?: "Set"
        hinweis("„$name“ beginnt, der erste Song liegt bereit.")
        return true
    }

    /**
     * Set aktiv machen. Läuft gerade ein Song, wird er nicht unterbrochen und das Set beginnt erst
     * danach (früher wurde es sofort aktiv: der Song bekam dann keinen Haken, die Automatik stand
     * und als Nächstes kam ein Song aus dem anderen Set). In der Pause zwischen zwei Songs endet
     * die Automatik und das Set liegt sofort bereit.
     */
    fun setWaehlen(setId: Long) {
        val z0 = _zustand.value
        if (angespielt() && !z0.probe) {
            if (z0.setId == setId) { _zustand.update { it.copy(setDanach = null) }; return }
            // 21.11. (Jan: Set ließ sich nicht öffnen, solange ein Song auf Pause stand): Nur ein
            // Song, der wirklich läuft, schiebt das neue Set nach hinten. Ein angehaltener Song wird
            // beendet (bekommt keinen Haken), das gewählte Set liegt sofort bereit.
            if (z0.spielt) {
                _zustand.update { it.copy(setDanach = setId) }
                hinweis("„${ablage.jetzt.set(setId)?.name ?: "Set"}“ beginnt nach diesem Song.")
                return
            }
            engine.stop(); _position.value = 0
        }
        if (z0.pause != null) {
            pauseJob?.cancel(); engine.notizStoppen()
            _zustand.update { it.copy(pause = null, spielt = false) }
        }
        _zustand.update { if (it.setId != setId) it.copy(setDurch = null, setDanach = null) else it.copy(setDanach = null) }
        val z = _zustand.value
        if (z.spielt) { _zustand.update { it.copy(setId = setId, probe = false) }; return }
        val erster = ablage.jetzt.set(setId)?.ersterOffener()
        if (erster == null) {
            engine.entladen()
            _zustand.update { it.copy(setId = setId, songId = null, probe = false, dauerMs = 0) }
            return
        }
        ablage.jetzt.song(erster.songId)?.let { laden(it, setId) }
    }

    /**
     * Jans eine Regel (15.11.): Antippen = kommt als Nächstes dran. Läuft ein Song, kommt der
     * angetippte danach. In der Pause zwischen zwei Songs ersetzt er den wartenden Song, der
     * Countdown läuft weiter. Läuft nichts, liegt er sofort bereit. Danach gibt es 8 s lang
     * "Rückgängig". Ergebnis: Text für Jan, oder null, wenn nichts zu tun war.
     */
    fun vorziehen(setId: Long, songId: Long) {
        val z = _zustand.value
        if (z.probe) { hinweis("Erst das Probehören beenden."); return }
        if (songId == z.songId && setId == z.setId) return
        val song = ablage.jetzt.song(songId) ?: return
        // 21.11.: Antippen in einem ANDEREN Set, während ein Song läuft (Vorschau des Folge-Sets):
        // Der laufende Song bleibt unberührt. Der angetippte steht dort vorne, das Set beginnt danach.
        if (angespielt() && z.setId != null && z.setId != setId && ablage.jetzt.set(z.setId) != null) {
            val vor = ablage.jetzt.set(setId)?.eintraege ?: return
            ablage.aendern { it.vorziehen(setId, songId, null, false) }
            _zustand.update { it.copy(setDanach = setId) }
            val name = ablage.jetzt.set(setId)?.name ?: "Set"
            merken(Vorher(setId, vor, z.songId, true, z.songId), "Nach diesem Song: „$name“ mit „${song.titel}“.")
            return
        }
        val laeuft = angespielt() && z.setId == setId
        val vorEintraege = ablage.jetzt.set(setId)?.eintraege ?: return
        ablage.aendern { it.vorziehen(setId, songId, z.songId.takeIf { z.setId == setId }, laeuft) }
        val text: String
        if (laeuft) {
            z.songId?.let { vorladen(setId, it) }
            text = "„${song.titel}“ kommt als Nächstes."
        } else {
            laden(song, setId)
            _zustand.update { it.copy(setDurch = null, setDanach = null) }
            text = if (z.pause != null) "„${song.titel}“ kommt nach der Pause." else "„${song.titel}“ liegt bereit."
        }
        merken(Vorher(setId, vorEintraege, z.songId, laeuft, _zustand.value.songId), text)
    }

    /**
     * Wischen nach links (Jans Wunsch 15.11.): Song ganz ans Ende vom Set. Der laufende oder
     * bereite Song bleibt, wie er ist; nur was danach kommt, ändert sich. 8 s "Rückgängig".
     */
    fun ansEnde(setId: Long, songId: Long) {
        val z = _zustand.value
        if (z.probe) { hinweis("Erst das Probehören beenden."); return }
        if (songId == z.songId && setId == z.setId) { hinweis("Dieser Song ist gerade dran."); return }
        val song = ablage.jetzt.song(songId) ?: return
        val vorEintraege = ablage.jetzt.set(setId)?.eintraege ?: return
        ablage.aendern { it.ansEnde(setId, songId) }
        if (z.setId == setId) z.songId?.let { vorladen(setId, it) }
        // laeuft = true: Rückgängig stellt nur die Reihenfolge zurück und lädt den aktuellen Song nicht neu.
        merken(Vorher(setId, vorEintraege, z.songId, true, z.songId), "„${song.titel}“ steht jetzt am Ende.")
    }

    private fun merken(v: Vorher, text: String) {
        vorher = v
        _zustand.update { it.copy(rueckgaengig = text, hinweis = null) }
        rueckJob?.cancel()
        rueckJob = haupt.launch { delay(8000); vorher = null; _zustand.update { it.copy(rueckgaengig = null) } }
    }

    /** Letztes Antippen zurücknehmen, solange sich auf der Bühne nichts weiter getan hat. */
    fun rueckgaengig() {
        val v = vorher ?: return
        vorher = null; rueckJob?.cancel()
        _zustand.update { it.copy(rueckgaengig = null) }
        val z = _zustand.value
        val nochGleich = z.songId == v.songIdDanach && (v.laeuft || !z.spielt)
        if (!nochGleich) { hinweis("Geht nicht mehr, der Song hat schon begonnen."); return }
        ablage.aendern { it.setAendern(v.setId) { s -> s.copy(eintraege = v.eintraege) } }
        if (v.laeuft) {
            z.songId?.let { vorladen(v.setId, it) }
        } else {
            val alt = ablage.jetzt.song(v.songId)
            if (alt != null) laden(alt, v.setId) else { engine.entladen(); _position.value = 0; _zustand.update { it.copy(songId = null, dauerMs = 0) } }
        }
        hinweis("Zurückgenommen.")
    }

    /**
     * Beim Öffnen der App: Gibt es heute einen Gig (bis 6 Uhr morgens zählt noch der Vortag), wird
     * sein erstes offenes Set aktiv, sofern nicht schon ein Set dieses Gigs aktiv ist.
     * true = die App soll gleich auf der Bühne aufgehen.
     */
    fun gigHeute(): Boolean {
        val z = _zustand.value
        if (z.spielt || z.pause != null) return true
        val b = ablage.jetzt
        val jetzt = java.time.LocalDateTime.now()
        val tag = (if (jetzt.hour < 6) jetzt.toLocalDate().minusDays(1) else jetzt.toLocalDate()).toEpochDay()
        val gig = b.gigs.firstOrNull { it.datum == tag && it.sets.any { s -> s.eintraege.isNotEmpty() } } ?: return false
        if (gig.sets.any { it.id == z.setId }) return z.songId != null
        val set = gig.sets.firstOrNull { it.ersterOffener() != null } ?: gig.sets.firstOrNull { it.eintraege.isNotEmpty() } ?: return false
        setWaehlen(set.id)
        return _zustand.value.songId != null
    }

    /**
     * Abend-Protokoll (15.11.): eine Zeile pro Song in files/protokoll.txt, damit wir nach einem Gig
     * wissen, was wirklich lief (und als Grundlage für die GEMA-Liste).
     */
    private fun protokoll(art: String, songId: Long, setId: Long?, nachMs: Long?) {
        runCatching {
            val b = ablage.jetzt
            val gig = b.gigVonSet(setId)
            val zeit = java.time.LocalDateTime.now().withNano(0).toString().replace('T', ' ')
            val zeile = listOf(zeit, gig?.name ?: "-", b.set(setId)?.name ?: "-", b.song(songId)?.titel ?: "?",
                art + (nachMs?.let { " nach ${it / 60000}:%02d".format((it / 1000) % 60) } ?: "")).joinToString(" | ")
            File(app.filesDir, if (ablage.testmodus) "protokoll-test.txt" else "protokoll.txt").appendText(zeile + "\n")
        }.onFailure { Log.w(TAG, "Protokoll: ${it.message}") }
    }

    /** Probehören außerhalb eines Sets. Verweigert, solange ein Set-Song läuft. */
    fun probe(songId: Long, abMs: Long = 0): Boolean {
        val z = _zustand.value
        if ((z.spielt || z.pause != null) && !z.probe) { hinweis("Erst den laufenden Song stoppen."); return false }
        val s = ablage.jetzt.song(songId) ?: return false
        // Was vorher bereitlag, merken: nach der Probe liegt genau dieser Song wieder bereit.
        if (!z.probe) vorProbe = z.songId
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
        val vorher = vorProbe; vorProbe = null
        val setId = z.setId ?: return
        val set = ablage.jetzt.set(setId)
        val song = ablage.jetzt.song(vorher)
        if (song != null && set != null && set.eintraege.any { it.songId == song.id }) laden(song, setId) else setWaehlen(setId)
    }

    /** Position in diesem Moment (der 200-ms-Takt wäre fürs Einmessen zu grob). */
    fun positionJetzt(): Long = if (engine.hatSpuren) engine.positionMs else _position.value

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
        val frisch = _zustand.value.let { z -> !z.probe && engine.positionMs == 0L && z.songId != null }
        _zustand.value.let { z -> if (frisch) z.songId?.let { protokoll("Start", it, z.setId, null) } }
        engine.play()
        // Probehören zählt nicht als Weiterspielen: der Set-Ende-Hinweis bleibt dann stehen.
        _zustand.update { it.copy(spielt = true, setDurch = if (it.probe) it.setDurch else null, startNr = if (frisch) it.startNr + 1 else it.startNr) }
        dienstAn()
    }

    private fun dienstAn() {
        runCatching { ContextCompat.startForegroundService(app, Intent(app, WiedergabeDienst::class.java)) }
            .onSuccess { dienstLaeuft = true }
            .onFailure { Log.w(TAG, "Dienst nicht startbar: ${it.message}") }
    }

    /** Dienst aus, aber nur, wenn keine Show mehr läuft (sonst hält er die Set-Pause am Leben). */
    private fun dienstAus() {
        val z = _zustand.value
        if (_show.value || z.spielt || z.pause != null) return
        runCatching { app.stopService(Intent(app, WiedergabeDienst::class.java)) }
        dienstLaeuft = false
    }
}
