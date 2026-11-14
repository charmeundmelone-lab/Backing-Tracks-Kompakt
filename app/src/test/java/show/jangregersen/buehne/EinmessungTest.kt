package show.jangregersen.buehne

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import show.jangregersen.buehne.lyrics.Ablauf
import show.jangregersen.buehne.lyrics.Abschnitte
import show.jangregersen.buehne.lyrics.Einmessung
import show.jangregersen.buehne.lyrics.LyricsSeite
import show.jangregersen.buehne.lyrics.Teil
import show.jangregersen.buehne.lyrics.Zeitplan

/** Einmessen Zeile für Zeile und der eigene Ablauf. Erfundene Texte, nur zum Prüfen der Logik. */
class EinmessungTest {
    private val text = "[Strophe 1]\na\nb\n\n[Refrain]\nc\nd\n\n[Strophe 2]\ne\nf"
    private val ab = Abschnitte.zerlegen(text)
    private val standard = Ablauf.standard(ab.size)
    private val I = Ablauf.INSTRUMENTAL

    private fun tippe(l: List<Teil>, vararg ms: Long) = ms.fold(l) { acc, t -> Einmessung.tippen(ab, acc, t) }

    @Test fun zeileFuerZeileDerReiheNach() {
        assertEquals(0 to 0, Einmessung.ziel(ab, standard))
        var l = tippe(standard, 1_000)
        assertEquals(0 to 1, Einmessung.ziel(ab, l))
        l = tippe(l, 3_000)
        assertEquals(1 to 0, Einmessung.ziel(ab, l))
        l = tippe(l, 10_000, 12_000, 20_000, 22_000)
        assertNull(Einmessung.ziel(ab, l))
        assertEquals(listOf(Teil(0, 1_000, listOf(1_000L, 3_000L)), Teil(1, 10_000, listOf(10_000L, 12_000L)), Teil(2, 20_000, listOf(20_000L, 22_000L))), l)
        // Alles getippt: ein weiterer Tipp ändert nichts.
        assertEquals(l, tippe(l, 30_000))
    }

    @Test fun doppeltippZaehltNicht() {
        val l = tippe(standard, 1_000)
        assertEquals(l, tippe(l, 1_200))
        assertEquals(0 to 2, tippe(l, 1_300).let { 0 to it[0].zeilenMs.size })
    }

    @Test fun tippWegNimmtNurDenLetztenTipp() {
        val l = tippe(standard, 1_000, 3_000, 10_000)
        val ohneRefrain = Einmessung.tippWeg(ab, l)
        assertEquals(listOf(Teil(0, 1_000, listOf(1_000L, 3_000L)), Teil(1), Teil(2)), ohneRefrain)
        assertEquals(listOf(Teil(0, 1_000, listOf(1_000L)), Teil(1), Teil(2)), Einmessung.tippWeg(ab, ohneRefrain))
        assertEquals(standard, Einmessung.tippWeg(ab, standard))
    }

    @Test fun zurueckspringenLoeschtNurSpaeteres() {
        val l = tippe(standard, 1_000, 3_000, 10_000, 12_000)
        assertEquals(listOf(Teil(0, 1_000, listOf(1_000L, 3_000L)), Teil(1, 10_000, listOf(10_000L)), Teil(2)), Einmessung.zurueck(l, 11_000))
        assertEquals(listOf(Teil(0, 1_000, listOf(1_000L, 3_000L)), Teil(1), Teil(2)), Einmessung.zurueck(l, 9_000))
        assertEquals(l, Einmessung.zurueck(l, 60_000))
    }

    @Test fun auslassenNurVorDemTeilAnfang() {
        assertTrue(Einmessung.kannAuslassen(ab, standard))
        assertEquals(listOf(Teil(1), Teil(2)), Einmessung.auslassen(ab, standard))
        // Mitten in Strophe 1 (eine von zwei Zeilen getippt): nichts auszulassen.
        val mitten = tippe(standard, 1_000)
        assertFalse(Einmessung.kannAuslassen(ab, mitten))
        assertEquals(mitten, Einmessung.auslassen(ab, mitten))
        // Strophe 1 fertig: der Refrain kann raus, die Zeiten von Strophe 1 bleiben.
        val fertig = tippe(mitten, 3_000)
        assertEquals(listOf(fertig[0], Teil(2)), Einmessung.auslassen(ab, fertig))
        // Ein einziger Teil bleibt immer stehen.
        assertFalse(Einmessung.kannAuslassen(ab, listOf(Teil(0))))
    }

    @Test fun andererTeilWirdEingefuegt() {
        // Nach Strophe 1 singt Jan gleich Strophe 2, der Refrain kommt später.
        val l = Einmessung.teilBeginnt(ab, tippe(standard, 1_000, 3_000), 2, 8_000)
        assertEquals(listOf(Teil(0, 1_000, listOf(1_000L, 3_000L)), Teil(2, 8_000, listOf(8_000L)), Teil(1), Teil(2)), l)
        assertEquals(1 to 1, Einmessung.ziel(ab, l))
    }

    @Test fun andererTeilNimmtDenNaechstenStattDoppelt() {
        // Letzte Zeile von Strophe 1 nicht getippt, der Refrain beginnt: kein zweiter Refrain im Ablauf.
        val l = Einmessung.teilBeginnt(ab, tippe(standard, 1_000), 1, 8_000)
        assertEquals(listOf(Teil(0, 1_000, listOf(1_000L)), Teil(1, 8_000, listOf(8_000L)), Teil(2)), l)
    }

    @Test fun instrumentalHatKeineZeilenZumTippen() {
        val l = Einmessung.teilBeginnt(ab, tippe(standard, 1_000, 3_000), I, 6_000)
        assertEquals(Teil(I, 6_000), l[1])
        // Danach geht es mit dem Refrain weiter, nicht mit einer "Zeile" des Instrumentals.
        assertEquals(2 to 0, Einmessung.ziel(ab, l))
        // Im Ablauf geplantes Instrumental: ein Tipp misst nur seinen Anfang.
        val geplant = tippe(listOf(Teil(I), Teil(0)), 500)
        assertEquals(listOf(Teil(I, 500), Teil(0)), geplant)
        assertEquals(1 to 0, Einmessung.ziel(ab, geplant))
    }

    @Test fun weiterEinmessenLoeschtNichts() {
        val l = tippe(standard, 1_000, 3_000, 10_000)
        assertEquals(5_000L, Einmessung.weiterAb(ab, l))
        assertEquals(0L, Einmessung.weiterAb(ab, tippe(standard, 1_000)))
        assertEquals(0L, Einmessung.weiterAb(ab, standard))
    }

    @Test fun alteDatenKennenNurDenTeilAnfang() {
        val alt = listOf(Teil(0, 5_000), Teil(1), Teil(2))
        assertEquals(listOf(5_000L), Einmessung.getippt(ab, alt[0]))
        assertEquals(0 to 1, Einmessung.ziel(ab, alt))
    }

    @Test fun jansAblaufWeichtVomOriginalAb() {
        // Original: Strophe 1, Refrain, Strophe 2. Jan singt: Refrain, Strophe 1, Solo, Refrain. Strophe 2 fällt weg.
        var l = Einmessung.teilBeginnt(ab, standard, 1, 2_000)     // Refrain zuerst
        l = tippe(l, 4_000)                                          // Refrain Zeile 2
        l = tippe(l, 9_000, 11_500)                                  // Strophe 1 (steht als Nächstes da)
        l = Einmessung.teilBeginnt(ab, l, I, 15_000)                 // Solo
        l = tippe(l, 20_000, 22_000)                                 // Refrain (steht als Nächstes da)
        l = Einmessung.auslassen(ab, l)                              // Strophe 2 raus
        assertEquals(listOf(1, 0, I, 1), l.map { it.abschnitt })
        assertNull(Einmessung.ziel(ab, l))
        // Speichern und wieder lesen ändert nichts.
        assertEquals(l, Ablauf.von(Ablauf.schreiben(l), ab))

        val s = Zeitplan.zeilenStarts(ab, l, emptyList(), 30_000)
        assertEquals(listOf(listOf(2_000L, 4_000L), listOf(9_000L, 11_500L), emptyList(), listOf(20_000L, 22_000L)), s)
        assertNull(Zeitplan.aktiveZeile(l, s, 1_999))               // Vorspiel
        assertEquals(0 to 1, Zeitplan.aktiveZeile(l, s, 8_999))     // Refrain, Zeile 2
        assertEquals(1 to 0, Zeitplan.aktiveZeile(l, s, 9_000))     // Strophe 1, genau am Tipp
        assertEquals(2 to -1, Zeitplan.aktiveZeile(l, s, 16_000))   // Solo: keine Zeile
        assertEquals(3 to 1, Zeitplan.aktiveZeile(l, s, 29_000))    // zweiter Refrain hat eigene Zeiten
    }

    @Test fun nichtGetippteZeilenLaufenNieInDenNaechstenTeil() {
        // Strophe 1: nur Zeile 1 getippt, Zeile 2 wird geschätzt. Beginnt vorher der Refrain, gilt der Refrain.
        val l = listOf(Teil(0, 1_000, listOf(1_000L)), Teil(1, 2_000, listOf(2_000L, 5_000L)))
        val s = Zeitplan.zeilenStarts(ab, l, emptyList(), 60_000)
        assertEquals(1 to 0, Zeitplan.aktiveZeile(l, s, 2_500))
        assertEquals(1 to 1, Zeitplan.aktiveZeile(l, s, 5_000))
    }

    @Test fun buehnenSeiteFolgtDemAblauf() {
        // Ablauf: Refrain, Solo, Strophe 1 (lang, auf zwei Seiten geteilt).
        val l = listOf(Teil(1, 1_000, listOf(1_000L, 3_000L)), Teil(I, 6_000), Teil(0, 10_000, listOf(10_000L, 14_000L)))
        val seiten = listOf(
            LyricsSeite(0, "Refrain", 1, 1, listOf("c", "d"), 28f),
            LyricsSeite(1, "Instrumental", 1, 1, listOf("— instrumental —"), 28f),
            LyricsSeite(2, "Strophe 1", 1, 2, listOf("a"), 28f, ersteZeile = 0),
            LyricsSeite(2, "Strophe 1", 2, 2, listOf("b"), 28f, ersteZeile = 1)
        )
        val s = Zeitplan.zeilenStarts(ab, l, emptyList(), 30_000)
        assertEquals(0, Zeitplan.seiteFuer(seiten, l, s, 0))
        assertEquals(0, Zeitplan.seiteFuer(seiten, l, s, 5_999))
        assertEquals(1, Zeitplan.seiteFuer(seiten, l, s, 6_000))
        assertEquals(2, Zeitplan.seiteFuer(seiten, l, s, 10_000))
        assertEquals(2, Zeitplan.seiteFuer(seiten, l, s, 13_999))
        assertEquals(3, Zeitplan.seiteFuer(seiten, l, s, 14_000))
        assertEquals(3, Zeitplan.seiteFuer(seiten, l, s, 29_000))
        // Großer Vorlauf (1,5 s) nur beim Teilwechsel, mitten im Teil der kleine (0,3 s).
        assertEquals(1, Zeitplan.seiteFuer(seiten, l, s, 4_500 + 1_500, 4_500 + 300))
        assertEquals(2, Zeitplan.seiteFuer(seiten, l, s, 8_500 + 1_500, 8_500 + 300))
        assertEquals(2, Zeitplan.seiteFuer(seiten, l, s, 12_600 + 1_500, 12_600 + 300))
        assertEquals(3, Zeitplan.seiteFuer(seiten, l, s, 13_700 + 1_500, 13_700 + 300))
    }
}

/** Der Ablauf muss mitziehen, wenn sich der Text oder alte Daten ändern. */
class AblaufPflegeTest {
    private val alt = "[Strophe 1]\na\nb\n\n[Refrain]\nc\nd\n\n[Strophe 2]\ne\nf"
    private val l = listOf(Teil(1, 0, listOf(0L, 2_000L)), Teil(0, 10_000, listOf(10_000L, 12_000L)), Teil(1, 20_000, listOf(20_000L, 22_000L)), Teil(2, 30_000, listOf(30_000L)))

    private fun nach(neu: String) = Ablauf.nachTextAenderung(l, Abschnitte.zerlegen(alt), Abschnitte.zerlegen(neu))

    @Test fun tippfehlerAendertNichts() {
        assertEquals(l, nach(alt.replace("\nc\n", "\ncc\n")))
    }

    @Test fun neueZeileLoeschtNurDieZeilenTippsDiesesAbschnitts() {
        val r = nach(alt.replace("\nc\nd", "\nc\nd\nd2"))
        assertEquals(listOf(Teil(1, 0), l[1], Teil(1, 20_000), l[3]), r)
    }

    @Test fun neuerAbschnittAmEndeKommtAnsEnde() {
        val r = nach("$alt\n\n[Bridge]\ng")
        assertEquals(l + Teil(3), r)
    }

    @Test fun neuerAbschnittInDerMitteKommtHinterSeinenVorgaenger() {
        // Bridge zwischen Refrain (1) und Strophe 2: Strophe 2 wird Abschnitt 3.
        val r = nach(alt.replace("\n\n[Strophe 2]", "\n\n[Bridge]\ng\n\n[Strophe 2]"))
        // Hinter den letzten Refrain im Ablauf, ab dort ohne Zeiten.
        assertEquals(listOf(l[0], l[1], l[2], Teil(2), Teil(3)), r)
    }

    @Test fun abschnittGeteilt() {
        // Der Refrain wird in Refrain (c) und Bridge (d) geteilt.
        val r = nach(alt.replace("\nc\nd", "\nc\n\n[Bridge]\nd"))
        // Jeder Refrain im Ablauf wird zu Refrain + Bridge; ab der ersten Stelle ohne Zeiten.
        assertEquals(listOf(Teil(1), Teil(2), Teil(0), Teil(1), Teil(2), Teil(3)), r)
    }

    @Test fun abschnittGeloeschtZeitenBleiben() {
        val r = nach("[Strophe 1]\na\nb\n\n[Strophe 2]\ne\nf")
        assertEquals(listOf(l[1], Teil(1, 30_000, listOf(30_000L))), r)
    }

    @Test fun markenNachText() {
        // Standard-Ablauf (leer) folgt dem Text von selbst.
        assertEquals("", Ablauf.markenNachText("", alt, "$alt\n\n[Bridge]\ng"))
        val m = Ablauf.schreiben(l)
        assertEquals(m, Ablauf.markenNachText(m, alt, alt))
        assertEquals(Ablauf.schreiben(l + Teil(3)), Ablauf.markenNachText(m, alt, "$alt\n\n[Bridge]\ng"))
    }

    @Test fun alterStandBekommtAlleAbschnitte() {
        // Früher waren nur eingemessene Abschnitte gespeichert. Die übrigen dürfen nicht verschwinden.
        assertEquals("0:0,1:14000,2:-1,3:-1", Ablauf.ausAltemStand("0:0,1:14000", 4))
        assertEquals("", Ablauf.ausAltemStand("", 4))
        assertEquals(listOf(Teil(0, 0), Teil(1, 14_000), Teil(2), Teil(3)), Ablauf.lesen(Ablauf.ausAltemStand("0:0,1:14000", 4)))
    }

    @Test fun zusammenlegenBetrifftAlleVerweise() {
        // Abschnitt 2 an 1 gehängt: beide Refrains (1) verlieren ihre Zeilen-Tipps, Strophe 2 (2) geht im Refrain auf.
        val r = Ablauf.nachZusammenlegen(l, 2)
        assertEquals(listOf(Teil(1, 0), l[1], Teil(1, 20_000)), r)
    }
}

/** Abgleich Laptop ↔ Kompakt: Text und Reihenfolge vom Laptop, getippte Zeiten vom Kompakt. */
class AbgleichTest {
    private val text = "[Strophe 1]\na\nb\n\n[Refrain]\nc\nd\n\n[Strophe 2]\ne\nf"
    private val getippt = listOf(Teil(0, 1_000, listOf(1_000L, 3_000L)), Teil(1, 10_000, listOf(10_000L, 12_000L)), Teil(2, 20_000, listOf(20_000L)))
    private fun song(lyrics: String, l: List<Teil>?, vorl: Boolean = false) =
        show.jangregersen.buehne.daten.Song(1, "T", pfad = "p", lyrics = lyrics, marken = l?.let { Ablauf.schreiben(it) } ?: "", markenVorlaeufig = vorl)
    private val A = show.jangregersen.buehne.lyrics.Abgleich

    @Test fun weglassenBehaeltAlleAnderenZeiten() {
        assertEquals(listOf(getippt[0], getippt[2]), A.folgeSetzen(getippt, listOf(0, 2)))
        assertEquals(1, A.verliertZeiten(getippt, listOf(0, 2)))   // nur der weggelassene Teil selbst
    }

    @Test fun umstellenLoeschtAbDerStelle() {
        assertEquals(listOf(getippt[0], Teil(2), Teil(1)), A.folgeSetzen(getippt, listOf(0, 2, 1)))
        assertEquals(2, A.verliertZeiten(getippt, listOf(0, 2, 1)))
        // Hinten anfügen kostet nichts.
        assertEquals(getippt + Teil(1), A.folgeSetzen(getippt, listOf(0, 1, 2, 1)))
        assertEquals(0, A.verliertZeiten(getippt, listOf(0, 1, 2, 1)))
    }

    @Test fun laptopOhneAenderungLaesstDenKompaktInRuhe() {
        val geraet = song(text, getippt)
        // Der Laptop hat denselben Text und dieselbe Reihenfolge, aber noch den alten Stand ohne Zeiten.
        val r = A.textUndTeile(geraet, song(text, Ablauf.standard(3)))
        assertEquals(geraet, r)
    }

    @Test fun neueReihenfolgeVomLaptopBehaeltZeitenBisZurAenderung() {
        val geraet = song(text, getippt)
        val r = A.textUndTeile(geraet, song(text, listOf(Teil(0), Teil(2), Teil(1))))
        assertEquals(listOf(getippt[0], Teil(2), Teil(1)), Ablauf.lesen(r.marken))
    }

    @Test fun geaenderterTextVomLaptopGilt() {
        val geraet = song(text, getippt)
        // Am Laptop bekam der Refrain eine dritte Zeile: Strophe 1 bleibt eingemessen, ab dem Refrain neu.
        val neuerText = text.replace("\nc\nd", "\nc\nd\nd2")
        val r = A.textUndTeile(geraet, song(neuerText, Ablauf.standard(3)))
        assertEquals(neuerText, r.lyrics)
        assertEquals(listOf(getippt[0], Teil(1), Teil(2)), Ablauf.lesen(r.marken))
    }

    @Test fun zeitenAusStudioOneGeltenWoNichtsGetipptIst() {
        // Kompakt: noch nichts eingemessen. Laptop: Ablauf aus Studio One mit Zeiten (vorläufig).
        val geraet = song(text, null)
        val vomLaptop = listOf(Teil(1, 5_000), Teil(0, 20_000), Teil(1, 40_000))
        val r = A.textUndTeile(geraet, song(text, vomLaptop, vorl = true))
        assertEquals(vomLaptop, Ablauf.lesen(r.marken))
        assertTrue(r.markenVorlaeufig)
    }

    @Test fun teilOhneGesangBekommtEigenenNamen() {
        // B8: Platz 1 ist Instrumental mit Zeit. Er soll "Intro" heißen.
        val l = listOf(Teil(Ablauf.INSTRUMENTAL, 1_000), Teil(0, 9_000), Teil(1, 20_000), Teil(2, 30_000))
        val (t, neu) = A.ohneGesangBenennen(text, l, 0, " Intro ")!!
        val ab = show.jangregersen.buehne.lyrics.Abschnitte.zerlegen(t)
        // Die alten Abschnitte behalten ihre Nummern und Zeilen, "Intro" kommt leer dazu.
        assertEquals(show.jangregersen.buehne.lyrics.Abschnitte.zerlegen(text).map { it.zeilen }, ab.dropLast(1).map { it.zeilen })
        assertEquals("Intro", ab.last().name)
        assertTrue(ab.last().zeilen.isEmpty())
        assertEquals(Teil(ab.size - 1, 1_000), neu[0])
        assertEquals(l.drop(1), neu.drop(1))
        assertEquals("Intro", Ablauf.name(ab, neu[0]))
        // Zweiter Platz mit demselben Namen nutzt denselben leeren Abschnitt, legt keinen neuen an.
        val (t2, neu2) = A.ohneGesangBenennen(t, neu + Teil(Ablauf.INSTRUMENTAL, 40_000), 4, "Intro")!!
        assertEquals(t, t2)
        assertEquals(ab.size - 1, neu2[4].abschnitt)
        // Zurück auf Instrumental, Zeit bleibt.
        val (_, zurueck) = A.ohneGesangBenennen(t, neu, 0, "Instrumental")!!
        assertEquals(Teil(Ablauf.INSTRUMENTAL, 1_000), zurueck[0])
        // Gesungene Plätze lassen sich so nicht umbenennen.
        assertEquals(null, A.ohneGesangBenennen(text, l, 1, "Intro"))
    }

    @Test fun tonartUndKapoVomLaptopGelten() {
        // B22: Tabelle "Songdaten" am Laptop. Getippte Zeiten bleiben, Tonart und Kapo kommen an.
        val geraet = song(text, getippt)
        val r = A.textUndTeile(geraet, song(text, getippt).copy(tonart = "Cm", kapo = 3))
        assertEquals("Cm", r.tonart)
        assertEquals(3, r.kapo)
        assertEquals(geraet.marken, r.marken)
    }
}
