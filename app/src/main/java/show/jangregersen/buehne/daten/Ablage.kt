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
import org.json.JSONArray
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

        fun alsJson(b: Bestand): JSONObject = JSONObject().apply {
            put("version", 1)
            put("naechsteId", b.naechsteId)
            put("songs", JSONArray().apply { b.songs.forEach { put(songJson(it)) } })
            put("gigs", JSONArray().apply { b.gigs.forEach { put(gigJson(it)) } })
        }

        fun ausJson(o: JSONObject): Bestand {
            val songs = o.optJSONArray("songs")?.let { a -> (0 until a.length()).map { songAus(a.getJSONObject(it)) } } ?: emptyList()
            val gigs = o.optJSONArray("gigs")?.let { a -> (0 until a.length()).map { gigAus(a.getJSONObject(it)) } } ?: emptyList()
            val maxId = (songs.map { it.id } + gigs.map { it.id } + gigs.flatMap { g -> g.sets.map { it.id } }).maxOrNull() ?: 0
            return Bestand(songs, gigs, maxOf(o.optLong("naechsteId", 1), maxId + 1))
        }

        private fun songJson(s: Song) = JSONObject().apply {
            put("id", s.id); put("titel", s.titel); put("interpret", s.interpret); put("bpm", s.bpm)
            put("tonart", s.tonart); put("kapo", s.kapo); put("tempo", s.tempo); put("pfad", s.pfad)
            put("dauerMs", s.dauerMs); put("lyrics", s.lyrics); put("marken", s.marken)
            put("ansageVorher", s.ansageVorher); put("ansageVorherMs", s.ansageVorherMs)
            put("ansageNachher", s.ansageNachher); put("ansageNachherMs", s.ansageNachherMs)
            put("pauseSek", s.pauseSek)
            put("mix", JSONObject().apply { s.mix.forEach { (k, v) -> put(k, v) } })
        }

        private fun songAus(o: JSONObject): Song {
            val mixO = o.optJSONObject("mix")
            val mix = mutableMapOf<String, Int>()
            mixO?.keys()?.forEach { k -> mix[k] = mixO.optInt(k, 10) }
            return Song(
                id = o.getLong("id"), titel = o.optString("titel"), interpret = o.optString("interpret"),
                bpm = o.optInt("bpm"), tonart = o.optString("tonart"), kapo = o.optInt("kapo"),
                tempo = o.optInt("tempo"), pfad = o.optString("pfad"), dauerMs = o.optLong("dauerMs"),
                lyrics = o.optString("lyrics"), marken = o.optString("marken"),
                ansageVorher = o.optString("ansageVorher"), ansageVorherMs = o.optLong("ansageVorherMs"),
                ansageNachher = o.optString("ansageNachher"), ansageNachherMs = o.optLong("ansageNachherMs"),
                pauseSek = o.optInt("pauseSek"), mix = mix
            )
        }

        private fun gigJson(g: Gig) = JSONObject().apply {
            put("id", g.id); put("name", g.name); put("datum", g.datum); put("autoWeiter", g.autoWeiter)
            put("sets", JSONArray().apply {
                g.sets.forEach { s ->
                    put(JSONObject().apply {
                        put("id", s.id); put("name", s.name)
                        put("eintraege", JSONArray().apply {
                            s.eintraege.forEach { e ->
                                put(JSONObject().apply { put("songId", e.songId); put("gespielt", e.gespielt); put("ende", e.ende) })
                            }
                        })
                    })
                }
            })
        }

        private fun gigAus(o: JSONObject): Gig {
            val sets = o.optJSONArray("sets")?.let { a ->
                (0 until a.length()).map { i ->
                    val s = a.getJSONObject(i)
                    val e = s.optJSONArray("eintraege")
                    SetListe(
                        id = s.getLong("id"), name = s.optString("name"),
                        eintraege = if (e == null) emptyList() else (0 until e.length()).map { j ->
                            val x = e.getJSONObject(j)
                            Eintrag(x.getLong("songId"), x.optBoolean("gespielt"), x.optInt("ende", Ende.WEITER))
                        }
                    )
                }
            } ?: emptyList()
            return Gig(o.getLong("id"), o.optString("name"), o.optLong("datum"), sets, o.optBoolean("autoWeiter", true))
        }
    }
}
