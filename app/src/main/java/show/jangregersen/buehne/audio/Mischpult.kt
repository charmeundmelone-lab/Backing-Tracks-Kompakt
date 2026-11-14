package show.jangregersen.buehne.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Live regelbare Verteilung einer Spur: links = inL*a + inR*b, rechts = inL*c + inR*d. */
class Pegel {
    @Volatile var a = 1f
    @Volatile var b = 0f
    @Volatile var c = 0f
    @Volatile var d = 1f
    fun setze(a: Float, b: Float, c: Float, d: Float) { this.a = a; this.b = b; this.c = c; this.d = d }
}

/**
 * Spielt alle Spuren eines Songs sample-genau gemeinsam ab: ein Faden liest aus jeder Quelle
 * denselben Block, mischt ihn auf Stereo (Band links, Ohr rechts) und schreibt ihn in EINE
 * AudioTrack. Spuren können so nie gegeneinander verrutschen.
 *
 * Der Faden füllt den Puffer schon vor dem Start, damit "Play" sofort klingt.
 */
class Mischpult(context: Context, private val quellen: List<Quelle>, val pegel: List<Pegel>, private val name: String) {
    val rate: Int = quellen.firstOrNull()?.rate ?: 48000
    val frames: Long = quellen.maxOfOrNull { it.frames } ?: 0L

    private val track: AudioTrack
    private val pufferFrames: Int
    private val lock = Object()
    private val wach: PowerManager.WakeLock =
        (context.getSystemService(Context.POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "buehne:wiedergabe")

    @Volatile private var laeuft = false
    @Volatile private var beendet = false
    @Volatile private var quellenLeer = false
    @Volatile var zuEnde = false; private set
    @Volatile var fehler: String? = null; private set

    // Nur unter [lock] ändern
    private var sprungZiel = -1L
    private var basis = 0L
    private var kopfBasis = 0L
    private var geschrieben = 0L

    private val faden: Thread

    init {
        val min = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT)
        val ziel = maxOf(min * 2, rate / 5 * 8) // mind. 200 ms
        track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).setEncoding(AudioFormat.ENCODING_PCM_FLOAT).build())
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(ziel)
            .build()
        pufferFrames = track.bufferSizeInFrames
        faden = Thread({ schleife() }, "Mischpult-$name").also { it.start() }
    }

    // ── Steuerung (beliebiger Faden) ─────────────────────────────────────────

    fun play() {
        synchronized(lock) {
            if (zuEnde) springenIntern(0)
            laeuft = true
            if (!wach.isHeld) wach.acquire(6 * 60 * 60 * 1000L)
            runCatching { track.play() }
            lock.notifyAll()
        }
    }

    fun pause() {
        synchronized(lock) {
            laeuft = false
            runCatching { track.pause() }
            if (wach.isHeld) wach.release()
        }
    }

    fun springen(frame: Long) { synchronized(lock) { springenIntern(frame); lock.notifyAll() } }

    private fun springenIntern(frame: Long) {
        sprungZiel = frame.coerceIn(0L, maxOf(0L, frames))
        zuEnde = false
    }

    val spielt: Boolean get() = laeuft && !zuEnde

    val positionFrames: Long
        get() = synchronized(lock) {
            if (sprungZiel >= 0) return sprungZiel
            val kopf = track.playbackHeadPosition.toLong() and 0xFFFFFFFFL
            basis + (kopf - kopfBasis).coerceIn(0L, geschrieben)
        }

    fun freigeben() {
        beendet = true
        synchronized(lock) { laeuft = false; lock.notifyAll() }
        faden.join(1000)
        runCatching { track.pause(); track.flush() }
        runCatching { track.release() }
        quellen.forEach { runCatching { it.close() } }
        if (wach.isHeld) wach.release()
    }

    // ── Mischfaden ───────────────────────────────────────────────────────────

    private fun schleife() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val block = 1024
        val aus = FloatArray(block * 2)
        val ein = quellen.map { FloatArray(block * maxOf(2, it.kanaele) * 4) }
        var offen = 0          // noch nicht geschriebene Werte in [aus]
        var offenAb = 0
        var stillSeit = 0L
        var letzterKopf = -1L
        var letzteMeldung = SystemClock.elapsedRealtime()
        var aussetzerGemeldet = 0
        val mitschnitt = Mitschnitt.starten(name, rate)
        try {
            while (!beendet) {
                // Selbstkontrolle: läuft die Tonausgabe leer, steht es im Protokoll (Tag Mischpult).
                if (SystemClock.elapsedRealtime() - letzteMeldung > 5000) {
                    letzteMeldung = SystemClock.elapsedRealtime()
                    val u = runCatching { track.underrunCount }.getOrDefault(0)
                    if (u != aussetzerGemeldet) { Log.w(TAG, "$name: Tonausgabe lief leer, insgesamt $u Mal"); aussetzerGemeldet = u }
                    else if (laeuft) Log.i(TAG, "$name: läuft sauber, Position ${positionFrames / rate} s")
                }
                // Sprünge erledigen
                synchronized(lock) {
                    if (sprungZiel >= 0) {
                        runCatching { track.pause(); track.flush() }
                        quellen.forEach { it.springen(sprungZiel) }
                        basis = sprungZiel; geschrieben = 0; offen = 0; offenAb = 0
                        kopfBasis = track.playbackHeadPosition.toLong() and 0xFFFFFFFFL
                        quellenLeer = false; zuEnde = false; sprungZiel = -1
                        if (laeuft) runCatching { track.play() }
                    }
                }
                if (offen == 0) {
                    if (quellenLeer) {
                        // Ausklingen abwarten, dann Ende melden
                        val kopf = track.playbackHeadPosition.toLong() and 0xFFFFFFFFL
                        val gespielt = kopf - kopfBasis
                        if (laeuft && !zuEnde) {
                            if (kopf != letzterKopf) { letzterKopf = kopf; stillSeit = SystemClock.elapsedRealtime() }
                            if (gespielt >= geschrieben || SystemClock.elapsedRealtime() - stillSeit > 600) zuEnde = true
                        }
                        warten(20); continue
                    }
                    // Angehalten: nur bis zum Puffer vorfüllen
                    if (!laeuft && geschrieben >= pufferFrames - block) { warten(30); continue }
                    // Einen Block mischen
                    aus.fill(0f)
                    var maxGelesen = 0
                    for (s in quellen.indices) {
                        val q = quellen[s]
                        val n = q.lesen(ein[s], block)
                        if (n <= 0) continue
                        if (n > maxGelesen) maxGelesen = n
                        val k = q.kanaele
                        val p = pegel[s]
                        val pa = p.a; val pb = p.b; val pc = p.c; val pd = p.d
                        val e = ein[s]
                        if (k == 1) {
                            val l = pa + pb; val r = pc + pd
                            for (i in 0 until n) { val v = e[i]; aus[2 * i] += v * l; aus[2 * i + 1] += v * r }
                        } else {
                            for (i in 0 until n) {
                                val l = e[i * k]; val r = e[i * k + 1]
                                aus[2 * i] += l * pa + r * pb
                                aus[2 * i + 1] += l * pc + r * pd
                            }
                        }
                    }
                    if (maxGelesen == 0) { quellenLeer = true; stillSeit = SystemClock.elapsedRealtime(); continue }
                    for (i in 0 until maxGelesen * 2) {
                        val v = aus[i] * AUSGANG; aus[i] = if (v > 1f) 1f else if (v < -1f) -1f else v
                    }
                    mitschnitt?.schreiben(aus, maxGelesen * 2)
                    offen = maxGelesen * 2; offenAb = 0
                }
                // Schreiben, ohne zu blockieren (Pause und Sprung bleiben so sofort wirksam)
                val w = track.write(aus, offenAb, offen, AudioTrack.WRITE_NON_BLOCKING)
                if (w < 0) { fehler = "Tonausgabe meldet Fehler $w"; Log.e(TAG, "write $w"); warten(50); continue }
                synchronized(lock) { if (sprungZiel < 0) geschrieben += w / 2 }
                offenAb += w; offen -= w
                if (offen > 0) warten(if (laeuft) 5 else 30)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Mischfaden abgebrochen", e)
            fehler = "$name: ${e.javaClass.simpleName} ${e.message ?: ""}"
            quellenLeer = true; zuEnde = true
        } finally {
            mitschnitt?.schliessen()
        }
    }

    private fun warten(ms: Long) { synchronized(lock) { if (!beendet && sprungZiel < 0) lock.wait(ms) } }

    companion object {
        private const val TAG = "Mischpult"

        /**
         * Ausgangspegel der ganzen App in dB. Jan fand den Kompakt am Gig lauter als sein altes
         * Nothing Phone 3a (gleicher USB-Adapter, beide auf voll). Ausgelesen am 14.11.: Die
         * Lautstärkekurve des Nothing endet bei −2 dB, die des Kompakt bei 0 dB. Die
         * Medienlautstärke bleibt auf voll (VolleLautstaerke), nur die App gibt 2 dB weniger aus.
         */
        const val AUSGANG_DB = -2f
        val AUSGANG: Float = Math.pow(10.0, AUSGANG_DB / 20.0).toFloat()
    }
}

/** Fehlersuche: liegt files/mitschnitt.an, schreibt jedes Mischpult seine ersten 20 s Ausgabe (float32 LE, stereo) mit. */
class Mitschnitt private constructor(private val f: FileOutputStream, private var rest: Long) {
    private val buf = ByteBuffer.allocate(8192 * 4).order(ByteOrder.LITTLE_ENDIAN)

    fun schreiben(werte: FloatArray, n: Int) {
        if (rest <= 0) return
        val m = minOf(n.toLong(), rest).toInt()
        var i = 0
        while (i < m) {
            buf.clear()
            val stueck = minOf(m - i, buf.capacity() / 4)
            for (j in 0 until stueck) buf.putFloat(werte[i + j])
            f.write(buf.array(), 0, stueck * 4)
            i += stueck
        }
        rest -= m
        if (rest <= 0) schliessen()
    }

    fun schliessen() { runCatching { f.close() }; rest = 0 }

    companion object {
        @Volatile var ordner: File? = null
        fun starten(name: String, rate: Int): Mitschnitt? {
            val o = ordner ?: return null
            if (!File(o, "mitschnitt.an").exists()) return null
            val sauber = name.replace(Regex("[^A-Za-z0-9]"), "_")
            return Mitschnitt(FileOutputStream(File(o, "mitschnitt-$sauber-$rate.f32")), rate * 2L * 20)
        }
    }
}
