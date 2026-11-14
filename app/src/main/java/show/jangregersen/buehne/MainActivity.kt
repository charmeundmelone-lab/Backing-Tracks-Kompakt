package show.jangregersen.buehne

import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import show.jangregersen.buehne.ui.Navi
import show.jangregersen.buehne.ui.Seite
import show.jangregersen.buehne.ui.Tasten
import show.jangregersen.buehne.ui.Wurzel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val navi = remember { Navi() }
            val z by App.buehne.zustand.collectAsState()
            // Post vom Laptop (Werkstatt "Zum Mudita senden"): alle 2 s nachsehen, aber nur, wenn die
            // App zu sehen ist, nichts spielt und Jan gerade nicht an einem Text oder Song arbeitet.
            LaunchedEffect(Unit) {
                while (true) {
                    val zz = App.buehne.zustand.value
                    val ruhig = navi.stapel.none {
                        it is Seite.Einmessen || it is Seite.Editor || it is Seite.TexteHolen || it is Seite.Pruefen ||
                            it is Seite.ProbeBuehne || it is Seite.Eingabe || it is Seite.Frage
                    }
                    if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) && !zz.spielt && zz.pause == null && ruhig)
                        withContext(Dispatchers.IO) { App.ablage.eingangUebernehmen() }?.let { App.buehne.hinweis(it) }
                    delay(2000)
                }
            }
            val bildschirmAn = z.spielt || z.pause != null || navi.oben == Seite.Buehne
            LaunchedEffect(bildschirmAn) {
                if (bildschirmAn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            // Feste Schriftgröße: das Layout ist auf den Kompakt-Bildschirm abgestimmt.
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, 1f)) {
                Wurzel(navi) { finish() }
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val vor = when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN -> true
            KeyEvent.KEYCODE_VOLUME_UP -> false
            else -> return super.dispatchKeyEvent(event)
        }
        val h = Tasten.handler ?: return super.dispatchKeyEvent(event)
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) h(vor)
        return true
    }

    override fun onStart() {
        super.onStart()
        // Kommt sie nicht auf voll (Gehörschutz-Sperre), zeigt die Wurzel einen großen Hinweis mit
        // Knopf "Ton freigeben" (VolleLautstaerke.gedrosselt).
        show.jangregersen.buehne.audio.VolleLautstaerke.sichtbar(true)
    }

    override fun onStop() {
        super.onStop()
        show.jangregersen.buehne.audio.VolleLautstaerke.sichtbar(false)
        App.ablage.sofortSichern()
    }
}
