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
    val mix: Map<String, Int> = emptyMap(),
    /** Kurze Notiz für die Bühne (27.11.), z. B. „Drop D“ oder „Einsatz nach 2 Takten“. Nur am Mudita einzutragen. */
    val notiz: String = ""
)

/**
 * Jans Wahl 15.11.: fünf Pausen zum Umschalten, jeder Song hat ohne eigenen Eintrag 10 s.
 * Gespeichert: 0 = kein Eintrag (gilt als 10 s), [KEINE_PAUSE] = bewusst 0 s, sonst Sekunden.
 * So bleiben alte Daten (dort hieß 0 "nichts eingestellt") ohne Umschreiben gültig.
 */
val PAUSE_WAHL = listOf(0, 10, 30, 60, 120)
const val PAUSE_STANDARD = 10
const val KEINE_PAUSE = -1

/** Stille Pause nach diesem Song in Sekunden, so wie die Automatik sie nimmt. */
val Song.pauseDanach: Int get() = when {
    pauseSek < 0 -> 0
    pauseSek == 0 -> PAUSE_STANDARD
    else -> pauseSek
}

/** Wert zum Speichern für eine gewählte Pause (0 s wird [KEINE_PAUSE]). */
fun pauseSpeichern(sek: Int): Int = if (sek <= 0) KEINE_PAUSE else sek

/** Nächster Wert im Umschalter: 10 s → 30 s → 1 min → 2 min → 0 s → wieder 10 s. */
fun naechstePause(sek: Int): Int = PAUSE_WAHL.firstOrNull { it > sek } ?: PAUSE_WAHL.first()

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
    val autoWeiter: Boolean = true,
    /**
     * Joker (27.11.): Songs, die an diesem Abend vielleicht gewünscht werden. Stehen in keinem Set,
     * live im Reiter „Joker“ mit einem Tipp als Nächstes einplanbar.
     */
    val joker: List<Long> = emptyList()
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
