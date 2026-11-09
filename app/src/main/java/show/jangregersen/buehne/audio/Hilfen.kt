package show.jangregersen.buehne.audio

import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.util.Log
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.ByteArrayOutputStream
import java.io.File

/** Ansagen aufnehmen (aus der alten App übernommen: AAC 128 kBit/s, 44,1 kHz, mono). */
object Aufnahme {
    private const val TAG = "Aufnahme"

    fun dateiFuer(context: Context, songId: Long, vorher: Boolean): File =
        File(context.filesDir, "ansage_${songId}_${if (vorher) "vorher" else "nachher"}_${System.currentTimeMillis()}.m4a")

    fun starten(context: Context, ziel: File): MediaRecorder? {
        val r = try {
            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
        } catch (e: Exception) { Log.e(TAG, "Recorder nicht erzeugbar", e); return null }
        return try {
            r.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128_000)
                setAudioSamplingRate(44_100)
                setAudioChannels(1)
                setOutputFile(ziel.absolutePath)
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Aufnahme-Start fehlgeschlagen", e)
            runCatching { r.release() }
            null
        }
    }

    /** Stoppt und gibt die Dauer zurück (0 = fehlgeschlagen). */
    fun stoppen(r: MediaRecorder, ziel: File): Long = try {
        r.stop(); r.release(); dauer(ziel)
    } catch (e: Exception) {
        Log.e(TAG, "Aufnahme-Stopp fehlgeschlagen", e)
        runCatching { r.release() }
        0L
    }

    fun dauer(f: File): Long {
        if (!f.exists() || f.length() == 0L) return 0L
        val m = MediaMetadataRetriever()
        return try {
            m.setDataSource(f.absolutePath)
            m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (e: Exception) { 0L } finally { runCatching { m.release() } }
    }
}

/** Fester Warnton vor dem Ende einer stillen Pause: aufsteigendes Glocken-Arpeggio, nur rechts. */
object Warnton {
    private const val SR = 44100

    fun datei(context: Context): File {
        val f = File(context.filesDir, "warnton.wav")
        if (!f.exists()) schreiben(f)
        return f
    }

    private fun schreiben(file: File, sekunden: Float = 5f) {
        val n = (SR * sekunden).toInt()
        val pcm = ShortArray(n * 2)
        val noten = floatArrayOf(523.25f, 659.25f, 783.99f, 1046.50f)
        val abstand = sekunden / noten.size
        noten.forEachIndexed { i, f ->
            val start = (i * abstand * SR).toInt()
            val laenge = (SR * abstand * 2.2f).toInt().coerceAtMost(n - start)
            for (t in 0 until laenge) {
                val idx = start + t
                if (idx >= n) break
                val zeit = t / SR.toFloat()
                val huelle = Math.exp(-zeit * 2.2)
                val v = (Short.MAX_VALUE * 0.5 * huelle * (Math.sin(2.0 * Math.PI * f * t / SR) + 0.35 * Math.sin(2.0 * Math.PI * f * 2.01 * t / SR))).toInt()
                pcm[idx * 2 + 1] = (pcm[idx * 2 + 1] + v).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
        }
        val daten = pcm.size * 2
        val out = ByteArrayOutputStream(44 + daten)
        fun i32(v: Int) = out.write(byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte()))
        fun i16(v: Int) = out.write(byteArrayOf(v.toByte(), (v shr 8).toByte()))
        out.write("RIFF".toByteArray()); i32(36 + daten); out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray()); i32(16); i16(1); i16(2); i32(SR); i32(SR * 4); i16(4); i16(16)
        out.write("data".toByteArray()); i32(daten)
        pcm.forEach { s -> out.write(s.toInt() and 0xFF); out.write((s.toInt() shr 8) and 0xFF) }
        file.writeBytes(out.toByteArray())
    }
}

/** Liest den Textlayer eines Akkord-PDFs und wirft reine Akkordzeilen raus (Logik der alten App). */
object PdfLyrics {
    private val AKKORD = Regex("^[A-G](#|b)?(m|maj|min|sus|dim|aug|add)?\\d{0,2}(/[A-G](#|b)?)?$")
    private val TAKT = setOf("|", ":", "/", "||", "|:", ":|")
    private val ABSCHNITT = Regex("^\\[.*\\]$")

    fun lesen(context: Context, uri: Uri): String {
        PDFBoxResourceLoader.init(context.applicationContext)
        val roh = context.contentResolver.openInputStream(uri)?.use { s -> PDDocument.load(s).use { PDFTextStripper().getText(it) } } ?: return ""
        return roh.lineSequence().filterNot { istAkkordzeile(it) }.joinToString("\n").trim()
    }

    private fun istAkkordzeile(zeile: String): Boolean {
        val t = zeile.trim()
        if (t.isEmpty() || ABSCHNITT.matches(t)) return false
        val tokens = t.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return false
        return tokens.count { it in TAKT || AKKORD.matches(it) }.toDouble() / tokens.size >= 0.8
    }
}
