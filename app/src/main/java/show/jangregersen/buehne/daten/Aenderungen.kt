package show.jangregersen.buehne.daten

/** Reine Funktionen: nehmen einen Bestand und geben den geänderten zurück. */

fun Bestand.mitSong(song: Song): Bestand =
    if (songs.any { it.id == song.id }) copy(songs = songs.map { if (it.id == song.id) song else it })
    else copy(songs = songs + song)

fun Bestand.ohneSong(songId: Long): Bestand = copy(
    songs = songs.filterNot { it.id == songId },
    gigs = gigs.map { g -> g.copy(sets = g.sets.map { s -> s.copy(eintraege = s.eintraege.filterNot { it.songId == songId }) }) }
)

fun Bestand.songAendern(songId: Long, f: (Song) -> Song): Bestand =
    copy(songs = songs.map { if (it.id == songId) f(it) else it })

fun Bestand.mitGig(gig: Gig): Bestand =
    if (gigs.any { it.id == gig.id }) copy(gigs = gigs.map { if (it.id == gig.id) gig else it })
    else copy(gigs = gigs + gig)

fun Bestand.ohneGig(gigId: Long): Bestand = copy(gigs = gigs.filterNot { it.id == gigId })

fun Bestand.gigAendern(gigId: Long, f: (Gig) -> Gig): Bestand =
    copy(gigs = gigs.map { if (it.id == gigId) f(it) else it })

fun Bestand.setAendern(setId: Long, f: (SetListe) -> SetListe): Bestand =
    copy(gigs = gigs.map { g -> if (g.sets.any { it.id == setId }) g.copy(sets = g.sets.map { if (it.id == setId) f(it) else it }) else g })

fun Bestand.eintragAendern(setId: Long, songId: Long, f: (Eintrag) -> Eintrag): Bestand =
    setAendern(setId) { s -> s.copy(eintraege = s.eintraege.map { if (it.songId == songId) f(it) else it }) }

/**
 * Songende, das in diesem Set üblich ist (bei Gleichstand Automatik, denn der letzte Song eines
 * Automatik-Sets hält oft an). Neue Songs bekommen es (15.11.): Ein
 * Wunschsong in einem Set mit Show-Automatik darf die Automatik nicht anhalten.
 */
fun SetListe.ueblichesEnde(): Int =
    eintraege.count { it.ende == Ende.WEITER }.let { w -> if (w > 0 && w >= eintraege.size - w) Ende.WEITER else Ende.ANHALTEN }

fun Bestand.songsHinzufuegen(setId: Long, songIds: List<Long>): Bestand = setAendern(setId) { s ->
    val schon = s.eintraege.map { it.songId }.toSet()
    s.copy(eintraege = s.eintraege + songIds.filterNot { it in schon }.map { Eintrag(it, ende = s.ueblichesEnde()) })
}

fun Bestand.ausSetNehmen(setId: Long, songId: Long): Bestand =
    setAendern(setId) { s -> s.copy(eintraege = s.eintraege.filterNot { it.songId == songId }) }

/** Verschiebt [songId] im Set direkt vor [vorSongId] (null = ans Ende). */
fun Bestand.verschieben(setId: Long, songId: Long, vorSongId: Long?): Bestand = setAendern(setId) { s ->
    val e = s.eintraege.firstOrNull { it.songId == songId } ?: return@setAendern s
    val rest = s.eintraege.filterNot { it.songId == songId }.toMutableList()
    val idx = if (vorSongId == null) rest.size else rest.indexOfFirst { it.songId == vorSongId }.let { if (it < 0) rest.size else it }
    rest.add(idx, e)
    s.copy(eintraege = rest)
}

/** Einen Platz nach oben (-1) oder unten (+1). */
fun Bestand.schieben(setId: Long, songId: Long, richtung: Int): Bestand = setAendern(setId) { s ->
    val l = s.eintraege.toMutableList()
    val i = l.indexOfFirst { it.songId == songId }
    val j = i + richtung
    if (i < 0 || j !in l.indices) return@setAendern s
    val x = l[i]; l[i] = l[j]; l[j] = x
    s.copy(eintraege = l)
}

/** Legt [songId] direkt hinter den gerade laufenden Song [aktuellId]. */
fun Bestand.alsNaechstes(setId: Long, songId: Long, aktuellId: Long?): Bestand = setAendern(setId) { s ->
    val e = s.eintraege.firstOrNull { it.songId == songId } ?: return@setAendern s
    val rest = s.eintraege.filterNot { it.songId == songId }.toMutableList()
    val anker = rest.indexOfFirst { it.songId == aktuellId }
    val idx = if (anker >= 0) anker + 1 else rest.indexOfFirst { !it.gespielt }.let { if (it < 0) rest.size else it }
    rest.add(idx, e.copy(gespielt = false))
    s.copy(eintraege = rest)
}

fun Bestand.gespieltZuruecksetzen(setId: Long): Bestand =
    setAendern(setId) { s -> s.copy(eintraege = s.eintraege.map { it.copy(gespielt = false) }) }

fun Bestand.setSchieben(gigId: Long, setId: Long, richtung: Int): Bestand = gigAendern(gigId) { g ->
    val l = g.sets.toMutableList()
    val i = l.indexOfFirst { it.id == setId }
    val j = i + richtung
    if (i < 0 || j !in l.indices) return@gigAendern g
    val x = l[i]; l[i] = l[j]; l[j] = x
    g.copy(sets = l)
}

fun Bestand.ohneSet(setId: Long): Bestand =
    copy(gigs = gigs.map { g -> g.copy(sets = g.sets.filterNot { it.id == setId }) })

/**
 * Der Song, der nach [aktuellId] dran ist (27.11., DANACH-Schild statt Umsortieren): Trägt ein
 * offener Song das Schild ([danachId]), kommt er. Sonst der erste offene Song von oben, also Jans
 * Plan in seiner Reihenfolge. Der aktuelle zählt nie.
 */
fun SetListe.naechster(aktuellId: Long?, danachId: Long?): Eintrag? =
    eintraege.firstOrNull { it.songId == danachId && !it.gespielt && it.songId != aktuellId }
        ?: eintraege.firstOrNull { !it.gespielt && it.songId != aktuellId }

/**
 * Mit welchem Song ein Set beginnt: der mit dem Schild ([danachId], z. B. in der Vorschau des
 * Folge-Sets angetippt), sonst der erste offene.
 */
fun SetListe.anfang(danachId: Long?): Eintrag? =
    eintraege.firstOrNull { it.songId == danachId && !it.gespielt } ?: ersterOffener()

fun SetListe.ersterOffener(): Eintrag? = eintraege.firstOrNull { !it.gespielt }

fun SetListe.dauerMs(b: Bestand, nurOffene: Boolean = false): Long =
    eintraege.filter { !nurOffene || !it.gespielt }.sumOf { b.song(it.songId)?.dauerMs ?: 0L }

/**
 * Testmodus (15.11., werkzeug/testlauf.sh): aus Jans Bestand eine Spielwiese machen. Alle Songs
 * bleiben (fürs Archiv/Wunschsong), Gigs werden ersetzt durch einen Gig "Testlauf" am Tag [heute]
 * mit zwei kurzen Sets aus den 8-s-Songs "Test Eins/Zwei/Drei" (sonst die drei kürzesten Songs),
 * Automatik mit 20 s Pause (lang genug, um die Pause-Seite zu prüfen), keine Ansagen.
 */
fun Bestand.testlauf(heute: Long): Bestand {
    val namen = listOf("Test Eins", "Test Zwei", "Test Drei")
    val drei = namen.mapNotNull { n -> songs.firstOrNull { it.titel == n } }.takeIf { it.size == 3 }
        ?: songs.filter { it.dauerMs > 0 }.sortedBy { it.dauerMs }.take(3)
    if (drei.size < 3) return copy(gigs = emptyList())
    val ids = drei.map { it.id }.toSet()
    val (a, bb, c) = drei
    var id = naechsteId
    val gig = Gig(id++, "Testlauf", heute, listOf(
        SetListe(id++, "Set A", listOf(Eintrag(a.id, ende = Ende.WEITER), Eintrag(bb.id, ende = Ende.WEITER), Eintrag(c.id, ende = Ende.ANHALTEN))),
        SetListe(id++, "Set B", listOf(Eintrag(c.id, ende = Ende.WEITER), Eintrag(a.id, ende = Ende.ANHALTEN)))
    ), autoWeiter = false)
    return copy(
        songs = songs.map { if (it.id in ids) it.copy(pauseSek = 20, ansageVorher = "", ansageNachher = "") else it },
        gigs = listOf(gig), naechsteId = id
    )
}

/**
 * Antippen = kommt als Nächstes dran (Jans eine Regel, 15.11.), seit 27.11. ohne Umsortieren: Der
 * Song bleibt an seinem Platz (die Bühne gibt ihm das Schild DANACH oder legt ihn bereit). Hier
 * passiert nur: Fehlt [songId] im Set (Wunschsong aus dem Archiv), kommt er ans Ende; war er
 * gespielt, gilt er wieder als offen. Jans geplante Reihenfolge bleibt.
 */
fun Bestand.einplanen(setId: Long, songId: Long): Bestand = setAendern(setId) { s ->
    if (s.eintraege.none { it.songId == songId }) s.copy(eintraege = s.eintraege + Eintrag(songId, ende = s.ueblichesEnde()))
    else s.copy(eintraege = s.eintraege.map { if (it.songId == songId) it.copy(gespielt = false) else it })
}

/** Wo ein Song im Gig schon steht (Jans Wunsch 15.11.: keine Wiederholung ohne Rückfrage). */
data class SchonImGig(val setId: Long, val setName: String, val gespielt: Boolean)

/** Alle Songs, die im Gig von [setId] schon in einem Set stehen: songId → Fundstellen. */
fun Bestand.schonImGig(setId: Long?): Map<Long, List<SchonImGig>> {
    val gig = gigVonSet(setId) ?: return emptyMap()
    val m = linkedMapOf<Long, MutableList<SchonImGig>>()
    gig.sets.forEach { s -> s.eintraege.forEach { e -> m.getOrPut(e.songId) { mutableListOf() }.add(SchonImGig(s.id, s.name, e.gespielt)) } }
    return m
}

/**
 * Muss vor dem Einplanen von [songId] in [setId] gefragt werden? Ja, wenn er an diesem Abend schon
 * gespielt wurde oder in einem anderen Set steht. Ein noch offener Song im selben Set wird nur
 * nach vorn geholt, das ist keine Wiederholung.
 */
fun Bestand.wiederholungsFrage(setId: Long, songId: Long): String? {
    val fund = schonImGig(setId)[songId] ?: return null
    val gespielt = fund.firstOrNull { it.gespielt }
    return when {
        gespielt != null -> "wurde heute schon gespielt (${gespielt.setName})"
        fund.any { it.setId == setId } -> null
        else -> "steht schon in ${fund.joinToString(", ") { it.setName }}"
    }
}

/**
 * Reihenfolge, in der die Songs wirklich drankommen (wie [naechster]): der aktuelle, dann der mit
 * dem Schild, dann alle übrigen offenen von oben.
 */
fun SetListe.wasKommt(aktuellId: Long?, danachId: Long? = null): List<Eintrag> {
    val jetzt = eintraege.filter { it.songId == aktuellId }.take(1)
    val danach = eintraege.filter { it.songId == danachId && !it.gespielt && it.songId != aktuellId }.take(1)
    val rest = eintraege.filter { !it.gespielt && it.songId != aktuellId && it.songId != danach.firstOrNull()?.songId }
    return jetzt + danach + rest
}

/**
 * Nimmt [songId] neu ins Set auf, direkt hinter [nachId] (z. B. den laufenden Song).
 * Ohne Anker landet er vor dem ersten ungespielten Song. Ist er schon drin, bleibt alles, wie es ist.
 */
fun Bestand.einfuegenNach(setId: Long, songId: Long, nachId: Long?): Bestand = setAendern(setId) { s ->
    if (s.eintraege.any { it.songId == songId }) return@setAendern s
    val l = s.eintraege.toMutableList()
    val anker = l.indexOfFirst { it.songId == nachId }
    val idx = if (anker >= 0) anker + 1 else l.indexOfFirst { !it.gespielt }.let { if (it < 0) l.size else it }
    l.add(idx, Eintrag(songId, ende = s.ueblichesEnde()))
    s.copy(eintraege = l)
}

/**
 * Wischen nach links (Jans Wunsch 15.11., wie in der alten App): [songId] rückt ganz ans Ende vom
 * Set und gilt wieder als offen. Steht er noch nicht im Set (Wischen im Archiv), wird er hinten
 * aufgenommen.
 */
fun Bestand.ansEnde(setId: Long, songId: Long): Bestand = setAendern(setId) { s ->
    val e = (s.eintraege.firstOrNull { it.songId == songId } ?: Eintrag(songId, ende = s.ueblichesEnde())).copy(gespielt = false)
    s.copy(eintraege = s.eintraege.filterNot { it.songId == songId } + e)
}
