package show.jangregersen.werkstatt

import show.jangregersen.buehne.daten.Bestand
import java.io.File
import java.net.URLDecoder

/**
 * B12: Neue oder neu exportierte Songs vom Laptop auf den Mudita. Maßgeblich sind Jans fertige
 * WAVs in `Music\Backing Tracks\Nothingphone` (Entscheidung 13.11.). Die Werkstatt vergleicht
 * sie mit den Dateien auf dem Mudita, kopiert die gewählten per USB in denselben Ordner, und
 * TrikTrak liest den Ordner danach selbst neu (Flag `neuSuchen` in eingang.json).
 */
object NeueSongs {
    val laptopOrdner = File(System.getProperty("user.home"), "Music/Backing Tracks/Nothingphone")
    const val STANDARD_ORDNER = "/sdcard/Music/Backing Tracks/Nothingphone"
    private val AUDIO = setOf("wav", "mp3", "m4a", "flac", "ogg", "aac")

    fun istAudio(name: String) = name.substringAfterLast('.', "").lowercase() in AUDIO

    /**
     * Ordner auf dem Mudita, aus dem TrikTrak seine Stereo-Songs hat: aus dem Pfad eines Songs
     * ("content://…/tree/primary%3AMusic%2FBacking%20Tracks%2FNothingphone/document/…").
     */
    fun muditaOrdner(b: Bestand): String {
        for (s in b.songs) {
            val baum = Regex("/tree/([^/]+)/document/").find(s.pfad)?.groupValues?.get(1) ?: continue
            val pfad = URLDecoder.decode(baum, "UTF-8")
            if (pfad.startsWith("primary:")) return "/sdcard/" + pfad.removePrefix("primary:")
        }
        return STANDARD_ORDNER
    }

    /** Dateiname, der zu einem Song gehört (nur Stereo-Songs aus einer Datei), sonst null. */
    fun dateiname(pfad: String): String? {
        if ("||" in pfad) return null
        val dok = pfad.substringAfterLast("/document/", "").ifEmpty { return null }
        return URLDecoder.decode(dok, "UTF-8").substringAfterLast('/')
    }

    enum class Stand { NEU, GLEICH, ANDERS, UNBEKANNT }

    data class Datei(val name: String, val groesse: Long, val stand: Stand, val titel: String?)

    /**
     * Vergleich Laptop ↔ Mudita. [mudita] = Dateiname → Größe auf dem Mudita (null = Mudita nicht
     * angeschlossen; dann zählt nur, ob es schon einen Song mit diesem Dateinamen gibt).
     */
    fun vergleichen(laptop: List<Pair<String, Long>>, b: Bestand, mudita: Map<String, Long>?): List<Datei> {
        val songVon = b.songs.mapNotNull { s -> dateiname(s.pfad)?.let { it.lowercase() to s.titel } }.toMap()
        val dort = mudita?.mapKeys { it.key.lowercase() }
        return laptop.filter { istAudio(it.first) }.sortedBy { it.first.lowercase() }.map { (name, groesse) ->
            val titel = songVon[name.lowercase()]
            val stand = when {
                dort == null -> if (titel != null) Stand.UNBEKANNT else Stand.NEU
                name.lowercase() !in dort -> Stand.NEU
                dort[name.lowercase()] == groesse -> Stand.GLEICH
                else -> Stand.ANDERS
            }
            Datei(name, groesse, stand, titel)
        }
    }

    /** Ausgabe von `stat -c '%s|%n'` im Mudita-Ordner → Dateiname → Größe. */
    fun statLesen(ausgabe: String): Map<String, Long> = ausgabe.lines().mapNotNull { z ->
        val i = z.indexOf('|')
        if (i <= 0) null else z.substring(0, i).trim().toLongOrNull()?.let { z.substring(i + 1).trim().substringAfterLast('/') to it }
    }.toMap()
}
