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

fun Bestand.songsHinzufuegen(setId: Long, songIds: List<Long>): Bestand = setAendern(setId) { s ->
    val schon = s.eintraege.map { it.songId }.toSet()
    s.copy(eintraege = s.eintraege + songIds.filterNot { it in schon }.map { Eintrag(it) })
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
 * Der Song, der nach [aktuellId] dran ist: erster ungespielter Eintrag hinter dem aktuellen,
 * sonst der erste ungespielte überhaupt (der aktuelle zählt nie).
 */
fun SetListe.naechsterNach(aktuellId: Long?): Eintrag? {
    val idx = eintraege.indexOfFirst { it.songId == aktuellId }
    val dahinter = if (idx >= 0) eintraege.drop(idx + 1).firstOrNull { !it.gespielt && it.songId != aktuellId } else null
    return dahinter ?: eintraege.firstOrNull { !it.gespielt && it.songId != aktuellId }
}

fun SetListe.ersterOffener(): Eintrag? = eintraege.firstOrNull { !it.gespielt }

fun SetListe.dauerMs(b: Bestand, nurOffene: Boolean = false): Long =
    eintraege.filter { !nurOffene || !it.gespielt }.sumOf { b.song(it.songId)?.dauerMs ?: 0L }
