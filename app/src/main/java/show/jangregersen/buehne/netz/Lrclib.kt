package show.jangregersen.buehne.netz

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import show.jangregersen.buehne.lyrics.Erkennung
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.net.UnknownHostException
import java.io.IOException

/**
 * Freie Songtext-Datenbank LRCLIB (lrclib.net): ohne Konto, ohne Schlüssel. Der Betreiber
 * verlangt, dass sich die App mit Namen meldet und Anfragen nacheinander mit Pause schickt.
 */
object Lrclib {
    data class Treffer(
        val id: Long,
        val interpret: String,
        val titel: String,
        val dauerMs: Long,
        val text: String,
        val zeitmarken: String?
    )

    class Fehler(val meldung: String) : Exception(meldung)

    private const val KENNUNG = "TrikTrak/1.0 (Android-App für Live-Musik)"
    private val reihe = Mutex()
    private var letzte = 0L

    /** Sucht Songtexte. [suchwort] ergänzt den Titel (meist der Interpret). */
    suspend fun suchen(titel: String, suchwort: String, trackMs: Long): List<Treffer> = withContext(Dispatchers.IO) {
        val q = listOf(titelSauber(titel), suchwort.trim()).filter { it.isNotBlank() }.joinToString(" ")
        if (q.isBlank()) return@withContext emptyList()
        val roh = abfragen("https://lrclib.net/api/search?q=" + URLEncoder.encode(q, "UTF-8"))
        ordnen(lesen(roh), titelSauber(titel), trackMs)
    }

    /** "Teenage Dirtbag(2)" → "Teenage Dirtbag", "Song_neu" → "Song neu". */
    fun titelSauber(t: String): String =
        t.replace('_', ' ').replace(Regex("\\(\\d+\\)"), "").replace(Regex("\\s+"), " ").trim()

    /** "Gnarls Barkley - Crazy (Official Video) [4K]" → "Crazy". */
    fun anzeigeTitel(titel: String, interpret: String): String {
        var t = titel.trim()
        if (interpret.isNotBlank() && t.lowercase().startsWith(interpret.lowercase() + " - ")) t = t.substring(interpret.length + 3)
        t = t.replace(Regex("\\s*[\\[(][^)\\]]*(official|video|audio|lyric|remaster|hd|4k)[^)\\]]*[)\\]]", RegexOption.IGNORE_CASE), "")
        return t.trim().ifEmpty { titel.trim() }
    }

    private suspend fun abfragen(url: String): String = reihe.withLock {
        val warten = 450L - (System.currentTimeMillis() - letzte)
        if (warten > 0) delay(warten)
        try {
            repeat(2) { versuch ->
                val c = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000; readTimeout = 15000
                    setRequestProperty("User-Agent", KENNUNG)
                    setRequestProperty("Accept", "application/json")
                }
                try {
                    val code = c.responseCode
                    if (code == 429 && versuch == 0) {
                        val sek = c.getHeaderField("Retry-After")?.toLongOrNull() ?: 5
                        delay(sek.coerceIn(1, 30) * 1000 + 300)
                        return@repeat
                    }
                    if (code == 404) return@withLock "[]"
                    if (code !in 200..299) throw Fehler("Die Songtext-Datenbank meldet einen Fehler ($code). Bitte später noch mal.")
                    return@withLock c.inputStream.bufferedReader().readText()
                } finally { c.disconnect() }
            }
            throw Fehler("Die Songtext-Datenbank ist gerade überlastet. Bitte in einer Minute noch mal.")
        } catch (e: Fehler) { throw e
        } catch (e: UnknownHostException) { throw Fehler("Kein Internet. Ist WLAN an und der Offline+-Schalter aus?")
        } catch (e: SocketTimeoutException) { throw Fehler("Die Songtext-Datenbank antwortet nicht. Bitte später noch mal.")
        } catch (e: IOException) { throw Fehler("Keine Verbindung zur Songtext-Datenbank. Ist WLAN an?")
        } finally { letzte = System.currentTimeMillis() }
    }

    private fun lesen(json: String): List<Treffer> {
        val a = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
        return (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            if (o.optBoolean("instrumental")) return@mapNotNull null
            val text = if (o.isNull("plainLyrics")) "" else o.optString("plainLyrics")
            if (text.isBlank()) return@mapNotNull null
            val interpret = o.optString("artistName")
            Treffer(
                id = o.optLong("id"), interpret = interpret,
                titel = anzeigeTitel(o.optString("trackName"), interpret),
                dauerMs = (o.optDouble("duration", 0.0) * 1000).toLong(),
                text = text,
                zeitmarken = if (o.isNull("syncedLyrics")) null else o.optString("syncedLyrics").ifBlank { null }
            )
        }
    }

    /**
     * Doppelte zusammenfassen und sortieren: zuerst, was am wahrscheinlichsten Jans Song ist
     * (Titel passt, viele Einträge desselben Interpreten = das bekannte Original, Länge nah am
     * Track, Zeitmarken vorhanden; Live/Remix/Karaoke nach hinten).
     */
    fun ordnen(liste: List<Treffer>, titel: String, trackMs: Long): List<Treffer> {
        val nTitel = Erkennung.normal(titel)
        val proInterpret = liste.groupingBy { Erkennung.normal(it.interpret) }.eachCount()
        val einzeln = liste.groupBy { Erkennung.normal(it.interpret) + "|" + Erkennung.normal(it.titel) }
            .values.map { g -> g.maxByOrNull { (if (it.zeitmarken != null) 2 else 0) + (if (trackMs > 0 && kotlin.math.abs(it.dauerMs - trackMs) < 4000) 1 else 0) }!! }
        fun punkte(t: Treffer): Double {
            val nt = Erkennung.normal(t.titel)
            var p = 0.0
            if (nt == nTitel) p += 4 else if (nt.contains(nTitel) || nTitel.contains(nt)) p += 2
            p += minOf(proInterpret[Erkennung.normal(t.interpret)] ?: 1, 8) * 0.6
            if (t.zeitmarken != null) p += 1.5
            if (trackMs > 0 && t.dauerMs > 0) p += maxOf(0.0, 2.0 - kotlin.math.abs(t.dauerMs - trackMs) / 15000.0)
            if (Regex("live|remix|karaoke|instrumental|cover|tribute|sped up|slowed", RegexOption.IGNORE_CASE).containsMatchIn(t.titel + " " + t.interpret)) p -= 3
            return p
        }
        return einzeln.sortedByDescending { punkte(it) }
    }
}
