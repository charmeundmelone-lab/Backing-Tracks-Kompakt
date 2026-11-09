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
import show.jangregersen.buehne.lyrics.Abschnitte
import show.jangregersen.buehne.lyrics.LyricsSeite
import show.jangregersen.buehne.lyrics.Seitenlauf

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

    @Test fun marken() {
        val m = mapOf(0 to 0L, 2 to 46000L, 1 to 14000L)
        assertEquals("0:0,1:14000,2:46000", Abschnitte.markenSchreiben(m))
        assertEquals(m, Abschnitte.markenLesen("0:0,1:14000,2:46000"))
    }

    @Test fun seitenlauf() {
        val s = listOf(
            LyricsSeite(0, "Intro", 1, 1, listOf("a"), 28f, 0f),
            LyricsSeite(1, "Strophe", 1, 2, listOf("b"), 28f, 0f),
            LyricsSeite(1, "Strophe", 2, 2, listOf("c"), 28f, 0.5f),
            LyricsSeite(2, "Refrain", 1, 1, listOf("d"), 28f, 0f)
        )
        val m = mapOf(0 to 0L, 1 to 10_000L, 2 to 30_000L)
        assertEquals(0, Seitenlauf.seiteFuer(s, m, 5_000, 100_000))
        assertEquals(1, Seitenlauf.seiteFuer(s, m, 12_000, 100_000))
        assertEquals(2, Seitenlauf.seiteFuer(s, m, 21_000, 100_000))
        assertEquals(3, Seitenlauf.seiteFuer(s, m, 31_000, 100_000))
        assertEquals(0, Seitenlauf.seiteFuer(s, emptyMap(), 31_000, 100_000))
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
}
