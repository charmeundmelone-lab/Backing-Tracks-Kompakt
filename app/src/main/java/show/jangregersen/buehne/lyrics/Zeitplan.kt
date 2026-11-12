package show.jangregersen.buehne.lyrics

/**
 * Zeitplan der Lyrics auf Jans Track: wann beginnt welche Zeile?
 *
 * Grundlage sind die Einmess-Punkte (Abschnittsanfänge auf dem Track). Innerhalb eines
 * Abschnitts werden die Zeitmarken des Originals auf die Länge des Abschnitts im Track
 * gestreckt. Ohne Zeitmarken wird die Zeit nach Textlänge verteilt.
 */
object Zeitplan {
    /** So viel früher springt die Seite um, damit der neue Abschnitt schon da ist. */
    const val SEITE_VORLAUF_MS = 1500L
    /** So viel früher springt die Zeilen-Marke, damit das Auge vorne ist. */
    const val ZEILE_VORLAUF_MS = 400L

    fun zeitenLesen(s: String): List<Long> = if (s.isBlank()) emptyList() else s.split(',').map { it.trim().toLongOrNull() ?: -1L }
    fun zeitenSchreiben(z: List<Long>): String = z.joinToString(",")

    /** Zeilen pro Abschnitt (nur Textzeilen, ohne Leerzeilen). */
    fun zeilenProAbschnitt(abschnitte: List<Abschnitt>): List<List<String>> = abschnitte.map { a -> a.zeilen.filter { it.isNotBlank() } }

    /** Zeitmarken gelten nur, wenn ihre Anzahl genau zu den Textzeilen passt. */
    fun gueltig(abschnitte: List<Abschnitt>, zeiten: List<Long>): Boolean =
        zeiten.isNotEmpty() && zeiten.size == zeilenProAbschnitt(abschnitte).sumOf { it.size }

    /**
     * Vorläufige Einmess-Punkte aus dem Original. Nur wenn die Länge des Originals bis auf ±3 s
     * zum Track passt (dann ist es sehr wahrscheinlich dieselbe Fassung).
     */
    fun markenVorschlag(abschnitte: List<Abschnitt>, zeiten: List<Long>, originalMs: Long, trackMs: Long): Map<Int, Long> {
        if (!gueltig(abschnitte, zeiten) || originalMs <= 0 || trackMs <= 0 || kotlin.math.abs(originalMs - trackMs) > 3000) return emptyMap()
        val out = mutableMapOf<Int, Long>()
        var i = 0
        zeilenProAbschnitt(abschnitte).forEachIndexed { ai, z ->
            val erste = zeiten.subList(i, i + z.size).firstOrNull { it >= 0 }
            if (erste != null) out[ai] = erste
            i += z.size
        }
        // Punkte müssen streng steigen, sonst lieber gar keinen Vorschlag.
        val w = out.toSortedMap().values.toList()
        return if (w.zipWithNext().all { (a, b) -> b > a }) out else emptyMap()
    }

    /**
     * Startzeit jeder Textzeile auf dem Track, pro Abschnitt. Abschnitte ohne Einmess-Punkt
     * bekommen keine Zeiten (null).
     */
    fun zeilenStarts(abschnitte: List<Abschnitt>, marken: Map<Int, Long>, zeiten: List<Long>, dauerMs: Long): List<List<Long>?> {
        val proAbschnitt = zeilenProAbschnitt(abschnitte)
        val mitZeiten = gueltig(abschnitte, zeiten)
        val sortiert = marken.entries.sortedBy { it.value }
        var offset = 0
        val origProAbschnitt = proAbschnitt.map { z -> val t = if (mitZeiten) zeiten.subList(offset, offset + z.size) else emptyList(); offset += z.size; t }
        return proAbschnitt.mapIndexed { ai, z ->
            val start = marken[ai] ?: return@mapIndexed null
            if (z.isEmpty()) return@mapIndexed emptyList()
            val ende = sortiert.firstOrNull { it.value > start }?.value ?: dauerMs.takeIf { it > start } ?: (start + 30_000)
            val orig = origProAbschnitt[ai]
            val o0 = orig.firstOrNull { it >= 0 }
            // Ende des Abschnitts im Original: erste Zeit des nächsten Abschnitts mit Zeiten.
            val oEnde = origProAbschnitt.drop(ai + 1).firstNotNullOfOrNull { n -> n.firstOrNull { it >= 0 } }
            if (mitZeiten && o0 != null && orig.all { it >= 0 } && oEnde != null && oEnde > o0 && orig.zipWithNext().all { (a, b) -> b >= a }) {
                val faktor = (ende - start).toDouble() / (oEnde - o0)
                orig.map { start + ((it - o0) * faktor).toLong() }
            } else {
                // Nach Textlänge verteilt; die letzten 15 % des Abschnitts bleiben Luft (Ausklang, Solo).
                val gewicht = z.map { maxOf(8, it.length).toDouble() }
                val summe = gewicht.sum()
                val nutzbar = (ende - start) * 0.85
                var acc = 0.0
                gewicht.map { g -> val t = start + (acc / summe * nutzbar).toLong(); acc += g; t }
            }
        }
    }

    /** (Abschnitt, Zeile im Abschnitt) der Zeile, die zur Position läuft, oder null vor der ersten. */
    fun aktiveZeile(starts: List<List<Long>?>, posMs: Long): Pair<Int, Int>? {
        var treffer: Pair<Int, Int>? = null
        var trefferZeit = Long.MIN_VALUE
        starts.forEachIndexed { ai, s ->
            s?.forEachIndexed { i, t -> if (t <= posMs && t >= trefferZeit) { treffer = ai to i; trefferZeit = t } }
        }
        return treffer
    }

    /** Erste Zeile, die nach der Position beginnt (für den Seitenvorlauf). */
    fun naechsteZeile(starts: List<List<Long>?>, posMs: Long): Pair<Int, Int>? {
        var treffer: Pair<Int, Int>? = null
        var trefferZeit = Long.MAX_VALUE
        starts.forEachIndexed { ai, s ->
            s?.forEachIndexed { i, t -> if (t > posMs && t < trefferZeit) { treffer = ai to i; trefferZeit = t } }
        }
        return treffer
    }
}
