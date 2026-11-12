package show.jangregersen.buehne.lyrics

import show.jangregersen.buehne.daten.Song
import show.jangregersen.buehne.netz.Lrclib

/** Übernimmt einen Treffer der Songtext-Datenbank in einen Song. */
object Uebernahme {
    data class Vorschau(val ergebnis: Erkennung.Ergebnis, val abschnitte: List<Abschnitt>, val ersteZeile: String, val refrain: Boolean)

    fun vorschau(t: Lrclib.Treffer): Vorschau? {
        val e = Erkennung.aufbereiten(t.text, t.zeitmarken) ?: return null
        val ab = Abschnitte.zerlegen(e.text)
        val erste = ab.flatMap { it.zeilen }.firstOrNull { it.isNotBlank() } ?: return null
        return Vorschau(e, ab, erste, ab.any { Abschnitte.art(it.name) == Abschnitte.Art.REFRAIN })
    }

    fun uebernehmen(song: Song, t: Lrclib.Treffer): Song? {
        val v = vorschau(t) ?: return null
        val vorschlag = Zeitplan.markenVorschlag(v.abschnitte, v.ergebnis.zeiten, t.dauerMs, song.dauerMs)
        return song.copy(
            lyrics = v.ergebnis.text,
            zeilenZeiten = Zeitplan.zeitenSchreiben(v.ergebnis.zeiten),
            originalMs = t.dauerMs,
            interpret = song.interpret.ifBlank { t.interpret },
            marken = Abschnitte.markenSchreiben(vorschlag),
            markenVorlaeufig = vorschlag.isNotEmpty()
        )
    }
}
