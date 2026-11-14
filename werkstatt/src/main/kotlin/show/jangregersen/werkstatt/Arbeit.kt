package show.jangregersen.werkstatt

import org.json.JSONArray
import org.json.JSONObject
import show.jangregersen.buehne.daten.Bestand
import show.jangregersen.buehne.daten.BestandJson
import show.jangregersen.buehne.daten.Gig
import show.jangregersen.buehne.daten.GigAbgleich
import show.jangregersen.buehne.daten.Song
import show.jangregersen.buehne.daten.songAendern
import show.jangregersen.buehne.lyrics.Abgleich
import show.jangregersen.buehne.lyrics.Abschnitt
import show.jangregersen.buehne.lyrics.Abschnitte
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class Fehler(val meldung: String) : Exception(meldung)

/**
 * Die Arbeitskopie auf dem Laptop: derselbe Bestand wie auf dem Kompakt (`bestand.json`), dazu
 * was nur der Laptop wissen muss (welche Songs hier geändert wurden, welche Studio-One-Datei zu
 * welchem Song gehört). Vor jedem Überschreiben wird eine Sicherung abgelegt.
 */
class Arbeit(private val ordner: File) {
    private val datei = File(ordner, "bestand.json")
    private val zustandDatei = File(ordner, "zustand.json")
    private val sicherungen = File(ordner, "sicherungen")

    var bestand: Bestand = Bestand(); private set
    /** Songs, deren Text oder Teile am Laptop geändert und noch nicht zum Kompakt gesendet wurden. */
    val geaendert = sortedSetOf<Long>()
    /** Song → Studio-One-Datei, von Jan gewählt ("" = bewusst keine). */
    val s1 = mutableMapOf<Long, String>()
    var geholtAm: Long = 0; private set
    private val verlauf = mutableMapOf<Long, ArrayDeque<Song>>()

    /**
     * B11: Gigs, die am Laptop geändert und noch nicht beim Mudita angekommen sind. Wert = Abdruck
     * des Mudita-Stands, auf dem die Änderung beruht (null = am Laptop neu angelegt). Danach
     * entscheidet der Mudita: Hat er den Gig inzwischen selbst geändert, bleibt seiner.
     */
    val gigsGeaendert = sortedMapOf<Long, String?>()
    /** Gesendet, aber vom Mudita noch nicht übernommen (eingang.json liegt dort noch). */
    val gigsGesendet = sortedSetOf<Long>()
    /** Beim letzten Holen nicht übernommene Laptop-Gigs (Mudita war geändert), für die Meldung. */
    var gigKonflikte: List<String> = emptyList(); private set

    init {
        ordner.mkdirs()
        if (datei.exists()) runCatching { bestand = BestandJson.ausJson(JSONObject(datei.readText())) }
        if (zustandDatei.exists()) runCatching {
            val o = JSONObject(zustandDatei.readText())
            o.optJSONArray("geaendert")?.let { a -> (0 until a.length()).forEach { geaendert += a.getLong(it) } }
            o.optJSONObject("s1")?.let { m -> m.keys().forEach { k -> s1[k.toLong()] = m.getString(k) } }
            o.optJSONObject("gigsGeaendert")?.let { m -> m.keys().forEach { k -> gigsGeaendert[k.toLong()] = if (m.isNull(k)) null else m.getString(k) } }
            o.optJSONArray("gigsGesendet")?.let { a -> (0 until a.length()).forEach { gigsGesendet += a.getLong(it) } }
            geholtAm = o.optLong("geholtAm")
        }
    }

    @Synchronized
    private fun speichern() {
        val tmp = File(ordner, "bestand.json.neu")
        tmp.writeText(BestandJson.alsJson(bestand).toString())
        if (!tmp.renameTo(datei)) { datei.delete(); tmp.renameTo(datei) }
        zustandDatei.writeText(JSONObject().apply {
            put("geaendert", JSONArray(geaendert.toList()))
            put("s1", JSONObject().apply { s1.forEach { (k, v) -> put(k.toString(), v) } })
            put("gigsGeaendert", JSONObject().apply { gigsGeaendert.forEach { (k, v) -> put(k.toString(), v ?: JSONObject.NULL) } })
            put("gigsGesendet", JSONArray(gigsGesendet.toList()))
            put("geholtAm", geholtAm)
        }.toString(1))
    }

    private fun stempel() = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))

    private fun aufraeumen(vorsilbe: String, behalten: Int) =
        sicherungen.listFiles { f -> f.name.startsWith(vorsilbe) }?.sortedByDescending { it.name }?.drop(behalten)?.forEach { it.delete() }

    private fun sichern() {
        if (!datei.exists()) return
        sicherungen.mkdirs()
        datei.copyTo(File(sicherungen, "bestand-" + stempel() + ".json"), overwrite = true)
        aufraeumen("bestand-", 40)
    }

    /** Genaue Kopie dessen, was auf dem Mudita lag (für "Mudita wiederherstellen"). Gleicher Stand wie zuletzt: keine neue Datei. */
    private fun muditaSichern(json: String) {
        sicherungen.mkdirs()
        val letzte = muditaSicherungen().firstOrNull()
        if (letzte != null && letzte.readText() == json) return
        File(sicherungen, "mudita-" + stempel() + ".json").writeText(json)
        aufraeumen("mudita-", 60)
    }

    /** Kopien vom Mudita, neueste zuerst. */
    fun muditaSicherungen(): List<File> =
        sicherungen.listFiles { f -> f.name.startsWith("mudita-") && f.name.endsWith(".json") }?.sortedByDescending { it.name } ?: emptyList()

    /**
     * Frischer Stand vom Kompakt. Was hier am Laptop an Text und Teilen geändert und noch nicht
     * gesendet wurde, bleibt erhalten (wird auf den neuen Stand gelegt).
     */
    @Synchronized
    fun vomGeraet(json: String, eingangOffen: Boolean = false) {
        var neu = try { BestandJson.ausJson(JSONObject(json)) } catch (e: Exception) { throw Fehler("Die Daten vom Mudita sind nicht lesbar. Es wurde nichts geändert.") }
        if (neu.songs.isEmpty() && bestand.songs.isNotEmpty()) throw Fehler("Der Mudita meldet gar keine Songs. Das sieht falsch aus, es wurde nichts geändert. Falls er wirklich Daten verloren hat: oben „Sicherungen“.")
        muditaSichern(json)
        sichern()
        geaendert.retainAll(neu.songs.map { it.id }.toSet())
        // Hat der Mudita den Laptop-Stand schon (gesendet und übernommen), gilt der Song nicht mehr als offen.
        val angekommen = mutableSetOf<Long>()
        for (id in geaendert) {
            val hier = bestand.song(id) ?: continue
            val dort = neu.song(id) ?: continue
            val zusammen = Abgleich.textUndTeile(dort, hier)
            if (zusammen == dort) angekommen += id else neu = neu.songAendern(id) { zusammen }
        }
        geaendert.removeAll(angekommen)
        neu = gigsDarauflegen(neu, eingangOffen)
        bestand = neu
        verlauf.clear()
        geholtAm = System.currentTimeMillis()
        speichern()
    }

    /** Ändert einen Song (Text, Teile, Tonart, Kapo). Der Stand davor bleibt für "Rückgängig" erhalten. */
    @Synchronized
    fun aendern(id: Long, f: (Song) -> Song): Boolean {
        val alt = bestand.song(id) ?: throw Fehler("Diesen Song gibt es nicht mehr.")
        val neu = f(alt)
        if (neu == alt) return false
        verlauf.getOrPut(id) { ArrayDeque() }.apply { addLast(alt); if (size > 50) removeFirst() }
        bestand = bestand.songAendern(id) { neu }
        geaendert += id
        speichern()
        return true
    }

    /**
     * B11: Die offenen Laptop-Gigs auf den frischen Mudita-Stand legen. Regel "Mudita schützen":
     * Hat der Mudita einen Gig seit der Laptop-Änderung selbst geändert (oder gelöscht), gilt
     * seiner; der Laptop-Stand landet als Datei in `sicherungen/` und in [gigKonflikte].
     * Liegt eine gesendete Datei noch unübernommen auf dem Mudita ([eingangOffen]), bleibt der
     * Laptop-Stand einfach stehen. Ist sie übernommen, sind die gesendeten Gigs erledigt.
     */
    private fun gigsDarauflegen(mudita: Bestand, eingangOffen: Boolean): Bestand {
        if (!eingangOffen && gigsGesendet.isNotEmpty()) { gigsGesendet.forEach { gigsGeaendert.remove(it) }; gigsGesendet.clear() }
        var neu = mudita
        val konflikte = mutableListOf<String>()
        for ((id, basis) in gigsGeaendert.toList()) {
            val hier = bestand.gig(id)
            val dort = neu.gig(id)
            val wartet = id in gigsGesendet
            if (!wartet && hier != null && dort != null && GigAbgleich.abdruck(hier) == GigAbgleich.abdruck(dort)) { gigsGeaendert.remove(id); continue }
            if (!wartet && GigAbgleich.hindernis(neu, GigAbgleich.Aenderung(id, hier, basis)) != null) {
                sicherungen.mkdirs()
                hier?.let { File(sicherungen, "laptop-gig-${stempel()}-$id.json").writeText(BestandJson.alsJson(Bestand(gigs = listOf(it))).toString(1)) }
                konflikte += "„${hier?.name ?: dort?.name ?: "Gig"}“"
                gigsGeaendert.remove(id)
                continue
            }
            neu = when {
                hier == null -> neu.copy(gigs = neu.gigs.filterNot { it.id == id })
                dort == null -> neu.copy(gigs = neu.gigs + hier)
                // gespielt-Haken zeigt der Laptop so, wie der Mudita sie hat.
                else -> neu.copy(gigs = neu.gigs.map { g ->
                    if (g.id != id) g else hier.copy(sets = hier.sets.map { s ->
                        val alt = dort.sets.firstOrNull { it.id == s.id }
                        s.copy(eintraege = s.eintraege.map { e -> e.copy(gespielt = alt?.eintraege?.firstOrNull { it.songId == e.songId }?.gespielt ?: e.gespielt) })
                    })
                })
            }
        }
        gigKonflikte = konflikte
        return neu
    }

    /** Neue vorläufige Nummer für einen am Laptop angelegten Gig oder ein Set (negativ, der Mudita vergibt die echte). */
    private fun vorlaeufigeId(): Long = minOf(0L, bestand.gigs.minOfOrNull { it.id } ?: 0L, bestand.gigs.flatMap { g -> g.sets.map { it.id } }.minOrNull() ?: 0L) - 1

    /**
     * Ändert einen Gig am Laptop. [gigId] null = neuen anlegen. [f] bekommt den Gig (bzw. einen
     * leeren neuen) und eine Quelle für vorläufige Nummern; Rückgabe null = Gig löschen.
     * Ergebnis: Nummer des Gigs (bei neuem die vorläufige).
     */
    @Synchronized
    fun gigAendern(gigId: Long?, f: (Gig, () -> Long) -> Gig?): Long {
        val alt = gigId?.let { bestand.gig(it) ?: throw Fehler("Diesen Gig gibt es nicht mehr.") }
        val id = gigId ?: vorlaeufigeId()
        var naechste = minOf(vorlaeufigeId(), id - 1)
        val neu = f(alt ?: Gig(id, "Neuer Gig"), { naechste-- })
        if (neu == alt) return id
        if (id !in gigsGeaendert) gigsGeaendert[id] = alt?.let { GigAbgleich.abdruck(it) }
        bestand = when {
            neu == null -> bestand.copy(gigs = bestand.gigs.filterNot { it.id == id })
            alt == null -> bestand.copy(gigs = bestand.gigs + neu.copy(id = id))
            else -> bestand.copy(gigs = bestand.gigs.map { if (it.id == id) neu.copy(id = id) else it })
        }
        // Am Laptop angelegt und wieder gelöscht: dann gibt es nichts zu senden.
        if (neu == null && gigsGeaendert[id] == null) gigsGeaendert.remove(id)
        speichern()
        return id
    }

    /** Was "Zum Mudita senden" zählt: geänderte Songs plus geänderte Gigs. */
    fun offen(): Int = geaendert.size + gigsGeaendert.size

    /** Inhalt für `eingang.json`: alle hier geänderten, noch nicht angekommenen Songs und Gigs. */
    @Synchronized
    fun zumSenden(): String = BestandJson.eingangJson(
        geaendert.mapNotNull { bestand.song(it) },
        gigsGeaendert.map { (id, basis) -> GigAbgleich.Aenderung(id, bestand.gig(id), basis) }
    ).toString()

    /** Nach dem Senden: Diese Gigs warten jetzt auf dem Mudita (bzw. sind schon übernommen). */
    @Synchronized
    fun gigsGesendet(uebernommen: Boolean) {
        if (uebernommen) { gigsGeaendert.clear(); gigsGesendet.clear() } else gigsGesendet += gigsGeaendert.keys
        speichern()
    }

    fun kannZurueck(id: Long) = verlauf[id]?.isNotEmpty() == true

    @Synchronized
    fun zurueck(id: Long): Boolean {
        val alt = verlauf[id]?.removeLastOrNull() ?: return false
        bestand = bestand.songAendern(id) { alt }
        speichern()
        return true
    }

    @Synchronized
    fun s1Setzen(id: Long, pfad: String) { s1[id] = pfad; speichern() }
}

/** Text-Umbauten, die es nur am Laptop gibt. */
object Texte {
    /**
     * Teilt Abschnitt [a] vor seiner Textzeile [zeile] (0 = erste Zeile; Leerzeilen zählen nicht).
     * Der neue zweite Abschnitt bekommt zunächst denselben Namen.
     */
    fun teilen(text: String, a: Int, zeile: Int): String {
        val l = Abschnitte.zerlegen(text).toMutableList()
        val ab = l.getOrNull(a) ?: return text
        var n = 0
        var schnitt = -1
        ab.zeilen.forEachIndexed { i, z -> if (z.isNotBlank()) { if (n == zeile) schnitt = i; n++ } }
        if (schnitt <= 0) return text
        l[a] = ab.copy(zeilen = ab.zeilen.take(schnitt).dropLastWhile { it.isBlank() })
        l.add(a + 1, Abschnitt(ab.name, ab.zeilen.drop(schnitt)))
        return Abschnitte.zusammensetzen(l)
    }
}
