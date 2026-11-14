package show.jangregersen.buehne.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * Jan spielt immer mit voller Medienlautstärke (früher stand sie aus Versehen auf halb). Sobald die
 * App offen ist, steht sie auf voll und bleibt dort: solange die App zu sehen ist oder ein Song
 * bzw. eine Pause läuft (auch bei dunklem Bildschirm). Wird sie von außen verstellt, geht sie
 * sofort zurück auf voll. Verlässt Jan die App, gilt wieder die Lautstärke von vorher (sonst
 * wären z. B. Kopfhörer in anderen Apps plötzlich auf voll).
 *
 * Gehörschutz-Sperre (14.11., Jan am Gig: nach der Android-Frage "über den Schwellenwert?" kam kein
 * Ton mehr): Android lässt Kopfhörer/USB-Adapter erst nach einem OK im System-Fenster über eine
 * Schwelle (beim Kompakt Stufe 2 von 15) und schaltet diese Sperre nach einer Spielzeit von selbst
 * wieder ein. Eine App kann sie nicht abschalten, und Android blockiert das Hochstellen dann ohne
 * Meldung. Deshalb meldet [gedrosselt] das sofort, die App zeigt einen großen Hinweis, und
 * [freigeben] holt das System-Fenster mit dem OK-Knopf von selbst hervor.
 *
 * Zusammen mit der Lautstärke schaltet [ShowRuhe] "Bitte nicht stören" ein und wieder aus.
 *
 * Für Tests am Gerät: Liegt `files/test-leise`, fasst die App die Lautstärke nicht an.
 * Liegt `files/test-gedrosselt`, tut sie so, als wäre die Sperre aktiv (nur für den Hinweis).
 */
object VolleLautstaerke {
    private const val GEAENDERT = "android.media.VOLUME_CHANGED_ACTION"
    private const val STROM = "android.media.EXTRA_VOLUME_STREAM_TYPE"

    private var ctx: Context? = null
    private var laeuft: () -> Boolean = { false }
    private var sichtbar = false
    /** Lautstärke, bevor die App sie hochgestellt hat (null = App hält sie gerade nicht fest). */
    private var vorher: Int? = null

    private val _gedrosselt = MutableStateFlow(false)
    /** true = App hält die Lautstärke, kommt aber nicht auf voll (Gehörschutz-Sperre). */
    val gedrosselt: StateFlow<Boolean> = _gedrosselt

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
        val halten = sichtbar || laeuft()
        if (halten) ShowRuhe.an(c) else ShowRuhe.aus(c)
        if (File(c.filesDir, "test-leise").exists()) {
            _gedrosselt.value = halten && File(c.filesDir, "test-gedrosselt").exists()
            return true
        }
        val am = c.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return true
        val voll = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val jetzt = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (halten) {
            if (vorher == null) vorher = jetzt
            if (jetzt < voll) runCatching { am.setStreamVolume(AudioManager.STREAM_MUSIC, voll, 0) }
            val ok = am.getStreamVolume(AudioManager.STREAM_MUSIC) >= voll
            _gedrosselt.value = !ok
            return ok
        }
        _gedrosselt.value = false
        vorher?.let { v -> if (v != jetzt) runCatching { am.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0) } }
        vorher = null
        return true
    }

    /**
     * Knopf "Ton freigeben": stellt auf voll MIT Anzeige. Ist die Gehörschutz-Sperre aktiv, zeigt
     * Android dabei sein Fenster mit OK; nach dem OK meldet sich die Lautstärke und [pruefen] läuft.
     */
    fun freigeben() {
        val c = ctx ?: return
        if (File(c.filesDir, "test-leise").exists()) { File(c.filesDir, "test-gedrosselt").delete(); pruefen(); return }
        val am = c.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        runCatching { am.setStreamVolume(AudioManager.STREAM_MUSIC, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC), AudioManager.FLAG_SHOW_UI) }
        pruefen()
    }
}
