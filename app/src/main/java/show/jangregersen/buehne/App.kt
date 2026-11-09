package show.jangregersen.buehne

import android.app.Application
import androidx.media3.common.util.UnstableApi
import show.jangregersen.buehne.daten.Ablage
import show.jangregersen.buehne.spiel.Buehne

@UnstableApi
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        ablage = Ablage(this)
        buehne = Buehne(this, ablage)
    }

    companion object {
        lateinit var ablage: Ablage; private set
        lateinit var buehne: Buehne; private set
    }
}
