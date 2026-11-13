package show.jangregersen.buehne.daten

import org.json.JSONArray
import org.json.JSONObject
import show.jangregersen.buehne.lyrics.Ablauf
import show.jangregersen.buehne.lyrics.Abschnitte

/**
 * Der Bestand als JSON (Datei `bestand.json`). Eigene Datei ohne Android-Teile, damit das
 * Laptop-Programm (Modul `werkstatt`) genau denselben Code zum Lesen und Schreiben benutzt.
 */
object BestandJson {
    fun alsJson(b: Bestand): JSONObject = JSONObject().apply {
        put("version", 1)
        put("naechsteId", b.naechsteId)
        put("songs", JSONArray().apply { b.songs.forEach { put(songJson(it)) } })
        put("gigs", JSONArray().apply { b.gigs.forEach { put(gigJson(it)) } })
    }

    fun ausJson(o: JSONObject): Bestand {
        val songs = o.optJSONArray("songs")?.let { a -> (0 until a.length()).map { songAus(a.getJSONObject(it)) } } ?: emptyList()
        val gigs = o.optJSONArray("gigs")?.let { a -> (0 until a.length()).map { gigAus(a.getJSONObject(it)) } } ?: emptyList()
        val maxId = (songs.map { it.id } + gigs.map { it.id } + gigs.flatMap { g -> g.sets.map { it.id } }).maxOrNull() ?: 0
        return Bestand(songs, gigs, maxOf(o.optLong("naechsteId", 1), maxId + 1))
    }

    /** Datei `eingang.json`: am Laptop geänderte Songs, die der Kompakt übernehmen soll. */
    fun eingangJson(songs: List<Song>): JSONObject = JSONObject().apply {
        put("eingang", 1)
        put("songs", JSONArray().apply { songs.forEach { put(songJson(it)) } })
    }

    fun eingangAus(o: JSONObject): List<Song> =
        o.optJSONArray("songs")?.let { a -> (0 until a.length()).map { songAus(a.getJSONObject(it)) } } ?: emptyList()

    private fun songJson(s: Song) = JSONObject().apply {
        put("id", s.id); put("titel", s.titel); put("interpret", s.interpret); put("bpm", s.bpm)
        put("tonart", s.tonart); put("kapo", s.kapo); put("tempo", s.tempo); put("pfad", s.pfad)
        put("dauerMs", s.dauerMs); put("lyrics", s.lyrics); put("marken", s.marken); put("ablauf", 1)
        put("markenVorlaeufig", s.markenVorlaeufig); put("zeilenZeiten", s.zeilenZeiten); put("originalMs", s.originalMs)
        put("ansageVorher", s.ansageVorher); put("ansageVorherMs", s.ansageVorherMs)
        put("ansageNachher", s.ansageNachher); put("ansageNachherMs", s.ansageNachherMs)
        put("pauseSek", s.pauseSek)
        put("mix", JSONObject().apply { s.mix.forEach { (k, v) -> put(k, v) } })
    }

    private fun songAus(o: JSONObject): Song {
        val mixO = o.optJSONObject("mix")
        val mix = mutableMapOf<String, Int>()
        mixO?.keys()?.forEach { k -> mix[k] = mixO.optInt(k, 10) }
        return Song(
            id = o.getLong("id"), titel = o.optString("titel"), interpret = o.optString("interpret"),
            bpm = o.optInt("bpm"), tonart = o.optString("tonart"), kapo = o.optInt("kapo"),
            tempo = o.optInt("tempo"), pfad = o.optString("pfad"), dauerMs = o.optLong("dauerMs"),
            lyrics = o.optString("lyrics"),
            // Ohne "ablauf": Stand von vor dem eigenen Ablauf, nur eingemessene Abschnitte gespeichert.
            marken = o.optString("marken").let { m -> if (o.has("ablauf") || m.isBlank()) m else Ablauf.ausAltemStand(m, Abschnitte.zerlegen(o.optString("lyrics")).size) },
            markenVorlaeufig = o.optBoolean("markenVorlaeufig"), zeilenZeiten = o.optString("zeilenZeiten"), originalMs = o.optLong("originalMs"),
            ansageVorher = o.optString("ansageVorher"), ansageVorherMs = o.optLong("ansageVorherMs"),
            ansageNachher = o.optString("ansageNachher"), ansageNachherMs = o.optLong("ansageNachherMs"),
            pauseSek = o.optInt("pauseSek"), mix = mix
        )
    }

    private fun gigJson(g: Gig) = JSONObject().apply {
        put("id", g.id); put("name", g.name); put("datum", g.datum); put("autoWeiter", g.autoWeiter)
        put("sets", JSONArray().apply {
            g.sets.forEach { s ->
                put(JSONObject().apply {
                    put("id", s.id); put("name", s.name)
                    put("eintraege", JSONArray().apply {
                        s.eintraege.forEach { e ->
                            put(JSONObject().apply { put("songId", e.songId); put("gespielt", e.gespielt); put("ende", e.ende) })
                        }
                    })
                })
            }
        })
    }

    private fun gigAus(o: JSONObject): Gig {
        val sets = o.optJSONArray("sets")?.let { a ->
            (0 until a.length()).map { i ->
                val s = a.getJSONObject(i)
                val e = s.optJSONArray("eintraege")
                SetListe(
                    id = s.getLong("id"), name = s.optString("name"),
                    eintraege = if (e == null) emptyList() else (0 until e.length()).map { j ->
                        val x = e.getJSONObject(j)
                        Eintrag(x.getLong("songId"), x.optBoolean("gespielt"), x.optInt("ende", Ende.ANHALTEN))
                    }
                )
            }
        } ?: emptyList()
        return Gig(o.getLong("id"), o.optString("name"), o.optLong("datum"), sets, o.optBoolean("autoWeiter", true))
    }
}
