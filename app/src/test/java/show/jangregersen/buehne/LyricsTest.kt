package show.jangregersen.buehne

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import show.jangregersen.buehne.lyrics.Abschnitte
import show.jangregersen.buehne.lyrics.Erkennung
import show.jangregersen.buehne.lyrics.Zeitplan
import show.jangregersen.buehne.lyrics.Teil
import show.jangregersen.buehne.lyrics.Ablauf

/** Erfundene Liedtexte, nur zum Prüfen der Logik. */
class LyricsTest {

    private val strophe1 = "Coffee in a paper cup\nthe station clock is slow"
    private val strophe1b = "I counted every light\nand every one said go"
    private val refrain = "Take the last train home\ndon't wait for morning\ntake the last train home"
    private val strophe2 = "Platform four is empty\nthe rain is coming down"
    private val strophe2b = "I hear the whistle calling\nall across the town"
    private val vor = "And I know, I know\nit's time to go"
    private val bridge = "Every station looks the same\nevery whistle sounds like you"

    @Test fun lrcLesen() {
        val z = Erkennung.lrcLesen("[ar:Wer]\n[00:12.50] Eins\n[00:15.00]\n[01:02.25] Zwei\n")
        assertEquals(2, z.size)
        assertEquals(12_500L, z[0].ms); assertEquals("Eins", z[0].text)
        assertEquals(62_250L, z[1].ms)
    }

    @Test fun erkenntRefrainVorrefrainBridge() {
        val plain = listOf(strophe1, strophe1b, vor, refrain, strophe2, strophe2b, vor, refrain, bridge, refrain).joinToString("\n\n")
        val e = Erkennung.aufbereiten(plain, null)!!
        val namen = Abschnitte.zerlegen(e.text).map { it.name }
        // Kurze Strophen-Stücke direkt hintereinander werden zu einer Strophe.
        assertEquals(listOf("Strophe 1", "Vorrefrain", "Refrain", "Strophe 2", "Vorrefrain", "Refrain", "Bridge", "Refrain"), namen)
        assertEquals(4, Abschnitte.zerlegen(e.text)[0].zeilen.size)
        assertTrue(e.zeiten.all { it == -1L })
    }

    @Test fun ohneWiederholungNurStrophen() {
        val e = Erkennung.aufbereiten(listOf(strophe1 + "\n" + strophe1b, strophe2 + "\n" + strophe2b, bridge + "\n" + vor).joinToString("\n\n"), null)!!
        assertEquals(listOf("Strophe 1", "Strophe 2", "Strophe 3"), Abschnitte.zerlegen(e.text).map { it.name })
    }

    @Test fun zeitenWerdenZugeordnet() {
        val plain = listOf(strophe1, refrain, strophe2, refrain).joinToString("\n\n")
        val zeilen = plain.lines().filter { it.isNotBlank() }
        val lrc = zeilen.mapIndexed { i, z -> "[00:%02d.00] %s".format(10 + i * 3, z) }.joinToString("\n")
        val e = Erkennung.aufbereiten(plain, lrc)!!
        assertEquals(zeilen.size, e.zeiten.size)
        assertEquals(10_000L, e.zeiten[0])
        assertEquals(10_000L + 3_000L * (zeilen.size - 1), e.zeiten.last())
    }

    @Test fun ohneAbsaetzeTrenntAnPausen() {
        // Kein Absatz im Text, aber lange Pausen in den Zeitmarken.
        val lrc = "[00:10.00] a eins\n[00:13.00] a zwei\n[00:30.00] b eins\n[00:33.00] b zwei\n[00:50.00] a eins\n[00:53.00] a zwei"
        val e = Erkennung.aufbereiten("a eins\na zwei\nb eins\nb zwei\na eins\na zwei", lrc)!!
        assertEquals(listOf("Refrain", "Strophe 1", "Refrain"), Abschnitte.zerlegen(e.text).map { it.name })
        assertEquals(listOf(10_000L, 13_000L, 30_000L, 33_000L, 50_000L, 53_000L), e.zeiten)
    }

    @Test fun leererTextGibtNichts() {
        assertNull(Erkennung.aufbereiten("", null))
        assertNull(Erkennung.aufbereiten(null, null))
    }

    @Test fun vorschlagNurBeiPassenderLaenge() {
        val ab = Abschnitte.zerlegen("[Strophe 1]\na\nb\n\n[Refrain]\nc\nd")
        val zeiten = listOf(10_000L, 14_000L, 30_000L, 34_000L)
        assertEquals(listOf(Teil(0, 10_000L), Teil(1, 30_000L)), Zeitplan.vorschlag(ab, zeiten, 200_000, 201_500))
        assertTrue(Zeitplan.vorschlag(ab, zeiten, 200_000, 210_000).isEmpty())   // andere Fassung
        assertTrue(Zeitplan.vorschlag(ab, zeiten.dropLast(1), 200_000, 200_000).isEmpty()) // Zeilenzahl passt nicht
    }

    @Test fun eigenerAblaufMitWiederholungUndAuslassung() {
        // Text: Strophe 1, Refrain, Strophe 2. Jan singt: Refrain, Strophe 1, Refrain (Strophe 2 weg).
        val ab = Abschnitte.zerlegen("[Strophe 1]\na\nb\n\n[Refrain]\nc\nd\n\n[Strophe 2]\ne\nf")
        val zeiten = listOf(10_000L, 14_000L, 20_000L, 24_000L, 40_000L, 44_000L)
        val ablauf = listOf(Teil(1, 5_000), Teil(0, 15_000), Teil(1, 30_000))
        val s = Zeitplan.zeilenStarts(ab, ablauf, zeiten, 60_000)
        // Refrain im Original 20 s lang (bis Strophe 2), auf dem Track nur 10 s: gestaucht.
        assertEquals(listOf(5_000L, 7_000L), s[0])
        // Strophe 1 im Original 10 s, auf dem Track 15 s: Originaltempo.
        assertEquals(listOf(15_000L, 19_000L), s[1])
        assertEquals(listOf(30_000L, 34_000L), s[2])
        assertEquals(1 to 1, Zeitplan.aktiveZeile(ablauf, s, 20_000))
        assertEquals(1 to 1, Zeitplan.aktiveZeile(ablauf, s, 29_999))
        assertEquals(2 to 0, Zeitplan.aktiveZeile(ablauf, s, 30_000))
        assertNull(Zeitplan.aktiveZeile(ablauf, s, 4_000))
    }

    @Test fun getippteZeilenGeltenGenau() {
        val ab = Abschnitte.zerlegen("[Strophe 1]\na\nb\nc")
        val zeiten = listOf(10_000L, 14_000L, 18_000L)
        // Jan hat Zeile 1 und 2 getippt (langsamer gesungen), Zeile 3 fehlt: Abstand wie im Original.
        val s = Zeitplan.zeilenStarts(ab, listOf(Teil(0, 2_000, listOf(2_000L, 7_000L))), zeiten, 60_000)
        assertEquals(listOf(2_000L, 7_000L, 11_000L), s[0])
    }

    @Test fun nichtEingemesseneTeileHabenKeineZeiten() {
        val ab = Abschnitte.zerlegen("[Strophe 1]\na\n\n[Refrain]\nb")
        val ablauf = listOf(Teil(0, 1_000), Teil(1))
        val s = Zeitplan.zeilenStarts(ab, ablauf, emptyList(), 30_000)
        assertEquals(listOf(1_000L), s[0])
        assertNull(s[1])
        assertEquals(0, Zeitplan.aktiverTeil(ablauf, 20_000))
    }

    @Test fun ablaufLesenUndSchreiben() {
        val l = Ablauf.lesen("1:30000,0:5000:5000|8000,-1:45000,1:-1")
        assertEquals(listOf(Teil(1, 30000), Teil(0, 5000, listOf(5000L, 8000L)), Teil(-1, 45000), Teil(1)), l)
        assertEquals("1:30000,0:5000:5000|8000,-1:45000,1:-1", Ablauf.schreiben(l))
        val ab = Abschnitte.zerlegen("[A]\na\n\n[B]\nb")
        assertEquals(listOf(Teil(0), Teil(1)), Ablauf.von("", ab))
        assertEquals(listOf(Teil(1, 5)), Ablauf.von("7:1,1:5", ab))
    }

    @Test fun ablaufNachZusammenlegen() {
        val l = listOf(Teil(0, 0), Teil(1, 10), Teil(2, 20), Teil(1, 30), Teil(3, 40))
        // Abschnitt 1 an 0 gehängt: 0+1 direkt hintereinander werden ein Teil, 2→1, die spätere 1→0, 3→2.
        assertEquals(listOf(Teil(0, 0), Teil(1, 20), Teil(0, 30), Teil(2, 40)), Ablauf.nachZusammenlegen(l, 1))
    }

    @Test fun abHierOhneZeit() {
        val l = listOf(Teil(0, 0, listOf(0L)), Teil(1, 10), Teil(2, 20))
        assertEquals(listOf(Teil(0, 0, listOf(0L)), Teil(1), Teil(2)), Ablauf.abHierOhneZeit(l, 1))
    }

    @Test fun ohneZeitmarkenNachTextlaenge() {
        val ab = Abschnitte.zerlegen("[Strophe 1]\nkurz\neine viel längere Zeile hier")
        val s = Zeitplan.teilZeiten(ab, emptyList(), 0, 0L, 10_000)
        assertEquals(0L, s[0])
        assertTrue(s[1] in 1_000L..3_000L)
    }

    @Test fun instrumentalHatKeineZeilen() {
        val ab = Abschnitte.zerlegen("[Strophe 1]\na")
        val ablauf = listOf(Teil(0, 0), Teil(Ablauf.INSTRUMENTAL, 5_000))
        val s = Zeitplan.zeilenStarts(ab, ablauf, emptyList(), 10_000)
        assertEquals(1, s[0]!!.size)
        assertTrue(s[1]!!.isEmpty())
        assertEquals(1 to -1, Zeitplan.aktiveZeile(ablauf, s, 6_000))
    }
}

class AbschnitteAendernTest {
    private val text = "[Strophe 1]\na\nb\n\n[Refrain]\nc\n\n[Strophe 2]\nd\n\n[Teil 4]\ne"

    @Test fun umbenennenZaehltStrophenNeu() {
        val t = Abschnitte.umbenennen(text, 2, "Bridge")
        assertEquals(listOf("Strophe 1", "Refrain", "Bridge", "Strophe 2"), Abschnitte.zerlegen(t).map { it.name })
    }

    @Test fun mitVorigemHaengtAn() {
        val t = Abschnitte.mitVorigem(text, 1)
        val a = Abschnitte.zerlegen(t)
        assertEquals(listOf("Strophe 1", "Strophe 2", "Strophe 3"), a.map { it.name })
        assertEquals(listOf("a", "b", "c"), a[0].zeilen)
        assertEquals(text, Abschnitte.mitVorigem(text, 0)) // erster hat keinen Vorgänger
    }

    @Test fun artErkennen() {
        assertEquals(Abschnitte.Art.REFRAIN, Abschnitte.art("Refrain"))
        assertEquals(Abschnitte.Art.VORREFRAIN, Abschnitte.art("Vorrefrain"))
        assertEquals(Abschnitte.Art.BRIDGE, Abschnitte.art("Bridge"))
        assertEquals(Abschnitte.Art.STROPHE, Abschnitte.art("Strophe 2"))
        assertEquals(Abschnitte.Art.ANDERS, Abschnitte.art("Outro"))
    }

    @Test fun zeitenBleibenGueltigNachUmbenennen() {
        val zeiten = listOf(1L, 2L, 3L, 4L, 5L)
        val t = Abschnitte.mitVorigem(Abschnitte.umbenennen(text, 1, "Vorrefrain"), 3)
        assertTrue(Zeitplan.gueltig(Abschnitte.zerlegen(t), zeiten))
    }
}

class LrclibTest {
    @Test fun titelSauber() {
        assertEquals("Teenage Dirtbag", show.jangregersen.buehne.netz.Lrclib.titelSauber("Teenage Dirtbag(2)"))
        assertEquals("Song neu", show.jangregersen.buehne.netz.Lrclib.titelSauber("Song_neu"))
    }

    @Test fun anzeigeTitel() {
        assertEquals("Crazy", show.jangregersen.buehne.netz.Lrclib.anzeigeTitel("Gnarls Barkley - Crazy (Official Video) [4K Remaster]", "Gnarls Barkley"))
        assertEquals("Valerie (feat. Amy Winehouse)", show.jangregersen.buehne.netz.Lrclib.anzeigeTitel("Valerie (feat. Amy Winehouse)", "Mark Ronson"))
    }

    @Test fun ordnenBevorzugtOriginal() {
        fun t(i: Long, a: String, ti: String, d: Long, sync: Boolean = true) = show.jangregersen.buehne.netz.Lrclib.Treffer(i, a, ti, d, "x", if (sync) "[00:01.00] x" else null)
        val l = listOf(
            t(1, "Michael Franti", "Crazy", 221_000),
            t(2, "Gnarls Barkley", "Crazy", 178_000), t(3, "Gnarls Barkley", "Crazy", 178_500), t(4, "Gnarls Barkley", "Crazy (Live)", 190_000),
            t(5, "Karaoke Stars", "Crazy (Karaoke)", 244_000)
        )
        val o = show.jangregersen.buehne.netz.Lrclib.ordnen(l, "Crazy", 244_000)
        assertEquals("Gnarls Barkley", o.first().interpret)
        assertEquals("Karaoke Stars", o.last().interpret)
        assertEquals(1, o.count { it.interpret == "Gnarls Barkley" && it.titel == "Crazy" }) // doppelte zusammengefasst
    }

    @Test fun seitenWerdenGleichmaessigGeteilt() {
        val bis3 = { t: List<String> -> t.size <= 3 }
        // Passt alles: eine Seite.
        assertEquals(listOf(listOf("a", "b")), show.jangregersen.buehne.lyrics.seitenTeilen(listOf("a", "b"), bis3))
        // 4 Zeilen, 3 passen: 2 + 2 statt 3 + 1.
        assertEquals(listOf(listOf("a", "b"), listOf("c", "d")), show.jangregersen.buehne.lyrics.seitenTeilen(listOf("a", "b", "c", "d"), bis3))
        // 7 Zeilen: 3 + 2 + 2.
        assertEquals(listOf(3, 2, 2), show.jangregersen.buehne.lyrics.seitenTeilen(listOf("a", "b", "c", "d", "e", "f", "g"), bis3).map { it.size })
        // Leerzeilen an der Schnittstelle fallen weg, mittendrin bleiben sie.
        val mitLeer = show.jangregersen.buehne.lyrics.seitenTeilen(listOf("a", "", "b", "c", "", "d"), { t -> t.count { it.isNotBlank() } <= 2 })
        assertEquals(listOf(listOf("a", "", "b"), listOf("c", "", "d")), mitLeer)
        // Eine lange Zeile (zählt hier als 3) kann das gleichmäßige Teilen verhindern: Sie steht dann allein.
        val gewicht = { t: List<String> -> t.sumOf { if (it == "LANG") 3L else 1L } <= 3L }
        val lang = show.jangregersen.buehne.lyrics.seitenTeilen(listOf("a", "b", "LANG", "c"), gewicht)
        assertTrue(lang.all(gewicht))
        assertEquals(listOf("a", "b", "LANG", "c"), lang.flatten())
    }
}
