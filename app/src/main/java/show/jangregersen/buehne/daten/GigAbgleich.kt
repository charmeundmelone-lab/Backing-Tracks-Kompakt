package show.jangregersen.buehne.daten

/**
 * B11: Gigs und Setlisten am Laptop UND am Mudita. Jans Regel (14.11.): **Der Mudita wird
 * geschützt.** Der Laptop ersetzt einen Gig nur, wenn er auf dem Mudita seit dem letzten Holen
 * unverändert ist. Sonst bleibt der Mudita-Stand, und die Werkstatt sagt, welcher Gig nicht
 * übernommen wurde. Nie geht etwas still verloren.
 *
 * Dafür trägt jede Laptop-Änderung den [abdruck] des Mudita-Stands, auf dem sie beruht. Der Abdruck
 * lässt "gespielt" weg: Das ist Live-Zustand vom Gig-Abend und zählt nicht als Änderung. Beim
 * Ersetzen bleiben die gespielt-Haken vom Mudita stehen.
 *
 * Am Laptop neu angelegte Gigs und Sets haben vorläufige negative Nummern. Der Mudita vergibt
 * beim Übernehmen echte Nummern aus seinem Zähler, so kann nichts mit dort angelegten kollidieren.
 *
 * Geteilter Code (App + Werkstatt): frei von Android-Teilen halten.
 */
object GigAbgleich {
    /** Fingerabdruck eines Gigs ohne "gespielt". Gleicher Abdruck = inhaltlich gleicher Gig. */
    fun abdruck(g: Gig): String = buildString {
        append(g.name).append('|').append(g.datum).append('|').append(g.autoWeiter)
        g.sets.forEach { s ->
            append("|S").append(s.id).append(':').append(s.name)
            s.eintraege.forEach { e -> append(',').append(e.songId).append('/').append(e.ende) }
        }
    }

    /**
     * Eine Laptop-Änderung an einem Gig. [gig] = neuer Stand (null = am Laptop gelöscht),
     * [basis] = Abdruck des Mudita-Stands, auf dem die Änderung beruht (null = am Laptop neu).
     */
    data class Aenderung(val gigId: Long, val gig: Gig?, val basis: String?)

    data class Ergebnis(val bestand: Bestand, val uebernommen: List<String>, val abgelehnt: List<String>)

    /** Prüft eine Änderung gegen den Mudita-Stand [b]: null = geht durch, sonst der Grund. */
    fun hindernis(b: Bestand, a: Aenderung): String? {
        if (a.basis == null) return null
        val dort = b.gig(a.gigId) ?: return "am Mudita gelöscht"
        return if (abdruck(dort) != a.basis) "am Mudita geändert" else null
    }

    /** Übernimmt die Laptop-Änderungen in den Mudita-Bestand [b] (läuft auf dem Kompakt). */
    fun uebernehmen(b: Bestand, aenderungen: List<Aenderung>): Ergebnis {
        var neu = b
        var naechste = b.naechsteId
        val ok = mutableListOf<String>()
        val nein = mutableListOf<String>()
        val songIds = b.songs.map { it.id }.toSet()
        // Songs, die es auf dem Mudita nicht (mehr) gibt, fallen aus den Sets.
        fun saeubern(s: SetListe, alt: SetListe?) = s.copy(
            id = if (s.id < 0) naechste++ else s.id,
            eintraege = s.eintraege.filter { it.songId in songIds }.distinctBy { it.songId }.map { e ->
                e.copy(gespielt = alt?.eintraege?.firstOrNull { it.songId == e.songId }?.gespielt ?: false)
            }
        )
        for (a in aenderungen) {
            val name = a.gig?.name ?: neu.gig(a.gigId)?.name ?: "Gig"
            val grund = hindernis(neu, a)
            if (grund != null) { nein += "„$name“ ($grund)"; continue }
            val dort = neu.gig(a.gigId)
            val g = a.gig
            when {
                g == null -> {
                    if (dort != null) { neu = neu.copy(gigs = neu.gigs.filterNot { it.id == a.gigId }); ok += "„$name“ gelöscht" }
                }
                dort == null -> {
                    val gid = if (g.id < 0 || neu.gig(g.id) != null) naechste++ else g.id
                    neu = neu.copy(gigs = neu.gigs + g.copy(id = gid, sets = g.sets.map { saeubern(it, null) }))
                    ok += "„$name“ neu"
                }
                else -> {
                    val ersetzt = g.copy(id = dort.id, sets = g.sets.map { s -> saeubern(s, dort.sets.firstOrNull { it.id == s.id }) })
                    neu = neu.copy(gigs = neu.gigs.map { if (it.id == dort.id) ersetzt else it })
                    ok += "„$name“"
                }
            }
        }
        return Ergebnis(neu.copy(naechsteId = maxOf(naechste, neu.naechsteId)), ok, nein)
    }
}
