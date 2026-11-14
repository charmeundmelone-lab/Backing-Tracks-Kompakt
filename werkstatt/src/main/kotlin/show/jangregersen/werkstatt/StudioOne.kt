package show.jangregersen.werkstatt

import show.jangregersen.buehne.lyrics.Ablauf
import show.jangregersen.buehne.lyrics.Abschnitt
import show.jangregersen.buehne.lyrics.Abschnitte
import show.jangregersen.buehne.lyrics.Teil
import java.io.File
import java.util.zip.ZipFile

/**
 * Liest Jans Songteile aus einer Studio-One-Datei. Eine `.song` ist ein ZIP; in `Song/song.xml`
 * steht der Arranger-Track (Name, Start und Länge in Viertelnoten) und das Tempo (Sekunden pro
 * Viertel). Das Format ist nicht offiziell beschrieben, sondern aus Jans Dateien abgelesen
 * (Studio One 7). Es wird nur gelesen, nie geschrieben.
 */
object StudioOne {
    data class S1Teil(val name: String, val startMs: Long, val dauerMs: Long)
    data class S1Song(val datei: String, val teile: List<S1Teil>, val hinweis: String = "")

    fun lesen(f: File): S1Song? = runCatching {
        // Im ZIP stehen Dateinamen mit Umlauten ("Lautstärke…"), nicht immer als UTF-8. Mit Latin-1
        // lässt sich jede Datei öffnen; der gesuchte Name "Song/song.xml" ist ohnehin reines ASCII.
        ZipFile(f, Charsets.ISO_8859_1).use { z ->
            val e = z.getEntry("Song/song.xml") ?: return null
            ausXml(f.path, z.getInputStream(e).readBytes().toString(Charsets.UTF_8))
        }
    }.getOrNull()

    private fun attr(tag: String, name: String): String? = Regex("\\b$name=\"([^\"]*)\"").find(tag)?.groupValues?.get(1)
        ?.replace("&quot;", "\"")?.replace("&apos;", "'")?.replace("&lt;", "<")?.replace("&gt;", ">")?.replace("&amp;", "&")
    private fun zahl(tag: String, name: String): Double = attr(tag, name)?.toDoubleOrNull() ?: 0.0
    private fun block(xml: String, name: String): String = Regex("<$name\\b.*?</$name>", RegexOption.DOT_MATCHES_ALL).find(xml)?.value ?: ""
    private fun tags(xml: String, name: String): List<String> = Regex("<$name\\b[^>]*>").findAll(xml).map { it.value }.toList()

    fun ausXml(datei: String, xml: String): S1Song {
        val tempi = tags(xml, "TempoMapSegment").map { zahl(it, "tempo") }.filter { it > 0 }
        val sekProViertel = tempi.firstOrNull() ?: 0.5
        // Jede Angabe trägt ihr Zeitformat: 0 = Sekunden, sonst Viertelnoten (bei Jan kommt beides vor).
        fun sek(tag: String, name: String): Double = zahl(tag, name) * if (attr(tag, "timeFormat") == "0") 1.0 else sekProViertel
        val marker = tags(block(xml, "MarkerTrack"), "MarkerEvent")
        // Der Export beginnt am Start-Marker (markerType 2), das Ende ist markerType 3.
        val anfang = marker.firstOrNull { attr(it, "markerType") == "2" }?.let { sek(it, "start") } ?: 0.0
        val ende = marker.firstOrNull { attr(it, "markerType") == "3" }?.let { sek(it, "start") }

        // (Name, Start in Sekunden, Länge in Sekunden)
        var roh = tags(block(xml, "ArrangerTrack"), "ArrangerEvent")
            .map { Triple(attr(it, "name")?.trim().orEmpty(), sek(it, "start"), sek(it, "length")) }
            .sortedBy { it.second }
        var quelle = "Arranger"
        if (roh.isEmpty()) {
            // Manche Songs haben statt Arranger-Teilen benannte Marker: ein Teil reicht bis zum nächsten.
            val benannt = marker.filter { attr(it, "markerType") !in setOf("2", "3") && !attr(it, "name").isNullOrBlank() }
                .map { attr(it, "name")!!.trim() to sek(it, "start") }.sortedBy { it.second }
            roh = benannt.mapIndexed { i, (n, st) -> Triple(n, st, (benannt.getOrNull(i + 1)?.second ?: ende ?: st) - st) }
            quelle = "Marker"
        }
        val teile = roh.filter { it.first.isNotEmpty() && it.second + it.third > anfang }
            .map { (n, st, l) -> S1Teil(n, ((st - anfang) * 1000).toLong().coerceAtLeast(0), (l * 1000).toLong()) }
        val hinweis = buildList {
            if (teile.isEmpty()) add("In dieser Datei stehen keine benannten Teile.")
            else if (quelle == "Marker") add("Teile aus den Markern gelesen (kein Arranger-Track).")
            if (tempi.map { Math.round(it * 100000) }.distinct().size > 1) add("Das Tempo wechselt im Song: Die Zeiten können abweichen.")
        }.joinToString(" ")
        return S1Song(datei, teile, hinweis)
    }

    /** Name ohne Tonart-/Kapo-Anhang und Sonderzeichen, zum Vergleichen ("Grenade KeyBm K2" → "grenade"). */
    fun norm(name: String): String = name.substringBeforeLast('.', name)
        .replace(Regex("\\s+Key\\S+.*$", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\s+[kK]\\d+\\s*$"), "")
        .lowercase().replace(Regex("\\(\\d+\\)|[^a-z0-9äöüß ]"), " ").replace(Regex("\\s+"), " ").trim()

    /** Alle `.song`-Dateien unter Studio One/Songs*. */
    fun alle(wurzel: File): List<File> =
        (wurzel.listFiles { f -> f.isDirectory && f.name.startsWith("Songs") } ?: emptyArray()).flatMap { s ->
            (s.listFiles { f -> f.isDirectory } ?: emptyArray()).flatMap { o ->
                (o.listFiles { f -> f.isFile && f.name.endsWith(".song") } ?: emptyArray()).toList()
            }
        }

    /** Passende Datei zum Titel: gleicher Name (Datei oder Ordner); bei mehreren die neueste mit Teilen. */
    fun passend(titel: String, dateien: List<File>): File? {
        val t = norm(titel)
        if (t.length < 3) return null
        val treffer = dateien.filter { norm(it.name) == t || norm(it.parentFile.name) == t }.sortedByDescending { it.lastModified() }
        return treffer.firstOrNull { lesen(it)?.teile?.isNotEmpty() == true } ?: treffer.firstOrNull()
    }
}

/**
 * Baut aus Jans Studio-One-Teilen einen Ablauf mit Text. Jans Teile sind die Fächer: Ihre Zeiten
 * stimmen (an seinen Songs nachgemessen), ihre Namen sind lose ("Vers" steht bei ihm auch über
 * Refrains). Deshalb wird der Text aus dem Internet der Reihe nach in die Fächer gelegt, und die
 * Namen entscheiden nur, wo sicher KEIN Gesang ist (Intro, Instr, Solo, Outro, End).
 */
object Vorschlag {
    /** Kürzer als das singt niemand einen Teil: gilt als Einzähler/Übergang ohne Gesang. */
    const val KURZ_MS = 6_000L

    private fun buchstaben(name: String) = name.lowercase().filter { it.isLetter() }

    /** Label, das sicher keinen Gesang meint ("Intro", "Intrs", "Instr.", "INST", "Solo", "Outro", "EN>D" …). */
    fun ohneGesang(name: String): Boolean = buchstaben(name).let { n ->
        listOf("intr", "ins", "solo", "out", "end", "zwischen", "vorspiel", "break", "pause", "count").any { n.startsWith(it) }
    }

    private fun chorus(name: String): Boolean = buchstaben(name).let { it.startsWith("cho") || it.startsWith("ref") || it.startsWith("hook") }

    /**
     * Ein Teil pro Studio-One-Teil, mit dessen Startzeit. Fächer ohne Gesang (nach Name, oder
     * sehr kurz) werden Instrumental. Die übrigen bekommen die Textteile in Textreihenfolge.
     * Einzige Ausnahme: Heißt ein Fach ausdrücklich "Chorus" und ist der nächste Text kein
     * Refrain, bekommt es den letzten Refrain noch einmal (Text, in dem der Refrain nur einmal
     * steht). Bleibt Text übrig, füllt er Fächer am Ende (ein "Outro" ist oft doch gesungen).
     * Nur ein Anfang: Jan prüft und schiebt zurecht.
     */
    fun ablauf(abschnitte: List<Abschnitt>, s1: List<StudioOne.S1Teil>): List<Teil> {
        val istRefrain = abschnitte.map { Abschnitte.art(it.name) == Abschnitte.Art.REFRAIN }
        val mitText = abschnitte.indices.filter { i -> abschnitte[i].zeilen.any { it.isNotBlank() } }
        var naechster = 0
        var letzterRefrain: Int? = null
        val out = s1.map { t ->
            val a: Int = when {
                ohneGesang(t.name) || t.dauerMs < KURZ_MS -> Ablauf.INSTRUMENTAL
                chorus(t.name) && letzterRefrain != null && mitText.getOrNull(naechster)?.let { istRefrain[it] } != true -> letzterRefrain!!
                else -> mitText.getOrNull(naechster)?.also { naechster++ } ?: Ablauf.INSTRUMENTAL
            }
            if (a != Ablauf.INSTRUMENTAL && istRefrain[a]) letzterRefrain = a
            Teil(a, t.startMs)
        }.toMutableList()
        // Übriger Text: in die Fächer ohne Gesang hinter dem letzten gesungenen, der Reihe nach.
        var frei = out.indexOfLast { it.abschnitt != Ablauf.INSTRUMENTAL } + 1
        while (naechster < mitText.size && frei < out.size) {
            if (s1[frei].dauerMs >= KURZ_MS) out[frei] = Teil(mitText[naechster++], out[frei].startMs)
            frei++
        }
        return out
    }

    /**
     * Am Platz [pos] ist doch kein Gesang: Der Text ab hier rückt ein Fach nach hinten. Die Zeiten
     * bleiben an den Fächern. Fällt hinten ein Textteil heraus, hängt er ohne Zeit am Ende.
     */
    fun nachHinten(l: List<Teil>, pos: Int, ohneGesang: (Int) -> Boolean = { it == Ablauf.INSTRUMENTAL }): List<Teil> {
        if (pos !in l.indices || ohneGesang(l[pos].abschnitt)) return l
        // B9 (Jans Wahl 14.11.): Nur gesungener Text rückt. Plätze ohne Gesang (Intro, Solo …)
        // bleiben an ihrer Zeit. Unbenannte Instrumental-Fächer hinter dem letzten Text sind frei
        // und nehmen Text auf (ein "Outro" ist oft doch gesungen).
        val textPlaetze = l.indices.filter { it >= pos && !ohneGesang(l[it].abschnitt) }
        val texte = textPlaetze.map { l[it].abschnitt }
        val frei = l.indices.filter { it > textPlaetze.last() && l[it].abschnitt == Ablauf.INSTRUMENTAL }
        val ziele = textPlaetze.drop(1) + frei
        val neu = l.toMutableList()
        neu[pos] = Teil(Ablauf.INSTRUMENTAL, l[pos].startMs)
        texte.forEachIndexed { k, a -> ziele.getOrNull(k)?.let { i -> if (neu[i].abschnitt != a || i == pos) neu[i] = Teil(a, l[i].startMs) } }
        // Kein Platz mehr: Der letzte Textteil hängt ohne Zeit hinten an, nichts geht verloren.
        return if (texte.size > ziele.size) neu + texte.drop(ziele.size).map { Teil(it) } else neu
    }

    /**
     * Das leere Fach [pos] wird gebraucht: Der gesungene Text dahinter rückt einen Textplatz nach
     * vorn, der letzte Textplatz wird frei. Plätze ohne Gesang bleiben stehen (B9).
     */
    fun nachVorn(l: List<Teil>, pos: Int, ohneGesang: (Int) -> Boolean = { it == Ablauf.INSTRUMENTAL }): List<Teil> {
        if (l.getOrNull(pos)?.abschnitt != Ablauf.INSTRUMENTAL) return l
        val textPlaetze = l.indices.filter { it > pos && !ohneGesang(l[it].abschnitt) }
        if (textPlaetze.isEmpty()) return l
        val ziele = listOf(pos) + textPlaetze
        val neu = l.toMutableList()
        textPlaetze.forEachIndexed { k, i -> neu[ziele[k]] = Teil(l[i].abschnitt, l[ziele[k]].startMs) }
        neu[textPlaetze.last()] = Teil(Ablauf.INSTRUMENTAL, l[textPlaetze.last()].startMs)
        // Ein frei gewordener Platz ganz hinten ohne Zeit (war nur angehängt) fällt weg.
        if (textPlaetze.last() == neu.lastIndex && neu.last().startMs < 0) neu.removeAt(neu.lastIndex)
        return neu
    }
}
