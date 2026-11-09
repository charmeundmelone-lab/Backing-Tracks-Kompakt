package show.jangregersen.buehne.audio

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import show.jangregersen.buehne.daten.Ablage
import show.jangregersen.buehne.daten.Song

private const val TAG = "Import"

/**
 * Holt Songs aus einem gewählten Ordner (Regel wie in der alten App):
 * - Audiodateien direkt im Ordner = je ein Stereo-Song (links Band, rechts Click).
 * - Unterordner mit Audiodateien = je ein Song aus Einzelspuren.
 * Schon vorhandene Songs (gleicher Pfad) werden nur aktualisiert, nie doppelt angelegt.
 */
object Import {

    data class Ergebnis(val neu: Int, val aktualisiert: Int)

    data class Name(val titel: String, val interpret: String, val tonart: String, val kapo: Int, val bpm: Int)

    fun ordnerHolen(context: Context, baum: Uri, ablage: Ablage, fortschritt: (String) -> Unit): Ergebnis {
        runCatching {
            context.contentResolver.takePersistableUriPermission(baum, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }.onFailure { Log.w(TAG, "Dauerhafte Freigabe fehlgeschlagen: ${it.message}") }

        val wurzel = DocumentFile.fromTreeUri(context, baum) ?: return Ergebnis(0, 0)
        val alle = runCatching { wurzel.listFiles().toList() }.getOrDefault(emptyList())
        var neu = 0; var aktualisiert = 0

        fun eintragen(pfad: String, roh: String, dauer: Long) {
            val n = nameLesen(roh)
            val vorhanden = ablage.jetzt.songs.firstOrNull { it.pfad == pfad }
            if (vorhanden != null) {
                ablage.aendern { b -> b.copy(songs = b.songs.map { if (it.id == vorhanden.id) it.copy(dauerMs = if (dauer > 0) dauer else it.dauerMs) else it }) }
                aktualisiert++
            } else {
                val id = ablage.neueId()
                val s = Song(id = id, titel = n.titel, interpret = n.interpret, bpm = n.bpm, tonart = n.tonart, kapo = n.kapo, pfad = pfad, dauerMs = dauer)
                ablage.aendern { b -> b.copy(songs = b.songs + s) }
                neu++
            }
        }

        for (datei in alle.filter { it.isFile && Spuren.istAudio(it.name) }.sortedBy { it.name?.lowercase() }) {
            val roh = Spuren.spurName(datei.name ?: continue)
            fortschritt(roh)
            eintragen(datei.uri.toString(), roh, dauerVon(context, datei.uri))
        }
        for (ordner in alle.filter { it.isDirectory }.sortedBy { it.name?.lowercase() }) {
            val roh = ordner.name ?: continue
            val dateien = runCatching { ordner.listFiles().filter { it.isFile && Spuren.istAudio(it.name) } }.getOrDefault(emptyList())
            if (dateien.isEmpty()) continue
            fortschritt(roh)
            val messen = dateien.firstOrNull { Spuren.rolleFuer(it.name ?: "") == Rolle.BAND } ?: dateien.first()
            eintragen("$baum||$roh", roh, dauerVon(context, messen.uri))
        }
        return Ergebnis(neu, aktualisiert)
    }

    /**
     * Liest Jans Dateinamen-Schema: "Country Roads KeyA K0", "Get Lucky KeyBDorian K0",
     * "Shake it off KeyF#m K2", optional "Interpret - Titel" und "120 bpm".
     */
    fun nameLesen(roh: String): Name {
        var t = roh.replace('_', ' ').trim()
        var bpm = 0
        Regex("""(\d{2,3})\s*bpm""", RegexOption.IGNORE_CASE).find(t)?.let { bpm = it.groupValues[1].toInt(); t = t.removeRange(it.range) }
        var tonart = ""
        Regex("""(?<=\s|^)Key\s*([A-Ga-g])([#b]?)([A-Za-z]*)""").find(t)?.let { m ->
            val grund = m.groupValues[1].uppercase() + m.groupValues[2]
            val rest = m.groupValues[3].lowercase()
            tonart = when {
                rest.isEmpty() -> grund
                rest == "m" || rest == "moll" || rest == "min" -> grund + "m"
                rest.startsWith("dor") -> "$grund dorisch"
                rest.startsWith("mix") -> "$grund mixolydisch"
                rest.startsWith("dur") || rest == "maj" -> grund
                else -> grund + rest
            }
            t = t.removeRange(m.range)
        }
        var kapo = 0
        Regex("""(?<=\s|^)[Kk](\d{1,2})(?=\s|$)""").find(t)?.let { kapo = it.groupValues[1].toInt().coerceIn(0, 11); t = t.removeRange(it.range) }
        t = t.replace(Regex("""\s+"""), " ").trim().trimEnd('-').trim()
        var interpret = ""
        val strich = t.indexOf(" - ")
        if (strich > 0) { interpret = t.substring(0, strich).trim(); t = t.substring(strich + 3).trim() }
        return Name(titel = t.ifBlank { roh }, interpret = interpret, tonart = tonart, kapo = kapo, bpm = bpm)
    }

    fun dauerVon(context: Context, uri: Uri): Long {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(context, uri)
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (e: Exception) { 0L } finally { runCatching { r.release() } }
    }
}
