package show.jangregersen.werkstatt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.json.JSONObject
import org.junit.Test
import show.jangregersen.buehne.daten.Bestand
import show.jangregersen.buehne.daten.BestandJson
import show.jangregersen.buehne.daten.Song
import show.jangregersen.buehne.lyrics.Abgleich
import show.jangregersen.buehne.lyrics.Ablauf
import show.jangregersen.buehne.lyrics.Abschnitte
import show.jangregersen.buehne.lyrics.Teil

/** Erfundene Daten im Aufbau von Jans Studio-One-Dateien. */
class WerkstattTest {
    @Test fun gigsInEingangJsonHinUndZurueck() {
        // B11: Laptop schreibt Gig-Änderungen in eingang.json, der Kompakt liest sie so wieder.
        val g = show.jangregersen.buehne.daten.Gig(10, "Bar", 20000, listOf(show.jangregersen.buehne.daten.SetListe(11, "Set 1", listOf(show.jangregersen.buehne.daten.Eintrag(1, ende = 2)))))
        val A = show.jangregersen.buehne.daten.GigAbgleich
        fun aend(id: Long, g: show.jangregersen.buehne.daten.Gig?, b: String?) = show.jangregersen.buehne.daten.GigAbgleich.Aenderung(id, g, b)
        val a = listOf(aend(10, g, A.abdruck(g)), aend(-1, show.jangregersen.buehne.daten.Gig(-1, "Neu"), null), aend(7, null, "x"))
        val text = BestandJson.eingangJson(emptyList(), a).toString()
        assertEquals(a, BestandJson.eingangGigsAus(JSONObject(text)))
        // Ohne Gigs bleibt die Datei wie bisher (ältere App-Fassungen lesen sie weiter).
        assertTrue(!BestandJson.eingangJson(emptyList()).has("gigs"))
    }

    // Tempo 0,5 s pro Viertel (120 BPM): 8 Viertel = 4 s.
    private fun xml(arranger: String, marker: String = """<MarkerEvent markerStop="0" markerType="2" timeFormat="2" name="Start"/>""") = """
        <Song><TempoMap x:id="tempoMap"><TempoMapSegment curveType="0" start="0" end="1e+200" tempo="0.5"/></TempoMap>
        <List x:id="Tracks">
          <MarkerTrack version="1"><Attributes x:id="attributes" height="28"/>$marker</MarkerTrack>
          <ArrangerTrack version="1" timeFormat="2"><Attributes x:id="attributes" height="28"/>$arranger</ArrangerTrack>
        </List></Song>"""

    @Test fun arrangerTeileMitZeit() {
        // Wie in Jans Dateien: nicht nach Zeit sortiert, der erste Teil ohne start-Angabe.
        val s = StudioOne.ausXml("x.song", xml("""
            <ArrangerEvent start="40" timeFormat="2" length="32" name="Chorus" color="FF"/>
            <ArrangerEvent timeFormat="2" length="8" name="Intro"/>
            <ArrangerEvent start="8" timeFormat="2" length="32" name="VERS"/>"""))
        assertEquals(listOf(StudioOne.S1Teil("Intro", 0, 4_000), StudioOne.S1Teil("VERS", 4_000, 16_000), StudioOne.S1Teil("Chorus", 20_000, 16_000)), s.teile)
        assertEquals("", s.hinweis)
    }

    @Test fun startMarkerVerschiebtDenAnfang() {
        // Der Export beginnt am Start-Marker bei Viertel 8: alles rückt 4 s nach vorn.
        val s = StudioOne.ausXml("x.song", xml("""<ArrangerEvent start="8" length="32" name="Vers"/><ArrangerEvent start="40" length="32" name="Chorus"/>""",
            """<MarkerEvent markerType="2" start="8" name="Start"/>"""))
        assertEquals(listOf(0L, 16_000L), s.teile.map { it.startMs })
    }

    @Test fun zeitenInSekundenStattVierteln() {
        // Manche Dateien (z. B. Country Roads) speichern Sekunden (timeFormat 0), über mehrere Zeilen verteilt.
        val s = StudioOne.ausXml("x.song", xml("""
            <ArrangerEvent timeFormat="0" length="32.5" offset="-6.5"
                           name="Instrumental" color="FF"/>
            <ArrangerEvent start="32.5" timeFormat="0" length="26"
                           name="Vers"/>"""))
        assertEquals(listOf(StudioOne.S1Teil("Instrumental", 0, 32_500), StudioOne.S1Teil("Vers", 32_500, 26_000)), s.teile)
    }

    @Test fun markerStattArranger() {
        val s = StudioOne.ausXml("x.song", xml("", """
            <MarkerEvent markerType="2" name="Start"/><MarkerEvent markerType="3" start="80" name="End"/>
            <MarkerEvent markerType="0" start="8" name="INTRO"/><MarkerEvent markerType="0" start="40" name="Vers"/>"""))
        assertEquals(listOf(StudioOne.S1Teil("INTRO", 4_000, 16_000), StudioOne.S1Teil("Vers", 20_000, 20_000)), s.teile)
        assertTrue(s.hinweis.contains("Marker"))
    }

    @Test fun namenVergleichen() {
        assertEquals("grenade", StudioOne.norm("Grenade KeyBm K2.wav"))
        assertEquals("country roads", StudioOne.norm("Country Roads KeyA K0"))
        assertEquals("careless whisper", StudioOne.norm("Careless Whisper k0"))
        assertEquals("every breath", StudioOne.norm("Every breath.song"))
    }

    private fun t(n: String, s: Long, dauer: Long = 16_000) = StudioOne.S1Teil(n, s, dauer)
    private val I = Ablauf.INSTRUMENTAL

    @Test fun vorschlagLegtDenTextDerReiheNachInJansTeile() {
        // Wie bei "Two Princes": Jan nennt fast alles "Vers", auch Refrain und Vorrefrain ("BRIDGE"),
        // Tippfehler inklusive. Der Text aus dem Internet steht in der Reihenfolge des Songs.
        val ab = Abschnitte.zerlegen("[Strophe 1]\na\n\n[Vorrefrain]\nv\n\n[Refrain]\nr\n\n[Strophe 2]\nd\n\n[Refrain]\nr2\n\n[Bridge]\ne")
        val s1 = listOf(t("Intro", 0), t("Vers", 16_000), t("BRIDGE", 32_000), t("Vers", 48_000), t("INSTR,", 64_000), t("Vers", 80_000), t("Vers", 96_000), t("OURTO", 112_000), t("EN>D", 128_000))
        val l = Vorschlag.ablauf(ab, s1)
        assertEquals(listOf(I, 0, 1, 2, I, 3, 4, 5, I), l.map { it.abschnitt })
        assertEquals(s1.map { it.startMs }, l.map { it.startMs })
    }

    @Test fun vorschlagWiederholtDenRefrainWennErNurEinmalImTextSteht() {
        val ab = Abschnitte.zerlegen("[Strophe 1]\na\n\n[Refrain]\nr\n\n[Strophe 2]\nd\n\n[Bridge]\ne")
        val s1 = listOf(t("Intro", 0), t("Vers", 16_000), t("Chorus", 32_000), t("Vers", 48_000), t("Chorus", 64_000), t("Bridge", 80_000), t("Chorus", 96_000), t("Outro", 112_000))
        assertEquals(listOf(I, 0, 1, 2, 1, 3, 1, I), Vorschlag.ablauf(ab, s1).map { it.abschnitt })
    }

    @Test fun kurzerTeilGiltAlsOhneGesang() {
        // Wie bei "There she goes": ein 5-Sekunden-"Vers" am Anfang ist nur der Einzähler.
        val ab = Abschnitte.zerlegen("[Refrain]\nr\n\n[Strophe 1]\na")
        val s1 = listOf(t("Vers", 0, 5_000), t("Intro", 5_000), t("Vers", 36_000), t("Vers", 57_000))
        assertEquals(listOf(I, I, 0, 1), Vorschlag.ablauf(ab, s1).map { it.abschnitt })
    }

    @Test fun textRuecktEinFachWeiterUndZurueck() {
        val l = listOf(Teil(0, 0, listOf(0L, 2_000L)), Teil(1, 16_000), Teil(2, 32_000), Teil(I, 48_000))
        // Fach 2 (Index 1) ist doch ohne Gesang: Text rückt nach hinten, die Zeiten bleiben an den Fächern.
        val hinten = Vorschlag.nachHinten(l, 1)
        assertEquals(listOf(Teil(0, 0, listOf(0L, 2_000L)), Teil(I, 16_000), Teil(1, 32_000), Teil(2, 48_000)), hinten)
        // Und wieder zurück.
        assertEquals(l, Vorschlag.nachVorn(hinten, 1))
        // Kein freies Fach am Ende: Der letzte Textteil hängt ohne Zeit hinten an, nichts geht verloren.
        assertEquals(listOf(Teil(I, 0), Teil(0, 16_000), Teil(1)), Vorschlag.nachHinten(listOf(Teil(0, 0), Teil(1, 16_000)), 0))
        // Nach vorn geht nur an einem leeren Fach.
        assertEquals(l, Vorschlag.nachVorn(l, 1))
    }

    @Test fun labelsOhneGesang() {
        listOf("Intro", "Intrs", "Instr.", "INST", "INSTR,", "Instrumental", "Solo", "Outro", "END", "EN>D", "ENDING").forEach { assertTrue(it, Vorschlag.ohneGesang(it)) }
        listOf("Vers", "VERS", "Chorus", "Bridge", "Tag", "OURTO").forEach { assertTrue(it, !Vorschlag.ohneGesang(it)) }
    }

    @Test fun teilTeilen() {
        val text = "[Strophe 1]\na\nb\n\nc\nd\n\n[Refrain]\ne"
        val neu = Abschnitte.zerlegen(Texte.teilen(text, 0, 2))
        assertEquals(listOf("Strophe 1", "Strophe 2", "Refrain"), neu.map { it.name })
        assertEquals(listOf("a", "b"), neu[0].zeilen)
        assertEquals(listOf("c", "d"), neu[1].zeilen)
        assertEquals(text, Texte.teilen(text, 0, 0))   // vor der ersten Zeile gibt es nichts zu teilen
        assertEquals(text, Texte.teilen(text, 0, 9))
    }

    /** Ganzer Weg: holen, am Laptop ändern, senden, Kompakt übernimmt, wieder holen. */
    @Test fun sendenUndWiederHolen() {
        val text = "[Strophe 1]\na\nb\n\n[Refrain]\nc\nd\n\n[Strophe 2]\ne\nf"
        // Auf dem Kompakt eingemessen: alle drei Teile mit Zeiten.
        val geraet = Bestand(listOf(
            Song(1, "Eins", pfad = "x", lyrics = text, marken = "0:1000:1000|2000,1:5000:5000|6000,2:9000:9000|10000"),
            Song(2, "Zwei", pfad = "y", mix = mapOf("Gitarre" to 7))
        ), naechsteId = 3)
        val ordner = java.nio.file.Files.createTempDirectory("werkstatt-test").toFile()
        try {
            val arbeit = Arbeit(ordner)
            arbeit.vomGeraet(BestandJson.alsJson(geraet).toString())
            // Am Laptop: Tippfehler in Strophe 2 verbessert.
            val neuText = text.replace("\ne\nf", "\ne\nF")
            arbeit.aendern(1) { it.copy(lyrics = neuText, marken = Ablauf.markenNachText(it.marken, it.lyrics, neuText)) }
            assertEquals(setOf(1L), arbeit.geaendert.toSet())

            // Der Kompakt liest eingang.json und führt zusammen.
            val post = BestandJson.eingangAus(JSONObject(arbeit.zumSenden()))
            val (dort, titel) = Abgleich.eingang(geraet, post)
            assertEquals(listOf("Eins"), titel)
            val eins = dort.song(1)!!
            assertEquals(neuText, eins.lyrics)
            val teile = Ablauf.von(eins.marken, Abschnitte.zerlegen(eins.lyrics))
            assertTrue("Strophe 1 und Refrain behalten ihre Zeiten", teile[0].eingemessen && teile[1].eingemessen)
            assertEquals(geraet.song(2), dort.song(2))   // anderer Song unberührt

            // Zweites Senden derselben Daten ändert nichts mehr.
            assertEquals(dort to emptyList<String>(), Abgleich.eingang(dort, post))

            // Wieder holen: Der Song gilt als angekommen.
            arbeit.vomGeraet(BestandJson.alsJson(dort).toString())
            assertTrue(arbeit.geaendert.isEmpty())
            assertEquals(eins, arbeit.bestand.song(1))
        } finally { ordner.deleteRecursively() }
    }

    /** Holen, bevor der Kompakt übernommen hat: Die Laptop-Änderung bleibt offen und erhalten. */
    @Test fun holenVorDerUebernahme() {
        val geraet = Bestand(listOf(Song(1, "Eins", pfad = "x", lyrics = "[Strophe 1]\na")), naechsteId = 2)
        val ordner = java.nio.file.Files.createTempDirectory("werkstatt-test").toFile()
        try {
            val arbeit = Arbeit(ordner)
            arbeit.vomGeraet(BestandJson.alsJson(geraet).toString())
            arbeit.aendern(1) { it.copy(lyrics = "[Strophe 1]\nA") }
            arbeit.vomGeraet(BestandJson.alsJson(geraet).toString())
            assertEquals(setOf(1L), arbeit.geaendert.toSet())
            assertEquals("[Strophe 1]\nA", arbeit.bestand.song(1)!!.lyrics)
            // Auch nach Neustart der Werkstatt noch offen.
            assertEquals(setOf(1L), Arbeit(ordner).geaendert.toSet())
        } finally { ordner.deleteRecursively() }
    }
}
