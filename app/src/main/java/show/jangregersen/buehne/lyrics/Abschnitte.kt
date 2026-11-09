package show.jangregersen.buehne.lyrics

/**
 * Zerlegt einen Songtext in Abschnitte (Strophe, Refrain …). Abschnitte beginnen an Zeilen wie
 * "[Chorus]". Gibt es keine solchen Zeilen, trennt jede Leerzeile einen neuen Abschnitt.
 */
data class Abschnitt(val name: String, val zeilen: List<String>)

object Abschnitte {
    private val LABEL = Regex("^\\s*\\[(.+)]\\s*$")

    private val UEBERSETZT = listOf(
        "pre-chorus" to "Vorrefrain", "prechorus" to "Vorrefrain", "pre chorus" to "Vorrefrain",
        "chorus" to "Refrain", "refrain" to "Refrain", "verse" to "Strophe", "strophe" to "Strophe",
        "bridge" to "Bridge", "intro" to "Intro", "outro" to "Outro", "solo" to "Solo",
        "interlude" to "Zwischenspiel", "instrumental" to "Instrumental", "hook" to "Hook", "ending" to "Schluss"
    )

    fun deutsch(label: String): String {
        val t = label.trim()
        val klein = t.lowercase()
        for ((en, de) in UEBERSETZT) {
            if (klein.startsWith(en)) {
                val rest = t.substring(en.length).trim().trimStart(':', '-').trim()
                return if (rest.isEmpty()) de else "$de $rest"
            }
        }
        return t
    }

    fun zerlegen(text: String): List<Abschnitt> {
        val zeilen = text.replace("\r", "").lines().map { it.trimEnd() }
        val mitLabels = zeilen.any { LABEL.matches(it) }
        val roh = mutableListOf<Pair<String?, MutableList<String>>>()
        if (mitLabels) {
            var aktuell: Pair<String?, MutableList<String>>? = null
            for (z in zeilen) {
                val m = LABEL.matchEntire(z)
                if (m != null) { aktuell = deutsch(m.groupValues[1]) to mutableListOf(); roh.add(aktuell) }
                else {
                    if (aktuell == null) { aktuell = null as String? to mutableListOf(); roh.add(aktuell) }
                    aktuell.second.add(z)
                }
            }
        } else {
            var aktuell = mutableListOf<String>()
            for (z in zeilen) {
                if (z.isBlank()) { if (aktuell.isNotEmpty()) { roh.add(null to aktuell); aktuell = mutableListOf() } }
                else aktuell.add(z)
            }
            if (aktuell.isNotEmpty()) roh.add(null to aktuell)
        }
        var teil = 0
        return roh.mapNotNull { (name, z) ->
            val sauber = z.dropWhile { it.isBlank() }.dropLastWhile { it.isBlank() }
                .fold(mutableListOf<String>()) { acc, s -> if (s.isBlank() && acc.lastOrNull()?.isBlank() == true) acc else acc.apply { add(s) } }
            if (name == null && sauber.isEmpty()) return@mapNotNull null
            teil++
            Abschnitt(name ?: "Teil $teil", sauber)
        }
    }

    // ── Einmess-Punkte ─────────────────────────────────────────────────────────

    fun markenLesen(s: String): Map<Int, Long> =
        s.split(',').mapNotNull { p ->
            val i = p.indexOf(':'); if (i <= 0) return@mapNotNull null
            val a = p.substring(0, i).trim().toIntOrNull(); val ms = p.substring(i + 1).trim().toLongOrNull()
            if (a == null || ms == null) null else a to ms
        }.toMap()

    fun markenSchreiben(m: Map<Int, Long>): String =
        m.entries.sortedBy { it.key }.joinToString(",") { "${it.key}:${it.value}" }
}

/** Eine Bildschirmseite der Lyrics. */
data class LyricsSeite(
    val abschnitt: Int,
    val name: String,
    val teil: Int,
    val teile: Int,
    val zeilen: List<String>,
    val schriftSp: Float,
    /** Anteil der Zeilen vor dieser Seite innerhalb des Abschnitts (0..1). */
    val anteilStart: Float
)

object Seitenlauf {
    /**
     * Welche Seite gehört zur Wiedergabeposition? Abschnittsstart = Einmess-Punkt; Seiten
     * innerhalb eines Abschnitts teilen sich seine Zeit nach Zeilenanteil. Ohne Punkt für den
     * ersten Abschnitt bleibt Seite 0 stehen (dann blättert man von Hand).
     */
    fun seiteFuer(seiten: List<LyricsSeite>, marken: Map<Int, Long>, posMs: Long, dauerMs: Long): Int {
        if (seiten.isEmpty() || marken.isEmpty()) return 0
        val gesetzt = marken.filter { it.value <= posMs }
        if (gesetzt.isEmpty()) return 0
        val abschnitt = gesetzt.maxByOrNull { it.value }!!.key
        val start = marken[abschnitt]!!
        val ende = marken.filter { it.value > start }.minOfOrNull { it.value } ?: dauerMs.takeIf { it > start } ?: (start + 60_000)
        val anteil = ((posMs - start).toFloat() / (ende - start).coerceAtLeast(1)).coerceIn(0f, 1f)
        val kandidaten = seiten.withIndex().filter { it.value.abschnitt == abschnitt }
        if (kandidaten.isEmpty()) return seiten.indexOfLast { it.abschnitt <= abschnitt }.coerceAtLeast(0)
        return kandidaten.lastOrNull { it.value.anteilStart <= anteil + 0.0001f }?.index ?: kandidaten.first().index
    }
}
