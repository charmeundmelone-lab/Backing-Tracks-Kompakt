package show.jangregersen.buehne.lyrics

/**
 * Ein Teil in Jans eigenem Ablauf. Jan singt oft nicht wie im Original (Teile weg, wiederholt,
 * umgestellt, Instrumental dazwischen). Der Songtext ist der Vorrat an Abschnitten; der Ablauf
 * ist die Liste der Teile in Spielreihenfolge und verweist auf diese Abschnitte.
 */
data class Teil(
    /** Index des Abschnitts im Text, oder [Ablauf.INSTRUMENTAL]. */
    val abschnitt: Int,
    /** Beginn auf dem Track (eingemessen), -1 = noch nicht eingemessen. */
    val startMs: Long = -1,
    /** Von Jan getippte Anfänge der Zeilen dieses Teils, in Reihenfolge. Leer = nicht getippt. */
    val zeilenMs: List<Long> = emptyList()
) {
    val eingemessen: Boolean get() = startMs >= 0
}

object Ablauf {
    const val INSTRUMENTAL = -1

    /**
     * Gespeichert im Song-Feld `marken`, Teile in Spielreihenfolge, kommagetrennt:
     * "abschnitt:ms" oder "abschnitt:ms:z1|z2|…" (ms = -1: nicht eingemessen).
     * Leer = Standard-Ablauf (alle Abschnitte in Textreihenfolge).
     */
    fun lesen(s: String): List<Teil> =
        s.split(',').mapNotNull { p ->
            val teile = p.trim().split(':')
            if (teile.size < 2) return@mapNotNull null
            val a = teile[0].trim().toIntOrNull() ?: return@mapNotNull null
            val ms = teile[1].trim().toLongOrNull() ?: return@mapNotNull null
            val z = teile.getOrNull(2)?.split('|')?.mapNotNull { it.trim().toLongOrNull() }?.sorted() ?: emptyList()
            Teil(a, if (ms < 0) -1 else ms, if (ms < 0) emptyList() else z)
        }

    fun schreiben(l: List<Teil>): String = l.joinToString(",") { t ->
        "${t.abschnitt}:${t.startMs}" + if (t.zeilenMs.isNotEmpty()) ":" + t.zeilenMs.joinToString("|") else ""
    }

    fun standard(anzahlAbschnitte: Int): List<Teil> = (0 until anzahlAbschnitte).map { Teil(it) }

    /** Der Ablauf eines Songs: gespeichert (nur gültige Verweise) oder Standard. */
    fun von(marken: String, abschnitte: List<Abschnitt>): List<Teil> {
        val l = lesen(marken).filter { it.abschnitt == INSTRUMENTAL || it.abschnitt in abschnitte.indices }
        return if (l.isEmpty()) standard(abschnitte.size) else l
    }

    /** Ab Teil [k] gelten keine Zeiten mehr (Ablauf dort geändert oder neu einmessen). */
    fun abHierOhneZeit(l: List<Teil>, k: Int): List<Teil> = l.mapIndexed { i, t -> if (i >= k) Teil(t.abschnitt) else t }

    fun hatZeiten(l: List<Teil>): Boolean = l.any { it.eingemessen }

    /** Nach "Abschnitt [index] an den vorigen hängen" (im Text): Verweise rücken nach. */
    fun nachZusammenlegen(l: List<Teil>, index: Int): List<Teil> {
        val neu = l.map { t -> when {
            t.abschnitt == INSTRUMENTAL -> t
            // Zeilenzahl ändert sich: getippte Zeilen der beiden Abschnitte gelten nicht mehr.
            t.abschnitt == index || t.abschnitt == index - 1 -> Teil(index - 1, t.startMs)
            t.abschnitt > index -> t.copy(abschnitt = t.abschnitt - 1)
            else -> t
        } }
        // Stand "vorige + diese" direkt hintereinander, ist es jetzt ein Teil.
        return neu.filterIndexed { i, t -> !(i > 0 && t.abschnitt == index - 1 && neu[i - 1].abschnitt == index - 1 && l[i].abschnitt == index && l[i - 1].abschnitt == index - 1) }
    }

    fun name(abschnitte: List<Abschnitt>, t: Teil): String = if (t.abschnitt == INSTRUMENTAL) "Instrumental" else abschnitte.getOrNull(t.abschnitt)?.name ?: "?"
}

/**
 * Zeitplan der Lyrics auf Jans Track: wann beginnt welche Zeile?
 *
 * Grundlage sind die eingemessenen Teile. Getippte Zeilen gelten genau. Sonst laufen die Zeilen
 * im Tempo des Originals (Zeitmarken aus der Datenbank); ist der Teil auf dem Track kürzer als im
 * Original, wird gestaucht. Ohne Zeitmarken wird nach Textlänge verteilt.
 */
object Zeitplan {
    /** So viel früher springt die Seite um, damit der neue Teil schon da ist. */
    const val SEITE_VORLAUF_MS = 1500L
    /** So viel früher springt die Zeilen-Marke, damit das Auge vorne ist. */
    const val ZEILE_VORLAUF_MS = 300L

    fun zeitenLesen(s: String): List<Long> = if (s.isBlank()) emptyList() else s.split(',').map { it.trim().toLongOrNull() ?: -1L }
    fun zeitenSchreiben(z: List<Long>): String = z.joinToString(",")

    /** Zeilen pro Abschnitt (nur Textzeilen, ohne Leerzeilen). */
    fun zeilenProAbschnitt(abschnitte: List<Abschnitt>): List<List<String>> = abschnitte.map { a -> a.zeilen.filter { it.isNotBlank() } }

    fun zeilen(abschnitte: List<Abschnitt>, t: Teil): List<String> =
        if (t.abschnitt == Ablauf.INSTRUMENTAL) emptyList() else abschnitte.getOrNull(t.abschnitt)?.zeilen?.filter { it.isNotBlank() } ?: emptyList()

    /** Zeitmarken gelten nur, wenn ihre Anzahl genau zu den Textzeilen passt. */
    fun gueltig(abschnitte: List<Abschnitt>, zeiten: List<Long>): Boolean =
        zeiten.isNotEmpty() && zeiten.size == zeilenProAbschnitt(abschnitte).sumOf { it.size }

    /** Originalzeiten der Zeilen eines Abschnitts, oder null wenn unbrauchbar. */
    private fun original(abschnitte: List<Abschnitt>, zeiten: List<Long>, a: Int): List<Long>? {
        if (!gueltig(abschnitte, zeiten) || a !in abschnitte.indices) return null
        val pro = zeilenProAbschnitt(abschnitte)
        val von = pro.take(a).sumOf { it.size }
        val z = zeiten.subList(von, von + pro[a].size)
        return z.takeIf { it.isNotEmpty() && it.all { t -> t >= 0 } && it.zipWithNext().all { (x, y) -> y >= x } }
    }

    /** Dauer des Abschnitts im Original: bis zum nächsten Abschnitt mit Zeiten, sonst letzte Zeile + 4 s. */
    private fun spanne(abschnitte: List<Abschnitt>, zeiten: List<Long>, a: Int, orig: List<Long>): Long {
        val naechster = (a + 1 until abschnitte.size).firstNotNullOfOrNull { original(abschnitte, zeiten, it)?.firstOrNull() }
        return if (naechster != null && naechster > orig.first()) naechster - orig.first() else orig.last() - orig.first() + 4000
    }

    /**
     * Startzeiten der Zeilen von Abschnitt [a], wenn er bei [startMs] beginnt. [verfuegbarMs] = Zeit
     * bis zum nächsten Teil (null = unbekannt, dann Originaltempo).
     */
    fun teilZeiten(abschnitte: List<Abschnitt>, zeiten: List<Long>, a: Int, startMs: Long, verfuegbarMs: Long?): List<Long> {
        val zeilen = zeilenProAbschnitt(abschnitte).getOrNull(a) ?: return emptyList()
        if (zeilen.isEmpty()) return emptyList()
        val orig = original(abschnitte, zeiten, a)
        if (orig != null) {
            val sp = spanne(abschnitte, zeiten, a, orig)
            val faktor = if (verfuegbarMs != null && verfuegbarMs in 1 until sp) verfuegbarMs.toDouble() / sp else 1.0
            return orig.map { startMs + ((it - orig.first()) * faktor).toLong() }
        }
        // Ohne Zeitmarken: etwa 3,5 s pro Zeile, nach Textlänge verteilt, höchstens 85 % des Teils.
        val gewicht = zeilen.map { maxOf(8, it.length).toDouble() }
        val summe = gewicht.sum()
        val dauer = minOf(zeilen.size * 3500.0, (verfuegbarMs ?: Long.MAX_VALUE / 2) * 0.85)
        var acc = 0.0
        return gewicht.map { g -> val t = startMs + (acc / summe * dauer).toLong(); acc += g; t }
    }

    /**
     * Startzeiten aller Zeilen pro Teil (null = Teil nicht eingemessen). Getippte Zeilen gelten
     * genau; was danach noch fehlt, läuft im Abstand des Originals hinter der letzten getippten.
     */
    fun zeilenStarts(abschnitte: List<Abschnitt>, ablauf: List<Teil>, zeiten: List<Long>, dauerMs: Long): List<List<Long>?> =
        ablauf.mapIndexed { k, t ->
            if (!t.eingemessen) return@mapIndexed null
            if (t.abschnitt == Ablauf.INSTRUMENTAL) return@mapIndexed emptyList()
            val ende = ablauf.drop(k + 1).firstOrNull { it.eingemessen && it.startMs > t.startMs }?.startMs ?: dauerMs.takeIf { it > t.startMs }
            val basis = teilZeiten(abschnitte, zeiten, t.abschnitt, t.startMs, ende?.let { it - t.startMs })
            val n = minOf(t.zeilenMs.size, basis.size)
            if (n == 0) basis
            else basis.indices.map { i -> if (i < n) t.zeilenMs[i] else t.zeilenMs[n - 1] + (basis[i] - basis[n - 1]) }
        }

    /** Teil, der zur Position läuft (Index im Ablauf), oder null vor dem ersten eingemessenen. */
    fun aktiverTeil(ablauf: List<Teil>, posMs: Long): Int? =
        ablauf.indices.filter { ablauf[it].eingemessen && ablauf[it].startMs <= posMs }.maxByOrNull { ablauf[it].startMs }

    /** (Teil, Zeile im Teil) zur Position; Zeile -1 = Teil hat begonnen, Gesang noch nicht. */
    fun aktiveZeile(ablauf: List<Teil>, starts: List<List<Long>?>, posMs: Long): Pair<Int, Int>? {
        val k = aktiverTeil(ablauf, posMs) ?: return null
        val s = starts.getOrNull(k) ?: return k to -1
        return k to s.indexOfLast { it <= posMs }
    }

    /**
     * Vorläufiger Ablauf aus dem Original (Textreihenfolge, mit Zeiten). Nur wenn die Länge des
     * Originals bis auf ±3 s zum Track passt (dann ist es sehr wahrscheinlich dieselbe Fassung).
     */
    fun vorschlag(abschnitte: List<Abschnitt>, zeiten: List<Long>, originalMs: Long, trackMs: Long): List<Teil> {
        if (!gueltig(abschnitte, zeiten) || originalMs <= 0 || trackMs <= 0 || kotlin.math.abs(originalMs - trackMs) > 3000) return emptyList()
        val out = abschnitte.indices.map { a -> Teil(a, original(abschnitte, zeiten, a)?.firstOrNull() ?: -1) }
        val mit = out.filter { it.eingemessen }
        return if (mit.isNotEmpty() && mit.zipWithNext().all { (x, y) -> y.startMs > x.startMs }) out else emptyList()
    }
}
