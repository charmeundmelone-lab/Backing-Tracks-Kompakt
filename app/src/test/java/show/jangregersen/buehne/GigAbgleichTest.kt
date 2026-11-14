package show.jangregersen.buehne

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import show.jangregersen.buehne.daten.Bestand
import show.jangregersen.buehne.daten.Ende
import show.jangregersen.buehne.daten.Eintrag
import show.jangregersen.buehne.daten.Gig
import show.jangregersen.buehne.daten.GigAbgleich
import show.jangregersen.buehne.daten.GigAbgleich.Aenderung
import show.jangregersen.buehne.daten.SetListe
import show.jangregersen.buehne.daten.Song

/** B11: Gigs vom Laptop, der Mudita wird geschützt. */
class GigAbgleichTest {
    private val songs = (1L..5L).map { Song(it, "S$it", pfad = "p$it") }
    private val gig = Gig(10, "Bar", 20000, listOf(SetListe(11, "Set 1", listOf(Eintrag(1, gespielt = true), Eintrag(2)))))
    private val mudita = Bestand(songs, listOf(gig), naechsteId = 20)
    private val G = GigAbgleich

    @Test fun gespieltZaehltNichtAlsAenderung() {
        val gespielt = gig.copy(sets = listOf(gig.sets[0].copy(eintraege = gig.sets[0].eintraege.map { it.copy(gespielt = true) })))
        assertEquals(G.abdruck(gig), G.abdruck(gespielt))
        assertTrue(G.abdruck(gig) != G.abdruck(gig.copy(name = "Bar 2")))
    }

    @Test fun unveraenderterGigWirdErsetztHakenBleiben() {
        val laptop = gig.copy(name = "Bar Eriksson", sets = listOf(gig.sets[0].copy(eintraege = listOf(Eintrag(1), Eintrag(3, ende = Ende.WEITER), Eintrag(2)))))
        val r = G.uebernehmen(mudita, listOf(Aenderung(10, laptop, G.abdruck(gig))))
        val g = r.bestand.gig(10)!!
        assertEquals("Bar Eriksson", g.name)
        assertEquals(listOf(1L, 3L, 2L), g.sets[0].eintraege.map { it.songId })
        // Song 1 war am Mudita schon gespielt: der Haken bleibt.
        assertEquals(listOf(true, false, false), g.sets[0].eintraege.map { it.gespielt })
        assertEquals(Ende.WEITER, g.sets[0].eintraege[1].ende)
        assertTrue(r.abgelehnt.isEmpty())
    }

    @Test fun amMuditaGeaendertBleibtDerMuditaStand() {
        val amMudita = mudita.copy(gigs = listOf(gig.copy(name = "Bar (am Mudita umbenannt)")))
        val r = G.uebernehmen(amMudita, listOf(Aenderung(10, gig.copy(name = "Laptop"), G.abdruck(gig))))
        assertEquals("Bar (am Mudita umbenannt)", r.bestand.gig(10)!!.name)
        assertEquals(1, r.abgelehnt.size)
        assertTrue(r.abgelehnt[0].contains("am Mudita geändert"))
    }

    @Test fun neuerGigBekommtEchteNummern() {
        val neu = Gig(-1, "Hochzeit", 0, listOf(SetListe(-2, "Set 1", listOf(Eintrag(4), Eintrag(99), Eintrag(4)))))
        val r = G.uebernehmen(mudita, listOf(Aenderung(-1, neu, null)))
        val g = r.bestand.gigs.last()
        assertEquals("Hochzeit", g.name)
        assertTrue(g.id >= 20 && g.sets[0].id >= 20 && g.id != g.sets[0].id)
        // Unbekannter Song 99 und die Doppelung fallen weg.
        assertEquals(listOf(4L), g.sets[0].eintraege.map { it.songId })
        assertTrue(r.bestand.naechsteId > maxOf(g.id, g.sets[0].id))
    }

    @Test fun loeschenNurWennUnveraendert() {
        val r = G.uebernehmen(mudita, listOf(Aenderung(10, null, G.abdruck(gig))))
        assertNull(r.bestand.gig(10))
        val amMudita = mudita.copy(gigs = listOf(gig.copy(datum = 1)))
        val r2 = G.uebernehmen(amMudita, listOf(Aenderung(10, null, G.abdruck(gig))))
        assertEquals(1L, r2.bestand.gig(10)!!.datum)
        assertEquals(1, r2.abgelehnt.size)
    }

}
