package show.jangregersen.buehne

import android.app.Application
import show.jangregersen.buehne.daten.Ablage
import show.jangregersen.buehne.spiel.Buehne

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        show.jangregersen.buehne.audio.Mitschnitt.ordner = filesDir
        // Schriftstufe: vorerst über files/schrift.txt (0–3) zum Vergleichen der Varianten.
        runCatching { show.jangregersen.buehne.ui.Schriftbild.stufe = java.io.File(filesDir, "schrift.txt").readText().trim().toInt().coerceIn(0, 3) }
        ablage = Ablage(this)
        buehne = Buehne(this, ablage)
    }

    companion object {
        lateinit var ablage: Ablage; private set
        lateinit var buehne: Buehne; private set
    }
}
