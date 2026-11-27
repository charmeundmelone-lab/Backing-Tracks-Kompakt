package show.jangregersen.buehne.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log

/**
 * Stiller Dauerton während der Show (21.11., Jan: „bei langen Pausen gelegentliches Knacksen
 * durch die PA“). Android legt die Tonausgabe 3 s nach dem letzten Ton schlafen (am Kompakt
 * gemessen: „Standby delay 3 s“). Beim Einschlafen und beim Aufwachen schalten viele USB-Adapter
 * hörbar, das knackt dann über die Anlage. Solange die Show läuft, spielt diese Tonspur deshalb
 * reine Stille in einer Schleife: Die Ausgabe bleibt wach, kein Ein- und Ausschalten.
 *
 * Feste Puffer-Schleife (MODE_STATIC), kein eigener Faden: kostet kaum Rechenzeit.
 */
object Stille {
    private const val TAG = "Stille"
    private var spur: AudioTrack? = null

    val an: Boolean get() = spur != null

    /** Gerät, an das Android die stille Spur leitet (für [TonAusgang], wenn kein Song geladen ist). */
    val geroutet: android.media.AudioDeviceInfo? get() = runCatching { spur?.routedDevice }.getOrNull()

    fun an() {
        if (spur != null) return
        runCatching {
            val rate = 48000
            val frames = rate / 2 // 0,5 s Stille, endlos wiederholt
            val t = AudioTrack.Builder()
                // Eigene Kennung (UNKNOWN statt MUSIC): So erkennt werkzeug/testlauf.sh echte Songs weiter.
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_UNKNOWN).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(frames * 4)
                .build()
            t.write(ShortArray(frames * 2), 0, frames * 2)
            t.setLoopPoints(0, frames, -1)
            t.play()
            spur = t
            Log.i(TAG, "Stille an")
        }.onFailure { Log.w(TAG, "Stille ließ sich nicht starten: ${it.message}") }
    }

    fun aus() {
        val t = spur ?: return
        spur = null
        runCatching { t.stop() }
        runCatching { t.release() }
        Log.i(TAG, "Stille aus")
    }
}
