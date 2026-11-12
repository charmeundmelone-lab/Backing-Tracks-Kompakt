package show.jangregersen.buehne.lyrics

/**
 * Macht aus einem Songtext der Datenbank (ohne Etiketten) einen Text mit Abschnitten wie
 * "[Strophe 1]", "[Refrain]", und ordnet jeder Textzeile ihre Zeit im Original zu.
 *
 * Erkennung (Vorschlag, Jan prüft danach): Was sich wiederholt, ist der Refrain; was mehrfach
 * direkt davor steht, der Vorrefrain; ein einmaliger Block nach dem zweiten Refrain die Bridge;
 * ein kurzer einmaliger Schluss nach dem letzten Refrain das Outro. Der Rest sind Strophen;
 * ein Bruchstück (bis 2 Zeilen) direkt neben einer Strophe wird an sie angehängt.
 */
object Erkennung {

    data class LrcZeile(val ms: Long, val text: String)

    data class Ergebnis(
        /** Text mit [Etikett]-Zeilen und Leerzeilen zwischen den Abschnitten. */
        val text: String,
        /** Pro Textzeile (ohne Etiketten- und Leerzeilen) die Zeit im Original, -1 = unbekannt. */
        val zeiten: List<Long>
    )

    private val ZEITMARKE = Regex("^\\s*\\[(\\d+):(\\d+(?:[.,]\\d+)?)]\\s*(.*)$")

    /** Liest LRC-Zeilen "[mm:ss.xx] Text". Leere Zeilen (Pausen) und Kopfzeilen fallen weg. */
    fun lrcLesen(lrc: String?): List<LrcZeile> {
        if (lrc.isNullOrBlank()) return emptyList()
        return lrc.replace("\r", "").lines().mapNotNull { l ->
            val m = ZEITMARKE.matchEntire(l) ?: return@mapNotNull null
            val ms = (m.groupValues[1].toLong() * 60_000 + (m.groupValues[2].replace(',', '.').toDouble() * 1000).toLong())
            val t = m.groupValues[3].trim()
            if (t.isEmpty()) null else LrcZeile(ms, t)
        }.sortedBy { it.ms }
    }

    fun normal(s: String): String =
        s.lowercase().replace(Regex("\\(.*?\\)"), "").replace(Regex("[^\\p{L}\\p{N} ]"), "").replace(Regex("\\s+"), " ").trim()

    /** Blöcke mit Zeilen und (falls bekannt) Zeiten. */
    private data class Block(val zeilen: List<String>, val zeiten: List<Long>)

    fun aufbereiten(plain: String?, synced: String?): Ergebnis? {
        val lrc = lrcLesen(synced)
        val bloecke = bloeckeBilden(plain.orEmpty(), lrc)
        if (bloecke.isEmpty()) return null
        val namen = benennen(bloecke.map { it.zeilen })
        // Zusammenlegen: Strophen-Bruchstück (bis 2 Zeilen) direkt neben einer anderen Strophe.
        val teile = mutableListOf<Pair<String, Block>>()
        for ((i, b) in bloecke.withIndex()) {
            val n = namen[i]
            val vorher = teile.lastOrNull()
            if (vorher != null && n.startsWith("S") && vorher.first.startsWith("S") &&
                minOf(vorher.second.zeilen.size, b.zeilen.size) <= 2 && vorher.second.zeilen.size + b.zeilen.size <= 8 &&
                namen.subList(0, i).count { it == n } == 0
            ) {
                teile[teile.size - 1] = vorher.first to Block(vorher.second.zeilen + b.zeilen, vorher.second.zeiten + b.zeiten)
            } else teile.add(n to b)
        }
        // Strophen nach dem Zusammenlegen neu durchzählen (gleiche Strophe = gleiche Nummer).
        val nummer = mutableMapOf<String, Int>()
        val sb = StringBuilder()
        val zeiten = mutableListOf<Long>()
        for ((n, b) in teile) {
            val etikett = when {
                n.startsWith("S") -> "Strophe ${nummer.getOrPut(n) { nummer.size + 1 }}"
                n == "R" -> "Refrain"
                n == "V" -> "Vorrefrain"
                n == "B" -> "Bridge"
                n == "O" -> "Outro"
                else -> n
            }
            if (sb.isNotEmpty()) sb.append("\n\n")
            sb.append("[").append(etikett).append("]\n").append(b.zeilen.joinToString("\n"))
            zeiten.addAll(b.zeiten)
        }
        return Ergebnis(sb.toString(), zeiten)
    }

    private fun bloeckeBilden(plain: String, lrc: List<LrcZeile>): List<Block> {
        val absaetze = plain.replace("\r", "").split(Regex("\\n\\s*\\n"))
            .map { a -> a.lines().map { it.trim() }.filter { it.isNotEmpty() } }.filter { it.isNotEmpty() }
        if (absaetze.size >= 3 || lrc.isEmpty()) {
            if (absaetze.isEmpty()) return emptyList()
            // Zeiten den Zeilen in Reihenfolge zuordnen (gleicher Text, sonst bleibt -1).
            var j = 0
            return absaetze.map { a ->
                Block(a, a.map { z ->
                    val nz = normal(z)
                    var k = j
                    while (k < lrc.size && k < j + 4 && normal(lrc[k].text) != nz) k++
                    if (k < lrc.size && k < j + 4) { j = k + 1; lrc[k].ms } else -1L
                })
            }
        }
        // Text ohne Absätze: an langen Pausen zwischen den Zeilen trennen.
        val abstaende = lrc.zipWithNext { a, b -> b.ms - a.ms }.sorted()
        val mittel = abstaende.getOrElse(abstaende.size / 2) { 3000L }
        val grenze = maxOf(4500L, (mittel * 2.2).toLong())
        val out = mutableListOf<Block>()
        var zeilen = mutableListOf<String>(); var zeiten = mutableListOf<Long>()
        lrc.forEachIndexed { i, z ->
            if (i > 0 && z.ms - lrc[i - 1].ms > grenze && zeilen.isNotEmpty()) {
                out.add(Block(zeilen, zeiten)); zeilen = mutableListOf(); zeiten = mutableListOf()
            }
            zeilen.add(z.text); zeiten.add(z.ms)
        }
        if (zeilen.isNotEmpty()) out.add(Block(zeilen, zeiten))
        return out
    }

    private fun aehnlich(a: List<String>, b: List<String>): Double {
        val x = a.map(::normal).filter { it.isNotEmpty() }.toSet()
        val y = b.map(::normal).filter { it.isNotEmpty() }.toSet()
        if (x.isEmpty() || y.isEmpty()) return 0.0
        return x.count { it in y }.toDouble() / minOf(x.size, y.size)
    }

    /** Kürzel pro Block: "S<gruppe>", "R", "V", "B", "O". */
    fun benennen(bloecke: List<List<String>>): List<String> {
        val n = bloecke.size
        val gruppe = IntArray(n) { -1 }
        var g = 0
        for (i in 0 until n) {
            if (gruppe[i] >= 0) continue
            gruppe[i] = g
            for (k in i + 1 until n) if (gruppe[k] < 0 && aehnlich(bloecke[i], bloecke[k]) >= 0.6) gruppe[k] = g
            g++
        }
        val anzahl = gruppe.toList().groupingBy { it }.eachCount()
        val wiederholt = anzahl.filter { it.value >= 2 }.keys
        val refrain = wiederholt.maxWithOrNull(compareBy<Int>({ anzahl[it]!! }, { gruppe.lastIndexOf(it) }))
        val vor = wiederholt.filter { r -> r != refrain && (0 until n - 1).count { gruppe[it] == r && gruppe[it + 1] == refrain } >= 2 }.toSet()
        val out = mutableListOf<String>()
        var refrainsBisher = 0
        var strophen = 0
        for (i in 0 until n) {
            val x = gruppe[i]
            val einmalig = anzahl[x] == 1
            val kommtNochRefrain = (i + 1 until n).any { gruppe[it] == refrain }
            out.add(when {
                x == refrain -> { refrainsBisher++; "R" }
                x in vor -> "V"
                einmalig && refrainsBisher >= 2 && !kommtNochRefrain && i == n - 1 && bloecke[i].size <= 3 -> "O"
                einmalig && refrainsBisher >= 2 && strophen >= 2 -> "B"
                else -> {
                    val frueher = (0 until i).firstOrNull { gruppe[it] == x }
                    if (frueher != null) out[frueher] else { strophen++; "S$x" }
                }
            })
        }
        return out
    }
}
