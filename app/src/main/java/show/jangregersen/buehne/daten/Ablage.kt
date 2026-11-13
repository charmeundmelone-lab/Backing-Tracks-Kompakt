package show.jangregersen.buehne.daten

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

/**
 * Hält den kompletten Bestand im Speicher und schreibt ihn kurz nach jeder Änderung als
 * JSON in den App-Speicher (erst in eine Zwischendatei, dann umbenennen: ein Absturz mitten
 * im Schreiben kann die alte Datei nicht zerstören).
 */
class Ablage(context: Context) {
    private val datei = File(context.filesDir, "bestand.json")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var speicherJob: Job? = null

    private val _stand = MutableStateFlow(laden())
    val stand: StateFlow<Bestand> = _stand.asStateFlow()

    val jetzt: Bestand get() = _stand.value

    @Synchronized
    fun aendern(f: (Bestand) -> Bestand) {
        val neu = f(_stand.value)
        if (neu == _stand.value) return
        _stand.value = neu
        speicherJob?.cancel()
        speicherJob = scope.launch { delay(300); schreiben(neu) }
    }

    /** Vergibt eine neue, nie wiederverwendete Nummer. */
    @Synchronized
    fun neueId(): Long {
        val id = _stand.value.naechsteId
        _stand.value = _stand.value.copy(naechsteId = id + 1)
        return id
    }

    fun sofortSichern() { speicherJob?.cancel(); schreiben(_stand.value) }

    private fun laden(): Bestand = try {
        if (datei.exists()) ausJson(JSONObject(datei.readText())) else Bestand()
    } catch (e: Exception) {
        Log.e(TAG, "Bestand nicht lesbar, sichere Kopie und starte leer", e)
        runCatching { datei.copyTo(File(datei.parentFile, "bestand-defekt-${System.currentTimeMillis()}.json")) }
        Bestand()
    }

    @Synchronized
    private fun schreiben(b: Bestand) {
        try {
            val tmp = File(datei.parentFile, "bestand.json.neu")
            tmp.writeText(alsJson(b).toString())
            if (!tmp.renameTo(datei)) { datei.delete(); tmp.renameTo(datei) }
        } catch (e: Exception) {
            Log.e(TAG, "Speichern fehlgeschlagen", e)
        }
    }

    companion object {
        private const val TAG = "Ablage"

        fun alsJson(b: Bestand): JSONObject = BestandJson.alsJson(b)
        fun ausJson(o: JSONObject): Bestand = BestandJson.ausJson(o)
    }
}
