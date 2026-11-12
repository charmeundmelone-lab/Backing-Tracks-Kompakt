package show.jangregersen.buehne

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import show.jangregersen.buehne.lyrics.Abschnitte
import show.jangregersen.buehne.lyrics.Erkennung
import show.jangregersen.buehne.lyrics.Zeitplan

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

    @Test fun markenVorschlagNurBeiPassenderLaenge() {
        val text = "[Strophe 1]\na\nb\n\n[Refrain]\nc\nd"
        val ab = Abschnitte.zerlegen(text)
        val zeiten = listOf(10_000L, 14_000L, 30_000L, 34_000L)
        assertEquals(mapOf(0 to 10_000L, 1 to 30_000L), Zeitplan.markenVorschlag(ab, zeiten, 200_000, 201_500))
        assertTrue(Zeitplan.markenVorschlag(ab, zeiten, 200_000, 210_000).isEmpty())   // andere Fassung
        assertTrue(Zeitplan.markenVorschlag(ab, zeiten.dropLast(1), 200_000, 200_000).isEmpty()) // Zeilenzahl passt nicht
    }

    @Test fun zeilenWerdenAufDenTrackGestreckt() {
        val ab = Abschnitte.zerlegen("[Strophe 1]\na\nb\n\n[Refrain]\nc\nd")
        val zeiten = listOf(10_000L, 15_000L, 20_000L, 25_000L)
        // Jan hat Strophe bei 12 s und Refrain bei 32 s eingemessen: Strophe dauert 20 statt 10 s.
        val s = Zeitplan.zeilenStarts(ab, mapOf(0 to 12_000L, 1 to 32_000L), zeiten, 60_000)
        assertEquals(listOf(12_000L, 22_000L), s[0])
        assertEquals(32_000L, s[1]!![0])
        assertEquals(0 to 1, Zeitplan.aktiveZeile(s, 25_000))
        assertEquals(1 to 0, Zeitplan.aktiveZeile(s, 32_000))
        assertNull(Zeitplan.aktiveZeile(s, 5_000))
        assertEquals(1 to 0, Zeitplan.naechsteZeile(s, 25_000))
    }

    @Test fun ohneZeitmarkenNachTextlaenge() {
        val ab = Abschnitte.zerlegen("[Strophe 1]\nkurz\neine viel längere Zeile hier")
        val s = Zeitplan.zeilenStarts(ab, mapOf(0 to 0L), emptyList(), 10_000)
        assertEquals(0L, s[0]!![0])
        assertTrue(s[0]!![1] in 1_000L..3_000L)
    }

    @Test fun abschnittOhneMarkeHatKeineZeiten() {
        val ab = Abschnitte.zerlegen("[Strophe 1]\na\n\n[Refrain]\nb")
        val s = Zeitplan.zeilenStarts(ab, mapOf(1 to 5_000L), emptyList(), 10_000)
        assertNull(s[0])
        assertEquals(listOf(5_000L), s[1])
    }
}
