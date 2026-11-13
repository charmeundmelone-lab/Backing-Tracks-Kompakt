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
        if (!show.jangregersen.buehne.audio.VolleLautstaerke.let { it.sichtbar(true); it.pruefen() })
            App.buehne.hinweis("Die Lautstärke lässt sich nicht auf voll stellen. Bitte am Gerät prüfen.")
    }

    override fun onStop() {
        super.onStop()
        show.jangregersen.buehne.audio.VolleLautstaerke.sichtbar(false)
        App.ablage.sofortSichern()
    }
}
