package show.jangregersen.buehne.lyrics

import show.jangregersen.buehne.daten.Bestand
import show.jangregersen.buehne.daten.Song

/**
 * Abgleich Laptop ↔ Kompakt für Text und Teile. Aufteilung: Text, Etiketten und Reihenfolge der
 * Teile entstehen am Laptop; getippte Zeiten entstehen am Kompakt (Einmessen). Beide stehen im
 * selben Feld `marken`, deshalb wird hier zusammengeführt statt überschrieben.
 */
object Abgleich {
    /**
     * Neue Reihenfolge der Teile ([folge] = Abschnitt-Nummern). Zeiten bleiben, wo sie noch
     * stimmen: Wurden nur Teile weggelassen, behalten alle übrigen ihre Zeiten (der Track ändert
     * sich nicht). Sonst gilt: bis zur ersten geänderten Stelle bleiben sie, dahinter nicht.
     */
    fun folgeSetzen(alt: List<Teil>, folge: List<Int>): List<Teil> {
        val rest = alt.toMutableList()
        val nurWeg = folge.map { a -> val i = rest.indexOfFirst { it.abschnitt == a }; if (i < 0) null else { val t = rest[i]; repeat(i + 1) { rest.removeAt(0) }; t } }
        if (nurWeg.none { it == null }) return nurWeg.filterNotNull()
        var gleich = 0
        while (gleich < minOf(alt.size, folge.size) && alt[gleich].abschnitt == folge[gleich]) gleich++
        return folge.mapIndexed { i, a -> if (i < gleich) alt[i] else Teil(a) }
    }

    /** Wie viele eingemessene Teile verlören bei [folgeSetzen] ihre Zeiten? (Für die Rückfrage.) */
    fun verliertZeiten(alt: List<Teil>, folge: List<Int>): Int {
        val neu = folgeSetzen(alt, folge)
        val bleibt = neu.count { it.eingemessen }
        // Weggelassene Teile zählen nicht als Verlust der anderen, wohl aber ihre eigenen Zeiten.
        return alt.count { it.eingemessen } - bleibt
    }

    /**
     * Der Stand vom Laptop ([laptop]) kommt auf den Stand vom Kompakt ([geraet]): Text, Etiketten,
     * Zeitmarken des Originals und die Reihenfolge der Teile vom Laptop; die getippten Zeiten vom
     * Kompakt, soweit Teil und Text dort noch dieselben sind. Alles andere (Titel, Ansagen, Mixer …)
     * bleibt, wie es auf dem Kompakt ist.
     */
    fun textUndTeile(geraet: Song, laptop: Song): Song {
        val abG = Abschnitte.zerlegen(geraet.lyrics)
        val abL = Abschnitte.zerlegen(laptop.lyrics)
        val g = Ablauf.von(geraet.marken, abG)
        val l = Ablauf.von(laptop.marken, abL)
        fun gleicherText(x: Teil, y: Teil) = x.abschnitt == y.abschnitt &&
            (x.abschnitt == Ablauf.INSTRUMENTAL || abG.getOrNull(x.abschnitt)?.zeilen == abL.getOrNull(y.abschnitt)?.zeilen)
        var gleich = 0
        while (gleich < minOf(g.size, l.size) && gleicherText(g[gleich], l[gleich])) gleich++
        // Bis hierher sind Teil und Text gleich: Jans getippte Zeiten gelten weiter. Dahinter gilt der Laptop.
        val neu = l.mapIndexed { i, t -> if (i < gleich && g[i].eingemessen) g[i] else t }
        val unveraendert = gleich == l.size && gleich == g.size
        return geraet.copy(
            // Tonart und Kapo kommen seit B22 auch vom Laptop (Tabelle "Songdaten").
            tonart = laptop.tonart,
            kapo = laptop.kapo,
            lyrics = laptop.lyrics,
            zeilenZeiten = laptop.zeilenZeiten,
            originalMs = laptop.originalMs,
            interpret = geraet.interpret.ifBlank { laptop.interpret },
            marken = if (unveraendert) geraet.marken else Ablauf.schreiben(neu),
            markenVorlaeufig = if (unveraendert) geraet.markenVorlaeufig else laptop.markenVorlaeufig && Ablauf.hatZeiten(neu)
        )
    }

    /**
     * Songs vom Laptop ([laptop], aus `eingang.json`) in den Bestand des Kompakts übernehmen.
     * Ergebnis: neuer Bestand und die Titel der Songs, die sich dadurch geändert haben. Songs, die
     * es auf dem Kompakt nicht mehr gibt, fallen weg.
     */
    fun eingang(b: Bestand, laptop: List<Song>): Pair<Bestand, List<String>> {
        var neu = b
        val titel = mutableListOf<String>()
        for (l in laptop) {
            val g = neu.song(l.id) ?: continue
            val m = textUndTeile(g, l)
            if (m != g) { neu = neu.copy(songs = neu.songs.map { if (it.id == l.id) m else it }); titel += m.titel }
        }
        return neu to titel
    }

    /** Ein Teil ohne Gesang: Instrumental oder ein Textabschnitt ganz ohne Zeilen (z. B. "[Intro]"). */
    fun ohneGesang(abschnitte: List<Abschnitt>, a: Int): Boolean =
        a == Ablauf.INSTRUMENTAL || abschnitte.getOrNull(a)?.zeilen?.none { it.isNotBlank() } == true

    /**
     * B8: Der Platz [pos] (ohne Gesang) bekommt den Namen [name], damit die Bühne "Intro", "Solo"
     * oder "Outro" zeigt statt "Instrumental". Weg: ein leerer Textabschnitt "[name]" (wird hinten
     * angelegt, falls es ihn noch nicht gibt; vorhandene Nummern bleiben so gleich). Die Zeit des
     * Platzes bleibt. Leerer Name oder "Instrumental" = zurück auf Instrumental.
     * Ergebnis: neuer Text und neuer Ablauf, oder null, wenn der Platz gesungen wird.
     */
    fun ohneGesangBenennen(text: String, ablauf: List<Teil>, pos: Int, name: String): Pair<String, List<Teil>>? {
        val ab = Abschnitte.zerlegen(text)
        val t = ablauf.getOrNull(pos) ?: return null
        if (!ohneGesang(ab, t.abschnitt)) return null
        val n = name.trim()
        if (n.isEmpty() || n.equals("Instrumental", ignoreCase = true)) {
            return text to ablauf.mapIndexed { i, x -> if (i == pos) x.copy(abschnitt = Ablauf.INSTRUMENTAL, zeilenMs = emptyList()) else x }
        }
        var idx = ab.indexOfFirst { it.name == n && it.zeilen.none { z -> z.isNotBlank() } }
        var neuerText = text
        if (idx < 0) {
            neuerText = Abschnitte.zusammensetzen(ab + Abschnitt(n, emptyList()))
            idx = ab.size
        }
        return neuerText to ablauf.mapIndexed { i, x -> if (i == pos) x.copy(abschnitt = idx, zeilenMs = emptyList()) else x }
    }
}
