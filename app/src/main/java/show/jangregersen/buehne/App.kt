package show.jangregersen.buehne

import android.app.Application
import show.jangregersen.buehne.daten.Ablage
import show.jangregersen.buehne.spiel.Buehne

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        show.jangregersen.buehne.audio.Mitschnitt.ordner = filesDir
        ablage = Ablage(this)
        buehne = Buehne(this, ablage)
    }

    companion object {
        lateinit var ablage: Ablage; private set
        lateinit var buehne: Buehne; private set
    }
}
