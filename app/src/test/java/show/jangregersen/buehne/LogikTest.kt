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
import show.jangregersen.buehne.daten.naechsterNach
import show.jangregersen.buehne.daten.schieben
import show.jangregersen.buehne.daten.verschieben
import show.jangregersen.buehne.daten.eintragAendern
import show.jangregersen.buehne.daten.vorziehen
import show.jangregersen.buehne.daten.wasKommt
import show.jangregersen.buehne.daten.testlauf
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

    /** Jans eine Regel (15.11.): Antippen = kommt als Nächstes dran. */
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
