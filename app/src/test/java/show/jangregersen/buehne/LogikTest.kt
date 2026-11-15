package show.jangregersen.buehne

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import show.jangregersen.buehne.audio.Import
import show.jangregersen.buehne.daten.Bestand
import show.jangregersen.buehne.daten.Eintrag
import show.jangregersen.buehne.daten.Gig
import show.jangregersen.buehne.daten.SetListe
import show.jangregersen.buehne.daten.Song
import show.jangregersen.buehne.daten.alsNaechstes
import show.jangregersen.buehne.daten.naechstePause
import show.jangregersen.buehne.daten.KEINE_PAUSE
import show.jangregersen.buehne.daten.pauseSpeichern
import show.jangregersen.buehne.daten.pauseDanach
import show.jangregersen.buehne.daten.schonImGig
import show.jangregersen.buehne.daten.wiederholungsFrage
import show.jangregersen.buehne.daten.naechsterNach
import show.jangregersen.buehne.daten.schieben
import show.jangregersen.buehne.daten.verschieben
import show.jangregersen.buehne.daten.eintragAendern
import show.jangregersen.buehne.daten.vorziehen
import show.jangregersen.buehne.daten.ansEnde
import show.jangregersen.buehne.daten.wasKommt
import show.jangregersen.buehne.daten.testlauf
import show.jangregersen.buehne.daten.setAendern
import show.jangregersen.buehne.daten.Ende
import show.jangregersen.buehne.lyrics.Abschnitte

class LogikTest {

    @Test fun dateinamen() {
        Import.nameLesen("Country Roads KeyA K0").let { assertEquals("Country Roads", it.titel); assertEquals("A", it.tonart); assertEquals(0, it.kapo) }
        Import.nameLesen("Get Lucky KeyBDorian K0").let { assertEquals("Get Lucky", it.titel); assertEquals("B dorisch", it.tonart) }
        Import.nameLesen("Shake it off KeyF#m K2").let { assertEquals("Shake it off", it.titel); assertEquals("F#m", it.tonart); assertEquals(2, it.kapo) }
        Import.nameLesen("Crazy KeyCm K3").let { assertEquals("Crazy", it.titel); assertEquals("Cm", it.tonart); assertEquals(3, it.kapo) }
        Import.nameLesen("Careless Whisper k0").let { assertEquals("Careless Whisper", it.titel); assertEquals("", it.tonart) }
        Import.nameLesen("Toto - Africa 92 bpm").let { assertEquals("Africa", it.titel); assertEquals("Toto", it.interpret); assertEquals(92, it.bpm) }
        Import.nameLesen("Dont look back in anger").let { assertEquals("Dont look back in anger", it.titel); assertEquals("", it.tonart) }
        Import.nameLesen("Grenade KeyBm K2").let { assertEquals("Bm", it.tonart) }
    }

    @Test fun abschnitteMitLabels() {
        val a = Abschnitte.zerlegen("[Verse 1]\nZeile eins\nZeile zwei\n\n[Chorus]\nRefrain\n\n[Instrumental]\n\n[Bridge]\nBrücke")
        assertEquals(listOf("Strophe 1", "Refrain", "Instrumental", "Bridge"), a.map { it.name })
        assertEquals(listOf("Zeile eins", "Zeile zwei"), a[0].zeilen)
        assertEquals(0, a[2].zeilen.size)
    }

    @Test fun abschnitteOhneLabels() {
        val a = Abschnitte.zerlegen("eins\nzwei\n\n\ndrei\n\nvier")
        assertEquals(listOf("Teil 1", "Teil 2", "Teil 3"), a.map { it.name })
        assertEquals(listOf("eins", "zwei"), a[0].zeilen)
    }

    private fun bestand(): Bestand {
        val songs = (1L..5L).map { Song(it, "S$it", pfad = "p$it") }
        val set = SetListe(100, "Set", (1L..5L).map { Eintrag(it) })
        return Bestand(songs, listOf(Gig(50, "Gig", 0, listOf(set))), 200)
    }

    @Test fun naechster() {
        var b = bestand()
        assertEquals(2L, b.set(100)!!.naechsterNach(1)!!.songId)
        b = b.copy(gigs = b.gigs.map { g -> g.copy(sets = g.sets.map { s -> s.copy(eintraege = s.eintraege.map { if (it.songId <= 3) it.copy(gespielt = true) else it }) }) })
        assertEquals(4L, b.set(100)!!.naechsterNach(3)!!.songId)
        assertEquals(5L, b.set(100)!!.naechsterNach(4)!!.songId)
        assertNull(b.set(100)!!.naechsterNach(5).let { if (it?.songId == 4L) null else it }?.takeIf { false })
    }

    @Test fun verschiebenUndAlsNaechstes() {
        var b = bestand()
        b = b.verschieben(100, 5, 2)
        assertEquals(listOf(1L, 5L, 2L, 3L, 4L), b.set(100)!!.eintraege.map { it.songId })
        b = b.verschieben(100, 1, null)
        assertEquals(listOf(5L, 2L, 3L, 4L, 1L), b.set(100)!!.eintraege.map { it.songId })
        b = b.schieben(100, 4, -1)
        assertEquals(listOf(5L, 2L, 4L, 3L, 1L), b.set(100)!!.eintraege.map { it.songId })
        b = bestand().alsNaechstes(100, 5, 2)
        assertEquals(listOf(1L, 2L, 5L, 3L, 4L), b.set(100)!!.eintraege.map { it.songId })
        assertEquals(5L, b.set(100)!!.naechsterNach(2)!!.songId)
    }

    private fun ids(b: Bestand) = b.set(100)!!.eintraege.map { it.songId }

    /** Jans Wunsch 15.11.: Pausen 0 s / 10 s / 30 s / 1 min / 2 min, ohne Eintrag 10 s. */
    @Test fun pausenUmschalten() {
        assertEquals(10, Song(1, "A", pfad = "p").pauseDanach)
        assertEquals(60, Song(1, "A", pfad = "p", pauseSek = 60).pauseDanach)
        assertEquals(0, Song(1, "A", pfad = "p", pauseSek = KEINE_PAUSE).pauseDanach)
        assertEquals(KEINE_PAUSE, pauseSpeichern(0))
        assertEquals(30, pauseSpeichern(30))
        assertEquals(listOf(30, 60, 120, 0, 10), listOf(10, 30, 60, 120, 0).map { naechstePause(it) })
        assertEquals(60, naechstePause(45))
    }

    /** Jans Wunsch 15.11.: im Gig keine Wiederholung ohne Rückfrage. */
    @Test fun wiederholungImGig() {
        val songs = (1L..6L).map { Song(it, "S$it", pfad = "p$it") }
        val set1 = SetListe(100, "Set 1", listOf(Eintrag(1, gespielt = true), Eintrag(2)))
        val set2 = SetListe(101, "Set 2", listOf(Eintrag(3), Eintrag(4)))
        val b = Bestand(songs, listOf(Gig(50, "Gig", 0, listOf(set1, set2))), 200)
        assertEquals(setOf(1L, 2L, 3L, 4L), b.schonImGig(101).keys)
        assertEquals("wurde heute schon gespielt (Set 1)", b.wiederholungsFrage(101, 1))
        assertEquals("steht schon in Set 1", b.wiederholungsFrage(101, 2))
        assertNull(b.wiederholungsFrage(101, 3)) // offen im selben Set: nur nach vorn holen
        assertNull(b.wiederholungsFrage(101, 6)) // noch nirgends
        assertEquals("wurde heute schon gespielt (Set 1)", b.wiederholungsFrage(100, 1))
    }

    /** Jans eine Regel (15.11.): Antippen = kommt als Nächstes dran. */
    /** Wischen nach links: ganz ans Ende, gespielt wird wieder offen, fremde Songs kommen hinten dazu. */
    @Test fun ansEndeWischen() {
        var b = bestand().ansEnde(100, 3)
        assertEquals(listOf(1L, 2L, 4L, 5L, 3L), ids(b))
        assertEquals(3L, b.eintragAendern(100, 1) { it.copy(gespielt = true) }.set(100)!!.wasKommt(2).last().songId)
        b = b.eintragAendern(100, 1) { it.copy(gespielt = true) }.ansEnde(100, 1)
        assertEquals(listOf(2L, 4L, 5L, 3L, 1L), ids(b))
        assertEquals(false, b.set(100)!!.eintraege.last().gespielt)
        b = b.copy(songs = b.songs + Song(9, "Wunsch", pfad = "p9")).ansEnde(100, 9)
        assertEquals(9L, ids(b).last())
    }

    @Test fun vorziehenWaehrendSongLaeuft() {
        // Song 2 läuft: der angetippte (5) kommt direkt danach.
        val b = bestand().vorziehen(100, 5, 2, laeuft = true)
        assertEquals(listOf(1L, 2L, 5L, 3L, 4L), ids(b))
        assertEquals(5L, b.set(100)!!.naechsterNach(2)!!.songId)
    }

    @Test fun vorziehenWennNurBereit() {
        // Song 2 liegt nur bereit (oder wartet in der Pause): der angetippte kommt davor und ist selbst dran.
        val b = bestand().vorziehen(100, 5, 2, laeuft = false)
        assertEquals(listOf(1L, 5L, 2L, 3L, 4L), ids(b))
        assertEquals(2L, b.set(100)!!.naechsterNach(5)!!.songId)
    }

    @Test fun vorziehenNimmtFremdenSongAufUndHoltGespieltenZurueck() {
        var b = bestand().copy(songs = bestand().songs + Song(9, "Wunsch", pfad = "p9"))
        b = b.vorziehen(100, 9, 3, laeuft = true)
        assertEquals(listOf(1L, 2L, 3L, 9L, 4L, 5L), ids(b))
        b = b.eintragAendern(100, 1) { it.copy(gespielt = true) }.vorziehen(100, 1, 3, laeuft = true)
        assertEquals(listOf(2L, 3L, 1L, 9L, 4L, 5L), ids(b))
        assertEquals(false, b.set(100)!!.eintraege.first { it.songId == 1L }.gespielt)
        // Der laufende Song selbst ändert nichts.
        assertEquals(ids(b), ids(b.vorziehen(100, 3, 3, laeuft = true)))
    }

    @Test fun wunschsongUebernimmtAutomatikDesSets() {
        // Set mit Show-Automatik (letzter Song hält an): ein Wunschsong darf die Automatik nicht stoppen.
        var b = bestand().setAendern(100) { s -> s.copy(eintraege = s.eintraege.mapIndexed { i, e -> e.copy(ende = if (i < 4) Ende.WEITER else Ende.ANHALTEN) }) }
        b = b.copy(songs = b.songs + Song(9, "Wunsch", pfad = "p9")).vorziehen(100, 9, 2, laeuft = true)
        assertEquals(Ende.WEITER, b.set(100)!!.eintraege.first { it.songId == 9L }.ende)
        // Set ohne Automatik: neuer Song hält an wie alle.
        val c = bestand().copy(songs = bestand().songs + Song(9, "Wunsch", pfad = "p9")).vorziehen(100, 9, 2, laeuft = true)
        assertEquals(Ende.ANHALTEN, c.set(100)!!.eintraege.first { it.songId == 9L }.ende)
    }

    @Test fun testlaufSpielwiese() {
        val songs = listOf(Song(1, "Lang", pfad = "a", dauerMs = 200_000), Song(2, "Test Eins", pfad = "b", dauerMs = 8000, ansageVorher = "x"),
            Song(3, "Test Zwei", pfad = "c", dauerMs = 8000), Song(4, "Test Drei", pfad = "d", dauerMs = 8000))
        val b = Bestand(songs, listOf(Gig(50, "Echter Gig", 0, listOf(SetListe(60, "S", listOf(Eintrag(1)))))), 100).testlauf(20000)
        assertEquals(listOf("Testlauf"), b.gigs.map { it.name })
        assertEquals(20000L, b.gigs[0].datum)
        assertEquals(listOf(2L, 3L, 4L), b.gigs[0].sets[0].eintraege.map { it.songId })
        assertEquals(listOf(4L, 2L), b.gigs[0].sets[1].eintraege.map { it.songId })
        assertEquals(4, b.songs.size)
        assertEquals("", b.song(2)!!.ansageVorher)
        assertEquals(20, b.song(3)!!.pauseSek)
        assertEquals(0, b.song(1)!!.pauseSek)
    }

    @Test fun wasKommtBeginntBeimLaufendenSong() {
        var b = bestand()
        b = b.eintragAendern(100, 1) { it.copy(gespielt = true) }.eintragAendern(100, 2) { it.copy(gespielt = true) }
        // Song 3 läuft, Song 1/2 gespielt: angezeigt werden 3, 4, 5.
        assertEquals(listOf(3L, 4L, 5L), b.set(100)!!.wasKommt(3).map { it.songId })
        // Ein übersprungener Song vor dem laufenden kommt am Schluss (wie die Automatik es spielt).
        b = b.eintragAendern(100, 2) { it.copy(gespielt = false) }
        assertEquals(listOf(3L, 4L, 5L, 2L), b.set(100)!!.wasKommt(3).map { it.songId })
        // Ohne laufenden Song: alle offenen in Reihenfolge.
        assertEquals(listOf(2L, 3L, 4L, 5L), b.set(100)!!.wasKommt(null).map { it.songId })
    }
}
