package show.jangregersen.buehne.audio

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlin.math.pow

private const val TAG = "Engine"

/**
 * Spielt einen Song (eine Stereo-Datei oder mehrere Einzelspuren) über ein [Mischpult] ab:
 * alle Spuren laufen sample-genau gemeinsam in einer Tonausgabe. Band liegt fest links,
 * Click/Cue/Ansage fest rechts. Nur vom Haupt-Thread aus benutzen.
 */
class Engine(private val context: Context) {

    private class Geladen(val songId: Long, val spuren: List<Spur>, val pult: Mischpult?)

    private var aktuell: Geladen? = null
    private var vorrat: Geladen? = null
    private val haupt = Handler(Looper.getMainLooper())
    private var gemeldet: String? = null

    var onFehler: ((String) -> Unit)? = null

    val geladeneSongId: Long? get() = aktuell?.songId
    val hatSpuren: Boolean get() = aktuell?.pult != null

    private val pult: Mischpult? get() = aktuell?.pult

    val spielt: Boolean get() = pult?.spielt == true
    val positionMs: Long get() = pult?.let { p -> p.positionFrames * 1000 / p.rate }.also { fehlerPruefen() } ?: 0L
    val dauerMs: Long get() = pult?.let { p -> if (p.frames > 0) p.frames * 1000 / p.rate else 0L } ?: 0L
    val istZuEnde: Boolean get() = pult?.zuEnde == true

    // ── Laden ────────────────────────────────────────────────────────────────

    fun laden(songId: Long, spuren: List<Spur>, mix: Map<String, Int>) {
        val v = vorrat
        if (v != null && v.songId == songId && v.spuren == spuren) {
            vorrat = null
            freigeben(aktuell)
            aktuell = v
        } else {
            freigeben(aktuell)
            aktuell = bauen(songId, spuren)
        }
        gemeldet = null
        mixSetzen(mix)
        aktuell?.pult?.springen(0L)
    }

    fun vorladen(songId: Long, spuren: List<Spur>, mix: Map<String, Int>) {
        if (vorrat?.songId == songId || aktuell?.songId == songId) return
        freigeben(vorrat)
        vorrat = bauen(songId, spuren).also { g -> mixAnwenden(g, mix) }
    }

    fun entladen() { freigeben(aktuell); aktuell = null }

    // ── Wiedergabe ───────────────────────────────────────────────────────────

    fun play() { pult?.play() }

    fun pause() { pult?.pause() }

    fun stop() { pult?.let { it.pause(); it.springen(0L) } }

    fun seek(ms: Long) {
        val p = pult ?: return
        val ziel = ms.coerceIn(0L, maxOf(0L, dauerMs - 50))
        p.springen(ziel * p.rate / 1000)
    }

    // ── Mixer ────────────────────────────────────────────────────────────────

    fun mixSetzen(mix: Map<String, Int>) { aktuell?.let { mixAnwenden(it, mix) } }

    private fun mixAnwenden(g: Geladen, mix: Map<String, Int>) {
        val p = g.pult ?: return
        g.spuren.forEachIndexed { i, spur ->
            val pg = p.pegel[i]
            when (spur.rolle) {
                Rolle.STEREO -> pg.setze(verstaerkung(mix[Spuren.KANAL_BAND]), 0f, 0f, verstaerkung(mix[Spuren.KANAL_CLICK]))
                Rolle.BAND -> { val v = verstaerkung(mix[spur.name.lowercase()]) / 2f; pg.setze(v, v, 0f, 0f) }
                Rolle.OHR -> { val v = verstaerkung(mix[spur.name.lowercase()]) / 2f; pg.setze(0f, 0f, v, v) }
            }
        }
    }

    private fun fehlerPruefen() {
        val f = pult?.fehler ?: return
        if (f == gemeldet) return
        gemeldet = f
        haupt.post { onFehler?.invoke(f) }
    }

    // ── Sprachnotizen (Ansagen, Warnton): hart rechts ────────────────────────

    private var notiz: MediaPlayer? = null

    val notizPositionMs: Long get() = notiz?.let { runCatching { it.currentPosition.toLong() }.getOrDefault(0L) } ?: 0L
    val notizDauerMs: Long get() = notiz?.let { runCatching { it.duration.toLong() }.getOrDefault(0L) } ?: 0L
    val notizLaeuft: Boolean get() = notiz != null

    fun notizSpielen(pfad: String, lautstaerke: Float = 1f, fertig: () -> Unit) {
        notizStoppen()
        if (pfad.isBlank()) { fertig(); return }
        val mp = MediaPlayer()
        try {
            mp.setDataSource(pfad)
            @Suppress("DEPRECATION") mp.setVolume(0f, lautstaerke.coerceIn(0f, 1f))
            mp.setOnCompletionListener { notizStoppen(); fertig() }
            mp.setOnErrorListener { _, _, _ -> notizStoppen(); fertig(); true }
            mp.prepare()
            mp.start()
            notiz = mp
        } catch (e: Exception) {
            Log.e(TAG, "Notiz nicht abspielbar: $pfad", e)
            runCatching { mp.release() }
            fertig()
        }
    }

    fun notizStoppen() {
        notiz?.let { p -> runCatching { p.stop() }; runCatching { p.release() } }
        notiz = null
    }

    fun freigebenAlles() { freigeben(aktuell); freigeben(vorrat); aktuell = null; vorrat = null; notizStoppen() }

    // ── Bauen ────────────────────────────────────────────────────────────────

    private fun freigeben(g: Geladen?) { g?.pult?.let { runCatching { it.freigeben() } } }

    private fun bauen(songId: Long, spuren: List<Spur>): Geladen {
        if (spuren.isEmpty()) return Geladen(songId, spuren, null)
        val quellen = ArrayList<Quelle>()
        return try {
            spuren.forEach { quellen += Quellen.oeffnen(context, it.uri) }
            // Alle Spuren auf die Rate der ersten bringen (normalerweise sind sie gleich).
            val rate = quellen[0].rate
            val gleich = quellen.map { if (it.rate == rate) it else Umrechner(it, rate) }
            Geladen(songId, spuren, Mischpult(context, gleich, gleich.map { Pegel() }, "song$songId"))
        } catch (e: Exception) {
            Log.e(TAG, "Song $songId nicht ladbar", e)
            quellen.forEach { runCatching { it.close() } }
            val text = "${spuren.first().name}: ${e.javaClass.simpleName} ${e.message ?: ""}"
            haupt.post { onFehler?.invoke(text) }
            Geladen(songId, spuren, null)
        }
    }

    companion object {
        /** Stufe 10 = volle Lautstärke, jede Stufe darunter 3 dB leiser, negativ = stumm. */
        fun verstaerkung(stufe: Int?): Float {
            val s = stufe ?: 10
            if (s <= 0) return 0f
            return 10f.pow((s.coerceAtMost(10) - 10) * 3f / 20f)
        }
    }
}
