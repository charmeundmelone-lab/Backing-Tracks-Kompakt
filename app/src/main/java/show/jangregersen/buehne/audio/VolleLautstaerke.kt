package show.jangregersen.buehne.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import androidx.core.content.ContextCompat
import java.io.File

/**
 * Jan spielt immer mit voller Medienlautstärke (früher stand sie aus Versehen auf halb). Sobald die
 * App offen ist, steht sie auf voll und bleibt dort: solange die App zu sehen ist oder ein Song
 * bzw. eine Pause läuft (auch bei dunklem Bildschirm). Wird sie von außen verstellt, geht sie
 * sofort zurück auf voll. Verlässt Jan die App, gilt wieder die Lautstärke von vorher (sonst
 * wären z. B. Kopfhörer in anderen Apps plötzlich auf voll).
 *
 * Für Tests am Gerät: Liegt die Datei `files/test-leise`, fasst die App die Lautstärke nicht an.
 */
object VolleLautstaerke {
    private const val GEAENDERT = "android.media.VOLUME_CHANGED_ACTION"
    private const val STROM = "android.media.EXTRA_VOLUME_STREAM_TYPE"

    private var ctx: Context? = null
    private var laeuft: () -> Boolean = { false }
    private var sichtbar = false
    /** Lautstärke, bevor die App sie hochgestellt hat (null = App hält sie gerade nicht fest). */
    private var vorher: Int? = null

    fun einrichten(app: Context, laeuft: () -> Boolean) {
        ctx = app.applicationContext
        this.laeuft = laeuft
        val empfaenger = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                if (i.getIntExtra(STROM, AudioManager.STREAM_MUSIC) == AudioManager.STREAM_MUSIC) pruefen()
            }
        }
        ContextCompat.registerReceiver(app.applicationContext, empfaenger, IntentFilter(GEAENDERT), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    /** Die App ist zu sehen (true) oder nicht mehr (false). */
    fun sichtbar(an: Boolean) { sichtbar = an; pruefen() }

    /** Stellt voll ein oder gibt die alte Lautstärke zurück. Antwort: Steht sie jetzt, wie sie soll? */
    fun pruefen(): Boolean {
        val c = ctx ?: return true
        if (File(c.filesDir, "test-leise").exists()) return true
        val am = c.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return true
        val voll = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val jetzt = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (sichtbar || laeuft()) {
            if (vorher == null) vorher = jetzt
            if (jetzt < voll) runCatching { am.setStreamVolume(AudioManager.STREAM_MUSIC, voll, 0) }
            return am.getStreamVolume(AudioManager.STREAM_MUSIC) >= voll
        }
        vorher?.let { v -> if (v != jetzt) runCatching { am.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0) } }
        vorher = null
        return true
    }
}
