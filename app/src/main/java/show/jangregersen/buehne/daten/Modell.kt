package show.jangregersen.buehne.daten

/**
 * Datenmodell der Kompakt-App. Bewusst schlicht (eine JSON-Datei statt Room): ein Musiker
 * hat ein paar hundert Songs und eine Handvoll Gigs, das passt locker in den Speicher.
 */

/** Am Songende: wie geht es im Set weiter? */
object Ende {
    /** Nächster Song wird bereitgelegt, Wiedergabe hält an (alte App: CUE). */
    const val ANHALTEN = 0
    /** Show-Automatik: Ansage/Pause, dann startet der nächste Song von selbst. */
    const val WEITER = 2
}

/** Manueller Tempo-Stempel fürs Archiv (nicht aus BPM abgeleitet, wie in der alten App). */
object Tempo {
    const val OHNE = 0
    const val LANGSAM = 1
    const val MITTEL = 2
    const val SCHNELL = 3
    fun name(t: Int) = when (t) { LANGSAM -> "Langsam"; MITTEL -> "Mittel"; SCHNELL -> "Schnell"; else -> "–" }
}

data class Song(
    val id: Long,
    val titel: String,
    val interpret: String = "",
    val bpm: Int = 0,
    val tonart: String = "",
    val kapo: Int = 0,
    val tempo: Int = Tempo.OHNE,
    /** SAF-Datei-URI (eine Stereo-WAV) oder "baumUri||Ordnername" (Einzelspuren). */
    val pfad: String,
    val dauerMs: Long = 0,
    val lyrics: String = "",
    /** Einmess-Punkte: "abschnittIndex:ms" kommagetrennt. */
    val marken: String = "",
    /** true = Punkte stammen aus den Zeitmarken des Originals und sind noch nicht von Jan bestätigt. */
    val markenVorlaeufig: Boolean = false,
    /** Zeit jeder Textzeile im Original (ms, kommagetrennt, -1 = unbekannt). Gilt nur bei passender Zeilenzahl. */
    val zeilenZeiten: String = "",
    /** Länge des Originals aus der Songtext-Datenbank (zum Vergleich mit dem Track). */
    val originalMs: Long = 0,
    val ansageVorher: String = "",
    val ansageVorherMs: Long = 0,
    val ansageNachher: String = "",
    val ansageNachherMs: Long = 0,
    /** Stille Pause nach diesem Song in Sekunden (nur wenn keine Ansage da ist). */
    val pauseSek: Int = 0,
    /** Mixer: Spurname -> Stufe 0..10, negativ = stumm (Betrag = Stufe vor dem Stummschalten). */
    val mix: Map<String, Int> = emptyMap()
)

data class Eintrag(
    val songId: Long,
    val gespielt: Boolean = false,
    val ende: Int = Ende.ANHALTEN
)

data class SetListe(
    val id: Long,
    val name: String,
    val eintraege: List<Eintrag> = emptyList()
)

data class Gig(
    val id: Long,
    val name: String,
    /** Tage seit 1970-01-01, 0 = ohne Datum. */
    val datum: Long = 0,
    val sets: List<SetListe> = emptyList(),
    val autoWeiter: Boolean = true
)

data class Bestand(
    val songs: List<Song> = emptyList(),
    val gigs: List<Gig> = emptyList(),
    val naechsteId: Long = 1
) {
    fun song(id: Long?): Song? = if (id == null) null else songs.firstOrNull { it.id == id }
    fun gig(id: Long?): Gig? = if (id == null) null else gigs.firstOrNull { it.id == id }
    fun gigVonSet(setId: Long?): Gig? = if (setId == null) null else gigs.firstOrNull { g -> g.sets.any { it.id == setId } }
    fun set(setId: Long?): SetListe? = gigVonSet(setId)?.sets?.firstOrNull { it.id == setId }
}
