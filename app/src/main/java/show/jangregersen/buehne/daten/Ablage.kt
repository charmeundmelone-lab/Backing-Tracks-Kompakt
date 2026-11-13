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

    /**
     * Hat die Werkstatt am Laptop `eingang.json` abgelegt, kommen Text und Teile dieser Songs in
     * den Bestand (getippte Zeiten bleiben, wo sie noch passen). Erst speichern, dann die Datei
     * löschen. Ergebnis: Hinweis für Jan, oder null, wenn nichts da war.
     */
    @Synchronized
    fun eingangUebernehmen(): String? {
        val f = File(datei.parentFile, "eingang.json")
        if (!f.exists()) return null
        val o: JSONObject
        val songs = try {
            o = JSONObject(f.readText())
            if (o.optBoolean("ganz")) BestandJson.ausJson(o.getJSONObject("bestand")).also { require(it.songs.isNotEmpty()) }
            BestandJson.eingangAus(o)
        } catch (e: Exception) {
            Log.e(TAG, "eingang.json nicht lesbar", e)
            runCatching { f.renameTo(File(datei.parentFile, "eingang-defekt-${System.currentTimeMillis()}.json")) }
            return "Die Daten vom Laptop waren nicht lesbar. Bitte am Laptop noch einmal senden."
        }
        if (o.optBoolean("ganz")) {
            // Wiederherstellen aus einer Laptop-Sicherung: Der ganze Bestand wird ersetzt. Der Stand
            // davor bleibt als Datei liegen, falls das ein Irrtum war.
            val alt = _stand.value
            val neu = BestandJson.ausJson(o.getJSONObject("bestand")).let { it.copy(naechsteId = maxOf(it.naechsteId, alt.naechsteId)) }
            runCatching { File(datei.parentFile, "bestand-vor-wiederherstellung-${System.currentTimeMillis()}.json").writeText(alsJson(alt).toString()) }
            speicherJob?.cancel(); _stand.value = neu; schreiben(neu)
            f.delete()
            return "Vom Laptop wiederhergestellt: ${neu.songs.size} Songs, ${neu.gigs.size} Gigs."
        }
        val (neu, titel) = show.jangregersen.buehne.lyrics.Abgleich.eingang(_stand.value, songs)
        if (neu != _stand.value) { speicherJob?.cancel(); _stand.value = neu; schreiben(neu) }
        f.delete()
        return when (titel.size) {
            0 -> "Vom Laptop: alles war schon auf dem neuesten Stand."
            1 -> "Vom Laptop übernommen: ${titel[0]}"
            else -> "Vom Laptop übernommen (${titel.size} Songs): " + titel.take(4).joinToString(", ") + if (titel.size > 4) " …" else ""
        }
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
