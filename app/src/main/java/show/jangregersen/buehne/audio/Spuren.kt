package show.jangregersen.buehne.audio

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

/** Wohin eine Spur geht. */
enum class Rolle {
    /** Fertig gemischte Stereo-Datei: links Band, rechts Click (so lief es auf dem Nothing). */
    STEREO,
    /** Einzelspur fürs Publikum, landet nur links. */
    BAND,
    /** Click, Cue, Ansage: landet nur rechts im Ohr. */
    OHR
}

data class Spur(val name: String, val rolle: Rolle, val uri: String)

object Spuren {
    val AUDIO_ENDUNGEN = setOf("wav", "mp3", "flac", "m4a", "ogg", "aac", "aif", "aiff")

    /** Mixer-Kanäle einer Stereo-Datei. */
    const val KANAL_BAND = "band"
    const val KANAL_CLICK = "click"

    fun istAudio(name: String?): Boolean =
        name != null && name.substringAfterLast('.', "").lowercase() in AUDIO_ENDUNGEN

    fun rolleFuer(dateiname: String): Rolle {
        val n = dateiname.lowercase()
        return if (listOf("click", "klick", "cue", "ansage", "count", "metronom").any { it in n }) Rolle.OHR else Rolle.BAND
    }

    fun spurName(dateiname: String): String = dateiname.substringBeforeLast('.')

    /**
     * Sucht die Spuren zu einem gespeicherten Pfad: "baumUri||Ordner" = Einzelspuren im Ordner,
     * sonst eine einzelne Stereo-Datei.
     */
    fun finden(context: Context, pfad: String): List<Spur> {
        if (pfad.isBlank()) return emptyList()
        val trenn = pfad.indexOf("||")
        if (trenn < 0) return listOf(Spur("Datei", Rolle.STEREO, pfad))
        val baum = DocumentFile.fromTreeUri(context, Uri.parse(pfad.substring(0, trenn))) ?: return emptyList()
        val ordner = baum.findFile(pfad.substring(trenn + 2)) ?: return emptyList()
        return ordner.listFiles()
            .filter { it.isFile && istAudio(it.name) }
            .sortedWith(compareBy({ rolleFuer(it.name!!) }, { it.name!!.lowercase() }))
            .map { Spur(spurName(it.name!!), rolleFuer(it.name!!), it.uri.toString()) }
    }

    /** Mixer-Kanäle eines Songs (für die Mixer-Seite), ohne Dateien zu öffnen, wenn möglich. */
    fun kanaele(spuren: List<Spur>): List<Pair<String, Rolle>> =
        if (spuren.size == 1 && spuren[0].rolle == Rolle.STEREO) listOf(KANAL_BAND to Rolle.BAND, KANAL_CLICK to Rolle.OHR)
        else spuren.map { it.name.lowercase() to it.rolle }
}
