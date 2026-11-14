package show.jangregersen.werkstatt

import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Der Mudita Kompakt am USB-Kabel (über adb). Liest `bestand.json`; geschrieben wird nur
 * `eingang.json` (die App führt sie selbst zusammen, `bestand.json` fasst der Laptop nie an).
 */
object Geraet {
    private const val PAKET = "show.jangregersen.buehne"
    private val adb = File(System.getenv("LOCALAPPDATA") ?: "", "Android/Sdk/platform-tools/adb.exe")

    private fun lauf(vararg args: String, sek: Long = 20): Pair<Int, ByteArray> {
        if (!adb.exists()) throw Fehler("Das Werkzeug für die USB-Verbindung (adb) fehlt auf diesem Laptop.")
        val p = ProcessBuilder(listOf(adb.path) + args).redirectError(ProcessBuilder.Redirect.DISCARD).start()
        var aus = ByteArray(0)
        val leser = Thread { aus = p.inputStream.readBytes() }.apply { start() }
        if (!p.waitFor(sek, TimeUnit.SECONDS)) { p.destroyForcibly(); throw Fehler("Der Mudita antwortet nicht. Ist er entsperrt und das Kabel fest?") }
        leser.join(2000)
        return p.exitValue() to aus
    }

    /** Seriennummer des angeschlossenen Geräts, null = keins da. */
    fun seriennummer(): String? = runCatching {
        String(lauf("devices", sek = 8).second).lines().drop(1)
            .map { it.trim().split(Regex("\\s+")) }.firstOrNull { it.size >= 2 && it[1] == "device" && !it[0].startsWith("emulator") }?.get(0)
    }.getOrNull()

    fun bestandLesen(): String {
        val s = seriennummer() ?: throw Fehler("Kein Mudita gefunden. Bitte per USB-Kabel anschließen und entsperren.")
        val (code, aus) = lauf("-s", s, "exec-out", "run-as", PAKET, "cat", "files/bestand.json")
        val text = String(aus, Charsets.UTF_8)
        if (code != 0 || !text.trimStart().startsWith("{")) throw Fehler("Die TrikTrak-Daten auf dem Mudita konnten nicht gelesen werden.")
        return text
    }

    /** Ab dieser App-Fassung (versionCode in app/build.gradle.kts) liest TrikTrak `eingang.json`. */
    const val EINGANG_AB = 2
    /** Ab dieser Fassung übernimmt TrikTrak auch Gigs und Setlisten vom Laptop (B11). */
    const val GIGS_AB = 4
    /** Ab dieser Fassung kann TrikTrak den ganzen Bestand übernehmen (Wiederherstellen). */
    const val GANZ_AB = 3
    private const val ZWISCHEN = "/data/local/tmp/triktrak-eingang.json"

    private fun appFassung(s: String): Int =
        Regex("versionCode=(\\d+)").find(String(lauf("-s", s, "shell", "dumpsys", "package", PAKET).second))?.groupValues?.get(1)?.toInt() ?: 0

    private fun liegtNochDa(s: String) =
        String(lauf("-s", s, "shell", "run-as $PAKET ls files").second).lines().any { it.trim() == "eingang.json" }

    /**
     * Legt die geänderten Songs als `files/eingang.json` auf den Mudita (erst unter anderem Namen,
     * dann umbenennen: TrikTrak sieht nie eine halbe Datei). Die App übernimmt sie, sobald sie zu
     * sehen ist und nichts spielt. Ergebnis: true = schon übernommen, false = liegt noch bereit.
     */
    fun senden(inhalt: String, abFassung: Int = EINGANG_AB): Boolean {
        val s = seriennummer() ?: throw Fehler("Kein Mudita gefunden. Bitte per USB-Kabel anschließen und entsperren.")
        when (appFassung(s)) {
            0 -> throw Fehler("Auf dem Mudita ist TrikTrak nicht zu finden.")
            in 1 until abFassung ->throw Fehler("Die TrikTrak-Fassung auf dem Mudita ist dafür zu alt. Erst muss die neue Fassung aufs Gerät. Es wurde nichts gesendet.")
        }
        val tmp = File.createTempFile("triktrak-eingang", ".json")
        try {
            tmp.writeText(inhalt, Charsets.UTF_8)
            if (lauf("-s", s, "push", tmp.path, ZWISCHEN, sek = 30).first != 0) throw Fehler("Senden hat nicht geklappt: Die Datei kam nicht auf den Mudita. Ist er entsperrt?")
            val ok = lauf("-s", s, "shell", "run-as $PAKET cp $ZWISCHEN files/eingang.neu && run-as $PAKET mv files/eingang.neu files/eingang.json").first == 0
            lauf("-s", s, "shell", "rm -f $ZWISCHEN")
            if (!ok) throw Fehler("Senden hat nicht geklappt: TrikTrak auf dem Mudita hat die Datei nicht angenommen.")
        } finally {
            tmp.delete()
        }
        repeat(10) { Thread.sleep(1000); if (!liegtNochDa(s)) return true }
        return false
    }

    /** B12: Dateien im Musikordner auf dem Mudita (Name → Größe). null = kein Mudita da. */
    fun musikDateien(ordner: String): Map<String, Long>? {
        val s = seriennummer() ?: return null
        val (_, aus) = lauf("-s", s, "shell", "cd ${shText(ordner)} && stat -c '%s|%n' *")
        return NeueSongs.statLesen(String(aus, Charsets.UTF_8))
    }

    /**
     * B12: Eine Musikdatei in den Ordner auf dem Mudita kopieren. Erst unter einem Namen, den
     * TrikTrak nicht als Musik erkennt, dann umbenennen: So sieht die App nie eine halbe Datei.
     */
    fun musikKopieren(datei: File, ordner: String) {
        val s = seriennummer() ?: throw Fehler("Kein Mudita gefunden. Bitte per USB-Kabel anschließen und entsperren.")
        val ziel = "$ordner/${datei.name}"
        val halb = "$ziel.kommt"
        // push nimmt den Zielpfad als ein Argument (keine Shell); mv läuft in der Shell, daher maskiert.
        if (lauf("-s", s, "push", datei.path, halb, sek = 300).first != 0) throw Fehler("„${datei.name}“ kam nicht auf den Mudita. Ist er entsperrt und das Kabel fest?")
        if (lauf("-s", s, "shell", "mv -f ${shText(halb)} ${shText(ziel)}").first != 0) throw Fehler("„${datei.name}“ ließ sich auf dem Mudita nicht ablegen.")
    }

    /** Text sicher für die Shell auf dem Mudita (Leerzeichen, Apostroph wie in „Can't judge a book“). */
    fun shText(t: String): String = "\"" + t.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$").replace("`", "\\`") + "\""

    /** Liegt noch eine gesendete Datei auf dem Mudita, die TrikTrak nicht übernommen hat? null = kein Mudita da. */
    fun wartetNoch(): Boolean? = seriennummer()?.let { runCatching { liegtNochDa(it) }.getOrNull() }
}
