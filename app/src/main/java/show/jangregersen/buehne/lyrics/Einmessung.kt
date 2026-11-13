package show.jangregersen.buehne.lyrics

/**
 * Die Regeln des Einmessens, ohne Bildschirm (damit sie prüfbar sind). Eingemessen wird immer der
 * Reihe nach: Die eingemessenen Teile stehen vorn im Ablauf, dahinter kommt, was noch fehlt.
 */
object Einmessung {
    /** Zwei Tipps so dicht hintereinander sind ein Versehen (Doppeltipp, träger E-Ink-Schirm). */
    const val DOPPELTIPP_MS = 300L
    /** "Weiter einmessen" spielt so viel vor dem letzten Tipp weiter. */
    const val ANLAUF_MS = 5_000L

    /** Getippte Zeilenanfänge eines Teils. Alte Daten kennen nur den Teil-Anfang: der gilt als Zeile 1. */
    fun getippt(abschnitte: List<Abschnitt>, t: Teil): List<Long> =
        if (t.zeilenMs.isEmpty() && t.eingemessen && Zeitplan.zeilen(abschnitte, t).isNotEmpty()) listOf(t.startMs) else t.zeilenMs

    /** Der zuletzt eingemessene Teil (Index im Ablauf), null = noch nichts. */
    fun letzter(ablauf: List<Teil>): Int? = ablauf.indices.lastOrNull { ablauf[it].eingemessen }

    /** Was als Nächstes getippt wird: (Teil, Zeile). null = alles getippt. */
    fun ziel(abschnitte: List<Abschnitt>, ablauf: List<Teil>): Pair<Int, Int>? {
        val k = letzter(ablauf) ?: return if (ablauf.isNotEmpty()) 0 to 0 else null
        val g = getippt(abschnitte, ablauf[k]).size
        return when {
            g < Zeitplan.zeilen(abschnitte, ablauf[k]).size -> k to g
            k + 1 < ablauf.size -> (k + 1) to 0
            else -> null
        }
    }

    /** Zeit des letzten Tipps, null = noch keiner. */
    fun letzterTipp(abschnitte: List<Abschnitt>, ablauf: List<Teil>): Long? =
        letzter(ablauf)?.let { getippt(abschnitte, ablauf[it]).lastOrNull() ?: ablauf[it].startMs }

    /** Tipp auf den großen Knopf bei [ms]: die nächste Zeile (oder der nächste Teil) beginnt. */
    fun tippen(abschnitte: List<Abschnitt>, ablauf: List<Teil>, ms: Long): List<Teil> {
        val (ti, zi) = ziel(abschnitte, ablauf) ?: return ablauf
        val davor = letzterTipp(abschnitte, ablauf)
        if (davor != null && ms - davor in 0 until DOPPELTIPP_MS) return ablauf
        val teil = ablauf[ti]
        val l = ablauf.toMutableList()
        l[ti] = if (zi == 0) Teil(teil.abschnitt, ms, if (Zeitplan.zeilen(abschnitte, teil).isNotEmpty()) listOf(ms) else emptyList())
        else teil.copy(zeilenMs = getippt(abschnitte, teil) + ms)
        return l
    }

    /** Letzten Tipp zurücknehmen. War es der Anfang eines Teils, gilt der Teil wieder als offen. */
    fun tippWeg(abschnitte: List<Abschnitt>, ablauf: List<Teil>): List<Teil> {
        val k = letzter(ablauf) ?: return ablauf
        val g = getippt(abschnitte, ablauf[k])
        return ablauf.toMutableList().also { it[k] = if (g.size > 1) ablauf[k].copy(zeilenMs = g.dropLast(1)) else Teil(ablauf[k].abschnitt) }
    }

    /** Nach dem Zurückspringen auf [jetztMs]: Alles, was später getippt wurde, gilt nicht mehr. */
    fun zurueck(ablauf: List<Teil>, jetztMs: Long): List<Teil> =
        ablauf.map { t -> if (t.startMs > jetztMs) Teil(t.abschnitt) else t.copy(zeilenMs = t.zeilenMs.filter { it <= jetztMs }) }

    /** Darf der nächste Teil ausgelassen werden? Nur vor seinem Anfang, und einer muss bleiben. */
    fun kannAuslassen(abschnitte: List<Abschnitt>, ablauf: List<Teil>): Boolean =
        ablauf.size > 1 && ziel(abschnitte, ablauf)?.second == 0

    /** Den nächsten Teil aus dem Ablauf nehmen (Jan singt ihn nicht). Zeiten bleiben. */
    fun auslassen(abschnitte: List<Abschnitt>, ablauf: List<Teil>): List<Teil> {
        if (!kannAuslassen(abschnitte, ablauf)) return ablauf
        val zi = ziel(abschnitte, ablauf)!!.first
        return ablauf.filterIndexed { i, _ -> i != zi }
    }

    /**
     * "Anderer Teil": Abschnitt [a] (oder Instrumental) beginnt bei [ms], direkt nach dem zuletzt
     * eingemessenen Teil. Steht genau dieser Teil dort ohnehin als Nächstes, wird er genommen statt
     * doppelt eingefügt (z. B. letzte Zeile der Strophe nicht getippt, Refrain beginnt).
     */
    fun teilBeginnt(abschnitte: List<Abschnitt>, ablauf: List<Teil>, a: Int, ms: Long): List<Teil> {
        val stelle = (letzter(ablauf) ?: -1) + 1
        val neu = Teil(a, ms).let { if (Zeitplan.zeilen(abschnitte, it).isNotEmpty()) it.copy(zeilenMs = listOf(ms)) else it }
        val l = ablauf.toMutableList()
        if (l.getOrNull(stelle)?.abschnitt == a) l[stelle] = neu else l.add(stelle, neu)
        return l
    }

    /** "Weiter einmessen": ab hier weiterspielen (kurz vor dem letzten Tipp). Gelöscht wird nichts. */
    fun weiterAb(abschnitte: List<Abschnitt>, ablauf: List<Teil>): Long =
        ((letzterTipp(abschnitte, ablauf) ?: 0L) - ANLAUF_MS).coerceAtLeast(0)
}
