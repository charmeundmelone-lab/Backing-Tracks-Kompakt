package show.jangregersen.buehne.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * Eine Tonquelle liefert Frames als Gleitkommazahlen (-1 … 1), Kanäle verschachtelt.
 * Wird nur vom Mischpult-Faden benutzt.
 */
interface Quelle : Closeable {
    val rate: Int
    val kanaele: Int
    /** Länge in Frames, -1 = unbekannt. */
    val frames: Long
    /** Liest bis zu [n] Frames nach [ziel] (Platz für n * kanaele). Gibt die Zahl der Frames zurück, 0 = Ende. */
    fun lesen(ziel: FloatArray, n: Int): Int
    fun springen(frame: Long)
}

object Quellen {
    /** Öffnet eine Datei: WAV wird direkt gelesen (bitgenau), alles andere über den Android-Decoder. */
    fun oeffnen(context: Context, uri: String): Quelle {
        val u = Uri.parse(uri)
        val pfd = oeffneFd(context, u)
        WavQuelle.oeffnen(pfd)?.let { return it }
        pfd.close()
        return CodecQuelle(context, u)
    }

    private fun oeffneFd(context: Context, u: Uri): ParcelFileDescriptor =
        if (u.scheme == null || u.scheme == "file") ParcelFileDescriptor.open(File(u.path ?: u.toString()), ParcelFileDescriptor.MODE_READ_ONLY)
        else context.contentResolver.openFileDescriptor(u, "r") ?: error("Datei nicht lesbar: $u")
}

// ── WAV direkt ───────────────────────────────────────────────────────────────

class WavQuelle private constructor(
    private val pfd: ParcelFileDescriptor,
    private val kanal: FileChannel,
    override val rate: Int,
    override val kanaele: Int,
    private val bits: Int,
    private val gleitkomma: Boolean,
    private val datenAb: Long,
    override val frames: Long
) : Quelle {
    private val blockBytes = kanaele * (bits / 8)
    private var pos = 0L
    private var puffer = ByteBuffer.allocateDirect(blockBytes * 2048).order(ByteOrder.LITTLE_ENDIAN)
    private var roh = ByteArray(puffer.capacity())

    override fun lesen(ziel: FloatArray, n: Int): Int {
        val m = minOf(n.toLong(), frames - pos).toInt()
        if (m <= 0) return 0
        val bytes = m * blockBytes
        if (bytes > puffer.capacity()) {
            puffer = ByteBuffer.allocateDirect(bytes).order(ByteOrder.LITTLE_ENDIAN); roh = ByteArray(bytes)
        }
        puffer.clear(); puffer.limit(bytes)
        var gelesen = 0
        while (gelesen < bytes) {
            val r = kanal.read(puffer, datenAb + pos * blockBytes + gelesen)
            if (r <= 0) break
            gelesen += r
        }
        val ganze = gelesen / blockBytes
        if (ganze == 0) return 0
        puffer.flip(); puffer.get(roh, 0, ganze * blockBytes)
        val werte = ganze * kanaele
        val b = roh
        when {
            gleitkomma && bits == 32 -> for (i in 0 until werte) {
                val o = i * 4
                ziel[i] = java.lang.Float.intBitsToFloat((b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8) or
                    ((b[o + 2].toInt() and 0xFF) shl 16) or (b[o + 3].toInt() shl 24))
            }
            bits == 16 -> for (i in 0 until werte) {
                val o = i * 2
                ziel[i] = ((b[o].toInt() and 0xFF) or (b[o + 1].toInt() shl 8)) / 32768f
            }
            bits == 24 -> for (i in 0 until werte) {
                val o = i * 3
                ziel[i] = ((b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8) or (b[o + 2].toInt() shl 16)) / 8388608f
            }
            bits == 32 -> for (i in 0 until werte) {
                val o = i * 4
                ziel[i] = ((b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8) or
                    ((b[o + 2].toInt() and 0xFF) shl 16) or (b[o + 3].toInt() shl 24)) / 2147483648f
            }
            else -> for (i in 0 until werte) ziel[i] = ((b[i].toInt() and 0xFF) - 128) / 128f
        }
        pos += ganze
        return ganze
    }

    override fun springen(frame: Long) { pos = frame.coerceIn(0L, frames) }

    override fun close() { runCatching { kanal.close() }; runCatching { pfd.close() } }

    companion object {
        /** Liest den Kopf; null, wenn es kein unkomprimiertes WAV ist. */
        fun oeffnen(pfd: ParcelFileDescriptor): WavQuelle? {
            val kanal = FileInputStream(pfd.fileDescriptor).channel
            val groesse = kanal.size()
            val k = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN)
            fun lies(an: Long, n: Int): ByteBuffer? {
                k.clear(); k.limit(n)
                var g = 0
                while (g < n) { val r = kanal.read(k, an + g); if (r <= 0) return null; g += r }
                k.flip(); return k
            }
            val kopf = lies(0, 12) ?: return null
            val riff = String(ByteArray(4).also { kopf.get(it) }, Charsets.US_ASCII)
            kopf.getInt()
            val wave = String(ByteArray(4).also { kopf.get(it) }, Charsets.US_ASCII)
            if ((riff != "RIFF" && riff != "RF64") || wave != "WAVE") return null
            var p = 12L
            var rate = 0; var kanaele = 0; var bits = 0; var gleit = false; var fmtOk = false
            while (p + 8 <= groesse) {
                val c = lies(p, 8) ?: return null
                val id = String(ByteArray(4).also { c.get(it) }, Charsets.US_ASCII)
                val len = c.getInt().toLong() and 0xFFFFFFFFL
                if (id == "fmt ") {
                    val f = lies(p + 8, minOf(len, 40L).toInt()) ?: return null
                    var tag = f.getShort(0).toInt() and 0xFFFF
                    kanaele = f.getShort(2).toInt() and 0xFFFF
                    rate = f.getInt(4)
                    bits = f.getShort(14).toInt() and 0xFFFF
                    if (tag == 0xFFFE && len >= 26) tag = f.getShort(24).toInt() and 0xFFFF
                    gleit = tag == 3
                    fmtOk = (tag == 1 && bits in setOf(8, 16, 24, 32)) || (tag == 3 && bits == 32)
                } else if (id == "data") {
                    if (!fmtOk || kanaele == 0 || rate == 0) return null
                    val ab = p + 8
                    val rest = groesse - ab
                    val datenLen = if (len == 0L || len == 0xFFFFFFFFL || len > rest) rest else len
                    val block = kanaele * (bits / 8)
                    return WavQuelle(pfd, kanal, rate, kanaele, bits, gleit, ab, datenLen / block)
                }
                p += 8 + len + (len and 1)
            }
            return null
        }
    }
}

// ── Komprimierte Formate über MediaCodec ─────────────────────────────────────

class CodecQuelle(context: Context, uri: Uri) : Quelle {
    private val extractor = MediaExtractor()
    private val codec: MediaCodec
    override val rate: Int
    @Volatile override var kanaele: Int; private set
    override val frames: Long
    private var float = false
    private var eingabeEnde = false
    private var ausgabeEnde = false
    private var rest = FloatArray(0)
    private var restAb = 0
    private var restBis = 0
    private var verwerfenBis = -1L
    private val info = MediaCodec.BufferInfo()

    init {
        extractor.setDataSource(context, uri, null)
        val spur = (0 until extractor.trackCount).firstOrNull {
            extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        } ?: error("Keine Tonspur in $uri")
        extractor.selectTrack(spur)
        val f = extractor.getTrackFormat(spur)
        rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        kanaele = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        frames = if (f.containsKey(MediaFormat.KEY_DURATION)) f.getLong(MediaFormat.KEY_DURATION) * rate / 1_000_000 else -1
        codec = MediaCodec.createDecoderByType(f.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(f, null, null, 0)
        codec.start()
    }

    override fun lesen(ziel: FloatArray, n: Int): Int {
        var fertig = 0
        while (fertig < n) {
            if (restAb < restBis) {
                val k = kanaele
                val m = minOf(n - fertig, (restBis - restAb) / k)
                System.arraycopy(rest, restAb, ziel, fertig * k, m * k)
                restAb += m * k; fertig += m
                continue
            }
            if (ausgabeEnde || !dekodieren()) break
        }
        return fertig
    }

    /** Holt einen weiteren Ausgabeblock in [rest]. false = Ende. */
    private fun dekodieren(): Boolean {
        var versuche = 0
        while (versuche++ < 400) {
            if (!eingabeEnde) {
                val i = codec.dequeueInputBuffer(2000)
                if (i >= 0) {
                    val buf = codec.getInputBuffer(i)!!
                    val n = extractor.readSampleData(buf, 0)
                    if (n < 0) { codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); eingabeEnde = true }
                    else { codec.queueInputBuffer(i, 0, n, extractor.sampleTime, 0); extractor.advance() }
                }
            }
            val o = codec.dequeueOutputBuffer(info, 2000)
            when {
                o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val f = codec.outputFormat
                    kanaele = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    float = f.containsKey(MediaFormat.KEY_PCM_ENCODING) && f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                }
                o >= 0 -> {
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) ausgabeEnde = true
                    val buf = codec.getOutputBuffer(o)!!.order(ByteOrder.nativeOrder())
                    buf.position(info.offset); buf.limit(info.offset + info.size)
                    val werte = if (float) info.size / 4 else info.size / 2
                    if (rest.size < werte) rest = FloatArray(werte)
                    if (float) buf.asFloatBuffer().get(rest, 0, werte)
                    else { val s = buf.asShortBuffer(); for (j in 0 until werte) rest[j] = s.get(j) / 32768f }
                    codec.releaseOutputBuffer(o, false)
                    restAb = 0; restBis = werte - werte % kanaele
                    if (verwerfenBis >= 0) {
                        val start = info.presentationTimeUs * rate / 1_000_000
                        val weg = (verwerfenBis - start).coerceIn(0L, (restBis / kanaele).toLong()).toInt()
                        restAb = weg * kanaele
                        if (restAb < restBis) verwerfenBis = -1
                    }
                    if (restAb < restBis) return true
                    if (ausgabeEnde) return false
                }
            }
        }
        return false
    }

    override fun springen(frame: Long) {
        extractor.seekTo(frame * 1_000_000 / rate, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        codec.flush()
        eingabeEnde = false; ausgabeEnde = false
        restAb = 0; restBis = 0
        verwerfenBis = frame
    }

    override fun close() {
        runCatching { codec.stop() }; runCatching { codec.release() }; runCatching { extractor.release() }
    }
}

// ── Abtastrate angleichen (nur nötig, wenn Spuren verschiedene Raten haben) ───

class Umrechner(private val q: Quelle, private val zielRate: Int) : Quelle {
    override val rate get() = zielRate
    override val kanaele get() = q.kanaele
    override val frames: Long get() = if (q.frames < 0) -1 else q.frames * zielRate / q.rate
    private val schritt = q.rate.toDouble() / zielRate
    private var ein = FloatArray(0)
    private var einFrames = 0
    private var phase = 1.0
    private var letzter = FloatArray(8)
    private var ende = false

    override fun lesen(ziel: FloatArray, n: Int): Int {
        val k = kanaele
        if (letzter.size < k) letzter = FloatArray(k)
        var aus = 0
        while (aus < n) {
            // Ein Fenster Eingangsframes holen: [letzter] + neue Frames
            if (phase + 1 >= einFrames + 1) {
                if (ende) break
                if (einFrames > 0) System.arraycopy(ein, (einFrames - 1) * k, letzter, 0, k)
                phase -= einFrames
                val brauche = 1024
                if (ein.size < brauche * k) ein = FloatArray(brauche * k)
                einFrames = q.lesen(ein, brauche)
                if (einFrames == 0) { ende = true; break }
            }
            val i = phase.toInt()
            val t = (phase - i).toFloat()
            for (c in 0 until k) {
                val a = if (i == 0) letzter[c] else ein[(i - 1) * k + c]
                val b = ein[i * k + c]
                ziel[aus * k + c] = a + (b - a) * t
            }
            aus++
            phase += schritt
        }
        return aus
    }

    override fun springen(frame: Long) {
        q.springen(frame * q.rate / zielRate)
        einFrames = 0; phase = 1.0; ende = false; letzter.fill(0f)
    }

    override fun close() = q.close()
}
