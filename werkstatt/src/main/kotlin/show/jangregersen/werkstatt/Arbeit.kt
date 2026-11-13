package show.jangregersen.werkstatt

import org.json.JSONArray
import org.json.JSONObject
import show.jangregersen.buehne.daten.Bestand
import show.jangregersen.buehne.daten.BestandJson
import show.jangregersen.buehne.daten.Song
import show.jangregersen.buehne.daten.songAendern
import show.jangregersen.buehne.lyrics.Abgleich
import show.jangregersen.buehne.lyrics.Abschnitt
import show.jangregersen.buehne.lyrics.Abschnitte
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class Fehler(val meldung: String) : Exception(meldung)

/**
 * Die Arbeitskopie auf dem Laptop: derselbe Bestand wie auf dem Kompakt (`bestand.json`), dazu
 * was nur der Laptop wissen muss (welche Songs hier geändert wurden, welche Studio-One-Datei zu
 * welchem Song gehört). Vor jedem Überschreiben wird eine Sicherung abgelegt.
 */
class Arbeit(private val ordner: File) {
    private val datei = File(ordner, "bestand.json")
    private val zustandDatei = File(ordner, "zustand.json")
    private val sicherungen = File(ordner, "sicherungen")

    var bestand: Bestand = Bestand(); private set
    /** Songs, deren Text oder Teile am Laptop geändert und noch nicht zum Kompakt gesendet wurden. */
    val geaendert = sortedSetOf<Long>()
    /** Song → Studio-One-Datei, von Jan gewählt ("" = bewusst keine). */
    val s1 = mutableMapOf<Long, String>()
    var geholtAm: Long = 0; private set
    private val verlauf = mutableMapOf<Long, ArrayDeque<Song>>()

    init {
        ordner.mkdirs()
        if (datei.exists()) runCatching { bestand = BestandJson.ausJson(JSONObject(datei.readText())) }
        if (zustandDatei.exists()) runCatching {
            val o = JSONObject(zustandDatei.readText())
            o.optJSONArray("geaendert")?.let { a -> (0 until a.length()).forEach { geaendert += a.getLong(it) } }
            o.optJSONObject("s1")?.let { m -> m.keys().forEach { k -> s1[k.toLong()] = m.getString(k) } }
            geholtAm = o.optLong("geholtAm")
        }
    }

    @Synchronized
    private fun speichern() {
        val tmp = File(ordner, "bestand.json.neu")
        tmp.writeText(BestandJson.alsJson(bestand).toString())
        if (!tmp.renameTo(datei)) { datei.delete(); tmp.renameTo(datei) }
        zustandDatei.writeText(JSONObject().apply {
            put("geaendert", JSONArray(geaendert.toList()))
            put("s1", JSONObject().apply { s1.forEach { (k, v) -> put(k.toString(), v) } })
            put("geholtAm", geholtAm)
        }.toString(1))
    }

    private fun sichern() {
        if (!datei.exists()) return
        sicherungen.mkdirs()
        datei.copyTo(File(sicherungen, "bestand-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".json"), overwrite = true)
        // Die letzten 40 Sicherungen bleiben.
        sicherungen.listFiles()?.sortedByDescending { it.name }?.drop(40)?.forEach { it.delete() }
    }

    /**
     * Frischer Stand vom Kompakt. Was hier am Laptop an Text und Teilen geändert und noch nicht
     * gesendet wurde, bleibt erhalten (wird auf den neuen Stand gelegt).
     */
    @Synchronized
    fun vomGeraet(json: String) {
        var neu = try { BestandJson.ausJson(JSONObject(json)) } catch (e: Exception) { throw Fehler("Die Daten vom Mudita sind nicht lesbar. Es wurde nichts geändert.") }
        if (neu.songs.isEmpty() && bestand.songs.isNotEmpty()) throw Fehler("Der Mudita meldet gar keine Songs. Das sieht falsch aus, es wurde nichts geändert.")
        sichern()
        geaendert.retainAll(neu.songs.map { it.id }.toSet())
        // Hat der Mudita den Laptop-Stand schon (gesendet und übernommen), gilt der Song nicht mehr als offen.
        val angekommen = mutableSetOf<Long>()
        for (id in geaendert) {
            val hier = bestand.song(id) ?: continue
            val dort = neu.song(id) ?: continue
            val zusammen = Abgleich.textUndTeile(dort, hier)
            if (zusammen == dort) angekommen += id else neu = neu.songAendern(id) { zusammen }
        }
        geaendert.removeAll(angekommen)
        bestand = neu
        verlauf.clear()
        geholtAm = System.currentTimeMillis()
        speichern()
    }

    /** Ändert einen Song (nur Text und Teile). Der Stand davor bleibt für "Rückgängig" erhalten. */
    @Synchronized
    fun aendern(id: Long, f: (Song) -> Song): Boolean {
        val alt = bestand.song(id) ?: throw Fehler("Diesen Song gibt es nicht mehr.")
        val neu = f(alt)
        if (neu == alt) return false
        verlauf.getOrPut(id) { ArrayDeque() }.apply { addLast(alt); if (size > 50) removeFirst() }
        bestand = bestand.songAendern(id) { neu }
        geaendert += id
        speichern()
        return true
    }

    /** Inhalt für `eingang.json`: alle hier geänderten, noch nicht angekommenen Songs. */
    @Synchronized
    fun zumSenden(): String = BestandJson.eingangJson(geaendert.mapNotNull { bestand.song(it) }).toString()

    fun kannZurueck(id: Long) = verlauf[id]?.isNotEmpty() == true

    @Synchronized
    fun zurueck(id: Long): Boolean {
        val alt = verlauf[id]?.removeLastOrNull() ?: return false
        bestand = bestand.songAendern(id) { alt }
        speichern()
        return true
    }

    @Synchronized
    fun s1Setzen(id: Long, pfad: String) { s1[id] = pfad; speichern() }
}

/** Text-Umbauten, die es nur am Laptop gibt. */
object Texte {
    /**
     * Teilt Abschnitt [a] vor seiner Textzeile [zeile] (0 = erste Zeile; Leerzeilen zählen nicht).
     * Der neue zweite Abschnitt bekommt zunächst denselben Namen.
     */
    fun teilen(text: String, a: Int, zeile: Int): String {
        val l = Abschnitte.zerlegen(text).toMutableList()
        val ab = l.getOrNull(a) ?: return text
        var n = 0
        var schnitt = -1
        ab.zeilen.forEachIndexed { i, z -> if (z.isNotBlank()) { if (n == zeile) schnitt = i; n++ } }
        if (schnitt <= 0) return text
        l[a] = ab.copy(zeilen = ab.zeilen.take(schnitt).dropLastWhile { it.isBlank() })
        l.add(a + 1, Abschnitt(ab.name, ab.zeilen.drop(schnitt)))
        return Abschnitte.zusammensetzen(l)
    }
}
