package show.jangregersen.werkstatt

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.json.JSONArray
import org.json.JSONObject
import show.jangregersen.buehne.daten.Song
import show.jangregersen.buehne.lyrics.Abgleich
import show.jangregersen.buehne.lyrics.Ablauf
import show.jangregersen.buehne.lyrics.Abschnitte
import show.jangregersen.buehne.lyrics.Einmessung
import show.jangregersen.buehne.lyrics.Teil
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URLDecoder

/**
 * TrikTrak Werkstatt: kleiner Server nur für diesen Rechner (127.0.0.1). Die Seite im Browser ist
 * die Oberfläche; alle Regeln (Text zerlegen, Ablauf, Abgleich) kommen aus dem Code der Kompakt-App.
 */
/** Erster Versuch; ist der Anschluss belegt, wird der nächste freie genommen. */
const val ERSTER_PORT = 47811
private var port = ERSTER_PORT

private val heim = File(System.getProperty("user.home"))
private val arbeit by lazy { Arbeit(File(datenOrdner)) }
private val s1Wurzel = File(heim, "Documents/Studio One")
private var s1Dateien: List<File> = emptyList()

private val datenOrdner = System.getProperty("triktrak.daten") ?: File(heim, "TrikTrak-Werkstatt").path

/** Läuft schon eine Werkstatt mit denselben Daten? Dann deren Anschluss (zweiter Doppelklick öffnet nur die Seite). */
private fun laeuftSchon(): Int? = (ERSTER_PORT until ERSTER_PORT + 10).firstOrNull { p ->
    runCatching {
        val c = java.net.URL("http://127.0.0.1:$p/api/wer").openConnection() as java.net.HttpURLConnection
        c.connectTimeout = 300; c.readTimeout = 1000
        JSONObject(c.inputStream.readBytes().toString(Charsets.UTF_8)).optString("daten") == File(datenOrdner).absolutePath
    }.getOrDefault(false)
}

private fun browser(p: Int) = runCatching { ProcessBuilder("cmd", "/c", "start", "", "http://127.0.0.1:$p").start() }

fun main(args: Array<String>) {
    laeuftSchon()?.let { p ->
        println("Die Werkstatt läuft schon (http://127.0.0.1:$p). Ich öffne nur die Seite. Dieses Fenster kannst du schließen.")
        if ("--ohne-browser" !in args) browser(p)
        return
    }
    s1Dateien = StudioOne.alle(s1Wurzel)
    var server: HttpServer? = null
    for (p in ERSTER_PORT until ERSTER_PORT + 10) {
        server = runCatching { HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), p), 0) }.getOrNull()
        if (server != null) { port = p; break }
    }
    if (server == null) { println("Kein freier Anschluss gefunden. Läuft die Werkstatt schon?"); return }
    server.createContext("/") { x -> bedienen(x) }
    server.start()
    println("TrikTrak Werkstatt läuft: http://127.0.0.1:$port  (Fenster offen lassen, Strg+C beendet)")
    if ("--ohne-browser" !in args) browser(port)
}

private fun bedienen(x: HttpExchange) {
    try {
        // Nur Aufrufe von der eigenen Seite: fremde Webseiten können weder die Adresse vortäuschen
        // noch den eigenen Kopf mitschicken.
        val host = x.requestHeaders.getFirst("Host") ?: ""
        if (host != "127.0.0.1:$port" && host != "localhost:$port") return antwort(x, 403, "text/plain", "Nur von diesem Rechner.".toByteArray())
        val pfad = x.requestURI.path
        if (!pfad.startsWith("/api/")) return datei(x, pfad)
        if (x.requestMethod == "POST" && x.requestHeaders.getFirst("X-TrikTrak") != "1") return antwort(x, 403, "text/plain", "Nicht erlaubt.".toByteArray())
        val frage = (x.requestURI.rawQuery ?: "").split('&').filter { '=' in it }.associate { it.substringBefore('=') to URLDecoder.decode(it.substringAfter('='), "UTF-8") }
        val koerper = if (x.requestMethod == "POST") x.requestBody.readBytes().toString(Charsets.UTF_8).ifBlank { "{}" }.let { JSONObject(it) } else JSONObject()
        val ergebnis = try { api(pfad.removePrefix("/api/"), frage, koerper) } catch (e: Fehler) { JSONObject().put("fehler", e.meldung) }
        antwort(x, 200, "application/json; charset=utf-8", ergebnis.toString().toByteArray())
    } catch (e: Exception) {
        e.printStackTrace()
        runCatching { antwort(x, 500, "application/json; charset=utf-8", JSONObject().put("fehler", "Da ist etwas schiefgegangen: ${e.message ?: e.javaClass.simpleName}").toString().toByteArray()) }
    }
}

private fun antwort(x: HttpExchange, code: Int, typ: String, daten: ByteArray) {
    x.responseHeaders.add("Content-Type", typ)
    x.responseHeaders.add("Cache-Control", "no-store")
    x.sendResponseHeaders(code, daten.size.toLong())
    x.responseBody.use { it.write(daten) }
}

private fun datei(x: HttpExchange, pfad: String) {
    val name = if (pfad == "/") "index.html" else pfad.removePrefix("/")
    if (".." in name) return antwort(x, 404, "text/plain", ByteArray(0))
    val daten = object {}.javaClass.getResourceAsStream("/web/$name")?.use { it.readBytes() } ?: return antwort(x, 404, "text/plain", "Nicht gefunden.".toByteArray())
    val typ = when (name.substringAfterLast('.')) {
        "html" -> "text/html; charset=utf-8"; "js" -> "text/javascript; charset=utf-8"; "css" -> "text/css; charset=utf-8"; "ttf" -> "font/ttf"; else -> "application/octet-stream"
    }
    antwort(x, 200, typ, daten)
}

// ── Die Aufrufe der Seite ────────────────────────────────────────────────────

private fun api(was: String, frage: Map<String, String>, k: JSONObject): JSONObject {
    fun id() = (frage["id"]?.toLongOrNull() ?: k.optLong("id", -1)).also { if (arbeit.bestand.song(it) == null) throw Fehler("Diesen Song gibt es nicht (mehr).") }
    return when (was) {
        "liste" -> liste()
        "wer" -> JSONObject().put("daten", File(datenOrdner).absolutePath)
        "holen" -> { arbeit.vomGeraet(Geraet.bestandLesen()); liste() }
        "senden" -> {
            if (arbeit.geaendert.isEmpty()) throw Fehler("Es gibt nichts zu senden: Am Laptop ist nichts geändert, was der Mudita nicht schon hat.")
            val n = arbeit.geaendert.size
            val angekommen = Geraet.senden(arbeit.zumSenden())
            // Angekommen: frisch holen, dabei fallen die übernommenen Songs aus "geändert" heraus.
            if (angekommen) arbeit.vomGeraet(Geraet.bestandLesen())
            liste().put("gesendet", if (angekommen) "TrikTrak auf dem Mudita hat $n ${if (n == 1) "Song" else "Songs"} übernommen. Deine Zeiten vom Einmessen sind geblieben, wo der Text gleich blieb."
                else "Gesendet. Die Änderungen liegen auf dem Mudita bereit und kommen in TrikTrak an, sobald du die App öffnest (nicht während ein Song läuft oder du einmisst). Danach hier einmal „Vom Mudita holen“.")
        }
        "song" -> songJson(id())
        "text" -> id().let { i -> arbeit.aendern(i) { s -> s.copy(lyrics = k.getString("text"), marken = Ablauf.markenNachText(s.marken, s.lyrics, k.getString("text"))) }; songJson(i) }
        "folge" -> folge(id(), k)
        "tauschen" -> tauschen(id(), k)
        "ruecken" -> id().let { i ->
            val pos = k.getInt("pos")
            arbeit.aendern(i) { s ->
                val alt = Ablauf.von(s.marken, Abschnitte.zerlegen(s.lyrics))
                val neu = if (k.optBoolean("vor")) Vorschlag.nachVorn(alt, pos) else Vorschlag.nachHinten(alt, pos)
                s.copy(marken = Ablauf.schreiben(neu))
            }
            songJson(i)
        }
        "etikett" -> id().let { i -> arbeit.aendern(i) { s -> s.copy(lyrics = Abschnitte.umbenennen(s.lyrics, k.getInt("abschnitt"), k.getString("name").trim().ifBlank { throw Fehler("Das Etikett braucht einen Namen.") })) }; songJson(i) }
        "verbinden" -> umbau(id(), k) { s, ab, l ->
            val a = k.getInt("abschnitt")
            if (a !in 1 until ab.size) throw Fehler("Dieser Teil hat keinen Vorgänger im Text.")
            Triple(Abschnitte.mitVorigem(s.lyrics, a), Ablauf.nachZusammenlegen(l, a), l.indexOfFirst { it.abschnitt == a || it.abschnitt == a - 1 })
        }
        "teilen" -> umbau(id(), k) { s, _, l ->
            val a = k.getInt("abschnitt")
            val neu = Texte.teilen(s.lyrics, a, k.getInt("zeile"))
            if (neu == s.lyrics) throw Fehler("An dieser Stelle lässt sich der Teil nicht teilen.")
            // Jeder Teil, der auf den Abschnitt zeigte, wird zu "erste Hälfte, zweite Hälfte"; spätere Nummern rücken auf.
            Triple(neu, l.flatMap { t -> when { t.abschnitt == a -> listOf(Teil(a, t.startMs), Teil(a + 1)); t.abschnitt > a -> listOf(t.copy(abschnitt = t.abschnitt + 1)); else -> listOf(t) } }, l.indexOfFirst { it.abschnitt == a })
        }
        "s1liste" -> JSONObject().put("dateien", JSONArray(s1Dateien.filter { f -> (frage["q"] ?: "").trim().lowercase().let { q -> q.isEmpty() || q in f.name.lowercase() || q in f.parentFile.name.lowercase() } }
            .sortedByDescending { it.lastModified() }.take(60).map { JSONObject().put("pfad", it.path).put("name", it.nameWithoutExtension).put("ordner", it.parentFile.name) }))
        "s1waehlen" -> id().let { i -> arbeit.s1Setzen(i, k.optString("datei")); songJson(i) }
        "s1vorschlag" -> s1Vorschlag(id(), k)
        "zurueck" -> id().let { i -> arbeit.zurueck(i); songJson(i) }
        else -> throw Fehler("Unbekannter Aufruf: $was")
    }
}

private fun liste(): JSONObject = JSONObject().apply {
    put("geholtAm", arbeit.geholtAm)
    put("mudita", Geraet.seriennummer() != null)
    put("songs", JSONArray(arbeit.bestand.songs.sortedBy { it.titel.lowercase() }.map { s ->
        val ab = Abschnitte.zerlegen(s.lyrics)
        val l = if (ab.isEmpty()) emptyList() else Ablauf.von(s.marken, ab)
        JSONObject().put("id", s.id).put("titel", s.titel).put("interpret", s.interpret).put("hatText", ab.isNotEmpty())
            .put("teile", l.size).put("eingemessen", l.count { it.eingemessen }).put("vorlaeufig", s.markenVorlaeufig).put("geaendert", s.id in arbeit.geaendert)
    }))
}

private fun s1Fuer(s: Song): StudioOne.S1Song? {
    val gewaehlt = arbeit.s1[s.id]
    val f = when {
        gewaehlt == "" -> null
        gewaehlt != null -> File(gewaehlt).takeIf { it.exists() }
        else -> StudioOne.passend(s.titel, s1Dateien)
    } ?: return null
    return StudioOne.lesen(f)
}

private fun zeit(ms: Long) = "%d:%02d".format(ms / 60000, ms / 1000 % 60)

/**
 * Jan exportiert genau den Bereich seiner Arranger-Teile: Deren Ende trifft die Länge der fertigen
 * Datei (an seinen Songs nachgemessen, meist auf 0,1 s). Weicht es deutlich ab, gehört die
 * Studio-One-Datei wohl zu einer anderen Fassung, und ihre Zeiten taugen nicht.
 */
private fun laengenHinweis(so: StudioOne.S1Song, s: Song): String {
    val ende = so.teile.maxOfOrNull { it.startMs + it.dauerMs } ?: return ""
    if (s.dauerMs <= 0 || kotlin.math.abs(ende - s.dauerMs) <= 3000) return ""
    return "Achtung: In Studio One enden die Teile bei ${zeit(ende)}, dein Track ist ${zeit(s.dauerMs)} lang. Vermutlich eine andere Fassung: Die Zeiten können falsch sein."
}

private fun songJson(id: Long): JSONObject {
    val s = arbeit.bestand.song(id)!!
    val ab = Abschnitte.zerlegen(s.lyrics)
    val l = if (ab.isEmpty()) emptyList() else Ablauf.von(s.marken, ab)
    return JSONObject().apply {
        put("id", s.id); put("titel", s.titel); put("interpret", s.interpret); put("dauerMs", s.dauerMs); put("text", s.lyrics)
        put("vorlaeufig", s.markenVorlaeufig); put("geaendert", s.id in arbeit.geaendert); put("kannZurueck", arbeit.kannZurueck(id))
        put("abschnitte", JSONArray(ab.mapIndexed { i, a ->
            JSONObject().put("name", a.name).put("art", Abschnitte.art(a.name).name).put("zeilen", JSONArray(a.zeilen)).put("imAblauf", l.count { it.abschnitt == i })
        }))
        put("ablauf", JSONArray(l.map { t ->
            JSONObject().put("abschnitt", t.abschnitt).put("startMs", t.startMs).put("getippt", if (s.markenVorlaeufig) 0 else Einmessung.getippt(ab, t).size)
        }))
        s1Fuer(s)?.let { so ->
            put("s1", JSONObject().put("datei", so.datei).put("name", File(so.datei).nameWithoutExtension).put("hinweis", listOf(so.hinweis, laengenHinweis(so, s)).filter { it.isNotBlank() }.joinToString(" "))
                .put("teile", JSONArray(so.teile.map { JSONObject().put("name", it.name).put("startMs", it.startMs).put("dauerMs", it.dauerMs) })))
        }
    }
}

/** Antwort "frage": So viele eingemessene Teile verlören ihre Zeiten. Die Seite fragt nach und ruft mit sicher=true erneut auf. */
private fun rueckfrage(verliert: Int) = JSONObject().put("frage", verliert)

private fun folge(id: Long, k: JSONObject): JSONObject {
    val s = arbeit.bestand.song(id)!!
    val ab = Abschnitte.zerlegen(s.lyrics)
    val alt = Ablauf.von(s.marken, ab)
    val f = k.getJSONArray("folge").let { a -> (0 until a.length()).map { a.getInt(it) } }
    if (f.isEmpty()) throw Fehler("Ein Teil muss im Ablauf bleiben.")
    if (f.any { it != Ablauf.INSTRUMENTAL && it !in ab.indices }) throw Fehler("Diesen Textteil gibt es nicht.")
    val verliert = if (s.markenVorlaeufig) 0 else Abgleich.verliertZeiten(alt, f)
    if (verliert > 0 && !k.optBoolean("sicher")) return rueckfrage(verliert)
    val neu = Abgleich.folgeSetzen(alt, f)
    arbeit.aendern(id) { it.copy(marken = Ablauf.schreiben(neu), markenVorlaeufig = it.markenVorlaeufig && Ablauf.hatZeiten(neu)) }
    return songJson(id)
}

/**
 * Anderer Text für denselben Platz im Ablauf (z. B. der Vorschlag hat die falsche Strophe gewählt).
 * Der Anfang des Teils auf dem Track bleibt, nur seine getippten Zeilen gelten nicht mehr.
 */
private fun tauschen(id: Long, k: JSONObject): JSONObject {
    val s = arbeit.bestand.song(id)!!
    val ab = Abschnitte.zerlegen(s.lyrics)
    val alt = Ablauf.von(s.marken, ab)
    val pos = k.getInt("pos")
    val a = k.getInt("abschnitt")
    if (pos !in alt.indices || (a != Ablauf.INSTRUMENTAL && a !in ab.indices)) throw Fehler("Diesen Teil gibt es nicht.")
    if (!s.markenVorlaeufig && alt[pos].zeilenMs.size > 1 && !k.optBoolean("sicher")) return rueckfrage(1)
    val neu = alt.toMutableList().also { it[pos] = Teil(a, alt[pos].startMs) }
    arbeit.aendern(id) { it.copy(marken = Ablauf.schreiben(neu)) }
    return songJson(id)
}

/** Umbau am Text, der die Nummern der Abschnitte ändert: neuer Text, neuer Ablauf, ab welcher Stelle Zeiten wegfallen. */
private fun umbau(id: Long, k: JSONObject, f: (Song, List<show.jangregersen.buehne.lyrics.Abschnitt>, List<Teil>) -> Triple<String, List<Teil>, Int>): JSONObject {
    val s = arbeit.bestand.song(id)!!
    val ab = Abschnitte.zerlegen(s.lyrics)
    val alt = Ablauf.von(s.marken, ab)
    val (text, neu, ab0) = f(s, ab, alt)
    val stelle = if (ab0 < 0) neu.size else ab0
    val verliert = if (s.markenVorlaeufig) 0 else alt.drop(stelle).count { it.eingemessen }
    if (verliert > 0 && !k.optBoolean("sicher")) return rueckfrage(verliert)
    val ohne = Ablauf.abHierOhneZeit(neu, stelle)
    arbeit.aendern(id) { it.copy(lyrics = text, marken = Ablauf.schreiben(ohne), markenVorlaeufig = it.markenVorlaeufig && Ablauf.hatZeiten(ohne)) }
    return songJson(id)
}

private fun s1Vorschlag(id: Long, k: JSONObject): JSONObject {
    val s = arbeit.bestand.song(id)!!
    val ab = Abschnitte.zerlegen(s.lyrics)
    if (ab.isEmpty()) throw Fehler("Erst braucht der Song einen Text.")
    val so = s1Fuer(s) ?: throw Fehler("Zu diesem Song ist keine Studio-One-Datei gewählt.")
    if (so.teile.isEmpty()) throw Fehler("In der Studio-One-Datei stehen keine benannten Teile.")
    val verliert = if (s.markenVorlaeufig) 0 else Ablauf.von(s.marken, ab).count { it.eingemessen }
    if (verliert > 0 && !k.optBoolean("sicher")) return rueckfrage(verliert)
    val neu = Vorschlag.ablauf(ab, so.teile)
    // Die Zeiten kommen aus Studio One, nicht von Jans Tippen: als "vorläufig" merken.
    arbeit.aendern(id) { it.copy(marken = Ablauf.schreiben(neu), markenVorlaeufig = true) }
    return songJson(id)
}
